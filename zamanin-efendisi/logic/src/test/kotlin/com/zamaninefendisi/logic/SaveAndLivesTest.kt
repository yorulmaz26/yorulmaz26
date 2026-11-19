package com.zamaninefendisi.logic

import com.zamaninefendisi.logic.levels.LevelCatalog
import com.zamaninefendisi.logic.save.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeClock(var wall: Long = 1_700_000_000_000, var elapsed: Long = 10_000, var boot: String = "1") : GameClock {
    override fun wallMs() = wall
    override fun elapsedMs() = elapsed
    override fun bootId() = boot
    fun pass(ms: Long) { wall += ms; elapsed += ms }
}

class SaveAndLivesTest {
    private val min = 60_000L

    @Test
    fun livesRegenerateSequentiallyAndStopWhenFull() {
        val c = FakeClock()
        val ls = LifeSystem(c)
        var s = ls.update(LivesState())
        assertEquals(4, s.lives)
        s = ls.consume(s); s = ls.consume(s)
        assertEquals(2, s.lives)
        c.pass(14 * min); s = ls.update(s)
        assertEquals(2, s.lives)
        c.pass(1 * min); s = ls.update(s)
        assertEquals(3, s.lives)
        assertEquals(LIFE_REGEN_MS, s.regenRemainingMs)
        c.pass(100 * min); s = ls.update(s)
        assertEquals(4, s.lives)
        assertEquals(0, s.regenRemainingMs)
        s = ls.consume(s)
        assertEquals(LIFE_REGEN_MS, s.regenRemainingMs)
    }

    @Test
    fun movingWallClockForwardDoesNotRefill() {
        val c = FakeClock()
        val ls = LifeSystem(c)
        var s = ls.update(LivesState())
        repeat(4) { s = ls.consume(s) }
        assertEquals(0, s.lives)
        c.wall += 10 * 60 * min // saat 10 saat ileri alındı, gerçek süre geçmedi
        c.elapsed += 1000
        s = ls.update(s)
        assertEquals(0, s.lives)
    }

    @Test
    fun movingWallClockBackDoesNotPunish() {
        val c = FakeClock()
        val ls = LifeSystem(c)
        var s = ls.consume(ls.update(LivesState()))
        c.wall -= 5 * 60 * min
        c.elapsed += 15 * min
        s = ls.update(s)
        assertEquals(4, s.lives)
    }

    @Test
    fun afterRebootWallClockIsUsed() {
        val c = FakeClock()
        val ls = LifeSystem(c)
        var s = ls.consume(ls.update(LivesState()))
        c.boot = "2"; c.elapsed = 5_000; c.wall += 20 * min
        s = ls.update(s)
        assertEquals(4, s.lives)
    }

    @Test
    fun saveRoundTripAndBackup() {
        val st = InMemoryStorage()
        val sys = SaveSystem(st)
        val data = SaveData(gold = 77, levels = mapOf(1 to LevelProgress(3, 8, 1, 2)),
            inProgress = InProgress(2, listOf(Action.SwitchEra(Era.PAST), Action.Build(BuildKind.PLANT, Pos(4, 4)), Action.Wait)))
        sys.save(data)
        sys.save(data.copy(gold = 78))
        assertEquals(78, sys.load().gold)
        assertEquals(data.inProgress, sys.load().inProgress)
        st.files[SaveSystem.MAIN] = "{bozuk"
        val recovered = sys.load()
        assertEquals(77, recovered.gold)
        assertNotNull(sys.lastLoadProblem)
    }

    @Test
    fun migratesVersion0() {
        val sys = SaveSystem(InMemoryStorage())
        val old = """{"stars":{"1":3,"2":1},"coins":120,"unknownField":true}"""
        val d = sys.decode(old)!!
        assertEquals(SAVE_VERSION, d.version)
        assertEquals(120, d.gold)
        assertEquals(3, d.levels[1]!!.stars)
        assertTrue(d.levels[2]!!.completed)
    }

    @Test
    fun progressionUnlocksAndRewardsAndUsesLives() {
        val c = FakeClock()
        val p = Progression(SaveSystem(InMemoryStorage()), c)
        assertTrue(p.isUnlocked(1)); assertFalse(p.isUnlocked(2))
        val l1 = LevelCatalog.level(1)!!
        val won = LevelSession.replay(l1, l1.solution)
        val r = p.recordWin(won, c.wallMs())
        assertTrue(r.firstClear); assertEquals(60, r.gold)
        assertTrue(p.isUnlocked(2))
        assertTrue(p.data.journal.isNotEmpty())
        val again = p.recordWin(LevelSession.replay(l1, l1.solution), c.wallMs())
        assertFalse(again.firstClear); assertEquals(5, again.gold)

        val l2 = LevelCatalog.level(2)!!
        val lost = LevelSession.replay(l2, List(12) { Action.SwitchEra(if (it % 2 == 0) Era.PAST else Era.PRESENT) })
        assertEquals(SessionStatus.LOST, lost.status)
        p.recordFail(lost)
        assertEquals(3, p.data.lives.lives)
        // Tamamlanmış bölümde başarısızlık can harcamaz.
        val lost1 = LevelSession.replay(l1, List(30) { Action.SwitchEra(if (it % 2 == 0) Era.PAST else Era.PRESENT) })
        assertEquals(SessionStatus.LOST, lost1.status)
        p.recordFail(lost1)
        assertEquals(3, p.data.lives.lives)
    }

    @Test
    fun masterLevelCurve() {
        assertEquals(1, MasterLevel.levelOf(0))
        assertEquals(2, MasterLevel.levelOf(100))
        assertEquals(2, MasterLevel.levelOf(249))
        assertEquals(3, MasterLevel.levelOf(250))
    }
}
