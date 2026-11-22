"""Seçim modu eşiklerinin kronolojik doğrulamayla belirlenmesi.

Sabit "%70 olasılık" veya "%10 EV" eşiği yoktur. Her mod için eşik adayları, zaman kesiminden
önceki doğrulama penceresindeki aday değerlerinin yüzdeliklerinden üretilir ve her aday eşik
kümesi, o pencerede *gerçekleşmiş* sonuçlarla puanlanır:

- En Güvenilir: isabet oranının Wilson alt sınırını en büyükler. Olasılık eşiği adayları havuzun
  üst kuyruğundaki yüzdeliklerden gelir.
- En Avantajlı: teorik birim ROI'nin alt güven sınırını en büyükler; EV eşiği ≥ 0.
- Dengeli: ihtiyatlı Kelly payıyla (olasılık alt sınırından) gerçekleşmiş ortalama log büyümenin
  alt güven sınırını en büyükler. Kelly büyümesi, aynı avantajda daha yüksek olasılığı ödüllendirdiği
  için olasılık ile değeri matematiksel olarak dengeler. Ek olarak isabet alt sınırı pozitif EV
  havuzunun isabet oranından düşük olamaz.

Bütün modlarda seçilen kümenin ortalama tahmini olasılığı, gözlenen isabet oranının üst güven
sınırını aşamaz (aşırı güven / "kazananın laneti" engeli).

"Güvenilir avantaj" kuralı: oranlı modlar (En Avantajlı, Dengeli) ancak doğrulama penceresinde
teorik birim ROI'nin tek yönlü %95 alt güven sınırı sıfırın üzerindeyse kurulur (Dengeli için ayrıca
Kelly log büyümesinin alt sınırı > 0). Aksi halde mod "belirlenmemiş" kalır ve seçim üretmez.
Verimli bir piyasada bu, çoğu zaman hiç seçim yapılmaması demektir — bu beklenen ve doğru davranıştır.
"""
from __future__ import annotations

import math

import numpy as np

from ..config import StatisticalPolicy
from .history import CONFIDENCE_LEVELS, HistoryView, level_to_q_index
from .modes import BALANCED, BEST_VALUE, HIGHEST_PROBABILITY, ModeConfig, ModeThresholds

Z95 = 1.959963984540054
Z_ONE_SIDED_95 = 1.6448536269514722


def _wilson(k: np.ndarray, n: np.ndarray, z: float = Z95) -> tuple[np.ndarray, np.ndarray]:
    n_safe = np.maximum(n, 1)
    ph = k / n_safe
    denom = 1 + z * z / n_safe
    centre = (ph + z * z / (2 * n_safe)) / denom
    half = z * np.sqrt(ph * (1 - ph) / n_safe + z * z / (4 * n_safe * n_safe)) / denom
    return np.where(n > 0, centre - half, np.nan), np.where(n > 0, centre + half, np.nan)


def _unique_grid(values: np.ndarray, qs: tuple[float, ...]) -> list[float]:
    if values.size == 0:
        return []
    return sorted({round(float(v), 6) for v in np.quantile(values, qs)})


