# Yanlış Renk — Derleme Raporu

| Çıktı | Dosya | Boyut | İmza |
|---|---|---|---|
| Release APK | `dist/YanlisRenk-1.0.7.apk` | 11,9 MB | **Android debug anahtarı** (kurulabilir test sürümü) |
| Debug APK | `dist/YanlisRenk-1.0.7-debug.apk` | ~12,7 MB | Android debug anahtarı |
| Google Play AAB | `dist/YanlisRenk-1.0.7-release.aab` | ~9 MB | **Android debug anahtarı** |

> 1.0.0 sürüm APK, R8 küçültmesi yüzünden açılışta çöküyordu; 1.0.1'de R8 kapalı (ayrıntı TEST_REPORT.md).

SHA-256 özetleri: `dist/SHA256SUMS.txt` (`dist/` git'e eklenmez; komutlarla yeniden üretilir).

* Paket adı: `com.yanlisrenk.oyun` · sürüm 1.0.7 (kod 8) · minSdk 26 (Android 8.0) · targetSdk/compileSdk 35
* ABI: armeabi-v7a, arm64-v8a, x86, x86_64
* İzinler: `INTERNET` (yalnızca güvenilir zaman için), `VIBRATE`
* Yönlendirme: dikey · Kenar yumuşatma: 4× MSAA

## Google Play'e yükleme için gerekenler (sizin yapmanız gereken tek adım)

Play Store, debug anahtarıyla imzalı AAB'yi **kabul etmez**. Yayın için kendi yükleme anahtarınızı oluşturmanız gerekir; bu bir hesap/gizli anahtar kararı olduğu için sizin adınıza oluşturulmadı:

```bash
keytool -genkeypair -v -keystore yanlisrenk-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

`yanlis-renk/keystore.properties` dosyası oluşturun (git'e eklenmez):

```
storeFile=yanlisrenk-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Ardından `./gradlew :android:bundleRelease` aynı AAB'yi sizin anahtarınızla imzalar.

## Yeniden derleme

```bash
cd yanlis-renk
export ANDROID_HOME=/yol/android-sdk        # ya da local.properties: sdk.dir=...
./gradlew :logic:generateLevels             # (isteğe bağlı) bölüm verisini yeniden üret
./gradlew :logic:test :server:test          # testler
./gradlew :android:assembleDebug :android:assembleRelease :android:bundleRelease
xvfb-run ./gradlew :desktop:screenshots     # görsel QA (Linux)
./gradlew :desktop:run                      # masaüstünde oyna
./gradlew :server:run                       # zaman sunucusu (:8080)
```

Kullanılan sürümler: Kotlin 2.1.0, libGDX 1.13.1, AGP 8.7.3, Gradle 8.14.3, kotlinx.serialization 1.7.3, JDK 21 (hedef bytecode 17).
