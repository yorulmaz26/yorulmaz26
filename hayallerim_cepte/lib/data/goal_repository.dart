import 'package:drift/drift.dart';

import '../core/database/app_database.dart';
import '../core/logic/achievements_catalog.dart';
import '../core/utils/dates.dart';
import 'gamification_repository.dart';
import 'reminder_repository.dart';

/// Hedefler ve görevler. Tekrarlanan görev tamamlandığında başarı geçmişine
/// yazılır ve bir sonraki tarihe ertelenir.
class GoalRepository {
  GoalRepository(this.db, this.game, this.reminders);

  final AppDatabase db;
  final GamificationRepository game;
  final ReminderRepository reminders;

  Stream<List<Goal>> watchGoals({String? period, bool includeArchived = false}) {
    final q = db.select(db.goals)
      ..where((g) {
        Expression<bool> e = includeArchived ? const Constant(true) : g.archivedAt.isNull();
        if (period != null) e = e & g.period.equals(period);
        return e;
      })
      ..orderBy([(g) => OrderingTerm.asc(g.isCompleted), (g) => OrderingTerm.desc(g.priority), (g) => OrderingTerm.asc(g.dueDate)]);
    return q.watch();
  }

  Stream<Goal?> watchGoal(int id) => (db.select(db.goals)..where((g) => g.id.equals(id))).watchSingleOrNull();

  Stream<List<TaskItem>> watchTasks() =>
      (db.select(db.tasks)..orderBy([(t) => OrderingTerm.asc(t.isCompleted), (t) => OrderingTerm.asc(t.dueDate)])).watch();

  Stream<List<TaskItem>> watchTasksForGoal(int goalId) => (db.select(db.tasks)
        ..where((t) => t.goalId.equals(goalId))
        ..orderBy([(t) => OrderingTerm.asc(t.isCompleted), (t) => OrderingTerm.asc(t.dueDate)]))
      .watch();

  Stream<List<Goal>> watchGoalsForDream(int dreamId) =>
      (db.select(db.goals)..where((g) => g.dreamId.equals(dreamId) & g.archivedAt.isNull())).watch();

  Stream<List<TaskItem>> watchTasksForDream(int dreamId) =>
      (db.select(db.tasks)..where((t) => t.dreamId.equals(dreamId))).watch();

  Future<int> createGoal({
    required String title,
    String description = '',
    required String period,
    int? dreamId,
    int priority = 1,
    DateTime? dueDate,
  }) async {
    final id = await db.into(db.goals).insert(GoalsCompanion.insert(
          title: title.trim(),
          description: Value(description),
          period: Value(period),
          dreamId: Value(dreamId),
          priority: Value(priority),
          dueDate: Value(dueDate ?? defaultDue(period)),
        ));
    await _syncGoalReminder(id, title, dueDate ?? defaultDue(period));
    return id;
  }

  static DateTime defaultDue(String period, [DateTime? from]) {
    final t = Dates.dayOf(from ?? DateTime.now());
    return switch (period) {
      'daily' => t,
      'weekly' => Dates.startOfWeek(t).add(const Duration(days: 6)),
      'monthly' => DateTime(t.year, t.month + 1, 0),
      _ => DateTime(t.year, 12, 31),
    };
  }

  Future<void> updateGoal(Goal g) async {
    await db.update(db.goals).replace(g);
    await _syncGoalReminder(g.id, g.title, g.isCompleted ? null : g.dueDate);
  }

  Future<void> setGoalCompleted(Goal g, bool done) async {
    await (db.update(db.goals)..where((x) => x.id.equals(g.id))).write(GoalsCompanion(
      isCompleted: Value(done),
      completedAt: Value(done ? DateTime.now() : null),
    ));
    if (done) {
      await game.award('goal', g.id, 'Hedef tamamlandı: ${g.title}', Points.goal);
      await reminders.removeFor('goal', g.id);
    } else {
      await game.revoke('goal', g.id);
      await _syncGoalReminder(g.id, g.title, g.dueDate);
    }
  }

  Future<void> deleteGoal(Goal g) async {
    final ts = await (db.select(db.tasks)..where((t) => t.goalId.equals(g.id))).get();
    await (db.delete(db.goals)..where((x) => x.id.equals(g.id))).go();
    for (final t in ts) {
      await reminders.removeFor('task', t.id, resyncNow: false);
    }
    await reminders.removeFor('goal', g.id);
  }

  Future<void> archiveGoal(Goal g) async {
    await (db.update(db.goals)..where((x) => x.id.equals(g.id))).write(GoalsCompanion(archivedAt: Value(DateTime.now())));
    await reminders.removeFor('goal', g.id);
  }

