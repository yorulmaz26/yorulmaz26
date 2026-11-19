package com.yuruyensehir.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.yuruyensehir.game.YuruyenSehirGame

/**
 * Geliştirme için masaüstü başlatıcı (9:16 pencere).
 * `-Dys.shots=<klasör>` verilirse [ScreenshotScript] ekran görüntüleri alıp çıkar.
 */
fun main() {
    val shots = System.getProperty("ys.shots")
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Yürüyen Şehir: Göçebe Medeniyet")
        setWindowedMode(450, 800)
        setForegroundFPS(60)
        useVsync(true)
        setBackBufferConfig(8, 8, 8, 8, 16, 0, 4)
    }
    Lwjgl3Application(YuruyenSehirGame(shots?.let { ScreenshotScript(it) }), config)
}
