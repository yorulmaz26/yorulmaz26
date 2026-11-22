"""API uçları (docs/API.md sözleşmesi) — v6.3 Bölüm 8."""
from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from akilli_tahmin.db.database import MODE_CODES

CARD_FIELDS = {
    "fixture", "market", "outcome", "odds", "model_a", "model_b", "expected_value", "uncertainty", "robustness",
    "data_freshness", "validity", "history", "reason_summary", "audit",
}


@pytest.fixture()
def client(demo_copy_path):
    from akilli_tahmin.api.app import create_app

    return TestClient(create_app(str(demo_copy_path), auto_refresh_demo=False))


def test_modes_endpoint(client):
    data = client.get("/api/v1/recommendations/modes").json()
    assert data["default_mode"] == "balanced"
    assert [m["code"] for m in data["modes"]] == ["highest_probability", "best_value", "balanced"]
    for m in data["modes"]:
        assert m["thresholds"]["status"] in ("established", "not_established")
        assert m["thresholds"]["source"] == "chronological_validation"


@pytest.mark.parametrize("mode", MODE_CODES)
def test_recommendations_each_mode(client, mode):
    data = client.get(f"/api/v1/recommendations?mode={mode}").json()
    assert data["mode"] == mode and "disclaimer" in data and "garanti" in data["disclaimer"]
    assert set(data["summary"]) == {"fixtures_scanned", "candidates", "eligible", "selected",
                                    "fixtures_without_selection"}
    if not data["items"]:
        assert data["message"]
    for it in data["items"]:
        assert CARD_FIELDS <= set(it)
        assert it["fixture"]["league"]["name"] and it["fixture"]["kickoff_at"]
        # MODEL A ve MODEL B ayrı gösterilir
        assert it["model_a"]["prediction_id"] != it["model_b"]["prediction_id"]
        assert it["audit"]["status"] == "passed"
        if it["odds"] is not None:
            assert it["odds"]["is_iddaa"] is False  # sentetik oranlar İddaa olarak gösterilmez
        if mode in ("best_value", "balanced"):
            assert it["odds"] is not None
            assert it["expected_value"]["point"] > 0 and it["expected_value"]["lower"] > 0
            assert it["expected_value"]["model_a"] > 0 and it["expected_value"]["model_b"] > 0
            assert any("garanti" in w for w in it["warnings"])
        exp = client.get(f"/api/v1/recommendations/{it['id']}/decision-explanation").json()
        assert exp["selected"] is True and all(c["passed"] for c in exp["checks"])
        if mode == "balanced":
            assert len(exp["conditions"]) == 10 and all(c["passed"] for c in exp["conditions"])


def test_balanced_empty_message(client):
    data = client.get("/api/v1/recommendations?mode=balanced&date=1999-01-01").json()
    assert data["items"] == [] and data["message"] == "Güvenilir ve avantajlı seçim bulunamadı."


def test_rejections_endpoints(client):
    data = client.get("/api/v1/recommendations/rejections?mode=balanced&limit=5").json()
    assert data["total"] > 0 and data["reason_counts"] and len(data["items"]) <= 5
    rid = data["items"][0]["id"]
    rej = client.get(f"/api/v1/recommendations/{rid}/rejection-reasons").json()
    assert rej["selected"] is False and rej["failed_checks"]
    assert all(not c["passed"] for c in rej["failed_checks"])
    assert client.get("/api/v1/recommendations/999999999/rejection-reasons").status_code == 404


def test_invalid_mode_is_rejected(client):
    assert client.get("/api/v1/recommendations?mode=model_c").status_code == 422
    assert client.get("/api/v1/recommendations/modes/performance?mode=x").status_code == 422


