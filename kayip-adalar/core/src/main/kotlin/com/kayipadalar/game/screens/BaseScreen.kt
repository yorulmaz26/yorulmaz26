package com.kayipadalar.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.kayipadalar.game.KayipAdalarGame
import com.kayipadalar.game.ui.Palette
import com.kayipadalar.logic.model.Res
import com.kayipadalar.logic.tr

const val VW = 720f
const val VH = 1280f

/** Tüm ekranların ortak tabanı: gökyüzü arka planı, sahne, başlık, kaynak çubuğu, diyalog ve bildirim. */
abstract class BaseScreen(val game: KayipAdalarGame) : ScreenAdapter() {
    val stage = Stage(ExtendViewport(VW, VH), game.batch)
    val root = Table()
    val theme get() = game.theme
    val skin get() = game.theme.skin
    val core get() = game.core
    private var overlay: Table? = null
    private var resourceBar: Table? = null

    open val showSky = true

    private val backKeys = object : InputAdapter() {
        override fun keyDown(keycode: Int): Boolean {
            if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                if (overlay != null) { closeOverlay(); return true }
                onBack(); return true
            }
            return false
        }
    }

    abstract fun build()
    open fun onBack() = game.back()
    open fun onPause() {}
    open fun update(dt: Float) {}
    open fun drawWorld(dt: Float) {}

    fun rebuild() {
        overlay = null
        stage.clear()
        root.clear()
        root.setFillParent(true)
        stage.addActor(root)
        build()
    }

    override fun show() {
        Gdx.input.inputProcessor = InputMultiplexer(stage, backKeys)
        core.tick()
        game.audio.updateMusic()
        if (this !is GameplayScreen) game.applySky()
        rebuild()
    }

    override fun render(delta: Float) {
        val dt = delta.coerceAtMost(0.05f)
        Gdx.gl.glClearColor(0.9f, 0.96f, 1f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        stage.viewport.apply()
        if (showSky) {
            game.sky.update(dt)
            game.shapes.projectionMatrix = stage.camera.combined
            game.sky.draw(game.shapes, stage.viewport.worldWidth, stage.viewport.worldHeight)
        }
        update(dt)
        drawWorld(dt)
        stage.act(dt)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun dispose() = stage.dispose()

    // ------------------------------------------------------------------ Bileşen yardımcıları

    fun label(text: String, style: String = "default", color: Color? = null, wrap: Boolean = false, align: Int = Align.left): Label =
        Label(text, skin, style).also { l ->
            color?.let { l.color = it }
            l.wrap = wrap
            l.setAlignment(align)
        }

    fun button(text: String, style: String = "default", enabled: Boolean = true, onClick: () -> Unit): TextButton {
        val b = TextButton(text, skin, style)
        b.label.wrap = true
        b.isDisabled = !enabled
        b.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (b.isDisabled) { game.audio.play("error", 0.4f); return }
                game.audio.play("tap", 0.5f)
                onClick()
            }
        })
        return b
    }

    fun panel(alt: Boolean = false): Table = Table().also { it.background = if (alt) theme.panelAlt else theme.panel; it.pad(18f) }

    fun scroll(content: Actor): ScrollPane = ScrollPane(content, skin).also {
        it.setScrollingDisabled(true, false)
        it.setFadeScrollBars(true)
        it.setOverscroll(false, true)
    }

    fun resName(r: String) = tr("res.$r")

    fun costText(cost: Map<String, Int>): String =
        if (cost.isEmpty()) tr("common.free") else cost.entries.joinToString("  ") { "${it.value} ${resName(it.key)}" }

    fun fmtDuration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%d sa %02d dk".format(h, m) else "%02d:%02d".format(m, sec)
    }

    /** Başlık satırı: geri butonu, başlık ve kaynak göstergeleri. */
    fun header(title: String, back: Boolean = true): Table {
        val t = Table()
        if (back) t.add(button("←", "smallGhost") { onBack() }).size(84f, 72f).padRight(12f)
        t.add(label(title, "title")).expandX().left()
        root.top()
        root.add(t).growX().pad(16f, 16f, 6f, 16f).row()
        root.add(resourceRow()).growX().padLeft(16f).padRight(16f).padBottom(8f).row()
        return t
    }

    fun resourceRow(res: List<Res> = listOf(Res.GOLD, Res.WOOD, Res.STONE, Res.FOOD, Res.CRYSTAL)): Table {
        val t = Table()
        t.background = theme.panelAlt
        t.pad(6f, 10f, 6f, 10f)
        for (r in res) {
            val chip = Table()
            chip.add(Image(skin.getRegion("circle")).also { it.color = resColor(r) }).size(20f).padRight(4f)
            chip.add(label(shortNumber(core.wallet.get(r)), "small"))
            t.add(chip).expandX()
        }
        resourceBar = t
        return t
    }

    fun refreshResources() {
        val bar = resourceBar ?: return
        val parent = bar.parent as? Table ?: return
        val cell = parent.getCell(bar) ?: return
        cell.setActor(resourceRow())
    }

    fun shortNumber(v: Long): String = when {
        v >= 1_000_000 -> "%.1fM".format(v / 1_000_000.0)
        v >= 10_000 -> "%.1fB".format(v / 1000.0)
        else -> v.toString()
    }

    fun resColor(r: Res): Color = when (r) {
        Res.GOLD -> Palette.gold
        Res.WOOD -> Color.valueOf("A0703F")
        Res.STONE -> Color.valueOf("9AA5B1")
        Res.IRON -> Color.valueOf("6F7C8C")
        Res.FOOD -> Color.valueOf("7CC35A")
        Res.CRYSTAL -> Color.valueOf("B57BFF")
        Res.ENERGY -> Color.valueOf("4FC3F7")
        Res.ANCIENT_SHARD -> Color.valueOf("E0A458")
        Res.STARDUST -> Color.valueOf("FFD54F")
        Res.ROYAL_SEAL -> Color.valueOf("E57373")
    }

    // ------------------------------------------------------------------ Diyalog & bildirim

    fun showDialog(title: String, body: String, vararg buttons: Pair<String, () -> Unit>, extra: Table? = null, dismissible: Boolean = true) {
        closeOverlay()
        val o = Table()
        o.setFillParent(true)
        o.background = theme.tinted("white", Palette.dim)
        o.touchable = Touchable.enabled
        val p = panel()
        p.add(label(title, "title", align = Align.center)).growX().padBottom(12f).row()
        if (body.isNotEmpty()) p.add(label(body, wrap = true, align = Align.center)).width(560f).padBottom(14f).row()
        extra?.let { p.add(it).growX().padBottom(14f).row() }
        val bt = Table()
        for ((i, b) in buttons.withIndex()) {
            bt.add(button(b.first, if (i == 0) "default" else "ghost") { closeOverlay(); b.second() }).growX().height(84f).pad(6f).row()
        }
        if (buttons.isEmpty() && dismissible) bt.add(button(tr("common.close"), "ghost") { closeOverlay() }).growX().height(80f)
        p.add(bt).growX()
        o.add(p).width(640f)
        p.isTransform = true
        p.setOrigin(Align.center)
        p.addAction(Actions.sequence(Actions.scaleTo(0.85f, 0.85f), Actions.scaleTo(1f, 1f, 0.18f, com.badlogic.gdx.math.Interpolation.swingOut)))
        overlay = o
        stage.addActor(o)
    }

    fun closeOverlay() {
        overlay?.remove()
        overlay = null
    }

    val hasOverlay get() = overlay != null

    fun toast(text: String, color: Color = Palette.text) {
        val t = Table()
        t.background = theme.panel
        t.pad(14f, 22f, 14f, 22f)
        t.add(label(text, "small", color, wrap = true, align = Align.center)).width(560f)
        t.pack()
        t.setPosition((stage.viewport.worldWidth - t.width) / 2, 150f)
        t.touchable = Touchable.disabled
        t.color.a = 0f
        t.addAction(Actions.sequence(Actions.fadeIn(0.15f), Actions.delay(1.8f), Actions.fadeOut(0.35f), Actions.removeActor()))
        stage.addActor(t)
    }

    fun error(key: String) {
        game.audio.play("error", 0.5f)
        toast(tr(key), Palette.danger)
    }
}
