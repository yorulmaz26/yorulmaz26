# Akıllı Tahmin — API sözleşmesi (v6.3)

Tüm uçlar `/api/v1` altındadır, JSON döner, zamanlar ISO‑8601 UTC (`2026-11-21T18:00:00Z`) biçimindedir.
Olasılıklar 0–1 arası ondalık, beklenen değer (EV) birim bahis başına ondalık getiridir (`0.05` = %5).
Kullanıcı kimliği `X-User-Id` başlığıyla gelir; yoksa `local` kabul edilir.

Durum değiştiren istekler (`PUT /api/v1/settings`, `POST /api/v1/radar/notifications/permission`) gövdeyi JSON
olarak ve `Content-Type: application/json` başlığıyla göndermelidir; başka içerik türü (ya da içerik türü olmayan
istek) `415 Unsupported Media Type` ile reddedilir. Bu, başka bir siteden ön kontrolsüz (CORS-basit) gönderilen
isteklerin kullanıcı ayarlarını değiştirmesini engeller.

Başarı Merkezi uçları (`/recommendations/modes/comparison`, `/recommendations/modes/performance`,
`/performance/selection-modes`) bütün kayıt havuzunu tarar ve gerçek veride birkaç saniye sürebilir. Web arayüzü bu
uçlar için 45 sn zaman aşımı kullanır, aynı veri için paralel ikinci istek açmaz ve dönem değişince süren eski
isteği iptal eder (`AbortController`).

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
        "values": {"level": 0.8, "max_relative_width": 0.3, "max_disagreement": 0.35, "max_market_deviation": 0.4,
                   "min_probability_lower": 0.52, "min_ev_lower": 0.012},
        "source": "chronological_validation",
        "validation_window": {"start": "2025-08-16T13:00:00Z", "end": "2026-11-16T12:30:00Z"},
        "validation_metrics": {"n": 212, "hit_rate": 0.62, "hit_rate_lower": 0.55, "roi": 0.031, "roi_lower": 0.004,
                               "confirm_n": 71, "confirm_hit_rate": 0.61, "confirm_roi": 0.018},
        "reason": null,
        "config_id": 12
      }
    }
  ]
}
```

`thresholds.values` anahtarları (yalnızca o modda kullanılanlar döner):

| Anahtar | Anlam | En Güvenilir | En Avantajlı | Dengeli |
|---|---|---|---|---|
| `level` | Tek yönlü güven düzeyi (alt sınırlar bu düzeyde hesaplanır) | ✓ | ✓ | ✓ |
| `max_relative_width` | Azami göreli belirsizlik: (%80 aralık genişliği) / p | ✓ | ✓ | ✓ |
| `max_disagreement` | Azami MODEL A/B uyuşmazlığı (logit farkı) | ✓ | ✓ | ✓ |
| `max_market_deviation` | Marjdan arındırılmış piyasa olasılığından azami sapma (logit) | | ✓ | ✓ |
| `min_probability_lower` | Kalibre olasılığın ihtiyatlı alt sınırı için asgari değer | ✓ | | ✓ |
| `min_ev_lower` | Belirsizlik altında EV alt sınırı için asgari değer (≥ 0) | | ✓ | ✓ |

`thresholds.status` `not_established` olduğunda o mod seçim üretmez (`values: {}`); rastgele sabit eşik dayatılmaz.
İki farklı nedeni vardır ve `reason` gerçek nedeni Türkçe cümle olarak taşır:

- **Doğrulama yapılamadı** — yeterli kronolojik doğrulama verisi yok (`reason` ör. `"Kronolojik doğrulama verisi yok."`
  veya `"Doğrulama örneklemi yetersiz (0 < 150)."`); `validation_window` yoktur.
- **Doğrulama yapıldı ama güvenilir eşik/avantaj belirlenemedi** — `validation_window` ve `validation_metrics`
  (`n`, bağımsız teyit dilimi için `confirm_n`, `confirm_roi` …) doludur; `reason` ör.
  `"Bağımsız teyit diliminde teorik getiri pozitif değil; güvenilir avantaj belirlenemedi."` veya
  `"Doğrulama penceresinde koşulları sağlayan eşik kümesi bulunamadı."`.

Arayüz `reason`, doğrulama penceresi ve `n` değerini gösterir; "henüz doğrulama yapılmadı" ifadesini yalnızca
`validation_window` yokken kullanır.

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
  "decision": {
    "id": 377,
    "first_decision": true,
    "recorded_odds": 1.92,
    "recorded_probability": 0.562,
    "selection_timestamp": "2026-11-21T17:00:00Z",
    "note": null
  },
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
  "history": {"scope": "Dengeli · Alt/Üst · Süper Lig", "n": 64, "hit_rate": 0.563, "roi": 0.021},
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
- `odds` En Güvenilir modunda `null` olabilir: oran bu modda zorunlu değildir; market için oran yoksa **ya da oran
  doğrulamadan geçmediyse** (eski, kaynağı doğrulanmamış vb.) `odds: null` ve `expected_value: null` döner. Arayüz
  "Geçerli oran yok (bu modda zorunlu değil)" gösterir. Diğer iki modda geçerli oran zorunludur.
- `history.scope` "Mod kısa adı · market ailesinin Türkçe adı · lig adı" biçimindedir
  (ör. `"En Güvenilir · Alt/Üst · Bundesliga"`); aile kodu (`totals`) değil, Türkçe ad döner.
- `decision` bu seçimin değiştirilemez performans kaydıdır (`selection_mode_decisions`). Aynı (mod, maç, market,
  seçenek) daha önceki bir çalıştırmada zaten seçildiyse `first_decision: false` olur ve performans **ilk karardaki**
  `recorded_odds` / `recorded_probability` değerleriyle, `selection_timestamp` anına göre sayılır; kartta gösterilen
  güncel oran ve olasılık bundan farklı olabilir. Arayüz bu durumda "Performans ilk karardaki oran ve olasılıkla
  (… tarihinde) sayılır" notunu kayıtlı değerlerle gösterir. `recorded_odds` En Güvenilir'de `null` olabilir;
  `note` sunucunun isteğe bağlı açıklama metnidir (`first_decision: true` iken `null`). Kayıt yoksa `decision: null`.

---

## GET /api/v1/recommendations/{id}/decision-explanation

"Neden seçildi?" alanı.

```json
{
  "id": 9812,
  "mode": "balanced",
  "selected": true,
  "summary": "…",
  "decision_id": 377,
  "config_id": 12,
  "thresholds": {"level": 0.8, "max_relative_width": 0.3, "max_disagreement": 0.35, "max_market_deviation": 0.4,
                 "min_probability_lower": 0.52, "min_ev_lower": 0.012},
  "checks": [
    {"code": "data_fresh", "label": "Veri güncel", "passed": true, "detail": "Sağlandı.", "value": null, "threshold": null},
    {"code": "prob_lower", "label": "Kalibre olasılığın ihtiyatlı alt sınırı", "passed": true,
     "detail": "Olasılık alt sınırı 0,538 ≥ eşik 0,520", "value": 0.538, "threshold": 0.52},
    {"code": "audit", "label": "Bağımsız seçim denetimi", "passed": true, "detail": "Bağımsız denetimden geçti.", "value": null, "threshold": null}
  ],
  "conditions": [
    {"no": 1, "label": "Veri güncel ve yeterli", "codes": ["fixture_ok", "data_fresh", "data_sufficient"], "passed": true},
    {"no": 8, "label": "Gerçek ve güncel oran", "codes": ["odds_valid", "market_reference"], "passed": null}
  ],
  "audit": {"status": "passed", "checks": [{"code": "chronology", "label": "Zaman kesimi ve sıralama", "passed": true, "detail": "…"}]}
}
```

- `checks[].value` yalnızca ölçülen bir büyüklüktür (olasılık alt sınırı, göreli belirsizlik, uyuşmazlık …); ölçüm
  yoksa `null` olur. Kayıt kimlikleri (denetim kaydı, eşik yapılandırması) `value` alanında **dönmez**. Arayüz
  "Değer …" ve "Eşik …" parçalarını yalnızca ilgili alan `null` değilse gösterir.
- `conditions` yalnızca Dengeli modunda döner: modun 10 numaralı koşulu ve her birinin dayandığı kontrol kodları.
  `passed: null` "Değerlendirilmedi" demektir (ilgili kontrol bu aday için hesaplanmadı); arayüz bunu nötr gösterir,
  başarısız saymaz.

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
  "failed_checks": [{"code": "ev_lower", "label": "Belirsizlik altında EV avantajı", "passed": false, "detail": "EV alt sınırı −0,021 ≤ 0,012", "value": -0.021, "threshold": 0.012}],
  "reason_text": "Sağlanmayan koşul: Belirsizlik altında EV avantajı."
}
```

