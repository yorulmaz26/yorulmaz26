package com.besaniyelik.dunya.endless

import com.besaniyelik.dunya.level.*
import com.besaniyelik.dunya.sim.*
import java.util.Random
import kotlin.math.min

/**
 * Sonsuz Dünya içerik üreticisi. Her parça bir 5 saniyelik kural aşamasıdır. Parça k'nin içeriği yalnızca
 * (tohum, k) çiftine bağlıdır; böylece dünya deterministiktir ve çözücüyle test edilebilir.
 */
class EndlessGenerator(
    val seed: Long,
    /** Oyuncunun bölümlü modda öğrendiği mekanikler; önce bunlar kullanılır. */
    private val learned: Set<Mechanic> = Mechanic.entries.toSet(),
) : ContentProvider {
    var nextX = 0f
        private set
    var chunkIndex = 0
        private set
    private val plan = ArrayList<List<Mechanic>>()
    private val planRng = Random(seed xor 0x5DEECE66DL)

    fun difficultyAt(k: Int): Float = min(1f, k / 36f)
    fun speedAt(k: Int): Float = 1f + min(0.4f, k * 0.011f)

    /** Parça k için kural seti (önceden planlanır, böylece sınır tamponları doğru kurulur). */
    fun rulesFor(k: Int): List<Mechanic> {
        while (plan.size <= k) plan.add(fit(planNext(plan.size), plan.size))
        return plan[k]
    }

    /** Hayalet / Kayan Platform kuralı, nesnesinin sığmayacağı kadar kısa parçalardan çıkarılır (bkz. LevelGenerator.fitRules). */
    private fun fit(rules: List<Mechanic>, k: Int): List<Mechanic> {
        if (k == 0) return rules
        var r = rules
        val prev = RuleEffects.of(plan[k - 1])
        for ((m, p) in listOf(Mechanic.GHOST to Pattern.GHOST_BRIDGE, Mechanic.SHIFTING to Pattern.SHIFT_BRIDGE)) {
            if (m !in r) continue
            val e = RuleEffects.of(r)
            if (LevelGenerator.zoneLength(prev, e, difficultyAt(k), false, false, speedAt(k)) < Patterns.minZone(p, e)) r = (r - m).ifEmpty { listOf(Mechanic.NORMAL) }
        }
        return r
    }

    private fun planNext(k: Int): List<Mechanic> {
        if (k == 0) return listOf(Mechanic.NORMAL)
        val prev = plan.getOrNull(k - 1) ?: listOf(Mechanic.NORMAL)
        val known = Mechanic.learnOrder.filter { it != Mechanic.NORMAL && it in learned }
        // Öğrenilmemiş mekanikler zamanla (kısa öğreticiyle) eklenir
        val unknown = Mechanic.learnOrder.filter { it != Mechanic.NORMAL && it !in learned }
        val introduced = unknown.take(((k - 4) / 5).coerceAtLeast(0))
        val pool = (known + introduced).ifEmpty { listOf(Mechanic.REVERSE_GRAVITY) }
        val d = (3 + k / 6).coerceAtMost(10)
        val maxCombo = when { k < 8 -> 1; k < 20 -> 2; else -> 3 }
        repeat(30) {
            val set = mutableListOf(pool[planRng.nextInt(pool.size)])
            val want = 1 + (if (planRng.nextFloat() < 0.15f + k * 0.015f) 1 else 0) + (if (planRng.nextFloat() < k * 0.008f) 1 else 0)
            var g = 0
            while (set.size < min(want, maxCombo) && g++ < 15) {
                val m = pool[planRng.nextInt(pool.size)]
                if (m !in set && RuleCompatibility.isValid(set + m, d)) set.add(m)
            }
            if (RuleCompatibility.isValid(set, d) && set.toSet() != prev.toSet()) return set
        }
        return if (prev == listOf(Mechanic.NORMAL)) listOf(pool.first()) else listOf(Mechanic.NORMAL)
    }

    override fun ensure(sim: Simulation, untilX: Float) {
        while (nextX < untilX) generateChunk(sim)
    }

    private fun generateChunk(sim: Simulation) {
        val k = chunkIndex++
        val rng = Random(seed * 1_000_003L + k * 7919L)
        val rules = rulesFor(k)
        val e = RuleEffects.of(rules)
        val prevE = if (k > 0) RuleEffects.of(rulesFor(k - 1)) else e
        val nextE = RuleEffects.of(rulesFor(k + 1))
        val mul = speedAt(k)
        val sp = Physics.realSpeed(e, mul)
        val x0 = nextX
        val len = Physics.RULE_PERIOD * sp * (if (k == 0) 1.3f else 1f)
        val x1 = x0 + len
        val b = LevelBuilder()
        val sign = e.gravitySign
        val needCeil = e.gravitySign < 0 || prevE.gravitySign < 0 || nextE.gravitySign < 0
        if (k == 0) b.ground(1, -12f, 0f)
        if (sign > 0) { if (needCeil) b.ground(-1, x0, x1) } else b.ground(1, x0, x1)
        val t = difficultyAt(k)
        val bufIn = if (k == 0) 9f else LevelGenerator.bufferIn(prevE, e, sp, t, speedMul = mul)
        val bufOut = LevelGenerator.bufferOut(e, sp, mul)
        b.ground(sign, x0, x0 + bufIn)
        b.ground(sign, x1 - bufOut, x1)
        val ctx = PatternCtx(b, rng, e, t, mul)
        val zx0 = x0 + bufIn
        val zx1 = x1 - bufOut
        val general = listOf(Pattern.GAP, Pattern.SPIKES, Pattern.STEP, Pattern.FLOATING, Pattern.SAW, Pattern.MOVING_SAW,
            Pattern.MOVING_PLATFORM, Pattern.SPRING_WALL, Pattern.BOOST_GAP, Pattern.OVERHEAD, Pattern.SPIKE_GAP, Pattern.STAIRS)
        if (zx1 - zx0 < 4f) b.ground(sign, zx0, zx1)
        else LevelGenerator.fillPhase(b, ctx, zx0, zx1, Patterns.dedicated(e), { ctx.lerp(3.2f, 1.4f, 1.0f) * ctx.rnd(0.8f, 1.3f) }, 0.6f) { avail, tried ->
            general.filter { it !in tried && Patterns.applicable(it, e) && Patterns.minLength(it) <= avail && (it != Pattern.BOOST_GAP || avail >= 15f) }
                .let { if (it.isEmpty()) null else it[rng.nextInt(it.size)] }
        }
        b.finalizeGround()

        // Toplanabilirler: desenlerin altın ipuçları, ara sıra yıldız ve güçlendirme
        val coins = b.coinHints.filter { c -> b.hazards.none { h -> kotlin.math.abs(h.x - c.x) < 1f && kotlin.math.abs(h.y - c.y) < 1f } }
        val stars = ArrayList<Vec>()
        if (k > 0 && k % 3 == 0) stars.add(Vec(x0 + bufIn * 0.5f, b.wy(sign, 2.0f)))
        val powerups = ArrayList<PowerupDef>()
        if (k > 0 && k % 4 == 2) {
            val types = PowerupType.entries.filter { it != PowerupType.CHECKPOINT_GUARD && it != PowerupType.STAR_FINDER }
            powerups.add(PowerupDef(x1 - bufOut * 0.5f, b.wy(sign, 0.45f), types[rng.nextInt(types.size)]))
        }
        sim.addContent(b.solids, b.hazards, b.pads, coins, stars, null, powerups, listOf(PhaseDef(r2(x0), rules)), emptyList())
        sim.phaseSpeed.add(mul)
        nextX = x1
    }
}

