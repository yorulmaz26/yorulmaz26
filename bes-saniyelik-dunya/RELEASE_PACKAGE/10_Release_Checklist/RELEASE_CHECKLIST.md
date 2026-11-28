# Yayın kontrol listesi — Stopwatch Shuffle / 5 Saniyelik Dünya (sürüm 1.1.0, kod 2)

Durum etiketleri: TAMAMLANDI · TEST EDİLDİ · EKSİK · ERİŞİM GEREKLİ · UYGULANAMAZ · MANUEL KONTROL GEREKLİ

## A. Paket (bu depoda yapılanlar)
| Madde | Durum | Not |
|---|---|---|
| Hedef API 36, compileSdk 36, minSdk 24 | TEST EDİLDİ | `aapt2 dump badging` |
| Android App Bundle | TAMAMLANDI | `01_Game_Build/AAB/` — hata ayıklama anahtarıyla imzalı |
| 64-bit (arm64-v8a, x86_64) + 16 KB sayfa hizası | TEST EDİLDİ | `zipalign -c -P 16`, `readelf` |
| İzinler | TEST EDİLDİ | VIBRATE, INTERNET, ACCESS_NETWORK_STATE + reklam kitaplığının ölçüm izinleri; `audience=child` iken AD_ID yok |
| Paket adı `com.besaniyelik.dunya` (değiştirilmedi) | TAMAMLANDI | kayıtlarla uyum korunur |
| Uygulama adı: Türkçe cihazda "5 Saniyelik Dünya", diğer dillerde "Stopwatch Shuffle" | TAMAMLANDI | `res/values`, `res/values-tr` |
| 6 dil, otomatik dil algılama, ayarlardan değiştirme | TEST EDİLDİ | LocalizationTest 9/9, eksik çeviri 0 |
| Kalıcı can sistemi (20 dk) | TEST EDİLDİ | LivesTest 16/16 |
| Gizlilik politikası (uygulama içi, 6 dil) | TAMAMLANDI | Ayarlar > Gizlilik Politikası |

## B. Sizin yapmanız gerekenler (onayınız/erişiminiz gerekir)
| Madde | Durum | Ne yapılacak |
|---|---|---|
| Yükleme anahtarı (upload key) | ERİŞİM GEREKLİ | `docs/play_store/YAYIN_REHBERI.md` §2.1 — anahtarı siz oluşturmalısınız, depoya konmaz |
| Google Play geliştirici hesabı ("Magic Lumos Software" adıyla) | ERİŞİM GEREKLİ | kimlik doğrulama, 25 USD |
| Gizlilik politikasını web'de yayımlama | ERİŞİM GEREKLİ | `09_Privacy_And_Legal/privacy_policy_tr_en.md`, iletişim e-postası eklenmeli |
| AdMob hesabı ve gerçek reklam kimlikleri (`ads.properties`) | ERİŞİM GEREKLİ | `docs/play_store/REKLAM_REHBERI.md` |
| Uygulama içeriği formları (veri güvenliği, reklam, hedef yaş, IARC) | MANUEL KONTROL GEREKLİ | yanıtlar `YAYIN_REHBERI.md` §2.4 ve `REKLAM_REHBERI.md` |
| Mağaza girişi: 6 dilde metin, simge, kapak, 8'er ekran görüntüsü, tablet görüntüleri | TAMAMLANDI (yükleme sizde) | `04_PlayStore`, `06_Store_Descriptions` |
| Tanıtım videosu (Play, YouTube bağlantısı ister) | ERİŞİM GEREKLİ | videoyu kendi YouTube kanalınıza yükleyip bağlantıyı girin; harici platforma yükleme yapılmadı |
| Marka tescil araştırması (TÜRKPATENT, EUIPO, USPTO) | MANUEL KONTROL GEREKLİ | `02_Game_Name_Research/Final_Name.md`: veritabanları bu ortamdan sorgulanamadı |
| Gerçek cihazda dahili test (açılış, dokunma, geri tuşu, çentik, reklamlar, canlar) | MANUEL KONTROL GEREKLİ | emülatör/cihaz yok (KVM yok) |
| Kapalı test: 12 kişi / 14 gün (yeni kişisel hesaplar) | ERİŞİM GEREKLİ | |
| Tablet ekran görüntüsü biçimi | MANUEL KONTROL GEREKLİ | 1920×1200 ve 2560×1600 (16:10) üretildi; Play Console 16:9 isterse yatay 16:9 telefon görüntüleri kullanılabilir |
