package com.besaniyelik.dunya.level

import com.besaniyelik.dunya.sim.Physics
import com.besaniyelik.dunya.sim.RuleEffects
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

fun r2(v: Float): Float = round(v * 100f) / 100f

/** Zemin parçası (yukarı-uzayında yükseklik hUp). side: 1 = taban, -1 = tavan. */
class GroundSpan(val x0: Float, val x1: Float, val hUp: Float)

/** Desenin bıraktığı özellik noktası (ipucu yerleşimi ve koleksiyon için). */
class Feature(val pattern: Pattern, val x: Float, val sign: Int)

/**
 * Bölüm geometrisi oluşturucu. Desenler "yukarı-uzayında" yazılır: aktif yüzey y=0'dadır ve yukarı pozitiftir.
 * Ters yer çekiminde (sign = -1) her şey tavana aynalanır.
 */
class LevelBuilder {
    val solids = ArrayList<SolidDef>()
    val hazards = ArrayList<HazardDef>()
    val pads = ArrayList<PadDef>()
    val floor = ArrayList<GroundSpan>()
    val ceil = ArrayList<GroundSpan>()
    val features = ArrayList<Feature>()
    val coinHints = ArrayList<Vec>()

    fun wy(sign: Int, yUp: Float): Float = if (sign > 0) yUp else Physics.CEIL - yUp

    fun ground(sign: Int, x0: Float, x1: Float, hUp: Float = 0f) {
        if (x1 - x0 < 0.01f) return
        (if (sign > 0) floor else ceil).add(GroundSpan(x0, x1, hUp))
    }

    /** Yukarı-uzayında [yUp0, yUp1] aralığını kaplayan kutu. */
    fun box(sign: Int, x: Float, yUp0: Float, w: Float, yUp1: Float, type: SolidType,
            ax: Float = 0f, ayUp: Float = 0f, period: Float = 0f, phase: Float = 0f, group: Int = 0) {
        val y = if (sign > 0) yUp0 else Physics.CEIL - yUp1
        solids.add(SolidDef(r2(x), r2(y), r2(w), r2(yUp1 - yUp0), type, r2(ax), r2(ayUp * sign), r2(period), r2(phase), group))
    }

    fun spikes(sign: Int, x: Float, yUp: Float, w: Float, up: Boolean = true) {
        val dir = if (up) sign else -sign
        hazards.add(HazardDef(HazardType.SPIKE, r2(x), r2(wy(sign, yUp)), r2(w), dir))
    }

    fun saw(sign: Int, cx: Float, cyUp: Float, r: Float, ax: Float = 0f, ayUp: Float = 0f, period: Float = 0f, phase: Float = 0f) {
        hazards.add(HazardDef(HazardType.SAW, r2(cx), r2(wy(sign, cyUp)), r2(r), sign, r2(ax), r2(ayUp * sign), r2(period), r2(phase)))
    }

    fun pad(sign: Int, type: PadType, x: Float, yUp: Float, w: Float) {
        pads.add(PadDef(type, r2(x), r2(wy(sign, yUp)), r2(w), sign))
    }

    fun coinArc(sign: Int, x0: Float, yUp0: Float, vx: Float, vy: Float, n: Int, dt: Float) {
        for (i in 1..n) {
            val t = i * dt
            val yy = yUp0 + vy * t - Physics.GRAVITY * t * t / 2f
            coinHints.add(Vec(r2(x0 + vx * t), r2(wy(sign, yy))))
        }
    }

    fun coinLine(sign: Int, x0: Float, x1: Float, yUp: Float) {
        var x = x0
        while (x <= x1) { coinHints.add(Vec(r2(x), r2(wy(sign, yUp)))); x += 0.9f }
    }

