package com.golgen.oyun.screens;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;

import com.golgen.oyun.GameView;
import com.golgen.oyun.core.GameEvents;
import com.golgen.oyun.core.GameWorld;
import com.golgen.oyun.core.MissionDef;
import com.golgen.oyun.core.Missions;
import com.golgen.oyun.core.Progress;
import com.golgen.oyun.core.RunResult;
import com.golgen.oyun.core.Synth;
import com.golgen.oyun.core.DifficultyProfile;
import com.golgen.oyun.ui.Button;
import com.golgen.oyun.ui.Icons;
import com.golgen.oyun.ui.Screen;
import com.golgen.oyun.ui.Theme;
import com.golgen.oyun.ui.Ui;
import com.golgen.oyun.ui.WorldRenderer;

/** Oynanış ekranı + HUD + duraklatma menüsü. */
public final class PlayScreen extends Screen implements GameEvents {
    private static final int READY = 0, PLAYING = 1, PAUSED = 2, ENDING = 3;
    private static final float SENSITIVITY = 1.25f;

    private final GameWorld world = new GameWorld(1);
    private final WorldRenderer renderer = new WorldRenderer();
    private final com.golgen.oyun.ui.FloatingTexts popups = new com.golgen.oyun.ui.FloatingTexts();
    private final RunResult result = new RunResult();
    private final Progress.Report report = new Progress.Report();
    private int mode;
    private int missionIndex;
    private MissionDef mission;
    private int phase;
    private float phaseTime;
    private int pointerId = -1;
    private float lastX, lastY;
    private float shake, flash;
    private float worldH;

    private final Button pauseBtn;
    private final Button resumeBtn, restartBtn, homeBtn, soundBtn, musicBtn, vibBtn;

    // HUD metin önbelleği (kare başına string üretmemek için)
    private int lastScore = -1, lastSec = -1, lastDiamonds = -1, lastProg = -1;
    private String scoreText = "0", timeText = "0:00", diamondText = "0", progText = "";

    public PlayScreen(GameView g) {
        super(g);
        world.setEvents(this);
        pauseBtn = add(new Button(null, Icons.PAUSE, Button.ICON, this::pause));
        resumeBtn = add(new Button("DEVAM ET", Icons.PLAY, Button.PRIMARY, this::resume));
        restartBtn = add(new Button("YENİDEN BAŞLAT", Icons.RESTART, Button.SECONDARY, () -> {
            restartNow();
        }));
        homeBtn = add(new Button("ANA MENÜ", Icons.HOME, Button.SECONDARY, g::goMenu));
        soundBtn = add(new Button(null, Icons.SOUND, Button.ICON, () -> {
            g.progress.sound = !g.progress.sound;
            g.applySettings();
            g.save();
            syncToggles();
        }));
        musicBtn = add(new Button(null, Icons.MUSIC, Button.ICON, () -> {
            g.progress.music = !g.progress.music;
            g.applySettings();
            g.save();
            syncToggles();
        }));
        vibBtn = add(new Button(null, Icons.VIBRATE, Button.ICON, () -> {
            g.progress.vibration = !g.progress.vibration;
            g.applySettings();
            g.save();
            g.haptics.medium();
            syncToggles();
        }));
    }

    /** Oyun modu seçimi; enter() sırasında dünya sıfırlanır. */
    public void setup(int mode, int missionIndex) {
        this.mode = mode;
        this.missionIndex = missionIndex;
        this.mission = mode == RunResult.MODE_MISSION ? Missions.ALL[missionIndex] : null;
    }

    public int mode() { return mode; }
    public int missionIndex() { return missionIndex; }

    @Override
    protected void layout() {
        renderer.s = w / GameWorld.WIDTH;
        worldH = h / renderer.s;
        world.setArenaHeight(worldH);
        float s = 112f * u;
        pauseBtn.set(w - safeR - 36f * u - s, safeT + 30f * u, w - safeR - 36f * u, safeT + 30f * u + s);
        float cx = w / 2f, bw = Math.min(w - 160f * u, 720f * u), bh = 130f * u;
        float top = h * 0.40f;
        resumeBtn.set(cx - bw / 2f, top, cx + bw / 2f, top + bh);
        restartBtn.set(cx - bw / 2f, top + bh + 30f * u, cx + bw / 2f, top + bh * 2f + 30f * u);
        homeBtn.set(cx - bw / 2f, top + bh * 2f + 60f * u, cx + bw / 2f, top + bh * 3f + 60f * u);
        float ts = 120f * u, ty = homeBtn.rect.bottom + 60f * u;
        soundBtn.set(cx - ts * 1.5f - 40f * u, ty, cx - ts * 0.5f - 40f * u, ty + ts);
        musicBtn.set(cx - ts * 0.5f, ty, cx + ts * 0.5f, ty + ts);
        vibBtn.set(cx + ts * 0.5f + 40f * u, ty, cx + ts * 1.5f + 40f * u, ty + ts);
    }

