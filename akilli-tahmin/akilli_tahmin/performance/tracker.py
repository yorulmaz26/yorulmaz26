"""Tahmin Başarı Merkezi › Seçim Modları: mod bazlı performans, aynı havuzda karşılaştırma ve kayıt.

İlkeler (MASTER PROMPT v6.3, Bölüm 3 ve 6):

- Üç modun başarısı birbirinden **bağımsız** hesaplanır: bir modun metrikleri yalnızca o modun kendi
  `selection_mode_decisions` kayıtlarından üretilir; bir modun kararı başka bir modun sonucuna karışmaz.
- Karşılaştırma **aynı zaman aralığı ve aynı maç havuzu** üzerinde yapılır. Havuz, pencere içinde
  değerlendirilen her (maç, market, seçenek) üçlüsüdür; bir maç birden çok kez değerlendirildiyse yalnızca
  başlama saatinden önceki **son** değerlendirme sayılır (tekrarlı çalıştırmalar iki kez sayılmaz). Üç mod
  aynı `candidate_evaluations` kayıtlarını okuduğu için havuz üç mod için birebir aynıdır.
- Yalnızca seçilmiş tahminleri göstererek başarı olduğundan yüksek sunulmaz: her karşılaştırmada, seçim
  yapılmadan Kalite Kapısı'nın ortak kontrollerinden geçen ve geçerli oranı olan bütün adayların MODEL B
  sonucu (taban çizgisi) birlikte verilir.
- Geçmiş zaman doğruluğu: `as_of` anından sonra yapılan değerlendirme ve seçimler ile `as_of` anında henüz
  bilinmeyen sonuçlar hesaba katılmaz (sonradan sonuçlanan seçim o an için "beklemede" sayılır).
- ROI teorik birim bahis getirisidir (her seçime 1 birim); kazanç garantisi değildir.

Kayıtlar (`snapshot_performance`) `selection_mode_performance`, `selection_mode_calibration` ve
`selection_mode_comparisons` tablolarına yalnızca eklenir; eski anlık görüntüler değiştirilmez.
"""
from __future__ import annotations

import hashlib
import re
import sqlite3
from dataclasses import dataclass
from functools import lru_cache
from typing import Any, Iterable, Mapping

import numpy as np

from ..calibration.metrics import (bootstrap_mean_ci, brier, log_loss, mean_ci_normal, reliability_bins,
                                   wilson_interval)
from ..config import SETTINGS
from ..db.database import (MODE_CODES, SELECTION_MODES, dumps, from_iso, insert_many, iso_add, to_iso,
                           transaction, utcnow)
from ..domain.markets import FAMILY_NAMES, MARKETS, MARKETS_BY_ID, MatchResult, settle
from ..leagues import LEAGUES, LEAGUES_BY_ID
from ..models.model_b import INDEX
from ..selection.history import outcome_vector

__all__ = [
    "PERIODS", "BASELINE_LABEL", "window_bounds", "metrics_for_decisions", "modes_comparison",
    "mode_performance", "selection_modes_overview", "history_for", "snapshot_performance",
]

#: Sabit pencereler (gün) ve kabul edilen dönem kodları (ayrıca `season:YYYY`).
WINDOW_DAYS = {"7d": 7, "30d": 30, "90d": 90}
PERIODS = ("7d", "30d", "90d", "all")
#: `snapshot_performance` ile kaydedilen dönemler.
SNAPSHOT_PERIODS = ("all", "7d", "30d", "90d")
_SEASON_RE = re.compile(r"^season:(\d{4})$")

BASELINE_LABEL = "Tüm adaylar (seçim yapılmadan, MODEL B)"

MODE_INFO = {m["code"]: m for m in SELECTION_MODES}

#: Bu örneklem büyüklüğünün üstünde ROI güven aralığı için bootstrap yerine normal yaklaşım kullanılır.
#: Büyük n'de bootstrap yüzdelik aralığı normal aralıkla çakışır; taban çizgisinde (on binlerce aday)
#: (tekrar × n) boyutlu yeniden örnekleme bellek ve süre bakımından gereksiz pahalıdır.
BOOTSTRAP_MAX_N = 5000
_Z95 = 1.959963984540054

# Sonuçlandırma kodları (numpy dizileri için).
PENDING, WON, LOST, VOID = 0, 1, 2, 3
_STATUS_CODE = {"pending": PENDING, "won": WON, "lost": LOST, "void": VOID}
_VOID_STATUSES = ("postponed", "abandoned", "cancelled")

K = len(INDEX.keys)
KEY_POS = {k: i for i, k in enumerate(INDEX.keys)}
_FAMILY_ORDER = {f: i for i, f in enumerate(FAMILY_NAMES)}
_LEAGUE_ORDER = {lg.id: i for i, lg in enumerate(LEAGUES)}
_FAMILY_MARKETS = {f: [mk.id for mk in MARKETS if mk.family == f] for f in FAMILY_NAMES}


# ───────────────────────── dönem ve pencere ─────────────────────────

