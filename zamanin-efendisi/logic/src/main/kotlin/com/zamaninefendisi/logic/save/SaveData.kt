package com.zamaninefendisi.logic.save

import com.zamaninefendisi.logic.Action
import kotlinx.serialization.Serializable

const val SAVE_VERSION = 1

@Serializable
data class SaveData(
    val version: Int = SAVE_VERSION,
    val levels: Map<Int, LevelProgress> = emptyMap(),
    val gold: Int = 0,
    val crystals: Int = 0,
    val xp: Int = 0,
    val lives: LivesState = LivesState(),
    val settings: Settings = Settings(),
    val journal: List<SavedJournalEntry> = emptyList(),
    val stats: Stats = Stats(),
    val inProgress: InProgress? = null,
    val seenIntros: Set<Int> = emptySet(),
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
