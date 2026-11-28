package com.yanlisrenk.game

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.yanlisrenk.game.render.WorldRenderer
import com.yanlisrenk.game.screens.BaseScreen
import com.yanlisrenk.game.screens.SplashScreen
import com.yanlisrenk.game.ui.Ui
import com.yanlisrenk.logic.GameSession
import com.yanlisrenk.logic.level.LevelRepository
import com.yanlisrenk.logic.save.SaveManager

/** Uygulama giriş noktası. Ekran yönetimi, oturum, ses ve varlıklar burada tutulur. */
class YanlisRenkGame(val platform: PlatformServices) : Game() {
    lateinit var assets: Assets
    lateinit var audio: Audio
    lateinit var ui: Ui
    lateinit var session: GameSession
    lateinit var levels: LevelRepository
    lateinit var world: WorldRenderer
    private var tickTimer = 0f
    private var syncTimer = 0f
    var lastSyncOk: Boolean? = null
        private set
    /** Ekran geçmişi (geri tuşu için). */
    private val history = ArrayList<() -> BaseScreen>()
    /** Testler/ekran görüntüsü için: arka plan sunucu senkronunu kapatır. */
    var autoSync = true

    override fun create() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        assets = Assets()
        assets.load()
        com.yanlisrenk.game.render.CharacterPainter.spriteFor = { id -> assets.characterSprite(id) }
        session = GameSession(SaveManager(platform.storage) { platform.newInstallId() }, platform.clock) { platform.tzOffsetMillis() }
        // dil: kayıtlı tercih yoksa cihaz dili (desteklenmiyorsa İngilizce)
        com.yanlisrenk.logic.i18n.L.install { code -> runCatching { Gdx.files.internal("localization/$code.json").readString("UTF-8") }.getOrNull() }
        com.yanlisrenk.logic.i18n.L.setLanguage(com.yanlisrenk.logic.i18n.L.resolve(session.data.settings.language, java.util.Locale.getDefault().language))
        audio = Audio(assets) { session.data.settings }
        ui = Ui(assets, audio)
        levels = LevelRepository { name -> runCatching { Gdx.files.internal("levels/$name").readString("UTF-8") }.getOrNull() }
        world = WorldRenderer(assets)
        syncTime()
        setScreen(com.yanlisrenk.game.screens.IntroScreen(this))
    }

    /** Güvenilir sunucu zamanıyla eşitle (internet yoksa sessizce geçer). */
    fun syncTime(onDone: ((Boolean, Long) -> Unit)? = null) {
        if (!autoSync && onDone == null) return
        platform.fetchServerTime { t ->
            Gdx.app.postRunnable {
                if (t != null) {
                    val credit = session.applyServerTime(t)
                    lastSyncOk = true
                    onDone?.invoke(true, credit)
                } else {
                    lastSyncOk = false
                    onDone?.invoke(false, 0)
                }
            }
        }
    }

    // ---------- reklamlar ----------

    val ads: AdService get() = platform.ads

    /**
     * Bölüm/koşu sonu molası: kurallar uygunsa geçiş reklamı gösterir, sonra [then] çalışır.
     * Reklam yoksa ya da sırası değilse [then] hemen çalışır (akış hiç beklemez).
     */
    fun adBreak(then: () -> Unit) {
        val st = session.data.ads
        val show = com.yanlisrenk.logic.ads.AdPolicy.onBreak(st, session.clock.now(), session.data.completedLevels)
        if (show && ads.isInterstitialReady()) {
            audio.pause()
            com.yanlisrenk.logic.ads.AdPolicy.interstitialShown(st, session.clock.now())
            session.save()
            ads.showInterstitial { audio.resume(); then() }
        } else then()
    }

    fun rewardedLeftToday(): Int = com.yanlisrenk.logic.ads.AdPolicy.rewardedLeft(session.data.ads, session.dayKey())

    /** İsteğe bağlı ödüllü video. Ödül hak edildiyse [onReward]. Hazır değilse false döner. */
    fun watchRewarded(onReward: () -> Unit): Boolean {
        if (rewardedLeftToday() <= 0 || !ads.isRewardedReady()) return false
        audio.pause()
        ads.showRewarded { ok ->
            audio.resume()
            if (ok) {
                com.yanlisrenk.logic.ads.AdPolicy.rewardedWatched(session.data.ads, session.dayKey())
                onReward()
                session.save()
            }
        }
        return true
    }

    /** Yeni ekrana geç. [remember] → geri tuşu buraya döner. */
    fun go(factory: () -> BaseScreen, remember: Boolean = true) {
        val current = screen as? BaseScreen
        if (remember && current != null) current.recreate?.let { history.add(it) }
        if (history.size > 20) history.removeAt(0)
        val next = factory()
        current?.let { old -> Gdx.app.postRunnable { old.dispose() } }
        setScreen(next)
    }

    fun back() {
        val f = history.removeLastOrNull()
        if (f == null) { go({ com.yanlisrenk.game.screens.MainMenuScreen(this) }, remember = false); return }
        go(f, remember = false)
    }

    fun clearHistory() = history.clear()

    override fun render() {
        val dt = minOf(Gdx.graphics.deltaTime, 0.1f)
        tickTimer += dt
        if (tickTimer >= 1f) {
            tickTimer = 0f
            val gained = session.update()
            if (gained > 0) { audio.play("life_refill"); (screen as? BaseScreen)?.onLivesChanged() }
            session.saveIfDirty()
        }
        syncTimer += dt
        if (syncTimer > 600f) { syncTimer = 0f; syncTime() }
        audio.update(dt)
        super.render()
    }

    override fun pause() {
        session.update()
        session.save()
        audio.pause()
        super.pause()
    }

    override fun resume() {
        session.update()
        audio.resume()
        syncTime()
        super.resume()
        (screen as? BaseScreen)?.onLivesChanged()
    }

    override fun dispose() {
        session.save()
        screen?.dispose()
        world.dispose()
        assets.dispose()
    }
}
