package com.kayipadalar.game.view

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.kayipadalar.game.Assets
import kotlin.math.sin

/**
 * Kısa otomatik savaş animasyonu: iki ordu karşılıklı ilerler, ortada çarpışır, kazanan taraf ilerlemeye devam eder.
 * Sonuç kural motorunda önceden hesaplanır; bu sahne yalnızca görselleştirir.
 */
class BattleScene(
    private val assets: Assets,
    private val ours: List<String>,
    private val theirs: List<String>,
    private val win: Boolean,
    private val onDone: () -> Unit,
) : Actor() {
    private var t = 0f
    private var done = false
    private val duration = 2.4f

    override fun act(delta: Float) {
        super.act(delta)
        t += delta
        if (!done && t >= duration) { done = true; onDone() }
    }

    private fun drawSprite(b: Batch, r: TextureRegion, cx: Float, by: Float, w: Float, flip: Boolean, tint: Color, alpha: Float) {
        val h = w * r.regionHeight / r.regionWidth.coerceAtLeast(1)
        b.setColor(tint.r, tint.g, tint.b, alpha)
        if (flip) b.draw(r, cx + w / 2, by, -w, h) else b.draw(r, cx - w / 2, by, w, h)
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        val cx = x + width / 2; val groundY = y + height * 0.32f
        // zemin: çimen karoları
        val tile = assets["terrain/t0"]
        val tw = 120f
        var gx = x - 20f
        var row = 0
        while (row < 3) {
            gx = x - 20f + (row % 2) * tw / 2
            while (gx < x + width + tw) {
                batch.setColor(1f, 1f, 1f, 1f)
                batch.draw(tile, gx, groundY - 70f - row * 32f, tw, tw * tile.regionHeight / tile.regionWidth)
                gx += tw
            }
            row++
        }
        val approach = Interpolation.pow2Out.apply((t / 1.1f).coerceIn(0f, 1f))
        val clash = t in 1.0f..1.9f
        val after = ((t - 1.6f) / 0.8f).coerceIn(0f, 1f)
        fun lane(i: Int) = groundY + (i % 3) * 52f - 30f
        fun rowShift(i: Int) = (i % 3) * 38f
        val ourAlpha = if (!win) 1f - after * 0.8f else 1f
        val theirAlpha = if (win) 1f - after * 0.9f else 1f
        for ((i, u) in ours.withIndex()) {
            val startX = x - 80f - (i / 3) * 90f
            val endX = cx - 70f - (i / 3) * 90f
            var px = MathUtils.lerp(startX, endX, approach)
            if (win) px += after * 120f
            val hop = if (clash) sin(t * 18f + i) * 6f else 0f
            drawSprite(batch, assets[u], px - rowShift(i), lane(2 - i % 3) + hop, 92f, false, Color.WHITE, ourAlpha)
        }
        for ((i, u) in theirs.withIndex()) {
            val startX = x + width + 80f + (i / 3) * 90f
            val endX = cx + 70f + (i / 3) * 90f
            var px = MathUtils.lerp(startX, endX, approach)
            if (!win) px -= after * 120f
            val hop = if (clash) sin(t * 17f + i * 2) * 6f else 0f
            drawSprite(batch, assets[u], px + rowShift(i), lane(2 - i % 3) + hop, 92f, true, Color(1f, 0.62f, 0.62f, 1f), theirAlpha)
        }
        if (clash) {
            val frame = ((t - 1.0f) / 0.9f * 4).toInt().coerceIn(0, 3)
            val boom = assets["effects/fx_boom$frame"]
            for (k in 0 until 3) {
                val w = 170f + k * 30f
                val bx = cx + (k - 1) * 70f; val by = groundY + 10f + k * 25f
                batch.setColor(1f, 1f, 1f, 0.95f)
                batch.draw(boom, bx - w / 2, by, w, w * boom.regionHeight / boom.regionWidth)
            }
        }
        if (t > 1.9f) {
            val fx = assets[if (win) "effects/fx_up3" else "effects/fx_boom3"]
            val a = ((t - 1.9f) / 0.5f).coerceIn(0f, 1f)
            batch.setColor(1f, 1f, 1f, a)
            val w = 260f
            batch.draw(fx, cx - w / 2, groundY + 140f, w, w * fx.regionHeight / fx.regionWidth)
        }
        batch.setColor(Color.WHITE)
    }
}
