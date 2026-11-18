import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/settings/settings_controller.dart';
import '../../data/providers.dart';

class LockState {
  const LockState({this.locked = false, this.privateUnlocked = false});

  /// Uygulama kilit ekranı gösteriliyor mu?
  final bool locked;

  /// Gizli hayaller / özel günlükler bu oturumda açıldı mı?
  final bool privateUnlocked;

  LockState copyWith({bool? locked, bool? privateUnlocked}) =>
      LockState(locked: locked ?? this.locked, privateUnlocked: privateUnlocked ?? this.privateUnlocked);
}

/// Başlangıçta kilitli olup olmadığı `main.dart`ta belirlenir.
final initialLockedProvider = Provider<bool>((ref) => false);

final lockProvider = NotifierProvider<LockController, LockState>(LockController.new);

class LockController extends Notifier<LockState> {
  DateTime? _pausedAt;

  @override
  LockState build() => LockState(locked: ref.read(initialLockedProvider));

  void onPaused() => _pausedAt = DateTime.now();

  /// Arka plandan dönüşte zaman aşımı geçtiyse kilitler; gizli içerik
  /// oturumunu her durumda kapatır.
  Future<void> onResumed() async {
    final s = ref.read(settingsProvider);
    final paused = _pausedAt;
    _pausedAt = null;
    if (paused == null) return;
    final elapsed = DateTime.now().difference(paused).inSeconds;
    var next = state;
    if (elapsed >= 5) next = next.copyWith(privateUnlocked: false);
    if (s.lockEnabled && elapsed >= s.lockTimeoutSeconds && await ref.read(lockServiceProvider).hasPin()) {
      next = next.copyWith(locked: true);
    }
    state = next;
  }

  void unlock() => state = state.copyWith(locked: false);
  void lockNow() => state = state.copyWith(locked: true, privateUnlocked: false);
  void setPrivateUnlocked(bool v) => state = state.copyWith(privateUnlocked: v);
}
