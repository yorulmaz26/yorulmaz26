"""Son 15 Dakika Radarı — API-Football canlı verisiyle iki pencerede gol analizi.

Kullanıcı talebi: "canlı veriler için ilk yarı son 15 dk ve ikinci yarı son 15 dakika için gol analizi yap
canlı radar oluştur. Günlük 100 istek ile sınırlı olduğu için gol olma ihtimali yüzde 90 ve üstü ise istek
gönder bildirim gönder."

Pencereler
----------
- H1: ilk yarının son 15 dakikası (31'–45+'). Hedef: değerlendirme anından ilk yarı bitene kadar en az bir gol.
- H2: maçın son 15 dakikası (76'–90+'). Hedef: değerlendirme anından normal süre bitene kadar en az bir gol.
Değerlendirme pencerenin başında yapılır (H1 ≈ 31', H2 ≈ 76'); kalan süre olasılığa doğrudan girer.

İstek kapıları (günlük bütçe korunur)
-------------------------------------
1. Kapı (istek yok): maç öncesi MODEL A ile her maç ve pencere için "eşiğe ulaşma şansı" hesaplanır:
   pencere başına kadar atılabilecek gol sayısının dağılımı (gama–Poisson) üzerinden, canlı MODEL A
   olasılığının eşiğe (varsayılan %90) ulaşma olasılığı. Bu şans `min_reach`'in (varsayılan %1) altındaysa o
   maç için istek GÖNDERİLMEZ.
2. Kapı (1 istek): pencere başladığında tek `/fixtures?live=...` isteği o anda oynanan bütün maçları getirir;
   canlı skor, dakika ve kırmızı kartlarla MODEL A pencere olasılığı hesaplanır.
3. Kapı (1 istek daha): yalnızca MODEL A olasılığı eşiğe ulaştıysa (≥) canlı oran (`/odds/live`) istenir;
   MODEL B hesaplanır. MODEL A eşiğin altındaysa oran isteği harcanmaz.
Bildirim yalnızca bütün şartlar sağlanınca verilir (aşağıda). Günlük sınır ve yedek aşılmaz.

MODEL A (yalnızca futbol verisi)
--------------------------------
Maç öncesi λ_ev, λ_dep ve ilk yarı payı h, seçim sürecinin son maç öncesi değerlendirmesinden
(`fixture_evaluations.model_a`) okunur. Pencere yoğunluğu
    λ_pencere = (λ_ev + λ_dep) · yarı_payı · pencere_payı · kalan_oran · kırmızı_kart_çarpanları
yarı_payı = h (H1) veya 1 − h (H2); pencere_payı = WINDOW_SHARE_OF_HALF (yarının gollerinin son 15 dakika +
uzatmaya düşen payı, yaklaşık literatür değeri; veri biriktikçe yeniden kestirilmelidir — VARSAYIMLAR §8);
kalan_oran = (bitiş + ortalama uzatma − dakika) / (15 + ortalama uzatma). Tempo belirsizliği canlı radarla
aynı gama–Poisson modeliyle işlenir: u ~ Gama(κ, κ), şimdiye kadarki goller G ve beklenen E ile güncellenir:
    p_A = 1 − (1 + λ_pencere / (κ + E))^(−(κ + G))
MODEL A'ya oran alanı giremez (`assert_football_only`).

MODEL B (futbol verisi + MODEL A + tahmin anında bilinen canlı oran)
--------------------------------------------------------------------
H1 için "Over/Under (1st Half)" (market 49), H2 için "Match Goals" (market 25) marketinin
(mevcut gol + 0,5) çizgisindeki Üst/Alt fiyatlarından marjı ayrılmış piyasa olasılığı p_piyasa hesaplanır.
    logit p_B = c0 + c1·logit(p_A) + c2·logit(p_piyasa),  önsel (0, 1, 0)
Katsayılar yalnızca bu radarın sonuçlanmış geçmiş değerlendirmeleriyle (değerlendirme gününden önce)
eğitilir; örnek sayısı `min_model_b_samples`'tan azsa MODEL B geçersizdir ve bildirim verilmez.

Bildirim şartları (hepsi gerekli; seçim modları bunları değiştiremez)
---------------------------------------------------------------------
MODEL A ≥ eşik, MODEL B ≥ eşik, pencerede en az `min_remaining_minutes` dakika, canlı veri ≤ 60 sn, canlı
oran ≤ 60 sn ve askıda değil, nadir olay kalibrasyonu (iki model; birim maç; ≥ `min_rare_event_samples`
maç), Kalite Kapısı (maç öncesi MODEL A geçerli ve güncel, eşleşme güvenilir), bildirim izni, mükerrer
bildirim engeli (maç + pencere başına tek bildirim).
"""
from __future__ import annotations

import contextlib
import math
import re
import sqlite3
import threading
import unicodedata
from dataclasses import dataclass
from datetime import datetime, timezone
from difflib import SequenceMatcher
from typing import Any, Callable

import numpy as np

from ..config import SETTINGS, Settings
from ..data import api_football as apif
from ..db.database import dumps, from_iso, insert, iso_add, loads, to_iso, transaction, utcnow
from ..leagues import display_name
from ..models.isolation import assert_football_only
from .live_goal_radar import (DEFAULT_DISPERSION, RED_CARD_OPPONENT_FACTOR, RED_CARD_OWN_FACTOR,
                              model_b_design, no_goal_probability, notification_permission_granted,
                              rare_event_summary, remaining_share, train_live_model_b)

SETTINGS_KEY = "window_radar"
DEFAULT_CONFIG: dict[str, Any] = {
    "enabled": True,
    #: Kullanıcı talebi: gol olasılığı %90 ve üstü (≥).
    "threshold": 0.90,
    "daily_limit": 100,
    #: Elle kontrol veya beklenmedik durumlar için dokunulmayan istek yedeği.
    "reserve": 5,
    #: 1. kapı: canlı olasılığın eşiğe ulaşma şansı bundan düşükse istek gönderilmez.
    "min_reach": 0.01,
    #: Bildirim için pencerede kalması gereken en az süre (dakika, ortalama uzatma dahil).
    "min_remaining_minutes": 10,
}
THRESHOLD_RANGE = (0.30, 0.99)

#: Yarının gollerinin son 15 dakika + uzatmaya düşen payı (yaklaşık; üst lig gol dakikası dağılımları:
#: golların ~%19'u 31'–45+', ~%23'ü 76'–90+' aralığında; ilk yarı payı ~%44).
WINDOW_SHARE_OF_HALF = {"H1": 0.42, "H2": 0.41}
#: Ortalama uzatma (dakika).
MEAN_STOPPAGE = {"H1": 2.0, "H2": 4.0}
#: Kalibrasyon için tek eşik: kesin büyüktür kullanan özet fonksiyonuna "≥" anlamı vermek için.
_GE_EPS = 1e-9
#: Aynı istekle toplanacak hedefler için bekleme (dakika).
BATCH_MINUTES = 4
#: Hedef başına en fazla deneme (gecikmeli başlama, uzun devre arası).
MAX_ATTEMPTS = 3
#: Planlama ufku.
PLAN_PAST_HOURS, PLAN_AHEAD_HOURS = 3, 36
MIN_LINK_SIMILARITY = 0.62


