"""Canlı Gol Radar testleri (%90 eşik, zorunlu şartlar, MODEL A oran izolasyonu, mod bağımsızlığı).

Hızlı olması için tek ligli, tek geçmiş sezonlu küçük bir sentetik veritabanı modül başına bir kez kurulur;
her test bu dosyanın kopyası üzerinde çalışır. Şartların tek tek sınanabilmesi için olasılık fonksiyonları
ve nadir olay kalibrasyonu gerektiğinde `monkeypatch` ile sabitlenir; diğer bütün şartlar (kalan süre,
canlı veri ve oran güncelliği, Kalite Kapısı, bildirim izni, mükerrer alarm engeli) gerçek kodla sınanır.
"""
from __future__ import annotations

import inspect
import math
import shutil
import sqlite3
from pathlib import Path

import numpy as np
import pytest

from akilli_tahmin.config import SETTINGS
from akilli_tahmin.data.synthetic import generate_demo
from akilli_tahmin.data.synthetic_live import generate_goal_events, historical_snapshots
from akilli_tahmin.db.database import MODE_CODES, connect, init_db, insert, iso_add
from akilli_tahmin.models.isolation import OddsIsolationError, assert_football_only
from akilli_tahmin.radar import live_goal_radar as R

AS_OF = "2026-11-21T17:00:00Z"
NOW = AS_OF
DAY_CUTOFF = "2026-11-21T00:00:00Z"
ITEM_KEYS = {"fixture", "minute", "score", "remaining_minutes", "model_a_probability", "model_b_probability",
             "odds", "conditions", "alarm", "alert_id", "blocked_reason"}


# ───────────────────────── düzen ─────────────────────────

@pytest.fixture(scope="module")
def radar_db_path(tmp_path_factory) -> Path:
    path = tmp_path_factory.mktemp("radar") / "radar.sqlite3"
    conn = connect(path)
    init_db(conn, AS_OF)
    generate_demo(conn, AS_OF, leagues=("tr-super-lig",), history_seasons=1)
    generate_goal_events(conn, AS_OF)
    conn.close()
    return path


@pytest.fixture()
def conn(radar_db_path, tmp_path):
    R.clear_cache()
    dst = tmp_path / "radar.sqlite3"
    shutil.copy(radar_db_path, dst)
    for ext in ("-wal", "-shm"):
        src = Path(str(radar_db_path) + ext)
        if src.exists():
            shutil.copy(src, str(dst) + ext)
    c = connect(dst)
    yield c
    c.close()
    R.clear_cache()


@pytest.fixture()
def iconn(conn):
    """Demo canlı maçları devre dışı: yalnızca testin kurduğu canlı maçlar değerlendirilir."""
    conn.execute("UPDATE fixtures SET status='postponed' WHERE status='live'")
    return conn


def _teams(conn) -> list[int]:
    return [r[0] for r in conn.execute("SELECT id FROM teams WHERE league_id='tr-super-lig' ORDER BY id")]


def add_live_fixture(conn, *, now: str = NOW, minute: int = 30, score: tuple[int, int] = (1, 0),
                     state_age: int = 10, odds_age: int = 10, odds: float | None = 1.05, market: str | None = None,
                     source: str = "synthetic-demo", precision: str = "exact", odds_minute: int | None = None,
                     xg: tuple[float, float] = (0.8, 0.6), reds: tuple[int, int] = (0, 0)) -> int:
    teams = _teams(conn)
    fid = insert(conn, "fixtures", {
        "league_id": "tr-kupa", "season": "2026", "round": 1, "home_team_id": teams[8], "away_team_id": teams[9],
        "kickoff_at": iso_add(now, minutes=-minute), "status": "live", "ft_home": None, "ft_away": None,
        "ht_home": None, "ht_away": None, "result_recorded_at": None, "source": "synthetic", "is_synthetic": 1,
        "created_at": iso_add(now, days=-10),
    })
    add_state(conn, fid, now=now, minute=minute, score=score, age=state_age, xg=xg, reds=reds)
    if odds is not None:
        add_odds(conn, fid, now=now, minute=odds_minute if odds_minute is not None else minute, score=score,
                 age=odds_age, price=odds, market=market, source=source, precision=precision)
    return fid


def add_state(conn, fid: int, *, now: str = NOW, minute: int, score: tuple[int, int], age: int = 10,
              xg: tuple[float, float] = (0.8, 0.6), reds: tuple[int, int] = (0, 0)) -> int:
    return insert(conn, "live_states", {
        "fixture_id": fid, "minute": minute, "home_goals": score[0], "away_goals": score[1],
        "home_xg": xg[0], "away_xg": xg[1], "home_shots": 6, "away_shots": 4, "home_red": reds[0],
        "away_red": reds[1], "captured_at": iso_add(now, seconds=-age), "source": "synthetic",
    })


def add_odds(conn, fid: int, *, now: str = NOW, minute: int, score: tuple[int, int], age: int = 10,
             price: float = 1.05, market: str | None = None, source: str = "synthetic-demo",
             precision: str = "exact") -> int:
    cap = iso_add(now, seconds=-age)
    return insert(conn, "odds_snapshots", {
        "fixture_id": fid, "market_id": market or f"OU_{score[0] + score[1] + 0.5}", "outcome_id": "OVER",
        "decimal_odds": price, "source": source, "captured_at": cap, "is_live": 1, "minute": minute,
        "timestamp_precision": precision, "ingested_at": cap,
    })


def grant(conn, user: str = "local", granted: bool = True) -> dict:
    return R.set_notification_permission(conn, user, granted, NOW)


def item_for(result: dict, fid: int) -> dict:
    return next(it for it in result["items"] if it["fixture"]["id"] == fid)


def cond(item: dict, code: str) -> dict:
    return next(c for c in item["conditions"] if c["code"] == code)


def failed_codes(item: dict) -> list[str]:
    return [c["code"] for c in item["conditions"] if not c["passed"]]


def _calibration(cutoff: str, *, a_ok: bool = True, b_ok: bool = True) -> dict:
    def mk(model: str, ok: bool) -> R.RareEventCalibration:
        return R.RareEventCalibration(model, 0.90, 600 if ok else 40, 0.93, 0.93 if ok else 0.80, 0.91, 0.95, ok,
                                      "Yeterli" if ok else "Örneklem yetersiz (40 < 150)", cutoff)
    return {"A": mk("A", a_ok), "B": mk("B", b_ok)}


