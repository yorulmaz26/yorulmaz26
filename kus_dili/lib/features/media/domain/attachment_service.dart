import 'dart:math';
import 'dart:typed_data';

import 'attachment.dart';
import 'attachment_crypto.dart';
import 'attachment_storage.dart';

/// Ek dosyayı cihazda şifreleyip yükler; indirip doğrulayarak çözer.
final class AttachmentService {
  AttachmentService(this._storage, {Random? random})
    : _rnd = random ?? Random.secure();

  final AttachmentStorage _storage;
  final Random _rnd;

  String _newId() => List.generate(
    16,
    (_) => _rnd.nextInt(256).toRadixString(16).padLeft(2, '0'),
  ).join();

  Future<Attachment> upload({
    required String chatId,
    required String uploader,
    required PickedMedia media,
  }) async {
    if (media.bytes.length > media.kind.maxBytes) {
      throw AttachmentTooLargeException(media.kind);
    }
    final enc = await AttachmentCrypto.encrypt(media.bytes);
    final path = 'attachments/$chatId/${_newId()}';
    await _storage.put(path, enc.ciphertext, uploader: uploader);
    return Attachment(
      kind: media.kind,
      path: path,
      key: enc.key,
      sha256: enc.sha256,
      size: media.bytes.length,
      mime: media.mime,
      name: media.name,
      durationMs: media.durationMs,
    );
  }

  Future<Uint8List> download(Attachment a) async {
    // GCM: 12 bayt nonce + 16 bayt etiket.
    final ct = await _storage.get(a.path, maxBytes: a.size + 28);
    return AttachmentCrypto.decrypt(ct, key: a.key, sha256: a.sha256);
  }

  Future<void> delete(Attachment a) => _storage.delete(a.path);
}
