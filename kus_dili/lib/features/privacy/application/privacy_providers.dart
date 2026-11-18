import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../chat/application/chat_providers.dart';
import '../../crypto/application/crypto_providers.dart';
import '../data/local_auth_biometric.dart';
import '../data/secure_screen.dart';
import '../domain/biometric_auth.dart';
import '../domain/local_privacy_prefs.dart';
import '../domain/pin_vault.dart';

final biometricAuthProvider = Provider<BiometricAuth>(
  (ref) => LocalAuthBiometric(),
);

final pinVaultProvider = Provider<PinVault?>((ref) {
  final uid = ref.watch(myUidProvider);
  return uid == null ? null : PinVault(ref.watch(secureKvProvider), uid);
});

final hasPinProvider = FutureProvider<bool>(
  (ref) async => await ref.watch(pinVaultProvider)?.hasPin() ?? false,
);

final biometricAvailableProvider = FutureProvider<bool>(
  (ref) => ref.watch(biometricAuthProvider).isAvailable(),
);

class PrivacyPrefsController extends AsyncNotifier<PrivacyPrefs> {
  PrivacyPrefsStore? get _store {
    final uid = ref.read(myUidProvider);
    return uid == null
        ? null
        : PrivacyPrefsStore(ref.read(secureKvProvider), uid);
  }

  @override
  Future<PrivacyPrefs> build() async {
    final uid = ref.watch(myUidProvider);
    if (uid == null) return const PrivacyPrefs();
    return PrivacyPrefsStore(ref.watch(secureKvProvider), uid).load();
  }

  Future<void> _save(PrivacyPrefs p) async {
    state = AsyncData(p);
    await _store?.save(p);
  }

  PrivacyPrefs get _cur => state.value ?? const PrivacyPrefs();

  Future<void> setHidden(String chatId, bool on) => _save(
    _cur.copyWith(
      hiddenChats: on
          ? {..._cur.hiddenChats, chatId}
          : ({..._cur.hiddenChats}..remove(chatId)),
    ),
  );

  Future<void> setLocked(String chatId, bool on) => _save(
    _cur.copyWith(
      lockedChats: on
          ? {..._cur.lockedChats, chatId}
          : ({..._cur.lockedChats}..remove(chatId)),
    ),
  );

  Future<void> setAppLock(bool on) => _save(_cur.copyWith(appLock: on));

  Future<void> setBiometrics(bool on) => _save(_cur.copyWith(biometrics: on));

  Future<void> setBlockScreenshots(bool on) =>
      _save(_cur.copyWith(blockScreenshots: on));

  /// PIN kaldırılınca PIN'e bağlı tüm kilitler de kalkar (aksi hâlde
  /// açılamayan sohbetler oluşur).
  Future<void> clearLocks() =>
      _save(_cur.copyWith(lockedChats: {}, hiddenChats: {}, appLock: false));
}

final privacyPrefsProvider =
    AsyncNotifierProvider<PrivacyPrefsController, PrivacyPrefs>(
      PrivacyPrefsController.new,
    );

final class UnlockState {
  const UnlockState({
    this.app = false,
    this.chats = const {},
    this.hiddenRevealed = false,
  });

  final bool app;
  final Set<String> chats;
  final bool hiddenRevealed;

  UnlockState copyWith({bool? app, Set<String>? chats, bool? hiddenRevealed}) =>
      UnlockState(
        app: app ?? this.app,
        chats: chats ?? this.chats,
        hiddenRevealed: hiddenRevealed ?? this.hiddenRevealed,
      );
}

/// Bu oturumda açılmış kilitler. Uygulama arka plana geçince hepsi kapanır.
class UnlockController extends Notifier<UnlockState> {
  @override
  UnlockState build() {
    ref.watch(myUidProvider); // kullanıcı değişince sıfırla
    return const UnlockState();
  }

  void unlockApp() => state = state.copyWith(app: true);

  void unlockChat(String id) =>
      state = state.copyWith(chats: {...state.chats, id});

  void revealHidden() => state = state.copyWith(hiddenRevealed: true);

  void lockAll() => state = const UnlockState();
}

final unlockProvider = NotifierProvider<UnlockController, UnlockState>(
  UnlockController.new,
);

final secureScreenProvider = Provider<SecureScreen>(
  (ref) => PlatformSecureScreen(),
);

/// Şu an ekranda gizli içerik gösteren bileşen sayısı.
class SecureContentCounter extends Notifier<int> {
  @override
  int build() => 0;
  // Ekran kapanırken sağlayıcı zaten yok edilmiş olabilir.
  void enter() {
    if (ref.mounted) state = state + 1;
  }

  void leave() {
    if (ref.mounted) state = state > 0 ? state - 1 : 0;
  }
}

final secureContentProvider = NotifierProvider<SecureContentCounter, int>(
  SecureContentCounter.new,
);

/// Ekran görüntüsü engeli açık olmalı mı?
final screenSecureProvider = Provider<bool>((ref) {
  final prefs = ref.watch(privacyPrefsProvider).value;
  return (prefs?.blockScreenshots ?? false) ||
      ref.watch(secureContentProvider) > 0;
});
