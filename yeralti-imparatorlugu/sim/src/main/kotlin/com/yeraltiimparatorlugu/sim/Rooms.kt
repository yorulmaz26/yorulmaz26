package com.yeraltiimparatorlugu.sim

import com.yeraltiimparatorlugu.sim.ResourceType.FOOD
import com.yeraltiimparatorlugu.sim.ResourceType.LEAF
import com.yeraltiimparatorlugu.sim.ResourceType.SOIL
import com.yeraltiimparatorlugu.sim.ResourceType.WOOD

/**
 * Oda türleri ve dengeleme verileri. Bu sürümde yalnızca işlevi gerçekten çalışan odalar listelenir;
 * diğer odalar (kışla, laboratuvar vb.) ilgili sistemlerle birlikte eklenecektir.
 */
enum class RoomType(
    val title: String,
    val description: String,
    val width: Int,
    val height: Int,
    val cost: Map<ResourceType, Int>,
    val buildWork: Float,
    /** Günlük bakım (toprak malzemesi). Ödenmezse oda %50 verimle çalışır. */
    val maintenanceSoil: Int,
    val maxLevel: Int,
    val buildable: Boolean = true,
    /** Komşu oda türü -> verim çarpanı artışı. */
    val adjacency: Map<RoomType, Float> = emptyMap(),
) {
    QUEEN_CHAMBER(
        "Kraliçe Odası", "Kraliçenin yuvası. 8 karıncalık barınak, 2 yumurta yeri, küçük besin ve malzeme yığını sağlar.",
        5, 3, emptyMap(), 0f, 0, 3, buildable = false,
    ),
    FOOD_STORAGE(
        "Besin Deposu", "Yiyecek, tohum ve mantar saklar (seviye başına 60). Toplayıcılar en yakın depoya taşır.",
        4, 3, mapOf(SOIL to 10, WOOD to 2), 20f, 1, 3,
    ),
    MATERIAL_STORAGE(
        "Malzeme Deposu", "Yaprak, odun, toprak, su, mineral ve fosil saklar (seviye başına 80).",
        4, 3, mapOf(SOIL to 8, LEAF to 4), 18f, 1, 3,
    ),
    REST_ROOM(
        "İşçi Dinlenme Odası", "Seviye başına +10 barınak ve 6 dinlenme yeri. Burada dinlenen işçiler 2,5 kat hızlı toparlanır.",
        4, 2, mapOf(SOIL to 12, LEAF to 6), 22f, 1, 3,
    ),
    EGG_ROOM(
        "Yumurta Odası", "Seviye başına 6 yumurta yeri. Kraliçe Odasına komşuysa yumurtalar %25 hızlı gelişir.",
        4, 2, mapOf(SOIL to 10, LEAF to 4, FOOD to 8), 20f, 1, 3,
        adjacency = mapOf(QUEEN_CHAMBER to 0.25f),
    ),
    LARVA_ROOM(
        "Larva Bakım Odası", "Seviye başına 4 larva yeri; larvalar %50 hızlı büyür. Besin Deposuna komşuysa +%20.",
        4, 2, mapOf(SOIL to 12, LEAF to 8, FOOD to 10), 24f, 1, 3,
        adjacency = mapOf(FOOD_STORAGE to 0.2f),
    ),
    FUNGUS_FARM(
        "Mantar Çiftliği", "Taşınan yaprakları mantara çevirir (1 yaprak → 2 mantar). Besin Deposuna komşuysa +%20 üretim.",
        5, 3, mapOf(SOIL to 14, LEAF to 10, WOOD to 4), 30f, 2, 3,
        adjacency = mapOf(FOOD_STORAGE to 0.2f, MATERIAL_STORAGE to 0.1f),
    ),
    ;

    /** Seviye yükseltme maliyeti: temel maliyet x (yeni seviye). */
    fun upgradeCost(toLevel: Int): Map<ResourceType, Int> =
        (if (cost.isEmpty()) mapOf(SOIL to 20, LEAF to 10) else cost).mapValues { it.value * toLevel }
}

enum class RoomState(val title: String) {
    EXCAVATING("Kazılıyor"),
    CONSTRUCTING("İnşa ediliyor"),
    ACTIVE("Çalışıyor"),
    UPGRADING("Geliştiriliyor"),
}

class Room(val id: Int, val type: RoomType, val x: Int, val y: Int) {
    var level = 1
    var state = RoomState.EXCAVATING
    var buildProgress = 0f
    var builders = 0
    var maintained = true

    // Mantar çiftliği
    var leafBuffer = 0
    var growProgress = 0f
    var supplyReserved = 0

    val width get() = type.width
    val height get() = type.height

    /** Karıncaların odada durduğu nokta (alt sıranın ortası). */
    val anchorX get() = x + width / 2
    val anchorY get() = y + height - 1

    val functional get() = state == RoomState.ACTIVE || state == RoomState.UPGRADING

    fun contains(cx: Int, cy: Int) = cx >= x && cx < x + width && cy >= y && cy < y + height

    inline fun forEachCell(f: (Int, Int) -> Unit) {
        for (yy in y until y + height) for (xx in x until x + width) f(xx, yy)
    }

    /** İki oda en fazla 2 hücre arayla duruyorsa komşu sayılır. */
    fun isAdjacentTo(o: Room): Boolean {
        if (o === this) return false
        val gapX = maxOf(o.x - (x + width), x - (o.x + o.width), 0)
        val gapY = maxOf(o.y - (y + height), y - (o.y + o.height), 0)
        return gapX <= 2 && gapY <= 2 && (gapX == 0 || gapY == 0)
    }

    val workRequired: Float get() = if (state == RoomState.UPGRADING) type.buildWork * level else type.buildWork
}
