"""football-data.co.uk ücretsiz veri akışı: indirme ve eşitleme.

21 organizasyondan 16'sı bu kaynakta vardır:
- Ana biçim (sezon dosyaları + `fixtures.csv`): Süper Lig, Premier League, Championship, La Liga, Serie A,
  Bundesliga, Ligue 1, Eredivisie, Primeira Liga, Belçika, İskoçya, Yunanistan.
- Ek lig biçimi (`new/XXX.csv` + `new_league_fixtures.csv`): Avusturya, İsviçre, Danimarka, Brezilya.
  Bu dört ligde ilk yarı skoru yoktur (ilk yarı marketleri bu liglerde seçilmez).
TFF 1. Lig, Türkiye Kupası ve UEFA organizasyonları bu ücretsiz kaynakta yoktur.

Bu kaynak İddaa oranı içermez (Bet365 / piyasa ortalaması / Pinnacle) ve canlı maç verisi sunmaz; Canlı Gol
Radar için ayrı bir canlı veri kaynağı gerekir. Dosyalar genellikle haftada iki kez güncellenir.

Yansı (mirror): bazı ağlarda (ör. Türkiye'deki bazı internet sağlayıcıları) football-data.co.uk'a erişilemez
(sertifika hatası / erişilemeyen ağ). Bu durumda dosyalar, aynı dosyaların değiştirilmeden kopyalandığı GitHub
yansısından alınır (`MIRROR_URL`; `scripts/mirror_football_data.py` her gün günceller). Ortam değişkeni
`AKILLI_TAHMIN_DATA_MIRROR` başka bir adres verir; "0" yansıyı kapatır.

İndirme ve içe aktarma ayrıdır: `plan_files` gereken dosyaları, `prefetch` indirmeyi (veritabanı kilidi
olmadan), `sync` içe aktarmayı yapar. Önbelleğe yalnızca bitmiş (güncel olmayan) sezon dosyaları yazılır.
"""
from __future__ import annotations

import json
import os
import sqlite3
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

from ..db.database import from_iso, to_iso, utcnow
from .football_data_csv import import_rows, mark_postponed, read_csv_text

BASE_URL = "https://www.football-data.co.uk"
USER_AGENT = "akilli-tahmin/6.3 (football-data.co.uk free files)"
#: football-data.co.uk dosyalarının GitHub yansısı (aynı göreli yollar).
MIRROR_URL = ("https://raw.githubusercontent.com/yorulmaz26/yorulmaz26/ccr-0bed25e9-5zihfo/"
              "akilli-tahmin/veri/football-data")


#: İndirilen projedeki yerel kopya (GitHub'a da erişilemezse son çare; indirme anındaki veridir).
LOCAL_COPY = Path(__file__).resolve().parents[2] / "veri" / "football-data"


def default_mirrors() -> tuple[str, ...]:
    env = os.environ.get("AKILLI_TAHMIN_DATA_MIRROR")
    if env is not None:
        return () if env.strip() in ("", "0") else (env.strip().rstrip("/"),)
    local = (LOCAL_COPY.as_uri(),) if (LOCAL_COPY / "fixtures.csv").exists() else ()
    return (MIRROR_URL, *local)

#: football-data.co.uk bölüm kodu → organizasyon kodu (ana biçim).
MAIN_DIVISIONS: dict[str, str] = {
    "T1": "tr-super-lig", "E0": "en-premier-league", "E1": "en-championship", "SP1": "es-la-liga",
    "I1": "it-serie-a", "D1": "de-bundesliga", "F1": "fr-ligue-1", "N1": "nl-eredivisie",
    "P1": "pt-primeira-liga", "B1": "be-pro-league", "SC0": "sco-premiership", "G1": "gr-super-league",
}
#: Ek lig dosya kodu → (ülke adı, dosyadaki lig adı, organizasyon kodu).
EXTRA_LEAGUES: dict[str, tuple[str, str, str]] = {
    "AUT": ("Austria", "Bundesliga", "at-bundesliga"), "SWZ": ("Switzerland", "Super League", "ch-super-league"),
    "DNK": ("Denmark", "Superliga", "dk-superliga"), "BRA": ("Brazil", "Serie A", "br-serie-a"),
}
UNAVAILABLE = ("tr-1-lig", "tr-kupa", "uefa-champions-league", "uefa-europa-league", "uefa-conference-league")
DEFAULT_CODES = ("T1", "E0", "SP1", "I1", "D1", "F1")
CONFIG_FIELD = "football_data_config"


def season_codes(as_of: str, n: int) -> list[str]:
    """Ana biçim sezon kodları, eskiden yeniye (ör. 2026-11 için n=3 → ['2425', '2526', '2627'])."""
    if n < 1:
        raise ValueError("Sezon sayısı en az 1 olmalı.")
    d = from_iso(as_of)
    start = d.year if d.month >= 7 else d.year - 1
    years = range(start - n + 1, start + 1)
    return [f"{y % 100:02d}{(y + 1) % 100:02d}" for y in years]


