import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/logic/achievements_catalog.dart';
import '../../core/logic/progress_logic.dart';
import '../../core/providers.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/dates.dart';
import '../../core/widgets/capture.dart';
import '../../core/widgets/common.dart';
import '../../core/widgets/logo.dart';
import '../../data/providers.dart';

class AchievementsScreen extends ConsumerWidget {
  const AchievementsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final points = ref.watch(pointsProvider).value ?? 0;
    final unlocked = {for (final a in ref.watch(unlockedProvider).value ?? const []) a.code: a.unlockedAt};
    final history = ref.watch(historyProvider).value ?? const [];
    final lvl = ProgressLogic.level(points);
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Başarılarım'),
          bottom: const TabBar(tabs: [Tab(text: 'Rozetler'), Tab(text: 'Başarı geçmişi')]),
        ),
        body: TabBarView(children: [
          ListView(
            padding: const EdgeInsets.all(Gap.lg),
            children: [
              GradientCard(
                gradient: AppColors.goldGradient,
                child: Row(children: [
                  Container(
                    width: 64,
                    height: 64,
                    decoration: const BoxDecoration(color: Colors.white24, shape: BoxShape.circle),
                    child: Center(child: Text('${lvl.level}', style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w900))),
                  ),
                  Gap.w16,
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(ProgressLogic.levelTitle(lvl.level), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
                      Text('$points puan · ${unlocked.length}/${kAchievements.length} rozet'),
                      Gap.h8,
                      LinearBar(value: lvl.intoLevel / lvl.needed, color: Colors.white, height: 8),
                      Gap.h4,
                      Text('Sonraki seviyeye ${lvl.needed - lvl.intoLevel} puan', style: const TextStyle(fontSize: 12)),
                    ]),
                  ),
                ]),
              ),
              Gap.h16,
              LayoutBuilder(builder: (context, c) {
                final cols = (c.maxWidth / 120).floor().clamp(3, 6);
                return GridView.count(
                  crossAxisCount: cols,
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  mainAxisSpacing: 12,
                  crossAxisSpacing: 12,
                  childAspectRatio: 0.78,
                  children: [
                    for (final a in kAchievements) _Badge(def: a, unlockedAt: unlocked[a.code]),
                  ],
                );
              }),
            ],
          ),
          history.isEmpty
              ? const EmptyState(icon: Icons.history, title: 'Henüz kayıt yok', message: 'Tamamladığın görevler, adımlar ve hayaller burada listelenir.')
              : ListView.builder(
                  itemCount: history.length,
                  itemBuilder: (_, i) {
                    final h = history[i];
                    return ListTile(
                      leading: CircleAvatar(
                        backgroundColor: AppColors.gold.withValues(alpha: 0.15),
                        child: Icon(_iconFor(h.type), color: AppColors.gold, size: 20),
                      ),
                      title: Text(h.title, maxLines: 1, overflow: TextOverflow.ellipsis),
                      subtitle: Text(Dates.dateTime(h.createdAt)),
                      trailing: h.points > 0 ? Text('+${h.points}', style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.success)) : null,
                    );
                  },
                ),
        ]),
      ),
    );
  }

  static IconData _iconFor(String t) => switch (t) {
        'task' => Icons.task_alt,
        'milestone' => Icons.route,
        'dream' => Icons.emoji_events,
        'habit' => Icons.repeat,
        'deposit' => Icons.savings,
        'focus' => Icons.timer,
        'journal' => Icons.menu_book,
        'goal' => Icons.flag,
        'badge' => Icons.military_tech,
        'balance' => Icons.donut_large,
        _ => Icons.auto_awesome,
      };
}

class _Badge extends StatelessWidget {
  const _Badge({required this.def, required this.unlockedAt});
  final AchievementDef def;
  final DateTime? unlockedAt;

