package com.golgen.oyun.core;

/** Görev modu seviye tanımı (veri nesnesi). */
public final class MissionDef {
    public static final int SURVIVE = 0, DODGE = 1, COLLECT = 2, NO_HIT = 3;

    public final int index;
    public final String title;
    public final int type;
    public final int target;
    public final float timeOffset;
    public final int reward;

    public MissionDef(int index, String title, int type, int target, float timeOffset, int reward) {
        this.index = index;
        this.title = title;
        this.type = type;
        this.target = target;
        this.timeOffset = timeOffset;
        this.reward = reward;
    }

    public String description() {
        switch (type) {
            case SURVIVE: return target + " saniye hayatta kal";
            case DODGE: return target + " kez gölgenden kıl payı kaç";
            case COLLECT: return target + " elmas topla";
            default: return "Engellere çarpmadan " + target + " saniye dayan";
        }
    }

    public int progress(GameWorld w) {
        switch (type) {
            case SURVIVE:
            case NO_HIT: return (int) w.time;
            case DODGE: return w.dodges;
            default: return w.diamonds;
        }
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
        return p;
    }
}
