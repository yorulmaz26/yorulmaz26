import 'dart:io';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../providers.dart';
import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';

/// Gradyanlı başlık kartı
class GradientCard extends StatelessWidget {
  const GradientCard({super.key, required this.child, this.gradient = AppColors.brandGradient, this.padding = Gap.card, this.onTap});
  final Widget child;
  final Gradient gradient;
  final EdgeInsets padding;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: Ink(
        decoration: BoxDecoration(
          gradient: gradient,
          borderRadius: Radii.card,
          boxShadow: [BoxShadow(color: AppColors.purple.withValues(alpha: 0.25), blurRadius: 18, offset: const Offset(0, 8))],
        ),
        child: InkWell(
          borderRadius: Radii.card,
          onTap: onTap,
          child: Padding(padding: padding, child: DefaultTextStyle.merge(style: const TextStyle(color: Colors.white), child: child)),
        ),
      ),
    );
  }
}

/// Hafif gölgeli standart kart
class SoftCard extends StatelessWidget {
  const SoftCard({super.key, required this.child, this.padding = Gap.card, this.onTap, this.color});
  final Widget child;
  final EdgeInsets padding;
  final VoidCallback? onTap;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      decoration: BoxDecoration(
        borderRadius: Radii.card,
        boxShadow: dark ? null : [BoxShadow(color: AppColors.night.withValues(alpha: 0.05), blurRadius: 16, offset: const Offset(0, 6))],
      ),
      child: Card(
        color: color,
        clipBehavior: Clip.antiAlias,
        child: InkWell(onTap: onTap, child: Padding(padding: padding, child: child)),
      ),
    );
  }
}

class SectionHeader extends StatelessWidget {
  const SectionHeader(this.title, {super.key, this.action, this.onAction});
  final String title;
  final String? action;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(0, Gap.lg, 0, Gap.sm),
      child: Row(
        children: [
          Expanded(child: Text(title, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700))),
          if (action != null) TextButton(onPressed: onAction, child: Text(action!)),
        ],
      ),
    );
  }
}

class EmptyState extends StatelessWidget {
  const EmptyState({super.key, required this.icon, required this.title, this.message, this.actionLabel, this.onAction});
  final IconData icon;
  final String title;
  final String? message;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(Gap.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 88,
              height: 88,
              decoration: BoxDecoration(color: cs.primary.withValues(alpha: 0.1), shape: BoxShape.circle),
              child: Icon(icon, size: 42, color: cs.primary),
            ),
            Gap.h16,
            Text(title, textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleLarge),
            if (message != null) ...[
              Gap.h8,
              Text(message!, textAlign: TextAlign.center, style: TextStyle(color: cs.onSurfaceVariant)),
            ],
            if (actionLabel != null) ...[
              Gap.h16,
              FilledButton.icon(onPressed: onAction, icon: const Icon(Icons.add), label: Text(actionLabel!)),
            ],
          ],
        ),
      ),
    );
  }
}

/// Dairesel ilerleme göstergesi
class ProgressRing extends StatelessWidget {
  const ProgressRing({super.key, required this.value, this.size = 56, this.stroke = 6, this.color, this.child, this.background});
  final double value;
  final double size;
  final double stroke;
  final Color? color;
  final Color? background;
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    final c = color ?? Theme.of(context).colorScheme.primary;
    return Semantics(
      label: 'İlerleme yüzde ${(value * 100).round()}',
      child: SizedBox(
        width: size,
        height: size,
        child: TweenAnimationBuilder<double>(
          tween: Tween(begin: 0, end: value.clamp(0, 1)),
          duration: const Duration(milliseconds: 700),
          curve: Curves.easeOutCubic,
          builder: (_, v, _) => CustomPaint(
            painter: _RingPainter(v, c, background ?? c.withValues(alpha: 0.15), stroke),
            child: Center(child: child ?? Text('%${(value * 100).round()}', style: TextStyle(fontWeight: FontWeight.w800, fontSize: size * 0.22))),
          ),
        ),
      ),
    );
  }
}

class _RingPainter extends CustomPainter {
  _RingPainter(this.v, this.c, this.bg, this.stroke);
  final double v;
  final Color c;
  final Color bg;
  final double stroke;

  @override
  void paint(Canvas canvas, Size size) {
    final r = Rect.fromLTWH(stroke / 2, stroke / 2, size.width - stroke, size.height - stroke);
    final p = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke
      ..strokeCap = StrokeCap.round;
    canvas.drawArc(r, 0, math.pi * 2, false, p..color = bg);
    if (v > 0) canvas.drawArc(r, -math.pi / 2, math.pi * 2 * v, false, p..color = c);
  }

  @override
  bool shouldRepaint(_RingPainter o) => o.v != v || o.c != c;
}

class LinearBar extends StatelessWidget {
  const LinearBar({super.key, required this.value, this.color, this.height = 8});
  final double value;
  final Color? color;
  final double height;

  @override
  Widget build(BuildContext context) {
    final c = color ?? Theme.of(context).colorScheme.primary;
    return ClipRRect(
      borderRadius: BorderRadius.circular(height),
      child: TweenAnimationBuilder<double>(
        tween: Tween(begin: 0, end: value.clamp(0, 1)),
        duration: const Duration(milliseconds: 600),
        builder: (_, v, _) => LinearProgressIndicator(value: v, minHeight: height, color: c, backgroundColor: c.withValues(alpha: 0.14)),
      ),
    );
  }
}

