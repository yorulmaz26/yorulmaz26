"""Skor ızgarası ve market olasılıklarının ızgaradan hesaplanması.

Izgara iki yarının ev/deplasman gollerinin ortak dağılımıdır: (h1, a1, h2, a2), her biri 0..G-1.
Maç sonu skoru h1+h2, a1+a2'dir. Bütün marketler aynı ortak dağılımdan türetildiği için
olasılıklar birbiriyle tutarlıdır (ör. Çifte Şans = ilgili Maç Sonucu olasılıklarının toplamı).
"""
from __future__ import annotations

import math
from functools import lru_cache

import numpy as np

from ..domain.markets import MARKETS, Grid

G = 8  # yarı başına takım gol sınırı (0..7); kesilen kütle yeniden normalize edilir


@lru_cache(maxsize=1)
def grid() -> Grid:
    r = np.arange(G)
    h1, a1, h2, a2 = np.meshgrid(r, r, r, r, indexing="ij")
    hh, ha = h1.ravel(), a1.ravel()
    fh, fa = (h1 + h2).ravel(), (a1 + a2).ravel()
    return hh, ha, fh, fa


@lru_cache(maxsize=1)
def outcome_index() -> tuple[tuple[tuple[str, str], ...], np.ndarray, np.ndarray]:
    """(market, seçenek) anahtarları, kazanma ve iade maskeleri (K × G⁴)."""
    g = grid()
    keys, wins, voids = [], [], []
    for mk in MARKETS:
        for o in mk.outcomes:
            keys.append((mk.id, o.id))
            wins.append(o.win(g).astype(np.float64))
            voids.append(o.void(g).astype(np.float64) if o.void is not None else np.zeros(g[0].shape))
    return tuple(keys), np.vstack(wins), np.vstack(voids)


def poisson_pmf(lam: np.ndarray, n: int = G) -> np.ndarray:
    """lam: (...,) → (..., n) Poisson olasılıkları."""
    lam = np.asarray(lam, float)[..., None]
    k = np.arange(n)
    logp = k * np.log(np.maximum(lam, 1e-12)) - lam - np.array([math.lgamma(i + 1) for i in range(n)])
    return np.exp(logp)


def dc_tau(fh: np.ndarray, fa: np.ndarray, lam_h: np.ndarray, lam_a: np.ndarray, rho: float) -> np.ndarray:
    """Dixon–Coles düşük skor düzeltmesi τ (S × G⁴)."""
    lam_h = np.asarray(lam_h, float)[:, None]
    lam_a = np.asarray(lam_a, float)[:, None]
    tau = np.ones((lam_h.shape[0], fh.shape[0]))
    m00 = (fh == 0) & (fa == 0)
    m01 = (fh == 0) & (fa == 1)
    m10 = (fh == 1) & (fa == 0)
    m11 = (fh == 1) & (fa == 1)
    tau[:, m00] = 1 - lam_h * lam_a * rho
    tau[:, m01] = 1 + lam_h * rho
    tau[:, m10] = 1 + lam_a * rho
    tau[:, m11] = 1 - rho
    return np.maximum(tau, 1e-9)


def joint_weights(lam_h: np.ndarray, lam_a: np.ndarray, ht_share: float, rho: float) -> np.ndarray:
    """S beklenen gol çifti için ortak skor dağılımı (S × G⁴), satırlar 1'e normalize."""
    lam_h = np.atleast_1d(np.asarray(lam_h, float))
    lam_a = np.atleast_1d(np.asarray(lam_a, float))
    p1h = poisson_pmf(lam_h * ht_share)
    p1a = poisson_pmf(lam_a * ht_share)
    p2h = poisson_pmf(lam_h * (1 - ht_share))
    p2a = poisson_pmf(lam_a * (1 - ht_share))
    w = (p1h[:, :, None, None, None] * p1a[:, None, :, None, None]
         * p2h[:, None, None, :, None] * p2a[:, None, None, None, :]).reshape(lam_h.shape[0], -1)
    if rho != 0.0:
        _, _, fh, fa = grid()
        w = w * dc_tau(fh, fa, lam_h, lam_a, rho)
    w /= w.sum(axis=1, keepdims=True)
    return w


def market_probabilities(weights: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """(S × G⁴) ağırlıklardan (S × K) kazanma ve iade olasılıkları."""
    _, win, void = outcome_index()
    return weights @ win.T, weights @ void.T
