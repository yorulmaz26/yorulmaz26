# Fragmanlar — Prismway (Işık Yolu)

Tüm videolar oyunun **gerçek oynanışından** üretildi: oyun, testlerde gerçek çizimle oynatılıp 30 FPS kaydedildi.
Reklam, filigran, siyah kenar, esnetme ve oyunda olmayan özellik yok. Müzik ve sesler oyunun kendi sentezlenmiş
sesleridir (ticari kullanım engeli yok). Yazı tipi Roboto (Apache 2.0).

| Klasör / dosya | Biçim | Kullanım |
|---|---|---|
| `Vertical/trailer_tr_vertical.mp4`, `trailer_en_vertical.mp4` | 30,0 sn · 1080×1920 (9:16) · 30 FPS · H.264 High + AAC 192 kb/s | **Play Store / YouTube** sürümü. Eylem çağrısı yok, kapanışta slogan var. |
| `Social_Media/trailer_<tr\|en>_vertical_social_cta1.mp4`, `…_cta2.mp4` | aynı | Instagram Reels, TikTok, YouTube Shorts. Kapanışta "Hemen Oyna!" / "Maceraya Başla!", "Play Now!" / "Start Your Adventure!". **Oyun yayına girmeden paylaşma.** |
| `Horizontal/trailer_tr_horizontal.mp4`, `trailer_en_horizontal.mp4` | 30,0 sn · 1920×1080 (16:9) | YouTube, web |
| `Subtitles/trailer_tr.srt`, `trailer_en.srt` | SRT (UTF-8) | Ekrandaki başlıkların altyazısı (sessiz izleme için yazılar zaten görüntüde) |
| `Social_Media/youtube_cover_<dil>_1280x720.png`, `reels_cover_<dil>_1080x1920.png` | PNG | YouTube kapak görseli, Reels kapağı |
| `Turkish/`, `English/` | — | İlgili dildeki dikey ve yatay video ile altyazının kopyası |
| `Source_Files/` | — | Düzenlenebilir kaynaklar (aşağıda) |

## Senaryo (her iki dil)

| Süre | Bölüm | Görüntü | Ekrandaki yazı (TR / EN) |
|---|---|---|---|
| 0–3 sn | Dikkat çekme | Mantık bölümü 169: son hamlede beş hedef birden yanar | Işığı yönlendir! / Guide the light! |
| 3–8 sn | Oynanış | Bölüm 12, aynalar döndürülür (dokunuşlar halkayla gösterilir) + prizma bölümü 52 | Aynaları döndür · Bütün hedefleri yak / Rotate the mirrors · Light up every target; Prizmalarla renkleri ayır / Split colors with prisms |
| 8–14 sn | Deneyim | Karma bölüm 95 (prizma, süzgeç, blok), blok itme bölümü 74 | Her bölüm farklı bir bulmaca / Every level, a different puzzle; Taşları kaydır / Slide the blocks |
| 14–21 sn | Çeşitlilik | Dünyalar ve bölüm seçimi, anahtar/kapı bölümü 86, görünümler | 5 dünya · 500 bölüm; Anahtarlar ve kapılar; Kendi tarzın / 5 worlds · 500 levels; Keys and doors; Your own style |
| 21–27 sn | Başarı | Renk bölümü 40 çözülür, "Kusursuz!" penceresi, 3 yıldız | 3 yıldızı topla / Collect all 3 stars |
| 27–30 sn | Marka | Amblem, iki dilli ad, slogan, Magic Lumos Software | Işığı yönlendir, yolu bul. / Guide the light, find the way. |

Gerçek oynanış oranı: ~%89 (son ~3,2 sn marka kartı).

## Düzenlenebilir kaynaklar (`Source_Files/`)

- `timeline_<dil>.json`: her bölümün başlangıç/bitiş süresi, ekrandaki yazısı ve geçiş uzunluğu (8 kare); bir kurgu
  programında aynı kesimi kurmak için.
- `clips_<tr|en>/*.mp4`: fragmanda kullanılan ham oynanış klipleri (1080×1920, 30 FPS, yazısız); her klibin
  dokunuş, hamle ve kazanma kareleri `*.events.json` içinde.
- `kit/`: Roboto yazı tipleri (Apache 2.0), üretim betikleri ve kullanım notu; amblemin vektörü
  `03_Logos/Vector/emblem.svg`.
- Ekrandaki yazılar `06_Store_Descriptions/store_texts.<dil>.json` dosyasındaki `trailer_captions` ve üç bölümde
  `screenshot_captions` (bloklar, anahtar/kapı, görünümler) alanlarından gelir; ayrıntı `kit/README.md` içinde.
