"""Sentetik demo verisi.

Gerçek veri sağlayıcısı bağlanana kadar sistemin uçtan uca çalıştırılması ve test edilmesi için
kurgusal takımlarla lig sezonları, sonuçlar, ilk yarı skorları, maç öncesi ve canlı oranlar üretir.

- Takım adları kurgusaldır; lig adları yalnızca organizasyon kodunu temsil eder.
- Bütün fikstürler `is_synthetic = 1`, bütün oranlar `synthetic-demo` kaynağıyla kaydedilir.
  Bu oranlar hiçbir zaman İddaa olarak gösterilmez.
- "Gerçek" gol üretim süreci zamanla değişen takım güçleri içerir; bahisçi bu süreci gürültülü
  bir tahminle fiyatlar ve marj ekler.
"""
from __future__ import annotations

import math
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

import numpy as np

from ..db.database import dumps, from_iso, insert, insert_many, iso_add, to_iso, transaction
from ..domain.markets import MARKETS
from ..models.score_grid import joint_weights, market_probabilities, outcome_index

SOURCE = "synthetic"
ODDS_SOURCE = "synthetic-demo"

TEAM_NAMES = {
    "tr-super-lig": [
        "Kartal SK", "Liman Gücü", "Bozkır FK", "Yıldız Spor", "Toros Gençlik", "Deniz Yıldızı",
        "Çınar Belediye", "Kızılırmak SK", "Ege Rüzgârı", "Anadolu Ocak", "Karadeniz Fırtına", "Bağlar SK",
        "Göktepe FK", "Saray Kapı",
    ],
    "es-la-liga": [
        "Montaña CF", "Puerto Azul", "Real Olivar", "Atlético Sierra", "Costa Dorada", "Villa Roble",
        "Deportivo Nube", "Unión Brisa", "Racing Faro", "Sporting Lago", "CD Mirador", "Celta Viento",
    ],
    "de-bundesliga": [
        "FC Talburg", "Eintracht Weiden", "Borussia Lindau", "SV Brückenfeld", "TSV Hohenwald", "VfB Seestadt",
        "Fortuna Bergheim", "Union Altmark", "SC Rheinau", "Kickers Waldstein", "1. FC Mühlbach", "Viktoria Osterau",
    ],
    "en-premier-league": [
        "Northbridge Town", "Harbour City", "Kingsford United", "Ashby Rovers", "Millbrook Athletic",
        "Westmoor FC", "Calder Vale", "Redhill Albion", "Stonegate", "Fenwick Wanderers", "Lowther Park",
        "Brayside", "Elmstead Borough", "Oakham Castle",
    ],
}

#: Market ailesine göre demo bahisçi marjı.
FAMILY_MARGIN = {
    "result": 0.055, "double_chance": 0.05, "dnb": 0.05, "totals": 0.055, "btts": 0.06,
    "team_totals": 0.07, "halftime": 0.07, "halftime_totals": 0.07, "htft": 0.16, "goal_range": 0.10,
    "correct_score": 0.22, "handicap": 0.08, "odd_even": 0.05,
}

TRUE_HT_SHARE = 0.44
TRUE_RHO = -0.06


@dataclass
class DemoSummary:
    fixtures_finished: int
    fixtures_upcoming: int
    fixtures_live: int
    odds_snapshots: int
    leagues: list[str]


