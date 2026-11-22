"""Kronolojik ileri yürüyen (walk-forward) geriye dönük test.

Geçmişteki her başlama saati dilimi için sistem, o dilimin 30 dakika öncesindeki bilgiyle çalıştırılır:
- MODEL A yalnızca o ana kadar *sonucu kaydedilmiş* maçlarla uydurulur,
- MODEL B, kalibrasyon, birleştirici ve mod eşikleri yalnızca o ana kadar sonucu bilinen geçmiş
  tahminlerle eğitilir/ayarlanır,
- oranlar yalnızca o ana kadar yakalanmış anlık görüntülerden alınır.
Bu nedenle üretilen geçmiş seçimler, canlı sistemin o gün üreteceği seçimlerle aynı bilgi kümesine
dayanır ve modlar arası karşılaştırma aynı maç havuzunda yapılır.
"""
from __future__ import annotations

import sqlite3
import time
from collections import defaultdict
from dataclasses import dataclass

from ..config import SETTINGS, Settings
from ..db.database import iso_add
from ..selection.engine import Engine, settle_fixture_results
from ..selection.history import History


@dataclass
class BacktestReport:
    runs: int
    fixtures: int
    skipped_fixtures: int
    selected: dict[str, int]
    seconds: float


def run_backtest(conn: sqlite3.Connection, until: str, *, settings: Settings = SETTINGS,
                 lead_minutes: int = 30, n_draws: int | None = None, progress=None) -> tuple[BacktestReport, Engine]:
    hist = History()
    engine = Engine(conn, settings, hist, n_draws=n_draws)
    rows = conn.execute(
        "SELECT id, league_id, kickoff_at, result_recorded_at FROM fixtures WHERE result_recorded_at IS NOT NULL"
        " AND kickoff_at < ? ORDER BY kickoff_at", (until,),
    ).fetchall()
    slots: dict[str, list[int]] = defaultdict(list)
    for r in rows:
        slots[r["kickoff_at"]].append(r["id"])
    t0 = time.time()
    selected: dict[str, int] = defaultdict(int)
    n_runs = n_fx = n_skip = 0
    day_models = {}
    for ko in sorted(slots):
        cutoff = iso_add(ko, minutes=-lead_minutes)
        settle_fixture_results(conn, hist, cutoff)
        ids = []
        for fid in slots[ko]:
            ids.append(fid)
        # MODEL A'nın uydurulamadığı erken dönem maçları değerlendirilmez (gerekçesi raporlanır).
        valid_ids = [fid for fid in ids if _league_ready(engine, conn, fid, cutoff, settings)]
        n_skip += len(ids) - len(valid_ids)
        if not valid_ids:
            continue
        day = cutoff[:10]
        if day not in day_models:
            day_models.clear()
            day_models[day] = engine.prepare(cutoff)
        summary = engine.run(cutoff, kind="backtest", fixture_ids=valid_ids, models=day_models[day],
                             point_in_time_status=True)
        n_runs += 1
        n_fx += summary.fixtures
        for m, v in summary.selected.items():
            selected[m] += v
        if progress:
            progress(ko, summary)
    settle_fixture_results(conn, hist, until)
    return BacktestReport(n_runs, n_fx, n_skip, dict(selected), time.time() - t0), engine


def _league_ready(engine: Engine, conn: sqlite3.Connection, fixture_id: int, cutoff: str, settings: Settings) -> bool:
    lg = conn.execute("SELECT league_id FROM fixtures WHERE id=?", (fixture_id,)).fetchone()[0]
    return len(engine.football.match_records(lg, cutoff)) >= settings.data.min_league_matches
