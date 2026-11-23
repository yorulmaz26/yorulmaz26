# Derleme Raporu — Gölgen Seni Yakalamasın 1.0.0

| Öğe | Değer |
|---|---|
| Proje | Android Studio, saf Java 17. Tek harici kütüphane reklam içindir: Google Mobile Ads (AdMob) 23.6.0 + User Messaging Platform 3.1.0 (gizlilik onayı). Test: JUnit 4.13.2 |
| Android Gradle Plugin / Gradle | 8.13.0 / 8.14.3 (wrapper) |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 (Android 8.0+) |
| Uygulama kimliği | `com.golgen.oyun`, sürüm 1.0.0 (versionCode 1) |
| Yön | Dikey (portrait), tam ekran, kamera çentiği uyumlu |
| İzinler | `VIBRATE` (oyun) + AdMob kütüphanesinin eklediği `INTERNET`, `ACCESS_NETWORK_STATE`, `AD_ID`, Android reklam hizmetleri (`ACCESS_ADSERVICES_*`) izinleri ve reklam SDK'sının kullandığı WorkManager'dan gelen `WAKE_LOCK`, `FOREGROUND_SERVICE`. Oyun çevrimdışı tam çalışır; internet yalnızca reklam için kullanılır |
| Release APK | `GolgenSeniYakalamasin.apk` — R8 küçültme + kaynak küçültme, **~2,1 MB** (reklam SDK'sı dahil; önceki reklamsız sürüm ~58 KB) |
| Debug APK | `GolgenSeniYakalamasin-debug.apk` — **~6,3 MB** |
| İmza | Release APK doğrudan kurulabilsin diye debug anahtarıyla imzalıdır. Play Store için kendi keystore'unuzla imzalayın. |
| CI | `.github/workflows/golgen-android.yml`: birim testleri + lint + debug/release APK → emülatör UI testi → GitHub Releases |
| Lint | 0 hata. İlk ölçümde 13 uyarı vardı; kullanılmayan kaynaklar (3), eski `-v26` klasör eki ve dokunma erişilebilirliği giderildi. Kalanlar bilinçli tercihler: dikey yön kilidi (LockedOrientation/DiscouragedApi), oyun görünümünün yalnızca kodla oluşturulması (ViewConstructor), API 33 altı cihazlar için geri tuşu uyumluluğu (GestureBackNavigation/UnusedAttribute) ve daha yeni AGP/SDK sürümü önerileri |
| Reklam kimlikleri | Varsayılan olarak **Google'ın resmi test kimlikleri**. Yayın öncesi `-PadmobAppId=… -PadmobBannerId=… -PadmobInterstitialId=… -PadmobRewardedId=…` ile (veya `gradle.properties`) gerçek kimlikler verilmelidir |
| Sonuç | BUILD SUCCESSFUL, birim testleri 79/79, UI testi 51/51, release `golgen-v1.0.0-b<N>` olarak yayınlandı |

## APK'yı edinme
- GitHub deposunda **Releases** › `golgen-v1.0.0-b…` › `GolgenSeniYakalamasin.apk`
- veya Actions › "Gölgen Seni Yakalamasın (Android)" › son çalışma › `golgen-apk` artefaktı.

Telefona kurulum: APK'yı indirip açın; "bilinmeyen kaynaklardan yükleme" izni istenirse verin.

## Yerelde derleme
```bash
cd golgen-seni-yakalamasin
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```
