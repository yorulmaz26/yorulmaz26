package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import kotlin.math.sqrt

object ResourceSystem {
    fun recalcCapacity(state: GameState) {
        val cap = state.store.capacity
        cap.clear()
        for (b in state.buildings) {
            if (b.underConstruction && b.level == 1) continue
            val lm = 1.0 + 0.4 * (b.level - 1)
            b.def.storage.forEach { (r, v) -> cap[r] = (cap[r] ?: 0.0) + v * lm }
        }
        // Mutlak taban: depo olmasa bile küçük bir yığın tutulabilir.
        Resource.entries.forEach { r -> if (r != GOLD) cap[r] = (cap[r] ?: 0.0).coerceAtLeast(20.0) }
    }
}

data class BalanceReport(
    val totalWeight: Double,
    val buildingWeight: Double,
    val cargoWeight: Double,
    val capacity: Double,
    /** Ağırlık merkezinin ızgara merkezine göre normalize kayması (-1..1). +x ön, +y sol. */
    val offsetX: Double,
    val offsetY: Double,
    /** 0..1 */
    val balance: Double,
    val frontShare: Double,
    val leftShare: Double,
    val loadRatio: Double,
    /** 0..1 hareket verimliliği (yük + denge). */
    val movementEfficiency: Double,
) {
    val balancePercent get() = (balance * 100).toInt()
    val efficiencyPercent get() = (movementEfficiency * 100).toInt()
    val overloaded get() = loadRatio > 1.0
}

object WeightBalanceSystem {
    fun buildingWeightFactor(state: GameState): Double {
        var f = 1.0
        if (ResearchSystem.has(state, TechId.LIGHT_TIMBER)) f *= 0.90
        if (ResearchSystem.has(state, TechId.COMPOSITE_ROOFS)) f *= 0.85
        return f
    }

    fun cargoWeightFactor(state: GameState) = if (ResearchSystem.has(state, TechId.HANGING_STORAGE)) 0.75 else 1.0

    fun capacity(state: GameState): Double {
        var c = 600.0 + 120.0 * state.carrier.upgrade(CarrierUpgrade.CAPACITY) + 25.0 * (state.carrier.level - 1)
        if (ResearchSystem.has(state, TechId.MUSCLE_TRAINING)) c *= 1.15
        return c
    }

    fun weightOf(state: GameState, b: PlacedBuilding, supported: Boolean): Double {
        var w = b.def.weight * (1.0 + 0.3 * (b.level - 1)) * buildingWeightFactor(state)
        if (supported) w *= 0.8
        return w
    }

    /**
     * Ağırlık ve denge raporu. [extra] verilirse o bina eklenmiş gibi hesaplanır (yerleştirme önizlemesi);
     * [movedId] ve [extra] birlikte verilirse bina taşınmış gibi hesaplanır.
     */
    fun compute(state: GameState, extra: PlacedBuilding? = null, movedId: Int = -1): BalanceReport {
        val list = ArrayList<PlacedBuilding>(state.buildings.size + 1)
        for (b in state.buildings) if (b.id != movedId) list += b
        if (extra != null) list += extra

        val supports = list.filter { it.def.supportRadius > 0 }
        // Kaynak ağırlığı ilgili depolara kapasiteleriyle orantılı dağılır.
        val cargoFactor = cargoWeightFactor(state)
        val storageTotals = HashMap<Resource, Double>()
        for (b in list) b.def.storage.forEach { (r, v) -> storageTotals[r] = (storageTotals[r] ?: 0.0) + v * (1.0 + 0.4 * (b.level - 1)) }

        var total = 0.0
        var bw = 0.0
        var cw = 0.0
        var mx = 0.0
        var my = 0.0
        var front = 0.0
        var left = 0.0
        for (b in list) {
            val supported = b.def.supportRadius == 0 && supports.any { s -> s.gapTo(b) <= s.def.supportRadius }
            var w = weightOf(state, b, supported)
            bw += w
            var cargo = 0.0
            b.def.storage.forEach { (r, v) ->
                val share = v * (1.0 + 0.4 * (b.level - 1)) / (storageTotals[r] ?: 1.0)
                cargo += state.store[r] * share * r.unitWeight * cargoFactor
            }
            cw += cargo
            w += cargo
            total += w
            val dx = b.centerX - CityGrid.CENTER_X
            val dy = b.centerY - CityGrid.CENTER_Y
            mx += w * dx
            my += w * dy
            front += w * (0.5 + (dx / CityGrid.GRID_W).coerceIn(-0.5, 0.5))
            left += w * (0.5 + (dy / CityGrid.GRID_H).coerceIn(-0.5, 0.5))
        }
        val cap = capacity(state)
        val ox = if (total > 0) (mx / total) / CityGrid.radiusX(state) else 0.0
        val oy = if (total > 0) (my / total) / CityGrid.radiusY(state) else 0.0
        val imbalance = sqrt(ox * ox + (oy * 1.3) * (oy * 1.3))
        val balance = (1.0 - imbalance * 1.6).coerceIn(0.0, 1.0)
        val ratio = total / cap
        val loadSupport = state.carrier.abilityActive(CarrierAbility.LOAD_SUPPORT)
        val loadFactor = when {
            loadSupport && ratio <= 1.3 -> 1.0
            ratio <= 0.6 -> 1.0
            ratio <= 1.0 -> 1.0 - (ratio - 0.6)
            else -> (0.6 - (ratio - 1.0) * 1.5).coerceAtLeast(0.2)
        }
        val balanceFactor = 0.55 + 0.45 * balance
        return BalanceReport(
            totalWeight = total, buildingWeight = bw, cargoWeight = cw, capacity = cap,
            offsetX = ox, offsetY = oy, balance = balance,
            frontShare = if (total > 0) front / total else 0.5,
            leftShare = if (total > 0) left / total else 0.5,
            loadRatio = ratio,
            movementEfficiency = (loadFactor * balanceFactor).coerceIn(0.0, 1.0),
        )
    }
}

