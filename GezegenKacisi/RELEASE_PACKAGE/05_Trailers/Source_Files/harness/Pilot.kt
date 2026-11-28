import kotlin.math.max
import kotlin.math.min

/**
 * Oyunu gerçek kurallarla oynayan otomatik pilot (çarpışmalar açık). Her karede iki seçeneği (şimdi dokun /
 * dokunma) kısa süre ileriye simüle eder ve yaklaşan kule geçidinin ortasına en uygun olanı seçer.
 * Yalnızca dokunma kararı verir; oyunun fiziğine, puanına ya da çarpışmasına müdahale etmez.
 */
class Pilot(private val s: Studio) {
    private var cooldown = 0f
    var debug = false
    /** Tohumlu küçük gecikmeler: aynı tohumla aynı oyun (kuru deneme = kayıt), farklı tohumla farklı oyun. */
    var rng: kotlin.random.Random? = null
    var taps = 0
        private set

    private fun f(name: String) = (s.get(name) as Number).toFloat()

    class Gap(val x: Float, val half: Float, val top: Float, val bottom: Float) { override fun toString() = "x=%.0f half=%.0f top=%.0f bot=%.0f".format(x, half, top, bottom) }

    fun gaps(): List<Gap> {
        @Suppress("UNCHECKED_CAST")
        val cols = s.get("columns") as List<Any>
        val out = ArrayList<Gap>()
        for (c in cols) {
            val cc = c.javaClass
            fun cf(n: String): Any { val fl = cc.getDeclaredField(n); fl.isAccessible = true; return fl.get(c) }
            val x = (cf("x") as Number).toFloat()
            val half = (cf("halfWidth") as Number).toFloat()
            val gapTop = (cf("gapTop") as Number).toFloat()
            val off = (cf("offset") as Number).toFloat()
            @Suppress("UNCHECKED_CAST")
            val planets = cf("planets") as List<Any>
            var top = -1e9f
            var bottom = 1e9f
            for (p in planets) {
                val pc = p.javaClass
                fun pf(n: String): Float { val fl = pc.getDeclaredField(n); fl.isAccessible = true; return (fl.get(p) as Number).toFloat() }
                val y = pf("y")
                val r = pf("r")
                if (y < gapTop + 1f) top = max(top, y + r) else bottom = min(bottom, y - r)
            }
            out.add(Gap(x, half, top + off, bottom + off))
        }
        return out
    }

    private class Haz(val x: Float, val y: Float, val vy: Float, val warn: Float, val top: Boolean)
    private class BoltS(val x: Float, val len: Float, val warn: Float, val life: Float)

    private fun fld(o: Any, n: String): Float { val f = o.javaClass.getDeclaredField(n); f.isAccessible = true; return (f.get(o) as Number).toFloat() }
    private fun fldB(o: Any, n: String): Boolean { val f = o.javaClass.getDeclaredField(n); f.isAccessible = true; return f.get(o) as Boolean }

    private fun hazards(): Pair<List<Haz>, List<BoltS>> {
        @Suppress("UNCHECKED_CAST") val fbs = s.get("fireballs") as List<Any>
        @Suppress("UNCHECKED_CAST") val bs = s.get("bolts") as List<Any>
        return fbs.map { Haz(fld(it, "x"), fld(it, "y"), fld(it, "vy"), fld(it, "warn"), fldB(it, "fromTop")) } to
            bs.map { BoltS(fld(it, "x"), fld(it, "len"), fld(it, "warn"), fld(it, "life")) }
    }

    /** [dt] sonraki karede dokunulsun mu. */
    fun decide(dt: Float): Boolean {
        cooldown -= dt
        if (cooldown > 0f) return false
        val rx = f("rocketX")
        val y = f("rocketY")
        val vy = f("rocketVy")
        val speed = max(60f, f("speed"))
        val g = 1850f * f("gravMul") + f("wind")
        val flapV = -540f * f("flapMul")
        val gs = gaps().filter { it.x + it.half > rx - 30f }.sortedBy { it.x }
        val (fbs, bolts) = hazards()
        fun hazardCost(py: Float, t: Float): Float {
            var c = 0f
            for (fb in fbs) {
                // Uyarı süresince yerinde bekler, sonra yer çekimiyle hareket eder
                val tt = t - fb.warn
                var fx = fb.x - speed * t
                var fy = fb.y
                if (tt > 0f) {
                    val gg = if (fb.top) 1150f * 0.45f else 1150f
                    fy = fb.y + fb.vy * tt + 0.5f * gg * tt * tt
                    if (fb.top) fx -= 90f * tt
                } else if (!fb.top) continue // yerden çıkacak top henüz yerde
                val dx = fx - rx
                val dy = fy - py
                val rr = 15f + 26f + 22f
                if (dx * dx + dy * dy < rr * rr) c += 400f
            }
            for (b in bolts) {
                val bx = b.x - speed * t
                val active = t >= b.warn - 0.05f && t <= b.warn + b.life + 0.05f
                if (active && kotlin.math.abs(bx - rx) < 13f + 30f && py - 26f < b.len + 14f) c += 400f
            }
            return c
        }
        fun target(t: Float): Float {
            val next = gs.firstOrNull { it.x - speed * t + it.half + 10f > rx } ?: return 520f
            return (next.top + next.bottom) / 2f + 20f
        }
        // İlk karar (dokun / dokunma) sonrası makul bir oyuncu gibi devam eden bir politikayla 1,3 sn ileri bakar
        fun cost(flapNow: Boolean): Float {
            var py = y
            var pv = if (flapNow) flapV else vy
            var cd = if (flapNow) 0.1f else 0f
            var t = 0f
            var c = 0f
            val h = 1f / 120f
            while (t < 1.3f) {
                cd -= h
                if (t > 0.05f && cd <= 0f && py > target(t) && pv > -60f) { pv = flapV; cd = 0.1f }
                pv = min(pv + g * h, 820f)
                py += pv * h
                t += h
                if (py < 20f || py > 980f) c += 40f
                for (gp in gs) {
                    val gx = gp.x - speed * t
                    if (gx - gp.half - 16f < rx && gx + gp.half + 16f > rx) {
                        val margin = 34f
                        if (py < gp.top + margin) c += (gp.top + margin - py) * 1.5f
                        if (py > gp.bottom - margin) c += (py - gp.bottom + margin) * 1.5f
                    }
                }
                c += kotlin.math.abs(py - target(t)) * 0.002f
                if (t < 1.0f) c += hazardCost(py, t)
            }
            return c
        }
        val c1 = cost(true); val c0 = cost(false)
        val tap = c1 < c0
        if (debug) println("decide y=%.0f vy=%.0f c1=%.1f c0=%.1f gaps=%s".format(y, vy, c1, c0, gs))
        if (tap && rng != null && rng!!.nextFloat() < 0.2f) return false
        if (tap) { cooldown = 0.1f + (rng?.nextFloat() ?: 0f) * 0.02f; taps++ }
        return tap
    }

    /** [sec] saniye oynar. Yandıysa false döner. */
    fun fly(sec: Float, fps: Int = 60, onFrame: ((Int) -> Unit)? = null): Boolean {
        val dt = 1f / fps
        var t = 0f
        var i = 0
        while (t < sec - 1e-4f) {
            val st = s.stateName()
            if (st == "PLAYING" && decide(dt)) s.call("onPress", true)
            s.call("update", dt)
            onFrame?.invoke(i++)
            t += dt
            val now = s.stateName()
            if (now == "DYING" || now == "OVER") return false
        }
        return true
    }
}
