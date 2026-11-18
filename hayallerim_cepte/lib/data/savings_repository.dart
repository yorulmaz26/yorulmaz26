import 'package:decimal/decimal.dart';
import 'package:drift/drift.dart';

import '../core/database/app_database.dart';
import '../core/logic/achievements_catalog.dart';
import '../core/utils/dates.dart';
import '../core/utils/money.dart';
import 'gamification_repository.dart';
import 'reminder_repository.dart';

class SavingsSummary {
  SavingsSummary(this.goal, this.transactions);

  final SavingsGoal goal;
  final List<SavingsTransaction> transactions;

  /// Bakiye: yatırılan − çekilen (kuruş, tam sayı).
  int get balance {
    var b = 0;
    for (final t in transactions) {
      if (t.kind == 'deposit') b += t.amountMinor;
      if (t.kind == 'withdraw') b -= t.amountMinor;
    }
    return b;
  }

  int get remaining => (goal.targetMinor - balance).clamp(0, goal.targetMinor);
  double get ratio => Money.ratio(balance, goal.targetMinor);

  /// Son 3 ayın net aylık ortalaması.
  int get recentMonthlyAverage {
    final from = Dates.addMonths(Dates.today(), -3);
    var net = 0;
    for (final t in transactions) {
      if (t.date.isBefore(from)) continue;
      if (t.kind == 'deposit') net += t.amountMinor;
      if (t.kind == 'withdraw') net -= t.amountMinor;
    }
    final first = transactions.isEmpty ? Dates.today() : transactions.map((t) => t.date).reduce((a, b) => a.isBefore(b) ? a : b);
    final months = Dates.daysBetween(first.isAfter(from) ? first : from, Dates.today()) / 30.0;
    if (months < 1) return net; // ilk ay: o ana kadarki toplam
    return net ~/ months.ceil();
  }

  /// Hedefe ulaşma tahmini (aylık hedef ya da son ortalamaya göre).
  DateTime? get estimatedDate {
    final monthly = goal.monthlyTargetMinor ?? recentMonthlyAverage;
    final m = Money.monthsToGoal(remainingMinor: remaining, monthlyMinor: monthly);
    if (m == null) return null;
    return Dates.addMonths(Dates.today(), m);
  }

  /// Hedef tarih varsa gereken aylık tutar.
  int? get requiredMonthly {
    final td = goal.targetDate;
    if (td == null) return null;
    final months = ((Dates.daysBetween(Dates.today(), td)) / 30).ceil();
    return Money.requiredMonthly(remainingMinor: remaining, months: months);
  }

  int thisMonthDeposits() {
    final s = Dates.startOfMonth(DateTime.now());
    var sum = 0;
    for (final t in transactions) {
      if (!t.date.isBefore(s)) {
        if (t.kind == 'deposit') sum += t.amountMinor;
        if (t.kind == 'withdraw') sum -= t.amountMinor;
      }
    }
    return sum;
  }

  /// Grafik için birikimli bakiye serisi (tarih sırasıyla).
  List<(DateTime, int)> balanceSeries() {
    final sorted = transactions.where((t) => t.kind == 'deposit' || t.kind == 'withdraw').toList()
      ..sort((a, b) => a.date.compareTo(b.date));
    var b = 0;
    return [
      for (final t in sorted) (t.date, b += t.kind == 'deposit' ? t.amountMinor : -t.amountMinor),
    ];
  }
}

class SavingsRepository {
  SavingsRepository(this.db, this.game, this.reminders);

  final AppDatabase db;
  final GamificationRepository game;
  final ReminderRepository reminders;

  Stream<List<SavingsGoal>> watchGoals() =>
      (db.select(db.savingsGoals)..where((g) => g.archivedAt.isNull())..orderBy([(g) => OrderingTerm.desc(g.createdAt)])).watch();

  Stream<List<SavingsTransaction>> watchTransactions() =>
      (db.select(db.savingsTransactions)..orderBy([(t) => OrderingTerm.desc(t.date), (t) => OrderingTerm.desc(t.id)])).watch();

  Future<int> createGoal({
    required String title,
    required int targetMinor,
    String currency = 'TRY',
    int? monthlyTargetMinor,
    DateTime? targetDate,
    int? dreamId,
    bool monthlyReminder = false,
  }) async {
    final id = await db.into(db.savingsGoals).insert(SavingsGoalsCompanion.insert(
          title: title.trim(),
          targetMinor: targetMinor,
          currency: Value(currency),
          monthlyTargetMinor: Value(monthlyTargetMinor),
          targetDate: Value(targetDate),
          dreamId: Value(dreamId),
        ));
    if (monthlyReminder) await setMonthlyReminder(id, title, true);
    return id;
  }

  Future<void> updateGoal(SavingsGoal g) => db.update(db.savingsGoals).replace(g);

  Future<void> deleteGoal(SavingsGoal g) async {
    await (db.delete(db.savingsGoals)..where((x) => x.id.equals(g.id))).go();
    await reminders.removeFor('savings', g.id);
  }

  Future<void> setMonthlyReminder(int goalId, String title, bool on) async {
    if (!on) {
      await reminders.removeFor('savings', goalId);
      return;
    }
    final now = DateTime.now();
    await reminders.upsertFor(
      kind: 'savings',
      refId: goalId,
      title: 'Kumbara zamanı: $title',
      body: 'Bu ayın birikimini eklemeyi unutma 💰',
      at: DateTime(now.year, now.month, 1, 10),
      repeat: 'monthly',
    );
  }

  /// İşlem ekler. Yabancı para birimiyle girilirse manuel kur ile kumbara
  /// para birimine çevrilir (tam sayı kuruş + Decimal kur).
  Future<void> addTransaction({
    int? goalId,
    required String kind,
    required int amountMinor,
    required String currency,
    String? originalCurrency,
    int? originalAmountMinor,
    Decimal? rate,
    String note = '',
    DateTime? date,
    String? goalTitle,
  }) async {
    if (amountMinor <= 0) throw ArgumentError('Tutar sıfırdan büyük olmalı');
    await db.into(db.savingsTransactions).insert(SavingsTransactionsCompanion.insert(
          savingsGoalId: Value(goalId),
          kind: kind,
          amountMinor: amountMinor,
          currency: Value(currency),
          originalAmountMinor: Value(originalAmountMinor),
          originalCurrency: Value(originalCurrency),
          manualRate: Value(rate?.toString()),
          note: Value(note),
          date: date ?? DateTime.now(),
        ));
    if (kind == 'deposit' && goalId != null) {
      await game.award('deposit', goalId, 'Birikim: ${goalTitle ?? ''}', Points.deposit);
      await _checkCompleted(goalId);
    }
    if (kind == 'withdraw' && goalId != null) await _checkCompleted(goalId);
  }

  Future<void> deleteTransaction(SavingsTransaction t) async {
    await (db.delete(db.savingsTransactions)..where((x) => x.id.equals(t.id))).go();
    if (t.savingsGoalId != null) await _checkCompleted(t.savingsGoalId!);
  }

  Future<void> _checkCompleted(int goalId) async {
    final g = await (db.select(db.savingsGoals)..where((x) => x.id.equals(goalId))).getSingle();
    final txs = await (db.select(db.savingsTransactions)..where((t) => t.savingsGoalId.equals(goalId))).get();
    final s = SavingsSummary(g, txs);
    final done = s.balance >= g.targetMinor;
    if (done != g.isCompleted) {
      await (db.update(db.savingsGoals)..where((x) => x.id.equals(goalId))).write(SavingsGoalsCompanion(isCompleted: Value(done)));
      if (done) await game.evaluate();
    }
  }
}
