package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameLogicTest {

    private fun newCore(seed: Long = 42L) = GameCore.newGame(seed)

    private fun richCore(seed: Long = 42L): GameCore {
        val core = newCore(seed)
        val s = core.state
        // Testler için bol kapasite ve kaynak
        s.buildings += PlacedBuilding(s.nextBuildingId++, BuildingType.MATERIAL_STORAGE, 10, 3)
        ResourceSystem.recalcCapacity(s)
        Resource.entries.forEach { s.store.amounts[it] = s.store.cap(it).coerceAtMost(1000.0) }
        s.store.amounts[GOLD] = 1000.0
        return core
    }

    private fun runMinutes(core: GameCore, minutes: Double) {
        var t = minutes * 60
        while (t > 0) { core.step(GameCore.STEP); t -= GameCore.STEP }
    }

    @Test
    fun newGameHasStartingSettlement() {
        val s = newCore().state
        assertEquals(6, s.population.count)
        assertEquals(1, s.count(BuildingType.CITY_CENTER))
        assertEquals(1, s.count(BuildingType.FOOD_STORAGE))
        assertEquals(1, s.count(BuildingType.WATER_STORAGE))
        assertTrue(s.store[FOOD] > 0 && s.store[WATER] > 0)
        assertTrue(CityGrid.usableCells(s) > 50)
    }

    @Test
    fun atLeastTwentyFiveBuildableBuildingTypes() {
        assertTrue(BuildingCatalog.all.count { it.buildable } >= 25)
        BuildingCatalog.all.forEach { d ->
            assertTrue(d.weight > 0, "${d.title} ağırlığı olmalı")
            assertTrue(d.w in 1..2 && d.h in 1..2)
        }
    }

    @Test
    fun placementRejectsOverlapAndOutOfArea() {
        val s = newCore().state
        assertEquals(PlaceError.OVERLAP, BuildingSystem.canPlace(s, BuildingType.FARM, 8, 5))
        assertEquals(PlaceError.OUT_OF_AREA, BuildingSystem.canPlace(s, BuildingType.FARM, 0, 0))
        assertNull(BuildingSystem.canPlace(s, BuildingType.FARM, 10, 5))
    }

    @Test
    fun placingSpendsResourcesAndConstructs() {
        val core = newCore()
        val s = core.state
        val woodBefore = s.store[WOOD]
        val r = BuildingSystem.place(s, BuildingType.FARM, 10, 5)
        assertTrue(r.success, r.message)
        assertEquals(woodBefore - 30, s.store[WOOD], 0.001)
        val farm = s.buildings.last()
        assertTrue(farm.underConstruction)
        runMinutes(core, 1.0)
        assertFalse(farm.underConstruction)
        assertEquals(2, farm.workers, "işçiler otomatik atanmalı")
    }

    @Test
    fun lockedBuildingsCannotBePlaced() {
        val s = newCore().state
        assertEquals(PlaceError.LOCKED, BuildingSystem.canPlace(s, BuildingType.GREENHOUSE, 10, 5))
        s.research.completed += TechId.IRRIGATION
        assertNull(BuildingSystem.canPlace(s, BuildingType.GREENHOUSE, 10, 5))
    }

    @Test
    fun uniqueBuildingsLimited() {
        val s = richCore().state
        s.stage = 3
        assertTrue(BuildingSystem.place(s, BuildingType.CARRIER_CARE, 10, 5, instant = true).success)
        assertEquals(PlaceError.UNIQUE, BuildingSystem.canPlace(s, BuildingType.CARRIER_CARE, 10, 7))
    }

    @Test
    fun weightIncreasesAndBalanceShiftsWithPlacement() {
        val s = newCore().state
        val before = WeightBalanceSystem.compute(s)
        assertTrue(before.totalWeight > 0)
        assertTrue(before.frontShare < 0.5, "depolar arkada, ağırlık arkaya kaymalı")
        // Öne ağır bir bina koymak dengeyi düzeltmeli
        val preview = PlacedBuilding(-1, BuildingType.METALWORKS, 11, 5)
        val after = WeightBalanceSystem.compute(s, extra = preview)
        assertTrue(after.totalWeight > before.totalWeight)
        assertTrue(after.frontShare > before.frontShare)
    }

    @Test
    fun heavyOneSidedCityHasPoorBalanceAndEfficiency() {
        val s = richCore().state
        s.buildings.removeAll { it.type != BuildingType.CITY_CENTER }
        listOf(3 to 4, 3 to 6, 4 to 3, 4 to 7, 5 to 3).forEach { (x, y) ->
            s.buildings += PlacedBuilding(s.nextBuildingId++, BuildingType.WATER_STORAGE, x, y)
        }
        ResourceSystem.recalcCapacity(s)
        s.store.amounts[WATER] = s.store.cap(WATER)
        val lopsided = WeightBalanceSystem.compute(s)
        assertTrue(lopsided.balance < 0.8, "balance=${lopsided.balance}")
        assertTrue(lopsided.frontShare < 0.45)
    }

    @Test
    fun overloadReducesMovementEfficiency() {
        val s = newCore().state
        val normal = WeightBalanceSystem.compute(s)
        s.carrier.upgrades[CarrierUpgrade.CAPACITY] = 0
        repeat(12) { i -> s.buildings += PlacedBuilding(s.nextBuildingId++, BuildingType.APARTMENTS, 100 + i * 3, 100) }
        // Ağırlık merkezi bozulmasın diye dışarıdaki binaları simetrik değil ama yük testi için yeterli
        val heavy = WeightBalanceSystem.compute(s)
        assertTrue(heavy.loadRatio > 1.0)
        assertTrue(heavy.movementEfficiency < normal.movementEfficiency)
    }

    @Test
    fun supportFrameReducesWeight() {
        val s = richCore().state
        val house = PlacedBuilding(s.nextBuildingId++, BuildingType.APARTMENTS, 10, 5)
        s.buildings += house
        val w1 = WeightBalanceSystem.compute(s).buildingWeight
        s.buildings += PlacedBuilding(s.nextBuildingId++, BuildingType.SUPPORT_FRAME, 12, 5)
        val w2 = WeightBalanceSystem.compute(s).buildingWeight
        // Destek iskelesinin kendi ağırlığı (8) eklenir, apartman %20 hafifler (60*0.2=12)
        assertTrue(w2 < w1 + 8.0, "w1=$w1 w2=$w2")
    }

    @Test
    fun lightweightResearchReducesWeight() {
        val s = newCore().state
        val w1 = WeightBalanceSystem.compute(s).buildingWeight
        s.research.completed += TechId.LIGHT_TIMBER
        val w2 = WeightBalanceSystem.compute(s).buildingWeight
        assertEquals(w1 * 0.9, w2, 0.001)
    }

    @Test
    fun farmProducesFoodAndConsumesWater() {
        val core = newCore()
        val s = core.state
        BuildingSystem.place(s, BuildingType.FARM, 10, 5, instant = true)
        val farm = s.buildings.last()
        assertEquals(2, farm.workers)
        s.population.count = 6
        val food0 = s.store[FOOD]
        val water0 = s.store[WATER]
        ProductionSystem.step(s, 60.0)
        assertTrue(s.store[FOOD] > food0)
        assertTrue(s.store[WATER] < water0)
    }

    @Test
    fun productionScalesDownWhenInputMissing() {
        val s = newCore().state
        BuildingSystem.place(s, BuildingType.FARM, 10, 5, instant = true)
        val farm = s.buildings.last()
        s.store.amounts[WATER] = 0.5
        ProductionSystem.step(s, 60.0)
        val st = s.buildingStatus[farm.id]
        assertNotNull(st)
        assertTrue(st.inputRatio < 1.0)
        assertEquals(WATER, st.missingInput)
        assertTrue(s.store[WATER] >= 0.0)
    }

    @Test
    fun adjacencyBonusApplies() {
        val s = newCore().state
        BuildingSystem.place(s, BuildingType.FARM, 10, 5, instant = true)
        val farm = s.buildings.last()
        val e1 = ProductionSystem.efficiency(s, farm)
        // Su deposu çiftliğin hemen yanında
        s.buildings += PlacedBuilding(s.nextBuildingId++, BuildingType.WATER_STORAGE, 12, 5)
        val e2 = ProductionSystem.efficiency(s, farm)
        assertEquals(e1 * 1.2, e2, 1e-6)
    }

    @Test
    fun productionChainWorkshopMakesTools() {
        val s = richCore().state
        BuildingSystem.place(s, BuildingType.WORKSHOP, 10, 5, instant = true)
        val tools0 = s.store[TOOLS]
        s.store.amounts[TOOLS] = 0.0
        ProductionSystem.step(s, 60.0)
        assertTrue(s.store[TOOLS] > 0.0, "tools=${s.store[TOOLS]} before=$tools0")
    }

    @Test
    fun populationGrowsWithHousingAndFood() {
        val core = richCore()
        val s = core.state
        BuildingSystem.place(s, BuildingType.SMALL_HOUSE, 10, 7, instant = true)
        val pop0 = s.population.count
        s.population.happiness = 80.0
        runMinutes(core, 3.0)
        assertTrue(s.population.count > pop0)
        assertTrue(s.population.count <= PopulationSystem.housing(s))
    }

    @Test
    fun starvationNeverWipesOutPopulation() {
        val core = newCore()
        val s = core.state
        s.store.amounts[FOOD] = 0.0
        s.store.amounts[WATER] = 0.0
        runMinutes(core, 30.0)
        assertTrue(s.population.count >= PopulationSystem.MIN_POPULATION)
        assertTrue(s.carrier.health >= CarrierSystem.HEALTH_FLOOR)
    }

    @Test
    fun carrierHungerRisesAndFeedingFixesIt() {
        val core = newCore()
        val s = core.state
        s.carrier.autoFeed = false
        s.travel.position = Cell(1000, 1000) // her yer için deterministik ama beslenme azaltmak adına konum önemsiz
        val report = WeightBalanceSystem.compute(s)
        repeat(240) { CarrierSystem.step(s, 1.0, report) }
        val h = s.carrier.hunger
        val r = CarrierSystem.feed(s)
        assertTrue(r.success)
        assertTrue(s.carrier.hunger < h)
    }

    @Test
    fun carrierForcedRestOnExhaustionAndRecovers() {
        val core = newCore()
        val s = core.state
        s.carrier.fatigue = 100.0
        CarrierSystem.step(s, 1.0, WeightBalanceSystem.compute(s))
        assertTrue(s.carrier.forcedRest)
        assertFalse(CarrierSystem.canMove(s))
        runMinutes(core, 10.0)
        assertFalse(s.carrier.forcedRest)
    }

    @Test
    fun carrierUpgradeSpendsAndRaisesCapacity() {
        val s = richCore().state
        val cap1 = WeightBalanceSystem.capacity(s)
        val r = CarrierSystem.upgrade(s, CarrierUpgrade.CAPACITY)
        assertTrue(r.success, r.message)
        assertEquals(cap1 + 120, WeightBalanceSystem.capacity(s), 0.001)
    }

    @Test
    fun backExpansionGrowsBuildArea() {
        val s = richCore().state
        val cells = CityGrid.usableCells(s)
        s.carrier.level = 5
        assertTrue(CarrierSystem.upgrade(s, CarrierUpgrade.BACK_EXPANSION).success)
        assertTrue(CityGrid.usableCells(s) > cells)
    }

    @Test
    fun carrierLevelsUpWithXp() {
        val s = newCore().state
        CarrierSystem.addXp(s, 150.0)
        assertEquals(2, s.carrier.level)
    }

    @Test
    fun worldIsDeterministicAndVaried() {
        val a = WorldGenerator(7)
        val b = WorldGenerator(7)
        val seen = HashSet<Biome>()
        for (x in -60..60 step 3) for (y in -60..60 step 3) {
            assertEquals(a.biomeAt(x, y), b.biomeAt(x, y))
            seen += a.biomeAt(x, y)
        }
        assertEquals(Biome.PLAINS, a.biomeAt(0, 0))
        assertTrue(seen.size >= 9, "biyom çeşitliliği: $seen")
    }

    @Test
    fun routesAvoidDangerWhenSafe() {
        val core = newCore(3)
        val s = core.state
        // Geniş bir alanı keşfedilmiş say
        for (x in -15..15) for (y in -15..15) s.travel.explored += Cell(x, y).key()
        val target = (-12..12).flatMap { x -> (-12..12).map { Cell(x, it) } }
            .filter { it.dist(Cell(0, 0)) in 8..12 }
            .first { s.world.biomeAt(it).danger < 0.3 }
        val routes = TravelSystem.planRoutes(s, target)
        assertTrue(routes.isNotEmpty())
        routes.forEach { assertEquals(target, it.cells.last()) }
        if (routes.size == 2) {
            assertTrue(routes[1].avgDanger <= routes[0].avgDanger + 1e-9)
            assertTrue(routes[0].estMinutes <= routes[1].estMinutes + 1e-9)
        }
    }

    @Test
    fun travelMovesCityAndRevealsMap() {
        val core = richCore()
        val s = core.state
        val target = Cell(2, 0)
        assertNull(TravelSystem.canTarget(s, target))
        val route = TravelSystem.planRoutes(s, target).first()
        assertTrue(TravelSystem.start(s, route).success)
        val explored0 = s.travel.explored.size
        var guard = 0
        while (s.travel.traveling && guard++ < 20000) {
            core.step(GameCore.STEP)
            if (s.pendingEvent != null) EventSystem.resolve(s, s.pendingEvent!!.options.lastIndex)
        }
        assertEquals(target, s.travel.position)
        assertTrue(s.travel.explored.size > explored0)
        assertTrue(s.travel.distanceTraveled >= 2)
    }

    @Test
    fun travelRequiresSupplies() {
        val s = newCore().state
        s.store.amounts[FOOD] = 0.0
        val route = TravelSystem.planRoutes(s, Cell(2, 1)).first()
        assertFalse(TravelSystem.start(s, route).success)
    }

    @Test
    fun eventDamageIsFairAndNeverDestroys() {
        val s = newCore().state
        repeat(50) { EventSystem.damageBuildings(s, 3, 50.0) }
        assertEquals(3, s.buildings.size)
        assertTrue(s.buildings.all { it.hp >= 20.0 })
        val b = s.buildings.first()
        s.store.amounts[WOOD] = 500.0; s.store.amounts[STONE] = 500.0
        assertTrue(BuildingSystem.repair(s, b.id).success)
        assertEquals(100.0, b.hp, 0.001)
    }

    @Test
    fun eventResolutionAppliesCostsAndRewards() {
        val s = richCore().state
        s.pendingEvent = PendingEvent("caravan", "t", "t", listOf(EventOption("Su al", cost = amounts(GOLD to 30), reward = amounts(WATER to 10))))
        s.store.amounts[WATER] = 0.0
        val gold = s.store[GOLD]
        assertTrue(EventSystem.resolve(s, 0).success)
        assertEquals(gold - 30, s.store[GOLD], 0.001)
        assertEquals(10.0, s.store[WATER], 0.001)
        assertNull(s.pendingEvent)
    }

    @Test
    fun researchCompletesAndUnlocks() {
        val core = richCore()
        val s = core.state
        assertTrue(BuildingSystem.place(s, BuildingType.RESEARCH_CENTER, 12, 7, instant = true).success)
        assertTrue(ResearchSystem.start(s, TechId.IRRIGATION).success)
        var guard = 0
        while (!ResearchSystem.has(s, TechId.IRRIGATION) && guard++ < 20000) {
            s.store.amounts[ENERGY] = 50.0
            core.step(1.0)
        }
        assertTrue(ResearchSystem.has(s, TechId.IRRIGATION))
        assertTrue(BuildingSystem.unlocked(s, BuildingType.GREENHOUSE.def))
    }

    @Test
    fun researchPrerequisitesEnforced() {
        val s = richCore().state
        assertFalse(ResearchSystem.start(s, TechId.COMPOSITE_ROOFS).success)
    }

    @Test
    fun stageAdvancesWithDevelopment() {
        val s = newCore().state
        assertEquals(0, s.stage)
        s.population.count = 30
        StageSystem.check(s)
        assertTrue(s.stage >= 1)
    }

    @Test
    fun upgradeAndDemolish() {
        val core = richCore()
        val s = core.state
        BuildingSystem.place(s, BuildingType.SMALL_HOUSE, 10, 7, instant = true)
        val house = s.buildings.last()
        val h1 = PopulationSystem.housing(s)
        assertTrue(BuildingSystem.upgrade(s, house.id).success)
        runMinutes(core, 2.0)
        assertEquals(2, house.level)
        assertTrue(PopulationSystem.housing(s) > h1)
        assertTrue(BuildingSystem.demolish(s, house.id).success)
        assertNull(s.building(house.id))
        assertFalse(BuildingSystem.demolish(s, s.buildings.first { it.type == BuildingType.CITY_CENTER }.id).success)
    }

    @Test
    fun moveBuildingChangesBalance() {
        val s = richCore().state
        val store = s.buildings.first { it.type == BuildingType.WATER_STORAGE }
        val before = WeightBalanceSystem.compute(s).frontShare
        assertTrue(BuildingSystem.move(s, store.id, 11, 6).success)
        assertTrue(WeightBalanceSystem.compute(s).frontShare > before)
    }

    @Test
    fun saveRoundTripPreservesEverything() {
        val core = richCore()
        val s = core.state
        BuildingSystem.place(s, BuildingType.FARM, 10, 5)
        s.research.completed += TechId.LIGHT_TIMBER
        s.carrier.upgrades[CarrierUpgrade.SPEED] = 2
        s.travel.depleted[Cell(1, -2).key()] = 33.0
        s.log("Türkçe karakter testi: ğüşıöçİ \"tırnak\"", LogKind.INFO)
        val storage = MemoryStorage()
        val mgr = SaveManager(storage)
        mgr.save(s, 1_000_000L)
        val loaded = mgr.load()
        assertNotNull(loaded)
        assertEquals("main", mgr.lastLoadSource)
        assertEquals(s.seed, loaded.seed)
        assertEquals(s.buildings, loaded.buildings)
        assertEquals(s.store.amounts, loaded.store.amounts)
        assertEquals(s.research.completed, loaded.research.completed)
        assertEquals(s.carrier, loaded.carrier)
        assertEquals(s.travel.explored, loaded.travel.explored)
        assertEquals(s.travel.depleted, loaded.travel.depleted)
        assertEquals(s.log.last(), loaded.log.last())
        assertEquals(1_000_000L, loaded.lastSavedEpochMs)
        // Yüklenen durum çalışmaya devam etmeli
        val core2 = GameCore(loaded)
        core2.step(1.0)
    }

    @Test
    fun corruptMainSaveFallsBackToBackup() {
        val storage = MemoryStorage()
        val mgr = SaveManager(storage)
        val s = newCore().state
        mgr.save(s, 1L)
        s.population.count = 9
        mgr.save(s, 2L)
        storage.files[SaveManager.MAIN] = storage.files[SaveManager.MAIN]!!.replace("\"count\":9", "\"count\":99")
        val loaded = mgr.load()
        assertNotNull(loaded)
        assertEquals("backup", mgr.lastLoadSource)
        assertEquals(6, loaded.population.count)
    }

    @Test
    fun garbageSaveReturnsNull() {
        val storage = MemoryStorage()
        storage.files[SaveManager.MAIN] = "{bozuk"
        assertNull(SaveManager(storage).load())
    }

    @Test
    fun offlineProgressIsCappedAndSafe() {
        val core = newCore()
        val s = core.state
        BuildingSystem.place(s, BuildingType.WATER_COLLECTOR, 10, 5, instant = true)
        s.lastSavedEpochMs = 1_000L
        val report = core.applyOffline(1_000L + 48L * 3600 * 1000)
        assertNotNull(report)
        assertEquals(GameCore.MAX_OFFLINE_SECONDS, report.seconds, 0.001)
        assertTrue(s.population.count >= PopulationSystem.MIN_POPULATION)
        assertTrue(s.carrier.health >= CarrierSystem.HEALTH_FLOOR)
        assertEquals(3 + 1, s.buildings.size)
    }

    @Test
    fun offlineIgnoresClockGoingBackwards() {
        val core = newCore()
        core.state.lastSavedEpochMs = 10_000_000L
        assertNull(core.applyOffline(5_000L))
    }

    @Test
    fun economyEstimateIncludesPopulationNeeds() {
        val s = newCore().state
        val flow = EconomySystem.estimate(s)
        assertTrue((flow.consumed[FOOD] ?: 0.0) >= 6 * PopulationSystem.FOOD_PER_POP)
    }

    @Test
    fun longSimulationIsStable() {
        val core = richCore(99)
        val s = core.state
        BuildingSystem.place(s, BuildingType.FARM, 10, 5, instant = true)
        BuildingSystem.place(s, BuildingType.WATER_COLLECTOR, 10, 7, instant = true)
        BuildingSystem.place(s, BuildingType.SMALL_HOUSE, 11, 7, instant = true)
        runMinutes(core, 60.0)
        Resource.entries.forEach { r ->
            assertTrue(s.store[r] >= 0.0 && !s.store[r].isNaN(), "$r=${s.store[r]}")
            if (r != GOLD) assertTrue(s.store[r] <= s.store.cap(r) + 1e-6, "$r kapasiteyi aşmamalı")
        }
        assertTrue(s.population.happiness in 0.0..100.0)
    }
}
