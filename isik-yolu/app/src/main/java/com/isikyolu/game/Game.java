package com.isikyolu.game;

import android.content.Context;
import android.content.SharedPreferences;

import com.isikyolu.engine.LevelPack;
import com.isikyolu.engine.Progress;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Uygulama genelindeki servisler: bölümler, ilerleme kaydı, ses, titreşim. */
public final class Game {
    private static final String PREFS = "isik_yolu";
    private static final String KEY = "progress";

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
            progress = Progress.deserialize(prefs.getString(KEY, null));
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

    public static LevelPack levels() { return levels; }
    public static Progress progress() { return progress; }
    public static Audio audio() { return audio; }
    public static Haptics haptics() { return haptics; }

    /** Otomatik kayıt: her değişiklikten sonra çağrılır. */
    public static void save() {
        if (prefs != null) prefs.edit().putString(KEY, progress.serialize()).apply();
    }
}
