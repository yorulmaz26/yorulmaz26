"""MASTER PROMPT v6.3 — Bölüm 10'daki zorunlu testler (ve ilgili ek kontroller)."""
from __future__ import annotations

import hashlib
import sqlite3

import numpy as np
import pytest

from akilli_tahmin.config import SETTINGS
from akilli_tahmin.db.database import DEFAULT_MODE, MODE_CODES, dumps, insert, loads
from akilli_tahmin.models.isolation import OddsIsolationError, TemporalLeakageError
from akilli_tahmin.models.model_a import MatchRecord, fit_model_a
from akilli_tahmin.models.model_b import TrainingRows, train_model_b
from akilli_tahmin.odds.odds import OddsQuote, validate_quote
from akilli_tahmin.selection.modes import (BALANCED, BEST_VALUE, HIGHEST_PROBABILITY, CandidateMetrics, ModeConfig,
                                           ModeThresholds, evaluate_mode)

AS_OF = "2026-11-21T17:00:00Z"


def _metrics(**kw) -> CandidateMetrics:
    base = dict(core_pass=True, odds_valid=True, p_est=0.80, p_void=0.0,
                p_q=[0.74, 0.76, 0.77, 0.78, 0.80, 0.85], width=0.10, rel_width=0.125, dis=0.10, market_dev=0.10,
                ev_a=0.16, ev_b=0.15, odds=1.45, robust_down=-0.005, core_failed=[])
    base.update(kw)
    return CandidateMetrics(**base)


def _cfg(mode: str, **kw) -> ModeConfig:
    th = dict(level=0.9, max_relative_width=0.30, max_disagreement=0.50, max_market_deviation=0.50,
              min_probability_lower=0.70 if mode != BEST_VALUE else None,
              min_ev_lower=0.0 if mode != HIGHEST_PROBABILITY else None)
    th.update(kw)
    return ModeConfig(mode, "established", ModeThresholds(**th), AS_OF, db_id=1)


def _latest_run(conn):
    return conn.execute("SELECT * FROM prediction_runs WHERE kind='prematch' ORDER BY id DESC LIMIT 1").fetchone()


# ───────────────────────── 1. Varsayılan mod Dengeli ─────────────────────────

def test_default_mode_is_balanced(empty_conn):
    assert DEFAULT_MODE == "balanced"
    rows = empty_conn.execute("SELECT code FROM selection_modes WHERE is_default=1").fetchall()
    assert [r[0] for r in rows] == ["balanced"]
    assert SETTINGS.default_selection_mode == "balanced"
    assert set(MODE_CODES) == {"balanced", "highest_probability", "best_value"}
    # Yalnızca bir varsayılan mod olabilir (kısmi benzersiz dizin).
    with pytest.raises(sqlite3.IntegrityError):
        empty_conn.execute("UPDATE selection_modes SET is_default=1 WHERE code='best_value'")


def test_default_mode_via_api(demo_copy_path):
    from fastapi.testclient import TestClient

    from akilli_tahmin.api.app import create_app

    client = TestClient(create_app(str(demo_copy_path), auto_refresh_demo=False))
    assert client.get("/api/v1/settings").json()["default_mode"] == "balanced"
    modes = client.get("/api/v1/recommendations/modes").json()
    assert modes["default_mode"] == "balanced" and modes["user_default_mode"] == "balanced"
    assert client.get("/api/v1/recommendations").json()["mode"] == "balanced"
    # Kullanıcı tercihi kaydedilebilir ve parametresiz istekte kullanılır.
    assert client.put("/api/v1/settings", json={"default_mode": "best_value"}).status_code == 200
    assert client.get("/api/v1/recommendations").json()["mode"] == "best_value"
    assert client.put("/api/v1/settings", json={"default_mode": "yanlis"}).status_code == 422
    # Başka kullanıcının varsayılanı yine Dengeli.
    assert client.get("/api/v1/settings", headers={"X-User-Id": "baska"}).json()["default_mode"] == "balanced"


