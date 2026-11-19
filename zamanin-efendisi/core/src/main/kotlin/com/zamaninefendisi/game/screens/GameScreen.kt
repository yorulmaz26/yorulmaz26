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

/** Oynanışın hangi mod için olduğu: sonuç, can ve ödül kuralları buna göre değişir. */
sealed class PlayMode {
    data object Main : PlayMode()
    data object Endless : PlayMode()
    data class Daily(val day: Int) : PlayMode()
    data class Weekly(val week: Int) : PlayMode()
    data object Alt : PlayMode()
    data class Defense(val wave: Int) : PlayMode()
    data object Prestige : PlayMode()
}

/** Oynanış ekranı: izometrik harita, zaman geçiş arayüzü, hedefler, eylem menüsü ve sonuç pencereleri. */
class GameScreen(
    game: ZamaninEfendisiGame,
    val level: LevelDef,
    initialActions: List<Action>,
    /** Geliştirici otomatik oynatma: eylemler arayüzün kendi yolundan sırayla uygulanır. */
    autoplay: List<Action> = emptyList(),
    val mode: PlayMode = PlayMode.Main,
) : BaseScreen(game) {
    private val hpBonus = if (mode == PlayMode.Prestige) 0 else game.progression.heroHpBonus()
    private var session = LevelSession.replay(level, initialActions, hpBonus)
    private var foresightLeft: Int? = game.progression.foresightCharges()
    private val cosmetics = com.zamaninefendisi.game.render.Cosmetics(game.progression.data.equipped)
    private var portalT = 1f
    private val particles = ArrayList<FloatArray>()
    private val particleColors = ArrayList<Color>()
    private val renderer = IsoRenderer(game.shapes).also { it.cosmetics = com.zamaninefendisi.game.render.Cosmetics(game.progression.data.equipped) }
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
        title.add(ui.label(if (mode == PlayMode.Main || mode == PlayMode.Alt) "Bölüm ${level.id} · ${level.name}" else level.name, assets.fontM, Theme.ink, align = Align.center)).row()
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
        if (level.freezes > 0) parts += "Buz ${s.freezes}"
        if (s.combos > 0) parts += "Kombo ${s.combos}"
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
            val who = "Düşmanlar (${level.enemyEra.locative})"
            goalsTable.add(ui.label("$who mor çerçeveli hücreye ulaşırsa kaybedersin", assets.fontS, Theme.purple, wrap = true)).width(620f).left().row()
        }
        val hint = if (game.progression.data.settings.hints && mode == PlayMode.Main) s.currentHint() else null
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
        if (game.progression.hasGuide()) actionRow.add(ui.button("Rehber", Theme.gold, assets.fontS) { useGuide() }).width(150f)
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

    private fun selectUnit(id: Int) {
        val u = session.unit(id)
        if (u == null) { clearUnitSelection(); return }
        anim.selectedUnit = id
        anim.reach = session.unitReach(u)
        anim.targets = session.unitTargets(u)
    }

    private fun clearUnitSelection() { anim.selectedUnit = -1; anim.reach = emptySet(); anim.targets = emptySet() }

    private fun onWorldTap(p: Pos) {
        closePopup()
        if (session.status != SessionStatus.PLAYING || queue.isNotEmpty()) return
        if (!session.inBounds(p)) { anim.selected = null; clearUnitSelection(); return }
        // Birlik komutası
        if (anim.selectedUnit >= 0) {
            val id = anim.selectedUnit
            when (p) {
                in anim.targets -> { clearUnitSelection(); perform(Action.UnitAttack(id, p)); return }
                in anim.reach -> { clearUnitSelection(); perform(Action.UnitMove(id, p)); return }
            }
            clearUnitSelection()
            if (session.unitAt(p)?.id == id) return
        }
        val tappedUnit = if (session.heroEra == level.enemyEra) session.unitAt(p) else null
        if (tappedUnit != null) {
            selectUnit(tappedUnit.id)
            showUnitInfo(tappedUnit)
            return
        }
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
        closePopup(); clearUnitSelection()
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
                portalT = 0f
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
            is GameEvent.EnergyChanged -> floatAtHero(if (e.delta > 0) "+${e.delta} enerji" else "${e.delta} enerji", if (e.delta > 0) Theme.blue else Theme.purple)
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
            is GameEvent.EnemySpawned -> if (session.heroEra == level.enemyEra) floatAt(e.at, if (level.enemyEra == Era.FUTURE) "Yeni robot" else "Yeni akıncı", Theme.red)
            is GameEvent.EnemyDamaged -> floatAt(e.at, "-${e.amount}", Theme.orange)
            is GameEvent.UnitDamaged -> if (e.amount > 0) floatAt(e.at, "-${e.amount}", Theme.red) else floatAt(e.at, "Kalkan!", Theme.blue)
            is GameEvent.UnitLost -> { ui.toast(root, "Bir birliğini kaybettin!", Theme.red); game.audio.play("lose", 0.3f) }
            is GameEvent.Shot -> anim.shots += floatArrayOf(e.from.x.toFloat(), e.from.y.toFloat(), e.to.x.toFloat(), e.to.y.toFloat(), 0.35f)
            is GameEvent.Combo -> { ui.toast(root, "Zaman Zinciri Kombosu! Üç çağı birbirine bağladın (×${e.count}).", Theme.purple); game.audio.play("win", 0.4f, 1.3f) }
            is GameEvent.EnemyMoved, is GameEvent.UnitMoved, is GameEvent.Message -> {}
        }
    }

    private fun undo() {
        if (session.actions.isEmpty() || session.status != SessionStatus.PLAYING) return
        queue.clear(); closePopup(); clearUnitSelection()
        val before = session.heroEra
        session = LevelSession.replay(level, session.actions.dropLast(1), hpBonus)
        if (session.heroEra != before) anim.startTransition(session.heroPos)
        anim.snap(session)
        refreshHud(); saveProgress()
        ui.toast(root, "Son eylem geri alındı")
    }

    private fun restart() {
        queue.clear(); closePopup(); clearUnitSelection()
        if (mode == PlayMode.Main && session.status == SessionStatus.LOST && !game.progression.canStart(level.id)) { ui.toast(root, "Canın kalmadı", Theme.red); return }
        session = LevelSession(level, hpBonus)
        recorded = false
        foresightLeft = game.progression.foresightCharges()
        anim.snap(session); anim.startTransition(session.heroPos)
        stage.actors.filter { it.userObject == MODAL }.forEach { it.remove() }
        refreshHud(); saveProgress()
    }

    /** Zaman Rehberi: oyuncu doğrulanmış çözümün başındaysa sıradaki adımı gösterir (1 kristal). */
    private fun useGuide() {
        val sol = level.solution
        val acts = session.actions
        val onTrack = acts.size < sol.size && acts.indices.all { acts[it] == sol[it] }
        if (!onTrack) { ui.toast(root, "Rehber yalnızca kendi çözüm yolunu bilir. Yeniden başlatırsan baştan yol gösterebilir.", Theme.purple); return }
        if (!game.progression.spendGuide()) { ui.toast(root, "Rehber için 1 Zaman Kristali gerekli", Theme.red); return }
        val next = sol[acts.size]
        val text = when (next) {
            is Action.Move -> { anim.selected = next.to; "Sıradaki adım: ${next.to} hücresine yürü" }
            is Action.SwitchEra -> "Sıradaki adım: ${next.era.title} çağına geç"
            is Action.Build -> { anim.selected = next.at; "Sıradaki adım: ${next.at} hücresinde \"${next.kind.title}\"" }
            is Action.Attack -> { anim.selected = next.at; "Sıradaki adım: ${next.at} hücresindeki düşmana saldır" }
            is Action.UnitMove -> { anim.selected = next.to; "Sıradaki adım: ${session.unit(next.unit)?.type?.title} birliğini ${next.to} hücresine götür" }
            is Action.UnitAttack -> { anim.selected = next.at; "Sıradaki adım: ${session.unit(next.unit)?.type?.title} ile ${next.at} hedefine saldır" }
            Action.Wait -> "Sıradaki adım: bir tur bekle"
        }
        ui.toast(root, text, Theme.darker(Theme.gold, 0.7f))
    }

    fun saveProgress() {
        if (mode != PlayMode.Main) return
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
        s.enemyAt(p)?.takeIf { s.heroEra == level.enemyEra }?.let { e ->
            val k = e.kind
            box.add(ui.label("${k.title} · can ${e.hp}/${k.hp} · saldırı ${e.attack} · savunma ${k.defense} · menzil ${k.range}", assets.fontS, Theme.red, wrap = true)).width(600f).left().row()
            box.add(ui.label("Strateji: ${e.strategy.title} — ${e.strategy.description}", assets.fontS, Theme.inkSoft, wrap = true)).width(600f).left().row()
            if (e.frozenTurns > 0) box.add(ui.label("Donmuş: ${e.frozenTurns} tur", assets.fontS, Theme.blue)).left().row()
        }
        if (c.frozen) box.add(ui.label("Bu nesnenin zamanı donmuş: sonraki çağlarda değişmeyecek.", assets.fontS, Theme.blue, wrap = true)).width(600f).left().row()
        val btns = Table()
        btns.defaults().pad(5f)
        for (o in opts) {
            val b = ui.button(o.label, if (o.action is Action.Attack) Theme.red else if (o.action is Action.Move) Theme.blue else Theme.green, assets.fontS) {
                closePopup(); anim.selected = null; perform(o.action)
            }
            b.isDisabled = !o.enabled
            if (o.action is Action.Build && o.enabled && s.timeline.eras.last() != s.heroEra) {
                btns.add(b).width(400f).height(70f)
                val left = foresightLeft
                val fb = ui.button(if (left == null) "Önizle" else "Önizle ($left)", Theme.purple, assets.fontS) {
                    if (left != null) foresightLeft = left - 1
                    showForesight(o.action)
                }
                fb.isDisabled = left != null && left <= 0
                btns.add(fb).width(180f).height(70f).row()
            } else {
                btns.add(b).width(590f).colspan(2).height(70f).row()
            }
            val reason = o.reason
            if (!o.enabled && reason != null) btns.add(ui.label(reason, assets.fontS, Theme.red, wrap = true)).colspan(2).width(590f).left().row()
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

    private fun showUnitInfo(u: com.zamaninefendisi.logic.AllyUnit) {
        val box = ui.panel(Theme.panel)
        box.pad(16f, 22f, 16f, 22f)
        val t = u.type
        box.add(ui.label("${t.title} · can ${u.hp}/${t.hp}", assets.fontM, Color.valueOf("4F8BE8"))).left().row()
        val range = if (t.minRange == t.maxRange) "${t.maxRange}" else "${t.minRange}–${t.maxRange}"
        box.add(ui.label("Saldırı ${t.attack} · Savunma ${t.defense} · Menzil $range · Hareket ${t.move}", assets.fontS, Theme.ink)).left().row()
        box.add(ui.label("Çağ uyumu: ${t.eras.joinToString(", ") { it.title }}", assets.fontS, Theme.inkSoft)).left().row()
        t.ability?.let { box.add(ui.label("${it.title}: ${it.description}", assets.fontS, Theme.purple, wrap = true)).width(600f).left().row() }
        box.add(ui.label("Mavi hücreye dokun: yürü · Kırmızı hedefe dokun: saldır. Komuta etmediğin birlikler menzildeki düşmana kendiliğinden ateş eder.", assets.fontS, Theme.inkSoft, wrap = true)).width(600f).left().padTop(6f).row()
        box.add(ui.button("Kapat", Theme.grey, assets.fontS, Theme.ink) { closePopup(); clearUnitSelection() }).width(200f).height(60f).padTop(8f)
        box.pack()
        box.setPosition((stage.width - box.width) / 2f, eraBar.y + eraBar.height + 90f)
        stage.addActor(box)
        popup = box
    }

    private fun showForesight(a: Action) {
        val f = session.foresee(a) ?: return
        closePopup()
        val box = ui.panel(Theme.panel)
        box.pad(18f, 22f, 18f, 22f)
        box.add(ui.label("Geleceği Önceden Görme", assets.fontM, Theme.purple)).left().row()
        if (f.changes.isEmpty()) box.add(ui.label("Görünür bir değişiklik olmayacak.", assets.fontS, Theme.inkSoft)).left().row()
        for ((era, p, ch) in f.changes.take(8)) {
            val (a0, a1) = ch
            box.add(ui.label("${era.title} $p: ${a0.displayName.ifEmpty { "boş" }} → ${a1.displayName.ifEmpty { "boş" }}", assets.fontS, Theme.eraColor(era).let { Theme.darker(it, 0.8f) }, wrap = true)).width(600f).left().row()
            flashes.getOrPut(era) { HashMap() }[p] = 2.5f
        }
        if (f.changes.size > 8) box.add(ui.label("… ve ${f.changes.size - 8} hücre daha", assets.fontS, Theme.inkSoft)).left().row()
        for (n in f.notes) box.add(ui.label(n, assets.fontS, Theme.red, wrap = true)).width(600f).left().row()
        val row = Table()
        row.add(ui.button("Uygula", Theme.green, assets.fontS) { closePopup(); anim.selected = null; perform(a) }).size(220f, 66f).padRight(12f)
        row.add(ui.button("Vazgeç", Theme.grey, assets.fontS, Theme.ink) { closePopup(); anim.selected = null }).size(220f, 66f)
        box.add(row).padTop(10f)
        box.pack()
        box.setPosition((stage.width - box.width) / 2f, eraBar.y + eraBar.height + 90f)
        stage.addActor(box)
        popup = box
    }

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
        box.add(ui.button(if (mode == PlayMode.Main) "Bölümlere Dön" else "Mücadelelere Dön", Theme.inkSoft) {
            saveProgress()
            game.go(if (mode == PlayMode.Main) LevelSelectScreen(game) else ChallengesScreen(game))
        }).width(420f).height(84f).row()
        box.add(ui.label(if (mode == PlayMode.Main) "İlerlemen kaydedilir; çıkmak can harcamaz." else "Mücadeleler can harcamaz.", assets.fontS, Theme.inkSoft)).padTop(14f)
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
        if (mode != PlayMode.Main) { onModeFinished(); return }
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
            box.add(ui.label("+${r.gold} altın   +${r.xp} deneyim" + if (r.comboGold > 0) "   (kombo +${r.comboGold})" else "", assets.fontM, Theme.orange)).row()
            val others = r.gains.filterKeys { it != com.zamaninefendisi.logic.meta.Resource.GOLD }
            if (others.isNotEmpty()) box.add(ui.label(others.entries.joinToString(" · ") { "+${it.value} ${it.key.title}" }, assets.fontS, Theme.purple, wrap = true, align = Align.center)).width(560f).row()
            spawnVictoryParticles()
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
        announceAchievements()
    }

    private fun announceAchievements() {
        val fresh = game.progression.continuity.checkAchievements()
        if (fresh.isNotEmpty()) ui.toast(root, "Başarım açıldı: " + fresh.joinToString(", ") { it.title } + " (+${fresh.sumOf { it.reward }} kristal)", Theme.purple)
    }

    /** Ana bölüm dışındaki modların sonuç penceresi. */
    private fun onModeFinished() {
        val cm = game.progression.continuity
        val won = session.status == SessionStatus.WON
        val box = ui.panel(Theme.panel)
        box.pad(30f)
        lateinit var m: Table
        box.add(ui.label(if (won) "Başarılı!" else "Zaman Çizgisi Kırıldı", assets.fontL, if (won) Theme.green else Theme.red)).row()
        if (!won) box.add(ui.label(session.failReason ?: "", assets.fontS, Theme.ink, wrap = true, align = Align.center)).width(560f).padTop(8f).row()
        fun info(t: String, c: Color = Theme.purple) = box.add(ui.label(t, assets.fontS, c, wrap = true, align = Align.center)).width(560f).padTop(8f).row()
        fun rewardText(r: Map<com.zamaninefendisi.logic.meta.Resource, Int>) = r.entries.joinToString(" · ") { "+${it.value} ${it.key.title}" }
        val row = Table()
        row.defaults().pad(6f)
        when (val md = mode) {
            PlayMode.Endless -> {
                val next = cm.endlessResult(session)
                if (next != null) {
                    info("Aşama ${next.stage - 1} geçildi! Rekor: ${game.progression.data.continuity.endlessBest}")
                    row.add(ui.button("Sonraki Aşama ›", Theme.green, assets.fontS) { game.go(GameScreen(game, cm.endlessLevel(next), emptyList(), mode = PlayMode.Endless)) }).size(260f, 80f)
                } else info("Seri sona erdi. En uzun seri: ${game.progression.data.continuity.endlessBest} aşama")
                row.add(ui.button("Mücadeleler", Theme.blue, assets.fontS) { game.go(ChallengesScreen(game)) }).size(220f, 80f)
            }
            is PlayMode.Daily -> {
                val r = cm.recordDaily(session, md.day)
                if (won) info(if (r.isEmpty()) "${session.actionsUsed} eylem. Bugünün en iyisi: ${game.progression.data.continuity.dailyBest[md.day]}" else "Günün bulmacası çözüldü! ${rewardText(r)} ve bir koleksiyon eseri")
                row.add(ui.button("Tekrar", Theme.orange, assets.fontS) { m.remove(); restart() }).size(180f, 80f)
                row.add(ui.button("Mücadeleler", Theme.blue, assets.fontS) { game.go(ChallengesScreen(game)) }).size(220f, 80f)
            }
            is PlayMode.Weekly -> {
                val r = cm.recordWeekly(session, md.week)
                if (won) info(if (r.isEmpty()) "Bu haftanın krizi zaten çözülmüştü." else "Haftalık kriz çözüldü! ${rewardText(r)}")
                row.add(ui.button("Tekrar", Theme.orange, assets.fontS) { m.remove(); restart() }).size(180f, 80f)
                row.add(ui.button("Mücadeleler", Theme.blue, assets.fontS) { game.go(ChallengesScreen(game)) }).size(220f, 80f)
            }
            is PlayMode.Defense -> {
                val ok = cm.recordDefense(md.wave, session)
                if (ok) {
                    info("Dalga ${md.wave} püskürtüldü! En iyi: ${game.progression.data.continuity.defenseBest}")
                    row.add(ui.button("Dalga ${md.wave + 1} ›", Theme.green, assets.fontS) { game.go(GameScreen(game, cm.defenseLevel(md.wave + 1), emptyList(), mode = PlayMode.Defense(md.wave + 1))) }).size(240f, 80f)
                } else info("Kale düştü. Savunma 1. dalgadan yeniden başlar.")
                row.add(ui.button("Mücadeleler", Theme.blue, assets.fontS) { game.go(ChallengesScreen(game)) }).size(220f, 80f)
            }
            PlayMode.Alt -> {
                val task = cm.altTask(level)
                val ok = cm.recordAlt(level, session)
                info(if (ok) "Alternatif tarih yazıldı: ${task.title}! +1 Kadim Parça, +50 altın ve bir eser" else if (won) "Bölüm kazanıldı ama görev koşulu sağlanmadı: ${task.description}" else task.description, if (ok) Theme.green else Theme.purple)
                row.add(ui.button("Tekrar", Theme.orange, assets.fontS) { m.remove(); restart() }).size(180f, 80f)
                row.add(ui.button("Mücadeleler", Theme.blue, assets.fontS) { game.go(ChallengesScreen(game)) }).size(220f, 80f)
            }
            PlayMode.Prestige -> {
                val cycle = cm.recordPrestige(level.id, session)
                if (cycle) info("Zaman Döngüsü tamamlandı! +3 Zaman Mührü, +10 kristal. Yeni döngü başladı.", Theme.green)
                else if (won) info("Döngü ilerlemesi: ${game.progression.data.continuity.cycleDone.size}/${com.zamaninefendisi.logic.levels.LevelCatalog.levels.size}")
                row.add(ui.button("Tekrar", Theme.orange, assets.fontS) { m.remove(); restart() }).size(180f, 80f)
                row.add(ui.button("Döngü", Theme.blue, assets.fontS) { game.go(PrestigeScreen(game)) }).size(220f, 80f)
            }
            PlayMode.Main -> {}
        }
        game.audio.play(if (won) "win" else "lose")
        if (won) spawnVictoryParticles()
        box.add(row).padTop(18f)
        m = ui.modal(root, box)
        announceAchievements()
    }

    override fun onBack() {
        if (popup != null) { closePopup(); return }
        if (session.status == SessionStatus.PLAYING) showPause() else game.go(if (mode == PlayMode.Main) LevelSelectScreen(game) else ChallengesScreen(game))
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
        lastDelta = delta
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
    private var lastDelta = 0f

    override fun drawOverlay() {
        val sr = game.shapes
        sr.projectionMatrix = stage.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        sr.begin(ShapeRenderer.ShapeType.Filled)
        drawEffects(sr, lastDelta)
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
                if (era == level.enemyEra && session.unitAt(pp) != null) { sr.color = Color.valueOf("4F8BE8"); sr.circle(x + cell / 2, y + cell / 2, cell * 0.35f, 8) }
                if (era == session.heroEra && pp == session.heroPos) { sr.color = Theme.ink; sr.circle(x + cell / 2, y + cell / 2, cell * 0.38f, 10) }
                fl?.get(pp)?.let { t -> sr.color = Color(1f, 1f, 0.6f, (t / 1.2f) * 0.9f); sr.rect(x - 1, y - 1, cell + 1, cell + 1) }
            }
        }
        sr.end()
    }

    private fun spawnVictoryParticles() {
        particles.clear(); particleColors.clear()
        val cols = cosmetics.victory
        repeat(90) { i ->
            val x = stage.width * Math.random().toFloat()
            particles += floatArrayOf(x, stage.height + 20f + 300f * Math.random().toFloat(), (Math.random().toFloat() - 0.5f) * 60f, -(120f + 160f * Math.random().toFloat()), 4f)
            particleColors += cols[i % cols.size]
        }
    }

    private fun drawEffects(sr: ShapeRenderer, delta: Float) {
        if (portalT < 1f) {
            portalT = (portalT + delta / 0.7f).coerceAtMost(1f)
            val hx = layout.cx(anim.heroX, anim.heroY); val hy = layout.cy(anim.heroX, anim.heroY) + layout.tw * 0.3f
            for ((i, c) in cosmetics.portal.withIndex()) {
                val t = (portalT - i * 0.08f).coerceIn(0f, 1f)
                if (t <= 0f) continue
                sr.color = Color(c.r, c.g, c.b, (1f - t) * 0.8f)
                sr.arc(hx, hy, 20f + t * 260f, 0f, 360f, 40)
            }
        }
        val it = particles.iterator(); var idx = 0
        val keep = ArrayList<Color>()
        while (it.hasNext()) {
            val p = it.next(); val c = particleColors[idx++]
            p[0] += p[2] * delta; p[1] += p[3] * delta; p[4] -= delta
            if (p[4] <= 0f || p[1] < -20f) { it.remove(); continue }
            keep += c
            sr.color = Color(c.r, c.g, c.b, (p[4] / 1.5f).coerceAtMost(1f))
            if (cosmetics.victoryStyle == "anim_crystals") sr.triangle(p[0] - 5f, p[1], p[0] + 5f, p[1], p[0], p[1] + 12f)
            else if (cosmetics.victoryStyle == "anim_petals") sr.ellipse(p[0] - 6f, p[1] - 3f, 12f, 7f, 10)
            else sr.rect(p[0] - 4f, p[1] - 4f, 8f, 8f)
        }
        particleColors.clear(); particleColors.addAll(keep)
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
        Feature.CRYSTAL -> Theme.blue
        Feature.WATCHTOWER, Feature.STONE_TOWER, Feature.ENERGY_TURRET -> Theme.teal
    }

    private fun floatAtHero(text: String, color: Color) = floatAt(session.heroPos, text, color)

    private fun floatAt(p: Pos, text: String, color: Color) {
        floats += FloatText(text, layout.cx(p.x.toFloat(), p.y.toFloat()), layout.cy(p.x.toFloat(), p.y.toFloat()) + layout.tw * 0.7f, color)
    }

    override fun hide() { saveProgress() }
}
