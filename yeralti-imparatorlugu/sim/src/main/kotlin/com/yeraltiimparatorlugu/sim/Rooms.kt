package com.yeraltiimparatorlugu.sim

import com.yeraltiimparatorlugu.sim.ResourceType.ENZYME
import com.yeraltiimparatorlugu.sim.ResourceType.FOOD
import com.yeraltiimparatorlugu.sim.ResourceType.FOSSIL
import com.yeraltiimparatorlugu.sim.ResourceType.LEAF
import com.yeraltiimparatorlugu.sim.ResourceType.MINERAL
import com.yeraltiimparatorlugu.sim.ResourceType.RARE_MINERAL
import com.yeraltiimparatorlugu.sim.ResourceType.RESIN
import com.yeraltiimparatorlugu.sim.ResourceType.ROYAL_ESSENCE
import com.yeraltiimparatorlugu.sim.ResourceType.SOIL
import com.yeraltiimparatorlugu.sim.ResourceType.WATER
import com.yeraltiimparatorlugu.sim.ResourceType.WOOD

/**
 * 25 oda türü ve dengeleme verileri. [requires] bir araştırma kimliğidir; araştırılmadan oda inşa edilemez.
 * [stationSlots] > 0 olan odalarda işçiler çalışarak üretim/araştırma/eğitim yapar.
 */
