import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/auth/domain/auth_repository.dart';
import 'package:kus_dili/features/chat/data/firestore_chat_repository.dart';
import 'package:kus_dili/features/chat/domain/message_payload.dart';
import 'package:kus_dili_engine/kus_dili_engine.dart';
import 'package:material_ui/material_ui.dart';

import 'package:kus_dili/features/privacy/domain/local_privacy_prefs.dart';
import 'package:kus_dili/features/privacy/domain/pin_vault.dart';

import '../helpers/fake_auth_repository.dart';
import '../helpers/in_memory.dart';
import '../helpers/signal_client.dart';
import '../helpers/test_app.dart';

void main() {
  const a = 'uidAyse';
  const b = 'uidBora';
  const chatId = 'd_${a}_$b';

  Future<(FakeFirebaseFirestore, InMemorySecureKv, SignalClient)>
  setup() async {
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
    return (db, kv, bora);
  }

  Future<void> boraSends(
    FakeFirebaseFirestore db,
    SignalClient bora,
    String id,
    MessagePayload p, {
    DateTime? expireAt,
    bool viewOnce = false,
  }) async {
    await FirestoreChatRepository(db).sendMessage(
      chatId: chatId,
      messageId: id,
      senderId: b,
      encVersion: 1,
      body: await bora.sealer.seal(chatId, p),
      expireAt: expireAt,
      viewOnce: viewOnce,
    );
  }

  testWidgets('PIN, kilitli sohbet, gizli sohbet ve uygulama kilidi', (
    t,
  ) async {
    tallScreen(t);
    final (db, kv, _) = await setup();
    await t.pumpWidget(
      testApp(
        auth: FakeAuthRepository(signedIn: const AuthUser(uid: a)),
        db: db,
        kv: kv,
      ),
    );
    await settle(t);

    // PIN oluştur: Profil → Gizlilik.
    await t.tap(find.byKey(const Key('openProfile')));
    await settle(t);
    await t.tap(find.byKey(const Key('openPrivacy')));
    await settle(t);
    await t.tap(find.byKey(const Key('setPin')));
    await settle(t);
    await t.enterText(find.byKey(const Key('newPin')), '2468');
    await t.enterText(find.byKey(const Key('newPinAgain')), '1357');
    await t.tap(find.byKey(const Key('savePin')));
    await t.pump();
    expect(find.text('PIN\'ler eşleşmiyor.'), findsOneWidget);
    await t.enterText(find.byKey(const Key('newPinAgain')), '2468');
    await t.tap(find.byKey(const Key('savePin')));
    await settle(t);
    expect(kv.data.values.join(), isNot(contains('2468')));

    // Sohbete gir, kilitle ve gizle.
    await t.pageBack();
    await settle(t);
    await t.pageBack();
    await settle(t);
    await t.tap(find.text('Bora'));
    await settle(t);
    await t.tap(find.byKey(const Key('chatSettings')));
    await settle(t);
    await t.tap(find.byKey(const Key('lockChatSwitch')));
    await t.pump();
    await t.tap(find.byKey(const Key('hideChatSwitch')));
    await t.pump();
    await t.tap(find.byKey(const Key('saveSettings')));
    await settle(t);
    // Sunucuya gizlilik bilgisi yazılmadı.
    final settingsDoc = await db.doc('users/$a/chatSettings/$chatId').get();
    expect(settingsDoc.data()!.keys, isNot(contains('locked')));
    expect(settingsDoc.data()!.keys, isNot(contains('hidden')));

    // Bu oturumda açık; arka plana gidip dönünce kilitlenir.
    t.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    t.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    t.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    t.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await settle(t);
    expect(find.text('Bu sohbet kilitli'), findsOneWidget);
    await t.enterText(find.byKey(const Key('unlockPin')), '0000');
    await t.tap(find.byKey(const Key('unlockSubmit')));
    await settle(t);
    expect(find.byKey(const Key('unlockError')), findsOneWidget);
    await t.enterText(find.byKey(const Key('unlockPin')), '2468');
    await t.tap(find.byKey(const Key('unlockSubmit')));
    await settle(t);
    expect(find.byKey(const Key('messageInput')), findsOneWidget);

    // Ana listede görünmez; gizli sohbetler PIN ile açılır.
    await t.pageBack();
    await settle(t);
    expect(find.text('Bora'), findsNothing);
    expect(find.textContaining('Henüz sohbetin yok'), findsOneWidget);
    await t.tap(find.byKey(const Key('openProfile')));
    await settle(t);
    await t.tap(find.byKey(const Key('openPrivacy')));
    await settle(t);
    await t.tap(find.byKey(const Key('openHiddenChats')));
    await settle(t);
    expect(find.text('Gizli sohbetler'), findsOneWidget);
    await t.enterText(find.byKey(const Key('unlockPin')), '2468');
    await t.tap(find.byKey(const Key('unlockSubmit')));
    await settle(t);
    expect(find.text('Bora'), findsOneWidget);

    // Uygulama kilidi.
    await t.pageBack();
    await settle(t);
    await t.tap(find.byKey(const Key('appLockSwitch')));
    await settle(t);
    t.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    t.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    t.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    t.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await settle(t);
    expect(find.text('Kuş Dili kilitli'), findsOneWidget);
    await t.enterText(find.byKey(const Key('unlockPin')), '2468');
    await t.tap(find.byKey(const Key('unlockSubmit')));
    await settle(t);
    expect(find.text('Kuş Dili kilitli'), findsNothing);

    await t.pumpWidget(const SizedBox());
  });

  testWidgets('Süreli ve tek görüntülük mesajlar', (t) async {
    tallScreen(t);
    final (db, kv, bora) = await setup();
    final cache = InMemoryPayloadCache();
    final now = DateTime.now();
    await boraSends(
      db,
      bora,
      'old',
      MessagePayload(text: 'Süresi doldu'),
      expireAt: now.subtract(const Duration(minutes: 1)),
    );
    await boraSends(
      db,
      bora,
      'live',
      MessagePayload(text: 'Bir saat'),
      expireAt: now.add(const Duration(hours: 1)),
    );
    await boraSends(
      db,
      bora,
      'once',
      MessagePayload(
        text: 'Sadece bir kez',
        mode: DisplayMode.kusDili,
        viewOnce: true,
      ),
      viewOnce: true,
    );

    await t.pumpWidget(
      testApp(
        auth: FakeAuthRepository(signedIn: const AuthUser(uid: a)),
        db: db,
        kv: kv,
        cache: cache,
      ),
    );
    await settle(t);
    await t.tap(find.text('Bora'));
    await settle(t);

    expect(find.text('Süresi doldu'), findsNothing);
    expect(find.text('Bir saat'), findsOneWidget);
    expect(find.byIcon(Icons.timer_outlined), findsWidgets);
    expect(
      find.text('Sadece bir kez'),
      findsNothing,
      reason: 'içerik dokunmadan görünmez',
    );

    await t.tap(find.text('Tek görüntülük mesaj · açmak için dokun'));
    await settle(t);
    final encoded = KusDiliCodec(KusDiliVariant.g).encode('Sadece bir kez');
    expect(find.text(encoded), findsOneWidget);
    await t.tap(find.byKey(const Key('closeViewOnce')));
    await settle(t);
    expect(find.text('Tek görüntülük mesaj açıldı'), findsOneWidget);
    final once = await db.doc('chats/$chatId/messages/once').get();
    expect(once['deleted'], isTrue);
    expect(once['body'], '');
    expect(
      cache.data.values.where((v) => v.contains('Sadece')),
      isEmpty,
      reason: 'cihazdaki çözülmüş kopya da silindi',
    );

    // Ayşe süreli + tek görüntülük gönderir.
    await t.tap(find.byKey(const Key('chatSettings')));
    await settle(t);
    await t.tap(find.byKey(const Key('disappearPicker')));
    await settle(t);
    await t.tap(find.text('1 saat').last);
    await settle(t);
    await t.tap(find.byKey(const Key('saveSettings')));
    await settle(t);
    expect(find.byKey(const Key('disappearOn')), findsOneWidget);
    await t.tap(find.byKey(const Key('viewOnceToggle')));
    await t.enterText(find.byKey(const Key('messageInput')), 'Gizli not');
    await t.pump();
    await t.tap(find.byKey(const Key('send')));
    await settle(t);
    expect(find.text('Tek görüntülük mesaj gönderildi'), findsOneWidget);

    final mine = (await db.collection('chats/$chatId/messages').get()).docs
        .where((d) => d['senderId'] == a)
        .single;
    expect(mine['viewOnce'], isTrue);
    final exp = (mine['expireAt'] as dynamic).toDate() as DateTime;
    expect(exp.difference(DateTime.now()).inMinutes, inInclusiveRange(58, 60));
    final opened = await bora.sealer.open(chatId, a, 1, mine['body'] as String);
    expect(opened.viewOnce, isTrue);
    expect(opened.expiresInSeconds, 3600);
    expect(opened.text, 'Gizli not');

    await t.pumpWidget(const SizedBox());
  });

  testWidgets('Biyometri açıksa kilitli sohbet parmak iziyle açılır', (
    t,
  ) async {
    tallScreen(t);
    final (db, kv, _) = await setup();
    await PinVault(kv, a, iterations: 1000).setPin('2468');
    await PrivacyPrefsStore(
      kv,
      a,
    ).save(const PrivacyPrefs(lockedChats: {chatId}, biometrics: true));
    await t.pumpWidget(
      testApp(
        auth: FakeAuthRepository(signedIn: const AuthUser(uid: a)),
        db: db,
        kv: kv,
        biometrics: true,
      ),
    );
    await settle(t);
    await t.tap(find.text('Bora'));
    await settle(t);
    expect(find.text('Bu sohbet kilitli'), findsNothing);
    expect(find.byKey(const Key('messageInput')), findsOneWidget);
    await t.pumpWidget(const SizedBox());
  });
}
