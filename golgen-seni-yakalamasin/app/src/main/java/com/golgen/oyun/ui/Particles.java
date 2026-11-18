package com.golgen.oyun.ui;

import android.graphics.Canvas;

/** Havuzlanmış basit parçacık sistemi (dizi tabanlı, tahsis yok). */
public final class Particles {
    private final int cap;
    private final float[] x, y, vx, vy, life, max, size;
    private final int[] color;
    private int count;
    private long seed = 1234567L;

    public Particles(int capacity) {
        cap = capacity;
        x = new float[cap]; y = new float[cap]; vx = new float[cap]; vy = new float[cap];
        life = new float[cap]; max = new float[cap]; size = new float[cap];
        color = new int[cap];
    }

    private float rnd() {
        seed = seed * 6364136223846793005L + 1442695040888963407L;
        return ((seed >>> 40) & 0xFFFFFF) / (float) 0x1000000;
    }

    public void burst(float px, float py, int n, int col, float speed, float sz, float lifeS) {
        for (int i = 0; i < n && count < cap; i++) {
            int k = count++;
            float a = rnd() * 6.2832f;
            float sp = speed * (0.35f + rnd() * 0.65f);
            x[k] = px; y[k] = py;
            vx[k] = (float) Math.cos(a) * sp;
            vy[k] = (float) Math.sin(a) * sp;
            max[k] = life[k] = lifeS * (0.6f + rnd() * 0.4f);
            size[k] = sz * (0.5f + rnd() * 0.5f);
            color[k] = col;
        }
    }

    public void clear() { count = 0; }

    public void update(float dt, float scroll) {
        float drag = (float) Math.pow(0.08, dt);
        for (int i = count - 1; i >= 0; i--) {
            life[i] -= dt;
            if (life[i] <= 0f) {
                int last = --count;
                x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last];
                life[i] = life[last]; max[i] = max[last]; size[i] = size[last]; color[i] = color[last];
                continue;
            }
            vx[i] *= drag;
            vy[i] *= drag;
            x[i] += vx[i] * dt;
            y[i] += (vy[i] + scroll * 0.6f) * dt;
        }
    }

    /** s: dünya→piksel ölçeği. */
    public void draw(Canvas c, float s) {
        Ui.fill.setShader(null);
        for (int i = 0; i < count; i++) {
            float k = life[i] / max[i];
            Ui.fill.setColor(Theme.alpha(color[i], k));
            c.drawCircle(x[i] * s, y[i] * s, size[i] * s * (0.4f + 0.6f * k), Ui.fill);
        }
    }
}
