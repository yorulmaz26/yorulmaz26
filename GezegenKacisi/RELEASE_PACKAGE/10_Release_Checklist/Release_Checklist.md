# Yayın kontrol listesi

| Madde | Durum |
|---|---|
| Hedef API 36 (Android 16) — Play'in 31 Ağustos 2026'dan beri yeni uygulamalar için şartı (resmî sayfadan doğrulandı) | TAMAMLANDI |
| Android 16 geri hareketi (OnBackInvokedCallback), oyun kategorisi, kenardan kenara | TAMAMLANDI |
| Android App Bundle (AAB) üretimi | TAMAMLANDI (imzasız) |
| AAB'nin yükleme anahtarıyla imzalanması | ERİŞİM GEREKLİ — `GezegenKacisi/keystore.properties` oluşturun (bkz. app/build.gradle.kts), `./gradlew bundleRelease` |
| 64 bit / 16 KB sayfa boyutu: APK'da yerel (.so) kütüphane yok | TAMAMLANDI (uygulanmaz durumda uyumlu) |
| Paket adı com.example.gezegenkacisi | MANUEL KONTROL GEREKLİ — Play "com.example" ile başlayan paket adlarını kabul etmez. Değiştirmek yeni bir uygulama demektir (eski kurulumlar güncellenmez). Henüz yayınlanmadığı için yayından önce değiştirilmesi önerilir; talimatınız gereği değiştirilmedi. |
| Sürüm kodu 13 / sürüm adı 3.0 | TAMAMLANDI |
| AdMob TEST kimliklerinin gerçek kimliklerle değiştirilmesi (app/build.gradle.kts) | ERİŞİM GEREKLİ |
| Gizlilik politikasının web'de yayınlanması ve URL'si | ERİŞİM GEREKLİ |
| app-ads.txt | ERİŞİM GEREKLİ |
| Veri güvenliği formu | MANUEL KONTROL GEREKLİ (taslak hazır) |
| İçerik derecelendirmesi (IARC) ve hedef kitle | MANUEL KONTROL GEREKLİ (önerilen yanıtlar hazır) |
| Mağaza metinleri 6 dil | TAMAMLANDI (ana dili konuşan son okuma önerilir) |
| Simge 512, kapak 1024×500 (TR/EN), 48 telefon görseli, 7"/10" tablet görselleri | TAMAMLANDI |
| Tanıtım videosu: Play yalnızca YouTube bağlantısı kabul eder | ERİŞİM GEREKLİ — trailer_xx_vertical.mp4 YouTube'a yüklenip bağlantısı girilmeli |
| Marka araştırması (resmî veritabanları) | MANUEL KONTROL GEREKLİ |
| Gerçek cihaz testleri | MANUEL KONTROL GEREKLİ |
