import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/auth/domain/auth_repository.dart';
import 'package:kus_dili/features/chat/data/firestore_chat_repository.dart';
import 'package:kus_dili/features/chat/domain/message_payload.dart';
import 'package:kus_dili/features/crypto/domain/signal_sealer.dart';
import 'package:kus_dili_engine/kus_dili_engine.dart';
import 'package:material_ui/material_ui.dart';

import '../helpers/fake_auth_repository.dart';
import '../helpers/in_memory.dart';
import '../helpers/signal_client.dart';
import '../helpers/test_app.dart';

void main() {
  const a = 'uidAyse';
  const b = 'uidBora';
  const c = 'uidCem';

  testWidgets('Grup: kur, şifreli yazış, yönet, ayrıl', (t) async {
    tallScreen(t);
    final db = FakeFirebaseFirestore();
    for (final (uid, name, display) in [
      (a, 'ayse', 'Ayşe'),
      (b, 'bora', 'Bora'),
      (c, 'cem', 'Cem'),
    ]) {
      await db.doc('users/$uid').set({
        'username': name,
        'displayName': display,
        'createdAt': DateTime(2026),
      });
      await db.doc('usernames/$name').set({'uid': uid});
    }
    final bora = SignalClient(b, db);
    final cem = SignalClient(c, db);
    await bora.keys.ensureKeys();
    await cem.keys.ensureKeys();
    final kv = InMemorySecureKv();

    await t.pumpWidget(
      testApp(
        auth: FakeAuthRepository(signedIn: const AuthUser(uid: a)),
        db: db,
        kv: kv,
      ),
    );
    await settle(t);

    // Grup kur.
    await t.tap(find.byKey(const Key('newChat')));
    await settle(t);
    await t.tap(find.byKey(const Key('newGroupEntry')));
    await settle(t);
    await t.enterText(find.byKey(const Key('groupName')), 'Kuşlar');
    for (final u in ['bora', 'cem']) {
      await t.enterText(find.byKey(const Key('memberSearch')), u);
      await t.tap(find.byKey(const Key('memberSearchGo')));
      await settle(t);
    }
    expect(find.text('Bora'), findsOneWidget);
    expect(find.text('Cem'), findsOneWidget);
    await t.tap(find.byKey(const Key('createGroup')));
    await settle(t);
    expect(find.text('Kuşlar'), findsOneWidget);
    expect(find.textContaining('3 üye'), findsOneWidget);
    expect(
      find.textContaining('Grup mesajları uçtan uca şifreli'),
      findsOneWidget,
    );

    final groupId = (await db.collection('chats').get()).docs.single.id;
    expect(groupId, startsWith('g_'));

    // Şifreli grup mesajı.
    await t.enterText(find.byKey(const Key('messageInput')), 'Selam grup');
    await t.pump();
    await t.tap(find.byKey(const Key('send')));
    await settle(t);
    expect(find.text('Segelagam grugup'), findsOneWidget);
    final sent =
        (await db.collection('chats/$groupId/messages').get()).docs.single;
    expect(sent['body'] as String, isNot(contains('Selam')));
    final shares =
        (await db.collection('chats/$groupId/senderKeys').get()).docs;
    expect((shares.single['shares'] as Map).keys.toSet(), {b, c});
    expect(
      (await bora.sealer.open(groupId, a, 1, sent['body'] as String)).text,
      'Selam grup',
    );
    expect(
      (await cem.sealer.open(groupId, a, 1, sent['body'] as String)).text,
      'Selam grup',
    );

    // Bora yazar → gönderen adıyla görünür.
    await FirestoreChatRepository(db).sendMessage(
      chatId: groupId,
      messageId: 'fromBora',
      senderId: b,
      encVersion: 1,
      body: await bora.sealer.seal(
        groupId,
        MessagePayload(text: 'Merhaba', mode: DisplayMode.kusDili),
      ),
    );
    await settle(t);
    expect(
      find.text(KusDiliCodec(KusDiliVariant.g).encode('Merhaba')),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('sender-fromBora')), findsOneWidget);
    expect(
      t.widget<Text>(find.byKey(const ValueKey('sender-fromBora'))).data,
      'Bora',
    );

    // Grup bilgisi: yeniden adlandır, Cem'i çıkar.
    await t.tap(find.byKey(const Key('chatTitle')));
    await settle(t);
    await t.tap(find.byKey(const Key('renameGroup')));
    await settle(t);
    await t.enterText(find.byKey(const Key('renameField')), 'Kuş Sürüsü');
    await t.tap(find.byKey(const Key('renameSave')));
    await settle(t);
    expect(find.text('Kuş Sürüsü'), findsOneWidget);
    await t.tap(find.byKey(const ValueKey('memberMenu-$c')));
    await settle(t);
    await t.tap(find.text('Gruptan çıkar'));
    await settle(t);
    expect(find.byKey(const ValueKey('member-$c')), findsNothing);

    // Cem çıkarıldıktan sonraki mesajı okuyamaz.
    await t.pageBack();
    await settle(t);
    await t.enterText(find.byKey(const Key('messageInput')), 'Cem gitti');
    await t.pump();
    await t.tap(find.byKey(const Key('send')));
    await settle(t);
    final after = (await db.collection('chats/$groupId/messages').get()).docs
        .where((d) => d['senderId'] == a)
        .map((d) => d['body'] as String)
        .toList();
    final latest = after.firstWhere(
      (body) => !body.contains((sent['body'] as String).substring(0, 40)),
    );
    expect((await bora.sealer.open(groupId, a, 1, latest)).text, 'Cem gitti');
    await expectLater(
      cem.sealer.open(groupId, a, 1, latest),
      throwsA(isA<MessageUnavailableException>()),
    );

    // Ayrıl (son yönetici → Bora yönetici olur).
    await t.tap(find.byKey(const Key('chatTitle')));
    await settle(t);
    await t.tap(find.byKey(const Key('leaveGroup')));
    await settle(t);
    await t.tap(find.byKey(const Key('confirmLeave')));
    await settle(t);
    final g = await db.doc('chats/$groupId').get();
    expect(g['members'], [b]);
    expect(g['admins'], [b]);
    expect(find.text('Kuş Sürüsü'), findsNothing, reason: 'listeden çıktı');

    await t.pumpWidget(const SizedBox());
  });
}
