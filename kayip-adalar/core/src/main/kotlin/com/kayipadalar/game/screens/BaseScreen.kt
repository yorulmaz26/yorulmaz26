package com.kayipadalar.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Scaling
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.kayipadalar.game.KayipAdalarGame
import com.kayipadalar.game.ui.Palette
import com.kayipadalar.logic.kingdom.Res
import kotlin.math.roundToInt

const val VW = 720f
const val VH = 1280f

/** Tüm ekranların tabanı: gökyüzü, sahne, butonlar, diyaloglar, alt paneller ve bildirimler. */
abstract class BaseScreen(val app: KayipAdalarGame) : ScreenAdapter() {
    val stage = Stage(ExtendViewport(VW, VH), app.batch)
    val root = Table()
    val theme get() = app.theme
    val skin get() = app.theme.skin
    val k get() = app.k
    val assets get() = app.assets
    private var overlay: Table? = null
    open val drawSky = true

    private val keys = object : InputAdapter() {
        override fun keyDown(keycode: Int): Boolean {
            if (keycode != Input.Keys.BACK && keycode != Input.Keys.ESCAPE) return false
            if (overlay != null) closeOverlay() else onBack()
            return true
        }
    }

    abstract fun build()
    open fun onBack() = app.back()
    open fun onResume() {}
    open fun update(dt: Float) {}

    fun rebuild() {
        overlay = null
        stage.clear(); root.clear()
        root.setFillParent(true)
        stage.addActor(root)
        build()
    }

    override fun show() {
        Gdx.input.inputProcessor = InputMultiplexer(stage, keys)
        app.audio.updateMusic()
        rebuild()
    }

