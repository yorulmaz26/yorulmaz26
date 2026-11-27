package com.example.gezegenkacisi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Can sistemi testleri: dolum süresi, oyun açık/kapalı, en fazla/sıfır can, reklam ödülü, güncelleme,
 * telefon saati/tarih/saat dilimi değişiklikleri, yeniden başlatma, internet yokluğu ve kayıt değiştirme.
 */
class LivesTest {

    private class Mem : Lives.Store {
        val m = HashMap<String, Long>()
        var writes = 0
        override fun getLong(key: String, def: Long) = m[key] ?: def
        override fun contains(key: String) = key in m
        override fun putAll(values: Map<String, Long>) { m.putAll(values); writes++ }
    }

    /** Sahte saatler: e = açılıştan beri sayaç, wall = telefon saati, net = internet saati (-1: yok). */
    private class FakeClock : Lives.Clock {
        var real = 1_700_000_000_000L   // gerçek zaman (testin "dünyası")
        var bootAt = real - 3_600_000L  // telefonun açıldığı gerçek an
        var boot = 5
        var wallOffset = 0L             // kullanıcının saati ne kadar kaydırdığı
        var online = false
        override fun elapsed() = real - bootAt
        override fun bootCount() = boot
        override fun trustedNow() = if (online) real else -1L
        override fun wallNow() = real + wallOffset
        fun pass(ms: Long) { real += ms }
        /** Telefon [offMs] kapalı kalır, sonra açılır. */
        fun reboot(offMs: Long) { real += offMs; bootAt = real; boot++ }
    }

    private val min = 60_000L
    private val regen = GameRules.LIFE_REGEN_MS
    private val max = GameRules.MAX_LIVES

    private fun fresh(): Triple<Mem, FakeClock, Lives> {
        val st = Mem(); val c = FakeClock()
        return Triple(st, c, Lives(st, c))
    }

    @Test fun regenIsTwentyMinutesFromCentralConfig() {
        assertEquals(1200, GameRules.LIFE_REGEN_SECONDS)
        assertEquals(20 * min, Lives.REGEN_MS)
    }

    @Test fun firstInstallIsFull() {
        val (_, _, l) = fresh()
        assertEquals(max, l.count)
        assertEquals(0L, l.msToNext())
    }

    @Test fun zeroToFullOneLifePerTwentyMinutes() {
        val (_, c, l) = fresh()
        repeat(max) { assertTrue(l.spend()) }
        assertEquals(0, l.count)
        assertFalse("can yokken harcanamaz", l.spend())
        assertEquals(0, l.count) // negatife düşmez
        for (i in 1..max) {
            c.pass(20 * min - 1000); l.update()
            assertEquals("${i * 20}. dakikadan hemen önce", i - 1, l.count)
            c.pass(1000); l.update()
            assertEquals("${i * 20}. dakikada", i, l.count)
        }
        c.pass(5 * 60 * min); l.update()
        assertEquals("en fazlayı aşmaz", max, l.count)
        assertEquals("doluyken sayaç durur", 0L, l.msToNext())
    }

    @Test fun timerDoesNotResetWhenAnotherLifeIsLost() {
        val (_, c, l) = fresh()
        l.spend()                         // 3/4, sayaç başlar
        c.pass(15 * min); l.update()
        assertEquals(5 * min, l.msToNext())
        l.spend()                         // 2/4: sayaç sıfırlanmamalı
        assertEquals(5 * min, l.msToNext())
        c.pass(5 * min); l.update()
        assertEquals(3, l.count)
        assertEquals(20 * min, l.msToNext())
    }

    @Test fun timerStartsWhenLosingFromFull() {
        val (_, c, l) = fresh()
        c.pass(3 * 60 * min); l.update() // uzun süre dolu kaldı
        l.spend()
        assertEquals(20 * min, l.msToNext())
    }

    @Test fun timeCountsWhileAppIsClosedOrKilled() {
        val (st, c, l) = fresh()
        l.spend(); l.spend()
        c.pass(25 * min)                  // uygulama kapalı / zorla durduruldu
        val again = Lives(st, c)          // yeniden açıldı
        assertEquals(3, again.count)
        assertEquals(15 * min, again.msToNext())
    }

    @Test fun repeatedOpenCloseGivesNothingExtra() {
        val (st, c, _) = fresh()
        var l = Lives(st, c)
        l.spend(); l.spend()
        repeat(100) { c.pass(1000); l = Lives(st, c) }
        assertEquals(2, l.count)
        assertEquals(20 * min - 100_000L, l.msToNext())
    }

    @Test fun phoneClockForwardBackDateAndTimezoneDoNothing() {
        val (_, c, l) = fresh()
        l.spend(); l.spend()
        c.wallOffset = 3 * 24 * 60 * min; l.update() // saat/tarih 3 gün ileri
        assertEquals(2, l.count)
        c.wallOffset = -400 * 24 * 60 * min; l.update() // 400 gün geri
        assertEquals(2, l.count)
        // Saat dilimi değişikliği UTC zamanı değiştirmez (wallNow UTC'dir); yine de sayaç bozulmaz
        c.pass(20 * min); l.update()
        assertEquals(3, l.count)
    }

