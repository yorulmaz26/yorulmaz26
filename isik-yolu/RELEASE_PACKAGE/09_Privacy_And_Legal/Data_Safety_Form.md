# Google Play — Veri güvenliği formu (önerilen yanıtlar)

**Hazırlanma:** 28 Kasım 2026 · **Uygulama sürümü:** 1.8.0 (12) · **Paket:** `com.isikyolu.game`

Bu yanıtlar tahmin değildir. İki kaynağa dayanır:
1. **Projenin gerçek bağımlılık analizi:** `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`.
   - Doğrudan eklenen üçüncü taraf SDK'lar yalnızca **Google Mobile Ads SDK 25.5.0**
     (`com.google.android.gms:play-services-ads`) ve **User Messaging Platform 4.0.0**
     (`com.google.android.ump:user-messaging-platform`).
   - Diğerleri bunların getirdiği androidx ve Play services kütüphaneleri.
   - Oyunun kendi kodu ağa hiçbir veri göndermez: projede sunucu, analiz, çökme raporlama, hesap ya da ödeme SDK'sı
     yok.
2. **Google'ın resmî açıklaması:**
   - https://developers.google.com/admob/android/privacy/play-data-disclosure
   - 28.11.2026'da okundu; sayfa SDK'nın son sürümü 25.5.0'ı anlatıyor ve 26.11.2026'da güncellenmiş.

> Geliştirici olarak forma vereceğin yanıtların sorumluluğu sende. Google da bunu açıkça belirtiyor. SDK sürümü
> değişirse yukarıdaki sayfayı yeniden kontrol et.

## 1. Genel sorular

| Soru | Yanıt | Gerekçe |
|---|---|---|
| Uygulamanız, gerekli kullanıcı veri türlerinden herhangi birini topluyor veya paylaşıyor mu? | **Evet** | AdMob SDK verileri otomatik olarak toplar ve paylaşır. |
| Toplanan tüm kullanıcı verileri aktarım sırasında şifreleniyor mu? | **Evet** | Google: "All of the user data collected by Google Mobile Ads SDK is encrypted in transit using TLS." |
| Kullanıcıların verilerinin silinmesini isteyebileceği bir yol sağlıyor musunuz? | **Hayır** (oyunun sunucusu yok) | Oyunun kendi verisi yalnızca cihazdadır, "İlerlemeyi Sıfırla" ya da kaldırma siler. Google verileri için Google'ın kendi araçları geçerlidir. Formda bu durumu açıklama alanına yaz. |
| Hesap oluşturma | **Yok** | Uygulamada hesap yok. |

## 2. Veri türleri (AdMob SDK)

| Veri türü (Play formu) | Toplanıyor | Paylaşılıyor | Amaçlar | İsteğe bağlı mı? | Kaynak ifadesi |
|---|---|---|---|---|---|
| **Konum › Yaklaşık konum** | Evet | Evet | Reklam veya pazarlama, Analiz, Dolandırıcılığı önleme/güvenlik/uyumluluk | Hayır (zorunlu) | "Collects device's IP address, which may be used to estimate the general location of a device." Play: "Approximate location that is inferred, such as via IP address ... must be disclosed." |
| **Uygulama etkinliği › Uygulama etkileşimleri** | Evet | Evet | Reklam veya pazarlama, Analiz, Dolandırıcılığı önleme | Hayır | "Collects user product interactions ... including app launch, taps, and video views." |
| **Uygulama bilgileri ve performansı › Kilitlenme günlükleri** | Evet | Evet | Analiz, Dolandırıcılığı önleme | Hayır | "crash logs" |
| **Uygulama bilgileri ve performansı › Tanılama** | Evet | Evet | Analiz, Dolandırıcılığı önleme | Hayır | "app launch time, hang rate, and energy usage" |
| **Cihaz veya diğer kimlikler** | Evet | Evet | Reklam veya pazarlama, Analiz, Dolandırıcılığı önleme | Kullanıcı reklam kimliğini sıfırlayabilir/silebilir | "Collects Android advertising (ad) ID, app set ID ..." |

Toplanmayanlar: ad, e-posta, telefon, adres, kesin konum, kişiler, fotoğraf/video, ses, dosyalar, takvim, SMS,
arama geçmişi, sağlık, finans ve ödeme bilgileri, web tarama geçmişi.
- Oyun bunları istemez.
- Manifestte bunlara ait izin yok. Birleşik manifestteki izinler `08_Test_Reports/Play_Requirements.md` içinde.

**Geçici işleme (ephemeral):** Play tanımına göre reklam profili oluşturmak için kullanılan veri geçici sayılamaz.
Bu nedenle yukarıdaki türler "geçici" olarak işaretlenmemeli.

**Paylaşım istisnası:** Play'e göre birden çok müşteri için reklam profili oluşturan SDK "hizmet sağlayıcı"
sayılmaz. Bu yüzden veriler **paylaşılıyor** olarak işaretlenir.

## 3. Reklam kimliği beyanı (Play Console › Uygulama içeriği › Reklam kimliği)

- Uygulama reklam kimliği kullanıyor mu? **Evet.**
- Amaç: **Reklam veya pazarlama** ve **Analiz**.
- Birleşik manifestte `com.google.android.gms.permission.AD_ID` izni var. Bu izin AdMob SDK'dan gelir ve
  `aapt2 dump permissions` ile doğrulandı.

## 4. Reklamlar beyanı

- "Uygulamanızda reklam var mı?" **Evet.**
- Biçimler: banner, geçiş reklamı, ödüllü video.
- Reklam içeriği uygulama kodunda en fazla "PG" ile sınırlandırıldı (`AdMobProvider`).

## 5. Uygulama içi satın alma

**Yok.** Google Play Billing kütüphanesi projede yok. Market yalnızca oyun içi kristalle çalışır.
