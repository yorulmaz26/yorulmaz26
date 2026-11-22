# Build Raporu — Işık Yolu 1.7.1

Tarih: 22 Kasım 2026 · Ortam: Linux bulut kapsayıcısı (4 çekirdek), OpenJDK 21, Gradle 8.14.3

## Araç zinciri

| Bileşen | Sürüm |
|---|---|
| Android Gradle Plugin | 8.13.2 |
| compileSdk / targetSdk | 36 / 36 (Android 16) |
| minSdk | 26 (Android 8.0) |
| Build tools | 35.0.0 |
| Java kaynak/hedef | 17 |
| Bağımlılıklar (uygulama) | `:engine` modülü, Google Mobile Ads 25.5.0, Google UMP 4.0.0 (reklam ve izin) |
| Test bağımlılıkları | JUnit 4.13.2, Robolectric 4.14.1 |

Unity bu ortamda kurulu değildi. Talimatın son satırındaki "Android Studio Java" isteğine göre proje
yerel Android (Java) olarak kuruldu. Android SDK, command-line tools ile `/opt/android-sdk` altına kuruldu.

## 1.7.1 değişiklikleri (Play Store malzemesi)

- `magaza/`: Play Store görselleri ve tanıtım videoları (ayrıntı: `magaza/MAGAZA.md`). Hiçbirinde reklam yoktur.
  - **Kayıt:** `PromoCaptureTest` oyunu Robolectric'te gerçek çizimle oynatır ve 30 FPS kare kaydeder. Yalnızca
    `-PpromoDir` verilince çalışır.
  - **Birleştirme:** `tools/magaza/magaza.py` kareleri başlık, dokunuş halkası, oyun müziği ve sesleriyle birleştirir.
  - **Denetim:** bağımsız denetimde (her bulgu 2 şüpheciyle) 17 bulgu doğrulandı ve hepsi giderildi.
    - Simge 32 bit yapıldı.
    - Ses efektleri 0,33 sn erken çalıyordu; senkron düzeltildi.
    - "Kusursuz!" penceresi videoda bir kez ve yıldızlar dolana dek gösteriliyor.
    - Cihaz çerçevesi kaldırıldı.
    - Başlık hizası sabitlendi.
    - İlk ekran görüntüsü bütün hedefler yanarken alındı.
    - YouTube'da reklamları kapatma adımları rehbere eklendi.
- **Oyun düzeltmeleri** (kayıt sırasında bulundu):
  - Bitiş penceresindeki yıldız parıltısı düz gri disk gibi çizilip kesiliyordu; artık yumuşak bir hale.
  - 360 dp genişlikte "Bölümler" başlığı ikiye bölünüyordu.
  - Günlük seri, bugünün bulmacası çözülene kadar 0 görünüyordu.

## 1.7.0 değişiklikleri (reklamlar)

- **Google AdMob reklamları:** Google Mobile Ads SDK 25.5.0 ve izin için Google UMP 4.0.0. Ayrıntı: README › Reklamlar.
  - **Banner:** büyük uyarlanabilir boyutta; ekranın en altında, içerikten ayraçla ayrı.
    - Menü, bölümler, günlük bulmaca ve başarılar ekranında görünür.
    - Oyun ekranında yoktur: sürekli dokunulan oyun düğmelerine yakın olurdu.
    - İlk 3 bölüm bitene kadar gösterilmez. Açılışta, markette ve ayarlarda yoktur.
    - Bir pencere açıkken gizlenir ve duraklatılır.
    - Hiç yüklenemezse artan aralıklarla yeniden denenir. Yenileme başarısız olursa eski reklam kalır.
  - **Geçiş (resim) reklamı:** yalnızca bölüm bittikten sonra, sonraki ekrana geçerken.
    - Öğretici 10 bölümde yoktur.
    - En az 3 bölüm ve 4 dakika arayla gösterilir; ödüllü videodan sonraki 3 dakikada gösterilmez.
  - **Ödüllü video:** isteğe bağlıdır. Ödüller: +1 can, +1 ipucu, ek hamle hakkı, +25 kristal.
    - Günlük sınırlar vardır; ödül yalnızca video sonuna kadar izlenince verilir.
  - İçerik "PG" ile sınırlıdır.
  - İzin olmadan reklam istenmez. Gerektiğinde Ayarlar'da gizlilik seçenekleri düğmesi çıkar.
