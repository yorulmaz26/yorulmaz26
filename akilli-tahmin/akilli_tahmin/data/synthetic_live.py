"""Sentetik canlı maç geçmişi: gol dakikaları ve geçmiş canlı anlık durumlar (Canlı Gol Radar için).

Gerçek bir canlı veri arşivi (dakika dakika skor, xG ve canlı oran geçmişi) bağlanana kadar Canlı Gol
Radar'ın MODEL B eğitimi ve nadir olay kalibrasyonu bu modülün ürettiği sentetik geçmişle yapılır.

- `generate_goal_events`: oynanmış her sentetik maç için ilk yarı / maç sonu skoruyla tutarlı gol
  dakikaları üretir (ilk yarı golleri 1–45, ikinci yarı golleri 46–90 arasında düzgün dağılır).
  Kayıt zamanı (`recorded_at`) maç sonucunun kaydedildiği andır; böylece zaman kesimi sorguları
  gol dakikalarını sonuçla aynı anda "bilir".
- `historical_snapshots`: bu maçların belirli dakikalardaki durumunu (o ana kadarki skor, sentetik
  canlı xG, kalan süre) ve hedefin gerçekleşip gerçekleşmediğini (dakikadan sonra 90. dakikaya kadar
  en az bir gol) döndürür. Her anlık durum için o andaki canlı "Üst" fiyatı da sentetik olarak üretilir.
  Yalnızca maç sonuna kadar gözlenmiş maçlar (İY ve MS skoru dolu, gol dakikaları ikisiyle tutarlı)
  kullanılır; eksik kayıtlı bir maç için "gol olmadı" sonucu uydurulmaz.

Zaman sızıntısı yoktur: anlık durumun girdileri (skor, xG, fiyat) yalnızca o dakikaya kadar oluşmuş
bilgiden ve maç öncesi bilgiden türetilir; dakikadan sonraki goller yalnızca hedef değişkene (`outcome`)
girer. Bütün gürültüler fikstür kimliğiyle tohumlanır; aynı veritabanında aynı sonuç üretilir.

Oran izolasyonu: sentetik xG yalnızca futbol verisinden (geçmiş sonuçlar) kurulan maç öncesi
beklentiden türetilir; oranlardan hiçbir bilgi xG'ye taşınmaz. Canlı fiyat ise piyasa öncülü olarak
maç öncesi Alt/Üst 2,5 oranından çıkarılan toplam gol beklentisini kullanır (bkz. `_market_lambdas`).
Anlık durum sözlüklerinde fiyat alanı (`over_odds`) bulunduğundan MODEL A'ya bu sözlükler doğrudan
verilmez; radar yalnızca futbol alanlarını ayıklayarak MODEL A'ya iletir.
"""
from __future__ import annotations

import math
import sqlite3
from collections import defaultdict

import numpy as np

from ..db.database import insert_many, transaction
from ..domain.markets import MARKETS_BY_ID

def _fetchall(conn: sqlite3.Connection, sql: str, params: tuple = ()) -> list:
    """Sorguyu çalıştırır, bütün satırları okur ve imleci hemen kapatır (açık imleç bırakmaz)."""
    cur = conn.execute(sql, params)
    try:
        return cur.fetchall()
    finally:
        cur.close()


#: Varsayılan anlık durum dakikaları (5, 10, …, 75): normal sürede en az 15 dakika kalan dakikalar.
DEFAULT_MINUTES: tuple[int, ...] = tuple(range(5, 80, 5))
REGULAR_TIME = 90
HALF_TIME = 45

