import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/settings/settings_controller.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/dates.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';
import 'focus_controller.dart';

class FocusScreen extends ConsumerStatefulWidget {
  const FocusScreen({super.key});
  @override
  ConsumerState<FocusScreen> createState() => _FocusScreenState();
}

class _FocusScreenState extends ConsumerState<FocusScreen> with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState s) {
    if (s == AppLifecycleState.resumed) ref.read(focusProvider.notifier).tick();
  }

  @override
  Widget build(BuildContext context) {
    final st = ref.watch(focusProvider);
    final ctrl = ref.read(focusProvider.notifier);
    final settings = ref.watch(settingsProvider);
    final isWork = st.kind == 'work';
    final color = isWork ? AppColors.purple : AppColors.turquoise;
    final mm = (st.remainingSeconds ~/ 60).toString().padLeft(2, '0');
    final ss = (st.remainingSeconds % 60).toString().padLeft(2, '0');

    ref.listen(focusProvider.select((s) => s.justFinished), (_, f) {
      if (f == null) return;
      ctrl.consumeFinished();
      toast(context, f == 'work' ? 'Odak oturumu tamamlandı! +10 puan 🎯' : 'Mola bitti, hadi devam!');
    });

    final goals = (ref.watch(goalsProvider).value ?? const []).where((g) => !g.isCompleted).toList();
    final dreams = (ref.watch(dreamViewsProvider).value ?? const []).where((d) => !d.dream.isCompleted && !d.dream.isHidden).toList();
    final idle = st.phase == FocusPhase.idle;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Odaklanma'),
        actions: [IconButton(tooltip: 'Süre ayarları', icon: const Icon(Icons.tune), onPressed: () => _settings(context))],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 60),
        children: [
          Center(
            child: SegmentedButton<String>(
              segments: const [
                ButtonSegment(value: 'work', label: Text('Çalışma'), icon: Icon(Icons.center_focus_strong)),
                ButtonSegment(value: 'break', label: Text('Mola'), icon: Icon(Icons.coffee)),
              ],
              selected: {st.kind},
              onSelectionChanged: idle
                  ? (v) => ctrl.configure(
                        kind: v.first,
                        minutes: v.first == 'work' ? settings.focusWork : settings.focusBreak,
                        goalId: st.goalId,
                        dreamId: st.dreamId,
                      )
                  : null,
            ),
          ),
          Gap.h24,
          Center(
            child: ProgressRing(
              value: st.progress,
              size: 250,
              stroke: 14,
              color: color,
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                Text('$mm:$ss',
                    style: const TextStyle(fontSize: 56, fontWeight: FontWeight.w300, fontFeatures: [FontFeature.tabularFigures()])),
                Text(isWork ? 'Odaklan' : 'Dinlen', style: TextStyle(color: color, fontWeight: FontWeight.w700)),
                if (st.completedWorkSessions > 0) Text('${st.completedWorkSessions} oturum', style: const TextStyle(fontSize: 12)),
              ]),
            ),
          ),
          Gap.h24,
          Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            if (!idle)
              IconButton.outlined(
                tooltip: 'Bitir',
                iconSize: 28,
                onPressed: () async {
                  if (await confirm(context, title: 'Oturum bitirilsin mi?', message: 'Geçen süre kaydedilir.', ok: 'Bitir')) await ctrl.stop();
                },
                icon: const Icon(Icons.stop_rounded),
              )
            else
              IconButton.outlined(tooltip: 'Sıfırla', iconSize: 28, onPressed: ctrl.reset, icon: const Icon(Icons.refresh)),
            Gap.w24,
            SizedBox(
              width: 84,
              height: 84,
              child: FilledButton(
                style: FilledButton.styleFrom(shape: const CircleBorder(), backgroundColor: color),
                onPressed: st.phase == FocusPhase.running ? ctrl.pause : ctrl.start,
                child: Icon(st.phase == FocusPhase.running ? Icons.pause_rounded : Icons.play_arrow_rounded, size: 40),
              ),
            ),
            Gap.w24,
            const SizedBox(width: 48),
          ]),
          Gap.h16,
          if (idle && isWork) ...[
            DropdownButtonFormField<String>(
              initialValue: st.goalId != null ? 'g${st.goalId}' : (st.dreamId != null ? 'd${st.dreamId}' : ''),
              isExpanded: true,
              decoration: const InputDecoration(labelText: 'Bu oturum neye hizmet ediyor?', prefixIcon: Icon(Icons.link)),
              items: [
                const DropdownMenuItem(value: '', child: Text('Bağlantı yok')),
                for (final g in goals) DropdownMenuItem(value: 'g${g.id}', child: Text('Hedef: ${g.title}', overflow: TextOverflow.ellipsis)),
                for (final d in dreams) DropdownMenuItem(value: 'd${d.dream.id}', child: Text('Hayal: ${d.dream.title}', overflow: TextOverflow.ellipsis)),
              ],
              onChanged: (v) {
                final id = v == null || v.isEmpty ? null : int.tryParse(v.substring(1));
                ctrl.configure(
                  kind: 'work',
                  minutes: st.plannedSeconds ~/ 60,
                  goalId: v != null && v.startsWith('g') ? id : null,
                  dreamId: v != null && v.startsWith('d') ? id : null,
                );
              },
            ),
          ],
          Gap.h8,
          Text('Uygulamadan çıksan da süre doğru hesaplanır; bitişte bildirim alırsın.',
              textAlign: TextAlign.center, style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant)),
          const _FocusStats(),
        ],
      ),
    );
  }

  void _settings(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      builder: (_) => Consumer(builder: (context, ref, _) {
        final s = ref.watch(settingsProvider);
        final c = ref.read(settingsProvider.notifier);
        Widget row(String label, int v, int min, int max, AppSettingsState Function(int) f) => ListTile(
              title: Text(label),
              subtitle: Slider(value: v.toDouble(), min: min.toDouble(), max: max.toDouble(), divisions: max - min, label: '$v dk', onChanged: (x) => c.update((_) => f(x.round()))),
              trailing: Text('$v dk', style: const TextStyle(fontWeight: FontWeight.w700)),
            );
        return SafeArea(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            row('Çalışma', s.focusWork, 5, 90, (v) => s.copyWith(focusWork: v)),
            row('Kısa mola', s.focusBreak, 1, 30, (v) => s.copyWith(focusBreak: v)),
            row('Uzun mola (4 oturumda bir)', s.focusLongBreak, 5, 60, (v) => s.copyWith(focusLongBreak: v)),
            TextButton(
              onPressed: () {
                ref.read(focusProvider.notifier).reset();
                Navigator.pop(context);
              },
              child: const Text('Uygula'),
            ),
          ]),
        );
      }),
    );
  }
}