def set_probs(monkeypatch, p_a: float, p_b: float) -> None:
    monkeypatch.setattr(R, "model_a_live_probability", lambda *a, **k: p_a)
    monkeypatch.setattr(R, "model_b_live_probability",
                        lambda fit, pa, odds: p_b if (fit is not None and pa is not None and odds is not None) else None)


@pytest.fixture()
def passing(monkeypatch):
    """İki model %95 ve nadir olay kalibrasyonu yeterli; diğer şartlar gerçek koddan gelir."""
    set_probs(monkeypatch, 0.95, 0.95)
    monkeypatch.setattr(R, "rare_event_calibration", lambda conn, cutoff, settings=SETTINGS: _calibration(cutoff))
    return monkeypatch


# ───────────────────────── eşikler ─────────────────────────

def test_radar_config_thresholds_are_exactly_90_percent():
    rp = SETTINGS.radar
    assert rp.model_a_min_probability == 0.90 and rp.model_b_min_probability == 0.90
    assert rp.min_remaining_minutes == 15 and rp.live_data_max_age_seconds == 60
    assert rp.live_odds_max_age_seconds == 60 and rp.min_rare_event_samples == 150 and rp.duplicate_block
    cfg = R.radar_config()
    assert cfg == {
        "target": "Kalan normal sürede en az bir gol",
        "model_a_min_probability": 0.90,
        "model_b_min_probability": 0.90,
        "min_remaining_minutes": 15,
        "live_data_max_age_seconds": 60,
        "live_odds_max_age_seconds": 60,
        "requires_rare_event_calibration": True,
        "requires_quality_gate": True,
        "requires_notification_permission": True,
        "duplicate_alarm_block": True,
        "note": "Eşik kullanıcı talebiyle %99'dan %90'a indirildi (v6.3). Seçim modları bu eşiği değiştiremez.",
    }
    assert R.RADAR_TARGET == cfg["target"]
    labels = R.condition_labels()
    assert labels["model_a_threshold"] == "MODEL A > %90" and labels["model_b_threshold"] == "MODEL B > %90"
    assert tuple(labels) == R.CONDITION_CODES


def test_probability_of_exactly_90_percent_does_not_alarm(iconn, passing):
    grant(iconn)
    f_a = add_live_fixture(iconn)
    set_probs(passing, 0.90, 0.95)
    it = item_for(R.evaluate_live(iconn, NOW), f_a)
    assert it["alarm"] is False and it["alert_id"] is None
    assert failed_codes(it) == ["model_a_threshold"]
    assert it["blocked_reason"] == "Sağlanmayan koşul: MODEL A > %90"
    assert "kesin büyüktür" in cond(it, "model_a_threshold")["detail"]

    f_b = add_live_fixture(iconn, score=(0, 1))
    set_probs(passing, 0.95, 0.90)
    it = item_for(R.evaluate_live(iconn, iso_add(NOW, seconds=1)), f_b)
    assert it["alarm"] is False and failed_codes(it) == ["model_b_threshold"]
    assert it["blocked_reason"] == "Sağlanmayan koşul: MODEL B > %90"

    f_c = add_live_fixture(iconn, score=(2, 2))
    set_probs(passing, 0.9000001, 0.9000001)
    it = item_for(R.evaluate_live(iconn, iso_add(NOW, seconds=2)), f_c)
    assert it["alarm"] is True and it["blocked_reason"] is None and isinstance(it["alert_id"], int)


def test_less_than_15_minutes_remaining_blocks(iconn, passing):
    grant(iconn)
    f75 = add_live_fixture(iconn, minute=75)
    f76 = add_live_fixture(iconn, minute=76, score=(0, 0))
    res = R.evaluate_live(iconn, NOW)
    ok, late = item_for(res, f75), item_for(res, f76)
    assert ok["remaining_minutes"] == 15 and ok["alarm"] is True
    assert late["remaining_minutes"] == 14 and late["alarm"] is False
    assert failed_codes(late) == ["remaining_time"]
    assert late["blocked_reason"] == "Sağlanmayan koşul: ≥ 15 dk kalan normal süre"


def test_stale_live_data_blocks(iconn, passing):
    grant(iconn)
    fresh = add_live_fixture(iconn, state_age=60)
    stale = add_live_fixture(iconn, state_age=61, score=(0, 0))
    res = R.evaluate_live(iconn, NOW)
    assert item_for(res, fresh)["alarm"] is True
    it = item_for(res, stale)
    assert it["alarm"] is False and failed_codes(it) == ["live_data_fresh"]
    assert it["blocked_reason"] == "Sağlanmayan koşul: Güncel canlı veri"


def test_live_state_after_evaluation_time_is_not_used(iconn, passing):
    grant(iconn)
    fid = add_live_fixture(iconn, state_age=120, odds_age=5)
    add_state(iconn, fid, minute=31, score=(1, 0), age=-30)  # değerlendirme anından sonra yakalanmış
    it = item_for(R.evaluate_live(iconn, NOW), fid)
    assert it["minute"] == 30 and "live_data_fresh" in failed_codes(it)


def test_missing_or_stale_live_odds_block(iconn, passing):
    grant(iconn)
    missing = add_live_fixture(iconn, odds=None)
    stale = add_live_fixture(iconn, odds_age=61, score=(0, 0))
    wrong_market = add_live_fixture(iconn, score=(1, 0), market="OU_0.5")
    no_market = add_live_fixture(iconn, score=(5, 1), market="OU_5.5")
    unverified = add_live_fixture(iconn, source="iddaa", score=(0, 2))
    approximate = add_live_fixture(iconn, precision="approximate", score=(1, 1))
    ok = add_live_fixture(iconn, odds_age=60, score=(2, 1))
    # skor değişikliğinden önce fiyatlanmış oran: 27. dk 0-0 iken OU_1.5 fiyatı, 29. dk'da gol (1-0)
    before_goal = add_live_fixture(iconn, minute=29, score=(1, 0), odds=None)
    add_state(iconn, before_goal, minute=27, score=(0, 0), age=40)
    add_odds(iconn, before_goal, minute=27, score=(1, 0), age=35)

    res = R.evaluate_live(iconn, NOW)
    assert item_for(res, ok)["alarm"] is True
    assert item_for(res, ok)["odds"] == {"decimal": 1.05, "source_label": "Sentetik demo bahisçisi", "age_seconds": 60}
    for fid in (missing, stale, wrong_market, no_market, unverified, approximate, before_goal):
        it = item_for(res, fid)
        assert it["alarm"] is False and it["alert_id"] is None, fid
        assert not cond(it, "live_odds_fresh")["passed"], fid
    assert item_for(res, missing)["odds"] is None
    st = item_for(res, stale)
    assert st["odds"]["age_seconds"] == 61 and failed_codes(st) == ["live_odds_fresh"]
    assert st["blocked_reason"] == "Sağlanmayan koşul: Gerekli güncel canlı oran"
    assert "marketi yok" in cond(item_for(res, no_market), "live_odds_fresh")["detail"]
    assert "doğrulanmamış" in cond(item_for(res, unverified), "live_odds_fresh")["detail"]
    assert "yaklaşık" in cond(item_for(res, approximate), "live_odds_fresh")["detail"]
    bg = item_for(res, before_goal)
    assert "skor" in cond(bg, "live_odds_fresh")["detail"]
    assert bg["model_b_probability"] is None  # başka skor durumunun fiyatı MODEL B'ye verilmez


