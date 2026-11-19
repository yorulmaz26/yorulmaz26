package com.kayipadalar.game

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.kayipadalar.game.audio.AudioManager
import com.kayipadalar.game.screens.BaseScreen
import com.kayipadalar.game.screens.SplashScreen
import com.kayipadalar.game.ui.Theme
import com.kayipadalar.game.view.Sky
import com.kayipadalar.logic.kingdom.KingdomConfig
import com.kayipadalar.logic.kingdom.KingdomGame
import com.kayipadalar.logic.save.SaveStorage
import com.kayipadalar.logic.save.SaveSystem

/** libGDX yerel depolama üzerinde atomik kayıt. */
class GdxSaveStorage : SaveStorage {
    private fun f(name: String) = Gdx.files.local(name)
    override fun read(name: String): String? = f(name).takeIf { it.exists() }?.readString("UTF-8")
    override fun writeAtomic(name: String, text: String) {
        val tmp = f("$name.tmp")
        tmp.writeString(text, false, "UTF-8")
        tmp.moveTo(f(name))
    }
    override fun copy(from: String, to: String) { if (f(from).exists()) f(from).copyTo(f(to)) }
    override fun exists(name: String) = f(name).exists()
}

open class KayipAdalarGame(val platform: Platform) : Game() {
    lateinit var game: KingdomGame
    lateinit var theme: Theme
    lateinit var batch: SpriteBatch
    lateinit var audio: AudioManager
    lateinit var assets: Assets
    lateinit var sky: Sky
    private val history = ArrayList<Screen>()
    private var saveTimer = 0f

    val k get() = game.k

    override fun create() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        val cfg = KingdomConfig.parse(Gdx.files.internal("data/kingdom.json").readString("UTF-8"))
        game = KingdomGame(cfg, SaveSystem(GdxSaveStorage()), platform.timeSource)
        theme = Theme()
        batch = SpriteBatch()
        audio = AudioManager(game.save.settings)
        assets = Assets().also { it.queue() }
        setScreen(SplashScreen(this))
    }

    /** Assets yüklendikten sonra çağrılır. */
    fun onAssetsReady() { sky = Sky(assets) }

    fun goTo(s: Screen) {
        screen?.let { history.add(it) }
        setScreen(s)
    }

    fun resetTo(s: Screen) {
        history.forEach { it.dispose() }; history.clear()
        val cur = screen
        setScreen(s)
        cur?.dispose()
    }

    fun back() {
        if (history.isEmpty()) return
        val cur = screen
        setScreen(history.removeAt(history.size - 1))
        cur?.dispose()
    }

    override fun render() {
        super.render()
        saveTimer += Gdx.graphics.deltaTime
        if (saveTimer > 20f && ::game.isInitialized) { saveTimer = 0f; game.persist() } // düzenli otomatik kayıt
    }

    fun vibrate(ms: Int) { if (game.save.settings.vibration) platform.vibrate(ms) }

    override fun pause() {
        game.persist()
        audio.pause()
    }

    override fun resume() {
        (screen as? BaseScreen)?.onResume()
        audio.updateMusic()
    }

    override fun dispose() {
        game.persist()
        screen?.dispose()
        history.forEach { it.dispose() }
        audio.dispose(); theme.dispose(); batch.dispose(); assets.dispose()
    }
}
