"""football-data.co.uk biçimli sonuç/oran CSV içe aktarıcısı (ücretsiz, herkese açık dosyalar).

Desteklenen biçimler:
- Ana biçim (ör. `mmz4281/2627/T1.csv`, `fixtures.csv`): Div, Date, Time, HomeTeam, AwayTeam, FTHG, FTAG,
  HTHG, HTAG ve maç öncesi oranlar (B365H/D/A, B365>2.5/<2.5; yoksa piyasa ortalaması Avg*, Pinnacle P*).
- Ek lig biçimi (ör. `new/AUT.csv`, `new_league_fixtures.csv`): Country, League, Season, Date, Time, Home,
  Away, HG, AG ve yalnızca *kapanış* oranları (geçmiş dosyalarda) veya güncel oranlar (fikstür dosyasında).

Zaman kuralları (geçmiş zaman doğruluğu):
- Saatler İngiltere yerel saatidir (Europe/London); UTC'ye çevrilir.
- Sonucun maçtan 2 sa 5 dk sonra bilindiği kabul edilir (`result_recorded_at`).
- Maç öncesi oranların yakalanma anı dosyada yoktur: başlama saatinden 24 saat önce kabul edilir ve
  `timestamp_precision = 'approximate'` işaretlenir. Kapanış oranları başlama anında yakalanmış sayılır;
  bu nedenle maçtan önceki bir zaman kesiminde hiçbir zaman kullanılamaz (sızıntı yok).
- Yaklaşan maç dosyasındaki oranlar indirme anına tarihlenir ve yaklaşık işaretlenir. Yaklaşık oranlar
  güncel öneride reddedilir (oran gerektiren modlar seçim yapmaz), yalnızca geriye dönük testte kullanılır.
Kaynak etiketi "football-data.co.uk"tur; İddaa oranı değildir.
"""
from __future__ import annotations

import csv
import io
import sqlite3
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

from ..db.database import insert, insert_many, to_iso, transaction

SOURCE = "football-data-csv"
UK = ZoneInfo("Europe/London")
RESULT_DELAY = timedelta(hours=2, minutes=5)
PREMATCH_CAPTURE_LEAD = timedelta(hours=24)

#: Bir marketin bütün seçenekleri aynı kaynaktan alınır (marjdan arındırma için); öncelik sırası.
PREMATCH_ODDS = {
    "1X2": (("1", "X", "2"), (("B365H", "B365D", "B365A"), ("AvgH", "AvgD", "AvgA"), ("PSH", "PSD", "PSA"))),
    "OU_2.5": (("OVER", "UNDER"), (("B365>2.5", "B365<2.5"), ("Avg>2.5", "Avg<2.5"), ("P>2.5", "P<2.5"))),
}
CLOSING_ODDS = {
    "1X2": (("1", "X", "2"), (("B365CH", "B365CD", "B365CA"), ("AvgCH", "AvgCD", "AvgCA"),
                              ("PSCH", "PSCD", "PSCA"))),
}


def parse_kickoff(date: str, time: str | None) -> datetime:
    """İngiltere yerel tarih/saatini UTC'ye çevirir (saat yoksa 15:00 kabul edilir)."""
    for fmt in ("%d/%m/%Y", "%d/%m/%y"):
        try:
            d = datetime.strptime(date.strip(), fmt)
            break
        except ValueError:
            continue
    else:
        raise ValueError(f"Tarih çözümlenemedi: {date}")
    hh, mm = 15, 0
    if time and time.strip():
        try:
            hh, mm = (int(x) for x in time.strip().split(":")[:2])
        except ValueError:
            pass
    return d.replace(hour=hh, minute=mm, tzinfo=UK).astimezone(timezone.utc)


_parse_dt = parse_kickoff  # geriye dönük uyumluluk


def _num(v) -> float | None:
    try:
        x = float(v)
    except (TypeError, ValueError):
        return None
    return x if x > 1.0 else None


def _int(v) -> int | None:
    try:
        return int(float(v))
    except (TypeError, ValueError):
        return None


def _market_quotes(row: dict, spec: dict) -> dict[tuple[str, str], float]:
    out: dict[tuple[str, str], float] = {}
    for market, (outcomes, candidates) in spec.items():
        for cols in candidates:
            prices = [_num(row.get(c)) for c in cols]
            if all(p is not None for p in prices):
                for oid, p in zip(outcomes, prices):
                    out[(market, oid)] = p
                break
    return out


