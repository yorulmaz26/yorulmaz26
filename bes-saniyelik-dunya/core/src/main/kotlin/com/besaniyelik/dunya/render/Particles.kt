package com.besaniyelik.dunya.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Pool

class Particle : Pool.Poolable {
    var region: TextureRegion? = null
    var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
    var life = 0f; var maxLife = 1f
    var size = 0.3f; var endSize = 0.1f
    var rot = 0f; var vr = 0f
    var gravity = 0f
    var drag = 0f
    val color = Color(1f, 1f, 1f, 1f)
    var additive = false
    override fun reset() { region = null; life = 0f; gravity = 0f; drag = 0f; vr = 0f; rot = 0f; additive = false; color.set(1f, 1f, 1f, 1f) }
}

/** Nesne havuzlu parçacık sistemi (çöp üretmez). */
class Particles(private val maxCount: Int = 600) {
    private val pool = object : Pool<Particle>(128, maxCount) { override fun newObject() = Particle() }
    private val live = ArrayList<Particle>(maxCount)
    var reduced = false

    fun spawn(region: TextureRegion, x: Float, y: Float, vx: Float, vy: Float, life: Float, size: Float, endSize: Float, color: Color,
              gravity: Float = 0f, vr: Float = 0f, drag: Float = 0f): Particle? {
        if (live.size >= maxCount || (reduced && live.size >= maxCount / 3)) return null
        val p = pool.obtain()
        p.region = region; p.x = x; p.y = y; p.vx = vx; p.vy = vy; p.life = life; p.maxLife = life
        p.size = size; p.endSize = endSize; p.color.set(color); p.gravity = gravity; p.vr = vr; p.rot = MathUtils.random(360f); p.drag = drag
        live.add(p)
        return p
    }

    fun burst(region: TextureRegion, x: Float, y: Float, n: Int, speed: Float, life: Float, size: Float, color: Color, gravity: Float = 0f, colors: List<Color>? = null) {
        val count = if (reduced) n / 2 else n
        for (i in 0 until count) {
            val a = MathUtils.random(MathUtils.PI2)
            val s = speed * MathUtils.random(0.4f, 1f)
            spawn(region, x, y, MathUtils.cos(a) * s, MathUtils.sin(a) * s, life * MathUtils.random(0.7f, 1.2f), size * MathUtils.random(0.7f, 1.2f), 0f,
                colors?.get(i % colors.size) ?: color, gravity, MathUtils.random(-360f, 360f), 1.5f)
        }
    }

    fun update(dt: Float) {
        var i = 0
        while (i < live.size) {
            val p = live[i]
            p.life -= dt
            if (p.life <= 0f) {
                live[i] = live[live.size - 1]; live.removeAt(live.size - 1); pool.free(p); continue
            }
            p.vy += p.gravity * dt
            if (p.drag > 0f) { val f = 1f - p.drag * dt; p.vx *= f; p.vy *= f }
            p.x += p.vx * dt; p.y += p.vy * dt
            p.rot += p.vr * dt
            i++
        }
    }

    fun draw(batch: Batch) {
        for (p in live) {
            val t = p.life / p.maxLife
            val s = p.endSize + (p.size - p.endSize) * t
            batch.setColor(p.color.r, p.color.g, p.color.b, p.color.a * kotlin.math.min(1f, t * 2.5f))
            batch.draw(p.region, p.x - s / 2, p.y - s / 2, s / 2, s / 2, s, s, 1f, 1f, p.rot)
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }

    fun clear() { live.forEach { pool.free(it) }; live.clear() }
    val count get() = live.size
}
