import 'dart:convert';
import 'dart:typed_data';

import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/auth/domain/auth_repository.dart';
import 'package:kus_dili/features/chat/data/firestore_chat_repository.dart';
import 'package:kus_dili/features/chat/domain/message_payload.dart';
import 'package:kus_dili/features/media/domain/attachment.dart';
import 'package:kus_dili/features/media/domain/attachment_service.dart';
import 'package:material_ui/material_ui.dart';

import '../helpers/fake_auth_repository.dart';
import '../helpers/fake_media.dart';
import '../helpers/in_memory.dart';
import '../helpers/signal_client.dart';
import '../helpers/test_app.dart';

void main() {
  const a = 'uidAyse';
  const b = 'uidBora';
  const chatId = 'd_${a}_$b';

  testWidgets('Fotoğraf, sesli mesaj ve belge: şifreli gönder/al/sil', (
    t,
  ) async {
    tallScreen(t);
    final db = FakeFirebaseFirestore();
    for (final (uid, name, display) in [
      (a, 'ayse', 'Ayşe'),
      (b, 'bora', 'Bora'),
    ]) {
      await db.doc('users/$uid').set({
        'username': name,
        'displayName': display,
        'createdAt': DateTime(2026),
      });
      await db.doc('usernames/$name').set({'uid': uid});
    }
    await FirestoreChatRepository(db).ensureDirectChat(me: a, other: b);
    final kv = InMemorySecureKv();
    await SignalClient(a, db, kv: kv).keys.ensureKeys();
    final bora = SignalClient(b, db);
    await bora.keys.ensureKeys();
    final storage = InMemoryAttachmentStorage();
    final svc = AttachmentService(storage);

    await t.pumpWidget(
      testApp(
        auth: FakeAuthRepository(signedIn: const AuthUser(uid: a)),
        db: db,
        kv: kv,
        storage: storage,
      ),
    );
    await settle(t);
    await t.tap(find.text('Bora'));
    await settle(t);

    // Fotoğraf + açıklama (açıklama kuş diline çevrilir).
    await t.enterText(find.byKey(const Key('messageInput')), 'Bak');
    await t.pump();
    await t.tap(find.byKey(const Key('attach')));
    await settle(t);
    await t.tap(find.byKey(const Key('pick-gallery')));
    await settle(t, 20);
    expect(find.byKey(const Key('attImage')), findsOneWidget);
    expect(find.text('Bagak'), findsOneWidget);
    expect(storage.blobs, hasLength(1));
    final blob = storage.blobs.values.single;
    expect(blob, isNot(equals(tinyPng)), reason: 'depoda şifreli');

    // Bora kendi cihazında açar: ekin anahtarı Signal ile şifreli pakette.
    final doc1 =
        (await db.collection('chats/$chatId/messages').get()).docs.single;
    expect(
      doc1['body'] as String,
      isNot(contains('attachments/')),
      reason: 'dosya yolu bile sunucuda açık değil',
    );
    final p1 = await bora.sealer.open(chatId, a, 1, doc1['body'] as String);
    expect(p1.text, 'Bak');
    expect(p1.attachment!.kind, AttachmentKind.image);
    expect(await svc.download(p1.attachment!), tinyPng);

    // Sesli mesaj: metin kutusu boşken mikrofon görünür.
    expect(find.byKey(const Key('recordVoice')), findsOneWidget);
    await t.tap(find.byKey(const Key('recordVoice')));
    await settle(t);
    expect(find.byKey(const Key('voiceTimer')), findsOneWidget);
    await t.tap(find.byKey(const Key('stopVoice')));
    await settle(t, 20);
    expect(find.byKey(const Key('voicePlay')), findsOneWidget);
    expect(find.text('0:03'), findsOneWidget);
    expect(storage.blobs, hasLength(2));

    // Bora bir belge gönderir.
    final doc = await svc.upload(
      chatId: chatId,
      uploader: b,
      media: PickedMedia(
        kind: AttachmentKind.file,
        bytes: Uint8List.fromList(utf8.encode('rapor')),
        mime: 'application/pdf',
        name: 'rapor.pdf',
      ),
    );
    await FirestoreChatRepository(db).sendMessage(
      chatId: chatId,
      messageId: 'boraDoc',
      senderId: b,
      encVersion: 1,
      body: await bora.sealer.seal(
        chatId,
        MessagePayload(text: '', attachment: doc),
      ),
    );
    await settle(t);
    expect(find.byKey(const Key('att-file')), findsOneWidget);
    expect(find.text('rapor.pdf'), findsOneWidget);

    // Kendi fotoğrafını herkesten sil → depodan da silinir.
    await t.longPress(find.text('Bagak'));
    await settle(t);
    await t.tap(find.byKey(const Key('deleteMessage')));
    await settle(t);
    expect(find.text('Bu mesaj silindi'), findsOneWidget);
    expect(storage.blobs.containsKey(p1.attachment!.path), isFalse);

    await t.pumpWidget(const SizedBox());
  });

  testWidgets('Mikrofon izni yoksa uyarı', (t) async {
    tallScreen(t);
    final db = FakeFirebaseFirestore();
    for (final (uid, name) in [(a, 'ayse'), (b, 'bora')]) {
      await db.doc('users/$uid').set({
        'username': name,
        'displayName': name,
        'createdAt': DateTime(2026),
      });
    }
    await FirestoreChatRepository(db).ensureDirectChat(me: a, other: b);
    final rec = FakeVoiceRecorder()..permission = false;
    await t.pumpWidget(
      testApp(
        auth: FakeAuthRepository(signedIn: const AuthUser(uid: a)),
        db: db,
        recorder: rec,
      ),
    );
    await settle(t);
    await t.tap(find.text('bora'));
    await settle(t);
    await t.tap(find.byKey(const Key('recordVoice')));
    await settle(t);
    expect(find.textContaining('mikrofon izni'), findsOneWidget);
    expect(rec.recording, isFalse);
    await t.pumpWidget(const SizedBox());
  });
}