def _normalize(row: dict) -> dict | None:
    """İki biçimi ortak alanlara indirger. Dönüş None ise satır atlanır."""
    home = (row.get("HomeTeam") or row.get("Home") or "").strip()
    away = (row.get("AwayTeam") or row.get("Away") or "").strip()
    date = (row.get("Date") or "").strip()
    if not home or not away or not date:
        return None
    fh = _int(row.get("FTHG", row.get("HG")))
    fa = _int(row.get("FTAG", row.get("AG")))
    return {
        "home": home, "away": away, "kickoff": parse_kickoff(date, row.get("Time")),
        "ft_home": fh, "ft_away": fa, "ht_home": _int(row.get("HTHG")), "ht_away": _int(row.get("HTAG")),
        "prematch": _market_quotes(row, PREMATCH_ODDS), "closing": _market_quotes(row, CLOSING_ODDS),
        "season_label": (row.get("Season") or "").strip(),
    }


def _season_from_label(label: str, fallback: str) -> str:
    if label:
        return label.split("/")[0].strip()
    return fallback


class _Importer:
    def __init__(self, conn: sqlite3.Connection, league_id: str, as_of: str):
        self.conn = conn
        self.league_id = league_id
        self.as_of = as_of
        self.team_ids: dict[str, int] = {}
        self.stats = {"inserted": 0, "updated": 0, "unchanged": 0, "odds": 0}

    def team(self, name: str) -> int:
        if name not in self.team_ids:
            r = self.conn.execute("SELECT id FROM teams WHERE league_id=? AND name=?", (self.league_id, name)).fetchone()
            self.team_ids[name] = r[0] if r else insert(self.conn, "teams", {"league_id": self.league_id, "name": name})
        return self.team_ids[name]

    def upsert(self, rec: dict, season: str) -> int:
        """Aynı maç (lig, ev, deplasman, ±2 gün) varsa günceller; yoksa ekler. Dönüş: fikstür kimliği."""
        ht, at = self.team(rec["home"]), self.team(rec["away"])
        ko = rec["kickoff"]
        lo, hi = to_iso(ko - timedelta(days=2)), to_iso(ko + timedelta(days=2))
        row = self.conn.execute(
            "SELECT id, status, kickoff_at FROM fixtures WHERE league_id=? AND home_team_id=? AND away_team_id=?"
            " AND kickoff_at BETWEEN ? AND ? ORDER BY kickoff_at LIMIT 1", (self.league_id, ht, at, lo, hi),
        ).fetchone()
        has_result = rec["ft_home"] is not None and rec["ft_away"] is not None
        if row is None:
            fid = insert(self.conn, "fixtures", {
                "league_id": self.league_id, "season": season, "round": None, "home_team_id": ht,
                "away_team_id": at, "kickoff_at": to_iso(ko), "status": "finished" if has_result else "scheduled",
                "ft_home": rec["ft_home"], "ft_away": rec["ft_away"],
                "ht_home": rec["ht_home"] if has_result else None, "ht_away": rec["ht_away"] if has_result else None,
                "result_recorded_at": to_iso(ko + RESULT_DELAY) if has_result else None,
                "source": SOURCE, "is_synthetic": 0, "created_at": self.as_of,
            })
            self.stats["inserted"] += 1
            return fid
        fid, status, old_ko = row[0], row[1], row[2]
        if has_result and status != "finished":
            self.conn.execute(
                "UPDATE fixtures SET status='finished', kickoff_at=?, ft_home=?, ft_away=?, ht_home=?, ht_away=?,"
                " result_recorded_at=? WHERE id=?",
                (to_iso(ko), rec["ft_home"], rec["ft_away"], rec["ht_home"], rec["ht_away"],
                 to_iso(ko + RESULT_DELAY), fid))
            self.conn.execute(
                "INSERT INTO data_revisions(entity, entity_id, field, old_value, new_value, recorded_at)"
                " VALUES ('fixture', ?, 'result', ?, ?, ?)",
                (fid, status, f"{rec['ft_home']}-{rec['ft_away']}", self.as_of))
            self.stats["updated"] += 1
        elif not has_result and status == "scheduled" and old_ko != to_iso(ko):
            self.conn.execute("UPDATE fixtures SET kickoff_at=? WHERE id=?", (to_iso(ko), fid))
            self.conn.execute(
                "INSERT INTO data_revisions(entity, entity_id, field, old_value, new_value, recorded_at)"
                " VALUES ('fixture', ?, 'kickoff_at', ?, ?, ?)", (fid, old_ko, to_iso(ko), self.as_of))
            self.stats["updated"] += 1
        else:
            self.stats["unchanged"] += 1
        return fid

    def add_odds(self, fid: int, quotes: dict[tuple[str, str], float], captured_at: str) -> None:
        rows = []
        for (mid, oid), price in quotes.items():
            last = self.conn.execute(
                "SELECT decimal_odds, captured_at FROM odds_snapshots WHERE fixture_id=? AND market_id=? AND outcome_id=?"
                " AND source=? ORDER BY captured_at DESC LIMIT 1", (fid, mid, oid, SOURCE)).fetchone()
            if last is not None and (abs(last[0] - price) < 1e-9 or last[1] >= captured_at):
                continue  # aynı fiyat veya daha yeni anlık görüntü zaten var
            rows.append({"fixture_id": fid, "market_id": mid, "outcome_id": oid, "decimal_odds": price,
                         "source": SOURCE, "captured_at": captured_at, "is_live": 0, "minute": None,
                         "timestamp_precision": "approximate", "ingested_at": self.as_of})
        insert_many(self.conn, "odds_snapshots", rows)
        self.stats["odds"] += len(rows)


