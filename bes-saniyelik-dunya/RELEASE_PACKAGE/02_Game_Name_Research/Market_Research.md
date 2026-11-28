# Pazar ve İsim Çakışması Araştırması — "5 Saniyelik Dünya" İngilizce İsim Adayları

- **Araştırma tarihi:** 2026-11-27
- **Kapsam:** `Name_Candidates.md`'deki 33 aday. Her aday için en az bir genel web araması yapıldı. Öne çıkan 10 aday için birden çok kaynakta derin arama yapıldı: Twist O'Clock, Stopwatch Shuffle, 5-Second Shuffle, Tickwarp, Rulequake, Shiftopia, FiveTwist, Twistwatch, Tick Twist, Five Second Shift.
- **Dürüstlük notu:** Bu dosyada yalnızca gerçekten yapılan aramalar ve gerçekten görülen sonuçlar var. "Arandı, bulunamadı" ile "aranamadı / erişilemedi" ayrı ayrı belirtildi.

---

## 1. Yöntem ve araçlar

| Araç | Ne yapar | Kısıt |
|---|---|---|
| **WebSearch** (arama motoru) | Genel web araması. `allowed_domains` filtresiyle `site:` benzeri, belirli alan adlarıyla sınırlı arama | Sonuçlar arama motoru **dizinine** dayanır ve ABD odaklıdır. Mağazaların **canlı içi aramasının** yerini tutmaz: yeni, bölgesel veya dizine girmemiş uygulamalar görünmeyebilir. Sonuçlarda "bulunamadı", "bu dizin taramasında görünmedi" anlamına gelir |
| **WebFetch** (sayfa getirme) | Belirli bir URL'yi (ör. mağaza arama sayfası) doğrudan açar | Bu oturumda ağ izin listesi kısıtlı. Denenen tüm hedefler DNS hatası verdi (aşağıya bakın) |

Oturum ortamının ağ izin listesi ortam bilgisinden doğrulandı. İzin listesinde yalnızca birkaç geliştirme hostu (Android SDK / Maven indirme sunucuları), birkaç ilgisiz host ve paket depoları var. Mağaza ve marka veri tabanı hostları listede olmadığı için WebFetch ile açılamadı. Bu kaynakların doğrudan sorgulanabilmesi için ilgili hostların (ör. `play.google.com`, `store.steampowered.com`, `apps.apple.com`, `branddb.wipo.int`, `euipo.europa.eu`, `tmsearch.uspto.gov`, `turkpatent.gov.tr`) oturumun ağ ayarlarında izinli olması gerekir. Marka veri tabanları JavaScript uygulamaları olduğu için o durumda bile tam sorgu yapılamayabilir.

## 2. Kaynak erişim tablosu

| Kaynak | Doğrudan sorgu | Sonuç | Dolaylı yöntem |
|---|---|---|---|
| Google Play arama (`play.google.com/store/search?q=…&c=apps`) | WebFetch ile **denendi** | **ERİŞİLEMEDİ**: `getaddrinfo ENOTFOUND play.google.com` (ağ izin listesi dışında) | WebSearch + `allowed_domains: play.google.com` (dizinlenmiş sayfalar) |
| Steam arama (`store.steampowered.com/search/?term=…`) | WebFetch ile **denendi** | **ERİŞİLEMEDİ**: `ENOTFOUND store.steampowered.com` (aynı neden) | WebSearch + `allowed_domains: store.steampowered.com` |
| WIPO Global Brand Database (`branddb.wipo.int`) | WebFetch ile **denendi** | **ERİŞİLEMEDİ**: `ENOTFOUND branddb.wipo.int` (aynı neden; ayrıca JavaScript uygulaması) | WebSearch + `wipo.int` filtresi (yalnızca top adaylar için; ilgili kayıt çıkmadı. Bu "kayıt yok" **demek değildir**) |
| Apple App Store (`apps.apple.com`) | **Denenmedi**: aynı ağ kısıtı (izin listesi dışında). Araç, aynı hatayı veren 3 hosttan sonra yeni host denenmemesini önerdi | **ERİŞİLEMEDİ** (doğrudan) | WebSearch sonuçlarında çok sayıda `apps.apple.com` sayfası çıktı, ayrıca `allowed_domains: apps.apple.com` filtresi kullanıldı |
| EUIPO eSearch (`euipo.europa.eu/eSearch`) | **Denenmedi**: aynı ağ kısıtı. Ayrıca JS uygulaması | **ERİŞİLEMEDİ** | WebSearch + `euipo.europa.eu`, `tmdn.org` filtresi, top adaylar için 1 arama: ilgili kayıt çıkmadı (dizin taraması, veri tabanı sorgusu değil) |
| USPTO Trademark Search (`tmsearch.uspto.gov`) | **Denenmedi**: aynı ağ kısıtı. Ayrıca JS uygulaması | **ERİŞİLEMEDİ** | USPTO verisini yansıtan üçüncü taraf siteler (Justia Trademarks, Trademarkia, uspto.report, markinton) WebSearch ile tarandı. **Kısmi ve güncel olmayabilir** |
| TÜRKPATENT (`turkpatent.gov.tr`) | **Denenmedi**: aynı ağ kısıtı. Marka araştırması e-Devlet/JS gerektirir | **ERİŞİLEMEDİ** | WebSearch + `turkpatent.gov.tr` filtresi (top adaylar), sonuç yok. **Türkiye'deki marka durumu hakkında hiçbir bilgi elde edilemedi** |
| UK IPO | Doğrudan denenmedi | – | WebSearch'te `ipo.gov.uk` filtresi (top adaylar), ilgili kayıt çıkmadı |
| YouTube, Instagram, TikTok | Doğrudan denenmedi (ağ kısıtı) | **ERİŞİLEMEDİ** (doğrudan) | WebSearch + `youtube.com`, `instagram.com`, `tiktok.com` filtresi |
| itch.io, CrazyGames, Poki, RAWG, MobyGames, Nintendo eShop | Doğrudan denenmedi | – | WebSearch (genel sonuçlarda ve filtreli aramalarda çıktı) |
| Genel web (Google/Bing benzeri) | – | **Kullanıldı** | WebSearch (her aday için en az 1 sorgu) |