    override fun render(delta: Float) {
        val dt = delta.coerceAtMost(0.05f)
        Gdx.gl.glClearColor(0.85f, 0.95f, 1f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.viewport.apply()
        if (drawSky && app.assets.manager.isFinished) {
            app.sky.update(dt, stage.viewport.worldWidth)
            app.batch.projectionMatrix = stage.camera.combined
            app.batch.begin()
            app.sky.draw(app.batch, theme.white, stage.viewport.worldWidth, stage.viewport.worldHeight)
            app.batch.end()
        }
        update(dt)
        stage.act(dt)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) = stage.viewport.update(width, height, true)
    override fun dispose() = stage.dispose()

    // ------------------------------------------------------------------ Bileşenler

    fun label(text: String, style: String = "default", color: Color? = null, wrap: Boolean = false, align: Int = Align.left): Label =
        Label(text, skin, style).also { l -> color?.let { l.color = it }; l.wrap = wrap; l.setAlignment(align) }

    fun sprite(name: String, w: Float, h: Float = w): Image =
        Image(assets[name]).also { it.setScaling(Scaling.fit); it.setSize(w, h) }

    fun button(text: String, style: String = "default", enabled: Boolean = true, icon: String? = null, iconSize: Float = 44f, onClick: () -> Unit): Button {
        val b = TextButton(text, skin, style)
        b.label.wrap = text.length > 8
        if (icon != null) {
            val img = Image(assets[icon]).also { it.setScaling(Scaling.fit) }
            b.clearChildren()
            b.add(img).size(iconSize).padLeft(6f).padRight(6f)
            b.add(b.label).growX()
        }
        b.isDisabled = !enabled
        b.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (b.isDisabled) { app.audio.play("error", 0.4f); return }
                app.audio.play("tap", 0.5f)
                onClick()
            }
        })
        return b
    }

    /** Altında yazısı olan büyük ikon butonu (alt menü). */
    fun navButton(text: String, icon: String, badge: Boolean = false, onClick: () -> Unit): Table {
        val t = Table()
        t.touchable = Touchable.enabled
        val stack = Stack()
        val bg = Table().also { it.background = theme.patch("navy") }
        stack.add(bg)
        stack.add(Table().also { it.add(sprite(icon, 64f)).size(64f) })
        if (badge) stack.add(Table().also { it.top().right(); it.add(sprite("icons/alert", 28f)).size(28f) })
        t.add(stack).size(96f, 84f).row()
        t.add(label(text, "lightSmall", align = Align.center))
        t.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) { app.audio.play("tap", 0.5f); onClick() }
        })
        return t
    }

    fun parchment(pad: Float = 18f): Table = Table().also { it.background = theme.patch("parchment"); it.pad(pad) }

    fun progressBar(gold: Boolean = false, height: Float = 18f, value: () -> Float) = com.kayipadalar.game.view.Bar(theme, gold, height, value)

    fun costRow(cost: Map<Res, Int>, style: String = "small", have: (Res) -> Double): Table {
        val t = Table()
        for ((r, v) in cost) {
            if (v <= 0) continue
            t.add(sprite(r.icon, 30f)).size(30f).padRight(3f)
            t.add(label(fmt(v.toLong()), style, if (have(r) >= v) Palette.ink else Palette.red)).padRight(12f)
        }
        return t
    }

    fun fmt(v: Long): String = when {
        v >= 1_000_000 -> "%.1fM".format(v / 1_000_000.0)
        v >= 10_000 -> "%.1fB".format(v / 1000.0)
        else -> v.toString()
    }

    fun fmtTime(sec: Long): String {
        val s = sec.coerceAtLeast(0)
        return when {
            s >= 3600 -> "%dsa %02ddk".format(s / 3600, (s % 3600) / 60)
            s >= 60 -> "%ddk %02dsn".format(s / 60, s % 60)
            else -> "${s}sn"
        }
    }

    // ------------------------------------------------------------------ Katmanlar

    val hasOverlay get() = overlay != null

    fun closeOverlay() { overlay?.remove(); overlay = null }

    private fun installOverlay(o: Table) {
        closeOverlay()
        o.setFillParent(true)
        o.touchable = Touchable.enabled
        overlay = o
        stage.addActor(o)
    }

    /** Ortada açılan diyalog. [portrait] verilirse danışman konuşması olarak gösterilir. */
    fun dialog(title: String, body: String, portrait: String? = null, extra: Actor? = null, vararg buttons: Pair<String, () -> Unit>) {
        val o = Table()
        o.background = theme.tinted("white", Palette.dim)
        val p = parchment(22f)
        val head = Table()
        if (portrait != null) head.add(Image(assets[portrait]).also { it.setScaling(Scaling.fit) }).size(110f, 150f).padRight(14f)
        val txt = Table()
        txt.add(label(title, "title", wrap = true)).growX().left().row()
        if (body.isNotEmpty()) txt.add(label(body, "default", wrap = true)).growX().left().padTop(6f)
        head.add(txt).growX()
        p.add(head).growX().row()
        extra?.let { p.add(it).growX().padTop(10f).row() }
        val row = Table()
        for ((i, b) in buttons.withIndex()) row.add(button(b.first, if (i == 0) "green" else "default") { closeOverlay(); b.second() }).growX().height(80f).pad(6f)
        if (buttons.isEmpty()) row.add(button("Tamam", "green") { closeOverlay() }).growX().height(80f).pad(6f)
        p.add(row).growX().padTop(10f)
        o.add(p).width(660f)
        p.isTransform = true; p.setOrigin(Align.center); p.pack()
        p.addAction(Actions.sequence(Actions.scaleTo(0.85f, 0.85f), Actions.scaleTo(1f, 1f, 0.2f, Interpolation.swingOut)))
        installOverlay(o)
    }

    /** Ekranın altından açılan panel (bina, ordu, araştırma...). */
    fun sheet(title: String, content: Actor, icon: String? = null) {
        val o = Table()
        o.bottom()
        o.background = theme.tinted("white", Color(0f, 0f, 0f, 0.25f))
        o.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) { if (event?.target === o) closeOverlay() }
        })
        val p = parchment(16f)
        p.touchable = Touchable.enabled
        val head = Table()
        icon?.let { head.add(sprite(it, 72f)).size(72f).padRight(10f) }
        head.add(label(title, "title", wrap = true)).growX().left()
        head.add(button("✗", "smallRed") { closeOverlay() }).size(64f)
        p.add(head).growX().row()
        val sc = ScrollPane(content, skin).also { it.setScrollingDisabled(true, false); it.setFadeScrollBars(true) }
        p.add(sc).growX().maxHeight(stage.viewport.worldHeight * 0.62f).padTop(8f)
        o.add(p).growX().pad(0f, 8f, 8f, 8f)
        p.setPosition(0f, -600f)
        p.addAction(Actions.moveBy(0f, 0f))
        installOverlay(o)
        p.color.a = 0f
        p.addAction(Actions.fadeIn(0.15f))
    }

    fun toast(text: String, color: Color = Palette.ink) {
        val t = Table()
        t.background = theme.patch("parchment")
        t.pad(12f, 20f, 12f, 20f)
        t.add(label(text, "small", color, wrap = true, align = Align.center)).width(560f)
        t.pack()
        t.setPosition((stage.viewport.worldWidth - t.width) / 2, stage.viewport.worldHeight * 0.30f)
        t.touchable = Touchable.disabled
        t.color.a = 0f
        t.addAction(Actions.sequence(Actions.fadeIn(0.12f), Actions.delay(2.0f), Actions.fadeOut(0.3f), Actions.removeActor()))
        stage.addActor(t)
    }

    open fun afterBattle() {}

    /** Savaş animasyonunu gösterir, ardından sonuç panelini açar. */
    fun battle(rep: com.kayipadalar.logic.kingdom.BattleReport, ours: List<String>, theirs: List<String>, after: (() -> Unit)?) {
        closeOverlay()
        val o = Table()
        o.setFillParent(true)
        o.background = theme.tinted("white", Color(0.1f, 0.15f, 0.25f, 0.75f))
        o.touchable = Touchable.enabled
        val scene = com.kayipadalar.game.view.BattleScene(assets, ours, theirs, rep.win) {
            app.audio.play(if (rep.win) "win" else "lose", 0.8f)
            showReport(rep, after)
        }
        o.add(label("Ordun ${rep.armyPower}  ×  ${rep.enemyPower} Düşman", "title")).padBottom(10f).row()
        o.add(scene).size(stage.viewport.worldWidth, 520f)
        stage.addActor(o)
        app.audio.play("hit", 0.8f)
        o.addAction(Actions.sequence(Actions.delay(2.5f), Actions.removeActor()))
    }

    private fun showReport(rep: com.kayipadalar.logic.kingdom.BattleReport, after: (() -> Unit)?) {
        val t = Table()
        if (rep.loot.isNotEmpty()) {
            val pos = rep.loot.filterValues { it > 0 }; val neg = rep.loot.filterValues { it < 0 }.mapValues { -it.value }
            if (pos.isNotEmpty()) { t.add(label("Ganimet:", "small")).left().row(); t.add(costRow(pos) { Double.MAX_VALUE }).left().row() }
            if (neg.isNotEmpty()) { t.add(label("Yağmalanan:", "small", Palette.red)).left().row(); t.add(costRow(neg) { Double.MAX_VALUE }).left().row() }
        }
        if (rep.lost.isNotEmpty()) t.add(label("Kayıplar: " + rep.lost.entries.joinToString { "${k.cfg.unit(it.key).name} ${it.value}" }, "small", Palette.red, wrap = true)).growX().left().row()
        app.game.persist()
        afterBattle()
        dialog(rep.title, if (rep.win) "Ordun ${rep.armyPower} güçle ${rep.enemyPower} güçlü düşmanı yendi." else "Düşman (${rep.enemyPower}) ordundan (${rep.armyPower}) güçlüydü. Daha fazla asker eğit veya Kışla'yı yükselt.",
            if (rep.win) "portraits/commander" else "portraits/scholar", t, "Tamam" to { after?.invoke() })
    }


    fun error(msg: String) { app.audio.play("error", 0.5f); toast(msg, Palette.red) }

    fun amountFor(r: Res): Long = k.res(r).roundToInt().toLong()
}
