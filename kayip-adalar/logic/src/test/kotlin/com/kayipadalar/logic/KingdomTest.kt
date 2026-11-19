package com.kayipadalar.logic

import com.kayipadalar.logic.kingdom.*
import com.kayipadalar.logic.meta.TimeSample
import com.kayipadalar.logic.meta.TimeSource
import com.kayipadalar.logic.meta.TrustedClock
import com.kayipadalar.logic.save.MemoryStorage
import com.kayipadalar.logic.save.SaveSystem
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FakeTime(var wall: Long = 1_700_000_000_000L, var mono: Long = 10_000L, var boot: Long = 1) : TimeSource {
    override fun now() = TimeSample(wall, mono, boot)
    fun advance(ms: Long) { wall += ms; mono += ms }
}

class KingdomTest {
    private val dir = File(System.getProperty("assets.dir") ?: "../assets")
    private val cfg = KingdomConfig.parse(File(dir, "data/kingdom.json").readText())

    private fun fresh(): Pair<Kingdom, LongArray> {
        val k = Kingdom(cfg, KingdomSave())
        val t = longArrayOf(1_000_000L)
        k.newGame(t[0])
        return k to t
    }

    private fun Kingdom.wait(t: LongArray, sec: Long) { t[0] += sec * 1000; tick(t[0]) }
    private fun Kingdom.rich() { for (r in Res.entries) s.res[r.name] = 1_000_000.0 }

    @Test fun newGameStartsWithRuinedCastleAndFirstQuest() {
        val (k, _) = fresh()
        assertEquals(0, k.castleLevel())
        assertEquals("Ana Bina'yı onar", k.currentQuest().def.text)
        assertFalse(k.currentQuest().done)
        assertTrue(k.s.islands.size > 20)
        assertEquals(1, k.s.islands.count { it.status == IslandStatus.MERGED })
    }

    @Test fun repairCastleThenBuild() {
        val (k, t) = fresh()
        val castle = k.s.buildings.first()
        assertNotNull("Ana Bina onarılmadan bina kurulamaz", k.canBuild("lumber", 0, 1))
        assertNull(k.upgrade(castle.uid, t[0]))
        assertTrue(castle.busy)
        k.wait(t, 30)
        assertEquals(1, k.castleLevel())
        assertTrue(k.currentQuest().done)
        assertNull(k.claimQuest().first)
        assertNull(k.build("lumber", 0, 1, t[0]))
        assertNotNull("Tek inşaatçı varken ikinci iş başlamamalı", k.canBuild("farm", 0, 2))
        k.wait(t, 10)
        assertEquals(1, k.levelOf("lumber"))
    }

    @Test fun productionAccumulatesAndCollects() {
        val (k, t) = fresh()
        k.rich()
        k.upgrade(k.s.buildings.first().uid, t[0]); k.wait(t, 30)
        k.build("lumber", 0, 1, t[0]); k.wait(t, 10)
        k.s.res["WOOD"] = 0.0
        val lumber = k.s.buildings.first { it.type == "lumber" }
        k.wait(t, 600)
        val expected = k.perHour(lumber) / 6
        assertEquals(expected, lumber.stored, expected * 0.05)
        val got = k.collect(lumber.uid)
        assertTrue(got > 0)
        assertEquals(got.toDouble(), k.res(Res.WOOD), 1.0)
        k.wait(t, 100 * 3600)
        assertEquals(k.perHour(lumber) * k.r.collectCapHours, lumber.stored, 1.0) // üretim üst sınırı
    }

    @Test fun storageCapLimitsResources() {
        val (k, _) = fresh()
        k.add(Res.GOLD, 1e9)
        assertEquals(k.storageCap(), k.res(Res.GOLD), 0.1)
        k.add(Res.CRYSTAL, 5000.0)
        assertTrue(k.res(Res.CRYSTAL) > k.storageCap()) // kristal sınırsız
    }

