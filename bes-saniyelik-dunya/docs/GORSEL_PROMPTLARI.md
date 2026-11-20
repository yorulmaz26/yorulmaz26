# 5 Saniyelik Dünya — Profesyonel Görsel Üretim Promptları

Bu belge oyundaki **bütün görsellerin** listesini ve her biri için hazır promptları içerir. Promptlar İngilizcedir (Midjourney, DALL·E, Ideogram, Leonardo, Stable Diffusion vb. İngilizcede daha tutarlı sonuç verir). Açıklamalar Türkçedir.

**Hedef görünüm:** çocuksu değil, **premium indie mobil oyun** kalitesinde; stilize ama olgun, resimsi (painterly) dokular, sinematik yumuşak ışık, rafine renkler. Proje başındaki istek gereği **aydınlık ve açık renkli** kalır; koyu tema yoktur.

---

## 0. Nasıl kullanılır

1. Her promptun başına **Ortak Stil Bloğu**nu (STYLE) ekleyin. Tutarlılık için bütün görsellerde aynı bloğu kullanın.
2. Desteklenen araçlarda **Negatif Prompt**u da ekleyin.
3. Aynı serideki görselleri (ör. bir haritanın bütün parçaları) mümkünse **aynı seed / stil referansı** ile üretin. Midjourney'de ilk beğendiğiniz görseli `--sref` olarak kullanın.
4. Şeffaf arka plan gereken görselleri (✅ işaretli) düz beyaz/yeşil arka planla üretip arka planını kaldırın (remove.bg, Photoshop vb.) ya da şeffaf PNG destekleyen bir araç kullanın.
5. Dosyaları belirtilen **adla** ve **boyutla** `art_input/` klasörüne koyup bana gönderin. Oyuna yerleştirme, atlas paketleme ve hizalamayı ben yaparım.

### Ortak Stil Bloğu (STYLE) — her promptun başına ekleyin

```
Premium stylized 2D mobile game art, polished indie game quality, refined painterly textures with crisp clean edges, soft cinematic lighting with gentle rim light, subtle ambient occlusion, airy bright daylight palette, harmonious pastel-but-mature color grading (sky blue #87CEFA, turquoise #58D6D0, fresh green #73D65C, sun yellow #FFD54F, warm orange #FF9F43, soft pink #FFB6C9, deep navy accents #253858), elegant shape language, readable silhouettes, high detail, no text, no watermark
```

### Negatif Prompt — destekleyen araçlarda

```
childish, toddler style, baby cartoon, chibi, kawaii, googly eyes, faces on objects, crayon, clip art, flat vector clip art, comic sans, dark, gloomy, horror, neon cyberpunk, grunge, photorealistic photo, 3D render plastic look, blurry, noisy, jpeg artifacts, text, letters, watermark, signature, logo, border, frame, drop shadow box, cropped, cut off
```

### Teknik kurallar

| Kural | Değer |
|---|---|
| Format | PNG (şeffaf gereken yerde 32-bit alfa) |
| Karo (tile) görselleri | Kenarları **dikişsiz** tekrar edebilmeli (belirtilen yönlerde) |
| Süslemeler / nesneler | Şeffaf arka plan, nesnenin **alt kenarı** görselin alt kenarına değmeli (zemine oturur) |
| Işık yönü | Bütün görsellerde **sol üstten** gelen yumuşak ışık |
| Perspektif | Tam yandan görünüm (side view, orthographic), platform oyunu kamerası |
| Çözünürlük | Belirtilen boyut veya 2 katı (ben küçültürüm) |

---

## 1. Ana karakter

Mevcut karakter yuvarlak, yüzlü bir "blob". Daha olgun bir görünüm için önerim: **zamanı büken küçük bir kaşif** — kapüşonlu, çevik, göğsünde parlayan bir kronometre (oyunun "5 saniye" teması). Yüz ifadesi sade, gözler küçük ve parlak; çocuksu iri gözler yok.

