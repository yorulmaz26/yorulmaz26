package com.golgen.oyun.platform;

import android.content.Context;
import android.content.SharedPreferences;

import com.golgen.oyun.core.KeyValueStore;

/** SharedPreferences tabanlı yerel kayıt. İnternet gerektirmez. */
public final class PrefsStore implements KeyValueStore {
    private final SharedPreferences prefs;
    private SharedPreferences.Editor editor;

    public PrefsStore(Context ctx) {
        prefs = ctx.getSharedPreferences("golgen_kayit", Context.MODE_PRIVATE);
    }

    private SharedPreferences.Editor ed() {
        if (editor == null) editor = prefs.edit();
        return editor;
    }

    @Override public int getInt(String key, int def) { return prefs.getInt(key, def); }
    @Override public void putInt(String key, int value) { ed().putInt(key, value); }
    @Override public String getString(String key, String def) { return prefs.getString(key, def); }
    @Override public void putString(String key, String value) { ed().putString(key, value); }

    @Override public void apply() {
        if (editor != null) {
            editor.apply();
            editor = null;
        }
    }
}
