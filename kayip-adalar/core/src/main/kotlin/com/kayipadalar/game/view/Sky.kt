package com.kayipadalar.game.view

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.kayipadalar.game.Assets
import com.kayipadalar.game.ui.Palette

/** Açık mavi degrade gökyüzü ve süzülen bulut sprite'ları. */
class Sky(private val assets: Assets) {
    private var t = 0f
    private class Cloud(var x: Float, val y: Float, val scale: Float, val speed: Float, val alpha: Float)
    private val clouds = List(9) { i -> Cloud((i * 211f) % 900f - 100f, 0.08f + (i * 0.11f) % 0.9f, 0.7f + (i % 4) * 0.25f, 8f + i * 2.5f, 0.55f + (i % 3) * 0.15f) }
    var top: Color = Palette.skyTop
    var bottom: Color = Palette.skyBottom

    fun update(dt: Float, width: Float) {
        t += dt
        for (c in clouds) { c.x += c.speed * dt; if (c.x > width + 150f) c.x = -260f }
    }

    fun draw(b: Batch, white: com.badlogic.gdx.graphics.g2d.TextureRegion, w: Float, h: Float) {
        val strips = 40
        for (i in 0 until strips) {
            val f = i / (strips - 1f)
            b.setColor(Color(bottom).lerp(top, f))
            b.draw(white, 0f, h * i / strips, w, h / strips + 1f)
        }
        val cloud = assets["weather/cloud"]
        for (c in clouds) {
            b.setColor(1f, 1f, 1f, c.alpha)
            val cw = 170f * c.scale; val ch = cw * cloud.regionHeight / cloud.regionWidth.coerceAtLeast(1)
            b.draw(cloud, c.x, c.y * h, cw, ch)
        }
        b.setColor(Color.WHITE)
    }
}
