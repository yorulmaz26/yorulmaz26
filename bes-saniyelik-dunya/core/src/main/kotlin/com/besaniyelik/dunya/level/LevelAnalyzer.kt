package com.besaniyelik.dunya.level

import com.besaniyelik.dunya.sim.*
import java.util.Random
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bir bölümün ÖLÇÜLEN zorluğu. Bölüm gerçek simülasyonda "insan gibi" oynatılarak elde edilir:
 *  - [windows]: her zıplama kaç tick erken/geç başlatılabiliyor (zamanlama payı),
 *  - [holdWindows]: basılı tutma süresi ne kadar değişebiliyor,
 *  - [pComplete]: bütün dokunuşlara rastgele zamanlama hatası (σ tick) eklenince bölümün tek canla bitirilme olasılığı.
 */
class LevelMetrics(
    val ticks: Int,
    /** Her zıplamanın başlangıç x konumu (rapor ve bölüm şeridi için). */
    val jumpX: FloatArray,
    val jumpTick: IntArray,
    /** Her zıplamanın kaydırılabildiği pencere (tick): erken pay + geç pay + 1. */
    val windows: IntArray,
    /** Her zıplamada basılı tutma süresinin değişebildiği pencere (tick). */
    val holdWindows: IntArray,
    /** [LevelAnalyzer.SIGMAS] sırasıyla tamamlanma olasılığı. */
    val pComplete: FloatArray,
    /** Orta oyuncunun gürültülü oynatmalarında ölüm konumları (x). */
    val deathX: FloatArray,
    /** false ise zamanlama payları yetersiz bulunduğu için gürültülü oynatma yapılmamıştır ([pComplete] taban değerdedir). */
    val played: Boolean = true,
) {
    val jumps: Int get() = windows.size
    val minWindow: Int get() = windows.minOrNull() ?: LevelAnalyzer.FREE
    val minHold: Int get() = holdWindows.minOrNull() ?: LevelAnalyzer.FREE
    fun tight(limit: Int): Int = windows.indices.count { windows[it] < limit || holdWindows[it] < limit }
    private fun d(i: Int) = -ln(pComplete[i].coerceIn(0.004f, 1f))
    /** Orta oyuncu (σ = 3,5 tick) için bir denemedeki beklenen ölüm sayısı (Poisson varsayımı): -ln P. */
    val expectedDeaths: Float get() = d(1)
    /** Üç beceri düzeyinin ağırlıklı beklenen ölüm sayısı; zorluk kalibrasyonunun temel ölçüsü. */
    val load: Float get() = 0.45f * d(0) + 0.35f * d(1) + 0.20f * d(2)
    /** 1..10 ölçeğinde ölçülen zorluk. */
    val score: Float get() = LevelAnalyzer.scoreOf(load)
    /** Ölümlerin en çok yığıldığı 6 karoluk pencerenin merkezi ve payı. */
    val hotspot: Pair<Float, Float> get() {
        if (deathX.isEmpty()) return 0f to 0f
        val s = deathX.sorted()
        var best = 0; var lo = 0; var hx = 0f
        for (hi in s.indices) {
            while (s[hi] - s[lo] > 6f) lo++
            if (hi - lo + 1 > best) { best = hi - lo + 1; hx = (s[hi] + s[lo]) / 2f }
        }
        return hx to best / s.size.toFloat()
    }
}

/**
 * Bölüm analizcisi: çözücünün bulduğu çözümü insanın oynayacağı biçime çevirir (doğal dokunuşlar, her zıplama
 * zamanlama penceresinin ortasında) ve zorluğu ölçer. Tamamen deterministiktir.
 */