def window_bounds(period: str, as_of: str) -> tuple[str | None, str]:
    """Dönem kodunu (başlangıç, bitiş) zaman aralığına çevirir.

    `7d` / `30d` / `90d` → (as_of − gün, as_of); `all` → (None, as_of);
    `season:YYYY` → (None, as_of) — sezon filtresi zamanla değil `fixtures.season` ile uygulanır.
    Geçersiz dönem veya zaman biçimi `ValueError` verir.
    """
    _check_time(as_of)
    if period in WINDOW_DAYS:
        return iso_add(as_of, days=-WINDOW_DAYS[period]), as_of
    if period == "all" or _SEASON_RE.match(period or ""):
        return None, as_of
    raise ValueError(f"Geçersiz dönem: {period!r}. Kabul edilenler: 7d, 30d, 90d, all, season:YYYY.")


def _check_time(ts: str) -> None:
    try:
        from_iso(ts)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"Geçersiz zaman: {ts!r}; beklenen biçim ISO-8601 UTC (2026-11-21T18:00:00Z).") from exc


def _season_of(period: str) -> str | None:
    m = _SEASON_RE.match(period)
    return m.group(1) if m else None


def _check_mode(mode: str) -> None:
    if mode not in MODE_INFO:
        raise ValueError(f"Geçersiz seçim modu: {mode!r}. Kabul edilenler: {', '.join(MODE_CODES)}.")


# ───────────────────────── metrikler ─────────────────────────

def _r(x: Any, nd: int = 4) -> float | None:
    if x is None:
        return None
    v = float(x)
    return round(v, nd) if np.isfinite(v) else None


def _mean_ci(values: np.ndarray) -> tuple[float | None, float | None]:
    if values.size < 2:
        return None, None
    if values.size <= BOOTSTRAP_MAX_N:
        return bootstrap_mean_ci(values, reps=SETTINGS.stats.bootstrap_reps)
    _, lo, hi = mean_ci_normal(values, _Z95)
    return lo, hi


@dataclass
class _Raw:
    """Yuvarlanmamış özet değerler (sezon tutarlılığı gibi iç hesaplar için)."""
    won: int
    lost: int
    hit: float | None
    hit_lo: float | None
    hit_hi: float | None
    roi: float | None
    n_settled: int


def _realized_profit(st: np.ndarray, profit: np.ndarray) -> np.ndarray:
    """ROI'ye giren getiri: sonuçlanmış satırlarda kayıtlı kâr, iadede 0; oranı olmayan/bekleyen NaN."""
    out = np.where(st == VOID, 0.0, profit)
    return np.where(st == PENDING, np.nan, out)


def _metrics_arrays(st: np.ndarray, odds: np.ndarray, p: np.ndarray, pv: np.ndarray, ev: np.ndarray,
                    profit: np.ndarray) -> tuple[dict, _Raw]:
    n = int(st.size)
    won = int(np.count_nonzero(st == WON))
    lost = int(np.count_nonzero(st == LOST))
    n_void = int(np.count_nonzero(st == VOID))
    n_settled = won + lost + n_void
    hit = won / (won + lost) if won + lost else None
    lo, hi = wilson_interval(won, won + lost)

    # Kalibrasyon: sonuçlanmış, iade olmayan seçimler; iade olasılığı ayrılmış koşullu olasılık p/(1−p_void).
    cal = ((st == WON) | (st == LOST)) & np.isfinite(p)
    q = p[cal] / np.maximum(1.0 - np.nan_to_num(pv[cal]), 1e-9)
    q = np.clip(q, 0.0, 1.0)
    y = (st[cal] == WON).astype(int)
    bins = reliability_bins(q, y) if q.size else []
    ece = (sum(b["n"] * abs(b["p_mean"] - b["observed"]) for b in bins) / sum(b["n"] for b in bins)) if bins else None

    realized = _realized_profit(st, profit)
    realized = realized[np.isfinite(realized)]
    roi = float(realized.mean()) if realized.size else None
    roi_lo, roi_hi = _mean_ci(realized)

    def _avg(a: np.ndarray) -> float | None:
        a = a[np.isfinite(a)]
        return float(a.mean()) if a.size else None

    out = {
        "n_candidates": None, "n_eligible": None, "n_selections": n, "n_settled": n_settled, "n_void": n_void,
        "n_fixtures": None, "n_fixtures_without_selection": None, "selection_rate": None,
        "hit_rate": {"value": _r(hit), "ci_low": _r(lo), "ci_high": _r(hi)},
        "brier": _r(brier(q, y)), "log_loss": _r(log_loss(q, y)), "ece": _r(ece),
        "calibration_bins": [{"p_mean": _r(b["p_mean"]), "observed": _r(b["observed"]), "n": int(b["n"]),
                              "ci_low": _r(b["ci_low"]), "ci_high": _r(b["ci_high"])} for b in bins],
        "avg_odds": _r(_avg(odds)), "avg_probability": _r(_avg(p)), "avg_ev": _r(_avg(ev)),
        "roi": {"value": _r(roi), "ci_low": _r(roi_lo), "ci_high": _r(roi_hi)},
        "profit_units": _r(float(realized.sum())) if realized.size else None,
    }
    return out, _Raw(won, lost, hit, lo, hi, roi, n_settled)


def _field(row: Any, key: str) -> Any:
    try:
        return row[key]
    except (KeyError, IndexError):
        return None


def _f(v: Any) -> float:
    return np.nan if v is None else float(v)


