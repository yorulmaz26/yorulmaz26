package com.yanlisrenk.logic

import com.yanlisrenk.logic.life.LifeManager
import com.yanlisrenk.logic.life.LifeState
import com.yanlisrenk.logic.progress.Achievements
import com.yanlisrenk.logic.progress.Stat
import com.yanlisrenk.logic.run.EndlessGenerator
import com.yanlisrenk.logic.run.RunWorld
import com.yanlisrenk.logic.run.Track
import com.yanlisrenk.logic.level.LevelValidator
import com.yanlisrenk.logic.save.*
import com.yanlisrenk.logic.shop.*
import com.yanlisrenk.logic.time.PlatformClock
import org.junit.Assert.*
import org.junit.Test

/** Sahte cihaz saati: duvar saati, monoton saat ve açılış sayacı ayrı ayrı oynatılabilir. */
class FakeClock(var wall: Long = 1_763_500_000_000L, var elapsed: Long = 50_000L, var boot: Int = 7) : PlatformClock {
    override fun wallMillis() = wall
    override fun elapsedMillis() = elapsed
    override fun bootCount() = boot
    fun pass(ms: Long) { wall += ms; elapsed += ms }
    /** Cihaz kapalıyken [offMs] geçer, sonra [upMs] önce açılır. */
    fun reboot(offMs: Long, upMs: Long = 30_000L) { wall += offMs + upMs; elapsed = upMs; boot++ }
}

class MetaTests {
    private val MIN = 60_000L
    private fun session(storage: SaveStorage = MemoryStorage(), clock: FakeClock = FakeClock()) =
        GameSession(SaveManager(storage) { "test-install" }, clock)

    @Test fun ilkAcilistaDortCan() {
        val s = session()
        assertEquals(4, s.lives.lives); assertTrue(s.lives.isFull); assertEquals(0L, s.lives.nextLifeIn)
    }

    @Test fun canEksiltme() {
        val lm = LifeManager(LifeState())
        assertTrue(lm.onLevelFailed()); assertEquals(3, lm.lives)
        lm.onLevelFailed(); lm.onLevelFailed(); lm.onLevelFailed()
        assertEquals(0, lm.lives); assertFalse(lm.canPlay)
        // başarılı bölümde can eksilmez
        val s = session()
        s.startLevel(1); s.completeLevel(LevelResult(1, false, 3, 5, 2, 0, 7, 0))
        assertEquals(4, s.lives.lives)
        s.startLevel(2); s.failLevel(2)
        assertEquals(3, s.lives.lives)
    }

    @Test fun onBesDakikalikYenileme() {
        val lm = LifeManager(LifeState())
        repeat(4) { lm.onLevelFailed() }
        assertEquals(60 * MIN, lm.fullIn)
        lm.credit(15 * MIN - 1); assertEquals(0, lm.lives)
        lm.credit(1); assertEquals(1, lm.lives); assertEquals(45 * MIN, lm.fullIn)
        lm.credit(45 * MIN); assertEquals(4, lm.lives); assertEquals(0L, lm.nextLifeIn)
        // sayaç sıfırlanmaz: 3 can varken 10 dk geçti, sonra yeni kayıp
        val l2 = LifeManager(LifeState())
        l2.onLevelFailed(); l2.credit(10 * MIN); l2.onLevelFailed()
        assertEquals(2, l2.lives); assertEquals(5 * MIN, l2.nextLifeIn)
        l2.credit(5 * MIN); assertEquals(3, l2.lives); assertEquals(15 * MIN, l2.nextLifeIn)
        // örnekler: 0→60, 1→45, 2→30, 3→15 dk
        for (start in 0..3) {
            val l = LifeManager(LifeState(lives = start, regenRemaining = 15 * MIN))
            assertEquals((4 - start) * 15 * MIN, l.fullIn)
        }
    }

    @Test fun saatIleriAlmaCanDoldurmaz() {
        val c = FakeClock(); val st = MemoryStorage()
        val s = session(st, c)
        repeat(4) { s.startLevel(1); s.failLevel(1) }
        assertEquals(0, s.lives.lives)
        c.wall += 24 * 60 * MIN // telefon saati bir gün ileri
        c.elapsed += 1000
        s.update()
        assertEquals(0, s.lives.lives)
        assertTrue(s.data.clock.wallJumps >= 1)
        c.pass(15 * MIN); s.update()
        assertEquals(1, s.lives.lives) // gerçek geçen süre işler
    }

