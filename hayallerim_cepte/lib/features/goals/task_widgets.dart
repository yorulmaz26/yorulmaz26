import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/database/app_database.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/dates.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';

const repeatLabels = {'none': 'Tekrar yok', 'daily': 'Her gün', 'weekly': 'Her hafta', 'monthly': 'Her ay'};
const periodLabels = {'daily': 'Günlük', 'weekly': 'Haftalık', 'monthly': 'Aylık', 'yearly': 'Yıllık'};

bool isOverdue(TaskItem t) => !t.isCompleted && t.dueDate != null && t.dueDate!.isBefore(Dates.today());

class TaskTile extends ConsumerWidget {
  const TaskTile(this.task, {super.key, this.showDate = true});
  final TaskItem task;
  final bool showDate;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = task;
    final overdue = isOverdue(t);
    final cs = Theme.of(context).colorScheme;
    final sub = <String>[
      if (showDate && t.dueDate != null) Dates.relative(t.dueDate!),
      if (t.dueMinutes != null) Dates.minutesToHm(t.dueMinutes!),
      if (t.repeatRule != 'none') repeatLabels[t.repeatRule]!,
    ];
    return Dismissible(
      key: ValueKey('task-${t.id}'),
      direction: DismissDirection.endToStart,
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 20),
        color: AppColors.danger,
        child: const Icon(Icons.delete_outline, color: Colors.white),
      ),
      confirmDismiss: (_) => confirm(context, title: 'Görev silinsin mi?', message: t.title, ok: 'Sil', destructive: true),
      onDismissed: (_) => ref.read(goalRepoProvider).deleteTask(t),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 4),
        leading: Checkbox(
          value: t.isCompleted,
          shape: const CircleBorder(),
          onChanged: (_) => ref.read(goalRepoProvider).toggleTask(t),
        ),
        title: Text(
          t.title,
          style: TextStyle(
            decoration: t.isCompleted ? TextDecoration.lineThrough : null,
            color: t.isCompleted ? cs.onSurfaceVariant : null,
            fontWeight: FontWeight.w600,
          ),
        ),
        subtitle: sub.isEmpty
            ? null
            : Text(sub.join(' · '), style: TextStyle(color: overdue ? AppColors.danger : cs.onSurfaceVariant, fontSize: 12)),
        trailing: Row(mainAxisSize: MainAxisSize.min, children: [
          if (t.reminder) Icon(Icons.notifications_active_outlined, size: 16, color: cs.onSurfaceVariant),
          Gap.w4,
          Container(width: 8, height: 8, decoration: BoxDecoration(color: AppColors.priority(t.priority), shape: BoxShape.circle)),
        ]),
        onTap: () => showTaskSheet(context, ref, existing: t),
      ),
    );
  }
}

/// Görev ekleme / düzenleme sayfası
Future<void> showTaskSheet(BuildContext context, WidgetRef ref, {TaskItem? existing, int? goalId, int? dreamId, DateTime? date}) {
  return showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    builder: (_) => _TaskSheet(existing: existing, goalId: goalId, dreamId: dreamId, date: date),
  );
}

class _TaskSheet extends ConsumerStatefulWidget {
  const _TaskSheet({this.existing, this.goalId, this.dreamId, this.date});
  final TaskItem? existing;
  final int? goalId;
  final int? dreamId;
  final DateTime? date;

  @override
  ConsumerState<_TaskSheet> createState() => _TaskSheetState();
}

class _TaskSheetState extends ConsumerState<_TaskSheet> {
  late final _title = TextEditingController(text: widget.existing?.title);
  late final _note = TextEditingController(text: widget.existing?.note);
  late DateTime? _date = widget.existing?.dueDate ?? widget.date ?? Dates.today();
  late int? _minutes = widget.existing?.dueMinutes;
  late int _priority = widget.existing?.priority ?? 1;
  late String _repeat = widget.existing?.repeatRule ?? 'none';
  late bool _reminder = widget.existing?.reminder ?? false;
  late int? _goalId = widget.existing?.goalId ?? widget.goalId;

