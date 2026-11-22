"""Ücretsiz gerçek veri kaynağı (football-data.co.uk) — ağ kullanmadan, örnek CSV metinleriyle."""
from __future__ import annotations

import numpy as np
import pytest

from akilli_tahmin.data.football_data_csv import import_rows, parse_kickoff, read_csv_text
from akilli_tahmin.data.football_data_feed import Fetcher, season_codes, sync
from akilli_tahmin.data.point_in_time import latest_quotes
from akilli_tahmin.db.database import connect, init_db, to_iso

AS_OF = "2026-11-22T09:00:00Z"

MAIN_HEADER = "Div,Date,Time,HomeTeam,AwayTeam,FTHG,FTAG,FTR,HTHG,HTAG,HTR,B365H,B365D,B365A,AvgH,AvgD,AvgA,B365>2.5,B365<2.5,B365CH,B365CD,B365CA"
FIX_HEADER = "Div,Date,Time,HomeTeam,AwayTeam,Referee,B365H,B365D,B365A,AvgH,AvgD,AvgA,B365>2.5,B365<2.5"
NEW_HEADER = "Country,League,Season,Date,Time,Home,Away,HG,AG,Res,PSCH,PSCD,PSCA,AvgCH,AvgCD,AvgCA"


@pytest.fixture()
def conn():
    c = connect()
    init_db(c, AS_OF)
    yield c
    c.close()


def test_kickoff_is_converted_from_uk_time():
    assert to_iso(parse_kickoff("09/08/2025", "15:00")) == "2025-08-09T14:00:00Z"  # BST (UTC+1)
    assert to_iso(parse_kickoff("22/11/2026", "13:00")) == "2026-11-22T13:00:00Z"  # GMT
    assert to_iso(parse_kickoff("22/11/26", "")) == "2026-11-22T15:00:00Z"


def test_season_codes():
    assert season_codes("2026-11-22T00:00:00Z", 3) == ["2425", "2526", "2627"]
    assert season_codes("2026-03-01T00:00:00Z", 2) == ["2425", "2526"]


def test_results_and_prematch_odds_are_point_in_time(conn):
    text = "\n".join([
        MAIN_HEADER,
        "T1,08/11/2026,18:00,Besiktas,Genclerbirligi,3,0,H,2,0,H,1.19,6.25,11,1.18,6.1,10.5,1.6,2.3,1.15,7,13",
        "T1,08/11/2026,15:30,Alanyaspor,Trabzonspor,0,2,A,0,2,A,,,,4.6,3.7,1.6,,,4.4,3.7,1.65",
    ])
    res = import_rows(conn, read_csv_text(text), "tr-super-lig", "2026", as_of=AS_OF)
    assert res["inserted"] == 2
    f = conn.execute("SELECT * FROM fixtures WHERE ft_home=3").fetchone()
    assert f["status"] == "finished" and f["kickoff_at"] == "2026-11-08T18:00:00Z"
    assert f["result_recorded_at"] == "2026-11-08T20:05:00Z" and f["ht_home"] == 2 and f["is_synthetic"] == 0
    odds = conn.execute("SELECT market_id, outcome_id, decimal_odds, captured_at, timestamp_precision FROM odds_snapshots"
                        " WHERE fixture_id=? ORDER BY market_id, outcome_id", (f["id"],)).fetchall()
    # Maç öncesi Bet365 oranları kullanılır; kapanış oranı (B365C*) kullanılmaz.
    assert {(o[0], o[1]): o[2] for o in odds}[("1X2", "1")] == 1.19
    assert all(o[3] == "2026-11-07T18:00:00Z" and o[4] == "approximate" for o in odds)
    # Bet365 yoksa bütün seçenekler aynı kaynaktan (piyasa ortalaması) alınır.
    g = conn.execute("SELECT id FROM fixtures WHERE ft_away=2").fetchone()[0]
    p = dict(((m, o), v) for m, o, v in conn.execute(
        "SELECT market_id, outcome_id, decimal_odds FROM odds_snapshots WHERE fixture_id=?", (g,)))
    assert p == {("1X2", "1"): 4.6, ("1X2", "X"): 3.7, ("1X2", "2"): 1.6}
    # tekrar içe aktarma idempotenttir
    again = import_rows(conn, read_csv_text(text), "tr-super-lig", "2026", as_of=AS_OF)
    assert again["inserted"] == 0 and again["odds"] == 0