# ───────────────────────── 2. Mod değişimi MODEL A/B tahminlerini değiştirmemeli ─────────────────────────

def _prediction_digest(conn) -> str:
    h = hashlib.sha256()
    for row in conn.execute("SELECT id, model, raw_probability, calibrated_probability FROM predictions ORDER BY id"):
        h.update(repr(tuple(row)).encode())
    return h.hexdigest()


def test_mode_switch_does_not_change_model_probabilities(demo_conn):
    from akilli_tahmin.api import service

    before = _prediction_digest(demo_conn)
    per_mode = {}
    for mode in MODE_CODES:
        res = service.recommendations(demo_conn, mode, now="2026-11-21T17:05:00Z")
        per_mode[mode] = {(it["fixture"]["id"], it["market"]["id"], it["outcome"]["id"]):
                          (it["model_a"]["calibrated"], it["model_b"]["calibrated"], it["model_a"]["raw"],
                           it["model_b"]["raw"]) for it in res["items"]}
        service.put_settings(demo_conn, "local", mode)
    assert _prediction_digest(demo_conn) == before
    run = _latest_run(demo_conn)
    # Üç mod aynı moddan bağımsız değerlendirmeyi kullanır.
    rows = demo_conn.execute(
        "SELECT fixture_id, market_id, outcome_id, COUNT(DISTINCT evaluation_id), COUNT(DISTINCT selection_mode)"
        " FROM selection_mode_candidates WHERE run_id=? GROUP BY fixture_id, market_id, outcome_id", (run["id"],)
    ).fetchall()
    assert rows and all(r[3] == 1 and r[4] == 3 for r in rows)
    # Farklı modlarda görünen aynı seçimin MODEL A/B olasılıkları birebir aynıdır.
    common = set.intersection(*(set(v) for v in per_mode.values())) if all(per_mode.values()) else set()
    for key in common:
        vals = {per_mode[m][key] for m in MODE_CODES}
        assert len(vals) == 1


# ───────────────────────── 3. En Güvenilir kör seçim yapmamalı ─────────────────────────

def test_highest_probability_does_not_rank_by_raw_probability():
    cfg = _cfg(HIGHEST_PROBABILITY)
    # A: ham olasılığı daha yüksek ama belirsizliği yüksek → alt sınırı düşük.
    a = _metrics(p_est=0.92, p_q=[0.60, 0.70, 0.75, 0.80, 0.92, 0.97], width=0.37, rel_width=0.40)
    # B: ham olasılığı daha düşük, belirsizliği düşük → alt sınırı yüksek.
    b = _metrics(p_est=0.86, p_q=[0.82, 0.83, 0.84, 0.85, 0.86, 0.88], width=0.06, rel_width=0.07)
    ea, eb = evaluate_mode(HIGHEST_PROBABILITY, a, cfg), evaluate_mode(HIGHEST_PROBABILITY, b, cfg)
    assert not ea.eligible and eb.eligible
    # Belirsizlik kabul edilebilir olsa bile sıralama alt sınıra göre yapılır.
    a2 = _metrics(p_est=0.92, p_q=[0.75, 0.80, 0.82, 0.85, 0.92, 0.96], width=0.21, rel_width=0.23)
    ea2 = evaluate_mode(HIGHEST_PROBABILITY, a2, cfg)
    assert ea2.eligible and eb.rank_score > ea2.rank_score
    # Kalibrasyon kapısından geçmeyen en yüksek olasılık seçilmez.
    a3 = _metrics(p_est=0.99, p_q=[0.97] * 4 + [0.99, 0.995], core_pass=False, core_failed=["calibration_b"])
    assert not evaluate_mode(HIGHEST_PROBABILITY, a3, cfg).eligible