def test_insufficient_rare_event_calibration_blocks(iconn, passing):
    grant(iconn)
    fid = add_live_fixture(iconn)
    passing.setattr(R, "rare_event_calibration",
                    lambda conn, cutoff, settings=SETTINGS: _calibration(cutoff, b_ok=False))
    it = item_for(R.evaluate_live(iconn, NOW), fid)
    assert it["alarm"] is False and failed_codes(it) == ["rare_event_calibration"]
    assert it["blocked_reason"] == "Sağlanmayan koşul: Yeterli nadir olay kalibrasyonu"
    assert iconn.execute("SELECT COUNT(*) FROM radar_alerts").fetchone()[0] == 0


def test_rare_event_summary_rules():
    rng = np.random.default_rng(0)
    kw = dict(threshold=0.90, min_samples=150, data_cutoff_at=DAY_CUTOFF)
    p = rng.uniform(0.91, 0.99, 3000)
    own = np.arange(p.size)  # her anlık durum ayrı maç (bağımsız)
    good = R.rare_event_summary("A", p, rng.random(p.size) < p, own, **kw)
    assert good.adequate and good.n == 3000 and good.n_snapshots == 3000
    assert good.diff_low <= 0.0 <= good.diff_high and good.ci_low <= good.observed_rate <= good.ci_high
    over = R.rare_event_summary("A", p, rng.random(p.size) < p - 0.08, own, **kw)
    assert not over.adequate and over.diff_high < 0 and "Aşırı güvenli" in over.reason
    under = R.rare_event_summary("B", p * 0 + 0.92, rng.random(p.size) < 0.99, own, **kw)
    assert not under.adequate and under.diff_low > 0 and "Yetersiz güvenli" in under.reason
    few = R.rare_event_summary("B", p[:149], np.ones(149), own[:149], **kw)
    assert not few.adequate and few.n == 149 and "yetersiz" in few.reason and "149 < 150 maç" in few.reason
    # bölge kesin büyüktür: tam 0,90 tahminler nadir olay bölgesine girmez
    edge = R.rare_event_summary("A", np.full(500, 0.90), np.ones(500), np.arange(500), **kw)
    assert edge.n == 0 and edge.n_snapshots == 0 and not edge.adequate
    with pytest.raises(ValueError):
        R.rare_event_summary("A", p, np.ones(p.size), own[:10], **kw)


def _clustered(n_matches: int, per_match: int, p: float, n_goal_matches: int) -> tuple[np.ndarray, ...]:
    """Her maçın bütün anlık durumları aynı tahmini ve aynı sonucu paylaşır (tam küme içi ilişki)."""
    groups = np.repeat(np.arange(1000, 1000 + n_matches), per_match)
    y = np.repeat((np.arange(n_matches) < n_goal_matches).astype(float), per_match)
    return np.full(groups.size, p), y, groups


def test_rare_event_sample_minimum_counts_matches_not_snapshots():
    kw = dict(threshold=0.90, min_samples=SETTINGS.radar.min_rare_event_samples, data_cutoff_at=DAY_CUTOFF)
    # 20 maçtan 13'er anlık durum = 260 anlık durum ≥ 150, ama yalnızca 20 bağımsız maç → yetersiz
    p, y, g = _clustered(20, 13, 0.95, 19)
    few = R.rare_event_summary("A", p, y, g, **kw)
    assert few.n == 20 and few.n_snapshots == 260 and not few.adequate
    assert few.reason == "Örneklem yetersiz (20 < 150 maç)"
    assert "n = 20 maç (260 anlık durum)" in few.detail()
    # Aynı oranlar 149 maçta da yetmez, 150 maçta yeter (sınır maç sayısıdır, kesin değil ≥)
    p, y, g = _clustered(149, 13, 0.94, 140)
    assert R.rare_event_summary("A", p, y, g, **kw).n == 149 and not R.rare_event_summary("A", p, y, g, **kw).adequate
    p, y, g = _clustered(150, 1, 0.94, 141)  # gözlenen = tahmin = %94
    ok = R.rare_event_summary("A", p, y, g, **kw)
    assert ok.n == 150 and ok.n_snapshots == 150 and ok.adequate
    # bölgenin dışında kalan anlık durumlar maç sayısına katılmaz
    p2 = np.concatenate([p, np.full(400, 0.85)])
    y2 = np.concatenate([y, np.ones(400)])
    g2 = np.concatenate([g, np.arange(5000, 5400)])
    assert R.rare_event_summary("A", p2, y2, g2, **kw).n == 150


