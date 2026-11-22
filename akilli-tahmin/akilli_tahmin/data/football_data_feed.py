"""football-data.co.uk ücretsiz veri akışı: indirme ve eşitleme.

21 organizasyondan 16'sı bu kaynakta vardır:
- Ana biçim (sezon dosyaları + `fixtures.csv`): Süper Lig, Premier League, Championship, La Liga, Serie A,
  Bundesliga, Ligue 1, Eredivisie, Primeira Liga, Belçika, İskoçya, Yunanistan.
- Ek lig biçimi (`new/XXX.csv` + `new_league_fixtures.csv`): Avusturya, İsviçre, Danimarka, Brezilya.
TFF 1. Lig, Türkiye Kupası ve UEFA organizasyonları bu ücretsiz kaynakta yoktur.

Bu kaynak İddaa oranı içermez (Bet365 / piyasa ortalaması / Pinnacle) ve canlı maç verisi sunmaz; Canlı Gol
Radar için ayrı bir canlı veri kaynağı gerekir. Dosyalar genellikle haftada iki kez güncellenir.
"""
from __future__ import annotations

import sqlite3
import time
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

from ..db.database import from_iso, to_iso, utcnow
from .football_data_csv import import_rows, read_csv_text

BASE_URL = "https://www.football-data.co.uk"
USER_AGENT = "akilli-tahmin/6.3 (football-data.co.uk free files)"

#: football-data.co.uk bölüm kodu → organizasyon kodu (ana biçim).
MAIN_DIVISIONS: dict[str, str] = {
    "T1": "tr-super-lig", "E0": "en-premier-league", "E1": "en-championship", "SP1": "es-la-liga",
    "I1": "it-serie-a", "D1": "de-bundesliga", "F1": "fr-ligue-1", "N1": "nl-eredivisie",
    "P1": "pt-primeira-liga", "B1": "be-pro-league", "SC0": "sco-premiership", "G1": "gr-super-league",
}
#: Ek lig dosya kodu → (ülke adı, organizasyon kodu).
EXTRA_LEAGUES: dict[str, tuple[str, str]] = {
    "AUT": ("Austria", "at-bundesliga"), "SWZ": ("Switzerland", "ch-super-league"),
    "DNK": ("Denmark", "dk-superliga"), "BRA": ("Brazil", "br-serie-a"),
}
UNAVAILABLE = ("tr-1-lig", "tr-kupa", "uefa-champions-league", "uefa-europa-league", "uefa-conference-league")
DEFAULT_CODES = ("T1", "E0", "SP1", "I1", "D1", "F1")


def season_codes(as_of: str, n: int) -> list[str]:
    """Ana biçim sezon kodları, eskiden yeniye (ör. 2026-11 için n=3 → ['2425', '2526', '2627'])."""
    d = from_iso(as_of)
    start = d.year if d.month >= 7 else d.year - 1
    years = range(start - n + 1, start + 1)
    return [f"{y % 100:02d}{(y + 1) % 100:02d}" for y in years]


@dataclass
class Fetcher:
    cache_dir: Path | None = None
    timeout: float = 40.0
    retries: int = 3
    log: list[str] = field(default_factory=list)

    def get(self, path: str, *, cache: bool) -> str:
        """`cache=True` için dosya önbellekten okunur (geçmiş sezonlar değişmez)."""
        local = self.cache_dir / path.replace("/", "_") if self.cache_dir else None
        if cache and local is not None and local.exists():
            return local.read_text(encoding="utf-8-sig")
        url = f"{BASE_URL}/{path}"
        last_exc: Exception | None = None
        for attempt in range(self.retries):
            try:
                req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                    raw = resp.read()
                text = raw.decode("utf-8-sig", errors="replace")
                if local is not None:
                    local.parent.mkdir(parents=True, exist_ok=True)
                    local.write_text(text, encoding="utf-8")
                self.log.append(f"indirildi: {path} ({len(raw)} bayt)")
                return text
            except Exception as exc:  # ağ hatası: kısa bekleme ile yeniden dene
                last_exc = exc
                time.sleep(2 ** attempt)
        raise RuntimeError(f"{url} indirilemedi: {last_exc}")


def sync(conn: sqlite3.Connection, *, as_of: str | None = None, codes: tuple[str, ...] = DEFAULT_CODES,
         seasons: int = 3, include_extra: tuple[str, ...] = (), cache_dir: str | Path | None = None,
         fetcher: Fetcher | None = None) -> dict:
    """Seçilen liglerin geçmiş/güncel sezon sonuçlarını ve yaklaşan maçlarını eşitler (idempotent)."""
    as_of = as_of or to_iso(utcnow())
    f = fetcher or Fetcher(Path(cache_dir) if cache_dir else None)
    report: dict = {"as_of": as_of, "leagues": {}, "errors": []}
    sc = season_codes(as_of, seasons)
    for code in codes:
        league_id = MAIN_DIVISIONS[code]
        for i, s in enumerate(sc):
            current = i == len(sc) - 1
            try:
                text = f.get(f"mmz4281/{s}/{code}.csv", cache=not current)
            except RuntimeError as exc:
                report["errors"].append(str(exc))
                continue
            res = import_rows(conn, read_csv_text(text), league_id, "20" + s[:2], as_of=as_of)
            report["leagues"].setdefault(league_id, []).append({"season": s, **res})
    if codes:
        try:
            fx = read_csv_text(f.get("fixtures.csv", cache=False))
            for code in codes:
                rows = [r for r in fx if (r.get("Div") or "").strip() == code]
                res = import_rows(conn, rows, MAIN_DIVISIONS[code], "20" + sc[-1][:2], as_of=as_of, upcoming=True)
                report["leagues"].setdefault(MAIN_DIVISIONS[code], []).append({"season": "fixtures", **res})
        except RuntimeError as exc:
            report["errors"].append(str(exc))
    if include_extra:
        first_year = int("20" + sc[0][:2])
        for code in include_extra:
            country, league_id = EXTRA_LEAGUES[code]
            try:
                rows = read_csv_text(f.get(f"new/{code}.csv", cache=False))
            except RuntimeError as exc:
                report["errors"].append(str(exc))
                continue
            rows = [r for r in rows if _season_start(r.get("Season", "")) >= first_year]
            res = import_rows(conn, rows, league_id, str(first_year), as_of=as_of)
            report["leagues"].setdefault(league_id, []).append({"season": "all", **res})
        try:
            nf = read_csv_text(f.get("new_league_fixtures.csv", cache=False))
            for code in include_extra:
                country, league_id = EXTRA_LEAGUES[code]
                rows = [r for r in nf if (r.get("Country") or "").strip() == country]
                res = import_rows(conn, rows, league_id, "20" + sc[-1][:2], as_of=as_of, upcoming=True)
                report["leagues"].setdefault(league_id, []).append({"season": "fixtures", **res})
        except RuntimeError as exc:
            report["errors"].append(str(exc))
    report["downloads"] = f.log
    return report


def _season_start(label: str) -> int:
    try:
        return int(label.strip().split("/")[0])
    except (ValueError, AttributeError):
        return 0