def test_highest_probability_selection_in_run_is_not_raw_argmax(demo_conn):
    run = _latest_run(demo_conn)
    rows = demo_conn.execute(
        "SELECT c.fixture_id, c.selected, e.p_a_raw, e.core_pass FROM selection_mode_candidates c JOIN"
        " candidate_evaluations e ON e.id=c.evaluation_id WHERE c.run_id=? AND c.selection_mode=?",
        (run["id"], HIGHEST_PROBABILITY)).fetchall()
    assert rows
    by_fx: dict[int, list] = {}
    for r in rows:
        by_fx.setdefault(r["fixture_id"], []).append(r)
    # En yüksek ham MODEL A olasılığına sahip seçeneklerin hepsi otomatik seçilmiyor.
    tops = [max(v, key=lambda r: r["p_a_raw"]) for v in by_fx.values()]
    assert any(not t["selected"] for t in tops)
    # Seçilenlerin hepsi Kalite Kapısı'ndan geçmiş.
    assert all(r["core_pass"] for r in rows if r["selected"])


# ───────────────────────── 4. En Avantajlı geçersiz oran kullanmamalı ─────────────────────────

def _quote(**kw) -> OddsQuote:
    base = dict(snapshot_id=1, fixture_id=1, market_id="1X2", outcome_id="1", decimal_odds=2.1, source="x",
                source_label="X", source_type="bookmaker", is_iddaa=False, source_verified=True,
                captured_at="2026-11-21T16:50:00Z", is_live=False, timestamp_precision="exact")
    base.update(kw)
    return OddsQuote(**base)


def test_odds_validation_rules():
    at, max_age = AS_OF, 1800
    assert validate_quote(_quote(), at, max_age).valid
    assert validate_quote(None, at, max_age).code == "odds_missing"
    assert validate_quote(_quote(captured_at="2026-11-21T15:00:00Z"), at, max_age).code == "odds_stale"
    assert validate_quote(_quote(captured_at="2026-11-21T17:00:01Z"), at, max_age).code == "odds_from_future"
    assert validate_quote(_quote(source_verified=False), at, max_age).code == "odds_source_unverified"
    assert validate_quote(_quote(timestamp_precision="approximate"), at, max_age).code == "odds_timestamp_approximate"
    assert validate_quote(_quote(is_live=True), at, max_age).code == "odds_wrong_phase"
    assert validate_quote(_quote(decimal_odds=1.0), at, max_age).code == "odds_invalid_value"


def test_best_value_rejects_invalid_odds():
    cfg = _cfg(BEST_VALUE)
    good = _metrics(p_est=0.55, p_q=[0.52, 0.53, 0.54, 0.545, 0.55, 0.58], odds=2.1, ev_a=0.16, ev_b=0.15,
                    width=0.06, rel_width=0.11)
    assert evaluate_mode(BEST_VALUE, good, cfg).eligible
    bad = _metrics(**{**good.__dict__, "odds_valid": False, "odds": None, "ev_a": None, "ev_b": None})
    ev = evaluate_mode(BEST_VALUE, bad, cfg)
    assert not ev.eligible
    assert {c.code for c in ev.checks if not c.passed} >= {"odds_valid", "ev_models_positive", "ev_lower"}


def test_unverified_iddaa_source_never_shown_as_iddaa(empty_conn):
    row = empty_conn.execute("SELECT verified, is_iddaa, source_type FROM odds_sources WHERE code='iddaa'").fetchone()
    assert row["verified"] == 0  # resmi bağlantı yokken İddaa kaynağı doğrulanmış sayılmaz
    with pytest.raises(sqlite3.IntegrityError):
        empty_conn.execute("UPDATE odds_sources SET is_iddaa=1 WHERE code='synthetic-demo'")


def test_selected_odds_are_valid_in_run(demo_conn):
    rows = demo_conn.execute(
        "SELECT d.*, o.captured_at, o.source, s.verified, s.is_iddaa FROM selection_mode_decisions d"
        " JOIN odds_snapshots o ON o.id=d.odds_snapshot_id JOIN odds_sources s ON s.code=o.source"
        " WHERE d.selection_mode IN ('best_value','balanced')").fetchall()
    assert rows
    for r in rows:
        assert r["verified"] == 1 and r["is_iddaa"] == 0
        assert r["captured_at"] <= r["selection_timestamp"]


