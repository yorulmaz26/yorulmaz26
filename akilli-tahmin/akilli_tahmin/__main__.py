"""Komut satırı: python -m akilli_tahmin <komut>

  init        Boş veritabanını kurar (21 organizasyon, 3 seçim modu, market kuralları).
  demo        Sentetik demo verisi üretir, kronolojik geriye dönük testi çalıştırır, güncel önerileri üretir.
  run         Yaklaşan maçlar için 15 adımlı seçim sürecini çalıştırır (gerçek veri).
  backtest    Veritabanındaki geçmiş maçlar üzerinde kronolojik geriye dönük test çalıştırır.
  import-csv  football-data.co.uk biçimli geçmiş sonuç/oran dosyası içe aktarır.
  serve       API ve web arayüzünü başlatır.
"""
from __future__ import annotations

import argparse
import json
import sys
import time

from .db.database import connect, init_db, to_iso, utcnow


def _now_minute() -> str:
    return to_iso(utcnow().replace(second=0))


def cmd_init(a) -> None:
    conn = connect(a.db)
    init_db(conn)
    print(f"Veritabanı hazır: {a.db}")


def cmd_demo(a) -> None:
    from .backtest.walk_forward import run_backtest
    from .data.synthetic import generate_demo
    from .performance.tracker import snapshot_performance
    from .selection.engine import settle_fixture_results

    as_of = a.as_of or _now_minute()
    conn = connect(a.db)
    if conn.execute("SELECT name FROM sqlite_master WHERE name='fixtures'").fetchone() and \
            conn.execute("SELECT COUNT(*) FROM fixtures").fetchone()[0] > 0:
        sys.exit("Veritabanı boş değil; demo için yeni bir dosya kullanın (--db).")
    init_db(conn, as_of)
    t0 = time.time()
    s = generate_demo(conn, as_of, market=a.market, seed=a.seed)
    print(f"Sentetik veri: {s.fixtures_finished} oynanmış, {s.fixtures_upcoming} yaklaşan, {s.fixtures_live} canlı maç; "
          f"{s.odds_snapshots} oran (piyasa: {a.market}).")
    try:
        from .data.synthetic_live import generate_goal_events

        print(f"Gol dakikaları: {generate_goal_events(conn, as_of)}")
    except ImportError:
        pass
    rep, engine = run_backtest(conn, as_of)
    print(f"Geriye dönük test: {rep.runs} çalıştırma, {rep.fixtures} maç ({rep.skipped_fixtures} erken dönem maçı "
          f"MODEL A için yetersiz veri nedeniyle değerlendirilmedi), seçimler {rep.selected}, {rep.seconds:.0f} sn.")
    settle_fixture_results(conn, engine.history, as_of)
    summary = engine.run(as_of)
    print(f"Güncel çalıştırma #{summary.run_id}: {summary.fixtures} maç, seçimler {summary.selected}")
    n = snapshot_performance(conn, as_of)
    conn.execute("VACUUM")
    print(f"Performans kayıtları: {n}. Toplam süre {time.time() - t0:.0f} sn. Başlatmak için:\n"
          f"  python -m akilli_tahmin serve --db {a.db}")


def cmd_run(a) -> None:
    from .runtime import Runtime

    rt = Runtime(a.db)
    print(json.dumps(rt.run_prematch(a.as_of or _now_minute()), ensure_ascii=False))


def cmd_backtest(a) -> None:
    from .backtest.walk_forward import run_backtest
    from .performance.tracker import snapshot_performance

    conn = connect(a.db)
    init_db(conn)
    until = a.until or _now_minute()
    rep, _ = run_backtest(conn, until)
    snapshot_performance(conn, until)
    print(json.dumps(rep.__dict__, ensure_ascii=False))


def cmd_import(a) -> None:
    from .data.football_data_csv import import_csv

    conn = connect(a.db)
    init_db(conn)
    print(json.dumps(import_csv(conn, a.file, a.league, a.season), ensure_ascii=False))


def cmd_serve(a) -> None:
    import uvicorn

    from .api.app import create_app

    uvicorn.run(create_app(a.db), host=a.host, port=a.port)


def main(argv: list[str] | None = None) -> None:
    p = argparse.ArgumentParser(prog="akilli_tahmin", description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    def add(name, fn, help_):
        sp = sub.add_parser(name, help=help_)
        sp.add_argument("--db", default="akilli_tahmin.sqlite3")
        sp.set_defaults(fn=fn)
        return sp

    add("init", cmd_init, "Veritabanını kur")
    sp = add("demo", cmd_demo, "Sentetik demo")
    sp.add_argument("--market", choices=("biased", "efficient"), default="biased")
    sp.add_argument("--as-of", default=None)
    sp.add_argument("--seed", type=int, default=7)
    sp = add("run", cmd_run, "Yaklaşan maçları değerlendir")
    sp.add_argument("--as-of", default=None)
    sp = add("backtest", cmd_backtest, "Geriye dönük test")
    sp.add_argument("--until", default=None)
    sp = add("import-csv", cmd_import, "CSV içe aktar")
    sp.add_argument("file")
    sp.add_argument("--league", required=True)
    sp.add_argument("--season", required=True)
    sp = add("serve", cmd_serve, "API ve arayüzü başlat")
    sp.add_argument("--host", default="127.0.0.1")
    sp.add_argument("--port", type=int, default=8000)
    a = p.parse_args(argv)
    a.fn(a)


if __name__ == "__main__":
    main()
