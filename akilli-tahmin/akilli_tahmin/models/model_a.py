"""MODEL A — yalnızca futbol verisiyle çalışan skor modeli.

Zaman ağırlıklı Dixon–Coles Poisson modeli:
    log λ_ev  = μ + ev_avantajı + hücum[ev] − savunma[dep]
    log λ_dep = μ + hücum[dep] − savunma[ev]
Parametreler ağırlıklı en çok olabilirlikle (Newton, sırt cezası ile büzülme) uydurulur; ağırlık
w = 0.5^(yaş_gün / yarı_ömür). Düşük skor düzeltmesi ρ ayrı olabilirlik taramasıyla bulunur.
İlk yarı payı geçmiş ilk yarı skorlarından tahmin edilir; böylece İY ve İY/MS marketleri de aynı
ortak dağılımdan türetilir.

Belirsizlik: parametrelerin Laplace yaklaşımı N(β̂, H⁻¹) ile örneklenip her örnek için bütün
market olasılıkları yeniden hesaplanır.

Oran izolasyonu: girdi kayıtları `assert_football_only` ile denetlenir; bu modülün hiçbir yolu
oran tablosuna erişmez.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

import numpy as np

from ..db.database import from_iso
from .isolation import assert_football_only
from .score_grid import joint_weights, market_probabilities, outcome_index

MODEL_A_VERSION = "A-dc-poisson-1.0"
MODEL_A_INPUTS = "results,halftime_results"


@dataclass(frozen=True)
class MatchRecord:
    """MODEL A'nın görebildiği tek kayıt türü: oynanmış maçın futbol verisi."""

    fixture_id: int
    league_id: str
    home_team_id: int
    away_team_id: int
    kickoff_at: str
    ft_home: int
    ft_away: int
    ht_home: int | None
    ht_away: int | None
    result_recorded_at: str


@dataclass(frozen=True)
class ModelAConfig:
    name: str = "base"
    label: str = "Temel"
    half_life_days: float = 180.0
    ridge: float = 10.0
    use_dc: bool = True
    window_days: int = 730
    drop_latest_matchday: bool = False


BASE_CONFIG = ModelAConfig()

#: Tahmin dayanıklılığı senaryoları (MODEL A'nın alternatif, makul kurulumları).
ROBUSTNESS_SCENARIOS: tuple[ModelAConfig, ...] = (
    ModelAConfig(name="decay_fast", label="Zaman ağırlığı hızlı (90 gün)", half_life_days=90.0),
    ModelAConfig(name="decay_slow", label="Zaman ağırlığı yavaş (360 gün)", half_life_days=360.0),
    ModelAConfig(name="no_dc", label="Düşük skor düzeltmesi yok", use_dc=False),
    ModelAConfig(name="shrink_strong", label="Güçlü büzülme", ridge=25.0),
    ModelAConfig(name="drop_latest", label="Son maç günü çıkarıldı", drop_latest_matchday=True),
)


@dataclass
class ModelAFit:
    league_id: str
    data_cutoff_at: str
    config: ModelAConfig
    team_index: dict[int, int]
    beta: np.ndarray
    cov: np.ndarray
    rho: float
    ht_share: float
    n_matches: int
    team_matches: dict[int, int]
    last_result_at: str | None
    converged: bool
    valid: bool
    invalid_reason: str | None = None
    extras: dict = field(default_factory=dict)

    # ── yardımcılar ──
    def _lambda(self, beta: np.ndarray, home: int, away: int) -> tuple[np.ndarray, np.ndarray]:
        T = len(self.team_index)
        beta = np.atleast_2d(beta)
        mu, h = beta[:, 0], beta[:, 1]
        hi, ai = self.team_index.get(home), self.team_index.get(away)
        att_h = beta[:, 2 + hi] if hi is not None else 0.0
        att_a = beta[:, 2 + ai] if ai is not None else 0.0
        def_h = beta[:, 2 + T + hi] if hi is not None else 0.0
        def_a = beta[:, 2 + T + ai] if ai is not None else 0.0
        return np.exp(mu + h + att_h - def_a), np.exp(mu + att_a - def_h)

    def expected_goals(self, home: int, away: int) -> tuple[float, float]:
        lh, la = self._lambda(self.beta, home, away)
        return float(lh[0]), float(la[0])

    def predict(self, home: int, away: int) -> tuple[np.ndarray, np.ndarray]:
        """Nokta tahmini: (K,) kazanma ve iade olasılıkları."""
        lh, la = self._lambda(self.beta, home, away)
        w = joint_weights(lh, la, self.ht_share, self.rho if self.config.use_dc else 0.0)
        pw, pv = market_probabilities(w)
        return pw[0], pv[0]

    def predict_draws(self, home: int, away: int, n_draws: int, seed: int) -> tuple[np.ndarray, np.ndarray]:
        """Laplace örnekleri: (S × K) kazanma ve iade olasılıkları."""
        rng = np.random.default_rng(seed)
        try:
            L = np.linalg.cholesky(self.cov + 1e-10 * np.eye(self.cov.shape[0]))
        except np.linalg.LinAlgError:
            vals, vecs = np.linalg.eigh(self.cov)
            L = vecs @ np.diag(np.sqrt(np.clip(vals, 0, None)))
        z = rng.standard_normal((n_draws, self.beta.shape[0]))
        betas = self.beta[None, :] + z @ L.T
        lh, la = self._lambda(betas, home, away)
        w = joint_weights(lh, la, self.ht_share, self.rho if self.config.use_dc else 0.0)
        return market_probabilities(w)

    def team_sample(self, team_id: int) -> int:
        return self.team_matches.get(team_id, 0)

    def params_summary(self) -> dict:
        return {
            "version": MODEL_A_VERSION,
            "config": self.config.name,
            "half_life_days": self.config.half_life_days,
            "ridge": self.config.ridge,
            "rho": self.rho,
            "ht_share": self.ht_share,
            "home_advantage": float(self.beta[1]) if self.beta.size > 1 else None,
            "n_matches": self.n_matches,
            "inputs": MODEL_A_INPUTS,
            "uses_odds": False,
        }


