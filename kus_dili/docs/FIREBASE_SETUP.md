# Firebase kurulumu (Aşama 3)

Bu adımları yalnızca bir kez, kendi bilgisayarınızda yapmanız gerekir. Kod,
Firebase'i platform yapılandırma dosyalarından okur; bu dosyalar depoya
**eklenmez** (`.gitignore`).

## Kolay yol: yalnızca tarayıcıyla (bilgisayar ve komut gerekmez)

Android için test APK'sını GitHub kendisi derler; siz yalnızca Firebase
konsolunda tıklarsınız.

1. <https://console.firebase.google.com> → **Proje oluştur** → plan: **Blaze**.
2. Proje ana sayfası → **Android** simgesi → paket adı `com.kusdili.kus_dili`.
   Aynı ekranda **SHA-1** alanına (sonra Proje ayarları → Parmak izi ekle ile
   **SHA-256** de) test anahtarının parmak izlerini girin:
   * SHA-1: `53:47:F8:A7:1F:15:69:5C:03:1B:FE:58:2E:F8:37:27:7A:05:30:42`
   * SHA-256: `67:53:1A:BA:38:6D:38:65:4D:26:60:4F:6F:89:94:91:B4:A1:8C:1F:13:F8:21:D5:24:BB:BF:66:19:71:89:E7`
   **google-services.json** dosyasını indirin.
3. **Authentication → Başlayın → Telefon → Etkinleştir.** Deneme için
   "Test için telefon numaraları" bölümüne ör. `+90 555 000 00 01` / `123456`.
4. **Firestore Database → Veritabanı oluştur** (bölge `eur3`, üretim modu) →
   **Kurallar** sekmesi → içeriği silip `kus_dili/firebase/firestore.rules`
   dosyasının tamamını yapıştırın → **Yayınla**.
5. GitHub deposu → **Settings → Secrets and variables → Actions → New
   repository secret**: ad `KUSDILI_GOOGLE_SERVICES_JSON` (depodaki diğer uygulamanın `GOOGLE_SERVICES_JSON` gizlisine dokunmayın), değer olarak indirdiğiniz
   dosyanın **içeriğini** yapıştırın.
6. **Actions** sekmesinde son "Kuş Dili" çalışmasını **Re-run all jobs** ile
   yeniden çalıştırın; bitince `kus-dili-apk` dosyasını indirip kurun.

Bu yolla giriş, mesajlaşma ve gruplar çalışır. **Bildirimler** ve **uygulama
içinden hesap silme** için Cloud Functions'ın yüklenmesi gerekir (§5c, komut
satırı ister). Test anahtarı herkese açıktır; mağaza sürümü kendi gizli
anahtarınızla imzalanmalı (RELEASE.md §2).

## 1. Proje

1. <https://console.firebase.google.com> → **Proje ekle** (ör. `kus-dili`).
2. **Blaze** (kullandıkça öde) planına geçin. Telefonla SMS doğrulaması ücretsiz
   planda çalışmaz. Bütçe uyarısı (Billing → Budgets) kurmanız önerilir.

## 2. Uygulamaları bağlama

```bash
npm install -g firebase-tools
firebase login
dart pub global activate flutterfire_cli
cd kus_dili
flutterfire configure --project=<proje-id> --platforms=android,ios \
  --android-package-name=com.kusdili.kus_dili --ios-bundle-id=com.kusdili.kusDili
```

Bu komut şunları oluşturur/düzenler:

* `android/app/google-services.json` ve Gradle'a `com.google.gms.google-services` eklentisi
* `ios/Runner/GoogleService-Info.plist`
* `lib/firebase_options.dart` (uygulama bu dosyayı kullanmaz; zararı yok)

> iOS bundle kimliğini Xcode'daki `PRODUCT_BUNDLE_IDENTIFIER` ile aynı yazın.

## 3. Telefonla giriş

**Authentication → Sign-in method → Telefon → Etkinleştir.**

İsteğe bağlı: **Authentication → Settings → SMS region policy** ile yalnızca
kullanacağınız ülkelere (ör. Türkiye) izin verin; SMS dolandırıcılığına karşı
önemlidir.

Geliştirme sırasında SMS harcamamak için: **Telefon → Test için telefon
numaraları** bölümüne ör. `+90 555 000 00 01` / `123456` ekleyin.

### Android

```bash
cd android && ./gradlew signingReport
```

