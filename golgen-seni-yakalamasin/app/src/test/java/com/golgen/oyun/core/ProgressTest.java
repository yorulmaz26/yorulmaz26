package com.golgen.oyun.core;

import static org.junit.Assert.*;
import org.junit.Test;

public class ProgressTest {
    private static RunResult endless(int score, float time, int diamonds, int dodges) {
        RunResult r = new RunResult();
        r.mode = RunResult.MODE_ENDLESS;
        r.score = score; r.time = time; r.diamonds = diamonds; r.dodges = dodges;
        return r;
    }

    @Test public void saveAndLoadRoundTrip() {
        Progress p = new Progress();
        p.diamonds = 321; p.bestScore = 999; p.bestTime = 61.5f; p.unlockedSkins = 0b1011; p.selectedSkin = 3;
        p.missionsCompleted = 4; p.achievements = 0b101; p.sound = false; p.vibration = false; p.tutorialDone = true;
        p.topScores[0] = 999; p.topScores[1] = 500;
        p.ensureDaily(20000); p.dailyProgress[1] = 7; p.dailyClaimed = 2;
        MemoryStore s = new MemoryStore();
        p.save(s);
        Progress q = new Progress();
        q.load(s);
        assertEquals(321, q.diamonds);
        assertEquals(999, q.bestScore);
        assertEquals(61.5f, q.bestTime, 1e-3);
        assertEquals(0b1011, q.unlockedSkins);
        assertEquals(3, q.selectedSkin);
        assertEquals(4, q.missionsCompleted);
        assertFalse(q.sound);
        assertTrue(q.music);
        assertFalse(q.vibration);
        assertTrue(q.tutorialDone);
        assertEquals(500, q.topScores[1]);
        assertEquals(20000, q.dailyDay);
        assertEquals(7, q.dailyProgress[1]);
        assertArrayEquals(p.dailyTypes, q.dailyTypes);
        assertEquals(2, q.dailyClaimed);
    }

    @Test public void loadRejectsLockedSelectedSkin() {
        MemoryStore s = new MemoryStore();
        s.putInt("unlockedSkins", 1);
        s.putInt("selectedSkin", 5);
        Progress p = new Progress();
        p.load(s);
        assertEquals(0, p.selectedSkin);
    }

    @Test public void recordAndTopScores() {
        Progress p = new Progress();
        Progress.Report rep = new Progress.Report();
        p.applyRun(endless(300, 30, 2, 1), 1, rep);
        assertTrue(rep.newRecord);
        p.applyRun(endless(100, 10, 0, 0), 1, rep);
        assertFalse(rep.newRecord);
        p.applyRun(endless(500, 50, 1, 0), 1, rep);
        assertTrue(rep.newRecord);
        assertEquals(500, p.bestScore);
        assertArrayEquals(new int[]{500, 300, 100, 0, 0, 0, 0, 0, 0, 0}, p.topScores);
        assertEquals(3, p.gamesPlayed);
        assertTrue(p.hasAchievement(0));
    }

    @Test public void buyAndSelectSkin() {
        Progress p = new Progress();
        p.diamonds = 100;
        assertFalse(p.select(1));
        assertTrue(p.buySkin(1));
        assertEquals(40, p.diamonds);
        assertFalse("ikinci kez alınamaz", p.buySkin(1));
        assertFalse("yetersiz elmas", p.buySkin(7));
        assertTrue(p.select(1));
        assertEquals(1, p.selectedSkin);
    }

    @Test public void missionCompletionUnlocksNextAndRewardsOnce() {
        Progress p = new Progress();
        assertTrue(p.missionUnlocked(0));
        assertFalse(p.missionUnlocked(1));
        RunResult r = new RunResult();
        r.mode = RunResult.MODE_MISSION; r.missionIndex = 0; r.won = true; r.time = 20;
        Progress.Report rep = new Progress.Report();
        p.applyRun(r, 1, rep);
        assertEquals(Missions.ALL[0].reward, rep.missionReward);
        assertTrue(p.missionUnlocked(1));
        p.applyRun(r, 1, rep);
        assertTrue(rep.missionReward < Missions.ALL[0].reward);
        assertEquals(1, p.missionsCompleted);
    }

    @Test public void dailyMissionsDistinctAndClaimOnce() {
        int[] t = new int[3];
        for (int day = 19000; day < 19400; day++) {
            DailyMissions.typesFor(day, t);
            assertNotEquals(t[0], t[1]);
            assertNotEquals(t[1], t[2]);
            assertNotEquals(t[0], t[2]);
        }
        Progress p = new Progress();
        p.ensureDaily(19001);
        int idx = 0;
        int type = p.dailyTypes[idx];
        int target = DailyMissions.target(type, 19001);
        p.dailyProgress[idx] = target;
        int before = p.diamonds;
        int got = p.claimDaily(idx);
        assertTrue(got > 0);
        assertEquals(before + got, p.diamonds);
        assertEquals(0, p.claimDaily(idx));
        // Yeni gün sıfırlar
        assertTrue(p.ensureDaily(19002));
        assertFalse(p.dailyIsClaimed(idx));
        assertEquals(0, p.dailyProgress[idx]);
    }

    @Test public void dailyProgressAdvancesFromRuns() {
        Progress p = new Progress();
        p.ensureDaily(19000);
        Progress.Report rep = new Progress.Report();
        for (int i = 0; i < 10; i++) p.applyRun(endless(1500, 70, 30, 20), 19000, rep);
        for (int i = 0; i < 3; i++) assertTrue("daily " + i, p.dailyDone(i));
    }

    @Test public void resetClearsEverything() {
        Progress p = new Progress();
        p.diamonds = 50; p.bestScore = 10; p.tutorialDone = true; p.unlockedSkins = 0xFF; p.selectedSkin = 3;
        p.resetAll();
        assertEquals(0, p.diamonds);
        assertEquals(1, p.unlockedSkins);
        assertEquals(0, p.selectedSkin);
        assertFalse(p.tutorialDone);
    }
}
