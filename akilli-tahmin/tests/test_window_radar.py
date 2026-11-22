"""Son 15 Dakika Radarı (API-Football): olasılık modeli, istek kapıları, bütçe, eşleştirme, bildirim şartları.

Ağ kullanılmaz: API yanıtları, veritabanındaki gerçek maç/takım kayıtlarından kurulan sahte istemciyle verilir.
"""
from __future__ import annotations

import json
import math

import numpy as np
import pytest

from akilli_tahmin.data import api_football as apif
from akilli_tahmin.db.database import from_iso, iso_add, to_iso
from akilli_tahmin.models.isolation import OddsIsolationError
from akilli_tahmin.models.logistic import LogisticFit
from akilli_tahmin.radar import window_radar as W

H1, H2 = W.WINDOWS["H1"], W.WINDOWS["H2"]


# ───────────────────────── yardımcılar ─────────────────────────

class FakeClient:
    """`Client.get` yerine geçer; çağrıları kaydeder."""

    def __init__(self, live: list[dict], odds: list[dict] | None = None):
        self.live, self.odds, self.calls = live, odds or [], []

    def get(self, path, params=None):
        self.calls.append((path, dict(params or {})))
        payload = {"errors": [], "response": self.live if path == "/fixtures" else self.odds}
        return apif.ApiResponse(payload, remaining=90)


def _fixture(conn):
    leagues = tuple(apif.APIF_LEAGUE_IDS)
    row = conn.execute(
        "SELECT f.id, f.league_id, f.kickoff_at, th.name, ta.name FROM fixtures f"
        " JOIN teams th ON th.id=f.home_team_id JOIN teams ta ON ta.id=f.away_team_id"
        " WHERE f.status='scheduled' AND EXISTS (SELECT 1 FROM fixture_evaluations fe WHERE fe.fixture_id=f.id)"
        f" AND f.league_id IN ({','.join('?' * len(leagues))}) ORDER BY f.kickoff_at, f.id LIMIT 1", leagues).fetchone()
    assert row is not None, "demo veritabanında maç öncesi değerlendirmesi olan yaklaşan maç olmalı"
    return {"id": row[0], "league": row[1], "kickoff": row[2], "home": row[3], "away": row[4]}


def _api_time(iso: str) -> str:
    return iso.replace("Z", "+00:00")


def _live_item(fx, *, apif_id=900001, status="2H", minute=76, goals=(1, 1), ht=(1, 0), events=()):
    return {
        "fixture": {"id": apif_id, "date": _api_time(fx["kickoff"]),
                    "status": {"short": status, "elapsed": minute, "extra": None}},
        "league": {"id": apif.APIF_LEAGUE_IDS[fx["league"]], "name": "x"},
        "teams": {"home": {"id": 1, "name": fx["home"].upper() + " FK"}, "away": {"id": 2, "name": fx["away"]}},
        "goals": {"home": goals[0], "away": goals[1]},
        "score": {"halftime": {"home": ht[0], "away": ht[1]}, "fulltime": {"home": None, "away": None}},
        "events": list(events),
    }


def _odds_item(apif_id, updated, line, over, under, market=apif.MARKET_MATCH_GOALS, **status):
    return {"fixture": {"id": apif_id}, "update": _api_time(updated),
            "status": {"stopped": False, "blocked": False, "finished": False, **status},
            "odds": [{"id": market, "name": "x", "values": [
                {"value": "Over", "handicap": line, "odd": str(over), "suspended": False},
                {"value": "Under", "handicap": line, "odd": str(under), "suspended": False}]}]}


def _set_cfg(conn, **kw):
    cfg = dict(W.DEFAULT_CONFIG, **kw)
    conn.execute("INSERT INTO app_settings(key, value, updated_at) VALUES (?,?,?) ON CONFLICT(key) DO UPDATE"
                 " SET value=excluded.value", (W.SETTINGS_KEY, json.dumps(cfg), "2026-01-01T00:00:00Z"))
    conn.execute("UPDATE window_radar_targets SET threshold=-1 WHERE attempts=0 AND status IN ('pending','gated')")


# ───────────────────────── olasılık modeli ─────────────────────────

