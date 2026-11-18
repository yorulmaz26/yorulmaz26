import 'dart:typed_data';

import 'package:cryptography/cryptography.dart';

import 'attachment.dart';

/// Ek dosya şifreleme: dosya başına rastgele 256 bit anahtar, AES-GCM.
/// Çıktı = nonce(12) ‖ şifreli metin ‖ etiket(16).
abstract final class AttachmentCrypto {
  static final _aes = AesGcm.with256bits();

  static Future<({Uint8List ciphertext, Uint8List key, Uint8List sha256})>
  encrypt(Uint8List plain) async {
    final secret = await _aes.newSecretKey();
    final box = await _aes.encrypt(plain, secretKey: secret);
    final ct = box.concatenation();
    final digest = await Sha256().hash(ct);
    return (
      ciphertext: ct,
      key: Uint8List.fromList(await secret.extractBytes()),
      sha256: Uint8List.fromList(digest.bytes),
    );
  }

  /// Önce şifreli dosyanın özetini doğrular, sonra çözer (GCM etiketi de
  /// ayrıca doğrulanır). Uyuşmazlıkta [AttachmentIntegrityException].
  static Future<Uint8List> decrypt(
    Uint8List ciphertext, {
    required List<int> key,
    required List<int> sha256,
  }) async {
    final digest = (await Sha256().hash(ciphertext)).bytes;
    var diff = digest.length ^ sha256.length;
    for (var i = 0; i < digest.length && i < sha256.length; i++) {
      diff |= digest[i] ^ sha256[i];
    }
    if (diff != 0) throw const AttachmentIntegrityException();
    try {
      final box = SecretBox.fromConcatenation(
        ciphertext,
        nonceLength: 12,
        macLength: 16,
      );
      final clear = await _aes.decrypt(box, secretKey: SecretKey(key));
      return Uint8List.fromList(clear);
    } on SecretBoxAuthenticationError {
      throw const AttachmentIntegrityException();
    }
  }
}
