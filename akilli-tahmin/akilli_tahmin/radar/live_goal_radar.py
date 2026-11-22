"""Canlı Gol Radar — hedef: "Kalan normal sürede en az bir gol".

Kullanıcı talebi (v6.3 sonrası): "Gol radar oranını yüzde 90 yap". Bir alarm yalnızca aşağıdaki şartların
HEPSİ sağlandığında verilir (eşikler yalnızca `SETTINGS.radar` / `config.RadarPolicy`'den okunur):

 1. MODEL A olasılığı > %90 (kesin büyüktür; tam %90 yetmez)
 2. MODEL B olasılığı > %90 (kesin büyüktür)
 3. Normal sürede en az 15 dakika kalması (90 − dakika ≥ 15)
 4. Güncel canlı veri (son canlı durum ≤ 60 sn önce alınmış)
 5. Gerekli güncel canlı oran: o skordaki OU_{toplam+0,5} marketinin "Üst" fiyatı; doğrulanmış kaynak,
    kesin zaman damgası, değerlendirme anından önce ve ≤ 60 sn önce yakalanmış, son skor değişikliğinden
    sonra fiyatlanmış. Toplam + 0,5 > 5,5 ise market yoktur → şart sağlanmaz.
 6. Yeterli nadir olay kalibrasyonu (iki model için ayrı ayrı; bkz. `rare_event_summary`)
 7. Tahmin Kalite Kapısı (canlı bağlam): maç canlı, MODEL A iki takım için yeterli ve güncel veriyle
    uydurulmuş, MODEL B eğitilmiş, olasılıklar sonlu ve [0, 1] aralığında
 8. Kullanıcının bildirim izni (varsayılan: izin yok)
 9. Mükerrer alarm engeli: maç + hedef + skor durumu başına tek alarm (`radar_alerts.dedupe_key` UNIQUE)

Seçim modları (Dengeli / En Güvenilir / En Avantajlı) radarı etkileyemez: bu modüldeki hiçbir fonksiyon
mod parametresi almaz, kullanıcı mod tercihini okumaz ve seçim paketini içe aktarmaz.

Modeller
--------
MODEL A (canlı) — yalnızca futbol verisi:
    Maç öncesi beklenen goller λ_ev, λ_dep, ev sahibi takımın iç ligindeki maçlarla değerlendirme anına
    kadar uydurulmuş `fit_model_a`'dan alınır (kupa maçlarının kendi geçmişi olmadığından lig, `teams`
    tablosundan bulunur). Kalan yoğunluk
        λ_kalan = (λ_ev + λ_dep) · kalan_pay(dakika) · k · kırmızı_kart_çarpanları
    kalan_pay, MODEL A'nın geçmiş ilk yarı skorlarından tahmin ettiği ilk yarı payı h ile iki yarıya
    bölünür: h·max(0, 45 − d)/45 + (1 − h)·min(45, 90 − max(d, 45))/45 (h = 0,5 iken tam olarak
    kalan/90). k, gözlenen canlı xG temposunun maç öncesi tempoya doğru büzülmesidir (gama–Poisson):
        k = (xG_gözlenen + Λ·w0) / (Λ·geçen_pay + Λ·w0),  Λ = λ_ev + λ_dep,  w0 = LIVE_XG_PRIOR_MINUTES / 90
    Kırmızı kart: eksik kalan takımın kalan yoğunluğu × RED_CARD_OWN_FACTOR, rakibinki ×
    RED_CARD_OPPONENT_FACTOR (kart başına).
    Olasılık: p_A = 1 − (1 + λ_kalan/κ)^(−κ). Bu, maç temposundaki belirsizliğin (takım güçlerinin tahmin
    hatası, maç içi tempo farkı) gama dağılımıyla temsil edildiği gama–Poisson (negatif binom) öngörü
    dağılımıdır; κ → ∞ iken tam olarak p_A = 1 − exp(−λ_kalan) olur. Tek parametre κ ("tempo
    dağılımı"), yalnızca futbol verisinden (geçmiş anlık durumlardaki gol sonuçları) en çok olabilirlikle
    ve kronolojik olarak kestirilir. Saf Poisson, yüksek olasılık bölgesinde gol olmama ihtimalini
    sistematik olarak küçümser (aşırı güven); κ bunu düzeltir.
    Girdi kayıtları `assert_football_only` ile denetlenir; oran alanı içeren girdi `OddsIsolationError` verir.

MODEL B (canlı) — futbol verisi + out-of-fold MODEL A + tahmin anında bilinen canlı oran:
    logit p_B = c0 + c1·logit(p_A) + c2·logit(p_piyasa),  önsel ortalama (0, 1, 0)
    Yalnızca "Üst" tarafı fiyatlandığından adil olasılık p_piyasa ≈ (1/oran) / (1 + LIVE_MARKET_MARGIN)
    olarak yaklaşıklanır. Katsayılar, değerlendirme gününün başlangıcına (UTC) kadar sonucu kaydedilmiş
    maçların geçmiş canlı anlık durumlarıyla (hedef: dakikadan sonra gol) kronolojik olarak eğitilir.
    Eğitimdeki p_A değerleri out-of-fold'dur: λ'lar her maçın başlama gününe kadar uydurulmuş MODEL A'dan,
    κ ise o maçın ayından önce sonucu bilinen anlık durumlardan kestirilir. Örneklem yetersizse MODEL B
    geçersizdir → alarm verilmez.

Nadir olay kalibrasyonu: geçmiş anlık durumlarda modelin eşik üstü (> %90) tahminleri için örnek sayısı,
ortalama tahmin, gözlenen oran ve %95 Wilson aralığı. Yeterli ⇔ n ≥ min_rare_event_samples VE aralık
ortalama tahmini içerir (ne aşırı ne yetersiz güvenli). İki model için de aylık genişleyen pencereyle
(yalnızca o aydan önce sonucu kaydedilmiş maçlarla kestirilip/eğitilip o ay tahmin edilerek) üretilmiş
kronolojik out-of-fold tahminler kullanılır. Sonuçlar her kalibrasyon çalıştırmasında model başına bir
satır olarak `radar_calibration` tablosuna yazılır ve süreç içinde kesim günü başına önbelleğe alınır.
"""
from __future__ import annotations

