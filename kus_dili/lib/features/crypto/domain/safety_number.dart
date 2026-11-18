import 'dart:convert';
import 'dart:typed_data';

import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart';

/// İki kullanıcının karşılıklı karşılaştırabileceği 60 haneli güvenlik
/// numarası (Signal'in sayısal parmak izi). Her iki tarafta aynı çıkıyorsa
/// araya kimse girmemiştir.
///
/// Çoklu cihaz: her tarafın TÜM cihazlarının kimlik anahtarları hesaba
/// katılır (sıra önemsiz). Taraflardan biri cihaz ekler ya da kaldırırsa
/// numara değişir.
String safetyNumber({
  required String myUid,
  required List<List<int>> myIdentityKeys,
  required String otherUid,
  required List<List<int>> otherIdentityKeys,
}) {
  if (myIdentityKeys.isEmpty || otherIdentityKeys.isEmpty) {
    throw ArgumentError('Kimlik anahtarı yok.');
  }
  IdentityKey k(List<int> b) => IdentityKey.fromBytes(Uint8List.fromList(b), 0);
  final fp = NumericFingerprintGenerator(5200).createListFor(
    2,
    Uint8List.fromList(utf8.encode(myUid)),
    [for (final b in myIdentityKeys) k(b)],
    Uint8List.fromList(utf8.encode(otherUid)),
    [for (final b in otherIdentityKeys) k(b)],
  );
  final digits = fp.displayableFingerprint.getDisplayText();
  return [for (var i = 0; i < digits.length; i += 5) digits.substring(i, i + 5)]
      .join(' ');
}
