package com.golgen.oyun.core;

import static org.junit.Assert.*;
import org.junit.Test;

/** Can sistemi ve saat hilesine dayanıklılık. */
public class EnergyTest {
    static final long MIN = 60_000L;
    /** Yenilenme süresi dakika cinsinden (GameConfig: 1200 sn = 20 dk). */
    static final long R = GameConfig.LIFE_REGEN_SECONDS / 60;

    /** Sahte saat: duvar saati kullanıcıca değiştirilebilir, monoton süre değiştirilemez. */
    static final class Clock implements TimeSource {
        long wall = 1_800_000_000_000L, elapsed = 50_000L;
        int boot = 7;
        void pass(long ms) { wall += ms; elapsed += ms; }
        void reboot(long offMs) { wall += offMs; elapsed = 5_000L; boot++; }
        @Override public long wallMs() { return wall; }
        @Override public long elapsedMs() { return elapsed; }
        @Override public int bootCount() { return boot; }
    }

    private static Energy fresh(Clock c) {
        Energy e = new Energy();
        e.tick(c); // ilk kurulum
        return e;
    }

    @Test public void firstInstallGivesFour() {
        Clock c = new Clock();
        Energy e = fresh(c);
        assertEquals(4, e.lives);
        assertEquals(0, e.msToNext(c));
    }

    @Test public void dyingCostsLifeWinningRefunds() {
        Clock c = new Clock();
        Energy e = fresh(c);
        assertTrue(e.consume(c));   // bölüme başla
        assertEquals(3, e.lives);
        e.refund(c);                // kazandı
        assertEquals(4, e.lives);
        for (int i = 0; i < 4; i++) assertTrue(e.consume(c)); // 4 kez öldü
        assertEquals(0, e.lives);
        assertFalse("can yokken başlanamaz", e.consume(c));
        assertFalse(e.hasLife(c));
    }

    @Test public void regenerationIsConfiguredCentrallyAsTwentyMinutes() {
        assertEquals(1200, GameConfig.LIFE_REGEN_SECONDS);
        assertEquals(1200_000L, Energy.REGEN_MS);
        assertEquals(GameConfig.MAX_LIVES, Energy.MAX);
        assertEquals(4, Energy.MAX);
    }

    @Test public void regeneratesOnePerTwentyMinutes() {
        Clock c = new Clock();
        Energy e = fresh(c);
        for (int i = 0; i < 4; i++) e.consume(c);
        c.pass((R - 1) * MIN);
        e.tick(c);
        assertEquals(0, e.lives);
        assertEquals(MIN, e.msToNext(c));
        c.pass(MIN);
        e.tick(c);
        assertEquals(1, e.lives);
        c.pass(3 * R * MIN + 5000);
        e.tick(c);
        assertEquals(4, e.lives);
        c.pass(300 * MIN);
        e.tick(c);
        assertEquals("en fazla 4", 4, e.lives);
    }

    @Test public void zeroLivesTimelineMatchesSpec() {
        // 0 can: 20 dk → 1, 40 → 2, 60 → 3, 80 → 4 (azami); sonrası artmaz
        Clock c = new Clock();
        Energy e = fresh(c);
        for (int i = 0; i < 4; i++) e.consume(c);
        for (int k = 1; k <= 4; k++) {
            c.pass(R * MIN);
            e.tick(c);
            assertEquals(k * R + ". dakika", k, e.lives);
        }
        assertEquals("dolunca sayaç durur", 0, e.msToNext(c));
        assertEquals(0, e.progressMs);
        c.pass(R * MIN);
        e.tick(c);
        assertEquals(4, e.lives);
    }

    @Test public void losingAnotherLifeDoesNotResetRunningTimer() {
        Clock c = new Clock();
        Energy e = fresh(c);
        e.consume(c);                 // 3 can, sayaç başlar
        c.pass(12 * MIN);
        e.consume(c);                 // 2 can: süren sayaç sıfırlanmamalı
        assertEquals((R - 12) * MIN, e.msToNext(c));
        c.pass((R - 12) * MIN);
        e.tick(c);
        assertEquals(3, e.lives);
        assertEquals(R * MIN, e.msToNext(c));
    }