import math
import sqlite3
from collections import OrderedDict
from dataclasses import dataclass, fields, is_dataclass
from typing import Any, Mapping

import numpy as np

from ..calibration.metrics import wilson_interval
from ..config import SETTINGS, Settings
from ..data.point_in_time import FootballDataView
from ..data.synthetic_live import historical_snapshots
from ..db.database import dumps, from_iso, insert, iso_add, to_iso, transaction, utcnow
from ..domain.markets import MARKETS_BY_ID
from ..models.isolation import assert_football_only, assert_known_before
from ..models.logistic import LogisticFit, fit_logistic, logit
from ..models.model_a import ModelAFit, fit_model_a
from ..odds.odds import OddsQuote, validate_quote

RADAR_TARGET = "Kalan normal sürede en az bir gol"
#: Mükerrer alarm anahtarında kullanılan kararlı hedef kodu.
RADAR_TARGET_CODE = "goal_in_remaining_regular_time"
RADAR_VERSION = "radar-live-1.0"

REGULAR_TIME = 90
HALF_TIME = 45

#: MODEL A canlı: canlı xG temposunun maç öncesi tempoya doğru büzülmesindeki önsel ağırlık (sözde dakika).
#: 90 → maç öncesi beklenti bir maçlık gözlem ağırlığındadır; 30. dakikada gözlenen tempo %25 ağırlık alır.
LIVE_XG_PRIOR_MINUTES = 90.0
#: Kırmızı kart düzeltmesi (kart başına, yaklaşık literatür etkileri): eksik takımın gol yoğunluğu × 0,70,
#: rakibin × 1,20.
RED_CARD_OWN_FACTOR = 0.70
RED_CARD_OPPONENT_FACTOR = 1.20
#: MODEL A canlı tempo dağılımı κ için en çok olabilirlik taraması (∞ = saf Poisson ayrıca denenir).
DISPERSION_GRID: tuple[float, ...] = tuple(float(x) for x in np.geomspace(2.0, 400.0, 48))
#: Geçmiş veri yetersizken kullanılan ihtiyatlı κ (küçük κ → daha düşük olasılık).
DEFAULT_DISPERSION = 10.0
#: Canlı "kalan sürede gol" (Üst) fiyatlarının tipik marjı; yalnızca tek taraf fiyatlandığından adil
#: olasılık (1/oran)/(1+marj) olarak yaklaşıklanır. Sentetik canlı oran üreticisinin marjıyla aynıdır.
LIVE_MARKET_MARGIN = 0.06
#: MODEL B canlı lojistik birleştiricisinin önsel ortalaması ve sırt cezası: [sabit, logit p_A, logit p_piyasa].
MODEL_B_PRIOR = (0.0, 1.0, 0.0)
MODEL_B_RIDGE = (0.5, 2.0, 2.0)

#: Şartlar bu sırayla değerlendirilir; `blocked_reason` ilk sağlanmayan şartın etiketidir.
CONDITION_CODES: tuple[str, ...] = (
    "model_a_threshold", "model_b_threshold", "remaining_time", "live_data_fresh", "live_odds_fresh",
    "rare_event_calibration", "quality_gate", "notification_permission", "duplicate_block",
)

_CACHE_SIZE = 8
_HISTORY_CACHE: "OrderedDict[tuple, LiveHistory]" = OrderedDict()


# ───────────────────────── yapılandırma ─────────────────────────

def _pct_label(x: float) -> str:
    return f"{100 * x:.10g}".replace(".", ",")


def _pct(x: float | None, digits: int = 1) -> str:
    if x is None or not math.isfinite(x):
        return "—"
    return "%" + f"{100 * x:.{digits}f}".replace(".", ",")


def condition_labels(settings: Settings = SETTINGS) -> dict[str, str]:
    """Şart kodlarının Türkçe etiketleri (eşikler `settings.radar`'dan)."""
    rp = settings.radar
    return {
        "model_a_threshold": f"MODEL A > %{_pct_label(rp.model_a_min_probability)}",
        "model_b_threshold": f"MODEL B > %{_pct_label(rp.model_b_min_probability)}",
        "remaining_time": f"≥ {rp.min_remaining_minutes} dk kalan normal süre",
        "live_data_fresh": "Güncel canlı veri",
        "live_odds_fresh": "Gerekli güncel canlı oran",
        "rare_event_calibration": "Yeterli nadir olay kalibrasyonu",
        "quality_gate": "Tahmin Kalite Kapısı",
        "notification_permission": "Bildirim izni",
        "duplicate_block": "Mükerrer alarm engeli",
    }


def radar_config(settings: Settings = SETTINGS) -> dict:
    """GET /api/v1/radar/config — radarın zorunlu şartları. Seçim modu veya kullanıcı tercihi okunmaz."""
    rp = settings.radar
    if rp.model_a_min_probability == 0.90 and rp.model_b_min_probability == 0.90:
        note = "Eşik kullanıcı talebiyle %99'dan %90'a indirildi (v6.3). Seçim modları bu eşiği değiştiremez."
    else:  # pragma: no cover - yalnızca özel yapılandırmada
        note = (f"Eşikler MODEL A > %{_pct_label(rp.model_a_min_probability)}, MODEL B > "
                f"%{_pct_label(rp.model_b_min_probability)} olarak yapılandırıldı. Seçim modları bu eşiği değiştiremez.")
    return {
        "target": RADAR_TARGET,
        "model_a_min_probability": rp.model_a_min_probability,
        "model_b_min_probability": rp.model_b_min_probability,
        "min_remaining_minutes": rp.min_remaining_minutes,
        "live_data_max_age_seconds": rp.live_data_max_age_seconds,
        "live_odds_max_age_seconds": rp.live_odds_max_age_seconds,
        "requires_rare_event_calibration": True,
        "requires_quality_gate": True,
        "requires_notification_permission": True,
        "duplicate_alarm_block": bool(rp.duplicate_block),
        "note": note,
    }


