"""Tahmin Başarı Merkezi › Seçim Modları performans modülü testleri.

Küçük veritabanları `tmp_path` içinde doğrudan satır eklenerek kurulur (motor çalıştırılmaz); böylece
beklenen değerler elle hesaplanabilir.
"""
from __future__ import annotations

import hashlib
import json
import math
import sqlite3

import pytest

from akilli_tahmin.db.database import MODE_CODES, connect, dumps, init_db, insert, iso_add
from akilli_tahmin.performance import tracker
from akilli_tahmin.performance.tracker import (history_for, metrics_for_decisions, mode_performance,
                                               modes_comparison, selection_modes_overview, snapshot_performance,
                                               window_bounds)
from akilli_tahmin.selection.engine import settle_fixture_results
from akilli_tahmin.selection.history import History

AS_OF = "2026-11-21T00:00:00Z"
Z = 1.959963984540054

OVER, UNDER = ("OU_2.5", "OVER"), ("OU_2.5", "UNDER")
HOME, DRAW, AWAY = ("1X2", "1"), ("1X2", "X"), ("1X2", "2")
DNB_HOME = ("DNB", "1")
KEYS = (OVER, UNDER, HOME, DRAW, AWAY, DNB_HOME)


def wilson(k: int, n: int) -> tuple[float, float]:
    """Bağımsız Wilson %95 aralığı (test içi referans)."""
    ph = k / n
    d = 1 + Z * Z / n
    c = (ph + Z * Z / (2 * n)) / d
    h = Z * math.sqrt(ph * (1 - ph) / n + Z * Z / (4 * n * n)) / d
    return max(0.0, c - h), min(1.0, c + h)


# ───────────────────────── küçük veritabanı kurucu ─────────────────────────