def codes_for_leagues(league_ids: set[str]) -> tuple[tuple[str, ...], tuple[str, ...]]:
    """Veritabanındaki organizasyonlardan (ana, ek) football-data kodlarını çıkarır."""
    main = tuple(c for c, lg in MAIN_DIVISIONS.items() if lg in league_ids)
    extra = tuple(c for c, (_, _, lg) in EXTRA_LEAGUES.items() if lg in league_ids)
    return main, extra


def save_config(conn: sqlite3.Connection, codes: tuple[str, ...], extra: tuple[str, ...], as_of: str) -> None:
    conn.execute(
        "INSERT INTO data_revisions(entity, entity_id, field, old_value, new_value, recorded_at)"
        " VALUES ('system', 0, ?, NULL, ?, ?)", (CONFIG_FIELD, json.dumps({"codes": codes, "extra": extra}), as_of))


def load_config(conn: sqlite3.Connection) -> tuple[tuple[str, ...], tuple[str, ...]] | None:
    row = conn.execute("SELECT new_value FROM data_revisions WHERE entity='system' AND field=? ORDER BY id DESC LIMIT 1",
                       (CONFIG_FIELD,)).fetchone()
    if row is None:
        return None
    cfg = json.loads(row[0])
    return tuple(cfg.get("codes", ())), tuple(cfg.get("extra", ()))


@dataclass
class FilePlan:
    path: str
    cacheable: bool  # bitmiş sezon dosyası: önbellekten okunabilir ve önbelleğe yazılabilir


def plan_files(as_of: str, codes: tuple[str, ...], seasons: int, extra: tuple[str, ...]) -> list[FilePlan]:
    sc = season_codes(as_of, seasons)
    plans = [FilePlan(f"mmz4281/{s}/{c}.csv", i < len(sc) - 1) for c in codes for i, s in enumerate(sc)]
    if codes:
        plans.append(FilePlan("fixtures.csv", False))
    plans += [FilePlan(f"new/{c}.csv", False) for c in extra]
    if extra:
        plans.append(FilePlan("new_league_fixtures.csv", False))
    return plans


@dataclass
class Fetcher:
    cache_dir: Path | None = None
    timeout: float = 20.0
    retries: int = 3
    log: list[str] = field(default_factory=list)
    mirrors: tuple[str, ...] = field(default_factory=default_mirrors)
    #: Asıl kaynağa bağlanılamadıysa sonraki dosyalar doğrudan yansıdan istenir (her dosyada beklenmez).
    primary_down: bool = False

    def get(self, path: str, *, cache: bool) -> str:
        """`cache=True` yalnızca bitmiş sezon dosyaları içindir: önbellekten okunur ve önbelleğe yazılır.
        Güncel dosyalar her seferinde indirilir ve önbelleğe yazılmaz (yarım sezon "bitmiş" sanılmaz).
        Asıl kaynak başarısız olursa dosya yansıdan alınır; ikisi de başarısızsa ilk hata bildirilir."""
        local = self.cache_dir / path.replace("/", "_") if self.cache_dir else None
        if cache and local is not None and local.exists():
            return local.read_text(encoding="utf-8-sig")
        bases = [*self.mirrors, BASE_URL] if self.primary_down else [BASE_URL, *self.mirrors]
        first_err: RuntimeError | None = None
        for base in bases:
            try:
                text, size = self._download(f"{base}/{path}", retries=self.retries if base == BASE_URL else 2)
            except RuntimeError as exc:
                if base == BASE_URL and self.mirrors:
                    self.primary_down = True
                first_err = first_err or exc
                continue
            if base != BASE_URL and not _looks_like_csv(text):
                first_err = first_err or RuntimeError(f"{base}/{path}: yansıdaki dosya geçersiz")
                continue
            if cache and local is not None:
                local.parent.mkdir(parents=True, exist_ok=True)
                tmp = local.with_suffix(local.suffix + ".tmp")
                tmp.write_text(text, encoding="utf-8")
                os.replace(tmp, local)  # yarım yazılmış dosya önbellekte kalmaz
            via = "" if base == BASE_URL else (" [yerel kopya]" if base.startswith("file:") else " [GitHub yansısı]")
            self.log.append(f"indirildi: {path} ({size} bayt){via}")
            return text
        raise first_err or RuntimeError(f"{path} indirilemedi")

    def get_bytes(self, path: str) -> bytes:
        """Asıl kaynaktan dosyanın ham baytları (yansı betiği için; önbellek ve yansı kullanılmaz)."""
        return self._download(f"{BASE_URL}/{path}", retries=self.retries, raw=True)[0]

    def _download(self, url: str, *, retries: int, raw: bool = False) -> tuple:
        last_exc: Exception | None = None
        for attempt in range(retries):
            try:
                req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                    body = resp.read()
                return (body if raw else body.decode("utf-8-sig", errors="replace")), len(body)
            except urllib.error.HTTPError as exc:
                if 400 <= exc.code < 500:
                    raise RuntimeError(f"{url} bulunamadı ({exc.code})") from exc  # 4xx yeniden denenmez
                last_exc = exc
            except Exception as exc:
                last_exc = exc
            if attempt < retries - 1:
                time.sleep(2 ** attempt)
        raise RuntimeError(f"{url} indirilemedi: {last_exc}")


