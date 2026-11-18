package com.golgen.oyun.core;

import java.util.Random;

/**
 * Oyun simülasyonu (Android'den bağımsız, birim testli).
 * Sabit zaman adımıyla (1/120 sn) çalışır; bu sayede gölge tekrarı her cihazda aynıdır.
 * Duraklatma: update çağrılmadığı sürece oyun zamanı ilerlemez.
 */
public final class GameWorld {
    public static final float WIDTH = 1000f;
    public static final float STEP = 1f / 120f;
    public static final float PLAYER_R = 28f;
    public static final float MAX_SPEED = 1900f;
    public static final float DIAMOND_R = 26f;
    public static final float HIT_SCALE = 0.78f;   // gölge çarpışmasında bağışlayıcı oran
    public static final float NEAR_MISS = 70f;

    public static final int RUNNING = 0, DEAD = 1, WON = 2;
    public static final int CAUSE_NONE = 0, CAUSE_SHADOW = 1, CAUSE_OBSTACLE = 2;

    // --- Ayarlar ---
    private DifficultyProfile profile = DifficultyProfile.endless();
    private MissionDef mission;          // null = sonsuz mod
    public boolean spawnObstacles = true;
    public boolean spawnShadows = true;
    public boolean spawnDiamonds = true;
    public boolean shadowsHarmless = false; // öğretici için
    public boolean obstaclesHarmless = false;

    // --- Durum ---
    public float height = 1778f;
    public float time;
    private float accumulator;
    public int state;
    public int cause;
    public float px, py, tx, ty;
    public float pvx, pvy;
    public int lives;
    public float invuln;
    public int diamonds, dodges, hits;
    public float scroll;          // anlık kayma hızı (arka plan için)
    public float scrollDistance;  // toplam kayma

    public final MotionRecorder recorder;
    public final Pool<Shadow> shadows;
    public final Pool<Obstacle> obstacles = new Pool<>(48, Obstacle::new);
    public final Pool<Diamond> diamondPool = new Pool<>(16, Diamond::new);

    private final Random rng;
    private float nextObstacleAt;
    private float nextDiamondAt;
    private float lastGapX = WIDTH / 2f;
    private int nextShadow;
    private final float[] tmp = new float[2];
    private final int[] avail = new int[3];
    private GameEvents events;

    public GameWorld(long seed) {
        rng = new Random(seed);
        // En uzun gecikme + pay; 120 Hz örnekleme.
        recorder = new MotionRecorder((int) ((10f + 1f) / STEP));
        shadows = new Pool<>(8, Shadow::new);
    }

    public void setEvents(GameEvents e) { events = e; }
    public DifficultyProfile profile() { return profile; }
    public MissionDef mission() { return mission; }

    public void setArenaHeight(float h) {
        height = Math.max(1200f, h);
    }

    public float minPlayerY() { return height * 0.24f; }
    public float maxPlayerY() { return height - PLAYER_R - 24f; }
    public float minPlayerX() { return PLAYER_R + 14f; }
    public float maxPlayerX() { return WIDTH - PLAYER_R - 14f; }

    public void start(DifficultyProfile p, MissionDef m, long seed) {
        profile = p;
        mission = m;
        if (p.maxDelay() > 10f) throw new IllegalArgumentException("Gecikme kayıt kapasitesini aşıyor");
        rng.setSeed(seed);
        time = 0f;
        accumulator = 0f;
        state = RUNNING;
        cause = CAUSE_NONE;
        px = tx = WIDTH / 2f;
        py = ty = height * 0.72f;
        pvx = pvy = 0f;
        lives = p.lives;
        invuln = 0f;
        diamonds = dodges = hits = 0;
        scrollDistance = 0f;
        recorder.clear();
        recorder.record(0f, px, py);
        shadows.clear();
        obstacles.clear();
        diamondPool.clear();
        nextObstacleAt = 1.2f;
        nextDiamondAt = 2.0f;
        lastGapX = WIDTH / 2f;
        nextShadow = 0;
        scroll = p.scroll(0f);
    }

