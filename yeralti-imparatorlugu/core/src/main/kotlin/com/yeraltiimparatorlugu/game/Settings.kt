package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/** Cihaz ayarları (kayıttan ayrı tutulur). */
class Settings {
    private val prefs: Preferences = Gdx.app.getPreferences("yeralti-ayarlar")

    var lowQuality: Boolean
        get() = prefs.getBoolean("lowQuality", false)
        set(v) { prefs.putBoolean("lowQuality", v); prefs.flush() }

    var showHints: Boolean
        get() = prefs.getBoolean("showHints", true)
        set(v) { prefs.putBoolean("showHints", v); prefs.flush() }
}