# ───────────────────────── 5. Dengeli mod pozitif EV olmadan seçim üretmemeli ─────────────────────────

def test_balanced_requires_positive_ev():
    cfg = _cfg(BALANCED)
    ok = _metrics()
    assert evaluate_mode(BALANCED, ok, cfg).eligible
    # p_alt = 0,735 → oran 1,20'de EV alt sınırı negatif; modellerden biri negatif EV verirse de seçilmez.
    for change in ({"ev_b": -0.01}, {"ev_a": -0.02}, {"odds": 1.20}, {"odds_valid": False}):
        m = _metrics(**change)
        assert not evaluate_mode(BALANCED, m, cfg).eligible, change
    # Eşik negatif olamaz: kronolojik ayar EV eşiğini ≥ 0 üretir.
    from akilli_tahmin.selection.thresholds import tune_mode
    from akilli_tahmin.selection.history import HistoryView

    assert tune_mode(BALANCED, HistoryView(AS_OF, []), SETTINGS.stats, data_cutoff_at=AS_OF).status == "not_established"


def test_balanced_decisions_have_positive_ev(demo_conn):
    """Oranlı modlarda her karar pozitif EV taşır; karar yoksa mod gerekçesiyle kurulmamış olmalıdır."""
    for mode in (BALANCED, BEST_VALUE):
        rows = demo_conn.execute("SELECT estimated_ev, uncertainty_metrics, decision_reason FROM selection_mode_decisions"
                                 " WHERE selection_mode=?", (mode,)).fetchall()
        for r in rows:
            assert r["estimated_ev"] > 0
            assert loads(r["uncertainty_metrics"])["ev_low"] > 0
            assert loads(r["decision_reason"])["thresholds"]["min_ev_lower"] >= 0
        if not rows:
            cfgs = demo_conn.execute("SELECT status, validation_metrics FROM selection_mode_configs WHERE mode=?",
                                     (mode,)).fetchall()
            assert cfgs and all(c["status"] == "not_established" and loads(c["validation_metrics"]).get("reason")
                                for c in cfgs)


def test_no_forced_selection_in_efficient_market(tmp_path):
    """Verimli piyasada güvenilir avantaj yoksa oranlı modlar seçim üretmez (zorla tahmin yok)."""
    from akilli_tahmin.backtest.walk_forward import run_backtest
    from akilli_tahmin.data.synthetic import generate_demo
    from akilli_tahmin.db.database import connect, init_db

    conn = connect(tmp_path / "eff.sqlite3")
    init_db(conn, AS_OF)
    generate_demo(conn, AS_OF, market="efficient", leagues=("tr-super-lig",), history_seasons=2, live_fixtures=False)
    run_backtest(conn, AS_OF, n_draws=60)
    n = conn.execute("SELECT COUNT(*) FROM selection_mode_decisions WHERE selection_mode IN ('balanced','best_value')"
                     ).fetchone()[0]
    assert n == 0
    reasons = [loads(r[0]).get("reason") for r in conn.execute(
        "SELECT validation_metrics FROM selection_mode_configs WHERE mode='balanced'")]
    assert reasons and all(r for r in reasons)


# ───────────────────────── 6. Kalibrasyon yetersizse seçim reddedilmeli ─────────────────────────

def test_calibration_insufficient_rejects():
    from akilli_tahmin.calibration.calibrate import fit_calibration

    rng = np.random.default_rng(0)
    p = rng.uniform(0.2, 0.8, 50)
    y = (rng.uniform(size=50) < p).astype(float)
    cm = fit_calibration("A", "result", p, y, min_samples=300, alpha=0.05)
    assert not cm.adequate and cm.method == "identity"
    # Kötü kalibre edilmiş bir model (gerçek oran p'den çok farklı) doğrulamada reddedilir.
    p2 = rng.uniform(0.05, 0.95, 4000)
    y2 = (rng.uniform(size=4000) < 0.5).astype(float)
    cm2 = fit_calibration("A", "result", p2, y2, min_samples=300, alpha=0.05)
    assert cm2.local_support(0.97, 40)["ok"] is False


