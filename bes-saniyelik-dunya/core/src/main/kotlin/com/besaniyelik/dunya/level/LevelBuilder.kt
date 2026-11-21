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

    /** Geri alma noktası: bölgeden taşan desen [rollback] ile silinir. */
    fun mark(): IntArray = intArrayOf(solids.size, hazards.size, pads.size, floor.size, ceil.size, features.size, coinHints.size)

    fun rollback(m: IntArray) {
        fun <T> cut(l: ArrayList<T>, n: Int) { while (l.size > n) l.removeAt(l.size - 1) }
        cut(solids, m[0]); cut(hazards, m[1]); cut(pads, m[2]); cut(floor, m[3]); cut(ceil, m[4]); cut(features, m[5]); cut(coinHints, m[6])
    }

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

    fun saw(sign: Int, cx: Float, cyUp: Float, r: Float, ax: Float = 0f, ayUp: Float = 0f, period: Float = 0f, phase: Float = 0f, sync: Boolean = false) {
        hazards.add(HazardDef(HazardType.SAW, r2(cx), r2(wy(sign, cyUp)), r2(r), sign, r2(ax), r2(ayUp * sign), r2(period), r2(phase), sync))
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
                } else if (last != null && s.x0 - last.x1 < 0.9f) {
                    // karakterden dar "kılcal" boşluk: boşluk gibi görünür ama üstünden koşulur; komşu parçaya kat
                    out[out.size - 1] = GroundSpan(last.x0, s.x0, last.hUp)
                    out.add(s)
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
    /** Gerçek koşu hızı (karo/dünya-sn): buzda seyir hızı daha yüksektir, desenler buna göre boyutlanır. */
    val vx get() = Physics.RUN_SPEED * speedMul * (if (e.ice) Physics.ICE_SPEED_MUL else 1f)
    /**
     * Genişlik çarpanı: iniş alanları ve platformlar, oyuncunun GERÇEK zamanda aynı tepki payına sahip olması için
     * buzda (daha hızlı) ve Hızlı Dünya'da (zaman 1,3 kat akar) orantılı olarak genişler.
     */
    val kw get() = (if (e.ice) Physics.ICE_SPEED_MUL else 1f) * kotlin.math.max(1f, e.timeScale)
    val jumpV get() = Physics.JUMP_V * e.jumpMul
    val airTime get() = 2f * jumpV / Physics.GRAVITY
    /** Tam zıplamanın yatay menzili (karo). */
    val reach get() = vx * airTime
    /** Tam zıplama yüksekliği. */
    val height get() = jumpV * jumpV / (2f * Physics.GRAVITY)

    fun rnd(a: Float, b: Float) = a + rng.nextFloat() * (b - a)
    fun lerp(a: Float, b: Float) = a + (b - a) * t
    /** Zorluk 1'in ötesine (dökümhanenin sıkılaştırdığı adaylar) taşsa da değeri makul sınırda tutan sürüm. */
    fun lerp(a: Float, b: Float, lo: Float, hi: Float = Float.MAX_VALUE) = (a + (b - a) * t).coerceIn(lo, hi)
    fun chance(p: Float) = rng.nextFloat() < p
}

/** Desen kütüphanesi: her desen yerleştirildiği bölgenin sonunu döndürür. */
object Patterns {
    fun applicable(p: Pattern, e: RuleEffects): Boolean = when (p) {
        Pattern.GHOST_BRIDGE -> e.ghost
        Pattern.SHIFT_BRIDGE -> e.shifting
        Pattern.LOW_TUNNEL -> e.sizeScale < 1f && !e.reverseControl
        Pattern.BOUNCY_WALL -> e.jumpMul > 1.2f
        Pattern.CEILING_GAP -> !e.reverseControl && e.jumpMul < 1.2f && e.timeScale <= 1f
        Pattern.STEP, Pattern.STAIRS -> true
        else -> true
    }

    /** Kurala özgü (zorunlu) desenler. */
    fun dedicated(e: RuleEffects): List<Pattern> {
        val out = ArrayList<Pattern>()
        if (e.reverseControl) out.add(Pattern.OVERHEAD)
        if (e.ghost) out.add(Pattern.GHOST_BRIDGE)
        if (e.shifting) out.add(Pattern.SHIFT_BRIDGE)
        if (e.sizeScale < 1f && !e.reverseControl) out.add(Pattern.LOW_TUNNEL)
        if (e.jumpMul > 1.2f) out.add(Pattern.BOUNCY_WALL)
        return out
    }

