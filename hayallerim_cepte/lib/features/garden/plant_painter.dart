import 'dart:math' as math;

import 'package:flutter/material.dart';

/// Bitki türü paletleri: (yaprak, koyu yaprak, çiçek, meyve)
const _palettes = <String, (Color, Color, Color, Color)>{
  'oak': (Color(0xFF4CAF50), Color(0xFF2E7D32), Color(0xFFFFF59D), Color(0xFF8D6E63)),
  'pine': (Color(0xFF2E7D5B), Color(0xFF1B5E45), Color(0xFFFFFFFF), Color(0xFF8D6E63)),
  'palm': (Color(0xFF66BB6A), Color(0xFF388E3C), Color(0xFFFFCC80), Color(0xFF795548)),
  'sakura': (Color(0xFF81C784), Color(0xFF4CAF50), Color(0xFFF8BBD0), Color(0xFFE91E63)),
  'apple': (Color(0xFF66BB6A), Color(0xFF2E7D32), Color(0xFFFFFFFF), Color(0xFFE53935)),
  'lemon': (Color(0xFF7CB342), Color(0xFF558B2F), Color(0xFFFFFFFF), Color(0xFFFDD835)),
  'maple': (Color(0xFFFF8A65), Color(0xFFE64A19), Color(0xFFFFE082), Color(0xFFBF360C)),
  'sunflower': (Color(0xFF8BC34A), Color(0xFF558B2F), Color(0xFFFFC107), Color(0xFF6D4C41)),
};

const kPlantTypes = ['oak', 'pine', 'palm', 'sakura', 'apple', 'lemon', 'maple', 'sunflower'];
const kPlantNames = {
  'oak': 'Meşe',
  'pine': 'Çam',
  'palm': 'Palmiye',
  'sakura': 'Sakura',
  'apple': 'Elma',
  'lemon': 'Limon',
  'maple': 'Akçaağaç',
  'sunflower': 'Ayçiçeği',
};

/// 2D vektörel bitki çizimi (7 aşama). Hafif: yalnızca temel şekiller.
class PlantPainter extends CustomPainter {
  PlantPainter({required this.stage, required this.type, this.sway = 0, this.seed = 0});

  final int stage; // 0..6
  final String type;
  final double sway; // radyan
  final int seed;

