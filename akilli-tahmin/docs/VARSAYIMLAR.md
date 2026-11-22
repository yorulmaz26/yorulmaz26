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
  16'sı için geçmiş ve güncel sezon sonuçları ve yaklaşan maçlar; maç öncesi oranlar (Bet365 / piyasa
  ortalaması / Pinnacle). İlk yarı skorları 12 ana ligde vardır; Avusturya, İsviçre, Danimarka ve Brezilya
  dosyalarında yoktur, bu liglerde ilk yarı marketleri Kalite Kapısı'nda reddedilir. Kurulum:
  `python -m akilli_tahmin real-data --db gercek.sqlite3`, güncelleme: `update-data` (kurulumdaki liglerle; yeni
  lig yalnızca `--allow-new` ile). Sunucu, son çalıştırma 6 saatten eskiyse bir sonraki istekte arka planda
  günceller (istekleri bekletmez; hata olursa bir saat sonra yeniden dener). Ertelenen maçlar 3 gün sonra
  "ertelendi" sayılır ve kararları iade edilir; tarihi değişen maç aynı kayıtta taşınır. Sınırlar:
  - İddaa oranı değildir; kartta kaynak "football-data.co.uk (Bet365 / piyasa ortalaması)" olarak görünür.
  - Oranların yakalanma anı bilinmez (`approximate`): geriye dönük testte kullanılır, güncel öneride reddedilir.
    Bu nedenle gerçek veride En Avantajlı ve Dengeli güncel seçim üretmez; En Güvenilir oran gerektirmez.
  - Yalnızca Maç Sonucu ve Alt/Üst 2,5 için oran vardır; canlı maç verisi yoktur (radar gerçek veride kapalı kalır).
  - TFF 1. Lig, Türkiye Kupası, UEFA Şampiyonlar/Avrupa/Konferans Ligi bu kaynakta yoktur.
  - Dosyalar genellikle haftada iki kez güncellenir; sonuçlar birkaç gün gecikebilir.
- **Türkiye'de erişim sorunu ve GitHub yansısı (2026-11-22):** kullanıcının bilgisayarında football-data.co.uk
  "self-signed certificate" ve "erişilemeyen ağ" hatası verdi (internet sağlayıcısı düzeyinde engel olduğu
  tahmin ediliyor; doğrulanmadı). API-Football ücretsiz planı güncel sezonun maç programını vermediği için yerine
  geçemez ("Free plans do not have access to this season, try from 2022 to 2024"). Kullanıcı kararıyla dosyalar
  **değiştirilmeden** `veri/football-data/` klasörüne kopyalanır (12 ana lig × 3 sezon, `fixtures.csv`, 4 ek
  lig) ve her gün otomatik görevle yenilenir (`scripts/mirror_football_data.py`). Program önce asıl siteyi
  dener; olmazsa GitHub yansısını (`raw.githubusercontent.com`), o da olmazsa indirilen projedeki yerel kopyayı
  kullanır. Depo herkese açık olduğu için bu kopyalar da herkese açıktır; hakları kaynağa aittir.
- **Nesine bülten adresi — kullanılmıyor.** Belgelenmemiş, herkese açık olmayan bir adres; bu çalışma ortamının
  güvenlik denetimi isteği "yetkisiz erişim" olarak engelledi. Atlatılmaya çalışılmadı; ağ izni geri alındı.
- **Maçkolik — kullanılmıyor.** Kullanım koşulları veritabanına içeriği görüntüleme dışında erişimi açıkça
  yasaklar; resmi API'si yoktur; araştırmalara göre yalnızca Türkiye IP'lerine yanıt verir.
- **Gerçek piyasa sonucu (Süper Lig, 2024/25–2026/27, 747 maç):** En Güvenilir geriye dönük testte kalibre
  (419 seçim, isabet %91,9 / tahmin %89,8). Piyasa oranları modellerden daha isabetli (log loss: piyasa 0,551,
  MODEL B 0,554, MODEL A 0,572); En Avantajlı ve Dengeli hiç kurulmadı — güvenilir avantaj yok.
- **İleride:** İddaa oranları için izinli/lisanslı erişim. (Canlı veri için API-Football bağlandı: §8.)
5. İstatistiksel politika değerleri (güven düzeyleri, en az örneklemler) varsayılan haliyle kullanılır.

