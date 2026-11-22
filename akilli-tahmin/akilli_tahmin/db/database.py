"""SQLite bağlantısı, şema kurulumu ve temel kayıtlar."""
from __future__ import annotations

import json
import sqlite3
import threading
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Iterable

from ..domain.markets import MARKETS, SETTLEMENT_RULE_TEXT, SETTLEMENT_RULE_VERSION
from ..leagues import LEAGUES

SCHEMA_PATH = Path(__file__).with_name("schema.sql")

SELECTION_MODES = (
    {
        "code": "highest_probability",
        "name": "En Güvenilir Tahminler",
        "short_name": "En Güvenilir",
        "description": (
            "Veri ve kalibrasyon bakımından yeterli seçenekler arasından gerçekleşme olasılığı en yüksek "
            "marketleri arar. Oran yüksekliği zorunlu değildir; sıralama kalibrasyon ve belirsizlik "
            "kontrollerinden geçen olasılık alt sınırına göredir."
        ),
        "requires_odds": 0,
        "ranking": "Kalibre edilmiş olasılığın ihtiyatlı alt sınırı",
        "is_default": 0,
        "sort_order": 1,
    },
    {
        "code": "best_value",
        "name": "En Avantajlı Oranlar",
        "short_name": "En Avantajlı",
        "description": (
            "Kalibre edilmiş olasılık ile doğrulanmış güncel oran arasındaki matematiksel farkı "
            "(EV = p × oran − 1) değerlendirir. Avantaj yalnızca model tahmini doğruysa geçerlidir."
        ),
        "requires_odds": 1,
        "ranking": "Belirsizlik altında EV alt sınırı",
        "is_default": 0,
        "sort_order": 2,
    },
    {
        "code": "balanced",
        "name": "Dengeli Seçimler",
        "short_name": "Dengeli",
        "description": (
            "Yüksek gerçekleşme olasılığı ile olumlu beklenen değeri birlikte arar; on koşulun tamamını "
            "sağlayan seçimleri önerir."
        ),
        "requires_odds": 1,
        "ranking": "Belirsizlik altında ihtiyatlı Kelly oranı",
        "is_default": 1,
        "sort_order": 3,
    },
)

MODE_CODES = tuple(m["code"] for m in SELECTION_MODES)
DEFAULT_MODE = "balanced"

ODDS_SOURCES = (
    # (code, label, type, is_iddaa, verified, notes)
    ("synthetic-demo", "Sentetik demo bahisçisi", "synthetic", 0, 1,
     "Geliştirme ve test için üretilmiş oranlar; gerçek oran değildir."),
    ("football-data-csv", "football-data.co.uk (Bet365 / piyasa ortalaması)", "historical_csv", 0, 1,
     "Ücretsiz dosyalar; yakalanma zamanı yaklaşık: yalnızca geriye dönük testte kullanılır, güncel öneride "
     "reddedilir. İddaa oranı değildir."),
    ("iddaa", "İddaa (resmi)", "iddaa", 1, 0,
     "Resmi veri bağlantısı kurulmadan doğrulanmış sayılmaz."),
    ("manual", "Elle girilen oran (kullanıcı)", "bookmaker", 0, 1,
     "Kullanıcının bir bahis sitesinde (ör. İddaa) gördüğü ve elle girdiği oran; kullanıcının beyanıdır, "
     "otomatik doğrulanmaz ve İddaa olarak gösterilmez."),
)


# ───────────────────────── zaman yardımcıları ─────────────────────────

def utcnow() -> datetime:
    return datetime.now(timezone.utc).replace(microsecond=0)


def to_iso(dt: datetime) -> str:
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def from_iso(s: str) -> datetime:
    return datetime.strptime(s, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)


def iso_add(s: str, **kw: float) -> str:
    return to_iso(from_iso(s) + timedelta(**kw))


def dumps(obj: Any) -> str:
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":"), default=_json_default)


def loads(s: str | None, default: Any = None) -> Any:
    if s is None:
        return default
    return json.loads(s)


def _json_default(o: Any) -> Any:
    try:
        import numpy as np

        if isinstance(o, np.generic):
            return o.item()
        if isinstance(o, np.ndarray):
            return o.tolist()
    except ImportError:  # pragma: no cover
        pass
    if isinstance(o, datetime):
        return to_iso(o)
    raise TypeError(f"JSON'a çevrilemiyor: {type(o)}")


# ───────────────────────── bağlantı ─────────────────────────

