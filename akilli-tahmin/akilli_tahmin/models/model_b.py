"""MODEL B — futbol verisi + out-of-fold MODEL A + tahmin anında erişilebilir gerçek oran.

Hiyerarşik cezalı lojistik regresyon: önce market ailesi düzeyinde, ardından her market için
aile katsayılarını önsel ortalama alan (aileye doğru büzülen) ayrı bir model:
    logit p_B = c0 + c1·logit(p_A,oof) + c2·[oran var]·logit(p_piyasa,adil) + c3·[oran var] + c4·futbol_sinyali

- p_A,oof: MODEL A'nın o maç için *maçtan önceki* zaman kesimiyle üretilmiş ham olasılığı.
- p_piyasa,adil: zaman kesiminden önce yakalanmış oranların marjdan arındırılmış olasılığı.
- futbol_sinyali: son maç formu / gol eğilimi (seçeneğin yönüyle işaretlenir).

Eğitim satırları yalnızca kesimden önce sonucu bilinen ve özellikleri kesimden önce oluşmuş
satırlardır (`assert_known_before`). Önsel ortalama c1 = 1 olduğundan veri azken MODEL B,
MODEL A'ya yakın kalır.

Tutarlılık: dışlayıcı marketlerde olasılıklar normalize edilir; Çifte Şans ve Beraberlikte İade
MODEL B'nin Maç Sonucu olasılıklarından türetilir; Alt/Üst çizgilerinde monotonluk korunur.
"""
from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

from ..domain.markets import MARKETS, get_market
from .isolation import assert_known_before
from .logistic import LogisticFit, fit_logistic, logit, sigmoid
from .score_grid import outcome_index

MODEL_B_VERSION = "B-stacked-logit-1.0"
MODEL_B_INPUTS = "results,oof_model_a,odds_at_cutoff"

_RESULT_HOME = {"1", "1X"}
_RESULT_AWAY = {"2", "X2"}


def outcome_direction(market_id: str, outcome_id: str) -> tuple[str, float]:
    fam = get_market(market_id).family
    if fam in ("result", "double_chance", "dnb", "handicap", "halftime"):
        if outcome_id in _RESULT_HOME:
            return "form", 1.0
        if outcome_id in _RESULT_AWAY:
            return "form", -1.0
        return "form", 0.0
    if fam in ("totals", "team_totals", "halftime_totals", "btts"):
        return "goals", 1.0 if outcome_id in ("OVER", "YES") else -1.0
    if fam == "goal_range":
        return "goals", {"0-1": -1.0, "2-3": 0.0, "4-5": 1.0, "6+": 1.0}[outcome_id]
    return "none", 0.0


@dataclass(frozen=True)
class _Index:
    keys: tuple[tuple[str, str], ...]
    family: np.ndarray
    kind: np.ndarray
    direction: np.ndarray
    families: tuple[str, ...]
    exhaustive_groups: tuple[np.ndarray, ...]
    ou_chains: tuple[tuple[np.ndarray, np.ndarray], ...]  # (OVER idx sırası, UNDER idx sırası)
    market: np.ndarray
    market_idx: dict
    family_markets: dict
    k_1x2: tuple[int, int, int]
    dc: tuple[int, int, int]
    dnb: tuple[int, int]
    #: Farklı marketlerde mantıksal olarak *aynı* olaylar: (kanonik, ikiz) dizin çiftleri.
    identities: tuple[tuple[int, int], ...]


