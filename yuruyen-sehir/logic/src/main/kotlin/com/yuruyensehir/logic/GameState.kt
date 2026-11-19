package com.yuruyensehir.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class PlacedBuilding(
    val id: Int,
    val type: BuildingType,
    var x: Int,
    var y: Int,
    var level: Int = 1,
    var hp: Double = 100.0,
    var workers: Int = 0,
    /** Kalan inşaat/geliştirme süresi (saniye). 0 ise bina çalışır durumda. */
    var constructionLeft: Double = 0.0,
    var constructionTotal: Double = 0.0,
    var enabled: Boolean = true,
) {
    val def: BuildingDef get() = type.def
    val underConstruction: Boolean get() = constructionLeft > 0.0
    val w: Int get() = def.w
    val h: Int get() = def.h
    fun covers(cx: Int, cy: Int) = cx >= x && cx < x + w && cy >= y && cy < y + h
    val centerX: Double get() = x + w / 2.0
    val centerY: Double get() = y + h / 2.0

    /** İki bina ayak izi arasındaki Chebyshev mesafesi (0 = üst üste, 1 = komşu). */
    fun gapTo(o: PlacedBuilding): Int {
        val dx = maxOf(0, maxOf(o.x - (x + w - 1), x - (o.x + o.w - 1)))
        val dy = maxOf(0, maxOf(o.y - (y + h - 1), y - (o.y + o.h - 1)))
        return maxOf(dx, dy)
    }
}

@Serializable
data class ResourceStore(
    val amounts: MutableMap<Resource, Double> = mutableMapOf(),
    /** Kapasite önbelleği; şehir değiştikçe yeniden hesaplanır. */
    @Transient val capacity: MutableMap<Resource, Double> = mutableMapOf(),
) {
    operator fun get(r: Resource): Double = amounts[r] ?: 0.0
    fun cap(r: Resource): Double = if (r == Resource.GOLD) Double.MAX_VALUE else capacity[r] ?: 0.0

    fun canAfford(cost: Amounts): Boolean = cost.all { (r, v) -> get(r) + 1e-9 >= v }

    fun spend(cost: Amounts) {
        require(canAfford(cost)) { "Yetersiz kaynak" }
        cost.forEach { (r, v) -> amounts[r] = (get(r) - v).coerceAtLeast(0.0) }
    }

    /** Kapasiteye kadar ekler; eklenen miktarı döndürür. */
    fun add(r: Resource, v: Double): Double {
        if (v <= 0) return 0.0
        val cur = get(r)
        val room = (cap(r) - cur).coerceAtLeast(0.0)
        val added = minOf(room, v)
        amounts[r] = cur + added
        return added
    }

    fun take(r: Resource, v: Double): Double {
        val cur = get(r)
        val taken = minOf(cur, v.coerceAtLeast(0.0))
        amounts[r] = cur - taken
        return taken
    }

    fun clampToCapacity() {
        amounts.keys.toList().forEach { r -> if (r != Resource.GOLD) amounts[r] = get(r).coerceAtMost(cap(r)) }
    }
}

@Serializable
data class PopulationState(
    var count: Int = 6,
    var happiness: Double = 65.0,
    var health: Double = 75.0,
    var growthTimer: Double = 0.0,
    var declineTimer: Double = 0.0,
)

@Serializable
enum class CarrierUpgrade(val title: String, val description: String, val maxLevel: Int) {
    CAPACITY("Taşıma Kapasitesi", "Her seviye +120 taşıma kapasitesi.", 6),
    SPEED("Hareket Hızı", "Her seviye +%8 yürüme hızı.", 5),
    DURABILITY("Dayanıklılık", "Her seviye yorgunluk artışını %12 azaltır, +15 azami can.", 5),
    EFFICIENCY("Enerji Verimliliği", "Her seviye açlık ve susuzluk artışını %10 azaltır.", 5),
    TERRAIN("Arazi Uyumu", "Her seviye zorlu arazideki yavaşlamayı %15 azaltır.", 4),
    DEFENSE("Savunma", "Her seviye olay ve yük kayması hasarını %10 azaltır.", 5),
    BACK_EXPANSION("Sırt Genişletme", "Şehir kurulabilecek alanı büyütür.", 3),
}

@Serializable
enum class CarrierAbility(val title: String, val description: String, val unlockLevel: Int, val durationSec: Double, val cooldownSec: Double, val energyCost: Double) {
    QUICK_MARCH("Hızlı Yürüyüş", "2 dakika boyunca +%40 hız. Yorgunluk daha hızlı artar.", 2, 120.0, 600.0, 20.0),
    QUICK_REST("Hızlı Dinlenme", "3 dakika boyunca dinlenme iki kat etkili.", 3, 180.0, 900.0, 0.0),
    LOAD_SUPPORT("Kısa Süreli Yük Desteği", "2 dakika boyunca aşırı yük cezası yok.", 4, 120.0, 900.0, 25.0),
}

