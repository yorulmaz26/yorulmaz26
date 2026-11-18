import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/database/app_database.dart';
import '../../core/logic/progress_logic.dart';
import '../../core/providers.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/app_icons.dart';
import '../../core/utils/dates.dart';
import '../../core/utils/money.dart';
import '../../core/widgets/common.dart';
import '../../data/goal_repository.dart';
import '../../data/providers.dart';
import '../garden/plant_painter.dart';
import '../goals/goal_sheet.dart';
import '../goals/task_widgets.dart';
import '../security/lock_controller.dart';
import '../security/lock_screen.dart';

final _dreamProvider = StreamProvider.family<Dream?, int>((ref, id) => ref.watch(dreamRepoProvider).watchDream(id));
final _stepsProvider = StreamProvider.family<List<DreamMilestone>, int>((ref, id) => ref.watch(dreamRepoProvider).watchMilestones(id));
final _mediaProvider = StreamProvider.family<List<DreamMediaItem>, int>((ref, id) => ref.watch(dreamRepoProvider).watchDreamMedia(id));
final _readableProvider = FutureProvider.family<Dream?, int>((ref, id) async {
  final d = ref.watch(_dreamProvider(id)).value;
  if (d == null) return null;
  return ref.read(dreamRepoProvider).readable(d);
});
final _dreamGoalsProvider = StreamProvider.family<List<Goal>, int>((ref, id) => ref.watch(goalRepoProvider).watchGoalsForDream(id));
final _dreamTasksProvider = StreamProvider.family<List<TaskItem>, int>((ref, id) => ref.watch(goalRepoProvider).watchTasksForDream(id));

class DreamDetailScreen extends ConsumerWidget {
  const DreamDetailScreen({super.key, required this.id});
  final int id;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dreamAsync = ref.watch(_dreamProvider(id));
    final dream = dreamAsync.value;
    if (dreamAsync.isLoading && dream == null) return const Scaffold(body: Center(child: CircularProgressIndicator()));
    if (dream == null) {
      return Scaffold(appBar: AppBar(), body: const EmptyState(icon: Icons.search_off, title: 'Hayal bulunamadı'));
    }
    if (dream.isHidden && !ref.watch(lockProvider).privateUnlocked) {
      return Scaffold(
        appBar: AppBar(),
        body: EmptyState(
          icon: Icons.lock_outline,
          title: 'Bu hayal gizli',
          message: 'Görüntülemek için kimliğini doğrula.',
          actionLabel: 'Kilidi aç',
          onAction: () => requestPrivateAccess(context, ref),
        ),
      );
    }
    final readable = ref.watch(_readableProvider(id)).value ?? dream;
    final cats = ref.watch(categoriesProvider).value ?? const [];
    final cat = cats.where((c) => c.id == dream.categoryId).firstOrNull;
    final steps = ref.watch(_stepsProvider(id)).value ?? const [];
    final progress = ProgressLogic.dreamProgress(steps, weighted: dream.weightedProgress, completed: dream.isCompleted);
    final color = cat == null ? AppColors.purple : Color(cat.color);
    final stage = ProgressLogic.gardenStage(progress, completed: dream.isCompleted);

