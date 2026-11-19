package com.besaniyelik.dunya

import com.besaniyelik.dunya.endless.ScoreCalculator
import com.besaniyelik.dunya.level.*
import com.besaniyelik.dunya.progress.*
import com.besaniyelik.dunya.sim.Mechanic
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.*

class FixedClock(var day: Long) : GameClock { override fun epochDay() = day }

fun testLevel(id: Int = 1, coins: Int = 40) = LevelData(id, (id - 1) / 50 + 1, (id - 1) % 50 + 1, "T", GameContent.kindOf(id), 3, "x", Vec(2f, 0.4f), 100f,
    emptyList(), coins = List(coins) { Vec(it.toFloat(), 1f) }, stars = List(3) { Vec(it.toFloat(), 2f) }, phases = listOf(PhaseDef(0f, listOf(Mechanic.NORMAL))), coinTarget = (coins * 0.7f).toInt())

class ProgressTest {
    private fun service(clock: FixedClock = FixedClock(20000)): Pair<ProfileService, java.io.File> {
        val dir = Files.createTempDirectory("kayit").toFile()
        return ProfileService(SaveManager(FileSaveStorage(dir)), clock) to dir
    }

    private fun win(p: ProfileService, id: Int, stars: Int = 3, deaths: Int = 0, coins: Int = 30, piece: Boolean = false) =
        p.onLevelResult(LevelResult(testLevel(id), true, stars, coins, coins, deaths, 1800, 20, 6, 0, piece))

    @DisplayName("Skor hesaplama dengeli ve hileye karşı tutarlılık denetimli")
    @Test fun scoring() {
        val s = ScoreCalculator.endless(300f, 60f, 40, 2, 400)
        assertEquals(600 + 180 + 200 + 300 + 400, s)
        assertTrue(ScoreCalculator.plausible(s, 60f, 300f))
        assertFalse(ScoreCalculator.plausible(10_000_000, 60f, 300f))
        assertFalse(ScoreCalculator.plausible(500, 10f, 5000f))
        assertEquals(0, ScoreCalculator.levelStars(false, 3, 40, 28, 0))
        assertEquals(1, ScoreCalculator.levelStars(true, 1, 10, 28, 5))
        assertEquals(2, ScoreCalculator.levelStars(true, 2, 10, 28, 5))
        assertEquals(2, ScoreCalculator.levelStars(true, 0, 30, 28, 5))
        assertEquals(2, ScoreCalculator.levelStars(true, 3, 30, 28, 2))
        assertEquals(3, ScoreCalculator.levelStars(true, 3, 30, 28, 1))
    }

    @DisplayName("XP ve seviye sistemi: her 5 seviyede özel ödül, her 10 seviyede kozmetik")
    @Test fun xpLevels() {
        val d = SaveData()
        val ups = ProgressionManager.addXp(d, 100)
        assertEquals(1, ups.size); assertEquals(2, d.playerLevel); assertEquals(0, d.xp)
        assertEquals(50 + 20, d.coins.toInt())
        var total = 0L
        for (l in 2 until 10) total += ProgressionManager.xpToNext(l)
        ProgressionManager.addXp(d, total.toInt())
        assertEquals(10, d.playerLevel)
        assertTrue(d.gems > 0, "5. seviyede elmas ödülü")
        assertEquals(2, d.inventory[PowerupType.SHIELD], "5 ve 10. seviyede güçlendirme")
        CharacterManager.autoUnlock(d)
        assertTrue("kedi" in d.ownedSkins, "10. seviyede kozmetik")
    }

    @DisplayName("Bölüm sonucu: yıldızlar kalıcı, sonraki bölüm açılır, en iyi sonuç korunur")
    @Test fun levelProgress() {
        val (p, _) = service()
        val r = win(p, 1, stars = 3)
        assertEquals(3, r.stars); assertTrue(r.firstClear); assertEquals(2, r.nextLevelUnlocked)
        assertEquals(2, p.data.highestUnlocked)
        win(p, 1, stars = 1, deaths = 3)
        assertEquals(3, p.data.stars(1), "kötü tekrar en iyi yıldızı düşürmez")
        val fail = p.onLevelResult(LevelResult(testLevel(2), false, 1, 5, 5, 3, 600, 5, 1, 0, false))
        assertEquals(0, fail.stars); assertFalse(p.data.isCompleted(2))
        assertTrue(p.data.coins >= 65)
        assertEquals(1L, p.data.stats.levelsCompleted)
    }