  /// Hedefin tamamlanma oranı: alt görevlere göre (yoksa tamamlandı durumu).
  static double goalProgress(Goal g, List<TaskItem> tasks) {
    if (g.isCompleted) return 1;
    final own = tasks.where((t) => t.goalId == g.id && t.repeatRule == 'none').toList();
    if (own.isEmpty) return 0;
    return own.where((t) => t.isCompleted).length / own.length;
  }

  // ------------------------------ Görevler -------------------------------
  Future<int> createTask({
    required String title,
    String note = '',
    int? goalId,
    int? dreamId,
    DateTime? dueDate,
    int? dueMinutes,
    int priority = 1,
    String repeatRule = 'none',
    bool reminder = false,
  }) async {
    var dream = dreamId;
    if (dream == null && goalId != null) {
      final g = await (db.select(db.goals)..where((x) => x.id.equals(goalId))).getSingleOrNull();
      dream = g?.dreamId;
    }
    final id = await db.into(db.tasks).insert(TasksCompanion.insert(
          title: title.trim(),
          note: Value(note),
          goalId: Value(goalId),
          dreamId: Value(dream),
          dueDate: Value(dueDate == null ? null : Dates.dayOf(dueDate)),
          dueMinutes: Value(dueMinutes),
          priority: Value(priority),
          repeatRule: Value(repeatRule),
          reminder: Value(reminder),
        ));
    final t = await (db.select(db.tasks)..where((x) => x.id.equals(id))).getSingle();
    await _syncTaskReminder(t);
    return id;
  }

  Future<void> updateTask(TaskItem t) async {
    await db.update(db.tasks).replace(t);
    await _syncTaskReminder(t);
  }

  Future<void> deleteTask(TaskItem t) async {
    await (db.delete(db.tasks)..where((x) => x.id.equals(t.id))).go();
    await reminders.removeFor('task', t.id);
  }

  /// Bir sonraki tekrar tarihi.
  static DateTime nextDue(DateTime due, String rule) => switch (rule) {
        'daily' => due.add(const Duration(days: 1)),
        'weekly' => due.add(const Duration(days: 7)),
        'monthly' => Dates.addMonths(due, 1),
        _ => due,
      };

  Future<void> toggleTask(TaskItem t) async {
    if (!t.isCompleted && t.repeatRule != 'none') {
      // Tekrarlanan görev: geçmişe kaydet, sonraki tarihe ilerlet.
      final base = t.dueDate ?? Dates.today();
      var next = nextDue(base, t.repeatRule);
      while (next.isBefore(Dates.today())) {
        next = nextDue(next, t.repeatRule);
      }
      final updated = t.copyWith(dueDate: Value(next));
      await db.update(db.tasks).replace(updated);
      await game.award('task', t.id, 'Görev: ${t.title}', Points.task);
      await _syncTaskReminder(updated);
      return;
    }
    final done = !t.isCompleted;
    await (db.update(db.tasks)..where((x) => x.id.equals(t.id))).write(TasksCompanion(
      isCompleted: Value(done),
      completedAt: Value(done ? DateTime.now() : null),
    ));
    if (done) {
      await game.award('task', t.id, 'Görev: ${t.title}', Points.task);
      await reminders.removeFor('task', t.id);
    } else {
      await game.revoke('task', t.id);
      await _syncTaskReminder(t.copyWith(isCompleted: false));
    }
  }

  Future<void> _syncTaskReminder(TaskItem t) async {
    if (!t.reminder || t.isCompleted || t.dueDate == null) {
      await reminders.removeFor('task', t.id);
      return;
    }
    final mins = t.dueMinutes ?? 9 * 60;
    final at = t.dueDate!.add(Duration(minutes: mins));
    final repeat = switch (t.repeatRule) {
      'daily' => 'daily',
      'weekly' => 'weekly',
      'monthly' => 'monthly',
      _ => 'none',
    };
    await reminders.upsertFor(
      kind: 'task',
      refId: t.id,
      title: 'Görev zamanı: ${t.title}',
      body: t.note.isEmpty ? 'Hayallerine bir adım daha yaklaş.' : t.note,
      at: at,
      repeat: repeat,
    );
  }

  Future<void> _syncGoalReminder(int id, String title, DateTime? due) async {
    if (due == null) {
      await reminders.removeFor('goal', id);
      return;
    }
    final at = DateTime(due.year, due.month, due.day, 9).subtract(const Duration(days: 1));
    if (at.isBefore(DateTime.now())) {
      await reminders.removeFor('goal', id);
      return;
    }
    await reminders.upsertFor(
      kind: 'goal',
      refId: id,
      title: 'Hedefin yarın bitiyor: $title',
      body: 'Kalan görevlerine bir göz at.',
      at: at,
    );
  }
}
