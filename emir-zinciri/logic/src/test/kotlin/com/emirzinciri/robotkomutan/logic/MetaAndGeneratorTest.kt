package com.emirzinciri.robotkomutan.logic

import com.emirzinciri.robotkomutan.logic.gen.Generator
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.meta.Meta
import com.emirzinciri.robotkomutan.logic.meta.Metric
import com.emirzinciri.robotkomutan.logic.progress.SaveData
import com.emirzinciri.robotkomutan.logic.progress.Stars
import com.emirzinciri.robotkomutan.logic.sim.Outcome
import com.emirzinciri.robotkomutan.logic.sim.Simulation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MetaAndGeneratorTest {
    @Test fun atLeast100UniqueAchievements() {
        assertTrue(Meta.achievements.size >= 100)
        assertEquals(Meta.achievements.size, Meta.achievements.map { it.id }.toSet().size)
    }

    @Test fun questsAreDeterministicPerDayAndClaimOnce() {
        val a = SaveData(); val b = SaveData()
        Meta(a).refresh(20_000, 2_857); Meta(b).refresh(20_000, 2_857)
        assertEquals(a.meta.daily.map { it.metric to it.target }, b.meta.daily.map { it.metric to it.target })
        assertEquals(3, a.meta.daily.size); assertEquals(3, a.meta.weekly.size)
        val m = Meta(a)
        val q = a.meta.daily.first()
        assertFalse(m.claim(q))
        // Görevin ölçtüğü değeri hedef kadar artır
        repeat(q.target.toInt() * 50) { simulateProgress(a, q.metric) }
        assertTrue(m.isDone(q))
        val gold = a.wallet.gold
        assertTrue(m.claim(q))
        assertEquals(gold + q.gold, a.wallet.gold)
        assertFalse(m.claim(q))
        // Yeni gün: görevler yenilenir
        m.refresh(20_001, 2_857)
        assertTrue(a.meta.daily.none { it.claimed })
    }

    private fun simulateProgress(s: SaveData, metric: Metric) {
        val m = Meta(s)
        when (metric) {
            Metric.WINS -> m.count(com.emirzinciri.robotkomutan.logic.meta.Counter.WINS)
            Metric.STARS -> s.record(s.levels.size + 1).stars = 3
            Metric.CARDS_PLACED -> s.stats.cardsPlaced++
            Metric.BUILD_UPGRADES -> m.count(com.emirzinciri.robotkomutan.logic.meta.Counter.BUILD_UPGRADES)
            Metric.ENDLESS_BEST -> s.meta.endless.bestFloor++
            Metric.DAILY_PUZZLES -> m.count(com.emirzinciri.robotkomutan.logic.meta.Counter.DAILY_PUZZLES)
            Metric.ENEMIES -> m.count(com.emirzinciri.robotkomutan.logic.meta.Counter.ENEMIES)
            Metric.QUESTS -> m.count(com.emirzinciri.robotkomutan.logic.meta.Counter.QUESTS)
            Metric.TRADES -> s.factory.trades++
            else -> Unit
        }
    }

    @Test fun achievementUnlockAndClaim() {
        val s = SaveData()
        val m = Meta(s)
        val first = Meta.achievements.first { it.metric == Metric.LEVELS_COMPLETED && it.threshold == 1L }
        assertFalse(m.isUnlocked(first))
        s.record(1).stars = 1
        assertTrue(m.isUnlocked(first))
        assertTrue(m.claim(first)); assertFalse(m.claim(first))
    }

    @Test fun generatedLevelsAreSolvableAndDeterministic() {
        for (floor in listOf(1, 3, 5, 8, 12)) for (seed in 1L..2L) {
            val r = assertNotNull(Generator.generate(floor, seed), "kat $floor tohum $seed")
            val again = Generator.generate(floor, seed)!!
            assertEquals(r.def, again.def)
            val out = Simulation.create(LevelData(r.def), r.def.solution).getOrThrow().runToEnd()
            assertIs<Outcome.Win>(out)
            assertTrue(Stars.count(r.def, out) >= 2)
            assertTrue(r.def.par >= Generator.minCards(floor))
            assertIs<Outcome.Fail>(Simulation.create(LevelData(r.def), emptyMap()).getOrThrow().runToEnd())
        }
    }

    @Test fun dailyPuzzleSameForSameDay() {
        assertEquals(Generator.daily(20_100)!!.def, Generator.daily(20_100)!!.def)
    }
}