object LevelAnalyzer {
    /** Hiç bırakılmayan (bölüm sonuna kadar basılı) dokunuş. */
    const val NONE = Int.MAX_VALUE / 4
    /** Bir yanı serbest (sınırsız) pay ve tamamen serbest pencere değeri. */
    const val FREE_SIDE = 99
    const val FREE = 49
    /** İnsanın yapabildiği en kısa dokunuş (tick). */
    const val MIN_HOLD = 5
    /** Oyuncu zamanlama hatası düzeyleri (tick; 1 tick = 16,7 ms): usta, orta, acemi. */
    val SIGMAS = floatArrayOf(2f, 3.5f, 5f)
    private val STEPS = intArrayOf(1, 2, 3, 4, 5, 6, 8, 10, 12, 14, 16, 20, 24)

    /** Hedef zorluk 10'a karşılık gelen ağırlıklı beklenen ölüm sayısı. */
    const val LOAD_AT_10 = 2.6f
    private const val CURVE = 1.35f

    /** Ağırlıklı beklenen ölüm sayısını 1..10 (taşarsa 12'ye kadar) zorluk ölçeğine çevirir; [loadOf] tersidir. */
    fun scoreOf(load: Float): Float = (1f + 9f * Math.pow((load / LOAD_AT_10).toDouble(), 1.0 / CURVE).toFloat()).coerceIn(1f, 12f)
    fun loadOf(score: Float): Float = LOAD_AT_10 * Math.pow(((score - 1f) / 9f).coerceAtLeast(0f).toDouble(), CURVE.toDouble()).toFloat()

    /** Hedef zorluğa göre bir zıplamanın sahip olması gereken en küçük zamanlama penceresi (tick). */
    fun minWindowFor(target: Float): Int = when {
        target < 2f -> 12
        target < 4f -> 9
        target < 7f -> 7
        else -> 6
    }
    /** Basılı tutma süresi için en küçük pencere (tick). */
    fun minHoldFor(target: Float): Int = if (target < 5f) 6 else 5

    /** Bir oynatmanın tick tick kaydı (t. eleman, t. tick'in BAŞINDAKİ durumdur). */
    private class Ref {
        val snaps = ArrayList<SimSnapshot>(4096)
        val x = ArrayList<Float>(4096); val y = ArrayList<Float>(4096); val vy = ArrayList<Float>(4096)
        val grounded = ArrayList<Boolean>(4096); val canJump = ArrayList<Boolean>(4096); val inputs = ArrayList<Boolean>(4096)
        var endTick = 0
        val size get() = snaps.size

        class Tail(val snaps: List<SimSnapshot>, val x: List<Float>, val y: List<Float>, val vy: List<Float>,
                   val grounded: List<Boolean>, val canJump: List<Boolean>, val inputs: List<Boolean>, val endTick: Int)

        fun tail(from: Int) = Tail(ArrayList(snaps.subList(from, size)), ArrayList(x.subList(from, size)), ArrayList(y.subList(from, size)),
            ArrayList(vy.subList(from, size)), ArrayList(grounded.subList(from, size)), ArrayList(canJump.subList(from, size)),
            ArrayList(inputs.subList(from, inputs.size)), endTick)

        fun cut(from: Int) {
            fun <T> c(l: ArrayList<T>) { if (l.size > from) l.subList(from, l.size).clear() }
            c(snaps); c(x); c(y); c(vy); c(grounded); c(canJump); c(inputs)
        }

        fun restore(from: Int, t: Tail) {
            cut(from)
            snaps.addAll(t.snaps); x.addAll(t.x); y.addAll(t.y); vy.addAll(t.vy); grounded.addAll(t.grounded); canJump.addAll(t.canJump); inputs.addAll(t.inputs)
            endTick = t.endTick
        }
    }

