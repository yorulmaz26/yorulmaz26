package com.golgen.oyun.core;

/**
 * Görev modu bölüm tanımı. Her bölüm iki aşamalıdır:
 * 1. aşamada 1 gölge, 2. aşamada 2 gölge vardır; her aşama {@link #STARS_PER_STAGE} yıldız toplanınca biter.
 * Her 5 bölüm aynı haritada geçer; bölüm ilerledikçe haritanın imza engeli, özellik gücü ve hız artar.
 */
public final class MissionDef {
    public static final int STAGES = 2;
    public static final int STARS_PER_STAGE = 20;
    /** Eski arayüz/test uyumluluğu için tür sabiti (tüm bölümler yıldız toplama). */
    public static final int STAR_STAGES = 5;
    public static final int NO_HIT = 3;

    public final int index;       // 0..99
    public final int mapIndex;    // 0..19
    public final int sub;         // haritadaki sıra 0..4
    public final MapTheme map;
    public final String title;
    public final int type = STAR_STAGES;
    public final int target = STARS_PER_STAGE * STAGES;
    public final float timeOffset;
    public final int reward;
    public final int starCombo;   // 3. taç için gereken kombo
    public final boolean boss;    // haritanın son bölümü
    public final float intensity; // özellik gücü 0.6..1.2

    public MissionDef(int index, MapTheme map, int sub) {
        this.index = index;
        this.map = map;
        this.mapIndex = map.id;
        this.sub = sub;
        this.boss = sub == 4;
        this.title = "Bölüm " + (index + 1);
        this.timeOffset = map.id * 7f + sub * 5f;
        this.reward = 20 + map.id * 6 + sub * 4 + (boss ? 30 : 0);
        this.starCombo = 6 + map.id / 2 + sub + (boss ? 2 : 0);
        this.intensity = 0.6f + 0.12f * sub + 0.02f * map.id;
    }

    /** Eski isim: bölümün haritası (0..19). */
    public int chapter() {
        return mapIndex;
    }

    public boolean has(int feature) {
        return map.has(feature);
    }

    public String goal() {
        return "2 aşama · her aşamada " + STARS_PER_STAGE + " yıldız topla";
    }

    public String modsText() {
        return map.featuresText();
    }

    public String description() {
        return map.name + " — " + goal();
    }

    public String crownText() {
        return "Tamamla · Çarpmadan bitir · " + starCombo + " kombo";
    }

    /** Toplam yıldız ilerlemesi (0..40). */
    public int progress(GameWorld w) {
        return (w.levelStage - 1) * STARS_PER_STAGE + w.stageStars;
    }

    /** Taç: 1 tamamlama + 1 hiç çarpmama + 1 kombo hedefi. */
    public int stars(RunResult r) {
        if (!r.won) return 0;
        int s = 1;
        if (r.hits == 0) s++;
        if (r.maxCombo >= starCombo) s++;
        return s;
    }

    /** Bu bölüm için zorluk profili. */
    public DifficultyProfile profile() {
        DifficultyProfile p = DifficultyProfile.endless();
        p.timeOffset = timeOffset;
        p.rampSeconds = 140f;
        // Gölgeler bölüm mantığıyla doğar (aşamalar); otomatik takvim kapalı.
        float shrink = 1f - 0.03f * sub;
        float d1 = map.shadowDelay1 * shrink, d2 = map.shadowDelay2 * shrink;
        if (map.has(MapTheme.F_FAST_SHADOWS)) { d1 *= 0.8f; d2 *= 0.8f; }
        d1 = Math.max(1.7f, d1);
        d2 = Math.max(2.4f, d2);
        p.shadowDelays = new float[]{d1, d2};
        p.shadowSpawnTimes = new float[]{d1 + 0.4f};
        p.maxShadows = 2;
        // Engel karışımı: imza engel bölümle birlikte sıklaşır
        float[] w = map.weights.clone();
        for (int i = 0; i < w.length; i++) {
            if (Obstacle.WEIGHT_ORDER[i] == map.signature) w[i] += sub * 0.6f + (boss ? 1.5f : 0f);
        }
        p.weights = w;
        // Özellikler
        float k = intensity;
        if (map.has(MapTheme.F_WIND)) p.wind = 140f * k;
        if (map.has(MapTheme.F_ICE)) p.iceGrip = 3.2f / k;
        if (map.has(MapTheme.F_NARROW)) p.narrow = 70f + 40f * sub;
        if (map.has(MapTheme.F_STORM)) p.meteorInterval = 2.6f / k;
        if (map.has(MapTheme.F_RUSH)) p.scrollMul = 1.12f + 0.03f * sub;
        p.intervalStart = 1.9f - 0.04f * sub;
        // Yıldız sıklığı: harita ve bölüm ilerledikçe seyrekleşir (aşama başına ~40-70 sn)
        p.starIntervalMin = Math.min(3.4f, 2.4f + 0.03f * map.id + 0.1f * sub);
        p.starIntervalMax = p.starIntervalMin + 1.2f;
        p.diamondIntervalMin = 3.5f;
        p.diamondIntervalMax = 6f;
        p.powerupIntervalMin = 10f;
        p.powerupIntervalMax = 16f;
        return p;
    }
}
