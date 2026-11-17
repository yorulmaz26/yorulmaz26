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

Mesajlaşma, giriş ve şifreleme henüz yok (Aşama 3+).

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
