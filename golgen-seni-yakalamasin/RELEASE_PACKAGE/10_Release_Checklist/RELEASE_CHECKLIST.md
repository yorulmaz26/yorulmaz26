# Google Play yayın kontrol listesi — Doppelglow / Gölgen Seni Yakalamasın 1.1.0 (versionCode 2)

Durum anahtarı: ✅ TAMAMLANDI / TEST EDİLDİ · 🔑 ERİŞİM GEREKLİ · ✋ MANUEL KONTROL GEREKLİ · ➖ UYGULANAMAZ

## Teknik
| Madde | Durum | Not |
|---|---|---|
| Hedef API seviyesi 36 (31 Ağu 2026 kuralı) | ✅ | `targetSdk 36`, `compileSdk 36`; Android 16 emülatöründe UI testi |
| Android App Bundle (AAB) | ✅ | CI `bundleRelease` → `01_Game_Build/AAB` |
| 64 bit / 16 KB sayfa boyutu | ✅ | APK'da native (.so) kütüphane yok; yalnızca Java/Kotlin → kural etkilemez |
| Paket adı / Application ID | ✅ | `com.golgen.oyun` (değiştirilmedi) |
| Sürüm kodu / adı | ✅ | 2 / 1.1.0 |
| Yükleme anahtarıyla imzalama | 🔑 | Anahtar depoda yok. `-PuploadStoreFile=… -PuploadStorePassword=… -PuploadKeyAlias=… -PuploadKeyPassword=…` ile derleyin; Play App Signing açın |
| AdMob gerçek kimlikleri | 🔑 | Şu an Google TEST kimlikleri. `-PadmobAppId -PadmobBannerId -PadmobInterstitialId -PadmobRewardedId` |
| `app-ads.txt` | 🔑 | Geliştirici web sitesinde yayınlanmalı |
| İzinler | ✅ | VIBRATE + AdMob/WorkManager'ın eklediği izinler (bkz. 09_Privacy_And_Legal) |
| SDK güncelliği | ✋ | play-services-ads 23.6.0, UMP 3.1.0; CI özetindeki "en güncel" sürümlerle karşılaştırın |

## Mağaza girişi
| Madde | Durum | Not |
|---|---|---|
| Uygulama adı, kısa/uzun açıklama (6 dil) | ✅ | `06_Store_Descriptions`, karakter sınırları betikle doğrulandı |
| Simge 512×512 | ✅ | `04_PlayStore/App_Icon/app_icon_512.png` |
| Kapak görseli 1024×500 (TR/EN) | ✅ | `04_PlayStore/Feature_Graphics` |
| Telefon ekran görüntüleri (6 dil × 8) | ✅ | `04_PlayStore/Phone_Screenshots` |
| Tablet 7"/10" ekran görüntüleri | ✅ | `04_PlayStore/Tablet_*` |
| Tanıtım videosu | ✋ | Play, YouTube bağlantısı ister: videoyu YouTube'a sizin yüklemeniz gerekir |
| Gizlilik politikası URL'si | 🔑 | Metin hazır (`09_Privacy_And_Legal`); herkese açık bir adreste yayınlanmalı, e-posta doldurulmalı |
| Veri güvenliği formu | ✋ | Taslak hazır; AdMob satırları Google'ın resmî sayfasıyla doğrulanmalı |
| İçerik derecelendirmesi (IARC) | ✋ | Önerilen yanıtlar hazır; anketi Play Console'da doldurun |
| Hedef kitle 13+ / reklam içerir: Evet / uygulama içi satın alma: Yok | ✋ | Play Console'da beyan |
| İngilizce adın marka araştırması | ✋ | WIPO/EUIPO/USPTO/TÜRKPATENT sorgulanamadı (`02_Game_Name_Research/Final_Name.md`) |

## Yayın
| Madde | Durum |
|---|---|
| Dahili test kanalında gerçek cihaz testi (performans, dokunma hissi, ses) | ✋ |
| Kapalı test (Play'in yeni kişisel hesaplar için 12 testçi × 14 gün kuralı geçerliyse) | ✋ |
| Üretime yayın | ✋ Yalnızca sizin onayınızla |
