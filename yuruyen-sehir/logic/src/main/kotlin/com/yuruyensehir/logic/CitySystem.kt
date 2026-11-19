package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import kotlin.math.pow
import kotlin.math.roundToInt

/** Taşıyıcının sırtındaki şehir ızgarası. X ekseni arka(0) -> ön(GRID_W), Y ekseni sağ(0) -> sol(GRID_H). */
object CityGrid {
    const val GRID_W = 18
    const val GRID_H = 12
    const val CENTER_X = GRID_W / 2.0
    const val CENTER_Y = GRID_H / 2.0

    fun radiusX(state: GameState) = 5.6 + 1.15 * state.carrier.upgrade(CarrierUpgrade.BACK_EXPANSION)
    fun radiusY(state: GameState) = 4.0 + 0.62 * state.carrier.upgrade(CarrierUpgrade.BACK_EXPANSION)

    fun usable(state: GameState, x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= GRID_W || y >= GRID_H) return false
        val dx = (x + 0.5 - CENTER_X) / radiusX(state)
        val dy = (y + 0.5 - CENTER_Y) / radiusY(state)
        return dx * dx + dy * dy <= 1.0
    }

    fun usableCells(state: GameState): Int {
        var n = 0
        for (x in 0 until GRID_W) for (y in 0 until GRID_H) if (usable(state, x, y)) n++
        return n
    }
}

enum class PlaceError(val message: String) {
    OUT_OF_AREA("Bina taşıyıcının sırtı dışına taşıyor."),
    OVERLAP("Bu alan dolu."),
    NO_RESOURCES("Yetersiz kaynak."),
    LOCKED("Bu bina henüz açılmadı."),
    UNIQUE("Bu binadan yalnızca bir tane olabilir."),
}

object BuildingSystem {

    fun unlocked(state: GameState, def: BuildingDef): Boolean =
        def.buildable && state.stage >= def.minStage && (def.requiresTech == null || ResearchSystem.has(state, def.requiresTech))

    fun lockReason(state: GameState, def: BuildingDef): String? = when {
        !def.buildable -> "İnşa edilemez"
        state.stage < def.minStage -> "Gerekli aşama: ${StageSystem.title(def.minStage)}"
        def.requiresTech != null && !ResearchSystem.has(state, def.requiresTech) -> "Gerekli araştırma: ${TechCatalog.get(def.requiresTech).title}"
        else -> null
    }

    fun footprintFits(state: GameState, type: BuildingType, x: Int, y: Int, ignoreId: Int = -1): PlaceError? {
        val def = type.def
        for (cx in x until x + def.w) for (cy in y until y + def.h) {
            if (!CityGrid.usable(state, cx, cy)) return PlaceError.OUT_OF_AREA
            val other = state.buildingAt(cx, cy)
            if (other != null && other.id != ignoreId) return PlaceError.OVERLAP
        }
        return null
    }

    fun canPlace(state: GameState, type: BuildingType, x: Int, y: Int): PlaceError? {
        val def = type.def
        if (!unlocked(state, def)) return PlaceError.LOCKED
        if (def.unique && state.count(type) > 0) return PlaceError.UNIQUE
        footprintFits(state, type, x, y)?.let { return it }
        if (!state.store.canAfford(def.cost)) return PlaceError.NO_RESOURCES
        return null
    }

    fun place(state: GameState, type: BuildingType, x: Int, y: Int, instant: Boolean = false): ActionResult {
        canPlace(state, type, x, y)?.let { return ActionResult.fail(it.message) }
        val def = type.def
        state.store.spend(def.cost)
        val time = if (instant) 0.0 else def.buildSeconds * buildTimeFactor(state)
        val b = PlacedBuilding(state.nextBuildingId++, type, x, y, constructionLeft = time, constructionTotal = time)
        state.buildings += b
        if (time <= 0.0) onCompleted(state, b, upgrade = false)
        state.cityVersion++
        return ActionResult.ok("${def.title} inşaatı başladı.")
    }

    /** Boşta inşaatçı (çalışmayan nüfus) çoksa inşaat hızlanır. */
    fun buildTimeFactor(state: GameState): Double {
        val free = PopulationSystem.freeWorkers(state)
        return (1.0 / (1.0 + 0.08 * free.coerceAtMost(10))).coerceIn(0.45, 1.0)
    }

    fun upgradeCost(b: PlacedBuilding): Amounts {
        val f = 0.7 * (b.level + 1.0).pow(1.35)
        val base = b.def.cost.mapValues { (it.value * f).roundToInt().toDouble() }.toMutableMap()
        if (b.level >= 2) base[TOOLS] = (base[TOOLS] ?: 0.0) + 5.0 * b.level
        return base
    }

    fun upgrade(state: GameState, id: Int): ActionResult {
        val b = state.building(id) ?: return ActionResult.fail("Bina bulunamadı.")
        if (b.underConstruction) return ActionResult.fail("Bina inşa ediliyor.")
        if (b.level >= b.def.maxLevel) return ActionResult.fail("Bina en yüksek seviyede.")
        val cost = upgradeCost(b)
        if (!state.store.canAfford(cost)) return ActionResult.fail("Yetersiz kaynak: ${cost.describe()}")
        state.store.spend(cost)
        val t = (b.def.buildSeconds.coerceAtLeast(20.0)) * (0.8 + 0.4 * b.level) * buildTimeFactor(state)
        b.constructionLeft = t
        b.constructionTotal = t
        b.level += 1
        state.cityVersion++
        return ActionResult.ok("${b.def.title} seviye ${b.level} için geliştiriliyor.")
    }

