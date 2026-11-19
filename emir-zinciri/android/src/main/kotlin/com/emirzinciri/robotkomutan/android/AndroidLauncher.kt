package com.emirzinciri.robotkomutan.android

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.emirzinciri.robotkomutan.EmirZinciriGame
import com.emirzinciri.robotkomutan.logic.progress.GameClock
import com.emirzinciri.robotkomutan.platform.Platform
import java.io.File

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
            numSamples = 2
        }
        initialize(EmirZinciriGame(AndroidPlatform(this)), config)
    }
}

/** Android saat kaynağı: elapsedRealtime derin uykuyu sayar ve kullanıcı tarafından değiştirilemez. */
class AndroidPlatform(private val app: AndroidApplication) : Platform {
    override val name = "android"

    override val clock = object : GameClock {
        override fun wallMs() = System.currentTimeMillis()
        override fun monotonicMs() = SystemClock.elapsedRealtime()
        override fun bootId(): String = runCatching {
            Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT).toString()
        }.getOrElse {
            // Açılış sayacı okunamazsa açılış zamanı tahmini kullanılır (dakika hassasiyetinde).
            "est-" + ((System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000)
        }
    }

    override fun saveDir(): File = File(app.filesDir, "save")

    override fun vibrate(ms: Int) {
        val v = app.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
