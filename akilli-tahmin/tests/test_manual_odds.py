"""Oranı elle girme: kullanıcının gördüğü oranla EV / başa baş hesabı."""
from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

NOW = "2026-11-21T17:05:00Z"


@pytest.fixture()
def client(demo_copy_path):
    from akilli_tahmin.api.app import create_app

    return TestClient(create_app(str(demo_copy_path), auto_refresh_demo=False))


def _fixture(client):
    opts = client.get("/api/v1/manual-odds/options").json()
    assert opts["fixtures"], "demo veritabanında yaklaşan maç olmalı"
    assert any(m["id"] == "1X2" for m in opts["markets"])
    return opts["fixtures"][0]["id"]


def test_manual_odds_evaluation_math(demo_conn):
    from akilli_tahmin.api import manual_odds

    fid = demo_conn.execute("SELECT DISTINCT fixture_id FROM candidate_evaluations WHERE run_id ="
                            " (SELECT MAX(id) FROM prediction_runs WHERE kind='prematch')").fetchone()[0]
    before = demo_conn.execute("SELECT COUNT(*) FROM selection_mode_decisions").fetchone()[0]
    res = manual_odds.evaluate(demo_conn, fid, "1X2", {"1": 2.1, "X": "3,4".replace(",", "."), "2": 3.6}, now=NOW)
    assert res["complete_market"] and res["margin"] == pytest.approx(1 / 2.1 + 1 / 3.4 + 1 / 3.6 - 1)
    for o in res["outcomes"]:
        if o["estimated_probability"] is None:
            continue
        p, price, pv = o["estimated_probability"], o["odds"], o["void_probability"]
        assert o["ev"] == pytest.approx(p * price + pv - 1)
        assert o["break_even_odds"] == pytest.approx((1 - pv) / p, abs=1e-3)
        assert o["ev_lower"] <= o["ev"] + 1e-12
        assert {m["code"] for m in o["modes"]} == {"balanced", "best_value", "highest_probability"}
        if o["verdict"] == "value":
            assert o["ev_lower"] > 0 and o["ev_model_a"] > 0 and o["ev_model_b"] > 0
    assert sum(o["fair_probability"] for o in res["outcomes"]) == pytest.approx(1.0)
    # Hesap karar kaydı oluşturmaz; oran 'manual' kaynağıyla, İddaa olarak işaretlenmeden saklanır.
    assert demo_conn.execute("SELECT COUNT(*) FROM selection_mode_decisions").fetchone()[0] == before
    src = demo_conn.execute("SELECT s.is_iddaa, s.source_type, o.timestamp_precision, o.captured_at FROM odds_snapshots o"
                            " JOIN odds_sources s ON s.code=o.source WHERE o.source='manual'").fetchall()
    assert len(src) == 3 and all(r[0] == 0 and r[2] == "exact" and r[3] == NOW for r in src)


def test_manual_odds_model_a_untouched(demo_conn):
    """Elle girilen oran MODEL A olasılığını değiştirmez (yalnızca kayıtlı olasılıklar okunur)."""
    from akilli_tahmin.api import manual_odds

    fid = demo_conn.execute("SELECT DISTINCT fixture_id FROM candidate_evaluations WHERE run_id ="
                            " (SELECT MAX(id) FROM prediction_runs WHERE kind='prematch')").fetchone()[0]
    a = manual_odds.evaluate(demo_conn, fid, "OU_2.5", {"OVER": 1.5}, now=NOW)["outcomes"][0]["model_a"]
    b = manual_odds.evaluate(demo_conn, fid, "OU_2.5", {"OVER": 9.0}, now=NOW)["outcomes"][0]["model_a"]
    assert a == b


def test_manual_odds_api_validation(client):
    fid = _fixture(client)
    ok = client.post("/api/v1/manual-odds", json={"fixture_id": fid, "market_id": "OU_2.5", "odds": {"OVER": 1.9}})
    assert ok.status_code == 200 and ok.json()["complete_market"] is False
    assert "Elle girilen" in ok.json()["source_label"]
    for body in ({"fixture_id": fid, "market_id": "OU_2.5", "odds": {"OVER": 0.5}},
                 {"fixture_id": fid, "market_id": "OU_2.5", "odds": {"YES": 2.0}},
                 {"fixture_id": fid, "market_id": "NOPE", "odds": {"1": 2.0}},
                 {"fixture_id": fid, "market_id": "1X2", "odds": {}},
                 {"fixture_id": 999999, "market_id": "1X2", "odds": {"1": 2.0}}):
        r = client.post("/api/v1/manual-odds", json=body)
        assert r.status_code == 422 and isinstance(r.json()["detail"], str)
    assert client.post("/api/v1/manual-odds", content=b'{"fixture_id": 1}').status_code == 415
    hist = client.get("/api/v1/manual-odds/history").json()["items"]
    assert hist and hist[0]["odds"] == 1.9 and hist[0]["market"]["id"] == "OU_2.5"
