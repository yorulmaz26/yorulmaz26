package com.golgen.oyun.ui;

import android.graphics.Canvas;
import android.graphics.Shader;

import com.golgen.oyun.core.Diamond;
import com.golgen.oyun.core.GameWorld;
import com.golgen.oyun.core.MapTheme;
import com.golgen.oyun.core.Maps;
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
    private MapTheme theme = Maps.ENDLESS;
    private Shader themeShader;
    private float themeH = -1;
    private int obstacleColor = Theme.PURPLE, obstacleLight = Theme.PURPLE_LIGHT, accentColor = Theme.TEAL;

    /** Harita teması (renkler, arka plan efekti). */
    public void setTheme(MapTheme m) {
        theme = m == null ? Maps.ENDLESS : m;
        themeShader = null;
        obstacleColor = theme.obstacle;
        obstacleLight = Theme.lerp(theme.obstacle, Theme.WHITE, 0.35f);
        accentColor = theme.accent;
    }

    public MapTheme theme() { return theme; }

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
        if (themeShader == null || themeH != h) {
            themeShader = Ui.vertical(0, h, theme.bgTop, theme.bgBottom);
            themeH = h;
        }
        Ui.shaderPaint.setShader(themeShader);
        c.drawRect(0, 0, w, h, Ui.shaderPaint);
        Ui.shaderPaint.setShader(null);
        // Neon ızgara (harita rengi)
        float worldH = h / s;
        float spacing = 125f;
        Ui.stroke.setStrokeWidth(Math.max(1f, 1.6f * s));
        Ui.stroke.setColor(Theme.alpha(theme.grid, 0.08f));
        for (float x = spacing / 2f; x < 1000f; x += spacing) c.drawLine(x * s, 0, x * s, h, Ui.stroke);
        float off = scrollDistance % spacing;
        for (float y = off - spacing; y < worldH; y += spacing) {
            float k = Math.max(0f, Math.min(1f, y / worldH));
            Ui.stroke.setColor(Theme.alpha(theme.grid, 0.03f + 0.09f * k));
            c.drawLine(0, y * s, w, y * s, Ui.stroke);
        }
        drawDecorations(c, worldH, scrollDistance);
    }

    /** Haritaya özgü arka plan efekti (dekor; oyunu etkilemez). */
    private void drawDecorations(Canvas c, float worldH, float scrollDistance) {
        Ui.fill.setShader(null);
        int acc = theme.accent;
        for (int i = 0; i < STARS; i++) {
            float z = starZ[i];
            float x = starX[i], y;
            switch (theme.deco) {
                case MapTheme.DECO_SNOW: {
                    y = (starY[i] + t * (60f + 90f * z) + scrollDistance * z * 0.5f) % 2400f;
                    x = (x + (float) Math.sin(t * 0.8f + i) * 30f + 1000f) % 1000f;
                    Ui.fill.setColor(Theme.alpha(Theme.WHITE, 0.25f + 0.45f * z));
                    c.drawCircle(x * s, y * s, (2f + 4f * z) * s, Ui.fill);
                    break;
                }
                case MapTheme.DECO_RAIN: {
                    y = (starY[i] + t * (900f + 600f * z)) % 2400f;
                    Ui.stroke.setColor(Theme.alpha(theme.grid, 0.18f + 0.3f * z));
                    Ui.stroke.setStrokeWidth((1.5f + 2f * z) * s);
                    c.drawLine(x * s, y * s, (x - 12f) * s, (y + 46f * (0.6f + z)) * s, Ui.stroke);
                    break;
                }
                case MapTheme.DECO_EMBERS: {
                    y = 2400f - ((starY[i] + t * (70f + 120f * z)) % 2400f);
                    x = (x + (float) Math.sin(t * 1.3f + i * 0.7f) * 24f + 1000f) % 1000f;
                    float fl = 0.5f + 0.5f * (float) Math.sin(t * 9f + i);
                    Ui.fill.setColor(Theme.alpha(i % 2 == 0 ? acc : theme.obstacle, (0.25f + 0.5f * z) * (0.5f + 0.5f * fl)));
                    c.drawCircle(x * s, y * s, (1.8f + 3.5f * z) * s, Ui.fill);
                    break;
                }
                case MapTheme.DECO_BUBBLES: {
                    y = 2400f - ((starY[i] + t * (40f + 80f * z)) % 2400f);
                    x = (x + (float) Math.sin(t * 0.9f + i) * 18f + 1000f) % 1000f;
                    Ui.stroke.setColor(Theme.alpha(acc, 0.15f + 0.35f * z));
                    Ui.stroke.setStrokeWidth(2f * s);
                    c.drawCircle(x * s, y * s, (4f + 12f * z) * s, Ui.stroke);
                    break;
                }
                case MapTheme.DECO_LEAVES: {
                    y = (starY[i] + t * (50f + 70f * z) + scrollDistance * 0.3f * z) % 2400f;
                    x = (x + (float) Math.sin(t * 0.7f + i) * 60f + t * 25f + 2000f) % 1000f;
                    c.save();
                    c.rotate((t * 60f + i * 37f) % 360f, x * s, y * s);
                    Ui.fill.setColor(Theme.alpha(i % 3 == 0 ? acc : theme.obstacle, 0.25f + 0.4f * z));
                    Ui.rf.set((x - 9f - 6f * z) * s, (y - 4f) * s, (x + 9f + 6f * z) * s, (y + 4f) * s);
                    c.drawOval(Ui.rf, Ui.fill);
                    c.restore();
                    break;
                }
                case MapTheme.DECO_DUST: {
                    y = (starY[i] + scrollDistance * z * 0.6f) % 2400f;
                    x = (x + t * (120f + 260f * z)) % 1000f;
                    Ui.stroke.setColor(Theme.alpha(theme.grid, 0.08f + 0.2f * z));
                    Ui.stroke.setStrokeWidth((1.5f + 2f * z) * s);
                    c.drawLine(x * s, y * s, (x + 40f + 60f * z) * s, y * s, Ui.stroke);
                    break;
                }
                case MapTheme.DECO_SPARKS: {
                    y = (starY[i] + scrollDistance * z) % 2400f;
                    float tw = Math.max(0f, (float) Math.sin(t * 3f + i * 1.7f));
                    float r = (3f + 7f * z) * tw;
                    if (r < 0.5f) break;
                    Ui.stroke.setColor(Theme.alpha(i % 2 == 0 ? acc : theme.grid, 0.25f + 0.5f * z));
                    Ui.stroke.setStrokeWidth(2f * s);
                    c.drawLine((x - r) * s, y * s, (x + r) * s, y * s, Ui.stroke);
                    c.drawLine(x * s, (y - r) * s, x * s, (y + r) * s, Ui.stroke);
                    break;
                }
                case MapTheme.DECO_CODE: {
                    float col = (i % 16) * 62.5f + 20f;
                    y = (starY[i] + t * (200f + 300f * z)) % 2400f;
                    Ui.fill.setColor(Theme.alpha(theme.grid, 0.12f + 0.25f * z));
                    for (int k = 0; k < 4; k++) {
                        float yy = y - k * 26f;
                        Ui.rf.set((col - 5f) * s, (yy - 9f) * s, (col + 5f) * s, (yy + 9f) * s);
                        Ui.fill.setAlpha((int) (255 * (0.12f + 0.25f * z) * (1f - k * 0.22f)));
                        c.drawRect(Ui.rf, Ui.fill);
                    }
                    break;
                }
                default: { // DECO_STARS
                    y = (starY[i] + scrollDistance * z) % 2400f;
                    float tw = 0.5f + 0.5f * (float) Math.sin(t * 2f + i);
                    Ui.fill.setColor(Theme.alpha(i % 3 == 0 ? acc : Theme.WHITE, 0.15f + 0.35f * z * tw));
                    c.drawCircle(x * s, y * s, (1.2f + 2.6f * z) * s, Ui.fill);
                    break;
                }
            }
        }
    }

    public void drawWorld(Canvas c, GameWorld w, SkinDef skin, boolean playerVisible) {
        if (w.profile().narrow > 0f) drawNarrow(c, w);
        drawDiamonds(c, w);
        drawStars(c, w);
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

    private void drawNarrow(Canvas c, GameWorld w) {
        float n = w.profile().narrow * s, hpx = w.height * s;
        Ui.fill.setShader(null);
        Ui.fill.setColor(Theme.alpha(obstacleColor, 0.12f));
        c.drawRect(0, 0, n, hpx, Ui.fill);
        c.drawRect(GameWorld.WIDTH * s - n, 0, GameWorld.WIDTH * s, hpx, Ui.fill);
        Ui.neonLine(c, n, 0, n, hpx, 6f * s, obstacleColor);
        Ui.neonLine(c, GameWorld.WIDTH * s - n, 0, GameWorld.WIDTH * s - n, hpx, 6f * s, obstacleColor);
    }

    private void drawStars(Canvas c, GameWorld w) {
        float r = GameWorld.STAR_R * s;
        for (int i = 0; i < w.starPool.size(); i++) {
            Diamond st = w.starPool.get(i);
            float x = st.x * s, y = (st.y + (float) Math.sin(st.phase * 4f + i) * 5f) * s;
            float pulse = 0.85f + 0.15f * (float) Math.sin(st.phase * 6f + i);
            Ui.glow(c, x, y, r * 3.4f * pulse, Theme.GOLD, 0.6f);
            c.save();
            c.rotate((st.phase * 40f) % 360f, x, y);
            Icons.draw(c, Icons.STAR, x, y, r * 1.05f, 0xFFFFF1B8);
            c.restore();
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
                    if (gl > 0) Ui.neonRect(c, -r * 2f, top, gl, bottom, r, obstacleColor, 18f * s);
                    if (gr < GameWorld.WIDTH * s) Ui.neonRect(c, gr, top, GameWorld.WIDTH * s + r * 2f, bottom, r, obstacleColor, 18f * s);
                    // Boşluk kenar işaretleri
                    Ui.glow(c, gl, (top + bottom) / 2f, 40f * s, accentColor, 0.6f);
                    Ui.glow(c, gr, (top + bottom) / 2f, 40f * s, accentColor, 0.6f);
                    break;
                }
                case Obstacle.MINE: {
                    float x = o.x * s, y = o.y * s, r = o.mineRadius() * s;
                    Ui.glow(c, x, y, r * 2.8f, obstacleColor, 0.75f);
                    Ui.stroke.setColor(obstacleLight);
                    Ui.stroke.setStrokeWidth(5f * s);
                    for (int k = 0; k < 8; k++) {
                        double a = o.phase * 1.5 + k * Math.PI / 4;
                        float cx = (float) Math.cos(a), sy = (float) Math.sin(a);
                        c.drawLine(x + cx * r * 0.9f, y + sy * r * 0.9f, x + cx * r * 1.3f, y + sy * r * 1.3f, Ui.stroke);
                    }
                    Ui.fill.setShader(null);
                    Ui.fill.setColor(Theme.lerp(obstacleColor, Theme.NAVY_DEEP, 0.55f));
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
                    Ui.neonRect(c, l, top, r, bottom, o.h / 2f * s, obstacleLight, 18f * s);
                    break;
                }
                case Obstacle.GATE: {
                    float l = (o.x - o.w / 2f) * s, r = (o.x + o.w / 2f) * s, y = o.y * s;
                    // uç direkler
                    Ui.fill.setShader(null);
                    Ui.fill.setColor(accentColor);
                    c.drawCircle(l, y, 14f * s, Ui.fill);
                    c.drawCircle(r, y, 14f * s, Ui.fill);
                    if (o.gateActive()) {
                        Ui.neonLine(c, l, y, r, y, o.h * s, obstacleColor);
                    } else if (o.gateWarning()) {
                        float bl = (float) Math.abs(Math.sin(o.phase * 18f));
                        Ui.stroke.setColor(Theme.alpha(obstacleLight, 0.25f + 0.6f * bl));
                        Ui.stroke.setStrokeWidth(4f * s);
                        for (float xx = l; xx < r; xx += 36f * s) c.drawLine(xx, y, Math.min(r, xx + 18f * s), y, Ui.stroke);
                    } else {
                        Ui.stroke.setColor(Theme.alpha(obstacleLight, 0.15f));
                        Ui.stroke.setStrokeWidth(2f * s);
                        c.drawLine(l, y, r, y, Ui.stroke);
                    }
                    break;
                }
                case Obstacle.BOUNCER: {
                    float x = o.x * s, y = o.y * s, r = o.r * s;
                    Ui.glow(c, x, y, r * 2.6f, obstacleColor, 0.7f);
                    Ui.fill.setShader(null);
                    Ui.fill.setColor(Theme.lerp(obstacleColor, Theme.NAVY_DEEP, 0.4f));
                    c.drawCircle(x, y, r, Ui.fill);
                    Ui.stroke.setColor(obstacleLight);
                    Ui.stroke.setStrokeWidth(5f * s);
                    c.drawCircle(x, y, r, Ui.stroke);
                    float dir = o.vx > 0 ? 1f : -1f;
                    Ui.fill.setColor(Theme.alpha(Theme.WHITE, 0.85f));
                    c.drawCircle(x + dir * r * 0.35f, y, r * 0.22f, Ui.fill);
                    break;
                }
                case Obstacle.METEOR: {
                    float x = o.x * s, y = o.y * s, r = o.r * s;
                    if (o.y < 0f) {
                        // Uyarı: ekranın üstünde yanıp sönen işaret (karanlıkta drawShadowHints yeniden çizer)
                        drawMeteorWarning(c, o);
                    } else {
                        // Alev izi
                        for (int k = 4; k >= 1; k--) {
                            Ui.fill.setColor(Theme.alpha(accentColor, 0.12f * (5 - k)));
                            c.drawCircle(x, y - k * r * 0.9f, r * (1f - k * 0.15f), Ui.fill);
                        }
                        Ui.glow(c, x, y, r * 2.8f, obstacleColor, 0.8f);
                        Ui.fill.setColor(Theme.lerp(obstacleColor, Theme.NAVY_DEEP, 0.3f));
                        c.drawCircle(x, y, r, Ui.fill);
                        Ui.stroke.setColor(obstacleLight);
                        Ui.stroke.setStrokeWidth(3f * s);
                        c.drawCircle(x, y, r, Ui.stroke);
                    }
                    break;
                }
                case Obstacle.ORBITER: {
                    float x = o.x * s, y = o.y * s, R = Obstacle.ORBIT_R * s, br = Obstacle.ORBIT_BALL * s;
                    Ui.stroke.setColor(Theme.alpha(obstacleColor, 0.18f));
                    Ui.stroke.setStrokeWidth(2f * s);
                    c.drawCircle(x, y, R, Ui.stroke);
                    float cx = (float) Math.cos(o.angle) * R, cy = (float) Math.sin(o.angle) * R;
                    Ui.stroke.setColor(Theme.alpha(obstacleLight, 0.35f));
                    Ui.stroke.setStrokeWidth(4f * s);
                    c.drawLine(x - cx, y - cy, x + cx, y + cy, Ui.stroke);
                    Ui.fill.setShader(null);
                    Ui.fill.setColor(accentColor);
                    c.drawCircle(x, y, 12f * s, Ui.fill);
                    for (int k = -1; k <= 1; k += 2) {
                        float bx = x + cx * k, by = y + cy * k;
                        Ui.glow(c, bx, by, br * 2.6f, obstacleColor, 0.75f);
                        Ui.fill.setColor(Theme.lerp(obstacleColor, Theme.NAVY_DEEP, 0.35f));
                        c.drawCircle(bx, by, br, Ui.fill);
                        Ui.stroke.setColor(obstacleLight);
                        Ui.stroke.setStrokeWidth(4f * s);
                        c.drawCircle(bx, by, br, Ui.stroke);
                    }
                    break;
                }
                case Obstacle.ZIGZAG: {
                    float top = (o.y - o.h / 2f) * s, bottom = (o.y + o.h / 2f) * s;
                    float gl = (o.gapX - o.gapW / 2f) * s, gr = (o.gapX + o.gapW / 2f) * s;
                    float r = o.h / 2f * s;
                    if (gl > 0) Ui.neonRect(c, -r * 2f, top, gl, bottom, r, obstacleLight, 18f * s);
                    if (gr < GameWorld.WIDTH * s) Ui.neonRect(c, gr, top, GameWorld.WIDTH * s + r * 2f, bottom, r, obstacleLight, 18f * s);
                    // Kayma yönü okları
                    float dir = (float) Math.cos(o.phase * o.speed) >= 0 ? 1f : -1f;
                    float mx = (gl + gr) / 2f, my = (top + bottom) / 2f;
                    Ui.stroke.setColor(Theme.alpha(accentColor, 0.8f));
                    Ui.stroke.setStrokeWidth(4f * s);
                    for (int k = 0; k < 2; k++) {
                        float ax = mx + dir * (k * 22f - 10f) * s;
                        c.drawLine(ax, my - 12f * s, ax + dir * 12f * s, my, Ui.stroke);
                        c.drawLine(ax + dir * 12f * s, my, ax, my + 12f * s, Ui.stroke);
                    }
                    break;
                }
                default: { // ROTOR
                    float hx = (float) Math.cos(o.angle) * o.w / 2f, hy = (float) Math.sin(o.angle) * o.w / 2f;
                    Ui.neonLine(c, (o.x - hx) * s, (o.y - hy) * s, (o.x + hx) * s, (o.y + hy) * s, o.h * s, obstacleColor);
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

    /**
     * Karanlığın ÜSTÜNE çizilen adil uyarılar: gölgelerin gözleri, duvar/zikzak boşluklarının
     * kenar işaretleri, süpürücünün çerçevesi, kapının uç direkleri ile durumu, meteor uyarı üçgenleri
     * ve nokta engellerin soluk çerçeveleri. Karanlık yıldızları, elmasları ve engellerin ayrıntısını
     * gizler; ama tehlikenin yerini ve nereden geçileceğini asla gizlemez.
     */
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
        for (int i = 0; i < w.obstacles.size(); i++) {
            Obstacle o = w.obstacles.get(i);
            switch (o.type) {
                case Obstacle.WALL:
                case Obstacle.ZIGZAG: {
                    float y = o.y * s, hh = o.h / 2f * s;
                    float gl = (o.gapX - o.gapW / 2f) * s, gr = (o.gapX + o.gapW / 2f) * s;
                    Ui.fill.setColor(Theme.alpha(accentColor, 0.85f));
                    c.drawRect(gl - 6f * s, y - hh, gl, y + hh, Ui.fill);
                    c.drawRect(gr, y - hh, gr + 6f * s, y + hh, Ui.fill);
                    Ui.stroke.setColor(Theme.alpha(obstacleColor, 0.35f));
                    Ui.stroke.setStrokeWidth(3f * s);
                    c.drawLine(gl - 90f * s, y, gl - 8f * s, y, Ui.stroke);
                    c.drawLine(gr + 8f * s, y, gr + 90f * s, y, Ui.stroke);
                    break;
                }
                case Obstacle.GATE: {
                    float l = (o.x - o.w / 2f) * s, r = (o.x + o.w / 2f) * s, y = o.y * s;
                    Ui.fill.setColor(Theme.alpha(accentColor, 0.85f));
                    c.drawCircle(l, y, 10f * s, Ui.fill);
                    c.drawCircle(r, y, 10f * s, Ui.fill);
                    float a = o.gateActive() ? 0.55f : (o.gateWarning() ? 0.2f + 0.35f * (float) Math.abs(Math.sin(o.phase * 18f)) : 0.1f);
                    Ui.stroke.setColor(Theme.alpha(obstacleLight, a));
                    Ui.stroke.setStrokeWidth(3f * s);
                    c.drawLine(l, y, r, y, Ui.stroke);
                    break;
                }
                case Obstacle.SWEEPER: {
                    float l = (o.x - o.w / 2f) * s, r = (o.x + o.w / 2f) * s, y = o.y * s, hh = o.h / 2f * s;
                    Ui.stroke.setColor(Theme.alpha(obstacleLight, 0.45f));
                    Ui.stroke.setStrokeWidth(3f * s);
                    c.drawRect(l, y - hh, r, y + hh, Ui.stroke);
                    break;
                }
                case Obstacle.METEOR:
                    if (o.y < 0f) drawMeteorWarning(c, o);
                    else silhouette(c, o.x, o.y, o.r);
                    break;
                case Obstacle.MINE:
                    silhouette(c, o.x, o.y, o.mineRadius());
                    break;
                case Obstacle.BOUNCER:
                    silhouette(c, o.x, o.y, o.r);
                    break;
                case Obstacle.ORBITER: {
                    float cx = (float) Math.cos(o.angle) * Obstacle.ORBIT_R, cy = (float) Math.sin(o.angle) * Obstacle.ORBIT_R;
                    silhouette(c, o.x + cx, o.y + cy, Obstacle.ORBIT_BALL);
                    silhouette(c, o.x - cx, o.y - cy, Obstacle.ORBIT_BALL);
                    break;
                }
                case Obstacle.ROTOR: {
                    float hx = (float) Math.cos(o.angle) * o.w / 2f, hy = (float) Math.sin(o.angle) * o.w / 2f;
                    Ui.stroke.setColor(Theme.alpha(obstacleLight, 0.4f));
                    Ui.stroke.setStrokeWidth(Math.max(3f, o.h * 0.35f) * s);
                    c.drawLine((o.x - hx) * s, (o.y - hy) * s, (o.x + hx) * s, (o.y + hy) * s, Ui.stroke);
                    break;
                }
                default:
                    break;
            }
        }
    }

    /** Karanlıkta nokta engellerin soluk çerçevesi: yeri ve boyu seçilir, ayrıntısı gizli kalır. */
    private void silhouette(Canvas c, float x, float y, float r) {
        Ui.stroke.setColor(Theme.alpha(obstacleLight, 0.4f));
        Ui.stroke.setStrokeWidth(3f * s);
        c.drawCircle(x * s, y * s, r * s, Ui.stroke);
    }

    private void drawMeteorWarning(Canvas c, Obstacle o) {
        float x = o.x * s;
        float bl = 0.4f + 0.6f * (float) Math.abs(Math.sin(t * 10f));
        Ui.path.reset();
        float ty = 40f * s;
        Ui.path.moveTo(x, ty + 30f * s);
        Ui.path.lineTo(x - 22f * s, ty);
        Ui.path.lineTo(x + 22f * s, ty);
        Ui.path.close();
        Ui.fill.setShader(null);
        Ui.fill.setColor(Theme.alpha(accentColor, bl));
        c.drawPath(Ui.path, Ui.fill);
        Ui.stroke.setColor(Theme.alpha(accentColor, 0.15f * bl));
        Ui.stroke.setStrokeWidth(8f * s);
        c.drawLine(x, ty + 40f * s, x, ty + 400f * s, Ui.stroke);
    }

    // ---------------- Karanlık özelliği ----------------
    // Gradyan gölgelendirici yerine önceden üretilmiş "delik" dokusu + 4 dikdörtgen:
    // her cihaz ve yazılımsal GPU'da aynı, ucuz çizim yolu (ışıma dokusuyla aynı yöntem).
    private static android.graphics.Bitmap darkHole;
    private static final android.graphics.Paint darkPaint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
    private static final android.graphics.Rect darkSrc = new android.graphics.Rect();
    private static final int DARK_COLOR = 0xF70A101C; // NAVY_DEEP, %97

    private static void ensureDarkHole() {
        if (darkHole != null) return;
        int n = 256;
        darkHole = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888);
        int[] px = new int[n * n];
        float c = (n - 1) / 2f;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float d = (float) Math.sqrt((x - c) * (x - c) + (y - c) * (y - c)) / c; // 0 merkez, 1 kenar
                float a;
                if (d <= 0.45f) a = 0f;
                else if (d >= 1f) a = 0.97f;
                else {
                    float k = (d - 0.45f) / 0.55f;
                    a = 0.97f * k * k * (3f - 2f * k); // yumuşak geçiş
                }
                px[y * n + x] = ((int) (a * 255f) << 24) | 0x0A101C;
            }
        }
        darkHole.setPixels(px, 0, n, 0, 0, n, n);
        darkSrc.set(0, 0, n, n);
    }

    /** Oyuncu çevresi dışını karartır (yalnızca çizim; oyun mantığını değiştirmez). */
    public void drawDarkness(Canvas c, GameWorld w, float width, float height) {
        ensureDarkHole();
        float r = w.lightRadius() * s;
        float cx = w.px * s, cy = w.lightCenterY() * s;
        float l = cx - r, t0 = cy - r, rr = cx + r, b = cy + r;
        Ui.rf.set(l, t0, rr, b);
        c.drawBitmap(darkHole, darkSrc, Ui.rf, darkPaint);
        Ui.fill.setShader(null);
        Ui.fill.setColor(DARK_COLOR);
        // Kaydırılmış (ortalanmış) tuvalde de tüm ekranı kaplasın diye genişçe çizilir
        float x0 = -width, x1 = width * 2f;
        if (t0 > 0) c.drawRect(x0, 0, x1, t0, Ui.fill);
        if (b < height) c.drawRect(x0, b, x1, height, Ui.fill);
        c.drawRect(x0, Math.max(0, t0), l, Math.min(height, b), Ui.fill);
        c.drawRect(rr, Math.max(0, t0), x1, Math.min(height, b), Ui.fill);
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
