import 'dart:typed_data';

import 'package:firebase_storage/firebase_storage.dart';

import '../domain/attachment_storage.dart';

final class FirebaseAttachmentStorage implements AttachmentStorage {
  FirebaseAttachmentStorage(this._storage);

  final FirebaseStorage _storage;

  @override
  Future<void> put(
    String path,
    Uint8List ciphertext, {
    required String uploader,
  }) async {
    await _storage
        .ref(path)
        .putData(
          ciphertext,
          SettableMetadata(
            // Sunucu içeriğin türünü bilmesin: her şey opak ikili veri.
            contentType: 'application/octet-stream',
            customMetadata: {'uploader': uploader},
          ),
        );
  }

  @override
  Future<Uint8List> get(String path, {required int maxBytes}) async {
    final data = await _storage.ref(path).getData(maxBytes);
    if (data == null) throw StateError('Dosya bulunamadı.');
    return data;
  }

  @override
  Future<void> delete(String path) => _storage.ref(path).delete();
}
