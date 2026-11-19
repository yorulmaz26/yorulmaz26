package com.kayipadalar.logic.meta

import com.kayipadalar.logic.data.GameJson
import kotlinx.serialization.Serializable

typealias Cost = Map<String, Int>

@Serializable
data class Effect(val kind: String, val value: Double = 0.0)

@Serializable
data class BuildingDef(
    val id: String,
    val name: String,
    val desc: String,
    val maxLevel: Int,
    val baseCost: Cost,
    val growth: Double = 1.5,
    val effect: Effect,
    val unlockLevel: Int = 0,
)

@Serializable
data class ResearchDef(
    val id: String,
    val branch: String,
    val tier: Int,
    val name: String,
    val desc: String,
    val cost: Cost,
    val requires: List<String> = emptyList(),
    val exclusive: String? = null,
    val effect: Effect,
)

@Serializable
data class ShopItem(
    val id: String,
    val category: String,
    val name: String,
    val price: Cost,
    val kind: String = "COSMETIC",
    val color: String = "",
    val bundle: Cost = emptyMap(),
    val reputation: Int = 0,
)

@Serializable
data class CollectionSet(
    val id: String,
    val name: String,
    val itemName: String,
    val items: List<String> = emptyList(),
    val bonus: Effect,
)

@Serializable
data class AchievementTemplate(
    val stat: String,
    val name: String,
    val desc: String,
    val tiers: List<Long>,
    val rewardGold: Int = 50,
    val rewardStardust: Int = 0,
)

@Serializable
data class QuestTemplate(
    val id: String,
    val stat: String,
    val desc: String,
    val daily: Int = 0,
    val weekly: Int = 0,
    val reward: Cost,
    val weeklyReward: Cost = emptyMap(),
)

@Serializable
data class ExpeditionDef(
    val id: String,
    val name: String,
    val minutes: Int,
    val cost: Cost,
    val rewards: Cost,
    val collectionChancePct: Int = 0,
    val unlockLevel: Int = 0,
)

@Serializable
data class EventChoice(val label: String, val cost: Cost = emptyMap(), val reward: Cost = emptyMap())

@Serializable
data class RandomEventDef(val id: String, val title: String, val text: String, val choices: List<EventChoice>)

@Serializable
data class ReputationLevel(val xp: Int, val title: String)

@Serializable
data class EconomyRules(
    val livesMax: Int = 4,
    val lifeRegenMinutes: Int = 15,
    val idleCapHoursBase: Int = 4,
    val levelGoldBase: Int = 30,
    val levelGoldPerLevel: Double = 1.2,
    val starBonusPct: Int = 15,
    val replayRewardPct: Int = 25,
    val eventCooldownMinutes: Int = 20,
    val eventChancePct: Int = 60,
    val dailyResetMinHours: Int = 12,
    val tradeBaseRate: Double = 0.5,
    val expeditionSlotsBase: Int = 1,
    val challengeGold: Int = 60,
    val endlessGoldPerStage: Int = 12,
    val dailyPuzzleGold: Int = 80,
    val defenseEveryHours: Int = 6,
    val defenseGold: Int = 120,
    val eraGoldPct: Int = 5,
)

@Serializable
data class MetaConfig(
    val economy: EconomyRules = EconomyRules(),
    val buildings: List<BuildingDef>,
    val research: List<ResearchDef>,
    val shop: List<ShopItem>,
    val collection: List<CollectionSet>,
    val achievements: List<AchievementTemplate>,
    val quests: List<QuestTemplate>,
    val expeditions: List<ExpeditionDef>,
    val events: List<RandomEventDef>,
    val reputation: List<ReputationLevel>,
) {
    fun building(id: String) = buildings.first { it.id == id }
    fun researchNode(id: String) = research.first { it.id == id }

    /** 10 set × 10 öğe = 100 koleksiyon öğesi kimliği "SET#n". */
    val collectionItems: List<Pair<String, String>> by lazy {
        val roman = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")
        collection.flatMap { s ->
            (0 until 10).map { i -> "${s.id}#$i" to (s.items.getOrNull(i) ?: "${s.itemName} ${roman[i]}") }
        }
    }

    companion object {
        fun parse(text: String): MetaConfig = GameJson.decodeFromString(serializer(), text)
    }
}

/** Başarımlar şablonlardan üretilir: her şablonun her kademesi bir başarımdır. */
data class Achievement(val id: String, val stat: String, val target: Long, val name: String, val desc: String, val gold: Int, val stardust: Int)

fun MetaConfig.allAchievements(): List<Achievement> = achievements.flatMap { t ->
    t.tiers.mapIndexed { i, target ->
        Achievement(
            id = "${t.stat}_$target",
            stat = t.stat,
            target = target,
            name = "${t.name} ${i + 1}",
            desc = t.desc.replace("{n}", target.toString()),
            gold = t.rewardGold * (i + 1),
            stardust = t.rewardStardust * (i + 1),
        )
    }
}
