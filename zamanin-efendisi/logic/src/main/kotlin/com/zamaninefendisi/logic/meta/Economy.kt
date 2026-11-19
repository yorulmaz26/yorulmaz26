package com.zamaninefendisi.logic.meta

import kotlinx.serialization.Serializable

/** Oyun kaynakları. Özel kaynaklar daha nadirdir. */
@Serializable
enum class Resource(val title: String, val special: Boolean = false) {
    GOLD("Altın"),
    WOOD("Odun"),
    STONE("Taş"),
    IRON("Demir"),
    ENERGY("Enerji"),
    SCIENCE("Bilim Puanı"),
    CRYSTAL("Zaman Kristali", true),
    SHARD("Kadim Parça", true),
    PARADOX("Paradoks Enerjisi", true),
    SEAL("Zaman Mührü", true),
}

typealias Cost = Map<Resource, Int>

fun Map<Resource, Int>.has(cost: Cost): Boolean = cost.all { (r, n) -> (this[r] ?: 0) >= n }
fun Map<Resource, Int>.minus(cost: Cost): Map<Resource, Int> = toMutableMap().also { m -> cost.forEach { (r, n) -> m[r] = (m[r] ?: 0) - n } }
fun Map<Resource, Int>.plus(gain: Map<Resource, Int>): Map<Resource, Int> = toMutableMap().also { m -> gain.forEach { (r, n) -> m[r] = (m[r] ?: 0) + n } }
fun Cost.describe(): String = entries.filter { it.value > 0 }.joinToString(" · ") { "${it.value} ${it.key.title}" }

/**
 * Manipülasyona dayanıklı geçen süre hesabı (canlar ve bina üretimi ortak kullanır).
 * Aynı açılış içinde monotonik saat, yeniden başlatmada duvar saati kullanılır.
 */
object SafeElapsed {
    fun delta(lastWall: Long, lastElapsed: Long, lastBoot: String, nowWall: Long, nowElapsed: Long, nowBoot: String): Long {
        if (lastWall == 0L && lastElapsed == 0L) return 0
        return if (nowBoot == lastBoot && nowElapsed >= lastElapsed) nowElapsed - lastElapsed
        else maxOf(nowElapsed, (nowWall - lastWall).coerceAtLeast(0))
    }
}
