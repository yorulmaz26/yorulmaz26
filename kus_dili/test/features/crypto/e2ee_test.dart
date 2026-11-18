import 'dart:convert';

import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/chat/domain/message_payload.dart';
import 'package:kus_dili/features/crypto/domain/safety_number.dart';
import 'package:kus_dili/features/crypto/domain/signal_sealer.dart';

import '../../helpers/signal_client.dart';

void main() {
  const chat = 'd_alice_bob';
  late FakeFirebaseFirestore db;
  late SignalClient alice;
  late SignalClient bob;

  setUp(() async {
    db = FakeFirebaseFirestore();
    alice = SignalClient('alice', db);
    bob = SignalClient('bob', db);
    await alice.keys.ensureKeys();
    await bob.keys.ensureKeys();
  });

  MessagePayload p(String t) =>
      MessagePayload(text: t, mode: DisplayMode.kusDili);

  test('Sunucuya yalnızca açık anahtarlar yayımlanır', () async {
    final dev = await alice.deviceId;
    expect((await db.doc('keyBundles/alice').get())['devices'], [dev]);
    final bundle = (await db.doc('keyBundles/alice/devices/$dev').get())
        .data()!;
    expect(bundle.keys.toSet(), {
      'registrationId',
      'identityKey',
      'signedPreKeyId',
      'signedPreKey',
      'signedPreKeySignature',
      'name',
      'updatedAt',
    });
    expect((await db.collection(await alice.preKeysPath).get()).size, 100);
    // Özel kimlik anahtarı yalnızca cihaz deposunda.
    expect(alice.kv.data.keys.any((k) => k.endsWith('.identity')), isTrue);
  });

  test('Şifreli gönder → alıcı çözer; sunucu açık metni görmez', () async {
    final body = await alice.sealer.seal(chat, p('Seni seviyorum'));
    expect(body, isNot(contains('Seni')));
    expect(body, isNot(contains('seviyorum')));
    final env = jsonDecode(body) as Map<String, dynamic>;
    expect(env['t'], 'md');
    expect(env['sd'], await alice.deviceId);
    // Anahtar Bob'un tek cihazına sarılı; ilk mesaj PreKey mesajı.
    final wraps = env['k'] as Map<String, dynamic>;
    expect(wraps.keys, ['bob:${await bob.deviceId}']);
    expect(wraps.values.single['t'], 3);

    final got = await bob.sealer.open(chat, 'alice', 1, body);
    expect(got.text, 'Seni seviyorum');
    expect(got.mode, DisplayMode.kusDili);

    // Gönderen kendi mesajını önbellekten görür; alıcı tekrar açabilir.
    expect(
      (await alice.sealer.open(chat, 'alice', 1, body)).text,
      'Seni seviyorum',
    );
    expect(
      (await bob.sealer.open(chat, 'alice', 1, body)).text,
      'Seni seviyorum',
    );

    // Bir tek kullanımlık ön-anahtar tüketildi.
    expect((await db.collection(await bob.preKeysPath).get()).size, 99);
  });

  test(
    'İleriye dönük gizlilik: önbellek yoksa aynı mesaj ikinci kez çözülemez',
    () async {
      final body = await alice.sealer.seal(chat, p('Bir kez'));
      await bob.sealer.open(chat, 'alice', 1, body);
      bob.cache.data.clear();
      await expectLater(
        bob.sealer.open(chat, 'alice', 1, body),
        throwsA(isA<MessageUnavailableException>()),
      );
    },
  );

  test('Karşılıklı yazışma, sıra dışı çözme, eşzamanlı açma', () async {
    final a1 = await alice.sealer.seal(chat, p('Selam'));
    expect((await bob.sealer.open(chat, 'alice', 1, a1)).text, 'Selam');
    final b1 = await bob.sealer.seal(chat, p('Merhaba'));
    expect(_wrapType(b1), 2, reason: 'Bob oturumu almış durumda');
    expect((await alice.sealer.open(chat, 'bob', 1, b1)).text, 'Merhaba');

    final bodies = [
      for (var i = 0; i < 6; i++) await alice.sealer.seal(chat, p('m$i')),
    ];
    expect(_wrapType(bodies.last), 2, reason: 'oturum kuruldu');
    // Ters sırada ve aynı mesajı aynı anda iki kez açmak.
    for (final i in [5, 2, 0, 4, 1, 3]) {
      final r = await Future.wait([
        bob.sealer.open(chat, 'alice', 1, bodies[i]),
        bob.sealer.open(chat, 'alice', 1, bodies[i]),
      ]);
      expect(r.map((x) => x.text), ['m$i', 'm$i']);
    }
  });

  test('Bozulmuş şifreli metin reddedilir', () async {
    final body = await alice.sealer.seal(chat, p('Gizli'));
    final env = jsonDecode(body) as Map<String, dynamic>;
    final bytes = base64Decode(env['c'] as String);
    bytes[bytes.length - 1] ^= 0x01;
    env['c'] = base64Encode(bytes);
    await expectLater(
      bob.sealer.open(chat, 'alice', 1, jsonEncode(env)),
      throwsA(isA<MessageUnavailableException>()),
    );
    await expectLater(
      bob.sealer.open(chat, 'alice', 1, 'çöp'),
      throwsA(isA<MessageUnavailableException>()),
    );
  });

  test('Anahtarı olmayan alıcıya gönderilemez', () async {
    await expectLater(
      alice.sealer.seal('d_alice_zed', p('Selam')),
      throwsA(isA<RecipientHasNoKeysException>()),
    );
  });

  test('Uygulama yeniden başlatılınca oturum ve anahtarlar korunur', () async {
    await bob.sealer.open(
      chat,
      'alice',
      1,
      await alice.sealer.seal(chat, p('1')),
    );
    final a2 = alice.restart();
    final b2 = bob.restart();
    await a2.keys.ensureKeys();
    expect(await a2.deviceId, await alice.deviceId);
    expect(
      (await db.collection(await alice.preKeysPath).get()).size,
      100,
      reason: 'yeniden başlatma anahtarları yeniden üretmez',
    );
    final body = await a2.sealer.seal(chat, p('2'));
    expect(_wrapType(body), 3, reason: 'Bob henüz yanıt vermedi');
    expect((await b2.sealer.open(chat, 'alice', 1, body)).text, '2');
  });

  test(
    'Karşı taraf yeniden kurulum yaparsa oturum yenilenir ve uyarı verilir',
    () async {
      await bob.sealer.open(
        chat,
        'alice',
        1,
        await alice.sealer.seal(chat, p('önce')),
      );
      final oldNumber = await _number(alice, 'bob');

      // Bob telefonu sıfırladı: yeni depo, yeni cihaz. Eski cihaz
      // Cihazlar ekranından kaldırılır.
      final newBob = SignalClient('bob', db);
      await newBob.keys.ensureKeys();
      await newBob.dir.removeDevice('bob', await bob.deviceId);

      final a2 = alice.restart(); // Alice uygulamayı yeniden açtı
      final body = await a2.sealer.seal(chat, p('sonra'));
      expect((await newBob.sealer.open(chat, 'alice', 1, body)).text, 'sonra');
      expect(await a2.store.identityChanged('bob'), isTrue);
      expect(await _number(a2, 'bob'), isNot(oldNumber));
      await a2.store.acknowledgeIdentity('bob');
      expect(await a2.store.identityChanged('bob'), isFalse);
    },
  );

  test('Güvenlik numarası iki tarafta aynı, 60 hane', () async {
    final na = await _number(alice, 'bob');
    final nb = await _number(bob, 'alice');
    expect(na, nb);
    expect(na.replaceAll(' ', ''), matches(RegExp(r'^\d{60}$')));
  });

  test('Ön-anahtarlar azalınca yenilenir', () async {
    final others = [for (var i = 0; i < 80; i++) SignalClient('u$i', db)];
    for (final o in others) {
      await o.keys.ensureKeys();
      await o.sealer.seal('d_alice_${o.uid}', p('x'));
    }
    final pre = await alice.preKeysPath;
    expect((await db.collection(pre).get()).size, 20);
    await alice.restart().keys.ensureKeys();
    expect((await db.collection(pre).get()).size, 120);
  });
}

Future<String> _number(SignalClient me, String other) async {
  final mine = await me.dir.listDevices(me.uid);
  final theirs = await me.dir.listDevices(other);
  return safetyNumber(
    myUid: me.uid,
    myIdentityKeys: [for (final d in mine) d.identity.identityKey],
    otherUid: other,
    otherIdentityKeys: [for (final d in theirs) d.identity.identityKey],
  );
}

/// Tek alıcı cihazlı birebir mesajda anahtar sarmalayıcısının Signal türü.
int _wrapType(String body) =>
    ((jsonDecode(body)['k'] as Map).values.single as Map)['t'] as int;
