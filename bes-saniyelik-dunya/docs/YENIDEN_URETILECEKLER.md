# Yeniden Üretilecek Görseller (34 dosya)

Gelen 186 görselin 152'si kullanılabilir durumda (bir kısmında küçük düzeltmeleri ben yapacağım). Aşağıdaki 34 dosyanın yeniden üretilmesi gerekiyor.

## Neden bozuk çıktılar? (en önemli kural)

Bozuk gelenlerin neredeyse hepsi **toplu bir sayfadan kesilip hedef boyuta esnetilmiş**. Esnetme, görselleri dikey çizgili ve lekeli yapıyor. Bu yüzden:

1. **Her görseli ayrı ayrı üretin.** Toplu atlas veya ortak sayfa kullanmayın.
2. **Doğrudan doğru en-boy oranında üretin.** Gökyüzü için `--ar 9:16`, karolar için `--ar 1:1`, butonlar için `--ar 1:1`. Sonradan uzatmayın veya sündürmeyin.
3. Karolarda içerik **bütün tuvali doldursun**: beyaz kenar, boşluk, çerçeve olmasın.
4. Nesnelerde içerik tuvalin **büyük kısmını kaplasın**; minik çizilip etrafı boş bırakılmasın.

Promptların başına önceki belgedeki (`GORSEL_PROMPTLARI.md`) **STYLE** bloğunu, destekleyen araçlarda **Negatif Prompt**u eklemeye devam edin.

---

## 1. Gökyüzü — 8 dosya · 1080×1920 · `--ar 9:16`

`map03_sky`, `map04_sky`, `map05_sky`, `map06_sky`, `map07_sky`, `map08_sky`, `map09_sky`, `map10_sky`

**Sorun:** Panoramik şeritten dikey uzatılmış (`03`, `05`, `06`, `08`, `10`) ya da altı/üstü tek düz renkle doldurulmuş (`04`, `07`, `09`).

```
[STYLE] Vertical portrait 9:16 mobile game sky painting for <TEMA>, the ENTIRE canvas is painted sky from top to bottom: bright airy gradient (deeper blue at the top, soft warm light near the bottom), layered soft volumetric clouds distributed across the whole height, gentle sun glow, no ground, no buildings, no characters, no flat empty color areas, no stretching, no vertical streaks --ar 9:16
```

`<TEMA>` açıklamaları önceki belgedeki tablodadır (ör. Hız Çölü: *golden desert...*). Gökyüzünde yalnızca o temaya uygun renk tonu ve hava olsun; yer şekli olmasın.

---

## 2. Zemin üst karoları — 7 dosya · 512×512 · `--ar 1:1`

`map02_ground_top`, `map04_ground_top`, `map06_ground_top`, `map07_ground_top`, `map08_ground_top`, `map09_ground_top`, `map10_ground_top`

**Sorun:** Dikey uzatılmış (`02`, `04`, `06`, `07`, `08`), aynı desen 4 kez alt alta dizilmiş (`09`), perspektifli çizilmiş (`10`).

```
[STYLE] Single square ground tile for a 2D side-view platformer, strictly flat orthographic side cross-section (no perspective, no top-down view), <TEMA>, the top quarter of the tile is the walkable surface layer with a crisp clean edge, the lower three quarters is the material texture, the tile fills the whole canvas edge to edge with no white border, left and right edges tile seamlessly, one tile only (not repeated rows), no stretching --ar 1:1
```

---

## 3. Zemin dolgu karoları — 4 dosya · 512×512 · `--ar 1:1`

`map02_ground_fill`, `map07_ground_fill`, `map08_ground_fill`, `map09_ground_fill`

**Sorun:** Dikey uzatılmış (`02`, `07`, `08`) ya da aynı desen 4 kez alt alta dizilmiş (`09`).

```
[STYLE] Seamless tileable square texture of the underground material for a 2D platformer, <TEMA>, uniform low-contrast detail, tiles seamlessly on all four edges, fills the entire canvas, no border, no stretching, no streaks --ar 1:1 --tile
```