/** Bir binanın anlık çalışma durumu (arayüz ve testler için). */
data class BuildingStatus(val efficiency: Double, val inputRatio: Double, val missingInput: Resource?)

object ProductionSystem {
    fun researchMultiplier(state: GameState, type: BuildingType): Double {
        var m = 1.0
        when (type) {
            BuildingType.FARM, BuildingType.GREENHOUSE -> if (ResearchSystem.has(state, TechId.IRRIGATION)) m *= 1.2
            BuildingType.WORKSHOP, BuildingType.METALWORKS, BuildingType.TEXTILE -> if (ResearchSystem.has(state, TechId.ADVANCED_WORKSHOP)) m *= 1.2
            BuildingType.WATER_COLLECTOR -> if (ResearchSystem.has(state, TechId.ADVANCED_WATER)) m *= 1.3
            BuildingType.POWER_PLANT, BuildingType.MILL -> if (ResearchSystem.has(state, TechId.EFFICIENT_BOILERS)) m *= 1.25
            BuildingType.GATHERER -> if (ResearchSystem.has(state, TechId.BETTER_GATHERERS)) m *= 1.3
            else -> {}
        }
        return m
    }

    fun happinessFactor(state: GameState) = 0.6 + 0.6 * (state.population.happiness / 100.0).coerceIn(0.0, 1.0)

    /** Girdiler hariç binanın verim çarpanı. */
    fun efficiency(state: GameState, b: PlacedBuilding): Double {
        if (b.underConstruction || !b.enabled) return 0.0
        val def = b.def
        val workerRatio = if (def.workers == 0) 1.0 else b.workers.toDouble() / def.workers
        val hpFactor = 0.4 + 0.6 * (b.hp / 100.0)
        val upkeepPenalty = if (def.upkeepGold > 0 && state.store[GOLD] <= 0.0) 0.5 else 1.0
        return workerRatio * hpFactor * happinessFactor(state) * (1.0 + BuildingSystem.adjacencyBonus(state, b)) *
            BuildingSystem.levelMultiplier(b) * researchMultiplier(state, b.type) * upkeepPenalty
    }

    /** Biyom etkisi: su toplayıcılar biyomun su bulunabilirliğine göre çalışır. */
    fun biomeOutputFactor(state: GameState, b: PlacedBuilding): Double {
        val biome = state.world.biomeAt(state.travel.position)
        return when (b.type) {
            BuildingType.WATER_COLLECTOR -> 0.35 + 0.8 * biome.waterAvail
            BuildingType.FARM -> 0.6 + 0.5 * biome.forage
            else -> 1.0
        }
    }

    fun consumesWithModifiers(state: GameState, b: PlacedBuilding): Amounts {
        val saving = ResearchSystem.has(state, TechId.ENERGY_SAVING)
        return b.def.consumes.mapValues { (r, v) -> if (r == ENERGY && saving) v * 0.8 else v }
    }

