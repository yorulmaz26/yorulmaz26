import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/dates.dart';
import '../../core/utils/money.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';
import 'savings_widgets.dart';

class SavingsDetailScreen extends ConsumerWidget {
  const SavingsDetailScreen({super.key, required this.id});
  final int id;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(savingsSummariesProvider).where((x) => x.goal.id == id).firstOrNull;
    if (s == null) return Scaffold(appBar: AppBar(), body: const EmptyState(icon: Icons.savings_outlined, title: 'Kumbara bulunamadı'));
    final g = s.goal;
    final cur = g.currency;
    final series = s.balanceSeries();
    final est = s.estimatedDate;
    final req = s.requiredMonthly;
    return Scaffold(
      appBar: AppBar(
        title: Text(g.title),
        actions: [
          IconButton(tooltip: 'Düzenle', icon: const Icon(Icons.edit_outlined), onPressed: () => showSavingsGoalSheet(context, existing: g)),
          PopupMenuButton<String>(
            onSelected: (v) async {
              final repo = ref.read(savingsRepoProvider);
              if (v == 'reminder_on') await repo.setMonthlyReminder(g.id, g.title, true);
              if (v == 'reminder_off') await repo.setMonthlyReminder(g.id, g.title, false);
              if (v == 'delete' &&
                  context.mounted &&
                  await confirm(context, title: 'Kumbara silinsin mi?', message: 'Tüm işlem geçmişi de silinir.', ok: 'Sil', destructive: true)) {
                await repo.deleteGoal(g);
                if (context.mounted) context.pop();
              }
              if (context.mounted && v.startsWith('reminder')) toast(context, v == 'reminder_on' ? 'Aylık hatırlatıcı açıldı' : 'Hatırlatıcı kapatıldı');
            },
            itemBuilder: (_) => const [
              PopupMenuItem(value: 'reminder_on', child: Text('Aylık hatırlatıcıyı aç')),
              PopupMenuItem(value: 'reminder_off', child: Text('Aylık hatırlatıcıyı kapat')),
              PopupMenuItem(value: 'delete', child: Text('Sil')),
            ],
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 100),
        children: [
          SoftCard(
            child: Column(children: [
              ProgressRing(value: s.ratio, size: 130, stroke: 12, color: AppColors.gold),
              Gap.h16,
              Text(Money.format(s.balance, currency: cur), style: Theme.of(context).textTheme.headlineMedium),
              Text('Hedef ${Money.format(g.targetMinor, currency: cur)}'),
              Gap.h12,
              Row(children: [
                Expanded(child: StatTile(icon: Icons.flag, value: Money.format(s.remaining, currency: cur, showCents: false), label: 'Kalan tutar', color: AppColors.danger)),
                Gap.w8,
                Expanded(
                  child: StatTile(
                    icon: Icons.calendar_month,
                    value: Money.format(s.thisMonthDeposits(), currency: cur, showCents: false),
                    label: g.monthlyTargetMinor == null ? 'Bu ay' : 'Bu ay / ${Money.format(g.monthlyTargetMinor!, currency: cur, showCents: false)}',
                    color: AppColors.turquoise,
                  ),
                ),
              ]),
              Gap.h12,
              Row(children: [
                Expanded(
                  child: FilledButton.icon(
                    onPressed: () => showTransactionSheet(context, goal: g),
                    icon: const Icon(Icons.add),
                    label: const Text('Birikim ekle'),
                  ),
                ),
                Gap.w8,
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => showTransactionSheet(context, goal: g, kind: 'withdraw'),
                    icon: const Icon(Icons.remove),
                    label: const Text('Çek'),
                  ),
                ),
              ]),
            ]),
          ),
          Gap.h12,
          SoftCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('Hedefe ulaşma tahmini', style: TextStyle(fontWeight: FontWeight.w700)),
              Gap.h8,
              if (s.remaining == 0)
                const Text('Hedefe ulaştın! 🎉')
              else if (est != null)
                Text('Bu hızla yaklaşık ${Dates.monthYear(est)} (${g.monthlyTargetMinor != null ? 'aylık hedefine' : 'son birikimlerine'} göre)')
              else
                const Text('Tahmin için birikim ekle ya da aylık hedef belirle.'),
              if (req != null && s.remaining > 0) ...[
                Gap.h4,
                Text('${Dates.long(g.targetDate!)} tarihine yetişmek için ayda ${Money.format(req, currency: cur)} biriktirmelisin.'),
              ],
            ]),
          ),
          if (series.length >= 2) ...[
            const SectionHeader('Birikim grafiği'),
            SoftCard(
              child: SizedBox(
                height: 180,
                child: LineChart(LineChartData(
                  minY: 0,
                  gridData: const FlGridData(show: false),
                  borderData: FlBorderData(show: false),
                  titlesData: const FlTitlesData(
                    leftTitles: AxisTitles(),
                    rightTitles: AxisTitles(),
                    topTitles: AxisTitles(),
                    bottomTitles: AxisTitles(),
                  ),
                  lineTouchData: LineTouchData(
                    touchTooltipData: LineTouchTooltipData(
                      getTooltipItems: (spots) => [
                        for (final sp in spots)
                          LineTooltipItem(
                            '${Dates.short(series[sp.x.toInt()].$1)}\n${Money.format(series[sp.x.toInt()].$2, currency: cur)}',
                            const TextStyle(color: Colors.white, fontSize: 12),
                          ),
                      ],
                    ),
                  ),
                  extraLinesData: ExtraLinesData(horizontalLines: [
                    HorizontalLine(y: g.targetMinor / 100, color: AppColors.gold.withValues(alpha: 0.6), dashArray: [6, 4]),
                  ]),
                  lineBarsData: [
                    LineChartBarData(
                      spots: [for (var i = 0; i < series.length; i++) FlSpot(i.toDouble(), series[i].$2 / 100)],
                      isCurved: true,
                      preventCurveOverShooting: true,
                      color: AppColors.gold,
                      barWidth: 3,
                      dotData: const FlDotData(show: false),
                      belowBarData: BarAreaData(show: true, color: AppColors.gold.withValues(alpha: 0.15)),
                    ),
                  ],
                )),
              ),
            ),
          ],
          const SectionHeader('İşlem geçmişi'),
          if (s.transactions.isEmpty)
            Text('Henüz işlem yok.', style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant))
          else
            ...s.transactions.map((t) => TransactionTile(t)),
          Gap.h8,
          Text('Silmek için bir işleme uzun bas.', style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant)),
        ],
      ),
    );
  }
}
