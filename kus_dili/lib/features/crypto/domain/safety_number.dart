import 'dart:convert';
import 'dart:typed_data';

import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart';

/// İki kullanıcının karşılıklı karşılaştırabileceği 60 haneli güvenlik
/// numarası (Signal'in sayısal parmak izi). Her iki tarafta aynı çıkıyorsa
/// araya kimse girmemiştir.
String safetyNumber({
  required String myUid,
  required List<int> myIdentityKey,
  required String otherUid,
  required List<int> otherIdentityKey,
}) {
  final fp = NumericFingerprintGenerator(5200).createFor(
    1,
    Uint8List.fromList(utf8.encode(myUid)),
    IdentityKey.fromBytes(Uint8List.fromList(myIdentityKey), 0),
    Uint8List.fromList(utf8.encode(otherUid)),
    IdentityKey.fromBytes(Uint8List.fromList(otherIdentityKey), 0),
  );
  final digits = fp.displayableFingerprint.getDisplayText();
  return [for (var i = 0; i < digits.length; i += 5) digits.substring(i, i + 5)]
      .join(' ');
}
