import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/logic/progress_logic.dart';
import '../../core/settings/settings_controller.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/dates.dart';
import '../../core/utils/quotes.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';
import '../dreams/quick_add_sheet.dart';
import '../garden/plant_painter.dart';
import '../goals/task_widgets.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsProvider);
    final profile = ref.watch(profileProvider).value;
    final cards = settings.homeOrder.where((c) => !settings.hiddenCards.contains(c)).toList();
    final name = profile?.name ?? '';
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        heroTag: 'home-fab',
        onPressed: () => showQuickAddDream(context),
        icon: const Icon(Icons.add),
        label: const Text('Hayal ekle'),
      ),
      body: SafeArea(
        child: CustomScrollView(
          slivers: [
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(Gap.lg, Gap.lg, Gap.lg, 0),
              sliver: SliverToBoxAdapter(
                child: Row(children: [
                  GestureDetector(
                    onTap: () => context.go('/profile'),
                    child: Avatar(path: profile?.avatarPath, name: name, size: 48),
                  ),
                  Gap.w12,
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text('${Dates.greeting()}${name.isEmpty ? '' : ', $name'} 👋',
                          maxLines: 1, overflow: TextOverflow.ellipsis, style: Theme.of(context).textTheme.titleLarge),
                      Text(Dates.longWithDay(DateTime.now()),
                          style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant, fontSize: 13)),
                    ]),
                  ),
                  IconButton(
                    tooltip: 'Ana sayfayı düzenle',
                    icon: const Icon(Icons.tune_rounded),
                    onPressed: () => _customize(context, ref),
                  ),
                ]),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(Gap.lg, Gap.sm, Gap.lg, 100),
              sliver: SliverList.list(children: [
                for (final c in cards) _card(c),
              ]),
            ),
          ],
        ),
      ),
    );
  }

  Widget _card(HomeCard c) => switch (c) {
        HomeCard.stats => const _StatsCard(),
        HomeCard.quote => const _QuoteCard(),
        HomeCard.today => const _TodayCard(),
        HomeCard.upcoming => const _UpcomingCard(),
        HomeCard.garden => const _GardenPreview(),
        HomeCard.weekly => const _WeeklyChart(),
        HomeCard.shortcuts => const _Shortcuts(),
      };

  void _customize(BuildContext context, WidgetRef ref) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (_) => Consumer(builder: (context, ref, _) {
        final s = ref.watch(settingsProvider);
        final ctrl = ref.read(settingsProvider.notifier);
        return SizedBox(
          height: MediaQuery.sizeOf(context).height * 0.7,
          child: Column(children: [
            const Padding(
              padding: EdgeInsets.all(Gap.lg),
              child: Text('Kartları sürükleyerek sırala, istemediklerini gizle', textAlign: TextAlign.center),
            ),
            Expanded(
              child: ReorderableListView(
                onReorderItem: (o, n) {
                  final list = [...s.homeOrder];
                  list.insert(n, list.removeAt(o));
                  ctrl.update((x) => x.copyWith(homeOrder: list));
                },
                children: [
                  for (final c in s.homeOrder)
                    SwitchListTile(
                      key: ValueKey(c),
                      secondary: const Icon(Icons.drag_indicator),
                      title: Text(c.label),
                      value: !s.hiddenCards.contains(c),
                      onChanged: (v) {
                        final h = {...s.hiddenCards};
                        v ? h.remove(c) : h.add(c);
                        ctrl.update((x) => x.copyWith(hiddenCards: h));
                      },
                    ),
                ],
              ),
            ),
          ]),
        );
      }),
    );
  }
}

class _StatsCard extends ConsumerWidget {
  const _StatsCard();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dreams = ref.watch(dreamViewsProvider).value ?? const [];
    final visible = dreams.where((d) => !d.dream.isHidden).toList();
    final active = visible.where((d) => !d.dream.isCompleted).length;
    final done = visible.where((d) => d.dream.isCompleted).length;
    final history = ref.watch(historyProvider).value ?? const [];
    final tasksDone = history.where((h) => h.type == 'task').length;
    final streak = ProgressLogic.activityStreak(ref.watch(activeDaysProvider).value ?? {});
    final overall = visible.isEmpty ? 0.0 : visible.map((d) => d.progress).reduce((a, b) => a + b) / visible.length;
    return Padding(
      padding: const EdgeInsets.only(top: Gap.md),
      child: Column(children: [
        GradientCard(
          onTap: () => context.push('/stats'),
          child: Row(children: [
            ProgressRing(
              value: overall,
              size: 76,
              stroke: 8,
              color: Colors.white,
              background: Colors.white24,
              child: Text('%${(overall * 100).round()}', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 18)),
            ),
            Gap.w16,
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('Genel ilerleme', style: TextStyle(fontSize: 13, color: Colors.white70)),
                const SizedBox(height: 2),
                Text(
                  active == 0 && done == 0 ? 'İlk hayalini ekleyerek başla' : '$active aktif hayal, $done gerçekleşti',
                  style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: Colors.white),
                ),
                const SizedBox(height: 6),
                Row(children: [
                  const Icon(Icons.local_fire_department, color: AppColors.gold, size: 18),
                  const SizedBox(width: 4),
                  Text('$streak günlük seri', style: const TextStyle(color: Colors.white)),
                ]),
              ]),
            ),
          ]),
        ),
        Gap.h12,
        Row(children: [
          Expanded(child: StatTile(icon: Icons.auto_awesome, value: '$active', label: 'Aktif hayal')),
          Gap.w8,
          Expanded(child: StatTile(icon: Icons.emoji_events, value: '$done', label: 'Gerçekleşen', color: AppColors.gold)),
          Gap.w8,
          Expanded(child: StatTile(icon: Icons.task_alt, value: '$tasksDone', label: 'Biten görev', color: AppColors.turquoise)),
        ]),
      ]),
    );
  }
}

