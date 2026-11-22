"""API-Football (v3.football.api-sports.io) istemcisi ve günlük istek bütçesi.

Ücretsiz plan günde 100 istekle sınırlıdır (sayaç 00:00 UTC'de sıfırlanır). Bu modül:

- Her isteği GÖNDERMEDEN ÖNCE `api_requests` tablosuna yazar; günlük sınır ve yedek (reserve) aşılacaksa
  isteği hiç göndermez (`BudgetExceeded`). Böylece süreç çökse bile sayılmayan istek kalmaz.
- API anahtarını hiçbir yere yazmaz: anahtar `API_FOOTBALL_KEY` ortam değişkeninden okunur ve yalnızca
  istek başlığında (x-apisports-key) gönderilir. Ortam değişkeni yoksa yer tutucu değer gönderilir; bulut
  oturumunda gerçek anahtarı ağ vekil sunucusu ekler. Hata metinlerinde başlık/anahtar yer almaz.
- Sağlayıcının bildirdiği kalan istek sayısını (x-ratelimit-requests-remaining başlığı) kaydeder ve
  kendi sayacından düşükse onu esas alır.

Yalnızca iki uç kullanılır: `/fixtures?live=<lig kimlikleri>` (canlı skor, dakika, olaylar; tek istekte
bütün canlı maçlar) ve `/odds/live` (canlı oranlar; tek istekte bütün canlı maçlar).
"""
from __future__ import annotations

import json
import os
import sqlite3
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any, Callable

from ..db.database import dumps, insert, loads, to_iso, transaction, utcnow

PROVIDER = "api-football"
BASE_URL = "https://v3.football.api-sports.io"
KEY_ENV = "API_FOOTBALL_KEY"
#: Ortam değişkeni yoksa gönderilen yer tutucu (bulut ortamında vekil sunucu gerçek anahtarı ekler).
PLACEHOLDER_KEY = "proxy-injected"
TIMEOUT_SECONDS = 25

#: 21 organizasyonun API-Football lig kimlikleri (2026-11-22'de /leagues ile doğrulandı).
APIF_LEAGUE_IDS: dict[str, int] = {
    "tr-super-lig": 203, "tr-1-lig": 204, "tr-kupa": 206,
    "en-premier-league": 39, "en-championship": 40,
    "es-la-liga": 140, "it-serie-a": 135, "de-bundesliga": 78, "fr-ligue-1": 61,
    "nl-eredivisie": 88, "pt-primeira-liga": 94, "be-pro-league": 144, "sco-premiership": 179,
    "gr-super-league": 197, "at-bundesliga": 218, "ch-super-league": 207, "dk-superliga": 119,
    "br-serie-a": 71,
    "uefa-champions-league": 2, "uefa-europa-league": 3, "uefa-conference-league": 848,
}
LEAGUE_BY_APIF_ID: dict[int, str] = {v: k for k, v in APIF_LEAGUE_IDS.items()}

#: Canlı oran market kimlikleri (API-Football /odds/live).
MARKET_MATCH_GOALS = 25          # "Match Goals" — maç toplam gol Alt/Üst
MARKET_FIRST_HALF_GOALS = 49     # "Over/Under (1st Half)" — ilk yarı gol Alt/Üst


class ApiFootballError(RuntimeError):
    """Sağlayıcı hatası (ağ, HTTP, API'nin `errors` alanı). Metin anahtar/başlık içermez."""


class BudgetExceeded(RuntimeError):
    """Günlük istek bütçesi (sınır − yedek) doldu; istek gönderilmedi."""


@dataclass(frozen=True)
class ApiResponse:
    payload: dict
    remaining: int | None


#: fetch(url, headers) → (http_status, headers_dict, body_bytes). Testler sahte fetch verir.
Fetch = Callable[[str, dict], tuple[int, dict, bytes]]


def _urllib_fetch(url: str, headers: dict) -> tuple[int, dict, bytes]:
    req = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as resp:  # noqa: S310 (sabit https adresi)
            return resp.status, {k.lower(): v for k, v in resp.headers.items()}, resp.read()
    except urllib.error.HTTPError as exc:
        return exc.code, {k.lower(): v for k, v in (exc.headers or {}).items()}, exc.read() or b""
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise ApiFootballError(f"Bağlantı hatası: {type(exc).__name__}") from None


