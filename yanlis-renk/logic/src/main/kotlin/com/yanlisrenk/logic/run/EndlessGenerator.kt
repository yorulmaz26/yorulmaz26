package com.yanlisrenk.logic.run

import com.yanlisrenk.logic.level.LevelGenerator
import com.yanlisrenk.logic.model.LevelData
import com.yanlisrenk.logic.model.Mechanic
import com.yanlisrenk.logic.model.Mechanic.*
import kotlin.random.Random

/**
 * Sonsuz mod: oyuncu ilerledikçe dengeli parçalar üretir. Her parça her başlangıç
 * rengi ve önceki kapı rengi için doğrulanır; böylece parça sınırında imkânsız durum oluşmaz.
 * [forced] haftalık mücadelede her parçada bulunacak mekaniklerdir.
 */
class EndlessGenerator(private val seed: Long, private val forced: List<Mechanic> = emptyList()) {
    private val unlockOrder = listOf(SAME, DIFF, RAINBOW, DOUBLE, MIX, SPEED, NONE, MEMORY, ROTATE, TIMED, NEXT, MIRROR,
        HIDDEN, LOCK, PREV, TRIPLE, AUTOSHIFT, SLIDE, STROOP, DECOY, WAVE, FLICKER, REPEAT, CHAIN, FOG, QUICK)

    fun speedFor(chunk: Int): Float = minOf(10.5f, 4.8f + chunk * 0.22f)

    fun chunk(index: Int): LevelData {
        val rng = Random(seed * 1_000_003L + index * 7919L)
        val available = unlockOrder.take(minOf(unlockOrder.size, 2 + index))
        val mechs = LinkedHashSet<Mechanic>()
        mechs.add(SAME)
        if (index > 0) mechs.add(DIFF)
        mechs.addAll(available.drop(2).shuffled(rng).take(minOf(3, 1 + index / 4)))
        mechs.addAll(forced)
        if (index < 2) mechs.removeAll(setOf(QUICK, FOG, CHAIN, WAVE))
        val d = minOf(1f, index / 30f)
        val p = LevelGenerator.Params(
            id = 10_000 + index, area = 0, index = 0, isFinal = false, d = d, speed = speedFor(index),
            reaction = 0.55f - 0.2f * d, gateCount = 8, mechanics = mechs.toList().filter { it != CHAIN || index > 3 },
            intro = emptyList(), seed = seed * 31 + index, gapMul = 1f, coinDensity = 1.4f, coloredCoins = false,
            diamond = index % 6 == 5, endless = true, shieldChance = if (index % 4 == 3) 0.8f else 0.1f,
        )
        val lvl = LevelGenerator.generate(p)
        // parça sonu: bir sonraki parçanın ilk kapısına güvenli mesafe
        return lvl.copy(length = lvl.gates.last().y + lvl.speed * 1.2f)
    }
}
