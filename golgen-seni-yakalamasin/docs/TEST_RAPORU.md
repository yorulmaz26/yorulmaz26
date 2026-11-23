# Test Raporu — Gölgen Seni Yakalamasın 1.0.0

Tüm testler otomatik çalıştırıldı. Aşağıdaki sonuçlar gerçek çalıştırma çıktılarıdır.

## 1. Birim testleri (JUnit, 79 test) — ✅ 79/79 geçti
Çalıştırıldığı yerler: geliştirme konteynerinde (`javac` + JUnit 4.13.2) ve GitHub Actions'ta `./gradlew testDebugUnitTest`.

| Sınıf | Test | Doğrulanan |
|---|---|---|
| MotionRecorderTest | 4 | Enterpolasyon, uç değer kırpma, halka tampon taşması, zamanın geri gitmesinin reddi |
| PoolTest | 1 | Havuzun nesneleri yeniden kullanması ve kapasite sınırı |
| GameWorldTest | 12 | Gölgenin tam olarak *t − gecikme* konumunda olması, hareketsiz kalınca yakalanma, daire çizerek kaçabilme, **duraklatmada zaman/gölgenin donması**, determinizm, engel çarpması → can kaybı + dokunulmazlık, boşluktan geçiş, elmas toplama, gölge sayısı sınırı, botla adil engel üretimi, duvar boşluğu erişilebilirliği, kıl payı kaçış |
| ProgressTest | 9 | Kayıt/okuma gidiş-dönüş, kilitli karakter seçiminin reddi, rekor ve ilk 10, satın alma, bölüm açılması ve tek seferlik ödül, günlük görevler, sıfırlama, **eski kayıttan (sürüm 1) yükseltmede eşiği büyüyen başarıların geri alınması** |
| SynthTest | 3 | 8 ses efekti, müzik döngüsü, WAV başlığı |
| AllocationTest | 1 | Isınmadan sonra oyun döngüsünde bellek tahsisi < 8 bayt/kare (GC takılması yok) |
| EnergyTest | 13 | İlk kurulumda 4 can, ölünce can gitmesi / kazanınca iadesi, 15 dk'da 1 can (en fazla 4), saati ileri/geri almanın işe yaramaması, ileri-geri döngüsü, yeniden başlatma senaryoları, **yeniden başlatma + saati ileri alma ile en fazla 1 can**, **günlük ödül gününün tarih değişikliğinden etkilenmemesi ve geri gitmemesi**, **kapat-aç + tarihi ileri almanın en fazla bir ödül günü açması**, kayıt/yükleme, elmasla doldurma |
| LevelsTest | 18 | 500 bölüm/100 harita yapısı, kademeler, zorluğun **500 bölüm boyunca kesintisiz** artması, haritaların ayırt edilebilirliği, 2 aşama / 1→2 gölge / 40 yıldız kuralı, yıldızların erişilebilir alanda doğması, yıldız toplayan botun bölüm bitirmesi, rüzgâr, buz, dar geçit, meteor, tüm engel türleri, lazer kapı, **hızlı gölgelerin her bölümde etkili olması**, **dar geçitte 9 engel türünün, yıldız, elmas ve güçlendirmelerin koridor içinde kalması, kapının yanında geçiş payı (85 dar bölüm)**, **dar geçit ile hızlı gölgelerin hiçbir haritada birleşmemesi**, **karanlıkta her akış hızında ≥ 0,6 sn ileriyi görme** |
| LongevityTest | 12 | Kalkan, Yavaş Zaman, Mıknatıs, kombo, aşamalar, yıldız kuralları, XP/seviye, 7 günlük giriş serisi, günlük görev türleri, başarılar ve karakterler |
| MissionsAndSkinsTest | 2 | Karakter fiyatları, başarı tabloları |
| AdPolicyTest | 4 | İlk 3 oyunda geçiş reklamı olmaması, en fazla 3 oyunda bir + 150 sn arayla gösterim, 12 sn'den kısa oyunların sayılmaması, ödüllü video ile günde en fazla 3 can |

## 2. Derleme kontrolleri — ✅
- Android 15 API'sine (android-all) karşı tüm kaynakların yerel `javac` derlemesi: hatasız.
- GitHub Actions: `testDebugUnitTest lintDebug assembleDebug assembleRelease` → **BUILD SUCCESSFUL**.