@dataclass(frozen=True)
class Window:
    code: str
    period: str          # API-Football durum kodu ('1H' / '2H')
    start: int           # pencere başlangıç dakikası (bu dakikadan sonra)
    end: int             # normal süre sonu dakikası
    nominal_offset: int  # başlama saatinden pencere başlangıcına tahmini süre (dakika)
    market_id: int
    market_name: str
    label: str
    target: str

    @property
    def length(self) -> float:
        return (self.end - self.start) + MEAN_STOPPAGE[self.code]


WINDOWS: dict[str, Window] = {
    "H1": Window("H1", "1H", 30, 45, 31, apif.MARKET_FIRST_HALF_GOALS, "Over/Under (1st Half)",
                 "İlk yarının son 15 dakikası (31'–45+')", "İlk yarı bitmeden en az bir gol"),
    "H2": Window("H2", "2H", 75, 90, 93, apif.MARKET_MATCH_GOALS, "Match Goals",
                 "Maçın son 15 dakikası (76'–90+')", "Normal süre bitmeden en az bir gol"),
}

CONDITION_LABELS = {
    "model_a_threshold": "MODEL A ≥ eşik",
    "model_b_threshold": "MODEL B ≥ eşik",
    "remaining_time": "Pencerede yeterli süre",
    "live_data_fresh": "Canlı veri ≤ 60 sn",
    "live_odds_fresh": "Canlı oran ≤ 60 sn ve askıda değil",
    "rare_event_calibration": "Nadir olay kalibrasyonu (iki model)",
    "quality_gate": "Tahmin Kalite Kapısı",
    "notification_permission": "Bildirim izni",
    "duplicate_block": "Mükerrer bildirim engeli",
}

_TICK_GUARD = threading.Lock()


def _pct(x: float | None) -> str:
    return "—" if x is None else f"%{100 * x:.1f}".replace(".", ",")


# ───────────────────────── ayarlar ─────────────────────────

def get_config(conn: sqlite3.Connection) -> dict:
    row = conn.execute("SELECT value FROM app_settings WHERE key=?", (SETTINGS_KEY,)).fetchone()
    cfg = dict(DEFAULT_CONFIG)
    if row is not None:
        stored = loads(row[0], {}) or {}
        cfg.update({k: stored[k] for k in DEFAULT_CONFIG if k in stored})
    return cfg


def update_config(conn: sqlite3.Connection, changes: dict, now: str) -> dict:
    """Ayarları doğrulayıp kaydeder. Geçersiz değer → ValueError (Türkçe metin)."""
    cfg = get_config(conn)
    for key, value in changes.items():
        if value is None:
            continue
        if key == "enabled":
            cfg[key] = bool(value)
        elif key == "threshold":
            v = float(value)
            if not (THRESHOLD_RANGE[0] <= v <= THRESHOLD_RANGE[1]) or not math.isfinite(v):
                raise ValueError(f"Eşik %{THRESHOLD_RANGE[0] * 100:.0f} ile %{THRESHOLD_RANGE[1] * 100:.0f} arasında olmalı.")
            cfg[key] = round(v, 4)
        elif key == "daily_limit":
            v = int(value)
            if not 1 <= v <= 100_000:
                raise ValueError("Günlük istek sınırı 1 ile 100000 arasında olmalı.")
            cfg[key] = v
        elif key == "reserve":
            v = int(value)
            if not 0 <= v <= 50:
                raise ValueError("Yedek istek sayısı 0 ile 50 arasında olmalı.")
            cfg[key] = v
        else:
            raise ValueError(f"Bilinmeyen ayar: {key}")
    if cfg["reserve"] >= cfg["daily_limit"]:
        raise ValueError("Yedek, günlük sınırdan küçük olmalı.")
    with transaction(conn):
        conn.execute("INSERT INTO app_settings(key, value, updated_at) VALUES (?,?,?) ON CONFLICT(key) DO UPDATE"
                     " SET value=excluded.value, updated_at=excluded.updated_at", (SETTINGS_KEY, dumps(cfg), now))
        # Henüz istek harcanmamış hedefler yeni eşikle yeniden planlanır.
        conn.execute("UPDATE window_radar_targets SET threshold=-1 WHERE status IN ('pending','gated') AND attempts=0")
    plan(conn, now, cfg)
    return cfg


# ───────────────────────── MODEL A (pencere) ─────────────────────────

def window_intensity(lam_home: float, lam_away: float, ht_share: float, window: Window, minute: float,
                     home_red: int = 0, away_red: int = 0) -> tuple[float, float]:
    """(λ_pencere, kalan_dakika): değerlendirme anından devre sonuna kadarki beklenen gol ve kalan süre."""
    half = ht_share if window.code == "H1" else 1.0 - ht_share
    t = max(float(minute), float(window.start))
    remaining = min(max(window.end + MEAN_STOPPAGE[window.code] - t, 0.0), window.length)
    frac = remaining / window.length
    base = half * WINDOW_SHARE_OF_HALF[window.code] * frac
    hr, ar = int(home_red or 0), int(away_red or 0)
    lh = lam_home * base * RED_CARD_OWN_FACTOR ** hr * RED_CARD_OPPONENT_FACTOR ** ar
    la = lam_away * base * RED_CARD_OWN_FACTOR ** ar * RED_CARD_OPPONENT_FACTOR ** hr
    return lh + la, remaining


def expected_goals_before(lam_total: float, ht_share: float, minute: float) -> float:
    """Maç öncesi beklentiye göre `minute`'a kadar beklenen gol (E)."""
    return lam_total * (1.0 - remaining_share(min(float(minute), 90.0), ht_share))


def model_a_window_probability(lam_home: float, lam_away: float, ht_share: float, window: Window,
                               state: dict, *, dispersion: float = DEFAULT_DISPERSION) -> tuple[float, float]:
    """MODEL A (canlı, pencere): (olasılık, kalan_dakika). `state` yalnızca futbol verisi içerir."""
    assert_football_only([state], context="MODEL A (pencere)")
    minute = float(state["minute"]) + float(state.get("extra_minute") or 0)
    lam, remaining = window_intensity(lam_home, lam_away, ht_share, window, minute,
                                      state.get("home_red", 0), state.get("away_red", 0))
    goals = int(state.get("home_goals", 0)) + int(state.get("away_goals", 0))
    expected = expected_goals_before(lam_home + lam_away, ht_share, min(minute, window.end))
    return float(1.0 - no_goal_probability(lam, dispersion, goals, expected)), remaining


def prematch_window_probability(lam_home: float, lam_away: float, ht_share: float, window: Window, *,
                                dispersion: float = DEFAULT_DISPERSION) -> float:
    """Maç başlamadan (skor bilinmeden) pencerenin tamamında gol olasılığı: 1 − (1 + λ/κ)^(−κ)."""
    lam, _ = window_intensity(lam_home, lam_away, ht_share, window, window.start)
    return float(1.0 - no_goal_probability(lam, dispersion))


