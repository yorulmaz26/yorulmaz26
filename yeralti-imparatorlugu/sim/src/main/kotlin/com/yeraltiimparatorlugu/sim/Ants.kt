package com.yeraltiimparatorlugu.sim

enum class Caste { QUEEN, WORKER, MILITARY }

/**
 * 15 karınca sınıfı. İşçi sınıfları ortak iş yapay zekâsını tercih ettikleri görevle kullanır; askerî sınıflar
 * ayrı bir savaş yapay zekâsıyla hareket eder. Sınıflar Eğitim Odasında işçilerden yetiştirilir.
 */
enum class AntType(
    val title: String,
    val caste: Caste,
    val maxHp: Float,
    val speed: Float,
    val carry: Int,
    val efficiency: Float,
    val attack: Float,
    val defense: Float,
    val preferred: TaskCategory? = null,
    val trainCost: Map<ResourceType, Int> = emptyMap(),
    val trainTime: Float = 0f,
    val requires: String? = null,
    val requiresRoom: RoomType? = null,
    val description: String = "",
) {
    QUEEN("Kraliçe", Caste.QUEEN, 200f, 0f, 0, 0f, 2f, 4f),
    WORKER("İşçi", Caste.WORKER, 30f, 1.7f, 2, 1f, 2f, 1f, description = "Her işi yapan temel karınca."),
    GATHERER("Toplayıcı", Caste.WORKER, 30f, 1.95f, 3, 1.1f, 2f, 1f, TaskCategory.GATHER, mapOf(ResourceType.FOOD to 4), 20f, "cb_castes", description = "Hızlı, 3 birim taşır, toplamaya öncelik verir."),
    DIGGER("Kazıcı", Caste.WORKER, 34f, 1.6f, 2, 1.5f, 2f, 1.5f, TaskCategory.DIG, mapOf(ResourceType.FOOD to 4, ResourceType.SOIL to 4), 20f, "cb_castes", description = "%50 hızlı kazar."),
    CARRIER("Taşıyıcı", Caste.WORKER, 32f, 1.8f, 4, 1f, 1.5f, 1.5f, TaskCategory.HAUL, mapOf(ResourceType.FOOD to 4), 20f, "cb_castes", description = "4 birim taşır, taşımaya öncelik verir."),
    NURSE("Bakıcı", Caste.WORKER, 28f, 1.7f, 2, 1.2f, 1f, 1f, TaskCategory.NURSE, mapOf(ResourceType.FOOD to 5), 20f, "cb_castes", description = "Larva bakımında uzman."),
    SCOUT("Keşifçi", Caste.WORKER, 26f, 2.5f, 1, 1f, 2f, 1f, TaskCategory.EXPLORE, mapOf(ResourceType.FOOD to 5), 22f, "cb_castes", description = "Çok hızlı; yüzeyde ve yeraltında keşif yapar, rakipleri bulur."),
    MEDIC("Şifacı", Caste.WORKER, 32f, 1.8f, 1, 1f, 1f, 2f, TaskCategory.NURSE, mapOf(ResourceType.MUSHROOM to 4, ResourceType.WATER to 2), 24f, "cb_medicine", description = "Yaralı ve hasta karıncaları iyileştirir."),
    ENGINEER("Mühendis", Caste.WORKER, 34f, 1.7f, 2, 1.4f, 2f, 2f, TaskCategory.BUILD, mapOf(ResourceType.FOOD to 5, ResourceType.WOOD to 2), 24f, "cb_castes", description = "İnşa ve dolguda %40 hızlı."),
    TUNNELER("Tünel Uzmanı", Caste.WORKER, 36f, 1.7f, 2, 2f, 2f, 2f, TaskCategory.DIG, mapOf(ResourceType.FOOD to 6, ResourceType.MINERAL to 2), 28f, "dig_rock", description = "İki kat hızlı kazar, kayayı da kazabilir."),
    SOLDIER("Asker", Caste.MILITARY, 50f, 1.8f, 1, 0.6f, 6f, 3f, null, mapOf(ResourceType.FOOD to 8), 26f, "mil_soldiers", RoomType.BARRACKS, "Temel savaşçı."),
    HEAVY_SOLDIER("Ağır Asker", Caste.MILITARY, 90f, 1.3f, 1, 0.5f, 9f, 6f, null, mapOf(ResourceType.FOOD to 10, ResourceType.MINERAL to 3), 34f, "mil_heavy", RoomType.BARRACKS, "Yavaş ama çok dayanıklı ve güçlü."),
    DEFENDER("Savunmacı", Caste.MILITARY, 80f, 1.4f, 1, 0.5f, 5f, 7f, null, mapOf(ResourceType.FOOD to 8, ResourceType.SOIL to 6), 30f, "mil_defense", RoomType.BARRACKS, "Dar geçitleri tutar; savunma odasında nöbet bekler."),
    ELITE("Elit Savaşçı", Caste.MILITARY, 120f, 2f, 1, 0.6f, 14f, 8f, null, mapOf(ResourceType.FOOD to 15, ResourceType.RARE_MINERAL to 1, ResourceType.ENZYME to 1), 45f, "mil_elite", RoomType.ELITE_QUARTERS, "Hızlı ve ölümcül."),
    ROYAL_GUARD("Kraliyet Muhafızı", Caste.MILITARY, 150f, 1.6f, 1, 0.5f, 12f, 12f, null, mapOf(ResourceType.FOOD to 15, ResourceType.ROYAL_ESSENCE to 1), 45f, "mil_elite", RoomType.ROYAL_CENTER, "Kraliçeyi korur, onun yanından ayrılmaz."),
    ;

    val military: Boolean get() = caste == Caste.MILITARY
    val trainable: Boolean get() = trainTime > 0f
}

