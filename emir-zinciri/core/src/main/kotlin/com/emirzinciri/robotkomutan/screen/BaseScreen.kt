package com.emirzinciri.robotkomutan.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.emirzinciri.robotkomutan.EmirZinciriGame
import com.emirzinciri.robotkomutan.ui.Theme

/** Ortak ekran: aydınlık arka plan, Scene2D sahnesi, geri tuşu ve isteğe bağlı otomatik ekran görüntüsü. */
abstract class BaseScreen(val game: EmirZinciriGame) : ScreenAdapter() {
    val assets get() = game.assets
    val ui get() = game.ui
    val services get() = game.services
    val stage = Stage(ExtendViewport(EmirZinciriGame.VIRTUAL_W, EmirZinciriGame.VIRTUAL_H))
    val root = Table()
    protected var time = 0f
    private var shotPath: String? = null
    private var shotAt = 0f

    init {
        root.setFillParent(true)
        stage.addActor(root)
    }

    /** Bu ekranda çalacak müzik parçası. */
    open val musicTrack: String = "menu"

    override fun show() {
        game.assets.playMusic(musicTrack)
        Gdx.input.inputProcessor = InputMultiplexer(stage, object : InputAdapter() {
            override fun keyDown(keycode: Int): Boolean {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) { onBack(); return true }
                return false
            }
        })
        root.color.a = 0f
        root.addAction(Actions.fadeIn(0.18f))
    }

    /** Geri tuşu davranışı. */
    open fun onBack() {}
    open fun onPause() {}

    fun scheduleScreenshot(path: String, after: Float) { shotPath = path; shotAt = after }

    open fun update(delta: Float) {}
    open fun drawBackground() {}

    override fun render(delta: Float) {
        val dt = minOf(delta, 1 / 20f)
        time += dt
        Gdx.gl.glClearColor(Theme.sky.r, Theme.sky.g, Theme.sky.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        update(dt)
        stage.act(dt)
        drawBackground()
        stage.draw()
        shotPath?.let { if (time >= shotAt) { capture(it); shotPath = null } }
    }

    private fun capture(path: String) {
        val w = Gdx.graphics.backBufferWidth; val h = Gdx.graphics.backBufferHeight
        val pm = Pixmap.createFromFrameBuffer(0, 0, w, h)
        val flipped = Pixmap(w, h, Pixmap.Format.RGBA8888)
        for (y in 0 until h) flipped.drawPixmap(pm, 0, y, w, 1, 0, h - y - 1, w, 1)
        PixmapIO.writePNG(Gdx.files.absolute(path), flipped)
        pm.dispose(); flipped.dispose()
        Gdx.app.log("Ekran", "Görüntü kaydedildi: $path")
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun dispose() {
        stage.dispose()
    }
}
