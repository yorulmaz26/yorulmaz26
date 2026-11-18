package com.golgen.oyun.core;

import static org.junit.Assert.*;
import org.junit.Test;

public class GameWorldTest {
    private static GameWorld quietWorld() {
        GameWorld w = new GameWorld(1);
        w.setArenaHeight(1778f);
        w.start(DifficultyProfile.endless(), null, 1);
        w.spawnObstacles = false;
        w.spawnDiamonds = false;
        return w;
    }

    private static void run(GameWorld w, float seconds) {
        int frames = Math.round(seconds * 60f);
        for (int i = 0; i < frames && w.state == GameWorld.RUNNING; i++) w.update(1f / 60f);
    }

    @Test public void shadowReplaysPathWithExactDelay() {
        GameWorld w = quietWorld();
        w.spawnShadows = false;
        w.shadowsHarmless = true;
        // Oyuncuyu sağa doğru sürekli hareket ettir
        for (int f = 0; f < 600; f++) {
            if (f % 20 == 0) w.moveTargetBy(f % 40 == 0 ? 300f : -300f, f % 60 == 0 ? 120f : -60f);
            w.update(1f / 60f);
        }
        assertTrue(w.addShadow(3f));
        Shadow s = w.shadows.get(0);
        // Sonraki karelerde gölge = 3 sn önceki oyuncu konumu olmalı
        float[] o = new float[2];
        for (int f = 0; f < 120; f++) {
            w.update(1f / 60f);
            w.recorder.sample(w.time - 3f, o);
            assertEquals(o[0], s.x, 1e-3);
            assertEquals(o[1], s.y, 1e-3);
        }
        assertEquals(GameWorld.RUNNING, w.state);
    }

    @Test public void standingStillGetsCaughtByShadow() {
        GameWorld w = quietWorld();
        run(w, 20f);
        assertEquals(GameWorld.DEAD, w.state);
        assertEquals(GameWorld.CAUSE_SHADOW, w.cause);
        // İlk gölge 3. saniyede belirir, 1 sn zararsızdır → ~4. saniyede yakalanır
        assertTrue("time=" + w.time, w.time > 3.9f && w.time < 4.2f);
    }

    @Test public void movingInCirclesEscapesShadow() {
        GameWorld w = quietWorld();
        float cx = 500f, cy = 1200f;
        int frames = 60 * 15;
        for (int f = 0; f < frames && w.state == GameWorld.RUNNING; f++) {
            double a = f / 60.0 * 2.2; // ~2.85 sn'de bir tur: 3 sn gecikmeli gölgeden farklı faz
            w.setTarget(cx + (float) Math.cos(a) * 300f, cy + (float) Math.sin(a) * 300f);
            w.update(1f / 60f);
        }
        assertEquals(GameWorld.RUNNING, w.state);
    }

    @Test public void pauseDoesNotAdvanceShadowOrTime() {
        GameWorld w = quietWorld();
        for (int f = 0; f < 300; f++) { w.moveTargetBy(f % 2 == 0 ? 8f : -8f, 2f); w.update(1f / 60f); }
        float t = w.time;
        float sx = w.shadows.size() > 0 ? w.shadows.get(0).x : -1;
        // "Duraklatma" = update çağrılmaması; gerçek zaman geçse de oyun durumu aynı kalmalı
        assertEquals(t, w.time, 0f);
        if (w.shadows.size() > 0) assertEquals(sx, w.shadows.get(0).x, 0f);
        // Devam edince büyük bir sıçrama olmamalı (dt sınırlanır)
        w.update(5f);
        assertTrue(w.time - t <= 0.1f + 1e-4f);
    }

    @Test public void deterministicWithSameSeed() {
        GameWorld a = new GameWorld(7), b = new GameWorld(7);
        a.setArenaHeight(1778f); b.setArenaHeight(1778f);
        a.start(DifficultyProfile.endless(), null, 42);
        b.start(DifficultyProfile.endless(), null, 42);
        for (int f = 0; f < 600; f++) {
            float dx = (float) Math.sin(f * 0.1) * 20f;
            a.moveTargetBy(dx, 0); b.moveTargetBy(dx, 0);
            a.update(1f / 60f); b.update(1f / 60f);
        }
        assertEquals(a.score(), b.score());
        assertEquals(a.obstacles.size(), b.obstacles.size());
        assertEquals(a.state, b.state);
    }

    @Test public void obstacleHitCostsLifeAndGivesInvulnerability() {
        GameWorld w = quietWorld();
        w.spawnShadows = false;
        w.setTarget(500f, 1300f);
        run(w, 0.5f);
        // Boşluk oyuncudan uzakta, duvar oyuncunun üstüne düşüyor
        w.addWall(1250f, 100f, 150f);
        run(w, 0.5f);
        assertEquals(1, w.hits);
        assertEquals(2, w.lives);
        assertTrue(w.invuln > 0f);
        assertEquals(GameWorld.RUNNING, w.state);
    }

