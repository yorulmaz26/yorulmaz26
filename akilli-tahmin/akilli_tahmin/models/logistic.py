"""Sırt cezalı lojistik regresyon (Newton) ve Laplace kovaryansı."""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np


def logit(p: np.ndarray | float, eps: float = 1e-6) -> np.ndarray:
    p = np.clip(np.asarray(p, float), eps, 1 - eps)
    return np.log(p / (1 - p))


def sigmoid(z: np.ndarray | float) -> np.ndarray:
    z = np.asarray(z, float)
    return 1.0 / (1.0 + np.exp(-np.clip(z, -35, 35)))


@dataclass
class LogisticFit:
    coef: np.ndarray
    cov: np.ndarray
    n: int
    converged: bool

    def predict(self, X: np.ndarray) -> np.ndarray:
        return sigmoid(np.asarray(X, float) @ self.coef)

    def draw_coefs(self, n_draws: int, rng: np.random.Generator) -> np.ndarray:
        try:
            L = np.linalg.cholesky(self.cov + 1e-12 * np.eye(self.cov.shape[0]))
        except np.linalg.LinAlgError:
            vals, vecs = np.linalg.eigh(self.cov)
            L = vecs @ np.diag(np.sqrt(np.clip(vals, 0, None)))
        return self.coef[None, :] + rng.standard_normal((n_draws, self.coef.size)) @ L.T


def fit_logistic(X: np.ndarray, y: np.ndarray, *, ridge: float | np.ndarray = 1e-3,
                 prior_mean: np.ndarray | None = None, weights: np.ndarray | None = None,
                 max_iter: int = 50) -> LogisticFit:
    X = np.asarray(X, float)
    y = np.asarray(y, float)
    n, k = X.shape
    w = np.ones(n) if weights is None else np.asarray(weights, float)
    lam = np.full(k, ridge, float) if np.isscalar(ridge) else np.asarray(ridge, float)
    m0 = np.zeros(k) if prior_mean is None else np.asarray(prior_mean, float)
    beta = m0.copy()
    converged = False

    def obj(b: np.ndarray) -> float:
        z = X @ b
        return float(np.sum(w * (y * z - np.logaddexp(0, z))) - 0.5 * np.sum(lam * (b - m0) ** 2))

    cur = obj(beta)
    for _ in range(max_iter):
        p = sigmoid(X @ beta)
        grad = X.T @ (w * (y - p)) - lam * (beta - m0)
        H = (X * (w * p * (1 - p))[:, None]).T @ X + np.diag(lam)
        step = np.linalg.solve(H, grad)
        t = 1.0
        while True:
            cand = beta + t * step
            new = obj(cand)
            if new >= cur - 1e-12 or t < 1e-4:
                break
            t *= 0.5
        beta, prev, cur = cand, cur, new
        if abs(cur - prev) < 1e-10 * (1 + abs(cur)):
            converged = True
            break
    p = sigmoid(X @ beta)
    H = (X * (w * p * (1 - p))[:, None]).T @ X + np.diag(lam)
    return LogisticFit(beta, np.linalg.inv(H), n, converged)
