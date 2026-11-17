# Gezegen Kaçışı

Tek dokunuşla oynanan küçük bir Android uzay oyunu. Bir roketi yönetiyorsun: ekrana her dokunuşta
motor ateşlenir ve roket yukarı itilir, bırakınca yer çekimi aşağı çeker. Yukarıdan ve aşağıdan
uzanan gezegen kulelerinin arasındaki boşluktan geçtikçe puan kazanırsın.

- Her engel, boşluğun kenarında büyük bir gezegen ve onu ekranın kenarına bağlayan uydulardan oluşur.
- Gezegen çeşitleri: gaz devi (bazıları halkalı), kızıl kayalık, buz, lav ve okyanus gezegeni.
- Roket alev ve egzoz izi bırakır, çarpınca patlar, ekran sarsılır ve telefon titrer.
- Puan arttıkça oyun hızlanır, geçiş boşluğu daralır.
- En iyi skor telefonda saklanır.
- Uygulamadan çıkınca oyun duraklar, dokununca kaldığın yerden devam edersin.

### 1.1 ile gelenler

- **Ses ve müzik:** İtiş, puan, patlama, rekor ve menü sesleri ile her haritaya özel döngülü müzik.
  Ses dosyası yok, hepsi `SoundManager.kt` içinde kodla üretiliyor. Menüdeki hoparlör düğmesi sesi açıp kapatır.
- **5 harita** (menüde oklarla ya da kaydırarak seçilir):

  | Harita | Fark |
  |---|---|
  | Dünya Yörüngesi | Dengeli, karışık gezegenler |
  | Buz Kuşağı | Düşük yer çekimi, roket süzülür |
  | Lav Denizi | Daha hızlı akış, biraz daha güçlü yer çekimi |
  | Gaz Devleri | Büyük ve halkalı gezegenler |
  | Asteroit Kuşağı | Kuleler yukarı aşağı salınır |

- **Seviyeler:** Kolay, Orta, Zor. Geçiş boşluğu, hız, kule aralığı ve salınım seviyeye göre değişir.
- Her harita ve seviye için ayrı rekor tutulur. Oyun sonunda **Tekrar** ya da **Menü** seçilir, geri tuşu menüye döner.

## Çalıştırma

1. **Android Studio**'yu aç ve **File › Open** ile bu `GezegenKacisi` klasörünü seç.
2. Gradle senkronizasyonunun bitmesini bekle. İlk açılışta Gradle 8.9 ve Android eklentisi internetten indirilir.
3. Telefonunu USB hata ayıklama açık şekilde bağla ya da bir emülatör seç.
4. Yeşil **▶ Run** düğmesine bas.

APK dosyası üretmek için: **Build › Build App Bundle(s) / APK(s) › Build APK(s)**.
Oluşan dosya `app/build/outputs/apk/debug/app-debug.apk` konumunda olur.

Emülatörde fareyle tıklamanın yanında **Boşluk** veya **Enter** tuşu da çalışır.

## Zorluğu ayarlama

- Seviyeler ve haritalar [`GameConfig.kt`](app/src/main/java/com/example/gezegenkacisi/GameConfig.kt) dosyasında.
  `Difficulty` içinde boşluk (`startGap`, `minGap`), hız (`baseSpeed`, `speedPerScore`) ve kule aralığı (`spacing`) var.
  Her `GameMap` için yer çekimi, itiş, hız ve gezegen boyutu çarpanları ayarlanabilir.
- Temel fizik (`GRAVITY`, `FLAP_V`, `MAX_FALL`) [`GameView.kt`](app/src/main/java/com/example/gezegenkacisi/GameView.kt) içindeki `companion object` bloğunda.

Tüm ölçüler "dünya birimi" cinsinden. Ekranın yüksekliği her zaman 1000 birim sayılır, böylece oyun
her telefonda aynı hissettirir.

## Proje yapısı

```
app/src/main/
├── AndroidManifest.xml
├── java/com/example/gezegenkacisi/
│   ├── MainActivity.kt   tam ekran ayarı, yaşam döngüsü, geri tuşu
│   ├── GameView.kt       menü, oyun döngüsü, fizik, çarpışma ve tüm çizimler
│   ├── GameConfig.kt     haritalar ve seviyeler
│   └── SoundManager.kt   kodla üretilen ses efektleri ve müzik
└── res/
    ├── drawable/          uygulama ikonu (roket ve gezegen)
    ├── mipmap-anydpi-v26/
    └── values/            metinler, renkler, tema
```

Harici kütüphane kullanılmadı. Her şey Android'in `Canvas` API'siyle çiziliyor. En düşük Android sürümü 8.0.

## Sorun giderme

- **"Unsupported class file major version" veya Java sürüm hatası:** Android Studio'da
  **Settings › Build, Execution, Deployment › Build Tools › Gradle › Gradle JDK** ayarını 17 veya 21 yap.
- **Android Studio eklentiyi güncellemeyi önerirse** kabul edebilirsin. Oyun kodu bundan etkilenmez.