# ───────────────────────── MODEL A (canlı) ─────────────────────────

@dataclass(frozen=True)
class LiveFootballState:
    """MODEL A'nın canlı olarak görebildiği tek kayıt türü: yalnızca futbol verisi (oran alanı yoktur)."""

    fixture_id: int
    minute: int
    home_goals: int
    away_goals: int
    home_xg: float | None = None
    away_xg: float | None = None
    home_red: int = 0
    away_red: int = 0


_STATE_FIELDS = tuple(f.name for f in fields(LiveFootballState))


def _state_get(state: Any, name: str, default: Any = None) -> Any:
    if isinstance(state, Mapping):
        return state.get(name, default)
    return getattr(state, name, default)


def remaining_share(minute: float, ht_share: float = 0.5) -> float:
    """Normal sürenin `minute` sonrasına düşen beklenen gol payı (iki yarıya `ht_share` ile bölünmüş)."""
    m = min(max(float(minute), 0.0), float(REGULAR_TIME))
    first = ht_share * max(0.0, HALF_TIME - m) / HALF_TIME
    second = (1.0 - ht_share) * min(float(HALF_TIME), REGULAR_TIME - max(m, float(HALF_TIME))) / HALF_TIME
    return first + second


def _remaining_lambda(lam_home: float, lam_away: float, minute: float, home_xg: float | None,
                      away_xg: float | None, home_red: int, away_red: int, ht_share: float) -> float:
    share_rem = remaining_share(minute, ht_share)
    if share_rem <= 0.0:
        return 0.0
    total = lam_home + lam_away
    k = 1.0
    if home_xg is not None and away_xg is not None and total > 0:
        xg = float(home_xg) + float(away_xg)
        if math.isfinite(xg) and xg >= 0:
            prior = total * LIVE_XG_PRIOR_MINUTES / REGULAR_TIME
            k = (xg + prior) / (total * (1.0 - share_rem) + prior)
    hr, ar = int(home_red or 0), int(away_red or 0)
    lh = lam_home * share_rem * k * RED_CARD_OWN_FACTOR ** hr * RED_CARD_OPPONENT_FACTOR ** ar
    la = lam_away * share_rem * k * RED_CARD_OWN_FACTOR ** ar * RED_CARD_OPPONENT_FACTOR ** hr
    return lh + la


def no_goal_probability(lam_remaining: np.ndarray | float, dispersion: float | None) -> np.ndarray:
    """Kalan sürede gol olmama olasılığı: gama–Poisson (1 + λ/κ)^(−κ); κ None/∞ → exp(−λ)."""
    lam = np.maximum(np.asarray(lam_remaining, float), 0.0)
    if dispersion is None or not math.isfinite(dispersion):
        return np.exp(-lam)
    return np.power(1.0 + lam / dispersion, -dispersion)


def fit_dispersion(lam_remaining: np.ndarray, y: np.ndarray) -> float:
    """MODEL A tempo dağılımı κ'nın en çok olabilirlik kestirimi (ızgara + saf Poisson). Yalnızca futbol verisi."""
    lam = np.maximum(np.asarray(lam_remaining, float), 0.0)
    y = np.asarray(y, float)
    best_k, best_ll = math.inf, -math.inf
    for k in (*DISPERSION_GRID, math.inf):
        p0 = np.clip(no_goal_probability(lam, k), 1e-12, 1 - 1e-12)
        ll = float(np.sum(y * np.log1p(-p0) + (1 - y) * np.log(p0)))
        if ll > best_ll:
            best_k, best_ll = k, ll
    return best_k


def model_a_live_probability(lam_home: float, lam_away: float, state: Any, *, ht_share: float = 0.5,
                             dispersion: float | None = None) -> float:
    """MODEL A (canlı): kalan normal sürede en az bir gol olasılığı, p_A = 1 − (1 + λ_kalan/κ)^(−κ).

    `dispersion` (κ) None ise saf Poisson: p_A = 1 − exp(−λ_kalan). `state`: `LiveFootballState` veya aynı
    alanları taşıyan sözlük. Oranla ilişkili herhangi bir alan (ör. `odds`, `price`, `implied`)
    `OddsIsolationError` ile reddedilir.
    """
    assert_football_only([state], context="MODEL A (canlı)")
    lam = _remaining_lambda(float(lam_home), float(lam_away), _state_get(state, "minute", 0),
                            _state_get(state, "home_xg"), _state_get(state, "away_xg"),
                            _state_get(state, "home_red", 0), _state_get(state, "away_red", 0), ht_share)
    return float(1.0 - no_goal_probability(lam, dispersion))


def _model_a_ready(fit: ModelAFit, home: int, away: int, at: str, settings: Settings) -> tuple[bool, str]:
    dp = settings.data
    if not fit.valid:
        return False, f"MODEL A uydurulamadı ({fit.league_id}): {fit.invalid_reason}"
    nh, na = fit.team_sample(home), fit.team_sample(away)
    if nh < dp.min_team_matches or na < dp.min_team_matches:
        return False, (f"MODEL A takım verisi yetersiz ({fit.league_id}): ev sahibi {nh}, deplasman {na} maç "
                       f"(en az {dp.min_team_matches})")
    if fit.last_result_at is None or fit.last_result_at < iso_add(at, days=-dp.max_result_data_age_days):
        return False, f"MODEL A sonuç verisi eski (son sonuç {fit.last_result_at})"
    return True, f"MODEL A geçerli ({fit.league_id}, {fit.n_matches} maç; takımlar {nh}/{na} maç)"


