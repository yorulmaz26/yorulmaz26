"""Akıllı Tahmin Motoru — 15 adımlı market tarama ve seçim süreci.

 1. Fikstürü ve veri durumunu doğrula.        9. Market bağımlılığını değerlendir.
 2. Mevcut marketleri al.                     10. Güncel oran ve EV hesaplarını üret.
 3. MODEL A tahminlerini üret.                11. Tahmin Kalite Kapısı'nı uygula.
 4. MODEL B tahminlerini üret.                12. Üç seçim modu için uygun adayları ayrı belirle.
 5. Olasılık tutarlılığını kontrol et.        13. Bağımsız denetimi uygula.
 6. Kalibrasyon ve belirsizliği değerlendir.  14. Sonuçları gerekçeleriyle kaydet.
 7. Tahmin dayanıklılığını kontrol et.        15. Kullanıcının moduna göre sırala (API katmanı).
 8. Tahmin geçerliliğini kontrol et.

Moddan bağımsız her şey (1–11) bir kez hesaplanır ve `candidate_evaluations` tablosuna yazılır;
üç mod (12) aynı değerlendirmeyi okur. Bu nedenle mod değişimi MODEL A/B olasılıklarını değiştiremez.
"""
from __future__ import annotations

import math
import sqlite3
from dataclasses import dataclass, field

import numpy as np

from ..calibration.calibrate import CalibrationMap, fit_calibration
from ..config import SETTINGS, Settings
from ..db.database import (MODE_CODES, dumps, from_iso, insert, insert_many, iso_add, loads, to_iso,
                           transaction, utcnow)
from ..domain.markets import MARKETS, MatchResult, get_market
from ..data.point_in_time import FootballDataView, football_features, latest_quotes
from ..models.combiner import Combiner, fit_combiner
from ..models.logistic import logit
from ..models.model_a import (MODEL_A_INPUTS, MODEL_A_VERSION, ROBUSTNESS_SCENARIOS, ModelAFit,
                              fit_model_a)
from ..models.model_b import (INDEX, MODEL_B_INPUTS, MODEL_B_VERSION, ModelB, TrainingRows,
                              consistency_report, enforce_consistency, signals_for, train_model_b)
from ..models.score_grid import joint_weights, outcome_index
from ..odds.odds import expected_value, fair_probabilities, overround, validate_quote
from .audit import audit_candidate
from .checks import Check
from .history import QUANTILES, FixtureRecord, History
from .modes import (CandidateMetrics, ModeConfig, ModeThresholds, evaluate_mode)
from .thresholds import tune_mode

K = len(INDEX.keys)
KEY_POS = {k: i for i, k in enumerate(INDEX.keys)}
#: Kalibrasyonda türetilmiş ailelerin (Çifte Şans, Beraberlikte İade) dayandığı aile.
CAL_FAMILY = {"double_chance": "result", "dnb": "result"}
HISTORY_WINDOW_DAYS = 730
CONFIG_REFRESH_DAYS = 7


def cal_family(fam: str) -> str:
    return CAL_FAMILY.get(fam, fam)


@dataclass
class CutoffModels:
    cutoff: str
    model_b: ModelB
    cal_a: dict[str, CalibrationMap]
    cal_b: dict[str, CalibrationMap]
    combiner: Combiner
    configs: dict[str, ModeConfig]
    model_b_version_ids: dict[str, int] = field(default_factory=dict)
    calibration_ids: dict[tuple[str, str], int] = field(default_factory=dict)


@dataclass
class RunSummary:
    run_id: int
    as_of: str
    fixtures: int
    evaluations: int
    selected: dict[str, int]
    eligible: dict[str, int]


