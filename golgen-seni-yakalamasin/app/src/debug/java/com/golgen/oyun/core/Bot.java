package com.golgen.oyun.core;

// YALNIZCA DEBUG DERLEMESI: tanitim videosu ve ekran goruntusu cekimi icin otomatik pilot (yayin APK'sina girmez).

/**
 * İleri-simülasyonlu kaçınma botu: her karar anında dünya durumunu kopyalar, aday hedef noktalarını
 * (ve gerektiğinde iki parçalı hedef dizilerini) H saniye ileri oynatır, çarpma/ölüm/gölge yakınlığı/
 * yaklaşan duvar boşluğu/yıldız uzaklığına göre puanlar ve en iyisini seçer.
 */
public final class Bot {
    final GameWorld sim;
    float horizon = 0.75f;     // sn (oyun zamanı)
    int decideEvery = 4;       // kare (1/60) başına karar sıklığı
    public boolean strong = false;    // doğrulama modu: daha geniş arama
    private float lastTx = -1, lastTy = -1;
    private final float[] tmp = new float[2];
    private int frame;
    long rollouts;

    public Bot(GameWorld template) {
        sim = new GameWorld(1);
    }

    /** Gerçek dünyada bir sonraki karar. */
    // İnsan benzeri mod: tepki gecikmesi (kare), gelecekteki doğumları bilmeme, geniş arama yok
    boolean human = Boolean.getBoolean("bot.human");
    int reaction = Integer.getInteger("bot.reaction", 12);
    private GameWorld[] ring;

    // KARANLIK modeli: yalnızca oyuncu çevresinde R içinde görülmüş engeller bilinir; gelecekteki doğumlar bilinmez
    static final float DARK_R = Float.parseFloat(System.getProperty("bot.dark", "0"));
    static final boolean NOFUTURE = Boolean.getBoolean("bot.nofuture");
    boolean darkOn = false;
    static final boolean BAR_ENOUGH = Boolean.getBoolean("bot.bar");
    final java.util.IdentityHashMap<Obstacle, float[]> seen = new java.util.IdentityHashMap<>();
    static boolean visible(GameWorld w, Obstacle o, float R) {
        float px = w.px, py = w.py;
        switch (o.type) {
            case Obstacle.WALL: case Obstacle.ZIGZAG: {
                if (BAR_ENOUGH) return Math.abs(o.y - py) < R; // iyimser: çubuğu görünce boşluğun yerini bilir
                float gl = o.gapX - o.gapW / 2f, gr = o.gapX + o.gapW / 2f;
                float cx = Math.max(gl, Math.min(px, gr));
                return Geometry.dist2(px, py, cx, o.y) < R * R;
            }
            case Obstacle.GATE: case Obstacle.SWEEPER: {
                float cx = Math.max(o.x - o.w / 2f, Math.min(px, o.x + o.w / 2f));
                float cy = Math.max(o.y - o.h / 2f, Math.min(py, o.y + o.h / 2f));
                return Geometry.dist2(px, py, cx, cy) < R * R;
            }
            case Obstacle.ROTOR: return Math.sqrt(Geometry.dist2(px, py, o.x, o.y)) - o.w / 2f < R;
            case Obstacle.ORBITER: return Math.sqrt(Geometry.dist2(px, py, o.x, o.y)) - Obstacle.ORBIT_R - Obstacle.ORBIT_BALL < R;
            default: return Math.sqrt(Geometry.dist2(px, py, o.x, o.y)) - o.r < R;
        }
    }
    boolean known(Obstacle o) { if (!darkOn) return true; float[] v = seen.get(o); return v != null && v[1] > 0; }
    void updateSeen(GameWorld w) {
        if (!darkOn) return;
        for (int i = 0; i < w.obstacles.size(); i++) {
            Obstacle o = w.obstacles.get(i);
            float[] v = seen.get(o);
            if (v == null || o.y < v[0] - 100f) { v = new float[]{o.y, 0}; seen.put(o, v); }
            v[0] = o.y;
            if (v[1] == 0 && visible(w, o, DARK_R)) v[1] = 1;
        }
    }

