package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CombatExplorationTest {

    private fun core(seed: Long = 21L): GameCore {
        val c = GameCore.newGame(seed)
        val s = c.state
        s.stage = 3
        s.buildings += PlacedBuilding(s.nextBuildingId++, BuildingType.MATERIAL_STORAGE, 10, 3)
        s.buildings += PlacedBuilding(s.nextBuildingId++, BuildingType.LARGE_HOUSE, 7, 8)
        s.population.count = 14
        ResourceSystem.recalcCapacity(s)
        Resource.entries.forEach { s.store.amounts[it] = s.store.cap(it).coerceAtMost(500.0) }
        s.store.amounts[GOLD] = 1000.0
        return c
    }

    private fun runBattle(c: GameCore, maxSeconds: Double = 400.0): Boolean {
        var t = 0.0
        while (c.state.battle != null && t < maxSeconds) { c.step(0.25); t += 0.25 }
        return c.state.battle == null
    }

    private fun place(s: GameState, t: BuildingType, x: Int, y: Int) {
        val r = BuildingSystem.place(s, t, x, y, instant = true)
        assertTrue(r.success, "${t}: ${r.message}")
    }

    @Test
    fun atLeastThirtyBuildableBuildings() {
        assertTrue(BuildingCatalog.all.count { it.buildable } >= 30, "${BuildingCatalog.all.count { it.buildable }}")
    }

    @Test
    fun battleStartsPausesTravelAndEndsInVictoryWithTowers() {
        val c = core(); val s = c.state
        place(s, BuildingType.DEFENSE_TOWER, 11, 5); place(s, BuildingType.DEFENSE_TOWER, 6, 7)
        place(s, BuildingType.DEFENSE_TOWER, 9, 8); place(s, BuildingType.DEFENSE_TOWER, 9, 3)
        val route = TravelSystem.planRoutes(s, Cell(2, 0)).first()
        assertTrue(TravelSystem.start(s, route).success)
        assertTrue(CombatSystem.start(s, "Test", listOf(EnemyType.BANDIT to 3)).success)
        val pos = s.travel.position
        repeat(20) { c.step(0.25) }
        assertEquals(pos, s.travel.position)
        assertEquals(0.0, s.travel.progress, 1e-9, "savaşta yolculuk durmalı")
        assertTrue(runBattle(c))
        assertEquals(1, s.stats.battlesWon)
        assertTrue(s.buildings.all { it.hp >= 20.0 })
    }

    @Test
    fun undefendedCityIsNeverDestroyedAndEnemiesRetreat() {
        val c = core(); val s = c.state
        val n = s.buildings.size
        CombatSystem.start(s, "Baskın", listOf(EnemyType.RAIDER to 6))
        assertTrue(runBattle(c, 500.0))
        assertEquals(n, s.buildings.size)
        assertTrue(s.buildings.all { it.hp >= 20.0 })
        assertEquals(1, s.stats.battlesLost)
        assertTrue(s.store[GOLD] > 0)
    }

    @Test
    fun towersBeatNoTowersOnDamage() {
        fun totalHp(withTowers: Boolean): Double {
            val c = core(5); val s = c.state
            if (withTowers) { place(s, BuildingType.DEFENSE_TOWER, 11, 5); place(s, BuildingType.DEFENSE_TOWER, 6, 7); place(s, BuildingType.DEFENSE_TOWER, 9, 8) }
            CombatSystem.start(s, "x", listOf(EnemyType.BEAST to 4))
            runBattle(c)
            return s.buildings.filter { it.def.towerDps == 0.0 }.sumOf { it.hp }
        }
        assertTrue(totalHp(true) > totalHp(false))
    }

    @Test
    fun watchtowerExtendsRangeAndPrepTime() {
        val s = core().state
        place(s, BuildingType.DEFENSE_TOWER, 11, 5)
        val tower = s.buildings.last()
        val r0 = CombatSystem.towerRange(s, tower); val p0 = CombatSystem.prepTime(s)
        place(s, BuildingType.WATCHTOWER, 12, 6)
        assertEquals(r0 + 1.5, CombatSystem.towerRange(s, tower), 1e-9)
        assertTrue(CombatSystem.prepTime(s) > p0)
    }

    @Test
    fun energyTowerNeedsEnergy() {
        val c = core(); val s = c.state
        s.research.completed += TechId.EFFICIENT_BOILERS
        place(s, BuildingType.ENERGY_TOWER, 11, 5)
        s.store.amounts[ENERGY] = 0.0
        CombatSystem.start(s, "x", listOf(EnemyType.BANDIT to 1))
        val e = s.battle!!.enemies.first()
        e.x = 11.5; e.y = 5.5
        s.battle!!.prep = 0.0
        val hp0 = e.hp
        repeat(8) { c.step(0.25) }
        // Enerji yokken kule ateş etmemeli
        assertEquals(hp0, e.hp, 1e-9)
        assertTrue(s.shots.none { it.energy })
        // Enerji gelince ateş eder
        s.store.amounts[ENERGY] = 20.0
        repeat(8) { c.step(0.25) }
        assertTrue(e.hp < hp0)
    }

    @Test
    fun shieldAbsorbsDamage() {
        val c = core(); val s = c.state
        s.research.completed += TechId.ENERGY_SHIELDS
        place(s, BuildingType.SHIELD_CENTER, 10, 5)
        assertTrue(CombatSystem.shieldCapacity(s) > 0)
        CombatSystem.start(s, "x", listOf(EnemyType.BEAST to 1))
        assertTrue(s.battle!!.shield > 0)
    }

    @Test
    fun barracksGuardsFightAndTargetPriorityWorks() {
        val c = core(); val s = c.state
        place(s, BuildingType.BARRACKS, 11, 5)
        CombatSystem.start(s, "x", listOf(EnemyType.BANDIT to 2))
        assertEquals(2, s.battle!!.guards.size)
        assertTrue(CombatSystem.setPriority(s, TargetPriority.STRONGEST).success)
        assertEquals(TargetPriority.STRONGEST, s.defensePriority)
        place(s, BuildingType.DEFENSE_TOWER, 6, 7)
        assertTrue(CombatSystem.setTowerPriority(s, s.buildings.last().id, TargetPriority.FASTEST).success)
        assertTrue(runBattle(c))
    }

    @Test
    fun enemyAiPrefersRightTargets() {
        val s = core().state
        place(s, BuildingType.DEFENSE_TOWER, 12, 6)
        val mech = Enemy(1, EnemyType.MECH, 3.0, 6.0, 50.0, 50.0)
        assertEquals(BuildingType.DEFENSE_TOWER, CombatSystem.chooseTarget(s, mech)?.type)
        val bandit = Enemy(2, EnemyType.BANDIT, 15.0, 6.0, 30.0, 30.0)
        assertTrue(CombatSystem.chooseTarget(s, bandit)!!.def.storage.isNotEmpty())
    }

    @Test
    fun tributeAndEmergencyRepair() {
        val s = core().state
        CombatSystem.start(s, "x", listOf(EnemyType.RAIDER to 3))
        s.buildings.first().hp = 40.0
        assertTrue(CombatSystem.emergencyRepair(s).success)
        assertEquals(60.0, s.buildings.first().hp, 1e-9)
        assertFalse(CombatSystem.emergencyRepair(s).success) // bekleme süresi
        val gold = s.store[GOLD]
        assertTrue(CombatSystem.payTribute(s).success)
        assertNull(s.battle)
        assertEquals(gold - 36, s.store[GOLD], 1e-9)
    }

    @Test
    fun banditEventOfferBattle() {
        val s = core().state
        s.pendingEvent = EventSystem.create(s, Biome.DESERT).let { if (it.kind == "bandits") it else PendingEvent("bandits", "Haydut", "", listOf(EventOption("Savun", effect = "battle=BANDIT:3"))) }
        assertTrue(EventSystem.resolve(s, 0).success)
        assertNotNull(s.battle)
    }

    @Test
    fun battleSurvivesSaveAndOfflineDoesNotFight() {
        val c = core(); val s = c.state
        CombatSystem.start(s, "x", listOf(EnemyType.BANDIT to 2))
        val st = MemoryStorage(); SaveManager(st).save(s, 1_000L)
        val l = SaveManager(st).load()!!
        assertNotNull(l.battle)
        assertEquals(2, l.battle!!.enemies.size)
        val c2 = GameCore(l)
        c2.applyOffline(1_000L + 3600_000L)
        assertNotNull(l.battle, "çevrimdışıyken savaş otomatik sonuçlanmamalı")
        assertEquals(2, l.battle!!.enemies.size)
    }

    // ------------------------------------------------------------------ Keşif

    @Test
    fun expeditionRequiresCenterAndPeople() {
        val s = core().state
        assertNotNull(ExplorationSystem.reason(s, MissionType.RESOURCES, Cell(5, 0)))
        place(s, BuildingType.EXPLORATION_CENTER, 11, 6)
        assertNull(ExplorationSystem.reason(s, MissionType.RESOURCES, Cell(5, 0)))
        assertNotNull(ExplorationSystem.reason(s, MissionType.RESOURCES, Cell(30, 0)))
    }

    @Test
    fun expeditionTakesWorkersAndReturnsWithRewards() {
        val c = core(); val s = c.state
        place(s, BuildingType.EXPLORATION_CENTER, 11, 6)
        s.store.amounts[WOOD] = 0.0; s.store.amounts[STONE] = 0.0; s.store.amounts[FOOD] = 300.0
        val free0 = PopulationSystem.freeWorkers(s)
        val explored0 = s.travel.explored.size
        assertTrue(ExplorationSystem.send(s, MissionType.RESOURCES, Cell(6, 0)).success)
        assertEquals(free0 - 3, PopulationSystem.freeWorkers(s))
        assertFalse(ExplorationSystem.send(s, MissionType.RUINS, Cell(6, 0)).success) // tek ekip
        var t = 0.0
        while (s.expeditions.isNotEmpty() && t < 600) { c.step(1.0); t += 1.0; s.pendingEvent?.let { EventSystem.resolve(s, it.options.lastIndex) } }
        assertTrue(s.expeditions.isEmpty())
        assertEquals(0, s.population.away)
        assertEquals(1, s.stats.expeditionsDone)
        assertTrue(s.travel.explored.size > explored0)
    }

    @Test
    fun civilizationMissionFindsUnknownCiv() {
        val c = core(3); val s = c.state
        place(s, BuildingType.EXPLORATION_CENTER, 11, 6)
        s.research.completed += TechId.EXPEDITION_GEAR
        repeat(10) {
            if (s.expeditions.isEmpty() && s.civs.none { it.met && it.culture != Culture.MERCHANT_GUILD }) {
                ExplorationSystem.send(s, MissionType.CIVILIZATION, Cell(10, 6))
            }
            var t = 0.0
            while (s.expeditions.isNotEmpty() && t < 600) { c.step(1.0); t += 1.0; s.pendingEvent?.let { EventSystem.resolve(s, it.options.lastIndex) } }
        }
        assertTrue(s.civs.count { it.met } >= 1, "en az bir medeniyet bulunmalı")
    }

    @Test
    fun expeditionsProgressOffline() {
        val c = core(); val s = c.state
        place(s, BuildingType.EXPLORATION_CENTER, 11, 6)
        assertTrue(ExplorationSystem.send(s, MissionType.DANGER, Cell(4, 0)).success)
        s.lastSavedEpochMs = 1L
        c.applyOffline(1L + 1800_000L)
        assertTrue(s.expeditions.isEmpty())
    }

    @Test
    fun riskIsBoundedAndReducedByGear() {
        val s = core().state
        place(s, BuildingType.EXPLORATION_CENTER, 11, 6)
        val r0 = ExplorationSystem.risk(s, MissionType.TECH, Cell(10, 0))
        s.research.completed += TechId.EXPEDITION_GEAR
        val r1 = ExplorationSystem.risk(s, MissionType.TECH, Cell(10, 0))
        assertTrue(r1 < r0)
        assertTrue(r1 in 0.03..0.8)
    }
}