def reach_probability(lam_home: float, lam_away: float, ht_share: float, window: Window, threshold: float, *,
                      dispersion: float = DEFAULT_DISPERSION, max_goals: int = 40) -> float:
    """1. kapı: pencere başında canlı MODEL A olasılığının eşiğe ulaşma (≥) olasılığı.

    Pencere başına kadarki gol sayısı G, tempo u ~ Gama(κ, κ) altında negatif binom dağılır:
    P(G = g) = Γ(κ+g)/(Γ(κ) g!) · (κ/(κ+E))^κ · (E/(κ+E))^g. Canlı olasılık G ile arttığından şans
    P(G ≥ g*)'dır; g*, p_A(G) ≥ eşik olan en küçük gol sayısıdır. Kırmızı kart etkisi ihmal edilir.
    """
    lam, _ = window_intensity(lam_home, lam_away, ht_share, window, window.start)
    e = expected_goals_before(lam_home + lam_away, ht_share, window.start)
    k = float(dispersion)
    g_star = None
    for g in range(max_goals + 1):
        if 1.0 - float(no_goal_probability(lam, k, g, e)) >= threshold:
            g_star = g
            break
    if g_star is None:
        return 0.0
    if g_star == 0:
        return 1.0
    log_q, log_r = math.log(k / (k + e)), math.log(e / (k + e)) if e > 0 else -math.inf
    below = 0.0
    for g in range(g_star):
        below += math.exp(math.lgamma(k + g) - math.lgamma(k) - math.lgamma(g + 1) + k * log_q + g * log_r)
    return float(min(max(1.0 - below, 0.0), 1.0))


# ───────────────────────── maç öncesi MODEL A girdileri ─────────────────────────

def _prematch_inputs(conn: sqlite3.Connection, fixture_id: int, settings: Settings) -> dict:
    """Son maç öncesi değerlendirmeden λ'lar, ilk yarı payı ve Kalite Kapısı durumu."""
    row = conn.execute(
        "SELECT fe.model_a, fe.freshness, fe.data_cutoff_at, f.kickoff_at FROM fixture_evaluations fe"
        " JOIN prediction_runs r ON r.id = fe.run_id JOIN fixtures f ON f.id = fe.fixture_id"
        " WHERE fe.fixture_id=? AND r.kind='prematch' AND fe.data_cutoff_at <= f.kickoff_at"
        " ORDER BY fe.run_id DESC LIMIT 1", (fixture_id,)).fetchone()
    if row is None:
        return {"ok": False, "reason": "Maç öncesi MODEL A değerlendirmesi yok"}
    model = loads(row[0], {}) or {}
    fresh = loads(row[1], {}) or {}
    lam = model.get("lambda") or []
    ht = model.get("ht_share")
    if len(lam) != 2 or not all(isinstance(x, (int, float)) and math.isfinite(x) and x > 0 for x in lam):
        return {"ok": False, "reason": "MODEL A beklenen gol değerleri geçersiz"}
    if not isinstance(ht, (int, float)) or not 0.2 <= ht <= 0.8:
        ht = 0.45
    out = {"lam_home": float(lam[0]), "lam_away": float(lam[1]), "ht_share": float(ht), "ok": True, "reason": None}
    dp = settings.data
    tm = fresh.get("team_matches") or []
    last = fresh.get("last_result_at")
    if len(tm) == 2 and min(tm) < dp.min_team_matches:
        out.update(ok=False, reason=f"MODEL A takım verisi yetersiz ({tm[0]}/{tm[1]} maç)")
    elif last and last < iso_add(row[3], days=-dp.max_result_data_age_days):
        out.update(ok=False, reason=f"MODEL A sonuç verisi eski (son sonuç {last[:10]})")
    return out


# ───────────────────────── planlama (istek göndermez) ─────────────────────────

def plan(conn: sqlite3.Connection, now: str, cfg: dict | None = None, settings: Settings = SETTINGS) -> int:
    """Yaklaşan ve süren maçlar için pencere hedeflerini oluşturur/günceller. İstek göndermez."""
    cfg = cfg or get_config(conn)
    thr, min_reach = float(cfg["threshold"]), float(cfg["min_reach"])
    leagues = tuple(apif.APIF_LEAGUE_IDS)
    rows = conn.execute(
        f"SELECT id, kickoff_at FROM fixtures WHERE status IN ('scheduled','live') AND kickoff_at BETWEEN ? AND ?"
        f" AND league_id IN ({','.join('?' * len(leagues))})",
        (iso_add(now, hours=-PLAN_PAST_HOURS), iso_add(now, hours=PLAN_AHEAD_HOURS), *leagues)).fetchall()
    existing = {(r[0], r[1]): (r[2], r[3], r[4]) for r in conn.execute(
        "SELECT fixture_id, window, status, attempts, threshold FROM window_radar_targets")}
    n = 0
    with transaction(conn):
        for fid, kickoff in rows:
            inputs = None
            for w in WINDOWS.values():
                prev = existing.get((fid, w.code))
                if prev is not None and (prev[0] in ("evaluated", "missed") or prev[1] > 0 or
                                         (prev[0] != "no_model" and abs(prev[2] - thr) < 1e-9)):
                    continue
                inputs = inputs or _prematch_inputs(conn, fid, settings)
                due = iso_add(kickoff, minutes=w.nominal_offset)
                if not inputs["ok"]:
                    # Kalite Kapısı'ndan geçemeyecek maç için istek harcanmaz.
                    status, pre, reach = "no_model", None, None
                    note = f"İstek gönderilmeyecek: {inputs['reason']}"
                else:
                    pre = prematch_window_probability(inputs["lam_home"], inputs["lam_away"], inputs["ht_share"], w)
                    reach = reach_probability(inputs["lam_home"], inputs["lam_away"], inputs["ht_share"], w, thr)
                    if reach >= min_reach:
                        status, note = "pending", (f"Eşiğe ulaşma şansı {_pct(reach)} ≥ {_pct(min_reach)}: "
                                                   f"pencere başında 1 canlı istek planlandı")
                    else:
                        status, note = "gated", (f"İstek gönderilmeyecek: canlı gol olasılığının %{thr * 100:.0f} "
                                                 f"eşiğine ulaşma şansı {_pct(reach)} (en az {_pct(min_reach)} gerekir)")
                conn.execute(
                    "INSERT INTO window_radar_targets(fixture_id, window, due_at, prematch_probability,"
                    " reach_probability, threshold, status, attempts, note, updated_at) VALUES (?,?,?,?,?,?,?,0,?,?)"
                    " ON CONFLICT(fixture_id, window) DO UPDATE SET due_at=excluded.due_at,"
                    " prematch_probability=excluded.prematch_probability, reach_probability=excluded.reach_probability,"
                    " threshold=excluded.threshold, status=excluded.status, note=excluded.note,"
                    " updated_at=excluded.updated_at",
                    (fid, w.code, due, pre, reach, thr, status, note, now))
                n += 1
    return n


# ───────────────────────── takım adı eşleştirme ─────────────────────────