class Builder:
    def __init__(self, path):
        self.conn = connect(path)
        init_db(self.conn, "2024-01-01T00:00:00Z")
        self._teams: dict[tuple[str, str], int] = {}
        #: fixture_id → {key: "won" | "lost" | "void"} (beklenen sonuçlar)
        self.outcomes: dict[int, dict] = {}
        #: değerlendirme satırları (havuz beklentileri için): (fid, at, key, row)
        self.evaluations: list[tuple[int, str, tuple, dict]] = []

    def team(self, league: str, name: str) -> int:
        key = (league, name)
        if key not in self._teams:
            self._teams[key] = insert(self.conn, "teams", {"league_id": league, "name": name})
        return self._teams[key]

    def fixture(self, league: str, season: str, kickoff: str, ft: tuple[int, int], ht: tuple[int, int],
                *, synthetic: int = 1) -> int:
        fid = insert(self.conn, "fixtures", {
            "league_id": league, "season": season, "round": 1,
            "home_team_id": self.team(league, "Ev"), "away_team_id": self.team(league, "Deplasman"),
            "kickoff_at": kickoff, "status": "finished", "ft_home": ft[0], "ft_away": ft[1],
            "ht_home": ht[0], "ht_away": ht[1], "result_recorded_at": iso_add(kickoff, hours=2),
            "source": "test", "is_synthetic": synthetic, "created_at": "2024-01-01T00:00:00Z",
        })
        h, a = ft
        self.outcomes[fid] = {
            OVER: "won" if h + a > 2.5 else "lost", UNDER: "won" if h + a < 2.5 else "lost",
            HOME: "won" if h > a else "lost", DRAW: "won" if h == a else "lost", AWAY: "won" if h < a else "lost",
            DNB_HOME: "void" if h == a else ("won" if h > a else "lost"),
        }
        return fid

    def evaluate(self, fid: int, at: str, rows: dict[tuple, dict], *, eligible: dict[str, set] | None = None,
                 selected: dict[str, set] | None = None) -> int:
        """Bir maç için tek çalıştırma: tahminler, ortak değerlendirme, üç modun adayları ve kararlar."""
        eligible = eligible or {}
        selected = selected or {}
        c = self.conn
        run_id = insert(c, "prediction_runs", {"kind": "backtest", "as_of": at, "data_cutoff_at": at,
                                               "started_at": at, "finished_at": at, "status": "finished",
                                               "is_synthetic": 1})
        for key, r in rows.items():
            mid, oid = key
            p_b, odds, p_void = r["p_b"], r.get("odds"), r.get("p_void", 0.0)
            p_est = r.get("p_est", p_b)
            pred = {"run_id": run_id, "fixture_id": fid, "market_id": mid, "outcome_id": oid,
                    "raw_probability": p_b, "calibrated_probability": p_b, "void_probability": p_void,
                    "data_cutoff_at": at, "created_at": at, "is_oof": 1, "input_manifest": "test"}
            pa = insert(c, "predictions", {**pred, "model": "A", "uses_odds": 0})
            pb = insert(c, "predictions", {**pred, "model": "B", "uses_odds": 1})
            ev_b = None if odds is None else p_b * odds + p_void - 1.0
            ev_est = None if odds is None else p_est * odds + p_void - 1.0
            eid = insert(c, "candidate_evaluations", {
                "run_id": run_id, "fixture_id": fid, "market_id": mid, "outcome_id": oid,
                "model_a_prediction_id": pa, "model_b_prediction_id": pb, "data_cutoff_at": at,
                "evaluated_at": at, "p_a_raw": p_b, "p_b_raw": p_b, "p_a_cal": p_b, "p_b_cal": p_b,
                "p_void": p_void, "estimated_probability": p_est, "decimal_odds": odds, "ev_model_a": ev_b,
                "ev_model_b": ev_b, "estimated_ev": ev_est, "core_pass": r.get("core", 1),
                "odds_valid": r.get("ov", 1), "uncertainty_metrics": "{}", "robustness": "{}", "gate_failed": "[]",
            })
            self.evaluations.append((fid, at, key, r))
            for mode in MODE_CODES:
                is_sel = key in selected.get(mode, set())
                is_el = is_sel or key in eligible.get(mode, set())
                cid = insert(c, "selection_mode_candidates", {
                    "run_id": run_id, "evaluation_id": eid, "selection_mode": mode, "fixture_id": fid,
                    "market_id": mid, "outcome_id": oid, "mode_checks": "{}", "eligible": int(is_el),
                    "selected": int(is_sel), "rank": 1 if is_sel else None,
                })
                if not is_sel or c.execute(
                        "SELECT 1 FROM selection_mode_decisions WHERE selection_mode=? AND fixture_id=? AND market_id=?"
                        " AND outcome_id=?", (mode, fid, mid, oid)).fetchone():
                    continue  # aynı mod aynı seçimi bir kez kaydeder
                aid = insert(c, "selection_audits", {"candidate_id": cid, "selection_mode": mode, "status": "passed",
                                                     "checks": "[]", "auditor_version": "test", "audited_at": at})
                insert(c, "selection_mode_decisions", {
                    "candidate_id": cid, "audit_id": aid, "run_id": run_id, "fixture_id": fid, "market_id": mid,
                    "outcome_id": oid, "model_a_prediction_id": pa, "model_b_prediction_id": pb,
                    "selection_mode": mode, "selection_timestamp": at, "decimal_odds": odds, "data_cutoff_at": at,
                    "estimated_probability": p_est, "void_probability": p_void, "estimated_ev": ev_est,
                    "uncertainty_metrics": "{}", "audit_status": "passed", "decision_reason": dumps({}),
                })
        return run_id

    def settle(self, as_of: str = AS_OF) -> int:
        return settle_fixture_results(self.conn, History(), as_of)


def _rows(**over) -> dict[tuple, dict]:
    """Varsayılan altı seçenek; `over` ile satır bazında değişiklik."""
    base = {
        OVER: {"p_b": 0.55, "odds": 1.80}, UNDER: {"p_b": 0.45, "odds": 2.10},
        HOME: {"p_b": 0.48, "odds": 2.40}, DRAW: {"p_b": 0.27, "odds": 3.40, "core": 0},
        AWAY: {"p_b": 0.25, "odds": 3.90}, DNB_HOME: {"p_b": 0.48, "odds": 1.70, "p_void": 0.27},
    }
    for name, val in over.items():
        base[{"over": OVER, "under": UNDER, "home": HOME, "draw": DRAW, "away": AWAY, "dnb": DNB_HOME}[name]] = val
    return base