#: Canlı "Üst" fiyatlarının tipik marjı (sentetik canlı oran üreticisiyle aynı: synthetic._seed_live_odds).
LIVE_MARGIN = 0.06
#: Maç başına sentetik xG çarpanının log-normal gürültüsü (synthetic._seed_live_state ile aynı ölçek).
XG_NOISE_SIGMA = 0.25
#: Geçmiş canlı fiyatın kalan yoğunluk üzerindeki log-normal gürültüsü.
PRICE_NOISE_SIGMA = 0.10
#: Maç öncesi futbol beklentisinde takım oranlarının lig ortalamasına doğru büzülmesi (sözde maç sayısı).
PRIOR_GAMES = 5.0
#: Takım istatistiklerinin her yeni maçta uygulanan üstel unutma çarpanı (etkin bellek ≈ 33 maç).
TEAM_DECAY = 0.97
#: Lig ortalamaları için önsel (maç başına ev / deplasman golü) ve sözde maç sayısı.
PRIOR_HOME_GOALS = 1.5
PRIOR_AWAY_GOALS = 1.15
PRIOR_LEAGUE_MATCHES = 20.0


# ───────────────────────── gol dakikaları ─────────────────────────

def generate_goal_events(conn: sqlite3.Connection, as_of: str, seed: int = 11) -> int:
    """`as_of` anına kadar sonucu kaydedilmiş, gol dakikası olmayan sentetik maçlara gol dakikası ekler.

    İlk yarı golleri (İY skoru) 1–45, ikinci yarı golleri (MS − İY) 46–90 arasında düzgün dağılımla
    çekilir ve dakikaya göre sıralanır. Rastgelelik (seed, fikstür kimliği) ile tohumlandığından sonuç
    işlem sırasından bağımsızdır. İşlem idempotenttir: gol dakikası olan maçlar atlanır (0-0 biten
    maçlara hiç kayıt eklenmez). Dönüş: eklenen gol kaydı sayısı.
    """
    rows = _fetchall(
        conn,
        """
        SELECT f.id, f.ht_home, f.ht_away, f.ft_home, f.ft_away, f.result_recorded_at
        FROM fixtures f
        WHERE f.is_synthetic = 1 AND f.status = 'finished'
          AND f.result_recorded_at IS NOT NULL AND f.result_recorded_at <= ?
          AND f.ft_home IS NOT NULL AND f.ft_away IS NOT NULL
          AND f.ht_home IS NOT NULL AND f.ht_away IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM goal_events g WHERE g.fixture_id = f.id)
        ORDER BY f.id
        """,
        (as_of,),
    )
    out: list[dict] = []
    for fid, hth, hta, fth, fta, recorded in rows:
        sh, sa = fth - hth, fta - hta
        if min(hth, hta, sh, sa) < 0:
            continue  # tutarsız İY/MS skoru: gol dakikası üretilmez
        rng = np.random.default_rng([int(seed), int(fid)])
        goals: list[tuple[int, str]] = []
        for side, n_first, n_second in (("home", hth, sh), ("away", hta, sa)):
            goals += [(int(m), side) for m in rng.integers(1, HALF_TIME + 1, size=n_first)]
            goals += [(int(m), side) for m in rng.integers(HALF_TIME + 1, REGULAR_TIME + 1, size=n_second)]
        goals.sort()
        out.extend({"fixture_id": fid, "minute": m, "side": side, "recorded_at": recorded} for m, side in goals)
    if out:
        with transaction(conn):
            insert_many(conn, "goal_events", out)
    return len(out)


# ───────────────────────── geçmiş canlı anlık durumlar ─────────────────────────

