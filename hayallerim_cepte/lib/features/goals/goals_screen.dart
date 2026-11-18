import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/database/app_database.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/dates.dart';
import '../../core/widgets/common.dart';
import '../../data/goal_repository.dart';
import '../../data/providers.dart';
import 'goal_sheet.dart';
import 'task_widgets.dart';

/// Hedeflerim: görevler (liste/takvim), dönemsel hedefler, gecikenler.
class GoalsScreen extends ConsumerStatefulWidget {
  const GoalsScreen({super.key});
  @override
  ConsumerState<GoalsScreen> createState() => _GoalsScreenState();
}

class _GoalsScreenState extends ConsumerState<GoalsScreen> with SingleTickerProviderStateMixin {
  late final _tabs = TabController(length: 3, vsync: this);
  bool _calendar = false;
  DateTime _selected = Dates.today();

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Hedeflerim'),
        actions: [
          IconButton(tooltip: 'Alışkanlıklar', icon: const Icon(Icons.repeat_rounded), onPressed: () => context.push('/habits')),
          IconButton(tooltip: 'Akıllı Planlayıcı', icon: const Icon(Icons.lightbulb_outline), onPressed: () => context.push('/planner')),
        ],
        bottom: TabBar(controller: _tabs, tabs: const [Tab(text: 'Görevler'), Tab(text: 'Hedefler'), Tab(text: 'Gecikenler')]),
      ),
      floatingActionButton: FloatingActionButton(
        heroTag: 'goals-fab',
        tooltip: 'Ekle',
        onPressed: () {
          if (_tabs.index == 1) {
            showGoalSheet(context, ref);
          } else {
            showTaskSheet(context, ref, date: _calendar ? _selected : null);
          }
        },
        child: const Icon(Icons.add),
      ),
      body: TabBarView(controller: _tabs, children: [
        _tasksTab(),
        const _GoalsTab(),
        const _OverdueTab(),
      ]),
    );
  }

  Widget _tasksTab() {
    final tasks = ref.watch(tasksProvider).value ?? const <TaskItem>[];
    return Column(children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(Gap.lg, Gap.md, Gap.lg, 0),
        child: SegmentedButton<bool>(
          segments: const [
            ButtonSegment(value: false, label: Text('Liste'), icon: Icon(Icons.view_list)),
            ButtonSegment(value: true, label: Text('Takvim'), icon: Icon(Icons.calendar_month)),
          ],
          selected: {_calendar},
          onSelectionChanged: (v) => setState(() => _calendar = v.first),
        ),
      ),
      Expanded(child: _calendar ? _calendarView(tasks) : _listView(tasks)),
    ]);
  }

  Widget _listView(List<TaskItem> tasks) {
    if (tasks.isEmpty) {
      return EmptyState(
        icon: Icons.checklist_rounded,
        title: 'Görev yok',
        message: 'Hayallerini küçük, yapılabilir görevlere böl.',
        actionLabel: 'Görev ekle',
        onAction: () => showTaskSheet(context, ref),
      );
    }
    final today = Dates.today();
    final weekEnd = today.add(const Duration(days: 7));
    final groups = <String, List<TaskItem>>{
      'Bugün': [],
      'Bu hafta': [],
      'Daha sonra': [],
      'Tarihsiz': [],
      'Tamamlanan': [],
    };
    for (final t in tasks) {
      if (t.isCompleted) {
        groups['Tamamlanan']!.add(t);
      } else if (t.dueDate == null) {
        groups['Tarihsiz']!.add(t);
      } else if (!t.dueDate!.isAfter(today)) {
        groups['Bugün']!.add(t);
      } else if (t.dueDate!.isBefore(weekEnd)) {
        groups['Bu hafta']!.add(t);
      } else {
        groups['Daha sonra']!.add(t);
      }
    }
    final done = groups['Tamamlanan']!;
    if (done.length > 30) groups['Tamamlanan'] = done.sublist(0, 30);
    return ListView(
      padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 100),
      children: [
        for (final e in groups.entries)
          if (e.value.isNotEmpty) ...[
            SectionHeader('${e.key} (${e.value.length})'),
            SoftCard(padding: const EdgeInsets.symmetric(horizontal: 8), child: Column(children: [for (final t in e.value) TaskTile(t)])),
          ],
      ],
    );
  }

  Widget _calendarView(List<TaskItem> tasks) {
    final byDay = <DateTime, List<TaskItem>>{};
    for (final t in tasks) {
      if (t.dueDate != null) byDay.putIfAbsent(Dates.dayOf(t.dueDate!), () => []).add(t);
    }
    final dayTasks = byDay[_selected] ?? const [];
    return ListView(
      padding: const EdgeInsets.fromLTRB(Gap.lg, Gap.md, Gap.lg, 100),
      children: [
        SoftCard(
          padding: const EdgeInsets.all(Gap.sm),
          child: MonthCalendar(
            selected: _selected,
            markers: {for (final e in byDay.entries) e.key: e.value.any((t) => !t.isCompleted) ? 1 : 2},
            onSelect: (d) => setState(() => _selected = d),
          ),
        ),
        SectionHeader(Dates.longWithDay(_selected)),
        if (dayTasks.isEmpty)
          Text('Bu gün için görev yok.', style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant))
        else
          SoftCard(padding: const EdgeInsets.symmetric(horizontal: 8), child: Column(children: [for (final t in dayTasks) TaskTile(t, showDate: false)])),
      ],
    );
  }
}