    @Test fun buildingLevelCappedByCastle() {
        val (k, t) = fresh()
        k.rich()
        k.upgrade(k.s.buildings.first().uid, t[0]); k.wait(t, 30)
        k.build("house", 0, 1, t[0]); k.wait(t, 20)
        val house = k.s.buildings.first { it.type == "house" }
        assertEquals("Önce Ana Bina'yı 2. seviyeye yükselt.", k.canUpgrade(house))
    }

    @Test fun populationGrowsToCapAndLimitsArmy() {
        val (k, t) = fresh()
        k.rich()
        k.upgrade(k.s.buildings.first().uid, t[0]); k.wait(t, 3600)
        assertEquals(k.popCap().toDouble(), k.s.pop, 0.001)
        assertNotNull(k.canTrain("infantry", 1)) // kışla yok
    }

    @Test fun exploreAttackMergeMoveSplit() {
        val (k, t) = fresh()
        k.rich()
        val castle = k.s.buildings.first()
        k.upgrade(castle.uid, t[0]); k.wait(t, 60)
        k.upgrade(castle.uid, t[0]); k.wait(t, 120)
        assertEquals(2, k.castleLevel())
        k.build("explore", 0, 1, t[0]); k.wait(t, 60)
        val target = k.s.islands.first { it.ring == 1 }
        assertNull(k.explore(target.id, t[0]))
        assertEquals(IslandStatus.EXPLORING, target.status)
        k.wait(t, k.exploreSeconds(target).toLong() + 1)
        assertEquals(IslandStatus.DISCOVERED, target.status)
        assertEquals("Ordun yok. Kışla'dan asker eğit.", k.attack(target.id).first)
        k.build("barracks", 0, 2, t[0]); k.wait(t, 120)
        k.wait(t, 3600) // nüfus artsın
        val need = target.guard / cfg.unit("infantry").power + 1
        // nüfus yetmezse ev kur
        var plot = 3
        while (k.freePop() < need && plot < 5) { k.build("house", 0, plot++, t[0]); k.wait(t, 3600) }
        assertNull(k.train("infantry", minOf(need, k.freePop()), t[0]))
        k.wait(t, 3600)
        val (err, report) = k.attack(target.id)
        assertNull(err)
        assertTrue(report!!.win)
        assertEquals(IslandStatus.CLEARED, target.status)
        assertNull(k.merge(target.id))
        assertEquals(IslandStatus.MERGED, target.status)
        assertTrue(k.canBuild("farm", target.id, 0) == null || k.canBuild("farm", target.id, 0)!!.contains("inşaatçı").not())
        assertNull(k.moveIsland(target.id, 5))
        assertEquals(5, target.dock)
        assertNull(k.split(target.id))
        assertEquals(IslandStatus.CLEARED, target.status)
    }

    @Test fun losingBattleLosesHalfArmy() {
        val (k, _) = fresh()
        val isl = k.s.islands.last()
        isl.status = IslandStatus.DISCOVERED
        k.s.units["infantry"] = 10
        val (_, rep) = k.attack(isl.id)
        assertFalse(rep!!.win)
        assertEquals(5, k.s.units["infantry"])
        assertEquals(IslandStatus.DISCOVERED, isl.status)
    }

    @Test fun raidsArriveAndCanBeDefended() {
        val (k, t) = fresh()
        k.rich()
        k.s.buildings.first().level = 3
        k.wait(t, k.r.raidEveryMinutes * 60L + 1)
        assertNotNull(k.s.raid)
        k.s.units["cavalry"] = 50
        val rep = k.defend(t[0])!!
        assertTrue(rep.win)
        assertNull(k.s.raid)
        assertEquals(1L, k.s.stats["raidsWon"])
    }

    @Test fun researchSpeedsBuilding() {
        val (k, t) = fresh()
        k.rich()
        val before = cfg.building("house").timeFor(5, k.buildSpeed())
        k.s.research["building"] = 3
        assertTrue(cfg.building("house").timeFor(5, k.buildSpeed()) < before)
    }

