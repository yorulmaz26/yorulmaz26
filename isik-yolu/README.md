# Işık Yolu

Işık kaynağından çıkan ışını aynalar, prizmalar, renk filtreleri, hareketli bloklar ve
kapı–anahtar düzenekleriyle hedeflere ulaştırdığın, bölüm geçmeli bir mantık bulmacası.
**500 bölüm**: 5 dünya × 10 grup × 10 bölüm.
Android için **Java** ile, **Android Studio / Gradle** projesi olarak geliştirildi; dikey (9:16),
tamamen çevrimdışı, Türkçe.

> Talimatta önce Unity 2D istendi, son satırda "Android Studio Java olarak" denildi. Oyun bu son
> isteğe göre yerel Android (Java) olarak yazıldı. Unity'deki karşılıkları aşağıdaki tabloda var.

## Hızlı başlangıç

| İş | Komut (proje klasöründe: `isik-yolu/`) |
|---|---|
| Hazır APK'yı telefona kur | `teslimat/IsikYolu-1.4.0.apk` (sürüm, R8 ile küçültülmüş) veya `teslimat/IsikYolu-1.4.0-debug.apk` |
| Android Studio'da aç | *File › Open* → `isik-yolu` klasörü |
| Debug APK | `./gradlew :app:assembleDebug` |
| Sürüm APK | `./gradlew :app:assembleRelease` |
| Motor testleri | `./gradlew :engine:test` |
| Arayüz testleri + ekran görüntüleri | `./gradlew :app:testDebugUnitTest` → `docs/ekranlar/*.png` |
| Bölümleri yeniden üret | `./gradlew :engine:generateLevels -Pfrom=1 -Pto=500` |
| Sesleri yeniden üret | `java tools/SoundGen.java app/src/main/res/raw` |

Gereksinimler: JDK 17+, Android SDK 34 (build-tools 34.0.0). `local.properties` içinde `sdk.dir` olmalı
(Android Studio bunu kendisi yazar). APK kurulumu için telefonda "Bilinmeyen kaynaklardan yükleme" izni gerekir.

## Oynanış