def test_rare_event_clustered_interval_accepts_well_calibrated_correlated_outcomes():
    kw = dict(threshold=0.90, min_samples=150, data_cutoff_at=DAY_CUTOFF)
    # 300 maç × 12 anlık durum, tahmin %95, 279 maçta gol (gözlenen %93): tek maç birimiyle olağan dalgalanma
    p, y, g = _clustered(300, 12, 0.95, 279)
    cl = R.rare_event_summary("A", p, y, g, **kw)
    assert cl.adequate and cl.reason == "Yeterli" and cl.n == 300 and cl.n_snapshots == 3600
    assert cl.observed_rate == pytest.approx(0.93) and cl.mean_predicted == pytest.approx(0.95)
    # elle hesap: SE² = G/(G−1)·Σ_g (Σ_i (r_i − d))² / N², r = y − p, d = gözlenen − tahmin
    d = 0.93 - 0.95
    cluster_sums = np.array([12 * ((1.0 if i < 279 else 0.0) - 0.95 - d) for i in range(300)])
    se = math.sqrt(300 / 299 * float(np.sum(cluster_sums ** 2))) / 3600
    assert cl.diff_low == pytest.approx(d - R.Z95 * se) and cl.diff_high == pytest.approx(d + R.Z95 * se)
    assert cl.diff_low < 0 < cl.diff_high
    # gözlenen oranın maç kümeli aralığı (aynı yöntem, aynı küme toplamları)
    assert cl.ci_low == pytest.approx(0.93 - R.Z95 * se) and cl.ci_high == pytest.approx(0.93 + R.Z95 * se)
    # anlık durumları bağımsız sayan (eski) yaklaşım aynı veriyi yanlışlıkla reddeder
    iid = R.rare_event_summary("A", p, y, np.arange(p.size), **kw)
    assert not iid.adequate and "Aşırı güvenli" in iid.reason
    from akilli_tahmin.calibration.metrics import wilson_interval
    assert 0.95 > wilson_interval(int(y.sum()), y.size)[1]  # Wilson (n = 3600) ortalama tahmini dışlar


def test_rare_event_clustered_rule_has_nominal_acceptance_for_calibrated_model():
    """İyi kalibre model, maç içi güçlü ilişkiyle: kümeli kural ≈ %95 kabul; anlık durum-iid kural çok reddeder."""
    rng = np.random.default_rng(2026)
    kw = dict(threshold=0.90, min_samples=150, data_cutoff_at=DAY_CUTOFF)
    reps, clustered_ok, iid_ok = 300, 0, 0
    for _ in range(reps):
        sizes = rng.integers(8, 14, 200)                      # 200 maç, maç başına 8–13 anlık durum
        p_match = rng.uniform(0.91, 0.99, 200)
        y_match = (rng.random(200) < p_match).astype(float)   # sonuç maç başına tek: tam ilişki
        groups = np.repeat(np.arange(200), sizes)
        p = np.clip(np.repeat(p_match, sizes) + rng.normal(0, 0.005, sizes.sum()), 0.905, 0.995)
        y = np.repeat(y_match, sizes)
        clustered_ok += R.rare_event_summary("A", p, y, groups, **kw).adequate
        iid_ok += R.rare_event_summary("A", p, y, np.arange(p.size), **kw).adequate
    assert clustered_ok / reps >= 0.90
    assert iid_ok / reps <= 0.65


def test_notification_permission_required_then_alarm(iconn, passing):
    fid = add_live_fixture(iconn)
    assert R.notification_permission_granted(iconn, "local") is False  # varsayılan: izin yok
    it = item_for(R.evaluate_live(iconn, NOW), fid)
    assert it["alarm"] is False and failed_codes(it) == ["notification_permission"]
    assert it["blocked_reason"] == "Sağlanmayan koşul: Bildirim izni" and it["alert_id"] is None
    assert R.list_alerts(iconn) == {"items": []}

    assert grant(iconn) == {"user_id": "local", "granted": True}
    t1 = iso_add(NOW, seconds=5)
    it = item_for(R.evaluate_live(iconn, t1), fid)
    assert it["alarm"] is True and it["blocked_reason"] is None and all(c["passed"] for c in it["conditions"])
    assert set(it) == ITEM_KEYS
    alerts = R.list_alerts(iconn)["items"]
    assert alerts == [{"id": it["alert_id"], "fixture_id": fid, "minute": 30, "model_a_probability": 0.95,
                       "model_b_probability": 0.95, "created_at": t1, "notified": True}]
    # başka kullanıcı izin vermediyse onun için alarm yok
    other = item_for(R.evaluate_live(iconn, t1, user_id="ayse"), fid)
    assert not cond(other, "notification_permission")["passed"]
    assert other["blocked_reason"] == "Sağlanmayan koşul: Bildirim izni"
    # izin geri alınınca tekrar engellenir
    assert grant(iconn, granted=False) == {"user_id": "local", "granted": False}
    assert not cond(item_for(R.evaluate_live(iconn, t1), fid), "notification_permission")["passed"]


def test_duplicate_block_prevents_second_alert(iconn, passing):
    grant(iconn)
    fid = add_live_fixture(iconn, minute=30, score=(1, 0))
    first = item_for(R.evaluate_live(iconn, NOW), fid)
    assert first["alarm"] is True and first["alert_id"] is not None
    second = item_for(R.evaluate_live(iconn, iso_add(NOW, seconds=20)), fid)
    assert second["alert_id"] == first["alert_id"]
    assert failed_codes(second) == ["duplicate_block"]
    assert second["blocked_reason"] == (f"Yeni alarm oluşturulmadı: bu maç, hedef ve skor durumu için alarm "
                                        f"#{first['alert_id']} 30. dakikada zaten verildi (mükerrer alarm engeli).")
    assert second["alarm"] is True  # mevcut alarm geçerli; yeni alarm oluşturulmadı
    assert iconn.execute("SELECT COUNT(*) FROM radar_alerts WHERE fixture_id=?", (fid,)).fetchone()[0] == 1
    # veritabanı düzeyinde de tek alarm: aynı anahtar ikinci kez eklenemez
    with pytest.raises(sqlite3.IntegrityError):
        iconn.execute(
            "INSERT INTO radar_alerts(fixture_id, evaluation_id, target, minute, model_a_probability,"
            " model_b_probability, created_at, notified, dedupe_key) SELECT fixture_id, evaluation_id, target,"
            " minute, model_a_probability, model_b_probability, created_at, notified, dedupe_key"
            " FROM radar_alerts WHERE id=?", (first["alert_id"],))
    # skor değişince (yeni durum) yeni alarm verilebilir
    t2 = iso_add(NOW, seconds=60)
    add_state(iconn, fid, now=t2, minute=32, score=(2, 0), age=5)
    add_odds(iconn, fid, now=t2, minute=32, score=(2, 0), age=3)
    third = item_for(R.evaluate_live(iconn, t2), fid)
    assert third["score"] == "2-0" and third["alarm"] is True and third["blocked_reason"] is None
    assert third["alert_id"] != first["alert_id"]
    assert iconn.execute("SELECT COUNT(*) FROM radar_alerts WHERE fixture_id=?", (fid,)).fetchone()[0] == 2
    # her değerlendirme kaydedilir
    assert iconn.execute("SELECT COUNT(*) FROM radar_evaluations WHERE fixture_id=?", (fid,)).fetchone()[0] == 3


