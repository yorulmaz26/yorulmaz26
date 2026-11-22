"""Kontrol kodları, Türkçe etiketleri ve sıkıştırılmış saklama biçimi.

Kontroller veritabanında `[kod, geçti(0/1), değer, eşik, ayrıntı]` listesi olarak saklanır;
geçen kontrollerde ayrıntı boş bırakılır (boyut), API ayrıntıyı etiketlerden yeniden kurar.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any

CHECK_LABELS: dict[str, str] = {
    # Tahmin Kalite Kapısı — moddan bağımsız
    "fixture_ok": "Fikstür ve veri durumu doğrulandı",
    "data_fresh": "Veri güncel",
    "data_sufficient": "Veri yeterli",
    "settlement_rules": "Market sonuçlandırma kuralları doğrulandı",
    "model_a_valid": "MODEL A geçerli",
    "model_b_valid": "MODEL B geçerli",
    "probability_consistency": "Olasılık tutarlılığı",
    "calibration_a": "MODEL A kalibrasyonu yeterli",
    "calibration_b": "MODEL B kalibrasyonu yeterli",
    "prediction_valid": "Tahmin geçerli",
    "odds_valid": "Gerçek, doğrulanmış ve güncel oran",
    "odds_settlement_rules": "Oran kaynağının sonuçlandırma kuralları doğrulandı",
    # Seçim modu kontrolleri
    "thresholds_established": "Eşikler kronolojik doğrulamayla belirlendi",
    "prob_lower": "Kalibre olasılığın ihtiyatlı alt sınırı",
    "ev_models_positive": "MODEL A ve MODEL B ayrı ayrı pozitif EV",
    "ev_lower": "Belirsizlik altında EV avantajı",
    "uncertainty": "Belirsizlik kabul edilebilir düzeyde",
    "disagreement": "Model uyuşmazlığı kontrolü",
    "market_reference": "Marjdan arındırılmış piyasa referansıyla uyum",
    "robustness": "Tahmin dayanıklılık testi",
    "nested_duplicate": "İç içe market tekrarı yok",
    "audit": "Bağımsız seçim denetimi",
}

#: Tahmin Kalite Kapısı'nın moddan bağımsız kontrolleri (sırasıyla).
CORE_CHECKS: tuple[str, ...] = (
    "fixture_ok", "data_fresh", "data_sufficient", "settlement_rules", "model_a_valid", "model_b_valid",
    "probability_consistency", "calibration_a", "calibration_b", "prediction_valid",
)
ODDS_CHECKS: tuple[str, ...] = ("odds_valid", "odds_settlement_rules")

#: Dengeli modun 10 koşulu → kontrol kodları (arayüzde numaralı gösterim için).
BALANCED_CONDITIONS: tuple[tuple[int, str, tuple[str, ...]], ...] = (
    (1, "Veri güncel ve yeterli", ("fixture_ok", "data_fresh", "data_sufficient")),
    (2, "Market sonuçlandırma kuralları doğrulandı", ("settlement_rules", "odds_settlement_rules")),
    (3, "MODEL A ve MODEL B geçerli", ("model_a_valid", "model_b_valid", "probability_consistency")),
    (4, "Kalibrasyon yeterli", ("calibration_a", "calibration_b")),
    (5, "Belirsizlik kabul edilebilir", ("uncertainty", "prob_lower")),
    (6, "Tahmin dayanıklılık testi uygun", ("robustness",)),
    (7, "Model uyuşmazlığı kontrol edildi", ("disagreement",)),
    (8, "Gerçek ve güncel oran", ("odds_valid", "market_reference")),
    (9, "EV avantajı belirsizlik altında da destekleniyor", ("ev_models_positive", "ev_lower")),
    (10, "Bağımsız seçim denetimi başarılı", ("audit",)),
)


@dataclass
class Check:
    code: str
    passed: bool
    value: Any = None
    threshold: Any = None
    detail: str = ""

    def pack(self, with_detail: bool = True) -> list:
        v = _round(self.value)
        t = _round(self.threshold)
        if not with_detail or self.passed:
            return [self.code, 1 if self.passed else 0, v, t]
        return [self.code, 1 if self.passed else 0, v, t, self.detail]

    def as_api(self) -> dict:
        return {
            "code": self.code,
            "label": CHECK_LABELS.get(self.code, self.code),
            "passed": self.passed,
            "detail": self.detail or ("Sağlandı." if self.passed else "Sağlanmadı."),
            "value": self.value,
            "threshold": self.threshold,
        }


def _round(x: Any) -> Any:
    if isinstance(x, float):
        return round(x, 5)
    if isinstance(x, list):
        return [_round(v) for v in x]
    return x


def describe(code: str, passed: bool, value: Any, threshold: Any) -> str:
    """Saklanmış değer/eşikten Türkçe ayrıntı metni üretir (mod kontrolleri ayrıntısız saklanır)."""
    v, t = value, threshold
    if code == "prob_lower":
        return f"Olasılık alt sınırı {fmt_num(v)} {'≥' if passed else '<'} eşik {fmt_num(t)}"
    if code == "ev_lower":
        return f"EV alt sınırı {fmt_pct(v)} {'>' if passed else '≤'} eşik {fmt_pct(t)}"
    if code == "ev_models_positive":
        a, b = (v or [None, None]) if isinstance(v, list) else (None, None)
        return f"EV MODEL A {fmt_pct(a)}, MODEL B {fmt_pct(b)} (ikisi de pozitif olmalı)"
    if code == "uncertainty":
        return f"Göreli belirsizlik (%80 aralık / p) {fmt_num(v)} {'≤' if passed else '>'} {fmt_num(t)}"
    if code == "disagreement":
        return f"MODEL A/B uyuşmazlığı (logit farkı) {fmt_num(v)} {'≤' if passed else '>'} {fmt_num(t)}"
    if code == "market_reference":
        if v is None:
            return "Marjdan arındırılmış piyasa olasılığı hesaplanamadı (market eksik fiyatlanmış)."
        return f"Piyasa referansından sapma (logit) {fmt_num(v)} {'≤' if passed else '>'} {fmt_num(t)}"
    if code == "robustness":
        return ("Bütün alternatif model kurulumlarında eşikler sağlanıyor"
                if passed else "Alternatif model kurulumlarından en az birinde eşik sağlanmıyor") + (
                    f" (en kötü kayma {fmt_num(v)})" if isinstance(v, (int, float)) else "")
    if code == "odds_valid":
        return "Doğrulanmış ve güncel gerçek oran mevcut." if passed else "Bu mod doğrulanmış ve güncel gerçek oran gerektirir."
    if code == "thresholds_established":
        return ("Eşikler kronolojik doğrulamayla belirlendi." if passed
                else "Eşikler henüz kronolojik doğrulamayla belirlenmedi; mod seçim üretmiyor.")
    if code == "nested_duplicate":
        return "İç içe market tekrarı yok." if passed else "Daha üst sıradaki bir seçimle iç içe (aynı olayın alt kümesi)."
    if code == "audit":
        return "Bağımsız denetimden geçti." if passed else "Bağımsız denetimden kaldı."
    return "Sağlandı." if passed else "Sağlanmadı."


def unpack(rows: list | None) -> list[Check]:
    out = []
    for r in rows or []:
        code, passed, value, threshold, detail = (list(r) + [None] * 5)[:5]
        out.append(Check(code, bool(passed), value, threshold, detail or describe(code, bool(passed), value, threshold)))
    return out


def fmt_num(x: float | None, digits: int = 3) -> str:
    if x is None:
        return "—"
    return f"{x:.{digits}f}".replace(".", ",")


def fmt_pct(x: float | None, digits: int = 1) -> str:
    if x is None:
        return "—"
    return "%" + f"{100 * x:.{digits}f}".replace(".", ",")