    /** Desenin sığabileceği en kısa bölge (alt sınır; taşan desen üretici tarafından geri alınır). */
    fun minLength(p: Pattern): Float = when (p) {
        Pattern.MOVING_PLATFORM -> 11.5f
        Pattern.STAIRS, Pattern.SPRING_WALL, Pattern.FLOATING -> 10f
        Pattern.SHIFT_BRIDGE -> 8.5f
        Pattern.GHOST_BRIDGE, Pattern.LOW_TUNNEL, Pattern.BOUNCY_WALL -> 7.5f
        Pattern.BOOST_GAP -> 9f
        Pattern.OVERHEAD, Pattern.SAW, Pattern.MOVING_SAW -> 7f
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
        Pattern.BOUNCY_WALL -> bouncyWall(c, x, limit)
        Pattern.OVERHEAD -> overhead(c, x)
        Pattern.CEILING_GAP -> ceilingGap(c, x)
        Pattern.STAIRS -> stairs(c, x, limit)
        Pattern.SPIKE_GAP -> spikeGap(c, x)
    }.also { c.b.features.add(Feature(p, x, c.sign)) }

    private fun flat(c: PatternCtx, x0: Float, x1: Float) = c.b.ground(c.sign, x0, x1)

    /** Gerçek boşluk en az 1,8 karodur: daha darı boşluk gibi görünür ama karakter üstünden koşar. */
    const val MIN_GAP = 1.8f

    fun gapWidth(c: PatternCtx): Float = min(c.lerp(1.8f, 0.6f * c.reach) * c.rnd(0.9f, 1.05f), 0.66f * c.reach).coerceAtLeast(MIN_GAP)

    fun gap(c: PatternCtx, x: Float): Float {
        val w = gapWidth(c)
        flat(c, x, x + 1.5f)
        val g0 = x + 1.5f
        flat(c, g0 + w, g0 + w + 2f)
        c.b.coinArc(c.sign, g0 - 0.6f, Physics.PLAYER_H / 2f, c.vx, c.jumpV * 0.85f, 5, c.airTime / 6f)
        return g0 + w + 2f
    }

    fun spikes(c: PatternCtx, x: Float): Float {
        // Diken şeridinin genişliği zamanlama payını belirler: tam zıplamada ayaklar dikenlerin üstünde yaklaşık
        // 0,855 x menzil boyunca kalır; şerit + gövde bundan "pay" kadar kısadır. Pay zorlukla 2,0 -> 0,8 karoya iner.
        fun width(): Float {
            val slack = c.lerp(2.0f, 0.8f, 0.55f) * c.kw * c.rnd(0.9f, 1.1f)
            return r2(0.855f * c.reach - 0.53f - slack).coerceIn(0.9f, 3.4f)
        }
        val n = width()
        flat(c, x, x + 1.5f + n + 2.5f)
        c.b.spikes(c.sign, x + 1.5f, 0f, n)
        if (c.t > 0.45f && c.chance(0.4f)) {
            // ikinci sıra: arada zorlukla kısalan bir iniş alanı
            val land = c.lerp(3.8f, 2.4f, 1.9f) * c.kw
            val x2 = x + 1.5f + n + land
            val n2 = width()
            flat(c, x + 1.5f + n, x2 + n2 + 2.5f)
            c.b.spikes(c.sign, x2, 0f, n2)
            return x2 + n2 + 2.5f
        }
        return x + 1.5f + n + 2.5f
    }

    fun step(c: PatternCtx, x: Float, limit: Float): Float {
        val h = c.rnd(c.lerp(0.8f, 1f), min(c.lerp(1.3f, 2f), c.height - 0.9f)).coerceAtLeast(0.8f)
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
        // Yükseklik farkı ve boşluk zorlukla büyür; düşük zorlukta platformlar geniş ve neredeyse aynı hizadadır.
        val dh = c.lerp(0.45f, 1.05f)
        for (i in 0 until k) {
            val g = min(c.lerp(1.8f, 0.46f * c.reach) * c.rnd(0.95f, 1.05f), 0.5f * c.reach).coerceAtLeast(MIN_GAP)
            val bw = c.rnd(c.lerp(3.0f, 2.3f, 1.9f), c.lerp(3.6f, 3.0f, 2.3f)) * c.kw
            val maxH = min(2.0f, c.height - 0.8f)
            val h = (lastH + c.rnd(-dh, dh)).coerceIn(0.6f, maxH)
            if (px + g + bw + 3f > limit) break
            c.b.box(c.sign, px + g, h - 0.7f, bw, h, SolidType.BLOCK)
            px += g + bw
            lastH = h
        }
        val g = min(c.lerp(1.8f, 0.45f * c.reach), 0.5f * c.reach).coerceAtLeast(MIN_GAP)
        flat(c, px + g, px + g + 2f)
        return px + g + 2f
    }

