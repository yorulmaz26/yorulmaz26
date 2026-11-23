package com.golgen.oyun.core;

import static org.junit.Assert.*;
import org.junit.Test;

/** Can sistemi ve saat hilesine dayanıklılık. */
public class EnergyTest {
    static final long MIN = 60_000L;

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

    @Test public void regeneratesOnePerFifteenMinutes() {
        Clock c = new Clock();
        Energy e = fresh(c);
        for (int i = 0; i < 4; i++) e.consume(c);
        c.pass(14 * MIN);
        e.tick(c);
        assertEquals(0, e.lives);
        assertEquals(MIN, e.msToNext(c));
        c.pass(MIN);
        e.tick(c);
        assertEquals(1, e.lives);
        c.pass(45 * MIN + 5000);
        e.tick(c);
        assertEquals(4, e.lives);
        c.pass(120 * MIN);
        e.tick(c);
        assertEquals("en fazla 4", 4, e.lives);
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
        assertEquals(14 * MIN, e.msToNext(c));
    }

    @Test public void movingClockBackDoesNotBreakOrSteal() {
        Clock c = new Clock();
        Energy e = fresh(c);
        e.consume(c);
        c.pass(10 * MIN);
        c.wall -= 5 * 60 * MIN;    // saati geri al
        c.pass(5 * MIN);
        e.tick(c);
        assertEquals("gerçek 15 dk geçti → can geldi", 4, e.lives);
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
        c.reboot(31 * MIN);          // telefon 31 dk kapalı kaldı
        e.tick(c);
        assertEquals("yeniden başlatmada en fazla 1 can kredisi", 1, e.lives);
        // Saati geri alıp yeniden başlatma: yalnızca açılıştan beri geçen süre sayılır
        Energy e2 = fresh(c);
        for (int i = 0; i < 4; i++) e2.consume(c);
        c.wall -= 300 * MIN;
        c.reboot(0);
        c.elapsed = 16 * MIN;        // açılıştan beri 16 dk
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
        c.reboot(20 * MIN);          // 20 dk sonra yeniden başlatıldı
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
        c.pass(8 * MIN);
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
}
