package com.emirzinciri.robotkomutan

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.emirzinciri.robotkomutan.platform.Platform
import com.emirzinciri.robotkomutan.screen.BaseScreen
import com.emirzinciri.robotkomutan.screen.SplashScreen
import com.emirzinciri.robotkomutan.ui.Assets
import com.emirzinciri.robotkomutan.ui.Ui

/** Oyunun giriş noktası. Ekran yönetimi ve yaşam döngüsü burada. */
open class EmirZinciriGame(private val platform: Platform, private val startup: StartupOptions = StartupOptions()) : Game() {
    lateinit var assets: Assets; private set
    lateinit var ui: Ui; private set
    lateinit var services: Services; private set

    override fun create() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        val density = Gdx.graphics.width / VIRTUAL_W
        assets = Assets(density)
        ui = Ui(assets)
        services = Services(platform)
        assets.soundOn = services.save.settings.sound
        assets.musicOn = services.save.settings.music
        setScreen(SplashScreen(this, startup))
    }

    /** Ekran değiştirir; eski ekran serbest bırakılır. */
    fun go(next: Screen) {
        val old = screen
        setScreen(next)
        old?.dispose()
    }

    override fun pause() {
        (screen as? BaseScreen)?.onPause()
        services.persist()
    }

    override fun resume() {
        services.progression.lives.update()
    }

    override fun dispose() {
        services.persist()
        screen?.dispose()
        assets.dispose()
    }

    companion object {
        const val VIRTUAL_W = 720f
        const val VIRTUAL_H = 1280f
        const val TITLE = "Emir Zinciri: Robot Komutan"
        const val SLOGAN = "Komutları Sen Ver, Geleceği Robotların İnşa Etsin!"
    }
}

/** Geliştirme/test için açılış seçenekleri (masaüstü başlatıcısı doldurur). */
data class StartupOptions(
    val openLevel: Int? = null,
    val applySolution: Boolean = false,
    val autoRun: Boolean = false,
    val screenshotPath: String? = null,
    val screenshotAfter: Float = 2.5f,
    val openScreen: String? = null,
)
