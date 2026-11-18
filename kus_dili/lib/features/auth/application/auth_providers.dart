import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/firebase/firebase_providers.dart';
import '../../profile/application/profile_providers.dart';
import '../data/firebase_auth_repository.dart';
import '../domain/auth_repository.dart';

final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => FirebaseAuthRepository(ref.watch(firebaseAuthProvider)),
);

final authStateProvider = StreamProvider<AuthUser?>(
  (ref) => ref.watch(authRepositoryProvider).authStateChanges(),
);

/// Uygulamanın hangi bölüme yönlendirileceğini belirleyen oturum durumu.
enum SessionStatus {
  /// Firebase yapılandırılmamış / başlatılamadı.
  firebaseUnavailable,

  /// Oturum veya profil bilgisi yükleniyor.
  loading,

  /// Profil okunamadı (ör. izin hatası).
  error,

  signedOut,
  needsProfile,
  ready,
}

final sessionStatusProvider = Provider<SessionStatus>((ref) {
  if (!ref.watch(firebaseStatusProvider).isReady) {
    return SessionStatus.firebaseUnavailable;
  }
  final auth = ref.watch(authStateProvider);
  if (!auth.hasValue) {
    return auth.hasError ? SessionStatus.error : SessionStatus.loading;
  }
  if (auth.value == null) return SessionStatus.signedOut;
  final profile = ref.watch(currentProfileProvider);
  if (!profile.hasValue) {
    return profile.hasError ? SessionStatus.error : SessionStatus.loading;
  }
  return profile.value == null
      ? SessionStatus.needsProfile
      : SessionStatus.ready;
});
