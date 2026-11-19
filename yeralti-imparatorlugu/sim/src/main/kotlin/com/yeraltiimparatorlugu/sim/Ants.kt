package com.yeraltiimparatorlugu.sim

/** Karınca türleri ve temel özellikleri. Askerî türler savaş aşamasında eklenecek. */
enum class AntType(
    val title: String,
    val maxHp: Float,
    val speed: Float,
    val carry: Int,
    val efficiency: Float,
    val attack: Float,
    val defense: Float,
) {
    QUEEN("Kraliçe", 200f, 0f, 0, 0f, 2f, 4f),
    WORKER("İşçi", 30f, 1.7f, 2, 1f, 2f, 1f),
}

enum class AntActivity(val title: String) {
    IDLE("Boşta"),
    DIGGING("Kazıyor"),
    HAULING("Taşıyor"),
    GATHERING("Topluyor"),
    BUILDING("İnşa ediyor"),
    PRODUCING("Üretim"),
    NURSING("Larva bakımı"),
    EATING("Yemek yiyor"),
    RESTING("Dinleniyor"),
    WALKING("Yürüyor"),
}

class Ant(val id: Int, val type: AntType, var x: Float, var y: Float) {
    var prevX = x
    var prevY = y
    var hp = type.maxHp
    var hunger = 10f
    var energy = 100f
    var xp = 0
    var age = 0f
    var facing = 0f
    var walkPhase = 0f

    /** Oyuncunun atadığı uzmanlık (null: otomatik). */
    var assignment: TaskCategory? = null

    var carryType: ResourceType? = null
    var carryAmount = 0

    var job: Job? = null
    var activity = AntActivity.IDLE

    // Yol takibi
    var path: IntArray? = null
    var pathIndex = 0
    var pathTarget = Long.MIN_VALUE
    var waitTimer = 0f
    var starving = false
    var eatCooldown = 0f

    val cellX get() = kotlin.math.floor(x).toInt()
    val cellY get() = kotlin.math.floor(y).toInt()

    val level: Int get() = kotlin.math.sqrt(xp / 8.0).toInt().coerceAtMost(8)

    /** Deneyimle artan iş verimi; uzmanlar kendi alanında +%15 alır. */
    fun workRate(category: TaskCategory?): Float {
        var r = type.efficiency * (1f + level * 0.05f)
        if (category != null && assignment == category) r *= 1.15f
        if (starving) r *= 0.5f
        return r
    }

    val carryCapacity: Int get() = type.carry + if (level >= 4) 1 else 0

    fun clearPath() {
        path = null
        pathIndex = 0
        pathTarget = Long.MIN_VALUE
    }
}

enum class BroodStage(val title: String, val duration: Float) {
    EGG("Yumurta", 40f),
    LARVA("Larva", 90f),
    PUPA("Pupa", 40f),
}

/** Yumurta → larva → pupa → yetişkin işçi. Larvalar beslenmezse gelişmez. */
class Brood(val id: Int, var stage: BroodStage, var roomId: Int) {
    var progress = 0f

    /** 0..1: larvanın tokluğu. Zamanla azalır, bakıcılar besleyerek doldurur. */
    var fed = 1f
    var nurseReservedBy = -1
}
