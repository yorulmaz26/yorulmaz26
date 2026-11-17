import 'package:material_ui/material_ui.dart';

import 'app_colors.dart';

abstract final class AppTheme {
  static ThemeData dark() {
    final scheme =
        ColorScheme.fromSeed(
          seedColor: AppColors.violet,
          brightness: Brightness.dark,
        ).copyWith(
          primary: AppColors.violet,
          onPrimary: AppColors.snow,
          secondary: AppColors.turquoise,
          onSecondary: AppColors.night,
          tertiary: AppColors.gold,
          onTertiary: AppColors.night,
          surface: AppColors.night,
          onSurface: AppColors.snow,
          surfaceContainer: AppColors.nightRaised,
          surfaceContainerHigh: AppColors.nightRaised,
          outline: AppColors.nightOutline,
        );
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: AppColors.night,
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.night,
        foregroundColor: AppColors.snow,
        centerTitle: false,
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.nightRaised,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide.none,
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: AppColors.violet, width: 1.5),
        ),
      ),
      cardTheme: CardThemeData(
        color: AppColors.nightRaised,
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      ),
    );
  }
}
