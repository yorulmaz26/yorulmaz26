package com.besaniyelik.dunya

import com.besaniyelik.dunya.progress.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.TimeZone
import kotlin.test.*

/** Elle yönetilen saat: duvar saati, monotonik saat ve cihaz açılış sayacı ayrı ayrı değiştirilebilir. */
class FakeTime(var wall: Long = 1_800_000_000_000L, var mono: Long = 50_000_000L, var boot: Int = 7) : TimeSource {
    override fun wallMs() = wall
    override fun monoMs() = mono
    override fun bootCount() = boot
    /** Gerçekten geçen süre: iki saat birlikte ilerler. */
    fun pass(minutes: Double) { val ms = (minutes * 60_000).toLong(); wall += ms; mono += ms }
    /** Cihaz yeniden başlatıldı: monotonik saat baştan başlar, açılış sayacı artar (duvar saati sürer). */
    fun reboot(offMinutes: Double = 0.0) { wall += (offMinutes * 60_000).toLong(); mono = 5_000L; boot++ }
}

/** Kalıcı can sistemi: 20 dakikada 1 can, en fazla 3; saat oynamalarına, kapat-aç ve yeniden başlatmaya dayanıklılık. */
class LivesTest {
    private val R = GameConfig.LIFE_REGEN_SECONDS / 60.0 // dakika

    private class Env {
        val dir = Files.createTempDirectory("can").toFile()
        val time = FakeTime()
        var profile = ProfileService(SaveManager(FileSaveStorage(dir)), FixedClock(20000))
        var lives = LifeService(profile, time)
        val s get() = profile.data.lives
        /** Uygulamayı kapatıp yeniden açar (kayıttan yükler). */
        fun restart() { profile.save(); profile = ProfileService(SaveManager(FileSaveStorage(dir)), FixedClock(20000)); lives = LifeService(profile, time); lives.update() }
        /** Uygulama zorla durdurulur: son kayıttan sonraki bellek içi durum kaybolur. */
        fun kill() { profile = ProfileService(SaveManager(FileSaveStorage(dir)), FixedClock(20000)); lives = LifeService(profile, time); lives.update() }
        fun empty() { lives.update(); while (lives.lives > 0) assertTrue(lives.tryStart(50).ok) }
    }

    @DisplayName("Merkezi ayar: 1200 saniye, en fazla 3 can")
    @Test fun config() {
        assertEquals(1200L, GameConfig.LIFE_REGEN_SECONDS)
        assertEquals(3, GameConfig.MAX_LIVES)
        assertEquals(3, LifeManager.max)
    }

    @DisplayName("0 candan: 20 dk'da 1, 40 dk'da 2, 60 dk'da 3 can; sonra sayaç durur")
    @Test fun regenTimeline() {
        val e = Env(); e.empty()
        assertEquals(0, e.lives.lives)
        assertFalse(e.lives.tryStart(50).ok, "can yokken deneme başlamaz")
        e.time.pass(R - 0.1); e.lives.update(); assertEquals(0, e.lives.lives)
        e.time.pass(0.1); e.lives.update(); assertEquals(1, e.lives.lives)
        e.time.pass(R); e.lives.update(); assertEquals(2, e.lives.lives)
        e.time.pass(R); e.lives.update(); assertEquals(3, e.lives.lives)
        assertNull(e.lives.msToNext(), "canlar doluyken sayaç durur")
        e.time.pass(5 * R); e.lives.update(); assertEquals(3, e.lives.lives, "sınır aşılmaz")
        assertEquals(0L, e.s.progressMs)
    }

    @DisplayName("Can harcanınca sayaç başlar; süren sayaç yeni harcamada sıfırlanmaz")
    @Test fun timerNotReset() {
        val e = Env(); e.lives.update()
        assertTrue(e.lives.tryStart(50).spent)
        assertEquals(GameConfig.LIFE_REGEN_SECONDS * 1000, e.lives.msToNext())
        e.time.pass(15.0); e.lives.update()
        assertTrue(e.lives.tryStart(50).spent) // ikinci can harcandı
        assertEquals(5 * 60_000L, e.lives.msToNext(), "kalan süre 5 dk olmalı, baştan başlamamalı")
        e.time.pass(5.0); e.lives.update(); assertEquals(2, e.lives.lives)
        assertEquals(GameConfig.LIFE_REGEN_SECONDS * 1000, e.lives.msToNext())
    }

