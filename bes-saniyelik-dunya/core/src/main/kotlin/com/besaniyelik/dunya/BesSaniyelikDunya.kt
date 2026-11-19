package com.besaniyelik.dunya

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.physics.box2d.Box2D
import com.besaniyelik.dunya.audio.AudioManager
import com.besaniyelik.dunya.level.LevelRepository
import com.besaniyelik.dunya.progress.ProfileService
import com.besaniyelik.dunya.progress.SaveManager
import com.besaniyelik.dunya.progress.SaveStorage
import com.besaniyelik.dunya.ui.UiKit
import com.besaniyelik.dunya.ui.screens.BaseScreen
import com.besaniyelik.dunya.ui.screens.SplashScreen

/** Kayıtları Gdx.files.local altında tutar (uygulama güncellemelerinde silinmez). */
class GdxSaveStorage : SaveStorage {
    override fun read(name: String): String? = Gdx.files.local("save/$name").takeIf { it.exists() }?.readString("UTF-8")
    override fun write(name: String, text: String) {
        val tmp = Gdx.files.local("save/$name.tmp")
        tmp.writeString(text, false, "UTF-8")
        val dst = Gdx.files.local("save/$name")
        if (dst.exists()) dst.delete()
        tmp.moveTo(dst)
    }
    override fun delete(name: String) { Gdx.files.local("save/$name").delete() }
}

class BesSaniyelikDunya(val platform: Platform = Platform.NONE) : Game() {
    lateinit var assets: Assets
    lateinit var audio: AudioManager
    lateinit var profile: ProfileService
    lateinit var kit: UiKit
    lateinit var batch: SpriteBatch
    private var pending: BaseScreen? = null

    override fun create() {
        Box2D.init()
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        LevelRepository.reader = { path -> Gdx.files.internal(path).readString("UTF-8") }
        batch = SpriteBatch(4000)
        assets = Assets()
        profile = ProfileService(SaveManager(GdxSaveStorage()))
        audio = AudioManager { profile.data.settings }
        kit = UiKit(assets, audio)
        setScreen(SplashScreen(this))
    }

    /** Ekran geçişi (eski ekran kararıp yenisi beliren yumuşak geçiş BaseScreen içinde yapılır). */
    fun go(next: BaseScreen) {
        val cur = screen as? BaseScreen
        if (cur == null) { setScreen(next); return }
        if (pending != null) return
        pending = next
        cur.fadeOut {
            val old = screen
            setScreen(next)
            old?.dispose()
            pending = null
        }
    }

    /** Masaüstü test/ekran görüntüsü kancası (her karede çağrılır). */
    var debugHook: ((BesSaniyelikDunya, Int) -> Unit)? = null
    private var frameNo = 0

    override fun render() {
        super.render()
        debugHook?.invoke(this, frameNo++)
    }

    fun vibrate(ms: Int) { if (profile.data.settings.vibration) platform.vibrate(ms) }

    override fun pause() { super.pause(); profile.save(); audio.pause() }
    override fun resume() { super.resume(); audio.resume() }

    override fun dispose() {
        screen?.dispose()
        audio.dispose(); assets.dispose(); batch.dispose()
    }
}
