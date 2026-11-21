package com.besaniyelik.dunya.sim

import com.badlogic.gdx.physics.box2d.Body
import com.besaniyelik.dunya.level.*
import kotlin.math.PI
import kotlin.math.sin

private const val TWO_PI = (2 * PI).toFloat()

/** Çalışma zamanındaki katı platform. (x, y) sol-alt köşedir. */
class SolidEntity(val def: SolidDef, val index: Int) {
    var x = def.x
    var y = def.y
    val w get() = def.w
    val h get() = def.h
    var body: Body? = null
    var active = true
    /** Hayalet platformun görsel durumu: 0 = katı, 1 = uyarı (yanıp söner), 2 = kayıp. */
    var ghostState = 0
    var removed = false

    val moving get() = def.type == SolidType.MOVE || def.type == SolidType.SHIFT

    fun targetX(time: Float): Float = if (def.type == SolidType.MOVE && def.period > 0f) def.x + def.ax * sin(TWO_PI * time / def.period + def.phase) else def.x

    fun targetY(time: Float, shiftClock: Float, shiftAmp: Float): Float = when {
        def.type == SolidType.MOVE && def.period > 0f -> def.y + def.ay * sin(TWO_PI * time / def.period + def.phase)
        def.type == SolidType.SHIFT && def.period > 0f -> def.y + def.ay * shiftAmp * sin(TWO_PI * shiftClock / def.period + def.phase)
        else -> def.y
    }

    fun overlaps(x0: Float, y0: Float, x1: Float, y1: Float): Boolean =
        x0 < x + w && x1 > x && y0 < y + h && y1 > y
}

class HazardEntity(val def: HazardDef, val index: Int) {
    var cx = def.x
    var cy = def.y
    var removed = false

    fun update(time: Float, playerX: Float) {
        if (def.period > 0f) {
            // sync: "zaman" oyuncunun engele uzaklığından türetilir (nominal koşu hızında saniye); oyuncu engelin
            // hizasındayken faz tam def.phase olur.
            val tau = if (def.sync) (playerX - def.x) / Physics.RUN_SPEED else time
            val s = sin(TWO_PI * tau / def.period + def.phase)
            cx = def.x + def.ax * s
            cy = def.y + def.ay * s
        }
    }

    /** Oyuncu kutusuyla (yarı genişlik hw, yarı yükseklik hh) çarpışma; hitbox affedici biçimde küçültülmüştür. */
    fun hits(px: Float, py: Float, hw: Float, hh: Float, m: Float = 0.08f): Boolean {
        return when (def.type) {
            HazardType.SPIKE -> {
                val x0 = cx + 0.08f
                val x1 = cx + def.w - 0.08f
                val y0: Float
                val y1: Float
                if (def.dir > 0) { y0 = cy; y1 = cy + Physics.SPIKE_H } else { y0 = cy - Physics.SPIKE_H; y1 = cy }
                px - hw + m < x1 && px + hw - m > x0 && py - hh + m < y1 && py + hh - m > y0
            }
            HazardType.SAW -> {
                val r = def.w * Physics.SAW_HIT
                val nx = cx.coerceIn(px - hw + m, px + hw - m)
                val ny = cy.coerceIn(py - hh + m, py + hh - m)
                val dx = cx - nx
                val dy = cy - ny
                dx * dx + dy * dy < r * r
            }
        }
    }
}

class PadEntity(val def: PadDef, val index: Int) {
    var removed = false
    var anim = 0f
}

enum class CollectKind { COIN, STAR, PIECE, POWERUP }

class Collectible(val kind: CollectKind, val x: Float, val y: Float, val index: Int, val powerup: PowerupType? = null) {
    var collected = false
    var removed = false
    /** Mıknatısla çekilirken görsel konum. */
    var drawX = x
    var drawY = y
    val radius get() = when (kind) { CollectKind.COIN -> 0.38f; else -> 0.5f }
}

/** x ekseninde basit kova tabanlı uzamsal dizin. */
class SpatialIndex<T>(private val cell: Float = 4f) {
    private val buckets = HashMap<Int, ArrayList<T>>()
    fun add(item: T, x0: Float, x1: Float) {
        val a = Math.floorDiv(x0.toInt() - 1, cell.toInt())
        val b = Math.floorDiv(x1.toInt() + 1, cell.toInt())
        for (c in a..b) buckets.getOrPut(c) { ArrayList(4) }.add(item)
    }
    fun query(x0: Float, x1: Float, seen: HashSet<T>, out: MutableList<T>) {
        out.clear(); seen.clear()
        val a = Math.floorDiv(x0.toInt() - 1, cell.toInt())
        val b = Math.floorDiv(x1.toInt() + 1, cell.toInt())
        for (c in a..b) {
            val l = bucket(c) ?: continue
            for (i in l.indices) { val it = l[i]; if (seen.add(it)) out.add(it) }
        }
    }
    fun bucket(c: Int): ArrayList<T>? = buckets[c]
    fun removeBefore(x: Float) {
        val limit = Math.floorDiv(x.toInt(), cell.toInt()) - 2
        buckets.keys.removeIf { it < limit }
    }
}
