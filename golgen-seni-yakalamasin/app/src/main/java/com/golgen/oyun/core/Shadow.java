package com.golgen.oyun.core;

/**
 * Oyuncunun geçmiş hareketini sabit bir gecikmeyle tekrar eden gölge.
 * Konumu her zaman kayıttan (MotionRecorder) okunur; kendi fiziği yoktur.
 */
public final class Shadow {
    public float delay;        // saniye
    public float spawnTime;    // oyun zamanı
    public float x, y;
    public float dodgeCooldown;
    public boolean wasClose;

    /** Belirme (zararsız) süresi. */
    public static final float GRACE = 1.0f;

    public void set(float delay, float spawnTime) {
        this.delay = delay;
        this.spawnTime = spawnTime;
        this.dodgeCooldown = 0f;
        this.wasClose = false;
    }

    public float age(float now) {
        return now - spawnTime;
    }

    public boolean isSolid(float now) {
        return age(now) >= GRACE;
    }

    /** 0..1 arası belirme oranı. */
    public float materialize(float now) {
        float a = age(now) / GRACE;
        return a < 0f ? 0f : (a > 1f ? 1f : a);
    }
}