class _QuoteCard extends StatelessWidget {
  const _QuoteCard();
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: Gap.md),
        child: SoftCard(
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Icon(Icons.format_quote_rounded, color: AppColors.gold, size: 32),
            Gap.w8,
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('Günün sözü', style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant)),
                Gap.h4,
                Text(Quotes.ofDay(), style: const TextStyle(fontSize: 16, fontStyle: FontStyle.italic, height: 1.35)),
              ]),
            ),
          ]),
        ),
      );
}

class _TodayCard extends ConsumerWidget {
  const _TodayCard();
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tasks = ref.watch(tasksProvider).value ?? const [];
    final today = Dates.today();
    final list = tasks
        .where((t) => !t.isCompleted && t.dueDate != null && !t.dueDate!.isAfter(today))
        .take(6)
        .toList();
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      SectionHeader('Bugünkü görevler', action: 'Tümü', onAction: () => context.go('/goals')),
      SoftCard(
        padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 8),
        child: list.isEmpty
            ? ListTile(
                leading: const Icon(Icons.wb_sunny_outlined, color: AppColors.gold),
                title: const Text('Bugün için bekleyen görev yok'),
                trailing: TextButton(onPressed: () => showTaskSheet(context, ref), child: const Text('Ekle')),
              )
            : Column(children: [for (final t in list) TaskTile(t)]),
      ),
    ]);
  }
}

class _UpcomingCard extends ConsumerWidget {
  const _UpcomingCard();
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dreams = (ref.watch(dreamViewsProvider).value ?? const [])
        .where((d) => !d.dream.isCompleted && !d.dream.isHidden && d.dream.targetDate != null)
        .toList()
      ..sort((a, b) => a.dream.targetDate!.compareTo(b.dream.targetDate!));
    final goals = (ref.watch(goalsProvider).value ?? const []).where((g) => !g.isCompleted && g.dueDate != null).toList()
      ..sort((a, b) => a.dueDate!.compareTo(b.dueDate!));
    final items = <(String, DateTime, IconData, VoidCallback)>[
      for (final d in dreams.take(3)) (d.dream.title, d.dream.targetDate!, Icons.auto_awesome, () => context.push('/dream/${d.dream.id}')),
      for (final g in goals.take(3)) (g.title, g.dueDate!, Icons.flag_rounded, () => context.push('/goal/${g.id}')),
    ]..sort((a, b) => a.$2.compareTo(b.$2));
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      const SectionHeader('Yaklaşan hedefler'),
      SoftCard(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: items.isEmpty
            ? const ListTile(leading: Icon(Icons.event_available), title: Text('Tarihli hedefin yok'), subtitle: Text('Hayallerine hedef tarih ekleyebilirsin'))
            : Column(children: [
                for (final i in items.take(4))
                  ListTile(
                    leading: Icon(i.$3, color: Theme.of(context).colorScheme.primary),
                    title: Text(i.$1, maxLines: 1, overflow: TextOverflow.ellipsis),
                    subtitle: Text(Dates.long(i.$2)),
                    trailing: Text(Dates.relative(i.$2),
                        style: TextStyle(fontWeight: FontWeight.w700, color: i.$2.isBefore(Dates.today()) ? AppColors.danger : null)),
                    onTap: i.$4,
                  ),
              ]),
      ),
    ]);
  }
}

