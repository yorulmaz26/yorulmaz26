# Akıllı Tahmin v6.3

Futbol maçları için olasılık tahmini ve seçim sistemi. **İki ana tahmin modeli** (MODEL A, MODEL B),
**üç seçim modu** (En Güvenilir, En Avantajlı, **Dengeli** — varsayılan), Tahmin Kalite Kapısı, bağımsız
denetim, Tahmin Başarı Merkezi ve Canlı Gol Radar (MODEL A ve MODEL B > **%90**).

> Tahminler olasılık tahminidir. Yüksek isabet oranı veya pozitif beklenen değer **kazanç garantisi
> değildir.** Güvenilir seçim bulunmayan maçlarda sistem zorla tahmin üretmez. 18 yaş altı kullanamaz.

Belgeler: [`docs/MASTER_PROMPT_v6.3.md`](docs/MASTER_PROMPT_v6.3.md) (talimat) ·
[`docs/MIMARI.md`](docs/MIMARI.md) · [`docs/API.md`](docs/API.md) ·
[`docs/GELISTIRME_PLANI.md`](docs/GELISTIRME_PLANI.md) (66 aşama) ·
[`docs/VARSAYIMLAR.md`](docs/VARSAYIMLAR.md) (v6.2 bulunamadı, radar %90, sentetik veri).

## Hızlı başlangıç

```bash
cd akilli-tahmin
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python -m akilli_tahmin demo --db demo.sqlite3     # ~2 dk: sentetik veri + kronolojik geriye dönük test
.venv/bin/python -m akilli_tahmin serve --db demo.sqlite3    # http://127.0.0.1:8000
.venv/bin/python -m pytest -q                                # testler
```

Demo **kurgusal takımlar ve sentetik oranlar** kullanır (`--market biased` varsayılan: favori–sürpriz
eğilimli piyasa; `--market efficient`: verimli piyasa, oranlı modlar seçim üretmez). Gerçek veri için:
`import-csv` (football-data.co.uk biçimi, yalnızca geçmiş test) ve bir veri sağlayıcı bağlantısı gerekir.

## Ne nerede

| Katman | Dosya |
|---|---|
| 21 organizasyon | `akilli_tahmin/leagues.py` |
| Marketler ve sonuçlandırma kuralları | `akilli_tahmin/domain/markets.py` |
| Veritabanı (selection_mode_* tabloları, değişmezlik tetikleyicileri) | `akilli_tahmin/db/schema.sql` |
| Geçmiş zaman veri erişimi | `akilli_tahmin/data/point_in_time.py` |
| MODEL A (yalnızca futbol verisi) | `akilli_tahmin/models/model_a.py`, `models/isolation.py` |
| MODEL B (OOF MODEL A + oran) | `akilli_tahmin/models/model_b.py` |
| Birleştirici (ortalama yok) | `akilli_tahmin/models/combiner.py` |
| Kalibrasyon, metrikler | `akilli_tahmin/calibration/` |
| Oran doğrulama, marj, EV | `akilli_tahmin/odds/odds.py` |
| 15 adımlı motor, Kalite Kapısı | `akilli_tahmin/selection/engine.py`, `selection/checks.py` |
| Üç seçim modu, kronolojik eşikler | `akilli_tahmin/selection/modes.py`, `selection/thresholds.py` |
| Bağımsız denetim | `akilli_tahmin/selection/audit.py` |
| Başarı Merkezi | `akilli_tahmin/performance/tracker.py` |
| Canlı Gol Radar | `akilli_tahmin/radar/live_goal_radar.py` |
| API ve arayüz | `akilli_tahmin/api/`, `akilli_tahmin/web/static/` |
| Geriye dönük test | `akilli_tahmin/backtest/walk_forward.py` |

## Seçim modları

| | En Güvenilir | En Avantajlı | Dengeli (varsayılan) |
|---|---|---|---|
| Oran gerekli | Hayır | Evet (doğrulanmış, güncel) | Evet |
| Ana ölçüt | Kalibre olasılığın alt sınırı | EV alt sınırı (EV = p × oran − 1) | Olasılık alt sınırı **ve** EV alt sınırı |
| Sıralama | Olasılık alt sınırı (ham olasılık değil) | EV alt sınırı | İhtiyatlı Kelly oranı |
| Eşikler | Kronolojik doğrulama | Kronolojik doğrulama | Kronolojik doğrulama |
| Kurulma koşulu | Doğrulamada isabet alt sınırı en yüksek, aşırı güven yok | ROI %95 alt sınırı > 0 | ROI ve Kelly büyümesi %95 alt sınırı > 0 |

Modlar aynı moddan bağımsız değerlendirmeyi okur; mod değişimi MODEL A/B olasılıklarını değiştirmez.
Uygun seçim yoksa Dengeli modda **"Güvenilir ve avantajlı seçim bulunamadı."** gösterilir.

## Canlı Gol Radar

Hedef: kalan normal sürede en az bir gol. Alarm şartları: MODEL A > %90, MODEL B > %90 (kullanıcı talebiyle
%99'dan indirildi), en az 15 dk normal süre, ≤ 60 sn canlı veri ve oran, yeterli nadir olay kalibrasyonu,
Kalite Kapısı, bildirim izni, mükerrer alarm engeli. Seçim modları bu şartları değiştiremez.
