# Veri güvenliği formu, reklam beyanı, içerik derecelendirmesi (TASLAK)

Kaynak: projedeki kod ve bağımlılıkların incelenmesi (play-services-ads 24.9.0, user-messaging-platform 3.2.0) ve birleştirilmiş manifest izinleri.
Google'ın resmî "AdMob Play veri açıklaması" sayfası (developers.google.com) bu ortamdan açılamadı → **MANUEL KONTROL GEREKLİ**: formu doldurmadan önce o sayfayla karşılaştırın.

## Uygulamanın kendisi
- Kişisel veri toplamaz, sunucuya veri göndermez. Oyun verisi yalnızca cihazda (SharedPreferences, Android yedeklemesine açık).
- İnternet kullanımı: reklamlar ve saat doğrulaması (google.com, kişisel veri yok).
- Yerel ağ: çok oyunculu (UDP yayın + TCP, yalnızca aynı ağ).

## Google Mobile Ads SDK (üçüncü taraf, Play formunda beyan edilir)
| Veri türü | Toplanır | Paylaşılır | Amaç |
|---|---|---|---|
| Cihaz veya diğer kimlikler (reklam kimliği) | Evet | Evet (Google) | Reklam, analiz, dolandırıcılık önleme |
| Yaklaşık konum (IP'den) | Evet | Evet | Reklam, dolandırıcılık önleme |
| Uygulama etkileşimleri (reklam etkileşimi) | Evet | Evet | Reklam, analiz |
| Kilitlenme günlükleri / tanılama | Evet | Evet | Analiz, performans |
- Veriler aktarımda şifrelenir (SDK HTTPS kullanır). Kullanıcı verilerin silinmesini isteyebilir mi: uygulamada hesap yok; reklam kimliği Android ayarlarından sıfırlanır.

## İzinler (birleştirilmiş manifest)
INTERNET, ACCESS_NETWORK_STATE (reklam + saat), ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE (yerel çok oyunculu), AD_ID ve ACCESS_ADSERVICES_* (AdMob), WAKE_LOCK, FOREGROUND_SERVICE (AdMob'un WorkManager bağımlılığı). Gereksiz izin yok; konum, kamera, depolama gibi hassas izin yok.

## Play Console beyanları
- **Reklam içerir:** Evet. **Uygulama içi satın alma:** Yok (Google Play Billing kullanılmıyor → UYGULANAMAZ).
- **Hedef yaş:** Öneri 13+ (13 yaş altı seçilirse Aileler politikası, çocuklara yönelik reklam ayarları ve sertifikalı reklam SDK'sı zorunlu olur).
- **İçerik derecelendirmesi (IARC anketi önerilen yanıtlar):** Şiddet: yok (yalnızca uzay aracı çarpışınca küçük patlama efekti, kan yok) · Korku/cinsellik/kumar/uyuşturucu: yok · Kullanıcı etkileşimi: yerel çok oyunculu, sohbet yok, kullanıcı içeriği yok · Konum paylaşımı: yok · Dijital satın alma: yok. Beklenen sonuç: PEGI 3 / ESRB Everyone / USK 0.
- **Gizlilik politikası URL'si:** Privacy_Policy_TR_EN.md bir web sayfasında yayınlanmalı → ERİŞİM GEREKLİ.
- **app-ads.txt:** geliştirici web sitesinde yayınlanmalı → ERİŞİM GEREKLİ.

## Üçüncü taraf materyal lisansları
- Müzik ve ses efektleri: oyun kodunda sentezlenir (SoundManager.kt), dış dosya/lisans yok.
- Grafikler: tamamı kodla çizilir; açılış logosu Magic Lumos Software'e ait (kullanıcı tarafından sağlandı).
- Yazı tipleri: Android sistem yazı tipleri (Roboto, Apache 2.0); görseller ve videolar bu yazı tipleriyle üretildi.
- Kütüphaneler: Google Mobile Ads SDK ve UMP (Google'ın koşulları), Kotlin (Apache 2.0).
