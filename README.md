# Kuaförüm Cepte

> Güzelliğe Bir Dokunuş, Randevuya Tek Tık.

Türkiye pazarı için Android kuaför/güzellik salonu randevu uygulaması.
Kotlin + Jetpack Compose (Material 3) istemci, Firebase (Auth, Firestore, Functions, Storage, FCM, App Check) arka uç.

- Mimari, rol–yetki matrisi, veri modeli, durum makinesi, müsaitlik ve finans: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Cloud Functions API sözleşmeleri: [`docs/API_CONTRACTS.md`](docs/API_CONTRACTS.md)

## Depo yapısı

```
app/                    Android uygulaması (Compose, Hilt, Navigation, DataStore, Firebase)
  src/main/java/com/kuaforumcepte/app/
    core/designsystem/  Tema (renk, tipografi, şekil, boşluk) + ortak bileşenler
    core/navigation/    Type-safe route'lar ve NavHost
    core/firebase/      Firebase servisleri, emülatör bağlantısı, App Check
    core/datastore/     Cihaz tercihleri (Beni hatırla, aktif salon, tema)
    feature/            Ekranlar (splash, rol seçimi, ...)
  src/debug|release/    App Check sağlayıcısı (Debug / Play Integrity)
core/domain/            Saf Kotlin iş kuralları (para, durum makinesi, dönemler, yetki, telefon)
functions/              Cloud Functions (TypeScript) — yetkili sunucu işlemleri ve domain çekirdeği
tests/rules/            Firestore güvenlik kuralı testleri (Emulator Suite)
hosting/public/         App Link doğrulama ve web fallback sayfaları
firestore.rules  firestore.indexes.json  storage.rules  firebase.json
```

## Gereksinimler

| Araç | Sürüm |
|---|---|
| Android Studio | Narwhal (2025.1) veya üzeri, Android SDK 36 |
| JDK | 17+ (Android Studio'nun gömülü JDK'sı yeterli) |
| Node.js | 22 (Cloud Functions çalışma zamanı) |
| Firebase CLI | `npm i -g firebase-tools` |

> Bağımlılık sürümleri `gradle/libs.versions.toml` dosyasındadır. Android Studio daha yeni kararlı sürüm önerirse güncelleyebilirsiniz.

## Sizden gerekenler (projeye konmayan bilgiler)

1. **Firebase projesi** (Blaze planı — Cloud Functions ve Scheduler için gerekli). Bölge: `europe-west1`.
2. **Android uygulama kayıtları**: `com.kuaforumcepte` (release) ve `com.kuaforumcepte.debug` (debug).
3. **SHA-1 ve SHA-256 parmak izleri** (debug + release + Play App Signing) → Firebase Console'a ekleyin (telefon doğrulaması ve App Check için zorunlu).
4. **`app/google-services.json`** — Console'dan indirin. Repoya eklenmez (`.gitignore`).
5. **Release imzalama anahtarı** → `keystore.properties` (şablon: `keystore.properties.example`).
6. **Play Console hesabı** (Play Integrity / App Check ve yayın için).
7. **App Link alan adı** (varsayılan `kuaforumcepte.web.app`; `local.properties` → `appLinkHost`).
8. KVKK aydınlatma metni, açık rıza metni ve gizlilik politikası için **hukuki inceleme**.

## Firebase kurulumu

```bash
firebase login
cp .firebaserc.example .firebaserc     # proje kimliğinizi yazın
# Console: Authentication > Sign-in method > Phone'u etkinleştirin
# Console: Authentication > Settings > SMS bölge politikası: yalnızca Türkiye (+90) önerilir
# Console: Firestore (europe-west1) ve Storage oluşturun
firebase deploy --only firestore:rules,firestore:indexes,storage
cd functions && npm ci && npm run build && cd ..
firebase deploy --only functions
```

**Gerçek SMS** yalnızca yapılandırılmış bir Firebase projesiyle gönderilir. Geliştirmede
Authentication > Phone > *Test için telefon numaraları* bölümünden sabit kodlu test numaraları ekleyin
veya Auth Emulator kullanın. Uygulama SMS kodu üretmez veya kendi başına doğrulamaz.

**App Check**: Debug build ilk açılışta Logcat'e bir debug token yazar
(`DebugAppCheckProvider` etiketi); Console > App Check > *Debug token'ları yönet* ekranına ekleyin.

## Yerel geliştirme (Emulator Suite)

```bash
# 1) Emülatörleri başlatın (Auth, Firestore, Functions, Storage)
cd functions && npm install && npm run build && cd ..
firebase emulators:start --project demo-kuaforum

# 2) local.properties dosyasına ekleyin (şablon: local.properties.example)
useFirebaseEmulator=true
firebaseEmulatorHost=10.0.2.2     # gerçek cihazda bilgisayarınızın LAN IP'si
```

Emülatör bağlantısı yalnızca **debug** build'de etkindir; release build daima gerçek Firebase'i kullanır.
Demo/Mock modu yoktur.

