package com.kayipadalar.logic.data

import com.kayipadalar.logic.model.IslandType
import com.kayipadalar.logic.model.UnitType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val GameJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    prettyPrint = false
}

@Serializable
data class UnitStats(
    val type: String,
    val hp: Int,
    val atk: Int,
    val def: Int,
    val range: Int,
    val moves: Int,
    val unlockBarracks: Int = 0,
    val upgradeCost: Map<String, Int> = emptyMap(),
)

@Serializable
data class ComboEffect(val kind: String, val value: Int = 0, val radius: Int = 0, val res: String? = null, val to: String? = null)

@Serializable
data class ComboDef(
    val id: String,
    val a: String,
    val b: String,
    val effects: List<ComboEffect>,
)

@Serializable
data class ActionCosts(
    val raiseEnergy: Int = 1,
    val jumpEnergy: Int = 2,
    val swapEnergy: Int = 1,
)

@Serializable
data class RulesConfig(
    val costs: ActionCosts = ActionCosts(),
    val maxUnitsPerIsland: Int = 1,
    val maxHeight: Int = 2,
    val gravityRange: Int = 3,
    val volcanoEvery: Int = 3,
    val volcanoDamage: Int = 2,
    val poisonDamage: Int = 1,
    val lifeHeal: Int = 1,
    val timeBonusMoves: Int = 2,
    val ancientEnergy: Int = 3,
    val resourcerSpawnEvery: Int = 3,
    val resourcerMaxUnits: Int = 6,
    val trapperEvery: Int = 2,
    val tripleBonusMoves: Int = 1,
    val tripleBonusRes: Int = 3,
    val chainDamage: Int = 2,
    val chainEnergy: Int = 2,
)

@Serializable
data class GameConfigFile(
    val units: List<UnitStats>,
    val combos: List<ComboDef>,
    val rules: RulesConfig = RulesConfig(),
)

/** Merkezi oyun kuralları yapılandırması (data/rules.json). */
class GameConfig(file: GameConfigFile) {
    val rules = file.rules
    val units: Map<UnitType, UnitStats> = file.units.associateBy { UnitType.valueOf(it.type) }
    val combos: List<ComboDef> = file.combos
    private val comboMap: Map<Pair<IslandType, IslandType>, ComboDef> = buildMap {
        for (c in file.combos) {
            val a = IslandType.valueOf(c.a)
            val b = IslandType.valueOf(c.b)
            put(a to b, c); put(b to a, c)
        }
    }

    fun comboFor(a: IslandType, b: IslandType): ComboDef? = comboMap[a to b]
    fun stats(t: UnitType) = units[t] ?: error("Birim istatistiği yok: $t")

    companion object {
        fun parse(text: String) = GameConfig(GameJson.decodeFromString(GameConfigFile.serializer(), text))
    }
}
