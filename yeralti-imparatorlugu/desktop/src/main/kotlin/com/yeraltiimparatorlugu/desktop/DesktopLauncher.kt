package com.yeraltiimparatorlugu.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.yeraltiimparatorlugu.game.YeraltiGame

fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Yeraltı İmparatorluğu: Koloni Savaşları")
        // Telefon oranında (9:16) pencere.
        setWindowedMode(450, 800)
        setForegroundFPS(60)
        useVsync(true)
    }
    Lwjgl3Application(YeraltiGame(), config)
}
