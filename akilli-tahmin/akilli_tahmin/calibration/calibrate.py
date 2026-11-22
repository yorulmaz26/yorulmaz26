"""Kronolojik olasılık kalibrasyonu ve kalibrasyon yeterliliği.

Her (model, market ailesi) için yalnızca zaman kesiminden önce sonucu bilinen *out-of-fold*
tahminlerle bir kalibrasyon haritası kurulur:

1. Örnekler zamana göre sıralanır; ilk %70 ile aday haritalar (kimlik, Platt, beta) uydurulur.
2. Son %30 (kronolojik doğrulama) üzerinde log loss karşılaştırılır; en iyisi seçilir.
3. Doğrulama dilimindeki kalibrasyon Hosmer–Lemeshow testiyle sınanır.
4. Seçilen yöntem bütün örneklerle yeniden uydurulur.

Yeterlilik: örneklem ≥ en az örnek, doğrulamada kalibrasyon reddedilmemiş (p ≥ α) ve aday
olasılığın düştüğü yerel bölgede yeterli örnek ile gözlenen oranla uyum.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

import numpy as np

from ..models.logistic import LogisticFit, fit_logistic, logit, sigmoid
from .metrics import expected_calibration_error, log_loss, reliability_bins, wilson_interval


# ───────────── χ² kuyruk olasılığı (düzenlenmiş eksik gama) ─────────────

def _gammainc_lower_series(a: float, x: float) -> float:
    term = 1.0 / a
    total = term
    ap = a
    for _ in range(500):
        ap += 1
        term *= x / ap
        total += term
        if abs(term) < abs(total) * 1e-14:
            break
    return total * math.exp(-x + a * math.log(x) - math.lgamma(a))


def _gammainc_upper_cf(a: float, x: float) -> float:
    tiny = 1e-300
    b = x + 1 - a
    c = 1 / tiny
    d = 1 / b
    h = d
    for i in range(1, 500):
        an = -i * (i - a)
        b += 2
        d = an * d + b
        d = tiny if abs(d) < tiny else d
        c = b + an / c
        c = tiny if abs(c) < tiny else c
        d = 1 / d
        delta = d * c
        h *= delta
        if abs(delta - 1) < 1e-14:
            break
    return math.exp(-x + a * math.log(x) - math.lgamma(a)) * h


def chi2_sf(x: float, df: int) -> float:
    if x <= 0:
        return 1.0
    a, xx = df / 2.0, x / 2.0
    if xx < a + 1:
        return 1.0 - _gammainc_lower_series(a, xx)
    return _gammainc_upper_cf(a, xx)


def hosmer_lemeshow(p: np.ndarray, y: np.ndarray, groups: int = 10) -> tuple[float, int, float]:
    """Dış örneklem HL testi: istatistik, serbestlik derecesi (= grup sayısı), p-değeri."""
    p, y = np.asarray(p, float), np.asarray(y, float)
    order = np.argsort(p, kind="mergesort")
    chunks = [c for c in np.array_split(order, min(groups, max(1, p.size // 10))) if c.size]
    stat = 0.0
    for c in chunks:
        e = p[c].sum()
        o = y[c].sum()
        n = c.size
        var = e * (1 - e / n)
        if var > 1e-9:
            stat += (o - e) ** 2 / var
    df = len(chunks)
    return stat, df, chi2_sf(stat, df)


# ───────────── kalibrasyon haritası ─────────────

def _features(method: str, p: np.ndarray) -> np.ndarray:
    p = np.clip(np.asarray(p, float), 1e-6, 1 - 1e-6)
    if method == "platt":
        return np.column_stack([np.ones_like(p), logit(p)])
    if method == "beta":
        return np.column_stack([np.ones_like(p), np.log(p), -np.log(1 - p)])
    raise ValueError(method)


_PRIOR = {"platt": np.array([0.0, 1.0]), "beta": np.array([0.0, 1.0, 1.0])}


@dataclass
class CalibrationMap:
    model: str
    family: str
    method: str
    n: int
    adequate: bool
    reasons: list[str]
    fit: LogisticFit | None
    tests: dict
    bins: list[dict] = field(default_factory=list)
    p_range: tuple[float, float] = (0.0, 1.0)
    db_id: int | None = None

    def apply(self, p: np.ndarray | float) -> np.ndarray:
        p = np.asarray(p, float)
        if self.method == "identity" or self.fit is None:
            return np.clip(p, 0.0, 1.0)
        return self.fit.predict(_features(self.method, p.ravel())).reshape(p.shape)

    def apply_with_param_draws(self, p_draws: np.ndarray, rng: np.random.Generator) -> np.ndarray:
        """p_draws (S × m) → kalibrasyon parametre belirsizliği de katılmış (S × m)."""
        if self.method == "identity" or self.fit is None:
            return np.clip(p_draws, 0.0, 1.0)
        S, m = p_draws.shape
        coefs = self.fit.draw_coefs(S, rng)  # (S × f)
        X = _features(self.method, p_draws.ravel()).reshape(S, m, -1)
        return sigmoid(np.einsum("smf,sf->sm", X, coefs))

    def local_support(self, p_cal: float, min_local: int) -> dict:
        """Adayın kalibre olasılığının düştüğü bölgede geçmiş örneklem ve gözlenen oran uyumu."""
        if not self.bins:
            return {"ok": False, "n": 0, "reason": "Kalibrasyon bölmesi yok"}
        lo_p, hi_p = self.p_range
        if p_cal > hi_p + 1e-9 or p_cal < lo_p - 1e-9:
            return {"ok": False, "n": 0, "reason": "Olasılık geçmiş kalibrasyon aralığının dışında (dış değerleme yapılmaz)",
                    "range": [lo_p, hi_p]}
        best = min(self.bins, key=lambda b: abs(b["p_mean"] - p_cal))
        consistent = best["ci_low"] is not None and best["ci_low"] - 1e-9 <= p_cal <= best["ci_high"] + 1e-9
        ok = best["n"] >= min_local and consistent
        reason = None
        if best["n"] < min_local:
            reason = f"Yerel örneklem yetersiz ({best['n']} < {min_local})"
        elif not consistent:
            reason = (f"Yerel gözlenen oran [{best['ci_low']:.3f}, {best['ci_high']:.3f}] "
                      f"kalibre olasılıkla ({p_cal:.3f}) uyumsuz").replace(".", ",")
        return {"ok": ok, "n": best["n"], "observed": best["observed"], "ci_low": best["ci_low"],
                "ci_high": best["ci_high"], "p_mean": best["p_mean"], "reason": reason}

    def summary(self) -> dict:
        return {"method": self.method, "n": self.n, "adequate": self.adequate, "reasons": self.reasons,
                "tests": self.tests, "p_range": list(self.p_range)}


def fit_calibration(model: str, family: str, p_raw: np.ndarray, y: np.ndarray, *,
                    min_samples: int, alpha: float, n_bins: int = 10) -> CalibrationMap:
    """`p_raw` ve `y` zaman sırasına göre (eskiden yeniye) verilmelidir."""
    p_raw = np.clip(np.asarray(p_raw, float), 1e-6, 1 - 1e-6)
    y = np.asarray(y, float)
    n = int(p_raw.size)
    reasons: list[str] = []
    if n < min_samples:
        reasons.append(f"Kalibrasyon örneklemi yetersiz ({n} < {min_samples})")
        return CalibrationMap(model, family, "identity", n, False, reasons, None,
                              {"n": n}, reliability_bins(p_raw, y, n_bins) if n else [],
                              (float(p_raw.min()), float(p_raw.max())) if n else (0.0, 1.0))
    split = int(n * 0.7)
    tr_p, tr_y, ho_p, ho_y = p_raw[:split], y[:split], p_raw[split:], y[split:]
    candidates: dict[str, np.ndarray] = {"identity": ho_p}
    fits: dict[str, LogisticFit] = {}
    for method in ("platt", "beta"):
        f = fit_logistic(_features(method, tr_p), tr_y, ridge=np.r_[1e-4, np.full(len(_PRIOR[method]) - 1, 2.0)],
                         prior_mean=_PRIOR[method])
        fits[method] = f
        candidates[method] = f.predict(_features(method, ho_p))
    ll = {m: log_loss(v, ho_y) for m, v in candidates.items()}
    method = min(ll, key=lambda m: ll[m])
    ho_cal = candidates[method]
    hl_stat, hl_df, hl_p = hosmer_lemeshow(ho_cal, ho_y)
    ece = expected_calibration_error(ho_cal, ho_y, n_bins)
    tests = {
        "n": n, "n_holdout": int(ho_p.size), "holdout_log_loss": ll, "chosen": method,
        "hl_stat": hl_stat, "hl_df": hl_df, "hl_p": hl_p, "holdout_ece": ece,
    }
    adequate = True
    if hl_p < alpha:
        adequate = False
        reasons.append(f"Kronolojik doğrulamada kalibrasyon reddedildi (HL p={hl_p:.3f} < {alpha})".replace(".", ","))
    final_fit = None
    if method != "identity":
        final_fit = fit_logistic(_features(method, p_raw), y,
                                 ridge=np.r_[1e-4, np.full(len(_PRIOR[method]) - 1, 2.0)],
                                 prior_mean=_PRIOR[method])
    cmap = CalibrationMap(model, family, method, n, adequate, reasons, final_fit, tests)
    p_cal_all = cmap.apply(p_raw)
    cmap.bins = reliability_bins(p_cal_all, y, n_bins)
    cmap.p_range = (float(p_cal_all.min()), float(p_cal_all.max()))
    return cmap


__all__ = ["CalibrationMap", "fit_calibration", "hosmer_lemeshow", "chi2_sf", "wilson_interval"]
