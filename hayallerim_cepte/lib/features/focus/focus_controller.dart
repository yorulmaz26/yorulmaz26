import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../core/providers.dart';
import '../../core/settings/settings_controller.dart';
import '../../data/providers.dart';

enum FocusPhase { idle, running, paused }

class FocusState {
  const FocusState({
    this.phase = FocusPhase.idle,
    this.kind = 'work',
    this.plannedSeconds = 25 * 60,
    this.remainingSeconds = 25 * 60,
    this.startedAt,
    this.endAt,
    this.goalId,
    this.dreamId,
    this.completedWorkSessions = 0,
    this.justFinished,
  });

  final FocusPhase phase;
  final String kind; // work | break
  final int plannedSeconds;
  final int remainingSeconds;
  final DateTime? startedAt;
  final DateTime? endAt;
  final int? goalId;
  final int? dreamId;
  final int completedWorkSessions;

  /// Az önce biten oturumun türü (arayüzde bir kez kutlama için).
  final String? justFinished;

  double get progress => plannedSeconds == 0 ? 0 : 1 - remainingSeconds / plannedSeconds;

  FocusState copyWith({
    FocusPhase? phase,
    String? kind,
    int? plannedSeconds,
    int? remainingSeconds,
    DateTime? startedAt,
    DateTime? endAt,
    bool clearEnd = false,
    int? goalId,
    int? dreamId,
    bool clearLinks = false,
    int? completedWorkSessions,
    String? justFinished,
    bool clearFinished = false,
  }) =>
      FocusState(
        phase: phase ?? this.phase,
        kind: kind ?? this.kind,
        plannedSeconds: plannedSeconds ?? this.plannedSeconds,
        remainingSeconds: remainingSeconds ?? this.remainingSeconds,
        startedAt: startedAt ?? this.startedAt,
        endAt: clearEnd ? null : (endAt ?? this.endAt),
        goalId: clearLinks ? null : (goalId ?? this.goalId),
        dreamId: clearLinks ? null : (dreamId ?? this.dreamId),
        completedWorkSessions: completedWorkSessions ?? this.completedWorkSessions,
        justFinished: clearFinished ? null : (justFinished ?? this.justFinished),
      );
}

/// Pomodoro zamanlayıcısı.
///
/// Süre sayaçla değil, kaydedilen **bitiş zamanı** ile hesaplanır; bu yüzden
/// uygulama arka plana alınsa ya da işletim sistemi tarafından kapatılsa bile
/// geri dönüldüğünde kalan süre doğrudur. Bitişte yerel bildirim planlanır.
class FocusController extends Notifier<FocusState> {
  Timer? _ticker;

  static const _kEnd = 'focus_end_at';
  static const _kStart = 'focus_started_at';
  static const _kPlanned = 'focus_planned';
  static const _kKind = 'focus_kind';
  static const _kRemaining = 'focus_paused_remaining';
  static const _kGoal = 'focus_goal';
  static const _kDream = 'focus_dream';

  SharedPreferences get _prefs => ref.read(prefsProvider);

  @override
  FocusState build() {
    ref.onDispose(() => _ticker?.cancel());
    final s = ref.read(settingsProvider);
    var st = FocusState(plannedSeconds: s.focusWork * 60, remainingSeconds: s.focusWork * 60);
    final p = _prefs;
    final end = p.getInt(_kEnd);
    final paused = p.getInt(_kRemaining);
    final planned = p.getInt(_kPlanned);
    if (planned != null && (end != null || paused != null)) {
      st = st.copyWith(
        kind: p.getString(_kKind) ?? 'work',
        plannedSeconds: planned,
        startedAt: DateTime.fromMillisecondsSinceEpoch(p.getInt(_kStart) ?? DateTime.now().millisecondsSinceEpoch),
        goalId: p.getInt(_kGoal),
        dreamId: p.getInt(_kDream),
      );
      if (end != null) {
        final endAt = DateTime.fromMillisecondsSinceEpoch(end);
        st = st.copyWith(phase: FocusPhase.running, endAt: endAt, remainingSeconds: _remaining(endAt));
        Future.microtask(_startTicker);
      } else {
        st = st.copyWith(phase: FocusPhase.paused, remainingSeconds: paused);
      }
    }
    return st;
  }

  int _remaining(DateTime endAt) {
    final r = endAt.difference(DateTime.now()).inMilliseconds;
    return r <= 0 ? 0 : (r / 1000).ceil();
  }

  void _startTicker() {
    _ticker?.cancel();
    _ticker = Timer.periodic(const Duration(seconds: 1), (_) => tick());
    tick();
  }

  /// Her saniye ve uygulamaya dönüldüğünde çağrılır.
  void tick() {
    if (state.phase != FocusPhase.running || state.endAt == null) return;
    final r = _remaining(state.endAt!);
    if (r <= 0) {
      _finish(completed: true);
    } else if (r != state.remainingSeconds) {
      state = state.copyWith(remainingSeconds: r);
    }
  }

