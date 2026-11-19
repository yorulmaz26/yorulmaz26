package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import kotlin.math.pow
import kotlin.math.roundToInt

object CarrierSystem {
    const val HEALTH_FLOOR = 15.0
    const val FEED_THRESHOLD = 35.0
    /** Bir açlık puanını gidermek için gereken yiyecek. */
    const val FOOD_PER_POINT = 0.8
    const val WATER_PER_POINT = 0.8

    fun maxHealth(state: GameState) = 100.0 + 15.0 * state.carrier.upgrade(CarrierUpgrade.DURABILITY)

    fun xpForNext(level: Int) = (100.0 * level.toDouble().pow(1.5)).roundToInt().toDouble()

    fun needMultiplier(state: GameState): Double {
        var m = 1.0 - 0.10 * state.carrier.upgrade(CarrierUpgrade.EFFICIENCY)
        if (ResearchSystem.has(state, TechId.NUTRITIOUS_FODDER)) m *= 0.85
        return m * Bonuses.needs(state)
    }

    fun fatigueMultiplier(state: GameState): Double = 1.0 - 0.12 * state.carrier.upgrade(CarrierUpgrade.DURABILITY)

    fun restMultiplier(state: GameState): Double {
        var m = 1.0
        if (ResearchSystem.has(state, TechId.DEEP_SLEEP)) m *= 1.4
        if (state.carrier.abilityActive(CarrierAbility.QUICK_REST)) m *= 2.0
        return m
    }

    fun trustFactor(state: GameState) = 0.85 + 0.25 * (state.carrier.trust / 100.0)

    fun canMove(state: GameState) = !state.carrier.resting && !state.carrier.forcedRest

    /** Taşıyıcının hücre/dakika cinsinden hızı (biyom hariç). */
    fun speed(state: GameState, report: BalanceReport): Double {
        val c = state.carrier
        var s = 1.6 * (1.0 + 0.08 * c.upgrade(CarrierUpgrade.SPEED))
        s *= report.movementEfficiency.coerceAtLeast(0.15)
        s *= trustFactor(state)
        if (c.energy < 20) s *= 0.5
        if (c.health < 35) s *= 0.6
        if (c.abilityActive(CarrierAbility.QUICK_MARCH)) s *= 1.4
        if (ResearchSystem.has(state, TechId.ROUTE_SCIENCE)) s *= 1.10
        val nav = state.buildings.firstOrNull { it.type == BuildingType.NAVIGATION_TOWER && !it.underConstruction && it.enabled && it.workers > 0 }
        if (nav != null) s *= 1.0 + nav.def.travelSpeedBonus * nav.level
        return s * Bonuses.speed(state)
    }

    fun terrainFactor(state: GameState, biome: Biome): Double {
        if (biome in state.carrier.kind.adapted) return 1.0
        val adapt = 0.15 * state.carrier.upgrade(CarrierUpgrade.TERRAIN)
        return biome.moveFactor + (1.0 - biome.moveFactor) * adapt.coerceAtMost(0.8)
    }