@pytest.fixture()
def world(tmp_path):
    """Dört maç, iki lig, iki sezon; F1 iki kez değerlendirilmiş (tekrarlı çalıştırma)."""
    b = Builder(tmp_path / "perf.sqlite3")
    b.f0 = b.fixture("tr-super-lig", "2025", "2025-11-10T18:00:00Z", (3, 0), (1, 0))
    b.f1 = b.fixture("tr-super-lig", "2026", "2026-11-10T18:00:00Z", (2, 1), (1, 0))
    b.f2 = b.fixture("tr-super-lig", "2026", "2026-11-15T18:00:00Z", (1, 1), (0, 0))
    b.f3 = b.fixture("en-premier-league", "2026", "2026-11-18T18:00:00Z", (0, 2), (0, 1))
    b.evaluate(b.f0, "2025-11-10T17:30:00Z", _rows(), selected={"highest_probability": {OVER}})
    # F1: erken çalıştırma (karar burada) ve başlama öncesi son çalıştırma (havuzda yalnızca bu sayılır)
    b.evaluate(b.f1, "2026-11-09T16:00:00Z", _rows(), selected={"highest_probability": {OVER}})
    b.evaluate(b.f1, "2026-11-10T17:30:00Z", _rows(away={"p_b": 0.25, "odds": 3.90, "ov": 0}),
               eligible={"highest_probability": {HOME}},
               selected={"highest_probability": {OVER}, "best_value": {HOME}})
    b.evaluate(b.f2, "2026-11-15T17:30:00Z", _rows(),
               eligible={"balanced": {UNDER}}, selected={"highest_probability": {UNDER}, "best_value": {DNB_HOME}})
    b.evaluate(b.f3, "2026-11-18T17:30:00Z", _rows(),
               eligible={"best_value": {AWAY, OVER}}, selected={"highest_probability": {AWAY}, "balanced": {OVER}})
    b.settle()
    yield b
    b.conn.close()


# ───────────────────────── metrics_for_decisions ─────────────────────────

def _dec(status, odds, p, profit, pv=0.0, ev=None):
    return {"settlement_status": status, "decimal_odds": odds, "estimated_probability": p, "void_probability": pv,
            "estimated_ev": ev if ev is not None else (None if odds is None else p * odds + pv - 1), "profit_units": profit}


HAND = [
    _dec("won", 2.0, 0.6, 1.0),
    _dec("won", 1.5, 0.7, 0.5),
    _dec("lost", 2.5, 0.5, -1.0),
    _dec("lost", 3.0, 0.4, -1.0),
    _dec("void", 2.0, 0.3, 0.0, pv=0.4),
    _dec("pending", 1.8, 0.55, None),
]