    @Test public void livesNeverNegativeNorAboveMax() {
        Clock c = new Clock();
        Energy e = fresh(c);
        for (int i = 0; i < 10; i++) e.consume(c);
        assertEquals(0, e.lives);
        for (int i = 0; i < 10; i++) { e.refund(c); e.addLife(c); }
        assertEquals(4, e.lives);
        MemoryStore s = new MemoryStore();
        s.putInt("enLives", -7);
        Energy f = new Energy();
        f.load(s);
        assertEquals(0, f.lives);
        s.putInt("enLives", 99);
        f.load(s);
        assertEquals(4, f.lives);
    }

    @Test public void timePassesWhileAppIsClosed() {
        // Uygulama kapalı (ve zorla durdurulmuş) iken geçen süre kayıttaki kontrol noktasından hesaplanır
        Clock c = new Clock();
        Energy e = fresh(c);
        for (int i = 0; i < 4; i++) e.consume(c);
        MemoryStore s = new MemoryStore();
        e.save(s);
        c.pass(2 * R * MIN + MIN);    // uygulama kapalı, telefon açık
        Energy f = new Energy();
        f.load(s);
        f.tick(c);
        assertEquals(2, f.lives);
        assertEquals((R - 1) * MIN, f.msToNext(c));
    }

    @Test public void movingClockForwardGivesNothing() {
        Clock c = new Clock();
        Energy e = fresh(c);
        for (int i = 0; i < 4; i++) e.consume(c);
        c.wall += 10 * 60 * MIN;   // saati 10 saat ileri al (monoton süre değişmez)
        c.pass(MIN);
        e.tick(c);
        assertEquals(0, e.lives);
        assertTrue(e.tamperCount > 0);
        assertEquals((R - 1) * MIN, e.msToNext(c));
    }

    @Test public void movingClockBackDoesNotBreakOrSteal() {
        Clock c = new Clock();
        Energy e = fresh(c);
        e.consume(c);
        c.pass(10 * MIN);
        c.wall -= 5 * 60 * MIN;    // saati geri al
        c.pass((R - 10) * MIN);
        e.tick(c);
        assertEquals("gerçek 20 dk geçti → can geldi", 4, e.lives);
    }

    @Test public void forwardThenBackwardCycleGivesNoExtraLives() {
        Clock c = new Clock();
        Energy e = fresh(c);
        for (int i = 0; i < 4; i++) e.consume(c);
        for (int k = 0; k < 10; k++) {
            c.wall += 60 * MIN; e.tick(c);
            c.wall -= 60 * MIN; e.tick(c);
        }
        assertEquals(0, e.lives);
    }

    @Test public void rebootUsesWallClockButNeverNegative() {
        Clock c = new Clock();
        Energy e = fresh(c);
        for (int i = 0; i < 4; i++) e.consume(c);
        c.reboot((2 * R + 1) * MIN); // telefon 41 dk kapalı kaldı
        e.tick(c);
        assertEquals("yeniden başlatmada en fazla 1 can kredisi", 1, e.lives);
        // Saati geri alıp yeniden başlatma: yalnızca açılıştan beri geçen süre sayılır
        Energy e2 = fresh(c);
        for (int i = 0; i < 4; i++) e2.consume(c);
        c.wall -= 300 * MIN;
        c.reboot(0);
        c.elapsed = (R + 1) * MIN;   // açılıştan beri 21 dk
        e2.tick(c);
        assertEquals(1, e2.lives);
        assertTrue(e2.tamperCount > 0);
    }

    @Test public void clockChangeWhileAppClosedThenRebootIsDetectedByTrustedWall() {
        Clock c = new Clock();
        Energy e = fresh(c);
        for (int i = 0; i < 4; i++) e.consume(c);
        c.wall += 600 * MIN;         // aynı açılışta saat ileri alındı
        e.tick(c);                   // tespit edilir, güvenilir saat ilerletilmez
        assertEquals(0, e.lives);
        c.wall -= 600 * MIN;         // saat düzeltildi
        c.reboot(R * MIN);           // 20 dk sonra yeniden başlatıldı
        e.tick(c);
        assertEquals(1, e.lives);
    }

