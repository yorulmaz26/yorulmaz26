"""Komut satırı: python -m akilli_tahmin <komut>

  init        Boş veritabanını kurar (21 organizasyon, 3 seçim modu, market kuralları).
  demo        Sentetik demo verisi üretir, kronolojik geriye dönük testi çalıştırır, güncel önerileri üretir.
  run         Yaklaşan maçlar için 15 adımlı seçim sürecini çalıştırır (gerçek veri).
  backtest    Veritabanındaki geçmiş maçlar üzerinde kronolojik geriye dönük test çalıştırır.
  import-csv  football-data.co.uk biçimli geçmiş sonuç/oran dosyası içe aktarır.
  real-data   Ücretsiz gerçek veriyle (football-data.co.uk) kurulum: indir, geriye dönük test, güncel tahmin.
  update-data Gerçek veriyi günceller (yeni sonuçlar, yaklaşan maçlar) ve seçim sürecini yeniden çalıştırır.
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
    s = generate_demo(conn, as_of, market=a.market, seed=a.seed, leagues=tuple(a.leagues.split(",")))
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
    try:
        print(json.dumps(import_csv(conn, a.file, a.league, a.season), ensure_ascii=False))
    except (ValueError, OSError) as exc:
        sys.exit(str(exc))


def _codes(spec: str) -> tuple[tuple[str, ...], tuple[str, ...]]:
    from .data.football_data_feed import EXTRA_LEAGUES, MAIN_DIVISIONS

    wanted = [c.strip().upper() for c in spec.split(",") if c.strip()]
    bad = [c for c in wanted if c not in MAIN_DIVISIONS and c not in EXTRA_LEAGUES]
    if bad or not wanted:
        sys.exit(f"Bilinmeyen lig kodu: {', '.join(bad) or '(boş)'}. Geçerli: "
                 f"{', '.join(list(MAIN_DIVISIONS) + list(EXTRA_LEAGUES))}")
    return (tuple(c for c in wanted if c in MAIN_DIVISIONS), tuple(c for c in wanted if c in EXTRA_LEAGUES))


def _report_errors(rep: dict) -> None:
    for e in rep["errors"]:
        print(f"  ! {e}", file=sys.stderr)
    if rep.get("invalid_rows"):
        print(f"  ! {rep['invalid_rows']} bozuk satır atlandı", file=sys.stderr)


def cmd_real_data(a) -> None:
    from .backtest.walk_forward import run_backtest
    from .data.football_data_feed import save_config, sync
    from .performance.tracker import snapshot_performance
    from .selection.engine import settle_fixture_results

    main_codes, extra = _codes(a.leagues)  # bağımsız değişkenler veritabanı oluşturulmadan doğrulanır
    if a.seasons < 1:
        sys.exit("--seasons en az 1 olmalı.")
    as_of = _now_minute()
    conn = connect(a.db)
    init_db(conn, as_of)
    if conn.execute("SELECT 1 FROM fixtures WHERE is_synthetic=1 LIMIT 1").fetchone():
        sys.exit("Bu veritabanında sentetik demo verisi var; gerçek veri için yeni bir dosya kullanın (--db).")
    if conn.execute("SELECT 1 FROM prediction_runs WHERE kind='backtest' LIMIT 1").fetchone():
        sys.exit("Bu veritabanı zaten kurulmuş (geriye dönük test kayıtları var). Güncellemek için: "
                 f"python -m akilli_tahmin update-data --db {a.db}")
    t0 = time.time()
    rep = sync(conn, as_of=as_of, codes=main_codes, seasons=a.seasons, include_extra=extra,
               cache_dir=a.db + ".cache")
    _report_errors(rep)
    n = dict(conn.execute("SELECT status, COUNT(*) FROM fixtures GROUP BY status").fetchall())
    print("Veri: " + (", ".join(f"{k}={v}" for k, v in n.items()) or "yok"))
    if not n.get("finished") or (rep["errors"] and not a.allow_partial):
        sys.exit("Veri indirilemedi veya eksik indi; kurulum yapılmadı (ağı kontrol edin ya da --allow-partial).")
    save_config(conn, main_codes, extra, as_of)
    bt, engine = run_backtest(conn, as_of, lean=not a.full_history)
    print(f"Geriye dönük test: {bt.runs} çalıştırma, {bt.fixtures} maç, seçimler {bt.selected}, {bt.seconds:.0f} sn.")
    settle_fixture_results(conn, engine.history, as_of)
    s = engine.run(as_of)
    print(f"Güncel çalıştırma #{s.run_id}: {s.fixtures} yaklaşan maç, seçimler {s.selected}")
    snapshot_performance(conn, as_of)
    conn.execute("VACUUM")
    print(f"Toplam {time.time() - t0:.0f} sn. Başlatmak için: python -m akilli_tahmin serve --db {a.db}")


def cmd_update_data(a) -> None:
    from .data.football_data_feed import codes_for_leagues, load_config, sync
    from .runtime import Runtime

    if a.seasons < 1:
        sys.exit("--seasons en az 1 olmalı.")
    as_of = _now_minute()
    rt = Runtime(a.db)
    if rt.is_synthetic():
        sys.exit("Bu bir sentetik demo veritabanı; gerçek veri eklenemez. Yeni dosyayla: real-data --db ...")
    cfg = load_config(rt.conn) or codes_for_leagues(
        {r[0] for r in rt.conn.execute("SELECT DISTINCT league_id FROM fixtures WHERE source='football-data-csv'")})
    if a.leagues:
        main_codes, extra = _codes(a.leagues)
        new = (set(main_codes) | set(extra)) - (set(cfg[0]) | set(cfg[1]))
        if new and not a.allow_new:
            sys.exit(f"Geriye dönük testi yapılmamış lig: {', '.join(sorted(new))}. Bu liglerin eşikleri ve "
                     "kalibrasyonu doğrulanmadı; yine de eklemek için --allow-new kullanın (önerilen: yeni dosyayla "
                     "real-data).")
    else:
        main_codes, extra = cfg
    if not main_codes and not extra:
        sys.exit("Güncellenecek football-data.co.uk ligi yok; önce: real-data --db ...")
    with rt.lock:
        rep = sync(rt.conn, as_of=as_of, codes=main_codes, seasons=a.seasons, include_extra=extra,
                   cache_dir=a.db + ".cache")
        rt.engine.football.invalidate()
    _report_errors(rep)
    out = rt.run_prematch(as_of)
    print(json.dumps({"errors": rep["errors"], "postponed": rep.get("postponed", 0), **out}, ensure_ascii=False))
    if rep["errors"]:
        sys.exit(2)


def cmd_setup(a) -> None:
    """Tek komutla kurulum (Windows kur.bat): veritabanı kurulmamışsa real-data, kurulmuşsa update-data."""
    from pathlib import Path

    installed = False
    if Path(a.db).exists():
        try:
            c = connect(a.db)
            installed = c.execute("SELECT 1 FROM prediction_runs WHERE kind='backtest' LIMIT 1").fetchone() is not None
            c.close()
        except Exception:  # yarım kalmış/boş dosya: yeniden kurulur
            installed = False
    if installed:
        print("Veritabanı kurulu; yeni sonuçlar ve güncel maç programı alınıyor...")
        cmd_update_data(argparse.Namespace(db=a.db, leagues=None, seasons=2, allow_new=False))
    else:
        print("İlk kurulum: maç verileri indiriliyor ve model kuruluyor. Bu 10-20 dakika sürebilir; pencereyi kapatmayın...")
        cmd_real_data(argparse.Namespace(db=a.db, leagues=a.leagues, seasons=3, full_history=False,
                                         allow_partial=False))


def cmd_serve(a) -> None:
    import uvicorn

    from .api.app import create_app

    uvicorn.run(create_app(a.db), host=a.host, port=a.port)


def cmd_window_radar(a) -> None:
    """Son 15 Dakika Radarı: planı göster (--plan) veya ön planda çalıştır. İstekler bütçe kapılarından geçer."""
    import time

    from .data.api_football import Client
    from .db.database import connect, init_db, to_iso, utcnow
    from .radar import window_radar as W

    conn = connect(a.db)
    init_db(conn)
    if a.threshold is not None:
        W.update_config(conn, {"threshold": a.threshold}, to_iso(utcnow()))
    st = W.status(conn, to_iso(utcnow()))
    b = st["budget"]
    print(f"Eşik: %{st['config']['threshold'] * 100:.0f} ve üstü · bugün istek {b['used']}/{b['daily_limit']}"
          f" (kullanılabilir {b['usable']})")
    for t in st["targets"]:
        if t["status"] in ("pending", "gated", "no_model") and t["fixture"]:
            fx = t["fixture"]
            print(f"  {t['due_at']}  {fx['home']} – {fx['away']}  {t['window']}  maç öncesi "
                  f"%{100 * (t['prematch_probability'] or 0):.0f}  → {'İSTEK' if t['will_request'] else 'istek yok'}")
    if a.plan:
        return
    client = Client()
    print("Radar çalışıyor (Ctrl+C ile durdurun)…")
    while True:
        out = W.tick(conn, client)
        if out.get("requests") or out.get("alerts"):
            print(to_iso(utcnow()), f"istek: {out['requests']}, değerlendirme: {len(out.get('evaluations', []))},"
                                    f" bildirim: {len(out.get('alerts', []))}")
        time.sleep(30)


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
    sp.add_argument("--leagues", default="tr-super-lig,en-premier-league,es-la-liga,de-bundesliga",
                    help="Virgülle ayrılmış organizasyon kodları (sentetik demo)")
    sp = add("run", cmd_run, "Yaklaşan maçları değerlendir")
    sp.add_argument("--as-of", default=None)
    sp = add("backtest", cmd_backtest, "Geriye dönük test")
    sp.add_argument("--until", default=None)
    sp = add("import-csv", cmd_import, "CSV içe aktar")
    sp.add_argument("file")
    sp.add_argument("--league", required=True)
    sp.add_argument("--season", required=True)
    leagues_help = ("football-data.co.uk kodları (T1=Süper Lig, E0, E1, SP1, I1, D1, F1, N1, P1, B1, SC0, G1, "
                    "AUT, SWZ, DNK, BRA)")
    sp = add("real-data", cmd_real_data, "Ücretsiz gerçek veriyle kur")
    sp.add_argument("--leagues", default="T1,E0,SP1,I1,D1,F1", help=leagues_help)
    sp.add_argument("--seasons", type=int, default=3, help="Geçmiş + güncel sezon sayısı (en az 1)")
    sp.add_argument("--full-history", action="store_true",
                    help="Geriye dönük testte uygun olmayan adayları ve retleri de kaydet (büyük dosya)")
    sp.add_argument("--allow-partial", action="store_true", help="Bazı dosyalar indirilemese de kur")
    sp = add("update-data", cmd_update_data, "Gerçek veriyi güncelle")
    sp.add_argument("--leagues", default=None, help="Varsayılan: kurulumdaki ligler. " + leagues_help)
    sp.add_argument("--seasons", type=int, default=2, help="Güncel + önceki sezon (sezon geçişi için)")
    sp.add_argument("--allow-new", action="store_true", help="Geriye dönük testi yapılmamış lig eklemeye izin ver")
    sp = add("kurulum", cmd_setup, "Tek komutla kur veya güncelle (gerçek veri)")
    sp.add_argument("--leagues", default="T1,E0,SP1,I1,D1,F1", help="İlk kurulumda ligler. " + leagues_help)
    sp = add("serve", cmd_serve, "API ve arayüzü başlat")
    sp.add_argument("--host", default="127.0.0.1")
    sp.add_argument("--port", type=int, default=8000)
    sp = add("window-radar", cmd_window_radar, "Son 15 dakika radarı (API-Football)")
    sp.add_argument("--plan", action="store_true", help="Yalnızca istek planını göster (istek göndermez)")
    sp.add_argument("--threshold", type=float, default=None, help="Eşik (0,30–0,99); varsayılan kayıtlı ayar (0,60)")
    a = p.parse_args(argv)
    a.fn(a)


if __name__ == "__main__":
    main()