def historical_snapshots(conn: sqlite3.Connection, cutoff: str,
                         minutes: tuple[int, ...] = DEFAULT_MINUTES, *, seed: int = 13) -> list[dict]:
    """Sonucu `cutoff` anına kadar kaydedilmiş sentetik maçların dakika anlık durumları.

    Her öğe bir sözlüktür:
      fixture_id, league_id, home_team_id, away_team_id, kickoff_at, result_recorded_at,
      minute, home_goals, away_goals (o dakikaya kadar), home_xg, away_xg (sentetik canlı xG),
      home_red, away_red (sentetik geçmişte kırmızı kart yok → 0), remaining_minutes,
      outcome (dakikadan sonra 90. dakikaya kadar en az bir gol → 1, yoksa 0),
      market_id (o skordaki "kalan sürede gol" marketi: OU_{toplam+0,5}; 5,5 üstü çizgi yoksa None),
      over_odds (o andaki sentetik canlı Üst fiyatı; market yoksa veya fiyatlanmadıysa None).

    Yalnızca normal süre sonuna kadar gözlenmiş, tam kaydı olan maçlar kullanılır ("gol yok" sonucu
    uydurulmaz):
      - maç sonu (MS) ve ilk yarı (İY) skoru dolu, İY ≤ MS (iki takım için ayrı ayrı);
      - gol dakikaları MS skoruyla (takım başına gol sayısı) VE İY skoruyla (45. dakikaya kadarki goller)
        tutarlı. 0-0 / İY 0-0 maçlar gol kaydı olmadan tutarlıdır.
    Örneğin demo canlı kupa maçları son canlı skorla, İY skoru ve gol dakikası olmadan bitirildiyse
    (gözlenmeyen dakikalar için sonuç bilinmez) hiç anlık durum üretmez. Sıralama: başlama saati, fikstür,
    dakika. Sonuç deterministiktir.
    """
    minutes = tuple(int(m) for m in minutes)
    # Tam sonucu (MS + tutarlı İY) olan maçlar; xG dayanağı (`_football_expectations`) da yalnızca
    # bunları kullanır. Gol dakikası tutarlılığı aşağıda, anlık durum üretilirken ayrıca denetlenir.
    fixtures = _fetchall(
        conn,
        """
        SELECT f.id, f.league_id, f.home_team_id, f.away_team_id, f.kickoff_at, f.result_recorded_at,
               f.ft_home, f.ft_away, f.ht_home, f.ht_away
        FROM fixtures f
        WHERE f.is_synthetic = 1 AND f.status = 'finished'
          AND f.result_recorded_at IS NOT NULL AND f.result_recorded_at <= ?
          AND f.ft_home IS NOT NULL AND f.ft_away IS NOT NULL
          AND f.ht_home IS NOT NULL AND f.ht_away IS NOT NULL
          AND f.ht_home <= f.ft_home AND f.ht_away <= f.ft_away
        ORDER BY f.kickoff_at, f.id
        """,
        (cutoff,),
    )
    if not fixtures:
        return []
    events: dict[int, list[tuple[int, str]]] = defaultdict(list)
    for fid, minute, side in _fetchall(
        conn,
        """
        SELECT g.fixture_id, g.minute, g.side FROM goal_events g JOIN fixtures f ON f.id = g.fixture_id
        WHERE f.is_synthetic = 1 AND f.result_recorded_at IS NOT NULL AND f.result_recorded_at <= ?
          AND g.recorded_at <= ?
        ORDER BY g.fixture_id, g.minute, g.id
        """,
        (cutoff, cutoff),
    ):
        events[fid].append((int(minute), side))
    football = _football_expectations([tuple(r[:8]) for r in fixtures])
    market = _market_lambdas(conn, cutoff)

    out: list[dict] = []
    for fid, league, home, away, ko, recorded, fth, fta, hth, hta in fixtures:
        ev = events.get(fid, [])
        if not _events_consistent(ev, fth, fta, hth, hta):
            continue  # gol dakikaları eksik veya İY/MS skoruyla tutarsız: maç sonuna kadar gözlenmemiş
        lh_pre, la_pre = football[fid]
        lam_mkt = market.get(fid)
        rng = np.random.default_rng([int(seed), int(fid)])
        mult_h, mult_a = np.exp(rng.normal(0.0, XG_NOISE_SIGMA, 2))
        price_noise = rng.normal(0.0, PRICE_NOISE_SIGMA, len(minutes))
        for j, m in enumerate(minutes):
            hg = sum(1 for gm, s in ev if gm <= m and s == "home")
            ag = sum(1 for gm, s in ev if gm <= m and s == "away")
            frac = min(max(m, 0), REGULAR_TIME) / REGULAR_TIME
            remaining = max(0, REGULAR_TIME - m)
            line = hg + ag + 0.5
            market_id = f"OU_{line}"
            if market_id not in MARKETS_BY_ID:
                market_id = None
            over_odds = None
            if market_id is not None and lam_mkt is not None:
                over_odds = live_over_price(lam_mkt * remaining / REGULAR_TIME * math.exp(price_noise[j]))
            out.append({
                "fixture_id": fid, "league_id": league, "home_team_id": home, "away_team_id": away,
                "kickoff_at": ko, "result_recorded_at": recorded, "minute": m,
                "home_goals": hg, "away_goals": ag,
                "home_xg": round(float(lh_pre * frac * mult_h), 2),
                "away_xg": round(float(la_pre * frac * mult_a), 2),
                "home_red": 0, "away_red": 0, "remaining_minutes": remaining,
                "outcome": int(any(m < gm <= REGULAR_TIME for gm, _ in ev)),
                "market_id": market_id, "over_odds": over_odds,
            })
    return out