def test_metrics_hand_built_wilson_roi_brier():
    m = metrics_for_decisions(HAND)
    assert m["n_selections"] == 6
    assert m["n_settled"] == 5 and m["n_void"] == 1
    lo, hi = wilson(2, 4)
    assert m["hit_rate"]["value"] == pytest.approx(0.5)
    assert m["hit_rate"]["ci_low"] == pytest.approx(lo, abs=1e-4)
    assert m["hit_rate"]["ci_high"] == pytest.approx(hi, abs=1e-4)
    # Brier / log loss yalnızca sonuçlanmış ve iade olmayan dört seçimde
    assert m["brier"] == pytest.approx((0.4 ** 2 + 0.3 ** 2 + 0.5 ** 2 + 0.4 ** 2) / 4, abs=1e-4)
    ll = -(math.log(0.6) + math.log(0.7) + math.log(0.5) + math.log(0.6)) / 4
    assert m["log_loss"] == pytest.approx(ll, abs=1e-4)
    # dört nokta → dört tek elemanlı kutu; ECE = ortalama |p − y|
    assert len(m["calibration_bins"]) == 4
    assert m["ece"] == pytest.approx((0.4 + 0.3 + 0.5 + 0.4) / 4, abs=1e-4)
    # ROI: sonuçlanmış beş seçim (iade = 0), bekleyen hariç
    assert m["roi"]["value"] == pytest.approx((1.0 + 0.5 - 1.0 - 1.0 + 0.0) / 5, abs=1e-4)
    assert m["roi"]["ci_low"] <= m["roi"]["value"] <= m["roi"]["ci_high"]
    assert m["profit_units"] == pytest.approx(-0.5, abs=1e-4)
    # ortalamalar bütün seçimler üzerinden
    assert m["avg_odds"] == pytest.approx((2.0 + 1.5 + 2.5 + 3.0 + 2.0 + 1.8) / 6, abs=1e-4)
    assert m["avg_probability"] == pytest.approx((0.6 + 0.7 + 0.5 + 0.4 + 0.3 + 0.55) / 6, abs=1e-4)
    evs = [0.6 * 2 - 1, 0.7 * 1.5 - 1, 0.5 * 2.5 - 1, 0.4 * 3 - 1, 0.3 * 2 + 0.4 - 1, 0.55 * 1.8 - 1]
    assert m["avg_ev"] == pytest.approx(sum(evs) / 6, abs=1e-4)
    # havuz alanları çağırana bırakılır
    for k in ("n_candidates", "n_eligible", "n_fixtures", "n_fixtures_without_selection", "selection_rate"):
        assert m[k] is None
    # bootstrap aralığı deterministik
    assert metrics_for_decisions(HAND)["roi"] == m["roi"]


def test_metrics_void_handling_and_empty():
    # iade olasılığı ayrılır: 0.45 / (1 − 0.25) = 0.6
    m = metrics_for_decisions([_dec("won", 1.7, 0.45, 0.7, pv=0.25), _dec("void", 1.7, 0.45, 0.0, pv=0.25)])
    assert m["brier"] == pytest.approx(0.16, abs=1e-4)
    assert m["calibration_bins"][0]["p_mean"] == pytest.approx(0.6, abs=1e-4)
    assert m["hit_rate"]["value"] == pytest.approx(1.0)          # iade paydaya girmez
    assert m["roi"]["value"] == pytest.approx((0.7 + 0.0) / 2)   # iade ROI'ye 0 olarak girer
    assert m["n_void"] == 1 and m["n_settled"] == 2

    only_void = metrics_for_decisions([_dec("void", 2.0, 0.4, None, pv=0.3)])
    assert only_void["hit_rate"] == {"value": None, "ci_low": None, "ci_high": None}
    assert only_void["brier"] is None and only_void["calibration_bins"] == []
    assert only_void["roi"]["value"] == 0.0 and only_void["profit_units"] == 0.0

    no_odds = metrics_for_decisions([_dec("won", None, 0.9, None), _dec("lost", None, 0.8, None)])
    assert no_odds["hit_rate"]["value"] == pytest.approx(0.5)
    assert no_odds["roi"]["value"] is None and no_odds["profit_units"] is None and no_odds["avg_odds"] is None

    empty = metrics_for_decisions([])
    assert empty["n_selections"] == 0 and empty["hit_rate"]["value"] is None and empty["roi"]["value"] is None
    assert empty["avg_probability"] is None and empty["ece"] is None


# ───────────────────────── window_bounds ─────────────────────────

def test_window_bounds():
    t = "2026-11-21T17:00:00Z"
    assert window_bounds("7d", t) == ("2026-11-14T17:00:00Z", t)
    assert window_bounds("30d", t) == ("2026-10-22T17:00:00Z", t)
    assert window_bounds("90d", t) == ("2026-08-23T17:00:00Z", t)
    assert window_bounds("all", t) == (None, t)
    assert window_bounds("season:2025", t) == (None, t)
    for bad in ("", "1y", "7D", "season:", "season:25", "season:2025x", "30"):
        with pytest.raises(ValueError):
            window_bounds(bad, t)
    with pytest.raises(ValueError):
        window_bounds("7d", "2026-11-21")


