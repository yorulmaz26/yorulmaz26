package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/** Cihaz ayarları (kayıttan ayrı tutulur). */
class Settings {
    private val prefs: Preferences = Gdx.app.getPreferences("yeralti-ayarlar")

    var lowQuality: Boolean
        get() = prefs.getBoolean("lowQuality", false)
        set(v) { prefs.putBoolean("lowQuality", v); prefs.flush() }

    var soundOn: Boolean
        get() = prefs.getBoolean("soundOn", true)
        set(v) { prefs.putBoolean("soundOn", v); prefs.flush() }

    var volume: Float
        get() = prefs.getFloat("volume", 0.75f)
        set(v) { prefs.putFloat("volume", v.coerceIn(0f, 1f)); prefs.flush() }

    var showHints: Boolean
        get() = prefs.getBoolean("showHints", true)
        set(v) { prefs.putBoolean("showHints", v); prefs.flush() }
}
