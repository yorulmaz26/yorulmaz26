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
    public static final float MIN_DELAY1 = 1.75f, MIN_DELAY2 = 2.55f, FAST_FACTOR = 0.88f, NARROW_FACTOR = 1.12f;

    public final int index;       // 0..99
    public final int mapIndex;    // 0..19
    public final int sub;         // haritadaki sıra 0..4
    public final MapTheme map;
    public final int type = STAR_STAGES;
    public final int target = STARS_PER_STAGE * STAGES;
    public final float timeOffset;
    public final int reward;
    public final int starCombo;   // 3. taç için gereken kombo
    public final boolean boss;    // haritanın son bölümü
    public final float intensity; // özellik gücü 0.6..1.2

    /**
     * @deprecated {@link #title()} kullanın. Eski çağıranlar ({@code "…" + m.title}) derlenmeye devam etsin diye
     * dize birleştirmede o anki dildeki başlığa dönüşen köprü; metin saklanmaz, her seferinde üretilir.
     */
    @Deprecated
    public final Object title = new Object() {
        @Override public String toString() { return title(); }
    };

    // Yerelleştirilmiş metin önbelleği (dil değişince yeniden üretilir)
    private String cTitle, cDesc;
    private volatile int cVer = Integer.MIN_VALUE;

    public MissionDef(int index, MapTheme map, int sub) {
        this.index = index;
        this.map = map;
        this.mapIndex = map.id;
        this.sub = sub;
        this.boss = sub == 4;
        // 500 bölüm boyunca yavaş artan zorluk (erken tavana vurmaz)
        this.timeOffset = Math.min(150f, map.id * 1.5f + sub * 4f);
        this.reward = Math.min(150, 20 + map.id + sub * 4 + (boss ? 30 : 0));
        this.starCombo = Math.min(30, 6 + map.id / 5 + sub + (boss ? 2 : 0));
        this.intensity = Math.min(1.5f, 0.6f + 0.1f * sub + 0.006f * map.id);
    }

    /** Eski isim: bölümün haritası (0..19). */
    public int chapter() {
        return mapIndex;
    }

    public boolean has(int feature) {
        return map.has(feature);
    }

    /** Bölüm başlığı o anki dilde ("Bölüm 12"). */
    public String title() {
        refresh();
        return cTitle;
    }

    /** Bölüm hedefi ("2 aşama · her aşamada 20 yıldız topla"). */
    public String goal() {
        return L10n.p("level.goal", STARS_PER_STAGE, STAGES);
    }

    public String modsText() {
        return map.featuresText();
    }

    /** "Harita adı — hedef". */
    public String description() {
        refresh();
        return cDesc;
    }

    /** Üç taç koşulu: tamamla · çarpmadan bitir · N kombo. */
    public String crownText() {
        return L10n.p("level.crowns", starCombo);
    }

    private void refresh() {
        int v = L10n.version();
        if (v == cVer) return;
        cTitle = L10n.f("level.title", index + 1);
        cDesc = L10n.f("level.desc", map.name(), goal());
        cVer = v;
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
        // Tavan sonrası ek hız da harita ilerledikçe açılır
        p.overdriveMax = Math.min(220f, map.id * 2.2f);
        // Gölgeler bölüm mantığıyla doğar (aşamalar); otomatik takvim kapalı.
        // Gölge gecikmesi 500 bölüm boyunca tek bir eğriyle azalır (kademe başında zorluk sıfırlanmaz);
        // haritanın kendi karakteri ±%6 renk katar. "Hızlı Gölgeler" tabandan SONRA uygulanır, her bölümde etkilidir.
        float g = index / (float) (Missions.MAPS * Missions.PER_MAP - 1);
        float flavor = Math.max(0.94f, Math.min(1.06f, map.shadowDelay1 / 2.7f));
        float d1 = Math.max(MIN_DELAY1, (3.0f - 1.25f * g) * flavor);
        float d2 = Math.max(MIN_DELAY2, (4.4f - 1.7f * g) * flavor);
        if (map.has(MapTheme.F_FAST_SHADOWS)) { d1 *= FAST_FACTOR; d2 *= FAST_FACTOR; }
        // Dar koridorda kaçış alanı az: gölgeler biraz daha geriden gelir
        if (map.has(MapTheme.F_NARROW)) { d1 *= NARROW_FACTOR; d2 *= NARROW_FACTOR; }
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
        if (map.has(MapTheme.F_RUSH)) p.scrollMul = 1.1f + 0.02f * sub + 0.0006f * map.id;
        if (map.tier() >= 4 && boss) p.lives = 2; // Efsane kademe harita sonları: 2 can
        p.intervalStart = 1.9f - 0.04f * sub;
        // Yıldız sıklığı: harita ve bölüm ilerledikçe seyrekleşir (aşama başına ~40-70 sn)
        p.starIntervalMin = Math.min(3.4f, 2.4f + 0.006f * map.id + 0.1f * sub);
        p.starIntervalMax = p.starIntervalMin + 1.2f;
        p.diamondIntervalMin = 3.5f;
        p.diamondIntervalMax = 6f;
        p.powerupIntervalMin = 10f;
        p.powerupIntervalMax = 16f;
        return p;
    }
}