    /** Parmak hareketi (dünya birimi cinsinden göreli). */
    public void moveTargetBy(float dx, float dy) {
        tx = Geometry.clamp(tx + dx, minPlayerX(), maxPlayerX());
        ty = Geometry.clamp(ty + dy, minPlayerY(), maxPlayerY());
    }

    public void setTarget(float x, float y) {
        tx = Geometry.clamp(x, minPlayerX(), maxPlayerX());
        ty = Geometry.clamp(y, minPlayerY(), maxPlayerY());
    }

    /** Gerçek kare süresiyle çağrılır; içeride sabit adımlara bölünür. */
    public void update(float frameDt) {
        if (state != RUNNING) return;
        if (frameDt > 0.1f) frameDt = 0.1f; // takılmalardan sonra spiral engelleme
        accumulator += frameDt;
        while (accumulator >= STEP && state == RUNNING) {
            accumulator -= STEP;
            step(STEP);
        }
    }

    public int score() {
        return (int) (time * 10f) + diamonds * 25 + dodges * 15;
    }

    public float difficulty() {
        return profile.level(time);
    }

    void step(float dt) {
        time += dt;
        float d = profile.level(time);
        scroll = profile.scroll(d);
        scrollDistance += scroll * dt;
        if (invuln > 0f) invuln -= dt;

        // Oyuncu: hedefe en fazla MAX_SPEED ile ilerle
        float dx = tx - px, dy = ty - py;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float maxMove = MAX_SPEED * dt;
        float ox = px, oy = py;
        if (dist <= maxMove || dist < 0.001f) {
            px = tx;
            py = ty;
        } else {
            px += dx / dist * maxMove;
            py += dy / dist * maxMove;
        }
        pvx = (px - ox) / dt;
        pvy = (py - oy) / dt;
        recorder.record(time, px, py);

        if (spawnShadows) updateShadowSpawns();
        updateShadows(dt);
        if (state != RUNNING) return;

        if (spawnObstacles && time >= nextObstacleAt) spawnObstacle(d);
        if (spawnDiamonds && time >= nextDiamondAt) spawnDiamond();
        updateObstacles(dt);
        if (state != RUNNING) return;
        updateDiamonds(dt);
        checkMission();
    }

    private void updateShadowSpawns() {
        DifficultyProfile p = profile;
        if (nextShadow >= p.shadowSpawnTimes.length || nextShadow >= p.maxShadows) return;
        float at = p.shadowSpawnTimes[nextShadow];
        if (time >= at) {
            addShadow(p.shadowDelays[nextShadow % p.shadowDelays.length]);
            nextShadow++;
        }
    }

    /** Yeni gölge ekler (havuz sınırına kadar). */
    public boolean addShadow(float delay) {
        if (time < delay) return false; // gecikme kadar geçmiş yoksa tekrar edilemez
        Shadow s = shadows.obtain();
        if (s == null) return false;
        s.set(delay, time);
        recorder.sample(time - delay, tmp);
        s.x = tmp[0];
        s.y = tmp[1];
        if (events != null) events.onEvent(GameEvents.SHADOW_SPAWN, s.x, s.y);
        return true;
    }

    private void updateShadows(float dt) {
        float hitR = PLAYER_R * HIT_SCALE * 2f;
        float nearR = PLAYER_R * 2f + NEAR_MISS;
        for (int i = 0; i < shadows.size(); i++) {
            Shadow s = shadows.get(i);
            recorder.sample(time - s.delay, tmp);
            s.x = tmp[0];
            s.y = tmp[1];
            if (s.dodgeCooldown > 0f) s.dodgeCooldown -= dt;
            if (!s.isSolid(time)) continue;
            float d2 = Geometry.dist2(px, py, s.x, s.y);
            if (d2 < hitR * hitR) {
                if (!shadowsHarmless) {
                    die(CAUSE_SHADOW);
                    return;
                }
                continue;
            }
            if (d2 < nearR * nearR) {
                s.wasClose = true;
            } else if (s.wasClose && d2 > (nearR + 30f) * (nearR + 30f)) {
                s.wasClose = false;
                if (s.dodgeCooldown <= 0f) {
                    s.dodgeCooldown = 1.5f;
                    dodges++;
                    if (events != null) events.onEvent(GameEvents.DODGE, s.x, s.y);
                }
            }
        }
    }

