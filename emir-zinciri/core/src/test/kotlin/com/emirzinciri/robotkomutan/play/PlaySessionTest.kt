package com.emirzinciri.robotkomutan.play

import com.emirzinciri.robotkomutan.logic.command.CommandType
import com.emirzinciri.robotkomutan.logic.command.Instr
import com.emirzinciri.robotkomutan.logic.level.LevelCatalog
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.sim.Outcome
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaySessionTest {
    private val catalog by lazy {
        val dir = File(System.getProperty("levels.dir"))
        LevelCatalog.fromAreaFiles(dir.listFiles()!!.filter { it.name.startsWith("area_") }.sortedBy { it.name }.map { it.readText() })
    }

    private fun session(id: Int, cards: List<String> = emptyList()) =
        PlaySession(LevelData(catalog[id]!!), if (cards.isEmpty()) emptyMap() else mapOf("A" to cards))

    /** Sonuç gösterilene kadar oyun döngüsünü taklit eder. */
    private fun runUntilOutcome(s: PlaySession, dt: Float, tick: Float): Outcome {
        repeat(100_000) { s.update(dt, tick)?.let { return it } }
        error("sonuç gelmedi")
    }

    @Test fun editingRespectsCardLimit() {
        val s = session(1) // sınır 4
        repeat(4) { assertTrue(s.insert("A", 99, Instr(CommandType.FORWARD))) }
        assertFalse(s.canAddCard)
        assertFalse(s.insert("A", 0, Instr(CommandType.FORWARD)))
        assertEquals(4, s.totalCards)
    }

    @Test fun blockCardAddsMatchingEndAndRemovesIt() {
        val s = session(1)
        s.insertWithBlock("A", 0, Instr(CommandType.REPEAT, 3))
        assertEquals(listOf(CommandType.REPEAT, CommandType.END), s.programs.getValue("A").map { it.type })
        assertEquals(1, s.totalCards) // Blok sonu sayılmaz
        s.insert("A", 1, Instr(CommandType.FORWARD))
        s.remove("A", 0)
        assertEquals(listOf(CommandType.FORWARD), s.programs.getValue("A").map { it.type })
    }

    @Test fun moveDuplicateAndArg() {
        val s = session(3)
        s.insert("A", 0, Instr(CommandType.FORWARD)); s.insert("A", 1, Instr(CommandType.TURN_LEFT))
        s.move("A", 1, 0)
        assertEquals(CommandType.TURN_LEFT, s.programs.getValue("A")[0].type)
        assertTrue(s.duplicate("A", 0))
        assertEquals(listOf(CommandType.TURN_LEFT, CommandType.TURN_LEFT, CommandType.FORWARD), s.programs.getValue("A").map { it.type })
    }

    @Test fun draftCardsNotAvailableInLevelAreDropped() {
        val s = session(1, listOf("FORWARD", "PUSH", "BOGUS", "FORWARD"))
        assertEquals(2, s.totalCards)
    }

    @Test fun solutionWinsThroughGameLoop() {
        val def = catalog[7]!!
        val s = session(7, def.solution.getValue("A"))
        assertTrue(s.play())
        assertIs<Outcome.Win>(runUntilOutcome(s, 1 / 60f, 0.42f))
        assertEquals(PlaySession.Mode.DONE, s.mode)
    }

    @Test fun animationSpeedDoesNotChangeResult() {
        for (id in catalog.ids) {
            val def = catalog[id]!!
            val slow = session(id, def.solution.getValue("A")).apply { play() }
            val fast = session(id, def.solution.getValue("A")).apply { play() }
            val a = runUntilOutcome(slow, 1 / 60f, 0.42f)
            val b = runUntilOutcome(fast, 1 / 30f, 0.105f)
            assertEquals(a, b, "Bölüm $id")
            assertEquals(slow.current.signature(), fast.current.signature())
        }
    }

    @Test fun outcomeShownOnlyAfterLastAnimationFinishes() {
        val s = session(1, listOf("FORWARD", "FORWARD"))
        s.play()
        var shownAt = -1
        for (frame in 0 until 200) {
            if (s.update(0.1f, 0.4f) != null) { shownAt = frame; break }
        }
        // 2 adım × 0,4 sn; sonuç en erken 0,8 sn sonra (8. kare civarı) gösterilir.
        assertTrue(shownAt >= 7, "erken gösterildi: $shownAt")
    }

    @Test fun stepAndStepBack() {
        val s = session(2, catalog[2]!!.solution.getValue("A"))
        assertTrue(s.stepOnce())
        assertEquals(PlaySession.Mode.PAUSED, s.mode)
        s.update(1f, 0.4f) // animasyon biter
        assertTrue(s.stepOnce())
        s.update(1f, 0.4f)
        assertEquals(2, s.current.tick)
        assertTrue(s.stepBack())
        assertEquals(1, s.current.tick)
    }

    @Test fun editingDuringRunResetsToEdit() {
        val s = session(2, catalog[2]!!.solution.getValue("A"))
        s.play(); s.update(1f, 0.4f)
        assertEquals(PlaySession.Mode.RUNNING, s.mode)
        s.insert("A", 0, Instr(CommandType.FORWARD))
        assertEquals(PlaySession.Mode.EDIT, s.mode)
        assertNull(s.sim)
    }

    @Test fun failureMarksCulpritCardAndKeepsProgram() {
        val s = session(6, listOf("FORWARD", "TURN_LEFT", "FORWARD"))
        s.play()
        val o = runUntilOutcome(s, 0.5f, 0.4f)
        assertIs<Outcome.Fail>(o)
        assertTrue(2 in s.errorCards.getValue("A"))
        s.backToEdit()
        assertEquals(3, s.totalCards)
        assertTrue(2 in s.errorCards.getValue("A"))
    }

    @Test fun unbalancedBlockReportsCompileError() {
        val s = session(1)
        s.insert("A", 0, Instr(CommandType.FORWARD))
        s.insert("A", 1, Instr(CommandType.END))
        assertFalse(s.play())
        assertNotNull(s.compileError)
        assertEquals(1, s.compileError!!.second.error.index)
    }
}