    fun step(state: GameState, dt: Double, report: BalanceReport, offline: Boolean = false) {
        val c = state.carrier
        val minutes = dt / 60.0
        val moving = state.travel.traveling && canMove(state) && !offline
        val biome = state.world.biomeAt(state.travel.position)
        val loadFactor = 0.6 + 0.8 * report.loadRatio.coerceAtMost(2.0)
        val needs = needMultiplier(state)

        // Yetenek süreleri
        for (a in CarrierAbility.entries) {
            c.abilityActiveLeft[a]?.let { c.abilityActiveLeft[a] = (it - dt).coerceAtLeast(0.0) }
            c.abilityCooldown[a]?.let { c.abilityCooldown[a] = (it - dt).coerceAtLeast(0.0) }
        }

        val hungerRate = (if (moving) 5.0 else 2.4) * loadFactor * needs
        var thirstRate = (if (moving) 5.5 else 2.6) * loadFactor * needs
        if ((biome == Biome.DESERT || biome == Biome.VOLCANIC) && biome !in c.kind.adapted) thirstRate *= 1.5
        thirstRate *= c.kind.thirstMult
        c.hunger += hungerRate * minutes
        c.thirst += thirstRate * minutes

        // Dinlenirken bulunduğu araziden kendini besler.
        if (!moving) {
            c.hunger -= 2.6 * biome.forage * c.kind.forageMult * minutes
            c.thirst -= 2.8 * biome.waterAvail * minutes
        }

        val terrainStress = 1.0 + (1.0 - terrainFactor(state, biome)) + (1.0 - report.balance) * biome.roughness
        when {
            moving -> {
                var f = 4.0 * loadFactor * terrainStress * fatigueMultiplier(state)
                if (c.abilityActive(CarrierAbility.QUICK_MARCH)) f *= 1.5
                if (biome == Biome.SNOW && biome !in c.kind.adapted) f *= 1.25
                c.fatigue += f * minutes
                c.energy -= 2.0 * minutes
            }
            c.resting || c.forcedRest || offline -> {
                c.fatigue -= 9.0 * restMultiplier(state) * minutes
                c.energy += 7.0 * restMultiplier(state) * minutes
            }
            else -> {
                c.fatigue -= 2.5 * minutes
                c.energy += 2.0 * minutes
            }
        }

        if (c.autoFeed || offline) autoFeed(state)

        c.hunger = c.hunger.coerceIn(0.0, 100.0)
        c.thirst = c.thirst.coerceIn(0.0, 100.0)
        c.fatigue = c.fatigue.coerceIn(0.0, 100.0)
        c.energy = c.energy.coerceIn(0.0, 100.0)

        // Sağlık
        var dh = 0.0
        if (c.hunger > 85) dh -= 1.2
        if (c.thirst > 85) dh -= 1.2
        if (c.fatigue > 95) dh -= 0.6
        if (dh == 0.0 && c.hunger < 50 && c.thirst < 50) dh = if (c.resting || offline) 1.5 else 0.6
        c.health = (c.health + dh * minutes).coerceIn(HEALTH_FLOOR, maxHealth(state))

        // Güven
        val wellCared = c.hunger < 40 && c.thirst < 40 && c.fatigue < 60
        val neglected = c.hunger > 75 || c.thirst > 75 || c.fatigue > 90
        c.trust = (c.trust + (if (wellCared) 0.5 else if (neglected) -1.0 else 0.0) * minutes).coerceIn(0.0, 100.0)

        // Zorunlu dinlenme: taşıyıcı ölmez ama yürümeyi reddeder.
        if (!c.forcedRest && c.fatigue >= 99.5) {
            c.forcedRest = true
            if (!offline) state.log("${c.name} bitkin düştü ve dinlenmek zorunda! Yolculuk duraklatıldı.", LogKind.CARRIER)
        } else if (c.forcedRest && c.fatigue < 55) {
            c.forcedRest = false
            if (!offline) state.log("${c.name} toparlandı ve yeniden yürümeye hazır.", LogKind.CARRIER)
        }
    }

    /** Ambar varsa eşik üzerindeki açlık ve susuzluğu giderir. */
    fun autoFeed(state: GameState) {
        val c = state.carrier
        if (c.hunger > FEED_THRESHOLD) {
            val points = c.hunger - FEED_THRESHOLD + 10
            val got = state.store.take(FOOD, points * FOOD_PER_POINT)
            c.hunger -= got / FOOD_PER_POINT
        }
        if (c.thirst > FEED_THRESHOLD) {
            val points = c.thirst - FEED_THRESHOLD + 10
            val got = state.store.take(WATER, points * WATER_PER_POINT)
            c.thirst -= got / WATER_PER_POINT
        }
    }

    /** Elle besleme: açlığı ve susuzluğu tamamen giderir. */
    fun feed(state: GameState): ActionResult {
        val c = state.carrier
        if (c.hunger < 1 && c.thirst < 1) return ActionResult.fail("${c.name} zaten tok.")
        val f = state.store.take(FOOD, c.hunger * FOOD_PER_POINT)
        val w = state.store.take(WATER, c.thirst * WATER_PER_POINT)
        if (f <= 0 && w <= 0) return ActionResult.fail("Ambarda yiyecek ve su yok!")
        c.hunger -= f / FOOD_PER_POINT
        c.thirst -= w / WATER_PER_POINT
        c.hunger = c.hunger.coerceAtLeast(0.0)
        c.thirst = c.thirst.coerceAtLeast(0.0)
        c.trust = (c.trust + 2).coerceAtMost(100.0)
        return ActionResult.ok("${c.name} beslendi (${fmt(f)} yiyecek, ${fmt(w)} su).")
    }

    fun setResting(state: GameState, rest: Boolean): ActionResult {
        state.carrier.resting = rest
        return ActionResult.ok(if (rest) "${state.carrier.name} dinleniyor." else "${state.carrier.name} yola hazır.")
    }

    /** Bakım merkezi: açlık/susuzluğu giderir, iyileştirir, güveni artırır. */
    fun applyCare(state: GameState, points: Double) {
        val c = state.carrier
        val adj = state.buildings.firstOrNull { it.type == BuildingType.CARRIER_CARE }
        c.hunger = (c.hunger - points * 0.6).coerceAtLeast(0.0)
        c.thirst = (c.thirst - points * 0.6).coerceAtLeast(0.0)
        c.health = (c.health + points * 0.3).coerceAtMost(maxHealth(state))
        c.fatigue = (c.fatigue - points * 0.2).coerceAtLeast(0.0)
        c.trust = (c.trust + points * 0.08).coerceAtMost(100.0)
        if (adj != null) c.xp += points * 0.2
    }

    fun addXp(state: GameState, xp: Double) {
        val c = state.carrier
        c.xp += xp
        while (c.xp >= xpForNext(c.level)) {
            c.xp -= xpForNext(c.level)
            c.level++
            state.log("${c.name} seviye ${c.level} oldu! Yeni geliştirmeler açılabilir.", LogKind.CARRIER)
            state.cityVersion++
        }
    }

