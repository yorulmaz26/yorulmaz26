package com.yanlisrenk.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.yanlisrenk.game.YanlisRenkGame
import com.yanlisrenk.game.render.CharacterPainter
import com.yanlisrenk.game.render.Shapes
import com.yanlisrenk.game.ui.Pal
import com.yanlisrenk.game.ui.Ui
import com.yanlisrenk.logic.life.formatMs
import com.yanlisrenk.logic.shop.ShopCategory
import com.yanlisrenk.logic.shop.ShopItem
import kotlin.math.sin
import com.yanlisrenk.logic.model.Mechanic as M

/** Tüm menü ekranlarının temeli: 720x1280 sanal çözünürlüklü sahne, tema arka planı, üst çubuk. */
abstract class BaseScreen(val game: YanlisRenkGame) : ScreenAdapter() {
    val ui: Ui get() = game.ui
    val session get() = game.session
    val stage = Stage(ExtendViewport(720f, 1280f))
    val root = Table()
    protected val bgShapes = ShapeRenderer()
    protected var time = 0f
    /** Geri tuşuyla bu ekrana dönmek için fabrika (null → geçmişe eklenmez). */
    open val recreate: (() -> BaseScreen)? = null
    open val music: String = "menu"
    private var livesLabel: Label? = null
    private var timerLabel: Label? = null
    private var goldLabel: Label? = null
    private var diaLabel: Label? = null
    private var heartTable: Table? = null
    private val dots = Array(14) { floatArrayOf(MathUtils.random(720f), MathUtils.random(1280f), MathUtils.random(10f, 26f), MathUtils.random(3).toFloat(), MathUtils.random(10f, 30f)) }

    init {
        root.setFillParent(true)
        stage.addActor(root)
    }

