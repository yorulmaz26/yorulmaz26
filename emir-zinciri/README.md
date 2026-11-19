# Emir Zinciri: Robot Komutan

> Komutları Sen Ver, Geleceği Robotların İnşa Etsin!

Android için strateji + bulmaca + robot programlama oyunu. **Kotlin + libGDX**, dikey 9:16, 2D izometrik, tamamen çevrimdışı.

Oyuncu komut kartlarını bir zincire dizer, **Başlat**'a basar ve robotların komutları deterministik bir simülasyonda uygulamasını izler. Başarısız olursa zincir korunur; yalnızca hatalı kartı düzeltir.

## Modüller

| Modül | İçerik |
|---|---|
| `logic/` | Saf Kotlin (libGDX bağımsız): simülasyon motoru, komutlar, bölüm modeli, çözücü, ilerleme, can sistemi, kayıt. JVM'de test edilir. |
| `core/` | libGDX oyunu: ekranlar, izometrik çizici, arayüz, `PlaySession` (düzenle/çalıştır durum makinesi). |
| `android/` | Android başlatıcısı (monotonik saat + açılış sayacı ile can koruması). |
| `lwjgl3/` | Masaüstü geliştirme başlatıcısı ve otomatik ekran görüntüsü. |
| `assets/` | Bölümler (`levels/area_NNN.json`), fontlar (Nunito, OFL), sentezlenmiş sesler. |
| `tools/` | `gen_sounds.py` (ses üretimi), `shot.sh` (sanal ekranda ekran görüntüsü). |

## Derleme ve çalıştırma

```bash
./gradlew :logic:test :core:test          # bütün testler (bölüm doğrulaması dahil)
./gradlew :android:assembleDebug          # APK: android/build/outputs/apk/debug/
./gradlew :lwjgl3:run                     # masaüstünde oyna (çalışma klasörü assets/)
./gradlew :logic:levelTool --args="1 5"   # alan 1–5 bölümlerini çözücüyle çöz, par/çözüm yaz
```

Android Studio: `emir-zinciri/` klasörünü proje olarak açın (JDK 17+, Android SDK 35).

Masaüstü geliştirici argümanları: `--level 12 --solve --run --shot cikti.png --after 2 --exit --size 540x960`.

## Mevcut durum

Sürüm 1.0.0: 100 alan / 500 bölüm (1–50 elle tasarlandı, 51–500 doğrulanmış üretici), çoklu robot, savaş ve patronlar, kalıcı fabrika, görevler, 100 başarım, Sonsuz Fabrika, günlük bulmaca, market, müzik. 619 otomatik test.
Yayın adımları: [`docs/PLAY_STORE.md`](docs/PLAY_STORE.md).

Kampanya yeniden üretimi: `./gradlew :logic:campaignTool --args="11 100"` (deterministik; uzun sürer).

Ayrıntı ve sıradaki işler: [`docs/ROADMAP.md`](docs/ROADMAP.md). Teknik ayrıntı: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
