# 5 Saniyelik Dünya — Mimari

Kotlin + libGDX 1.13.1 + Box2D, **yalnızca yatay** (sensorLandscape, 16:9 ve daha geniş) Android oyunu. Oyun mantığı arayüzden tamamen ayrıdır: simülasyon, ilerleme ve bölüm üretimi saf Kotlin/Box2D kodudur ve grafik bağlamı olmadan test edilir.

## Modüller

| Modül | İçerik |
|---|---|
| `core` | Bütün oyun kodu (simülasyon, bölümler, sonsuz mod, ilerleme, görüntüleme, arayüz) ve testler |
| `android` | `AndroidLauncher` (titreşim, sürükleyici tam ekran), manifest, simgeler, imzalama yapılandırması |
| `desktop` | Geliştirme ve otomatik ekran görüntüsü/oynatma turu için LWJGL3 başlatıcı |
| `assets` | Atlaslar, arka planlar, yazı tipi, sesler, müzikler, `levels/map_XX.json` (500 bölüm) |
| `tools` | `art/gen_art.py` (bütün grafikler, programatik), `audio/gen_audio.py` (bütün sesler/müzikler, sentez) |

## Paketler (`core/src/main/kotlin/com/besaniyelik/dunya`)

```
sim/        Simulation (GameManager + PhysicsManager), PlayerController, Mechanic, RuleEffects,
            RuleCompatibility (WorldRuleManager + RuleCompatibilityManager), Entities (ObstacleManager varlıkları), Physics sabitleri
level/      LevelModel (JSON veri modeli), LevelRepository (LevelManager), LevelGenerator + LevelBuilder + Patterns (LevelGenerator),
            LevelSolver + LevelForge (LevelValidator), GameContent (haritalar, aşama fikirleri, zorluk eğrisi)
endless/    EndlessGenerator (EndlessWorldManager), ScoreCalculator
progress/   SaveData, SaveManager, Catalog (karakterler, izler, başarılar, görev havuzları, giriş ödülleri),
            Managers (Progression/Reward/Mission/Achievement/Character/Shop/Collection), ProfileService (tek giriş noktası)
audio/      AudioManager
render/     WorldRenderer, CharacterRenderer (parçalardan karakter + animasyon durumları), Particles (nesne havuzu)
ui/         UiKit (UIManager: buton, panel, çubuk), screens/ (20 ekran), Assets (atlas/yazı tipi/tema yönetimi)
```

## Simülasyon

* Sabit 60 Hz tick. Her tick: içerik akışı (sonsuz mod) → kural aşaması → zaman ölçekli `dt` → hareketli/hayalet platformlar → oyuncu girişi → `World.step` → etkileşimler (engel, ped, toplanabilir, kontrol noktası, kovalayan duvar, bitiş).
* **Oyuncu çarpışma kutusu** gövdeye uygun dikdörtgendir (`Physics.PLAYER_W` × `PLAYER_H` = 0,85 × 1,42 karo); karakter `PLAYER_VISUAL_H` = 1,55 karo boyunda, ayakları kutunun tabanına oturacak şekilde çizilir. Sürekli çarpışma kapalı olduğundan hızlı düşüşteki gömülme, yüzeye gerçekten üstten/alttan gelindiyse her adımda dışarı itilerek giderilir (yandan gelindiğinde itilmez: karakter dar boşlukların üstünden koşamaz). `AuditTest` 500 bölümde çizilen karakter ile çizilen engellerin ölmeden üst üste binmediğini denetler.
* **Box2D** oyuncu (dinamik, döndürülmez, pahlı kutu) ile katı platformlar (statik/kinematik kutular) arasındaki çarpışmayı çözer. Zemin, engel ve toplanabilir kontrolleri durumsuz geometrik sorgulardır; bu sayede **anlık görüntü / geri yükleme** tamamen deterministiktir (geri yüklemede oyuncu gövdesi yeniden kurulur, temas geçmişi sızmaz; hareketli platformlar her tick tam hedef konumlarına oturtulur).
* **Kural aşamaları konuma bağlıdır**: her aşama, o kuralın hızıyla 5 saniyede alınan mesafedir. Oyuncu engele takılsa bile geometri ile kurallar hiç kaymaz; HUD kalan süreyi saniye olarak gösterir; 1,2 sn önce uyarı verilir.
* Kontrol iyileştirmeleri: coyote time (0,1 sn), zıplama tamponu (0,12 sn), basılı tutmaya göre değişken zıplama yüksekliği, ters yer çekiminde ayna kontrol. "Yerde olma" üstünde durulan platforma göre ölçülür (yükselen platformda da zıplanır).
* Kontrol noktası bayrağı geçildiği anda (havada olsa da) kaydedilir; yeniden doğuş her zaman bayrağın dibinde, yerde, standart duruşla olur. Sonsuz Dünya'da ekstra can koşuyu geri sarmaz, son güvenli zeminde diriltir. Kalkan kurtarması son güvenli zeminin 2–6 karo gerisine, aynı kural aşamasına koyar.
* Aynı kurallı ardışık aşamalar kural değişimi sayılmaz (uyarı/ses/sayaç yalnızca kurallar gerçekten değişince).
* Adalet kuralları: dar geçitte "Küçük Dünya" biterse büyüme yer açılana kadar ertelenir; hayalet platform oyuncunun içindeyken geri gelmez; kural sınırlarında tampon düzlükler (yer çekimi değişiminde 1 sn) vardır.