def test_calibration_failures_never_selected(demo_conn):
    rows = demo_conn.execute(
        "SELECT c.selected, e.gate_failed FROM selection_mode_candidates c JOIN candidate_evaluations e"
        " ON e.id=c.evaluation_id").fetchall()
    cal_failed = [r for r in rows if any(ch[0] in ("calibration_a", "calibration_b") for ch in loads(r["gate_failed"]))]
    assert cal_failed
    assert all(not r["selected"] for r in cal_failed)
    rej = demo_conn.execute("SELECT COUNT(*) FROM selection_mode_rejections WHERE failed_checks LIKE '%calibration_%'"
                            ).fetchone()[0]
    assert rej > 0


# ───────────────────────── 7. MODEL A oran verisi kullanmamalı ─────────────────────────

def _records(n=80):
    rng = np.random.default_rng(3)
    out = []
    for i in range(n):
        h, a = rng.choice(8, 2, replace=False)
        day = 1 + i // 4
        out.append(MatchRecord(i, "tr-super-lig", int(h), int(a), f"2026-0{1 + day // 28}-{1 + day % 28:02d}T15:00:00Z",
                               int(rng.poisson(1.5)), int(rng.poisson(1.1)), 0, 0,
                               f"2026-0{1 + day // 28}-{1 + day % 28:02d}T17:05:00Z"))
    return out


def test_model_a_rejects_odds_inputs():
    from dataclasses import dataclass

    @dataclass(frozen=True)
    class LeakyRecord(MatchRecord):
        home_odds: float = 2.0

    recs = _records()
    leaky = [LeakyRecord(**r.__dict__) for r in recs]
    with pytest.raises(OddsIsolationError):
        fit_model_a(leaky, "2026-12-31T00:00:00Z", "tr-super-lig", min_matches=10)
    fit = fit_model_a(recs, "2026-12-31T00:00:00Z", "tr-super-lig", min_matches=10)
    assert fit.valid and fit.params_summary()["uses_odds"] is False


def test_model_a_schema_forbids_odds(empty_conn):
    run = insert(empty_conn, "prediction_runs", {"kind": "prematch", "as_of": AS_OF, "data_cutoff_at": AS_OF,
                                                 "started_at": AS_OF})
    team1 = insert(empty_conn, "teams", {"league_id": "tr-super-lig", "name": "A"})
    team2 = insert(empty_conn, "teams", {"league_id": "tr-super-lig", "name": "B"})
    fx = insert(empty_conn, "fixtures", {"league_id": "tr-super-lig", "season": "2026", "home_team_id": team1,
                                         "away_team_id": team2, "kickoff_at": "2026-11-22T17:00:00Z",
                                         "source": "test", "created_at": AS_OF})
    with pytest.raises(sqlite3.IntegrityError):
        insert(empty_conn, "predictions", {"run_id": run, "model": "A", "fixture_id": fx, "market_id": "1X2",
                                           "outcome_id": "1", "raw_probability": 0.5, "data_cutoff_at": AS_OF,
                                           "created_at": AS_OF, "uses_odds": 1, "input_manifest": "results,odds"})
    with pytest.raises(sqlite3.IntegrityError):
        insert(empty_conn, "model_versions", {"model": "A", "version": "x", "params": "{}", "training_rows": 1,
                                              "data_cutoff_at": AS_OF, "uses_odds": 1, "created_at": AS_OF})


