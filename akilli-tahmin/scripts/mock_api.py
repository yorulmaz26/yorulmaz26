#!/usr/bin/env python3
"""Akıllı Tahmin — arayüz geliştirme için küçük sahte (mock) API sunucusu.

Yalnızca standart kütüphane kullanır. Statik dosyaları (akilli_tahmin/web/static)
"/" ve "/static/..." altında, docs/API.md sözleşmesine uyan sabit JSON yanıtlarını
(tests/web/mock_api.json) "/api/v1/..." altında sunar. Gerçek backend yerine
geçmez; yalnızca UI geliştirme ve duman testleri içindir.

Kullanım:
    python3 scripts/mock_api.py --port 8765 [--host 127.0.0.1] [--delay 300]
"""
from __future__ import annotations

import argparse
import copy
import json
import mimetypes
import re
import sys
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

ROOT = Path(__file__).resolve().parent.parent
STATIC_DIR = ROOT / "akilli_tahmin" / "web" / "static"
DATA_FILE = ROOT / "tests" / "web" / "mock_api.json"
MODES = ("highest_probability", "best_value", "balanced")
PERIODS = ("7d", "30d", "90d", "all")

_lock = threading.Lock()
_settings: dict[str, str] = {}          # user_id -> default_mode
_notification_permission: dict[str, bool] = {}


def _now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


class ApiError(Exception):
    def __init__(self, status: int, detail: str):
        super().__init__(detail)
        self.status = status
        self.detail = detail


def load_data() -> dict:
    with DATA_FILE.open(encoding="utf-8") as fh:
        return json.load(fh)


def first(qs: dict, key: str, default=None):
    vals = qs.get(key)
    return vals[0] if vals else default


def as_int(value, name: str, default=None):
    if value is None or value == "":
        return default
    try:
        return int(value)
    except ValueError:
        raise ApiError(422, f"'{name}' tam sayı olmalıdır.")


def check_mode(mode: str | None, user: str) -> str:
    if mode is None or mode == "":
        return _settings.get(user, "balanced")
    if mode not in MODES:
        raise ApiError(422, f"Geçersiz seçim modu: {mode}")
    return mode


def check_period(period: str | None) -> str:
    period = period or "all"
    if period in PERIODS:
        return period
    if re.fullmatch(r"season:\d{4}", period):
        return "all"  # mock: sezon filtresi tüm dönem verisiyle yanıtlanır
    raise ApiError(422, f"Geçersiz dönem: {period}")