class _GoalsTab extends ConsumerWidget {
  const _GoalsTab();
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final goals = ref.watch(goalsProvider).value ?? const <Goal>[];
    final tasks = ref.watch(tasksProvider).value ?? const <TaskItem>[];
    if (goals.isEmpty) {
      return EmptyState(
        icon: Icons.flag_outlined,
        title: 'Henüz hedef yok',
        message: 'Günlük, haftalık, aylık ya da yıllık hedefler oluştur.',
        actionLabel: 'Hedef ekle',
        onAction: () => showGoalSheet(context, ref),
      );
    }
    return ListView(
      padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 100),
      children: [
        for (final p in periodLabels.entries)
          if (goals.any((g) => g.period == p.key)) ...[
            SectionHeader('${p.value} hedefler', action: 'Ekle', onAction: () => showGoalSheet(context, ref, period: p.key)),
            for (final g in goals.where((g) => g.period == p.key))
              Padding(padding: const EdgeInsets.only(bottom: 8), child: GoalCard(goal: g, progress: GoalRepository.goalProgress(g, tasks))),
          ],
      ],
    );
  }
}

class GoalCard extends ConsumerWidget {
  const GoalCard({super.key, required this.goal, required this.progress});
  final Goal goal;
  final double progress;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final overdue = !goal.isCompleted && goal.dueDate != null && goal.dueDate!.isBefore(Dates.today());
    return SoftCard(
      padding: const EdgeInsets.all(Gap.md),
      onTap: () => context.push('/goal/${goal.id}'),
      child: Row(children: [
        ProgressRing(value: progress, size: 48, stroke: 5, color: goal.isCompleted ? AppColors.success : null),
        Gap.w12,
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(goal.title,
                style: TextStyle(fontWeight: FontWeight.w700, decoration: goal.isCompleted ? TextDecoration.lineThrough : null)),
            if (goal.dueDate != null)
              Text(goal.isCompleted ? 'Tamamlandı' : Dates.relative(goal.dueDate!),
                  style: TextStyle(fontSize: 12, color: overdue ? AppColors.danger : Theme.of(context).colorScheme.onSurfaceVariant)),
          ]),
        ),
        PriorityChip(goal.priority),
        Checkbox(value: goal.isCompleted, onChanged: (v) => ref.read(goalRepoProvider).setGoalCompleted(goal, v ?? false)),
      ]),
    );
  }
}

