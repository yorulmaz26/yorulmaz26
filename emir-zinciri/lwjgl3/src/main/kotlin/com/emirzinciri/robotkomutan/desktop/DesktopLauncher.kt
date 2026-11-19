package com.emirzinciri.robotkomutan.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.emirzinciri.robotkomutan.EmirZinciriGame
import com.emirzinciri.robotkomutan.StartupOptions
import com.emirzinciri.robotkomutan.platform.DesktopPlatform
import java.io.File

/**
 * Masaüstü geliştirme başlatıcısı (9:16 pencere).
 * Argümanlar: --level N  --solve (referans çözümü yükle)  --run  --screen levels|settings|book|stats
 *             --shot dosya.png  --after saniye  --exit (görüntüden sonra çık)  --size 540x960  --save klasör
 */
fun main(args: Array<String>) {
    fun arg(name: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val (w, h) = (arg("--size") ?: "450x800").split('x').map { it.toInt() }
    val shot = arg("--shot")
    val after = arg("--after")?.toFloat() ?: 2.5f
    val options = StartupOptions(
        openLevel = arg("--level")?.toInt(),
        applySolution = "--solve" in args,
        autoRun = "--run" in args,
        screenshotPath = shot?.let { File(it).absolutePath },
        screenshotAfter = after,
        openScreen = arg("--screen"),
    )
    val saveDir = File(arg("--save") ?: (System.getProperty("user.home") + "/.emir-zinciri"))
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle(EmirZinciriGame.TITLE)
        setWindowedMode(w, h)
        setResizable(true)
        useVsync(true)
        setForegroundFPS(60)
        setBackBufferConfig(8, 8, 8, 8, 16, 0, 4)
    }
    shot?.let { File(it).delete() }
    val game = object : EmirZinciriGame(DesktopPlatform(saveDir), options) {
        private var t = 0f
        override fun render() {
            super.render()
            t += Gdx.graphics.deltaTime
            if (shot != null && "--exit" in args && (File(shot).exists() || t > after + 20f)) Gdx.app.exit()
        }
    }
    Lwjgl3Application(game, config)
}
