package com.isikyolu.game.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

import com.isikyolu.game.Audio;
import com.isikyolu.game.Game;

/** Üç yıldız; kazanılanlar sırayla zıplayarak dolar. Küçük modda statik gösterir. */
public final class StarsView extends View {
    private int earned;
    private boolean animate;
    private long startMs;
    private int played;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path p = new Path();

    public StarsView(Context c) { super(c); }

    public void setStars(int n, boolean animated) {
        earned = n;
        animate = animated;
        startMs = SystemClock.uptimeMillis();
        played = 0;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float size = Math.min(w / 3.4f, h);
        float t = (SystemClock.uptimeMillis() - startMs) / 1000f;
        boolean more = false;
        for (int i = 0; i < 3; i++) {
            float cx = w / 2f + (i - 1) * size * 1.1f;
            float cy = h / 2f + (i == 1 ? -size * 0.08f : size * 0.04f);
            float r = size * (i == 1 ? 0.5f : 0.43f);
            Icon.star(p, cx, cy, r, r * 0.45f);
            fill.setColor(0xFF4A5C87);
            c.drawPath(p, fill);
            if (i < earned) {
                float local = animate ? (t - 0.35f - i * 0.32f) / 0.35f : 1f;
                if (local < 1f) more = true;
                if (local <= 0) continue;
                if (animate && played <= i && local > 0) {
                    played = i + 1;
                    Game.audio().play(Audio.STAR, 1f + 0.12f * i);
                    Game.haptics().tick();
                }
                float sc = local < 1f ? (float) (1.0 + 0.35 * Math.sin(Math.min(1f, local) * Math.PI)) * Math.min(1f, local * 1.5f) : 1f;
                c.save();
                c.scale(sc, sc, cx, cy);
                glow.setColor(0x40F5C542);
                c.drawCircle(cx, cy, r * 1.15f, glow);
                fill.setColor(Palette.GOLD);
                c.drawPath(p, fill);
                fill.setColor(0x66FFFFFF);
                Icon.star(p, cx - r * 0.12f, cy - r * 0.12f, r * 0.35f, r * 0.16f);
                c.drawPath(p, fill);
                c.restore();
            }
        }
        if (more || animate && t < 2.5f) postInvalidateOnAnimation();
    }
}