class Api:
    def __init__(self, data: dict):
        self.data = data

    def handle(self, method: str, path: str, qs: dict, body, user: str):
        d = self.data
        if method == "GET":
            if path == "/recommendations/modes":
                resp = copy.deepcopy(d["modes"])
                resp["user_default_mode"] = _settings.get(user, resp.get("default_mode", "balanced"))
                return resp
            if path == "/recommendations":
                mode = check_mode(first(qs, "mode"), user)
                resp = copy.deepcopy(d["recommendations"][mode])
                league = first(qs, "league")
                if league:
                    resp["items"] = [i for i in resp["items"] if i["fixture"]["league"]["id"] == league]
                return resp
            if path == "/recommendations/rejections":
                return self.rejections(qs, user)
            if path == "/recommendations/modes/comparison":
                period = check_period(first(qs, "period"))
                return copy.deepcopy(d["comparison"][period])
            if path == "/recommendations/modes/performance":
                mode = check_mode(first(qs, "mode"), user)
                period = check_period(first(qs, "period"))
                resp = copy.deepcopy(d["mode_performance"][mode])
                by_period = resp.pop("by_period", {})  # mock'a özgü yardımcı alan; yanıtta yer almaz
                resp["period"] = period
                if period != "all":
                    resp["overall"] = resp["windows"][period]
                    resp.update(by_period.get(period, {}))
                return resp
            m = re.fullmatch(r"/recommendations/(\d+)/decision-explanation", path)
            if m:
                item = d["decision_explanations"].get(m.group(1))
                if item is None:
                    raise ApiError(404, "Seçim bulunamadı.")
                return item
            m = re.fullmatch(r"/recommendations/(\d+)/rejection-reasons", path)
            if m:
                return self.rejection_reasons(int(m.group(1)))
            if path == "/performance/selection-modes":
                return d["performance_selection_modes"]
            if path == "/settings":
                return {"user_id": user, "default_mode": _settings.get(user, "balanced")}
            if path == "/radar/config":
                return d["radar_config"]
            if path == "/radar/live":
                return self.radar_live(user)
            if path == "/radar/alerts":
                limit = max(1, min(500, as_int(first(qs, "limit"), "limit", 50)))
                return {"items": d["radar_alerts"]["items"][:limit]}
            if path == "/leagues":
                return d["leagues"]
            if path == "/system/status":
                return d["system_status"]
        if method == "PUT" and path == "/settings":
            mode = (body or {}).get("default_mode") if isinstance(body, dict) else None
            if mode not in MODES:
                raise ApiError(422, "default_mode geçerli bir seçim modu olmalıdır.")
            with _lock:
                _settings[user] = mode
            return {"user_id": user, "default_mode": mode}
        if method == "POST" and path == "/radar/notifications/permission":
            granted = (body or {}).get("granted") if isinstance(body, dict) else None
            if not isinstance(granted, bool):
                raise ApiError(422, "granted alanı true/false olmalıdır.")
            with _lock:
                _notification_permission[user] = granted
            return {"user_id": user, "granted": granted}
        raise ApiError(404, "Not Found")

    def rejections(self, qs: dict, user: str) -> dict:
        """Elenen adaylar: total, reason_counts ve fixtures kayıtlardan türetilir (gerçek API ile aynı anlam).

        `fixtures` limit/offset'ten bağımsız olarak o moddaki elenen adayı olan BÜTÜN maçları listeler.
        """
        mode = check_mode(first(qs, "mode"), user)
        limit = as_int(first(qs, "limit"), "limit", 50)
        offset = as_int(first(qs, "offset"), "offset", 0)
        if not 1 <= limit <= 500:
            raise ApiError(422, "'limit' 1 ile 500 arasında olmalıdır.")
        if offset < 0:
            raise ApiError(422, "'offset' negatif olamaz.")
        fixture_id = as_int(first(qs, "fixture_id"), "fixture_id")
        src = self.data["rejections"][mode]
        all_items = src["items"]
        fixtures: dict[int, dict] = {}
        for it in all_items:
            fx = it["fixture"]
            entry = fixtures.setdefault(fx["id"], {"id": fx["id"], "home": fx.get("home"), "away": fx.get("away"),
                                                   "kickoff_at": fx.get("kickoff_at"), "count": 0})
            entry["count"] += 1
        items = all_items if fixture_id is None else [i for i in all_items if i["fixture"]["id"] == fixture_id]
        counts: dict[str, dict] = {}
        for it in items:
            for fc in it["failed_checks"]:
                entry = counts.setdefault(fc["code"], {"code": fc["code"], "label": fc["label"], "count": 0})
                entry["count"] += 1
        page = []
        for it in items[offset:offset + limit]:
            row = copy.deepcopy(it)
            row["failed_checks"] = [{"code": fc["code"], "label": fc["label"], "detail": fc.get("detail")}
                                    for fc in it["failed_checks"]]
            page.append(row)
        return {
            "mode": mode,
            "run_id": src.get("run_id"),
            "total": len(items),
            "reason_counts": sorted(counts.values(), key=lambda c: -c["count"]),
            "fixtures": sorted(fixtures.values(), key=lambda f: (f["kickoff_at"] or "", f["id"])),
            "items": page,
        }

    def rejection_reasons(self, cid: int) -> dict:
        for mode, src in self.data["rejections"].items():
            for it in src["items"]:
                if it["id"] == cid:
                    return {
                        "id": cid, "mode": mode, "selected": False,
                        "fixture": {k: it["fixture"].get(k) for k in ("id", "home", "away")},
                        "market": it["market"], "outcome": it["outcome"],
                        "failed_checks": [{"code": fc["code"], "label": fc["label"], "passed": False,
                                           "detail": fc.get("detail"), "value": fc.get("value"),
                                           "threshold": fc.get("threshold")} for fc in it["failed_checks"]],
                        "reason_text": it.get("reason_text"),
                    }
        raise ApiError(404, "Elenen aday bulunamadı.")

    def radar_live(self, user: str) -> dict:
        resp = copy.deepcopy(self.data["radar_live"])
        resp["evaluated_at"] = _now_iso()
        granted = _notification_permission.get(user, False)
        for item in resp["items"]:
            for cond in item["conditions"]:
                if cond["code"] == "notification_permission":
                    cond["passed"] = granted
                    cond["detail"] = "İzin verildi" if granted else "Kullanıcı bildirim izni vermedi"
            if not granted and item.get("alarm") and not item.get("blocked_reason"):
                item["alarm"] = False
                item["alert_id"] = None
                item["blocked_reason"] = "Sağlanmayan koşul: Bildirim izni."
        return resp