    fun saw(c: PatternCtx, x: Float): Float {
        // Testereler karakterle boy ölçüşecek kadar büyüktür (çap 1,5-1,9 karo); yarısı zemine gömülüdür.
        val r = c.lerp(0.75f, 1.0f, 0.7f, 1.08f) * c.rnd(0.95f, 1.03f)
        flat(c, x, x + 5.5f)
        c.b.saw(c.sign, x + 2.75f, 0.1f, r)
        if (c.t > 0.35f && c.chance(0.5f)) {
            // ikinci testere: arada rahat bir iniş alanı kalır
            val d = 2f * (r * Physics.SAW_HIT + Physics.PLAYER_W / 2f) + c.rnd(c.lerp(2.6f, 1.7f, 1.4f), c.lerp(3.2f, 2.3f, 1.8f)) * c.kw
            flat(c, x + 5.5f, x + 5.5f + d)
            c.b.saw(c.sign, x + 2.75f + d, 0.1f, r)
            return x + 5.5f + d
        }
        return x + 5.5f
    }

    fun movingSaw(c: PatternCtx, x: Float): Float {
        flat(c, x, x + 7f)
        // Oyuncuya kilitli salınan testere: oyuncu hizasına geldiğinde testere hep aynı (yukarıya yakın) konumdadır;
        // altından koşulur, o sırada zıplanmaz. Zorluk arttıkça varış anındaki konum tepeden uzaklaşır (pay daralır).
        val period = c.rnd(2.4f, 3.2f)
        val off = c.lerp(0.25f, 1.0f) * c.rnd(0.5f, 1f) * (if (c.chance(0.5f)) 1f else -1f)
        c.b.saw(c.sign, x + 3.5f, 2.3f, 0.85f, 0f, 1.3f, period, (Math.PI / 2).toFloat() + off, sync = true)
        return x + 7f
    }