    @DisplayName("Günlük (3) ve haftalık (5) görevler üretilir, ilerler ve ödül verir")
    @Test fun missions() {
        val clock = FixedClock(20000)
        val (p, _) = service(clock)
        assertEquals(3, p.data.missions.daily.size); assertEquals(5, p.data.missions.weekly.size)
        val first = p.data.missions.daily.map { it.type }
        // aynı gün aynı görevler
        p.refreshDaily(); assertEquals(first, p.data.missions.daily.map { it.type })
        // görevi tamamla
        val m = p.data.missions.daily[0]
        MissionManager.record(p.data, m.type, m.target, max = true)
        assertTrue(m.done)
        val coins = p.data.coins
        val r = p.claimMission(m.id); assertNotNull(r)
        assertEquals(coins + m.rewardCoins, p.data.coins)
        assertNull(p.claimMission(m.id), "iki kez alınamaz")
        // ertesi gün yenilenir
        clock.day++; p.refreshDaily()
        assertEquals(20001, p.data.missions.dailyKey)
        assertTrue(p.data.missions.daily.none { it.claimed })
        // yeni hafta
        clock.day += 7; p.refreshDaily(); assertEquals(clock.weekKey(), p.data.missions.weeklyKey)
    }

    @DisplayName("Günlük giriş ödülü 7 günlük döngü; gün kaçırmak ilerlemeyi sıfırlamaz")
    @Test fun loginRewards() {
        val clock = FixedClock(100)
        val (p, _) = service(clock)
        assertTrue(p.loginAvailable())
        assertNotNull(p.claimLogin()); assertEquals(100, p.data.coins.toInt())
        assertFalse(p.loginAvailable()); assertNull(p.claimLogin())
        clock.day += 3 // iki gün kaçırıldı
        assertNotNull(p.claimLogin())
        assertEquals(2, p.data.login.cycleDay)
        repeat(5) { clock.day++; p.claimLogin() }
        assertEquals(0, p.data.login.cycleDay, "7. günden sonra döngü başa döner")
        assertTrue(p.data.gems >= 35)
    }

    @DisplayName("Mağaza ve karakter kilitleri: yetersiz bakiye, satın alma, seçme, oynayarak açılan kilitler")
    @Test fun shopAndCharacters() {
        val (p, _) = service()
        assertEquals(PurchaseResult.NOT_ENOUGH_COINS, p.buySkin("damla"))
        assertFalse(p.selectSkin("damla"))
        p.data.coins = 1000
        assertEquals(PurchaseResult.OK, p.buySkin("damla")); assertEquals(600, p.data.coins.toInt())
        assertEquals(PurchaseResult.OWNED, p.buySkin("damla"))
        assertTrue(p.selectSkin("damla")); assertEquals("damla", p.data.selectedSkin)
        assertEquals(PurchaseResult.LOCKED, p.buySkin("kedi"), "seviye kilitli karakter satın alınamaz")
        assertEquals(PurchaseResult.NOT_ENOUGH_GEMS, p.buySkin("bulut"))
        assertEquals(PurchaseResult.OK, p.buyPowerup(PowerupType.SHIELD))
        assertEquals(1, p.data.inventory[PowerupType.SHIELD]); assertTrue(p.usePowerup(PowerupType.SHIELD)); assertFalse(p.usePowerup(PowerupType.SHIELD))
        p.data.gems = 30; assertEquals(PurchaseResult.OK, p.buyCoinPack(0)); assertEquals(20, p.data.gems.toInt())
        assertEquals(20, Catalog.skins.size); assertEquals(20, Catalog.skins.map { it.id }.toSet().size)
        // bütün kozmetikler oynayarak elde edilebilir: hiçbiri gerçek para gerektirmez
        assertTrue(Catalog.skins.all { it.unlock !is Unlock.Default || it.id == "pati" })
    }

