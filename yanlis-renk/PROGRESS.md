# İlerleme Günlüğü

Oturum kesilirse buradan devam edin. Ayrıntılar: TASKS.md, TEST_REPORT.md, BUILD_REPORT.md.

## Durum: 10 aşamanın tamamı uygulandı ve test edildi (gerçek cihaz testi hariç)

| Aşama | Durum | Not |
|---|---|---|
| 1 Proje | ✅ | `yanlis-renk/` bağımsız Gradle projesi; depodaki mevcut kuaför uygulamasına dokunulmadı |
| 2 Temel mekanik | ✅ | `RunWorld`, `ColorCycle`, `GateData` |
| 3 İlk 5 alan | ✅ | Bölüm 1–25 + öğretici + alan açılış kartları |
| 4 Can sistemi | ✅ | `LifeManager`, `TrustedClock`, 7 zaman testi |
| 5 Market | ✅ | `ShopCatalog` (~110 ürün), `GameSession.buy/equip` |
| 6 100 alan / 500 bölüm | ✅ | Üreteç + doğrulayıcı; yedek üretime düşen bölüm: 0 |
| 7 Sonsuz, görev, başarı, koleksiyon | ✅ | Haftalık mücadele ve ustalık dahil |
| 8 Görsel/ses/ekran | ✅ | 25 ekran, 36 QA görüntüsü (`docs/screenshots`) |
| 9 Test | ✅ | 34/34 test, QA 0 hata |
| 10 APK/AAB | ✅ | `dist/` (debug anahtarıyla imzalı) |

## Önemli kararlar
* Bölümler el yapımı JSON değil, **tohumlu üreteç + kanıtlayıcı doğrulayıcı** ile üretilir ve dosyaya yazılır; dosya bozulursa aynı tohumla yeniden üretilir.
* Tüm görseller vektörel (ShapeRenderer) ve proje için üretilmiş PNG'lerdir; sesler sayısal olarak sentezlenmiştir; yazı tipi Nunito (OFL). Üçüncü taraf telifli varlık yoktur.
* Kayıt: DataStore yerine atomik dosya + HMAC + yedek (gerekçe ARCHITECTURE.md'de).
* Yazı tipinde olmayan simgeler (ok, eşit değil, yıldız) metinde kelimeyle, oyunda ise çizilmiş simgelerle gösterilir.
* Görsel QA sırasında bir bölümün dokunmadan geçildiği görüldü; asıl neden QA çalıştırıcısındaki bir hataydı (otomatik oyuncu kapalı sanılırken açıktı) ve düzeltildi. Güvence olarak üreticiye "en az max(2, kapı/4) dokunuş" kısıtı ve 500 bölümün tamamının dokunmadan **geçilemediğini** denetleyen test eklendi (kısıt mevcut bölümlerin hiçbirini değiştirmedi).

## 1.0.1 (açılış çökmesi düzeltmesi)
* Kullanıcı 1.0.0'ın açılmadığını bildirdi. Android 11 emülatöründe yeniden üretildi: R8 libGDX'in yansıma ile kullandığı kurucuları silmişti. R8 kapatıldı, yazı tipi paketleme ve müzik yükleme iyileştirildi, çökme raporu penceresi eklendi. 1.0.1 emülatörde açıldı ve oynandı.

## 1.0.2 (Magic Lumos Software açılışı)
* Oyun açılırken Magic Lumos Software logosu oyuna özel bir animasyonla gösteriliyor (`core/.../screens/IntroScreen.kt`, görsel `android/assets/brand/magic_lumos.jpg`): 4 oyun renginde devre hatları, logoyu soldan sağa açan renk değiştiren "lumos" ışık huzmesi, logodan geçerken K→M→Y→S olan küp karakter, 4 renkli ışık patlaması ve "sunar". ~8,6 sn (1.0.3 ile yavaşlatıldı), dokunarak atlanabilir. Masaüstü QA'da ara kareleri doğrulandı (`docs/screenshots/00a–00d`).

## 1.0.4 (kullanıcı görselleri)
* Kullanıcının ürettiği 39 görsel `tools/art/import_user_art.py` ile işlenip oyuna eklendi (sahte dama/beyaz zeminler temizlendi, mobil için boyutlandırıldı, sprite'lar 2'nin kuvveti tuvallere yerleştirildi).
* 11–20. dünyalar görsel arka planla çiziliyor (`assets/bg/`).
* 10 karakter (Klasik Küp, Zıp Top, Sevimli Robot, Mini Panda, Renkli Kedi, Uzaylı, Kristal, Bulut, Yıldız Kahraman, Kral Küp) görselle çiziliyor ve oyun rengine boyanıyor; kalan 20 karakter vektör.
* Altın, elmas, kalkan, renk pedi ve gizli/hafıza kapısı görselleri; yeni logo, menü ve harita arka planı, parlak butonlar, panel, kalp/elmas/kalkan/sandık simgeleri, ödül ekranı görselleri, Play Store tanıtım görseli.
* Kullanılmayanlar: 31/32/35 (yalnızca ince çizgi, kapı gövdesi yok), 33/34 (sabit 4 renk; oyunda kapı renkleri bölüme göre değişiyor), 43'teki renkli butonlar ayrı ayrı değil tek beyaz şablon olarak (her renge boyanabilsin diye).

## 1.0.5
* 1–10. dünyaların arka plan görselleri eklendi (`tools/art/import_world_bgs.py`). Artık 20 dünyanın tamamı kullanıcı arka planlarıyla çiziliyor; vektör arka planlar yalnızca dosya eksikse yedek olarak kalıyor.