    /**
     * Doğal dokunuş planı. İnsan "şu noktaya gelince zıpla" diye oynar; bu yüzden i. dokunuş karakter [pressX]
     * konumuna ulaşınca başlar (hız değişse de — buz, hız pedi — doğru yerde basılır) ve [hold] tick sürer.
     * Karakterin ilerlemediği anlardaki (duvara dayanmış, geri taşınıyor) dokunuşlar ise zamana bağlıdır ([timed],
     * [pressT]). [dx]: o anda bir tick'te kat edilen yol (zamanlama hatasını konuma çevirmek için).
     */
    private class Plan(val pressX: FloatArray, val pressT: IntArray, val timed: BooleanArray, val dx: FloatArray, val hold: IntArray, val adaptive: BooleanArray) {
        val size get() = pressX.size
        fun copy() = Plan(pressX.copyOf(), pressT.copyOf(), timed, dx, hold.copyOf(), adaptive)
        /** j. dokunuşu [ticks] kadar (eksi = erken) kaydırılmış kopya. */
        fun shifted(j: Int, ticks: Float): Plan {
            val c = copy()
            if (timed[j]) c.pressT[j] = pressT[j] + ticks.roundToInt() else c.pressX[j] = pressX[j] + ticks * dx[j]
            return c
        }
        fun allTimed() = Plan(pressX, pressT, BooleanArray(size) { true }, dx, hold, adaptive)
    }

    /**
     * Kapalı çevrim oyuncu: planlanan konuma (ya da ana) gelince dokunur; ama karakter hâlâ havadaysa (önceki zıplama
     * geç bittiyse) insan gibi inişi bekler. Basılı tutma süresi gerçek basış anından sayılır.
     */
    private class Bot(val plan: Plan, start: Int, private val reflex: Boolean = true) {
        private var i = start
        private var down = false
        private var releaseAt = 0
        private var lastRelease = -1
        fun at(sim: Simulation): Boolean {
            val tick = sim.tick
            if (down && tick >= releaseAt) { down = false; lastRelease = tick }
            val p = sim.player
            if (!down && i < plan.size && tick > lastRelease) {
                val due = if (plan.timed[i]) tick >= plan.pressT[i] else p.x >= plan.pressX[i]
                if (due) {
                    val wait = plan.adaptive[i] && !sim.effects.reverseControl && !p.grounded && p.coyote <= 0f
                    if (!wait) {
                        down = true
                        releaseAt = if (plan.hold[i] >= NONE) Int.MAX_VALUE else tick + plan.hold[i]
                        i++
                        return true
                    }
                }
            }
            // Kurtarma refleksi: karakter bir basamağın duvarına takılıp kaldıysa insan beklemez, yeniden zıplar
            // (yakındaki planlı dokunuş bu zıplamayla harcanmış sayılır).
            // Yatay giden platformun geri salınımı da "takılma" sayılır; orada refleks çalışmaz (insan platformdan atlamaz).
            if (reflex && !down && tick > lastRelease + 1 && p.grounded && p.blocked > 0.2f && !sim.effects.reverseControl &&
                p.groundEntity?.def?.type != SolidType.MOVE) {
                down = true; releaseAt = tick + 24
                if (i < plan.size && (if (plan.timed[i]) plan.pressT[i] <= tick + 30 else plan.pressX[i] <= p.x + 0.6f)) i++
            }
            return down
        }
    }

    private fun newSim(level: LevelData) = Simulation(level, SimConfig(hearts = 1, powerups = false))

    /** Simülasyonun şu anki durumundan bölüm bitene kadar oynatır ve [ref]'e ekler. Bölüm bitmezse false. */
    private fun record(sim: Simulation, ref: Ref, input: (Simulation) -> Boolean): Boolean {
        while (true) {
            val p = sim.player
            ref.snaps.add(sim.snapshot()); ref.x.add(p.x); ref.y.add(p.y); ref.vy.add(p.vy); ref.grounded.add(p.grounded)
            ref.canJump.add(!sim.effects.reverseControl && (p.grounded || p.coyote > 0f))
            val inp = input(sim)
            ref.inputs.add(inp)
            sim.step(inp)
            if (sim.status == SimStatus.COMPLETE) { ref.endTick = sim.tick; return true }
            if (sim.status != SimStatus.RUNNING || sim.tick > 60 * 300) return false
        }
    }

