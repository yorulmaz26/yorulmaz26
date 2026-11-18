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
- **5 harita** (menüde oklarla ya da kaydırarak seçilir). Her birinin engeli, tehlikesi, arka planı ve müziği farklı:

  | Harita | Engeller | Özel kural | Dekor | Müzik |
  |---|---|---|---|---|
  | Dünya Yörüngesi | Mavi gezegenler, aylar | Klasik | Dev Dünya ufku, uydular | Orta tempo, sakin |
  | Buz Kuşağı | Köşeli buz kristalleri | Çok düşük yer çekimi, yavaş düşüş | Kutup ışıkları, kar | Yavaş çanlar, davulsuz |
  | Lav Denizi | Kızgın lav kayaları | Yerden fırlayan ateş topları (önce zemin kızarır) | Dev kızıl güneş, kıvılcımlar | Hızlı, davullu |
  | Gaz Devleri | Dev halkalı gaz devleri | Roketi yukarı/aşağı iten rüzgâr (sol üstte gösterge) | Arkada dev gaz devi | Yavaş, dalgalı |
  | Asteroit Kuşağı | Dönen düzensiz asteroitler | Kuleler yukarı aşağı salınır | Toz kuşağı, uçuşan taşlar | Ritmik, senkoplu |

- **Seviyeler:** Kolay, Orta, Zor. Geçiş boşluğu, hız, kule aralığı ve haritanın özel tehlikesinin şiddeti
  (ateş topu sıklığı, rüzgâr gücü, salınım) seviyeye göre değişir.
- Her harita ve seviye için ayrı rekor tutulur. Oyun sonunda **Tekrar** ya da **Menü** seçilir, geri tuşu menüye döner.

### 2-4 oyuncu (her oyuncu kendi telefonunda)

Oyundaki **2-4 OYUNCU** ekranının sağ üstündeki **?** düğmesi de bu adımları gösterir (ilk girişte kendiliğinden açılır).

1. Oyunu bütün telefonlara kur.
2. Telefonları **aynı Wi-Fi ağına** bağla. Wi-Fi yoksa biri **hotspot** açsın, diğerleri ona bağlansın. İnternet gerekmez.
3. 1. telefon: **2-4 OYUNCU** › haritayı ve seviyeyi seç › **ODA KUR**.
4. Diğer telefonlar (en fazla 3): **2-4 OYUNCU** › **ODAYA KATIL**. Telefonlar birbirini kendiliğinden bulur.
   Lobide odadaki oyuncular renkleriyle görünür: 1 kırmızı, 2 mavi, 3 yeşil, 4 sarı.
5. Haritayı ve seviyeyi herkes değiştirebilir. Herkes gelince odayı kuran **BAŞLAT**'a basar;
   3-2-1 sayımından sonra bütün telefonlarda aynı parkur aynı anda başlar.
6. **Ekran seçimi** (lobinin altında, herkes kendi telefonu için seçer):
   - **BÖLÜNMÜŞ EKRAN:** herkesin oyunu ekranda görünür. 2 kişi: üst/alt; 3 kişi: üstte sen, altta iki rakip; 4 kişi: 2×2.
   - **TAM EKRAN:** kendi oyunun bütün ekranda büyük; diğerlerinin puanı ve durumu sağ üst köşede yazar.
7. Yanan oyuncu, diğerleri de yanana kadar **izlemeye devam eder**. Tam ekranda yanınca hâlâ oynayan biri
   gösterilir; ekrana dokunarak başka oyuncuya geçilir. Sonunda sıralama tablosu çıkar, en çok puan alan kazanır.
8. Odayı kuran **TEKRAR** ile aynı oyuncularla yeni parkur başlatabilir. **MENÜ** ya da geri tuşu odadan çıkarır.

Notlar: Maç sırasında uygulamadan çıkan oyuncu yanmış sayılır. Bazı misafir/okul Wi-Fi ağları cihazların birbirini
görmesini engeller; öyle bir ağda hotspot kullanın. Ağ kodu [`NetLink.kt`](app/src/main/java/com/example/gezegenkacisi/NetLink.kt) içinde.

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
│   ├── SoundManager.kt   kodla üretilen ses efektleri ve müzik
│   └── NetLink.kt        2-4 oyuncu: yerel ağda oda kurma, bulma ve mesajlaşma
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
