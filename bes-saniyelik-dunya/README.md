# 5 Saniyelik Dünya

Her 5 saniyede kuralların değiştiği, renkli bir **yatay** (landscape) Android platform oyunu. Kotlin + libGDX + Box2D.

* 10 harita × 10 alan × 5 bölüm = **100 alan, 500 doğrulanmış bölüm**, **Sonsuz Dünya** modu
* 10 dünya mekaniği ve dengeli kombinasyonları
* XP/seviye, yıldızlar, 20 karakter, 9 iz efekti, günlük/haftalık görevler, 50 başarı, gizli koleksiyon, 7 günlük giriş ödülü, mağaza, 8 güçlendirme
* Açılışta oyuna özel **Magic Lumos Software** logo animasyonu (önizleme: `docs/intro_animasyon.gif`)
* Yapay zekâ ile üretilmiş görseller (`art_input/`), otomatik temizleme ve atlas hattı (`tools/art/user_art.py`)
* Çevrimdışı da oynanır; Türkçe, açık renkli tasarım; grafikler ve sesler programatik üretildi
* Reklam: Google AdMob ile isteğe bağlı ödüllü reklamlar, yalnızca bölüm tamamlanınca seyrek gösterilen geçiş reklamı ve yalnızca liste menülerinin altında afiş (oyun sırasında afiş yok)

## Hazır dosyalar (`dist/`)
* `5-saniyelik-dunya-1.0.0.apk` — telefona kurulabilir (release, R8; hata ayıklama anahtarıyla imzalı; **deneme** reklam kimlikleriyle, Android 7.0+)
* `5-saniyelik-dunya-1.0.0.aab` — Google Play paketi (yüklemeden önce kendi anahtarınızla imzalayın)

## Derleme
```bash
echo "sdk.dir=/ANDROID/SDK/YOLU" > local.properties
./gradlew :android:assembleRelease :android:bundleRelease   # APK + AAB
./gradlew :core:test                                        # birim + entegrasyon testleri (500 bölüm doğrulaması dahil)
./gradlew :desktop:run                                      # masaüstünde oyna (assets klasöründen)
./gradlew :core:generateLevels                              # 500 bölümü yeniden üret, ölç ve hedef zorluğa oturt (~30 dk)
./gradlew :core:analyzeLevels                               # 500 bölümü "insan gibi" oynatıp ölç -> docs/difficulty_report.tsv
python3 tools/level_strip.py assets <dump> <çıktı>          # her bölümün şerit görseli (analyzeLevels -Pdump=<dump> ile)
python3 tools/art/gen_art.py assets android/res             # grafikleri yeniden üret
python3 tools/audio/gen_audio.py assets                     # sesleri yeniden üret (ffmpeg gerekir)
```
Play için imzalama: `keystore.properties.example` dosyasını `keystore.properties` olarak kopyalayıp doldurun.

Reklam: kökteki `ads.properties` dosyasına AdMob kimliklerinizi ve hedef kitleyi (`audience`) yazın; dosya Google'ın deneme kimlikleriyle gelir. Masaüstünde reklam akışlarını sahte reklamla denemek için: `./gradlew :desktop:run -Dads=fake -DtourAds=1 -Dshots=<klasör>`.

Belgeler: [ARCHITECTURE.md](ARCHITECTURE.md) · [TEST_REPORT.md](TEST_REPORT.md) · [PROGRESS.md](PROGRESS.md) · [TASKS.md](TASKS.md) · [Yayın rehberi](docs/play_store/YAYIN_REHBERI.md) · [Reklam rehberi](docs/play_store/REKLAM_REHBERI.md)
