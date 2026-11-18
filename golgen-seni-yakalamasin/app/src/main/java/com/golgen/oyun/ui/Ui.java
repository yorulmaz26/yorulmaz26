package com.golgen.oyun.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.golgen.oyun.core.SkinDef;

/**
 * Tüm ekranların paylaştığı çizim yardımcıları. Boyalar ve yollar önceden oluşturulur;
 * kare başına bellek tahsisi yapılmaz. Tek iş parçacığından (UI) kullanılır.
 */
public final class Ui {
    private Ui() {}

    public static final Typeface DISPLAY = Typeface.create("sans-serif-black", Typeface.NORMAL);
    public static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    public static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    public static final Typeface NUMBERS = Typeface.create("sans-serif-condensed", Typeface.BOLD);

    public static final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    public static final Paint shaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    public static final Path path = new Path();
    public static final Path path2 = new Path();
    public static final RectF rf = new RectF();
    public static final RectF rf2 = new RectF();
    private static final Rect srcRect = new Rect();

    private static Bitmap glow;
    private static final int[] GLOW_COLORS = {Theme.WHITE, Theme.PURPLE, Theme.TEAL, Theme.GOLD, Theme.PURPLE_LIGHT};
    private static final Paint[] glowPaints = new Paint[GLOW_COLORS.length];

