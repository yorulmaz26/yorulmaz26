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
    primary = Brand.Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE4EE),
    onPrimaryContainer = Brand.Navy,
    secondary = Brand.Gold,
    onSecondary = Brand.Navy,
    secondaryContainer = Color(0xFFF3E6D3),
    onSecondaryContainer = Color(0xFF4A3418),
    tertiary = Brand.Green,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7EEEA),
    onTertiaryContainer = Color(0xFF0A3A34),
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
    surfaceContainerLow = Color(0xFFFCFBF8),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF1EDE6),
    surfaceContainerHighest = Brand.Sand,
    outline = Color(0xFFC9C2B6),
    outlineVariant = Color(0xFFE4DED4),
)

private val DarkScheme = darkColorScheme(
    primary = Brand.Gold,
    onPrimary = Brand.NavyDeep,
    primaryContainer = Brand.NavyHigh,
    onPrimaryContainer = Brand.GoldLight,
    secondary = Brand.GoldLight,
    onSecondary = Brand.NavyDeep,
    secondaryContainer = Color(0xFF3A3020),
    onSecondaryContainer = Brand.GoldLight,
    tertiary = Color(0xFF4FB8AA),
    onTertiary = Brand.NavyDeep,
    tertiaryContainer = Color(0xFF173F3C),
    onTertiaryContainer = Color(0xFFBFE8E1),
    error = Color(0xFFE88A8A),
    onError = Brand.NavyDeep,
    errorContainer = Color(0xFF4A2326),
    onErrorContainer = Color(0xFFF8D6D6),
    background = Brand.NavyDeep,
    onBackground = Brand.OffWhite,
    surface = Brand.NavyDeep,
    onSurface = Brand.OffWhite,
    surfaceVariant = Brand.NavySurface,
    onSurfaceVariant = Color(0xFFB9C2CF),
    surfaceContainerLowest = Color(0xFF07101C),
    surfaceContainerLow = Color(0xFF101D30),
    surfaceContainer = Brand.Navy,
    surfaceContainerHigh = Brand.NavySurface,
    surfaceContainerHighest = Brand.NavyHigh,
    outline = Color(0xFF4A5D78),
    outlineVariant = Color(0xFF2A3D5A),
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
