package com.kayipadalar.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.kayipadalar.game.ui.Palette
import kotlin.math.sin

/** Açık mavi gökyüzü, yumuşak bulutlar ve uzakta süzülen küçük adalar. */
class SkyBackground {
    private var time = 0f
    var topTint: Color = Palette.skyTop
    var bottomTint: Color = Palette.skyBottom

    private data class Cloud(var x: Float, val y: Float, val s: Float, val speed: Float)
    private val clouds = List(7) { i -> Cloud((i * 173f) % 720f, 200f + i * 150f, 0.6f + (i % 3) * 0.35f, 6f + i * 2.3f) }

    fun update(dt: Float) {
        time += dt
        for (c in clouds) { c.x += c.speed * dt; if (c.x > 860f) c.x = -160f }
    }

    fun draw(sr: ShapeRenderer, w: Float, h: Float) {
        sr.begin(ShapeRenderer.ShapeType.Filled)
        sr.rect(0f, 0f, w, h, bottomTint, bottomTint, topTint, topTint)
        // Uzak adacıklar
        for (i in 0 until 4) {
            val x = (i * 210f + 80f) % w
            val y = h * (0.18f + i * 0.17f) + sin(time * 0.6f + i) * 6f
            val c = Color(0.72f, 0.86f, 0.96f, 1f)
            sr.color = c
            sr.triangle(x - 34f, y, x + 34f, y, x, y - 30f)
            sr.color = Color(0.76f, 0.92f, 0.8f, 1f)
            sr.ellipse(x - 36f, y - 6f, 72f, 14f)
        }
        sr.color = Color(1f, 1f, 1f, 0.85f)
        for (c in clouds) {
            val yy = c.y * h / 1280f
            sr.ellipse(c.x - 60f * c.s, yy, 120f * c.s, 40f * c.s)
            sr.ellipse(c.x - 30f * c.s, yy + 14f * c.s, 80f * c.s, 44f * c.s)
            sr.ellipse(c.x + 10f * c.s, yy + 6f * c.s, 70f * c.s, 36f * c.s)
        }
        sr.end()
    }
}
