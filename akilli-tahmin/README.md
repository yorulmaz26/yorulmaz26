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
.venv/bin/python -m akilli_tahmin demo --db demo.sqlite3     # ~3-4 dk, ~300 MB: 4 sentetik lig + kronolojik geriye dönük test
.venv/bin/python -m akilli_tahmin serve --db demo.sqlite3    # http://127.0.0.1:8000
.venv/bin/python -m pytest -q                                # testler
```

### Ücretsiz gerçek veri

```bash
.venv/bin/python -m akilli_tahmin real-data --db gercek.sqlite3      # varsayılan: T1,E0,SP1,I1,D1,F1; 3 sezon
.venv/bin/python -m akilli_tahmin real-data --db tr.sqlite3 --leagues T1   # yalnızca Süper Lig (~2 dk)
.venv/bin/python -m akilli_tahmin update-data --db gercek.sqlite3    # yeni sonuçlar ve yaklaşan maçlar (kurulumdaki ligler)
.venv/bin/python -m akilli_tahmin serve --db gercek.sqlite3          # veri 6 saatten eskiyse arka planda günceller
```

Kaynak: [football-data.co.uk](https://www.football-data.co.uk) ücretsiz dosyaları (21 organizasyonun 16'sı).
İddaa oranı ve canlı maç verisi içermez; oranların zamanı yaklaşık olduğu için güncel öneride yalnızca En
Güvenilir modu seçim yapabilir. Ayrıntılar ve Nesine/Maçkolik'in neden kullanılmadığı: `docs/VARSAYIMLAR.md` §7.

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
| Olasılık alanı | p > 0,5 (gerçekleşmesi daha olası) | — | p > 0,5 |
| Kurulma koşulu | Doğrulamada isabet alt sınırı en yüksek, aşırı güven yok, bağımsız teyit | ROI %95 alt sınırı > 0, teyit diliminde getiri > 0 | En Avantajlı ile aynı + isabet alt sınırı ≥ pozitif EV havuzu |

Eşik ayarında bağımsız birim **maçtır**: bir maçın seçenekleri aynı sonuca bağlı olduğu için örneklem
sayısı ve güven aralıkları maç düzeyinde kümelenmiş hatayla hesaplanır. Eşikler doğrulama penceresinin ilk
2/3'ünde seçilir, son 1/3'te (bağımsız teyit dilimi) yeniden sınanır.

Modlar aynı moddan bağımsız değerlendirmeyi okur; mod değişimi MODEL A/B olasılıklarını değiştirmez.
Uygun seçim yoksa Dengeli modda **"Güvenilir ve avantajlı seçim bulunamadı."** gösterilir.

## Oranı elle girme ("Oran Gir" sayfası)

İddaa'da (veya başka bir sitede) gördüğünüz oranı girersiniz; sistem kendi olasılığıyla karşılaştırır:
başa baş oranı (1 ÷ olasılık), beklenen değer (EV = olasılık × oran − 1), belirsizlik altındaki ihtiyatlı EV,
"Avantajlı / Belirsiz / Avantajsız" sonucu ve üç modun bu oranla seçim yapıp yapmayacağı. Oran
"Elle girilen oran (kullanıcı)" olarak kaydedilir; İddaa'dan otomatik doğrulanmaz, karar kaydı oluşturmaz,
MODEL A'yı etkilemez. API: `GET /api/v1/manual-odds/options`, `POST /api/v1/manual-odds`,
`GET /api/v1/manual-odds/history`. Bu bir öneri değildir; pozitif EV kazanç garantisi değildir.

## Canlı Gol Radar

Hedef: kalan normal sürede en az bir gol. Alarm şartları: MODEL A > %90, MODEL B > %90 (kullanıcı talebiyle
%99'dan indirildi), en az 15 dk normal süre, ≤ 60 sn canlı veri ve oran, yeterli nadir olay kalibrasyonu,
Kalite Kapısı, bildirim izni, mükerrer alarm engeli. Seçim modları bu şartları değiştiremez.
