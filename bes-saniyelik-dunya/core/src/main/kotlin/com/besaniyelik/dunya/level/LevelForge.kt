package com.besaniyelik.dunya.level

import kotlin.math.max
import com.besaniyelik.dunya.sim.*
import java.util.Random
import kotlin.math.abs

class ForgeReport(
    val levelId: Int,
    val attempts: Int,
    val nodes: Int,
    val solveTicks: Int,
    /** Ölçülen zorluk hedef bandında ve bütün zamanlama pencereleri yeterince geniş mi? */
    val fair: Boolean,
    val target: Float,
    val measured: Float,
    val minWindow: Int,
    val minHold: Int,
    val pComplete: FloatArray,
    val coins: Int,
    val note: String,
)

class ForgeResult(val level: LevelData, val report: ForgeReport)

/**
 * Bölüm dökümhanesi. Her aday bölüm için:
 *  1. taslak üretilir ve katı modda çözücüyle çözülür,
 *  2. çözüm insanın oynayacağı biçime çevrilir (doğal dokunuşlar, pencere ortası),
 *  3. yörünge üzerine altın/yıldız/parça yerleştirilir ve baştan oynatılarak doğrulanır,
 *  4. bölüm "insan gibi" (zamanlama hatalı) oynatılarak zorluğu ÖLÇÜLÜR,
 *  5. ölçülen zorluk bölümün hedef zorluk bandına düşüyor ve hiçbir zıplama kare hassasiyeti istemiyorsa kabul edilir;
 *     aksi halde desen yoğunluğu ölçüme göre ayarlanıp yeni aday üretilir.
 */
object LevelForge {
    class TrajPoint(val x: Float, val y: Float, val grounded: Boolean, val sign: Int, val tick: Int)

    private class Candidate(val result: ForgeResult, val err: Float, val windowsOk: Boolean, val deficit: Int, val toggles: IntArray) {
        /**
         * Küçük olan daha iyi: pencere ihlali her zaman zorluk sapmasından kötüdür. Penceresi dar adaylar ölçülmez
         * (puanları yoktur); kendi aralarında pencere açığına göre sıralanır.
         */
        val penalty: Float get() = if (windowsOk) kotlin.math.abs(err) else 5f + deficit
    }

    // Üretim süresi dökümü (teşhis)
    val tSolve = java.util.concurrent.atomic.AtomicLong(); val tNormalize = java.util.concurrent.atomic.AtomicLong()
    val tCenter = java.util.concurrent.atomic.AtomicLong(); val tMeasure = java.util.concurrent.atomic.AtomicLong()

    /** Kabul bandı: hedefin iki yanında izin verilen ölçülen zorluk sapması. */
    fun tolerance(target: Float): Float = 0.55f + 0.05f * target

