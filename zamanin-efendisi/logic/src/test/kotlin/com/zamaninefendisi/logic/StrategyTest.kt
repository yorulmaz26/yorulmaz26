package com.zamaninefendisi.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyTest {
    private fun level(
        map: List<String>, eras: List<Era> = listOf(Era.PAST, Era.PRESENT), enemies: List<EnemySpawn> = emptyList(),
        units: List<UnitSpawn> = emptyList(), protect: List<Pos> = emptyList(), start: Era = Era.PRESENT,
        allowed: Set<BuildKind> = BuildKind.entries.toSet(), freezes: Int = 0, overrides: Map<Era, List<String>> = emptyMap(),
    ) = LevelDef(
        id = 900, area = 0, name = "T", intro = "", map = map, eras = eras, startEra = start, startWood = 20,
        allowed = eras.associateWith { allowed }, goals = listOf(Goal.Survive(99, "")), maxTurns = 99, par = 1,
        enemies = enemies, units = units, protect = protect, freezes = freezes, overrides = overrides, solution = emptyList(),
    )

    @Test
    fun frozenObjectDoesNotEvolve() {
        val s = LevelSession(level(listOf("@H.G".replace('.', 'G')), eras = Era.entries, start = Era.PAST, freezes = 1))
        assertEquals(Feature.RUINS, s.cell(Era.PRESENT, Pos(1, 0)).feature)
        s.perform(Action.Build(BuildKind.FREEZE, Pos(1, 0)))
        assertEquals(Feature.HUT, s.cell(Era.PRESENT, Pos(1, 0)).feature)
        assertEquals(Feature.HUT, s.cell(Era.FUTURE, Pos(1, 0)).feature)
        assertEquals(0, s.freezes)
        assertTrue(s.timeline.matchesFullRebuild())
    }

    @Test
    fun pastTowerBecomesStoneTowerAndShoots() {
        val s = LevelSession(level(listOf("GGGGG", "GGGGG", "GGG@F"), enemies = listOf(EnemySpawn(Pos(4, 0), EnemyKind.PLUNDERER)), start = Era.PAST))
        s.perform(Action.Build(BuildKind.TOWER, Pos(3, 1)))
        assertEquals(Feature.STONE_TOWER, s.cell(Era.PRESENT, Pos(3, 1)).feature)
        s.perform(Action.Wait)
        assertTrue("Taş kule yağmacıyı vurmalı", s.enemies.isEmpty())
    }

    @Test
    fun plundererRazesFields() {
        val s = LevelSession(level(listOf("GGGG", "@GGG", "GGGF"), enemies = listOf(EnemySpawn(Pos(3, 0), EnemyKind.PLUNDERER))))
        repeat(3) { s.perform(Action.Wait) }
        assertEquals(Feature.NONE, s.cell(Era.PRESENT, Pos(3, 2)).feature)
        assertTrue(s.journal.any { "yağmaladı" in it.text })
    }

    @Test
    fun saboteurTargetsPlayerBuiltStructures() {
        val s = LevelSession(level(listOf("GGGGGG", "@GWGGG"), enemies = listOf(EnemySpawn(Pos(5, 0), EnemyKind.SABOTEUR)), start = Era.PAST))
        s.perform(Action.Move(Pos(1, 1)))
        s.perform(Action.Build(BuildKind.BRIDGE, Pos(2, 1)))
        assertEquals(Feature.BRIDGE_STONE, s.cell(Era.PRESENT, Pos(2, 1)).feature)
        repeat(4) { s.perform(Action.Wait) }
        assertEquals("Ajan Şimdi'deki köprüyü silmeli", Feature.NONE, s.cell(Era.PRESENT, Pos(2, 1)).feature)
        assertEquals("Geçmiş değişmez", Feature.BRIDGE_WOOD, s.cell(Era.PAST, Pos(2, 1)).feature)
    }

    @Test
    fun phaserJumpsOverWalls() {
        val map = listOf("GGGGG", "PPPPP", "GGGGG", "GG@GG", "GGCGG")
        val s = LevelSession(level(map, enemies = listOf(EnemySpawn(Pos(2, 0), EnemyKind.PHASER)), protect = listOf(Pos(2, 4)), start = Era.PAST))
        assertEquals(Feature.STONE_WALL, s.cell(Era.PRESENT, Pos(2, 1)).feature)
        s.perform(Action.Wait)
        assertTrue("Faz atlaması duvarın öbür yanına geçmeli", s.enemies[0].pos.y >= 2)
    }

    @Test
    fun defensiveArcherGuardsAnchorAndEnrages() {
        val s = LevelSession(level(listOf("GGGGGGG", "GGGGGGG", "GGGGGGG", "GGGGGGG", "GGGGGG@"), enemies = listOf(EnemySpawn(Pos(0, 0), EnemyKind.ARCHER))))
        repeat(3) { s.perform(Action.Wait) }
        assertEquals("Savunmacı uzaktaki muhafızı kovalamaz", Pos(0, 0), s.enemies[0].pos)
    }

    @Test
    fun technicianUpgradesItself() {
        val s = LevelSession(level(listOf("GGGGGGGG", "@GGGGGGG"), enemies = listOf(EnemySpawn(Pos(7, 0), EnemyKind.TECHNICIAN)), start = Era.PAST))
        repeat(6) { s.perform(Action.Wait) }
        assertEquals(2, s.enemies[0].attackBonus)
    }

    @Test
    fun knightShieldAbsorbsHitsAndUnitsGuardAutomatically() {
        val s = LevelSession(level(listOf("GGG", "G@G", "GGG", "GGG"), units = listOf(UnitSpawn(UnitType.KNIGHT, Pos(1, 2))), enemies = listOf(EnemySpawn(Pos(1, 3), EnemyKind.RAIDER))))
        s.perform(Action.Move(Pos(0, 1)))
        assertTrue("Nöbetteki şövalye akıncıyı vurmalı", s.enemies.isEmpty())
        assertEquals(5, s.units[0].hp)
    }

    @Test
    fun unitCommandsAreValidatedAndEraBound() {
        val lv = level(listOf("GGGG", "@GGG"), units = listOf(UnitSpawn(UnitType.ARCHER, Pos(3, 1))), enemies = listOf(EnemySpawn(Pos(3, 0), EnemyKind.BRUTE)))
        val s = LevelSession(lv)
        assertNotNull(s.validate(Action.UnitAttack(0, Pos(3, 0)))) // menzil 2–3, düşman bitişik
        s.perform(Action.SwitchEra(Era.PAST))
        assertNotNull(s.validate(Action.UnitMove(0, Pos(2, 1)))) // başka çağdan komuta edilemez
    }

    @Test
    fun comboAcrossThreeEras() {
        val t = LevelSession(level(listOf("@GGG"), eras = Era.entries, start = Era.PAST))
        t.perform(Action.Build(BuildKind.PLANT, Pos(1, 0)))
        t.perform(Action.SwitchEra(Era.PRESENT))
        t.perform(Action.Move(Pos(1, 0)))
        t.perform(Action.Build(BuildKind.WALL, Pos(2, 0)))
        t.perform(Action.SwitchEra(Era.FUTURE))
        t.perform(Action.Build(BuildKind.PLANT, Pos(0, 0)))
        assertEquals(1, t.combos)
    }

    @Test
    fun foresightPredictsWithoutChangingState() {
        val s = LevelSession(level(listOf("@WG"), eras = Era.entries, start = Era.PAST))
        val before = s.timeline.signature()
        val f = s.foresee(Action.Build(BuildKind.BRIDGE, Pos(1, 0)))!!
        assertEquals(before, s.timeline.signature())
        assertTrue(f.changes.any { it.first == Era.FUTURE && it.third.second.feature == Feature.BRIDGE_TECH })
        assertFalse(s.actions.isNotEmpty())
    }

    @Test
    fun heroOnRazedBridgeIsLost() {
        val s = LevelSession(level(listOf("GGGGG", "G@WGG", "GGGGG"), eras = Era.entries, start = Era.PAST, enemies = listOf(EnemySpawn(Pos(4, 0), EnemyKind.SABOTEUR))))
        s.perform(Action.Build(BuildKind.BRIDGE, Pos(2, 1)))
        s.perform(Action.SwitchEra(Era.FUTURE))
        s.perform(Action.Move(Pos(2, 1)))
        var guard = 0
        while (s.status == SessionStatus.PLAYING && guard++ < 6) s.perform(Action.Wait)
        assertEquals(SessionStatus.LOST, s.status)
    }
}
