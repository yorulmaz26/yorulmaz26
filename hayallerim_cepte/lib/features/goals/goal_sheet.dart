import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/database/app_database.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/dates.dart';
import '../../core/widgets/common.dart';
import '../../data/goal_repository.dart';
import '../../data/providers.dart';
import 'task_widgets.dart';

Future<void> showGoalSheet(BuildContext context, WidgetRef ref, {Goal? existing, int? dreamId, String? period}) => showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (_) => _GoalSheet(existing: existing, dreamId: dreamId, period: period),
    );

class _GoalSheet extends ConsumerStatefulWidget {
  const _GoalSheet({this.existing, this.dreamId, this.period});
  final Goal? existing;
  final int? dreamId;
  final String? period;
  @override
  ConsumerState<_GoalSheet> createState() => _GoalSheetState();
}

class _GoalSheetState extends ConsumerState<_GoalSheet> {
  late final _title = TextEditingController(text: widget.existing?.title);
  late final _desc = TextEditingController(text: widget.existing?.description);
  late String _period = widget.existing?.period ?? widget.period ?? 'monthly';
  late int? _dream = widget.existing?.dreamId ?? widget.dreamId;
  late int _priority = widget.existing?.priority ?? 1;
  late DateTime? _due = widget.existing?.dueDate;

  @override
  void dispose() {
    _title.dispose();
    _desc.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_title.text.trim().isEmpty) return;
    final repo = ref.read(goalRepoProvider);
    if (widget.existing == null) {
      await repo.createGoal(title: _title.text, description: _desc.text, period: _period, dreamId: _dream, priority: _priority, dueDate: _due);
    } else {
      await repo.updateGoal(widget.existing!.copyWith(
        title: _title.text.trim(),
        description: _desc.text,
        period: _period,
        dreamId: Value(_dream),
        priority: _priority,
        dueDate: Value(_due ?? GoalRepository.defaultDue(_period)),
      ));
    }
    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final dreams = (ref.watch(dreamViewsProvider).value ?? const []).where((d) => !d.dream.isCompleted).toList();
    final due = _due ?? GoalRepository.defaultDue(_period);
    return Padding(
      padding: EdgeInsets.only(left: Gap.lg, right: Gap.lg, bottom: MediaQuery.viewInsetsOf(context).bottom + Gap.lg),
      child: SingleChildScrollView(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min, children: [
          Text(widget.existing == null ? 'Yeni hedef' : 'Hedefi düzenle', style: Theme.of(context).textTheme.titleLarge),
          Gap.h12,
          TextField(controller: _title, autofocus: widget.existing == null, maxLength: 160, decoration: const InputDecoration(labelText: 'Hedef')),
          TextField(controller: _desc, maxLines: 2, decoration: const InputDecoration(labelText: 'Açıklama')),
          Gap.h12,
          const Text('Dönem', style: TextStyle(fontWeight: FontWeight.w600)),
          Gap.h8,
          ChoiceChips<String>(
            values: periodLabels.keys.toList(),
            labels: periodLabels.values.toList(),
            selected: _period,
            onSelected: (v) => setState(() {
              _period = v;
              _due = null;
            }),
          ),
          Gap.h12,
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.event),
            title: const Text('Bitiş tarihi'),
            subtitle: Text(Dates.long(due)),
            onTap: () async {
              final d = await pickDate(context, initial: due);
              if (d != null) setState(() => _due = d);
            },
          ),
          const Text('Öncelik', style: TextStyle(fontWeight: FontWeight.w600)),
          Gap.h8,
          ChoiceChips<int>(values: const [0, 1, 2], labels: PriorityChip.labels, selected: _priority, onSelected: (v) => setState(() => _priority = v)),
          Gap.h12,
          DropdownButtonFormField<int?>(
            initialValue: dreams.any((d) => d.dream.id == _dream) ? _dream : null,
            isExpanded: true,
            decoration: const InputDecoration(labelText: 'Bağlı hayal'),
            items: [
              const DropdownMenuItem(value: null, child: Text('Hayale bağlı değil')),
              for (final d in dreams.where((d) => !d.dream.isHidden)) DropdownMenuItem(value: d.dream.id, child: Text(d.dream.title, overflow: TextOverflow.ellipsis)),
            ],
            onChanged: (v) => setState(() => _dream = v),
          ),
          Gap.h16,
          FilledButton(onPressed: _save, child: const Text('Kaydet')),
        ]),
      ),
    );
  }
}