    fun upgradeCost(state: GameState, u: CarrierUpgrade): Amounts {
        val lvl = state.carrier.upgrade(u) + 1
        val f = lvl.toDouble().pow(1.5)
        return when (u) {
            CarrierUpgrade.CAPACITY -> amounts(FOOD to 80 * f, WATER to 60 * f, CARE_ESSENCE to (lvl - 1) * 4)
            CarrierUpgrade.SPEED -> amounts(FOOD to 70 * f, CLOTH to 15 * f, LIGHT_MATERIAL to (lvl - 1) * 5)
            CarrierUpgrade.DURABILITY -> amounts(FOOD to 60 * f, WATER to 60 * f, METAL to 10 * f)
            CarrierUpgrade.EFFICIENCY -> amounts(FOOD to 90 * f, CARE_ESSENCE to (lvl - 1) * 5.0)
            CarrierUpgrade.TERRAIN -> amounts(FOOD to 60 * f, STONE to 40 * f, TOOLS to 8 * f)
            CarrierUpgrade.DEFENSE -> amounts(METAL to 30 * f, STONE to 50 * f, TOOLS to 6 * f)
            CarrierUpgrade.BACK_EXPANSION -> amounts(FOOD to 150 * f, WATER to 120 * f, WOOD to 120 * f, CARE_ESSENCE to (lvl - 1) * 10)
        }.mapValues { it.value.roundToInt().toDouble() }.filterValues { it > 0 }
    }

    /** Geliştirme için gereken taşıyıcı seviyesi. */
    fun requiredLevel(state: GameState, u: CarrierUpgrade): Int {
        val next = state.carrier.upgrade(u) + 1
        return if (u == CarrierUpgrade.BACK_EXPANSION) 1 + next * 2 else next
    }

    fun upgrade(state: GameState, u: CarrierUpgrade): ActionResult {
        val c = state.carrier
        if (c.upgrade(u) >= u.maxLevel) return ActionResult.fail("${u.title} en yüksek seviyede.")
        val req = requiredLevel(state, u)
        if (c.level < req) return ActionResult.fail("Taşıyıcı seviyesi $req olmalı.")
        val cost = upgradeCost(state, u)
        if (!state.store.canAfford(cost)) return ActionResult.fail("Yetersiz kaynak: ${cost.describe()}")
        state.store.spend(cost)
        c.upgrades[u] = c.upgrade(u) + 1
        state.cityVersion++
        state.log("${c.name}: ${u.title} seviye ${c.upgrade(u)}.", LogKind.CARRIER)
        return ActionResult.ok("${u.title} geliştirildi.")
    }

    /** Kadim Taşıyıcı yetenekleri bir seviye erken açar. */
    fun unlockLevel(state: GameState, a: CarrierAbility) = (a.unlockLevel - if (state.carrier.kind == CarrierSpecies.ANCIENT_CARRIER) 1 else 0).coerceAtLeast(1)

    fun activateAbility(state: GameState, a: CarrierAbility): ActionResult {
        val c = state.carrier
        if (c.level < unlockLevel(state, a)) return ActionResult.fail("${a.title} taşıyıcı seviyesi ${unlockLevel(state, a)}'de açılır.")
        if ((c.abilityCooldown[a] ?: 0.0) > 0) return ActionResult.fail("${a.title} henüz hazır değil.")
        if (c.energy < a.energyCost) return ActionResult.fail("Taşıyıcının enerjisi yetersiz.")
        c.energy -= a.energyCost
        c.abilityActiveLeft[a] = a.durationSec
        c.abilityCooldown[a] = a.cooldownSec
        state.cityVersion++
        return ActionResult.ok("${a.title} etkinleştirildi!")
    }

    /** Ekonomi tahmini için taşıyıcının dakikadaki yiyecek/su ihtiyacı (otomatik beslemeyle). */
    fun estimatedFeedPerMinute(state: GameState): Pair<Double, Double> {
        val report = WeightBalanceSystem.compute(state)
        val moving = state.travel.traveling && canMove(state)
        val loadFactor = 0.6 + 0.8 * report.loadRatio.coerceAtMost(2.0)
        val needs = needMultiplier(state)
        val biome = state.world.biomeAt(state.travel.position)
        var h = (if (moving) 5.0 else 2.4) * loadFactor * needs
        var t = (if (moving) 5.5 else 2.6) * loadFactor * needs
        if ((biome == Biome.DESERT || biome == Biome.VOLCANIC) && biome !in state.carrier.kind.adapted) t *= 1.5
        t *= state.carrier.kind.thirstMult
        if (!moving) { h -= 2.6 * biome.forage * state.carrier.kind.forageMult; t -= 2.8 * biome.waterAvail }
        return Pair(h.coerceAtLeast(0.0) * FOOD_PER_POINT, t.coerceAtLeast(0.0) * WATER_PER_POINT)
    }
}