def _events_consistent(events: list[tuple[int, str]], ft_home: int, ft_away: int, ht_home: int, ht_away: int) -> bool:
    """Gol dakikaları MS ve İY skoruyla tutarlı mı (takım başına toplam ve 45. dakikaya kadarki goller)."""
    for side, ft, ht in (("home", ft_home, ht_home), ("away", ft_away, ht_away)):
        mins = [m for m, s in events if s == side]
        if len(mins) != ft or sum(1 for m in mins if m <= HALF_TIME) != ht:
            return False
    return all(s in ("home", "away") and m >= 1 for m, s in events)


def live_over_price(lam_remaining: float, margin: float = LIVE_MARGIN) -> float | None:
    """Kalan gol yoğunluğundan canlı "kalan sürede gol" (Üst) fiyatı — sentetik canlı oran üreticisiyle aynı
    kural: p = 1 − e^(−λ), fiyat = 1 / min(0,995; p·(1+marj)), iki ondalık, en az 1,01; p ≤ 0,01 fiyatlanmaz."""
    if not math.isfinite(lam_remaining) or lam_remaining <= 0:
        return None
    p = 1.0 - math.exp(-lam_remaining)
    if p <= 0.01:
        return None
    return max(1.01, round(1.0 / min(0.995, p * (1.0 + margin)), 2))


