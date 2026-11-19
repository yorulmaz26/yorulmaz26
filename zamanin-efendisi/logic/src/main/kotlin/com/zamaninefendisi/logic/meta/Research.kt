package com.zamaninefendisi.logic.meta

import com.zamaninefendisi.logic.meta.Resource.*

enum class ResearchCategory(val title: String, val description: String) {
    TIME("Zaman Bilimi", "Zaman geçişleri, önizleme ve rehberlik."),
    CIVILIZATION("Medeniyet", "Kaynak üretimi, ödüller ve depolama."),
    MILITARY("Askerî", "Muhafızın dayanıklılığı ve savaş ganimeti."),
    TECHNOLOGY("Teknoloji", "Enerji ve kristal üretimi, otomasyon."),
    PARADOX("Paradoks Bilimi", "Paradoksları avantaja çevirme."),
}

class ResearchNode(
    val id: String,
    val category: ResearchCategory,
    val tier: Int,
    val title: String,
    val effect: String,
    val cost: Cost,
    val requires: String?,
)

/** Beş kategorili araştırma ağacı. Hiçbir araştırma bir bulmacayı kendiliğinden çözmez. */
object ResearchTree {
    const val FORESIGHT_1 = "time_foresight_1"
    const val GUIDE = "time_guide"
    const val FORESIGHT_2 = "time_foresight_2"
    const val FORESIGHT_UNLIMITED = "time_foresight_inf"
    const val GOLD_10 = "civ_gold"
    const val SCIENCE_10 = "civ_science"
    const val PRODUCTION_15 = "civ_production"
    const val STORAGE_BOOST = "civ_storage"
    const val HP_1 = "mil_hp1"
    const val SPOILS = "mil_spoils"
    const val HP_2 = "mil_hp2"
    const val VETERAN = "mil_veteran"
    const val ENERGY_25 = "tech_energy"
    const val CRYSTAL_DRILL = "tech_crystal"
    const val WORKSHOP_25 = "tech_workshop"
    const val AUTO_COLLECT = "tech_auto"
    const val PARADOX_HARVEST = "par_harvest"
    const val PARADOX_COSMETICS = "par_cosmetics"
    const val PARADOX_TRADE = "par_trade"
    const val PARADOX_SHIELD = "par_shield"

    private fun c(gold: Int, science: Int, extra: Pair<Resource, Int>? = null): Cost =
        buildMap { put(GOLD, gold); put(SCIENCE, science); extra?.let { put(it.first, it.second) } }

