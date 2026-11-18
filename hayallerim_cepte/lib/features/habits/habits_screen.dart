import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/database/app_database.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/app_icons.dart';
import '../../core/utils/dates.dart';
import '../../core/widgets/common.dart';
import '../../data/habit_repository.dart';
import '../../data/providers.dart';
import '../goals/goals_screen.dart' show MonthCalendar;

const _templates = <(String, String, int)>[
  ('Kitap okumak', 'book', 0xFF6D5DFB),
  ('Spor yapmak', 'run', 0xFFEF4444),
  ('Su içmek', 'water', 0xFF3B82F6),
  ('Ders çalışmak', 'school', 0xFFF97316),
  ('Yabancı dil öğrenmek', 'language', 0xFF20B8A6),
  ('Para biriktirmek', 'money', 0xFFD6AD60),
  ('Meditasyon', 'meditation', 0xFF14B8A6),
  ('Erken uyumak', 'sleep', 0xFF101827),
];

class HabitsScreen extends ConsumerWidget {
  const HabitsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final stats = ref.watch(habitStatsProvider);
    final today = Dates.today();
    final todayPlanned = stats.where((s) => Dates.maskHas(s.habit.weekdaysMask, today)).toList();
    final todayDone = todayPlanned.where((s) => s.doneOn(today)).length;
    return Scaffold(
      appBar: AppBar(title: const Text('Alışkanlıklarım')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => showHabitSheet(context),
        icon: const Icon(Icons.add),
        label: const Text('Alışkanlık'),
      ),
      body: stats.isEmpty
          ? EmptyState(
              icon: Icons.repeat_rounded,
              title: 'Küçük alışkanlıklar, büyük hayaller',
              message: 'Her gün tekrarlayacağın bir alışkanlık ekle; serini büyüt, rozetleri topla.',
              actionLabel: 'Alışkanlık ekle',
              onAction: () => showHabitSheet(context),
            )
          : ListView(
              padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 100),
              children: [
                GradientCard(
                  gradient: const LinearGradient(colors: [AppColors.turquoise, Color(0xFF3B82F6)]),
                  child: Row(children: [
                    ProgressRing(
                      value: todayPlanned.isEmpty ? 0 : todayDone / todayPlanned.length,
                      size: 64,
                      color: Colors.white,
                      background: Colors.white24,
                      child: Text('$todayDone/${todayPlanned.length}', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800)),
                    ),
                    Gap.w16,
                    const Expanded(child: Text('Bugünün alışkanlıkları', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700))),
                  ]),
                ),
                Gap.h12,
                for (final s in stats) Padding(padding: const EdgeInsets.only(bottom: 10), child: HabitCard(s)),
              ],
            ),
    );
  }
}

class HabitCard extends ConsumerWidget {
  const HabitCard(this.s, {super.key});
  final HabitStats s;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final h = s.habit;
    final color = Color(h.color);
    final week = Dates.startOfWeek(DateTime.now());
    final today = Dates.today();
    final challenge = s.challengeProgress;
    return SoftCard(
      padding: const EdgeInsets.all(Gap.md),
      onTap: () => showModalBottomSheet(context: context, isScrollControlled: true, builder: (_) => _HabitDetail(habitId: h.id)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          CircleAvatar(backgroundColor: color.withValues(alpha: 0.15), child: Icon(AppIcons.of(h.iconKey), color: color)),
          Gap.w12,
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(h.title, style: const TextStyle(fontWeight: FontWeight.w700)),
              Text('🔥 ${s.streak} gün seri · En iyi ${s.best} · %${(s.rate * 100).round()}',
                  style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant)),
            ]),
          ),
          if (Dates.maskHas(h.weekdaysMask, today))
            IconButton.filled(
              tooltip: s.doneOn(today) ? 'Bugünü geri al' : 'Bugün yaptım',
              style: IconButton.styleFrom(backgroundColor: s.doneOn(today) ? color : color.withValues(alpha: 0.15)),
              onPressed: () => ref.read(habitRepoProvider).toggleDay(h, today),
              icon: Icon(Icons.check, color: s.doneOn(today) ? Colors.white : color),
            ),
        ]),
        Gap.h12,
        Row(children: [
          for (var i = 0; i < 7; i++)
            Expanded(
              child: Builder(builder: (_) {
                final d = week.add(Duration(days: i));
                final planned = Dates.maskHas(h.weekdaysMask, d);
                final done = s.doneOn(d);
                final future = d.isAfter(today);
                return GestureDetector(
                  onTap: future || !planned ? null : () => ref.read(habitRepoProvider).toggleDay(h, d),
                  child: Semantics(
                    label: '${Dates.weekdayLetters[i]} ${done ? 'yapıldı' : 'yapılmadı'}',
                    child: Column(children: [
                      Text(Dates.weekdayLetters[i], style: const TextStyle(fontSize: 11)),
                      Gap.h4,
                      AnimatedContainer(
                        duration: const Duration(milliseconds: 200),
                        width: 30,
                        height: 30,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: done ? color : (planned ? color.withValues(alpha: future ? 0.05 : 0.12) : Colors.transparent),
                          border: Dates.sameDay(d, today) ? Border.all(color: color, width: 2) : null,
                        ),
                        child: done ? const Icon(Icons.check, size: 16, color: Colors.white) : null,
                      ),
                    ]),
                  ),
                );
              }),
            ),
        ]),
        if (challenge != null) ...[
          Gap.h12,
          Row(children: [
            const Icon(Icons.emoji_events_outlined, size: 16, color: AppColors.gold),
            Gap.w4,
            Text('${h.challengeDays} günlük meydan okuma', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
            const Spacer(),
            Text('%${(challenge * 100).round()}', style: const TextStyle(fontSize: 12)),
          ]),
          Gap.h4,
          LinearBar(value: challenge, color: AppColors.gold, height: 6),
        ],
      ]),
    );
  }
}

