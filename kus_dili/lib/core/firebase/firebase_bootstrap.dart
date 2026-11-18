import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_app_check/firebase_app_check.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/foundation.dart';

/// Firebase başlatma sonucu.
final class FirebaseStatus {
  const FirebaseStatus.ready() : error = null;
  const FirebaseStatus.unavailable(Object this.error);

  final Object? error;
  bool get isReady => error == null;
}

/// Yerel emülatör: `flutter run --dart-define=USE_FIREBASE_EMULATOR=true`
/// (Android emülatöründe ana makine 10.0.2.2'dir; gerçek cihazda
/// `--dart-define=FIREBASE_EMULATOR_HOST=<bilgisayar IP>`).
const _useEmulator = bool.fromEnvironment('USE_FIREBASE_EMULATOR');
const _emulatorHost = String.fromEnvironment(
  'FIREBASE_EMULATOR_HOST',
  defaultValue: '10.0.2.2',
);

/// Firebase'i yerel yapılandırma dosyalarından (google-services.json /
/// GoogleService-Info.plist) başlatır. Dosyalar yoksa uygulama çökmez;
/// [FirebaseStatus.unavailable] döner ve kurulum ekranı gösterilir.
Future<FirebaseStatus> initFirebase() async {
  try {
    await Firebase.initializeApp();
    await FirebaseAppCheck.instance.activate(
      providerAndroid: kReleaseMode
          ? const AndroidPlayIntegrityProvider()
          : const AndroidDebugProvider(),
      providerApple: kReleaseMode
          ? const AppleAppAttestWithDeviceCheckFallbackProvider()
          : const AppleDebugProvider(),
    );
    if (_useEmulator) {
      await FirebaseAuth.instance.useAuthEmulator(_emulatorHost, 9099);
      FirebaseFirestore.instance.useFirestoreEmulator(_emulatorHost, 8080);
    }
    return const FirebaseStatus.ready();
  } catch (e, st) {
    debugPrint('Firebase başlatılamadı: $e\n$st');
    return FirebaseStatus.unavailable(e);
  }
}