# ───────────────────────── MODEL B (canlı) ─────────────────────────

def live_market_probability(decimal_odds: float) -> float:
    """Tek taraflı canlı Üst fiyatından yaklaşık adil olasılık: (1/oran)/(1+LIVE_MARKET_MARGIN)."""
    return float(min(max((1.0 / float(decimal_odds)) / (1.0 + LIVE_MARKET_MARGIN), 1e-6), 1 - 1e-6))


def model_b_design(p_a: np.ndarray | float, p_market: np.ndarray | float) -> np.ndarray:
    p_a = np.atleast_1d(np.asarray(p_a, float))
    p_m = np.atleast_1d(np.asarray(p_market, float))
    return np.stack([np.ones_like(p_a), logit(p_a), logit(p_m)], axis=-1)


def train_live_model_b(p_a: np.ndarray, p_market: np.ndarray, y: np.ndarray, *, min_samples: int) -> LogisticFit | None:
    """Kronolojik geçmiş anlık durumlarla MODEL B (canlı) eğitimi; örneklem yetersizse None."""
    y = np.asarray(y, float)
    if y.size < min_samples:
        return None
    return fit_logistic(model_b_design(p_a, p_market), y, ridge=np.array(MODEL_B_RIDGE),
                        prior_mean=np.array(MODEL_B_PRIOR))


def model_b_live_probability(fit: LogisticFit | None, p_a: float | None, decimal_odds: float | None) -> float | None:
    """MODEL B (canlı) olasılığı; model, MODEL A olasılığı veya canlı oran yoksa None."""
    if fit is None or p_a is None or decimal_odds is None or not (decimal_odds > 1.0):
        return None
    return float(fit.predict(model_b_design(p_a, live_market_probability(decimal_odds)))[0])


# ───────────────────────── nadir olay kalibrasyonu ─────────────────────────

@dataclass(frozen=True)
class RareEventCalibration:
    model: str
    region_low: float
    n: int
    mean_predicted: float | None
    observed_rate: float | None
    ci_low: float | None
    ci_high: float | None
    adequate: bool
    reason: str
    data_cutoff_at: str

    def detail(self) -> str:
        if self.n == 0:
            return f"MODEL {self.model}: {self.reason}"
        return (f"MODEL {self.model}: {self.reason} — n = {self.n}, ortalama tahmin {_pct(self.mean_predicted)}, "
                f"gözlenen {_pct(self.observed_rate)} (%95 Wilson {_pct(self.ci_low)}–{_pct(self.ci_high)})")


def rare_event_summary(model: str, p: np.ndarray, y: np.ndarray, *, threshold: float, min_samples: int,
                       data_cutoff_at: str) -> RareEventCalibration:
    """Eşik üstü (p > threshold) "nadir olay" bölgesinde kalibrasyon yeterliliği."""
    p, y = np.asarray(p, float), np.asarray(y, float)
    ok = np.isfinite(p) & np.isfinite(y)
    m = ok & (p > threshold)
    n = int(m.sum())
    if n == 0:
        return RareEventCalibration(model, threshold, 0, None, None, None, None, False,
                                    f"Nadir olay bölgesinde (> %{_pct_label(threshold)}) geçmiş örnek yok", data_cutoff_at)
    k = int(y[m].sum())
    mean_pred = float(p[m].mean())
    lo, hi = wilson_interval(k, n)
    if n < min_samples:
        adequate, reason = False, f"Örneklem yetersiz ({n} < {min_samples})"
    elif mean_pred > hi:
        adequate, reason = False, "Aşırı güvenli: ortalama tahmin güven aralığının üstünde"
    elif mean_pred < lo:
        adequate, reason = False, "Yetersiz güvenli: ortalama tahmin güven aralığının altında"
    else:
        adequate, reason = True, "Yeterli"
    return RareEventCalibration(model, threshold, n, mean_pred, k / n, lo, hi, adequate, reason, data_cutoff_at)


@dataclass
class LiveHistory:
    """Bir kalibrasyon kesimine ait geçmiş canlı veri özetleri (süreç içinde önbelleğe alınır)."""

    data_cutoff_at: str
    n_snapshots: int
    model_b: LogisticFit | None
    model_b_rows: int
    model_b_reason: str | None
    calibration: dict[str, RareEventCalibration]


def calibration_cutoff(now: str) -> str:
    """Kalibrasyon ve MODEL B eğitim kesimi: değerlendirme gününün başlangıcı (UTC)."""
    return now[:10] + "T00:00:00Z"


def clear_cache() -> None:
    _HISTORY_CACHE.clear()


def _cache_key(conn: sqlite3.Connection, cutoff: str, settings: Settings) -> tuple:
    db = conn.execute("PRAGMA database_list").fetchone()
    path = db[2] if db is not None else ""
    ident = path or f"memory:{id(conn)}"
    fx = tuple(conn.execute(
        "SELECT COUNT(*), MAX(result_recorded_at), MAX(id) FROM fixtures"
        " WHERE result_recorded_at IS NOT NULL AND result_recorded_at <= ?", (cutoff,)).fetchone())
    ge = tuple(conn.execute("SELECT COUNT(*), MAX(id) FROM goal_events").fetchone())
    return (ident, cutoff, fx, ge, settings.radar, settings.data, settings.stats.min_model_b_samples)


def live_history(conn: sqlite3.Connection, cutoff: str, *, settings: Settings = SETTINGS) -> LiveHistory:
    """Kesime ait geçmiş canlı özetler (önbellekli). İlk hesaplamada `radar_calibration` satırları yazılır."""
    key = _cache_key(conn, cutoff, settings)
    hit = _HISTORY_CACHE.get(key)
    if hit is not None:
        _HISTORY_CACHE.move_to_end(key)
        return hit
    hist = _build_history(conn, cutoff, settings)
    _store_calibration(conn, hist.calibration.values())
    _HISTORY_CACHE[key] = hist
    while len(_HISTORY_CACHE) > _CACHE_SIZE:
        _HISTORY_CACHE.popitem(last=False)
    return hist


