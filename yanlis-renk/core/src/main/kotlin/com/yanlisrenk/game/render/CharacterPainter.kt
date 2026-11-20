package com.yanlisrenk.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.yanlisrenk.game.ui.Pal
import com.yanlisrenk.logic.shop.Body
import com.yanlisrenk.logic.shop.Eyes
import com.yanlisrenk.logic.shop.Feature
import com.yanlisrenk.logic.shop.Idle
import com.yanlisrenk.logic.shop.ShopItem
import kotlin.math.cos
import kotlin.math.sin

/** Yardımcı vektör şekiller (ShapeRenderer, Filled modunda). */
object Shapes {
    fun roundRect(sr: ShapeRenderer, x: Float, y: Float, w: Float, h: Float, r0: Float) {
        val r = minOf(r0, w / 2, h / 2)
        sr.rect(x + r, y, w - 2 * r, h)
        sr.rect(x, y + r, r, h - 2 * r)
        sr.rect(x + w - r, y + r, r, h - 2 * r)
        val seg = 14
        sr.circle(x + r, y + r, r, seg); sr.circle(x + w - r, y + r, r, seg)
        sr.circle(x + r, y + h - r, r, seg); sr.circle(x + w - r, y + h - r, r, seg)
    }

    fun star(sr: ShapeRenderer, cx: Float, cy: Float, rOut: Float, rIn: Float, points: Int = 5, rot: Float = 90f) {
        val n = points * 2
        for (i in 0 until n) {
            val a1 = (rot + i * 360f / n) * MathUtils.degRad
            val a2 = (rot + (i + 1) * 360f / n) * MathUtils.degRad
            val r1 = if (i % 2 == 0) rOut else rIn
            val r2 = if (i % 2 == 0) rIn else rOut
            sr.triangle(cx, cy, cx + cos(a1) * r1, cy + sin(a1) * r1, cx + cos(a2) * r2, cy + sin(a2) * r2)
        }
    }

    fun polygon(sr: ShapeRenderer, cx: Float, cy: Float, r: Float, sides: Int, rot: Float = 90f) {
        for (i in 0 until sides) {
            val a1 = (rot + i * 360f / sides) * MathUtils.degRad
            val a2 = (rot + (i + 1) * 360f / sides) * MathUtils.degRad
            sr.triangle(cx, cy, cx + cos(a1) * r, cy + sin(a1) * r, cx + cos(a2) * r, cy + sin(a2) * r)
        }
    }

    fun heart(sr: ShapeRenderer, cx: Float, cy: Float, s: Float) {
        val r = s * 0.27f
        sr.circle(cx - r * 0.95f, cy + r * 0.55f, r, 18)
        sr.circle(cx + r * 0.95f, cy + r * 0.55f, r, 18)
        sr.triangle(cx - r * 1.9f, cy + r * 0.35f, cx + r * 1.9f, cy + r * 0.35f, cx, cy - s * 0.48f)
    }

    fun smile(sr: ShapeRenderer, cx: Float, cy: Float, r: Float, dark: Color, body: Color) {
        sr.color = dark; sr.arc(cx, cy, r, 180f, 180f, 14)
        sr.color = body; sr.arc(cx, cy + r * 0.38f, r * 1.02f, 180f, 180f, 14)
    }

    /** Kalın çizgi (dikdörtgen) */
    fun line(sr: ShapeRenderer, x1: Float, y1: Float, x2: Float, y2: Float, w: Float) = sr.rectLine(x1, y1, x2, y2, w)
}

/**
 * Karakter çizimi. Gövde rengi oyuncunun o anki oyun rengidir; özellikler (kulak, anten vb.)
 * karakter verisinden gelir. [size] karakterin genişliğidir.
 */
object CharacterPainter {
    private val dark = Pal.hex(0x253858)
    private val white = Color(1f, 1f, 1f, 1f)
    private val cheek = Pal.hex(0xFF8FA8, 0.7f)
    private val tmp = Color()

