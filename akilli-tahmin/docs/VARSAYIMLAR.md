# Varsayımlar ve açık noktalar (v6.3)

Bu belge, v6.3 talimatının uygulanırken verilmek zorunda kalınan kararları ve kullanıcıdan doğrulanması
gereken noktaları kaydeder.

## 1. v6.2 bulunamadı — yeniden kuruldu

MASTER PROMPT v6.2 metni ve v6.2'ye ait kod bu depoda (bütün dallar ve etiketler tarandı) bulunamadı.
`yorulmaz26/yorulmaz26` deposunda başka projeler (kuaför randevu uygulaması ve oyunlar) var; futbol tahmin
kodu yok. Bu nedenle sistem `akilli-tahmin/` klasöründe sıfırdan kuruldu. v6.3'ün v6.2'ye atıf yaptığı
unsurlar şöyle yeniden kuruldu:

| v6.2 unsuru (v6.3'teki atıf) | Bu uygulamadaki karşılığı | Doğrulanmalı mı? |
|---|---|---|
| 21 futbol organizasyonu | `leagues.py` — Süper Lig, TFF 1. Lig, Türkiye Kupası, 5 büyük lig, Championship, Eredivisie, Primeira Liga, Belçika, İskoçya, Yunanistan, Avusturya, İsviçre, Danimarka, Brezilya, UEFA ŞL/AL/KL | **Evet** — özgün liste farklıysa yalnızca bu dosya değişir |
| İki ana tahmin modeli | MODEL A (Dixon–Coles Poisson), MODEL B (hiyerarşik yığınlanmış lojistik) | Model ailesi özgün tasarımdan farklı olabilir |
| Veriyle desteklenen bütün marketler | 26 market / 92 seçenek (`domain/markets.py`) | Özgün market listesiyle karşılaştırılmalı |
| Geçmiş zaman veri deposu | `result_recorded_at`, `captured_at`, `data_revisions`, `FootballDataView` | — |
| Tahmin kalibrasyonu | Kronolojik Platt / beta kalibrasyonu + HL testi + yerel destek | — |
| Tahmin dayanıklılığı | 5 alternatif MODEL A kurulumu ile senaryo testi | — |
| Tahmin Kalite Kapısı | 10 moddan bağımsız + 2 oran kontrolü (`selection/checks.py`) | — |
| Bağımsız denetim | `selection/audit.py` — motor kodunu kullanmadan kayıtlardan yeniden doğrulama | — |
| Tahmin Başarı Merkezi | `performance/tracker.py` + arayüz | — |
| Canlı Gol Radar | `radar/live_goal_radar.py` | — |
| 65 aşamalı plan | `docs/GELISTIRME_PLANI.md` Aşama 1–65 (yeniden kurulum, uzlaştırma tablosu Ek A'da) | **Evet** |

## 2. Canlı Gol Radar eşiği: %99 → %90

v6.3 metninin 9. bölümü "MODEL A > %99, MODEL B > %99" der ve "Radar'ın eşikleri gevşetilmeyecektir"
ekler. Kullanıcı aynı mesajın sonunda açıkça **"Gol radar oranını yüzde 90 yap"** talimatını verdi.
Kullanıcının son ve açık talimatı esas alındı:

- `config.RadarPolicy.model_a_min_probability = 0.90`, `model_b_min_probability = 0.90` (kesin büyüktür).
- Diğer şartların hepsi korunur: ≥ 15 dk normal süre, güncel canlı veri (≤ 60 sn), güncel canlı oran
  (≤ 60 sn), nadir olay kalibrasyonu, Kalite Kapısı, bildirim izni, mükerrer alarm engeli.
- "Gevşetilmeyecek" kuralı **seçim modlarına karşı** korunur: hiçbir mod (Dengeli dahil) radar eşiğini
  değiştiremez; radar işlevleri mod parametresi almaz. Bölüm 10'daki test "%90 korunmalı" olarak uygulandı.

## 3. Gerçek veri yok — sentetik demo

Gerçek fikstür, sonuç ve oran akışı (ör. lisanslı bir sağlayıcı veya resmi İddaa verisi) bağlı değildir;
bunun için API anahtarı ve lisans gerekir. Sistem:

- `data/synthetic.py` ile **kurgusal takımlar** üzerinde sezonlar, sonuçlar ve oranlar üretir.
  Bütün kayıtlar `is_synthetic = 1` ve `synthetic-demo` kaynağıyla işaretlenir; arayüzde "Sentetik demo
  verisi" uyarısı gösterilir. Bu oranlar **hiçbir zaman İddaa olarak gösterilmez**; `iddaa` kaynağı resmi
  bağlantı kurulmadan doğrulanmış sayılmaz.
- İki piyasa kurgusu vardır:
  - `efficient` (verimli): bahisçi gerçek takım güçlerini bilir. Bu kurguda sistem En Avantajlı ve Dengeli
    modlarda **hiç seçim üretmez** ("Güvenilir ve avantajlı seçim bulunamadı.") — testle doğrulanır.
  - `biased` (demo varsayılanı): bahisçi favori–sürpriz eğilimiyle (olasılıkları 0,5'e sıkıştırarak) ve
    4 tur gecikmeli güçlerle fiyatlar. Bu, gerçek piyasalarda belgelenmiş bir verimsizliğin kurgusudur ve
    oranlı modların ancak kronolojik doğrulamada istatistiksel avantaj kanıtlandığında seçim ürettiğini
    göstermek içindir. **Buradaki getiriler gerçek dünyadaki kârlılık hakkında hiçbir şey söylemez.**
- `data/football_data_csv.py` ile football-data.co.uk biçimindeki gerçek geçmiş sonuç/oran dosyaları içe
  aktarılabilir. Bu oranların yakalanma anı bilinmediğinden `approximate` işaretlenir; yalnızca geçmiş
  testte kullanılır, güncel öneride reddedilir.

## 4. Eşiklerin belirlenmesi ("sabit eşik yok")

Olasılık, EV, belirsizlik, uyuşmazlık ve piyasa sapması eşikleri `selection/thresholds.py` ile kronolojik
doğrulama penceresinden (kesimden önceki 540 gün) seçilir; eşik adayları verinin yüzdeliklerinden üretilir.
Sabit kalan değerler seçim eşiği değil, **istatistiksel politika** değerleridir (`config.py`): güven
düzeyleri (%95 güven aralıkları, %70/%80/%90 tek yönlü alt sınır adayları), en az örneklem sayıları,
veri/oran güncellik süreleri. Bunlar gerekirse kullanıcıyla birlikte değiştirilebilir.

"Güvenilir avantaj" kuralı: oranlı modlar doğrulama penceresinde teorik birim ROI'nin tek yönlü %95 alt
sınırı > 0 olmadıkça kurulmaz (Dengeli için ek olarak Kelly log büyümesinin alt sınırı > 0). Bu bilinçli
olarak ihtiyatlıdır; verimli piyasalarda çoğu zaman seçim yapılmaması beklenir.

## 5. MODEL A/B birleşimi

Otomatik ortalama alınmaz. Doğrulanmış bir yığınlama birleştiricisi (kronolojik doğrulamada her iki modeli
de anlamlı biçimde geçen) yoksa **ihtiyatlı en küçük** kuralı kullanılır: her seçenek için iki kalibre
olasılığın küçüğü. Bu, iki modelin de desteklemesini şart koşan bir karar kuralıdır; ortalama değildir.
İki model aynı veriden etkilenebildiği için bağımsız doğrulama sayılmaz (denetim notunda belirtilir).

## 6. Kullanıcıya sorulacaklar

1. Özgün v6.2 metni ve 65 aşamalı liste paylaşılırsa plan ve organizasyon listesi uzlaştırılacak.
2. Gerçek veri sağlayıcısı (fikstür, sonuç, canlı veri, oran) ve İddaa verisine erişim yöntemi.
3. Radar eşiğinin %90 olması (kullanıcı talebi) korunacak mı; "gevşetilmez" kuralı yalnızca modlara karşı mı?
4. İstatistiksel politika değerleri (güven düzeyleri, en az örneklemler) için tercih.
