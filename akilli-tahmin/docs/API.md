# Akıllı Tahmin — API sözleşmesi (v6.3)

Tüm uçlar `/api/v1` altındadır, JSON döner, zamanlar ISO‑8601 UTC (`2026-11-21T18:00:00Z`) biçimindedir.
Olasılıklar 0–1 arası ondalık, beklenen değer (EV) birim bahis başına ondalık getiridir (`0.05` = %5).
Kullanıcı kimliği `X-User-Id` başlığıyla gelir; yoksa `local` kabul edilir.

Seçim modu kodları:

| Kod | Ad | Kısa ad |
|---|---|---|
| `balanced` | Dengeli Seçimler (varsayılan) | Dengeli |
| `highest_probability` | En Güvenilir Tahminler | En Güvenilir |
| `best_value` | En Avantajlı Oranlar | En Avantajlı |

Seçim modları üçüncü bir tahmin modeli değildir: MODEL A ve MODEL B olasılıkları modlar arasında **aynıdır**,
yalnızca uygunluk, sıralama ve gerekçe değişir.

---

## GET /api/v1/recommendations/modes

```json
{
  "default_mode": "balanced",
  "user_default_mode": "balanced",
  "modes": [
    {
      "code": "balanced",
      "name": "Dengeli Seçimler",
      "short_name": "Dengeli",
      "description": "Yüksek gerçekleşme olasılığı ile olumlu beklenen değeri birlikte arar.",
      "is_default": true,
      "requires_odds": true,
      "ranking": "Belirsizlik altında ihtiyatlı Kelly oranı",
      "thresholds": {
        "status": "established",
        "values": {"min_probability_lower": 0.58, "min_ev_lower": 0.012, "max_interval_width": 0.14, "max_disagreement": 0.35},
        "source": "chronological_validation",
        "validation_window": {"start": "2025-08-01T00:00:00Z", "end": "2026-11-20T18:00:00Z"},
        "validation_metrics": {"n": 212, "hit_rate_lower": 0.55, "roi_lower": 0.004}
      }
    }
  ]
}
```

`thresholds.status` `not_established` olabilir (yeterli kronolojik doğrulama verisi yok). Bu durumda o mod seçim üretmez.

---

## GET /api/v1/recommendations?mode=balanced

Sorgu parametreleri: `mode` (varsayılan: kullanıcının kayıtlı tercihi, o da yoksa `balanced`), isteğe bağlı `league` (lig kodu), `date` (YYYY-MM-DD).

```json
{
  "mode": "balanced",
  "mode_name": "Dengeli Seçimler",
  "run_id": 41,
  "generated_at": "2026-11-21T17:00:00Z",
  "data_cutoff_at": "2026-11-21T17:00:00Z",
  "is_synthetic_data": true,
  "message": null,
  "disclaimer": "Tahminler olasılık tahminidir; yüksek isabet oranı veya pozitif beklenen değer kazanç garantisi değildir. 18 yaş altı kullanamaz.",
  "summary": {"fixtures_scanned": 18, "candidates": 1040, "eligible": 4, "selected": 3, "fixtures_without_selection": 15},
  "items": [ /* Recommendation, aşağıda */ ]
}
```

Uygun seçim yoksa `items: []` ve `message`:
- `balanced`: `"Güvenilir ve avantajlı seçim bulunamadı."`
- `best_value`: `"Güvenilir bir avantaj belirlenemedi; seçim üretilmedi."`
- `highest_probability`: `"Kalibrasyon ve belirsizlik kontrollerinden geçen güvenilir seçim bulunamadı."`

### Recommendation nesnesi

`id` aday kimliğidir (`selection_mode_candidates.id`) ve diğer uçlarda `{id}` olarak kullanılır.

