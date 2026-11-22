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
#: Aynı eşleşmenin ertelenmiş/öne alınmış planlı kaydı bu kadar gün içinde aranır.
RESCHEDULE_WINDOW = timedelta(days=45)
#: Takvim yılı sezonu kullanan organizasyonlar (sezon etiketi başlama yılıdır).
CALENDAR_YEAR_LEAGUES = frozenset({"br-serie-a"})
#: football-data.co.uk ana biçim bölüm kodu → organizasyon (dosya/organizasyon uyuşma denetimi için).
DIV_TO_LEAGUE = {
    "T1": "tr-super-lig", "E0": "en-premier-league", "E1": "en-championship", "SP1": "es-la-liga",
    "I1": "it-serie-a", "D1": "de-bundesliga", "F1": "fr-ligue-1", "N1": "nl-eredivisie",
    "P1": "pt-primeira-liga", "B1": "be-pro-league", "SC0": "sco-premiership", "G1": "gr-super-league",
}
COUNTRY_TO_LEAGUE = {"Austria": "at-bundesliga", "Switzerland": "ch-super-league", "Denmark": "dk-superliga",
                     "Brazil": "br-serie-a"}

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
            hh, mm = 15, 0
        if not (0 <= hh <= 23 and 0 <= mm <= 59):
            raise ValueError(f"Saat çözümlenemedi: {time}")
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
        "div": (row.get("Div") or "").strip(), "country": (row.get("Country") or "").strip(),
        "league_name": (row.get("League") or "").strip(),
    }


def _season_for(rec: dict, league_id: str, fallback: str) -> str:
    if rec["season_label"]:
        return rec["season_label"].split("/")[0].strip()
    if league_id in CALENDAR_YEAR_LEAGUES:
        return str(rec["kickoff"].year)
    return fallback


