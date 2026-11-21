# Google Play yayın rehberi — 5 Saniyelik Dünya

Durum tarihi: 21 Kasım 2026. Kurallar her yıl değişir; yüklemeden önce Play Console'daki uyarıları okuyun.

## 1. Paketin durumu (bu depoda yapılanlar)

| Gereklilik | Durum | Nasıl doğrulandı |
|---|---|---|
| Hedef API: yeni uygulamalar 31 Ağustos 2026'dan beri Android 16 (API 36) hedeflemeli | Tamam: `targetSdk = 36`, `compileSdk = 36` | `aapt2 dump badging` |
| Android App Bundle (.aab) | Tamam: `dist/5-saniyelik-dunya-1.0.0.aab` (53 MB) | — |
| 64-bit kütüphaneler | Tamam: arm64-v8a ve x86_64 var | `aapt2 dump badging` |
| 16 KB bellek sayfası desteği | Tamam: 64-bit `.so` dosyalarında LOAD hizası 0x4000 | `readelf -lW`, `zipalign -c -P 16` |
| İzinler | `VIBRATE` ve reklam kitaplığının ekledikleri (`INTERNET`, `ACCESS_NETWORK_STATE`, reklam ölçüm izinleri). `audience=child` iken reklam kimliği izni pakette yok | `aapt2 dump badging` |
| Reklam | Google AdMob: isteğe bağlı ödüllü reklamlar ve seyrek geçiş reklamı. Ayrıntı ve sizin yapacaklarınız: `REKLAM_REHBERI.md` | birim testleri, masaüstü denemesi; **gerçek cihazda denenmedi** |
| Satın alma / analiz | Yok | kaynak taraması |
| En düşük Android sürümü | 7.0 (API 24): reklam kitaplığı gerektiriyor | `aapt2 dump badging` |
| Uygulama içinde gizlilik politikası | Tamam: Ayarlar > Gizlilik Politikası | masaüstü ekran görüntüsü |
| Android 16 geri tuşu | Eski davranış korunuyor (`enableOnBackInvokedCallback="false"`): geri tuşu oyunun kendi menüsüne gider | manifest; **gerçek cihazda denenmedi** |
| Çentik / kamera deliği | Arayüz güvenli alana çekiliyor | masaüstünde çentik benzetimi; **gerçek cihazda denenmedi** |
| Tabletlerde yatay kilit | `appCategory="game"` olduğu için Android 16'da da geçerli | manifest |
| **İmza** | **EKSİK: paket hata ayıklama anahtarıyla imzalı; Play bunu kabul etmez** | `apksigner verify --print-certs` |

## 2. Sizin yapmanız gerekenler

### 2.1 Yükleme anahtarı (bir kez)

Anahtar size ait olmalı ve depoya konmamalı; kaybederseniz güncelleme yayınlamak için Play Console'dan sıfırlama istemeniz gerekir.

```bash
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
cp keystore.properties.example keystore.properties   # içindeki SIFRE alanlarını doldurun
./gradlew :android:bundleRelease
# çıktı: android/build/outputs/bundle/release/android-release.aab
```

Geçerlilik süresi 22 Ekim 2033'ten sonra bitmeli (10000 gün yeterli). İlk yüklemede kullanılan anahtar kalıcı "yükleme anahtarı" olur; Play App Signing şartlarını kabul edin (uygulama imzalama anahtarını Google üretir).

### 2.2 Geliştirici hesabı

* Tek seferlik 25 ABD doları kayıt ücreti, kimlik doğrulaması.
* Yeni kişisel hesaplarda Play Console mobil uygulamasıyla gerçek bir Android telefon (Android 10+) doğrulaması istenir.
* Kişisel hesapta mağaza sayfasında yasal adınız, ülkeniz ve geliştirici e-postanız görünür; ayrı bir iletişim e-postası açmanız iyi olur.

### 2.3 Gizlilik politikası adresi

`docs/play_store/gizlilik_politikasi.md` metnini herkese açık bir web sayfasına koyun (PDF olmamalı), reklam modu paragrafını `audience` ayarınıza göre seçin, iletişim e-postanızı yazın ve adresi Play Console > Uygulama içeriği > Gizlilik politikası alanına girin.

### 2.4 Play Console formları (Uygulama içeriği)

| Form | Bu oyun için yanıt |
|---|---|
| Uygulama erişimi | Tüm işlevler özel erişim olmadan kullanılabilir |
| Reklamlar | **Evet, reklam içeriyor** |
| İçerik derecelendirmesi (IARC) | Kategori: oyun. Gerçekçi olmayan çizgi film tehlikeleri (diken, testere), karakter yenilip yeniden doğar, kan yok, kullanıcılar arası etkileşim yok, satın alma yok, konum paylaşımı yok; reklam var |
| Hedef kitle | Sizin kararınız ve `ads.properties` içindeki `audience` ile aynı olmalı. 13 yaş altını da seçerseniz (`audience=child`) Aileler Politikası uygulanır: reklamlar çocuklara uygun modda istenir. Yalnızca 13 yaş üstü seçerseniz (`audience=general`) çizgi film tarzı "çocuklara hitap ediyor" sayılıp sorgulanabilir |
| Veri güvenliği | Reklam kitaplığı nedeniyle toplanıyor ve paylaşılıyor: yaklaşık konum, uygulama etkileşimleri, tanılama, cihaz kimlikleri (ayrıntı: `REKLAM_REHBERI.md` bölüm 2.7). Hesap oluşturma yok |
| Haber / sağlık / finans / devlet uygulaması | Hayır |
| Reklam kimliği | `audience=child`: kullanılmıyor. `audience=general`: kullanılıyor (reklam) |

