import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/core/firebase/firebase_providers.dart';
import 'package:kus_dili/features/auth/application/auth_providers.dart';
import 'package:kus_dili/features/auth/domain/auth_repository.dart';
import 'package:kus_dili/features/calls/application/call_controller.dart';
import 'package:kus_dili/features/calls/domain/call.dart';
import 'package:kus_dili/features/calls/domain/call_engine.dart';
import 'package:kus_dili/features/chat/data/firestore_chat_repository.dart';
import 'package:kus_dili/features/crypto/application/crypto_providers.dart';

import '../../helpers/fake_auth_repository.dart';
import '../../helpers/fake_call.dart';
import '../../helpers/in_memory.dart';

const a = 'uidAyse';
const b = 'uidBora';
const chatId = 'd_${a}_$b';

class Device {
  Device(
    this.uid,
    FakeFirebaseFirestore db, {
    Duration ring = const Duration(seconds: 45),
  }) {
    c = ProviderContainer(
      overrides: [
        firestoreProvider.overrideWithValue(db),
        authRepositoryProvider.overrideWithValue(
          FakeAuthRepository(signedIn: AuthUser(uid: uid)),
        ),
        secureKvProvider.overrideWithValue(InMemorySecureKv()),
        payloadCacheProvider.overrideWithValue(InMemoryPayloadCache()),
        callEngineFactoryProvider.overrideWithValue(() {
          final e = FakeCallEngine(uid);
          engines.add(e);
          return e;
        }),
        iceServerSourceProvider.overrideWithValue(FakeIceServers()),
        ringTimeoutProvider.overrideWithValue(ring),
      ],
    );
    c.listen(incomingCallProvider, (_, _) {});
    c.listen(callControllerProvider, (_, _) {});
  }

  final String uid;
  late final ProviderContainer c;
  final List<FakeCallEngine> engines = [];

  Future<void> ready() async {
    await c.read(authStateProvider.future);
    await c.read(keyManagerProvider)!.ensureKeys();
  }

  CallController get ctrl => c.read(callControllerProvider.notifier);
  CallSession get session => c.read(callControllerProvider);
  FakeCallEngine get engine => engines.last;
}

Future<void> until(bool Function() cond, {String? what}) async {
  for (var i = 0; i < 200; i++) {
    if (cond()) return;
    await Future<void>.delayed(const Duration(milliseconds: 10));
  }
  fail('Beklenen durum oluşmadı: ${what ?? ''}');
}