## On mekanik ve uyumluluk

`Mechanic` her mekaniği bir **grup** (aynı fiziksel parametreyi değiştirenler) ve **öncelik** ile tanımlar. `RuleCompatibility` matrisi: aynı grup = NO (ör. Hızlı + Yavaş, Hayalet + Değişen), bazı çiftler HARD (yalnız zorluk ≥ 7, ör. Ters Yer Çekimi + Ters Kontrol). `resolve()` çakışmada yüksek önceliği tutar; `RuleEffects.of()` bütün etkileri tek yerde hesaplar, böylece iki kural aynı parametreyi farklı değerlerle değiştiremez.

| # | Mekanik | Etki | Açılış |
|---|---|---|---|
| 1 | Normal | — | 1 |
| 2 | Ters Yer Çekimi | yer çekimi tersine, oyuncu tavanda | 11 |
| 3 | Hızlı Dünya | zaman ölçeği ×1,3 | 21 |
| 4 | Buz | yavaş ivmelenme, ×1,3 azami hız | 31 |
| 5 | Hayalet | mor platformlar 2,7 sn ritimle kaybolur (iki faz grubu) | 41 |
| 6 | Zıplayan | zıplama ×1,38 | 51 |
| 7 | Küçük | boyut ×0,55, alçak geçitler | 61 |
| 8 | Ters Kontrol | karakter kendiliğinden zıplar; basılı tutmak yerde tutar / zıplamayı kısaltır | 71 |
| 9 | Yavaş Zaman | zaman ölçeği ×0,62 | 81 |
| 10 | Değişen Platformlar | oklu platformlar dikey salınır | 91 |

## Bölüm üretimi, ölçüm ve zorluk kalibrasyonu (`:core:generateLevels`)