_STOP = {"fc", "sk", "fk", "ac", "as", "cf", "sc", "afc", "cd", "ud", "rc", "club", "sv", "vfb", "vfl", "tsg",
         "ss", "us", "ssc", "kv", "krc", "bk", "if", "aik", "sl", "jk", "1", "de", "the", "and"}
_ALIASES = {
    "buyuksehyr": "basaksehir", "man united": "manchester united", "man city": "manchester city",
    "nott m forest": "nottingham forest", "ath madrid": "atletico madrid", "ath bilbao": "athletic bilbao",
    "ein frankfurt": "eintracht frankfurt", "m gladbach": "monchengladbach", "paris sg": "paris saint germain",
    "espanol": "espanyol", "sociedad": "real sociedad", "vallecano": "rayo vallecano",
    "la coruna": "deportivo la coruna", "st etienne": "saint etienne", "ad demirspor": "adana demirspor",
    "wolves": "wolverhampton", "goztep": "goztepe", "sheffield weds": "sheffield wednesday",
    "qpr": "queens park rangers", "west brom": "west bromwich", "bayern munich": "bayern munchen",
    "fc koln": "koln", "leverkusen": "bayer leverkusen", "inter": "inter milan", "milan": "ac milan",
}


def normalize_team(name: str) -> str:
    s = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode("ascii").lower()
    s = re.sub(r"[^a-z0-9]+", " ", s.replace("'", " ")).strip()
    s = _ALIASES.get(s, s)
    toks = [t for t in s.split() if t not in _STOP]
    return " ".join(toks) or s


def team_similarity(a: str, b: str) -> float:
    na, nb = normalize_team(a), normalize_team(b)
    if not na or not nb:
        return 0.0
    if na == nb:
        return 1.0
    ca, cb = na.replace(" ", ""), nb.replace(" ", "")
    ratio = SequenceMatcher(None, ca, cb).ratio()
    short, long_ = (ca, cb) if len(ca) <= len(cb) else (cb, ca)
    contain = 0.9 if len(short) >= 4 and short in long_ else 0.0
    ta, tb = na.split(), nb.split()
    st, lt = (ta, tb) if len(ta) <= len(tb) else (tb, ta)
    hits = sum(1 for t in st if len(t) >= 3 and any(u.startswith(t) or t.startswith(u) for u in lt if len(u) >= 3))
    token = 0.85 * hits / len(st) if st else 0.0
    return float(max(ratio, contain, token))


def link_fixture(conn: sqlite3.Connection, item: dict, now: str) -> tuple[int | None, float]:
    """API-Football maçını yerel maça bağlar (lig + ±3 saat + iki takım adı benzerliği)."""
    apif_id = int(item["fixture"]["id"])
    row = conn.execute("SELECT fixture_id, similarity FROM apif_fixture_links WHERE apif_fixture_id=?",
                       (apif_id,)).fetchone()
    if row is not None:
        return int(row[0]), float(row[1])
    league = apif.LEAGUE_BY_APIF_ID.get(int(item["league"]["id"]))
    kickoff = _parse_api_time(item["fixture"].get("date"))
    if league is None or kickoff is None:
        return None, 0.0
    home, away = item["teams"]["home"]["name"], item["teams"]["away"]["name"]
    cands = conn.execute(
        "SELECT f.id, th.name, ta.name FROM fixtures f JOIN teams th ON th.id=f.home_team_id"
        " JOIN teams ta ON ta.id=f.away_team_id WHERE f.league_id=? AND f.kickoff_at BETWEEN ? AND ?"
        " AND f.status IN ('scheduled','live','finished')",
        (league, iso_add(kickoff, hours=-3), iso_add(kickoff, hours=3))).fetchall()
    scored = []
    for fid, h, a in cands:
        sh, sa = team_similarity(home, h), team_similarity(away, a)
        scored.append((min(sh, sa), (sh + sa) / 2, int(fid)))
    scored.sort(reverse=True)
    if not scored or scored[0][0] < 0.5 or scored[0][1] < MIN_LINK_SIMILARITY:
        return None, scored[0][1] if scored else 0.0
    if len(scored) > 1 and scored[1][1] > scored[0][1] - 0.08 and scored[0][1] < 0.95:
        return None, scored[0][1]  # belirsiz eşleşme: bağlanmaz
    best = scored[0]
    with transaction(conn):
        conn.execute("INSERT OR IGNORE INTO apif_fixture_links(apif_fixture_id, fixture_id, apif_home, apif_away,"
                     " similarity, linked_at) VALUES (?,?,?,?,?,?)", (apif_id, best[2], home, away, best[1], now))
    return best[2], best[1]


def _parse_api_time(s: str | None) -> str | None:
    if not s:
        return None
    try:
        dt = datetime.fromisoformat(s.replace("Z", "+00:00"))
    except ValueError:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return to_iso(dt)


# ───────────────────────── canlı yanıt ayrıştırma ─────────────────────────

def parse_live_item(item: dict) -> dict:
    """`/fixtures?live` öğesinden yalnızca futbol verisi: dakika, skor, kırmızı kart, devre skoru."""
    st = item["fixture"]["status"]
    reds = {"home": 0, "away": 0}
    home_id = item["teams"]["home"]["id"]
    for ev in item.get("events") or []:
        if ev.get("type") == "Card" and ev.get("detail") == "Red Card":
            side = "home" if (ev.get("team") or {}).get("id") == home_id else "away"
            reds[side] += 1
    ht = (item.get("score") or {}).get("halftime") or {}
    ft = (item.get("score") or {}).get("fulltime") or {}
    return {
        "apif_fixture_id": int(item["fixture"]["id"]), "status": st.get("short"),
        "minute": int(st.get("elapsed") or 0), "extra_minute": int(st.get("extra") or 0),
        "home_goals": int((item.get("goals") or {}).get("home") or 0),
        "away_goals": int((item.get("goals") or {}).get("away") or 0),
        "home_red": reds["home"], "away_red": reds["away"],
        "ht_home": ht.get("home"), "ht_away": ht.get("away"), "ft_home": ft.get("home"), "ft_away": ft.get("away"),
    }


def current_window(state: dict) -> str | None:
    """Maçın şu anda içinde bulunduğu pencere (yoksa None)."""
    if state["status"] == "1H" and state["minute"] >= WINDOWS["H1"].start:
        return "H1"
    if state["status"] == "2H" and state["minute"] >= WINDOWS["H2"].start:
        return "H2"
    return None


