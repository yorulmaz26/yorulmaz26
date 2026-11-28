# Yanlış Renk – İngilizce Ad Adayları ve Puanlama

**Tarih:** 2026-11-27
**Oyun:** Yanlış Renk (Magic Lumos Software). Android, dikey ekran, açık pastel görseller, herkes için içerik.
**İlgili belgeler:** [Market_Research.md](Market_Research.md) (her adayın arama kayıtları) · [Final_Name.md](Final_Name.md) (ilk 5 ve seçilen ad)

---

## 1. Konsept analizi (adın ne anlatması gerekiyor?)

| Oyunun özü | Ada yansıması gereken fikir |
|---|---|
| Karakter kendiliğinden koşar, dokununca rengi Kırmızı → Mavi → Yeşil → Sarı sırasıyla değişir | **renk** + hızlı refleks |
| "=" kapısından aynı renkle, "≠" kapısından farklı renkle geçilir | **aynı / farklı**, "yanlış renk" |
| Hareketli renkler, hafıza kapıları, titreme, sis, ayna bölgeleri (dokunuş geri sayar), kilit, hız, renk pedleri, Stroop benzeri yazı kapıları, zincir ve bölünmüş kapılar | **zihinsel ters köşe**: "bariz renk yanlış olandır" |
| 20 dünya (şeker, bahçe, gökyüzü, kristal, uzay…), 500 bölüm, sonsuz mod, 2 kişilik bölünmüş ekran, 30 karakter | sevimli, aileye uygun, uzun ömürlü marka |

Seçim ölçütleri (görev tanımından): kısa olmalı, "renk + yanlış/ters köşe" fikrini anlatmalı, EN/DE/IT/FR/ES konuşanların rahatça okuyabileceği bir ad olmalı, oyun ve uygulamalarda çakışma riski düşük olmalı.

## 2. Karakter sınırı kuralları

* Google Play uygulama başlığı ve App Store uygulama adı en fazla **30 karakter** olabilir.
* `Yanlış Renk: ` öneki **13 karakter**, dolayısıyla İngilizce kısım en fazla 17 karakter olabilir.
* ` – Yanlış Renk` soneki (boşluklu uzun tire) **14 karakter**, dolayısıyla İngilizce kısım en fazla 16 karakter olabilir.
* Sayımlar Python `len()` ile yapıldı. Türkçe "ı" ve "ş" birer karakter sayılır.

## 3. Puanlama ağırlıkları

Her kriter kendi ağırlığı üzerinden puanlandı. Toplam 100 puandır.

| Kriter | Ağırlık | Ne ölçüldü |
|---|---|---|
| Özgünlük (Öz) | 20 | Ad uydurma ya da az kullanılmış mı, jenerik bir ifade mi? |
| Akılda kalıcılık (Akıl) | 15 | Kısalık, ritim, tek seferde hatırlanabilmesi |
| Telaffuz kolaylığı (Tel) | 10 | EN/DE/IT/FR/ES konuşanların yazıldığı gibi okuyup okuyamayacağı; "th", sessiz "w", "hue" gibi zor sesler puan düşürür |
| Oyun konseptine uygunluk (Kon) | 20 | "Renk + yanlış/ters köşe/aynı-farklı" fikrini ne kadar taşıdığı |
| Pazarlama gücü (Paz) | 10 | Logo, slogan ve kelime oyunu potansiyeli; olumsuz çağrışım puan düşürür |
| Arama görünürlüğü (Ara) | 10 | Ad aramada sahiplenilebilir mi, yoksa jenerik terimler, deyimler ya da başka sektörler (cam filmi, saç boyası, fizik terimi…) arasında kaybolur mu? |
| Çakışma riski (Çak) | 15 | **Düşük risk = yüksek puan.** Oyun veya uygulamada birebir aynı ad en ağır cezayı alır. Oyun veya yazılım sınıfında (Nice 9/28/41) benzer marka kaydı orta ceza alır. İlgisiz sektörde benzer ad küçük ceza alır. |

**Araştırma derinliği:** **D** = derin (6'dan fazla sorgu: genel web, Google Play, App Store, Steam/itch.io, YouTube/Instagram/TikTok, marka toplayıcıları). **O** = orta (3–5 sorgu). **H** = hafif (1–2 sorgu, ayrıntı için Market_Research.md'ye bakın). Hafif kontrol edilen adlar için risk puanı daha belirsizdir.

## 4. Aday tablosu (27 aday, toplam puana göre sıralı)