def rare_event_calibration(conn: sqlite3.Connection, cutoff: str, *,
                           settings: Settings = SETTINGS) -> dict[str, RareEventCalibration]:
    """İki model için nadir olay kalibrasyonu: {"A": …, "B": …}."""
    return live_history(conn, cutoff, settings=settings).calibration


def _build_history(conn: sqlite3.Connection, cutoff: str, settings: Settings) -> LiveHistory:
    rp, dp = settings.radar, settings.data
    min_b = settings.stats.min_model_b_samples
    snaps = historical_snapshots(conn, cutoff)
    view = FootballDataView(conn)
    team_league = {int(r[0]): r[1] for r in conn.execute("SELECT id, league_id FROM teams")}
    fits: dict[tuple[str, str], ModelAFit] = {}
    pre: dict[int, tuple[float, float, float] | None] = {}
    states: list[LiveFootballState] = []
    lam, ys, odds, known, kick, ident = [], [], [], [], [], []
    for s in snaps:
        fid, home, away = s["fixture_id"], s["home_team_id"], s["away_team_id"]
        if fid not in pre:
            lg = team_league.get(home, s["league_id"])
            day = s["kickoff_at"][:10] + "T00:00:00Z"
            fit = fits.get((lg, day))
            if fit is None:
                fit = fits[(lg, day)] = fit_model_a(view.all_records(lg), day, lg, min_matches=dp.min_league_matches)
            ok, _ = _model_a_ready(fit, home, away, day, settings)
            pre[fid] = (*fit.expected_goals(home, away), fit.ht_share) if ok else None
        if pre[fid] is None:
            continue  # canlıda da Kalite Kapısı'ndan geçemeyecek durum: kalibrasyon bölgesine alınmaz
        # MODEL A'ya yalnızca futbol alanları geçer (anlık durumdaki fiyat alanları ayıklanır).
        states.append(LiveFootballState(**{k: s[k] for k in _STATE_FIELDS}))
        lam.append(pre[fid])
        ys.append(s["outcome"])
        odds.append(np.nan if s["over_odds"] is None else float(s["over_odds"]))
        known.append(s["result_recorded_at"])
        kick.append(s["kickoff_at"])
        ident.append(f"{fid}@{s['minute']}")
    assert_football_only(states, context="MODEL A (canlı geçmiş)")
    pa = np.array([1.0 - math.exp(-_remaining_lambda(lh, la, st.minute, st.home_xg, st.away_xg,
                                                      st.home_red, st.away_red, ht))
                   for st, (lh, la, ht) in zip(states, lam)], dtype=float)
    y = np.array(ys, dtype=float)
    odds_arr = np.array(odds, dtype=float)
    known_arr = np.array(known, dtype=object)
    kick_arr = np.array(kick, dtype=object)
    ident_arr = np.array(ident, dtype=object)

    cal_a = rare_event_summary("A", pa, y, threshold=rp.model_a_min_probability,
                               min_samples=rp.min_rare_event_samples, data_cutoff_at=cutoff)

    has = np.isfinite(odds_arr)
    pm = np.full(y.size, np.nan)
    if has.any():
        pm[has] = [live_market_probability(o) for o in odds_arr[has]]
    # MODEL B kronolojik out-of-fold: her ay, yalnızca o aydan önce sonucu kaydedilmiş maçlarla eğitilir.
    pb_oof = np.full(y.size, np.nan)
    month = np.array([k[:7] for k in kick], dtype=object)
    for mon in sorted(set(month[has])):
        start = f"{mon}-01T00:00:00Z"
        tr = has & (known_arr < start)
        te = has & (month == mon)
        if tr.sum() < min_b or not te.any():
            continue
        assert_known_before(zip(ident_arr[tr], known_arr[tr]), start, what="MODEL B (canlı) eğitim sonucu")
        f = train_live_model_b(pa[tr], pm[tr], y[tr], min_samples=min_b)
        if f is not None:
            pb_oof[te] = f.predict(model_b_design(pa[te], pm[te]))
    cal_b = rare_event_summary("B", pb_oof, y, threshold=rp.model_b_min_probability,
                               min_samples=rp.min_rare_event_samples, data_cutoff_at=cutoff)

    n_b = int(has.sum())
    model_b = None
    reason = f"MODEL B (canlı) eğitim örneklemi yetersiz ({n_b} < {min_b})"
    if n_b >= min_b:
        assert_known_before(zip(ident_arr[has], known_arr[has]), cutoff, what="MODEL B (canlı) eğitim sonucu")
        model_b = train_live_model_b(pa[has], pm[has], y[has], min_samples=min_b)
        reason = None
    return LiveHistory(cutoff, len(snaps), model_b, n_b, reason, {"A": cal_a, "B": cal_b})


def _store_calibration(conn: sqlite3.Connection, cals) -> None:
    now = to_iso(utcnow())
    with transaction(conn):
        for c in cals:
            exists = conn.execute(
                "SELECT 1 FROM radar_calibration WHERE model=? AND region_low=? AND data_cutoff_at=? AND n=?"
                " AND adequate=? LIMIT 1", (c.model, c.region_low, c.data_cutoff_at, c.n, int(c.adequate)),
            ).fetchone()
            if exists:
                continue
            insert(conn, "radar_calibration", {
                "model": c.model, "region_low": c.region_low, "n": c.n, "mean_predicted": c.mean_predicted,
                "observed_rate": c.observed_rate, "ci_low": c.ci_low, "adequate": int(c.adequate),
                "data_cutoff_at": c.data_cutoff_at, "created_at": now,
            })


# ───────────────────────── bildirim izni ve alarmlar ─────────────────────────

