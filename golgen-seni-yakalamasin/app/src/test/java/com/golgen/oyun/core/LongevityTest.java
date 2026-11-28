package com.golgen.oyun.core;

import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;

/** Uzun süreli oynanış sistemleri: güçlendirmeler, kombo, aşamalar, yıldızlar, XP, giriş serisi, 60 görev. */
public class LongevityTest {
    @BeforeClass public static void setUp() { L10nTestSupport.load("tr"); }

    private static GameWorld quiet() {
        GameWorld w = new GameWorld(3);
        w.setArenaHeight(1778f);
        w.start(DifficultyProfile.endless(), null, 3);
        w.spawnObstacles = false;
        w.spawnDiamonds = false;
        w.spawnPowerups = false;
        w.spawnShadows = false;
        return w;
    }

    private static void run(GameWorld w, float s) {
        for (int i = 0; i < Math.round(s * 60f) && w.state == GameWorld.RUNNING; i++) w.update(1f / 60f);
    }

    @Test public void shieldAbsorbsShadowOnce() {
        GameWorld w = quiet();
        w.setTarget(500f, 1300f);
        run(w, 0.5f);
        w.addPowerup(PowerUp.SHIELD, 500f, 1250f);
        run(w, 0.5f);
        assertTrue(w.shield);
        assertEquals(1, w.powerupsCollected);
        run(w, 3f);
        assertTrue(w.addShadow(3f));
        run(w, 1.6f); // hareketsiz: gölge katılaşır ve dokunur → kalkan kırılır
        assertEquals(GameWorld.RUNNING, w.state);
        assertFalse(w.shield);
        run(w, 4f);   // kalkan yokken yakalanır
        assertEquals(GameWorld.DEAD, w.state);
        assertEquals(GameWorld.CAUSE_SHADOW, w.cause);
    }

    @Test public void shieldAbsorbsObstacleWithoutLosingLife() {
        GameWorld w = quiet();
        w.setTarget(500f, 1300f);
        run(w, 0.3f);
        w.shield = true;
        w.addWall(1250f, 100f, 150f);
        run(w, 0.5f);
        assertEquals(3, w.lives);
        assertEquals(0, w.hits);
        assertFalse(w.shield);
    }

    @Test public void slowTimeSlowsSimulationButKeepsShadowExact() {
        GameWorld a = quiet(), b = quiet();
        b.slowTimer = GameWorld.SLOW_TIME;
        run(a, 1f);
        run(b, 1f);
        assertEquals(1f, a.time, 0.02f);
        assertEquals(GameWorld.SLOW_SCALE, b.time, 0.03f);
    }

    @Test public void magnetPullsDiamonds() {
        GameWorld w = quiet();
        w.setTarget(500f, 1300f);
        run(w, 0.3f);
        w.magnetTimer = GameWorld.MAGNET_TIME;
        w.addDiamond(800f, 1300f); // aynı yükseklikte, 300 birim yanda — akışla aşağı kayar ama mıknatıs çeker
        run(w, 1f);
        assertEquals(1, w.diamonds);
    }

    @Test public void comboMultiplierAndReset() {
        GameWorld w = quiet();
        w.setTarget(500f, 1300f);
        run(w, 0.3f);
        for (int i = 0; i < 8; i++) { w.addDiamond(500f, 1290f); run(w, 0.1f); }
        assertEquals(8, w.combo);
        assertEquals(3, w.multiplier());
        assertEquals(8, w.maxCombo);
        int bonus = w.bonus;
        assertTrue("çarpanlı bonus", bonus > 8 * 25);
        run(w, GameWorld.COMBO_WINDOW + 0.5f);
        assertEquals(0, w.combo);
        assertEquals(8, w.maxCombo);
    }

    @Test public void stagesAdvanceAndOverdriveKeepsRising() {
        GameWorld w = quiet();
        w.shadowsHarmless = true;
        final int[] stages = new int[1];
        w.setEvents((t, x, y) -> { if (t == GameEvents.STAGE) stages[0]++; });
        float circle = 0f;
        for (int f = 0; f < 60 * 200; f++) {
            circle += 0.03f;
            w.setTarget(500f + (float) Math.cos(circle) * 300f, 1200f + (float) Math.sin(circle) * 300f);
            w.update(1f / 60f);
        }
        assertEquals(1 + (int) (w.time / 30f), w.stage);
        assertEquals(w.stage - 1, stages[0]);
        DifficultyProfile p = w.profile();
        assertTrue(p.overdrive(200f) > p.overdrive(160f));
        assertTrue(w.scroll > p.scrollMax);
    }

