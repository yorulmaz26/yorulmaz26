"""Ortak test düzeni.

`demo_db`: oturum başına bir kez kurulan, sentetik veriyle (favori–sürpriz eğilimli piyasa) kronolojik
geriye dönük testi ve güncel bir maç öncesi çalıştırmayı içeren veritabanı. Testler bu dosyanın
kopyası üzerinde çalışır; böylece değiştiren testler birbirini etkilemez.
"""
from __future__ import annotations

import shutil
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

AS_OF = "2026-11-21T17:00:00Z"


@pytest.fixture(scope="session")
def demo_db_path(tmp_path_factory) -> Path:
    from akilli_tahmin.backtest.walk_forward import run_backtest
    from akilli_tahmin.data.synthetic import generate_demo
    from akilli_tahmin.db.database import connect, init_db
    from akilli_tahmin.selection.engine import settle_fixture_results

    path = tmp_path_factory.mktemp("demo") / "demo.sqlite3"
    conn = connect(path)
    init_db(conn, AS_OF)
    generate_demo(conn, AS_OF, market="biased")
    try:
        from akilli_tahmin.data.synthetic_live import generate_goal_events

        generate_goal_events(conn, AS_OF)
    except ImportError:
        pass
    _, engine = run_backtest(conn, AS_OF)
    settle_fixture_results(conn, engine.history, AS_OF)
    engine.run(AS_OF)
    conn.close()
    return path


@pytest.fixture()
def demo_conn(demo_db_path, tmp_path):
    from akilli_tahmin.db.database import connect

    dst = tmp_path / "copy.sqlite3"
    shutil.copy(demo_db_path, dst)
    for ext in ("-wal", "-shm"):
        src = Path(str(demo_db_path) + ext)
        if src.exists():
            shutil.copy(src, str(dst) + ext)
    conn = connect(dst)
    yield conn
    conn.close()


@pytest.fixture()
def demo_copy_path(demo_db_path, tmp_path) -> Path:
    dst = tmp_path / "api.sqlite3"
    shutil.copy(demo_db_path, dst)
    return dst


@pytest.fixture()
def empty_conn():
    from akilli_tahmin.db.database import connect, init_db

    conn = connect()
    init_db(conn, AS_OF)
    yield conn
    conn.close()
