/// Türkçe ↔ Kuş Dili dönüşüm motoru.
///
/// Kuş dili bir **gösterim/eğlence katmanıdır, şifreleme değildir**: kuralı
/// bilen herkes çözebilir. Mesaj gizliliği uygulamada uçtan uca şifreleme ile
/// ayrıca sağlanır (bkz. docs/ARCHITECTURE.md).
library;

export 'src/codec.dart';
export 'src/decode_result.dart';
export 'src/exceptions.dart';
export 'src/turkish_text.dart' show TurkishText;
export 'src/variant.dart';
