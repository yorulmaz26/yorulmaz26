import 'package:flutter/material.dart';

/// Hayallerim Cepte marka renkleri.
abstract final class AppColors {
  static const night = Color(0xFF101827); // Gece laciverti
  static const purple = Color(0xFF6D5DFB); // Premium mor
  static const turquoise = Color(0xFF20B8A6); // Turkuaz
  static const gold = Color(0xFFD6AD60); // Altın
  static const lightBg = Color(0xFFF7F8FC); // Açık arka plan
  static const card = Color(0xFFFFFFFF); // Kart arka planı

  static const darkBg = Color(0xFF0B111C);
  static const darkCard = Color(0xFF162033);

  static const success = Color(0xFF2EAD6B);
  static const warning = Color(0xFFE59E2B);
  static const danger = Color(0xFFE5484D);

  /// Kullanıcının seçebileceği vurgu renkleri.
  static const accents = <Color>[
    purple,
    turquoise,
    gold,
    Color(0xFFEF6C8F),
    Color(0xFF3B82F6),
    Color(0xFFF97316),
  ];

  static const accentNames = <String>[
    'Premium Mor',
    'Turkuaz',
    'Altın',
    'Gül',
    'Gökyüzü',
    'Gün Batımı',
  ];

  static const brandGradient = LinearGradient(
    colors: [purple, Color(0xFF8B7CFF), turquoise],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const nightGradient = LinearGradient(
    colors: [night, Color(0xFF1F2A44)],
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
  );

  static const goldGradient = LinearGradient(
    colors: [Color(0xFFE9C987), gold, Color(0xFFB98D3E)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  /// Öncelik renkleri (0: düşük, 1: orta, 2: yüksek)
  static Color priority(int p) => switch (p) {
        2 => danger,
        1 => warning,
        _ => turquoise,
      };
}
