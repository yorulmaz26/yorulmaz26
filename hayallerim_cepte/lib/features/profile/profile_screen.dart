import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/logic/progress_logic.dart';
import '../../core/providers.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';

class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final p = ref.watch(profileProvider).value;
    final points = ref.watch(pointsProvider).value ?? 0;
    final badges = ref.watch(unlockedProvider).value?.length ?? 0;
    final lvl = ProgressLogic.level(points);
    final name = p?.name ?? '';

    Widget tile(IconData icon, String title, String route, {Color? color, String? subtitle}) => ListTile(
          leading: Icon(icon, color: color ?? Theme.of(context).colorScheme.primary),
          title: Text(title),
          subtitle: subtitle == null ? null : Text(subtitle),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => context.push(route),
        );

    return Scaffold(
      appBar: AppBar(title: const Text('Profil')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 40),
        children: [
          SoftCard(
            child: Row(children: [
              GestureDetector(
                onTap: () async {
                  final cam = await pickSourceSheet(context);
                  if (cam == null) return;
                  final path = await ref.read(mediaProvider).pickImage(camera: cam);
                  if (path != null) await ref.read(profileRepoProvider).save(avatarPath: path);
                },
                child: Avatar(path: p?.avatarPath, name: name, size: 72),
              ),
              Gap.w16,
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(name.isEmpty ? 'Hayalperest' : name, style: Theme.of(context).textTheme.titleLarge),
                  Text('Seviye ${lvl.level} · ${ProgressLogic.levelTitle(lvl.level)}', style: const TextStyle(color: AppColors.gold, fontWeight: FontWeight.w600)),
                  Text('$points puan · $badges rozet', style: const TextStyle(fontSize: 12)),
                ]),
              ),
              IconButton(
                tooltip: 'Adı düzenle',
                icon: const Icon(Icons.edit_outlined),
                onPressed: () async {
                  final n = await promptText(context, title: 'Adın', initial: name, maxLength: 40);
                  if (n != null) await ref.read(profileRepoProvider).save(name: n);
                },
              ),
            ]),
          ),
          const SectionHeader('Modüller'),
          SoftCard(
            padding: EdgeInsets.zero,
            child: Column(children: [
              tile(Icons.emoji_events_rounded, 'Başarılarım ve rozetler', '/achievements', color: AppColors.gold),
              tile(Icons.insights_rounded, 'İstatistikler', '/stats'),
              tile(Icons.dashboard_customize_rounded, 'Hayal Panom', '/board', color: const Color(0xFFEC4899)),
              tile(Icons.repeat_rounded, 'Alışkanlıklar', '/habits', color: AppColors.turquoise),
              tile(Icons.savings_rounded, 'Hayal Kumbarası', '/savings', color: AppColors.gold),
              tile(Icons.menu_book_rounded, 'Hayal Günlüğüm', '/journal'),
              tile(Icons.hourglass_bottom_rounded, 'Gelecekteki Ben', '/capsules', color: const Color(0xFF0EA5E9)),
              tile(Icons.timer_rounded, 'Odaklanma', '/focus', color: AppColors.danger),
              tile(Icons.donut_large_rounded, 'Yaşam Dengem', '/balance', color: const Color(0xFF14B8A6)),
              tile(Icons.lightbulb_rounded, 'Akıllı Planlayıcı', '/planner', color: const Color(0xFFF97316)),
            ]),
          ),
          const SectionHeader('Uygulama'),
          SoftCard(
            padding: EdgeInsets.zero,
            child: Column(children: [
              tile(Icons.settings_rounded, 'Ayarlar', '/settings', subtitle: 'Tema, bildirimler, ana sayfa'),
              tile(Icons.lock_rounded, 'Güvenlik ve gizlilik', '/security', subtitle: 'PIN, biyometrik, gizli içerik'),
              tile(Icons.backup_rounded, 'Yedekleme ve geri yükleme', '/backup', subtitle: 'Şifreli yedek dosyası'),
              tile(Icons.inventory_2_rounded, 'Arşiv', '/archive'),
              tile(Icons.category_rounded, 'Kategoriler', '/categories'),
            ]),
          ),
          Gap.h24,
          Center(
            child: Text('Hayallerim Cepte · Hayal Et, Planla, Gerçekleştir.\nTüm verilerin yalnızca bu cihazda saklanır.',
                textAlign: TextAlign.center, style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant)),
          ),
        ],
      ),
    );
  }
}
