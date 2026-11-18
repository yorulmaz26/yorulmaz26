package com.golgen.oyun.core;

import static org.junit.Assert.*;
import org.junit.Test;

/** 100 bölüm / 20 harita / 2 aşama yapısı ve harita özellikleri. */
public class LevelsTest {
    private static GameWorld level(int index) {
        GameWorld w = new GameWorld(index + 1);
        w.setArenaHeight(1778f);
        MissionDef m = Missions.ALL[index];
        w.start(m.profile(), m, index + 1);
        return w;
    }

    private static void run(GameWorld w, float s) {
        for (int i = 0; i < Math.round(s * 60f) && w.state == GameWorld.RUNNING; i++) w.update(1f / 60f);
    }

    @Test public void fiveHundredLevelsHundredMapsFiveEach() {
        assertEquals(500, Missions.ALL.length);
        assertEquals(100, Maps.ALL.length);
        for (int i = 0; i < 500; i++) {
            MissionDef m = Missions.ALL[i];
            assertEquals(i, m.index);
            assertEquals(i / 5, m.mapIndex);
            assertSame(Maps.ALL[i / 5], m.map);
            assertEquals(i % 5 == 4, m.boss);
            assertEquals(40, m.target);
            DifficultyProfile p = m.profile();
            assertEquals(2, p.shadowDelays.length);
            assertTrue(p.maxDelay() <= 10f);
            assertNotNull(p.weights);
            if (i > 0 && m.mapIndex == Missions.ALL[i - 1].mapIndex && m.timeOffset < 150f) assertTrue(m.timeOffset > Missions.ALL[i - 1].timeOffset);
            for (float d : p.shadowDelays) assertTrue("gölge gecikmesi " + i, d >= 1.7f);
        }
    }

    @Test public void mapsAreDistinct() {
        java.util.HashSet<String> names = new java.util.HashSet<>();
        java.util.HashSet<Integer> looks = new java.util.HashSet<>();
        int[] decoUse = new int[9];
        for (MapTheme t : Maps.ALL) {
            assertTrue(names.add(t.name));
            looks.add(t.bgBottom);
            decoUse[t.deco]++;
            float total = 0f;
            for (float w : t.weights) total += w;
            assertTrue(t.name, total > 0f);
            assertTrue(t.shadowDelay2 > t.shadowDelay1);
        }
        assertEquals(100, names.size());
        assertTrue("renk çeşitliliği " + looks.size(), looks.size() >= 95);
        for (int d = 0; d < decoUse.length; d++) assertTrue("efekt " + d, decoUse[d] > 0);
        // Tüm yeni engel türleri en az bir haritada kullanılıyor
        for (int type = 0; type < Obstacle.TYPE_COUNT; type++) {
            boolean used = false;
            for (MapTheme t : Maps.ALL) for (int i = 0; i < t.weights.length; i++) if (Obstacle.WEIGHT_ORDER[i] == type && t.weights[i] > 0) used = true;
            assertTrue("engel " + type, used);
        }
    }

    @Test public void signatureObstacleGetsMoreFrequentAcrossFiveLevels() {
        MapTheme t = Maps.ALL[3];
        int sigIdx = -1;
        for (int i = 0; i < Obstacle.WEIGHT_ORDER.length; i++) if (Obstacle.WEIGHT_ORDER[i] == t.signature) sigIdx = i;
        float w0 = Missions.ALL[15].profile().weights[sigIdx], w4 = Missions.ALL[19].profile().weights[sigIdx];
        assertTrue(w4 > w0);
    }