    return Scaffold(
      body: CustomScrollView(slivers: [
        SliverAppBar(
          expandedHeight: 240,
          pinned: true,
          stretch: true,
          actions: [
            IconButton(tooltip: 'Düzenle', icon: const Icon(Icons.edit_outlined), onPressed: () => context.push('/dream/$id/edit')),
            PopupMenuButton<String>(
              onSelected: (v) => _menu(context, ref, dream, v),
              itemBuilder: (_) => [
                PopupMenuItem(value: 'complete', child: Text(dream.isCompleted ? 'Gerçekleşmedi olarak işaretle' : 'Gerçekleşti olarak işaretle')),
                const PopupMenuItem(value: 'photos', child: Text('Fotoğraf ekle')),
                const PopupMenuItem(value: 'savings', child: Text('Kumbara oluştur')),
                const PopupMenuItem(value: 'archive', child: Text('Arşivle')),
              ],
            ),
          ],
          flexibleSpace: FlexibleSpaceBar(
            title: Text(dream.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
            background: Hero(
              tag: 'dream-cover-$id',
              child: Stack(fit: StackFit.expand, children: [
                dream.coverPath != null
                    ? LocalImage(dream.coverPath, cacheWidth: 900)
                    : Container(decoration: BoxDecoration(gradient: LinearGradient(colors: [color, color.withValues(alpha: 0.55)]))),
                const DecoratedBox(
                  decoration: BoxDecoration(gradient: LinearGradient(colors: [Colors.black26, Colors.transparent, Colors.black54], begin: Alignment.topCenter, end: Alignment.bottomCenter)),
                ),
              ]),
            ),
          ),
        ),
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(Gap.lg, Gap.lg, Gap.lg, 100),
          sliver: SliverList.list(children: [
            SoftCard(
              child: Row(children: [
                ProgressRing(value: progress, size: 72, stroke: 7, color: dream.isCompleted ? AppColors.gold : color),
                Gap.w16,
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    if (cat != null)
                      Row(children: [
                        Icon(AppIcons.of(cat.iconKey), size: 16, color: color),
                        Gap.w4,
                        Text(cat.name, style: TextStyle(color: color, fontWeight: FontWeight.w600)),
                        const Spacer(),
                        PriorityChip(dream.priority),
                      ]),
                    Gap.h8,
                    Text(dream.isCompleted ? 'Gerçekleşti 🎉 ${dream.completedAt == null ? '' : Dates.short(dream.completedAt!)}' : '${steps.where((s) => s.isCompleted).length}/${steps.length} adım tamamlandı'),
                    if (dream.targetDate != null) ...[
                      Gap.h4,
                      Text('Hedef: ${Dates.long(dream.targetDate!)} · ${Dates.relative(dream.targetDate!)}',
                          style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant)),
                    ],
                    if (dream.budgetMinor != null) ...[
                      Gap.h4,
                      Text('Bütçe: ${Money.format(dream.budgetMinor!, currency: dream.currency)}', style: const TextStyle(fontSize: 12)),
                    ],
                  ]),
                ),
                Plant(stage: stage, type: cat?.plantType ?? 'oak', size: 54, seed: id),
              ]),
            ),
            if (readable.motivation.isNotEmpty) ...[
              Gap.h12,
              GradientCard(
                gradient: AppColors.goldGradient,
                child: Row(children: [
                  const Icon(Icons.format_quote_rounded, color: Colors.white),
                  Gap.w8,
                  Expanded(child: Text(readable.motivation, style: const TextStyle(fontStyle: FontStyle.italic, fontSize: 15))),
                ]),
              ),
            ],
            if (readable.description.isNotEmpty) ...[
              const SectionHeader('Açıklama'),
              Text(readable.description, style: const TextStyle(height: 1.45)),
            ],
            if (!dream.isCompleted && progress >= 1 && steps.isNotEmpty) ...[
              Gap.h16,
              FilledButton.icon(
                style: FilledButton.styleFrom(backgroundColor: AppColors.gold),
                onPressed: () => _complete(context, ref, dream),
                icon: const Icon(Icons.emoji_events),
                label: const Text('Tüm adımlar bitti — Hayalimi gerçekleştirdim!'),
              ),
            ],
            SectionHeader('Hayal yolculuğum', action: 'Adım ekle', onAction: () => _addStep(context, ref, dream)),
            _Journey(dream: dream, steps: steps, color: color),
            _Gallery(dreamId: id),
            _LinkedGoals(dreamId: id),
            if (readable.notes.isNotEmpty) ...[
              const SectionHeader('Kişisel notlar'),
              SoftCard(child: Text(readable.notes, style: const TextStyle(height: 1.45))),
            ],
            Gap.h16,
            Text('Oluşturulma: ${Dates.long(dream.createdAt)}', textAlign: TextAlign.center, style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant)),
          ]),
        ),
      ]),
    );
  }

  Future<void> _complete(BuildContext context, WidgetRef ref, Dream d) async {
    await ref.read(dreamRepoProvider).setCompleted(d, true);
    if (!context.mounted) return;
    await showDialog<void>(
      context: context,
      builder: (c) => AlertDialog(
        icon: const Icon(Icons.emoji_events, color: AppColors.gold, size: 48),
        title: const Text('Tebrikler! 🎉'),
        content: Text('"${d.title}" hayalini gerçekleştirdin. Ağacın meyve verdi ve bahçende sonsuza dek kalacak.'),
        actions: [FilledButton(onPressed: () => Navigator.pop(c), child: const Text('Harika!'))],
      ),
    );
  }

  Future<void> _menu(BuildContext context, WidgetRef ref, Dream d, String v) async {
    final repo = ref.read(dreamRepoProvider);
    switch (v) {
      case 'complete':
        if (d.isCompleted) {
          await repo.setCompleted(d, false);
        } else {
          await _complete(context, ref, d);
        }
      case 'photos':
        final paths = await ref.read(mediaProvider).pickMultipleImages();
        await repo.addDreamImages(d.id, paths);
        if (context.mounted && paths.isNotEmpty) toast(context, '${paths.length} fotoğraf eklendi');
      case 'savings':
        await ref.read(savingsRepoProvider).createGoal(
              title: d.title,
              targetMinor: d.budgetMinor ?? 0,
              currency: d.currency,
              dreamId: d.id,
              targetDate: d.targetDate,
            );
        if (context.mounted) {
          toast(context, d.budgetMinor == null ? 'Kumbara oluşturuldu — hedef tutarı düzenlemeyi unutma' : 'Kumbara oluşturuldu');
          context.push('/savings');
        }
      case 'archive':
        if (await confirm(context, title: 'Hayal arşivlensin mi?', message: 'Arşivden istediğin zaman geri alabilirsin.', ok: 'Arşivle')) {
          await repo.archive(d.id);
          if (context.mounted) context.pop();
        }
    }
  }

  Future<void> _addStep(BuildContext context, WidgetRef ref, Dream d) async {
    final t = await promptText(context, title: 'Yeni adım', hint: 'Örn. Gideceğim yerleri araştır', maxLength: 160);
    if (t != null) await ref.read(dreamRepoProvider).addMilestone(d.id, t);
  }
}

