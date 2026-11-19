# Google Play Hazırlığı

## 1. Yayın anahtarı (bir kez, sizin bilgisayarınızda)

Anahtar **asla depoya konmaz** (`.gitignore` içinde `*.jks`, `keystore.properties`). Kaybedilirse uygulama güncellenemez;
güvenli bir yerde yedekleyin. Google Play App Signing kullanmanız önerilir (yüklediğiniz anahtar "yükleme anahtarı" olur).

```bash
keytool -genkeypair -v -keystore yuruyen-sehir-upload.jks -alias yuruyen-sehir \
  -keyalg RSA -keysize 4096 -validity 10000
```

`yuruyen-sehir/keystore.properties` dosyasını oluşturun (depoya eklemeyin):

```properties
storeFile=yuruyen-sehir-upload.jks
storePassword=...
keyAlias=yuruyen-sehir
keyPassword=...
```

CI'da imzalamak isterseniz aynı değerleri `YS_STOREFILE`, `YS_STOREPASSWORD`, `YS_KEYALIAS`, `YS_KEYPASSWORD`
ortam değişkenleri olarak verebilirsiniz (anahtar dosyasını gizli değişkenden geri yazarak).

## 2. Paket üretimi

```bash
cd yuruyen-sehir
./gradlew :logic:test :android:bundleRelease
# Çıktı: android/build/outputs/bundle/release/android-release.aab
```

- R8 küçültme ve kaynak budama açık (`proguard-rules.pro`). Küçültülmüş sürüm CI'da emülatörde açılış, geri tuşu ve döndürme testinden geçer.
- Anahtar dosyası yoksa sürüm paketi **hata ayıklama anahtarıyla** imzalanır: yalnızca yerel deneme içindir, Play'e yüklenemez.
- Her yüklemede `android/build.gradle.kts` içindeki `versionCode` artırılmalıdır.
- Hedef API 35, en düşük API 24, uygulama kimliği `com.yuruyensehir.gocebe` (yayından sonra değiştirilemez).

## 3. Mağaza kaydı metinleri (Türkçe)

**Uygulama adı (en fazla 30):** Yürüyen Şehir: Göçebe Medeniyet

**Kısa açıklama (en fazla 80):** Dev bir taşıyıcının sırtında şehrini kur, dünyayı keşfet, medeniyetini büyüt.

**Tam açıklama:**

> Büyük Çöküş'ün ardından topluluğunuz dev bir yürüyen canlının sırtında yaşıyor. Küçük bir göçebe yerleşimi
> kalıcı, kendi kendine yetebilen hareketli bir imparatorluğa dönüştürün.
>
> • Şehir kurun: 37 farklı yapı, komşuluk avantajları ve üretim zincirleri.
> • Taşıyıcınızı koruyun: açlık, susuzluk, yorgunluk ve güven; ağırlık ve denge her kararı etkiler.
> • Dünyayı keşfedin: 12 biyomlu sonsuz bir dünya, hızlı ya da güvenli rotalar, yol olayları.
> • Ticaret ve diplomasi: 8 kültür, değişen fiyatlar, kervanlar, anlaşmalar, ittifaklar ve barış.
> • Savunun: kuleler, kalkanlar ve muhafızlarla duraklatılabilir gerçek zamanlı savaşlar.
> • Büyüyün: yeni taşıyıcı türleri, yan şehirlerden oluşan göçebe filo, uzmanlaşma, hedefler, başarımlar ve antik eserler.
> • Tamamen çevrimdışı oynanır; siz yokken şehriniz 8 saate kadar üretmeye devam eder.
>
> Reklam yok. İsteğe bağlı iki kozmetik paket Google Play üzerinden satın alınabilir; bunlar oynanışa
> hiçbir avantaj vermez ve bütün oyun parasız oynanabilir.

**Kategori:** Oyun › Strateji (ikincil: Simülasyon) • **Etiketler:** şehir kurma, strateji, simülasyon, çevrimdışı

## 4. Uygulama içi ürünler (Google Play Billing)

Play Console › Para kazanma › Ürünler › Uygulama içi ürünler bölümünde şu **tek seferlik** ürünleri oluşturun
(kimlikler koddaki `PremiumProduct` ile birebir aynı olmalı):

