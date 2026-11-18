import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/database/app_database.dart';
import '../../core/logic/progress_logic.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';
import '../security/lock_controller.dart';
import 'plant_painter.dart';

/// Hayal Bahçem — her hayal bir bitki. Adımlar tamamlandıkça büyür;
/// gerçekleşen hayaller meyve veren ağaç olarak bahçede kalır.
class GardenScreen extends ConsumerStatefulWidget {
  const GardenScreen({super.key});
  @override
  ConsumerState<GardenScreen> createState() => _GardenScreenState();
}

class _GardenScreenState extends ConsumerState<GardenScreen> with SingleTickerProviderStateMixin {
  late final AnimationController _wind = AnimationController(vsync: this, duration: const Duration(seconds: 4))..repeat();

  @override
  void dispose() {
    _wind.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final privateOpen = ref.watch(lockProvider).privateUnlocked;
    final trees = (ref.watch(gardenProvider).value ?? const <(GardenTree, Dream, DreamCategory)>[])
        .where((t) => !t.$2.isHidden || privateOpen)
        .toList();
    final views = {for (final v in ref.watch(dreamViewsProvider).value ?? const <DreamView>[]) v.dream.id: v};
    final fruit = trees.where((t) => t.$1.stage >= 6).length;
    final blooming = trees.where((t) => t.$1.stage == 5).length;
    final points = ref.watch(pointsProvider).value ?? 0;
    final lvl = ProgressLogic.level(points);
    final hour = DateTime.now().hour;
    final night = hour < 6 || hour >= 20;
    final reduceMotion = MediaQuery.disableAnimationsOf(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Hayal Bahçem'),
        actions: [
          IconButton(tooltip: 'Başarılarım', icon: const Icon(Icons.emoji_events_outlined), onPressed: () => context.push('/achievements')),
        ],
      ),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: Gap.lg),
          child: Row(children: [
            Expanded(child: StatTile(icon: Icons.park_rounded, value: '${trees.length}', label: 'Bitki', color: AppColors.success)),
            Gap.w8,
            Expanded(child: StatTile(icon: Icons.local_florist, value: '$blooming', label: 'Çiçek açan', color: const Color(0xFFEC4899))),
            Gap.w8,
            Expanded(child: StatTile(icon: Icons.apple, value: '$fruit', label: 'Meyve veren', color: AppColors.danger)),
            Gap.w8,
            Expanded(child: StatTile(icon: Icons.star_rounded, value: '${lvl.level}', label: 'Seviye', color: AppColors.gold)),
          ]),
        ),
        Gap.h12,
        Expanded(
          child: Container(
            margin: const EdgeInsets.symmetric(horizontal: Gap.lg),
            decoration: BoxDecoration(borderRadius: BorderRadius.circular(Radii.xl)),
            clipBehavior: Clip.antiAlias,
            child: LayoutBuilder(builder: (context, c) {
              const slotW = 150.0;
              final worldW = math.max(c.maxWidth, trees.length * slotW + 80);
              final plantSize = (c.maxHeight * 0.42).clamp(90.0, 170.0);
              return SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                physics: const BouncingScrollPhysics(),
                child: SizedBox(
                  width: worldW,
                  height: c.maxHeight,
                  child: Stack(children: [
                    Positioned.fill(
                      child: RepaintBoundary(
                        child: AnimatedBuilder(
                          animation: _wind,
                          builder: (_, _) => CustomPaint(painter: _SkyPainter(night: night, t: reduceMotion ? 0 : _wind.value)),
                        ),
                      ),
                    ),
                    if (trees.isEmpty)
                      Center(
                        child: Padding(
                          padding: const EdgeInsets.all(32),
                          child: EmptyState(
                            icon: Icons.grass_rounded,
                            title: 'Bahçen seni bekliyor',
                            message: 'Her yeni hayal bahçene bir tohum eker. Adımlarını tamamladıkça filizlenir, büyür ve meyve verir.',
                            actionLabel: 'Hayal ekle',
                            onAction: () => context.push('/dream/new'),
                          ),
                        ),
                      ),
                    for (var i = 0; i < trees.length; i++)
                      Positioned(
                        left: 40 + i * slotW + (slotW - plantSize) / 2,
                        bottom: c.maxHeight * 0.14 + (i.isOdd ? 14 : 0),
                        child: _TreeView(
                          data: trees[i],
                          progress: views[trees[i].$2.id]?.progress ?? 0,
                          size: plantSize,
                          wind: _wind,
                          phase: i * 0.7,
                          reduceMotion: reduceMotion,
                        ),
                      ),
                  ]),
                ),
              );
            }),
          ),
        ),
        Padding(
          padding: const EdgeInsets.all(Gap.md),
          child: Text(
            trees.isEmpty ? '' : '← Bahçeni kaydırarak gez →   ·   Ağaca dokunup hayaline git',
            style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant),
          ),
        ),
      ]),
    );
  }
}

class _TreeView extends StatelessWidget {
  const _TreeView({required this.data, required this.progress, required this.size, required this.wind, required this.phase, required this.reduceMotion});
  final (GardenTree, Dream, DreamCategory) data;
  final double progress;
  final double size;
  final Animation<double> wind;
  final double phase;
  final bool reduceMotion;