def _football_expectations(fixtures: list) -> dict[int, tuple[float, float]]:
    """Her maç için yalnızca futbol verisinden (başlama anından önce kaydedilmiş sonuçlar) maç öncesi
    gol beklentisi (λ_ev, λ_dep). Sentetik xG'nin dayanağıdır; tahmin modeli değildir.

    Basit çarpımsal model: λ_ev = ev_ort · hücum_ev · savunma_dep, λ_dep = dep_ort · hücum_dep · savunma_ev.
    Takım oranları üstel unutmalı atılan/yenilen gollerden, PRIOR_GAMES sözde maçla lig ortalamasına
    doğru büzülerek hesaplanır.
    """
    queries = [(r[4], 1, i) for i, r in enumerate(fixtures)]          # başlama anında sorgu
    updates = [(r[5], 0, i) for i, r in enumerate(fixtures)]          # sonuç kaydında güncelleme
    league_tot: dict[str, list[float]] = defaultdict(lambda: [0.0, 0.0, 0.0])  # ev golü, dep golü, maç
    team: dict[int, list[float]] = defaultdict(lambda: [0.0, 0.0, 0.0])        # atılan, yenilen, maç
    out: dict[int, tuple[float, float]] = {}
    # Aynı anda kaydedilen sonuç, o anda başlayan maçtan önce işlenir (sonuç "bilinir": kayıt ≤ başlama).
    for _, kind, i in sorted(queries + updates):
        fid, league, home, away, _ko, _rec, fth, fta = fixtures[i]
        lt = league_tot[league]
        h_avg = (lt[0] + PRIOR_HOME_GOALS * PRIOR_LEAGUE_MATCHES) / (lt[2] + PRIOR_LEAGUE_MATCHES)
        a_avg = (lt[1] + PRIOR_AWAY_GOALS * PRIOR_LEAGUE_MATCHES) / (lt[2] + PRIOR_LEAGUE_MATCHES)
        g = 0.5 * (h_avg + a_avg)
        if kind == 1:
            th, ta = team[home], team[away]
            att_h = (th[0] + PRIOR_GAMES * g) / (th[2] * g + PRIOR_GAMES * g)
            def_h = (th[1] + PRIOR_GAMES * g) / (th[2] * g + PRIOR_GAMES * g)
            att_a = (ta[0] + PRIOR_GAMES * g) / (ta[2] * g + PRIOR_GAMES * g)
            def_a = (ta[1] + PRIOR_GAMES * g) / (ta[2] * g + PRIOR_GAMES * g)
            out[fid] = (h_avg * att_h * def_a, a_avg * att_a * def_h)
        else:
            lt[0] += fth
            lt[1] += fta
            lt[2] += 1
            for t, gf, ga in ((home, fth, fta), (away, fta, fth)):
                s = team[t]
                s[0] = s[0] * TEAM_DECAY + gf
                s[1] = s[1] * TEAM_DECAY + ga
                s[2] = s[2] * TEAM_DECAY + 1
    return out


def _market_lambdas(conn: sqlite3.Connection, cutoff: str) -> dict[int, float]:
    """Piyasa öncülü: başlama anından önce yakalanan son maç öncesi Alt/Üst 2,5 oranlarından (orantılı
    marj arındırma) çıkarılan toplam gol beklentisi λ; P(Poisson(λ) ≥ 3) = p_Üst denklemi ikiye bölmeyle
    çözülür. Geçmiş maçlarda gerçek gol yoğunluğu saklanmadığından canlı fiyatın dayanağı budur."""
    last: dict[tuple[int, str], float] = {}
    for fid, outcome, price in _fetchall(
        conn,
        """
        SELECT o.fixture_id, o.outcome_id, o.decimal_odds FROM odds_snapshots o JOIN fixtures f ON f.id = o.fixture_id
        WHERE o.market_id = 'OU_2.5' AND o.is_live = 0 AND o.captured_at <= f.kickoff_at AND o.captured_at <= ?
          AND f.is_synthetic = 1 AND f.status = 'finished'
          AND f.result_recorded_at IS NOT NULL AND f.result_recorded_at <= ?
        ORDER BY o.captured_at, o.id
        """,
        (cutoff, cutoff),
    ):
        last[(fid, outcome)] = float(price)
    out: dict[int, float] = {}
    for (fid, outcome), over in last.items():
        if outcome != "OVER" or (fid, "UNDER") not in last:
            continue
        inv_o, inv_u = 1.0 / over, 1.0 / last[(fid, "UNDER")]
        out[fid] = _lambda_for_over25(inv_o / (inv_o + inv_u))
    return out


def _lambda_for_over25(p_over: float) -> float:
    p_over = min(max(p_over, 0.01), 0.99)
    lo, hi = 0.05, 10.0
    for _ in range(60):
        lam = 0.5 * (lo + hi)
        p = 1.0 - math.exp(-lam) * (1.0 + lam + 0.5 * lam * lam)
        if p < p_over:
            lo = lam
        else:
            hi = lam
    return 0.5 * (lo + hi)


__all__ = ["generate_goal_events", "historical_snapshots", "live_over_price", "DEFAULT_MINUTES", "LIVE_MARGIN"]
