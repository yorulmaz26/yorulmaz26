# Google Play "Veri güvenliği" formu için teknik notlar (TAHMİNLE DOLDURULMADI)

Durum: **MANUEL KONTROL GEREKLİ** — Formu Play Console'da geliştirici doldurmalıdır. Aşağıdakiler yalnızca kaynak koddan ve Google'ın resmî belgesinden çıkarılmış olgulardır.

## Uygulamanın kendi kodu (1.4.0)
| Konu | Olgu (kaynak) |
|---|---|
| Hesap / giriş | Yok |
| Geliştiriciye gönderilen veri | Yok. Kayıt dosyası yalnızca cihazda (SaveManager, AtomicFileStorage) |
| Ağ istekleri | AdMob SDK + zaman doğrulama: `https://www.google.com/generate_204`, `https://www.cloudflare.com/cdn-cgi/trace` — yalnızca `Date` başlığı okunur, gövdeye veri yazılmaz (AndroidLauncher.kt) |
| Hata kaydı | Çökme metni yalnızca cihazda `son_hata.txt` dosyasına yazılır; kullanıcı isterse kendisi kopyalar. Otomatik gönderim yok |
| İzinler (birleşik manifest) | INTERNET, VIBRATE (oyun) · ACCESS_NETWORK_STATE, AD_ID, ACCESS_ADSERVICES_AD_ID/ATTRIBUTION/TOPICS, WAKE_LOCK, FOREGROUND_SERVICE, DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION (Google Mobile Ads / Play services kütüphanelerinden) |
| Uygulama içi satın alma | Yok (Billing kütüphanesi yok) |

## Üçüncü taraf SDK: Google Mobile Ads SDK 25.5.0 (+ UMP 4.0.0)
Google'ın resmî açıklaması (erişim 2026-11-28): https://developers.google.com/admob/android/privacy/play-data-disclosure
- SDK otomatik olarak IP adresi, kullanıcı ürün etkileşimleri, teşhis bilgileri, cihaz ve hesap kimliklerini (reklam kimliği) **toplar ve paylaşır**; amaç: reklam/pazarlama, analiz, dolandırıcılığı önleme. Aktarımda TLS ile şifrelenir.
- Sayfa, verilerin en güncel SDK sürümü için listelendiğini belirtir; geliştirici kullandığı sürüme göre teyit etmelidir.
- UMP için ayrıca: https://developers.google.com/admob/ump/android/play-data-disclosure

## Play Console'da yapılacaklar
1. Veri güvenliği formunu yukarıdaki resmî sayfaya göre doldurun (tahmin yok).
2. "Reklam içerir" = Evet.
3. Reklam kimliği beyanı (Advertising ID declaration): AD_ID izni var → "Evet, reklam için" (doğrulayın).
4. Hedef kitle ve içerik: hedef yaş grubunu seçin; 13 yaş altı seçilirse Aileler politikası + AdMob çocuk ayarları (tagForChildDirectedTreatment) gerekir — kod şu an bu bayrağı AYARLAMIYOR.
5. İçerik derecelendirmesi (IARC) anketini doldurun: şiddet yok, kumar yok, kullanıcı etkileşimi/sohbet yok, reklam var.
6. Gizlilik politikası URL'si: PRIVACY_POLICY_*.md metnini bir web adresinde yayımlayın.
7. FOREGROUND_SERVICE izni kütüphaneden geliyor; Play Console ön plan hizmeti beyanı isterse Google Mobile Ads belgesine göre yanıtlayın.
