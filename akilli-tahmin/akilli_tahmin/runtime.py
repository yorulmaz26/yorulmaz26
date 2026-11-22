"""Uygulama çalışma zamanı: veritabanı bağlantısı, motor, sentetik demo akışının tazelenmesi."""
from __future__ import annotations

import logging
import threading
from dataclasses import dataclass, field
from pathlib import Path

from .config import SETTINGS, Settings
from .db.database import connect, from_iso, init_db, to_iso, utcnow
from .selection.engine import Engine, settle_fixture_results

DEMO_REFRESH_MINUTES = 15
log = logging.getLogger("akilli_tahmin")


@dataclass
class Runtime:
    db_path: str
    settings: Settings = SETTINGS
    lock: threading.RLock = field(default_factory=threading.RLock)
    _engine: Engine | None = None
    _refreshing: bool = False
    _last_refresh_attempt: str | None = None
    last_refresh_report: dict | None = None
    last_window_tick: dict | None = None

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

    def refresh_real_if_stale(self, now: str | None = None, *, hours: float = 6.0, background: bool = True) -> bool:
        """Gerçek veritabanında son çalıştırma `hours` saatten eskiyse ücretsiz kaynaktan (football-data.co.uk)
        yeni sonuçları ve yaklaşan maçları alıp seçim sürecini yeniden çalıştırır.

        - Ligler, kurulumda kaydedilen ayardan (`real-data`) veya veritabanındaki organizasyonlardan gelir;
          başka lig eklenmez.
        - İndirme veritabanı kilidi dışında, varsayılan olarak arka planda yapılır; istek beklemez.
        - Başarısız denemeden sonra bir saat beklenir; hata isteği bozmaz (500 dönmez), kayda yazılır.
        Dönüş: güncelleme başlatıldıysa True.
        """
        now = now or to_iso(utcnow())
        if self.is_synthetic():
            return False
        with self.lock:
            from .data.football_data_feed import codes_for_leagues, load_config

            cfg = load_config(self.conn)
            if cfg is None:
                leagues = {r[0] for r in self.conn.execute(
                    "SELECT DISTINCT league_id FROM fixtures WHERE source='football-data-csv'")}
                cfg = codes_for_leagues(leagues)
            codes, extra = cfg
            if not codes and not extra:
                return False
            row = self.conn.execute(
                "SELECT as_of FROM prediction_runs WHERE kind='prematch' AND status='finished' ORDER BY as_of DESC LIMIT 1"
            ).fetchone()
            if row and (from_iso(now) - from_iso(row[0])).total_seconds() < hours * 3600:
                return False
            if self._refreshing or (self._last_refresh_attempt and
                                    (from_iso(now) - from_iso(self._last_refresh_attempt)).total_seconds() < 3600):
                return False
            self._refreshing = True
            self._last_refresh_attempt = now
        if background:
            threading.Thread(target=self._do_real_refresh, args=(now, codes, extra), daemon=True).start()
        else:
            self._do_real_refresh(now, codes, extra)
        return True

    def _do_real_refresh(self, now: str, codes: tuple[str, ...], extra: tuple[str, ...]) -> None:
        from .data.football_data_feed import Fetcher, plan_files, prefetch, sync

        try:
            # Önceki sezon da alınır: sezon geçişinde (Temmuz) son haftaların sonuçları kaçmaz.
            plans = plan_files(now, codes, 2, extra)
            files = prefetch(plans, Fetcher(Path(self.db_path + ".cache")))  # kilit dışında
            with self.lock:
                report = sync(self.conn, as_of=now, codes=codes, seasons=2, include_extra=extra, fetcher=files)
                self.engine.football.invalidate()
                self.run_prematch(now)
                self.last_refresh_report = {"at": now, "ok": not report["errors"], "errors": report["errors"],
                                            "postponed": report.get("postponed", 0)}
        except Exception as exc:  # güncelleme hatası istekleri etkilemez
            self.last_refresh_report = {"at": now, "ok": False, "errors": [f"{type(exc).__name__}: {exc}"]}
            log.warning("Gerçek veri güncellemesi başarısız: %s", exc)
        finally:
            self._refreshing = False

    def start_window_radar(self, interval_seconds: float = 30.0, client=None) -> bool:
        """Son 15 Dakika Radarı'nı arka planda çalıştırır (yalnızca gerçek veri). İstekler bütçe ve olasılık
        kapılarından geçer; ağ çağrıları veritabanı kilidi dışında yapılır."""
        if self.is_synthetic() or getattr(self, "_window_thread", None) is not None:
            return False
        from .data.api_football import Client
        from .radar import window_radar

        client = client or Client()
        stop = self._window_stop = threading.Event()

        def loop() -> None:
            while not stop.wait(interval_seconds):
                try:
                    self.last_window_tick = window_radar.tick(self.conn, client, lock=self.lock,
                                                              settings=self.settings)
                except Exception as exc:  # radar hatası sunucuyu durdurmaz
                    self.last_window_tick = {"error": f"{type(exc).__name__}: {exc}"}
                    log.warning("Son 15 dakika radarı adımı başarısız: %s", exc)

        self._window_thread = threading.Thread(target=loop, name="window-radar", daemon=True)
        self._window_thread.start()
        return True

    def stop_window_radar(self) -> None:
        if getattr(self, "_window_stop", None) is not None:
            self._window_stop.set()
            self._window_thread = None

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
