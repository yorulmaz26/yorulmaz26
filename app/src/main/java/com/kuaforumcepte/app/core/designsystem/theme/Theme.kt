package com.kuaforumcepte.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.kuaforumcepte.app.core.datastore.ThemeMode

private val LightScheme = lightColorScheme(
    primary = Brand.Espresso,
    onPrimary = Color.White,
    primaryContainer = Brand.Champagne,
    onPrimaryContainer = Brand.Espresso,
    secondary = Brand.GoldDark,
    onSecondary = Color.White,
    secondaryContainer = Brand.Champagne,
    onSecondaryContainer = Color(0xFF4A3418),
    tertiary = Brand.Sage,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDEBE3),
    onTertiaryContainer = Color(0xFF173A2B),
    error = Brand.Red,
    onError = Color.White,
    errorContainer = Color(0xFFF8E0E0),
    onErrorContainer = Color(0xFF5C1D1D),
    background = Brand.OffWhite,
    onBackground = Brand.Ink,
    surface = Brand.OffWhite,
    onSurface = Brand.Ink,
    surfaceVariant = Brand.Sand,
    onSurfaceVariant = Brand.InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCFAF6),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF3EEE6),
    surfaceContainerHighest = Brand.Sand,
    outline = Color(0xFFCFC5B8),
    outlineVariant = Color(0xFFE7E0D5),
)

private val DarkScheme = darkColorScheme(
    primary = Brand.Gold,
    onPrimary = Brand.EspressoDeep,
    primaryContainer = Color(0xFF3B2F22),
    onPrimaryContainer = Brand.GoldLight,
    secondary = Brand.GoldLight,
    onSecondary = Brand.EspressoDeep,
    secondaryContainer = Color(0xFF332A20),
    onSecondaryContainer = Brand.GoldLight,
    tertiary = Color(0xFF8FC2A8),
    onTertiary = Brand.EspressoDeep,
    tertiaryContainer = Color(0xFF1F3A2D),
    onTertiaryContainer = Color(0xFFCDE8DA),
    error = Color(0xFFE88A8A),
    onError = Brand.EspressoDeep,
    errorContainer = Color(0xFF3F2222),
    onErrorContainer = Color(0xFFF8D6D6),
    background = Brand.EspressoDeep,
    onBackground = Color(0xFFF3EEE7),
    surface = Brand.EspressoDeep,
    onSurface = Color(0xFFF3EEE7),
    surfaceVariant = Brand.CharcoalHigh,
    onSurfaceVariant = Color(0xFFC4B9AC),
    surfaceContainerLowest = Color(0xFF0D0C0B),
    surfaceContainerLow = Color(0xFF181614),
    surfaceContainer = Brand.Charcoal,
    surfaceContainerHigh = Brand.CharcoalHigh,
    surfaceContainerHighest = Brand.CharcoalHighest,
    outline = Color(0xFF5E554C),
    outlineVariant = Color(0xFF39332D),
)

@Composable
fun KuaforumTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // Marka tutarlılığı için dinamik renk (Material You) kullanılmaz.
    CompositionLocalProvider(LocalKcColors provides if (dark) DarkExtended else LightExtended) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = KcTypography,
            shapes = KcShapes,
            content = content,
        )
    }
}

/** MaterialTheme.colorScheme yanında anlamsal renkler: KcTheme.colors.success vb. */
object KcTheme {
    val colors: KcExtendedColors
        @Composable @ReadOnlyComposable get() = LocalKcColors.current
}