### 2.5 Mağaza sayfası

Metinler `docs/play_store/magaza_metinleri.md` dosyasında, görseller aynı dosyadaki tabloda. Uygulamayı oluştururken varsayılan dili Türkçe, türü Oyun, fiyatı Ücretsiz seçin (ücretsiz yayımlanan uygulama sonradan ücretli yapılamaz).

### 2.6 Yayın sırası

1. **Dahili test:** imzalı AAB'yi yükleyin, kendi telefonunuza kurup oynayın. Oyun bugüne kadar gerçek bir Android cihazda çalıştırılmadı; ilk gerçek deneme bu olacak. Özellikle açılış, dokunma, geri tuşu, ses, titreşim ve çentikli ekranda üst köşedeki düğmelere bakın. Yükleme sonrası oluşan "yayın öncesi rapor" birkaç gerçek cihazda kurulum ve açılış çökmelerini gösterir.
2. **Kapalı test:** 13 Kasım 2023'ten sonra açılmış kişisel hesaplarda, üretim erişimi istemeden önce en az **12 test kullanıcısının 14 gün kesintisiz** katılımı gerekir. Birkaç fazla kişi ekleyin; ayrılan kullanıcının süresi sıfırlanır.
3. **Üretim erişimi başvurusu**, ardından üretim incelemesi. Hesap doğrulaması, 14 günlük test ve incelemelerle birlikte hesabı açtıktan sonra yaklaşık dört hafta ya da daha fazla sürebilir.

Kuruluş hesabı ya da 13 Kasım 2023 ve öncesinde açılmış kişisel hesap için kapalı test şartı yoktur; diğer bütün adımlar aynıdır.

Bir derlemeyi herhangi bir kanala yüklediğinizde sürüm kodu harcanır; aynı paketi düzeltip yeniden yükleyecekseniz `android/build.gradle.kts` içindeki `versionCode` değerini artırın.

Reklam geliri için ayrıca AdMob hesabı, gerçek reklam kimlikleri, `app-ads.txt` ve onay mesajları gerekir: `REKLAM_REHBERI.md`.

## 3. Bilinen riskler

* Reklam kodu gerçek cihazda hiç çalıştırılmadı. Dahili testte deneme reklamlarıyla her reklam düğmesi denenmeli (bkz. `REKLAM_REHBERI.md` bölüm 4).

* Gerçek cihaz testi yapılmadı (bu ortamda cihaz ve emülatör yok). Açılışta çökme, siyah ekran ya da çalışmayan dokunma, incelemede "bozuk işlev" gerekçesiyle ret sebebidir; dahili testte yakalanmalı.
* libGDX 1.13.1 kullanılıyor. Android 16'da geri hareketi için yeni yol yerine eski tuş yolu açık bırakıldı; Google bu seçeneği "geçici" olarak tanımlıyor. İleride libGDX 1.14.1 ya da sonrasına geçmek gerekecek.
* Zorluk ölçümü bir oyuncu modeline dayanır; gerçek oyuncu geri bildirimi kapalı testte toplanmalı.

## 4. İleri tarihli kurallar

| Tarih | Kural | Bu oyuna etkisi |
|---|---|---|
| 1 Şubat 2027 | 16 KB desteklemeyen güncellemeler yayınlanamaz | Paket zaten uyumlu |
| Ocak 2027'den itibaren | Android geliştirici doğrulaması Google Play'i de kapsayarak yayılıyor | Play Console kimlik doğrulaması bunu karşılar |
| Nisan 2027 | Yeni oyunlarda ve mağaza sayfası güncellemelerinde katlanabilir cihaz ekran görüntüleri (1:1, 4:3, 16:10) zorunlu | O tarihten sonra sayfayı güncellerseniz 3 ek görüntü gerekir |
| Ağustos 2027 | Oyunlar için büyük ekran / katlanabilir temel kalite düzeyi | Güncelleme yayınlamadan önce katlanır cihaz ve pencere boyutlandırma denenmeli |
| Her yıl 31 Ağustos | Hedef API bir düzey yükselir | Güncelleme yayınlamak için yeniden derleme gerekir |
| 30 Haziran 2027 / 30 Haziran 2028 | Kullanılan reklam kitaplığı (25.x) kullanımdan kalkıyor / sonlanıyor | 2028'den önce yeni nesil reklam kitaplığına geçilmeli (tek dosya: `AndroidAds.kt`) |

## 5. Kaynaklar ve doğrulama düzeyi

Doğrudan açılıp okunan sayfalar (developer.android.com): hedef API gerekliliği, Android 16 davranış değişiklikleri, uygulama imzalama, 16 KB sayfa boyutu, AGP sürüm tablosu, simge ölçüleri, katlanabilir cihaz duyurusu (18 Kasım 2026), geliştirici doğrulaması.

Play Console Yardım sayfaları (support.google.com) bu ortamdan açılamadı; kapalı test (12 kişi / 14 gün), gizlilik politikası, veri güvenliği ve beyan formlarıyla ilgili bilgiler bu sayfaların arama sonuçlarındaki alıntılarına dayanır. İki bağımsız araştırma ve bir doğrulama aynı sayıları verdi, ancak yüklemeden önce Play Console'un kendi ekranlarındaki güncel metni esas alın. Türkiye için kabul edilen kimlik belgeleri, hesap doğrulama süresi ve ekran görüntüsü sayısının alt sınırı doğrulanamadı.
