package com.yeraltiimparatorlugu.sim

import com.yeraltiimparatorlugu.sim.ResourceType.ENZYME
import com.yeraltiimparatorlugu.sim.ResourceType.FOOD
import com.yeraltiimparatorlugu.sim.ResourceType.GENETIC
import com.yeraltiimparatorlugu.sim.ResourceType.LEAF
import com.yeraltiimparatorlugu.sim.ResourceType.MINERAL
import com.yeraltiimparatorlugu.sim.ResourceType.MUSHROOM
import com.yeraltiimparatorlugu.sim.ResourceType.RARE_MINERAL
import com.yeraltiimparatorlugu.sim.ResourceType.RESIN
import com.yeraltiimparatorlugu.sim.ResourceType.SEED
import com.yeraltiimparatorlugu.sim.ResourceType.SOIL
import com.yeraltiimparatorlugu.sim.ResourceType.WATER
import com.yeraltiimparatorlugu.sim.ResourceType.WOOD
import com.yeraltiimparatorlugu.sim.ResourceType.FOSSIL

/** Araştırma, evrim ve genetiğin birleşik etkileri. [Simulation.recomputeModifiers] ile yeniden hesaplanır. */
class Modifiers {
    var digSpeed = 1f
    var buildSpeed = 1f
    var workMult = 1f
    var carryBonus = 0
    var storageMult = 1f
    var farmMult = 1f
    var gatherMult = 1f
    var layIntervalMult = 1f
    var broodMult = 1f
    var pheromoneDuration = 1f
    var pheromoneRadius = 0
    var trafficMult = 1f
    var collapseMult = 1f
    var revealBonus = 0
    var fossilMult = 1
    var researchMult = 1f
    var attackMult = 1f
    var defenseMult = 1f
    var hpMult = 1f
    var speedMult = 1f
    var climateTolerance = 0f
    var coldTolerance = 0f
    var seasonPenaltyMult = 1f
    var sourceYieldMult = 1f
    var floodAbsorbMult = 1f
    var waterproof = false
    var hardRock = false
    var knownRangeBonus = 0
    var expeditionSpeed = 1f
    var alarm = false
}

enum class ResearchCategory(val title: String) {
    DIGGING("Kazı Teknolojisi"),
    BIOLOGY("Koloni Biyolojisi"),
    PRODUCTION("Üretim Verimliliği"),
    MILITARY("Askerî Gelişim"),
    PHEROMONE("Feromon Bilimi"),
    ECOLOGY("Ekosistem Uyumu"),
}

/** Araştırma tanımı. [effect] tamamlanınca değiştiricilere uygulanır; oda ve sınıf kilitleri kimlikle açılır. */
class ResearchDef(
    val id: String,
    val category: ResearchCategory,
    val title: String,
    val description: String,
    val cost: Map<ResourceType, Int>,
    val work: Float,
    val prereqs: List<String> = emptyList(),
    val effect: (Modifiers) -> Unit = {},
)