    /** Karakterin görsel hâlini sağlar (Assets tarafından ayarlanır). */
    var spriteFor: (String) -> com.badlogic.gdx.graphics.g2d.TextureRegion? = { null }

    /**
     * Görseli varsa görseli (oyun rengine boyanmış), yoksa vektör çizimi kullanır.
     * Çağrıldığında [sr] Filled modunda açık olmalıdır; dönüşte yine açıktır.
     */
    fun drawAny(sr: ShapeRenderer, batch: com.badlogic.gdx.graphics.g2d.SpriteBatch, item: ShopItem, costume: Int, bodyColor: Color,
                cx: Float, cy: Float, size: Float, t: Float, squash: Float = 0f, blink: Float = 0f, idleAnim: Boolean = true) {
        val reg = spriteFor(item.id)
        if (reg == null) { draw(sr, item, costume, bodyColor, cx, cy, size, t, squash, blink, idleAnim); return }
        val io = if (idleAnim) idleOffset(item, t) else floatArrayOf(0f, 0f, 1f, 1f)
        val sx = io[2] * (1f + squash * 0.16f)
        val sy = io[3] * (1f - squash * 0.16f)
        // gölge
        sr.color = tmp.set(0.15f, 0.22f, 0.35f, 0.18f * bodyColor.a)
        sr.ellipse(cx - size * 0.42f * sx, cy - size * 0.66f, size * 0.84f * sx, size * 0.18f, 20)
        sr.end()
        val dw = size * 1.45f * sx
        val dh = size * 1.45f * sy
        batch.projectionMatrix = sr.projectionMatrix
        batch.transformMatrix = sr.transformMatrix
        batch.begin()
        batch.color = bodyColor
        batch.draw(reg, cx - dw / 2, cy - size * 0.62f + io[0] * size, dw / 2, 0f, dw, dh, 1f, 1f, io[1])
        batch.color = Color.WHITE
        batch.end()
        batch.transformMatrix = com.badlogic.gdx.math.Matrix4()
        com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
        com.badlogic.gdx.Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA)
        sr.begin(ShapeRenderer.ShapeType.Filled)
        if (costume > 0) costume(sr, costume, cx, cy + io[0] * size + size * 0.08f, size * sx, size * sy, t)
    }

    fun idleOffset(item: ShopItem, t: Float): FloatArray {
        // [dy, rot, scaleX, scaleY]
        return when (item.idle) {
            Idle.BOUNCE -> floatArrayOf(kotlin.math.abs(sin(t * 4f)) * 0.08f, 0f, 1f, 1f)
            Idle.WOBBLE -> floatArrayOf(0f, sin(t * 3.5f) * 6f, 1f, 1f)
            Idle.SPIN -> floatArrayOf(sin(t * 2f) * 0.03f, sin(t * 1.5f) * 10f, 1f, 1f)
            Idle.FLOAT -> floatArrayOf(sin(t * 2.2f) * 0.07f, 0f, 1f, 1f)
            Idle.PULSE -> { val p = 1f + sin(t * 4f) * 0.035f; floatArrayOf(0f, 0f, p, 2f - p) }
        }
    }

    /**
     * @param squash 0..1 dokunuş anında basılma
     * @param blink göz kırpma (0..1)
     */
    fun draw(sr: ShapeRenderer, item: ShopItem, costume: Int, bodyColor: Color, cx: Float, cy: Float, size: Float, t: Float,
             squash: Float = 0f, blink: Float = 0f, idleAnim: Boolean = true) {
        val io = if (idleAnim) idleOffset(item, t) else floatArrayOf(0f, 0f, 1f, 1f)
        val sx = io[2] * (1f + squash * 0.18f)
        val sy = io[3] * (1f - squash * 0.18f)
        val s = size
        val y = cy + io[0] * s
        val w = s * sx
        val h = s * sy
        val body = bodyColor
        val light = Pal.lighter(body, 0.45f)
        val shade = Pal.darker(body, 0.85f)

        // gölge
        sr.color = tmp.set(0.15f, 0.22f, 0.35f, 0.18f)
        sr.ellipse(cx - w * 0.42f, cy - h * 0.62f, w * 0.84f, h * 0.18f, 20)

        // arka özellikler
        backFeatures(sr, item.feature, cx, y, w, h, body, t)

        // dış çizgi + gövde
        sr.color = dark
        bodyShape(sr, item.body, cx, y, w * 1.1f, h * 1.1f)
        sr.color = body
        bodyShape(sr, item.body, cx, y, w, h)
        // alt gölge ve parlama
        sr.color = tmp.set(shade.r, shade.g, shade.b, 0.55f)
        sr.ellipse(cx - w * 0.3f, y - h * 0.46f, w * 0.6f, h * 0.16f, 16)
        sr.color = tmp.set(light.r, light.g, light.b, 0.8f)
        sr.ellipse(cx - w * 0.32f, y + h * 0.18f, w * 0.26f, h * 0.16f, 14)

        frontFeatures(sr, item.feature, cx, y, w, h, body, t)
        face(sr, item, cx, y, w, h, body, blink)
        if (costume > 0) costume(sr, costume, cx, y, w, h, t)
    }

    private fun bodyShape(sr: ShapeRenderer, b: Body, cx: Float, cy: Float, w: Float, h: Float) {
        when (b) {
            Body.SQUARE -> Shapes.roundRect(sr, cx - w / 2, cy - h / 2, w, h, w * 0.24f)
            Body.CIRCLE -> sr.ellipse(cx - w / 2, cy - h / 2, w, h, 32)
            Body.TRIANGLE -> {
                val r = w * 0.14f
                sr.triangle(cx - w / 2 + r, cy - h / 2, cx + w / 2 - r, cy - h / 2, cx, cy + h / 2 - r)
                sr.triangle(cx - w / 2, cy - h / 2 + r, cx + w / 2, cy - h / 2 + r, cx, cy + h / 2)
                sr.circle(cx - w / 2 + r, cy - h / 2 + r, r, 12); sr.circle(cx + w / 2 - r, cy - h / 2 + r, r, 12)
                sr.rect(cx - w / 2 + r, cy - h / 2, w - 2 * r, r * 2)
            }
            Body.DIAMOND -> {
                sr.triangle(cx - w / 2, cy, cx + w / 2, cy, cx, cy + h / 2)
                sr.triangle(cx - w / 2, cy, cx + w / 2, cy, cx, cy - h / 2)
            }
            Body.STAR -> Shapes.star(sr, cx, cy - h * 0.04f, w * 0.58f, w * 0.3f)
            Body.HEART -> Shapes.heart(sr, cx, cy, w * 1.05f)
            Body.HEXAGON -> Shapes.polygon(sr, cx, cy, w * 0.56f, 6, 0f)
            Body.CLOUD -> {
                sr.ellipse(cx - w / 2, cy - h * 0.45f, w, h * 0.6f, 24)
                sr.circle(cx - w * 0.2f, cy + h * 0.05f, w * 0.3f, 20)
                sr.circle(cx + w * 0.16f, cy + h * 0.12f, w * 0.34f, 20)
            }
            Body.EGG -> sr.ellipse(cx - w * 0.45f, cy - h * 0.52f, w * 0.9f, h * 1.06f, 32)
            Body.GHOST -> {
                sr.circle(cx, cy + h * 0.08f, w * 0.45f, 28)
                sr.rect(cx - w * 0.45f, cy - h * 0.38f, w * 0.9f, h * 0.46f)
                for (i in 0 until 3) sr.circle(cx - w * 0.3f + i * w * 0.3f, cy - h * 0.38f, w * 0.15f, 12)
            }
            Body.DROP -> {
                sr.circle(cx, cy - h * 0.1f, w * 0.42f, 28)
                sr.triangle(cx - w * 0.38f, cy + h * 0.02f, cx + w * 0.38f, cy + h * 0.02f, cx, cy + h * 0.56f)
            }
            Body.TALL -> Shapes.roundRect(sr, cx - w * 0.36f, cy - h * 0.55f, w * 0.72f, h * 1.1f, w * 0.3f)
        }
    }

    private fun face(sr: ShapeRenderer, item: ShopItem, cx: Float, cy: Float, w: Float, h: Float, body: Color, blink: Float) {
        val ey = cy + h * 0.06f
        val ex = w * 0.19f
        val er = w * 0.12f
        val pr = er * 0.55f
        when (item.eyes) {
            Eyes.VISOR -> {
                sr.color = dark; Shapes.roundRect(sr, cx - w * 0.36f, ey - er * 0.8f, w * 0.72f, er * 1.7f, er * 0.8f)
                sr.color = Pal.hex(0x7FF0FF)
                val eh = er * 0.9f * (1f - blink)
                Shapes.roundRect(sr, cx - ex - er * 0.45f, ey - eh / 2, er * 0.9f, maxOf(eh, 0.01f), er * 0.4f)
                Shapes.roundRect(sr, cx + ex - er * 0.45f, ey - eh / 2, er * 0.9f, maxOf(eh, 0.01f), er * 0.4f)
            }
            Eyes.SLEEPY, Eyes.HAPPY -> {
                // ^ ^ biçimli kapalı/mutlu gözler
                sr.color = dark
                for (sgn in intArrayOf(-1, 1)) {
                    val x = cx + sgn * ex
                    if (item.eyes == Eyes.HAPPY) {
                        Shapes.line(sr, x - er * 0.8f, ey - er * 0.2f, x, ey + er * 0.5f, er * 0.4f)
                        Shapes.line(sr, x, ey + er * 0.5f, x + er * 0.8f, ey - er * 0.2f, er * 0.4f)
                    } else {
                        Shapes.line(sr, x - er * 0.8f, ey, x + er * 0.8f, ey, er * 0.35f)
                        sr.circle(x, ey, er * 0.25f, 8)
                    }
                }
            }
            Eyes.THREE -> {
                for (i in -1..1) eye(sr, cx + i * w * 0.24f, ey + (if (i == 0) h * 0.1f else 0f), er * 0.85f, pr * 0.85f, blink)
            }
            Eyes.BIG -> { eye(sr, cx - ex * 1.05f, ey, er * 1.35f, pr * 1.2f, blink); eye(sr, cx + ex * 1.05f, ey, er * 1.35f, pr * 1.2f, blink) }
            Eyes.ROUND -> { eye(sr, cx - ex, ey, er, pr, blink); eye(sr, cx + ex, ey, er, pr, blink) }
        }
        // yanaklar ve ağız
        sr.color = cheek
        sr.ellipse(cx - w * 0.36f, cy - h * 0.14f, w * 0.16f, h * 0.09f, 12)
        sr.ellipse(cx + w * 0.2f, cy - h * 0.14f, w * 0.16f, h * 0.09f, 12)
        Shapes.smile(sr, cx, cy - h * 0.1f, w * 0.1f, dark, body)
    }

    private fun eye(sr: ShapeRenderer, x: Float, y: Float, r: Float, pr: Float, blink: Float) {
        if (blink > 0.5f) { sr.color = dark; Shapes.line(sr, x - r, y, x + r, y, r * 0.35f); return }
        sr.color = white; sr.circle(x, y, r, 18)
        sr.color = dark; sr.circle(x + r * 0.12f, y - r * 0.05f, pr, 14)
        sr.color = white; sr.circle(x + r * 0.3f, y + r * 0.25f, pr * 0.38f, 8)
    }

    private fun backFeatures(sr: ShapeRenderer, f: Feature, cx: Float, cy: Float, w: Float, h: Float, body: Color, t: Float) {
        when (f) {
            Feature.CAT -> {
                for (sgn in intArrayOf(-1, 1)) {
                    sr.color = dark; sr.triangle(cx + sgn * w * 0.12f, cy + h * 0.3f, cx + sgn * w * 0.5f, cy + h * 0.25f, cx + sgn * w * 0.4f, cy + h * 0.72f)
                    sr.color = body; sr.triangle(cx + sgn * w * 0.16f, cy + h * 0.3f, cx + sgn * w * 0.45f, cy + h * 0.27f, cx + sgn * w * 0.38f, cy + h * 0.64f)
                }
            }
            Feature.BEAR, Feature.PANDA -> for (sgn in intArrayOf(-1, 1)) {
                sr.color = dark; sr.circle(cx + sgn * w * 0.36f, cy + h * 0.4f, w * 0.18f, 16)
                sr.color = if (f == Feature.PANDA) dark else body; sr.circle(cx + sgn * w * 0.36f, cy + h * 0.4f, w * 0.13f, 16)
            }
            Feature.BUNNY -> for (sgn in intArrayOf(-1, 1)) {
                sr.color = dark; sr.ellipse(cx + sgn * w * 0.2f - w * 0.12f, cy + h * 0.22f, w * 0.24f, h * 0.62f, 16)
                sr.color = body; sr.ellipse(cx + sgn * w * 0.2f - w * 0.08f, cy + h * 0.26f, w * 0.16f, h * 0.54f, 16)
                sr.color = Pal.PINK; sr.ellipse(cx + sgn * w * 0.2f - w * 0.04f, cy + h * 0.32f, w * 0.08f, h * 0.38f, 12)
            }
            Feature.ANTENNA, Feature.GOLD_TRIM, Feature.ALIEN -> {
                val n = if (f == Feature.ALIEN) 2 else 1
                for (i in 0 until n) {
                    val ax = if (n == 1) cx else cx + (if (i == 0) -1 else 1) * w * 0.22f
                    sr.color = dark; Shapes.line(sr, ax, cy + h * 0.45f, ax + sin(t * 3 + i) * w * 0.05f, cy + h * 0.82f, w * 0.06f)
                    sr.color = if (f == Feature.GOLD_TRIM) Pal.GOLD else Pal.YELLOW; sr.circle(ax + sin(t * 3 + i) * w * 0.05f, cy + h * 0.85f, w * 0.1f, 12)
                }
            }
            Feature.DRAGON -> {
                for (sgn in intArrayOf(-1, 1)) {
                    sr.color = Pal.hex(0x9BE7A8); sr.triangle(cx + sgn * w * 0.45f, cy + h * 0.1f, cx + sgn * w * 0.9f, cy + h * 0.35f + sin(t * 6) * h * 0.08f, cx + sgn * w * 0.5f, cy - h * 0.25f)
                    sr.color = Pal.YELLOW; sr.triangle(cx + sgn * w * 0.18f, cy + h * 0.45f, cx + sgn * w * 0.32f, cy + h * 0.46f, cx + sgn * w * 0.3f, cy + h * 0.75f)
                }
            }
            Feature.RAYS -> {
                sr.color = Pal.YELLOW
                for (i in 0 until 10) {
                    val a = (i * 36f + t * 30f) * MathUtils.degRad
                    sr.triangle(cx + cos(a - 0.15f) * w * 0.5f, cy + sin(a - 0.15f) * w * 0.5f, cx + cos(a + 0.15f) * w * 0.5f, cy + sin(a + 0.15f) * w * 0.5f, cx + cos(a) * w * 0.78f, cy + sin(a) * w * 0.78f)
                }
            }
            Feature.OCTO -> {
                sr.color = dark
                for (i in 0 until 5) { val x = cx - w * 0.36f + i * w * 0.18f; sr.ellipse(x - w * 0.08f, cy - h * 0.72f + sin(t * 5 + i) * h * 0.04f, w * 0.16f, h * 0.36f, 12) }
                sr.color = body
                for (i in 0 until 5) { val x = cx - w * 0.36f + i * w * 0.18f; sr.ellipse(x - w * 0.055f, cy - h * 0.68f + sin(t * 5 + i) * h * 0.04f, w * 0.11f, h * 0.3f, 12) }
            }
            Feature.SPARKLE, Feature.PRISM -> {
                sr.color = Pal.hex(0xFFFFFF, 0.85f)
                for (i in 0 until 3) {
                    val a = t * 1.5f + i * 2.1f
                    Shapes.star(sr, cx + cos(a) * w * 0.75f, cy + sin(a) * h * 0.65f, w * 0.1f, w * 0.035f, 4)
                }
            }
            else -> {}
        }
    }

    private fun frontFeatures(sr: ShapeRenderer, f: Feature, cx: Float, cy: Float, w: Float, h: Float, body: Color, t: Float) {
        when (f) {
            Feature.PANDA -> { sr.color = dark; sr.ellipse(cx - w * 0.33f, cy - h * 0.05f, w * 0.28f, h * 0.26f, 14); sr.ellipse(cx + w * 0.05f, cy - h * 0.05f, w * 0.28f, h * 0.26f, 14) }
            Feature.RAINBOW -> {
                for (i in 0 until 4) { sr.color = Pal.lighter(Pal.game[i], 0.15f); sr.rect(cx - w * 0.42f, cy - h * 0.42f + i * h * 0.07f, w * 0.84f, h * 0.07f) }
            }
            Feature.GOLD_TRIM -> { sr.color = Pal.GOLD; sr.rect(cx - w * 0.46f, cy - h * 0.34f, w * 0.92f, h * 0.06f) }
            Feature.BEE -> { sr.color = Pal.hex(0x253858, 0.75f); sr.rect(cx - w * 0.48f, cy - h * 0.3f, w * 0.96f, h * 0.08f); sr.rect(cx - w * 0.44f, cy - h * 0.44f, w * 0.88f, h * 0.07f) }
            Feature.PENGUIN -> { sr.color = white; sr.ellipse(cx - w * 0.3f, cy - h * 0.48f, w * 0.6f, h * 0.62f, 20); sr.color = Pal.ORANGE; sr.triangle(cx - w * 0.08f, cy - h * 0.06f, cx + w * 0.08f, cy - h * 0.06f, cx, cy - h * 0.18f) }
            Feature.FROG -> for (sgn in intArrayOf(-1, 1)) { sr.color = dark; sr.circle(cx + sgn * w * 0.22f, cy + h * 0.42f, w * 0.17f, 14); sr.color = body; sr.circle(cx + sgn * w * 0.22f, cy + h * 0.42f, w * 0.13f, 14) }
            Feature.CHICK -> { sr.color = Pal.ORANGE; sr.triangle(cx - w * 0.08f, cy - h * 0.04f, cx + w * 0.08f, cy - h * 0.04f, cx, cy - h * 0.16f); sr.color = body; sr.circle(cx, cy + h * 0.52f, w * 0.08f, 10); sr.circle(cx + w * 0.08f, cy + h * 0.5f, w * 0.07f, 10) }
            Feature.FROST -> { sr.color = Pal.hex(0xFFFFFF, 0.7f); Shapes.roundRect(sr, cx - w * 0.44f, cy + h * 0.32f, w * 0.88f, h * 0.14f, w * 0.06f); for (i in 0 until 4) sr.triangle(cx - w * 0.38f + i * w * 0.22f, cy + h * 0.33f, cx - w * 0.3f + i * w * 0.22f, cy + h * 0.33f, cx - w * 0.34f + i * w * 0.22f, cy + h * 0.2f) }
            Feature.CANDY -> for (sgn in intArrayOf(-1, 1)) { sr.color = Pal.PINK; sr.triangle(cx + sgn * w * 0.45f, cy, cx + sgn * w * 0.75f, cy + h * 0.2f, cx + sgn * w * 0.75f, cy - h * 0.2f) }
            Feature.CRATER -> { sr.color = Pal.hex(0x253858, 0.12f); sr.circle(cx - w * 0.2f, cy + h * 0.3f, w * 0.08f, 12); sr.circle(cx + w * 0.25f, cy - h * 0.3f, w * 0.06f, 12); sr.circle(cx + w * 0.3f, cy + h * 0.25f, w * 0.05f, 10) }
            Feature.SPIKES -> { sr.color = Pal.hex(0xFFFFFF, 0.85f); for (i in 0 until 6) { val yy = cy - h * 0.4f + (i % 3) * h * 0.3f; val xx = if (i < 3) cx - w * 0.37f else cx + w * 0.37f; sr.circle(xx, yy, w * 0.03f, 6) } }
            Feature.CROWN -> crown(sr, cx, cy + h * 0.52f, w * 0.7f)
            Feature.PRISM -> { sr.color = Pal.hex(0xFFFFFF, 0.35f); sr.triangle(cx - w * 0.1f, cy + h * 0.32f, cx + w * 0.16f, cy - h * 0.2f, cx + w * 0.3f, cy - h * 0.1f) }
            else -> {}
        }
    }

    private fun crown(sr: ShapeRenderer, cx: Float, y: Float, w: Float) {
        sr.color = dark
        sr.rect(cx - w / 2 - w * 0.04f, y - w * 0.04f, w * 1.08f, w * 0.28f)
        sr.color = Pal.GOLD
        sr.rect(cx - w / 2, y, w, w * 0.2f)
        for (i in 0..2) sr.triangle(cx - w / 2 + i * w / 2 - w * 0.18f, y + w * 0.18f, cx - w / 2 + i * w / 2 + w * 0.18f, y + w * 0.18f, cx - w / 2 + i * w / 2, y + w * 0.5f)
        sr.color = Pal.RED; sr.circle(cx, y + w * 0.1f, w * 0.06f, 8)
    }

    /** Kostümler (style indeksine göre). */
    private fun costume(sr: ShapeRenderer, style: Int, cx: Float, cy: Float, w: Float, h: Float, t: Float) {
        val top = cy + h * 0.48f
        when (style) {
            1 -> { // parti şapkası
                sr.color = dark; sr.triangle(cx - w * 0.24f, top - 0.02f * w, cx + w * 0.24f, top - 0.02f * w, cx + w * 0.05f, top + w * 0.6f)
                sr.color = Pal.PINK; sr.triangle(cx - w * 0.2f, top, cx + w * 0.2f, top, cx + w * 0.05f, top + w * 0.55f)
                sr.color = Pal.YELLOW; sr.circle(cx + w * 0.05f, top + w * 0.57f, w * 0.08f, 10)
            }
            2 -> crown(sr, cx, top - w * 0.04f, w * 0.62f)
            3, 4 -> { // gözlük
                val ey = cy + h * 0.06f
                sr.color = if (style == 4) dark else dark
                sr.circle(cx - w * 0.19f, ey, w * 0.16f, 16); sr.circle(cx + w * 0.19f, ey, w * 0.16f, 16)
                sr.rect(cx - w * 0.06f, ey + w * 0.02f, w * 0.12f, w * 0.04f)
                if (style == 3) { sr.color = Pal.hex(0xFFFFFF, 0.55f); sr.circle(cx - w * 0.19f, ey, w * 0.12f, 16); sr.circle(cx + w * 0.19f, ey, w * 0.12f, 16) }
                else { sr.color = Pal.hex(0x5B6B8C); sr.circle(cx - w * 0.19f, ey, w * 0.12f, 16); sr.circle(cx + w * 0.19f, ey, w * 0.12f, 16) }
            }
            5 -> { // papyon
                val y = cy - h * 0.36f
                sr.color = Pal.RED; sr.triangle(cx, y, cx - w * 0.2f, y + w * 0.1f, cx - w * 0.2f, y - w * 0.1f); sr.triangle(cx, y, cx + w * 0.2f, y + w * 0.1f, cx + w * 0.2f, y - w * 0.1f)
                sr.color = Pal.darker(Pal.RED); sr.circle(cx, y, w * 0.05f, 8)
            }
            6 -> { sr.color = Pal.TURQ; Shapes.roundRect(sr, cx - w * 0.5f, cy - h * 0.42f, w, h * 0.14f, h * 0.06f); sr.rect(cx + w * 0.2f, cy - h * 0.62f, w * 0.16f, h * 0.24f) }
            7 -> { sr.color = Pal.hex(0xFFFFFF, 0.35f); sr.circle(cx, cy + h * 0.05f, w * 0.68f, 28); sr.color = Pal.hex(0xFFFFFF, 0.6f); sr.ellipse(cx - w * 0.45f, cy + h * 0.3f, w * 0.3f, h * 0.15f, 12) }
            8 -> { sr.color = dark; Shapes.line(sr, cx - w * 0.5f, cy + h * 0.3f, cx + w * 0.5f, cy + h * 0.05f, w * 0.05f); sr.circle(cx + w * 0.19f, cy + h * 0.08f, w * 0.15f, 14) }
            9 -> for (i in 0 until 5) { sr.color = Pal.game[i % 4]; sr.circle(cx - w * 0.36f + i * w * 0.18f, top - w * 0.02f, w * 0.1f, 12); sr.color = Pal.YELLOW; sr.circle(cx - w * 0.36f + i * w * 0.18f, top - w * 0.02f, w * 0.04f, 8) }
            10 -> {
                sr.color = Pal.hex(0x7C5CFF); sr.triangle(cx - w * 0.34f, top - w * 0.02f, cx + w * 0.34f, top - w * 0.02f, cx + w * 0.1f, top + w * 0.75f)
                sr.color = Pal.YELLOW; Shapes.star(sr, cx, top + w * 0.22f, w * 0.09f, w * 0.04f)
                sr.color = Pal.hex(0x5A3FD9); Shapes.roundRect(sr, cx - w * 0.44f, top - w * 0.08f, w * 0.88f, w * 0.1f, w * 0.05f)
            }
            11 -> { sr.color = dark; sr.arc(cx, cy + h * 0.1f, w * 0.56f, 0f, 180f, 20); sr.color = Pal.hex(0xFFFFFF, 0f) ; sr.color = Pal.RED; Shapes.roundRect(sr, cx - w * 0.62f, cy - h * 0.05f, w * 0.2f, h * 0.32f, w * 0.08f); Shapes.roundRect(sr, cx + w * 0.42f, cy - h * 0.05f, w * 0.2f, h * 0.32f, w * 0.08f) }
            12 -> { sr.color = Pal.ORANGE; sr.arc(cx, top - w * 0.12f, w * 0.44f, 0f, 180f, 20); sr.color = Pal.lighter(Pal.ORANGE); sr.rect(cx - w * 0.46f, top - w * 0.16f, w * 0.92f, w * 0.1f); sr.color = Pal.WHITE; sr.circle(cx, top + w * 0.34f, w * 0.09f, 10) }
            13 -> { sr.color = Pal.RED; sr.triangle(cx - w * 0.4f, cy + h * 0.2f, cx + w * 0.4f, cy + h * 0.2f, cx + sin(t * 4) * w * 0.1f, cy - h * 0.9f) }
            14 -> { sr.color = Pal.hex(0xFFD1F0); sr.triangle(cx - w * 0.08f, top - w * 0.02f, cx + w * 0.08f, top - w * 0.02f, cx, top + w * 0.5f); sr.color = Pal.YELLOW; sr.rect(cx - w * 0.06f, top + w * 0.12f, w * 0.12f, w * 0.03f) }
            15 -> { sr.color = Pal.WHITE; sr.rect(cx - w * 0.3f, top - w * 0.06f, w * 0.6f, w * 0.18f); sr.circle(cx - w * 0.2f, top + w * 0.25f, w * 0.18f, 14); sr.circle(cx + w * 0.2f, top + w * 0.25f, w * 0.18f, 14); sr.circle(cx, top + w * 0.32f, w * 0.2f, 14) }
        }
    }
}
