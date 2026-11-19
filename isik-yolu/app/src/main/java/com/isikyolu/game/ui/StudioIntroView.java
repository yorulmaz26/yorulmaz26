package com.isikyolu.game.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import com.isikyolu.game.R;

import java.util.Random;

/**
 * Magic Lumos Software stüdyo açılışı (≈5,8 sn): karanlıkta bir ışık noktası yanar, büyüyerek logoyu
 * daire biçiminde aydınlatır, kıvılcımlar saçılır, yazının üzerinden ışık süzülür, sonra sahne kararır.
 */
public final class StudioIntroView extends View {
    public static final int BG = 0xFF06182D;
    /** Animasyon yavaşlatma katsayısı: tüm aşamalar bu oranda uzar. */
    public static final float SLOW = 1.8f;
    public static final long DURATION_MS = (long) (3200 * SLOW);

    private final Bitmap logo;
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private final Matrix m = new Matrix();
    private LinearGradient shineShader;
    private long start = -1;
    private Runnable onLight;
    private boolean lightFired;

    private static final int SPARKS = 46;
    private final float[] sa = new float[SPARKS], ss = new float[SPARKS], sl = new float[SPARKS], sz = new float[SPARKS];
    private final int[] sc = new int[SPARKS];

    public StudioIntroView(Context c) {
        super(c);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        logo = BitmapFactory.decodeResource(c.getResources(), R.drawable.studio_logo, o);
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        shine.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        Random r = new Random(5);
        int[] cols = {0xFFBFE6FF, 0xFFF5D58A, 0xFFFFFFFF, 0xFF7FC4FF};
        for (int i = 0; i < SPARKS; i++) {
            sa[i] = (float) (r.nextFloat() * Math.PI * 2);
            ss[i] = 0.25f + r.nextFloat() * 0.75f;
            sl[i] = 0.6f + r.nextFloat() * 0.9f;
            sz[i] = 1.2f + r.nextFloat() * 2.4f;
            sc[i] = cols[r.nextInt(cols.length)];
        }
        setContentDescription("Magic Lumos Software");
    }

    /** Işık logoya vurduğu an (ses için). */
    public void setOnLight(Runnable r) { onLight = r; }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static float easeOut(float t) { t = clamp(t); return 1f - (1f - t) * (1f - t) * (1f - t); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float lw = w * 0.96f;
        float lh = logo == null ? 0 : lw * logo.getHeight() / logo.getWidth();
        float cy = h * 0.46f;
        dst.set((w - lw) / 2f, cy - lh / 2f, (w + lw) / 2f, cy + lh / 2f);
        float bw = lw * 0.18f;
        shineShader = new LinearGradient(-bw, 0, bw, 0, new int[]{0x00FFFFFF, 0x55FFF4D6, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        shine.setShader(shineShader);
    }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        if (start < 0) start = now;
        float t = (now - start) / 1000f / SLOW; // aşama zamanları 3,2 sn'lik ölçekte yazılıdır
        int w = getWidth(), h = getHeight();
        c.drawColor(BG);
        float cx = dst.centerX(), cy = dst.centerY();
        float d = getResources().getDisplayMetrics().density;

        // 1) ışık noktası yanar (0–0.5 sn)
        float spark = easeOut(t / 0.45f) * (1f - clamp((t - 0.9f) / 0.6f));
        if (spark > 0.01f) {
            float rr = d * (6 + 70 * clamp(t / 0.6f));
            glow.setShader(new RadialGradient(cx, cy, rr, new int[]{0xFFFFFFFF, 0x88BFE6FF, 0x00BFE6FF}, new float[]{0f, 0.25f, 1f}, Shader.TileMode.CLAMP));
            glow.setAlpha((int) (255 * spark));
            c.drawCircle(cx, cy, rr, glow);
        }
        if (!lightFired && t >= 0.35f) { lightFired = true; if (onLight != null) onLight.run(); }

        // 2) daire biçiminde açılan logo (0.35–1.5 sn), hafif yakınlaşma
        float rev = easeOut((t - 0.35f) / 1.15f);
        if (rev > 0 && logo != null) {
            float maxR = (float) Math.hypot(dst.width() / 2f, dst.height() / 2f) * 1.1f;
            float radius = Math.max(1f, maxR * rev);
            int layer = c.saveLayer(0, 0, w, h, null);
            float sc = 1.06f - 0.06f * easeOut((t - 0.35f) / 2.2f);
            c.save();
            c.scale(sc, sc, cx, cy);
            c.drawBitmap(logo, null, dst, bmp);
            // ışıltı geçişi (1.3–2.2 sn)
            float sw = clamp((t - 1.3f) / 0.9f);
            if (sw > 0 && sw < 1) {
                m.setTranslate(dst.left - dst.width() * 0.2f + dst.width() * 1.4f * sw, 0);
                m.preSkew(-0.35f, 0);
                shineShader.setLocalMatrix(m);
                c.drawRect(dst, shine);
            }
            c.restore();
            mask.setShader(new RadialGradient(cx, cy, radius, new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.8f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, mask);
            c.restoreToCount(layer);
        }

        // 3) kıvılcımlar (0.35–1.9 sn)
        float st = t - 0.35f;
        if (st > 0 && st < 1.6f) {
            for (int i = 0; i < SPARKS; i++) {
                float life = clamp(st / sl[i]);
                if (life >= 1f) continue;
                float dist = easeOut(life) * w * 0.55f * ss[i];
                float x = cx + (float) Math.cos(sa[i]) * dist, y = cy + (float) Math.sin(sa[i]) * dist * 0.6f;
                fill.setColor(sc[i]);
                fill.setAlpha((int) (230 * (1f - life)));
                c.drawCircle(x, y, sz[i] * d * (1f - 0.5f * life), fill);
            }
        }

        // 4) kenar karartması ve kapanış (2.6–3.2 sn)
        float out = clamp((t - 2.6f) / 0.6f);
        if (out > 0) {
            fill.setColor(BG);
            fill.setAlpha((int) (255 * out));
            c.drawRect(0, 0, w, h, fill);
        }
        if (t * 1000 * SLOW < DURATION_MS + 100) postInvalidateOnAnimation();
    }
}