def _looks_like_csv(text: str) -> bool:
    head = text.lstrip("\ufeff")[:400]
    return "HomeTeam" in head or ",Home," in head


@dataclass
class Prefetched(Fetcher):
    """Önceden (kilit dışında) indirilmiş dosyaları sunar; indirilemeyenler hata olarak bildirilir."""

    files: dict[str, str] = field(default_factory=dict)
    failures: dict[str, str] = field(default_factory=dict)

    def get(self, path: str, *, cache: bool) -> str:
        if path in self.files:
            return self.files[path]
        raise RuntimeError(self.failures.get(path, f"{path} indirilmedi"))


def prefetch(plans: list[FilePlan], fetcher: Fetcher) -> Prefetched:
    out = Prefetched(files={}, failures={})
    for p in plans:
        try:
            out.files[p.path] = fetcher.get(p.path, cache=p.cacheable)
        except RuntimeError as exc:
            out.failures[p.path] = str(exc)
    out.log = list(fetcher.log)
    return out


def sync(conn: sqlite3.Connection, *, as_of: str | None = None, codes: tuple[str, ...] = DEFAULT_CODES,
         seasons: int = 3, include_extra: tuple[str, ...] = (), cache_dir: str | Path | None = None,
         fetcher: Fetcher | None = None) -> dict:
    """Seçilen liglerin geçmiş/güncel sezon sonuçlarını ve yaklaşan maçlarını eşitler (idempotent).

    Her dosya ve lig ayrı ayrı içe aktarılır; birindeki hata diğerlerini durdurmaz (rapora yazılır).
    Sonunda başlama saatinin üzerinden 3 gün geçip sonucu gelmeyen planlı maçlar ertelenmiş işaretlenir.
    """
    as_of = as_of or to_iso(utcnow())
    f = fetcher or Fetcher(Path(cache_dir) if cache_dir else None)
    report: dict = {"as_of": as_of, "leagues": {}, "errors": [], "invalid_rows": 0}
    sc = season_codes(as_of, seasons)
    first_year = int("20" + sc[0][:2])

    def run(league_id: str, label: str, fn) -> None:
        try:
            res = fn()
        except (RuntimeError, ValueError, sqlite3.Error) as exc:
            report["errors"].append(f"{league_id} {label}: {exc}")
            return
        report["invalid_rows"] += res.get("invalid", 0)
        report["leagues"].setdefault(league_id, []).append({"season": label, **res})

    for code in codes:
        league_id = MAIN_DIVISIONS[code]
        for i, s in enumerate(sc):
            path = f"mmz4281/{s}/{code}.csv"
            run(league_id, s, lambda path=path, s=s, i=i: import_rows(
                conn, read_csv_text(f.get(path, cache=i < len(sc) - 1)), league_id, "20" + s[:2], as_of=as_of))
    if codes:
        try:
            fx = read_csv_text(f.get("fixtures.csv", cache=False))
        except RuntimeError as exc:
            report["errors"].append(str(exc))
            fx = None
        if fx is not None:
            for code in codes:
                rows = [r for r in fx if (r.get("Div") or "").strip() == code]
                run(MAIN_DIVISIONS[code], "fixtures", lambda rows=rows, code=code: import_rows(
                    conn, rows, MAIN_DIVISIONS[code], "20" + sc[-1][:2], as_of=as_of, upcoming=True))
    if include_extra:
        for code in include_extra:
            country, lname, league_id = EXTRA_LEAGUES[code]

            def extra_rows(code=code, lname=lname):
                rows = read_csv_text(f.get(f"new/{code}.csv", cache=False))
                rows = [r for r in rows if _season_start(r.get("Season", "")) >= first_year
                        and (r.get("League") or "").strip() == lname]
                return rows

            run(league_id, "all", lambda league_id=league_id, extra_rows=extra_rows: import_rows(
                conn, extra_rows(), league_id, str(first_year), as_of=as_of))
        try:
            nf = read_csv_text(f.get("new_league_fixtures.csv", cache=False))
        except RuntimeError as exc:
            report["errors"].append(str(exc))
            nf = None
        if nf is not None:
            for code in include_extra:
                country, lname, league_id = EXTRA_LEAGUES[code]
                rows = [r for r in nf if (r.get("Country") or "").strip() == country
                        and (r.get("League") or "").strip() == lname]
                run(league_id, "fixtures", lambda rows=rows, league_id=league_id: import_rows(
                    conn, rows, league_id, "20" + sc[-1][:2], as_of=as_of, upcoming=True))
    postponed = 0
    for lg in {MAIN_DIVISIONS[c] for c in codes} | {EXTRA_LEAGUES[c][2] for c in include_extra}:
        postponed += mark_postponed(conn, lg, as_of)
    report["postponed"] = postponed
    report["downloads"] = f.log
    return report


def _season_start(label: str) -> int:
    try:
        return int(label.strip().split("/")[0])
    except (ValueError, AttributeError):
        return 0
