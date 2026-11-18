package com.isikyolu.game.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Küçük statik üç yıldız göstergesi. */
public final class MiniStars extends View {
    private int stars;
    private int empty = 0xFF576A94;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public MiniStars(Context c, int stars) { super(c); this.stars = stars; }

    public void setStars(int s) { if (s != stars) { stars = s; invalidate(); } }

    public void setEmptyColor(int c) { empty = c; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        float h = getHeight(), w = getWidth();
        float r = Math.min(h / 2f, w / 6.6f);
        for (int i = 0; i < 3; i++) {
            float cx = w / 2f + (i - 1) * r * 2.2f;
            Icon.star(path, cx, h / 2f + r * 0.05f, r, r * 0.45f);
            p.setColor(i < stars ? Palette.GOLD : empty);
            c.drawPath(path, p);
        }
    }
}