```json
{
  "id": 9812,
  "decision_id": 377,
  "rank": 1,
  "rank_score": 0.061,
  "fixture": {
    "id": 5521,
    "home": "Kartal SK",
    "away": "Liman Gücü",
    "league": {"id": "tr-super-lig", "name": "Süper Lig", "country": "Türkiye"},
    "kickoff_at": "2026-11-21T18:00:00Z"
  },
  "market": {"id": "OU_2.5", "name": "Alt/Üst 2,5", "family": "totals"},
  "outcome": {"id": "OVER", "name": "Üst"},
  "odds": {
    "decimal": 1.92,
    "source": "synthetic-demo",
    "source_label": "Sentetik demo bahisçisi",
    "is_iddaa": false,
    "captured_at": "2026-11-21T16:58:00Z",
    "age_seconds": 120,
    "margin": 0.052,
    "fair_probability": 0.495,
    "snapshot_id": 88123
  },
  "model_a": {"prediction_id": 120331, "raw": 0.583, "calibrated": 0.571, "lower": 0.541, "upper": 0.602, "ev": 0.096},
  "model_b": {"prediction_id": 120332, "raw": 0.566, "calibrated": 0.562, "lower": 0.538, "upper": 0.587, "ev": 0.079},
  "estimated_probability": 0.562,
  "probability_method": "conservative_min",
  "expected_value": {"point": 0.079, "lower": 0.033, "model_a": 0.096, "model_b": 0.079},
  "uncertainty": {"level": "low", "label": "Düşük", "interval_width": 0.064, "disagreement": 0.037},
  "robustness": {
    "status": "robust", "label": "Dayanıklı", "score": 1.0, "max_shift": 0.018,
    "scenarios": [{"name": "Zaman ağırlığı yavaş", "probability": 0.566}]
  },
  "data_freshness": {"status": "fresh", "label": "Güncel", "last_result_at": "2026-11-20T21:00:00Z", "odds_age_seconds": 120},
  "validity": {"status": "valid", "label": "Geçerli", "valid_until": "2026-11-21T18:00:00Z", "reasons": []},
  "history": {"scope": "Dengeli · totals · Süper Lig", "n": 64, "hit_rate": 0.563, "roi": 0.021},
  "reason_summary": "Kalibre edilmiş olasılık alt sınırı 0,538 ≥ 0,520 ve EV alt sınırı 0,033 > 0,012; iki model de avantajı destekliyor.",
  "audit": {"status": "passed", "label": "Denetimden geçti", "audited_at": "2026-11-21T17:00:01Z"},
  "related_selections": [{"candidate_id": 9815, "market": "BTTS", "outcome": "YES", "label": "Karşılıklı Gol · Var", "correlation": 0.41}],
  "warnings": ["Avantaj yalnızca model tahmini doğruysa geçerlidir.", "Pozitif EV kazanç garantisi değildir."]
}
```

Alan değerleri:
- `uncertainty.level`: `low` | `medium` | `high` (etiket: Düşük / Orta / Yüksek)
- `robustness.status`: `robust` | `fragile` (Dayanıklı / Kırılgan)
- `data_freshness.status`: `fresh` | `stale` (Güncel / Eski)
- `validity.status`: `valid` | `expired` (Geçerli / Süresi doldu)
- `audit.status`: `passed` | `failed` | `pending` (Denetimden geçti / Denetimden kaldı / Bekliyor)
- `odds` En Güvenilir modunda `null` olabilir (oran zorunlu değil); bu durumda `expected_value` da `null` olur.

---

## GET /api/v1/recommendations/{id}/decision-explanation

"Neden seçildi?" alanı.

```json
{
  "id": 9812,
  "mode": "balanced",
  "selected": true,
  "summary": "…",
  "config_id": 12,
  "thresholds": {"min_probability_lower": 0.52, "min_ev_lower": 0.012, "max_interval_width": 0.14, "max_disagreement": 0.35},
  "checks": [
    {"code": "data_fresh", "label": "Veri güncel ve yeterli", "passed": true, "detail": "Son sonuç 20 saat önce işlendi; her takım için ≥ 8 maç.", "value": 12, "threshold": 6}
  ],
  "audit": {"status": "passed", "checks": [{"code": "odds_timing", "label": "Oran zamanı seçimden önce", "passed": true, "detail": "…"}]}
}
```

## GET /api/v1/recommendations/{id}/rejection-reasons