    public void act(GameWorld w) {
        updateSeen(w);
        if (human) {
            if (ring == null) {
                ring = new GameWorld[reaction + 1];
                for (int i = 0; i < ring.length; i++) { ring[i] = new GameWorld(1); ring[i].setArenaHeight(w.height); }
                decideEvery = Integer.getInteger("bot.every", 6);
                horizon = 0.6f;
            }
            int f = frame++;
            WorldCopier.copy(w, ring[f % ring.length]);
            if (f % decideEvery == 0 && f >= reaction) {
                float[] best = plan(ring[(f - reaction) % ring.length]);
                lastTx = best[0];
                lastTy = best[1];
            }
            if (lastTx >= 0) w.setTarget(lastTx, lastTy);
            return;
        }
        if ((frame++ % decideEvery) != 0 && lastTx >= 0) {
            w.setTarget(lastTx, lastTy);
            return;
        }
        float[] best = plan(w);
        lastTx = best[0];
        lastTy = best[1];
        w.setTarget(lastTx, lastTy);
    }

    private float[] candX = new float[400], candY = new float[400];
    private int nc;

    private void add(GameWorld w, float x, float y) {
        if (nc >= candX.length) return;
        candX[nc] = Geometry.clamp(x, w.minPlayerX(), w.maxPlayerX());
        candY[nc] = Geometry.clamp(y, w.minPlayerY(), w.maxPlayerY());
        nc++;
    }

    private void buildCandidates(GameWorld w, boolean wide) {
        nc = 0;
        add(w, w.px, w.py);
        if (lastTx >= 0) add(w, lastTx, lastTy);
        int dirs = wide ? 16 : 10;
        float[] radii = wide ? new float[]{90f, 200f, 380f, 650f} : new float[]{160f, 420f};
        for (float r : radii) {
            for (int k = 0; k < dirs; k++) {
                double a = k * 2 * Math.PI / dirs + (wide ? 0.1 : 0);
                add(w, w.px + (float) Math.cos(a) * r, w.py + (float) Math.sin(a) * r);
            }
        }
        // Yıldızlar
        for (int i = 0; i < w.starPool.size(); i++) {
            Diamond s = w.starPool.get(i);
            if (s.y < w.minPlayerY() - 600f) continue;
            add(w, s.x, Math.max(s.y, w.minPlayerY()));
            add(w, s.x, w.py);
        }
        // Güçlendirmeler (kalkan özellikle)
        for (int i = 0; i < w.powerups.size(); i++) {
            PowerUp p = w.powerups.get(i);
            add(w, p.x, Math.max(p.y, w.minPlayerY()));
        }
        // Duvar boşlukları
        for (int i = 0; i < w.obstacles.size(); i++) {
            Obstacle o = w.obstacles.get(i);
            if ((o.type == Obstacle.WALL || o.type == Obstacle.ZIGZAG) && o.y < w.py + 30f && known(o)) {
                float ta = (w.py - o.y) / Math.max(100f, w.scroll);
                float gx = o.gapX;
                if (o.type == Obstacle.ZIGZAG) gx = o.baseX + (float) Math.sin((o.phase + ta) * o.speed) * o.amp;
                add(w, gx, w.py);
                add(w, gx, w.py + 200f);
                add(w, gx, w.py - 200f);
            }
        }
        // Sabit ızgara noktaları (geniş aramada)
        if (wide) {
            for (int gx = 0; gx <= 6; gx++)
                for (int gy = 0; gy <= 6; gy++)
                    add(w, w.minPlayerX() + (w.maxPlayerX() - w.minPlayerX()) * gx / 6f,
                            w.minPlayerY() + (w.maxPlayerY() - w.minPlayerY()) * gy / 6f);
        }
    }

