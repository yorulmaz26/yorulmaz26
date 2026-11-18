import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/app/app.dart';
import 'package:kus_dili/core/firebase/firebase_bootstrap.dart';
import 'package:kus_dili/core/firebase/firebase_providers.dart';
import 'package:kus_dili/features/auth/application/auth_providers.dart';
import 'package:material_ui/material_ui.dart';

import 'fake_auth_repository.dart';

/// Tüm uygulama; yalnızca SMS doğrulaması ve Firestore sunucusu test
/// sahtesiyle değiştirilir. Depolar gerçek Firestore kodudur.
Widget testApp({
  required FakeAuthRepository auth,
  required FakeFirebaseFirestore db,
  FirebaseStatus firebase = const FirebaseStatus.ready(),
}) => ProviderScope(
  overrides: [
    firebaseStatusProvider.overrideWithValue(firebase),
    authRepositoryProvider.overrideWithValue(auth),
    firestoreProvider.overrideWithValue(db),
  ],
  child: const KusDiliApp(),
);

Future<void> settle(WidgetTester t, [int frames = 10]) async {
  for (var i = 0; i < frames; i++) {
    await t.pump(const Duration(milliseconds: 100));
  }
}
