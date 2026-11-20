package com.golgen.oyun.screens;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import com.golgen.oyun.GameView;
import com.golgen.oyun.core.Skins;
import com.golgen.oyun.ui.Particles;
import com.golgen.oyun.ui.Screen;
import com.golgen.oyun.ui.Theme;
import com.golgen.oyun.ui.Ui;
import com.golgen.oyun.ui.WorldRenderer;

import java.io.InputStream;

/**
 * Magic Lumos Software açılış animasyonu (oyunun mekaniğiyle):
 * karanlıkta oyuncu devre yolunda ilerler, gölgesi geçmiş hareketlerini gecikmeli tekrar ederek onu kovalar;
 * oyuncu merkeze varınca "Lumos" ışığı patlar, gölge dağılır ve logo ışık halkasıyla karanlıktan ortaya çıkar.
 */
public final class LogoScreen extends Screen {
    private static final int BG = 0xFF061729;           // logonun kenar rengi
    private static final float T_ARRIVE = 2.6f;        // oyuncunun merkeze varışı
    private static final float T_REVEAL = 1.9f;         // ışık halkasının yayılma süresi
    private static final float SHADOW_DELAY = 0.75f;    // gölge gecikmesi (oyundaki gibi geçmişi tekrar eder)
    private static final float DURATION = 7.2f;

    private Bitmap logo;
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Particles particles = new Particles(120);
    private final float[] px = new float[5], py = new float[5], segLen = new float[4];
    private float pathLen;
    private final float[] tmp = new float[2];
    private boolean burst, left;
    private android.graphics.Shader fadeTop, fadeBottom, fadeLeft, fadeRight;
    private final Paint fadePaint = new Paint();

