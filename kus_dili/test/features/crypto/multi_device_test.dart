import 'dart:convert';

import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/chat/domain/message_payload.dart';
import 'package:kus_dili/features/crypto/domain/key_directory.dart';
import 'package:kus_dili/features/crypto/domain/key_manager.dart';
import 'package:kus_dili/features/crypto/domain/safety_number.dart';
import 'package:kus_dili/features/crypto/domain/signal_sealer.dart';

import '../../helpers/signal_client.dart';

/// Çoklu cihaz: aynı hesabın en fazla 5 cihazı, her birinin kendi Signal
/// kimliği. Sunucu yine yalnızca şifreli metin görür.
void main() {
  const chat = 'd_alice_bob';
  const g = 'g_multidevice01';
  late FakeFirebaseFirestore db;
  late SignalClient phone, tablet, bob;

  setUp(() async {
    db = FakeFirebaseFirestore();
    phone = SignalClient('alice', db, name: 'Android telefon');
    tablet = SignalClient('alice', db, name: 'iPhone');
    bob = SignalClient('bob', db);
    for (final c in [phone, tablet, bob]) {
      await c.keys.ensureKeys();
    }
  });

  MessagePayload p(String t) => MessagePayload(text: t);

  test('Her kurulum ayrı cihaz; ikisi de hesapta listelenir', () async {
    final a = await phone.deviceId;
    final b = await tablet.deviceId;
    expect(a, isNot(b));
    expect(await phone.dir.devices('alice'), [a, b]);
    final infos = await phone.dir.listDevices('alice');
    expect(infos.map((d) => d.name), ['Android telefon', 'iPhone']);
  });

  test('Bob yazar → Alice\'in iki cihazı da çözer', () async {
    final body = await bob.sealer.seal(chat, p('İkinize de selam'));
    final wraps = (jsonDecode(body)['k'] as Map).keys.toSet();
    expect(wraps, {
      'alice:${await phone.deviceId}',
      'alice:${await tablet.deviceId}',
    });
    expect(body, isNot(contains('selam')));
    expect(
      (await phone.sealer.open(chat, 'bob', 1, body)).text,
      'İkinize de selam',
    );
    expect(
      (await tablet.sealer.open(chat, 'bob', 1, body)).text,
      'İkinize de selam',
    );
  });

  test('Telefondan yazılan mesaj tablette de görünür', () async {
    final body = await phone.sealer.seal(chat, p('Telefondan'));
    final wraps = (jsonDecode(body)['k'] as Map).keys.toSet();
    expect(wraps, {
      'bob:${await bob.deviceId}',
      'alice:${await tablet.deviceId}',
    }, reason: 'gönderen cihazın kendisi hariç');
    expect((await bob.sealer.open(chat, 'alice', 1, body)).text, 'Telefondan');
    expect(
      (await tablet.sealer.open(chat, 'alice', 1, body)).text,
      'Telefondan',
    );
    // Gönderen cihaz kendi kopyasını önbellekten okur.
    expect(
      (await phone.sealer.open(chat, 'alice', 1, body)).text,
      'Telefondan',
    );
    phone.cache.data.clear();
    await expectLater(
      phone.sealer.open(chat, 'alice', 1, body),
      throwsA(isA<MessageUnavailableException>()),
    );
  });

  test('Sonradan bağlanan cihaz eski mesajları okuyamaz', () async {
    final old = await bob.sealer.seal(chat, p('eski'));
    final laptop = SignalClient('alice', db);
    await laptop.keys.ensureKeys();
    await expectLater(
      laptop.sealer.open(chat, 'bob', 1, old),
      throwsA(
        isA<MessageUnavailableException>().having(
          (e) => e.reason,
          'neden',
          contains('bağlanmadan önce'),
        ),
      ),
    );
    final fresh = await bob.sealer.seal(chat, p('yeni'));
    expect((await laptop.sealer.open(chat, 'bob', 1, fresh)).text, 'yeni');
  });

  test('Grup: her cihazın kendi anahtarı; tüm cihazlar okur', () async {
    final carol = SignalClient('carol', db);
    await carol.keys.ensureKeys();
    await db.doc('chats/$g').set({
      'type': 'group',
      'members': ['alice', 'bob', 'carol'],
    });
    final fromBob = await bob.sealer.seal(g, p('Bob gruba'));
    final fromPhone = await phone.sealer.seal(g, p('Telefon gruba'));
    for (final c in [phone, tablet, carol]) {
      expect((await c.sealer.open(g, 'bob', 1, fromBob)).text, 'Bob gruba');
    }
    for (final c in [tablet, bob, carol]) {
      expect(
        (await c.sealer.open(g, 'alice', 1, fromPhone)).text,
        'Telefon gruba',
      );
    }
    final keys = await db.collection('chats/$g/senderKeys').get();
    final phoneShare = keys.docs
        .firstWhere((d) => d.id.startsWith('alice_'))
        .data();
    expect(phoneShare['device'], await phone.deviceId);
    expect((phoneShare['shares'] as Map).keys.toSet(), {
      'alice:${await tablet.deviceId}',
      'bob:${await bob.deviceId}',
      'carol:${await carol.deviceId}',
    });
  });

  test('Grup: yeni cihaz eklenince anahtar yeniden dağıtılır', () async {
    await db.doc('chats/$g').set({
      'type': 'group',
      'members': ['alice', 'bob'],
    });
    final m1 = await bob.sealer.seal(g, p('1'));
    final bob2 = SignalClient('bob', db);
    await bob2.keys.ensureKeys();
    final m2 = await bob.sealer.seal(g, p('2'));
    expect(jsonDecode(m2)['e'], isNot(jsonDecode(m1)['e']));
    expect((await bob2.sealer.open(g, 'bob', 1, m2)).text, '2');
    expect((await tablet.sealer.open(g, 'bob', 1, m2)).text, '2');
  });

  test('Karşı taraf cihaz ekleyince uyarı ve yeni güvenlik numarası', () async {
    await phone.sealer.seal(chat, p('önce'));
    expect(await phone.store.identityChanged('bob'), isFalse);
    final before = await _number(phone, 'bob');

    final bobTablet = SignalClient('bob', db);
    await bobTablet.keys.ensureKeys();
    await phone.sealer.seal(chat, p('sonra'));
    expect(await phone.store.identityChanged('bob'), isTrue);
    expect(await _number(phone, 'bob'), isNot(before));
    // İki taraf aynı numarayı görür.
    expect(await _number(phone, 'bob'), await _number(bob, 'alice'));
  });

  test('Yeni cihazdan gelen ilk mesaj da uyarı verir', () async {
    await bob.sealer.open(
      chat,
      'alice',
      1,
      await phone.sealer.seal(chat, p('a')),
    );
    expect(await phone.store.identityChanged('bob'), isFalse);
    final bobTablet = SignalClient('bob', db);
    await bobTablet.keys.ensureKeys();
    final body = await bobTablet.sealer.seal(chat, p('yeni cihazdan'));
    expect(
      (await phone.sealer.open(chat, 'bob', 1, body)).text,
      'yeni cihazdan',
    );
    expect(await phone.store.identityChanged('bob'), isTrue);
  });

  test(
    'Cihaz kaldırılınca yeni mesajlar ona gitmez, kendisi kapanır',
    () async {
      final tabletId = await tablet.deviceId;
      await phone.dir.removeDevice('alice', tabletId);
      expect(await phone.dir.devices('alice'), [await phone.deviceId]);
      expect(
        (await db
                .collection('keyBundles/alice/devices/$tabletId/prekeys')
                .get())
            .size,
        0,
      );
      expect(
        (await db.doc('keyBundles/alice/devices/$tabletId').get()).exists,
        isFalse,
      );

      final body = await bob.sealer.seal(chat, p('yalnız telefona'));
      expect((jsonDecode(body)['k'] as Map).keys, [
        'alice:${await phone.deviceId}',
      ]);
      await expectLater(
        tablet.restart().keys.ensureKeys(),
        throwsA(isA<DeviceRemovedException>()),
      );
    },
  );

  test('Hesaba en fazla 5 cihaz bağlanır', () async {
    for (var i = 0; i < 3; i++) {
      await SignalClient('alice', db).keys.ensureKeys();
    }
    expect((await phone.dir.devices('alice')).length, maxDevices);
    final sixth = SignalClient('alice', db);
    await expectLater(
      sixth.keys.ensureKeys(),
      throwsA(isA<TooManyDevicesException>()),
    );
    // Biri kaldırılınca yer açılır; reddedilen cihaz yeniden dener.
    await phone.dir.removeDevice('alice', await tablet.deviceId);
    await sixth.keys.ensureKeys();
    expect(await sixth.dir.devices('alice'), contains(await sixth.deviceId));
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