def import_rows(conn: sqlite3.Connection, rows: list[dict], league_id: str, season: str, *, as_of: str,
                upcoming: bool = False) -> dict:
    """Satırları içe aktarır. `upcoming=True`: yaklaşan maç dosyası (oranlar indirme anına tarihlenir)."""
    from ..leagues import LEAGUES_BY_ID

    if league_id not in LEAGUES_BY_ID:
        valid = ", ".join(sorted(LEAGUES_BY_ID))
        raise ValueError(f"Bilinmeyen organizasyon kodu: {league_id!r}. Geçerli kodlar: {valid}")
    imp = _Importer(conn, league_id, as_of)
    with transaction(conn):
        for raw in rows:
            rec = _normalize(raw)
            if rec is None:
                continue
            if rec["ft_home"] is None and not upcoming:
                continue  # sonuç dosyasında sonucu olmayan satır (ertelenmiş vb.)
            if upcoming and rec["kickoff"] <= datetime.fromisoformat(as_of.replace("Z", "+00:00")):
                continue  # başlamış veya geçmiş maç yaklaşan maç sayılmaz
            fid = imp.upsert(rec, _season_from_label(rec["season_label"], season))
            ko = rec["kickoff"]
            if upcoming:
                cap = as_of  # yaklaşık: dosyadaki fiyatların gerçek yakalanma anı bilinmiyor (≤ indirme anı)
                imp.add_odds(fid, rec["prematch"], cap)
            else:
                cap = to_iso(min(ko - PREMATCH_CAPTURE_LEAD, datetime.fromisoformat(as_of.replace("Z", "+00:00"))))
                if rec["prematch"]:
                    imp.add_odds(fid, rec["prematch"], cap)
                elif rec["closing"]:
                    # yalnızca kapanış oranı varsa: başlama anına tarihlenir, maç öncesi kesimde kullanılamaz
                    imp.add_odds(fid, rec["closing"], to_iso(ko))
    return {**imp.stats, "league_id": league_id, "season": season}


def read_csv_text(text: str) -> list[dict]:
    return list(csv.DictReader(io.StringIO(text.lstrip("﻿"))))


def import_csv(conn: sqlite3.Connection, path: str | Path, league_id: str, season: str, *,
               as_of: str | None = None) -> dict:
    """Yerel bir football-data.co.uk CSV dosyasını içe aktarır (geçmiş sonuçlar)."""
    from ..db.database import utcnow

    with open(path, newline="", encoding="utf-8-sig") as fh:
        rows = list(csv.DictReader(fh))
    res = import_rows(conn, rows, league_id, season, as_of=as_of or to_iso(utcnow()))
    res["fixtures"] = res["inserted"]
    return res
