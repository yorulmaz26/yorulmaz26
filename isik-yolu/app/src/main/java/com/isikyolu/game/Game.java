package com.isikyolu.game;

import android.content.Context;
import android.content.SharedPreferences;

import com.isikyolu.engine.LevelPack;
import com.isikyolu.engine.Lives;
import com.isikyolu.engine.Progress;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Uygulama genelindeki servisler: bölümler, ilerleme kaydı, ses, titreşim. */
public final class Game {
    private static final String PREFS = "isik_yolu";
    private static final String KEY = "progress";
    /** Kayıt imzası ayrı anahtarda: kayıt düz JSON kalır, eski sürümler de okuyabilir. */
    private static final String KEY_SIG = "progress_sig";

    private static LevelPack levels;
    private static Progress progress = new Progress();
    private static SharedPreferences prefs;
    private static Audio audio;
    private static Haptics haptics;

    private Game() {}

    private static Context appCtx;

    static synchronized void init(Context c) {
        Context app = c.getApplicationContext();
        if (appCtx != app) { // yeni süreç/uygulama örneği: servisleri yeniden kur
            appCtx = app;
            prefs = null; audio = null; haptics = null;
        }
        if (prefs == null) {
            prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY, null);
            progress = json != null && json.contains("\n#sig:") ? Progress.deserialize(json) // geliştirme sürümü biçimi
                    : Progress.load(json, prefs.getString(KEY_SIG, null));
            saveTamperNotice = progress.saveTampered; // kayıt elle değiştirilmiş: bir kez bildirilir, kayıt yeniden imzalanır
            clockSource = app;
            // uygulama bir bölümün ortasında kapatıldıysa o denemenin canı geri gelmez
            if (progress.attemptLevel != 0) {
                abandonedShield = com.isikyolu.engine.Economy.loseAttempt(progress, clock());
                abandonedAttempt = true;
            }
            Lives.update(progress, clock());
            save();
        }
        if (audio == null) audio = new Audio(app);
        if (haptics == null) haptics = new Haptics(app);
    }

    /** Bölümleri assets/levels.json dosyasından yükler (arka planda çağrılır). */
    static synchronized LevelPack loadLevels(Context c) throws IOException {
        if (levels == null) {
            try (InputStream in = c.getAssets().open("levels.json")) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                levels = LevelPack.parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        return levels;
    }

    private static Context clockSource;
    private static boolean abandonedAttempt, abandonedShield, saveTamperNotice;

    /** Kayıt dosyası değiştirilmiş mi (bir kez bildirilir). */
    public static boolean takeSaveTamperNotice() { boolean b = saveTamperNotice; saveTamperNotice = false; return b; }

    /** Son sorgudan beri canlar zamanla yenilenip dolduysa true (bildirim için; ödül ve satın almalar sayılmaz). */
    public static boolean takeLivesRefilled() {
        Lives.update(progress, clock());
        boolean full = progress.regenGained > 0 && progress.lives >= Lives.MAX;
        progress.regenGained = 0;
        return full;
    }

    /** Yarım kalan denemede can harcandı mı (bir kez bildirilir). */
    public static boolean takeAbandonedNotice() { boolean b = abandonedAttempt; abandonedAttempt = false; return b; }

    /** Yarım kalan denemede Can Kalkanı kullanıldı mı. */
    public static boolean abandonedShieldUsed() { return abandonedShield; }

    /** Can sistemi için saatler: monoton saat, açılış sayacı ve (varsa) ağ saati. */
    public static Lives.Clock clock() {
        int boot = -1;
        try {
            if (clockSource != null) boot = android.provider.Settings.Global.getInt(clockSource.getContentResolver(), android.provider.Settings.Global.BOOT_COUNT);
        } catch (Exception ignored) { }
        long net = -1;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            try { net = android.os.SystemClock.currentNetworkTimeClock().millis(); } catch (Exception ignored) { }
        }
        return new Lives.Clock(System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime(), boot, net);
    }

    /** Canları güncel süreye göre yeniler; saat değişikliği algılanırsa true. */
    public static boolean refreshLives() {
        boolean tamper = Lives.update(progress, clock());
        save();
        return tamper;
    }

    public static long zoneOffset() { return java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()); }

    /** Güvenilir şimdiki zaman: telefon saatini ileri/geri almak etkilemez (bkz. Lives.trustedWall). */
    public static long trustedNow() {
        Lives.Clock c = clock();
        Lives.update(progress, c);
        return Lives.trustedWall(progress, c);
    }

    /** Market ve günlük bulmaca günü (saat hilesine karşı güvenilir gün). */
    public static int shopDay() { return com.isikyolu.engine.Economy.monotonicDay(progress, trustedNow(), zoneOffset()); }

    /** Bir sonraki market/günlük bulmaca gününe kalan süre. */
    public static long msUntilNextDay() { return com.isikyolu.engine.Economy.msUntilNextMonotonicDay(progress, trustedNow(), zoneOffset()); }

    /** Günlük bulmacanın "bugün"ü. */
    public static int today() { return shopDay(); }

    public static LevelPack levels() { return levels; }
    public static Progress progress() { return progress; }
    public static Audio audio() { return audio; }
    public static Haptics haptics() { return haptics; }

    /** Otomatik kayıt: her değişiklikten sonra çağrılır. */
    public static void save() {
        if (prefs == null) return;
        String json = progress.serialize();
        prefs.edit().putString(KEY, json).putString(KEY_SIG, Progress.signature(json)).apply();
    }
}
