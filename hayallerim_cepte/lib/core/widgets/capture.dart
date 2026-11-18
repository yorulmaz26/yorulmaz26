import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';
import 'package:flutter/widgets.dart';

/// RepaintBoundary içeriğini PNG olarak yakalar (pano, rozet kartı).
Future<Uint8List?> capturePng(GlobalKey key, {double pixelRatio = 3}) async {
  final obj = key.currentContext?.findRenderObject();
  if (obj is! RenderRepaintBoundary) return null;
  final image = await obj.toImage(pixelRatio: pixelRatio);
  final data = await image.toByteData(format: ui.ImageByteFormat.png);
  image.dispose();
  return data?.buffer.asUint8List();
}
