package com.golgen.oyun.core;

import static org.junit.Assert.*;
import org.junit.Test;

/** Reklam sıklığı kuralları ve video ile can. */
public class AdPolicyTest {
    @Test public void noInterstitialForNewPlayersOrTooOften() {
        AdPolicy p = new AdPolicy();
        long t = 1_000_000L;
        for (int i = 0; i < 3; i++) p.onRunFinished(30f);
        assertFalse("ilk oyunlarda yok", p.shouldShowInterstitial(2, t));
        assertTrue(p.shouldShowInterstitial(3, t));
        p.onInterstitialShown(t);
        for (int i = 0; i < 3; i++) p.onRunFinished(30f);
        assertFalse("en az 150 sn arayla", p.shouldShowInterstitial(10, t + 60_000));
        assertTrue(p.shouldShowInterstitial(10, t + AdPolicy.MIN_GAP_MS));
    }

    @Test public void shortRunsDoNotCount() {
        AdPolicy p = new AdPolicy();
        for (int i = 0; i < 10; i++) p.onRunFinished(5f);
        assertEquals(0, p.runsSinceInterstitial());
        assertFalse(p.shouldShowInterstitial(50, 10_000_000L));
    }

    @Test public void atMostEveryThirdRun() {
        AdPolicy p = new AdPolicy();
        long t = 0;
        int shown = 0;
        for (int run = 0; run < 30; run++) {
            p.onRunFinished(60f);
            t += 200_000L; // uzun oyunlar
            if (p.shouldShowInterstitial(100, t)) { p.onInterstitialShown(t); shown++; }
        }
        assertEquals(10, shown);
    }

    @Test public void rewardedLifeLimitedPerDay() {
        EnergyTest.Clock c = new EnergyTest.Clock();
        Progress p = new Progress();
        p.energy.tick(c);
        for (int i = 0; i < 4; i++) p.energy.consume(c);
        int day = 500;
        assertTrue(p.canWatchForLife(day, c));
        for (int i = 0; i < AdPolicy.REWARDED_LIVES_PER_DAY; i++) assertTrue(p.onWatchedForLife(day, c));
        assertEquals(3, p.energy.lives);
        assertFalse("günlük sınır", p.canWatchForLife(day, c));
        assertFalse(p.onWatchedForLife(day, c));
        assertTrue("ertesi gün yenilenir", p.canWatchForLife(day + 1, c));
        p.energy.refill(c);
        assertFalse("canlar doluyken izlenmez", p.canWatchForLife(day + 1, c));
        MemoryStore s = new MemoryStore();
        p.save(s);
        Progress q = new Progress();
        q.load(s);
        assertEquals(day, q.rewardedLifeDay);
        assertEquals(3, q.rewardedLivesToday);
    }
}
