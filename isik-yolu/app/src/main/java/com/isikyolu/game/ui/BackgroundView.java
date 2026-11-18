package com.isikyolu.game.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/** Hafif hareketli gece arka planı: yavaş süzülen ışık bulutları ve göz kırpan parçacıklar. */
public final class BackgroundView extends View {
    private static final int MOTES = 42;
    private final Paint bg = new Paint();
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mx = new float[MOTES], my = new float[MOTES], ms = new float[MOTES], mp = new float[MOTES], mv = new float[MOTES];
    private final int[] mc = new int[MOTES];
    private RadialGradient[] glows;
    private final Matrix m = new Matrix();
    private final long start = SystemClock.uptimeMillis();

    public BackgroundView(Context c) {
        super(c);
        Random r = new Random(7);
        int[] cols = {Palette.GOLD, Palette.TURQUOISE, Palette.PURPLE, Palette.WHITE};
        for (int i = 0; i < MOTES; i++) {
            mx[i] = r.nextFloat(); my[i] = r.nextFloat(); ms[i] = 0.6f + r.nextFloat() * 1.6f;
            mp[i] = r.nextFloat() * 6.28f; mv[i] = 0.004f + r.nextFloat() * 0.01f; mc[i] = cols[r.nextInt(cols.length)];
        }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        bg.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF141E33, Palette.NIGHT, Palette.NIGHT_DEEP}, null, Shader.TileMode.CLAMP));
        float r = Math.max(w, h) * 0.55f;
        glows = new RadialGradient[]{
                new RadialGradient(0, 0, r, 0x336D5DFB, 0x006D5DFB, Shader.TileMode.CLAMP),
                new RadialGradient(0, 0, r * 0.8f, 0x2620B8A6, 0x0020B8A6, Shader.TileMode.CLAMP),
                new RadialGradient(0, 0, r * 0.6f, 0x1CF5C542, 0x00F5C542, Shader.TileMode.CLAMP)};
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float t = (SystemClock.uptimeMillis() - start) / 1000f;
        c.drawRect(0, 0, w, h, bg);
        if (glows != null) {
            float[][] pos = {
                    {0.2f + 0.08f * (float) Math.sin(t * 0.11f), 0.18f + 0.05f * (float) Math.cos(t * 0.09f)},
                    {0.85f + 0.06f * (float) Math.cos(t * 0.08f), 0.62f + 0.07f * (float) Math.sin(t * 0.1f)},
                    {0.4f + 0.1f * (float) Math.sin(t * 0.07f + 2), 0.95f}};
            for (int i = 0; i < glows.length; i++) {
                m.setTranslate(pos[i][0] * w, pos[i][1] * h);
                glows[i].setLocalMatrix(m);
                glow.setShader(glows[i]);
                c.drawRect(0, 0, w, h, glow);
            }
        }
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < MOTES; i++) {
            float y = (my[i] - t * mv[i]) % 1f;
            if (y < 0) y += 1f;
            float x = mx[i] + 0.01f * (float) Math.sin(t * 0.5f + mp[i]);
            float a = 0.25f + 0.35f * (0.5f + 0.5f * (float) Math.sin(t * 1.3f + mp[i]));
            dot.setColor(Palette.withAlpha(mc[i], (int) (a * 255)));
            c.drawCircle(x * w, y * h, ms[i] * d, dot);
        }
        postInvalidateDelayed(33);
    }
}
