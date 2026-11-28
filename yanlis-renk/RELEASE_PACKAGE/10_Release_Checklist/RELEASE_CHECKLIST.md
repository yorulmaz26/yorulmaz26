# Yayın Kontrol Listesi — Miscolor / Yanlış Renk 1.4.0 (versionCode 15)

Durum kodları: TAMAMLANDI · TEST EDİLDİ · EKSİK · ERİŞİM GEREKLİ · UYGULANAMAZ · MANUEL KONTROL GEREKLİ

## Teknik (Google Play gereksinimleri — resmî kaynaklarla doğrulandı, 2026-11-27)
| Madde | Durum | Not |
|---|---|---|
| targetSdk 36 (Android 16) — yeni uygulama/güncelleme şartı 31.08.2026 | TEST EDİLDİ | aapt2: targetSdkVersion 36, compileSdk 36 |
| AAB formatı | TAMAMLANDI | 01_Game_Build/AAB |
| 64-bit yerel kitaplıklar | TEST EDİLDİ | arm64-v8a, x86_64 (+ armeabi-v7a, x86) |
| 16 KB sayfa boyutu (zorunluluk 01.02.2027) | TEST EDİLDİ | ELF LOAD hizası 0x4000; `zipalign -c -P 16` başarılı |
| Paket adı değişmedi | TAMAMLANDI | com.yanlisrenk.oyun |
| versionCode / versionName | TAMAMLANDI | 15 / 1.4.0 |
| Yayın imzası | ERİŞİM GEREKLİ | Paket hata ayıklama anahtarıyla imzalı. Kendi keystore'unuzla `keystore.properties` oluşturup yeniden derleyin (BUILD_REPORT.md). Play App Signing önerilir |
| Gerçek AdMob kimlikleri | ERİŞİM GEREKLİ | Şu an Google TEST kimlikleri. `-PadmobAppId=… -PadmobBanner=… -PadmobInterstitial=… -PadmobRewarded=…` ile derleyin |
| Reklam SDK güncelliği | TAMAMLANDI | play-services-ads 25.5.0 (23.x kullanımdan kaldırılmış), UMP 4.0.0 |
| Gereksiz izin | MANUEL KONTROL GEREKLİ | Oyun yalnız INTERNET+VIBRATE ister; diğerleri Ads SDK'dan. FOREGROUND_SERVICE için Console beyanı istenirse yanıtlayın |
| Uygulama içi satın alma / Billing | UYGULANAMAZ | Gerçek parayla satın alma yok |

## Play Console içerik bölümü
| Madde | Durum |
|---|---|
| Gizlilik politikası URL'si (09_Privacy_And_Legal taslağı yayımlanmalı) | MANUEL KONTROL GEREKLİ |
| Veri güvenliği formu (DATA_SAFETY_NOTES.md; tahminle doldurulmadı) | MANUEL KONTROL GEREKLİ |
| Reklam içerir beyanı = Evet | MANUEL KONTROL GEREKLİ |
| Reklam kimliği beyanı | MANUEL KONTROL GEREKLİ |
| İçerik derecelendirmesi (IARC anketi) | MANUEL KONTROL GEREKLİ |
| Hedef kitle / yaş grubu (çocuk seçilirse Aileler politikası) | MANUEL KONTROL GEREKLİ |
| Marka hukuk taraması (Miscolor) | MANUEL KONTROL GEREKLİ |

## Mağaza varlıkları
| Madde | Durum |
|---|---|
| Uygulama simgesi 512×512, 32-bit PNG, 277 KB | TAMAMLANDI |
| Öne çıkan grafik TR/EN 1024×500, saydamlık yok | TAMAMLANDI |
| Telefon ekran görüntüleri 6 dil × 8 = 48 (1080×1920, ilk 3 oynanış) | TAMAMLANDI |
| Tablet 7"/10" (gerçek tablet oranı oyun çıktısı, 5×2 dil her boyut) | TAMAMLANDI |
| Mağaza metinleri 6 dil, karakter sınırları | TEST EDİLDİ (06_Store_Descriptions/LIMIT_CHECK.md) |
| Tanıtım videosu: Play "promo video" alanı YouTube bağlantısı ister | MANUEL KONTROL GEREKLİ (yükleme sizin onayınızla) |

## Yükleme / yayın
Yayınlama ve harici platformlara yükleme YAPILMADI (açık onayınız gerekir).