    private fun reference(sim: Simulation, toggles: IntArray): Ref? {
        val track = InputTrack(toggles)
        val ref = Ref()
        return if (record(sim, ref) { track.at(it.tick) }) ref else null
    }

    private fun togglesOf(inputs: List<Boolean>): IntArray {
        val out = ArrayList<Int>()
        var state = false
        for ((t, b) in inputs.withIndex()) if (b != state) { out.add(t); state = b }
        return out.toIntArray()
    }

    private fun planOf(toggles: IntArray, ref: Ref): Plan {
        val n = (toggles.size + 1) / 2
        val last = ref.size - 1
        fun tk(i: Int) = min(toggles[2 * i], last)
        fun step(i: Int): Float { val t = tk(i); return if (t < last) ref.x[t + 1] - ref.x[t] else 0f }
        val nominal = Physics.RUN_SPEED * Physics.TICK
        // ilerleme yoksa (duvara dayanmış) ya da son 20 tick'te karakter neredeyse yerinde saydıysa zamana bağlı
        val timed = BooleanArray(n) { val t = tk(it); step(it) <= 0.02f || (t >= 20 && ref.x[t] - ref.x[t - 20] < 20 * 0.4f * nominal) }
        return Plan(
            FloatArray(n) { ref.x[tk(it)] }, IntArray(n) { toggles[2 * it] }, timed,
            FloatArray(n) { val s = step(it); if (s > 0.02f) s else nominal },
            IntArray(n) { if (2 * it + 1 < toggles.size) toggles[2 * it + 1] - toggles[2 * it] else NONE },
            BooleanArray(n) { ref.canJump[tk(it)] },
        )
    }

    /** [from] tick'inden itibaren j. dokunuştan başlayarak [plan] oynatılırsa bölüm hâlâ geçiliyor mu? */
    private fun survives(sim: Simulation, ref: Ref, plan: Plan, j: Int, from: Int, settleAt: Int): Boolean {
        if (from >= ref.size) return true
        val bot = Bot(plan, j)
        sim.restore(ref.snaps[from])
        val settle = settleAt + 3
        var same = 0
        while (true) {
            sim.step(bot.at(sim))
            if (sim.status == SimStatus.COMPLETE) return true
            if (sim.status != SimStatus.RUNNING) return false
            val t = sim.tick
            if (t >= settle && t < ref.size) {
                val p = sim.player
                if (abs(p.x - ref.x[t]) < 0.03f && abs(p.y - ref.y[t]) < 0.03f && abs(p.vy - ref.vy[t]) < 0.3f && p.grounded == ref.grounded[t]) {
                    if (++same >= 3) return true // asıl yörüngeye geri döndü: devamı aynıdır
                } else same = 0
                if (t > settle + 480) return true // 8 sn boyunca hayatta: bu dokunuş tek başına öldürmüyor
            }
            if (t > ref.endTick + 900) return false
        }
    }

    /** j. dokunuşun [erken, geç, kısa tutma, uzun tutma] payları (tick). */
    private fun slackOf(sim: Simulation, ref: Ref, plan: Plan, toggles: IntArray, j: Int): IntArray {
        val out = IntArray(4)
        val p0 = toggles[2 * j]; val h0 = plan.hold[j]
        val prevRelease = if (j > 0 && plan.hold[j - 1] < NONE) toggles[2 * j - 1] else if (j > 0) Int.MAX_VALUE else -1
        for (side in 0..1) {
            var ok = 0
            for (st in STEPS) {
                val sft = if (side == 0) -st else st
                val np = p0 + sft
                if (np <= prevRelease || np < 0) break // önceki dokunuşla çakışır: pay burada biter
                val relEnd = if (h0 >= NONE) max(p0, np) else max(p0, np) + h0
                if (survives(sim, ref, plan.shifted(j, sft.toFloat()), j, min(p0, np), relEnd)) ok = st else break
            }
            out[side] = ok
        }
        if (h0 >= NONE) { out[2] = FREE_SIDE; out[3] = FREE_SIDE; return out }
        for (side in 0..1) {
            var ok = 0
            var free = false
            for (st in STEPS) {
                val nh = h0 + (if (side == 0) -st else st)
                if (nh < MIN_HOLD) {
                    if (side == 0) { free = true; break } // insan daha kısa basamaz: kısa yan serbest
                    continue // çözüm insanın basabileceğinden kısa tutuyor: uzun yan MIN_HOLD'dan başlayarak sınanır
                }
                val mod = plan.copy(); mod.hold[j] = nh
                if (survives(sim, ref, mod, j, p0, p0 + max(h0, nh))) ok = st else break
                if (st == STEPS.last()) free = true
            }
            out[2 + side] = if (free) FREE_SIDE else ok
        }
        return out
    }

