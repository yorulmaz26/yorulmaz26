# Yanlış Renk – Ad Çakışması Pazar Araştırması

**Araştırma tarihi:** 2026-11-27 (bütün sorgular bu tarihte yapıldı)
**İlgili belgeler:** [Name_Candidates.md](Name_Candidates.md) · [Final_Name.md](Final_Name.md)

---

## 1. Yöntem notu

**Kullanılan araçlar**

1. **WebSearch** ("standard" mod): Çalışan tek kaynak buydu. Arama motoru ABD bölgesinden sorgu yapıyor ve sonuçları bir dil modeli özetliyor. Her sonucun bağlantısı ve kısa özeti alınabildi.
2. **curl** (oturumun HTTPS proxy'si üzerinden): Mağaza API'leri ve marka veritabanları doğrudan sorgulanmak istendi. Bütün denemeler engellendi (bkz. Bölüm 2).
3. **WebFetch**: Mağaza ve marka toplayıcı sayfalarını okumak için denendi. Bütün denemeler engellendi (bkz. Bölüm 2).

**Sorgu şablonu.** Her güçlü aday için aynı sırayla şu sorgular çalıştırıldı:

* Genel web: `"<ad>" game app` ve gerektiğinde `"<ad>"` tek başına
* Google Play: `site:play.google.com "<ad>"`
* Apple App Store: `site:apps.apple.com "<ad>"`
* Steam ve itch.io: `site:store.steampowered.com "<ad>"` veya `site:store.steampowered.com OR site:itch.io "<ad>"`
* YouTube, Instagram ve TikTok: `site:youtube.com OR site:tiktok.com OR site:instagram.com "<ad>"`
* Markalar: `"<AD>" trademark`. Bu sorgu Justia Trademarks, Markinton, TrademarkElite ve trademarkregistration.app gibi USPTO aynalarını yakalıyor.
* Resmî veritabanları: `site:euipo.europa.eu OR site:tmdn.org OR site:branddb.wipo.int "<AD>"` ve `site:turkpatent.gov.tr …`

Zayıf adaylar için yalnızca genel arama ve/veya birleşik bir mağaza sorgusu yapıldı (hafif kontrol). Bu durum her adayın altında belirtildi.

**Risk sınıfları**

| Sınıf | Tanım |
|---|---|
| **YÜKSEK** | Oyun veya uygulama mağazasında **aynı ya da neredeyse aynı adlı** oyun veya uygulama var |
| **ORTA** | Oyun veya yazılım sınıflarında (Nice 9 / 28 / 41) aynı ya da çok benzer marka kaydı veya başvurusu var; ya da çok benzer adlı ve benzer mekanikli bir oyun var |
| **DÜŞÜK** | Benzer ad yalnızca ilgisiz sektörlerde var (giyim, kimya, fiber optik vb.) |
| **Belirgin çakışma bulunmadı** | Bu tarihteki aramalarda ilgili bir sonuç görülmedi. **Bu, adın boş olduğu anlamına gelmez** (bkz. Erişim sınırları) |

**Yorum kuralı:** Sonuç çıkmaması hiçbir zaman "ad boş" ya da "kullanılmıyor" diye yorumlanmadı. Arama motoru mağazalardaki her sayfayı dizinlemiyor, `site:` ve tam eşleşme operatörlerini her zaman uygulamıyor. Resmî marka veritabanlarındaki kayıtlar ise hiç dizinlenmiyor.

---

## 2. Erişim sınırları

| Kaynak | Ne denendi | Sonuç |
|---|---|---|
| **Apple App Store** (iTunes Search API `itunes.apple.com/search?term=miscolor&entity=software`) | curl, WebFetch | curl: `CONNECT tunnel failed, response 403` (oturumun çıkış proxy'si politika gereği reddetti). WebFetch: `getaddrinfo ENOTFOUND itunes.apple.com` (izin verilen ağ alanları dışında). **Engellendi.** Alternatif olarak `site:apps.apple.com` WebSearch sorguları çalıştı, ancak yalnızca dizinlenmiş sayfaları kapsıyor. |
| **Google Play** (`play.google.com/store/search?q=miscolor&c=apps`) | curl, WebFetch | curl: HTTP yanıtı yok (kod 000). WebFetch: `ENOTFOUND play.google.com`. **Engellendi.** `site:play.google.com` sorguları çoğunlukla geliştirici sayfalarını döndürdü. Mağazanın kendi arama sonuçları görülemedi. Bazı Android uygulamaları yalnızca üçüncü taraf izleyici appgoblin.info üzerinden görülebildi. |
| **Steam** (`store.steampowered.com/api/storesearch/?term=…`) | curl | HTTP 000. **Engellendi.** `site:store.steampowered.com` WebSearch sorguları çalıştı. |
| **itch.io** | Yalnızca WebSearch `site:itch.io` | Kısmen çalıştı. Oyun reçeli sayfaları da göründü. |
| **YouTube, Instagram, TikTok** | WebSearch `site:` sorguları | Bu platformlar oturum açma ve JavaScript gerektirdiği için doğrudan erişilemiyor. `site:` sorguları çoğunlukla alakasız toplayıcı sayfalar döndürdü (Snapchat Explore, Linktree, Lemon8, haber siteleri). **Kapsama zayıf. Sonuç çıkmaması anlamlı değil.** |
| **USPTO** (`tmsearch.uspto.gov`, yeni Trademark Search) | curl | HTTP 000. Site JavaScript tabanlı bir uygulama. **Engellendi.** Yalnızca WebSearch'te görünen üçüncü taraf aynalar (trademark(s).justia.com, markinton.com, trademarkelite.com, trademarkregistration.app, trademarkdir.com) okunabildi. Ayna kayıtları resmî TSDR kaydıyla doğrulanamadı. |
| **Markinton** (`markinton.com/trademark/color-bluff-88211416`) | WebFetch | `ENOTFOUND markinton.com`. **Engellendi.** Kayıt bilgisi yalnızca WebSearch özetinden alındı. |
| **WIPO Global Brand Database** (`branddb.wipo.int`) | curl, WebSearch `site:` | curl HTTP 000. `site:` sorgusu kayıt döndürmedi (veritabanı arama motorlarına dizinlenmiyor). **Sorgulanamadı.** |
| **EUIPO eSearch** (`euipo.europa.eu/eSearch`) ve **TMview** (`tmdn.org/tmview`) | curl, WebSearch `site:` | curl HTTP 000. `site:` sorgusu yalnızca EUIPO kılavuz PDF'lerini döndürdü. **Sorgulanamadı.** |
| **TÜRKPATENT** (`turkpatent.gov.tr/arastirma-yap?form=trademark`) | curl, WebSearch `site:` | curl HTTP 000. `site:` sorgusu yalnızca genel belgeler döndürdü. Marka kayıtları dizinlenmiyor ve arama e-Devlet veya site üzerinden etkileşimli yapılıyor. **Sorgulanamadı.** |
| **Trademarkia** / **Justia arama sayfaları** (`trademarkia.com/search…`, `trademarks.justia.com/search?q=…`) | curl | HTTP 000. **Engellendi.** Justia'nın ayrıntı sayfaları yalnızca WebSearch sonuçlarında göründü. |
| **DuckDuckGo / Bing** doğrudan | curl | HTTP 000. **Engellendi.** |
| **WebSearch'ün kendi sınırları** | — | Yalnızca ABD bölgesinden arama yapıyor. TR, DE, IT, FR ve ES mağaza bölgeleri yalnızca dizinlenmiş bölgesel apps.apple.com sayfaları üzerinden dolaylı olarak görülebildi. Sonuçlar bir model tarafından özetleniyor. Tam eşleşme (`"..."`), `OR` ve `-` operatörleri her zaman uygulanmıyor. Çok küçük, yeni ya da kaldırılmış uygulamalar dizinde olmayabilir. |

Not: curl denemelerinin hepsi aynı 12 adreslik döngüde yapıldı. Reddin nedeni (proxy 403) yalnızca itunes.apple.com için ayrıca doğrulandı. Diğer adreslerde yanıt kodu 000 oldu ve aynı çıkış politikası nedeniyle engellendikleri varsayıldı. Oturumun proxy kuralları politika retlerinin tekrar denenmemesini istediği için yeniden deneme yapılmadı. WebFetch üç farklı alan adında aynı "ENOTFOUND (izinli alan dışı)" hatasını verince başka site denenmedi.

**Sonuç:** Mağazalar ve resmî marka veritabanları **doğrudan sorgulanamadı**. Aşağıdaki bulguların hepsi arama motoru dizinine dayanıyor. Yayın öncesinde mutlaka elle doğrulanmalı. Kontrol listesi [Final_Name.md](Final_Name.md) içinde, Bölüm 7'de.

---

## 3. Özet risk tablosu

| # | Aday | Derinlik | Oyun/uygulama çakışması | Marka (TM) bulgusu | Risk |
|---|---|---|---|---|---|
| 1 | Miscolor | Derin | Aynı ad görülmedi. Benzer: "ColorMismatch: Challenge" (iOS) | MISSCOLOR (ABD, sınıf 25 giyim) | **DÜŞÜK** (belirgin çakışma yok) |
| 2 | Color Bluff | Derin | Aynı ad görülmedi | COLOR BLUFF, ABD seri no 88211416, sınıf 28, "dead" | **ORTA–DÜŞÜK** |
| 3 | Anticolor | Derin | Görülmedi | Aynı marka görülmedi. "ANTI COLOR" mürekkep temizleyicisi (DE), fizik terimi | **DÜŞÜK** |
| 4 | Wrongbow | Derin | Görülmedi | WONDERBOW (sınıf 9/28/41) ile kısmi ses benzerliği | **DÜŞÜK–ORTA** |
| 5 | Lying Colors | Derin | Görülmedi. Benzer: "Fake Colours" (Steam) | Görülmedi | **DÜŞÜK** |
| 6 | Tint Trick | Derin | Görülmedi. Kalabalık "Tint" ailesi (Tint, Tint Twist, Tint Tango, Tint Hero) | TINT MAGIC, TINT WIZDOM vb. (farklı sektörler) | **DÜŞÜK–ORTA** |
| 7 | Not That Color | Derin | Görülmedi. Kavramsal yakınlar: "Not Not", "ColorNot" | Görülmedi | **ORTA** (kavramsal yakınlık, jenerik ifade) |
| 8 | Huedini | Orta | Görülmedi | Houdini türevi markalar (HOUDINI'S, sınıf 28) | **ORTA** |
| 9 | Chromiss | Derin | Görülmedi. "Chromis" fotoğraf uygulaması ve 2014 bulmaca oyunu | CHROMIS (iptal), CHROMIS TECHNOLOGIES (fiber) | **DÜŞÜK–ORTA** |
| 10 | Mischroma | Hafif | Görülmedi | "Michroma" (gıda renklendirici girişimi) | **DÜŞÜK–ORTA** |
| 11 | Chroma Twist | Derin | Görülmedi. Benzer: "TWIST3D" (Chroma Studios), "Tint Twist" | Çok sayıda CHROMA markası, Razer Chroma | **ORTA** |
| 12 | Contracolor | Hafif | itch.io "Contracolor" (oyun reçeli sayfası) | — | **ORTA–YÜKSEK** |
| 13 | Color Nope | Hafif | "Nope" renk eşleştirmeli kart oyunu | — | **ORTA** |
| 14 | Chroma Gates | Hafif | Görülmedi. "Chroma Challenge" aynı mekanikte | — | **ORTA** |
| 15 | Twistint | Hafif | Görülmedi. Benzer: "Tint Twist" | — | **DÜŞÜK–ORTA** |
| 16 | Chroma Trap | Hafif | Görülmedi | — | **DÜŞÜK–ORTA** |
| 17 | Tricky Tints | Hafif | Görülmedi. "Tricky Color", "Tiny Tints" | — | **ORTA** |
| 18 | Not My Color | Hafif | Görülmedi. "Where's My Color?" | — | **DÜŞÜK–ORTA** |
| 19 | Unhue | Hafif | Görülmedi | — | **DÜŞÜK** |
| 20 | Off Color | Hafif | Görülmedi. Ancak olumsuz deyim anlamı var | — | **ORTA** (itibar riski) |
| 21 | Wrong Color | Orta | **Var:** iOS "wrongColor", 2 itch.io oyunu, Android "Wrong Color" (appgoblin) | Görülmedi | **YÜKSEK** |
| 22 | ColorNot | Hafif | **Var:** iOS "ColorNot - Deceive your eyes" (Stroop) | — | **YÜKSEK** |
| 23 | Color Twist | Hafif | **Var:** itch.io "Color Twist" (koşu), iOS "Color Twist" | — | **YÜKSEK** |
| 24 | Same or Not | Hafif | **Var:** "Same Or Not" (GBC), "Same or Not?" (mobil) | — | **YÜKSEK** |
| 25 | Hue Flip | Hafif | **Var:** Android "Hue Flip" (Kasım 2025) | — | **YÜKSEK** |
| 26 | Hue Dash | Hafif | **Var:** iOS "HueDash", "Hue Dash - Color Bump 3D" | — | **YÜKSEK** |
| 27 | Odd Hue Out | Hafif | **Var:** Nintendo Switch "Odd Hue Out!" (2024) | — | **YÜKSEK** |

---

## 4. Aday bazında arama kayıtları

Her sorgu 2026-11-27 tarihinde WebSearch ile çalıştırıldı. Sorgular aşağıda aynen yazıldığı gibidir.

### 4.1 Miscolor — derin kontrol (seçilen ad)

**Taranan kaynaklar:** genel web, Google Play, App Store, Steam, itch.io, Nintendo/Xbox/Microsoft/Epic, YouTube, Instagram/TikTok, Android izleyicileri (appgoblin/appbrain/apkpure), USPTO aynaları, EUIPO/TMview/WIPO (`site:`), TÜRKPATENT (`site:`), alan adı ve kullanıcı adı izleri, sözlük.

| # | Sorgu | Bulgu |
|---|---|---|
| 1 | `"Miscolor" game app` | Aynı ad yok. Benzer: "Mixed Colors" (DellaCore LLC, iOS refleks oyunu) https://apps.apple.com/ca/app/id1212080393 · "Mix Color - Color Game" https://apps.apple.com/us/app/id6736512790 · "ColorMismatch: Challenge" https://apps.apple.com/lc/app/colormismatch-challenge/id6654894163 |
| 2 | `"Miscolor" OR "Miscolour" -wikipedia` | Yalnızca sözlük ve ansiklopedi sayfaları |
| 3 | `site:play.google.com "Miscolor"` | Eşleşme yok (yalnızca MisXu, MISSU, Recolor gibi alakasız geliştirici sayfaları) |
| 4 | `site:apps.apple.com "Miscolor"` | Eşleşme yok (MixedColorFix, Rosco myColor, MakeColor) |
| 5 | `site:store.steampowered.com "Miscolor"` | Eşleşme yok (ColorMinis, Colorless, Recolor) |
| 6 | `site:youtube.com "Miscolor"` | Eşleşme yok (operatör uygulanmamış görünüyor) |
| 7 | `site:instagram.com OR site:tiktok.com "miscolor"` | Eşleşme yok (alakasız toplayıcı sayfalar) |
| 8 | `"MISCOLOR" trademark` | Aynı marka görülmedi. Benzerleri: **MISSCOLOR**, ABD seri no 98475123, sahibi Liu Liye (Henan), sınıf 25 giyim, 2025'te tescilli, "live" https://markinton.com/trademark/misscolor-98475123 · MIXCOLOR 88248204 https://www.trademarkelite.com/trademark/trademark-detail/88248204/MIXCOLOR · MICOLOR (terk edilmiş) https://trademark.justia.com/863/84/micolor-86384594.html |
| 9 | `"Miscolor" itch.io OR "Mis Color" game` | Aynı ad yok. Benzer: "Missing Colors" https://michaellamore.itch.io/missing-colors |
| 10 | `"Miscolor" company OR brand OR studio OR cosmetics` | Aynı ad yok. Benzer: Shenzhen MyColor Cosmetics (Çin, kozmetik) |
| 11 | `"ColorMismatch" challenge app` | "ColorMismatch: Challenge" (geliştirici Ryland Lillibridge, iOS, Stroop tarzı). Ad farklı, kavram yakın |
| 12 | `"Miss Color" game app Android iOS` | Sesteş "Miss Color" adlı bir oyun görülmedi |
| 13 | `"miscolor.com" OR "miscolor.app" OR "miscolor.io" OR "@miscolor"` | Hiçbir iz görülmedi. **Alan adı ve kullanıcı adı boşta mı, doğrulanamadı** (kayıt kuruluşu sorgusu yapılamadı) |
| 14 | `"Miscolour" OR "Mis-Color" OR "MisColor" app game` | Eşleşme yok |
| 15 | `"Miscolor" Nintendo OR Xbox OR "Microsoft Store" OR "Epic Games"` | Eşleşme yok |
| 16 | `site:appgoblin.info OR site:appbrain.com OR site:apkpure.com "Miscolor" OR "Anticolor" OR "Color Bluff"` | Eşleşme yok |
| 17 | `site:itch.io "Color Bluff" OR "Miscolor" OR "Anticolor"` | Eşleşme yok |
| 18 | `site:turkpatent.gov.tr marka araştırma "Miscolor" OR "Yanlış Renk"` | Kayıt döndürmedi (veritabanı dizinlenmiyor). Yalnızca TÜRKPATENT araştırma sayfasının adresi doğrulandı |
| 19 | `site:euipo.europa.eu OR site:tmdn.org OR site:branddb.wipo.int "MISCOLOR" OR "ANTICOLOR" OR "COLOR BLUFF"` | Kayıt döndürmedi (yalnızca kılavuz PDF'leri) |
| 20 | `miscolor definition dictionary meaning verb` | Sözlük: "miscolor" geçişli fiil, (1) yanlış renk vermek, (2) mecazen gerçekleri çarpıtmak. İlk kaydı 1800–1810 https://www.dictionary.com/browse/miscolor |
| — | curl ve WebFetch: iTunes API, Steam API, Google Play araması, Trademarkia, Justia (`miscolor` terimiyle) | Engellendi (bkz. Bölüm 2) |

**Değerlendirme:** Aramalarda oyun veya uygulamalarda belirgin bir çakışma bulunmadı (erişim sınırları: mağaza API'leri, Google Play'in kendi araması ve resmî marka veritabanları sorgulanamadı; sosyal medya kapsaması zayıf). En yakın marka MISSCOLOR. Sesli söylendiğinde "Miscolor" ile neredeyse aynı duyuluyor, ancak giyim sınıfında (25). Oyun ve yazılım sınıflarında (9/41) karışıklık riski düşük görünüyor, ama avukat tarafından değerlendirilmeli. Ayrıca "miscolor" bir sözlük kelimesi olduğu için bazı ofisler, renk oyunu için ayırt edicilik sorusu sorabilir. **Risk: DÜŞÜK (belirgin çakışma yok).**

### 4.2 Color Bluff — derin kontrol

**Taranan kaynaklar:** genel web, Google Play, App Store, Steam, itch.io, YouTube, Instagram/TikTok, USPTO aynaları, Android izleyicileri, EUIPO/WIPO (`site:`).

| # | Sorgu | Bulgu |
|---|---|---|
| 1 | `"Color Bluff" game app` | Aynı ad yok (Color Guess +, Color Sleuth, HUE gibi benzerler) |
| 2 | `site:play.google.com "Color Bluff"` | Eşleşme yok (en yakın: "Bluffy: quiz and bluffing", enovly) |
| 3 | `site:apps.apple.com "Color Bluff" OR "Colour Bluff"` | Eşleşme yok (en yakın: "Colour Blitz") |
| 4 | `site:store.steampowered.com "Color Bluff" OR "Colour Bluff"` | Eşleşme yok (Color Blocks, Colour Bind, Colour Flux) |
| 5 | `"Color Bluff" OR "Colour Bluff" OR "ColorBluff" -poker` | Hanabi kart oyunu stratejisinde "color bluff" terimi https://hanabi.github.io/examples/self-color-bluffs · "BLUFF!" (iOS poker) https://apps.apple.com/us/app/bluff/id6448482151 |
| 6 | `site:youtube.com OR site:tiktok.com OR site:instagram.com "Color Bluff"` | Eşleşme yok (en yakın: "The Color Blew" adlı müzik grubu) |
| 7 | `"COLOR BLUFF" trademark justia OR trademarkia OR uspto` | **COLOR BLUFF**, ABD seri no 88211416, başvuran Playtacular LLC (Highland Park, IL), **sınıf 28 (oyunlar ve oyuncaklar)**, başvuru Kasım 2018, durum **"dead"** (tescil edilmemiş, son işlem Mart 2019) https://markinton.com/trademark/color-bluff-88211416 |
| 8 | `Playtacular "Color Bluff" game` | Piyasada böyle bir ürün görülmedi |
| — | Birleşik sorgular 16, 17 ve 19 (bkz. 4.1) | Eşleşme yok |
| — | WebFetch markinton.com | Engellendi |

**Değerlendirme:** Mağazalarda aynı adlı bir oyun veya uygulama görülmedi (erişim sınırları aynı). Ancak oyun sınıfında geçersiz bir ABD başvurusu var. Başvurunun geçersiz olması, adı kimsenin kullanmadığını kanıtlamaz (başvuru sahibinin ABD hukukunda kullanıma dayalı common-law hakları olabilir). **Risk: ORTA–DÜŞÜK.**

### 4.3 Anticolor — derin kontrol

| # | Sorgu | Bulgu |
|---|---|---|
| 1 | `"Anticolor" game app` | Aynı ad yok. Benzerler: "Color Resist" (iOS) https://apps.apple.com/bo/app/color-resist/id1620129891 · "Colerr" (Android, kelime ve renk eşleşmesi) https://boldaestheticcreative.itch.io/colerr |
| 2 | `"Anticolor" OR "Anti-Color" brand OR company OR software` | "ANTI COLOR" mürekkep lekesi el temizleyicisi (Almanya) https://www.druckerpatronentankstellen.de/en/p/anti-color-hand-cleaner-for-ink · ANICOLOR markası (baskı makinesi yazılımı) https://trademark.justia.com/787/70/anicolor-78770240.html |
| 3 | `site:play.google.com "Anticolor" OR "Anti Color"` | Eşleşme yok (en yakın: "Anycolor", geliştirici Aniway) |
| 4 | `site:apps.apple.com "Anticolor" OR "Anti-Color"` | Eşleşme yok |
| 5 | `site:store.steampowered.com OR site:itch.io "Anticolor" OR "Anti-Color"` | Eşleşme yok |
| 6 | `site:youtube.com OR site:tiktok.com OR site:instagram.com "anticolor"` | Eşleşme yok |
| 7 | `"ANTICOLOR" trademark registration` | Aynı marka görülmedi. Benzerler: ANTICOR (su kimyasalı, geçersiz), ANICOLOR (reg. 3241546), ANOCOLOR |
| 8 | `anticolor quark antired antiblue physics term` | Parçacık fiziğinde yerleşik terim (antikuarkların renk yükü) https://www.britannica.com/science/antired |
| — | Birleşik sorgular 16, 17 ve 19 | Eşleşme yok |

**Değerlendirme:** Aramalarda oyun veya uygulamalarda belirgin bir çakışma bulunmadı (erişim sınırları aynı). Arama sonuçlarını fizik terimi ve temizlik ürünü paylaşıyor. **Risk: DÜŞÜK.**

### 4.4 Wrongbow — derin kontrol

| # | Sorgu | Bulgu |
|---|---|---|
| 1 | `"Wrongbow" game OR app` | Eşleşme yok (Longbow, Bowman, Mr Bow gibi okçuluk oyunları) |
| 2 | `site:play.google.com OR site:apps.apple.com "Wrongbow"` | Eşleşme yok. En yakın: "Wrongle" (kelime oyunu) https://apps.apple.com/fj/app/wrongle/id6746725747 |
| 3 | `"Wrongbow"` | Eşleşme yok ("Wristbow" gibi farklı terimler) |
| 4 | `site:store.steampowered.com OR site:itch.io "Wrongbow" OR "Wrong Bow"` | Eşleşme yok ("Wrong Wrong West", "Overbowed") |
| 5 | `site:youtube.com OR site:tiktok.com OR site:instagram.com "wrongbow"` | Eşleşme yok |
| 6 | `"WRONGBOW" trademark` | Aynı marka görülmedi. Benzer: **WONDERBOW**, seri no 79363967, Avalia Studios GmbH, **sınıf 9, 28 ve 41 (oyun)**, 2024'te tescilli https://trademarkregistration.app/Trademark/Details/79363967 |
| 7 | `"HUEDINI" OR "TINT TRICK" OR "WRONGBOW" OR "CHROMA GATES" trademark justia` | WRONGBOW için kayıt yok |

**Değerlendirme:** Belirgin bir çakışma bulunmadı (erişim sınırları aynı). WONDERBOW ile görsel ve işitsel benzerlik sınırlı, ama aynı sınıflarda olduğu için not edildi. **Risk: DÜŞÜK–ORTA.**

### 4.5 Lying Colors — derin kontrol

| # | Sorgu | Bulgu |
|---|---|---|
| 1 | `"Lying Colors" game` | Aynı ad yok. "In Living Colors" (boya damlası bulmacası) https://www.gamezebo.com/reviews/in-living-colors-review/ · oyun reçeli "Pile of Lies" https://wormius51.itch.io/pile-of-lies |
| 2 | `site:play.google.com OR site:apps.apple.com "Lying Colors" OR "Lying Colours"` | Eşleşme yok (Who Is Lying, Lies, Telling Lies) |
| 3 | `"LYING COLORS" OR "LYING COLOURS" trademark OR band OR album` | Marka yok. Sonuçları Flying Colors ve Living Colour adlı müzik grupları domine ediyor |
| 4 | `site:store.steampowered.com OR site:itch.io "Lying Colors" OR "Lying Colours"` | Eşleşme yok. Benzer konsept: "Fake Colours" (Steam bulmaca) https://store.steampowered.com/app/334300 |
| 5 | `site:youtube.com OR site:tiktok.com OR site:instagram.com "lying colors"` | Eşleşme yok |
| 6 | `"Lying Colors" app OR brand OR company OR book` | Eşleşme yok. Benzer: "The Color of Lies" (CJ Lyons, roman) |

**Değerlendirme:** Belirgin bir çakışma bulunmadı (erişim sınırları aynı). Arama görünürlüğü müzik gruplarıyla rekabet ettiği için zayıf. **Risk: DÜŞÜK.**

### 4.6 Tint Trick — derin kontrol

| # | Sorgu | Bulgu |
|---|---|---|
| 1 | `"Tint Trick" game app` | Aynı ad yok. "Tint" ailesi: Tint (Apple Arcade, Lykke Studios) https://apps.apple.com/app/tint/id1451786388 · **Tint Twist** (Sunspear Software, renk bölgelerine göre renk değiştiren koşu oyunu) https://apps.apple.com/us/app/-/id6741045260 · Tint Tango https://apps.apple.com/us/app/tint-tango/id6468386191 · Tint Hero https://apps.apple.com/mm/app/tint-hero/id1550735454 |
| 2 | `site:play.google.com "Tint Trick" OR "TintTrick"` | Eşleşme yok ("Tint: Gay dating & Chat", Hour 25; duvar kâğıdı uygulamaları) |
| 3 | `site:apps.apple.com "Tint Trick"` | Eşleşme yok (Tint boya alışverişi, Tint: Color Analysis, Tint: Photo Editor) |
| 4 | `site:store.steampowered.com OR site:itch.io "Tint Trick"` | Eşleşme yok ("Tintoria": renk değiştiren platform oyunu, 2026'nın 3. çeyreğinde çıkması planlanıyor; "Tinting Time") |
| 5 | `"Tint Trick" OR "TintTrick" -car -window` | Yalnızca araç cam filmi sayfaları |
| 6 | `site:youtube.com OR site:tiktok.com OR site:instagram.com "tint trick"` | Makyaj ("skin tint", "lip tint") ipuçları |
| 7 | `"TINT TRICK" trademark` | Aynı marka görülmedi. TINT SHOT, TINT MAGIC (bekleyen), TINT WIZDOM (farklı sektörler) |

**Değerlendirme:** Aynı ad görülmedi (erişim sınırları aynı). Ancak "Tint Twist" aynı türde (renk değiştirerek koşma) ve adları birbirine çok benziyor. **Risk: DÜŞÜK–ORTA.** Arama görünürlüğü çok zayıf.

### 4.7 Not That Color — derin kontrol

| # | Sorgu | Bulgu |
|---|---|---|
| 1 | `"Not That Color" game` | Aynı ad yok. "Name That Color" (Stroop videosu) https://www.downloadyouthministry.com/p/name-that-color/games-4061.html · "It's Not Red" https://cosmicbrain.itch.io/its-not-red · "Color Game" https://biggstudios.itch.io/color-game |
| 2 | `site:play.google.com "Not That Color" OR "Not That Colour"` | Eşleşme yok |
| 3 | `site:apps.apple.com "Not That Color" OR "Not That Colour"` | Eşleşme yok ("What's That Color?" https://apps.apple.com/app/id1142933218) |
| 4 | `site:store.steampowered.com OR site:itch.io "Not That Color"` | Eşleşme yok |
| 5 | `site:youtube.com OR site:tiktok.com OR site:instagram.com "not that color" game` | Eşleşme yok (TikTok renk akımları) |
| 6 | `"NOT THAT COLOR" trademark` | Aynı marka yok. Benzer: NUDE IS NOT A COLOR (giyim) https://trademark.justia.com/900/01/nude-is-not-a-90001980.html |
| 7 | `"Not Not" brain challenge game Altered Matter` (sorgudaki stüdyo adı yanlış yazıldı. Sonuçlara göre doğru geliştirici Alternative Shift) | "Not Not - A Brain-Buster" https://apps.apple.com/app/id1107589341 · "Not Not 2" https://apps.apple.com/app/id1575206039 . Switch sayfasına göre mobilde 10 milyon indirme |

**Değerlendirme:** Aynı ad görülmedi (erişim sınırları aynı). "Not …" ile başlayan ad, popüler "Not Not" serisini ve iOS'taki "ColorNot"u çağrıştırıyor. Ayrıca jenerik bir ifade olduğu için marka olarak korunması zayıf. **Risk: ORTA.**

### 4.8 Chroma Twist — derin kontrol

| # | Sorgu | Bulgu |
|---|---|---|
| 1 | `"Chroma Twist" game` | Aynı ad yok. "TWIST3D" (Chroma Studios) https://www.pocketgamer.com/twist3d/ · "Chroma Swirl" https://speederoo.itch.io/chroma-swirl |
| 2 | `site:play.google.com "Chroma Twist" OR "ChromaTwist"` | Eşleşme yok. "Chroma Shift" (FTTech) https://play.google.com/store/apps/developer?id=FTTech |
| 3 | `site:apps.apple.com "Chroma Twist"` | Eşleşme yok. "Tint Twist", "Chroma Sprint" https://apps.apple.com/us/app/chroma-sprint/id6470514010 |
| 4 | `site:store.steampowered.com OR site:itch.io "Chroma Twist"` | Eşleşme yok. "Chroma Shift" (Steam) https://store.steampowered.com/app/860990 |
| 5 | `"Chroma Twist" OR "ChromaTwist" -hair` | Razer "Rapunzel Chroma" saç boyası https://www.razer.com/rapunzel |
| 6 | `site:youtube.com OR site:tiktok.com OR site:instagram.com "chroma twist"` | Eşleşme yok |
| 7 | `"CHROMA TWIST" trademark` | Aynı marka yok. Çok sayıda CHROMA kaydı var, ör. https://www.trademarkelite.com/trademark/trademark-detail/77733638/CHROMA |
| 8 | `"Hue Twist" OR "HueTwist" game app` (varyant) | "Hue Twist" (Philips Hue ile Twister çarkı uygulaması) https://apps.appfollow.io/ios/hue-twist/1210440450?country=us |

**Değerlendirme:** Aynı ad görülmedi (erişim sınırları aynı). Ancak "Chroma" ön eki oyunlarda ve markalarda çok kalabalık. **Risk: ORTA.**

### 4.9 Chromiss — derin kontrol

| # | Sorgu | Bulgu |
|---|---|---|
| 1 | `"Chromiss" game OR app` | Aynı ad yok. "Chromis" (2014 renk bulmacası) https://ffapps.danielherr.software/app/chromis/ · "Chromix: Merging Colors" https://apps.apple.com/us/app/chromix-merging-colors/id6535690133 |
| 2 | `site:play.google.com OR site:apps.apple.com "Chromiss" OR "Chromis"` | "Chromis" fotoğraf düzenleyici (Android, tech.idct.chromis) https://chrome-stats.com/d/tech.idct.chromis |
| 3 | `"Chromiss"` | Eşleşme yok |
| 4 | `site:store.steampowered.com OR site:itch.io "Chromiss" OR "Chromis"` | Eşleşme yok (Chromocide, Chromasia) |
| 5 | `"CHROMIS" OR "CHROMISS" trademark` | CHROMIS (iptal edilmiş kayıtlar), CHROMIS TECHNOLOGIES (fiber optik, geçerli) https://trademarkdir.com/trademark/chromis-technologies-90648196 |
| 6 | `site:youtube.com OR site:tiktok.com OR site:instagram.com "chromiss"` | Eşleşme yok. Ancak TikTok'ta tehlikeli **"chroming"** akımı var https://socialmediavictims.org/?p=462 |

**Değerlendirme:** Belirgin bir çakışma bulunmadı (erişim sınırları aynı). "Chromis" ile çok benzer, ayrıca "chroming" çağrışımı olumsuz. **Risk: DÜŞÜK–ORTA (itibar riski).**

### 4.10 Wrong Color (birebir çeviri) — orta kontrol

| # | Sorgu | Bulgu |
|---|---|---|
| 1 | `"Wrong Color" game app` | **iOS "wrongColor"** ("yanlış rengi seç" oyunu, satıcı Hirofumi Nakano) https://apps.apple.com/app/id960553084 · **itch.io "Wrong Color"** (nitelite) https://nitelite.itch.io/wrong-color · **itch.io "Wrong Color"** (Raultl_12, Mini Jam 111) https://raultl-12.itch.io/wrong-color · "Stupid Colors" https://toucharcade.com/games/stupid-colors |
| 2 | `site:play.google.com "Wrong Color" OR "Wrong Colour" game` | **Android "Wrong Color"** (org.minomegabyte.app, appgoblin) https://appgoblin.info/apps/org.minomegabyte.app/keywords/discovery · "Shades Of Grey - Wrong Color" https://appgoblin.info/apps/com.free5min.wrongcolor/keywords/discovery · "Wrong Again" (tek dokunuşlu Stroop oyunu, yasak renkten kaçınma) https://appgoblin.info/apps/com.UNSdev.WrongAgain |
| 3 | `"WRONG COLOR" OR "WRONG COLOUR" trademark` | Aynı marka görülmedi |
| 4 | `site:store.steampowered.com "Wrong Color" OR "Wrong Colour"` | Eşleşme yok |
| 5 | `"Wrong Color" org.minomegabyte.app` | Ek bilgi yok |

**Değerlendirme:** Aynı adlı en az 4 oyun veya uygulama var. **Risk: YÜKSEK.** Bu ad yalnızca açıklama metninde kullanılabilir, başlık olarak kullanılmamalı. "Wrong Color" ifadesini Miscolor'ın alt başlığında açıklayıcı olarak kullanmak ise jenerik bir ifade olduğu için makul (bkz. Final_Name.md).

### 4.11 Huedini — orta kontrol

| # | Sorgu | Bulgu |
|---|---|---|
| 1 | `"Huedini" game app` | Aynı ad yok. "Hue" (Fiddlesticks, 2016) https://wikipedia.com/wiki/Hue_(video_game) |
| 2 | `"Huedini"` | Eşleşme yok |
| 3 | `site:play.google.com "Chroma Gates" OR "Chroma Trap" OR "Huedini" OR "Unhue"` | Eşleşme yok |
| 4 | `"HUEDINI" OR "TINT TRICK" OR "WRONGBOW" OR "CHROMA GATES" trademark justia` | HUEDINI kaydı yok. Benzer: **HOUDINI'S** (sınıf 28, sihirbazlık ürünleri ve iskambil kâğıtları) https://trademark.justia.com/783/13/houdini-78313042.html · HUEGLOW (Live Tinted, kozmetik) |

**Değerlendirme:** Belirgin bir çakışma bulunmadı (erişim sınırları aynı). Ancak ünlü bir isimden türetildiği için risk taşıyor. **Risk: ORTA.**

### 4.12 Hafif kontrol edilen adaylar

Aşağıdakiler yalnızca 1–2 sorguyla kontrol edildi. Sonuçlar derin kontrol kadar güvenilir değil.

| Aday | Sorgu(lar) | Bulgu | Risk |
|---|---|---|---|
| **Hue Dash** | `"Hue Dash" game app` | **iOS "HueDash"** (Igor Dourado, renk eşleştirme refleks oyunu) https://apps.apple.com/de/app/huedash/id6476564227 · "Hue Dash - Color Bump 3D" https://www.producthunt.com/products/hue-dash/launches | YÜKSEK |
| **Hue Flip** | `"Hue Flip" OR "HueFlip" game app` | **Android "Hue Flip"** (Furkan Damar, Kasım 2025, renk refleks oyunu) https://appgoblin.info/apps/com.FurkanDamar.hue_flip · "Flipped Hue" (iOS) https://apps.apple.com/iq/app/6751538989 | YÜKSEK |
| **Odd Hue Out** | `"Odd Hue Out" game` | **Nintendo Switch "Odd Hue Out!"** (2024) https://www.nintendolife.com/games/switch-eshop/odd_hue_out · "Odd Color Out" (iOS) https://apps.apple.com/app/id6448401800 | YÜKSEK |
| **ColorNot** | `"ColorNot" OR "Color Not" game app` | **iOS "ColorNot - Deceive your eyes"** (Stroop) https://apps.apple.com/app/id1124967441 | YÜKSEK |
| **Same or Not** | `"Same or Not" color game app` | "Same Or Not" (GBC homebrew) https://gamebrew.org/wiki/Same_Or_Not_GB · "Same or Not?" (mobil, Unity) https://discussions.unity.com/t/android-ios-free-game-same-or-not/593213 · "SameSameOrDifferent" https://apps.apple.com/us/app/id1017103398 | YÜKSEK |
| **Color Twist** | `"Color Twist" game app runner` · bağlam için: `"Color Switch" game Fortafy trademark` | **itch.io "Color Twist"** (sonsuz koşu, farklı renkli platformlara inme) https://rahulnaidu10.itch.io/colortwist . Arama özetine göre iOS'ta "Color Twist" adlı bir beyin oyunu da var (URL doğrulanamadı). Bağlam: **COLOR SWITCH** tescilli marka (reg. 5508739, Color Switch Productions, Inc., video oyunu) https://trademark.justia.com/879/77/color-87977732.html . "Color Switch" ifadesi başlıkta kullanılmamalı | YÜKSEK |
| **Contracolor** | `"Contracolor" OR "Contra Color" game app` | itch.io "Contracolor" (1-Bit Weekend #2 reçel sayfası) https://aliahsanisk.itch.io/contracolor | ORTA–YÜKSEK |
| **Chroma Gates** | `"Chroma Gates" game` · birleşik Play sorgusu (4.11 #3) | Aynı ad yok. "Chroma Challenge" (HTML5, renkli kapılardan aynı renkle geçme) https://marketjs.com/item/chroma-challenge · "Chromadic" https://alimad.itch.io/chromadic | ORTA |
| **Color Nope** | `"Color Nope" OR "ColorNope" game` · `site:apps.apple.com "Tricky Tints" OR "Not My Color" OR "Color Nope" OR "Twistint" OR "Mischroma"` | Aynı ad yok. "Nope" (Ridley's Games, renk ve sayı eşleştirmeli kart oyunu) https://dicetower.com/board-game/274049 | ORTA |
| **Tricky Tints** | `"Tricky Tints" game app` · birleşik App Store sorgusu | Aynı ad yok. "Tricky Color" (Microsoft Store) https://www.microsoft.com/en-us/store/games/tricky-color/9nblggh4ql7v · "Tiny Tints" https://apps.apple.com/se/app/house-coloring/id6465389883 | ORTA |
| **Twistint** | `"Twistint" game OR app OR brand` · birleşik App Store sorgusu | Aynı ad yok. "Tint Twist" (iOS) | DÜŞÜK–ORTA |
| **Not My Color** | `"Not My Color" game app` · birleşik App Store sorgusu | Aynı ad yok. "Where's My Color?" https://apps.apple.com/us/app/wheres-my-color/id916467784 | DÜŞÜK–ORTA |
| **Mischroma** | `"Mischroma" OR "MisChroma"` · birleşik App Store sorgusu | Aynı ad yok. "Michroma" (gıda renklendirici girişimi) https://techcrunch.com/2023/02/01/michroma-food-coloring-fungi-foodtech/ | DÜŞÜK–ORTA |
| **Chroma Trap** | `"Chroma Trap" game` · birleşik Play sorgusu | Aynı ad yok. "Chroma" (açık kaynak bulmaca), "Chroma Key" (Steam) | DÜŞÜK–ORTA |
| **Unhue** | `"Unhue" game OR app` · birleşik Play sorgusu | Aynı ad yok. HUE, I Love Hue gibi kalabalık "Hue" oyunları | DÜŞÜK |
| **Off Color** | `"Off Color" game app mobile` · `"off-color" meaning idiom indecent risqué` | Aynı ad yok. Ancak deyim olarak "müstehcen, açık saçık" anlamına geliyor https://www.merriam-webster.com/dictionary/off-color | ORTA (itibar riski) |

---

## 5. Genel gözlemler

1. "Hue …" ve "Color …" ile kurulan iki kelimelik adların neredeyse hepsi zaten kullanılıyor (HueDash, Hue Flip, Color Twist, ColorNot, Odd Hue Out…). Ayrıca 2016 tarihli "Hue" (Fiddlesticks/Curve) oyunu ve "Color Switch" markası bu alanda güçlü.
2. Mekaniğe en yakın mevcut oyunlar şunlar: iOS "wrongColor", Android "Wrong Again", iOS "ColorNot", iOS "ColorMismatch: Challenge", HTML5 "Chroma Challenge" ve itch.io "Color Twist". Bu nedenle mağaza açıklamasında oyunun farklı yönlerini (koşu, 20 dünya, ayna/sis/hafıza kapıları, 2 kişilik mod) öne çıkarmak gerekiyor.
3. Tek kelimelik ve sözlük tabanlı olup az kullanılan **Miscolor**, aramalarda en temiz görünen ad oldu. Bu tespit yalnızca erişilebilen kaynaklara dayanıyor.
