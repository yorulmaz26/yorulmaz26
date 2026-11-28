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

import com.golgen.oyun.core.L10n;
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
public final class GameView extends View implements Choreographer.FrameCallback, com.golgen.oyun.platform.Ads.Listener {
    public final Activity activity;
    public final boolean debuggable;
    public final Progress progress = new Progress();
    public final PrefsStore store;
    public final SoundSystem sound;
    public final Haptics haptics;
    public final com.golgen.oyun.core.TimeSource clock;
    /** Reklam katmanı (MainActivity kurar; test/çevrimdışı durumda reklamsız). */
    public com.golgen.oyun.platform.Ads ads = new com.golgen.oyun.platform.NoAds();
    public final com.golgen.oyun.core.AdPolicy adPolicy = new com.golgen.oyun.core.AdPolicy();
    private int bannerInset;
    private int lastEnergySec = -1;
    private String energyText = "";

    public final com.golgen.oyun.screens.LogoScreen logo;
    public final SplashScreen splash;
    public final MenuScreen menu;
    public final PlayScreen play;
    public final GameOverScreen gameOver;
    public final SkinsScreen skins;
    public final MissionsScreen missions;
    public final RecordsScreen records;
    public final SettingsScreen settings;
    public final TutorialScreen tutorial;
    public final com.golgen.oyun.screens.LivesDialog livesDialog;
    private final Screen[] allScreens;
    /** Can yenilendiğinde 1'e çıkar ve söner (can göstergelerindeki kısa animasyon). */
    public float livesPulse;
    private int livesSeen = -1, regenSeen;
    private float livesCheckT;

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
        // Saat kaynağı, güvenilir güne (epochDay) ve canlara erişen her şeyden ÖNCE kurulmalı
        clock = new com.golgen.oyun.platform.SystemTime(activity.getContentResolver());
        progress.energy.tick(clock); // ilk kurulumda tam can
        progress.ensureDaily(epochDay());
        Ui.debugOverflow = debuggable;
        initLanguage();
        save();
        sound = new SoundSystem(activity);
        haptics = new Haptics(activity);
        applySettings();