| # | Aday (EN) | Kar. | `Yanlış Renk: X` | `X – Yanlış Renk` | Konsept anlamı | Öz /20 | Akıl /15 | Tel /10 | Kon /20 | Paz /10 | Ara /10 | Çak /15 | **Toplam** | Derinlik |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | **Miscolor** | 8 | 21 ✓ | 22 ✓ | "mis-" (yanlış) + "color". İngilizcede nadir kullanılan gerçek bir fiil: "yanlış renklendirmek". Türkçe adın birebir ruhunu taşıyor | 17 | 13 | 9 | 18 | 8 | 8 | 13 | **86** | D |
| 2 | **Color Bluff** | 11 | 24 ✓ | 25 ✓ | "Renk blöfü": renkler oyuncuyu kandırır (Stroop, hafıza, sis) | 15 | 13 | 8 | 17 | 8 | 6 | 10 | **77** | D |
| 3 | **Anticolor** | 9 | 22 ✓ | 23 ✓ | "Karşı-renk": ≠ kapıları ve "bariz rengin tersi" fikri | 15 | 12 | 9 | 14 | 7 | 6 | 13 | **76** | D |
| 4 | **Wrongbow** | 8 | 21 ✓ | 22 ✓ | "wrong" + "rainbow" kelime oyunu: "yanlış gökkuşağı" | 18 | 12 | 6 | 13 | 7 | 8 | 12 | **76** | D |
| 5 | **Lying Colors** | 12 | 25 ✓ | 26 ✓ | "Yalan söyleyen renkler". "with flying colors" deyimine gönderme | 15 | 12 | 7 | 16 | 8 | 5 | 12 | **75** | D |
| 6 | Tint Trick | 10 | 23 ✓ | 24 ✓ | "Ton hilesi", aliterasyonlu. Tint kökü DE "Tinte", IT "tinta", FR "teinte", ES "tinte" ile akraba | 14 | 13 | 8 | 16 | 8 | 4 | 11 | **74** | D |
| 7 | Not That Color | 14 | 27 ✓ | 28 ✓ | "O renk değil!" Mekaniği en açık anlatan ifade | 10 | 13 | 6 | 19 | 8 | 5 | 11 | **72** | D |
| 8 | Huedini | 7 | 20 ✓ | 21 ✓ | "hue" + Houdini: renk illüzyonisti | 15 | 13 | 6 | 13 | 8 | 7 | 10 | **72** | O |
| 9 | Chromiss | 8 | 21 ✓ | 22 ✓ | "chroma" + "miss": rengi ıskalamak | 16 | 10 | 8 | 12 | 5 | 6 | 12 | **69** | D |
| 10 | Mischroma | 9 | 22 ✓ | 23 ✓ | "mis-" + "chroma" (Yunanca renk) | 15 | 9 | 6 | 14 | 6 | 7 | 11 | **68** | H |
| 11 | Chroma Twist | 12 | 25 ✓ | 26 ✓ | "Renk bükümü / renk sürprizi" | 11 | 12 | 8 | 15 | 7 | 5 | 10 | **68** | D |
| 12 | Contracolor | 11 | 24 ✓ | 25 ✓ | "contra" (karşı) + color | 13 | 10 | 8 | 14 | 6 | 7 | 9 | **67** | H |
| 13 | Color Nope | 10 | 23 ✓ | 24 ✓ | "Renk mi? Hayır!" esprili ret | 12 | 11 | 7 | 15 | 7 | 5 | 10 | **67** | H |
| 14 | Chroma Gates | 12 | 25 ✓ | 26 ✓ | "Renk kapıları": mekaniğin doğrudan tarifi | 11 | 10 | 8 | 15 | 6 | 6 | 11 | **67** | H |
| 15 | Twistint | 8 | 21 ✓ | 22 ✓ | "twist" + "tint" birleşik uydurma kelime | 16 | 8 | 4 | 12 | 5 | 8 | 12 | **65** | H |
| 16 | Chroma Trap | 11 | 24 ✓ | 25 ✓ | "Renk tuzağı" | 10 | 11 | 8 | 13 | 6 | 5 | 11 | **64** | H |
| 17 | Tricky Tints | 12 | 25 ✓ | 26 ✓ | "Hileli tonlar" | 10 | 11 | 7 | 14 | 7 | 4 | 10 | **63** | H |
| 18 | Not My Color | 12 | 25 ✓ | 26 ✓ | "Benim rengim değil" | 9 | 11 | 7 | 15 | 6 | 4 | 11 | **63** | H |
| 19 | Unhue | 5 | 18 ✓ | 19 ✓ | "un-" + "hue": renk-değil | 15 | 8 | 4 | 10 | 5 | 7 | 12 | **61** | H |
| 20 | Off Color | 9 | 22 ✓ | 23 ✓ | "Rengi tutmayan". İngilizcede "müstehcen, açık saçık" anlamı da var | 9 | 12 | 8 | 14 | 3 | 3 | 10 | **59** | H |
| 21 | Wrong Color | 11 | 24 ✓ | 25 ✓ | Türkçe adın birebir çevirisi | 4 | 12 | 8 | 20 | 6 | 3 | 3 | **56** | O |
| 22 | ColorNot | 8 | 21 ✓ | 22 ✓ | "Renk-değil" | 6 | 11 | 8 | 17 | 6 | 3 | 2 | **53** | H |
| 23 | Color Twist | 11 | 24 ✓ | 25 ✓ | "Renk bükümü" | 5 | 12 | 8 | 15 | 7 | 3 | 3 | **53** | H |
| 24 | Same or Not | 11 | 24 ✓ | 25 ✓ | "Aynı mı, değil mi?" (= ve ≠ kapıları) | 6 | 10 | 7 | 15 | 5 | 3 | 5 | **51** | H |
| 25 | Hue Flip | 8 | 21 ✓ | 22 ✓ | "Tonu çevir" | 7 | 11 | 6 | 13 | 6 | 4 | 4 | **51** | H |
| 26 | Hue Dash | 8 | 21 ✓ | 22 ✓ | "Ton koşusu" | 5 | 12 | 6 | 12 | 7 | 3 | 2 | **47** | H |
| 27 | Odd Hue Out | 11 | 24 ✓ | 25 ✓ | "Farklı tonu bul" ("odd one out" deyimi) | 5 | 11 | 6 | 10 | 6 | 3 | 2 | **43** | H |