class _FocusStats extends ConsumerWidget {
  const _FocusStats();
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final sessions = (ref.watch(focusSessionsProvider).value ?? const []).where((s) => s.kind == 'work').toList();
    final today = Dates.today();
    final todaySecs = sessions.where((s) => Dates.sameDay(s.startedAt, today)).fold<int>(0, (a, s) => a + s.actualSeconds);
    final start = today.subtract(const Duration(days: 6));
    final week = List<int>.filled(7, 0);
    for (final s in sessions) {
      final i = Dates.daysBetween(start, s.startedAt);
      if (i >= 0 && i < 7) week[i] += s.actualSeconds ~/ 60;
    }
    final total = sessions.fold<int>(0, (a, s) => a + s.actualSeconds);
    final maxY = (week.fold<int>(0, (a, b) => a > b ? a : b) + 10).toDouble();
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      const SectionHeader('Odak istatistiklerin'),
      Row(children: [
        Expanded(child: StatTile(icon: Icons.today, value: Dates.durationLabel(todaySecs), label: 'Bugün')),
        Gap.w8,
        Expanded(child: StatTile(icon: Icons.date_range, value: Dates.durationLabel(week.fold(0, (a, b) => a + b) * 60), label: 'Son 7 gün', color: AppColors.turquoise)),
        Gap.w8,
        Expanded(child: StatTile(icon: Icons.all_inclusive, value: Dates.durationLabel(total), label: 'Toplam', color: AppColors.gold)),
      ]),
      Gap.h12,
      SoftCard(
        child: SizedBox(
          height: 150,
          child: BarChart(BarChartData(
            maxY: maxY,
            gridData: const FlGridData(show: false),
            borderData: FlBorderData(show: false),
            titlesData: FlTitlesData(
              leftTitles: const AxisTitles(),
              rightTitles: const AxisTitles(),
              topTitles: const AxisTitles(),
              bottomTitles: AxisTitles(
                sideTitles: SideTitles(
                  showTitles: true,
                  getTitlesWidget: (v, _) => Text(Dates.weekdayShort(start.add(Duration(days: v.toInt()))), style: const TextStyle(fontSize: 11)),
                ),
              ),
            ),
            barGroups: [
              for (var i = 0; i < 7; i++)
                BarChartGroupData(x: i, barRods: [BarChartRodData(toY: week[i].toDouble(), width: 16, color: AppColors.purple, borderRadius: BorderRadius.circular(6))]),
            ],
          )),
        ),
      ),
      const SectionHeader('Çalışma geçmişi'),
      if (sessions.isEmpty) const Text('Henüz oturum yok.'),
      for (final s in sessions.take(15))
        ListTile(
          contentPadding: EdgeInsets.zero,
          leading: Icon(s.completed ? Icons.check_circle : Icons.timelapse, color: s.completed ? AppColors.success : AppColors.warning),
          title: Text(Dates.durationLabel(s.actualSeconds)),
          subtitle: Text(Dates.dateTime(s.startedAt)),
        ),
    ]);
  }
}