"Neden elendi?" alanı (tek aday).

```json
{
  "id": 9813,
  "mode": "balanced",
  "selected": false,
  "fixture": {"id": 5521, "home": "Kartal SK", "away": "Liman Gücü"},
  "market": {"id": "1X2", "name": "Maç Sonucu"},
  "outcome": {"id": "1", "name": "Ev Sahibi"},
  "failed_checks": [{"code": "ev_lower", "label": "Belirsizlik altında EV avantajı", "passed": false, "detail": "EV alt sınırı -0,021 ≤ 0,012", "value": -0.021, "threshold": 0.012}],
  "reason_text": "EV alt sınırı eşiğin altında."
}
```

## GET /api/v1/recommendations/rejections?mode=balanced&fixture_id=5521&limit=50

Elenen adayların listesi (sayfanın "Neden elendi?" bölümü).

```json
{
  "mode": "balanced",
  "run_id": 41,
  "total": 1036,
  "reason_counts": [{"code": "ev_lower", "label": "Belirsizlik altında EV avantajı", "count": 702}],
  "items": [
    {"id": 9813, "fixture": {"id": 5521, "home": "Kartal SK", "away": "Liman Gücü", "kickoff_at": "…"},
     "market": {"id": "1X2", "name": "Maç Sonucu"}, "outcome": {"id": "1", "name": "Ev Sahibi"},
     "estimated_probability": 0.47, "odds": 2.05, "ev_lower": -0.021,
     "failed_checks": [{"code": "ev_lower", "label": "…", "detail": "…"}], "reason_text": "…"}
  ]
}
```

---

## Performans nesnesi (`Metrics`)

```json
{
  "n_candidates": 18233, "n_eligible": 410, "n_selections": 402, "n_settled": 380, "n_void": 2,
  "n_fixtures": 1530, "n_fixtures_without_selection": 1241, "selection_rate": 0.022,
  "hit_rate": {"value": 0.56, "ci_low": 0.51, "ci_high": 0.61},
  "brier": 0.238, "log_loss": 0.668,
  "ece": 0.021,
  "calibration_bins": [{"p_mean": 0.55, "observed": 0.57, "n": 120, "ci_low": 0.48, "ci_high": 0.66}],
  "avg_odds": 1.93, "avg_probability": 0.55, "avg_ev": 0.06,
  "roi": {"value": 0.031, "ci_low": -0.04, "ci_high": 0.10},
  "profit_units": 11.8
}
```

Değerler veri yoksa `null` olur. `roi` teorik birim bahis getirisidir (her seçime 1 birim).

## GET /api/v1/recommendations/modes/comparison?period=30d

`period`: `7d` | `30d` | `90d` | `all` | `season:2025` (varsayılan `all`).
Üç mod **aynı zaman aralığı ve aynı maç havuzu** üzerinde karşılaştırılır.

```json
{
  "period": "30d",
  "window": {"start": "…", "end": "…"},
  "pool": {"fixtures": 220, "candidates": 12000, "description": "Aynı 220 maç ve aynı aday havuzu üç mod için ortak."},
  "baseline": {"label": "Tüm adaylar (seçim yapılmadan, MODEL B)", "metrics": { /* Metrics */ }},
  "modes": [
    {"code": "balanced", "name": "Dengeli Seçimler", "metrics": { /* Metrics */ }},
    {"code": "highest_probability", "name": "En Güvenilir Tahminler", "metrics": { }},
    {"code": "best_value", "name": "En Avantajlı Oranlar", "metrics": { }}
  ],
  "notes": ["Seçim kapsamı modlar arasında farklıdır; isabet oranları farklı sayıda seçim üzerinden hesaplanmıştır."]
}
```

## GET /api/v1/recommendations/modes/performance?mode=balanced&period=all

Bir modun ayrıntılı performansı.

