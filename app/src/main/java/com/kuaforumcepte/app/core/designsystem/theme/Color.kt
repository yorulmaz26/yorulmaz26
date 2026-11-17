package com.kuaforumcepte.app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Marka paleti (master prompt §2). */
object Brand {
    val Navy = Color(0xFF14243A)
    val NavyDeep = Color(0xFF0B1626)
    val NavySurface = Color(0xFF1B2E48)
    val NavyHigh = Color(0xFF243A58)
    val Gold = Color(0xFFC59A64)
    val GoldLight = Color(0xFFE3C9A5)
    val GoldDark = Color(0xFF8E6A3C)
    val OffWhite = Color(0xFFF7F5F1)
    val Paper = Color(0xFFFFFFFF)
    val Sand = Color(0xFFEDE8E0)
    val Green = Color(0xFF20877B)
    val Red = Color(0xFFD45B5B)
    val Ink = Color(0xFF1A1D23)
    val InkMuted = Color(0xFF5E6672)
    val Amber = Color(0xFFB7791F)
}

/** Material şemasında olmayan anlamsal renkler (durum etiketleri vb.). */
@Immutable
data class KcExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val cancel: Color,
    val onCancel: Color,
    val cancelContainer: Color,
    val pending: Color,
    val pendingContainer: Color,
    val accent: Color,
    val skeleton: Color,
)

val LightExtended = KcExtendedColors(
    success = Brand.Green,
    onSuccess = Color.White,
    successContainer = Color(0xFFD7EEEA),
    cancel = Brand.Red,
    onCancel = Color.White,
    cancelContainer = Color(0xFFF8E0E0),
    pending = Brand.Amber,
    pendingContainer = Color(0xFFF6EBD9),
    accent = Brand.Gold,
    skeleton = Color(0xFFE6E1D8),
)

val DarkExtended = KcExtendedColors(
    success = Color(0xFF4FB8AA),
    onSuccess = Brand.NavyDeep,
    successContainer = Color(0xFF173F3C),
    cancel = Color(0xFFE88A8A),
    onCancel = Brand.NavyDeep,
    cancelContainer = Color(0xFF4A2326),
    pending = Color(0xFFE3B66B),
    pendingContainer = Color(0xFF473A22),
    accent = Brand.Gold,
    skeleton = Color(0xFF223450),
)

val LocalKcColors = staticCompositionLocalOf { LightExtended }