    @DisplayName("Oyun kapalıyken geçen süre sayılır (aynı açılış: monotonik saat)")
    @Test fun closedApp() {
        val e = Env(); e.empty(); e.restart()
        e.time.pass(45.0)
        e.restart()
        assertEquals(2, e.lives.lives)
        assertEquals(15 * 60_000L, e.lives.msToNext())
    }

    @DisplayName("Saati ileri almak can kazandırmaz (uygulama açıkken ve kapat-aç ile)")
    @Test fun clockForward() {
        val e = Env(); e.empty()
        e.time.wall += 10L * 3600_000; e.lives.update()
        assertEquals(0, e.lives.lives, "açıkken saat ileri: kazanç yok")
        e.restart(); e.time.wall += 24L * 3600_000; e.restart()
        assertEquals(0, e.lives.lives, "kapat-aç arasında saat ileri: kazanç yok")
        e.time.pass(R); e.lives.update()
        assertEquals(1, e.lives.lives, "gerçek süre yine sayılır")
    }

    @DisplayName("Saati geri almak ilerlemeyi bozmaz, eksiye düşürmez")
    @Test fun clockBackward() {
        val e = Env(); e.empty()
        e.time.pass(10.0); e.lives.update()
        e.time.wall -= 5L * 24 * 3600_000; e.lives.update()
        e.time.pass(10.0); e.lives.update()
        assertEquals(1, e.lives.lives, "toplam 20 gerçek dakika = 1 can")
        e.restart(); e.time.wall -= 3600_000; e.time.pass(1.0); e.restart()
        assertTrue(e.s.progressMs >= 0)
        assertEquals(1, e.lives.lives)
    }

    @DisplayName("Saat dilimi değişikliği etkisiz (UTC milisaniye)")
    @Test fun timezone() {
        val old = TimeZone.getDefault()
        try {
            val e = Env(); e.empty()
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati")) // UTC+14
            e.lives.update(); e.restart()
            assertEquals(0, e.lives.lives)
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Pago_Pago")) // UTC-11
            e.time.pass(R); e.restart()
            assertEquals(1, e.lives.lives)
        } finally { TimeZone.setDefault(old) }
    }

    @DisplayName("Cihaz yeniden başlatılınca duvar saatiyle devam edilir; geriye giden saat 0 sayılır")
    @Test fun reboot() {
        val e = Env(); e.empty(); e.restart()
        e.time.pass(10.0)
        e.time.reboot(offMinutes = 30.0) // 10 dk açık + 30 dk kapalı
        e.restart()
        assertEquals(2, e.lives.lives, "40 dakika = 2 can")
        e.restart(); e.time.wall -= 2 * 3600_000L; e.time.reboot(); e.restart()
        assertEquals(2, e.lives.lives, "geri giden saat can götürmez, getirmez")
        assertTrue(e.s.progressMs in 0 until LifeManager.regenMs)
    }

    @DisplayName("Zorla kapatma ve sürekli kapat-aç: süre kaybolmaz, fazladan da sayılmaz")
    @Test fun killAndRestartLoop() {
        val e = Env(); e.empty(); e.restart()
        e.time.pass(7.0); e.lives.update() // bellekte ilerledi ama kaydedilmedi
        e.kill()
        repeat(30) { e.time.pass(0.5); e.restart() } // 15 dk boyunca 30 kez kapat-aç
        assertEquals(1, e.lives.lives, "7 + 15 = 22 dk: tam 1 can")
        assertEquals(18 * 60_000L, e.lives.msToNext())
    }

    @DisplayName("Kayıttaki can değeri bozuksa (eksi, sınır üstü) güvenli aralığa çekilir")
    @Test fun sanitize() {
        val st = LivesState(lives = -4, progressMs = -100)
        LifeManager.sanitize(st); assertEquals(0, st.lives); assertEquals(0L, st.progressMs)
        val hi = LivesState(lives = 99, progressMs = 99_999_999)
        LifeManager.sanitize(hi); assertEquals(GameConfig.MAX_LIVES, hi.lives); assertEquals(0L, hi.progressMs)
        val e = Env(); e.profile.data.lives.lives = 77; e.restart()
        assertEquals(GameConfig.MAX_LIVES, e.lives.lives)
    }

    @DisplayName("Elle değiştirilmiş kayıt dosyası kabul edilmez (imza)")
    @Test fun tamperedSave() {
        val e = Env(); e.empty(); e.restart()
        val f = java.io.File(e.dir, SaveManager.MAIN)
        val txt = f.readText()
        f.writeText(txt.replace("\\\"lives\\\":0", "\\\"lives\\\":3"))
        assertNotEquals(txt, f.readText(), "deneme düzenlemesi yapılmış olmalı")
        java.io.File(e.dir, SaveManager.BACKUP).delete()
        e.kill()
        assertEquals("yeni", SaveManager(FileSaveStorage(e.dir)).also { it.load() }.lastLoadSource, "imzası tutmayan kayıt yüklenmez")
    }