class _Importer:
    def __init__(self, conn: sqlite3.Connection, league_id: str, as_of: str):
        self.conn = conn
        self.league_id = league_id
        self.as_of = as_of
        self.team_ids: dict[str, int] = {}
        self.stats = {"inserted": 0, "updated": 0, "unchanged": 0, "odds": 0, "invalid": 0, "foreign": 0}

    def team(self, name: str) -> int:
        if name not in self.team_ids:
            r = self.conn.execute("SELECT id FROM teams WHERE league_id=? AND name=?", (self.league_id, name)).fetchone()
            self.team_ids[name] = r[0] if r else insert(self.conn, "teams", {"league_id": self.league_id, "name": name})
        return self.team_ids[name]

    def find(self, ht: int, at: int, ko: datetime) -> sqlite3.Row | None:
        """Aynı maçın kaydı: ±2 günde herhangi bir kayıt; yoksa ±45 günde sonucu olmayan planlı kayıt
        (ertelenmiş ya da öne alınmış maç; aynı eşleşme sezonda bir kez daha oynanabilir, en yakını seçilir)."""
        near = self.conn.execute(
            "SELECT id, status, kickoff_at, season FROM fixtures WHERE league_id=? AND home_team_id=? AND away_team_id=?"
            " AND kickoff_at BETWEEN ? AND ? ORDER BY abs(julianday(kickoff_at) - julianday(?)) LIMIT 1",
            (self.league_id, ht, at, to_iso(ko - timedelta(days=2)), to_iso(ko + timedelta(days=2)), to_iso(ko)),
        ).fetchone()
        if near is not None:
            return near
        return self.conn.execute(
            "SELECT id, status, kickoff_at, season FROM fixtures WHERE league_id=? AND home_team_id=? AND away_team_id=?"
            " AND status IN ('scheduled', 'postponed') AND ft_home IS NULL AND kickoff_at BETWEEN ? AND ?"
            " ORDER BY abs(julianday(kickoff_at) - julianday(?)) LIMIT 1",
            (self.league_id, ht, at, to_iso(ko - RESCHEDULE_WINDOW), to_iso(ko + RESCHEDULE_WINDOW), to_iso(ko)),
        ).fetchone()

    def revise(self, fid: int, field: str, old, new) -> None:
        self.conn.execute(
            "INSERT INTO data_revisions(entity, entity_id, field, old_value, new_value, recorded_at)"
            " VALUES ('fixture', ?, ?, ?, ?, ?)", (fid, field, None if old is None else str(old), str(new), self.as_of))

    def reschedule_only(self, rec: dict) -> None:
        """Yaklaşan maç dosyasında başlama saati geçmiş satır: mevcut planlı kaydın saatini düzeltir (eklemez)."""
        ht, at = self.team(rec["home"]), self.team(rec["away"])
        row = self.find(ht, at, rec["kickoff"])
        if row is not None and row["status"] == "scheduled" and row["kickoff_at"] != to_iso(rec["kickoff"]):
            self.conn.execute("UPDATE fixtures SET kickoff_at=? WHERE id=?", (to_iso(rec["kickoff"]), row["id"]))
            self.revise(row["id"], "kickoff_at", row["kickoff_at"], to_iso(rec["kickoff"]))
            self.stats["updated"] += 1

    def upsert(self, rec: dict, season: str) -> int:
        """Aynı maç varsa günceller (sonuç, saat, sezon), yoksa ekler. Dönüş: fikstür kimliği."""
        ht, at = self.team(rec["home"]), self.team(rec["away"])
        ko = rec["kickoff"]
        row = self.find(ht, at, ko)
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
        fid, status, old_ko, old_season = row["id"], row["status"], row["kickoff_at"], row["season"]
        if has_result and status != "finished":
            self.conn.execute(
                "UPDATE fixtures SET status='finished', kickoff_at=?, ft_home=?, ft_away=?, ht_home=?, ht_away=?,"
                " result_recorded_at=?, season=? WHERE id=?",
                (to_iso(ko), rec["ft_home"], rec["ft_away"], rec["ht_home"], rec["ht_away"],
                 to_iso(ko + RESULT_DELAY), season, fid))
            self.revise(fid, "result", status, f"{rec['ft_home']}-{rec['ft_away']}")
            if old_ko != to_iso(ko):
                self.revise(fid, "kickoff_at", old_ko, to_iso(ko))
            self.stats["updated"] += 1
        elif not has_result and status in ("scheduled", "postponed") and (old_ko != to_iso(ko) or status != "scheduled"):
            self.conn.execute("UPDATE fixtures SET kickoff_at=?, status='scheduled' WHERE id=?", (to_iso(ko), fid))
            self.revise(fid, "kickoff_at", old_ko, to_iso(ko))
            self.stats["updated"] += 1
        else:
            if rec["season_label"] and old_season != season:
                self.conn.execute("UPDATE fixtures SET season=? WHERE id=?", (season, fid))
            self.stats["unchanged"] += 1
        return fid

    def add_odds(self, fid: int, quotes: dict[tuple[str, str], float], captured_at: str) -> None:
        """Bir marketin bütün seçenekleri tek anlık görüntü olarak birlikte yazılır (karışık fiyat kümesi oluşmaz)."""
        rows = []
        by_market: dict[str, dict[str, float]] = {}
        for (mid, oid), price in quotes.items():
            by_market.setdefault(mid, {})[oid] = price
        for mid, prices in by_market.items():
            latest = {}
            for oid in prices:
                last = self.conn.execute(
                    "SELECT decimal_odds, captured_at FROM odds_snapshots WHERE fixture_id=? AND market_id=?"
                    " AND outcome_id=? AND source=? ORDER BY captured_at DESC LIMIT 1", (fid, mid, oid, SOURCE)).fetchone()
                latest[oid] = last
            if all(v is not None and v[1] >= captured_at for v in latest.values()):
                continue  # her seçenek için daha yeni anlık görüntü var
            if all(v is not None and abs(v[0] - prices[o]) < 1e-9 for o, v in latest.items()):
                continue  # fiyatlar değişmemiş
            for oid, price in prices.items():
                rows.append({"fixture_id": fid, "market_id": mid, "outcome_id": oid, "decimal_odds": price,
                             "source": SOURCE, "captured_at": captured_at, "is_live": 0, "minute": None,
                             "timestamp_precision": "approximate", "ingested_at": self.as_of})
        insert_many(self.conn, "odds_snapshots", rows)
        self.stats["odds"] += len(rows)