> Karakterin çarpışma kutusu kareye yakın olduğundan, siluet **kompakt ve yaklaşık kare** olmalı (eni ≈ boyu).

### 1.1 Karakter tasarım sayfası (önce bunu üretin, sonrakilerde referans olarak kullanın)
`character_design_sheet.png` · 2048×1024 · beyaz arka plan

```
[STYLE] Character design sheet of the main hero of a side-scrolling runner game: a small agile time-bending explorer with a compact, almost square silhouette, short hooded cloak in sun yellow and warm orange, slim dark navy undersuit, a round glowing turquoise chronometer emblem on the chest, small determined bright eyes, no mouth or a very subtle one, light boots with soft glow trails. Front view, side view (facing right), back view, and a close-up of the chest chronometer. Clean white background, consistent proportions, professional concept art presentation.
```

### 1.2 Animasyon kareleri (sprite sheet) ✅
`hero_sheet.png` · 8 sütun × 2 satır, her kare **256×256** (toplam 2048×512) · şeffaf arka plan · karakter sağa bakar, her karede ayakları aynı zemin çizgisinde

```
[STYLE] Sprite sheet for a 2D side-scrolling platformer hero (the hooded time-bending explorer from the reference), side view facing right, transparent background, each frame in its own equal 256x256 cell, same scale and same ground line in every frame. Row 1: 6-frame smooth run cycle, then jump take-off, jump rising. Row 2: jump apex, falling, landing squash, hurt (knocked back), defeated (spinning, fading sparkles), celebration (arms up, cape fluttering), power-up activation (chronometer glowing brightly), idle breathing.
```

### 1.3 Karakter görünümleri (20 skin)
Aynı sprite sheet'i her görünüm için aşağıdaki satırı ekleyerek üretin. Dosya adı: `hero_<id>_sheet.png`.

```
[STYLE] Same sprite sheet layout and same hero proportions as the reference, but costume variant: <AÇIKLAMA>
```

| id | Ad | `<AÇIKLAMA>` |
|---|---|---|
| pati | Pati (varsayılan) | sun yellow and warm orange hooded cloak, turquoise chronometer |
| damla | Damla | turquoise and white water-themed cloak with droplet pattern, leaf clasp |
| cilek | Çilek | berry pink cloak with tiny seed pattern, green leaf hood tip |
| nane | Nane | mint green sporty jacket with a red cap |
| bulut | Bulut | white and sky blue cloud-soft cloak, ribbon bow |
| murdum | Mürdüm | violet mage cloak with star embroidery, pointed hood |
| gunes | Güneş | golden royal cloak with a small elegant crown |
| kedi | Kedi | orange tabby cat-ear hood with subtle stripes |
| tavsan | Tavşan | lavender hood with long bunny ears |
| robot | Robot | light steel blue armored suit with antenna and chest panel lights |
| astronot | Astronot | white space suit with glass helmet and orange details |
| papatya | Papatya | cream cloak with a daisy flower pin |
| kum | Kum Tanesi | desert sand cloak with a pink headband and scarf |
| penguen | Penguen | navy and white penguin-style coat with orange scarf |
| mantar | Mantar | red mushroom-cap hood with white spots |
| ari | Arı | honey yellow and dark brown striped suit with small antennae |
| saatci | Saatçi | bronze clockmaker coat with a top hat and gears |
| kaos | Kaos | pink and turquoise swirling cloak, star-shaped glasses |
| yildiz | Yıldız | pale gold cloak with a floating halo |
| gokkusagi | Gökkuşağı | white cloak with soft rainbow gradient trims and headphones |

---

## 2. Harita temaları (10 harita)

Her harita için **8 dosya** gerekir. Aşağıdaki şablonu kullanın; `<TEMA>` yerine o haritanın tema açıklamasını yazın.