class _Journey extends ConsumerWidget {
  const _Journey({required this.dream, required this.steps, required this.color});
  final Dream dream;
  final List<DreamMilestone> steps;
  final Color color;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cs = Theme.of(context).colorScheme;
    if (steps.isEmpty) {
      return const SoftCard(child: Text('Hayalini küçük adımlara böl. Her adım ağacını büyütür 🌱'));
    }
    return SoftCard(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: ReorderableListView(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        buildDefaultDragHandles: false,
        onReorderItem: (o, n) {
          final list = [...steps];
          list.insert(n, list.removeAt(o));
          ref.read(dreamRepoProvider).reorderMilestones(list);
        },
        children: [
          for (var i = 0; i < steps.length; i++)
            IntrinsicHeight(
              key: ValueKey(steps[i].id),
              child: Row(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                Gap.w12,
                Column(children: [
                  Container(width: 2, height: 10, color: i == 0 ? Colors.transparent : cs.outlineVariant),
                  InkWell(
                    customBorder: const CircleBorder(),
                    onTap: () => ref.read(dreamRepoProvider).toggleMilestone(steps[i]),
                    child: Semantics(
                      checked: steps[i].isCompleted,
                      label: 'Adım ${i + 1}',
                      child: AnimatedContainer(
                        duration: const Duration(milliseconds: 250),
                        width: 28,
                        height: 28,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: steps[i].isCompleted ? color : Colors.transparent,
                          border: Border.all(color: color, width: 2),
                        ),
                        child: steps[i].isCompleted
                            ? const Icon(Icons.check, size: 16, color: Colors.white)
                            : Center(child: Text('${i + 1}', style: TextStyle(fontSize: 12, color: color, fontWeight: FontWeight.w700))),
                      ),
                    ),
                  ),
                  Expanded(child: Container(width: 2, color: i == steps.length - 1 ? Colors.transparent : (steps[i].isCompleted ? color : cs.outlineVariant))),
                ]),
                Expanded(
                  child: InkWell(
                    onTap: () => showModalBottomSheet(
                      context: context,
                      isScrollControlled: true,
                      builder: (_) => MilestoneSheet(step: steps[i], weighted: dream.weightedProgress),
                    ),
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(12, 12, 4, 12),
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(steps[i].title,
                            style: TextStyle(
                              fontWeight: FontWeight.w600,
                              decoration: steps[i].isCompleted ? TextDecoration.lineThrough : null,
                            )),
                        if (steps[i].description.isNotEmpty || steps[i].targetDate != null || steps[i].reminderAt != null || (dream.weightedProgress))
                          Padding(
                            padding: const EdgeInsets.only(top: 2),
                            child: Text(
                              [
                                if (steps[i].targetDate != null) Dates.short(steps[i].targetDate!),
                                if (steps[i].reminderAt != null) '🔔 ${Dates.dateTime(steps[i].reminderAt!)}',
                                if (dream.weightedProgress) 'Ağırlık ${steps[i].weight}',
                                if (steps[i].description.isNotEmpty) steps[i].description,
                              ].join(' · '),
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant),
                            ),
                          ),
                        if (steps[i].photoPath != null)
                          Padding(
                            padding: const EdgeInsets.only(top: 8),
                            child: ClipRRect(borderRadius: BorderRadius.circular(10), child: SizedBox(height: 80, width: 120, child: LocalImage(steps[i].photoPath, cacheWidth: 240))),
                          ),
                      ]),
                    ),
                  ),
                ),
                ReorderableDragStartListener(index: i, child: const Padding(padding: EdgeInsets.all(12), child: Icon(Icons.drag_handle, size: 20))),
              ]),
            ),
        ],
      ),
    );
  }
}