def import_rows(conn: sqlite3.Connection, rows: list[dict], league_id: str, season: str, *, as_of: str,
                upcoming: bool = False, league_name: str | None = None) -> dict:
    """Satırları içe aktarır. `upcoming=True`: yaklaşan maç dosyası (oranlar indirme anına tarihlenir)."""
    from ..leagues import LEAGUES_BY_ID

    if league_id not in LEAGUES_BY_ID:
        valid = ", ".join(sorted(LEAGUES_BY_ID))
        raise ValueError(f"Bilinmeyen organizasyon kodu: {league_id!r}. Geçerli kodlar: {valid}")
    imp = _Importer(conn, league_id, as_of)
    now = datetime.fromisoformat(as_of.replace("Z", "+00:00"))
    with transaction(conn):
        for raw in rows:
            try:
                rec = _normalize(raw)
            except ValueError:
                imp.stats["invalid"] += 1  # bozuk tarih/saat: satır atlanır, içe aktarma sürer
                continue
            if rec is None:
                continue
            # Dosya başka bir organizasyona aitse (Div/Country/League uyuşmazlığı) satır alınmaz.
            if (rec["div"] and DIV_TO_LEAGUE.get(rec["div"]) != league_id) or \
                    (rec["country"] and COUNTRY_TO_LEAGUE.get(rec["country"]) != league_id) or \
                    (league_name and rec["league_name"] and rec["league_name"] != league_name):
                imp.stats["foreign"] += 1
                continue
            if rec["ft_home"] is None and not upcoming:
                continue  # sonuç dosyasında sonucu olmayan satır (ertelenmiş vb.)
            if upcoming and rec["kickoff"] <= now:
                imp.reschedule_only(rec)  # başlamış maç eklenmez; mevcut kaydın saati düzeltilir
                continue
            fid = imp.upsert(rec, _season_for(rec, league_id, season))
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
    if res["foreign"] and not (res["inserted"] or res["updated"] or res["unchanged"]):
        raise ValueError(f"Dosyadaki maçlar {league_id} organizasyonuna ait değil (Div/Country uyuşmuyor).")
    res["fixtures"] = res["inserted"]
    return res


def mark_postponed(conn: sqlite3.Connection, league_id: str, as_of: str, *, grace_days: int = 3) -> int:
    """Başlama saatinin üzerinden `grace_days` gün geçmiş, sonucu gelmemiş planlı maçlar ertelenmiş sayılır.

    Kararları sonuçlandırmada iade (void) edilir; maç daha sonra oynanırsa sonucu ±45 gün içinde aynı kayda
    işlenir (yeniden planlanır) ya da yeni kayıt olarak eklenir.
    """
    cut = to_iso(datetime.fromisoformat(as_of.replace("Z", "+00:00")) - timedelta(days=grace_days))
    rows = conn.execute("SELECT id, kickoff_at FROM fixtures WHERE league_id=? AND status='scheduled' AND source=?"
                        " AND ft_home IS NULL AND kickoff_at < ?", (league_id, SOURCE, cut)).fetchall()
    with transaction(conn):
        for fid, ko in rows:
            conn.execute("UPDATE fixtures SET status='postponed', result_recorded_at=? WHERE id=?", (as_of, fid))
            conn.execute(
                "INSERT INTO data_revisions(entity, entity_id, field, old_value, new_value, recorded_at)"
                " VALUES ('fixture', ?, 'status', 'scheduled', 'postponed', ?)", (fid, as_of))
    return len(rows)
