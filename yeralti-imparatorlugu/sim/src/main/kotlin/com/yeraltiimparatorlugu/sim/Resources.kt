package com.yeraltiimparatorlugu.sim

enum class StorageKind(val title: String) { FOOD("Besin"), MATERIAL("Malzeme"), WATER("Su") }

/** Kaynak türleri. [nutrition] > 0 olanlar yenebilir; [advanced] gelişmiş kaynaklardır. */
enum class ResourceType(val title: String, val storage: StorageKind, val nutrition: Float = 0f, val advanced: Boolean = false) {
    FOOD("Yiyecek", StorageKind.FOOD, 34f),
    SEED("Tohum", StorageKind.FOOD, 26f),
    MUSHROOM("Mantar", StorageKind.FOOD, 40f),
    LEAF("Yaprak", StorageKind.MATERIAL),
    WOOD("Odun parçası", StorageKind.MATERIAL),
    SOIL("Toprak malzemesi", StorageKind.MATERIAL),
    WATER("Su", StorageKind.WATER),
    MINERAL("Mineral", StorageKind.MATERIAL),
    FOSSIL("Fosil", StorageKind.MATERIAL, advanced = true),
    RESIN("Reçine", StorageKind.MATERIAL, advanced = true),
    ENZYME("Özel enzim", StorageKind.MATERIAL, advanced = true),
    RARE_MINERAL("Nadir mineral", StorageKind.MATERIAL, advanced = true),
    GENETIC("Genetik örnek", StorageKind.MATERIAL, advanced = true),
    ROYAL_ESSENCE("Kraliyet özü", StorageKind.MATERIAL, advanced = true),
    ;

    val edible: Boolean get() = nutrition > 0f
}

/** Koloninin toplam stoğu. Kapasite depo odalarından hesaplanır ([Simulation.capacityOf]). */
class ResourceStore {
    private val amounts = IntArray(ResourceType.entries.size)

    operator fun get(type: ResourceType): Int = amounts[type.ordinal]

    fun set(type: ResourceType, value: Int) { amounts[type.ordinal] = value.coerceAtLeast(0) }

    fun add(type: ResourceType, amount: Int) { amounts[type.ordinal] = (amounts[type.ordinal] + amount).coerceAtLeast(0) }

    fun take(type: ResourceType, amount: Int): Int {
        val taken = minOf(amount, amounts[type.ordinal])
        amounts[type.ordinal] -= taken
        return taken
    }

    fun totalOf(kind: StorageKind): Int = ResourceType.entries.filter { it.storage == kind }.sumOf { amounts[it.ordinal] }

    fun hasAll(cost: Map<ResourceType, Int>): Boolean = cost.all { (t, n) -> this[t] >= n }

    fun pay(cost: Map<ResourceType, Int>): Boolean {
        if (!hasAll(cost)) return false
        cost.forEach { (t, n) -> amounts[t.ordinal] -= n }
        return true
    }

    fun refund(cost: Map<ResourceType, Int>, fraction: Float) { cost.forEach { (t, n) -> add(t, (n * fraction).toInt()) } }

    fun edibleTotal(): Int = ResourceType.entries.filter { it.edible }.sumOf { amounts[it.ordinal] }

    fun bestEdible(): ResourceType? = ResourceType.entries.filter { it.edible && this[it] > 0 }.maxByOrNull { this[it] }

    fun toMap(): Map<String, Int> = ResourceType.entries.associate { it.name to amounts[it.ordinal] }

    fun loadFrom(map: Map<String, Int>) {
        amounts.fill(0)
        map.forEach { (k, v) -> ResourceType.entries.firstOrNull { it.name == k }?.let { amounts[it.ordinal] = v.coerceAtLeast(0) } }
    }
}
