package com.zamaninefendisi.logic

import com.zamaninefendisi.logic.levels.LevelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Her bölümün en az bir geçerli çözümü olduğunu ve çözümün deterministik olduğunu doğrular. */
class LevelSolvabilityTest {

    @Test
    fun everyLevelIsSolvableWithItsReferenceSolution() {
        for (level in LevelCatalog.levels) {
            val s = LevelSession(level)
            for ((i, a) in level.solution.withIndex()) {
                assertNull("Bölüm ${level.id}, adım $i ($a) geçersiz", s.validate(a))
                s.perform(a)
                assertTrue("Bölüm ${level.id}, adım $i sonrası nedensellik tutarsız", s.timeline.matchesFullRebuild())
                if (i < level.solution.lastIndex) assertEquals("Bölüm ${level.id} erken bitti (adım $i): ${s.failReason}", SessionStatus.PLAYING, s.status)
            }
            assertEquals("Bölüm ${level.id} çözümle kazanılamadı: ${s.failReason}", SessionStatus.WON, s.status)
            assertTrue("Bölüm ${level.id}: çözüm par değerini aşıyor", s.actionsUsed <= level.par)
            assertEquals(3, s.stars())
        }
    }

    @Test
    fun catalogHas100AreasAnd500Levels() {
        assertEquals(100, LevelCatalog.areas.size)
        assertEquals(500, LevelCatalog.levels.size)
        assertEquals((1..500).toList(), LevelCatalog.levels.map { it.id })
        for (a in LevelCatalog.areas) assertTrue(a.levels.all { it.area == a.id })
        assertEquals(LevelCatalog.levelName(250), LevelCatalog.level(250)!!.name)
    }

    @Test
    fun replayIsDeterministic() {
        for (level in LevelCatalog.levels) {
            val a = LevelSession.replay(level, level.solution)
            val b = LevelSession.replay(level, level.solution)
            assertEquals(a.timeline.signature(), b.timeline.signature())
            assertEquals(a.status, b.status)
            assertEquals(a.enemies.map { it.pos }, b.enemies.map { it.pos })
        }
    }

    @Test
    fun levelsStartUnsolved() {
        for (level in LevelCatalog.levels) {
            val s = LevelSession(level)
            assertTrue("Bölüm ${level.id} başlangıçta çözülmüş olmamalı", !level.goals.all { it.met(s) })
        }
    }

    @Test
    fun allLevelsHaveValidCausality() {
        for (level in LevelCatalog.levels) assertTrue(CausalGraph.validate(level).isEmpty())
    }

    @Test
    fun undoByReplayRestoresState() {
        val level = LevelCatalog.level(1)!!
        val full = LevelSession.replay(level, level.solution.take(4))
        val undone = LevelSession.replay(level, full.actions.dropLast(1))
        val direct = LevelSession.replay(level, level.solution.take(3))
        assertEquals(direct.timeline.signature(), undone.timeline.signature())
        assertEquals(direct.wood, undone.wood)
    }
}
