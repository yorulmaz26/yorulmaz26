# Build Raporu — Işık Yolu 1.1.0

Tarih: 18 Kasım 2026 · Ortam: Linux bulut kapsayıcısı (4 çekirdek), OpenJDK 21, Gradle 8.14.3

## Araç zinciri

| Bileşen | Sürüm |
|---|---|
| Android Gradle Plugin | 8.7.3 |
| compileSdk / targetSdk | 34 |
| minSdk | 26 (Android 8.0) |
| Build tools | 34.0.0 |
| Java kaynak/hedef | 17 |
| Bağımlılıklar (uygulama) | yok (yalnızca Android çerçevesi + `:engine` modülü) |
| Test bağımlılıkları | JUnit 4.13.2, Robolectric 4.14.1 |

Unity bu ortamda kurulu değildi. Talimatın son satırındaki "Android Studio Java" isteğine göre proje
yerel Android (Java) olarak kuruldu. Android SDK, command-line tools ile `/opt/android-sdk` altına kuruldu.

## 1.1.0 değişiklikleri

- 500 bölüm (5 dünya). İlk 100 bölüm değişmedi.
- Yeni ayna tasarımı: 90° çentikli döner taban, metal çerçeveli cam bıçak, ışık vurunca dönen yansıma parıltısı; sabit aynalar cıvatalı çelik yuvada.
- İpucu kuralı: 3 ipucuyla başlanır, her 10 tamamlanan bölümde +1 (3 yıldız ödülü kaldırıldı).
- Tema: aurora bantlı arka plan, parlayan yıldızlar, cam efektli kartlar, parlaklık katmanlı butonlar, degrade başlık, renk geçişli tahta çerçevesi, dünya sekmeleri.
- 25 başarı (dünya başarıları ve yüksek yıldız hedefleri eklendi).

## Çıktılar

| Dosya | Boyut | SHA-256 |
|---|---|---|
| `teslimat/IsikYolu-1.1.0.apk` (release, R8 küçültme + kaynak küçültme) | 1 756 176 bayt | `a8411f87d3c367744daee7de8213fc3af6799789242979f0fe3c7c9cefce0347` |
| `teslimat/IsikYolu-1.1.0-debug.apk` | 1 846 963 bayt | `9c93fe621da4ef48e751e78b34e31977de65c9e7f46648ab442f074b262cadb1` |

- Paket: `com.isikyolu.game`, sürüm kodu 2, sürüm adı 1.1.0 (1.0.0'ın üzerine kurulur, ilerleme korunur), etiket "Işık Yolu".
- İzinler: yalnızca `android.permission.VIBRATE`.
- İmza: iki APK da Android debug sertifikasıyla imzalı (`CN=Android Debug`). Test için doğrudan kurulabilir.
  Google Play'e yüklemek için kendi yükleme anahtarınla imzalanmalıdır.
- APK içeriği: `assets/levels.json` (500 bölüm), 11 WAV ses (müzik 1,4 MB), vektör ikon ve amblem.

## Komutlar ve sonuçlar

```
./gradlew clean :engine:test :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest
BUILD SUCCESSFUL in 1m 6s
```

`./gradlew :app:lintDebug` sonucu: **0 hata**, 32 uyarı. Uyarılar şu türden:
yalnızca Türkçe olduğu için kodda duran metinler (`SetTextI18n`), kodla oluşturulan özel görünümlerde XML
yapıcısı olmaması (`ViewConstructor`), dikey ekran kilidi (tasarım gereği), özel açılış ekranı. Bunlar bilinçli
seçimler; çalışmayı etkilemez.

## Sürekli entegrasyon

`.github/workflows/isik-yolu.yml`: `isik-yolu/` değiştiğinde motor testlerini, debug ve release APK'yı,
Robolectric arayüz testlerini çalıştırır. APK'ları ve test raporlarını artefakt olarak yükler.

## Ortam notları

- Android SDK'yı (`dl.google.com`) ve Robolectric'in büyük yerel grafik paketlerini (`gcp.mirrors.maven.org`)
  indirmek için bulut ortamının ağ izinlerine bu iki alan adı eklendi.
- Kapsayıcıda KVM yok; bu yüzden Android emülatörü çalıştırılamadı. Uygulama gerçek cihazda değil, Robolectric ile
  JVM üzerinde gerçek Android çizim hattıyla çalıştırıldı (ayrıntılar test raporunda). İlk cihaz denemesi kullanıcıdadır.