    @Override
    public void enter() {
        super.enter();
        restartNow();
    }

    private void restartNow() {
        DifficultyProfile p = mission != null ? mission.profile() : DifficultyProfile.endless();
        world.setArenaHeight(worldH > 0 ? worldH : 1778f);
        world.start(p, mission, System.nanoTime());
        renderer.particles.clear();
        popups.clear();
        phase = READY;
        phaseTime = 0f;
        pointerId = -1;
        shake = flash = 0f;
        lastScore = lastSec = lastDiamonds = lastProg = -1;
        setPausedUi(false);
        syncToggles();
    }

    private void setPausedUi(boolean paused) {
        pauseBtn.visible = !paused && phase != ENDING;
        resumeBtn.visible = restartBtn.visible = homeBtn.visible = paused;
        soundBtn.visible = musicBtn.visible = vibBtn.visible = paused;
    }

    private void syncToggles() {
        soundBtn.icon = g.progress.sound ? Icons.SOUND : Icons.SOUND_OFF;
        soundBtn.style = g.progress.sound ? Button.ICON : Button.GHOST;
        musicBtn.style = g.progress.music ? Button.ICON : Button.GHOST;
        vibBtn.style = g.progress.vibration ? Button.ICON : Button.GHOST;
    }

    private void pause() {
        if (phase != PLAYING && phase != READY) return;
        phase = PAUSED;
        pointerId = -1;
        setPausedUi(true);
        dumpTargets();
    }

    private void resume() {
        phase = PLAYING;
        setPausedUi(false);
        dumpTargets();
    }

    @Override
    public void onAppPause() {
        if (phase == PLAYING) pause();
    }

    @Override
    public boolean onBack() {
        if (phase == PAUSED) resume();
        else if (phase == PLAYING || phase == READY) pause();
        return true;
    }

    @Override
    public boolean onTouch(MotionEvent e) {
        if (dispatchButtons(e)) return true;
        if (phase == PAUSED || phase == ENDING) return true;
        int a = e.getActionMasked();
        switch (a) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (pointerId != -1) break;
                int i = e.getActionIndex();
                pointerId = e.getPointerId(i);
                lastX = e.getX(i);
                lastY = e.getY(i);
                if (phase == READY) phase = PLAYING;
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                int i = e.findPointerIndex(pointerId);
                if (i < 0) break;
                // Geçmiş noktalar dahil (yüksek örnekleme hızlı ekranlarda hassasiyet)
                float x = e.getX(i), y = e.getY(i);
                float k = SENSITIVITY / renderer.s;
                world.moveTargetBy((x - lastX) * k, (y - lastY) * k);
                lastX = x;
                lastY = y;
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (e.getPointerId(e.getActionIndex()) == pointerId) pointerId = -1;
                break;
            case MotionEvent.ACTION_CANCEL:
                pointerId = -1;
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        phaseTime += dt;
        if (phase == PLAYING) {
            world.update(dt);
            if (world.state != GameWorld.RUNNING) {
                phase = ENDING;
                phaseTime = 0f;
                pointerId = -1;
                setPausedUi(false);
                pauseBtn.visible = false;
            }
        }
        if (phase != PAUSED) {
            renderer.update(dt, phase == PLAYING ? world.scroll : 0f);
            popups.update(dt);
        }
        if (shake > 0f) shake = Math.max(0f, shake - dt * 3f);
        if (flash > 0f) flash = Math.max(0f, flash - dt * 2.5f);
        if (phase == ENDING && phaseTime > 1.15f) finishRun();
        refreshHud();
    }

    private void finishRun() {
        phase = PAUSED; // tekrar çağrılmasın
        world.toResult(mode, missionIndex, result);
        g.progress.applyRun(result, g.epochDay(), report);
        g.save();
        g.gameOver.show(result, report);
        g.go(g.gameOver);
    }