def test_window_probability_scale_and_monotonicity():
    """15 dakikalık pencerede gol olasılığı tipik maçta %35–%55; %90'a çok yüksek beklentide bile ulaşmaz."""
    p1 = W.prematch_window_probability(1.5, 1.2, 0.44, H1)
    p2 = W.prematch_window_probability(1.5, 1.2, 0.44, H2)
    assert 0.25 < p1 < 0.5 and 0.3 < p2 < 0.55 and p2 > p1
    assert W.prematch_window_probability(3.0, 2.2, 0.44, H2) < 0.9
    base = {"minute": 76, "home_goals": 1, "away_goals": 1}
    pa, rem = W.model_a_window_probability(1.5, 1.2, 0.44, H2, base)
    later, rem_later = W.model_a_window_probability(1.5, 1.2, 0.44, H2, {**base, "minute": 85})
    assert rem == pytest.approx(18.0) and rem_later == pytest.approx(9.0) and later < pa
    # Çok gollü maç (tempo güncellemesi) olasılığı artırır; ilk yarı uzatması bitince pencere kapanır.
    assert W.model_a_window_probability(1.5, 1.2, 0.44, H2, {**base, "home_goals": 4, "away_goals": 3})[0] > pa
    closed, rem0 = W.model_a_window_probability(1.5, 1.2, 0.44, H1, {"minute": 45, "extra_minute": 3,
                                                                    "home_goals": 0, "away_goals": 0})
    assert closed == 0.0 and rem0 == 0.0


def test_model_a_window_rejects_odds_fields():
    with pytest.raises(OddsIsolationError):
        W.model_a_window_probability(1.5, 1.2, 0.44, H2, {"minute": 76, "home_goals": 0, "away_goals": 0,
                                                         "live_odds": 1.8})


def test_reach_probability_matches_simulation():
    """1. kapının 'eşiğe ulaşma şansı', gol sayısı simülasyonuyla aynı sonucu verir."""
    rng = np.random.default_rng(3)
    lh, la, ht, k = 1.9, 1.4, 0.45, W.DEFAULT_DISPERSION
    e = W.expected_goals_before(lh + la, ht, H2.start)
    u = rng.gamma(k, 1 / k, 200_000)
    g = rng.poisson(e * u)
    lam, _ = W.window_intensity(lh, la, ht, H2, H2.start)
    for thr in (0.45, 0.5, 0.55):
        p_live = 1 - np.power(1 + lam / (k + e), -(k + g))
        sim = float((p_live >= thr).mean())
        assert W.reach_probability(lh, la, ht, H2, thr) == pytest.approx(sim, abs=0.01)
    # %90: en gollü maçta bile şans binde birin altında (1. kapı eşiği %1) → istek yok
    assert W.reach_probability(2.6, 2.6, 0.45, H2, 0.90) < 1e-3 < W.DEFAULT_CONFIG["min_reach"]


# ───────────────────────── eşleştirme ve ayrıştırma ─────────────────────────

@pytest.mark.parametrize("ours,theirs", [("Fenerbahce", "Fenerbahçe"), ("Man United", "Manchester United"),
                                         ("Buyuksehyr", "Istanbul Basaksehir"), ("Goztep", "Göztepe"),
                                         ("Nott'm Forest", "Nottingham Forest"), ("Ath Madrid", "Atletico Madrid"),
                                         ("Erzurumspor", "Erzurumspor FK"), ("Paris SG", "Paris Saint Germain")])
def test_team_similarity_matches_known_spellings(ours, theirs):
    assert W.team_similarity(ours, theirs) >= W.MIN_LINK_SIMILARITY


def test_team_similarity_rejects_different_teams():
    assert W.team_similarity("Galatasaray", "Fenerbahçe") < 0.5
    assert W.team_similarity("Man City", "Manchester United") < 0.9


def test_market_probability_parsing():
    now = "2026-11-22T12:00:30Z"
    item = _odds_item(5, "2026-11-22T12:00:10Z", "2.5", 2.2, 1.65)
    res = W.market_probability(item, H2, 2, now, 60)
    assert res["ok"] and res["probability"] == pytest.approx((1 / 2.2) / (1 / 2.2 + 1 / 1.65))
    assert not W.market_probability(item, H2, 1, now, 60)["ok"]  # çizgi 1.5 yok
    assert not W.market_probability(item, H1, 2, now, 60)["ok"]  # ilk yarı marketi yok
    assert not W.market_probability(item, H2, 2, "2026-11-22T12:05:00Z", 60)["ok"]  # eski oran
    stopped = _odds_item(5, "2026-11-22T12:00:10Z", "2.5", 2.2, 1.65, stopped=True)
    assert not W.market_probability(stopped, H2, 2, now, 60)["ok"]