    private void spawnObstacle(float d) {
        Obstacle o = obstacles.obtain();
        float interval = profile.interval(d);
        if (o == null) { nextObstacleAt = time + interval; return; }
        o.reset();
        float dt = profile.difficultyTime(time);
        // %40 duvar; kalan olasılık zorluk zamanına göre açılmış türler arasında paylaşılır.
        int mapped = Obstacle.WALL;
        if (rng.nextInt(10) >= 4) {
            int n = 0;
            if (dt >= profile.mineFrom) avail[n++] = Obstacle.MINE;
            if (dt >= profile.sweeperFrom) avail[n++] = Obstacle.SWEEPER;
            if (dt >= profile.rotorFrom) avail[n++] = Obstacle.ROTOR;
            if (n > 0) mapped = avail[rng.nextInt(n)];
        }
        o.type = mapped;
        float extraWait = 0f;
        switch (mapped) {
            case Obstacle.WALL: {
                o.h = 34f;
                o.gapW = profile.gap(d);
                float half = o.gapW / 2f;
                float maxShift = Geometry.clamp(MAX_SPEED * interval * 0.32f, 180f, 560f);
                float lo = Math.max(half + 20f, lastGapX - maxShift);
                float hi = Math.min(WIDTH - half - 20f, lastGapX + maxShift);
                o.gapX = lo + rng.nextFloat() * Math.max(0f, hi - lo);
                lastGapX = o.gapX;
                o.y = -o.h;
                break;
            }
            case Obstacle.MINE: {
                o.r = 38f + rng.nextFloat() * 16f;
                o.x = 80f + rng.nextFloat() * (WIDTH - 160f);
                o.y = -o.r * 1.3f;
                o.phase = rng.nextFloat() * 6f;
                break;
            }
            case Obstacle.SWEEPER: {
                o.w = 250f + rng.nextFloat() * 50f;
                o.h = 30f;
                o.amp = (WIDTH - o.w) / 2f - 30f;
                o.baseX = WIDTH / 2f;
                o.speed = 1.3f + 1.2f * d;
                o.phase = rng.nextFloat() * 6.28f;
                o.x = o.baseX + (float) Math.sin(o.phase) * o.amp;
                o.y = -o.h;
                break;
            }
            default: { // ROTOR
                o.w = 420f + 140f * d;
                o.h = 22f;
                o.x = o.w / 2f + 10f + rng.nextFloat() * (WIDTH - o.w - 20f);
                o.speed = (rng.nextBoolean() ? 1f : -1f) * (1.1f + 0.9f * d);
                o.angle = rng.nextFloat() * 3.14f;
                o.y = -o.w / 2f - o.h;
                extraWait = (o.w * 0.5f) / profile.scroll(d);
                break;
            }
        }
        nextObstacleAt = time + interval + extraWait;
    }

    private void spawnDiamond() {
        DifficultyProfile p = profile;
        nextDiamondAt = time + p.diamondIntervalMin + rng.nextFloat() * (p.diamondIntervalMax - p.diamondIntervalMin);
        float y = -DIAMOND_R * 2f;
        // Engelle çakışıyorsa kısa süre sonra tekrar dene
        for (int i = 0; i < obstacles.size(); i++) {
            Obstacle o = obstacles.get(i);
            if (Math.abs(o.y - y) < o.halfExtent() + 90f) {
                nextDiamondAt = time + 0.25f;
                return;
            }
        }
        Diamond dm = diamondPool.obtain();
        if (dm == null) return;
        dm.set(60f + rng.nextFloat() * (WIDTH - 120f), y);
    }

