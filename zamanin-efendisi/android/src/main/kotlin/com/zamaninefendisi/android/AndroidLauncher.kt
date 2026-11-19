package com.zamaninefendisi.android

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.zamaninefendisi.game.Platform
import com.zamaninefendisi.game.ZamaninEfendisiGame
import com.zamaninefendisi.logic.save.GameClock

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
            numSamples = 2
        }
        initialize(ZamaninEfendisiGame(AndroidPlatform(this)), config)
    }
}

/**
 * Android saat kaynağı: SystemClock.elapsedRealtime() cihaz açıldığından beri monotonik artar ve
 * kullanıcı saati değiştirdiğinde etkilenmez. Açılış sayacı (API 24+) yeniden başlatmayı ayırt eder.
 */
class AndroidPlatform(private val activity: AndroidApplication) : Platform {
    override val name = "Android"
    override val clock = object : GameClock {
        override fun wallMs() = System.currentTimeMillis()
        override fun elapsedMs() = SystemClock.elapsedRealtime()
        override fun bootId(): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val count = try { Settings.Global.getInt(activity.contentResolver, Settings.Global.BOOT_COUNT, -1) } catch (e: Exception) { -1 }
                if (count >= 0) return "boot-$count"
            }
            // Yedek: açılış anının dakika hassasiyetinde tahmini.
            return "t-" + ((System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000)
        }
    }
}
