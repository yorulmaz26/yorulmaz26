# Test Raporu — Işık Yolu 1.7.1

Tarih: 22 Kasım 2026 · Komut: `./gradlew clean :engine:test :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest`

**Sonuç: 116 test geçti, 0 hata.** `PromoCaptureTest`'in 2 kayıt testi yalnızca `-PpromoDir` verilince çalışır, normalde atlanır.
- 1.7.0: reklamlar ve Android 16 hedefi (aşağıda "Reklamlar ve Android 16").
- 1.6.0: bölüm denetimi (aşağıda "Bölüm denetimi").

| Test sınıfı | Test | Sonuç | Süre |
|---|---|---|---|
| `BeamReflectionTest` | 6 | ✅ | 0,01 sn |
| `ColorFilterTest` | 5 | ✅ | <0,01 sn |
| `PrismTest` | 3 | ✅ | <0,01 sn |
| `DoorKeyTest` | 5 | ✅ | <0,01 sn |
| `GameSessionTest` (akıllı ipucu planı dahil) | 10 | ✅ | 6,5 sn |
| `LevelPackTest` | 3 | ✅ | 0,06 sn |
| `AllLevelsSolvableTest` | 2 | ✅ | 4,6 sn |
| `LevelAuditTest` (500 bölümün bütün durumları) | 1 | ✅ | 39,3 sn |
| `ShortcutsTest` (kısayol ve canlı yem) | 3 | ✅ | 1,7 sn |
| `BeamFollowerTest` (mantık ölçüsü) | 3 | ✅ | 0,16 sn |
| `StarRatingTest` | 2 | ✅ | <0,01 sn |
| `ProgressTest` | 9 | ✅ | <0,01 sn |
| `LivesTest` (can sistemi, saat hilesi) | 11 | ✅ | <0,01 sn |
| `EconomyTest` (market, ekonomi, saat dilimi) | 15 | ✅ | 0,02 sn |
| `AdPolicyTest` (reklam sıklığı ve video sınırları) | 6 | ✅ | 0,02 sn |
| `UiFlowTest` (Robolectric, gerçek çizim; denetim, reklam ve dar ekran testleri) | 32 | ✅ | ~300 sn |

## İstenen 10 kontrol

| # | Kontrol | Nasıl doğrulandı | Sonuç |
|---|---|---|---|
| 1 | Derleme testi | `:engine:compileJava`, `:app:assembleDebug`, `:app:assembleRelease` (R8). Lint: 0 hata | ✅ |
| 2 | Bölüm yükleme | `LevelPackTest.allLevelsLoad`: 500 bölüm, kimlikler 1–500, grup eşleşmesi, kaynak/hedef var, çakışan hücre yok, JSON gidiş-dönüş aynı, **kopya düzen yok**. Uygulamada `GameLoader` aynı dosyayı yükler (UiFlowTest) | ✅ |
| 3 | Işın yansıma | `BeamReflectionTest`: 8 yön/ayna birleşimi, yansıma tablosu ve tersinirlik, duvar/blok/kaynakta durma, ızgara kenarı, 4000 rastgele yoğun düzende sonlanma ve döngü yokluğu | ✅ |
| 4 | Renk filtresi | `ColorFilterTest`: 6 filtre rengi, engelleme, tam renk eşleşmesi, hedefte karışım (kırmızı + yeşil = sarı), kesişen ışınlar | ✅ |
| 5 | Prizma | `PrismTest`: 4 yönde beyaz → kırmızı sol / yeşil düz / mavi sağ; tek renkli sapma; prizmadan önce filtre | ✅ |
| 6 | Kapı ve anahtar | `DoorKeyTest`: ışık alan anahtar kapıyı açar, karanlık anahtar açmaz, kapı kendini açamaz (en küçük sabit nokta), zincirleme kapılar, çözücü kapılı bölümü çözer. Ayrıca 9. grup bölümlerinde kapılar başlangıçta kapalı | ✅ |
| 7 | 500 bölümün çözülebilirliği | `AllLevelsSolvableTest`: her bölüm başlangıçta çözülmemiş; BFS çözüm buldu; kayıtlı optimum = BFS optimumu; çözüm yeniden oynatılınca çözülüyor; durum uzayı ipucu sınırının altında. `GameSessionTest.hintsLeadToSolution`: her bölüm yalnızca ipuçlarını izleyerek optimum hamlede bitiyor. `UiFlowTest.allLevelsPlayableThroughUi`: 500 bölüm sırayla, kilitler açılarak, **oyun ekranının dokunma arayüzünden** oynandı ve hepsi 3 ★ aldı | ✅ |
| 8 | Yıldız hesaplama | `StarRatingTest`: eşikler ve monotonluk; arayüz testinde 1500/1500 yıldız | ✅ |
| 9 | Kayıt ve yükleme | `ProgressTest`: JSON gidiş-dönüş, eski 100 bölümlük kaydın yeni sürümde okunması, her 10 bölümde +1 ipucu kuralı, bozuk kayıtta varsayılana dönme, kilit açma, yalnızca iyileşen rekorlar, başarılar, günlük seri, sıfırlama. UiFlowTest: yeni Activity aynı ilerlemeyi SharedPreferences'tan okuyor | ✅ |
| 10 | Android build testi | Debug ve release APK üretildi, `aapt2 dump badging` ile paket bilgisi ve `apksigner verify` ile imza doğrulandı | ✅ |

### Ek testler

- **Oturum** (`GameSessionTest`): sabit ayna dönmez, çözüldükten sonra hamle kabul edilmez, geri alma durumu bit bit geri
  getirir, blok itme ve geri alma, rastgele yanlış hamlelerden sonra ipucuyla çözüm.
- **Günlük bulmaca** (`AllLevelsSolvableTest.dailyPuzzleIsDeterministicAndSolvable`): aynı gün aynı bulmaca,
  ardışık günler farklı, 10 günün hepsi çözülebilir.
- **Arayüz akışı** (`UiFlowTest.allScreensRenderAndFlowWorks`): açılış → menü → öğretici → bölüm 1 dokunarak çözülür →
  tamamlandı penceresi → bölüm seçme → 9 farklı mekanikten bölüm → hamle, geri alma, ipucu penceresi, ipucu kullanımı
  (arka plan iş parçacığı) → duraklatma → günlük bulmaca (oynanıp çözülür) → başarılar → ayarlar → "Nasıl Oynanır?".
  Her adımda ekran görüntüsü alındı: [`docs/ekranlar/`](ekranlar).

## Reklamlar ve Android 16 (1.7.0)

### Otomatik testler

`AdPolicyTest` (motor):
- Öğretici bölümler sayılmaz.
- İlk 10 bölümde geçiş reklamı yoktur.
- Geçiş reklamı en az 3 bölüm ve 4 dakika arayla gösterilir: 30 bölüm ve 15 dakikalık örnekte tam 4 reklam.
- Ödüllü videodan sonraki 3 dakikada geçiş reklamı yoktur.
- Video ödüllerinin günlük sınırları ve gerçek etkileri (can, ipucu, kristal) doğru çalışır.
- Ertesi gün sınır yenilenir; geri giden gün yenilemez.
- Kayıt ve yükleme sonrası reklam durumu korunur.

`UiFlowTest` (Robolectric, sahte reklam sağlayıcısıyla):

| Test | Doğrulanan |
|---|---|
| `bannerPlacement` | İlk 3 bölümde banner yok. Bölümler ekranında var; ekran banner'ın üstünde biter ve banner ince bir şerittir. Pencere açılınca gizlenir, kapanınca geri gelir. Oyun ekranında, markette ve ayarlarda yok. Ayarlarda gizlilik seçenekleri düğmesi var. |
| `bannerPausedWheneverHidden` | Görünmeyen banner her durumda duraklatılır: yeni oyuncu, pencere altı, oyun ekranı, arka plan. Görününce yeniden çalışır. |
| `bannerRebuiltWhenWidthChanges` | Yuvanın genişliği değişince (döndürme, katlama, bölünmüş ekran) banner yeni genişlikte yeniden istenir. |
| `interstitialBetweenLevelsWithFrequencyCap` | 1–10. bölümler arayüzden oynanır: reklam çıkmaz. 11–16'da yalnızca 13'ten sonra bir reklam çıkar. Saat 5 dakika ilerletilince 17'den sonra ikinci reklam gelir. Reklamdan sonra istenen ekran açılır. |
| `rewardedVideoGivesLife` | Can bitince video ile +1 can alınır ve bölüm açılır. Günde 3 kez; sonra düğme kalkar. |
| `rewardedVideoGivesMoves` | Yarıda kapatılan video ödül vermez. Tam izlenince ek hak verilir; kristalli devam fiyatı artmaz ve oyun sürer. |
| `rewardedVideoGivesHintAndCrystals` | Video ile ipucu kazanılır ve hemen kullanılır. Markette +25 kristal, günde 3 kez. Video yeniden yüklenene dek yerinde "hazırlanıyor" yazar ve liste kaymaz. |
| `failedVideoAndPrivacyPolicy` | Açılamayan video ödül vermez ve doğru uyarı çıkar. Ayarlar'da gizlilik politikası okunur. |
| `fullscreenAdDoesNotPauseGame` | Tam ekran reklam açıkken oyun "Duraklatıldı" penceresi açmaz; normal arka plana geçişte açar. |
| `contentStaysInsideSystemBars` | Kenardan kenara çizimde içerik durum ve gezinme çubuğunun içinde kalır. |
| `tabletLandscapeWorks` | 1280×800 yatay tablette bölüm açılır, tahta sığar ve arayüzden çözülür. |

### Bağımsız inceleme (2 tur, her bulgu 2 şüpheciyle)

**1. tur:** ajanlar politika uyumunu, oyun akışını ve SDK kullanımını inceledi. 13 bulgu doğrulandı, 6'sı reddedildi.
Düzeltmeler:
- Oyun ekranındaki banner kaldırıldı: sürekli dokunulan düğmelere yakındı.
- Pencere açıkken banner gizlenir.
- Gizlilik politikası eklendi.
- Banner yenileme hatasında gizlenmez; hiç yüklenmezse yeniden dener.
- Market video düğmesi, video yeniden yüklenince geri gelir.
- `singleTop` başlatma modu kullanılır.
- Ödüllü video sonucu üç durumludur; geçiş reklamı sayacı yalnızca gösterimde ilerler.
- İzin isteği çevrimdışı açılıştan sonra yeniden denenir.
- **targetSdk 36:** Play 31 Ağustos 2026'dan beri bunu şart koşuyor.
- Kimlikler dördü birlikte verilmezse derleme durur.
- Yeni banner boyutu API'si kullanılır.

**2. tur:** ajanlar düzeltmeleri ve Android 16 geçişini inceledi. 6 bulgu doğrulandı, 2'si reddedildi.
Düzeltmeler:
- Banner'ın duraklatma durumu doğru başlar; duraklatılmışken yeniden deneme yapılmaz.
- Genişlik değişince banner yeniden kurulur. Uygulama Android 16'da dikey kilidini korumak için "oyun" kategorisinde
  bildirildi.
- Reklam dinleyicisi kapanan etkinliği tutmaz.
- Market listesi kaymaz.
- Gezinme çubuğu rengi doğru gösterilir.

Reddedilen başlıca öneriler ve gerekçeleri:
- "Geçiş reklamı düğmeye basınca çıkıyor." Bölüm sonu geçişi, Google'ın önerdiği doğal geçiş noktasıdır.
- "Ana menüde geri hareketi uygulamayı kapatıyor." Bu bilinçli bir davranış; önceki sürümle aynı.

### Sınırlar

- Gerçek AdMob reklamı bu ortamda gösterilemez: emülatör yok ve Robolectric reklam SDK'sını çalıştıramaz. Reklam
  akışları sahte sağlayıcıyla, SDK kullanımı ise kod incelemesi ve SDK bayt koduyla doğrulandı.
- İlk cihaz denemesi test kimlikleriyle yapılmalıdır ("Test Ad" etiketli reklamlar).

## Bölüm denetimi (1.6.0)

İstek: "Bölümlerin hepsini tek tek oyna; mantık hatası ya da geçememe sorunu var mı kontrol et." Denetim üç yoldan
yapıldı:
1. kodla, her bölümün bütün durumları gezildi;
2. ajanlar bölümleri çözüme bakmadan oynadı;
3. değişiklikler kod denetiminden geçti.

### 1. Kodla tam oynama (her bölümün bütün durumları)

`tools.Audit` her bölümde başlangıçtan ulaşılabilen **bütün** tahta durumlarını gezer. Kontrol ettikleri:

| Kod | Kontrol |
|---|---|
| `çözümsüz`, `optimum`, `hak-yetmez` | Çözüm var mı; kayıtlı en az hamle gerçek mi; en kısa çözüm hamle hakkına (2×optimum+5) sığıyor mu |
| `çıkmaz`, `tersinmez` | Hiçbir durumdan çözüme dönüş kapanmıyor mu; her hamle geri alınabiliyor mu |
| `ışın-kuralı` | Işın parçaları düz, ızgarada, engellerin içinden geçmiyor |
| `oynatma`, `yıldız`, `ipucu` | Çözüm oyun oturumunda (hak sınırıyla) oynanınca 3 yıldız; rastgele yanlış hamlelerden sonra ipuçları çözüme götürüyor |
| `kapı`, `kaynak-yönü`, `gereksiz-kaynak` | Her kapı bir durumda açılabiliyor; kaynak tahta dışına bakmıyor; her kaynak gerekli |
| `ölü-yem` | Hiçbir durumda ışık almayan döner ayna yok (oyuncu boşa dokunmasın) |
| `kısayol` (yeni) | Mantık bölümü "her döner aynaya bir kez dokun"la, satır/sütun sırasıyla dokunmayla ya da hepsini çevirip blokları itmekle bitmiyor |
| `canlı-yem` (yeni) | Mantık bölümünde çevrilince çözümü bozan, zaten doğru duran en az bir döner ayna var |
| `ipucu-sınırı`, `kopya`, `günlük` | Durum sayısı ipucu sınırının altında; aynı düzen iki kez yok; 60 günlük bulmaca çözülebilir |

**Sonuç (1.6.0 paketi):**
- 500 bölümde **6 594 836 durum** gezildi; 60 günlük bulmaca da denetlendi.
- **HATA 0, UYARI 0.** 16 BİLGİ var; hepsi "bir hedef başlangıçta yanıyor".
- `LevelAuditTest` bu denetimi her test çalıştırmasında yapar.

İlk turda 384 bölümde, hiçbir durumda ışık almayan 1003 döner ayna bulundu ve sabitlendi; optimumlar değişmedi.

### 2. Çözüme bakmadan oynayan ajanlar

Ajanlar her bölümü komut satırı oynatıcısında (`tools.Play`) açtı. Işını elle izleyerek çözdüler; çözüme
(`--solution`) ancak bitirdikten sonra baktılar.

| Tur | Bölümler | Akıl yürüterek çözülen | En az hamleyle | Kurala aykırı ışın |
|---|---|---|---|---|
| 1 (1.5.2 paketi) | 39 bölüm (1–492 arası, her dünyadan) | 39/39 | 37/39 (279: 15/10, 287: 9/8) | yok |
| 2 (1.6.0 ilk paketi) | değişen 29 mantık bölümü (yeniden üretilen 10'dan 4'ü dahil) | 29/29 | 29/29 | yok |

3. tur bulgusu düzeltilince son pakette bu bölümlerin bir kısmı 1 hamle uzadı. Örneğin 472'nin en az hamlesi 4'ten 5'e
çıktı. Son paket yeniden kodla tam denetlendi (HATA 0, UYARI 0) ve arayüzden 500 bölüm 3 yıldızla oynandı.

Doğrulanan davranışlar:
- yansıma ve prizmanın gidiş yönüne göre ayrışması (dört yönde);
- filtre, renk birleşimi ve tam eşleşme;
- anahtar–kapı zincirleri ve blok itme kuralları;
- hedefin ışığı durdurması ve sabit aynanın dönmemesi.

2. turda "her döner aynaya bir kez dokun" hiçbir bölümü bitirmedi. Ajanlar, zaten doğru duran tuzak aynaları ışını
izleyen bir oyuncunun bulabileceği türden, adil buldu.

### 3. Bulunan mantık hatası: mantık bölümleri düşünmeden geçiliyordu

