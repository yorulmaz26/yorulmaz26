package com.golgen.oyun.core;

/** Görev modu seviye tanımı (veri nesnesi). */
public final class MissionDef {
    public static final int SURVIVE = 0, DODGE = 1, COLLECT = 2, NO_HIT = 3, SCORE = 4;

    // Zorlaştırıcılar (bit maskesi)
    public static final int MOD_DARK = 1;          // görüş oyuncu çevresiyle sınırlı
    public static final int MOD_FAST_SHADOWS = 2;  // gölgeler daha kısa gecikmeyle gelir
    public static final int MOD_SWARM = 4;         // gölgeler daha erken ve sık doğar
    public static final int MOD_RUSH = 8;          // akış %25 hızlı
    public static final String[] MOD_NAMES = {"Karanlık", "Hızlı Gölgeler", "Gölge Akını", "Hızlı Akış"};

    public final int index;
    public final int chapter;
    public final String title;
    public final int type;
    public final int target;
    public final float timeOffset;
    public final int reward;
    public final int mods;
    public final int starCombo;   // 3. yıldız için gereken en yüksek kombo
    public final boolean boss;    // bölüm sonu

    public MissionDef(int index, int chapter, String title, int type, int target, float timeOffset, int reward,
                      int mods, int starCombo, boolean boss) {
        this.index = index;
        this.chapter = chapter;
        this.title = title;
        this.type = type;
        this.target = target;
        this.timeOffset = timeOffset;
        this.reward = reward;
        this.mods = mods;
        this.starCombo = starCombo;
        this.boss = boss;
    }

    /** Geriye dönük uyumlu kısa kurucu (zorlaştırıcısız). */
    public MissionDef(int index, String title, int type, int target, float timeOffset, int reward) {
        this(index, 0, title, type, target, timeOffset, reward, 0, 4, false);
    }

    public boolean has(int mod) {
        return (mods & mod) != 0;
    }

    public String goal() {
        switch (type) {
            case SURVIVE: return target + " saniye hayatta kal";
            case DODGE: return target + " kez gölgenden kıl payı kaç";
            case COLLECT: return target + " elmas topla";
            case SCORE: return target + " puana ulaş";
            default: return "Engellere çarpmadan " + target + " saniye dayan";
        }
    }

    public String modsText() {
        if (mods == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MOD_NAMES.length; i++) {
            if ((mods & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(MOD_NAMES[i]);
            }
        }
        return sb.toString();
    }

    public String description() {
        String m = modsText();
        return m.isEmpty() ? goal() : m + " — " + goal();
    }

    public String starText() {
        return "★ Tamamla   ★ Çarpmadan   ★ x" + (1 + Math.min(4, starCombo / 4)) + " kombo (" + starCombo + ")";
    }

    public int progress(GameWorld w) {
        switch (type) {
            case SURVIVE:
            case NO_HIT: return (int) w.time;
            case DODGE: return w.dodges;
            case SCORE: return w.score();
            default: return w.diamonds;
        }
    }

    /** Kazanılan yıldız: 1 tamamlama + 1 hiç çarpmama + 1 kombo hedefi. */
    public int stars(RunResult r) {
        if (!r.won) return 0;
        int s = 1;
        if (r.hits == 0) s++;
        if (r.maxCombo >= starCombo) s++;
        return s;
    }

    /** Bu görev için zorluk profili üretir. */
    public DifficultyProfile profile() {
        DifficultyProfile p = DifficultyProfile.endless();
        p.timeOffset = timeOffset;
        if (type == DODGE) {
            p.shadowSpawnTimes = new float[]{2.4f, 9f, 17f, 27f, 38f};
            p.shadowDelays = new float[]{2.4f, 3.4f, 4.4f, 2.8f, 5.2f};
            p.intervalStart = 2.2f;
        } else if (type == COLLECT) {
            p.diamondIntervalMin = 1.3f;
            p.diamondIntervalMax = 2.2f;
        } else if (type == NO_HIT) {
            p.lives = 1;
        }
        if (has(MOD_FAST_SHADOWS)) {
            float[] d = p.shadowDelays.clone();
            for (int i = 0; i < d.length; i++) d[i] = Math.max(1.6f, d[i] * 0.7f);
            p.shadowDelays = d;
        }
        if (has(MOD_SWARM)) {
            float[] t = p.shadowSpawnTimes.clone();
            for (int i = 0; i < t.length; i++) t[i] = Math.max(p.shadowDelays[i % p.shadowDelays.length] + 0.2f, t[i] * 0.55f);
            p.shadowSpawnTimes = t;
        }
        if (has(MOD_RUSH)) p.scrollMul = 1.25f;
        return p;
    }
}
