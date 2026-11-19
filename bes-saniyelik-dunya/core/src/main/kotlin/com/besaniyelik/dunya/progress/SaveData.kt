package com.besaniyelik.dunya.progress

import com.besaniyelik.dunya.level.PowerupType
import kotlinx.serialization.Serializable

@Serializable
data class LevelProgress(
    var completed: Boolean = false,
    var stars: Int = 0,
    var bestCoins: Int = 0,
    var bestTimeTicks: Int = 0,
    var plays: Int = 0,
    var starMask: Int = 0,
)

@Serializable
data class Stats(
    var jumps: Long = 0,
    var coinsCollected: Long = 0,
    var starsCollected: Long = 0,
    var deaths: Long = 0,
    var levelsCompleted: Long = 0,
    var perfectLevels: Long = 0,
    var endlessRuns: Long = 0,
    var endlessBestScore: Long = 0,
    var endlessBestSeconds: Float = 0f,
    var endlessBestDistance: Float = 0f,
    var rulesSurvived: Long = 0,
    var powerupsUsed: Long = 0,
    var springs: Long = 0,
    var playSeconds: Float = 0f,
)

@Serializable
data class MissionProgress(
    val id: String,
    val type: MissionType,
    val target: Int,
    val rewardCoins: Int,
    val rewardGems: Int,
    val rewardXp: Int,
    var progress: Int = 0,
    var claimed: Boolean = false,
) {
    val done get() = progress >= target
}

@Serializable
data class MissionState(
    var dailyKey: Long = -1,
    var daily: MutableList<MissionProgress> = mutableListOf(),
    var weeklyKey: Long = -1,
    var weekly: MutableList<MissionProgress> = mutableListOf(),
)

@Serializable
data class LoginState(
    var lastClaimDay: Long = -1,
    var cycleDay: Int = 0,
    var totalClaims: Int = 0,
)

@Serializable
data class Settings(
    var music: Boolean = true,
    var sound: Boolean = true,
    var vibration: Boolean = true,
    var musicVolume: Float = 0.6f,
    var soundVolume: Float = 0.9f,
    var showFps: Boolean = false,
    var reducedEffects: Boolean = false,
)

@Serializable
data class EndlessRecord(
    val score: Int,
    val seconds: Float,
    val distance: Float,
    val coins: Int,
    val day: Long,
)

@Serializable
data class SaveData(
    var version: Int = SaveManager.CURRENT_VERSION,
    var playerName: String = "Koşucu",
    var levels: MutableMap<Int, LevelProgress> = mutableMapOf(),
    var highestUnlocked: Int = 1,
    var xp: Long = 0,
    var playerLevel: Int = 1,
    var coins: Long = 0,
    var gems: Long = 0,
    var ownedSkins: MutableSet<String> = mutableSetOf("pati"),
    var selectedSkin: String = "pati",
    var ownedTrails: MutableSet<String> = mutableSetOf("toz"),
    var selectedTrail: String = "toz",
    var inventory: MutableMap<PowerupType, Int> = mutableMapOf(),
    var missions: MissionState = MissionState(),
    var achievements: MutableSet<String> = mutableSetOf(),
    var stats: Stats = Stats(),
    var endlessRecords: MutableList<EndlessRecord> = mutableListOf(),
    var pieces: MutableSet<Int> = mutableSetOf(),
    var collectionsClaimed: MutableSet<Int> = mutableSetOf(),
    var login: LoginState = LoginState(),
    var settings: Settings = Settings(),
    var seenTutorials: MutableSet<String> = mutableSetOf(),
    var levelRewardsClaimed: Int = 1,
) {
    val totalStars get() = levels.values.sumOf { it.stars }
    fun stars(id: Int) = levels[id]?.stars ?: 0
    fun isCompleted(id: Int) = levels[id]?.completed == true
    fun isUnlocked(id: Int) = id <= highestUnlocked
}