# ───────────────────────── karşılaştırma ─────────────────────────

def test_comparison_same_pool_for_all_modes(world):
    c = modes_comparison(world.conn, "all", AS_OF)
    # havuz: 4 maç × 6 seçenek; F1'in iki çalıştırmasından yalnızca sonuncusu sayılır
    assert c["pool"]["fixtures"] == 4 and c["pool"]["candidates"] == 24
    expected_hash = hashlib.sha256(",".join(str(f) for f in sorted(
        [world.f0, world.f1, world.f2, world.f3])).encode()).hexdigest()
    assert c["pool"]["hash"] == expected_hash
    assert [m["code"] for m in c["modes"]] == list(MODE_CODES)
    by = {m["code"]: m["metrics"] for m in c["modes"]}
    for m in by.values():
        assert m["n_candidates"] == 24 and m["n_fixtures"] == 4
        assert m["selection_rate"] == pytest.approx(m["n_selections"] / 24, abs=1e-4)
    assert {k: v["n_selections"] for k, v in by.items()} == {"highest_probability": 4, "best_value": 2, "balanced": 1}
    # uygunluk: farklı havuz anahtarları (seçilenler uygundur)
    assert {k: v["n_eligible"] for k, v in by.items()} == {"highest_probability": 5, "best_value": 4, "balanced": 2}
    assert by["highest_probability"]["n_fixtures_without_selection"] == 0
    assert by["best_value"]["n_fixtures_without_selection"] == 2
    assert by["balanced"]["n_fixtures_without_selection"] == 3
    assert any("Seçim kapsamı" in n for n in c["notes"])
    assert any("teorik" in n and "garanti" in n for n in c["notes"])
    assert c["window"] == {"start": "2025-11-10T17:30:00Z", "end": AS_OF}


def test_comparison_windows_and_season(world):
    c7 = modes_comparison(world.conn, "7d", AS_OF)         # 2026-11-14 sonrası: F2, F3
    assert c7["window"] == {"start": "2026-11-14T00:00:00Z", "end": AS_OF}
    assert (c7["pool"]["fixtures"], c7["pool"]["candidates"]) == (2, 12)
    assert {m["code"]: m["metrics"]["n_selections"] for m in c7["modes"]} == \
        {"highest_probability": 2, "best_value": 1, "balanced": 1}
    cs = modes_comparison(world.conn, "season:2025", AS_OF)
    assert (cs["pool"]["fixtures"], cs["pool"]["candidates"]) == (1, 6)
    assert {m["code"]: m["metrics"]["n_selections"] for m in cs["modes"]} == \
        {"highest_probability": 1, "best_value": 0, "balanced": 0}
    with pytest.raises(ValueError):
        modes_comparison(world.conn, "yesterday", AS_OF)


