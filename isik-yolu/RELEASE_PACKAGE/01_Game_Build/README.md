# Derleme çıktıları — Prismway (Işık Yolu) 1.8.0 (versionCode 12)

| Dosya | Boyut | SHA-256 | Durum |
|---|---|---|---|
| `APK/Prismway-IsikYolu-1.8.0-test.apk` | 2,86 MB (2.861.637 bayt) | `bbac175006684c60a327039b0c2288f3c250ba0916acc4877b7d514977833c07` | Test APK'sı. R8 ile küçültülmüş release derlemesi, **debug anahtarıyla** imzalı (APK Signature Scheme v2). Telefona doğrudan kurulabilir. |
| `AAB/Prismway-IsikYolu-1.8.0-debug-signed.aab` | 5,18 MB (5.183.437 bayt) | `f61e42283879bdfe92b653709e5ae1c57f4accea5b41449bf45cd0f02da3689f` | Play biçiminde paket, ama **debug anahtarıyla imzalı**. **Play Console'a yüklenmemeli.** |

## Doğrulananlar

- **Kimlik:** `apksigner verify` başarılı. Paket `com.isikyolu.game`. targetSdk 36, minSdk 26.
- **16 KB:** yerel (.so) kütüphane yok. `zipalign -c -P 16 -v 4` → "Verification succesful".
- **Dil ve ad:** APK'da yalnızca 6 oyun dili var. Uygulama adı Türkçe telefonda "Işık Yolu", diğerlerinde "Prismway".
- **Reklam kimlikleri:** test kimlikleri (Google'ın resmî test reklam birimleri). Gerçek kimlikler verilmedi.

## Play'e yüklenecek AAB'yi üretmek (ERİŞİM GEREKLİ: yükleme anahtarı)

İmza anahtarı ve şifreler bu pakette yoktur, depoya da konmaz.

```
# 1) bir kez: yükleme anahtarı oluştur (şifreyi güvenli sakla)
keytool -genkeypair -v -keystore ~/prismway-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000

# 2) ~/.gradle/gradle.properties
uploadStoreFile=/home/<kullanıcı>/prismway-upload.jks
uploadStorePassword=<şifre>
uploadKeyAlias=upload
uploadKeyPassword=<şifre>
admobAppId=ca-app-pub-…~…        # gerçek AdMob kimlikleri (dördü birlikte)
admobBanner=ca-app-pub-…/…
admobInterstitial=ca-app-pub-…/…
admobRewarded=ca-app-pub-…/…
privacyPolicyUrl=https://…

# 3) derle
./gradlew :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```
