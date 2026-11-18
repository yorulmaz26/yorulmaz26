import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/app/app.dart';
import 'package:kus_dili/core/firebase/firebase_bootstrap.dart';
import 'package:kus_dili/core/firebase/firebase_providers.dart';
import 'package:kus_dili/features/auth/application/auth_providers.dart';
import 'package:kus_dili/features/profile/application/profile_providers.dart';
import 'package:kus_dili/features/profile/data/firestore_profile_repository.dart';
import 'package:material_ui/material_ui.dart';

import '../helpers/fake_auth_repository.dart';

/// Yönlendirici + ekranlar + denetleyiciler + Firestore deposu birlikte.
/// Yalnızca Firebase Auth (SMS) ve Firestore sunucusu test sahtesiyle
/// değiştirilir.
void main() {
  Future<void> settle(WidgetTester t) async {
    for (var i = 0; i < 10; i++) {
      await t.pump(const Duration(milliseconds: 100));
    }
  }

  Widget app({
    required FakeAuthRepository auth,
    required FakeFirebaseFirestore db,
    FirebaseStatus firebase = const FirebaseStatus.ready(),
  }) => ProviderScope(
    overrides: [
      firebaseStatusProvider.overrideWithValue(firebase),
      authRepositoryProvider.overrideWithValue(auth),
      profileRepositoryProvider.overrideWithValue(
        FirestoreProfileRepository(db),
      ),
    ],
    child: const KusDiliApp(),
  );

  testWidgets('Kayıt: telefon → kod → profil → ana ekran → çıkış', (t) async {
    final auth = FakeAuthRepository();
    final db = FakeFirebaseFirestore();
    await t.pumpWidget(app(auth: auth, db: db));
    await settle(t);

    // Oturum yok → giriş ekranı.
    expect(find.text('Telefon numaranı gir'), findsOneWidget);
    await t.enterText(find.byKey(const Key('phone')), '0532 123 45 67');
    await t.tap(find.byKey(const Key('submit')));
    await settle(t);

    expect(find.textContaining('+90 532 123 45 67'), findsOneWidget);
    await t.enterText(find.byKey(const Key('code')), '999999');
    await settle(t);
    expect(find.text('Doğrulama kodu hatalı.'), findsOneWidget);

    await t.enterText(find.byKey(const Key('code')), '123456');
    await settle(t);

    // Oturum açıldı, profil yok → profil oluşturma.
    expect(find.text('Profil oluştur'), findsOneWidget);
    await t.enterText(find.byKey(const Key('displayName')), 'Ayşe');
    await t.enterText(find.byKey(const Key('username')), 'Ayse.K');
    await t.tap(find.byKey(const Key('saveProfile')));
    await settle(t);

    // Profil oluştu → ana ekran.
    expect(find.text('@ayse.k  ·  +90 532 123 45 67'), findsOneWidget);
    expect((await db.doc('usernames/ayse.k').get()).data(), {
      'uid': 'uid-905321234567',
    });

    await t.tap(find.byKey(const Key('signOut')));
    await settle(t);
    expect(find.text('Telefon numaranı gir'), findsOneWidget);

    await t.pumpWidget(const SizedBox());
  });

  testWidgets('Alınmış kullanıcı adı uyarısı', (t) async {
    final db = FakeFirebaseFirestore();
    await db.doc('usernames/ayse').set({'uid': 'baskasi'});
    final auth = FakeAuthRepository();
    await t.pumpWidget(app(auth: auth, db: db));
    await settle(t);
    await t.enterText(find.byKey(const Key('phone')), '05321234567');
    await t.tap(find.byKey(const Key('submit')));
    await settle(t);
    await t.enterText(find.byKey(const Key('code')), '123456');
    await settle(t);

    await t.enterText(find.byKey(const Key('displayName')), 'Ayşe');
    await t.enterText(find.byKey(const Key('username')), 'ayse');
    await t.tap(find.byKey(const Key('saveProfile')));
    await settle(t);
    expect(
      find.text('@ayse kullanılıyor. Başka bir ad seçin.'),
      findsOneWidget,
    );
    expect(find.text('Profil oluştur'), findsOneWidget);
    await t.pumpWidget(const SizedBox());
  });

  testWidgets('Firebase yoksa kurulum ekranı, çevirici yine açılır', (t) async {
    await t.pumpWidget(
      app(
        auth: FakeAuthRepository(),
        db: FakeFirebaseFirestore(),
        firebase: FirebaseStatus.unavailable(Exception('no google-services')),
      ),
    );
    await settle(t);
    expect(find.text('Bağlantı kurulamadı'), findsOneWidget);
    await t.tap(find.text('Çevrimdışı çeviriciyi aç'));
    await settle(t);
    expect(find.text('Kuş Dili Çevirici'), findsOneWidget);
  });
}
