# Test Raporu — Gölgen Seni Yakalamasın 1.0.0

Tüm testler otomatik çalıştırıldı. Aşağıdaki sonuçlar gerçek çalıştırma çıktılarıdır.

## 1. Birim testleri (JUnit, 34 test) — ✅ 34/34 geçti
Çalıştırıldığı yerler: geliştirme konteynerinde (`javac` + JUnit 4.13.2) ve GitHub Actions'ta `./gradlew testDebugUnitTest`.

| Sınıf | Test | Doğrulanan |
|---|---|---|
| MotionRecorderTest | 4 | Enterpolasyon, uç değer kırpma, halka tampon taşması, zamanın geri gitmesinin reddi |
| PoolTest | 1 | Havuzun nesneleri yeniden kullanması ve kapasite sınırı |
| GameWorldTest | 13 | Gölgenin tam olarak *t − gecikme* konumunda olması (120 kare boyunca), hareketsiz kalınca ~4. sn'de yakalanma, daire çizerek kaçabilme, **duraklatmada zaman/gölgenin donması** ve devamda sıçrama olmaması, aynı tohumla determinizm, engel çarpması → can kaybı + dokunulmazlık, boşluktan geçiş, elmas toplama, gölge sayısı sınırı (5), 20 tohumla bot üzerinden adil engel üretimi, duvar boşluk genişliği/erişilebilirliği (300 sn), görev kazanma, engelsiz görevde ilk çarpmada başarısızlık, kıl payı kaçış sayımı |
| ProgressTest | 8 | Kayıt/okuma gidiş-dönüş (tüm alanlar), kilitli karakter seçiminin reddi, rekor ve ilk 10 listesi, satın alma/seçme, görev açılması ve tek seferlik ödül, günlük görev üretimi (400 gün, tekrarsız) ve ödülün bir kez alınması, oyunlardan günlük ilerleme, sıfırlama |
| SynthTest | 3 | 8 ses efektinin duyulabilir ve kırpılmamış olması, müzik döngüsü, WAV başlığı |
| AllocationTest | 1 | 100 sn ısınmadan sonra 30 sn'lik simülasyonda (5 gölge, engeller, elmaslar) iş parçacığı bellek tahsisi < 8 bayt/kare — pratikte sıfır; GC kaynaklı takılma olmaz |
| MissionsAndSkinsTest | 3 | ≥8 karakter ve artan fiyatlar, 12 görevin 4 türü de kapsaması, başarı tabloları |

## 2. Derleme kontrolleri — ✅
- Android 15 API'sine (android-all) karşı tüm kaynakların yerel `javac` derlemesi: hatasız.
- GitHub Actions: `testDebugUnitTest lintDebug assembleDebug assembleRelease` → **BUILD SUCCESSFUL**.

## 3. Emülatörde uçtan uca UI testi — ✅ 28/28 geçti
Ortam: GitHub Actions, Android 14 (API 34) emülatörü, Pixel 6 profili (1080×2400), betik: `tools/smoke_test.sh`.
Butonlara gerçek dokunma olaylarıyla (`adb input tap/swipe`) basılır; ekran geçişleri uygulama günlüğünden doğrulanır.

Sonuçların tamamı: [`ekranlar/test_sonuclari.txt`](ekranlar/test_sonuclari.txt) · Ekran görüntüleri: [`ekranlar/`](ekranlar/)

| Alan | Kontrol |
|---|---|
| Kurulum / açılış | APK kurulumu, Açılış ekranı, ilk açılışta öğretici |
| Sahne geçişleri | Öğretici→Menü, Menü→Oyun, Oyun→Duraklatma→Menü, Oyun sonu→Tekrar, Karakter, Görevler, Görev 1, Rekorlar, Ayarlar, Ayarlar→Öğretici→Menü, geri tuşu |
| Oynanış | Sürükleyerek oynama, duraklatmada 6 sn beklenince oyunun bitmemesi, devam edip hareketsiz kalınca gölgenin yakalaması |
| Kayıt sistemi | Oyun sayısı ve rekor kaydı, satın alma sonrası elmas/karakter kaydı, ses ve titreşim ayarlarının kaydı, uygulama kapatılıp açılınca öğreticinin atlanması ve seçili karakterin korunması |
| Kararlılık | Çökme (FATAL EXCEPTION) ve ANR yok |

## 4. Bulunan ve düzeltilen hatalar
1. **Açılış ekranı test günlüğüne düşmüyordu** → ilk ekran da loglanıyor; açılış kontrolü yavaş emülatörde zamanlama yarışına girmeyecek şekilde düzeltildi.
2. **Android'in "Viewing full screen" sistem uyarısı** ilk açılışta dokunuşları yutuyordu (UI testinde 50 adım zincirleme kaldı). Gerçek kullanıcı "Got it"e bir kez basar; test ortamında uyarı önceden onaylanıyor. Düzeltmeden sonra 28/28.
3. **Uyumluluk (letterbox) modu:** manifestteki `resizeableActivity="false"` nedeniyle sistem uygulamayı küçültüp "Tap to restart this app" düğmesi gösterdi; bu düğme Rekorlar butonunu örttü. Ayar kaldırıldı, yapılandırma değişiklikleri uygulama içinde ele alınıyor.
4. **Duraklatma testi:** oyun hareket sırasında engel/gölge yüzünden bitebildiği için test yeniden deneme yapacak şekilde sağlamlaştırıldı.
5. Ekran görüntüsü incelemesiyle düzen düzeltmeleri: karakter adının "Satın Al" butonuyla çakışması, oyun sonu başlığının başarı bildirimi altında kalması, ana menüde gösteri karakterlerinin başlığa yapışık olması, günlük görev ilerleme metninin çubuğa yapışık olması.

## 5. Performans notu (dürüst değerlendirme)
- Emülatörde ölçülen kare hızı ~21 FPS'dir; CI emülatörü GPU'suz yazılımsal (SwiftShader) çizim kullandığı için bu değer gerçek cihazı temsil etmez. **Gerçek cihazda 60 FPS ölçümü yapılmadı** (bu ortamda fiziksel cihaz yok).
- Tasarım tarafında 60 FPS için: oyun döngüsünde tahsis yok, havuzlar, önceden üretilmiş ışıma dokusu, donanım hızlandırmalı Canvas, ekran yenilemesine senkron Choreographer döngüsü, sabit 1/120 sn simülasyon adımı.
- Uygulama, debug derlemede her 5 sn'de `GolgenFPS` etiketiyle FPS yazar: `adb logcat -s GolgenFPS` ile gerçek cihazda doğrulanabilir.

## 6. Test edilmeyenler
- Gerçek cihazda dokunma hissi, titreşim ve ses çıkışı (emülatör sessiz ve titreşimsiz çalıştırıldı; ses sentezi birim testinde doğrulandı).
- iOS (proje Android hedefli; çekirdek mantık taşınabilir).
