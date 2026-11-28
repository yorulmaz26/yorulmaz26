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

### 2.2: Reklamlar (AdMob)

Reklamlar oyunun akışını bozmayacak şekilde yerleştirildi:

| Reklam | Nerede | Kural |
|---|---|---|
| **Banner** | Ana ekran, Sonsuz Mod ayarı, Bölümler, Market (altta) | Oyun oynanırken, geri sayımda ve çok oyunculuda **hiç görünmez**. Ekranlar banner'a yer bırakır; banner, 3 düğmeli gezinmede geri/ana ekran düğmelerinin üstünde durur. Küçük pencerede (bölünmüş ekran) ana ekranda gösterilmez. |
| **Geçiş (resim) reklamı** | Oyun bitince yalnızca TEKRAR / MENÜ / SONRAKİ / BÖLÜMLER düğmesine basıldığında (ekranın başka yerine dokunmak bir şey yapmaz) | En az 3 oyunda bir, araya en az 2,5 dk; uygulama açıldıktan sonraki ilk 1,5 dk ve ödüllü videodan sonraki 1 dk hiç yok. Ölüm anında aniden açılmaz. |
| **Ödüllü video** | Can bitince, bölüm başarısız/geçildi ekranında, sonsuz mod sonunda | Tamamen isteğe bağlı: **+1 can**, **bölüm ödülünü 2 kat al**, **parayı 2 kat al**. Düğme yalnızca video hazırsa görünür. |

- AB/İngiltere kullanıcılarına Google'ın reklam izni penceresi (UMP) reklamlardan önce gösterilir; izin yoksa reklam istenmez.
  Gerekli bölgelerde ana ekranın altında "Reklam gizlilik ayarları" bağlantısı çıkar.
- Reklam yüklenemezse oyun etkilenmez: düğmeler gizli kalır, geçiş reklamı atlanır; banner artan aralıklarla yeniden denenir.
- Ödüllü video yalnızca düğmesine dokununca açılır (klavye/kumanda tuşu açmaz); erken kapatılsa da ardından 1 dk geçiş reklamı gelmez.
- **Yayından önce:** `app/build.gradle.kts` başındaki `admobAppId`, `adBannerId`, `adInterstitialId`, `adRewardedId`
  değerlerini AdMob hesabınızdaki kendi kimliklerinizle değiştirin. Şu an Google'ın herkese açık **test** kimlikleri var
  (reklamlarda "Test Ad" yazar, gelir getirmez). Play Console'da "Veri güvenliği" formunda reklam kimliği ve reklam
  verilerini beyan edin; web sitenizde `app-ads.txt` yayınlayın.
- Kod: [`AdManager.kt`](app/src/main/java/com/example/gezegenkacisi/AdManager.kt) (AdMob + onay),
  [`Ads.kt`](app/src/main/java/com/example/gezegenkacisi/Ads.kt) (oyunun gördüğü arayüz). Sıklık kuralları `GameView.afterBreak`.

### 3.0: Planet Gap – global yayın hazırlığı

- **İki dilli ad:** Gezegen Kaçışı / Planet Gap. Türkçe telefonda "Gezegen Kaçışı", diğer dillerde "Planet Gap".
- **6 dil:** Türkçe, İngilizce (varsayılan), Almanca, İtalyanca, Fransızca, İspanyolca. Telefon diline göre otomatik;
  Ayarlar'dan (dişli simgesi) oyunu yeniden başlatmadan değiştirilir. Eksik çeviri her derlemede denetlenir
  (`./gradlew :app:checkTranslations`). Yeni dil: `res/values-xx/strings.xml` + `GameView.Lang` + `res/xml/locales_config.xml`.
- **Canlar:** en fazla 4, her biri **20 dakikada** dolar (`GameRules.kt`: `LIFE_REGEN_SECONDS = 1200`). Can yoksa pencere:
  video izle (+1), 60 para ile +1 ya da bekle. Saat hilesine ve kayıt değiştirmeye karşı korumalı; testler `app/src/test`.
- **Android 16 (API 36)**, AGP 8.13.2, Gradle 8.14.3. Yayın imzası için `keystore.properties` (depoya eklenmez).
- **Yayın paketi:** `RELEASE_PACKAGE/` (APK, AAB, logolar, mağaza görselleri, fragmanlar, mağaza metinleri, raporlar).
  Önce `RELEASE_PACKAGE/FINAL_RELEASE_REPORT.md` dosyasını okuyun (paket adı ve imza gibi yayından önce yapılması gerekenler).

### 2.2: Bölüm denetimi (500 bölüm)

Bütün bölümler, oyunun fiziğini birebir taklit eden ve geleceği bilen bir "pilot" ile simüle edildi
(her bölüm 6 farklı dünyada, hedef puana kadar). Geçilemeyen anlar bulundu ve düzeltildi:

| Sorun | Düzeltme |
|---|---|
| Yıldırım geçidin içine kadar iniyordu | Yıldırım boyu yakındaki kulelerin geçidine göre kısalır, sığmıyorsa hiç çıkmaz |
| İleri turlarda hız 600'ü geçiyordu, iki geçit arası fark kapatılamıyordu | Kaydırma hızı en fazla 400 (`Levels.MAX_SPEED`) |
| Rüzgâr / kara delik çekimi yer çekimini yeniyordu | Toplam kuvvet sınırlı; rüzgâr en fazla yer çekiminin ¼'ü |
| Ardışık geçitler arası dikey fark, iniş/çıkış hızından büyüktü | Fark, roketin o sürede gerçekten gidebileceği mesafeyle sınırlı |
| Dar geçit + büyük salınım; sık kuleler + büyük gezegenler | Salınım ve en küçük kule aralığı sınırlandı; salınım fazı kuleden kuleye yumuşak değişir |
| Ateş topu / meteor kulelerin dibinde çıkıyordu | Kule yakınında yerden ateş topu çıkmaz |
| Bölüm zorluğu en zor değerlerde ekranı kapatıyordu | Boşluk en az 185, ilk boşluk en az 210 |

