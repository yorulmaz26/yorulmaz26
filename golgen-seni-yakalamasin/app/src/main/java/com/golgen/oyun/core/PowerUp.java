package com.golgen.oyun.core;

/** Toplanabilir güçlendirme: Kalkan, Yavaş Zaman, Mıknatıs. */
public final class PowerUp {
    public static final int SHIELD = 0, SLOW = 1, MAGNET = 2;
    public static final int COUNT = 3;
    private static final String[] KEYS = {"powerup.shield", "powerup.slow", "powerup.magnet"};

    /** Güçlendirmenin o anki dildeki adı (Kalkan / Yavaş Zaman / Mıknatıs). */
    public static String name(int type) {
        return L10n.t(KEYS[type]);
    }

    public int type;
    public float x, y, phase;

    public void set(int type, float x, float y) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.phase = 0f;
    }
}