## 8. Son 15 Dakika Radarı — API-Football (kullanıcı talebi, 2026-11-22)

Talep: "ilk yarı son 15 dk ve ikinci yarı son 15 dakika için gol analizi yap canlı radar oluştur. Günlük 100
istek ile sınırlı olduğu için gol olma ihtimali yüzde 90 ve üstü ise istek gönder bildirim gönder."

- **Anahtar:** kullanıcının API-Football anahtarı koda, veritabanına, kayıtlara veya git geçmişine yazılmaz.
  Bulut oturumunda ağ vekil sunucusu ekler; kendi bilgisayarınızda `API_FOOTBALL_KEY` ortam değişkeniyle
  verilir. Anahtar sohbette paylaşıldığı için yenilenmesi önerildi.
- **Ücretsiz plan doğrulandı (2026-11-22):** `/fixtures?live` ve `/odds/live` erişilebilir; 21 organizasyonun
  lig kimlikleri `/leagues` ile doğrulandı (`APIF_LEAGUE_IDS`). Sayaç 00:00 UTC'de (03:00 TSİ) sıfırlanır.
- **Eşik "≥ %90":** kullanıcı "yüzde 90 ve üstü" dediği için bu radar büyük-eşit kullanır (Canlı Gol Radar
  > %90 kullanır). Eşik Canlı Gol Radar sayfasından %50–%90 arasında (API ile %30–%99) değiştirilebilir; seçim modları
  değiştiremez.
- **Gerçekçi beklenti:** 15 dakikalık pencerede gol olasılığı tipik maçta %35–%55'tir; %90 için maçta yaklaşık
  10 gol beklentisi gerekir. Gerçek veride (6 lig, 4.412 maç değerlendirmesi) maç öncesi pencere olasılığı
  ortalama %39,8 (H1) / %46,0 (H2), en çok %61,6 / %67,3; hiçbir maç %90'a yaklaşmadı. Bu yüzden %90 eşiğinde radar **pratikte hiç istek göndermez ve
  bildirim üretmez**. 2026-11-22 planında 47 pencere için istek sayısı: %90 → 0, %80 → 0, %70 → 2, %60 → 12,
  %50 → 33.
- **Eşik %60'a indirildi (kullanıcı kararı, 2026-11-22):** %90'ın pratikte hiç istek ve bildirim üretmediği
  gösterildikten sonra kullanıcı "60 a indir" dedi; varsayılan eşik ≥ %60. 22–23 Kasım'daki 22 maçta 44
  bölümün 12'si canlı izlenir. Bildirim için MODEL B ≥ %60 ve iki modelin nadir olay kalibrasyonu yine
  gereklidir; radar yeterli geçmiş toplayana kadar (MODEL B için ≥ 300 oranlı örnek) bildirim üretmez.
- **Pencere payları (yaklaşık, literatür):** yarının gollerinin %42'si (H1) ve %41'i (H2) son 15 dakika +
  uzatmada; ortalama uzatma 2 (H1) ve 4 (H2) dakika. Tempo dağılımı κ = 10 (ihtiyatlı). Radar kendi
  değerlendirmelerini sonuçlandırdıkça (canlı yanıttaki devre/maç sonu skoru veya football-data.co.uk sonucu)
  bu değerler yeniden kestirilmelidir.
- **MODEL B ve nadir olay kalibrasyonu:** yalnızca bu radarın sonuçlanmış geçmişiyle eğitilir/sınanır
  (MODEL B ≥ 300 oranlı örnek, kalibrasyon ≥ 150 maç). Bu birikene kadar bildirim verilmez; değerlendirmeler
  yine kaydedilir ve gerekçesiyle gösterilir.
- **Bildirim teslimi:** tarayıcı bildirimi; uygulama sayfası açıkken çalışır (sekme arka planda olabilir).
  Telefona uygulama kapalıyken bildirim (web push) yoktur (Aşama 61).
- **Sunucu açık olmalı:** yoklama `serve` çalışırken arka planda yapılır (`AKILLI_TAHMIN_WINDOW_RADAR=0` kapatır)
  veya `python -m akilli_tahmin window-radar --db gercek.sqlite3` ile ön planda. Sunucu kapalıyken kaçan
  pencereler "kaçırıldı" olarak işaretlenir.