    float[] plan(GameWorld w) {
        buildCandidates(w, strong);
        double bestScore = -Double.MAX_VALUE;
        float bx = w.px, by = w.py;
        for (int i = 0; i < nc; i++) {
            double sc = rollout(w, candX[i], candY[i], -1, -1, 0f, horizon);
            if (i == 1 && lastTx >= 0) sc += 300;  // süreklilik
            if (sc > bestScore) { bestScore = sc; bx = candX[i]; by = candY[i]; }
        }
        if (bestScore < -1e6 && !human) {
            // Tehlike: iki parçalı aramaya geç (ilk hedefe t1 boyunca, sonra ikinci hedefe)
            int n1 = nc;
            float[] ax = java.util.Arrays.copyOf(candX, n1), ay = java.util.Arrays.copyOf(candY, n1);
            buildCandidates(w, true);
            int n2 = nc;
            float[] t1s = strong ? new float[]{0.12f, 0.25f, 0.4f} : new float[]{0.15f, 0.3f};
            float h2 = strong ? Math.max(horizon, 1.0f) : horizon;
            for (int i = 0; i < n2; i++) {
                double sc = rollout(w, candX[i], candY[i], -1, -1, 0f, h2);
                if (sc > bestScore) { bestScore = sc; bx = candX[i]; by = candY[i]; }
            }
            int step = strong ? 2 : 3;
            for (float t1 : t1s) {
                for (int i = 0; i < n1; i += 1) {
                    for (int j = (i % step); j < n2; j += step) {
                        double sc = rollout(w, ax[i], ay[i], candX[j], candY[j], t1, h2);
                        if (sc > bestScore) { bestScore = sc; bx = ax[i]; by = ay[i]; }
                    }
                }
            }
        }
        lastScore = bestScore;
        return new float[]{bx, by};
    }

    double lastScore;

    /** Hedef (x1,y1)'e t1 boyunca, sonra (x2,y2)'ye (x2<0 ise aynı hedef) h süresince ilerler. */
    double rollout(GameWorld w, float x1, float y1, float x2, float y2, float t1, float h) {
        rollouts++;
        GameWorld s = sim;
        WorldCopier.copy(w, s);
        if (darkOn) { for (int i = s.obstacles.size() - 1; i >= 0; i--) if (!known(w.obstacles.get(i))) s.obstacles.free(i); }
        if (darkOn || NOFUTURE) { s.spawnObstacles = false; }
        if (human) { s.spawnObstacles = false; s.spawnPowerups = false; s.spawnDiamonds = false; }
        int stars0 = s.totalStars, hits0 = s.hits;
        boolean shield0 = s.shield;
        float t0 = s.time;
        s.setTarget(x1, y1);
        boolean switched = x2 < 0;
        int steps = Math.round(h / GameWorld.STEP);
        int st = 0;
        double minShadow = 1e9;
        for (; st < steps && s.state == GameWorld.RUNNING; st++) {
            if (!switched && s.time - t0 >= t1) switched = true;
            // gerçek oyunda bot hedefi her karede (2 adım) yeniden yazar; rüzgâr kaymasını aynı şekilde modelle
            if ((st & 1) == 0) { if (switched && x2 >= 0) s.setTarget(x2, y2); else s.setTarget(x1, y1); }
            s.step(GameWorld.STEP);
            for (int i = 0; i < s.shadows.size(); i++) {
                Shadow sh = s.shadows.get(i);
                double d = Math.sqrt(Geometry.dist2(s.px, s.py, sh.x, sh.y));
                if (d < minShadow) minShadow = d;
            }
        }
        double score = 0;
        int starsGained = s.totalStars - stars0;
        if (s.state == GameWorld.WON) return 1e9 - (s.hits - hits0) * 2e9 - ((shield0 && !s.shield) ? 2e7 : 0);
        score += starsGained * 4000.0;
        if (s.state == GameWorld.DEAD) {
            score -= 1e10;
            score += (s.time - t0) * 1e9; // geç ölüm daha iyi
            return score;
        }
        score -= (s.hits - hits0) * 5e8;
        if (shield0 && !s.shield) score -= 2e7;
        if (minShadow < 110) score -= (110 - minShadow) * 2000;
        score -= endPenalty(s);
        return score;
    }