def test_upcoming_fixture_is_later_settled_in_place(conn):
    fx = "\n".join([FIX_HEADER, "T1,23/11/2026,17:00,Buyuksehyr,Corum,,2.0,3.4,3.9,1.98,3.3,3.8,1.9,1.9",
                    "T1,21/11/2026,13:00,Amedspor,Rizespor,,1.69,3.75,4.2,1.68,3.9,4.3,1.57,2.35"])
    res = import_rows(conn, read_csv_text(fx), "tr-super-lig", "2026", as_of=AS_OF, upcoming=True)
    assert res["inserted"] == 1  # başlamış maç yaklaşan maç olarak alınmaz
    f = conn.execute("SELECT * FROM fixtures").fetchone()
    assert f["status"] == "scheduled" and f["ft_home"] is None
    caps = {r[0] for r in conn.execute("SELECT captured_at FROM odds_snapshots WHERE fixture_id=?", (f["id"],))}
    assert caps == {AS_OF}
    later = "2026-11-25T09:00:00Z"
    season = "\n".join([MAIN_HEADER, "T1,23/11/2026,17:00,Buyuksehyr,Corum,1,1,D,0,1,A,2.0,3.4,3.9,1.98,3.3,3.8,1.9,1.9,,,"])
    res2 = import_rows(conn, read_csv_text(season), "tr-super-lig", "2026", as_of=later)
    assert res2["updated"] == 1 and res2["inserted"] == 0
    g = conn.execute("SELECT * FROM fixtures WHERE id=?", (f["id"],)).fetchone()
    assert g["status"] == "finished" and (g["ft_home"], g["ft_away"], g["ht_away"]) == (1, 1, 1)
    assert conn.execute("SELECT COUNT(*) FROM data_revisions WHERE entity='fixture' AND entity_id=? AND field='result'",
                        (f["id"],)).fetchone()[0] == 1


def test_closing_only_odds_never_usable_before_kickoff(conn):
    text = "\n".join([NEW_HEADER, "Austria,Bundesliga,2026/2027,08/11/2026,16:00,Sturm Graz,Salzburg,3,4,A,2.6,3.8,2.5,2.5,3.6,2.4"])
    import_rows(conn, read_csv_text(text), "at-bundesliga", "2026", as_of=AS_OF)
    f = conn.execute("SELECT * FROM fixtures").fetchone()
    assert f["season"] == "2026" and f["ht_home"] is None
    cap = conn.execute("SELECT DISTINCT captured_at FROM odds_snapshots").fetchone()[0]
    assert cap == f["kickoff_at"]  # kapanış oranı başlama anına tarihlenir
    before = latest_quotes(conn, [f["id"]], "2026-11-08T14:30:00Z")
    assert before == {}  # maç öncesi kesimde görünmez (sızıntı yok)


def test_unknown_league_rejected(conn):
    with pytest.raises(ValueError):
        import_rows(conn, [], "E0", "2026", as_of=AS_OF)


class FakeFetcher(Fetcher):
    def __init__(self, files: dict[str, str]):
        super().__init__(None)
        self.files = files
        self.calls: list[str] = []

    def get(self, path: str, *, cache: bool) -> str:
        self.calls.append(path)
        if path not in self.files:
            raise RuntimeError(f"{path} yok")
        return self.files[path]