    @Test fun saatGeriAlmaCaniDurdurmaz() {
        val c = FakeClock(); val s = session(MemoryStorage(), c)
        s.startLevel(1); s.failLevel(1)
        c.wall -= 5 * 60 * MIN; c.elapsed += 10 * MIN; s.update()
        assertEquals(5 * MIN, s.lives.nextLifeIn)
        c.pass(5 * MIN); s.update()
        assertEquals(4, s.lives.lives)
    }

    @Test fun uygulamaYenidenAcilinca() {
        val c = FakeClock(); val st = MemoryStorage()
        val s = session(st, c)
        s.startLevel(1); s.failLevel(1); s.startLevel(1); s.failLevel(1)
        assertEquals(2, s.lives.lives)
        c.pass(20 * MIN) // uygulama kapalı, cihaz açık
        val s2 = session(st, c)
        assertEquals(3, s2.lives.lives); assertEquals(10 * MIN, s2.lives.nextLifeIn)
    }

    @Test fun cihazYenidenBaslatma() {
        val c = FakeClock(); val st = MemoryStorage()
        val s = session(st, c)
        s.applyServerTime(c.wall) // doğrulanmış çapa
        repeat(4) { s.startLevel(1); s.failLevel(1) }
        c.reboot(offMs = 50 * MIN, upMs = 10 * MIN) // 50 dk kapalı, 10 dk önce açıldı, saat ileri alınmış olabilir
        c.wall += 300 * MIN
        val s2 = session(st, c)
        // çevrimdışı: yalnızca açılıştan beri geçen 10 dk kesin → can yok, 5 dk kaldı
        assertEquals(0, s2.lives.lives); assertEquals(5 * MIN, s2.lives.nextLifeIn)
        assertTrue(s2.clock.hasUnverifiedGap)
        // internet geldi: gerçek zaman = çapa + 60 dk (saat hilesi yok sayılır)
        val realNow = c.wall - 300 * MIN
        val credit = s2.applyServerTime(realNow)
        assertEquals(50 * MIN, credit)
        assertEquals(4, s2.lives.lives)
        assertFalse(s2.clock.hasUnverifiedGap)
    }

    @Test fun yarimKalanBolumBasarisizSayilir() {
        val c = FakeClock(); val st = MemoryStorage()
        val s = session(st, c); s.startLevel(1) // uygulama burada öldürüldü
        val s2 = session(st, c)
        assertEquals(3, s2.lives.lives); assertEquals(0, s2.data.pendingAttempt)
    }

    @Test fun kalkanCaniKorur() {
        val s = session(); s.grantConsumable("boost_shield", 1)
        assertTrue(s.useConsumable("boost_shield"))
        s.startLevel(1); assertFalse(s.failLevel(1)); assertEquals(4, s.lives.lives)
        s.startLevel(1); assertTrue(s.failLevel(1)); assertEquals(3, s.lives.lives)
    }

    @Test fun marketSatinAlma() {
        val s = session()
        val robot = ShopCatalog.item("char_robot")!!
        assertEquals(PurchaseResult.NOT_ENOUGH, s.buy(robot))
        s.credit(Currency.GOLD, 2000, "test")
        val before = s.gold
        assertEquals(PurchaseResult.OK, s.buy(robot))
        assertEquals(before - robot.price, s.gold)
        assertEquals(PurchaseResult.ALREADY_OWNED, s.buy(robot))
        assertTrue(s.equip(robot)); assertEquals("char_robot", s.equipped(ShopCategory.CHARACTER).id)
        assertFalse(s.equip(ShopCatalog.item("char_panda")!!)) // sahip değil
        assertEquals(PurchaseResult.LOCKED_LEVEL, s.canBuy(ShopCatalog.item("char_altin_robot")!!).let { if (s.gold < 3000) { s.credit(Currency.GOLD, 5000, "t"); s.canBuy(ShopCatalog.item("char_altin_robot")!!) } else it })
        assertEquals(PurchaseResult.NOT_FOR_SALE, s.buy(ShopCatalog.item("char_kral")!!))
    }

