import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/auth/domain/auth_repository.dart';
import 'package:kus_dili/features/chat/data/firestore_chat_repository.dart';
import 'package:kus_dili/features/chat/domain/message_payload.dart';
import 'package:kus_dili/features/privacy/domain/local_privacy_prefs.dart';
import 'package:kus_dili/features/privacy/domain/pin_vault.dart';
import 'package:material_ui/material_ui.dart';

import '../helpers/fake_auth_repository.dart';
import '../helpers/in_memory.dart';
import '../helpers/test_app.dart';

void main() {
  const a = 'uidAyse';
  const b = 'uidBora';
  const chatId = 'd_${a}_$b';

  Future<FakeFirebaseFirestore> seeded({int messages = 0}) async {
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
    final repo = FirestoreChatRepository(db);
    await repo.ensureDirectChat(me: a, other: b);
    for (var i = 0; i < messages; i++) {
      // Eski (şifresiz geliştirme) biçim yalnızca okunabilir; sayfalama
      // testi için hızlı.
      await repo.sendMessage(
        chatId: chatId,
        messageId: 'm${i.toString().padLeft(3, '0')}',
        senderId: b,
        encVersion: 0,
        body: MessagePayload(text: 'Mesaj $i').encode(),
      );
    }
    return db;
  }

  testWidgets('Sayfalama: ilk 50 mesaj, istenince daha eskiler', (t) async {
    tallScreen(t);
    final db = await seeded(messages: 60);
    await t.pumpWidget(
      testApp(
        auth: FakeAuthRepository(signedIn: const AuthUser(uid: a)),
        db: db,
      ),
    );
    await settle(t);
    await t.tap(find.text('Bora'));
    await settle(t);
    await t.scrollUntilVisible(
      find.byKey(const Key('loadOlder')),
      600,
      scrollable: find
          .descendant(
            of: find.byType(ListView),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    await t.tap(find.byKey(const Key('loadOlder')));
    await settle(t);
    expect(
      find.byKey(const Key('loadOlder')),
      findsNothing,
      reason: '60 < 100: hepsi yüklendi',
    );
    await t.pumpWidget(const SizedBox());
  });

  testWidgets('Kilitli sohbette ekran görüntüsü engellenir, çıkınca kalkar', (
    t,
  ) async {
    tallScreen(t);
    final db = await seeded();
    final kv = InMemorySecureKv();
    await PinVault(kv, a, iterations: 1000).setPin('2468');
    await PrivacyPrefsStore(
      kv,
      a,
    ).save(const PrivacyPrefs(lockedChats: {chatId}));
    final secure = FakeSecureScreen();
    await t.pumpWidget(
      testApp(
        auth: FakeAuthRepository(signedIn: const AuthUser(uid: a)),
        db: db,
        kv: kv,
        secureScreen: secure,
      ),
    );
    await settle(t);
    expect(secure.secure, isFalse);
    await t.tap(find.text('Bora'));
    await settle(t);
    await t.enterText(find.byKey(const Key('unlockPin')), '2468');
    await t.tap(find.byKey(const Key('unlockSubmit')));
    await settle(t);
    expect(find.byKey(const Key('messageInput')), findsOneWidget);
    expect(secure.secure, isTrue);
    await t.pageBack();
    await settle(t);
    expect(secure.secure, isFalse);
    await t.pumpWidget(const SizedBox());
  });

  testWidgets('Genel "ekran görüntüsünü engelle" anahtarı', (t) async {
    tallScreen(t);
    final db = await seeded();
    final secure = FakeSecureScreen();
    await t.pumpWidget(
      testApp(
        auth: FakeAuthRepository(signedIn: const AuthUser(uid: a)),
        db: db,
        secureScreen: secure,
      ),
    );
    await settle(t);
    await t.tap(find.byKey(const Key('openProfile')));
    await settle(t);
    await t.tap(find.byKey(const Key('openPrivacy')));
    await settle(t);
    await t.tap(find.byKey(const Key('blockScreenshotsSwitch')));
    await settle(t);
    expect(secure.secure, isTrue);
    await t.tap(find.byKey(const Key('blockScreenshotsSwitch')));
    await settle(t);
    expect(secure.secure, isFalse);
    await t.pumpWidget(const SizedBox());
  });
}