**Sonuç:** Hiçbir marka veri tabanı (WIPO, EUIPO, USPTO, TÜRKPATENT) doğrudan sorgulanamadı. Bu dosyada hiçbir aday için "marka tescili yok" denmiyor. Yalnızca "dizinlenmiş web/aynalarda görülmedi" denebilir.

---

## 3. Aday bazında bulgular

Biçim: **Yapılan sorgular** → **Bulunanlar (URL)** → **Çakışma notu**. "D" ile işaretli adaylar derin araştırılanlardır.

### 3.1 5 Second World *(referans, birebir çeviri)*
- **Sorgular:** genel `"5 Second World" game`. Ayrıca "5 Second" öneki diğer aramalarda da tarandı.
- **Bulunanlar:** "5 Second World" adlı bir oyun bulunamadı. Önek çok kalabalık:
  - 5 Second Rule (PlayMonster, parti oyunu): https://www.hobbycraft.co.uk/5-second-rule-game/6838411000.html
  - 5 Second Rule – Party Games (iOS): https://apps.apple.com/us/app/-/id1555266150
  - 5 second game (iOS): https://apps.apple.com/us/app/id6752538623
  - 5 Second Challenge (iOS): https://apps.apple.com/app/id1111506368
  - 5 seconds (itch.io): https://prisoner9430.itch.io/5-seconds
  - ABD'de tescilli **5 SECOND RULE** markası (PlayMonster; arama özetine göre Reg. No. 3846453, oyun/kart oyunu): https://trademark.justia.com/778/50/5-second-77850326.html
- **Not:** Birebir çakışma yok. Ancak "5 Second …" isimleri mağazada parti oyunlarının arasında kaybolur ve ismin kendisi tanımlayıcı olduğu için marka korumasına zayıf aday.

### 3.2 Five Second Shift (D)
- **Sorgular:** genel `"Five Second Shift" game app`. Filtreli `"Five Second Shift" OR "5 Second Shift"` (Play, App Store, Steam, YouTube, Instagram, TikTok, Justia, Trademarkia).
- **Bulunanlar:**
  - Mel Robbins "5 Second Shift" videosu: https://www.youtube.com/watch?v=M6UTI3_PqsY
  - "The 5 Second Shift" (manifest videosu): https://www.youtube.com/watch?v=rcLVgXrABuU
  - **Second Shift** (Steam, korku oyunu): https://store.steampowered.com/app/3042860/Second_Shift/
  - Shift Game (iOS refleks oyunu): https://apps.apple.com/us/app/shift-game/id6757751621
  - Shifted (iOS): https://apps.apple.com/us/app/shifted/id6757963363
- **Not:** Birebir oyun yok. İsim kişisel gelişim içeriklerine ve "Second Shift" korku oyununa çok yakın düşüyor.

### 3.3 5-Second Shuffle (D)
- **Sorgular:** genel `"5 Second Shuffle" game` ve `"Five Second Shuffle"`. Filtreli: Play + App Store + Steam, ayrıca YouTube + Instagram + TikTok. Marka: `"5 Second Rule" PlayMonster trademark`. itch/RAWG/MobyGames birleşik sorgusu.
- **Bulunanlar:** Birebir "5-Second Shuffle" / "Five Second Shuffle" adlı oyun veya uygulama **görülmedi**. Yakın olanlar:
  - 5 Second Rule (Google Play): https://play.google.com/store/apps/details?id=com.gtm.fiveseconds&hl=en
  - 5 Second Rule – Group Games (iOS): https://apps.apple.com/us/app/5-second-rule-group-games/id1441645718
  - 5 SECOND RULE ABD markası (PlayMonster): https://trademark.justia.com/778/50/5-second-77850326.html
  - **Shuffle Man** (itch.io): https://evangeder.itch.io/shuffleman. İlk aramanın özetinde "her şeyin 5 saniyede değişebildiği kaotik bir oyun" diye geçti, ama sayfa açıklaması **doğrulanamadı** (WebFetch erişimi yok).
  - Shuffle Grand Prix (beş saniye temalı masa oyunu incelemesi): https://therewillbe.games/hot-takes-board-game-reviews/6979-shuffle-grand-prix-a-five-second-board-game-review
  - YouTube'da yalnızca dans ("shuffle") ve John Cena "Five Knuckle Shuffle" içerikleri çıktı. Oyun bulunmadı.
