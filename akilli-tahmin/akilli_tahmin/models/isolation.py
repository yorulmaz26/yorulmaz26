"""MODEL A oran izolasyonu ve MODEL B zaman sızıntısı korumaları.

- MODEL A yalnızca futbol verisi (sonuçlar, ilk yarı skorları) kullanır. Oran içeren her girdi
  `OddsIsolationError` ile reddedilir; ayrıca veritabanı şeması MODEL A tahmininin `uses_odds = 1`
  olarak kaydedilmesini engeller.
- MODEL B yalnızca tahmin anında (zaman kesimi) erişilebilir bilgiyi kullanabilir. Kesimden sonra
  bilinen sonuç, kesimden sonra yakalanmış oran veya out-of-fold olmayan MODEL A çıktısı
  `TemporalLeakageError` ile reddedilir.
"""
from __future__ import annotations

from dataclasses import fields, is_dataclass
from typing import Any, Iterable

ODDS_KEYWORDS = ("odds", "oran", "price", "implied", "bookmaker", "margin", "overround", "market_prob", "fair_prob")


class OddsIsolationError(RuntimeError):
    """MODEL A'ya oran verisi ulaştı."""


class TemporalLeakageError(RuntimeError):
    """Zaman kesiminden sonra erişilebilen bilgi kullanılmaya çalışıldı."""


def _names(obj: Any) -> Iterable[str]:
    if is_dataclass(obj):
        return [f.name for f in fields(obj)]
    if isinstance(obj, dict):
        return [str(k) for k in obj.keys()]
    return [n for n in dir(obj) if not n.startswith("_")]


def assert_football_only(records: Iterable[Any], *, context: str = "MODEL A") -> None:
    """Girdilerde oranla ilişkili alan bulunmadığını doğrular (her kayıt türü bir kez incelenir)."""
    seen_types: set[type] = set()
    for rec in records:
        if is_dataclass(rec) and type(rec) in seen_types:
            continue
        for name in _names(rec):
            low = name.lower()
            if any(k in low for k in ODDS_KEYWORDS):
                raise OddsIsolationError(f"{context} oran verisi kullanamaz: '{name}' alanı reddedildi")
        if is_dataclass(rec):
            seen_types.add(type(rec))


def assert_known_before(items: Iterable[tuple[str, str | None]], cutoff: str, *, what: str) -> None:
    """(kimlik, bilinme_zamanı) çiftlerinin hepsi kesimden önce/eşit olmalı."""
    for ident, known_at in items:
        if known_at is None or known_at > cutoff:
            raise TemporalLeakageError(
                f"{what} zaman kesiminden ({cutoff}) sonra bilinen veri içeriyor: {ident} → {known_at}"
            )