  @override
  void paint(Canvas canvas, Size size) {
    final pal = _palettes[type] ?? _palettes['oak']!;
    final w = size.width;
    final h = size.height;
    final base = Offset(w / 2, h * 0.92);
    final rnd = math.Random(seed);

    // Toprak tümseği
    canvas.drawOval(Rect.fromCenter(center: base + Offset(0, h * 0.02), width: w * 0.62, height: h * 0.08),
        Paint()..color = const Color(0xFF8D6E63));
    canvas.drawOval(Rect.fromCenter(center: base, width: w * 0.5, height: h * 0.05), Paint()..color = const Color(0xFFA1887F));

    if (stage <= 0) {
      // Tohum
      canvas.save();
      canvas.translate(base.dx, base.dy - h * 0.035);
      canvas.rotate(-0.4);
      canvas.drawOval(Rect.fromCenter(center: Offset.zero, width: w * 0.09, height: w * 0.14), Paint()..color = const Color(0xFF6D4C41));
      canvas.drawOval(Rect.fromCenter(center: Offset(-w * 0.012, -w * 0.02), width: w * 0.03, height: w * 0.05),
          Paint()..color = const Color(0xFFA1887F));
      canvas.restore();
      return;
    }

    canvas.save();
    canvas.translate(base.dx, base.dy);
    canvas.rotate(sway * (stage <= 2 ? 1.6 : 1));
    final leaf = Paint()..color = pal.$1;
    final dark = Paint()..color = pal.$2;

    if (stage == 1) {
      final stem = Paint()
        ..color = pal.$2
        ..strokeWidth = w * 0.025
        ..strokeCap = StrokeCap.round;
      canvas.drawLine(Offset.zero, Offset(0, -h * 0.18), stem);
      _leaf(canvas, Offset(0, -h * 0.17), w * 0.12, -0.7, leaf);
      _leaf(canvas, Offset(0, -h * 0.17), w * 0.12, math.pi + 0.7, leaf);
      canvas.restore();
      return;
    }

    if (type == 'sunflower') {
      _sunflower(canvas, w, h, pal);
      canvas.restore();
      return;
    }
    if (type == 'palm') {
      _palm(canvas, w, h, pal);
      canvas.restore();
      return;
    }

    // Gövde
    final trunkH = h * [0, 0, 0.3, 0.4, 0.5, 0.52, 0.52][stage];
    final trunkW = w * [0, 0, 0.03, 0.06, 0.09, 0.1, 0.1][stage];
    final trunk = Path()
      ..moveTo(-trunkW, 0)
      ..quadraticBezierTo(-trunkW * 0.6, -trunkH * 0.5, -trunkW * 0.45, -trunkH)
      ..lineTo(trunkW * 0.45, -trunkH)
      ..quadraticBezierTo(trunkW * 0.6, -trunkH * 0.5, trunkW, 0)
      ..close();
    canvas.drawPath(trunk, Paint()..color = const Color(0xFF795548));

    if (stage == 2) {
      for (var i = 0; i < 4; i++) {
        final y = -trunkH * (0.45 + i * 0.18);
        _leaf(canvas, Offset(0, y), w * 0.11, i.isEven ? -0.5 : math.pi + 0.5, leaf);
      }
      canvas.restore();
      return;
    }

    final top = Offset(0, -trunkH);
    final r = w * [0, 0, 0, 0.2, 0.3, 0.32, 0.32][stage];
    final blobs = <(Offset, double)>[];
    if (type == 'pine') {
      for (var i = 0; i < 3; i++) {
        final y = top.dy + r * 0.5 - i * r * 0.55;
        final half = r * (1.05 - i * 0.25);
        final tri = Path()
          ..moveTo(-half, y)
          ..lineTo(0, y - r * 0.9)
          ..lineTo(half, y)
          ..close();
        canvas.drawPath(tri, i.isEven ? dark : leaf);
        blobs.add((Offset(0, y - r * 0.35), half * 0.6));
      }
    } else {
      final pts = [
        Offset(0, -r * 0.35),
        Offset(-r * 0.6, 0),
        Offset(r * 0.6, 0),
        Offset(-r * 0.3, -r * 0.75),
        Offset(r * 0.35, -r * 0.7),
        Offset(0, r * 0.15),
      ];
      for (var i = 0; i < pts.length; i++) {
        final c = top + pts[i];
        final rr = r * (0.55 + (i % 3) * 0.08);
        canvas.drawCircle(c + Offset(0, rr * 0.12), rr, dark);
        canvas.drawCircle(c, rr, leaf);
        blobs.add((c, rr));
      }
      // Parlama
      canvas.drawCircle(top + Offset(-r * 0.3, -r * 0.55), r * 0.18, Paint()..color = Colors.white.withValues(alpha: 0.18));
    }

    if (stage >= 5) {
      final bloom = Paint()..color = pal.$3;
      final center = Paint()..color = const Color(0xFFFFD54F);
      for (var i = 0; i < 9; i++) {
        final b = blobs[rnd.nextInt(blobs.length)];
        final a = rnd.nextDouble() * math.pi * 2;
        final d = rnd.nextDouble() * b.$2 * 0.8;
        final p = b.$1 + Offset(math.cos(a) * d, math.sin(a) * d);
        for (var k = 0; k < 5; k++) {
          final pa = k * math.pi * 2 / 5;
          canvas.drawCircle(p + Offset(math.cos(pa), math.sin(pa)) * (w * 0.018), w * 0.016, bloom);
        }
        canvas.drawCircle(p, w * 0.01, center);
      }
    }
    if (stage >= 6) {
      final fruit = Paint()..color = pal.$4;
      for (var i = 0; i < 7; i++) {
        final b = blobs[rnd.nextInt(blobs.length)];
        final a = rnd.nextDouble() * math.pi * 2;
        final d = rnd.nextDouble() * b.$2 * 0.75;
        final p = b.$1 + Offset(math.cos(a) * d, math.sin(a) * d);
        canvas.drawCircle(p, w * 0.032, fruit);
        canvas.drawCircle(p + Offset(-w * 0.01, -w * 0.01), w * 0.009, Paint()..color = Colors.white.withValues(alpha: 0.6));
      }
    }
    canvas.restore();
  }

  void _leaf(Canvas c, Offset at, double len, double angle, Paint p) {
    c.save();
    c.translate(at.dx, at.dy);
    c.rotate(angle);
    final path = Path()
      ..moveTo(0, 0)
      ..quadraticBezierTo(len * 0.5, -len * 0.35, len, 0)
      ..quadraticBezierTo(len * 0.5, len * 0.35, 0, 0);
    c.drawPath(path, p);
    c.restore();
  }

