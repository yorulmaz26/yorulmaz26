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
    public static final float POWERUP_R = 30f;
    public static final float SLOW_SCALE = 0.55f;
    public static final float SLOW_TIME = 3.2f;     // oyun zamanı (≈5,8 gerçek sn)
    public static final float MAGNET_TIME = 7f;
    public static final float MAGNET_RANGE = 430f;
    public static final float COMBO_WINDOW = 4f;
    public static final float STAGE_SECONDS = 30f;
    public static final float STAR_R = 24f;

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
    public boolean spawnPowerups = true;

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
    // Güçlendirmeler, kombo, aşama
    public boolean shield;
    public float slowTimer, magnetTimer, shadowGrace;
    public int combo, maxCombo, bonus, powerupsCollected, lastPowerup;
    public float comboTimer;
    public int stage;
    // Bölüm (görev modu): 2 aşama × 20 yıldız
    public int levelStage = 1;
    public int stageStars;
    public int totalStars;
    public float windX;
    private float ivx, ivy;   // buz ataleti
    public final Pool<Diamond> starPool = new Pool<>(24, Diamond::new);
    private float nextStarAt, nextMeteorAt;

    public final MotionRecorder recorder;
    public final Pool<Shadow> shadows;
    public final Pool<Obstacle> obstacles = new Pool<>(48, Obstacle::new);
    public final Pool<Diamond> diamondPool = new Pool<>(16, Diamond::new);
    public final Pool<PowerUp> powerups = new Pool<>(4, PowerUp::new);

    private final Random rng;
    private float nextObstacleAt;
    private float nextDiamondAt;
    private float nextPowerupAt;
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
        height = Math.max(600f, h); // ekran ölçeği PlayScreen'de en az 1200 birim görünecek şekilde seçilir
    }

    public float minPlayerY() { return height * 0.24f; }
    public float maxPlayerY() { return height - PLAYER_R - 24f; }
    // Karanlık özelliği: aydınlık alan akış hızıyla büyür ve hareket yönüne (yukarı) kayar,
    // böylece hızlı kademelerde de yaklaşan engeli görüp kaçacak zaman kalır (>= ~0,6 sn).
    public static final float LIGHT_MIN_R = 330f;
    public static final float LIGHT_PER_SPEED = 0.6f;
    public static final float LIGHT_FORWARD = 0.3f;
    public float lightRadius() { return Math.max(LIGHT_MIN_R, scroll * LIGHT_PER_SPEED); }
    public float lightCenterY() { return py - lightRadius() * LIGHT_FORWARD; }

    public float minPlayerX() { return PLAYER_R + 14f + profile.narrow; }
    public float maxPlayerX() { return WIDTH - PLAYER_R - 14f - profile.narrow; }

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
        shield = false;
        slowTimer = magnetTimer = shadowGrace = comboTimer = 0f;
        combo = maxCombo = bonus = powerupsCollected = 0;
        stage = 1;
        levelStage = 1;
        stageStars = totalStars = 0;
        windX = ivx = ivy = 0f;
        starPool.clear();
        nextStarAt = 1.5f;
        nextMeteorAt = p.meteorInterval > 0f ? 4f : Float.MAX_VALUE;
        powerups.clear();
        nextPowerupAt = 9f;
        recorder.clear();
        recorder.record(0f, px, py);
        shadows.clear();
        obstacles.clear();
        diamondPool.clear();
        nextObstacleAt = 1.2f;
        nextDiamondAt = 2.0f;
        lastGapX = WIDTH / 2f;
        nextShadow = 0;
        // İlk adımın kullanacağı hızla başla (bölümün zorluk ofseti dahil): karanlıktaki ışık
        // dairesi "dokun ve başla" anında sıçramasın.
        scroll = p.scroll(p.level(0f)) + p.overdrive(0f);
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
        accumulator += slowTimer > 0f ? frameDt * SLOW_SCALE : frameDt;
        while (accumulator >= STEP && state == RUNNING) {
            accumulator -= STEP;
            step(STEP);
        }
    }

    public int score() {
        return (int) (time * 10f) + bonus;
    }

    /** Kombo çarpanı: her 4 ardışık toplama/kaçışta +1, en fazla x5. */
    public int multiplier() {
        return 1 + Math.min(4, combo / 4);
    }

    private void addCombo(int basePoints) {
        int before = multiplier();
        combo++;
        if (combo > maxCombo) maxCombo = combo;
        comboTimer = COMBO_WINDOW;
        bonus += basePoints * multiplier();
        if (multiplier() > before && events != null) events.onEvent(GameEvents.COMBO, px, py);
    }

    public float difficulty() {
        return profile.level(time);
    }

    void step(float dt) {
        time += dt;
        float d = profile.level(time);
        scroll = profile.scroll(d) + profile.overdrive(time);
        scrollDistance += scroll * dt;
        if (invuln > 0f) invuln -= dt;
        if (shadowGrace > 0f) shadowGrace -= dt;
        if (slowTimer > 0f) slowTimer -= dt;
        if (magnetTimer > 0f) magnetTimer -= dt;
        if (comboTimer > 0f) { comboTimer -= dt; if (comboTimer <= 0f) combo = 0; }
        int st = 1 + (int) (time / STAGE_SECONDS);
        if (st > stage) {
            stage = st;
            if (events != null) events.onEvent(GameEvents.STAGE, px, py);
        }

        // Rüzgâr: hedefi (ve dolayısıyla karakteri) yavaşça yana iter
        if (profile.wind > 0f) {
            windX = profile.wind * (float) Math.sin(time * 0.45f + 1.3f);
            tx = Geometry.clamp(tx + windX * dt, minPlayerX(), maxPlayerX());
        }
        // Oyuncu: hedefe en fazla MAX_SPEED ile ilerle
        float dx = tx - px, dy = ty - py;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float maxMove = MAX_SPEED * dt;
        float ox = px, oy = py;
        if (profile.iceGrip > 0f) {
            // Buz: istenen hıza ataletle yaklaş
            float wantVx = 0f, wantVy = 0f;
            if (dist > 0.5f) {
                float sp = Math.min(MAX_SPEED, dist * 8f);
                wantVx = dx / dist * sp;
                wantVy = dy / dist * sp;
            }
            float k = Math.min(1f, profile.iceGrip * dt);
            ivx += (wantVx - ivx) * k;
            ivy += (wantVy - ivy) * k;
            px = Geometry.clamp(px + ivx * dt, minPlayerX(), maxPlayerX());
            py = Geometry.clamp(py + ivy * dt, minPlayerY(), maxPlayerY());
        } else if (dist <= maxMove || dist < 0.001f) {
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
        if (spawnPowerups && time >= nextPowerupAt) spawnPowerup();
        if (mission != null && spawnDiamonds && time >= nextStarAt) spawnStars();
        if (spawnObstacles && time >= nextMeteorAt) spawnMeteor(d);
        updateObstacles(dt);
        if (state != RUNNING) return;
        updateDiamonds(dt);
        updatePowerups(dt);
        if (mission != null) updateStars(dt);
        if (state != RUNNING) return;
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
                if (shadowsHarmless || shadowGrace > 0f) continue;
                if (shield) {
                    breakShield();
                    continue;
                }
                die(CAUSE_SHADOW);
                return;
            }
            if (d2 < nearR * nearR) {
                s.wasClose = true;
            } else if (s.wasClose && d2 > (nearR + 30f) * (nearR + 30f)) {
                s.wasClose = false;
                if (s.dodgeCooldown <= 0f) {
                    s.dodgeCooldown = 1.5f;
                    dodges++;
                    addCombo(15);
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
        if (profile.weights != null) {
            float total = 0f;
            for (float wv : profile.weights) total += wv;
            float r = rng.nextFloat() * total;
            for (int i = 0; i < profile.weights.length; i++) {
                r -= profile.weights[i];
                if (r <= 0f && profile.weights[i] > 0f) { mapped = Obstacle.WEIGHT_ORDER[i]; break; }
            }
        } else if (rng.nextInt(10) >= 4) {
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
                float lo = Math.max(profile.narrow + half + 20f, lastGapX - maxShift);
                float hi = Math.min(WIDTH - profile.narrow - half - 20f, lastGapX + maxShift);
                if (hi < lo) { lo = hi = WIDTH / 2f; }
                o.gapX = lo + rng.nextFloat() * Math.max(0f, hi - lo);
                lastGapX = o.gapX;
                o.y = -o.h;
                break;
            }
            case Obstacle.MINE: {
                o.r = 38f + rng.nextFloat() * 16f;
                o.x = profile.narrow + 80f + rng.nextFloat() * (WIDTH - 2f * profile.narrow - 160f); // dar geçitte lazer şeridine düşmesin
                o.y = -o.r * 1.3f;
                o.phase = rng.nextFloat() * 6f;
                break;
            }
            case Obstacle.SWEEPER: {
                o.w = 250f + rng.nextFloat() * 50f;
                o.h = 30f;
                o.amp = Math.max(0f, (WIDTH - 2f * profile.narrow - o.w) / 2f - 30f); // dar geçitte koridor içinde salınır
                o.baseX = WIDTH / 2f;
                o.speed = 1.3f + 1.2f * d;
                o.phase = rng.nextFloat() * 6.28f;
                o.x = o.baseX + (float) Math.sin(o.phase) * o.amp;
                o.y = -o.h;
                break;
            }
            case Obstacle.ROTOR: {
                o.w = Math.min(420f + 140f * d, (WIDTH - 2f * profile.narrow) * 0.8f); // koridordan uzun olmasın
                o.h = 22f;
                o.x = profile.narrow + o.w / 2f + 10f + rng.nextFloat() * Math.max(0f, WIDTH - 2f * profile.narrow - o.w - 20f);
                o.speed = (rng.nextBoolean() ? 1f : -1f) * (1.1f + 0.9f * d);
                o.angle = rng.nextFloat() * 3.14f;
                o.y = -o.w / 2f - o.h;
                extraWait = (o.w * 0.5f) / profile.scroll(d);
                break;
            }
            case Obstacle.GATE: {
                o.h = 20f;
                float span = WIDTH - 2f * profile.narrow;
                o.w = span * (0.5f + 0.12f * rng.nextFloat());
                boolean left = rng.nextBoolean();
                o.x = left ? profile.narrow + o.w / 2f : WIDTH - profile.narrow - o.w / 2f;
                o.phase = rng.nextFloat() * Obstacle.GATE_WARN; // kapalı evrede başla
                o.y = -o.h;
                break;
            }
            case Obstacle.BOUNCER: {
                o.r = Obstacle.BOUNCER_R;
                o.x = 120f + rng.nextFloat() * (WIDTH - 240f);
                o.vx = (rng.nextBoolean() ? 1f : -1f) * (260f + 220f * d);
                o.y = -o.r;
                break;
            }
            case Obstacle.METEOR: {
                initMeteor(o);
                break;
            }
            case Obstacle.ORBITER: {
                float span = WIDTH - 2f * profile.narrow - 2f * Obstacle.ORBIT_R - 120f;
                o.x = span > 0f ? profile.narrow + Obstacle.ORBIT_R + 60f + rng.nextFloat() * span : WIDTH / 2f;
                o.speed = (rng.nextBoolean() ? 1f : -1f) * (1.4f + 0.8f * d);
                o.angle = rng.nextFloat() * 6.28f;
                o.y = -Obstacle.ORBIT_R - Obstacle.ORBIT_BALL;
                extraWait = Obstacle.ORBIT_R / profile.scroll(d);
                break;
            }
            default: { // ZIGZAG
                o.h = 34f;
                o.gapW = profile.gap(d) + 30f;
                float lo = profile.narrow + o.gapW / 2f + 20f, hi = WIDTH - profile.narrow - o.gapW / 2f - 20f;
                o.baseX = (lo + hi) / 2f;
                o.amp = Math.max(0f, Math.min(260f, (hi - lo) / 2f));
                o.speed = 1.1f + 0.7f * d;
                o.phase = rng.nextFloat() * 6.28f;
                o.gapX = o.baseX + (float) Math.sin(o.phase * o.speed) * o.amp;
                lastGapX = o.gapX;
                o.y = -o.h;
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
        float lo = minPlayerX() + 10f, hi = maxPlayerX() - 10f; // yalnızca oyuncunun erişebileceği alan
        dm.set(lo + rng.nextFloat() * Math.max(0f, hi - lo), y);
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
            switch (o.type) {
                case Obstacle.GATE: {
                    if (o.gateActive()) {
                        hit = Geometry.circleRect(px, py, pr, o.x - o.w / 2f, o.y - o.h / 2f, o.x + o.w / 2f, o.y + o.h / 2f);
                    }
                    break;
                }
                case Obstacle.BOUNCER: {
                    o.x += o.vx * dt;
                    float lo = profile.narrow + o.r, hi = WIDTH - profile.narrow - o.r;
                    if (o.x < lo) { o.x = lo; o.vx = -o.vx; }
                    if (o.x > hi) { o.x = hi; o.vx = -o.vx; }
                    hit = Geometry.circleCircle(px, py, pr, o.x, o.y, o.r * 0.9f);
                    break;
                }
                case Obstacle.METEOR: {
                    o.y += o.speed * dt; // akışa ek düşüş hızı
                    hit = Geometry.circleCircle(px, py, pr, o.x, o.y, o.r * 0.9f);
                    break;
                }
                case Obstacle.ORBITER: {
                    o.angle += o.speed * dt;
                    float cx = (float) Math.cos(o.angle) * Obstacle.ORBIT_R, cy = (float) Math.sin(o.angle) * Obstacle.ORBIT_R;
                    hit = Geometry.circleCircle(px, py, pr, o.x + cx, o.y + cy, Obstacle.ORBIT_BALL * 0.9f)
                            || Geometry.circleCircle(px, py, pr, o.x - cx, o.y - cy, Obstacle.ORBIT_BALL * 0.9f);
                    break;
                }
                case Obstacle.ZIGZAG: {
                    o.gapX = o.baseX + (float) Math.sin(o.phase * o.speed) * o.amp;
                    float top = o.y - o.h / 2f, bottom = o.y + o.h / 2f;
                    float gl = o.gapX - o.gapW / 2f, gr = o.gapX + o.gapW / 2f;
                    hit = Geometry.circleRect(px, py, pr, -50f, top, gl, bottom)
                            || Geometry.circleRect(px, py, pr, gr, top, WIDTH + 50f, bottom);
                    break;
                }
                default:
                    break;
            }
            if (hit && invuln <= 0f && !obstaclesHarmless && shield) {
                breakShield();
            } else if (hit && invuln <= 0f && !obstaclesHarmless) {
                combo = 0;
                comboTimer = 0f;
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
            if (magnetTimer > 0f) {
                float mx = px - dm.x, my = py - dm.y;
                float md2 = mx * mx + my * my;
                if (md2 < MAGNET_RANGE * MAGNET_RANGE && md2 > 1f) {
                    float ml = (float) Math.sqrt(md2);
                    float pull = 950f * dt;
                    dm.x += mx / ml * pull;
                    dm.y += my / ml * pull;
                }
            }
            dm.phase += dt;
            if (Geometry.dist2(px, py, dm.x, dm.y) < cr * cr) {
                diamonds++;
                addCombo(25);
                if (events != null) events.onEvent(GameEvents.DIAMOND, dm.x, dm.y);
                diamondPool.free(i);
            } else if (dm.y > height + 60f) {
                diamondPool.free(i);
            }
        }
    }

    private void initMeteor(Obstacle o) {
        o.type = Obstacle.METEOR;
        o.r = Obstacle.METEOR_R;
        o.x = profile.narrow + 60f + rng.nextFloat() * (WIDTH - 2f * profile.narrow - 120f);
        o.y = -520f;          // ekranın üstünde başlar: üstte uyarı işareti görünür
        o.speed = 520f;       // akışa ek düşüş hızı
        o.phase = 0f;
    }

    private void spawnMeteor(float d) {
        nextMeteorAt = time + profile.meteorInterval * (0.7f + 0.6f * rng.nextFloat()) * (1f - 0.3f * d);
        Obstacle o = obstacles.obtain();
        if (o == null) return;
        o.reset();
        initMeteor(o);
    }

    /** Yıldız grupları: tek, dikey çizgi, çapraz, yatay sıra, yay. */
    private void spawnStars() {
        DifficultyProfile p = profile;
        nextStarAt = time + p.starIntervalMin + rng.nextFloat() * (p.starIntervalMax - p.starIntervalMin);
        float baseY = -STAR_R * 2f;
        for (int i = 0; i < obstacles.size(); i++) {
            Obstacle o = obstacles.get(i);
            if (o.type != Obstacle.METEOR && Math.abs(o.y - baseY) < o.halfExtent() + 80f) {
                nextStarAt = time + 0.2f;
                return;
            }
        }
        float lo = minPlayerX() + 10f, hi = maxPlayerX() - 10f;
        int pattern = rng.nextInt(5);
        int n = pattern == 0 ? 1 : 3;
        float x = lo + 120f + rng.nextFloat() * Math.max(1f, hi - lo - 240f);
        for (int k = 0; k < n; k++) {
            Diamond s = starPool.obtain();
            if (s == null) return;
            float sx = x, sy = baseY;
            switch (pattern) {
                case 1: sy = baseY - k * 85f; break;                                     // dikey
                case 2: sx = x + (k - 1) * 90f; sy = baseY - k * 80f; break;            // çapraz
                case 3: sx = x + (k - 1) * 110f; break;                                  // yatay
                case 4: sx = x + (k - 1) * 105f; sy = baseY - (k == 1 ? 60f : 0f); break; // yay
                default: break;
            }
            s.set(Geometry.clamp(sx, lo, hi), sy);
        }
    }

    private void updateStars(float dt) {
        float cr = PLAYER_R + STAR_R + 8f;
        for (int i = starPool.size() - 1; i >= 0; i--) {
            Diamond st = starPool.get(i);
            st.y += scroll * dt;
            st.phase += dt;
            if (magnetTimer > 0f) {
                float mx = px - st.x, my = py - st.y;
                float md2 = mx * mx + my * my;
                if (md2 < MAGNET_RANGE * MAGNET_RANGE && md2 > 1f) {
                    float ml = (float) Math.sqrt(md2);
                    st.x += mx / ml * 950f * dt;
                    st.y += my / ml * 950f * dt;
                }
            }
            if (Geometry.dist2(px, py, st.x, st.y) < cr * cr) {
                starPool.free(i);
                collectStar(st.x, st.y);
                if (state != RUNNING) return;
            } else if (st.y > height + 60f) {
                starPool.free(i);
            }
        }
    }

    private void collectStar(float x, float y) {
        stageStars++;
        totalStars++;
        addCombo(10);
        if (events != null) events.onEvent(GameEvents.STAR, x, y);
        if (stageStars >= MissionDef.STARS_PER_STAGE) {
            if (levelStage < MissionDef.STAGES) {
                levelStage++;
                stageStars = 0;
                // 2. aşama: ikinci gölge katılır
                if (profile.shadowDelays.length > 1) addShadow(profile.shadowDelays[1]);
                if (events != null) events.onEvent(GameEvents.LEVEL_STAGE, px, py);
            } else {
                state = WON;
                if (events != null) events.onEvent(GameEvents.WIN, px, py);
            }
        }
    }

    /** Test için yıldız ekleme. */
    public Diamond addStar(float x, float y) {
        Diamond s = starPool.obtain();
        if (s != null) s.set(x, y);
        return s;
    }

    private void breakShield() {
        shield = false;
        invuln = 1.5f;
        shadowGrace = 1.5f;
        if (events != null) events.onEvent(GameEvents.SHIELD_BREAK, px, py);
    }

    private void spawnPowerup() {
        DifficultyProfile p = profile;
        nextPowerupAt = time + p.powerupIntervalMin + rng.nextFloat() * (p.powerupIntervalMax - p.powerupIntervalMin);
        float y = -POWERUP_R * 2f;
        for (int i = 0; i < obstacles.size(); i++) {
            Obstacle o = obstacles.get(i);
            if (Math.abs(o.y - y) < o.halfExtent() + 100f) {
                nextPowerupAt = time + 0.3f;
                return;
            }
        }
        PowerUp pu = powerups.obtain();
        if (pu == null) return;
        int type = rng.nextInt(PowerUp.COUNT);
        if (type == PowerUp.SHIELD && shield) type = PowerUp.MAGNET;
        float lo = minPlayerX() + 20f, hi = maxPlayerX() - 20f; // yalnızca oyuncunun erişebileceği alan
        pu.set(type, lo + rng.nextFloat() * Math.max(0f, hi - lo), y);
    }

    /** Test ve öğretici için güçlendirme ekleme. */
    public PowerUp addPowerup(int type, float x, float y) {
        PowerUp pu = powerups.obtain();
        if (pu != null) pu.set(type, x, y);
        return pu;
    }

    private void updatePowerups(float dt) {
        float cr = PLAYER_R + POWERUP_R + 6f;
        for (int i = powerups.size() - 1; i >= 0; i--) {
            PowerUp pu = powerups.get(i);
            pu.y += scroll * dt;
            pu.phase += dt;
            if (Geometry.dist2(px, py, pu.x, pu.y) < cr * cr) {
                powerupsCollected++;
                lastPowerup = pu.type;
                switch (pu.type) {
                    case PowerUp.SHIELD: shield = true; break;
                    case PowerUp.SLOW: slowTimer = SLOW_TIME; break;
                    default: magnetTimer = MAGNET_TIME; break;
                }
                if (events != null) events.onEvent(GameEvents.POWERUP, pu.x, pu.y);
                powerups.free(i);
            } else if (pu.y > height + 60f) {
                powerups.free(i);
            }
        }
    }

    private void checkMission() {
        // Bölüm sonu yıldız toplamayla belirlenir (collectStar).
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
        out.maxCombo = maxCombo;
        out.powerups = powerupsCollected;
        out.stars = (mission != null && out.won) ? mission.stars(out) : 0;
        return out;
    }
}