def test_parse_live_item_counts_red_cards():
    fx = {"kickoff": "2026-11-22T12:00:00Z", "league": "tr-super-lig", "home": "A", "away": "B"}
    item = _live_item(fx, events=[{"type": "Card", "detail": "Red Card", "team": {"id": 2}},
                                  {"type": "Card", "detail": "Yellow Card", "team": {"id": 1}}])
    st = W.parse_live_item(item)
    assert (st["home_red"], st["away_red"]) == (0, 1) and W.current_window(st) == "H2"
    assert W.current_window({**st, "status": "1H", "minute": 29}) is None


# ───────────────────────── bütçe ─────────────────────────

def test_budget_reserve_and_limit(demo_conn):
    now = "2026-11-22T10:00:00Z"
    ids = [apif.reserve_request(demo_conn, now, "/x", {}, "test", daily_limit=3, reserve=1) for _ in range(2)]
    assert len(ids) == 2
    with pytest.raises(apif.BudgetExceeded):
        apif.reserve_request(demo_conn, now, "/x", {}, "test", daily_limit=3, reserve=1)
    # Ertesi gün sayaç sıfırlanır; sağlayıcının bildirdiği kalan sayı kendi sayacımızdan azsa o esas alınır.
    rid = apif.reserve_request(demo_conn, "2026-11-23T00:00:05Z", "/x", {}, "test", daily_limit=100, reserve=5)
    apif.finish_request(demo_conn, rid, ok=True, remaining=6)
    st = apif.budget_status(demo_conn, "2026-11-23T00:10:00Z", 100, 5)
    assert st["used"] == 1 and st["remaining"] == 6 and st["usable"] == 1


def test_client_never_records_key():
    seen = {}

    def fetch(url, headers):
        seen.update(headers)
        return 200, {"x-ratelimit-requests-remaining": "41"}, b'{"errors": [], "response": []}'

    res = apif.Client(fetch, key="gizli-anahtar").get("/status")
    assert seen["x-apisports-key"] == "gizli-anahtar" and res.remaining == 41

    def bad(url, headers):
        return 200, {}, b'{"errors": {"token": "Error/Missing application key."}, "response": []}'

    with pytest.raises(apif.ApiFootballError) as exc:
        apif.Client(bad, key="gizli-anahtar").get("/status")
    assert "gizli-anahtar" not in str(exc.value)


# ───────────────────────── planlama ve tik ─────────────────────────

def test_threshold_90_sends_no_requests(demo_conn):
    """Varsayılan %90 eşiğinde hiçbir maç eşiğe ulaşamadığı için istek gönderilmez."""
    fx = _fixture(demo_conn)
    now = iso_add(fx["kickoff"], minutes=H2.nominal_offset + W.BATCH_MINUTES)
    W.plan(demo_conn, iso_add(fx["kickoff"], hours=-1))
    rows = demo_conn.execute("SELECT status, reach_probability FROM window_radar_targets").fetchall()
    assert rows and all(r[0] in ("gated", "no_model") for r in rows)
    assert all(r[1] is None or r[1] < W.DEFAULT_CONFIG["min_reach"] for r in rows)
    client = FakeClient([_live_item(fx)])
    out = W.tick(demo_conn, client, now, clock=lambda: now)
    assert out["requests"] == 0 and client.calls == []
    assert demo_conn.execute("SELECT COUNT(*) FROM api_requests").fetchone()[0] == 0