- **Reklam kimlikleri:**
  - Varsayılan olarak Google'ın test kimlikleri kullanılır.
  - Gerçek kimlikler yalnızca release derlemesinde, `admobAppId`, `admobBanner`, `admobInterstitial` ve
    `admobRewarded` gradle özellikleriyle verilir.
  - `BuildConfig.AD_TEST_IDS` hangi kimliklerin kullanıldığını gösterir.
- **Android 16 (API 36) hedefi:** Google Play 31 Ağustos 2026'dan beri güncellemelerde bunu istiyor. AGP 8.7.3 →
  8.13.2, compileSdk ve targetSdk 36.
  - **Kenardan kenara çizim:** arka plan sistem çubuklarının altına uzanır; içerik çubukların iç boşluğu kadar
    içeride kalır. Stüdyo açılışında çubukların arkası logo rengindedir.
  - **Geri hareketi:** `OnBackInvokedCallback` ile karşılanır.
  - **Başlatma modu:** `singleTop`. `singleTask` simgeden dönüşte açık reklamı kapatıyordu.
  - **Ekran yönü:** uygulama `android:appCategory="game"` ile bildirildi. Android 16 oyunlarda büyük ekranda da dikey
    kilidi korur. Yine de yatay tablette ve bölünmüş ekranda oynanır, banner yeni genişliğe göre yeniden kurulur.
  - **Gezinme çubuğu:** pencere rengi saydam, kontrast perdesi kapalı. Çubuğun arkasını uygulama boyar; stüdyo
    açılışında logo rengindedir.
- **Gizlilik politikası:** Ayarlar'da uygulama içi metin; web için `docs/GIZLILIK.md` (`privacyPolicyUrl`).
- **Reklam kimlikleri:** dördü birlikte verilmezse derleme durur.
- **İzin isteği:** çevrimdışı ilk açılıştan sonra yeniden denenir.
- **Ödüllü video:** sonuç üç durumludur (kazanıldı / yarıda kapatıldı / açılamadı) ve her biri için doğru uyarı verilir.
- **Geçiş reklamı:** sayacı yalnızca reklam gerçekten gösterilince ilerler.
- **Banner:**
  - Görünmediği her durumda duraklatılır (yeni oyuncu, pencere altı, oyun ekranı, arka plan).
  - Duraklatılmışken yeniden deneme isteği yapılmaz.
  - Reklam dinleyicisi kapanan etkinliği tutmaz.
- **Market video düğmesi:** video yeniden yüklenirken yerinde "hazırlanıyor" yazar, liste kaymaz.
- **İzinler:** titreşime ek olarak internet ve ağ durumu eklendi. AD_ID izni SDK tarafından eklenir.
- Kurallar motor tarafında, `engine/AdPolicy.java`. İlerleme kaydına reklam sayaçları eklendi; eski kayıtlar okunur.

## 1.6.0 değişiklikleri (bölüm denetimi)

Bütün bölümler hem kodla (her bölümün ulaşılabilir bütün durumları) hem de çözüme bakmadan akıl yürüten oyuncu
ajanlarıyla oynandı. Bulunan sorunlar düzeltildi; ayrıntı: `docs/TEST_RAPORU.md` › Bölüm denetimi.

- **Mantık bölümleri artık düşünmeden geçilemiyor.** 454 mantık bölümünün 411'i "her döner aynaya bir kez dokun"
  ile (gerekirse blokları itip) bitiyordu, 55'i bu yolla 3 yıldız veriyordu. Çözüm yolunda zaten doğru duran döner
  aynalar (canlı yem) eklendi: önce yoldaki sabit aynalar döner yapıldı (372 ayna, optimum değişmez); yetmeyen 207
  bölümde bir yol aynası başlangıçta doğru bırakıldı (1 hamle kısaldı). 10 bölüm zorluk gevşetilmeden yeniden
  üretildi. Kısayol artık hiçbir mantık bölümünü bitirmiyor; ortalama karar ağacı 39 → 51 yaprak. Mantık
  bölümlerinde en az hamle ortalaması 7,41 → 6,97.
- Üretici ve günlük bulmaca aynı kuralı uyguluyor; denetime `kısayol` (HATA) ve `canlı-yem` (UYARI) eklendi.
- İşlevsiz döner aynalar sabitlendi (384 bölümde 1003 ayna; hiçbir durumda ışık almıyorlardı).
- **Akıllı ipucu:** Geri Al hak harcamadığı için gerekirse önce Geri Al önerir. Plan tek çok kaynaklı BFS ile ilk
  çözüm katmanında durur (ortalama 2,8 ms, yaklaşık 14 kat hızlı), canlı tahtayı değiştirmez. Eşitlikte daha çok
  geri alan yolu seçer, böylece yıldız kaybettirmez. Hak yetmeyecekse ipucu iade edilir ve "ipucu kullanıldı" sayılmaz.
