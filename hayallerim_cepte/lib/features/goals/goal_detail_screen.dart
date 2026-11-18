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

final _goalProvider = StreamProvider.family<Goal?, int>((ref, id) => ref.watch(goalRepoProvider).watchGoal(id));
final _goalTasksProvider = StreamProvider.family<List<TaskItem>, int>((ref, id) => ref.watch(goalRepoProvider).watchTasksForGoal(id));

class GoalDetailScreen extends ConsumerWidget {
  const GoalDetailScreen({super.key, required this.id});
  final int id;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final g = ref.watch(_goalProvider(id)).value;
    final tasks = ref.watch(_goalTasksProvider(id)).value ?? const [];
    if (g == null) return Scaffold(appBar: AppBar(), body: const Center(child: CircularProgressIndicator()));
    final dreams = ref.watch(dreamViewsProvider).value ?? const [];
    final dream = dreams.where((d) => d.dream.id == g.dreamId).firstOrNull;
    final progress = GoalRepository.goalProgress(g, tasks);
    return Scaffold(
      appBar: AppBar(
        title: Text(periodLabels[g.period] ?? 'Hedef'),
        actions: [
          IconButton(tooltip: 'Düzenle', icon: const Icon(Icons.edit_outlined), onPressed: () => showGoalSheet(context, ref, existing: g)),
          PopupMenuButton<String>(
            onSelected: (v) async {
              final repo = ref.read(goalRepoProvider);
              if (v == 'archive') {
                await repo.archiveGoal(g);
                if (context.mounted) context.pop();
              } else if (v == 'delete') {
                if (await confirm(context, title: 'Hedef silinsin mi?', message: 'Bu hedefe bağlı görevler de silinir.', ok: 'Sil', destructive: true)) {
                  await repo.deleteGoal(g);
                  if (context.mounted) context.pop();
                }
              }
            },
            itemBuilder: (_) => const [
              PopupMenuItem(value: 'archive', child: Text('Arşivle')),
              PopupMenuItem(value: 'delete', child: Text('Sil')),
            ],
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => showTaskSheet(context, ref, goalId: g.id, dreamId: g.dreamId),
        icon: const Icon(Icons.add_task),
        label: const Text('Alt görev'),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 100),
        children: [
          SoftCard(
            child: Row(children: [
              ProgressRing(value: progress, size: 80, stroke: 8, color: g.isCompleted ? AppColors.success : null),
              Gap.w16,
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(g.title, style: Theme.of(context).textTheme.titleLarge),
                  if (g.description.isNotEmpty) Text(g.description),
                  Gap.h8,
                  Wrap(spacing: 8, children: [
                    PriorityChip(g.priority),
                    if (g.dueDate != null) Text('Bitiş: ${Dates.short(g.dueDate!)}', style: const TextStyle(fontSize: 12)),
                  ]),
                ]),
              ),
            ]),
          ),
          if (dream != null) ...[
            Gap.h12,
            SoftCard(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              onTap: () => context.push('/dream/${dream.dream.id}'),
              child: ListTile(
                leading: const Icon(Icons.auto_awesome),
                title: Text(dream.dream.title),
                subtitle: const Text('Bağlı hayal'),
                trailing: const Icon(Icons.chevron_right),
              ),
            ),
          ],
          Gap.h12,
          SwitchListTile(
            title: const Text('Hedef tamamlandı'),
            value: g.isCompleted,
            onChanged: (v) => ref.read(goalRepoProvider).setGoalCompleted(g, v),
          ),
          SectionHeader('Alt görevler (${tasks.where((t) => t.isCompleted).length}/${tasks.length})'),
          if (tasks.isEmpty)
            Text('Hedefini alt görevlere böl; ilerleme otomatik hesaplanır.', style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant))
          else
            SoftCard(padding: const EdgeInsets.symmetric(horizontal: 8), child: Column(children: [for (final t in tasks) TaskTile(t)])),
        ],
      ),
    );
  }
}