✓ = 30 karakter sınırına uyuyor. 27 adayın tamamı hem tek başına hem de iki birleşik biçimde 30 karakterin altında kalıyor.

**Eşitlik kuralı:** Toplam puanlar eşitse önce Çakışma riski puanı, sonra Telaffuz puanı yüksek olan öne alınır. Bu kurala göre Anticolor (Çak 13), Wrongbow'un (Çak 12) önünde yer aldı.

## 5. Adaylara kısa notlar (puan gerekçeleri)

1. **Miscolor:** Tek kelime, 8 harf, beş dilde de yazıldığı gibi "mis-ko-lor" okunuyor. "mis-" öneki İngilizcede (mistake), Almancada (Miss-: Missverständnis) ve İtalyancada (misfatto) "yanlış/kötü" anlamını çağrıştırıyor. Arama yapılan mağaza sayfalarında aynı adlı bir oyun ya da uygulama görülmedi. En yakın marka benzeri ABD'de giyim sınıfındaki MISSCOLOR (sınıf 25). Sözlük kelimesi olduğu için ayırt edicilik değerlendirmesini bir avukatın yapması gerekir. Ayrıntı için [Final_Name.md](Final_Name.md).
2. **Color Bluff:** Anlamı ve dil geçişi güçlü ("bluff" poker sayesinde DE/FR/IT/ES'de biliniyor). Ancak ABD'de oyun sınıfında (28) "COLOR BLUFF" başvurusu yapılmış (2018, Playtacular LLC). Üçüncü taraf kayda göre başvuru artık geçersiz ("dead"), ama aynı alana daha önce ilgi gösterildiğini gösteriyor. Ayrıca aramalar poker ve Hanabi sonuçlarıyla dolu.
3. **Anticolor:** "anti-" öneki beş dilde de ortak, telaffuzu çok kolay. Oyun veya uygulama çakışması görülmedi. Ancak "anticolor" parçacık fiziğinde yerleşik bir terim (antikuark renk yükü) ve "=" kapılarını değil yalnızca ≠ kapılarını anlatıyor.
4. **Wrongbow:** Çok özgün, aramada sahiplenilebilir. Ancak "wr" sesi ve "bow" (bo/bau) okunuşu anadili İngilizce olmayanlar için belirsiz, kelime oyunu da çeviride kayboluyor. Oyun sınıflarında (9/28/41) kayıtlı WONDERBOW (Avalia Studios GmbH) markasıyla ses benzerliği düşük-orta düzeyde.
5. **Lying Colors:** Stroop ve hafıza kapılarını çok iyi anlatıyor, slogan potansiyeli yüksek. Ancak arama sonuçlarını "Flying Colors" ve "Living Colour" adlı müzik grupları domine ediyor. "lying" kelimesi "uzanmak" anlamında da okunabiliyor.
6. **Tint Trick:** Aliterasyon güzel. Ancak "tint" araması cam filmi ve makyaj içerikleriyle dolu, mağazada da "Tint", "Tint Twist" (renk eşleştirmeli koşu oyunu) ve "Tint Tango" gibi kalabalık bir "Tint" ailesi var.
7. **Not That Color:** Konsepti en iyi anlatan ad, ama jenerik bir ifade olduğu için marka olarak korunması zayıf. "th" sesi DE/FR/IT/ES konuşanlar için zor. 10 milyondan fazla indirilen "Not Not" serisine ve iOS'taki "ColorNot" oyununa kavramsal olarak çok yakın.
8. **Huedini:** Eğlenceli, ama "hue" kelimesinin okunuşu dillere göre değişiyor (FR "ü", ES "u-e"). Houdini adına dayalı markalar var (ör. sınıf 28'de HOUDINI'S, sihirbazlık ürünleri), yani ünlü isimden türetme riski taşıyor.
9. **Chromiss:** Özgün, ama anlamı kapalı. TikTok'taki tehlikeli "chroming" (inhalan) akımıyla ses benzerliği pazarlama açısından olumsuz. Chromis adlı bir fotoğraf uygulaması ve CHROMIS markaları var.
10. **Mischroma:** "ch" harf grubu dillere göre k/ş/ç diye okunuyor. Gıda renklendiricisi üreten "Michroma" girişimiyle yazım olarak neredeyse aynı (renk alanı).
11. **Chroma Twist:** "Chroma X" adlandırması oyunlarda çok kalabalık. Razer'ın "Chroma" markası oyun donanımında çok güçlü, iOS'ta da "Tint Twist" adlı bir koşu oyunu var.
12. **Contracolor:** itch.io'da aynı adlı bir oyun reçeli (1-Bit Weekend #2) sayfası var. Konami'nin "Contra" serisini çağrıştırıyor.
13. **Color Nope:** Ridley's Games'in renk ve sayı eşleştirmeli "Nope" kart oyunu aynı alanda.
14. **Chroma Gates:** Aynı ad görülmedi, ama "Chroma Challenge" adlı HTML5 oyunu birebir "renkli kapı" mekaniğine sahip. Ad özgün değil, jenerik.
15. **Twistint:** Söylemesi zor bir tekerleme, telaffuz puanı düşük.
16. **Chroma Trap:** Jenerik. Chroma adlı başka bulmaca oyunlarıyla karışıyor.
17. **Tricky Tints:** Microsoft Store'da "Tricky Color", App Store'da "Tiny Tints" var. Jenerik.
18. **Not My Color:** Jenerik. App Store'da "Where's My Color?" adlı Stroop oyunu var.
19. **Unhue:** "hue" telaffuz sorunu var, anlamı belirsiz.
20. **Off Color:** İngilizcede "off-color" = açık saçık, müstehcen (Merriam-Webster). Herkese uygun bir oyun için kaçınılmalı.
21. **Wrong Color:** Birebir çeviri. iOS'ta "wrongColor", itch.io'da iki ayrı "Wrong Color" oyunu ve Google Play izleyicisi appgoblin'de "Wrong Color" adlı bir paket var. Yüksek risk.
22. **ColorNot:** iOS'ta aynı adlı Stroop oyunu var ("ColorNot - Deceive your eyes"). Yüksek risk.
23. **Color Twist:** itch.io'da "Color Twist" adlı bir sonsuz koşu oyunu var (kendinden farklı renkli platformlara inme mekaniği). Arama özetine göre iOS'ta da aynı adlı bir beyin oyunu var. Yüksek risk.
24. **Same or Not:** "Same Or Not" (Game Boy Color homebrew) ve mobilde "Same or Not?" adlı oyunlar var. Jenerik.
25. **Hue Flip:** Google Play'de Kasım 2025'te çıkmış "Hue Flip" adlı bir renk refleks oyunu var. Yüksek risk.
26. **Hue Dash:** App Store'da "HueDash" adlı bir renk eşleştirme refleks oyunu ve Product Hunt'ta "Hue Dash - Color Bump 3D" var. Yüksek risk.
27. **Odd Hue Out:** Nintendo Switch eShop'ta aynı adlı oyun var (2024). Ayrıca konsepti farklı (farklı tonu bulma). Yüksek risk.

> Önemli: Puanlar yalnızca 2026-11-27 tarihinde yapılan web aramalarına ve ad analizine dayanıyor. Hiçbir aday için "ad boş" ya da "kullanılmıyor" denemez. Erişim sınırları için [Market_Research.md](Market_Research.md) dosyasının "Erişim sınırları" bölümüne bakın.
