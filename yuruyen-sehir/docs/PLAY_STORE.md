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
> Reklam yok, gerçek parayla satın alma yok; market yalnızca oyun içi kaynaklarla çalışır.

**Kategori:** Oyun › Strateji (ikincil: Simülasyon) • **Etiketler:** şehir kurma, strateji, simülasyon, çevrimdışı

## 4. Görseller (`docs/play/`)

| Dosya | Kullanım |
|---|---|
| `icon-512.png` | Yüksek çözünürlüklü simge (512×512) |
| `feature-graphic-1024x500.png` | Öne çıkan görsel |
| `telefon-*.png` | Telefon ekran görüntüleri (1080×1920), `xvfb-run ./gradlew :desktop:screenshots -Dys.size=1080x1920` ile yeniden üretilir |

## 5. Veri güvenliği formu

- Uygulama **hiçbir kullanıcı verisi toplamaz ve paylaşmaz**. İnternet izni yoktur (manifestte `INTERNET` yok).
- Oyun kaydı yalnızca cihazın uygulamaya özel depolamasında tutulur; Android yedeklemesine dahildir (`allowBackup`).
- Hesap, reklam kimliği, konum, analiz veya çökme raporu SDK'sı yoktur.
- Formda: "Veri toplanmıyor", "Veri paylaşılmıyor" seçilebilir.

## 6. İçerik derecelendirmesi (IARC) notları

Çizgi film tarzında, kansız savaş (kuleler haydut/canlı/mekanik düşmanlara ateş eder, yenilen düşman kaybolur).
Kumar, gerçek para, sohbet, kullanıcı içeriği yoktur. Beklenen derece: PEGI 7 / ESRB Everyone 10+ civarı (anket sonucu belirler).

## 7. Yayın kontrol listesi

- [ ] Yükleme anahtarı oluşturuldu ve yedeklendi
- [ ] `versionCode` / `versionName` güncellendi
- [ ] `./gradlew :logic:test :android:bundleRelease` başarılı, CI yeşil (emülatör testi dahil)
- [ ] Gerçek bir düşük segment cihazda 10 dakika oynandı (FPS göstergesi Ayarlar'da)
- [ ] Gizlilik politikası bir web adresinde yayımlandı (`docs/PRIVACY_POLICY.md`)
- [ ] Mağaza metinleri, görseller, veri güvenliği ve içerik derecelendirmesi dolduruldu
- [ ] Önce dahili test kanalına, sonra kapalı teste yüklendi
