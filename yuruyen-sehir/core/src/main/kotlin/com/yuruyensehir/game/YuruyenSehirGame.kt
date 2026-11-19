package com.yuruyensehir.game

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.yuruyensehir.game.screens.GameScreen
import com.yuruyensehir.game.ui.Theme
import com.yuruyensehir.logic.GameCore
import com.yuruyensehir.logic.OfflineReport
import com.yuruyensehir.logic.SaveManager
import com.yuruyensehir.logic.SaveStorage

/** libGDX yerel dosyalarına kayıt (Android'de uygulamaya özel dahili depolama). */
class GdxSaveStorage : SaveStorage {
    override fun read(name: String): String? = Gdx.files.local(name).takeIf { it.exists() }?.readString("UTF-8")
    override fun write(name: String, content: String) = Gdx.files.local(name).writeString(content, false, "UTF-8")
    override fun delete(name: String) { Gdx.files.local(name).delete() }
}

/** Masaüstü test betikleri gibi dış sürücüler için kanca. */
fun interface GameHook {
    fun onFrame(game: YuruyenSehirGame, frame: Long)
}

class YuruyenSehirGame(private val hook: GameHook? = null, val store: PlatformStore = NoStore) : Game() {
    lateinit var theme: Theme
        private set
    lateinit var saves: SaveManager
        private set
    lateinit var core: GameCore
        private set
    var pendingOffline: OfflineReport? = null
    var saveError: String? = null
        private set
    private var frame = 0L
    lateinit var gameScreen: GameScreen
        private set
    val audio = AudioManager()

    override fun create() {
        theme = Theme()
        audio.init()
        saves = SaveManager(GdxSaveStorage())
        val loaded = try { saves.load() } catch (e: Exception) { Gdx.app.error("Kayit", "Yuklenemedi", e); null }
        core = if (loaded != null) {
            GameCore(loaded).also { pendingOffline = it.applyOffline(System.currentTimeMillis()) }
        } else {
            GameCore.newGame()
        }
        gameScreen = GameScreen(this)
        setScreen(gameScreen)
        connectStore()
        save()
    }

    override fun render() {
        super.render()
        hook?.onFrame(this, frame++)
    }

    /** Play mağazasından sahip olunan ürünleri geri yükler (iadeler dahil). */
    fun connectStore() {
        store.connect { owned ->
            com.yuruyensehir.logic.PremiumSystem.sync(core.state, owned)
            save()
        }
    }

    fun save() {
        try {
            saves.save(core.state, System.currentTimeMillis())
            saveError = null
        } catch (e: Exception) {
            saveError = e.message
            Gdx.app.error("Kayit", "Kaydedilemedi", e)
        }
    }

    override fun pause() {
        save()
        audio.pause()
    }

    override fun resume() {
        core.applyOffline(System.currentTimeMillis())?.let {
            pendingOffline = it
            gameScreen.showOfflineIfAny()
        }
    }

    fun startNewGame() {
        saves.wipe()
        core = GameCore.newGame()
        save()
        gameScreen.onNewGame()
    }

    override fun dispose() {
        save()
        gameScreen.dispose()
        audio.dispose()
        store.dispose()
        theme.dispose()
    }
}
