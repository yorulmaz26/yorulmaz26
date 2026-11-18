import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/logic/progress_logic.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/dates.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';

/// Genel istatistikler: hayaller, görevler, alışkanlıklar, odak, puanlar.
class StatsScreen extends ConsumerWidget {
  const StatsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dreams = (ref.watch(dreamViewsProvider).value ?? const []).where((d) => !d.dream.isHidden).toList();
    final history = ref.watch(historyProvider).value ?? const [];
    final habits = ref.watch(habitStatsProvider);
    final focus = (ref.watch(focusSessionsProvider).value ?? const []).where((s) => s.kind == 'work');
    final points = ref.watch(pointsProvider).value ?? 0;
    final streak = ProgressLogic.activityStreak(ref.watch(activeDaysProvider).value ?? {});

    // Son 6 ayda aylık tamamlanan işler
    final now = DateTime.now();
    final months = [for (var i = 5; i >= 0; i--) DateTime(now.year, now.month - i)];
    final perMonth = List<int>.filled(6, 0);
    for (final h in history) {
      if (h.type == 'badge') continue;
      for (var i = 0; i < 6; i++) {
        if (h.createdAt.year == months[i].year && h.createdAt.month == months[i].month) perMonth[i]++;
      }
    }
    // Kategori dağılımı
    final byCat = <String, (int, Color)>{};
    for (final d in dreams) {
      final e = byCat[d.category.name];
      byCat[d.category.name] = ((e?.$1 ?? 0) + 1, Color(d.category.color));
    }
    final avgProgress = dreams.isEmpty ? 0.0 : dreams.map((d) => d.progress).reduce((a, b) => a + b) / dreams.length;
    final focusMin = focus.fold<int>(0, (a, s) => a + s.actualSeconds) ~/ 60;

    return Scaffold(
      appBar: AppBar(title: const Text('İstatistikler')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 60),
        children: [
          GridView.count(
            crossAxisCount: 3,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            mainAxisSpacing: 8,
            crossAxisSpacing: 8,
            childAspectRatio: 0.95,
            children: [
              StatTile(icon: Icons.auto_awesome, value: '${dreams.length}', label: 'Toplam hayal'),
              StatTile(icon: Icons.emoji_events, value: '${dreams.where((d) => d.dream.isCompleted).length}', label: 'Gerçekleşen', color: AppColors.gold),
              StatTile(icon: Icons.percent, value: '%${(avgProgress * 100).round()}', label: 'Ort. ilerleme', color: AppColors.turquoise),
              StatTile(icon: Icons.task_alt, value: '${history.where((h) => h.type == 'task').length}', label: 'Biten görev', color: AppColors.success),
              StatTile(icon: Icons.local_fire_department, value: '$streak', label: 'Günlük seri', color: AppColors.warning),
              StatTile(icon: Icons.timer, value: Dates.durationLabel(focusMin * 60), label: 'Odak süresi', color: AppColors.purple),
              StatTile(icon: Icons.repeat, value: '${habits.length}', label: 'Alışkanlık'),
              StatTile(icon: Icons.military_tech, value: '${habits.fold<int>(0, (a, h) => h.best > a ? h.best : a)}', label: 'En uzun seri', color: AppColors.gold),
              StatTile(icon: Icons.star, value: '$points', label: 'Puan', color: AppColors.gold),
            ],
          ),
          const SectionHeader('Aylık tamamlanan işler'),
          SoftCard(
            child: SizedBox(
              height: 170,
              child: BarChart(BarChartData(
                maxY: (perMonth.fold<int>(0, (a, b) => a > b ? a : b) + 3).toDouble(),
                gridData: const FlGridData(show: false),
                borderData: FlBorderData(show: false),
                titlesData: FlTitlesData(
                  leftTitles: const AxisTitles(),
                  rightTitles: const AxisTitles(),
                  topTitles: const AxisTitles(),
                  bottomTitles: AxisTitles(
                    sideTitles: SideTitles(
                      showTitles: true,
                      getTitlesWidget: (v, _) => Text(Dates.monthYear(months[v.toInt()]).split(' ').first.substring(0, 3), style: const TextStyle(fontSize: 11)),
                    ),
                  ),
                ),
                barGroups: [
                  for (var i = 0; i < 6; i++)
                    BarChartGroupData(x: i, barRods: [
                      BarChartRodData(toY: perMonth[i].toDouble(), width: 22, color: AppColors.turquoise, borderRadius: BorderRadius.circular(6)),
                    ]),
                ],
              )),
            ),
          ),
          if (byCat.isNotEmpty) ...[
            const SectionHeader('Hayallerin kategorilere dağılımı'),
            SoftCard(
              child: Row(children: [
                SizedBox(
                  width: 140,
                  height: 140,
                  child: PieChart(PieChartData(
                    sectionsSpace: 2,
                    centerSpaceRadius: 30,
                    sections: [
                      for (final e in byCat.entries)
                        PieChartSectionData(value: e.value.$1.toDouble(), color: e.value.$2, radius: 36, title: '${e.value.$1}', titleStyle: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                    ],
                  )),
                ),
                Gap.w16,
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    for (final e in byCat.entries)
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 2),
                        child: Row(children: [
                          Container(width: 10, height: 10, decoration: BoxDecoration(color: e.value.$2, shape: BoxShape.circle)),
                          Gap.w8,
                          Expanded(child: Text(e.key, style: const TextStyle(fontSize: 12))),
                          Text('${e.value.$1}', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
                        ]),
                      ),
                  ]),
                ),
              ]),
            ),
          ],
          if (habits.isNotEmpty) ...[
            const SectionHeader('Alışkanlık tamamlanma oranları'),
            for (final h in habits)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Row(children: [
                  SizedBox(width: 120, child: Text(h.habit.title, maxLines: 1, overflow: TextOverflow.ellipsis)),
                  Expanded(child: LinearBar(value: h.rate, color: Color(h.habit.color))),
                  Gap.w8,
                  Text('%${(h.rate * 100).round()}'),
                ]),
              ),
          ],
        ],
      ),
    );
  }
}
