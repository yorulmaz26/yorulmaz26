import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/auth/domain/auth_repository.dart';
import 'package:material_ui/material_ui.dart';

import '../helpers/fake_auth_repository.dart';
import '../helpers/in_memory.dart';
import '../helpers/signal_client.dart';
import '../helpers/test_app.dart';

/// Bağlı cihazlar: listele, yeni cihaz uyarısı, kaldır, kaldırılan cihazın
/// kendini temizlemesi, 5 cihaz sınırı.
void main() {
  const a = 'uidAyse';

  Future<FakeFirebaseFirestore> seeded() async {
    final db = FakeFirebaseFirestore();
    await db.doc('users/$a').set({
      'username': 'ayse',
      'displayName': 'Ayşe',
      'createdAt': DateTime(2026),
    });
    await db.doc('usernames/ayse').set({'uid': a});
    return db;
  }

  FakeAuthRepository signedIn() => FakeAuthRepository(
    signedIn: const AuthUser(uid: a, phoneNumber: '+905321234567'),
  );

  Future<List<int>> devicesOf(FakeFirebaseFirestore db) async => [
    for (final d in (await db.doc('keyBundles/$a').get())['devices'] as List)
      d as int,
  ];

  testWidgets('Yeni cihaz uyarısı, onay ve başka cihazı kaldırma', (t) async {
    tallScreen(t);
    final db = await seeded();
    final ipad = SignalClient(a, db, name: 'iPhone');
    await ipad.keys.ensureKeys();

    await t.pumpWidget(testApp(auth: signedIn(), db: db));
    await settle(t);
    expect((await devicesOf(db)).length, 2);
    // Bu cihaz kaydolurken var olan cihaz için uyarı yok.
    expect(find.byKey(const Key('newDeviceBanner')), findsNothing);

    // Sonradan üçüncü bir cihaz bağlanıyor.
    final laptop = SignalClient(a, db, name: 'Android telefon');
    await laptop.keys.ensureKeys();
    await settle(t);
    expect(find.byKey(const Key('newDeviceBanner')), findsOneWidget);

    await t.tap(find.text('Kontrol et'));
    await settle(t);
    expect(find.byKey(const Key('newDeviceAlert')), findsOneWidget);
    expect(find.textContaining('(bu cihaz)'), findsOneWidget);
    expect(find.textContaining('Son etkinlik'), findsNWidgets(3));

    await t.tap(find.byKey(const Key('ackNewDevice')));
    await settle(t);
    expect(find.byKey(const Key('newDeviceAlert')), findsNothing);

    final ipadId = await ipad.deviceId;
    await t.tap(find.byKey(Key('removeDevice-$ipadId')));
    await settle(t);
    await t.tap(find.byKey(const Key('confirmRemoveDevice')));
    await settle(t);
    expect(await devicesOf(db), isNot(contains(ipadId)));
    expect(find.byKey(Key('device-$ipadId')), findsNothing);
    expect((await db.doc('keyBundles/$a/devices/$ipadId').get()).exists, false);
  });

  testWidgets('Başka cihazdan kaldırılan cihaz temizlenip çıkış yapar', (
    t,
  ) async {
    final db = await seeded();
    final kv = InMemorySecureKv();
    await t.pumpWidget(testApp(auth: signedIn(), db: db, kv: kv));
    await settle(t);
    final mine = (await devicesOf(db)).single;
    expect(kv.data, isNotEmpty);

    final other = SignalClient(a, db);
    await other.keys.ensureKeys();
    await other.dir.removeDevice(a, mine);
    await settle(t, 20);

    expect(find.byKey(const Key('signOutNotice')), findsOneWidget);
    expect(find.textContaining('bağlantısı'), findsOneWidget);
    expect(kv.data, isEmpty, reason: 'anahtarlar ve PIN silindi');
  });

  testWidgets('5 cihaz doluysa uyarı; biri kaldırılınca bu cihaz eklenir', (
    t,
  ) async {
    tallScreen(t);
    final db = await seeded();
    final others = [for (var i = 0; i < 5; i++) SignalClient(a, db)];
    for (final o in others) {
      await o.keys.ensureKeys();
    }
    await t.pumpWidget(testApp(auth: signedIn(), db: db));
    await settle(t);
    expect(find.byKey(const Key('tooManyDevices')), findsOneWidget);
    expect((await devicesOf(db)).length, 5);

    await t.tap(find.text('Cihazlar'));
    await settle(t);
    final first = await others.first.deviceId;
    await t.tap(find.byKey(Key('removeDevice-$first')));
    await settle(t);
    await t.tap(find.byKey(const Key('confirmRemoveDevice')));
    await settle(t, 20);
    final now = await devicesOf(db);
    expect(now.length, 5, reason: 'bu cihaz boşalan yere eklendi');
    expect(now, isNot(contains(first)));
    expect(find.textContaining('(bu cihaz)'), findsOneWidget);
  });
}
