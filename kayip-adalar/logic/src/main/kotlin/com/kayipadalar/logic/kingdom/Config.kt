package com.kayipadalar.logic.kingdom

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.pow
import kotlin.math.roundToInt

val GameJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Kaynak türleri. */
enum class Res(val label: String, val icon: String) {
    GOLD("Altın", "resources/gold"),
    WOOD("Odun", "resources/wood"),
    STONE("Taş", "resources/stone"),
    FOOD("Yiyecek", "resources/food"),
    CRYSTAL("Kristal", "resources/crystal"),
}

typealias Cost = Map<String, Int>

@Serializable
data class Rules(
    val baseStorage: Int = 1000,
    val storagePerCastle: Int = 500,
    val storagePerWarehouse: Int = 1500,
    val popPerCastle: Int = 5,
    val popGrowSeconds: Int = 8,
    val collectCapHours: Double = 2.0,
    val speedupSecondsPerCrystal: Int = 30,
    val raidEveryMinutes: Int = 15,
    val raidFromCastle: Int = 3,
    val raidLootPct: Int = 10,
    val towerDefense: Int = 30,
    val armyDefensePct: Int = 60,
    val builders: List<Int> = listOf(1),
    val dockSlots: Int = 8,
)

@Serializable
data class BuildingDef(
    val id: String,
    val name: String,
    val sprite: String,
    val spriteBig: String? = null,
    val desc: String,
    val maxLevel: Int,
    val unlockCastle: Int,
    val cost: Cost,
    val growth: Double,
    val time: Int,
    val timeGrowth: Double,
    val unique: Boolean = false,
    val produces: String? = null,
    val perHour: Int = 0,
    val pop: Int = 0,
) {
    fun costFor(level: Int, discount: Double = 0.0): Map<Res, Int> =
        cost.entries.associate { (k, v) -> Res.valueOf(k) to (v * growth.pow(level - 1) * (1 - discount)).roundToInt() }

    fun timeFor(level: Int, speed: Double): Int = (time * timeGrowth.pow(level - 1) * (1 - speed)).roundToInt().coerceAtLeast(1)
}

@Serializable
data class UnitDef(val id: String, val name: String, val sprite: String, val power: Int, val cost: Cost, val time: Int, val barracks: Int)

@Serializable
data class IslandDef(val type: String, val name: String, val sprite: String, val plots: Int, val bonus: String, val desc: String)

@Serializable
data class ResearchDef(val id: String, val name: String, val desc: String, val icon: String, val maxLevel: Int, val cost: Cost, val growth: Double, val time: Int) {
    fun costFor(level: Int): Map<Res, Int> = cost.entries.associate { (k, v) -> Res.valueOf(k) to (v * growth.pow(level - 1)).roundToInt() }
    fun timeFor(level: Int): Int = (time * 1.7.pow(level - 1)).roundToInt()
}

enum class QuestType { LEVEL, COUNT, COLLECT, POP, ARMY, POWER, EXPLORE, CONQUER, MERGE, RESEARCH, DEFEND }

@Serializable
data class QuestDef(
    val chapter: String,
    val type: QuestType,
    val target: String? = null,
    val amount: Int,
    val text: String,
    val hint: String,
    val reward: Cost,
)

@Serializable
data class KingdomConfig(
    val start: Cost,
    val rules: Rules,
    val buildings: List<BuildingDef>,
    val units: List<UnitDef>,
    val islands: List<IslandDef>,
    val research: List<ResearchDef>,
    val quests: List<QuestDef>,
) {
    fun building(id: String) = buildings.first { it.id == id }
    fun unit(id: String) = units.first { it.id == id }
    fun island(type: String) = islands.firstOrNull { it.type == type } ?: HOME
    fun researchDef(id: String) = research.first { it.id == id }

    companion object {
        val HOME = IslandDef("home", "Ana Ada", "islands/home", 9, "NONE", "Krallığının başladığı ada.")
        fun parse(text: String): KingdomConfig = GameJson.decodeFromString(serializer(), text)
    }
}