def tune_mode(mode: str, view: HistoryView, policy: StatisticalPolicy, *, data_cutoff_at: str) -> ModeConfig:
    recs = view.records
    if not recs:
        return ModeConfig(mode, "not_established", None, data_cutoff_at,
                          metrics={"reason": "Kronolojik doğrulama verisi yok.", "n": 0})
    y = view.stacked("y")
    core = view.stacked("core_pass").astype(bool)
    odds_ok = view.stacked("odds_valid").astype(bool)
    odds = view.stacked("odds")
    p_est = view.stacked("p_est")
    p_void = view.stacked("p_void")
    width = view.stacked("width") / np.maximum(view.stacked("p_est"), 1e-9)
    dis = view.stacked("dis")
    mdev = view.stacked("market_dev")
    ev_a = view.stacked("ev_a")
    ev_b = view.stacked("ev_b")
    down = np.minimum(0.0, np.nan_to_num(view.stacked("robust_down"), nan=-np.inf))
    pq = view.quantiles()

    uses_odds = mode in (BEST_VALUE, BALANCED)
    valid = core & np.isfinite(y) & np.isfinite(p_est) & np.isfinite(width) & np.isfinite(dis)
    if uses_odds:
        valid &= odds_ok & np.isfinite(odds) & np.isfinite(mdev)
    n_valid = int(valid.sum())
    n_min = policy.min_validation_selections_odds if uses_odds else policy.min_validation_selections
    start, end = recs[0].kickoff_at, view.cutoff
    if n_valid < n_min:
        return ModeConfig(mode, "not_established", None, data_cutoff_at, start, end,
                          {"reason": f"Doğrulama örneklemi yetersiz ({n_valid} < {n_min}).",
                           "n": n_valid})

    idx = np.where(valid)[0]
    y_v = y[idx]
    won = (y_v == 1).astype(float)
    p_cond = p_est[idx] / np.maximum(1e-9, 1 - p_void[idx])
    w_grid = _unique_grid(width[idx], (0.5, 0.75, 1.0))
    d_grid = _unique_grid(dis[idx], (0.75, 1.0))
    m_grid = _unique_grid(mdev[idx], (0.5, 0.75, 1.0)) if uses_odds else [None]
    if uses_odds:
        o = odds[idx]
        ret = np.where(y_v == 1, o - 1.0, -1.0)
        pos_ev = (ev_a[idx] > 0) & (ev_b[idx] > 0)
    else:
        ret = np.zeros(idx.size)
        pos_ev = np.ones(idx.size, bool)

    best = None
    for level in CONFIDENCE_LEVELS:
        qi = level_to_q_index(level)
        p_low = pq[qi, idx]
        p_rob = p_low + down[idx]
        ok_p = np.isfinite(p_rob)
        ev_rob = p_rob * odds[idx] + p_void[idx] - 1.0 if uses_odds else np.full(idx.size, np.nan)
        if mode == HIGHEST_PROBABILITY:
            p_grid = _unique_grid(p_rob[ok_p], (0.80, 0.85, 0.90, 0.925, 0.95, 0.965, 0.975, 0.985, 0.99))
        elif mode == BALANCED:
            p_grid = _unique_grid(p_rob[ok_p], (0.6, 0.7, 0.8, 0.9, 0.95, 0.975))
        else:
            p_grid = [None]
        if uses_odds:
            pos_mask = ok_p & pos_ev & (ev_rob > 0)
            e_grid = [0.0] + [v for v in _unique_grid(ev_rob[pos_mask], (0.25, 0.5, 0.75)) if v > 0]
            baseline_hit = float(won[pos_mask].mean()) if pos_mask.any() else None
        else:
            e_grid = [None]
            baseline_hit = None
        combos = [(tp, te) for tp in p_grid for te in e_grid]
        for tw, td, tm in ((a, b, c) for a in w_grid for b in d_grid for c in m_grid):
            base = ok_p & (width[idx] <= tw + 1e-12) & (dis[idx] <= td + 1e-12)
            if uses_odds:
                base &= pos_ev & (mdev[idx] <= tm + 1e-12)
            if not base.any():
                continue
            M = np.zeros((len(combos), idx.size), dtype=np.float32)
            for ci, (tp, te) in enumerate(combos):
                mk = base.copy()
                if tp is not None:
                    mk &= p_rob >= tp
                if te is not None:
                    mk &= ev_rob > te
                M[ci] = mk
            n = M.sum(axis=1)
            k = M @ won
            if mode == BALANCED:
                f = np.clip((p_rob * (o - 1.0) - (1.0 - p_rob - p_void[idx])) / np.maximum(o - 1.0, 1e-9), 0.0, 0.99)
                g = np.where(y_v == 1, np.log1p(f * (o - 1.0)), np.log1p(-f))
                gs = M @ np.nan_to_num(g)
                gs2 = M @ np.nan_to_num(g * g)
            rs = M @ ret
            rs2 = M @ (ret * ret)
            ps = M @ p_cond
            lo, hi = _wilson(k, n)
            with np.errstate(invalid="ignore", divide="ignore"):
                roi = np.where(n > 0, rs / np.maximum(n, 1), np.nan)
                var = np.maximum(0.0, rs2 / np.maximum(n, 1) - roi * roi)
                roi_lb = roi - Z_ONE_SIDED_95 * np.sqrt(var / np.maximum(n, 1))
                mean_p = np.where(n > 0, ps / np.maximum(n, 1), np.nan)
            if mode == BALANCED:
                with np.errstate(invalid="ignore", divide="ignore"):
                    gm = gs / np.maximum(n, 1)
                    gvar = np.maximum(0.0, gs2 / np.maximum(n, 1) - gm * gm)
                    g_lb = gm - Z_ONE_SIDED_95 * np.sqrt(gvar / np.maximum(n, 1))
            for ci, (tp, te) in enumerate(combos):
                if n[ci] < n_min:
                    continue
                if bool(hi[ci] < mean_p[ci]):
                    continue  # aşırı güven: tahmini olasılık gözlenen oranın üst sınırını aşıyor
                if mode == HIGHEST_PROBABILITY:
                    score = (float(lo[ci]), float(n[ci]))
                elif mode == BEST_VALUE:
                    score = (float(roi_lb[ci]), float(n[ci]))
                else:
                    if baseline_hit is None or lo[ci] < baseline_hit:
                        continue
                    score = (float(g_lb[ci]), float(n[ci]))
                if best is None or score > best[0]:
                    best = (score, ModeThresholds(
                        level=level, max_relative_width=float(tw), max_disagreement=float(td),
                        max_market_deviation=None if tm is None else float(tm),
                        min_probability_lower=None if tp is None else float(tp),
                        min_ev_lower=None if te is None else float(te),
                    ), {
                        "n": int(n[ci]), "hit_rate": float(k[ci] / n[ci]), "hit_rate_lower": float(lo[ci]),
                        "hit_rate_upper": float(hi[ci]), "mean_probability": float(mean_p[ci]),
                        "roi": float(roi[ci]) if uses_odds else None,
                        "roi_lower": float(roi_lb[ci]) if uses_odds else None,
                        "baseline_hit_rate": baseline_hit, "n_validation_pool": n_valid,
                        "log_growth": float(gm[ci]) if mode == BALANCED else None,
                        "log_growth_lower": float(g_lb[ci]) if mode == BALANCED else None,
                        "grid_size": len(combos) * len(w_grid) * len(d_grid) * len(m_grid) * len(CONFIDENCE_LEVELS),
                    })
    if best is None:
        return ModeConfig(mode, "not_established", None, data_cutoff_at, start, end,
                          {"reason": "Doğrulama penceresinde koşulları sağlayan eşik kümesi bulunamadı.", "n": n_valid})
    _, th, metrics = best
    growth_ok = mode != BALANCED or (metrics.get("log_growth_lower") is not None and metrics["log_growth_lower"] > 0)
    if mode in (BEST_VALUE, BALANCED) and not (metrics["roi_lower"] is not None and metrics["roi_lower"] > 0
                                               and growth_ok):
        metrics["reason"] = ("Doğrulama penceresinde teorik getirinin %95 alt güven sınırı pozitif değil; "
                             "güvenilir avantaj belirlenemedi.")
        return ModeConfig(mode, "not_established", None, data_cutoff_at, start, end, metrics)
    if not all(math.isfinite(v) for v in (th.max_relative_width, th.max_disagreement)):
        return ModeConfig(mode, "not_established", None, data_cutoff_at, start, end,
                          {"reason": "Geçersiz eşik.", "n": n_valid})
    return ModeConfig(mode, "established", th, data_cutoff_at, start, end, metrics)
