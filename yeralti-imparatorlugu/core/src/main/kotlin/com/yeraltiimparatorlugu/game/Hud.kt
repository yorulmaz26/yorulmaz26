package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.yeraltiimparatorlugu.sim.AdviceLevel
import com.yeraltiimparatorlugu.sim.Advisor
import com.yeraltiimparatorlugu.sim.ResourceType
import com.yeraltiimparatorlugu.sim.Simulation
import com.yeraltiimparatorlugu.sim.SoundCue
import com.yeraltiimparatorlugu.sim.StorageKind

/** Oyun ekranından arayüzün çağırdığı işlemler. */
interface GameActions {
    val sim: Simulation
    fun saveNow()
    fun newColony()
    fun focus(x: Int, y: Int)
}

/**
 * Sade dikey arayüz: üstte tek kapsül bilgi çubuğu ve 3 kaynak çipi, altında hedef kartı ve öneriler;
 * altta büyük ikonlu 5–6 düğme. Gelişmiş ekranlar "Diğer" menüsünde ve yalnızca açıldıklarında görünür.
 */
class Hud(
    internal val ui: Ui,
    internal val view: ViewState,
    internal val actions: GameActions,
    internal val renderer: WorldRenderer,
    internal val settings: Settings,
    internal val sounds: SoundFx,
) {
    val stage = Stage(ExtendViewport(720f, 1280f))
    internal val sim get() = actions.sim

    private val root = Table().apply { setFillParent(true); touchable = Touchable.childrenOnly; top() }
    private val dayLabel = ui.bold("")
    private val seasonImg = Image()
    private val weatherImg = Image()
    private val tempLabel = ui.small("")
    private val coinLabel = ui.bold("")
    private val speedBtn = Container<Actor>()
    private val foodLabel = ui.bold("")
    private val matLabel = ui.bold("")
    private val popLabel = ui.bold("")
    private val foodBar = ui.bar(0f)
    private val matBar = ui.bar(0f)
    private val popBar = ui.bar(0f)
    private val alertLabel = ui.small("", Color.WHITE, wrap = true)
    private val alertBox = Table().apply { background = ui.alert; isVisible = false }
    private val toast = ui.label("", wrap = true, align = Align.center)
    private val toastBox = Container(toast).apply { background = ui.toast; fillX(); isVisible = false }
    private var toastTime = 0f
    private val goalTitle = ui.bold("", wrap = true)
    private val goalInfo = ui.small("", Palette.uiTextSoft)
    private val goalBar = ui.bar(0f)
    private val goalCard = Table()
    private val adviceBtnLabel = ui.small("Öneriler")
    private val adviceDot = Image(ui.art.r("ui_dot"))
    private val hintLabel = ui.small("", Palette.uiTextSoft, wrap = true)
    internal val contextRow = Table()
    private val contextBox = Table()
    private val bottomBar = Table()
    private val modeButtons = HashMap<String, Button>()
    private val sheetLayer = Table().apply { setFillParent(true); bottom(); touchable = Touchable.childrenOnly }
    internal val sheet = Table().apply { background = ui.panel; top() }
    internal var sheetRefresh: (() -> Unit)? = null
    private var refreshTimer = 0f
    private var armyShown = false
    private var goalCollapsed = false
    internal var confirmReset = false

    val sheetOpen: Boolean get() = sheet.parent != null

    init {
        stage.addActor(WorldLabels())
        buildTop()
        buildMiddle()
        buildBottom()
        stage.addActor(root)
        stage.addActor(sheetLayer)
        setMode(ToolMode.VIEW)
    }

    // ---------------------------------------------------------------- Üst

    private fun buildTop() {
        val capsule = Table().apply { background = ui.panel; pad(14f, 22f, 18f, 18f) }
        capsule.add(seasonImg).size(42f).padRight(6f)
        capsule.add(dayLabel).left()
        capsule.add(weatherImg).size(42f).padLeft(16f).padRight(4f)
        capsule.add(tempLabel).left().expandX()
        capsule.add(ui.image(ui.art.r("coin"))).size(36f).padRight(4f)
        capsule.add(coinLabel).padRight(12f)
        capsule.add(speedBtn).size(78f, 66f)
        capsule.onClick { openEcosystem() }
        root.add(capsule).growX().pad(10f, 10f, 0f, 10f).row()

        val chips = Table()
        fun chip(icon: Actor, label: Label, bar: Ui.Bar, onTap: () -> Unit) {
            val c = Table().apply { background = ui.chip; pad(6f, 10f, 8f, 12f) }
            c.add(icon).size(36f).padRight(6f)
            val col = Table().apply { left() }
            col.add(label).left().row()
            col.add(bar).width(92f).height(12f).padTop(2f).left()
            c.add(col)
            c.onClick(onTap)
            chips.add(c).width(224f).pad(0f, 3f, 0f, 3f)
        }
        chip(ui.icon(ResourceType.FOOD), foodLabel, foodBar) { openInventory() }
        chip(ui.icon(ResourceType.LEAF), matLabel, matBar) { openInventory() }
        chip(ui.icon("people", Palette.accentDark), popLabel, popBar) { openColony(1) }
        root.add(chips).growX().pad(8f, 6f, 0f, 6f).row()
    }

    private fun speedIcon() = when (view.speed) { 0 -> "pause"; 1 -> "play"; 2 -> "fast"; else -> "faster" }

    private fun rebuildSpeed() {
        val b = ui.iconButton(speedIcon(), null, if (view.speed == 0) ui.dangerStyle else ui.buttonStyle, iconSize = 34f) {
            view.speed = (view.speed + 1) % 4; rebuildSpeed(); sounds.play(SoundCue.CLICK)
            showToast(when (view.speed) { 0 -> "Duraklatıldı — emir verebilirsiniz"; 1 -> "Normal hız"; 2 -> "2× hız"; else -> "3× hız" })
        }
        speedBtn.actor = b
    }

    internal fun updateSpeedButtons() = rebuildSpeed()

    private fun buildMiddle() {
        val middle = Table().apply { touchable = Touchable.childrenOnly; top() }
        alertBox.pad(14f, 22f, 18f, 22f)
        alertBox.add(ui.icon("warning", Color.WHITE)).size(38f).padRight(10f)
        alertBox.add(alertLabel).growX()
        alertBox.onClick { openMilitary() }
        middle.add(alertBox).width(690f).padTop(6f).row()

        goalCard.background = ui.card
        goalCard.pad(14f, 16f, 14f, 16f)
        middle.add(goalCard).width(690f).padTop(8f).row()
        rebuildGoalCard()

        val adviceBtn = Button(Button.ButtonStyle(ui.chip, ui.chip, null))
        adviceBtn.add(ui.icon("info", Palette.accentDark)).size(30f).padRight(6f)
        adviceBtn.add(adviceBtnLabel)
        adviceBtn.add(adviceDot).size(22f).padLeft(6f)
        adviceBtn.pad(6f, 14f, 8f, 14f)
        adviceBtn.onClick { openAdvice() }
        middle.add(adviceBtn).left().padLeft(16f).padTop(6f).row()

        middle.add(toastBox).width(620f).padTop(10f).row()
        root.add(middle).grow().row()
    }

    private fun rebuildGoalCard() {
        goalCard.clearChildren()
        val head = Table()
        head.add(ui.icon("star", Palette.orange)).size(30f).padRight(8f)
        head.add(ui.small("HEDEF", Palette.orange)).left().expandX()
        head.add(ui.iconButton(if (goalCollapsed) "down" else "up", null, ui.smallButtonStyle, iconSize = 22f) { goalCollapsed = !goalCollapsed; rebuildGoalCard() }).size(54f, 48f)
        goalCard.add(head).growX().row()
        if (goalCollapsed) { goalCard.add(goalTitle).growX().left(); return }
        goalCard.add(goalTitle).growX().left().padTop(2f).row()
        val row = Table()
        row.add(goalBar).height(14f).growX().padRight(10f)
        row.add(goalInfo).padRight(10f)
        row.add(ui.accent("Yap") { doGoalAction() }).height(58f).width(110f)
        goalCard.add(row).growX().padTop(6f)
    }

    // ---------------------------------------------------------------- Alt

    private fun buildBottom() {
        contextBox.background = ui.panel
        contextBox.pad(14f, 18f, 20f, 18f)
        contextBox.add(hintLabel).growX().left().padBottom(6f).row()
        contextBox.add(contextRow).growX()
        root.add(contextBox).growX().pad(0f, 10f, 0f, 10f).row()
        bottomBar.background = ui.bottom
        bottomBar.pad(14f, 12f, 18f, 12f)
        root.add(bottomBar).growX().pad(4f, 8f, 8f, 8f)
        rebuildBottom()
    }

    private fun rebuildBottom() {
        bottomBar.clearChildren()
        modeButtons.clear()
        fun main(key: String, icon: String, title: String, f: () -> Unit) {
            val b = ui.iconButton(icon, title, ui.buttonStyle, iconSize = 42f) { sounds.play(SoundCue.CLICK); f() }
            modeButtons[key] = b
            bottomBar.add(b).height(104f).growX().uniformX().pad(0f, 3f, 0f, 3f)
        }
        main("dig", "dig", "Kaz") { closeSheet(); setMode(if (view.mode == ToolMode.DIG) ToolMode.VIEW else ToolMode.DIG) }
        main("build", "build", "İnşa") { if (view.mode == ToolMode.BUILD) setMode(ToolMode.VIEW) else openBuildMenu() }
        main("mark", "mark", "İşaret") { closeSheet(); setMode(if (view.mode == ToolMode.PHEROMONE) ToolMode.VIEW else ToolMode.PHEROMONE) }
        if (armyShown) main("army", "army", "Ordu") { openMilitary() }
        main("colony", "colony", "Koloni") { openColony(0) }
        main("more", "more", "Diğer") { openMenu() }
        highlightModes()
    }

    private fun highlightModes() {
        val active = when (view.mode) {
            ToolMode.DIG, ToolMode.CANCEL, ToolMode.FILL -> "dig"
            ToolMode.BUILD -> "build"
            ToolMode.PHEROMONE -> "mark"
            ToolMode.ORDER_POINT, ToolMode.TARGET -> "army"
            ToolMode.VIEW -> ""
        }
        for ((k, b) in modeButtons) {
            val on = k == active
            b.style = Button.ButtonStyle(if (on) ui.nine("ui_btn_selected") else ui.nine("ui_btn"), ui.nine("ui_btn_down"), null)
            b.children.forEach { c -> c.color = if (on) Color.WHITE else Palette.uiText }
        }
    }

    fun setMode(m: ToolMode) {
        view.mode = m
        if (m != ToolMode.BUILD) view.clearGhost()
        if (m != ToolMode.VIEW) view.selectedCell = null
        hintLabel.setText(if (settings.showHints) m.hint else "")
        hintLabel.isVisible = settings.showHints && m != ToolMode.VIEW
        contextBox.isVisible = m != ToolMode.VIEW
        highlightModes()
        rebuildContext()
    }

    internal fun rebuildContext() = buildContextRow(this)

    fun onGhostMoved() = rebuildContext()

    // ---------------------------------------------------------------- Hedef ve öneriler

    private fun doGoalAction() {
        val g = sim.goals.active.firstOrNull() ?: return
        goalAction(this, g)
    }

    // ---------------------------------------------------------------- Güncelleme

    fun update(delta: Float) {
        val s = sim
        val env = s.environment
        val minutes = ((s.dayFraction * 24 * 60).toInt() + 6 * 60) % (24 * 60)
        dayLabel.setText("Gün ${s.day}  ${"%02d:%02d".format(minutes / 60, minutes % 60)}")
        seasonImg.drawable = com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(ui.art.r("season_${s.season.name}"))
        weatherImg.drawable = com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(ui.art.r(if (s.season == com.yeraltiimparatorlugu.sim.Season.WINTER && env.weather == com.yeraltiimparatorlugu.sim.Weather.CLOUDY) "wx_SNOW" else "wx_${env.weather.name}"))
        tempLabel.setText("${env.surfaceTemp.toInt()}°")
        coinLabel.setText("${s.coins}")
        if (speedBtn.actor == null) rebuildSpeed()
        val f = s.resources.edibleTotal(); val fc = s.capacityOf(StorageKind.FOOD)
        val m = s.resources.totalOf(StorageKind.MATERIAL); val mc = s.capacityOf(StorageKind.MATERIAL)
        foodLabel.setText("$f"); foodBar.value = f.toFloat() / fc.coerceAtLeast(1); foodBar.warn = f < s.foodConsumptionPerMinute() * 2
        matLabel.setText("$m/$mc"); matBar.value = m.toFloat() / mc.coerceAtLeast(1); matBar.warn = m >= mc
        popLabel.setText("${s.population}/${s.housing()}"); popBar.value = s.population.toFloat() / s.housing().coerceAtLeast(1); popBar.warn = s.population >= s.housing()
        val g = s.goals.active.firstOrNull()
        if (g != null) {
            goalTitle.setText(g.title)
            val p = g.progress(s).coerceIn(0, g.goalValue)
            goalInfo.setText("$p/${g.goalValue}")
            goalBar.value = p.toFloat() / g.goalValue.coerceAtLeast(1)
        }
        val adv = Advisor.advice(s)
        adviceBtnLabel.setText(if (adv.isEmpty()) "Her şey yolunda" else "Öneriler (${adv.size})")
        adviceDot.isVisible = adv.any { it.level == AdviceLevel.DANGER }
        val threats = s.creatures.count { it.kind.hostile && (it.cellY >= 0 || kotlin.math.abs(it.cellX) < 30) }
        alertBox.isVisible = threats > 0
        if (threats > 0) alertLabel.setText("$threats düşman yakında! Dokunun: Ordu ve emirler.")
        val wantArmy = s.militaryCount > 0 || s.isUnlocked("mil_soldiers") || s.stats.enemiesKilled > 0
        if (wantArmy != armyShown) { armyShown = wantArmy; rebuildBottom() }
        toastTime -= delta
        if (toastTime <= 0f) {
            val n = s.notifications.removeFirstOrNull()
            if (n != null) showToast(n) else toastBox.isVisible = false
        }
        refreshTimer -= delta
        if (refreshTimer <= 0f) {
            refreshTimer = 0.5f
            sheetRefresh?.invoke()
            if (view.mode == ToolMode.DIG || view.mode == ToolMode.CANCEL || view.mode == ToolMode.FILL) rebuildContext()
        }
        stage.act(delta)
    }

    fun showToast(text: String) {
        toast.setText(text)
        toastBox.isVisible = true
        toastTime = 3f
    }

    // ---------------------------------------------------------------- Panel altyapısı

    fun closeSheet() {
        sheet.remove()
        sheetLayer.clearChildren()
        sheetRefresh = null
        confirmReset = false
    }

    internal fun openSheet(title: String, heightFrac: Float = 0.62f, icon: String? = null, build: (Table) -> Unit) {
        closeSheet()
        sheet.clearChildren()
        sheet.pad(22f, 26f, 30f, 26f)
        val header = Table()
        if (icon != null) header.add(ui.icon(icon, Palette.accentDark)).size(44f).padRight(10f)
        header.add(ui.title(title)).left().expandX()
        header.add(ui.iconButton("close", null, ui.buttonStyle, iconSize = 26f) { closeSheet() }).size(68f, 64f)
        sheet.add(header).growX().padBottom(12f).row()
        val content = Table().top().left()
        build(content)
        val scroll = ScrollPane(content, ui.scrollStyle).apply { setScrollingDisabled(true, false); setFadeScrollBars(false) }
        sheet.add(scroll).grow()
        sheetLayer.add(sheet).growX().height(stage.viewport.worldHeight * heightFrac).pad(0f, 6f, 6f, 6f)
    }

    internal fun liveContent(fill: (Table) -> Unit) {
        sheetRefresh = { (sheet.children.lastOrNull() as? ScrollPane)?.let { sp -> (sp.actor as Table).apply { clearChildren(); fill(this) } } }
    }

    internal fun costRow(cost: Map<ResourceType, Int>): Table {
        val t = Table()
        if (cost.isEmpty()) t.add(ui.small("Ücretsiz", Palette.uiTextSoft))
        for ((r, n) in cost) {
            t.add(ui.icon(r)).size(30f)
            t.add(ui.small("$n", if (sim.resources[r] >= n) Palette.uiText else Palette.red)).padRight(10f)
        }
        return t
    }

    internal fun line(t: Table, text: String, color: Color = Palette.uiText) {
        t.add(ui.small(text, color, wrap = true)).growX().left().padBottom(6f).row()
    }

    internal fun header(t: Table, text: String) { t.add(ui.bold(text)).left().padTop(14f).padBottom(8f).row() }

    internal fun card(t: Table, kind: Int = 0, build: (Table) -> Unit) {
        val row = Table().apply { background = when (kind) { 1 -> ui.cardHi; 2 -> ui.cardWarn; else -> ui.card }; pad(14f, 16f, 14f, 16f) }
        build(row)
        t.add(row).growX().padBottom(8f).row()
    }

    internal fun result(msg: String?, ok: String) { showToast(msg ?: ok); sounds.play(if (msg == null) SoundCue.BUILD else SoundCue.CLICK) }

    // ---------------------------------------------------------------- Dünya etiketleri

    private inner class WorldLabels : Actor() {
        private val v = Vector3()
        private val layout = com.badlogic.gdx.graphics.g2d.GlyphLayout()
        override fun draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: Float) {
            val cam = renderer.camera
            if (cam.zoom > 2.4f) return
            val font = ui.art.fontSmall
            val sw = stage.viewport.screenWidth.toFloat()
            val sh = stage.viewport.screenHeight.toFloat()
            fun label(text: String, wx: Float, wy: Float, maxW: Float, c: Color) {
                v.set(wx, wy, 0f)
                cam.project(v, 0f, 0f, sw, sh)
                val x = v.x / sw * stage.viewport.worldWidth
                val y = v.y / sh * stage.viewport.worldHeight
                if (x < -100 || x > stage.viewport.worldWidth + 100 || y < 0 || y > stage.viewport.worldHeight) return
                layout.setText(font, text)
                if (layout.width > maxW - 10f) return
                val bw = layout.width + 20f
                batch.setColor(1f, 1f, 1f, 0.82f)
                ui.art.nine("ui_chip").draw(batch, x - bw / 2f, y - 40f, bw, 36f)
                font.color = c
                font.draw(batch, text, x - 150f, y - 14f, 300f, Align.center, false)
            }
            val unit = stage.viewport.worldWidth / (cam.viewportWidth * cam.zoom)
            for (r in sim.rooms) label(if (r.level > 1) "${r.type.title} ${r.level}" else r.type.title, r.x + r.width / 2f, -r.y.toFloat() - 0.05f, r.width * unit, Palette.uiText)
            for (r in sim.rivalSystem.rivals) if (r.discovered && r.alive) label(r.name, r.x + 0.5f, 2.6f, 14 * unit, Palette.hex("A8382A"))
            batch.setColor(Color.WHITE)
            font.color = Color.WHITE
        }
    }

    fun resize(w: Int, h: Int) { stage.viewport.update(w, h, true) }

    fun dispose() = stage.dispose()
}
