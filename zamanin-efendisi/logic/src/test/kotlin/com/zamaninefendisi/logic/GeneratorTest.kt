package com.zamaninefendisi.logic

import com.zamaninefendisi.logic.continuity.GenRules
import com.zamaninefendisi.logic.continuity.LevelGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorTest {
    @Test
    fun everyTemplateProducesSolvableDeterministicLevels() {
        for (t in LevelGenerator.Template.entries) {
            for (seed in 1L..25L) {
                val g = LevelGenerator.generate(500_000, seed, GenRules(), only = t)
                val s = LevelSession.replay(g.level, g.level.solution)
                assertEquals("Şablon ${g.template} tohum $seed çözülemedi", SessionStatus.WON, s.status)
                assertTrue(s.actionsUsed <= g.level.par)
                val again = LevelSession.replay(LevelGenerator.generate(500_000, seed, GenRules(), only = t).level, g.level.solution)
                assertEquals(s.timeline.signature(), again.timeline.signature())
            }
        }
    }

    @Test
    fun anomaliesStillSolvable() {
        for (seed in 1L..40L) {
            val g = LevelGenerator.generate(600_000, seed, GenRules(energyLimited = true, desertSpeed = 2, extraEnemies = 1, slack = 2, futureGoal = seed % 2 == 0L))
            val s = LevelSession.replay(g.level, g.level.solution)
            assertEquals("tohum $seed (${g.template})", SessionStatus.WON, s.status)
            assertTrue(!LevelSession(g.level).let { st -> g.level.goals.all { it.met(st) } })
        }
    }

    @Test
    fun templatesAreVaried() {
        val kinds = (1L..60L).map { LevelGenerator.generate(700_000, it).template }.toSet()
        assertTrue("En az 4 farklı şablon üretilmeli: $kinds", kinds.size >= 4)
    }
}

class GeneratorCoverageTest {
    @Test
    fun forcedTemplatesAreHonoured() {
        for (t in LevelGenerator.Template.entries) {
            val hits = (1L..20L).count { LevelGenerator.generate(800_000, it, only = t).template == t }
            println("ŞABLON $t: $hits/20")
            assertTrue("$t şablonu üretilemiyor", hits >= 15)
        }
    }
}