    public LogoScreen(GameView g) {
        super(g);
        try (InputStream in = g.activity.getAssets().open("magic_lumos_logo.jpg")) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            logo = BitmapFactory.decodeStream(in, null, o);
            if (logo != null) src.set(0, 0, logo.getWidth(), logo.getHeight());
        } catch (Exception e) {
            Log.w("LogoScreen", "Logo yüklenemedi", e);
            logo = null;
        }
    }

    @Override
    protected void layout() {
        // Dikey ekranda yazı büyük görünsün: logo genişliğin %130'u (kenarlar arka planla aynı renk)
        float k = logo != null ? 1.3f * w / logo.getWidth() : 1f;
        float lw = logo != null ? logo.getWidth() * k : w, lh = logo != null ? logo.getHeight() * k : w * 0.56f;
        float cy = h * 0.44f;
        dst.set(w / 2f - lw / 2f, cy - lh / 2f, w / 2f + lw / 2f, cy + lh / 2f);
        // Görsel kenarlarını arka plana yumuşakça karıştıran geçişler (bir kez oluşturulur)
        float f = 90f * u;
        int clear = BG & 0x00FFFFFF;
        fadeTop = new android.graphics.LinearGradient(0, dst.top, 0, dst.top + f, BG, clear, android.graphics.Shader.TileMode.CLAMP);
        fadeBottom = new android.graphics.LinearGradient(0, dst.bottom - f, 0, dst.bottom, clear, BG, android.graphics.Shader.TileMode.CLAMP);
        fadeLeft = new android.graphics.LinearGradient(dst.left, 0, dst.left + f, 0, BG, clear, android.graphics.Shader.TileMode.CLAMP);
        fadeRight = new android.graphics.LinearGradient(dst.right - f, 0, dst.right, 0, clear, BG, android.graphics.Shader.TileMode.CLAMP);
        // Devre yolu (90° dönüşlü): ekranın altından SOFTWARE yazısının hemen altına (yazının üstünden geçmez)
        float cx = w / 2f, endY = dst.top + lh * 0.70f;
        px[0] = cx - 300f * u; py[0] = h + 60f * u;
        px[1] = cx - 300f * u; py[1] = h * 0.80f;
        px[2] = cx + 180f * u; py[2] = h * 0.80f;
        px[3] = cx + 180f * u; py[3] = dst.bottom + 40f * u;
        px[4] = cx;            py[4] = endY;
        pathLen = 0f;
        for (int i = 0; i < 4; i++) {
            float dx = px[i + 1] - px[i], dy = py[i + 1] - py[i];
            segLen[i] = (float) Math.sqrt(dx * dx + dy * dy);
            pathLen += segLen[i];
        }
    }

    @Override
    public void enter() {
        super.enter();
        burst = false;
        left = false;
        particles.clear();
        if (logo == null) leave();
    }

    private void leave() {
        if (left) return;
        left = true;
        g.go(g.splash);
    }

    /** Yol üzerinde t (0..1) konumu. */
    private void pointAt(float t, float[] out) {
        float d = Math.max(0f, Math.min(1f, t)) * pathLen;
        for (int i = 0; i < 4; i++) {
            if (d <= segLen[i] || i == 3) {
                float k = segLen[i] <= 0f ? 0f : Math.min(1f, d / segLen[i]);
                out[0] = px[i] + (px[i + 1] - px[i]) * k;
                out[1] = py[i] + (py[i + 1] - py[i]) * k;
                return;
            }
            d -= segLen[i];
        }
    }

    private static float easeInOut(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    private float progressAt(float time) {
        return easeInOut(time / T_ARRIVE);
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        particles.update(dt, 0f);
        if (!burst && time >= T_ARRIVE) {
            burst = true;
            // Gölge, ışık patlamasıyla dağılır
            pointAt(progressAt(time - SHADOW_DELAY), tmp);
            particles.burst(tmp[0], tmp[1], 46, Theme.PURPLE_LIGHT, 900f * u, 9f * u, 1.0f);
            particles.burst(px[4], py[4], 60, 0xFFFFF1B8, 1300f * u, 7f * u, 0.9f);
            g.sound.play(com.golgen.oyun.core.Synth.SUCCESS, 0.8f, 1.0f);
            g.haptics.light();
        }
        if (time > DURATION) leave();
    }

    @Override
    protected void onTap(float x, float y) {
        if (time > 0.5f) leave();
    }

    @Override
    public boolean onBack() {
        leave();
        return true;
    }

    @Override
    public void draw(Canvas c) {
        c.drawColor(BG);
        if (logo == null) return;
        com.golgen.oyun.core.SkinDef skin = Skins.ALL[0];
        float r = 30f * u;

        // 1) Işık halkasıyla açığa çıkan logo
        float rt = (time - T_ARRIVE) / T_REVEAL;
        if (rt > 0f) {
            float ease = 1f - (1f - Math.min(1f, rt)) * (1f - Math.min(1f, rt)) * (1f - Math.min(1f, rt));
            float maxR = (float) Math.hypot(Math.max(px[4], w - px[4]), Math.max(py[4] - dst.top, dst.bottom - py[4])) + 40f * u;
            float revealR = maxR * ease;
            c.save();
            Ui.path.reset();
            Ui.path.addCircle(px[4], py[4], Math.max(1f, revealR), android.graphics.Path.Direction.CW);
            c.clipPath(Ui.path);
            c.drawBitmap(logo, src, dst, bmp);
            float f = 90f * u;
            fadePaint.setShader(fadeTop);
            c.drawRect(dst.left, dst.top, dst.right, dst.top + f, fadePaint);
            fadePaint.setShader(fadeBottom);
            c.drawRect(dst.left, dst.bottom - f, dst.right, dst.bottom, fadePaint);
            fadePaint.setShader(fadeLeft);
            c.drawRect(dst.left, dst.top, dst.left + f, dst.bottom, fadePaint);
            fadePaint.setShader(fadeRight);
            c.drawRect(dst.right - f, dst.top, dst.right, dst.bottom, fadePaint);
            c.restore();
            if (rt < 1f) {
                // Işık cephesi
                Ui.stroke.setColor(Theme.alpha(Theme.TEAL, 0.8f * (1f - rt)));
                Ui.stroke.setStrokeWidth(6f * u);
                c.drawCircle(px[4], py[4], revealR, Ui.stroke);
                Ui.stroke.setColor(Theme.alpha(Theme.GOLD, 0.35f * (1f - rt)));
                Ui.stroke.setStrokeWidth(18f * u);
                c.drawCircle(px[4], py[4], revealR, Ui.stroke);
            }
        }

        // 2) Yazı üzerinde süzülen ışık
        float st = (time - (T_ARRIVE + T_REVEAL * 0.7f)) / 1.6f;
        if (st > 0f && st < 1f) {
            float sx = dst.left + dst.width() * (0.15f + 0.75f * st);
            float a = (float) Math.sin(Math.PI * st);
            for (int k = -1; k <= 1; k++) {
                Ui.glow(c, sx + k * 40f * u, dst.top + dst.height() * (0.5f + k * 0.05f), 150f * u, Theme.WHITE, 0.32f * a);
            }
        }

        // 3) Devre izi (oyuncunun bıraktığı neon iz)
        float pNow = progressAt(time);
        float fadeTrail = rt > 0f ? Math.max(0f, 1f - rt * 2.2f) : 1f;
        if (fadeTrail > 0f && pNow > 0f) {
            float d = pNow * pathLen;
            Ui.stroke.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 4 && d > 0f; i++) {
                float k = Math.min(1f, d / segLen[i]);
                float ex = px[i] + (px[i + 1] - px[i]) * k, ey = py[i] + (py[i + 1] - py[i]) * k;
                Ui.neonLine(c, px[i], py[i], ex, ey, 5f * u, Theme.alpha(Theme.TEAL, fadeTrail));
                Ui.fill.setShader(null);
                Ui.fill.setColor(Theme.alpha(Theme.TEAL, fadeTrail));
                if (k >= 1f) c.drawCircle(px[i + 1], py[i + 1], 9f * u, Ui.fill); // devre düğümü
                d -= segLen[i];
            }
        }

        // 4) Gölge: oyuncunun geçmiş hareketini gecikmeli tekrar eder; ışık patlamasında dağılır
        if (!burst) {
            float sp = progressAt(time - SHADOW_DELAY);
            if (time > SHADOW_DELAY * 0.6f) {
                pointAt(sp, tmp);
                float appear = Math.min(1f, (time - SHADOW_DELAY * 0.6f) / 0.5f);
                WorldRenderer.drawSkinPreview(c, skin, tmp[0], tmp[1], r, 0f, appear, true);
            }
        }

        // 5) Oyuncu: merkeze varınca ışığa dönüşür
        pointAt(pNow, tmp);
        float pa = rt > 0f ? Math.max(0f, 1f - rt * 1.6f) : Math.min(1f, time / 0.45f);
        if (pa > 0f) {
            float grow = rt > 0f ? 1f + rt * 1.5f : 1f;
            WorldRenderer.drawSkinPreview(c, skin, tmp[0], tmp[1], r * grow, 0f, pa, false);
        }

        // 6) Patlama ışığı ve parçacıklar
        if (rt > 0f && rt < 0.6f) {
            float fa = 1f - rt / 0.6f;
            Ui.glow(c, px[4], py[4], 520f * u * (0.6f + rt), Theme.WHITE, 0.9f * fa);
            c.drawColor(Theme.alpha(Theme.WHITE, 0.25f * fa * fa));
        }
        particles.draw(c, 1f);

        if (time > 0.6f && time < T_ARRIVE) {
            Ui.text(c, "Dokunarak geç", w / 2f, h - safeB - 70f * u, 28f * u, Theme.alpha(Theme.MUTED, 0.6f), Paint.Align.CENTER, Ui.MEDIUM);
        }
    }
}
