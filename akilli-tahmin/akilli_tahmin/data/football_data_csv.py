"""football-data.co.uk biçimli geçmiş sonuç/oran CSV içe aktarıcısı.

Gerçek geçmiş veriyle kronolojik geriye dönük test yapılabilmesi içindir.
- Sonuçlar: Date, Time (varsa), HomeTeam, AwayTeam, FTHG, FTAG, HTHG, HTAG
- Oranlar (varsa): B365H/B365D/B365A (Maç Sonucu), B365>2.5 / B365<2.5 (Alt/Üst 2,5)
Oranların toplandığı an CSV'de yoktur; başlama saatinden 24 saat önce yakalanmış kabul edilir ve
`timestamp_precision = 'approximate'` olarak işaretlenir. Bu oranlar yalnızca geçmiş testte kullanılabilir;
güncel öneri üretiminde reddedilir. Kaynak etiketi "football-data.co.uk geçmiş oranları"dır, İddaa değildir.
"""
from __future__ import annotations

import csv
import sqlite3
from datetime import datetime, timedelta, timezone
from pathlib import Path

from ..db.database import insert, insert_many, to_iso, transaction

SOURCE = "football-data-csv"
ODDS_COLUMNS = {
    ("1X2", "1"): ("B365H", "PSH", "AvgH"),
    ("1X2", "X"): ("B365D", "PSD", "AvgD"),
    ("1X2", "2"): ("B365A", "PSA", "AvgA"),
    ("OU_2.5", "OVER"): ("B365>2.5", "P>2.5", "Avg>2.5"),
    ("OU_2.5", "UNDER"): ("B365<2.5", "P<2.5", "Avg<2.5"),
}


def _parse_dt(date: str, time: str | None) -> datetime:
    for fmt in ("%d/%m/%Y", "%d/%m/%y"):
        try:
            d = datetime.strptime(date.strip(), fmt)
            break
        except ValueError:
            continue
    else:
        raise ValueError(f"Tarih çözümlenemedi: {date}")
    hh, mm = 15, 0
    if time:
        try:
            hh, mm = (int(x) for x in time.strip().split(":")[:2])
        except ValueError:
            pass
    # football-data saatleri İngiltere saatidir; yaklaşık UTC kabul edilir (oran zamanı zaten yaklaşık).
    return d.replace(hour=hh, minute=mm, tzinfo=timezone.utc)


def import_csv(conn: sqlite3.Connection, path: str | Path, league_id: str, season: str) -> dict:
    n_fx = n_odds = 0
    with open(path, newline="", encoding="utf-8-sig") as fh:
        rows = list(csv.DictReader(fh))
    with transaction(conn):
        team_ids: dict[str, int] = {}

        def team(name: str) -> int:
            if name not in team_ids:
                r = conn.execute("SELECT id FROM teams WHERE league_id=? AND name=?", (league_id, name)).fetchone()
                team_ids[name] = r[0] if r else insert(conn, "teams", {"league_id": league_id, "name": name})
            return team_ids[name]

        for row in rows:
            if not row.get("HomeTeam") or row.get("FTHG") in (None, ""):
                continue
            ko = _parse_dt(row["Date"], row.get("Time"))
            ht, at = team(row["HomeTeam"].strip()), team(row["AwayTeam"].strip())
            exists = conn.execute("SELECT id FROM fixtures WHERE league_id=? AND home_team_id=? AND away_team_id=?"
                                  " AND kickoff_at=?", (league_id, ht, at, to_iso(ko))).fetchone()
            if exists:
                continue
            hthg, htag = row.get("HTHG"), row.get("HTAG")
            fid = insert(conn, "fixtures", {
                "league_id": league_id, "season": season, "round": None, "home_team_id": ht, "away_team_id": at,
                "kickoff_at": to_iso(ko), "status": "finished", "ft_home": int(row["FTHG"]), "ft_away": int(row["FTAG"]),
                "ht_home": int(hthg) if hthg not in (None, "") else None,
                "ht_away": int(htag) if htag not in (None, "") else None,
                "result_recorded_at": to_iso(ko + timedelta(hours=2, minutes=5)), "source": SOURCE, "is_synthetic": 0,
                "created_at": to_iso(ko - timedelta(days=7)),
            })
            n_fx += 1
            cap = to_iso(ko - timedelta(hours=24))
            odds_rows = []
            for (mid, oid), cols in ODDS_COLUMNS.items():
                val = next((row.get(c) for c in cols if row.get(c) not in (None, "")), None)
                try:
                    price = float(val) if val is not None else None
                except ValueError:
                    price = None
                if price and price > 1.0:
                    odds_rows.append({"fixture_id": fid, "market_id": mid, "outcome_id": oid, "decimal_odds": price,
                                      "source": SOURCE, "captured_at": cap, "is_live": 0, "minute": None,
                                      "timestamp_precision": "approximate", "ingested_at": cap})
            insert_many(conn, "odds_snapshots", odds_rows)
            n_odds += len(odds_rows)
    return {"fixtures": n_fx, "odds": n_odds, "league_id": league_id, "season": season}