    private void updateObstacles(float dt) {
        float pr = PLAYER_R * 0.9f;
        for (int i = obstacles.size() - 1; i >= 0; i--) {
            Obstacle o = obstacles.get(i);
            o.y += scroll * dt;
            o.phase += dt;
            boolean hit = false;
            switch (o.type) {
                case Obstacle.WALL: {
                    float top = o.y - o.h / 2f, bottom = o.y + o.h / 2f;
                    float gl = o.gapX - o.gapW / 2f, gr = o.gapX + o.gapW / 2f;
                    hit = Geometry.circleRect(px, py, pr, -50f, top, gl, bottom)
                            || Geometry.circleRect(px, py, pr, gr, top, WIDTH + 50f, bottom);
                    break;
                }
                case Obstacle.MINE:
                    hit = Geometry.circleCircle(px, py, pr, o.x, o.y, o.mineRadius() * 0.92f);
                    break;
                case Obstacle.SWEEPER: {
                    o.x = o.baseX + (float) Math.sin(o.phase * o.speed) * o.amp;
                    hit = Geometry.circleRect(px, py, pr, o.x - o.w / 2f, o.y - o.h / 2f, o.x + o.w / 2f, o.y + o.h / 2f);
                    break;
                }
                case Obstacle.ROTOR: {
                    o.angle += o.speed * dt;
                    float hx = (float) Math.cos(o.angle) * o.w / 2f;
                    float hy = (float) Math.sin(o.angle) * o.w / 2f;
                    hit = Geometry.circleSegment(px, py, pr, o.x - hx, o.y - hy, o.x + hx, o.y + hy, o.h);
                    break;
                }
            }
            if (hit && invuln <= 0f && !obstaclesHarmless) {
                hits++;
                lives--;
                invuln = 1.5f;
                if (events != null) events.onEvent(GameEvents.HIT, px, py);
                if (lives <= 0 || (mission != null && mission.type == MissionDef.NO_HIT)) {
                    die(CAUSE_OBSTACLE);
                    return;
                }
            }
            if (o.y - o.halfExtent() > height + 40f) obstacles.free(i);
        }
    }

    private void updateDiamonds(float dt) {
        float cr = PLAYER_R + DIAMOND_R + 6f;
        for (int i = diamondPool.size() - 1; i >= 0; i--) {
            Diamond dm = diamondPool.get(i);
            dm.y += scroll * dt;
            dm.phase += dt;
            if (Geometry.dist2(px, py, dm.x, dm.y) < cr * cr) {
                diamonds++;
                if (events != null) events.onEvent(GameEvents.DIAMOND, dm.x, dm.y);
                diamondPool.free(i);
            } else if (dm.y > height + 60f) {
                diamondPool.free(i);
            }
        }
    }

    private void checkMission() {
        if (mission == null) return;
        if (mission.progress(this) >= mission.target) {
            state = WON;
            if (events != null) events.onEvent(GameEvents.WIN, px, py);
        }
    }

    private void die(int c) {
        state = DEAD;
        cause = c;
        if (events != null) events.onEvent(GameEvents.DEATH, px, py);
    }

    /** Test ve öğretici için engel ekleme. */
    public Obstacle addWall(float y, float gapX, float gapW) {
        Obstacle o = obstacles.obtain();
        if (o == null) return null;
        o.reset();
        o.type = Obstacle.WALL;
        o.h = 34f;
        o.y = y;
        o.gapX = gapX;
        o.gapW = gapW;
        return o;
    }

    public Diamond addDiamond(float x, float y) {
        Diamond d = diamondPool.obtain();
        if (d != null) d.set(x, y);
        return d;
    }

    public RunResult toResult(int mode, int missionIndex, RunResult out) {
        out.mode = mode;
        out.missionIndex = missionIndex;
        out.score = score();
        out.time = time;
        out.diamonds = diamonds;
        out.dodges = dodges;
        out.hits = hits;
        out.won = state == WON;
        out.cause = cause;
        return out;
    }
}
