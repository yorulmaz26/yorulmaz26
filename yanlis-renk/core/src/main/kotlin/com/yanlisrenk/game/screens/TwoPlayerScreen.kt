package com.yanlisrenk.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.yanlisrenk.game.YanlisRenkGame
import com.yanlisrenk.game.render.Theme
import com.yanlisrenk.game.render.WorldRenderer
import com.yanlisrenk.game.ui.Pal
import com.yanlisrenk.game.ui.Ui
import com.yanlisrenk.logic.level.AreaCatalog
import com.yanlisrenk.logic.run.EndlessGenerator
import com.yanlisrenk.logic.run.RunEvent
import com.yanlisrenk.logic.run.RunWorld
import com.yanlisrenk.logic.run.Track
import com.yanlisrenk.logic.shop.ShopCatalog
import com.yanlisrenk.logic.shop.ShopCategory

/**
 * İki kişilik kapışma (aynı cihazda, yüz yüze).
 * Ekran ikiye bölünür: alt yarı 1. oyuncu, üst yarı 2. oyuncu (180° döndürülmüş, karşıdan okunur).
 * İki oyuncu birebir aynı doğrulanmış sonsuz pistte yarışır; yanlış kapıdan ilk geçen elenir.
 * Can harcanmaz.
 */
class TwoPlayerScreen(game: YanlisRenkGame, private val seed: Long = System.nanoTime(), private val pace: Pace = Pace.RAHAT) : BaseScreen(game) {
    /** Oyun hızı seçeneği: hız çarpanı. */
    enum class Pace(val title: String, val scale: Float) { RAHAT("Rahat", 0.68f), NORMAL("Normal", 0.82f), HIZLI("Hızlı", 1f) }

    private enum class State { READY, COUNTDOWN, PLAYING, PAUSED, ENDED }

    override val music: String = "endless"

    private inner class Player(val index: Int, val renderer: WorldRenderer, val look: WorldRenderer.Look, startColor: Int) {
        val track = Track(gen.speedFor(0))
        val world = RunWorld(track, startColor, endless = true)
        val cam = OrthographicCamera()
        val hud = OrthographicCamera()
        val events = ArrayList<RunEvent>()
        var out = false
        val top get() = index == 1
        val label get() = if (index == 0) "1. OYUNCU" else "2. OYUNCU"
        val tint: Color get() = if (index == 0) Pal.BLUE else Pal.ORANGE
    }

    private val gen = EndlessGenerator.twoPlayer(seed, pace.scale)
    /** Paylaşılan parçalar: iki oyuncu aynı pisti oynar. */
    private val chunks = ArrayList<com.yanlisrenk.logic.model.LevelData>()
    private val chunkStart = ArrayList<Float>()
    private val players: List<Player>
    private val secondRenderer = WorldRenderer(game.assets)
    private var state = State.READY
    private var countdown = 3f
    private var lastBeep = 4
    private var accumulator = 0f
    private var endTimer = 0f
    private var winner = -2 // -2 sürüyor, -1 berabere, 0/1 kazanan
    private var theme: Theme = themeFor(0)
    private val hudBatch = SpriteBatch()
    private val hudShapes = ShapeRenderer()
    private val layout = GlyphLayout()
    private var readyCard: Table? = null

    companion object {
        /** Her yarıda görünen dünya yüksekliği (doğrulayıcının 11 birimlik görüşünü karşılar). */
        /** Her yarıda görünen dünya yüksekliği: büyük görüntü; üretici 8,2 birimlik görüşe göre doğrular. */
        const val HALF_VIEW_H = 10.5f
        const val PLAYER_Y = 1.7f
    }

    init {
        val start = MathUtils.random(3)
        val p1Char = session.equipped(ShopCategory.CHARACTER)
        val p2Char = ShopCatalog.item(if (p1Char.id == "char_klasik") "char_kedi" else "char_klasik")!!
        val s = session.data.settings
        players = listOf(
            Player(0, game.world, WorldRenderer.Look(p1Char, session.equipped(ShopCategory.COSTUME).style, session.equipped(ShopCategory.TRAIL).style, s.colorBlindSymbols, s.reducedMotion), start),
            Player(1, secondRenderer, WorldRenderer.Look(p2Char, 0, 3, s.colorBlindSymbols, s.reducedMotion), start),
        )
        ensureChunks(0f)
        players.forEach { it.renderer.resetRun(it.world.color) }
        root.touchable = Touchable.childrenOnly
    }

    private fun themeFor(chunk: Int): Theme {
        val a = AreaCatalog.area((((chunk / 4) * 5 + 3) % 100 + 1).coerceIn(1, 100))
        return Theme(a, AreaCatalog.world(a.world))
    }

