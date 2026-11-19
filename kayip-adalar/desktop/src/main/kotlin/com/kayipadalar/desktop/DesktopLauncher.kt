package com.kayipadalar.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.kayipadalar.game.DesktopPlatform
import com.kayipadalar.game.KayipAdalarGame

/** Geliştirme için masaüstü başlatıcı (9:16 dikey pencere). */
fun main(args: Array<String>) {
    val cfg = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Kayıp Adalar – Hareketli Krallık")
        setWindowedMode(450, 800)
        setForegroundFPS(60)
        useVsync(true)
    }
    val shots = args.indexOf("--shots").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    Lwjgl3Application(if (shots != null) ScreenshotDriver(shots) else KayipAdalarGame(DesktopPlatform()), cfg)
}
