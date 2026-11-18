package com.golgen.oyun.ui;

import android.graphics.Canvas;
import android.graphics.Shader;

import com.golgen.oyun.core.Diamond;
import com.golgen.oyun.core.GameWorld;
import com.golgen.oyun.core.Obstacle;
import com.golgen.oyun.core.PowerUp;
import com.golgen.oyun.core.Shadow;
import com.golgen.oyun.core.SkinDef;

/** Oyun dünyasını (arka plan, engeller, elmaslar, gölgeler, oyuncu) çizer. */
public final class WorldRenderer {
    public float s = 1f;               // piksel / dünya birimi
    public final Particles particles = new Particles(320);
    private static final int STARS = 48;
    private final float[] starX = new float[STARS], starY = new float[STARS], starZ = new float[STARS];
    private Shader bgShader;
    private float bgH = -1;
    private float t;
    private final float[] tmp = new float[2];

    public WorldRenderer() {
        long seed = 99;
        for (int i = 0; i < STARS; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            starX[i] = ((seed >>> 33) % 1000);
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            starY[i] = ((seed >>> 33) % 2400);
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            starZ[i] = 0.15f + ((seed >>> 33) % 100) / 100f * 0.55f;
        }
    }

    public void update(float dt, float scroll) {
        t += dt;
        particles.update(dt, scroll);
    }

