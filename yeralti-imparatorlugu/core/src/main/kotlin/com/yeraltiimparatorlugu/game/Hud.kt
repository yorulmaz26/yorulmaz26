package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.yeraltiimparatorlugu.sim.Reputation
import com.yeraltiimparatorlugu.sim.ResourceType
import com.yeraltiimparatorlugu.sim.Simulation
import com.yeraltiimparatorlugu.sim.StorageKind

/** Oyun ekranından arayüzün çağırdığı işlemler. */
interface GameActions {
    val sim: Simulation
    fun saveNow()
    fun newColony()
    fun focus(x: Int, y: Int)
}

/**
 * Dikey ekran arayüzü: üst bilgi çubuğu, bildirimler, hedef kartı, alt araç çubuğu ve açılır paneller.
 * Panellerin içerikleri [Screens.kt] dosyasındadır. Her düğme gerçek bir simülasyon işlemine bağlıdır.
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
    private val dayLabel = ui.label("")
    private val popLabel = ui.small("", wrap = true)
    private val climateLabel = ui.small("", Palette.uiTextSoft, wrap = true)
    private val resLabels = HashMap<ResourceType, Label>()
    private val advancedRow = Table()
    private var advancedShown = emptyList<ResourceType>()
    private val storageLabel = ui.small("", wrap = true)
    private val speedButtons = ArrayList<TextButton>()
    private val alertLabel = ui.small("", Color.WHITE, wrap = true, align = Align.center)
    private val alertBox = Container(alertLabel).apply { background = ui.bg(Palette.red); pad(8f); fillX(); isVisible = false }
    private val toast = ui.label("", wrap = true, align = Align.center)
    private val toastBox = Container(toast).apply { background = ui.bg(Palette.hex("FFF3C4")); pad(12f); fillX(); isVisible = false }
    private var toastTime = 0f
    private val goalLabel = ui.small("", wrap = true)
    private val goalBox = Table().apply { background = ui.panelSoft; pad(10f) }
    private val hintLabel = ui.small("", Palette.uiTextSoft, wrap = true)
    internal val contextRow = Table()
    private val modeButtons = HashMap<ToolMode, TextButton>()
    private val sheetLayer = Table().apply { setFillParent(true); bottom(); touchable = Touchable.childrenOnly }
    internal val sheet = Table().apply { background = ui.panel; pad(18f); top() }
    internal var sheetRefresh: (() -> Unit)? = null
    private var refreshTimer = 0f
    private val labelsActor = WorldLabels()
    internal var confirmReset = false

    val sheetOpen: Boolean get() = sheet.parent != null

    init {
        stage.addActor(labelsActor)
        buildTopBar()
        buildMiddle()
        buildBottom()
        stage.addActor(root)
        stage.addActor(sheetLayer)
        setMode(ToolMode.VIEW)
    }

    private fun buildTopBar() {
        val bar = Table().apply { background = ui.panel; pad(10f, 14f, 8f, 14f) }
        val row1 = Table()
        row1.add(dayLabel).left().expandX()
        for ((i, t) in listOf("II", "1×", "2×", "3×").withIndex()) {
            val b = TextButton(t, ui.smallButtonStyle).onClick { view.speed = i; updateSpeedButtons(); sounds.play(com.yeraltiimparatorlugu.sim.SoundCue.CLICK) }
            speedButtons.add(b)
            row1.add(b).size(58f, 48f).padLeft(4f)
        }
        bar.add(row1).growX().row()
        bar.add(popLabel).width(670f).left().row()
        bar.add(climateLabel).width(670f).left().padBottom(2f).row()
        val chips = Table()
        val basic = listOf(ResourceType.FOOD, ResourceType.SEED, ResourceType.MUSHROOM, ResourceType.LEAF, ResourceType.WOOD, ResourceType.SOIL, ResourceType.WATER, ResourceType.MINERAL)
        for ((i, r) in basic.withIndex()) {
            chips.add(chip(r)).padRight(3f).padTop(4f).height(38f)
        }
        bar.add(chips).left().row()
        bar.add(advancedRow).left().row()
        bar.add(storageLabel).width(670f).left().padTop(2f)
        root.add(bar).growX().pad(8f, 8f, 0f, 8f).row()
        updateSpeedButtons()
    }

    private fun chip(r: ResourceType): Table {
        val l = ui.small("0")
        resLabels[r] = l
        val chip = Table().apply { background = ui.chip; pad(2f, 4f, 2f, 6f) }
        chip.add(ui.icon(r)).size(28f)
        chip.add(l).padLeft(2f)
        chip.onClick { showToast("${r.title}: ${sim.resources[r]} (${r.storage.title} deposu)") }
        return chip
    }

    private fun rebuildAdvanced() {
        val now = ResourceType.entries.filter { it.advanced && sim.resources[it] > 0 }
        if (now == advancedShown) return
        advancedShown = now
        advancedRow.clearChildren()
        for (r in now) advancedRow.add(chip(r)).padRight(4f).padTop(4f).height(38f)
    }

    private fun buildMiddle() {
        val middle = Table().apply { touchable = Touchable.childrenOnly; top() }
        middle.add(alertBox).colspan(2).width(690f).pad(6f, 8f, 0f, 8f).row()
        alertBox.onClick { openMilitary() }
        goalBox.add(ui.small("HEDEF", Palette.accentDark)).left().row()
        goalBox.add(goalLabel).width(300f).left()
        goalBox.onClick { openColony(3) }
        middle.add(goalBox).left().top().pad(8f).width(330f)
        middle.add().expandX().row()
        middle.add(toastBox).colspan(2).width(600f).pad(8f, 0f, 0f, 0f)
        root.add(middle).grow().row()
    }

    private fun buildBottom() {
        val bottom = Table().apply { background = ui.panel; pad(10f) }
        bottom.add(hintLabel).growX().left().padBottom(6f).row()
        bottom.add(contextRow).growX().padBottom(6f).row()
        val tools = Table()
        for (m in ToolMode.entries.filter { it.inToolbar }) {
            val b = TextButton(m.title, ui.smallButtonStyle).onClick {
                sounds.play(com.yeraltiimparatorlugu.sim.SoundCue.CLICK)
                if (m == ToolMode.BUILD) openBuildMenu() else { closeSheet(); setMode(m) }
            }
            modeButtons[m] = b
            tools.add(b).height(66f).growX().uniformX().pad(2f)
            if (m == ToolMode.BUILD) tools.row()
        }
        tools.add(ui.button("Ordu", small = true) { openMilitary() }).height(66f).growX().uniformX().pad(2f)
        tools.add(ui.button("Koloni", small = true) { openColony(0) }).height(66f).growX().uniformX().pad(2f)
        tools.add(ui.button("Menü", small = true) { openMenu() }).height(66f).growX().uniformX().pad(2f)
        bottom.add(tools).growX()
        root.add(bottom).growX().pad(0f, 8f, 8f, 8f)
    }

    fun setMode(m: ToolMode) {
        view.mode = m
        if (m != ToolMode.BUILD) view.clearGhost()
        if (m != ToolMode.VIEW) view.selectedCell = null
        for ((mode, b) in modeButtons) b.isChecked = mode == m
        hintLabel.setText(if (settings.showHints) m.hint else "")
        hintLabel.isVisible = settings.showHints
        rebuildContext()
    }

    internal fun rebuildContext() = buildContextRow(this)

    fun onGhostMoved() = rebuildContext()

    // ---------------------------------------------------------------- Güncelleme

    fun update(delta: Float) {
        val s = sim
        val minutes = (s.dayFraction * 24 * 60).toInt()
        val env = s.environment
        dayLabel.setText("Gün ${s.day} • ${"%02d:%02d".format(minutes / 60, minutes % 60)} • ${s.season.title}")
        popLabel.setText("Nüfus ${s.population}/${s.housing()} (asker ${s.militaryCount}) • Yavru ${s.brood.size} • ${s.coins} jeton • ${Reputation.title(s.reputation)}")
        climateLabel.setText("${env.weather.title} • Yüzey ${env.surfaceTemp.toInt()}°C • Koloni ${env.colonyTemp.toInt()}°C • Nem %${env.humidity.toInt()} • Yavru gelişimi ×${"%.2f".format(env.climateFactor(s.mods))}")
        for ((r, l) in resLabels) l.setText(s.resources[r].toString())
        rebuildAdvanced()
        val f = s.resources.totalOf(StorageKind.FOOD); val fc = s.capacityOf(StorageKind.FOOD)
        val m = s.resources.totalOf(StorageKind.MATERIAL); val mc = s.capacityOf(StorageKind.MATERIAL)
        val w = s.resources.totalOf(StorageKind.WATER); val wc = s.capacityOf(StorageKind.WATER)
        storageLabel.setText("Besin $f/$fc • Malzeme $m/$mc • Su $w/$wc")
        storageLabel.color = if (f >= fc || m >= mc) Palette.red else Palette.uiTextSoft
        val g = s.goals.active.firstOrNull()
        goalLabel.setText(g?.let { "${it.title}\n${it.progress(s).coerceAtMost(it.goalValue)}/${it.goalValue} • Ödül: ${it.rewardAmount} ${it.rewardType.title}" } ?: "")
        val threats = s.creatures.count { it.kind.hostile && (it.cellY >= 0 || kotlin.math.abs(it.cellX) < 30) }
        alertBox.isVisible = threats > 0
        if (threats > 0) alertLabel.setText("⚠ $threats düşman yakında! Ordu paneli için dokunun (oyunu duraklatıp emir verebilirsiniz).")
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

    internal fun updateSpeedButtons() { speedButtons.forEachIndexed { i, b -> b.isChecked = i == view.speed } }

    // ---------------------------------------------------------------- Panel altyapısı

    fun closeSheet() {
        sheet.remove()
        sheetLayer.clearChildren()
        sheetRefresh = null
        confirmReset = false
    }

    internal fun openSheet(title: String, heightFrac: Float = 0.62f, build: (Table) -> Unit) {
        closeSheet()
        sheet.clearChildren()
        val header = Table()
        header.add(ui.title(title)).left().expandX()
        header.add(ui.button("Kapat", small = true) { closeSheet() }).height(56f).width(120f)
        sheet.add(header).growX().padBottom(10f).row()
        val content = Table().top().left()
        build(content)
        val scroll = ScrollPane(content, ui.scrollStyle).apply { setScrollingDisabled(true, false); setFadeScrollBars(false) }
        sheet.add(scroll).grow()
        sheetLayer.add(sheet).growX().height(stage.viewport.worldHeight * heightFrac).pad(0f, 8f, 8f, 8f)
    }

    /** Panel içeriğini yerinde yeniler (kaydırma konumu korunur). */
    internal fun liveContent(fill: (Table) -> Unit) {
        sheetRefresh = { (sheet.children.lastOrNull() as? ScrollPane)?.let { sp -> (sp.actor as Table).apply { clearChildren(); fill(this) } } }
    }

    internal fun costRow(cost: Map<ResourceType, Int>): Table {
        val t = Table()
        if (cost.isEmpty()) t.add(ui.small("Ücretsiz", Palette.uiTextSoft))
        for ((r, n) in cost) {
            t.add(ui.icon(r)).size(26f)
            t.add(ui.small("$n", if (sim.resources[r] >= n) Palette.uiText else Palette.red)).padRight(8f)
        }
        return t
    }

    internal fun line(t: Table, text: String, color: Color = Palette.uiText) {
        t.add(ui.small(text, color, wrap = true)).growX().left().padBottom(4f).row()
    }

    internal fun header(t: Table, text: String) { t.add(ui.label(text)).left().padTop(10f).padBottom(6f).row() }

    internal fun card(t: Table, build: (Table) -> Unit) {
        val row = Table().apply { background = ui.panelSoft; pad(10f) }
        build(row)
        t.add(row).growX().padBottom(6f).row()
    }

    internal fun result(msg: String?, ok: String) { showToast(msg ?: ok); sounds.play(if (msg == null) com.yeraltiimparatorlugu.sim.SoundCue.BUILD else com.yeraltiimparatorlugu.sim.SoundCue.CLICK) }

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
                if (layout.width > maxW - 6f) return
                font.color = c
                font.draw(batch, text, x - 150f, y - 4f, 300f, Align.center, false)
            }
            val unit = stage.viewport.worldWidth / (cam.viewportWidth * cam.zoom)
            for (r in sim.rooms) label(if (r.level > 1) "${r.type.title} ${r.level}" else r.type.title, r.x + r.width / 2f, -r.y.toFloat() - 0.05f, r.width * unit, Color(0.29f, 0.2f, 0.14f, 0.9f))
            for (r in sim.rivalSystem.rivals) if (r.discovered && r.alive) label(r.name, r.x + 0.5f, 2.2f, 14 * unit, Palette.hex("8A2A1A"))
            font.color = Color.WHITE
        }
    }

    fun resize(w: Int, h: Int) { stage.viewport.update(w, h, true) }

    fun dispose() = stage.dispose()
}