    @Test public void rebootPlusClockForwardGivesAtMostOneLife() {
        Clock c = new Clock();
        Energy e = fresh(c);
        for (int i = 0; i < 4; i++) e.consume(c);
        for (int round = 0; round < 3; round++) {
            int before = e.lives;
            c.reboot(0);               // gerçek süre ~0
            c.wall += 60 * MIN;        // açılır açılmaz saat 1 saat ileri
            e.tick(c);
            assertTrue("tur " + round, e.lives - before <= 1);
        }
        assertTrue(e.lives < 4);
    }

    @Test public void trustedDayIgnoresDateChangesAndNeverGoesBack() {
        Clock c = new Clock();
        Progress p = new Progress();
        java.time.ZoneId z = java.time.ZoneOffset.UTC;
        int d0 = p.trustedDay(c, z);
        assertEquals(d0, p.trustedDay(c, z));
        // Aynı açılışta tarihi ileri/geri almak gün değiştirmez
        c.wall += 3L * 24 * 60 * MIN;
        assertEquals(d0, p.trustedDay(c, z));
        c.wall -= 10L * 24 * 60 * MIN;
        assertEquals(d0, p.trustedDay(c, z));
        // Gerçekten 1 gün geçince gün ilerler
        c.pass(24 * 60 * MIN);
        assertEquals(d0 + 1, p.trustedDay(c, z));
        // Yeniden başlatıp tarihi geri almak günü geri götürmez
        c.wall -= 5L * 24 * 60 * MIN;
        c.reboot(0);
        assertEquals(d0 + 1, p.trustedDay(c, z));
        // Günlük giriş ödülü aynı güvenilir günde ikinci kez alınamaz
        int day = p.trustedDay(c, z);
        assertTrue(p.claimLogin(day) > 0);
        c.wall -= 2L * 24 * 60 * MIN;
        assertEquals(0, p.claimLogin(p.trustedDay(c, z)));
    }

    /**
     * Sunucusuz engellenemeyen tek durum belgelendiği gibi sınırlı: kapat-aç + tarihi ileri almak
     * yalnızca BİR yeni ödül günü açar; aynı açılışta tarihle oynamak ya da geri almak başka gün açmaz.
     */
    @Test public void rebootWithDateForwardOpensOneRewardDayAndNothingMore() {
        Clock c = new Clock();
        Progress p = new Progress();
        java.time.ZoneId z = java.time.ZoneOffset.UTC;
        int d0 = p.trustedDay(c, z);
        assertTrue(p.claimLogin(d0) > 0);
        c.reboot(0);
        c.wall += 3L * 24 * 60 * MIN;
        int d1 = p.trustedDay(c, z);
        assertEquals(d0 + 3, d1);
        assertTrue(p.claimLogin(d1) > 0);
        c.wall += 5L * 24 * 60 * MIN;
        assertEquals(0, p.claimLogin(p.trustedDay(c, z)));
        c.wall -= 9L * 24 * 60 * MIN;
        assertEquals(0, p.claimLogin(p.trustedDay(c, z)));
        assertEquals(d1, p.trustedDay(c, z));
    }

    @Test public void saveLoadRoundTrip() {
        Clock c = new Clock();
        Energy e = fresh(c);
        e.consume(c); e.consume(c);
        c.pass(7 * MIN);
        e.tick(c);
        MemoryStore s = new MemoryStore();
        e.save(s);
        Energy f = new Energy();
        f.load(s);
        assertEquals(2, f.lives);
        assertEquals(e.progressMs, f.progressMs);
        c.pass((R - 7) * MIN);
        f.tick(c);
        assertEquals(3, f.lives);
    }

