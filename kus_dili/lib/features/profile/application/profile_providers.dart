import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/firebase/firebase_providers.dart';
import '../../auth/application/auth_providers.dart';
import '../data/firestore_profile_repository.dart';
import '../domain/user_profile.dart';
import '../domain/username.dart';

final profileRepositoryProvider = Provider<ProfileRepository>(
  (ref) => FirestoreProfileRepository(ref.watch(firestoreProvider)),
);

/// Oturumdaki kullanıcının profili; oturum yoksa `null`.
final currentProfileProvider = StreamProvider<UserProfile?>((ref) {
  final uid = ref.watch(authStateProvider.select((a) => a.value?.uid));
  if (uid == null) return Stream.value(null);
  return ref.watch(profileRepositoryProvider).watchProfile(uid);
});

final class ProfileSetupState {
  const ProfileSetupState({this.busy = false, this.error});
  final bool busy;
  final String? error;
}

class ProfileSetupController extends Notifier<ProfileSetupState> {
  @override
  ProfileSetupState build() => const ProfileSetupState();

  /// Başarılıysa `true`. Profil akışı güncellenince yönlendirici ana
  /// ekrana geçer.
  Future<bool> submit({
    required String username,
    required String displayName,
  }) async {
    if (state.busy) return false;
    final name = Username.normalize(username);
    final error = Username.validate(name) ?? DisplayName.validate(displayName);
    if (error != null) {
      state = ProfileSetupState(error: error);
      return false;
    }
    final uid = ref.read(authRepositoryProvider).currentUser?.uid;
    if (uid == null) {
      state = const ProfileSetupState(error: 'Oturum bulunamadı.');
      return false;
    }
    state = const ProfileSetupState(busy: true);
    try {
      await ref
          .read(profileRepositoryProvider)
          .createProfile(
            uid: uid,
            username: name,
            displayName: displayName.trim(),
          );
      if (ref.mounted) state = const ProfileSetupState();
      return true;
    } on UsernameTakenException {
      if (ref.mounted) {
        state = ProfileSetupState(
          error: '@$name kullanılıyor. Başka bir ad seçin.',
        );
      }
    } on ProfileAlreadyExistsException {
      if (ref.mounted) state = const ProfileSetupState();
      return true;
    } catch (_) {
      if (ref.mounted) {
        state = const ProfileSetupState(
          error:
              'Profil kaydedilemedi. Bağlantınızı kontrol edip tekrar deneyin.',
        );
      }
    }
    return false;
  }
}

final profileSetupControllerProvider =
    NotifierProvider<ProfileSetupController, ProfileSetupState>(
      ProfileSetupController.new,
    );
