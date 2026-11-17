# Firebase Kurulumu (telefondan yapılabilir, bilgisayar gerekmez)

Bu adımlar bir **test projesi** içindir. Süre: yaklaşık 30–45 dakika. Her şey tarayıcıdan yapılır;
sunucu kodunu GitHub Actions yükler.

## A. Firebase projesi
1. https://console.firebase.google.com › **Proje ekle** › ad: `kuaforum-cepte-test` (Analytics gerekmez).
   Oluşan **proje kimliğini** not edin (ör. `kuaforum-cepte-test-1a2b3`).
2. **Plan:** sol alttaki "Yükselt" › **Blaze (kullandıkça öde)**. Sunucu fonksiyonları ücretsiz planda çalışmaz.
   Test kullanımı ücretsiz kotaların içinde kalır; güvenlik için **bütçe uyarısı** (ör. 1 USD) kurun.
3. **Authentication** › Başlayın › Sign-in method › **Telefon** › Etkinleştir.
   Aynı sayfada **"Test için telefon numaraları"**: SMS beklemeden giriş için ekleyin:
   | Numara | Kod | Kim |
   |---|---|---|
   | +90 555 000 0001 | 123456 | Salon sahibi |
   | +90 555 000 0002 | 123456 | Uzman |
   | +90 555 000 0003 | 123456 | Müşteri |
4. **Firestore Database** › Veritabanı oluştur › konum **europe-west1 (Belçika)** › üretim modu.
5. **Storage** › Başlayın › aynı konum.
6. **Hosting** › Başlayın › adımları "İleri" ile geçin (komut çalıştırmanız gerekmez).
7. **Proje ayarları (⚙️) › Genel › Uygulama ekle › Android**
   - Paket adı: **`com.kuaforumcepte.debug`** (test APK'sının paket adı)
   - SHA-1: `CD:A7:C4:68:D5:A1:2A:0B:4A:6E:BE:C0:CD:D6:B1:A7:63:2F:91:44`
   - Kaydettikten sonra aynı uygulamaya "Parmak izi ekle" ile SHA-256:
     `02:62:50:E1:51:0C:DB:86:80:ED:EC:A8:A5:88:68:1C:3B:18:F0:10:B6:88:DE:B8:26:59:C6:33:72:9E:4C:71`
   - **google-services.json** dosyasını indirin.
   (Bu parmak izleri depodaki ortak test imzasına aittir; yayın sürümü için ayrı anahtar kullanılır.)
8. **Proje ayarları › Hizmet hesapları › "Yeni özel anahtar oluştur"** › JSON dosyası iner. Bu dosya gizlidir; kimseyle paylaşmayın.
9. Hizmet hesabına yükleme yetkisi: https://console.cloud.google.com/iam-admin/iam (üstten projenizi seçin)
   › `firebase-adminsdk-…@…iam.gserviceaccount.com` satırında kalem › **Rol ekle › Sahip (Owner)** › Kaydet.
   (Yalnızca test projesi için. Üretimde daha dar roller verin.)

## B. GitHub gizli değişkenleri
GitHub › depo › **Settings › Secrets and variables › Actions › New repository secret**:
| Ad | İçerik |
|---|---|
| `GOOGLE_SERVICES_JSON` | 7. adımdaki dosyanın **tüm** metni |
| `FIREBASE_SERVICE_ACCOUNT` | 8. adımdaki JSON dosyasının **tüm** metni |

Telefonda: dosyayı bir metin görüntüleyiciyle açın (ör. Dosyalar › Birlikte aç › metin), tümünü seçip kopyalayın.

## C. Sunucuyu yükle
GitHub › **Actions › "Firebase'e Yükle" › Run workflow** (App Check: `false`) › yeşil olana kadar bekleyin (~5–10 dk).
İlk yüklemede "izinler yayılıyor / Eventarc" benzeri bir hata çıkarsa 5 dakika bekleyip **yeniden çalıştırın**.

## D. APK'yı al ve dene
1. GitHub › **Actions › CI › Run workflow** (veya yeni bir push). Bitince çalıştırma sayfasının altındaki
   **Artifacts › kuaforum-cepte-debug-apk** dosyasını indirip kurun. Bu APK artık Firebase'e bağlıdır.
2. Yeni salonlar **onaylanana kadar müşteri aramasında görünmez** (kod/QR/link ile her zaman açılır).
   Kendinizi yönetici yapın: uygulamaya bir kez giriş yaptıktan sonra GitHub › **Actions › "Yönetici Ata" › Run workflow**
   › telefonunuz (ör. `+905550000001`). Uygulamada çıkış yapıp yeniden girin › Hesap › **Moderasyon ve Salon Onayı** › Salon onayı.
3. Önerilen deneme (3 telefon veya tek telefonda çıkış/giriş):
   - 0001 ile **Kuaför Girişi** › salon kaydı › Hizmetlerim › çalışma saatleri.
   - Stilist Yönetimi › 0002 numarasını davet et › 0002 ile Kuaför Girişi › daveti kabul.
   - 0003 ile **Müşteri Girişi** › salon kodunu girin › uzman › hizmet › saat › onay.
   - 0002'de bildirim/randevu › Onayla; 0003'te durum anında değişir.

## Sorun giderme
| Belirti | Çözüm |
|---|---|
| Uygulama "Firebase yapılandırılmadı" diyor | APK, `GOOGLE_SERVICES_JSON` eklenmeden önce derlenmiş; CI'ı yeniden çalıştırın. |
| SMS/kod ekranında hata | Test numarası ve SHA-1/SHA-256 eklendi mi (A.3, A.7)? Paket adı `com.kuaforumcepte.debug` mı? |
| İşlemlerde "UNAUTHENTICATED"/App Check hatası | C adımını App Check `false` ile çalıştırın. |
| "The query requires an index" | C adımı tamamlanmadı veya indeksler hâlâ oluşuyor (birkaç dakika). |
| Yükleme "Permission denied" | B.9 rolü verilmedi veya yanlış hizmet hesabına verildi. |
