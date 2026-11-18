import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/auth/domain/auth_repository.dart';
import 'package:material_ui/material_ui.dart';

import '../helpers/fake_auth_repository.dart';
import '../helpers/in_memory.dart';
import '../helpers/test_app.dart';

void main() {
  const a = 'uidAyse';

  Future<FakeFirebaseFirestore> seeded() async {
    final db = FakeFirebaseFirestore();
    await db.doc('users/$a').set({
      'username': 'ayse',
      'displayName': 'Ayşe',
      'createdAt': DateTime(2026),
    });
    return db;
  }

  Future<void> openDelete(WidgetTester t) async {
    await t.tap(find.byKey(const Key('openProfile')));
    await settle(t);
    await t.tap(find.byKey(const Key('deleteAccount')));
    await settle(t);
  }

  testWidgets('Onaylanınca hesap silinir, cihaz temizlenir, çıkış yapılır', (
    t,
  ) async {
    tallScreen(t);
    final kv = InMemorySecureKv()..data['sig.uidAyse.identity'] = 'gizli';
    final cache = InMemoryPayloadCache()..data['d_a_b/h'] = '{}';
    final account = FakeAccountService();
    await t.pumpWidget(
      testApp(
        auth: FakeAuthRepository(signedIn: const AuthUser(uid: a)),
        db: await seeded(),
        kv: kv,
        cache: cache,
        account: account,
      ),
    );
    await settle(t);
    await openDelete(t);

    await t.enterText(find.byKey(const Key('deleteConfirmField')), 'baskasi');
    await t.pump();
    expect(
      t.widget<FilledButton>(find.byKey(const Key('deleteConfirm'))).onPressed,
      isNull,
    );
    await t.enterText(find.byKey(const Key('deleteConfirmField')), '@ayse');
    await t.pump();
    await t.tap(find.byKey(const Key('deleteConfirm')));
    await settle(t);

    expect(account.calls, 1);
    expect(kv.data.keys.where((k) => k.startsWith('sig.')), isEmpty);
    expect(cache.data, isEmpty);
    expect(find.text('Telefon numaranı gir'), findsOneWidget);
    await t.pumpWidget(const SizedBox());
  });

  testWidgets('Sunucu silemezse cihazda hiçbir şey silinmez', (t) async {
    tallScreen(t);
    final kv = InMemorySecureKv()..data['sig.uidAyse.identity'] = 'gizli';
    await t.pumpWidget(
      testApp(
        auth: FakeAuthRepository(signedIn: const AuthUser(uid: a)),
        db: await seeded(),
        kv: kv,
        account: FakeAccountService(fail: true),
      ),
    );
    await settle(t);
    await openDelete(t);
    await t.enterText(find.byKey(const Key('deleteConfirmField')), 'ayse');
    await t.pump();
    await t.tap(find.byKey(const Key('deleteConfirm')));
    await settle(t);
    expect(find.textContaining('Hesap silinemedi'), findsOneWidget);
    expect(kv.data['sig.uidAyse.identity'], 'gizli');
    await t.pumpWidget(const SizedBox());
  });
}
