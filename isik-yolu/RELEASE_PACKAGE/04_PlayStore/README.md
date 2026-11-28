# Google Play görselleri — Prismway (Işık Yolu)

Tüm görüntüler oyunun gerçek arayüzünden (testlerde gerçek çizimle) alındı. Oyunda olmayan bir özellik, cihaz
çerçevesi, fiyat ya da "en iyi / #1 / yeni" türü ifade yoktur.

| Klasör | İçerik | Play Console alanı |
|---|---|---|
| `App_Icon/app_icon_512.png` | 512×512, 32 bit PNG (RGBA), uygulamanın uyarlanabilir simgesiyle aynı tasarım, tam kare (köşe maskesini Play uygular) | Uygulama simgesi |
| `Feature_Graphics/feature_graphic_tr.png`, `feature_graphic_en.png` | 1024×500, 24 bit PNG (alfasız) | Öne çıkan görsel. TR → Türkçe girişte; EN → diğer dillerde. |
| `Phone_Screenshots/<DİL>/<dil>_01…08_*.png` | 1080×1920 (9:16), 24 bit PNG, 8 adet × 6 dil = 48 | Telefon ekran görüntüleri. İlk üçü oynanış: ışığı yönlendirme, prizma ve renkler, 3 yıldız. |
| `Tablet_7_Inch/<DİL>/*.png` | 1200×1920 (7 inç tablet, dikey, 600×960 dp), 5 adet × 6 dil | 7 inç tablet ekran görüntüleri |
| `Tablet_10_Inch/<DİL>/*.png` | 1600×2560 (10 inç tablet, dikey, 800×1280 dp), 5 adet × 6 dil | 10 inç tablet ekran görüntüleri |

## Telefon ekran görüntülerinin sırası

| # | Dosya eki | İçerik | Kategori |
|---|---|---|---|
| 1 | `light` | Mantık bölümü 169: beş hedef birden yanıyor | En etkileyici oynanış |
| 2 | `prism` | Prizma bölümü 52: beyaz ışık üç renge ayrılıyor | Temel mekanik |
| 3 | `stars` | Renk bölümü 40 tamamlandı: "Kusursuz!", 3 yıldız | Görev ve zorluk |
| 4 | `worlds` | Bölüm seçimi: dünyalar ve bölüm grupları | Farklı dünyalar |
| 5 | `keys` | Anahtar/kapı bölümü 86 | Özel mekanik |
| 6 | `stones` | Taş itme bölümü 74 | Özel mekanik |
| 7 | `daily` | Günlük bulmaca: takvim ve seri | Oyun modu |
| 8 | `skins` | Gökkuşağı ayna, galaksi tahta, plazma ışın görünümleri | Özelleştirme |

## Tablet

Oyun tabletlerde de çalışır; Android 16'da "oyun" kategorisinde olduğu için dikey kalır. Tablet görüntüleri
Robolectric'te gerçek tablet ekran boyutlarıyla çizildi. Fiziksel bir tablette çekilmedi; ama yapay ya da düzenlenmiş
değil, uygulamanın kendi çizimidir.
Play kuralı: kenarlar 1080–7680 px, uzun kenar ≤ 2,3 × kısa kenar, en az 4 adet. 1200×1920 ve 1600×2560 (oran 1,6)
bu kurala uyar.

Görsellerin dosya adları, boyutları ve biçimleri `08_Test_Reports/Media_QA.md` içinde tek tek doğrulandı.
