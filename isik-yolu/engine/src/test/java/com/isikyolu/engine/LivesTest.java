package com.isikyolu.engine;

import static org.junit.Assert.*;

import org.junit.Test;

/** Can sistemi ve saat hilesi koruması. */
public class LivesTest {
    static final long MIN = 60_000L;
    static final long T0 = 1_800_000_000_000L; // duvar saati

    /** Sahte telefon: duvar saati ve monoton saat ayrı ayrı oynatılabilir. */
    static final class Phone {
        long wall = T0, elapsed = 5 * MIN, net = -1;
        int boot = 7;
        Lives.Clock clock() { return new Lives.Clock(wall, elapsed, boot, net); }
        void pass(long ms) { wall += ms; elapsed += ms; if (net >= 0) net += ms; }
        void reboot(long offMs) { wall += offMs; if (net >= 0) net += offMs; elapsed = 30_000; boot++; }
    }

    private static Progress fresh(Phone ph) {
        Progress p = new Progress();
        Lives.update(p, ph.clock());
        return p;
    }

    @Test public void startsWithFourLives() {
        Phone ph = new Phone();
        Progress p = fresh(ph);
        assertEquals(4, p.lives);
        assertEquals(0, Lives.msUntilNext(p, ph.clock()));
        // eski kayıt (can alanı yok) de 4 canla açılır
        assertEquals(4, Progress.deserialize("{\"stars\":[3,3]}").lives);
    }

    @Test public void attemptCostsLifeWinRefunds() {
        Phone ph = new Phone();
        Progress p = fresh(ph);
        assertTrue(Lives.startAttempt(p, ph.clock(), 5));
        assertEquals(3, p.lives);
        assertTrue(Lives.startAttempt(p, ph.clock(), 5)); // aynı deneme ikinci kez düşmez
        assertEquals(3, p.lives);
        Lives.winAttempt(p, ph.clock(), 5);
        assertEquals(4, p.lives);
        assertEquals(0, p.attemptLevel);
    }

    @Test public void lostAttemptsEmptyLivesAndBlock() {
        Phone ph = new Phone();
        Progress p = fresh(ph);
        for (int i = 0; i < 4; i++) {
            assertTrue(Lives.startAttempt(p, ph.clock(), 9));
            Lives.loseAttempt(p);
        }
        assertEquals(0, p.lives);
        assertFalse(Lives.startAttempt(p, ph.clock(), 9));
        assertEquals(15 * MIN, Lives.msUntilNext(p, ph.clock()), 1000);
    }

    @Test public void regeneratesEveryFifteenMinutesKeepingRemainder() {
        Phone ph = new Phone();
        Progress p = fresh(ph);
        for (int i = 0; i < 4; i++) { Lives.startAttempt(p, ph.clock(), 1); Lives.loseAttempt(p); }
        ph.pass(14 * MIN);
        Lives.update(p, ph.clock());
        assertEquals(0, p.lives);
        assertEquals(MIN, Lives.msUntilNext(p, ph.clock()), 1000);
        ph.pass(6 * MIN); // toplam 20 dk: 1 can, 5 dk ilerleme korunur
        Lives.update(p, ph.clock());
        assertEquals(1, p.lives);
        assertEquals(10 * MIN, Lives.msUntilNext(p, ph.clock()), 1000);
        ph.pass(3 * 60 * MIN); // saatler sonra: en fazla 4
        Lives.update(p, ph.clock());
        assertEquals(4, p.lives);
    }

    @Test public void movingClockForwardGivesNothing() {
        Phone ph = new Phone();
        Progress p = fresh(ph);
        for (int i = 0; i < 4; i++) { Lives.startAttempt(p, ph.clock(), 1); Lives.loseAttempt(p); }
        ph.wall += 5 * 60 * MIN; // kullanıcı saati 5 saat ileri aldı
        assertTrue("saat değişikliği algılanmalı", Lives.update(p, ph.clock()));
        assertEquals(0, p.lives);
        ph.pass(15 * MIN); // gerçek 15 dk geçince yine 1 can
        assertFalse(Lives.update(p, ph.clock()));
        assertEquals(1, p.lives);
    }