def _build_index() -> _Index:
    keys = outcome_index()[0]
    pos = {k: i for i, k in enumerate(keys)}
    fam = np.array([get_market(m).family for m, _ in keys], dtype=object)
    kinds, dirs = [], []
    for m, o in keys:
        k, d = outcome_direction(m, o)
        kinds.append(k)
        dirs.append(d)
    groups = []
    for mk in MARKETS:
        if mk.exhaustive and mk.family not in ("dnb",):
            groups.append(np.array([pos[(mk.id, o.id)] for o in mk.outcomes]))
    chains = []
    for prefix in ("OU_", "HOME_OU_", "AWAY_OU_", "HT_OU_"):
        lines = sorted((mk.line, mk.id) for mk in MARKETS if mk.id.startswith(prefix))
        chains.append((np.array([pos[(mid, "OVER")] for _, mid in lines]),
                       np.array([pos[(mid, "UNDER")] for _, mid in lines])))
    # Aynı olay: kazanma ve iade maskeleri ızgarada birebir aynı olan seçenekler (ör. ÇŞ 1X = Handikap(+1) 1,
    # Alt 0,5 = Skor 0-0, Alt 1,5 = TG 0-1, Üst 5,5 = TG 6+). Kanonik olan, öncelikli ailedekidir.
    _, win, void = outcome_index()
    prio = {f: i for i, f in enumerate(("result", "double_chance", "totals", "team_totals", "halftime",
                                         "halftime_totals", "btts", "goal_range", "handicap", "htft",
                                         "correct_score", "odd_even", "dnb"))}
    sig: dict[bytes, list[int]] = {}
    for i in range(len(keys)):
        sig.setdefault(win[i].tobytes() + void[i].tobytes(), []).append(i)
    identities = []
    for members in sig.values():
        if len(members) < 2:
            continue
        members.sort(key=lambda i: (prio.get(fam[i], 99), i))
        canon = members[0]
        identities.extend((canon, j) for j in members[1:])
    return _Index(
        keys=keys, family=fam, kind=np.array(kinds, dtype=object), direction=np.array(dirs),
        families=tuple(sorted({str(x) for x in fam})), exhaustive_groups=tuple(groups), ou_chains=tuple(chains),
        market=np.array([m for m, _ in keys], dtype=object),
        market_idx={mk.id: np.array([pos[(mk.id, o.id)] for o in mk.outcomes]) for mk in MARKETS},
        family_markets={f: [mk.id for mk in MARKETS if mk.family == f] for f in {mk.family for mk in MARKETS}},
        k_1x2=(pos[("1X2", "1")], pos[("1X2", "X")], pos[("1X2", "2")]),
        dc=(pos[("DC", "1X")], pos[("DC", "12")], pos[("DC", "X2")]),
        dnb=(pos[("DNB", "1")], pos[("DNB", "2")]),
        identities=tuple(identities),
    )


INDEX = _build_index()

#: MODEL B'nin kendi lojistik modelini kurmadığı, Maç Sonucundan türettiği aileler.
DERIVED_FAMILIES = ("double_chance", "dnb")


def signals_for(form_diff: float, goal_dev: float) -> np.ndarray:
    """(K,) seçenek yönüyle işaretlenmiş futbol sinyali."""
    base = np.where(INDEX.kind == "form", form_diff, np.where(INDEX.kind == "goals", goal_dev, 0.0))
    return base * INDEX.direction


N_FEATURES = 7
#: Önsel ortalama: oran varken ve yokken MODEL B, MODEL A'ya yakın başlar.
PRIOR_MEAN = np.array([0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0])
PRIOR_RIDGE = np.array([0.5, 2.0, 2.0, 0.5, 2.0, 4.0, 4.0])


def design(pa: np.ndarray, fair: np.ndarray, signal: np.ndarray) -> np.ndarray:
    """pa, fair, signal: (..., m) → (..., m, 7) tasarım matrisi.

    Oran bulunan ve bulunmayan rejimler ayrı katsayılarla modellenir:
    [1, var·logit p_A, var·logit p_piyasa, var, var·sinyal, yok·logit p_A, yok·sinyal].
    Böylece oranla eğitilmiş (MODEL A'ya düşük ağırlık veren) katsayılar oransız bir markete
    uygulanıp anlamsız olasılık üretmez; oransız rejimde önsel MODEL A'yı korur.
    """
    has = np.isfinite(fair).astype(float)
    no = 1.0 - has
    fm = np.where(np.isfinite(fair), fair, 0.5)
    la = logit(pa)
    return np.stack([np.ones_like(pa), has * la, has * logit(fm), has, has * signal, no * la, no * signal], axis=-1)


@dataclass
class TrainingRows:
    """MODEL B eğitim satırları (tek aile)."""

    ident: list[str]
    known_at: list[str]
    feature_cutoff: list[str]
    pa: np.ndarray
    fair: np.ndarray
    signal: np.ndarray
    y: np.ndarray
    market: np.ndarray | None = None