    @Test public void starsRules() {
        MissionDef m = Missions.ALL[5];
        RunResult r = new RunResult();
        r.won = false;
        assertEquals(0, m.stars(r));
        r.won = true; r.hits = 2; r.maxCombo = 0;
        assertEquals(1, m.stars(r));
        r.hits = 0;
        assertEquals(2, m.stars(r));
        r.maxCombo = m.starCombo;
        assertEquals(3, m.stars(r));
    }

    @Test public void starsSavedAndRewardedOnlyForImprovement() {
        Progress p = new Progress();
        RunResult r = new RunResult();
        r.mode = RunResult.MODE_MISSION; r.missionIndex = 0; r.won = true; r.stars = 2; r.time = 20;
        Progress.Report rep = new Progress.Report();
        p.applyRun(r, 1, rep);
        assertEquals(2, rep.newStars);
        r.stars = 1;
        p.applyRun(r, 1, rep);
        assertEquals(0, rep.newStars);
        r.stars = 3;
        p.applyRun(r, 1, rep);
        assertEquals(1, rep.newStars);
        assertEquals(3, p.totalStars());
        MemoryStore s = new MemoryStore();
        p.save(s);
        Progress q = new Progress();
        q.load(s);
        assertEquals(3, q.missionStars[0]);
        assertEquals(3, q.totalStars());
        assertEquals(p.level, q.level);
        assertEquals(p.xp, q.xp);
    }

    @Test public void xpLevelsUpAndRewards() {
        Progress p = new Progress();
        Progress.Report rep = new Progress.Report();
        int d0 = p.diamonds;
        int ups = p.addXp(Progress.xpForLevel(1) + Progress.xpForLevel(2) + 5, rep);
        assertEquals(2, ups);
        assertEquals(3, p.level);
        assertEquals(5, p.xp);
        assertEquals(Progress.levelReward(2) + Progress.levelReward(3), p.diamonds - d0);
    }

    @Test public void loginStreakCycleAndReset() {
        Progress p = new Progress();
        assertTrue(p.loginAvailable(100));
        assertEquals(1, p.nextLoginDay(100));
        assertEquals(Progress.LOGIN_REWARDS[0], p.claimLogin(100));
        assertFalse(p.loginAvailable(100));
        assertEquals(0, p.claimLogin(100));
        for (int d = 101; d <= 106; d++) p.claimLogin(d);
        assertEquals(7, p.loginStreak);
        assertEquals(1, p.nextLoginDay(107)); // döngü başa döner
        // Bir gün atlanınca seri sıfırlanır
        assertEquals(1, p.nextLoginDay(109));
        p.claimLogin(109);
        assertEquals(1, p.loginStreak);
    }

    @Test public void dailyHasEightTypesAndNewTypesAdvance() {
        assertEquals(8, DailyMissions.TYPE_COUNT);
        RunResult r = new RunResult();
        r.powerups = 2; r.maxCombo = 9; r.mode = RunResult.MODE_MISSION; r.won = true;
        assertEquals(2, DailyMissions.advance(DailyMissions.POWERUPS, 0, r));
        assertEquals(1, DailyMissions.advance(DailyMissions.MISSION_WINS, 0, r));
        assertEquals(9, DailyMissions.advance(DailyMissions.COMBO_SINGLE, 4, r));
        boolean[] seen = new boolean[8];
        int[] t = new int[3];
        for (int d = 0; d < 200; d++) { DailyMissions.typesFor(d, t); for (int x : t) seen[x] = true; }
        for (boolean b : seen) assertTrue(b);
    }

    @Test public void achievementsTwentyOneWithRewards() {
        assertEquals(21, Achievements.COUNT);
        for (int i = 0; i < Achievements.COUNT; i++) {
            assertNotEquals("ach." + i + ".title", Achievements.title(i));
            assertFalse(Achievements.desc(i), Achievements.desc(i).startsWith("ach."));
        }
        assertEquals(Achievements.COUNT, Achievements.REWARDS.length);
        assertEquals(12, Skins.ALL.length);
    }
}