  @override
  Widget build(BuildContext context) {
    final on = unlockedAt != null;
    return Semantics(
      label: '${def.title}, ${on ? 'kazanıldı' : 'kilitli'}',
      button: true,
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: () => showDialog<void>(context: context, builder: (_) => _BadgeDialog(def: def, unlockedAt: unlockedAt)),
        child: Column(children: [
          AspectRatio(
            aspectRatio: 1,
            child: Container(
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: on ? LinearGradient(colors: [def.color, def.color.withValues(alpha: 0.6)]) : null,
                color: on ? null : Theme.of(context).colorScheme.surfaceContainerHighest,
                boxShadow: on ? [BoxShadow(color: def.color.withValues(alpha: 0.35), blurRadius: 12, offset: const Offset(0, 4))] : null,
              ),
              child: Icon(on ? def.icon : Icons.lock_outline, color: on ? Colors.white : Colors.grey, size: 32),
            ),
          ),
          Gap.h8,
          Text(def.title, textAlign: TextAlign.center, maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: on ? null : Colors.grey)),
        ]),
      ),
    );
  }
}

class _BadgeDialog extends ConsumerStatefulWidget {
  const _BadgeDialog({required this.def, required this.unlockedAt});
  final AchievementDef def;
  final DateTime? unlockedAt;
  @override
  ConsumerState<_BadgeDialog> createState() => _BadgeDialogState();
}

class _BadgeDialogState extends ConsumerState<_BadgeDialog> {
  final _key = GlobalKey();

  Future<void> _save({required bool share}) async {
    final png = await capturePng(_key);
    if (png == null) return;
    try {
      if (share) {
        final f = await ref.read(mediaProvider).writeTemp(png, 'rozet_${widget.def.code}.png');
        await SharePlus.instance.share(ShareParams(files: [XFile(f.path)], text: 'Hayallerim Cepte rozetim: ${widget.def.title}'));
      } else {
        await ref.read(mediaProvider).saveToGallery(png, name: 'rozet_${widget.def.code}');
        if (mounted) toast(context, 'Rozet kartı galeriye kaydedildi');
      }
    } catch (e) {
      if (mounted) toast(context, '$e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final d = widget.def;
    final on = widget.unlockedAt != null;
    final name = ref.watch(profileProvider).value?.name ?? '';
    return Dialog(
      child: Padding(
        padding: const EdgeInsets.all(Gap.lg),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          RepaintBoundary(
            key: _key,
            child: Container(
              width: 280,
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(gradient: AppColors.nightGradient, borderRadius: BorderRadius.circular(24)),
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                Container(
                  width: 96,
                  height: 96,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: on ? LinearGradient(colors: [d.color, d.color.withValues(alpha: 0.6)]) : null,
                    color: on ? null : Colors.white12,
                  ),
                  child: Icon(on ? d.icon : Icons.lock_outline, color: Colors.white, size: 48),
                ),
                Gap.h16,
                Text(d.title, style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w800)),
                Gap.h8,
                Text(d.description, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white70)),
                Gap.h12,
                Text(
                  on ? '${name.isEmpty ? '' : '$name · '}${Dates.long(widget.unlockedAt!)}' : 'Henüz kazanılmadı · +${d.points} puan',
                  style: const TextStyle(color: AppColors.gold, fontWeight: FontWeight.w600),
                ),
                Gap.h16,
                const Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                  AppLogo(size: 22),
                  SizedBox(width: 6),
                  Text('Hayallerim Cepte', style: TextStyle(color: Colors.white54, fontSize: 12)),
                ]),
              ]),
            ),
          ),
          Gap.h16,
          if (on)
            Row(mainAxisAlignment: MainAxisAlignment.center, children: [
              OutlinedButton.icon(onPressed: () => _save(share: true), icon: const Icon(Icons.share), label: const Text('Paylaş')),
              Gap.w8,
              FilledButton.icon(onPressed: () => _save(share: false), icon: const Icon(Icons.download), label: const Text('Kaydet')),
            ])
          else
            TextButton(onPressed: () => Navigator.pop(context), child: const Text('Kapat')),
        ]),
      ),
    );
  }
}
