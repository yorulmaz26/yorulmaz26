"""Üç seçim modu: En Güvenilir Tahminler, En Avantajlı Oranlar, Dengeli Seçimler (varsayılan).

Modlar üçüncü bir tahmin modeli değildir. Hepsi aynı moddan bağımsız aday değerlendirmesini
(MODEL A/B kalibre olasılıkları, belirsizlik yüzdelikleri, dayanıklılık, oran/EV) okur ve yalnızca
uygunluk, sıralama ve gerekçe üretir. Eşikler `thresholds.py` ile kronolojik doğrulamadan gelir.
"""
from __future__ import annotations

import math
from dataclasses import asdict, dataclass, field

from ..db.database import DEFAULT_MODE, MODE_CODES
from ..odds.odds import kelly_fraction
from .checks import Check, fmt_num, fmt_pct
from .history import QUANTILES, level_to_q_index

HIGHEST_PROBABILITY = "highest_probability"
BEST_VALUE = "best_value"
BALANCED = "balanced"

assert DEFAULT_MODE == BALANCED
assert set(MODE_CODES) == {HIGHEST_PROBABILITY, BEST_VALUE, BALANCED}

USES_PROBABILITY = {HIGHEST_PROBABILITY, BALANCED}
USES_ODDS = {BEST_VALUE, BALANCED}

EMPTY_MESSAGES = {
    BALANCED: "Güvenilir ve avantajlı seçim bulunamadı.",
    BEST_VALUE: "Güvenilir bir avantaj belirlenemedi; seçim üretilmedi.",
    HIGHEST_PROBABILITY: "Kalibrasyon ve belirsizlik kontrollerinden geçen güvenilir seçim bulunamadı.",
}


def normalize_mode(mode: str | None) -> str:
    if mode is None:
        return DEFAULT_MODE
    if mode not in MODE_CODES:
        raise ValueError(f"Geçersiz seçim modu: {mode}")
    return mode


@dataclass(frozen=True)
class ModeThresholds:
    level: float                                # tek yönlü güven düzeyi (alt yüzdelik = 1 − düzey)
    max_relative_width: float                   # (q90 − q10) / p
    max_disagreement: float                     # |logit p_A − logit p_B|
    max_market_deviation: float | None = None   # |logit p − logit p_piyasa,adil| (oranlı modlar)
    min_probability_lower: float | None = None  # En Güvenilir ve Dengeli
    min_ev_lower: float | None = None           # En Avantajlı ve Dengeli (≥ 0)

    def as_api(self) -> dict:
        d = asdict(self)
        return {k: v for k, v in d.items() if v is not None}


@dataclass
class ModeConfig:
    mode: str
    status: str                                # established | not_established
    thresholds: ModeThresholds | None
    data_cutoff_at: str
    validation_start: str | None = None
    validation_end: str | None = None
    metrics: dict = field(default_factory=dict)
    db_id: int | None = None

    @property
    def established(self) -> bool:
        return self.status == "established" and self.thresholds is not None


@dataclass
class CandidateMetrics:
    """Bir (maç, market, seçenek) için moddan bağımsız değerler."""

    core_pass: bool
    odds_valid: bool
    p_est: float | None
    p_void: float
    p_q: list[float | None]          # QUANTILES sırası
    width: float | None
    rel_width: float | None
    dis: float | None
    market_dev: float | None
    ev_a: float | None
    ev_b: float | None
    odds: float | None
    robust_down: float | None
    core_failed: list[str]


@dataclass
class ModeEvaluation:
    mode: str
    checks: list[Check]
    eligible: bool
    rank_score: float | None
    p_low: float | None
    ev_low: float | None
    p_low_robust: float | None
    ev_low_robust: float | None


def _finite(x) -> bool:
    return x is not None and isinstance(x, (int, float)) and math.isfinite(x)