  @override
  void dispose() {
    _title.dispose();
    _note.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final title = _title.text.trim();
    if (title.isEmpty) return;
    final repo = ref.read(goalRepoProvider);
    if (widget.existing == null) {
      await repo.createTask(
        title: title,
        note: _note.text,
        goalId: _goalId,
        dreamId: widget.dreamId,
        dueDate: _date,
        dueMinutes: _minutes,
        priority: _priority,
        repeatRule: _repeat,
        reminder: _reminder,
      );
    } else {
      await repo.updateTask(widget.existing!.copyWith(
        title: title,
        note: _note.text,
        goalId: Value(_goalId),
        dueDate: Value(_date),
        dueMinutes: Value(_minutes),
        priority: _priority,
        repeatRule: _repeat,
        reminder: _reminder,
      ));
    }
    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final goals = ref.watch(goalsProvider).value ?? const [];
    return Padding(
      padding: EdgeInsets.only(left: Gap.lg, right: Gap.lg, bottom: MediaQuery.viewInsetsOf(context).bottom + Gap.lg),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(widget.existing == null ? 'Yeni görev' : 'Görevi düzenle', style: Theme.of(context).textTheme.titleLarge),
            Gap.h16,
            TextField(controller: _title, autofocus: widget.existing == null, maxLength: 160, decoration: const InputDecoration(labelText: 'Görev')),
            TextField(controller: _note, maxLines: 2, decoration: const InputDecoration(labelText: 'Not (isteğe bağlı)')),
            Gap.h12,
            Wrap(spacing: 8, runSpacing: 8, children: [
              ActionChip(
                avatar: const Icon(Icons.event, size: 18),
                label: Text(_date == null ? 'Tarih yok' : Dates.short(_date!)),
                onPressed: () async {
                  final d = await pickDate(context, initial: _date);
                  if (d != null) setState(() => _date = d);
                },
              ),
              ActionChip(
                avatar: const Icon(Icons.schedule, size: 18),
                label: Text(_minutes == null ? 'Saat yok' : Dates.minutesToHm(_minutes!)),
                onPressed: () async {
                  final t = await pickTime(context);
                  if (t != null) setState(() => _minutes = t.hour * 60 + t.minute);
                },
              ),
              if (_date != null) ActionChip(label: const Text('Tarihi kaldır'), onPressed: () => setState(() => _date = null)),
            ]),
            Gap.h12,
            const Text('Öncelik', style: TextStyle(fontWeight: FontWeight.w600)),
            Gap.h8,
            ChoiceChips<int>(values: const [0, 1, 2], labels: PriorityChip.labels, selected: _priority, onSelected: (v) => setState(() => _priority = v)),
            Gap.h12,
            const Text('Tekrar', style: TextStyle(fontWeight: FontWeight.w600)),
            Gap.h8,
            ChoiceChips<String>(
              values: repeatLabels.keys.toList(),
              labels: repeatLabels.values.toList(),
              selected: _repeat,
              onSelected: (v) => setState(() => _repeat = v),
            ),
            if (goals.isNotEmpty) ...[
              Gap.h12,
              DropdownButtonFormField<int?>(
                initialValue: _goalId,
                decoration: const InputDecoration(labelText: 'Bağlı hedef'),
                items: [
                  const DropdownMenuItem(value: null, child: Text('Hedefe bağlı değil')),
                  for (final g in goals) DropdownMenuItem(value: g.id, child: Text(g.title, overflow: TextOverflow.ellipsis)),
                ],
                onChanged: (v) => setState(() => _goalId = v),
              ),
            ],
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              value: _reminder,
              title: const Text('Hatırlatıcı'),
              subtitle: Text(_date == null ? 'Hatırlatıcı için tarih seçin' : 'Seçilen gün ${Dates.minutesToHm(_minutes ?? 540)}'),
              onChanged: _date == null ? null : (v) => setState(() => _reminder = v),
            ),
            Gap.h8,
            FilledButton(onPressed: _save, child: const Text('Kaydet')),
          ],
        ),
      ),
    );
  }
}