    @Test fun speedupUsesCrystals() {
        val (k, t) = fresh()
        val castle = k.s.buildings.first()
        k.upgrade(castle.uid, t[0])
        val cost = k.speedupCost(castle.busyUntil - t[0])
        val crystals = k.res(Res.CRYSTAL)
        assertNull(k.speedupBuilding(castle.uid, t[0]))
        assertEquals(1, castle.level)
        assertEquals(crystals - cost, k.res(Res.CRYSTAL), 0.001)
    }

    /** Görev zincirinin ilk 22 görevi gerçek kurallarla, hileli kaynak vermeden tamamlanabilmeli. */
    @Test fun questChainIsCompletableByFollowingHints() {
        val (k, t) = fresh()
        var guard = 0
        while (k.s.questIndex < 22 && guard++ < 20000) {
            val q = k.currentQuest()
            if (q.done) { assertNull(k.claimQuest().first); continue }
            botStep(k, t, q.def)
            k.wait(t, 20)
        }
        assertTrue("Görev ${k.s.questIndex}: ${k.currentQuest().def.text} tamamlanamadı", k.s.questIndex >= 22)
        println("22 görev ${(t[0] - 1_000_000L) / 60000} dakikalık oyun süresinde tamamlandı.")
    }

    private fun freePlot(k: Kingdom): Pair<Int, Int>? {
        for (isl in k.s.islands.filter { it.status == IslandStatus.MERGED }) for (p in 0 until k.plotsOf(isl))
            if (k.buildingAt(isl.id, p) == null) return isl.id to p
        return null
    }

    private fun collectAndBuildEconomy(k: Kingdom, t: LongArray) {
        k.collectAll()
        if (k.activeBuilds() >= k.builders()) return
        for (type in listOf("lumber", "farm", "quarry", "house", "mine")) {
            if (k.countOf(type) == 0 && k.s.buildings.none { it.type == type }) {
                val p = freePlot(k) ?: break
                if (k.build(type, p.first, p.second, t[0]) == null) return
            }
        }
        // en düşük seviyeli üretim binasını yükselt
        val b = k.s.buildings.filter { it.type in setOf("lumber", "farm", "quarry", "mine", "house", "warehouse") && !it.busy }
            .minByOrNull { it.level }
        if (b != null && k.upgrade(b.uid, t[0]) == null) return
        val castle = k.s.buildings.first { it.type == "castle" }
        k.upgrade(castle.uid, t[0])
    }

    private fun botStep(k: Kingdom, t: LongArray, q: QuestDef) {
        k.collectAll()
        k.s.raid?.let { k.defend(t[0]) }
        when (q.type) {
            QuestType.LEVEL -> {
                val b = k.s.buildings.firstOrNull { it.type == q.target }
                if (b == null) freePlot(k)?.let { k.build(q.target!!, it.first, it.second, t[0]) }
                else if (k.upgrade(b.uid, t[0]) != null) {
                    val castle = k.s.buildings.first { it.type == "castle" }
                    if (b.type != "castle" && k.upgrade(castle.uid, t[0]) == null) return
                    collectAndBuildEconomy(k, t)
                }
            }
            QuestType.COUNT -> {
                val p = freePlot(k)
                val err = if (p != null) k.build(q.target!!, p.first, p.second, t[0]) else "yer yok"
                if (err != null) {
                    if (err.startsWith("Ana Bina")) k.upgrade(k.s.buildings.first { it.type == "castle" }.uid, t[0])
                    else if (p == null) mergeSomething(k, t)
                    else collectAndBuildEconomy(k, t)
                }
            }
            QuestType.COLLECT, QuestType.POP -> collectAndBuildEconomy(k, t)
            QuestType.ARMY, QuestType.POWER -> {
                val u = k.cfg.units.filter { k.unitUnlocked(it) }.maxByOrNull { it.power } ?: return
                if (k.train(u.id, k.freePop().coerceIn(1, 10), t[0]) != null) collectAndBuildEconomy(k, t)
            }
            QuestType.EXPLORE -> k.s.islands.filter { it.status == IslandStatus.HIDDEN }.minByOrNull { it.ring }?.let { k.explore(it.id, t[0]) ?: return }
                .also { collectAndBuildEconomy(k, t) }
            QuestType.CONQUER -> conquer(k, t)
            QuestType.MERGE -> mergeSomething(k, t)
            QuestType.RESEARCH -> if (k.startResearch("farming", t[0]) != null) collectAndBuildEconomy(k, t)
            QuestType.DEFEND -> {
                if (k.s.raid != null) k.defend(t[0])
                else { k.cfg.units.filter { k.unitUnlocked(it) }.maxByOrNull { it.power }?.let { k.train(it.id, k.freePop().coerceIn(1, 5), t[0]) }; collectAndBuildEconomy(k, t) }
            }
        }
    }