    @Test fun rebootWithInternetCountsTheOffTimeExactly() {
        val (st, c, l) = fresh()
        c.online = true; l.update()
        repeat(4) { l.spend() }
        c.reboot(50 * min); c.online = false
        val a = Lives(st, c)              // internet yok: telefon saatiyle geçici sayım
        assertEquals(2, a.count)
        c.online = true; a.update()       // internet geldi: doğrulandı, değişiklik yok
        assertEquals(2, a.count)
        assertEquals(10 * min, a.msToNext())
    }

    @Test fun offlinePlayerIsNotPunishedAfterReboot() {
        val (st, c, l) = fresh()          // hiç internet yok
        repeat(4) { l.spend() }
        c.reboot(45 * min)
        val a = Lives(st, c)
        assertEquals(2, a.count)
        assertEquals(15 * min, a.msToNext())
    }

    @Test fun clockForwardPlusRebootIsTakenBackWhenOnline() {
        val (st, c, l) = fresh()
        c.online = true; l.update(); c.online = false
        repeat(4) { l.spend() }
        c.wallOffset = 10 * 60 * min      // hile: saati 10 saat ileri al
        c.reboot(1 * min)                 // ve yeniden başlat
        val a = Lives(st, c)
        assertEquals("geçici olarak doldu", max, a.count)
        a.spend(); a.spend(); a.spend()   // 3 can harcadı
        c.online = true; c.pass(1000); a.update()
        // Gerçekte ~1 dk geçti: fazladan verilen süre geri alınır, harcanan canlar borç olur
        assertEquals(0, a.count)
        assertTrue("borç sonraki dolumu geciktirir", a.msToNext() > 20 * min)
        c.wallOffset = 0
        c.pass(a.msToNext()); a.update()
        assertEquals(1, a.count)
    }

    @Test fun clockBackwardThenRebootGivesNoTimeButIsFixedOnline() {
        val (st, c, l) = fresh()
        c.online = true; l.update(); c.online = false
        repeat(4) { l.spend() }
        c.wallOffset = -5 * 60 * min      // saat geri alındı
        c.reboot(40 * min)
        val a = Lives(st, c)
        assertEquals("telefon saati geride: geçici süre verilmez", 0, a.count)
        c.online = true; a.update()       // gerçek süre doğrulanınca eksik süre eklenir
        assertEquals(2, a.count)
    }

    @Test fun manualEditOfSaveIsDetected() {
        val (st, c, l) = fresh()
        repeat(4) { l.spend() }
        st.m["lives_count"] = 4L          // elle değiştirildi (imza tutmaz)
        val a = Lives(st, c)
        assertTrue(a.tamperDetected)
        assertEquals(0, a.count)
        c.pass(20 * min); a.update()
        assertEquals("sonra normal dolar", 1, a.count)
    }

    @Test fun legacySaveFromOlderVersionIsKeptOnUpdate() {
        val st = Mem(); val c = FakeClock()
        // 2.2 sürümünün kaydı: imza ve telefon saati yok, 15 dakikalık düzen
        st.m.putAll(mapOf(
            "lives_init" to 1L, "lives_count" to 2L, "lives_progress" to 7 * min,
            "lives_elapsed" to c.elapsed(), "lives_boot" to 5L, "lives_trusted" to -1L,
            "lives_pending" to -1L, "lives_pcred" to 0L
        ))
        val l = Lives(st, c)
        assertFalse(l.tamperDetected)
        assertEquals("güncellemede canlar korunur", 2, l.count)
        assertEquals(13 * min, l.msToNext())
        assertTrue("artık imzalı kaydedilir", st.contains("lives_sig"))
        assertEquals(2, Lives(st, c).count)
    }

    @Test fun rewardedAdAndCoinLifeNeverExceedMax() {
        val (_, _, l) = fresh()
        assertFalse(l.refund())
        assertEquals(max, l.count)
        l.spend(); l.spend()
        assertTrue(l.refund())
        assertTrue(l.refund())
        assertFalse(l.refund())
        assertEquals(max, l.count)
        assertEquals(0L, l.msToNext())
    }

    @Test fun refillEventsAreReportedForAnimation() {
        val (_, c, l) = fresh()
        l.spend(); l.spend()
        l.consumeGained()
        c.pass(40 * min); l.update()
        assertEquals(2, l.consumeGained())
        assertEquals(0, l.consumeGained())
    }

    @Test fun everySaveIsSingleAtomicWrite() {
        val (st, c, l) = fresh()
        val before = st.writes
        l.spend(); c.pass(1000); l.update()
        assertEquals("her işlem tek bir toplu yazma", before + 3, st.writes)
        assertFalse("kayıt her an imzasıyla tutarlı", Lives(st, c).tamperDetected)
    }

    @Test fun nextTextFormat() {
        val (_, c, l) = fresh()
        l.spend()
        c.pass(18_000L); l.update()
        assertEquals("19:42", l.nextText())
    }
}