object ResearchTree {
    val all: List<ResearchDef> = listOf(
        // Kazı
        ResearchDef("dig_tools", ResearchCategory.DIGGING, "Güçlü Çeneler", "Kazı hızı +%25.", mapOf(SOIL to 15, FOOD to 10), 40f) { it.digSpeed *= 1.25f },
        ResearchDef("dig_rock", ResearchCategory.DIGGING, "Sert Kaya Kazma", "Kaya ve nadir mineral damarları kazılabilir. Tünel Uzmanı sınıfını açar.", mapOf(MINERAL to 6, WOOD to 6), 80f, listOf("dig_tools")) { it.hardRock = true },
        ResearchDef("dig_survey", ResearchCategory.DIGGING, "Zemin Etüdü", "Keşif Merkezi ve Keşif feromonu açılır; kazıda görüş yarıçapı +1.", mapOf(SOIL to 20, FOOD to 15), 60f, listOf("dig_tools")) { it.revealBonus += 1 },
        ResearchDef("dig_paleo", ResearchCategory.DIGGING, "Paleontoloji", "Fosil Müzesi açılır; fosil alanları iki kat fosil verir.", mapOf(FOSSIL to 1, SOIL to 20), 90f, listOf("dig_survey")) { it.fossilMult = 2 },
        ResearchDef("dig_wide", ResearchCategory.DIGGING, "Geniş Tüneller", "Tünel trafiği yarıya iner, çökme riski %50 azalır.", mapOf(SOIL to 30, WOOD to 10, RESIN to 2), 120f, listOf("dig_rock")) { it.trafficMult *= 0.5f; it.collapseMult *= 0.5f },
        // Biyoloji
        ResearchDef("cb_castes", ResearchCategory.BIOLOGY, "Kast Gelişimi", "Eğitim Odası ve işçi uzman sınıfları (toplayıcı, kazıcı, taşıyıcı, bakıcı, keşifçi, mühendis) açılır.", mapOf(FOOD to 20, LEAF to 10), 50f),
        ResearchDef("cb_fertility", ResearchCategory.BIOLOGY, "Verimli Kraliçe", "Yumurtlama aralığı %25 kısalır.", mapOf(FOOD to 30, MUSHROOM to 10), 80f, listOf("cb_castes")) { it.layIntervalMult *= 0.75f },
        ResearchDef("cb_medicine", ResearchCategory.BIOLOGY, "Şifa Bilgisi", "Sağlık Odası ve Şifacı sınıfı açılır; tüm karıncaların canı +%10.", mapOf(MUSHROOM to 10, WATER to 10), 80f, listOf("cb_castes")) { it.hpMult *= 1.1f },
        ResearchDef("cb_genetics", ResearchCategory.BIOLOGY, "Genetik", "Genetik Araştırma Odası açılır.", mapOf(GENETIC to 1, ENZYME to 2, MINERAL to 6), 140f, listOf("cb_medicine", "prod_workshop")),
        ResearchDef("cb_royal", ResearchCategory.BIOLOGY, "Kraliyet Soyu", "Kraliyet Merkezi açılır: kraliyet özü, ileri evrim ve yan koloniler.", mapOf(RARE_MINERAL to 2, ENZYME to 4, FOOD to 40), 220f, listOf("cb_fertility", "cb_genetics")),
        // Üretim
        ResearchDef("prod_fungus", ResearchCategory.PRODUCTION, "Mantar Kültürü", "Mantar çiftlikleri +%40 üretir.", mapOf(LEAF to 15, MUSHROOM to 5), 50f) { it.farmMult *= 1.4f },
        ResearchDef("prod_carry", ResearchCategory.PRODUCTION, "Taşıma Teknikleri", "Her karınca +1 birim fazla taşır.", mapOf(FOOD to 15, WOOD to 6), 60f) { it.carryBonus += 1 },
        ResearchDef("prod_storage", ResearchCategory.PRODUCTION, "Depo Düzeni", "Tüm depo kapasiteleri +%30.", mapOf(SOIL to 25, WOOD to 8), 70f, listOf("prod_carry")) { it.storageMult *= 1.3f },
        ResearchDef("prod_workshop", ResearchCategory.PRODUCTION, "Atölye", "Özel Üretim Odası açılır: reçine, enzim ve nadir mineral üretimi.", mapOf(WOOD to 12, MINERAL to 4), 80f, listOf("prod_fungus")),
        ResearchDef("prod_trade", ResearchCategory.PRODUCTION, "Ticaret", "Ticaret Odası ve rakiplerle ticaret anlaşmaları açılır.", mapOf(FOOD to 25, MINERAL to 4), 90f, listOf("prod_storage")),
        ResearchDef("prod_harvest", ResearchCategory.PRODUCTION, "Verimli Hasat", "Toplama hızı ve yüzey kaynak miktarı +%25.", mapOf(SEED to 20, LEAF to 15), 100f, listOf("prod_carry")) { it.gatherMult *= 1.25f; it.sourceYieldMult *= 1.25f },
        // Askerî
        ResearchDef("mil_soldiers", ResearchCategory.MILITARY, "Asker Yetiştirme", "Asker Kışlası, Asker sınıfı, Saldırı ve Savunma feromonları açılır.", mapOf(FOOD to 25, SOIL to 15), 60f, listOf("cb_castes")),
        ResearchDef("mil_defense", ResearchCategory.MILITARY, "Savunma Düzeni", "Savunma Odası ve Savunmacı sınıfı açılır; savunma +%10.", mapOf(SOIL to 25, MINERAL to 4), 80f, listOf("mil_soldiers")) { it.defenseMult *= 1.1f },
        ResearchDef("mil_traps", ResearchCategory.MILITARY, "Tuzaklar", "Tuzak Odası açılır.", mapOf(RESIN to 2, WOOD to 10), 90f, listOf("mil_defense", "prod_workshop")),
        ResearchDef("mil_heavy", ResearchCategory.MILITARY, "Ağır Zırh", "Ağır Asker sınıfı açılır; saldırı +%10.", mapOf(MINERAL to 10, FOOD to 30), 110f, listOf("mil_soldiers")) { it.attackMult *= 1.1f },
        ResearchDef("mil_fortress", ResearchCategory.MILITARY, "Kale Mimarisi", "İleri Savunma Merkezi açılır.", mapOf(MINERAL to 12, RESIN to 4, RARE_MINERAL to 1), 180f, listOf("mil_traps", "mil_heavy")),
        ResearchDef("mil_elite", ResearchCategory.MILITARY, "Elit Savaşçılar", "Elit Asker Odası, Elit Savaşçı ve Kraliyet Muhafızı açılır; saldırı +%15.", mapOf(RARE_MINERAL to 2, ENZYME to 3, FOOD to 50), 240f, listOf("mil_fortress")) { it.attackMult *= 1.15f },
        // Feromon
        ResearchDef("ph_lasting", ResearchCategory.PHEROMONE, "Kalıcı İzler", "Feromonlar %60 daha uzun sürer.", mapOf(FOOD to 15, LEAF to 8), 40f) { it.pheromoneDuration *= 1.6f },
        ResearchDef("ph_strong", ResearchCategory.PHEROMONE, "Güçlü Sinyaller", "Feromon yarıçapı +2.", mapOf(FOOD to 20, MUSHROOM to 6), 60f, listOf("ph_lasting")) { it.pheromoneRadius += 2 },
        ResearchDef("ph_alarm", ResearchCategory.PHEROMONE, "Alarm Feromonu", "Tehlike ve Acil yardım feromonları; işçiler tehlikeden daha erken kaçar.", mapOf(FOOD to 20, RESIN to 1), 70f, listOf("ph_lasting")) { it.alarm = true },
        ResearchDef("ph_trails", ResearchCategory.PHEROMONE, "Koku İzleri", "Bilinen yüzey menzili +12 hücre; tüm karıncalar %8 hızlı yürür.", mapOf(FOOD to 25, LEAF to 10), 90f, listOf("ph_strong")) { it.knownRangeBonus += 12; it.speedMult *= 1.08f },
        ResearchDef("ph_archive", ResearchCategory.PHEROMONE, "Koloni Hafızası", "Koloni Arşivi açılır; araştırma +%10.", mapOf(LEAF to 20, RESIN to 2), 100f, listOf("ph_lasting")) { it.researchMult *= 1.1f },
        // Ekoloji
        ResearchDef("eco_ventilation", ResearchCategory.ECOLOGY, "Havalandırma", "Havalandırma Odası açılır.", mapOf(SOIL to 20, WOOD to 4), 50f),
        ResearchDef("eco_water", ResearchCategory.ECOLOGY, "Su Toplama", "Su Deposu açılır; zemin sel suyunu iki kat hızlı emer.", mapOf(SOIL to 20, LEAF to 8), 60f) { it.floodAbsorbMult *= 2f },
        ResearchDef("eco_humidity", ResearchCategory.ECOLOGY, "Nem Dengesi", "Nem Kontrol Odası açılır; nem toleransı artar.", mapOf(WATER to 15, SOIL to 10), 80f, listOf("eco_water")) { it.climateTolerance += 0.25f },
        ResearchDef("eco_waterproof", ResearchCategory.ECOLOGY, "Suya Dayanıklı Odalar", "Odalar sel suyundan etkilenmez.", mapOf(RESIN to 4, SOIL to 30), 140f, listOf("eco_humidity", "prod_workshop")) { it.waterproof = true },
        ResearchDef("eco_seasons", ResearchCategory.ECOLOGY, "Mevsim Bilgisi", "Kış ve kuraklık cezaları %50 azalır, soğuk toleransı artar.", mapOf(FOOD to 30, SEED to 15), 110f, listOf("eco_ventilation")) { it.seasonPenaltyMult *= 0.5f; it.coldTolerance += 0.3f },
    )

