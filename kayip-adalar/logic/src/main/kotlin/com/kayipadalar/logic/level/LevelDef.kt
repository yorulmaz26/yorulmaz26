package com.kayipadalar.logic.level

import com.kayipadalar.logic.model.AiBehavior
import kotlinx.serialization.Serializable

/** Bölüm hedef türleri (16 tür). */
enum class ObjectiveType {
    CONNECT_ALL,        // Bütün adaları birleştir
    GATHER,             // Belirli kaynak miktarına ulaş
    PROTECT_CASTLE,     // Krallık kalesini koru (N tur)
    CAPTURE,            // Düşman adasını ele geçir
    CONNECT_TARGET,     // Belirli sürede köprü kur (kaleyi hedef adaya bağla)
    RESCUE,             // Kayıp halkı kurtar (köyleri bağla)
    COLLECT_CRYSTALS,   // Enerji kristallerini topla
    DELIVER,            // Hareketli adayı hedefe ulaştır
    TRAP_ENEMY,         // Düşman ordusunu tuzağa düşür
    PORTAL_CHAIN,       // Portal zinciri oluştur
    ESCAPE,             // Ada çökmeden kaçış yolu kur
    TRADE_ROUTE,        // Ticaret güzergâhını tamamla
    SURVIVE,            // Belirli sayıda tur hayatta kal
    MAKE_COMBO,         // Özel kombo oluştur
    DEFEAT_BOSS,        // Alan patronunu yen
    DEFEAT_ALL,         // Bütün düşmanları yen
}

@Serializable
data class ObjectiveDef(
    val type: ObjectiveType,
    val amount: Int = 0,
    val res: String? = null,
    val target: String? = null,
    val combo: String? = null,
    val x: Int = -1,
    val y: Int = -1,
)

@Serializable
data class IslandDef(
    val id: String,
    val type: String,
    val x: Int,
    val y: Int,
    val ports: String = "",
    val owner: String = "NEUTRAL",
    val height: Int = 0,
    val fixed: Boolean = false,
    val hidden: Boolean = false,
    val frozen: Int = 0,
    val crumble: Int = -1,
)

@Serializable
data class UnitDef(val id: String, val type: String, val owner: String, val island: String, val hpBonus: Int = 0, val atkBonus: Int = 0)

@Serializable
data class CellFeature(val x: Int, val y: Int, val kind: String, val dir: String = "")

@Serializable
data class BossPhase(val hpBelowPct: Int, val abilities: List<String>)

@Serializable
data class BossDef(
    val unitId: String,
    val name: String,
    val every: Int = 2,
    val phases: List<BossPhase>,
    val sealDamage: Int = 0,
)

@Serializable
data class RewardDef(
    val gold: Int = 0,
    val wood: Int = 0,
    val stone: Int = 0,
    val iron: Int = 0,
    val food: Int = 0,
    val crystal: Int = 0,
    val ancientShard: Int = 0,
    val stardust: Int = 0,
    val royalSeal: Int = 0,
    val collectionChancePct: Int = 0,
)

@Serializable
data class LevelDef(
    val id: Int,
    val area: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val moveLimit: Int,
    val star3: Int,
    val star2: Int,
    val mechanics: List<String>,
    val islands: List<IslandDef>,
    val units: List<UnitDef> = emptyList(),
    val blocked: List<List<Int>> = emptyList(),
    val features: List<CellFeature> = emptyList(),
    val objectives: List<ObjectiveDef>,
    val ai: String = AiBehavior.AGGRESSIVE.name,
    val boss: BossDef? = null,
    val castleHp: Int = 10,
    val startEnergy: Int = 0,
    val tutorial: String? = null,
    val special: String = "",
    val difficulty: Int = 1,
    val seed: Long = 1,
    val rewards: RewardDef = RewardDef(),
    val theme: String = "GREEN",
    val solution: List<String> = emptyList(),
)

@Serializable
data class AreaFile(val area: Int, val name: String, val theme: String, val feature: String, val levels: List<LevelDef>)