    private double endPenalty(GameWorld s) {
        double pen = 0;
        float px = s.px, py = s.py;
        // Gölgelerin gelecekteki konumları (kendi geçmiş yolumuz)
        for (int i = 0; i < s.shadows.size(); i++) {
            Shadow sh = s.shadows.get(i);
            for (int k = 0; k <= 12; k++) {
                float t = s.time - sh.delay + k * 0.1f;
                if (t > s.time) break;
                s.recorder.sample(t, tmp);
                double d = Math.sqrt(Geometry.dist2(px, py, tmp[0], tmp[1]));
                double wk = 1.0 - k / 14.0;
                if (d < 220) pen += (220 - d) * (220 - d) * 0.6 * wk;
            }
        }
        // 1. aşamada gölge yakında doğacaksa doğma noktası = (time - d1) konumu
        if (s.shadows.size() == 0) {
            DifficultyProfile p = s.profile();
            float at = p.shadowSpawnTimes[0];
            if (at - s.time < 1.2f && at > s.time - 0.01f) {
                s.recorder.sample(at - p.shadowDelays[0] + 1.0f, tmp);
                double d = Math.sqrt(Geometry.dist2(px, py, tmp[0], tmp[1]));
                if (d < 220) pen += (220 - d) * (220 - d) * 0.6;
            }
        }
        float sc = Math.max(100f, s.scroll);
        for (int i = 0; i < s.obstacles.size(); i++) {
            Obstacle o = s.obstacles.get(i);
            switch (o.type) {
                case Obstacle.WALL:
                case Obstacle.ZIGZAG: {
                    if (o.y > py + 40f) break;
                    float ta = (py - o.y - 45f) / sc;
                    if (ta > 1.6f) break;
                    if (ta < 0f) ta = 0f;
                    float gx = o.gapX;
                    if (o.type == Obstacle.ZIGZAG) gx = o.baseX + (float) Math.sin((o.phase + ta) * o.speed) * o.amp;
                    float need = Math.abs(px - gx) - (o.gapW / 2f - 38f);
                    if (need > 0) pen += need * 60.0 / (ta + 0.12);
                    break;
                }
                case Obstacle.MINE: {
                    float ta = 0.25f;
                    double d = Math.sqrt(Geometry.dist2(px, py, o.x, o.y + sc * ta)) - o.r * 1.18f;
                    if (d < 90) pen += (90 - d) * (90 - d) * 3;
                    break;
                }
                case Obstacle.BOUNCER: {
                    for (float ta = 0f; ta <= 0.5f; ta += 0.25f) {
                        float bx = o.x + o.vx * ta;
                        double d = Math.sqrt(Geometry.dist2(px, py, bx, o.y + sc * ta)) - o.r;
                        if (d < 90) pen += (90 - d) * (90 - d) * 3;
                    }
                    break;
                }
                case Obstacle.METEOR: {
                    // Meteor düşey çizgisinde, oyuncunun üstündeyse tehlikeli
                    if (o.y < py + 30f) {
                        float dx = Math.abs(px - o.x);
                        float ta = (py - o.y) / (sc + o.speed);
                        if (dx < 90 && ta < 1.5f) pen += (90 - dx) * (90 - dx) * 4 / (ta + 0.2);
                    }
                    break;
                }
                case Obstacle.ORBITER: {
                    double d = Math.sqrt(Geometry.dist2(px, py, o.x, o.y + sc * 0.2f));
                    double ring = Math.abs(d - Obstacle.ORBIT_R);
                    if (d < Obstacle.ORBIT_R + 90) pen += Math.max(0, 90 - ring) * Math.max(0, 90 - ring) * 2;
                    break;
                }
                case Obstacle.ROTOR: {
                    double d = Math.sqrt(Geometry.dist2(px, py, o.x, o.y + sc * 0.2f));
                    if (d < o.w / 2f + 60f) pen += (o.w / 2f + 60f - d) * 8;
                    break;
                }
                case Obstacle.SWEEPER: {
                    if (o.y < py + 40 && py - o.y < 500) {
                        float ta = (py - o.y) / sc;
                        float ox = o.baseX + (float) Math.sin((o.phase + ta) * o.speed) * o.amp;
                        float dx = Math.abs(px - ox) - o.w / 2f;
                        if (dx < 70) pen += (70 - dx) * 30 / (ta + 0.2);
                    }
                    break;
                }
                case Obstacle.GATE: {
                    if (o.y < py + 40 && py - o.y < 600) {
                        float ta = (py - o.y) / sc;
                        float ph = (o.phase + ta) % Obstacle.GATE_CYCLE;
                        boolean inSpan = px > o.x - o.w / 2f - 40f && px < o.x + o.w / 2f + 40f;
                        if (inSpan && ph >= Obstacle.GATE_WARN - 0.2f) pen += 4000 / (ta + 0.2);
                    }
                    break;
                }
            }
        }
        // Yıldızlara yönel
        double bestStar = 1e9;
        for (int i = 0; i < s.starPool.size(); i++) {
            Diamond st = s.starPool.get(i);
            if (st.y > py + 60f) continue;
            double d = Math.sqrt(Geometry.dist2(px, py, st.x, st.y));
            if (d < bestStar) bestStar = d;
        }
        if (bestStar < 1e8) pen += bestStar * 1.2;
        // Kalkan topla
        if (!s.shield) {
            for (int i = 0; i < s.powerups.size(); i++) {
                PowerUp p = s.powerups.get(i);
                if (p.type == PowerUp.SHIELD && p.y < py) pen += Math.sqrt(Geometry.dist2(px, py, p.x, p.y)) * 0.6;
            }
        }
        // Konum: biraz aşağıda dur (tepki süresi), kenarlardan uzak
        float midY = s.height * 0.66f;
        pen += Math.abs(py - midY) * 0.4;
        float edge = Math.min(px - s.minPlayerX(), s.maxPlayerX() - px);
        if (edge < 80) pen += (80 - edge) * 6;
        return pen;
    }