`failed_checks[].label` koşulun **sağlanması gereken** hâlini adlandırır ("MODEL A kalibrasyonu yeterli"); eleme
gerekçesi olarak tek başına okunmamalıdır. `reason_text` her zaman olumsuz bir cümledir: ya
`"Sağlanmayan koşul: <etiket>."` ya da başarısız kontrolün kendi açıklaması
(ör. `"Yerel gözlenen oran [0,944, 0,967] kalibre olasılıkla (0,993) uyumsuz"`).

## GET /api/v1/recommendations/rejections?mode=balanced&fixture_id=5521&limit=50&offset=0

Elenen adayların listesi (sayfanın "Neden elendi?" bölümü). Sorgu parametreleri: `mode`, isteğe bağlı `fixture_id`,
`limit` (1–500, varsayılan 50), `offset` (≥ 0, varsayılan 0). Sıralama sabittir; aynı sorgu `offset` artırılarak
sayfalanır.

```json
{
  "mode": "balanced",
  "run_id": 41,
  "total": 2760,
  "offset": 0,
  "limit": 50,
  "reason_counts": [{"code": "ev_lower", "label": "Belirsizlik altında EV avantajı", "count": 702}],
  "fixtures": [
    {"id": 5521, "home": "Kartal SK", "away": "Liman Gücü", "kickoff_at": "2026-11-21T18:00:00Z", "count": 92}
  ],
  "items": [
    {"id": 9813, "fixture": {"id": 5521, "home": "Kartal SK", "away": "Liman Gücü", "kickoff_at": "…"},
     "market": {"id": "1X2", "name": "Maç Sonucu"}, "outcome": {"id": "1", "name": "Ev Sahibi"},
     "estimated_probability": 0.47, "odds": 2.05, "ev_lower": -0.021,
     "failed_checks": [{"code": "ev_lower", "label": "Belirsizlik altında EV avantajı", "detail": "EV alt sınırı −0,021 ≤ 0,012"}],
     "reason_text": "Sağlanmayan koşul: Belirsizlik altında EV avantajı."}
  ]
}
```

