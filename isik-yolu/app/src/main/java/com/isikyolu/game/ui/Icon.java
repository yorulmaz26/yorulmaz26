package com.isikyolu.game.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Kodla çizilen vektör ikonlar (tüm ekran yoğunluklarında keskin). */
public final class Icon extends Drawable {
    public static final int BACK = 0, PAUSE = 1, UNDO = 2, RESET = 3, BULB = 4, STAR = 5, LOCK = 6, GEAR = 7,
            TROPHY = 8, CALENDAR = 9, SOUND = 10, MUSIC = 11, PLAY = 12, GRID = 13, HELP = 14, VIBRATE = 15,
            CHECK = 16, FLAME = 17, NEXT = 18, HOME = 19, STAR_OUTLINE = 20, CLOSE = 21, HEART = 22;

    private final int kind;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    public Icon(int kind, int color) {
        this.kind = kind;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(color);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
    }

    public void setColor(int c) { stroke.setColor(c); fill.setColor(c); invalidateSelf(); }

    @Override public void draw(Canvas c) {
        float l = getBounds().left, t = getBounds().top;
        float s = Math.min(getBounds().width(), getBounds().height());
        c.save();
        c.translate(l + (getBounds().width() - s) / 2f, t + (getBounds().height() - s) / 2f);
        draw(c, kind, s, stroke, fill, path, rect);
        c.restore();
    }

