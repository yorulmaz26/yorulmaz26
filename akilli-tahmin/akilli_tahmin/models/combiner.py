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


def _cluster_mean_z(values: np.ndarray, groups: np.ndarray) -> tuple[float, int]:
    """Kümeli (maç düzeyinde) ortalama ve z istatistiği: her maç tek bir gözlem birimidir."""
    uniq, inv = np.unique(groups, return_inverse=True)
    sums = np.bincount(inv, weights=values)
    counts = np.bincount(inv)
    means = sums / counts
    g = means.size
    if g < 2:
        return 0.0, g
    se = means.std(ddof=1) / np.sqrt(g)
    return (float(means.mean() / se) if se > 0 else 0.0), g


def _cluster_calibration_z(p: np.ndarray, y: np.ndarray, groups: np.ndarray) -> float:
    """Kümeli kalibrasyon-büyüklük testi: Σ(y − p) / Σn, maç düzeyinde sağlam standart hatayla."""
    uniq, inv = np.unique(groups, return_inverse=True)
    r = np.bincount(inv, weights=y - p)
    n = np.bincount(inv).astype(float)
    g = r.size
    if g < 2:
        return 0.0
    mean = r.sum() / n.sum()
    var = g / (g - 1) * np.sum((r - mean * n) ** 2) / n.sum() ** 2
    return float(mean / np.sqrt(var)) if var > 0 else 0.0


def fit_combiner(pa_cal: np.ndarray, pb_cal: np.ndarray, y: np.ndarray, *, min_samples: int,
                 alpha: float, groups: np.ndarray | None = None) -> Combiner:
    """Girdiler zaman sırasında (eskiden yeniye) olmalıdır; `groups` her satırın maç kimliğidir.

    Bir maçın ~92 seçeneği aynı sonuca bağlı olduğundan çıkarım satır değil *maç* düzeyinde yapılır:
    log loss farkları maç başına ortalanır ve z testi maçlar üzerinden kurulur; kalibrasyon da
    maç düzeyinde sağlam (kümeli) standart hatayla sınanır.
    """
    ok = np.isfinite(pa_cal) & np.isfinite(pb_cal) & np.isfinite(y)
    if groups is None:
        groups = np.arange(y.size)
    pa, pb, yy, gg = pa_cal[ok], pb_cal[ok], y[ok], np.asarray(groups)[ok]
    n = int(yy.size)
    n_fixtures = int(np.unique(gg).size)
    if n < 2 * min_samples or n_fixtures < 2 * 30:
        return Combiner("conservative_min", None, {"n": n, "fixtures": n_fixtures,
                                                   "reason": "Birleştirici için doğrulama verisi yetersiz"})
    # kronolojik bölme maç sınırında yapılır (aynı maçın seçenekleri iki tarafa düşmez)
    split = int(n * 0.7)
    while 0 < split < n and gg[split] == gg[split - 1]:
        split += 1
    X = np.column_stack([np.ones(n), logit(pa), logit(pb)])
    f = fit_logistic(X[:split], yy[:split], ridge=np.array([1e-3, 1.0, 1.0]), prior_mean=np.array([0, 0.5, 0.5]))
    ho = slice(split, n)
    ps = np.clip(f.predict(X[ho]), 1e-9, 1 - 1e-9)

    def ll(p):
        p = np.clip(p, 1e-9, 1 - 1e-9)
        return -(yy[ho] * np.log(p) + (1 - yy[ho]) * np.log(1 - p))

    l_s, l_a, l_b = ll(ps), ll(pa[ho]), ll(pb[ho])
    z_a, g_ho = _cluster_mean_z(l_s - l_a, gg[ho])
    z_b, _ = _cluster_mean_z(l_s - l_b, gg[ho])
    z_cal = _cluster_calibration_z(ps, yy[ho], gg[ho])
    validation = {
        "n": n, "n_holdout": n - split, "holdout_fixtures": g_ho, "ll_stacked": float(l_s.mean()),
        "ll_a": float(l_a.mean()), "ll_b": float(l_b.mean()), "z_vs_a": z_a, "z_vs_b": z_b,
        "calibration_z": z_cal, "inference": "maç düzeyinde kümeli",
    }
    z_crit = -1.6448536269514722
    if z_a < z_crit and z_b < z_crit and abs(z_cal) < 1.959963984540054:
        final = fit_logistic(X, yy, ridge=np.array([1e-3, 1.0, 1.0]), prior_mean=np.array([0, 0.5, 0.5]))
        validation["enabled"] = True
        return Combiner("stacked", final, validation)
    validation["enabled"] = False
    validation["reason"] = "Yığınlama kronolojik doğrulamada (maç düzeyinde) iki modeli de anlamlı biçimde geçemedi"
    return Combiner("conservative_min", None, validation)