    /** [seedOffset]: deneme tohumlarını kaydırır (aykırı çıkan bir bölümü farklı adaylarla yeniden üretmek için). */
    fun forge(levelId: Int, maxAttempts: Int = 30, seedOffset: Int = 0, log: (String) -> Unit = {}): ForgeResult {
        val target = GameContent.targetDifficulty(levelId)
        val tol = tolerance(target)
        val needWin = LevelAnalyzer.minWindowFor(target)
        val needHold = LevelAnalyzer.minHoldFor(target)
        val kind = GameContent.kindOf(levelId)
        val nodeCap = if (kind == LevelKind.FINAL || kind == LevelKind.GRAND_FINAL) 350_000 else 160_000
        var best: Candidate? = null
        var totalNodes = 0
        var tShift = 0f
        var unsolved = 0
        for (attempt in 0 until maxAttempts) {
            // Uzun süredir tam isabet yoksa, pencereleri uygun ve hedefe yeterince yakın en iyi adayla yetin
            val b0 = best
            if (attempt >= 14 && b0 != null && b0.windowsOk && kotlin.math.abs(b0.err) <= tol * 1.6f) break
            val draft = LevelGenerator.generate(levelId, attempt + seedOffset, tShift)
            val ts = System.nanoTime()
            val res = trySolve(draft.data, nodeCap)
            tSolve.addAndGet(System.nanoTime() - ts)
            totalNodes += res.nodes
            if (!res.success) {
                log("  #$levelId deneme $attempt: çözülemedi (${res.reason})")
                if (++unsolved % 2 == 0) tShift = (tShift - 0.05f).coerceAtLeast(-0.6f)
                continue
            }
            // çözümü insanın oynayacağı biçime çevir: doğal dokunuşlar + pencere ortası
            var t0 = System.nanoTime()
            val natural = LevelAnalyzer.normalize(draft.data, res.toggles)
            tNormalize.addAndGet(System.nanoTime() - t0); t0 = System.nanoTime()
            val toggles = LevelAnalyzer.center(draft.data, natural)
            tCenter.addAndGet(System.nanoTime() - t0)
            val traj = trace(draft.data, toggles) ?: run { log("  #$levelId deneme $attempt: tekrar oynatma tutmadı"); null } ?: continue
            val withItems = placeItems(draft, traj, toggles, Random(draft.data.seed + 99))
            if (!verify(withItems, toggles)) { log("  #$levelId deneme $attempt: toplanabilir doğrulaması başarısız"); continue }
            if (!finishOk(withItems, toggles)) { log("  #$levelId deneme $attempt: bitiş düzlüğü doğrulaması başarısız"); continue }
            // 3) ölçüm: pencereler yetersizse ya da kaba puan banttan çok uzaksa erken kesilir
            t0 = System.nanoTime()
            val m = LevelAnalyzer.measure(withItems, toggles,
                gate = { w, h -> w >= needWin && h >= needHold },
                stopEarly = { rough -> kotlin.math.abs(rough - target) > tol + 1.3f })
            tMeasure.addAndGet(System.nanoTime() - t0)
            if (m == null) continue
            val windowsOk = m.minWindow >= needWin && m.minHold >= needHold
            val err = m.score - target
            val fair = windowsOk && kotlin.math.abs(err) <= tol
            val note = when {
                fair -> "ok"
                !windowsOk -> "dar pencere (${m.minWindow}/${m.minHold} tick)"
                err > 0 -> "hedeften zor"
                else -> "hedeften kolay"
            }
            val report = ForgeReport(levelId, attempt + 1, totalNodes, traj.last().tick, fair, target, m.score, m.minWindow, m.minHold, m.pComplete, withItems.coins.size, note)
            val deficit = max(0, needWin - m.minWindow) + max(0, needHold - m.minHold)
            val cand = Candidate(ForgeResult(withItems, report), err, windowsOk, deficit, toggles)
            if (best == null || cand.penalty < best.penalty) best = cand
            if (fair) return cand.result
            log("  #$levelId deneme $attempt: ölçülen %.1f / hedef %.1f, pencere %d, tutma %d, t%+.2f -> %s".format(m.score, target, m.minWindow, m.minHold, tShift, note))
            if (!windowsOk) for (jj in 0 until m.jumps) if (m.windows[jj] < needWin || m.holdWindows[jj] < needHold) {
                val fx = m.jumpX[jj]
                val f = withItems.features.lastOrNull { it.x <= fx + 0.5f }
                val ph = withItems.phases.lastOrNull { it.x <= fx }
                log("    DAR %s / %s w%d h%d dx%.1f".format(f?.p ?: "-", ph?.rules?.joinToString("+") { it.name.take(5) } ?: "-", m.windows[jj], m.holdWindows[jj], fx - (f?.x ?: fx)))
            }
            // Desen yoğunluğunu ölçüme göre ayarla: zor/dar çıktıysa azalt, kolay çıktıysa artır
            val push = if (!windowsOk) -(0.04f + 0.015f * deficit).coerceAtMost(0.12f) else (-0.08f * err.coerceIn(-3f, 3f))
            tShift = (tShift + push).coerceIn(-0.6f, 0.9f)
        }
        val b = best ?: throw IllegalStateException("Bölüm $levelId üretilemedi")
        val r = b.result.report
        var measured = r.measured; var pc = r.pComplete
        if (!b.windowsOk) {
            // Penceresi dar aday ölçülmemişti: rapora gerçek puan yazılsın diye kapısız ölçülür
            LevelAnalyzer.measure(b.result.level, b.toggles)?.let { measured = it.score; pc = it.pComplete }
        }
        return ForgeResult(b.result.level, ForgeReport(levelId, r.attempts, totalNodes, r.solveTicks, false, r.target, measured, r.minWindow, r.minHold, pc, r.coins, r.note))
    }

