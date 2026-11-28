package com.golgen.oyun.screens;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;

import com.golgen.oyun.GameView;
import com.golgen.oyun.core.GameEvents;
import com.golgen.oyun.core.GameWorld;
import com.golgen.oyun.core.L10n;
import com.golgen.oyun.core.MissionDef;
import com.golgen.oyun.core.Missions;
import com.golgen.oyun.core.PowerUp;
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
    private float stageBanner;
    private String stageText = "";
    private int lastStage = -1, lastMult = -1;
    private String multText = "";
    private boolean dark;
    private int phaseBeforePause = PLAYING;
    private float offX;

    // ---- Yalnızca debug çekim modu (mağaza görselleri/videolar): sabit tohum ve otomatik pilot ----
    /** Otomatik pilot arayüzü (debug kaynak kümesindeki bot uygular). */
    public interface Pilot { void act(GameWorld w); }
    public long debugSeed;
    public Pilot debugPilot;
    public void debugBegin() { if (phase == READY) phase = PLAYING; }
    public GameWorld debugWorld() { return world; }
    /** Oyuncunun ekran koordinatı (çekimde dokunma işareti için). */
    public boolean debugPlayerScreenPos(float[] out) {
        if (phase != PLAYING) return false;
        out[0] = offX + world.px * renderer.s;
        out[1] = world.py * renderer.s;
        return true;
    }
    private int lastLevelStage = -1;
    private String levelStageText = "";
    private float levelBanner;

    private final Button pauseBtn;
    private final Button resumeBtn, restartBtn, homeBtn, soundBtn, musicBtn, vibBtn;

    // HUD metin önbelleği (kare başına string üretmemek için)
    private int lastScore = -1, lastSec = -1, lastDiamonds = -1, lastProg = -1;
    private String scoreText = "0", timeText = "0:00", diamondText = "0", progText = "";
    private int hudVer = Integer.MIN_VALUE;

    // Yerelleştirilmiş metin önbelleği (onLocalize'da ve bölüm değişince yenilenir)
    private String readyHint = "", readySub = "", pausedTitle = "", stageSub = "", levelBannerTitle = "", levelBannerSub = "";
    private String endWin = "", endCaught = "", endObstacle = "";
    private String pauseInfo = "";
    private int pauseInfoScore = -1, pauseInfoSec = -1, pauseInfoVer = Integer.MIN_VALUE;

    public PlayScreen(GameView g) {
        super(g);
        world.setEvents(this);
        pauseBtn = add(new Button(null, Icons.PAUSE, Button.ICON, this::pause));
        resumeBtn = add(new Button(null, Icons.PLAY, Button.PRIMARY, this::resume).key("play.pause.resume"));
        restartBtn = add(new Button(null, Icons.RESTART, Button.SECONDARY, () -> {
            if (mode == RunResult.MODE_MISSION) { g.startMission(missionIndex); return; } // yeni deneme 1 can
            restartNow();
        }).key("play.pause.restart"));
        homeBtn = add(new Button(null, Icons.HOME, Button.SECONDARY, g::goMenu).key("play.pause.main_menu"));
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

    @Override
    protected void onLocalize() {
        readyHint = L10n.t("play.ready.hint");
        pausedTitle = L10n.t("play.pause.title");
        stageSub = L10n.t("play.banner.stage_sub");
        levelBannerTitle = L10n.t("play.banner.level_stage");
        levelBannerSub = L10n.p("play.banner.level_stage_sub", MissionDef.STARS_PER_STAGE);
        endWin = L10n.t("play.end.level_complete");
        endCaught = L10n.t("play.end.caught"); // Not: oyun adıyla kelime oyunu; marka adımında gözden geçirilebilir
        endObstacle = L10n.t("play.end.hit_obstacle");
        rebuildReadySub();
        // HUD metinleri (aşama, kombo, bölüm satırı) refreshHud'da L10n.version() ile yenilenir
    }

    /** Başlangıç ipucunun alt satırı: bölüme (harita adı/özellikleri) ve dile bağlıdır. */
    private void rebuildReadySub() {
        readySub = mission != null
                ? L10n.p("play.ready.level_sub", MissionDef.STARS_PER_STAGE,
                        mission.map.name(), mission.map.featuresText(), MissionDef.STAGES)
                : L10n.t("play.ready.endless_sub");
    }
    public int missionIndex() { return missionIndex; }

    @Override
    protected void layout() {
        // Ölçek: genişliğe sığ, ama en az 1200 birim yükseklik görünsün (bölünmüş ekran/küçük pencere);
        // gerekirse oyun alanı yatayda ortalanır.
        renderer.s = Math.min(w / GameWorld.WIDTH, h / 1200f);
        offX = (float) Math.floor((w - GameWorld.WIDTH * renderer.s) / 2f); // tam piksel: karanlık katmanında dikiş olmasın
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
        world.start(p, mission, debugSeed != 0 ? debugSeed : System.nanoTime());
        renderer.particles.clear();
        popups.clear();
        phase = READY;
        phaseTime = 0f;
        pointerId = -1;
        shake = flash = 0f;
        lastScore = lastSec = lastDiamonds = lastProg = -1;
        lastStage = lastMult = -1;
        stageBanner = 0f;
        dark = mission != null && mission.has(com.golgen.oyun.core.MapTheme.F_DARK);
        renderer.setTheme(mission != null ? mission.map : com.golgen.oyun.core.Maps.ENDLESS);
        lastLevelStage = -1;
        rebuildReadySub();
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
        phaseBeforePause = phase; // READY'den duraklatılınca devamda yine "dokun ve başla" beklenir
        phase = PAUSED;
        pointerId = -1;
        setPausedUi(true);
        dumpTargets();
    }

    private void resume() {
        phase = phaseBeforePause == READY ? READY : PLAYING;
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
            if (debugPilot != null) debugPilot.act(world);
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
        if (stageBanner > 0f && phase != PAUSED) stageBanner -= dt;
        if (levelBanner > 0f && phase != PAUSED) levelBanner -= dt;
        if (phase == ENDING && phaseTime > 1.15f) finishRun();
        refreshHud();
    }

    private void finishRun() {
        phase = PAUSED; // tekrar çağrılmasın
        world.toResult(mode, missionIndex, result);
        g.adPolicy.onRunFinished(result.time);
        if (mode == RunResult.MODE_MISSION && result.won) g.progress.energy.refund(g.clock);
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
        int ver = L10n.version();
        boolean lang = ver != hudVer; // dil değişince sayılar aynı olsa da metinler yenilenir
        hudVer = ver;
        if (world.stage != lastStage || lang) { lastStage = world.stage; stageText = L10n.f("hud.stage", lastStage); }
        int m = world.multiplier();
        if (m != lastMult || lang) { lastMult = m; multText = L10n.f("hud.combo", m); }
        if (mission != null) {
            int pr = world.stageStars;
            if (pr != lastProg || world.levelStage != lastLevelStage || lang) {
                lastProg = pr;
                lastLevelStage = world.levelStage;
                progText = pr + " / " + MissionDef.STARS_PER_STAGE;
                levelStageText = L10n.f("hud.level_stage", world.levelStage, MissionDef.STAGES, mission.title());
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
                popups.add(L10n.t("hud.popup.hit"), x, y - 60f, Theme.PURPLE_LIGHT);
                break;
            case SHADOW_SPAWN:
                g.sound.play(Synth.SHADOW, 0.9f, 1f);
                renderer.particles.burst(x, y, 18, Theme.PURPLE, 300f, 7f, 0.9f);
                break;
            case DODGE:
                g.sound.play(Synth.DODGE, 0.8f, 1f);
                renderer.particles.burst(world.px, world.py, 8, Theme.TEAL, 300f, 5f, 0.5f);
                popups.add(L10n.t("hud.popup.near_miss"), world.px, world.py - 70f, Theme.TEAL);
                break;
            case DEATH:
                g.sound.play(Synth.GAME_OVER);
                g.haptics.heavy();
                renderer.particles.burst(x, y, 60, Theme.WHITE, 900f, 9f, 1.1f);
                renderer.particles.burst(x, y, 30, Theme.PURPLE_LIGHT, 700f, 8f, 1.1f);
                shake = 1.4f;
                flash = 0.8f;
                break;
            case POWERUP:
                g.sound.play(Synth.PURCHASE);
                g.haptics.light();
                renderer.particles.burst(x, y, 18, com.golgen.oyun.ui.WorldRenderer.powerupColor(world.lastPowerup), 420f, 7f, 0.7f);
                popups.add(L10n.f("hud.popup.powerup", PowerUp.name(world.lastPowerup)), x, y - 50f, com.golgen.oyun.ui.WorldRenderer.powerupColor(world.lastPowerup));
                break;
            case SHIELD_BREAK:
                g.sound.play(Synth.HIT, 0.8f, 1.3f);
                g.haptics.medium();
                renderer.particles.burst(x, y, 26, Theme.TEAL, 650f, 7f, 0.7f);
                popups.add(L10n.t("hud.popup.shield_break"), x, y - 70f, Theme.TEAL);
                shake = 0.6f;
                break;
            case STAGE:
                if (mission == null) {
                    g.sound.play(Synth.SUCCESS, 0.5f, 1.2f);
                    stageBanner = 1.8f;
                }
                break;
            case STAR:
                g.sound.play(Synth.DIAMOND, 0.7f, 1.25f);
                renderer.particles.burst(x, y, 10, Theme.GOLD, 380f, 6f, 0.5f);
                break;
            case LEVEL_STAGE:
                g.sound.play(Synth.SHADOW, 1f, 0.8f);
                g.sound.play(Synth.SUCCESS, 0.7f, 1f);
                g.haptics.medium();
                levelBanner = 2.4f;
                flash = 0.3f;
                break;
            case COMBO:
                g.haptics.light();
                popups.add(L10n.f("hud.popup.combo", world.multiplier()), x, y - 95f, Theme.GOLD);
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
            c.translate(Math.round(Math.sin(time * 90f) * a), Math.round(Math.cos(time * 75f) * a));
        }
        renderer.drawBackground(c, w, h, world.scrollDistance);
        c.translate(offX, 0);
        boolean playerVisible = !(phase == ENDING && world.state == GameWorld.DEAD);
        renderer.drawWorld(c, world, g.skin(), playerVisible);
        if (dark) {
            renderer.drawDarkness(c, world, w, h);
            renderer.drawShadowHints(c, world);
        }
        popups.draw(c, renderer.s, u);
        c.restore();
        if (flash > 0f) c.drawColor(Theme.alpha(Theme.WHITE, flash * 0.35f));

        drawHud(c);
        if (phase == READY) drawReadyHint(c);
        if (stageBanner > 0f && phase == PLAYING) drawStageBanner(c);
        if (levelBanner > 0f && phase == PLAYING) drawLevelBanner(c);
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
        if (mission == null) {
            float sx = cx + 120f * u;
            Ui.textFit(c, stageText, sx, top + 142f * u, 26f * u, w - safeR - 40f * u - sx, Theme.alpha(Theme.TEAL, 0.8f), Paint.Align.LEFT, Ui.BOLD);
        }
        if (lastMult > 1) {
            float pulse = 1f + 0.06f * (float) Math.sin(time * 10f);
            float mx = cx - 120f * u;
            Ui.textFit(c, multText, mx, top + 142f * u, 28f * u * pulse, mx - safeL - 40f * u, Theme.GOLD, Paint.Align.RIGHT, Ui.BOLD);
            // kombo süresi çubuğu
            float k = world.comboTimer / GameWorld.COMBO_WINDOW;
            Ui.panel(c, cx - 110f * u, top + 154f * u, cx - 110f * u + 220f * u * k, top + 160f * u, 3f * u, Theme.alpha(Theme.GOLD, 0.7f), 0, 0);
        }
        // Etkin güçlendirmeler
        float ex = w - safeR - 80f * u, ey = pauseBtn.rect.bottom + 120f * u;
        if (world.shield) { drawEffect(c, ex, ey, Icons.SHIELD, Theme.TEAL, 1f); ey += 84f * u; }
        if (world.slowTimer > 0f) { drawEffect(c, ex, ey, Icons.CLOCK, Theme.PURPLE_LIGHT, world.slowTimer / GameWorld.SLOW_TIME); ey += 84f * u; }
        if (world.magnetTimer > 0f) { drawEffect(c, ex, ey, Icons.MAGNET, Theme.GOLD, world.magnetTimer / GameWorld.MAGNET_TIME); }
        // Sol: canlar + elmaslar
        float lx = safeL + 44f * u;
        for (int i = 0; i < world.profile().lives; i++) {
            boolean full = i < world.lives;
            // Dayanıklılık (bölüm içi darbe hakkı) — bölüm canlarıyla karışmasın diye kalkan simgesi
            Icons.draw(c, Icons.SHIELD, lx + 30f * u + i * 56f * u, top + 40f * u, 24f * u, full ? Theme.TEAL : Theme.alpha(Theme.LINE, 0.9f));
        }
        Ui.diamond(c, lx + 34f * u, top + 106f * u, 22f * u, 1f);
        Ui.text(c, diamondText, lx + 70f * u, Ui.centerY(top + 106f * u, 40f * u), 40f * u, Theme.GOLD, Paint.Align.LEFT, Ui.NUMBERS);
        // Görev ilerlemesi
        if (mission != null) {
            float bw = Math.min(w - 260f * u, 560f * u), by = top + 182f * u;
            int stageCol = world.levelStage == 2 ? Theme.PURPLE_LIGHT : Theme.TEAL;
            // sağdaki gölge noktalarına değmesin
            Ui.textFit(c, levelStageText, cx, by, 28f * u, w - 2f * Math.max(safeL, safeR) - 240f * u, stageCol, Paint.Align.CENTER, Ui.BOLD);
            float barT = by + 18f * u;
            Ui.panel(c, cx - bw / 2f, barT, cx + bw / 2f, barT + 18f * u, 9f * u, Theme.alpha(Theme.PANEL, 0.9f), Theme.LINE, 2f * u);
            float p = Math.min(1f, lastProg / (float) MissionDef.STARS_PER_STAGE);
            if (p > 0f) Ui.panel(c, cx - bw / 2f, barT, cx - bw / 2f + bw * p, barT + 18f * u, 9f * u, Theme.GOLD, 0, 0);
            // iki aşama noktası
            for (int k = 0; k < MissionDef.STAGES; k++) {
                boolean done = k < world.levelStage - 1, cur = k == world.levelStage - 1;
                Ui.fill.setShader(null);
                Ui.fill.setColor(done ? Theme.GOLD : (cur ? stageCol : Theme.LINE));
                c.drawCircle(cx + bw / 2f + 30f * u + k * 30f * u, barT + 9f * u, 9f * u, Ui.fill);
            }
            Icons.draw(c, Icons.STAR, cx - 40f * u, barT + 52f * u, 16f * u, Theme.GOLD);
            Ui.text(c, progText, cx - 16f * u, barT + 62f * u, 30f * u, Theme.WHITE, Paint.Align.LEFT, Ui.NUMBERS);
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

    private void drawEffect(Canvas c, float x, float y, int icon, int color, float k) {
        Ui.fill.setShader(null);
        Ui.fill.setColor(Theme.alpha(Theme.NAVY_DEEP, 0.85f));
        c.drawCircle(x, y, 32f * u, Ui.fill);
        Ui.stroke.setColor(Theme.alpha(color, 0.35f));
        Ui.stroke.setStrokeWidth(5f * u);
        c.drawCircle(x, y, 32f * u, Ui.stroke);
        Ui.stroke.setColor(color);
        Ui.rf.set(x - 32f * u, y - 32f * u, x + 32f * u, y + 32f * u);
        c.drawArc(Ui.rf, -90f, 360f * Math.max(0f, Math.min(1f, k)), false, Ui.stroke);
        Icons.draw(c, icon, x, y, 16f * u, color);
    }

    private void drawStageBanner(Canvas c) {
        float t = stageBanner / 1.8f;
        float a = t > 0.8f ? (1f - t) / 0.2f : Math.min(1f, t / 0.3f);
        float cy = h * 0.42f;
        Ui.glow(c, w / 2f, cy - 20f * u, 420f * u, Theme.TEAL, 0.3f * a);
        Ui.textFit(c, stageText, w / 2f, cy, 84f * u, w - 100f * u, Theme.alpha(Theme.WHITE, a), Paint.Align.CENTER, Ui.DISPLAY);
        Ui.textFit(c, stageSub, w / 2f, cy + 60f * u, 34f * u, w - 100f * u, Theme.alpha(Theme.TEAL, a), Paint.Align.CENTER, Ui.MEDIUM);
    }

    private void drawLevelBanner(Canvas c) {
        float t = levelBanner / 2.4f;
        float a = t > 0.85f ? (1f - t) / 0.15f : Math.min(1f, t / 0.3f);
        float cy = h * 0.42f;
        c.drawColor(Theme.alpha(Theme.NAVY_DEEP, 0.35f * a));
        Ui.glow(c, w / 2f, cy - 20f * u, 460f * u, Theme.PURPLE, 0.45f * a);
        Ui.textFit(c, levelBannerTitle, w / 2f, cy, 96f * u, w - 100f * u, Theme.alpha(Theme.WHITE, a), Paint.Align.CENTER, Ui.DISPLAY);
        Ui.textFit(c, levelBannerSub, w / 2f, cy + 66f * u, 34f * u, w - 100f * u, Theme.alpha(Theme.PURPLE_LIGHT, a), Paint.Align.CENTER, Ui.BOLD);
    }

    private String readySubSrc;
    private float readySubW;
    private String[] readySubLines = new String[0];
    private final float[] readySubSize = {32f};

    private void drawReadyHint(Canvas c) {
        float cx = w / 2f, cy = h * 0.58f;
        float pulse = 0.6f + 0.4f * (float) Math.sin(time * 4f);
        if (readySubSrc != readySub || readySubW != w) {   // uzun harita bilgisi satırlara bölünür (önbellekli)
            readySubSrc = readySub;
            readySubW = w;
            readySubLines = readySub.isEmpty() ? new String[0] : Ui.wrapFit(readySub, 32f * u, 740f * u, 3, Ui.MEDIUM, readySubSize);
        }
        float lh = readySubSize[0] * 1.25f, extra = Math.max(0, readySubLines.length - 1) * lh;
        Ui.panel(c, cx - 400f * u, cy - 80f * u, cx + 400f * u, cy + 120f * u + extra, 40f * u, Theme.alpha(Theme.NAVY_DEEP, 0.8f), Theme.alpha(Theme.TEAL, 0.6f), 3f * u);
        Ui.textFit(c, readyHint, cx, cy, 44f * u, 760f * u, Theme.alpha(Theme.WHITE, pulse), Paint.Align.CENTER, Ui.BOLD);
        for (int i = 0; i < readySubLines.length; i++) {
            Ui.text(c, readySubLines[i], cx, cy + 60f * u + i * lh, readySubSize[0], Theme.TEAL, Paint.Align.CENTER, Ui.MEDIUM);
        }
    }

    private void drawEnding(Canvas c) {
        float k = Math.min(1f, phaseTime / 0.5f);
        c.drawColor(Theme.alpha(Theme.NAVY_DEEP, 0.55f * k));
        String title;
        int color;
        if (world.state == GameWorld.WON) { title = endWin; color = Theme.GOLD; }
        else if (world.cause == GameWorld.CAUSE_SHADOW) { title = endCaught; color = Theme.PURPLE_LIGHT; }
        else { title = endObstacle; color = Theme.PURPLE_LIGHT; }
        float sc = 0.8f + 0.2f * k;
        Ui.textFit(c, title, w / 2f, h * 0.45f, 74f * u * sc, w - 100f * u, Theme.alpha(color, k), Paint.Align.CENTER, Ui.DISPLAY);
    }

    private void drawPause(Canvas c) {
        c.drawColor(Theme.alpha(Theme.NAVY_DEEP, 0.82f));
        float cx = w / 2f;
        float pw = Math.min(w - 100f * u, 820f * u);
        Ui.panel(c, cx - pw / 2f, resumeBtn.rect.top - 230f * u, cx + pw / 2f, soundBtn.rect.bottom + 60f * u, 48f * u,
                Theme.alpha(Theme.PANEL, 0.96f), Theme.alpha(Theme.PURPLE, 0.7f), 3f * u);
        Ui.textFit(c, pausedTitle, cx, resumeBtn.rect.top - 120f * u, 64f * u, pw - 80f * u, Theme.WHITE, Paint.Align.CENTER, Ui.DISPLAY);
        int sc = Math.max(0, lastScore), ver = L10n.version();
        if (sc != pauseInfoScore || lastSec != pauseInfoSec || ver != pauseInfoVer) {
            pauseInfoScore = sc;
            pauseInfoSec = lastSec;
            pauseInfoVer = ver;
            pauseInfo = L10n.p("play.pause.score", sc, timeText);
        }
        Ui.textFit(c, pauseInfo, cx, resumeBtn.rect.top - 55f * u, 34f * u, pw - 80f * u, Theme.MUTED, Paint.Align.CENTER, Ui.MEDIUM);
    }
}