def connect(path: str | Path = ":memory:") -> sqlite3.Connection:
    conn = sqlite3.connect(str(path), check_same_thread=False, isolation_level=None)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    if str(path) != ":memory:":
        conn.execute("PRAGMA journal_mode = WAL")
    return conn


def init_db(conn: sqlite3.Connection, now: str | None = None) -> None:
    now = now or to_iso(utcnow())
    conn.executescript(SCHEMA_PATH.read_text(encoding="utf-8"))
    with transaction(conn):
        for lg in LEAGUES:
            conn.execute(
                "INSERT OR IGNORE INTO leagues(id, name, country, type) VALUES (?,?,?,?)",
                (lg.id, lg.name, lg.country, lg.type),
            )
        for m in SELECTION_MODES:
            conn.execute(
                "INSERT OR IGNORE INTO selection_modes(code, name, short_name, description, requires_odds,"
                " ranking, is_default, sort_order) VALUES (:code,:name,:short_name,:description,:requires_odds,"
                " :ranking,:is_default,:sort_order)",
                m,
            )
        for code, label, typ, is_iddaa, verified, notes in ODDS_SOURCES:
            conn.execute(
                "INSERT OR IGNORE INTO odds_sources(code, label, source_type, is_iddaa, verified, verified_at, notes)"
                " VALUES (?,?,?,?,?,?,?)",
                (code, label, typ, is_iddaa, verified, now if verified else None, notes),
            )
        for mk in MARKETS:
            for source in ("standard", "synthetic-demo", "football-data-csv", "manual"):
                conn.execute(
                    "INSERT OR IGNORE INTO market_settlement_rules(market_id, source, rule_version, rule_text,"
                    " verified, verified_at, verified_by) VALUES (?,?,?,?,1,?,?)",
                    (mk.id, source, SETTLEMENT_RULE_VERSION, SETTLEMENT_RULE_TEXT, now, "sistem"),
                )


class transaction:
    """`isolation_level=None` bağlantıda açık BEGIN/COMMIT; iç içe kullanımda SAVEPOINT.

    İç içelik derinliği iş parçacığına ve bağlantıya özeldir. Bir bağlantının aynı anda birden çok
    iş parçacığından kullanılması desteklenmez: API katmanı bütün veritabanı erişimini tek kilitle
    sıralar (`Runtime.lock`).
    """

    def __init__(self, conn: sqlite3.Connection):
        self.conn = conn

    def __enter__(self) -> sqlite3.Connection:
        depths = _depths()
        depth = depths.get(id(self.conn), 0)
        if depth == 0 and self.conn.in_transaction:
            # Dışarıda açık kalmış bir işlem varsa iç içe kabul edilir (kaydetme noktasıyla).
            depth = 1
            depths[id(self.conn)] = 1
            self._adopted = True
        else:
            self._adopted = False
        self.conn.execute("BEGIN" if depth == 0 else f"SAVEPOINT sp{depth}")
        depths[id(self.conn)] = depth + 1
        return self.conn

    def __exit__(self, exc_type, exc, tb) -> bool:
        depths = _depths()
        depth = max(0, depths.get(id(self.conn), 1) - 1)
        depths[id(self.conn)] = depth
        if depth == 0:
            self.conn.execute("ROLLBACK" if exc_type else "COMMIT")
        else:
            if exc_type:
                self.conn.execute(f"ROLLBACK TO sp{depth}")
            self.conn.execute(f"RELEASE sp{depth}")
            if self._adopted and depth == 1:
                depths[id(self.conn)] = 0
        return False


_LOCAL = threading.local()


def _depths() -> dict[int, int]:
    d = getattr(_LOCAL, "depths", None)
    if d is None:
        d = _LOCAL.depths = {}
    return d


def insert(conn: sqlite3.Connection, table: str, row: dict[str, Any]) -> int:
    cols = list(row.keys())
    sql = f"INSERT INTO {table} ({', '.join(cols)}) VALUES ({', '.join('?' for _ in cols)})"
    cur = conn.execute(sql, [row[c] for c in cols])
    return int(cur.lastrowid)


def insert_many(conn: sqlite3.Connection, table: str, rows: Iterable[dict[str, Any]]) -> None:
    rows = list(rows)
    if not rows:
        return
    cols = list(rows[0].keys())
    sql = f"INSERT INTO {table} ({', '.join(cols)}) VALUES ({', '.join('?' for _ in cols)})"
    conn.executemany(sql, [[r[c] for c in cols] for r in rows])
