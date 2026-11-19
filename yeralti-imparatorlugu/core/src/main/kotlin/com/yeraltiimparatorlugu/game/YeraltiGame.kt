package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.yeraltiimparatorlugu.sim.SaveManager
import com.yeraltiimparatorlugu.sim.Simulation

/**
 * Uygulama giriş noktası. Tek kalıcı koloni: açılışta kayıt yüklenir (yoksa yeni koloni kurulur),
 * 30 saniyede bir ve uygulama arka plana alınınca otomatik kaydedilir.
 */
class YeraltiGame : ApplicationAdapter(), GameActions {
    override lateinit var sim: Simulation
    private lateinit var settings: Settings
    private lateinit var art: Art
    private lateinit var ui: Ui
    private lateinit var batch: SpriteBatch
    private lateinit var renderer: WorldRenderer
    private lateinit var hud: Hud
    private lateinit var input: WorldInput
    private lateinit var saves: SaveManager
    private lateinit var sounds: SoundFx
    private val view = ViewState()

    /** Masaüstü araçları (ekran görüntüsü betiği) için erişim. */
    val tools: Tools get() = Tools()

    inner class Tools {
        val hud: Hud get() = this@YeraltiGame.hud
        val view: ViewState get() = this@YeraltiGame.view
        val camera get() = renderer.camera
        fun replaceSim(s: Simulation) { sim = s }
    }
    private var autosave = 0f
    private var ready = false

    override fun create() {
        settings = Settings()
        val w = Gdx.graphics.width.coerceAtLeast(1)
        val h = Gdx.graphics.height.coerceAtLeast(1)
        val uiScale = minOf(w / 720f, h / 1280f).coerceIn(0.4f, 4f)
        art = Art(uiScale)
        ui = Ui(art)
        batch = SpriteBatch(4000)
        renderer = WorldRenderer(art, settings)
        val storage = GdxSaveStorage()
        saves = SaveManager(storage)

        var welcome: List<String>? = null
        var offlineLines: List<String>? = null
        var backupNote = false
        val loaded = try { saves.load(System.currentTimeMillis()) } catch (e: Exception) { Gdx.app.error("Kayit", "Yüklenemedi", e); null }
        if (loaded != null) {
            sim = loaded.sim
            backupNote = loaded.usedBackup
            if (loaded.offline.meaningful) offlineLines = loaded.offline.lines
        } else {
            if (saves.hasSave()) {
                // Okunamayan kaydı silmek yerine kenara al.
                if (storage.exists("koloni.json")) storage.move("koloni.json", "koloni.json.bozuk")
            }
            sim = Simulation.newGame(System.currentTimeMillis() xor System.nanoTime())
            welcome = welcomeLines()
        }

        sounds = SoundFx(settings)
        hud = Hud(ui, view, this, renderer, settings, sounds)
        input = WorldInput(view, hud, renderer, this)
        Gdx.input.inputProcessor = InputMultiplexer(hud.stage, input.keysAndScroll, input.detector)
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        resize(w, h)
        focus(0, 6)
        when {
            welcome != null -> hud.showOfflineReport(welcome, "Hoş geldiniz")
            offlineLines != null -> hud.showOfflineReport(offlineLines, "Siz yokken")
        }
        if (backupNote) hud.showToast("Ana kayıt okunamadı, yedekten yüklendi.")
        ready = true
    }

    private fun welcomeLines() = listOf(
        "Kraliçeniz ve 6 işçiniz yüzeyin altındaki küçük yuvada.",
        "Kaz aracıyla hücreleri işaretleyin: işçiler gerçekten kazar, çıkan toprağı depoya ya da yüzeye taşır.",
        "İnşa menüsünden oda planlayın: alan önce kazılır, sonra inşa edilir. Yerleşim önemlidir, komşu odalar bonus verir.",
        "Toplayıcılar yüzeydeki yaprak, tohum ve böcek kalıntılarını besin deposuna getirir.",
        "Koloni › Görevler ekranından öncelikleri ve uzman işçileri ayarlayın.",
        "Feromonlarla karıncaları yönlendirin: uzak kaynaklar ancak Yiyecek feromonuyla fark edilir.",
        "Araştırma Laboratuvarı kurup yeni odaları, sınıfları ve yetenekleri açın. Mevsimler, sel ve kuraklık koloniyi etkiler.",
        "İlk günler sakindir; sonra rakip koloniler baskın yapabilir. Asker yetiştirin, tuzak kurun, gerekirse tüneli kapatın.",
    )

    override fun saveNow() {
        if (!ready) return
        try { saves.save(sim, System.currentTimeMillis()) } catch (e: Exception) { Gdx.app.error("Kayit", "Kaydedilemedi", e) }
    }

    override fun newColony() {
        saves.delete()
        sim = Simulation.newGame(System.currentTimeMillis() xor System.nanoTime())
        view.selectedCell = null
        hud.setMode(ToolMode.VIEW)
        focus(0, 6)
        saveNow()
    }

    override fun focus(x: Int, y: Int) {
        renderer.camera.position.set(x + 0.5f, -y - 0.5f, 0f)
        input.clamp()
    }

    override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        renderer.resize(width, height)
        hud.resize(width, height)
    }

    override fun render() {
        val delta = Gdx.graphics.deltaTime.coerceAtMost(0.1f)
        sim.update(delta, view.speed)
        autosave += delta
        if (autosave >= 30f) { autosave = 0f; saveNow() }
        while (true) { val cue = sim.sounds.removeFirstOrNull() ?: break; sounds.play(cue) }
        hud.update(delta)
        val bg = Palette.unexplored
        Gdx.gl.glClearColor(bg.r, bg.g, bg.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        renderer.render(batch, sim, view, delta)
        hud.stage.draw()
    }

    override fun pause() = saveNow()

    override fun dispose() {
        saveNow()
        hud.dispose()
        sounds.dispose()
        batch.dispose()
        art.dispose()
    }
}
