package com.golgen.oyun.core;

/** Toplanabilir elmas. */
public final class Diamond {
    public float x, y, phase;

    public void set(float x, float y) {
        this.x = x;
        this.y = y;
        this.phase = 0f;
    }
}