    @DisplayName("Koleksiyon: 5 parça haritayı tamamlar ve ödülü açar")
    @Test fun collection() {
        val (p, _) = service()
        for (id in listOf(8, 18, 28, 38)) win(p, id, piece = true)
        assertFalse(1 in p.data.collectionsClaimed)
        val r = win(p, 48, piece = true)
        assertEquals(1, r.collectionCompleted)
        assertTrue("papatya" in p.data.ownedSkins)
        assertEquals(5, CollectionManager.piecesOf(p.data, 1))
    }

    @DisplayName("En az 50 başarı rozeti tanımlı ve koşullar sağlanınca açılır")
    @Test fun achievements() {
        assertTrue(Catalog.achievements.size >= 50)
        assertEquals(Catalog.achievements.size, Catalog.achievements.map { it.id }.toSet().size)
        val (p, _) = service()
        val r = win(p, 1)
        assertTrue(r.achievements.any { it.id == "levels_1" })
        assertTrue("levels_1" in p.data.achievements)
    }

    @DisplayName("Sonsuz mod sonucu: rekorlar, görevler, şüpheli skor reddi")
    @Test fun endlessResult() {
        val (p, _) = service()
        val r = p.onEndlessResult(EndlessResult(1500, 70f, 340f, 30, 2, 50, 12, 1))
        assertTrue(r.newRecord); assertEquals(1500L, p.data.stats.endlessBestScore)
        assertEquals(1, p.data.endlessRecords.size)
        val bad = p.onEndlessResult(EndlessResult(99_999_999, 5f, 10f, 0, 0, 0, 0, 0))
        assertTrue(bad.rejected); assertEquals(1500L, p.data.stats.endlessBestScore)
    }

    @DisplayName("Kayıt ve yükleme: veriler korunur, bozuk kayıt yedekten kurtarılır")
    @Test fun saveLoad() {
        val (p, dir) = service()
        win(p, 1); win(p, 2, stars = 2)
        p.data.coins = 777; p.data.settings.music = false; p.save()
        p.data.coins = 778; p.save() // ikinci kayıt: ilki yedeğe geçer
        val loaded = SaveManager(FileSaveStorage(dir)).load()
        assertEquals(778, loaded.coins.toInt()); assertEquals(3, loaded.stars(1)); assertEquals(2, loaded.stars(2)); assertFalse(loaded.settings.music)
        // ana kaydı boz
        java.io.File(dir, SaveManager.MAIN).writeText("{bozuk")
        val sm = SaveManager(FileSaveStorage(dir))
        val rec = sm.load()
        assertEquals("yedek", sm.lastLoadSource); assertEquals(777, rec.coins.toInt())
        // sağlama toplamı tutmayan (elle değiştirilmiş) kayıt reddedilir
        val text = java.io.File(dir, SaveManager.BACKUP).readText().replace("\\\"coins\\\":777", "\\\"coins\\\":999999")
        java.io.File(dir, SaveManager.BACKUP).writeText(text)
        val sm2 = SaveManager(FileSaveStorage(dir)); val d2 = sm2.load()
        assertEquals("yeni", sm2.lastLoadSource); assertEquals(0, d2.coins.toInt())
    }

    @DisplayName("Kayıt sürüm geçişi: eski/eksik alanlı kayıt silinmeden yüklenir")
    @Test fun migration() {
        val dir = Files.createTempDirectory("eski").toFile()
        val body = """{"version":0,"coins":50,"highestUnlocked":12,"selectedSkin":"yok"}"""
        val crc = SaveManager.checksum(body)
        java.io.File(dir, SaveManager.MAIN).writeText("""{"v":0,"crc":$crc,"data":${kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.serializer<String>(), body)}}""")
        val d = SaveManager(FileSaveStorage(dir)).load()
        assertEquals(50, d.coins.toInt()); assertEquals(12, d.highestUnlocked); assertEquals("pati", d.selectedSkin); assertEquals(SaveManager.CURRENT_VERSION, d.version)
    }
}