    /** Zemin parçalarını birleştirip katı platformlara dönüştürür. */
    fun finalizeGround() {
        fun merge(list: ArrayList<GroundSpan>): List<GroundSpan> {
            val sorted = list.sortedBy { it.x0 }
            val out = ArrayList<GroundSpan>()
            for (s in sorted) {
                val last = out.lastOrNull()
                if (last != null && s.x0 <= last.x1 + 0.01f && kotlin.math.abs(s.hUp - last.hUp) < 0.01f) {
                    out[out.size - 1] = GroundSpan(last.x0, max(last.x1, s.x1), last.hUp)
                } else if (last != null && s.x0 < last.x1 - 0.01f) {
                    // çakışma: yeni parçayı kırp
                    if (s.x1 > last.x1) out.add(GroundSpan(last.x1, s.x1, s.hUp))
                } else out.add(s)
            }
            return out
        }
        for (g in merge(floor)) solids.add(SolidDef(r2(g.x0), -3f, r2(g.x1 - g.x0), r2(3f + g.hUp), SolidType.GROUND))
        for (g in merge(ceil)) solids.add(SolidDef(r2(g.x0), r2(Physics.CEIL - g.hUp), r2(g.x1 - g.x0), r2(3f + g.hUp), SolidType.CEILING))
        floor.clear(); ceil.clear()
    }
}

/** Bir aşamanın desen bağlamı. */
class PatternCtx(
    val b: LevelBuilder,
    val rng: Random,
    val e: RuleEffects,
    /** zorluk 0..1 */
    val t: Float,
    val speedMul: Float = 1f,
) {
    val sign get() = e.gravitySign
    val vx get() = Physics.RUN_SPEED * speedMul
    val jumpV get() = Physics.JUMP_V * e.jumpMul
    val airTime get() = 2f * jumpV / Physics.GRAVITY
    /** Tam zıplamanın yatay menzili (karo). */
    val reach get() = vx * airTime
    /** Tam zıplama yüksekliği. */
    val height get() = jumpV * jumpV / (2f * Physics.GRAVITY)

    fun rnd(a: Float, b: Float) = a + rng.nextFloat() * (b - a)
    fun lerp(a: Float, b: Float) = a + (b - a) * t
    fun chance(p: Float) = rng.nextFloat() < p
}

/** Desen kütüphanesi: her desen yerleştirildiği bölgenin sonunu döndürür. */
object Patterns {
    fun applicable(p: Pattern, e: RuleEffects): Boolean = when (p) {
        Pattern.GHOST_BRIDGE -> e.ghost
        Pattern.SHIFT_BRIDGE -> e.shifting
        Pattern.LOW_TUNNEL -> e.sizeScale < 1f && !e.reverseControl
        Pattern.BOUNCY_WALL -> e.jumpMul > 1.2f
        Pattern.CEILING_GAP -> !e.reverseControl && e.jumpMul < 1.2f
        Pattern.STEP, Pattern.STAIRS -> true
        else -> true
    }

    /** Kurala özgü (zorunlu) desenler. */
    fun dedicated(e: RuleEffects): List<Pattern> {
        val out = ArrayList<Pattern>()
        if (e.ghost) out.add(Pattern.GHOST_BRIDGE)
        if (e.shifting) out.add(Pattern.SHIFT_BRIDGE)
        if (e.sizeScale < 1f && !e.reverseControl) out.add(Pattern.LOW_TUNNEL)
        if (e.jumpMul > 1.2f) out.add(Pattern.BOUNCY_WALL)
        if (e.reverseControl) out.add(Pattern.OVERHEAD)
        return out
    }

    fun minLength(p: Pattern): Float = when (p) {
        Pattern.GHOST_BRIDGE, Pattern.SHIFT_BRIDGE, Pattern.MOVING_PLATFORM -> 11f
        Pattern.LOW_TUNNEL, Pattern.STAIRS, Pattern.SPRING_WALL, Pattern.BOUNCY_WALL, Pattern.FLOATING -> 10f
        else -> 6f
    }

    fun place(p: Pattern, c: PatternCtx, x: Float, limit: Float): Float = when (p) {
        Pattern.GAP -> gap(c, x)
        Pattern.SPIKES -> spikes(c, x)
        Pattern.STEP -> step(c, x, limit)
        Pattern.FLOATING -> floating(c, x, limit)
        Pattern.SAW -> saw(c, x)
        Pattern.MOVING_SAW -> movingSaw(c, x)
        Pattern.MOVING_PLATFORM -> movingPlatform(c, x)
        Pattern.SPRING_WALL -> springWall(c, x)
        Pattern.BOOST_GAP -> boostGap(c, x)
        Pattern.GHOST_BRIDGE -> ghostBridge(c, x, limit)
        Pattern.SHIFT_BRIDGE -> shiftBridge(c, x, limit)
        Pattern.LOW_TUNNEL -> lowTunnel(c, x, limit)
        Pattern.BOUNCY_WALL -> bouncyWall(c, x)
        Pattern.OVERHEAD -> overhead(c, x)
        Pattern.CEILING_GAP -> ceilingGap(c, x)
        Pattern.STAIRS -> stairs(c, x, limit)
        Pattern.SPIKE_GAP -> spikeGap(c, x)
    }.also { c.b.features.add(Feature(p, x, c.sign)) }