def test_blocked_reason_is_a_negative_sentence(iconn, passing):
    """blocked_reason sağlanan şartın adını değil, sağlanmayan şartı bildirir (ui-05)."""
    labels = R.condition_labels()
    fids = {
        "model_a_threshold": add_live_fixture(iconn, score=(0, 0)),
        "remaining_time": add_live_fixture(iconn, minute=80, score=(1, 0)),
        "live_data_fresh": add_live_fixture(iconn, state_age=600, odds_age=5, score=(0, 1)),
        "live_odds_fresh": add_live_fixture(iconn, odds_age=61, score=(1, 1)),
        "notification_permission": add_live_fixture(iconn, score=(2, 0)),
    }
    target = fids["model_a_threshold"]
    passing.setattr(R, "model_a_live_probability",
                    lambda lh, la, st, **kw: 0.5 if st.fixture_id == target else 0.95)
    res = R.evaluate_live(iconn, NOW)
    stored = dict(iconn.execute("SELECT fixture_id, blocked_reason FROM radar_evaluations").fetchall())
    for code, fid in fids.items():
        it = item_for(res, fid)
        assert failed_codes(it)[0] == code, (code, failed_codes(it))
        reason = it["blocked_reason"]
        assert reason == f"Sağlanmayan koşul: {labels[code]}", reason
        assert reason not in labels.values()  # çıplak (olumlu) etiket gerekçe olarak verilmez
        assert stored[fid] == reason
    # bütün şartlar sağlanınca gerekçe yok
    grant(iconn)
    ok = add_live_fixture(iconn, score=(3, 0))
    assert item_for(R.evaluate_live(iconn, iso_add(NOW, seconds=1)), ok)["blocked_reason"] is None


def test_evaluate_live_writes_in_single_short_transaction(iconn, passing):
    grant(iconn)
    a = add_live_fixture(iconn, score=(1, 0))
    b = add_live_fixture(iconn, score=(0, 0), odds=None)
    statements: list[str] = []
    iconn.set_trace_callback(statements.append)
    try:
        res = R.evaluate_live(iconn, NOW)
    finally:
        iconn.set_trace_callback(None)
    assert item_for(res, a)["alarm"] is True and item_for(res, b)["alarm"] is False
    norm = [" ".join(st.split()).upper() for st in statements]
    begins = [i for i, st in enumerate(norm) if st.startswith("BEGIN")]
    commits = [i for i, st in enumerate(norm) if st.startswith("COMMIT")]
    assert len(begins) == 1 and len(commits) == 1 and begins[0] < commits[0], norm
    writes = [i for i, st in enumerate(norm) if st.startswith(("INSERT", "UPDATE", "DELETE", "REPLACE"))]
    assert writes and all(begins[0] < i < commits[0] for i in writes)
    # işlemin içinde yalnızca yazmalar ve küçük anahtar okumaları var: model kurma sorguları işlemden önce
    inside = norm[begins[0] + 1:commits[0]]
    assert not any("FROM FIXTURES" in st or "FROM LIVE_STATES" in st or "FROM ODDS_SNAPSHOTS" in st
                   for st in inside), inside
    assert not any(st.startswith(("SAVEPOINT", "ROLLBACK")) for st in norm)
    # ilk çalıştırmada kalibrasyon satırları da aynı işlemde yazıldı
    assert any("INTO RADAR_CALIBRATION" in norm[i] for i in writes)
    assert iconn.in_transaction is False
    # çağrıdan sonra açık imleç / okuma anlık görüntüsü yok: başka bağlantı WAL'ı tamamen boşaltabilir
    path = iconn.execute("PRAGMA database_list").fetchall()[0][2]
    R.list_alerts(iconn)
    R.rare_event_calibration(iconn, DAY_CUTOFF)
    other = sqlite3.connect(path)
    try:
        busy, _log, _ckpt = other.execute("PRAGMA wal_checkpoint(TRUNCATE)").fetchone()
    finally:
        other.close()
    assert busy == 0


# ───────────────────────── MODEL A oran izolasyonu ─────────────────────────

def test_model_a_live_receives_no_odds(iconn, monkeypatch):
    clean = R.LiveFootballState(1, 30, 0, 0, 0.5, 0.4)
    assert_football_only([clean])
    assert 0.0 < R.model_a_live_probability(1.6, 1.2, clean) < 1.0
    base = {"fixture_id": 1, "minute": 30, "home_goals": 0, "away_goals": 0, "home_xg": 0.5, "away_xg": 0.4}
    for bad in ("odds", "live_odds", "over_price", "implied_probability", "bookmaker_margin"):
        with pytest.raises(OddsIsolationError):
            R.model_a_live_probability(1.6, 1.2, {**base, bad: 1.05})
    # geçmiş anlık durum sözlükleri fiyat alanı taşır; MODEL A'ya doğrudan verilemez
    snap = historical_snapshots(iconn, DAY_CUTOFF)[0]
    assert "over_odds" in snap
    with pytest.raises(OddsIsolationError):
        R.model_a_live_probability(1.6, 1.2, snap)

    # değerlendirmede MODEL A'ya giden kayıt yalnızca futbol alanları taşır
    seen = []
    original = R.model_a_live_probability

    def spy(lam_home, lam_away, state, **kw):
        seen.append(state)
        return original(lam_home, lam_away, state, **kw)

    monkeypatch.setattr(R, "model_a_live_probability", spy)
    fid = add_live_fixture(iconn)
    it = item_for(R.evaluate_live(iconn, NOW), fid)
    assert seen and all(isinstance(s, R.LiveFootballState) for s in seen)
    assert it["model_a_probability"] is not None and 0.0 <= it["model_a_probability"] <= 1.0


