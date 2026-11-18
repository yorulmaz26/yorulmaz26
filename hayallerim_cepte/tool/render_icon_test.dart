// Uygulama ikonunu vektör logodan üretir:
//   flutter test tool/render_icon_test.dart && python3 tool/make_icons.py
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hayallerim_cepte/core/widgets/logo.dart';

void main() {
  testWidgets('ikon 1024 px', (t) async {
    final key = GlobalKey();
    t.view.physicalSize = const Size(1024, 1024);
    t.view.devicePixelRatio = 1;
    await t.pumpWidget(Directionality(
      textDirection: TextDirection.ltr,
      child: Center(child: RepaintBoundary(key: key, child: const AppLogo(size: 1024, square: true))),
    ));
    await t.runAsync(() async {
      final b = key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
      final img = await b.toImage();
      final data = await img.toByteData(format: ui.ImageByteFormat.png);
      await File('tool/icon_1024.png').writeAsBytes(data!.buffer.asUint8List());
    });
    t.view.reset();
  });
}
