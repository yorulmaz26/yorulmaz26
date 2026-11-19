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
import com.yeraltiimparatorlugu.sim.AntType
import com.yeraltiimparatorlugu.sim.BroodStage
import com.yeraltiimparatorlugu.sim.DigDesignation
import com.yeraltiimparatorlugu.sim.PheromoneType
import com.yeraltiimparatorlugu.sim.ResourceType
import com.yeraltiimparatorlugu.sim.Room
import com.yeraltiimparatorlugu.sim.RoomState
import com.yeraltiimparatorlugu.sim.RoomType
import com.yeraltiimparatorlugu.sim.Simulation
import com.yeraltiimparatorlugu.sim.StorageKind
import com.yeraltiimparatorlugu.sim.TaskCategory

/** Oyun ekranından arayüzün çağırdığı işlemler. */
interface GameActions {
    val sim: Simulation
    fun saveNow()
    fun newColony()
    fun focus(x: Int, y: Int)
}

/**
 * Dikey ekran arayüzü: üst bilgi çubuğu, bildirimler, hedef kartı, alt araç çubuğu ve açılır paneller.
 * Her düğme gerçek bir simülasyon işlemine bağlıdır.
 */
class Hud(private val ui: Ui, private val view: ViewState, private val actions: GameActions, private val renderer: WorldRenderer, private val settings: Settings) {
    val stage = Stage(ExtendViewport(720f, 1280f))
    private val sim get() = actions.sim

    private val root = Table().apply { setFillParent(true); touchable = Touchable.childrenOnly; top() }
    private val dayLabel = ui.label("")
    private val popLabel = ui.small("")
    private val resLabels = HashMap<ResourceType, Label>()
    private val storageLabel = ui.small("")
    private val speedButtons = ArrayList<TextButton>()
    private val toast = ui.label("", wrap = true, align = Align.center)
    private val toastBox = Container(toast).apply { background = ui.bg(Palette.hex("FFF3C4")); pad(12f); fillX(); isVisible = false }
    private var toastTime = 0f
    private val goalLabel = ui.small("", wrap = true)
    private val goalBox = Table().apply { background = ui.panelSoft; pad(10f) }
    private val hintLabel = ui.small("", Palette.uiTextSoft, wrap = true)
    private val contextRow = Table()
    private val modeButtons = HashMap<ToolMode, TextButton>()
    private val sheetLayer = Table().apply { setFillParent(true); bottom(); touchable = Touchable.childrenOnly }
    private val sheet = Table().apply { background = ui.panel; pad(18f); top() }
    private var sheetRefresh: (() -> Unit)? = null
    private var refreshTimer = 0f
    private val labelsActor = WorldLabels()
    private var confirmReset = false

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

    // ---------------------------------------------------------------- Yerleşim

    private fun buildTopBar() {
        val bar = Table().apply { background = ui.panel; pad(12f, 14f, 10f, 14f) }
        val row1 = Table()
        row1.add(dayLabel).left().expandX()
        for ((i, t) in listOf("II", "1×", "2×", "3×").withIndex()) {
            val b = TextButton(t, ui.smallButtonStyle).onClick { view.speed = i; updateSpeedButtons() }
            speedButtons.add(b)
            row1.add(b).size(58f, 50f).padLeft(4f)
        }
        bar.add(row1).growX().row()
        bar.add(popLabel).left().padTop(2f).row()
        val chips = Table()
        for (r in ResourceType.entries) {
            val l = ui.small("0")
            resLabels[r] = l
            val chip = Table().apply { background = ui.chip; pad(2f, 4f, 2f, 6f) }
            chip.add(ui.icon(r)).size(30f)
            chip.add(l).padLeft(2f)
            chip.onClick { showToast("${r.title}: ${sim.resources[r]} (${r.storage.title} deposu)") }
            chips.add(chip).padRight(4f).padTop(6f).height(40f)
            if (r == ResourceType.LEAF) chips.row()
        }
        bar.add(chips).left().row()
        bar.add(storageLabel).left().padTop(4f)
        root.add(bar).growX().pad(8f, 8f, 0f, 8f).row()
        updateSpeedButtons()
    }

