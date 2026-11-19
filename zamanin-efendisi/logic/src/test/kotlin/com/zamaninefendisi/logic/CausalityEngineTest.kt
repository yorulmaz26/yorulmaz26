package com.zamaninefendisi.logic

import com.zamaninefendisi.logic.levels.Area01
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CausalityEngineTest {

    private fun level(
        map: List<String>, eras: List<Era> = Era.entries, desert: Int = 0,
        links: List<CausalLink> = emptyList(), allowed: Set<BuildKind> = BuildKind.entries.toSet(),
    ) = LevelDef(
        id = 999, area = 0, name = "Test", intro = "", map = map, eras = eras, startEra = Era.PAST, startWood = 99,
        allowed = eras.associateWith { allowed }, goals = listOf(Goal.Survive(1000, "")), maxTurns = 1000, par = 1,
        desertSpeed = desert, links = links, solution = emptyList(),
    )

    @Test
    fun bridgeEvolvesThroughAllThreeEras() {
        val e = CausalityEngine(level(listOf("@WG")))
        e.addChange(Era.PAST, Pos(1, 0), BuildKind.BRIDGE, 1)
        assertEquals(Feature.BRIDGE_WOOD, e.grid(Era.PAST)[Pos(1, 0)].feature)
        assertEquals(Feature.BRIDGE_STONE, e.grid(Era.PRESENT)[Pos(1, 0)].feature)
        assertEquals(Feature.BRIDGE_TECH, e.grid(Era.FUTURE)[Pos(1, 0)].feature)
        assertEquals(2, e.grid(Era.FUTURE)[Pos(1, 0)].level)
        assertEquals(0, e.grid(Era.FUTURE)[Pos(1, 0)].causeId)
    }

    @Test
    fun futureChangesNeverAffectThePast() {
        val e = CausalityEngine(level(listOf("@GG")))
        val r = e.addChange(Era.FUTURE, Pos(1, 0), BuildKind.PLANT, 1).second
        assertEquals(Feature.NONE, e.grid(Era.PAST)[Pos(1, 0)].feature)
        assertEquals(Feature.NONE, e.grid(Era.PRESENT)[Pos(1, 0)].feature)
        assertTrue(r.changed[Era.PAST].isNullOrEmpty())
    }

    @Test
    fun villageNeedsWaterAndNoSand() {
        val e = CausalityEngine(level(listOf("@GGW", "GGGG")))
        e.addChange(Era.PAST, Pos(2, 0), BuildKind.VILLAGE, 1)
        e.addChange(Era.PAST, Pos(0, 1), BuildKind.VILLAGE, 2)
        assertEquals(Feature.TOWN, e.grid(Era.PRESENT)[Pos(2, 0)].feature)
        assertEquals(Feature.CITY, e.grid(Era.FUTURE)[Pos(2, 0)].feature)
        assertEquals(Feature.RUINS, e.grid(Era.PRESENT)[Pos(0, 1)].feature)
    }

    @Test
    fun saplingStopsDesert() {
        val lv = level(listOf("SG@G"), eras = listOf(Era.PAST, Era.PRESENT), desert = 2)
        val e = CausalityEngine(lv)
        assertEquals(Terrain.SAND, e.grid(Era.PRESENT)[Pos(2, 0)].terrain)
        e.addChange(Era.PAST, Pos(3, 0), BuildKind.PLANT, 1)
        assertEquals(Terrain.GRASS, e.grid(Era.PRESENT)[Pos(2, 0)].terrain)
        assertEquals(Terrain.SAND, e.grid(Era.PRESENT)[Pos(1, 0)].terrain) // fidana 2 hücre uzak: korunmaz
        assertTrue(e.matchesFullRebuild())
    }

    @Test
    fun paradoxVoidsLaterEraChangePermanently() {
        val s = LevelSession(level(listOf("@GG")))
        s.perform(Action.SwitchEra(Era.PRESENT))
        s.perform(Action.Build(BuildKind.WALL, Pos(1, 0)))
        assertEquals(Feature.PALISADE, s.cell(Era.PRESENT, Pos(1, 0)).feature)
        val woodBefore = s.wood
        s.perform(Action.SwitchEra(Era.PAST))
        s.perform(Action.Build(BuildKind.PLANT, Pos(1, 0)))
        assertEquals(Feature.TREE, s.cell(Era.PRESENT, Pos(1, 0)).feature)
        assertEquals(1, s.paradoxCount)
        assertEquals(woodBefore + BuildKind.WALL.woodCost, s.wood)
        assertTrue(s.timeline.changes[0].voided)
        assertTrue(s.journal.any { it.kind == JournalKind.PARADOX })
        assertTrue(s.timeline.matchesFullRebuild())
    }

    @Test
    fun incrementalMatchesFullRebuildUnderRandomPlay() {
        val rnd = Random(42)
        val map = listOf("SSGGWGGT", "SGTGWGGG", "SG@GWGTG", "GGGGWWGG", "MGGTGWGG", "GGGGGWGG")
        repeat(30) {
            val e = CausalityEngine(level(map, desert = 2))
            repeat(40) { turn ->
                val era = Era.entries[rnd.nextInt(3)]
                val p = Pos(rnd.nextInt(8), rnd.nextInt(6))
                val kind = BuildKind.entries[rnd.nextInt(BuildKind.entries.size)]
                if (EvolutionRules.applyBuild(kind, e.grid(era)[p], era) != null) e.addChange(era, p, kind, turn)
            }
            assertTrue(e.matchesFullRebuild())
        }
    }

    @Test
    fun incrementalRecomputesOnlyNearbyCells() {
        val map = List(20) { "G".repeat(20) }.toMutableList().also { it[0] = "@" + "G".repeat(19) }
        val e = CausalityEngine(level(map))
        val before = e.recomputedCells
        e.addChange(Era.PAST, Pos(10, 10), BuildKind.PLANT, 1)
        val cost = e.recomputedCells - before
        assertTrue("Yeniden hesaplanan hücre sayısı çok yüksek: $cost", cost < 30)
    }

    @Test
    fun sameEraLinkAndCycleDetection() {
        val ok = level(
            listOf("@XW"), eras = listOf(Era.PAST, Era.PRESENT),
            links = listOf(CausalLink("L1", Era.PAST, Pos(1, 0), Feature.SHRINE_ACTIVE, Era.PRESENT, Pos(2, 0), setTerrain = Terrain.GRASS, description = "")),
        )
        val e = CausalityEngine(ok)
        assertEquals(Terrain.WATER, e.grid(Era.PRESENT)[Pos(2, 0)].terrain)
        e.addChange(Era.PAST, Pos(1, 0), BuildKind.ACTIVATE, 1)
        assertEquals(Terrain.GRASS, e.grid(Era.PRESENT)[Pos(2, 0)].terrain)
        assertTrue(e.matchesFullRebuild())

        val cyclic = level(
            listOf("@XX"),
            links = listOf(
                CausalLink("A", Era.PAST, Pos(1, 0), Feature.SHRINE_ACTIVE, Era.PAST, Pos(2, 0), setFeature = Feature.SHRINE_ACTIVE, description = ""),
                CausalLink("B", Era.PAST, Pos(2, 0), Feature.SHRINE_ACTIVE, Era.PAST, Pos(1, 0), setFeature = Feature.SHRINE_ACTIVE, description = ""),
            ),
        )
        assertFalse(CausalGraph.validate(cyclic).isEmpty())
        val retro = level(
            listOf("@XX"),
            links = listOf(CausalLink("R", Era.FUTURE, Pos(1, 0), Feature.SHRINE, Era.PAST, Pos(2, 0), setFeature = Feature.NONE, description = "")),
        )
        assertTrue(CausalGraph.validate(retro).any { "geriye dönük" in it })
    }

    @Test
    fun cuttingTheWrongTreeRuinsTheVillage() {
        val s = LevelSession(Area01.level3)
        s.perform(Action.SwitchEra(Era.PAST))
        s.perform(Action.Move(Pos(1, 3)))
        s.perform(Action.Build(BuildKind.CUT, Pos(1, 4)))
        s.perform(Action.Move(Pos(2, 3)))
        s.perform(Action.Move(Pos(3, 3)))
        s.perform(Action.Build(BuildKind.VILLAGE, Pos(3, 4)))
        assertEquals(Feature.RUINS, s.cell(Era.PRESENT, Pos(3, 4)).feature)
        assertEquals(SessionStatus.PLAYING, s.status)
    }
}
