# Yanlış Renk

Türkçe, aydınlık temalı, dikey Android renk-refleks oyunu. Kotlin + libGDX + Scene2D.

Ekrana dokun → karakterin rengi Kırmızı → Mavi → Yeşil → Sarı sırasıyla değişir. "=" kapısından aynı, "≠" kapısından farklı renkle geç. 100 alan, 500 bölüm, sonsuz mod, iki kişilik kapışma (aynı cihazda), haftalık mücadele, ustalık modu, 4 can, market, 30 karakter, görevler, 115 başarı, 100 parçalık koleksiyon.

| Belge | İçerik |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modüller, sistemler, zaman güvenliği ve sınırları, kayıt |
| [LEVEL_DESIGN.md](LEVEL_DESIGN.md) | Kurallar, zorluk eğrisi, doğrulama, **100 alanın tam tablosu** |
| [TEST_REPORT.md](TEST_REPORT.md) | 22 istenen testin sonucu ve nerede çalıştığı |
| [BUILD_REPORT.md](BUILD_REPORT.md) | APK/AAB, imzalama, yeniden derleme komutları |
| [PROGRESS.md](PROGRESS.md) · [TASKS.md](TASKS.md) | İlerleme ve kalan işler |

Ekran görüntüleri: [`docs/screenshots/`](docs/screenshots).

## Hızlı başlangıç

Android Studio ile `yanlis-renk/` klasörünü açın (kökteki diğer proje değil) ve `android` yapılandırmasını çalıştırın. Komut satırı için BUILD_REPORT.md'ye bakın.

## Açılış
Oyun, Magic Lumos Software logosunun oyuna özel animasyonuyla açılır (dokunarak geçilebilir).

## Lisanslar
* Kod ve üretilmiş varlıklar: bu projeye aittir (`tools/art/gen_icons.py`, `tools/audio/gen_audio.py` ile yeniden üretilebilir).
* Magic Lumos Software logosu: şirketin kendi görseli (`android/assets/brand/`).
* Kullanıcı tarafından sağlanan oyun görselleri: `android/assets/bg`, `chars`, `objects`, `ui2` (işleme betiği: `tools/art/import_user_art.py`).
* Yazı tipi: Nunito — SIL Open Font License 1.1 (`android/assets/fonts/OFL.txt`).
* libGDX: Apache 2.0.