def metrics_for_decisions(rows: Iterable[Mapping[str, Any]]) -> dict:
    """Karar satırlarından `Metrics` nesnesi (docs/API.md › Performans nesnesi).

    Satır alanları: `settlement_status`, `decimal_odds`, `estimated_probability`, `void_probability`,
    `estimated_ev`, `profit_units` (sqlite3.Row veya dict).

    - İsabet oranı = kazanan / (kazanan + kaybeden), %95 Wilson aralığıyla; iadeler paydaya girmez.
    - Brier, log loss, ECE ve güvenilirlik kutuları: sonuçlanmış ve iade olmayan seçimlerde p/(1−p_void).
    - Ortalama oran / olasılık / EV: bütün seçimler (bekleyenler dahil) üzerinden.
    - ROI: sonuçlanmış seçimlerde (iade = 0) birim bahis kârının ortalaması, %95 bootstrap aralığıyla;
      oranı kaydedilmemiş seçimler ROI'ye girmez. `profit_units` aynı satırların toplamıdır.
    - `n_settled` iadeler dahil sonuçlanmış seçim sayısı, `n_void` bunların iade olanlarıdır.

    Aday havuzu gerektiren alanlar (`n_candidates`, `n_eligible`, `n_fixtures`,
    `n_fixtures_without_selection`, `selection_rate`) burada `None` bırakılır; çağıran doldurur.
    Veri yoksa değerler `None` olur.
    """
    rows = list(rows)
    st = np.array([_STATUS_CODE.get(_field(r, "settlement_status") or "pending", PENDING) for r in rows], np.int8)
    cols = {k: np.array([_f(_field(r, k)) for r in rows], float)
            for k in ("decimal_odds", "estimated_probability", "void_probability", "estimated_ev", "profit_units")}
    out, _ = _metrics_arrays(st, cols["decimal_odds"], cols["estimated_probability"], cols["void_probability"],
                             cols["estimated_ev"], cols["profit_units"])
    return out


# ───────────────────────── sonuç vektörleri ─────────────────────────

@lru_cache(maxsize=4096)
def _outcome_codes(status: str, ft_home: int | None, ft_away: int | None, ht_home: int | None,
                   ht_away: int | None) -> np.ndarray:
    """(K,) sonuçlandırma kodu; yalnızca maç sonucuna bağlıdır, bu yüzden sonuç değerleriyle önbelleklenir."""
    codes = np.zeros(K, np.int8)
    if status in _VOID_STATUSES:
        codes[:] = VOID
    elif status == "finished" and ft_home is not None and ft_away is not None:
        res = MatchResult(status, ft_home, ft_away, ht_home, ht_away)
        y = outcome_vector(res)
        codes[y == 1] = WON
        codes[y == 0] = LOST
        # NaN: iade (ör. beraberlikte iade) veya sonuçlanamayan (ilk yarı skoru eksik) — kuralla ayrılır.
        for i in np.flatnonzero(np.isnan(y)):
            m, o = INDEX.keys[i]
            codes[i] = VOID if settle(m, o, res) == "void" else PENDING
    codes.setflags(write=False)
    return codes


def _settle_code(market_id: str, outcome_id: str, res: MatchResult) -> int:
    if market_id not in MARKETS_BY_ID:
        return PENDING
    s = settle(market_id, outcome_id, res)
    return _STATUS_CODE.get(s, PENDING)


# ───────────────────────── veri yükleme ─────────────────────────

@dataclass
class _Data:
    """`as_of` anında bilinen havuz ve kararlar (numpy dizileri)."""
    as_of: str
    is_synthetic: bool
    # havuz: maç başına başlama saatinden önceki son değerlendirmenin bütün seçenekleri
    p_fid: np.ndarray
    p_at: np.ndarray
    p_league: np.ndarray
    p_season: np.ndarray
    p_family: np.ndarray
    p_base: np.ndarray        # taban çizgisine girer: core_pass ∧ odds_valid ∧ MODEL B olasılığı ve oran var
    p_pb: np.ndarray
    p_void: np.ndarray
    p_odds: np.ndarray
    p_evb: np.ndarray
    p_st: np.ndarray          # as_of anında bilinen sonuç kodu
    p_elig: dict[str, np.ndarray]
    # kararlar
    d_mode: np.ndarray
    d_fid: np.ndarray
    d_league: np.ndarray
    d_season: np.ndarray
    d_family: np.ndarray
    d_ts: np.ndarray
    d_st: np.ndarray
    d_settled_at: np.ndarray
    d_odds: np.ndarray
    d_p: np.ndarray
    d_pv: np.ndarray
    d_ev: np.ndarray
    d_profit: np.ndarray

    @property
    def n_pool(self) -> int:
        return int(self.p_fid.size)

    @property
    def n_dec(self) -> int:
        return int(self.d_fid.size)


