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
