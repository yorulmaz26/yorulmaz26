import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/settings/settings_controller.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/dates.dart';
import '../../core/utils/money.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';
import 'savings_widgets.dart';

class SavingsScreen extends ConsumerWidget {
  const SavingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final list = ref.watch(savingsSummariesProvider);
    final txs = ref.watch(savingsTxProvider).value ?? const [];
    final cur = ref.watch(settingsProvider).defaultCurrency;
    final month = Dates.startOfMonth(DateTime.now());
    var income = 0;
    var expense = 0;
    for (final t in txs) {
      if (t.date.isBefore(month) || t.currency != cur) continue;
      if (t.kind == 'income') income += t.amountMinor;
      if (t.kind == 'expense') expense += t.amountMinor;
    }
    final totals = <String, int>{};
    for (final s in list) {
      totals[s.goal.currency] = (totals[s.goal.currency] ?? 0) + s.balance;
    }
    return Scaffold(
      appBar: AppBar(
        title: const Text('Hayal Kumbarası'),
        actions: [
          IconButton(tooltip: 'Tasarruf hesaplayıcısı', icon: const Icon(Icons.calculate_outlined), onPressed: () => showSavingsCalculator(context)),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => showSavingsGoalSheet(context),
        icon: const Icon(Icons.add),
        label: const Text('Kumbara'),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 100),
        children: [
          GradientCard(
            gradient: AppColors.goldGradient,
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('Toplam birikim', style: TextStyle(color: Colors.white70)),
              Gap.h4,
              if (totals.isEmpty)
                Text(Money.format(0, currency: cur), style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w800))
              else
                for (final e in totals.entries)
                  Text(Money.format(e.value, currency: e.key), style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w800)),
              Gap.h12,
              Row(children: [
                Expanded(child: Text('Bu ay gelir\n${Money.format(income, currency: cur)}', style: const TextStyle(fontSize: 13))),
                Expanded(child: Text('Bu ay gider\n${Money.format(expense, currency: cur)}', style: const TextStyle(fontSize: 13))),
                TextButton(
                  style: TextButton.styleFrom(foregroundColor: Colors.white),
                  onPressed: () => showTransactionSheet(context, goal: null),
                  child: const Text('Gelir/Gider'),
                ),
              ]),
            ]),
          ),
          if (list.isEmpty)
            const Padding(
              padding: EdgeInsets.only(top: 40),
              child: EmptyState(icon: Icons.savings_outlined, title: 'Henüz kumbaran yok', message: 'Para gerektiren hayallerin için kumbara oluştur.'),
            ),
          for (final s in list) ...[
            Gap.h12,
            SoftCard(
              onTap: () => context.push('/savings/${s.goal.id}'),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  const Icon(Icons.savings_rounded, color: AppColors.gold),
                  Gap.w8,
                  Expanded(child: Text(s.goal.title, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16))),
                  if (s.goal.isCompleted) const Icon(Icons.verified, color: AppColors.success),
                ]),
                Gap.h12,
                LinearBar(value: s.ratio, color: AppColors.gold, height: 10),
                Gap.h8,
                Row(children: [
                  Text('${Money.format(s.balance, currency: s.goal.currency)} / ${Money.format(s.goal.targetMinor, currency: s.goal.currency, showCents: false)}',
                      style: const TextStyle(fontSize: 13)),
                  const Spacer(),
                  Text('%${(s.ratio * 100).floor()}', style: const TextStyle(fontWeight: FontWeight.w800)),
                ]),
              ]),
            ),
          ],
          const SectionHeader('Son gelir / gider kayıtları'),
          ...txs.where((t) => t.kind == 'income' || t.kind == 'expense').take(10).map((t) => TransactionTile(t)),
        ],
      ),
    );
  }
}
