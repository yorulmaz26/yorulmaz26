"""MODEL A/B karar birleştiricisi.

v6.3 kuralı: iki modelin olasılıkları otomatik ortalanmaz. Birleştirme yalnızca kronolojik olarak
eğitilmiş, kalibre edilmiş ve doğrulanmış bir yöntemle yapılabilir:

- `stacked`: geçmiş kalibre MODEL A/B olasılıkları üzerinde lojistik yığınlama. Yalnızca kronolojik
  doğrulama diliminde hem MODEL A'yı hem MODEL B'yi log loss'ta istatistiksel olarak anlamlı
  biçimde (tek yönlü eşleştirilmiş z testi) geçerse ve doğrulamada kalibrasyonu reddedilmezse
  etkinleşir.
- `conservative_min`: aksi halde her seçenek için iki kalibre olasılıktan *küçük olanı* kullanılır.
  Bu bir ortalama değil, iki modelin de desteklemesini şart koşan ihtiyatlı karar kuralıdır.
"""
from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

from ..calibration.calibrate import hosmer_lemeshow
from .logistic import LogisticFit, fit_logistic, logit, sigmoid


@dataclass
class Combiner:
    method: str
    fit: LogisticFit | None = None
    validation: dict = field(default_factory=dict)

    def combine(self, pa: np.ndarray, pb: np.ndarray, va: np.ndarray, vb: np.ndarray,
                rng: np.random.Generator | None = None) -> tuple[np.ndarray, np.ndarray]:
        """pa, pb, va, vb: aynı şekilli diziler (K,) veya (S × K). Dönüş: (p, iade)."""
        if self.method == "stacked" and self.fit is not None:
            X = np.stack([np.ones_like(pa), logit(pa), logit(pb)], axis=-1)
            if rng is not None and pa.ndim == 2:
                coefs = self.fit.draw_coefs(pa.shape[0], rng)
                p = sigmoid(np.einsum("skf,sf->sk", X, coefs))
            else:
                p = sigmoid(X @ self.fit.coef)
            v = np.minimum(va, vb)
            p = np.where(np.isfinite(pa) & np.isfinite(pb), p, np.nan)
            return p, v
        take_a = pa < pb
        p = np.where(take_a, pa, pb)
        v = np.where(take_a, va, vb)
        p = np.where(np.isfinite(pa) & np.isfinite(pb), p, np.nan)
        return p, v


def fit_combiner(pa_cal: np.ndarray, pb_cal: np.ndarray, y: np.ndarray, *, min_samples: int,
                 alpha: float) -> Combiner:
    """Girdiler zaman sırasında (eskiden yeniye) olmalıdır."""
    ok = np.isfinite(pa_cal) & np.isfinite(pb_cal) & np.isfinite(y)
    pa, pb, yy = pa_cal[ok], pb_cal[ok], y[ok]
    n = int(yy.size)
    if n < 2 * min_samples:
        return Combiner("conservative_min", None, {"n": n, "reason": "Birleştirici için doğrulama verisi yetersiz"})
    split = int(n * 0.7)
    X = np.column_stack([np.ones(n), logit(pa), logit(pb)])
    f = fit_logistic(X[:split], yy[:split], ridge=np.array([1e-3, 1.0, 1.0]), prior_mean=np.array([0, 0.5, 0.5]))
    ho = slice(split, n)
    ps = np.clip(f.predict(X[ho]), 1e-9, 1 - 1e-9)

    def ll(p):
        p = np.clip(p, 1e-9, 1 - 1e-9)
        return -(yy[ho] * np.log(p) + (1 - yy[ho]) * np.log(1 - p))

    l_s, l_a, l_b = ll(ps), ll(pa[ho]), ll(pb[ho])
    z_scores = {}
    for name, other in (("A", l_a), ("B", l_b)):
        d = l_s - other
        se = d.std(ddof=1) / np.sqrt(d.size) if d.size > 1 else np.inf
        z_scores[name] = float(d.mean() / se) if se > 0 else 0.0
    _, _, hl_p = hosmer_lemeshow(ps, yy[ho])
    validation = {
        "n": n, "n_holdout": n - split, "ll_stacked": float(l_s.mean()), "ll_a": float(l_a.mean()),
        "ll_b": float(l_b.mean()), "z_vs_a": z_scores["A"], "z_vs_b": z_scores["B"], "hl_p": hl_p,
    }
    z_crit = -1.6448536269514722
    if z_scores["A"] < z_crit and z_scores["B"] < z_crit and hl_p >= alpha:
        final = fit_logistic(X, yy, ridge=np.array([1e-3, 1.0, 1.0]), prior_mean=np.array([0, 0.5, 0.5]))
        validation["enabled"] = True
        return Combiner("stacked", final, validation)
    validation["enabled"] = False
    validation["reason"] = "Yığınlama kronolojik doğrulamada iki modeli de anlamlı biçimde geçemedi"
    return Combiner("conservative_min", None, validation)