/// Adım ayrıntıları: açıklama, hedef tarih, fotoğraf, not, hatırlatıcı, ağırlık
class MilestoneSheet extends ConsumerStatefulWidget {
  const MilestoneSheet({super.key, required this.step, required this.weighted});
  final DreamMilestone step;
  final bool weighted;
  @override
  ConsumerState<MilestoneSheet> createState() => _MilestoneSheetState();
}

class _MilestoneSheetState extends ConsumerState<MilestoneSheet> {
  late final _title = TextEditingController(text: widget.step.title);
  late final _desc = TextEditingController(text: widget.step.description);
  late final _note = TextEditingController(text: widget.step.note);
  late DateTime? _target = widget.step.targetDate;
  late DateTime? _reminder = widget.step.reminderAt;
  late String? _photo = widget.step.photoPath;
  late int _weight = widget.step.weight;

  @override
  void dispose() {
    _title.dispose();
    _desc.dispose();
    _note.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_title.text.trim().isEmpty) return;
    if (widget.step.photoPath != null && widget.step.photoPath != _photo) await ref.read(mediaProvider).delete(widget.step.photoPath);
    await ref.read(dreamRepoProvider).updateMilestone(widget.step.copyWith(
          title: _title.text.trim(),
          description: _desc.text,
          note: _note.text,
          targetDate: Value(_target),
          reminderAt: Value(_reminder),
          photoPath: Value(_photo),
          weight: _weight,
        ));
    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(left: Gap.lg, right: Gap.lg, bottom: MediaQuery.viewInsetsOf(context).bottom + Gap.lg),
      child: SingleChildScrollView(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min, children: [
          Row(children: [
            Expanded(child: Text('Adım ayrıntıları', style: Theme.of(context).textTheme.titleLarge)),
            IconButton(
              tooltip: 'Adımı sil',
              icon: const Icon(Icons.delete_outline, color: AppColors.danger),
              onPressed: () async {
                if (await confirm(context, title: 'Adım silinsin mi?', ok: 'Sil', destructive: true)) {
                  await ref.read(dreamRepoProvider).deleteMilestone(widget.step);
                  if (context.mounted) Navigator.pop(context);
                }
              },
            ),
          ]),
          Gap.h8,
          TextField(controller: _title, maxLength: 160, decoration: const InputDecoration(labelText: 'Adım')),
          TextField(controller: _desc, maxLines: 2, decoration: const InputDecoration(labelText: 'Açıklama')),
          Gap.h8,
          TextField(controller: _note, maxLines: 3, decoration: const InputDecoration(labelText: 'Not')),
          Gap.h12,
          Wrap(spacing: 8, runSpacing: 8, children: [
            InputChip(
              avatar: const Icon(Icons.event, size: 18),
              label: Text(_target == null ? 'Hedef tarihi' : Dates.short(_target!)),
              onPressed: () async {
                final d = await pickDate(context, initial: _target);
                if (d != null) setState(() => _target = d);
              },
              onDeleted: _target == null ? null : () => setState(() => _target = null),
            ),
            InputChip(
              avatar: const Icon(Icons.notifications_outlined, size: 18),
              label: Text(_reminder == null ? 'Hatırlatıcı' : Dates.dateTime(_reminder!)),
              onPressed: () async {
                final d = await pickDate(context, initial: _reminder ?? DateTime.now(), first: Dates.today());
                if (d == null || !context.mounted) return;
                final t = await pickTime(context);
                if (t == null) return;
                setState(() => _reminder = DateTime(d.year, d.month, d.day, t.hour, t.minute));
              },
              onDeleted: _reminder == null ? null : () => setState(() => _reminder = null),
            ),
            InputChip(
              avatar: const Icon(Icons.photo_outlined, size: 18),
              label: Text(_photo == null ? 'Fotoğraf' : 'Fotoğraf eklendi'),
              onPressed: () async {
                final cam = await pickSourceSheet(context);
                if (cam == null) return;
                final p = await ref.read(mediaProvider).pickImage(camera: cam);
                if (p != null) setState(() => _photo = p);
              },
              onDeleted: _photo == null ? null : () => setState(() => _photo = null),
            ),
          ]),
          if (_photo != null) ...[
            Gap.h8,
            ClipRRect(borderRadius: BorderRadius.circular(12), child: SizedBox(height: 140, child: LocalImage(_photo, cacheWidth: 600))),
          ],
          if (widget.weighted) ...[
            Gap.h12,
            Text('Ağırlık: $_weight'),
            Slider(value: _weight.toDouble(), min: 1, max: 10, divisions: 9, label: '$_weight', onChanged: (v) => setState(() => _weight = v.round())),
          ],
          Gap.h12,
          FilledButton(onPressed: _save, child: const Text('Kaydet')),
        ]),
      ),
    );
  }
}