    private val byId = all.associateBy { it.id }
    fun get(id: String): ResearchDef? = byId[id]
}

class ResearchSystem {
    val completed = LinkedHashSet<String>()
    var current: String? = null
    var progress = 0f
    var boostUntil = 0.0

    fun isDone(id: String?) = id == null || id in completed

    fun available(def: ResearchDef) = def.id !in completed && def.prereqs.all { it in completed }

    fun currentDef(): ResearchDef? = current?.let { ResearchTree.get(it) }
}

enum class EvolutionPath(val title: String, val description: String) {
    PRODUCTION("Üretim Kolonisi", "Her kademe: toplama ve mantar üretimi +%8."),
    WAR("Savaş Kolonisi", "Her kademe: saldırı +%8, savunma +%5."),
    EXPLORATION("Keşif Kolonisi", "Her kademe: yürüme +%3, sefer hızı +%10, bilinen menzil +4."),
    ENGINEERING("Mühendislik Kolonisi", "Her kademe: kazı ve inşa +%8, çökme riski -%10."),
    BIOLOGY("Biyolojik Koloni", "Her kademe: yavru gelişimi +%8, can +%5, iklim toleransı."),
}

/** Koloni evrimi: itibar arttıkça puan kazanılır, beş yol arasında serbestçe (hibrit) dağıtılır. */
class EvolutionSystem {
    val ranks = IntArray(EvolutionPath.entries.size)
    val spent: Int get() = ranks.sum()

