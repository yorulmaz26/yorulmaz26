package com.kuaforumcepte.app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Marka paleti: sıcak siyah/espresso zemin + altın vurgu (kuaför/güzellik salonu konsepti).
 * Eski lacivert tonlar kaldırıldı; adlar geriye dönük uyumluluk için korunanlar da sıcak tonlara eşlendi.
 */
object Brand {
    // Koyu, sıcak nötrler
    val Espresso = Color(0xFF2B2420)
    val EspressoDeep = Color(0xFF131110)
    val Charcoal = Color(0xFF1C1917)
    val CharcoalHigh = Color(0xFF26221F)
    val CharcoalHighest = Color(0xFF312C28)
    // Altın vurgu
    val Gold = Color(0xFFC9A06A)
    val GoldLight = Color(0xFFE6CFA8)
    val GoldDark = Color(0xFF8E6A3C)
    val Champagne = Color(0xFFF3E7D5)
    // Açık nötrler
    val OffWhite = Color(0xFFFAF7F2)
    val Paper = Color(0xFFFFFFFF)
    val Sand = Color(0xFFEFE8DD)
    // Durum renkleri
    val Sage = Color(0xFF5B8A73)
    val Green = Color(0xFF2F8A6E)
    val Red = Color(0xFFC75252)
    val Ink = Color(0xFF1C1917)
    val InkMuted = Color(0xFF6B625A)
    val Amber = Color(0xFFB7791F)

    // Geriye dönük adlar (eski "Navy" kullanımları sıcak koyu tonlara yönlenir)
    val Navy = Espresso
    val NavyDeep = EspressoDeep
    val NavySurface = CharcoalHigh
    val NavyHigh = CharcoalHighest
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
    successContainer = Color(0xFFDDEFE6),
    cancel = Brand.Red,
    onCancel = Color.White,
    cancelContainer = Color(0xFFF8E0E0),
    pending = Brand.Amber,
    pendingContainer = Color(0xFFF6EBD9),
    accent = Brand.Gold,
    skeleton = Color(0xFFE9E2D7),
)

val DarkExtended = KcExtendedColors(
    success = Color(0xFF6CC3A0),
    onSuccess = Brand.EspressoDeep,
    successContainer = Color(0xFF1D3329),
    cancel = Color(0xFFE88A8A),
    onCancel = Brand.EspressoDeep,
    cancelContainer = Color(0xFF3F2222),
    pending = Color(0xFFE3B66B),
    pendingContainer = Color(0xFF3A3020),
    accent = Brand.Gold,
    skeleton = Color(0xFF2A2522),
)

val LocalKcColors = staticCompositionLocalOf { LightExtended }