_POOL_SQL = """
WITH runs AS (
    SELECT e.fixture_id AS fid, e.run_id AS rid, MAX(e.evaluated_at) AS at
    FROM candidate_evaluations e JOIN fixtures f ON f.id = e.fixture_id
    WHERE e.evaluated_at <= :as_of AND e.evaluated_at < f.kickoff_at
    GROUP BY e.fixture_id, e.run_id
), last AS (
    SELECT fid, rid FROM (
        SELECT fid, rid, ROW_NUMBER() OVER (PARTITION BY fid ORDER BY at DESC, rid DESC) AS rn FROM runs
    ) WHERE rn = 1
)
SELECT e.fixture_id, e.market_id, e.outcome_id, e.evaluated_at, e.core_pass, e.odds_valid, e.p_b_cal, e.p_void,
       e.decimal_odds, e.ev_model_b
FROM last JOIN candidate_evaluations e ON e.run_id = last.rid AND e.fixture_id = last.fid
"""

_ELIGIBLE_SQL = """
SELECT c.selection_mode, c.fixture_id, c.market_id, c.outcome_id
FROM selection_mode_candidates c
JOIN candidate_evaluations e ON e.id = c.evaluation_id
JOIN fixtures f ON f.id = c.fixture_id
WHERE c.eligible = 1 AND e.evaluated_at <= :as_of AND e.evaluated_at < f.kickoff_at
"""

_DECISIONS_SQL = """
SELECT selection_mode, fixture_id, market_id, outcome_id, selection_timestamp, settlement_status, settled_at,
       decimal_odds, estimated_probability, void_probability, estimated_ev, profit_units
FROM selection_mode_decisions WHERE selection_timestamp <= :as_of
"""


def _cursor(conn: sqlite3.Connection) -> sqlite3.Cursor:
    cur = conn.cursor()
    cur.row_factory = None  # demet satırlar (sqlite3.Row'dan hızlı)
    return cur


def _family_of(market_id: str) -> str:
    mk = MARKETS_BY_ID.get(market_id)
    return mk.family if mk else "unknown"


def _key_codes(fid: np.ndarray, mkt: Iterable[str], out: Iterable[str]) -> tuple[np.ndarray, np.ndarray]:
    """(maç, market, seçenek) → (tek tamsayı anahtar, INDEX konumu).

    Bilinmeyen seçenekler INDEX konumu −1 ve birbirinden farklı negatif anahtar alır.
    """
    keys = np.array([KEY_POS.get(k, -1) for k in zip(mkt, out)], np.int64)
    return np.where(keys >= 0, fid.astype(np.int64) * K + keys, -1 - np.arange(keys.size)), keys


def _load(conn: sqlite3.Connection, as_of: str) -> _Data:
    cur = _cursor(conn)
    fx = cur.execute("SELECT id, league_id, season, status, ft_home, ft_away, ht_home, ht_away, result_recorded_at,"
                     " is_synthetic FROM fixtures").fetchall()
    fx_info = {r[0]: r for r in fx}
    is_synth = any(r[9] for r in fx)

    def fx_attr(fids: np.ndarray, j: int) -> np.ndarray:
        return np.array([fx_info[i][j] if i in fx_info else "" for i in fids.tolist()], dtype=str) \
            if fids.size else np.array([], dtype=str)

    # ── havuz
    rows = cur.execute(_POOL_SQL, {"as_of": as_of}).fetchall()
    cols = list(zip(*rows)) if rows else [()] * 10
    p_fid = np.array(cols[0], np.int64)
    p_mkt, p_out = cols[1], cols[2]
    p_at = np.array(cols[3], dtype=str)
    core = np.array(cols[4], bool)
    ov = np.array(cols[5], bool)
    p_pb = np.array(cols[6], float)
    p_void = np.nan_to_num(np.array(cols[7], float))
    p_odds = np.array(cols[8], float)
    p_evb = np.array(cols[9], float)
    p_code, p_key = _key_codes(p_fid, p_mkt, p_out)
    fam_arr = np.array(INDEX.family, dtype=str)
    p_family = np.where(p_key >= 0, fam_arr[np.maximum(p_key, 0)], "") if p_key.size else np.array([], dtype=str)
    for i in np.flatnonzero(p_key < 0):
        p_family[i] = _family_of(p_mkt[i])
    p_base = core & ov & np.isfinite(p_pb) & np.isfinite(p_odds)

    # sonuçlar: yalnızca sonucu as_of anında bilinen maçlar; vektörler sonuç değerleriyle önbellekli
    p_st = np.zeros(p_fid.size, np.int8)
    if p_fid.size:
        ufid, inv = np.unique(p_fid, return_inverse=True)
        mat = np.zeros((ufid.size, K), np.int8)
        known = np.zeros(ufid.size, bool)
        for j, fid in enumerate(ufid.tolist()):
            r = fx_info.get(fid)
            if r is not None and r[8] is not None and r[8] <= as_of:
                mat[j] = _outcome_codes(r[3], r[4], r[5], r[6], r[7])
                known[j] = True
        ok = p_key >= 0
        p_st[ok] = mat[inv[ok], p_key[ok]]
        for i in np.flatnonzero(~ok & known[inv]):
            r = fx_info[int(p_fid[i])]
            p_st[i] = _settle_code(p_mkt[i], p_out[i], MatchResult(r[3], r[4], r[5], r[6], r[7]))

    # ── modlara göre uygunluk (aynı havuz anahtarları)
    elig = cur.execute(_ELIGIBLE_SQL, {"as_of": as_of}).fetchall()
    p_elig: dict[str, np.ndarray] = {}
    for mode in MODE_CODES:
        sel = [r for r in elig if r[0] == mode]
        if sel and p_code.size:
            e_fid = np.array([r[1] for r in sel], np.int64)
            e_code, _ = _key_codes(e_fid, [r[2] for r in sel], [r[3] for r in sel])
            p_elig[mode] = np.isin(p_code, e_code[e_code >= 0])
        else:
            p_elig[mode] = np.zeros(p_fid.size, bool)

    # ── kararlar (geçmiş zaman doğruluğu: as_of anından sonra sonuçlananlar o an için beklemede)
    drows = cur.execute(_DECISIONS_SQL, {"as_of": as_of}).fetchall()
    dc = list(zip(*drows)) if drows else [()] * 12
    d_fid = np.array(dc[1], np.int64)
    settled_at = np.array([s if s is not None else "" for s in dc[6]], dtype=str)
    st = np.array([_STATUS_CODE.get(s, PENDING) for s in dc[5]], np.int8)
    known_settle = (settled_at != "") & (settled_at <= as_of)
    st = np.where(known_settle, st, PENDING).astype(np.int8)
    profit = np.where(known_settle, np.array(dc[11], float), np.nan)

    return _Data(
        as_of=as_of, is_synthetic=is_synth,
        p_fid=p_fid, p_at=p_at, p_league=fx_attr(p_fid, 1), p_season=fx_attr(p_fid, 2), p_family=p_family,
        p_base=p_base, p_pb=p_pb, p_void=p_void, p_odds=p_odds, p_evb=p_evb, p_st=p_st, p_elig=p_elig,
        d_mode=np.array(dc[0], dtype=str), d_fid=d_fid, d_league=fx_attr(d_fid, 1), d_season=fx_attr(d_fid, 2),
        d_family=np.array([_family_of(m) for m in dc[2]], dtype=str), d_ts=np.array(dc[4], dtype=str),
        d_st=st, d_settled_at=np.where(known_settle, settled_at, ""), d_odds=np.array(dc[7], float),
        d_p=np.array(dc[8], float), d_pv=np.nan_to_num(np.array(dc[9], float)), d_ev=np.array(dc[10], float),
        d_profit=profit,
    )


