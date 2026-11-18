import 'dart:math';

import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/app/app.dart';
import 'package:kus_dili/features/account/application/account_providers.dart';
import 'package:kus_dili/features/account/domain/account_service.dart';
import 'package:kus_dili/core/firebase/firebase_bootstrap.dart';
import 'package:kus_dili/core/firebase/firebase_providers.dart';
import 'package:kus_dili/features/auth/application/auth_providers.dart';
import 'package:kus_dili/features/chat/application/chat_providers.dart';
import 'package:kus_dili/features/crypto/application/crypto_providers.dart';
import 'package:kus_dili/features/learn/application/learn_providers.dart';
import 'package:kus_dili/features/notifications/application/push_providers.dart';
import 'package:kus_dili/features/privacy/application/privacy_providers.dart';
import 'package:kus_dili/features/privacy/data/secure_screen.dart';
import 'package:kus_dili/features/privacy/domain/biometric_auth.dart';
import 'package:kus_dili/features/privacy/domain/pin_vault.dart';
import 'package:material_ui/material_ui.dart';

import 'fake_auth_repository.dart';
import 'fake_learn.dart';
import 'fake_push.dart';
import 'in_memory.dart';

/// Tüm uygulama; yalnızca SMS doğrulaması ve Firestore sunucusu test
/// sahtesiyle değiştirilir. Depolar gerçek Firestore kodudur.
Widget testApp({
  required FakeAuthRepository auth,
  required FakeFirebaseFirestore db,
  FirebaseStatus firebase = const FirebaseStatus.ready(),
  InMemorySecureKv? kv,
  InMemoryPayloadCache? cache,
  bool biometrics = false,
  FakePushMessaging? push,
  FakeSpeaker? speaker,
  InMemoryProgressStore? progress,
  FakeSecureScreen? secureScreen,
  FakeAccountService? account,
}) => ProviderScope(
  overrides: [
    accountServiceProvider.overrideWithValue(account ?? FakeAccountService()),
    secureScreenProvider.overrideWithValue(secureScreen ?? FakeSecureScreen()),
    speakerProvider.overrideWithValue(speaker ?? FakeSpeaker()),
    progressStoreProvider.overrideWithValue(
      progress ?? InMemoryProgressStore(),
    ),
    learnRandomProvider.overrideWithValue(Random(3)),
    pushMessagingProvider.overrideWithValue(push ?? FakePushMessaging()),
    // PBKDF2 tur sayısı testte düşük; uygulamada 210.000.
    pinVaultProvider.overrideWith((ref) {
      final uid = ref.watch(myUidProvider);
      return uid == null
          ? null
          : PinVault(ref.watch(secureKvProvider), uid, iterations: 1000);
    }),
    biometricAuthProvider.overrideWithValue(FakeBiometric(biometrics)),
    firebaseStatusProvider.overrideWithValue(firebase),
    authRepositoryProvider.overrideWithValue(auth),
    firestoreProvider.overrideWithValue(db),
    secureKvProvider.overrideWithValue(kv ?? InMemorySecureKv()),
    payloadCacheProvider.overrideWithValue(cache ?? InMemoryPayloadCache()),
  ],
  child: const KusDiliApp(),
);

Future<void> settle(WidgetTester t, [int frames = 10]) async {
  for (var i = 0; i < frames; i++) {
    await t.pump(const Duration(milliseconds: 100));
  }
}

/// YALNIZCA TESTLER: biyometri sahtesi.
class FakeBiometric implements BiometricAuth {
  FakeBiometric(this.available, {this.succeed = true});

  final bool available;
  bool succeed;
  int calls = 0;

  @override
  Future<bool> isAvailable() async => available;

  @override
  Future<bool> authenticate(String reason) async {
    calls++;
    return available && succeed;
  }
}

/// Telefon oranında uzun test ekranı (ayar sayfaları kaydırmasız sığsın).
void tallScreen(WidgetTester t) {
  t.view.physicalSize = const Size(1080, 2340);
  t.view.devicePixelRatio = 2.25;
  addTearDown(t.view.reset);
}

/// YALNIZCA TESTLER: FLAG_SECURE sahtesi.
class FakeSecureScreen implements SecureScreen {
  final List<bool> calls = [];
  bool get secure => calls.isNotEmpty && calls.last;

  @override
  Future<void> setSecure(bool secure) async => calls.add(secure);
}

/// YALNIZCA TESTLER: hesap silme sahtesi.
class FakeAccountService implements AccountService {
  FakeAccountService({this.fail = false});
  bool fail;
  int calls = 0;

  @override
  Future<void> deleteAccount() async {
    calls++;
    if (fail) throw Exception('sunucu hatası');
  }
}