class _OverdueTab extends ConsumerWidget {
  const _OverdueTab();
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tasks = (ref.watch(tasksProvider).value ?? const <TaskItem>[]).where(isOverdue).toList();
    final goals = (ref.watch(goalsProvider).value ?? const <Goal>[])
        .where((g) => !g.isCompleted && g.dueDate != null && g.dueDate!.isBefore(Dates.today()))
        .toList();
    final allTasks = ref.watch(tasksProvider).value ?? const <TaskItem>[];
    if (tasks.isEmpty && goals.isEmpty) {
      return const EmptyState(icon: Icons.verified_rounded, title: 'Geciken bir şey yok 🎉', message: 'Harika gidiyorsun!');
    }
    return ListView(
      padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 100),
      children: [
        if (tasks.isNotEmpty) ...[
          SectionHeader('Geciken görevler (${tasks.length})'),
          SoftCard(padding: const EdgeInsets.symmetric(horizontal: 8), child: Column(children: [for (final t in tasks) TaskTile(t)])),
        ],
        if (goals.isNotEmpty) ...[
          SectionHeader('Süresi geçen hedefler (${goals.length})'),
          for (final g in goals) Padding(padding: const EdgeInsets.only(bottom: 8), child: GoalCard(goal: g, progress: GoalRepository.goalProgress(g, allTasks))),
        ],
      ],
    );
  }
}

/// Bağımlılıksız, hafif aylık takvim. markers: 1 = bekleyen, 2 = tamamlanmış
class MonthCalendar extends StatefulWidget {
  const MonthCalendar({super.key, required this.selected, required this.onSelect, this.markers = const {}, this.markerColors});
  final DateTime selected;
  final ValueChanged<DateTime> onSelect;
  final Map<DateTime, int> markers;
  final Map<int, Color>? markerColors;

  @override
  State<MonthCalendar> createState() => _MonthCalendarState();
}

class _MonthCalendarState extends State<MonthCalendar> {
  late DateTime _month = Dates.startOfMonth(widget.selected);

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final first = _month;
    final offset = first.weekday - 1;
    final days = DateTime(first.year, first.month + 1, 0).day;
    final cells = offset + days;
    final rows = (cells / 7).ceil();
    final today = Dates.today();
    return Column(children: [
      Row(children: [
        IconButton(tooltip: 'Önceki ay', icon: const Icon(Icons.chevron_left), onPressed: () => setState(() => _month = DateTime(_month.year, _month.month - 1))),
        Expanded(child: Text(Dates.monthYear(_month), textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.w700))),
        IconButton(tooltip: 'Sonraki ay', icon: const Icon(Icons.chevron_right), onPressed: () => setState(() => _month = DateTime(_month.year, _month.month + 1))),
      ]),
      Row(children: [
        for (final l in Dates.weekdayLetters)
          Expanded(child: Center(child: Text(l, style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant)))),
      ]),
      Gap.h4,
      for (var r = 0; r < rows; r++)
        Row(children: [
          for (var c = 0; c < 7; c++)
            Expanded(
              child: Builder(builder: (_) {
                final idx = r * 7 + c - offset;
                if (idx < 0 || idx >= days) return const SizedBox(height: 42);
                final d = DateTime(first.year, first.month, idx + 1);
                final sel = Dates.sameDay(d, widget.selected);
                final isToday = Dates.sameDay(d, today);
                final m = widget.markers[d];
                final markerColor = widget.markerColors?[m] ?? (m == 1 ? cs.primary : AppColors.success);
                return InkWell(
                  borderRadius: BorderRadius.circular(12),
                  onTap: () => widget.onSelect(d),
                  child: Container(
                    height: 42,
                    margin: const EdgeInsets.all(1),
                    decoration: BoxDecoration(
                      color: sel ? cs.primary : null,
                      borderRadius: BorderRadius.circular(12),
                      border: isToday && !sel ? Border.all(color: cs.primary) : null,
                    ),
                    child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                      Text('${idx + 1}', style: TextStyle(color: sel ? Colors.white : null, fontWeight: isToday ? FontWeight.w800 : null)),
                      if (m != null)
                        Container(width: 5, height: 5, decoration: BoxDecoration(color: sel ? Colors.white : markerColor, shape: BoxShape.circle)),
                    ]),
                  ),
                );
              }),
            ),
        ]),
    ]);
  }
}