- **Not:** Orta risk. "5 Second" önekinde tescilli bir marka var, mağazada parti oyunları kalabalık. "Shuffle Man" kavram olarak yakın olabilir.

### 3.4 FiveShift
- **Sorgular:** genel `"FiveShift" game OR app`.
- **Bulunanlar:** **ShiftFive** (iOS/Mac kelime oyunu, Braxton Smallwood): https://apps.apple.com/ca/app/shiftfive/id6502816034. Five Midnight Shifts: https://app.cinevva.com/arcade/five-midnight-shifts. Shift (Playdate): https://play.date/games/shift
- **Not:** Ters sıralı birebir isim (ShiftFive) oyun kategorisinde var, karışıklık riski yüksek.

### 3.5 FiveTwist (D)
- **Sorgular:** genel `"FiveTwist" OR "Five Twist" game app`. Filtreli (Play, App Store, Steam, YouTube, IG, TikTok, Justia, Trademarkia). Birleşik marka sorgusu.
- **Bulunanlar:**
  - **Quintwist: Twist Five in a Row** (iOS): https://apps.apple.com/ca/app/quintwist-twist-five-in-a-row/id6821054324
  - **Twist 5** (itch.io, Pentago benzeri): https://threeflags.itch.io/twist-5
  - Twist (Ketchapp, mobil): https://apps.apple.com/us/app/twist/id1052231801
  - YouTube/TikTok'ta "five twist" saç modeli içerikleri: https://www.youtube.com/watch?v=yweyaPUCCfQ
  - Marka aynalarında "FiveTwist" kaydı görülmedi (dizin taraması).
- **Not:** Orta risk. Twist + five kombinasyonları zaten kullanılıyor.

### 3.6 Five Flip
- **Sorgular:** genel `"Five Flip" game app`.
- **Bulunanlar:** Birebir yok. Game of FLIP: https://apps.apple.com/us/app/id1502177320. Friendly Flip: https://apps.apple.com/us/app/-/id6759228597. Flippa: https://apps.apple.com/au/app/flippa/id1545716050. Human Flip (geliştirici "Five More Minutes Games"): https://apps.apple.com/us/app/id1521495165
- **Not:** "Flip" çok kalabalık, aramada ayırt edilmesi zor.

### 3.7 Every Five
- **Sorgular:** genel `"Every Five" mobile game app`.
- **Bulunanlar:** Every 5×5 Nonogram: https://grand-screen.com/games/every-5x5-nonogram/. **Fiveish** (iOS, "her beş saniyede yeni mini oyun", kavram olarak yakın): https://apps.apple.com/us/app/fiveish/id6759258843. Five Fives: https://apps.apple.com/us/app/id1609983818
- **Not:** Birebir yok. İsim çok jenerik. Fiveish kavram olarak rakip.

### 3.8 Twist Every Five
- **Sorgular:** genel `"Twist Every Five" OR "Twist in Five" game app`.
- **Bulunanlar:** "Twist Every Five" görülmedi. Twist 5: https://threeflags.itch.io/twist-5. Ketchapp Twist: https://apps.apple.com/gb/app/twist/id1052231801. Twisty Pals: https://apps.apple.com/bh/app/twisty-pals/id1514748217
- **Not:** Düşük-orta risk. Uzun (16 karakter), slogan gibi.

### 3.9 5 Sec Switch
- **Sorgular:** genel `"5 Sec Switch" OR "5 Second Switch" game`.
- **Bulunanlar:** 5 Second Rule kurallarında "SWITCH" kartı: https://www.boardgamecapital.com/5-second-rule-rules.htm. Switch! (CrazyGames): https://www.crazygames.com/game/switch. 5 seconds (itch.io): https://jacobeut.itch.io/5-seconds. Quick Switch: https://aerokev.itch.io/quick-switch. 5sec Stopwatch Timer Game App (iOS): https://apps.apple.com/us/app/id6465250269
- **Not:** "Switch" kelimesi Nintendo Switch'i çağrıştırır. Bu çalışmada Nintendo'nun marka kaydı ayrıca aranmadı, bu genel bilgidir.