enum class RoomType(
    val title: String,
    val description: String,
    val width: Int,
    val height: Int,
    val cost: Map<ResourceType, Int>,
    val buildWork: Float,
    val maintenanceSoil: Int,
    val maxLevel: Int,
    val buildable: Boolean = true,
    val requires: String? = null,
    val stationSlots: Int = 0,
) {
    QUEEN_CHAMBER("Kraliçe Odası", "Kraliçenin yuvası. 8 barınak, 2 yumurta yeri, küçük besin ve malzeme yığını.", 5, 3, emptyMap(), 0f, 0, 3, buildable = false),
    EGG_ROOM("Yumurta Odası", "Seviye başına 6 yumurta yeri. Kraliçe Odasına komşuysa yumurtalar %25 hızlı gelişir.", 4, 2, mapOf(SOIL to 10, LEAF to 4, FOOD to 8), 20f, 1, 3),
    LARVA_ROOM("Larva Bakım Odası", "Seviye başına 4 larva yeri; larvalar %50 hızlı büyür. Besin Deposuna komşuysa +%20.", 4, 2, mapOf(SOIL to 12, LEAF to 8, FOOD to 10), 24f, 1, 3),
    FOOD_STORAGE("Besin Deposu", "Yiyecek, tohum ve mantar saklar (seviye başına 60).", 4, 3, mapOf(SOIL to 10, WOOD to 2), 20f, 1, 3),
    MATERIAL_STORAGE("Malzeme Deposu", "Yaprak, odun, toprak, mineral ve gelişmiş kaynakları saklar (seviye başına 80).", 4, 3, mapOf(SOIL to 8, LEAF to 4), 18f, 1, 3),
    FUNGUS_FARM("Mantar Çiftliği", "İşçilerin taşıdığı yaprakları mantara çevirir (1 yaprak → 2 mantar, 20 sn). Besin Deposuna komşuysa +%20.", 5, 3, mapOf(SOIL to 14, LEAF to 10, WOOD to 4), 30f, 2, 3),
    REST_ROOM("İşçi Dinlenme Odası", "Seviye başına +10 barınak ve 6 dinlenme yeri; burada dinlenme 2,5 kat hızlıdır.", 4, 2, mapOf(SOIL to 12, LEAF to 6), 22f, 1, 3),
    BARRACKS("Asker Kışlası", "Seviye başına +8 barınak; askerler burada dinlenir. Asker eğitimi için gereklidir.", 5, 2, mapOf(SOIL to 18, WOOD to 6, FOOD to 10), 30f, 2, 3, requires = "mil_soldiers"),
    TRAINING_ROOM("Eğitim Odası", "İşçileri uzman sınıflara (toplayıcı, kazıcı, asker…) dönüştürür. Seviye başına 2 eğitim yeri.", 4, 2, mapOf(SOIL to 14, WOOD to 4, FOOD to 10), 26f, 1, 3, requires = "cb_castes", stationSlots = 2),
    RESEARCH_LAB("Araştırma Laboratuvarı", "İşçiler burada araştırma yapar. Seviye başına 2 araştırmacı yeri.", 4, 2, mapOf(SOIL to 12, LEAF to 6, WOOD to 4), 24f, 1, 3, stationSlots = 2),
    DEFENSE_ROOM("Savunma Odası", "İçinde ve 3 hücre çevresinde dost karıncalara +%30 savunma. Savunmacıların nöbet yeri.", 4, 2, mapOf(SOIL to 20, MINERAL to 4, WOOD to 4), 30f, 2, 3, requires = "mil_defense"),
    TRAP_ROOM("Tuzak Odası", "İçinden geçen düşmanlara saniyede seviye×4 hasar verir ve onları yavaşlatır. Giriş yollarına kurun.", 3, 1, mapOf(SOIL to 16, RESIN to 2, WOOD to 4), 24f, 2, 3, requires = "mil_traps"),
    VENTILATION("Havalandırma Odası", "Koloni sıcaklığını seviye başına %30 ideale (24°C) yaklaştırır, hastalık yayılmasını azaltır.", 3, 3, mapOf(SOIL to 14, WOOD to 4), 22f, 1, 3, requires = "eco_ventilation"),
    WATER_STORAGE("Su Deposu", "Seviye başına 60 su saklar; hücrelerine gelen sel suyunu toplayıp depolar.", 4, 2, mapOf(SOIL to 14, RESIN to 1, LEAF to 6), 24f, 1, 3, requires = "eco_water"),
    HUMIDITY_CONTROL("Nem Kontrol Odası", "Su harcayarak koloni nemini ideale (%60) çeker; yavru gelişimini korur.", 3, 2, mapOf(SOIL to 12, WATER to 10, WOOD to 4), 22f, 1, 3, requires = "eco_humidity"),
    INFIRMARY("Sağlık Odası", "Burada dinlenen karıncalar 3 kat hızlı iyileşir ve hastalıktan kurtulur. Yaralı askerler buraya çekilir.", 4, 2, mapOf(SOIL to 14, ResourceType.MUSHROOM to 6, WATER to 6), 26f, 1, 3, requires = "cb_medicine"),
    EXPLORATION_CENTER("Keşif Merkezi", "Uzak bölgelere keşif seferi gönderir. Seviye başına 1 sefer yeri.", 4, 2, mapOf(SOIL to 16, WOOD to 8, FOOD to 15), 28f, 1, 3, requires = "dig_survey"),
    TRADE_ROOM("Ticaret Odası", "Rakip kolonilerle ticaret ve kaynak takası yapılmasını sağlar.", 4, 2, mapOf(SOIL to 16, WOOD to 6, MINERAL to 4), 26f, 1, 3, requires = "prod_trade"),
    GENETICS_LAB("Genetik Araştırma Odası", "Genetik örneklerden kalıcı koloni özellikleri geliştirir. Seviye başına 2 çalışma yeri.", 4, 2, mapOf(SOIL to 16, MINERAL to 6, ENZYME to 2), 32f, 2, 3, requires = "cb_genetics", stationSlots = 2),
    WORKSHOP("Özel Üretim Odası", "Tariflerle gelişmiş kaynak üretir: odun → reçine, mantar+su → enzim, mineral → nadir mineral.", 4, 2, mapOf(SOIL to 14, WOOD to 8, MINERAL to 4), 28f, 2, 3, requires = "prod_workshop", stationSlots = 2),
    FOSSIL_MUSEUM("Fosil Müzesi", "Keşfedilen fosilleri sergiler (seviye başına 4). Her sergilenen fosil itibar kazandırır.", 5, 2, mapOf(SOIL to 16, FOSSIL to 1, WOOD to 6), 28f, 1, 3, requires = "dig_paleo"),
    ARCHIVE("Koloni Arşivi", "Seviye başına +%10 araştırma hızı ve +20 itibar. Büyük olayları saklar.", 3, 2, mapOf(SOIL to 14, LEAF to 10, RESIN to 2), 26f, 1, 3, requires = "ph_archive"),
    FORTRESS("İleri Savunma Merkezi", "Tüm askerlere +%15 saldırı ve savunma; baskınları önceden haber verir.", 5, 3, mapOf(SOIL to 30, MINERAL to 10, RESIN to 4, RARE_MINERAL to 1), 45f, 3, 3, requires = "mil_fortress"),
    ELITE_QUARTERS("Elit Asker Odası", "Elit savaşçı eğitimini açar; seviye başına +4 barınak.", 4, 2, mapOf(SOIL to 22, MINERAL to 8, RARE_MINERAL to 2, FOOD to 20), 40f, 3, 3, requires = "mil_elite"),
    ROYAL_CENTER("Kraliyet Merkezi", "Kraliyet özü üretir (enzim+yiyecek). İleri evrim, yan koloni ve Kraliyet Muhafızı için gereklidir.", 5, 3, mapOf(SOIL to 30, RARE_MINERAL to 3, ENZYME to 4, FOOD to 30), 50f, 3, 3, requires = "cb_royal", stationSlots = 2),
    ;

    fun upgradeCost(toLevel: Int): Map<ResourceType, Int> =
        (if (cost.isEmpty()) mapOf(SOIL to 20, LEAF to 10) else cost).mapValues { it.value * toLevel }

    val isStation: Boolean get() = stationSlots > 0

    /** Komşu oda türü → verim artışı (ileri referans sorunu olmaması için tembel). */
    val adjacency: Map<RoomType, Float>
        get() = when (this) {
            EGG_ROOM -> mapOf(QUEEN_CHAMBER to 0.25f)
            LARVA_ROOM -> mapOf(FOOD_STORAGE to 0.2f)
            FUNGUS_FARM -> mapOf(FOOD_STORAGE to 0.2f, MATERIAL_STORAGE to 0.1f, HUMIDITY_CONTROL to 0.15f)
            RESEARCH_LAB -> mapOf(ARCHIVE to 0.2f)
            GENETICS_LAB -> mapOf(RESEARCH_LAB to 0.2f)
            WORKSHOP -> mapOf(MATERIAL_STORAGE to 0.2f)
            TRAINING_ROOM -> mapOf(BARRACKS to 0.2f)
            INFIRMARY -> mapOf(REST_ROOM to 0.15f)
            ROYAL_CENTER -> mapOf(QUEEN_CHAMBER to 0.25f)
            DEFENSE_ROOM -> mapOf(BARRACKS to 0.15f)
            else -> emptyMap()
        }
}

