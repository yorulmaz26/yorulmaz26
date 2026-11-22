# Mimari (v6.3)

```
                    ┌───────────────── geçmiş zaman veri deposu (SQLite) ─────────────────┐
 fikstür / sonuç ──►│ fixtures(result_recorded_at)  odds_snapshots(captured_at, kaynak)    │
 oran akışı ───────►│ goal_events, live_states       market_settlement_rules               │
                    └──────────────┬─────────────────────────────┬────────────────────────┘
                                   │ yalnızca futbol verisi      │ kesimden önceki oranlar
                                   ▼                             ▼
                       MODEL A (Dixon–Coles)  ──OOF p_A──►  MODEL B (hiyerarşik lojistik)
                                   │                             │
                                   └──── kalibrasyon (kronolojik, aile bazlı) ────┐
                                                                                  ▼
            belirsizlik (Laplace örnekleri) · dayanıklılık (5 senaryo) · tutarlılık · bağımlılık
                                   │
                                   ▼
                    candidate_evaluations  (moddan bağımsız, değişmez)
                                   │
            ┌──────────────────────┼───────────────────────┐
            ▼                      ▼                       ▼
     En Güvenilir           En Avantajlı             Dengeli (varsayılan)
   (p alt sınırı)        (EV alt sınırı)        (p + EV, Kelly sıralaması)
            └──────────────────────┼───────────────────────┘
                                   ▼
                     bağımsız denetim → kararlar / retler
                                   ▼
                 Başarı Merkezi (mod bazlı, aynı havuz) · API · arayüz

  Canlı Gol Radar: MODEL A canlı + MODEL B canlı > %90, ≥ 15 dk, güncel veri/oran, nadir olay
  kalibrasyonu, Kalite Kapısı, bildirim izni, mükerrer alarm engeli — seçim modlarından bağımsız.
```

## 15 adım → kod

| Adım | Kod |
|---|---|
| 1 Fikstür ve veri durumu | `Engine._evaluate_fixture` — `fixture_ok`, `data_fresh`, `data_sufficient` |
| 2 Mevcut marketler | `domain/markets.py` (26 market, 92 seçenek), `data/point_in_time.latest_quotes` |
| 3 MODEL A | `models/model_a.py` — `fit_model_a`, `predict`, `predict_draws` |
| 4 MODEL B | `models/model_b.py` — `train_model_b`, `ModelB.predict` |
| 5 Olasılık tutarlılığı | `models/model_b.consistency_report`, `enforce_consistency` |
| 6 Kalibrasyon ve belirsizlik | `calibration/calibrate.py`, Laplace örnekleri → yüzdelikler |
| 7 Dayanıklılık | `ROBUSTNESS_SCENARIOS` (hızlı/yavaş zaman ağırlığı, DC yok, güçlü büzülme, son gün çıkarıldı) |
| 8 Tahmin geçerliliği | `prediction_valid` kontrolü; API'de geçerlilik (başlama saati, oran yaşı, yeni sonuç) |
| 9 Market bağımlılığı | MODEL A ortak dağılımından iç içe olay tespiti (kesin) ve φ korelasyonu |
| 10 Oran ve EV | `odds/odds.py` — doğrulama, marj, güç yöntemiyle marjdan arındırma, EV, Kelly |
| 11 Kalite Kapısı | `selection/checks.CORE_CHECKS` + `ODDS_CHECKS` |
| 12 Üç mod | `selection/modes.evaluate_mode`, eşikler `selection/thresholds.tune_mode` |
| 13 Bağımsız denetim | `selection/audit.audit_candidate` → `selection_audits` |
| 14 Kayıt | `selection_mode_candidates`, `selection_mode_decisions`, `selection_mode_rejections` |
| 15 Sıralama ve gösterim | `api/service.recommendations` (kullanıcının modu) |

## Değişmezlik

Tahminler, oranlar, değerlendirmeler, adaylar, retler, denetimler ve eşik yapılandırmaları SQLite
tetikleyicileriyle yalnızca eklenebilir. Kararlarda yalnızca sonuçlandırma alanları, yalnızca bir kez
(`pending` → sonuç) güncellenebilir; mod, olasılık, oran, gerekçe gibi alanlar değiştirilemez. Bir mod aynı
maç/market/seçenek için yalnızca bir karar kaydeder (performansta tekrar sayılmaz).

## Kronoloji

Her çalıştırmanın bir zaman kesimi vardır. MODEL A yalnızca o ana kadar sonucu kaydedilmiş maçları,
MODEL B/kalibrasyon/birleştirici/eşikler yalnızca o ana kadar sonucu bilinen geçmiş değerlendirmeleri,
oranlar yalnızca o ana kadar yakalanmış anlık görüntüleri kullanır. Geriye dönük test (`backtest/`) aynı
kodu her başlama saati diliminden 30 dk önceki kesimle çalıştırır.