def _round_robin(n: int) -> list[list[tuple[int, int]]]:
    teams = list(range(n))
    rounds = []
    for r in range(n - 1):
        pairs = []
        for i in range(n // 2):
            a, b = teams[i], teams[n - 1 - i]
            pairs.append((a, b) if r % 2 == 0 else (b, a))
        rounds.append(pairs)
        teams = [teams[0]] + [teams[-1]] + teams[1:-1]
    second = [[(b, a) for a, b in rnd] for rnd in rounds]
    return rounds + second


def _season_start(year: int) -> datetime:
    d = datetime(year, 8, 9, tzinfo=timezone.utc)
    while d.weekday() != 5:  # cumartesi
        d += timedelta(days=1)
    return d


class _World:
    """Gerçek veri üretim süreci: yavaşça değişen takım güçleri.

    `bookmaker_lag` > 0 ise bahisçi takım güçlerini o kadar tur gecikmeyle bilir ("yumuşak" piyasa);
    0 ise gerçek güçleri bilir ve yalnızca küçük gürültü ekler ("keskin", verimli piyasa).
    """

    def __init__(self, rng: np.random.Generator, n: int, bookmaker_lag: int = 0):
        self.rng = rng
        self.att = rng.normal(0, 0.28, n)
        self.dfn = rng.normal(0, 0.22, n)
        self.mu = math.log(1.32)
        self.home = 0.24
        self.lag = bookmaker_lag
        self._past: list[tuple[np.ndarray, np.ndarray]] = []

    def drift(self, scale: float = 0.035) -> None:
        self._past.append((self.att.copy(), self.dfn.copy()))
        self._past = self._past[-(self.lag + 1):] if self.lag else []
        self.att += self.rng.normal(0, scale, self.att.size)
        self.dfn += self.rng.normal(0, scale, self.dfn.size)
        self.att -= self.att.mean()
        self.dfn -= self.dfn.mean()

    def lambdas(self, h: int, a: int, *, bookmaker: bool = False) -> tuple[float, float]:
        att, dfn = self.att, self.dfn
        if bookmaker and self.lag and self._past:
            att, dfn = self._past[0]
        lh = math.exp(self.mu + self.home + att[h] - dfn[a])
        la = math.exp(self.mu + att[a] - dfn[h])
        return lh, la


def _sample_score(rng: np.random.Generator, lh: float, la: float) -> tuple[int, int, int, int]:
    w = joint_weights(np.array([lh]), np.array([la]), TRUE_HT_SHARE, TRUE_RHO)[0]
    from ..models.score_grid import grid

    idx = rng.choice(w.size, p=w)
    hh, ha, fh, fa = (int(x[idx]) for x in grid())
    return hh, ha, fh, fa


def _bookmaker_odds(rng: np.random.Generator, lh: float, la: float, noise: float = 0.09,
                    compression: float = 1.0) -> dict[tuple[str, str], float]:
    """Bahisçinin gürültülü fiyatları (marj dahil).

    `compression` < 1 favori–sürpriz eğilimini kurgular: bahisçinin olasılıkları logit ölçeğinde
    0,5'e doğru sıkıştırılır (favoriler olduğundan düşük, sürprizler olduğundan yüksek fiyatlanır).
    """
    blh = lh * math.exp(rng.normal(0, noise))
    bla = la * math.exp(rng.normal(0, noise))
    w = joint_weights(np.array([blh]), np.array([bla]), TRUE_HT_SHARE, -0.03)
    pw, pv = market_probabilities(w)
    keys = outcome_index()[0]
    pos = {k: i for i, k in enumerate(keys)}
    pc = pw[0].copy()
    if compression != 1.0:
        z = np.log(np.clip(pc, 1e-9, 1 - 1e-9) / (1 - np.clip(pc, 1e-9, 1 - 1e-9)))
        pc = 1 / (1 + np.exp(-compression * z))
        for mk in MARKETS:
            if mk.exhaustive and mk.family != "dnb":
                idx = [pos[(mk.id, o.id)] for o in mk.outcomes]
                pc[idx] = pc[idx] / pc[idx].sum()
        i1, ix, i2 = pos[("1X2", "1")], pos[("1X2", "X")], pos[("1X2", "2")]
        pc[pos[("DC", "1X")]] = pc[i1] + pc[ix]
        pc[pos[("DC", "12")]] = pc[i1] + pc[i2]
        pc[pos[("DC", "X2")]] = pc[ix] + pc[i2]
        pc[pos[("DNB", "1")]] = pc[i1]
        pc[pos[("DNB", "2")]] = pc[i2]
    voids = {k: float(pc[pos[("1X2", "X")]]) if k[0] == "DNB" else float(pv[0, i]) for i, k in enumerate(keys)}
    prob = {k: (float(pc[i]), voids[k]) for i, k in enumerate(keys)}
    out: dict[tuple[str, str], float] = {}
    for mk in MARKETS:
        margin = FAMILY_MARGIN[mk.family]
        for o in mk.outcomes:
            p, v = prob[(mk.id, o.id)]
            if mk.family == "dnb":
                # iade marketinde adil oran: 1 / P(kazanç | iade yok)
                denom = max(1e-9, 1 - v)
                fair_p = p / denom
            else:
                fair_p = p
            if fair_p <= 0.004:
                continue  # çok düşük olasılıklı seçenekler fiyatlanmaz
            price = 1.0 / min(0.995, fair_p * (1 + margin))
            price = max(1.01, round(price, 2))
            if price > 150:
                continue
            out[(mk.id, o.id)] = price
    return out


def generate_demo(conn: sqlite3.Connection, as_of: str, *, seed: int = 7,
                  leagues: tuple[str, ...] = ("tr-super-lig", "en-premier-league"),
                  upcoming_days: int = 8,
                  history_seasons: int = 2, live_fixtures: bool = True, teams_per_league: int = 12,
                  market: str = "biased", bookmaker_noise: float | None = None) -> DemoSummary:
    """market:
    - "efficient": verimli piyasa — bahisçi gerçek güçleri bilir, küçük gürültü + marj (avantaj beklenmez).
    - "biased": favori–sürpriz eğilimli ve 4 tur gecikmeli fiyatlayan bahisçi (demo varsayılanı).
    """
    if market not in ("efficient", "biased"):
        raise ValueError("market 'efficient' veya 'biased' olmalı")
    lag = 4 if market == "biased" else 0
    compression = 0.70 if market == "biased" else 1.0
    noise = bookmaker_noise if bookmaker_noise is not None else 0.05
    """`as_of` anına kadar oynanmış sezonları, sonraki günlerin fikstürlerini ve canlı maçları üretir."""
    rng = np.random.default_rng(seed)
    now = from_iso(as_of)
    cur_year = now.year if now.month >= 8 else now.year - 1
    years = list(range(cur_year - history_seasons, cur_year + 1))
    finished = upcoming = live = n_odds = 0
    worlds: dict[str, tuple[_World, list[int]]] = {}
    with transaction(conn):
        for li, league_id in enumerate(leagues):
            names = (TEAM_NAMES.get(league_id) or [f"Takım {i + 1}" for i in range(14)])[:teams_per_league]
            team_ids = []
            for nm in names:
                row = conn.execute("SELECT id FROM teams WHERE league_id=? AND name=?", (league_id, nm)).fetchone()
                team_ids.append(row[0] if row else insert(conn, "teams", {"league_id": league_id, "name": nm}))
            world = _World(rng, len(names), bookmaker_lag=lag)
            worlds[league_id] = (world, team_ids)
            for year in years:
                rounds = _round_robin(len(names))
                start = _season_start(year) + timedelta(days=li)  # ligler farklı günlerde oynar
                for rno, pairs in enumerate(rounds, start=1):
                    day = start + timedelta(days=7 * rno)
                    world.drift()
                    odds_rows = []
                    for k, (h, a) in enumerate(pairs):
                        ko = day.replace(hour=13 + 2 * (k % 4), minute=0)
                        ko_iso = to_iso(ko)
                        lh, la = world.lambdas(h, a)
                        blh, bla = world.lambdas(h, a, bookmaker=True)
                        if ko_iso >= as_of and (ko - now) > timedelta(days=upcoming_days):
                            continue  # yalnızca yakın fikstürler
                        if ko <= now < ko + timedelta(hours=2, minutes=5):
                            continue  # şu an oynanan lig maçı üretilmez; canlı demo maçları ayrıca kurulur
                        if ko + timedelta(hours=2, minutes=5) <= now:
                            hh, ha, fh, fa = _sample_score(rng, lh, la)
                            fid = insert(conn, "fixtures", {
                                "league_id": league_id, "season": str(year), "round": rno,
                                "home_team_id": team_ids[h], "away_team_id": team_ids[a],
                                "kickoff_at": ko_iso, "status": "finished", "ft_home": fh, "ft_away": fa,
                                "ht_home": hh, "ht_away": ha,
                                "result_recorded_at": to_iso(ko + timedelta(hours=2, minutes=5)),
                                "source": SOURCE, "is_synthetic": 1, "created_at": to_iso(ko - timedelta(days=30)),
                            })
                            finished += 1
                        else:
                            fid = insert(conn, "fixtures", {
                                "league_id": league_id, "season": str(year), "round": rno,
                                "home_team_id": team_ids[h], "away_team_id": team_ids[a],
                                "kickoff_at": ko_iso, "status": "scheduled",
                                "ft_home": None, "ft_away": None, "ht_home": None, "ht_away": None,
                                "result_recorded_at": None, "source": SOURCE, "is_synthetic": 1,
                                "created_at": to_iso(ko - timedelta(days=30)),
                            })
                            upcoming += 1
                            # Demo akışı bu maçı başlama saati geçince "oynatabilsin" diye gerçek beklenen goller saklanır.
                            conn.execute(
                                "INSERT INTO data_revisions(entity, entity_id, field, old_value, new_value, recorded_at)"
                                " VALUES ('fixture', ?, 'true_lambda', NULL, ?, ?)",
                                (fid, dumps({"lh": lh, "la": la}), as_of),
                            )
                        # maç öncesi oranlar: 2 gün önce (eski) ve başlama saatinden 45 dk önce
                        early = ko - timedelta(days=2)
                        late = ko - timedelta(minutes=45)
                        if ko_iso > as_of:
                            late = min(late, now - timedelta(minutes=10))
                        for cap in (early, late):
                            if cap > now:
                                continue
                            for (mid, oid), price in _bookmaker_odds(rng, blh, bla, noise, compression).items():
                                odds_rows.append({
                                    "fixture_id": fid, "market_id": mid, "outcome_id": oid,
                                    "decimal_odds": price, "source": ODDS_SOURCE, "captured_at": to_iso(cap),
                                    "is_live": 0, "minute": None, "timestamp_precision": "exact",
                                    "ingested_at": to_iso(cap),
                                })
                    insert_many(conn, "odds_snapshots", odds_rows)
                    n_odds += len(odds_rows)
                # sezon arası güç değişimi
                world.drift(scale=0.12)
        if live_fixtures and worlds:
            world, team_ids = next(iter(worlds.values()))
            pairs = [(0, 1), (2, 3), (4, 5), (6, 7)]
            for k, (h, a) in enumerate(pairs):
                ko = now - timedelta(minutes=(18, 37, 61, 83)[k])
                lh, la = world.lambdas(h, a)
                fid = insert(conn, "fixtures", {
                    "league_id": "tr-kupa", "season": str(cur_year), "round": 1,
                    "home_team_id": team_ids[h], "away_team_id": team_ids[a], "kickoff_at": to_iso(ko),
                    "status": "live", "ft_home": None, "ft_away": None, "ht_home": None, "ht_away": None,
                    "result_recorded_at": None, "source": SOURCE, "is_synthetic": 1,
                    "created_at": to_iso(ko - timedelta(days=10)),
                })
                _seed_live_state(conn, rng, fid, ko, now, lh, la)
                live += 1
        conn.execute(
            "INSERT INTO data_revisions(entity, entity_id, field, old_value, new_value, recorded_at)"
            " VALUES ('system', 0, 'synthetic_demo', NULL, ?, ?)",
            (dumps({"seed": seed, "leagues": list(leagues), "market": market, "bookmaker_lag": lag,
                    "bookmaker_noise": noise, "favourite_longshot_compression": compression}), as_of),
        )
    return DemoSummary(finished, upcoming, live, n_odds, list(leagues))


def _seed_live_state(conn: sqlite3.Connection, rng: np.random.Generator, fixture_id: int,
                     kickoff: datetime, now: datetime, lh: float, la: float) -> None:
    minute = max(1, min(90, int((now - kickoff).total_seconds() // 60)))
    frac = minute / 90.0
    hg = int(rng.poisson(lh * frac))
    ag = int(rng.poisson(la * frac))
    hxg = round(float(lh * frac * math.exp(rng.normal(0, 0.25))), 2)
    axg = round(float(la * frac * math.exp(rng.normal(0, 0.25))), 2)
    insert(conn, "live_states", {
        "fixture_id": fixture_id, "minute": minute, "home_goals": hg, "away_goals": ag,
        "home_xg": hxg, "away_xg": axg, "home_shots": int(rng.poisson(14 * frac)),
        "away_shots": int(rng.poisson(11 * frac)), "home_red": 0, "away_red": 0,
        "captured_at": to_iso(now - timedelta(seconds=20)), "source": SOURCE,
    })
    _seed_live_odds(conn, fixture_id, minute, hg, ag, lh, la, now - timedelta(seconds=15))
    conn.execute(
        "INSERT INTO data_revisions(entity, entity_id, field, old_value, new_value, recorded_at)"
        " VALUES ('fixture', ?, 'live_lambda', NULL, ?, ?)",
        (fixture_id, dumps({"lh": lh, "la": la}), to_iso(now)),
    )


def _seed_live_odds(conn: sqlite3.Connection, fixture_id: int, minute: int, hg: int, ag: int,
                    lh: float, la: float, captured: datetime) -> None:
    total = hg + ag
    line = total + 0.5
    market_id = f"OU_{line}"
    if market_id not in {m.id for m in MARKETS}:
        return
    remaining = max(0.0, (90 - minute) / 90.0)
    lam = (lh + la) * remaining
    p_over = 1 - math.exp(-lam)
    margin = 0.06
    price = max(1.01, round(1.0 / min(0.995, p_over * (1 + margin)), 2)) if p_over > 0.01 else None
    if price is None:
        return
    insert(conn, "odds_snapshots", {
        "fixture_id": fixture_id, "market_id": market_id, "outcome_id": "OVER", "decimal_odds": price,
        "source": ODDS_SOURCE, "captured_at": to_iso(captured), "is_live": 1, "minute": minute,
        "timestamp_precision": "exact", "ingested_at": to_iso(captured),
    })


def simulate_live_tick(conn: sqlite3.Connection, now: str, seed: int = 99) -> int:
    """Sentetik canlı maçları gerçek saate göre ilerletir (yalnızca demo verisi)."""
    rng = np.random.default_rng(abs(hash((now, seed))) % (2**32))
    nowdt = from_iso(now)
    rows = conn.execute(
        "SELECT f.id, f.kickoff_at, r.new_value FROM fixtures f JOIN data_revisions r"
        " ON r.entity='fixture' AND r.entity_id=f.id AND r.field='live_lambda'"
        " WHERE f.status='live' AND f.is_synthetic=1"
    ).fetchall()
    n = 0
    with transaction(conn):
        for fid, ko, lam_json in rows:
            import json

            lam = json.loads(lam_json)
            last = conn.execute(
                "SELECT minute, home_goals, away_goals, home_xg, away_xg, home_shots, away_shots, captured_at"
                " FROM live_states WHERE fixture_id=? ORDER BY captured_at DESC LIMIT 1", (fid,)
            ).fetchone()
            if last is None or (nowdt - from_iso(last["captured_at"])).total_seconds() < 30:
                continue
            minute = max(last["minute"], min(95, int((nowdt - from_iso(ko)).total_seconds() // 60)))
            dm = max(0, minute - last["minute"])
            frac = dm / 90.0
            hg = last["home_goals"] + int(rng.poisson(lam["lh"] * frac))
            ag = last["away_goals"] + int(rng.poisson(lam["la"] * frac))
            insert(conn, "live_states", {
                "fixture_id": fid, "minute": minute, "home_goals": hg, "away_goals": ag,
                "home_xg": round((last["home_xg"] or 0) + lam["lh"] * frac, 2),
                "away_xg": round((last["away_xg"] or 0) + lam["la"] * frac, 2),
                "home_shots": (last["home_shots"] or 0) + int(rng.poisson(14 * frac)),
                "away_shots": (last["away_shots"] or 0) + int(rng.poisson(11 * frac)),
                "home_red": 0, "away_red": 0, "captured_at": now, "source": SOURCE,
            })
            _seed_live_odds(conn, fid, minute, hg, ag, lam["lh"], lam["la"], nowdt - timedelta(seconds=5))
            n += 1
    return n


def is_synthetic_db(conn: sqlite3.Connection) -> bool:
    row = conn.execute("SELECT 1 FROM fixtures WHERE is_synthetic=1 LIMIT 1").fetchone()
    return row is not None


__all__ = ["generate_demo", "simulate_live_tick", "is_synthetic_db", "iso_add"]


def refresh_prematch_odds(conn: sqlite3.Connection, now: str, seed: int = 5) -> int:
    """Sentetik oran akışı: yaklaşan sentetik maçlar için `now` anında yeni oran anlık görüntüsü ekler.

    Yalnızca `is_synthetic = 1` fikstürlere ve `synthetic-demo` kaynağına uygulanır; gerçek veri
    bağlandığında bu işlev kullanılmaz. Fiyatlar son anlık görüntünün küçük bir rastgele yürüyüşüdür.
    """
    rng = np.random.default_rng(abs(hash((now, seed))) % (2**32))
    rows = conn.execute(
        """
        SELECT o.fixture_id, o.market_id, o.outcome_id, o.decimal_odds
        FROM odds_snapshots o JOIN fixtures f ON f.id = o.fixture_id
        WHERE f.is_synthetic = 1 AND f.status = 'scheduled' AND f.kickoff_at > ? AND o.is_live = 0
          AND o.source = ? AND o.captured_at = (
              SELECT MAX(o2.captured_at) FROM odds_snapshots o2
              WHERE o2.fixture_id = o.fixture_id AND o2.market_id = o.market_id AND o2.outcome_id = o.outcome_id
                AND o2.is_live = 0 AND o2.captured_at <= ?)
        """, (now, ODDS_SOURCE, now),
    ).fetchall()
    out = []
    for fid, mid, oid, price in rows:
        new = max(1.01, round(float(price) * math.exp(rng.normal(0, 0.01)), 2))
        out.append({"fixture_id": fid, "market_id": mid, "outcome_id": oid, "decimal_odds": new,
                    "source": ODDS_SOURCE, "captured_at": now, "is_live": 0, "minute": None,
                    "timestamp_precision": "exact", "ingested_at": now})
    with transaction(conn):
        insert_many(conn, "odds_snapshots", out)
    return len(out)


def _team_pool(conn: sqlite3.Connection) -> list[int]:
    """Canlı demo için takım havuzu: sentetik ligler içinde en çok takımı olan lig."""
    row = conn.execute(
        "SELECT t.league_id, COUNT(*) n FROM teams t WHERE EXISTS (SELECT 1 FROM fixtures f WHERE f.is_synthetic=1"
        " AND f.home_team_id=t.id) GROUP BY t.league_id ORDER BY n DESC, t.league_id LIMIT 1").fetchone()
    if row is None:
        return []
    return [r[0] for r in conn.execute("SELECT id FROM teams WHERE league_id=? ORDER BY id", (row[0],))]


def _finish_live_fixture(conn: sqlite3.Connection, rng: np.random.Generator, fid: int, kickoff: datetime,
                         now: str) -> None:
    """Sentetik canlı maçı son gözlenen durumdan 90. dakikaya kadar simüle ederek bitirir.

    İlk yarı skoru ve gol dakikaları gözlenmediği için yazılmaz; bu maçlar radar geçmişine
    (gol dakikası ve İY skoru gerektirir) girmez, yalnızca sonucu kaydedilir.
    """
    last = conn.execute("SELECT minute, home_goals, away_goals FROM live_states WHERE fixture_id=?"
                        " ORDER BY captured_at DESC LIMIT 1", (fid,)).fetchone()
    lam = conn.execute("SELECT new_value FROM data_revisions WHERE entity='fixture' AND entity_id=?"
                       " AND field='live_lambda' ORDER BY id DESC LIMIT 1", (fid,)).fetchone()
    minute, hg, ag = (last[0], last[1], last[2]) if last else (0, 0, 0)
    if lam is not None:
        import json

        lv = json.loads(lam[0])
        frac = max(0.0, (90 - minute) / 90.0)
        hg += int(rng.poisson(lv["lh"] * frac))
        ag += int(rng.poisson(lv["la"] * frac))
    recorded = to_iso(max(from_iso(now), kickoff + timedelta(hours=1, minutes=50)))
    conn.execute("UPDATE fixtures SET status='finished', ft_home=?, ft_away=?, result_recorded_at=? WHERE id=?",
                 (hg, ag, recorded, fid))
    conn.execute(
        "INSERT INTO data_revisions(entity, entity_id, field, old_value, new_value, recorded_at)"
        " VALUES ('fixture', ?, 'demo_finished', NULL, ?, ?)",
        (fid, dumps({"observed_until_minute": minute, "ft": [hg, ag]}), now),
    )


def ensure_live_demo(conn: sqlite3.Connection, now: str, seed: int = 21) -> int:
    """Sentetik canlı demo maçlarını gerçek saate hizalar.

    Normal süresi dolan sentetik canlı maçlar 90. dakikaya kadar simüle edilerek bitirilir; hiç
    canlı maç kalmadıysa `now` anına göre 4 yeni sentetik kupa maçı başlatılır. Yalnızca demo verisi.
    Dönüş: yeni başlatılan maç sayısı.
    """
    nowdt = from_iso(now)
    rng = np.random.default_rng(abs(hash((now[:16], seed))) % (2**32))
    live = conn.execute("SELECT id, kickoff_at FROM fixtures WHERE status='live' AND is_synthetic=1").fetchall()
    expired = [r for r in live if (nowdt - from_iso(r[1])).total_seconds() >= 95 * 60]
    running = [r for r in live if (nowdt - from_iso(r[1])).total_seconds() < 88 * 60]
    with transaction(conn):
        for fid, ko in expired:
            _finish_live_fixture(conn, rng, fid, from_iso(ko), now)
    if running:
        return 0
    ids = _team_pool(conn)
    if len(ids) < 8:
        return 0
    with transaction(conn):
        for fid, ko in live:
            if (fid, ko) not in expired:
                _finish_live_fixture(conn, rng, fid, from_iso(ko), now)
        order = rng.permutation(len(ids))
        for k in range(4):
            h, a = ids[order[2 * k]], ids[order[2 * k + 1]]
            ko = nowdt - timedelta(minutes=(12, 31, 55, 77)[k])
            lh = float(1.45 * math.exp(rng.normal(0, 0.2)))
            la = float(1.15 * math.exp(rng.normal(0, 0.2)))
            fid = insert(conn, "fixtures", {
                "league_id": "tr-kupa", "season": str(nowdt.year if nowdt.month >= 8 else nowdt.year - 1),
                "round": 1, "home_team_id": h, "away_team_id": a, "kickoff_at": to_iso(ko), "status": "live",
                "ft_home": None, "ft_away": None, "ht_home": None, "ht_away": None, "result_recorded_at": None,
                "source": SOURCE, "is_synthetic": 1, "created_at": to_iso(ko - timedelta(days=10)),
            })
            _seed_live_state(conn, rng, fid, ko, nowdt, lh, la)
    return 4


def play_due_fixtures(conn: sqlite3.Connection, now: str, seed: int = 31) -> int:
    """Sentetik demo: başlama saatinin üzerinden 2 sa 5 dk geçmiş planlı lig maçlarını oynatır.

    Skor, üretimde saklanan gerçek beklenen gollerden (true_lambda) örneklenir; sonuç başlama + 2 sa 5 dk
    anında kaydedilmiş sayılır. Yalnızca `is_synthetic = 1` fikstürlere uygulanır. Dönüş: oynatılan maç sayısı.
    """
    import json

    rows = conn.execute(
        "SELECT f.id, f.kickoff_at, r.new_value FROM fixtures f JOIN data_revisions r ON r.entity='fixture'"
        " AND r.entity_id=f.id AND r.field='true_lambda' WHERE f.is_synthetic=1 AND f.status='scheduled'"
        " AND f.kickoff_at<=?", (to_iso(from_iso(now) - timedelta(hours=2, minutes=5)),),
    ).fetchall()
    if not rows:
        return 0
    with transaction(conn):
        for fid, ko, lam_json in rows:
            lv = json.loads(lam_json)
            rng = np.random.default_rng(fid * 104729 + seed)
            hh, ha, fh, fa = _sample_score(rng, lv["lh"], lv["la"])
            conn.execute("UPDATE fixtures SET status='finished', ft_home=?, ft_away=?, ht_home=?, ht_away=?,"
                         " result_recorded_at=? WHERE id=?",
                         (fh, fa, hh, ha, to_iso(from_iso(ko) + timedelta(hours=2, minutes=5)), fid))
    return len(rows)