    private fun buildMiddle() {
        val middle = Table().apply { touchable = Touchable.childrenOnly; top() }
        goalBox.add(ui.small("HEDEF", Palette.accentDark)).left().row()
        goalBox.add(goalLabel).width(300f).left()
        goalBox.onClick { openColony(3) }
        middle.add(goalBox).left().top().pad(8f).width(330f)
        middle.add().expandX().row()
        middle.add(toastBox).colspan(2).growX().pad(8f, 60f, 0f, 60f)
        root.add(middle).grow().row()
    }

    private fun buildBottom() {
        val bottom = Table().apply { background = ui.panel; pad(10f) }
        bottom.add(hintLabel).growX().left().padBottom(6f).row()
        bottom.add(contextRow).growX().padBottom(6f).row()
        val tools = Table()
        for (m in ToolMode.entries) {
            val b = TextButton(m.title, ui.smallButtonStyle).onClick {
                if (m == ToolMode.BUILD) openBuildMenu() else { closeSheet(); setMode(m) }
            }
            modeButtons[m] = b
            tools.add(b).height(68f).growX().uniformX().pad(2f)
            if (m == ToolMode.BUILD) tools.row()
        }
        tools.add(ui.button("Koloni", small = true) { openColony(0) }).height(68f).growX().uniformX().pad(2f)
        tools.add(ui.button("Menü", small = true) { openMenu() }).height(68f).growX().uniformX().pad(2f)
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

    private fun rebuildContext() {
        contextRow.clearChildren()
        when (view.mode) {
            ToolMode.PHEROMONE -> for (p in PheromoneType.entries) {
                val b = TextButton(p.title, ui.smallButtonStyle).onClick { view.pheromone = p; rebuildContext(); showToast(p.description) }
                b.isChecked = view.pheromone == p
                b.label.color = if (b.isChecked) Color.WHITE else Palette.pheromone(p)
                contextRow.add(b).height(58f).growX().uniformX().pad(2f)
            }
            ToolMode.BUILD -> {
                val t = view.ghostType
                if (t != null) {
                    val gx = view.ghostX
                    val gy = view.ghostY
                    val check = if (gx != null && gy != null) sim.checkRoomPlacement(t, gx, gy) else null
                    contextRow.add(ui.small(t.title + "  " + (check?.reason ?: if (check == null) "Yer seçin" else "Uygun (${check.digCells} hücre kazılacak)"),
                        if (check?.ok == true) Palette.accentDark else Palette.red, wrap = true)).growX().left()
                    val ok = ui.accent("Onayla") { confirmPlacement() }
                    ok.isDisabled = check?.ok != true
                    contextRow.add(ok).height(58f).width(150f).padLeft(6f)
                    contextRow.add(ui.button("Vazgeç", small = true) { setMode(ToolMode.VIEW) }).height(58f).width(120f).padLeft(4f)
                }
            }
            ToolMode.DIG, ToolMode.CANCEL -> {
                contextRow.add(ui.small("Bekleyen kazı: ${sim.digOrders.size}")).growX().left()
                if (view.mode == ToolMode.CANCEL) contextRow.add(ui.danger("Tümünü sil") {
                    sim.digOrders.values.filter { it.roomId == -1 }.toList().forEach { sim.cancelDig(it.x, it.y) }; rebuildContext()
                }).height(54f)
                contextRow.add(ui.button("Bitti", small = true) { setMode(ToolMode.VIEW) }).height(54f).width(110f).padLeft(6f)
            }
            ToolMode.VIEW -> {}
        }
    }

    private fun confirmPlacement() {
        val t = view.ghostType ?: return
        val gx = view.ghostX ?: return
        val gy = view.ghostY ?: return
        val room = sim.placeRoom(t, gx, gy)
        if (room != null) {
            showToast("${t.title} planlandı: işçiler önce alanı kazacak, sonra inşa edecek.")
            setMode(ToolMode.VIEW)
        } else showToast(sim.checkRoomPlacement(t, gx, gy).reason ?: "Yerleştirilemedi")
    }

    fun onGhostMoved() = rebuildContext()

    // ---------------------------------------------------------------- Güncelleme

    fun update(delta: Float) {
        val s = sim
        val minutes = (s.dayFraction * 24 * 60).toInt()
        dayLabel.setText("Gün ${s.day} • ${"%02d:%02d".format(minutes / 60, minutes % 60)}")
        popLabel.setText("İşçi ${s.workerCount}/${s.housing()} • Yavru ${s.brood.size}")
        for ((r, l) in resLabels) l.setText(s.resources[r].toString())
        val f = s.resources.totalOf(StorageKind.FOOD)
        val fc = s.capacityOf(StorageKind.FOOD)
        val m = s.resources.totalOf(StorageKind.MATERIAL)
        val mc = s.capacityOf(StorageKind.MATERIAL)
        storageLabel.setText("Besin deposu $f/$fc   •   Malzeme deposu $m/$mc")
        storageLabel.color = if (f >= fc || m >= mc) Palette.red else Palette.uiTextSoft
        val g = s.goals.active.firstOrNull()
        goalLabel.setText(g?.let { "${it.title}\n${it.progress(s).coerceAtMost(it.goalValue)}/${it.goalValue} • Ödül: ${it.rewardAmount} ${it.rewardType.title}" } ?: "")
        // Bildirimler
        toastTime -= delta
        if (toastTime <= 0f) {
            val n = s.notifications.removeFirstOrNull()
            if (n != null) showToast(n) else toastBox.isVisible = false
        }
        refreshTimer -= delta
        if (refreshTimer <= 0f) {
            refreshTimer = 0.5f
            sheetRefresh?.invoke()
            if (view.mode == ToolMode.DIG || view.mode == ToolMode.CANCEL) rebuildContext()
        }
        stage.act(delta)
    }

    fun showToast(text: String) {
        toast.setText(text)
        toastBox.isVisible = true
        toastTime = 3f
    }

    private fun updateSpeedButtons() {
        speedButtons.forEachIndexed { i, b -> b.isChecked = i == view.speed }
    }

    // ---------------------------------------------------------------- Paneller

    fun closeSheet() {
        sheet.remove()
        sheetLayer.clearChildren()
        sheetRefresh = null
        confirmReset = false
    }

    private fun openSheet(title: String, heightFrac: Float = 0.62f, build: (Table) -> Unit) {
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

    private fun costRow(cost: Map<ResourceType, Int>): Table {
        val t = Table()
        if (cost.isEmpty()) t.add(ui.small("Ücretsiz", Palette.uiTextSoft))
        for ((r, n) in cost) {
            t.add(ui.icon(r)).size(28f)
            t.add(ui.small("$n", if (sim.resources[r] >= n) Palette.uiText else Palette.red)).padRight(10f)
        }
        return t
    }

    fun openBuildMenu() {
        openSheet("Oda İnşa Et") { c ->
            for (t in RoomType.entries.filter { it.buildable }) {
                val row = Table().apply { background = ui.panelSoft; pad(12f) }
                val head = Table()
                head.add(ui.swatch(Palette.room(t))).size(34f).padRight(10f)
                head.add(ui.label(t.title)).left().expandX()
                head.add(ui.small("${t.width}×${t.height}", Palette.uiTextSoft)).padRight(10f)
                val pick = ui.accent("Seç") {
                    closeSheet()
                    setMode(ToolMode.BUILD)
                    view.ghostType = t
                    view.ghostX = null; view.ghostY = null
                    rebuildContext()
                }
                head.add(pick).height(56f).width(110f)
                row.add(head).growX().row()
                row.add(ui.small(t.description, Palette.uiTextSoft, wrap = true)).growX().padTop(4f).row()
                val info = Table()
                info.add(costRow(t.cost)).left().expandX()
                info.add(ui.small("Bakım: ${t.maintenanceSoil} toprak/gün", Palette.uiTextSoft))
                row.add(info).growX().padTop(6f)
                c.add(row).growX().padBottom(8f).row()
            }
        }
    }

    /** Sekmeler: 0 Genel, 1 Görevler, 2 Karıncalar, 3 Hedefler, 4 Günlük, 5 İstatistik */
    fun openColony(tab: Int) {
        val tabs = listOf("Genel", "Görevler", "Karıncalar", "Hedefler", "Günlük", "İstatistik")
        openSheet("Koloni", 0.7f) { c ->
            val bar = Table()
            for ((i, name) in tabs.withIndex()) {
                val b = TextButton(name, ui.smallButtonStyle).onClick { openColony(i) }
                b.isChecked = i == tab
                bar.add(b).height(54f).growX().uniformX().pad(2f)
                if (i == 2) bar.row()
            }
            c.add(bar).growX().padBottom(10f).row()
            val body = Table().top().left()
            c.add(body).growX().row()
            val fill: () -> Unit = when (tab) {
                0 -> { { overview(body) } }
                1 -> { { priorities(body) } }
                2 -> { { antList(body) } }
                3 -> { { goals(body) } }
                4 -> { { worldLog(body) } }
                else -> { { statistics(body) } }
            }
            fill()
            if (tab == 0 || tab == 2 || tab == 3) sheetRefresh = { body.clearChildren(); fill() }
        }
    }

    private fun line(t: Table, text: String, color: Color = Palette.uiText) {
        t.add(ui.small(text, color, wrap = true)).growX().left().padBottom(4f).row()
    }

    private fun overview(t: Table) {
        val s = sim
        val eggs = s.brood.count { it.stage == BroodStage.EGG }
        val larvae = s.brood.count { it.stage == BroodStage.LARVA }
        val pupae = s.brood.count { it.stage == BroodStage.PUPA }
        line(t, "Nüfus: ${s.workerCount} işçi, 1 kraliçe • Barınak ${s.housing()}")
        line(t, "Yavrular: $eggs yumurta, $larvae larva, $pupae pupa")
        val blocker = s.layBlocker()
        line(t, if (blocker == null) "Kraliçe yumurtluyor (sonraki: ${(Simulation.LAY_INTERVAL - s.queenLayTimer).toInt()} sn)" else "Kraliçe bekliyor: $blocker", if (blocker == null) Palette.accentDark else Palette.orange)
        line(t, "Yiyecek tüketimi: ~${"%.1f".format(s.foodConsumptionPerMinute())} birim/dk • Stok: ${s.resources.edibleTotal()}")
        line(t, "Günlük oda bakımı: ${s.dailyMaintenance()} toprak malzemesi")
        line(t, "Bekleyen kazı: ${s.digOrders.size} • Yerdeki yığın: ${s.piles.size} • Yüzey kaynakları: ${s.sources.size}")
        t.add(ui.label("Odalar")).left().padTop(10f).padBottom(6f).row()
        for (r in s.rooms) {
            val row = Table().apply { background = ui.panelSoft; pad(8f) }
            row.add(ui.swatch(Palette.room(r.type))).size(26f).padRight(8f)
            row.add(ui.small("${r.type.title} Sv.${r.level} • ${r.state.title}" + if (!r.maintained) " • bakımsız" else "")).left().expandX()
            row.add(ui.button("Git", small = true) { closeSheet(); actions.focus(r.anchorX, r.anchorY); selectCell(r.anchorX, r.anchorY) }).height(48f).width(80f)
            t.add(row).growX().padBottom(4f).row()
        }
    }

    private fun priorities(t: Table) {
        line(t, "1 en yüksek öncelik, 5 en düşük (↑ daha önemli, ↓ daha az önemli). Kapalı görevler hiç yapılmaz. Karıncalar önce yüksek öncelikli işleri, aynı öncelikte en yakını seçer. Uzmanlar önce kendi işine bakar ve +%15 verimle çalışır.", Palette.uiTextSoft)
        for (cat in TaskCategory.active) {
            val row = Table().apply { background = ui.panelSoft; pad(8f) }
            row.add(ui.label(cat.title)).left().expandX().row()
            val ctl = Table()
            val pl = ui.label(prioText(cat), align = Align.center)
            ctl.add(ui.small("Öncelik", Palette.uiTextSoft)).padRight(6f)
            ctl.add(ui.button("↑", small = true) { val p = sim.priorities[cat]; if (p > 1) sim.priorities.set(cat, p - 1); pl.setText(prioText(cat)) }).size(56f)
            ctl.add(pl).width(90f)
            ctl.add(ui.button("↓", small = true) { val p = sim.priorities[cat]; if (p in 1..4) sim.priorities.set(cat, p + 1); pl.setText(prioText(cat)) }).size(56f)
            ctl.add(ui.button(if (sim.priorities[cat] == 0) "Aç" else "Kapat", small = true) {
                sim.priorities.set(cat, if (sim.priorities[cat] == 0) 3 else 0); openColony(1)
            }).height(56f).width(92f).padLeft(4f)
            ctl.add().expandX()
            val sl = ui.label("${sim.specialistCount(cat)}", align = Align.center)
            ctl.add(ui.small("Uzman", Palette.uiTextSoft)).padRight(6f)
            ctl.add(ui.button("−", small = true) { sim.setSpecialists(cat, (sim.specialistCount(cat) - 1).coerceAtLeast(0)); sl.setText("${sim.specialistCount(cat)}") }).size(56f)
            ctl.add(sl).width(50f)
            ctl.add(ui.button("+", small = true) { sim.setSpecialists(cat, sim.specialistCount(cat) + 1); sl.setText("${sim.specialistCount(cat)}") }).size(56f)
            row.add(ctl).growX()
            t.add(row).growX().padBottom(6f).row()
        }
    }

    private fun prioText(cat: TaskCategory): String = sim.priorities[cat].let { if (it == 0) "Kapalı" else "$it" }

    private fun antList(t: Table) {
        val s = sim
        val list = s.ants.sortedWith(compareBy({ it.type != AntType.QUEEN }, { it.id })).take(80)
        for (a in list) {
            val row = Table().apply { background = ui.panelSoft; pad(6f, 8f, 6f, 8f) }
            val spec = a.assignment?.let { " • Uzman: ${it.title}" } ?: ""
            val text = if (a.type == AntType.QUEEN) "Kraliçe • Can ${a.hp.toInt()}/${a.type.maxHp.toInt()}"
            else "İşçi #${a.id} Sv.${a.level} • ${a.activity.title}$spec\nAçlık ${a.hunger.toInt()} • Enerji ${a.energy.toInt()} • Can ${a.hp.toInt()} • Deneyim ${a.xp}"
            row.add(ui.small(text, if (a.starving) Palette.red else Palette.uiText, wrap = true)).growX().left()
            row.add(ui.button("Git", small = true) { closeSheet(); actions.focus(a.cellX, a.cellY) }).height(48f).width(80f)
            t.add(row).growX().padBottom(4f).row()
        }
        if (s.ants.size > 80) line(t, "… ve ${s.ants.size - 80} karınca daha", Palette.uiTextSoft)
    }

    private fun goals(t: Table) {
        line(t, "Hedefler koloninin durumuna göre sürekli yenilenir. Tamamlanan: ${sim.goals.completed}", Palette.uiTextSoft)
        for (g in sim.goals.active) {
            val row = Table().apply { background = ui.panelSoft; pad(10f) }
            row.add(ui.label(g.title)).left().expandX().row()
            val p = g.progress(sim).coerceIn(0, g.goalValue)
            row.add(ui.small("İlerleme: $p/${g.goalValue} • Ödül: ${g.rewardAmount} ${g.rewardType.title}", Palette.uiTextSoft)).left()
            t.add(row).growX().padBottom(6f).row()
        }
    }

    private fun worldLog(t: Table) {
        for (e in sim.log.asReversed().take(80)) line(t, "Gün ${e.day}: ${e.text}")
    }

    private fun statistics(t: Table) {
        val st = sim.stats
        val h = (st.playTime / 3600).toInt()
        val m = ((st.playTime % 3600) / 60).toInt()
        listOf(
            "Oyun süresi: ${h} sa ${m} dk",
            "Kazılan hücre: ${st.cellsDug}",
            "Toplanan yiyecek: ${st.foodGathered} • Toplanan malzeme: ${st.materialsGathered}",
            "Yenen yiyecek: ${st.foodEaten}",
            "Bırakılan yumurta: ${st.eggsLaid} • Doğan işçi: ${st.antsBorn} • Ölen: ${st.antsDied}",
            "İnşa edilen oda: ${st.roomsBuilt} • Tünel çökmesi: ${st.collapses}",
            "Yüzeye atılan toprak: ${st.soilDumped}",
            "Tamamlanan hedef: ${st.goalsCompleted}",
            "Yüklü harita parçası: ${sim.world.loadedChunkCount}",
        ).forEach { line(t, it) }
    }

    fun openMenu() {
        openSheet("Menü", 0.55f) { c -> menuBody(c) }
    }

    private fun menuBody(c: Table) {
        c.add(ui.label("Ayarlar")).left().padBottom(6f).row()
        val q = TextButton(if (settings.lowQuality) "Grafik: Düşük (performans)" else "Grafik: Yüksek", ui.buttonStyle).onClick {
            settings.lowQuality = !settings.lowQuality; openMenu()
        }
        c.add(q).growX().height(64f).padBottom(6f).row()
        val hints = TextButton(if (settings.showHints) "İpuçları: Açık" else "İpuçları: Kapalı", ui.buttonStyle).onClick {
            settings.showHints = !settings.showHints; setMode(view.mode); openMenu()
        }
        c.add(hints).growX().height(64f).padBottom(6f).row()
        c.add(ui.button("Şimdi kaydet") { actions.saveNow(); showToast("Koloni kaydedildi") }).growX().height(64f).padBottom(14f).row()
        c.add(ui.small("Oyun otomatik olarak 30 saniyede bir ve uygulamadan çıkarken kaydedilir. Oyun kapalıyken en fazla 8 saat, yarı verimle ilerler: toplama, mantar üretimi, yumurta ve larva gelişimi. Kazı ve inşa yalnızca oyun açıkken yapılır.", Palette.uiTextSoft, wrap = true)).growX().padBottom(14f).row()
        val reset = ui.danger(if (confirmReset) "Emin misiniz? Tüm ilerleme silinir — tekrar dokunun" else "Yeni koloni başlat") {
            if (confirmReset) { actions.newColony(); closeSheet(); showToast("Yeni koloni kuruldu") } else { confirmReset = true; c.clearChildren(); menuBody(c) }
        }
        c.add(reset).growX().height(64f).padBottom(14f).row()
        c.add(ui.small("Yeraltı İmparatorluğu: Koloni Savaşları • Sürüm 0.2 (Aşama 1–2: kazı, işçiler, odalar, kaynaklar, üreme, görev öncelikleri, feromonlar, kayıt). Savaş, araştırma ve ekosistem sonraki aşamalarda.", Palette.uiTextSoft, wrap = true)).growX().row()
    }

    fun showOfflineReport(lines: List<String>, title: String) {
        openSheet(title, 0.45f) { c ->
            for (l in lines) line(c, "• $l")
            c.add(ui.accent("Devam") { closeSheet() }).growX().height(64f).padTop(10f).row()
        }
    }

    // ---------------------------------------------------------------- Hücre bilgisi

    fun selectCell(x: Int, y: Int) {
        view.selectedCell = x to y
        val room = if (y >= 0) sim.roomAt(x, y) else null
        openSheet(room?.type?.title ?: if (y < 0) "Yüzey" else "Hücre ($x, derinlik $y)", 0.42f) { c -> cellBody(c, x, y) }
        sheetRefresh = { (sheet.children.lastOrNull() as? ScrollPane)?.let { sp -> (sp.actor as Table).apply { clearChildren(); cellBody(this, x, y) } } }
    }

    private fun cellBody(c: Table, x: Int, y: Int) {
        val s = sim
        if (y < 0) {
            val src = s.sources.firstOrNull { kotlin.math.abs(it.x - x) <= 1 }
            if (src != null) {
                line(c, "${src.kind.title}: ${src.amount}/${src.maxAmount} ${src.kind.resource.title}")
                if (kotlin.math.abs(src.x) > Simulation.KNOWN_RANGE) line(c, "Uzak kaynak: karıncalar ancak Yiyecek feromonuyla buraya gider.", Palette.orange)
                c.add(ui.button("Yiyecek feromonu bırak") { s.placePheromone(PheromoneType.FOOD, src.x, -1); showToast("Yiyecek feromonu bırakıldı") }).growX().height(60f).row()
            } else line(c, "Açık yüzey. Kaynaklar burada belirir ve zamanla yenilenir.", Palette.uiTextSoft)
            return
        }
        val info = s.cellInfo(x, y)
        val room = info.room
        if (room != null) { roomBody(c, room); return }
        val t = info.terrain
        line(c, if (info.revealed || t.walkable) "${t.title}: ${t.description}" else "Keşfedilmemiş toprak. Yakınını kazınca içeriği görünür.")
        if (t.diggable) {
            val workers = maxOf(1, s.workerCount)
            line(c, "Kazı süresi: ~${"%.1f".format(info.estimatedSeconds)} sn (1 işçi)" + (if (info.revealed && t.yieldType != null) " • Verim: ${t.yieldAmount} ${t.yieldType!!.title}" else ""), Palette.uiTextSoft)
            if (workers > 0 && s.digOrders.isNotEmpty()) line(c, "Kuyrukta ${s.digOrders.size} kazı işi var.", Palette.uiTextSoft)
        }
        for (r in info.risks) line(c, "⚠ $r", Palette.orange)
        val traffic = s.trafficAt(x, y)
        if (traffic > 1) line(c, "Trafik: $traffic karınca bu hücrede — dar tünel yavaşlatıyor.", Palette.orange)
        val btns = Table()
        if (info.designated) btns.add(ui.danger("Kazıyı iptal et") { s.cancelDig(x, y); selectCell(x, y) }).growX().height(60f)
        else if (t.diggable) btns.add(ui.accent("Kazı işaretle") {
            val r = s.designateDig(x, y)
            showToast(r.message ?: "Kazı işaretlendi"); if (r == DigDesignation.OK) selectCell(x, y)
        }).growX().height(60f)
        for (p in listOf(PheromoneType.DIG, PheromoneType.RALLY, PheromoneType.AVOID)) {
            btns.add(ui.button(p.title, small = true) { s.placePheromone(p, x, y); showToast("${p.title} feromonu bırakıldı") }).height(60f).padLeft(4f)
        }
        c.add(btns).growX().padTop(6f).row()
        val here = s.ants.filter { it.cellX == x && it.cellY == y }
        for (a in here.take(4)) line(c, "${a.type.title} #${a.id}: ${a.activity.title}", Palette.uiTextSoft)
    }

    private fun roomBody(c: Table, r: Room) {
        val s = sim
        line(c, "${r.type.title} • Seviye ${r.level}/${r.type.maxLevel} • ${r.state.title}")
        line(c, r.type.description, Palette.uiTextSoft)
        if (r.functional) {
            val eff = (s.roomEfficiency(r) * 100).toInt()
            line(c, "Verim: %$eff" + (if (!r.maintained) " (bakımsız: %50)" else "") + " • Bakım: ${r.type.maintenanceSoil * r.level} toprak/gün")
            when (r.type) {
                RoomType.FOOD_STORAGE -> line(c, "Kapasite katkısı: ${60 * r.level} • Toplam besin: ${s.resources.totalOf(StorageKind.FOOD)}/${s.capacityOf(StorageKind.FOOD)}")
                RoomType.MATERIAL_STORAGE -> line(c, "Kapasite katkısı: ${80 * r.level} • Toplam malzeme: ${s.resources.totalOf(StorageKind.MATERIAL)}/${s.capacityOf(StorageKind.MATERIAL)}")
                RoomType.FUNGUS_FARM -> line(c, "Yaprak tamponu: ${r.leafBuffer}/${s.farmLeafCapacity(r)} • Üretim: ${"%.1f".format(s.farmRate(r) * 60)} mantar/dk (yaprak varken)")
                RoomType.REST_ROOM -> line(c, "Barınak: +${10 * r.level} • Dinlenme yeri: ${6 * r.level}")
                RoomType.EGG_ROOM, RoomType.LARVA_ROOM, RoomType.QUEEN_CHAMBER -> line(c, "Yavru yeri: ${s.brood.count { it.roomId == r.id }}/${s.broodCapacity(r)}")
            }
            if (r.type == RoomType.QUEEN_CHAMBER) line(c, s.layBlocker()?.let { "Kraliçe bekliyor: $it" } ?: "Kraliçe yumurtluyor", Palette.accentDark)
            val bonus = r.type.adjacency.keys.filter { t -> s.rooms.any { it.type == t && it.functional && it.isAdjacentTo(r) } }
            if (r.type.adjacency.isNotEmpty()) line(c, "Komşuluk: " + r.type.adjacency.entries.joinToString { (t, b) -> "${t.title} +%${(b * 100).toInt()}" + if (t in bonus) " ✓" else "" }, Palette.uiTextSoft)
        } else {
            line(c, if (r.state == RoomState.EXCAVATING) "Önce alan kazılıyor." else "İnşa: %${(r.buildProgress / r.workRequired * 100).toInt()} (en fazla ${Simulation.MAX_BUILDERS} işçi)", Palette.uiTextSoft)
        }
        val btns = Table()
        if (r.state == RoomState.ACTIVE && r.level < r.type.maxLevel) {
            btns.add(ui.small("Geliştir:")).padRight(6f)
            btns.add(costRow(r.type.upgradeCost(r.level + 1))).padRight(6f)
            val up = ui.accent("Geliştir") { if (s.upgradeRoom(r)) showToast("${r.type.title} geliştiriliyor") else showToast("Yetersiz kaynak"); selectCell(r.anchorX, r.anchorY) }
            up.isDisabled = !s.canUpgrade(r)
            btns.add(up).height(60f).width(150f)
        }
        btns.add().expandX()
        if (r.type != RoomType.QUEEN_CHAMBER) {
            btns.add(ui.danger(if (r.functional) "Yık (%50 iade)" else "İptal (tam iade)") { s.removeRoom(r); closeSheet(); view.selectedCell = null }).height(60f)
        }
        c.add(btns).growX().padTop(8f).row()
    }

    // ---------------------------------------------------------------- Dünya etiketleri

    /** Oda adlarını dünya üzerinde, arayüz yazı tipiyle gösterir. */
    private inner class WorldLabels : Actor() {
        private val v = Vector3()
        private val layout = com.badlogic.gdx.graphics.g2d.GlyphLayout()
        override fun draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: Float) {
            val cam = renderer.camera
            if (cam.zoom > 2.4f) return
            val font = ui.art.fontSmall
            val sw = stage.viewport.screenWidth.toFloat()
            val sh = stage.viewport.screenHeight.toFloat()
            for (r in sim.rooms) {
                v.set(r.x + r.width / 2f, -r.y.toFloat() - 0.05f, 0f)
                cam.project(v, 0f, 0f, sw, sh)
                val x = v.x / sw * stage.viewport.worldWidth
                val y = v.y / sh * stage.viewport.worldHeight
                if (x < -100 || x > stage.viewport.worldWidth + 100 || y < 0 || y > stage.viewport.worldHeight) continue
                val text = if (r.level > 1) "${r.type.title} ${r.level}" else r.type.title
                // Oda ekranda yazıdan darsa etiket gösterilmez (üst üste binmeyi önler).
                val roomW = r.width / (cam.viewportWidth * cam.zoom) * stage.viewport.worldWidth
                layout.setText(font, text)
                if (layout.width > roomW - 6f) continue
                font.color = Color(0.29f, 0.2f, 0.14f, 0.9f)
                font.draw(batch, text, x - 150f, y - 4f, 300f, Align.center, false)
            }
            font.color = Color.WHITE
        }
    }

    fun resize(w: Int, h: Int) {
        stage.viewport.update(w, h, true)
    }

    fun dispose() = stage.dispose()
}
