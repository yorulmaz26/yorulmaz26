package com.emirzinciri.robotkomutan.ui

import com.badlogic.gdx.graphics.Color
import com.emirzinciri.robotkomutan.logic.command.CommandCategory

/** Açık, renkli tema paleti. Koyu tema kullanılmaz. */
object Theme {
    val sky = rgb(0xEAF6FF)
    val skyDeep = rgb(0xD3ECFB)
    val white = rgb(0xFFFFFF)
    val turquoise = rgb(0x24B8B0)
    val orange = rgb(0xFF9B42)
    val green = rgb(0x62C78A)
    val yellow = rgb(0xFFD66B)
    val purple = rgb(0x9277E8)
    val ink = rgb(0x25324A)
    val inkSoft = rgb(0x5B6780)
    val inkFaint = rgb(0x9AA6BD)
    val red = rgb(0xF0606E)
    val blue = rgb(0x4FA3E3)
    val pink = rgb(0xF07A8C)
    val panel = rgb(0xFFFFFF)
    val panelAlt = rgb(0xF4F8FC)
    val line = rgb(0xDCE6F2)
    val shadow = Color(0.15f, 0.2f, 0.32f, 0.16f)

    // Harita
    val floorTop = rgb(0xF7FBFF)
    val floorTopAlt = rgb(0xEDF4FB)
    val floorSide = rgb(0xC9D9EA)
    val wallTop = rgb(0xA9BCD6)
    val wallSide = rgb(0x8296B6)
    val exitColor = rgb(0x62C78A)
    val targetColor = rgb(0xFFD66B)
    val plateColor = rgb(0xFF9B42)
    val doorColor = rgb(0x4FA3E3)
    val doorInvColor = rgb(0x9277E8)
    val boxColor = rgb(0xE8A15E)
    val energyColor = rgb(0xFFC93C)
    val pitColor = rgb(0x8C9BB4)

    val robotColors = arrayOf(rgb(0x24B8B0), rgb(0xFF9B42), rgb(0x9277E8), rgb(0x4FA3E3), rgb(0xF07A8C), rgb(0x62C78A))

    fun category(c: CommandCategory): Color = when (c) {
        CommandCategory.MOVE -> turquoise
        CommandCategory.ACTION -> orange
        CommandCategory.ENERGY -> rgb(0xF2B630)
        CommandCategory.SIGNAL -> blue
        CommandCategory.FLOW -> purple
    }

    fun rgb(hex: Int): Color = Color(((hex shr 16) and 0xFF) / 255f, ((hex shr 8) and 0xFF) / 255f, (hex and 0xFF) / 255f, 1f)

    fun shade(c: Color, f: Float): Color = Color(c.r * f, c.g * f, c.b * f, c.a)
    fun mix(a: Color, b: Color, t: Float): Color = Color(a).lerp(b, t)
}