    static {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        // Işıma dokusu: beyaz radyal gradyan, renk filtreleriyle boyanır.
        int size = 128;
        glow = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas gc = new Canvas(glow);
        Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
        gp.setShader(new RadialGradient(size / 2f, size / 2f, size / 2f,
                new int[]{0xFFFFFFFF, 0x88FFFFFF, 0x22FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.25f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        gc.drawCircle(size / 2f, size / 2f, size / 2f, gp);
        srcRect.set(0, 0, size, size);
        for (int i = 0; i < GLOW_COLORS.length; i++) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            p.setColorFilter(new PorterDuffColorFilter(GLOW_COLORS[i], PorterDuff.Mode.SRC_IN));
            glowPaints[i] = p;
        }
    }

    private static Paint glowPaintFor(int color) {
        for (int i = 0; i < GLOW_COLORS.length; i++) if ((GLOW_COLORS[i] & 0xffffff) == (color & 0xffffff)) return glowPaints[i];
        return glowPaints[0];
    }

    /** Yumuşak neon ışıma (önceden üretilmiş doku). */
    public static void glow(Canvas c, float cx, float cy, float radius, int color, float alpha) {
        if (alpha <= 0.01f || radius <= 0f) return;
        Paint p = glowPaintFor(color);
        p.setAlpha((int) (255 * Math.min(1f, alpha)));
        rf.set(cx - radius, cy - radius, cx + radius, cy + radius);
        c.drawBitmap(glow, srcRect, rf, p);
    }

    // ---------------- Metin ----------------
    public static void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, Typeface tf) {
        text.setTypeface(tf);
        text.setTextSize(size);
        text.setColor(color);
        text.setTextAlign(align);
        text.setShader(null);
        c.drawText(s, x, y, text);
    }

    /** Metni genişliğe sığacak şekilde küçültür. */
    public static void textFit(Canvas c, String s, float x, float y, float size, float maxW, int color, Paint.Align align, Typeface tf) {
        text.setTypeface(tf);
        text.setTextSize(size);
        float w = text.measureText(s);
        if (w > maxW && w > 0) size *= maxW / w;
        text(c, s, x, y, size, color, align, tf);
    }

    public static float measure(String s, float size, Typeface tf) {
        text.setTypeface(tf);
        text.setTextSize(size);
        return text.measureText(s);
    }

    /** Dikey ortalanmış metin taban çizgisi. */
    public static float centerY(float cy, float size) {
        return cy + size * 0.35f;
    }

    /** Basit satır kaydırma (önbelleğe alınmış satırlar için bir kez çağrılır). */
    public static String[] wrap(String s, float size, float maxW, Typeface tf) {
        text.setTypeface(tf);
        text.setTextSize(size);
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String cand = line.length() == 0 ? word : line + " " + word;
            if (text.measureText(cand) > maxW && line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(cand);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines.toArray(new String[0]);
    }

    // ---------------- Paneller ----------------
    public static void panel(Canvas c, float l, float t, float r, float b, float radius, int fillColor, int strokeColor, float strokeW) {
        rf.set(l, t, r, b);
        fill.setColor(fillColor);
        fill.setShader(null);
        c.drawRoundRect(rf, radius, radius, fill);
        if (strokeW > 0) {
            stroke.setColor(strokeColor);
            stroke.setStrokeWidth(strokeW);
            c.drawRoundRect(rf, radius, radius, stroke);
        }
    }

    /** Neon çerçeveli çubuk: geniş saydam katmanlar + parlak çekirdek. */
    public static void neonRect(Canvas c, float l, float t, float r, float b, float radius, int color, float glowW) {
        rf.set(l, t, r, b);
        stroke.setColor(Theme.alpha(color, 0.10f));
        stroke.setStrokeWidth(glowW * 2.2f);
        c.drawRoundRect(rf, radius, radius, stroke);
        stroke.setColor(Theme.alpha(color, 0.22f));
        stroke.setStrokeWidth(glowW * 1.1f);
        c.drawRoundRect(rf, radius, radius, stroke);
        fill.setColor(color);
        fill.setShader(null);
        c.drawRoundRect(rf, radius, radius, fill);
        float inset = Math.min((b - t), (r - l)) * 0.3f;
        rf.set(l + inset, t + inset, r - inset, b - inset);
        if (rf.width() > 0 && rf.height() > 0) {
            fill.setColor(Theme.alpha(Theme.WHITE, 0.55f));
            c.drawRoundRect(rf, radius * 0.5f, radius * 0.5f, fill);
        }
    }

    public static void neonLine(Canvas c, float x1, float y1, float x2, float y2, float w, int color) {
        stroke.setColor(Theme.alpha(color, 0.12f));
        stroke.setStrokeWidth(w * 3.2f);
        c.drawLine(x1, y1, x2, y2, stroke);
        stroke.setColor(Theme.alpha(color, 0.3f));
        stroke.setStrokeWidth(w * 1.8f);
        c.drawLine(x1, y1, x2, y2, stroke);
        stroke.setColor(color);
        stroke.setStrokeWidth(w);
        c.drawLine(x1, y1, x2, y2, stroke);
        stroke.setColor(Theme.alpha(Theme.WHITE, 0.7f));
        stroke.setStrokeWidth(w * 0.35f);
        c.drawLine(x1, y1, x2, y2, stroke);
    }

    public static Shader vertical(float top, float bottom, int c1, int c2) {
        return new LinearGradient(0, top, 0, bottom, c1, c2, Shader.TileMode.CLAMP);
    }

    // ---------------- Şekiller ----------------
    /** Karakter şeklini merkez (cx,cy), yarıçap s ile çizer. */
    public static void shape(Canvas c, int shape, float cx, float cy, float s, float rot, Paint p) {
        path.reset();
        switch (shape) {
            case SkinDef.SQUARE: {
                float h = s * 0.82f;
                c.save();
                c.rotate((float) Math.toDegrees(rot), cx, cy);
                rf.set(cx - h, cy - h, cx + h, cy + h);
                c.drawRoundRect(rf, s * 0.22f, s * 0.22f, p);
                c.restore();
                return;
            }
            case SkinDef.TRIANGLE: polygon(cx, cy + s * 0.12f, s * 1.08f, 3, rot - (float) Math.PI / 2f); break;
            case SkinDef.RHOMBUS: {
                path.moveTo(cx, cy - s * 1.1f);
                path.lineTo(cx + s * 0.8f, cy);
                path.lineTo(cx, cy + s * 1.1f);
                path.lineTo(cx - s * 0.8f, cy);
                path.close();
                rotatePath(cx, cy, rot);
                break;
            }
            case SkinDef.HEXAGON: polygon(cx, cy, s * 0.98f, 6, rot); break;
            case SkinDef.STAR: star(cx, cy, s * 1.1f, s * 0.48f, rot); break;
            case SkinDef.RING: {
                path.addCircle(cx, cy, s * 0.95f, Path.Direction.CW);
                path.addCircle(cx, cy, s * 0.5f, Path.Direction.CCW);
                break;
            }
            case SkinDef.CROSS: {
                float a = s * 0.95f, b = s * 0.34f;
                path.moveTo(cx - b, cy - a);
                path.lineTo(cx + b, cy - a);
                path.lineTo(cx + b, cy - b);
                path.lineTo(cx + a, cy - b);
                path.lineTo(cx + a, cy + b);
                path.lineTo(cx + b, cy + b);
                path.lineTo(cx + b, cy + a);
                path.lineTo(cx - b, cy + a);
                path.lineTo(cx - b, cy + b);
                path.lineTo(cx - a, cy + b);
                path.lineTo(cx - a, cy - b);
                path.lineTo(cx - b, cy - b);
                path.close();
                rotatePath(cx, cy, rot);
                break;
            }
            default: { // CRYSTAL
                path.moveTo(cx, cy - s * 1.15f);
                path.lineTo(cx + s * 0.62f, cy - s * 0.45f);
                path.lineTo(cx + s * 0.62f, cy + s * 0.45f);
                path.lineTo(cx, cy + s * 1.15f);
                path.lineTo(cx - s * 0.62f, cy + s * 0.45f);
                path.lineTo(cx - s * 0.62f, cy - s * 0.45f);
                path.close();
                rotatePath(cx, cy, rot);
                break;
            }
        }
        c.drawPath(path, p);
    }

    private static final android.graphics.Matrix matrix = new android.graphics.Matrix();

    private static void rotatePath(float cx, float cy, float rot) {
        if (rot == 0f) return;
        matrix.setRotate((float) Math.toDegrees(rot), cx, cy);
        path.transform(matrix);
    }

    private static void polygon(float cx, float cy, float r, int n, float rot) {
        for (int i = 0; i < n; i++) {
            double a = rot + i * Math.PI * 2 / n;
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
    }

    private static void star(float cx, float cy, float ro, float ri, float rot) {
        for (int i = 0; i < 10; i++) {
            double a = rot - Math.PI / 2 + i * Math.PI / 5;
            float r = (i % 2 == 0) ? ro : ri;
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
    }

    /** Altın elmas simgesi. */
    public static void diamond(Canvas c, float cx, float cy, float s, float alpha) {
        path.reset();
        path.moveTo(cx - s * 0.9f, cy - s * 0.25f);
        path.lineTo(cx - s * 0.5f, cy - s * 0.75f);
        path.lineTo(cx + s * 0.5f, cy - s * 0.75f);
        path.lineTo(cx + s * 0.9f, cy - s * 0.25f);
        path.lineTo(cx, cy + s * 0.9f);
        path.close();
        fill.setShader(null);
        fill.setColor(Theme.alpha(Theme.GOLD, alpha));
        c.drawPath(path, fill);
        // Yüzey parıltısı
        path.reset();
        path.moveTo(cx - s * 0.9f, cy - s * 0.25f);
        path.lineTo(cx + s * 0.9f, cy - s * 0.25f);
        path.lineTo(cx + s * 0.5f, cy - s * 0.75f);
        path.lineTo(cx - s * 0.5f, cy - s * 0.75f);
        path.close();
        fill.setColor(Theme.alpha(0xFFF3DDA6, alpha));
        c.drawPath(path, fill);
        stroke.setColor(Theme.alpha(Theme.WHITE, 0.7f * alpha));
        stroke.setStrokeWidth(Math.max(1f, s * 0.08f));
        c.drawLine(cx - s * 0.2f, cy - s * 0.25f, cx, cy + s * 0.75f, stroke);
    }

    /** Elmas sayacı hapı (üst çubuk). */
    public static float diamondPill(Canvas c, float x, float y, float h, String value, boolean alignRight) {
        float ts = h * 0.46f;
        float w = h * 1.25f + measure(value, ts, NUMBERS) + h * 0.5f;
        float l = alignRight ? x - w : x;
        panel(c, l, y, l + w, y + h, h / 2f, Theme.alpha(Theme.NAVY_DEEP, 0.85f), Theme.alpha(Theme.GOLD, 0.6f), Math.max(1.5f, h * 0.04f));
        diamond(c, l + h * 0.55f, y + h / 2f, h * 0.28f, 1f);
        text(c, value, l + h * 1.05f, centerY(y + h / 2f, ts), ts, Theme.WHITE, Paint.Align.LEFT, NUMBERS);
        return w;
    }

    public static int color(int r, int g, int b) {
        return Color.rgb(r, g, b);
    }
}
