# Google Play yayın kontrol listesi

## Derleme
- [ ] `keystore.properties.example` dosyasını `keystore.properties` olarak kopyalayın, anahtarı üretin
      (`keytool -genkeypair -v -keystore yeralti-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias yeralti`).
      Anahtarı ve parolaları depoya **eklemeyin**; Play App Signing kullanın.
- [ ] `android/build.gradle.kts` içinde `versionCode`/`versionName` artırın.
- [ ] `./gradlew :sim:test :android:bundleRelease` → `android/build/outputs/bundle/release/android-release.aab`.
- [ ] Release APK'yı en az bir düşük ve bir yüksek segment cihazda deneyin: açılış, kazı, kayıt/yükleme,
      arka plana alıp dönme (çevrimdışı rapor), uygulamayı öldürüp açma, düşük grafik kalitesi.

## Mağaza kaydı (taslak)
- **Ad:** Yeraltı İmparatorluğu: Koloni Savaşları
- **Kısa açıklama:** Kaz, üret, savun, keşfet: tek bir kalıcı karınca kolonisini yeraltı imparatorluğuna dönüştür.
- **Uzun açıklama:** Bölüm yok, tek bir kalıcı koloni var. Tünel kaz, 25 farklı oda kur, 15 karınca sınıfı yetiştir,
  32 araştırmayla yeni yetenekler aç. Mevsimler, sel ve kuraklıkla başa çık; rakip kolonilerle savaş ya da ittifak
  kur; keşif seferleriyle sonsuz bölgeler keşfet; fosil müzeni ve genetik koleksiyonunu doldur. Tamamen çevrimdışı,
  reklamsız; gerçek para ile satın alma yok.
- **Kategori:** Strateji • **İçerik derecelendirmesi:** Herkes (hafif çizgi film şiddeti: böcekler arası çatışma)
- Ekran görüntüleri: `docs/ekran/` (1080×1920 için masaüstü betiğini `-Dyi.scale=2.4` ile çalıştırın).
- Uygulama simgesi: `android/res/drawable/ic_launcher.xml` (512×512 mağaza simgesi ayrıca hazırlanmalı).

## Veri güvenliği formu
- Veri toplanmaz, paylaşılmaz. İnternet izni yoktur (`AndroidManifest.xml` izin istemez).
- Kayıt yalnızca uygulamanın özel depolamasında tutulur (`files/save/koloni.json`, yedek `.bak`).
- Reklam, analiz veya üçüncü taraf SDK'sı yoktur.

## Gelecekte uygulama içi satın alma
- `sim/Market.kt` içindeki `PurchaseBackend` arayüzü Google Play Billing ile uygulanabilir; şu an
  `OfflinePurchaseBackend` kullanılır ve gerçek para akışı yoktur. Eklenirse veri güvenliği formu güncellenmelidir.
