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
    /**
     * Yoğunluk (0..1): zorluk 1'in üstüne çıktığında devreye girer. Kurala özgü uzun desenler (köprü, tünel) kısalır,
     * aynı aşamaya daha çok engel sığar. Zorluk böylece pencereleri daraltarak değil, engel sayısını artırarak yükselir.
     */
    val crowd get() = ((t - 1f) / 0.5f).coerceIn(0f, 1f)
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
        // Dünyayı değiştiren kuralların nesnesi (hayalet / kayan platform) önce gelir: duyurulan kural mutlaka görünür
        if (e.ghost) out.add(Pattern.GHOST_BRIDGE)
        if (e.shifting) out.add(Pattern.SHIFT_BRIDGE)
        if (e.reverseControl) out.add(Pattern.OVERHEAD)
        if (e.sizeScale < 1f && !e.reverseControl) out.add(Pattern.LOW_TUNNEL)
        if (e.jumpMul > 1.2f) out.add(Pattern.BOUNCY_WALL)
        return out
    }

    /** Zamanlama hassasiyeti isteyen desenler: zorluğun asıl taşıyıcıları. */
    val precision = listOf(Pattern.SPIKES, Pattern.SAW, Pattern.SPIKE_GAP)

    /**
     * Aşamaya mutlaka konacak desenler: kurala özgü olanlar + en üst zorlukta (yoğunluk > 0) hassasiyet desenleri.
     * Yoğunluk 0 iken rastgele sayı tüketilmez (zorluk <= 1 olan bölümlerin üretimi değişmez).
     */
    fun required(c: PatternCtx): List<Pattern> {
        val d = dedicated(c.e)
        if (c.crowd <= 0f) return d
        // yoğunluk 0,5'te aşama başına ortalama bir, 1'de iki hassasiyet deseni
        val n = (2f * c.crowd + c.rng.nextFloat()).toInt()
        return d + List(n) { precision[c.rng.nextInt(precision.size)] }
    }

    /** Sığmayan desenin döndürdüğü "taştı" değeri: üretici deseni geri alır. */
    const val NO_FIT = 1e9f

    /** Desenin sığabileceği en kısa bölge (alt sınır; taşan desen üretici tarafından geri alınır). */
    fun minLength(p: Pattern): Float = when (p) {
        Pattern.MOVING_PLATFORM -> 11.5f
        Pattern.STAIRS, Pattern.SPRING_WALL, Pattern.FLOATING -> 10f
        Pattern.SHIFT_BRIDGE -> 7.7f
        Pattern.BOUNCY_WALL -> 8f
        Pattern.LOW_TUNNEL -> 7.5f
        Pattern.GHOST_BRIDGE -> 5.7f
        Pattern.BOOST_GAP -> 9f
        Pattern.OVERHEAD, Pattern.SAW, Pattern.MOVING_SAW -> 7f
        else -> 6f
    }

    /**
     * Kuralın dünyadaki nesnesinin (hayalet köprü / kayan platform) en dar haliyle sığdığı engel bölgesi uzunluğu.
     * Bölge bundan kısaysa üretici o kuralı o aşamadan çıkarır (kural duyurulup nesnesi görünmeden geçmesin).
     */
    fun minZone(p: Pattern, e: RuleEffects): Float {
        val kw = (if (e.ice) Physics.ICE_SPEED_MUL else 1f) * max(1f, e.timeScale)
        return when (p) {
            Pattern.GHOST_BRIDGE -> 0.5f + 0.6f + 4f + 1f + 0.1f
            Pattern.SHIFT_BRIDGE -> 0.5f + 0.6f + MIN_GAP + 2.4f * kw + MIN_GAP + 1f + 0.1f
            else -> minLength(p) + 0.5f
        }
    }

    fun place(p: Pattern, c: PatternCtx, x: Float, limit: Float): Float = when (p) {
        Pattern.GAP -> gap(c, x)
        Pattern.SPIKES -> spikes(c, x, limit)
        Pattern.STEP -> step(c, x, limit)
        Pattern.FLOATING -> floating(c, x, limit)
        Pattern.SAW -> saw(c, x, limit)
        Pattern.MOVING_SAW -> movingSaw(c, x)
        Pattern.MOVING_PLATFORM -> movingPlatform(c, x, limit)
        Pattern.SPRING_WALL -> springWall(c, x)
        Pattern.BOOST_GAP -> boostGap(c, x)
        Pattern.GHOST_BRIDGE -> ghostBridge(c, x, limit)
        Pattern.SHIFT_BRIDGE -> shiftBridge(c, x, limit)
        Pattern.LOW_TUNNEL -> lowTunnel(c, x, limit)
        Pattern.BOUNCY_WALL -> bouncyWall(c, x, limit)
        Pattern.OVERHEAD -> overhead(c, x, limit)
        Pattern.CEILING_GAP -> ceilingGap(c, x)
        Pattern.STAIRS -> stairs(c, x, limit)
        Pattern.SPIKE_GAP -> spikeGap(c, x, limit)
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

    fun spikes(c: PatternCtx, x: Float, limit: Float = Float.MAX_VALUE): Float {
        // Diken şeridinin genişliği zamanlama payını belirler: tam zıplamada ayaklar dikenlerin üstünde yaklaşık
        // 0,855 x menzil boyunca kalır; şerit + gövde bundan "pay" kadar kısadır. Pay zorlukla 2,0 -> 0,74 karoya (~8 tick)
        // iner; daha darı dürüst çözücüyle güvenilir biçimde çözülemiyor, zorluk bunun ötesinde engel sayısıyla artar.
        fun width(): Float {
            val slack = c.lerp(2.0f, 0.8f, 0.74f) * c.kw * c.rnd(0.95f, 1.1f)
            return r2(0.855f * c.reach - 0.53f - slack).coerceIn(0.9f, 3.4f)
        }
        val n = width()
        flat(c, x, x + 1.5f + n + 2.5f)
        c.b.spikes(c.sign, x + 1.5f, 0f, n)
        if (c.t > 0.45f && c.chance(0.4f + 0.4f * c.crowd)) {
            // ikinci sıra: arada zorlukla kısalan bir iniş alanı
            val land = c.lerp(3.8f, 2.4f, 1.9f) * c.kw
            val x2 = x + 1.5f + n + land
            val n2 = width()
            // ikinci sıra yalnızca bölgeye sığıyorsa konur
            if (x2 + n2 + 2.5f <= limit) {
                flat(c, x + 1.5f + n, x2 + n2 + 2.5f)
                c.b.spikes(c.sign, x2, 0f, n2)
                return x2 + n2 + 2.5f
            }
        }
        return x + 1.5f + n + 2.5f
    }

    fun step(c: PatternCtx, x: Float, limit: Float): Float {
        val h = c.rnd(c.lerp(0.8f, 1f), min(c.lerp(1.3f, 2f), c.height - 0.9f)).coerceAtLeast(0.8f)
        val len = min(c.rnd(4f, 7f), limit - x - 3.5f)
        if (len < 3.5f) return NO_FIT
        flat(c, x, x + 1.5f)
        c.b.ground(c.sign, x + 1.5f, x + 1.5f + len, h)
        if (c.t > 0.4f && len > 5.5f) c.b.spikes(c.sign, x + 1.5f + len * 0.5f, h, 1f)
        flat(c, x + 1.5f + len, x + 1.5f + len + 2f)
        return x + 1.5f + len + 2f
    }

    fun floating(c: PatternCtx, x: Float, limit: Float): Float {
        flat(c, x, x + 1.5f)
        var px = x + 1.5f
        // en üst zorlukta platform sayısı azalır (2-3): yer, hassasiyet isteyen engellere kalır
        val k = min(2 + (c.rng.nextFloat() * (1f + c.t * 2f)).toInt(), if (c.crowd > 0.5f) 2 else if (c.crowd > 0f) 3 else 9)
        var lastH = 0f
        var placed = 0
        // Yükseklik farkı ve boşluk zorlukla büyür; düşük zorlukta platformlar geniş ve neredeyse aynı hizadadır.
        val dh = c.lerp(0.45f, 1.05f)
        for (i in 0 until k) {
            val g = min(c.lerp(1.8f, 0.46f * c.reach) * c.rnd(0.95f, 1.05f), 0.5f * c.reach).coerceAtLeast(MIN_GAP)
            val bw = c.rnd(c.lerp(3.0f, 2.3f, 1.9f), c.lerp(3.6f, 3.0f, 2.3f)) * c.kw
            val maxH = min(2.0f, c.height - 0.8f)
            val h = (lastH + c.rnd(-dh, dh)).coerceIn(0.6f, maxH)
            // kuyruk (son boşluk + iniş düzlüğü) için de yer ayrılır: desen bölgeden taşmaz
            if (px + g + bw + 0.5f * c.reach + 2f > limit) break
            c.b.box(c.sign, px + g, h - 0.7f, bw, h, SolidType.BLOCK)
            px += g + bw
            lastH = h
            placed++
        }
        if (placed == 0) return NO_FIT
        val g = min(c.lerp(1.8f, 0.45f * c.reach), 0.5f * c.reach).coerceAtLeast(MIN_GAP)
        flat(c, px + g, px + g + 2f)
        return px + g + 2f
    }

    fun saw(c: PatternCtx, x: Float, limit: Float = Float.MAX_VALUE): Float {
        // Testereler karakterle boy ölçüşecek kadar büyüktür (çap 1,5-1,9 karo); yarısı zemine gömülüdür.
        val r = c.lerp(0.75f, 1.0f, 0.7f, 1.08f) * c.rnd(0.95f, 1.03f)
        flat(c, x, x + 5.5f)
        c.b.saw(c.sign, x + 2.75f, 0.1f, r)
        if (c.t > 0.35f && c.chance(0.5f + 0.35f * c.crowd)) {
            // ikinci testere: arada rahat bir iniş alanı kalır
            val d = 2f * (r * Physics.SAW_HIT + Physics.PLAYER_W / 2f) + c.rnd(c.lerp(2.6f, 1.7f, 1.4f), c.lerp(3.2f, 2.3f, 1.8f)) * c.kw
            // ikinci testere yalnızca bölgeye sığıyorsa konur
            if (x + 5.5f + d <= limit) {
                flat(c, x + 5.5f, x + 5.5f + d)
                c.b.saw(c.sign, x + 2.75f + d, 0.1f, r)
                return x + 5.5f + d
            }
        }
        return x + 5.5f
    }

    fun movingSaw(c: PatternCtx, x: Float): Float {
        flat(c, x, x + 7f)
        // Oyuncuya kilitli salınan testere: oyuncu hizasına geldiğinde testere hep aynı (yukarıya yakın) konumdadır;
        // altından koşulur, o sırada zıplanmaz. Zorluk arttıkça varış anındaki konum tepeden uzaklaşır (pay daralır).
        val period = c.rnd(2.4f, 3.2f)
        val off = c.lerp(0.25f, 1.0f, 0.25f, 1.15f) * c.rnd(0.5f, 1f) * (if (c.chance(0.5f)) 1f else -1f)
        c.b.saw(c.sign, x + 3.5f, 2.3f, 0.85f, 0f, 1.3f, period, (Math.PI / 2).toFloat() + off, sync = true)
        return x + 7f
    }

    fun movingPlatform(c: PatternCtx, x: Float, limit: Float): Float {
        // Yatay giden platform yalnızca normal boyda ve buz dışında, çukur 7,6 karoyu aşmadan kullanılır (daha uzun
        // çukurlarda ya da küçükken bazı varış anlarında geçilemiyordu); diğer durumlarda dikey platformlar.
        val horizontal = c.chance(0.5f) && c.e.sizeScale >= 1f && !c.e.ice
        val p0 = x + 1.5f
        val w = c.lerp(3.4f, 2.8f, 2.4f)
        val len: Float
        if (horizontal) {
            len = c.rnd(6.5f, 7.6f)
            if (p0 + len + 2f > limit) return NO_FIT
            val ax = (len - w) / 2f - 0.6f
            // Platformun en yüksek hızı koşu hızının %65'ini aşmaz: üstündeki karakter geriye sürüklenmez
            val minPeriod = (2.0 * Math.PI).toFloat() * ax / (0.65f * c.vx)
            val period = max(c.rnd(c.lerp(3.4f, 2.6f, 2.6f), c.lerp(4.0f, 3.2f, 3.2f)), minPeriod)
            c.b.box(c.sign, p0 + len / 2f - w / 2f, -0.7f, w, 0f, SolidType.MOVE, ax, 0f, period, c.rnd(0f, 6.28f))
        } else {
            // Dikey hareket eden platformlar; aralarındaki boşluklar gerçek boşluktur (en az MIN_GAP), yer darsa tek platform
            val w2 = c.lerp(2.7f, 2.3f, 2.0f) * c.kw
            val gap = MIN_GAP * c.rnd(1f, 1.12f)
            val amp = c.lerp(0.5f, 0.8f, 0.4f, 1.0f)
            val two = p0 + 2 * w2 + 3 * gap + 2f <= limit
            len = if (two) 2 * w2 + 3 * gap else w2 + 2 * gap
            if (p0 + len + 2f > limit) return NO_FIT
            c.b.box(c.sign, p0 + gap, 0f, w2, 0.7f, SolidType.MOVE, 0f, amp, c.rnd(2.6f, 3.2f), 0f)
            if (two) c.b.box(c.sign, p0 + 2 * gap + w2, 0f, w2, 0.7f, SolidType.MOVE, 0f, amp, c.rnd(2.6f, 3.2f), 3.14f)
        }
        flat(c, x, p0)
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
        // Yer darsa giriş ve çıkış düzlükleri kısalır (öncesi ve sonrası zaten düz zemindir)
        val tight = limit - x < 9f
        val lead = if (tight) 0.6f else 1.5f
        val tail = if (tight) 1f else 2f
        val len = min(c.lerp(6f, 11f, 6f, 11f) * (1f - 0.4f * c.crowd) * c.rnd(0.85f, 1f), limit - x - lead - tail)
        if (len < 4f) return NO_FIT
        flat(c, x, x + lead)
        // Köprü eşit parçalara bölünür (her parça ~2-2,9 karo): karakterden dar, üstünden koşulan artık parça kalmaz
        val n = max(2, Math.round(len / 2.5f))
        val w = len / n
        for (i in 0 until n) c.b.box(c.sign, x + lead + i * w, -0.6f, w, 0f, SolidType.GHOST, group = i % 2)
        val px = x + lead + len
        flat(c, px, px + tail)
        return px + tail
    }

    fun shiftBridge(c: PatternCtx, x: Float, limit: Float): Float {
        // Yer darsa giriş/çıkış düzlükleri kısalır (öncesi ve sonrası zaten düz zemindir) ve platform alt sınıra kadar daralır
        val tight = limit - x < 12f
        val lead = if (tight) 0.6f else 1.5f
        flat(c, x, x + lead)
        var px = x + lead
        val k = min(2 + (c.t * 2.5f).toInt(), if (c.crowd > 0.5f) 2 else if (c.crowd > 0f) 3 else 9)
        // Kayma genliği zorlukla büyür; platformlar geniş, boşluklar kısa tutulur ki iniş zamanlaması kare hassasiyeti istemesin.
        val amp = c.lerp(0.45f, 0.8f, 0.4f, 1.0f)
        var tail = MIN_GAP + 2f
        var placed = 0
        for (i in 0 until k) {
            var g = c.rnd(MIN_GAP, c.lerp(2.0f, 2.3f))
            var w = c.rnd(c.lerp(3.2f, 2.6f, 2.2f), c.lerp(3.8f, 3.2f, 2.6f)) * c.kw
            if (px + g + w + tail > limit) {
                if (placed > 0) break
                // ilk platform sığmıyorsa en dar haliyle denenir
                g = MIN_GAP
                tail = MIN_GAP + 1f
                w = min(w, limit - px - g - tail)
                if (w < 2.4f * c.kw - 0.01f) return NO_FIT
            }
            c.b.box(c.sign, px + g, -0.2f, w, 0.5f, SolidType.SHIFT, 0f, amp, c.rnd(3.0f, 3.8f), i * 1.9f)
            px += g + w
            placed++
        }
        val end = px + tail
        flat(c, px + MIN_GAP, end)
        return end
    }

    fun lowTunnel(c: PatternCtx, x: Float, limit: Float): Float {
        val len = min(c.lerp(4f, 8f, 4f, 8f) * (1f - 0.45f * c.crowd) * c.rnd(0.9f, 1.1f), limit - x - 4f)
        if (len < 3f) return NO_FIT
        flat(c, x, x + 2f + len + 2f)
        c.b.box(c.sign, x + 2f, Physics.LOW_CLEARANCE, len, 4.2f, SolidType.LOW)
        c.b.coinLine(c.sign, x + 2.3f, x + 1.7f + len, Physics.PLAYER_H * Physics.SMALL_SCALE / 2f)
        var end = x + 2f + len + 2f
        if (c.t > 0.4f && end + 9f < limit) {
            val w = min(gapWidth(c), 0.5f * c.reach)
            val run = c.lerp(3.4f, 2.4f, 2.0f) * c.kw // boşluktan sonra, tünel ağzından önce iniş alanı
            val l2 = min(c.rnd(3f, 5f), limit - end - w - run - 2f)
            if (l2 < 2.5f) return end
            flat(c, end + w, end + w + run)
            val x2 = end + w + run
            flat(c, x2, x2 + l2 + 2f)
            c.b.box(c.sign, x2, Physics.LOW_CLEARANCE, l2, 4.2f, SolidType.LOW)
            end = x2 + l2 + 2f
        }
        return end
    }

    fun bouncyWall(c: PatternCtx, x: Float, limit: Float = Float.MAX_VALUE): Float {
        val len = min(c.rnd(4f, 6f), limit - x - 4.5f)
        val w = c.rnd(0.5f, 0.62f) * c.reach
        val wallFits = len >= 3.5f
        val gapFits = x + 1.5f + w + 2.5f <= limit
        if (!wallFits && !gapFits) return NO_FIT
        return if (wallFits && (c.chance(0.6f) || !gapFits)) {
            val h = c.rnd(3.3f, min(4.2f, c.height - 0.7f))
            flat(c, x, x + 2f)
            c.b.ground(c.sign, x + 2f, x + 2f + len, h)
            flat(c, x + 2f + len, x + 2f + len + 2.5f)
            x + 2f + len + 2.5f
        } else {
            flat(c, x, x + 1.5f)
            flat(c, x + 1.5f + w, x + 1.5f + w + 2.5f)
            c.b.coinArc(c.sign, x + 0.9f, Physics.PLAYER_H / 2f, c.vx, c.jumpV * 0.85f, 6, c.airTime / 7f)
            x + 1.5f + w + 2.5f
        }
    }

    fun overhead(c: PatternCtx, x: Float, limit: Float = Float.MAX_VALUE): Float {
        val len = min(c.rnd(2.5f, 4.5f), limit - x - 4.5f)
        if (len < 2f) return NO_FIT
        flat(c, x, x + 2f + len + 2.5f)
        // Asılı dikenlerin ucu (blok altı - diken boyu) karakterin çizilen boyunun üstünde kalır
        val oy = Physics.PLAYER_VISUAL_H + Physics.SPIKE_VISUAL_H + 0.18f
        c.b.box(c.sign, x + 2f, oy, len, oy + 0.6f, SolidType.BLOCK)
        c.b.spikes(c.sign, x + 2f, oy, len, up = false)
        c.b.coinLine(c.sign, x + 2.2f, x + 1.8f + len, Physics.PLAYER_H / 2f)
        var end = x + 2f + len + 2.5f
        if (c.t > 0.3f && c.chance(0.6f)) {
            // ardından küçük bir boşluk (yer varsa)
            val w = min(gapWidth(c), 0.5f * c.reach)
            if (end + w + 2f <= limit) {
                flat(c, end + w, end + w + 2f)
                end += w + 2f
            }
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
        var placed = 0
        val n = min(3 + (c.t * 1.5f).toInt(), if (c.crowd > 0.5f) 3 else 4)
        for (i in 0 until n) {
            val dh = c.rnd(c.lerp(0.7f, 0.9f), min(c.lerp(1.0f, 1.4f), c.height - 1f))
            if (h + dh > 5f) break
            h += dh
            val w = c.rnd(c.lerp(3.0f, 2.2f, 1.9f), c.lerp(3.8f, 3f, 2.4f)) * c.kw
            if (px + w + 2.4f + 2.5f > limit) break
            c.b.ground(c.sign, px, px + w, h)
            px += w
            placed++
        }
        if (placed == 0) return NO_FIT
        // inişte küçük boşluk
        val gw = if (c.t > 0.3f) c.rnd(MIN_GAP, 2.4f) else 0f
        flat(c, px + gw, px + gw + 2.5f)
        return px + gw + 2.5f
    }

    fun spikeGap(c: PatternCtx, x: Float, limit: Float = Float.MAX_VALUE): Float {
        val w = min(gapWidth(c), 0.5f * c.reach)
        val g0 = x + 1.5f
        val land = c.rnd(c.lerp(3.6f, 2.3f, 1.9f), c.lerp(4.4f, 3.0f, 2.3f)) * c.kw
        var n = 1 + c.rng.nextInt(2)
        if (g0 + w + land + n + 2.5f > limit) n = 1
        if (g0 + w + land + n + 2.5f > limit) return NO_FIT
        flat(c, x, x + 1.5f)
        flat(c, g0 + w, g0 + w + land + n + 2.5f)
        c.b.spikes(c.sign, g0 + w + land, 0f, n.toFloat())
        return g0 + w + land + n + 2.5f
    }
}