İki konumlu aynalarda dönüşlerin sırası önemsizdir. Üretici karıştırırken çoğu zaman çözüm yolundaki bütün döner
aynaları çevirmişti. Sonuç olarak **454 mantık bölümünün 411'i** "her döner aynaya bir kez dokun" ile (gerekirse
blokları itip) bitiyordu. **55'i** bu yolla 3 yıldız veriyordu.

Düzeltme (`engine/Shortcuts.java`, `tools/AddLiveDecoys`, `tools/RegenerateLevels`):
1. Önce çözüm yolundaki sabit aynalar (yönü zaten doğru) döner yapıldı: 372 ayna. Optimum değişmez.
2. Bu yetmeyen 207 bölümde, çözümün çevirdiği tek bir yol aynası başlangıçta doğru bırakıldı. Bu bölümler 1 hamle
   kısaldı.
3. 5. dünyada optimumu değiştirmeden ek canlı yem eklendi.
4. Şart sağlanamayan 10 bölüm (35 56 101 102 103 109 110 124 172 202) zorluk gevşetilmeden yeniden üretildi.
   Hiçbirinde en az hamle azalmadı; 7'sinde arttı.
5. Üretici ve günlük bulmaca artık aynı şartı kendisi uyguluyor. 400 günlük bulmacada kısayolla çözülen 0,
   canlı yemi olmayan 0.

| Ölçü (454 mantık bölümü) | 1.5.2 | 1.6.0 |
|---|---|---|
| "Hepsine bir kez dokun" / satır-sütun sırası / çevir+it ile bitiyor | 411 | **0** |
| Bu yolla 3 yıldız | 55 | **0** |
| Ortalama karar ağacı (yaprak) | 39 | **51** |
| Ortalama döner ayna | 9,1 | 10,1 |
| Ortalama en az hamle | 7,41 | 6,97 |

**Mantık ölçüleri (1.6.0, dünyalara göre):**

| Dünya | Mantık bölümü | Karar ağacı ortalama | Ortanca | Canlı yem ortalama | En az canlı yem |
|---|---|---|---|---|---|
| 1 | 54 | 30,2 | 23 | 1,5 | 1 |
| 2 | 100 | 33,9 | 25 | 1,7 | 1 |
| 3 | 100 | 53,4 | 35 | 1,6 | 1 |
| 4 | 100 | 65,7 | 46 | 1,6 | 1 |
| 5 | 100 | 65,2 | 48 | 2,2 | 1 |

`ShortcutsTest` her mantık bölümünde kısayolun çözmediğini ve en az bir gerçek canlı yem olduğunu doğrular.
`BeamFollowerTest` ortancaların dünyadan dünyaya azalmadığını doğrular.

### 4. Kod denetimi bulguları

Her turda ajanlar değişiklikleri oyun akışı, dokunma ve motor açısından inceledi. Her bulguyu iki bağımsız şüpheci
yeniden üretmeye çalıştı; yalnızca yeniden üretilenler düzeltildi. Her düzeltmenin bir geri dönüş testi var.