/** Sonsuz Dünya ve bölümlü mod puanlama kuralları. */
object ScoreCalculator {
    /** Sonsuz mod puanı: mesafe, süre, toplananlar ve atlatılan kural zorluğu dengeli biçimde. */
    fun endless(distance: Float, seconds: Float, coins: Int, stars: Int, ruleBonus: Int): Int {
        val d = (distance * 2f).toInt()
        val time = (seconds * 3f).toInt()
        return d + time + coins * 5 + stars * 150 + ruleBonus
    }

    /** Hile/bozulma kontrolü: puan, süre ve mesafeyle fiziksel olarak tutarlı olmalı. */
    fun plausible(score: Int, seconds: Float, distance: Float): Boolean {
        if (score < 0 || seconds < 0f || distance < 0f) return false
        val maxSpeed = Physics.RUN_SPEED * 1.4f * Physics.FAST_SCALE * Physics.BOOST_MUL
        if (distance > seconds * maxSpeed + 20f) return false
        val maxScore = distance * 2f + seconds * 3f + seconds * 12f * 5f + (seconds / 10f + 2f) * 150f + (seconds / 4f + 2f) * 25f * 3f * (1f + seconds / 4f * 0.05f)
        return score <= maxScore + 50f
    }

    /** Bölüm yıldızı (1-3). */
    fun levelStars(completed: Boolean, starsCollected: Int, coins: Int, coinTarget: Int, deaths: Int): Int {
        if (!completed) return 0
        var s = 1
        if (starsCollected >= 2 || (coinTarget > 0 && coins >= coinTarget)) s = 2
        if (starsCollected >= 3 && deaths <= 1) s = 3
        return s
    }

    fun levelXp(difficulty: Int, stars: Int, firstClear: Boolean): Int = (15 + difficulty * 3 + stars * 8) * (if (firstClear) 2 else 1)

    fun endlessXp(score: Int): Int = (score / 40).coerceIn(5, 400)
}
