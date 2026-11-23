package com.golgen.oyun.screens;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;

import com.golgen.oyun.GameView;
import com.golgen.oyun.core.DifficultyProfile;
import com.golgen.oyun.core.GameEvents;
import com.golgen.oyun.core.GameWorld;
import com.golgen.oyun.core.Geometry;
import com.golgen.oyun.core.Shadow;
import com.golgen.oyun.core.Synth;
import com.golgen.oyun.ui.Button;
import com.golgen.oyun.ui.Icons;
import com.golgen.oyun.ui.Screen;
import com.golgen.oyun.ui.Theme;
import com.golgen.oyun.ui.Ui;
import com.golgen.oyun.ui.WorldRenderer;

/** İnteraktif öğretici: hareket, gölge, engel ve elmas adımları. */
public final class TutorialScreen extends Screen implements GameEvents {
    private static final int MOVE = 0, SHADOW = 1, OBSTACLES = 2, DONE = 3;
    private static final float SHADOW_DELAY = 2.0f, SURVIVE = 7f;

    private final GameWorld world = new GameWorld(5);
    private final WorldRenderer renderer = new WorldRenderer();
    private int step;
    private float stepTime, moved, safeTime, caughtMsg, nextWall;
    private float lastPx, lastPy;
    private int pointerId = -1;
    private float lastX, lastY;
    private boolean fromSettings;
    private final Button skipBtn, playBtn, menuBtn;
    private final String[] titles = {"1 / 3  ·  Hareket", "2 / 3  ·  Gölgen", "3 / 3  ·  Engeller ve Elmaslar", "Hazırsın!"};
    private final String[] texts = {
            "Parmağını ekranın herhangi bir yerine bas ve sürükle. Karakterin parmağının hareketini izler.",
            "Bu senin gölgen: 2 saniye önceki hareketlerini aynen tekrarlar. Durursan seni yakalar, hareket etmeye devam et!",
            "Mor neon engellere çarpma, boşluklarından geç. Altın elmasları topla: 2 elmas topla.",
            "Gölgen ve engeller zamanla çoğalır. Ne kadar uzun dayanırsan o kadar çok puan!"
    };
    private String[][] wrapped = new String[4][];
    private String progressText = "";

    public TutorialScreen(GameView g) {
        super(g);
        world.setEvents(this);
        skipBtn = add(new Button("Atla", Icons.NONE, Button.GHOST, this::finish));
        playBtn = add(new Button("OYNA", Icons.PLAY, Button.PRIMARY, () -> { complete(); g.startEndless(); }));
        menuBtn = add(new Button("ANA MENÜ", Icons.HOME, Button.SECONDARY, () -> { complete(); g.goMenu(); }));
    }

    public void setFromSettings(boolean v) {
        fromSettings = v;
        // Ayarlar'dan/menüden açıldıysa kapatınca o ekrana dönülür
        returnTo = v ? g.current() : null;
    }

    private Screen returnTo;

    private void goBack() {
        if (returnTo != null && returnTo != this) g.go(returnTo); else g.goMenu();
    }

    private void complete() {
        if (!g.progress.tutorialDone) {
            g.progress.tutorialDone = true;
            g.save();
        }
    }

    private void finish() {
        complete();
        goBack();
    }

    @Override
    protected void layout() {
        renderer.s = Math.min(w / GameWorld.WIDTH, h / 1200f);
        world.setArenaHeight(h / renderer.s);
        skipBtn.set(w - safeR - 220f * u, safeT + 30f * u, w - safeR - 30f * u, safeT + 130f * u);
        float cx = w / 2f, bw = Math.min(w - 160f * u, 700f * u), bh = 130f * u;
        playBtn.set(cx - bw / 2f, h * 0.56f, cx + bw / 2f, h * 0.56f + bh);
        menuBtn.set(cx - bw / 2f, playBtn.rect.bottom + 30f * u, cx + bw / 2f, playBtn.rect.bottom + 30f * u + bh);
        float maxW = Math.min(w - 160f * u, 860f * u);
        for (int i = 0; i < 4; i++) wrapped[i] = Ui.wrap(texts[i], 36f * u, maxW, Ui.MEDIUM);
    }

    @Override
    public void enter() {
        super.enter();
        world.start(DifficultyProfile.endless(), null, 5);
        world.spawnObstacles = false;
        world.spawnShadows = false;
        world.spawnDiamonds = false;
        world.shadowsHarmless = true;
        world.obstaclesHarmless = true;
        renderer.particles.clear();
        setStep(MOVE);
        skipBtn.label = fromSettings ? "Kapat" : "Atla";
    }

    private void setStep(int s) {
        step = s;
        stepTime = 0f;
        moved = 0f;
        safeTime = 0f;
        caughtMsg = 0f;
        lastPx = world.px;
        lastPy = world.py;
        playBtn.visible = menuBtn.visible = s == DONE;
        skipBtn.visible = s != DONE;
        if (s == SHADOW) {
            world.shadows.clear();
            world.addShadow(SHADOW_DELAY);
        } else if (s == OBSTACLES) {
            world.shadows.clear();
            world.diamonds = 0;
            nextWall = 0f;
        } else if (s == DONE) {
            world.shadows.clear();
            world.obstacles.clear();
            world.diamondPool.clear();
            g.sound.play(Synth.SUCCESS);
        }
        if (s != MOVE) g.haptics.light();
    }

    @Override
    public boolean onBack() {
        finish();
        return true;
    }