    @DisplayName("Eski sürüm kaydı (can alanı yok, CRC imzalı) yüklenir; canlar dolu başlar")
    @Test fun oldSaveMigrates() {
        val dir = Files.createTempDirectory("eski").toFile()
        val body = """{"version":1,"coins":120,"gems":7,"highestUnlocked":42}"""
        val env = """{"v":1,"crc":${SaveManager.checksum(body)},"data":${kotlinx.serialization.json.JsonPrimitive(body)}}"""
        java.io.File(dir, SaveManager.MAIN).writeText(env)
        val sm = SaveManager(FileSaveStorage(dir)); val d = sm.load()
        assertEquals("ana kayıt", sm.lastLoadSource)
        assertEquals(42, d.highestUnlocked); assertEquals(120, d.coins)
        assertEquals(GameConfig.MAX_LIVES, d.lives.lives)
        assertEquals("", d.settings.language, "dil ilk açılışta telefondan seçilecek")
    }

    @DisplayName("Güncelleme sonrası can bilgisi korunur (kaydet-yükle)")
    @Test fun survivesUpdate() {
        val e = Env(); e.lives.update(); e.lives.tryStart(50); e.time.pass(3.0); e.lives.update(); e.restart()
        assertEquals(2, e.lives.lives)
        assertEquals(17 * 60_000L, e.lives.msToNext())
    }

    @DisplayName("Reklamla +1 can ve elmasla doldurma sınırı aşmaz; günlük reklam sınırı")
    @Test fun adsAndGems() {
        val e = Env(); e.empty()
        val ads = TestAds()
        val director = com.besaniyelik.dunya.ads.AdDirector(ads, e.profile) { 0L }
        assertTrue(director.lifeAdOffer(e.lives.full))
        assertTrue(director.grantLifeAd(e.lives)); assertEquals(1, e.lives.lives)
        repeat(10) { director.grantLifeAd(e.lives) }
        assertEquals(GameConfig.MAX_LIVES, e.lives.lives, "reklam sınırı aşamaz")
        assertFalse(director.lifeAdOffer(e.lives.full), "canlar doluyken reklam önerilmez")
        e.profile.data.gems = 100
        assertFalse(e.lives.refillWithGems(), "dolu canlar için elmas harcanmaz")
        e.empty(); assertTrue(e.lives.refillWithGems())
        assertEquals(GameConfig.MAX_LIVES, e.lives.lives); assertEquals(100L - GameConfig.LIFE_REFILL_GEMS, e.profile.data.gems)
        e.empty(); e.profile.data.gems = 3
        assertFalse(e.lives.refillWithGems()); assertEquals(0, e.lives.lives)
        // günlük sınır
        val e2 = Env(); val d2 = com.besaniyelik.dunya.ads.AdDirector(TestAds(), e2.profile) { 0L }
        var granted = 0
        repeat(GameConfig.LIFE_ADS_PER_DAY + 3) { e2.empty(); if (d2.grantLifeAd(e2.lives)) granted++ }
        assertEquals(GameConfig.LIFE_ADS_PER_DAY, granted)
    }

    @DisplayName("İlk bölümler ve iade kuralları: geçince ya da kalp kaybetmeden çıkınca can geri gelir")
    @Test fun costAndRefund() {
        val e = Env(); e.lives.update()
        val free = e.lives.tryStart(GameConfig.FREE_LEVELS)
        assertTrue(free.ok); assertFalse(free.spent); assertEquals(3, e.lives.lives, "ilk bölümler can harcamaz")
        val st = e.lives.tryStart(GameConfig.FREE_LEVELS + 1)
        assertTrue(st.spent); assertEquals(2, e.lives.lives)
        e.lives.refund(); assertEquals(3, e.lives.lives)
        e.lives.refund(); assertEquals(3, e.lives.lives, "iade sınırı aşmaz")
    }

    @DisplayName("Kalan süre biçimi: dakika:saniye")
    @Test fun clockFormat() {
        assertEquals("20:00", LifeManager.clock(1_200_000))
        assertEquals("19:42", LifeManager.clock(1_182_000))
        assertEquals("00:01", LifeManager.clock(1))
        assertEquals("1:00:00", LifeManager.clock(3_600_000))
    }
}
