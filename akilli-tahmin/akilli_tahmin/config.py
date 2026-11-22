"""Sistem genelindeki ayarlar.

Buradaki değerler *seçim eşikleri değildir*. Seçim modlarının olasılık, beklenen değer (EV),
belirsizlik ve model uyuşmazlığı eşikleri kronolojik doğrulama verisinden
`selection/thresholds.py` ile belirlenir; sabit %70 olasılık veya %10 EV gibi eşikler yoktur.

Buradakiler veri yeterliliği, güncellik, istatistiksel güven düzeyi ve Canlı Gol Radar'ın
kullanıcı tarafından belirlenmiş zorunlu şartları gibi politika değerleridir.
"""
from __future__ import annotations

import os
from dataclasses import dataclass, field

VERSION = "6.3.0"

#: Sistemde yalnızca iki ana tahmin modeli bulunur.
PREDICTION_MODELS = ("A", "B")


@dataclass(frozen=True)
class DataPolicy:
    #: Bir takımın modele girebilmesi için zaman kesiminden önce oynanmış en az maç sayısı.
    min_team_matches: int = 6
    #: Ligdeki en az geçmiş maç sayısı (model uydurma için).
    min_league_matches: int = 60
    #: Maç öncesi tahmin için en son sonuç verisinin en fazla yaşı (gün). Daha eskiyse veri "eski" sayılır.
    max_result_data_age_days: int = 21
    #: Maç öncesi oran anlık görüntüsünün seçim anında en fazla yaşı (saniye).
    max_prematch_odds_age_seconds: int = 30 * 60
    #: Oran değişimi bu göreli eşikten büyükse eski EV hesabı geçersiz sayılır.
    odds_move_tolerance: float = 0.02


@dataclass(frozen=True)
class StatisticalPolicy:
    #: Alt/üst güven sınırları için tek yönlü güven düzeyi (0.90 → %10 ve %90 yüzdelikleri).
    confidence_level: float = 0.90
    #: Belirsizlik için Laplace örneklem sayısı.
    uncertainty_draws: int = 160
    #: Kalibrasyon testlerinin anlamlılık düzeyi.
    calibration_alpha: float = 0.05
    #: Bir market ailesinde kalibrasyon haritası kurmak için en az örnek.
    min_calibration_samples: int = 300
    #: Adayın düştüğü yerel olasılık bölgesinde gereken en az örnek.
    min_local_calibration_samples: int = 40
    #: Model B'nin bir market ailesinde eğitilebilmesi için en az örnek.
    min_model_b_samples: int = 300
    #: Eşik ayarı için doğrulama penceresinde gereken en az seçim sayısı.
    min_validation_selections: int = 60
    #: Oranlı modlarda (getiri varyansı yüksek) doğrulama penceresinde gereken en az seçim.
    min_validation_selections_odds: int = 150
    #: Bootstrap tekrar sayısı (ROI güven aralığı).
    bootstrap_reps: int = 400


@dataclass(frozen=True)
class RadarPolicy:
    """Canlı Gol Radar zorunlu şartları.

    Kullanıcı talebi (v6.3 sonrası): "Gol radar oranını yüzde 90 yap" → iki model için de eşik %90.
    Şartlar *kesin büyüktür* (>) olarak uygulanır. Seçim modları bu değerleri değiştiremez.
    """

    model_a_min_probability: float = 0.90
    model_b_min_probability: float = 0.90
    min_remaining_minutes: int = 15
    live_data_max_age_seconds: int = 60
    live_odds_max_age_seconds: int = 60
    #: Nadir olay (yüksek olasılık bölgesi) kalibrasyonu için o bölgede gereken en az geçmiş örnek.
    min_rare_event_samples: int = 150
    #: Mükerrer alarm engeli: aynı maç ve hedef için tek alarm.
    duplicate_block: bool = True


@dataclass(frozen=True)
class Settings:
    db_path: str = field(default_factory=lambda: os.environ.get("AKILLI_TAHMIN_DB", "akilli_tahmin.sqlite3"))
    data: DataPolicy = field(default_factory=DataPolicy)
    stats: StatisticalPolicy = field(default_factory=StatisticalPolicy)
    radar: RadarPolicy = field(default_factory=RadarPolicy)
    default_selection_mode: str = "balanced"


SETTINGS = Settings()
