package com.yeraltiimparatorlugu.sim

/**
 * Görev kategorileri. [implemented] olmayanlar ilgili sistem geldiğinde etkinleşir ve arayüzde gösterilmez.
 */
enum class TaskCategory(val title: String, val implemented: Boolean) {
    DIG("Kazı", true),
    BUILD("İnşa", true),
    GATHER("Toplama", true),
    HAUL("Taşıma", true),
    PRODUCE("Üretim", true),
    NURSE("Larva bakımı", true),
    DEFENSE("Savunma", false),
    EXPLORE("Keşif", false),
    REPAIR("Onarım", false),
    CLEAN("Temizlik", false),
    ;

    companion object {
        val active: List<TaskCategory> get() = entries.filter { it.implemented }
    }
}

/**
 * Görev öncelikleri: 1 en yüksek, 5 en düşük, 0 kapalı. Karıncalar önce en yüksek öncelikli
 * kategoride iş arar; aynı öncelikte en yakın işi seçer.
 */
class TaskPriorities {
    private val values = IntArray(TaskCategory.entries.size) { 3 }

    init {
        set(TaskCategory.GATHER, 2)
        set(TaskCategory.NURSE, 1)
        set(TaskCategory.HAUL, 3)
        set(TaskCategory.DIG, 2)
        set(TaskCategory.BUILD, 2)
        set(TaskCategory.PRODUCE, 2)
    }

    operator fun get(c: TaskCategory) = values[c.ordinal]

    fun set(c: TaskCategory, p: Int) {
        values[c.ordinal] = p.coerceIn(0, 5)
    }

    /** Etkin kategorileri öncelik sırasına göre gruplar. */
    fun orderedGroups(): List<List<TaskCategory>> =
        TaskCategory.active.filter { this[it] > 0 }.groupBy { this[it] }.toSortedMap().values.toList()

    fun toMap(): Map<String, Int> = TaskCategory.entries.associate { it.name to values[it.ordinal] }

    fun loadFrom(map: Map<String, Int>) {
        map.forEach { (k, v) -> TaskCategory.entries.firstOrNull { it.name == k }?.let { set(it, v) } }
    }
}

/** Oyuncunun işaretlediği kazı hücresi. */
class DigOrder(val x: Int, val y: Int, val roomId: Int = -1) {
    var progress = 0f
    var reservedBy = -1
    var unreachableUntil = 0.0
}

/** Yerde duran kaynak yığını (kazı artığı, çiftlik ürünü, taşınamayan fazla). */
class Pile(val x: Int, val y: Int, val type: ResourceType, var amount: Int) {
    var reservedBy = -1
    var unreachableUntil = 0.0
}

enum class SourceKind(val title: String, val resource: ResourceType, val gatherTime: Float) {
    LEAF_BUSH("Yaprak öbeği", ResourceType.LEAF, 1.6f),
    SEED_PILE("Tohum yığını", ResourceType.SEED, 1.4f),
    DEAD_INSECT("Böcek kalıntısı", ResourceType.FOOD, 2.0f),
    TWIGS("Dal parçaları", ResourceType.WOOD, 2.2f),
    DEW("Çiy damlaları", ResourceType.WATER, 1.0f),
}

/** Yüzeydeki toplanabilir kaynak noktası (y = -1 şeridinde). */
class FoodSource(val id: Int, val x: Int, val kind: SourceKind, var amount: Int) {
    val maxAmount = amount
    var reserved = 0
    var spawnedAt = 0.0
    var unreachableUntil = 0.0
}