    val nodes: List<ResearchNode> = listOf(
        ResearchNode(FORESIGHT_1, ResearchCategory.TIME, 1, "Kehanet I", "Her bölümde +2 önizleme hakkı", c(60, 30), null),
        ResearchNode(GUIDE, ResearchCategory.TIME, 2, "Zaman Rehberi", "Bölümde 1 Zaman Kristali karşılığında bir sonraki doğru adımı gösterir", c(120, 70), FORESIGHT_1),
        ResearchNode(FORESIGHT_2, ResearchCategory.TIME, 3, "Kehanet II", "Her bölümde +3 önizleme hakkı", c(220, 140, ENERGY to 40), GUIDE),
        ResearchNode(FORESIGHT_UNLIMITED, ResearchCategory.TIME, 4, "Zamanın Gözü", "Önizleme sınırsız olur", c(400, 260, CRYSTAL to 5), FORESIGHT_2),
        ResearchNode(GOLD_10, ResearchCategory.CIVILIZATION, 1, "Ticaret Yolları", "Bölüm altın ödülleri +%10", c(50, 25), null),
        ResearchNode(SCIENCE_10, ResearchCategory.CIVILIZATION, 2, "Kütüphaneler", "Bölüm bilim ödülleri +%10", c(100, 60), GOLD_10),
        ResearchNode(PRODUCTION_15, ResearchCategory.CIVILIZATION, 3, "Lonca Sistemi", "Bütün bina üretimi +%15", c(200, 120, WOOD to 60), SCIENCE_10),
        ResearchNode(STORAGE_BOOST, ResearchCategory.CIVILIZATION, 4, "Büyük Ambarlar", "Depo sınırı +%50", c(320, 200, STONE to 80), PRODUCTION_15),
        ResearchNode(HP_1, ResearchCategory.MILITARY, 1, "Zırh Ustalığı", "Savaş bölümlerinde Muhafız +1 can", c(70, 40, IRON to 15), null),
        ResearchNode(SPOILS, ResearchCategory.MILITARY, 2, "Savaş Ganimeti", "Yenilen her düşman +3 altın", c(120, 70), HP_1),
        ResearchNode(HP_2, ResearchCategory.MILITARY, 3, "Kadim Kalkan", "Savaş bölümlerinde Muhafız +1 can daha", c(240, 150, IRON to 40), SPOILS),
        ResearchNode(VETERAN, ResearchCategory.MILITARY, 4, "Gazi Komutan", "Savaş bölümlerinde deneyim +%25", c(360, 230), HP_2),
        ResearchNode(ENERGY_25, ResearchCategory.TECHNOLOGY, 1, "Verimli Türbinler", "Enerji üretimi +%25", c(60, 35), null),
        ResearchNode(CRYSTAL_DRILL, ResearchCategory.TECHNOLOGY, 2, "Kristal Matkabı", "Kristal üretimi +%50", c(140, 90, ENERGY to 30), ENERGY_25),
        ResearchNode(WORKSHOP_25, ResearchCategory.TECHNOLOGY, 3, "Mekanik Atölye", "Odun, taş ve demir üretimi +%25", c(220, 140, IRON to 30), CRYSTAL_DRILL),
        ResearchNode(AUTO_COLLECT, ResearchCategory.TECHNOLOGY, 4, "Zaman Otomatı", "Kale üretimi kendiliğinden toplanır", c(350, 220, CRYSTAL to 4), WORKSHOP_25),
        ResearchNode(PARADOX_HARVEST, ResearchCategory.PARADOX, 1, "Paradoks Hasadı", "Bölümlerde yaşanan her paradoks +2 Paradoks Enerjisi", c(80, 50), null),
        ResearchNode(PARADOX_COSMETICS, ResearchCategory.PARADOX, 2, "Kırık Estetik", "Markette paradoks temalı görünümler açılır", c(130, 80, PARADOX to 10), PARADOX_HARVEST),
        ResearchNode(PARADOX_TRADE, ResearchCategory.PARADOX, 3, "Enerji Dönüşümü", "Markette Paradoks Enerjisi → Zaman Kristali takası açılır", c(230, 150, PARADOX to 20), PARADOX_COSMETICS),
        ResearchNode(PARADOX_SHIELD, ResearchCategory.PARADOX, 4, "Paradoks Ustalığı", "Paradoks yaşanan bölümlerde bütün ödüller +%20", c(380, 240, SHARD to 3), PARADOX_TRADE),
    )

    fun node(id: String): ResearchNode? = nodes.firstOrNull { it.id == id }

    fun canResearch(n: ResearchNode, done: Set<String>, labLevel: Int, wallet: Map<Resource, Int>): UpgradeCheck = when {
        n.id in done -> UpgradeCheck(false, "Araştırıldı")
        n.requires != null && n.requires !in done -> UpgradeCheck(false, "Önce: ${node(n.requires)?.title}")
        labLevel < n.tier -> UpgradeCheck(false, "Araştırma Laboratuvarı seviye ${n.tier} gerekli")
        !wallet.has(n.cost) -> UpgradeCheck(false, "Yetersiz kaynak: ${n.cost.describe()}")
        else -> UpgradeCheck(true, null)
    }

    fun productionMultiplier(r: Resource, done: Set<String>): Double {
        var m = 1.0
        if (PRODUCTION_15 in done) m += 0.15
        if (r == ENERGY && ENERGY_25 in done) m += 0.25
        if (r == CRYSTAL && CRYSTAL_DRILL in done) m += 0.5
        if ((r == WOOD || r == STONE || r == IRON) && WORKSHOP_25 in done) m += 0.25
        return m
    }

    /** Bölüm başına önizleme hakkı (null = sınırsız). */
    fun foresightCharges(done: Set<String>, techCenterLevel: Int): Int? {
        if (FORESIGHT_UNLIMITED in done) return null
        var n = 2 + techCenterLevel
        if (FORESIGHT_1 in done) n += 2
        if (FORESIGHT_2 in done) n += 3
        return n
    }

    fun heroHpBonus(done: Set<String>, academyLevel: Int): Int =
        academyLevel / 2 + (if (HP_1 in done) 1 else 0) + (if (HP_2 in done) 1 else 0)
}
