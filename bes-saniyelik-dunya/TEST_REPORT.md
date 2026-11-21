# Test Raporu — 5 Saniyelik Dünya 1.0.0

Tarih: 2026-11-21 · Ortam: Linux bulut konteyneri, OpenJDK 21, Gradle 8.14.3, Android SDK 36 (derleme araçları 35.0.0), libGDX 1.13.1.
Bütün sonuçlar bu ortamda **gerçekten çalıştırılmıştır**.

## Özet

| # | Kontrol | Sonuç | Nasıl |
|---|---|---|---|
| 1 | Projenin derlenmesi | ✅ | `./gradlew :core:compileKotlin :desktop:compileKotlin` |
| 2 | Oyunun açılması | ✅ | Masaüstü sürümü Xvfb + Mesa (gerçek OpenGL) altında açıldı, tur hatasız bitti |
| 3 | Karakter kontrolleri | ✅ | `SimulationTest`: otomatik koşu, dokun/basılı tut zıplama, coyote time, zıplama tamponu, çift zıplama |
| 4 | Box2D çarpışmaları | ✅ | `SimulationTest`: platforma iniş, duvara takılma, diken/testere, boşluğa düşme |
| 5 | On dünya mekaniği | ✅ | Her mekanik için ayrı test (ters yer çekimi, hızlı, buz, hayalet, zıplayan, küçük + dar geçitte ertelenen büyüme, ters kontrol, yavaş, değişen platformlar) |
| 6 | Mekanik kombinasyonları | ✅ | Üçlü kombinasyon testi + uyumluluk matrisi/öncelik çözümü (10×10×10 bütün kombinasyonlar) |
| 7 | Beş saniyelik geçişler | ✅ | Değişim zamanı ±20 tick, hızlı dünyada 5 gerçek saniye, 1,2 sn önceden uyarı |
| 8 | Bölüm yükleme | ✅ | 10 harita dosyası, 500 bölüm, veri tutarlılığı, benzersizlik |
| 9 | Bölüm tamamlama | ✅ | Gerçek oyun ayarlarıyla 7 bölüm + arayüzde 10 bölüm baştan sona |
| 10 | 500 bölümün çözülebilirliği | ✅ | (a) kayıtlı çözümle 500/500 baştan oynatma, 3/3 yıldız ve gizli parçalar alındı; (b) çözücüyle **sıfırdan** 500/500 çözüldü |
| 11 | Sonsuz mod engel üretimi | ✅ | 5 tohumda çözücü 90 sn hayatta kaldı; üretim deterministik; kurallar her aşamada değişiyor; hız artıyor |
| 12 | Skor hesaplama | ✅ | Formül, yıldız kuralları, imkânsız puanların reddi |
| 13 | XP ve seviye sistemi | ✅ | Seviye atlama, 5'te bir özel ödül, 10'da bir kozmetik |
| 14 | Görev sistemi | ✅ | 3 günlük / 5 haftalık görev, tarih tohumu, ilerleme, ödül, tekrar alma engeli, gün/hafta yenilenmesi |
| 15 | Ödüller | ✅ | 7 günlük giriş döngüsü, gün kaçırınca sıfırlanmama, koleksiyon ödülü, başarı elmasları |
| 16 | Mağaza | ✅ | Yetersiz bakiye, satın alma, sahip olunanı tekrar alamama, güçlendirme envanteri, altın paketi |
| 17 | Karakter kilitleri | ✅ | Seviye/koleksiyon/yıldız kilitleri satın alınamaz, oynayarak açılır; 20 benzersiz karakter |
| 18 | Kayıt ve yükleme | ✅ | Kalıcılık, bozuk ana kayıtta yedekten kurtarma, elle değiştirilmiş kaydın reddi, sürüm göçü |
| 19 | Menü geçişleri | ✅ | Masaüstü turu: 14 menü ekranı + oyun + sonsuz mod sırayla açıldı, ekran görüntüleri alındı |
| 20 | Android derleme | ✅ | Debug APK, R8 küçültmeli release APK ve AAB üretildi; APK içeriği (4 ABI için libgdx/box2d/freetype .so, varlıklar) doğrulandı |

**Otomatik birim/entegrasyon testleri: 61/61 geçti** (`./gradlew :core:test`, ~5 dk).

