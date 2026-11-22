"""Olasılık tahmini ve seçim başarısı metrikleri."""
from __future__ import annotations

import math
from typing import Sequence

import numpy as np

EPS = 1e-12


def brier(p: Sequence[float], y: Sequence[int]) -> float | None:
    p, y = np.asarray(p, float), np.asarray(y, float)
    if p.size == 0:
        return None
    return float(np.mean((p - y) ** 2))


def log_loss(p: Sequence[float], y: Sequence[int]) -> float | None:
    p, y = np.asarray(p, float), np.asarray(y, float)
    if p.size == 0:
        return None
    p = np.clip(p, 1e-9, 1 - 1e-9)
    return float(-np.mean(y * np.log(p) + (1 - y) * np.log(1 - p)))


def wilson_interval(k: int, n: int, z: float = 1.959963984540054) -> tuple[float | None, float | None]:
    if n <= 0:
        return None, None
    phat = k / n
    denom = 1 + z * z / n
    centre = (phat + z * z / (2 * n)) / denom
    half = z * math.sqrt(phat * (1 - phat) / n + z * z / (4 * n * n)) / denom
    return max(0.0, centre - half), min(1.0, centre + half)


def z_for_one_sided(level: float) -> float:
    """Tek yönlü güven düzeyi için standart normal z (ör. 0.90 → 1.2816)."""
    return float(normal_ppf(level))


def normal_ppf(q: float) -> float:
    """Acklam'ın ters normal yaklaşımı (scipy bağımlılığı olmadan)."""
    if not 0 < q < 1:
        raise ValueError("q (0,1) aralığında olmalı")
    a = [-3.969683028665376e01, 2.209460984245205e02, -2.759285104469687e02,
         1.383577518672690e02, -3.066479806614716e01, 2.506628277459239e00]
    b = [-5.447609879822406e01, 1.615858368580409e02, -1.556989798598866e02,
         6.680131188771972e01, -1.328068155288572e01]
    c = [-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e00,
         -2.549732539343734e00, 4.374664141464968e00, 2.938163982698783e00]
    d = [7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e00, 3.754408661907416e00]
    plow = 0.02425
    if q < plow:
        r = math.sqrt(-2 * math.log(q))
        return (((((c[0] * r + c[1]) * r + c[2]) * r + c[3]) * r + c[4]) * r + c[5]) / \
            ((((d[0] * r + d[1]) * r + d[2]) * r + d[3]) * r + 1)
    if q > 1 - plow:
        r = math.sqrt(-2 * math.log(1 - q))
        return -(((((c[0] * r + c[1]) * r + c[2]) * r + c[3]) * r + c[4]) * r + c[5]) / \
            ((((d[0] * r + d[1]) * r + d[2]) * r + d[3]) * r + 1)
    r = q - 0.5
    t = r * r
    return (((((a[0] * t + a[1]) * t + a[2]) * t + a[3]) * t + a[4]) * t + a[5]) * r / \
        (((((b[0] * t + b[1]) * t + b[2]) * t + b[3]) * t + b[4]) * t + 1)


def normal_cdf(x: float) -> float:
    return 0.5 * (1 + math.erf(x / math.sqrt(2)))


def reliability_bins(p: Sequence[float], y: Sequence[int], n_bins: int = 10) -> list[dict]:
    """Eşit frekanslı bölmelerle güvenilirlik diyagramı verisi (her bölmede Wilson aralığı)."""
    p, y = np.asarray(p, float), np.asarray(y, int)
    if p.size == 0:
        return []
    order = np.argsort(p, kind="mergesort")
    chunks = np.array_split(order, min(n_bins, p.size))
    out = []
    for idx in chunks:
        if idx.size == 0:
            continue
        k, n = int(y[idx].sum()), int(idx.size)
        lo, hi = wilson_interval(k, n)
        out.append({
            "p_mean": float(p[idx].mean()),
            "observed": k / n,
            "n": n,
            "ci_low": lo,
            "ci_high": hi,
        })
    return out


def expected_calibration_error(p: Sequence[float], y: Sequence[int], n_bins: int = 10) -> float | None:
    bins = reliability_bins(p, y, n_bins)
    if not bins:
        return None
    total = sum(b["n"] for b in bins)
    return float(sum(b["n"] * abs(b["p_mean"] - b["observed"]) for b in bins) / total)


def bootstrap_mean_ci(values: Sequence[float], reps: int = 400, level: float = 0.95,
                      seed: int = 12345) -> tuple[float | None, float | None]:
    v = np.asarray(values, float)
    if v.size < 2:
        return None, None
    rng = np.random.default_rng(seed)
    idx = rng.integers(0, v.size, size=(reps, v.size))
    means = v[idx].mean(axis=1)
    a = (1 - level) / 2
    return float(np.quantile(means, a)), float(np.quantile(means, 1 - a))


def mean_ci_normal(values: Sequence[float], z: float) -> tuple[float | None, float | None, float | None]:
    v = np.asarray(values, float)
    if v.size < 2:
        return (float(v.mean()) if v.size else None), None, None
    m = float(v.mean())
    se = float(v.std(ddof=1) / math.sqrt(v.size))
    return m, m - z * se, m + z * se
