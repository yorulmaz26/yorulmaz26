package com.yanlisrenk.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.yanlisrenk.game.ui.Pal
import com.yanlisrenk.logic.level.AreaInfo
import com.yanlisrenk.logic.level.WorldInfo
import kotlin.math.cos
import kotlin.math.sin

/** Alanın görsel teması. */
class Theme(val area: AreaInfo, val world: WorldInfo) {
    val skyTop: Color = Pal.hex(area.skyTop)
    val skyBottom: Color = Pal.hex(area.skyBottom)
    val path: Color = Pal.hex(area.path)
    val a1: Color = Pal.hex(area.accent1)
    val a2: Color = Pal.hex(area.accent2)
    val a3: Color = Pal.hex(area.accent3)
    val deco: String = world.deco
    val variant: Int = area.variant
    val accents = arrayOf(a1, a2, a3)
}

/**
 * Parallax arka planlar. Her dünyanın kendi süsleme ailesi vardır; aynı dünyadaki
 * alanlar varyant numarasına göre farklı yoğunluk, boyut ve renk alır.
 * Katmanlar kameradan farklı hızlarda kayar (uzak 0.25, orta 0.55, yakın 1.0).
 */
object Backgrounds {
    private val tmp = Color()

    private fun hash(i: Int, salt: Int): Float {
        var h = i * 374761393 + salt * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFFFF) / 16777215f
    }

    fun drawSky(sr: ShapeRenderer, th: Theme, left: Float, bottom: Float, w: Float, h: Float) {
        sr.rect(left, bottom, w, h, th.skyBottom, th.skyBottom, th.skyTop, th.skyTop)
    }

    /**
     * @param camY kamera merkezi
     * @param sideOnly yakın katman yalnızca yol kenarlarına çizilir
     */
    fun drawLayers(sr: ShapeRenderer, th: Theme, left: Float, bottom: Float, w: Float, h: Float, camY: Float, t: Float, reduced: Boolean) {
        layer(sr, th, 0, 0.25f, 5.5f, left, bottom, w, h, camY, t, reduced)
        layer(sr, th, 1, 0.55f, 3.6f, left, bottom, w, h, camY, t, reduced)
    }

    fun drawNear(sr: ShapeRenderer, th: Theme, left: Float, bottom: Float, w: Float, h: Float, camY: Float, t: Float, reduced: Boolean) {
        layer(sr, th, 2, 1f, 2.4f, left, bottom, w, h, camY, t, reduced)
    }

    private fun layer(sr: ShapeRenderer, th: Theme, idx: Int, f: Float, spacing0: Float, left: Float, bottom: Float, w: Float, h: Float, camY: Float, t: Float, reduced: Boolean) {
        val spacing = spacing0 * (1f - th.variant * 0.06f)
        val shift = camY * (1f - f)
        val from = ((bottom - shift) / spacing).toInt() - 2
        val to = ((bottom + h - shift) / spacing).toInt() + 2
        val anim = if (reduced) 0f else t
        for (i in from..to) {
            val r1 = hash(i, idx * 17 + th.area.id)
            val r2 = hash(i, idx * 31 + 5)
            val r3 = hash(i, idx * 47 + 11)
            val y = i * spacing + shift + r2 * spacing * 0.5f
            val x = when (idx) {
                2 -> if (r1 < 0.5f) left + 0.15f + r3 * 0.9f else left + w - 0.15f - r3 * 0.9f
                else -> left + r1 * w
            }
            val scale = when (idx) { 0 -> 0.5f + r3 * 0.4f; 1 -> 0.7f + r3 * 0.4f; else -> 0.8f + r3 * 0.35f } * (1f + th.variant * 0.05f)
            val alpha = when (idx) { 0 -> 0.45f; 1 -> 0.7f; else -> 1f }
            val col = th.accents[(i + idx + th.variant).mod(3)]
            item(sr, th.deco, idx, x, y, scale, r1, r2, r3, col, alpha, anim, th)
        }
    }

    private fun c(col: Color, a: Float) = tmp.set(col.r, col.g, col.b, a)
    private fun wa(a: Float) = tmp.set(1f, 1f, 1f, a)

    private fun item(sr: ShapeRenderer, deco: String, layer: Int, x: Float, y: Float, s: Float, r1: Float, r2: Float, r3: Float, col: Color, a: Float, t: Float, th: Theme) {
        when (deco) {
            "garden", "forest" -> {
                if (layer == 0) { sr.color = c(th.a1, a * 0.6f); sr.circle(x, y, 1.4f * s, 24); sr.circle(x + 1f * s, y - 0.2f, 1.1f * s, 20) }
                else if (deco == "forest" && r3 > 0.5f) tree(sr, x, y, s, col, a, t)
                else flower(sr, x, y, s, col, a, t, r1)
            }
            "candy" -> when ((r3 * 3).toInt()) {
                0 -> { sr.color = c(Pal.WHITE, a); sr.rect(x - 0.05f * s, y - 1f * s, 0.1f * s, 1f * s); sr.color = c(col, a); sr.circle(x, y + 0.1f * s, 0.45f * s, 20); sr.color = wa(a * 0.7f); sr.circle(x, y + 0.1f * s, 0.25f * s, 16); sr.color = c(col, a); sr.circle(x, y + 0.1f * s, 0.12f * s, 12) }
                1 -> { sr.color = c(col, a); Shapes.roundRect(sr, x - 0.5f * s, y - 0.2f * s, 1f * s, 0.4f * s, 0.2f * s); sr.color = wa(a * 0.6f); sr.circle(x - 0.2f * s, y + 0.05f * s, 0.06f * s, 8); sr.circle(x + 0.15f * s, y - 0.05f * s, 0.06f * s, 8) }
                else -> { sr.color = c(Pal.PINK, a); sr.circle(x, y, 0.35f * s, 18); sr.color = c(col, a); sr.triangle(x - 0.35f * s, y, x - 0.7f * s, y + 0.25f * s, x - 0.7f * s, y - 0.25f * s); sr.triangle(x + 0.35f * s, y, x + 0.7f * s, y + 0.25f * s, x + 0.7f * s, y - 0.25f * s) }
            }
            "clouds", "sky" -> cloud(sr, x + sin(t * 0.3f + r1 * 6f) * 0.2f, y, s * (if (layer == 0) 1.4f else 1f), a * 0.95f)
            "ice" -> if (layer == 0) { sr.color = c(Pal.WHITE, a * 0.8f); sr.triangle(x - 1.5f * s, y - 1f, x + 1.5f * s, y - 1f, x, y + 1.6f * s); sr.color = c(col, a * 0.5f); sr.triangle(x - 0.5f * s, y + 0.5f * s, x + 0.5f * s, y + 0.5f * s, x, y + 1.6f * s) }
                else snowflake(sr, x, y, s * 0.45f, a, t + r1 * 5f)
            "crystal", "prism" -> crystal(sr, x, y, s, col, a, deco == "prism", t, r1)
            "desert" -> if (layer == 0) { sr.color = c(th.a3, a * 0.5f); sr.ellipse(x - 2f * s, y - 0.6f * s, 4f * s, 1.2f * s, 24) } else if (r3 > 0.6f) cactus(sr, x, y, s, a) else { sr.color = c(th.a1, a * 0.5f); sr.ellipse(x - 0.6f * s, y - 0.15f * s, 1.2f * s, 0.3f * s, 14) }
            "toys" -> { sr.color = c(col, a); Shapes.roundRect(sr, x - 0.35f * s, y - 0.35f * s, 0.7f * s, 0.7f * s, 0.12f * s); sr.color = wa(a * 0.6f); sr.circle(x - 0.12f * s, y + 0.38f * s, 0.12f * s, 10); sr.circle(x + 0.12f * s, y + 0.38f * s, 0.12f * s, 10) }
            "ocean" -> if (r3 > 0.55f && layer > 0) fish(sr, x + sin(t * 0.8f + r1 * 9f) * 0.4f, y, s, col, a) else bubble(sr, x, y + (t * 0.4f + r2 * 3f) % 3f, s * 0.4f, a)
            "rainbow" -> if (layer == 0) { for (k in 0 until 4) { sr.color = c(Pal.game[k], a * 0.55f); sr.arc(x, y, (1.6f - k * 0.18f) * s, 0f, 180f, 28) }; sr.color = c(th.skyBottom, 1f); sr.arc(x, y, 0.85f * s, 0f, 180f, 28) }
                else { sr.color = c(th.a2, a); sr.ellipse(x - 0.8f * s, y - 0.25f * s, 1.6f * s, 0.5f * s, 18); sr.color = c(Pal.GREEN, a); sr.circle(x, y + 0.15f * s, 0.35f * s, 14) }
            "space", "stars", "universe" -> when {
                layer == 0 && r3 > 0.6f -> planet(sr, x, y, s, col, a)
                deco == "universe" && r3 > 0.75f -> galaxy(sr, x, y, s, a, t)
                else -> { sr.color = c(if (r1 > 0.5f) Pal.YELLOW else Pal.WHITE, a * (0.6f + 0.4f * sin(t * 2f + r2 * 9f))); Shapes.star(sr, x, y, 0.18f * s, 0.07f * s, 4) }
            }
            "balloons" -> balloon(sr, x, y + sin(t + r1 * 6f) * 0.15f, s, col, a)
            "clocks", "factory" -> gear(sr, x, y, s * (if (layer == 0) 1.3f else 0.7f), c(col, a * (if (layer == 0) 0.4f else 0.85f)).cpy(), t * (if (r1 > 0.5f) 0.6f else -0.6f))
            "library" -> books(sr, x, y, s, a, r1)
            "robots" -> if (layer == 0) { sr.color = c(th.a1, a * 0.45f); sr.rect(x - 0.6f * s, y - 2f, 1.2f * s, 2.5f * s + r2 * 1.5f); sr.color = c(Pal.YELLOW, a * 0.6f); for (k in 0 until 3) sr.rect(x - 0.4f * s, y + k * 0.5f * s, 0.25f * s, 0.25f * s) } else bolt(sr, x, y, s, col, a, t)
            "carnival" -> if (layer == 0) tent(sr, x, y, s, col, a) else confetti(sr, x, y + (t * 0.6f + r2 * 4f) % 4f - 2f, s, col, a, t + r1 * 7f)
            else -> { sr.color = c(col, a); sr.circle(x, y, 0.3f * s, 12) }
        }
    }

    private fun flower(sr: ShapeRenderer, x: Float, y: Float, s: Float, col: Color, a: Float, t: Float, r: Float) {
        sr.color = c(Pal.GREEN, a); sr.rect(x - 0.04f * s, y - 0.8f * s, 0.08f * s, 0.8f * s)
        sr.ellipse(x, y - 0.5f * s, 0.3f * s, 0.12f * s, 10)
        val sway = sin(t * 1.5f + r * 6f) * 0.05f
        sr.color = c(col, a)
        for (k in 0 until 5) { val an = k * 72f * MathUtils.degRad + sway; sr.circle(x + cos(an) * 0.2f * s, y + sin(an) * 0.2f * s, 0.16f * s, 12) }
        sr.color = c(Pal.YELLOW, a); sr.circle(x, y, 0.12f * s, 10)
    }

    private fun tree(sr: ShapeRenderer, x: Float, y: Float, s: Float, col: Color, a: Float, t: Float) {
        sr.color = c(Pal.hex(0xC98B5A), a); sr.rect(x - 0.1f * s, y - 0.9f * s, 0.2f * s, 0.8f * s)
        sr.color = c(Pal.hex(0x58C46A), a); sr.circle(x, y, 0.55f * s, 18); sr.circle(x - 0.35f * s, y - 0.2f * s, 0.38f * s, 16); sr.circle(x + 0.35f * s, y - 0.2f * s, 0.38f * s, 16)
        sr.color = c(col, a); sr.circle(x - 0.2f * s, y + 0.15f * s, 0.08f * s, 8); sr.circle(x + 0.25f * s, y - 0.1f * s, 0.08f * s, 8)
    }

    private fun cloud(sr: ShapeRenderer, x: Float, y: Float, s: Float, a: Float) {
        sr.color = wa(a)
        sr.ellipse(x - 0.9f * s, y - 0.3f * s, 1.8f * s, 0.6f * s, 20)
        sr.circle(x - 0.35f * s, y + 0.05f * s, 0.4f * s, 18); sr.circle(x + 0.25f * s, y + 0.15f * s, 0.5f * s, 18)
    }

    private fun snowflake(sr: ShapeRenderer, x: Float, y: Float, s: Float, a: Float, t: Float) {
        sr.color = wa(a)
        for (k in 0 until 3) { val an = k * 60f * MathUtils.degRad + t * 0.3f; sr.rectLine(x - cos(an) * s, y - sin(an) * s, x + cos(an) * s, y + sin(an) * s, 0.07f) }
        sr.circle(x, y, 0.1f, 8)
    }

    private fun crystal(sr: ShapeRenderer, x: Float, y: Float, s: Float, col: Color, a: Float, prism: Boolean, t: Float, r: Float) {
        val glow = 0.75f + 0.25f * sin(t * 2f + r * 8f)
        sr.color = c(col, a * glow)
        if (prism) { sr.triangle(x - 0.5f * s, y - 0.4f * s, x + 0.5f * s, y - 0.4f * s, x, y + 0.5f * s); sr.color = wa(a * 0.5f); sr.triangle(x - 0.1f * s, y - 0.3f * s, x + 0.25f * s, y - 0.3f * s, x, y + 0.35f * s) }
        else {
            sr.triangle(x - 0.25f * s, y - 0.5f * s, x + 0.25f * s, y - 0.5f * s, x, y + 0.7f * s)
            sr.triangle(x + 0.1f * s, y - 0.5f * s, x + 0.5f * s, y - 0.5f * s, x + 0.35f * s, y + 0.25f * s)
            sr.color = wa(a * 0.45f); sr.triangle(x - 0.05f * s, y - 0.3f * s, x + 0.1f * s, y - 0.3f * s, x, y + 0.45f * s)
        }
    }

    private fun cactus(sr: ShapeRenderer, x: Float, y: Float, s: Float, a: Float) {
        sr.color = c(Pal.hex(0x6CCB8A), a)
        Shapes.roundRect(sr, x - 0.15f * s, y - 0.8f * s, 0.3f * s, 1.2f * s, 0.15f * s)
        Shapes.roundRect(sr, x - 0.45f * s, y - 0.2f * s, 0.2f * s, 0.5f * s, 0.1f * s)
        Shapes.roundRect(sr, x + 0.25f * s, y - 0.4f * s, 0.2f * s, 0.55f * s, 0.1f * s)
        sr.color = c(Pal.PINK, a); sr.circle(x, y + 0.42f * s, 0.1f * s, 8)
    }

    private fun fish(sr: ShapeRenderer, x: Float, y: Float, s: Float, col: Color, a: Float) {
        sr.color = c(col, a); sr.ellipse(x - 0.35f * s, y - 0.18f * s, 0.7f * s, 0.36f * s, 16)
        sr.triangle(x + 0.3f * s, y, x + 0.55f * s, y + 0.2f * s, x + 0.55f * s, y - 0.2f * s)
        sr.color = wa(a); sr.circle(x - 0.18f * s, y + 0.04f * s, 0.06f * s, 8)
    }

    private fun bubble(sr: ShapeRenderer, x: Float, y: Float, s: Float, a: Float) {
        sr.color = wa(a * 0.5f); sr.circle(x, y, s, 16)
        sr.color = wa(a * 0.9f); sr.circle(x - s * 0.35f, y + s * 0.35f, s * 0.22f, 8)
    }

    private fun planet(sr: ShapeRenderer, x: Float, y: Float, s: Float, col: Color, a: Float) {
        sr.color = c(col, a); sr.circle(x, y, 0.9f * s, 28)
        sr.color = wa(a * 0.35f); sr.ellipse(x - 1.4f * s, y - 0.15f * s, 2.8f * s, 0.3f * s, 24)
        sr.color = c(Pal.lighter(col, 0.4f), a); sr.circle(x - 0.3f * s, y + 0.3f * s, 0.2f * s, 12)
    }

    private fun galaxy(sr: ShapeRenderer, x: Float, y: Float, s: Float, a: Float, t: Float) {
        for (k in 0 until 18) {
            val an = k * 0.55f + t * 0.2f
            val r = k * 0.05f * s
            sr.color = c(Pal.game[k % 4], a * 0.6f)
            sr.circle(x + cos(an) * r, y + sin(an) * r, 0.07f * s, 8)
        }
    }

    private fun balloon(sr: ShapeRenderer, x: Float, y: Float, s: Float, col: Color, a: Float) {
        sr.color = c(Pal.TEXT_SOFT, a * 0.5f); sr.rectLine(x, y - 0.4f * s, x + 0.05f, y - 1.1f * s, 0.03f)
        sr.color = c(col, a); sr.ellipse(x - 0.32f * s, y - 0.4f * s, 0.64f * s, 0.8f * s, 20)
        sr.triangle(x - 0.06f * s, y - 0.45f * s, x + 0.06f * s, y - 0.45f * s, x, y - 0.38f * s)
        sr.color = wa(a * 0.6f); sr.ellipse(x - 0.2f * s, y + 0.05f * s, 0.14f * s, 0.2f * s, 10)
    }

    private fun gear(sr: ShapeRenderer, x: Float, y: Float, s: Float, col: Color, rot: Float) {
        sr.color = col
        sr.circle(x, y, 0.45f * s, 24)
        for (k in 0 until 8) { val an = k * 45f * MathUtils.degRad + rot; sr.rectLine(x + cos(an) * 0.4f * s, y + sin(an) * 0.4f * s, x + cos(an) * 0.62f * s, y + sin(an) * 0.62f * s, 0.16f * s) }
        sr.color = tmp.set(1f, 1f, 1f, col.a * 0.8f); sr.circle(x, y, 0.16f * s, 14)
    }

    private fun books(sr: ShapeRenderer, x: Float, y: Float, s: Float, a: Float, r: Float) {
        var bx = x - 0.5f * s
        for (k in 0 until 4) {
            val bw = (0.18f + 0.08f * ((k + (r * 10).toInt()) % 3)) * s
            val bh = (0.7f + 0.2f * ((k * 7 + (r * 10).toInt()) % 3)) * s
            sr.color = c(Pal.game[(k + (r * 4).toInt()) % 4], a * 0.85f)
            sr.rect(bx, y - 0.5f * s, bw, bh)
            sr.color = wa(a * 0.6f); sr.rect(bx, y - 0.5f * s + bh * 0.7f, bw, 0.05f * s)
            bx += bw + 0.03f * s
        }
    }

    private fun bolt(sr: ShapeRenderer, x: Float, y: Float, s: Float, col: Color, a: Float, t: Float) {
        sr.color = c(Pal.hex(0xC8D3E0), a); Shapes.polygon(sr, x, y, 0.3f * s, 6, t * 20f)
        sr.color = c(col, a); sr.circle(x, y, 0.13f * s, 10)
    }

    private fun tent(sr: ShapeRenderer, x: Float, y: Float, s: Float, col: Color, a: Float) {
        for (k in 0 until 4) { sr.color = c(if (k % 2 == 0) col else Pal.WHITE, a * 0.7f); sr.triangle(x - 1.2f * s + k * 0.6f * s, y - 0.8f * s, x - 0.6f * s + k * 0.6f * s, y - 0.8f * s, x, y + 0.9f * s) }
        sr.color = c(Pal.YELLOW, a); Shapes.star(sr, x, y + 1.05f * s, 0.15f * s, 0.06f * s)
    }

    private fun confetti(sr: ShapeRenderer, x: Float, y: Float, s: Float, col: Color, a: Float, t: Float) {
        sr.color = c(col, a)
        val w = 0.18f * s * kotlin.math.abs(cos(t * 3f)) + 0.03f
        sr.rect(x - w / 2, y - 0.08f * s, w, 0.16f * s)
    }
}