class _GardenPreview extends ConsumerWidget {
  const _GardenPreview();
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final trees = (ref.watch(gardenProvider).value ?? const []).where((t) => !t.$2.isHidden).toList();
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      SectionHeader('Hayal Bahçem', action: 'Bahçeye git', onAction: () => context.go('/garden')),
      GestureDetector(
        onTap: () => context.go('/garden'),
        child: Container(
          height: 150,
          decoration: BoxDecoration(
            borderRadius: Radii.card,
            gradient: const LinearGradient(
              colors: [Color(0xFFBEE3F8), Color(0xFFE6F6EC)],
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
            ),
          ),
          clipBehavior: Clip.antiAlias,
          child: Stack(children: [
            Positioned(left: 0, right: 0, bottom: 0, height: 34, child: Container(color: const Color(0xFF8BC34A))),
            if (trees.isEmpty)
              const Center(child: Text('İlk hayalini ekle, bahçene ilk tohum ekilsin 🌱', style: TextStyle(color: AppColors.night)))
            else
              ListView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 12),
                children: [
                  for (final t in trees.take(10))
                    Padding(
                      padding: const EdgeInsets.only(right: 4, top: 12),
                      child: Plant(stage: t.$1.stage, type: t.$1.plantType, size: 88, seed: t.$1.id),
                    ),
                ],
              ),
          ]),
        ),
      ),
    ]);
  }
}

class _WeeklyChart extends ConsumerWidget {
  const _WeeklyChart();
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final counts = ref.watch(weeklyCountsProvider).value ?? List.filled(7, 0);
    final start = Dates.today().subtract(const Duration(days: 6));
    final cs = Theme.of(context).colorScheme;
    final maxY = (counts.fold<int>(0, (a, b) => a > b ? a : b) + 2).toDouble();
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      const SectionHeader('Haftalık ilerleme'),
      SoftCard(
        child: SizedBox(
          height: 160,
          child: Semantics(
            label: 'Son 7 günde tamamlanan işler: ${counts.join(', ')}',
            child: BarChart(BarChartData(
              maxY: maxY,
              gridData: const FlGridData(show: false),
              borderData: FlBorderData(show: false),
              barTouchData: BarTouchData(enabled: true),
              titlesData: FlTitlesData(
                leftTitles: const AxisTitles(),
                rightTitles: const AxisTitles(),
                topTitles: const AxisTitles(),
                bottomTitles: AxisTitles(
                  sideTitles: SideTitles(
                    showTitles: true,
                    getTitlesWidget: (v, _) => Padding(
                      padding: const EdgeInsets.only(top: 6),
                      child: Text(Dates.weekdayShort(start.add(Duration(days: v.toInt()))), style: const TextStyle(fontSize: 11)),
                    ),
                  ),
                ),
              ),
              barGroups: [
                for (var i = 0; i < 7; i++)
                  BarChartGroupData(x: i, barRods: [
                    BarChartRodData(
                      toY: counts[i].toDouble(),
                      width: 18,
                      borderRadius: BorderRadius.circular(6),
                      gradient: LinearGradient(colors: [cs.primary, AppColors.turquoise], begin: Alignment.bottomCenter, end: Alignment.topCenter),
                    ),
                  ]),
              ],
            )),
          ),
        ),
      ),
    ]);
  }
}

class _Shortcuts extends StatelessWidget {
  const _Shortcuts();

  static const items = <(String, IconData, String, Color)>[
    ('Hayal Panom', Icons.dashboard_customize_rounded, '/board', Color(0xFFEC4899)),
    ('Alışkanlıklar', Icons.repeat_rounded, '/habits', AppColors.turquoise),
    ('Kumbara', Icons.savings_rounded, '/savings', AppColors.gold),
    ('Günlüğüm', Icons.menu_book_rounded, '/journal', AppColors.purple),
    ('Gelecekteki Ben', Icons.hourglass_bottom_rounded, '/capsules', Color(0xFF0EA5E9)),
    ('Odaklanma', Icons.timer_rounded, '/focus', Color(0xFFEF4444)),
    ('Yaşam Dengem', Icons.donut_large_rounded, '/balance', Color(0xFF14B8A6)),
    ('Akıllı Planlayıcı', Icons.lightbulb_rounded, '/planner', Color(0xFFF97316)),
    ('Başarılarım', Icons.emoji_events_rounded, '/achievements', Color(0xFFEAB308)),
  ];

  @override
  Widget build(BuildContext context) {
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      const SectionHeader('Hızlı erişim'),
      LayoutBuilder(builder: (context, c) {
        final cols = c.maxWidth > 600 ? 5 : 3;
        return GridView.count(
          crossAxisCount: cols,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: 10,
          crossAxisSpacing: 10,
          childAspectRatio: 1.05,
          children: [
            for (final i in items)
              SoftCard(
                padding: const EdgeInsets.all(10),
                onTap: () => context.push(i.$3),
                child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(color: i.$4.withValues(alpha: 0.14), borderRadius: BorderRadius.circular(14)),
                    child: Icon(i.$2, color: i.$4),
                  ),
                  Gap.h8,
                  Text(i.$1, textAlign: TextAlign.center, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
                ]),
              ),
          ],
        );
      }),
    ]);
  }
}
