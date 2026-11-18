import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../theme/app_colors.dart';

/// Hayallerim Cepte logosu: yükselen yol + hayal bulutu + stilize yıldız.
/// Vektörel çizildiği için her çözünürlükte keskindir ve varlık dosyası
/// gerektirmez.
class AppLogo extends StatelessWidget {
  const AppLogo({super.key, this.size = 120, this.progress = 1});
  final double size;

  /// 0..1 — açılış animasyonu için çizim ilerlemesi.
  final double progress;

  @override
  Widget build(BuildContext context) => SizedBox(
        width: size,
        height: size,
        child: CustomPaint(painter: _LogoPainter(progress)),
      );
}

class _LogoPainter extends CustomPainter {
  _LogoPainter(this.t);
  final double t;

  @override
  void paint(Canvas canvas, Size s) {
    final w = s.width;
    final h = s.height;
    // Arka plan rozeti
    final r = RRect.fromRectAndRadius(Offset.zero & s, Radius.circular(w * 0.26));
    canvas.drawRRect(
      r,
      Paint()
        ..shader = const LinearGradient(
          colors: [AppColors.night, Color(0xFF26325A)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ).createShader(Offset.zero & s),
    );

    // Yükselen yol
    final road = Path()
      ..moveTo(w * 0.22, h * 0.9)
      ..cubicTo(w * 0.3, h * 0.66, w * 0.62, h * 0.72, w * 0.56, h * 0.52)
      ..cubicTo(w * 0.52, h * 0.42, w * 0.44, h * 0.42, w * 0.5, h * 0.36);
    final metric = road.computeMetrics().first;
    final partial = metric.extractPath(0, metric.length * Curves.easeOut.transform(t.clamp(0, 1)));
    canvas.drawPath(
      partial,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeCap = StrokeCap.round
        ..strokeWidth = w * 0.075
        ..shader = const LinearGradient(colors: [AppColors.turquoise, AppColors.purple], begin: Alignment.bottomLeft, end: Alignment.topRight)
            .createShader(Offset.zero & s),
    );

    // Hayal bulutu
    final cloudT = ((t - 0.3) / 0.4).clamp(0.0, 1.0);
    if (cloudT > 0) {
      final cp = Paint()..color = Colors.white.withValues(alpha: 0.92 * cloudT);
      final cx = w * 0.34, cy = h * 0.34;
      final k = w * 0.075 * cloudT;
      canvas.drawCircle(Offset(cx - k * 1.2, cy + k * 0.3), k * 1.05, cp);
      canvas.drawCircle(Offset(cx, cy - k * 0.2), k * 1.4, cp);
      canvas.drawCircle(Offset(cx + k * 1.3, cy + k * 0.3), k * 1.0, cp);
      canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTRB(cx - k * 2.2, cy + k * 0.1, cx + k * 2.3, cy + k * 1.35), Radius.circular(k)), cp);
    }

    // Yıldız
    final starT = ((t - 0.55) / 0.45).clamp(0.0, 1.0);
    if (starT > 0) {
      final c = Offset(w * 0.68, h * 0.24);
      final outer = w * 0.13 * Curves.elasticOut.transform(starT);
      final inner = outer * 0.45;
      final path = Path();
      for (var i = 0; i < 10; i++) {
        final rad = i.isEven ? outer : inner;
        final a = -math.pi / 2 + i * math.pi / 5;
        final p = c + Offset(math.cos(a) * rad, math.sin(a) * rad);
        i == 0 ? path.moveTo(p.dx, p.dy) : path.lineTo(p.dx, p.dy);
      }
      path.close();
      canvas.drawPath(path, Paint()..shader = AppColors.goldGradient.createShader(Rect.fromCircle(center: c, radius: outer)));
      canvas.drawCircle(c, outer * 1.6, Paint()..color = AppColors.gold.withValues(alpha: 0.12 * starT));
    }
  }

  @override
  bool shouldRepaint(_LogoPainter o) => o.t != t;
}
