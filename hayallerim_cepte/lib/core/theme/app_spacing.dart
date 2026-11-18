import 'package:flutter/material.dart';

/// Tutarlı boşluk ve köşe yarıçapı ölçekleri.
abstract final class Gap {
  static const xs = 4.0;
  static const sm = 8.0;
  static const md = 12.0;
  static const lg = 16.0;
  static const xl = 24.0;
  static const xxl = 32.0;

  static const page = EdgeInsets.symmetric(horizontal: lg);
  static const card = EdgeInsets.all(lg);

  static const h4 = SizedBox(height: xs);
  static const h8 = SizedBox(height: sm);
  static const h12 = SizedBox(height: md);
  static const h16 = SizedBox(height: lg);
  static const h24 = SizedBox(height: xl);
  static const w4 = SizedBox(width: xs);
  static const w8 = SizedBox(width: sm);
  static const w12 = SizedBox(width: md);
  static const w16 = SizedBox(width: lg);
  static const w24 = SizedBox(width: xl);
}

abstract final class Radii {
  static const sm = 10.0;
  static const md = 16.0;
  static const lg = 22.0;
  static const xl = 28.0;

  static final card = BorderRadius.circular(lg);
  static final chip = BorderRadius.circular(sm);
}