def test_model_a_predictions_invariant_to_odds(demo_conn):
    """Oran tablosu tamamen değişse bile MODEL A'nın tahmini aynıdır."""
    from akilli_tahmin.data.point_in_time import FootballDataView

    fx = demo_conn.execute("SELECT * FROM fixtures WHERE status='scheduled' ORDER BY kickoff_at LIMIT 1").fetchone()
    view = FootballDataView(demo_conn)
    fit1 = fit_model_a(view.all_records(fx["league_id"]), AS_OF, fx["league_id"])
    p1, _ = fit1.predict(fx["home_team_id"], fx["away_team_id"])
    demo_conn.execute("DROP TRIGGER trg_odds_no_update")
    demo_conn.execute("UPDATE odds_snapshots SET decimal_odds = decimal_odds * 1.5")
    view2 = FootballDataView(demo_conn)
    fit2 = fit_model_a(view2.all_records(fx["league_id"]), AS_OF, fx["league_id"])
    p2, _ = fit2.predict(fx["home_team_id"], fx["away_team_id"])
    assert np.allclose(p1, p2)
    assert demo_conn.execute("SELECT COUNT(*) FROM predictions WHERE model='A' AND uses_odds<>0").fetchone()[0] == 0


# ───────────────────────── 8. MODEL B zaman sızıntısı içermemeli ─────────────────────────

def _rows(known_at: list[str], feat: list[str]):
    n = len(known_at)
    rng = np.random.default_rng(1)
    return TrainingRows([str(i) for i in range(n)], known_at, feat, rng.uniform(0.2, 0.8, n),
                        rng.uniform(0.2, 0.8, n), np.zeros(n), (rng.uniform(size=n) < 0.5).astype(float),
                        np.array(["1X2"] * n, dtype=object))


def test_model_b_rejects_future_rows():
    cutoff = "2026-11-01T00:00:00Z"
    ok = ["2026-10-01T00:00:00Z"] * 400
    train_model_b({"result": _rows(ok, ok)}, cutoff, min_samples=300)
    with pytest.raises(TemporalLeakageError):
        train_model_b({"result": _rows(ok[:-1] + ["2026-11-02T00:00:00Z"], ok)}, cutoff, min_samples=300)
    with pytest.raises(TemporalLeakageError):
        train_model_b({"result": _rows(ok, ok[:-1] + ["2026-11-05T00:00:00Z"])}, cutoff, min_samples=300)


def test_history_view_is_point_in_time(demo_conn):
    from akilli_tahmin.selection.history import History

    hist = History.from_db(demo_conn)
    cutoff = "2025-12-01T00:00:00Z"
    view = hist.view(cutoff)
    assert view.records
    for r in view.records:
        assert r.known_at <= cutoff and r.data_cutoff_at <= cutoff and r.data_cutoff_at < r.kickoff_at


def test_model_b_unchanged_by_future_data(demo_conn):
    """Kesimden sonraki sonuç/oran değişiklikleri o kesimdeki MODEL B'yi değiştirmez."""
    from akilli_tahmin.selection.engine import Engine

    cutoff = "2026-03-01T12:00:00Z"
    e1 = Engine(demo_conn)
    m1 = e1.prepare(cutoff).model_b
    # Gelecek: kesimden sonra kaydedilmiş sonuçları ve oranları boz.
    demo_conn.execute("DROP TRIGGER trg_odds_no_update")
    demo_conn.execute("UPDATE fixtures SET ft_home = ft_home + 3 WHERE result_recorded_at > ?", (cutoff,))
    demo_conn.execute("UPDATE odds_snapshots SET decimal_odds = decimal_odds + 1 WHERE captured_at > ?", (cutoff,))
    e2 = Engine(demo_conn)
    m2 = e2.prepare(cutoff).model_b
    for fam, f in m1.fits.items():
        g = m2.fits.get(fam)
        assert (f is None) == (g is None)
        if f is not None:
            assert np.allclose(f.coef, g.coef)


# ───────────────────────── 9. Bağımsız denetim sonuçları saklanmalı ─────────────────────────