def test_baseline_covers_all_core_pass_candidates(world):
    c = modes_comparison(world.conn, "all", AS_OF)
    base = c["baseline"]
    assert base["label"] == "Tüm adaylar (seçim yapılmadan, MODEL B)"
    # beklenen: havuzdaki (maç başına son değerlendirme) core_pass ∧ odds_valid satırları
    last = {}
    for fid, at, key, r in world.evaluations:
        if fid not in last or at >= last[fid]:
            last[fid] = at
    rows = [(fid, key, r) for fid, at, key, r in world.evaluations
            if at == last[fid] and r.get("core", 1) and r.get("ov", 1)]
    status = [world.outcomes[fid][key] for fid, key, _ in rows]
    profit = [r["odds"] - 1 if s == "won" else -1.0 if s == "lost" else 0.0
              for (_, _, r), s in zip(rows, status)]
    won, lost = status.count("won"), status.count("lost")
    m = base["metrics"]
    assert m["n_candidates"] == 24
    assert m["n_selections"] == m["n_eligible"] == len(rows) == 19   # 24 − 4 core_pass=0 − 1 geçersiz oran
    assert m["n_settled"] == len(rows) and m["n_void"] == status.count("void")
    assert m["hit_rate"]["value"] == pytest.approx(won / (won + lost), abs=1e-4)
    assert m["profit_units"] == pytest.approx(sum(profit), abs=1e-4)
    assert m["roi"]["value"] == pytest.approx(sum(profit) / len(profit), abs=1e-4)
    assert m["avg_probability"] == pytest.approx(sum(r["p_b"] for _, _, r in rows) / len(rows), abs=1e-4)
    # taban çizgisi seçilmiş adaylardan çok daha geniştir: seçilmeyen kaybedenler de sayılır
    sel_total = sum(x["metrics"]["n_selections"] for x in c["modes"])
    assert m["n_selections"] > sel_total
    hp = next(x for x in c["modes"] if x["code"] == "highest_probability")["metrics"]
    assert hp["hit_rate"]["value"] == 1.0 and m["hit_rate"]["value"] < 0.6


# ───────────────────────── modlar ayrı ─────────────────────────

def test_mode_records_are_separate(world):
    bal = mode_performance(world.conn, "balanced", "all", AS_OF)
    bv = mode_performance(world.conn, "best_value", "all", AS_OF)
    hp = mode_performance(world.conn, "highest_probability", "all", AS_OF)
    # dengeli: yalnızca F3 Üst 2,5 (kaybetti) — diğer modların kazanan seçimleri karışmaz
    o = bal["overall"]
    assert (o["n_selections"], o["n_settled"], o["hit_rate"]["value"], o["profit_units"]) == (1, 1, 0.0, -1.0)
    # en avantajlı: F1 Ev (2,40 kazandı) + F2 BI Ev (iade)
    o = bv["overall"]
    assert (o["n_selections"], o["n_settled"], o["n_void"]) == (2, 2, 1)
    assert o["hit_rate"]["value"] == 1.0 and o["roi"]["value"] == pytest.approx((1.4 + 0.0) / 2, abs=1e-4)
    o = hp["overall"]
    assert (o["n_selections"], o["hit_rate"]["value"]) == (4, 1.0)
    assert o["profit_units"] == pytest.approx(0.8 + 0.8 + 1.1 + 2.9, abs=1e-4)
    # lig/market kırılımları da yalnızca kendi kararlarından
    lg = {r["league"]["id"]: r["metrics"]["n_selections"] for r in bal["by_league"]}
    assert lg == {"tr-super-lig": 0, "en-premier-league": 1}
    fam = {r["family"]: (r["name"], r["metrics"]["n_selections"]) for r in bv["by_market"]}
    assert fam["result"] == ("Maç Sonucu", 1) and fam["dnb"] == ("Beraberlikte İade", 1) and fam["totals"][1] == 0
    # bir modun kaydını başka moda aktarmak veritabanında reddedilir
    did = world.conn.execute("SELECT id FROM selection_mode_decisions WHERE selection_mode='balanced'").fetchone()[0]
    with pytest.raises(sqlite3.IntegrityError, match="değiştirilemez|sonuçlandırılamaz"):
        world.conn.execute("UPDATE selection_mode_decisions SET selection_mode='best_value' WHERE id=?", (did,))
    assert world.conn.execute("SELECT selection_mode FROM selection_mode_decisions WHERE id=?",
                              (did,)).fetchone()[0] == "balanced"