    fun movingPlatform(c: PatternCtx, x: Float): Float {
        // Yatay giden platform yalnızca normal boyda ve buz dışında, çukur 7,6 karoyu aşmadan kullanılır (daha uzun
        // çukurlarda ya da küçükken bazı varış anlarında geçilemiyordu); diğer durumlarda dikey platform çifti.
        val horizontal = c.chance(0.5f) && c.e.sizeScale >= 1f && !c.e.ice
        val len = if (horizontal) c.rnd(6.5f, 7.6f) else c.rnd(6.5f, 9f) * c.kw
        flat(c, x, x + 1.5f)
        val p0 = x + 1.5f
        val w = c.lerp(3.4f, 2.8f, 2.4f)
        if (horizontal) {
            val ax = (len - w) / 2f - 0.6f
            c.b.box(c.sign, p0 + len / 2f - w / 2f, -0.7f, w, 0f, SolidType.MOVE, ax, 0f, c.rnd(c.lerp(3.4f, 2.6f), c.lerp(4.0f, 3.2f)), c.rnd(0f, 6.28f))
        } else {
            // dikey hareket eden iki platform
            val w2 = c.lerp(2.7f, 2.3f, 2.0f) * c.kw
            val gap = (len - 2 * w2) / 3f
            val amp = c.lerp(0.5f, 0.8f, 0.4f, 1.0f)
            c.b.box(c.sign, p0 + gap, 0f, w2, 0.7f, SolidType.MOVE, 0f, amp, c.rnd(2.6f, 3.2f), 0f)
            c.b.box(c.sign, p0 + 2 * gap + w2, 0f, w2, 0.7f, SolidType.MOVE, 0f, amp, c.rnd(2.6f, 3.2f), 3.14f)
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
        c.b.coinArc(c.sign, x + 3.4f, Physics.PLAYER_H / 2f, c.vx * Physics.BOOST_MUL, c.jumpV * 0.85f, 6, c.airTime / 7f)
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
        // Kayma genliği zorlukla büyür; platformlar geniş, boşluklar kısa tutulur ki iniş zamanlaması kare hassasiyeti istemesin.
        val amp = c.lerp(0.45f, 0.8f, 0.4f, 1.0f)
        for (i in 0 until k) {
            val g = c.rnd(MIN_GAP, c.lerp(2.0f, 2.3f))
            val w = c.rnd(c.lerp(3.2f, 2.6f, 2.2f), c.lerp(3.8f, 3.2f, 2.6f)) * c.kw
            if (px + g + w + 3f > limit) break
            c.b.box(c.sign, px + g, -0.2f, w, 0.5f, SolidType.SHIFT, 0f, amp, c.rnd(3.0f, 3.8f), i * 1.9f)
            px += g + w
        }
        val g = MIN_GAP
        flat(c, px + g, px + g + 2f)
        return px + g + 2f
    }

    fun lowTunnel(c: PatternCtx, x: Float, limit: Float): Float {
        val len = min(c.lerp(4f, 8f) * c.rnd(0.9f, 1.1f), limit - x - 5f).coerceAtLeast(3f)
        flat(c, x, x + 2f + len + 2f)
        c.b.box(c.sign, x + 2f, Physics.LOW_CLEARANCE, len, 4.2f, SolidType.LOW)
        c.b.coinLine(c.sign, x + 2.3f, x + 1.7f + len, Physics.PLAYER_H * Physics.SMALL_SCALE / 2f)
        var end = x + 2f + len + 2f
        if (c.t > 0.4f && end + 9f < limit) {
            val w = min(gapWidth(c), 0.5f * c.reach)
            val run = c.lerp(3.4f, 2.4f, 2.0f) * c.kw // boşluktan sonra, tünel ağzından önce iniş alanı
            flat(c, end + w, end + w + run)
            val l2 = c.rnd(3f, 5f)
            val x2 = end + w + run
            flat(c, x2, x2 + l2 + 2f)
            c.b.box(c.sign, x2, Physics.LOW_CLEARANCE, l2, 4.2f, SolidType.LOW)
            end = x2 + l2 + 2f
        }
        return end
    }

    fun bouncyWall(c: PatternCtx, x: Float, limit: Float = Float.MAX_VALUE): Float {
        val len = c.rnd(4f, 6f)
        return if (c.chance(0.6f) && x + 2f + len + 2.5f <= limit) {
            val h = c.rnd(3.3f, min(4.2f, c.height - 0.7f))
            flat(c, x, x + 2f)
            c.b.ground(c.sign, x + 2f, x + 2f + len, h)
            flat(c, x + 2f + len, x + 2f + len + 2.5f)
            x + 2f + len + 2.5f
        } else {
            val w = c.rnd(0.5f, 0.62f) * c.reach
            flat(c, x, x + 1.5f)
            flat(c, x + 1.5f + w, x + 1.5f + w + 2.5f)
            c.b.coinArc(c.sign, x + 0.9f, Physics.PLAYER_H / 2f, c.vx, c.jumpV * 0.85f, 6, c.airTime / 7f)
            x + 1.5f + w + 2.5f
        }
    }

    fun overhead(c: PatternCtx, x: Float): Float {
        val len = c.rnd(2.5f, 4.5f)
        flat(c, x, x + 2f + len + 2.5f)
        // Asılı dikenlerin ucu (blok altı - diken boyu) karakterin çizilen boyunun üstünde kalır
        val oy = Physics.PLAYER_VISUAL_H + Physics.SPIKE_VISUAL_H + 0.18f
        c.b.box(c.sign, x + 2f, oy, len, oy + 0.6f, SolidType.BLOCK)
        c.b.spikes(c.sign, x + 2f, oy, len, up = false)
        c.b.coinLine(c.sign, x + 2.2f, x + 1.8f + len, Physics.PLAYER_H / 2f)
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
        // Dikenlerin ucu zeminden 3,5 karo yukarıda: 0,2 saniyeye kadar basılı tutulan zıplamalar sığar, tam zıplama sığmaz
        val cy = 3.5f + Physics.SPIKE_H
        c.b.box(c.sign, g0 - 0.8f, cy, w + 1.6f, cy + 0.5f, SolidType.BLOCK)
        c.b.spikes(c.sign, g0 - 0.8f, cy, w + 1.6f, up = false)
        return g0 + w + 2.5f
    }

    fun stairs(c: PatternCtx, x: Float, limit: Float): Float {
        flat(c, x, x + 1.5f)
        var px = x + 1.5f
        var h = 0f
        val n = 3 + (c.t * 1.5f).toInt()
        for (i in 0 until n) {
            val dh = c.rnd(c.lerp(0.7f, 0.9f), min(c.lerp(1.0f, 1.4f), c.height - 1f))
            if (h + dh > 5f) break
            h += dh
            val w = c.rnd(c.lerp(3.0f, 2.2f, 1.9f), c.lerp(3.8f, 3f, 2.4f)) * c.kw
            if (px + w + 4f > limit) break
            c.b.ground(c.sign, px, px + w, h)
            px += w
        }
        // inişte küçük boşluk
        val gw = if (c.t > 0.3f) c.rnd(MIN_GAP, 2.4f) else 0f
        flat(c, px + gw, px + gw + 2.5f)
        return px + gw + 2.5f
    }

    fun spikeGap(c: PatternCtx, x: Float): Float {
        val w = min(gapWidth(c), 0.5f * c.reach)
        flat(c, x, x + 1.5f)
        val g0 = x + 1.5f
        val land = c.rnd(c.lerp(3.6f, 2.3f, 1.9f), c.lerp(4.4f, 3.0f, 2.3f)) * c.kw
        val n = 1 + c.rng.nextInt(2)
        flat(c, g0 + w, g0 + w + land + n + 2.5f)
        c.b.spikes(c.sign, g0 + w + land, 0f, n.toFloat())
        return g0 + w + land + n + 2.5f
    }
}