| Dosya | Boyut | Not |
|---|---|---|
| `mapXX_sky.png` | 1080×1920 | Dikey gökyüzü. Sadece gökyüzü, yer yok |
| `mapXX_far.png` ✅ | 2048×640 | Uzak paralaks katmanı. **Yatayda dikişsiz**, üst yarı şeffaf |
| `mapXX_mid.png` ✅ | 2048×640 | Yakın paralaks katmanı. **Yatayda dikişsiz**, üst kısım şeffaf |
| `mapXX_ground_top.png` | 512×512 | Zemin yüzey karosu. Üstte çim/kar/kum şeridi. **Yatayda dikişsiz** |
| `mapXX_ground_fill.png` | 512×512 | Zemin iç dolgu karosu. **Her yönde dikişsiz** |
| `mapXX_ceiling.png` | 512×512 | Tavan yüzeyi. Ters yer çekiminde koşulan yüzey; alt kenarda yüzey şeridi. **Yatayda dikişsiz** |
| `mapXX_platform.png` ✅ | 1536×384 | Havada duran platform: sol uç, orta (yatayda tekrar eden), sağ uç yan yana, her biri 512×384 |
| `mapXX_decor.png` ✅ | 3072×1024 | 6 süs nesnesi, her biri 512×1024 hücrede, alt kenara oturur |

### Şablonlar

**Gökyüzü**
```
[STYLE] Vertical mobile game sky background for <TEMA>, bright airy daylight sky gradient, soft volumetric clouds, gentle sun glow, atmospheric depth, empty lower area, no ground, no characters, portrait 9:16.
```

**Uzak paralaks katmanı**
```
[STYLE] Seamless horizontally tileable far background parallax layer for a side-scrolling game, <TEMA>, distant silhouettes with strong atmospheric perspective (light, hazy, low contrast), side view, transparent sky above the shapes, left and right edges match perfectly.
```

**Yakın paralaks katmanı**
```
[STYLE] Seamless horizontally tileable mid-ground parallax layer for a side-scrolling game, <TEMA>, medium-distance landscape shapes with more detail and contrast than the far layer, side view, transparent area above the silhouettes, left and right edges match perfectly.
```

**Zemin yüzey karosu**
```
[STYLE] Seamless horizontally tileable ground tile for a 2D platformer, side view cross-section, <TEMA>, top 25% is the walkable surface layer with a crisp readable edge, below it the earth/material texture, left and right edges tile perfectly, square tile.
```

**Zemin dolgu karosu**
```
[STYLE] Seamless tileable (all four edges) underground fill texture for a 2D platformer, <TEMA>, subtle material detail, low contrast so it does not distract, square tile.
```

**Tavan karosu**
```
[STYLE] Seamless horizontally tileable ceiling tile for a 2D platformer where the hero can run upside down, <TEMA>, the walkable surface layer is at the BOTTOM edge of the tile with a crisp readable edge, material texture above it, square tile.
```

**Havada duran platform**
```
[STYLE] Floating platform pieces for a 2D side-scrolling platformer, <TEMA>, side view, transparent background, three pieces side by side in equal cells: left end cap, middle piece that tiles seamlessly horizontally, right end cap; flat walkable top surface clearly readable, thickness about one third of the height of the cell.
```

