# 5 Saniyelik Dünya

Her 5 saniyede kuralların değiştiği, renkli ve sevimli bir dikey Android platform oyunu. Kotlin + libGDX + Box2D.

* 10 harita × 10 alan × 5 bölüm = **100 alan, 500 doğrulanmış bölüm**, **Sonsuz Dünya** modu
* 10 dünya mekaniği ve dengeli kombinasyonları
* XP/seviye, yıldızlar, 20 karakter, 9 iz efekti, günlük/haftalık görevler, 50 başarı, gizli koleksiyon, 7 günlük giriş ödülü, mağaza, 8 güçlendirme
* Tamamen çevrimdışı, Türkçe, açık renkli tasarım; grafikler ve sesler programatik üretildi

## Hazır dosyalar (`dist/`)
* `5-saniyelik-dunya-1.0.0.apk` — telefona kurulabilir (release, R8; hata ayıklama anahtarıyla imzalı)
* `5-saniyelik-dunya-1.0.0-debug.apk` — hata ayıklama sürümü
* `5-saniyelik-dunya-1.0.0.aab` — Google Play paketi (yüklemeden önce kendi anahtarınızla imzalayın)

## Derleme
```bash
echo "sdk.dir=/ANDROID/SDK/YOLU" > local.properties
./gradlew :android:assembleRelease :android:bundleRelease   # APK + AAB
./gradlew :core:test                                        # 39 test (500 bölüm doğrulaması dahil)
./gradlew :desktop:run                                      # masaüstünde oyna (assets klasöründen)
./gradlew :core:generateLevels                              # 500 bölümü yeniden üret ve doğrula
python3 tools/art/gen_art.py assets android/res             # grafikleri yeniden üret
python3 tools/audio/gen_audio.py assets                     # sesleri yeniden üret (ffmpeg gerekir)
```
Play için imzalama: `keystore.properties.example` dosyasını `keystore.properties` olarak kopyalayıp doldurun.

Belgeler: [ARCHITECTURE.md](ARCHITECTURE.md) · [TEST_REPORT.md](TEST_REPORT.md) · [PROGRESS.md](PROGRESS.md) · [TASKS.md](TASKS.md)