    fun step(state: GameState, dt: Double) {
        val minutes = dt / 60.0
        var research = 0.0
        val gatherFactor = if (state.travel.traveling) 0.4 else 1.0
        for (b in state.buildings) {
            if (b.underConstruction || !b.enabled) {
                state.buildingStatus[b.id] = BuildingStatus(0.0, 0.0, null)
                continue
            }
            val eff = efficiency(state, b)
            if (eff <= 0.0) {
                state.buildingStatus[b.id] = BuildingStatus(0.0, 0.0, null)
                continue
            }
            val cons = consumesWithModifiers(state, b)
            var ratio = 1.0
            var missing: Resource? = null
            for ((r, v) in cons) {
                val need = v * eff * minutes
                if (need <= 0) continue
                val have = state.store[r]
                if (have < need) {
                    val rr = have / need
                    if (rr < ratio) { ratio = rr; missing = r }
                }
            }
            if (ratio > 0) {
                cons.forEach { (r, v) -> state.store.take(r, v * eff * minutes * ratio) }
                val out = eff * ratio * biomeOutputFactor(state, b)
                b.def.produces.forEach { (r, v) ->
                    val added = state.store.add(r, v * out * minutes)
                    if (r == FOOD) state.stats.foodProduced += added
                    if (r == WATER) state.stats.waterProduced += added
                }
                research += b.def.researchPerMin * eff * ratio * minutes
                if (b.def.gatherPerMin > 0) gather(state, b.def.gatherPerMin * eff * gatherFactor * minutes)
                if (b.def.carrierCarePerMin > 0) CarrierSystem.applyCare(state, b.def.carrierCarePerMin * eff * ratio * minutes)
            }
            state.buildingStatus[b.id] = BuildingStatus(eff, ratio, missing)
        }
        if (research > 0) ResearchSystem.addPoints(state, research)
        // Vergi ve bakım giderleri
        val pop = state.population.count
        var tax = 0.0
        var upkeep = 0.0
        for (b in state.buildings) {
            if (b.underConstruction || !b.enabled) continue
            if (b.def.taxPerPop > 0) {
                val workerRatio = if (b.def.workers == 0) 1.0 else b.workers.toDouble() / b.def.workers
                tax += b.def.taxPerPop * pop * workerRatio
            }
            upkeep += b.def.upkeepGold * b.level
        }
        tax *= happinessFactor(state)
        state.store.add(GOLD, tax * minutes)
        state.store.take(GOLD, upkeep * minutes)
    }

    private fun gather(state: GameState, amount: Double) {
        val pos = state.travel.position
        val key = pos.key()
        val deposit = state.world.depositSize(pos)
        val used = state.travel.depleted[key] ?: 0.0
        val left = deposit - used
        if (left <= 0) return
        val take = minOf(left, amount)
        val biome = state.world.biomeAt(pos)
        var actual = 0.0
        biome.yields.forEach { (r, share) -> actual += state.store.add(r, take * share) }
        state.travel.depleted[key] = used + take
        state.stats.gathered += actual
    }

    fun depositLeft(state: GameState, c: Cell): Double =
        (state.world.depositSize(c) - (state.travel.depleted[c.key()] ?: 0.0)).coerceAtLeast(0.0)
}

/** Dakika başına tahmini kaynak akışı (ekonomi ekranı için). */
object EconomySystem {
    data class Flow(val produced: Map<Resource, Double>, val consumed: Map<Resource, Double>) {
        fun net(r: Resource) = (produced[r] ?: 0.0) - (consumed[r] ?: 0.0)
    }

    fun estimate(state: GameState): Flow {
        val p = HashMap<Resource, Double>()
        val c = HashMap<Resource, Double>()
        fun addP(r: Resource, v: Double) { p[r] = (p[r] ?: 0.0) + v }
        fun addC(r: Resource, v: Double) { c[r] = (c[r] ?: 0.0) + v }
        val biome = state.world.biomeAt(state.travel.position)
        val gatherFactor = if (state.travel.traveling) 0.4 else 1.0
        for (b in state.buildings) {
            val eff = ProductionSystem.efficiency(state, b)
            if (eff <= 0) continue
            val status = state.buildingStatus[b.id]
            val ratio = status?.inputRatio?.takeIf { it > 0 } ?: 1.0
            ProductionSystem.consumesWithModifiers(state, b).forEach { (r, v) -> addC(r, v * eff * ratio) }
            val out = eff * ratio * ProductionSystem.biomeOutputFactor(state, b)
            b.def.produces.forEach { (r, v) -> addP(r, v * out) }
            if (b.def.gatherPerMin > 0 && ProductionSystem.depositLeft(state, state.travel.position) > 0) {
                biome.yields.forEach { (r, s) -> addP(r, b.def.gatherPerMin * eff * gatherFactor * s) }
            }
            if (b.def.upkeepGold > 0) addC(GOLD, b.def.upkeepGold * b.level)
            if (b.def.taxPerPop > 0) {
                val wr = if (b.def.workers == 0) 1.0 else b.workers.toDouble() / b.def.workers
                addP(GOLD, b.def.taxPerPop * state.population.count * wr * ProductionSystem.happinessFactor(state))
            }
        }
        val pop = state.population.count
        addC(FOOD, pop * PopulationSystem.FOOD_PER_POP)
        addC(WATER, pop * PopulationSystem.WATER_PER_POP)
        addP(WASTE, pop * PopulationSystem.WASTE_PER_POP)
        val carrier = CarrierSystem.estimatedFeedPerMinute(state)
        addC(FOOD, carrier.first)
        addC(WATER, carrier.second)
        return Flow(p, c)
    }
}