def market_probability(odds_item: dict | None, window: Window, goals_line: int, now: str,
                       max_age: int) -> dict:
    """Canlı oran öğesinden (mevcut gol + 0,5) çizgisinde marjı ayrılmış Üst olasılığı ve tazelik."""
    if odds_item is None:
        return {"ok": False, "reason": "Canlı oran listesinde bu maç yok", "probability": None}
    st = odds_item.get("status") or {}
    if st.get("stopped") or st.get("blocked") or st.get("finished"):
        return {"ok": False, "reason": "Canlı oran askıya alınmış", "probability": None}
    updated = _parse_api_time(odds_item.get("update"))
    age = None if updated is None else (from_iso(now) - from_iso(updated)).total_seconds()
    line = f"{goals_line + 0.5:g}"
    over = under = None
    for bet in odds_item.get("odds") or []:
        if int(bet.get("id", -1)) != window.market_id:
            continue
        for v in bet.get("values") or []:
            if str(v.get("handicap")) != line or v.get("suspended"):
                continue
            try:
                price = float(v.get("odd"))
            except (TypeError, ValueError):
                continue
            if price > 1.0:
                if v.get("value") == "Over":
                    over = price
                elif v.get("value") == "Under":
                    under = price
    if over is None or under is None:
        return {"ok": False, "reason": f"{window.market_name} {line} çizgisinde Üst/Alt fiyatı yok",
                "probability": None, "updated_at": updated}
    p = (1 / over) / (1 / over + 1 / under)
    fresh = age is not None and -5 <= age <= max_age
    return {"ok": fresh, "probability": float(p), "over": over, "under": under, "line": line,
            "updated_at": updated, "age_seconds": age,
            "reason": None if fresh else f"Canlı oran eski ({'bilinmiyor' if age is None else f'{age:.0f} sn'})"}


# ───────────────────────── geçmiş: MODEL B eğitimi ve nadir olay kalibrasyonu ─────────────────────────

def _history(conn: sqlite3.Connection, now: str, threshold: float, settings: Settings) -> dict:
    cutoff = now[:10] + "T00:00:00Z"
    rows = conn.execute(
        "SELECT fixture_id, model_a_probability, market_probability, model_b_probability, outcome"
        " FROM window_radar_evaluations WHERE outcome IS NOT NULL AND evaluated_at < ?", (cutoff,)).fetchall()
    g = np.array([r[0] for r in rows], dtype=np.int64)
    pa = np.array([r[1] for r in rows], float)
    y = np.array([r[4] for r in rows], float)
    with_m = np.array([r[2] is not None for r in rows], bool)
    pm = np.array([r[2] if r[2] is not None else np.nan for r in rows], float)
    pb = np.array([r[3] if r[3] is not None else np.nan for r in rows], float)
    min_b = settings.stats.min_model_b_samples
    fit = None
    if with_m.sum() >= min_b:
        fit = train_live_model_b(np.clip(pa[with_m], 1e-6, 1 - 1e-6), np.clip(pm[with_m], 1e-6, 1 - 1e-6),
                                 y[with_m], min_samples=min_b)
    min_rare = settings.radar.min_rare_event_samples
    cal_a = rare_event_summary("A", pa, y, g, threshold=threshold - _GE_EPS, min_samples=min_rare,
                               data_cutoff_at=cutoff) if rows else None
    cal_b = rare_event_summary("B", pb, y, g, threshold=threshold - _GE_EPS, min_samples=min_rare,
                               data_cutoff_at=cutoff) if rows else None
    return {"cutoff": cutoff, "settled": len(rows), "with_market": int(with_m.sum()), "model_b": fit,
            "model_b_min_samples": min_b, "cal_a": cal_a, "cal_b": cal_b, "min_rare": min_rare}


def _cal_text(cal, model: str, threshold: float, min_rare: int) -> tuple[bool, str]:
    if cal is None or cal.n == 0:
        return False, (f"MODEL {model}: %{threshold * 100:.0f} ve üstü bölgede sonuçlanmış geçmiş örnek yok "
                       f"(en az {min_rare} maç gerekir)")
    return bool(cal.adequate), cal.detail()


# ───────────────────────── sonuçlandırma ─────────────────────────

def settle(conn: sqlite3.Connection, now: str, live_states: dict[int, dict] | None = None) -> int:
    """Pencere değerlendirmelerinin sonucunu yazar (bir kez): canlı yanıttaki devre/maç sonu skoru veya
    veritabanındaki kayıtlı sonuç (football-data.co.uk güncellemesi)."""
    rows = conn.execute(
        "SELECT e.id, e.fixture_id, e.apif_fixture_id, e.window, e.home_goals + e.away_goals,"
        " f.ht_home, f.ht_away, f.ft_home, f.ft_away, f.result_recorded_at FROM window_radar_evaluations e"
        " JOIN fixtures f ON f.id = e.fixture_id WHERE e.outcome IS NULL").fetchall()
    n = 0
    with transaction(conn):
        for eid, fid, apid, window, goals_at, hth, hta, fth, fta, rec in rows:
            total, by = None, None
            live = (live_states or {}).get(apid)
            if window == "H1":
                if live and live["status"] not in ("1H", "NS", "TBD") and live["ht_home"] is not None:
                    total, by = live["ht_home"] + live["ht_away"], "api-football"
                elif rec and hth is not None and hta is not None:
                    total, by = hth + hta, "sonuç kaydı"
            else:
                if live and live["status"] in ("FT", "AET", "PEN") and live["ft_home"] is not None:
                    total, by = live["ft_home"] + live["ft_away"], "api-football"
                elif rec and fth is not None and fta is not None:
                    total, by = fth + fta, "sonuç kaydı"
            if total is None:
                continue
            conn.execute("UPDATE window_radar_evaluations SET outcome=?, settled_at=?, settled_by=? WHERE id=?",
                         (1 if total > goals_at else 0, now, by, eid))
            n += 1
    return n


# ───────────────────────── tik: planla → (gerekirse) iste → değerlendir ─────────────────────────

def _due_targets(conn: sqlite3.Connection, now: str) -> list[sqlite3.Row]:
    return conn.execute(
        "SELECT t.fixture_id, t.window, t.due_at, t.attempts, f.league_id, f.kickoff_at FROM window_radar_targets t"
        " JOIN fixtures f ON f.id=t.fixture_id WHERE t.status='pending' ORDER BY t.due_at").fetchall()


def tick(conn: sqlite3.Connection, client: apif.Client | None, now: str | None = None, *,
         lock: Any = None, settings: Settings = SETTINGS, user_id: str = "local",
         clock: Callable[[], str] | None = None) -> dict:
    """Bir radar adımı. Ağ istekleri `lock` dışında yapılır; veritabanı işlemleri `lock` altında.

    Dönüş: {'requests': gönderilen istek sayısı, 'evaluations': [...], 'alerts': [...], 'note': ...}
    """
    clock = clock or apif.now_iso
    now = now or clock()
    lock = lock if lock is not None else contextlib.nullcontext()
    if not _TICK_GUARD.acquire(blocking=False):
        return {"requests": 0, "note": "Önceki adım sürüyor"}
    try:
        return _tick(conn, client, now, lock, settings, user_id, clock)
    finally:
        _TICK_GUARD.release()


