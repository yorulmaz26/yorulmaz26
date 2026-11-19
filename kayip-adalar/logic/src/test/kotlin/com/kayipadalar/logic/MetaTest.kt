package com.kayipadalar.logic

import com.kayipadalar.logic.engine.*
import com.kayipadalar.logic.meta.*
import com.kayipadalar.logic.model.Res
import com.kayipadalar.logic.save.*
import org.junit.Assert.*
import org.junit.Test

class FakeTime(var wall: Long = 1_700_000_000_000L, var mono: Long = 10_000L, var boot: Long = 1) : TimeSource {
    override fun now() = TimeSample(wall, mono, boot)
    fun advance(ms: Long) { wall += ms; mono += ms }
}

class MetaTest {
    private val meta = MetaConfig.parse(TestAssets.text("data/meta.json"))
    private fun core(storage: SaveStorage = MemoryStorage(), time: FakeTime = FakeTime()) =
        GameCore(TestAssets.config, meta, SaveSystem(storage), time) { p -> java.io.File(TestAssets.dir, p).takeIf { it.exists() }?.readText() }

    // ---------------- Kayıt

    @Test fun saveRoundTripAndBackupRecovery() {
        val st = MemoryStorage()
        val sys = SaveSystem(st)
        val d = SaveData(playerName = "Test"); d.res["GOLD"] = 123
        sys.save(d)
        d.res["GOLD"] = 456
        sys.save(d) // ilk kayıt yedeğe alınır
        assertEquals(456L, sys.load().data.res["GOLD"])
        // Ana kaydı boz: yedekten (123) dönmeli
        st.files[SaveSystem.MAIN] = st.files[SaveSystem.MAIN]!!.replace("456", "999")
        val r = sys.load()
        assertEquals(LoadResult.Source.BACKUP, r.source)
        assertEquals(123L, r.data.res["GOLD"])
        assertTrue(st.exists(SaveSystem.CORRUPT))
    }

    @Test fun bothCorruptStartsNew() {
        val st = MemoryStorage()
        st.files[SaveSystem.MAIN] = "çöp"
        st.files[SaveSystem.BACKUP] = "KA|1|0\n{}"
        val r = SaveSystem(st).load()
        assertEquals(LoadResult.Source.NEW, r.source)
        assertEquals("save.reset", r.message)
    }

    @Test fun migratesVersionlessSave() {
        val body = """{"playerName":"Eski","res":{"GOLD":77}}"""
        val text = "KA|0|${SaveSystem.crc(body)}\n$body"
        val d = SaveSystem.decode(text)!!
        assertEquals(SaveSystem.CURRENT_VERSION, d.version)
        assertEquals("Eski", d.playerName)
        assertEquals(77L, d.res["GOLD"])
    }

    // ---------------- Zaman ve canlar

    @Test fun trustedClockIgnoresWallJumpWithinBoot() {
        val t = FakeTime(); val st = ClockState()
        val c = TrustedClock(st, t)
        val t0 = c.now()
        t.wall += 10 * 3600_000L // saat ileri alındı, monoton saat değişmedi
        t.mono += 1000
        assertEquals(t0 + 1000, c.now())
        assertTrue(st.suspicious > 0)
    }

    @Test fun trustedClockUsesWallAfterRebootButNeverNegative() {
        val t = FakeTime(); val c = TrustedClock(ClockState(), t)
        val t0 = c.now()
        t.boot = 2; t.mono = 500; t.wall += 3600_000L
        assertEquals(t0 + 3600_000L, c.now())
        t.boot = 3; t.mono = 100; t.wall -= 7200_000L // saat geri alındı
        assertEquals(t0 + 3600_000L, c.now())
    }

    @Test fun livesRegenerateEvery15Minutes() {
        val time = FakeTime()
        val g = core(time = time)
        val now = g.now()
        assertEquals(4, g.lives.lives(now))
        g.lives.loseLife(now); g.lives.loseLife(now)
        assertEquals(2, g.lives.lives(g.now()))
        time.advance(15 * 60_000L + 5)
        assertEquals(3, g.lives.lives(g.now()))
        time.advance(15 * 60_000L)
        assertEquals(4, g.lives.lives(g.now()))
        time.advance(60 * 60_000L)
        assertEquals(4, g.lives.lives(g.now()))
    }

    // ---------------- Bölüm akışı

