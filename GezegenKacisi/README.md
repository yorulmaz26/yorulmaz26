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

## Çalıştırma

1. **Android Studio**'yu aç ve **File › Open** ile bu `GezegenKacisi` klasörünü seç.
2. Gradle senkronizasyonunun bitmesini bekle. İlk açılışta Gradle 8.9 ve Android eklentisi internetten indirilir.
3. Telefonunu USB hata ayıklama açık şekilde bağla ya da bir emülatör seç.
4. Yeşil **▶ Run** düğmesine bas.

APK dosyası üretmek için: **Build › Build App Bundle(s) / APK(s) › Build APK(s)**.
Oluşan dosya `app/build/outputs/apk/debug/app-debug.apk` konumunda olur.

Emülatörde fareyle tıklamanın yanında **Boşluk** veya **Enter** tuşu da çalışır.

## Zorluğu ayarlama

Ayarların hepsi [`GameView.kt`](app/src/main/java/com/example/gezegenkacisi/GameView.kt) dosyasının başındaki
`companion object` bloğunda:

| Sabit | Ne işe yarar |
|---|---|
| `GRAVITY` | Yer çekimi. Büyüdükçe roket daha hızlı düşer. |
| `FLAP_V` | Dokunuşta kazanılan yukarı hız. Daha negatif olursa daha yükseğe zıplar. |
| `BASE_SPEED` | Başlangıçtaki kaydırma hızı. |
| `COLUMN_SPACING` | İki gezegen kulesi arasındaki mesafe. |
| `START_GAP` / `MIN_GAP` | Geçiş boşluğunun başlangıçtaki ve en dar hâli. |

Tüm ölçüler "dünya birimi" cinsinden. Ekranın yüksekliği her zaman 1000 birim sayılır, böylece oyun
her telefonda aynı hissettirir.

## Proje yapısı

```
app/src/main/
├── AndroidManifest.xml
├── java/com/example/gezegenkacisi/
│   ├── MainActivity.kt   tam ekran ayarı ve yaşam döngüsü
│   └── GameView.kt       oyun döngüsü, fizik, çarpışma ve tüm çizimler
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