def evaluate_mode(mode: str, m: CandidateMetrics, cfg: ModeConfig) -> ModeEvaluation:
    checks: list[Check] = []
    if not cfg.established:
        checks.append(Check("thresholds_established", False, None, None,
                            cfg.metrics.get("reason", "Eşikler henüz kronolojik doğrulamayla belirlenmedi.")))
        return ModeEvaluation(mode, checks, False, None, None, None, None, None)
    th = cfg.thresholds
    assert th is not None
    checks.append(Check("thresholds_established", True, cfg.db_id, None))
    qi = level_to_q_index(th.level)
    p_low = m.p_q[qi] if m.p_q and _finite(m.p_q[qi]) else None
    down = min(0.0, m.robust_down) if _finite(m.robust_down) else None
    p_low_rob = (p_low + down) if (p_low is not None and down is not None) else None
    ev_low = ev_low_rob = None
    if _finite(m.odds) and p_low is not None:
        ev_low = p_low * m.odds + m.p_void - 1.0
        if p_low_rob is not None:
            ev_low_rob = p_low_rob * m.odds + m.p_void - 1.0
    lvl = f"%{int(th.level * 100)} güven"

    if mode in USES_ODDS:
        checks.append(Check("odds_valid", m.odds_valid, m.odds, None,
                            "Bu mod doğrulanmış ve güncel gerçek oran gerektirir."))
    if mode in USES_PROBABILITY:
        ok = p_low is not None and p_low >= th.min_probability_lower
        checks.append(Check("prob_lower", ok, p_low, th.min_probability_lower,
                            f"Olasılık alt sınırı ({lvl}) {fmt_num(p_low)} < {fmt_num(th.min_probability_lower)}"))
    if mode in USES_ODDS:
        ok = _finite(m.ev_a) and _finite(m.ev_b) and m.ev_a > 0 and m.ev_b > 0
        checks.append(Check("ev_models_positive", ok, [m.ev_a, m.ev_b], 0.0,
                            f"EV MODEL A {fmt_pct(m.ev_a)}, MODEL B {fmt_pct(m.ev_b)}; ikisi de pozitif olmalı"))
        ok = ev_low is not None and ev_low > th.min_ev_lower
        checks.append(Check("ev_lower", ok, ev_low, th.min_ev_lower,
                            f"EV alt sınırı ({lvl}) {fmt_pct(ev_low)} ≤ {fmt_pct(th.min_ev_lower)}"))
    ok = _finite(m.rel_width) and m.rel_width <= th.max_relative_width
    checks.append(Check("uncertainty", ok, m.rel_width, th.max_relative_width,
                        f"Göreli belirsizlik (%80 aralık / p) {fmt_num(m.rel_width)} > {fmt_num(th.max_relative_width)}"))
    if mode in USES_ODDS:
        ok = _finite(m.market_dev) and th.max_market_deviation is not None and m.market_dev <= th.max_market_deviation
        checks.append(Check("market_reference", ok, m.market_dev, th.max_market_deviation,
                            "Marjdan arındırılmış piyasa olasılığı yok veya modelden sapma "
                            f"{fmt_num(m.market_dev)} > {fmt_num(th.max_market_deviation)}"))
    ok = _finite(m.dis) and m.dis <= th.max_disagreement
    checks.append(Check("disagreement", ok, m.dis, th.max_disagreement,
                        f"MODEL A/B uyuşmazlığı (logit farkı) {fmt_num(m.dis)} > {fmt_num(th.max_disagreement)}"))
    rob_ok = p_low_rob is not None
    if rob_ok and mode in USES_PROBABILITY:
        rob_ok = rob_ok and p_low_rob >= th.min_probability_lower
    if rob_ok and mode in USES_ODDS:
        rob_ok = rob_ok and ev_low_rob is not None and ev_low_rob > th.min_ev_lower
    checks.append(Check("robustness", rob_ok, down, None,
                        "Alternatif model kurulumlarından en az birinde eşik sağlanmıyor"
                        + (f" (en kötü kayma {fmt_num(down)})" if down is not None else "")))

    eligible = m.core_pass and all(c.passed for c in checks)
    score = None
    if eligible:
        if mode == HIGHEST_PROBABILITY:
            score = p_low_rob
        elif mode == BEST_VALUE:
            score = ev_low_rob
        else:
            score = kelly_fraction(p_low_rob, m.odds, m.p_void)
    return ModeEvaluation(mode, checks, eligible, score, p_low, ev_low, p_low_rob, ev_low_rob)


__all__ = [
    "HIGHEST_PROBABILITY", "BEST_VALUE", "BALANCED", "EMPTY_MESSAGES", "ModeThresholds", "ModeConfig",
    "CandidateMetrics", "ModeEvaluation", "evaluate_mode", "normalize_mode", "QUANTILES",
]