| Test sınıfı | Test | Başarısız |
|---|---|---|
| SimulationTest | 19 | 0 |
| LogicFixesTest (denetimde bulunan mantık hatalarının düzeltmeleri) | 15 | 0 |
| LevelsTest | 11 | 0 |
| ProgressTest | 11 | 0 |
| AuditTest (görsel ↔ mantık, aşama içeriği, kural çizgileri, kontrol noktaları) | 5 | 0 |

## Bölüm mantığı denetimi ve zorluk kalibrasyonu (21 Kasım)

Geri bildirim: "Bölümlerde mantık hataları var, örneğin kapıya gelince ters dünyada kalıyor. Teller, testereler, cisimler küçük, belli olmuyor. Tüm bölümleri incele, tek tek oyna, zorluk düzeylerini düzgünce ayarla."

### 1. Nasıl incelendi

* **Çok yönlü denetim:** dört bağımsız denetçi (bölüm sonu, kural geçişleri, ölüm/yeniden doğuş, engel kalıpları) kodu okudu ve 500 bölümü gerçek oyun motoruyla oynatarak ölçtü; 30 bulgu çıktı (aşağıda). Diğer dört mercek (görünürlük, zorluk, HUD, fizik çekirdeği) kullanım sınırına takıldığı için çalıştırılamadı; bu alanları kendi ölçümlerim ve testlerimle kapattım.
* **İkinci tur kod incelemesi:** iki bağımsız inceleyici (simülasyon ve bölüm üretici/ölçüm) değişiklikleri okuyup yayımlanan bölüm verisi üzerinde ölçtü; 12 bulgunun tamamı düzeltildi (tablonun son 8 satırı) ve 500 bölüm yeniden üretildi. Üçüncü inceleyici (oyun ekranı / çizim) ile bölüm şeritlerini inceleyecek on ajan kullanım sınırı nedeniyle **çalışamadı**; oyun ekranı kodunu kendim gözden geçirdim (2 hata bulundu ve düzeltildi).
* **Her bölüm "insan gibi" oynatıldı:** yeni `LevelAnalyzer` her bölümü, dokunuşlarına zamanlama hatası eklenmiş (usta / orta / acemi) bir oyuncuyla 108 kez oynatır ve her zıplamanın zamanlama payını ölçer. 500 bölümde toplam 7 574 zıplama tek tek ölçüldü.
* **Her bölümün şeridi çizildi** (`tools/level_strip.py`): zemin, engeller, altınlar, kural aşamaları, oynanış yolu ve zıplama payları tek görselde; 118 bölümün şeridi (her alandan hedefinden en çok sapan bölüm + ilk 10 bölüm + finaller) ayrıca gözle incelendi.

### 2. Bulunan ve düzeltilen mantık hataları