def _tick(conn, client, now, lock, settings, user_id, clock) -> dict:
    out: dict[str, Any] = {"requests": 0, "evaluations": [], "alerts": [], "note": None}
    with lock:
        cfg = get_config(conn)
        if not cfg["enabled"]:
            out["note"] = "Radar kapalı"
            return out
        plan(conn, now, cfg, settings)
        settle(conn, now)
        stale = iso_add(now, minutes=-25)
        with transaction(conn):
            conn.execute("UPDATE window_radar_targets SET status='missed', note=?, updated_at=? WHERE status='pending'"
                         " AND due_at < ?", ("Pencere zamanında yoklanamadı (sunucu kapalıydı veya bütçe doluydu)",
                                             now, stale))
        targets = _due_targets(conn, now)
        due = [t for t in targets if t["due_at"] <= now]
        if not due:
            out["note"] = "Zamanı gelen hedef yok"
            return out
        first = min(t["due_at"] for t in due)
        soon = [t for t in targets if now < t["due_at"] <= iso_add(first, minutes=BATCH_MINUTES)]
        if soon and now < iso_add(first, minutes=BATCH_MINUTES):
            out["note"] = "Yakın zamanlı hedefler aynı istekte toplanmak için bekleniyor"
            return out
        if client is None:
            out["note"] = "API istemcisi yok"
            return out
        league_ids = sorted({apif.APIF_LEAGUE_IDS[t["league_id"]] for t in targets})
        params = {"live": "-".join(str(i) for i in league_ids)}
        try:
            rid = apif.reserve_request(conn, now, "/fixtures", params, "pencere canlı durum",
                                       daily_limit=cfg["daily_limit"], reserve=cfg["reserve"])
        except apif.BudgetExceeded as exc:
            _note_targets(conn, due, f"İstek gönderilmedi: {exc}", now)
            out["note"] = str(exc)
            return out
    out["requests"] += 1
    try:
        resp = client.get("/fixtures", params)  # kilit dışında
        error = None
    except apif.ApiFootballError as exc:
        resp, error = None, str(exc)
    t_live = clock()
    with lock:
        if resp is None:
            apif.finish_request(conn, rid, ok=False, error=error)
            out["note"] = error
            return out
        items = resp.payload.get("response") or []
        apif.finish_request(conn, rid, ok=True, results=len(items), remaining=resp.remaining)
        states: dict[int, dict] = {}
        linked: dict[int, tuple[int, float]] = {}
        for item in items:
            try:
                st = parse_live_item(item)
            except (KeyError, TypeError, ValueError):
                continue
            states[st["apif_fixture_id"]] = st
            fid, sim = link_fixture(conn, item, t_live)
            if fid is not None:
                linked[fid] = (st["apif_fixture_id"], sim)
        settle(conn, t_live, states)
        pending = {(r[0], r[1]): r for r in conn.execute(
            "SELECT fixture_id, window, status, attempts, due_at FROM window_radar_targets"
            " WHERE status IN ('pending','gated')")}
        hist = _history(conn, t_live, float(cfg["threshold"]), settings)
        assessments = []
        for fid, (apid, sim) in linked.items():
            st = states[apid]
            w = current_window(st)
            if w is None or (fid, w) not in pending:
                continue
            assessments.append(_assess(conn, fid, apid, sim, w, st, rid, t_live, cfg, settings, user_id, hist))
        _reschedule(conn, due, linked, states, t_live, {(a["fixture_id"], a["window"]) for a in assessments})
        need_odds = [a for a in assessments if a["pre_ok"]]
        odds_rid = None
        if need_odds:
            odds_params = {"fixture": need_odds[0]["apif_fixture_id"]} if len(need_odds) == 1 else {}
            try:
                odds_rid = apif.reserve_request(conn, t_live, "/odds/live", odds_params, "pencere canlı oran",
                                                daily_limit=cfg["daily_limit"], reserve=cfg["reserve"])
            except apif.BudgetExceeded as exc:
                for a in need_odds:
                    a["odds_info"] = {"ok": False, "probability": None, "reason": f"Oran istenmedi: {exc}"}
    odds_items: dict[int, dict] = {}
    if odds_rid is not None:
        out["requests"] += 1
        try:
            oresp = client.get("/odds/live", odds_params)
            oerr = None
        except apif.ApiFootballError as exc:
            oresp, oerr = None, str(exc)
        t_odds = clock()
        with lock:
            if oresp is None:
                apif.finish_request(conn, odds_rid, ok=False, error=oerr)
                for a in need_odds:
                    a["odds_info"] = {"ok": False, "probability": None, "reason": f"Canlı oran alınamadı: {oerr}"}
            else:
                olist = oresp.payload.get("response") or []
                apif.finish_request(conn, odds_rid, ok=True, results=len(olist), remaining=oresp.remaining)
                for o in olist:
                    try:
                        odds_items[int(o["fixture"]["id"])] = o
                    except (KeyError, TypeError, ValueError):
                        continue
                for a in need_odds:
                    w = WINDOWS[a["window"]]
                    a["odds_info"] = market_probability(odds_items.get(a["apif_fixture_id"]), w,
                                                        a["state"]["home_goals"] + a["state"]["away_goals"],
                                                        t_odds, settings.radar.live_odds_max_age_seconds)
    with lock:
        for a in assessments:
            res = _finalize(conn, a, odds_rid, cfg, settings, hist)
            out["evaluations"].append(res)
            if res["alarm"]:
                out["alerts"].append(res)
    return out


def _note_targets(conn, targets, note: str, now: str) -> None:
    with transaction(conn):
        for t in targets:
            conn.execute("UPDATE window_radar_targets SET note=?, updated_at=? WHERE fixture_id=? AND window=?",
                         (note, now, t["fixture_id"], t["window"]))


def _reschedule(conn, due, linked, states, now: str, assessed: set) -> None:
    """Zamanı gelmiş ama pencerede bulunmayan hedefler: yeniden zamanla veya kaçırıldı say."""
    by_fixture = {fid: states[apid] for fid, (apid, _) in linked.items()}
    with transaction(conn):
        for t in due:
            key = (t["fixture_id"], t["window"])
            if key in assessed:
                continue
            w = WINDOWS[t["window"]]
            st = by_fixture.get(t["fixture_id"])
            attempts = t["attempts"] + 1
            if st is None:
                wait, note = 10, "Maç canlı listede bulunamadı (başlamadı, ertelendi veya eşleşmedi)"
            elif st["status"] in ("FT", "AET", "PEN", "ET", "BT", "P") or \
                    (w.code == "H1" and st["status"] in ("HT", "2H")):
                attempts, wait, note = MAX_ATTEMPTS, 0, "Pencere geçti"
            elif st["status"] == w.period:
                wait, note = max(2, w.start - st["minute"] + 1), f"Maç {st['minute']}. dakikada; pencere başlamadı"
            elif w.code == "H2" and st["status"] == "1H":
                wait, note = max(5, (45 - st["minute"]) + 15 + 31), "Maç ilk yarıda"
            elif w.code == "H2" and st["status"] == "HT":
                wait, note = 38, "Devre arası"
            else:
                wait, note = 10, f"Maç durumu: {st['status']}"
            if attempts >= MAX_ATTEMPTS:
                conn.execute("UPDATE window_radar_targets SET status='missed', attempts=?, note=?, updated_at=?"
                             " WHERE fixture_id=? AND window=?", (attempts, note, now, *key))
            else:
                conn.execute("UPDATE window_radar_targets SET due_at=?, attempts=?, note=?, updated_at=?"
                             " WHERE fixture_id=? AND window=?",
                             (iso_add(now, minutes=wait), attempts, note + f" — {wait} dk sonra yeniden yoklanacak",
                              now, *key))