    fun level(reputation: Int): Int = kotlin.math.sqrt(reputation / 40.0).toInt()

    fun points(reputation: Int) = level(reputation) - spent

    fun apply(m: Modifiers) {
        val p = ranks[EvolutionPath.PRODUCTION.ordinal]; m.gatherMult *= 1f + 0.08f * p; m.farmMult *= 1f + 0.08f * p
        val w = ranks[EvolutionPath.WAR.ordinal]; m.attackMult *= 1f + 0.08f * w; m.defenseMult *= 1f + 0.05f * w
        val e = ranks[EvolutionPath.EXPLORATION.ordinal]; m.speedMult *= 1f + 0.03f * e; m.expeditionSpeed *= 1f + 0.1f * e; m.knownRangeBonus += 4 * e
        val g = ranks[EvolutionPath.ENGINEERING.ordinal]; m.digSpeed *= 1f + 0.08f * g; m.buildSpeed *= 1f + 0.08f * g; m.collapseMult *= 1f - 0.1f * g
        val b = ranks[EvolutionPath.BIOLOGY.ordinal]; m.broodMult *= 1f + 0.08f * b; m.hpMult *= 1f + 0.05f * b; m.climateTolerance += 0.06f * b
    }
}

enum class GeneticTrait(val title: String, val description: String, val samples: Int, val work: Float, val effect: (Modifiers) -> Unit) {
    THICK_SHELL("Kalın Kabuk", "Savunma +%15.", 2, 60f, { it.defenseMult *= 1.15f }),
    FAST_LEGS("Hızlı Bacaklar", "Yürüme hızı +%10.", 2, 60f, { it.speedMult *= 1.1f }),
    STRONG_JAWS("Güçlü Çene", "Saldırı ve kazı +%10.", 3, 80f, { it.attackMult *= 1.1f; it.digSpeed *= 1.1f }),
    FERTILE_EGGS("Verimli Yumurta", "Yavru gelişimi +%15.", 3, 80f, { it.broodMult *= 1.15f }),
    HUMIDITY_TOLERANCE("Nem Toleransı", "Nem cezaları yarıya iner.", 2, 70f, { it.climateTolerance += 0.3f }),
    COLD_RESISTANCE("Soğuk Direnci", "Soğuk cezaları yarıya iner.", 2, 70f, { it.coldTolerance += 0.4f }),
    LONG_LIFE("Uzun Ömür", "Can +%20.", 4, 100f, { it.hpMult *= 1.2f }),
    KEEN_SENSES("Keskin Duyular", "Görüş yarıçapı +1, bilinen menzil +6.", 3, 90f, { it.revealBonus += 1; it.knownRangeBonus += 6 }),
}

class GeneticsSystem {
    val unlocked = LinkedHashSet<GeneticTrait>()
    var current: GeneticTrait? = null
    var progress = 0f
}