class _HabitDetail extends ConsumerStatefulWidget {
  const _HabitDetail({required this.habitId});
  final int habitId;
  @override
  ConsumerState<_HabitDetail> createState() => _HabitDetailState();
}

class _HabitDetailState extends ConsumerState<_HabitDetail> {
  DateTime _sel = Dates.today();

  @override
  Widget build(BuildContext context) {
    final s = ref.watch(habitStatsProvider).where((x) => x.habit.id == widget.habitId).firstOrNull;
    if (s == null) return const SizedBox(height: 200);
    final h = s.habit;
    return Padding(
      padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, Gap.lg),
      child: SingleChildScrollView(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min, children: [
          Row(children: [
            Expanded(child: Text(h.title, style: Theme.of(context).textTheme.titleLarge)),
            IconButton(tooltip: 'Düzenle', icon: const Icon(Icons.edit_outlined), onPressed: () => showHabitSheet(context, existing: h)),
            IconButton(
              tooltip: 'Arşivle',
              icon: const Icon(Icons.archive_outlined),
              onPressed: () async {
                if (await confirm(context, title: 'Alışkanlık arşivlensin mi?', message: 'Geçmiş kayıtların ve rozetlerin korunur.', ok: 'Arşivle')) {
                  await ref.read(habitRepoProvider).archiveHabit(h);
                  if (context.mounted) Navigator.pop(context);
                }
              },
            ),
          ]),
          Row(children: [
            Expanded(child: StatTile(icon: Icons.local_fire_department, value: '${s.streak}', label: 'Güncel seri', color: AppColors.warning)),
            Gap.w8,
            Expanded(child: StatTile(icon: Icons.military_tech, value: '${s.best}', label: 'En iyi seri', color: AppColors.gold)),
            Gap.w8,
            Expanded(child: StatTile(icon: Icons.percent, value: '%${(s.rate * 100).round()}', label: 'Tamamlanma')),
          ]),
          Gap.h12,
          MonthCalendar(
            selected: _sel,
            markers: {for (final d in s.days) d: 2},
            markerColors: {2: Color(h.color)},
            onSelect: (d) {
              setState(() => _sel = d);
              if (!d.isAfter(Dates.today())) ref.read(habitRepoProvider).toggleDay(h, d);
            },
          ),
          Text('Geçmiş bir günü işaretlemek ya da kaldırmak için güne dokun.',
              textAlign: TextAlign.center, style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant)),
        ]),
      ),
    );
  }
}

Future<void> showHabitSheet(BuildContext context, {Habit? existing}) =>
    showModalBottomSheet(context: context, isScrollControlled: true, builder: (_) => _HabitSheet(existing: existing));

class _HabitSheet extends ConsumerStatefulWidget {
  const _HabitSheet({this.existing});
  final Habit? existing;
  @override
  ConsumerState<_HabitSheet> createState() => _HabitSheetState();
}

