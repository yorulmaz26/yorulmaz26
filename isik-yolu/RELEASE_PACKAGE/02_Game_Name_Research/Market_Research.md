# İsim pazar ve marka araştırması

**Araştırma tarihi:** 27–28 Kasım 2026 (UTC)
**Kapsam:** 31 İngilizce aday; oyun ve yazılım kategorilerinde aynı ya da benzer adlar.
**Önemli not:** Aşağıdaki "çakışma bulunmadı" ifadeleri, yalnızca o gün o kaynakta yapılan aramanın sonucudur.
Bir adın boş olduğu ya da tescil edilebileceği anlamına gelmez. Marka başvurusundan önce bir marka vekili ya da
avukatla resmî araştırma yaptırılmalıdır.

## 1. Kaynaklar ve erişim durumu

| Kaynak | Yöntem | Durum |
|---|---|---|
| Google Play (ABD, İngilizce) | `play.google.com/store/search?q=…&c=apps&hl=en&gl=US` sayfası doğrudan indirildi, uygulama adları ayıklandı | **Erişildi** (31 aday) |
| Google Play (Türkiye, Türkçe) | aynı arama, `hl=tr&gl=TR` | **Erişildi** (31 aday) |
| Apple App Store (ABD) | `apps.apple.com/us/iphone/search?term=…` web araması | **Erişildi** (31 aday) |
| Steam | `store.steampowered.com/search/?term=…` | **Erişildi** (31 aday) |
| USPTO (ABD marka ofisi) | Trademark Search arka uç API'si, kelime markası araması | **Erişildi** (31 aday). Canlı/ölü durumu, sınıflar ve mal/hizmet listesi okundu. |
| Google / Bing genel web | Web arama aracı (arama motoru dizini) | **Kısmen.** İlk turda 22 aday için yapıldı (5 aday için arama kotası bitti). Son 6 aday ve ilk 5 için ayrıca yapıldı. Bing doğrudan erişime kapalıydı (ağ izni yok). |
| YouTube, Instagram, TikTok | Web arama aracında `site:` ve ad aramaları | **Kısmen.** Doğrudan erişim yok (ağ izni yok). Yalnızca arama motoru dizini üzerinden. |
| EUIPO / TMview | `www.tmdn.org/tmview` API'si | **Erişilemedi**: istek güvenlik duvarınca reddedildi ("Request Rejected"). |
| WIPO Global Brand Database | `branddb.wipo.int` | **Erişilemedi**: sayfa açılıyor ama sonuçlar JavaScript ve bot korumasıyla yükleniyor; otomatik sorgu yapılamadı. |
| TÜRKPATENT | `www.turkpatent.gov.tr` | **Erişilemedi**: marka araştırması oturum ve doğrulama gerektiren ayrı bir sistemde. |

Ham veriler: `raw/direct_research.json` (25 aday), `raw/extra_candidates.json` (6 ek aday),
`raw/prismway_extra.json` ("Prism Way", "Prismway game", "Prismway puzzle" aramaları). Betik:
`tools/magaza/isim_arastirma.py`.

## 2. Türkçe ad, tema ve hedef kitle

- **Türkçe ad:** Işık Yolu ("ışığın yolu"). Ad korunur.
- **Tema:** Işık kaynağından çıkan ışın aynalar, prizmalar, renk süzgeçleri, bloklar, anahtar/kapı ve mantık
  düzenekleriyle hedeflere yönlendirilir. 500 bölüm, 5 dünya, 3 yıldız, günlük bulmaca, görünümler.
- **Görsel kimlik:** Gece mavisi zemin, altın ışın, turkuaz hedef, mor kaynak; amblemde iki aynadan yansıyan ışın.
- **Hedef kitle:** Her yaştan mantık/bulmaca oyuncusu. Kısa oturumlar, internetsiz oyun.

## 3. Önemli bulgular

### Doğrudan çeviriler kalabalık
- **Light Path:** App Store'da "Light Path - Puzzle & Maze", "Amunara Light Path", "Phase Loom: Light Path Puzzle";
  Play'de aynı adlı uygulamalar; USPTO'da canlı "LIGHT PATH" ve "PATH LIGHT" kayıtları (sınıf 9 dahil).
- **Path of Light:** "Archaica: The Path of Light" aynı mekanikle (ayna/lazer) Steam, iOS ve konsollarda.
- **Prism Path, Lumen Path, Mirror Beam:** Google Play'de birebir aynı adlı ışık/ayna bulmacaları var.

### Tescilli ya da kullanılan adlar
- **Beamwise:** USPTO'da canlı BEAMWISE, sınıf 9 (yazılımı kapsar).
- **Glowline:** USPTO'da canlı GLOWLINE, sınıf 9.
- **Beamora:** USPTO'da canlı BEAMORA, sınıf 9. Play'de "Beamora: Screen Mirror & Cast".
- **Lightloom:** USPTO'da canlı LIGHTLOOM, sınıf 25. Ayrıca Adobe "Lightroom" ile karışma riski var.
- **Lumibeam:** "LumiBeam™" adlı projektör ve fener satılıyor, Instagram hesabı var.
- **Lumora, Lumaze, Prismora, Glint Path, Photon Path:** Mağazalarda ya da itch.io'da aynı adlı oyunlar var.
- **Shinepath:** "Shining Path" (Sendero Luminoso, silahlı örgüt) çağrışımı. İspanyolca pazarda marka güvenliği
  sorunu.

