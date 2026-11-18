package com.golgen.oyun;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.os.Build;
import android.view.Choreographer;
import android.view.DisplayCutout;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

import com.golgen.oyun.core.Progress;
import com.golgen.oyun.core.RunResult;
import com.golgen.oyun.core.Skins;
import com.golgen.oyun.core.SkinDef;
import com.golgen.oyun.platform.Haptics;
import com.golgen.oyun.platform.PrefsStore;
import com.golgen.oyun.platform.SoundSystem;
import com.golgen.oyun.screens.GameOverScreen;
import com.golgen.oyun.screens.MenuScreen;
import com.golgen.oyun.screens.MissionsScreen;
import com.golgen.oyun.screens.PlayScreen;
import com.golgen.oyun.screens.RecordsScreen;
import com.golgen.oyun.screens.SettingsScreen;
import com.golgen.oyun.screens.SkinsScreen;
import com.golgen.oyun.screens.SplashScreen;
import com.golgen.oyun.screens.TutorialScreen;
import com.golgen.oyun.ui.Icons;
import com.golgen.oyun.ui.Screen;
import com.golgen.oyun.ui.Theme;
import com.golgen.oyun.ui.Ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Oyunun tek görünümü: Choreographer ile ekran yenilemesine senkron (60 FPS hedefli) döngü,
 * ekran yönetimi, geçiş animasyonları, bildirimler ve paylaşılan servisler.
 */
public final class GameView extends View implements Choreographer.FrameCallback {
    public final Activity activity;
    public final boolean debuggable;
    public final Progress progress = new Progress();
    public final PrefsStore store;
    public final SoundSystem sound;
    public final Haptics haptics;

    public final SplashScreen splash;
    public final MenuScreen menu;
    public final PlayScreen play;
    public final GameOverScreen gameOver;
    public final SkinsScreen skins;
    public final MissionsScreen missions;
    public final RecordsScreen records;
    public final SettingsScreen settings;
    public final TutorialScreen tutorial;

    private Screen current;
    private Screen pending;
    private float fade;          // 0 = görünür, 1 = tamamen kararmış
    private int fadeDir;         // 1: kararıyor, -1: açılıyor, 0: sabit
    private static final float FADE_SPEED = 6.5f;

    private boolean running;
    private long lastNanos;
    private float u = 1f;
    private float safeL, safeT, safeR, safeB;

    // Bildirimler (başarı, günlük görev vb.)
    private static final int TOAST_MAX = 6;
    private final String[] toastText = new String[TOAST_MAX];
    private final int[] toastIcon = new int[TOAST_MAX];
    private int toastCount;
    private float toastTime;

    // FPS ölçümü (logcat'e yazılır; test betiği okur)
    private int fpsFrames;
    private float fpsTime;
    public float lastFps;

