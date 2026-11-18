import 'package:flutter/material.dart';

/// Kategori ve alışkanlık ikonları. Veritabanında anahtar (String) saklanır;
/// böylece ikon ağacı küçültme (tree-shaking) bozulmaz.
abstract final class AppIcons {
  static const map = <String, IconData>{
    'home': Icons.home_rounded,
    'car': Icons.directions_car_rounded,
    'flight': Icons.flight_takeoff_rounded,
    'school': Icons.school_rounded,
    'work': Icons.work_rounded,
    'savings': Icons.savings_rounded,
    'health': Icons.favorite_rounded,
    'family': Icons.diversity_1_rounded,
    'growth': Icons.self_improvement_rounded,
    'palette': Icons.palette_rounded,
    'star': Icons.star_rounded,
    'book': Icons.menu_book_rounded,
    'run': Icons.directions_run_rounded,
    'water': Icons.water_drop_rounded,
    'language': Icons.translate_rounded,
    'money': Icons.payments_rounded,
    'music': Icons.music_note_rounded,
    'sleep': Icons.bedtime_rounded,
    'food': Icons.restaurant_rounded,
    'meditation': Icons.spa_rounded,
    'code': Icons.code_rounded,
    'camera': Icons.photo_camera_rounded,
    'pet': Icons.pets_rounded,
    'check': Icons.check_circle_rounded,
  };

  static IconData of(String key) => map[key] ?? Icons.star_rounded;
}