Sonuç: 500 bölümün hepsi, 6 farklı dünyada **3000/3000 geçilebilir**. Mantık hataları da düzeltildi: reklam açıkken
dokunma, telefon bildirim perdesi açılınca oyunun duraklaması, kare hızından bağımsız fizik, çok oyunculu geri sayımda
uygulamadan çıkma, kurucunun arka plandayken mesaj aktarması ve can sayacında aynı sürenin iki kez sayılması.

### 2.1: Açılış animasyonu

Oyun açılırken **Magic Lumos Software** logosu oyuna özel bir animasyonla gösterilir: seçili araç karanlık uzaydan
geçer, ışığı logoyu soldan sağa aydınlatarak ortaya çıkarır ("Lumos"), ardından ışık patlaması, harflerin üstünden
geçen parıltı ve "GEZEGEN KAÇIŞI" yazısı gelir. Ekrana dokununca atlanır. Logo resmi
`res/drawable-nodpi/splash_logo.jpg`, animasyon `GameView.kt` içindeki "Açılış" bölümündedir.

### 2.0: Bölümler, para, market ve canlar

Ana ekranda **BÖLÜMLER**, **SONSUZ MOD**, **2-4 OYUNCU** ve **MARKET** var.

**500 bölüm.** 10 haritanın her birinde 5 bölüm vardır; 50 bölümlük tur 10 kez tekrarlanır ve her turda zorlaşır.
- 1. bölümü geçmek için **15 puan** yeter. Her yeni 5'li harita grubunda hedef **2 puan artar** (15, 17, 19 …), 120'de sabitlenir.
- Her bölümün kendi özellikleri vardır: Hızlı akış, Dar geçitler, Sık kuleler, Ağır/Hafif yer çekimi, Altın yağmuru,
  Rüzgâr, Ateş topları, Meteor yağmuru, Salınan kuleler, Kara delik çekimi, Yıldırım. 1. bölüm özelliksizdir;
  her haritanın 5. bölümü "HARİTA FİNALİ"dir ve daha zordur.
- Bölümler sırayla açılır, ilerleme kaydedilir. Bölüm üretimi [`Progress.kt`](app/src/main/java/com/example/gezegenkacisi/Progress.kt) içindedir.

**Canlar (yalnızca bölüm modu).** En fazla 4 can; oyun ilk yüklendiğinde canlar doludur.
- Bölüm başlarken 1 can harcanır, bölüm geçilirse geri verilir. Yanmak, bölümden çıkmak ya da uygulamayı kapatmak canı götürür.
- Eksik canlar **15 dakikada bir** dolar; kalan süre kalp kutusunda görünür.
- **Saat hilesine karşı:** süre, telefonun saatinden değil, değiştirilemeyen açılıştan beri geçen süre sayacından ölçülür.
  Telefon yeniden başlatılırsa kapalı kalınan süre ancak internetten alınan gerçek saatle doğrulanınca sayılır.
  Telefonun saatini ileri/geri almak can kazandırmaz. Ayrıntılar: [`Lives.kt`](app/src/main/java/com/example/gezegenkacisi/Lives.kt).

**Para ve market.** Uçarken boşluklardaki paraları topla; bölüm geçince ödül parası (ilk kez geçince daha fazla) kazanılır.
Marketten 10 uçan araç alınır: Klasik, Gece ve Zümrüt roketleri, Uzay Mekiği, Kuyruklu Yıldız, UFO, Altın Roket,
Halkalı Gezegen, Lav Gezegeni ve Kristal Gemi. Para, satın alınanlar ve seçili araç kalıcıdır.

**10 harita.** Yeni haritalar: Kara Delik (ortaya çekim), Elektrik Fırtınası (yıldırım), Meteor Yağmuru,
Zehirli Bulutsu (rüzgâr + salınım) ve Galaksi Merkezi (final). Her haritanın kendi dekoru ve müziği vardır.

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
│   ├── GameConfig.kt     haritalar, mekanikler ve seviyeler
│   ├── Progress.kt       500 bölüm, bölüm özellikleri ve marketteki araçlar
│   ├── Lives.kt          canlar ve saat hilesine karşı süre ölçümü
│   ├── AdManager.kt      AdMob reklamları (banner, geçiş, ödüllü video) ve reklam izni
│   ├── Ads.kt            oyunun reklamlarla konuştuğu arayüz
│   ├── SoundManager.kt   kodla üretilen ses efektleri ve müzik
│   └── NetLink.kt        2-4 oyuncu: yerel ağda oda kurma, bulma ve mesajlaşma
└── res/
    ├── drawable/          uygulama ikonu (roket ve gezegen)
    ├── mipmap-anydpi-v26/
    └── values/            metinler, renkler, tema
```

Oyun tamamen Android'in `Canvas` API'siyle çiziliyor; tek dış kütüphaneler reklam (Google AdMob) ve reklam izni (UMP). En düşük Android sürümü 8.0.

## Sorun giderme

- **"Unsupported class file major version" veya Java sürüm hatası:** Android Studio'da
  **Settings › Build, Execution, Deployment › Build Tools › Gradle › Gradle JDK** ayarını 17 veya 21 yap.
- **Android Studio eklentiyi güncellemeyi önerirse** kabul edebilirsin. Oyun kodu bundan etkilenmez.