def _assess(conn, fid, apid, sim, window_code, st, rid, now, cfg, settings, user_id, hist) -> dict:
    w = WINDOWS[window_code]
    thr = float(cfg["threshold"])
    inputs = _prematch_inputs(conn, fid, settings)
    state = {k: st[k] for k in ("minute", "extra_minute", "home_goals", "away_goals", "home_red", "away_red")}
    p_a, remaining = (None, 0.0)
    if "lam_home" in inputs:
        p_a, remaining = model_a_window_probability(inputs["lam_home"], inputs["lam_away"], inputs["ht_share"], w, state)
    quality_ok = bool(inputs["ok"]) and p_a is not None and math.isfinite(p_a) and sim >= MIN_LINK_SIMILARITY
    quality_reason = inputs["reason"] or (None if sim >= MIN_LINK_SIMILARITY else "Maç eşleşmesi güvenilir değil")
    dup = conn.execute("SELECT 1 FROM window_radar_alerts WHERE dedupe_key=?", (f"{fid}:{w.code}",)).fetchone()
    a = {
        "fixture_id": fid, "apif_fixture_id": apid, "window": w.code, "state": state, "similarity": sim,
        "model_a_probability": p_a, "remaining_minutes": remaining, "threshold": thr, "live_request_id": rid,
        "evaluated_at": now, "quality_ok": quality_ok, "quality_reason": quality_reason,
        "permission": notification_permission_granted(conn, user_id), "duplicate": dup is not None,
        "odds_info": None, "user_id": user_id,
    }
    # 3. kapı: oran isteği yalnızca MODEL A eşiğe ulaştıysa ve oran dışı şartlar engel değilse.
    a["pre_ok"] = (p_a is not None and p_a >= thr and quality_ok
                   and remaining >= cfg["min_remaining_minutes"] and not a["duplicate"])
    return a


def _finalize(conn, a: dict, odds_rid, cfg, settings, hist) -> dict:
    thr = a["threshold"]
    p_a = a["model_a_probability"]
    oi = a["odds_info"]
    p_m = oi["probability"] if oi else None
    p_b = None
    fit = hist["model_b"]
    if fit is not None and p_a is not None and p_m is not None:
        p_b = float(fit.predict(model_b_design(np.clip(p_a, 1e-6, 1 - 1e-6), np.clip(p_m, 1e-6, 1 - 1e-6)))[0])
    cal_a_ok, cal_a_txt = _cal_text(hist["cal_a"], "A", thr, hist["min_rare"])
    cal_b_ok, cal_b_txt = _cal_text(hist["cal_b"], "B", thr, hist["min_rare"])
    min_rem = cfg["min_remaining_minutes"]
    conds: list[dict] = []

    def add(code: str, passed: bool | None, detail: str) -> None:
        conds.append({"code": code, "label": CONDITION_LABELS[code], "passed": passed, "detail": detail})

    add("model_a_threshold", p_a is not None and p_a >= thr,
        f"MODEL A {_pct(p_a)} (eşik %{thr * 100:.0f} ve üstü)")
    if p_b is not None:
        add("model_b_threshold", p_b >= thr, f"MODEL B {_pct(p_b)} (eşik %{thr * 100:.0f} ve üstü)")
    elif fit is None:
        add("model_b_threshold", False, f"MODEL B henüz eğitilmedi: oranlı sonuçlanmış {hist['with_market']} örnek "
                                        f"(en az {hist['model_b_min_samples']} gerekir)")
    else:
        add("model_b_threshold", False, "MODEL B hesaplanamadı: canlı piyasa olasılığı yok")
    add("remaining_time", a["remaining_minutes"] >= min_rem,
        f"Pencerede kalan ≈ {a['remaining_minutes']:.0f} dk (en az {min_rem} dk)")
    add("live_data_fresh", True, "Canlı durum bu adımda alındı")
    if oi is None:
        add("live_odds_fresh", None, "Canlı oran istenmedi: MODEL A eşiğin altında veya başka şart sağlanmıyor "
                                     "(istek harcanmadı)")
    else:
        add("live_odds_fresh", bool(oi["ok"]),
            (f"Piyasa olasılığı {_pct(p_m)} (Üst {oi.get('over')} / Alt {oi.get('under')}, çizgi {oi.get('line')})"
             if oi["ok"] else oi["reason"]))
    add("rare_event_calibration", cal_a_ok and cal_b_ok, f"{cal_a_txt} · {cal_b_txt}")
    add("quality_gate", a["quality_ok"], a["quality_reason"] or "Maç öncesi MODEL A geçerli, eşleşme güvenilir")
    add("notification_permission", a["permission"], "İzin verildi" if a["permission"] else "Bildirim izni verilmedi")
    add("duplicate_block", not a["duplicate"], "Bu maç ve pencere için daha önce bildirim yok" if not a["duplicate"]
        else "Bu maç ve pencere için bildirim zaten gönderildi")
    alarm = all(c["passed"] is True for c in conds)
    failed = next((c for c in conds if c["passed"] is not True), None)
    blocked = None if alarm else f"Bildirim gönderilmedi — sağlanmayan koşul: {failed['label']} ({failed['detail']})"
    st = a["state"]
    with transaction(conn):
        eid = insert(conn, "window_radar_evaluations", {
            "fixture_id": a["fixture_id"], "apif_fixture_id": a["apif_fixture_id"], "window": a["window"],
            "minute": st["minute"], "extra_minute": st["extra_minute"] or None, "home_goals": st["home_goals"],
            "away_goals": st["away_goals"], "home_red": st["home_red"], "away_red": st["away_red"],
            "remaining_minutes": a["remaining_minutes"], "model_a_probability": p_a if p_a is not None else 0.0,
            "market_probability": p_m, "market_odds": dumps({k: oi.get(k) for k in ("over", "under", "line")}) if oi and p_m is not None else None,
            "odds_updated_at": oi.get("updated_at") if oi else None, "model_b_probability": p_b,
            "threshold": thr, "conditions": dumps(conds), "alarm": 1 if alarm else 0, "blocked_reason": blocked,
            "live_request_id": a["live_request_id"], "odds_request_id": odds_rid if oi else None,
            "evaluated_at": a["evaluated_at"],
        })
        conn.execute("UPDATE window_radar_targets SET status='evaluated', note=?, updated_at=? WHERE fixture_id=?"
                     " AND window=?", (blocked or "Bildirim gönderildi", a["evaluated_at"], a["fixture_id"], a["window"]))
        if alarm:
            conn.execute("INSERT OR IGNORE INTO window_radar_alerts(evaluation_id, fixture_id, window, minute,"
                         " model_a_probability, model_b_probability, created_at, dedupe_key) VALUES (?,?,?,?,?,?,?,?)",
                         (eid, a["fixture_id"], a["window"], st["minute"], p_a, p_b, a["evaluated_at"],
                          f"{a['fixture_id']}:{a['window']}"))
    return {"id": eid, "fixture_id": a["fixture_id"], "window": a["window"], "model_a_probability": p_a,
            "market_probability": p_m, "model_b_probability": p_b, "alarm": alarm, "blocked_reason": blocked,
            "conditions": conds}