    private void refreshHud() {
        int sc = world.score();
        if (sc != lastScore) { lastScore = sc; scoreText = Integer.toString(sc); }
        int sec = (int) world.time;
        if (sec != lastSec) {
            lastSec = sec;
            timeText = (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60);
        }
        if (world.diamonds != lastDiamonds) { lastDiamonds = world.diamonds; diamondText = Integer.toString(lastDiamonds); }
        if (mission != null) {
            int pr = Math.min(mission.target, mission.progress(world));
            if (pr != lastProg) {
                lastProg = pr;
                String unit;
                switch (mission.type) {
                    case MissionDef.DODGE: unit = " kaçış"; break;
                    case MissionDef.COLLECT: unit = " elmas"; break;
                    default: unit = " sn"; break;
                }
                progText = pr + " / " + mission.target + unit;
            }
        }
    }

    // ---------------- Oyun olayları ----------------
    @Override
    public void onEvent(int type, float x, float y) {
        switch (type) {
            case DIAMOND:
                g.sound.play(Synth.DIAMOND);
                g.haptics.light();
                renderer.particles.burst(x, y, 14, Theme.GOLD, 420f, 7f, 0.6f);
                popups.add("+25", x, y - 40f, Theme.GOLD);
                break;
            case HIT:
                g.sound.play(Synth.HIT);
                g.haptics.medium();
                renderer.particles.burst(x, y, 22, Theme.PURPLE_LIGHT, 600f, 8f, 0.6f);
                shake = 1f;
                flash = 0.35f;
                popups.add("-1 can", x, y - 60f, Theme.PURPLE_LIGHT);
                break;
            case SHADOW_SPAWN:
                g.sound.play(Synth.SHADOW, 0.9f, 1f);
                renderer.particles.burst(x, y, 18, Theme.PURPLE, 300f, 7f, 0.9f);
                break;
            case DODGE:
                g.sound.play(Synth.DODGE, 0.8f, 1f);
                renderer.particles.burst(world.px, world.py, 8, Theme.TEAL, 300f, 5f, 0.5f);
                popups.add("Kıl payı! +15", world.px, world.py - 70f, Theme.TEAL);
                break;
            case DEATH:
                g.sound.play(Synth.GAME_OVER);
                g.haptics.heavy();
                renderer.particles.burst(x, y, 60, Theme.WHITE, 900f, 9f, 1.1f);
                renderer.particles.burst(x, y, 30, Theme.PURPLE_LIGHT, 700f, 8f, 1.1f);
                shake = 1.4f;
                flash = 0.8f;
                break;
            case WIN:
                g.sound.play(Synth.SUCCESS);
                g.haptics.medium();
                renderer.particles.burst(x, y, 50, Theme.GOLD, 800f, 8f, 1.2f);
                renderer.particles.burst(x, y, 30, Theme.TEAL, 600f, 7f, 1.2f);
                break;
            default:
                break;
        }
    }

    // ---------------- Çizim ----------------
    @Override
    public void draw(Canvas c) {
        c.save();
        if (shake > 0f) {
            float a = shake * 14f * u;
            c.translate((float) Math.sin(time * 90f) * a, (float) Math.cos(time * 75f) * a);
        }
        renderer.drawBackground(c, w, h, world.scrollDistance);
        boolean playerVisible = !(phase == ENDING && world.state == GameWorld.DEAD);
        renderer.drawWorld(c, world, g.skin(), playerVisible);
        popups.draw(c, renderer.s, u);
        c.restore();
        if (flash > 0f) c.drawColor(Theme.alpha(Theme.WHITE, flash * 0.35f));

        drawHud(c);
        if (phase == READY) drawReadyHint(c);
        if (phase == ENDING) drawEnding(c);
        if (phase == PAUSED && resumeBtn.visible) drawPause(c);
        drawButtons(c);
    }