def test_model_a_live_formula_and_behaviour():
    st = lambda m, g=(0, 0), xg=(None, None), reds=(0, 0): R.LiveFootballState(1, m, g[0], g[1], xg[0], xg[1], *reds)
    lh, la = 1.7, 1.2
    # κ yok, ilk yarı payı 0,5, xG yok → tam olarak p = 1 − exp(−(λ_ev+λ_dep)·kalan/90)
    for m in (0, 10, 30, 45, 60, 75, 89):
        exp_p = 1 - math.exp(-(lh + la) * (90 - m) / 90)
        assert R.model_a_live_probability(lh, la, st(m)) == pytest.approx(exp_p, abs=1e-12)
    assert R.model_a_live_probability(lh, la, st(90)) == 0.0
    assert R.remaining_share(0, 0.4) == pytest.approx(1.0) and R.remaining_share(45, 0.4) == pytest.approx(0.6)
    ps = [R.model_a_live_probability(lh, la, st(m), ht_share=0.44, dispersion=12.0) for m in range(0, 91, 5)]
    assert all(a > b for a, b in zip(ps, ps[1:])) and ps[-1] == 0.0
    # yüksek canlı xG temposu olasılığı artırır, düşük tempo azaltır
    mid = R.model_a_live_probability(lh, la, st(40, xg=(0.7, 0.6)))
    assert R.model_a_live_probability(lh, la, st(40, xg=(2.0, 1.5))) > mid > R.model_a_live_probability(
        lh, la, st(40, xg=(0.1, 0.05)))
    # ev sahibi kırmızı kart: ev yoğunluğu ×0,70, deplasman ×1,20 → toplamda azalır (λ_ev > λ_dep)
    assert R.model_a_live_probability(lh, la, st(40, reds=(1, 0))) < R.model_a_live_probability(lh, la, st(40))
    # tempo dağılımı (κ) ihtiyatlıdır; atılan goller tempo tahminini yükseltir
    poisson = R.model_a_live_probability(lh, la, st(30))
    disp = R.model_a_live_probability(lh, la, st(30), dispersion=10.0)
    assert disp < poisson
    assert R.model_a_live_probability(lh, la, st(30, g=(2, 1)), dispersion=10.0) > disp


def test_fit_dispersion_recovers_overdispersion():
    rng = np.random.default_rng(3)
    lam = rng.uniform(0.5, 3.5, 20000)
    u = rng.gamma(10.0, 1 / 10.0, lam.size)
    y = (rng.poisson(lam * u) > 0).astype(float)
    k = R.fit_dispersion(lam, y)
    assert 5.0 < k < 25.0
    y_poisson = (rng.poisson(lam) > 0).astype(float)
    assert R.fit_dispersion(lam, y_poisson) > 50.0


# ───────────────────────── mod bağımsızlığı ─────────────────────────

def test_radar_thresholds_unaffected_by_selection_modes(iconn, passing):
    grant(iconn)
    base = R.radar_config()
    for fn in (R.radar_config, R.evaluate_live, R.list_alerts, R.set_notification_permission,
               R.rare_event_calibration, R.live_history):
        assert not any("mode" in p for p in inspect.signature(fn).parameters), fn.__name__
    assert "selection" not in {m.split(".")[1] for m in _imported_modules(R) if m.startswith("akilli_tahmin.")}
    for i, mode in enumerate(MODE_CODES):
        iconn.execute(
            "INSERT INTO user_settings(user_id, default_mode, updated_at) VALUES ('local', ?, ?)"
            " ON CONFLICT(user_id) DO UPDATE SET default_mode=excluded.default_mode", (mode, NOW))
        assert R.radar_config() == base and base["model_a_min_probability"] == 0.90
        hi = add_live_fixture(iconn, score=(i, 0))
        set_probs(passing, 0.905, 0.905)
        it = item_for(R.evaluate_live(iconn, iso_add(NOW, seconds=i)), hi)
        assert cond(it, "model_a_threshold")["passed"] and cond(it, "model_b_threshold")["passed"]
        assert cond(it, "model_a_threshold")["label"] == "MODEL A > %90"
        lo = add_live_fixture(iconn, score=(i, 1))
        set_probs(passing, 0.895, 0.895)
        it = item_for(R.evaluate_live(iconn, iso_add(NOW, seconds=i)), lo)
        assert not cond(it, "model_a_threshold")["passed"] and not cond(it, "model_b_threshold")["passed"]
        assert it["alarm"] is False


def _imported_modules(module) -> set[str]:
    import ast

    tree = ast.parse(inspect.getsource(module))
    out = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom):
            base = module.__package__.split(".")
            mod = ".".join(base[: len(base) - node.level + 1] + ([node.module] if node.module else []))
            out.add(mod if node.level else node.module)
        elif isinstance(node, ast.Import):
            out.update(a.name for a in node.names)
    return out


# ───────────────────────── uçtan uca (gerçek modeller) ─────────────────────────

def test_evaluate_live_end_to_end_on_demo(conn):
    res = R.evaluate_live(conn, NOW)
    assert res["evaluated_at"] == NOW and len(res["items"]) == 4
    for it in res["items"]:
        assert set(it) == ITEM_KEYS
        assert set(it["fixture"]) == {"id", "home", "away", "league"}
        assert it["fixture"]["league"] == {"id": "tr-kupa", "name": "Türkiye Kupası"}
        assert [c["code"] for c in it["conditions"]] == list(R.CONDITION_CODES)
        assert all(set(c) == {"code", "label", "passed", "detail"} and c["detail"] for c in it["conditions"])
        assert 0.0 <= it["model_a_probability"] <= 1.0 and 0.0 <= it["model_b_probability"] <= 1.0
        assert it["odds"]["age_seconds"] == 15 and it["odds"]["decimal"] > 1.0
        assert cond(it, "live_data_fresh")["passed"] and cond(it, "live_odds_fresh")["passed"]
        assert cond(it, "quality_gate")["passed"], cond(it, "quality_gate")["detail"]
        assert not cond(it, "notification_permission")["passed"] and it["alarm"] is False
        assert it["remaining_minutes"] == 90 - it["minute"]
    late = max(res["items"], key=lambda x: x["minute"])
    assert late["minute"] == 83 and not cond(late, "remaining_time")["passed"]
    assert conn.execute("SELECT COUNT(*) FROM radar_evaluations").fetchone()[0] == 4
    cal = conn.execute("SELECT model, region_low, data_cutoff_at, n FROM radar_calibration ORDER BY model").fetchall()
    assert [(r[0], r[1], r[2]) for r in cal] == [("A", 0.90, DAY_CUTOFF), ("B", 0.90, DAY_CUTOFF)]
    assert all(r[3] > 0 for r in cal)
    # aynı gün ikinci değerlendirme: önbellekten, kalibrasyon satırı tekrarlanmaz
    R.evaluate_live(conn, iso_add(NOW, seconds=30))
    assert conn.execute("SELECT COUNT(*) FROM radar_evaluations").fetchone()[0] == 8
    assert conn.execute("SELECT COUNT(*) FROM radar_calibration").fetchone()[0] == 2
    assert R.list_alerts(conn) == {"items": []}