## Android uygulamasını çalıştırma

1. Android Studio → *Open* → depo kök klasörü.
2. `app/google-services.json` dosyasını ekleyin (yoksa uygulama açılır ve **"Firebase yapılandırması bulunamadı"** ekranını gösterir).
3. *Run 'app'* (debug).

Komut satırı:

```bash
./gradlew :app:assembleDebug
./gradlew :core:domain:test :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest     # bağlı cihaz/emülatör gerekir
```

## Testler

| Komut | Kapsam |
|---|---|
| `./gradlew :core:domain:test` | Para/kuruş, durum makinesi, 2 saat iptal sınırı, İstanbul dönem sınırları, yetki matrisi, telefon normalizasyonu, hizmet seçimi toplamları |
| `./gradlew :app:testDebugUnitTest` | Splash yönlendirme kararı (Beni hatırla), giriş sonrası rol/salon yönlendirmesi |
| `./gradlew :app:connectedDebugAndroidTest` | Compose UI: rol seçimi |
| `cd functions && npm test` | Müsaitlik/slot algoritması, molalar, kapalı günler, çakışma, zaman dilimi ve dönem anahtarları, durum geçişleri, salon kodu tahsisi, puan ortalaması, il/ilçe |
| `cd functions && npm run test:integration` | Emulator: kayıtlar, benzersiz salon kodu, davetler, salon profili/görseller, takvim değişikliği çakışma analizi (önizleme/onay/işaretleme/bildirim), kapatmalar, hizmet etkisi |
| `cd tests/rules && npm install && npm test` | Firestore güvenlik kuralları (Emulator) |

## Geliştirmede SMS girişi

Firebase Console → Authentication → Sign-in method → Phone → **Phone numbers for testing** bölümüne örneğin
`+90 555 000 00 01 / 123456` (müşteri), `+90 555 000 00 02 / 123456` (salon sahibi), `+90 555 000 00 03 / 123456` (personel)
ekleyin. Bu numaralara gerçek SMS gitmez; kod sabittir. Auth Emulator kullanıyorsanız kod emülatör konsoluna yazılır.

## Randevu linki, App Link ve web sayfası

- Paylaşılan link: `https://<appLinkHost>/s/<salonKodu>`. Uygulama yüklüyse doğrudan salon sayfası açılır (giriş yoksa
  önce giriş, ardından otomatik olarak salon sayfası). Yüklü değilse Firebase Hosting → `salonLanding` fonksiyonu salon
  bilgilerini, "Uygulamada Aç" ve Google Play bağlantısını içeren bir sayfa döndürür.
- App Link doğrulaması için `hosting/public/.well-known/assetlinks.json` içindeki parmak izini **Play App Signing SHA-256**
  ile değiştirip `firebase deploy --only hosting,functions` çalıştırın. Debug paketi (`com.kuaforumcepte.debug`) için
  ikinci bir giriş ekleyebilir veya şu komutla deneyebilirsiniz:
  `adb shell am start -a android.intent.action.VIEW -d "kuaforumcepte://salon/<kod>" com.kuaforumcepte.debug`
- Play Store adresi farklıysa fonksiyon ortam değişkeni `PLAY_STORE_URL` ile verin.

## Lisanslar

`app/src/main/assets/tr_locations.json` (81 il / 973 ilçe) `turkey-neighbourhoods` paketinden (MIT, PTT verisi) türetilmiştir; ayrıntı `assets/THIRD_PARTY_NOTICES.txt`.

## Oturum politikası ("Beni hatırla")

Firebase Auth oturumu cihazdaki şifreli depoda tutulur; uygulama ayrıca token saklamaz.
*Beni hatırla* açıksa (varsayılan) oturum uygulama yeniden açıldığında sürer.
Kapalıysa uygulama her soğuk başlatmada oturumu kapatır ve rol seçimine döner.

## Geliştirme aşamaları

| # | Aşama | Durum |
|---|---|---|
| 0 | Mimari, veri modeli, kurallar, indeksler, API sözleşmeleri, domain çekirdeği | ✅ |
| 1 | Gradle, tema, navigasyon, DI, Firebase şablonu, splash, rol seçimi | ✅ |
| 2 | Telefon OTP, müşteri/salon sahibi kaydı, personel daveti, rol sistemi | ✅ |
| 3 | Salon profili, logo/galeri, il/ilçe, salon kodu, paylaşım/QR | ✅ |
| 4 | Çalışma takvimi, izinler, kapamalar, hizmetler | ✅ |
| 5 | Salon listesi, detay, uzman/hizmet seçimi, müsaitlik, rezervasyon | ⏳ |
| 6 | Randevu yönetimi, bildirimler, zamanlanmış hatırlatmalar | ⏳ |
| 7 | Finansal ledger ve raporlar | ⏳ |
| 8 | Geçmiş, puanlama, salon bilgileri, takvim ve harita | ⏳ |
| 9 | Güvenlik sertleştirme, testler, release ve yayın kontrol listesi | ⏳ |