    /**
     * Dürüst (katı) çözücü. Önce 4 tick'lik karar ızgarasıyla aranır; çözüm çıkmazsa 2 tick'lik ince ızgarayla bir kez
     * daha denenir: dar ama insan için geçilebilir (6-8 tick paylı) engeller kaba ızgaraya denk gelmeyebilir.
     */
    fun trySolve(level: LevelData, maxNodes: Int = 350_000): SolveResult {
        val coarse = solveWith(level, maxNodes, 4)
        if (coarse.success || !coarse.reason.startsWith("çözüm yok")) return coarse
        val fine = solveWith(level, maxNodes, 2)
        return if (fine.success) fine else coarse
    }

    private fun solveWith(level: LevelData, maxNodes: Int, decision: Int): SolveResult {
        val sim = Simulation(level, SimConfig(solver = true, powerups = false, strict = true))
        try {
            return LevelSolver(maxNodes = maxNodes, decision = decision).solve(sim)
        } finally { sim.dispose() }
    }

    /**
     * Bitiş doğrulaması: çözüm oynatılır, son kural çizgisinden sonra hiçbir tuşa basılmaz. Karakter kapıya Normal
     * Dünya'da, normal boyda ve yerde varmalıdır.
     */
    fun finishOk(level: LevelData, toggles: IntArray): Boolean {
        val line = level.goalX - Physics.FINISH_LEN
        val sim = Simulation(level, SimConfig(hearts = 1, powerups = false))
        try {
            val track = InputTrack(toggles)
            while (sim.tick < 60 * 300) {
                val inp = track.at(sim.tick) && sim.player.x < line
                sim.step(inp)
                if (sim.status == SimStatus.COMPLETE) {
                    val p = sim.player
                    return sim.effects == RuleEffects.DEFAULT && p.scale == 1f && p.y - p.hh < 0.6f
                }
                if (sim.status != SimStatus.RUNNING) return false
            }
            return false
        } finally { sim.dispose() }
    }

    fun trace(level: LevelData, toggles: IntArray): List<TrajPoint>? {
        val sim = Simulation(level, SimConfig(hearts = 1, powerups = false))
        val pts = ArrayList<TrajPoint>()
        try {
            val ok = LevelSolver.replay(sim, toggles) { s -> pts.add(TrajPoint(s.player.x, s.player.y, s.player.grounded, s.effects.gravitySign, s.tick)) }
            return if (ok) pts else null
        } finally { sim.dispose() }
    }

    fun verify(level: LevelData, toggles: IntArray): Boolean {
        val sim = Simulation(level, SimConfig(hearts = 1, powerups = false))
        try {
            val ok = LevelSolver.replay(sim, toggles)
            return ok && sim.starsCollected == level.stars.size && (level.piece == null || sim.pieceCollected)
        } finally { sim.dispose() }
    }

    private class Arc(val pts: List<TrajPoint>) {
        val apex: TrajPoint = pts.maxBy { upHeight(it) }
        val apexH get() = upHeight(apex)
    }

    private fun upHeight(p: TrajPoint): Float = if (p.sign > 0) p.y else Physics.CEIL - p.y

