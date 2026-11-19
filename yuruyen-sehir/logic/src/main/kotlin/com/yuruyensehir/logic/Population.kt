package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*

data class HappinessBreakdown(val target: Double, val factors: List<Pair<String, Double>>)

object PopulationSystem {
    const val FOOD_PER_POP = 0.5
    const val WATER_PER_POP = 0.5
    const val WASTE_PER_POP = 0.08
    const val MIN_POPULATION = 3
    const val GROWTH_SECONDS = 40.0
    const val DECLINE_SECONDS = 60.0

    fun housing(state: GameState): Int = state.buildings.filter { !(it.underConstruction && it.level == 1) }
        .sumOf { it.def.housing * (1.0 + 0.5 * (it.level - 1)) }.toInt()

    fun employed(state: GameState): Int = state.buildings.sumOf { it.workers }
    fun freeWorkers(state: GameState): Int = (state.population.count - state.population.away - employed(state)).coerceAtLeast(0)

    fun byProfession(state: GameState): Map<Profession, Int> {
        val m = LinkedHashMap<Profession, Int>()
        for (b in state.buildings) {
            val p = b.def.profession ?: continue
            if (b.workers > 0) m[p] = (m[p] ?: 0) + b.workers
        }
        return m
    }

    /** Boştaki çalışanları eksik iş yerlerine dağıtır (en eski bina önce). */
    fun autoAssign(state: GameState) {
        var free = freeWorkers(state)
        if (free <= 0) return
        for (b in state.buildings.sortedBy { it.id }) {
            if (free <= 0) break
            if (b.underConstruction || !b.enabled) continue
            val need = b.def.workers - b.workers
            if (need > 0) {
                val n = minOf(need, free)
                b.workers += n
                free -= n
            }
        }
    }

    /** Nüfus azaldığında en yeni binalardan çalışan çekilir. */
    fun releaseExcess(state: GameState) {
        var excess = employed(state) - (state.population.count - state.population.away)
        if (excess <= 0) return
        for (b in state.buildings.sortedByDescending { it.id }) {
            if (excess <= 0) break
            val n = minOf(b.workers, excess)
            b.workers -= n
            excess -= n
        }
    }

    fun healthCoverage(state: GameState): Double {
        val houses = state.buildings.filter { it.def.housing > 0 && it.type != BuildingType.CITY_CENTER && !it.underConstruction }
        if (houses.isEmpty()) return 0.0
        val centers = state.buildings.filter { it.def.healthRadius > 0 && !it.underConstruction && it.enabled && it.workers > 0 }
        val covered = houses.count { h -> centers.any { c -> c.gapTo(h) <= c.def.healthRadius } }
        return covered.toDouble() / houses.size
    }

    fun happinessBreakdown(state: GameState): HappinessBreakdown {
        val f = ArrayList<Pair<String, Double>>()
        f += "Temel" to 55.0
        val housing = housing(state)
        val pop = state.population.count
        if (pop > housing) f += "Kalabalık barınma" to -15.0 else if (housing - pop >= 4) f += "Geniş barınma" to 4.0
        var amenities = 0.0
        for (b in state.buildings) if (!b.underConstruction && b.enabled && b.def.happiness > 0) {
            val wr = if (b.def.workers == 0) 1.0 else b.workers.toDouble() / b.def.workers
            amenities += b.def.happiness * (1.0 + 0.3 * (b.level - 1)) * wr
        }
        if (amenities > 0) f += "Sosyal yapılar" to amenities.coerceAtMost(30.0)
        val cov = healthCoverage(state)
        if (cov > 0) f += "Sağlık hizmeti" to 10.0 * cov
        if (state.store[FOOD] < 1.0) f += "Yiyecek kıtlığı" to -25.0
        if (state.store[WATER] < 1.0) f += "Su kıtlığı" to -25.0
        if (state.store[WASTE] >= state.store.cap(WASTE) - 1) f += "Biriken atık" to -10.0
        if (state.carrier.health < 40) f += "Taşıyıcı endişesi" to -10.0
        if (state.carrier.trust > 80) f += "Mutlu taşıyıcı" to 5.0
        val target = f.sumOf { it.second }.coerceIn(0.0, 100.0)
        return HappinessBreakdown(target, f)
    }

    fun step(state: GameState, dt: Double, offline: Boolean = false) {
        val p = state.population
        val minutes = dt / 60.0
        val pop = p.count
        // Tüketim
        val foodNeed = pop * FOOD_PER_POP * minutes
        val waterNeed = pop * WATER_PER_POP * minutes
        val gotFood = state.store.take(FOOD, foodNeed)
        val gotWater = state.store.take(WATER, waterNeed)
        state.store.add(WASTE, pop * WASTE_PER_POP * minutes)
        val starving = gotFood + 1e-9 < foodNeed || gotWater + 1e-9 < waterNeed

        // Mutluluk ve sağlık hedefe doğru yavaşça yaklaşır.
        val target = happinessBreakdown(state).target
        p.happiness += (target - p.happiness) * (minutes * 0.6).coerceAtMost(1.0)
        val healthTarget = (70.0 + 25.0 * healthCoverage(state) - if (starving) 35.0 else 0.0).coerceIn(5.0, 100.0)
        p.health += (healthTarget - p.health) * (minutes * 0.4).coerceAtMost(1.0)

        val housing = housing(state)
        if (!starving && p.happiness >= 50 && pop < housing && state.store[FOOD] > 15 && state.store[WATER] > 15) {
            p.growthTimer += dt * (p.happiness / 80.0)
            p.declineTimer = 0.0
            if (p.growthTimer >= GROWTH_SECONDS) {
                p.growthTimer = 0.0
                p.count++
                if (p.count > state.stats.peakPopulation) state.stats.peakPopulation = p.count
                if (p.count % 5 == 0) state.log("Nüfus ${p.count} kişiye ulaştı.", LogKind.INFO)
                autoAssign(state)
            }
        } else if (starving || p.health < 25 || p.happiness < 15) {
            p.declineTimer += dt
            if (p.declineTimer >= DECLINE_SECONDS && pop > MIN_POPULATION) {
                p.declineTimer = 0.0
                p.count--
                releaseExcess(state)
                if (!offline) state.log("Zor koşullar yüzünden bir yerleşimci şehri terk etti.", LogKind.WARNING)
            }
        } else {
            p.growthTimer = (p.growthTimer - dt * 0.2).coerceAtLeast(0.0)
            p.declineTimer = 0.0
        }
    }
}
