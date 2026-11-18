package com.besaniyelik.dunya.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.besaniyelik.dunya.BesSaniyelikDunya

fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("5 Saniyelik Dünya")
        setWindowedMode(450, 800)
        useVsync(true)
        setForegroundFPS(60)
    }
    Lwjgl3Application(BesSaniyelikDunya(), config)
}
