import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/chat/domain/message_payload.dart';
import 'package:kus_dili/features/media/domain/attachment.dart';
import 'package:kus_dili/features/media/domain/attachment_crypto.dart';
import 'package:kus_dili/features/media/domain/attachment_service.dart';

import '../../helpers/fake_media.dart';

void main() {
  final plain = Uint8List.fromList(utf8.encode('GIZLI-FOTO-' * 100));

  group('AttachmentCrypto', () {
    test('şifrele → çöz; şifreli veride açık içerik yok', () async {
      final e = await AttachmentCrypto.encrypt(plain);
      expect(e.key.length, 32);
      expect(e.sha256.length, 32);
      expect(
        utf8.decode(e.ciphertext, allowMalformed: true),
        isNot(contains('GIZLI')),
      );
      final d = await AttachmentCrypto.decrypt(
        e.ciphertext,
        key: e.key,
        sha256: e.sha256,
      );
      expect(d, plain);
    });

    test('her dosya için farklı anahtar', () async {
      final a = await AttachmentCrypto.encrypt(plain);
      final b = await AttachmentCrypto.encrypt(plain);
      expect(a.key, isNot(b.key));
      expect(a.ciphertext, isNot(b.ciphertext));
    });

    test('değiştirilmiş dosya ve yanlış anahtar reddedilir', () async {
      final e = await AttachmentCrypto.encrypt(plain);
      final tampered = Uint8List.fromList(e.ciphertext)..[20] ^= 1;
      await expectLater(
        AttachmentCrypto.decrypt(tampered, key: e.key, sha256: e.sha256),
        throwsA(isA<AttachmentIntegrityException>()),
      );
      final wrongKey = Uint8List.fromList(e.key)..[0] ^= 1;
      await expectLater(
        AttachmentCrypto.decrypt(e.ciphertext, key: wrongKey, sha256: e.sha256),
        throwsA(isA<AttachmentIntegrityException>()),
      );
    });
  });

  group('Attachment ve paket', () {
    test('JSON gidiş-dönüş', () async {
      final a = Attachment(
        kind: AttachmentKind.audio,
        path: 'attachments/d_a_b/00ff',
        key: Uint8List(32),
        sha256: Uint8List(32)..[0] = 7,
        size: 1234,
        mime: 'audio/mp4',
        name: 'ses.m4a',
        durationMs: 3200,
      );
      final back = Attachment.fromJson(
        jsonDecode(jsonEncode(a.toJson())) as Map<String, Object?>,
      );
      expect(back, a);
      expect(back.durationMs, 3200);
      expect(back.kind, AttachmentKind.audio);
    });

    test('ekli mesajda açıklama boş olabilir; özet', () {
      final a = Attachment(
        kind: AttachmentKind.image,
        path: 'p',
        key: Uint8List(32),
        sha256: Uint8List(32),
        size: 1,
        mime: 'image/png',
      );
      final p = MessagePayload(text: '', attachment: a);
      final back = MessagePayload.decode(p.encode());
      expect(back.attachment, a);
      expect(back.hasText, isFalse);
      expect(back.summary, '📷 Fotoğraf');
      expect(
        () => MessagePayload(text: ''),
        throwsA(isA<PayloadFormatException>()),
      );
      expect(
        () => MessagePayload.decode('{"v":1,"text":"","a":{"k":"img"}}'),
        throwsA(isA<PayloadFormatException>()),
      );
    });

    test('biçimlendirme', () {
      expect(Attachment.formatSize(500), '500 B');
      expect(Attachment.formatSize(1536 * 1024), '1,5 MB');
      expect(Attachment.formatDuration(3200), '0:03');
      expect(Attachment.formatDuration(83000), '1:23');
    });
  });

  group('AttachmentService', () {
    test('yükle: depoda yalnızca şifreli veri; indir: aynı içerik', () async {
      final storage = InMemoryAttachmentStorage();
      final svc = AttachmentService(storage);
      final att = await svc.upload(
        chatId: 'd_a_b',
        uploader: 'a',
        media: PickedMedia(
          kind: AttachmentKind.file,
          bytes: plain,
          mime: 'text/plain',
          name: 'not.txt',
        ),
      );
      expect(att.path, matches(RegExp(r'^attachments/d_a_b/[0-9a-f]{32}$')));
      final blob = storage.blobs[att.path]!;
      expect(utf8.decode(blob, allowMalformed: true), isNot(contains('GIZLI')));
      expect(storage.uploaders[att.path], 'a');
      expect(await svc.download(att), plain);
      await svc.delete(att);
      expect(storage.blobs, isEmpty);
    });

    test('boyut sınırı', () async {
      final svc = AttachmentService(InMemoryAttachmentStorage());
      await expectLater(
        svc.upload(
          chatId: 'c',
          uploader: 'a',
          media: PickedMedia(
            kind: AttachmentKind.image,
            bytes: Uint8List(AttachmentKind.image.maxBytes + 1),
            mime: 'image/jpeg',
          ),
        ),
        throwsA(isA<AttachmentTooLargeException>()),
      );
    });
  });
}