### 3.10 Tick Twist (D)
- **Sorgular:** genel `"Tick Twist" game`. Filtreli (Play, App Store, Steam, YouTube, IG, TikTok, Justia, Trademarkia).
- **Bulunanlar:**
  - **Tick Twister®** kene sökme aleti (O'Tom), birden çok tanıtım videosu: https://www.youtube.com/watch?v=CbIUtLBOnG4 , https://www.youtube.com/watch?v=jIoCTWYJsvo
  - Tricky Twist (oyun): https://rawg.io/games/tricky-twist
  - Tick: The Time Based Puzzle Game (Steam): https://store.steampowered.com/app/388010
  - "The Tick Tock Twist" (şarkı): https://www.youtube.com/watch?v=n5yGcWO1uhw
- **Not:** Arama sonuçlarına bir parazit (kene) aleti hâkim. Çocuk oyunu için olumsuz çağrışım.

### 3.11 Tick Shift
- **Sorgular:** genel `"Tick Shift" OR "TickShift" game`.
- **Bulunanlar:** Tick Hop (Steam, zaman temalı platform/bulmaca): https://store.steampowered.com/app/3419780/Tick_Hop/. Tick (Steam): https://store.steampowered.com/app/388010. Shift serisi (Armor Games): https://wikipedia.com/wiki/Shift_(game). Özette "OneShift" (Steam) adlı bir oyun da geçti, URL'si doğrulanamadı.
- **Not:** Birebir yok. Her iki kelime de aynı türde kullanılıyor.

### 3.12 Tickwarp (D)
- **Sorgular:** genel `"Tickwarp" OR "Tick Warp" game` ve `"Tickwarp" OR "TickWarp"`. Filtreli (Play, App Store, Steam, YouTube, IG, TikTok, Trademarkia, Justia). Birleşik marka sorgusu (Justia/Trademarkia/uspto.report). itch/RAWG/MobyGames birleşik sorgusu. EU/WIPO/UKIPO/TÜRKPATENT dizin sorgusu.
- **Bulunanlar:**
  - Oyun, uygulama veya marka olarak "Tickwarp" **görülmedi**.
  - Minecraft Carpet mod'undaki "tick warp" komutu (YouTube eğitim videoları): https://www.youtube.com/watch?v=eWVfD8KDo54 , https://www.youtube.com/watch?v=_Me-Ik_20Gg
  - Time Warp (itch.io, "her 10 saniyede başka bir uzay-zamana ışınlanma", kavram olarak yakın): https://zoglu.itch.io/time-warp
  - Tick Hop (Steam): https://store.steampowered.com/app/3419780/Tick_Hop/
- **Not:** Düşük-orta risk. Arama görünürlüğü Minecraft içerikleriyle paylaşılıyor. "Tick" = kene çağrışımı var.

### 3.13 Tickaboo
- **Sorgular:** genel `"Tickaboo" game OR app`. Filtreli birleşik (Play, App Store, IG, TikTok, YouTube).
- **Bulunanlar:** "Tickaboo" görülmedi. Toca Boo (Toca Boca, çocuk oyunu): https://tocaboca.com/for-parents/toca-boo. Sneak-a-Boo!: https://apps.apple.com/us/app/sneak-a-boo/id1112428583. Pokaboo: https://www.pocketgamer.com/pokaboo/. Picaboo: https://www.producthunt.com/posts/picaboo
- **Not:** "Tickaboo" ile "Toca Boo" fonetik olarak yakın ve aynı hedef kitleye (çocuklar) hitap ediyor.

### 3.14 Quintick
- **Sorgular:** genel `"Quintick" game OR app`.
- **Bulunanlar:** Görülmedi. Uzak benzerler: Quintastic: https://apps.apple.com/ms/app/quintastic/id6758269684. Quinb: https://deepdaikon.itch.io/quinb. QuinX: https://appgoblin.info/apps/com.statthordev.quinx
- **Not:** Çakışma düşük, ama ismin anlamı ve telaffuzu zayıf.

### 3.15 Twist O'Clock (D)
- **Sorgular:**
  1. genel `"Twist O'Clock" OR "Twist o clock" game`
  2. genel `"Twist O'Clock"`
  3. filtreli Play + App Store: `Twist O'Clock app`
  4. filtreli marka aynaları (Trademarkia, Justia, uspto.report, EUIPO, WIPO): `"Twist O'Clock" trademark`
  5. filtreli YouTube + Instagram + TikTok + Steam
  6. filtreli itch.io + Steam + CrazyGames + Poki + RAWG + MobyGames + Nintendo
  7. EU/WIPO/UKIPO/TÜRKPATENT dizin sorgusu
  8. `TWIST trademark Ketchapp class 9`
- **Bulunanlar:**
  - **Oyun veya uygulama olarak "Twist O'Clock" görülmedi.**
  - Aynı isimli **kafe** (Cumbayá, Quito, Ekvador), TikTok: https://www.tiktok.com/@twistoclock.ec
  - Aynı isimli **pastane/cupcake** hesabı, Instagram: https://www.instagram.com/twistoclockbakery/
  - 1961 filmi "Twist Around the Clock": https://www.wikiwand.com/en/articles/Twist_Around_the_Clock
  - "… O'Clock" kalıbındaki oyunlar: **Fly O'Clock** (itch.io, sonsuz zıplama oyunu; geliştirici "Twisted Twice Games"): https://twistedtwicegames.itch.io/fly-oclock-gameboy-version. Die O'Clock (Steam): https://store.steampowered.com/app/2081490/Die_OClock. Too O'Clock: https://meanderxander.itch.io/too-oclock. Scrabble O' Clock (Play): https://play.google.com/store/apps/details?id=com.suvink.scrabble_o_clock&hl=en_US. O'Clock (iOS saat widget'ı): https://apps.apple.com/us/app/oclock/id6746874666
  - **Ketchapp "Twist"** (tek dokunuşla zıplama/platform, aynı tür): https://apps.apple.com/us/app/twist/id1052231801
  - Sınıf 9'daki TWIST kayıtları (marka aynaları): Doist "TWIST" https://www.trademarkia.com/twist-87847198. Özete göre OOZEX Licensing ve PopSockets'in TWIST kayıtları da var, Jam City'nin sınıf 9/41 TWIST başvurusu ise "dead". Ketchapp adına TWIST kaydı **görülmedi**. KETCHAPP markası Ubisoft Mobile Games adına kayıtlı: https://trademarks.justia.com/865/87/ketchapp-86587575.html
  - Marka aynalarında "Twist O'Clock" kaydı **görülmedi** (dizin taraması, veri tabanı sorgusu değil).
- **Not:** Oyun ve yazılımda birebir çakışma görülmedi. Riskler: (a) aynı isim yiyecek-içecek sektöründe kullanılıyor (farklı Nice sınıfları, karışıklık ihtimali düşük), (b) "Twist" kelimesi aynı türdeki popüler Ketchapp oyunuyla ve sınıf 9'daki çok sayıda TWIST kaydıyla ortak.

### 3.16 Twistwatch (D)
- **Sorgular:** genel `"Twistwatch" OR "Twist Watch" game app`. Filtreli (Play, Steam, YouTube, IG, TikTok, Trademarkia, Justia).
- **Bulunanlar:**
  - "Twistwatch" adlı şarkı (The String-A-Longs): https://www.youtube.com/watch?v=HVv4QZg8tXE ve "Twist Watch": https://www.youtube.com/watch?v=47DzluCSK_A
  - Fossil "Twist" saat serisi: https://www.youtube.com/watch?v=1jPSlhlPXGg
  - Wear OS "Twist" saat yüzü: https://play.google.com/store/apps/details?id=com.watchfacestudio.twistwatchface&hl=en
  - TimeTwist Watch (Apple Watch kelime oyunu): https://apps.apple.com/app/id6484401615
- **Not:** Saat ürünleri arama sonuçlarına hâkim, ürün kategorisiyle karışabilir.

### 3.17 Stopwatch Shuffle (D)
- **Sorgular:**
  1. genel `"Stopwatch Shuffle" game`
  2. filtreli Play + App Store + Steam + YouTube + IG + TikTok
  3. filtreli marka aynaları (Trademarkia, Justia, uspto.report)
  4. itch/CrazyGames/Poki/RAWG/MobyGames birleşik
  5. EU/WIPO/UKIPO/TÜRKPATENT dizin sorgusu
  6. varyant `"Stop Watch Shuffle" OR "Stopwatch Shuffle" app game`
  7. `STOPWATCH trademark video game class 9 OR class 41`
  8. "Shuffle" içeren oyun isimleri kontrolü (Pokémon Shuffle / Shuffle Cats)
- **Bulunanlar:**
  - **Hiçbir kaynakta "Stopwatch Shuffle" / "Stop Watch Shuffle" adlı oyun, uygulama, sosyal hesap veya marka kaydı görülmedi.**
  - Kronometre zamanlama oyunları (isim benzer değil, kelime ortak): Stop It!: https://apps.apple.com/app/id6754552103. 99: The Stopwatch Game: https://apps.apple.com/us/app/id6503170083. Stopwatch 3000 (itch.io): https://bamsarker.itch.io/stopwatch-3000. Microsoft Store'da kronometre dokunma oyunu: https://apps.microsoft.com/detail/9nblggh52x63
  - Kronometre yardımcı uygulamaları mağaza sonuçlarına hâkim: https://apps.apple.com/us/app/multi-stopwatch/id933638040 , https://play.google.com/store/apps/details?id=com.theab.stopwatch&hl=en_US
  - "Shuffle" içeren bilinen oyunlar: **Pokémon Shuffle / Shuffle Mobile** (Nintendo / The Pokémon Company, bulmaca): https://apps.apple.com/app/id1014919815. **Shuffle Cats** (King, kart oyunu): https://www.pocketgamer.com/shuffle-cats. **Shuffle World** (bulmaca-platform): https://www.mobygames.com/game/171579/shuffle-world/. Shuffle Man (itch.io): https://evangeder.itch.io/shuffleman
  - Marka aynaları: STOPWATCH (Telekonnectors Ltd., elektrik test ekipmanı): https://trademarks.justia.com/766/53/stopwatch-76653063.html. SHUFFLE (Cartamundi; Trademarkia'ya göre iptal edilmiş): https://trademarks.justia.com/791/56/shuffle-79156054.html. Lovevery portföyünde "TIME & COMPARE STOPWATCH" (sınıf 28, oyuncak): https://www.trademarkia.com/owners/lovevery-inc
  - YouTube: yalnızca "Stop Time Shuffle" adlı bir dans videosu: https://www.youtube.com/watch?v=xY0kdZBhKr8
- **Not:** Bu çalışmanın erişebildiği kaynaklarda en temiz aday bu. Riskler: "Shuffle" kelimesi Pokémon Shuffle gibi bilinen oyunlarla ortak (jenerik bir kelime ama ünlü bir marka bunu kullanıyor). "Stopwatch" kelimesi mağaza aramasında yardımcı uygulamalarla karışır.

### 3.18 Hopwatch
- **Sorgular:** genel `"Hopwatch" game OR app`.
- **Bulunanlar:** **HopWatch for Reddit** (Android TV uygulaması): https://grand-screen.com/apps/hopwatch-for-reddit/. **Hopwatch: Time Tracker** (Google Play, geliştirici Urbs): https://play.google.com/store/apps/developer?id=Urbs
- **Not:** Google Play'de birebir isimli uygulamalar var, yüksek risk.

### 3.19 Flipwatch
- **Sorgular:** genel `"Flipwatch" game OR app`. Filtreli birleşik (Play, App Store, IG, TikTok, YouTube).
- **Bulunanlar:** **Flipwatch** (Google Play, flip animasyonlu saat): https://play.google.com/store/apps/details?id=com.mymad.flipwatch&hl=en_US. Coin Flip Watch (iOS): https://apps.apple.com/sg/app/coin-flip-watch/id985745714
- **Not:** Google Play'de birebir isim var, yüksek risk.

### 3.20 Shiftwatch
- **Sorgular:** genel `"Shiftwatch" game OR app`.
- **Bulunanlar:** "Shiftwatch" görülmedi. Shift-işçisi uygulamaları: ShiftWake: https://play.google.com/store/apps/details?id=com.shiftwake.app. Shiftwell: https://play.google.com/store/apps/details?id=app.shiftwell. ShiftStreak: https://apps.apple.com/cz/app/shiftstreak/id6756852760
- **Not:** Çakışma düşük, ama isim "vardiya takip" uygulaması gibi algılanıyor.

### 3.21 Flipclock Run
- **Sorgular:** genel `"Flipclock" game runner OR platformer`.
- **Bulunanlar:** "Flipclock" oyunu görülmedi. Flip Runner (itch.io): https://parengryan.itch.io/flip-runner. Flip Runner (iOS): https://apps.apple.com/bw/app/flip-runner/id1562569618. FlipSwitch: https://arn9e.itch.io/flipswitch. Clock Runner: https://tedbolt.itch.io/clockrunner. Flipwatch (flip saat, Play) için bkz. 3.19
- **Not:** "Flip … Run" kalıbı aynı türde çok kullanılıyor.

### 3.22 Chrono Hop
- **Sorgular:** genel `"Chrono Hop" game`.
- **Bulunanlar:** **Chrono Hop** (itch.io jam girişi, Lloyd Archer, "Hop through a turning clock…"): https://itch.io/post/6405671/view-in-topic. Clock Hop (mobil): https://toucharcade.com/games/clock-hop. Tick Hop (Steam): https://store.steampowered.com/app/3419780/Tick_Hop/
- **Not:** Birebir isimli bir platform jam oyunu var, yüksek risk.

### 3.23 Rule Flip Run
- **Sorgular:** genel `"Rule Flip" game runner`.
- **Bulunanlar:** **Flip the Rule** (CrazyGames, yerçekimi temelli bulmaca, özete göre Kasım 2026 yayın öncesi): https://www.crazygames.com/game/flip-the-rule. Flip Runner: https://www.pocketgamer.com/flip-runner/. Gravity Flip Runner: https://www.y8.com/games/gravity_flip_runner_html5. RULE (itch.io koşu prototipi): https://mikael-game-design.itch.io/rule
- **Not:** Aynı kelimelerle yeni bir yerçekimi oyunu var, yüksek risk.

### 3.24 Rule Shuffle
- **Sorgular:** genel `"Rule Shuffle" game`.
- **Bulunanlar:** Birebir yok. Kural değiştiren masa oyunları: Fluxx (Looney Labs): https://www.looneylabs.com/sites/default/files/literature/DogFluxxRules.pdf. Gotcha: https://cdn6.bigcommerce.com/s-gyhhtwx4/product_images/uploaded_images/instructions/GotchaInstructions_021011.pdf
- **Not:** Düşük risk ama isim jenerik.

### 3.25 Rule Twist
- **Sorgular:** genel `"Rule Twist" OR "Ruletwist" game app`.
- **Bulunanlar:** Birebir yok. **Rules Change** (iPad, "her turda kural değişir", kavram aynı): https://apps.apple.com/us/app/rules-change/id6758916628. **Rules!** (TheCodingMonkeys, ödüllü): https://apps.apple.com/us/app/rules!/id840811083
- **Not:** Kavram olarak rakip oyunlar "Rule(s)" kelimesini kullanıyor.

### 3.26 Rulequake (D)
- **Sorgular:** genel `"Rulequake" OR "Rule Quake" game` ve `"Rulequake"`. Filtreli (Play, App Store, Steam, YouTube, IG, TikTok, Trademarkia, Justia). Birleşik marka sorgusu.
- **Bulunanlar:**
  - "Rulequake" **görülmedi**.
  - Quake (id Software, Steam): https://store.steampowered.com/app/2310/Quake/
  - QUAKE markası (7-Eleven, enerji içeceği): https://www.trademarkia.com/quake-77134105. QUAKE başvurusu (Tackle Tactics): https://trademarks.justia.com/793/81/quake-79381477.html
  - Kural değiştiren oyunlar: Overruled! (Team17): https://www.team17.com/games/overruled. Rule No 1 (Steam): https://store.steampowered.com/app/1458390/Rule_No_1
- **Not:** Birebir çakışma yok, ama "Quake" oyun dünyasında çok ünlü bir marka öğesi. Ayrıca "deprem" çağrışımı Türkiye pazarında hassas.

### 3.27 Shiftopia (D)
- **Sorgular:** genel `"Shiftopia" game` ve `"Shiftopia"`. Filtreli (Play, App Store, Steam, YouTube, IG, TikTok, Trademarkia, Justia). Marka ve itch birleşik sorguları.
- **Bulunanlar:**
  - Oyun, uygulama veya marka olarak "Shiftopia" **görülmedi**.
  - TikTok'ta "shiftopiia" hesabı ("reality shifting" topluluğu): https://www.tiktok.com/@shiftopiia/video/7230539092939918638?lang=en
  - YouTube'da "Shiftopie" kanalı: https://www.youtube.com/channel/UC06MLHzgqEs2Q6FTa926yDw
  - Shift serisi (Armor Games, yerçekimi platform/bulmaca): https://wikipedia.com/wiki/Shift_(game). Shifto (Play, vardiya uygulaması): https://play.google.com/store/apps/details?id=app.shifto.mobileapp
- **Not:** Düşük-orta risk. Sosyal medyada "reality shifting" çağrışımı var.

### 3.28 Twistopia
- **Sorgular:** genel `"Twistopia" game app`. Filtreli birleşik (Play, App Store, IG, TikTok, YouTube).
- **Bulunanlar:** **Twistopia** (itch.io prototipi, byebyesama): https://byebyesama.itch.io/twistopia. YouTube kanalı @TwistopiaOffical: https://www.youtube.com/@TwistopiaOffical. Bali'de jimnastik işletmesi: https://www.instagram.com/twistopia_gymnastics_bali/. Twistingo (iOS): https://apps.apple.com/us/app/twistingo/id1627880018
- **Not:** Birebir isim birden fazla yerde kullanılıyor.

### 3.29 Fivetopia
- **Sorgular:** genel `"Fivetopia" game OR app`. Filtreli birleşik.
- **Bulunanlar:** Görülmedi. **Livetopia: Party!** (Century Games, büyük bir oyun): https://apps.apple.com/app/id6472251532
- **Not:** Popüler bir oyunla kafiyeli, taklit gibi algılanabilir.

### 3.30 Quirkworld
- **Sorgular:** genel `"Quirkworld" OR "Quirk World" game app`. Filtreli (Play, App Store, YouTube, IG, TikTok, Trademarkia, Justia).
- **Bulunanlar:** **QuirkWord** (iOS kelime oyunu, tek harf farklı): https://apps.apple.com/lb/app/quirkword/id6596767992. Instagram @quirk.world (~10K takipçi): https://www.instagram.com/quirk.world/. Quirkbound (Steam): https://store.steampowered.com/app/2711300. Go Quirk!: https://happyoddity.itch.io/go-quirk
- **Not:** Tek harf farkla aynı kategoride bir uygulama var, yüksek risk.

### 3.31 Wobbleworld
- **Sorgular:** genel `"Wobbleworld" OR "Wobble World" game`.
- **Bulunanlar:** **Wobbly World** (itch.io, geliştirme aşamasında): https://simplyjam.itch.io/wobblyworld. Wobbly Life: https://www.nintendolife.com/reviews/switch-eshop/wobbly-life. Wobbledogs: https://www.nme.com/reviews/wobbledogs-review-3201989. Wobbles: https://rawg.io/games/wobbles
- **Not:** Bilinen "Wobbly" oyunlarına çok yakın.

### 3.32 World Shuffle
- **Sorgular:** genel `"World Shuffle" game`.
- **Bulunanlar:** **Shuffle World** (bulmaca-platform): https://www.mobygames.com/game/171579/shuffle-world/ ve https://toucharcade.com/games/shuffle-world. Word Shuffle (çeşitli): https://gamebrain.co/game/word-shuffle
- **Not:** Ters sıralı birebir isim aynı türde var, yüksek risk.

### 3.33 Switchy World
- **Sorgular:** genel `"Switchy World" OR "Switchyworld" game`.
- **Bulunanlar:** Switchy Road (Nintendo Switch): https://www.nintendo.com/us/store/products/switchy-road-switch/. Switch Worlds (itch.io): https://blueberry-sad.itch.io/switch-worlds. Switchy Sides: https://www.producthunt.com/products/switchy-sides
- **Not:** Orta risk. Nintendo Switch çağrışımı var.

---

## 4. Kavram düzeyinde benzer oyunlar (isim çakışması değil, pazarlama farklılaşması için)

Bunlar "kurallar sürekli değişiyor" kancasına yakın, arama sonuçlarında rastlanan ürünlerdir. İsim çakışması sayılmazlar, ama mağaza metninde farkın net anlatılması önerilir:

| Ürün | Benzerlik | URL |
|---|---|---|
| Fiveish (iOS) | Her beş saniyede yeni mini oyun | https://apps.apple.com/us/app/fiveish/id6759258843 |
| Rules Change (iPad) | Her turda kural değişir, tersine döner | https://apps.apple.com/us/app/rules-change/id6758916628 |
| Rules! (TheCodingMonkeys) | Zamanlayıcı altında değişen kural listesi | https://apps.apple.com/us/app/rules!/id840811083 |
| Overruled! (Team17) | Maç sırasında kuralları değiştirme | https://www.team17.com/games/overruled |
| Time Warp (itch.io) | Her 10 saniyede başka bir uzay-zamana geçen platform oyunu | https://zoglu.itch.io/time-warp |
| Shuffle Man (itch.io) | Özette "her şey 5 saniyede değişebilir" diye anıldı (doğrulanamadı) | https://evangeder.itch.io/shuffleman |
| Flip the Rule (CrazyGames) | Yerçekimi temelli, yayın öncesi | https://www.crazygames.com/game/flip-the-rule |
| Twist (Ketchapp) | Tek dokunuşla zıplama/platform, aynı tür | https://apps.apple.com/us/app/twist/id1052231801 |

---

## 5. Kaynak × aday özet matrisi (derin araştırılan 10 aday)

İşaretler: ✔0 = arandı, ilgili bir şey çıkmadı · ✔! = arandı, benzer/çakışan bir şey çıktı · ✘ = doğrudan erişilemedi · (D) = dolaylı (arama motoru dizini)

| Aday | Genel web | Play (D) | App Store (D) | Steam (D) | itch vb. (D) | YouTube (D) | IG/TikTok (D) | Marka aynaları (D) | WIPO / EUIPO / USPTO / TÜRKPATENT |
|---|---|---|---|---|---|---|---|---|---|
| Twist O'Clock | ✔! (1961 filmi) | ✔! ("…O'Clock" uygulamaları) | ✔! (Ketchapp Twist, O'Clock widget) | ✔! (Die O'Clock) | ✔! (Fly O'Clock) | ✔0 | ✔! (kafe, pastane) | ✔! (TWIST sınıf 9 kayıtları) | ✘ ✘ ✘ ✘ |
| Stopwatch Shuffle | ✔0 | ✔0 (yalnızca kronometre araçları) | ✔0 (yalnızca kronometre oyunları) | ✔0 | ✔0 | ✔0 ("Stop Time Shuffle" dans) | ✔0 | ✔! (STOPWATCH ve SHUFFLE ayrı kayıtlar, ilgisiz) | ✘ ✘ ✘ ✘ |
| 5-Second Shuffle | ✔! (5 Second Rule) | ✔! (5 Second Rule uygulamaları) | ✔! | ✔0 | ✔! (Shuffle Man) | ✔0 | ✔0 | ✔! (5 SECOND RULE tescili) | ✘ ✘ ✘ ✘ |
| Tickwarp | ✔0 | ✔0 | ✔0 | ✔! (Tick Hop) | ✔! (Time Warp) | ✔! (Minecraft tick warp) | ✔0 | ✔0 | ✘ ✘ ✘ ✘ |
| Rulequake | ✔0 | ✔0 | ✔0 | ✔! (Quake) | – | ✔! (Quake videoları) | ✔0 | ✔! (QUAKE kayıtları) | ✘ ✘ ✘ ✘ |
| Shiftopia | ✔0 | ✔! (Shifto) | ✔0 | ✔0 | ✔0 | ✔! (Shiftopie kanalı) | ✔! (TikTok shiftopiia) | ✔0 | ✘ ✘ ✘ ✘ |
| FiveTwist | ✔! (Twist 5) | ✔0 | ✔! (Quintwist) | ✔0 | ✔! (Twist 5) | ✔! (saç modeli) | ✔! (saç modeli) | ✔0 | ✘ ✘ ✘ ✘ |
| Twistwatch | ✔! (TimeTwist Watch) | ✔! (Twist saat yüzü) | ✔! | ✔0 | – | ✔! (şarkı, Fossil) | ✔0 | ✔0 | ✘ ✘ ✘ ✘ |
| Tick Twist | ✔! (Tricky Twist) | ✔0 | ✔! (Ketchapp Twist) | ✔! (Tick) | – | ✔! (Tick Twister aleti) | ✔0 | ✔0 | ✘ ✘ ✘ ✘ |
| Five Second Shift | ✔! (5 second uygulamaları) | ✔! (5D Shift) | ✔! (Shift Game) | ✔! (Second Shift) | ✔! ("5 seconds" / "Five Seconds V1") | ✔! (Mel Robbins) | ✔0 | ✔0 | ✘ ✘ ✘ ✘ |

Diğer 23 aday için genel web araması (WebSearch) yapıldı. Bunların çoğunda sonuçlarda Play, App Store, Steam ve itch.io sayfaları da çıktı. Ayrıntılar bölüm 3'te.

---

## 6. Eksikler ve önerilen manuel kontroller

1. **Google Play ve App Store'da canlı arama:** Gerçek bir cihazdan TR, US ve DE mağazalarında "Stopwatch Shuffle", "Twist O'Clock" ve diğer finalistler aranmalı. Bu çalışma dizin taramasıyla sınırlı kaldı.
2. **Steam ve itch.io'da canlı arama:** Tam isim ve varyantlar (boşluksuz, tireli) aranmalı.
3. **Marka veri tabanları (erişilemedi):** TÜRKPATENT, EUIPO eSearch / TMview, USPTO Trademark Search ve WIPO Global Brand Database'de sınıf 9 (indirilebilir oyun yazılımı), sınıf 41 (çevrim içi oyun hizmetleri) ve sınıf 28 (oyuncak/merchandise) için tam ve benzer isim araması yapılmalı.
4. **Alan adı ve sosyal medya kullanıcı adları:** stopwatchshuffle.com / .net / .app ile Instagram, TikTok ve YouTube kullanıcı adlarının müsaitliği kontrol edilmeli. Bu çalışmada alan adı (WHOIS) kontrolü **yapılmadı**.
5. **Shuffle Man (itch.io)** sayfasının açıklaması doğrudan açılıp kavram benzerliği teyit edilmeli.
