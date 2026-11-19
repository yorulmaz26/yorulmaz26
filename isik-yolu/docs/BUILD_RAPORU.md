# Build Raporu — Işık Yolu 1.5.0

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

## 1.5.0 değişiklikleri

- **Magic Lumos Software stüdyo açılışı** (yaklaşık 3,2 sn, dokununca atlanır). Animasyon sırası:
  1. Karanlıkta bir ışık noktası yanar.
  2. Logo daire biçiminde aydınlanarak açılır ve hafifçe yakınlaşır.
  3. Kıvılcımlar saçılır.
  4. Yazının üzerinden ışık süzülür.
  5. Sahne kararır ve oyunun açılışına geçilir.
- **Logo:** kullanıcının verdiği görsel kullanıldı. Yazı ve devre çizgilerinin olduğu orta bölüm kesildi,
  kenarları şeffaflaştırılıp arka plan rengine (#06182D) eritildi.
  `res/drawable-nodpi/studio_logo.webp`: 1136×550, 90 KB.
- **Ses:** ışık logoya vurduğunda çalan, sentezlenmiş "lumos" sesi (`res/raw/intro.wav`).
- **Bekleme:** bölümler animasyon sırasında yüklendiği için oyun açılış ekranının bekleme süresi 1,9'dan
  1,5 saniyeye indirildi.
- **Sistem rengi:** pencere arka planı ve sistem çubukları açılışta logonun lacivert rengindedir.

## 1.4.0 değişiklikleri

- **Market** (yalnızca oyun içi para; gerçek para yok). Sekmeler: Teklifler, Güçlendiriciler, Canlar, Görünüm,
  Nasıl Kazanılır.
- **İki para birimi:**
  - Işık Kristali: başlangıçta 50.
  - Prizma Taşı: nadir.
- **Fiyatlar ve sınırlar:**
  - Aynı gün tekrar alınan ürünün fiyatı her alımda artar (ör. ipucu 60 → 80 → 100…).
  - Günlük alım sınırları vardır.
  - Görünümlerin bölüm şartı vardır.
  - Günlük 3 indirimli teklif (%20–40, tek adet).
- **Güvenilir gün:** can sistemindeki en ileri görülen zamana göre belirlenir. Saati geri almak sınırları ve
  teklifleri sıfırlamaz.
- **Ürünler:** ipucu, ipucu paketi, ek hamle bileti, can kalkanı, kristali taşa çevirme, +1 can, canları doldur.
- **Görünümler:** 4 ayna kaplaması, 4 tahta teması, 3 ışın efekti; önizlemeli, kalıcı, takılıp çıkarılabilir.
- **Kazanç:**
  - Bölümü ilk kez bitirmek: 4 + 2 × (dünya − 1) kristal.
  - İlk kez 3 yıldız: +3 kristal.
  - Grup 30/30: 1 taş.
  - Günlük bulmaca: 20 + seri bonusu (en fazla +30).
  - Başarılar: 30 kristal, büyük başarılar ayrıca taş.
  - Tekrar oynamak kazandırmaz.
- **Oyun içi:** hak bitince "+5 hak ile devam" (bilet ya da 80 → 160 → 320 kristal). İpucu kalmayınca ve can
  kalmayınca pencereden doğrudan satın alma.
- **Denge:** 500 bölümün bütün kazancı (5 500 kristal) bile kristalle satılan görünümlerin toplamına
  (6 000) yetmez.
- 2 yeni başarı: İlk Alışveriş, Koleksiyoncu (toplam 27).

## 1.3.0 değişiklikleri

- Bölüm modunda can sistemi: 4 can; 15 dakikada 1 can yenilenir (en fazla 4). Günlük bulmaca can harcamaz.
- Her bölümde hamle hakkı: 2 × en az hamle + 5. Geri Al hakkı iade etmez. Hak bitince deneme kaybedilir.
- İlk hamlede 1 can düşer, bölüm kazanılınca geri verilir. Hak bitince, yeniden başlayınca, bölümden
  çıkınca veya uygulama bölüm ortasında kapatılınca can geri gelmez.
- Saat hilesi koruması (`Lives`):
  - aynı açılışta yalnızca `elapsedRealtime` (kullanıcının değiştiremediği monoton saat) sayılır;
  - telefon yeniden başlatılırsa Android açılış sayacı, varsa Android 13+ ağ saati kullanılır;
  - saat daha önce görülen en ileri zamandan geri alınmışsa süre sayılmaz;
  - aynı açılışta duvar saati monoton saatten 2 dakikadan fazla saparsa değişiklik algılanır ve dikkate alınmaz.
- Arayüz: menüde, bölüm seçmede ve oyun ekranında kalp sayısı ve geri sayım; "Hamle hakkın bitti" ve
  "Canın kalmadı" pencereleri; oyun ekranında kalan hak (son 3 hakta kırmızı).

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
| `teslimat/IsikYolu-1.5.0.apk` (release, R8 küçültme + kaynak küçültme) | 1 984 317 bayt | `e42dda1e18a5946b38783733e30b0eaafc65cd1f9cff351ec6d27507502ec50f` |
| `teslimat/IsikYolu-1.5.0-debug.apk` | 2 098 078 bayt | `019740cdfba5dcf02ce4e605db33365aaa5b1fdceaf143506859d8d46d738859` |

- Paket: `com.isikyolu.game`, sürüm kodu 6, sürüm adı 1.5.0 (önceki sürümlerin üzerine kurulur, ilerleme korunur), etiket "Işık Yolu".
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
