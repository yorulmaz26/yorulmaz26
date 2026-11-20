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
class EndlessGenerator(
    private val seed: Long,
    private val forced: List<Mechanic> = emptyList(),
    /** Hız çarpanı (başlangıç ve artış birlikte ölçeklenir). */
    private val speedScale: Float = 1f,
    /** Doğrulayıcının varsaydığı tepki süresine eklenir → kapılar arası daha geniş. */
    private val reactionBonus: Float = 0f,
    /** >0 ise oyuncunun görüş mesafesi (bölünmüş ekran). */
    private val viewDist: Float = 0f,
    /** Bu modda hiç kullanılmayacak mekanikler. */
    private val exclude: Set<Mechanic> = emptySet(),
) {
    companion object {
        /** İki kişilik mod ayarları: bölünmüş ekranda daha kısa görüş, daha fazla zaman. */
        fun twoPlayer(seed: Long, pace: Float): EndlessGenerator = EndlessGenerator(
            seed, speedScale = pace, reactionBonus = 0.22f, viewDist = 8.2f,
            exclude = setOf(FOG, HIDDEN, CHAIN, QUICK, FLICKER, MEMORY),
        )
    }

    private val unlockOrder = listOf(SAME, DIFF, RAINBOW, DOUBLE, MIX, SPEED, NONE, MEMORY, ROTATE, TIMED, NEXT, MIRROR,
        HIDDEN, LOCK, PREV, TRIPLE, AUTOSHIFT, SLIDE, STROOP, DECOY, WAVE, FLICKER, REPEAT, CHAIN, FOG, QUICK)

    fun speedFor(chunk: Int): Float = minOf(10.5f, 4.8f + chunk * 0.22f) * speedScale

    fun chunk(index: Int): LevelData {
        val rng = Random(seed * 1_000_003L + index * 7919L)
        val available = unlockOrder.take(minOf(unlockOrder.size, 2 + index))
        val mechs = LinkedHashSet<Mechanic>()
        mechs.add(SAME)
        if (index > 0) mechs.add(DIFF)
        mechs.addAll(available.drop(2).filter { it !in exclude }.shuffled(rng).take(minOf(3, 1 + index / 4)))
        mechs.addAll(forced)
        if (index < 2) mechs.removeAll(setOf(QUICK, FOG, CHAIN, WAVE))
        val d = minOf(1f, index / 30f)
        val p = LevelGenerator.Params(
            id = 10_000 + index, area = 0, index = 0, isFinal = false, d = d, speed = speedFor(index),
            reaction = 0.55f - 0.2f * d + reactionBonus, gateCount = 8, mechanics = mechs.toList().filter { it != CHAIN || index > 3 },
            intro = emptyList(), seed = seed * 31 + index, coinDensity = 1.4f, coloredCoins = false,
            diamond = index % 6 == 5, endless = true, shieldChance = if (index % 4 == 3) 0.8f else 0.1f,
            viewDist = viewDist, gapMul = 1f + reactionBonus,
        )
        val lvl = LevelGenerator.generate(p)
        // parça sonu: bir sonraki parçanın ilk kapısına güvenli mesafe
        return lvl.copy(length = lvl.gates.last().y + lvl.speed * 1.2f)
    }
}
