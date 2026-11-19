package com.emirzinciri.robotkomutan.logic

import com.emirzinciri.robotkomutan.logic.level.LevelDef
import com.emirzinciri.robotkomutan.logic.level.RobotDef
import com.emirzinciri.robotkomutan.logic.progress.FileSaveStorage
import com.emirzinciri.robotkomutan.logic.progress.LoadResult
import com.emirzinciri.robotkomutan.logic.progress.MemorySaveStorage
import com.emirzinciri.robotkomutan.logic.progress.Progression
import com.emirzinciri.robotkomutan.logic.progress.SaveData
import com.emirzinciri.robotkomutan.logic.progress.SaveManager
import com.emirzinciri.robotkomutan.logic.sim.Outcome
import com.emirzinciri.robotkomutan.logic.sim.RunStats
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SaveAndProgressTest {

    private fun sample() = SaveData().apply {
        record(1).stars = 3
        record(2).stars = 1
        drafts[3] = mutableMapOf("A" to listOf("FORWARD", "REPEAT:3", "TURN_LEFT", "END"))
        wallet.gold = 120
        lives.lives = 2
        settings.sound = false
        tutorialsSeen += "first_move"
    }

    @Test fun roundTrip() {
        val m = SaveManager(MemorySaveStorage())
        m.save(sample())
        val r = m.load()
        assertEquals(LoadResult.Source.PRIMARY, r.source)
        assertEquals(sample(), r.data)
    }

    @Test fun corruptPrimaryRecoversFromBackup() {
        val st = MemorySaveStorage()
        val m = SaveManager(st)
        m.save(sample())
        m.save(sample().apply { wallet.gold = 999 }) // önceki sağlam kayıt yedeğe geçer
        st.files[SaveManager.PRIMARY] = "{ \"version\": 1, \"levels\": { bozuk"
        val r = m.load()
        assertEquals(LoadResult.Source.BACKUP, r.source)
        assertEquals(120, r.data.wallet.gold)
        assertNotNull(r.message)
    }

    @Test fun bothCorruptStartsFreshAndKeepsCopy() {
        val st = MemorySaveStorage()
        st.files[SaveManager.PRIMARY] = "çöp"
        st.files[SaveManager.BACKUP] = ""
        val r = SaveManager(st).load()
        assertEquals(LoadResult.Source.NEW, r.source)
        assertEquals("çöp", st.files[SaveManager.CORRUPT])
    }

    @Test fun unknownFieldsAndOldVersionAreAccepted() {
        val st = MemorySaveStorage()
        st.files[SaveManager.PRIMARY] = """{"profileName":"Eski","levels":{"1":{"stars":2}},"futureField":42}"""
        val r = SaveManager(st).load()
        assertEquals(LoadResult.Source.PRIMARY, r.source)
        assertEquals("Eski", r.data.profileName)
        assertEquals(2, r.data.stars(1))
        assertEquals(SaveData.CURRENT_VERSION, r.data.version)
    }

    @Test fun newerVersionIsNotLoadedSilently() {
        val st = MemorySaveStorage()
        st.files[SaveManager.PRIMARY] = """{"version":99}"""
        assertEquals(LoadResult.Source.NEW, SaveManager(st).load().source)
    }

    @Test fun fileStorageAtomicWrite() {
        val dir = Files.createTempDirectory("ez-save").toFile()
        val m = SaveManager(FileSaveStorage(dir))
        m.save(sample())
        m.save(sample())
        assertTrue(dir.resolve(SaveManager.PRIMARY).isFile)
        assertTrue(dir.resolve(SaveManager.BACKUP).isFile)
        assertFalse(dir.resolve(SaveManager.PRIMARY + ".tmp").exists())
        assertEquals(sample(), SaveManager(FileSaveStorage(dir)).load().data)
        dir.deleteRecursively()
    }

    // ---------------------------------------------------------------- ilerleme

    private fun def(id: Int) = LevelDef(
        id = id, area = (id - 1) / 5 + 1, name = "B$id", map = listOf(".E"), robots = listOf(RobotDef("A", 0, 0)),
        commands = emptyList(), maxCommands = 5, par = 2,
    )

    private fun win(cards: Int) = Outcome.Win(RunStats(ticks = cards, energyUsed = 0, bumps = 0, cards = cards))

    @Test fun unlockChain() {
        val p = Progression(SaveData(), FakeClock())
        assertTrue(p.isUnlocked(1))
        assertFalse(p.isUnlocked(2))
        p.onWin(def(1), win(2), 1)
        assertTrue(p.isUnlocked(2))
        assertFalse(p.isUnlocked(3))
    }

    @Test fun firstAreaAndReplaysAreFree() {
        val p = Progression(SaveData(), FakeClock())
        p.lives.update()
        assertFalse(p.costsLife(def(3)))
        assertTrue(p.costsLife(def(7)))
        assertTrue(p.onFail(def(7), 5))
        assertEquals(3, p.lives.lives)
        p.onWin(def(7), win(2), 3)
        assertFalse(p.costsLife(def(7)))
        assertFalse(p.onFail(def(7), 5))
        assertEquals(3, p.lives.lives)
    }

    @Test fun cannotStartNewLevelWithoutLivesButCanReplay() {
        val p = Progression(SaveData(), FakeClock())
        (1..6).forEach { p.onWin(def(it), win(2), 1) }
        repeat(4) { p.onFail(def(7), 3) }
        assertEquals(0, p.lives.lives)
        assertFalse(p.canStart(def(7)))
        assertTrue(p.canStart(def(6)))
        assertTrue(p.canStart(def(2)))
    }

    @Test fun rewardsFirstClearOnceAndStarUpgrades() {
        val save = SaveData()
        val p = Progression(save, FakeClock())
        val r1 = p.onWin(def(1), win(4), 1)
        assertTrue(r1.firstClear)
        val gold1 = save.wallet.gold
        assertEquals(Progression.firstClearReward(def(1)).gold + 5, gold1)
        val r2 = p.onWin(def(1), win(2), 3)
        assertFalse(r2.firstClear)
        assertEquals(3, save.stars(1))
        assertEquals(gold1 + 10, save.wallet.gold)
        assertTrue(r2.newBestCards)
        p.onWin(def(1), win(5), 1)
        assertEquals(3, save.stars(1)) // yıldız düşmez
        assertEquals(2, save.record(1).bestCards)
    }

    @Test fun playerLevelCurve() {
        assertEquals(1, Progression.levelForXp(0))
        assertEquals(2, Progression.levelForXp(100))
        assertEquals(3, Progression.levelForXp(240))
    }
}
