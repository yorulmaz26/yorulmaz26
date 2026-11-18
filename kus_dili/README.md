# Kuş Dili

> Sadece anlayan anlasın.

Türkçe mesajları geleneksel kuş diline çeviren, uçtan uca şifreli mesajlaşma
uygulaması (Flutter, Android + iOS). Mimari ve güvenlik sınırları:
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Şu an çalışan

* **Dönüşüm motoru** (`packages/kus_dili_engine`): G'li / F'li / C'li ve özel
  kurallar, ters dönüşüm, varyant tespiti. Saf Dart, çevrimdışı.
* **Uygulama iskeleti**: Riverpod, GoRouter, marka teması, açılış ekranı ve
  motoru kullanan çevrimdışı **Kuş Dili Çevirici** ekranı.
* **Kayıt / giriş (Aşama 3)**: telefon numarası + SMS kodu (Firebase Auth),
  App Check, benzersiz kullanıcı adıyla profil oluşturma, oturuma göre
  yönlendirme, Firestore güvenlik kuralları. Kurulum:
  [`docs/FIREBASE_SETUP.md`](docs/FIREBASE_SETUP.md).

* **Mesajlaşma (Aşama 4–6)**: kullanıcı adıyla sohbet başlatma, gerçek zamanlı
  metin mesajlaşma, yanıtlama, düzenleme, herkesten silme, okundu bilgisi;
  sohbet başına otomatik kuş dili (G/F/C/özel kural), yazarken önizleme,
  dokununca animasyonlu çözme, görünüm tercihi.

* **Uçtan uca şifreleme (Aşama 7a)**: Signal Protokolü (X3DH + Double
  Ratchet), özel anahtarlar cihazın Keystore/Keychain'inde, sunucuda yalnızca
  şifreli metin, cihazda AES-GCM ile şifreli önbellek, güvenlik numarası.
* **Grup sohbeti (Aşama 8)**: en fazla 64 üye, Signal Sender Keys ile uçtan
  uca şifreli; üyelik değişince anahtar yenilenir; yönetici işlemleri, ayrılma.
* **Medya (Aşama 9)**: fotoğraf, video, belge ve sesli mesaj; dosyalar cihazda
  AES-256-GCM ile şifrelenir, Storage'a yalnızca şifreli hâli yüklenir.
* **Gizli sohbet (Aşama 7b)**: PIN (PBKDF2, deneme sınırı) ve biyometri ile
  kilitli sohbet, listeden gizlenen sohbetler, uygulama kilidi, süreli ve tek
  görüntülük mesajlar. Gizli/kilitli bilgisi yalnızca cihazda.

⚠️ Kullanılan Signal kütüphanesi (`libsignal_protocol_dart`) bağımsız olarak
denetlenmiş değildir. Yayından önce resmî libsignal'e geçiş veya bağımsız
güvenlik incelemesi gerekir (bkz. `docs/ARCHITECTURE.md` §4).

## Kurulum

Gereksinimler: Flutter 3.50+ (Dart 3.14+), Android Studio ve/veya Xcode.

```bash
cd kus_dili
flutter pub get
flutter run
```

## Testler

```bash
# Motor (Flutter gerekmez, yalnızca Dart SDK)
cd kus_dili/packages/kus_dili_engine
dart pub get
dart test

# Uygulama
cd kus_dili
flutter analyze
flutter test

# Firestore güvenlik kuralları (Java 11+, Node 20+)
cd kus_dili/firebase/rules-test
npm ci && npm test
```

## Motor kullanımı

```dart
import 'package:kus_dili_engine/kus_dili_engine.dart';

const codec = KusDiliCodec(KusDiliVariant.g);
codec.encode('Seni seviyorum');               // Segenigi segevigiyogorugum
codec.decode('Segenigi segevigiyogorugum');   // .text == 'Seni seviyorum'

final ozel = KusDiliVariant.custom(id: 'benim', name: 'Benim dilim', template: 'z{v}');
KusDiliCodec(ozel).encode('Merhaba');         // Mezerhazabaza

KusDiliCodec.detectVariant('Sefelafam');      // KusDiliVariant.f
```