class Client:
    """Bütçe denetimli API-Football istemcisi."""

    def __init__(self, fetch: Fetch | None = None, *, key: str | None = None):
        self._fetch = fetch or _urllib_fetch
        self._key = key if key is not None else os.environ.get(KEY_ENV) or PLACEHOLDER_KEY

    def get(self, path: str, params: dict[str, Any] | None = None) -> ApiResponse:
        query = urllib.parse.urlencode(params or {})
        url = f"{BASE_URL}{path}" + (f"?{query}" if query else "")
        status, headers, body = self._fetch(url, {"x-apisports-key": self._key, "Accept": "application/json"})
        remaining = _int_or_none(headers.get("x-ratelimit-requests-remaining"))
        if status != 200:
            raise ApiFootballError(f"HTTP {status} ({path})")
        try:
            payload = json.loads(body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            raise ApiFootballError(f"Geçersiz yanıt ({path})") from None
        errors = payload.get("errors")
        if errors:  # API hataları sözlük veya liste olabilir; anahtar içermez
            text = "; ".join(f"{k}: {v}" for k, v in errors.items()) if isinstance(errors, dict) else str(errors)
            raise ApiFootballError(f"API hatası ({path}): {text[:300]}")
        return ApiResponse(payload, remaining)


def _int_or_none(v: Any) -> int | None:
    try:
        return int(v)
    except (TypeError, ValueError):
        return None


# ───────────────────────── günlük istek bütçesi ─────────────────────────

def budget_day(now: str) -> str:
    """Sağlayıcının sayaç günü (UTC)."""
    return now[:10]


def requests_used(conn: sqlite3.Connection, now: str) -> int:
    return int(conn.execute("SELECT COUNT(*) FROM api_requests WHERE provider=? AND day=?",
                            (PROVIDER, budget_day(now))).fetchone()[0])


def provider_remaining(conn: sqlite3.Connection, now: str) -> int | None:
    row = conn.execute("SELECT provider_remaining FROM api_requests WHERE provider=? AND day=? AND"
                       " provider_remaining IS NOT NULL ORDER BY id DESC LIMIT 1",
                       (PROVIDER, budget_day(now))).fetchone()
    return None if row is None else int(row[0])


def budget_status(conn: sqlite3.Connection, now: str, daily_limit: int, reserve: int) -> dict:
    used = requests_used(conn, now)
    remaining = max(0, daily_limit - used)
    prov = provider_remaining(conn, now)
    if prov is not None:
        remaining = min(remaining, prov)
    usable = max(0, remaining - reserve)
    return {"day": budget_day(now), "daily_limit": daily_limit, "reserve": reserve, "used": used,
            "remaining": remaining, "usable": usable, "provider_remaining": prov}


def reserve_request(conn: sqlite3.Connection, now: str, endpoint: str, params: dict, purpose: str, *,
                    daily_limit: int, reserve: int) -> int:
    """Bütçe uygunsa isteği 'sent' olarak kaydeder ve kimliğini döndürür; değilse `BudgetExceeded`."""
    with transaction(conn):
        st = budget_status(conn, now, daily_limit, reserve)
        if st["usable"] < 1:
            raise BudgetExceeded(f"Günlük istek bütçesi doldu ({st['used']}/{daily_limit}, yedek {reserve})")
        return insert(conn, "api_requests", {
            "provider": PROVIDER, "endpoint": endpoint, "params": dumps(params), "purpose": purpose,
            "day": budget_day(now), "requested_at": now, "status": "sent",
        })


def finish_request(conn: sqlite3.Connection, request_id: int, *, ok: bool, results: int | None = None,
                   remaining: int | None = None, error: str | None = None) -> None:
    with transaction(conn):
        conn.execute("UPDATE api_requests SET status=?, results=?, provider_remaining=?, error=? WHERE id=?",
                     ("ok" if ok else "error", results, remaining, (error or None) and error[:500], request_id))


def recent_requests(conn: sqlite3.Connection, limit: int = 20) -> list[dict]:
    rows = conn.execute("SELECT id, endpoint, params, purpose, requested_at, status, results, error"
                        " FROM api_requests WHERE provider=? ORDER BY id DESC LIMIT ?", (PROVIDER, limit)).fetchall()
    return [{"id": r[0], "endpoint": r[1], "params": loads(r[2], {}), "purpose": r[3], "requested_at": r[4],
             "status": r[5], "results": r[6], "error": r[7]} for r in rows]


def now_iso() -> str:
    return to_iso(utcnow())