- `total` ve `reason_counts` filtreye (`fixture_id`) uyan **bütün** elenen adaylar içindir; `limit`/`offset`'ten bağımsızdır.
- `fixtures` çalıştırmada bu mod için elenen adayı olan **bütün** maçları ve her birinin elenen aday sayısını
  (`count`) listeler; `limit`/`offset`'ten bağımsızdır. Arayüzün maç filtresi bu listeden kurulur; maç seçilince
  liste `offset=0` ve `fixture_id` ile baştan yüklenir, "Daha fazla göster" ise `offset`i 50'şer artırır.
- `reason_text` her zaman olumsuz bir cümledir (bkz. `rejection-reasons`).

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
    {"code": "balanced", "name": "Dengeli Seçimler", "metrics": { /* Metrics */ },
     "timeline": [{"date": "2026-10-23", "cumulative_profit": 0.9, "n": 1}, {"date": "2026-11-20", "cumulative_profit": 1.1, "n": 18}]},
    {"code": "highest_probability", "name": "En Güvenilir Tahminler", "metrics": { }, "timeline": [ ]},
    {"code": "best_value", "name": "En Avantajlı Oranlar", "metrics": { }, "timeline": [ ]}
  ],
  "notes": ["Seçim kapsamı modlar arasında farklıdır; isabet oranları farklı sayıda seçim üzerinden hesaplanmıştır."]
}
```

`modes[].timeline`, o satırın `metrics` değerleriyle **aynı kararlardan** (aynı zaman aralığı ve aynı maç havuzu)
sonuçlanma gününe göre birikimli kâr serisidir; pencere başında 0'dan başlar ve son noktanın `cumulative_profit`
değeri `metrics.profit_units` ile aynıdır. `baseline` için zaman serisi yoktur. Başarı Merkezi'ndeki kümülatif kâr
grafiği her dönem (`all` dahil) bu seriyi çizer; böylece grafik lejantı tablodaki "Kâr (birim)" ile aynıdır. Alan
yoksa (eski sunucu) arayüz `/performance/selection-modes` serisini dönem başına göre yeniden tabanlar ve farkı belirtir.

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

`PUT /api/v1/settings` gövde `{"default_mode": "best_value"}` (`Content-Type: application/json`) → güncel ayarlar.
Geçersiz mod `422`, JSON olmayan içerik türü `415`.

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

`conditions[].label` sağlanması gereken koşulu adlandırır. Alarm üretilmediğinde `blocked_reason` olumsuz bir
cümledir: `"Sağlanmayan koşul: MODEL A > %90."` (koşulun ayrıntısı eklenebilir).

`GET /api/v1/radar/alerts?limit=50` → `{"items": [{"id": 17, "fixture_id": 1, "minute": 31, "model_a_probability": 0.93, "model_b_probability": 0.92, "created_at": "…", "notified": true}]}`

`POST /api/v1/radar/notifications/permission` gövde `{"granted": true}` (`Content-Type: application/json`; aksi hâlde
`415`) → `{"user_id": "local", "granted": true}`

---

## Diğer

- `GET /api/v1/leagues` → `{"items": [{"id": "tr-super-lig", "name": "Süper Lig", "country": "Türkiye", "type": "league", "active": true}]}` (21 organizasyon)
- `GET /api/v1/system/status` → `{"version": "6.3.0", "prediction_models": ["MODEL_A", "MODEL_B"], "selection_modes": 3, "default_mode": "balanced", "is_synthetic_data": true, "last_run": {"id": 41, "finished_at": "…"}}`
- Web arayüzü `GET /` (statik dosyalar `/static/...`).