| Tur | Doğrulanan | Reddedilen |
|---|---|---|
| 1 | 17 | – |
| 2 | 14 (3'ü kısayol) | 8 |
| 3 | 5 | 5 |

**1. tur düzeltmeleri:**
- Hak bitince geri ya da çıkış canı yakar.
- Pencere kapanırken çift dokunuş yutulur.
- Günlük bulmaca ve market günü saat hilesine dayanıklı.
- Akıllı ipucu gerekirse önce Geri Al önerir.
- Bir sürüklemede tek itme yapılır.
- Can 0'dayken "Tekrar Dene" bölümlere döner.

**2. tur düzeltmeleri:**
- "Her aynaya bir kez dokun" kısayolu kapatıldı.
- Geri Al'a art arda basmak artık yutulmuyor.
- Gece yarısını geçen günlük bulmaca ödülünü korur.
- Kazanır kazanmaz geri basınca da başarı ödülü verilir.
- İade edilen ipucu "ipucu kullanıldı" sayılmaz.
- İpucu talimatından sapınca bekleyen "Geri Al" sayacı sıfırlanır.
- Blok itmede sıçrama giderildi.
- Pencere açıkken altında hamle yapılmaz.
- Giriş kapanınca sürükleme donmaz.
- Saat dilimini değiştirmek market sınırlarını sıfırlamaz.
- İpucu eşitlikte yıldız kaybettirmez.
- İpucu yaklaşık 14 kat hızlı.

**3. tur düzeltmeleri:**
- Canlı yem aracı gereksiz yere optimum düşürüyordu. 14 bölüm eski hamle sayısına döndü; 472'nin optimumu 4'ten
  5'e çıktı.
- Canlı yem sayımı yalnızca çözümü gerçekten bozan aynaları sayar.
- Başarı afişi Tamamlandı penceresinin üstünde görünür.
- Can yokken reddedilen itmede blok kayarak geri döner.

## Sınırlar

- Uygulama gerçek bir telefonda veya emülatörde çalıştırılmadı (kapsayıcıda KVM yok). Arayüz Robolectric 4.14.1
  ile, Android 14 çerçevesinin gerçek çizim hattıyla (NATIVE grafik modu) JVM üzerinde çalıştırıldı. Dokunma
  olayları `BoardView.Host` arayüzünden ve ham `MotionEvent` olaylarıyla (sürükleme, ikinci parmak, pencere altında dokunma) verildi.
- 60 FPS hedefi cihazda ölçülmedi. Çizim döngüsü bellek ayırmayacak şekilde yazıldı (README › Performans).
- Ses çalma Robolectric'te gölgelenir; seslerin cihazda duyulması elle denenmelidir.

## Stüdyo açılışı (1.5.0)

- `UiFlowTest.splashScreenRenders`: uygulama Magic Lumos Software açılışıyla başlar; animasyon kareleri
  kaydedilir (`docs/ekranlar/00a…00e`); ardından ana menüye geçilir.
- `UiFlowTest.studioIntroCanBeSkipped`: açılışa dokununca oyunun açılış ekranına geçilir, oradan ana menüye.

## Market ve ekonomi (1.4.0)

| Senaryo | Sonuç |
|---|---|
| Başlangıç bakiyesi 50 kristal, 0 taş, varsayılan görünümler | ✅ |
| İpucu fiyatı aynı gün 60 → 80 → 100 → 120 → 140, 6. alım günlük sınırda reddedilir; ertesi gün fiyat ve sınır sıfırlanır | ✅ |
| Yetersiz bakiyede satın alma reddedilir, bakiye değişmez | ✅ |
| Saat 3 gün geri alınınca market günü geri gitmez | ✅ |
| Canlar doluyken can satılmaz; +1 can fiyatı her alımda +25; doldurma en fazla 4'e çıkarır | ✅ |
| Görünüm bölüm şartı, bir kez satın alma, takma ve varsayılana dönme, sahip olunmayanı takamama, taşla ödeme | ✅ |
| Günlük teklifler aynı gün aynı, ertesi gün farklı; %20–40 indirim; tek stok | ✅ |
| Devam fiyatı 80 → 160 → 320; önce bilet kullanılır; yetersiz kristalde devam reddedilir; oturuma +5 hak eklenir | ✅ |
| Can Kalkanı kaybedilen denemenin canını geri verir, deneme yokken harcanmaz | ✅ |
| Bölüm ödülü yalnızca ilk kez; 3 yıldıza yükselme +3; 5. dünyada 12 kristal; grup 30/30 bir kez 1 taş | ✅ |
| Günlük bulmaca ödülü seriyle artar (en fazla +30); büyük başarılar taş verir | ✅ |
| Denge: 500 bölümden en fazla 5 500 kristal; kristalle satılan görünümler 6 000 kristal; taşla satılanlar 12 taş | ✅ |
| Kaydet / yükle ve ilerlemeyi sıfırla ekonomiyi doğru işler | ✅ |

`UiFlowTest.marketFlow` arayüzde şunları doğrular:
- Market'in 5 sekmesi açılır.
- Galaksi tahtası önizlenir.
- Altın aynalar, Galaksi tahtası ve Yıldız Tozu ışını satın alınıp oyunda gösterilir.
- Hak bitince "+5 hak · 80 kristal" butonuyla devam edilir: 80 kristal düşer, can harcanmaz, hamleye devam edilebilir.
- "İlk Alışveriş" başarısı açılır.

Tüm 500 bölümü oynayan test, kazanılan kristalin bölüm kazancının tamamına eşit ya da fazla olduğunu doğrular.

## Can sistemi (1.3.0)

`LivesTest` testleri sahte bir telefon saatiyle (duvar saati, monoton saat, açılış sayacı ve ağ saati ayrı ayrı
oynatılarak) yapıldı:

| Senaryo | Beklenen | Sonuç |
|---|---|---|
| İlk kurulum, eski kayıt | 4 can | ✅ |
| Deneme başlar / kazanılır | 3 can → 4 can | ✅ |
| 4 deneme kaybedilir | 0 can, bölüm başlatılamaz | ✅ |
| 14 dk, sonra 20 dk geçer | 0 can, ardından 1 can; kalan 5 dk ilerleme korunur | ✅ |
| Saat 5 saat ileri alınır | can yok, değişiklik algılanır; gerçek 15 dk sonra 1 can | ✅ |
| Saat 3 saat geri alınır | can yok; gerçek süreyle yenilenir | ✅ |
| Telefon 32 dk kapalı kalır (yeniden başlatma) | 2 can | ✅ |
| Saat geri alınıp yeniden başlatılır | süre sayılmaz | ✅ |
| Saat 10 saat ileri alınıp yeniden başlatılır, ağ saati var | ağ saati kullanılır, can yok | ✅ |
| Kaydet / yükle | can, sayaç ve süren deneme korunur | ✅ |
| Hamle hakkı | hak bitince hamle kabul edilmez; geri almak hakkı iade etmez | ✅ |

`UiFlowTest.livesFlow` aynı akışı arayüzde doğrular:
- 4 denemede hak bitirilir, her seferinde "Hamle hakkın bitti" penceresi çıkar.
- 0 canla bölüm açılmaz, "Canın kalmadı" penceresi çıkar.
- 15 dakika (monoton saat) ilerletilince 1 can gelir.
- Bölümden yarıda çıkmak canı geri getirmez.
- 500 bölüm kazanılırken can hiç azalmaz.

Bilinen sınır: Android 13 öncesinde ağ saati yoksa, saati ileri alıp telefonu yeniden başlatan oyuncu
geçen süreyi ileri alınan kadar sayılmış bulur. Saati sonra geri alırsa yenilenme gerçek zaman o noktaya
gelene kadar durur. Sunucusuz bir oyunda bundan ötesi ancak internet saatiyle mümkün. Oyun kendi saat sunucusuna bağlanmaz; varsa Android'in ağ saati (API 33+) kullanılır.

## Mantık bölümleri (1.2.0)

**Ölçü:** ışını takip eden oyuncunun karar ağacı (`BeamFollower`). Oyuncu ışını kaynaktan izler; ışının
değdiği ilk karar verilmemiş döner aynada iki yönelimi dener. Ağacın yaprak sayısı "takip zahmeti"dir. Kolay
bölümde yanlış seçim ışını hemen duvara gönderir (ağaç ≈ ayna sayısı). Mantık bölümünde yanlış seçimler uzun,
inandırıcı çıkmazlara gider.

**Üretimdeki değişiklikler:** 11–500 arası bölümler yeniden üretildi; 1–10 öğretici bölümleri değişmedi.
1. dünyada 2–10. grupların son 6 bölümü, sonraki dünyaların tamamı mantık bölümüdür: toplam **454 bölüm**,
oyun ekranında "MANTIK" etiketiyle gösterilir. Bu bölümler için:
- En az karar ağacı şartı aranır: dünyaya göre 14, 20, 26, 30 ve 34 yapraktan başlar, grup içinde artar.
  Prizmalı, bloklu ve kapılı bölümlerde bu şart yarıya iner.
- Bir ayna iki ışın tarafından paylaşılabilir; aynayı çevirmek iki ışını birden etkiler.
- Bölüm başına +1 döner, +1 sabit yanıltıcı ayna eklenir.

95 bölümde şart sağlanamadı; hamle aralığı ve zahmet eşiği adım adım gevşetildi.

**Karar ağacı ortancası (her dünyanın x11–x00 bölümleri):**

| Dünya | Önceki sürüm (1.1.0) | 1.2.0 |
|---|---|---|
| Işık Bahçesi | 9 | 13 |
| Kristal Mağaralar | 11 | 22 |
| Gece Bahçesi | 20 | 29 |
| Yıldız Atölyesi | 22 | 35 |
| Işık Tapınağı | 28 | 36 |

**Mantık bölümlerinde ortalama / ortanca karar ağacı:**

| Dünya | Ortalama | Ortanca |
|---|---|---|
| 1 (54 bölüm) | 29,1 | 21 |
| 2 | 28,3 | 22 |
| 3 | 41,3 | 28 |
| 4 | 53,0 | 35 |
| 5 | 43,0 | 36 |

1. dünyanın öteki bölümlerinde ortalama 6,3'tür.

**`BeamFollowerTest` şunları doğrular:**
- Her mantık bölümünde karar ağacı en az 5 yapraklıdır.
- Her dünyada ortanca en az 14'tür.
- Ortancalar dünyadan dünyaya azalmaz.

## Dünyalara göre zorluk

| Dünya | Bölümler | Optimum hamle (en az–en çok) | Ortalama | En büyük durum uzayı |
|---|---|---|---|---|
| Işık Bahçesi | 1–100 | 1–9 | 4,62 | 76160 |
| Kristal Mağaralar | 101–200 | 3–11 | 5,48 | 74240 |
| Gece Bahçesi | 201–300 | 3–12 | 6,70 | 87552 |
| Yıldız Atölyesi | 301–400 | 3–12 | 7,99 | 94208 |
| Işık Tapınağı | 401–500 | 4–13 | 8,58 | 98304 |

1.5.2 ortalamaları: 4,93 · 6,01 · 7,06 · 8,44 · 8,90. Canlı yem için 207 mantık bölümü 1 hamle kısaldı (bkz. Bölüm denetimi).

İpucu kuralı: 3 ipucuyla başlanır; 3 yıldız tek başına ipucu vermez; her 10 tamamlanan bölümde +1 ipucu.
500 bölüm bitince 3 + 50 = 53 ipucu olur; arayüz testinde doğrulandı.

## İlk dünyada konulara göre zorluk

| Grup | Konu | Optimum hamle (en az–en çok) | Ortalama |
|---|---|---|---|
| 1 | Temel aynalar | 1–3 | 2,3 |
| 2 | Birden fazla ayna | 3–5 | 4,2 |
| 3 | Engeller | 4–5 | 4,2 |
| 4 | Renkli ışıklar | 3–6 | 4,5 |
| 5 | Renk filtreleri | 3–5 | 4,3 |
| 6 | Prizmalar | 3–6 | 4,3 |
| 7 | Birden fazla hedef | 4–8 | 5,7 |
| 8 | Hareketli bloklar | 3–7 | 5,0 |
| 9 | Kapılar ve anahtarlar | 4–7 | 5,0 |
| 10 | Karma mekanikler | 5–9 | 6,7 |

Her grubun ikinci yarısı ilk yarısından en fazla 4 hamle kolay olabilir (`LevelPackTest.difficultyProgresses`; gevşetilen bölümler nedeniyle).
Yeni mekaniğin geldiği grupların ilk bölümleri bilinçli olarak biraz daha kolaydır.

## 500 bölümün doğrulama tablosu

"Mantık" = mantık bölümü. "Kaynak/Hedef" = kaynak sayısı / hedef sayısı.
"Durum" = başlangıçtan ulaşılabilen farklı tahta durumu sayısı (BFS). "Canlı yem" = çözümde ışık alan ama zaten doğru
duran döner ayna sayısı (bunlara dokunan oyuncu yolu bozar).

| # | Ad | Mantık | Izgara | Kaynak/Hedef | Nesneler | Optimum | Durum | Canlı yem | Çözülebilir |
|---|---|---|---|---|---|---|---|---|---|
| 1 | İlk Kıvılcım 1 |  | 5×7 | 1/1 | 1 ayna (1 döner) | 1 | 2 |  | ✅ |
| 2 | İlk Kıvılcım 2 |  | 5×7 | 1/1 | 1 ayna (1 döner) | 1 | 2 |  | ✅ |
| 3 | İlk Kıvılcım 3 |  | 5×7 | 1/1 | 2 ayna (2 döner) | 2 | 4 |  | ✅ |
| 4 | İlk Kıvılcım 4 |  | 5×7 | 1/1 | 2 ayna (2 döner), 1 duvar | 2 | 4 |  | ✅ |
| 5 | İlk Kıvılcım 5 |  | 6×8 | 1/1 | 4 ayna (3 döner), 1 duvar | 3 | 8 |  | ✅ |
| 6 | İlk Kıvılcım 6 |  | 6×8 | 1/1 | 3 ayna (2 döner), 1 duvar | 2 | 4 |  | ✅ |
| 7 | İlk Kıvılcım 7 |  | 6×8 | 1/1 | 4 ayna (3 döner), 2 duvar | 3 | 8 |  | ✅ |
| 8 | İlk Kıvılcım 8 |  | 6×8 | 1/1 | 5 ayna (4 döner), 2 duvar | 3 | 16 |  | ✅ |
| 9 | İlk Kıvılcım 9 |  | 6×8 | 1/1 | 5 ayna (3 döner), 2 duvar | 3 | 8 |  | ✅ |
| 10 | İlk Kıvılcım 10 |  | 6×8 | 1/1 | 5 ayna (3 döner), 3 duvar | 3 | 8 |  | ✅ |
| 11 | Ayna Labirenti 1 |  | 6×8 | 1/1 | 5 ayna (4 döner), 1 duvar | 4 | 16 |  | ✅ |
| 12 | Ayna Labirenti 2 |  | 6×8 | 1/1 | 4 ayna (3 döner), 1 duvar | 3 | 8 |  | ✅ |
| 13 | Ayna Labirenti 3 |  | 6×8 | 1/1 | 4 ayna (3 döner), 1 duvar | 3 | 8 |  | ✅ |
| 14 | Ayna Labirenti 4 |  | 6×8 | 1/1 | 5 ayna (4 döner), 1 duvar | 4 | 16 |  | ✅ |
| 15 | Ayna Labirenti 5 | ✔ | 6×8 | 1/1 | 9 ayna (8 döner), 2 duvar | 4 | 256 | 1 | ✅ |
| 16 | Ayna Labirenti 6 | ✔ | 6×8 | 1/1 | 9 ayna (8 döner), 2 duvar | 4 | 256 | 1 | ✅ |
| 17 | Ayna Labirenti 7 | ✔ | 6×8 | 1/1 | 10 ayna (9 döner), 2 duvar | 5 | 512 | 3 | ✅ |
| 18 | Ayna Labirenti 8 | ✔ | 6×8 | 1/1 | 10 ayna (9 döner), 2 duvar | 5 | 512 | 1 | ✅ |
| 19 | Ayna Labirenti 9 | ✔ | 6×8 | 1/1 | 11 ayna (10 döner), 3 duvar | 5 | 1024 | 1 | ✅ |
| 20 | Ayna Labirenti 10 | ✔ | 6×8 | 1/1 | 12 ayna (11 döner), 3 duvar | 5 | 2048 | 4 | ✅ |
| 21 | Gölge Duvarlar 1 |  | 6×9 | 1/1 | 7 ayna (4 döner), 6 duvar | 4 | 16 |  | ✅ |
| 22 | Gölge Duvarlar 2 |  | 6×9 | 1/1 | 6 ayna (4 döner), 7 duvar | 4 | 16 |  | ✅ |
| 23 | Gölge Duvarlar 3 |  | 6×9 | 1/1 | 7 ayna (4 döner), 8 duvar | 4 | 16 |  | ✅ |
| 24 | Gölge Duvarlar 4 |  | 6×9 | 1/1 | 6 ayna (4 döner), 9 duvar | 4 | 16 |  | ✅ |
| 25 | Gölge Duvarlar 5 | ✔ | 6×9 | 1/1 | 10 ayna (8 döner), 10 duvar | 4 | 256 | 1 | ✅ |
| 26 | Gölge Duvarlar 6 | ✔ | 7×9 | 1/1 | 11 ayna (8 döner), 11 duvar | 4 | 256 | 1 | ✅ |
| 27 | Gölge Duvarlar 7 | ✔ | 7×9 | 1/1 | 12 ayna (8 döner), 12 duvar | 4 | 256 | 2 | ✅ |
| 28 | Gölge Duvarlar 8 | ✔ | 7×9 | 1/1 | 12 ayna (9 döner), 13 duvar | 4 | 512 | 2 | ✅ |
| 29 | Gölge Duvarlar 9 | ✔ | 7×9 | 1/1 | 13 ayna (9 döner), 14 duvar | 5 | 512 | 1 | ✅ |
| 30 | Gölge Duvarlar 10 | ✔ | 7×9 | 1/1 | 14 ayna (12 döner), 15 duvar | 5 | 4096 | 6 | ✅ |
| 31 | Renk Cümbüşü 1 |  | 7×9 | 2/2 | 5 ayna (4 döner), 2 duvar, renkli ışık | 4 | 16 |  | ✅ |
| 32 | Renk Cümbüşü 2 |  | 7×9 | 2/2 | 5 ayna (4 döner), 2 duvar, renkli ışık | 4 | 16 |  | ✅ |
| 33 | Renk Cümbüşü 3 |  | 7×9 | 2/2 | 5 ayna (4 döner), 3 duvar, renkli ışık | 3 | 16 |  | ✅ |
| 34 | Renk Cümbüşü 4 |  | 7×9 | 2/2 | 5 ayna (4 döner), 3 duvar, renkli ışık | 4 | 16 |  | ✅ |
| 35 | Renk Cümbüşü 5 | ✔ | 7×9 | 2/2 | 9 ayna (6 döner), 4 duvar, renkli ışık | 4 | 64 | 1 | ✅ |
| 36 | Renk Cümbüşü 6 | ✔ | 7×9 | 2/1 | 10 ayna (6 döner), 4 duvar, renkli ışık | 5 | 64 | 1 | ✅ |
| 37 | Renk Cümbüşü 7 | ✔ | 7×9 | 2/1 | 11 ayna (7 döner), 5 duvar, renkli ışık | 6 | 128 | 1 | ✅ |
| 38 | Renk Cümbüşü 8 | ✔ | 7×9 | 3/2 | 11 ayna (8 döner), 5 duvar, renkli ışık | 4 | 256 | 3 | ✅ |
| 39 | Renk Cümbüşü 9 | ✔ | 7×9 | 3/2 | 12 ayna (7 döner), 6 duvar, renkli ışık | 5 | 128 | 2 | ✅ |
| 40 | Renk Cümbüşü 10 | ✔ | 7×9 | 3/2 | 13 ayna (9 döner), 6 duvar, renkli ışık | 6 | 512 | 2 | ✅ |
| 41 | Süzgeç Atölyesi 1 |  | 7×9 | 1/1 | 5 ayna (4 döner), 2 duvar, 2 filtre | 4 | 16 |  | ✅ |
| 42 | Süzgeç Atölyesi 2 |  | 7×9 | 1/1 | 5 ayna (4 döner), 2 duvar, 2 filtre | 3 | 16 |  | ✅ |
| 43 | Süzgeç Atölyesi 3 |  | 7×9 | 1/1 | 4 ayna (3 döner), 3 duvar, 2 filtre | 3 | 8 |  | ✅ |
| 44 | Süzgeç Atölyesi 4 |  | 7×9 | 1/1 | 6 ayna (5 döner), 3 duvar, 2 filtre | 5 | 32 |  | ✅ |
| 45 | Süzgeç Atölyesi 5 | ✔ | 7×9 | 2/2 | 9 ayna (7 döner), 4 duvar, 4 filtre | 5 | 128 | 1 | ✅ |
| 46 | Süzgeç Atölyesi 6 | ✔ | 7×9 | 2/2 | 10 ayna (6 döner), 4 duvar, 4 filtre | 5 | 64 | 1 | ✅ |
| 47 | Süzgeç Atölyesi 7 | ✔ | 7×9 | 2/2 | 9 ayna (7 döner), 5 duvar, 4 filtre | 4 | 128 | 1 | ✅ |
| 48 | Süzgeç Atölyesi 8 | ✔ | 7×9 | 2/2 | 10 ayna (6 döner), 5 duvar, 4 filtre | 5 | 64 | 1 | ✅ |
| 49 | Süzgeç Atölyesi 9 | ✔ | 7×9 | 2/2 | 10 ayna (9 döner), 6 duvar, 5 filtre | 4 | 512 | 3 | ✅ |
| 50 | Süzgeç Atölyesi 10 | ✔ | 7×9 | 2/2 | 10 ayna (8 döner), 6 duvar, 5 filtre | 5 | 256 | 1 | ✅ |
| 51 | Prizma Bahçesi 1 |  | 7×10 | 1/3 | 3 ayna (3 döner), 2 duvar, 1 prizma | 3 | 8 |  | ✅ |
| 52 | Prizma Bahçesi 2 |  | 7×10 | 1/3 | 3 ayna (3 döner), 2 duvar, 1 prizma | 3 | 8 |  | ✅ |
| 53 | Prizma Bahçesi 3 |  | 7×10 | 1/3 | 4 ayna (4 döner), 3 duvar, 1 prizma | 4 | 16 |  | ✅ |
| 54 | Prizma Bahçesi 4 |  | 7×10 | 1/2 | 4 ayna (4 döner), 3 duvar, 1 filtre, 1 prizma | 4 | 16 |  | ✅ |
| 55 | Prizma Bahçesi 5 | ✔ | 7×10 | 1/3 | 8 ayna (6 döner), 4 duvar, 1 prizma | 4 | 64 | 1 | ✅ |
| 56 | Prizma Bahçesi 6 | ✔ | 7×10 | 1/3 | 10 ayna (9 döner), 4 duvar, 1 prizma | 5 | 512 | 2 | ✅ |
| 57 | Prizma Bahçesi 7 | ✔ | 7×10 | 1/2 | 8 ayna (6 döner), 5 duvar, 1 filtre, 1 prizma | 4 | 64 | 1 | ✅ |
| 58 | Prizma Bahçesi 8 | ✔ | 7×10 | 1/3 | 9 ayna (8 döner), 5 duvar, 1 prizma | 5 | 256 | 1 | ✅ |
| 59 | Prizma Bahçesi 9 | ✔ | 7×10 | 1/2 | 10 ayna (6 döner), 6 duvar, 1 filtre, 1 prizma | 5 | 64 | 1 | ✅ |
| 60 | Prizma Bahçesi 10 | ✔ | 7×10 | 1/3 | 11 ayna (7 döner), 6 duvar, 1 prizma | 6 | 128 | 1 | ✅ |
| 61 | Çoklu Hedef 1 |  | 7×10 | 2/2 | 7 ayna (6 döner), 2 duvar | 4 | 64 |  | ✅ |
| 62 | Çoklu Hedef 2 |  | 7×10 | 2/2 | 5 ayna (4 döner), 2 duvar | 4 | 16 |  | ✅ |
| 63 | Çoklu Hedef 3 |  | 7×10 | 2/2 | 6 ayna (5 döner), 3 duvar | 5 | 32 |  | ✅ |
| 64 | Çoklu Hedef 4 |  | 7×10 | 2/2 | 5 ayna (4 döner), 3 duvar | 4 | 16 |  | ✅ |
| 65 | Çoklu Hedef 5 | ✔ | 7×10 | 3/3 | 11 ayna (9 döner), 4 duvar | 6 | 512 | 2 | ✅ |
| 66 | Çoklu Hedef 6 | ✔ | 7×10 | 3/3 | 11 ayna (8 döner), 4 duvar | 6 | 256 | 1 | ✅ |
| 67 | Çoklu Hedef 7 | ✔ | 7×10 | 3/5 | 13 ayna (11 döner), 5 duvar, 1 prizma | 6 | 2048 | 3 | ✅ |
| 68 | Çoklu Hedef 8 | ✔ | 7×10 | 3/5 | 13 ayna (11 döner), 5 duvar, 1 prizma | 7 | 2048 | 2 | ✅ |
| 69 | Çoklu Hedef 9 | ✔ | 7×10 | 3/5 | 13 ayna (9 döner), 6 duvar, 1 prizma, renkli ışık | 7 | 512 | 2 | ✅ |
| 70 | Çoklu Hedef 10 | ✔ | 7×10 | 3/5 | 13 ayna (10 döner), 6 duvar, 1 prizma, renkli ışık | 8 | 1024 | 1 | ✅ |
| 71 | Hareketli Taşlar 1 |  | 7×9 | 1/1 | 3 ayna (3 döner), 8 duvar, 1 blok | 4 | 400 |  | ✅ |
| 72 | Hareketli Taşlar 2 |  | 7×9 | 1/1 | 2 ayna (2 döner), 8 duvar, 1 blok | 3 | 204 |  | ✅ |
| 73 | Hareketli Taşlar 3 |  | 7×9 | 1/1 | 3 ayna (3 döner), 9 duvar, 1 blok | 4 | 344 |  | ✅ |
| 74 | Hareketli Taşlar 4 |  | 7×9 | 1/1 | 3 ayna (3 döner), 9 duvar, 1 blok | 4 | 24 |  | ✅ |
| 75 | Hareketli Taşlar 5 | ✔ | 7×9 | 1/1 | 5 ayna (4 döner), 10 duvar, 2 blok | 4 | 15840 | 1 | ✅ |
| 76 | Hareketli Taşlar 6 | ✔ | 7×9 | 2/2 | 8 ayna (5 döner), 10 duvar, 2 blok | 6 | 7936 | 1 | ✅ |
| 77 | Hareketli Taşlar 7 | ✔ | 7×9 | 2/2 | 7 ayna (4 döner), 11 duvar, 2 blok | 6 | 11856 | 1 | ✅ |
| 78 | Hareketli Taşlar 8 | ✔ | 7×9 | 2/2 | 8 ayna (5 döner), 11 duvar, 2 blok | 6 | 19040 | 1 | ✅ |
| 79 | Hareketli Taşlar 9 | ✔ | 7×9 | 2/2 | 8 ayna (7 döner), 12 duvar, 2 blok | 6 | 76160 | 1 | ✅ |
| 80 | Hareketli Taşlar 10 | ✔ | 7×9 | 2/2 | 8 ayna (6 döner), 12 duvar, 2 blok | 7 | 3584 | 1 | ✅ |
| 81 | Kilitli Kapılar 1 |  | 7×9 | 2/2 | 7 ayna (6 döner), 3 duvar, 1 kapı | 4 | 64 |  | ✅ |
| 82 | Kilitli Kapılar 2 |  | 7×9 | 2/2 | 5 ayna (4 döner), 3 duvar, 1 kapı | 4 | 16 |  | ✅ |
| 83 | Kilitli Kapılar 3 |  | 7×9 | 2/2 | 7 ayna (6 döner), 4 duvar, 1 kapı, renkli ışık | 4 | 64 |  | ✅ |
| 84 | Kilitli Kapılar 4 |  | 7×9 | 2/2 | 6 ayna (5 döner), 4 duvar, 1 kapı, renkli ışık | 5 | 32 |  | ✅ |
| 85 | Kilitli Kapılar 5 | ✔ | 7×9 | 2/2 | 9 ayna (6 döner), 5 duvar, 1 kapı | 4 | 64 | 2 | ✅ |
| 86 | Kilitli Kapılar 6 | ✔ | 7×9 | 2/2 | 9 ayna (5 döner), 5 duvar, 1 kapı, renkli ışık | 4 | 32 | 1 | ✅ |
| 87 | Kilitli Kapılar 7 | ✔ | 7×9 | 3/3 | 12 ayna (10 döner), 6 duvar, 2 kapı | 7 | 1024 | 1 | ✅ |
| 88 | Kilitli Kapılar 8 | ✔ | 7×9 | 3/3 | 12 ayna (9 döner), 6 duvar, 2 kapı, renkli ışık | 7 | 512 | 1 | ✅ |
| 89 | Kilitli Kapılar 9 | ✔ | 7×9 | 3/3 | 12 ayna (10 döner), 7 duvar, 2 kapı | 5 | 1024 | 3 | ✅ |
| 90 | Kilitli Kapılar 10 | ✔ | 7×9 | 3/3 | 12 ayna (8 döner), 7 duvar, 2 kapı, renkli ışık | 6 | 256 | 2 | ✅ |
| 91 | Işığın Ustası 1 |  | 7×10 | 2/2 | 6 ayna (4 döner), 4 duvar, 1 filtre, 1 blok | 5 | 800 |  | ✅ |
| 92 | Işığın Ustası 2 |  | 7×10 | 2/4 | 7 ayna (5 döner), 4 duvar, 1 prizma, 1 kapı | 5 | 32 |  | ✅ |
| 93 | Işığın Ustası 3 |  | 7×10 | 2/1 | 7 ayna (6 döner), 5 duvar, 1 blok, renkli ışık | 7 | 3520 |  | ✅ |
| 94 | Işığın Ustası 4 |  | 7×10 | 2/2 | 7 ayna (6 döner), 5 duvar, 2 filtre, 1 kapı | 6 | 64 |  | ✅ |
| 95 | Işığın Ustası 5 | ✔ | 7×10 | 1/3 | 8 ayna (6 döner), 6 duvar, 1 filtre, 1 prizma, 1 blok | 5 | 2240 | 1 | ✅ |
| 96 | Işığın Ustası 6 | ✔ | 7×10 | 3/2 | 12 ayna (8 döner), 6 duvar, 1 kapı, renkli ışık | 7 | 256 | 1 | ✅ |
| 97 | Işığın Ustası 7 | ✔ | 7×10 | 2/2 | 9 ayna (7 döner), 7 duvar, 1 filtre, 1 blok, 1 kapı | 7 | 5632 | 1 | ✅ |
| 98 | Işığın Ustası 8 | ✔ | 7×10 | 2/3 | 11 ayna (9 döner), 7 duvar, 1 filtre, 1 prizma, 1 kapı | 7 | 512 | 1 | ✅ |
| 99 | Işığın Ustası 9 | ✔ | 7×10 | 3/2 | 13 ayna (10 döner), 8 duvar, 1 blok, 1 kapı, renkli ışık | 9 | 35840 | 1 | ✅ |
| 100 | Işığın Ustası 10 | ✔ | 7×10 | 3/5 | 12 ayna (7 döner), 8 duvar, 1 prizma, 1 blok, 1 kapı | 9 | 1664 | 1 | ✅ |
| 101 | Kristal Kapı 1 | ✔ | 5×8 | 1/1 | 8 ayna (7 döner), 2 duvar | 3 | 128 | 2 | ✅ |
| 102 | Kristal Kapı 2 | ✔ | 5×8 | 1/1 | 8 ayna (8 döner), 2 duvar | 3 | 256 | 4 | ✅ |
| 103 | Kristal Kapı 3 | ✔ | 5×8 | 1/1 | 9 ayna (8 döner), 2 duvar | 4 | 256 | 2 | ✅ |
| 104 | Kristal Kapı 4 | ✔ | 5×8 | 1/1 | 7 ayna (6 döner), 3 duvar | 3 | 64 | 1 | ✅ |
| 105 | Kristal Kapı 5 | ✔ | 6×9 | 1/1 | 8 ayna (6 döner), 3 duvar | 3 | 64 | 1 | ✅ |
| 106 | Kristal Kapı 6 | ✔ | 6×9 | 1/1 | 8 ayna (7 döner), 3 duvar | 3 | 128 | 1 | ✅ |
| 107 | Kristal Kapı 7 | ✔ | 6×9 | 1/1 | 8 ayna (7 döner), 4 duvar | 3 | 128 | 1 | ✅ |
| 108 | Kristal Kapı 8 | ✔ | 6×9 | 1/1 | 10 ayna (9 döner), 4 duvar | 4 | 512 | 3 | ✅ |
| 109 | Kristal Kapı 9 | ✔ | 6×9 | 1/1 | 12 ayna (11 döner), 4 duvar | 5 | 2048 | 2 | ✅ |
| 110 | Kristal Kapı 10 | ✔ | 6×9 | 1/1 | 11 ayna (11 döner), 5 duvar | 4 | 2048 | 4 | ✅ |
| 111 | Yankı Odası 1 | ✔ | 6×9 | 1/1 | 9 ayna (8 döner), 3 duvar | 3 | 256 | 2 | ✅ |
| 112 | Yankı Odası 2 | ✔ | 6×9 | 1/1 | 9 ayna (8 döner), 3 duvar | 4 | 256 | 1 | ✅ |
| 113 | Yankı Odası 3 | ✔ | 6×9 | 1/1 | 9 ayna (8 döner), 3 duvar | 4 | 256 | 1 | ✅ |
| 114 | Yankı Odası 4 | ✔ | 6×9 | 1/1 | 10 ayna (8 döner), 3 duvar | 5 | 256 | 1 | ✅ |
| 115 | Yankı Odası 5 | ✔ | 6×9 | 1/1 | 11 ayna (10 döner), 4 duvar | 4 | 1024 | 5 | ✅ |
| 116 | Yankı Odası 6 | ✔ | 6×9 | 1/1 | 11 ayna (10 döner), 4 duvar | 5 | 1024 | 1 | ✅ |
| 117 | Yankı Odası 7 | ✔ | 6×9 | 1/1 | 11 ayna (10 döner), 4 duvar | 5 | 1024 | 1 | ✅ |
| 118 | Yankı Odası 8 | ✔ | 6×9 | 1/1 | 12 ayna (11 döner), 4 duvar | 6 | 2048 | 3 | ✅ |
| 119 | Yankı Odası 9 | ✔ | 6×9 | 1/1 | 13 ayna (13 döner), 5 duvar | 5 | 8192 | 6 | ✅ |
| 120 | Yankı Odası 10 | ✔ | 6×9 | 1/1 | 14 ayna (13 döner), 5 duvar | 6 | 8192 | 2 | ✅ |
| 121 | Taş Koridor 1 | ✔ | 6×10 | 1/1 | 11 ayna (10 döner), 8 duvar | 5 | 1024 | 3 | ✅ |
| 122 | Taş Koridor 2 | ✔ | 6×10 | 1/1 | 10 ayna (8 döner), 9 duvar | 4 | 256 | 1 | ✅ |
| 123 | Taş Koridor 3 | ✔ | 6×10 | 1/1 | 11 ayna (10 döner), 10 duvar | 3 | 1024 | 5 | ✅ |
| 124 | Taş Koridor 4 | ✔ | 6×10 | 1/1 | 13 ayna (10 döner), 11 duvar | 6 | 1024 | 2 | ✅ |
| 125 | Taş Koridor 5 | ✔ | 6×10 | 1/1 | 12 ayna (10 döner), 12 duvar | 4 | 1024 | 4 | ✅ |
| 126 | Taş Koridor 6 | ✔ | 7×10 | 1/1 | 13 ayna (10 döner), 13 duvar | 5 | 1024 | 1 | ✅ |
| 127 | Taş Koridor 7 | ✔ | 7×10 | 1/1 | 14 ayna (11 döner), 14 duvar | 6 | 2048 | 1 | ✅ |
| 128 | Taş Koridor 8 | ✔ | 7×10 | 1/1 | 14 ayna (11 döner), 15 duvar | 6 | 2048 | 1 | ✅ |
| 129 | Taş Koridor 9 | ✔ | 7×10 | 1/1 | 14 ayna (11 döner), 16 duvar | 5 | 2048 | 1 | ✅ |
| 130 | Taş Koridor 10 | ✔ | 7×10 | 1/1 | 16 ayna (12 döner), 17 duvar | 6 | 4096 | 1 | ✅ |
| 131 | Renkli Damlalar 1 | ✔ | 7×10 | 2/2 | 10 ayna (7 döner), 4 duvar, renkli ışık | 5 | 128 | 1 | ✅ |
| 132 | Renkli Damlalar 2 | ✔ | 7×10 | 2/2 | 10 ayna (8 döner), 4 duvar, renkli ışık | 5 | 256 | 1 | ✅ |
| 133 | Renkli Damlalar 3 | ✔ | 7×10 | 2/2 | 10 ayna (8 döner), 5 duvar, renkli ışık | 5 | 256 | 1 | ✅ |
| 134 | Renkli Damlalar 4 | ✔ | 7×10 | 2/2 | 10 ayna (9 döner), 5 duvar, renkli ışık | 5 | 512 | 1 | ✅ |
| 135 | Renkli Damlalar 5 | ✔ | 7×10 | 2/2 | 11 ayna (8 döner), 6 duvar, renkli ışık | 5 | 256 | 1 | ✅ |
| 136 | Renkli Damlalar 6 | ✔ | 7×10 | 2/1 | 13 ayna (10 döner), 6 duvar, renkli ışık | 7 | 1024 | 1 | ✅ |
| 137 | Renkli Damlalar 7 | ✔ | 7×10 | 2/1 | 12 ayna (9 döner), 7 duvar, renkli ışık | 5 | 512 | 2 | ✅ |
| 138 | Renkli Damlalar 8 | ✔ | 7×10 | 3/2 | 18 ayna (12 döner), 7 duvar, renkli ışık | 8 | 4096 | 2 | ✅ |
| 139 | Renkli Damlalar 9 | ✔ | 7×10 | 3/2 | 15 ayna (12 döner), 8 duvar, renkli ışık | 5 | 4096 | 6 | ✅ |
| 140 | Renkli Damlalar 10 | ✔ | 7×10 | 3/2 | 14 ayna (9 döner), 8 duvar, renkli ışık | 7 | 512 | 1 | ✅ |
| 141 | Cam Süzgeçler 1 | ✔ | 7×10 | 1/1 | 9 ayna (7 döner), 4 duvar, 2 filtre | 4 | 128 | 1 | ✅ |
| 142 | Cam Süzgeçler 2 | ✔ | 7×10 | 1/1 | 9 ayna (8 döner), 4 duvar, 2 filtre | 3 | 256 | 2 | ✅ |
| 143 | Cam Süzgeçler 3 | ✔ | 7×10 | 1/1 | 9 ayna (8 döner), 5 duvar, 2 filtre | 3 | 256 | 2 | ✅ |
| 144 | Cam Süzgeçler 4 | ✔ | 7×10 | 1/1 | 10 ayna (9 döner), 5 duvar, 2 filtre | 5 | 512 | 1 | ✅ |
| 145 | Cam Süzgeçler 5 | ✔ | 7×10 | 2/2 | 12 ayna (10 döner), 6 duvar, 4 filtre | 7 | 1024 | 1 | ✅ |
| 146 | Cam Süzgeçler 6 | ✔ | 7×10 | 2/2 | 13 ayna (9 döner), 6 duvar, 4 filtre | 7 | 512 | 1 | ✅ |
| 147 | Cam Süzgeçler 7 | ✔ | 7×10 | 2/2 | 12 ayna (10 döner), 7 duvar, 4 filtre | 6 | 1024 | 1 | ✅ |
| 148 | Cam Süzgeçler 8 | ✔ | 7×10 | 2/2 | 12 ayna (9 döner), 7 duvar, 4 filtre | 6 | 512 | 1 | ✅ |
| 149 | Cam Süzgeçler 9 | ✔ | 7×10 | 2/2 | 13 ayna (11 döner), 8 duvar, 5 filtre | 7 | 2048 | 1 | ✅ |
| 150 | Cam Süzgeçler 10 | ✔ | 7×10 | 2/2 | 12 ayna (10 döner), 8 duvar, 5 filtre | 6 | 1024 | 1 | ✅ |
| 151 | Gökkuşağı Kuyusu 1 | ✔ | 7×11 | 1/3 | 8 ayna (6 döner), 4 duvar, 1 prizma | 4 | 64 | 1 | ✅ |
| 152 | Gökkuşağı Kuyusu 2 | ✔ | 7×11 | 1/3 | 8 ayna (6 döner), 4 duvar, 1 prizma | 3 | 64 | 2 | ✅ |
| 153 | Gökkuşağı Kuyusu 3 | ✔ | 7×11 | 1/3 | 9 ayna (8 döner), 5 duvar, 1 prizma | 5 | 256 | 1 | ✅ |
| 154 | Gökkuşağı Kuyusu 4 | ✔ | 7×11 | 1/2 | 9 ayna (7 döner), 5 duvar, 1 filtre, 1 prizma | 5 | 128 | 1 | ✅ |
| 155 | Gökkuşağı Kuyusu 5 | ✔ | 7×11 | 1/3 | 10 ayna (8 döner), 6 duvar, 1 prizma | 5 | 256 | 1 | ✅ |
| 156 | Gökkuşağı Kuyusu 6 | ✔ | 7×11 | 1/3 | 12 ayna (9 döner), 6 duvar, 1 prizma | 5 | 512 | 3 | ✅ |
| 157 | Gökkuşağı Kuyusu 7 | ✔ | 7×11 | 1/2 | 11 ayna (9 döner), 7 duvar, 1 filtre, 1 prizma | 6 | 512 | 1 | ✅ |
| 158 | Gökkuşağı Kuyusu 8 | ✔ | 7×11 | 1/3 | 10 ayna (7 döner), 7 duvar, 1 prizma | 5 | 128 | 1 | ✅ |
| 159 | Gökkuşağı Kuyusu 9 | ✔ | 7×11 | 1/2 | 11 ayna (9 döner), 8 duvar, 1 filtre, 1 prizma | 5 | 512 | 1 | ✅ |
| 160 | Gökkuşağı Kuyusu 10 | ✔ | 7×11 | 1/3 | 12 ayna (8 döner), 8 duvar, 1 prizma | 6 | 256 | 1 | ✅ |
| 161 | Çifte Parıltı 1 | ✔ | 7×11 | 2/2 | 11 ayna (8 döner), 4 duvar | 5 | 256 | 2 | ✅ |
| 162 | Çifte Parıltı 2 | ✔ | 7×11 | 2/2 | 10 ayna (7 döner), 4 duvar | 4 | 128 | 2 | ✅ |
| 163 | Çifte Parıltı 3 | ✔ | 7×11 | 2/2 | 10 ayna (7 döner), 5 duvar | 5 | 128 | 1 | ✅ |
| 164 | Çifte Parıltı 4 | ✔ | 7×11 | 2/2 | 11 ayna (10 döner), 5 duvar | 5 | 1024 | 2 | ✅ |
| 165 | Çifte Parıltı 5 | ✔ | 7×11 | 3/3 | 12 ayna (8 döner), 6 duvar | 6 | 256 | 2 | ✅ |
| 166 | Çifte Parıltı 6 | ✔ | 7×11 | 3/3 | 14 ayna (12 döner), 6 duvar | 5 | 4096 | 4 | ✅ |
| 167 | Çifte Parıltı 7 | ✔ | 7×11 | 3/5 | 13 ayna (8 döner), 7 duvar, 1 prizma | 6 | 256 | 2 | ✅ |
| 168 | Çifte Parıltı 8 | ✔ | 7×11 | 3/5 | 16 ayna (9 döner), 7 duvar, 1 prizma | 6 | 512 | 3 | ✅ |
| 169 | Çifte Parıltı 9 | ✔ | 7×11 | 3/5 | 16 ayna (13 döner), 8 duvar, 1 prizma, renkli ışık | 8 | 8192 | 2 | ✅ |
| 170 | Çifte Parıltı 10 | ✔ | 7×11 | 3/5 | 14 ayna (10 döner), 8 duvar, 1 prizma, renkli ışık | 8 | 1024 | 1 | ✅ |
| 171 | Kayan Kayalar 1 | ✔ | 7×10 | 1/1 | 7 ayna (7 döner), 10 duvar, 1 blok | 4 | 6016 | 3 | ✅ |
| 172 | Kayan Kayalar 2 | ✔ | 7×10 | 1/1 | 9 ayna (7 döner), 10 duvar, 1 blok | 5 | 6272 | 2 | ✅ |
| 173 | Kayan Kayalar 3 | ✔ | 7×10 | 1/1 | 7 ayna (5 döner), 11 duvar, 1 blok | 4 | 1408 | 1 | ✅ |
| 174 | Kayan Kayalar 4 | ✔ | 7×10 | 1/1 | 7 ayna (5 döner), 11 duvar, 1 blok | 4 | 224 | 1 | ✅ |
| 175 | Kayan Kayalar 5 | ✔ | 7×10 | 1/1 | 6 ayna (5 döner), 12 duvar, 2 blok | 8 | 36096 | 1 | ✅ |
| 176 | Kayan Kayalar 6 | ✔ | 7×10 | 2/2 | 9 ayna (7 döner), 12 duvar, 2 blok | 6 | 55680 | 1 | ✅ |
| 177 | Kayan Kayalar 7 | ✔ | 7×10 | 2/2 | 9 ayna (7 döner), 13 duvar, 2 blok | 7 | 38400 | 1 | ✅ |
| 178 | Kayan Kayalar 8 | ✔ | 7×10 | 2/2 | 9 ayna (7 döner), 13 duvar, 2 blok | 8 | 1280 | 1 | ✅ |
| 179 | Kayan Kayalar 9 | ✔ | 7×10 | 2/2 | 10 ayna (8 döner), 14 duvar, 2 blok | 7 | 74240 | 1 | ✅ |
| 180 | Kayan Kayalar 10 | ✔ | 7×10 | 2/2 | 10 ayna (8 döner), 14 duvar, 2 blok | 8 | 35328 | 1 | ✅ |
| 181 | Mühürlü Geçit 1 | ✔ | 7×10 | 2/2 | 10 ayna (6 döner), 5 duvar, 1 kapı | 4 | 64 | 2 | ✅ |
| 182 | Mühürlü Geçit 2 | ✔ | 7×10 | 2/2 | 11 ayna (7 döner), 5 duvar, 1 kapı | 5 | 128 | 2 | ✅ |
| 183 | Mühürlü Geçit 3 | ✔ | 7×10 | 2/2 | 10 ayna (6 döner), 6 duvar, 1 kapı, renkli ışık | 5 | 64 | 1 | ✅ |
| 184 | Mühürlü Geçit 4 | ✔ | 7×10 | 2/2 | 11 ayna (8 döner), 6 duvar, 1 kapı, renkli ışık | 6 | 256 | 1 | ✅ |
| 185 | Mühürlü Geçit 5 | ✔ | 7×10 | 2/2 | 11 ayna (7 döner), 7 duvar, 1 kapı | 5 | 128 | 2 | ✅ |
| 186 | Mühürlü Geçit 6 | ✔ | 7×10 | 2/2 | 13 ayna (8 döner), 7 duvar, 1 kapı, renkli ışık | 7 | 256 | 1 | ✅ |
| 187 | Mühürlü Geçit 7 | ✔ | 7×10 | 3/3 | 14 ayna (9 döner), 8 duvar, 2 kapı | 7 | 512 | 2 | ✅ |
| 188 | Mühürlü Geçit 8 | ✔ | 7×10 | 3/3 | 13 ayna (8 döner), 8 duvar, 2 kapı, renkli ışık | 6 | 256 | 2 | ✅ |
| 189 | Mühürlü Geçit 9 | ✔ | 7×10 | 3/3 | 15 ayna (10 döner), 9 duvar, 2 kapı | 9 | 1024 | 1 | ✅ |
| 190 | Mühürlü Geçit 10 | ✔ | 7×10 | 3/3 | 14 ayna (10 döner), 9 duvar, 2 kapı, renkli ışık | 8 | 1024 | 1 | ✅ |
| 191 | Mağaranın Kalbi 1 | ✔ | 7×11 | 2/2 | 12 ayna (8 döner), 6 duvar, 1 filtre, 1 blok | 8 | 12800 | 1 | ✅ |
| 192 | Mağaranın Kalbi 2 | ✔ | 7×11 | 2/4 | 12 ayna (9 döner), 6 duvar, 1 prizma, 1 kapı | 6 | 512 | 2 | ✅ |
| 193 | Mağaranın Kalbi 3 | ✔ | 7×11 | 2/1 | 11 ayna (8 döner), 7 duvar, 1 blok, renkli ışık | 7 | 12544 | 1 | ✅ |
| 194 | Mağaranın Kalbi 4 | ✔ | 7×11 | 2/2 | 12 ayna (8 döner), 7 duvar, 2 filtre, 1 kapı | 7 | 256 | 1 | ✅ |
| 195 | Mağaranın Kalbi 5 | ✔ | 7×11 | 1/3 | 10 ayna (8 döner), 8 duvar, 1 filtre, 1 prizma, 1 blok | 6 | 10752 | 1 | ✅ |
| 196 | Mağaranın Kalbi 6 | ✔ | 7×11 | 3/2 | 14 ayna (10 döner), 8 duvar, 1 kapı, renkli ışık | 6 | 1024 | 4 | ✅ |
| 197 | Mağaranın Kalbi 7 | ✔ | 7×11 | 2/2 | 11 ayna (9 döner), 9 duvar, 1 filtre, 1 blok, 1 kapı | 8 | 21504 | 1 | ✅ |
| 198 | Mağaranın Kalbi 8 | ✔ | 7×11 | 2/3 | 13 ayna (9 döner), 9 duvar, 1 filtre, 1 prizma, 1 kapı | 8 | 512 | 1 | ✅ |
| 199 | Mağaranın Kalbi 9 | ✔ | 7×11 | 3/2 | 14 ayna (9 döner), 10 duvar, 1 blok, 1 kapı, renkli ışık | 8 | 16896 | 2 | ✅ |
| 200 | Mağaranın Kalbi 10 | ✔ | 7×11 | 3/5 | 15 ayna (11 döner), 10 duvar, 1 prizma, 1 blok, 1 kapı | 11 | 28672 | 2 | ✅ |
| 201 | Ay Işığı 1 | ✔ | 6×8 | 1/1 | 9 ayna (7 döner), 4 duvar, 2 filtre | 3 | 128 | 1 | ✅ |
| 202 | Ay Işığı 2 | ✔ | 6×8 | 1/1 | 9 ayna (7 döner), 4 duvar, 1 blok | 3 | 3712 | 2 | ✅ |
| 203 | Ay Işığı 3 | ✔ | 6×8 | 2/2 | 11 ayna (9 döner), 4 duvar, 1 kapı | 4 | 512 | 2 | ✅ |
| 204 | Ay Işığı 4 | ✔ | 6×8 | 2/2 | 11 ayna (7 döner), 5 duvar | 5 | 128 | 1 | ✅ |
| 205 | Ay Işığı 5 | ✔ | 7×9 | 1/1 | 11 ayna (9 döner), 5 duvar, 2 filtre | 4 | 512 | 1 | ✅ |
| 206 | Ay Işığı 6 | ✔ | 7×9 | 1/1 | 10 ayna (8 döner), 5 duvar, 1 blok | 4 | 10240 | 1 | ✅ |
| 207 | Ay Işığı 7 | ✔ | 7×9 | 2/2 | 12 ayna (8 döner), 6 duvar, 1 kapı | 5 | 256 | 1 | ✅ |
| 208 | Ay Işığı 8 | ✔ | 7×9 | 2/2 | 15 ayna (12 döner), 6 duvar | 7 | 4096 | 1 | ✅ |
| 209 | Ay Işığı 9 | ✔ | 7×9 | 1/1 | 12 ayna (10 döner), 6 duvar, 2 filtre | 3 | 1024 | 2 | ✅ |
| 210 | Ay Işığı 10 | ✔ | 7×9 | 1/1 | 11 ayna (10 döner), 7 duvar, 1 blok | 3 | 31744 | 4 | ✅ |
| 211 | Sarmaşık Yolu 1 | ✔ | 7×9 | 1/1 | 10 ayna (8 döner), 5 duvar, 1 blok | 4 | 11520 | 1 | ✅ |
| 212 | Sarmaşık Yolu 2 | ✔ | 7×9 | 2/2 | 16 ayna (11 döner), 5 duvar, 1 kapı | 6 | 2048 | 2 | ✅ |
| 213 | Sarmaşık Yolu 3 | ✔ | 7×9 | 2/2 | 16 ayna (12 döner), 5 duvar | 6 | 4096 | 2 | ✅ |
| 214 | Sarmaşık Yolu 4 | ✔ | 7×9 | 1/1 | 13 ayna (11 döner), 5 duvar, 2 filtre | 6 | 2048 | 1 | ✅ |
| 215 | Sarmaşık Yolu 5 | ✔ | 7×9 | 1/1 | 12 ayna (9 döner), 6 duvar, 1 blok | 5 | 3072 | 1 | ✅ |
| 216 | Sarmaşık Yolu 6 | ✔ | 7×9 | 2/2 | 16 ayna (9 döner), 6 duvar, 1 kapı | 7 | 512 | 2 | ✅ |
| 217 | Sarmaşık Yolu 7 | ✔ | 7×9 | 2/2 | 15 ayna (13 döner), 6 duvar | 7 | 8192 | 1 | ✅ |
| 218 | Sarmaşık Yolu 8 | ✔ | 7×9 | 1/1 | 15 ayna (12 döner), 6 duvar, 2 filtre | 7 | 4096 | 1 | ✅ |
| 219 | Sarmaşık Yolu 9 | ✔ | 7×9 | 1/1 | 13 ayna (11 döner), 7 duvar, 1 blok | 6 | 55296 | 1 | ✅ |
| 220 | Sarmaşık Yolu 10 | ✔ | 7×9 | 2/2 | 18 ayna (15 döner), 7 duvar, 1 kapı | 8 | 32768 | 2 | ✅ |
| 221 | Gölge Çitler 1 | ✔ | 7×10 | 2/2 | 16 ayna (11 döner), 10 duvar, 1 kapı | 7 | 2048 | 2 | ✅ |
| 222 | Gölge Çitler 2 | ✔ | 7×10 | 2/2 | 16 ayna (10 döner), 11 duvar | 5 | 1024 | 3 | ✅ |
| 223 | Gölge Çitler 3 | ✔ | 7×10 | 1/1 | 13 ayna (8 döner), 12 duvar, 2 filtre | 5 | 256 | 1 | ✅ |
| 224 | Gölge Çitler 4 | ✔ | 7×10 | 1/1 | 13 ayna (10 döner), 13 duvar, 1 blok | 7 | 13312 | 1 | ✅ |
| 225 | Gölge Çitler 5 | ✔ | 7×10 | 2/2 | 20 ayna (10 döner), 14 duvar, 1 kapı | 6 | 1024 | 3 | ✅ |
| 226 | Gölge Çitler 6 | ✔ | 8×10 | 2/2 | 19 ayna (11 döner), 15 duvar | 6 | 2048 | 2 | ✅ |
| 227 | Gölge Çitler 7 | ✔ | 8×10 | 1/1 | 17 ayna (13 döner), 16 duvar, 2 filtre | 7 | 8192 | 1 | ✅ |
| 228 | Gölge Çitler 8 | ✔ | 8×10 | 1/1 | 15 ayna (11 döner), 17 duvar, 1 blok | 6 | 38912 | 1 | ✅ |
| 229 | Gölge Çitler 9 | ✔ | 8×10 | 2/2 | 21 ayna (13 döner), 18 duvar, 1 kapı | 8 | 8192 | 2 | ✅ |
| 230 | Gölge Çitler 10 | ✔ | 8×10 | 2/2 | 20 ayna (13 döner), 19 duvar | 8 | 8192 | 2 | ✅ |
| 231 | Çiçek Renkleri 1 | ✔ | 8×10 | 3/3 | 15 ayna (9 döner), 6 duvar, renkli ışık | 6 | 512 | 2 | ✅ |
| 232 | Çiçek Renkleri 2 | ✔ | 8×10 | 2/2 | 12 ayna (9 döner), 6 duvar, 1 filtre, renkli ışık | 5 | 512 | 1 | ✅ |
| 233 | Çiçek Renkleri 3 | ✔ | 8×10 | 2/2 | 12 ayna (9 döner), 7 duvar, 1 blok, renkli ışık | 6 | 28160 | 1 | ✅ |
| 234 | Çiçek Renkleri 4 | ✔ | 8×10 | 2/2 | 12 ayna (8 döner), 7 duvar, 1 kapı, renkli ışık | 5 | 256 | 1 | ✅ |
| 235 | Çiçek Renkleri 5 | ✔ | 8×10 | 3/3 | 16 ayna (11 döner), 8 duvar, renkli ışık | 7 | 2048 | 2 | ✅ |
| 236 | Çiçek Renkleri 6 | ✔ | 8×10 | 2/1 | 16 ayna (11 döner), 8 duvar, 1 filtre, renkli ışık | 6 | 2048 | 2 | ✅ |
| 237 | Çiçek Renkleri 7 | ✔ | 8×10 | 2/1 | 13 ayna (9 döner), 9 duvar, 1 blok, renkli ışık | 7 | 24064 | 1 | ✅ |
| 238 | Çiçek Renkleri 8 | ✔ | 8×10 | 3/2 | 19 ayna (16 döner), 9 duvar, 1 kapı, renkli ışık | 10 | 65536 | 1 | ✅ |
| 239 | Çiçek Renkleri 9 | ✔ | 8×10 | 3/2 | 20 ayna (14 döner), 10 duvar, 1 kapı, renkli ışık | 10 | 16384 | 2 | ✅ |
| 240 | Çiçek Renkleri 10 | ✔ | 8×10 | 3/2 | 21 ayna (11 döner), 10 duvar, 1 filtre, renkli ışık | 8 | 2048 | 2 | ✅ |
| 241 | Sisli Cam 1 | ✔ | 8×10 | 1/1 | 12 ayna (12 döner), 6 duvar, 3 filtre | 5 | 4096 | 3 | ✅ |
| 242 | Sisli Cam 2 | ✔ | 8×10 | 1/1 | 10 ayna (8 döner), 6 duvar, 2 filtre, 1 blok | 4 | 15104 | 1 | ✅ |
| 243 | Sisli Cam 3 | ✔ | 8×10 | 2/2 | 14 ayna (10 döner), 7 duvar, 2 filtre, 1 kapı | 5 | 1024 | 2 | ✅ |
| 244 | Sisli Cam 4 | ✔ | 8×10 | 2/2 | 14 ayna (11 döner), 7 duvar, 2 filtre | 7 | 2048 | 1 | ✅ |
| 245 | Sisli Cam 5 | ✔ | 8×10 | 2/2 | 14 ayna (11 döner), 8 duvar, 5 filtre | 6 | 2048 | 2 | ✅ |
| 246 | Sisli Cam 6 | ✔ | 8×10 | 2/2 | 14 ayna (9 döner), 8 duvar, 4 filtre, 1 blok | 6 | 7680 | 2 | ✅ |
| 247 | Sisli Cam 7 | ✔ | 8×10 | 2/2 | 14 ayna (11 döner), 9 duvar, 4 filtre, 1 kapı | 6 | 2048 | 1 | ✅ |
| 248 | Sisli Cam 8 | ✔ | 8×10 | 3/3 | 16 ayna (10 döner), 9 duvar, 4 filtre | 8 | 1024 | 1 | ✅ |
| 249 | Sisli Cam 9 | ✔ | 8×10 | 2/2 | 15 ayna (10 döner), 10 duvar, 6 filtre | 7 | 1024 | 1 | ✅ |
| 250 | Sisli Cam 10 | ✔ | 8×10 | 2/2 | 15 ayna (13 döner), 10 duvar, 5 filtre, 1 blok | 10 | 81920 | 2 | ✅ |
| 251 | Çiy Prizması 1 | ✔ | 8×11 | 1/3 | 10 ayna (7 döner), 6 duvar, 1 prizma, 1 blok | 5 | 8320 | 1 | ✅ |
| 252 | Çiy Prizması 2 | ✔ | 8×11 | 2/4 | 11 ayna (8 döner), 6 duvar, 1 prizma, 1 kapı | 5 | 256 | 1 | ✅ |
| 253 | Çiy Prizması 3 | ✔ | 8×11 | 2/4 | 12 ayna (8 döner), 7 duvar, 1 prizma | 5 | 256 | 2 | ✅ |
| 254 | Çiy Prizması 4 | ✔ | 8×11 | 1/2 | 12 ayna (9 döner), 7 duvar, 2 filtre, 1 prizma | 6 | 512 | 1 | ✅ |
| 255 | Çiy Prizması 5 | ✔ | 8×11 | 1/3 | 13 ayna (8 döner), 8 duvar, 1 prizma, 1 blok | 7 | 15104 | 1 | ✅ |
| 256 | Çiy Prizması 6 | ✔ | 8×11 | 2/4 | 14 ayna (9 döner), 8 duvar, 1 prizma, 1 kapı | 5 | 512 | 3 | ✅ |
| 257 | Çiy Prizması 7 | ✔ | 8×11 | 2/3 | 14 ayna (8 döner), 9 duvar, 1 filtre, 1 prizma | 6 | 256 | 2 | ✅ |
| 258 | Çiy Prizması 8 | ✔ | 8×11 | 1/3 | 15 ayna (9 döner), 9 duvar, 1 filtre, 1 prizma | 7 | 512 | 2 | ✅ |
| 259 | Çiy Prizması 9 | ✔ | 8×11 | 1/2 | 13 ayna (10 döner), 10 duvar, 1 filtre, 1 prizma, 1 blok | 6 | 7168 | 1 | ✅ |
| 260 | Çiy Prizması 10 | ✔ | 8×11 | 2/4 | 18 ayna (16 döner), 10 duvar, 1 prizma, 1 kapı | 10 | 65536 | 1 | ✅ |
| 261 | Ateşböcekleri 1 | ✔ | 8×11 | 2/2 | 14 ayna (9 döner), 6 duvar, 1 kapı | 7 | 512 | 1 | ✅ |
| 262 | Ateşböcekleri 2 | ✔ | 8×11 | 3/3 | 15 ayna (9 döner), 6 duvar | 8 | 512 | 1 | ✅ |
| 263 | Ateşböcekleri 3 | ✔ | 8×11 | 2/2 | 13 ayna (10 döner), 7 duvar, 2 filtre | 6 | 1024 | 1 | ✅ |
| 264 | Ateşböcekleri 4 | ✔ | 8×11 | 2/2 | 13 ayna (7 döner), 7 duvar, 1 blok | 7 | 7680 | 1 | ✅ |
| 265 | Ateşböcekleri 5 | ✔ | 8×11 | 3/3 | 17 ayna (12 döner), 8 duvar, 1 kapı | 10 | 4096 | 1 | ✅ |
| 266 | Ateşböcekleri 6 | ✔ | 8×11 | 3/3 | 16 ayna (9 döner), 8 duvar, 1 kapı | 7 | 512 | 1 | ✅ |
| 267 | Ateşböcekleri 7 | ✔ | 8×11 | 3/5 | 18 ayna (10 döner), 9 duvar, 1 filtre, 1 prizma | 8 | 1024 | 2 | ✅ |
| 268 | Ateşböcekleri 8 | ✔ | 8×11 | 3/5 | 18 ayna (12 döner), 9 duvar, 1 prizma, 1 blok | 8 | 73728 | 2 | ✅ |
| 269 | Ateşböcekleri 9 | ✔ | 8×11 | 3/5 | 18 ayna (13 döner), 10 duvar, 1 prizma, 1 kapı, renkli ışık | 8 | 8192 | 2 | ✅ |
| 270 | Ateşböcekleri 10 | ✔ | 8×11 | 3/5 | 20 ayna (13 döner), 10 duvar, 1 prizma, 1 kapı, renkli ışık | 9 | 8192 | 2 | ✅ |
| 271 | Bahçe Taşları 1 | ✔ | 8×10 | 2/2 | 12 ayna (9 döner), 12 duvar, 1 blok | 7 | 22528 | 1 | ✅ |
| 272 | Bahçe Taşları 2 | ✔ | 8×10 | 1/1 | 9 ayna (7 döner), 12 duvar, 2 filtre, 1 blok | 4 | 5248 | 1 | ✅ |
| 273 | Bahçe Taşları 3 | ✔ | 8×10 | 1/1 | 9 ayna (7 döner), 13 duvar, 1 blok | 4 | 7040 | 1 | ✅ |
| 274 | Bahçe Taşları 4 | ✔ | 8×10 | 2/2 | 13 ayna (9 döner), 13 duvar, 1 blok, 1 kapı | 7 | 7680 | 2 | ✅ |
| 275 | Bahçe Taşları 5 | ✔ | 8×10 | 2/2 | 12 ayna (9 döner), 14 duvar, 2 blok | 9 | 86016 | 1 | ✅ |
| 276 | Bahçe Taşları 6 | ✔ | 8×10 | 2/2 | 12 ayna (8 döner), 14 duvar, 2 filtre, 2 blok | 6 | 53760 | 1 | ✅ |
| 277 | Bahçe Taşları 7 | ✔ | 8×10 | 2/2 | 13 ayna (8 döner), 15 duvar, 2 blok | 9 | 76800 | 1 | ✅ |
| 278 | Bahçe Taşları 8 | ✔ | 8×10 | 2/2 | 13 ayna (9 döner), 15 duvar, 2 blok, 1 kapı | 11 | 69632 | 2 | ✅ |
| 279 | Bahçe Taşları 9 | ✔ | 8×10 | 3/3 | 16 ayna (8 döner), 16 duvar, 2 blok | 9 | 64768 | 1 | ✅ |
| 280 | Bahçe Taşları 10 | ✔ | 8×10 | 2/2 | 14 ayna (9 döner), 16 duvar, 2 filtre, 2 blok | 8 | 87552 | 1 | ✅ |
| 281 | Gizli Kapılar 1 | ✔ | 8×10 | 2/2 | 13 ayna (8 döner), 7 duvar, 2 filtre, 1 kapı | 6 | 256 | 1 | ✅ |
| 282 | Gizli Kapılar 2 | ✔ | 8×10 | 2/2 | 13 ayna (9 döner), 7 duvar, 1 blok, 1 kapı | 6 | 23552 | 2 | ✅ |
| 283 | Gizli Kapılar 3 | ✔ | 8×10 | 2/2 | 13 ayna (7 döner), 8 duvar, 1 kapı, renkli ışık | 5 | 128 | 2 | ✅ |
| 284 | Gizli Kapılar 4 | ✔ | 8×10 | 3/3 | 16 ayna (12 döner), 8 duvar, 1 kapı, renkli ışık | 8 | 4096 | 2 | ✅ |
| 285 | Gizli Kapılar 5 | ✔ | 8×10 | 2/2 | 13 ayna (9 döner), 9 duvar, 2 filtre, 1 kapı | 5 | 512 | 2 | ✅ |
| 286 | Gizli Kapılar 6 | ✔ | 8×10 | 2/2 | 13 ayna (8 döner), 9 duvar, 1 blok, 1 kapı, renkli ışık | 6 | 1536 | 1 | ✅ |
| 287 | Gizli Kapılar 7 | ✔ | 8×10 | 3/3 | 18 ayna (15 döner), 10 duvar, 2 kapı | 8 | 32768 | 5 | ✅ |
| 288 | Gizli Kapılar 8 | ✔ | 8×10 | 3/3 | 18 ayna (11 döner), 10 duvar, 2 kapı, renkli ışık | 8 | 2048 | 2 | ✅ |
| 289 | Gizli Kapılar 9 | ✔ | 8×10 | 3/3 | 18 ayna (14 döner), 11 duvar, 2 filtre, 2 kapı | 9 | 16384 | 2 | ✅ |
| 290 | Gizli Kapılar 10 | ✔ | 8×10 | 3/3 | 17 ayna (9 döner), 11 duvar, 1 blok, 2 kapı, renkli ışık | 10 | 9728 | 2 | ✅ |
| 291 | Gece Yarısı 1 | ✔ | 8×11 | 2/2 | 14 ayna (10 döner), 8 duvar, 1 filtre, 1 blok | 8 | 33792 | 1 | ✅ |
| 292 | Gece Yarısı 2 | ✔ | 8×11 | 2/4 | 13 ayna (8 döner), 8 duvar, 1 prizma, 1 kapı | 6 | 256 | 1 | ✅ |
| 293 | Gece Yarısı 3 | ✔ | 8×11 | 3/2 | 19 ayna (8 döner), 9 duvar, 1 blok, renkli ışık | 7 | 9728 | 2 | ✅ |
| 294 | Gece Yarısı 4 | ✔ | 8×11 | 2/2 | 14 ayna (11 döner), 9 duvar, 3 filtre, 1 kapı | 7 | 2048 | 1 | ✅ |
| 295 | Gece Yarısı 5 | ✔ | 8×11 | 1/3 | 12 ayna (10 döner), 10 duvar, 1 filtre, 1 prizma, 1 blok | 8 | 47104 | 1 | ✅ |
| 296 | Gece Yarısı 6 | ✔ | 8×11 | 3/2 | 18 ayna (11 döner), 10 duvar, 1 kapı, renkli ışık | 8 | 2048 | 3 | ✅ |
| 297 | Gece Yarısı 7 | ✔ | 8×11 | 3/3 | 17 ayna (9 döner), 11 duvar, 1 filtre, 1 blok, 1 kapı | 9 | 1536 | 2 | ✅ |
| 298 | Gece Yarısı 8 | ✔ | 8×11 | 2/3 | 16 ayna (11 döner), 11 duvar, 2 filtre, 1 prizma, 1 kapı | 9 | 2048 | 1 | ✅ |
| 299 | Gece Yarısı 9 | ✔ | 8×11 | 3/2 | 18 ayna (11 döner), 12 duvar, 1 blok, 1 kapı, renkli ışık | 9 | 75776 | 2 | ✅ |
| 300 | Gece Yarısı 10 | ✔ | 8×11 | 3/5 | 17 ayna (8 döner), 12 duvar, 1 prizma, 1 blok, 1 kapı | 12 | 2560 | 2 | ✅ |
| 301 | Yıldız Tozu 1 | ✔ | 6×9 | 1/1 | 11 ayna (9 döner), 6 duvar, 2 filtre | 4 | 512 | 1 | ✅ |
| 302 | Yıldız Tozu 2 | ✔ | 6×9 | 1/1 | 9 ayna (7 döner), 6 duvar, 1 blok | 3 | 4608 | 1 | ✅ |
| 303 | Yıldız Tozu 3 | ✔ | 6×9 | 2/2 | 14 ayna (9 döner), 6 duvar, 1 kapı | 7 | 512 | 1 | ✅ |
| 304 | Yıldız Tozu 4 | ✔ | 6×9 | 2/2 | 14 ayna (10 döner), 7 duvar | 7 | 1024 | 1 | ✅ |
| 305 | Yıldız Tozu 5 | ✔ | 7×10 | 1/1 | 14 ayna (12 döner), 7 duvar, 2 filtre | 5 | 4096 | 4 | ✅ |
| 306 | Yıldız Tozu 6 | ✔ | 7×10 | 1/1 | 12 ayna (11 döner), 7 duvar, 1 blok | 5 | 18432 | 3 | ✅ |
| 307 | Yıldız Tozu 7 | ✔ | 7×10 | 2/2 | 16 ayna (12 döner), 8 duvar, 1 kapı | 6 | 4096 | 3 | ✅ |
| 308 | Yıldız Tozu 8 | ✔ | 7×10 | 2/2 | 17 ayna (11 döner), 8 duvar | 8 | 2048 | 1 | ✅ |
| 309 | Yıldız Tozu 9 | ✔ | 7×10 | 1/1 | 16 ayna (14 döner), 8 duvar, 2 filtre | 7 | 16384 | 1 | ✅ |
| 310 | Yıldız Tozu 10 | ✔ | 7×10 | 1/1 | 13 ayna (11 döner), 9 duvar, 1 blok | 9 | 65536 | 3 | ✅ |
| 311 | Kuyruklu Yıldız 1 | ✔ | 7×10 | 1/1 | 13 ayna (10 döner), 7 duvar, 1 blok | 7 | 5120 | 1 | ✅ |
| 312 | Kuyruklu Yıldız 2 | ✔ | 7×10 | 2/2 | 19 ayna (12 döner), 7 duvar, 1 kapı | 7 | 4096 | 3 | ✅ |
| 313 | Kuyruklu Yıldız 3 | ✔ | 7×10 | 2/2 | 18 ayna (12 döner), 7 duvar | 8 | 4096 | 2 | ✅ |
| 314 | Kuyruklu Yıldız 4 | ✔ | 7×10 | 1/1 | 16 ayna (14 döner), 7 duvar, 2 filtre | 8 | 16384 | 1 | ✅ |
| 315 | Kuyruklu Yıldız 5 | ✔ | 7×10 | 1/1 | 14 ayna (11 döner), 8 duvar, 1 blok | 8 | 51200 | 1 | ✅ |
| 316 | Kuyruklu Yıldız 6 | ✔ | 7×10 | 2/2 | 20 ayna (12 döner), 8 duvar, 1 kapı | 9 | 4096 | 2 | ✅ |
| 317 | Kuyruklu Yıldız 7 | ✔ | 7×10 | 2/2 | 18 ayna (13 döner), 8 duvar | 9 | 8192 | 1 | ✅ |
| 318 | Kuyruklu Yıldız 8 | ✔ | 7×10 | 1/1 | 17 ayna (15 döner), 8 duvar, 2 filtre | 8 | 32768 | 3 | ✅ |
| 319 | Kuyruklu Yıldız 9 | ✔ | 7×10 | 1/1 | 15 ayna (13 döner), 9 duvar, 1 blok | 6 | 73728 | 2 | ✅ |
| 320 | Kuyruklu Yıldız 10 | ✔ | 7×10 | 2/2 | 18 ayna (10 döner), 9 duvar, 1 kapı | 8 | 1024 | 1 | ✅ |
| 321 | Kara Delik 1 | ✔ | 7×11 | 2/2 | 18 ayna (11 döner), 12 duvar, 1 kapı | 7 | 2048 | 2 | ✅ |
| 322 | Kara Delik 2 | ✔ | 7×11 | 2/2 | 18 ayna (9 döner), 13 duvar | 7 | 512 | 2 | ✅ |
| 323 | Kara Delik 3 | ✔ | 7×11 | 1/1 | 16 ayna (12 döner), 14 duvar, 2 filtre | 7 | 4096 | 1 | ✅ |
| 324 | Kara Delik 4 | ✔ | 7×11 | 1/1 | 14 ayna (10 döner), 15 duvar, 1 blok | 7 | 39936 | 1 | ✅ |
| 325 | Kara Delik 5 | ✔ | 7×11 | 2/2 | 18 ayna (11 döner), 16 duvar, 1 kapı | 7 | 2048 | 2 | ✅ |
| 326 | Kara Delik 6 | ✔ | 8×11 | 2/2 | 19 ayna (13 döner), 17 duvar | 8 | 8192 | 1 | ✅ |
| 327 | Kara Delik 7 | ✔ | 8×11 | 1/1 | 20 ayna (16 döner), 18 duvar, 2 filtre | 9 | 65536 | 2 | ✅ |
| 328 | Kara Delik 8 | ✔ | 8×11 | 1/1 | 15 ayna (11 döner), 19 duvar, 1 blok | 6 | 36864 | 1 | ✅ |
| 329 | Kara Delik 9 | ✔ | 8×11 | 2/2 | 22 ayna (11 döner), 20 duvar, 1 kapı | 8 | 2048 | 3 | ✅ |
| 330 | Kara Delik 10 | ✔ | 8×11 | 2/2 | 21 ayna (14 döner), 21 duvar | 9 | 16384 | 1 | ✅ |
| 331 | Nebula 1 | ✔ | 8×11 | 3/3 | 17 ayna (10 döner), 8 duvar, renkli ışık | 8 | 1024 | 2 | ✅ |
| 332 | Nebula 2 | ✔ | 8×11 | 2/2 | 15 ayna (11 döner), 8 duvar, 1 filtre, renkli ışık | 7 | 2048 | 1 | ✅ |
| 333 | Nebula 3 | ✔ | 8×11 | 2/2 | 14 ayna (10 döner), 9 duvar, 1 blok, renkli ışık | 7 | 62464 | 2 | ✅ |
| 334 | Nebula 4 | ✔ | 8×11 | 2/2 | 15 ayna (9 döner), 9 duvar, 1 kapı, renkli ışık | 7 | 512 | 1 | ✅ |
| 335 | Nebula 5 | ✔ | 8×11 | 3/3 | 19 ayna (16 döner), 10 duvar, renkli ışık | 9 | 65536 | 2 | ✅ |
| 336 | Nebula 6 | ✔ | 8×11 | 2/1 | 17 ayna (12 döner), 10 duvar, 1 filtre, renkli ışık | 8 | 4096 | 1 | ✅ |
| 337 | Nebula 7 | ✔ | 8×11 | 2/1 | 15 ayna (10 döner), 11 duvar, 1 blok, renkli ışık | 10 | 38912 | 2 | ✅ |
| 338 | Nebula 8 | ✔ | 8×11 | 3/2 | 20 ayna (13 döner), 11 duvar, 1 kapı, renkli ışık | 9 | 8192 | 2 | ✅ |
| 339 | Nebula 9 | ✔ | 8×11 | 3/2 | 19 ayna (10 döner), 12 duvar, 1 kapı, renkli ışık | 9 | 1024 | 1 | ✅ |
| 340 | Nebula 10 | ✔ | 8×11 | 3/2 | 19 ayna (13 döner), 12 duvar, 1 filtre, renkli ışık | 9 | 8192 | 1 | ✅ |
| 341 | Yıldız Camı 1 | ✔ | 8×11 | 1/1 | 15 ayna (13 döner), 8 duvar, 3 filtre | 7 | 8192 | 1 | ✅ |
| 342 | Yıldız Camı 2 | ✔ | 8×11 | 1/1 | 12 ayna (10 döner), 8 duvar, 2 filtre, 1 blok | 7 | 65536 | 1 | ✅ |
| 343 | Yıldız Camı 3 | ✔ | 8×11 | 2/2 | 15 ayna (13 döner), 9 duvar, 2 filtre, 1 kapı | 7 | 8192 | 1 | ✅ |
| 344 | Yıldız Camı 4 | ✔ | 8×11 | 2/2 | 17 ayna (12 döner), 9 duvar, 2 filtre | 9 | 4096 | 1 | ✅ |
| 345 | Yıldız Camı 5 | ✔ | 8×11 | 2/2 | 16 ayna (13 döner), 10 duvar, 5 filtre | 7 | 8192 | 2 | ✅ |
| 346 | Yıldız Camı 6 | ✔ | 8×11 | 2/2 | 16 ayna (11 döner), 10 duvar, 4 filtre, 1 blok | 8 | 22528 | 2 | ✅ |
| 347 | Yıldız Camı 7 | ✔ | 8×11 | 2/2 | 18 ayna (11 döner), 11 duvar, 4 filtre, 1 kapı | 9 | 2048 | 1 | ✅ |
| 348 | Yıldız Camı 8 | ✔ | 8×11 | 3/3 | 18 ayna (13 döner), 11 duvar, 4 filtre | 8 | 8192 | 2 | ✅ |
| 349 | Yıldız Camı 9 | ✔ | 8×11 | 2/2 | 17 ayna (11 döner), 12 duvar, 6 filtre | 8 | 2048 | 1 | ✅ |
| 350 | Yıldız Camı 10 | ✔ | 8×11 | 2/2 | 15 ayna (12 döner), 12 duvar, 5 filtre, 1 blok | 10 | 94208 | 2 | ✅ |
| 351 | Takımyıldızı 1 | ✔ | 8×11 | 1/3 | 12 ayna (7 döner), 8 duvar, 1 prizma, 1 blok | 6 | 1408 | 1 | ✅ |
| 352 | Takımyıldızı 2 | ✔ | 8×11 | 2/4 | 15 ayna (12 döner), 8 duvar, 1 prizma, 1 kapı | 7 | 4096 | 2 | ✅ |
| 353 | Takımyıldızı 3 | ✔ | 8×11 | 2/4 | 15 ayna (12 döner), 9 duvar, 1 prizma | 8 | 4096 | 1 | ✅ |
| 354 | Takımyıldızı 4 | ✔ | 8×11 | 1/2 | 14 ayna (10 döner), 9 duvar, 2 filtre, 1 prizma | 7 | 1024 | 1 | ✅ |
| 355 | Takımyıldızı 5 | ✔ | 8×11 | 1/3 | 14 ayna (11 döner), 10 duvar, 1 prizma, 1 blok | 8 | 24576 | 2 | ✅ |
| 356 | Takımyıldızı 6 | ✔ | 8×11 | 2/4 | 17 ayna (10 döner), 10 duvar, 1 prizma, 1 kapı | 8 | 1024 | 2 | ✅ |
| 357 | Takımyıldızı 7 | ✔ | 8×11 | 2/3 | 17 ayna (12 döner), 11 duvar, 1 filtre, 1 prizma | 9 | 4096 | 1 | ✅ |
| 358 | Takımyıldızı 8 | ✔ | 8×11 | 1/3 | 16 ayna (9 döner), 11 duvar, 1 filtre, 1 prizma | 8 | 512 | 1 | ✅ |
| 359 | Takımyıldızı 9 | ✔ | 8×11 | 1/2 | 15 ayna (11 döner), 12 duvar, 1 filtre, 1 prizma, 1 blok | 10 | 36864 | 2 | ✅ |
| 360 | Takımyıldızı 10 | ✔ | 8×11 | 2/4 | 20 ayna (13 döner), 12 duvar, 1 prizma, 1 kapı | 10 | 8192 | 2 | ✅ |
| 361 | Çifte Güneş 1 | ✔ | 8×11 | 2/2 | 15 ayna (9 döner), 8 duvar, 1 kapı | 7 | 512 | 1 | ✅ |
| 362 | Çifte Güneş 2 | ✔ | 8×11 | 3/3 | 20 ayna (13 döner), 8 duvar | 10 | 8192 | 2 | ✅ |
| 363 | Çifte Güneş 3 | ✔ | 8×11 | 2/2 | 16 ayna (11 döner), 9 duvar, 2 filtre | 8 | 2048 | 1 | ✅ |
| 364 | Çifte Güneş 4 | ✔ | 8×11 | 2/2 | 15 ayna (9 döner), 9 duvar, 1 blok | 8 | 24576 | 2 | ✅ |
| 365 | Çifte Güneş 5 | ✔ | 8×11 | 3/3 | 19 ayna (11 döner), 10 duvar, 1 kapı | 9 | 2048 | 2 | ✅ |
| 366 | Çifte Güneş 6 | ✔ | 8×11 | 3/3 | 19 ayna (12 döner), 10 duvar, 1 kapı | 10 | 4096 | 1 | ✅ |
| 367 | Çifte Güneş 7 | ✔ | 8×11 | 3/5 | 19 ayna (16 döner), 11 duvar, 1 filtre, 1 prizma | 8 | 65536 | 3 | ✅ |
| 368 | Çifte Güneş 8 | ✔ | 8×11 | 3/5 | 19 ayna (9 döner), 11 duvar, 1 prizma, 1 blok | 10 | 11264 | 3 | ✅ |
| 369 | Çifte Güneş 9 | ✔ | 8×11 | 3/5 | 21 ayna (14 döner), 12 duvar, 1 prizma, 1 kapı, renkli ışık | 10 | 16384 | 2 | ✅ |
| 370 | Çifte Güneş 10 | ✔ | 8×11 | 3/5 | 20 ayna (14 döner), 12 duvar, 1 prizma, 1 kapı, renkli ışık | 10 | 16384 | 2 | ✅ |
| 371 | Gezegen Taşları 1 | ✔ | 8×11 | 2/2 | 14 ayna (7 döner), 14 duvar, 1 blok | 8 | 4352 | 1 | ✅ |
| 372 | Gezegen Taşları 2 | ✔ | 8×11 | 1/1 | 10 ayna (7 döner), 14 duvar, 2 filtre, 1 blok | 5 | 1024 | 1 | ✅ |
| 373 | Gezegen Taşları 3 | ✔ | 8×11 | 1/1 | 11 ayna (8 döner), 15 duvar, 1 blok | 5 | 1536 | 1 | ✅ |
| 374 | Gezegen Taşları 4 | ✔ | 8×11 | 2/2 | 13 ayna (9 döner), 15 duvar, 1 blok, 1 kapı | 7 | 19968 | 1 | ✅ |
| 375 | Gezegen Taşları 5 | ✔ | 8×11 | 2/2 | 14 ayna (8 döner), 16 duvar, 2 blok | 7 | 70656 | 1 | ✅ |
| 376 | Gezegen Taşları 6 | ✔ | 8×11 | 2/2 | 14 ayna (10 döner), 16 duvar, 2 filtre, 2 blok | 8 | 93184 | 2 | ✅ |
| 377 | Gezegen Taşları 7 | ✔ | 8×11 | 2/2 | 14 ayna (7 döner), 17 duvar, 2 blok | 6 | 37120 | 2 | ✅ |
| 378 | Gezegen Taşları 8 | ✔ | 8×11 | 2/2 | 14 ayna (7 döner), 17 duvar, 2 blok, 1 kapı | 6 | 38400 | 2 | ✅ |
| 379 | Gezegen Taşları 9 | ✔ | 8×11 | 3/3 | 16 ayna (5 döner), 18 duvar, 2 blok | 5 | 8096 | 2 | ✅ |
| 380 | Gezegen Taşları 10 | ✔ | 8×11 | 2/2 | 15 ayna (8 döner), 18 duvar, 2 filtre, 2 blok | 9 | 48640 | 2 | ✅ |
| 381 | Kozmik Kilit 1 | ✔ | 8×11 | 2/2 | 15 ayna (9 döner), 9 duvar, 2 filtre, 1 kapı | 6 | 512 | 2 | ✅ |
| 382 | Kozmik Kilit 2 | ✔ | 8×11 | 2/2 | 15 ayna (8 döner), 9 duvar, 1 blok, 1 kapı | 7 | 8704 | 2 | ✅ |
| 383 | Kozmik Kilit 3 | ✔ | 8×11 | 2/2 | 17 ayna (12 döner), 10 duvar, 1 kapı, renkli ışık | 8 | 4096 | 2 | ✅ |
| 384 | Kozmik Kilit 4 | ✔ | 8×11 | 3/3 | 18 ayna (11 döner), 10 duvar, 1 kapı, renkli ışık | 8 | 2048 | 2 | ✅ |
| 385 | Kozmik Kilit 5 | ✔ | 8×11 | 2/2 | 16 ayna (9 döner), 11 duvar, 2 filtre, 1 kapı | 8 | 512 | 1 | ✅ |
| 386 | Kozmik Kilit 6 | ✔ | 8×11 | 2/2 | 15 ayna (11 döner), 11 duvar, 1 blok, 1 kapı, renkli ışık | 9 | 22528 | 2 | ✅ |
| 387 | Kozmik Kilit 7 | ✔ | 8×11 | 3/3 | 20 ayna (14 döner), 12 duvar, 2 kapı | 9 | 16384 | 2 | ✅ |
| 388 | Kozmik Kilit 8 | ✔ | 8×11 | 3/3 | 18 ayna (16 döner), 12 duvar, 2 kapı, renkli ışık | 9 | 65536 | 1 | ✅ |
| 389 | Kozmik Kilit 9 | ✔ | 8×11 | 3/3 | 19 ayna (16 döner), 13 duvar, 2 filtre, 2 kapı | 9 | 65536 | 2 | ✅ |
| 390 | Kozmik Kilit 10 | ✔ | 8×11 | 3/3 | 19 ayna (9 döner), 13 duvar, 1 blok, 2 kapı, renkli ışık | 11 | 5120 | 2 | ✅ |
| 391 | Süpernova 1 | ✔ | 8×11 | 2/2 | 15 ayna (10 döner), 10 duvar, 1 filtre, 1 blok | 9 | 5120 | 2 | ✅ |
| 392 | Süpernova 2 | ✔ | 8×11 | 2/4 | 18 ayna (11 döner), 10 duvar, 1 prizma, 1 kapı | 9 | 2048 | 1 | ✅ |
| 393 | Süpernova 3 | ✔ | 8×11 | 3/2 | 20 ayna (8 döner), 11 duvar, 1 blok, renkli ışık | 10 | 6400 | 2 | ✅ |
| 394 | Süpernova 4 | ✔ | 8×11 | 2/2 | 17 ayna (11 döner), 11 duvar, 3 filtre, 1 kapı | 9 | 2048 | 1 | ✅ |
| 395 | Süpernova 5 | ✔ | 8×11 | 1/3 | 13 ayna (10 döner), 12 duvar, 1 filtre, 1 prizma, 1 blok | 10 | 13312 | 1 | ✅ |
| 396 | Süpernova 6 | ✔ | 8×11 | 3/2 | 18 ayna (15 döner), 12 duvar, 1 kapı, renkli ışık | 9 | 32768 | 2 | ✅ |
| 397 | Süpernova 7 | ✔ | 8×11 | 3/3 | 18 ayna (10 döner), 13 duvar, 1 filtre, 1 blok, 1 kapı | 11 | 39936 | 2 | ✅ |
| 398 | Süpernova 8 | ✔ | 8×11 | 2/3 | 18 ayna (14 döner), 13 duvar, 2 filtre, 1 prizma, 1 kapı | 10 | 16384 | 1 | ✅ |
| 399 | Süpernova 9 | ✔ | 8×11 | 3/2 | 20 ayna (8 döner), 14 duvar, 1 blok, 1 kapı, renkli ışık | 12 | 9216 | 2 | ✅ |
| 400 | Süpernova 10 | ✔ | 8×11 | 3/5 | 19 ayna (8 döner), 14 duvar, 1 prizma, 1 blok, 1 kapı | 12 | 3840 | 2 | ✅ |
| 401 | Tapınak Kapısı 1 | ✔ | 6×9 | 1/1 | 15 ayna (13 döner), 8 duvar, 2 filtre | 5 | 8192 | 4 | ✅ |
| 402 | Tapınak Kapısı 2 | ✔ | 6×9 | 1/1 | 11 ayna (8 döner), 8 duvar, 1 blok | 4 | 7424 | 1 | ✅ |
| 403 | Tapınak Kapısı 3 | ✔ | 6×9 | 2/2 | 16 ayna (9 döner), 8 duvar, 1 kapı | 7 | 512 | 1 | ✅ |
| 404 | Tapınak Kapısı 4 | ✔ | 6×9 | 2/2 | 15 ayna (10 döner), 9 duvar | 6 | 1024 | 1 | ✅ |
| 405 | Tapınak Kapısı 5 | ✔ | 7×10 | 1/1 | 17 ayna (14 döner), 9 duvar, 2 filtre | 7 | 16384 | 1 | ✅ |
| 406 | Tapınak Kapısı 6 | ✔ | 7×10 | 1/1 | 14 ayna (11 döner), 9 duvar, 1 blok | 7 | 92160 | 1 | ✅ |
| 407 | Tapınak Kapısı 7 | ✔ | 7×10 | 2/2 | 18 ayna (12 döner), 10 duvar, 1 kapı | 8 | 4096 | 1 | ✅ |
| 408 | Tapınak Kapısı 8 | ✔ | 7×10 | 2/2 | 20 ayna (13 döner), 10 duvar | 8 | 8192 | 2 | ✅ |
| 409 | Tapınak Kapısı 9 | ✔ | 7×10 | 1/1 | 18 ayna (15 döner), 10 duvar, 2 filtre | 7 | 32768 | 1 | ✅ |
| 410 | Tapınak Kapısı 10 | ✔ | 7×10 | 1/1 | 15 ayna (11 döner), 11 duvar, 1 blok | 5 | 63488 | 1 | ✅ |
| 411 | Sonsuz Aynalar 1 | ✔ | 7×10 | 1/1 | 15 ayna (11 döner), 9 duvar, 1 blok | 9 | 59392 | 1 | ✅ |
| 412 | Sonsuz Aynalar 2 | ✔ | 7×10 | 2/2 | 19 ayna (14 döner), 9 duvar, 1 kapı | 8 | 16384 | 2 | ✅ |
| 413 | Sonsuz Aynalar 3 | ✔ | 7×10 | 2/2 | 20 ayna (16 döner), 9 duvar | 9 | 65536 | 2 | ✅ |
| 414 | Sonsuz Aynalar 4 | ✔ | 7×10 | 1/1 | 19 ayna (14 döner), 9 duvar, 2 filtre | 8 | 16384 | 2 | ✅ |
| 415 | Sonsuz Aynalar 5 | ✔ | 7×10 | 1/1 | 15 ayna (12 döner), 10 duvar, 1 blok | 7 | 24576 | 3 | ✅ |
| 416 | Sonsuz Aynalar 6 | ✔ | 7×10 | 2/2 | 22 ayna (14 döner), 10 duvar, 1 kapı | 9 | 16384 | 3 | ✅ |
| 417 | Sonsuz Aynalar 7 | ✔ | 7×10 | 2/2 | 20 ayna (13 döner), 10 duvar | 9 | 8192 | 1 | ✅ |
| 418 | Sonsuz Aynalar 8 | ✔ | 7×10 | 1/1 | 21 ayna (14 döner), 10 duvar, 2 filtre | 9 | 16384 | 2 | ✅ |
| 419 | Sonsuz Aynalar 9 | ✔ | 7×10 | 1/1 | 15 ayna (11 döner), 11 duvar, 1 blok | 7 | 6144 | 1 | ✅ |
| 420 | Sonsuz Aynalar 10 | ✔ | 7×10 | 2/2 | 22 ayna (13 döner), 11 duvar, 1 kapı | 8 | 8192 | 3 | ✅ |
| 421 | Kadim Duvarlar 1 | ✔ | 7×11 | 2/2 | 21 ayna (11 döner), 14 duvar, 1 kapı | 8 | 2048 | 3 | ✅ |
| 422 | Kadim Duvarlar 2 | ✔ | 7×11 | 2/2 | 21 ayna (14 döner), 15 duvar | 8 | 16384 | 3 | ✅ |
| 423 | Kadim Duvarlar 3 | ✔ | 7×11 | 1/1 | 19 ayna (13 döner), 16 duvar, 2 filtre | 7 | 8192 | 2 | ✅ |
| 424 | Kadim Duvarlar 4 | ✔ | 7×11 | 1/1 | 16 ayna (12 döner), 17 duvar, 1 blok | 12 | 98304 | 3 | ✅ |
| 425 | Kadim Duvarlar 5 | ✔ | 7×11 | 2/2 | 22 ayna (13 döner), 18 duvar, 1 kapı | 9 | 8192 | 2 | ✅ |
| 426 | Kadim Duvarlar 6 | ✔ | 8×11 | 2/2 | 24 ayna (16 döner), 19 duvar | 9 | 65536 | 3 | ✅ |
| 427 | Kadim Duvarlar 7 | ✔ | 8×11 | 1/1 | 23 ayna (16 döner), 20 duvar, 2 filtre | 9 | 65536 | 2 | ✅ |
| 428 | Kadim Duvarlar 8 | ✔ | 8×11 | 1/1 | 16 ayna (10 döner), 21 duvar, 1 blok | 6 | 9216 | 3 | ✅ |
| 429 | Kadim Duvarlar 9 | ✔ | 8×11 | 2/2 | 24 ayna (12 döner), 22 duvar, 1 kapı | 8 | 4096 | 3 | ✅ |
| 430 | Kadim Duvarlar 10 | ✔ | 8×11 | 2/2 | 23 ayna (11 döner), 23 duvar | 8 | 2048 | 2 | ✅ |
| 431 | Altın Renkler 1 | ✔ | 8×11 | 3/3 | 19 ayna (14 döner), 10 duvar, renkli ışık | 8 | 16384 | 2 | ✅ |
| 432 | Altın Renkler 2 | ✔ | 8×11 | 2/2 | 17 ayna (10 döner), 10 duvar, 1 filtre, renkli ışık | 7 | 1024 | 1 | ✅ |
| 433 | Altın Renkler 3 | ✔ | 8×11 | 2/2 | 16 ayna (10 döner), 11 duvar, 1 blok, renkli ışık | 8 | 5120 | 2 | ✅ |
| 434 | Altın Renkler 4 | ✔ | 8×11 | 2/2 | 17 ayna (11 döner), 11 duvar, 1 kapı, renkli ışık | 7 | 2048 | 1 | ✅ |
| 435 | Altın Renkler 5 | ✔ | 8×11 | 3/3 | 21 ayna (16 döner), 12 duvar, renkli ışık | 9 | 65536 | 2 | ✅ |
| 436 | Altın Renkler 6 | ✔ | 8×11 | 2/1 | 19 ayna (15 döner), 12 duvar, 1 filtre, renkli ışık | 8 | 32768 | 1 | ✅ |
| 437 | Altın Renkler 7 | ✔ | 8×11 | 2/1 | 19 ayna (12 döner), 13 duvar, 1 blok, renkli ışık | 9 | 77824 | 5 | ✅ |
| 438 | Altın Renkler 8 | ✔ | 8×11 | 3/2 | 21 ayna (12 döner), 13 duvar, 1 kapı, renkli ışık | 9 | 4096 | 2 | ✅ |
| 439 | Altın Renkler 9 | ✔ | 8×11 | 3/2 | 22 ayna (16 döner), 14 duvar, 1 kapı, renkli ışık | 8 | 65536 | 3 | ✅ |
| 440 | Altın Renkler 10 | ✔ | 8×11 | 3/2 | 25 ayna (14 döner), 14 duvar, 1 filtre, renkli ışık | 8 | 16384 | 6 | ✅ |
| 441 | Mabet Camları 1 | ✔ | 8×11 | 1/1 | 18 ayna (12 döner), 10 duvar, 3 filtre | 8 | 4096 | 1 | ✅ |
| 442 | Mabet Camları 2 | ✔ | 8×11 | 1/1 | 14 ayna (10 döner), 10 duvar, 2 filtre, 1 blok | 7 | 58368 | 1 | ✅ |
| 443 | Mabet Camları 3 | ✔ | 8×11 | 2/2 | 19 ayna (10 döner), 11 duvar, 2 filtre, 1 kapı | 9 | 1024 | 1 | ✅ |
| 444 | Mabet Camları 4 | ✔ | 8×11 | 2/2 | 18 ayna (10 döner), 11 duvar, 2 filtre | 8 | 1024 | 1 | ✅ |
| 445 | Mabet Camları 5 | ✔ | 8×11 | 2/2 | 18 ayna (12 döner), 12 duvar, 5 filtre | 8 | 4096 | 1 | ✅ |
| 446 | Mabet Camları 6 | ✔ | 8×11 | 2/2 | 17 ayna (12 döner), 12 duvar, 4 filtre, 1 blok | 9 | 65536 | 2 | ✅ |
| 447 | Mabet Camları 7 | ✔ | 8×11 | 2/2 | 20 ayna (12 döner), 13 duvar, 4 filtre, 1 kapı | 9 | 4096 | 1 | ✅ |
| 448 | Mabet Camları 8 | ✔ | 8×11 | 3/3 | 20 ayna (13 döner), 13 duvar, 4 filtre | 9 | 8192 | 1 | ✅ |
| 449 | Mabet Camları 9 | ✔ | 8×11 | 2/2 | 20 ayna (15 döner), 14 duvar, 6 filtre | 8 | 32768 | 2 | ✅ |
| 450 | Mabet Camları 10 | ✔ | 8×11 | 2/2 | 18 ayna (11 döner), 14 duvar, 5 filtre, 1 blok | 10 | 67584 | 3 | ✅ |
| 451 | Işık Sunağı 1 | ✔ | 8×11 | 1/3 | 15 ayna (9 döner), 10 duvar, 1 prizma, 1 blok | 11 | 29184 | 2 | ✅ |
| 452 | Işık Sunağı 2 | ✔ | 8×11 | 2/4 | 16 ayna (8 döner), 10 duvar, 1 prizma, 1 kapı | 7 | 256 | 1 | ✅ |
| 453 | Işık Sunağı 3 | ✔ | 8×11 | 2/4 | 17 ayna (11 döner), 11 duvar, 1 prizma | 7 | 2048 | 2 | ✅ |
| 454 | Işık Sunağı 4 | ✔ | 8×11 | 1/2 | 16 ayna (12 döner), 11 duvar, 2 filtre, 1 prizma | 7 | 4096 | 1 | ✅ |
| 455 | Işık Sunağı 5 | ✔ | 8×11 | 1/3 | 16 ayna (11 döner), 12 duvar, 1 prizma, 1 blok | 9 | 63488 | 2 | ✅ |
| 456 | Işık Sunağı 6 | ✔ | 8×11 | 2/4 | 20 ayna (11 döner), 12 duvar, 1 prizma, 1 kapı | 10 | 2048 | 1 | ✅ |
| 457 | Işık Sunağı 7 | ✔ | 8×11 | 2/3 | 19 ayna (10 döner), 13 duvar, 1 filtre, 1 prizma | 9 | 1024 | 1 | ✅ |
| 458 | Işık Sunağı 8 | ✔ | 8×11 | 1/3 | 19 ayna (11 döner), 13 duvar, 1 filtre, 1 prizma | 9 | 2048 | 1 | ✅ |
| 459 | Işık Sunağı 9 | ✔ | 8×11 | 1/2 | 16 ayna (12 döner), 14 duvar, 1 filtre, 1 prizma, 1 blok | 9 | 81920 | 4 | ✅ |
| 460 | Işık Sunağı 10 | ✔ | 8×11 | 2/4 | 20 ayna (11 döner), 14 duvar, 1 prizma, 1 kapı | 8 | 2048 | 2 | ✅ |
| 461 | Yedi Hedef 1 | ✔ | 8×11 | 2/2 | 19 ayna (11 döner), 10 duvar, 1 kapı | 9 | 2048 | 1 | ✅ |
| 462 | Yedi Hedef 2 | ✔ | 8×11 | 3/3 | 21 ayna (13 döner), 10 duvar | 9 | 8192 | 3 | ✅ |
| 463 | Yedi Hedef 3 | ✔ | 8×11 | 2/2 | 19 ayna (13 döner), 11 duvar, 2 filtre | 9 | 8192 | 1 | ✅ |
| 464 | Yedi Hedef 4 | ✔ | 8×11 | 2/2 | 17 ayna (11 döner), 11 duvar, 1 blok | 12 | 77824 | 2 | ✅ |
| 465 | Yedi Hedef 5 | ✔ | 8×11 | 3/3 | 20 ayna (12 döner), 12 duvar, 1 kapı | 10 | 4096 | 1 | ✅ |
| 466 | Yedi Hedef 6 | ✔ | 8×11 | 3/3 | 20 ayna (11 döner), 12 duvar, 1 kapı | 9 | 2048 | 1 | ✅ |
| 467 | Yedi Hedef 7 | ✔ | 8×11 | 3/5 | 22 ayna (12 döner), 13 duvar, 1 filtre, 1 prizma | 9 | 4096 | 3 | ✅ |
| 468 | Yedi Hedef 8 | ✔ | 8×11 | 3/5 | 20 ayna (12 döner), 13 duvar, 1 prizma, 1 blok | 8 | 86016 | 3 | ✅ |
| 469 | Yedi Hedef 9 | ✔ | 8×11 | 3/5 | 21 ayna (12 döner), 14 duvar, 1 prizma, 1 kapı, renkli ışık | 9 | 4096 | 2 | ✅ |
| 470 | Yedi Hedef 10 | ✔ | 8×11 | 3/5 | 19 ayna (16 döner), 1 prizma, 1 kapı, renkli ışık | 12 | 65536 | 1 | ✅ |
| 471 | Kayan Sütunlar 1 | ✔ | 8×11 | 2/2 | 15 ayna (11 döner), 16 duvar, 1 blok | 8 | 14336 | 1 | ✅ |
| 472 | Kayan Sütunlar 2 | ✔ | 8×11 | 1/1 | 13 ayna (10 döner), 16 duvar, 2 filtre, 1 blok | 5 | 43008 | 1 | ✅ |
| 473 | Kayan Sütunlar 3 | ✔ | 8×11 | 1/1 | 13 ayna (10 döner), 17 duvar, 1 blok | 6 | 20480 | 1 | ✅ |
| 474 | Kayan Sütunlar 4 | ✔ | 8×11 | 2/2 | 16 ayna (9 döner), 17 duvar, 1 blok, 1 kapı | 10 | 19968 | 2 | ✅ |
| 475 | Kayan Sütunlar 5 | ✔ | 8×11 | 2/2 | 17 ayna (10 döner), 18 duvar, 1 blok | 9 | 10240 | 3 | ✅ |
| 476 | Kayan Sütunlar 6 | ✔ | 8×11 | 2/2 | 18 ayna (12 döner), 18 duvar, 2 filtre, 1 blok | 9 | 69632 | 8 | ✅ |
| 477 | Kayan Sütunlar 7 | ✔ | 8×11 | 2/2 | 16 ayna (10 döner), 19 duvar, 1 blok | 10 | 15360 | 2 | ✅ |
| 478 | Kayan Sütunlar 8 | ✔ | 8×11 | 2/2 | 16 ayna (11 döner), 19 duvar, 1 blok, 1 kapı | 10 | 51200 | 2 | ✅ |
| 479 | Kayan Sütunlar 9 | ✔ | 8×11 | 3/3 | 21 ayna (11 döner), 20 duvar, 1 blok | 11 | 51200 | 5 | ✅ |
| 480 | Kayan Sütunlar 10 | ✔ | 8×11 | 2/2 | 18 ayna (12 döner), 20 duvar, 2 filtre, 1 blok | 12 | 73728 | 2 | ✅ |
| 481 | Son Mühür 1 | ✔ | 8×11 | 2/2 | 19 ayna (14 döner), 11 duvar, 2 filtre, 1 kapı | 9 | 16384 | 1 | ✅ |
| 482 | Son Mühür 2 | ✔ | 8×11 | 2/2 | 16 ayna (12 döner), 11 duvar, 1 blok, 1 kapı | 8 | 45056 | 2 | ✅ |
| 483 | Son Mühür 3 | ✔ | 8×11 | 2/2 | 19 ayna (13 döner), 12 duvar, 1 kapı, renkli ışık | 8 | 8192 | 2 | ✅ |
| 484 | Son Mühür 4 | ✔ | 8×11 | 3/3 | 20 ayna (12 döner), 12 duvar, 1 kapı, renkli ışık | 9 | 4096 | 2 | ✅ |
| 485 | Son Mühür 5 | ✔ | 8×11 | 2/2 | 19 ayna (11 döner), 13 duvar, 2 filtre, 1 kapı | 9 | 2048 | 1 | ✅ |
| 486 | Son Mühür 6 | ✔ | 8×11 | 2/2 | 17 ayna (10 döner), 13 duvar, 1 blok, 1 kapı, renkli ışık | 8 | 19456 | 3 | ✅ |
| 487 | Son Mühür 7 | ✔ | 8×11 | 3/3 | 21 ayna (13 döner), 14 duvar, 2 kapı | 8 | 8192 | 3 | ✅ |
| 488 | Son Mühür 8 | ✔ | 8×11 | 3/3 | 22 ayna (13 döner), 14 duvar, 2 kapı, renkli ışık | 9 | 8192 | 3 | ✅ |
| 489 | Son Mühür 9 | ✔ | 8×11 | 3/3 | 22 ayna (15 döner), 15 duvar, 2 filtre, 2 kapı | 9 | 32768 | 3 | ✅ |
| 490 | Son Mühür 10 | ✔ | 8×11 | 3/3 | 20 ayna (11 döner), 15 duvar, 1 blok, 2 kapı, renkli ışık | 8 | 43008 | 6 | ✅ |
| 491 | Işığın Kalbi 1 | ✔ | 8×11 | 2/2 | 17 ayna (11 döner), 12 duvar, 1 filtre, 1 blok | 13 | 96256 | 1 | ✅ |
| 492 | Işığın Kalbi 2 | ✔ | 8×11 | 2/4 | 22 ayna (16 döner), 12 duvar, 1 prizma, 1 kapı | 10 | 65536 | 3 | ✅ |
| 493 | Işığın Kalbi 3 | ✔ | 8×11 | 3/2 | 20 ayna (11 döner), 13 duvar, 1 blok, renkli ışık | 10 | 55296 | 4 | ✅ |
| 494 | Işığın Kalbi 4 | ✔ | 8×11 | 2/2 | 19 ayna (10 döner), 13 duvar, 3 filtre, 1 kapı | 9 | 1024 | 1 | ✅ |
| 495 | Işığın Kalbi 5 | ✔ | 8×11 | 1/3 | 14 ayna (10 döner), 14 duvar, 1 filtre, 1 prizma, 1 blok | 9 | 7168 | 1 | ✅ |
| 496 | Işığın Kalbi 6 | ✔ | 8×11 | 3/2 | 23 ayna (14 döner), 14 duvar, 1 kapı, renkli ışık | 10 | 16384 | 4 | ✅ |
| 497 | Işığın Kalbi 7 | ✔ | 8×11 | 3/3 | 19 ayna (11 döner), 15 duvar, 1 filtre, 1 blok, 1 kapı | 12 | 43008 | 5 | ✅ |
| 498 | Işığın Kalbi 8 | ✔ | 8×11 | 2/3 | 20 ayna (12 döner), 15 duvar, 2 filtre, 1 prizma, 1 kapı | 10 | 4096 | 1 | ✅ |
| 499 | Işığın Kalbi 9 | ✔ | 8×11 | 3/2 | 22 ayna (13 döner), 16 duvar, 1 blok, 1 kapı, renkli ışık | 10 | 90112 | 4 | ✅ |
| 500 | Işığın Kalbi 10 | ✔ | 8×11 | 3/5 | 18 ayna (9 döner), 16 duvar, 1 prizma, 1 blok, 1 kapı | 11 | 4608 | 4 | ✅ |
