package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import kotlinx.serialization.Serializable

enum class BuildingCategory(val title: String) {
    ADMIN("Yönetim"), POPULATION("Nüfus"), PRODUCTION("Üretim"), STORAGE("Depolama"), SPECIAL("Özel")
}

enum class Profession(val title: String) {
    FARMER("Çiftçi"), ENGINEER("Mühendis"), BUILDER("İnşaatçı"), RESEARCHER("Araştırmacı"),
    MERCHANT("Tüccar"), EXPLORER("Keşifçi"), GUARD("Muhafız"), DOCTOR("Doktor"),
    CARETAKER("Taşıyıcı Bakıcısı"), NAVIGATOR("Navigatör"), ADMINISTRATOR("Yönetici")
}

/** Komşu bina türü yanında olduğunda üretime eklenen oran (örn. 0.2 = %20). */
data class Adjacency(val neighbor: BuildingType, val bonus: Double, val note: String)

data class BuildingDef(
    val type: BuildingType,
    val title: String,
    val category: BuildingCategory,
    val description: String,
    val w: Int,
    val h: Int,
    val weight: Double,
    val cost: Amounts,
    val buildSeconds: Double,
    val produces: Amounts = emptyMap(),
    val consumes: Amounts = emptyMap(),
    val workers: Int = 0,
    val profession: Profession? = null,
    val upkeepGold: Double = 0.0,
    val maxLevel: Int = 5,
    val housing: Int = 0,
    val storage: Amounts = emptyMap(),
    val happiness: Double = 0.0,
    val researchPerMin: Double = 0.0,
    val taxPerPop: Double = 0.0,
    val carrierCarePerMin: Double = 0.0,
    val gatherPerMin: Double = 0.0,
    val supportRadius: Int = 0,
    val healthRadius: Int = 0,
    val revealBonus: Int = 0,
    val travelSpeedBonus: Double = 0.0,
    val adjacency: List<Adjacency> = emptyList(),
    val unique: Boolean = false,
    val requiresTech: TechId? = null,
    val minStage: Int = 0,
    val buildable: Boolean = true,
)

@Serializable
enum class BuildingType {
    CITY_CENTER, TOWN_HALL, RESEARCH_CENTER, MAP_CENTER,
    SMALL_HOUSE, LARGE_HOUSE, APARTMENTS, HEALTH_CENTER, REST_AREA,
    FARM, GREENHOUSE, WATER_COLLECTOR, MILL, WORKSHOP, METALWORKS, TEXTILE, FOOD_FACTORY, POWER_PLANT, RECYCLER,
    FOOD_STORAGE, WATER_STORAGE, MATERIAL_STORAGE,
    CARRIER_CARE, TECH_LAB, NAVIGATION_TOWER, GATHERER, SUPPORT_FRAME, CULTURE_CENTER;

    val def: BuildingDef get() = BuildingCatalog.get(this)
}