## 3. Emülatörde uçtan uca UI testi — ✅ 51/51 geçti
Ortam: GitHub Actions, Android 14 (API 34) emülatörü, Pixel 6 profili (1080×2400), betik: `tools/smoke_test.sh`.
Butonlara gerçek dokunma olaylarıyla (`adb input tap/swipe`) basılır; ekran geçişleri uygulama günlüğünden doğrulanır.

Sonuçların tamamı: [`ekranlar/test_sonuclari.txt`](ekranlar/test_sonuclari.txt) · Ekran görüntüleri: [`ekranlar/`](ekranlar/)

| Alan | Kontrol |
|---|---|
| Kurulum / açılış | APK kurulumu, logo açılışı, ilk açılışta öğretici, günlük giriş ödülü ve kaydı |
| Sahne geçişleri | Tüm menü butonları, oyun, duraklatma, oyun sonu, karakter, bölümler, rekorlar, ayarlar, öğretici (Ayarlar'a döner), geri tuşu |
| Oynanış | Sürükleyerek oynama, duraklatmada beklenince oyunun bitmemesi, hazır ekranından duraklatılınca yine hazır ekranına dönme, gölge yakalaması |
| Bölümler | 14 farklı haritanın bölümleri (karanlık, fırtına, dar geçit, 500. bölüm dahil) açılıp oynanıyor, çökme yok |
| Reklamlar | AdMob SDK'sının başlaması, banner'ın yalnızca menülerde yüklenmesi ve oyun ekranında gizlenmesi, geçiş reklamı ve ödüllü videonun gösterilmesi, reklam sırasında çökme olmaması (Google test kimlikleriyle) |
| Kayıt sistemi | Oyun sayısı, rekor, satın alma, ayarlar, yeniden açılışta kalıcılık |
| Kararlılık | Çökme (FATAL EXCEPTION) ve ANR yok |

Reklam adımları "BİLGİ" olarak işaretlidir: reklamın dolması Google sunucusuna bağlıdır, dolmazsa test başarısız sayılmaz
(oyun reklamsız devam eder). Son çalıştırmada banner, geçiş reklamı ve ödüllü video üçü de gerçekten gösterildi.

## 4. 500 bölümün denetimi (mantık hatası / geçilemeyen bölüm)
Kod; bölüm üretimi, harita özellikleri, can/saat, reklam akışı, ekran durumları ve kayıt açısından paralel denetçilerle
incelendi, her bulgu ayrı doğrulayıcılarla çürütülmeye çalışıldı. Doğrulanan ve düzeltilen bulgular:

| # | Bulgu | Düzeltme |
|---|---|---|
| 1 | Dar geçitli haritalarda elmas, güçlendirme ve mayınlar ulaşılamayan kenar şeritlerinde doğabiliyordu; salınan blok, dönen lazer ve çift top koridoru hesaba katmıyordu | Tümü koridor içinde üretiliyor; dönen lazer koridorun en fazla %80'i |
| 2 | Dar geçit + hızlı gölgeler birlikte olan 70 bölüm (14 harita) komşularından 5–10 kat zordu | İkili aynı haritada birleşmiyor; dar geçitte gölgeler %12 daha geriden gelir |
| 3 | Hızlı gölgeler özelliği gecikme alt sınırı yüzünden geç bölümlerde etkisizdi; kademe sınırlarında zorluk testere dişi gibi düşüyordu | Gölge gecikmesi 500 bölüm boyunca tek eğri; hızlı gölgeler her zaman %12 daha yakın |
| 4 | Karanlıkta görüş yarıçapı sabitti; hızlı kademelerde duvar boşluğu görüldüğünde kaçacak zaman kalmıyordu (iyimser bot bile %74) | Aydınlık alan akış hızıyla büyür ve ileri kayar; boşluk kenarları ve tehlike çerçeveleri karanlığın üstünde çizilir |
| 5 | Meteor uyarısı karanlığın altında kalıyordu (karanlık + meteor olan 105 bölüm) | Uyarı karanlığın üstünde çiziliyor |
| 6 | Tarihi ileri almak günlük ödülleri (giriş serisi, günlük görevler) yeniden açıyordu | Gün, monoton saatle doğrulanan güvenilir saatten hesaplanır, geri gitmez. Sunucusuz kapatılamayan tek yol: telefonu yeniden başlatıp tarihi ileri almak — her yeniden başlatma en fazla bir yeni ödül günü açar |
| 7 | Telefonu yeniden başlatıp saati ileri almak 4 canı birden dolduruyordu | Yeniden başlatma başına en fazla 1 can |
| 8 | Hazır ekranındayken duraklatıp devam edince oyun kendiliğinden başlıyordu | Hazır ekranına döner |
| 9 | Uygulama arka plana geçip odak kaybedince oyun duraklamıyordu | Odak kaybında duraklar |
| 10 | Başarı bildirimi yanlış ödül miktarını gösteriyordu; can sayacı oyun sonu ekranında donuyordu | Düzeltildi |
| 11 | Kısa/geniş ekranlarda oyun alanı çok küçülebiliyordu | En az 1200 birim yükseklik görünecek şekilde ölçeklenir, yatayda ortalanır |
| 12 | Öğretici Ayarlar'dan açılınca ana menüye dönüyordu; eski kayıttaki başarılar yeni eşiklerle tutarsızdı | Ayarlar'a döner; yükseltmede tutarsız başarılar geri alınır |
| 13 | (Son kontrol) Karanlık bölümde dokunup başlarken ışık dairesi bir karede %28'e kadar büyüyüp kayıyordu; karanlık katmanının kenarlarında ince ışık sızıntısı vardı | Akış hızı ilk adımın değeriyle başlar; karanlık katmanı tam piksellere hizalı, kenar yumuşatmasız çizilir |

### Oynanabilirlik ölçümü (botlar)
Her bölüm 2222 birim yüksekliğinde arenada, 1/60 sn adımla, gerçek oyun kodu (`core`) ile oynatıldı.

| Bot | Kapsam | Sonuç |
|---|---|---|
| Güçlü planlayıcı (geleceği simülasyonla tarar) | 500 bölüm × 3 tohum | **1500/1500 kazandı** |
| İnsan benzeri (kısa ufuk, 1/6 sn'de bir karar, yeni doğacak engelleri bilmez) | 500 bölüm × 3 tohum | **1500/1500 kazandı** (düzeltmelerden önce 1498/1500: 499 ve 500. bölümler kaybediliyordu) |
| Karanlık modeli: yalnızca ekranda gerçekten görünen engelleri bilen güçlü bot | 225 karanlık bölüm × 2 tohum | **450/450** (düzeltmeden önce sabit görüşle 311/420) |
| Karanlık modeli: insan benzeri bot | 225 karanlık bölüm × 2 tohum | **450/450** |

Sonuç: **geçilemeyen bölüm bulunmadı.** Botlar insan değildir; bu ölçüm "her bölüm adil biçimde geçilebilir" olduğunu
gösterir, bir insanın zorluğu nasıl hissedeceğini göstermez.

## 5. Daha önce bulunan ve düzeltilen hatalar
1. Android'in "Viewing full screen" uyarısı ilk açılışta dokunuşları yutuyordu → test ortamında önceden onaylanıyor.
2. Uyumluluk (letterbox) modu: `resizeableActivity="false"` kaldırıldı.
3. Karanlık haritalarda yazılımsal GPU'da donma: gradyan gölgelendirici yerine önceden üretilmiş doku.
4. Ekran görüntüsü incelemesiyle çeşitli yerleşim çakışmaları giderildi.

## 6. Performans notu (dürüst değerlendirme)
- CI emülatörü GPU'suz yazılımsal çizim kullandığı için ölçülen ~21 FPS gerçek cihazı temsil etmez. **Gerçek cihazda 60 FPS
  ölçümü yapılmadı** (bu ortamda fiziksel cihaz yok).
- Debug derlemede her 5 sn'de `GolgenFPS` etiketiyle FPS yazılır: `adb logcat -s GolgenFPS`.

## 7. Test edilmeyenler
- Gerçek cihazda dokunma hissi, titreşim, ses ve zorluğun insan oyuncuya nasıl hissettirdiği.
- **Gerçek AdMob reklam kimlikleriyle** gösterim ve gelir (yalnızca Google test kimlikleri test edildi).
- iOS (proje Android hedefli).
