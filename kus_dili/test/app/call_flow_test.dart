import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/auth/domain/auth_repository.dart';
import 'package:kus_dili/features/chat/data/firestore_chat_repository.dart';
import 'package:material_ui/material_ui.dart';

import '../helpers/fake_auth_repository.dart';
import '../helpers/in_memory.dart';
import '../helpers/signal_client.dart';
import '../helpers/test_app.dart';

void main() {
  const a = 'uidAyse';
  const b = 'uidBora';
  const chatId = 'd_${a}_$b';

  Future<(FakeFirebaseFirestore, InMemorySecureKv)> setup() async {
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
    final kv = InMemorySecureKv();
    await SignalClient(a, db, kv: kv).keys.ensureKeys();
    await SignalClient(b, db).keys.ensureKeys();
    return (db, kv);
  }

  testWidgets('Sohbetten sesli arama başlatma ve kapatma', (t) async {
    tallScreen(t);
    final (db, kv) = await setup();
    await t.pumpWidget(
      testApp(
        auth: FakeAuthRepository(signedIn: const AuthUser(uid: a)),
        db: db,
        kv: kv,
      ),
    );
    await settle(t);
    await t.tap(find.text('Bora'));
    await settle(t);
    await t.tap(find.byKey(const Key('voiceCall')));
    await settle(t);
    expect(find.text('Aranıyor…'), findsOneWidget);
    expect(find.text('Uçtan uca şifreli'), findsOneWidget);
    final call = (await db.collection('calls').get()).docs.single;
    expect(call['callee'], b);
    expect(call['video'], isFalse);

    await t.tap(find.byKey(const Key('callHangUp')));
    await settle(t, 25);
    expect((await db.doc('calls/${call.id}').get())['state'], 'missed');
    expect(
      find.byKey(const Key('callStatus')),
      findsNothing,
      reason: 'ekran kapandı',
    );
    await t.pumpWidget(const SizedBox());
  });

  testWidgets('Gelen görüntülü arama: ekran çıkar, kabul edilir', (t) async {
    tallScreen(t);
    final (db, kv) = await setup();
    await t.pumpWidget(
      testApp(
        auth: FakeAuthRepository(signedIn: const AuthUser(uid: a)),
        db: db,
        kv: kv,
      ),
    );
    await settle(t);
    await db.doc('calls/c1').set({
      'chatId': chatId,
      'caller': b,
      'callee': a,
      'video': true,
      'state': 'ringing',
      'createdAt': DateTime.now(),
    });
    await settle(t);
    expect(find.text('Görüntülü arama'), findsOneWidget);
    expect(find.text('Bora'), findsWidgets);
    await t.tap(find.byKey(const Key('acceptCall')));
    await settle(t);
    expect((await db.doc('calls/c1').get())['state'], 'accepted');
    expect(find.text('Bağlanıyor…'), findsOneWidget);
    expect(find.byKey(const Key('remoteVideo')), findsOneWidget);
    await t.tap(find.byKey(const Key('callHangUp')));
    await settle(t, 25);
    expect((await db.doc('calls/c1').get())['state'], 'ended');
    await t.pumpWidget(const SizedBox());
  });

  testWidgets('Gelen aramayı reddetme', (t) async {
    tallScreen(t);
    final (db, kv) = await setup();
    await t.pumpWidget(
      testApp(
        auth: FakeAuthRepository(signedIn: const AuthUser(uid: a)),
        db: db,
        kv: kv,
      ),
    );
    await settle(t);
    await db.doc('calls/c2').set({
      'chatId': chatId,
      'caller': b,
      'callee': a,
      'video': false,
      'state': 'ringing',
      'createdAt': DateTime.now(),
    });
    await settle(t);
    expect(find.text('Sesli arama'), findsOneWidget);
    await t.tap(find.byKey(const Key('declineCall')));
    await settle(t);
    expect((await db.doc('calls/c2').get())['state'], 'declined');
    expect(find.text('Sesli arama'), findsNothing);
    await t.pumpWidget(const SizedBox());
  });
}