def notification_permission_granted(conn: sqlite3.Connection, user_id: str) -> bool:
    row = conn.execute("SELECT granted FROM notification_permissions WHERE user_id=?", (user_id,)).fetchone()
    return bool(row[0]) if row is not None else False


def set_notification_permission(conn: sqlite3.Connection, user_id: str, granted: bool, now: str) -> dict:
    """POST /api/v1/radar/notifications/permission."""
    conn.execute(
        "INSERT INTO notification_permissions(user_id, granted, updated_at) VALUES (?,?,?)"
        " ON CONFLICT(user_id) DO UPDATE SET granted=excluded.granted, updated_at=excluded.updated_at",
        (user_id, 1 if granted else 0, now),
    )
    return {"user_id": user_id, "granted": bool(granted)}


def list_alerts(conn: sqlite3.Connection, limit: int = 50) -> dict:
    """GET /api/v1/radar/alerts — en yeni alarmlar önce."""
    limit = max(1, min(int(limit), 500))
    rows = conn.execute(
        "SELECT id, fixture_id, minute, model_a_probability, model_b_probability, created_at, notified"
        " FROM radar_alerts ORDER BY created_at DESC, id DESC LIMIT ?", (limit,),
    ).fetchall()
    return {"items": [{
        "id": r[0], "fixture_id": r[1], "minute": r[2], "model_a_probability": r[3],
        "model_b_probability": r[4], "created_at": r[5], "notified": bool(r[6]),
    } for r in rows]}


def dedupe_key(fixture_id: int, home_goals: int, away_goals: int) -> str:
    """Mükerrer alarm anahtarı: maç + hedef + skor durumu."""
    return f"{fixture_id}:{RADAR_TARGET_CODE}:{home_goals}-{away_goals}"


# ───────────────────────── canlı değerlendirme ─────────────────────────

def _latest_state(conn: sqlite3.Connection, fixture_id: int, now: str) -> tuple[sqlite3.Row | None, int | None]:
    """Son canlı durum ve mevcut skorun ilk gözlendiği dakika (son ardışık aynı skorlu kayıtlar)."""
    rows = conn.execute(
        "SELECT * FROM live_states WHERE fixture_id=? AND captured_at<=? ORDER BY captured_at DESC, id DESC LIMIT 50",
        (fixture_id, now),
    ).fetchall()
    if not rows:
        return None, None
    since = rows[0]["minute"]
    for r in rows:
        if (r["home_goals"], r["away_goals"]) != (rows[0]["home_goals"], rows[0]["away_goals"]):
            break
        since = r["minute"]
    return rows[0], since


def _live_quotes(conn: sqlite3.Connection, fixture_id: int, market_id: str, now: str) -> list[tuple[OddsQuote, int | None]]:
    rows = conn.execute(
        """
        SELECT o.id, o.fixture_id, o.market_id, o.outcome_id, o.decimal_odds, o.source, s.label, s.source_type,
               s.is_iddaa, s.verified, o.captured_at, o.is_live, o.timestamp_precision, o.minute
        FROM odds_snapshots o JOIN odds_sources s ON s.code = o.source
        WHERE o.fixture_id=? AND o.market_id=? AND o.outcome_id='OVER' AND o.is_live=1 AND o.captured_at<=?
        ORDER BY o.captured_at DESC, o.id DESC LIMIT 20
        """,
        (fixture_id, market_id, now),
    ).fetchall()
    return [(OddsQuote(snapshot_id=r[0], fixture_id=r[1], market_id=r[2], outcome_id=r[3], decimal_odds=float(r[4]),
                       source=r[5], source_label=r[6], source_type=r[7], is_iddaa=bool(r[8]),
                       source_verified=bool(r[9]), captured_at=r[10], is_live=bool(r[11]),
                       timestamp_precision=r[12]), r[13]) for r in rows]


def _pick_quote(quotes: list[tuple[OddsQuote, int | None]], now: str, max_age: float,
                score_since: int | None) -> tuple[OddsQuote | None, bool, str]:
    """En yeni geçerli oranı seçer; yoksa en yeni oranı ve geçersizlik gerekçesini döndürür."""
    first: tuple[OddsQuote, str] | None = None
    for q, q_minute in quotes:
        v = validate_quote(q, now, max_age, live=True)
        ok, detail = v.valid, v.detail
        if ok and q_minute is not None and score_since is not None and q_minute < score_since:
            ok, detail = False, "Oran son skor değişikliğinden önce fiyatlanmış; güncel skorla uyumsuz."
        if ok:
            return q, True, detail
        if first is None:
            first = (q, detail)
    if first is None:
        return None, False, ""
    return first[0], False, first[1]


def _quote_matches_score(quotes: list[tuple[OddsQuote, int | None]], q: OddsQuote | None,
                         score_since: int | None) -> bool:
    for cand, q_minute in quotes:
        if q is not None and cand.snapshot_id == q.snapshot_id:
            return q_minute is None or score_since is None or q_minute >= score_since
    return False


