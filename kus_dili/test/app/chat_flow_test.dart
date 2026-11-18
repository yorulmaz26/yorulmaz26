import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/auth/domain/auth_repository.dart';
import 'package:kus_dili/features/chat/data/firestore_chat_repository.dart';
import 'package:kus_dili/features/chat/domain/message_payload.dart';
import 'package:kus_dili_engine/kus_dili_engine.dart';
import 'package:material_ui/material_ui.dart';

import '../helpers/fake_auth_repository.dart';
import '../helpers/test_app.dart';

/// Ayşe (oturumdaki kullanıcı) ile Bora arasında gerçek Firestore deposu
/// kodu üzerinden yazışma. Bora'nın mesajları aynı depo sınıfıyla yazılır.
void main() {
  const a = 'uidAyse';
  const b = 'uidBora';

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

  testWidgets(
    'Kuş dilinde yazışma: gönder, al, çöz, yanıtla, sil, mod değiştir',
    (t) async {
      final db = await seeded();
      final auth = FakeAuthRepository(
        signedIn: const AuthUser(uid: a, phoneNumber: '+905321234567'),
      );
      await t.pumpWidget(testApp(auth: auth, db: db));
      await settle(t);
      expect(find.textContaining('Henüz sohbetin yok'), findsOneWidget);

      // Kullanıcı adıyla bul ve sohbet başlat.
      await t.tap(find.byKey(const Key('newChat')));
      await settle(t);
      await t.enterText(find.byKey(const Key('searchUsername')), '@Bora');
      await t.tap(find.byKey(const Key('doSearch')));
      await settle(t);
      await t.tap(find.byKey(const Key('searchResult')));
      await settle(t);
      expect(find.text('@bora'), findsOneWidget);
      expect(find.textContaining('uçtan uca şifreli değil'), findsOneWidget);

      // Varsayılan mod: otomatik kuş dili, yazarken canlı önizleme.
      await t.enterText(
        find.byKey(const Key('messageInput')),
        'Seni seviyorum',
      );
      await t.pump();
      expect(
        t.widget<Text>(find.byKey(const Key('livePreview'))).data,
        'Segenigi segevigiyogorugum',
      );
      await t.tap(find.byKey(const Key('send')));
      await settle(t);
      expect(find.text('Segenigi segevigiyogorugum'), findsOneWidget);

      // Sunucuya kuş dili değil, orijinal metin + gösterim bilgisi gider
      // (Aşama 7'de bu gövde şifrelenecek).
      const chatId = 'd_${a}_$b';
      final msgs = await db.collection('chats/$chatId/messages').get();
      final sent = MessagePayload.decode(msgs.docs.single['body'] as String);
      expect(sent.text, 'Seni seviyorum');
      expect(sent.mode, DisplayMode.kusDili);

      // Bora yanıt veriyor (F'li kuş dili).
      final repo = FirestoreChatRepository(db);
      await repo.sendMessage(
        chatId: chatId,
        messageId: 'fromBora',
        senderId: b,
        encVersion: 0,
        body: MessagePayload(
          text: 'Ben de seni',
          mode: DisplayMode.kusDili,
          variant: KusDiliVariant.f,
        ).encode(),
      );
      await settle(t);
      final encoded = KusDiliCodec(KusDiliVariant.f).encode('Ben de seni');
      expect(find.text(encoded), findsOneWidget);

      // Açık sohbet ekranında gelen mesaj okundu olarak işaretlenir.
      final chatDoc = await db.doc('chats/$chatId').get();
      final boraMsg = await db.doc('chats/$chatId/messages/fromBora').get();
      final readA = ((chatDoc['readAt'] as Map)[a] as Timestamp).toDate();
      final sentB = (boraMsg['sentAt'] as Timestamp).toDate();
      expect(readA.isBefore(sentB), isFalse);

      // Dokununca çözülür, tekrar dokununca kuş diline döner.
      await t.tap(find.text(encoded));
      await settle(t);
      expect(find.text('Ben de seni'), findsOneWidget);
      await t.tap(find.text('Ben de seni'));
      await settle(t);
      expect(find.text(encoded), findsOneWidget);

      // Yanıtla.
      await t.longPress(find.text(encoded));
      await settle(t);
      await t.tap(find.text('Yanıtla'));
      await settle(t);
      await t.enterText(find.byKey(const Key('messageInput')), 'Selam');
      await t.pump();
      await t.tap(find.byKey(const Key('send')));
      await settle(t);
      final reply = (await db.collection('chats/$chatId/messages').get()).docs
          .map((d) => MessagePayload.decode(d['body'] as String))
          .firstWhere((p) => p.text == 'Selam');
      expect(reply.replyToId, 'fromBora');
      expect(reply.replyQuote, 'Ben de seni');

      // Kendi mesajını herkesten sil.
      await t.longPress(find.text('Segenigi segevigiyogorugum'));
      await settle(t);
      await t.tap(find.byKey(const Key('deleteMessage')));
      await settle(t);
      expect(find.text('Bu mesaj silindi'), findsOneWidget);
      expect(find.text('Segenigi segevigiyogorugum'), findsNothing);

      // Sohbet dilini "Normal" yap → düz mesaj.
      await t.tap(find.byKey(const Key('chatSettings')));
      await settle(t);
      await t.tap(find.text('Normal'));
      await t.pump();
      await t.tap(find.byKey(const Key('saveSettings')));
      await settle(t);
      await t.enterText(find.byKey(const Key('messageInput')), 'Merhaba');
      await t.pump();
      await t.tap(find.byKey(const Key('send')));
      await settle(t);
      expect(find.text('Merhaba'), findsOneWidget);
      expect(
        (await db.doc('users/$a/chatSettings/$chatId').get())
            .data()!['sendMode'],
        'p',
      );

      await t.pumpWidget(const SizedBox());
    },
  );

  testWidgets('Düzenleme ve "her zaman Türkçe" görünümü', (t) async {
    final db = await seeded();
    const chatId = 'd_${a}_$b';
    final repo = FirestoreChatRepository(db);
    await repo.ensureDirectChat(me: a, other: b);
    await repo.sendMessage(
      chatId: chatId,
      messageId: 'x',
      senderId: b,
      encVersion: 0,
      body: MessagePayload(text: 'Gizli', mode: DisplayMode.kusDili).encode(),
    );
    await db.doc('users/$a/chatSettings/$chatId').set({
      'sendMode': 'k',
      'variant': {'id': 'g', 'name': "G'li", 'template': 'g{v}'},
      'viewMode': 'original',
    });
    final auth = FakeAuthRepository(signedIn: const AuthUser(uid: a));
    await t.pumpWidget(testApp(auth: auth, db: db));
    await settle(t);

    // Listede okunmamış işareti, sonra sohbet.
    expect(find.byKey(const Key('unreadDot')), findsOneWidget);
    await t.tap(find.text('Bora'));
    await settle(t);
    expect(find.text('Gizli'), findsOneWidget, reason: 'Türkçe görünüm');

    await t.enterText(find.byKey(const Key('messageInput')), 'Selam');
    await t.pump();
    await t.tap(find.byKey(const Key('send')));
    await settle(t);
    expect(find.text('Segelagam'), findsOneWidget);

    await t.longPress(find.text('Segelagam'));
    await settle(t);
    await t.tap(find.byKey(const Key('editMessage')));
    await settle(t);
    await t.enterText(find.byKey(const Key('messageInput')), 'Selamlar');
    await t.pump();
    await t.tap(find.byKey(const Key('send')));
    await settle(t);
    expect(find.text('Segelagamlagar'), findsOneWidget);
    expect(find.textContaining('düzenlendi'), findsOneWidget);

    await t.pumpWidget(const SizedBox());
  });
}