def test_sync_with_fake_downloads(conn):
    files = {
        "mmz4281/2526/T1.csv": "\n".join([MAIN_HEADER, "T1,10/05/2026,18:00,Galatasaray,Fenerbahce,2,1,H,1,0,H,2.1,3.5,3.3,2.05,3.4,3.2,1.7,2.1,,,"]),
        "mmz4281/2627/T1.csv": "\n".join([MAIN_HEADER, "T1,08/11/2026,18:00,Besiktas,Genclerbirligi,3,0,H,2,0,H,1.19,6.25,11,1.18,6.1,10.5,1.6,2.3,,,"]),
        "fixtures.csv": "\n".join([FIX_HEADER, "T1,23/11/2026,17:00,Buyuksehyr,Corum,,2.0,3.4,3.9,1.98,3.3,3.8,1.9,1.9",
                                   "E0,23/11/2026,16:30,Arsenal,Chelsea,,1.9,3.6,4.0,1.9,3.5,3.9,1.8,2.0"]),
        "new/AUT.csv": "\n".join([NEW_HEADER, "Austria,Bundesliga,2026/2027,08/11/2026,16:00,Sturm Graz,Salzburg,3,4,A,2.6,3.8,2.5,2.5,3.6,2.4",
                                  "Austria,Bundesliga,2012/2013,21/07/2012,15:00,Sturm Graz,Salzburg,0,2,A,3.2,3.3,2.4,3.0,3.2,2.3"]),
        "new_league_fixtures.csv": "Country,League,Date,Time,Home,Away,PSH,PSD,PSA,AvgH,AvgD,AvgA,B365H,B365D,B365A\n"
                                   "Austria,Bundesliga,29/11/2026,16:00,Rapid Vienna,Salzburg,2.9,3.4,2.4,2.8,3.4,2.4,2.8,3.4,2.4",
    }
    f = FakeFetcher(files)
    rep = sync(conn, as_of=AS_OF, codes=("T1",), seasons=2, include_extra=("AUT",), fetcher=f)
    assert rep["errors"] == []
    counts = dict(conn.execute("SELECT league_id, COUNT(*) FROM fixtures GROUP BY league_id").fetchall())
    assert counts == {"tr-super-lig": 3, "at-bundesliga": 2}  # 2012 sezonu ve başka ligler alınmaz
    assert conn.execute("SELECT COUNT(*) FROM fixtures WHERE status='scheduled'").fetchone()[0] == 2
    assert "mmz4281/2627/T1.csv" in f.calls and "fixtures.csv" in f.calls


def test_lean_backtest_skips_ineligible_rows(tmp_path):
    from akilli_tahmin.backtest.walk_forward import run_backtest
    from akilli_tahmin.data.synthetic import generate_demo

    c = connect(tmp_path / "lean.sqlite3")
    init_db(c, "2026-11-21T17:00:00Z")
    generate_demo(c, "2026-11-21T17:00:00Z", leagues=("tr-super-lig",), history_seasons=1, live_fixtures=False)
    run_backtest(c, "2026-11-21T17:00:00Z", n_draws=40, lean=True)
    n_eval = c.execute("SELECT COUNT(*) FROM candidate_evaluations").fetchone()[0]
    n_cand = c.execute("SELECT COUNT(*) FROM selection_mode_candidates").fetchone()[0]
    n_inel = c.execute("SELECT COUNT(*) FROM selection_mode_candidates WHERE eligible=0").fetchone()[0]
    assert n_eval > 0 and n_inel == 0 and n_cand < n_eval
    # Kararların her biri denetimden geçmiş bir aday satırına bağlıdır.
    orphan = c.execute("SELECT COUNT(*) FROM selection_mode_decisions d LEFT JOIN selection_mode_candidates m"
                       " ON m.id=d.candidate_id WHERE m.id IS NULL").fetchone()[0]
    assert orphan == 0
    assert np.isfinite(n_eval)


# ───────────── inceleme düzeltmeleri: ertelenen maçlar, önbellek, karışık fiyat, lig seçimi ─────────────

def _fx_row(date, time_, home, away, o=(2.0, 3.4, 3.9)):
    return f"T1,{date},{time_},{home},{away},,{o[0]},{o[1]},{o[2]},{o[0]},{o[1]},{o[2]},1.9,1.9"


def test_rescheduled_match_keeps_one_row_and_postponed_is_voided(conn):
    from akilli_tahmin.data.football_data_csv import mark_postponed

    fx1 = "\n".join([FIX_HEADER, _fx_row("29/11/2026", "16:00", "Fenerbahce", "Galatasaray"),
                     _fx_row("23/11/2026", "16:00", "Trabzonspor", "Eyupspor")])
    import_rows(conn, read_csv_text(fx1), "tr-super-lig", "2026", as_of=AS_OF, upcoming=True)
    # Derbi 4 gün ileri alındı: yeni kayıt değil, aynı kayıt taşınır.
    fx2 = "\n".join([FIX_HEADER, _fx_row("03/12/2026", "16:00", "Fenerbahce", "Galatasaray")])
    later = "2026-11-27T09:00:00Z"
    import_rows(conn, read_csv_text(fx2), "tr-super-lig", "2026", as_of=later, upcoming=True)
    rows = conn.execute("SELECT kickoff_at, status FROM fixtures WHERE home_team_id=(SELECT id FROM teams WHERE"
                        " name='Fenerbahce')").fetchall()
    assert [tuple(r) for r in rows] == [("2026-12-03T16:00:00Z", "scheduled")]
    # Oynanmayan maç 3 gün sonra ertelenmiş sayılır; kararları iade edilebilir.
    assert mark_postponed(conn, "tr-super-lig", later) == 1
    st = conn.execute("SELECT status, result_recorded_at FROM fixtures WHERE home_team_id=(SELECT id FROM teams"
                      " WHERE name='Trabzonspor')").fetchone()
    assert st["status"] == "postponed" and st["result_recorded_at"] == later
    from akilli_tahmin.domain.markets import MatchResult, settle

    assert settle("1X2", "1", MatchResult("postponed", None, None)) == "void"


