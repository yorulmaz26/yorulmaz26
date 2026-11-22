package com.isikyolu.engine;

import static org.junit.Assert.*;

import org.junit.Test;

/** Reklam sıklığı ve ödüllü video kuralları. */
public class AdPolicyTest {
    private static final long MIN = 60_000L, T0 = 1_800_000_000_000L;

    private static Progress withCompleted(int n) {
        Progress p = new Progress();
        for (int i = 1; i <= n; i++) p.stars[i] = 3;
        return p;
    }

    @Test public void tutorialLevelsDoNotCountTowardAds() {
        Progress p = withCompleted(10);
        for (int id = 1; id <= 10; id++) AdPolicy.onLevelCompleted(p, id);
        assertEquals(0, p.adLevelsSince);
        AdPolicy.onLevelCompleted(p, 0); // günlük bulmaca sayılır
        assertEquals(1, p.adLevelsSince);
    }

    @Test public void noAdsDuringTutorialOrFirstLevels() {
        Progress p = withCompleted(9);
        p.adLevelsSince = 50;
        for (int id = 1; id <= 10; id++) assertFalse("öğretici bölüm " + id, AdPolicy.interstitialDue(p, id, T0));
        assertFalse("10 bölüm bitmeden geçiş reklamı yok", AdPolicy.interstitialDue(p, 0, T0));
        assertFalse(AdPolicy.bannersAllowed(withCompleted(2)));
        assertTrue(AdPolicy.bannersAllowed(withCompleted(3)));
    }

    @Test public void interstitialEveryThirdLevelAndFourMinutesApart() {
        Progress p = withCompleted(12);
        int shown = 0;
        long now = T0;
        for (int id = 11; id <= 40; id++) {
            AdPolicy.onLevelCompleted(p, id);
            if (AdPolicy.interstitialDue(p, id, now)) { AdPolicy.onInterstitialShown(p, now); shown++; }
            now += 30_000; // her bölüm 30 sn
        }
        // 30 bölüm, 15 dakika: 4 dakikada en fazla bir reklam (ilk + 3) ve en az 3 bölüm arayla
        assertEquals(4, shown);
        Progress q = withCompleted(12);
        AdPolicy.onLevelCompleted(q, 11); AdPolicy.onLevelCompleted(q, 12);
        assertFalse("2 bölümde reklam yok", AdPolicy.interstitialDue(q, 13, T0));
        AdPolicy.onLevelCompleted(q, 13);
        assertTrue(AdPolicy.interstitialDue(q, 14, T0));
    }

    @Test public void noInterstitialRightAfterRewardedVideo() {
        Progress p = withCompleted(20);
        p.adLevelsSince = 5;
        assertTrue(AdPolicy.grant(p, AdPolicy.Reward.HINT, 100, T0, null));
        assertFalse(AdPolicy.interstitialDue(p, 21, T0 + 2 * MIN));
        assertTrue(AdPolicy.interstitialDue(p, 21, T0 + 3 * MIN + 1));
    }

    @Test public void rewardsHaveDailyLimitsAndRealEffects() {
        Progress p = new Progress();
        int hints = p.hints, kr = p.crystals;
        for (int i = 0; i < AdPolicy.Reward.HINT.dailyLimit; i++) assertTrue(AdPolicy.grant(p, AdPolicy.Reward.HINT, 100, T0, null));
        assertFalse("günlük sınır", AdPolicy.grant(p, AdPolicy.Reward.HINT, 100, T0, null));
        assertEquals(hints + AdPolicy.Reward.HINT.dailyLimit, p.hints);
        assertTrue(AdPolicy.grant(p, AdPolicy.Reward.CRYSTALS, 100, T0, null));
        assertEquals(kr + AdPolicy.VIDEO_CRYSTALS, p.crystals);
        // canlar doluyken can videosu sunulmaz; eksikse +1
        assertFalse(AdPolicy.canOffer(p, AdPolicy.Reward.LIFE, 100));
        p.lives = 1;
        assertTrue(AdPolicy.grant(p, AdPolicy.Reward.LIFE, 100, T0, null));
        assertEquals(2, p.lives);
        // ertesi gün sınır yenilenir; geri giden gün yenilemez
        assertEquals(0, AdPolicy.rewardsLeft(p, AdPolicy.Reward.HINT, 99));
        assertEquals(AdPolicy.Reward.HINT.dailyLimit, AdPolicy.rewardsLeft(p, AdPolicy.Reward.HINT, 101));
        assertEquals(AdPolicy.Reward.HINT.dailyLimit, AdPolicy.rewardsLeft(p, AdPolicy.Reward.HINT, 100));
        assertEquals(AdPolicy.Reward.HINT.dailyLimit + 2, p.adVideos);
    }

    @Test public void adStateSurvivesSaveAndLoad() {
        Progress p = withCompleted(15);
        p.adLevelsSince = 2; p.adLastWall = T0; p.adLastReward = T0 + 5;
        AdPolicy.grant(p, AdPolicy.Reward.MOVES, 77, T0, null);
        Progress q = Progress.deserialize(p.serialize());
        assertEquals(2, q.adLevelsSince);
        assertEquals(T0, q.adLastWall);
        assertEquals(p.adLastReward, q.adLastReward);
        assertEquals(77, q.adRewardDay);
        assertEquals(AdPolicy.Reward.MOVES.dailyLimit - 1, AdPolicy.rewardsLeft(q, AdPolicy.Reward.MOVES, 77));
        assertEquals(1, q.adVideos);
    }
}