  @override
  Widget build(BuildContext context) {
    final (tree, dream, cat) = data;
    return Semantics(
      button: true,
      label: '${dream.title}, ${ProgressLogic.stageNames[tree.stage]}, yüzde ${(progress * 100).round()}',
      child: GestureDetector(
        onTap: () => _info(context),
        child: SizedBox(
          width: size,
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            AnimatedBuilder(
              animation: wind,
              builder: (_, _) => Plant(
                stage: tree.stage,
                type: tree.plantType,
                size: size,
                seed: tree.id,
                sway: reduceMotion ? 0 : math.sin(wind.value * math.pi * 2 + phase) * 0.035,
              ),
            ),
            Container(
              margin: const EdgeInsets.only(top: 4),
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.88), borderRadius: BorderRadius.circular(10)),
              child: Column(children: [
                Text(dream.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: AppColors.night)),
                Text(ProgressLogic.stageNames[tree.stage], style: TextStyle(fontSize: 10, color: Color(cat.color))),
              ]),
            ),
          ]),
        ),
      ),
    );
  }

  void _info(BuildContext context) {
    final (tree, dream, cat) = data;
    showModalBottomSheet<void>(
      context: context,
      builder: (c) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(Gap.lg),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Plant(stage: tree.stage, type: tree.plantType, size: 120, seed: tree.id),
            Text(dream.title, style: Theme.of(context).textTheme.titleLarge, textAlign: TextAlign.center),
            Gap.h4,
            Text('${kPlantNames[tree.plantType]} · ${ProgressLogic.stageNames[tree.stage]} · %${(progress * 100).round()}'),
            Gap.h12,
            Row(children: [
              for (var s = 0; s < 7; s++)
                Expanded(
                  child: Container(
                    height: 6,
                    margin: const EdgeInsets.symmetric(horizontal: 2),
                    decoration: BoxDecoration(
                      color: s <= tree.stage ? Color(cat.color) : Color(cat.color).withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
                ),
            ]),
            Gap.h8,
            Text(
              tree.stage >= 6
                  ? 'Bu hayal gerçekleşti ve meyvelerini verdi 🍎'
                  : 'Sonraki aşama: ${ProgressLogic.stageNames[(tree.stage + 1).clamp(0, 6)]} — yolculuk adımlarını tamamla.',
              textAlign: TextAlign.center,
            ),
            Gap.h16,
            FilledButton(
              onPressed: () {
                Navigator.pop(c);
                context.push('/dream/${dream.id}');
              },
              child: const Text('Hayale git'),
            ),
          ]),
        ),
      ),
    );
  }
}

class _SkyPainter extends CustomPainter {
  _SkyPainter({required this.night, required this.t});
  final bool night;
  final double t;

  @override
  void paint(Canvas canvas, Size s) {
    final sky = Rect.fromLTWH(0, 0, s.width, s.height);
    canvas.drawRect(
      sky,
      Paint()
        ..shader = LinearGradient(
          colors: night ? const [Color(0xFF0B1330), Color(0xFF2B3A67)] : const [Color(0xFF8ED1FC), Color(0xFFE3F4FF)],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ).createShader(sky),
    );
    // Güneş / ay
    final orb = Offset(s.width * 0.82 > 260 ? 220 : s.width * 0.75, s.height * 0.15);
    if (night) {
      canvas.drawCircle(orb, 22, Paint()..color = const Color(0xFFFFF8E1));
      final rnd = math.Random(7);
      for (var i = 0; i < 60; i++) {
        canvas.drawCircle(
          Offset(rnd.nextDouble() * s.width, rnd.nextDouble() * s.height * 0.5),
          rnd.nextDouble() * 1.5 + 0.3,
          Paint()..color = Colors.white.withValues(alpha: 0.4 + 0.5 * ((math.sin(t * math.pi * 2 + i) + 1) / 2)),
        );
      }
    } else {
      canvas.drawCircle(orb, 40, Paint()..color = const Color(0x33FFD54F));
      canvas.drawCircle(orb, 26, Paint()..color = const Color(0xFFFFD54F));
      final cloud = Paint()..color = Colors.white.withValues(alpha: 0.85);
      for (var i = 0; i < (s.width / 260).ceil() + 1; i++) {
        final x = ((i * 260 + t * 60) % (s.width + 200)) - 100;
        final y = s.height * (0.12 + (i % 3) * 0.08);
        canvas.drawCircle(Offset(x, y), 18, cloud);
        canvas.drawCircle(Offset(x + 20, y - 8), 22, cloud);
        canvas.drawCircle(Offset(x + 44, y), 16, cloud);
      }
    }
    // Uzak tepeler
    final hills = Path()..moveTo(0, s.height * 0.72);
    for (double x = 0; x <= s.width; x += 40) {
      hills.lineTo(x, s.height * 0.68 + math.sin(x / 120) * 18);
    }
    hills
      ..lineTo(s.width, s.height)
      ..lineTo(0, s.height)
      ..close();
    canvas.drawPath(hills, Paint()..color = night ? const Color(0xFF1E3A2F) : const Color(0xFFA5D6A7));
    // Zemin
    final ground = Rect.fromLTWH(0, s.height * 0.8, s.width, s.height * 0.2);
    canvas.drawRect(
      ground,
      Paint()
        ..shader = LinearGradient(
          colors: night ? const [Color(0xFF2E5E3A), Color(0xFF1B3A24)] : const [Color(0xFF8BC34A), Color(0xFF689F38)],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ).createShader(ground),
    );
    // Çimen tutamları
    final grass = Paint()
      ..color = night ? const Color(0xFF3E7D4C) : const Color(0xFF7CB342)
      ..strokeWidth = 2
      ..strokeCap = StrokeCap.round;
    final rnd = math.Random(3);
    for (double x = 4; x < s.width; x += 14) {
      final y = s.height * 0.8 + rnd.nextDouble() * s.height * 0.15;
      canvas.drawLine(Offset(x, y), Offset(x - 3, y - 7), grass);
      canvas.drawLine(Offset(x, y), Offset(x + 3, y - 8), grass);
    }
  }

  @override
  bool shouldRepaint(_SkyPainter o) => o.t != t || o.night != night;
}