    private fun replays(level: LevelData, toggles: IntArray): Boolean {
        val sim = newSim(level)
        try { return LevelSolver.replay(sim, toggles) } finally { sim.dispose() }
    }

    // ------------------------------------------------------------------ doğallaştırma

    /**
     * Çözümü "doğal dokunuşlara" çevirir. Çözücü girişi mümkün olduğunca değiştirmeden sürdürdüğü için çözümleri
     * "hep basılı tut, zıplamak için 4 tick bırakıp yeniden bas" biçimindedir; insan ise kısa dokunur. Her basılı
     * tutma, yörüngeyi BİREBİR aynı bırakan en erken bırakma anına kısaltılır; havada (tamponla) yapılan basışlar
     * zıplamanın gerçekten başladığı ana taşınır.
     */
    fun normalize(level: LevelData, toggles: IntArray): IntArray {
        if (toggles.size < 2) return toggles
        val sim = newSim(level)
        val out = toggles.copyOf()
        try {
            val ref = reference(sim, toggles) ?: return toggles
            var j = 0
            while (2 * j + 1 < toggles.size) {
                val p = 2 * j; val r = p + 1
                val orig = toggles[r]
                var lo = out[p] + 1; var hi = orig
                while (lo < hi) {
                    val mid = (lo + hi) / 2
                    if (sameTrajectory(sim, ref, out, r, mid, mid, orig + 1)) hi = mid else lo = mid + 1
                }
                out[r] = hi
                j++
            }
            // tamponlu basışları zıplamanın başladığı ana taşı (en geç aynı-yörünge basışı)
            j = 0
            while (2 * j < out.size) {
                val p = 2 * j
                val limit = if (p + 1 < out.size) out[p + 1] - 1 else out[p] + 8
                var best = out[p]
                for (t in out[p] + 1..min(out[p] + 8, limit)) {
                    if (sameTrajectory(sim, ref, out, p, t, out[p], t + 30)) best = t else break
                }
                out[p] = best
                j++
            }
            // etkisi olmayan dokunuşları at (çözücü duvar dibinde ya da havada boşuna basmış olabilir)
            var cur = out
            j = cur.size / 2 - 1
            while (j >= 0) {
                val p = 2 * j
                if (p + 1 < cur.size) {
                    val mod = IntArray(cur.size - 2)
                    for (k in 0 until p) mod[k] = cur[k]
                    for (k in p + 2 until cur.size) mod[k - 2] = cur[k]
                    val end = min(ref.snaps.size - 1, (if (p + 2 < cur.size) cur[p + 2] else cur[p + 1]) + 40)
                    if (sameRun(sim, ref, mod, cur[p], end)) cur = mod
                }
                j--
            }
            return if (replays(level, cur)) cur else if (replays(level, out)) out else toggles
        } finally { sim.dispose() }
    }

