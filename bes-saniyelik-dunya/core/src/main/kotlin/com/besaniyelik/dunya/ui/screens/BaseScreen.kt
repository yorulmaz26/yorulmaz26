package com.besaniyelik.dunya.ui.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.besaniyelik.dunya.BesSaniyelikDunya
import com.besaniyelik.dunya.Theme
import com.besaniyelik.dunya.progress.Catalog
import com.besaniyelik.dunya.render.CharState
import com.besaniyelik.dunya.render.CharacterRenderer
import com.besaniyelik.dunya.ui.Palette
import com.besaniyelik.dunya.ui.UiButton
import com.besaniyelik.dunya.ui.popIn

/** Menülerde arkada kayan, açık renkli tema manzarası. */
class MenuBackground(private val game: BesSaniyelikDunya, private var map: Int = 1) {
    private val cam = OrthographicCamera()
    private val vp = ExtendViewport(12.5f, 22.2f, cam)
    private var t = 0f
    private var theme: Theme = game.assets.theme(map)
    private val clouds = List(6) { floatArrayOf(MathUtils.random(0f, 14f), MathUtils.random(6f, 16f), MathUtils.random(0.6f, 1.3f)) }
    var showRunner = true

    fun setMap(m: Int) { if (m != map) { map = m; theme = game.assets.theme(m) } }
    fun resize(w: Int, h: Int) = vp.update(w, h, true)

    fun render(dt: Float) {
        t += dt
        val b = game.batch
        vp.apply()
        cam.position.set(6.25f, 4.5f, 0f); cam.update()
        b.projectionMatrix = cam.combined
        b.begin()
        val vw = vp.worldWidth; val vh = vp.worldHeight
        val left = 6.25f - vw / 2; val bottom = 4.5f - vh / 2
        theme.drawSky(b, left, bottom, vw, vh)
        val scroll = t * 1.2f
        fun par(tex: com.badlogic.gdx.graphics.Texture, y: Float, h: Float, f: Float) {
            val tw = h * com.besaniyelik.dunya.Theme.PARALLAX_ASPECT
            val u0 = (scroll * f) / tw
            b.draw(tex, left, y, vw, h, u0, 1f, u0 + vw / tw, 0f)
        }
        par(theme.far, -1.2f, 9.5f, 0.15f)
        par(theme.mid, -1.8f, 7.2f, 0.4f)
        val cloud = game.assets.g("pt_dust")
        b.setColor(1f, 1f, 1f, 0.85f)
        for (c in clouds) {
            val x = ((c[0] - t * 0.3f * c[2]) % 16f + 16f) % 16f - 2f + left
            b.draw(cloud, x, c[1], 2.4f * c[2], 1.4f * c[2])
        }
        b.setColor(1f, 1f, 1f, 1f)
        // zemin şeridi
        var x = left - ((scroll) % 1f)
        while (x < left + vw) {
            b.draw(theme.top, x, -1f, 1f, 1f)
            var y = -2f
            while (y > bottom - 1f) { b.draw(theme.fill, x, y, 1f, 1f); y -= 1f }
            x += 1f
        }
        if (showRunner) {
            val skin = Catalog.skin(game.profile.data.selectedSkin)
            val jump = ((t % 2.2f) / 2.2f)
            val jy = if (jump > 0.7f) kotlin.math.sin((jump - 0.7f) / 0.3f * Math.PI.toFloat()) * 1.6f else 0f
            renderer.draw(b, skin, left + vw * 0.3f, 0.47f + jy, 0.98f, if (jy > 0.05f) CharState.JUMP else CharState.RUN, t)
        }
        b.end()
    }

    private val renderer = CharacterRenderer(game.assets)
}

/** Bütün ekranların temeli: Scene2D sahnesi, geri tuşu, geçiş, bildirim ve diyalog yardımcıları. */
abstract class BaseScreen(val game: BesSaniyelikDunya) : ScreenAdapter() {
    val stage = Stage(ExtendViewport(720f, 1280f), game.batch)
    val kit get() = game.kit
    val a get() = game.assets
    val root = Table().apply { setFillParent(true); top() }
    open val background: MenuBackground? by lazy { MenuBackground(game) }
    /** Ekran açılırken hangi renkten belirdiği (çıkış her zaman beyaza). */
    open val fadeInColor: Color = Color.WHITE
    private var fade = 1f
    private var fadeDir = -1f
    private var fadeDone: (() -> Unit)? = null
    private val fadeImg by lazy { Image(a.ui("white")).apply { color = Color(1f, 1f, 1f, 1f); touchable = Touchable.disabled } }
    protected val input = InputMultiplexer()