@Serializable
data class CarrierState(
    val species: String = "Dev Kara Kaplumbağası",
    var name: String = "Bilge Tosun",
    var health: Double = 100.0,
    var hunger: Double = 10.0,
    var thirst: Double = 10.0,
    var fatigue: Double = 0.0,
    var energy: Double = 100.0,
    var trust: Double = 60.0,
    var level: Int = 1,
    var xp: Double = 0.0,
    var resting: Boolean = false,
    var forcedRest: Boolean = false,
    var autoFeed: Boolean = true,
    val upgrades: MutableMap<CarrierUpgrade, Int> = mutableMapOf(),
    val abilityActiveLeft: MutableMap<CarrierAbility, Double> = mutableMapOf(),
    val abilityCooldown: MutableMap<CarrierAbility, Double> = mutableMapOf(),
) {
    fun upgrade(u: CarrierUpgrade): Int = upgrades[u] ?: 0
    fun abilityActive(a: CarrierAbility) = (abilityActiveLeft[a] ?: 0.0) > 0.0
}

@Serializable
data class TravelState(
    var position: Cell = Cell(0, 0),
    val route: MutableList<Cell> = mutableListOf(),
    var routeIndex: Int = 0,
    var progress: Double = 0.0,
    var routeName: String = "",
    val explored: MutableSet<Long> = mutableSetOf(),
    /** Hücre anahtarı -> toplanmış miktar (tükenme). Yalnız değişen hücreler saklanır. */
    val depleted: MutableMap<Long, Double> = mutableMapOf(),
    var distanceTraveled: Int = 0,
) {
    val traveling: Boolean get() = routeIndex < route.size
    val destination: Cell? get() = route.lastOrNull()?.takeIf { traveling }
}

@Serializable
enum class LogKind { INFO, BUILD, CARRIER, TRAVEL, EVENT, RESEARCH, STAGE, WARNING }

@Serializable
data class LogEntry(val day: Int, val text: String, val kind: LogKind = LogKind.INFO)

@Serializable
data class EventOption(val label: String, val cost: Amounts = emptyMap(), val reward: Amounts = emptyMap(), val effect: String = "")

@Serializable
data class PendingEvent(val kind: String, val title: String, val text: String, val options: List<EventOption>)

@Serializable
data class Settings(
    var graphicsHigh: Boolean = true,
    var showWeightOverlay: Boolean = true,
    var sound: Boolean = true,
    var gameSpeed: Int = 1,
)

@Serializable
data class Stats(
    var buildingsBuilt: Int = 0,
    var upgradesDone: Int = 0,
    var eventsResolved: Int = 0,
    var peakPopulation: Int = 6,
    var foodProduced: Double = 0.0,
    var waterProduced: Double = 0.0,
    var gathered: Double = 0.0,
)

@Serializable
data class GameState(
    var version: Int = SaveCodec.CURRENT_VERSION,
    val seed: Long,
    var gameSeconds: Double = 0.0,
    val store: ResourceStore = ResourceStore(),
    val buildings: MutableList<PlacedBuilding> = mutableListOf(),
    var nextBuildingId: Int = 1,
    val population: PopulationState = PopulationState(),
    val carrier: CarrierState = CarrierState(),
    val travel: TravelState = TravelState(),
    val research: ResearchState = ResearchState(),
    val log: MutableList<LogEntry> = mutableListOf(),
    var pendingEvent: PendingEvent? = null,
    var rngState: Long = seed xor 0x5DEECE66DL,
    var stage: Int = 0,
    val settings: Settings = Settings(),
    val stats: Stats = Stats(),
    var lastSavedEpochMs: Long = 0L,
    var eventCooldown: Double = 0.0,
) {
    /** Şehir yapısı değiştiğinde artar; önbellekli hesaplamalar bunu izler. */
    @Transient var cityVersion: Int = 0

    @Transient val world: WorldGenerator = WorldGenerator(seed)

    /** Bina başına son üretim adımının durumu (kalıcı değil). */
    @Transient val buildingStatus: HashMap<Int, BuildingStatus> = HashMap()

    val day: Int get() = (gameSeconds / SECONDS_PER_DAY).toInt() + 1

    fun log(text: String, kind: LogKind = LogKind.INFO) {
        log += LogEntry(day, text, kind)
        if (log.size > MAX_LOG) log.removeAt(0)
    }

    fun building(id: Int) = buildings.firstOrNull { it.id == id }
    fun buildingAt(x: Int, y: Int) = buildings.firstOrNull { it.covers(x, y) }
    fun count(type: BuildingType) = buildings.count { it.type == type }
    fun activeBuildings(): List<PlacedBuilding> = buildings.filter { !it.underConstruction && it.enabled }

    fun nextRandom(): Double {
        var x = rngState
        x = x xor (x shl 13); x = x xor (x ushr 7); x = x xor (x shl 17)
        rngState = x
        return (x ushr 11).toDouble() / (1L shl 53).toDouble()
    }

    companion object {
        const val SECONDS_PER_DAY = 300.0
        const val MAX_LOG = 200
    }
}

data class ActionResult(val success: Boolean, val message: String) {
    companion object {
        fun ok(msg: String) = ActionResult(true, msg)
        fun fail(msg: String) = ActionResult(false, msg)
    }
}
