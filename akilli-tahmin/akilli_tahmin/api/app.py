"""FastAPI uygulaması — /api/v1 uçları ve web arayüzü."""
from __future__ import annotations

import os
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException, Query
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from ..db.database import MODE_CODES, to_iso, utcnow
from ..runtime import Runtime
from ..selection.modes import normalize_mode
from . import service

STATIC_DIR = Path(__file__).resolve().parent.parent / "web" / "static"


class SettingsBody(BaseModel):
    default_mode: str


class PermissionBody(BaseModel):
    granted: bool


def create_app(db_path: str | None = None, *, auto_refresh_demo: bool = True) -> FastAPI:
    runtime = Runtime(db_path or os.environ.get("AKILLI_TAHMIN_DB", "akilli_tahmin.sqlite3"))
    app = FastAPI(title="Akıllı Tahmin", version="6.3.0",
                  description="İki tahmin modeli (MODEL A, MODEL B), üç seçim modu, Canlı Gol Radar.")
    app.state.runtime = runtime
    conn = runtime.conn

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

    # ───────────── öneriler ─────────────
    @app.get("/api/v1/recommendations/modes")
    def get_modes(x_user_id: str | None = Header(default=None)):
        return service.modes(conn, user(x_user_id))

    @app.get("/api/v1/recommendations/modes/comparison")
    def get_comparison(period: str = "all"):
        from ..performance.tracker import modes_comparison

        try:
            return modes_comparison(conn, period, to_iso(utcnow()))
        except ValueError as exc:
            raise HTTPException(422, str(exc)) from exc

    @app.get("/api/v1/recommendations/modes/performance")
    def get_mode_performance(mode: str = "balanced", period: str = "all"):
        from ..performance.tracker import mode_performance

        if mode not in MODE_CODES:
            raise HTTPException(422, f"Geçersiz mod: {mode}")
        try:
            return mode_performance(conn, mode, period, to_iso(utcnow()))
        except ValueError as exc:
            raise HTTPException(422, str(exc)) from exc

    @app.get("/api/v1/recommendations/rejections")
    def get_rejections(mode: str | None = None, fixture_id: int | None = None,
                       limit: int = Query(50, ge=1, le=500), offset: int = Query(0, ge=0),
                       x_user_id: str | None = Header(default=None)):
        return service.rejections(conn, mode_or_default(mode, user(x_user_id)), fixture_id=fixture_id,
                                  limit=limit, offset=offset)

    @app.get("/api/v1/recommendations")
    def get_recommendations(mode: str | None = None, league: str | None = None, date: str | None = None,
                            x_user_id: str | None = Header(default=None)):
        maybe_refresh()
        return service.recommendations(conn, mode_or_default(mode, user(x_user_id)), league=league, date=date)

    @app.get("/api/v1/recommendations/{cid}/decision-explanation")
    def get_explanation(cid: int):
        try:
            return service.decision_explanation(conn, cid)
        except service.NotFound as exc:
            raise HTTPException(404, str(exc)) from exc

    @app.get("/api/v1/recommendations/{cid}/rejection-reasons")
    def get_rejection(cid: int):
        try:
            return service.rejection_reasons(conn, cid)
        except service.NotFound as exc:
            raise HTTPException(404, str(exc)) from exc

    # ───────────── başarı merkezi ─────────────
    @app.get("/api/v1/performance/selection-modes")
    def get_selection_mode_performance():
        from ..performance.tracker import selection_modes_overview

        return selection_modes_overview(conn, to_iso(utcnow()))

    # ───────────── ayarlar ─────────────
    @app.get("/api/v1/settings")
    def get_settings(x_user_id: str | None = Header(default=None)):
        return service.get_settings(conn, user(x_user_id))

    @app.put("/api/v1/settings")
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
    def get_radar_live(x_user_id: str | None = Header(default=None)):
        from ..radar.live_goal_radar import evaluate_live

        runtime.live_tick()
        return evaluate_live(conn, to_iso(utcnow()), user_id=user(x_user_id), settings=runtime.settings)

    @app.get("/api/v1/radar/alerts")
    def get_radar_alerts(limit: int = Query(50, ge=1, le=500)):
        from ..radar.live_goal_radar import list_alerts

        return list_alerts(conn, limit)

    @app.post("/api/v1/radar/notifications/permission")
    def post_permission(body: PermissionBody, x_user_id: str | None = Header(default=None)):
        from ..radar.live_goal_radar import set_notification_permission

        return set_notification_permission(conn, user(x_user_id), body.granted, to_iso(utcnow()))

    # ───────────── diğer ─────────────
    @app.get("/api/v1/leagues")
    def get_leagues():
        return service.leagues()

    @app.get("/api/v1/system/status")
    def get_status():
        return service.system_status(conn)

    if STATIC_DIR.exists():
        app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")

        @app.get("/", include_in_schema=False)
        def index():
            return FileResponse(STATIC_DIR / "index.html")

    return app
