package com.kayipadalar.game

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.kayipadalar.game.audio.AudioManager
import com.kayipadalar.game.render.SkyBackground
import com.kayipadalar.game.screens.BaseScreen
import com.kayipadalar.game.screens.SplashScreen
import com.kayipadalar.game.ui.Theme
import com.kayipadalar.logic.GameCore
import com.kayipadalar.logic.L10n
import com.kayipadalar.logic.data.GameConfig
import com.kayipadalar.logic.meta.MetaConfig
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
    lateinit var core: GameCore
    lateinit var theme: Theme
    lateinit var batch: SpriteBatch
    lateinit var shapes: ShapeRenderer
    lateinit var audio: AudioManager
    lateinit var sky: SkyBackground
    private val history = ArrayList<Screen>()

    override fun create() {
        Gdx.input.setCatchKey(com.badlogic.gdx.Input.Keys.BACK, true)
        L10n.install(L10n(Gdx.files.internal("i18n/strings_tr.properties").readString("UTF-8")))
        val rules = GameConfig.parse(Gdx.files.internal("data/rules.json").readString("UTF-8"))
        val meta = MetaConfig.parse(Gdx.files.internal("data/meta.json").readString("UTF-8"))
        core = GameCore(rules, meta, SaveSystem(GdxSaveStorage()), platform.timeSource) { path ->
            val f = Gdx.files.internal(path)
            if (f.exists()) f.readString("UTF-8") else null
        }
        theme = Theme(if (core.save.settings.largeText) 1.12f else 1f)
        batch = SpriteBatch()
        shapes = ShapeRenderer()
        audio = AudioManager(core.save.settings)
        sky = SkyBackground()
        setScreen(SplashScreen(this))
    }

    /** Yeni ekrana geçer; [remember] ise geri tuşu bir önceki ekrana döner. */
    fun goTo(s: Screen, remember: Boolean = true) {
        val cur = screen
        if (remember && cur != null) history.add(cur) else cur?.dispose()
        setScreen(s)
    }

    /** Geçmişi temizleyip kök ekrana döner. */
    fun resetTo(s: Screen) {
        history.forEach { it.dispose() }
        history.clear()
        val cur = screen
        setScreen(s)
        cur?.dispose()
    }

    fun back() {
        if (history.isEmpty()) { Gdx.app.exit(); return }
        val cur = screen
        val prev = history.removeAt(history.size - 1)
        setScreen(prev)
        cur?.dispose()
    }

    /** Gökyüzünü kuşanılmış krallık temasına göre ayarlar (yalnızca açık tonlar). */
    fun applySky() {
        val c = core.shop.equippedColor("THEME")?.takeIf { it.isNotEmpty() }?.let { com.badlogic.gdx.graphics.Color.valueOf(it) }
        sky.topTint = c?.let { com.badlogic.gdx.graphics.Color(it).lerp(com.kayipadalar.game.ui.Palette.skyTop, 0.35f) } ?: com.kayipadalar.game.ui.Palette.skyTop
        sky.bottomTint = com.kayipadalar.game.ui.Palette.skyBottom
    }

    fun vibrate(ms: Int) { if (core.save.settings.vibration) platform.vibrate(ms) }

    /** Tema (yazı boyutu) değişince tüm arayüzü yeniden kurar. */
    fun rebuildTheme() {
        val old = theme
        theme = Theme(if (core.save.settings.largeText) 1.12f else 1f)
        (screen as? BaseScreen)?.rebuild()
        old.dispose()
    }

    override fun pause() {
        // Arka plana alınırken ilerleme güvenle kaydedilir.
        (screen as? BaseScreen)?.onPause()
        core.persist()
        audio.pause()
    }

    override fun resume() {
        core.tick()
        audio.updateMusic()
    }

    override fun dispose() {
        core.persist()
        screen?.dispose()
        history.forEach { it.dispose() }
        audio.dispose()
        theme.dispose()
        batch.dispose()
        shapes.dispose()
    }
}