    public GameView(Activity activity) {
        super(activity);
        this.activity = activity;
        debuggable = (activity.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        setFocusable(true);
        store = new PrefsStore(activity);
        progress.load(store);
        progress.ensureDaily(epochDay());
        sound = new SoundSystem(activity);
        haptics = new Haptics(activity);
        applySettings();

        splash = new SplashScreen(this);
        menu = new MenuScreen(this);
        play = new PlayScreen(this);
        gameOver = new GameOverScreen(this);
        skins = new SkinsScreen(this);
        missions = new MissionsScreen(this);
        records = new RecordsScreen(this);
        settings = new SettingsScreen(this);
        tutorial = new TutorialScreen(this);
        current = splash;
        current.enter();
        android.util.Log.i("GolgenUI", "ekran=SplashScreen");
    }

    public void applySettings() {
        sound.soundEnabled = progress.sound;
        sound.setMusicEnabled(progress.music);
        haptics.enabled = progress.vibration;
    }

    public void save() {
        progress.save(store);
    }

    public SkinDef skin() {
        return Skins.ALL[progress.selectedSkin];
    }

    public int epochDay() {
        return (int) LocalDate.now().toEpochDay();
    }

    /** Gece yarısına kalan süre (dakika). */
    public long minutesToMidnight() {
        LocalDateTime now = LocalDateTime.now();
        return now.until(now.toLocalDate().plusDays(1).atStartOfDay(), ChronoUnit.MINUTES);
    }

    // ---------------- Ekran geçişleri ----------------
    public void go(Screen next) {
        if (next == current && fadeDir == 0) return;
        pending = next;
        fadeDir = 1;
    }

    public void goMenu() { go(menu); }

    public void startEndless() {
        play.setup(RunResult.MODE_ENDLESS, 0);
        go(play);
    }

    public void startMission(int index) {
        play.setup(RunResult.MODE_MISSION, index);
        go(play);
    }

    public Screen current() { return current; }

    private void switchNow(Screen next) {
        current.exit();
        current = next;
        layoutScreen(current);
        current.enter();
        android.util.Log.i("GolgenUI", "ekran=" + current.getClass().getSimpleName());
        current.dumpTargets();
    }

    /** Test betiği için dokunma hedefi (yalnızca debug build). */
    public void debugTarget(String screen, String id, float x, float y) {
        if (debuggable) android.util.Log.i("GolgenUI", "hedef ekran=" + screen + " ad=" + id + " x=" + (int) x + " y=" + (int) y);
    }

    /**
     * Debug build'de test betiğinin doğrudan ekran açabilmesi için intent ekstraları.
     * Örn: adb shell am start -n com.golgen.oyun/.MainActivity --es ekran gorev:0
     */
    public void handleDebugIntent(android.content.Intent intent) {
        if (!debuggable || intent == null) return;
        int extraDiamonds = intent.getIntExtra("elmas", 0);
        if (extraDiamonds > 0) {
            progress.diamonds += extraDiamonds;
            save();
        }
        String e = intent.getStringExtra("ekran");
        if (e == null) return;
        progress.tutorialDone = true;
        switch (e) {
            case "menu": go(menu); break;
            case "oyun": startEndless(); break;
            case "karakter": go(skins); break;
            case "gorevler": missions.showTab(MissionsScreen.TAB_LEVELS); go(missions); break;
            case "gunluk": missions.showTab(MissionsScreen.TAB_DAILY); go(missions); break;
            case "basarilar": missions.showTab(MissionsScreen.TAB_ACH); go(missions); break;
            case "rekorlar": go(records); break;
            case "ayarlar": go(settings); break;
            case "ogretici": tutorial.setFromSettings(true); go(tutorial); break;
            default:
                if (e.startsWith("gorev:")) {
                    try { startMission(Integer.parseInt(e.substring(6))); } catch (NumberFormatException ignored) { }
                }
                break;
        }
    }

    private void layoutScreen(Screen s) {
        if (getWidth() > 0) {
            s.resize(getWidth(), getHeight(), u, safeL, safeT, safeR, safeB);
            s.dumpTargets();
        }
    }

    // ---------------- Bildirimler ----------------
    public void toast(String text, int icon) {
        if (toastCount >= TOAST_MAX) return;
        toastText[toastCount] = text;
        toastIcon[toastCount] = icon;
        if (toastCount == 0) toastTime = 0f;
        toastCount++;
    }

    private void updateToasts(float dt) {
        if (toastCount == 0) return;
        toastTime += dt;
        if (toastTime > 2.6f) {
            System.arraycopy(toastText, 1, toastText, 0, TOAST_MAX - 1);
            System.arraycopy(toastIcon, 1, toastIcon, 0, TOAST_MAX - 1);
            toastCount--;
            toastTime = 0f;
        }
    }

    private void drawToast(Canvas c) {
        if (toastCount == 0) return;
        float t = toastTime;
        float k = t < 0.25f ? t / 0.25f : (t > 2.3f ? Math.max(0f, (2.6f - t) / 0.3f) : 1f);
        float ease = 1f - (1f - k) * (1f - k);
        float w = getWidth();
        float bw = Math.min(w - 80f * u, 900f * u), bh = 120f * u;
        float top = safeT + 24f * u - (1f - ease) * 160f * u;
        float l = (w - bw) / 2f;
        Ui.glow(c, w / 2f, top + bh / 2f, bw * 0.55f, Theme.GOLD, 0.25f * ease);
        Ui.panel(c, l, top, l + bw, top + bh, bh / 2f, Theme.alpha(Theme.PANEL, 0.97f * ease), Theme.alpha(Theme.GOLD, ease), 3f * u);
        Icons.draw(c, toastIcon[0], l + bh * 0.55f, top + bh / 2f, bh * 0.22f, Theme.alpha(Theme.GOLD, ease));
        Ui.textFit(c, toastText[0], l + bh, Ui.centerY(top + bh / 2f, 38f * u), 38f * u, bw - bh * 1.3f,
                Theme.alpha(Theme.WHITE, ease), Paint.Align.LEFT, Ui.BOLD);
    }

    // ---------------- Yaşam döngüsü ----------------
    public void onResume() {
        running = true;
        lastNanos = 0;
        sound.onResume();
        Choreographer.getInstance().removeFrameCallback(this);
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void onPause() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        current.onAppPause();
        sound.onPause();
        save();
    }

    public void release() {
        sound.release();
    }

    /** Geri tuşu: ekran ele almazsa uygulamadan çıkılır. */
    public void handleBack() {
        if (fadeDir != 0) return;
        if (!current.onBack()) activity.finish();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) return;
        float dt = lastNanos == 0 ? 1f / 60f : (frameTimeNanos - lastNanos) / 1_000_000_000f;
        lastNanos = frameTimeNanos;
        if (dt > 0.05f) dt = 0.05f;
        if (dt < 0f) dt = 0f;
        step(dt);
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void step(float dt) {
        if (fadeDir != 0) {
            fade += fadeDir * FADE_SPEED * dt;
            if (fadeDir == 1 && fade >= 1f) {
                fade = 1f;
                if (pending != null) switchNow(pending);
                pending = null;
                fadeDir = -1;
            } else if (fadeDir == -1 && fade <= 0f) {
                fade = 0f;
                fadeDir = 0;
            }
        }
        current.update(dt);
        updateToasts(dt);
        fpsFrames++;
        fpsTime += dt;
        if (fpsTime >= 5f) {
            lastFps = fpsFrames / fpsTime;
            android.util.Log.i("GolgenFPS", String.format(java.util.Locale.US, "fps=%.1f ekran=%s", lastFps, current.getClass().getSimpleName()));
            fpsFrames = 0;
            fpsTime = 0f;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        current.draw(c);
        drawToast(c);
        if (fade > 0f) c.drawColor(Theme.alpha(Theme.NAVY_DEEP, fade));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (debuggable && (e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_UP)) {
            android.util.Log.i("GolgenUI", "dokunma a=" + e.getActionMasked() + " x=" + (int) e.getX() + " y=" + (int) e.getY()
                    + " fade=" + fadeDir + " ekranAdi=" + current.getClass().getSimpleName());
        }
        if (fadeDir == 1) return true; // geçiş sırasında girişi yut
        return current.onTouch(e);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        u = Math.min(w / 1080f, h / 1920f);
        layoutScreen(current);
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets in) {
        int l = 0, t = 0, r = 0, b = 0;
        if (Build.VERSION.SDK_INT >= 30) {
            Insets i = in.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            l = i.left; t = i.top; r = i.right; b = i.bottom;
        } else {
            l = in.getSystemWindowInsetLeft();
            t = in.getSystemWindowInsetTop();
            r = in.getSystemWindowInsetRight();
            b = in.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= 28) {
                DisplayCutout dc = in.getDisplayCutout();
                if (dc != null) {
                    l = Math.max(l, dc.getSafeInsetLeft());
                    t = Math.max(t, dc.getSafeInsetTop());
                    r = Math.max(r, dc.getSafeInsetRight());
                    b = Math.max(b, dc.getSafeInsetBottom());
                }
            }
        }
        safeL = l; safeT = t; safeR = r; safeB = b;
        layoutScreen(current);
        return super.onApplyWindowInsets(in);
    }
}