    /** s boyutlu karede ikon çizer (0..s koordinatları). */
    public static void draw(Canvas c, int kind, float s, Paint st, Paint fl, Path p, RectF r) {
        st.setStrokeWidth(s * 0.09f);
        p.reset();
        float m = s / 2f;
        switch (kind) {
            case BACK:
                p.moveTo(s * 0.62f, s * 0.2f); p.lineTo(s * 0.32f, m); p.lineTo(s * 0.62f, s * 0.8f);
                c.drawPath(p, st);
                break;
            case NEXT:
                p.moveTo(s * 0.38f, s * 0.2f); p.lineTo(s * 0.68f, m); p.lineTo(s * 0.38f, s * 0.8f);
                c.drawPath(p, st);
                break;
            case PAUSE:
                r.set(s * 0.28f, s * 0.22f, s * 0.42f, s * 0.78f); c.drawRoundRect(r, s * 0.04f, s * 0.04f, fl);
                r.set(s * 0.58f, s * 0.22f, s * 0.72f, s * 0.78f); c.drawRoundRect(r, s * 0.04f, s * 0.04f, fl);
                break;
            case PLAY:
                p.moveTo(s * 0.32f, s * 0.2f); p.lineTo(s * 0.8f, m); p.lineTo(s * 0.32f, s * 0.8f); p.close();
                c.drawPath(p, fl);
                break;
            case UNDO:
                r.set(s * 0.22f, s * 0.28f, s * 0.8f, s * 0.82f);
                c.drawArc(r, 200, 250, false, st);
                p.moveTo(s * 0.12f, s * 0.3f); p.lineTo(s * 0.26f, s * 0.48f); p.lineTo(s * 0.42f, s * 0.32f);
                c.drawPath(p, st);
                break;
            case RESET:
                r.set(s * 0.2f, s * 0.2f, s * 0.8f, s * 0.8f);
                c.drawArc(r, -60, 300, false, st);
                p.moveTo(s * 0.62f, s * 0.12f); p.lineTo(s * 0.76f, s * 0.26f); p.lineTo(s * 0.58f, s * 0.34f);
                c.drawPath(p, st);
                break;
            case BULB:
                c.drawCircle(m, s * 0.4f, s * 0.24f, st);
                c.drawLine(s * 0.4f, s * 0.72f, s * 0.6f, s * 0.72f, st);
                c.drawLine(s * 0.43f, s * 0.86f, s * 0.57f, s * 0.86f, st);
                c.drawLine(m, s * 0.4f, m, s * 0.58f, st);
                break;
            case STAR:
            case STAR_OUTLINE:
                star(p, m, m * 1.04f, s * 0.46f, s * 0.2f);
                c.drawPath(p, kind == STAR ? fl : st);
                break;
            case LOCK:
                r.set(s * 0.24f, s * 0.44f, s * 0.76f, s * 0.86f); c.drawRoundRect(r, s * 0.08f, s * 0.08f, fl);
                r.set(s * 0.33f, s * 0.14f, s * 0.67f, s * 0.6f); c.drawArc(r, 180, 180, false, st);
                c.drawLine(s * 0.33f, s * 0.37f, s * 0.33f, s * 0.46f, st);
                c.drawLine(s * 0.67f, s * 0.37f, s * 0.67f, s * 0.46f, st);
                break;
            case GEAR:
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    c.drawLine(m + (float) Math.cos(a) * s * 0.26f, m + (float) Math.sin(a) * s * 0.26f,
                            m + (float) Math.cos(a) * s * 0.4f, m + (float) Math.sin(a) * s * 0.4f, st);
                }
                c.drawCircle(m, m, s * 0.25f, st);
                c.drawCircle(m, m, s * 0.08f, fl);
                break;
            case TROPHY:
                p.moveTo(s * 0.28f, s * 0.16f); p.lineTo(s * 0.72f, s * 0.16f); p.lineTo(s * 0.68f, s * 0.46f);
                p.quadTo(m, s * 0.66f, s * 0.32f, s * 0.46f); p.close();
                c.drawPath(p, fl);
                r.set(s * 0.12f, s * 0.2f, s * 0.36f, s * 0.44f); c.drawArc(r, 90, 180, false, st);
                r.set(s * 0.64f, s * 0.2f, s * 0.88f, s * 0.44f); c.drawArc(r, -90, 180, false, st);
                c.drawLine(m, s * 0.6f, m, s * 0.76f, st);
                c.drawLine(s * 0.32f, s * 0.84f, s * 0.68f, s * 0.84f, st);
                break;
            case CALENDAR:
                r.set(s * 0.16f, s * 0.22f, s * 0.84f, s * 0.84f); c.drawRoundRect(r, s * 0.08f, s * 0.08f, st);
                c.drawLine(s * 0.16f, s * 0.4f, s * 0.84f, s * 0.4f, st);
                c.drawLine(s * 0.34f, s * 0.12f, s * 0.34f, s * 0.28f, st);
                c.drawLine(s * 0.66f, s * 0.12f, s * 0.66f, s * 0.28f, st);
                c.drawCircle(s * 0.62f, s * 0.64f, s * 0.07f, fl);
                break;
            case SOUND:
                p.moveTo(s * 0.14f, s * 0.38f); p.lineTo(s * 0.3f, s * 0.38f); p.lineTo(s * 0.5f, s * 0.2f);
                p.lineTo(s * 0.5f, s * 0.8f); p.lineTo(s * 0.3f, s * 0.62f); p.lineTo(s * 0.14f, s * 0.62f); p.close();
                c.drawPath(p, fl);
                r.set(s * 0.42f, s * 0.3f, s * 0.72f, s * 0.7f); c.drawArc(r, -50, 100, false, st);
                r.set(s * 0.42f, s * 0.16f, s * 0.88f, s * 0.84f); c.drawArc(r, -50, 100, false, st);
                break;
            case MUSIC:
                c.drawCircle(s * 0.32f, s * 0.72f, s * 0.12f, fl);
                c.drawCircle(s * 0.72f, s * 0.64f, s * 0.12f, fl);
                c.drawLine(s * 0.42f, s * 0.72f, s * 0.42f, s * 0.22f, st);
                c.drawLine(s * 0.82f, s * 0.64f, s * 0.82f, s * 0.14f, st);
                c.drawLine(s * 0.42f, s * 0.22f, s * 0.82f, s * 0.14f, st);
                break;
            case VIBRATE:
                r.set(s * 0.34f, s * 0.16f, s * 0.66f, s * 0.84f); c.drawRoundRect(r, s * 0.06f, s * 0.06f, st);
                c.drawLine(s * 0.16f, s * 0.36f, s * 0.16f, s * 0.64f, st);
                c.drawLine(s * 0.84f, s * 0.36f, s * 0.84f, s * 0.64f, st);
                break;
            case GRID:
                for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) {
                    r.set(s * (0.16f + 0.38f * i), s * (0.16f + 0.38f * j), s * (0.46f + 0.38f * i), s * (0.46f + 0.38f * j));
                    c.drawRoundRect(r, s * 0.06f, s * 0.06f, fl);
                }
                break;
            case HELP:
                c.drawCircle(m, m, s * 0.38f, st);
                r.set(s * 0.37f, s * 0.26f, s * 0.63f, s * 0.52f); c.drawArc(r, 180, 250, false, st);
                c.drawLine(m, s * 0.52f, m, s * 0.58f, st);
                c.drawCircle(m, s * 0.72f, s * 0.05f, fl);
                break;
            case CHECK:
                p.moveTo(s * 0.2f, s * 0.52f); p.lineTo(s * 0.42f, s * 0.74f); p.lineTo(s * 0.8f, s * 0.28f);
                c.drawPath(p, st);
                break;
            case HEART:
                p.moveTo(m, s * 0.86f);
                p.cubicTo(s * 0.1f, s * 0.5f, s * 0.08f, s * 0.12f, s * 0.32f, s * 0.14f);
                p.cubicTo(s * 0.42f, s * 0.15f, s * 0.48f, s * 0.22f, m, s * 0.3f);
                p.cubicTo(s * 0.52f, s * 0.22f, s * 0.58f, s * 0.15f, s * 0.68f, s * 0.14f);
                p.cubicTo(s * 0.92f, s * 0.12f, s * 0.9f, s * 0.5f, m, s * 0.86f);
                p.close();
                c.drawPath(p, fl);
                break;
            case CLOSE:
                c.drawLine(s * 0.25f, s * 0.25f, s * 0.75f, s * 0.75f, st);
                c.drawLine(s * 0.75f, s * 0.25f, s * 0.25f, s * 0.75f, st);
                break;
            case FLAME:
                p.moveTo(m, s * 0.1f);
                p.cubicTo(s * 0.86f, s * 0.42f, s * 0.84f, s * 0.9f, m, s * 0.9f);
                p.cubicTo(s * 0.16f, s * 0.9f, s * 0.14f, s * 0.5f, s * 0.38f, s * 0.36f);
                p.cubicTo(s * 0.4f, s * 0.5f, s * 0.46f, s * 0.56f, s * 0.52f, s * 0.58f);
                p.cubicTo(s * 0.5f, s * 0.4f, s * 0.46f, s * 0.24f, m, s * 0.1f);
                c.drawPath(p, fl);
                break;
            case HOME:
                p.moveTo(s * 0.14f, s * 0.48f); p.lineTo(m, s * 0.16f); p.lineTo(s * 0.86f, s * 0.48f);
                c.drawPath(p, st);
                p.reset();
                p.moveTo(s * 0.26f, s * 0.42f); p.lineTo(s * 0.26f, s * 0.84f); p.lineTo(s * 0.74f, s * 0.84f); p.lineTo(s * 0.74f, s * 0.42f);
                c.drawPath(p, st);
                break;
            default:
                break;
        }
    }

    public static void star(Path p, float cx, float cy, float outer, float inner) {
        p.reset();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + Math.PI * i / 5;
            float rr = (i % 2 == 0) ? outer : inner;
            float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
    }

    @Override public void setAlpha(int alpha) { stroke.setAlpha(alpha); fill.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { stroke.setColorFilter(cf); fill.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
