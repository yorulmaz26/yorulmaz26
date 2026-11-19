package com.yeraltiimparatorlugu.sim

/**
 * Hücre türleri. [digWork] iş birimidir: verimliliği 1 olan bir işçi saniyede 1 birim kazar.
 * [needsHardRockTech] olan hücreler Sert Kaya Kazma araştırması (veya Tünel Uzmanı) olmadan kazılamaz.
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
    SAND(5, "Kum", 1.5f, false, ResourceType.SOIL, 1, description = "Çok hızlı kazılır, en kolay çöken zemindir. Suyu iyi emer."),
    ROCK(6, "Kaya", 14f, false, ResourceType.MINERAL, 1, needsHardRockTech = true, description = "Sert Kaya Kazma araştırması veya Tünel Uzmanı gerekir."),
    WATER_SOIL(7, "Su taşıyan katman", 3f, false, ResourceType.WATER, 2, description = "Kazıldığında su verir; yağmurda yanındaki tünellere su sızar."),
    FUNGUS(8, "Mantar bölgesi", 2.5f, false, ResourceType.MUSHROOM, 3, description = "Doğal mantar yatağı: yiyecek kaynağı."),
    MINERAL(9, "Mineral damarı", 8f, false, ResourceType.MINERAL, 2, description = "Değerli mineral verir."),
    FOSSIL(10, "Fosil alanı", 6f, false, ResourceType.FOSSIL, 1, description = "Nadir fosil parçası içerir."),
    RUBBLE(11, "Moloz", 1f, false, ResourceType.SOIL, 1, description = "Çökme veya dolgu sonrası oluşan gevşek toprak."),
    RARE_VEIN(12, "Nadir mineral damarı", 12f, false, ResourceType.RARE_MINERAL, 1, needsHardRockTech = true, description = "Derinlerde bulunan parlak kristaller."),
    ANCIENT_NEST(13, "Eski yuva", 1.5f, false, ResourceType.SOIL, 1, description = "Terk edilmiş bir koloninin kalıntısı: içinden erzak, fosil veya genetik örnek çıkabilir."),
    CREATURE_DEN(14, "Canlı yuvası", 3f, false, ResourceType.SOIL, 1, description = "Tehlikeli bir canlının yuvası. Açılırsa içindeki canlı uyanır."),
    WATER_POCKET(15, "Yeraltı gölü", 1f, false, ResourceType.WATER, 3, description = "Kazılırsa içindeki su tünellere boşalır!"),
    RIVAL_TUNNEL(16, "Rakip tüneli", 0f, true, null, 0, description = "Rakip bir koloninin kazdığı tünel. Bağlantı kurulursa rakipler buradan gelebilir."),
    FILL(17, "Dolgu", 3f, false, ResourceType.SOIL, 1, description = "Tüneli kapatmak için sıkıştırılmış toprak."),
    ;

    val diggable: Boolean get() = !walkable

    companion object {
        private val byId = entries.associateBy { it.id }
        fun of(id: Int): Terrain = byId[id] ?: SOFT_SOIL
    }
}
