import 'dart:convert';

/// Öğrenme ilerlemesi (yalnızca cihazda; hesap gerekmez).
final class LearnProgress {
  const LearnProgress({
    this.xp = 0,
    this.correct = 0,
    this.answered = 0,
    this.completedLessons = const {},
    this.bestQuiz = 0,
    this.streak = 0,
    this.lastActiveDay,
  });

  factory LearnProgress.fromJson(Map<String, Object?> j) => LearnProgress(
    xp: j['xp'] as int? ?? 0,
    correct: j['correct'] as int? ?? 0,
    answered: j['answered'] as int? ?? 0,
    completedLessons: {...?(j['done'] as List?)?.cast<String>()},
    bestQuiz: j['bestQuiz'] as int? ?? 0,
    streak: j['streak'] as int? ?? 0,
    lastActiveDay: j['last'] as String?,
  );

  static const xpPerCorrect = 10;
  static const xpPerLevel = 100;
  static const passRatio = 0.8;

  final int xp;
  final int correct;
  final int answered;
  final Set<String> completedLessons;

  /// En iyi mini test puanı (0-100).
  final int bestQuiz;

  /// Art arda çalışılan gün sayısı.
  final int streak;

  /// yyyy-MM-dd (yerel gün).
  final String? lastActiveDay;

  int get level => xp ~/ xpPerLevel + 1;
  double get levelProgress => (xp % xpPerLevel) / xpPerLevel;
  int get accuracyPercent =>
      answered == 0 ? 0 : (correct * 100 / answered).round();

  static String dayKey(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-'
      '${d.day.toString().padLeft(2, '0')}';

  /// Bir çalışma oturumunu (ders veya test) işler.
  LearnProgress record({
    required int correct,
    required int total,
    required DateTime now,
    String? lessonId,
    bool quiz = false,
  }) {
    final today = dayKey(now);
    final yesterday = dayKey(now.subtract(const Duration(days: 1)));
    final newStreak = lastActiveDay == today
        ? streak
        : lastActiveDay == yesterday
        ? streak + 1
        : 1;
    final passed = total > 0 && correct / total >= passRatio;
    final score = total == 0 ? 0 : (correct * 100 / total).round();
    return LearnProgress(
      xp: xp + correct * xpPerCorrect,
      correct: this.correct + correct,
      answered: answered + total,
      completedLessons: {
        ...completedLessons,
        if (lessonId != null && passed) lessonId,
      },
      bestQuiz: quiz && score > bestQuiz ? score : bestQuiz,
      streak: newStreak,
      lastActiveDay: today,
    );
  }

  /// Bugün veya dün çalışılmadıysa seri sıfır görünür.
  int streakOn(DateTime now) {
    final d = lastActiveDay;
    if (d == null) return 0;
    if (d == dayKey(now) ||
        d == dayKey(now.subtract(const Duration(days: 1)))) {
      return streak;
    }
    return 0;
  }

  Map<String, Object?> toJson() => {
    'xp': xp,
    'correct': correct,
    'answered': answered,
    'done': completedLessons.toList()..sort(),
    'bestQuiz': bestQuiz,
    'streak': streak,
    'last': lastActiveDay,
  };

  String encode() => jsonEncode(toJson());
}

abstract interface class ProgressStore {
  Future<LearnProgress> load();
  Future<void> save(LearnProgress p);
}

/// Seslendirme (TTS). Testlerde sahtesi kullanılır.
abstract interface class Speaker {
  Future<void> speak(String text);
  Future<void> stop();
}
