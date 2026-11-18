# Yayın kontrol listesi (Aşama 12)

Bu liste uygulamayı mağazalara göndermeden önce **sırayla** yapılmalıdır.
Kutucuklar işaretlenmeden yayın yapmayın.

## 0. Güvenlik — yayını engelleyen madde

- [ ] **Şifreleme kütüphanesi denetimi.** Uçtan uca şifreleme
      `libsignal_protocol_dart` (Signal'in resmî olmayan Dart portu) ile
      yapılıyor; bu port için bağımsız bir güvenlik denetimi bilgisi yok.
      Yayından önce biri yapılmalı:
      1. Signal'in resmî `libsignal` kütüphanesine Android (Java) ve iOS (Swift)
         bağlamalarıyla geçiş (`MessageSealer` arayüzü sayesinde yalnızca
         `lib/features/crypto` değişir), **veya**
      2. mevcut uygulamanın bağımsız bir güvenlik firmasınca incelenmesi.
      Bu yapılmadan mağaza açıklamasında "denetlenmiş", "kırılamaz" gibi
      ifadeler **kullanılmamalı**.
- [ ] Gerçek cihazlarda (en az bir Android, bir iPhone) iki hesapla: kayıt,
      mesaj, grup, ikinci cihaz bağlama ve kaldırma, engelleme ve şikâyet,
      bildirim, kilit, gizli sohbet, süreli/tek görüntülük mesaj elle denendi.

## 1. Firebase (bkz. FIREBASE_SETUP.md)

- [ ] Ayrı bir **üretim** projesi (geliştirme projesinden farklı).
- [ ] Telefonla giriş açık; SMS bölge politikası yalnızca hedef ülkeler.
- [ ] **App Check** Play Integrity / App Attest kayıtlı ve Firestore,
      Authentication, Functions için **zorunlu kılındı**.
- [ ] `firebase deploy --only firestore:rules,firestore:indexes,functions:kusdili`
- [ ] Firestore TTL (`messages.expireAt`).
- [ ] Bütçe uyarısı (Faturalandırma → Bütçeler).

## 2. Android

```bash
# Bir kez: yayın anahtarı oluştur (GÜVENLİ yerde sakla, kaybetme!)
keytool -genkey -v -keystore ~/kusdili-upload.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias upload
```

`kus_dili/android/key.properties` (depoya EKLENMEZ, `.gitignore`'da):

```
storeFile=/tam/yol/kusdili-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

- [ ] Derleme için **JDK 21** kullanın (Android Studio'nun JBR'si ya da
      Temurin 21). JDK 17 ile yayın derlemesinde Android lint aracı Firebase
      eklentisini incelerken çöker.
- [ ] `pubspec.yaml` içinde `version: 1.0.0+1` (her yüklemede `+N` artar).
- [ ] `flutter build appbundle --release` → `build/app/outputs/bundle/release/app-release.aab`
- [ ] Play Console → **Play App Signing** açık; uygulama imzalama anahtarının
      SHA-1/SHA-256 değerleri Firebase'e eklendi (yoksa giriş çalışmaz).
- [ ] Play Console "Veri güvenliği" formu (bkz. MAGAZA.md).
- [ ] Önce **dahili test** kanalı, sonra kapalı test.

Yayın derlemesi R8 ile küçültülür (`android/app/proguard-rules.pro`);
Android yedeklemesi şifreleme anahtarlarını içermesin diye kapalıdır.

## 3. iOS

- [ ] Xcode → Runner → Signing: ekip ve paket kimliği `com.kusdili.kusDili`.
- [ ] Capabilities: Push Notifications, Background Modes (Remote notifications).
- [ ] APNs anahtarı Firebase'de.
- [ ] `flutter build ipa --release` → App Store Connect'e yükle (Transporter).
- [ ] **İhracat uyumluluğu:** `Info.plist`'te `ITSAppUsesNonExemptEncryption = true`.
      Uygulama standart şifreleme (AES, Curve25519, TLS) kullanır; App Store
      Connect'te yıllık kendi sınıflandırma raporu / muafiyet sorularını
      doğru yanıtlayın (Fransa dağıtımı için ayrıca beyan gerekebilir).
- [ ] App Privacy etiketleri (bkz. MAGAZA.md).
- [ ] TestFlight ile iç test.

## 4. Hukuk ve içerik

- [ ] Gizlilik politikası (taslak: `GIZLILIK_POLITIKASI.md`) bir hukukçuya
      okutuldu, yayımlandı ve URL'si mağazalara girildi.
- [ ] KVKK aydınlatma metni ve VERBİS yükümlülüğü değerlendirildi.
- [ ] Destek e-postası / sayfası.
- [x] Engelleme ve şikâyet (sohbet menüsü; grupta mesaj menüsü). Kullanım
      koşulları uygulama içinde (`KULLANIM_KOSULLARI.md` ile aynı).
- [ ] **Moderasyon süreci:** `reports` koleksiyonunu kim, ne sıklıkla
      inceleyecek (koşullarda "24 saat" yazıyor); kuralı çiğneyen hesap
      Firebase Authentication'dan devre dışı bırakılır. App Store 1.2 bunu
      şart koşar.
- [x] Uygulama içinden hesap silme: Profil → "Hesabı kalıcı olarak sil"
      (`deleteAccount` Cloud Function). Mağaza formlarına bu yolu yazın.

## 5. Bilinen eksikler (yayın öncesi karar verin)

| Konu | Durum |
|---|---|
| Fotoğraf, video, belge, sesli mesaj, aramalar | Kapsam dışı (bilinçli olarak kaldırıldı) |
| Engelleme / şikâyet | ✅ Var (gruplarda engel istemcide) |
| Çoklu cihaz | ✅ Hesap başına 5 cihaz; geçmiş yeni cihaza aktarılmaz |
| Mesaj yedekleme / geçmiş aktarımı | Yok |
| Yeni cihaz için mevcut cihazdan onay (QR) | Yok — yalnızca uyarı ve kaldırma |
| İçerikli bildirim (cihazda çözme) | Yok |
| Uygulama simgesi ve açılış görseli | ✅ `assets/icon` (marka renkleri) |

## 6. Otomatik kontroller (CI)

`.github/workflows/kus-dili.yml`: motor ve uygulama testleri, Firestore
güvenlik kuralı testleri (emülatör), Cloud Functions testleri,
Android debug + küçültülmüş release APK derlemesi, imzasız iOS derlemesi.
Hepsi yeşil olmadan yayın yapmayın.