    override fun show() {
        val mux = InputMultiplexer(stage, object : InputAdapter() {
            override fun keyDown(keycode: Int): Boolean {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) { onBack(); return true }
                return false
            }
        })
        extraInput()?.let { mux.addProcessor(it) }
        Gdx.input.inputProcessor = mux
        game.audio.music(music)
        build()
        root.color.a = 0f
        root.addAction(Actions.fadeIn(0.18f))
    }

    open fun extraInput(): com.badlogic.gdx.InputProcessor? = null

    /** Ekran içeriğini kurar. */
    abstract fun build()

    open fun onBack() = game.back()

    open fun onLivesChanged() { refreshTopBar() }

    /** Tema paketine göre menü arka plan renkleri. */
    fun themeColors(): Pair<Color, Color> = when (session.equipped(ShopCategory.THEME).style) {
        1 -> Pal.hex(0xFFC6DD) to Pal.hex(0xFFF5FA)
        2 -> Pal.hex(0xA8EDD3) to Pal.hex(0xF3FFF9)
        3 -> Pal.hex(0xFFC38A) to Pal.hex(0xFFF6E8)
        4 -> Pal.hex(0xD3C4FF) to Pal.hex(0xF8F5FF)
        5 -> Pal.hex(0xFFE680) to Pal.hex(0xFFFDEB)
        else -> Pal.hex(0x87CEFA) to Pal.hex(0xF8FBFF)
    }

    /** Varsayılan temada menü arka plan görseli (kullanıcı görseli). */
    open val backgroundImage: String?
        get() = if (session.equipped(ShopCategory.THEME).style == 0) "ui2/menu_bg.jpg" else null

    open fun drawBackground(delta: Float) {
        val img = backgroundImage?.let { game.assets.background(it) }
        if (img != null) {
            val cam = stage.camera
            val w = stage.viewport.worldWidth
            val h = stage.viewport.worldHeight
            val b = stage.batch
            b.projectionMatrix = cam.combined
            b.begin()
            b.setColor(1f, 1f, 1f, 1f)
            val ratio = img.height.toFloat() / img.width
            var bw = w * 1.04f
            var bh = bw * ratio
            if (bh < h * 1.04f) { bh = h * 1.04f; bw = bh / ratio }
            val drift = if (session.data.settings.reducedMotion) 0f else sin(time * 0.25f) * 10f
            b.draw(img, cam.position.x - bw / 2, cam.position.y - bh / 2 + drift, bw, bh)
            b.end()
            return
        }
        val (top, bottom) = themeColors()
        val cam = stage.camera
        bgShapes.projectionMatrix = cam.combined
        val w = stage.viewport.worldWidth
        val h = stage.viewport.worldHeight
        val x0 = cam.position.x - w / 2
        val y0 = cam.position.y - h / 2
        Gdx.gl.glEnable(GL20.GL_BLEND)
        bgShapes.begin(ShapeRenderer.ShapeType.Filled)
        bgShapes.rect(x0, y0, w, h, bottom, bottom, top, top)
        if (!session.data.settings.reducedMotion) for (d in dots) d[1] = (d[1] + delta * d[4]) % (h + 60f)
        for (d in dots) {
            val c = Pal.game[d[3].toInt()]
            bgShapes.setColor(c.r, c.g, c.b, 0.16f)
            val x = x0 + d[0] * w / 720f + sin(time * 0.5f + d[2]) * 12f
            val y = y0 + d[1] - 30f
            when (d[3].toInt()) {
                0 -> bgShapes.circle(x, y, d[2], 20)
                1 -> Shapes.roundRect(bgShapes, x - d[2], y - d[2], d[2] * 2, d[2] * 2, d[2] * 0.4f)
                2 -> bgShapes.triangle(x - d[2], y - d[2] * 0.8f, x + d[2], y - d[2] * 0.8f, x, y + d[2])
                else -> Shapes.star(bgShapes, x, y, d[2] * 1.1f, d[2] * 0.5f)
            }
        }
        bgShapes.end()
    }

    override fun render(delta: Float) {
        time += delta
        Gdx.gl.glClearColor(0.97f, 0.98f, 1f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.viewport.apply()
        drawBackground(delta)
        stage.act(delta)
        stage.draw()
        timerLabel?.let { updateTimer() }
        afterDraw(delta)
    }

    open fun afterDraw(delta: Float) {}

    override fun resize(width: Int, height: Int) { stage.viewport.update(width, height, true) }

    override fun dispose() { stage.dispose(); bgShapes.dispose() }

    // ---------- ortak parçalar ----------

    /** Üst çubuk: geri butonu, başlık, canlar ve para. */
    fun topBar(title: String?, back: Boolean = true, showLives: Boolean = true): Table {
        val bar = Table()
        bar.pad(16f, 20f, 8f, 20f)
        if (back) bar.add(ui.iconButton("back", 84f, null) { onBack() }).size(84f).padRight(8f)
        if (showLives) {
            val lt = Table()
            heartTable = Table()
            lt.add(heartTable).left().row()
            timerLabel = ui.label("", Ui.Size.S, Pal.TEXT_SOFT)
            lt.add(timerLabel).left().padLeft(6f)
            bar.add(lt).left()
            fillHearts()
        }
        bar.add().expandX()
        goldLabel = ui.label(session.gold.toString(), Ui.Size.M)
        diaLabel = ui.label(session.diamonds.toString(), Ui.Size.M)
        bar.add(chip("coin", goldLabel!!)).padRight(10f)
        bar.add(chip("diamond", diaLabel!!))
        val wrap = Table()
        wrap.add(bar).growX().row()
        if (title != null) wrap.add(ui.label(title, Ui.Size.XL)).padTop(4f).padBottom(8f)
        return wrap
    }

    private fun chip(icon: String, l: Label): Table {
        val t = Table(); t.background = ui.pill(Pal.WHITE); t.pad(4f, 8f, 4f, 20f)
        t.add(ui.icon(icon, 46f)).size(46f).padRight(6f); t.add(l)
        return t
    }

    private fun fillHearts() {
        val ht = heartTable ?: return
        ht.clear()
        val lives = session.lives.lives
        for (i in 0 until 4) ht.add(ui.icon(if (i < lives) "heart_full" else "heart_empty", 48f)).size(48f).padRight(2f)
        ht.add(ui.label("$lives/4", Ui.Size.S)).padLeft(6f)
        ui.onTap(ht) { if (this !is LivesScreen) game.go({ LivesScreen(game) }) }
    }

    private fun updateTimer() {
        val l = session.lives
        timerLabel?.setText(if (l.isFull) "Canlar dolu" else "Sonraki can: ${formatMs(l.nextLifeIn)}")
        if (heartTable != null && heartTable!!.children.size > 0) {
            val shown = heartTable!!.children.count { (it as? Image)?.drawable == null }.let { 0 }
            if (shown < 0) fillHearts()
        }
    }

    fun refreshTopBar() {
        fillHearts()
        goldLabel?.setText(session.gold.toString())
        diaLabel?.setText(session.diamonds.toString())
    }

    /** Başarı bildirimleri. */
    fun flushAchievementToasts() {
        val list = session.pendingAchievementToasts
        list.forEachIndexed { i, a ->
            stage.addAction(Actions.delay(i * 2.4f, Actions.run { game.audio.play("achievement"); ui.toast(stage, "Başarı: ${a.title}", "trophy") }))
        }
        list.clear()
    }
}

/** Karakter önizleme aktörü (vektör çizim). */
class CharacterActor(private val game: YanlisRenkGame, var item: ShopItem, var costume: Int, var colorIndex: Int = 0, var cycleColors: Boolean = false) : Actor() {
    private val sr get() = game.world.sr
    private var t = MathUtils.random(10f)
    private var squash = 0f
    var locked = false

    init {
        game.ui.onTap(this) { squash = 1f; colorIndex = (colorIndex + 1) % 4; game.audio.play("tap") }
    }

    override fun act(delta: Float) { super.act(delta); t += delta; squash = maxOf(0f, squash - delta * 5f); if (cycleColors && ((t * 0.7f) % 1f) < delta * 0.7f) colorIndex = (colorIndex + 1) % 4 }

