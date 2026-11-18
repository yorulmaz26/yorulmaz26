import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

import 'app_colors.dart';
import 'app_spacing.dart';

/// Material 3 tabanlı açık/koyu tema. iOS'ta platforma özgü kaydırma ve
/// sayfa geçişleri korunur.
abstract final class AppTheme {
  static ThemeData light(Color accent) => _build(Brightness.light, accent);
  static ThemeData dark(Color accent) => _build(Brightness.dark, accent);

  static ThemeData _build(Brightness b, Color accent) {
    final isDark = b == Brightness.dark;
    final scheme = ColorScheme.fromSeed(
      seedColor: accent,
      brightness: b,
      primary: accent,
      secondary: AppColors.turquoise,
      tertiary: AppColors.gold,
      surface: isDark ? AppColors.darkCard : AppColors.card,
      error: AppColors.danger,
    );
    final bg = isDark ? AppColors.darkBg : AppColors.lightBg;
    final onBg = isDark ? Colors.white : AppColors.night;

    final base = ThemeData(
      useMaterial3: true,
      brightness: b,
      colorScheme: scheme,
      scaffoldBackgroundColor: bg,
      visualDensity: VisualDensity.standard,
    );

    final text = base.textTheme.apply(bodyColor: onBg, displayColor: onBg).copyWith(
          headlineLarge: base.textTheme.headlineLarge?.copyWith(fontWeight: FontWeight.w800, letterSpacing: -0.5, color: onBg),
          headlineMedium: base.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w800, letterSpacing: -0.4, color: onBg),
          headlineSmall: base.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700, color: onBg),
          titleLarge: base.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700, color: onBg),
          titleMedium: base.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600, color: onBg),
        );

    return base.copyWith(
      textTheme: text,
      appBarTheme: AppBarTheme(
        backgroundColor: bg,
        foregroundColor: onBg,
        elevation: 0,
        scrolledUnderElevation: 0.5,
        centerTitle: false,
        titleTextStyle: text.titleLarge,
      ),
      cardTheme: CardThemeData(
        color: scheme.surface,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: Radii.card,
          side: BorderSide(color: isDark ? Colors.white10 : const Color(0xFFE9ECF5)),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: isDark ? const Color(0xFF1C273D) : Colors.white,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(Radii.md), borderSide: BorderSide.none),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Radii.md),
          borderSide: BorderSide(color: isDark ? Colors.white12 : const Color(0xFFE3E7F1)),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Radii.md),
          borderSide: BorderSide(color: accent, width: 1.6),
        ),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(48, 52),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Radii.md)),
          textStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(48, 48),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Radii.md)),
        ),
      ),
      chipTheme: base.chipTheme.copyWith(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Radii.sm)),
        side: BorderSide(color: isDark ? Colors.white12 : const Color(0xFFE3E7F1)),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: isDark ? AppColors.darkCard : Colors.white,
        indicatorColor: accent.withValues(alpha: 0.16),
        height: 68,
        labelTextStyle: WidgetStateProperty.resolveWith(
          (s) => TextStyle(
            fontSize: 12,
            fontWeight: s.contains(WidgetState.selected) ? FontWeight.w700 : FontWeight.w500,
          ),
        ),
      ),
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        backgroundColor: accent,
        foregroundColor: Colors.white,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Radii.md)),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: isDark ? AppColors.darkCard : Colors.white,
        showDragHandle: true,
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(Radii.xl))),
      ),
      dialogTheme: DialogThemeData(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Radii.lg)),
      ),
      snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating),
      pageTransitionsTheme: const PageTransitionsTheme(builders: {
        TargetPlatform.android: FadeForwardsPageTransitionsBuilder(),
        TargetPlatform.iOS: CupertinoPageTransitionsBuilder(),
      }),
      cupertinoOverrideTheme: CupertinoThemeData(primaryColor: accent, brightness: b),
    );
  }
}