| Hata | Etkisi | Düzeltme |
|---|---|---|
| **99 bölüm ters yer çekimiyle bitiyordu**; tavan kapıdan 5 karo önce kesiliyor, kapı yerde duruyordu | Hiçbir şey yapmayan oyuncu gökyüzüne düşüp ölüyor; bölüm ancak havada "bitiyordu" | Her bölümün sonunda 12 karoluk, her zaman **Normal Dünya** olan bitiş düzlüğü; karakter kapıya yerde varır |
| 430 bölümde kapıya küçükken / ters kontrolde / yavaş çekimde varılıyordu | Kapıda yarım boy ya da havada donmuş karakter | Aynı düzeltme; kapıdan zıplayarak geçen karakter de yere iner |
| Yükselen (kayan / dikey hareketli) platformda zıplama girişi çalışmıyordu | Döngünün %38–42'sinde dokunuş boşa gidiyor, karakter platformdan düşüyordu | "Yerde olma" platformun hızına göre ölçülüyor |
| Hareketli testereler varış anına göre bazen geçilemezdi | Kaçınılmaz ölüm | Testere oyuncuya kilitli: oyuncu ne zaman gelirse gelsin aynı konumda |
| Kontrol noktası, bayraktan sonraki ilk "yerde" anı kaydediyordu | Duvar dibinde kaydedilirse sonsuz ölüm döngüsü; havada geçilen bayrak kaydedilmiyordu | Bayrak geçildiği an kaydedilir; yeniden doğuş bayrağın dibinde, standart duruşla |
| Sonsuz Dünya'da Ekstra Can oyuncuyu dünyanın başına atıyordu | Koşu ve puan yok oluyordu | Koşu geri sarılmaz, son güvenli zeminde dirilir |
| "Devam Et" sonucu iki kez işliyordu | Altın, görev ve istatistikler çift sayılıyordu | Başarısız deneme yalnızca bölümden çıkarken, bir kez işlenir |
| 1–10. bölümlerde 57 "kural değişti" uyarısı hiçbir şeyi değiştirmiyordu | Sayaç, ses, titreşim ve bildirim boşuna çalışıyordu | Aynı kurallı ardışık aşamalar kural değişimi sayılmaz |
| Kaos Duvarı (finaller) hiç görünmüyor ve kimseyi yakalayamıyordu | Final bölümleri sıradan bölüm gibiydi | Duvar ekranın sol kenarında görünür; duran oyuncuyu ~1 sn'de yakalar |
| Kalkan/dokunulmazlık boşluk kurtarması sonsuza dek yenileniyordu | Tek kalkan sınırsız koruma; karakter kenara geri konuyordu | Tek kurtarma; son güvenli zeminin 2–6 karo gerisine, aynı kural aşamasına |
| Buzda hız pedi hızı 2 katına çıkarıyor, iki kural boyunca sönmüyordu | Kontrol edilemeyen kayma | Ped buzla çarpılmaz, fazla hız çabuk söner |
| Yavaş Zaman aşamalarının 172'si tamamen boştu | 5–15 sn hiçbir şey olmuyordu | Sığmayan desen yerine sığanı konur: boş aşama 172 → 36 (3 870 aşamada; tamamı yer çekimi dönüşü ya da boy değişimi sonrası çok kısa kalan Yavaş Zaman aşamaları) |
| Desenler kural çizgisinin tampon bölgesine taşıyordu (97 yer), 30 "kılcal" boşluk | Kural değişirken diken üstünde olma; boşluk gibi görünüp üstünden koşulan çatlaklar | Taşan desen geri alınır; gerçek boşluk en az 1,8 karo: her ikisi de 0 |
| Küçülme, yer çekimi dönüşüyle aynı anda bitince karakter havada büyüyordu | Görsel sıçrama | Büyüme uygun yüzeye göre hemen yapılır |
| Geçen tur eklediğim gömülme düzeltmesi basamak tırmandırıyordu | Karakter 1 karoluk boşlukların üstünden koşuyordu | Düzeltme yalnızca yüzeye üstten/alttan gelindiğinde |
| Yeniden doğuşta oyun hemen başlıyordu; HUD eski uyarıyı gösteriyordu | Oyuncu karakteri bulamadan can kaybediyordu | 0,8 sn "Hazır ol!" duraklaması; HUD yeniden doğuşta tazelenir |
| Tavandaki dikenli boşluk yalnızca 0,1 sn'lik dokunuşla geçilebiliyordu | 9. bölümde bile kare hassasiyeti | Dikenler yükseltildi (0,2 sn'ye kadar basış sığar), ipucu eklendi |
| **"Değişen Platformlar" duyurulan 593 aşamanın 172'sinde kayan platform yoktu** (46 bölümde hiç); hayalet köprü ve dar geçitte de benzeri | Kural duyuruluyor, dünyada hiçbir şey değişmiyordu | Desenler bölgeye sığacak kadar küçülebiliyor, kurala özgü nesne önce yerleşiyor; nesnesi hiç sığmayacak kadar kısa aşamadan (yer çekimi dönüşü ardından Yavaş Zaman) o kural çıkarılıyor: **eksik 0** |
| Hayalet köprünün son parçası karakterden dar olabiliyordu (91 köprü); dikey platform çiftinde 0,4–1,7 karoluk "boşluklar" vardı | Yanıp sönen parçanın / boşluğun üstünden havada koşma | Köprü eşit parçalara bölünür (her biri ≥ 2 karo); platform boşlukları en az 1,8 karo |
| Yatay hareketli platform koşu hızından hızlı gidebiliyordu (405. bölümde 5,6 karo/sn) | Üstündeki karakter geriye sürükleniyordu | Platformun en yüksek hızı koşu hızının %65'ini aşmaz |
| Kalkan / dokunulmazlık kurtarması final bölümlerinde oyuncuyu Kaos Duvarı'nın içine bırakıyordu | "Kalkan korudu!" yazısından hemen sonra can kaybı | Kurtarmada duvar oyuncunun gerisine çekilir |
| Kurtarma, aşılmış bir boşluğun gerisindeki önceki platforma dönebiliyordu | Işınlan–düş döngüsü; kalkan ya da ekstra can boşa gidiyordu | Kurtarma her zaman son basılan platformda kalır, ikinci düşüşte de aynı noktaya döner |
| Sonsuz Dünya'da kural uyarısı sırasında ölünce uyarı yutuluyordu | Dirilişten 1 sn sonra uyarısız kural değişimi | Dirilişte uyarı yeniden verilir |
| Eğitim bölümünde "dokun" ipucu beklenirken ölen oyuncu ağır çekimde kalıyordu; başlangıç kartında geri tuşu çalışmıyordu | Bölüm başında yavaşlamış, ipucusuz oyun | İpuçları yeniden doğuşta geri sarılır; geri tuşu bölüm seçimine döner |
| Zorluk ölçümü: yalnızca çok kısa dokunuşla geçilen zıplamalar "serbest" sayılıyor, duvara takılan dokunuşlarda model oyuncu iki kez zıplıyordu | Bazı bölümlerin puanı olduğundan kolay/zor çıkıyordu | Tutma payı insanın basabileceği sürelerden (≥ 5 tick) hesaplanır; 5 tick'ten kısa dokunuşlar uzatılır; refleks zıplaması planlı dokunuşu tüketir |