/** Özel Üretim Odası tarifleri. */
enum class Recipe(val title: String, val input: Map<ResourceType, Int>, val output: Map<ResourceType, Int>, val work: Float) {
    RESIN("Reçine", mapOf(WOOD to 3), mapOf(ResourceType.RESIN to 1), 14f),
    ENZYME("Özel enzim", mapOf(ResourceType.MUSHROOM to 2, WATER to 1), mapOf(ResourceType.ENZYME to 1), 16f),
    RARE("Nadir mineral", mapOf(MINERAL to 5), mapOf(RARE_MINERAL to 1), 30f),
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

    // İstasyon odaları (atölye, eğitim, kraliyet merkezi, genetik)
    var workers = 0
    var stationProgress = 0f
    var recipe = Recipe.RESIN
    var trainClass: AntType? = null
    var trainCount = 0

    val width get() = type.width
    val height get() = type.height
    val anchorX get() = x + width / 2
    val anchorY get() = y + height - 1
    val functional get() = state == RoomState.ACTIVE || state == RoomState.UPGRADING
    val stationCapacity get() = type.stationSlots * level

    fun contains(cx: Int, cy: Int) = cx >= x && cx < x + width && cy >= y && cy < y + height

    inline fun forEachCell(f: (Int, Int) -> Unit) {
        for (yy in y until y + height) for (xx in x until x + width) f(xx, yy)
    }

    fun isAdjacentTo(o: Room): Boolean {
        if (o === this) return false
        val gapX = maxOf(o.x - (x + width), x - (o.x + o.width), 0)
        val gapY = maxOf(o.y - (y + height), y - (o.y + o.height), 0)
        return gapX <= 2 && gapY <= 2 && (gapX == 0 || gapY == 0)
    }

    fun distanceTo(cx: Int, cy: Int): Int {
        val dx = maxOf(x - cx, 0, cx - (x + width - 1))
        val dy = maxOf(y - cy, 0, cy - (y + height - 1))
        return dx + dy
    }

    val workRequired: Float get() = if (state == RoomState.UPGRADING) type.buildWork * level else type.buildWork
}