# ───────────────────────── kapsam hesapları ─────────────────────────

def _masks(data: _Data, period: str) -> tuple[np.ndarray, np.ndarray]:
    """Dönem için (havuz maskesi, karar maskesi)."""
    start, _ = window_bounds(period, data.as_of)
    pm = np.ones(data.n_pool, bool)
    dm = np.ones(data.n_dec, bool)
    if start is not None:
        pm &= data.p_at >= start
        dm &= data.d_ts >= start
    season = _season_of(period)
    if season is not None:
        pm &= data.p_season == season
        dm &= data.d_season == season
    return pm, dm


def _fill_pool(m: dict, data: _Data, pm: np.ndarray, sel_fids: np.ndarray, n_eligible: int) -> dict:
    pool_fx = np.unique(data.p_fid[pm])
    n_cand = int(np.count_nonzero(pm))
    m["n_candidates"] = n_cand
    m["n_eligible"] = int(n_eligible)
    m["n_fixtures"] = int(pool_fx.size)
    m["n_fixtures_without_selection"] = int(np.setdiff1d(pool_fx, sel_fids).size)
    m["selection_rate"] = _r(m["n_selections"] / n_cand) if n_cand else None
    return m


def _mode_metrics(data: _Data, mode: str, pm: np.ndarray, dm: np.ndarray) -> tuple[dict, _Raw]:
    """Bir modun metrikleri — yalnızca o modun kendi kararları; havuz sayıları aynı havuzdan."""
    d = dm & (data.d_mode == mode)
    m, raw = _metrics_arrays(data.d_st[d], data.d_odds[d], data.d_p[d], data.d_pv[d], data.d_ev[d],
                             data.d_profit[d])
    _fill_pool(m, data, pm, np.unique(data.d_fid[d]), np.count_nonzero(pm & data.p_elig[mode]))
    return m, raw


def _baseline_metrics(data: _Data, pm: np.ndarray) -> dict:
    """Taban çizgisi: seçim yapılmadan, ortak kontrollerden geçen ve geçerli oranı olan bütün adaylar (MODEL B)."""
    b = pm & data.p_base
    st = data.p_st[b]
    odds = data.p_odds[b]
    profit = np.where(st == WON, odds - 1.0, np.where(st == LOST, -1.0, np.where(st == VOID, 0.0, np.nan)))
    m, _ = _metrics_arrays(st, odds, data.p_pb[b], data.p_void[b], data.p_evb[b], profit)
    n_base = int(np.count_nonzero(b))
    return _fill_pool(m, data, pm, np.unique(data.p_fid[b]), n_base)


def _pool_hash(fids: np.ndarray) -> str:
    return hashlib.sha256(",".join(str(int(x)) for x in np.unique(fids)).encode("ascii")).hexdigest()


def _league_name(league_id: str) -> str:
    lg = LEAGUES_BY_ID.get(league_id)
    return lg.name if lg else league_id


