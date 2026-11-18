import 'package:drift/drift.dart';

import '../core/database/app_database.dart';
import '../core/logic/achievements_catalog.dart';
import '../core/logic/progress_logic.dart';
import '../core/utils/dates.dart';
import 'gamification_repository.dart';
import 'reminder_repository.dart';

class HabitStats {
  HabitStats(this.habit, this.days);
  final Habit habit;
  final Set<DateTime> days;

  bool doneOn(DateTime d) => days.contains(Dates.dayOf(d));
  int get streak => ProgressLogic.habitStreak(days, habit.weekdaysMask);
  int get best => ProgressLogic.bestStreak(days, habit.weekdaysMask);

  /// Başlangıçtan bugüne planlanan günlerdeki tamamlanma oranı.
  double get rate {
    final start = Dates.dayOf(habit.startDate);
    final today = Dates.today();
    var planned = 0;
    var done = 0;
    for (var d = start; !d.isAfter(today); d = d.add(const Duration(days: 1))) {
      if (!Dates.maskHas(habit.weekdaysMask, d)) continue;
      planned++;
      if (days.contains(d)) done++;
    }
    return planned == 0 ? 0 : done / planned;
  }

  /// Meydan okuma ilerlemesi (başlangıçtan beri yapılan gün / hedef gün).
  double? get challengeProgress {
    final c = habit.challengeDays;
    if (c == null || c <= 0) return null;
    final count = days.where((d) => !d.isBefore(Dates.dayOf(habit.startDate))).length;
    return (count / c).clamp(0, 1).toDouble();
  }
}

class HabitRepository {
  HabitRepository(this.db, this.game, this.reminders);

  final AppDatabase db;
  final GamificationRepository game;
  final ReminderRepository reminders;

  Stream<List<Habit>> watchHabits() =>
      (db.select(db.habits)..where((h) => h.archivedAt.isNull())..orderBy([(h) => OrderingTerm.asc(h.createdAt)])).watch();

  Stream<List<HabitCompletion>> watchCompletions() => db.select(db.habitCompletions).watch();

  Future<int> createHabit({
    required String title,
    required String iconKey,
    required int color,
    int weekdaysMask = 127,
    int? reminderMinutes,
    int? challengeDays,
    int? dreamId,
  }) async {
    final id = await db.into(db.habits).insert(HabitsCompanion.insert(
          title: title.trim(),
          iconKey: Value(iconKey),
          color: color,
          weekdaysMask: Value(weekdaysMask),
          reminderMinutes: Value(reminderMinutes),
          challengeDays: Value(challengeDays),
          dreamId: Value(dreamId),
          startDate: Value(Dates.today()),
        ));
    final h = await (db.select(db.habits)..where((x) => x.id.equals(id))).getSingle();
    await _syncReminder(h);
    return id;
  }

  Future<void> updateHabit(Habit h) async {
    await db.update(db.habits).replace(h);
    await _syncReminder(h);
  }

  Future<void> archiveHabit(Habit h) async {
    await (db.update(db.habits)..where((x) => x.id.equals(h.id))).write(HabitsCompanion(archivedAt: Value(DateTime.now())));
    await reminders.removeFor('habit', h.id);
  }

  Future<void> deleteHabit(Habit h) async {
    await (db.delete(db.habits)..where((x) => x.id.equals(h.id))).go();
    await reminders.removeFor('habit', h.id);
  }

  Future<void> toggleDay(Habit h, DateTime day) async {
    final d = Dates.dayOf(day);
    if (d.isAfter(Dates.today())) return; // gelecek gün işaretlenemez
    final existing = await (db.select(db.habitCompletions)
          ..where((c) => c.habitId.equals(h.id) & c.day.equals(d)))
        .getSingleOrNull();
    if (existing != null) {
      await (db.delete(db.habitCompletions)..where((c) => c.id.equals(existing.id))).go();
      await game.revoke('habit', h.id);
    } else {
      await db.into(db.habitCompletions).insert(
            HabitCompletionsCompanion.insert(habitId: h.id, day: d),
            mode: InsertMode.insertOrIgnore,
          );
      await game.award('habit', h.id, 'Alışkanlık: ${h.title}', Points.habit);
    }
  }

  Future<void> _syncReminder(Habit h) async {
    if (h.reminderMinutes == null || h.archivedAt != null) {
      await reminders.removeFor('habit', h.id);
      return;
    }
    final t = Dates.today().add(Duration(minutes: h.reminderMinutes!));
    await reminders.upsertFor(
      kind: 'habit',
      refId: h.id,
      title: 'Alışkanlık zamanı: ${h.title}',
      body: 'Serini korumak için bugün de işaretle 🔥',
      at: t,
      repeat: h.weekdaysMask == 127 ? 'daily' : 'weekly',
      weekdaysMask: h.weekdaysMask == 127 ? null : h.weekdaysMask,
    );
  }
}