    private fun conquer(k: Kingdom, t: LongArray) {
        val target = k.s.islands.filter { it.status == IslandStatus.DISCOVERED }.minByOrNull { it.guard }
        if (target == null) {
            k.s.islands.filter { it.status == IslandStatus.HIDDEN && it.ring <= k.levelOf("explore") }.minByOrNull { it.ring }?.let { k.explore(it.id, t[0]) }
            collectAndBuildEconomy(k, t); return
        }
        if (k.armyPower() >= target.guard) { k.attack(target.id); return }
        val u = k.cfg.units.filter { k.unitUnlocked(it) }.maxByOrNull { it.power }
        if (u == null || k.train(u.id, k.freePop().coerceIn(1, 10), t[0]) != null) collectAndBuildEconomy(k, t)
    }

    private fun mergeSomething(k: Kingdom, t: LongArray) {
        val c = k.s.islands.firstOrNull { it.status == IslandStatus.CLEARED }
        if (c != null) k.merge(c.id) else conquer(k, t)
    }

    // ---------------- Kayıt ve saat

    @Test fun saveRoundTripBackupAndMigration() {
        val st = MemoryStorage(); val sys = SaveSystem(st)
        val (k, _) = fresh()
        sys.save(k.s); k.s.res["GOLD"] = 777.0; sys.save(k.s)
        assertEquals(777.0, sys.load().data.res["GOLD"]!!, 0.0)
        st.files[SaveSystem.MAIN] = st.files[SaveSystem.MAIN]!!.dropLast(3)
        assertEquals(com.kayipadalar.logic.save.LoadResult.Source.BACKUP, sys.load().source)
        // Eski (sürüm 1) kayıt yeni oyuna taşınır, ad korunur
        val body = """{"version":1,"playerName":"Eski Lord","res":{"GOLD":5},"levels":{}}"""
        val old = SaveSystem.decode("KA|1|${SaveSystem.crc(body)}\n$body")!!
        assertEquals("Eski Lord", old.playerName)
        assertFalse(old.created)
    }

    @Test fun gameSurvivesRestartAndOfflineProgress() {
        val st = MemoryStorage(); val time = FakeTime()
        val g1 = KingdomGame(cfg, SaveSystem(st), time)
        g1.k.upgrade(g1.save.buildings.first().uid, g1.now())
        g1.persist()
        time.advance(5 * 60_000L) // uygulama kapalıyken
        val g2 = KingdomGame(cfg, SaveSystem(st), time)
        assertEquals(1, g2.k.castleLevel())
    }

    @Test fun clockIgnoresWallJump() {
        val t = FakeTime(); val c = TrustedClock(ClockState(), t)
        val t0 = c.now()
        t.wall += 10 * 3600_000L; t.mono += 1000
        assertEquals(t0 + 1000, c.now())
    }
}