def _by_league(data: _Data, mode: str, pm: np.ndarray, dm: np.ndarray) -> list[dict]:
    d = dm & (data.d_mode == mode)
    ids = set(np.unique(data.p_league[pm]).tolist()) | set(np.unique(data.d_league[d]).tolist())
    ids.discard("")
    out = []
    for lg in sorted(ids, key=lambda x: (_LEAGUE_ORDER.get(x, 999), x)):
        m, _ = _mode_metrics(data, mode, pm & (data.p_league == lg), dm & (data.d_league == lg))
        out.append({"league": {"id": lg, "name": _league_name(lg)}, "metrics": m})
    return out


def _by_market(data: _Data, mode: str, pm: np.ndarray, dm: np.ndarray) -> list[dict]:
    d = dm & (data.d_mode == mode)
    fams = set(np.unique(data.p_family[pm]).tolist()) | set(np.unique(data.d_family[d]).tolist())
    fams.discard("")
    out = []
    for fam in sorted(fams, key=lambda x: (_FAMILY_ORDER.get(x, 999), x)):
        m, _ = _mode_metrics(data, mode, pm & (data.p_family == fam), dm & (data.d_family == fam))
        out.append({"family": fam, "name": FAMILY_NAMES.get(fam, fam), "metrics": m})
    return out


def _by_season(data: _Data, mode: str, pm: np.ndarray, dm: np.ndarray) -> tuple[list[dict], list[_Raw]]:
    d = dm & (data.d_mode == mode)
    seasons = set(np.unique(data.p_season[pm]).tolist()) | set(np.unique(data.d_season[d]).tolist())
    seasons.discard("")
    out, raws = [], []
    for s in sorted(seasons):
        m, raw = _mode_metrics(data, mode, pm & (data.p_season == s), dm & (data.d_season == s))
        out.append({"season": s, "metrics": m})
        raws.append(raw)
    return out, raws


def _season_stability(raws: list[_Raw], overall: _Raw) -> dict:
    """Sezonlar arası dayanıklılık.

    `seasons`: sonuçlanmış (kazanan/kaybeden) seçimi olan sezon sayısı; `roi_std` / `hit_rate_std`: bu
    sezonların ROI ve isabet oranlarının (anakütle) standart sapması (en az iki sezon gerekir);
    `consistent`: en az iki sezon var ve her sezonun %95 Wilson aralığı dönemin genel isabet oranını içeriyor.
    """
    valid = [r for r in raws if r.won + r.lost > 0]
    hits = [r.hit for r in valid]
    rois = [r.roi for r in valid if r.roi is not None]
    tol = 1e-9  # Wilson sınırları 0/1'de kayan nokta hatasıyla 1 − ε çıkabilir
    consistent = (len(valid) >= 2 and overall.hit is not None
                  and all(r.hit_lo - tol <= overall.hit <= r.hit_hi + tol for r in valid))
    return {
        "seasons": len(valid),
        "roi_std": _r(np.std(rois)) if len(rois) >= 2 else None,
        "hit_rate_std": _r(np.std(hits)) if len(hits) >= 2 else None,
        "consistent": bool(consistent),
    }


def _timeline(data: _Data, mode: str, dm: np.ndarray) -> list[dict]:
    """Sonuçlanma tarihine (UTC gün) göre kümülatif teorik kâr ve kümülatif sonuçlanmış seçim sayısı."""
    d = dm & (data.d_mode == mode)
    realized = _realized_profit(data.d_st[d], data.d_profit[d])
    ok = np.isfinite(realized)
    if not ok.any():
        return []
    days = np.array([s[:10] for s in data.d_settled_at[d][ok]], dtype=str)
    uniq, inv = np.unique(days, return_inverse=True)
    cum_profit = np.cumsum(np.bincount(inv, weights=realized[ok]))
    cum_n = np.cumsum(np.bincount(inv))
    return [{"date": str(day), "cumulative_profit": _r(cp), "n": int(n)}
            for day, cp, n in zip(uniq.tolist(), cum_profit.tolist(), cum_n.tolist())]


def _window_payload(data: _Data, period: str, pm: np.ndarray) -> dict:
    start, end = window_bounds(period, data.as_of)
    if start is None and pm.any():
        start = min(data.p_at[pm].tolist())  # tüm zaman / sezon: havuzdaki ilk değerlendirme
    return {"start": start, "end": end}


# ───────────────────────── açık uçlar ─────────────────────────

