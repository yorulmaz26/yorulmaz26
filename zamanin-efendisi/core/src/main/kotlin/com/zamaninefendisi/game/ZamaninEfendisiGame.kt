package com.zamaninefendisi.game

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.zamaninefendisi.game.audio.AudioManager
import com.zamaninefendisi.game.screens.GameScreen
import com.zamaninefendisi.game.screens.SplashScreen
import com.zamaninefendisi.game.ui.Assets
import com.zamaninefendisi.game.ui.Ui
import com.zamaninefendisi.logic.save.Progression
import com.zamaninefendisi.logic.save.SaveSystem

/** Oyunun kök sınıfı: ortak hizmetleri tutar ve ekranlar arasında geçişi yönetir (ScreenManager). */
class ZamaninEfendisiGame(
    val platform: Platform = DefaultPlatform(),
    /** Geliştirici kısayolu (yalnızca masaüstü): açılışta doğrudan ekrana git, ör. "game:3:2" veya "menu". */
    val devStart: String? = null,
) : Game() {
    lateinit var batch: SpriteBatch; private set
    lateinit var shapes: ShapeRenderer; private set
    val assets = Assets()
    lateinit var ui: Ui; private set
    lateinit var audio: AudioManager; private set
    lateinit var progression: Progression; private set

    override fun create() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        batch = SpriteBatch()
        shapes = ShapeRenderer(8000)
        assets.load()
        ui = Ui(this)
        progression = Progression(SaveSystem(GdxSaveStorage(platform.externalSaveDir)), platform.clock)
        audio = AudioManager { progression.data.settings.sound }
        audio.load()
        setScreen(SplashScreen(this))
    }

    /** Yeni ekrana geçer ve eskisini serbest bırakır. */
    fun go(next: Screen) {
        val old = screen
        setScreen(next)
        old?.dispose()
    }

    fun vibrate(ms: Int) {
        if (progression.data.settings.vibration) Gdx.input.vibrate(ms)
    }

    override fun pause() {
        (screen as? GameScreen)?.saveProgress()
        progression.refreshLives()
        progression.persist()
        super.pause()
    }

    override fun resume() {
        progression.refreshLives()
        super.resume()
    }

    override fun dispose() {
        (screen as? GameScreen)?.saveProgress()
        screen?.dispose()
        audio.dispose()
        assets.dispose()
        shapes.dispose()
        batch.dispose()
    }
}