    private fun flat(c: PatternCtx, x0: Float, x1: Float) = c.b.ground(c.sign, x0, x1)

    fun gapWidth(c: PatternCtx): Float = min(c.lerp(1.5f, 0.6f * c.reach) * c.rnd(0.85f, 1f), 0.66f * c.reach)

    fun gap(c: PatternCtx, x: Float): Float {
        val w = gapWidth(c)
        flat(c, x, x + 1.5f)
        val g0 = x + 1.5f
        flat(c, g0 + w, g0 + w + 2f)
        c.b.coinArc(c.sign, g0 - 0.6f, 0.4f, c.vx, c.jumpV * 0.85f, 5, c.airTime / 6f)
        return g0 + w + 2f
    }

    fun spikes(c: PatternCtx, x: Float): Float {
        val maxN = (0.5f * c.reach).toInt().coerceAtLeast(1)
        val n = (1 + (c.rng.nextFloat() * (0.5f + c.t * 2.5f)).toInt()).coerceIn(1, maxN.coerceAtMost(3))
        flat(c, x, x + 1.5f + n + 2.5f)
        c.b.spikes(c.sign, x + 1.5f, 0f, n.toFloat())
        if (c.t > 0.45f && c.chance(0.4f)) {
            // ikinci sıra
            val x2 = x + 1.5f + n + 2.5f
            val n2 = (1 + c.rng.nextInt(2)).coerceAtMost(maxN)
            flat(c, x2, x2 + n2 + 2.5f)
            c.b.spikes(c.sign, x2, 0f, n2.toFloat())
            return x2 + n2 + 2.5f
        }
        return x + 1.5f + n + 2.5f
    }

    fun step(c: PatternCtx, x: Float, limit: Float): Float {
        val h = c.rnd(1f, min(2f, c.height - 0.9f)).coerceAtLeast(0.8f)
        val len = c.rnd(4f, 7f)
        flat(c, x, x + 1.5f)
        c.b.ground(c.sign, x + 1.5f, x + 1.5f + len, h)
        if (c.t > 0.4f && len > 5.5f) c.b.spikes(c.sign, x + 1.5f + len * 0.5f, h, 1f)
        flat(c, x + 1.5f + len, x + 1.5f + len + 2f)
        return x + 1.5f + len + 2f
    }

    fun floating(c: PatternCtx, x: Float, limit: Float): Float {
        flat(c, x, x + 1.5f)
        var px = x + 1.5f
        val k = 2 + (c.rng.nextFloat() * (1f + c.t * 2f)).toInt()
        var lastH = 0f
        for (i in 0 until k) {
            val g = min(c.lerp(1.3f, 0.5f * c.reach) * c.rnd(0.85f, 1f), 0.58f * c.reach)
            val bw = c.rnd(2f, 3f)
            val maxH = min(2.2f, c.height - 0.7f)
            val h = (lastH + c.rnd(-1f, 1.2f)).coerceIn(0.6f, maxH)
            if (px + g + bw + 3f > limit) break
            c.b.box(c.sign, px + g, h - 0.7f, bw, h, SolidType.BLOCK)
            px += g + bw
            lastH = h
        }
        val g = min(c.lerp(1.3f, 0.45f * c.reach), 0.5f * c.reach)
        flat(c, px + g, px + g + 2f)
        return px + g + 2f
    }

    fun saw(c: PatternCtx, x: Float): Float {
        val r = c.rnd(0.5f, 0.7f)
        flat(c, x, x + 5f)
        c.b.saw(c.sign, x + 2.5f, 0.15f, r)
        if (c.t > 0.35f && c.chance(0.5f)) {
            val d = c.rnd(2.8f, 3.6f)
            flat(c, x + 5f, x + 5f + d)
            c.b.saw(c.sign, x + 2.5f + d, 0.15f, r)
            return x + 5f + d
        }
        return x + 5f
    }