    @Test fun paraBakiyesiNegatifOlmaz() {
        val s = session()
        val g = s.gold
        assertFalse(s.spend(Currency.GOLD, g + 1, "fazla")); assertEquals(g, s.gold)
        assertTrue(s.spend(Currency.GOLD, g, "hepsi")); assertEquals(0, s.gold)
        assertFalse(s.spend(Currency.DIAMOND, 1000, "x")); assertTrue(s.diamonds >= 0)
        // aynı ödül iki kez verilmez
        assertTrue(s.credit(Currency.GOLD, 100, "ödül", "test:1")); assertFalse(s.credit(Currency.GOLD, 100, "ödül", "test:1"))
        assertEquals(100, s.gold)
        assertTrue(s.data.ledger.size >= 2); assertEquals(100, s.data.ledger.last().balanceAfter)
    }

    @Test fun karakterAcma() {
        val s = session()
        assertEquals(30, ShopCatalog.characters.size)
        assertEquals(30, ShopCatalog.characters.map { it.name }.toSet().size)
        assertTrue(s.owns("char_klasik"))
        s.credit(Currency.DIAMOND, 100, "t")
        assertEquals(PurchaseResult.OK, s.buy(ShopCatalog.item("char_kristal")!!))
        assertTrue(s.owns("char_kristal"))
        // 500. bölüm başarısı Kral Küp'ü açar
        (1..500).forEach { s.data.levelStars[it] = 1 }
        s.checkAchievements()
        assertTrue(s.owns("char_kral"))
    }

    @Test fun gorevOdulu() {
        val s = session()
        val slot = s.data.missions.daily.first()
        assertEquals(3, s.data.missions.daily.size); assertEquals(5, s.data.missions.weekly.size)
        assertFalse(s.claimMission(slot, false))
        slot.progress = slot.target
        val g = s.gold
        assertTrue(s.claimMission(slot, false))
        assertEquals(g + slot.rewardGold, s.gold)
        assertFalse(s.claimMission(slot, false)) // iki kez alınamaz
        assertEquals(1L, s.data.stat(Stat.MISSIONS_DONE))
    }

    @Test fun gunlukGirisDongusu() {
        val c = FakeClock(); val s = session(MemoryStorage(), c)
        assertTrue(s.canClaimDaily()); assertNotNull(s.claimDaily()); assertNull(s.claimDaily())
        c.pass(24 * 60 * MIN); s.update(); assertEquals(1, s.dailyIndexToday())
        assertNotNull(s.claimDaily())
        c.pass(3 * 24 * 60 * MIN); s.update(); assertEquals(0, s.dailyIndexToday()) // seri bozuldu
    }

    @Test fun basarilar100denFazla() {
        assertTrue(Achievements.all.size >= 100)
        assertEquals(Achievements.all.size, Achievements.all.map { it.id }.toSet().size)
        val s = session()
        s.startLevel(1); val r = s.completeLevel(LevelResult(1, false, 3, 10, 2, 0, 7, 0))
        assertTrue(r.achievements.any { it.id == "lvl_1" })
        val a = Achievements.get("lvl_1")!!
        val g = s.gold; assertTrue(s.claimAchievement(a)); assertFalse(s.claimAchievement(a)); assertEquals(g + a.gold, s.gold)
    }

    @Test fun alanTamamlamaVeKoleksiyon() {
        val s = session()
        var last: Reward? = null
        for (id in 1..5) { s.startLevel(id); last = s.completeLevel(LevelResult(id, false, 2, 10, 1, 0, 8, 0)) }
        assertEquals(1, last!!.areaCompleted); assertEquals("Gülümseyen Lale", last.collectible)
        assertTrue(1 in s.data.collection)
        assertEquals(6, s.data.unlockedLevel)
        // tekrar oynamak alan ödülünü tekrar vermez
        val g = s.gold; s.startLevel(5); val again = s.completeLevel(LevelResult(5, false, 2, 10, 1, 0, 8, 0))
        assertEquals(0, again.areaCompleted); assertTrue(s.gold - g < 100)
    }

    @Test fun kilitliBolumBaslatilamaz() {
        val s = session(); assertFalse(s.startLevel(3)); assertTrue(s.startLevel(1))
        repeat(4) { s.failLevel(1) }; assertFalse(s.startLevel(1))
    }