class _HabitSheetState extends ConsumerState<_HabitSheet> {
  late final _title = TextEditingController(text: widget.existing?.title);
  late String _icon = widget.existing?.iconKey ?? 'check';
  late int _color = widget.existing?.color ?? 0xFF20B8A6;
  late int _mask = widget.existing?.weekdaysMask ?? 127;
  late int? _reminder = widget.existing?.reminderMinutes;
  late int? _challenge = widget.existing?.challengeDays;

  @override
  void dispose() {
    _title.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_title.text.trim().isEmpty || _mask == 0) return;
    final repo = ref.read(habitRepoProvider);
    if (widget.existing == null) {
      await repo.createHabit(
          title: _title.text, iconKey: _icon, color: _color, weekdaysMask: _mask, reminderMinutes: _reminder, challengeDays: _challenge);
    } else {
      await repo.updateHabit(widget.existing!.copyWith(
        title: _title.text.trim(),
        iconKey: _icon,
        color: _color,
        weekdaysMask: _mask,
        reminderMinutes: Value(_reminder),
        challengeDays: Value(_challenge),
      ));
    }
    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(left: Gap.lg, right: Gap.lg, bottom: MediaQuery.viewInsetsOf(context).bottom + Gap.lg),
      child: SingleChildScrollView(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min, children: [
          Text(widget.existing == null ? 'Yeni alışkanlık' : 'Alışkanlığı düzenle', style: Theme.of(context).textTheme.titleLarge),
          if (widget.existing == null) ...[
            Gap.h12,
            SizedBox(
              height: 40,
              child: ListView(scrollDirection: Axis.horizontal, children: [
                for (final t in _templates)
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ActionChip(
                      avatar: Icon(AppIcons.of(t.$2), size: 16, color: Color(t.$3)),
                      label: Text(t.$1),
                      onPressed: () => setState(() {
                        _title.text = t.$1;
                        _icon = t.$2;
                        _color = t.$3;
                      }),
                    ),
                  ),
              ]),
            ),
          ],
          Gap.h12,
          TextField(controller: _title, maxLength: 80, decoration: const InputDecoration(labelText: 'Alışkanlık')),
          const Text('Günler', style: TextStyle(fontWeight: FontWeight.w600)),
          Gap.h8,
          Wrap(spacing: 6, children: [
            for (var i = 0; i < 7; i++)
              FilterChip(
                label: Text(Dates.weekdayLetters[i]),
                selected: (_mask >> i) & 1 == 1,
                onSelected: (v) => setState(() => _mask = v ? _mask | (1 << i) : _mask & ~(1 << i)),
              ),
          ]),
          Gap.h12,
          const Text('Meydan okuma', style: TextStyle(fontWeight: FontWeight.w600)),
          Gap.h8,
          ChoiceChips<int?>(
            values: const [null, 7, 21, 30, 100],
            labels: const ['Yok', '7 gün', '21 gün', '30 gün', '100 gün'],
            selected: _challenge,
            onSelected: (v) => setState(() => _challenge = v),
          ),
          Gap.h12,
          const Text('İkon ve renk', style: TextStyle(fontWeight: FontWeight.w600)),
          Gap.h8,
          Wrap(spacing: 6, runSpacing: 6, children: [
            for (final k in const ['check', 'book', 'run', 'water', 'school', 'language', 'money', 'meditation', 'sleep', 'food', 'music', 'code'])
              ChoiceChip(label: Icon(AppIcons.of(k), size: 18), selected: _icon == k, onSelected: (_) => setState(() => _icon = k)),
          ]),
          Gap.h8,
          Wrap(spacing: 10, children: [
            for (final c in AppColors.accents.map((e) => e.toARGB32()))
              GestureDetector(
                onTap: () => setState(() => _color = c),
                child: CircleAvatar(radius: 15, backgroundColor: Color(c), child: _color == c ? const Icon(Icons.check, size: 15, color: Colors.white) : null),
              ),
          ]),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Hatırlatıcı'),
            subtitle: Text(_reminder == null ? 'Kapalı' : 'Seçili günlerde ${Dates.minutesToHm(_reminder!)}'),
            value: _reminder != null,
            onChanged: (v) async {
              if (!v) return setState(() => _reminder = null);
              final t = await pickTime(context, initial: const TimeOfDay(hour: 20, minute: 0));
              if (t != null) setState(() => _reminder = t.hour * 60 + t.minute);
            },
          ),
          Gap.h8,
          FilledButton(onPressed: _save, child: const Text('Kaydet')),
        ]),
      ),
    );
  }
}
