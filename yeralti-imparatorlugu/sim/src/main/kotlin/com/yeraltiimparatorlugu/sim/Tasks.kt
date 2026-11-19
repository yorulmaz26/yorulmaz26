package com.yeraltiimparatorlugu.sim

/** Görev kategorileri; hepsi çalışır ve öncelikleri ayarlanabilir. */
enum class TaskCategory(val title: String) {
    DEFENSE("Savunma"),
    DIG("Kazı"),
    BUILD("İnşa"),
    GATHER("Toplama"),
    HAUL("Taşıma"),
    PRODUCE("Üretim"),
    NURSE("Larva bakımı"),
    RESEARCH("Araştırma"),
    EXPLORE("Keşif"),
    REPAIR("Onarım ve dolgu"),
    CLEAN("Temizlik"),
    ;

    companion object {
        val active: List<TaskCategory> get() = entries
    }
}

/**
 * Görev öncelikleri: 1 en yüksek, 5 en düşük, 0 kapalı. Karıncalar önce en yüksek öncelikli kategoride iş arar;
 * aynı öncelikte en yakın işi seçer.
 */
class TaskPriorities {
    private val values = IntArray(TaskCategory.entries.size) { 3 }

    init {
        set(TaskCategory.DEFENSE, 3)
        set(TaskCategory.NURSE, 1)
        set(TaskCategory.GATHER, 2)
        set(TaskCategory.DIG, 2)
        set(TaskCategory.BUILD, 2)
        set(TaskCategory.PRODUCE, 2)
        set(TaskCategory.HAUL, 3)
        set(TaskCategory.RESEARCH, 3)
        set(TaskCategory.REPAIR, 2)
        set(TaskCategory.EXPLORE, 4)
        set(TaskCategory.CLEAN, 4)
    }

    operator fun get(c: TaskCategory) = values[c.ordinal]

    fun set(c: TaskCategory, p: Int) { values[c.ordinal] = p.coerceIn(0, 5) }

    fun orderedGroups(): List<List<TaskCategory>> =
        TaskCategory.active.filter { this[it] > 0 }.groupBy { this[it] }.toSortedMap().values.toList()

    fun toMap(): Map<String, Int> = TaskCategory.entries.associate { it.name to values[it.ordinal] }

    fun loadFrom(map: Map<String, Int>) {
        map.forEach { (k, v) -> TaskCategory.entries.firstOrNull { it.name == k }?.let { set(it, v) } }
    }
}

class DigOrder(val x: Int, val y: Int, val roomId: Int = -1) {
    var progress = 0f
    var reservedBy = -1
    var unreachableUntil = 0.0
}

/** "Tüneli kapat": yürünebilir bir hücreyi toprakla doldurma emri. */
class FillOrder(val x: Int, val y: Int) {
    var progress = 0f
    var reservedBy = -1
    var unreachableUntil = 0.0
}

class Pile(val x: Int, val y: Int, val type: ResourceType, var amount: Int) {
    var reservedBy = -1
    var unreachableUntil = 0.0
}

/** Ölen karınca veya canlı kalıntısı; temizlenmezse hastalık riskini artırır. */
class Corpse(val id: Int, var x: Int, var y: Int, val title: String) {
    var reservedBy = -1
    var age = 0f
}

enum class SourceKind(val title: String, val resource: ResourceType, val gatherTime: Float) {
    LEAF_BUSH("Yaprak öbeği", ResourceType.LEAF, 1.6f),
    SEED_PILE("Tohum yığını", ResourceType.SEED, 1.4f),
    DEAD_INSECT("Böcek kalıntısı", ResourceType.FOOD, 2.0f),
    TWIGS("Dal parçaları", ResourceType.WOOD, 2.2f),
    DEW("Çiy damlaları", ResourceType.WATER, 1.0f),
    RESIN_DROP("Reçine damlası", ResourceType.RESIN, 3.0f),
    CRUMBS("Ekmek kırıntısı", ResourceType.FOOD, 1.2f),
    PEBBLES("Mineral çakılları", ResourceType.MINERAL, 2.6f),
}

class FoodSource(val id: Int, val x: Int, val kind: SourceKind, var amount: Int) {
    var maxAmount = amount
    var reserved = 0
    var unreachableUntil = 0.0
    var spawnedAt = 0.0
}