    /** Bina taşıma: malzemelerin %15'i kadar ücret. */
    fun moveCost(b: PlacedBuilding): Amounts = b.def.cost.filterKeys { it != GOLD }.mapValues { (it.value * 0.15).roundToInt().toDouble() }

    fun move(state: GameState, id: Int, x: Int, y: Int): ActionResult {
        val b = state.building(id) ?: return ActionResult.fail("Bina bulunamadı.")
        if (b.x == x && b.y == y) return ActionResult.fail("Bina zaten burada.")
        footprintFits(state, b.type, x, y, ignoreId = id)?.let { return ActionResult.fail(it.message) }
        val cost = moveCost(b)
        if (!state.store.canAfford(cost)) return ActionResult.fail("Taşıma için yetersiz kaynak: ${cost.describe()}")
        state.store.spend(cost)
        b.x = x
        b.y = y
        state.cityVersion++
        return ActionResult.ok("${b.def.title} yeni yerine taşındı.")
    }

    fun demolish(state: GameState, id: Int): ActionResult {
        val b = state.building(id) ?: return ActionResult.fail("Bina bulunamadı.")
        if (b.type == BuildingType.CITY_CENTER) return ActionResult.fail("Şehir Merkezi yıkılamaz.")
        state.buildings.remove(b)
        val refund = b.def.cost * 0.5
        refund.forEach { (r, v) -> state.store.add(r, v) }
        state.cityVersion++
        ResourceSystem.recalcCapacity(state)
        state.store.clampToCapacity()
        return ActionResult.ok("${b.def.title} söküldü, malzemelerin yarısı geri kazanıldı.")
    }

    fun repairCost(b: PlacedBuilding): Amounts {
        val missing = (100.0 - b.hp) / 100.0
        return b.def.cost.filterKeys { it == WOOD || it == STONE || it == METAL }
            .mapValues { (it.value * 0.4 * missing).roundToInt().toDouble().coerceAtLeast(1.0) }
    }

    fun repair(state: GameState, id: Int): ActionResult {
        val b = state.building(id) ?: return ActionResult.fail("Bina bulunamadı.")
        if (b.hp >= 99.5) return ActionResult.fail("Bina hasarsız.")
        val cost = repairCost(b)
        if (!state.store.canAfford(cost)) return ActionResult.fail("Onarım için yetersiz kaynak: ${cost.describe()}")
        state.store.spend(cost)
        b.hp = 100.0
        return ActionResult.ok("${b.def.title} onarıldı.")
    }

    fun setEnabled(state: GameState, id: Int, enabled: Boolean): ActionResult {
        val b = state.building(id) ?: return ActionResult.fail("Bina bulunamadı.")
        if (b.type == BuildingType.CITY_CENTER) return ActionResult.fail("Şehir Merkezi kapatılamaz.")
        b.enabled = enabled
        if (!enabled) b.workers = 0 else PopulationSystem.autoAssign(state)
        state.cityVersion++
        return ActionResult.ok(if (enabled) "${b.def.title} yeniden açıldı." else "${b.def.title} kapatıldı; işçiler serbest.")
    }

    fun setWorkers(state: GameState, id: Int, n: Int): ActionResult {
        val b = state.building(id) ?: return ActionResult.fail("Bina bulunamadı.")
        val target = n.coerceIn(0, b.def.workers)
        val free = PopulationSystem.freeWorkers(state)
        val delta = target - b.workers
        if (delta > free) return ActionResult.fail("Boşta çalışan yok.")
        b.workers = target
        return ActionResult.ok("${b.def.title}: ${b.workers}/${b.def.workers} çalışan.")
    }

    fun tickConstruction(state: GameState, dt: Double) {
        for (b in state.buildings) {
            if (b.constructionLeft > 0) {
                b.constructionLeft -= dt
                if (b.constructionLeft <= 0) {
                    b.constructionLeft = 0.0
                    onCompleted(state, b, upgrade = b.level > 1)
                }
            }
        }
    }

    private fun onCompleted(state: GameState, b: PlacedBuilding, upgrade: Boolean) {
        if (upgrade) {
            state.stats.upgradesDone++
            state.log("${b.def.title} seviye ${b.level} oldu.", LogKind.BUILD)
        } else {
            state.stats.buildingsBuilt++
            if (b.type != BuildingType.CITY_CENTER) state.log("${b.def.title} tamamlandı.", LogKind.BUILD)
        }
        state.cityVersion++
        ResourceSystem.recalcCapacity(state)
        PopulationSystem.autoAssign(state)
    }

    fun levelMultiplier(b: PlacedBuilding) = 1.0 + 0.5 * (b.level - 1)

    /** Komşuluk bonusu: her komşu türü bir kez sayılır. */
    fun adjacencyBonus(state: GameState, b: PlacedBuilding): Double {
        val adj = b.def.adjacency
        if (adj.isEmpty()) return 0.0
        val mult = if (ResearchSystem.has(state, TechId.EFFICIENT_LAYOUT)) 1.5 else 1.0
        var bonus = 0.0
        for (a in adj) {
            if (a.bonus <= 0) continue
            if (state.buildings.any { it.type == a.neighbor && it.id != b.id && !it.underConstruction && b.gapTo(it) <= 1 }) bonus += a.bonus * mult
        }
        return bonus
    }

    fun activeAdjacencies(state: GameState, b: PlacedBuilding): List<Adjacency> =
        b.def.adjacency.filter { a -> state.buildings.any { it.type == a.neighbor && it.id != b.id && !it.underConstruction && b.gapTo(it) <= 1 } }
}