    /** [mod] girişleriyle [from]..[end] aralığında yörünge referansla her tick'te birebir aynı mı? */
    private fun sameRun(sim: Simulation, ref: Ref, mod: IntArray, from: Int, end: Int): Boolean {
        if (from >= end) return false
        val track = InputTrack(mod)
        sim.restore(ref.snaps[from])
        while (sim.tick < end) {
            sim.step(track.at(sim.tick))
            if (sim.status != SimStatus.RUNNING) return false
            val t = sim.tick
            if (abs(sim.player.x - ref.x[t]) > 1e-4f || abs(sim.player.y - ref.y[t]) > 1e-4f) return false
        }
        val a = sim.snapshot(); val b = ref.snaps[end]
        return abs(a.vy - b.vy) < 1e-3f && abs(a.vxCur - b.vxCur) < 1e-4f && a.grounded == b.grounded && abs(a.autoTimer - b.autoTimer) < 1e-4f
    }

    /** [idx]. giriş [at] tick'ine taşınırsa [from]..[end] aralığında yörünge referansla her tick'te birebir aynı mı? */
    private fun sameTrajectory(sim: Simulation, ref: Ref, cur: IntArray, idx: Int, at: Int, from: Int, end: Int): Boolean {
        if (end >= ref.snaps.size) return false
        val mod = cur.copyOf(); mod[idx] = at
        val track = InputTrack(mod)
        sim.restore(ref.snaps[from])
        while (sim.tick < end) {
            sim.step(track.at(sim.tick))
            if (sim.status != SimStatus.RUNNING) return false
            val t = sim.tick
            if (abs(sim.player.x - ref.x[t]) > 1e-4f || abs(sim.player.y - ref.y[t]) > 1e-4f) return false
        }
        val a = sim.snapshot(); val b = ref.snaps[end]
        return abs(a.vy - b.vy) < 1e-3f && abs(a.vxCur - b.vxCur) < 1e-4f && a.grounded == b.grounded && a.cutAllowed == b.cutAllowed &&
            abs(a.autoTimer - b.autoTimer) < 1e-4f && a.airJumps == b.airJumps && abs(a.scale - b.scale) < 1e-4f
    }


    // ------------------------------------------------------------------ ortalama

    /**
     * Doğal çözümü zıplama zıplama, sırayla, her zıplamanın zamanlama penceresinin ortasına kaydırır. Her kaydırmadan
     * sonra bölümün devamı yeniden oynatılır; bitirmeyen kaydırma geri alınır. Dönen çözüm düz giriş listesidir ve
     * baştan oynatıldığında bölümü bitirir.
     */
    fun center(level: LevelData, toggles: IntArray): IntArray {
        var cur = toggles
        val sim = newSim(level)
        try {
            val ref = reference(sim, cur) ?: return cur
            var plan = planOf(cur, ref)
            // Taban doğrulaması: plan (hiç kaydırılmadan) kapalı çevrim oyuncuyla aynı girişleri üretmeli; üretmiyorsa
            // bütün dokunuşlar zamana bağlanır (bu durumda plan, düz giriş listesinin birebir karşılığıdır).
            var forceTimed = false
            run {
                val test = Ref()
                sim.restore(ref.snaps[0])
                val bot = Bot(plan, 0, reflex = false)
                if (!record(sim, test) { bot.at(it) } || !togglesOf(test.inputs).contentEquals(cur)) { forceTimed = true; plan = plan.allTimed() }
            }
            var j = 0
            while (j < plan.size) {
                val sl = slackOf(sim, ref, plan, cur, j)
                val c = ((sl[1] - sl[0]) / 2f).toInt().coerceIn(-12, 12)
                val h = if (plan.hold[j] >= NONE) 0 else {
                    // tutma süresi: iki yanı da sınırlıysa ortala; tek yanı serbestse sınırdan uzaklaş
                    val short = sl[2]; val long = sl[3]
                    when {
                        short >= FREE_SIDE && long >= FREE_SIDE -> 0
                        short >= FREE_SIDE -> -min(4, max(0, 6 - long))
                        long >= FREE_SIDE -> min(4, max(0, 6 - short))
                        else -> ((long - short) / 2f).toInt().coerceIn(-6, 6)
                    }
                }
                // insanın basamayacağı kadar kısa dokunuş (1-4 tick) her durumda en az MIN_HOLD'a uzatılmaya çalışılır
                val raise = plan.hold[j] < MIN_HOLD
                if (c != 0 || h != 0 || raise) {
                    for (k in floatArrayOf(1f, 0.5f)) {
                        val ck = (c * k).toInt(); val hk = (h * k).toInt()
                        if (ck == 0 && hk == 0 && !(raise && k == 1f)) continue
                        val cand = plan.shifted(j, ck.toFloat())
                        if (plan.hold[j] < NONE) cand.hold[j] = max(MIN_HOLD, plan.hold[j] + hk)
                        // yalnızca bu dokunuştan sonrası yeniden oynatılır
                        val from = min(cur[2 * j], cur[2 * j] + ck).coerceAtLeast(0)
                        if (from >= ref.size) break
                        val saved = ref.tail(from)
                        sim.restore(ref.snaps[from])
                        ref.cut(from)
                        val bot = Bot(cand, j, reflex = false)
                        val ok = record(sim, ref) { bot.at(it) }
                        val tg = if (ok) togglesOf(ref.inputs) else null
                        if (tg == null || (tg.size + 1) / 2 != plan.size) { ref.restore(from, saved); continue } // güvenli değil: geri al
                        cur = tg
                        plan = planOf(cur, ref).let { if (forceTimed) it.allTimed() else it }
                        break
                    }
                }
                j++
            }
        } finally { sim.dispose() }
        return if (replays(level, cur)) cur else toggles
    }