    @Test public void progressSavesEnergyAndRefillCostsDiamonds() {
        Clock c = new Clock();
        Progress p = new Progress();
        p.energy.tick(c);
        p.energy.consume(c);
        p.diamonds = Progress.REFILL_COST - 1;
        assertFalse(p.buyRefill(c));
        p.diamonds = Progress.REFILL_COST + 5;
        assertTrue(p.buyRefill(c));
        assertEquals(4, p.energy.lives);
        assertEquals(5, p.diamonds);
        assertFalse("dolu iken alınmaz", p.buyRefill(c));
        MemoryStore s = new MemoryStore();
        p.energy.consume(c);
        p.save(s);
        Progress q = new Progress();
        q.load(s);
        assertEquals(3, q.energy.lives);
    }

    // ---------------- Kayıt güvenliği ----------------
    @Test public void signedSaveRoundTripIsAccepted() {
        Clock c = new Clock();
        Progress p = new Progress();
        p.energy.tick(c);
        p.energy.consume(c);
        p.diamonds = 321;
        p.language = "de";
        MemoryStore s = new MemoryStore();
        p.save(s);
        assertNotNull(s.map.get("sig"));
        Progress q = new Progress();
        q.load(s);
        assertFalse(q.saveTampered);
        assertEquals(321, q.diamonds);
        assertEquals(3, q.energy.lives);
        assertEquals("de", q.language);
    }

    @Test public void editedSaveFileIsDetectedAndEconomyReset() {
        Clock c = new Clock();
        Progress p = new Progress();
        p.energy.tick(c);
        for (int i = 0; i < 4; i++) p.energy.consume(c);
        p.diamonds = 10;
        p.missionsCompleted = 37;
        MemoryStore s = new MemoryStore();
        p.save(s);
        s.putInt("enLives", 4);        // kayıt dosyası elle düzenlendi
        s.putInt("diamonds", 99999);
        Progress q = new Progress();
        q.load(s);
        assertTrue(q.saveTampered);
        assertEquals(0, q.energy.lives);
        assertEquals(0, q.diamonds);
        assertEquals("ilerleme korunur", 37, q.missionsCompleted);
        // Silinen imza (sürüm 3) de yakalanır
        p.save(s);
        s.map.remove("sig");
        Progress r = new Progress();
        r.load(s);
        assertTrue(r.saveTampered);
    }

    @Test public void updateFromOlderUnsignedSaveKeepsLives() {
        // Önceki sürümün (2) imzasız kaydı: canlar ve sayaç korunur, sıfırlanmaz
        MemoryStore s = new MemoryStore();
        s.putInt("version", 2);
        s.putInt("diamonds", 50);
        s.putInt("enLives", 2);
        s.putString("enProg", Long.toString(5 * MIN));
        s.putString("enWall", "1800000000000");
        s.putString("enElapsed", "50000");
        s.putInt("enBoot", 7);
        Progress q = new Progress();
        q.load(s);
        assertFalse(q.saveTampered);
        assertEquals(2, q.energy.lives);
        assertEquals(5 * MIN, q.energy.progressMs);
        assertEquals(50, q.diamonds);
        q.save(s);                      // yeni sürümde imzalanır
        Progress r = new Progress();
        r.load(s);
        assertFalse(r.saveTampered);
        assertEquals(2, r.energy.lives);
    }

    @Test public void timeZoneChangeDoesNotChangeLivesAndGivesAtMostOneDay() {
        // Canlar UTC milisaniye + monoton saatle hesaplanır: saat dilimi değişikliği can vermez.
        Clock c = new Clock();
        Progress p = new Progress();
        p.energy.tick(c);
        for (int i = 0; i < 4; i++) p.energy.consume(c);
        int d0 = p.trustedDay(c, java.time.ZoneId.of("Pacific/Pago_Pago"));   // UTC-11
        int d1 = p.trustedDay(c, java.time.ZoneId.of("Pacific/Kiritimati"));  // UTC+14
        assertTrue(d1 - d0 <= 1);
        int back = p.trustedDay(c, java.time.ZoneId.of("Pacific/Pago_Pago"));
        assertEquals("gün geri gitmez", d1, back);
        assertEquals(0, p.energy.lives);
        assertEquals(R * MIN, p.energy.msToNext(c));
    }
}