Çıktıdaki **SHA-1** ve **SHA-256** değerlerini (debug ve release anahtarları)
**Proje ayarları → Android uygulaması → Parmak izi ekle** bölümüne girin.
Play Store'a yüklediğinizde Play Console'daki **uygulama imzalama anahtarının**
parmak izlerini de ekleyin. Bunlar eksikse giriş `app-not-authorized` /
`invalid-app-credential` hatası verir.

### iOS

1. Apple Developer → Keys → **APNs anahtarı** oluşturun (.p8) ve
   **Proje ayarları → Cloud Messaging → Apple uygulaması** bölümüne yükleyin.
2. Xcode → Runner → Signing & Capabilities → **Push Notifications** ve
   **Background Modes → Remote notifications** ekleyin.
3. `GoogleService-Info.plist` içindeki `REVERSED_CLIENT_ID` değerini
   (yoksa "Encoded App ID" değerini) Runner → Info → **URL Types** bölümüne
   URL Scheme olarak ekleyin. (APNs sessiz bildirimi gelmezse reCAPTCHA
   doğrulaması bu şemaya döner.)

## 4. App Check

**App Check → Uygulamalar**: Android için **Play Integrity**, iOS için
**App Attest** (DeviceCheck yedekli) sağlayıcısını kaydedin.

Debug derlemeleri hata ayıklama sağlayıcısı kullanır: uygulamayı ilk
çalıştırdığınızda konsol günlüğünde çıkan debug token'ı
**App Check → Debug token'ları yönet** bölümüne ekleyin.

Her şey çalıştıktan sonra **Firestore** ve **Authentication** için
"Zorunlu kıl" (Enforce) seçeneğini açın.

## 5. Firestore ve güvenlik kuralları

1. **Firestore Database → Veritabanı oluştur** → konum: `eur3` (Avrupa) veya
   `europe-west3`; **üretim modu**.
2. Kuralları yükleyin:

```bash
cd kus_dili
firebase use <proje-id>
firebase deploy --only firestore:rules,firestore:indexes
```

Kurallar `firebase/firestore.rules` dosyasındadır; değiştirmeden önce testleri
çalıştırın (aşağıda).

3. **Süreli mesajlar için TTL politikası** (bir kez):

```bash
gcloud firestore fields ttls update expireAt \
  --collection-group=messages --enable-ttl --project=<proje-id>
```

veya konsolda **Firestore → TTL → Politika oluştur** (koleksiyon grubu
`messages`, alan `expireAt`). Firestore süresi dolan belgeleri genellikle
24 saat içinde siler; uygulama bu sırada süresi dolan mesajları zaten göstermez.

## 5c. Bildirimler (Cloud Functions + FCM)

1. Blaze planı gerekli (Aşama 3'te açıldı).
2. iOS için APNs anahtarı Firebase'e yüklenmiş olmalı (§3 iOS). Xcode'da
   **Push Notifications** ve **Background Modes → Remote notifications** açık.
3. Fonksiyonu yükleyin:

```bash
cd kus_dili/functions && npm ci && cd ..
firebase deploy --only functions:kusdili,firestore:rules
```

Fonksiyonlar: `notifyNewMessage` (içeriksiz bildirim; göndereni engelleyen
alıcıya gitmez) ve `deleteAccount` (uygulama içinden hesap silme).

Fonksiyon `europe-west1` bölgesinde çalışır; isterseniz `functions/src/index.ts`
içindeki `REGION` değerini Firestore bölgenize yakın bir bölgeyle değiştirin.

## 5d. Şikâyetleri inceleme

Şikâyetler `reports` koleksiyonuna düşer ve uygulamadan okunamaz. Konsolda
**Firestore → reports** altından inceleyin; kuralı çiğneyen hesabı
**Authentication → Kullanıcılar → Hesabı devre dışı bırak** ile kapatın.
Şikâyette yalnızca kullanıcının onay verdiği (en fazla 5) mesajın metni
bulunur; diğer mesajlar şifrelidir.

## 6. Yerel emülatörle geliştirme (isteğe bağlı)

```bash
cd kus_dili
firebase emulators:start --only auth,firestore --project demo-kusdili
flutter run --dart-define=USE_FIREBASE_EMULATOR=true
# Gerçek cihazda: --dart-define=FIREBASE_EMULATOR_HOST=<bilgisayarın-IP'si>
```

Auth emülatörü SMS göndermez; kod emülatör arayüzünde
(<http://localhost:4000/auth>) görünür.

## 7. Testler

```bash
# Güvenlik kuralları (Java 11+ ve Node 20+ gerekir)
cd kus_dili/firebase/rules-test && npm ci && npm test

# Uygulama
cd kus_dili && flutter test
```