enum class AntActivity(val title: String) {
    IDLE("Boşta"), DIGGING("Kazıyor"), HAULING("Taşıyor"), GATHERING("Topluyor"), BUILDING("İnşa ediyor"),
    PRODUCING("Üretim"), NURSING("Larva bakımı"), EATING("Yemek yiyor"), RESTING("Dinleniyor"), WALKING("Yürüyor"),
    RESEARCHING("Araştırıyor"), TRAINING("Eğitimde"), EXPLORING("Keşifte"), FIGHTING("Savaşıyor"), FLEEING("Kaçıyor"),
    HEALING("İyileştiriyor"), FILLING("Tünel dolduruyor"), CLEANING("Temizlik"), GUARDING("Nöbette"), MARCHING("Sefer yürüyüşü"),
}

class Ant(val id: Int, var type: AntType, var x: Float, var y: Float) {
    var prevX = x
    var prevY = y
    var hp = type.maxHp
    var hunger = 10f
    var energy = 100f
    var xp = 0
    var age = 0f
    var facing = 0f
    var walkPhase = 0f
    var assignment: TaskCategory? = null
    var carryType: ResourceType? = null
    var carryAmount = 0
    var carryingCorpse = false
    var job: Job? = null
    var activity = AntActivity.IDLE
    var path: IntArray? = null
    var pathIndex = 0
    var pathTarget = Long.MIN_VALUE
    var waitTimer = 0f
    var starving = false
    var eatCooldown = 0f
    var sick = false
    var squad = 0
    var attackCooldown = 0f
    var lastHit = -10.0
    var ambushReady = false
    var targetId = -1

    val cellX get() = kotlin.math.floor(x).toInt()
    val cellY get() = kotlin.math.floor(y).toInt()
    val level: Int get() = kotlin.math.sqrt(xp / 8.0).toInt().coerceAtMost(8)

    fun workRate(category: TaskCategory?, mods: Modifiers? = null): Float {
        var r = type.efficiency * (1f + level * 0.05f)
        if (category != null && assignment == category) r *= 1.15f
        if (starving) r *= 0.5f
        if (sick) r *= 0.6f
        if (mods != null) r *= mods.workMult
        return r
    }

    fun maxHp(mods: Modifiers?): Float = type.maxHp * (mods?.hpMult ?: 1f)

    fun carryCapacity(mods: Modifiers?): Int = type.carry + (if (level >= 4) 1 else 0) + (mods?.carryBonus ?: 0)

    fun clearPath() { path = null; pathIndex = 0; pathTarget = Long.MIN_VALUE }

    /** Sınıf değişince (eğitim) can oranı korunur ve tercih edilen görev uzmanlık olur. */
    fun becomeType(t: AntType) {
        val ratio = hp / type.maxHp
        type = t
        hp = t.maxHp * ratio
        assignment = t.preferred
    }
}

enum class BroodStage(val title: String, val duration: Float) { EGG("Yumurta", 40f), LARVA("Larva", 90f), PUPA("Pupa", 40f) }

class Brood(val id: Int, var stage: BroodStage, var roomId: Int) {
    var progress = 0f
    var fed = 1f
    var nurseReservedBy = -1
}