    @Test public void stageOneHasOneShadowStageTwoHasTwoAndFortyStarsWin() {
        GameWorld w = level(0);
        w.spawnObstacles = false;
        w.spawnPowerups = false;
        w.spawnDiamonds = false; // otomatik yıldızlar kapalı; testte elle eklenir
        w.shadowsHarmless = true;
        final int[] stageEvents = new int[1];
        w.setEvents((t, x, y) -> { if (t == GameEvents.LEVEL_STAGE) stageEvents[0]++; });
        w.setTarget(500f, 1300f);
        run(w, 5f);
        assertEquals(1, w.shadows.size());
        assertEquals(1, w.levelStage);
        for (int i = 0; i < MissionDef.STARS_PER_STAGE; i++) { w.addStar(500f, 1280f); run(w, 0.1f); }
        assertEquals(2, w.levelStage);
        assertEquals(0, w.stageStars);
        assertEquals(1, stageEvents[0]);
        assertEquals("2. aşamada iki gölge", 2, w.shadows.size());
        assertEquals(GameWorld.RUNNING, w.state);
        for (int i = 0; i < MissionDef.STARS_PER_STAGE; i++) { w.addStar(500f, 1280f); run(w, 0.1f); }
        assertEquals(GameWorld.WON, w.state);
        assertEquals(40, w.totalStars);
        assertEquals(40, Missions.ALL[0].progress(w) + 0 * 1); // aşama 2, 20 yıldız
    }

    @Test public void starsSpawnInLevelsAndAreReachable() {
        GameWorld w = level(0);
        w.shadowsHarmless = true;
        w.obstaclesHarmless = true;
        int maxSeen = 0;
        float lo = w.minPlayerX(), hi = w.maxPlayerX();
        for (int f = 0; f < 60 * 30; f++) {
            w.update(1f / 60f);
            maxSeen = Math.max(maxSeen, w.starPool.size());
            for (int i = 0; i < w.starPool.size(); i++) {
                Diamond s = w.starPool.get(i);
                assertTrue(s.x >= lo - 1 && s.x <= hi + 1);
            }
        }
        assertTrue(maxSeen > 0);
    }

    @Test public void botCanFinishEarlyLevelsByChasingStars() {
        // Basit bot: en yakın yıldıza gider; gölgeler ve engeller zararsız. Yıldız akışının bölümü
        // makul sürede (3 dk içinde) bitirmeye yettiğini doğrular.
        for (int idx : new int[]{0, 4, 37, 99, 250, 377, 499}) {
            GameWorld w = level(idx);
            w.shadowsHarmless = true;
            w.obstaclesHarmless = true;
            for (int f = 0; f < 60 * 180 && w.state == GameWorld.RUNNING; f++) {
                Diamond best = null;
                float bd = Float.MAX_VALUE;
                for (int i = 0; i < w.starPool.size(); i++) {
                    Diamond s = w.starPool.get(i);
                    if (s.y > w.py + 40f) continue;
                    float d = Geometry.dist2(w.px, w.py, s.x, s.y);
                    if (d < bd) { bd = d; best = s; }
                }
                if (best != null) w.setTarget(best.x, Math.max(w.minPlayerY(), best.y + 40f));
                w.update(1f / 60f);
            }
            assertEquals("bölüm " + (idx + 1) + " süre=" + w.time + " yıldız=" + w.totalStars, GameWorld.WON, w.state);
        }
    }

    @Test public void windPushesPlayer() {
        int idx = 10; // Yağmurlu Şehir (rüzgâr)
        assertTrue(Missions.ALL[idx].map.has(MapTheme.F_WIND));
        GameWorld w = level(idx);
        w.spawnObstacles = false; w.spawnDiamonds = false; w.spawnPowerups = false; w.shadowsHarmless = true;
        float x0 = w.px;
        run(w, 2f);
        assertTrue(Math.abs(w.px - x0) > 50f);
    }

