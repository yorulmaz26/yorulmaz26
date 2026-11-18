package com.golgen.oyun.core;

/** Bir oyunun sonucu. */
public final class RunResult {
    public static final int MODE_ENDLESS = 0, MODE_MISSION = 1;

    public int mode;
    public int missionIndex;
    public int score;
    public float time;
    public int diamonds;
    public int dodges;
    public int hits;
    public boolean won;
    public int cause;
    public int maxCombo;
    public int powerups;
    public int stars;          // görev modu: 0-3
}
