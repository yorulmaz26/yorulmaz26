import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/chat/data/firestore_chat_repository.dart';

void main() {
  late FakeFirebaseFirestore db;
  late FirestoreChatRepository repo;

  setUp(() {
    db = FakeFirebaseFirestore();
    repo = FirestoreChatRepository(db);
  });

  test('ensureDirectChat tek sohbet oluşturur (iki yönden de)', () async {
    final id1 = await repo.ensureDirectChat(me: 'b', other: 'a');
    final id2 = await repo.ensureDirectChat(me: 'a', other: 'b');
    expect(id1, 'd_a_b');
    expect(id2, id1);
    final chats = await db.collection('chats').get();
    expect(chats.docs, hasLength(1));
    final d = chats.docs.single.data();
    expect(d['members'], ['a', 'b']);
    expect(d.containsKey('preview'), isFalse);
  });

  test('Mesaj gönderme sohbeti günceller, önizleme yazmaz', () async {
    final id = await repo.ensureDirectChat(me: 'a', other: 'b');
    final mid = repo.newMessageId(id);
    await repo.sendMessage(
      chatId: id,
      messageId: mid,
      senderId: 'a',
      encVersion: 0,
      body: '{}',
    );
    final chat = await repo.watchChat(id).first;
    expect(chat!.lastSenderId, 'a');
    expect(chat.isUnreadFor('b'), isTrue);
    expect(chat.isUnreadFor('a'), isFalse);
    final msgs = await repo.watchMessages(id).first;
    expect(msgs.single.id, mid);
    expect(msgs.single.body, '{}');
    final raw = (await db.doc('chats/$id/messages/$mid').get()).data()!;
    expect(raw.keys.toSet(), {
      'senderId',
      'sentAt',
      'encVersion',
      'body',
      'deleted',
    });
  });

  test('Düzenleme, silme ve okundu', () async {
    final id = await repo.ensureDirectChat(me: 'a', other: 'b');
    await repo.sendMessage(
      chatId: id,
      messageId: 'm1',
      senderId: 'a',
      encVersion: 0,
      body: 'x',
    );
    await repo.editMessage(chatId: id, messageId: 'm1', body: 'y');
    var m = (await repo.watchMessages(id).first).single;
    expect(m.body, 'y');
    expect(m.editedAt, isNotNull);
    await repo.deleteMessage(chatId: id, messageId: 'm1');
    m = (await repo.watchMessages(id).first).single;
    expect(m.deleted, isTrue);
    expect(m.body, '');

    await repo.markRead(chatId: id, uid: 'b');
    final chat = await repo.watchChat(id).first;
    expect(chat!.isUnreadFor('b'), isFalse);
  });

  test('Sohbet listesi yalnızca üyesi olunanlar, yeni → eski', () async {
    final ab = await repo.ensureDirectChat(me: 'a', other: 'b');
    await repo.ensureDirectChat(me: 'c', other: 'd');
    final ac = await repo.ensureDirectChat(me: 'a', other: 'c');
    await repo.sendMessage(
      chatId: ab,
      messageId: 'm',
      senderId: 'a',
      encVersion: 0,
      body: 'x',
    );
    final list = await repo.watchChats('a').first;
    expect(list.map((c) => c.id), [ab, ac]);
  });
}