    @Test public void iceAddsInertia() {
        int idx = 20; // Buz Çölü
        assertTrue(Missions.ALL[idx].map.has(MapTheme.F_ICE));
        GameWorld ice = level(idx), normal = level(0);
        for (GameWorld w : new GameWorld[]{ice, normal}) {
            w.spawnObstacles = false; w.spawnDiamonds = false; w.spawnPowerups = false; w.shadowsHarmless = true;
            w.setTarget(800f, w.py);
            run(w, 0.15f);
        }
        assertTrue("buzda daha geç ulaşır", ice.px < normal.px - 50f);
        run(ice, 3f);
        assertEquals(800f, ice.px, 15f);
    }

    @Test public void narrowLimitsArena() {
        int idx = 70; // Ayna Labirenti
        assertTrue(Missions.ALL[idx].map.has(MapTheme.F_NARROW));
        GameWorld w = level(idx);
        assertTrue(w.minPlayerX() > GameWorld.PLAYER_R + 60f);
        w.setTarget(0f, 1300f);
        assertTrue(w.tx >= w.minPlayerX());
    }

    @Test public void newObstaclesSpawnAndCollide() {
        int[] seen = new int[Obstacle.TYPE_COUNT];
        for (int idx = 0; idx < 500; idx += 13) {
            GameWorld w = level(idx);
            w.shadowsHarmless = true;
            w.obstaclesHarmless = true;
            for (int f = 0; f < 60 * 40; f++) {
                w.update(1f / 60f);
                for (int i = 0; i < w.obstacles.size(); i++) seen[w.obstacles.get(i).type]++;
            }
        }
        for (int t = 0; t < Obstacle.TYPE_COUNT; t++) assertTrue("tür " + t + " görülmedi", seen[t] > 0);
    }

    @Test public void tiersRevisitMapsWithChanges() {
        for (int b = 0; b < 20; b++) {
            MapTheme base = Maps.ALL[b];
            for (int k = 1; k < Maps.TIERS; k++) {
                MapTheme t = Maps.ALL[k * 20 + b];
                assertEquals(k, t.tier());
                assertTrue(t.name.startsWith(base.name));
                assertTrue("kademe yeni özellik ekler", Integer.bitCount(t.features) > Integer.bitCount(base.features)
                        || (t.features != base.features));
                assertTrue(t.shadowDelay1 < base.shadowDelay1);
                assertNotEquals(base.bgBottom, t.bgBottom);
            }
        }
    }

    @Test public void difficultyGrowsGraduallyOverFiveHundred() {
        float prev = -1f;
        for (int i = 0; i < 500; i += 5) {
            float off = Missions.ALL[i].timeOffset;
            assertTrue(off >= prev);
            prev = off;
        }
        assertTrue(Missions.ALL[50].timeOffset < 100f);   // ilk 50 bölüm tavana yakın değil
        assertTrue(Missions.ALL[499].timeOffset >= 140f);
        assertTrue(Missions.ALL[499].profile().overdriveMax > Missions.ALL[10].profile().overdriveMax);
    }

    @Test public void gateOnlyHurtsWhenActive() {
        Obstacle o = new Obstacle();
        o.reset();
        o.type = Obstacle.GATE;
        o.phase = 0.2f;
        assertFalse(o.gateActive());
        o.phase = Obstacle.GATE_WARN + 0.1f;
        assertTrue(o.gateWarning());
        assertFalse(o.gateActive());
        o.phase = Obstacle.GATE_ON + 0.1f;
        assertTrue(o.gateActive());
    }

    @Test public void stormSpawnsMeteors() {
        int idx = 15; // Lav Çekirdeği
        assertTrue(Missions.ALL[idx].map.has(MapTheme.F_STORM));
        GameWorld w = level(idx);
        w.shadowsHarmless = true; w.obstaclesHarmless = true;
        int meteors = 0;
        for (int f = 0; f < 60 * 30; f++) {
            w.update(1f / 60f);
            for (int i = 0; i < w.obstacles.size(); i++) if (w.obstacles.get(i).type == Obstacle.METEOR && w.obstacles.get(i).y < -500f) meteors++;
        }
        assertTrue(meteors > 3);
    }
}