    fun movingSaw(c: PatternCtx, x: Float): Float {
        flat(c, x, x + 7f)
        val period = c.rnd(1.8f, 2.6f)
        c.b.saw(c.sign, x + 3.5f, 1.8f, 0.6f, 0f, 1.25f, period, c.rnd(0f, 6.28f))
        return x + 7f
    }

    fun movingPlatform(c: PatternCtx, x: Float): Float {
        val len = c.rnd(6.5f, 9f)
        flat(c, x, x + 1.5f)
        val p0 = x + 1.5f
        val w = 2.6f
        if (c.chance(0.5f)) {
            val ax = (len - w) / 2f - 0.6f
            c.b.box(c.sign, p0 + len / 2f - w / 2f, -0.7f, w, 0f, SolidType.MOVE, ax, 0f, c.rnd(2.4f, 3.2f), c.rnd(0f, 6.28f))
        } else {
            // dikey hareket eden iki platform
            val w2 = 2.2f
            val gap = (len - 2 * w2) / 3f
            c.b.box(c.sign, p0 + gap, 0f, w2, 0.7f, SolidType.MOVE, 0f, 0.8f, c.rnd(2.2f, 2.8f), 0f)
            c.b.box(c.sign, p0 + 2 * gap + w2, 0f, w2, 0.7f, SolidType.MOVE, 0f, 0.8f, c.rnd(2.2f, 2.8f), 3.14f)
        }
        flat(c, p0 + len, p0 + len + 2f)
        return p0 + len + 2f
    }

    fun springWall(c: PatternCtx, x: Float): Float {
        val h = c.rnd(3.3f, 4.1f)
        val len = c.rnd(4.5f, 6.5f)
        flat(c, x, x + 3f)
        c.b.pad(c.sign, PadType.SPRING, x + 1f, 0f, 1f)
        c.b.ground(c.sign, x + 3f, x + 3f + len, h)
        flat(c, x + 3f + len, x + 3f + len + 2.5f)
        return x + 3f + len + 2.5f
    }

    fun boostGap(c: PatternCtx, x: Float): Float {
        flat(c, x, x + 4f)
        c.b.pad(c.sign, PadType.BOOST, x + 0.8f, 0f, 2f)
        val w = min(gapWidth(c) * 1.45f, 0.62f * c.reach * Physics.BOOST_MUL)
        flat(c, x + 4f + w, x + 4f + w + 2.5f)
        c.b.coinArc(c.sign, x + 3.4f, 0.4f, c.vx * Physics.BOOST_MUL, c.jumpV * 0.85f, 6, c.airTime / 7f)
        return x + 4f + w + 2.5f
    }

    fun ghostBridge(c: PatternCtx, x: Float, limit: Float): Float {
        val len = min(c.lerp(6f, 11f) * c.rnd(0.85f, 1f), limit - x - 4f).coerceAtLeast(4f)
        flat(c, x, x + 1.5f)
        var px = x + 1.5f
        var g = 0
        while (px < x + 1.5f + len - 0.5f) {
            val w = min(c.rnd(2.2f, 2.8f), x + 1.5f + len - px)
            c.b.box(c.sign, px, -0.6f, w, 0f, SolidType.GHOST, group = g)
            px += w
            g = 1 - g
        }
        flat(c, px, px + 2f)
        return px + 2f
    }

    fun shiftBridge(c: PatternCtx, x: Float, limit: Float): Float {
        flat(c, x, x + 1.5f)
        var px = x + 1.5f
        val k = 2 + (c.t * 2.5f).toInt()
        for (i in 0 until k) {
            val g = c.rnd(0.9f, 1.4f)
            val w = c.rnd(2.4f, 3.0f)
            if (px + g + w + 3f > limit) break
            c.b.box(c.sign, px + g, -0.2f, w, 0.5f, SolidType.SHIFT, 0f, 0.9f, c.rnd(2.6f, 3.4f), i * 1.9f)
            px += g + w
        }
        val g = 1.2f
        flat(c, px + g, px + g + 2f)
        return px + g + 2f
    }