def evaluate_live(conn: sqlite3.Connection, now: str, *, user_id: str = "local",
                  settings: Settings = SETTINGS) -> dict:
    """GET /api/v1/radar/live — canlı maçların radar değerlendirmesi.

    Her maç için `radar_evaluations` satırı yazılır; dokuz şartın tamamı sağlanırsa `radar_alerts`
    satırı oluşturulur. Aynı maç + hedef + skor için alarm daha önce verilmişse yeni alarm oluşmaz:
    öğe mevcut alarmı (`alert_id`) gösterir; diğer sekiz şart hâlâ sağlanıyorsa `alarm` true kalır
    (alarm geçerli), `blocked_reason` "Mükerrer alarm engeli" olur.
    """
    rp = settings.radar
    labels = condition_labels(settings)
    fixtures = conn.execute(
        """
        SELECT f.id, f.league_id, f.home_team_id, f.away_team_id, f.kickoff_at, f.status,
               l.name AS league_name, th.name AS home_name, ta.name AS away_name, th.league_id AS home_league
        FROM fixtures f JOIN leagues l ON l.id = f.league_id
             JOIN teams th ON th.id = f.home_team_id JOIN teams ta ON ta.id = f.away_team_id
        WHERE f.status = 'live' ORDER BY f.kickoff_at, f.id
        """
    ).fetchall()
    if not fixtures:
        return {"evaluated_at": now, "items": []}

    cal_cut = calibration_cutoff(now)
    hist = live_history(conn, cal_cut, settings=settings)
    calib = rare_event_calibration(conn, cal_cut, settings=settings)
    permitted = notification_permission_granted(conn, user_id)
    view = FootballDataView(conn)
    fits: dict[str, ModelAFit] = {}
    items = []
    with transaction(conn):
        for f in fixtures:
            lg = f["home_league"]
            if lg not in fits:
                fits[lg] = fit_model_a(view.all_records(lg), now, lg, min_matches=settings.data.min_league_matches)
            items.append(_evaluate_fixture(conn, f, now, user_id, settings, labels, hist, calib, permitted, fits[lg]))
    return {"evaluated_at": now, "items": items}


