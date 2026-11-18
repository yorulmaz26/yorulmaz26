import 'dart:convert';

import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/chat/domain/message_payload.dart';
import 'package:kus_dili/features/crypto/domain/signal_sealer.dart';

import '../../helpers/signal_client.dart';

void main() {
  const g = 'g_testgroup0001';
  late FakeFirebaseFirestore db;
  late SignalClient alice, bob, carol;

  Future<void> setMembers(List<String> m) =>
      db.doc('chats/$g').set({'type': 'group', 'members': m});

  setUp(() async {
    db = FakeFirebaseFirestore();
    alice = SignalClient('alice', db);
    bob = SignalClient('bob', db);
    carol = SignalClient('carol', db);
    for (final c in [alice, bob, carol]) {
      await c.keys.ensureKeys();
    }
    await setMembers(['alice', 'bob', 'carol']);
  });

  MessagePayload p(String t) => MessagePayload(text: t);

  test('Grup mesajı bir kez şifrelenir, tüm üyeler çözer', () async {
    final body = await alice.sealer.seal(g, p('Herkese selam'));
    expect(body, isNot(contains('selam')));
    expect(jsonDecode(body)['t'], 'sk');

    // Anahtar paylaşımı: her üye için ayrı, birebir şifreli.
    final shares = await db.collection('chats/$g/senderKeys').get();
    expect(shares.size, 1);
    final s = shares.docs.single.data()['shares'] as Map;
    expect(s.keys.toSet(), {
      'bob:${await bob.deviceId}',
      'carol:${await carol.deviceId}',
    });
    expect(jsonEncode(s), isNot(contains('selam')));

    expect((await bob.sealer.open(g, 'alice', 1, body)).text, 'Herkese selam');
    expect(
      (await carol.sealer.open(g, 'alice', 1, body)).text,
      'Herkese selam',
    );
    expect(
      (await alice.sealer.open(g, 'alice', 1, body)).text,
      'Herkese selam',
    );
  });

  test('Herkes yazar; aynı dönemde anahtar yeniden dağıtılmaz', () async {
    final a1 = await alice.sealer.seal(g, p('a1'));
    final a2 = await alice.sealer.seal(g, p('a2'));
    final b1 = await bob.sealer.seal(g, p('b1'));
    final c1 = await carol.sealer.seal(g, p('c1'));
    expect((await db.collection('chats/$g/senderKeys').get()).size, 3);
    // Sıra dışı çözme.
    expect((await carol.sealer.open(g, 'alice', 1, a2)).text, 'a2');
    expect((await carol.sealer.open(g, 'alice', 1, a1)).text, 'a1');
    expect((await alice.sealer.open(g, 'bob', 1, b1)).text, 'b1');
    expect((await bob.sealer.open(g, 'carol', 1, c1)).text, 'c1');
  });

  test('Çıkarılan üye yeni mesajları çözemez (anahtar yenilenir)', () async {
    final before = await alice.sealer.seal(g, p('önce'));
    expect((await carol.sealer.open(g, 'alice', 1, before)).text, 'önce');

    await setMembers(['alice', 'bob']);
    final after = await alice.sealer.seal(g, p('sonra'));
    expect(jsonDecode(after)['e'], isNot(jsonDecode(before)['e']));
    expect((await bob.sealer.open(g, 'alice', 1, after)).text, 'sonra');
    await expectLater(
      carol.sealer.open(g, 'alice', 1, after),
      throwsA(isA<MessageUnavailableException>()),
    );
  });

  test(
    'Yeni üye eklendikten sonraki mesajları okur, öncekileri okuyamaz',
    () async {
      final dave = SignalClient('dave', db);
      await dave.keys.ensureKeys();
      final old = await alice.sealer.seal(g, p('eski'));
      await setMembers(['alice', 'bob', 'carol', 'dave']);
      final fresh = await alice.sealer.seal(g, p('yeni'));
      expect((await dave.sealer.open(g, 'alice', 1, fresh)).text, 'yeni');
      await expectLater(
        dave.sealer.open(g, 'alice', 1, old),
        throwsA(isA<MessageUnavailableException>()),
      );
    },
  );

  test(
    'Anahtarı olmayan üye atlanır, anahtarı gelince sonraki dönemde alır',
    () async {
      final erin = SignalClient('erin', db);
      await setMembers(['alice', 'bob', 'erin']);
      final m1 = await alice.sealer.seal(g, p('1'));
      expect((await bob.sealer.open(g, 'alice', 1, m1)).text, '1');
      await erin.keys.ensureKeys();
      final m2 = await alice.sealer.seal(g, p('2'));
      expect((await erin.sealer.open(g, 'alice', 1, m2)).text, '2');
    },
  );

  test('Üye olmayan gönderemez', () async {
    await setMembers(['bob', 'carol']);
    await expectLater(
      alice.sealer.seal(g, p('x')),
      throwsA(isA<MessageUnavailableException>()),
    );
  });

  test('Uygulama yeniden başlatılınca grup anahtarları korunur', () async {
    final m1 = await alice.sealer.seal(g, p('1'));
    await bob.sealer.open(g, 'alice', 1, m1);
    final a2 = alice.restart();
    final b2 = bob.restart();
    final m2 = await a2.sealer.seal(g, p('2'));
    expect(
      (await db.collection('chats/$g/senderKeys').get()).size,
      1,
      reason: 'aynı dönem sürer',
    );
    expect((await b2.sealer.open(g, 'alice', 1, m2)).text, '2');
  });
}