def _comparison(data: _Data, period: str) -> dict:
    pm, dm = _masks(data, period)
    pool_fids = np.unique(data.p_fid[pm])
    # Aynı havuz: karşılaştırmada yalnızca havuzdaki maçlara ait kararlar sayılır.
    dm = dm & np.isin(data.d_fid, pool_fids)
    n_cand = int(np.count_nonzero(pm))
    modes = []
    for mode in MODE_CODES:
        m, _ = _mode_metrics(data, mode, pm, dm)
        modes.append({"code": mode, "name": MODE_INFO[mode]["name"], "metrics": m})
    notes = [
        "Seçim kapsamı modlar arasında farklıdır; isabet oranları farklı sayıda seçim üzerinden hesaplanmıştır.",
        "Üç mod aynı zaman aralığı ve aynı maç havuzu üzerinde karşılaştırılır; havuz, her maçın başlama "
        "saatinden önceki son değerlendirmesindeki bütün market seçeneklerinden oluşur.",
        "Taban çizgisi (Tüm adaylar), seçim yapılmadan Kalite Kapısı'nın ortak kontrollerinden geçen ve geçerli "
        "oranı olan bütün adayların MODEL B sonucudur; yalnızca seçilmiş tahminlerin başarısı genel başarı "
        "olarak okunmamalıdır.",
        "ROI ve kâr, her seçime 1 birim yatırıldığı varsayımıyla hesaplanan teorik getiridir; kazanç garantisi "
        "değildir.",
    ]
    no_odds = dm & ~np.isfinite(data.d_odds)
    if no_odds.any():
        notes.append(f"Oranı kaydedilmemiş {int(no_odds.sum())} seçim isabet oranına katılır, ROI hesabına katılmaz.")
    pending = int(np.count_nonzero(dm & (data.d_st == PENDING)))
    if pending:
        notes.append(f"Henüz sonuçlanmamış {pending} seçim isabet oranı ve ROI hesabına katılmamıştır.")
    if data.is_synthetic:
        notes.append("Sonuçlar sentetik veriyle üretilmiştir; yalnızca işlevsel doğrulama içindir, gerçek "
                     "performans iddiası değildir.")
    return {
        "period": period,
        "window": _window_payload(data, period, pm),
        "pool": {"fixtures": int(pool_fids.size), "candidates": n_cand,
                 "description": f"Aynı {pool_fids.size} maç ve aynı {n_cand} aday havuzu üç mod için ortak.",
                 "hash": _pool_hash(pool_fids)},
        "baseline": {"label": BASELINE_LABEL, "metrics": _baseline_metrics(data, pm)},
        "modes": modes,
        "notes": notes,
    }


def modes_comparison(conn: sqlite3.Connection, period: str, as_of: str) -> dict:
    """GET /api/v1/recommendations/modes/comparison — üç mod aynı zaman aralığı ve aynı aday havuzunda.

    Havuz pencere içinde değerlendirilen bütün (maç, market, seçenek) üçlüleridir (maç başına başlama
    saatinden önceki son değerlendirme). Her mod için `n_candidates` havuz büyüklüğü, `n_eligible` o modda
    uygun bulunan farklı havuz anahtarı sayısı, `n_selections` seçim zamanı pencerede olan kararlardır.
    `baseline` seçim yapılmadan bütün ortak-kontrolden-geçen adayların MODEL B sonucudur.
    """
    window_bounds(period, as_of)
    return _comparison(_load(conn, as_of), period)


def mode_performance(conn: sqlite3.Connection, mode: str, period: str, as_of: str) -> dict:
    """GET /api/v1/recommendations/modes/performance — bir modun ayrıntılı ve bağımsız performansı.

    `windows` (son 7/30/90 gün) dönemden bağımsızdır; diğer bütün alanlar (`overall`, lig, market, sezon,
    sezon dayanıklılığı, zaman çizelgesi) seçilen döneme göredir.
    """
    _check_mode(mode)
    window_bounds(period, as_of)
    data = _load(conn, as_of)
    pm, dm = _masks(data, period)
    overall, raw = _mode_metrics(data, mode, pm, dm)
    seasons, raws = _by_season(data, mode, pm, dm)
    return {
        "mode": mode,
        "period": period,
        "overall": overall,
        "windows": {w: _mode_metrics(data, mode, *_masks(data, w))[0] for w in WINDOW_DAYS},
        "by_league": _by_league(data, mode, pm, dm),
        "by_market": _by_market(data, mode, pm, dm),
        "by_season": seasons,
        "season_stability": _season_stability(raws, raw),
        "timeline": _timeline(data, mode, dm),
    }


def selection_modes_overview(conn: sqlite3.Connection, as_of: str) -> dict:
    """GET /api/v1/performance/selection-modes — Başarı Merkezi › "Seçim Modları" sekmesi."""
    _check_time(as_of)
    data = _load(conn, as_of)
    pm, dm = _masks(data, "all")
    modes = []
    for mode in MODE_CODES:
        overall, raw = _mode_metrics(data, mode, pm, dm)
        seasons, raws = _by_season(data, mode, pm, dm)
        info = MODE_INFO[mode]
        modes.append({
            "code": mode, "name": info["name"], "short_name": info["short_name"],
            "overall": overall,
            "windows": {w: _mode_metrics(data, mode, *_masks(data, w))[0] for w in WINDOW_DAYS},
            "by_season": seasons,
            "season_stability": _season_stability(raws, raw),
            "timeline": _timeline(data, mode, dm),
        })
    notes = [
        "Modların başarısı birbirinden bağımsız kaydedilir; geçmiş seçimler değiştirilemez.",
        "Geçmiş bir seçim sonradan başka bir modun sonucuymuş gibi gösterilmez.",
        "Taban çizgisi, seçim yapılmadan bütün uygun adayların MODEL B sonucudur; seçim modlarının başarısı "
        "bu çizgiyle birlikte okunmalıdır.",
        "ROI teorik birim bahis getirisidir; yüksek isabet oranı veya pozitif ROI kazanç garantisi değildir.",
    ]
    if data.is_synthetic:
        notes.append("Sonuçlar sentetik veriyle üretilmiştir; gerçek performans iddiası değildir.")
    return {
        "generated_at": as_of,
        "is_synthetic_data": data.is_synthetic,
        "modes": modes,
        "baseline": {"label": BASELINE_LABEL, "metrics": _baseline_metrics(data, pm)},
        "notes": notes,
    }


