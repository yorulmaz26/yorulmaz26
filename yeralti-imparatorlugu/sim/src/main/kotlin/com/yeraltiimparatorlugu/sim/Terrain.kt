package com.yeraltiimparatorlugu.sim

/**
 * Hücre türleri. [digWork] iş birimidir: verimliliği 1 olan bir işçi saniyede 1 birim kazar.
 * [needsHardRockTech] olan hücreler, sert kaya araştırması açılmadan kazılamaz.
 */
enum class Terrain(
    val id: Int,
    val title: String,
    val digWork: Float,
    val walkable: Boolean,
    val yieldType: ResourceType?,
    val yieldAmount: Int,
    val needsHardRockTech: Boolean = false,
    val description: String,
) {
    AIR(0, "Yüzey", 0f, true, null, 0, description = "Açık hava."),
    TUNNEL(1, "Tünel", 0f, true, null, 0, description = "Kazılmış, yürünebilir boşluk."),
    SOFT_SOIL(2, "Yumuşak toprak", 2f, false, ResourceType.SOIL, 1, description = "Hızlı kazılır. Geniş boşluklarda çökebilir."),
    HARD_SOIL(3, "Sert toprak", 4f, false, ResourceType.SOIL, 1, description = "Dayanıklı ama yavaş kazılır."),
    CLAY(4, "Kil", 5f, false, ResourceType.SOIL, 2, description = "Bol inşa malzemesi verir."),
    SAND(5, "Kum", 1.5f, false, ResourceType.SOIL, 1, description = "Çok hızlı kazılır, en kolay çöken zemindir."),
    ROCK(6, "Kaya", 14f, false, ResourceType.MINERAL, 1, needsHardRockTech = true, description = "Kazmak için Sert Kaya Kazma araştırması gerekir."),
    WATER_SOIL(7, "Su taşıyan katman", 3f, false, ResourceType.WATER, 2, description = "Kazıldığında su verir."),
    FUNGUS(8, "Mantar bölgesi", 2.5f, false, ResourceType.MUSHROOM, 3, description = "Doğal mantar yatağı: yiyecek kaynağı."),
    MINERAL(9, "Mineral damarı", 8f, false, ResourceType.MINERAL, 2, description = "Değerli mineral verir."),
    FOSSIL(10, "Fosil alanı", 6f, false, ResourceType.FOSSIL, 1, description = "Nadir fosil parçası içerir."),
    RUBBLE(11, "Moloz", 1f, false, ResourceType.SOIL, 1, description = "Çökme sonrası oluşan gevşek dolgu."),
    ;

    val diggable: Boolean get() = !walkable

    companion object {
        private val byId = entries.associateBy { it.id }
        fun of(id: Int): Terrain = byId[id] ?: SOFT_SOIL
    }
}