def test_audit_results_are_stored(demo_conn):
    dec = demo_conn.execute("SELECT d.id, d.audit_id, d.candidate_id, a.status, a.candidate_id AS acid"
                            " FROM selection_mode_decisions d JOIN selection_audits a ON a.id=d.audit_id").fetchall()
    assert dec
    assert all(r["status"] == "passed" and r["acid"] == r["candidate_id"] for r in dec)
    n_sel = demo_conn.execute("SELECT COUNT(*) FROM selection_mode_candidates WHERE selected=1").fetchone()[0]
    n_aud = demo_conn.execute("SELECT COUNT(DISTINCT candidate_id) FROM selection_audits").fetchone()[0]
    assert n_aud == n_sel  # her seçilen aday denetlenmiş ve kaydedilmiş
    a = demo_conn.execute("SELECT checks FROM selection_audits LIMIT 1").fetchone()[0]
    codes = {c[0] for c in loads(a)}
    assert {"model_a_isolation", "chronology", "model_b_temporal", "thresholds", "combination"} <= codes
    with pytest.raises(sqlite3.IntegrityError):
        demo_conn.execute("UPDATE selection_audits SET status='failed'")
    with pytest.raises(sqlite3.IntegrityError):
        demo_conn.execute("DELETE FROM selection_audits")


def test_audit_detects_tampering(demo_conn):
    """Kayıtlar tutarsızsa denetim başarısız olur ve başarısızlık da saklanır."""
    from akilli_tahmin.selection.audit import audit_candidate

    cid = demo_conn.execute("SELECT c.id FROM selection_mode_candidates c JOIN selection_mode_decisions d"
                            " ON d.candidate_id=c.id ORDER BY c.selection_mode='highest_probability' LIMIT 1").fetchone()[0]
    assert audit_candidate(demo_conn, cid, SETTINGS).passed
    demo_conn.execute("DROP TRIGGER trg_evaluations_no_update")
    demo_conn.execute("UPDATE candidate_evaluations SET estimated_probability = estimated_probability + 0.05,"
                      " estimated_ev = estimated_ev + 0.5 WHERE id ="
                      " (SELECT evaluation_id FROM selection_mode_candidates WHERE id=?)", (cid,))
    res = audit_candidate(demo_conn, cid, SETTINGS)
    failed = {c.code for c in res.checks if not c.passed}
    assert not res.passed and "combination" in failed
    row = demo_conn.execute("SELECT status FROM selection_audits WHERE id=?", (res.audit_id,)).fetchone()
    assert row["status"] == "failed"


# ───────────────────────── 10. Üç modun performans kayıtları ayrı tutulmalı ─────────────────────────

def test_mode_records_are_separate(demo_conn):
    counts = dict(demo_conn.execute("SELECT selection_mode, COUNT(*) FROM selection_mode_decisions GROUP BY 1"))
    assert set(counts) <= set(MODE_CODES) and len(counts) >= 2
    dup = demo_conn.execute(
        "SELECT selection_mode, fixture_id, market_id, outcome_id, COUNT(*) FROM selection_mode_decisions"
        " GROUP BY 1,2,3,4 HAVING COUNT(*) > 1").fetchall()
    assert dup == []
    try:
        from akilli_tahmin.performance.tracker import mode_performance
    except ImportError:
        pytest.skip("performans modülü yok")
    perf = {m: mode_performance(demo_conn, m, "all", AS_OF)["overall"]["n_selections"] for m in MODE_CODES}
    for m in MODE_CODES:
        assert perf[m] == counts.get(m, 0)


# ───────────────────────── 11. Geçmiş seçimler geriye dönük değiştirilmemeli ─────────────────────────