def test_mode_performance_shape_season_stability_timeline(world):
    hp = mode_performance(world.conn, "highest_probability", "all", AS_OF)
    assert set(hp) == {"mode", "period", "overall", "windows", "by_league", "by_market", "by_season",
                       "season_stability", "timeline"}
    assert set(hp["windows"]) == {"7d", "30d", "90d"}
    assert hp["windows"]["7d"]["n_selections"] == 2 and hp["windows"]["30d"]["n_selections"] == 3
    assert [s["season"] for s in hp["by_season"]] == ["2025", "2026"]
    assert [s["metrics"]["n_selections"] for s in hp["by_season"]] == [1, 3]
    st = hp["season_stability"]
    assert st["seasons"] == 2 and st["consistent"] is True and st["hit_rate_std"] == 0.0
    # zaman çizelgesi: sonuçlanma gününe göre kümülatif
    assert hp["timeline"] == [
        {"date": "2025-11-10", "cumulative_profit": 0.8, "n": 1},
        {"date": "2026-11-10", "cumulative_profit": 1.6, "n": 2},
        {"date": "2026-11-15", "cumulative_profit": 2.7, "n": 3},
        {"date": "2026-11-18", "cumulative_profit": 5.6, "n": 4},
    ]
    one = mode_performance(world.conn, "balanced", "all", AS_OF)["season_stability"]
    assert one == {"seasons": 1, "roi_std": None, "hit_rate_std": None, "consistent": False}
    with pytest.raises(ValueError):
        mode_performance(world.conn, "most_profitable", "all", AS_OF)


def test_season_stability_inconsistent_when_ci_excludes_overall():
    R = tracker._Raw
    good = R(won=90, lost=10, hit=0.9, hit_lo=None, hit_hi=None, roi=0.05, n_settled=100)
    bad = R(won=40, lost=60, hit=0.4, hit_lo=None, hit_hi=None, roi=-0.2, n_settled=100)
    for r in (good, bad):
        r.hit_lo, r.hit_hi = wilson(r.won, r.won + r.lost)
    overall = R(won=130, lost=70, hit=0.65, hit_lo=None, hit_hi=None, roi=-0.075, n_settled=200)
    st = tracker._season_stability([good, bad], overall)
    assert st["seasons"] == 2 and st["consistent"] is False
    assert st["roi_std"] == pytest.approx(0.125) and st["hit_rate_std"] == pytest.approx(0.25)


def test_point_in_time_settlement(world):
    # F3 sonucu 2026-11-18T20:00'de kaydedildi; öncesinde F3 kararları "beklemede" sayılır.
    t = "2026-11-18T19:00:00Z"
    bal = mode_performance(world.conn, "balanced", "all", t)["overall"]
    assert bal["n_selections"] == 1 and bal["n_settled"] == 0 and bal["roi"]["value"] is None
    c = modes_comparison(world.conn, "all", t)
    base = c["baseline"]["metrics"]
    assert base["n_selections"] == 19 and base["n_settled"] == 19 - 5   # F3'ün 5 adayı henüz sonuçsuz
    assert any("sonuçlanmamış" in n for n in c["notes"])
    # as_of'tan sonraki değerlendirme ve seçimler görünmez
    early = modes_comparison(world.conn, "all", "2026-11-12T00:00:00Z")
    assert early["pool"]["fixtures"] == 2
    assert {m["code"]: m["metrics"]["n_selections"] for m in early["modes"]}["best_value"] == 1


# ───────────────────────── Başarı Merkezi sekmesi ─────────────────────────

def test_selection_modes_overview(world):
    ov = selection_modes_overview(world.conn, AS_OF)
    assert ov["generated_at"] == AS_OF and ov["is_synthetic_data"] is True
    assert [m["code"] for m in ov["modes"]] == ["highest_probability", "best_value", "balanced"]
    for m in ov["modes"]:
        assert set(m) == {"code", "name", "short_name", "overall", "windows", "by_season", "season_stability",
                          "timeline"}
    assert ov["modes"][2]["short_name"] == "Dengeli" and ov["modes"][2]["name"] == "Dengeli Seçimler"
    assert ov["baseline"]["metrics"]["n_selections"] == 19
    assert "Modların başarısı birbirinden bağımsız kaydedilir; geçmiş seçimler değiştirilemez." in ov["notes"]


# ───────────────────────── geçmiş başarı ─────────────────────────

