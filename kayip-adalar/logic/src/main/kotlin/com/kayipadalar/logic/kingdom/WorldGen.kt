package com.kayipadalar.logic.kingdom

import java.util.Random
import kotlin.math.pow
import kotlin.math.roundToInt

/** Ana adanın çevresindeki halkalara ada yerleştirir. Uzak halkalar daha nadir türler ve güçlü korsanlar içerir. */
object WorldGen {
    private val pools = listOf(
        listOf("grass", "forest", "rocky", "swamp"),
        listOf("grass", "forest", "desert", "mountain", "swamp", "snow"),
        listOf("desert", "mountain", "snow", "gold", "crystal", "ice", "forest"),
        listOf("gold", "crystal", "ice", "volcano", "mountain", "snow"),
        listOf("volcano", "mystic", "crystal", "gold", "ice"),
    )

    fun generate(cfg: KingdomConfig, rings: Int, seed: Long = 20260601): List<IslandState> {
        val rnd = Random(seed)
        val out = ArrayList<IslandState>()
        var id = 1
        for (ring in 1..rings) {
            val n = 2 + ring * 2
            val pool = pools[(ring - 1).coerceAtMost(pools.size - 1)]
            val offset = rnd.nextFloat() * 360f
            for (k in 0 until n) {
                val type = if (ring == 1 && k == 0) "grass" else pool[rnd.nextInt(pool.size)]
                val guard = (22 * 2.7.pow(ring - 1) * (0.8 + 0.4 * rnd.nextDouble()) * (1 + k * 0.08)).roundToInt()
                out.add(IslandState(id++, type, ring, (offset + k * 360f / n) % 360f, guard = guard))
            }
        }
        return out
    }
}
