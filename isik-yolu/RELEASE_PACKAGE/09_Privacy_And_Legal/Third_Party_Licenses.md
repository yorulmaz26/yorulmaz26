# Üçüncü taraf bileşenler ve lisanslar

**Hazırlanma:** 28 Kasım 2026, sürüm 1.8.0 (12).

## Uygulamaya giren bileşenler

Kaynak: release çalışma zamanı bağımlılık ağacı.

| Bileşen | Sürüm | Lisans / koşullar | Not |
|---|---|---|---|
| Google Mobile Ads SDK (`play-services-ads`) | 25.5.0 | Google APIs Terms of Service, AdMob politikaları | Reklam gösterimi. Ücretsiz ticari kullanım; AdMob hesabı ve politikalarına uyum gerekir. |
| Google User Messaging Platform (`user-messaging-platform`) | 4.0.0 | Google APIs Terms of Service | AB/İngiltere izin formu. |
| Play services temel kütüphaneleri (`basement`, `base`, `tasks`, `ads-identifier`, `appset`, `measurement-*`) | 18.x / 16.x / 20.1.2 | Google APIs Terms of Service | AdMob'un getirdiği bağımlılıklar. |
| AndroidX (core, activity, fragment, lifecycle, work, room, sqlite, startup, browser, webkit, privacysandbox ads …) | çeşitli | Apache License 2.0 | AdMob'un getirdiği bağımlılıklar. |
| Kotlin standart kütüphanesi (dolaylı) | — | Apache License 2.0 | androidx core-ktx üzerinden. |
| Oyun motoru, arayüz, bölümler, grafikler | — | Magic Lumos Software (kendi eseri) | Tüm kod ve 500 bölüm proje içinde üretildi. |
| Müzik ve ses efektleri (`res/raw`) | — | Magic Lumos Software (kendi eseri) | `tools/SoundGen.java` ile sentezlendi. Ticari kullanım engeli yok; örnek (sample) ya da lisanslı müzik içermez. |
| Yazı tipi (oyun içi) | — | Cihazın sistem yazı tipi | APK'ya yazı tipi dosyası eklenmedi. |

## Mağaza materyallerinde kullanılanlar

APK'ya girmez.

| Bileşen | Lisans | Kullanım |
|---|---|---|
| Roboto yazı tipi (Black, Bold, Medium) | Apache License 2.0 | Logolar, mağaza görselleri, video yazıları. Ticari kullanıma uygun. |
| Oyun ekran görüntüleri ve videolardaki oynanış | Magic Lumos Software | Oyunun gerçek görüntüsü. |
| Ses (videolar) | Magic Lumos Software | Oyunun kendi müziği ve efektleri. |

## Üretim araçları

Dağıtılmaz.

- **FFmpeg (libx264, AAC):** Videoları kodlamak için kullanıldı. Araç dağıtılmaz; üretilen MP4 dosyaları araç
  lisansına bağlı değildir.
  - H.264 için not: internette son kullanıcıya ücretsiz sunulan video için Via LA (eski MPEG LA) koşulları telif
    ücreti istemez. Ücretli dağıtım planlanırsa güncel koşullar kontrol edilmeli.
- **Python (Pillow, NumPy):** Görsel üretimi. Dağıtılmaz.
- **Robolectric:** Ekran kayıtları için gerçek oyun görüntüsünü çizer. Yalnızca test bağımlılığıdır.

## Uygulama içi bildirim

Apache 2.0 bileşenleri, AdMob ile gelen kütüphanelerin içinde kendi lisans bilgileriyle bulunur. Uygulamaya ayrıca
bir "açık kaynak lisansları" ekranı eklenmedi.

**Manuel öneri:** Ayarlar'a bir lisans ekranı eklemek ya da bu dosyayı gizlilik politikasıyla birlikte yayımlamak.
