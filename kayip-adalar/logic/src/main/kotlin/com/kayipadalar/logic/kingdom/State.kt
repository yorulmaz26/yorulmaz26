package com.kayipadalar.logic.kingdom

import kotlinx.serialization.Serializable

enum class IslandStatus { HIDDEN, EXPLORING, DISCOVERED, CLEARED, MERGED }

@Serializable
data class BuildingState(
    val uid: Int,
    val type: String,
    var level: Int,
    val island: Int,
    val plot: Int,
    /** İnşaat/yükseltme bitiş zamanı (güvenilir oyun saati, ms); 0 = boşta. */
    var busyUntil: Long = 0,
    var busyStarted: Long = 0,
    /** Toplanmayı bekleyen üretim. */
    var stored: Double = 0.0,
) {
    val busy get() = busyUntil > 0
}

@Serializable
data class IslandState(
    val id: Int,
    val type: String,
    val ring: Int,
    /** Haritadaki konum (derece) – yalnızca görsel. */
    val angle: Float,
    var status: IslandStatus = IslandStatus.HIDDEN,
    var exploreUntil: Long = 0,
    var exploreStarted: Long = 0,
    val guard: Int,
    /** Krallıktaki yuva (0..dockSlots-1); ana ada -1. */
    var dock: Int = -1,
)

@Serializable
data class TrainJob(val unit: String, var count: Int, var nextAt: Long, var each: Long)

@Serializable
data class ResearchJob(val id: String, val level: Int, val started: Long, val endsAt: Long)

@Serializable
data class Raid(val power: Int, val arrivedAt: Long)

@Serializable
data class Settings(var sound: Boolean = true, var music: Boolean = true, var vibration: Boolean = true)

@Serializable
data class ClockState(var trusted: Long = 0, var lastWall: Long = 0, var lastMono: Long = 0, var lastBoot: Long = -1, var suspicious: Int = 0)

/** Kalıcı oyuncu kaydı (şema sürümü 2: gelişim stratejisi). */
@Serializable
data class KingdomSave(
    var version: Int = 2,
    var playerName: String = "Ada Muhafızı",
    val res: MutableMap<String, Double> = mutableMapOf(),
    val buildings: MutableList<BuildingState> = mutableListOf(),
    val islands: MutableList<IslandState> = mutableListOf(),
    val units: MutableMap<String, Int> = mutableMapOf(),
    val training: MutableList<TrainJob> = mutableListOf(),
    val research: MutableMap<String, Int> = mutableMapOf(),
    var researching: ResearchJob? = null,
    var questIndex: Int = 0,
    val stats: MutableMap<String, Long> = mutableMapOf(),
    var pop: Double = 0.0,
    var lastTick: Long = 0,
    var nextRaidAt: Long = 0,
    var raid: Raid? = null,
    var nextUid: Int = 1,
    var created: Boolean = false,
    val settings: Settings = Settings(),
    val clock: ClockState = ClockState(),
    var seenIntro: Boolean = false,
)
