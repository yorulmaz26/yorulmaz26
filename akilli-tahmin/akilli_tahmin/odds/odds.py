"""Oran doğrulama, piyasa marjı, marjdan arındırma ve beklenen değer (EV).

EV = (p × decimal_odds) − 1. İade (void) olasılığı olan marketlerde (ör. beraberlikte iade)
EV = p_kazanç × oran + p_iade − 1 olarak hesaplanır.
"""
from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Sequence

from ..db.database import from_iso


@dataclass(frozen=True)
class OddsQuote:
    snapshot_id: int
    fixture_id: int
    market_id: str
    outcome_id: str
    decimal_odds: float
    source: str
    source_label: str
    source_type: str
    is_iddaa: bool
    source_verified: bool
    captured_at: str
    is_live: bool
    timestamp_precision: str


@dataclass(frozen=True)
class OddsValidation:
    valid: bool
    code: str
    detail: str
    age_seconds: float | None


def validate_quote(quote: OddsQuote | None, at: str, max_age_seconds: float, *, live: bool = False,
                   allow_approximate: bool = False) -> OddsValidation:
    """Oranın gerçek, doğrulanmış ve güncel olduğunu sınar.

    - Kaynak doğrulanmış olmalı (doğrulanmamış İddaa bağlantısı dahil kabul edilmez).
    - Zaman damgası kesin olmalı (yaklaşık zamanlı geçmiş CSV oranları güncel öneride kullanılmaz;
      yalnızca geçmiş testte `allow_approximate=True` ile kabul edilir).
    - Oran, değerlendirme anından *önce* yakalanmış olmalı (gelecekten oran = sızıntı).
    - Oran yaşı sınırı aşmamalı.
    """
    if quote is None:
        return OddsValidation(False, "odds_missing", "Gerçek oran bulunamadı.", None)
    if not (quote.decimal_odds > 1.0 and math.isfinite(quote.decimal_odds)):
        return OddsValidation(False, "odds_invalid_value", f"Geçersiz oran değeri: {quote.decimal_odds}", None)
    if not quote.source_verified:
        return OddsValidation(False, "odds_source_unverified", f"Oran kaynağı doğrulanmamış: {quote.source_label}", None)
    if quote.timestamp_precision != "exact" and not allow_approximate:
        return OddsValidation(False, "odds_timestamp_approximate",
                              "Oran zaman damgası yaklaşık; güncel seçimde kullanılamaz.", None)
    if quote.is_live != live:
        return OddsValidation(False, "odds_wrong_phase",
                              "Canlı/maç öncesi oran türü değerlendirmeyle uyuşmuyor.", None)
    age = (from_iso(at) - from_iso(quote.captured_at)).total_seconds()
    if age < 0:
        return OddsValidation(False, "odds_from_future", "Oran değerlendirme anından sonra yakalanmış.", age)
    if quote.timestamp_precision != "exact" and allow_approximate:
        # Yaklaşık zamanlı geçmiş oranlar (ör. football-data.co.uk) maç öncesi fiyat kabul edilir;
        # yakalanma anı bilinmediği için yaş sınırı uygulanmaz, yalnızca geçmiş testte.
        return OddsValidation(True, "ok_approximate", "Yaklaşık zamanlı geçmiş oran (yalnızca geçmiş test).", age)
    if age > max_age_seconds:
        return OddsValidation(False, "odds_stale", f"Oran {int(age)} sn önce alınmış; sınır {int(max_age_seconds)} sn.", age)
    return OddsValidation(True, "ok", "Doğrulanmış ve güncel oran.", age)


def overround(odds: Sequence[float]) -> float:
    """Piyasa marjı: Σ 1/oran − 1."""
    return sum(1.0 / o for o in odds) - 1.0


def devig_proportional(odds: Sequence[float]) -> list[float]:
    inv = [1.0 / o for o in odds]
    s = sum(inv)
    return [x / s for x in inv]


def devig_power(odds: Sequence[float], tol: float = 1e-10) -> list[float]:
    """Güç yöntemi: p_i = (1/o_i)^k, Σ p_i = 1 olacak k bulunur (favori-sürpriz eğilimini düzeltir)."""
    inv = [1.0 / o for o in odds]
    if abs(sum(inv) - 1.0) < 1e-12:
        return inv
    lo, hi = 0.2, 5.0
    for _ in range(200):
        k = 0.5 * (lo + hi)
        s = sum(x ** k for x in inv)
        if s > 1.0:
            lo = k
        else:
            hi = k
        if hi - lo < tol:
            break
    k = 0.5 * (lo + hi)
    p = [x ** k for x in inv]
    s = sum(p)
    return [x / s for x in p]


def devig_shin(odds: Sequence[float], tol: float = 1e-12) -> list[float]:
    """Shin (1993) yöntemi: içeriden bilgili bahisçi payı z ile marjdan arındırma."""
    inv = [1.0 / o for o in odds]
    b = sum(inv)
    if b <= 1.0:
        return [x / b for x in inv]

    def probs(z: float) -> list[float]:
        return [(math.sqrt(z * z + 4 * (1 - z) * (x * x) / b) - z) / (2 * (1 - z)) for x in inv]

    lo, hi = 0.0, 0.999
    for _ in range(200):
        z = 0.5 * (lo + hi)
        s = sum(probs(z))
        if s > 1.0:
            lo = z
        else:
            hi = z
        if hi - lo < tol:
            break
    p = probs(0.5 * (lo + hi))
    s = sum(p)
    return [x / s for x in p]


def fair_probabilities(odds: Sequence[float], method: str = "power") -> list[float]:
    if method == "proportional":
        return devig_proportional(odds)
    if method == "shin":
        return devig_shin(odds)
    return devig_power(odds)


def expected_value(p_win: float, decimal_odds: float, p_void: float = 0.0) -> float:
    """EV = p × oran − 1 (iade olasılığı varsa iade edilen stake eklenir)."""
    return p_win * decimal_odds + p_void - 1.0


def kelly_fraction(p_win: float, decimal_odds: float, p_void: float = 0.0) -> float:
    """Kelly oranı f* = (b·p − q) / b; b = oran − 1, q = kaybetme olasılığı (iade kütlesi stake'i değiştirmez)."""
    b = decimal_odds - 1.0
    if b <= 0:
        return -1.0
    q = max(0.0, 1.0 - p_win - p_void)
    return (b * p_win - q) / b
