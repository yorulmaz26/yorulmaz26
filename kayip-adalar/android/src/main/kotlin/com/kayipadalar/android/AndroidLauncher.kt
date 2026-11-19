package com.kayipadalar.android

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.kayipadalar.game.KayipAdalarGame
import com.kayipadalar.game.Platform
import com.kayipadalar.logic.meta.TimeSample
import com.kayipadalar.logic.meta.TimeSource

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
            numSamples = 2
        }
        initialize(KayipAdalarGame(AndroidPlatform(this)), config)
    }
}

/** Android'e özgü güvenilir zaman kaynağı: elapsedRealtime + önyükleme sayacı. */
class AndroidPlatform(private val activity: AndroidApplication) : Platform {
    private fun bootCount(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            runCatching { Settings.Global.getInt(activity.contentResolver, Settings.Global.BOOT_COUNT).toLong() }.getOrDefault(-2L)
        else {
            // Eski sürümlerde önyükleme anı tahmini kullanılır (dakika hassasiyetinde).
            (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000L
        }

    override val timeSource = TimeSource { TimeSample(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount()) }
    override fun vibrate(ms: Int) { Gdx.input.vibrate(ms) }
    override val name = "android"
}