def test_kickoff_moved_earlier_and_passed_drops_from_upcoming(conn):
    import_rows(conn, read_csv_text("\n".join([FIX_HEADER, _fx_row("23/11/2026", "17:00", "Buyuksehyr", "Corum")])),
                "tr-super-lig", "2026", as_of=AS_OF, upcoming=True)
    import_rows(conn, read_csv_text("\n".join([FIX_HEADER, _fx_row("22/11/2026", "13:00", "Buyuksehyr", "Corum")])),
                "tr-super-lig", "2026", as_of="2026-11-22T20:00:00Z", upcoming=True)
    ko = conn.execute("SELECT kickoff_at FROM fixtures").fetchone()[0]
    assert ko == "2026-11-22T13:00:00Z"  # artık yaklaşan maç değil


def test_odds_are_written_per_market_snapshot(conn):
    up = lambda o, at: import_rows(conn, read_csv_text("\n".join([FIX_HEADER, _fx_row("29/11/2026", "16:00", "A", "B", o)])),
                                   "tr-super-lig", "2026", as_of=at, upcoming=True)
    up((2.0, 3.4, 3.9), "2026-11-20T09:00:00Z")
    up((1.8, 3.4, 4.6), "2026-11-23T09:00:00Z")  # yalnızca 1 ve 2 değişti
    fid = conn.execute("SELECT id FROM fixtures").fetchone()[0]
    q = latest_quotes(conn, [fid], "2026-11-29T15:00:00Z")[fid]
    caps = {q[("1X2", o)].captured_at for o in ("1", "X", "2")}
    assert caps == {"2026-11-23T09:00:00Z"}  # aynı anlık görüntüden üç fiyat
    assert [q[("1X2", o)].decimal_odds for o in ("1", "X", "2")] == [1.8, 3.4, 4.6]


def test_bad_rows_and_foreign_rows_do_not_abort(conn, tmp_path):
    from akilli_tahmin.data.football_data_csv import import_csv

    text = "\n".join([MAIN_HEADER,
                      "T1,31/11/2026,18:00,A,B,1,0,H,0,0,D,2,3,4,2,3,4,1.9,1.9,,,",
                      "T1,01/11/2026,24:00,A,B,1,0,H,0,0,D,2,3,4,2,3,4,1.9,1.9,,,",
                      "E0,01/11/2026,15:00,Arsenal,Chelsea,1,0,H,0,0,D,2,3,4,2,3,4,1.9,1.9,,,",
                      "T1,02/11/2026,18:00,C,D,2,2,D,1,1,D,2,3,4,2,3,4,1.9,1.9,,,"])
    res = import_rows(conn, read_csv_text(text), "tr-super-lig", "2026", as_of=AS_OF)
    assert res["invalid"] == 2 and res["foreign"] == 1 and res["inserted"] == 1
    p = tmp_path / "e0.csv"
    p.write_text("\n".join([MAIN_HEADER, "E0,01/11/2026,15:00,Arsenal,Chelsea,1,0,H,0,0,D,2,3,4,2,3,4,1.9,1.9,,,"]))
    with pytest.raises(ValueError):
        import_csv(conn, p, "tr-super-lig", "2026")