**Süs nesneleri (6'lı)**
```
[STYLE] Set of six non-interactive decoration props for a 2D side-scrolling game, <TEMA>, side view, each prop isolated in its own equal cell on a transparent background, bottom of each prop touching the bottom of its cell, varied heights: <SÜSLER>.
```

### Harita açıklamaları

| Harita | `<TEMA>` | `<SÜSLER>` |
|---|---|---|
| 01 — Yeşil Vadi | lush green valley with rolling hills, wildflower meadows and sunny countryside | tall leafy tree, round bush, pink wildflowers, yellow wildflowers, slim birch tree, mossy rock |
| 02 — Ters Gökyüzü | floating sky islands above the clouds, soft pink and blue sky, cloud platforms made of compact white cloud stone | hot air balloon, small cloud puff, rainbow arch, tiny floating island, hanging star lantern, second balloon |
| 03 — Hız Çölü | golden desert with dunes, red mesas and a speed-race track feeling | saguaro cactus, small cactus cluster, sandstone rock, palm tree, wooden arrow signpost, bleached rock formation |
| 04 — Buz Krallığı | bright ice kingdom with snow, glacier walls and blue crystal formations | snowy pine tree, ice crystal cluster, frozen rock, tall crystal spire, small snowy pine, snowman-like ice sculpture (elegant, not cute) |
| 05 — Hayalet Ormanı | luminous enchanted forest in daylight, lavender and mint tones, glowing mushrooms and floating light motes (bright, not spooky) | glowing pink mushroom, glowing cyan mushroom, hanging lantern, magical twisted tree, lavender bush, jar of fireflies |
| 06 — Zıplayan Şehir | colorful modern rooftop city with springboards, cranes and pastel buildings | street lamp, potted tree, park bench, fire hydrant, small pastel building, flower box |
| 07 — Minik Dünya | macro world seen from an insect's size: giant flowers, giant mushrooms, dew drops and grass blades | giant daisy, giant red mushroom, ladybug, tall grass blades, dew drop, giant purple flower |
| 08 — Ayna Diyarı | crystal mirror realm with prisms, reflective surfaces and soft rainbow light refractions | ornate standing mirror, crystal cluster, glass prism with rainbow beam, tall crystal, second mirror, small rainbow arc |
| 09 — Zaman Şehri | warm brass steampunk clock city with clock towers, gears and pendulums (bright and clean) | clock on a lamp post, large brass gear, hourglass, silver gear, gas lamp, small clock tower |
| 10 — Kaos Adaları | surreal floating islands mixing all previous worlds, pastel pink and cyan sky, portals | swirling portal, crystal cluster, giant flower, brass gear, balloon, small floating island |

---

## 3. Engeller ve oyun nesneleri ✅

Hepsi şeffaf arka plan, yandan görünüm.

| Dosya | Boyut | Prompt |
|---|---|---|
| `spike.png` | 512×288 | `[STYLE] Single row unit of sharp crystal-metal floor spikes for a 2D platformer, side view, clearly dangerous but elegant, steel with subtle turquoise highlights, transparent background, flat bottom edge, tileable horizontally.` |
| `saw.png` | 1024×1024 | `[STYLE] Spinning circular saw blade hazard for a 2D platformer, front view, polished steel teeth, warm orange hub with brass details, slight motion sheen, perfectly circular and centered, transparent background.` |
| `spring_idle.png`, `spring_pressed.png` | 512×448 | `[STYLE] Mechanical jump pad / spring launcher for a 2D platformer, side view, sleek pink-red pad on a steel coil spring and metal base, <idle: extended / pressed: compressed>, transparent background, bottom touches the cell bottom.` |
| `boost.png` | 1024×256 | `[STYLE] Speed boost floor pad for a 2D runner game, side view, turquoise glowing strip with bright chevron arrows pointing right, energy glow, transparent background.` |
| `checkpoint_off.png`, `checkpoint_on.png` | 384×800 | `[STYLE] Checkpoint flag pole for a 2D platformer, side view, slim metal pole with a small lantern on top, <off: grey folded flag, unlit lantern / on: bright green waving flag, glowing lantern>, transparent background.` |
| `goal.png` | 1280×1680 | `[STYLE] Level finish gate for a 2D runner game, elegant arch with a glowing rainbow energy portal, checkered finish banner on top, two white stone pillars, side/front view, transparent background.` |
| `chaos_wall.png` | 1024×1024 | `[STYLE] Vertically tileable swirling chaos storm wall that chases the player, purple-pink energy clouds with sparks, two faint glowing eyes, transparent background, top and bottom edges tile seamlessly.` |
| `platform_ghost.png` | 512×320 | `[STYLE] Ghost platform tile for a 2D platformer that fades in and out, translucent lavender crystal slab with soft inner glow and a dashed light outline, side view, transparent background, tileable horizontally.` |
| `platform_shift.png` | 512×320 | `[STYLE] Shifting platform tile for a 2D platformer, golden-yellow mechanical slab with up/down arrow light indicators, side view, transparent background, tileable horizontally.` |
| `platform_move.png` | 512×320 | `[STYLE] Moving platform tile for a 2D platformer, light blue metal hover platform with bolts and small thrusters underneath, side view, transparent background, tileable horizontally.` |

## 4. Toplanabilirler ✅

| Dosya | Boyut | Prompt |
|---|---|---|
| `coin_sheet.png` | 6 kare × 256 = 1536×256 | `[STYLE] 6-frame spinning gold coin animation sheet for a mobile game, embossed clock emblem in the center, shiny gold with warm highlights, frames: front, three-quarter, side-thin, edge, side-thin, three-quarter, transparent background, equal cells.` |
| `star.png` | 512×512 | `[STYLE] Collectible golden star for a mobile platformer, faceted crystal-gold material, soft golden glow aura, no face, centered, transparent background.` |
| `piece.png` | 512×512 | `[STYLE] Hidden collectible puzzle piece artifact, violet crystal puzzle shape with a small golden star engraving, mysterious soft glow, centered, transparent background.` |
| `powerup_bubble.png` | 512×512 | `[STYLE] Translucent glass bubble container for a power-up item, soft white rim highlight and subtle rainbow sheen, empty inside, centered, transparent background.` |

## 5. Arayüz (UI)

Arayüz için tutarlı **ikon seti** gerekiyor: yuvarlatılmış, hafif kabartmalı, temiz. Hepsi şeffaf arka plan ✅.

### 5.1 Ortak ikon stili (ikon promptlarının başına STYLE yerine bunu ekleyin)
```
Premium mobile game UI icon, clean glossy-but-refined style, soft gradient fill, subtle inner highlight, thin darker outline, gentle drop shadow, bright light palette, centered on transparent background, consistent icon set, no text
```

### 5.2 Para birimi ve genel ikonlar — 256×256
`icon_<ad>.png` → `<ad>`: prompt

| Ad | Prompt eki |
|---|---|
| coin | gold coin with embossed clock emblem |
| gem | faceted cyan diamond gem |
| star | golden star (filled) |
| star_empty | empty star outline slot, pale grey-blue |
| heart | glossy coral-red heart (life) |
| heart_empty | empty heart slot, pale grey |
| trophy | golden trophy cup with star |
| gift | wrapped gift box, pink with gold ribbon |
| calendar | calendar page with pink header |
| missions | rolled scroll with checklist |
| collection | violet puzzle piece |
| profile | person silhouette in a blue circle |
| shop | shopping bag in warm orange |
| map | folded world map with a route line |
| endless | infinity symbol in a turquoise circle |
| crown | golden crown |
| lightning | lightning bolt in an orange circle |
| lock | padlock |

### 5.3 Buton simgeleri (beyaz, düz glif) — 128×128
`glyph_<ad>.png`: `Minimal white UI glyph icon, rounded strokes, centered, transparent background: <AÇIKLAMA>`

play (oynat üçgeni), pause, home, retry (döngü oku), next (sağ ok), back (sol ok), close (X), check, plus, settings (dişli), music, music_off, sound, sound_off, vibrate (titreşen telefon), info, tap (dokunan parmak), hold (basılı tutan parmak), run (koşan figür).

### 5.4 Mekanik rozetleri (10) — 256×256
`mech_<ad>.png`: `[İkon stili] Circular badge icon representing a game rule: <AÇIKLAMA>`

| Ad | Renk | Açıklama |
|---|---|---|
| normal | green | a bright sun |
| gravity | blue | two opposite vertical arrows (gravity flip) |
| fast | orange | double fast-forward chevrons |
| ice | ice cyan | a snowflake |
| ghost | violet | a fading translucent platform with a small ghost wisp |
| bouncy | pink | a coiled spring |
| small | turquoise | a figure shrinking with inward arrows |
| control | magenta | two crossing swap arrows (reversed controls) |
| slow | periwinkle | an hourglass |
| shift | golden yellow | a platform with up and down arrows |

### 5.5 Güçlendirme rozetleri (8) — 256×256
`power_<ad>.png`: `[İkon stili] Circular power-up badge icon: <AÇIKLAMA>`

shield (turkuaz kalkan), magnet (kırmızı mıknatıs), double_jump (mavi, iki yukarı ok / kanat), invincible (altın parlayan yıldız), extra_life (pembe kalp ve artı), coin_x2 (turuncu, altın ve "x2" yerine iki üst üste altın), star_finder (mor, büyüteç içinde yıldız), checkpoint_guard (yeşil, bayrak ve kalkan).

### 5.6 Buton ve panel zeminleri (9-patch) ✅
`button_<renk>.png` · 240×240 · köşe yarıçapı ~80 px · altta ~20 px koyu "kalınlık" şeridi
```
Premium mobile game UI button background, rounded rectangle, <RENK> soft vertical gradient, subtle top inner highlight, slightly darker bottom edge giving thickness, clean, no text, no icon, transparent background, symmetrical so it can be 9-sliced
```
Renkler: yellow #FFD54F, orange #FF9F43, pink #FF8FB1, turquoise #58D6D0, green #73D65C, blue #6FB7F5, purple #B388FF, white, grey #C9D3E0, red #FF6B6B.

`panel.png` · 320×320
```
Premium mobile game UI panel background, rounded rectangle, off-white (#FDFEFF) with very subtle cool gradient, thin light blue-grey border, soft drop shadow, clean, no text, transparent background, symmetrical for 9-slicing
```

### 5.7 Oyun logosu ✅
`game_logo.png` · 1800×840
```
Premium mobile game title logo "5 Saniyelik Dünya" (Turkish text, exact spelling with dotted ü), a large bold "5" inside a golden stopwatch emblem on the left, the words "Saniyelik" and "Dünya" stacked on the right in a bold rounded modern display font, white letters with turquoise and coral outlines, subtle glossy highlights, bright cheerful but polished, transparent background
```
> Not: Yazılı logolarda yapay zekâ harfleri bozabilir (özellikle **ü**). Gerekirse yazıyı ben ekleyebilirim; yalnızca kronometre amblemini üretin: `...golden stopwatch emblem with a large "5", no other text`.

---

## 6. Mağaza (Google Play) görselleri

| Dosya | Boyut | Prompt |
|---|---|---|
| `play_icon.png` | 512×512 | `[STYLE] Mobile game app icon, the hooded time-bending explorer hero mid-jump in front of a glowing golden stopwatch "5", bright sky blue to turquoise background, bold readable at small size, rounded square composition, no other text` |
| `feature_graphic.png` | 1024×500 | `[STYLE] Google Play feature graphic for a side-scrolling runner game: the hooded explorer hero leaping across floating platforms that transform between worlds (green valley, sky islands, desert, ice kingdom) from left to right, a glowing stopwatch in the sky, dynamic composition, empty space on the left third for a title, no text` |

---

## 7. Teslim kontrol listesi

- [ ] `character_design_sheet.png` + `hero_sheet.png` (+ isteğe bağlı 19 skin sheet'i)
- [ ] 10 harita × 8 dosya = 80 dosya (`map01_sky.png` … `map10_decor.png`)
- [ ] Engeller ve nesneler: 13 dosya
- [ ] Toplanabilirler: 4 dosya
- [ ] UI: 18 genel ikon, 19 glif, 10 mekanik, 8 güçlendirme, 10 buton, 1 panel, 1 logo
- [ ] Mağaza: ikon + öne çıkan görsel

Dosyaları `art_input/` klasörüne koyup gönderdiğinizde: arka plan temizliği kontrolü, boyutlandırma, karo dikiş kontrolü, atlas paketleme ve oyuna yerleştirme işlemlerini yapar, yeni ekran görüntülerini ve APK'yı gönderirim. Karakter sprite sheet'i gelirse karakter çizimini parça sistemi yerine kare animasyonuna geçiririm.
