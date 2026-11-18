package com.yanlisrenk.logic.save

import com.yanlisrenk.logic.life.LifeState
import com.yanlisrenk.logic.time.ClockState
import kotlinx.serialization.Serializable

@Serializable
data class Txn(
    val id: Long,
    val currency: String,
    val amount: Int,
    val balanceAfter: Int,
    val reason: String,
    val at: Long,
)

@Serializable
data class MissionSlot(
    val templateId: String,
    val target: Int,
    var progress: Int = 0,
    var claimed: Boolean = false,
    val rewardGold: Int = 0,
    val rewardDiamonds: Int = 0,
    val rewardXp: Int = 0,
)

@Serializable
data class MissionState(
    var dailyKey: Long = -1,
    var daily: MutableList<MissionSlot> = mutableListOf(),
    var weeklyKey: Long = -1,
    var weekly: MutableList<MissionSlot> = mutableListOf(),
)

@Serializable
data class DailyLoginState(
    var lastClaimDay: Long = -1,
    /** Döngüdeki sıradaki gün 0..6 */
    var streakIndex: Int = 0,
    var totalClaims: Int = 0,
    var longestStreak: Int = 0,
    var currentStreak: Int = 0,
)

@Serializable
data class Settings(
    var musicVolume: Float = 0.6f,
    var sfxVolume: Float = 0.9f,
    var vibration: Boolean = true,
    var colorBlindSymbols: Boolean = true,
    var reducedMotion: Boolean = false,
    var showTimers: Boolean = true,
    var leftHanded: Boolean = false,
)

@Serializable
data class WeeklyChallengeState(
    var weekKey: Long = -1,
    var best: Int = 0,
    var attempts: Int = 0,
    var rewardTiersClaimed: MutableSet<Int> = mutableSetOf(),
)

/**
 * Oyunun tüm kalıcı verisi. [version] her şema değişikliğinde artırılır ve
 * [SaveMigrations] eski kayıtları yeni sürüme taşır.
 */
@Serializable
data class SaveData(
    var version: Int = CURRENT_VERSION,
    var installId: String = "",
    var createdAt: Long = 0L,
    // ilerleme
    var levelStars: MutableMap<Int, Int> = mutableMapOf(),
    var levelBestTaps: MutableMap<Int, Int> = mutableMapOf(),
    var masteryStars: MutableMap<Int, Int> = mutableMapOf(),
    var unlockedLevel: Int = 1,
    var areaIntroSeen: MutableSet<Int> = mutableSetOf(),
    var areaRewardClaimed: MutableSet<Int> = mutableSetOf(),
    var collection: MutableSet<Int> = mutableSetOf(),
    var worldSetClaimed: MutableSet<Int> = mutableSetOf(),
    // oyuncu
    var xp: Long = 0,
    var playerLevel: Int = 1,
    var levelRewardsClaimed: MutableSet<Int> = mutableSetOf(),
    var playerName: String = "Renkçi",
    // para
    var gold: Int = 200,
    var diamonds: Int = 5,
    var ledger: MutableList<Txn> = mutableListOf(),
    var nextTxnId: Long = 1,
    var grantedRewards: MutableSet<String> = mutableSetOf(),
    // envanter
    var owned: MutableSet<String> = mutableSetOf(),
    var equipped: MutableMap<String, String> = mutableMapOf(),
    var consumables: MutableMap<String, Int> = mutableMapOf(),
    // canlar ve zaman
    var lives: LifeState = LifeState(),
    var clock: ClockState = ClockState(),
    /** Bölüm başladı ama sonuçlanmadı (uygulama kapatıldıysa başarısız sayılır). */
    var pendingAttempt: Int = 0,
    // görevler / başarılar
    var missions: MissionState = MissionState(),
    var achievementsUnlocked: MutableMap<String, Long> = mutableMapOf(),
    var achievementsClaimed: MutableSet<String> = mutableSetOf(),
    var stats: MutableMap<String, Long> = mutableMapOf(),
    var daily: DailyLoginState = DailyLoginState(),
    // sonsuz mod
    var endlessBest: Int = 0,
    var endlessBestGates: Int = 0,
    var endlessWeekBest: MutableMap<Long, Int> = mutableMapOf(),
    var weekly: WeeklyChallengeState = WeeklyChallengeState(),
    // diğer
    var settings: Settings = Settings(),
    var tutorialsSeen: MutableSet<String> = mutableSetOf(),
    var saveCount: Long = 0,
) {
    companion object {
        const val CURRENT_VERSION = 2
    }

    fun stat(key: String): Long = stats[key] ?: 0L
    fun addStat(key: String, v: Long = 1) { stats[key] = stat(key) + v }
    fun maxStat(key: String, v: Long) { if (v > stat(key)) stats[key] = v }
    val totalStars: Int get() = levelStars.values.sum()
    val completedLevels: Int get() = levelStars.size
}
