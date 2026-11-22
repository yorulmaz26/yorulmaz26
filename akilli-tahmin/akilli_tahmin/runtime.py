"""Uygulama çalışma zamanı: veritabanı bağlantısı, motor, sentetik demo akışının tazelenmesi."""
from __future__ import annotations

import threading
from dataclasses import dataclass, field

from .config import SETTINGS, Settings
from .db.database import connect, from_iso, init_db, to_iso, utcnow
from .selection.engine import Engine, settle_fixture_results

DEMO_REFRESH_MINUTES = 15


@dataclass
class Runtime:
    db_path: str
    settings: Settings = SETTINGS
    lock: threading.RLock = field(default_factory=threading.RLock)
    _engine: Engine | None = None

    def __post_init__(self) -> None:
        self.conn = connect(self.db_path)
        init_db(self.conn)
        if self.is_synthetic():
            # Eski demo veritabanlarında gol dakikaları yoksa üret (idempotent); radar geçmişi buna dayanır.
            from .data.synthetic_live import generate_goal_events

            generate_goal_events(self.conn, to_iso(utcnow()))

    @property
    def engine(self) -> Engine:
        if self._engine is None:
            self._engine = Engine(self.conn, self.settings)
        return self._engine

    def is_synthetic(self) -> bool:
        from .data.synthetic import is_synthetic_db

        return is_synthetic_db(self.conn)

    def run_prematch(self, now: str | None = None) -> dict:
        """Gerçek veriyle çalışan üretim döngüsü: sonuçları işle, yaklaşan maçları değerlendir, performansı kaydet."""
        now = now or to_iso(utcnow())
        with self.lock:
            settle_fixture_results(self.conn, self.engine.history, now)
            summary = self.engine.run(now)
            self._snapshot(now)
            return {"run_id": summary.run_id, "fixtures": summary.fixtures, "selected": summary.selected}

    def refresh_demo_if_stale(self, now: str | None = None) -> bool:
        """Sentetik demo veritabanında son çalıştırma eskiyse sentetik oran akışını ilerletip yeniden çalıştırır."""
        now = now or to_iso(utcnow())
        if not self.is_synthetic():
            return False
        with self.lock:
            row = self.conn.execute(
                "SELECT as_of FROM prediction_runs WHERE kind='prematch' AND status='finished' ORDER BY as_of DESC LIMIT 1"
            ).fetchone()
            if row and (from_iso(now) - from_iso(row[0])).total_seconds() < DEMO_REFRESH_MINUTES * 60:
                return False
            from .data.synthetic import play_due_fixtures, refresh_prematch_odds
            from .data.synthetic_live import generate_goal_events

            # Sentetik akış: başlama saati geçmiş demo maçları oynatılır (sonuçlar kaydedilir, kararlar
            # sonuçlanır), yaklaşan maçlar için yeni oran anlık görüntüsü eklenir, ardından süreç çalışır.
            if play_due_fixtures(self.conn, now):
                generate_goal_events(self.conn, now)
                self.engine.football.invalidate()
            refresh_prematch_odds(self.conn, now)
            self.run_prematch(now)
            return True

    def live_tick(self, now: str | None = None) -> int:
        now = now or to_iso(utcnow())
        if not self.is_synthetic():
            return 0
        from .data.synthetic import ensure_live_demo, simulate_live_tick

        with self.lock:
            started = ensure_live_demo(self.conn, now)
            return started + simulate_live_tick(self.conn, now)

    def _snapshot(self, now: str) -> None:
        try:
            from .performance.tracker import snapshot_performance
        except ImportError:  # pragma: no cover
            return
        snapshot_performance(self.conn, now)
