package com.golgen.oyun.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Vektör ikonlar (Path ile çizilir, harici görsel gerekmez). */
public final class Icons {
    private Icons() {}

    public static final int NONE = -1, PLAY = 0, PAUSE = 1, GEAR = 2, BACK = 3, TROPHY = 4, TARGET = 5, PERSON = 6,
            SOUND = 7, SOUND_OFF = 8, MUSIC = 9, VIBRATE = 10, HEART = 11, LOCK = 12, CHECK = 13, STAR = 14,
            RESTART = 15, HOME = 16, BOOK = 17, TRASH = 18, CLOSE = 19, NEXT = 20, CALENDAR = 21;

    private static final Path p = new Path();
    private static final RectF r = new RectF();

    /** cx,cy merkezli, s boyutunda (yaklaşık kutu yarı genişliği) ikon. */
    public static void draw(Canvas c, int icon, float cx, float cy, float s, int color) {
        Paint st = Ui.stroke, fi = Ui.fill;
        st.setColor(color);
        st.setStrokeWidth(Math.max(2f, s * 0.17f));
        fi.setColor(color);
        fi.setShader(null);
        p.reset();
        switch (icon) {
            case PLAY:
                p.moveTo(cx - s * 0.45f, cy - s * 0.7f);
                p.lineTo(cx + s * 0.7f, cy);
                p.lineTo(cx - s * 0.45f, cy + s * 0.7f);
                p.close();
                c.drawPath(p, fi);
                break;
            case PAUSE:
                r.set(cx - s * 0.55f, cy - s * 0.65f, cx - s * 0.15f, cy + s * 0.65f);
                c.drawRoundRect(r, s * 0.1f, s * 0.1f, fi);
                r.set(cx + s * 0.15f, cy - s * 0.65f, cx + s * 0.55f, cy + s * 0.65f);
                c.drawRoundRect(r, s * 0.1f, s * 0.1f, fi);
                break;
            case GEAR: {
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    float x1 = cx + (float) Math.cos(a) * s * 0.55f, y1 = cy + (float) Math.sin(a) * s * 0.55f;
                    float x2 = cx + (float) Math.cos(a) * s * 0.85f, y2 = cy + (float) Math.sin(a) * s * 0.85f;
                    st.setStrokeWidth(s * 0.26f);
                    c.drawLine(x1, y1, x2, y2, st);
                }
                st.setStrokeWidth(s * 0.2f);
                c.drawCircle(cx, cy, s * 0.5f, st);
                c.drawCircle(cx, cy, s * 0.12f, fi);
                break;
            }
            case BACK:
                p.moveTo(cx + s * 0.25f, cy - s * 0.6f);
                p.lineTo(cx - s * 0.35f, cy);
                p.lineTo(cx + s * 0.25f, cy + s * 0.6f);
                c.drawPath(p, st);
                break;
            case NEXT:
                p.moveTo(cx - s * 0.25f, cy - s * 0.6f);
                p.lineTo(cx + s * 0.35f, cy);
                p.lineTo(cx - s * 0.25f, cy + s * 0.6f);
                c.drawPath(p, st);
                break;
            case TROPHY: {
                p.moveTo(cx - s * 0.55f, cy - s * 0.7f);
                p.lineTo(cx + s * 0.55f, cy - s * 0.7f);
                p.lineTo(cx + s * 0.45f, cy - s * 0.05f);
                p.quadTo(cx, cy + s * 0.35f, cx - s * 0.45f, cy - s * 0.05f);
                p.close();
                c.drawPath(p, fi);
                st.setStrokeWidth(s * 0.13f);
                r.set(cx - s * 0.85f, cy - s * 0.6f, cx - s * 0.35f, cy - s * 0.05f);
                c.drawArc(r, 90, 180, false, st);
                r.set(cx + s * 0.35f, cy - s * 0.6f, cx + s * 0.85f, cy - s * 0.05f);
                c.drawArc(r, -90, 180, false, st);
                c.drawRect(cx - s * 0.1f, cy + s * 0.15f, cx + s * 0.1f, cy + s * 0.5f, fi);
                r.set(cx - s * 0.45f, cy + s * 0.5f, cx + s * 0.45f, cy + s * 0.72f);
                c.drawRoundRect(r, s * 0.08f, s * 0.08f, fi);
                break;
            }
            case TARGET:
                st.setStrokeWidth(s * 0.15f);
                c.drawCircle(cx, cy, s * 0.75f, st);
                c.drawCircle(cx, cy, s * 0.42f, st);
                c.drawCircle(cx, cy, s * 0.14f, fi);
                break;
            case PERSON:
                c.drawCircle(cx, cy - s * 0.38f, s * 0.3f, fi);
                r.set(cx - s * 0.65f, cy + s * 0.05f, cx + s * 0.65f, cy + s * 1.1f);
                c.drawArc(r, 180, 180, true, fi);
                break;
            case SOUND:
            case SOUND_OFF: {
                p.moveTo(cx - s * 0.75f, cy - s * 0.25f);
                p.lineTo(cx - s * 0.4f, cy - s * 0.25f);
                p.lineTo(cx, cy - s * 0.65f);
                p.lineTo(cx, cy + s * 0.65f);
                p.lineTo(cx - s * 0.4f, cy + s * 0.25f);
                p.lineTo(cx - s * 0.75f, cy + s * 0.25f);
                p.close();
                c.drawPath(p, fi);
                st.setStrokeWidth(s * 0.13f);
                if (icon == SOUND) {
                    r.set(cx - s * 0.2f, cy - s * 0.4f, cx + s * 0.4f, cy + s * 0.4f);
                    c.drawArc(r, -50, 100, false, st);
                    r.set(cx - s * 0.3f, cy - s * 0.72f, cx + s * 0.72f, cy + s * 0.72f);
                    c.drawArc(r, -50, 100, false, st);
                } else {
                    c.drawLine(cx + s * 0.25f, cy - s * 0.3f, cx + s * 0.8f, cy + s * 0.3f, st);
                    c.drawLine(cx + s * 0.8f, cy - s * 0.3f, cx + s * 0.25f, cy + s * 0.3f, st);
                }
                break;
            }
            case MUSIC:
                c.drawCircle(cx - s * 0.4f, cy + s * 0.45f, s * 0.25f, fi);
                c.drawCircle(cx + s * 0.45f, cy + s * 0.3f, s * 0.25f, fi);
                st.setStrokeWidth(s * 0.13f);
                c.drawLine(cx - s * 0.17f, cy + s * 0.45f, cx - s * 0.17f, cy - s * 0.55f, st);
                c.drawLine(cx + s * 0.68f, cy + s * 0.3f, cx + s * 0.68f, cy - s * 0.7f, st);
                st.setStrokeWidth(s * 0.22f);
                c.drawLine(cx - s * 0.17f, cy - s * 0.55f, cx + s * 0.68f, cy - s * 0.7f, st);
                break;
            case VIBRATE:
                st.setStrokeWidth(s * 0.13f);
                r.set(cx - s * 0.3f, cy - s * 0.65f, cx + s * 0.3f, cy + s * 0.65f);
                c.drawRoundRect(r, s * 0.12f, s * 0.12f, st);
                c.drawLine(cx - s * 0.55f, cy - s * 0.35f, cx - s * 0.55f, cy + s * 0.35f, st);
                c.drawLine(cx + s * 0.55f, cy - s * 0.35f, cx + s * 0.55f, cy + s * 0.35f, st);
                c.drawLine(cx - s * 0.8f, cy - s * 0.2f, cx - s * 0.8f, cy + s * 0.2f, st);
                c.drawLine(cx + s * 0.8f, cy - s * 0.2f, cx + s * 0.8f, cy + s * 0.2f, st);
                break;
            case HEART:
                p.moveTo(cx, cy + s * 0.75f);
                p.cubicTo(cx - s * 1.1f, cy - s * 0.05f, cx - s * 0.55f, cy - s * 0.95f, cx, cy - s * 0.35f);
                p.cubicTo(cx + s * 0.55f, cy - s * 0.95f, cx + s * 1.1f, cy - s * 0.05f, cx, cy + s * 0.75f);
                p.close();
                c.drawPath(p, fi);
                break;
            case LOCK:
                r.set(cx - s * 0.55f, cy - s * 0.1f, cx + s * 0.55f, cy + s * 0.7f);
                c.drawRoundRect(r, s * 0.12f, s * 0.12f, fi);
                st.setStrokeWidth(s * 0.16f);
                r.set(cx - s * 0.35f, cy - s * 0.7f, cx + s * 0.35f, cy + s * 0.1f);
                c.drawArc(r, 180, 180, false, st);
                c.drawLine(cx - s * 0.35f, cy - s * 0.3f, cx - s * 0.35f, cy - s * 0.1f, st);
                c.drawLine(cx + s * 0.35f, cy - s * 0.3f, cx + s * 0.35f, cy - s * 0.1f, st);
                break;
            case CHECK:
                st.setStrokeWidth(s * 0.22f);
                p.moveTo(cx - s * 0.6f, cy);
                p.lineTo(cx - s * 0.15f, cy + s * 0.45f);
                p.lineTo(cx + s * 0.65f, cy - s * 0.45f);
                c.drawPath(p, st);
                break;
            case STAR:
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + i * Math.PI / 5;
                    float rr = (i % 2 == 0) ? s * 0.85f : s * 0.36f;
                    float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
                    if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
                }
                p.close();
                c.drawPath(p, fi);
                break;
            case RESTART:
                st.setStrokeWidth(s * 0.17f);
                r.set(cx - s * 0.6f, cy - s * 0.6f, cx + s * 0.6f, cy + s * 0.6f);
                c.drawArc(r, -60, 290, false, st);
                p.moveTo(cx + s * 0.35f, cy - s * 0.85f);
                p.lineTo(cx + s * 0.62f, cy - s * 0.42f);
                p.lineTo(cx + s * 0.12f, cy - s * 0.35f);
                p.close();
                c.drawPath(p, fi);
                break;
            case HOME:
                p.moveTo(cx, cy - s * 0.75f);
                p.lineTo(cx + s * 0.8f, cy);
                p.lineTo(cx + s * 0.55f, cy);
                p.lineTo(cx + s * 0.55f, cy + s * 0.7f);
                p.lineTo(cx + s * 0.15f, cy + s * 0.7f);
                p.lineTo(cx + s * 0.15f, cy + s * 0.25f);
                p.lineTo(cx - s * 0.15f, cy + s * 0.25f);
                p.lineTo(cx - s * 0.15f, cy + s * 0.7f);
                p.lineTo(cx - s * 0.55f, cy + s * 0.7f);
                p.lineTo(cx - s * 0.55f, cy);
                p.lineTo(cx - s * 0.8f, cy);
                p.close();
                c.drawPath(p, fi);
                break;
            case BOOK:
                st.setStrokeWidth(s * 0.13f);
                r.set(cx - s * 0.75f, cy - s * 0.6f, cx - s * 0.05f, cy + s * 0.6f);
                c.drawRoundRect(r, s * 0.08f, s * 0.08f, st);
                r.set(cx + s * 0.05f, cy - s * 0.6f, cx + s * 0.75f, cy + s * 0.6f);
                c.drawRoundRect(r, s * 0.08f, s * 0.08f, st);
                break;
            case TRASH:
                st.setStrokeWidth(s * 0.13f);
                c.drawLine(cx - s * 0.7f, cy - s * 0.5f, cx + s * 0.7f, cy - s * 0.5f, st);
                c.drawLine(cx - s * 0.2f, cy - s * 0.72f, cx + s * 0.2f, cy - s * 0.72f, st);
                p.moveTo(cx - s * 0.5f, cy - s * 0.4f);
                p.lineTo(cx - s * 0.4f, cy + s * 0.75f);
                p.lineTo(cx + s * 0.4f, cy + s * 0.75f);
                p.lineTo(cx + s * 0.5f, cy - s * 0.4f);
                c.drawPath(p, st);
                break;
            case CLOSE:
                st.setStrokeWidth(s * 0.18f);
                c.drawLine(cx - s * 0.5f, cy - s * 0.5f, cx + s * 0.5f, cy + s * 0.5f, st);
                c.drawLine(cx + s * 0.5f, cy - s * 0.5f, cx - s * 0.5f, cy + s * 0.5f, st);
                break;
            case CALENDAR:
                st.setStrokeWidth(s * 0.13f);
                r.set(cx - s * 0.7f, cy - s * 0.55f, cx + s * 0.7f, cy + s * 0.7f);
                c.drawRoundRect(r, s * 0.12f, s * 0.12f, st);
                c.drawLine(cx - s * 0.7f, cy - s * 0.15f, cx + s * 0.7f, cy - s * 0.15f, st);
                c.drawLine(cx - s * 0.35f, cy - s * 0.8f, cx - s * 0.35f, cy - s * 0.45f, st);
                c.drawLine(cx + s * 0.35f, cy - s * 0.8f, cx + s * 0.35f, cy - s * 0.45f, st);
                break;
            default:
                break;
        }
    }
}