    /**
     * Tutma penceresi: insanın basabileceği sürelerden ([MIN_HOLD] ve üstü) kaç tanesi bölümü geçiriyor. Bir yanı
     * serbest diye pencere sınırsız sayılmaz; yalnızca kısa dokunuşla geçilen zıplama dar pencere olarak görünür.
     */
    private fun holdWindow(h0: Int, sl: IntArray): Int {
        if (h0 >= NONE) return FREE
        val lo = if (sl[2] >= FREE_SIDE) MIN_HOLD else max(MIN_HOLD, h0 - sl[2])
        val hi = if (sl[3] >= FREE_SIDE) h0 + STEPS.last() else h0 + sl[3]
        return (hi - lo + 1).coerceIn(0, FREE)
    }

    /** Çözücü çözümünü insanın oynayacağı çözüme çevirir: doğal dokunuşlar + pencere ortası. */
    fun humanize(level: LevelData, toggles: IntArray): IntArray = center(level, normalize(level, toggles))

    // ------------------------------------------------------------------ ölçüm

    /** Yalnızca zamanlama payları: (en dar zıplama penceresi, en dar tutma penceresi). Çözüm bitirmiyorsa null. */
    fun windowsOf(level: LevelData, toggles: IntArray): Pair<Int, Int>? {
        val sim = newSim(level)
        try {
            val ref = reference(sim, toggles) ?: return null
            val plan = planOf(toggles, ref)
            var w = FREE; var h = FREE
            for (j in 0 until plan.size) {
                val sl = slackOf(sim, ref, plan, toggles, j)
                w = min(w, sl[0] + sl[1] + 1); h = min(h, holdWindow(plan.hold[j], sl))
            }
            return w to h
        } finally { sim.dispose() }
    }

