package com.besaniyelik.dunya.level

import com.besaniyelik.dunya.sim.*
import java.util.Random
import kotlin.math.abs

class ForgeReport(
    val levelId: Int,
    val attempts: Int,
    val nodes: Int,
    val solveTicks: Int,
    val fair: Boolean,
    val fairShift: Int,
    val coins: Int,
    val note: String,
)

class ForgeResult(val level: LevelData, val report: ForgeReport)

/**
 * Bölüm dökümhanesi: taslağı üretir, katı modda çözer, çözüm yörüngesi üzerine altın/yıldız/parça yerleştirir,
 * ardından normal modda baştan oynatarak (ve zamanlamayı ±tick kaydırarak) doğrular.
 */
object LevelForge {
    class TrajPoint(val x: Float, val y: Float, val grounded: Boolean, val sign: Int, val tick: Int)

    fun forge(levelId: Int, maxAttempts: Int = 24, log: (String) -> Unit = {}): ForgeResult {
        var fallback: ForgeResult? = null
        var totalNodes = 0
        for (attempt in 0 until maxAttempts) {
            val drop = when { attempt >= 16 -> 2; attempt >= 9 -> 1; else -> 0 }
            val draft = LevelGenerator.generate(levelId, attempt, drop)
            val res = trySolve(draft.data)
            totalNodes += res.nodes
            if (!res.success) { log("  #$levelId deneme $attempt: çözülemedi (${res.reason})"); continue }
            val toggles = res.toggles
            val traj = trace(draft.data, toggles) ?: run { log("  #$levelId deneme $attempt: tekrar oynatma tutmadı"); null } ?: continue
            val withItems = placeItems(draft, traj, toggles, Random(draft.data.seed + 99))
            if (!verify(withItems, toggles)) { log("  #$levelId deneme $attempt: toplanabilir doğrulaması başarısız"); continue }
            val kind = withItems.kind
            val shift = if (kind == LevelKind.NORMAL || kind == LevelKind.TUTORIAL) 2 else 1
            val fair = isFair(withItems, toggles, shift)
            val report = ForgeReport(levelId, attempt + 1, totalNodes, res.ticks, fair, shift, withItems.coins.size, if (fair) "ok" else "dar zamanlama")
            val result = ForgeResult(withItems, report)
            if (fair) return result
            log("  #$levelId deneme $attempt: zamanlama penceresi dar (±$shift)")
            if (fallback == null) fallback = result
        }
        return fallback ?: throw IllegalStateException("Bölüm $levelId üretilemedi")
    }

    fun trySolve(level: LevelData): SolveResult {
        val sim = Simulation(level, SimConfig(solver = true, powerups = false, strict = true))
        try {
            return LevelSolver(maxNodes = 350_000).solve(sim)
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

    fun isFair(level: LevelData, toggles: IntArray, shift: Int): Boolean {
        for (s in intArrayOf(shift, -shift)) {
            val sim = Simulation(level, SimConfig(hearts = 1, powerups = false))
            val ok = try { LevelSolver.replay(sim, toggles, s) } finally { sim.dispose() }
            if (!ok) return false
        }
        return true
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
