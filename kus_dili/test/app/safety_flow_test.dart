import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:kus_dili/features/auth/domain/auth_repository.dart';
import 'package:kus_dili/features/chat/data/firestore_chat_repository.dart';
import 'package:kus_dili/features/chat/domain/message_payload.dart';
import 'package:material_ui/material_ui.dart';

import '../helpers/fake_auth_repository.dart';
import '../helpers/signal_client.dart';
import '../helpers/test_app.dart';

/// Engelleme ve şikâyet (mağaza kuralı: kullanıcı içeriği olan uygulamalar).
void main() {
  const a = 'uidAyse';
  const b = 'uidBora';
  const direct = 'd_${a}_$b';
  const group = 'g_kuslar12345';

  Future<FakeFirebaseFirestore> seeded() async {
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
    return db;
  }

  Future<void> boraSays(
    FakeFirebaseFirestore db,
    SignalClient bora,
    String chatId,
    String id,
    String text,
  ) async => FirestoreChatRepository(db).sendMessage(
    chatId: chatId,
    messageId: id,
    senderId: b,
    encVersion: 1,
    body: await bora.sealer.seal(chatId, MessagePayload(text: text)),
  );

  final ayse = FakeAuthRepository(
    signedIn: const AuthUser(uid: a, phoneNumber: '+905321234567'),
  );

  testWidgets('Birebir: şikâyet et (mesajlarla) + engelle, engeli kaldır', (
    t,
  ) async {
    tallScreen(t);
    final db = await seeded();
    final bora = SignalClient(b, db);
    await bora.keys.ensureKeys();
    await db.doc('chats/$direct').set({
      'type': 'direct',
      'members': [a, b],
      'createdAt': DateTime(2026),
      'lastActivityAt': DateTime(2026),
      'readAt': <String, Object>{},
    });
    await t.pumpWidget(testApp(auth: ayse, db: db));
    await settle(t);
    await boraSays(db, bora, direct, 'm1', 'Ucuz takipçi satın al');
    await settle(t);

    await t.tap(find.text('Bora'));
    await settle(t);
    expect(find.text('Ucuz takipçi satın al'), findsOneWidget);

    await t.tap(find.byKey(const Key('chatMenu')));
    await settle(t);
    await t.tap(find.byKey(const Key('menuReport')));
    await settle(t);
    expect(find.text('Son 1 mesajı ekle'), findsOneWidget);
    await t.tap(find.byKey(const Key('reason-spam')));
    await t.enterText(find.byKey(const Key('reportNote')), 'reklam');
    await t.pump();
    await t.tap(find.byKey(const Key('submitReport')));
    await settle(t);
    expect(find.textContaining('Şikâyetin alındı'), findsOneWidget);

    final reports = await db.collection('reports').get();
    final r = reports.docs.single.data();
    expect(r['reporter'], a);
    expect(r['reported'], b);
    expect(r['chatId'], direct);
    expect(r['reason'], 'spam');
    expect(r['note'], 'reklam');
    final msgs = r['messages'] as List;
    expect(msgs.single['text'], 'Ucuz takipçi satın al');
    expect(msgs.single['senderId'], b);

    // "Ayrıca engelle" varsayılan açık.
    final blocks = await db.doc('blocks/$a').get();
    expect(blocks['uids'], [b]);
    expect(find.byKey(const Key('blockedBar')), findsOneWidget);
    expect(find.byKey(const Key('messageInput')), findsNothing);

    await t.tap(find.byKey(const Key('unblockBar')));
    await settle(t);
    expect((await db.doc('blocks/$a').get())['uids'], isEmpty);
    expect(find.byKey(const Key('messageInput')), findsOneWidget);

    // Menüden yeniden engelle (onaylı).
    await t.tap(find.byKey(const Key('chatMenu')));
    await settle(t);
    await t.tap(find.byKey(const Key('menuBlock')));
    await settle(t);
    await t.tap(find.byKey(const Key('confirmBlock')));
    await settle(t);
    expect((await db.doc('blocks/$a').get())['uids'], [b]);

    // Profil → Engellenen kişiler listesinden kaldır.
    await t.pageBack();
    await settle(t);
    await t.tap(find.byKey(const Key('openProfile')));
    await settle(t);
    await t.tap(find.byKey(const Key('openBlocked')));
    await settle(t);
    expect(find.text('@bora'), findsOneWidget);
    await t.tap(find.byKey(const Key('unblock-$b')));
    await settle(t);
    expect(find.text('Kimseyi engellemedin.'), findsOneWidget);
  });

  testWidgets('Şikâyet mesajsız da gönderilebilir', (t) async {
    tallScreen(t);
    final db = await seeded();
    final bora = SignalClient(b, db);
    await bora.keys.ensureKeys();
    await db.doc('chats/$direct').set({
      'type': 'direct',
      'members': [a, b],
      'createdAt': DateTime(2026),
      'lastActivityAt': DateTime(2026),
      'readAt': <String, Object>{},
    });
    await t.pumpWidget(testApp(auth: ayse, db: db));
    await settle(t);
    await boraSays(db, bora, direct, 'm1', 'Gizli kalsın');
    await settle(t);
    await t.tap(find.text('Bora'));
    await settle(t);
    await t.longPress(find.text('Gizli kalsın'));
    await settle(t);
    await t.tap(find.byKey(const Key('reportMessage')));
    await settle(t);
    await t.tap(find.byKey(const Key('reason-harassment')));
    await t.tap(find.byKey(const Key('includeMessages')));
    await t.tap(find.byKey(const Key('alsoBlock')));
    await t.pump();
    await t.tap(find.byKey(const Key('submitReport')));
    await settle(t);
    final r = (await db.collection('reports').get()).docs.single.data();
    expect(r['messages'], isEmpty);
    expect(r['reason'], 'harassment');
    expect((await db.doc('blocks/$a').get()).exists, isFalse);
  });

  testWidgets('Grup: engellenen kişinin mesajları gizlenir', (t) async {
    tallScreen(t);
    final db = await seeded();
    final bora = SignalClient(b, db);
    await bora.keys.ensureKeys();
    await db.doc('chats/$group').set({
      'type': 'group',
      'name': 'Kuşlar',
      'members': [a, b],
      'admins': [b],
      'createdBy': b,
      'createdAt': DateTime(2026),
      'lastActivityAt': DateTime(2026),
      'readAt': <String, Object>{},
    });
    await t.pumpWidget(testApp(auth: ayse, db: db));
    await settle(t);
    await boraSays(db, bora, group, 'g1', 'Herkese selam');
    await settle(t);
    await t.tap(find.text('Kuşlar'));
    await settle(t);
    expect(find.text('Herkese selam'), findsOneWidget);

    await t.longPress(find.text('Herkese selam'));
    await settle(t);
    await t.tap(find.byKey(const Key('blockSender')));
    await settle(t);
    await t.tap(find.byKey(const Key('confirmBlock')));
    await settle(t);
    expect(find.text('Herkese selam'), findsNothing);
    expect(find.byKey(const Key('hiddenBlocked')), findsOneWidget);
    // Gruba yazmak serbest kalır.
    expect(find.byKey(const Key('messageInput')), findsOneWidget);

    // Grubu şikâyet et: yalnızca benden olmayan mesajlar eklenebilir.
    await t.tap(find.byKey(const Key('chatMenu')));
    await settle(t);
    await t.tap(find.byKey(const Key('menuReportGroup')));
    await settle(t);
    expect(find.byKey(const Key('alsoBlock')), findsNothing);
    await t.tap(find.byKey(const Key('reason-inappropriate')));
    await t.pump();
    await t.tap(find.byKey(const Key('submitReport')));
    await settle(t);
    final r = (await db.collection('reports').get()).docs.single.data();
    expect(r.containsKey('reported'), isFalse);
    expect((r['messages'] as List).single['text'], 'Herkese selam');
  });

  testWidgets('Kullanım koşulları oturum açmadan da okunur', (t) async {
    final db = await seeded();
    await t.pumpWidget(testApp(auth: FakeAuthRepository(), db: db));
    await settle(t);
    GoRouter.of(t.element(find.byType(Scaffold).first)).push('/terms');
    await settle(t);
    expect(find.byKey(const Key('terms')), findsOneWidget);
    expect(find.textContaining('hiçbir tolerans'), findsOneWidget);
  });
}