def outcome_keys() -> tuple[tuple[str, str], ...]:
    return outcome_index()[0]


def fit_model_a(records: list[MatchRecord], cutoff: str, league_id: str,
                config: ModelAConfig = BASE_CONFIG, *, min_matches: int = 60) -> ModelAFit:
    """Zaman kesiminden önce bilinen maçlarla MODEL A'yı uydurur."""
    assert_football_only(records)
    cut = from_iso(cutoff)
    usable = [
        r for r in records
        if r.league_id == league_id and r.result_recorded_at <= cutoff and r.kickoff_at < cutoff
        and (cut - from_iso(r.kickoff_at)).days <= config.window_days
    ]
    if config.drop_latest_matchday and usable:
        latest_day = max(r.kickoff_at[:10] for r in usable)
        usable = [r for r in usable if r.kickoff_at[:10] != latest_day]

    teams = sorted({r.home_team_id for r in usable} | {r.away_team_id for r in usable})
    tindex = {t: i for i, t in enumerate(teams)}
    T = len(teams)
    P = 2 + 2 * T
    team_matches: dict[int, int] = {}
    for r in usable:
        team_matches[r.home_team_id] = team_matches.get(r.home_team_id, 0) + 1
        team_matches[r.away_team_id] = team_matches.get(r.away_team_id, 0) + 1
    last_result = max((r.result_recorded_at for r in usable), default=None)

    if len(usable) < min_matches or T < 2:
        return ModelAFit(league_id, cutoff, config, tindex, np.zeros(P), np.eye(P), 0.0, 0.45,
                         len(usable), team_matches, last_result, False, False,
                         f"Yetersiz geçmiş maç: {len(usable)} < {min_matches}")

    n = len(usable)
    ages = np.array([(cut - from_iso(r.kickoff_at)).total_seconds() / 86400.0 for r in usable])
    w_match = np.power(0.5, ages / config.half_life_days)
    X = np.zeros((2 * n, P))
    y = np.zeros(2 * n)
    for k, r in enumerate(usable):
        hi, ai = tindex[r.home_team_id], tindex[r.away_team_id]
        X[2 * k, 0] = 1; X[2 * k, 1] = 1; X[2 * k, 2 + hi] = 1; X[2 * k, 2 + T + ai] = -1
        y[2 * k] = r.ft_home
        X[2 * k + 1, 0] = 1; X[2 * k + 1, 2 + ai] = 1; X[2 * k + 1, 2 + T + hi] = -1
        y[2 * k + 1] = r.ft_away
    w = np.repeat(w_match, 2)

    lam_pen = np.full(P, config.ridge)
    lam_pen[:2] = 1e-6
    beta = np.zeros(P)
    beta[0] = math.log(max(1e-3, float(np.average(y, weights=w))))
    converged = False

    def objective(b: np.ndarray) -> float:
        eta = X @ b
        return float(np.sum(w * (y * eta - np.exp(eta))) - 0.5 * np.sum(lam_pen * b * b))

    obj = objective(beta)
    for _ in range(60):
        eta = X @ beta
        m = np.exp(eta)
        grad = X.T @ (w * (y - m)) - lam_pen * beta
        H = (X * (w * m)[:, None]).T @ X + np.diag(lam_pen)
        step = np.linalg.solve(H, grad)
        t = 1.0
        while True:
            cand = beta + t * step
            new_obj = objective(cand)
            if new_obj >= obj - 1e-12 or t < 1e-4:
                break
            t *= 0.5
        beta, prev = cand, obj
        obj = new_obj
        if abs(obj - prev) < 1e-9 * (1 + abs(obj)):
            converged = True
            break
    eta = X @ beta
    m = np.exp(eta)
    H = (X * (w * m)[:, None]).T @ X + np.diag(lam_pen)
    cov = np.linalg.inv(H)

    rho = 0.0
    if config.use_dc:
        lam_h, lam_a = m[0::2], m[1::2]
        gh, ga = y[0::2], y[1::2]
        best = (-np.inf, 0.0)
        for cand in np.round(np.arange(-0.20, 0.2001, 0.01), 3):
            tau = np.ones(n)
            m00 = (gh == 0) & (ga == 0)
            m01 = (gh == 0) & (ga == 1)
            m10 = (gh == 1) & (ga == 0)
            m11 = (gh == 1) & (ga == 1)
            tau[m00] = 1 - lam_h[m00] * lam_a[m00] * cand
            tau[m01] = 1 + lam_h[m01] * cand
            tau[m10] = 1 + lam_a[m10] * cand
            tau[m11] = 1 - cand
            if np.any(tau <= 0):
                continue
            ll = float(np.sum(w_match * np.log(tau)))
            if ll > best[0]:
                best = (ll, float(cand))
        rho = best[1]

    ht_num = ht_den = 0.0
    for r, wm in zip(usable, w_match):
        if r.ht_home is not None and r.ht_away is not None:
            ht_num += wm * (r.ht_home + r.ht_away)
            ht_den += wm * (r.ft_home + r.ft_away)
    ht_share = float(np.clip(ht_num / ht_den, 0.3, 0.6)) if ht_den > 0 else 0.45

    return ModelAFit(league_id, cutoff, config, tindex, beta, cov, rho, ht_share, n,
                     team_matches, last_result, converged, True)