    @Test fun playingLevelOneWithSolutionGivesRewardsAndUnlocks() {
        val g = core()
        val gold0 = g.wallet.get(Res.GOLD)
        val s = g.startLevel(1)!!
        for (a in s.def.solution) Rules.apply(s.board, Action.decode(a))
        assertEquals(Status.WON, s.board.status)
        val out = g.finish(s)
        assertTrue(out.won); assertTrue(out.firstClear); assertEquals(3, out.stars)
        assertTrue(g.wallet.get(Res.GOLD) > gold0)
        assertEquals(2, g.save.highestUnlocked)
        assertEquals(1L, g.save.stats[Stat.LEVELS_COMPLETED])
        // Tekrar oynamada ilk ödül verilmez, can gerekmez.
        val s2 = g.startLevel(1)!!
        assertTrue(s2.replay)
    }

    @Test fun failingNewLevelCostsLifeButReplayDoesNot() {
        val g = core()
        val s = g.startLevel(1)!!
        s.board.status = Status.LOST
        val out = g.finish(s)
        assertTrue(out.lifeLost)
        assertEquals(3, g.lives.lives(g.now()))
        repeat(3) { g.startLevel(1)!!.also { it.board.status = Status.LOST; g.finish(it) } }
        assertEquals(PlayBlock.NO_LIVES, g.playBlock(1))
        assertNull(g.startLevel(1))
    }

    @Test fun lockedLevelCannotStart() {
        val g = core()
        assertEquals(PlayBlock.LOCKED, g.playBlock(7))
    }

    @Test fun progressPersistsAcrossRestart() {
        val st = MemoryStorage()
        val g = core(st)
        val s = g.startLevel(1)!!
        s.def.solution.forEach { Rules.apply(s.board, Action.decode(it)) }
        g.finish(s)
        val g2 = core(st)
        assertEquals(2, g2.save.highestUnlocked)
        assertEquals(3, g2.record(1)!!.stars)
    }

    @Test fun challengesAvailableOnlyWhenSolvable() {
        val g = core()
        val s = g.startLevel(1)!!
        s.def.solution.forEach { Rules.apply(s.board, Action.decode(it)) }
        g.finish(s)
        val ch = g.availableChallenges(1)
        assertTrue(Challenge.TIGHT in ch)
        assertFalse(Challenge.FAST_ENEMY in ch) // düşman yok
        val cs = g.startLevel(1, Challenge.TIGHT)!!
        assertEquals(cs.def.solution.size, cs.board.moveLimit)
    }

    // ---------------- Krallık ekonomisi

    @Test fun buildingUpgradeRespectsCastleCapAndCost() {
        val g = core()
        g.wallet.add("GOLD", 100_000); g.wallet.add("WOOD", 100_000); g.wallet.add("STONE", 100_000)
        assertNull(g.upgradeBuilding("LUMBER"))
        assertEquals("error.needCastle", g.upgradeBuilding("LUMBER")) // kale 0 → azami 1
        assertNull(g.upgradeBuilding("CASTLE"))
        assertNull(g.upgradeBuilding("LUMBER"))
        val c1 = g.kingdom.upgradeCost("CASTLE").getValue("GOLD")
        assertTrue(c1 > meta.building("CASTLE").baseCost.getValue("GOLD"))
        assertEquals(3L, g.save.stats[Stat.BUILDINGS])
    }

    @Test fun idleProductionIsCappedByStorage() {
        val time = FakeTime()
        val g = core(time = time)
        time.advance(100 * 3600_000L)
        val p = g.kingdom.pendingIdle(g.now())
        val hours = g.kingdom.storageHours()
        assertEquals((10.0 * hours).toLong(), p[Res.GOLD])
        g.collectIdle()
        assertTrue(g.kingdom.pendingIdle(g.now()).isEmpty())
    }

    @Test fun researchExclusiveChoice() {
        val g = core()
        g.wallet.add("GOLD", 100_000); g.wallet.add("STONE", 100_000); g.wallet.add("IRON", 100_000); g.wallet.add("WOOD", 100_000); g.wallet.add("CRYSTAL", 1000)
        assertEquals("error.prereq", g.doResearch("ENG2"))
        assertNull(g.doResearch("ENG1")); assertNull(g.doResearch("ENG2")); assertNull(g.doResearch("ENG3A"))
        assertEquals("error.exclusive", g.doResearch("ENG3B"))
        assertNull(g.doResearch("ENG4")) // ENG3A|ENG3B koşulu
        assertEquals(1 + 1 + 2, g.bonuses.int("START_ENERGY"))
        assertEquals(4, g.modifiers().bonusEnergy)
    }

    @Test fun shopBuyEquipAndBundles() {
        val g = core()
        g.wallet.add("GOLD", 10_000)
        assertNull(g.buy("ISL_0").first)
        assertEquals("ISL_0", g.save.equipped["ISLAND_SKIN"])
        assertEquals("error.owned", g.buy("ISL_0").first)
        val wood = g.wallet.get(Res.WOOD)
        assertNull(g.buy("BND_0").first)
        assertEquals(wood + 150, g.wallet.get(Res.WOOD))
        assertEquals("error.reputation", g.buy("FRM_3").first)
    }

