package com.zamaninefendisi.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.zamaninefendisi.game.ZamaninEfendisiGame
import com.zamaninefendisi.game.ui.Theme
import com.zamaninefendisi.logic.save.MAX_LIVES
import kotlin.math.sin

/** Ortak ekran altyapısı: sanal 720x1280 dikey sahne, aydınlık arka plan, geri tuşu. */
abstract class BaseScreen(val game: ZamaninEfendisiGame) : ScreenAdapter() {
    val stage = Stage(ExtendViewport(720f, 1280f), game.batch)
    val root = Table().apply { setFillParent(true); touchable = Touchable.childrenOnly }
    protected val ui get() = game.ui
    protected val assets get() = game.assets
    protected var time = 0f
    open val skyTop: Color = Color.valueOf("FFF1D6")
    open val skyBottom: Color = Color.valueOf("E6F5FF")

    init {
        stage.addActor(root)
        stage.addListener(object : InputListener() {
            override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    // Açık bir modal varsa önce onu kapat.
                    val top = stage.actors.lastOrNull()
                    if (top != null && top !== root && top.userObject == MODAL) { top.remove(); return true }
                    onBack(); return true
                }
                return false
            }
        })
    }

    open fun extraInput(): InputProcessor? = null

    override fun show() {
        Gdx.input.inputProcessor = extraInput()?.let { InputMultiplexer(stage, it) } ?: stage
        root.color.a = 0f
        root.addAction(Actions.fadeIn(0.2f))
    }

    open fun onBack() {}

    override fun render(delta: Float) {
        time += delta
        Gdx.gl.glClearColor(skyBottom.r, skyBottom.g, skyBottom.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        drawBackdrop()
        drawWorld(delta)
        stage.act(delta.coerceAtMost(1 / 20f))
        stage.draw()
        drawOverlay()
    }

    protected open fun drawWorld(delta: Float) {}
    protected open fun drawOverlay() {}

    /** Gökyüzü geçişi ve süzülen ışık halkaları. */
    protected open fun drawBackdrop() {
        val sr = game.shapes
        stage.viewport.apply()
        sr.projectionMatrix = stage.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        sr.begin(ShapeRenderer.ShapeType.Filled)
        val w = stage.width; val h = stage.height
        sr.rect(0f, 0f, w, h, skyBottom, skyBottom, skyTop, skyTop)
        for (i in 0 until 7) {
            val x = (i * 173f + time * (8f + i * 2f)) % (w + 200f) - 100f
            val y = h * (0.15f + 0.12f * i) + sin(time * 0.6f + i) * 12f
            sr.color = Color(1f, 1f, 1f, 0.35f)
            sr.circle(x, y, 26f + (i % 3) * 10f, 24)
            sr.circle(x + 30f, y + 6f, 20f + (i % 2) * 8f, 24)
            sr.circle(x - 28f, y + 2f, 16f, 20)
        }
        sr.end()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun dispose() {
        stage.dispose()
    }

    /** Ekranın üstündeki ortak kaynak/can çubuğu. */
    protected fun resourceBar(): com.badlogic.gdx.scenes.scene2d.ui.Container<Table> =
        object : com.badlogic.gdx.scenes.scene2d.ui.Container<Table>(buildResources()) {
            private var acc = 0f
            override fun act(delta: Float) {
                super.act(delta)
                acc += delta
                if (acc >= 1f) { acc = 0f; actor = buildResources() }
            }
        }

    private fun buildResources(): Table {
        val p = game.progression
        val lives = p.refreshLives()
        val t = ui.panel(Theme.panel, small = true)
        val livesText = if (lives.lives >= MAX_LIVES) "Can ${lives.lives}/$MAX_LIVES" else "Can ${lives.lives}/$MAX_LIVES · ${com.zamaninefendisi.game.ui.formatTime(lives.regenRemainingMs)}"
        t.add(ui.label(livesText, assets.fontS, Theme.red)).padRight(18f)
        t.add(ui.label("Altın ${p.amount(com.zamaninefendisi.logic.meta.Resource.GOLD)}", assets.fontS, Theme.darker(Theme.gold, 0.8f))).padRight(18f)
        t.add(ui.label("Kristal ${p.amount(com.zamaninefendisi.logic.meta.Resource.CRYSTAL)}", assets.fontS, Theme.purple)).padRight(18f)
        t.add(ui.label("Bilim ${p.amount(com.zamaninefendisi.logic.meta.Resource.SCIENCE)}", assets.fontS, Theme.blue)).padRight(18f)
        t.add(ui.label("Yıldız ${p.totalStars()}", assets.fontS, Theme.orange))
        t.pad(10f, 18f, 10f, 18f)
        return t
    }

    protected fun header(title: String, onBack: () -> Unit): Table {
        val t = Table()
        t.add(ui.button("‹ Geri", Theme.blue, assets.fontS) { onBack() }).width(140f).height(70f).left()
        t.add(ui.label(title, assets.fontL, Theme.ink, align = Align.center)).expandX().center()
        t.add().width(140f)
        return t
    }

    companion object {
        const val MODAL = "modal"
        fun wobble(t: Float) = MathUtils.sin(t)
    }
}