    /**
     * Bölümü ölçer. Çözüm bölümü bitirmiyorsa null.
     * [gate]: (en dar pencere, en dar tutma) yeterli değilse false döndürür; o zaman gürültülü oynatma yapılmaz.
     * [stopEarly]: ilk [quick] denemeden sonraki kaba zorluk puanı verilir; true dönerse ölçüm orada kesilir.
     */
    fun measure(level: LevelData, toggles: IntArray, trials: Int = 36, quick: Int = 12,
                gate: ((Int, Int) -> Boolean)? = null, stopEarly: ((Float) -> Boolean)? = null): LevelMetrics? {
        val sim = newSim(level)
        try {
            val ref = reference(sim, toggles) ?: return null
            val plan = planOf(toggles, ref)
            val sl = Array(plan.size) { slackOf(sim, ref, plan, toggles, it) }
            val windows = IntArray(sl.size) { min(FREE, sl[it][0] + sl[it][1] + 1) }
            val holds = IntArray(sl.size) { holdWindow(plan.hold[it], sl[it]) }
            val jt = IntArray(plan.size) { toggles[2 * it] }
            if (gate != null && !gate(windows.minOrNull() ?: FREE, holds.minOrNull() ?: FREE)) {
                return LevelMetrics(ref.endTick, plan.pressX.copyOf(), jt, windows, holds, floatArrayOf(0.004f, 0.004f, 0.004f), FloatArray(0), played = false)
            }
            val rng = Random(level.id * 1_000_003L + 17)
            val done = IntArray(SIGMAS.size)
            val deathX = ArrayList<Float>()
            fun run(n0: Int, n1: Int) {
                for ((si, sigma) in SIGMAS.withIndex()) for (trial in n0 until n1) {
                    val bot = Bot(jitter(plan, sigma, rng), 0)
                    sim.restore(ref.snaps[0])
                    while (true) {
                        sim.step(bot.at(sim))
                        if (sim.status == SimStatus.COMPLETE) { done[si]++; break }
                        if (sim.status != SimStatus.RUNNING) { if (si == 1) deathX.add(sim.player.x); break }
                        if (sim.tick > ref.endTick + 900) break
                    }
                }
            }
            fun metrics(n: Int) = LevelMetrics(ref.endTick, plan.pressX.copyOf(), jt, windows, holds,
                FloatArray(SIGMAS.size) { (done[it] + 0.5f) / (n + 1f) }, deathX.toFloatArray())
            val q = min(quick, trials)
            run(0, q)
            if (q < trials && stopEarly != null && stopEarly(metrics(q).score)) return metrics(q)
            run(q, trials)
            return metrics(trials)
        } finally { sim.dispose() }
    }

    /**
     * İnsan zamanlama hatası modeli: her dokunuşun başlangıcı N(0, σ) tick kayar; basılı tutma süresi ayrıca
     * N(0, σ/2) kadar değişir (en az [MIN_HOLD] tick).
     */
    private fun jitter(plan: Plan, sigma: Float, rng: Random): Plan {
        val out = plan.copy()
        for (i in 0 until plan.size) {
            val d = (rng.nextGaussian() * sigma).toFloat()
            if (plan.timed[i]) out.pressT[i] = plan.pressT[i] + d.roundToInt() else out.pressX[i] = plan.pressX[i] + d * plan.dx[i]
            if (plan.hold[i] < NONE) out.hold[i] = max(MIN_HOLD, plan.hold[i] + (rng.nextGaussian() * sigma * 0.5f).roundToInt())
        }
        return out
    }

    /** Teşhis: her dokunuşun "tick+tutma e(rken) g(eç) k(ısa) u(zun)" payları ('*' = havada, 'z' = zamana bağlı). */
    fun debug(level: LevelData, toggles: IntArray): String {
        val sim = newSim(level)
        try {
            val ref = reference(sim, toggles) ?: return "çözüm bölümü bitirmiyor"
            val plan = planOf(toggles, ref)
            return (0 until plan.size).joinToString(" ") { j ->
                val sl = slackOf(sim, ref, plan, toggles, j)
                "[${toggles[2 * j]}+${if (plan.hold[j] >= NONE) "∞" else plan.hold[j].toString()}${if (plan.adaptive[j]) "" else "*"}${if (plan.timed[j]) "z" else ""} e${sl[0]} g${sl[1]} k${sl[2]} u${sl[3]}]"
            }
        } finally { sim.dispose() }
    }
}
