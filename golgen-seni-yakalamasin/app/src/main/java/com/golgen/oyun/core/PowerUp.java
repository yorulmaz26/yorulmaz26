package com.golgen.oyun.core;

/** Toplanabilir güçlendirme: Kalkan, Yavaş Zaman, Mıknatıs. */
public final class PowerUp {
    public static final int SHIELD = 0, SLOW = 1, MAGNET = 2;
    public static final int COUNT = 3;
    public static final String[] NAMES = {"Kalkan", "Yavaş Zaman", "Mıknatıs"};

    public int type;
    public float x, y, phase;

    public void set(int type, float x, float y) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.phase = 0f;
    }
}
