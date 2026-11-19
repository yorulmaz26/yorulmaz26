package com.yuruyensehir.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2

/** İzometrik izdüşüm: ızgara (gx, gy) -> dünya (x, y). +gx ekranda sağ-yukarı (taşıyıcının önü). */
object Iso {
    const val HW = 30f
    const val HH = 15f

    fun x(gx: Float, gy: Float) = (gx - gy) * HW
    fun y(gx: Float, gy: Float) = (gx + gy) * HH

    fun toGrid(wx: Float, wy: Float, out: Vector2): Vector2 {
        val a = wx / HW
        val b = wy / HH
        return out.set((a + b) / 2f, (b - a) / 2f)
    }
}

/** ShapeRenderer için küçük çizim yardımcıları (hepsi dolu şekil modunda). */
object Draw {
    fun quad(sr: ShapeRenderer, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float) {
        sr.triangle(x1, y1, x2, y2, x3, y3)
        sr.triangle(x1, y1, x3, y3, x4, y4)
    }

    fun ellipseFan(sr: ShapeRenderer, cx: Float, cy: Float, rx: Float, ry: Float, segments: Int = 28) {
        var px = cx + rx
        var py = cy
        for (i in 1..segments) {
            val a = (i.toFloat() / segments) * Math.PI.toFloat() * 2f
            val nx = cx + rx * kotlin.math.cos(a)
            val ny = cy + ry * kotlin.math.sin(a)
            sr.triangle(cx, cy, px, py, nx, ny)
            px = nx; py = ny
        }
    }

    /** Dikey renk geçişli dörtgen: (x1,y1)-(x2,y2) alt kenar [bottom], (x3,y3)-(x4,y4) üst kenar [top]. */
    fun quadGradient(sr: ShapeRenderer, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float, bottom: Color, top: Color) {
        sr.triangle(x1, y1, x2, y2, x3, y3, bottom, bottom, top)
        sr.triangle(x1, y1, x3, y3, x4, y4, bottom, top, top)
    }

    fun thickLine(sr: ShapeRenderer, x1: Float, y1: Float, x2: Float, y2: Float, w: Float) = sr.rectLine(x1, y1, x2, y2, w)

    fun shade(c: Color, f: Float, out: Color = Color()): Color = out.set(c.r * f, c.g * f, c.b * f, c.a)
}