def test_tick_live_then_odds_only_above_threshold(demo_conn):
    fx = _fixture(demo_conn)
    _set_cfg(demo_conn, threshold=0.30)
    now = iso_add(fx["kickoff"], minutes=H2.nominal_offset + W.BATCH_MINUTES)
    W.plan(demo_conn, now)
    assert demo_conn.execute("SELECT status FROM window_radar_targets WHERE fixture_id=? AND window='H2'",
                             (fx["id"],)).fetchone()[0] == "pending"
    client = FakeClient([_live_item(fx)], [_odds_item(900001, now, "2.5", 2.1, 1.7)])
    out = W.tick(demo_conn, client, now, clock=lambda: now)
    assert [c[0] for c in client.calls] == ["/fixtures", "/odds/live"] and out["requests"] == 2
    assert "-" in client.calls[0][1]["live"] or client.calls[0][1]["live"].isdigit()
    ev = out["evaluations"][0]
    assert ev["fixture_id"] == fx["id"] and ev["window"] == "H2" and ev["model_a_probability"] >= 0.30
    assert ev["market_probability"] == pytest.approx((1 / 2.1) / (1 / 2.1 + 1 / 1.7))
    # MODEL B henüz eğitilmedi → bildirim yok, nedeni açık.
    assert ev["model_b_probability"] is None and not ev["alarm"] and "MODEL B" in ev["blocked_reason"]
    assert demo_conn.execute("SELECT status FROM window_radar_targets WHERE fixture_id=? AND window='H2'",
                             (fx["id"],)).fetchone()[0] == "evaluated"
    reqs = demo_conn.execute("SELECT endpoint, status, params FROM api_requests ORDER BY id").fetchall()
    assert [(r[0], r[1]) for r in reqs] == [("/fixtures", "ok"), ("/odds/live", "ok")]
    assert all("key" not in r[2].lower() for r in reqs)
    link = demo_conn.execute("SELECT fixture_id FROM apif_fixture_links WHERE apif_fixture_id=900001").fetchone()
    assert link[0] == fx["id"]
    # Aynı pencere ikinci kez değerlendirilmez (yeni istek de gönderilmez).
    out2 = W.tick(demo_conn, client, iso_add(now, minutes=1), clock=lambda: iso_add(now, minutes=1))
    assert out2["requests"] == 0


def test_no_odds_request_when_model_a_below_threshold(demo_conn):
    fx = _fixture(demo_conn)
    _set_cfg(demo_conn, threshold=0.85, min_reach=0.0)
    now = iso_add(fx["kickoff"], minutes=H2.nominal_offset + W.BATCH_MINUTES)
    client = FakeClient([_live_item(fx)], [_odds_item(900001, now, "2.5", 2.1, 1.7)])
    out = W.tick(demo_conn, client, now, clock=lambda: now)
    assert [c[0] for c in client.calls] == ["/fixtures"] and out["requests"] == 1
    ev = out["evaluations"][0]
    odds_cond = next(c for c in ev["conditions"] if c["code"] == "live_odds_fresh")
    assert ev["model_a_probability"] < 0.85 and odds_cond["passed"] is None and "istek harcanmadı" in odds_cond["detail"]


def test_alarm_requires_all_conditions_and_is_deduplicated(demo_conn, monkeypatch):
    fx = _fixture(demo_conn)
    _set_cfg(demo_conn, threshold=0.30)
    now = iso_add(fx["kickoff"], minutes=H2.nominal_offset + W.BATCH_MINUTES)
    fit = LogisticFit(coef=np.array([0.0, 1.0, 0.0]), cov=np.eye(3) * 1e-4, n=400, converged=True)
    monkeypatch.setattr(W, "_history", lambda *a, **k: {
        "cutoff": now[:10], "settled": 400, "with_market": 400, "model_b": fit, "model_b_min_samples": 300,
        "cal_a": None, "cal_b": None, "min_rare": 150})
    monkeypatch.setattr(W, "_cal_text", lambda *a, **k: (True, "Yeterli"))
    client = FakeClient([_live_item(fx)], [_odds_item(900001, now, "2.5", 2.1, 1.7)])
    # İzin yokken bildirim verilmez.
    ev = W.tick(demo_conn, client, now, clock=lambda: now)["evaluations"][0]
    assert not ev["alarm"] and "Bildirim izni" in ev["blocked_reason"]
    # İzin verilip yeni bir pencere (H1 değil, başka maç kimliği) değerlendirildiğinde bildirim verilir.
    from akilli_tahmin.radar.live_goal_radar import set_notification_permission

    set_notification_permission(demo_conn, "local", True, now)
    demo_conn.execute("UPDATE window_radar_targets SET status='pending', attempts=0 WHERE fixture_id=? AND"
                      " window='H2'", (fx["id"],))
    ev = W.tick(demo_conn, client, now, clock=lambda: now)["evaluations"][0]
    assert ev["alarm"] and ev["model_b_probability"] >= 0.30
    alerts = W.list_window_alerts(demo_conn)
    assert len(alerts) == 1 and alerts[0]["window"] == "H2" and alerts[0]["fixture"]["home"] == fx["home"]
    # Mükerrer: aynı maç + pencere için ikinci bildirim yok.
    demo_conn.execute("UPDATE window_radar_targets SET status='pending', attempts=0 WHERE fixture_id=? AND"
                      " window='H2'", (fx["id"],))
    out = W.tick(demo_conn, client, now, clock=lambda: now)
    assert not out["evaluations"][0]["alarm"] and len(W.list_window_alerts(demo_conn)) == 1
    assert [c for c in client.calls if c[0] == "/odds/live"].__len__() == 2  # mükerrerde oran istenmez