    @Override
    public boolean onTouch(MotionEvent e) {
        if (dispatchButtons(e)) return true;
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            pointerId = e.getPointerId(0);
            lastX = e.getX();
            lastY = e.getY();
        } else if (a == MotionEvent.ACTION_MOVE && step != DONE) {
            int i = e.findPointerIndex(pointerId);
            if (i >= 0) {
                float k = 1.25f / renderer.s;
                world.moveTargetBy((e.getX(i) - lastX) * k, (e.getY(i) - lastY) * k);
                lastX = e.getX(i);
                lastY = e.getY(i);
            }
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            pointerId = -1;
        }
        return true;
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        stepTime += dt;
        if (step != DONE) world.update(dt);
        renderer.update(dt, step == OBSTACLES ? world.scroll : 0f);
        if (caughtMsg > 0f) caughtMsg -= dt;
        switch (step) {
            case MOVE: {
                moved += (float) Math.sqrt(Geometry.dist2(world.px, world.py, lastPx, lastPy));
                lastPx = world.px;
                lastPy = world.py;
                progressText = Math.min(100, (int) (moved / 12f)) + "%";
                if (moved > 1200f) setStep(SHADOW);
                break;
            }
            case SHADOW: {
                if (world.shadows.size() == 0) world.addShadow(SHADOW_DELAY); // yeterli geçmiş oluşunca
                Shadow s = world.shadows.size() > 0 ? world.shadows.get(0) : null;
                if (s != null && s.isSolid(world.time)) {
                    float r = GameWorld.PLAYER_R * GameWorld.HIT_SCALE * 2f;
                    if (Geometry.dist2(world.px, world.py, s.x, s.y) < r * r) {
                        if (caughtMsg <= 0f) {
                            g.sound.play(Synth.HIT, 0.6f, 1f);
                            g.haptics.medium();
                        }
                        caughtMsg = 1.6f;
                        safeTime = 0f;
                    } else {
                        safeTime += dt;
                    }
                }
                progressText = (int) Math.min(SURVIVE, safeTime) + " / " + (int) SURVIVE + " sn";
                if (safeTime >= SURVIVE) setStep(OBSTACLES);
                break;
            }
            case OBSTACLES: {
                nextWall -= dt;
                if (nextWall <= 0f) {
                    nextWall = 2.6f;
                    float gap = 300f;
                    float gx = 200f + (float) Math.random() * 600f;
                    world.addWall(-30f, gx, gap);
                    world.addDiamond(gx, -30f);
                }
                progressText = world.diamonds + " / 2 elmas";
                if (world.diamonds >= 2) setStep(DONE);
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void onEvent(int type, float x, float y) {
        if (type == DIAMOND) {
            g.sound.play(Synth.DIAMOND);
            g.haptics.light();
            renderer.particles.burst(x, y, 14, Theme.GOLD, 420f, 7f, 0.6f);
        } else if (type == HIT) {
            g.sound.play(Synth.HIT, 0.7f, 1f);
            g.haptics.medium();
        } else if (type == SHADOW_SPAWN) {
            g.sound.play(Synth.SHADOW);
            renderer.particles.burst(x, y, 18, Theme.PURPLE, 300f, 7f, 0.9f);
        }
    }

    @Override
    public void draw(Canvas c) {
        renderer.drawBackground(c, w, h, world.scrollDistance);
        if (step != DONE) renderer.drawWorld(c, world, g.skin(), true);
        float cx = w / 2f;
        // Bilgi kartı
        String[] lines = wrapped[step];
        if (lines != null) {
            float top = step == DONE ? h * 0.22f : safeT + 160f * u;
            float ch = 130f * u + lines.length * 48f * u + 60f * u;
            float cw = Math.min(w - 80f * u, 940f * u);
            Ui.panel(c, cx - cw / 2f, top, cx + cw / 2f, top + ch, 40f * u, Theme.alpha(Theme.PANEL, 0.92f), Theme.alpha(Theme.TEAL, 0.7f), 3f * u);
            Ui.text(c, titles[step], cx, top + 80f * u, step == DONE ? 64f * u : 44f * u, step == DONE ? Theme.GOLD : Theme.TEAL, Paint.Align.CENTER, step == DONE ? Ui.DISPLAY : Ui.BOLD);
            for (int i = 0; i < lines.length; i++) {
                Ui.text(c, lines[i], cx, top + 140f * u + i * 48f * u, 36f * u, Theme.WHITE, Paint.Align.CENTER, Ui.MEDIUM);
            }
            if (step != DONE) {
                Ui.text(c, progressText, cx, top + ch - 30f * u, 34f * u, Theme.GOLD, Paint.Align.CENTER, Ui.NUMBERS);
            }
        }
        if (step == MOVE && moved < 60f) drawHandHint(c);
        if (caughtMsg > 0f) {
            float a = Math.min(1f, caughtMsg);
            Ui.text(c, "Yakalandın! Durmadan hareket et", cx, h * 0.5f, 46f * u, Theme.alpha(Theme.PURPLE_LIGHT, a), Paint.Align.CENTER, Ui.BOLD);
        }
        drawButtons(c);
    }

    private void drawHandHint(Canvas c) {
        float t = (time % 1.6f) / 1.6f;
        float x = w / 2f - 180f * u + 360f * u * (0.5f - 0.5f * (float) Math.cos(t * Math.PI * 2));
        float y = h * 0.82f;
        Ui.glow(c, x, y, 90f * u, Theme.TEAL, 0.5f);
        Ui.fill.setColor(Theme.alpha(Theme.WHITE, 0.85f));
        c.drawCircle(x, y, 26f * u, Ui.fill);
        Ui.stroke.setColor(Theme.alpha(Theme.WHITE, 0.4f));
        Ui.stroke.setStrokeWidth(4f * u);
        c.drawCircle(x, y, 44f * u, Ui.stroke);
    }
}