/// Medya klasöründeki görseli küçültülmüş olarak gösterir (bellek dostu).
class LocalImage extends ConsumerWidget {
  const LocalImage(this.path, {super.key, this.fit = BoxFit.cover, this.cacheWidth = 600, this.placeholder});
  final String? path;
  final BoxFit fit;
  final int cacheWidth;
  final Widget? placeholder;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final file = ref.watch(mediaProvider).resolve(path);
    final ph = placeholder ??
        Container(
          color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.08),
          child: Icon(Icons.image_outlined, color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.5)),
        );
    if (file == null) return ph;
    final dpr = MediaQuery.devicePixelRatioOf(context);
    return Image.file(
      file,
      fit: fit,
      cacheWidth: (cacheWidth * dpr).round().clamp(100, 2048),
      gaplessPlayback: true,
      errorBuilder: (_, _, _) => ph,
    );
  }
}

class Avatar extends ConsumerWidget {
  const Avatar({super.key, this.path, this.name = '', this.size = 48});
  final String? path;
  final String name;
  final double size;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final File? f = ref.watch(mediaProvider).resolve(path);
    final initial = name.trim().isEmpty ? '✨' : name.trim().characters.first.toUpperCase();
    return Container(
      width: size,
      height: size,
      decoration: const BoxDecoration(shape: BoxShape.circle, gradient: AppColors.brandGradient),
      clipBehavior: Clip.antiAlias,
      child: f != null
          ? Image.file(f, fit: BoxFit.cover, cacheWidth: (size * 3).round(), errorBuilder: (_, _, _) => const SizedBox())
          : Center(child: Text(initial, style: TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: size * 0.4))),
    );
  }
}

class PriorityChip extends StatelessWidget {
  const PriorityChip(this.priority, {super.key});
  final int priority;
  static const labels = ['Düşük', 'Orta', 'Yüksek'];

  @override
  Widget build(BuildContext context) {
    final c = AppColors.priority(priority);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(color: c.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(8)),
      child: Text(labels[priority.clamp(0, 2)], style: TextStyle(color: c, fontSize: 11, fontWeight: FontWeight.w700)),
    );
  }
}

class StatTile extends StatelessWidget {
  const StatTile({super.key, required this.icon, required this.value, required this.label, this.color});
  final IconData icon;
  final String value;
  final String label;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final c = color ?? Theme.of(context).colorScheme.primary;
    return SoftCard(
      padding: const EdgeInsets.all(Gap.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: c, size: 22),
          Gap.h8,
          FittedBox(child: Text(value, style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800))),
          Text(label, maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant)),
        ],
      ),
    );
  }
}

/// Basit onay iletişim kutusu
Future<bool> confirm(BuildContext context, {required String title, String? message, String ok = 'Evet', bool destructive = false}) async {
  final r = await showDialog<bool>(
    context: context,
    builder: (c) => AlertDialog(
      title: Text(title),
      content: message == null ? null : Text(message),
      actions: [
        TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Vazgeç')),
        FilledButton(
          style: destructive ? FilledButton.styleFrom(backgroundColor: AppColors.danger) : null,
          onPressed: () => Navigator.pop(c, true),
          child: Text(ok),
        ),
      ],
    ),
  );
  return r ?? false;
}

void toast(BuildContext context, String message) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message)));
}

/// Fotoğraf kaynağı seçimi (galeri / kamera)
Future<bool?> pickSourceSheet(BuildContext context) => showModalBottomSheet<bool>(
      context: context,
      builder: (c) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(leading: const Icon(Icons.photo_library_outlined), title: const Text('Galeriden seç'), onTap: () => Navigator.pop(c, false)),
          ListTile(leading: const Icon(Icons.photo_camera_outlined), title: const Text('Kamerayla çek'), onTap: () => Navigator.pop(c, true)),
        ]),
      ),
    );

/// Metin girişi iletişim kutusu
Future<String?> promptText(BuildContext context, {required String title, String initial = '', String hint = '', int maxLength = 120, int maxLines = 1}) {
  final c = TextEditingController(text: initial);
  return showDialog<String>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(title),
      content: TextField(
        controller: c,
        autofocus: true,
        maxLength: maxLength,
        maxLines: maxLines,
        decoration: InputDecoration(hintText: hint),
        textCapitalization: TextCapitalization.sentences,
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Vazgeç')),
        FilledButton(
          onPressed: () {
            final v = c.text.trim();
            if (v.isNotEmpty) Navigator.pop(ctx, v);
          },
          child: const Text('Kaydet'),
        ),
      ],
    ),
  );
}

class ChoiceChips<T> extends StatelessWidget {
  const ChoiceChips({super.key, required this.values, required this.labels, required this.selected, required this.onSelected});
  final List<T> values;
  final List<String> labels;
  final T selected;
  final ValueChanged<T> onSelected;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        for (var i = 0; i < values.length; i++)
          ChoiceChip(label: Text(labels[i]), selected: values[i] == selected, onSelected: (_) => onSelected(values[i])),
      ],
    );
  }
}

/// Tarih seçici (Türkçe)
Future<DateTime?> pickDate(BuildContext context, {DateTime? initial, DateTime? first, DateTime? last}) => showDatePicker(
      context: context,
      initialDate: initial ?? DateTime.now(),
      firstDate: first ?? DateTime(2000),
      lastDate: last ?? DateTime(2100),
      locale: const Locale('tr', 'TR'),
    );

Future<TimeOfDay?> pickTime(BuildContext context, {TimeOfDay? initial}) =>
    showTimePicker(context: context, initialTime: initial ?? const TimeOfDay(hour: 9, minute: 0));