  void _sunflower(Canvas canvas, double w, double h, (Color, Color, Color, Color) pal) {
    final height = h * [0, 0, 0.32, 0.45, 0.6, 0.66, 0.68][stage];
    final stem = Paint()
      ..color = pal.$2
      ..strokeWidth = w * 0.035
      ..strokeCap = StrokeCap.round;
    canvas.drawLine(Offset.zero, Offset(0, -height), stem);
    final leaf = Paint()..color = pal.$1;
    for (var i = 0; i < (stage >= 4 ? 4 : 2); i++) {
      _leaf(canvas, Offset(0, -height * (0.25 + i * 0.18)), w * (0.16 + stage * 0.01), i.isEven ? -0.3 : math.pi + 0.3, leaf);
    }
    final head = Offset(0, -height);
    if (stage >= 4) {
      final petal = Paint()..color = pal.$3;
      final pr = w * (stage >= 5 ? 0.17 : 0.1);
      for (var i = 0; i < 14; i++) {
        final a = i * math.pi * 2 / 14;
        canvas.save();
        canvas.translate(head.dx, head.dy);
        canvas.rotate(a);
        canvas.drawOval(Rect.fromCenter(center: Offset(pr * 0.75, 0), width: pr * 0.9, height: pr * 0.32), petal);
        canvas.restore();
      }
      canvas.drawCircle(head, pr * 0.5, Paint()..color = pal.$4);
      if (stage >= 6) {
        final dot = Paint()..color = const Color(0xFF3E2723);
        for (var i = 0; i < 12; i++) {
          final a = i * 2.4;
          final d = pr * 0.4 * math.sqrt(i / 12);
          canvas.drawCircle(head + Offset(math.cos(a) * d, math.sin(a) * d), w * 0.008, dot);
        }
      }
    } else {
      canvas.drawCircle(head, w * 0.04, Paint()..color = pal.$2);
    }
  }

  void _palm(Canvas canvas, double w, double h, (Color, Color, Color, Color) pal) {
    final height = h * [0, 0, 0.3, 0.45, 0.6, 0.62, 0.62][stage];
    final trunk = Paint()
      ..color = const Color(0xFF8D6E63)
      ..strokeWidth = w * (0.03 + stage * 0.008)
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;
    final path = Path()
      ..moveTo(0, 0)
      ..quadraticBezierTo(w * 0.08, -height * 0.5, w * 0.04, -height);
    canvas.drawPath(path, trunk);
    final top = Offset(w * 0.04, -height);
    final frond = Paint()
      ..color = pal.$1
      ..strokeWidth = w * 0.05
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;
    final len = w * (0.12 + stage * 0.04);
    for (var i = 0; i < 6; i++) {
      final a = -math.pi / 2 + (i - 2.5) * 0.55;
      final end = top + Offset(math.cos(a) * len, math.sin(a) * len * 0.6 + len * 0.35);
      final ctrl = top + Offset(math.cos(a) * len * 0.5, math.sin(a) * len * 0.6 - len * 0.15);
      canvas.drawPath(Path()..moveTo(top.dx, top.dy)..quadraticBezierTo(ctrl.dx, ctrl.dy, end.dx, end.dy), frond..color = i.isEven ? pal.$1 : pal.$2);
    }
    if (stage >= 5) {
      final bloom = Paint()..color = pal.$3;
      canvas.drawCircle(top + Offset(-w * 0.05, w * 0.03), w * 0.025, bloom);
      canvas.drawCircle(top + Offset(w * 0.06, w * 0.02), w * 0.025, bloom);
    }
    if (stage >= 6) {
      final fruit = Paint()..color = pal.$4;
      for (var i = 0; i < 3; i++) {
        canvas.drawCircle(top + Offset((i - 1) * w * 0.035, w * 0.05), w * 0.03, fruit);
      }
    }
  }

  @override
  bool shouldRepaint(PlantPainter o) => o.stage != stage || o.type != type || o.sway != sway;
}

/// Tek bir bitki widget'ı (isteğe bağlı salınım animasyonuyla).
class Plant extends StatelessWidget {
  const Plant({super.key, required this.stage, required this.type, this.size = 120, this.sway = 0, this.seed = 0});
  final int stage;
  final String type;
  final double size;
  final double sway;
  final int seed;

  @override
  Widget build(BuildContext context) => RepaintBoundary(
        child: CustomPaint(size: Size(size, size * 1.25), painter: PlantPainter(stage: stage, type: type, sway: sway, seed: seed)),
      );
}