def test_live_history_models_and_cache(conn):
    hist = R.live_history(conn, DAY_CUTOFF)
    assert R.live_history(conn, DAY_CUTOFF) is hist  # kesim günü başına önbellek
    assert hist.model_b is not None and hist.model_b_rows >= SETTINGS.stats.min_model_b_samples
    assert hist.dispersion > 0 and hist.dispersion_rows >= SETTINGS.stats.min_model_b_samples
    cal = R.rare_event_calibration(conn, DAY_CUTOFF)
    assert set(cal) == {"A", "B"} and all(c.n > 0 and c.region_low == 0.90 for c in cal.values())
    # n bağımsız maç sayısıdır (anlık durum sayısı değil); veritabanına da maç sayısı yazılır
    matches = {s["fixture_id"] for s in historical_snapshots(conn, DAY_CUTOFF)}
    for c in cal.values():
        assert c.n <= len(matches) and c.n < c.n_snapshots <= 15 * c.n
        assert "maç" in c.detail() and f"({c.n_snapshots} anlık durum)" in c.detail()
    stored = dict(conn.execute("SELECT model, n FROM radar_calibration WHERE data_cutoff_at=?",
                               (DAY_CUTOFF,)).fetchall())
    assert stored == {"A": cal["A"].n, "B": cal["B"].n}
    # daha erken kesim: daha az geçmiş, ayrı kalibrasyon çalıştırması
    early = R.live_history(conn, "2026-02-01T00:00:00Z")
    assert early.n_snapshots < hist.n_snapshots
    assert conn.execute("SELECT COUNT(DISTINCT data_cutoff_at) FROM radar_calibration").fetchone()[0] == 2


def test_model_b_live_blend():
    assert R.live_market_probability(1.06) == pytest.approx((1 / 1.06) / 1.06)
    assert R.model_b_live_probability(None, 0.95, 1.05) is None
    rng = np.random.default_rng(5)
    pa = rng.uniform(0.3, 0.98, 4000)
    y = (rng.random(pa.size) < pa).astype(float)
    pm = np.clip(pa + rng.normal(0, 0.05, pa.size), 0.05, 0.95)
    assert R.train_live_model_b(pa[:100], pm[:100], y[:100], min_samples=300) is None  # yetersiz örneklem
    fit = R.train_live_model_b(pa, pm, y, min_samples=300)
    assert fit is not None and abs(fit.coef[1] - 1.0) < 0.35 and abs(fit.coef[0]) < 0.5
    p = R.model_b_live_probability(fit, 0.95, 1.04)
    assert 0.0 < p < 1.0


# ───────────────────────── sentetik canlı geçmiş ─────────────────────────

def test_goal_events_consistent_and_idempotent(conn):
    assert generate_goal_events(conn, AS_OF) == 0  # modül düzeninde zaten üretildi
    rows = conn.execute(
        "SELECT f.id, f.ft_home, f.ft_away, f.ht_home + f.ht_away, f.result_recorded_at,"
        " (SELECT COUNT(*) FROM goal_events g WHERE g.fixture_id=f.id AND g.side='home'),"
        " (SELECT COUNT(*) FROM goal_events g WHERE g.fixture_id=f.id AND g.side='away'),"
        " (SELECT COUNT(*) FROM goal_events g WHERE g.fixture_id=f.id AND g.minute<=45),"
        " (SELECT COUNT(*) FROM goal_events g WHERE g.fixture_id=f.id AND g.recorded_at<>f.result_recorded_at)"
        " FROM fixtures f WHERE f.status='finished'").fetchall()
    assert rows
    for fid, fth, fta, ht, _rec, nh, na, first_half, wrong_time in rows:
        assert (nh, na) == (fth, fta), fid
        assert first_half == ht, fid
        assert wrong_time == 0
    before = conn.execute("SELECT fixture_id, minute, side FROM goal_events ORDER BY id").fetchall()
    conn.execute("DELETE FROM goal_events")
    n = generate_goal_events(conn, AS_OF)
    after = conn.execute("SELECT fixture_id, minute, side FROM goal_events ORDER BY id").fetchall()
    assert n == len(before) and [tuple(r) for r in after] == [tuple(r) for r in before]  # deterministik


def test_historical_snapshots_point_in_time_and_deterministic(conn):
    snaps = historical_snapshots(conn, DAY_CUTOFF)
    assert snaps and snaps == historical_snapshots(conn, DAY_CUTOFF)
    goals = {}
    for fid, minute, side in conn.execute("SELECT fixture_id, minute, side FROM goal_events"):
        goals.setdefault(fid, []).append((minute, side))
    for s in snaps:
        ev = goals.get(s["fixture_id"], [])
        m = s["minute"]
        assert s["result_recorded_at"] <= DAY_CUTOFF
        assert s["home_goals"] == sum(1 for gm, sd in ev if gm <= m and sd == "home")
        assert s["away_goals"] == sum(1 for gm, sd in ev if gm <= m and sd == "away")
        assert s["outcome"] == int(any(gm > m for gm, _ in ev))
        assert s["remaining_minutes"] == 90 - m and 5 <= m <= 75
        total = s["home_goals"] + s["away_goals"]
        if total + 0.5 > 5.5:
            assert s["market_id"] is None and s["over_odds"] is None
        else:
            assert s["market_id"] == f"OU_{total + 0.5}"
            assert s["over_odds"] is None or s["over_odds"] >= 1.01
    early = historical_snapshots(conn, "2026-01-01T00:00:00Z")
    assert 0 < len(early) < len(snaps) and all(s["result_recorded_at"] <= "2026-01-01T00:00:00Z" for s in early)
    # aynı maçın anlık durumları kesimden bağımsızdır (yalnızca maç öncesi + o dakikaya kadarki bilgi)
    by_key = {(s["fixture_id"], s["minute"]): s for s in snaps}
    assert all(by_key[(s["fixture_id"], s["minute"])] == s for s in early)