object BuildingCatalog {
    private val defs: Map<BuildingType, BuildingDef> = listOf(
        // ---------------- Yönetim ----------------
        BuildingDef(
            BuildingType.CITY_CENTER, "Şehir Merkezi", BuildingCategory.ADMIN,
            "Topluluğun kalbi. Barınak, temel depo ve küçük vergi geliri sağlar.",
            2, 2, 80.0, amounts(WOOD to 200, STONE to 150), 0.0,
            housing = 6, taxPerPop = 0.10, happiness = 2.0,
            storage = amounts(FOOD to 150, WATER to 150, WOOD to 200, STONE to 200, METAL to 80, CLOTH to 80, TOOLS to 60,
                ENERGY to 40, CRYSTAL to 30, RARE_ORE to 30, ANCIENT_PART to 30, TECH_PART to 30, LIGHT_MATERIAL to 40, CARE_ESSENCE to 30, WASTE to 120),
            unique = true, buildable = false, maxLevel = 5,
        ),
        BuildingDef(
            BuildingType.TOWN_HALL, "Belediye", BuildingCategory.ADMIN,
            "Nüfustan altın vergisi toplar ve düzeni sağlar.",
            2, 1, 45.0, amounts(WOOD to 80, STONE to 60, CLOTH to 10), 40.0,
            workers = 2, profession = Profession.ADMINISTRATOR, taxPerPop = 0.30, happiness = 3.0, unique = true, minStage = 1,
        ),
        BuildingDef(
            BuildingType.RESEARCH_CENTER, "Araştırma Merkezi", BuildingCategory.ADMIN,
            "Araştırmacılar burada bilgi puanı üretir.",
            2, 1, 40.0, amounts(WOOD to 70, STONE to 40, METAL to 10), 35.0,
            consumes = amounts(ENERGY to 1), workers = 2, profession = Profession.RESEARCHER, researchPerMin = 4.0, upkeepGold = 0.2,
            adjacency = listOf(Adjacency(BuildingType.TECH_LAB, 0.25, "Teknoloji Laboratuvarı yanında +%25 araştırma")),
        ),
        BuildingDef(
            BuildingType.MAP_CENTER, "Harita Merkezi", BuildingCategory.ADMIN,
            "Haritacılar çevreyi daha uzağa kadar haritalar (+1 keşif menzili).",
            1, 1, 15.0, amounts(WOOD to 60, CLOTH to 20), 30.0,
            workers = 1, profession = Profession.NAVIGATOR, revealBonus = 1, unique = true, minStage = 1, maxLevel = 3,
        ),
        // ---------------- Nüfus ----------------
        BuildingDef(
            BuildingType.SMALL_HOUSE, "Küçük Ev", BuildingCategory.POPULATION,
            "4 kişilik hafif barınak.",
            1, 1, 12.0, amounts(WOOD to 30, CLOTH to 4), 15.0, housing = 4, maxLevel = 3,
            adjacency = listOf(Adjacency(BuildingType.HEALTH_CENTER, 0.0, "Sağlık Merkezi yanında daha yüksek yaşam kalitesi")),
        ),
        BuildingDef(
            BuildingType.LARGE_HOUSE, "Büyük Ev", BuildingCategory.POPULATION,
            "8 kişilik sağlam ev.",
            2, 1, 28.0, amounts(WOOD to 60, STONE to 30, CLOTH to 10), 30.0, housing = 8, maxLevel = 3, minStage = 1,
        ),
        BuildingDef(
            BuildingType.APARTMENTS, "Toplu Konut", BuildingCategory.POPULATION,
            "16 kişilik yoğun konut. Ağırdır, dengeye dikkat!",
            2, 2, 60.0, amounts(WOOD to 90, STONE to 90, METAL to 20, CLOTH to 20), 55.0, housing = 16, maxLevel = 3, upkeepGold = 0.3,
            requiresTech = TechId.EFFICIENT_LAYOUT, minStage = 2,
        ),
        BuildingDef(
            BuildingType.HEALTH_CENTER, "Sağlık Merkezi", BuildingCategory.POPULATION,
            "Yakınındaki (2 kare) evlerde sağlığı ve mutluluğu artırır.",
            1, 1, 18.0, amounts(WOOD to 40, STONE to 20, CLOTH to 10), 25.0,
            consumes = amounts(WATER to 1), workers = 1, profession = Profession.DOCTOR, healthRadius = 2, upkeepGold = 0.1,
        ),
        BuildingDef(
            BuildingType.REST_AREA, "Dinlenme Alanı", BuildingCategory.POPULATION,
            "Bahçeler ve gölgelikler. Mutluluğu artırır.",
            1, 1, 6.0, amounts(WOOD to 25, CLOTH to 6), 12.0, happiness = 6.0, maxLevel = 3,
        ),
        BuildingDef(
            BuildingType.CULTURE_CENTER, "Kültür Merkezi", BuildingCategory.POPULATION,
            "Şenlikler ve hikâyeler. Büyük mutluluk artışı ve az miktarda araştırma.",
            2, 1, 30.0, amounts(WOOD to 80, STONE to 40, CLOTH to 30, GOLD to 40), 45.0,
            workers = 1, profession = Profession.ADMINISTRATOR, happiness = 10.0, researchPerMin = 0.5, upkeepGold = 0.4, minStage = 2, maxLevel = 3,
        ),
        // ---------------- Üretim ----------------
        BuildingDef(
            BuildingType.FARM, "Tarla", BuildingCategory.PRODUCTION,
            "Su ve tohumla yiyecek üretir.",
            2, 1, 22.0, amounts(WOOD to 30), 20.0,
            produces = amounts(FOOD to 7), consumes = amounts(WATER to 2), workers = 2, profession = Profession.FARMER,
            adjacency = listOf(
                Adjacency(BuildingType.WATER_STORAGE, 0.20, "Su Deposu yanında +%20"),
                Adjacency(BuildingType.MILL, 0.15, "Değirmen yanında +%15"),
            ),
        ),
        BuildingDef(
            BuildingType.GREENHOUSE, "Sera", BuildingCategory.PRODUCTION,
            "Her iklimde yüksek verimli yiyecek. Enerji ister.",
            2, 1, 26.0, amounts(WOOD to 40, STONE to 20, CLOTH to 12, METAL to 6), 30.0,
            produces = amounts(FOOD to 12), consumes = amounts(WATER to 3, ENERGY to 1.5), workers = 2, profession = Profession.FARMER,
            adjacency = listOf(Adjacency(BuildingType.WATER_STORAGE, 0.20, "Su Deposu yanında +%20")),
            requiresTech = TechId.IRRIGATION,
        ),
        BuildingDef(
            BuildingType.WATER_COLLECTOR, "Su Toplama Tesisi", BuildingCategory.PRODUCTION,
            "Yağmur ve nemden su toplar. Çölde verimi düşer, gölde artar.",
            1, 1, 14.0, amounts(WOOD to 25, CLOTH to 4), 15.0,
            produces = amounts(WATER to 9), workers = 1, profession = Profession.ENGINEER,
            adjacency = listOf(Adjacency(BuildingType.WATER_STORAGE, 0.15, "Su Deposu yanında +%15")),
        ),
        BuildingDef(
            BuildingType.MILL, "Değirmen", BuildingCategory.PRODUCTION,
            "Rüzgârdan az miktarda enerji üretir; yanındaki tarlaları hızlandırır.",
            1, 1, 16.0, amounts(WOOD to 35, STONE to 10), 20.0,
            produces = amounts(ENERGY to 3), workers = 1, profession = Profession.ENGINEER,
        ),
        BuildingDef(
            BuildingType.WORKSHOP, "Atölye", BuildingCategory.PRODUCTION,
            "Odun ve metalden alet üretir.",
            1, 1, 24.0, amounts(WOOD to 40, STONE to 20), 25.0,
            produces = amounts(TOOLS to 2, WASTE to 0.3), consumes = amounts(WOOD to 2, METAL to 1), workers = 2, profession = Profession.ENGINEER,
            adjacency = listOf(
                Adjacency(BuildingType.MATERIAL_STORAGE, 0.20, "Malzeme Deposu yanında +%20"),
                Adjacency(BuildingType.POWER_PLANT, 0.15, "Enerji Tesisi yanında +%15"),
            ),
        ),
        BuildingDef(
            BuildingType.METALWORKS, "Metal İşleme Tesisi", BuildingCategory.PRODUCTION,
            "Taş cevherini enerjiyle eritip metal üretir.",
            2, 1, 55.0, amounts(WOOD to 50, STONE to 60), 35.0,
            produces = amounts(METAL to 2.5, WASTE to 0.6), consumes = amounts(STONE to 4, ENERGY to 2), workers = 2, profession = Profession.ENGINEER, upkeepGold = 0.2,
            adjacency = listOf(
                Adjacency(BuildingType.POWER_PLANT, 0.15, "Enerji Tesisi yanında +%15"),
                Adjacency(BuildingType.MATERIAL_STORAGE, 0.10, "Malzeme Deposu yanında +%10"),
            ),
        ),
        BuildingDef(
            BuildingType.TEXTILE, "Tekstil Atölyesi", BuildingCategory.PRODUCTION,
            "Bitki liflerinden ve sudan kumaş dokur.",
            1, 1, 15.0, amounts(WOOD to 35, STONE to 10), 25.0,
            produces = amounts(CLOTH to 2), consumes = amounts(FOOD to 1, WATER to 1), workers = 2, profession = Profession.ENGINEER,
            adjacency = listOf(Adjacency(BuildingType.MATERIAL_STORAGE, 0.15, "Malzeme Deposu yanında +%15")),
        ),
        BuildingDef(
            BuildingType.FOOD_FACTORY, "Gıda Üretim Merkezi", BuildingCategory.PRODUCTION,
            "Suyu ve enerjiyi kullanarak toplu yiyecek üretir.",
            2, 2, 50.0, amounts(WOOD to 70, STONE to 50, METAL to 20, TOOLS to 10), 45.0,
            produces = amounts(FOOD to 22, WASTE to 0.5), consumes = amounts(WATER to 5, ENERGY to 3), workers = 4, profession = Profession.FARMER, upkeepGold = 0.3,
            adjacency = listOf(
                Adjacency(BuildingType.FOOD_STORAGE, 0.15, "Yiyecek Deposu yanında +%15"),
                Adjacency(BuildingType.POWER_PLANT, 0.15, "Enerji Tesisi yanında +%15"),
            ),
            minStage = 2,
        ),
        BuildingDef(
            BuildingType.POWER_PLANT, "Enerji Üretim Tesisi", BuildingCategory.PRODUCTION,
            "Odun yakarak enerji üretir ve enerji depolar.",
            1, 1, 30.0, amounts(WOOD to 40, STONE to 30, METAL to 5), 30.0,
            produces = amounts(ENERGY to 10, WASTE to 0.4), consumes = amounts(WOOD to 2), workers = 1, profession = Profession.ENGINEER,
            storage = amounts(ENERGY to 60),
        ),
        BuildingDef(
            BuildingType.RECYCLER, "Geri Dönüşüm Merkezi", BuildingCategory.PRODUCTION,
            "Atıkları taş ve metale dönüştürür.",
            1, 1, 22.0, amounts(WOOD to 40, STONE to 20, METAL to 5), 25.0,
            produces = amounts(STONE to 1.5, METAL to 0.6), consumes = amounts(WASTE to 3), workers = 1, profession = Profession.ENGINEER,
        ),
        BuildingDef(
            BuildingType.GATHERER, "Toplayıcı Rampası", BuildingCategory.PRODUCTION,
            "Taşıyıcının bulunduğu araziden kaynak toplar. Kaynaklar biyoma göre değişir; yolculukta %40 verimle çalışır.",
            1, 1, 14.0, amounts(WOOD to 30, CLOTH to 4), 15.0,
            workers = 2, profession = Profession.EXPLORER, gatherPerMin = 8.0,
        ),
        // ---------------- Depolama ----------------
        BuildingDef(
            BuildingType.FOOD_STORAGE, "Yiyecek Deposu", BuildingCategory.STORAGE,
            "Yiyecek kapasitesini artırır. Depolanan yiyecek bu binada ağırlık yapar.",
            1, 1, 10.0, amounts(WOOD to 30), 12.0, storage = amounts(FOOD to 200),
        ),
        BuildingDef(
            BuildingType.WATER_STORAGE, "Su Deposu", BuildingCategory.STORAGE,
            "Su kapasitesini artırır. Su ağırdır, yerine dikkat!",
            1, 1, 10.0, amounts(WOOD to 30, STONE to 10), 12.0, storage = amounts(WATER to 200),
        ),
        BuildingDef(
            BuildingType.MATERIAL_STORAGE, "Malzeme Deposu", BuildingCategory.STORAGE,
            "Odun, taş, metal, kumaş, alet ve nadir malzemeleri depolar.",
            2, 1, 18.0, amounts(WOOD to 50, STONE to 20), 18.0,
            storage = amounts(WOOD to 200, STONE to 200, METAL to 100, CLOTH to 100, TOOLS to 80, CRYSTAL to 40, RARE_ORE to 40,
                ANCIENT_PART to 40, TECH_PART to 40, LIGHT_MATERIAL to 60, CARE_ESSENCE to 40, WASTE to 100),
        ),
        // ---------------- Özel ----------------
        BuildingDef(
            BuildingType.CARRIER_CARE, "Taşıyıcı Bakım Merkezi", BuildingCategory.SPECIAL,
            "Yiyecek ve suyla taşıyıcıyı besler, iyileştirir ve güvenini artırır.",
            2, 1, 30.0, amounts(WOOD to 60, STONE to 30, CLOTH to 10), 30.0,
            consumes = amounts(FOOD to 2, WATER to 2), workers = 2, profession = Profession.CARETAKER, carrierCarePerMin = 6.0, unique = true,
            adjacency = listOf(Adjacency(BuildingType.CITY_CENTER, 0.25, "Şehir Merkezi yanında +%25 bakım")),
        ),
        BuildingDef(
            BuildingType.TECH_LAB, "Teknoloji Laboratuvarı", BuildingCategory.SPECIAL,
            "Metal ve kristalden teknoloji bileşeni, nadir madenden hafif malzeme üretir.",
            2, 1, 35.0, amounts(WOOD to 60, STONE to 40, METAL to 40, TOOLS to 15), 45.0,
            produces = amounts(TECH_PART to 0.8, LIGHT_MATERIAL to 1.0), consumes = amounts(METAL to 1, CRYSTAL to 0.5, RARE_ORE to 0.5, ENERGY to 2),
            workers = 2, profession = Profession.RESEARCHER, researchPerMin = 1.0, upkeepGold = 0.4,
            requiresTech = TechId.ADVANCED_WORKSHOP, minStage = 1,
        ),
        BuildingDef(
            BuildingType.NAVIGATION_TOWER, "Navigasyon Kulesi", BuildingCategory.SPECIAL,
            "Navigatörler rotayı iyileştirir: yolculuk hızı +%10.",
            1, 1, 20.0, amounts(WOOD to 50, METAL to 15, CLOTH to 10), 30.0,
            workers = 1, profession = Profession.NAVIGATOR, travelSpeedBonus = 0.10, unique = true, minStage = 1, maxLevel = 3,
        ),
        BuildingDef(
            BuildingType.SUPPORT_FRAME, "Destek İskelesi", BuildingCategory.SPECIAL,
            "Çevresindeki (1 kare) binaların yükünü taşıyıcının kemiklerine dağıtır: ağırlıkları %20 azalır.",
            1, 1, 8.0, amounts(WOOD to 40, METAL to 10), 15.0, supportRadius = 1, maxLevel = 1,
        ),
    ).associateBy { it.type }

    fun get(type: BuildingType): BuildingDef = defs.getValue(type)
    val all: List<BuildingDef> get() = BuildingType.entries.map { get(it) }
}
