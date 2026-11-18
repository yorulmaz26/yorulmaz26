package com.golgen.oyun.ui;

import android.graphics.Canvas;
import android.graphics.Paint;

/** Yukarı süzülerek kaybolan kısa geri bildirim metinleri ("+25", "Kıl payı!"). Sabit kapasite, tahsis yok. */
public final class FloatingTexts {
    private static final int CAP = 12;
    private final float[] x = new float[CAP], y = new float[CAP], life = new float[CAP];
    private final String[] text = new String[CAP];
    private final int[] color = new int[CAP];
    private int count;
    private static final float LIFE = 0.9f;

    public void add(String t, float wx, float wy, int col) {
        int k = count < CAP ? count++ : 0;
        text[k] = t; x[k] = wx; y[k] = wy; life[k] = LIFE; color[k] = col;
    }

    public void clear() { count = 0; }

    public void update(float dt) {
        for (int i = count - 1; i >= 0; i--) {
            life[i] -= dt;
            y[i] -= 90f * dt;
            if (life[i] <= 0f) {
                int l = --count;
                x[i] = x[l]; y[i] = y[l]; life[i] = life[l]; text[i] = text[l]; color[i] = color[l];
            }
        }
    }

    /** s: dünya→piksel ölçeği, u: arayüz ölçeği. */
    public void draw(Canvas c, float s, float u) {
        for (int i = 0; i < count; i++) {
            float k = life[i] / LIFE;
            float a = k > 0.3f ? 1f : k / 0.3f;
            float pop = 1f + 0.25f * Math.max(0f, (k - 0.8f) / 0.2f);
            Ui.text(c, text[i], x[i] * s, y[i] * s, 40f * u * pop, Theme.alpha(color[i], a), Paint.Align.CENTER, Ui.BOLD);
        }
    }
}