    /** Oyuncuya çarpan engelin türü (HIT olayı anında). */
    static int hitType(GameWorld w) {
        float pr = GameWorld.PLAYER_R * 0.9f + 4f;
        float px = w.px, py = w.py;
        for (int i = 0; i < w.obstacles.size(); i++) {
            Obstacle o = w.obstacles.get(i);
            boolean hit = false;
            switch (o.type) {
                case Obstacle.WALL:
                case Obstacle.ZIGZAG: {
                    float top = o.y - o.h / 2f, bottom = o.y + o.h / 2f;
                    float gl = o.gapX - o.gapW / 2f, gr = o.gapX + o.gapW / 2f;
                    hit = Geometry.circleRect(px, py, pr, -50f, top, gl, bottom) || Geometry.circleRect(px, py, pr, gr, top, WIDTHP, bottom);
                    break;
                }
                case Obstacle.MINE: hit = Geometry.circleCircle(px, py, pr, o.x, o.y, o.mineRadius()); break;
                case Obstacle.SWEEPER: hit = Geometry.circleRect(px, py, pr, o.x - o.w / 2f, o.y - o.h / 2f, o.x + o.w / 2f, o.y + o.h / 2f); break;
                case Obstacle.ROTOR: {
                    float hx = (float) Math.cos(o.angle) * o.w / 2f, hy = (float) Math.sin(o.angle) * o.w / 2f;
                    hit = Geometry.circleSegment(px, py, pr, o.x - hx, o.y - hy, o.x + hx, o.y + hy, o.h);
                    break;
                }
                case Obstacle.GATE: hit = o.gateActive() && Geometry.circleRect(px, py, pr, o.x - o.w / 2f, o.y - o.h / 2f, o.x + o.w / 2f, o.y + o.h / 2f); break;
                case Obstacle.BOUNCER:
                case Obstacle.METEOR: hit = Geometry.circleCircle(px, py, pr, o.x, o.y, o.r); break;
                case Obstacle.ORBITER: {
                    float cx = (float) Math.cos(o.angle) * Obstacle.ORBIT_R, cy = (float) Math.sin(o.angle) * Obstacle.ORBIT_R;
                    hit = Geometry.circleCircle(px, py, pr, o.x + cx, o.y + cy, Obstacle.ORBIT_BALL)
                            || Geometry.circleCircle(px, py, pr, o.x - cx, o.y - cy, Obstacle.ORBIT_BALL);
                    break;
                }
            }
            if (hit) return o.type;
        }
        return -1;
    }

    private static final float WIDTHP = GameWorld.WIDTH + 50f;
}
