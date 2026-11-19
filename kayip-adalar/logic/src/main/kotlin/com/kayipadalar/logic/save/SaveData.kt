package com.kayipadalar.logic.save

import kotlinx.serialization.Serializable

@Serializable
data class LevelRecord(
    var stars: Int = 0,
    var bestMoves: Int = 0,
    var completions: Int = 0,
    var attempts: Int = 0,
    val challenges: MutableSet<String> = mutableSetOf(),
)

@Serializable
data class QuestProgress(val templateId: String, val target: Int, var progress: Int = 0, var claimed: Boolean = false)

@Serializable
data class QuestState(
    var dailyKey: Long = -1,
    var dailyResetAt: Long = Long.MIN_VALUE / 2,
    val daily: MutableList<QuestProgress> = mutableListOf(),
    var weeklyKey: Long = -1,
    val weekly: MutableList<QuestProgress> = mutableListOf(),
)

@Serializable
data class ClockState(
    var trusted: Long = 0,
    var lastWall: Long = 0,
    var lastMono: Long = 0,
    var lastBoot: Long = -1,
    var suspicious: Int = 0,
)

@Serializable
data class LivesState(var lives: Int = 4, var regenStart: Long = 0)

@Serializable
data class ExpeditionRun(val id: String, val startedAt: Long, val endsAt: Long, val seed: Long)

@Serializable
data class SettingsState(
    var sound: Boolean = true,
    var music: Boolean = true,
    var vibration: Boolean = true,
    var animationsFast: Boolean = false,
    var largeText: Boolean = false,
    var language: String = "tr",
    val tutorialsSeen: MutableSet<String> = mutableSetOf(),
)

@Serializable
data class EndlessState(var best: Int = 0, var current: Int = 0, var runSeed: Long = 0, var active: Boolean = false)

@Serializable
data class DefenseState(var lastAttackAt: Long = 0, var wins: Int = 0, var attackIndex: Int = 0, val garrison: MutableList<String> = mutableListOf())

@Serializable
data class EventState(var lastEventAt: Long = 0, var pending: String? = null, var counter: Long = 0)

/**
 * Kalıcı oyuncu kaydı. [version] şema sürümüdür; [SaveSystem.migrate] eski sürümleri yükseltir.
 * Tüm alanların varsayılan değeri vardır, böylece eski kayıtlarda olmayan alanlar güvenle eklenir.
 */
@Serializable
data class SaveData(
    var version: Int = SaveSystem.CURRENT_VERSION,
    var playerName: String = "Ada Muhafızı",
    var createdAtWall: Long = 0,
    val res: MutableMap<String, Long> = mutableMapOf(),
    val levels: MutableMap<Int, LevelRecord> = mutableMapOf(),
    var highestUnlocked: Int = 1,
    val buildings: MutableMap<String, Int> = mutableMapOf(),
    val research: MutableSet<String> = mutableSetOf(),
    val unitLevels: MutableMap<String, Int> = mutableMapOf(),
    val collection: MutableSet<String> = mutableSetOf(),
    val owned: MutableSet<String> = mutableSetOf(),
    val equipped: MutableMap<String, String> = mutableMapOf(),
    val stats: MutableMap<String, Long> = mutableMapOf(),
    val achievementsClaimed: MutableSet<String> = mutableSetOf(),
    val quests: QuestState = QuestState(),
    val clock: ClockState = ClockState(),
    val lives: LivesState = LivesState(),
    val expeditions: MutableList<ExpeditionRun> = mutableListOf(),
    var lastIdleCollect: Long = 0,
    val endless: EndlessState = EndlessState(),
    val dailyPuzzleBest: MutableMap<Long, Int> = mutableMapOf(),
    val settings: SettingsState = SettingsState(),
    var reputationXp: Long = 0,
    val discoveredCombos: MutableSet<String> = mutableSetOf(),
    val discoveredTypes: MutableSet<String> = mutableSetOf(),
    val events: EventState = EventState(),
    val defense: DefenseState = DefenseState(),
    var era: Int = 0,
    var tutorialStep: Int = 0,
)