- **Aynalar:** turkuaz çentikli döner tabandaki aynaya dokun, 90° döner (`/` ↔ `\`). Cıvatalı çelik yuvadaki bronz çerçeveli aynalar sabittir.
- **Duvarlar** ve **kaynak kutuları** ışığı durdurur.
- **Renkli ışık:** hedef halka yalnızca kendi rengindeki ışıkla yanar. Işınlar hedefte karışır: kırmızı + yeşil = sarı,
  kırmızı + mavi = pembe, yeşil + mavi = camgöbeği. Halkadaki küçük noktalar karışımın bileşenlerini gösterir.
- **Renk filtresi:** yalnızca kendi bileşenlerini geçirir; beyaz ışık filtreden renkli çıkar.
- **Prizma:** ışığı ayrıştırır: kırmızı sola, yeşil düz, mavi sağa sapar.
- **Hareketli blok (altın):** sürükleyerek bir kare kaydırılır; ışığı keser.
- **Anahtar ve kapı:** ışık bir anahtar taşına değince aynı renkteki kapı açılır.
- Bütün hedefler aynı anda doğru renkte yandığında bölüm biter.
- **Yıldızlar:** optimum hamlede 3 ★, optimum + max(2, ⌈optimum/2⌉) hamleye kadar 2 ★, daha fazlası 1 ★.
  Optimum, her bölüm için BFS çözücüyle hesaplanmıştır. Geri Al hamle sayısını da geri alır.
- **İpucu:** 3 ipucuyla başlanır; **her 10 bölüm tamamlandığında +1 ipucu** kazanılır (aynı bölümü tekrar
  oynamak sayılmaz). İpucu, mevcut durumdan en kısa çözümün ilk hamlesini tahtada altın halkayla gösterir.

## Market

Oyun içi parayla çalışır; gerçek para yoktur. İki para birimi vardır:
- **Işık Kristali:** bölümleri ilk kez bitirerek, 3 yıldız alarak, günlük bulmacayla ve başarılarla kazanılır.
- **Prizma Taşı:** bir grubu 30/30 yıldızla bitirerek ve büyük başarılarla kazanılır.

Ekonomi bilerek dar tutuldu:
- Aynı gün tekrar alınan ürün pahalanır ve günlük sınırlar vardır.
- Görünümler bölüm şartı ister.
- Her gün 3 indirimli teklif gelir.
- Bütün oyunun kazancı bile bütün görünümlere yetmez.

Market günü saat hilesine karşı korumalıdır. Hak bitince bilet veya kristalle +5 hak ile devam edilebilir; her
devam fiyatı ikiye katlar. Ayrıntılar: `engine/Economy.java`, `docs/BUILD_RAPORU.md`.

## Canlar (bölüm modu)

- 4 canla başlanır; 15 dakikada 1 can yenilenir (en fazla 4). Günlük bulmaca can harcamaz.
- Her bölümde **hamle hakkı** vardır: 2 × en az hamle + 5. Geri Al hakkı iade etmez.
- Bir bölümde ilk hamleyi yapınca 1 can düşer, bölümü bitirince geri gelir. Can şu durumlarda geri gelmez:
  hak bittiğinde, yeniden başlayınca, bölümden çıkınca, uygulama bölüm ortasında kapatılınca.
- Süre, telefon saatine göre değil, kullanıcının değiştiremediği monoton sistem saatine göre ölçülür.
  Yeniden başlatmalarda açılış sayacı ve varsa ağ saati kullanılır; saati geri almak süre kazandırmaz
  (`engine/Lives.java`).

## Mantık bölümleri

Oyun ekranında **MANTIK** etiketiyle gösterilir: 1. dünyada her grubun son 6 bölümü (1. grup hariç) ve
101–500 arasının tamamı, toplam 454 bölüm. Bu bölümlerde bariz hamle genellikle çıkmaza götürür:
- yanıltıcı döner aynalar ışını uzun ve inandırıcı yollara saptırır,
- bazı aynaları iki ayrı ışın kullanır; birini düzeltmek ötekini bozabilir.

Üretici, ışını takip eden bir oyuncunun karar ağacını (`BeamFollower`) ölçer; ağacı küçük kalan adaylar
reddedilir. Ayrıntılar ve ölçümler: `docs/TEST_RAPORU.md`.

## Dünyalar

| Dünya | Bölümler | Zorluk | Özellik |
|---|---|---|---|
| Işık Bahçesi | 1–100 | Başlangıç | 10 mekanik sırayla öğretilir |
| Kristal Mağaralar | 101–200 | Zor | Aynı konular, daha uzun yollar, daha çok yanıltıcı ayna |
| Gece Bahçesi | 201–300 | Uzman | Her grupta ek düzenek (ek kaynak, filtre, blok veya kapı) |
| Yıldız Atölyesi | 301–400 | Usta | 8 sütunlu büyük tahtalar |
| Işık Tapınağı | 401–500 | Efsane | 8×11 tahtalar, en uzun çözümler |

Bölüm seçme ekranında dünyalar sekme olarak gösterilir; yeni dünyaya girişte tanıtım kartı açılır.

## Ekranlar

| # | Ekran | Sınıf |
|---|---|---|
| 1 | Açılış (animasyonlu amblem, bölümler arka planda yüklenir) | `screens/SplashScreen` |
| 2 | Ana menü (devam et, yıldız sayacı, kutucuklar) | `screens/MenuScreen` |
| 3 | Bölüm seçme (5 dünya sekmesi, her dünyada 10 grup × 10, yıldızlar, kilitler) | `screens/LevelSelectScreen` |
| 4 | Oyun (tahta, hamle sayacı, canlı yıldız göstergesi, Geri Al / Sıfırla / İpucu) | `screens/GameScreen` |
| 5 | Duraklatma (devam, yeniden başla, ses/müzik/titreşim) | `GameScreen#showPause` |
| 6 | Bölüm tamamlandı (yıldız animasyonu, sonraki bölüm) | `GameScreen#showComplete` |
| 7 | İpucu penceresi | `GameScreen#requestHint` |
| 8 | Günlük bulmaca (tarih, seri, son 7 gün) | `screens/DailyScreen` |
| 9 | Başarılar (27 başarı, ilerleme çubukları) | `screens/AchievementsScreen` |
| 10 | Ayarlar (ses, müzik, titreşim, öğretici, ilerlemeyi sıfırla) | `screens/SettingsScreen` |
| 11 | Market (teklifler, güçlendiriciler, canlar, görünümler, kazanç rehberi) | `screens/MarketScreen` |

Ayrıca: Türkçe öğretici (ilk açılışta 3 sayfa, her yeni mekanikte canlı örnek tahtalı tanıtım kartı,
menüde "Nasıl Oynanır?" ile 13 sayfa). Ekran görüntüleri: [`docs/ekranlar/`](docs/ekranlar).

## Mimari

```
isik-yolu/
  engine/   Saf Java bulmaca motoru (Android'e bağımlı değil, masaüstü JVM'de test edilir)
    Board          ızgara durumu, hamleler, 64 bitlik durum anahtarı
    BeamTracer     ışın simülasyonu: yansıma, renk, filtre, prizma, kapı sabit noktası, döngü koruması
    Solver         BFS en kısa çözüm (optimum hamle, ipucu); kutulama yapmayan özel hash tablosu
    GameSession    oturum: dokunma, sürükleme, geri alma, sıfırlama, ipucu, yıldız
    LevelGenerator çözümden geriye bölüm üretici + çözücü doğrulaması
    Chapters       5 dünya / 50 grup: adlar, konular, mekanik açıklamaları, üretici ayarları ve zorlaştırma
    DailyPuzzle    tarihten deterministik günlük bulmaca
    Progress, Achievements, StarRating, Json, Level, LevelPack
    tools/         GenerateLevels (bölüm üretimi), Ascii (metin çizimi)
  app/      Android uygulaması (Java, AndroidX'siz, tek Activity)
    MainActivity   ekran geçişleri, geri tuşu, bildirim şeridi
    Game           servisler: bölümler, SharedPreferences kaydı, ses, titreşim
    Audio          SoundPool efektleri + döngüsel MediaPlayer müziği
    screens/       10 ekran
    ui/            BoardView (Canvas çizimi + dokunma), BackgroundView, LogoView, StarsView,
                   Overlay, TutorialOverlay, Demos, Icon (vektör ikonlar), Palette, Ui
    assets/levels.json   500 bölüm (JSON, veri odaklı)
    res/raw/*.wav        sentezlenmiş sesler ve müzik
  tools/SoundGen.java    ses ve müzik sentezleyicisi
  teslimat/              hazır APK'lar
  docs/                  test raporu, build raporu, ekran görüntüleri
```

Motor görselden tamamen bağımsızdır: `BoardView` yalnızca `GameSession`'ı okur ve hamleleri
`Host` arayüzüyle iletir. Bölümler JSON'dur; yeni bölüm eklemek için `levels.json`'a satır eklemek yeterlidir.

### Işın simülasyonu

- Koordinatlar yarım hücre birimindedir (hücre merkezi `2x+1`); çizim parçaları doğrudan buradan üretilir.
- Ayna: `/` için sağ→yukarı, yukarı→sağ, aşağı→sol, sol→aşağı; `\` simetriği.
- Döngü koruması: (hücre, yön, renk) ziyaret tablosu. Optik her renk için tersinir olduğundan kaynaktan çıkan
  ışın kapalı döngüye giremez (test bunu 4000 rastgele yoğun düzende doğrular); tablo güvenlik ağıdır.
- Kapılar: en küçük sabit nokta. Hepsi kapalı başlar, anahtarı ışık alan kapılar açılır, değişiklik kalmayana kadar
  yeniden izlenir. Böylece bir kapı yalnızca kendi arkasındaki anahtarla kendini açamaz.

### Bölüm üretimi ve doğrulama

1. Kaynaktan rastgele bir ışık yolu kurulur: düz koşular, dönüşlerde ayna, plana göre filtre/prizma, isteğe bağlı
   birleşen ikinci ışın (renk karışımı). Yollar yalnızca dik açıyla kesişebilir.
2. Kapı yolun düz bir hücresine, anahtarı başka bir kaynağın yoluna; bloklar yolun üstüne konur.
3. Yol üzerindeki aynaların bir kısmı sabitlenir, kalanları karıştırılır; yol dışına yanıltıcı ayna, filtre ve duvar eklenir.
4. BFS ile doğrulanır. Bölüm şu durumlarda reddedilir: çözülemiyorsa, başlangıçta zaten çözülmüşse, optimum hedef
   aralıkta değilse, durum uzayı ipucu için fazla büyükse, kapı başlangıçta açıksa ya da düzen başka bir bölümün kopyasıysa.
5. Sonraki dünyalarda aynı ayarlar zorlaştırılır: daha büyük tahta, daha çok dönüş ve yanıltıcı nesne, daha
   yüksek optimum, 3. dünyadan itibaren grup başına ek düzenek. Bir bölüm istenen aralıkta üretilemezse aralık
   adım adım gevşetilir.
6. Tohumlar sabittir; aynı komut aynı 500 bölümü üretir. İlk 100 bölüm önceki sürümle birebir aynıdır, kayıtlar bozulmaz.

## Unity terimlerinin karşılıkları

| Unity | Bu proje |
|---|---|
| Sahne | `screens/*Screen` (tek Activity içinde animasyonlu geçiş) |
| Prefab | Tekrar kullanılan bileşenler: `BoardView`, `Overlay`, `StarsView`, `MiniStars`, `Ui.button`, `Demos` örnek tahtaları |
| ScriptableObject | `assets/levels.json` (bölüm verisi) + `Chapters` (grup ayarları) |
| LineRenderer | `BoardView#drawBeams`: üç katmanlı parıltı, akan foton noktaları, büyüme animasyonu |
| PlayerPrefs | `SharedPreferences` + motor içindeki `Progress` JSON'u |

## Performans

- Çizim sırasında bellek ayrılmaz; boyalar, `Path` ve `RectF` önceden oluşturulur. Parçacıklar sabit boyutlu havuzda tutulur (en fazla 280).
- Işın yeniden yalnızca hamleden sonra hesaplanır; değişmeyen ışın parçaları yeniden animasyona girmez.
- Arka plan 30 FPS, tahta 60 FPS (`postInvalidateOnAnimation`) çalışır. Tahta donanım katmanında çizilir.
- İpucu BFS'i arka plan iş parçacığında çalışır. En büyük bölümün durum uzayı 79 872'dir (sınır 80 000).

## Gizlilik

İnternet izni yoktur; reklam ve veri toplama yoktur. Yalnızca titreşim izni istenir.