def _finished_cup(conn, *, ft: tuple[int, int], ht: tuple[int, int] | None, goals=(), kickoff="2026-11-20T18:00:00Z"):
    """Sonucu kaydedilmiş sentetik kupa maçı ve (verilirse) gol dakikaları."""
    teams = _teams(conn)
    recorded = iso_add(kickoff, hours=2)
    fid = insert(conn, "fixtures", {
        "league_id": "tr-kupa", "season": "2026", "round": 1, "home_team_id": teams[2], "away_team_id": teams[3],
        "kickoff_at": kickoff, "status": "finished", "ft_home": ft[0], "ft_away": ft[1],
        "ht_home": ht[0] if ht else None, "ht_away": ht[1] if ht else None, "result_recorded_at": recorded,
        "source": "synthetic", "is_synthetic": 1, "created_at": iso_add(kickoff, days=-3),
    })
    for minute, side in goals:
        insert(conn, "goal_events", {"fixture_id": fid, "minute": minute, "side": side, "recorded_at": recorded})
    return fid


def test_historical_snapshots_require_half_time_score_and_goal_events(conn):
    """bugs-4: maç sonuna kadar gözlenmemiş (İY skoru / gol dakikası olmayan) maçlar 'gol yok' sonucu üretmez."""
    before = historical_snapshots(conn, DAY_CUTOFF)
    no_ht_00 = _finished_cup(conn, ft=(0, 0), ht=None)                   # demo kupa bitirişi: İY yok, gol kaydı yok
    no_ht_10 = _finished_cup(conn, ft=(1, 0), ht=None)
    no_events = _finished_cup(conn, ft=(2, 0), ht=(1, 0))                 # İY var, gol dakikası yok
    missing_one = _finished_cup(conn, ft=(2, 0), ht=(1, 0), goals=[(20, "home")])
    wrong_half = _finished_cup(conn, ft=(1, 1), ht=(1, 0), goals=[(60, "home"), (70, "away")])
    ht_gt_ft = _finished_cup(conn, ft=(0, 0), ht=(1, 0))
    real_00 = _finished_cup(conn, ft=(0, 0), ht=(0, 0))                   # gerçek 0-0: gol kaydı olmadan tutarlı
    full = _finished_cup(conn, ft=(2, 1), ht=(1, 0), goals=[(20, "home"), (70, "home"), (80, "away")])
    snaps = historical_snapshots(conn, DAY_CUTOFF)
    by_fid: dict[int, list[dict]] = {}
    for s_ in snaps:
        by_fid.setdefault(s_["fixture_id"], []).append(s_)
    for fid in (no_ht_00, no_ht_10, no_events, missing_one, wrong_half, ht_gt_ft):
        assert fid not in by_fid, fid
    assert [s_["outcome"] for s_ in by_fid[real_00]] == [0] * 15
    f = {s_["minute"]: s_ for s_ in by_fid[full]}
    assert len(f) == 15 and (f[15]["home_goals"], f[15]["away_goals"]) == (0, 0)
    assert (f[20]["home_goals"], f[20]["away_goals"]) == (1, 0)  # 20. dk golü o dakikanın skoruna dahil
    assert (f[75]["home_goals"], f[75]["away_goals"]) == (2, 0) and f[75]["outcome"] == 1
    assert f[5]["outcome"] == 1 and f[20]["outcome"] == 1
    # geçersiz maçlar xG dayanağına da girmez: önceki maçların anlık durumları değişmez
    assert [s_ for s_ in snaps if s_["fixture_id"] not in (real_00, full)] == before
    # radar geçmişi de bu maçları kullanmaz
    R.clear_cache()
    hist = R.live_history(conn, DAY_CUTOFF)
    assert hist.n_snapshots == len(snaps)


def test_demo_cup_fixtures_finished_without_full_record_are_excluded(conn):
    """Demo canlı kupa maçları son canlı skorla bitirildiğinde, tam kaydı olmayanlar radar geçmişine girmez."""
    from akilli_tahmin.data.synthetic import ensure_live_demo

    live = [r[0] for r in conn.execute("SELECT id FROM fixtures WHERE status='live' AND is_synthetic=1 ORDER BY id")]
    assert live
    # Bir maç 0-0 bitsin (son canlı durum 90. dk, 0-0; İY skoru ve gol dakikası yok): eski kod bu maçı
    # 15 adet "gol yok" sonucuyla radar geçmişine alıyordu.
    conn.execute("UPDATE live_states SET home_goals=0, away_goals=0, minute=90 WHERE fixture_id=?", (live[0],))
    later = iso_add(AS_OF, hours=3)  # bütün demo canlı maçları ≥ 88 dk önce başlamış olur
    ensure_live_demo(conn, later)
    cutoff = iso_add(AS_OF, days=1)
    snaps = historical_snapshots(conn, cutoff)
    included = {s_["fixture_id"] for s_ in snaps}
    rows = {r[0]: r for r in conn.execute(
        "SELECT f.id, f.status, f.ht_home, f.ht_away, f.ft_home, f.ft_away FROM fixtures f WHERE f.id IN (%s)"
        % ",".join("?" * len(live)), live)}
    for fid in live:
        _id, status, hth, hta, fth, fta = rows[fid]
        if status != "finished" or hth is None or hta is None:
            assert fid not in included, fid  # gözlenmeyen dakikalar için "gol yok" uydurulmaz
    assert rows[live[0]][1] == "finished" and (rows[live[0]][4], rows[live[0]][5]) == (0, 0)
    # dahil edilen her maçın İY skoru dolu ve gol dakikaları MS/İY ile tutarlı
    ev: dict[int, list[tuple[int, str]]] = {}
    for fid, minute, side in conn.execute("SELECT fixture_id, minute, side FROM goal_events"):
        ev.setdefault(fid, []).append((minute, side))
    meta = {r[0]: r[1:] for r in conn.execute("SELECT id, ht_home, ht_away, ft_home, ft_away FROM fixtures")}
    for fid in included:
        hth, hta, fth, fta = meta[fid]
        assert hth is not None and hta is not None, fid
        goals = ev.get(fid, [])
        for side, ft_, ht_ in (("home", fth, hth), ("away", fta, hta)):
            assert sum(1 for _, sd in goals if sd == side) == ft_, fid
            assert sum(1 for m, sd in goals if sd == side and m <= 45) == ht_, fid