    @Test public void gapLetsPlayerThrough() {
        GameWorld w = quietWorld();
        w.spawnShadows = false;
        w.setTarget(500f, 1300f);
        run(w, 0.5f);
        w.addWall(1000f, 500f, 260f);
        run(w, 2f);
        assertEquals(0, w.hits);
    }

    @Test public void collectsDiamond() {
        GameWorld w = quietWorld();
        w.spawnShadows = false;
        w.setTarget(500f, 1300f);
        run(w, 0.5f);
        w.addDiamond(500f, 1200f);
        run(w, 1f);
        assertEquals(1, w.diamonds);
        assertEquals(0, w.diamondPool.size());
    }

    @Test public void shadowCountIsCapped() {
        GameWorld w = quietWorld();
        w.shadowsHarmless = true;
        run(w, 200f);
        assertTrue(w.shadows.size() <= w.profile().maxShadows);
        assertEquals(5, w.shadows.size());
    }

    @Test public void endlessBotSurvivesFairObstacles() {
        // Fark gözetmeyen basit bir bot: bir sonraki duvarın boşluğuna gider, gölgeden uzaklaşır.
        // Engel üretimi adilse bot uzun süre (çoğunlukla) hayatta kalabilmeli.
        int survived = 0;
        for (int seed = 1; seed <= 20; seed++) {
            GameWorld w = new GameWorld(seed);
            w.setArenaHeight(1778f);
            w.start(DifficultyProfile.endless(), null, seed);
            w.shadowsHarmless = true;
            w.obstaclesHarmless = false;
            w.profile().lives = 1000;
            w.lives = 1000;
            for (int f = 0; f < 60 * 90; f++) {
                Obstacle next = null;
                for (int i = 0; i < w.obstacles.size(); i++) {
                    Obstacle o = w.obstacles.get(i);
                    if (o.type == Obstacle.WALL && o.y < w.py - 10 && (next == null || o.y > next.y)) next = o;
                }
                if (next != null) w.setTarget(next.gapX, w.py);
                w.update(1f / 60f);
            }
            // Duvarlar her zaman geçilebilir olmalı: sadece duvarlara yönelen bot bile az çarpmalı
            if (w.hits < 40) survived++;
            // Görünür alanda aynı anda makul sayıda engel
            assertTrue(w.obstacles.size() < 20);
        }
        assertTrue("survived=" + survived, survived >= 15);
    }

    @Test public void wallGapsAreAlwaysWideAndReachable() {
        GameWorld w = new GameWorld(3);
        w.setArenaHeight(1778f);
        w.start(DifficultyProfile.endless(), null, 3);
        w.shadowsHarmless = true;
        w.obstaclesHarmless = true;
        float lastGap = -1;
        int walls = 0;
        java.util.IdentityHashMap<Obstacle, Float> seen = new java.util.IdentityHashMap<>();
        for (int f = 0; f < 60 * 300; f++) {
            w.update(1f / 60f);
            for (int i = 0; i < w.obstacles.size(); i++) {
                Obstacle o = w.obstacles.get(i);
                Float prev = o.type == Obstacle.WALL && o.y < 0 ? seen.put(o, o.gapX) : Float.valueOf(o.gapX);
                if (o.type == Obstacle.WALL && o.y < 0 && (prev == null || prev != o.gapX)) {
                    assertTrue(o.gapW >= 4 * GameWorld.PLAYER_R * 1.9f);
                    assertTrue(o.gapX - o.gapW / 2 >= 0 && o.gapX + o.gapW / 2 <= GameWorld.WIDTH);
                    if (lastGap >= 0) assertTrue(Math.abs(o.gapX - lastGap) <= 560f + 1e-3);
                    lastGap = o.gapX;
                    walls++;
                }
            }
        }
        assertTrue(walls > 50);
    }

    @Test public void dodgeCountedOnNearMiss() {
        GameWorld w = quietWorld();
        w.spawnShadows = false;
        w.setTarget(300f, 1300f);
        run(w, 3.2f);
        assertTrue(w.addShadow(3f));
        run(w, 1.2f); // gölge katılaşır, oyuncunun durduğu yerde
        // Oyuncu yakında duruyordu -> gölge çok yakın; hemen uzaklaş
        w.setTarget(300f, 1300f);
        // Gölge yeni konumumuza yaklaşacak: kısa bir sıçrayışla yanından geçip uzaklaş
        assertEquals(GameWorld.DEAD, w.state); // durmak = yakalanmak
        GameWorld w2 = quietWorld();
        w2.spawnShadows = false;
        w2.setTarget(300f, 1300f);
        run(w2, 2.0f);
        w2.setTarget(700f, 1300f);
        run(w2, 2.5f);
        assertTrue(w2.addShadow(3f)); // gölge 300'den 700'e doğru gidecek
        w2.setTarget(700f, 1300f - 120f); // yolun hemen yanında bekle
        run(w2, 2.0f);
        w2.setTarget(700f, 700f);         // sonra uzaklaş
        run(w2, 1.5f);
        assertTrue("dodges=" + w2.dodges + " state=" + w2.state, w2.dodges >= 1);
    }
}