def test_performance_endpoints(client):
    cmp_ = client.get("/api/v1/recommendations/modes/comparison?period=all").json()
    assert {m["code"] for m in cmp_["modes"]} == set(MODE_CODES)
    pools = {m["metrics"]["n_candidates"] for m in cmp_["modes"]}
    assert len(pools) == 1  # aynı aday havuzu
    assert cmp_["baseline"]["metrics"]["n_selections"] is not None or cmp_["baseline"]["metrics"]
    for period in ("7d", "30d", "90d", "all"):
        assert client.get(f"/api/v1/recommendations/modes/comparison?period={period}").status_code == 200
    perf = client.get("/api/v1/recommendations/modes/performance?mode=balanced&period=all").json()
    assert {"overall", "windows", "by_league", "by_market", "by_season", "season_stability", "timeline"} <= set(perf)
    assert set(perf["windows"]) == {"7d", "30d", "90d"}
    ov = client.get("/api/v1/performance/selection-modes").json()
    assert [m["code"] for m in ov["modes"]] == ["highest_probability", "best_value", "balanced"]
    assert "baseline" in ov and ov["is_synthetic_data"] is True
    assert client.get("/api/v1/recommendations/modes/comparison?period=yanlis").status_code == 422


def test_radar_endpoints(client):
    cfg = client.get("/api/v1/radar/config").json()
    assert cfg["model_a_min_probability"] == 0.90 and cfg["model_b_min_probability"] == 0.90
    assert cfg["min_remaining_minutes"] == 15
    live = client.get("/api/v1/radar/live").json()
    assert "items" in live
    for it in live["items"]:
        codes = {c["code"] for c in it["conditions"]}
        assert {"model_a_threshold", "model_b_threshold", "remaining_time", "notification_permission"} <= codes
        if it["alarm"]:
            assert all(c["passed"] for c in it["conditions"])
    assert client.post("/api/v1/radar/notifications/permission", json={"granted": True}).json()["granted"] is True
    assert "items" in client.get("/api/v1/radar/alerts").json()


def test_misc_endpoints(client):
    assert len(client.get("/api/v1/leagues").json()["items"]) == 21
    st = client.get("/api/v1/system/status").json()
    assert st["prediction_models"] == ["MODEL_A", "MODEL_B"] and st["selection_modes"] == 3
    assert st["default_mode"] == "balanced"
    r = client.get("/")
    assert r.status_code == 200 and "Akıllı" in r.text
    assert client.get("/static/app.js").status_code == 200


def test_concurrent_requests_are_serialized(client):
    """Tek bağlantı paylaşılan iş parçacıkları arasında bozulmaz (bütün erişim kilitle sıralanır)."""
    from concurrent.futures import ThreadPoolExecutor

    urls = ["/api/v1/radar/live", "/api/v1/recommendations/modes", "/api/v1/recommendations/rejections?limit=5",
            "/api/v1/system/status", "/api/v1/radar/alerts", "/api/v1/recommendations?mode=highest_probability"] * 4
    with ThreadPoolExecutor(max_workers=12) as ex:
        codes = list(ex.map(lambda u: client.get(u).status_code, urls))
    assert codes == [200] * len(urls)
    assert client.put("/api/v1/settings", json={"default_mode": "best_value"}).status_code == 200
    assert client.get("/api/v1/settings").json()["default_mode"] == "best_value"


def test_state_changes_require_json_and_same_origin(client):
    r = client.post("/api/v1/radar/notifications/permission", content=b'{"granted": true}')
    assert r.status_code == 415
    r = client.post("/api/v1/radar/notifications/permission", json={"granted": True},
                    headers={"Sec-Fetch-Site": "cross-site"})
    assert r.status_code == 403
    r = client.put("/api/v1/settings", json={"default_mode": "balanced"}, headers={"Origin": "http://evil.example"})
    assert r.status_code == 403


def test_out_of_range_ids_are_rejected(client):
    big = 2**63
    assert client.get(f"/api/v1/recommendations/{big}/decision-explanation").status_code == 422
    assert client.get(f"/api/v1/recommendations/rejections?offset={big}").status_code == 422


def test_rejection_reasons_are_negative_and_paged(client):
    data = client.get("/api/v1/recommendations/rejections?mode=balanced&limit=5&offset=5").json()
    assert data["offset"] == 5 and data["fixtures"]
    assert sum(f["count"] for f in data["fixtures"]) == data["total"]
    for it in data["items"]:
        assert it["reason_text"].startswith("Sağlanmayan koşul:")


def test_comparison_timeline_matches_table(client):
    cmp_ = client.get("/api/v1/recommendations/modes/comparison?period=all").json()
    for m in cmp_["modes"]:
        tl = m.get("timeline") or []
        profit = m["metrics"].get("profit_units")
        if tl and profit is not None:
            assert tl[-1]["cumulative_profit"] == pytest.approx(profit, abs=1e-3)
