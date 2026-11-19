package com.zamaninefendisi.logic.save

import com.zamaninefendisi.logic.Action
import com.zamaninefendisi.logic.meta.BuildingType
import com.zamaninefendisi.logic.meta.Kingdom
import com.zamaninefendisi.logic.meta.Resource
import com.zamaninefendisi.logic.meta.Shop
import com.zamaninefendisi.logic.meta.ShopCategory
import kotlinx.serialization.Serializable

const val SAVE_VERSION = 2

@Serializable
data class SaveData(
    val version: Int = SAVE_VERSION,
    val levels: Map<Int, LevelProgress> = emptyMap(),
    /** Sürüm 1'den kalan alanlar; göçte cüzdana taşınır. */
    val gold: Int = 0,
    val crystals: Int = 0,
    val xp: Int = 0,
    val wallet: Map<Resource, Int> = mapOf(Resource.GOLD to 0),
    val buildings: Map<BuildingType, Int> = Kingdom.starting,
    val research: Set<String> = emptySet(),
    val production: ProductionState = ProductionState(),
    val owned: Set<String> = Shop.defaults().values.toSet(),
    val equipped: Map<ShopCategory, String> = Shop.defaults(),
    val lives: LivesState = LivesState(),
    val settings: Settings = Settings(),
    val journal: List<SavedJournalEntry> = emptyList(),
    val stats: Stats = Stats(),
    val inProgress: InProgress? = null,
    val seenIntros: Set<Int> = emptySet(),
    val continuity: com.zamaninefendisi.logic.continuity.ContinuityState = com.zamaninefendisi.logic.continuity.ContinuityState(),
)

/** Kale üretiminin biriktiği durum ve son ölçüm anı (manipülasyona dayanıklı süre için). */
@Serializable
data class ProductionState(
    val pending: Map<Resource, Double> = emptyMap(),
    val lastWallMs: Long = 0,
    val lastElapsedMs: Long = 0,
    val bootId: String = "",
)

@Serializable
data class LevelProgress(
    val stars: Int = 0,
    val bestActions: Int = Int.MAX_VALUE,
    val completions: Int = 0,
    val attempts: Int = 0,
) {
    val completed: Boolean get() = completions > 0
}

@Serializable
data class Settings(
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val hints: Boolean = true,
)

@Serializable
data class SavedJournalEntry(val levelId: Int, val text: String, val timestamp: Long)

@Serializable
data class Stats(
    val levelsWon: Int = 0,
    val levelsFailed: Int = 0,
    val eraSwitches: Int = 0,
    val buildings: Int = 0,
    val paradoxes: Int = 0,
    val enemiesDefeated: Int = 0,
    val fewestActionsWin: Int = 0,
    val bestCombo: Int = 0,
    val unitsLost: Int = 0,
)

/** Yarım kalan bölüm: eylem listesi deterministik olarak yeniden oynatılır. */
@Serializable
data class InProgress(val levelId: Int, val actions: List<Action>)

/**
 * Can durumu. [regenRemainingMs]: bir sonraki cana kalan süre (yalnızca can < 4 iken anlamlı).
 * Son ölçüm anının hem duvar saati hem açılıştan beri geçen (monotonik) süresi saklanır.
 */
@Serializable
data class LivesState(
    val lives: Int = MAX_LIVES,
    val regenRemainingMs: Long = 0,
    val lastWallMs: Long = 0,
    val lastElapsedMs: Long = 0,
    val bootId: String = "",
)

const val MAX_LIVES = 4
const val LIFE_REGEN_MS = 15L * 60 * 1000