1. `PhasePlanner` aşama kurallarını planlar (1–10 yalnız Normal; 11–100 odak mekanik + eskiler; 101+ harita odaklı 2–3'lü kombinasyonlar; finallerde bütün mekanikler).
2. `LevelGenerator` aşama sınırlarını hesaplar ve 17 desenden aşama kuralına uygun olanları yerleştirir (`fillPhase`). Önemli kurallar:
   * **Bitiş düzlüğü:** son kural aşamasından sonra her bölümde 12 karoluk, her zaman Normal Dünya olan bir düzlük vardır (`Physics.FINISH_LEN`); karakter kapıya yerde, normal boyda ve normal yer çekiminde varır.
   * **Kural çizgisi tamponları** mesafeye ve zorluğa duyarlıdır (`bufferIn` / `bufferOut`): yer çekimi dönüşünde karşı yüzeye iniş mesafesi + tepki süresi (zorlukla 1,05 → 0,65 sn; mekaniğin tanıtıldığı bölümde 1,5 sn).
   * Desenler gerçek hıza göre boyutlanır (`PatternCtx.vx`, `kw`): buzda ve Hızlı Dünya'da iniş alanları ve platformlar orantılı genişler; gerçek boşluklar en az 1,8 karodur.
   * Her desen kendisine verilen bölge sınırına (`limit`) uyar: sığmıyorsa küçülür (kısa köprü, tek platform, tek sıra diken) ya da `NO_FIT` döndürür; taşan desen geri alınır (`mark`/`rollback`).
   * **Duyurulan kuralın dünyada karşılığı vardır:** Hayalet Dünya ve Değişen Platformlar aşamalarında ilgili köprü her zaman ilk yerleştirilen desendir (`Patterns.dedicated`); nesnesi en dar haliyle bile sığmayacak kadar kısa aşamalardan (ör. yer çekimi dönüşünden hemen sonraki Yavaş Zaman) kural çıkarılır (`LevelGenerator.fitRules`, `Patterns.minZone`). Diğer kurala özgü desenler (dar geçit, asılı diken, yüksek duvar) yer kaldıkça eklenir.
   * Hayalet köprü eşit parçalara bölünür (parça ≥ 2 karo), dikey platformların çevresindeki boşluklar gerçek boşluktur (≥ 1,8 karo), yatay platformun en yüksek hızı koşu hızının %65'ini aşmaz.
   * **Yoğunluk** (`PatternCtx.crowd`, zorluk parametresi 1'in üstünde): kurala özgü uzun desenler kısalır ve her aşamaya 1–2 hassasiyet deseni (diken, testere, dikenli boşluk) eklenir. Üst düzey zorluk pencereleri daraltarak değil (en dar pay ~7–8 tick'te kalır) engel sayısını artırarak sağlanır.
   * Hareketli testereler oyuncuya kilitlidir (`HazardDef.sync`): oyuncu ne zaman gelirse gelsin testere aynı konumdadır.
3. `LevelSolver` gerçek Box2D simülasyonunda anlık görüntü geri yüklemeli derinlik öncelikli arama yapar. Arama **katı modda** yapılır: engel isabet kutuları 0,1 büyük, coyote payı yok, duvar dibinde 0,3 sn'den fazla oyalanmak yasak; Ters Kontrol'de önce "basılı tut" denenir. Böylece insan için pay kalır ve çözümler doğal akışa yakındır. Kararlar 4 tick'te bir verilir; çözüm çıkmazsa 2 tick'lik ince ızgarayla bir kez daha aranır (`LevelForge.trySolve`).
4. `LevelAnalyzer` çözümü **insanın oynayacağı biçime** çevirir ve bölümü ölçer:
   * `normalize`: çözücünün "hep basılı tut" biçimli girişleri, yörüngeyi birebir koruyan doğal dokunuşlara indirgenir; etkisiz dokunuşlar atılır.
   * `center`: her zıplama, sırayla, kendi zamanlama penceresinin ortasına kaydırılır (çözücü engellere son anda tepki veren çözümler bulur; insan pencerenin ortasını hedefler); 5 tick'ten kısa dokunuşlar insanın basabileceği süreye uzatılır.
   * `measure`: her zıplamanın **zamanlama payı** (kaç tick erken/geç başlatılabilir) ve **basılı tutma payı** ölçülür (tutma payı, insanın basabileceği sürelerden — en az 5 tick — kaç tanesinin bölümü geçirdiğidir); ardından bölüm, dokunuşlarına rastgele zamanlama hatası eklenmiş (σ = 2 / 3,5 / 5 tick: usta / orta / acemi) kapalı çevrim bir oyuncuyla onlarca kez oynatılır. Oyuncu "şu noktaya gelince zıpla" diye oynar (konum tetiklemeli), önceki zıplaması geç biterse inişi bekler, duvara takılırsa yeniden zıplar. Tamamlama olasılıklarından **ölçülen zorluk** (1–10 ölçeği) hesaplanır.
5. `LevelForge` her aday için: çöz → insanlaştır → yörünge üzerine altın/yıldız/parça yerleştir → baştan oynatarak doğrula → bitiş düzlüğünü doğrula → ölç. Aday, ölçülen zorluğu bölümün **hedef zorluk bandına** düşüyor ve hiçbir zıplaması dar pencere istemiyorsa kabul edilir; aksi halde desen yoğunluğu ölçüme göre ayarlanıp yeni aday üretilir (en çok 30 deneme; en iyi aday tutulur).
6. **Hedef zorluk eğrisi** (`GameContent.targetDifficulty`) "testere dişi"dir: her harita bir öncekinden biraz zor başlar (taban +0,62) ve harita boyunca artar (+1,45), yeni haritanın ilk bölümleri öncekinin sonundan kolaydır; alanın 5. bölümü (meydan okuma) bir basamak yukarıdadır; mekanik tanıtım bölümleri kolaydır.
7. Çözüm (`solution`, insanlaştırılmış hali) ve desen kayıtları (`features`) JSON'a yazılır; testler çözümün oynatılmasını, sıfırdan çözülebilirliği, bitiş düzlüğünü, zamanlama paylarını ve ölçülen zorluğun hedefi izlediğini 500 bölümün tamamında doğrular.

Araçlar: `:core:analyzeLevels` (500 bölümü ölçer → `docs/difficulty_report.tsv`; `-Pdump=<klasör>` ile yörünge dökümü), `tools/level_strip.py` (her bölümün tamamını, oynanış yolu ve zıplama paylarıyla tek görselde çizer), `-Poffset=N -Pimprove=1` (hedeften sapan bölümleri farklı tohumlarla yeniden üretir, yalnızca daha iyi sonucu yazar), `-Prestamp=1` (yeniden üretmeden zorluk etiketlerini güncel eğriye göre yazar), `:core:draftStats` (taslaklarda kurala özgü nesnelerin bulunma oranı), `:core:draftDump -Pa="<id> <denemeler> <tShift>"` (bir taslağın aşama/desen/pay dökümü), `:core:probeUnsolved` (çözülemeyen taslakların takıldığı desen).

## Sonsuz Dünya

`EndlessGenerator` her 5 saniyelik parçayı yalnızca (tohum, parça no) ile üretir → deterministik ve çözücüyle test edilebilir. Hız parça başına %1,1 artar (azami %40), kombinasyon sayısı zamanla 3'e çıkar, öğrenilmemiş mekanikler sonradan ve kısa öğreticiyle gelir. Puan: mesafe×2 + süre×3 + altın×5 + yıldız×150 + kural bonusu (kural sayısı ve aşama ilerlemesiyle artar). `plausible()` fiziksel olarak imkânsız puanları reddeder.

## İlerleme ve kayıt

`ProfileService` bütün değişikliklerin tek kapısıdır ve her değişiklikten sonra kaydeder. `SaveManager`: JSON + CRC32 (tuzlu) imza, önce geçici dosyaya yazıp yer değiştirme, önceki sağlam kayıt `profile.bak`, bozuk/elle değiştirilmiş kayıtta yedekten yükleme, sürüm alanı ile göç. Kayıtlar `Gdx.files.local("save/")` altındadır (güncellemede silinmez; Android otomatik yedeklemeye dahil).

## Görüntüleme ve performans

* Tek `SpriteBatch`, üç atlas sayfası (ui, game, tema) + iki paralaks dokusu; yalnızca o anki haritanın teması bellekte. Görünmeyen karolar çizilmez (görünür aralık kırpma), parçacıklar nesne havuzludur, çöp üretimi en aza indirilmiştir.
* Dünya `ExtendViewport(21 × 11.8)` (zemin ve tavan aynı anda görünür, oyuncu soldan %28'de), arayüz `ExtendViewport(1280 × 720)` → bütün yatay en-boy oranları desteklenir; menüler iki sütunlu, oyun göstergeleri tek üst satırdadır. Yazı tipleri ekran yoğunluğuna göre FreeType ile keskin üretilir (Türkçe karakterler dahil).
* "Az efekt" ayarı parçacık sayısını düşürür (düşük segment cihazlar için).

## Android paketi

`targetSdk` / `compileSdk` 36 (Android 16), `minSdk` 21, AGP 8.11.1. Tek izin `VIBRATE`. `android:appCategory="game"` yatay kilidin Android 16'da büyük ekranlarda da geçerli kalmasını sağlar; `android:enableOnBackInvokedCallback="false"` Android 16'da geri tuşunun eski yoldan (`KEYCODE_BACK`) oyuna ulaşmasını korur (Google bu seçeneği geçici sayar; kalıcı çözüm libGDX 1.14.1+ sürümüne geçmektir). Oyun tam ekran ve kenardan kenara çizilir; `BaseScreen.applySafeArea` kök tabloyu `Gdx.graphics.safeInset*` kadar içeri alır (masaüstünde `-Dinsets=sol,üst,sağ,alt` ile denenir). İmza: `keystore.properties` varsa yükleme anahtarı, yoksa hata ayıklama anahtarı.

## Açılış animasyonu (Magic Lumos Software)

`IntroScreen` uygulama açılışında ~7,4 sn oynar (dokununca geçilir). `tools/intro/extract_logo.py` şirket logosunu (`tools/intro/magic_lumos_logo.jpg`) parlaklık anahtarlamasıyla katmanlara ayırır: 10 harf, SOFTWARE'in 10 karakteri, köşeli parantezler, kod işaretleri ve kenarları yumuşatılmış devre çizgileri (`assets/intro/`, konumlar `layout.txt`). Animasyon oyunun diliyle kurgulanmıştır: devre çizgileri merkezden açılır → harfler zıplayarak düşer → oyunun karakteri harflerin üstünde zıplar → "Lumos" ışık patlaması → SOFTWARE ters yer çekimiyle baş aşağı gelip düzelir → ışık süzmesi ve "sunar".

## Bölüm haritası

`LevelSelectScreen` her haritanın 50 bölümünü manzara üzerinde kıvrılan noktalı bir yol (Catmull-Rom eğrisi) boyunca yuvarlak düğmeler olarak gösterir; yatayda kaydırılır ve arka plan kaydırmayla paralaks hareket eder. Geçilen yol altın rengindedir, şu anki bölümde karakter durur ve nabız efekti vardır; her 5 bölümde bir alan tabelası, finallerde taç bulunur.

## Kullanıcı görselleri (art_input/)

Oyunun görselleri yapay zekâ ile üretilmiş kullanıcı görselleridir (`art_input/parca1..4`, `art_input/duzeltilmis`; sonra gelen öncekinin yerine geçer). `tools/art/user_art.py` bunları otomatik işler, `tools/art/gen_art.py` atlaslara paketler:

* Kalan beyaz arka plan ve yapay "dama deseni" artıkları temizlenir; zemin üst karolarının üstü şeffaf yapılır; kesim artığı koyu bantlar ve beyaz kenarlar kırpılır.
* Karolar çapraz geçişle dikişsiz hale getirilir; tavan karosu zemin üst karosunun dikey çevrilmiş halidir.
* Şeffaf olmayan paralaks katmanlarının üstü gökyüzüne eritilir; arka planlar 2048×512 JPG + PNG alfa maskesi olarak saklanır (2'nin kuvveti boyut, küçük APK) ve çizimde 3,2:1 oranı geri kurulur.
* Karakter sayfaları (8×2 kare) kırpılır, ayak çizgisine hizalanır, bütün görünümler aynı boya ölçeklenir; her görünüm ayrı küçük atlastadır (`hero_<id>.atlas`), oyunda yalnızca gerekenler yüklenir.
* Süs nesneleri ayrıştırılıp tutarlı dünya ölçeğine (110 piksel = 1 karo) getirilir.
* Kullanıcı görseli olmayan ya da kullanılamayan öğeler (altın animasyonu, güçlendirme baloncuğu, bazı arayüz parçaları) programatik çizimlerle tamamlanır.

## Grafik ve ses kaynakları

Bütün grafikler `tools/art` betikleriyle programatik çizilir (4× süper örnekleme ile kenar yumuşatma); bütün sesler/müzikler `tools/audio` ile sentezlenir. Tek dış varlık Nunito yazı tipidir (SIL Open Font License, `assets/fonts/OFL-Nunito.txt`). Lisans sorunu yoktur.