def _evaluate_fixture(conn: sqlite3.Connection, f: sqlite3.Row, now: str, user_id: str, settings: Settings,
                      labels: dict[str, str], hist: LiveHistory, calib: dict[str, RareEventCalibration],
                      permitted: bool, fit: ModelAFit) -> dict:
    rp = settings.radar
    fid, home, away = f["id"], f["home_team_id"], f["away_team_id"]
    state, score_since = _latest_state(conn, fid, now)
    checks: dict[str, tuple[bool, str]] = {}

    minute = int(state["minute"]) if state is not None else None
    hg = int(state["home_goals"]) if state is not None else None
    ag = int(state["away_goals"]) if state is not None else None
    remaining = max(0, REGULAR_TIME - minute) if minute is not None else None

    # MODEL A — yalnızca futbol verisi
    a_ok, a_reason = _model_a_ready(fit, home, away, now, settings)
    p_a: float | None = None
    if state is not None and a_ok:
        lh, la = fit.expected_goals(home, away)
        live = LiveFootballState(fid, minute, hg, ag, state["home_xg"], state["away_xg"],
                                 int(state["home_red"] or 0), int(state["away_red"] or 0))
        p_a = model_a_live_probability(lh, la, live, ht_share=fit.ht_share)

    # Gerekli canlı oran: o skorda "kalan sürede gol" = OU_{toplam+0,5} Üst
    market_id = None
    quote: OddsQuote | None = None
    odds_ok, odds_detail = False, "Canlı durum olmadan gerekli market belirlenemiyor."
    quotes: list = []
    if state is not None:
        line = hg + ag + 0.5
        cand = f"OU_{line}"
        if cand in MARKETS_BY_ID:
            market_id = cand
            quotes = _live_quotes(conn, fid, market_id, now)
            quote, odds_ok, why = _pick_quote(quotes, now, rp.live_odds_max_age_seconds, score_since)
            mname = MARKETS_BY_ID[market_id].name
            if quote is None:
                odds_detail = f"{mname} Üst için canlı oran bulunamadı."
            elif odds_ok:
                age = int((from_iso(now) - from_iso(quote.captured_at)).total_seconds())
                price = f"{quote.decimal_odds:.2f}".replace(".", ",")
                odds_detail = (f"{mname} Üst {price} · {age} sn önce · {quote.source_label} "
                               f"(sınır {rp.live_odds_max_age_seconds} sn)")
            else:
                odds_detail = f"{mname} Üst: {why}"
        else:
            odds_detail = (f"Skor {hg}-{ag}: Alt/Üst {str(line).replace('.', ',')} marketi yok (en yüksek çizgi 5,5); "
                           "gerekli oran bulunamıyor.")

    # MODEL B — MODEL A + tahmin anında bilinen canlı oran (skorla uyumlu olmalı)
    p_b: float | None = None
    if quote is not None and _quote_matches_score(quotes, quote, score_since):
        p_b = model_b_live_probability(hist.model_b, p_a, quote.decimal_odds)

    # 1–2: eşikler (kesin büyüktür)
    for code, model, p, thr, reason in (
        ("model_a_threshold", "A", p_a, rp.model_a_min_probability, a_reason if not a_ok else "canlı durum yok"),
        ("model_b_threshold", "B", p_b, rp.model_b_min_probability, _b_missing_reason(hist, p_a, quote, market_id)),
    ):
        if p is None:
            checks[code] = (False, f"MODEL {model} olasılığı hesaplanamadı: {reason}.")
        elif p > thr:
            checks[code] = (True, f"MODEL {model} {_pct(p, 2)} > %{_pct_label(thr)}")
        else:
            checks[code] = (False, f"MODEL {model} {_pct(p, 2)} ≤ %{_pct_label(thr)} (eşik kesin büyüktür)")

    # 3: kalan süre
    if remaining is None:
        checks["remaining_time"] = (False, "Canlı durum yok; kalan süre bilinmiyor.")
    else:
        checks["remaining_time"] = (remaining >= rp.min_remaining_minutes,
                                    f"{remaining} dk kaldı (en az {rp.min_remaining_minutes} dk gerekli)")

    # 4: canlı veri güncelliği
    if state is None:
        checks["live_data_fresh"] = (False, "Canlı veri bulunamadı.")
    else:
        age = (from_iso(now) - from_iso(state["captured_at"])).total_seconds()
        checks["live_data_fresh"] = (age <= rp.live_data_max_age_seconds,
                                     f"Canlı veri {int(age)} sn önce alındı (sınır {rp.live_data_max_age_seconds} sn)")

    # 5: gerekli güncel canlı oran
    checks["live_odds_fresh"] = (odds_ok, odds_detail)

    # 6: nadir olay kalibrasyonu (iki model)
    ca, cb = calib.get("A"), calib.get("B")
    cal_ok = bool(ca is not None and ca.adequate and cb is not None and cb.adequate)
    checks["rare_event_calibration"] = (cal_ok, " · ".join(
        c.detail() if c is not None else f"MODEL {m}: kalibrasyon yok" for m, c in (("A", ca), ("B", cb))))

    # 7: Tahmin Kalite Kapısı (canlı bağlam)
    gate: list[tuple[bool, str]] = [
        (f["status"] == "live", "Maç canlı" if f["status"] == "live" else f"Maç canlı değil ({f['status']})"),
        (a_ok, a_reason),
        (hist.model_b is not None,
         f"MODEL B geçerli ({hist.model_b_rows} geçmiş anlık durum)" if hist.model_b is not None else hist.model_b_reason),
    ]
    probs_ok = all(p is not None and math.isfinite(p) and 0.0 <= p <= 1.0 for p in (p_a, p_b))
    gate.append((probs_ok, "Olasılıklar sonlu ve tutarlı" if probs_ok else "MODEL A/B olasılıkları eksik veya geçersiz"))
    gate_ok = all(ok for ok, _ in gate)
    checks["quality_gate"] = (gate_ok, "Geçti: " + "; ".join(d for _, d in gate) if gate_ok
                              else "; ".join(d for ok, d in gate if not ok))

    # 8: bildirim izni
    checks["notification_permission"] = (permitted, "Bildirim izni verildi." if permitted
                                         else f"'{user_id}' kullanıcısı bildirim izni vermedi (varsayılan: izin yok).")

    # 9: mükerrer alarm engeli
    existing = None
    if state is not None and rp.duplicate_block:
        existing = conn.execute("SELECT id, minute FROM radar_alerts WHERE dedupe_key=?",
                                (dedupe_key(fid, hg, ag),)).fetchone()
    if not rp.duplicate_block:
        checks["duplicate_block"] = (True, "Mükerrer alarm engeli devre dışı.")
    elif existing is not None:
        checks["duplicate_block"] = (False, f"Bu maç, hedef ve skor ({hg}-{ag}) için alarm #{existing[0]} "
                                            f"{existing[1]}. dakikada verildi; yeni alarm oluşturulmadı.")
    else:
        checks["duplicate_block"] = (True, f"Bu maç, hedef ve skor ({hg}-{ag}) için önceki alarm yok."
                                     if state is not None else "Canlı durum yok.")

    conditions = [{"code": c, "label": labels[c], "passed": bool(checks[c][0]), "detail": checks[c][1]}
                  for c in CONDITION_CODES]
    failed = [c for c in conditions if not c["passed"]]
    others_ok = all(c["passed"] for c in conditions if c["code"] != "duplicate_block")
    fire = not failed
    alarm = fire or (others_ok and existing is not None)
    blocked_reason = failed[0]["label"] if failed else None

    eval_id = insert(conn, "radar_evaluations", {
        "fixture_id": fid, "live_state_id": state["id"] if state is not None else None,
        "minute": minute if minute is not None else 0,
        "model_a_probability": p_a, "model_b_probability": p_b,
        "odds_snapshot_id": quote.snapshot_id if quote is not None else None,
        "conditions": dumps([{"code": c["code"], "passed": c["passed"], "detail": c["detail"]} for c in conditions]),
        "alarm": int(alarm), "blocked_reason": blocked_reason, "evaluated_at": now,
    })
    alert_id = existing[0] if existing is not None else None
    if fire:
        key = dedupe_key(fid, hg, ag) if rp.duplicate_block else f"{dedupe_key(fid, hg, ag)}:{eval_id}"
        conn.execute(
            "INSERT INTO radar_alerts(fixture_id, evaluation_id, target, minute, model_a_probability,"
            " model_b_probability, odds_snapshot_id, created_at, notified, dedupe_key)"
            " VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT(dedupe_key) DO NOTHING",
            (fid, eval_id, RADAR_TARGET, minute, p_a, p_b, quote.snapshot_id if quote else None, now, 1, key),
        )
        alert_id = conn.execute("SELECT id FROM radar_alerts WHERE dedupe_key=?", (key,)).fetchone()[0]

    odds_obj = None
    if quote is not None:
        odds_obj = {"decimal": quote.decimal_odds, "source_label": quote.source_label,
                    "age_seconds": int((from_iso(now) - from_iso(quote.captured_at)).total_seconds())}
    return {
        "fixture": {"id": fid, "home": f["home_name"], "away": f["away_name"],
                    "league": {"id": f["league_id"], "name": f["league_name"]}},
        "minute": minute,
        "score": f"{hg}-{ag}" if state is not None else None,
        "remaining_minutes": remaining,
        "model_a_probability": None if p_a is None else round(p_a, 4),
        "model_b_probability": None if p_b is None else round(p_b, 4),
        "odds": odds_obj,
        "conditions": conditions,
        "alarm": bool(alarm),
        "alert_id": alert_id,
        "blocked_reason": blocked_reason,
    }


def _b_missing_reason(hist: LiveHistory, p_a: float | None, quote: OddsQuote | None, market_id: str | None) -> str:
    if hist.model_b is None:
        return hist.model_b_reason or "MODEL B eğitilmedi"
    if p_a is None:
        return "MODEL A olasılığı yok"
    if market_id is None:
        return "gerekli canlı market yok"
    if quote is None:
        return "canlı oran yok"
    return "oran güncel skorla uyumsuz"


__all__ = [
    "RADAR_TARGET", "RADAR_TARGET_CODE", "CONDITION_CODES", "LiveFootballState", "RareEventCalibration",
    "radar_config", "condition_labels", "evaluate_live", "list_alerts", "set_notification_permission",
    "notification_permission_granted", "model_a_live_probability", "model_b_live_probability",
    "live_market_probability", "train_live_model_b", "rare_event_summary", "rare_event_calibration",
    "live_history", "calibration_cutoff", "remaining_share", "dedupe_key", "clear_cache",
]
