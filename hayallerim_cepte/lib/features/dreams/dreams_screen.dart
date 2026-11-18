import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/app_icons.dart';
import '../../core/utils/dates.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';
import '../security/lock_controller.dart';
import '../security/lock_screen.dart';

enum DreamViewMode { cards, list, board, timeline }

enum DreamSort { newest, target, progress, priority, title }

const _sortLabels = {
  DreamSort.newest: 'En yeni',
  DreamSort.target: 'Hedef tarihi',
  DreamSort.progress: 'İlerleme',
  DreamSort.priority: 'Öncelik',
  DreamSort.title: 'Ada göre',
};

class DreamsScreen extends ConsumerStatefulWidget {
  const DreamsScreen({super.key});
  @override
  ConsumerState<DreamsScreen> createState() => _DreamsScreenState();
}

class _DreamsScreenState extends ConsumerState<DreamsScreen> {
  DreamViewMode _mode = DreamViewMode.cards;
  DreamSort _sort = DreamSort.newest;
  String _query = '';
  int? _category;
  int _status = 0; // 0 aktif, 1 gerçekleşen, 2 tümü
  bool _showHidden = false;

  List<DreamView> _filter(List<DreamView> all) {
    final q = _query.toLowerCase();
    final privateOpen = ref.watch(lockProvider).privateUnlocked;
    final list = all.where((d) {
      if (d.dream.isHidden && !(_showHidden && privateOpen)) return false;
      if (_category != null && d.dream.categoryId != _category) return false;
      if (_status == 0 && d.dream.isCompleted) return false;
      if (_status == 1 && !d.dream.isCompleted) return false;
      if (q.isNotEmpty && !d.dream.title.toLowerCase().contains(q) && !d.category.name.toLowerCase().contains(q)) return false;
      return true;
    }).toList();
    int cmpDate(DateTime? a, DateTime? b) => a == null ? (b == null ? 0 : 1) : (b == null ? -1 : a.compareTo(b));
    switch (_sort) {
      case DreamSort.newest:
        list.sort((a, b) => b.dream.createdAt.compareTo(a.dream.createdAt));
      case DreamSort.target:
        list.sort((a, b) => cmpDate(a.dream.targetDate, b.dream.targetDate));
      case DreamSort.progress:
        list.sort((a, b) => b.progress.compareTo(a.progress));
      case DreamSort.priority:
        list.sort((a, b) => b.dream.priority.compareTo(a.dream.priority));
      case DreamSort.title:
        list.sort((a, b) => a.dream.title.toLowerCase().compareTo(b.dream.title.toLowerCase()));
    }
    if (_mode == DreamViewMode.timeline) list.sort((a, b) => cmpDate(a.dream.targetDate, b.dream.targetDate));
    return list;
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(dreamViewsProvider);
    final cats = ref.watch(categoriesProvider).value ?? const [];
    return Scaffold(
      appBar: AppBar(
        title: const Text('Hayallerim'),
        actions: [
          PopupMenuButton<DreamViewMode>(
            tooltip: 'Görünüm',
            icon: Icon(switch (_mode) {
              DreamViewMode.cards => Icons.grid_view_rounded,
              DreamViewMode.list => Icons.view_list_rounded,
              DreamViewMode.board => Icons.photo_library_rounded,
              DreamViewMode.timeline => Icons.timeline_rounded,
            }),
            onSelected: (m) => setState(() => _mode = m),
            itemBuilder: (_) => const [
              PopupMenuItem(value: DreamViewMode.cards, child: Text('Kart görünümü')),
              PopupMenuItem(value: DreamViewMode.list, child: Text('Liste görünümü')),
              PopupMenuItem(value: DreamViewMode.board, child: Text('Fotoğraflı pano')),
              PopupMenuItem(value: DreamViewMode.timeline, child: Text('Zaman çizelgesi')),
            ],
          ),
          PopupMenuButton<String>(
            onSelected: (v) async {
              switch (v) {
                case 'hidden':
                  if (_showHidden) {
                    setState(() => _showHidden = false);
                  } else if (await requestPrivateAccess(context, ref)) {
                    setState(() => _showHidden = true);
                  }
                case 'categories':
                  context.push('/categories');
                case 'archive':
                  context.push('/archive');
                case 'board':
                  context.push('/board');
              }
            },
            itemBuilder: (_) => [
              PopupMenuItem(value: 'hidden', child: Text(_showHidden ? 'Gizli hayalleri sakla' : 'Gizli hayalleri göster')),
              const PopupMenuItem(value: 'board', child: Text('Hayal Panom')),
              const PopupMenuItem(value: 'categories', child: Text('Kategoriler')),
              const PopupMenuItem(value: 'archive', child: Text('Arşiv')),
            ],
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        heroTag: 'dreams-fab',
        tooltip: 'Yeni hayal',
        onPressed: () => context.push('/dream/new'),
        child: const Icon(Icons.add),
      ),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, Gap.sm),
          child: TextField(
            decoration: const InputDecoration(hintText: 'Hayallerinde ara', prefixIcon: Icon(Icons.search), isDense: true),
            onChanged: (v) => setState(() => _query = v),
          ),
        ),
        SizedBox(
          height: 44,
          child: ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: Gap.lg),
            children: [
              _chip(['Aktif', 'Gerçekleşen', 'Tümü'][_status], Icons.filter_list, () => setState(() => _status = (_status + 1) % 3)),
              _chip(_sortLabels[_sort]!, Icons.sort, () async {
                final s = await showModalBottomSheet<DreamSort>(
                  context: context,
                  builder: (c) => SafeArea(
                    child: Column(mainAxisSize: MainAxisSize.min, children: [
                      for (final e in _sortLabels.entries)
                        ListTile(title: Text(e.value), trailing: e.key == _sort ? const Icon(Icons.check) : null, onTap: () => Navigator.pop(c, e.key)),
                    ]),
                  ),
                );
                if (s != null) setState(() => _sort = s);
              }),
              Padding(
                padding: const EdgeInsets.only(right: 8),
                child: ChoiceChip(label: const Text('Tüm kategoriler'), selected: _category == null, onSelected: (_) => setState(() => _category = null)),
              ),
              for (final c in cats)
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(
                    avatar: Icon(AppIcons.of(c.iconKey), size: 16, color: Color(c.color)),
                    label: Text(c.name),
                    selected: _category == c.id,
                    onSelected: (_) => setState(() => _category = _category == c.id ? null : c.id),
                  ),
                ),
            ],
          ),
        ),
        Expanded(
          child: async.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (e, _) => Center(child: Text('Hata: $e')),
            data: (all) {
              final list = _filter(all);
              if (list.isEmpty) {
                return EmptyState(
                  icon: Icons.auto_awesome,
                  title: all.isEmpty ? 'Henüz bir hayalin yok' : 'Sonuç bulunamadı',
                  message: all.isEmpty ? 'Hayallerini ekle, adım adım gerçekleştir.' : 'Filtreleri değiştirmeyi dene.',
                  actionLabel: all.isEmpty ? 'İlk hayalimi ekle' : null,
                  onAction: () => context.push('/dream/new'),
                );
              }
              return switch (_mode) {
                DreamViewMode.cards => _CardsView(list),
                DreamViewMode.list => _ListView(list),
                DreamViewMode.board => _BoardView(list),
                DreamViewMode.timeline => _TimelineView(list),
              };
            },
          ),
        ),
      ]),
    );
  }

  Widget _chip(String label, IconData icon, VoidCallback onTap) => Padding(
        padding: const EdgeInsets.only(right: 8),
        child: ActionChip(avatar: Icon(icon, size: 18), label: Text(label), onPressed: onTap),
      );
}