    @Test public void movingClockBackGivesNothing() {
        Phone ph = new Phone();
        Progress p = fresh(ph);
        for (int i = 0; i < 4; i++) { Lives.startAttempt(p, ph.clock(), 1); Lives.loseAttempt(p); }
        ph.wall -= 3 * 60 * MIN;
        assertTrue(Lives.update(p, ph.clock()));
        ph.pass(10 * MIN);
        Lives.update(p, ph.clock());
        assertEquals(0, p.lives);
        ph.pass(5 * MIN);
        Lives.update(p, ph.clock());
        assertEquals(1, p.lives);
    }

    @Test public void rebootUsesWallClockButNotBackwards() {
        Phone ph = new Phone();
        Progress p = fresh(ph);
        for (int i = 0; i < 4; i++) { Lives.startAttempt(p, ph.clock(), 1); Lives.loseAttempt(p); }
        ph.reboot(32 * MIN); // telefon 32 dk kapalı kaldı
        Lives.update(p, ph.clock());
        assertEquals(2, p.lives);
        // saati geri alıp yeniden başlatmak süre kazandırmaz
        ph.reboot(-120 * MIN);
        assertTrue(Lives.update(p, ph.clock()));
        assertEquals(2, p.lives);
        ph.pass(15 * MIN);
        Lives.update(p, ph.clock());
        assertEquals(3, p.lives);
    }

    @Test public void networkTimeBeatsForwardClockAcrossReboot() {
        Phone ph = new Phone();
        ph.net = T0;
        Progress p = fresh(ph);
        for (int i = 0; i < 4; i++) { Lives.startAttempt(p, ph.clock(), 1); Lives.loseAttempt(p); }
        // saat 10 saat ileri alındı ve telefon yeniden başlatıldı; ağ saati gerçek süreyi (1 dk) gösterir
        ph.reboot(MIN);
        ph.wall += 10 * 60 * MIN;
        Lives.update(p, ph.clock());
        assertEquals(0, p.lives);
    }

    @Test public void stateSurvivesSaveAndLoad() {
        Phone ph = new Phone();
        Progress p = fresh(ph);
        Lives.startAttempt(p, ph.clock(), 3);
        ph.pass(MIN);
        Progress q = Progress.deserialize(p.serialize());
        assertEquals(3, q.lives);
        assertEquals(3, q.attemptLevel);
        ph.pass(14 * MIN);
        Lives.update(q, ph.clock());
        assertEquals(4, q.lives);
    }

    @Test public void moveBudgetLimitsSession() throws Exception {
        Level l = TestUtil.pack().get(2);
        GameSession g = new GameSession(l, true);
        int budget = Lives.moveBudget(l.optimal);
        assertEquals(budget, g.budgetLeft());
        int x = -1, y = -1;
        for (Piece pc : l.pieces) if (pc.type == Cell.MIRROR && pc.rotatable) { x = pc.x; y = pc.y; }
        // aynayı çift sayıda çevirip başlangıç durumunda kal
        for (int i = 0; i < budget; i++) {
            assertTrue(g.tap(x, y) || g.isSolved());
            if (g.isSolved()) return;
            if (g.canUndo() && i % 3 == 0) g.undo(); // geri almak hakkı iade etmez
        }
        assertEquals(0, g.budgetLeft());
        if (!g.isSolved()) {
            assertTrue(g.isOutOfMoves());
            assertFalse(g.tap(x, y));
        }
        assertFalse(new GameSession(l).isOutOfMoves()); // günlük/sınırsız oturum
    }

    @Test public void trustedWallIgnoresClockChangesWithinBoot() {
        Phone ph = new Phone();
        Progress p = new Progress();
        long t1 = Lives.trustedWall(p, ph.clock());
        assertEquals(ph.wall, t1);
        ph.pass(10 * MIN);
        ph.wall += 3 * 24 * 60 * MIN; // saat 3 gün ileri
        assertEquals(t1 + 10 * MIN, Lives.trustedWall(p, ph.clock()));
        ph.wall -= 6 * 24 * 60 * MIN; // 6 gün geri
        ph.pass(MIN);
        assertEquals(t1 + 11 * MIN, Lives.trustedWall(p, ph.clock()));
        // yeniden başlatma sonrası saat geri alınmışsa güvenilir zaman geri gitmez
        ph.reboot(5 * MIN);
        assertTrue(Lives.trustedWall(p, ph.clock()) >= t1 + 11 * MIN);
        // gün hesabı
        assertEquals(Economy.dayOf(t1, 0), (int) Math.floorDiv(t1, 86_400_000L));
    }
}
