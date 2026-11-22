package com.isikyolu.engine;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

/** Test 9: kayıt ve yükleme, kilit açma ve başarılar. */
public class ProgressTest {

    @Test public void roundTrip() {
        Progress p = new Progress();
        p.recordLevel(1, 3, 2, false);
        p.recordLevel(2, 2, 7, true);
        p.useHint();
        p.dailyDone.add("2026-11-18");
        p.sound = false; p.vibration = false; p.tutorialSeen = true; p.undoCount = 4;
        Achievements.update(p);
        String s = p.serialize();
        Progress q = Progress.deserialize(s);
        assertEquals(s, q.serialize());
        assertEquals(3, q.stars[1]);
        assertEquals(2, q.stars[2]);
        assertEquals(7, q.bestMoves[2]);
        assertFalse(q.sound);
        assertTrue(q.music);
        assertTrue(q.tutorialSeen);
        assertTrue(q.dailyDone.contains("2026-11-18"));
        assertTrue(q.achievements.contains("ilk_isik"));
        assertEquals(p.hints, q.hints);
    }

    @Test public void corruptSaveFallsBackToDefaults() {
        for (String bad : new String[]{null, "", "{", "[1,2]", "{\"stars\":\"x\"}", "çöp"}) {
            Progress p = Progress.deserialize(bad);
            assertEquals(0, p.completedCount());
            assertEquals(Progress.START_HINTS, p.hints);
        }
    }

    @Test public void unlockingFollowsCompletion() {
        Progress p = new Progress();
        assertTrue(p.isUnlocked(1));
        assertFalse(p.isUnlocked(2));
        p.recordLevel(1, 1, 9, false);
        assertTrue(p.isUnlocked(2));
        assertFalse(p.isUnlocked(3));
        assertEquals(2, p.nextLevel());
    }

    @Test public void recordsOnlyImprove() {
        Progress p = new Progress();
        assertTrue(p.recordLevel(5, 3, 4, false));
        int hints = p.hints;
        assertFalse(p.recordLevel(5, 1, 10, false));
        assertEquals(3, p.stars[5]);
        assertEquals(4, p.bestMoves[5]);
        assertEquals(hints, p.hints); // ödül yalnızca ilk seferde
    }

    @Test public void hintRewardEveryTenLevels() {
        Progress p = new Progress();
        assertEquals(3, p.hints);
        for (int i = 1; i <= 9; i++) p.recordLevel(i, 3, 1, false);
        assertEquals("3 yıldız tek başına ipucu vermez", 3, p.hints);
        assertEquals(1, p.levelsUntilNextHint());
        p.recordLevel(10, 1, 9, false);
        assertEquals(4, p.hints);
        assertEquals(10, p.levelsUntilNextHint());
        p.recordLevel(10, 3, 1, false); // tekrar oynamak ödül vermez
        assertEquals(4, p.hints);
        for (int i = 11; i <= 30; i++) p.recordLevel(i, 2, 5, false);
        assertEquals(6, p.hints);
    }

    @Test public void oldHundredLevelSaveStillLoads() {
        StringBuilder s = new StringBuilder("{\"stars\":[");
        for (int i = 0; i < 100; i++) s.append(i == 0 ? "" : ",").append(3);
        s.append("],\"hints\":7}");
        Progress p = Progress.deserialize(s.toString());
        assertEquals(100, p.completedCount());
        assertTrue(p.isUnlocked(101));
        assertFalse(p.isUnlocked(102));
        assertEquals(7, p.hints);
    }

    @Test public void achievementsUnlock() {
        Progress p = new Progress();
        for (int i = 1; i <= 10; i++) p.recordLevel(i, 3, 1, false);
        List<Achievements.Def> fresh = Achievements.update(p);
        assertTrue(p.achievements.contains("ilk_isik"));
        assertTrue(p.achievements.contains("cirak"));
        assertTrue(p.achievements.contains("parilti"));
        assertTrue(p.achievements.contains("mukemmel"));
        assertFalse(p.achievements.contains("yansima"));
        assertFalse(fresh.isEmpty());
        assertTrue(Achievements.update(p).isEmpty()); // tekrar verilmez
    }

    @Test public void dailyStreak() {
        Progress p = new Progress();
        int today = 20_000;
        assertEquals("bugün henüz çözülmedi: seri 0 görünmemeli", 0, p.currentDailyStreak(today));
        p.dailyDone.add(DailyPuzzle.keyFor(today));
        p.dailyDone.add(DailyPuzzle.keyFor(today - 1));
        p.dailyDone.add(DailyPuzzle.keyFor(today - 3));
        assertEquals(2, p.dailyStreak(today));
    }

    @Test public void resetClearsEverything() {
        Progress p = new Progress();
        p.recordLevel(1, 3, 1, false);
        Achievements.update(p);
        p.resetAll();
        assertEquals(0, p.completedCount());
        assertTrue(p.achievements.isEmpty());
    }
}