class _Gallery extends ConsumerWidget {
  const _Gallery({required this.dreamId});
  final int dreamId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final media = ref.watch(_mediaProvider(dreamId)).value ?? const [];
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      SectionHeader('Görseller', action: 'Ekle', onAction: () async {
        final paths = await ref.read(mediaProvider).pickMultipleImages();
        await ref.read(dreamRepoProvider).addDreamImages(dreamId, paths);
      }),
      if (media.isEmpty)
        Text('Hayalini hatırlatan fotoğraflar ekle. Panona da eklenir.', style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant))
      else
        SizedBox(
          height: 110,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: media.length,
            separatorBuilder: (_, _) => Gap.w8,
            itemBuilder: (_, i) => GestureDetector(
              onLongPress: () async {
                if (await confirm(context, title: 'Görsel silinsin mi?', ok: 'Sil', destructive: true)) {
                  await ref.read(dreamRepoProvider).deleteMedia(media[i]);
                }
              },
              onTap: () => showDialog(
                context: context,
                builder: (c) => Dialog(
                  clipBehavior: Clip.antiAlias,
                  child: InteractiveViewer(child: LocalImage(media[i].path, fit: BoxFit.contain, cacheWidth: 1400)),
                ),
              ),
              child: ClipRRect(borderRadius: BorderRadius.circular(12), child: SizedBox(width: 110, child: LocalImage(media[i].path, cacheWidth: 220))),
            ),
          ),
        ),
    ]);
  }
}

class _LinkedGoals extends ConsumerWidget {
  const _LinkedGoals({required this.dreamId});
  final int dreamId;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final goals = ref.watch(_dreamGoalsProvider(dreamId)).value ?? const [];
    final tasks = ref.watch(_dreamTasksProvider(dreamId)).value ?? const [];
    final allTasks = ref.watch(tasksProvider).value ?? const [];
    final loose = tasks.where((t) => t.goalId == null).toList();
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      SectionHeader('Bağlı hedefler ve görevler', action: 'Hedef ekle', onAction: () => showGoalSheet(context, ref, dreamId: dreamId)),
      if (goals.isEmpty && loose.isEmpty)
        Text('Bu hayale bağlı hedef yok.', style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
      for (final g in goals)
        Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: SoftCard(
            padding: const EdgeInsets.all(Gap.md),
            onTap: () => context.push('/goal/${g.id}'),
            child: Row(children: [
              ProgressRing(value: GoalRepository.goalProgress(g, allTasks), size: 40, stroke: 4),
              Gap.w12,
              Expanded(child: Text(g.title, style: const TextStyle(fontWeight: FontWeight.w600))),
              Text(periodLabels[g.period] ?? '', style: const TextStyle(fontSize: 12)),
            ]),
          ),
        ),
      if (loose.isNotEmpty) SoftCard(padding: const EdgeInsets.symmetric(horizontal: 8), child: Column(children: [for (final t in loose) TaskTile(t)])),
      Align(
        alignment: Alignment.centerLeft,
        child: TextButton.icon(onPressed: () => showTaskSheet(context, ref, dreamId: dreamId), icon: const Icon(Icons.add), label: const Text('Görev ekle')),
      ),
    ]);
  }
}
