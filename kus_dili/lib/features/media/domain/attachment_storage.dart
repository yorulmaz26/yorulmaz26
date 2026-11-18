import 'dart:typed_data';

/// Şifreli ek dosyaların saklandığı yer (Firebase Storage).
abstract interface class AttachmentStorage {
  Future<void> put(
    String path,
    Uint8List ciphertext, {
    required String uploader,
  });

  Future<Uint8List> get(String path, {required int maxBytes});

  Future<void> delete(String path);
}
