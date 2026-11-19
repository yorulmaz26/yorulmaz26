package com.emirzinciri.robotkomutan.logic.meta

import com.emirzinciri.robotkomutan.logic.factory.BuildingType
import com.emirzinciri.robotkomutan.logic.progress.GameClock
import com.emirzinciri.robotkomutan.logic.progress.SaveData
import kotlinx.serialization.Serializable

/** Koleksiyon parçası: 5 set × 20 parça = 100. */
data class CollectionItem(val id: Int, val set: Int, val title: String, val rarity: Int)

object Collection {
    val sets = listOf("Klasik Prototipler", "Fabrika Hattı", "Uzay İstasyonu", "Derin Deniz", "Çekirdek Arşivi")
    private val parts = listOf("Anten", "Göz Modülü", "Tekerlek", "Pil", "Kol", "Kalkan Plakası", "İşlemci", "Hoparlör", "Mıknatıs", "Pervane",
        "Sensör", "Lazer Ucu", "Kask", "Zırh", "Motor", "Paraşüt", "Radar", "Kamera", "Matkap", "Çekirdek")
    val items: List<CollectionItem> = sets.indices.flatMap { s ->
        parts.mapIndexed { i, p -> CollectionItem(s * 20 + i, s, "${sets[s].split(' ').first()} $p", rarity = if (i >= 17) 3 else if (i >= 11) 2 else 1) }
    }
    init { check(items.size == 100) }

    /** Tamamlanan her set için tek seferlik ödül. */
    const val SET_REWARD_GOLD = 500L
}

/** Sefer rotası. */
enum class Route(val title: String, val minutes: Int, val energyCost: Long, val gold: LongRange, val metal: LongRange, val chips: LongRange, val itemChance: Int, val minLevel: Int) {
    NEAR("Yakın Hurdalık", 30, 20, 30L..60L, 10L..25L, 0L..1L, 25, 1),
    CITY("Terk Edilmiş Şehir", 120, 50, 90L..160L, 30L..60L, 1L..3L, 55, 2),
    DEEP("Derin Kuyu Madeni", 480, 120, 250L..400L, 80L..140L, 3L..6L, 90, 3),
}

@Serializable
data class Expedition(
    val route: Route,
    val seed: Long,
    var elapsedMs: Long = 0,
    var lastWallMs: Long,
    var lastMonoMs: Long,
    var lastBootId: String,
    var claimed: Boolean = false,
)

data class ExpeditionReward(val gold: Long, val metal: Long, val chips: Long, val item: CollectionItem?, val duplicate: Boolean)

/**
 * Robot seferleri: Keşif Merkezi seviyesi kadar eş zamanlı sefer. Süre, can ve fabrika ile aynı güvenilir saat yöntemiyle
 * ölçülür (aynı açılışta monotonik). Ödül sefer başlarken belirlenen tohumdan hesaplanır (iptal-yeniden dene hilesi yok).
 */
class Expeditions(private val save: SaveData, private val clock: GameClock) {
    private val list get() = save.meta.expeditions
    val active: List<Expedition> get() = list.filter { !it.claimed }
    fun slots(): Int = save.factory.buildings[BuildingType.EXPLORATION] ?: 0

    fun update() {
        val wall = clock.wallMs(); val mono = clock.monotonicMs(); val boot = clock.bootId()
        for (e in active) {
            val d = if (boot == e.lastBootId && mono >= e.lastMonoMs) mono - e.lastMonoMs else (wall - e.lastWallMs).coerceAtLeast(0)
            e.elapsedMs = (e.elapsedMs + d).coerceAtMost(e.route.minutes * 60_000L)
            e.lastWallMs = wall; e.lastMonoMs = mono; e.lastBootId = boot
        }
    }

    fun remainingMs(e: Expedition) = e.route.minutes * 60_000L - e.elapsedMs
    fun isDone(e: Expedition) = remainingMs(e) <= 0

    fun canStart(r: Route): String? = when {
        slots() == 0 -> "Önce Fabrika'da Keşif Merkezi'ni kur."
        active.size >= slots() -> "Bütün sefer yuvaları dolu (Keşif Merkezi seviyesi kadar)."
        slots() < r.minLevel -> "Keşif Merkezi seviye ${r.minLevel} gerekli."
        save.wallet.energy < r.energyCost -> "${r.energyCost} enerji gerekli."
        else -> null
    }

    fun start(r: Route): Boolean {
        if (canStart(r) != null) return false
        save.wallet.energy -= r.energyCost
        list.removeAll { it.claimed }
        val seed = (save.meta.counters[Counter.EXPEDITIONS] ?: 0) * 7919L + r.ordinal * 31L + save.createdAtMs
        list += Expedition(r, seed, 0, clock.wallMs(), clock.monotonicMs(), clock.bootId())
        return true
    }

    fun claim(e: Expedition): ExpeditionReward? {
        update()
        if (e.claimed || !isDone(e)) return null
        e.claimed = true
        val rnd = java.util.Random(e.seed)
        fun roll(rg: LongRange) = rg.first + (rnd.nextDouble() * (rg.last - rg.first + 1)).toLong()
        val gold = roll(e.route.gold); val metal = roll(e.route.metal); val chips = roll(e.route.chips)
        save.wallet.gold += gold; save.wallet.metal += metal; save.wallet.chips += chips
        var item: CollectionItem? = null; var dup = false
        if (rnd.nextInt(100) < e.route.itemChance) {
            val pool = Collection.items.filter { it.rarity <= 1 + e.route.ordinal }
            item = pool[rnd.nextInt(pool.size)]
            dup = !save.meta.collection.add(item.id)
            if (dup) save.wallet.gold += 25L * item.rarity
            else Collection.sets.indices.forEach { s ->
                val all = Collection.items.filter { it.set == s }.all { it.id in save.meta.collection }
                if (all && save.meta.setsRewarded.add(s)) save.wallet.gold += Collection.SET_REWARD_GOLD
            }
        }
        save.meta.counters.merge(Counter.EXPEDITIONS, 1, Long::plus)
        return ExpeditionReward(gold, metal, chips, item, dup)
    }
}
