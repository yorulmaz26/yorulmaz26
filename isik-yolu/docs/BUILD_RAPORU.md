# Build Raporu — Işık Yolu 1.2.0

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

## 1.2.0 değişiklikleri

- Mantık bölümleri: 11–500 yeniden üretildi; 454 bölüm "MANTIK" bölümü. Bu bölümlerde ışını takip eden oyuncunun
  karar ağacı için en az yaprak şartı var, aynalar ışınlar arasında paylaşılıyor, daha çok yanıltıcı ayna bulunuyor.
- Aydınlık tema: daha açık indigo arka plan, açık lacivert kareler, kartlar ve butonlar; belirgin kabarık duvarlar;
  durum ve gezinme çubukları arka planla uyumlu; metin kontrastı artırıldı.
- Not: Eski kayıtlardaki yıldızlar bölüm numarasına bağlı kalır. 11–500 arası bölümlerin düzeni değiştiği için
  bu bölümleri yeniden oynamak gerekebilir; kilitler ve yıldızlar korunur.

## 1.1.0 değişiklikleri

- 500 bölüm (5 dünya). İlk 100 bölüm değişmedi.
- Yeni ayna tasarımı: 90° çentikli döner taban, metal çerçeveli cam bıçak, ışık vurunca dönen yansıma parıltısı; sabit aynalar cıvatalı çelik yuvada.
- İpucu kuralı: 3 ipucuyla başlanır, her 10 tamamlanan bölümde +1 (3 yıldız ödülü kaldırıldı).
- Tema: aurora bantlı arka plan, parlayan yıldızlar, cam efektli kartlar, parlaklık katmanlı butonlar, degrade başlık, renk geçişli tahta çerçevesi, dünya sekmeleri.
- 25 başarı (dünya başarıları ve yüksek yıldız hedefleri eklendi).

## Çıktılar

| Dosya | Boyut | SHA-256 |
|---|---|---|
| `teslimat/IsikYolu-1.2.0.apk` (release, R8 küçültme + kaynak küçültme) | 1 759 844 bayt | `499425bc8419e6784733cf0f0da69300212cc4fe10c87362699d1a9bcb0af447` |
| `teslimat/IsikYolu-1.2.0-debug.apk` | 1 854 679 bayt | `cef7f8ad610e5434f26ad878b20e2eb9c2bea0d028d35f9b352f91edf8b13ce3` |

- Paket: `com.isikyolu.game`, sürüm kodu 3, sürüm adı 1.2.0 (önceki sürümlerin üzerine kurulur, ilerleme korunur), etiket "Işık Yolu".
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