def test_past_selections_are_immutable(demo_conn):
    d = demo_conn.execute("SELECT * FROM selection_mode_decisions WHERE settlement_status<>'pending' LIMIT 1").fetchone()
    assert d is not None
    for sql in (
        "UPDATE selection_mode_decisions SET selection_mode='best_value' WHERE id=?",
        "UPDATE selection_mode_decisions SET estimated_probability=0.99 WHERE id=?",
        "UPDATE selection_mode_decisions SET decimal_odds=9.9 WHERE id=?",
        "UPDATE selection_mode_decisions SET settlement_status='won' WHERE id=?",
        "DELETE FROM selection_mode_decisions WHERE id=?",
    ):
        with pytest.raises(sqlite3.IntegrityError):
            demo_conn.execute(sql, (d["id"],))
    for table in ("selection_mode_candidates", "candidate_evaluations", "predictions", "selection_mode_rejections"):
        with pytest.raises(sqlite3.IntegrityError):
            demo_conn.execute(f"DELETE FROM {table} WHERE id=(SELECT MIN(id) FROM {table})")
    with pytest.raises(sqlite3.IntegrityError):
        demo_conn.execute("UPDATE selection_mode_configs SET thresholds='{}'")


def test_pending_decision_settles_once(demo_conn):
    p = demo_conn.execute("SELECT id FROM selection_mode_decisions WHERE settlement_status='pending' LIMIT 1").fetchone()
    assert p is not None
    demo_conn.execute("UPDATE selection_mode_decisions SET settlement_status='won', settled_at=?, profit_units=0.5"
                      " WHERE id=?", (AS_OF, p[0]))
    with pytest.raises(sqlite3.IntegrityError):
        demo_conn.execute("UPDATE selection_mode_decisions SET settlement_status='lost' WHERE id=?", (p[0],))


# ───────────────────────── 12. Canlı Gol Radar eşikleri (%90) korunmalı ─────────────────────────

def test_radar_thresholds_are_90_percent_and_mode_independent(empty_conn):
    assert SETTINGS.radar.model_a_min_probability == 0.90
    assert SETTINGS.radar.model_b_min_probability == 0.90
    assert SETTINGS.radar.min_remaining_minutes == 15
    try:
        from akilli_tahmin.radar.live_goal_radar import radar_config
    except ImportError:
        pytest.skip("radar modülü yok")
    from akilli_tahmin.api import service

    for mode in MODE_CODES:
        service.put_settings(empty_conn, "local", mode)
        cfg = radar_config(SETTINGS)
        assert cfg["model_a_min_probability"] == 0.90 and cfg["model_b_min_probability"] == 0.90
        assert cfg["min_remaining_minutes"] == 15


# ───────────────────────── ek: iki model, üç mod, 21 organizasyon ─────────────────────────

def test_only_two_prediction_models_and_21_organizations(empty_conn):
    from akilli_tahmin.config import PREDICTION_MODELS
    from akilli_tahmin.leagues import LEAGUES

    assert PREDICTION_MODELS == ("A", "B")
    assert len(LEAGUES) == 21
    assert empty_conn.execute("SELECT COUNT(*) FROM leagues").fetchone()[0] == 21
    with pytest.raises(sqlite3.IntegrityError):
        insert(empty_conn, "selection_modes", {"code": "model_c", "name": "x", "short_name": "x", "description": "x",
                                               "requires_odds": 0, "ranking": "x", "sort_order": 9})


def test_market_probabilities_consistent():
    from akilli_tahmin.models.model_b import consistency_report
    from akilli_tahmin.models.score_grid import joint_weights, market_probabilities

    w = joint_weights(np.array([1.6]), np.array([1.1]), 0.45, -0.05)
    pw, pv = market_probabilities(w)
    rep = consistency_report(pw, pv)
    assert rep["ok"], rep


def test_settlement_rules_match_prediction_rules():
    from akilli_tahmin.domain.markets import MatchResult, settle

    r = MatchResult("finished", 2, 1, 1, 1)
    assert settle("1X2", "1", r) == "won"
    assert settle("HTFT", "X/1", r) == "won"
    assert settle("OU_2.5", "OVER", r) == "won"
    assert settle("DNB", "1", MatchResult("finished", 1, 1, 0, 0)) == "void"
    assert settle("BTTS", "YES", r) == "won"
    assert settle("1X2", "1", MatchResult("postponed", None, None)) == "void"
    assert settle("HT_1X2", "X", MatchResult("finished", 2, 1, None, None)) == "unresolved"