    public void drawBackground(Canvas c, float w, float h, float scrollDistance) {
        c.drawColor(Theme.NAVY);
        if (bgShader == null || bgH != h) {
            bgShader = Ui.vertical(0, h, 0x00000000, 0x33000000 | (Theme.PURPLE & 0xffffff));
            bgH = h;
        }
        Ui.shaderPaint.setShader(bgShader);
        c.drawRect(0, 0, w, h, Ui.shaderPaint);
        // Neon ızgara
        float worldH = h / s;
        float spacing = 125f;
        Ui.stroke.setStrokeWidth(Math.max(1f, 1.6f * s));
        Ui.stroke.setColor(Theme.alpha(Theme.PURPLE, 0.09f));
        for (float x = spacing / 2f; x < 1000f; x += spacing) c.drawLine(x * s, 0, x * s, h, Ui.stroke);
        float off = scrollDistance % spacing;
        for (float y = off - spacing; y < worldH; y += spacing) {
            float k = Math.max(0f, Math.min(1f, y / worldH));
            Ui.stroke.setColor(Theme.alpha(Theme.PURPLE, 0.04f + 0.1f * k));
            c.drawLine(0, y * s, w, y * s, Ui.stroke);
        }
        // Paralaks yıldızlar
        Ui.fill.setShader(null);
        for (int i = 0; i < STARS; i++) {
            float y = (starY[i] + scrollDistance * starZ[i]) % 2400f;
            if (y > worldH) continue;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 2f + i);
            Ui.fill.setColor(Theme.alpha(i % 3 == 0 ? Theme.TEAL : Theme.WHITE, 0.15f + 0.35f * starZ[i] * tw));
            c.drawCircle(starX[i] * s, y * s, (1.2f + 2.6f * starZ[i]) * s, Ui.fill);
        }
    }

    public void drawWorld(Canvas c, GameWorld w, SkinDef skin, boolean playerVisible) {
        drawDiamonds(c, w);
        drawPowerups(c, w);
        drawObstacles(c, w);
        drawShadows(c, w, skin);
        particles.draw(c, s);
        if (playerVisible) drawPlayer(c, w, skin);
    }

    private void drawDiamonds(Canvas c, GameWorld w) {
        for (int i = 0; i < w.diamondPool.size(); i++) {
            Diamond d = w.diamondPool.get(i);
            float bob = (float) Math.sin(d.phase * 4f) * 6f;
            float x = d.x * s, y = (d.y + bob) * s;
            Ui.glow(c, x, y, GameWorld.DIAMOND_R * 3.2f * s, Theme.GOLD, 0.55f);
            Ui.diamond(c, x, y, GameWorld.DIAMOND_R * s, 1f);
        }
    }

    public static int powerupColor(int type) {
        return type == PowerUp.SHIELD ? Theme.TEAL : (type == PowerUp.SLOW ? Theme.PURPLE_LIGHT : Theme.GOLD);
    }

    private void drawPowerups(Canvas c, GameWorld w) {
        float r = GameWorld.POWERUP_R * s;
        for (int i = 0; i < w.powerups.size(); i++) {
            PowerUp pu = w.powerups.get(i);
            float x = pu.x * s, y = (pu.y + (float) Math.sin(pu.phase * 3.5f) * 6f) * s;
            int col = powerupColor(pu.type);
            float pulse = 0.85f + 0.15f * (float) Math.sin(pu.phase * 6f);
            Ui.glow(c, x, y, r * 3.6f * pulse, col, 0.7f);
            Ui.fill.setShader(null);
            Ui.fill.setColor(Theme.alpha(Theme.NAVY_DEEP, 0.9f));
            c.drawCircle(x, y, r, Ui.fill);
            Ui.stroke.setColor(col);
            Ui.stroke.setStrokeWidth(4f * s);
            c.drawCircle(x, y, r, Ui.stroke);
            Icons.draw(c, Icons.forPowerup(pu.type), x, y, r * 0.6f, col);
        }
    }

    private void drawObstacles(Canvas c, GameWorld w) {
        for (int i = 0; i < w.obstacles.size(); i++) {
            Obstacle o = w.obstacles.get(i);
            switch (o.type) {
                case Obstacle.WALL: {
                    float top = (o.y - o.h / 2f) * s, bottom = (o.y + o.h / 2f) * s;
                    float gl = (o.gapX - o.gapW / 2f) * s, gr = (o.gapX + o.gapW / 2f) * s;
                    float r = o.h / 2f * s;
                    if (gl > 0) Ui.neonRect(c, -r * 2f, top, gl, bottom, r, Theme.PURPLE, 18f * s);
                    if (gr < GameWorld.WIDTH * s) Ui.neonRect(c, gr, top, GameWorld.WIDTH * s + r * 2f, bottom, r, Theme.PURPLE, 18f * s);
                    // Boşluk kenar işaretleri
                    Ui.glow(c, gl, (top + bottom) / 2f, 40f * s, Theme.TEAL, 0.6f);
                    Ui.glow(c, gr, (top + bottom) / 2f, 40f * s, Theme.TEAL, 0.6f);
                    break;
                }
                case Obstacle.MINE: {
                    float x = o.x * s, y = o.y * s, r = o.mineRadius() * s;
                    Ui.glow(c, x, y, r * 2.8f, Theme.PURPLE, 0.75f);
                    Ui.stroke.setColor(Theme.PURPLE_LIGHT);
                    Ui.stroke.setStrokeWidth(5f * s);
                    for (int k = 0; k < 8; k++) {
                        double a = o.phase * 1.5 + k * Math.PI / 4;
                        float cx = (float) Math.cos(a), sy = (float) Math.sin(a);
                        c.drawLine(x + cx * r * 0.9f, y + sy * r * 0.9f, x + cx * r * 1.3f, y + sy * r * 1.3f, Ui.stroke);
                    }
                    Ui.fill.setShader(null);
                    Ui.fill.setColor(Theme.PURPLE_DARK);
                    c.drawCircle(x, y, r, Ui.fill);
                    Ui.stroke.setStrokeWidth(4f * s);
                    c.drawCircle(x, y, r, Ui.stroke);
                    Ui.fill.setColor(Theme.alpha(Theme.WHITE, 0.6f + 0.4f * (float) Math.sin(o.phase * 6f)));
                    c.drawCircle(x, y, r * 0.3f, Ui.fill);
                    break;
                }
                case Obstacle.SWEEPER: {
                    float l = (o.x - o.w / 2f) * s, r = (o.x + o.w / 2f) * s;
                    float top = (o.y - o.h / 2f) * s, bottom = (o.y + o.h / 2f) * s;
                    Ui.neonRect(c, l, top, r, bottom, o.h / 2f * s, Theme.PURPLE_LIGHT, 18f * s);
                    break;
                }
                default: { // ROTOR
                    float hx = (float) Math.cos(o.angle) * o.w / 2f, hy = (float) Math.sin(o.angle) * o.w / 2f;
                    Ui.neonLine(c, (o.x - hx) * s, (o.y - hy) * s, (o.x + hx) * s, (o.y + hy) * s, o.h * s, Theme.PURPLE);
                    Ui.glow(c, o.x * s, o.y * s, 60f * s, Theme.GOLD, 0.5f);
                    Ui.fill.setShader(null);
                    Ui.fill.setColor(Theme.GOLD);
                    c.drawCircle(o.x * s, o.y * s, 16f * s, Ui.fill);
                    break;
                }
            }
        }
    }

    private void drawShadows(Canvas c, GameWorld w, SkinDef skin) {
        float pr = GameWorld.PLAYER_R * s;
        for (int i = 0; i < w.shadows.size(); i++) {
            Shadow sh = w.shadows.get(i);
            float m = sh.materialize(w.time);
            float x = sh.x * s, y = sh.y * s;
            if (m < 1f) {
                // Belirme: daralan halka uyarısı
                float ringR = pr * (1.2f + 2.8f * (1f - m));
                Ui.stroke.setColor(Theme.alpha(Theme.PURPLE_LIGHT, 0.9f * m + 0.1f));
                Ui.stroke.setStrokeWidth(4f * s);
                c.drawCircle(x, y, ringR, Ui.stroke);
            }
            float flicker = m < 1f ? (0.4f + 0.6f * (float) Math.abs(Math.sin(t * 22f))) * m : 1f;
            Ui.glow(c, x, y, pr * 3.6f, Theme.PURPLE, 0.75f * flicker);
            Ui.fill.setShader(null);
            Ui.fill.setColor(Theme.alpha(Theme.SHADOW_FILL, 0.92f * flicker));
            Ui.shape(c, skin.shape, x, y, pr * 1.12f, 0f, Ui.fill);
            Ui.stroke.setColor(Theme.alpha(Theme.PURPLE_LIGHT, flicker));
            Ui.stroke.setStrokeWidth(4.5f * s);
            Ui.shape(c, skin.shape, x, y, pr * 1.12f, 0f, Ui.stroke);
            // Gözler: oyuncuya doğru bakar
            float dx = w.px - sh.x, dy = w.py - sh.y;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > 0.001f) { dx /= len; dy /= len; }
            Ui.fill.setColor(Theme.alpha(Theme.PURPLE_LIGHT, flicker));
            float ex = dx * pr * 0.22f, ey = dy * pr * 0.22f;
            c.drawCircle(x - pr * 0.3f + ex, y - pr * 0.1f + ey, pr * 0.14f, Ui.fill);
            c.drawCircle(x + pr * 0.3f + ex, y - pr * 0.1f + ey, pr * 0.14f, Ui.fill);
        }
    }

    private void drawPlayer(Canvas c, GameWorld w, SkinDef skin) {
        float pr = GameWorld.PLAYER_R * s;
        // İz
        Ui.fill.setShader(null);
        for (int k = 8; k >= 1; k--) {
            if (!w.recorder.sample(w.time - k * 0.018f, tmp)) break;
            float a = (1f - k / 9f) * 0.35f;
            Ui.fill.setColor(Theme.alpha(skin.glowColor, a));
            c.drawCircle(tmp[0] * s, tmp[1] * s, pr * (0.9f - k * 0.07f), Ui.fill);
        }
        boolean blink = w.invuln > 0f && ((int) (w.invuln * 12f)) % 2 == 0;
        float alpha = blink ? 0.35f : 1f;
        float x = w.px * s, y = w.py * s;
        Ui.glow(c, x, y, pr * 3.4f, skin.glowColor, 0.85f * alpha);
        float rot = Math.max(-0.5f, Math.min(0.5f, w.pvx * 0.00035f));
        Ui.fill.setColor(Theme.alpha(Theme.WHITE, alpha));
        Ui.shape(c, skin.shape, x, y, pr * 1.12f, rot, Ui.fill);
        Ui.stroke.setColor(Theme.alpha(skin.glowColor, alpha));
        Ui.stroke.setStrokeWidth(3f * s);
        Ui.shape(c, skin.shape, x, y, pr * 1.12f, rot, Ui.stroke);
        if (w.shield) {
            float sr = pr * (1.9f + 0.08f * (float) Math.sin(t * 6f));
            Ui.glow(c, x, y, sr * 1.5f, Theme.TEAL, 0.35f);
            Ui.stroke.setColor(Theme.alpha(Theme.TEAL, 0.85f));
            Ui.stroke.setStrokeWidth(4f * s);
            c.drawCircle(x, y, sr, Ui.stroke);
        }
        if (w.magnetTimer > 0f) {
            Ui.stroke.setColor(Theme.alpha(Theme.GOLD, 0.25f));
            Ui.stroke.setStrokeWidth(2f * s);
            c.drawCircle(x, y, GameWorld.MAGNET_RANGE * s * (0.6f + 0.4f * ((t * 1.5f) % 1f)), Ui.stroke);
        }
    }

    /** Karanlıkta gölgelerin gözleri görünmeye devam eder (adil uyarı). */
    public void drawShadowHints(Canvas c, GameWorld w) {
        float pr = GameWorld.PLAYER_R * s;
        Ui.fill.setShader(null);
        for (int i = 0; i < w.shadows.size(); i++) {
            Shadow sh = w.shadows.get(i);
            float a = 0.5f + 0.5f * (float) Math.abs(Math.sin(t * 4f + i));
            Ui.fill.setColor(Theme.alpha(Theme.PURPLE_LIGHT, a));
            c.drawCircle(sh.x * s - pr * 0.3f, sh.y * s - pr * 0.1f, pr * 0.14f, Ui.fill);
            c.drawCircle(sh.x * s + pr * 0.3f, sh.y * s - pr * 0.1f, pr * 0.14f, Ui.fill);
        }
    }

    // ---------------- Karanlık zorlaştırıcısı ----------------
    private android.graphics.RadialGradient darkShader;
    private final android.graphics.Matrix darkMatrix = new android.graphics.Matrix();
    private float darkR = -1f;

    /** Oyuncu çevresi dışını karartır (yalnızca çizim; oyun mantığını değiştirmez). */
    public void drawDarkness(Canvas c, GameWorld w, float width, float height) {
        float r = 330f * s;
        if (darkShader == null || darkR != r) {
            darkShader = new android.graphics.RadialGradient(0f, 0f, r,
                    new int[]{0x00000000, 0x00000000, Theme.alpha(Theme.NAVY_DEEP, 0.75f), Theme.alpha(Theme.NAVY_DEEP, 0.97f)},
                    new float[]{0f, 0.45f, 0.8f, 1f}, android.graphics.Shader.TileMode.CLAMP);
            darkR = r;
        }
        darkMatrix.setTranslate(w.px * s, w.py * s);
        darkShader.setLocalMatrix(darkMatrix);
        Ui.shaderPaint.setShader(darkShader);
        c.drawRect(0, 0, width, height, Ui.shaderPaint);
        Ui.shaderPaint.setShader(null);
    }

    /** Menü ve önizlemeler için bağımsız karakter çizimi. */
    public static void drawSkinPreview(Canvas c, SkinDef skin, float x, float y, float r, float rot, float alpha, boolean asShadow) {
        if (asShadow) {
            Ui.glow(c, x, y, r * 3.4f, Theme.PURPLE, 0.7f * alpha);
            Ui.fill.setShader(null);
            Ui.fill.setColor(Theme.alpha(Theme.SHADOW_FILL, 0.95f * alpha));
            Ui.shape(c, skin.shape, x, y, r, rot, Ui.fill);
            Ui.stroke.setColor(Theme.alpha(Theme.PURPLE_LIGHT, alpha));
            Ui.stroke.setStrokeWidth(Math.max(2f, r * 0.13f));
            Ui.shape(c, skin.shape, x, y, r, rot, Ui.stroke);
            return;
        }
        Ui.glow(c, x, y, r * 3.2f, skin.glowColor, 0.85f * alpha);
        Ui.fill.setShader(null);
        Ui.fill.setColor(Theme.alpha(Theme.WHITE, alpha));
        Ui.shape(c, skin.shape, x, y, r, rot, Ui.fill);
        Ui.stroke.setColor(Theme.alpha(skin.glowColor, alpha));
        Ui.stroke.setStrokeWidth(Math.max(2f, r * 0.1f));
        Ui.shape(c, skin.shape, x, y, r, rot, Ui.stroke);
    }

}