void _open(BuildContext context, DreamView d) => context.push('/dream/${d.dream.id}');

class _CardsView extends StatelessWidget {
  const _CardsView(this.list);
  final List<DreamView> list;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(builder: (context, c) {
      final cols = (c.maxWidth / 220).floor().clamp(2, 5);
      return GridView.builder(
        padding: const EdgeInsets.fromLTRB(Gap.lg, Gap.sm, Gap.lg, 100),
        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount: cols, mainAxisSpacing: 12, crossAxisSpacing: 12, childAspectRatio: 0.72),
        itemCount: list.length,
        itemBuilder: (_, i) => DreamCard(list[i]),
      );
    });
  }
}

class DreamCard extends StatelessWidget {
  const DreamCard(this.d, {super.key});
  final DreamView d;

  @override
  Widget build(BuildContext context) {
    final color = Color(d.category.color);
    return SoftCard(
      padding: EdgeInsets.zero,
      onTap: () => _open(context, d),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Expanded(
          child: Hero(
            tag: 'dream-cover-${d.dream.id}',
            child: d.dream.coverPath != null
                ? LocalImage(d.dream.coverPath, cacheWidth: 360)
                : Container(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(colors: [color, color.withValues(alpha: 0.6)], begin: Alignment.topLeft, end: Alignment.bottomRight),
                    ),
                    child: Icon(AppIcons.of(d.category.iconKey), color: Colors.white, size: 42),
                  ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.all(10),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              if (d.dream.isHidden) const Padding(padding: EdgeInsets.only(right: 4), child: Icon(Icons.lock, size: 14)),
              Expanded(child: Text(d.dream.title, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700))),
            ]),
            Gap.h4,
            Text(d.category.name, style: TextStyle(fontSize: 11, color: color, fontWeight: FontWeight.w600)),
            Gap.h8,
            Row(children: [
              Expanded(child: LinearBar(value: d.progress, color: d.dream.isCompleted ? AppColors.gold : color, height: 6)),
              Gap.w8,
              Text('%${(d.progress * 100).round()}', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
            ]),
          ]),
        ),
      ]),
    );
  }
}

