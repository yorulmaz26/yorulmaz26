# Düzenleme seti (fragmanlar, kapaklar, sosyal medya görselleri)

Bu klasör, videoları ve görselleri depo dışında da yeniden üretmek ya da değiştirmek için gerekenleri içerir.

| Dosya | İçerik |
|---|---|
| `fonts/Roboto-*.ttf` | Yazı tipleri (Apache License 2.0, ticari kullanım serbest) |
| `release_media.py`, `magaza.py`, `logos.py` | Üretim betikleri (Python 3 + Pillow + NumPy + ffmpeg) |
| `../timeline_<dil>.json` | Fragmanın kesim listesi: her bölümün başlangıç/bitiş süresi ve ekrandaki yazısı |
| `../clips_<dil>/*.mp4` | Ham oynanış klipleri (1080×1920, 30 FPS, yazısız). Herhangi bir kurgu programına alınabilir. |
| `../../../03_Logos/Vector/emblem.svg` | Amblemin vektör hâli |

## Ekrandaki yazılar nereden gelir?

`06_Store_Descriptions/store_texts.<dil>.json` dosyasından gelir:
- **`trailer_captions`:** `hook`, `mechanic`/`mechanic2`, `experience`, `experience2`, `variety`, `success`,
  `brand_tagline`, `cta1`/`cta2`.
- **`screenshot_captions[5]`:** bloklar.
- **`screenshot_captions[4]`:** anahtarlar ve kapılar.
- **`screenshot_captions[7]`:** görünümler.
- `variety2` fragmanda kullanılmaz.

## Yeniden üretim

Proje içinde:
```
./gradlew :app:testDebugUnitTest --tests '*PromoCaptureTest*' -PpromoDir=KAYIT/tr -PpromoLang=tr
python3 tools/magaza/release_media.py --rec KAYIT --fonts RELEASE_PACKAGE/05_Trailers/Source_Files/kit/fonts \
    --sounds app/src/main/res/raw --texts RELEASE_PACKAGE/06_Store_Descriptions --out RELEASE_PACKAGE --langs tr --only video,social
```
