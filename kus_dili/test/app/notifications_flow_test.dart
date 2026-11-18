import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/auth/domain/auth_repository.dart';
import 'package:kus_dili/features/chat/data/firestore_chat_repository.dart';
import 'package:material_ui/material_ui.dart';

import '../helpers/fake_auth_repository.dart';
import '../helpers/fake_push.dart';
import '../helpers/test_app.dart';

void main() {
  const a = 'uidAyse';
  const b = 'uidBora';
  const chatId = 'd_${a}_$b';

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
    }
    await FirestoreChatRepository(db).ensureDirectChat(me: a, other: b);
    return db;
  }

  testWidgets('Belirteç kaydı, sessize alma, bildirimle sohbet açma, çıkış', (
    t,
  ) async {
    tallScreen(t);
    final db = await seeded();
    final push = FakePushMessaging();
    final auth = FakeAuthRepository(signedIn: const AuthUser(uid: a));
    await t.pumpWidget(testApp(auth: auth, db: db, push: push));
    await settle(t);

    var doc = await db.doc('pushTokens/$a').get();
    expect(doc['tokens'], ['tok-1']);

    // Belirteç yenilenince eklenir.
    push.refresh.add('tok-2');
    await settle(t);
    doc = await db.doc('pushTokens/$a').get();
    expect(doc['tokens'], ['tok-1', 'tok-2']);

    // Bildirime dokunuldu → sohbet açılır.
    push.opened.add(chatId);
    await settle(t);
    expect(find.byKey(const Key('messageInput')), findsOneWidget);

    // Sohbeti sessize al.
    await t.tap(find.byKey(const Key('chatSettings')));
    await settle(t);
    await t.tap(find.byKey(const Key('muteChatSwitch')));
    await settle(t);
    doc = await db.doc('pushTokens/$a').get();
    expect(doc['muted'], [chatId]);
    await t.tap(find.byKey(const Key('saveSettings')));
    await settle(t);
    await t.pageBack();
    await settle(t);

    // Bildirim ayarları: genel kapatma.
    await t.tap(find.byKey(const Key('openProfile')));
    await settle(t);
    await t.tap(find.byKey(const Key('openNotifications')));
    await settle(t);
    expect(find.textContaining('içeriği gösterilmez'), findsOneWidget);
    await t.tap(find.byKey(const Key('pushEnabled')));
    await settle(t);
    doc = await db.doc('pushTokens/$a').get();
    expect(doc['enabled'], isFalse);
    await t.pageBack();
    await settle(t);

    // Çıkış: bu cihazın belirteci silinir.
    push.currentToken = 'tok-2';
    await t.tap(find.byKey(const Key('signOut')));
    await settle(t);
    doc = await db.doc('pushTokens/$a').get();
    expect(doc['tokens'], ['tok-1']);
    expect(push.deleted, isTrue);
    expect(find.text('Telefon numaranı gir'), findsOneWidget);

    await t.pumpWidget(const SizedBox());
  });

  testWidgets('İzin verilmezse belirteç kaydedilmez, uygulama çalışır', (
    t,
  ) async {
    final db = await seeded();
    await t.pumpWidget(
      testApp(
        auth: FakeAuthRepository(signedIn: const AuthUser(uid: a)),
        db: db,
        push: FakePushMessaging(permission: false),
      ),
    );
    await settle(t);
    expect((await db.doc('pushTokens/$a').get()).exists, isFalse);
    expect(find.text('Bora'), findsOneWidget);
    await t.pumpWidget(const SizedBox());
  });

  testWidgets(
    'Uygulama bildirimle açıldıysa oturum hazır olunca sohbet açılır',
    (t) async {
      final db = await seeded();
      final push = FakePushMessaging()..initial = chatId;
      await t.pumpWidget(
        testApp(
          auth: FakeAuthRepository(signedIn: const AuthUser(uid: a)),
          db: db,
          push: push,
        ),
      );
      await settle(t, 15);
      expect(find.byKey(const Key('messageInput')), findsOneWidget);
      await t.pumpWidget(const SizedBox());
    },
  );
}
