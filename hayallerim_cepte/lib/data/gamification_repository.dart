import 'dart:async';

import 'package:drift/drift.dart';

import '../core/database/app_database.dart';
import '../core/logic/achievements_catalog.dart';
import '../core/logic/progress_logic.dart';
import '../core/utils/dates.dart';

/// Puan defteri (ActivityLog), rozetler ve başarı geçmişi.
class GamificationRepository {
  GamificationRepository(this.db);

  final AppDatabase db;
  final _unlocked = StreamController<AchievementDef>.broadcast();

  /// Yeni açılan rozet olayları (arayüzde kutlama göstermek için).
  Stream<AchievementDef> get unlockedEvents => _unlocked.stream;

  Future<List<AchievementDef>> award(String type, int? refId, String title, int points) async {
    await db.into(db.activityLog).insert(ActivityLogCompanion.insert(
          type: type,
          refId: Value(refId),
          title: title,
          points: Value(points),
        ));
    return evaluate();
  }

  /// Geri alınan işlemin (ör. görev işaretinin kaldırılması) puanını siler.
  Future<void> revoke(String type, int refId) async {
    final last = await (db.select(db.activityLog)
          ..where((a) => a.type.equals(type) & a.refId.equals(refId))
          ..orderBy([(a) => OrderingTerm.desc(a.createdAt)])
          ..limit(1))
        .getSingleOrNull();
    if (last != null) {
      await (db.delete(db.activityLog)..where((a) => a.id.equals(last.id))).go();
    }
  }

  Future<int> _count(String sql, [List<Variable> args = const []]) async {
    final r = await db.customSelect(sql, variables: args).getSingle();
    return (r.data.values.first as int?) ?? 0;
  }

  Future<GameStats> stats() async {
    final habits = await db.select(db.habits).get();
    var bestHabit = 0;
    for (final h in habits) {
      final days = await (db.select(db.habitCompletions)..where((c) => c.habitId.equals(h.id))).get();
      final b = ProgressLogic.bestStreak(days.map((d) => Dates.dayOf(d.day)).toSet(), h.weekdaysMask);
      if (b > bestHabit) bestHabit = b;
    }
    final activity = await db.customSelect('SELECT DISTINCT created_at FROM activity_log').get();
    final activeDays = activity
        .map((r) => Dates.dayOf(DateTime.fromMillisecondsSinceEpoch((r.data['created_at'] as int) * 1000)))
        .toSet();
    return GameStats(
      dreams: await _count('SELECT COUNT(*) FROM dreams'),
      completedDreams: await _count('SELECT COUNT(*) FROM dreams WHERE is_completed = 1'),
      completedMilestones: await _count('SELECT COUNT(*) FROM dream_milestones WHERE is_completed = 1'),
      completedTasks: await _count("SELECT COUNT(*) FROM activity_log WHERE type = 'task'"),
      bestHabitStreak: bestHabit,
      activityStreak: ProgressLogic.activityStreak(activeDays),
      depositCount: await _count("SELECT COUNT(*) FROM savings_transactions WHERE kind = 'deposit'"),
      completedSavings: await _count('SELECT COUNT(*) FROM savings_goals WHERE is_completed = 1'),
      journalCount: await _count('SELECT COUNT(*) FROM journal_entries'),
      focusMinutes: await _count("SELECT COALESCE(SUM(actual_seconds),0)/60 FROM focus_sessions WHERE kind = 'work'"),
      capsules: await _count('SELECT COUNT(*) FROM time_capsules'),
      balanceEntries: await _count('SELECT COUNT(*) FROM life_balance_entries'),
      badges: await _count('SELECT COUNT(*) FROM achievements'),
    );
  }

  /// Koşulu sağlanan ama henüz açılmamış rozetleri açar.
  Future<List<AchievementDef>> evaluate() async {
    final s = await stats();
    final have = (await db.select(db.achievements).get()).map((a) => a.code).toSet();
    final fresh = <AchievementDef>[];
    for (final def in kAchievements) {
      if (have.contains(def.code)) continue;
      if (def.check(s)) {
        await db.into(db.achievements).insert(
              AchievementsCompanion.insert(code: def.code),
              mode: InsertMode.insertOrIgnore,
            );
        await db.into(db.activityLog).insert(ActivityLogCompanion.insert(
              type: 'badge',
              title: 'Rozet: ${def.title}',
              points: Value(def.points),
            ));
        fresh.add(def);
        _unlocked.add(def);
      }
    }
    return fresh;
  }

  Stream<int> watchPoints() {
    final sum = db.activityLog.points.sum();
    return (db.selectOnly(db.activityLog)..addColumns([sum])).map((r) => r.read(sum) ?? 0).watchSingle();
  }

  Stream<List<Achievement>> watchUnlocked() => db.select(db.achievements).watch();

  Stream<List<ActivityLogEntry>> watchHistory({int limit = 100}) => (db.select(db.activityLog)
        ..orderBy([(a) => OrderingTerm.desc(a.createdAt)])
        ..limit(limit))
      .watch();

  /// Son 7 günde gün başına tamamlanan iş sayısı (ana sayfa grafiği).
  Stream<List<int>> watchWeeklyCounts() {
    final start = Dates.today().subtract(const Duration(days: 6));
    return (db.select(db.activityLog)..where((a) => a.createdAt.isBiggerOrEqualValue(start) & a.type.isNotValue('badge')))
        .watch()
        .map((rows) {
      final out = List<int>.filled(7, 0);
      for (final r in rows) {
        final i = Dates.daysBetween(start, r.createdAt);
        if (i >= 0 && i < 7) out[i]++;
      }
      return out;
    });
  }

  Stream<Set<DateTime>> watchActiveDays() => db
      .select(db.activityLog)
      .watch()
      .map((rows) => rows.where((r) => r.type != 'badge').map((r) => Dates.dayOf(r.createdAt)).toSet());

  void dispose() => _unlocked.close();
}