```json
{
  "mode": "balanced",
  "period": "all",
  "overall": { /* Metrics */ },
  "windows": {"7d": { }, "30d": { }, "90d": { }},
  "by_league": [{"league": {"id": "tr-super-lig", "name": "Süper Lig"}, "metrics": { }}],
  "by_market": [{"family": "totals", "name": "Alt/Üst", "metrics": { }}],
  "by_season": [{"season": "2025", "metrics": { }}],
  "season_stability": {"seasons": 2, "roi_std": 0.03, "hit_rate_std": 0.02, "consistent": true},
  "timeline": [{"date": "2026-11-01", "cumulative_profit": 3.2, "n": 45}]
}
```

## GET /api/v1/performance/selection-modes

Tahmin Başarı Merkezi › "Seçim Modları" sekmesi.

```json
{
  "generated_at": "…",
  "is_synthetic_data": true,
  "modes": [
    {
      "code": "balanced", "name": "Dengeli Seçimler", "short_name": "Dengeli",
      "overall": { /* Metrics */ },
      "windows": {"7d": { }, "30d": { }, "90d": { }},
      "by_season": [{"season": "2025", "metrics": { }}],
      "season_stability": {"seasons": 2, "roi_std": 0.03, "hit_rate_std": 0.02, "consistent": true},
      "timeline": [{"date": "2026-11-01", "cumulative_profit": 3.2, "n": 45}]
    }
  ],
  "baseline": {"label": "Tüm adaylar (MODEL B, seçim yok)", "metrics": { }},
  "notes": ["Modların başarısı birbirinden bağımsız kaydedilir; geçmiş seçimler değiştirilemez."]
}
```

---

## Ayarlar

`GET /api/v1/settings` → `{"user_id": "local", "default_mode": "balanced"}`

`PUT /api/v1/settings` gövde `{"default_mode": "best_value"}` → güncel ayarlar. Geçersiz mod `422`.

---

## Canlı Gol Radar

Radar seçim modlarından bağımsızdır; mod değişimi radar eşiklerini etkilemez.

`GET /api/v1/radar/config`

```json
{
  "target": "Kalan normal sürede en az bir gol",
  "model_a_min_probability": 0.90,
  "model_b_min_probability": 0.90,
  "min_remaining_minutes": 15,
  "live_data_max_age_seconds": 60,
  "live_odds_max_age_seconds": 60,
  "requires_rare_event_calibration": true,
  "requires_quality_gate": true,
  "requires_notification_permission": true,
  "duplicate_alarm_block": true,
  "note": "Eşik kullanıcı talebiyle %99'dan %90'a indirildi (v6.3). Seçim modları bu eşiği değiştiremez."
}
```

`GET /api/v1/radar/live` → canlı maçların radar değerlendirmesi

```json
{
  "evaluated_at": "…",
  "items": [
    {"fixture": {"id": 1, "home": "…", "away": "…", "league": {"id": "…", "name": "…"}},
     "minute": 31, "score": "1-0", "remaining_minutes": 59,
     "model_a_probability": 0.93, "model_b_probability": 0.92,
     "odds": {"decimal": 1.08, "source_label": "…", "age_seconds": 20},
     "conditions": [{"code": "model_a_threshold", "label": "MODEL A > %90", "passed": true, "detail": "…"}],
     "alarm": true, "alert_id": 17, "blocked_reason": null}
  ]
}
```

`GET /api/v1/radar/alerts?limit=50` → `{"items": [{"id": 17, "fixture_id": 1, "minute": 31, "model_a_probability": 0.93, "model_b_probability": 0.92, "created_at": "…", "notified": true}]}`

`POST /api/v1/radar/notifications/permission` gövde `{"granted": true}` → `{"user_id": "local", "granted": true}`

---

## Diğer

- `GET /api/v1/leagues` → `{"items": [{"id": "tr-super-lig", "name": "Süper Lig", "country": "Türkiye", "type": "league", "active": true}]}` (21 organizasyon)
- `GET /api/v1/system/status` → `{"version": "6.3.0", "prediction_models": ["MODEL_A", "MODEL_B"], "selection_modes": 3, "default_mode": "balanced", "is_synthetic_data": true, "last_run": {"id": 41, "finished_at": "…"}}`
- Web arayüzü `GET /` (statik dosyalar `/static/...`).