### 3. Engeller ve nesneler artık iri ve belirgin

* Dikenler soluk mavi, karo başına 5 ince dişli ve 0,58 karo boyundaydı ("tel" gibi). Şimdi **kırmızı, koyu konturlu, karo başına 2 iri diş ve 0,92 karo** (karakterin yarı boyundan fazla); isabet bölgesi çizimle birlikte büyütüldü.
* Testereler **kırmızı, konturlu**, çap 1,0–1,4 karodan **1,5–2,2 karoya** çıktı. Diken ve testerelerin arkasında nabız gibi atan kırmızı parıltı var.
* Yay, hız pedi, bayrak, altın, yıldız, güçlendirme ve gizli parça büyütüldü; kapı, yay ve pedlere kontur eklendi. On haritanın arka planında özgün / konturlu / kırmızı sürümler karşılaştırılarak seçildi.
* `AuditTest` yeni boyutlarla da "görsel dikene/testereye değip ölmeden geçme" olmadığını 500 bölümde doğrular (0 temas).

### 4. Zorluk kalibrasyonu

**Önceki durum (ölçüm):** çözücü, engellere son anda tepki veren ve duvar dibinde bekleyen çözümler buluyordu; kabul ölçütü yalnızca "bütün girişleri ±2 tick kaydır" idi. İnsan modeliyle ölçüldüğünde her zorluk etiketinde bölümler 1'den 10'a rastgele dağılıyordu (ör. 4. bölüm oyunun en zorlarından, bazı meydan okumalar en kolaylarındandı) ve 6. ile 9. haritaların yarısı aşırı zordu.

**Şimdi:**