    @Test fun kayitSistemi() {
        val st = MemoryStorage(); val c = FakeClock()
        val s = session(st, c)
        s.credit(Currency.GOLD, 777, "t"); s.data.levelStars[1] = 3; s.save()
        val s2 = session(st, c)
        assertEquals(s.gold, s2.gold); assertEquals(3, s2.stars(1)); assertEquals(LoadOutcome.OK, s2.loadOutcome)
        // dosyayı elle değiştirme → yedekten geri yükleme
        s2.save()
        st.files[SaveManager.FILE] = st.files[SaveManager.FILE]!!.replace("\"gold\":${s2.gold}", "\"gold\":999999")
        val s3 = session(st, c)
        assertEquals(LoadOutcome.RESTORED_BACKUP, s3.loadOutcome); assertNotEquals(999999, s3.gold)
        assertTrue(st.files.containsKey(SaveManager.FILE + ".corrupt"))
        // ikisi de bozuk → temiz başlangıç, oyun çökmez
        st.files[SaveManager.FILE] = "bozuk"; st.files[SaveManager.BACKUP] = "bozuk"
        val s4 = session(st, c)
        assertEquals(LoadOutcome.TAMPERED_RESET, s4.loadOutcome); assertEquals(4, s4.lives.lives)
    }

    @Test fun eskiKayitGocu() {
        val st = MemoryStorage(); val sm = SaveManager(st) { "x" }
        sm.writeLegacyV1("""{"version":1,"installId":"old","coins":1234,"levelStars":{"1":2}}""", "old")
        val s = GameSession(sm, FakeClock())
        assertEquals(LoadOutcome.MIGRATED, s.loadOutcome)
        assertEquals(1234, s.gold); assertEquals(2, s.stars(1)); assertEquals(SaveData.CURRENT_VERSION, s.data.version)
    }

    @Test fun sonsuzMod() {
        val gen = EndlessGenerator(42)
        val track = Track(gen.speedFor(0))
        var y = 0f
        for (i in 0 until 30) {
            val ch = gen.chunk(i)
            // her başlangıç rengi için doğrulanmış olmalı
            assertTrue("parça $i", LevelValidator.validate(ch, startColors = listOf(0, 1, 2, 3), startLastGate = listOf(0, 1, 2, 3)).ok)
            track.append(ch, y); y += ch.length
        }
        assertTrue(gen.speedFor(29) > gen.speedFor(0) * 1.8f)
        // hiç dokunmayan oyuncu kısa sürede kaybeder; sonsuz mod can kullanmaz
        val w = RunWorld(track, 0, endless = true)
        var guard = 0
        while (!w.failed && guard++ < 100_000) w.update(1 / 60f)
        assertTrue(w.failed); assertFalse(w.finished)
        val s = session()
        repeat(4) { s.startLevel(1); s.failLevel(1) }
        val r = s.completeEndless(score = 340, gates = 30, coins = 12, maxCombo = 22, taps = 40, weekly = true, magnet = false)
        assertTrue(r.newBest); assertEquals(340, s.data.endlessBest); assertEquals(0, s.lives.lives)
        assertTrue(s.claimWeeklyTier(0)); assertFalse(s.claimWeeklyTier(0)); assertFalse(s.claimWeeklyTier(1))
    }