def test_current_season_is_never_cached(tmp_path, monkeypatch):
    import io
    import urllib.request

    served = {"v": "Div,Date\nT1,10/05/2026\n"}

    class Resp(io.BytesIO):
        def __enter__(self):
            return self

        def __exit__(self, *a):
            return False

    monkeypatch.setattr(urllib.request, "urlopen", lambda req, timeout=0: Resp(served["v"].encode()))
    f = Fetcher(tmp_path)
    f.get("mmz4281/2526/T1.csv", cache=False)  # sezon sürerken
    assert not list(tmp_path.iterdir())
    served["v"] = "Div,Date\nT1,10/05/2026\nT1,17/05/2026\n"
    text = f.get("mmz4281/2526/T1.csv", cache=True)  # sezon bittikten sonra ilk okuma güncel dosyayı indirir
    assert "17/05/2026" in text and len(list(tmp_path.iterdir())) == 1


def test_extra_league_rows_filtered_by_league_and_brazil_calendar_season(conn):
    from akilli_tahmin.data.football_data_feed import EXTRA_LEAGUES

    assert EXTRA_LEAGUES["SWZ"][1] == "Super League"
    files = {
        "new/SWZ.csv": "\n".join([NEW_HEADER,
                                  "Switzerland,Super League,2026/2027,01/11/2026,16:00,Basel,Young Boys,1,1,D,2,3,3,2,3,3",
                                  "Switzerland,Challenge League,2026/2027,02/11/2026,16:00,Thun,Sion,2,0,H,2,3,3,2,3,3"]),
        "new_league_fixtures.csv": "Country,League,Date,Time,Home,Away,PSH,PSD,PSA,AvgH,AvgD,AvgA,B365H,B365D,B365A\n"
                                   "Brazil,Serie A,14/02/2027,20:00,Flamengo RJ,Gremio,1.6,3.9,5.5,1.6,3.9,5.5,1.6,3.9,5.5",
        "new/BRA.csv": NEW_HEADER + "\n",
    }
    sync(conn, as_of="2027-02-10T09:00:00Z", codes=(), seasons=1, include_extra=("SWZ", "BRA"), fetcher=FakeFetcher(files))
    names = {r[0] for r in conn.execute("SELECT name FROM teams WHERE league_id='ch-super-league'")}
    assert names == {"Basel", "Young Boys"}
    assert conn.execute("SELECT season FROM fixtures WHERE league_id='br-serie-a'").fetchone()[0] == "2027"


def test_server_refresh_uses_configured_leagues_only(tmp_path, monkeypatch):
    from akilli_tahmin.data import football_data_feed as feed
    from akilli_tahmin.runtime import Runtime

    db = tmp_path / "r.sqlite3"
    c = connect(db)
    init_db(c, AS_OF)
    feed.save_config(c, (), ("AUT",), AS_OF)
    c.close()
    asked: list[str] = []

    def fake_get(self, path, *, cache):
        asked.append(path)
        if path == "new/AUT.csv":
            return NEW_HEADER + "\nAustria,Bundesliga,2026/2027,08/11/2026,16:00,Sturm Graz,Salzburg,3,4,A,2.6,3.8,2.5,2.5,3.6,2.4"
        return "Country,League,Date,Time,Home,Away\n"

    monkeypatch.setattr(feed.Fetcher, "get", fake_get)
    rt = Runtime(str(db))
    assert rt.refresh_real_if_stale(AS_OF, background=False) is True
    assert asked and all("AUT" in p or p == "new_league_fixtures.csv" for p in asked)  # T1/E0... istenmez
    assert rt.last_refresh_report["ok"] is True
    # Başarısız/yakın tarihli denemeden sonra bir saat içinde yeniden denenmez.
    assert rt.refresh_real_if_stale("2026-11-22T09:30:00Z", background=False) is False


def test_halftime_markets_blocked_without_halftime_data(tmp_path):
    from akilli_tahmin.data.synthetic import generate_demo
    from akilli_tahmin.selection.engine import Engine

    c = connect(tmp_path / "ht.sqlite3")
    init_db(c, "2026-11-21T17:00:00Z")
    generate_demo(c, "2026-11-21T17:00:00Z", leagues=("tr-super-lig",), history_seasons=1, live_fixtures=False)
    c.execute("UPDATE fixtures SET ht_home=NULL, ht_away=NULL")
    Engine(c, n_draws=30).run("2026-11-21T17:00:00Z")
    rows = c.execute("SELECT market_id, gate_failed FROM candidate_evaluations").fetchall()
    assert rows
    for r in rows:
        failed = {x[0] for x in __import__("json").loads(r["gate_failed"])}
        assert ("halftime_data" in failed) == r["market_id"].startswith(("HT_", "HTFT"))
