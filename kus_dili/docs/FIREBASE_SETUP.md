# Firebase kurulumu (Aşama 3)

Bu adımları yalnızca bir kez, kendi bilgisayarınızda yapmanız gerekir. Kod,
Firebase'i platform yapılandırma dosyalarından okur; bu dosyalar depoya
**eklenmez** (`.gitignore`).

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

## 5b. Storage (fotoğraf, video, ses, belge)

1. **Storage → Başlayın** → üretim modu, Firestore ile aynı bölge.
2. Kuralları yükleyin (kurallar Firestore'dan üyeliği okur; CLI ilk seferde
   bunun için gerekli izni vermeyi sorar, **Evet** deyin):

```bash
firebase deploy --only storage
```

3. Önerilen: sahipsiz/eskimiş şifreli dosyalar için Google Cloud Console →
   Cloud Storage → kova → **Yaşam döngüsü** → "30 günden eski nesneleri sil".

## 5c. Bildirimler (Cloud Functions + FCM)

1. Blaze planı gerekli (Aşama 3'te açıldı).
2. iOS için APNs anahtarı Firebase'e yüklenmiş olmalı (§3 iOS). Xcode'da
   **Push Notifications** ve **Background Modes → Remote notifications** açık.
3. Fonksiyonu yükleyin:

```bash
cd kus_dili/functions && npm ci && cd ..
firebase deploy --only functions:kusdili,firestore:rules
```

Fonksiyon `europe-west1` bölgesinde çalışır; isterseniz `functions/src/index.ts`
içindeki `REGION` değerini Firestore bölgenize yakın bir bölgeyle değiştirin.

## 5d. Aramalar için TURN sunucusu (önerilir)

STUN tek başına mobil ağlarda çoğu zaman yetmez. Kendi coturn sunucunuzu
kurun (ör. küçük bir VPS, 3478/UDP+TCP ve 5349/TLS açık) ve
`/etc/turnserver.conf` içinde:

```
use-auth-secret
static-auth-secret=<uzun-rastgele-gizli>
realm=kusdili
```

Ardından gizli anahtarı ve adresleri Firebase'e verin (gizli anahtar Secret
Manager'da durur, uygulamaya gömülmez):

```bash
firebase functions:secrets:set TURN_SECRET          # coturn'deki değer
# functions/.env.<proje-id> dosyasına:
# TURN_URLS=turn:turn.alanadiniz.com:3478,turns:turn.alanadiniz.com:5349
firebase deploy --only functions:kusdili
```

## 6. Yerel emülatörle geliştirme (isteğe bağlı)

```bash
cd kus_dili
firebase emulators:start --only auth,firestore,storage --project demo-kusdili
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