* Çözücü "dürüst" çözer: coyote payını kullanmaz, duvarda oyalanmaz, isabet kutuları büyütülmüştür. Çözüm doğal dokunuşlara çevrilir ve her zıplama zamanlama penceresinin ortasına alınır; altın ve yıldızlar bu doğal yol üzerine dizilir.
* Her bölümün zorluğu ölçülür ve **hedef eğrisine** oturtulur: harita içinde artan, yeni haritada nefes aldıran "testere dişi"; meydan okuma bölümleri bir basamak yukarıda, yeni mekaniğin tanıtıldığı bölümler kolay.
* Desenler gerçek hıza göre boyutlanır (buzda ve Hızlı Dünya'da iniş alanları genişler), kural değişimlerinden sonra tepki süresi zorlukla kısalır (1,05 → 0,65 sn), ilk engel başlangıçtan en az ~1,7 sn sonradır.

`./gradlew :core:analyzeLevels` sonucu (`docs/difficulty_report.tsv`; ölçek 1–10, tamamlama = bölümü hiç ölmeden bitirme olasılığı):

| Harita | Hedef ort. | Ölçülen ort. | Hedef bandında | En dar pay (tick) | Tamamlama usta / orta / acemi | Ort. süre |
|---|---|---|---|---|---|---|
| 1 | 1.79 | 1.79 | 50/50 | 10 | %98 / %92 / %76 | 39 sn |
| 2 | 2.39 | 2.21 | 50/50 | 9 | %96 / %84 / %64 | 40 sn |
| 3 | 3.06 | 2.84 | 50/50 | 9 | %94 / %73 / %47 | 40 sn |
| 4 | 3.68 | 3.47 | 50/50 | 7 | %88 / %61 / %36 | 45 sn |
| 5 | 4.30 | 4.32 | 50/50 | 7 | %76 / %47 / %29 | 45 sn |
| 6 | 4.92 | 4.82 | 50/50 | 7 | %75 / %40 / %20 | 46 sn |
| 7 | 5.54 | 5.42 | 50/50 | 7 | %71 / %34 / %13 | 50 sn |
| 8 | 6.16 | 6.06 | 50/50 | 6 | %71 / %26 / %9 | 50 sn |
| 9 | 6.78 | 6.65 | 50/50 | 6 | %61 / %21 / %7 | 51 sn |
| 10 | 7.41 | 7.30 | 50/50 | 6 | %56 / %17 / %5 | 56 sn |

* **500 / 500 bölüm hedef bandında**, en büyük sapma 0,9 puan; harita ortalamaları hedefin en çok 0,22 puan altında ve haritadan haritaya düzenli artıyor.
* **Hiçbir zıplama kare hassasiyeti istemiyor:** 500 bölümde en dar zamanlama payı 6 tick (100 ms), ilk üç haritada en az 9–10 tick; ilk 12 bölümde en az 19 tick (0,3 sn). Basılı tutma payı (insanın basabileceği sürelerle ölçülür) en az 5 tick.
* İlk 10 bölümde orta oyuncunun hiç ölmeden bitirme olasılığı en az %96.
* Her kontrol noktasında yeniden doğan oyuncu, hiçbir şey yapmasa da en az 1 sn hayatta kalır (1 500+ bayrak, 0 sorun).
* **Zorluk nasıl yükseliyor:** ileri haritalarda zamanlama payı 6–7 tick'in altına inmiyor (dikenlerde ~8 tick); bunun yerine aşama başına engel sayısı artıyor (10. haritada bölüm başına ortalama 19 zıplama, 1. haritada 11).
* **Eğri düzeltmesi:** ikinci tur incelemede ölçüm modelindeki iki hata giderilince (bkz. tablo) ileri haritalar hedeften ~0,5 puan kolay ölçüldü. Üretici yüksek zorlukta kurala özgü uzun desenlerle (uzun tünel/köprü) aşamayı doldurduğu için hedefe ulaşamıyordu; "yoğunluk" ayarı eklendi ve eğrinin harita basamağı 0,68 → 0,62'ye çekildi (10. harita hedef ortalaması 7,9 → 7,4). 500 bölümün tamamı yeniden üretildi; banttan sapan 106 bölüm yeni ayarla bir kez daha üretildi.

Ölçüm bir **modeldir** (zamanlama hatası olan, yolu bilen bir oyuncu); gerçek oyuncuların algı ve öğrenme farklarını içermez. Mutlak yüzdeler değil, bölümler arası sıralama ve zamanlama payları güvenilirdir.

## Google Play'e hazırlık denetimi (21 Kasım)

Soru: "Uygulama Play Store'a yüklenmeye hazır mı, sıkıntı çıkar mı?" Paket yerinde denetlendi; güncel kurallar üç bağımsız araştırma ve bir doğrulama adımıyla kaynaklarından kontrol edildi (ayrıntı ve kaynaklar: `docs/play_store/YAYIN_REHBERI.md`).

| Bulgu | Önce | Şimdi |
|---|---|---|
| Hedef API (31 Ağustos 2026'dan beri yeni uygulamalar API 36 hedeflemeli; uzatma 1 Kasım'da bitti) | `targetSdk 35`: yükleme reddedilirdi | `targetSdk 36`, `compileSdk 36`; `aapt2 dump badging` ile doğrulandı |
| Android 16'da geri tuşu (API 36 hedefinde KEYCODE_BACK varsayılan olarak gönderilmez) | Geri tuşu oyun menüsü yerine ana ekrana çıkarırdı | `enableOnBackInvokedCallback="false"` ile eski davranış korunuyor |
| Kenardan kenara çizim / çentik | Üst köşedeki duraklat düğmesi kamera deliğinin altında kalabilirdi | Arayüz ekran kesiği kadar içeri çekiliyor (`BaseScreen.applySafeArea`); masaüstünde `-Dinsets=90,0,0,0` ile denendi |
| Uygulama içinde gizlilik politikası | Yoktu | Ayarlar > Gizlilik Politikası |
| Mağaza simgesi | 24-bit PNG | 32-bit, opak PNG |
| Mağaza ekran görüntüleri | 960×540, eski görseller | 8 adet 1920×1080, güncel oyun (`docs/play_store/ekran_goruntuleri/`) |
| 16 KB bellek sayfası, 64-bit, AAB, izinler | — | Uyumlu: LOAD hizası 0x4000; yalnızca `VIBRATE`; ağ, reklam, satın alma, analiz yok |
| İmza | Hata ayıklama anahtarı | **Değişmedi: yükleme anahtarını geliştirici oluşturmalı** |

Son derlemeden sonra: 61/61 test, 10 bölümlük 1920×1080 oyun turu (3, 60, 120, 180, 230, 290, 340, 390, 440, 500) 10/10 `TAMAMLANDI`.

## Çarpışma kutusu ↔ görsel uyumu ve tam oyun turu (20 Kasım)

Geri bildirim: karakter büyüdüğü için dikenlere görsel olarak sürtüp ölmeden geçiyordu; bazı ödül ekranları büyüktü.

* **Neden:** karakter ekranda ~1,67 karo boyunda ve pelerinle ~2 karo genişliğinde çiziliyordu, çarpışma kutusu ise 0,8×0,8 karoydu.
* **Düzeltme:** çarpışma kutusu gövdeye uygun dikdörtgen yapıldı (0,85 × 1,42 karo); karakter 1,55 karo boyunda, ayakları kutunun tabanına oturacak şekilde çiziliyor. Diken isabet bölgesi çizilen dikene, testere isabet yarıçapı çizilen testereye yaklaştırıldı. Dar geçit, üstü dikenli blok ve tavan boşluğu kalıpları yeni boya göre yeniden ölçülendi; 500 bölüm yeniden üretildi ve doğrulandı.
* **Gömülme hatası (yeni bulundu):** hızlı düşüşte karakter bir-iki kare zeminin 0,2+ karo içine giriyordu (sürekli çarpışma kapalı). Sabit katılarla örtüşme her adımda hemen gideriliyor.
* **Yeni `AuditTest`:** 500 bölümün hepsi kayıtlı çözümle oynatılırken her karede (a) karakterin çizilen gövdesi çizilen dikene/testereye değip ölmeden geçiyor mu, (b) karakter bir katının içine giriyor mu, (c) altın/yıldız/parça bir katının içinde mi, (d) havada asılı diken var mı kontrol ediliyor. İlk çalıştırmada 499 bölümde gömülme bulundu; düzeltmeden sonra **0 sorun**.
* **Sonuç ekranları sabit boyutta:** "Bölüm Tamamlandı", "Canların Bitti" ve sonsuz mod sonuç pencereleri artık içerik ne olursa olsun aynı boyutta (iki sütun; ödül/görev bildirimleri sağda kaydırılabilir alanda).
* **Tam tur:** her alandan bir bölüm + 1. ve 500. bölüm (**102 bölüm**) gerçek oyun ekranında baştan sona oynatıldı: **102/102 tamamlandı, 0 başarısız**. Ekran görüntüleri incelenerek şu görüntü hataları bulunup düzeltildi:
  * Ağaç/kaya süslerinde kaynak görselin kırpma çerçevesinden kalan ince koyu çizgiler (havada asılı "çubuk" gibi görünüyordu) → süs işleme adımına ince çizgi temizliği eklendi.
  * Güçlendirme bildirimi, kural etiketi ve ipucu kutusu üst üste biniyordu → bildirimler sol üstte küçük, kural etiketi ekranın ortasının üstünde, ipucu üstte ayrı yerlerde.
  * 2. haritanın (Ters Gökyüzü) zemini gökyüzüyle aynı renkteydi, neredeyse görünmüyordu → zemin koyulaştırıldı, üst kenarına bulut kıvrımı eklendi.
  * Fontta olmayan ★ ve ✓ simgeleri kutucuk olarak görünüyordu → yazıyla değiştirildi.
  * Profil ekranında "Sonraki ödül: Seviye atladın!" gibi anlamsız metin → bir sonraki seviyenin gerçek ödülü yazıyor.
  * Ayrıntılı arka planlı haritalarda (5, 7, 8, 9, 10) karakter ve engeller öne çıksın diye arka plan pusu artırıldı.

## Bölüm üretimi ve doğrulama

`./gradlew :core:generateLevels` → 500 bölüm ~35 dk'da üretilir (100 alan × 5 bölüm): her aday çözülür, insanlaştırılır, ölçülür ve hedef zorluk bandına düşen aday kabul edilir (bölüm başına ortalama 6 aday). Hedeften sapan bölümler `-Poffset=N -Pimprove=1` ile farklı tohumlarla iki tur yeniden üretildi.

* Ortalama bölüm süresi 46 sn (en uzun: büyük final), her bölümde 3 yıldız, 50 bölümde gizli parça.
* Testler 500 bölümün tamamında şunları doğrular: kayıtlı çözüm bölümü bitirir, 3 yıldız ve parça alınır, kapıya Normal Dünya'da ve yerde varılır; çözücü bölümü sıfırdan çözer; son kural çizgisinden sonra hiç dokunulmasa da kapıya varılır; ölçülen zorluk hedefi izler; hiçbir zıplama dar pencere istemez.

## Arayüzde uçtan uca oynatma (masaüstü)

`xvfb-run ./gradlew :desktop:run -Dshots=<klasör>` — gerçek `GameScreen` üzerinde kayıtlı çözüm dokunuş girişi olarak oynatıldı:

```
Bölüm 1:   TAMAMLANDI (yıldız 3/3, altın 83, ölüm 0)
Bölüm 23:  TAMAMLANDI (yıldız 3/3, altın 85, ölüm 0)
Bölüm 61:  TAMAMLANDI (yıldız 3/3, altın 101, ölüm 0)
Bölüm 160: TAMAMLANDI (yıldız 3/3, altın 93, ölüm 0)
Bölüm 215: TAMAMLANDI (yıldız 3/3, altın 32, ölüm 0)
Bölüm 280: TAMAMLANDI (yıldız 3/3, altın 107, ölüm 0)
Bölüm 330: TAMAMLANDI (yıldız 3/3, altın 91, ölüm 0)
Bölüm 377: TAMAMLANDI (yıldız 3/3, altın 94, ölüm 0)
Bölüm 420: TAMAMLANDI (yıldız 3/3, altın 122, ölüm 0)
Bölüm 500: TAMAMLANDI (yıldız 3/3, altın 275, ölüm 0)
```

Her haritadan bir bölüm; tamamlanma ekranı, XP, başarı ve seviye bildirimleri görsel olarak kontrol edildi (`docs/screenshots/`).

**21 Kasım, son üretimden sonra** (aynı yöntem; yeni desenlerin bulunduğu bölümler): 1, 6, 41, 91, 216, 300, 442 ve 500. bölümler oyun ekranında baştan sona oynatıldı — 8/8 `TAMAMLANDI`, hepsinde 3/3 yıldız ve 0 ölüm. Ekran görüntülerinde kırmızı diken/testereler, "Değişen Platformlar" bildirimi ve kayan platform, hayalet köprü kontrol edildi.

## Karakter netliği

* Karakter atlaslarında mipmap kapatıldı (`Linear,Linear`): üç-doğrusal filtre 200 → ~130 piksel küçültmede yarı çözünürlüklü kopyayı karıştırıp karakteri bulanıklaştırıyordu.
* Oyun ekranında karakter %18 büyütüldü (yalnızca çizim; çarpışma kutusu ve ayak çizgisi aynı).
* Kare hizalama: gövde/bacak bandına göre yatay ortalama (pelerin salınımı karakteri kaydırmaz), koşu ve bekleme karelerinde boy eşitleme (±%10), ayak altındaki gri gölge şeridinin temizlenmesi.
* Önce/sonra karşılaştırması 1920×1080 pencerede alındı: `docs/karakter_once_sonra.jpg`. Testler 39/39.

## Görüntü kalitesi, bölüm haritası, ses

* Kalite: karakter kareleri 200 piksel (telefonda küçültülerek keskin çizilir), arka planlar kaynak çözünürlüğünde 2048×1024 (önce 512 yükseklikti), gökyüzü kaynak genişliğinde 16:9 bant (önce 540 genişlikte, 3,5 kat büyütülüyordu). Bazı paralaks katmanlarında kalan gökyüzü artıklarının oluşturduğu düz kenarlı bantlar temizlendi. Bellek için büyük karakter atlaslarından en fazla 3'ü tutulur; listelerde hafif önizleme atlası kullanılır.
* Bölüm haritası: masaüstünde kontrol edildi; bölüm düğmeleri açılıyor, şu anki bölüme otomatik kaydırılıyor.
* Altın sesi: hacim 0,45 → 0,16 ve kare dalga yerine yumuşak çan tınısı. Açılıştaki "sunar" yazısı kaldırıldı.
* APK 52 MB (yüksek çözünürlüklü görseller nedeniyle). Testler 39/39.

## Yatay ekrana geçiş

Oyun yalnızca yatay olarak çalışır (manifestte `sensorLandscape`, APK'da doğrulandı). Arayüz 1280×720 sanal çözünürlüğe, oyun kamerası 21×11,8 karoya göre yeniden düzenlendi; 15 menü ekranı, oyun içi göstergeler, diyaloglar ve açılış animasyonu masaüstünde 960×540 pencerede kontrol edildi. Bölüm 1, 23, 61, 160, 215, 330, 420 ve 470 yatay ekranda arayüz üzerinden baştan sona oynatıldı (hepsi 3/3 yıldız, 0 ölüm). Testler 39/39.

## Kullanıcı görsellerinin entegrasyonu

220 kullanıcı görseli otomatik kontrol edildi (boyut, şeffaflık, kenar dikişi). Bozuk gelen 34 dosya yeniden üretildi ve kullanıldı; 10 tavan karosu yerine zeminin çevrilmiş hali kullanıldı. Altın animasyonu ve güçlendirme baloncuğu yarı saydam geldiği için programatik çizimler korundu. Masaüstü turunda her haritadan bir bölüm yeni görsellerle baştan sona oynatıldı (hepsi 3/3 yıldız, 0 ölüm); testler 39/39 geçti. APK 32 MB.

## Açılış animasyonu

Masaüstünde (Xvfb + OpenGL) açılıştan oyunun splash ekranına kesintisiz geçiş doğrulandı; kare kare kayıt `docs/intro_animasyon.gif`. Gerilen beyaz bölgenin kenar yumuşaması nedeniyle oluşan açık renkli çerçeve ve portre ekranda devre katmanının boş kalması düzeltildi.

## Bulunan ve düzeltilen hatalar

* (Geri bildirim) Kural değişim yazıları ekranda uzun kalıp oyunu zorlaştırıyordu → bildirim küçük bir etikete dönüştürüldü, ekranın üstünde (HUD altında) 0,55–0,9 sn kalıyor; ölüm yazısı 0,5 sn, ipuçları 1,8 sn, oyun içi bildirimler 1 sn.
* (Geri bildirim) Alanlar 10 değil 5 bölüm olmalı → 100 alan, her alanın son bölümü meydan okuma; testle doğrulanıyor.

* Çözücü geri yüklemesi deterministik değildi (Box2D temas geçmişi) → geri yüklemede oyuncu gövdesi yeniden kuruluyor; hareketli platformlar tam hedef konumuna oturtuluyor.
* Yüksekten düşerken yanlış "ezildin" ölümü → ezilme yalnızca 5 tick süren derin gömülmede.
* Bölüm 69'da yalnızca 9 altın → az altınlı bölümler için ikinci yerleştirme geçişi (en az 24 altın).
* Gökyüzü şeridinde kenar filtreleme bantları, beyaz buton üstünde beyaz simgeler, uzun başlık taşması, ayar satırı çakışması → düzeltildi.
* R8 kural dosyasında sözdizimi hatası → düzeltildi.

## Yapılamayanlar (açıkça)

* **Gerçek Android cihaz veya emülatör testi yapılamadı**: bu konteynerde cihaz bağlı değil ve `/dev/kvm` yok. APK yapısal olarak doğrulandı (paket adı, etiket, başlatıcı aktivite, 4 ABI yerel kütüphaneleri, varlıklar); küçültme riskini azaltmak için oyun ve libGDX sınıfları R8'de korunuyor. Kurulumdan sonra ilk açılış, dokunma gecikmesi, ses ve 60 FPS performansının gerçek cihazda kontrol edilmesi önerilir.
* Düşük/orta segment cihaz performans ölçümü yapılamadı (aynı sebep). Masaüstü yazılım GPU'sunda (llvmpipe) oyun akıcı çalıştı.
* Android 16 davranışları (geri tuşu, kenardan kenara çizim, çentik payı) yalnızca manifest/kod düzeyinde ve masaüstü benzetimiyle doğrulandı; Android 16 cihazında denenmedi.
* Play Console Yardım sayfaları (support.google.com) bu ortamdan açılamadı; hesap ve form kuralları arama sonucu alıntılarına dayanır (bkz. yayın rehberi, bölüm 5).
* Release APK/AAB **hata ayıklama anahtarıyla** imzalı: kurulup denenebilir, Play Console'a yüklemek için kendi yükleme anahtarınızla yeniden derlenmeli (`keystore.properties`).