def history_for(conn: sqlite3.Connection, mode: str, family: str, league_id: str, before: str) -> dict:
    """Öneri kartındaki "geçmiş başarı": modun bu market ailesi ve ligdeki sonuçlanmış seçimleri.

    Yalnızca seçim zamanı `before` anından **kesin önce** olan ve sonucu `before` anında bilinen
    (`settled_at <= before`) kararlar sayılır. `n` iadeler dahil sonuçlanmış seçim sayısıdır; veri yoksa
    `hit_rate` / `roi` `None` olur.
    """
    _check_mode(mode)
    if family not in FAMILY_NAMES:
        raise ValueError(f"Bilinmeyen market ailesi: {family!r}.")
    _check_time(before)
    markets = _FAMILY_MARKETS[family]
    q = ",".join("?" for _ in markets)
    rows = _cursor(conn).execute(
        "SELECT d.settlement_status, d.profit_units FROM selection_mode_decisions d"
        " JOIN fixtures f ON f.id = d.fixture_id"
        f" WHERE d.selection_mode = ? AND d.selection_timestamp < ? AND f.league_id = ? AND d.market_id IN ({q})"
        " AND d.settlement_status <> 'pending' AND d.settled_at IS NOT NULL AND d.settled_at <= ?",
        (mode, before, league_id, *markets, before),
    ).fetchall()
    st = np.array([_STATUS_CODE.get(r[0], PENDING) for r in rows], np.int8)
    profit = np.array([_f(r[1]) for r in rows], float)
    won, lost = int(np.count_nonzero(st == WON)), int(np.count_nonzero(st == LOST))
    realized = _realized_profit(st, profit)
    realized = realized[np.isfinite(realized)]
    return {
        "scope": f"{MODE_INFO[mode]['short_name']} · {FAMILY_NAMES[family]} · {_league_name(league_id)}",
        "n": int(st.size),
        "hit_rate": _r(won / (won + lost)) if won + lost else None,
        "roi": _r(realized.mean()) if realized.size else None,
    }


def snapshot_performance(conn: sqlite3.Connection, as_of: str) -> int:
    """Mod performansının `as_of` anındaki anlık görüntüsünü kaydeder; yazılan satır sayısını döndürür.

    - `selection_mode_performance`: mod × dönem (all/7d/30d/90d) × kapsam (overall, her lig, her market ailesi)
    - `selection_mode_calibration`: mod × dönem (güvenilirlik kutuları, ECE, n)
    - `selection_mode_comparisons`: dönem başına aynı havuz karşılaştırması (havuz özeti ve özeti)

    Her modun kaydı yalnızca kendi kararlarından hesaplanır. Satırlar yalnızca eklenir; aynı `as_of` için
    tekrar çağrı eski satırları değiştirmez, yeni satırlar ekler (`computed_at` ile ayrılır).
    """
    _check_time(as_of)
    data = _load(conn, as_of)
    now = to_iso(utcnow())
    perf_rows: list[dict] = []
    cal_rows: list[dict] = []
    cmp_rows: list[dict] = []

    def perf(mode: str, period: str, scope: str, key: str, metrics: dict) -> None:
        perf_rows.append({"mode": mode, "period": period, "scope": scope, "scope_key": key,
                          "metrics": dumps(metrics), "as_of": as_of, "computed_at": now})

    for period in SNAPSHOT_PERIODS:
        pm, dm = _masks(data, period)
        for mode in MODE_CODES:
            overall, _ = _mode_metrics(data, mode, pm, dm)
            perf(mode, period, "overall", "", overall)
            for row in _by_league(data, mode, pm, dm):
                perf(mode, period, "league", row["league"]["id"], row["metrics"])
            for row in _by_market(data, mode, pm, dm):
                perf(mode, period, "market", row["family"], row["metrics"])
            cal_rows.append({"mode": mode, "period": period, "bins": dumps(overall["calibration_bins"]),
                             "ece": overall["ece"], "n": sum(b["n"] for b in overall["calibration_bins"]),
                             "as_of": as_of, "computed_at": now})
        cmp = _comparison(data, period)
        cmp_rows.append({
            "period": period, "window_start": cmp["window"]["start"], "window_end": cmp["window"]["end"],
            "pool_fixtures": cmp["pool"]["fixtures"], "pool_candidates": cmp["pool"]["candidates"],
            "pool_hash": cmp["pool"]["hash"],
            "metrics": dumps({"baseline": cmp["baseline"], "modes": cmp["modes"], "notes": cmp["notes"]}),
            "as_of": as_of, "computed_at": now,
        })
    with transaction(conn):
        insert_many(conn, "selection_mode_performance", perf_rows)
        insert_many(conn, "selection_mode_calibration", cal_rows)
        insert_many(conn, "selection_mode_comparisons", cmp_rows)
    return len(perf_rows) + len(cal_rows) + len(cmp_rows)
