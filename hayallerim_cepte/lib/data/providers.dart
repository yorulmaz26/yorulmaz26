import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/database/app_database.dart';
import '../core/logic/progress_logic.dart';
import '../core/providers.dart';
import '../core/services/backup_service.dart';
import '../core/services/lock_service.dart';
import 'dream_repository.dart';
import 'gamification_repository.dart';
import 'goal_repository.dart';
import 'habit_repository.dart';
import 'journal_repository.dart';
import 'misc_repositories.dart';
import 'reminder_repository.dart';
import 'savings_repository.dart';

// ------------------------------ Depolar -----------------------------------
final reminderRepoProvider = Provider((ref) => ReminderRepository(ref.watch(databaseProvider), ref.watch(notificationsProvider)));
final gameRepoProvider = Provider((ref) {
  final r = GamificationRepository(ref.watch(databaseProvider));
  ref.onDispose(r.dispose);
  return r;
});
final dreamRepoProvider = Provider((ref) => DreamRepository(
      ref.watch(databaseProvider),
      ref.watch(cryptoProvider),
      ref.watch(mediaProvider),
      ref.watch(gameRepoProvider),
      ref.watch(reminderRepoProvider),
    ));
final goalRepoProvider =
    Provider((ref) => GoalRepository(ref.watch(databaseProvider), ref.watch(gameRepoProvider), ref.watch(reminderRepoProvider)));
final habitRepoProvider =
    Provider((ref) => HabitRepository(ref.watch(databaseProvider), ref.watch(gameRepoProvider), ref.watch(reminderRepoProvider)));
final savingsRepoProvider =
    Provider((ref) => SavingsRepository(ref.watch(databaseProvider), ref.watch(gameRepoProvider), ref.watch(reminderRepoProvider)));
final journalRepoProvider = Provider((ref) => JournalRepository(
      ref.watch(databaseProvider),
      ref.watch(cryptoProvider),
      ref.watch(mediaProvider),
      ref.watch(gameRepoProvider),
      ref.watch(reminderRepoProvider),
    ));
final focusRepoProvider = Provider((ref) => FocusRepository(ref.watch(databaseProvider), ref.watch(gameRepoProvider)));
final balanceRepoProvider = Provider((ref) => BalanceRepository(ref.watch(databaseProvider), ref.watch(gameRepoProvider)));
final profileRepoProvider = Provider((ref) => ProfileRepository(ref.watch(databaseProvider), ref.watch(mediaProvider)));
final boardRepoProvider = Provider((ref) => BoardRepository(ref.watch(databaseProvider), ref.watch(mediaProvider)));
final backupServiceProvider =
    Provider((ref) => BackupService(ref.watch(databaseProvider), ref.watch(cryptoProvider), ref.watch(mediaProvider)));
final lockServiceProvider = Provider((ref) => LockService(ref.watch(keyStoreProvider)));

// ------------------------------ Akışlar -----------------------------------
final profileProvider = StreamProvider((ref) => ref.watch(profileRepoProvider).watch());
final categoriesProvider = StreamProvider((ref) => ref.watch(dreamRepoProvider).watchCategories());
final dreamsRawProvider = StreamProvider((ref) => ref.watch(dreamRepoProvider).watchDreams());
final archivedDreamsProvider = StreamProvider((ref) => ref.watch(dreamRepoProvider).watchDreams(archived: true));
final allMilestonesProvider = StreamProvider((ref) => ref.watch(dreamRepoProvider).watchAllMilestones());

class DreamView {
  DreamView(this.dream, this.category, this.progress, this.totalSteps, this.doneSteps);
  final Dream dream;
  final DreamCategory category;
  final double progress;
  final int totalSteps;
  final int doneSteps;
}

/// Hayaller + kategori + ilerleme (adımlardan otomatik hesaplanır).
final dreamViewsProvider = Provider<AsyncValue<List<DreamView>>>((ref) {
  final dreams = ref.watch(dreamsRawProvider);
  final steps = ref.watch(allMilestonesProvider);
  if (dreams.hasError) return AsyncValue.error(dreams.error!, dreams.stackTrace ?? StackTrace.current);
  if (!dreams.hasValue || !steps.hasValue) return const AsyncValue.loading();
  final byDream = <int, List<DreamMilestone>>{};
  for (final s in steps.value!) {
    byDream.putIfAbsent(s.dreamId, () => []).add(s);
  }
  return AsyncValue.data([
    for (final d in dreams.value!)
      () {
        final ms = byDream[d.dream.id] ?? const <DreamMilestone>[];
        return DreamView(
          d.dream,
          d.category,
          ProgressLogic.dreamProgress(ms, weighted: d.dream.weightedProgress, completed: d.dream.isCompleted),
          ms.length,
          ms.where((m) => m.isCompleted).length,
        );
      }(),
  ]);
});

final goalsProvider = StreamProvider((ref) => ref.watch(goalRepoProvider).watchGoals());
final tasksProvider = StreamProvider((ref) => ref.watch(goalRepoProvider).watchTasks());
final habitsProvider = StreamProvider((ref) => ref.watch(habitRepoProvider).watchHabits());
final habitCompletionsProvider = StreamProvider((ref) => ref.watch(habitRepoProvider).watchCompletions());
final savingsGoalsProvider = StreamProvider((ref) => ref.watch(savingsRepoProvider).watchGoals());
final savingsTxProvider = StreamProvider((ref) => ref.watch(savingsRepoProvider).watchTransactions());
final journalProvider = StreamProvider((ref) => ref.watch(journalRepoProvider).watchEntries());
final capsulesProvider = StreamProvider((ref) => ref.watch(journalRepoProvider).watchCapsules());
final gardenProvider = StreamProvider((ref) => ref.watch(dreamRepoProvider).watchGarden());
final pointsProvider = StreamProvider((ref) => ref.watch(gameRepoProvider).watchPoints());
final unlockedProvider = StreamProvider((ref) => ref.watch(gameRepoProvider).watchUnlocked());
final historyProvider = StreamProvider((ref) => ref.watch(gameRepoProvider).watchHistory());
final weeklyCountsProvider = StreamProvider((ref) => ref.watch(gameRepoProvider).watchWeeklyCounts());
final activeDaysProvider = StreamProvider((ref) => ref.watch(gameRepoProvider).watchActiveDays());
final focusSessionsProvider = StreamProvider((ref) => ref.watch(focusRepoProvider).watchSessions());
final balanceProvider = StreamProvider((ref) => ref.watch(balanceRepoProvider).watchEntries());
final boardProvider = StreamProvider((ref) => ref.watch(boardRepoProvider).watchBoard());
final specialDaysProvider = StreamProvider((ref) => ref.watch(reminderRepoProvider).watchSpecialDays());

final habitStatsProvider = Provider<List<HabitStats>>((ref) {
  final habits = ref.watch(habitsProvider).value ?? const [];
  final comps = ref.watch(habitCompletionsProvider).value ?? const [];
  final byHabit = <int, Set<DateTime>>{};
  for (final c in comps) {
    byHabit.putIfAbsent(c.habitId, () => {}).add(DateTime(c.day.year, c.day.month, c.day.day));
  }
  return [for (final h in habits) HabitStats(h, byHabit[h.id] ?? {})];
});

final savingsSummariesProvider = Provider<List<SavingsSummary>>((ref) {
  final goals = ref.watch(savingsGoalsProvider).value ?? const [];
  final txs = ref.watch(savingsTxProvider).value ?? const [];
  return [for (final g in goals) SavingsSummary(g, txs.where((t) => t.savingsGoalId == g.id).toList())];
});