  void configure({required String kind, required int minutes, int? goalId, int? dreamId}) {
    if (state.phase != FocusPhase.idle) return;
    state = state.copyWith(
      kind: kind,
      plannedSeconds: minutes * 60,
      remainingSeconds: minutes * 60,
      goalId: goalId,
      dreamId: dreamId,
      clearLinks: goalId == null && dreamId == null,
      clearFinished: true,
    );
  }

  Future<void> start() async {
    final now = DateTime.now();
    final end = now.add(Duration(seconds: state.remainingSeconds));
    final fresh = state.phase == FocusPhase.idle;
    state = state.copyWith(phase: FocusPhase.running, endAt: end, startedAt: fresh ? now : state.startedAt, clearFinished: true);
    await _persist();
    await ref.read(notificationsProvider).scheduleFocusEnd(
          end,
          state.kind == 'work' ? 'Odak oturumu tamamlandı 🎯' : 'Mola bitti ☕',
          state.kind == 'work' ? 'Harika iş! Şimdi kısa bir mola ver.' : 'Yeni bir odak oturumuna hazır mısın?',
        );
    _startTicker();
  }

  Future<void> pause() async {
    if (state.phase != FocusPhase.running) return;
    _ticker?.cancel();
    state = state.copyWith(phase: FocusPhase.paused, remainingSeconds: _remaining(state.endAt!), clearEnd: true);
    await _persist();
    await ref.read(notificationsProvider).cancelFocus();
  }

  /// Oturumu erken bitirir (gerçekleşen süre kaydedilir).
  Future<void> stop() async => _finish(completed: false);

  Future<void> _finish({required bool completed}) async {
    _ticker?.cancel();
    final s = state;
    if (s.phase == FocusPhase.idle) return;
    final now = DateTime.now();
    final actual = (s.plannedSeconds - (completed ? 0 : s.remainingSeconds)).clamp(0, s.plannedSeconds);
    await ref.read(focusRepoProvider).record(
          startedAt: s.startedAt ?? now,
          endedAt: now,
          plannedSeconds: s.plannedSeconds,
          actualSeconds: actual,
          kind: s.kind,
          completed: completed,
          goalId: s.goalId,
          dreamId: s.dreamId,
        );
    if (!completed) await ref.read(notificationsProvider).cancelFocus();
    final settings = ref.read(settingsProvider);
    final sessions = s.kind == 'work' && completed ? s.completedWorkSessions + 1 : s.completedWorkSessions;
    // Tamamlanan çalışmadan sonra mola, moladan sonra çalışma önerilir.
    final nextKind = completed ? (s.kind == 'work' ? 'break' : 'work') : s.kind;
    final longBreak = nextKind == 'break' && sessions > 0 && sessions % 4 == 0;
    final minutes = nextKind == 'work' ? settings.focusWork : (longBreak ? settings.focusLongBreak : settings.focusBreak);
    state = FocusState(
      kind: nextKind,
      plannedSeconds: minutes * 60,
      remainingSeconds: minutes * 60,
      goalId: s.goalId,
      dreamId: s.dreamId,
      completedWorkSessions: sessions,
      justFinished: completed ? s.kind : null,
    );
    await _clearPersisted();
  }

  void reset() {
    if (state.phase != FocusPhase.idle) return;
    final s = ref.read(settingsProvider);
    final m = state.kind == 'work' ? s.focusWork : s.focusBreak;
    state = state.copyWith(plannedSeconds: m * 60, remainingSeconds: m * 60, clearFinished: true);
  }

  void consumeFinished() => state = state.copyWith(clearFinished: true);

  Future<void> _persist() async {
    final p = _prefs;
    await p.setInt(_kPlanned, state.plannedSeconds);
    await p.setString(_kKind, state.kind);
    await p.setInt(_kStart, (state.startedAt ?? DateTime.now()).millisecondsSinceEpoch);
    if (state.goalId != null) {
      await p.setInt(_kGoal, state.goalId!);
    } else {
      await p.remove(_kGoal);
    }
    if (state.dreamId != null) {
      await p.setInt(_kDream, state.dreamId!);
    } else {
      await p.remove(_kDream);
    }
    if (state.phase == FocusPhase.running) {
      await p.setInt(_kEnd, state.endAt!.millisecondsSinceEpoch);
      await p.remove(_kRemaining);
    } else {
      await p.setInt(_kRemaining, state.remainingSeconds);
      await p.remove(_kEnd);
    }
  }

  Future<void> _clearPersisted() async {
    for (final k in [_kEnd, _kStart, _kPlanned, _kKind, _kRemaining, _kGoal, _kDream]) {
      await _prefs.remove(k);
    }
  }
}

final focusProvider = NotifierProvider<FocusController, FocusState>(FocusController.new);