    fun placeItems(draft: LevelDraft, traj: List<TrajPoint>, toggles: IntArray, rng: Random): LevelData {
        val level = draft.data
        // Hava yayları
        val arcs = ArrayList<Arc>()
        var cur = ArrayList<TrajPoint>()
        for (p in traj) {
            if (!p.grounded) cur.add(p) else {
                if (cur.size >= 14) arcs.add(Arc(cur))
                cur = ArrayList()
            }
        }
        if (cur.size >= 14) arcs.add(Arc(cur))
        val coins = ArrayList<Vec>()
        fun addCoin(x: Float, y: Float) {
            if (x < 5f || x > level.goalX - 1f) return
            if (coins.any { abs(it.x - x) < 0.7f && abs(it.y - y) < 0.7f }) return
            coins.add(Vec(r2(x), r2(y)))
        }
        for (arc in arcs) {
            var lastX = -99f
            for (p in arc.pts) {
                if (p.x - lastX >= 0.95f) { addCoin(p.x, p.y); lastX = p.x }
            }
        }
        // Zemin koşuları: uzun düz bölümlere altın dizisi (az altınlı bölümlerde daha sık)
        for (pass in 0 until 2) {
        if (pass == 1 && coins.size >= 30) break
        var runStart = -1
        for (i in traj.indices) {
            val p = traj[i]
            if (p.grounded && runStart < 0) runStart = i
            if ((!p.grounded || i == traj.size - 1) && runStart >= 0) {
                val a = traj[runStart]; val b = traj[i]
                if (b.x - a.x > (if (pass == 0) 4.5f else 2.5f) && rng.nextFloat() < (if (pass == 0) 0.25f else 0.7f) && coins.size < 90) {
                    var x = a.x + 1f
                    var j = runStart
                    while (x < b.x - 1f) {
                        while (j < i && traj[j].x < x) j++
                        addCoin(traj[j].x, traj[j].y)
                        x += 0.95f
                    }
                }
                runStart = -1
            }
        }
        }
        // Yıldızlar: yolun %25, %55, %85'inde en yakın yüksek yayın tepesi
        val x0 = traj.first().x; val x1 = traj.last().x
        val used = HashSet<Arc>()
        val stars = ArrayList<Vec>()
        for (f in floatArrayOf(0.25f, 0.55f, 0.85f)) {
            val tx = x0 + (x1 - x0) * f
            val arc = arcs.filter { it !in used && it.apexH > 0.9f }.minByOrNull { abs(it.apex.x - tx) }
            if (arc != null && abs(arc.apex.x - tx) < (x1 - x0) * 0.2f) {
                used.add(arc); stars.add(Vec(r2(arc.apex.x), r2(arc.apex.y)))
            } else {
                val p = traj.minBy { abs(it.x - tx) }
                stars.add(Vec(r2(p.x), r2(p.y)))
            }
        }
        // Gizli koleksiyon parçası: kalan en yüksek yay
        var piece: Vec? = null
        if (GameContent.hasPiece(level.id)) {
            val arc = arcs.filter { it !in used }.maxByOrNull { it.apexH }
            piece = if (arc != null) Vec(r2(arc.apex.x), r2(arc.apex.y)) else traj[traj.size / 2].let { Vec(r2(it.x), r2(it.y)) }
        }
        val special = stars + listOfNotNull(piece)
        coins.removeAll { c -> special.any { abs(it.x - c.x) < 1.1f && abs(it.y - c.y) < 1.1f } }

        // Güçlendirmeler
        val powerups = ArrayList<PowerupDef>()
        if (level.id >= 12) {
            val n = when (level.kind) { LevelKind.FINAL, LevelKind.GRAND_FINAL -> 3; LevelKind.CHALLENGE -> 2; else -> if (rng.nextFloat() < 0.6f) 1 else 0 }
            val pool = listOf(PowerupType.SHIELD, PowerupType.MAGNET, PowerupType.DOUBLE_JUMP, PowerupType.INVINCIBLE,
                PowerupType.EXTRA_LIFE, PowerupType.COIN_MULTIPLIER, PowerupType.STAR_FINDER, PowerupType.CHECKPOINT_GUARD)
            val grounded = traj.filter { it.grounded && it.x > 12f && it.x < level.goalX - 8f }
            for (k in 0 until n) {
                if (grounded.isEmpty()) break
                val p = grounded[(grounded.size * (k + 0.5f) / n).toInt().coerceIn(0, grounded.size - 1)]
                if (special.any { abs(it.x - p.x) < 2f }) continue
                powerups.add(PowerupDef(r2(p.x), r2(p.y), pool[rng.nextInt(pool.size)]))
                coins.removeAll { abs(it.x - p.x) < 1f && abs(it.y - p.y) < 1f }
            }
        }
        val coinTarget = (coins.size * 0.7f).toInt()
        return level.copy(coins = coins, stars = stars, piece = piece, powerups = powerups,
            coinTarget = coinTarget, solution = toggles.toList(), solveTicks = traj.last().tick)
    }
}
