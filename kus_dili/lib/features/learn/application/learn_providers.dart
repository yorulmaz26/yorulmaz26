import 'dart:math';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/prefs_progress_store.dart';
import '../data/tts_speaker.dart';
import '../domain/progress.dart';

final progressStoreProvider = Provider<ProgressStore>(
  (ref) => PrefsProgressStore(),
);

final speakerProvider = Provider<Speaker>((ref) => TtsSpeaker());

/// Testlerde sabit tohumla değiştirilir.
final learnRandomProvider = Provider<Random>((ref) => Random());

final learnClockProvider = Provider<DateTime Function()>((ref) => DateTime.now);

class ProgressController extends AsyncNotifier<LearnProgress> {
  @override
  Future<LearnProgress> build() => ref.watch(progressStoreProvider).load();

  Future<LearnProgress> record({
    required int correct,
    required int total,
    String? lessonId,
    bool quiz = false,
  }) async {
    final cur = state.value ?? await future;
    final next = cur.record(
      correct: correct,
      total: total,
      now: ref.read(learnClockProvider)(),
      lessonId: lessonId,
      quiz: quiz,
    );
    state = AsyncData(next);
    await ref.read(progressStoreProvider).save(next);
    return next;
  }
}

final progressProvider =
    AsyncNotifierProvider<ProgressController, LearnProgress>(
      ProgressController.new,
    );