class Engine:
    def __init__(self, conn: sqlite3.Connection, settings: Settings = SETTINGS, history: History | None = None,
                 *, n_draws: int | None = None):
        self.conn = conn
        self.s = settings
        self.football = FootballDataView(conn)
        self.history = history if history is not None else History.from_db(conn)
        self.n_draws = n_draws or settings.stats.uncertainty_draws
        self._config_cache: dict[str, ModeConfig] = {}

    # ───────────────────────── zaman kesimine bağlı modeller ─────────────────────────
    def prepare(self, cutoff: str) -> CutoffModels:
        st = self.s.stats
        view = self.history.view(cutoff, HISTORY_WINDOW_DAYS)
        fam = view.keys_family()
        y = view.stacked("y")
        pa = view.stacked("pa_raw")
        pb = view.stacked("pb_raw")
        fair = view.stacked("fair")
        sig = view.stacked("signal")
        fids = view.fixture_ids()
        mkt = np.tile(INDEX.market, len(view.records))
        known = view.known()
        cuts = view.cutoffs()
        rows: dict[str, TrainingRows] = {}
        cal_a: dict[str, CalibrationMap] = {}
        cal_b: dict[str, CalibrationMap] = {}
        for f in INDEX.families:
            m = (fam == f) & np.isfinite(y) & np.isfinite(pa)
            if m.any():
                rows[f] = TrainingRows([str(x) for x in fids[m]], list(known[m]), list(cuts[m]),
                                       pa[m], fair[m], np.nan_to_num(sig[m]), y[m], mkt[m])
            if f in CAL_FAMILY:
                continue
            cal_a[f] = fit_calibration("A", f, pa[m], y[m], min_samples=st.min_calibration_samples,
                                       alpha=st.calibration_alpha)
            mb = (fam == f) & np.isfinite(y) & np.isfinite(pb)
            cal_b[f] = fit_calibration("B", f, pb[mb], y[mb], min_samples=st.min_calibration_samples,
                                       alpha=st.calibration_alpha)
        model_b = train_model_b(rows, cutoff, min_samples=st.min_model_b_samples)
        combiner = fit_combiner(view.stacked("pa_cal"), view.stacked("pb_cal"), y,
                                min_samples=st.min_calibration_samples, alpha=st.calibration_alpha)
        configs = {mode: self._mode_config(mode, cutoff) for mode in MODE_CODES}
        return CutoffModels(cutoff, model_b, cal_a, cal_b, combiner, configs)

    def _mode_config(self, mode: str, cutoff: str) -> ModeConfig:
        row = self.conn.execute(
            "SELECT * FROM selection_mode_configs WHERE mode=? AND data_cutoff_at<=? ORDER BY data_cutoff_at DESC, id DESC"
            " LIMIT 1", (mode, cutoff),
        ).fetchone()
        if row is not None and row["data_cutoff_at"] >= iso_add(cutoff, days=-CONFIG_REFRESH_DAYS):
            return _config_from_row(row)
        view = self.history.view(cutoff, 540)
        cfg = tune_mode(mode, view, self.s.stats, data_cutoff_at=cutoff)
        cfg.db_id = insert(self.conn, "selection_mode_configs", {
            "mode": mode, "status": cfg.status,
            "thresholds": dumps(cfg.thresholds.as_api() if cfg.thresholds else {}),
            "source": "chronological_validation", "validation_start": cfg.validation_start,
            "validation_end": cfg.validation_end, "validation_metrics": dumps(cfg.metrics),
            "data_cutoff_at": cutoff, "created_at": to_iso(utcnow()),
        })
        return cfg

    # ───────────────────────── çalıştırma ─────────────────────────
    def run(self, as_of: str, *, kind: str = "prematch", horizon_hours: float = 96.0,
            fixture_ids: list[int] | None = None, models: CutoffModels | None = None,
            point_in_time_status: bool = False) -> RunSummary:
        """`point_in_time_status`: geçmiş testte fikstür durumu `as_of` anındaki haliyle kullanılır."""
        cutoff = as_of
        self._kind = kind
        if fixture_ids is None:
            fx = self.conn.execute(
                "SELECT * FROM fixtures WHERE status='scheduled' AND kickoff_at>? AND kickoff_at<=? ORDER BY kickoff_at",
                (as_of, iso_add(as_of, hours=horizon_hours)),
            ).fetchall()
        else:
            q = ",".join("?" for _ in fixture_ids)
            fx = self.conn.execute(f"SELECT * FROM fixtures WHERE id IN ({q}) ORDER BY kickoff_at", fixture_ids).fetchall()
        fx = [dict(r) for r in fx]
        if point_in_time_status:
            for r in fx:
                if not (r["result_recorded_at"] and r["result_recorded_at"] <= as_of):
                    r["status"] = "scheduled" if r["kickoff_at"] > as_of else "live"
        with transaction(self.conn):
            models = models or self.prepare(cutoff)
            run_id = insert(self.conn, "prediction_runs", {
                "kind": kind, "as_of": as_of, "data_cutoff_at": cutoff, "started_at": to_iso(utcnow()),
                "status": "running", "is_synthetic": int(any(r["is_synthetic"] for r in fx)) if fx else 0,
                "notes": dumps({"combiner": models.combiner.method}),
            })
            cal_ids = self._store_models(models, run_id)
            selected = {m: 0 for m in MODE_CODES}
            eligible = {m: 0 for m in MODE_CODES}
            n_eval = 0
            leagues = sorted({r["league_id"] for r in fx})
            a_fits = {lg: fit_model_a(self.football.all_records(lg), cutoff, lg,
                                      min_matches=self.s.data.min_league_matches) for lg in leagues}
            a_scen = {lg: [fit_model_a(self.football.all_records(lg), cutoff, lg, sc,
                                       min_matches=self.s.data.min_league_matches)
                           for sc in ROBUSTNESS_SCENARIOS] for lg in leagues}
            a_ver = {lg: self._store_model_a(a_fits[lg], cutoff) for lg in leagues}
            quotes = latest_quotes(self.conn, [r["id"] for r in fx], as_of)
            for f in fx:
                res = self._evaluate_fixture(f, run_id, as_of, cutoff, models, a_fits[f["league_id"]],
                                             a_scen[f["league_id"]], a_ver[f["league_id"]], quotes.get(f["id"], {}),
                                             cal_ids)
                n_eval += K
                for m in MODE_CODES:
                    selected[m] += res[m][0]
                    eligible[m] += res[m][1]
            self.conn.execute("UPDATE prediction_runs SET status='finished', finished_at=? WHERE id=?",
                              (to_iso(utcnow()), run_id))
        return RunSummary(run_id, as_of, len(fx), n_eval, selected, eligible)

    # ───────────────────────── kayıt yardımcıları ─────────────────────────
    def _store_model_a(self, fit: ModelAFit, cutoff: str) -> int:
        return insert(self.conn, "model_versions", {
            "model": "A", "league_id": fit.league_id, "family": None, "version": MODEL_A_VERSION,
            "params": dumps(fit.params_summary()), "training_rows": fit.n_matches, "data_cutoff_at": cutoff,
            "uses_odds": 0, "created_at": to_iso(utcnow()),
        })

    def _store_models(self, models: CutoffModels, run_id: int) -> dict[tuple[str, str], int]:
        if models.calibration_ids:
            return models.calibration_ids  # aynı kesim modelleri bir kez kaydedilir
        ids: dict[tuple[str, str], int] = models.calibration_ids
        now = to_iso(utcnow())
        for fam, f in models.model_b.fits.items():
            models.model_b_version_ids[fam] = insert(self.conn, "model_versions", {
                "model": "B", "league_id": None, "family": fam, "version": MODEL_B_VERSION,
                "params": dumps({"coef": None if f is None else f.coef.tolist(), "inputs": MODEL_B_INPUTS,
                                 "market_coef": {mid: [round(x, 5) for x in mf.coef.tolist()]
                                                 for mid, mf in models.model_b.market_fits.items()
                                                 if get_market(mid).family == fam},
                                 "n": models.model_b.n_rows.get(fam, 0),
                                 "invalid_reason": models.model_b.invalid_reasons.get(fam)}),
                "training_rows": models.model_b.n_rows.get(fam, 0), "data_cutoff_at": models.cutoff,
                "uses_odds": 1, "created_at": now,
            })
        for model, maps in (("A", models.cal_a), ("B", models.cal_b)):
            for fam, cm in maps.items():
                ids[(model, fam)] = insert(self.conn, "calibration_maps", {
                    "model": model, "family": fam, "method": cm.method,
                    "params": dumps({"coef": None if cm.fit is None else [round(x, 6) for x in cm.fit.coef.tolist()],
                                     "bins": [[round(b["p_mean"], 4), round(b["observed"], 4), b["n"]] for b in cm.bins],
                                     "p_range": [round(x, 5) for x in cm.p_range]}),
                    "n": cm.n, "adequate": int(cm.adequate), "tests": dumps(cm.tests),
                    "data_cutoff_at": models.cutoff, "created_at": now,
                })
                cm.db_id = ids[(model, fam)]
        return ids

    # ───────────────────────── tek fikstür ─────────────────────────
    def _evaluate_fixture(self, f: sqlite3.Row, run_id: int, as_of: str, cutoff: str, models: CutoffModels,
                          fit: ModelAFit, scen: list[ModelAFit], a_version_id: int, quotes: dict,
                          cal_ids: dict) -> dict[str, tuple[int, int]]:
        dp, st = self.s.data, self.s.stats
        fid, home, away = f["id"], f["home_team_id"], f["away_team_id"]
        rng = np.random.default_rng(fid * 7919 + int(from_iso(cutoff).timestamp()) % 100000)

        # 1 — fikstür ve veri durumu
        fixture_ok = f["status"] == "scheduled" and f["kickoff_at"] > as_of
        last_res = fit.last_result_at
        fresh = last_res is not None and last_res >= iso_add(cutoff, days=-dp.max_result_data_age_days)
        sufficient = (fit.valid and fit.team_sample(home) >= dp.min_team_matches
                      and fit.team_sample(away) >= dp.min_team_matches)
        # 3 — MODEL A
        pa_w, pa_v = fit.predict(home, away)
        pa_dw, _ = fit.predict_draws(home, away, self.n_draws, seed=int(rng.integers(1 << 31)))
        records = self.football.match_records(f["league_id"], cutoff)
        feats = football_features(records, home, away)
        signal = signals_for(feats.form_diff, feats.goal_dev)
        # 2 + 10 — oranlar, marj, adil olasılık
        odds = np.full(K, np.nan)
        fair = np.full(K, np.nan)
        margin = np.full(K, np.nan)
        snap = np.zeros(K, dtype=np.int64)
        odds_val: list = [None] * K
        for mk in MARKETS:
            qs = [quotes.get((mk.id, o.id)) for o in mk.outcomes]
            for o, q in zip(mk.outcomes, qs):
                i = KEY_POS[(mk.id, o.id)]
                v = validate_quote(q, as_of, dp.max_prematch_odds_age_seconds,
                                   allow_approximate=self._kind == "backtest")
                odds_val[i] = v
                if q is not None:
                    odds[i] = q.decimal_odds
                    snap[i] = q.snapshot_id
            if (mk.exhaustive and mk.family not in ("dnb",) and all(q is not None for q in qs)
                    and len({q.source for q in qs}) == 1):
                prices = [q.decimal_odds for q in qs]
                fp = fair_probabilities(prices)
                mg = overround(prices)
                for o, p in zip(mk.outcomes, fp):
                    i = KEY_POS[(mk.id, o.id)]
                    fair[i] = p
                    margin[i] = mg
        # 4 — MODEL B (MODEL A'nın aynı kesimli çıktısı + kesimden önceki oran)
        pb_w, pb_v = models.model_b.predict(pa_w[None, :], fair, signal)
        pb_dw, pb_dv = models.model_b.predict(pa_dw, fair, signal, rng=rng, coef_draws=True)
        pb_w, pb_v = pb_w[0], pb_v[0]
        # 5 — tutarlılık
        cons_a = consistency_report(pa_w, pa_v)
        cons_b = consistency_report(pb_w, pb_v)
        # 6 — kalibrasyon (+ parametre belirsizliği) ve tutarlılığın korunması
        ca, ca_v = self._calibrate(models.cal_a, pa_w[None, :])
        cb, cb_v = self._calibrate(models.cal_b, pb_w[None, :])
        ca_d, ca_dv = self._calibrate(models.cal_a, pa_dw, rng)
        cb_d, cb_dv = self._calibrate(models.cal_b, pb_dw, rng)
        ca, ca_v, cb, cb_v = ca[0], ca_v[0], cb[0], cb_v[0]
        p_est, p_void = models.combiner.combine(ca, cb, ca_v, cb_v)
        p_est_d, _ = models.combiner.combine(ca_d, cb_d, ca_dv, cb_dv, rng)
        with np.errstate(invalid="ignore"):
            pq = np.nanquantile(np.where(np.isfinite(p_est_d), p_est_d, np.nan), QUANTILES, axis=0) \
                if np.isfinite(p_est_d).any() else np.full((len(QUANTILES), K), np.nan)
        width = pq[QUANTILES.index(0.9)] - pq[QUANTILES.index(0.1)]
        dis = np.abs(logit(ca) - logit(cb))
        dis = np.where(np.isfinite(ca) & np.isfinite(cb), dis, np.nan)
        rel_width = width / np.maximum(p_est, 1e-9)
        market_dev = np.where(np.isfinite(fair) & np.isfinite(p_est), np.abs(logit(p_est) - logit(fair)), np.nan)
        # 7 — dayanıklılık senaryoları
        scen_p = []
        for sfit in scen:
            if not sfit.valid:
                scen_p.append(np.full(K, np.nan))
                continue
            sa, sav = sfit.predict(home, away)
            sb, sbv = models.model_b.predict(sa[None, :], fair, signal)
            sca, scav = self._calibrate(models.cal_a, sa[None, :])
            scb, scbv = self._calibrate(models.cal_b, sb)
            sp, _ = models.combiner.combine(sca[0], scb[0], scav[0], scbv[0])
            scen_p.append(sp)
        scen_p = np.array(scen_p)
        with np.errstate(invalid="ignore"):
            robust_down = np.where(np.all(np.isfinite(scen_p), axis=0), np.min(scen_p - p_est, axis=0), np.nan)
        # 9 — market bağımlılığı (MODEL A ortak dağılımından): iç içe olaylar ve korelasyon
        lh, la = fit.expected_goals(home, away)
        w = joint_weights(np.array([lh]), np.array([la]), fit.ht_share, fit.rho)[0]
        _, win_mask, _ = outcome_index()
        pab = (win_mask * w) @ win_mask.T
        pk = np.diag(pab)
        with np.errstate(invalid="ignore", divide="ignore"):
            phi = (pab - np.outer(pk, pk)) / np.sqrt(np.outer(pk * (1 - pk), pk * (1 - pk)))
        support = (w > 1e-12).astype(float)
        nested = ((win_mask * support) @ (1 - win_mask).T) < 0.5  # nested[i,j]: i ⊆ j
        # 10 — EV
        ev_a = ca * odds + ca_v - 1.0
        ev_b = cb * odds + cb_v - 1.0
        ev = p_est * odds + p_void - 1.0

        # rules
        rules = self._rules()
        now_iso = to_iso(utcnow()) if as_of is None else as_of
        # tahminleri kaydet
        a_ids, b_ids = self._store_predictions(run_id, fid, cutoff, now_iso, pa_w, pa_v, ca, pq_a=ca_d,
                                               pb=pb_w, pbv=pb_v, cb=cb, pq_b=cb_d, models=models,
                                               a_version_id=a_version_id, cal_ids=cal_ids)
        # 8 + 11 — geçerlilik ve Kalite Kapısı
        eval_rows = []
        metrics_list: list[CandidateMetrics] = []
        for i, (mid, oid) in enumerate(INDEX.keys):
            fam = str(INDEX.family[i])
            cf = cal_family(fam)
            mk = get_market(mid)
            gate: list[Check] = []
            gate.append(Check("fixture_ok", fixture_ok, f["status"], None,
                              "Fikstür planlı değil veya başlama saati geçti."))
            gate.append(Check("data_fresh", fresh, last_res, dp.max_result_data_age_days,
                              f"Son sonuç verisi {last_res or 'yok'}; en fazla {dp.max_result_data_age_days} gün olmalı."))
            gate.append(Check("data_sufficient", sufficient, [fit.team_sample(home), fit.team_sample(away)],
                              dp.min_team_matches,
                              fit.invalid_reason or f"Takım başına en az {dp.min_team_matches} geçmiş maç gerekli."))
            gate.append(Check("settlement_rules", (mid, "standard") in rules, None, None,
                              "Market için doğrulanmış sonuçlandırma kuralı yok."))
            gate.append(Check("model_a_valid", fit.valid and bool(np.isfinite(pa_w[i])), MODEL_A_VERSION, None,
                              fit.invalid_reason or "MODEL A tahmini üretilemedi."))
            b_ok = models.model_b.family_valid(fam) and bool(np.isfinite(pb_w[i]))
            gate.append(Check("model_b_valid", b_ok, MODEL_B_VERSION, None,
                              models.model_b.invalid_reasons.get(cf, "MODEL B bu aile için geçerli değil.")))
            gate.append(Check("probability_consistency", cons_a["ok"] and cons_b["ok"],
                              max(cons_a["max_deviation"], cons_b["max_deviation"]), 1e-6,
                              "Market olasılıkları birbirleriyle tutarsız."))
            for code, maps, pc in (("calibration_a", models.cal_a, ca[i]), ("calibration_b", models.cal_b, cb[i])):
                cm = maps.get(cf)
                if cm is None or not np.isfinite(pc):
                    gate.append(Check(code, False, None, None, "Kalibrasyon haritası yok."))
                    continue
                loc = cm.local_support(float(pc), st.min_local_calibration_samples)
                ok = cm.adequate and loc["ok"]
                reason = "; ".join(cm.reasons) if not cm.adequate else (loc.get("reason") or "")
                gate.append(Check(code, ok, cm.n, st.min_calibration_samples, reason))
            valid_pred = fixture_ok and cutoff <= as_of < f["kickoff_at"]
            gate.append(Check("prediction_valid", valid_pred, f["kickoff_at"], None,
                              "Tahmin geçerlilik süresi dolmuş (maç başladı veya veri değişti)."))
            core_pass = all(c.passed for c in gate)
            ov = odds_val[i]
            q = quotes.get((mid, oid))
            odds_ok = bool(ov.valid) if ov is not None else False
            src_rule = q is not None and (mid, q.source) in rules
            gate.append(Check("odds_valid", odds_ok, None if ov is None else ov.age_seconds,
                              dp.max_prematch_odds_age_seconds, "" if ov is None else ov.detail))
            gate.append(Check("odds_settlement_rules", src_rule, None if q is None else q.source, None,
                              "Oran kaynağı için doğrulanmış sonuçlandırma kuralı yok."))
            odds_ok = odds_ok and src_rule
            metrics = CandidateMetrics(
                core_pass=core_pass, odds_valid=odds_ok, p_est=_f(p_est[i]), p_void=float(p_void[i] or 0.0),
                p_q=[_f(pq[j, i]) for j in range(len(QUANTILES))], width=_f(width[i]), rel_width=_f(rel_width[i]),
                dis=_f(dis[i]), market_dev=_f(market_dev[i]),
                ev_a=_f(ev_a[i]) if odds_ok else None, ev_b=_f(ev_b[i]) if odds_ok else None,
                odds=_f(odds[i]) if odds_ok else None, robust_down=_f(robust_down[i]),
                core_failed=[c.code for c in gate if not c.passed],
            )
            metrics_list.append(metrics)
            unc = {"q": {f"{qq:.1f}": _r(pq[j, i]) for j, qq in enumerate(QUANTILES)}}
            rob = {"s": [_r(x) for x in scen_p[:, i]]}
            eval_rows.append({
                "run_id": run_id, "fixture_id": fid, "market_id": mid, "outcome_id": oid,
                "model_a_prediction_id": a_ids[i], "model_b_prediction_id": b_ids[i],
                "odds_snapshot_id": int(snap[i]) if snap[i] else None, "data_cutoff_at": cutoff,
                "evaluated_at": as_of, "p_a_raw": float(pa_w[i]), "p_b_raw": _f(pb_w[i]),
                "p_a_cal": _f(ca[i]), "p_b_cal": _f(cb[i]), "p_void": float(p_void[i] or 0.0),
                "estimated_probability": _f(p_est[i]), "probability_method": models.combiner.method,
                "fair_probability": _f(fair[i]), "market_margin": _f(margin[i]), "football_signal": float(signal[i]),
                "decimal_odds": _f(odds[i]), "ev_model_a": _f(ev_a[i]), "ev_model_b": _f(ev_b[i]),
                "estimated_ev": _f(ev[i]), "interval_width": _f(width[i]), "relative_width": _f(rel_width[i]),
                "disagreement": _f(dis[i]), "market_deviation": _f(market_dev[i]),
                "robust_down": _f(robust_down[i]), "core_pass": int(core_pass), "odds_valid": int(odds_ok),
                "uncertainty_metrics": dumps(unc), "robustness": dumps(rob),
                "gate_failed": dumps([c.pack() for c in gate if not c.passed]),
            })
        insert(self.conn, "fixture_evaluations", {
            "run_id": run_id, "fixture_id": fid, "data_cutoff_at": cutoff, "evaluated_at": as_of,
            "validity": dumps({"valid_until": f["kickoff_at"], "data_cutoff_at": cutoff, "last_result_at": last_res}),
            "freshness": dumps({"last_result_at": last_res, "team_matches": [fit.team_sample(home), fit.team_sample(away)],
                                "max_age_days": dp.max_result_data_age_days}),
            "model_a": dumps({"lambda": [round(lh, 4), round(la, 4)], "rho": fit.rho, "ht_share": round(fit.ht_share, 4),
                              "scenarios": [sc.name for sc in ROBUSTNESS_SCENARIOS],
                              "consistency": [cons_a, cons_b]}),
        })
        insert_many(self.conn, "candidate_evaluations", eval_rows)
        eval_ids = {(r[0], r[1]): r[2] for r in self.conn.execute(
            "SELECT market_id, outcome_id, id FROM candidate_evaluations WHERE run_id=? AND fixture_id=?", (run_id, fid))}

        # geçmiş deposuna ekle (sonuç bilinince doldurulur)
        self.history.add(FixtureRecord(
            fixture_id=fid, league_id=f["league_id"], kickoff_at=f["kickoff_at"], data_cutoff_at=cutoff,
            pa_raw=pa_w.copy(), pb_raw=pb_w.copy(), pa_cal=ca.copy(), pb_cal=cb.copy(), fair=fair.copy(),
            signal=signal.copy(), p_est=p_est.copy(), p_void=np.nan_to_num(p_void).copy(), p_q=pq.copy(),
            width=width.copy(), dis=dis.copy(), market_dev=market_dev.copy(),
            ev_a=np.array([m.ev_a if m.ev_a is not None else np.nan for m in metrics_list]),
            ev_b=np.array([m.ev_b if m.ev_b is not None else np.nan for m in metrics_list]),
            odds=np.array([m.odds if m.odds is not None else np.nan for m in metrics_list]),
            robust_down=robust_down.copy(),
            core_pass=np.array([m.core_pass for m in metrics_list]),
            odds_valid=np.array([m.odds_valid for m in metrics_list]),
        ))

        # 12 — üç mod ayrı ayrı
        out: dict[str, tuple[int, int]] = {}
        for mode in MODE_CODES:
            cfg = models.configs[mode]
            evals = [evaluate_mode(mode, m, cfg) for m in metrics_list]
            order = sorted((i for i, e in enumerate(evals) if e.eligible), key=lambda i: -evals[i].rank_score)
            chosen: list[int] = []
            nested_block: dict[int, int] = {}
            for i in order:
                blocker = next((j for j in chosen if nested[i, j] or nested[j, i]), None)
                if blocker is not None:
                    nested_block[i] = blocker
                    continue
                chosen.append(i)
            rank = {i: r + 1 for r, i in enumerate(chosen)}
            cand_rows = []
            for i, e in enumerate(evals):
                checks = list(e.checks)
                if e.eligible:
                    b = nested_block.get(i)
                    checks.append(Check("nested_duplicate", b is None, None, None,
                                        "" if b is None else
                                        f"Daha üst sıradaki {INDEX.keys[b][0]}·{INDEX.keys[b][1]} seçimiyle iç içe."))
                related = []
                if i in rank:
                    for j in chosen:
                        if j != i and np.isfinite(phi[i, j]):
                            related.append([INDEX.keys[j][0], INDEX.keys[j][1], round(float(phi[i, j]), 3)])
                cand_rows.append({
                    "run_id": run_id, "evaluation_id": eval_ids[INDEX.keys[i]], "selection_mode": mode,
                    "config_id": cfg.db_id, "fixture_id": fid, "market_id": INDEX.keys[i][0],
                    "outcome_id": INDEX.keys[i][1], "probability_lower": e.p_low, "ev_lower": e.ev_low,
                    "mode_checks": dumps({"checks": [c.pack(with_detail=c.code == "thresholds_established")
                                                     for c in checks if e.eligible or not c.passed],
                                          "related": related, "p_low_rob": _r(e.p_low_robust),
                                          "ev_low_rob": _r(e.ev_low_robust)}),
                    "eligible": int(e.eligible), "selected": int(i in rank), "rank": rank.get(i),
                    "rank_score": e.rank_score,
                })
            insert_many(self.conn, "selection_mode_candidates", cand_rows)
            cand_ids = {(r[0], r[1]): r[2] for r in self.conn.execute(
                "SELECT market_id, outcome_id, id FROM selection_mode_candidates WHERE run_id=? AND fixture_id=?"
                " AND selection_mode=?", (run_id, fid, mode))}
            n_sel = 0
            rej_rows = []
            for i, e in enumerate(evals):
                cid = cand_ids[INDEX.keys[i]]
                failed = [c for c in metrics_list[i].core_failed]
                if i in rank:
                    # 13 — bağımsız denetim; 14 — karar kaydı
                    audit = audit_candidate(self.conn, cid, self.s)
                    if audit.passed:
                        self._record_decision(cid, audit.audit_id, mode, cfg, run_id, fid, i, a_ids, b_ids,
                                              snap, odds, p_est, p_void, ev, cutoff, as_of, rank[i], e, unc_q=pq)
                        n_sel += 1
                        continue
                    failed = ["audit"]
                    detail = "; ".join(audit.failed_details())
                else:
                    detail = ""
                mode_failed = [c.code for c in e.checks if not c.passed]
                if e.eligible and i in nested_block:
                    mode_failed.append("nested_duplicate")
                all_failed = failed + [c for c in mode_failed if c not in failed]
                primary = all_failed[0] if all_failed else "not_ranked"
                rej_rows.append({
                    "candidate_id": cid, "run_id": run_id, "selection_mode": mode, "fixture_id": fid,
                    "market_id": INDEX.keys[i][0], "outcome_id": INDEX.keys[i][1],
                    "failed_checks": dumps(all_failed), "primary_reason_code": primary,
                    "reason_text": detail or "", "created_at": as_of,
                })
            insert_many(self.conn, "selection_mode_rejections", rej_rows)
            out[mode] = (n_sel, sum(1 for e in evals if e.eligible))
        return out

    def _calibrate(self, maps: dict[str, CalibrationMap], p: np.ndarray,
                   rng: np.random.Generator | None = None) -> tuple[np.ndarray, np.ndarray]:
        """(S × K) ham olasılık → kalibre + tutarlı (S × K) kazanma, iade."""
        out = np.full_like(p, np.nan)
        for fam in INDEX.families:
            if fam in CAL_FAMILY:
                continue
            idx = np.where(INDEX.family == fam)[0]
            cm = maps.get(fam)
            block = p[:, idx]
            if cm is None or not np.isfinite(block).all():
                continue
            out[:, idx] = cm.apply_with_param_draws(block, rng) if rng is not None else cm.apply(block)
        return enforce_consistency(out)

    def _rules(self) -> set[tuple[str, str]]:
        if not hasattr(self, "_rules_cache"):
            self._rules_cache = {(r[0], r[1]) for r in self.conn.execute(
                "SELECT market_id, source FROM market_settlement_rules WHERE verified=1")}
        return self._rules_cache

    def _store_predictions(self, run_id, fid, cutoff, now_iso, pa, pav, ca, pq_a, pb, pbv, cb, pq_b, models,
                           a_version_id, cal_ids) -> tuple[list[int], list[int | None]]:
        rows_a, rows_b = [], []
        for i, (mid, oid) in enumerate(INDEX.keys):
            fam = cal_family(str(INDEX.family[i]))
            lo_a = _f(np.quantile(pq_a[:, i], 0.1)) if np.isfinite(pq_a[:, i]).all() else None
            hi_a = _f(np.quantile(pq_a[:, i], 0.9)) if np.isfinite(pq_a[:, i]).all() else None
            rows_a.append({
                "run_id": run_id, "model": "A", "model_version_id": a_version_id, "fixture_id": fid,
                "market_id": mid, "outcome_id": oid, "raw_probability": float(np.clip(pa[i], 0, 1)),
                "calibrated_probability": _f(ca[i]), "probability_lower": lo_a, "probability_upper": hi_a,
                "void_probability": float(pav[i]), "calibration_id": cal_ids.get(("A", fam)),
                "data_cutoff_at": cutoff, "created_at": now_iso, "uses_odds": 0, "is_oof": 1,
                "input_manifest": MODEL_A_INPUTS,
            })
            if np.isfinite(pb[i]):
                lo_b = _f(np.quantile(pq_b[:, i], 0.1)) if np.isfinite(pq_b[:, i]).all() else None
                hi_b = _f(np.quantile(pq_b[:, i], 0.9)) if np.isfinite(pq_b[:, i]).all() else None
                rows_b.append({
                    "run_id": run_id, "model": "B",
                    "model_version_id": models.model_b_version_ids.get(fam),
                    "fixture_id": fid, "market_id": mid, "outcome_id": oid,
                    "raw_probability": float(np.clip(pb[i], 0, 1)), "calibrated_probability": _f(cb[i]),
                    "probability_lower": lo_b, "probability_upper": hi_b, "void_probability": float(pbv[i]),
                    "calibration_id": cal_ids.get(("B", fam)), "data_cutoff_at": cutoff, "created_at": now_iso,
                    "uses_odds": 1, "is_oof": 1, "input_manifest": MODEL_B_INPUTS,
                })
        insert_many(self.conn, "predictions", rows_a)
        insert_many(self.conn, "predictions", rows_b)
        ids = {(r[0], r[1], r[2]): r[3] for r in self.conn.execute(
            "SELECT model, market_id, outcome_id, id FROM predictions WHERE run_id=? AND fixture_id=?", (run_id, fid))}
        a_ids = [ids[("A", m, o)] for m, o in INDEX.keys]
        b_ids = [ids.get(("B", m, o)) for m, o in INDEX.keys]
        return a_ids, b_ids

    def _record_decision(self, cid, audit_id, mode, cfg: ModeConfig, run_id, fid, i, a_ids, b_ids, snap, odds,
                         p_est, p_void, ev, cutoff, as_of, rank, e, unc_q) -> None:
        mid, oid = INDEX.keys[i]
        exists = self.conn.execute(
            "SELECT id FROM selection_mode_decisions WHERE selection_mode=? AND fixture_id=? AND market_id=?"
            " AND outcome_id=?", (mode, fid, mid, oid)).fetchone()
        if exists:
            return  # aynı mod aynı seçimi daha önce kaydetti; performansta iki kez sayılmaz
        insert(self.conn, "selection_mode_decisions", {
            "candidate_id": cid, "audit_id": audit_id, "run_id": run_id, "config_id": cfg.db_id,
            "fixture_id": fid, "market_id": mid, "outcome_id": oid,
            "model_a_prediction_id": a_ids[i], "model_b_prediction_id": b_ids[i], "selection_mode": mode,
            "selection_timestamp": as_of, "odds_snapshot_id": int(snap[i]) if snap[i] else None,
            "decimal_odds": _f(odds[i]), "data_cutoff_at": cutoff, "estimated_probability": float(p_est[i]),
            "void_probability": float(p_void[i] or 0.0), "estimated_ev": _f(ev[i]),
            "uncertainty_metrics": dumps({"q": {f"{qq:.1f}": _f(unc_q[j, i]) for j, qq in enumerate(QUANTILES)},
                                          "p_low": e.p_low, "ev_low": e.ev_low, "p_low_rob": e.p_low_robust,
                                          "ev_low_rob": e.ev_low_robust}),
            "audit_status": "passed",
            "decision_reason": dumps({"checks": [c.pack(with_detail=False) for c in e.checks],
                                      "thresholds": cfg.thresholds.as_api() if cfg.thresholds else None,
                                      "rank_score": e.rank_score}),
            "rank": rank,
        })