@dataclass
class ModelB:
    data_cutoff_at: str
    fits: dict[str, LogisticFit | None]
    n_rows: dict[str, int]
    invalid_reasons: dict[str, str] = field(default_factory=dict)
    market_fits: dict[str, LogisticFit] = field(default_factory=dict)

    def family_valid(self, family: str) -> bool:
        if family in DERIVED_FAMILIES:
            return self.fits.get("result") is not None
        return self.fits.get(family) is not None

    def predict(self, pa: np.ndarray, fair: np.ndarray, signal: np.ndarray, *,
                rng: np.random.Generator | None = None, coef_draws: bool = False) -> tuple[np.ndarray, np.ndarray]:
        """pa: (S × K) MODEL A ham olasılıkları; fair, signal: (K,). Dönüş (S × K) kazanma, iade."""
        pa = np.atleast_2d(pa)
        S, K = pa.shape
        out = np.full((S, K), np.nan)
        X = design(pa, np.broadcast_to(fair, (S, K)), np.broadcast_to(signal, (S, K)))
        for fam in INDEX.families:
            if fam in DERIVED_FAMILIES:
                continue
            if self.fits.get(fam) is None:
                continue
            for mid in INDEX.family_markets[fam]:
                f = self.market_fits.get(mid) or self.fits[fam]
                idx = INDEX.market_idx[mid]
                if coef_draws and rng is not None:
                    coefs = f.draw_coefs(S, rng)  # (S × N_FEATURES)
                    z = np.einsum("smk,sk->sm", X[:, idx, :], coefs)
                else:
                    z = X[:, idx, :] @ f.coef
                out[:, idx] = sigmoid(z)
        return enforce_consistency(out)


