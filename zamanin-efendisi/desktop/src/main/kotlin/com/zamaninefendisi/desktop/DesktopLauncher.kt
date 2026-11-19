package com.zamaninefendisi.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.zamaninefendisi.game.ZamaninEfendisiGame

/** Geliştirme için masaüstü başlatıcısı (9:16 pencere). */
fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Zamanın Efendisi: Üç Çağ")
        setWindowedMode(System.getProperty("zaman.w")?.toIntOrNull() ?: 450, System.getProperty("zaman.h")?.toIntOrNull() ?: 800)
        useVsync(true)
        setForegroundFPS(60)
    }
    Lwjgl3Application(ZamaninEfendisiGame(devStart = System.getProperty("zaman.start")), config)
}