    init {
        stage.addActor(root)
        input.addProcessor(stage)
        input.addProcessor(object : InputAdapter() {
            override fun keyDown(keycode: Int): Boolean {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) { onBack(); return true }
                return false
            }
        })
    }

    abstract fun build()

    open fun onBack() { game.go(MainMenuScreen(game)) }

    override fun show() {
        Gdx.input.inputProcessor = input
        build()
        stage.addActor(fadeImg)
        fade = 1f; fadeDir = -1f
    }

    fun rebuild() {
        root.clear()
        build()
        fadeImg.toFront()
    }

    fun fadeOut(done: () -> Unit) { fadeDir = 1f; fadeDone = done; Gdx.input.inputProcessor = null }

    override fun render(delta: Float) {
        val dt = delta.coerceAtMost(1 / 20f)
        Gdx.gl.glClearColor(Palette.BG.r, Palette.BG.g, Palette.BG.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        renderWorld(dt)
        stage.act(dt)
        fade = (fade + fadeDir * dt * 5f).coerceIn(0f, 1f)
        fadeImg.setBounds(-2000f, -2000f, 6000f, 6000f)
        val fc = if (fadeDir < 0) fadeInColor else Color.WHITE
        fadeImg.color.set(fc.r, fc.g, fc.b, Interpolation.smooth.apply(fade))
        fadeImg.isVisible = fade > 0.001f
        stage.viewport.apply()
        stage.draw()
        if (fadeDir > 0 && fade >= 1f) { val f = fadeDone; fadeDone = null; f?.invoke() }
    }

    open fun renderWorld(dt: Float) { background?.render(dt) }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        background?.resize(width, height)
    }

    override fun dispose() { stage.dispose() }

    // ------------------------------------------------------------ yardımcılar
    /** Üst çubuk: geri butonu, başlık ve para birimleri. */
    fun header(title: String, showCurrency: Boolean = true, back: () -> Unit = { onBack() }): Table {
        val t = Table()
        t.add(kit.iconButton("g_back", "white", 92f, 48f) { back() }).size(92f).left().padLeft(20f)
        val titleCell = Table().apply { background = kit.patch("banner"); pad(10f, 30f, 14f, 30f); add(kit.label(title, a.f36, Palette.INK)) }
        t.add(titleCell).expandX().center()
        if (showCurrency) {
            val c = Table()
            c.add(kit.currency("b_coin", game.profile.data.coins.toString(), a.f22)).right().row()
            c.add(kit.currency("b_gem", game.profile.data.gems.toString(), a.f22)).right().padTop(4f)
            t.add(c).right().padRight(16f).width(160f)
        } else t.add().width(112f)
        return t
    }

    private var toastY = 0f
    /** Bildirim süresi (oyun ekranı kısaltır). */
    open val toastTime = 2.4f
    fun toast(text: String, icon: String? = null) {
        val t = Table().apply {
            background = kit.patch("banner"); pad(16f, 26f, 20f, 26f)
            if (icon != null) add(Image(a.ui(icon))).size(56f).padRight(12f)
            add(kit.label(text, a.f28, Palette.INK, wrap = true)).width(460f)
        }
        t.pack()
        t.setPosition((stage.width - t.width) / 2, stage.height - 220f - toastY)
        toastY += t.height + 10f
        t.color.a = 0f
        t.addAction(Actions.sequence(Actions.fadeIn(0.2f), Actions.delay(toastTime), Actions.fadeOut(0.3f), Actions.run { toastY = (toastY - t.height - 10f).coerceAtLeast(0f) }, Actions.removeActor()))
        stage.addActor(t)
    }

    /** Ortada açılan modal diyalog; arka plan yarı saydam açık renk. */
    fun dialog(build: (Table, () -> Unit) -> Unit): Table {
        val overlay = Table().apply { setFillParent(true); background = kit.patch("white").tint(Color(0.97f, 0.98f, 1f, 0.6f)); touchable = Touchable.enabled }
        val panel = kit.panel()
        lateinit var close: () -> Unit
        close = { overlay.addAction(Actions.sequence(Actions.fadeOut(0.15f), Actions.removeActor())) }
        build(panel, close)
        overlay.add(panel).width(640f).center()
        overlay.addListener(object : ClickListener() { override fun clicked(event: InputEvent?, x: Float, y: Float) {} })
        stage.addActor(overlay)
        panel.popIn()
        return overlay
    }

    fun scroll(content: Actor): ScrollPane = ScrollPane(content).apply { setScrollingDisabled(true, false); fadeScrollBars = true; setOverscroll(false, true) }
}