class _ListView extends StatelessWidget {
  const _ListView(this.list);
  final List<DreamView> list;
  @override
  Widget build(BuildContext context) => ListView.separated(
        padding: const EdgeInsets.fromLTRB(Gap.lg, Gap.sm, Gap.lg, 100),
        itemCount: list.length,
        separatorBuilder: (_, _) => Gap.h8,
        itemBuilder: (_, i) {
          final d = list[i];
          final color = Color(d.category.color);
          return SoftCard(
            padding: const EdgeInsets.all(Gap.md),
            onTap: () => _open(context, d),
            child: Row(children: [
              ProgressRing(value: d.progress, size: 52, stroke: 5, color: d.dream.isCompleted ? AppColors.gold : color),
              Gap.w12,
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(d.dream.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
                  Gap.h4,
                  Text(
                    [d.category.name, '${d.doneSteps}/${d.totalSteps} adım', if (d.dream.targetDate != null) Dates.short(d.dream.targetDate!)].join(' · '),
                    style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant),
                  ),
                ]),
              ),
              PriorityChip(d.dream.priority),
            ]),
          );
        },
      );
}

/// Fotoğraflı pano: iki sütunlu, farklı yükseklikli kartlar.
class _BoardView extends StatelessWidget {
  const _BoardView(this.list);
  final List<DreamView> list;
  @override
  Widget build(BuildContext context) {
    final left = <DreamView>[];
    final right = <DreamView>[];
    for (var i = 0; i < list.length; i++) {
      (i.isEven ? left : right).add(list[i]);
    }
    Widget col(List<DreamView> items, int offset) => Column(children: [
          for (var i = 0; i < items.length; i++)
            Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: GestureDetector(
                onTap: () => _open(context, items[i]),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(Radii.md),
                  child: AspectRatio(
                    aspectRatio: (i + offset) % 3 == 0 ? 0.75 : 1.0,
                    child: Stack(fit: StackFit.expand, children: [
                      items[i].dream.coverPath != null
                          ? LocalImage(items[i].dream.coverPath, cacheWidth: 400)
                          : Container(color: Color(items[i].category.color)),
                      const DecoratedBox(
                        decoration: BoxDecoration(
                          gradient: LinearGradient(colors: [Colors.transparent, Colors.black54], begin: Alignment.center, end: Alignment.bottomCenter),
                        ),
                      ),
                      Positioned(
                        left: 10,
                        right: 10,
                        bottom: 10,
                        child: Text(items[i].dream.title,
                            maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                      ),
                    ]),
                  ),
                ),
              ),
            ),
        ]);
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(Gap.lg, Gap.sm, Gap.lg, 100),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Expanded(child: col(left, 0)),
        Gap.w8,
        Expanded(child: col(right, 1)),
      ]),
    );
  }
}

class _TimelineView extends StatelessWidget {
  const _TimelineView(this.list);
  final List<DreamView> list;
  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(Gap.lg, Gap.sm, Gap.lg, 100),
      itemCount: list.length,
      itemBuilder: (_, i) {
        final d = list[i];
        final color = d.dream.isCompleted ? AppColors.gold : Color(d.category.color);
        return IntrinsicHeight(
          child: Row(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            SizedBox(
              width: 74,
              child: Padding(
                padding: const EdgeInsets.only(top: 14),
                child: Text(
                  d.dream.targetDate == null ? 'Tarihsiz' : Dates.monthYear(d.dream.targetDate!),
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: cs.onSurfaceVariant),
                ),
              ),
            ),
            Column(children: [
              Container(width: 2, height: 14, color: i == 0 ? Colors.transparent : cs.outlineVariant),
              Container(width: 14, height: 14, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
              Expanded(child: Container(width: 2, color: i == list.length - 1 ? Colors.transparent : cs.outlineVariant)),
            ]),
            Gap.w12,
            Expanded(
              child: Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: SoftCard(
                  padding: const EdgeInsets.all(Gap.md),
                  onTap: () => _open(context, d),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(d.dream.title, style: const TextStyle(fontWeight: FontWeight.w700)),
                    Gap.h8,
                    LinearBar(value: d.progress, color: color, height: 6),
                    Gap.h4,
                    Text(d.dream.targetDate == null ? '%${(d.progress * 100).round()}' : '%${(d.progress * 100).round()} · ${Dates.relative(d.dream.targetDate!)}',
                        style: const TextStyle(fontSize: 12)),
                  ]),
                ),
              ),
            ),
          ]),
        );
      },
    );
  }
}