        logo = new com.golgen.oyun.screens.LogoScreen(this);
        splash = new SplashScreen(this);
        menu = new MenuScreen(this);
        play = new PlayScreen(this);
        gameOver = new GameOverScreen(this);
        skins = new SkinsScreen(this);
        missions = new MissionsScreen(this);
        records = new RecordsScreen(this);
        settings = new SettingsScreen(this);
        tutorial = new TutorialScreen(this);
        livesDialog = new com.golgen.oyun.screens.LivesDialog(this);
        allScreens = new Screen[]{logo, splash, menu, play, gameOver, skins, missions, records, settings, tutorial, livesDialog};
        for (Screen s : allScreens) s.localize();
        livesSeen = progress.energy.lives;
        if (progress.saveTampered) toast(L10n.t("save.tampered"), Icons.LOCK);
        current = logo;
        current.enter();
        android.util.Log.i("GolgenUI", "ekran=LogoScreen");
    }

    public void applySettings() {
        sound.soundEnabled = progress.sound;
        sound.setMusicEnabled(progress.music);
        haptics.enabled = progress.vibration;
    }

    public void save() {
        progress.save(store);
    }

    // ---------------- Dil ----------------
    /** İlk açılışta sistem dilini algılar (desteklenmiyorsa İngilizce) ve tercihi kalıcı kaydeder. */
    private void initLanguage() {
        String lang = progress.language;
        if (!L10n.isSupported(lang)) {
            lang = L10n.pickForSystem(java.util.Locale.getDefault().getLanguage());
            progress.language = lang;
        }
        loadLanguage(L10n.DEFAULT);
        loadLanguage(lang);
        L10n.use(lang);
    }

    private void loadLanguage(String code) {
        if (L10n.isLoaded(code)) return;
        try (java.io.InputStream in = activity.getAssets().open("localization/" + code + ".json")) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(64 * 1024);
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            L10n.load(code, new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException | RuntimeException e) {
            android.util.Log.e("GolgenL10n", "dil dosyası okunamadı: " + code, e);
        }
    }

    /** Oyun içinden dil değişimi: yeniden başlatma gerekmez, ilerleme etkilenmez. */
    public void setLanguage(String code) {
        if (!L10n.isSupported(code)) return;
        loadLanguage(code);
        L10n.use(code);
        progress.language = code;
        save();
        for (Screen s : allScreens) s.localize();
        layoutScreen(current);
        layoutOverlay();
        android.util.Log.i("GolgenUI", "dil=" + code);
    }

    // ---------------- Canlar ----------------
    /** Can sayısı açık bir işlemle (video, elmas, bölüm) değiştiğinde: animasyon var, "yenilendi" bildirimi yok. */
    public void noteLivesChanged() {
        livesSeen = lives();
        livesPulse = 1f;
    }

    private void watchLives(float dt) {
        if (livesPulse > 0f) livesPulse = Math.max(0f, livesPulse - dt * 1.6f);
        livesCheckT += dt;
        if (livesCheckT < 0.5f) return;
        livesCheckT = 0f;
        int l = lives();
        int regen = progress.energy.regenTotal;
        if (regen > regenSeen) {                      // zamanla yenilendi: kısa animasyon + bildirim
            livesPulse = 1f;
            sound.play(com.golgen.oyun.core.Synth.DIAMOND, 0.6f, 1.2f);
            toast(l >= com.golgen.oyun.core.Energy.MAX ? L10n.t("lives.full_toast") : L10n.t("lives.regen_toast"), Icons.HEART);
        } else if (livesSeen >= 0 && l > livesSeen) {
            livesPulse = 1f;                          // video / elmas / bölüm kazanımı
        }
        regenSeen = regen;
        livesSeen = l;
    }

    public SkinDef skin() {
        return Skins.ALL[progress.selectedSkin];
    }

    /** Günlük ödüller için güvenilir gün (tarih değiştirerek hile yapılamaz; bkz. Progress.trustedDay). */
    public int epochDay() {
        long now = clock.elapsedMs();
        if (cachedDay == Integer.MIN_VALUE || now - dayCheckedAt >= 1000L || now < dayCheckedAt) {
            cachedDay = progress.trustedDay(clock, java.time.ZoneId.systemDefault());
            dayCheckedAt = now;
        }
        return cachedDay;
    }

    private int cachedDay = Integer.MIN_VALUE;
    private long dayCheckedAt;

    /** Gece yarısına kalan süre (dakika). */
    public long minutesToMidnight() {
        progress.energy.tick(clock);
        LocalDateTime now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(progress.energy.lastWall), java.time.ZoneId.systemDefault());
        LocalDate day = LocalDate.ofEpochDay(epochDay());
        long m = now.until(day.plusDays(1).atStartOfDay(), ChronoUnit.MINUTES);
        return Math.max(0, m);
    }

    // ---------------- Ekran geçişleri ----------------
    public void go(Screen next) {
        // Aynı ekrana tekrar geçiş de (ör. oyundan yeni bölüme) yeniden başlatır.
        pending = next;
        if (fadeDir != 1) fadeDir = 1;
    }

    public void goMenu() { go(menu); }

    public void startEndless() {
        play.setup(RunResult.MODE_ENDLESS, 0);
        go(play);
    }

    /** Bölüm başlatır: 1 can harcanır (kazanınca geri verilir). Can yoksa uyarır. */
    public boolean startMission(int index) {
        if (index < 0 || index >= com.golgen.oyun.core.Missions.ALL.length) return false;
        if (!progress.energy.consume(clock)) {
            if (current != missions && current != play) { missions.showTab(MissionsScreen.TAB_LEVELS); go(missions); }
            livesDialog.show(index);
            return false;
        }
        livesSeen = progress.energy.lives;
        progress.saveNow(store);   // zorla kapatmaya karşı can harcaması hemen diske yazılır
        play.setup(RunResult.MODE_MISSION, index);
        go(play);
        return true;
    }

    public int lives() {
        progress.energy.tick(clock);
        return progress.energy.lives;
    }

    // ---------------- Reklamlar ----------------
    /** Bildirim perdesi/sistem paneli açılınca (onPause gelmeden odak kaybı) oyun duraklatılır. */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) current.onAppPause();
    }

    @Override
    public void onBannerHeightChanged(int px) {
        if (px == bannerInset) return;
        bannerInset = px;
        layoutScreen(current);
    }

    /**
     * Oyun sonu ekranından çıkarken (doğal mola) gerekirse geçiş reklamı gösterir, sonra işlemi yapar.
     * Sıklık AdPolicy ile sınırlıdır; reklam hazır değilse işlem hemen yapılır.
     */
    public void afterRunBreak(Runnable action) {
        long now = clock.elapsedMs();
        if (adPolicy.shouldShowInterstitial(progress.gamesPlayed, now) && ads.isInterstitialReady()) {
            adPolicy.onInterstitialShown(now);
            ads.showInterstitial(action);
        } else {
            action.run();
        }
    }

    /** Yeni reklam katmanı bağlandığında o anki ekranın banner isteğini ve alt boşluğu uygular. */
    public void attachAds(com.golgen.oyun.platform.Ads a) {
        ads = a;
        ads.setBottomMargin((int) safeB);
        ads.setBannerWanted(current.wantsBanner());
    }

    /** Debug: reklamları testte kapat/aç (kalıcı). */
    public boolean adsDisabledForTest() {
        return debuggable && store.getInt("testReklamsiz", 0) == 1;
    }

    /** Sonraki cana kalan süre "dd:ss" (saniyede bir yeniden üretilir; dolu ise "Canlar dolu"). */
    public String energyTimer() {
        long ms = progress.energy.msToNext(clock);
        int sec = (int) ((ms + 999) / 1000);
        if (sec != lastEnergySec || lastEnergyLang != L10n.version()) {
            lastEnergySec = sec;
            lastEnergyLang = L10n.version();
            energyText = sec <= 0 ? L10n.t("lives.full")
                    : String.format(java.util.Locale.ROOT, "%02d:%02d", sec / 60, sec % 60);
        }
        return energyText;
    }

    private int lastEnergyLang = -1;
    private int lastMissing;

    public Screen current() { return current; }

    private void switchNow(Screen next) {
        current.exit();
        current = next;
        ads.setBannerWanted(current.wantsBanner());
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
        if (intent.getIntExtra("demo", 0) == 1) debugDemoProgress();
        String cekim = intent.getStringExtra("cekim");
        if (cekim != null) {
            try {
                Class.forName("com.golgen.oyun.debug.Capture").getMethod("run", GameView.class, String.class).invoke(null, this, cekim);
            } catch (Exception ex) {
                android.util.Log.e("GolgenCekim", "çekim modu yok", ex);
            }
            return;
        }
        if (intent.hasExtra("reklamsiz")) {
            boolean off = intent.getBooleanExtra("reklamsiz", false);
            store.putInt("testReklamsiz", off ? 1 : 0);
            store.apply();
            if (activity instanceof MainActivity) ((MainActivity) activity).setupAds(!off);
        }
        String dil = intent.getStringExtra("dil");
        if (dil != null) setLanguage(dil);
        if (intent.hasExtra("canlar")) {           // test: can sayısını ayarla (0..MAX)
            progress.energy.tick(clock);
            progress.energy.lives = Math.max(0, Math.min(com.golgen.oyun.core.Energy.MAX, intent.getIntExtra("canlar", 0)));
            progress.energy.progressMs = 0;
            noteLivesChanged();
            save();
        }
        String reklam = intent.getStringExtra("reklam");
        if ("gecis".equals(reklam)) ads.showInterstitial(null);
        else if ("video".equals(reklam)) ads.showRewarded(() -> android.util.Log.i("GolgenAds", "test ödülü verildi"), null);
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
                    progress.energy.refill(clock); // test: can sınırına takılmasın
                    try { startMission(Integer.parseInt(e.substring(6))); } catch (NumberFormatException ignored) { }
                }
                break;
        }
    }

    // ---------------- Debug çekim modu kancaları (yalnızca debug build kullanır) ----------------
    private volatile boolean capturing;

    /** Çekim sırasında oyun döngüsü durur ve ekrana çizim askıya alınır (durum tek iş parçacığından değişir). */
    public void debugSetCapturing(boolean on) {
        if (!debuggable) return;
        capturing = on;
        if (on) {
            running = false;
            Choreographer.getInstance().removeFrameCallback(this);
        } else {
            onResume();
        }
    }

    public void debugStep(float dt) { step(dt); }

    public void debugDraw(Canvas c) {
        current.draw(c);
        livesDialog.draw(c);
        drawToast(c);
        if (fade > 0f) c.drawColor(Theme.alpha(Theme.NAVY_DEEP, fade));
    }

    public boolean debugCurrentIsPlay() { return current == play; }

    public void debugStartEndless() {
        play.setup(RunResult.MODE_ENDLESS, 0);
        switchNow(play);
        fade = 0f; fadeDir = 0; pending = null;
    }

    public void debugStartMission(int index) {
        progress.energy.refill(clock);
        progress.missionsCompleted = Math.max(progress.missionsCompleted, index);
        play.setup(RunResult.MODE_MISSION, index);
        switchNow(play);
        fade = 0f; fadeDir = 0; pending = null;
    }

    /** Çekim için ekran aç: menu (ödül penceresi kapalı), gunluk_odul, karakter, gorevler, gunluk, basarilar, rekorlar, ayarlar, canyok. */
    public void debugShow(String name) {
        progress.tutorialDone = true;
        Screen s = menu;
        switch (name) {
            case "menu": progress.lastLoginClaimDay = epochDay(); break;
            case "gunluk_odul": progress.lastLoginClaimDay = epochDay() - 1; break;
            case "karakter": s = skins; break;
            case "gorevler": missions.showTab(MissionsScreen.TAB_LEVELS); s = missions; break;
            case "gunluk": missions.showTab(MissionsScreen.TAB_DAILY); s = missions; break;
            case "basarilar": missions.showTab(MissionsScreen.TAB_ACH); s = missions; break;
            case "rekorlar": s = records; break;
            case "ayarlar": s = settings; break;
            case "ogretici": tutorial.setFromSettings(true); s = tutorial; break;
            case "canyok": progress.energy.lives = 0; progress.energy.progressMs = 0; s = missions; break;
            default: break;
        }
        switchNow(s);
        fade = 0f; fadeDir = 0; pending = null;
        if ("canyok".equals(name)) livesDialog.show(Math.min(progress.missionsCompleted, com.golgen.oyun.core.Missions.ALL.length - 1));
    }

    /** Mağaza görselleri için örnek ilerleme: açık bölümler, taçlar, elmas, birkaç karakter, rekorlar. */
    public void debugDemoProgress() {
        progress.tutorialDone = true;
        progress.missionsCompleted = Math.max(progress.missionsCompleted, 37);
        for (int i = 0; i < 37; i++) progress.missionStars[i] = (byte) (i % 5 == 4 ? 2 : 3);
        progress.diamonds = Math.max(progress.diamonds, 1240);
        progress.unlockedSkins |= 0b1011;
        progress.level = Math.max(progress.level, 12);
        progress.bestScore = Math.max(progress.bestScore, 18450);
        int[] top = {18450, 15210, 12980, 11040, 9875, 8120, 7340, 6010, 5225, 4180};
        for (int i = 0; i < top.length; i++) progress.topScores[i] = Math.max(progress.topScores[i], top[i]);
        progress.gamesPlayed = Math.max(progress.gamesPlayed, 48);
        progress.loginStreak = Math.max(progress.loginStreak, 4);
        save();
    }

    /** Üst katman (can penceresi) yerleşimi ve test hedefleri. */
    public void layoutOverlay() {
        if (getWidth() > 0) {
            livesDialog.resize(getWidth(), getHeight(), u, safeL, safeT, safeR, safeB);
            if (livesDialog.isOpen()) livesDialog.dumpTargets();
        }
    }

    private void layoutScreen(Screen s) {
        if (getWidth() > 0) {
            int inset = s.wantsBanner() ? bannerInset : 0; // banner yalnızca menülerde; altındaki butonlar yukarı kayar
            s.resize(getWidth(), getHeight(), u, safeL, safeT, safeR, safeB + inset);
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
        float maxW = bw - bh * 1.3f;
        String s = toastText[0];
        if (s != toastWrapSrc || maxW != toastWrapW) {      // uzun çeviriler iki satıra kaydırılır (bildirim başına bir kez)
            toastWrapSrc = s;
            toastWrapW = maxW;
            toastLines = Ui.measure(s, 38f * u, Ui.BOLD) * 0.85f <= maxW ? null : Ui.wrapFit(s, 34f * u, maxW, 2, Ui.BOLD, toastFit);
        }
        if (toastLines == null || toastLines.length != 2) {   // tek satır (veya aşırı uzunsa küçültülmüş tek satır)
            Ui.textFit(c, s, l + bh, Ui.centerY(top + bh / 2f, 38f * u), 38f * u, maxW,
                    Theme.alpha(Theme.WHITE, ease), Paint.Align.LEFT, Ui.BOLD);
        } else {
            float ts = toastFit[0], lh = ts * 1.15f;
            for (int i = 0; i < 2; i++) {
                Ui.textFit(c, toastLines[i], l + bh, Ui.centerY(top + bh / 2f + (i - 0.5f) * lh, ts), ts, maxW,
                        Theme.alpha(Theme.WHITE, ease), Paint.Align.LEFT, Ui.BOLD);
            }
        }
    }

    private String toastWrapSrc;
    private float toastWrapW;
    private String[] toastLines;
    private final float[] toastFit = new float[1];

    // ---------------- Yaşam döngüsü ----------------
    public void onResume() {
        progress.energy.tick(clock);
        ads.onResume();
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
        ads.onPause();
        sound.onPause();
        save();
    }

    public void release() {
        ads.destroy();
        sound.release();
    }

    /** Geri tuşu: ekran ele almazsa uygulamadan çıkılır. */
    public void handleBack() {
        if (fadeDir != 0) return;
        if (livesDialog.isOpen()) { livesDialog.onBack(); return; }
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
        livesDialog.update(dt);
        watchLives(dt);
        updateToasts(dt);
        fpsFrames++;
        fpsTime += dt;
        if (fpsTime >= 5f) {
            lastFps = fpsFrames / fpsTime;
            android.util.Log.i("GolgenFPS", String.format(java.util.Locale.US, "fps=%.1f ekran=%s", lastFps, current.getClass().getSimpleName()));
            if (debuggable) {
                java.util.Set<String> miss = L10n.missingSeen();
                if (miss.size() != lastMissing) { lastMissing = miss.size(); android.util.Log.w("GolgenL10n", "eksik=" + miss); }
            }
            fpsFrames = 0;
            fpsTime = 0f;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (capturing) { c.drawColor(Theme.NAVY_DEEP); return; }
        current.draw(c);
        livesDialog.draw(c);
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
        if (e.getActionMasked() == MotionEvent.ACTION_UP) performClick();
        if (livesDialog.isOpen()) return livesDialog.onTouch(e);
        return current.onTouch(e);
    }

    @Override
    public boolean performClick() {
        // Erişilebilirlik servisleri için tıklama olayı; oyun girdisi onTouchEvent'te işlenir.
        return super.performClick();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        u = Math.min(w / 1080f, h / 1920f);
        layoutScreen(current);
        layoutOverlay();
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
        ads.setBottomMargin(b);
        layoutScreen(current);
        layoutOverlay();
        return super.onApplyWindowInsets(in);
    }
}