    override fun draw(batch: Batch, parentAlpha: Float) {
        batch.end()
        Gdx.gl.glEnable(GL20.GL_BLEND)
        sr.projectionMatrix = batch.projectionMatrix
        sr.transformMatrix = batch.transformMatrix
        sr.begin(ShapeRenderer.ShapeType.Filled)
        val c = if (locked) Pal.hex(0xC5CCDA) else Pal.game[colorIndex]
        CharacterPainter.drawAny(sr, game.world.batch, item, if (locked) 0 else costume, Color(c.r, c.g, c.b, parentAlpha * color.a), x + width / 2, y + height * 0.42f, width * 0.55f, t, squash,
            idleAnim = !game.session.data.settings.reducedMotion)
        sr.end()
        sr.transformMatrix.idt()
        batch.begin()
    }
}

/** Mekanik anlatım çizimi: küçük bir kapı + karakter. */
class MechanicActor(private val game: YanlisRenkGame, val mechanic: com.yanlisrenk.logic.model.Mechanic) : Actor() {
    private var t = 0f
    override fun act(delta: Float) { super.act(delta); t += delta }
    override fun draw(batch: Batch, parentAlpha: Float) {
        val sr = game.world.sr
        batch.end()
        Gdx.gl.glEnable(GL20.GL_BLEND)
        sr.projectionMatrix = batch.projectionMatrix
        sr.transformMatrix = batch.transformMatrix
        sr.begin(ShapeRenderer.ShapeType.Filled)
        val cx = x + width / 2
        val gy = y + height * 0.7f
        val gw = width * 0.86f
        val gh = height * 0.2f
        val dark = Pal.TEXT
        sr.color = dark; Shapes.roundRect(sr, cx - gw / 2 - 4, gy - gh / 2 - 4, gw + 8, gh + 8, gh / 2 + 4)
        val cols: List<Int> = when (mechanic) {
            M.RAINBOW -> listOf(0, 1, 2); M.MIX -> listOf(1, 3); M.NONE -> listOf(0, 2)
            M.ROTATE, M.TIMED, M.SLIDE -> listOf(((t / 1.2f).toInt()) % 4)
            else -> listOf(1)
        }
        val sw = gw / cols.size
        cols.forEachIndexed { k, c ->
            sr.color = if (mechanic == M.MEMORY && (t % 3f) > 1.5f || mechanic == M.CHAIN) Pal.hex(0xC7D0DD) else Pal.game[c]
            if (mechanic == M.DECOY) sr.color = Pal.hex(0xE6EAF2)
            Shapes.roundRect(sr, cx - gw / 2 + k * sw, gy - gh / 2, sw, gh, if (cols.size == 1) gh / 2 else 6f)
        }
        if (mechanic == M.HIDDEN || mechanic == M.FLICKER) { sr.setColor(1f, 1f, 1f, if (mechanic == M.FLICKER) (if ((t % 0.9f) < 0.5f) 0f else 0.9f) else 0.6f + 0.4f * sin(t * 2f)); Shapes.roundRect(sr, cx - gw / 2, gy - gh / 2, gw, gh, gh / 2) }
        // karakter
        val stepColor = when (mechanic) { M.DIFF, M.NONE -> 3; M.NEXT -> 2; M.PREV -> 0; M.MIX -> 3; else -> 1 }
        val chY = y + height * 0.22f + ((t * 0.5f) % 1f) * height * 0.15f
        CharacterPainter.drawAny(sr, game.world.batch, com.yanlisrenk.logic.shop.ShopCatalog.characters[0], 0, Pal.game[stepColor], cx, chY, height * 0.26f, t)
        sr.end()
        sr.transformMatrix.idt()
        batch.begin()
        val glyph = when (mechanic) {
            M.DIFF, M.NONE -> "sym_notequal"; M.NEXT -> "sym_next"; M.PREV -> "sym_prev"; M.REPEAT -> "sym_repeat"
            M.MEMORY, M.CHAIN -> "sym_question"; M.STROOP -> "sym_word"; M.DECOY -> null
            M.LOCK -> "lock"; M.SPEED, M.WAVE, M.QUICK -> "bolt"; M.MIRROR -> "refresh"; M.FOG -> "info"; M.COINS -> "coin"
            M.TIMED -> "clock"; else -> "sym_equal"
        }
        if (glyph != null) {
            val s = gh * 1.25f
            batch.setColor(1f, 1f, 1f, parentAlpha)
            batch.draw(game.assets.region(glyph), cx - s / 2, gy - s / 2, s, s)
        }
    }
}

/** Ortalanmış kart içinde başlık + içerik düzeni için yardımcı. */
fun Table.header(ui: Ui, text: String, size: Ui.Size = Ui.Size.L, color: Color = Pal.TEXT) {
    add(ui.label(text, size, color, Align.left)).left().padBottom(10f).row()
}