# ───────────────────────── durum (GET /api/v1/radar/windows) ─────────────────────────

def _fixture_labels(conn: sqlite3.Connection, ids: set[int]) -> dict[int, dict]:
    if not ids:
        return {}
    q = ",".join("?" * len(ids))
    rows = conn.execute(
        f"SELECT f.id, th.name, ta.name, f.league_id, f.kickoff_at FROM fixtures f JOIN teams th ON th.id=f.home_team_id"
        f" JOIN teams ta ON ta.id=f.away_team_id WHERE f.id IN ({q})", tuple(ids)).fetchall()
    return {r[0]: {"home": r[1], "away": r[2], "league": display_name(r[3]), "kickoff_at": r[4]} for r in rows}


def status(conn: sqlite3.Connection, now: str, settings: Settings = SETTINGS) -> dict:
    cfg = get_config(conn)
    plan(conn, now, cfg, settings)
    budget = apif.budget_status(conn, now, cfg["daily_limit"], cfg["reserve"])
    targets = conn.execute(
        "SELECT fixture_id, window, due_at, prematch_probability, reach_probability, status, note"
        " FROM window_radar_targets WHERE due_at >= ? ORDER BY due_at LIMIT 200", (iso_add(now, hours=-3),)).fetchall()
    evals = conn.execute(
        "SELECT id, fixture_id, window, minute, extra_minute, home_goals, away_goals, model_a_probability,"
        " market_probability, model_b_probability, alarm, blocked_reason, conditions, evaluated_at, outcome,"
        " remaining_minutes FROM window_radar_evaluations ORDER BY id DESC LIMIT 30").fetchall()
    alerts = conn.execute(
        "SELECT id, fixture_id, window, minute, model_a_probability, model_b_probability, created_at"
        " FROM window_radar_alerts ORDER BY id DESC LIMIT 30").fetchall()
    ids = {r[0] for r in targets} | {r[1] for r in evals} | {r[1] for r in alerts}
    labels = _fixture_labels(conn, ids)
    hist = _history(conn, now, float(cfg["threshold"]), settings)
    pres = [r[3] for r in targets if r[3] is not None]
    preview = threshold_preview(conn, [r for r in targets if r[2] >= now and r[5] in ("pending", "gated")],
                                float(cfg["min_reach"]), settings)
    upcoming = [r for r in targets if r[2] >= now]
    planned_requests = len({r[2][:16] for r in upcoming if r[5] == "pending"})
    return {
        "name": "Son 15 Dakika Radarı",
        "windows": [{"code": w.code, "label": w.label, "target": w.target} for w in WINDOWS.values()],
        "config": cfg,
        "threshold_range": list(THRESHOLD_RANGE),
        "budget": budget,
        "planned_requests": {"live": planned_requests, "note": "Her canlı yoklama 1 istektir; oran isteği yalnızca "
                                                               "MODEL A eşiğe ulaşırsa eklenir."},
        "probability_scale": {
            "targets": len(pres),
            "max_prematch_probability": max(pres) if pres else None,
            "mean_prematch_probability": float(np.mean(pres)) if pres else None,
            "note": ("15 dakikalık bir pencerede gol olasılığı genellikle %35–%55 arasındadır; %90 için maçta "
                     "yaklaşık 10 gol beklentisi gerekir. Bu nedenle %90 eşiğinde istek ve bildirim çok nadirdir."),
        },
        "threshold_preview": preview,
        "targets": [{
            "fixture_id": r[0], "fixture": labels.get(r[0]), "window": r[1], "window_label": WINDOWS[r[1]].label,
            "due_at": r[2], "prematch_probability": r[3], "reach_probability": r[4], "status": r[5], "note": r[6],
            "will_request": r[5] == "pending",
        } for r in targets],
        "evaluations": [{
            "id": r[0], "fixture_id": r[1], "fixture": labels.get(r[1]), "window": r[2],
            "window_label": WINDOWS[r[2]].label, "minute": r[3], "extra_minute": r[4], "score": f"{r[5]}-{r[6]}",
            "model_a_probability": r[7], "market_probability": r[8], "model_b_probability": r[9],
            "alarm": bool(r[10]), "blocked_reason": r[11], "conditions": loads(r[12], []), "evaluated_at": r[13],
            "outcome": r[14], "remaining_minutes": r[15],
        } for r in evals],
        "alerts": [{
            "id": r[0], "fixture_id": r[1], "fixture": labels.get(r[1]), "window": r[2],
            "window_label": WINDOWS[r[2]].label, "target": WINDOWS[r[2]].target, "minute": r[3],
            "model_a_probability": r[4], "model_b_probability": r[5], "created_at": r[6],
        } for r in alerts],
        "models": {
            "settled_evaluations": hist["settled"], "with_market": hist["with_market"],
            "model_b_ready": hist["model_b"] is not None, "model_b_min_samples": hist["model_b_min_samples"],
            "calibration": [(_cal_text(hist[k], m, float(cfg["threshold"]), hist["min_rare"])[1])
                            for k, m in (("cal_a", "A"), ("cal_b", "B"))],
        },
        "recent_requests": apif.recent_requests(conn, 10),
        "notes": [
            "Gol olasılığı eşiğe ulaşamayacak maçlar için API isteği gönderilmez.",
            "Bildirim yalnızca bu sayfa (veya uygulama sekmesi) açıkken tarayıcı bildirimi olarak gösterilir.",
            "Tahminler olasılıktır; yüksek olasılık gol garantisi değildir.",
        ],
    }


PREVIEW_THRESHOLDS = (0.90, 0.80, 0.70, 0.60, 0.50)


def threshold_preview(conn: sqlite3.Connection, targets: list, min_reach: float, settings: Settings) -> list[dict]:
    """Farklı eşiklerde kaç pencere için istek gönderileceği (yalnızca hesap; istek göndermez)."""
    inputs: dict[int, dict] = {}
    out = []
    for thr in PREVIEW_THRESHOLDS:
        n = 0
        for r in targets:
            fid = r[0]
            if fid not in inputs:
                inputs[fid] = _prematch_inputs(conn, fid, settings)
            x = inputs[fid]
            if x["ok"] and reach_probability(x["lam_home"], x["lam_away"], x["ht_share"], WINDOWS[r[1]], thr) >= min_reach:
                n += 1
        out.append({"threshold": thr, "windows_with_request": n, "windows": len(targets)})
    return out


def list_window_alerts(conn: sqlite3.Connection, after_id: int = 0, limit: int = 20) -> list[dict]:
    rows = conn.execute("SELECT id, fixture_id, window, minute, model_a_probability, model_b_probability, created_at"
                        " FROM window_radar_alerts WHERE id > ? ORDER BY id LIMIT ?", (after_id, limit)).fetchall()
    labels = _fixture_labels(conn, {r[1] for r in rows})
    return [{"id": r[0], "fixture_id": r[1], "fixture": labels.get(r[1]), "window": r[2],
             "window_label": WINDOWS[r[2]].label, "target": WINDOWS[r[2]].target, "minute": r[3],
             "model_a_probability": r[4], "model_b_probability": r[5], "created_at": r[6]} for r in rows]
