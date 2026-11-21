package com.besaniyelik.dunya.level

import com.besaniyelik.dunya.sim.*

class SolveResult(
    val success: Boolean,
    /** Giriş durumunun değiştiği tick'ler (başlangıç: bırakılmış). */
    val toggles: IntArray,
    val ticks: Int,
    val nodes: Int,
    val reason: String,
)

/** Bir kayıt edilmiş girdi dizisini tick bazında üretir. */
class InputTrack(private val toggles: IntArray, private val shift: Int = 0) {
    private var idx = 0
    private var state = false
    fun at(tick: Int): Boolean {
        while (idx < toggles.size && toggles[idx] + shift <= tick) { state = !state; idx++ }
        return state
    }
}

/**
 * Çözülebilirlik doğrulayıcı. Gerçek Box2D simülasyonu üzerinde, anlık görüntüleri geri yükleyerek
 * derinlik öncelikli arama yapar. Her [decision] tick'te giriş basılı/bırakılmış olarak dallanır;
 * nicemlenmiş durumlar tekrar ziyaret edilmez.
 */
class LevelSolver(
    private val maxNodes: Int = 500_000,
    private val decision: Int = 4,
    private val maxTicks: Int = 60 * 300,
) {
    fun solve(sim: Simulation, untilX: Float = Float.MAX_VALUE): SolveResult {
        val visited = HashSet<Long>(1 shl 16)
        // first: bu düğümde önce denenecek giriş. Normalde mevcut giriş sürdürülür; karakter bir duvara dayanmışsa
        // önce giriş değiştirilir (çözümler duvar dibinde oyalanmasın).
        class Frame(val snap: SimSnapshot, val input: Boolean, var next: Int, val first: Boolean)
        val stack = ArrayList<Frame>()
        stack.add(Frame(sim.snapshot(), false, 0, false))
        var nodes = 0
        var bestX = 0f
        while (stack.isNotEmpty()) {
            val f = stack[stack.size - 1]
            if (f.next >= 2) { stack.removeAt(stack.size - 1); continue }
            val choice = f.next++
            // Önce mevcut girişi sürdür, sonra değiştir
            val input = if (choice == 0) f.first else !f.first
            sim.restore(f.snap)
            for (i in 0 until decision) {
                sim.step(input)
                if (sim.failed || sim.status == SimStatus.COMPLETE) break
            }
            nodes++
            if (sim.player.x > bestX) bestX = sim.player.x
            if (sim.status == SimStatus.COMPLETE || (sim.player.x >= untilX && !sim.failed)) {
                return SolveResult(true, buildToggles(stack.drop(1).map { it.input } + input), sim.tick, nodes, "ok")
            }
            if (sim.failed) continue
            if (nodes >= maxNodes) return SolveResult(false, IntArray(0), sim.tick, nodes, "düğüm sınırı (en iyi x=${"%.1f".format(bestX)})")
            if (sim.tick > maxTicks) continue
            val k = key(sim, input)
            if (!visited.add(k)) continue
            // Ters Kontrol'de doğal oyun "basılı tut (yerde kal), zıplamak için bırak" biçimindedir: önce basılı tutmak denenir;
            // böylece çözümler sürekli kendiliğinden zıplayan, zamanlamaya aşırı duyarlı çözümler olmaz.
            val first = when {
                sim.effects.reverseControl -> true
                sim.player.blocked > 0.1f && sim.player.grounded -> !input
                else -> input
            }
            stack.add(Frame(sim.snapshot(), input, 0, first))
        }
        return SolveResult(false, IntArray(0), 0, nodes, "çözüm yok (en iyi x=${"%.1f".format(bestX)})")
    }

    private fun buildToggles(inputs: List<Boolean>): IntArray {
        val out = ArrayList<Int>()
        var state = false
        for ((i, b) in inputs.withIndex()) {
            if (b != state) { out.add(i * decision); state = b }
        }
        return out.toIntArray()
    }

    private fun key(sim: Simulation, input: Boolean): Long {
        val p = sim.player
        var h = 1469598103934665603L
        fun mix(v: Int) { h = (h xor v.toLong()) * 1099511628211L }
        mix((p.x * 16f).toInt())
        mix((p.y * 16f).toInt())
        mix((p.vy * 2f).toInt())
        mix((p.vxCur * 4f).toInt())
        mix((sim.time * 12f).toInt())
        mix(if (input) 1 else 0)
        mix((p.scale * 10f).toInt())
        mix(if (p.cutAllowed) 1 else 0)
        mix((p.boost * 5f).toInt())
        return h
    }

    companion object {
        /** Kaydedilmiş girişi yeni bir simülasyonda baştan oynatır. */
        fun replay(sim: Simulation, toggles: IntArray, shift: Int = 0, maxTicks: Int = 60 * 300, onTick: ((Simulation) -> Unit)? = null): Boolean {
            val track = InputTrack(toggles, shift)
            while (sim.tick < maxTicks) {
                sim.step(track.at(sim.tick))
                onTick?.invoke(sim)
                if (sim.status == SimStatus.COMPLETE) return true
                if (sim.failed || sim.status == SimStatus.DYING || sim.status == SimStatus.GAME_OVER) return false
            }
            return false
        }
    }
}