class Handler(BaseHTTPRequestHandler):
    server_version = "AkilliTahminMock/6.3"
    api: Api
    delay_ms = 0
    quiet = False

    def log_message(self, fmt, *args):  # noqa: D401 - stdlib imzası
        if not self.quiet:
            sys.stderr.write("[mock] %s - %s\n" % (self.address_string(), fmt % args))

    # -- helpers ---------------------------------------------------------
    def _send(self, status: int, body: bytes, content_type: str, extra: dict | None = None):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def _json(self, status: int, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self._send(status, body, "application/json; charset=utf-8")

    def _static(self, rel: str):
        target = (STATIC_DIR / rel).resolve()
        if STATIC_DIR.resolve() not in target.parents and target != STATIC_DIR.resolve():
            return self._json(404, {"detail": "Not Found"})
        if not target.is_file():
            return self._json(404, {"detail": "Not Found"})
        ctype = mimetypes.guess_type(target.name)[0] or "application/octet-stream"
        if ctype.startswith("text/") or ctype in ("application/javascript", "application/json"):
            ctype += "; charset=utf-8"
        self._send(200, target.read_bytes(), ctype)

    def _dispatch(self, method: str):
        url = urlsplit(self.path)
        path = url.path
        if path.startswith("/api/v1"):
            if self.delay_ms:
                time.sleep(self.delay_ms / 1000)
            sub = path[len("/api/v1"):].rstrip("/") or "/"
            body = None
            if method in ("POST", "PUT"):
                # Gerçek sunucu gibi: durum değiştiren istekler yalnızca application/json kabul eder
                # (CORS-basit, içerik türü olmayan çapraz site isteklerini engeller).
                ctype = (self.headers.get("Content-Type") or "").split(";")[0].strip().lower()
                if ctype != "application/json":
                    return self._json(415, {"detail": "İstek gövdesi application/json olmalıdır."})
            length = int(self.headers.get("Content-Length") or 0)
            if length:
                raw = self.rfile.read(length)
                try:
                    body = json.loads(raw.decode("utf-8"))
                except (UnicodeDecodeError, json.JSONDecodeError):
                    return self._json(422, {"detail": "Geçersiz JSON gövdesi."})
            user = (self.headers.get("X-User-Id") or "local").strip() or "local"
            try:
                payload = self.api.handle(method, sub, parse_qs(url.query), body, user)
            except ApiError as exc:
                return self._json(exc.status, {"detail": exc.detail})
            return self._json(200, payload)
        if method not in ("GET", "HEAD"):
            return self._json(405, {"detail": "Method Not Allowed"})
        if path in ("/", "/index.html"):
            return self._static("index.html")
        if path.startswith("/static/"):
            return self._static(path[len("/static/"):])
        return self._json(404, {"detail": "Not Found"})

    def do_GET(self):  # noqa: N802
        self._dispatch("GET")

    def do_HEAD(self):  # noqa: N802
        self._dispatch("GET")

    def do_POST(self):  # noqa: N802
        self._dispatch("POST")

    def do_PUT(self):  # noqa: N802
        self._dispatch("PUT")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Akıllı Tahmin sahte API sunucusu (yalnızca UI geliştirme).")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--delay", type=int, default=0, help="Her API yanıtına eklenecek gecikme (ms).")
    parser.add_argument("--quiet", action="store_true", help="İstek günlüğünü kapat.")
    args = parser.parse_args(argv)

    Handler.api = Api(load_data())
    Handler.delay_ms = max(0, args.delay)
    Handler.quiet = args.quiet
    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Akıllı Tahmin mock API: http://{args.host}:{args.port}/ (statik: {STATIC_DIR})", flush=True)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