| Ürün kimliği | Ad | İçerik | Önerilen fiyat |
|---|---|---|---|
| `destekci_paketi` | Destekçi Paketi | Kutup Işığı Derisi + Yıldız Sancağı (yalnızca bu pakette) | düşük kademe |
| `tum_kozmetikler` | Tüm Kozmetikler | Marketteki bütün kozmetikler (oyun içi altınla da tek tek alınabilir) | orta kademe |

- Satın alma, Play `PURCHASED` bildirdikten ve uygulama onayladıktan (acknowledge) sonra işlenir; bekleyen (PENDING) ödemeler onaylanınca açılır.
- Market açılışında ve "Satın alımları geri yükle" düğmesinde sahip olunanlar sorgulanır; iade edilen ürünün öğeleri kapanır (altınla alınanlar korunur). Sorgu başarısızsa (çevrimdışı) hiçbir şey geri alınmaz.
- Sunucu tarafı doğrulama yoktur: ürünler yalnızca kozmetik olduğu için kabul edilebilir bir risktir. Değerli içerik satılacaksa Play Developer API ile sunucuda doğrulama eklenmelidir.
- **Deneme:** Play Console › Ayarlar › Lisans testi'ne test hesaplarınızı ekleyin, uygulamayı dahili test kanalına yükleyip Play'den kurun. Billing yalnızca Play'den kurulan, aynı uygulama kimliği ve imzalı sürümle çalışır; emülatördeki Google Play'siz görüntüde market "mağaza kullanılamıyor" der (CI bunu çökmeden geçtiğini doğrular).

## 5. Görseller (`docs/play/`)

| Dosya | Kullanım |
|---|---|
| `icon-512.png` | Yüksek çözünürlüklü simge (512×512) |
| `feature-graphic-1024x500.png` | Öne çıkan görsel |
| `telefon-*.png` | Telefon ekran görüntüleri (1080×1920; market ekranı bilerek yok: masaüstünde Play mağazası görünmez), `xvfb-run ./gradlew :desktop:screenshots -Dys.size=1080x1920` ile yeniden üretilir |

## 6. Veri güvenliği formu

- Uygulamanın kendi sunucusu yoktur; geliştirici **hiçbir kullanıcı verisi toplamaz, saklamaz veya paylaşmaz**.
- Google Play Billing kütüphanesi manifeste `INTERNET`, `ACCESS_NETWORK_STATE` ve `com.android.vending.BILLING` izinlerini ekler;
  bu bağlantı yalnızca satın alma için Google Play ile yapılır. Ödeme bilgisi uygulamaya hiç gelmez.
- Oyun kaydı yalnızca cihazın uygulamaya özel depolamasında tutulur; Android yedeklemesine dahildir (`allowBackup`).
- Reklam kimliği, konum, analiz veya çökme raporu SDK'sı yoktur.
- Formu doldururken Google'ın Play Billing ile ilgili güncel veri güvenliği rehberine bakın (satın alma işlemleri Google tarafından işlenir).

## 7. İçerik derecelendirmesi (IARC) notları

Çizgi film tarzında, kansız savaş (kuleler haydut/canlı/mekanik düşmanlara ateş eder, yenilen düşman kaybolur).
Kumar, sohbet ve kullanıcı içeriği yoktur. **Uygulama içi satın alma vardır** (yalnızca kozmetik); ankette ve mağaza kaydında
"Uygulama içi satın alma içerir" işaretlenmelidir. Beklenen derece: PEGI 7 / ESRB Everyone 10+ civarı (anket sonucu belirler).

## 8. Yayın kontrol listesi

- [ ] Yükleme anahtarı oluşturuldu ve yedeklendi
- [ ] `versionCode` / `versionName` güncellendi
- [ ] `./gradlew :logic:test :android:bundleRelease` başarılı, CI yeşil (emülatör testi dahil)
- [ ] Gerçek bir düşük segment cihazda 10 dakika oynandı (FPS göstergesi Ayarlar'da)
- [ ] Gizlilik politikası bir web adresinde yayımlandı (`docs/PRIVACY_POLICY.md`)
- [ ] `destekci_paketi` ve `tum_kozmetikler` ürünleri Play Console'da oluşturuldu ve etkinleştirildi
- [ ] Lisans test hesabıyla dahili test kanalında satın alma, iptal ve geri yükleme denendi
- [ ] Mağaza metinleri, görseller, veri güvenliği ve içerik derecelendirmesi dolduruldu
- [ ] Önce dahili test kanalına, sonra kapalı teste yüklendi