void main() {
  late FakeFirebaseFirestore db;
  late Device ayse;
  late Device bora;

  Future<void> setup({Duration ring = const Duration(seconds: 45)}) async {
    db = FakeFirebaseFirestore();
    await FirestoreChatRepository(db).ensureDirectChat(me: a, other: b);
    ayse = Device(a, db, ring: ring);
    bora = Device(b, db, ring: ring);
    await ayse.ready();
    await bora.ready();
  }

  tearDown(() {
    ayse.c.dispose();
    bora.c.dispose();
  });

  test(
    'Arama: çalar, kabul edilir, şifreli kurulum, bağlanır, kapanır',
    () async {
      await setup();
      await ayse.ctrl.startCall(chatId: chatId, peer: b, video: true);
      expect(ayse.session.phase, CallPhase.outgoing);

      await until(
        () => bora.c.read(incomingCallProvider).value != null,
        what: 'gelen arama',
      );
      final incoming = bora.c.read(incomingCallProvider).value!;
      expect(incoming.caller, a);
      expect(incoming.video, isTrue);

      await bora.ctrl.accept(incoming);
      await until(() => bora.engine.receivedOffer != null, what: 'teklif');
      expect(bora.engine.receivedOffer, ayse.engine.offer);
      await until(() => ayse.engine.receivedAnswer != null, what: 'yanıt');
      expect(ayse.engine.receivedAnswer, bora.engine.answer);
      expect(ayse.session.phase, CallPhase.connecting);

      // ICE adayları iki yönde.
      ayse.engine.ice.add(
        '{"candidate":"cand-A","sdpMid":"0","sdpMLineIndex":0}',
      );
      bora.engine.ice.add(
        '{"candidate":"cand-B","sdpMid":"0","sdpMLineIndex":0}',
      );
      await until(
        () =>
            bora.engine.receivedIce.isNotEmpty &&
            ayse.engine.receivedIce.isNotEmpty,
      );
      expect(bora.engine.receivedIce.single, contains('cand-A'));
      expect(ayse.engine.receivedIce.single, contains('cand-B'));

      // Sunucuda kurulum bilgisi açık değil.
      final call = (await db.collection('calls').get()).docs.single;
      final signals = await db.collection('calls/${call.id}/signals').get();
      expect(signals.size, 4);
      for (final s in signals.docs) {
        final body = s['body'] as String;
        expect(body, isNot(contains('FAKE-')));
        expect(body, isNot(contains('fingerprint')));
        expect(body, isNot(contains('cand-')));
      }

      ayse.engine.conn.add(EngineConnection.connected);
      bora.engine.conn.add(EngineConnection.connected);
      await until(
        () =>
            ayse.session.phase == CallPhase.connected &&
            bora.session.phase == CallPhase.connected,
      );

      await bora.ctrl.toggleMute();
      expect(bora.engine.muted, isTrue);

      await bora.ctrl.hangUp();
      await until(
        () => ayse.session.phase == CallPhase.ended,
        what: 'karşı taraf kapattı',
      );
      expect(ayse.session.endReason, 'Arama bitti');
      expect(ayse.engine.disposed && bora.engine.disposed, isTrue);
      expect((await db.doc('calls/${call.id}').get())['state'], 'ended');
    },
  );

  test('Reddedilen arama', () async {
    await setup();
    await ayse.ctrl.startCall(chatId: chatId, peer: b, video: false);
    await until(() => bora.c.read(incomingCallProvider).value != null);
    await bora.ctrl.decline(bora.c.read(incomingCallProvider).value!);
    await until(() => ayse.session.phase == CallPhase.ended);
    expect(ayse.session.endReason, 'Arama reddedildi');
    await until(
      () => bora.c.read(incomingCallProvider).value == null,
      what: 'çalma durdu',
    );
  });

  test('Cevapsız arama zaman aşımı', () async {
    await setup(ring: const Duration(milliseconds: 300));
    await ayse.ctrl.startCall(chatId: chatId, peer: b, video: false);
    await until(
      () => ayse.session.phase == CallPhase.ended,
      what: 'zaman aşımı',
    );
    expect(ayse.session.endReason, 'Cevap yok');
    final call = (await db.collection('calls').get()).docs.single;
    expect(call['state'], 'missed');
    expect(call['endedBy'], a);
  });

  test('Arayan çalarken vazgeçer', () async {
    await setup();
    await ayse.ctrl.startCall(chatId: chatId, peer: b, video: false);
    await until(() => bora.c.read(incomingCallProvider).value != null);
    await ayse.ctrl.hangUp();
    await until(() => bora.c.read(incomingCallProvider).value == null);
    expect((await db.collection('calls').get()).docs.single['state'], 'missed');
  });

  test('Bağlantı koparsa arama biter', () async {
    await setup();
    await ayse.ctrl.startCall(chatId: chatId, peer: b, video: false);
    await until(() => bora.c.read(incomingCallProvider).value != null);
    await bora.ctrl.accept(bora.c.read(incomingCallProvider).value!);
    await until(() => ayse.engine.receivedAnswer != null);
    ayse.engine.conn.add(EngineConnection.failed);
    await until(() => bora.session.phase == CallPhase.ended);
    expect(ayse.session.endReason, 'Bağlantı koptu');
    expect(CallStatus.parse('ended').isTerminal, isTrue);
  });
}
