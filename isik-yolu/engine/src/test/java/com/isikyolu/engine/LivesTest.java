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
        assertEquals(20 * MIN, Lives.msUntilNext(p, ph.clock()), 1000);
    }

    @Test public void configIsTwentyMinutes() {
        assertEquals(1200, GameConfig.LIFE_REGEN_SECONDS);
        assertEquals(20 * MIN, Lives.REGEN_MS);
        assertEquals(4, Lives.MAX);
        assertEquals(20, Lives.regenMinutes());
    }

    @Test public void regeneratesEveryTwentyMinutesKeepingRemainder() {
        Phone ph = new Phone();
        Progress p = fresh(ph);
        for (int i = 0; i < 4; i++) { Lives.startAttempt(p, ph.clock(), 1); Lives.loseAttempt(p); }
        ph.pass(19 * MIN);
        Lives.update(p, ph.clock());
        assertEquals(0, p.lives);
        assertEquals(MIN, Lives.msUntilNext(p, ph.clock()), 1000);
        ph.pass(6 * MIN); // toplam 25 dk: 1 can, 5 dk ilerleme korunur
        Lives.update(p, ph.clock());
        assertEquals(1, p.lives);
        assertEquals(15 * MIN, Lives.msUntilNext(p, ph.clock()), 1000);
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
        ph.pass(20 * MIN); // gerçek 20 dk geçince yine 1 can
        assertFalse(Lives.update(p, ph.clock()));
        assertEquals(1, p.lives);
    }

    @Test public void movingClockBackGivesNothing() {
        Phone ph = new Phone();
        Progress p = fresh(ph);
        for (int i = 0; i < 4; i++) { Lives.startAttempt(p, ph.clock(), 1); Lives.loseAttempt(p); }
        ph.wall -= 3 * 60 * MIN;
        assertTrue(Lives.update(p, ph.clock()));
        ph.pass(15 * MIN);
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
        ph.reboot(42 * MIN); // telefon 42 dk kapalı kaldı
        Lives.update(p, ph.clock());
        assertEquals(2, p.lives);
        // saati geri alıp yeniden başlatmak süre kazandırmaz
        ph.reboot(-120 * MIN);
        assertTrue(Lives.update(p, ph.clock()));
        assertEquals(2, p.lives);
        ph.pass(20 * MIN);
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
        ph.pass(19 * MIN);
        Lives.update(q, ph.clock());
        assertEquals(4, q.lives);
    }

    // ---------- 1.8.0: 20 dakikalık yenilenme, sayaç sürekliliği, kayıt koruması ----------

    @Test public void zeroToFullTakesEightyMinutes() {
        Phone ph = new Phone();
        Progress p = fresh(ph);
        for (int i = 0; i < 4; i++) { Lives.startAttempt(p, ph.clock(), 1); Lives.loseAttempt(p); }
        for (int k = 1; k <= 4; k++) {
            ph.pass(20 * MIN - 1000);
            Lives.update(p, ph.clock());
            assertEquals("20 dk dolmadan can gelmez", k - 1, p.lives);
            ph.pass(1000);
            Lives.update(p, ph.clock());
            assertEquals(k, p.lives);
        }
        assertEquals("doluyken sayaç durur", 0, Lives.msUntilNext(p, ph.clock()));
        ph.pass(5 * 60 * MIN);
        Lives.update(p, ph.clock());
        assertEquals("en fazla 4", 4, p.lives);
    }

    @Test public void furtherLossDoesNotResetRunningTimer() {
        Phone ph = new Phone();
        Progress p = fresh(ph);
        Lives.startAttempt(p, ph.clock(), 1); Lives.loseAttempt(p); // 3/4: sayaç başladı
        ph.pass(12 * MIN);
        assertEquals(8 * MIN, Lives.msUntilNext(p, ph.clock()), 1000);
        Lives.startAttempt(p, ph.clock(), 2); Lives.loseAttempt(p); // 2/4: sayaç sıfırlanmaz
        assertEquals(2, p.lives);
        assertEquals(8 * MIN, Lives.msUntilNext(p, ph.clock()), 1000);
        ph.pass(8 * MIN);
        Lives.update(p, ph.clock());
        assertEquals(3, p.lives);
        assertEquals("sonraki can için yeni 20 dk", 20 * MIN, Lives.msUntilNext(p, ph.clock()), 1000);
    }

    @Test public void winningOrRewardKeepsTimerProgress() {
        Phone ph = new Phone();
        Progress p = fresh(ph);
        for (int i = 0; i < 2; i++) { Lives.startAttempt(p, ph.clock(), 1); Lives.loseAttempt(p); }
        ph.pass(5 * MIN);
        // reklam/market ödülü: +1 can, yarım kalan sayaç korunur
        Economy.addLives(p, 1, ph.clock());
        assertEquals(3, p.lives);
        assertEquals(15 * MIN, Lives.msUntilNext(p, ph.clock()), 1000);
        // ödül canı dolu sayıyı aşmaz
        Economy.addLives(p, 9, ph.clock());
        assertEquals(4, p.lives);
        assertEquals(0, Lives.msUntilNext(p, ph.clock()));
    }

    @Test public void timePassesWhileAppClosedAndRestarted() {
        Phone ph = new Phone();
        Progress p = fresh(ph);
        for (int i = 0; i < 4; i++) { Lives.startAttempt(p, ph.clock(), 1); Lives.loseAttempt(p); }
        String saved = p.serialize();
        // uygulama kapatıldı (zorla durdurma dahil); 47 dk sonra aynı açılışta yeniden açıldı
        ph.pass(47 * MIN);
        Progress q = Progress.deserialize(saved);
        assertFalse(q.saveTampered);
        Lives.update(q, ph.clock());
        assertEquals(2, q.lives);
        assertEquals(13 * MIN, Lives.msUntilNext(q, ph.clock()), 1000);
        // art arda aç/kapat: süre ne kazandırır ne kaybettirir
        for (int i = 0; i < 30; i++) {
            q = Progress.deserialize(q.serialize());
            Lives.update(q, ph.clock());
            ph.pass(1000);
        }
        assertEquals(2, q.lives);
        assertEquals(13 * MIN - 30_000, Lives.msUntilNext(q, ph.clock()), 1500);
    }

    @Test public void rebootWithoutNetworkCountsOffTime() {
        Phone ph = new Phone();
        Progress p = fresh(ph);
        for (int i = 0; i < 4; i++) { Lives.startAttempt(p, ph.clock(), 1); Lives.loseAttempt(p); }
        ph.pass(10 * MIN);
        Progress q = Progress.deserialize(p.serialize());
        ph.reboot(30 * MIN); // internetsiz, telefon kapalı 30 dk: toplam 40 dk
        Lives.update(q, ph.clock());
        assertEquals(2, q.lives);
        assertEquals(20 * MIN, Lives.msUntilNext(q, ph.clock()), 1000);
    }

    @Test public void timeZoneChangeHasNoEffect() {
        // saat dilimi duvar saatini (UTC milisaniye) değiştirmez; yalnızca gösterim değişir
        Phone ph = new Phone();
        Progress p = fresh(ph);
        Lives.startAttempt(p, ph.clock(), 1); Lives.loseAttempt(p);
        ph.pass(10 * MIN);
        assertFalse(Lives.update(p, ph.clock()));
        assertEquals(10 * MIN, Lives.msUntilNext(p, ph.clock()), 1000);
        // tarih değişikliği (saat 1 gün ileri) aynı açılışta algılanır, can vermez
        ph.wall += 24 * 60 * MIN;
        assertTrue(Lives.update(p, ph.clock()));
        assertEquals(3, p.lives);
        assertEquals(10 * MIN, Lives.msUntilNext(p, ph.clock()), 1000);
    }

    @Test public void livesNeverNegativeOrAboveMax() {
        Phone ph = new Phone();
        Progress p = fresh(ph);
        p.lives = -3;
        Lives.update(p, ph.clock());
        assertEquals(0, p.lives);
        p.lives = 99;
        Lives.update(p, ph.clock());
        assertEquals(4, p.lives);
        assertEquals(0, Progress.deserialize("{\"lives\":-5}").lives);
        assertEquals(4, Progress.deserialize("{\"lives\":50}").lives);
    }

    @Test public void editedSaveIsDetectedButProgressKept() {
        Phone ph = new Phone();
        Progress p = fresh(ph);
        p.stars[1] = 3; p.stars[2] = 2; p.gems = 120; p.crystals = 300;
        p.owned.add("mirror_gold");
        for (int i = 0; i < 4; i++) { Lives.startAttempt(p, ph.clock(), 1); Lives.loseAttempt(p); }
        String saved = p.serialize();
        String sig = Progress.signature(saved);
        assertFalse("kayıt düz JSON (eski sürümler de okur)", saved.contains("#sig"));
        // kayıt dosyasında can ve kristal elle değiştirildi
        String hacked = saved.replace("\"lives\":0", "\"lives\":4").replace("\"kr\":300", "\"kr\":99999");
        assertNotEquals(saved, hacked);
        Progress q = Progress.load(hacked, sig);
        assertTrue(q.saveTampered);
        assertEquals("can güvenli değere döner", 0, q.lives);
        assertEquals("değiştirilmiş kristal kullanılamaz", Economy.START_CRYSTALS, q.crystals);
        assertEquals(0, q.gems);
        assertEquals("bölüm ilerlemesi korunur", 3, q.stars[1]);
        assertEquals(2, q.stars[2]);
        assertTrue("görünümler korunur", q.owned.contains("mirror_gold"));
        // can normal hızla yeniden dolar
        Lives.update(q, ph.clock());
        ph.pass(20 * MIN);
        Lives.update(q, ph.clock());
        assertEquals(1, q.lives);
        // imzayı silmek de işe yaramaz (yeni biçimdeki kayıt imzasız olamaz)
        assertTrue(Progress.load(hacked, null).saveTampered);
        // sağlam kayıt sorunsuz yüklenir, hiçbir şey değişmez
        Progress ok = Progress.load(saved, sig);
        assertFalse(ok.saveTampered);
        assertEquals(300, ok.crystals);
        assertEquals(120, ok.gems);
    }

    @Test public void oldUnsignedSaveMigratesWithoutPenalty() {
        // 1.7.1 kaydı: imza ve "sv" alanı yok
        String old = "{\"stars\":[3,2,1],\"gem\":40,\"lives\":2}";
        Progress q = Progress.load(old, null);
        assertFalse(q.saveTampered);
        assertEquals(2, q.lives);
        assertEquals(40, q.gems);
        assertEquals(3, q.stars[1]);
        // sonraki kayıt imzayla yazılır ve yeniden yüklenince temizdir
        String json = q.serialize();
        Progress r = Progress.load(json, Progress.signature(json));
        assertFalse(r.saveTampered);
        assertEquals(2, r.lives);
    }

    @Test public void newSaveReadableByOldParser() {
        // geri dönüş güvenliği: 1.8.0 kaydı düz JSON'dur; eski sürümün ayrıştırıcısı (Json.parse) okuyabilir
        Phone ph = new Phone();
        Progress p = fresh(ph);
        p.stars[1] = 3; p.crystals = 999;
        Object m = Json.parse(p.serialize());
        assertTrue(m instanceof java.util.Map);
        assertEquals(999, ((Number) ((java.util.Map<?, ?>) m).get("kr")).intValue());
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