### Prismway ayrıntısı
- **Mağazalar:** Google Play (ABD ve TR), App Store (ABD) ve Steam'de "Prismway", "Prism Way", "Prismway game" ve
  "Prismway puzzle" aramalarında aynı ya da adı içeren uygulama/oyun **bulunmadı**. Play'de "Prism: Daily Puzzle"
  gibi "Prism" önekli bulmacalar var. Bu, tür kalabalığı demektir; aynı ad değildir.
- **USPTO, "PRISMWAY" kelime markası:** 0 sonuç.
- **USPTO, "PRISM" + "WAY" birlikte:**
  - **THE PRISM WAY.** Prism Venture Group LLC, sınıf 41. Mal/hizmetler: iş eğitimi, yönetici koçluğu, yapay zekâ
    benimseme seminerleri. Durum: yeni başvuru, inceleyiciye atanmamış. Oyunla ilgisi yok.
  - **PRISM: LIGHT THE WAY.** Eidos Interactive, sınıf 9 ve 41 canlı, "registered and renewed". 2007'de çıkmış,
    renkli ışınları hedefe yönlendiren bir bulmaca oyunu (DS, PC, mobil).
    - Bu, Prismway için **en önemli komşu marka**.
    - Ortak öğe: "PRISM" ve "WAY". "Prism" ışık bulmacalarında yaygın ve zayıf (tanımlayıcı) bir öğe: USPTO'da
      "PRISM" içeren ~1.940 kayıt var.
    - Bu yüzden risk **düşük–orta** olarak değerlendirildi.
    - Tescil başvurusundan önce bir marka vekilinin görüşü alınmalı.
- **Genel web:**
  - prismway.com: Michigan'da bir koçluk şirketi (PRISMway).
  - "The Prism Way": Los Angeles'ta bir sivil toplum kuruluşu (Instagram @theprismway, YouTube @ThePrismWay).
  - Oyun ya da yazılım kategorisinde değiller.
- **Sosyal medya:** "Prismway" adına oyunla ilgili Instagram/TikTok/YouTube hesabı bulunmadı (yalnızca arama motoru
  dizini üzerinden; doğrudan erişim yoktu). @prismway kullanıcı adlarının boş olup olmadığı **elle kontrol
  edilmeli**.
- **İlk turdaki web araştırması:** PRISMWORK (US Reg. 6939004, sınıf 41) komşu marka olarak not edildi. Benzerliği
  düşük.

### Diğer güçlü adaylar
- **Beam Route, Lux Trail, Glimway, Ray Route:** Hiçbir doğrudan kaynakta eşleşme yok. Ancak:
  - **Beam Route** ve **Ray Route:** iki genel kelime. Marka olarak korunması zor, "route" telaffuzu belirsiz.
  - **Lux Trail:** aramada LUX markası ve yürüyüş "trail" sonuçları baskın.
  - **Glimway:** anlamı belirsiz, akılda kalıcılığı düşük.
- **Lumiway:** Mağazalarda eşleşme yok. USPTO'da LUMIWAY sınıf 5 (ilaç) canlı; farklı sınıf.

## 4. Kültürel ve dil kontrolü (Prismway)

| Dil | Okunuş | Anlam/çağrışım |
|---|---|---|
| İngilizce | "PRIZM-way" | prism + way: prizmanın yolu. Olumsuz anlam yok. |
| Türkçe | "prizm-vey" | "prizma" tanıdık. Olumsuz anlam yok. |
| Almanca | "prizm-vey" | "Prisma" tanıdık. Olumsuz anlam yok. |
| İtalyanca | "prizm-uey" | "prisma" tanıdık. Olumsuz anlam yok. |
| Fransızca | "prizm-ue" | "prisme" tanıdık. Olumsuz anlam yok. |
| İspanyolca | "prism-uey" | "prisma" tanıdık. Olumsuz anlam yok. |

Bu tablo dil bilgisine dayanan bir masa başı değerlendirmesidir. Anadili konuşan kişilerle kısa bir kontrol önerilir.

## 5. Sınırlamalar

- EUIPO, WIPO ve TÜRKPATENT sorgulanamadı. Bu üç kaynak için **"çakışma yok" denemez**.
- Google Play ve App Store aramaları yalnızca ABD (ve Play'de TR) vitrinindedir. Diğer ülke vitrinleri ayrıca
  kontrol edilmeli.
- Mağaza arama sonuçları kişiselleştirme ve sıralama nedeniyle zamanla değişir.
- Instagram, TikTok ve YouTube'da kullanıcı adı uygunluğu doğrudan denetlenemedi.