    fun lowTunnel(c: PatternCtx, x: Float, limit: Float): Float {
        val len = min(c.lerp(4f, 8f) * c.rnd(0.9f, 1.1f), limit - x - 5f).coerceAtLeast(3f)
        flat(c, x, x + 2f + len + 2f)
        c.b.box(c.sign, x + 2f, 0.62f, len, 4.2f, SolidType.LOW)
        c.b.coinLine(c.sign, x + 2.3f, x + 1.7f + len, 0.25f)
        var end = x + 2f + len + 2f
        if (c.t > 0.4f && end + 9f < limit) {
            val w = min(gapWidth(c), 0.55f * c.reach)
            flat(c, end + w, end + w + 1.5f)
            val l2 = c.rnd(3f, 5f)
            val x2 = end + w + 1.5f
            flat(c, x2, x2 + l2 + 2f)
            c.b.box(c.sign, x2, 0.62f, l2, 4.2f, SolidType.LOW)
            end = x2 + l2 + 2f
        }
        return end
    }

    fun bouncyWall(c: PatternCtx, x: Float): Float {
        return if (c.chance(0.6f)) {
            val h = c.rnd(3.3f, min(4.2f, c.height - 0.7f))
            val len = c.rnd(4f, 6f)
            flat(c, x, x + 2f)
            c.b.ground(c.sign, x + 2f, x + 2f + len, h)
            flat(c, x + 2f + len, x + 2f + len + 2.5f)
            x + 2f + len + 2.5f
        } else {
            val w = c.rnd(0.5f, 0.62f) * c.reach
            flat(c, x, x + 1.5f)
            flat(c, x + 1.5f + w, x + 1.5f + w + 2.5f)
            c.b.coinArc(c.sign, x + 0.9f, 0.4f, c.vx, c.jumpV * 0.85f, 6, c.airTime / 7f)
            x + 1.5f + w + 2.5f
        }
    }

    fun overhead(c: PatternCtx, x: Float): Float {
        val len = c.rnd(2.5f, 4.5f)
        flat(c, x, x + 2f + len + 2.5f)
        c.b.box(c.sign, x + 2f, 1.6f, len, 2.2f, SolidType.BLOCK)
        c.b.spikes(c.sign, x + 2f, 1.6f, len, up = false)
        c.b.coinLine(c.sign, x + 2.2f, x + 1.8f + len, 0.4f)
        var end = x + 2f + len + 2.5f
        if (c.t > 0.3f && c.chance(0.6f)) {
            // ardından küçük bir boşluk
            val w = min(gapWidth(c), 0.5f * c.reach)
            flat(c, end + w, end + w + 2f)
            end += w + 2f
        }
        return end
    }

    fun ceilingGap(c: PatternCtx, x: Float): Float {
        val w = c.rnd(0.38f, 0.46f) * c.reach
        flat(c, x, x + 2f)
        val g0 = x + 2f
        flat(c, g0 + w, g0 + w + 2.5f)
        c.b.box(c.sign, g0 - 0.8f, 2.9f, w + 1.6f, 3.4f, SolidType.BLOCK)
        c.b.spikes(c.sign, g0 - 0.8f, 2.9f, w + 1.6f, up = false)
        return g0 + w + 2.5f
    }

    fun stairs(c: PatternCtx, x: Float, limit: Float): Float {
        flat(c, x, x + 1.5f)
        var px = x + 1.5f
        var h = 0f
        val n = 3 + (c.t * 1.5f).toInt()
        for (i in 0 until n) {
            val dh = c.rnd(0.9f, min(1.4f, c.height - 1f))
            if (h + dh > 5f) break
            h += dh
            val w = c.rnd(2.2f, 3f)
            if (px + w + 4f > limit) break
            c.b.ground(c.sign, px, px + w, h)
            px += w
        }
        // inişte küçük boşluk
        val gw = if (c.t > 0.3f) c.rnd(1f, 1.8f) else 0f
        flat(c, px + gw, px + gw + 2.5f)
        return px + gw + 2.5f
    }

    fun spikeGap(c: PatternCtx, x: Float): Float {
        val w = min(gapWidth(c), 0.5f * c.reach)
        flat(c, x, x + 1.5f)
        val g0 = x + 1.5f
        val land = c.rnd(2.2f, 3f)
        val n = 1 + c.rng.nextInt(2)
        flat(c, g0 + w, g0 + w + land + n + 2.5f)
        c.b.spikes(c.sign, g0 + w + land, 0f, n.toFloat())
        return g0 + w + land + n + 2.5f
    }
}
