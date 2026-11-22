"""FastAPI uygulaması — /api/v1 uçları ve web arayüzü.

Eşzamanlılık: SQLite bağlantısı ve süreç içi önbellekler (geçmiş deposu, radar kalibrasyonu) tek
bir bağlantı üzerinden çalışır. FastAPI eşzamanlı uçları iş parçacığı havuzunda çalıştırdığı için
bütün veritabanı erişimi `Runtime.lock` ile sıralanır; böylece işlemler ve imleçler iç içe geçmez.
Başarı Merkezi uçlarının sonuçları veri sürümüne ve dakikaya göre önbelleğe alınır.
"""
from __future__ import annotations

import functools
import os
from collections import OrderedDict
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException, Path as PathParam, Query, Request
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from ..db.database import MODE_CODES, to_iso, utcnow
from ..runtime import Runtime
from ..selection.modes import normalize_mode
from . import service

STATIC_DIR = Path(__file__).resolve().parent.parent / "web" / "static"
MAX_ID = 2**63 - 1
_CACHE_SIZE = 64


class SettingsBody(BaseModel):
    default_mode: str


class PermissionBody(BaseModel):
    granted: bool


def _minute_now() -> str:
    return to_iso(utcnow().replace(second=0))


def create_app(db_path: str | None = None, *, auto_refresh_demo: bool = True) -> FastAPI:
    runtime = Runtime(db_path or os.environ.get("AKILLI_TAHMIN_DB", "akilli_tahmin.sqlite3"))
    app = FastAPI(title="Akıllı Tahmin", version="6.3.0",
                  description="İki tahmin modeli (MODEL A, MODEL B), üç seçim modu, Canlı Gol Radar.")
    app.state.runtime = runtime
    conn = runtime.conn
    lock = runtime.lock
    cache: OrderedDict = OrderedDict()

    def locked(fn):
        """Uç gövdesini çalışma zamanı kilidi altında çalıştırır (tek bağlantı, sıralı erişim)."""

        @functools.wraps(fn)
        def wrapper(*args, **kwargs):
            with lock:
                return fn(*args, **kwargs)

        return wrapper

    def data_version() -> tuple:
        row = conn.execute(
            "SELECT (SELECT MAX(id) FROM selection_mode_decisions),"
            " (SELECT COUNT(*) FROM selection_mode_decisions WHERE settlement_status<>'pending'),"
            " (SELECT MAX(id) FROM candidate_evaluations)").fetchone()
        return tuple(row)

    def cached(name: str, args: tuple, compute):
        key = (name, args, data_version(), _minute_now())
        if key in cache:
            cache.move_to_end(key)
            return cache[key]
        value = compute()
        cache[key] = value
        while len(cache) > _CACHE_SIZE:
            cache.popitem(last=False)
        return value

    def user(x_user_id: str | None) -> str:
        return (x_user_id or "local").strip()[:64] or "local"

    def mode_or_default(mode: str | None, uid: str) -> str:
        if mode is None:
            return service.get_settings(conn, uid)["default_mode"]
        if mode not in MODE_CODES:
            raise HTTPException(422, f"Geçersiz mod: {mode}. Geçerli: {', '.join(MODE_CODES)}")
        return mode

    def maybe_refresh() -> None:
        if auto_refresh_demo:
            runtime.refresh_demo_if_stale()

    @app.middleware("http")
    async def same_origin_writes(request: Request, call_next):
        """Durum değiştiren istekler yalnızca JSON gövdeyle ve aynı kaynaktan kabul edilir (CSRF önlemi)."""
        if request.method in ("POST", "PUT", "PATCH", "DELETE") and request.url.path.startswith("/api/"):
            ctype = request.headers.get("content-type", "").split(";")[0].strip().lower()
            if ctype != "application/json":
                return JSONResponse({"detail": "Content-Type: application/json gerekli."}, status_code=415)
            site = request.headers.get("sec-fetch-site")
            if site is not None and site not in ("same-origin", "none"):
                return JSONResponse({"detail": "Başka bir siteden gelen istek reddedildi."}, status_code=403)
            origin = request.headers.get("origin")
            if origin is not None:
                host = request.headers.get("host", "")
                if origin.split("://", 1)[-1] != host:
                    return JSONResponse({"detail": "Kaynak (Origin) uyuşmuyor."}, status_code=403)
        return await call_next(request)

    # ───────────── öneriler ─────────────
    @app.get("/api/v1/recommendations/modes")
    @locked
    def get_modes(x_user_id: str | None = Header(default=None)):
        return service.modes(conn, user(x_user_id))

    @app.get("/api/v1/recommendations/modes/comparison")
    @locked
    def get_comparison(period: str = "all"):
        from ..performance.tracker import modes_comparison

        try:
            return cached("comparison", (period,), lambda: modes_comparison(conn, period, _minute_now()))
        except ValueError as exc:
            raise HTTPException(422, str(exc)) from exc

    @app.get("/api/v1/recommendations/modes/performance")
    @locked
    def get_mode_performance(mode: str = "balanced", period: str = "all"):
        from ..performance.tracker import mode_performance

        if mode not in MODE_CODES:
            raise HTTPException(422, f"Geçersiz mod: {mode}")
        try:
            return cached("mode_performance", (mode, period),
                          lambda: mode_performance(conn, mode, period, _minute_now()))
        except ValueError as exc:
            raise HTTPException(422, str(exc)) from exc

    @app.get("/api/v1/recommendations/rejections")
    @locked
    def get_rejections(mode: str | None = None, fixture_id: int | None = Query(None, ge=1, le=MAX_ID),
                       limit: int = Query(50, ge=1, le=500), offset: int = Query(0, ge=0, le=10_000_000),
                       x_user_id: str | None = Header(default=None)):
        return service.rejections(conn, mode_or_default(mode, user(x_user_id)), fixture_id=fixture_id,
                                  limit=limit, offset=offset)

    @app.get("/api/v1/recommendations")
    @locked
    def get_recommendations(mode: str | None = None, league: str | None = Query(None, max_length=64),
                            date: str | None = Query(None, pattern=r"^\d{4}-\d{2}-\d{2}$"),
                            x_user_id: str | None = Header(default=None)):
        maybe_refresh()
        return service.recommendations(conn, mode_or_default(mode, user(x_user_id)), league=league, date=date)

    @app.get("/api/v1/recommendations/{cid}/decision-explanation")
    @locked
    def get_explanation(cid: int = PathParam(..., ge=1, le=MAX_ID)):
        try:
            return service.decision_explanation(conn, cid)
        except service.NotFound as exc:
            raise HTTPException(404, str(exc)) from exc

    @app.get("/api/v1/recommendations/{cid}/rejection-reasons")
    @locked
    def get_rejection(cid: int = PathParam(..., ge=1, le=MAX_ID)):
        try:
            return service.rejection_reasons(conn, cid)
        except service.NotFound as exc:
            raise HTTPException(404, str(exc)) from exc

    # ───────────── başarı merkezi ─────────────
    @app.get("/api/v1/performance/selection-modes")
    @locked
    def get_selection_mode_performance():
        from ..performance.tracker import selection_modes_overview

        return cached("overview", (), lambda: selection_modes_overview(conn, _minute_now()))

    # ───────────── ayarlar ─────────────
    @app.get("/api/v1/settings")
    @locked
    def get_settings(x_user_id: str | None = Header(default=None)):
        return service.get_settings(conn, user(x_user_id))

    @app.put("/api/v1/settings")
    @locked
    def put_settings(body: SettingsBody, x_user_id: str | None = Header(default=None)):
        try:
            normalize_mode(body.default_mode)
        except ValueError as exc:
            raise HTTPException(422, str(exc)) from exc
        return service.put_settings(conn, user(x_user_id), body.default_mode)

    # ───────────── canlı gol radar ─────────────
    @app.get("/api/v1/radar/config")
    def get_radar_config():
        from ..radar.live_goal_radar import radar_config

        return radar_config(runtime.settings)

    @app.get("/api/v1/radar/live")
    @locked
    def get_radar_live(x_user_id: str | None = Header(default=None)):
        from ..radar.live_goal_radar import evaluate_live

        runtime.live_tick()
        return evaluate_live(conn, to_iso(utcnow()), user_id=user(x_user_id), settings=runtime.settings)

    @app.get("/api/v1/radar/alerts")
    @locked
    def get_radar_alerts(limit: int = Query(50, ge=1, le=500)):
        from ..radar.live_goal_radar import list_alerts

        return list_alerts(conn, limit)

    @app.post("/api/v1/radar/notifications/permission")
    @locked
    def post_permission(body: PermissionBody, x_user_id: str | None = Header(default=None)):
        from ..radar.live_goal_radar import set_notification_permission

        return set_notification_permission(conn, user(x_user_id), body.granted, to_iso(utcnow()))

    # ───────────── diğer ─────────────
    @app.get("/api/v1/leagues")
    def get_leagues():
        return service.leagues()

    @app.get("/api/v1/system/status")
    @locked
    def get_status():
        return service.system_status(conn)

    if STATIC_DIR.exists():
        app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")

        @app.get("/", include_in_schema=False)
        def index():
            return FileResponse(STATIC_DIR / "index.html")

    return app