    /** Öndeki oyuncunun 45 birim ilerisine kadar parça üretir ve iki piste de ekler. */
    private fun ensureChunks(leadY: Float) {
        var end = if (chunks.isEmpty()) 0f else chunkStart.last() + chunks.last().length
        while (end < leadY + 45f) {
            val ch = gen.chunk(chunks.size)
            chunks.add(ch); chunkStart.add(end)
            for (p in players) p.track.append(ch, end)
            end += ch.length
        }
    }

    override fun extraInput(): InputProcessor = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (state != State.PLAYING) return false
            // üst yarı (ekran koordinatında y küçük) 2. oyuncu
            val p = if (screenY < Gdx.graphics.height / 2) players[1] else players[0]
            tap(p)
            return true
        }
        override fun keyDown(keycode: Int): Boolean {
            if (state != State.PLAYING) return false
            when (keycode) {
                com.badlogic.gdx.Input.Keys.A, com.badlogic.gdx.Input.Keys.SPACE -> tap(players[0])
                com.badlogic.gdx.Input.Keys.L, com.badlogic.gdx.Input.Keys.ENTER -> tap(players[1])
                else -> return false
            }
            return true
        }
    }

    private fun tap(p: Player) {
        if (p.out) return
        val before = p.world.color
        p.world.tap()
        if (p.world.color != before) {
            game.audio.play("tap", 0.9f + p.world.color * 0.07f + p.index * 0.05f, 0.7f)
            p.renderer.tapEffect(if (p.index == 0) session.equipped(ShopCategory.COLOR_FX).style else 0, p.world.color, p.world.y)
        }
    }

    override fun build() {
        val pause = ui.iconButton("pause", 84f) { pauseGame() }
        root.add(pause).size(84f).expand().left().padLeft(12f)
        showReady()
    }

    private fun showReady() {
        val card = ui.card(pad = 34f)
        card.add(ui.label("İki Kişilik Kapışma", Ui.Size.XL)).padBottom(10f).row()
        card.add(ui.label("Telefonu masaya koyun ve karşılıklı oturun.\nAlt yarı 1. oyuncu, üst yarı 2. oyuncu.\nKendi yarınıza dokunarak renk değiştirin.\nYanlış kapıdan ilk geçen elenir!", Ui.Size.M, Pal.TEXT_SOFT, wrap = true))
            .width(560f).padBottom(14f).row()
        card.add(ui.label("İkiniz de aynı pisti oynarsınız · Can harcanmaz", Ui.Size.S, Pal.BLUE, wrap = true)).width(560f).padBottom(14f).row()
        card.add(ui.label("Hız", Ui.Size.M)).padBottom(6f).row()
        val pr = Table()
        lateinit var ov: Table
        for (pc in Pace.entries) {
            val sel = pc == pace
            pr.add(ui.button(pc.title, if (sel) Pal.ORANGE else Pal.WHITE, size = Ui.Size.S, textColor = if (sel) Pal.WHITE else Pal.TEXT) {
                if (!sel) { ov.remove(); game.go({ TwoPlayerScreen(game, seed, pc) }, remember = false) }
            }).width(180f).pad(4f)
        }
        card.add(pr).padBottom(16f).row()
        card.add(ui.button("BAŞLA", Pal.GREEN, "play") { ov.remove(); readyCard = null; startCountdown() }).width(400f)
        ov = ui.overlay(stage, card, 0.3f)
        readyCard = ov
    }

    private fun startCountdown() { state = State.COUNTDOWN; countdown = 3f; lastBeep = 4 }

    private fun pauseGame() {
        if (state != State.PLAYING && state != State.COUNTDOWN) return
        state = State.PAUSED
        val card = ui.card(pad = 36f)
        lateinit var ov: Table
        card.add(ui.label("DURAKLATILDI", Ui.Size.XL)).padBottom(20f).row()
        card.add(ui.button("Devam Et", Pal.GREEN, "play") { ov.remove(); startCountdown() }).width(420f).padBottom(12f).row()
        card.add(ui.button("Menü", Pal.BLUE, "home") { game.clearHistory(); game.go({ MainMenuScreen(game) }, remember = false) }).width(420f)
        ov = ui.overlay(stage, card)
    }

    override fun pause() { pauseGame() }
    override fun onBack() { if (state == State.PLAYING || state == State.COUNTDOWN) pauseGame() else { game.clearHistory(); game.go({ MainMenuScreen(game) }, remember = false) } }

    private fun step(dt: Float) {
        for (p in players) {
            if (p.out) continue
            p.world.update(dt)
            p.events.clear(); p.world.drainEvents(p.events)
            for (e in p.events) when (e) {
                is RunEvent.GatePassed -> {
                    val gy = p.track.gates[e.gate].y
                    p.renderer.gateEffect(0, e.color, gy, e.ok)
                    if (e.ok) game.audio.play("gate_ok", 1f + p.index * 0.06f, 0.6f)
                    else if (e.shielded) game.audio.play("shield")
                }
                is RunEvent.Coin -> if (e.collected) { p.renderer.coinEffect(p.track.pickups[e.index].y); game.audio.play("coin", 1.1f, 0.5f) }
                is RunEvent.Diamond -> p.renderer.diamondEffect(p.track.pickups[e.index].y)
                is RunEvent.Pad -> p.renderer.tapEffect(2, e.color, p.world.y)
                is RunEvent.Failed -> { p.out = true; game.audio.play("gate_fail"); if (session.data.settings.vibration) game.platform.vibrate(100) }
                else -> {}
            }
        }
        ensureChunks(players.maxOf { it.world.y })
        val outs = players.count { it.out }
        if (outs > 0 && state == State.PLAYING) {
            winner = if (outs == 2) -1 else players.first { !it.out }.index
            state = State.ENDED
            endTimer = 0f
            game.audio.play(if (winner >= 0) "level_complete" else "whoosh")
            session.data.addStat("two_player_games"); session.save()
            val w = players.getOrNull(winner)
            if (w != null) repeat(4) { k -> w.renderer.particles.emit(4.5f, w.world.y + 2f, 14, Pal.game[k], 7f, 0.16f, 1.4f, 3, -5f) }
        }
        val ch = (chunks.size - 3).coerceAtLeast(0)
        val nt = themeFor(ch)
        if (nt.area.id != theme.area.id) theme = nt
    }

    /** QA: hazır kartını kapatıp [seconds] sn ileri sarar; [loser] verilirse o oyuncu dokunmaz. */
    fun debugPlay(seconds: Float, loser: Int = -1) {
        readyCard?.remove(); readyCard = null
        state = State.PLAYING
        var t = 0f
        while (t < seconds && state == State.PLAYING) {
            for (p in players) if (p.index != loser) oracle(p)
            step(1f / 120f); t += 1f / 120f
        }
    }

    private fun oracle(p: Player) {
        val g = p.track.gates.firstOrNull { it.y > p.world.y } ?: return
        if (g.y - p.world.y > 1.2f) return
        val te = p.track.timeline.timeAt(g.y)
        var k = 0
        while (!g.accepts(p.world.color, te, p.world.lastGateColor) && k++ < 4) p.world.tap()
    }

    override fun render(delta: Float) {
        time += delta
        val dt = minOf(delta, 0.05f)
        when (state) {
            State.COUNTDOWN -> {
                countdown -= dt
                val n = kotlin.math.ceil(countdown).toInt()
                if (n != lastBeep && n >= 1) { lastBeep = n; game.audio.play("countdown") }
                if (countdown <= 0f) { state = State.PLAYING; game.audio.play("go") }
            }
            State.PLAYING -> {
                accumulator += dt
                while (accumulator >= 1f / 120f && state == State.PLAYING) { step(1f / 120f); accumulator -= 1f / 120f }
            }
            State.ENDED -> {
                endTimer += dt
                if (endTimer > 1.4f && endTimer - dt <= 1.4f) showResult()
            }
            else -> {}
        }
        for (p in players) p.renderer.update(if (state == State.PAUSED) 0f else dt, p.world)

        val sw = Gdx.graphics.backBufferWidth
        val sh = Gdx.graphics.backBufferHeight
        val half = sh / 2
        Gdx.gl.glClearColor(theme.skyBottom.r, theme.skyBottom.g, theme.skyBottom.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        for (p in players) {
            val vy = if (p.top) sh - half else 0
            Gdx.gl.glViewport(0, vy, sw, half)
            val cam = p.cam
            cam.viewportHeight = HALF_VIEW_H
            cam.viewportWidth = HALF_VIEW_H * sw / half.toFloat()
            cam.up.set(0f, if (p.top) -1f else 1f, 0f)
            cam.direction.set(0f, 0f, -1f)
            // üst oyuncu için görüntü 180° döner: kendi karakteri kendi kenarına yakın olsun
            val camY = p.world.y + HALF_VIEW_H / 2 - PLAYER_Y
            cam.position.set(WorldRenderer.W / 2, camY, 0f)
            cam.update()
            p.renderer.render(cam, p.world, theme, p.look, time, null)
            drawHud(p, sw, half)
        }
        // orta ayırıcı
        Gdx.gl.glViewport(0, 0, sw, sh)
        stage.viewport.apply()
        hudShapes.projectionMatrix = stage.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        hudShapes.begin(ShapeRenderer.ShapeType.Filled)
        val cy = stage.camera.position.y
        val vw = stage.viewport.worldWidth
        hudShapes.setColor(1f, 1f, 1f, 0.95f); hudShapes.rect(stage.camera.position.x - vw / 2, cy - 5f, vw, 10f)
        hudShapes.setColor(Pal.BLUE); hudShapes.rect(stage.camera.position.x - vw / 2, cy - 5f, vw / 2, 4f)
        hudShapes.setColor(Pal.ORANGE); hudShapes.rect(stage.camera.position.x, cy + 1f, vw / 2, 4f)
        hudShapes.end()
        stage.act(delta)
        stage.draw()
    }

    /** Her yarıya, o oyuncunun bakış yönünde skor/geri sayım/sonuç yazar. */
    private fun drawHud(p: Player, sw: Int, half: Int) {
        val hw = 720f
        val hh = hw * half / sw
        val c = p.hud
        c.setToOrtho(false, hw, hh)
        c.up.set(0f, if (p.top) -1f else 1f, 0f); c.direction.set(0f, 0f, -1f)
        c.position.set(hw / 2, hh / 2, 0f)
        c.update()
        hudBatch.projectionMatrix = c.combined
        hudShapes.projectionMatrix = c.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        hudShapes.begin(ShapeRenderer.ShapeType.Filled)
        hudShapes.setColor(1f, 1f, 1f, 0.82f)
        com.yanlisrenk.game.render.Shapes.roundRect(hudShapes, hw - 270f, hh - 92f, 250f, 76f, 30f)
        hudShapes.setColor(p.tint)
        com.yanlisrenk.game.render.Shapes.roundRect(hudShapes, hw - 270f, hh - 92f, 14f, 76f, 7f)
        if (state == State.ENDED || state == State.COUNTDOWN) {
            hudShapes.setColor(1f, 1f, 1f, 0.75f); com.yanlisrenk.game.render.Shapes.roundRect(hudShapes, hw / 2 - 260f, hh / 2 - 90f, 520f, 180f, 40f)
        }
        hudShapes.end()
        hudBatch.begin()
        val fs = ui.font(Ui.Size.S); val fm = ui.font(Ui.Size.L); val fx = ui.font(Ui.Size.HUGE)
        fs.color = Pal.TEXT_SOFT; fs.draw(hudBatch, p.label, hw - 245f, hh - 26f)
        fm.color = Pal.TEXT; fm.draw(hudBatch, "${p.world.gatesPassed} kapı", hw - 245f, hh - 48f)
        val big = when (state) {
            State.COUNTDOWN -> kotlin.math.ceil(countdown).toInt().coerceAtLeast(1).toString()
            State.ENDED -> when {
                winner == -1 -> "BERABERE"
                winner == p.index -> "KAZANDIN!"
                else -> "ELENDİN"
            }
            else -> null
        }
        if (big != null) {
            val f = if (big.length > 2) ui.font(Ui.Size.XL) else fx
            f.color = when { state == State.COUNTDOWN -> Pal.TEXT; winner == p.index -> Pal.GREEN; winner == -1 -> Pal.ORANGE; else -> Pal.RED }
            layout.setText(f, big)
            f.draw(hudBatch, layout, hw / 2 - layout.width / 2, hh / 2 + layout.height / 2)
        }
        fs.color = Color.WHITE; fm.color = Color.WHITE; fx.color = Color.WHITE; ui.font(Ui.Size.XL).color = Color.WHITE
        hudBatch.end()
    }

    private fun showResult() {
        val card = ui.card(pad = 34f)
        val title = when (winner) { -1 -> "Berabere!"; 0 -> "1. Oyuncu kazandı!"; else -> "2. Oyuncu kazandı!" }
        card.add(ui.label(title, Ui.Size.XL, if (winner == -1) Pal.ORANGE else Pal.GREEN)).padBottom(8f).row()
        card.add(ui.label("1. Oyuncu: ${players[0].world.gatesPassed} kapı   ·   2. Oyuncu: ${players[1].world.gatesPassed} kapı", Ui.Size.S, Pal.TEXT_SOFT)).padBottom(18f).row()
        card.add(ui.button("Rövanş", Pal.GREEN, "refresh") { game.go({ TwoPlayerScreen(game, pace = pace) }, remember = false) }).width(420f).padBottom(12f).row()
        card.add(ui.button("Menü", Pal.BLUE, "home") { game.clearHistory(); game.go({ MainMenuScreen(game) }, remember = false) }).width(420f)
        val ov = ui.overlay(stage, card, 0.2f)
        ov.addAction(Actions.fadeIn(0.2f))
    }

    override fun resize(width: Int, height: Int) { super.resize(width, height) }

    override fun dispose() {
        super.dispose()
        secondRenderer.dispose()
        hudBatch.dispose(); hudShapes.dispose()
    }
}