def test_history_for_counts_only_strictly_before(world):
    c = world.conn
    h = history_for(c, "highest_probability", "totals", "tr-super-lig", "2026-11-09T16:00:00Z")
    assert h["scope"] == "En Güvenilir · Alt/Üst · Süper Lig"
    assert (h["n"], h["hit_rate"], h["roi"]) == (1, 1.0, 0.8)            # yalnızca F0 (F1 tam bu anda: hariç)
    h = history_for(c, "highest_probability", "totals", "tr-super-lig", "2026-11-09T16:00:01Z")
    assert h["n"] == 1                                                     # F1 seçildi ama henüz sonuçsuz
    h = history_for(c, "highest_probability", "totals", "tr-super-lig", "2026-11-10T20:00:00Z")
    assert h["n"] == 2
    h = history_for(c, "highest_probability", "totals", "tr-super-lig", AS_OF)
    assert (h["n"], h["hit_rate"]) == (3, 1.0)
    assert history_for(c, "highest_probability", "totals", "en-premier-league", AS_OF)["n"] == 0
    empty = history_for(c, "balanced", "btts", "tr-super-lig", AS_OF)
    assert empty == {"scope": "Dengeli · Karşılıklı Gol · Süper Lig", "n": 0, "hit_rate": None, "roi": None}
    bv = history_for(c, "best_value", "dnb", "tr-super-lig", AS_OF)
    assert (bv["n"], bv["hit_rate"], bv["roi"]) == (1, None, 0.0)          # tek seçim iade
    with pytest.raises(ValueError):
        history_for(c, "balanced", "nope", "tr-super-lig", AS_OF)


# ───────────────────────── kayıt (append-only) ─────────────────────────

def _counts(conn):
    return {t: conn.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]
            for t in ("selection_mode_performance", "selection_mode_calibration", "selection_mode_comparisons")}


def test_snapshot_performance_is_append_only(world):
    c = world.conn
    n1 = snapshot_performance(c, AS_OF)
    first = _counts(c)
    assert n1 == sum(first.values())
    assert first["selection_mode_calibration"] == 3 * 4 and first["selection_mode_comparisons"] == 4
    overall = c.execute("SELECT mode, period FROM selection_mode_performance WHERE scope='overall'").fetchall()
    assert sorted(map(tuple, overall)) == sorted((m, p) for m in MODE_CODES for p in ("all", "7d", "30d", "90d"))
    scopes = {r[0] for r in c.execute("SELECT DISTINCT scope FROM selection_mode_performance")}
    assert scopes == {"overall", "league", "market"}
    old = {r["id"]: tuple(r) for r in c.execute("SELECT * FROM selection_mode_performance")}

    n2 = snapshot_performance(c, AS_OF)
    assert n2 == n1
    second = _counts(c)
    assert all(second[t] == 2 * first[t] for t in first)
    for rid, row in old.items():
        assert tuple(c.execute("SELECT * FROM selection_mode_performance WHERE id=?", (rid,)).fetchone()) == row

    # her mod kaydı yalnızca kendi kararlarından
    rec = {r["mode"]: json.loads(r["metrics"]) for r in c.execute(
        "SELECT mode, metrics FROM selection_mode_performance WHERE scope='overall' AND period='all' AND id IN"
        " (SELECT MIN(id) FROM selection_mode_performance WHERE scope='overall' AND period='all' GROUP BY mode)")}
    assert {m: v["n_selections"] for m, v in rec.items()} == {"highest_probability": 4, "best_value": 2, "balanced": 1}
    cmp = c.execute("SELECT * FROM selection_mode_comparisons WHERE period='all' ORDER BY id LIMIT 1").fetchone()
    assert (cmp["pool_fixtures"], cmp["pool_candidates"]) == (4, 24) and len(cmp["pool_hash"]) == 64
    assert json.loads(cmp["metrics"])["baseline"]["metrics"]["n_selections"] == 19
    cal = c.execute("SELECT n, ece FROM selection_mode_calibration WHERE mode='highest_probability' AND period='all'"
                    " ORDER BY id LIMIT 1").fetchone()
    assert cal["n"] == 4 and cal["ece"] is not None