    private void drawHud(Canvas c) {
        float top = safeT + 30f * u;
        // Üst bölgeyi hafif karart (okunabilirlik)
        Ui.shaderPaint.setShader(null);
        Ui.fill.setShader(null);
        // Skor
        float cx = w / 2f;
        Ui.text(c, scoreText, cx, top + 92f * u, 96f * u, Theme.WHITE, Paint.Align.CENTER, Ui.NUMBERS);
        Ui.text(c, timeText, cx, top + 142f * u, 34f * u, Theme.MUTED, Paint.Align.CENTER, Ui.NUMBERS);
        // Sol: canlar + elmaslar
        float lx = safeL + 44f * u;
        for (int i = 0; i < world.profile().lives; i++) {
            boolean full = i < world.lives;
            Icons.draw(c, Icons.HEART, lx + 34f * u + i * 66f * u, top + 40f * u, 26f * u, full ? Theme.TEAL : Theme.alpha(Theme.LINE, 0.9f));
        }
        Ui.diamond(c, lx + 34f * u, top + 106f * u, 22f * u, 1f);
        Ui.text(c, diamondText, lx + 70f * u, Ui.centerY(top + 106f * u, 40f * u), 40f * u, Theme.GOLD, Paint.Align.LEFT, Ui.NUMBERS);
        // Görev ilerlemesi
        if (mission != null) {
            float bw = Math.min(w - 200f * u, 640f * u), by = top + 180f * u;
            Ui.text(c, mission.title, cx, by, 30f * u, Theme.TEAL, Paint.Align.CENTER, Ui.BOLD);
            float barT = by + 20f * u;
            Ui.panel(c, cx - bw / 2f, barT, cx + bw / 2f, barT + 16f * u, 8f * u, Theme.alpha(Theme.PANEL, 0.9f), Theme.LINE, 2f * u);
            float p = Math.min(1f, lastProg / (float) mission.target);
            if (p > 0f) Ui.panel(c, cx - bw / 2f, barT, cx - bw / 2f + bw * p, barT + 16f * u, 8f * u, Theme.TEAL, 0, 0);
            Ui.text(c, progText, cx, barT + 56f * u, 30f * u, Theme.WHITE, Paint.Align.CENTER, Ui.MEDIUM);
        }
        // Gölge sayısı
        int sh = world.shadows.size();
        if (sh > 0) {
            float rx = w - safeR - 48f * u, ry = pauseBtn.rect.bottom + 52f * u;
            for (int i = 0; i < sh; i++) {
                Ui.fill.setColor(Theme.alpha(Theme.PURPLE_LIGHT, 0.9f));
                c.drawCircle(rx - i * 34f * u - 10f * u, ry, 10f * u, Ui.fill);
            }
        }
    }

    private void drawReadyHint(Canvas c) {
        float cx = w / 2f, cy = h * 0.58f;
        float pulse = 0.6f + 0.4f * (float) Math.sin(time * 4f);
        Ui.panel(c, cx - 400f * u, cy - 80f * u, cx + 400f * u, cy + 120f * u, 40f * u, Theme.alpha(Theme.NAVY_DEEP, 0.8f), Theme.alpha(Theme.TEAL, 0.6f), 3f * u);
        Ui.text(c, "Parmağını bas ve sürükle", cx, cy, 44f * u, Theme.alpha(Theme.WHITE, pulse), Paint.Align.CENTER, Ui.BOLD);
        String sub = mission != null ? mission.description() : "Gölgen geçmiş hareketlerini tekrar eder";
        Ui.textFit(c, sub, cx, cy + 60f * u, 32f * u, 760f * u, Theme.TEAL, Paint.Align.CENTER, Ui.MEDIUM);
    }

    private void drawEnding(Canvas c) {
        float k = Math.min(1f, phaseTime / 0.5f);
        c.drawColor(Theme.alpha(Theme.NAVY_DEEP, 0.55f * k));
        String title;
        int color;
        if (world.state == GameWorld.WON) { title = "GÖREV TAMAM!"; color = Theme.GOLD; }
        else if (world.cause == GameWorld.CAUSE_SHADOW) { title = "GÖLGEN SENİ YAKALADI"; color = Theme.PURPLE_LIGHT; }
        else { title = "ENGELE ÇARPTIN"; color = Theme.PURPLE_LIGHT; }
        float sc = 0.8f + 0.2f * k;
        Ui.textFit(c, title, w / 2f, h * 0.45f, 74f * u * sc, w - 100f * u, Theme.alpha(color, k), Paint.Align.CENTER, Ui.DISPLAY);
    }

    private void drawPause(Canvas c) {
        c.drawColor(Theme.alpha(Theme.NAVY_DEEP, 0.82f));
        float cx = w / 2f;
        float pw = Math.min(w - 100f * u, 820f * u);
        Ui.panel(c, cx - pw / 2f, resumeBtn.rect.top - 230f * u, cx + pw / 2f, soundBtn.rect.bottom + 60f * u, 48f * u,
                Theme.alpha(Theme.PANEL, 0.96f), Theme.alpha(Theme.PURPLE, 0.7f), 3f * u);
        Ui.text(c, "DURAKLATILDI", cx, resumeBtn.rect.top - 120f * u, 64f * u, Theme.WHITE, Paint.Align.CENTER, Ui.DISPLAY);
        Ui.text(c, scoreText + " puan  ·  " + timeText, cx, resumeBtn.rect.top - 55f * u, 34f * u, Theme.MUTED, Paint.Align.CENTER, Ui.MEDIUM);
    }
}