> Midjourney'de `--tile` parametresi her yönde kusursuz tekrar eden doku üretir; yalnızca **dolgu** karolarında kullanın, üst karolarda kullanmayın.

---

## 4. Oyun nesneleri — 5 dosya (şeffaf arka plan)

| Dosya | Boyut | Sorun | Prompt eki |
|---|---|---|---|
| `star.png` | 512×512 | Arka plan silinirken yıldızın içi de gitmiş, yalnızca dış çizgi kalmış | `solid filled faceted golden star with soft glow, the star fills about 85% of the canvas, opaque body` |
| `piece.png` | 512×512 | Yalnızca ince dış çizgi | `solid filled glowing violet crystal puzzle piece, opaque body, fills about 85% of the canvas` |
| `goal.png` | 1280×1680 | İçerik çok küçük (387×356 piksel) | `the gate fills about 90% of the canvas height` |
| `boost.png` | 1024×256 | İçerik çok küçük (234×82 piksel) | `the pad fills the full width of the canvas` |
| `platform_ghost.png` | 512×320 | İçerik çok küçük ve soluk | `the slab fills the full width of the canvas, clearly visible translucent crystal` |

Promptların kendisi önceki belgedekiyle aynıdır; tablodaki eki promptun sonuna ekleyin.

---

## 5. Butonlar — 10 dosya · 240×240 · `--ar 1:1`

`button_yellow`, `button_orange`, `button_pink`, `button_turquoise`, `button_green`, `button_blue`, `button_purple`, `button_white`, `button_grey`, `button_red`

**Sorun:** Hepsinin içinden geçen beyaz bir yay çizgisi var. Buton farklı boyutlara esnetildiğinde (9-slice) bu çizgi kırılır ve bozuk görünür.

```
Premium mobile game UI button background, rounded rectangle, <RENK> smooth vertical gradient, the face is completely plain and uniform, NO inner lines, NO arcs, NO curved highlight strokes, only a very soft straight highlight band along the top edge, slightly darker bottom edge giving thickness, perfectly symmetrical for 9-slice scaling, no text, no icon, transparent background --ar 1:1
```

Renkler: yellow #FFD54F, orange #FF9F43, pink #FF8FB1, turquoise #58D6D0, green #73D65C, blue #6FB7F5, purple #B388FF, white, grey #C9D3E0, red #FF6B6B.

---

## Tavanlar: yeniden üretmenize gerek yok

10 tavan karosunun hepsi bozuk geldi, ama **bunları üretmenize gerek yok**. Tavan, zeminin aşağı bakan yüzeyidir. O haritanın düzgün zemin üst karosunu ters çevirerek tavanı ben oluşturacağım; böylece zemin ve tavan birebir aynı tarzda olur.

---

## Benim düzelteceklerim (sizden bir şey gerekmez)

- Zemin dolgularındaki üst beyaz şerit ve dikişler: `map01`, `map03`, `map04`, `map05`, `map06`, `map10`
- `map01_ground_top` alttaki siyah çizgi, `map03_ground_top` beyaz kenar
- Şeffaf olmayan paralaks katmanlarının üst kısmını gökyüzüne eritme: `map03_far`, `map05_far`, `map05_mid`, `map06_far`, `map06_mid`, `map07_far`, `map07_mid`, `map08_far`, `map08_mid`, `map09_far`, `map09_mid`, `map10_far`, `map10_mid`
- Küçük çizilmiş süsleri kırpma: `map01_decor`, `map06_decor`
- `hero_murdum_sheet` arkasındaki beyaz sis, soluk `powerup_bubble`
- `hero_sheet` dosyasını ana karakter Pati olarak kullanma

## Teslim

34 dosyayı aynı adlarla tek bir zip içinde (gerekirse ikiye bölerek) gönderin. Geldiğinde yine otomatik kontrol edip sonucu yazarım; hepsi tamamsa bütün görselleri oyuna yerleştirip yeni APK'yı gönderirim.
