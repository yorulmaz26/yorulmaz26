# Google Play yayın gereksinimleri — doğrulama

**Tarih:** 28 Kasım 2026 · **Sürüm:** 1.8.0 (versionCode 12) · **Paket:** `com.isikyolu.game` (değiştirilmedi)

**Resmî kaynaklar:** Bu oturumda, bu tarihte doğrudan indirilip okundu.
- developers.google.com/admob/android/privacy/play-data-disclosure (son güncelleme 26.11.2026)
- developers.google.com/admob/android/deprecation
- support.google.com/googleplay/android-developer/answer/11926878 (hedef API)
- …/answer/9866151 (mağaza görselleri)
- …/answer/9898842 (meta veri politikası)
- …/answer/10787469 (veri güvenliği)
- …/answer/13392821 (ön plan hizmeti)
- developer.android.com/guide/practices/page-sizes (16 KB)

| Gereksinim | Resmî kural | Projedeki durum | Sonuç |
|---|---|---|---|
| Hedef API | 31 Ağustos 2026'dan beri yeni uygulamalar ve güncellemeler **Android 16 (API 36)** hedeflemeli | `targetSdk 36`, `compileSdk 36`, `minSdk 26` | TEST EDİLDİ |
| Android App Bundle | Yeni uygulamalar AAB ile yüklenir | `bundleRelease` ile `app-release.aab` üretildi (6,3 MB) | TEST EDİLDİ (imza: bkz. aşağı) |
| 64 bit | Yerel kod varsa 64 bit sürümü gerekir | APK/AAB'de hiç `.so` yok (yalnızca Java/Kotlin) | UYGULANAMAZ (yerel kod yok) |
| 16 KB sayfa boyutu | API 35+ hedefleyen uygulamalar 64 bit cihazlarda 16 KB'ı desteklemeli; 1 Şubat 2027'den sonra desteklemeyen güncellemeler yayınlanamaz. Yalnızca Java/Kotlin kullanan uygulama "zaten destekler" | Yerel kod yok. `zipalign -c -P 16 -v 4` → "Verification succesful" | TEST EDİLDİ |
| İzinler | Gereksiz izin olmamalı | Uygulamanın kendi izinleri: `VIBRATE`, `INTERNET`, `ACCESS_NETWORK_STATE`. Diğerleri AdMob SDK'sının birleşik manifestinden gelir: `AD_ID`, `ACCESS_ADSERVICES_*`, `WAKE_LOCK`, `FOREGROUND_SERVICE`. Konum, kamera, depolama, kişiler gibi izin **yok** | TAMAMLANDI |
| Ön plan hizmeti beyanı | API 34+ hedefleyen ve ön plan hizmeti **türü** kullanan uygulamalar beyan verir | Tür izni yok; WorkManager'ın türsüz `SystemForegroundService` hizmeti ve `FOREGROUND_SERVICE` izni SDK'dan gelir; oyun ön plan hizmeti başlatmaz | MANUEL KONTROL GEREKLİ (Play Console sorarsa "kullanılmıyor") |
| Gizlilik politikası | Zorunlu; herkese açık URL | 6 dilde metin hazır (`09_Privacy_And_Legal`). Oyun içinde Ayarlar > Gizlilik Politikası | ERİŞİM GEREKLİ (yayın adresi yok) |
| Veri güvenliği | SDK verileri dahil beyan edilmeli | `Data_Safety_Form.md` (AdMob resmî tablosu + bağımlılık analizi) | TAMAMLANDI (Console'a girilmesi MANUEL) |
| Reklam beyanı | "Reklam içerir" | Evet: banner, geçiş, ödüllü video | MANUEL KONTROL GEREKLİ (Console) |
| Reklam kimliği | AD_ID izni olan uygulama beyan eder | Evet (reklam, analiz) | MANUEL KONTROL GEREKLİ (Console) |
| İçerik derecelendirmesi | IARC anketi | Önerilen yanıtlar: `Content_Rating_and_Audience.md` | MANUEL KONTROL GEREKLİ |
| Hedef kitle | Çocuklara yönelikse Aileler politikası | 13+ önerildi (kişiselleştirilmiş reklam) | MANUEL KONTROL GEREKLİ |
| SDK güncelliği | Kullanımdan kalkmış SDK'lar risklidir | Google Mobile Ads **25.5.0** = eski SDK'nın en son sürümü. v25 "Supported"; kullanımdan kalkma 30.06.2027, kapanış 30.06.2028. Google eski SDK'nın "bakım modunda" olduğunu, yeni özellikler için "GMA Next-Gen SDK"ya geçişi öneriyor. UMP 4.0.0 | TAMAMLANDI; Next-Gen geçişi 2027 ortasından önce planlanmalı |
| Uygulama içi satın alma / Billing | Gerçek para varsa Play Billing | Yok; Play Billing kütüphanesi yok | UYGULANAMAZ |
| Sürüm kodu/adı | Her yüklemede artmalı | 11 → **12**, "1.7.1" → **"1.8.0"** | TAMAMLANDI |
| Paket adı / applicationId | Değişmemeli | `com.isikyolu.game` (aynı) | TAMAMLANDI |
| İmza | Play App Signing; yükleme anahtarı geliştiricide | Gradle `uploadStoreFile` … ile kendi anahtarını kullanır. Verilmezse debug anahtarı (yalnızca test). Teslimdeki AAB **debug imzalı** | ERİŞİM GEREKLİ (yükleme anahtarı) |
| Uygulama adı | ≤ 30 karakter, emoji/büyük harf/"en iyi" yok | Mağaza başlıkları 20 karakter; `store_texts.py` denetimi geçti | TEST EDİLDİ |
| Kısa/uzun açıklama | 80 / 4000 karakter | En uzun: kısa 78 (fr), uzun 1669 (fr) | TEST EDİLDİ |
| Simge | 512×512, 32 bit PNG (alfa), ≤ 1024 KB | `app_icon_512.png` | bkz. `Media_QA.md` |
| Öne çıkan görsel | 1024×500, JPEG ya da 24 bit PNG (alfa yok) | `feature_graphic_tr/en.png` | bkz. `Media_QA.md` |
| Telefon ekran görüntüleri | 2–8 adet; kenarlar 320–3840 px; uzun kenar ≤ 2,3 × kısa kenar. Oyunlarda öne çıkmak için en az 3 adet 9:16 (≥1080×1920), gerçek oyun deneyimi | Her dilde 8 adet 1080×1920; ilk üçü oynanış | bkz. `Media_QA.md` |
| Tablet ekran görüntüleri | En az 4; kenarlar 1080–7680 px | 7 inç 1200×1920, 10 inç 1600×2560, gerçek arayüz | bkz. `Media_QA.md` |
| Tanıtım videosu | YouTube URL'si; para kazanma kapalı; herkese açık/liste dışı; yaş kısıtlaması yok; yerleştirilebilir | MP4'ler hazır; yükleme senin hesabınla | ERİŞİM GEREKLİ (YouTube'a yükleme ve URL) |
| Mağaza görsellerinde yasak ifadeler | "Best, #1, Top, New, Free, Discount…" yok | Görsellerde ve metinlerde yok; sosyal medya CTA'ları yalnızca sosyal sürümlerde | TAMAMLANDI |

## İzinlerin kaynağı

`aapt2 dump permissions` (release APK):

```
VIBRATE, INTERNET, ACCESS_NETWORK_STATE                      ← uygulama (manifest)
com.google.android.gms.permission.AD_ID                      ← play-services-ads
ACCESS_ADSERVICES_AD_ID / _ATTRIBUTION / _TOPICS             ← play-services-ads (Privacy Sandbox)
WAKE_LOCK, FOREGROUND_SERVICE                                ← androidx.work (AdMob bağımlılığı)
com.isikyolu.game.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION   ← androidx.core (imza düzeyi, iç kullanım)
```

SDK izinlerini `tools:node="remove"` ile kaldırmak reklam ölçümünü bozabileceği için dokunulmadı.
