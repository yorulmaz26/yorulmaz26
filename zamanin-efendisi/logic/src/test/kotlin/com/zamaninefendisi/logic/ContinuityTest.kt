package com.zamaninefendisi.logic

import com.zamaninefendisi.logic.continuity.*
import com.zamaninefendisi.logic.levels.LevelCatalog
import com.zamaninefendisi.logic.meta.BuildingType
import com.zamaninefendisi.logic.meta.Resource
import com.zamaninefendisi.logic.save.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuityTest {
    private val hour = 3_600_000L
    private fun prog(c: FakeClock = FakeClock()) = Progression(SaveSystem(InMemoryStorage()), c) to c

    @Test
    fun collectionHas150UniqueItemsAndMuseumHalls() {
        assertEquals(150, CivCollection.items.size)
        assertEquals(150, CivCollection.items.map { it.id }.toSet().size)
        assertEquals(150, CivCollection.items.map { it.title }.toSet().size)
        assertTrue(Museum.hallOpen(0, 0, 0))
        assertFalse(Museum.hallOpen(2, 30, 1))
        assertTrue(Museum.hallOpen(2, 30, 2))
    }

    @Test
    fun dailyQuestsTrackAndClaim() {
        val (p, c) = prog()
        val cm = p.continuity
        cm.ensurePeriods()
        val q = p.data.continuity.dailyQuests.quests
        assertEquals(3, q.size)
        assertFalse(cm.claimQuest(false, 0))
        cm.event(q[0].type, q[0].target)
        val gold = p.amount(Resource.GOLD)
        assertTrue(cm.claimQuest(false, 0))
        assertTrue(p.amount(Resource.GOLD) > gold)
        assertFalse("İki kez alınamaz", cm.claimQuest(false, 0))
        // Ertesi gün görevler yenilenir.
        c.pass(25 * hour)
        cm.ensurePeriods()
        assertTrue(p.data.continuity.dailyQuests.claimed.isEmpty())
    }

    @Test
    fun dailyAndWeeklyPuzzlesAreDeterministicAndRewardOnce() {
        val (p, _) = prog()
        val cm = p.continuity
        val a = cm.dailyLevel(19000); val b = cm.dailyLevel(19000)
        assertEquals(a.map, b.map)
        val won = LevelSession.replay(a, a.solution)
        assertEquals(SessionStatus.WON, won.status)
        assertTrue(cm.recordDaily(won, 19000).isNotEmpty())
        assertTrue(cm.recordDaily(won, 19000).isEmpty())
        for (w in 2700..2703) {
            val lv = cm.weeklyLevel(w)
            assertEquals("hafta $w", SessionStatus.WON, LevelSession.replay(lv, lv.solution).status)
        }
    }

    @Test
    fun endlessRunAdvancesAndRecordsBest() {
        val (p, _) = prog()
        val cm = p.continuity
        var run = cm.startEndless()
        repeat(7) {
            val lv = cm.endlessLevel(run)
            val s = LevelSession.replay(lv, lv.solution)
            assertEquals("aşama ${run.stage}", SessionStatus.WON, s.status)
            run = cm.endlessResult(s)!!
        }
        assertEquals(8, run.stage)
        val lost = LevelSession.replay(cm.endlessLevel(run), List(40) { Action.Wait })
        assertNull(cm.endlessResult(lost))
        assertEquals(7, p.data.continuity.endlessBest)
    }

    @Test
    fun expeditionsUseSafeClockAndSlots() {
        val (p, c) = prog()
        val cm = p.continuity
        assertNull(cm.startExpedition(ExpeditionKind.PAST_SHORT))
        assertNotNull("Tek yuva", cm.startExpedition(ExpeditionKind.PAST_SHORT))
        val id = p.data.continuity.expeditions[0].id
        c.wall += 10 * hour // saat ileri alındı
        assertNull(cm.claimExpedition(id))
        c.pass(hour)
        val got = cm.claimExpedition(id)
        assertNotNull(got)
        assertTrue(p.data.continuity.expeditions.isEmpty())
        p.update { it.copy(buildings = it.buildings + (BuildingType.PORTAL to 2)) }
        assertEquals(2, cm.expeditionSlots())
    }

    @Test
    fun randomEventsTriggerAfterRealTime() {
        val (p, c) = prog()
        val cm = p.continuity
        assertNull(cm.tick())
        c.pass(4 * hour)
        assertNotNull(cm.tick())
        assertNull(cm.tick())
    }

    @Test
    fun prestigeAndDefenseLevelsRemainSolvable() {
        val (p, _) = prog()
        val cm = p.continuity
        assertFalse(cm.prestigeAvailable())
        for (lv in LevelCatalog.handcraftedLevels + listOf(40, 125, 250, 500).map { LevelCatalog.level(it)!! }) {
            val pl = cm.prestigeLevel(lv)
            assertEquals("döngü ${lv.id}", SessionStatus.WON, LevelSession.replay(pl, pl.solution).status)
            assertTrue(pl.maxTurns <= lv.maxTurns)
        }
        for (wave in 1..8) {
            val d = cm.defenseLevel(wave)
            assertEquals("dalga $wave", SessionStatus.WON, LevelSession.replay(d, d.solution).status)
        }
    }

    @Test
    fun altTasksAndAchievements() {
        val (p, c) = prog()
        val cm = p.continuity
        val l1 = LevelCatalog.level(1)!!
        val s = LevelSession.replay(l1, l1.solution)
        p.recordWin(s, c.wallMs())
        val fresh = cm.checkAchievements()
        assertTrue(fresh.any { it.id == "first_win" })
        assertTrue(cm.checkAchievements().isEmpty())
        val task = cm.altTask(l1)
        assertNotNull(task)
        cm.recordAlt(l1, s)
        assertTrue(Achievements.all.size >= 30)
    }
}