def test_budget_exhausted_blocks_requests(demo_conn):
    fx = _fixture(demo_conn)
    _set_cfg(demo_conn, threshold=0.30, daily_limit=2, reserve=1)
    now = iso_add(fx["kickoff"], minutes=H2.nominal_offset + W.BATCH_MINUTES)
    apif.reserve_request(demo_conn, now, "/fixtures", {}, "önceki", daily_limit=2, reserve=1)
    client = FakeClient([_live_item(fx)])
    out = W.tick(demo_conn, client, now, clock=lambda: now)
    assert out["requests"] == 0 and client.calls == [] and "bütçe" in out["note"].lower()


def test_settlement_from_live_and_results(demo_conn):
    fx = _fixture(demo_conn)
    _set_cfg(demo_conn, threshold=0.30, min_reach=0.0)
    t1 = iso_add(fx["kickoff"], minutes=H1.nominal_offset + W.BATCH_MINUTES)
    client = FakeClient([_live_item(fx, status="1H", minute=31, goals=(0, 0), ht=(None, None))])
    ev = W.tick(demo_conn, client, t1, clock=lambda: t1)["evaluations"][0]
    assert ev["window"] == "H1"
    # Devre arasında ilk yarı skoru 1-0 → H1 penceresinde gol oldu.
    later = {900001: {"status": "HT", "ht_home": 1, "ht_away": 0, "ft_home": None, "ft_away": None}}
    assert W.settle(demo_conn, iso_add(t1, minutes=20), later) == 1
    row = demo_conn.execute("SELECT outcome, settled_by FROM window_radar_evaluations WHERE id=?", (ev["id"],)).fetchone()
    assert tuple(row) == (1, "api-football")
    with pytest.raises(Exception):  # sonuç bir kez yazılır
        demo_conn.execute("UPDATE window_radar_evaluations SET outcome=0 WHERE id=?", (ev["id"],))


def test_threshold_change_replans_targets(demo_conn):
    fx = _fixture(demo_conn)
    now = iso_add(fx["kickoff"], hours=-1)
    W.plan(demo_conn, now)
    assert demo_conn.execute("SELECT COUNT(*) FROM window_radar_targets WHERE status='pending'").fetchone()[0] == 0
    W.update_config(demo_conn, {"threshold": 0.35}, now)
    assert demo_conn.execute("SELECT COUNT(*) FROM window_radar_targets WHERE status='pending'").fetchone()[0] > 0
    with pytest.raises(ValueError):
        W.update_config(demo_conn, {"threshold": 0.2}, now)
    with pytest.raises(ValueError):
        W.update_config(demo_conn, {"reserve": 100}, now)


# ───────────────────────── API ─────────────────────────

def test_window_radar_api(demo_copy_path):
    from fastapi.testclient import TestClient

    from akilli_tahmin.api.app import create_app

    app = create_app(str(demo_copy_path), auto_refresh_demo=False)
    client = TestClient(app)
    data = client.get("/api/v1/radar/windows").json()
    assert {"config", "budget", "targets", "evaluations", "alerts", "probability_scale", "models"} <= set(data)
    assert data["config"]["threshold"] == 0.90 and data["budget"]["daily_limit"] == 100
    assert client.put("/api/v1/radar/windows/settings", json={"threshold": 0.1}).status_code == 422
    r = client.put("/api/v1/radar/windows/settings", json={"threshold": 0.6, "enabled": False})
    assert r.status_code == 200 and r.json()["threshold"] == 0.6 and r.json()["enabled"] is False
    assert client.put("/api/v1/radar/windows/settings", content=b"{}").status_code == 415
    assert client.get("/api/v1/radar/windows/alerts?after_id=0").json() == {"items": []}
    # Sentetik veritabanında arka plan yoklayıcısı hiç başlamaz.
    assert app.state.runtime.start_window_radar() is False