def enforce_consistency(p: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Olasılıkları market kurallarıyla tutarlı hale getirir. p: (S × K), NaN = model yok."""
    p = p.copy()
    void = np.zeros_like(p)
    for g in INDEX.exhaustive_groups:
        block = p[:, g]
        if np.all(np.isfinite(block)):
            p[:, g] = block / block.sum(axis=1, keepdims=True)
    for over_idx, under_idx in INDEX.ou_chains:
        ov = p[:, over_idx]
        if np.all(np.isfinite(ov)):
            # çizgi yükseldikçe Üst olasılığı artamaz (izotonik, ağırlıksız PAV)
            mono = np.array([_pav_decreasing(row) for row in ov])
            p[:, over_idx] = mono
            p[:, under_idx] = 1.0 - mono
    i1, ix, i2 = INDEX.k_1x2
    if np.all(np.isfinite(p[:, [i1, ix, i2]])):
        d1x, d12, dx2 = INDEX.dc
        p[:, d1x] = p[:, i1] + p[:, ix]
        p[:, d12] = p[:, i1] + p[:, i2]
        p[:, dx2] = p[:, ix] + p[:, i2]
        n1, n2 = INDEX.dnb
        p[:, n1] = p[:, i1]
        p[:, n2] = p[:, i2]
        void[:, n1] = p[:, ix]
        void[:, n2] = p[:, ix]
    # Aynı olay farklı marketlerde aynı olasılığı taşır; ikizin bulunduğu dışlayıcı market,
    # sabitlenen ikiz dışındaki seçenekler yeniden ölçeklenerek tekrar 1'e tamamlanır.
    fixed = np.zeros(p.shape[1], bool)
    for canon, twin in INDEX.identities:
        if np.all(np.isfinite(p[:, canon])) and np.all(np.isfinite(p[:, twin])):
            p[:, twin] = p[:, canon]
            fixed[twin] = True
    if fixed.any():
        for g in INDEX.exhaustive_groups:
            fx = fixed[g]
            if not fx.any() or fx.all() or not np.all(np.isfinite(p[:, g])):
                continue
            free = g[~fx]
            rest = np.clip(1.0 - p[:, g[fx]].sum(axis=1), 0.0, 1.0)
            tot = p[:, free].sum(axis=1)
            p[:, free] = p[:, free] * (rest / np.maximum(tot, 1e-12))[:, None]
    return np.clip(p, 0.0, 1.0), void


def _pav_decreasing(x: np.ndarray) -> np.ndarray:
    vals = list(-x)
    blocks = [[v, 1] for v in vals]
    i = 0
    while i < len(blocks) - 1:
        if blocks[i][0] / blocks[i][1] > blocks[i + 1][0] / blocks[i + 1][1]:
            blocks[i] = [blocks[i][0] + blocks[i + 1][0], blocks[i][1] + blocks[i + 1][1]]
            del blocks[i + 1]
            i = max(0, i - 1)
        else:
            i += 1
    out = []
    for s, c in blocks:
        out.extend([s / c] * c)
    return -np.array(out)


def consistency_report(p_win: np.ndarray, p_void: np.ndarray, tol: float = 1e-6) -> dict:
    """Adım 5 — olasılık tutarlılığı: dışlayıcı marketlerin toplamı, türetilmiş marketler, farklı
    marketlerdeki aynı olayların eşitliği ve Alt/Üst monotonluğu."""
    p_win = np.atleast_2d(p_win)[0]
    p_void = np.atleast_2d(p_void)[0]
    worst = 0.0
    for mk in MARKETS:
        if not mk.exhaustive:
            continue
        idx = [INDEX.keys.index((mk.id, o.id)) for o in mk.outcomes]
        vals = p_win[idx]
        if not np.all(np.isfinite(vals)):
            continue
        total = vals.sum() + (p_void[idx[0]] if mk.family == "dnb" else 0.0)
        worst = max(worst, abs(total - 1.0))
    i1, ix, i2 = INDEX.k_1x2
    d1x, d12, dx2 = INDEX.dc
    if np.all(np.isfinite(p_win[[i1, ix, i2, d1x]])):
        worst = max(worst, abs(p_win[d1x] - p_win[i1] - p_win[ix]), abs(p_win[dx2] - p_win[ix] - p_win[i2]),
                    abs(p_win[d12] - p_win[i1] - p_win[i2]))
    for canon, twin in INDEX.identities:
        if np.isfinite(p_win[canon]) and np.isfinite(p_win[twin]):
            worst = max(worst, abs(p_win[canon] - p_win[twin]))
    mono_ok = True
    for over_idx, _ in INDEX.ou_chains:
        ov = p_win[over_idx]
        if np.all(np.isfinite(ov)) and np.any(np.diff(ov) > tol):
            mono_ok = False
    return {"ok": bool(worst <= tol and mono_ok), "max_deviation": float(worst), "monotone": mono_ok}


def train_model_b(rows_by_family: dict[str, TrainingRows], cutoff: str, *, min_samples: int) -> ModelB:
    fits: dict[str, LogisticFit | None] = {}
    market_fits: dict[str, LogisticFit] = {}
    n_rows: dict[str, int] = {}
    reasons: dict[str, str] = {}
    prior = PRIOR_MEAN
    ridge = PRIOR_RIDGE
    for fam in INDEX.families:
        if fam in DERIVED_FAMILIES:
            continue
        rows = rows_by_family.get(fam)
        n = 0 if rows is None else int(rows.y.size)
        n_rows[fam] = n
        if rows is None or n < min_samples:
            fits[fam] = None
            reasons[fam] = f"MODEL B eğitim örneklemi yetersiz ({n} < {min_samples})"
            continue
        # Zaman sızıntısı koruması: sonuç ve özellikler kesimden önce bilinmeli.
        assert_known_before(zip(rows.ident, rows.known_at), cutoff, what="MODEL B eğitim sonucu")
        assert_known_before(zip(rows.ident, rows.feature_cutoff), cutoff, what="MODEL B eğitim özelliği")
        X = design(rows.pa, rows.fair, rows.signal)
        fam_fit = fit_logistic(X, rows.y, ridge=ridge, prior_mean=prior)
        fits[fam] = fam_fit
        if rows.market is not None:
            for mid in INDEX.family_markets[fam]:
                m = rows.market == mid
                if m.sum() >= min_samples:
                    # aile katsayılarına doğru büzülen market modeli
                    market_fits[mid] = fit_logistic(X[m], rows.y[m], ridge=np.full(N_FEATURES, 25.0),
                                                    prior_mean=fam_fit.coef)
    return ModelB(cutoff, fits, n_rows, reasons, market_fits)