    @Test fun questsRefreshAndClaim() {
        val time = FakeTime()
        val g = core(time = time)
        assertEquals(3, g.save.quests.daily.size)
        assertEquals(3, g.save.quests.weekly.size)
        val q = g.save.quests.daily.first()
        val t = g.quests.template(q)
        g.quests.record(t.stat, q.target.toLong())
        assertNull(g.claimQuest(q, false))
        assertEquals("error.claimed", g.claimQuest(q, false))
        val key = g.save.quests.dailyKey
        time.advance(24 * 3600_000L)
        g.tick()
        assertTrue(g.save.quests.dailyKey > key)
    }

    @Test fun dailyResetResistsClockManipulation() {
        val time = FakeTime()
        val g = core(time = time)
        val key = g.save.quests.dailyKey
        time.wall += 3 * 24 * 3600_000L // yalnızca duvar saati ileri alındı
        g.tick()
        assertEquals(key, g.save.quests.dailyKey)
    }

    @Test fun achievementsAndCollectionCounts() {
        assertTrue(meta.allAchievements().size >= 100)
        assertEquals(100, meta.collectionItems.size)
        assertEquals(100, meta.collectionItems.map { it.first }.toSet().size)
        val g = core()
        g.save.stats[Stat.LEVELS_COMPLETED] = 5
        val claim = g.achievements.claimable().map { it.id }
        assertTrue("levelsCompleted_1" in claim && "levelsCompleted_5" in claim)
        assertNull(g.claimAchievement("levelsCompleted_5"))
    }

    @Test fun expeditionsUseTrustedTime() {
        val time = FakeTime()
        val g = core(time = time)
        g.save.highestUnlocked = 10
        assertNull(g.startExpedition("SCOUT_SHORT"))
        val run = g.save.expeditions.first()
        assertEquals("error.notDone", g.claimExpedition(run).first)
        time.wall += 3600_000L // saat oynaması
        assertEquals("error.notDone", g.claimExpedition(run).first)
        time.advance(6 * 60_000L)
        val (e, loot) = g.claimExpedition(run)
        assertNull(e); assertNotNull(loot)
        assertTrue(g.save.expeditions.isEmpty())
    }

    @Test fun randomEventChoiceAppliesCostAndReward() {
        val time = FakeTime()
        val g = core(time = time)
        g.save.highestUnlocked = 10
        var ev: RandomEventDef? = null
        repeat(10) { if (ev == null) { time.advance(30 * 60_000L); ev = g.events.maybeTrigger(g.now()) } }
        assertNotNull(ev)
        val idx = ev!!.choices.indexOfFirst { g.wallet.canAfford(it.cost) }
        assertNull(g.resolveEvent(idx))
        assertNull(g.events.pending())
    }

    @Test fun endlessAndDailyGenerateAndRecord() {
        val g = core()
        val e = g.startEndless(true)
        assertTrue(e.def.solution.isNotEmpty())
        assertEquals(Status.WON, Solver.replay(e.def, g.rules, e.def.solution).status)
        e.def.solution.forEach { Rules.apply(e.board, Action.decode(it)) }
        val out = g.finish(e)
        assertTrue(out.won); assertEquals(2, g.save.endless.current); assertEquals(1, g.save.endless.best)
        val d = g.startDaily()
        assertEquals(d.def, g.startDaily().def) // aynı gün aynı bulmaca
        d.def.solution.forEach { Rules.apply(d.board, Action.decode(it)) }
        assertTrue(g.finish(d).won)
        assertEquals(d.def.solution.size, g.save.dailyPuzzleBest[g.dailyKey()])
    }

    @Test fun defenseAttackCycle() {
        val time = FakeTime()
        val g = core(time = time)
        g.save.highestUnlocked = 60
        assertFalse(g.defenseReady())
        time.advance(7 * 3600_000L)
        assertTrue(g.defenseReady())
        val s = g.startDefense()!!
        s.def.solution.forEach { Rules.apply(s.board, Action.decode(it)) }
        assertEquals(Status.WON, s.board.status)
        assertTrue(g.finish(s).won)
        assertFalse(g.defenseReady())
        assertEquals(1, g.save.defense.wins)
    }

    @Test fun eraRequiresAllLevels() {
        val g = core()
        assertFalse(g.eraUnlocked)
        assertNull(g.startEra(1, Challenge.TIGHT))
    }
}
