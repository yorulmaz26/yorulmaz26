package com.zamaninefendisi.game.ui

import com.badlogic.gdx.graphics.Color
import com.zamaninefendisi.logic.Era

/** Açık, canlı ve fantastik renk paleti. Koyu tema kullanılmaz. */
object Theme {
    private fun c(hex: String): Color = Color.valueOf(hex)

    val cream = c("FFF6E9")
    val creamDeep = c("FCE9C8")
    val ink = c("3B3355")
    val inkSoft = c("6B6485")
    val muted = c("A39FB5")
    val panel = c("FFFFFF")
    val panelWarm = c("FFF3DC")
    val orange = c("FF8A3D")
    val blue = c("4FB3E8")
    val green = c("5CC46A")
    val gold = c("F5B83D")
    val red = c("EC5F5F")
    val purple = c("8E6CE0")
    val pink = c("F27BA8")
    val teal = c("2EC4B6")
    val grey = c("C9C5D6")
    val dim = Color(0.23f, 0.2f, 0.33f, 0.45f)

    fun eraColor(e: Era): Color = when (e) {
        Era.PAST -> c("5DBB63")
        Era.PRESENT -> c("F2A33A")
        Era.FUTURE -> c("3FB2E6")
    }

    fun skyTop(e: Era): Color = when (e) {
        Era.PAST -> c("FFF1C9")
        Era.PRESENT -> c("D9EEFF")
        Era.FUTURE -> c("EDE6FF")
    }

    fun skyBottom(e: Era): Color = when (e) {
        Era.PAST -> c("E4F6D2")
        Era.PRESENT -> c("F6FBFF")
        Era.FUTURE -> c("DDFBFF")
    }

    fun darker(col: Color, f: Float = 0.82f) = Color(col.r * f, col.g * f, col.b * f, col.a)
    fun lighter(col: Color, f: Float = 0.25f) = Color(col.r + (1 - col.r) * f, col.g + (1 - col.g) * f, col.b + (1 - col.b) * f, col.a)
}