def _f(x) -> float | None:
    try:
        v = float(x)
    except (TypeError, ValueError):
        return None
    return v if math.isfinite(v) else None


def _r(x, nd: int = 5):
    v = _f(x)
    return None if v is None else round(v, nd)


def _reason_text(code: str) -> str:
    from .checks import CHECK_LABELS

    return f"Sağlanmayan koşul: {CHECK_LABELS.get(code, code)}."


def _config_from_row(row: sqlite3.Row) -> ModeConfig:
    th = loads(row["thresholds"], {})
    thresholds = ModeThresholds(**th) if row["status"] == "established" and th else None
    return ModeConfig(row["mode"], row["status"], thresholds, row["data_cutoff_at"], row["validation_start"],
                      row["validation_end"], loads(row["validation_metrics"], {}), row["id"])


def settle_fixture_results(conn: sqlite3.Connection, history: History, as_of: str) -> int:
    """Sonucu `as_of` anında bilinen maçların kararlarını sonuçlandırır ve geçmişe sonucu işler."""
    from ..domain.markets import settle

    rows = conn.execute(
        "SELECT d.id, d.market_id, d.outcome_id, d.decimal_odds, f.status, f.ft_home, f.ft_away, f.ht_home,"
        " f.ht_away, f.result_recorded_at FROM selection_mode_decisions d JOIN fixtures f ON f.id=d.fixture_id"
        " WHERE d.settlement_status='pending' AND f.result_recorded_at IS NOT NULL AND f.result_recorded_at<=?",
        (as_of,),
    ).fetchall()
    n = 0
    with transaction(conn):
        for r in rows:
            res = MatchResult(r["status"], r["ft_home"], r["ft_away"], r["ht_home"], r["ht_away"])
            s = settle(r["market_id"], r["outcome_id"], res)
            if s == "unresolved":
                continue
            o = r["decimal_odds"]
            profit = (o - 1.0 if s == "won" else -1.0 if s == "lost" else 0.0) if o else None
            conn.execute("UPDATE selection_mode_decisions SET settlement_status=?, settled_at=?, profit_units=?"
                         " WHERE id=?", (s, r["result_recorded_at"], profit, r["id"]))
            n += 1
    for fid, rec in history.records.items():
        if rec.y is None:
            f = conn.execute("SELECT status, ft_home, ft_away, ht_home, ht_away, result_recorded_at FROM fixtures"
                             " WHERE id=?", (fid,)).fetchone()
            if f is not None and f["result_recorded_at"] and f["result_recorded_at"] <= as_of:
                history.set_outcome(fid, MatchResult(f["status"], f["ft_home"], f["ft_away"], f["ht_home"],
                                                     f["ht_away"]), f["result_recorded_at"])
    return n
