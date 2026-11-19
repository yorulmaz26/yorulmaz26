package com.yeraltiimparatorlugu.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.yeraltiimparatorlugu.game.YeraltiGame

fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Yeraltı İmparatorluğu: Koloni Savaşları")
        // Telefon oranında (9:16) pencere.
        val scale = System.getProperty("yi.scale")?.toFloatOrNull() ?: 1f
        setWindowedMode((450 * scale).toInt(), (800 * scale).toInt())
        setForegroundFPS(60)
        useVsync(true)
    }
    val shots = System.getProperty("yi.shots")
    val game = YeraltiGame()
    Lwjgl3Application(if (shots != null) ScreenshotDriver(game, shots) else game, config)
}