    @Test fun ikiKisilikPistAdilVeYavas() {
        // bölünmüş ekran: 8,2 birim görüşle doğrulanmış, normal sonsuzdan yavaş ve aralıklı
        val two = EndlessGenerator.twoPlayer(99, 0.68f)
        val solo = EndlessGenerator(99)
        assertTrue(two.speedFor(0) < solo.speedFor(0) * 0.75f)
        val banned = setOf(com.yanlisrenk.logic.model.Mechanic.FOG, com.yanlisrenk.logic.model.Mechanic.HIDDEN,
            com.yanlisrenk.logic.model.Mechanic.CHAIN, com.yanlisrenk.logic.model.Mechanic.FLICKER, com.yanlisrenk.logic.model.Mechanic.MEMORY)
        for (i in 0 until 25) {
            val ch = two.chunk(i)
            assertEquals(8.2f, ch.fogDist)
            assertTrue("parça $i", LevelValidator.validate(ch, startColors = listOf(0, 1, 2, 3), startLastGate = listOf(0, 1, 2, 3)).ok)
            assertTrue(ch.mechanics.none { it in banned })
            assertTrue(ch.gates.none { it.revealDist > 0f || it.memoryDist > 0f || it.chain || it.flicker })
        }
        // ilk parçalarda kapılar arası süre daha uzun
        fun avgGap(g: EndlessGenerator): Float { val c = g.chunk(5); val tl = com.yanlisrenk.logic.level.Timeline.build(c.speed, c.zones)
            return (1 until c.gates.size).map { tl.timeAt(c.gates[it].y) - tl.timeAt(c.gates[it - 1].y) }.average().toFloat() }
        assertTrue(avgGap(two) > avgGap(solo))
    }

    @Test fun reklamKurallariAkisiBozmaz() {
        val a = com.yanlisrenk.logic.ads.AdState()
        val P = com.yanlisrenk.logic.ads.AdPolicy
        var now = 1_000_000L
        // ilk 8 bölüm boyunca hiç geçiş reklamı yok
        repeat(10) { assertFalse(P.onBreak(a, now, completedLevels = 5)); now += 600_000 }
        // sonra en az 3 molada bir
        val b = com.yanlisrenk.logic.ads.AdState()
        assertFalse(P.onBreak(b, now, 20)); assertFalse(P.onBreak(b, now, 20)); assertTrue(P.onBreak(b, now, 20))
        P.interstitialShown(b, now)
        // 3 mola geçse de 2,5 dakika dolmadan gösterilmez
        repeat(3) { assertFalse(P.onBreak(b, now + 60_000, 20)) }
        assertTrue(P.onBreak(b, now + 160_000, 20))
        // ödüllü reklam izlenen molada geçiş reklamı yok
        val c = com.yanlisrenk.logic.ads.AdState(breaksSinceInterstitial = 10)
        P.rewardedWatched(c, 5); assertFalse(P.onBreak(c, now, 20)); assertTrue(P.onBreak(c, now, 20))
        // günlük ödüllü sınır
        val d = com.yanlisrenk.logic.ads.AdState()
        repeat(6) { assertTrue(P.rewardedLeft(d, 7) > 0); P.rewardedWatched(d, 7) }
        assertEquals(0, P.rewardedLeft(d, 7)); assertEquals(6, P.rewardedLeft(d, 8))
    }

    @Test fun sonsuzModKombo() {
        // 30 aynı renk kapı: kombo 30, çarpan 4 olmalı
        val gates = (1..30).map { com.yanlisrenk.logic.model.GateData(10f + it * 6f, com.yanlisrenk.logic.model.RuleType.SAME, listOf(0)) }
        val lvl = com.yanlisrenk.logic.model.LevelData(1, 1, 0, false, 6f, 400f, 1, 0, 0.5f, gates)
        val w = RunWorld(Track.fromLevel(lvl), 0)
        while (!w.finished) w.update(1 / 60f)
        assertEquals(30, w.maxCombo); assertEquals(4, w.multiplier)
        assertTrue(w.score > 30 * 10)
    }

    @Test fun dusukPerformansSimulasyonu() {
        // düşük FPS (15 FPS, büyük dt) ile aynı bölüm yine aynı sonucu vermeli
        val repo = LevelTests.repo()
        for (id in listOf(3, 120, 333, 499)) {
            val l = repo.level(id)
            val v = LevelValidator.validate(l, coinTargetRequired = l.coinTarget)
            for (dt in listOf(1 / 15f, 1 / 30f, 1 / 144f)) {
                val w = RunWorld(Track.fromLevel(l), l.startColor)
                var ti = 0
                while (!w.finished && !w.failed) {
                    // tap zamanı kare içinde gelirse kareyi böl (oyun ekranı da girdiyi anında işler)
                    val next = v.tapTimes.getOrNull(ti)
                    if (next != null && next < w.time + dt) { w.update(maxOf(0f, next - w.time)); w.tap(); ti++ } else w.update(dt)
                }
                assertTrue("bölüm $id dt=$dt", w.finished)
            }
        }
    }
}
