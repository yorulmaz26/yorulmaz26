package com.isikyolu.game.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.os.SystemClock;
import android.view.View;

/** Animasyonlu amblem: kaynaktan çıkan ışın iki aynadan yansıyıp hedefe ulaşır. */
public final class LogoView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path beam = new Path(), part = new Path();
    private PathMeasure pm;
    private final long start = SystemClock.uptimeMillis();

    public LogoView(Context c) {
        super(c);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 108f;
        float offX = (getWidth() - 108 * s) / 2f, offY = (getHeight() - 108 * s) / 2f;
        c.save();
        c.translate(offX, offY);
        c.scale(s, s);
        float t = (SystemClock.uptimeMillis() - start) / 1000f;
        if (pm == null) {
            beam.reset();
            beam.moveTo(24, 72); beam.lineTo(58, 72); beam.lineTo(58, 36); beam.lineTo(84, 36);
            pm = new PathMeasure(beam, false);
        }
        float len = pm.getLength();
        float prog = Math.min(1f, t / 1.2f);
        part.reset();
        pm.getSegment(0, len * prog, part, true);
        p.setStyle(Paint.Style.STROKE);
        float sh = 1f + 0.1f * (float) Math.sin(t * 4);
        p.setStrokeWidth(13 * sh); p.setColor(0x40F5C542); c.drawPath(part, p);
        p.setStrokeWidth(5.5f); p.setColor(Palette.GOLD); c.drawPath(part, p);
        p.setStrokeWidth(2f); p.setColor(0xFFFFFFFF); c.drawPath(part, p);
        // aynalar
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFE3EAF6);
        c.save(); c.rotate(-45, 58, 72); c.drawRoundRect(46, 70, 70, 74.5f, 2, 2, p); c.restore();
        c.save(); c.rotate(-45, 58, 36); c.drawRoundRect(46, 34, 70, 38.5f, 2, 2, p); c.restore();
        // kaynak
        p.setColor(Palette.PURPLE);
        c.drawRoundRect(10, 62, 34, 82, 5, 5, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(22, 72, 4.5f, p);
        // hedef
        boolean hit = prog >= 1f;
        if (hit) {
            float pul = 1f + 0.15f * (float) Math.sin(t * 5);
            p.setColor(0x4020B8A6);
            c.drawCircle(88, 36, 15 * pul, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4);
        p.setColor(Palette.TURQUOISE);
        c.drawCircle(88, 36, 9, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(hit ? 0xFFBFFFF6 : Palette.TURQUOISE);
        c.drawCircle(88, 36, 4, p);
        c.restore();
        postInvalidateOnAnimation();
    }
}