- **Oyun akışı:**
  - Hak bitince geri/çıkış canı yakar; ertelenmiş pencereler ekran değişince iptal edilir.
  - Kazanınca başarılar hemen verilir; Başarılar ekranı artık ödülsüz işaretlemez.
  - Can 0'da "Tekrar Dene" bölümlere döner; can alınınca bölüm açılır.
- **Dokunma:**
  - Bir sürüklemede tek itme yapılır. Blok parmağın bıraktığı yerden kayar, sıçramaz.
  - Pencere açıkken süren dokunma hamle yapmaz; giriş kapanınca sürükleme düzgün biter.
  - Pencere kapanırken dokunuşlar yutulur. Düğmelerde çift tıklama koruması var; Geri Al'a art arda basılabilir.
- **Saat ve gün:**
  - Günlük bulmaca ve market günü güvenilir zamandan hesaplanır ve geri gitmez. Saati ya da saat dilimini
    değiştirmek günlük sınırları sıfırlamaz.
  - Gece yarısından önce başlanan günlük bulmaca ödülünü ve serisini korur.
- Sabit aynaya dokununca kırmızı halka ve açıklama gösterilir. Prizma metni "gidiş yönüne göre" yapıldı;
  öğretici 4 sayfa oldu.

## 1.5.2 değişiklikleri

- **Logonun kenarı artık belli olmuyor.** Logo resminin arka plan ışıltısı, yazı ve çizgiler silinerek
  çıkarıldı. Bu ışıltı logonun çevresinde elips biçiminde ekranın kenar rengine (#081A2E, resmin kendi kenar
  rengi) eritildi ve bütün ekranın arka planı yapıldı. Böylece kutu ya da bant şeklinde bir kenar kalmadı.
  Dosya: `res/drawable-nodpi/studio_bg.webp`, 4 KB.
- **Işık süzülmesi bütün ekrandan geçiyor:** çapraz ışık bandı yalnızca logonun üstünden değil, ekranın
  tamamından geçiyor; baştan sona ekranın her yerini kat ediyor.
- Pencere ve sistem çubuğu rengi #081A2E yapıldı.

## 1.5.1 değişiklikleri

- Stüdyo açılışı 1,8 kat yavaşlatıldı: toplam yaklaşık 5,8 sn. Tüm aşamalar aynı oranda uzar
  (`StudioIntroView.SLOW`); dokununca atlama aynen çalışır.

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
| `teslimat/IsikYolu-1.7.1.apk` (release, R8 küçültme + kaynak küçültme) | 4 410 475 bayt | `748bdf58a1b96a598784038232e4b427a9b1723ca9372fa4eeb5d400a980429f` |
| `teslimat/IsikYolu-1.7.1-debug.apk` | 9 099 693 bayt | `7f096797b13183040206c365be1a9e8977adf00890ad92e0085a3bc17696fa60` |

APK, reklam SDK'sı ve bağımlılıkları (AndroidX, Kotlin standart kitaplığı) yüzünden 2,0 MB'tan 4,4 MB'a büyüdü.

- Paket: `com.isikyolu.game`, sürüm kodu 11, sürüm adı 1.7.1 (önceki sürümlerin üzerine kurulur, ilerleme korunur), etiket "Işık Yolu".
- İzinler:
  - Uygulamanın kendisi: `VIBRATE`, `INTERNET`, `ACCESS_NETWORK_STATE`.
  - Reklam SDK'sının eklediği: `AD_ID`, `ACCESS_ADSERVICES_*`, `WAKE_LOCK`, `FOREGROUND_SERVICE`.
- İmza: iki APK da Android debug sertifikasıyla imzalı (`CN=Android Debug`). Test için doğrudan kurulabilir.
  Google Play'e yüklemek için kendi yükleme anahtarınla imzalanmalıdır.
- APK içeriği: `assets/levels.json` (500 bölüm), 11 WAV ses (müzik 1,4 MB), vektör ikon ve amblem.

## Komutlar ve sonuçlar

```
./gradlew clean :engine:test :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest
BUILD SUCCESSFUL in 5m 53s    (116 test geçti, 2 kayıt testi bilerek atlandı, 0 hata)
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
