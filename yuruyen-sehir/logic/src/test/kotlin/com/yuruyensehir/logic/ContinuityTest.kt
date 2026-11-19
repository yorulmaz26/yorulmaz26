package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContinuityTest {

    private fun core(seed: Long = 31L): GameCore {
        val c = GameCore.newGame(seed)
        val s = c.state
        s.buildings += PlacedBuilding(s.nextBuildingId++, BuildingType.MATERIAL_STORAGE, 10, 3)
        s.buildings += PlacedBuilding(s.nextBuildingId++, BuildingType.APARTMENTS, 7, 7)
        ResourceSystem.recalcCapacity(s)
        Resource.entries.forEach { s.store.amounts[it] = s.store.cap(it).coerceAtMost(500.0) }
        s.store.amounts[GOLD] = 2000.0
        s.population.count = 20
        return c
    }

    // ---------------------------------------------------------------- Taşıyıcı türleri

    @Test
    fun speciesHaveRealDifferences() {
        val s = core().state
        val cap = WeightBalanceSystem.capacity(s)
        val speed = CarrierSystem.speed(s, WeightBalanceSystem.compute(s))
        val cells = CityGrid.usableCells(s)
        s.carrier.kind = CarrierSpecies.ROCK_BACK
        assertTrue(WeightBalanceSystem.capacity(s) > cap)
        assertTrue(CarrierSystem.speed(s, WeightBalanceSystem.compute(s)) < speed)
        assertTrue(CityGrid.usableCells(s) > cells)
        s.carrier.kind = CarrierSpecies.LONG_LEGS
        assertTrue(CarrierSystem.speed(s, WeightBalanceSystem.compute(s)) > speed)
        assertTrue(CityGrid.usableCells(s) < cells)
        s.carrier.kind = CarrierSpecies.DESERT_WANDERER
        assertEquals(1.0, CarrierSystem.terrainFactor(s, Biome.DESERT), 1e-9)
        s.carrier.kind = CarrierSpecies.CRYSTAL_HORN
        val d = EventSystem.damageMultiplier(s)
        s.carrier.kind = CarrierSpecies.GIANT_TORTOISE
        assertTrue(d < EventSystem.damageMultiplier(s))
    }

    @Test
    fun carrierSearchMissionDiscoversSpeciesInHabitat() {
        val s = core().state
        val found = CollectionSystem.searchSpecies(s, Biome.SNOW, offline = false)
        assertEquals(CarrierSpecies.ICE_WALKER, found)
        assertTrue(CarrierSpecies.ICE_WALKER in s.collection.species)
        // Kadim taşıyıcı erken aşamada bulunamaz
        assertNull(CollectionSystem.searchSpecies(s, Biome.RUINS, offline = false).takeIf { it == CarrierSpecies.ANCIENT_CARRIER })
    }

    @Test
    fun switchingCarrierKeepsCityAndUpgrades() {
        val s = core().state
        s.carrier.upgrades[CarrierUpgrade.SPEED] = 2
        s.collection.species += CarrierSpecies.ROCK_BACK
        val buildings = s.buildings.map { it.copy() }
        assertTrue(CollectionSystem.switchCarrier(s, CarrierSpecies.ROCK_BACK).success)
        assertEquals(CarrierSpecies.ROCK_BACK, s.carrier.kind)
        assertEquals(buildings, s.buildings)
        assertEquals(2, s.carrier.upgrade(CarrierUpgrade.SPEED))
    }

    @Test
    fun switchingBlockedWhenBuildingsDoNotFit() {
        val s = core().state
        s.collection.species += CarrierSpecies.LONG_LEGS
        // Kenara bina koy: dar sırta sığmaz
        val edge = (0 until CityGrid.GRID_W).firstNotNullOf { x ->
            (0 until CityGrid.GRID_H).firstOrNull { y -> CityGrid.usable(s, x, y) && s.buildingAt(x, y) == null }?.let { x to it }
        }
        s.buildings += PlacedBuilding(s.nextBuildingId++, BuildingType.REST_AREA, edge.first, edge.second)
        assertTrue(CollectionSystem.misfits(s, CarrierSpecies.LONG_LEGS).isNotEmpty())
        val r = CollectionSystem.switchCarrier(s, CarrierSpecies.LONG_LEGS)
        assertFalse(r.success)
        assertTrue("sığmayan" in r.message)
        assertEquals(CarrierSpecies.GIANT_TORTOISE, s.carrier.kind)
    }

    // ---------------------------------------------------------------- Yan şehirler

    @Test
    fun sideCityRequiresStageAndSpecies() {
        val s = core().state
        assertNotNull(FleetSystem.foundReason(s, CarrierSpecies.ROCK_BACK))
        s.stage = 3
        assertNotNull(FleetSystem.foundReason(s, CarrierSpecies.ROCK_BACK)) // keşfedilmedi
        s.collection.species += CarrierSpecies.ROCK_BACK
        assertNull(FleetSystem.foundReason(s, CarrierSpecies.ROCK_BACK))
        assertNotNull(FleetSystem.foundReason(s, CarrierSpecies.GIANT_TORTOISE)) // ana şehirde kullanılıyor
    }

    @Test
    fun sideCityProducesAndSendsToMainCity() {
        val c = core(); val s = c.state
        s.stage = 3
        s.collection.species += CarrierSpecies.ROCK_BACK
        val pop = s.population.count
        assertTrue(FleetSystem.found(s, CarrierSpecies.ROCK_BACK, SideSpecialty.MINING).success)
        assertEquals(pop - 6, s.population.count)
        s.store.amounts[STONE] = 0.0; s.store.amounts[METAL] = 0.0
        repeat(240) { c.step(0.25) }
        assertTrue(s.store[STONE] > 0 && s.store[METAL] > 0, "konvoy ana şehre taş/metal getirmeli")
        assertTrue(s.stats.convoyGoods > 0)
    }

    @Test
    fun fleetMovesAtSlowestSpeed() {
        val s = core().state
        val sp = CarrierSystem.speed(s, WeightBalanceSystem.compute(s))
        s.stage = 3
        s.collection.species += CarrierSpecies.ROCK_BACK
        FleetSystem.found(s, CarrierSpecies.ROCK_BACK, SideSpecialty.FARMING)
        assertTrue(FleetSystem.fleetSpeedFactor(s) < 1.0)
        assertTrue(CarrierSystem.speed(s, WeightBalanceSystem.compute(s)) < sp)
    }

    @Test
    fun sideCityWorkersUpgradeAndGuards() {
        val s = core().state
        s.stage = 4
        s.collection.species += CarrierSpecies.LONG_LEGS
        FleetSystem.found(s, CarrierSpecies.LONG_LEGS, SideSpecialty.GUARD)
        val city = s.sideCities.first()
        assertTrue(FleetSystem.moveWorkers(s, city.id, 4).success)
        assertEquals(10, city.workers)
        assertFalse(FleetSystem.moveWorkers(s, city.id, 1).success) // kapasite 10
        FleetSystem.upgradeCost(city).forEach { (r, v) -> s.store.amounts[r] = v }
        assertTrue(FleetSystem.upgrade(s, city.id).success)
        assertEquals(14, city.capacity)
        assertEquals(5, FleetSystem.guards(s))
        CombatSystem.start(s, "x", listOf(EnemyType.BANDIT to 1))
        assertTrue(s.battle!!.guards.size >= 5)
    }

    // ---------------------------------------------------------------- Uzmanlaşma

    @Test
    fun specializationSlotsAndEffects() {
        val s = core().state
        assertFalse(SpecializationSystem.choose(s, Specialization.NATURE).success)
        s.stage = 2
        BuildingSystem.place(s, BuildingType.FARM, 11, 5, instant = true)
        val farm = s.buildings.last()
        val e0 = ProductionSystem.efficiency(s, farm)
        assertTrue(SpecializationSystem.choose(s, Specialization.NATURE).success)
        assertEquals(e0 * 1.15, ProductionSystem.efficiency(s, farm), 1e-6)
        assertFalse(SpecializationSystem.choose(s, Specialization.MILITARY).success) // tek yuva
        s.stage = 4
        assertTrue(SpecializationSystem.choose(s, Specialization.MILITARY).success) // hibrit
        assertEquals(2, s.specializations.size)
    }

    // ---------------------------------------------------------------- Hedefler

    @Test
    fun dailyGoalsGenerateTrackAndReward() {
        val c = core(); val s = c.state
        c.step(0.25)
        val daily = s.goals.filter { !it.weekly }
        assertEquals(3, daily.size)
        assertEquals(2, s.goals.count { it.weekly })
        val idx = s.goals.indexOfFirst { !it.weekly && it.kind == GoalKind.BUILD }.takeIf { it >= 0 }
            ?: run { s.goals.add(0, Goal(GoalKind.BUILD, 1.0, GoalSystem.stat(s, GoalKind.BUILD), amounts(GOLD to 30), false)); 0 }
        assertFalse(GoalSystem.claim(s, idx).success)
        BuildingSystem.place(s, BuildingType.SMALL_HOUSE, 11, 7, instant = true)
        s.stats.buildingsBuilt += 10
        val gold = s.store[GOLD]
        assertTrue(GoalSystem.claim(s, idx).success)
        assertTrue(s.store[GOLD] > gold)
        assertFalse(GoalSystem.claim(s, idx).success)
    }

    @Test
    fun goalsRefreshEachDayAndQuestCenterAddsOne() {
        val c = core(); val s = c.state
        c.step(0.25)
        s.stage = 1
        assertTrue(BuildingSystem.place(s, BuildingType.QUEST_CENTER, 11, 5, instant = true).success)
        s.gameSeconds += GameState.SECONDS_PER_DAY
        c.step(0.25)
        assertEquals(4, s.goals.count { !it.weekly })
    }

    // ---------------------------------------------------------------- Başarım, koleksiyon, ustalık

    @Test
    fun achievementsUnlockOnceWithReward() {
        val c = core(); val s = c.state
        s.stats.buildingsBuilt = 5
        val gold = s.store[GOLD]
        AchievementSystem.check(s, false)
        assertTrue(Achievement.FIRST_STEPS.name in s.achievements)
        assertTrue(s.store[GOLD] > gold)
        val g2 = s.store[GOLD]
        AchievementSystem.check(s, false)
        assertEquals(g2, s.store[GOLD], 1e-9)
    }

    @Test
    fun artifactsAreUniqueAndGiveBonuses() {
        val s = core().state
        val r0 = TravelSystem.revealRadius(s)
        repeat(30) { CollectionSystem.tryFindArtifact(s, 1.0) }
        assertEquals(Artifact.entries.size, s.collection.artifacts.size)
        assertNull(CollectionSystem.tryFindArtifact(s, 1.0))
        assertEquals(r0 + 1, TravelSystem.revealRadius(s))
    }

    @Test
    fun masteryIsEndlessAfterAllResearch() {
        val s = core().state
        assertFalse(MasterySystem.start(s).success)
        s.research.completed += TechId.entries
        assertTrue(MasterySystem.start(s).success)
        ResearchSystem.addPoints(s, MasterySystem.cost(0) + MasterySystem.cost(1) + 1)
        assertEquals(2, s.research.masteryLevel)
        BuildingSystem.place(s, BuildingType.FARM, 11, 5, instant = true)
        assertTrue(Bonuses.production(s, BuildingType.FARM) > 1.03)
    }

    @Test
    fun tradeOpportunityBoostsPrice() {
        val s = core().state
        val m = s.civs.first { it.culture == Culture.MERCHANT_GUILD }
        s.travel.position = m.position; CivSystem.meetCheck(s, false)
        val p0 = TradeSystem.quote(s, m, WOOD).sell
        m.boostResource = WOOD; m.boostSeconds = 100.0
        assertTrue(TradeSystem.quote(s, m, WOOD).sell > p0 * 1.5)
    }

    @Test
    fun airshipStationExtendsExpeditions() {
        val s = core().state
        s.stage = 4
        BuildingSystem.place(s, BuildingType.EXPLORATION_CENTER, 11, 5, instant = true)
        val d0 = ExplorationSystem.duration(s, Cell(10, 0))
        s.research.completed += TechId.EXPEDITION_GEAR
        s.store.amounts[LIGHT_MATERIAL] = 50.0
        val spot = (0..CityGrid.GRID_W - 2).flatMap { x -> (0..CityGrid.GRID_H - 2).map { x to it } }
            .first { (x, y) -> BuildingSystem.canPlace(s, BuildingType.AIRSHIP_STATION, x, y) == null }
        assertTrue(BuildingSystem.place(s, BuildingType.AIRSHIP_STATION, spot.first, spot.second, instant = true).success)
        PopulationSystem.autoAssign(s)
        assertEquals(18, ExplorationSystem.maxRange(s))
        assertTrue(ExplorationSystem.duration(s, Cell(10, 0)) < d0 * 0.6)
    }

    @Test
    fun fullStateSurvivesSave() {
        val s = core().state
        s.stage = 3
        s.collection.species += CarrierSpecies.ROCK_BACK
        s.collection.artifacts += Artifact.OLD_MAP
        FleetSystem.found(s, CarrierSpecies.ROCK_BACK, SideSpecialty.RESEARCH)
        SpecializationSystem.choose(s, Specialization.ECONOMY)
        GoalSystem.refresh(s)
        s.achievements["FIRST_STEPS"] = 2
        s.research.masteryLevel = 3
        val st = MemoryStorage(); SaveManager(st).save(s, 1L)
        val l = SaveManager(st).load()!!
        assertEquals(s.collection, l.collection)
        assertEquals(s.sideCities, l.sideCities)
        assertEquals(s.specializations, l.specializations)
        assertEquals(s.goals, l.goals)
        assertEquals(s.achievements, l.achievements)
        assertEquals(3, l.research.masteryLevel)
        assertEquals(s.carrier.kind, l.carrier.kind)
    }

    @Test
    fun longRunWithFleetStaysStable() {
        val c = core(77); val s = c.state
        s.stage = 3
        s.collection.species += CarrierSpecies.FOREST_GUARDIAN
        FleetSystem.found(s, CarrierSpecies.FOREST_GUARDIAN, SideSpecialty.FARMING)
        s.lastSavedEpochMs = 1L
        c.applyOffline(1L + 8 * 3600_000L)
        repeat(2400) { c.step(0.25); s.pendingEvent?.let { EventSystem.resolve(s, it.options.lastIndex) } }
        Resource.entries.forEach { r -> assertTrue(s.store[r] >= 0 && !s.store[r].isNaN(), "$r") }
        assertTrue(s.population.count >= PopulationSystem.MIN_POPULATION)
        assertTrue(s.sideCities.first().workers >= 2)
    }
}
