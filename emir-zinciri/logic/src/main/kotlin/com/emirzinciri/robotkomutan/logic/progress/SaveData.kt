package com.emirzinciri.robotkomutan.logic.progress

import kotlinx.serialization.Serializable

@Serializable
data class LevelRecord(
    var stars: Int = 0,
    var bestCards: Int = 0,
    var bestTicks: Int = 0,
    var completions: Int = 0,
    var attempts: Int = 0,
)

@Serializable
data class Settings(
    var sound: Boolean = true,
    var music: Boolean = true,
    /** Animasyon hızı çarpanı: 1, 2 veya 4. Simülasyon sonucunu etkilemez. */
    var animSpeed: Int = 1,
    var language: String = "tr",
    var showGrid: Boolean = true,
)

/** Oyun kaynakları. Nadir kaynaklar ileriki aşamalarda (fabrika, sefer, patron) kazanılır. */
@Serializable
data class Wallet(
    var gold: Long = 0,
    var metal: Long = 0,
    var energy: Long = 0,
    var chips: Long = 0,
    var tech: Long = 0,
    var quantumCores: Long = 0,
    var legendaryParts: Long = 0,
    var commandCrystals: Long = 0,
    var rareCircuits: Long = 0,
)

@Serializable
data class PlayerStats(
    var runs: Long = 0,
    var wins: Long = 0,
    var fails: Long = 0,
    var ticksSimulated: Long = 0,
    var cardsPlaced: Long = 0,
    var livesLost: Long = 0,
)

/** Kalıcı kaydın tamamı. Alan eklerken varsayılan değer verilmeli; böylece eski kayıtlar sorunsuz okunur. */
@Serializable
data class SaveData(
    var version: Int = CURRENT_VERSION,
    var profileName: String = "Komutan",
    var createdAtMs: Long = 0,
    val levels: MutableMap<Int, LevelRecord> = mutableMapOf(),
    /** Bölüm -> robot -> kartlar. Oyuncunun son komut zinciri korunur; başarısızlıktan sonra baştan yazmak gerekmez. */
    val drafts: MutableMap<Int, MutableMap<String, List<String>>> = mutableMapOf(),
    var lives: LivesState = LivesState(),
    var wallet: Wallet = Wallet(),
    var xp: Long = 0,
    var settings: Settings = Settings(),
    val tutorialsSeen: MutableSet<String> = mutableSetOf(),
    var stats: PlayerStats = PlayerStats(),
    var factory: com.emirzinciri.robotkomutan.logic.factory.FactoryState = com.emirzinciri.robotkomutan.logic.factory.FactoryState(),
) {
    fun record(levelId: Int): LevelRecord = levels.getOrPut(levelId) { LevelRecord() }
    fun stars(levelId: Int): Int = levels[levelId]?.stars ?: 0
    val totalStars: Int get() = levels.values.sumOf { it.stars }

    companion object {
        const val CURRENT_VERSION = 1
    }
}
