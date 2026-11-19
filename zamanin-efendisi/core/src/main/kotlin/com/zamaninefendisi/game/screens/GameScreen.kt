package com.zamaninefendisi.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.zamaninefendisi.game.ZamaninEfendisiGame
import com.zamaninefendisi.game.render.IsoLayout
import com.zamaninefendisi.game.render.IsoRenderer
import com.zamaninefendisi.game.render.Palette
import com.zamaninefendisi.game.render.WorldAnim
import com.zamaninefendisi.game.ui.Theme
import com.zamaninefendisi.game.ui.formatTime
import com.zamaninefendisi.logic.Action
import com.zamaninefendisi.logic.Era
import com.zamaninefendisi.logic.Feature
import com.zamaninefendisi.logic.GameEvent
import com.zamaninefendisi.logic.JournalKind
import com.zamaninefendisi.logic.LevelDef
import com.zamaninefendisi.logic.LevelSession
import com.zamaninefendisi.logic.Pos
import com.zamaninefendisi.logic.SessionStatus
import com.zamaninefendisi.logic.levels.LevelCatalog
import java.util.ArrayDeque
import java.util.EnumMap

/** Oynanış ekranı: izometrik harita, zaman geçiş arayüzü, hedefler, eylem menüsü ve sonuç pencereleri. */
class GameScreen(
    game: ZamaninEfendisiGame,
    val level: LevelDef,
    initialActions: List<Action>,
    /** Geliştirici otomatik oynatma: eylemler arayüzün kendi yolundan sırayla uygulanır. */
    autoplay: List<Action> = emptyList(),
) : BaseScreen(game) {
    private var session = LevelSession.replay(level, initialActions)
    private val renderer = IsoRenderer(game.shapes)
    private val anim = WorldAnim().also { it.snap(session) }
    private var layout = IsoLayout(0f, 0f, 60f)
    private val queue = ArrayDeque<Action>()
    private var queueTimer = 0f
    private var recorded = false
    private val floats = ArrayList<FloatText>()
    private val flashes = EnumMap<Era, HashMap<Pos, Float>>(Era::class.java)
    private val tmpV = Vector2()

    override val skyTop: Color get() = Theme.skyTop(session.heroEra)
    override val skyBottom: Color get() = Theme.skyBottom(session.heroEra)

    // Arayüz
    private val topBar = Table()
    private val statsLabel = ui.label("", assets.fontS, Theme.ink)
    private val goalsTable = Table()
    private val hintLabel = ui.label("", assets.fontS, Theme.ink, wrap = true)
    private val hintBox = ui.panel(Theme.panelWarm, small = true)
    private val eraBar = Table()
    private val eraButtons = EnumMap<Era, TextButton>(Era::class.java)
    private val actionRow = Table()
    private var popup: Table? = null
    private lateinit var undoButton: TextButton

    private class FloatText(val text: String, val x: Float, var y: Float, val color: Color, var life: Float = 1.3f)

    init {
        queue.addAll(autoplay)
        buildUi()
        refreshHud()
        if (session.status != SessionStatus.PLAYING) onFinished()
    }

    // ------------------------------------------------------------------ Arayüz kurulumu

    private fun buildUi() {
        root.top()
        val bar = ui.panel(Theme.panel)
        bar.pad(10f, 14f, 10f, 14f)
        bar.add(ui.button("Menü", Theme.inkSoft, assets.fontS) { showPause() }).size(110f, 64f)
        val title = Table()
        title.add(ui.label("Bölüm ${level.id} · ${level.name}", assets.fontM, Theme.ink, align = Align.center)).row()
        title.add(statsLabel).padTop(2f)
        bar.add(title).expandX()
        undoButton = ui.button("Geri Al", Theme.blue, assets.fontS) { undo() }
        bar.add(undoButton).size(120f, 64f)
        topBar.add(bar).growX().row()
        root.add(topBar).width(688f).pad(16f, 16f, 6f, 16f).row()
        val goalsBox = ui.panel(Color(1f, 1f, 1f, 0.82f), small = true)
        goalsBox.pad(8f, 16f, 8f, 16f)
        goalsBox.add(goalsTable).growX()
        root.add(goalsBox).width(688f).row()
        hintBox.pad(8f, 16f, 8f, 16f)
        hintBox.add(hintLabel).width(640f)
        root.add(hintBox).padTop(8f).row()
        root.add().expandY().row()

        actionRow.defaults().height(72f).pad(0f, 6f, 0f, 6f)
        root.add(actionRow).padBottom(10f).row()
        eraBar.defaults().pad(0f, 6f, 0f, 6f)
        for (era in level.eras) {
            val b = ui.button(era.title, Theme.eraColor(era), assets.fontM) { requestEra(era) }
            b.label.setAlignment(Align.bottom)
            b.padBottom(14f)
            eraButtons[era] = b
            eraBar.add(b).size(if (level.eras.size == 2) 300f else 215f, 170f)
        }
        root.add(eraBar).padBottom(26f)
    }

    private fun refreshHud() {
        val s = session
        val parts = ArrayList<String>()
        parts += "Tur ${s.turn}/${level.maxTurns}"
        parts += "Eylem ${s.actionsUsed}/${level.par}"
        parts += "Odun ${s.wood}"
        if (level.hasEnemies) parts += "Can ${s.heroHp}"
        s.energy?.let { parts += "Enerji $it" }
        statsLabel.setText(parts.joinToString(" · "))
        goalsTable.clearChildren()
        val met = s.goalsMet()
        for ((i, g) in level.goals.withIndex()) {
            val ok = met[i]
            goalsTable.add(ui.label(if (ok) "✓" else "•", assets.fontS, if (ok) Theme.green else Theme.orange)).width(26f).top()
            goalsTable.add(ui.label(g.text, assets.fontS, if (ok) Theme.green else Theme.ink, wrap = true)).width(620f).left().row()
        }
        if (level.hasEnemies && level.protect.isNotEmpty()) {
            goalsTable.add(ui.label("!", assets.fontS, Theme.purple)).width(26f)
            goalsTable.add(ui.label("Akıncılar mor çerçeveli hücreye ulaşırsa kaybedersin", assets.fontS, Theme.purple, wrap = true)).width(620f).left().row()
        }
        val hint = if (game.progression.data.settings.hints) s.currentHint() else null
        hintBox.isVisible = hint != null && s.status == SessionStatus.PLAYING
        hintLabel.setText(hint ?: "")
        for ((era, b) in eraButtons) {
            val here = era == s.heroEra
            b.isDisabled = s.status != SessionStatus.PLAYING
            b.color.a = if (here) 1f else 0.85f
        }
        actionRow.clearChildren()
        if (level.hasEnemies) actionRow.add(ui.button("Bekle", Theme.purple, assets.fontS) { perform(Action.Wait) }).width(150f)
        actionRow.add(ui.button("Günlük", Theme.teal, assets.fontS) { showJournal() }).width(150f)
        actionRow.add(ui.button("Yeniden", Theme.orange, assets.fontS) { restart() }).width(150f)
        undoButton.isDisabled = s.actions.isEmpty() || s.status != SessionStatus.PLAYING
    }

    // ------------------------------------------------------------------ Girdi

    override fun extraInput(): InputProcessor = object : InputAdapter() {
        private var downX = 0; private var downY = 0
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean { downX = screenX; downY = screenY; return true }
        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (Math.abs(screenX - downX) > 25 || Math.abs(screenY - downY) > 25) return false
            tmpV.set(screenX.toFloat(), screenY.toFloat())
            stage.screenToStageCoordinates(tmpV)
            onWorldTap(layout.pick(tmpV.x, tmpV.y + 0f))
            return true
        }
    }

    private fun onWorldTap(p: Pos) {
        closePopup()
        if (session.status != SessionStatus.PLAYING || queue.isNotEmpty()) return
        if (!session.inBounds(p)) { anim.selected = null; return }
        anim.selected = p
        val opts = session.options(p)
        val dist = p.manhattan(session.heroPos)
        if (dist > 1) {
            val path = session.pathTo(p)
            if (path != null && path.isNotEmpty()) { path.forEach { queue.add(Action.Move(it)) }; anim.selected = null; return }
            showCellInfo(p, opts)
            return
        }
        val enabled = opts.filter { it.enabled }
        if (opts.size == 1 && enabled.size == 1 && enabled[0].action is Action.Move) { perform(enabled[0].action); anim.selected = null; return }
        showCellInfo(p, opts)
    }

    private fun requestEra(era: Era) {
        closePopup()
        if (era == session.heroEra) { ui.toast(root, "Zaten ${era.locative}sın. Haritaya dokunarak hareket et."); return }
        perform(Action.SwitchEra(era))
    }

    // ------------------------------------------------------------------ Eylemler

    private fun perform(a: Action): Boolean {
        val err = session.validate(a)
        if (err != null) {
            ui.toast(root, err, Theme.red)
            queue.clear()
            return false
        }
        val events = session.perform(a)
        handleEvents(events)
        refreshHud()
        if (session.status != SessionStatus.PLAYING) { queue.clear(); onFinished() } else saveProgress()
        return true
    }

    private fun handleEvents(events: List<GameEvent>) {
        for (e in events) when (e) {
            is GameEvent.HeroMoved -> game.audio.play("move", 0.35f)
            is GameEvent.EraSwitched -> {
                anim.startTransition(session.heroPos)
                anim.snap(session)
                game.audio.play("switch")
            }
            is GameEvent.CellsChanged -> {
                var grew = false
                for ((era, cells) in e.changed) for (p in cells) {
                    if (era == session.heroEra) anim.pops[p] = 0.35f
                    else { flashes.getOrPut(era) { HashMap() }[p] = 1.2f; grew = true }
                }
                game.audio.play("build")
                if (grew) game.audio.play("grow", 0.4f)
            }
            is GameEvent.WoodChanged -> floatAtHero(if (e.delta > 0) "+${e.delta} odun" else "${e.delta} odun", if (e.delta > 0) Theme.green else Theme.orange)
            is GameEvent.EnemyDefeated -> {
                anim.fading += floatArrayOf(e.at.x.toFloat(), e.at.y.toFloat(), 0.6f)
                if (e.trapped) floatAt(e.at, "Zaman tuzağı!", Theme.purple) else floatAt(e.at, "Yenildi!", Theme.green)
                game.audio.play("hit")
            }
            is GameEvent.HeroDamaged -> {
                anim.shake = 0.3f
                floatAtHero("-${e.by} can", Theme.red)
                game.audio.play("hit"); game.vibrate(60)
            }
            is GameEvent.WallDamaged -> { anim.pops[e.at] = 0.35f; game.audio.play("hit", 0.3f) }
            is GameEvent.Paradox -> { ui.toast(root, e.text, Theme.purple); game.audio.play("paradox"); game.vibrate(40) }
            is GameEvent.EnemySpawned -> if (session.heroEra == level.enemyEra) floatAt(e.at, "Yeni akıncı", Theme.red)
            is GameEvent.EnemyMoved, is GameEvent.Message -> {}
        }
    }

    private fun undo() {
        if (session.actions.isEmpty() || session.status != SessionStatus.PLAYING) return
        queue.clear(); closePopup()
        val before = session.heroEra
        session = LevelSession.replay(level, session.actions.dropLast(1))
        if (session.heroEra != before) anim.startTransition(session.heroPos)
        anim.snap(session)
        refreshHud(); saveProgress()
        ui.toast(root, "Son eylem geri alındı")
    }

    private fun restart() {
        queue.clear(); closePopup()
        if (session.status == SessionStatus.LOST && !game.progression.canStart(level.id)) { ui.toast(root, "Canın kalmadı", Theme.red); return }
        session = LevelSession(level)
        recorded = false
        anim.snap(session); anim.startTransition(session.heroPos)
        stage.actors.filter { it.userObject == MODAL }.forEach { it.remove() }
        refreshHud(); saveProgress()
    }

    fun saveProgress() {
        if (session.status == SessionStatus.PLAYING) game.progression.saveInProgress(level.id, session.actions)
        else if (game.progression.data.inProgress?.levelId == level.id) game.progression.clearInProgress()
    }

    // ------------------------------------------------------------------ Hücre menüsü

    private fun showCellInfo(p: Pos, opts: List<com.zamaninefendisi.logic.ActionOption>) {
        val s = session
        val box = ui.panel(Theme.panel)
        box.pad(18f, 22f, 18f, 22f)
        val c = s.cell(s.heroEra, p)
        val name = c.displayName + if (c.level > 0) " · Gelişim ${c.level}" else ""
        box.add(ui.label("$name  $p", assets.fontM, Theme.ink)).left().row()
        if (c.causeId >= 0) {
            val rec = s.timeline.changes[c.causeId]
            box.add(ui.label("Kökeni: ${rec.era.locative} ${rec.turn}. turda ${rec.kind.verb}.", assets.fontS, Theme.purple, wrap = true)).width(600f).left().row()
        }
        // Zaman görüşü: aynı hücrenin bütün çağlardaki hâli.
        val line = s.timeline.eras.joinToString("  →  ") { era -> "${era.title}: ${s.cell(era, p).displayName}" }
        box.add(ui.label(line, assets.fontS, Theme.inkSoft, wrap = true)).width(600f).left().padTop(4f).padBottom(10f).row()
        s.enemyAt(p)?.takeIf { s.heroEra == level.enemyEra }?.let { box.add(ui.label("${it.kind.title} · can ${it.hp}", assets.fontS, Theme.red)).left().row() }
        val btns = Table()
        btns.defaults().pad(5f)
        for (o in opts) {
            val b = ui.button(o.label, if (o.action is Action.Attack) Theme.red else if (o.action is Action.Move) Theme.blue else Theme.green, assets.fontS) {
                closePopup(); anim.selected = null; perform(o.action)
            }
            b.isDisabled = !o.enabled
            btns.add(b).width(290f).height(70f)
            if (btns.children.size % 2 == 0) btns.row()
            val reason = o.reason
            if (!o.enabled && reason != null) { btns.row(); btns.add(ui.label(reason, assets.fontS, Theme.red, wrap = true)).colspan(2).width(590f).left().row() }
        }
        if (opts.isEmpty()) {
            val why = if (p.manhattan(s.heroPos) > 1) "Buraya ulaşan bir yol yok. Başka bir çağda yol açmayı dene." else "Bu hücrede ${s.heroEra.locative} yapabileceğin bir şey yok."
            btns.add(ui.label(why, assets.fontS, Theme.inkSoft, wrap = true)).width(590f).row()
        }
        box.add(btns).row()
        box.add(ui.button("Kapat", Theme.grey, assets.fontS, Theme.ink) { closePopup(); anim.selected = null }).width(200f).height(60f).padTop(6f)
        box.pack()
        box.setPosition((stage.width - box.width) / 2f, eraBar.y + eraBar.height + 90f)
        stage.addActor(box)
        popup = box
    }

    private fun closePopup() { popup?.remove(); popup = null }

    // ------------------------------------------------------------------ Pencereler

    private fun showPause() {
        closePopup()
        val box = ui.panel(Theme.panel)
        box.pad(30f)
        box.add(ui.label("Duraklatıldı", assets.fontL, Theme.ink)).padBottom(18f).row()
        lateinit var m: Table
        box.add(ui.button("Devam Et", Theme.green) { m.remove() }).width(420f).height(84f).padBottom(12f).row()
        box.add(ui.button("Yeniden Başlat", Theme.orange) { m.remove(); restart() }).width(420f).height(84f).padBottom(12f).row()
        box.add(ui.button("Nasıl Oynanır", Theme.blue) { saveProgress(); game.go(HowToPlayScreen(game, returnTo = { GameScreen(game, level, session.actions) })) }).width(420f).height(84f).padBottom(12f).row()
        box.add(ui.button("Bölümlere Dön", Theme.inkSoft) { saveProgress(); game.go(LevelSelectScreen(game)) }).width(420f).height(84f).row()
        box.add(ui.label("İlerlemen kaydedilir; çıkmak can harcamaz.", assets.fontS, Theme.inkSoft)).padTop(14f)
        m = ui.modal(root, box, dismissOnOutside = true)
    }

    private fun showJournal() {
        closePopup()
        val box = ui.panel(Theme.panel)
        box.pad(24f)
        box.add(ui.label("Zaman Günlüğü · bu deneme", assets.fontM, Theme.ink)).padBottom(10f).row()
        val list = Table()
        for (j in session.journal.asReversed()) {
            val col = when (j.kind) {
                JournalKind.CAUSALITY -> Theme.purple
                JournalKind.PARADOX -> Theme.pink
                JournalKind.BATTLE -> Theme.red
                JournalKind.ACTION -> Theme.ink
                JournalKind.INFO -> Theme.inkSoft
            }
            list.add(ui.label("Tur ${j.turn} · ${j.era.title}", assets.fontS, Theme.eraColor(j.era))).left().row()
            list.add(ui.label(j.text, assets.fontS, col, wrap = true)).width(560f).left().padBottom(10f).row()
        }
        box.add(ui.scroll(list)).height(700f).width(580f).row()
        lateinit var m: Table
        box.add(ui.button("Kapat", Theme.teal) { m.remove() }).width(260f).height(76f).padTop(12f)
        m = ui.modal(root, box, dismissOnOutside = true)
    }

    private fun onFinished() {
        if (recorded) return
        recorded = true
        val p = game.progression
        val box = ui.panel(Theme.panel)
        box.pad(30f)
        lateinit var m: Table
        if (session.status == SessionStatus.WON) {
            val r = p.recordWin(session, game.platform.clock.wallMs())
            game.audio.play("win")
            val stars = session.stars()
            box.add(ui.label("Zaman Dengesi Sağlandı!", assets.fontL, Theme.green)).row()
            box.add(ui.label("★".repeat(stars) + "☆".repeat(3 - stars), assets.fontXL, Theme.gold)).padTop(6f).row()
            box.add(ui.label("${session.actionsUsed} eylem · 3 yıldız için en fazla ${level.par}", assets.fontS, Theme.inkSoft)).padBottom(12f).row()
            val rw = buildString {
                append("+${r.gold} altın   +${r.xp} deneyim")
                if (r.crystals > 0) append("   +${r.crystals} kristal")
            }
            box.add(ui.label(rw, assets.fontM, Theme.orange)).row()
            if (r.levelUp) box.add(ui.label("Zaman Ustası seviyen yükseldi!", assets.fontS, Theme.purple)).padTop(4f).row()
            val summary = session.causalSummary().take(3)
            if (summary.isNotEmpty()) {
                box.add(ui.label("Zaman çizgisine etkin", assets.fontS, Theme.ink)).padTop(14f).left().row()
                for (line in summary) box.add(ui.label("• $line", assets.fontS, Theme.purple, wrap = true)).width(560f).left().row()
            }
            val row = Table()
            row.defaults().pad(6f)
            val next = LevelCatalog.next(level.id)
            row.add(ui.button("Tekrar", Theme.orange, assets.fontS) { m.remove(); restart() }).size(170f, 80f)
            row.add(ui.button("Bölümler", Theme.blue, assets.fontS) { game.go(LevelSelectScreen(game)) }).size(170f, 80f)
            if (next != null) row.add(ui.button("Sonraki ›", Theme.green, assets.fontS) { LevelSelectScreen.tryStart(game, root, next.id, resume = false) }).size(190f, 80f)
            box.add(row).padTop(20f)
        } else {
            p.recordFail(session)
            game.audio.play("lose"); game.vibrate(120)
            box.add(ui.label("Zaman Çizgisi Kırıldı", assets.fontL, Theme.red)).row()
            box.add(ui.label(session.failReason ?: "", assets.fontM, Theme.ink, wrap = true, align = Align.center)).width(560f).padTop(10f).row()
            val lives = p.refreshLives()
            val lifeText = if (!p.needsLife(level.id)) "Tamamlanmış bölüm: can harcanmadı."
            else if (lives.lives > 0) "1 can harcandı. Kalan can: ${lives.lives}" else "Canın kalmadı. Sonraki can: ${formatTime(lives.regenRemainingMs)}"
            box.add(ui.label(lifeText, assets.fontS, Theme.inkSoft, wrap = true, align = Align.center)).width(560f).padTop(10f).row()
            box.add(ui.label("İpucu: Günlük'te hangi değişikliğin neye yol açtığını görebilirsin.", assets.fontS, Theme.purple, wrap = true, align = Align.center)).width(560f).padTop(10f).row()
            val row = Table()
            row.defaults().pad(6f)
            row.add(ui.button("Günlük", Theme.teal, assets.fontS) { showJournal() }).size(170f, 80f)
            row.add(ui.button("Bölümler", Theme.blue, assets.fontS) { game.go(LevelSelectScreen(game)) }).size(170f, 80f)
            val retry = ui.button("Tekrar Dene", Theme.green, assets.fontS) { m.remove(); restart() }
            retry.isDisabled = !p.canStart(level.id)
            row.add(retry).size(190f, 80f)
            box.add(row).padTop(20f)
        }
        m = ui.modal(root, box)
    }

    override fun onBack() {
        if (popup != null) { closePopup(); return }
        if (session.status == SessionStatus.PLAYING) showPause() else game.go(LevelSelectScreen(game))
    }

    // ------------------------------------------------------------------ Çizim

    private fun computeLayout() {
        val bottom = eraBar.y + eraBar.height + actionRow.height + 30f
        val topY = if (hintBox.isVisible) hintBox.y - 10f else topBar.y - goalsTable.parent.height - 20f
        val rh = (topY - bottom).coerceAtLeast(200f)
        layout = IsoLayout.fit(level.width, level.height, 14f, bottom, stage.width - 28f, rh)
    }

    override fun drawWorld(delta: Float) {
        anim.update(delta, session)
        for (m in flashes.values) { val it = m.entries.iterator(); while (it.hasNext()) { val e = it.next(); e.setValue(e.value - delta); if (e.value <= 0) it.remove() } }
        if (queue.isNotEmpty()) {
            queueTimer -= delta
            if (queueTimer <= 0f) { queueTimer = 0.14f; perform(queue.poll()) }
        }
        root.validate()
        computeLayout()
        val sr = game.shapes
        val cam = stage.camera
        val shakeX = if (anim.shake > 0) (Math.random().toFloat() - 0.5f) * 14f * anim.shake / 0.3f else 0f
        cam.position.x += shakeX; cam.update()
        sr.projectionMatrix = cam.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        sr.begin(ShapeRenderer.ShapeType.Filled)
        val markers = level.goals.flatMap { it.markers }.filter { it.first == session.heroEra }.map { it.second }.toSet()
        val protect = if (session.heroEra == level.enemyEra) level.protect.toSet() else emptySet()
        renderer.draw(session, session.heroEra, layout, anim, markers, protect)
        sr.end()
        cam.position.x -= shakeX; cam.update()
        // Uçan yazılar
        game.batch.projectionMatrix = cam.combined
        game.batch.begin()
        val it = floats.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.life -= delta; f.y += delta * 50f
            if (f.life <= 0) { it.remove(); continue }
            assets.fontM.color = Color(f.color.r, f.color.g, f.color.b, (f.life / 0.5f).coerceAtMost(1f))
            assets.fontM.draw(game.batch, f.text, f.x - 150f, f.y, 300f, Align.center, false)
        }
        assets.fontM.color = Color.WHITE
        game.batch.end()
    }

    /** Çağ düğmelerinin içine o çağın canlı küçük haritası çizilir: bir çağdaki değişiklik diğerlerinde anında görünür. */
    override fun drawOverlay() {
        val sr = game.shapes
        sr.projectionMatrix = stage.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        sr.begin(ShapeRenderer.ShapeType.Filled)
        for ((era, b) in eraButtons) {
            if (!b.hasParent() || b.stage == null) continue
            tmpV.set(0f, 0f); b.localToStageCoordinates(tmpV)
            if (era == session.heroEra) {
                val pulse = 0.6f + 0.4f * kotlin.math.sin(time * 4f)
                sr.color = Color(0.23f, 0.2f, 0.33f, pulse)
                val bx = tmpV.x; val by = tmpV.y; val bw = b.width; val bh = b.height
                sr.rect(bx - 6f, by - 6f, bw + 12f, 6f); sr.rect(bx - 6f, by + bh, bw + 12f, 6f)
                sr.rect(bx - 6f, by, 6f, bh); sr.rect(bx + bw, by, 6f, bh)
            }
            val areaW = b.width - 30f; val areaH = b.height - 64f
            val cell = minOf(areaW / level.width, areaH / level.height)
            val ox = tmpV.x + (b.width - cell * level.width) / 2f
            val oy = tmpV.y + 52f + (areaH - cell * level.height) / 2f
            sr.color = Color(1f, 1f, 1f, 0.9f); sr.rect(ox - 4f, oy - 4f, cell * level.width + 8f, cell * level.height + 8f)
            val g = session.timeline.grid(era)
            val fl = flashes[era]
            for (pp in g.positions()) {
                val c = g[pp]
                val x = ox + pp.x * cell; val y = oy + (level.height - 1 - pp.y) * cell
                sr.color = Palette.tile(era, c.terrain).top
                sr.rect(x, y, cell - 1f, cell - 1f)
                featureColor(c.feature)?.let { sr.color = it; sr.rect(x + cell * 0.2f, y + cell * 0.2f, cell * 0.6f - 1f, cell * 0.6f - 1f) }
                if (era == level.enemyEra && session.enemyAt(pp) != null) { sr.color = Theme.red; sr.circle(x + cell / 2, y + cell / 2, cell * 0.35f, 8) }
                if (era == session.heroEra && pp == session.heroPos) { sr.color = Theme.ink; sr.circle(x + cell / 2, y + cell / 2, cell * 0.38f, 10) }
                fl?.get(pp)?.let { t -> sr.color = Color(1f, 1f, 0.6f, (t / 1.2f) * 0.9f); sr.rect(x - 1, y - 1, cell + 1, cell + 1) }
            }
        }
        sr.end()
    }

    private fun featureColor(f: Feature): Color? = when (f) {
        Feature.NONE, Feature.STUMP -> null
        Feature.SAPLING, Feature.TREE, Feature.FOREST -> Color.valueOf("3E9E45")
        Feature.FIELD -> Theme.gold
        Feature.BRIDGE_WOOD, Feature.PALISADE -> Color.valueOf("A26D3F")
        Feature.BRIDGE_STONE, Feature.STONE_WALL, Feature.RUINS, Feature.ENEMY_RUINS -> Color.valueOf("8F8BA3")
        Feature.BRIDGE_TECH, Feature.ENERGY_WALL -> Color.valueOf("7FD8FF")
        Feature.HUT, Feature.TOWN, Feature.CITY -> Theme.orange
        Feature.CASTLE -> Theme.purple
        Feature.ENEMY_CAMP, Feature.ENEMY_FORT -> Theme.red
        Feature.SHRINE, Feature.SHRINE_ACTIVE -> Theme.gold
    }

    private fun floatAtHero(text: String, color: Color) = floatAt(session.heroPos, text, color)

    private fun floatAt(p: Pos, text: String, color: Color) {
        floats += FloatText(text, layout.cx(p.x.toFloat(), p.y.toFloat()), layout.cy(p.x.toFloat(), p.y.toFloat()) + layout.tw * 0.7f, color)
    }

    override fun hide() { saveProgress() }
}
