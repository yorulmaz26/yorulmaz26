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

"Güvenilir avantaj" kuralı: oranlı modlar (En Avantajlı, Dengeli) doğrulama penceresinin ayar diliminde
teorik birim ROI'nin tek yönlü %95 alt sınırı > 0 olmadıkça ve bağımsız teyit diliminde getiri pozitif
kalmadıkça kurulmaz. Dengeli ek olarak yalnızca p > 0,5 alanında çalışır ve isabet alt sınırının pozitif EV
havuzunun isabet oranından düşük olmamasını ister; çalışma anında ihtiyatlı Kelly oranıyla sıralar. Kelly log
büyümesi raporlanır ama kurulma koşulu değildir. Bütün güven aralıkları ve en az örneklem koşulları maç
düzeyinde kümelenir (bir maçın ~92 seçeneği bağımsız gözlem sayılmaz). Bu bilinçli olarak ihtiyatlıdır;
verimli piyasalarda çoğu zaman seçim yapılmaması beklenir.

Yaklaşık zamanlı geçmiş CSV oranları yalnızca geriye dönük testte, yaş sınırı uygulanmadan maç öncesi fiyat
olarak kabul edilir; güncel öneride reddedilir. En Güvenilir modunda oran zorunlu değildir: doğrulamadan
geçmeyen bir oran kartta "gerçek oran" olarak gösterilmez ve kararla birlikte kaydedilmez (bu kararlar
isabet oranına katılır, ROI'ye katılmaz).

## 4.1 Birleştirici ve tutarlılık

Yığınlama birleştiricisi yalnızca maç düzeyinde kümelenmiş eşleştirilmiş log loss testinde her iki modeli de
geçerse etkinleşir; çıktısı market kurallarıyla tutarlı hale getirilir. Farklı marketlerdeki mantıksal olarak
aynı olaylar (ÇŞ 1X = Handikap(+1) 1, ÇŞ X2 = Handikap(−1) 2, Alt 0,5 = Skor 0-0, Alt 1,5 = Toplam Gol 0-1,
Üst 5,5 = Toplam Gol 6+) tek bir kanonik olasılık taşır; olasılık tutarlılığı kontrolü bunu da sınar.

## 5. MODEL A/B birleşimi

Otomatik ortalama alınmaz. Doğrulanmış bir yığınlama birleştiricisi (kronolojik doğrulamada her iki modeli
de anlamlı biçimde geçen) yoksa **ihtiyatlı en küçük** kuralı kullanılır: her seçenek için iki kalibre
olasılığın küçüğü. Bu, iki modelin de desteklemesini şart koşan bir karar kuralıdır; ortalama değildir.
İki model aynı veriden etkilenebildiği için bağımsız doğrulama sayılmaz (denetim notunda belirtilir).

## 6. Kullanıcı kararları (2026-11-22)

1. **v6.2 metni yok.** Yeniden kurulan 66 aşamalı plan (`GELISTIRME_PLANI.md`) ve 21 organizasyon listesi
   bundan sonra esas alınır; uzlaştırma tablosu (Ek A) kullanılmayacaktır.
2. **Canlı Gol Radar eşiği %90 onaylandı** (MODEL A > %90, MODEL B > %90; diğer şartlar aynen).
3. **Oran kaynağı: İddaa.** Açık araştırma sonucu: İddaa / Spor Toto'nun herkese açık, belgelenmiş bir
   geliştirici veri servisi bulunamadı. Seçenekler:
   - İddaa'dan (Spor Toto Teşkilatı / iddaa.com) resmi veri ortaklığı veya lisanslı veri talebi;
   - İddaa oranlarını sunduğunu belirten lisanslı bir oran veri sağlayıcısına abonelik (kapsam ve koşullar
     sağlayıcının sitesinden doğrulanmalıdır; bu ortamın ağ politikası nedeniyle doğrulanamadı);
   - iddaa.com sayfalarının veya belgelenmemiş uç noktalarının kazınması **önerilmez**: kullanım koşullarını
     ihlal edebilir ve haber verilmeden bozulabilir.
   Sistemde `iddaa` kaynağı yalnızca resmi/lisanslı bağlantı kurulup doğrulandığında `verified = 1` olur;
   o zamana kadar hiçbir oran İddaa olarak gösterilmez.
4. Fikstür, sonuç ve canlı maç verisi (dakika, skor, xG) için oran kaynağından ayrı bir futbol veri sağlayıcısı
   gerekebilir.

## 7. Ücretsiz kaynak kararı (kullanıcı: "ücretsiz olanları kullan")

- **football-data.co.uk — kullanılıyor.** Herkesin indirebileceği ücretsiz CSV dosyaları: 21 organizasyonun
  16'sı için geçmiş ve güncel sezon sonuçları, ilk yarı skorları ve yaklaşan maçlar; maç öncesi oranlar
  (Bet365 / piyasa ortalaması / Pinnacle). Kurulum: `python -m akilli_tahmin real-data --db gercek.sqlite3`,
  güncelleme: `update-data` (sunucu da 6 saatte bir kendisi günceller). Sınırlar:
  - İddaa oranı değildir; kartta kaynak "football-data.co.uk (Bet365 / piyasa ortalaması)" olarak görünür.
  - Oranların yakalanma anı bilinmez (`approximate`): geriye dönük testte kullanılır, güncel öneride reddedilir.
    Bu nedenle gerçek veride En Avantajlı ve Dengeli güncel seçim üretmez; En Güvenilir oran gerektirmez.
  - Yalnızca Maç Sonucu ve Alt/Üst 2,5 için oran vardır; canlı maç verisi yoktur (radar gerçek veride kapalı kalır).
  - TFF 1. Lig, Türkiye Kupası, UEFA Şampiyonlar/Avrupa/Konferans Ligi bu kaynakta yoktur.
  - Dosyalar genellikle haftada iki kez güncellenir; sonuçlar birkaç gün gecikebilir.
- **Nesine bülten adresi — kullanılmıyor.** Belgelenmemiş, herkese açık olmayan bir adres; bu çalışma ortamının
  güvenlik denetimi isteği "yetkisiz erişim" olarak engelledi. Atlatılmaya çalışılmadı; ağ izni geri alındı.
- **Maçkolik — kullanılmıyor.** Kullanım koşulları veritabanına içeriği görüntüleme dışında erişimi açıkça
  yasaklar; resmi API'si yoktur; araştırmalara göre yalnızca Türkiye IP'lerine yanıt verir.
- **Gerçek piyasa sonucu (Süper Lig, 2024/25–2026/27, 747 maç):** En Güvenilir geriye dönük testte kalibre
  (419 seçim, isabet %91,9 / tahmin %89,8). Piyasa oranları modellerden daha isabetli (log loss: piyasa 0,551,
  MODEL B 0,554, MODEL A 0,572); En Avantajlı ve Dengeli hiç kurulmadı — güvenilir avantaj yok.
- **İleride:** canlı maç verisi için ücretsiz katmanı olan resmi bir servis (ör. API-Football, günde 100 istek;
  kullanıcının kendi ücretsiz anahtarı gerekir); İddaa oranları için izinli/lisanslı erişim.
5. İstatistiksel politika değerleri (güven düzeyleri, en az örneklemler) varsayılan haliyle kullanılır.
