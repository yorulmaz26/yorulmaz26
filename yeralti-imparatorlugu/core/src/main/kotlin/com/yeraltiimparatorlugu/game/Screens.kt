package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.yeraltiimparatorlugu.sim.Achievement
import com.yeraltiimparatorlugu.sim.AdviceAction
import com.yeraltiimparatorlugu.sim.AdviceLevel
import com.yeraltiimparatorlugu.sim.Advisor
import com.yeraltiimparatorlugu.sim.AntType
import com.yeraltiimparatorlugu.sim.Biome
import com.yeraltiimparatorlugu.sim.BroodStage
import com.yeraltiimparatorlugu.sim.CreatureKind
import com.yeraltiimparatorlugu.sim.DigDesignation
import com.yeraltiimparatorlugu.sim.Difficulty
import com.yeraltiimparatorlugu.sim.EmpireGoalKind
import com.yeraltiimparatorlugu.sim.Environment
import com.yeraltiimparatorlugu.sim.EvolutionPath
import com.yeraltiimparatorlugu.sim.FossilKind
import com.yeraltiimparatorlugu.sim.GeneticTrait
import com.yeraltiimparatorlugu.sim.Goal
import com.yeraltiimparatorlugu.sim.GoalKind
import com.yeraltiimparatorlugu.sim.MarketItem
import com.yeraltiimparatorlugu.sim.MarketSlot
import com.yeraltiimparatorlugu.sim.OrderKind
import com.yeraltiimparatorlugu.sim.PheromoneType
import com.yeraltiimparatorlugu.sim.Recipe
import com.yeraltiimparatorlugu.sim.Reputation
import com.yeraltiimparatorlugu.sim.ResearchCategory
import com.yeraltiimparatorlugu.sim.ResearchTree
import com.yeraltiimparatorlugu.sim.ResourceType
import com.yeraltiimparatorlugu.sim.Room
import com.yeraltiimparatorlugu.sim.RoomState
import com.yeraltiimparatorlugu.sim.RoomType
import com.yeraltiimparatorlugu.sim.Simulation
import com.yeraltiimparatorlugu.sim.StorageKind
import com.yeraltiimparatorlugu.sim.TaskCategory
import com.yeraltiimparatorlugu.sim.Treaty

private fun sq(i: Int) = if (i == 0) "A" else "B"

// ==================================================================== Araç alt çubuğu

internal fun buildContextRow(h: Hud) {
    val ui = h.ui
    val view = h.view
    val sim = h.sim
    val row = h.contextRow
    row.clearChildren()
    when (view.mode) {
        ToolMode.DIG, ToolMode.CANCEL, ToolMode.FILL -> {
            val seg = Table()
            for ((m, icon) in listOf(ToolMode.DIG to "dig", ToolMode.CANCEL to "erase", ToolMode.FILL to "fill")) {
                val b = ui.iconButton(icon, m.title, if (view.mode == m) ui.primaryStyle else ui.buttonStyle, vertical = false, iconSize = 30f) { h.setMode(m) }
                seg.add(b).height(64f).growX().uniformX().pad(2f)
            }
            row.add(seg).growX()
            row.add(ui.accent("Bitti") { h.setMode(ToolMode.VIEW) }).height(64f).width(118f).padLeft(6f).row()
            row.add(ui.small("Bekleyen kazı ${sim.digOrders.size} • dolgu ${sim.fillOrders.size}", Palette.uiTextSoft)).colspan(2).left().padTop(4f)
        }
        ToolMode.PHEROMONE -> {
            val types = PheromoneType.entries.filter { sim.pheromoneUnlocked(it) }
            val grid = Table()
            for ((i, p) in types.withIndex()) {
                val sel = view.pheromone == p
                val b = TextButton(p.title, if (sel) ui.primaryStyle else ui.smallButtonStyle).onClick { view.pheromone = p; h.rebuildContext(); h.showToast(p.description) }
                if (!sel) b.label.color = Palette.pheromone(p)
                grid.add(b).height(54f).growX().uniformX().pad(2f)
                if (i % 3 == 2) grid.row()
            }
            row.add(grid).growX().row()
            row.add(ui.accent("Bitti") { h.setMode(ToolMode.VIEW) }).height(58f).growX().padTop(6f)
        }
        ToolMode.BUILD -> {
            val t = view.ghostType ?: return
            val gx = view.ghostX
            val gy = view.ghostY
            val check = if (gx != null && gy != null) sim.checkRoomPlacement(t, gx, gy) else null
            row.add(ui.bold(t.title)).left().row()
            row.add(ui.small(check?.reason ?: if (check == null) "Haritada bir yere dokunun" else "Uygun yer • ${check.digCells} hücre kazılacak", if (check?.ok == true) Palette.accentDark else Palette.red, wrap = true)).growX().left().padBottom(6f).row()
            val b = Table()
            val ok = ui.iconButton("check", "Kur", ui.primaryStyle, vertical = false, iconSize = 28f) {
                val room = if (gx != null && gy != null) sim.placeRoom(t, gx, gy) else null
                if (room != null) { h.showToast("${t.title} planlandı. İşçiler önce kazacak, sonra inşa edecek."); h.setMode(ToolMode.VIEW) }
                else h.showToast(check?.reason ?: "Yerleştirilemedi")
            }
            ok.isDisabled = check?.ok != true
            b.add(ok).height(64f).growX().pad(2f)
            b.add(ui.iconButton("target", "Önerilen yer", ui.buttonStyle, vertical = false, iconSize = 28f) { h.suggestPlacement(t) }).height(64f).growX().pad(2f)
            b.add(ui.iconButton("close", null, ui.buttonStyle, iconSize = 26f) { h.setMode(ToolMode.VIEW) }).size(70f, 64f).pad(2f)
            row.add(b).growX()
        }
        ToolMode.ORDER_POINT, ToolMode.TARGET -> {
            row.add(ui.bold("Birlik ${sq(view.pendingSquad)}: ${if (view.mode == ToolMode.TARGET) "saldırılacak düşmana dokunun" else view.pendingOrder.title + " — noktaya dokunun"}", Palette.accentDark, wrap = true)).growX().left()
            row.add(ui.button("Vazgeç", small = true) { h.setMode(ToolMode.VIEW); h.openMilitary() }).height(60f).width(120f)
        }
        ToolMode.VIEW -> {}
    }
}

// ==================================================================== Hedef ve öneri eylemleri

internal fun Hud.suggestPlacement(t: RoomType) {
    val p = sim.suggestRoomPlacement(t)
    if (p == null) { showToast("Uygun yer bulunamadı: önce biraz tünel kazın"); return }
    view.ghostType = t; view.ghostX = p.first; view.ghostY = p.second
    actions.focus(p.first + t.width / 2, p.second + t.height / 2)
    rebuildContext()
}

/** İnşa modunu başlatır ve en iyi yeri otomatik önerir; oyuncu isterse başka yere dokunur. */
fun Hud.startBuild(t: RoomType) {
    closeSheet()
    setMode(ToolMode.BUILD)
    view.ghostType = t
    view.ghostX = null; view.ghostY = null
    suggestPlacement(t)
}

internal fun goalAction(h: Hud, g: Goal) {
    val sim = h.sim
    when (g.kind) {
        GoalKind.DIG -> { h.setMode(ToolMode.DIG); h.actions.focus(-4, 9); h.showToast("Kazılacak hücrelere parmağınızı sürükleyin") }
        GoalKind.BUILD_ROOM -> g.roomType?.let { if (sim.roomUnlocked(it)) h.startBuild(it) else h.openResearch(0) }
        GoalKind.GATHER_FOOD -> { h.view.pheromone = PheromoneType.FOOD; h.setMode(ToolMode.PHEROMONE); h.actions.focus(8, -1); h.showToast("İşçiler kendiliğinden toplar. Yüzeyde bir kaynağın yanına Yiyecek işareti bırakın.") }
        GoalKind.POPULATION -> if (sim.population + sim.brood.size >= sim.housing()) h.startBuild(RoomType.REST_ROOM) else h.openAdvice()
        GoalKind.MUSHROOM_STOCK -> if (sim.rooms.none { it.type == RoomType.FUNGUS_FARM }) h.startBuild(RoomType.FUNGUS_FARM) else h.showToast("Mantar çiftliği yaprakları mantara çeviriyor; Üretim önceliğini yükseltebilirsiniz.")
        GoalKind.ROOMS_TOTAL -> h.openBuildMenu()
        GoalKind.UPGRADE_ROOM -> sim.rooms.firstOrNull { it.type == g.roomType && it.functional }?.let { h.actions.focus(it.anchorX, it.anchorY); h.selectCell(it.anchorX, it.anchorY) }
        GoalKind.RESEARCH -> if (sim.rooms.none { it.type == RoomType.RESEARCH_LAB }) h.startBuild(RoomType.RESEARCH_LAB) else h.openResearch(0)
    }
}

internal fun Hud.runAdvice(a: AdviceAction) {
    when (a) {
        is AdviceAction.Build -> startBuild(a.room)
        is AdviceAction.Research -> {
            val d = ResearchTree.get(a.id)
            if (d != null && sim.research.available(d) && sim.rooms.any { it.type == RoomType.RESEARCH_LAB && it.functional }) result(sim.startResearch(d), "Araştırma başladı: ${d.title}")
            else openResearch(d?.category?.ordinal ?: 0)
        }
        is AdviceAction.Open -> when (a.screen) {
            "military" -> openMilitary(); "tasks" -> openColony(2); "evolution" -> openEvolution(); "museum" -> openMuseum(); else -> openMenu()
        }
        is AdviceAction.Focus -> { closeSheet(); actions.focus(a.x, a.y) }
        AdviceAction.Dig -> { closeSheet(); setMode(ToolMode.DIG) }
    }
}

fun Hud.openAdvice() {
    openSheet("Öneriler", 0.55f, "info") { c ->
        val list = Advisor.advice(sim)
        if (list.isEmpty()) line(c, "Koloniniz iyi durumda. Yeni tüneller kazın, odalar kurun veya Diğer menüsünden araştırmalara bakın.", Palette.uiTextSoft)
        for (a in list) card(c, if (a.level == AdviceLevel.DANGER) 2 else if (a.level == AdviceLevel.WARN) 2 else 1) { row ->
            val col = Table().apply { left(); defaults().left() }
            col.add(ui.bold(a.title, if (a.level == AdviceLevel.DANGER) Palette.red else Palette.uiText)).left().row()
            col.add(ui.small(a.text, Palette.uiTextSoft, wrap = true)).growX().left()
            row.add(col).growX().left()
            val act = a.action
            if (act != null) row.add(ui.accent(a.actionLabel ?: "Yap") { runAdvice(act) }).height(60f).width(150f).padLeft(8f)
        }
    }
}

// ==================================================================== İnşa

fun Hud.openBuildMenu() {
    openSheet("Oda İnşa Et", 0.68f, "build") { c ->
        line(c, "Bir oda seçin: en uygun yer otomatik önerilir. İşçiler alanı kazar, sonra inşa eder.", Palette.uiTextSoft)
        for (t in RoomType.entries.filter { sim.roomUnlocked(it) }) card(c) { row ->
            row.add(ui.swatch(Palette.room(t))).size(52f).padRight(12f)
            val col = Table().apply { left(); defaults().left() }
            col.add(ui.bold(t.title)).left().row()
            col.add(ui.small(t.description, Palette.uiTextSoft, wrap = true)).growX().left().row()
            col.add(costRow(t.cost)).left().padTop(4f)
            row.add(col).growX().left()
            val b = ui.accent("Kur") { startBuild(t) }
            row.add(b).height(62f).width(100f).padLeft(8f)
        }
        val locked = RoomType.entries.count { it.buildable && !sim.roomUnlocked(it) }
        if (locked > 0) card(c, 1) { row ->
            row.add(ui.icon("lock", Palette.accentDark)).size(36f).padRight(10f)
            row.add(ui.small("$locked oda daha araştırmayla açılır.", Palette.accentDark, wrap = true)).growX().left()
            row.add(ui.button("Araştırma", small = true) { openResearch(0) }).height(56f)
        }
    }
}

// ==================================================================== Koloni

fun Hud.openColony(tab: Int) {
    val tabs = listOf("Durum" to "home", "Karıncalar" to "people", "Görevler" to "chart", "Günlük" to "book")
    openSheet("Koloni", 0.72f, "colony") { c ->
        val bar = Table()
        for ((i, t) in tabs.withIndex()) bar.add(ui.iconButton(t.second, t.first, if (i == tab) ui.primaryStyle else ui.buttonStyle, iconSize = 36f) { openColony(i) }).height(96f).growX().uniformX().pad(2f)
        c.add(bar).growX().padBottom(10f).row()
        val body = Table().top().left()
        c.add(body).growX().row()
        val fill: () -> Unit = when (tab) { 0 -> { { overview(body) } }; 1 -> { { antList(body) } }; 2 -> { { priorities(body) } }; else -> { { worldLog(body) } } }
        fill()
        if (tab != 2) sheetRefresh = { body.clearChildren(); fill() }
    }
}

private fun Hud.stat(t: Table, icon: com.badlogic.gdx.scenes.scene2d.Actor, title: String, value: String, sub: String? = null) {
    card(t) { row ->
        row.add(icon).size(46f).padRight(12f)
        val col = Table().apply { left(); defaults().left() }
        col.add(ui.small(title, Palette.uiTextSoft)).left().row()
        col.add(ui.bold(value)).left()
        if (sub != null) { col.row(); col.add(ui.small(sub, Palette.uiTextSoft, wrap = true)).growX().left() }
        row.add(col).growX().left()
    }
}

private fun Hud.overview(t: Table) {
    val s = sim
    val blocker = s.layBlocker()
    stat(t, ui.icon("people", Palette.accentDark), "Nüfus", "${s.population} / ${s.housing()} yer", "${s.workerCount} işçi, ${s.militaryCount} asker • yavru: ${s.brood.count { it.stage == BroodStage.EGG }} yumurta, ${s.brood.count { it.stage == BroodStage.LARVA }} larva, ${s.brood.count { it.stage == BroodStage.PUPA }} pupa")
    stat(t, ui.icon("crown", Palette.orange), "Kraliçe", if (blocker == null) "Yumurtluyor" else "Bekliyor", blocker)
    stat(t, ui.icon(ResourceType.FOOD), "Yiyecek", "${s.resources.edibleTotal()} birim", "Tüketim yaklaşık ${"%.1f".format(s.foodConsumptionPerMinute())} / dakika")
    val adv = Advisor.advice(s)
    if (adv.isNotEmpty()) c3(t, adv.size)
    header(t, "Odalar (${s.rooms.size})")
    for (r in s.rooms) card(t) { row ->
        row.add(ui.swatch(Palette.room(r.type))).size(30f).padRight(10f)
        row.add(ui.small("${r.type.title}  •  Sv.${r.level}  •  ${r.state.title}" + (if (!r.maintained) "  •  bakımsız" else ""))).left().expandX()
        row.add(ui.iconButton("eye", null, ui.buttonStyle, iconSize = 26f) { closeSheet(); actions.focus(r.anchorX, r.anchorY); selectCell(r.anchorX, r.anchorY) }).size(64f, 56f)
    }
}

private fun Hud.c3(t: Table, n: Int) {
    t.add(ui.iconButton("info", "$n öneriyi gör", ui.goldStyle, vertical = false, iconSize = 28f) { openAdvice() }).growX().height(64f).padBottom(8f).row()
}

private fun Hud.priorities(t: Table) {
    line(t, "Karıncalar önce yüksek öncelikli işleri yapar. Yıldızlara dokunarak önceliği değiştirin; boş bırakılan iş yapılmaz.", Palette.uiTextSoft)
    for (cat in TaskCategory.active) card(t) { row ->
        row.add(ui.bold(cat.title)).left().width(250f)
        val stars = Table()
        val p = sim.priorities[cat]
        // 5 yıldız = öncelik 1 (en yüksek); 0 yıldız = kapalı
        val filled = if (p == 0) 0 else 6 - p
        for (i in 1..5) {
            val on = i <= filled
            stars.add(ui.icon("star", if (on) Palette.orange else Palette.hex("E6D6BC")).onClick {
                sim.priorities.set(cat, if (filled == i) 0 else 6 - i); openColony(2)
            }).size(44f).pad(2f)
        }
        row.add(stars).expandX().right()
    }
    header(t, "Uzman işçiler")
    line(t, "Uzmanlar önce kendi işine bakar ve %15 daha verimli çalışır.", Palette.uiTextSoft)
    for (cat in listOf(TaskCategory.DIG, TaskCategory.GATHER, TaskCategory.BUILD, TaskCategory.HAUL, TaskCategory.NURSE, TaskCategory.RESEARCH)) card(t) { row ->
        row.add(ui.small(cat.title)).left().expandX()
        val n = sim.specialistCount(cat)
        row.add(ui.iconButton("minus", null, ui.buttonStyle, iconSize = 22f) { sim.setSpecialists(cat, (n - 1).coerceAtLeast(0)); openColony(2) }).size(60f, 54f)
        row.add(ui.bold("$n", align = Align.center)).width(50f)
        row.add(ui.iconButton("plus", null, ui.buttonStyle, iconSize = 22f) { sim.setSpecialists(cat, n + 1); openColony(2) }).size(60f, 54f)
    }
}

private fun Hud.antList(t: Table) {
    val s = sim
    val counts = s.ants.groupingBy { it.type }.eachCount()
    val grid = Table()
    for ((i, e) in counts.entries.sortedBy { it.key.ordinal }.withIndex()) {
        val c = Table().apply { background = ui.chip; pad(6f, 12f, 8f, 12f) }
        c.add(ui.small("${e.key.title}  ${e.value}"))
        grid.add(c).pad(3f)
        if (i % 3 == 2) grid.row()
    }
    t.add(grid).left().padBottom(8f).row()
    for (a in s.ants.sortedWith(compareBy({ it.type.ordinal }, { it.id })).take(60)) card(t, if (a.sick || a.starving) 2 else 0) { row ->
        val col = Table().apply { left(); defaults().left() }
        col.add(ui.bold(if (a.type == AntType.QUEEN) "Kraliçe" else "${a.type.title} #${a.id}")).left().row()
        col.add(ui.small(if (a.type == AntType.QUEEN) "Koloninin kalbi" else a.activity.title + (a.assignment?.let { " • uzman: ${it.title.lowercase()}" } ?: "") + (if (a.sick) " • hasta" else ""), Palette.uiTextSoft)).left()
        row.add(col).growX().left()
        val hp = ui.bar(a.hp / a.maxHp(s.mods), warn = a.hp < a.maxHp(s.mods) * 0.4f)
        row.add(hp).width(110f).height(14f).padRight(10f)
        row.add(ui.iconButton("eye", null, ui.buttonStyle, iconSize = 24f) { closeSheet(); actions.focus(a.cellX, a.cellY) }).size(60f, 54f)
    }
}

private fun Hud.worldLog(t: Table) {
    for (e in sim.log.asReversed().take(100)) {
        val r = Table()
        r.add(ui.small("Gün ${e.day}", Palette.accentDark)).width(90f).top().left()
        r.add(ui.small(e.text, wrap = true)).growX().left()
        t.add(r).growX().padBottom(6f).row()
    }
}

// ==================================================================== Diğer menüsü

fun Hud.openMenu() {
    openSheet("Diğer", 0.72f, "more") { c ->
        val s = sim
        val items = ArrayList<Triple<String, String, () -> Unit>>()
        if (s.rooms.any { it.type == RoomType.RESEARCH_LAB }) items.add(Triple("research", "Araştırma") { openResearch(0) })
        if (s.militaryCount > 0 || s.isUnlocked("mil_soldiers") || s.stats.enemiesKilled > 0) items.add(Triple("army", "Ordu") { openMilitary() })
        if (s.rivalSystem.rivals.any { it.discovered }) items.add(Triple("handshake", "Diplomasi") { openRivals() })
        items.add(Triple("map", "Keşif") { openExploration() })
        if (s.evolution.level(s.reputation) > 0) items.add(Triple("leaf", "Evrim") { openEvolution() })
        if (s.resources[ResourceType.GENETIC] > 0 || s.isUnlocked("cb_genetics") || s.expeditions.discoveredCreatures.size > 1) items.add(Triple("dna", "Koleksiyon") { openCollection() })
        if (s.resources[ResourceType.FOSSIL] > 0 || s.museum.found.isNotEmpty() || s.isUnlocked("dig_paleo")) items.add(Triple("bone", "Fosil Müzesi") { openMuseum() })
        items.add(Triple("cart", "Market") { openMarket() })
        items.add(Triple("bag", "Envanter") { openInventory() })
        items.add(Triple("trophy", "İmparatorluk") { openEmpire() })
        items.add(Triple("chart", "İstatistik") { openStats() })
        items.add(Triple("leaf", "Ekosistem") { openEcosystem() })
        items.add(Triple("gear", "Ayarlar") { openSettings() })
        val grid = Table()
        for ((i, it) in items.withIndex()) {
            grid.add(ui.iconButton(it.first, it.second, ui.buttonStyle, iconSize = 56f) { it.third() }).height(136f).growX().uniformX().pad(4f)
            if (i % 3 == 2) grid.row()
        }
        c.add(grid).growX().row()
        line(c, "Yeni ekranlar oynadıkça açılır: laboratuvar kurunca araştırma, rakip bulunca diplomasi gibi.", Palette.uiTextSoft)
    }
}

fun Hud.openSettings() { openSheet("Ayarlar", 0.66f, "gear") { c -> settingsBody(c) } }

private fun Hud.settingsBody(c: Table) {
    fun toggle(text: String, f: () -> Unit) = c.add(TextButton(text, ui.buttonStyle).onClick { f(); openSettings() }).growX().height(66f).padBottom(8f).row()
    header(c, "Zorluk")
    val d = Table()
    for (diff in Difficulty.entries) {
        d.add(TextButton(diff.title, if (sim.difficulty == diff) ui.primaryStyle else ui.buttonStyle).onClick { sim.difficulty = diff; openSettings() }).growX().uniformX().height(62f).pad(2f)
    }
    c.add(d).growX().row()
    line(c, sim.difficulty.description, Palette.uiTextSoft)
    header(c, "Görüntü ve ses")
    toggle(if (settings.lowQuality) "Grafik: Düşük (pil dostu)" else "Grafik: Yüksek") { settings.lowQuality = !settings.lowQuality }
    toggle(if (settings.soundOn) "Ses: Açık" else "Ses: Kapalı") { settings.soundOn = !settings.soundOn }
    toggle("Ses düzeyi: %${(settings.volume * 100).toInt()}") { settings.volume = if (settings.volume >= 1f) 0.25f else settings.volume + 0.25f }
    toggle(if (settings.showHints) "İpuçları: Açık" else "İpuçları: Kapalı") { settings.showHints = !settings.showHints; setMode(view.mode) }
    c.add(ui.button("Şimdi kaydet") { actions.saveNow(); showToast("Koloni kaydedildi") }).growX().height(66f).padTop(6f).padBottom(8f).row()
    line(c, "Oyun 30 saniyede bir ve çıkarken otomatik kaydedilir. Kapalıyken en fazla 8 saat yarı hızla ilerler; o sırada baskın olmaz.", Palette.uiTextSoft)
    val reset = ui.danger(if (confirmReset) "Emin misiniz? Tekrar dokunun" else "Yeni koloni başlat") {
        if (confirmReset) { actions.newColony(); closeSheet(); showToast("Yeni koloni kuruldu") } else { confirmReset = true; c.clearChildren(); settingsBody(c) }
    }
    c.add(reset).growX().height(66f).padTop(10f).row()
}

fun Hud.showOfflineReport(lines: List<String>, title: String) {
    openSheet(title, 0.5f, "home") { c ->
        for (l in lines) card(c) { row -> row.add(ui.small(l, wrap = true)).growX().left() }
        c.add(ui.accent("Başla") { closeSheet() }).growX().height(70f).padTop(10f).row()
    }
}

// ==================================================================== Hücre, oda ve canlı bilgisi

fun Hud.selectCell(x: Int, y: Int) {
    view.selectedCell = x to y
    val room = if (y >= 0) sim.roomAt(x, y) else null
    val creature = sim.creatures.firstOrNull { kotlin.math.abs(it.x - (x + 0.5f)) < 1f && kotlin.math.abs(it.y - (y + 0.5f)) < 1f }
    val title = creature?.kind?.title ?: room?.type?.title ?: if (y < 0) Biome.at(sim.seed, x).title else sim.cellInfo(x, y).let { if (it.revealed || it.terrain.walkable) it.terrain.title else "Keşfedilmemiş toprak" }
    openSheet(title, 0.42f, if (room != null) "home" else if (creature != null) "warning" else "eye") { c -> cellBody(c, x, y) }
    liveContent { cellBody(it, x, y) }
}

private fun Hud.cellBody(c: Table, x: Int, y: Int) {
    val s = sim
    val creature = s.creatures.firstOrNull { kotlin.math.abs(it.x - (x + 0.5f)) < 1f && kotlin.math.abs(it.y - (y + 0.5f)) < 1f }
    if (creature != null) {
        line(c, creature.kind.description, Palette.uiTextSoft)
        card(c) { row -> row.add(ui.small("Can")).padRight(8f); row.add(ui.bar(creature.hp / creature.maxHp, true)).growX().height(14f) }
        if (creature.kind.hostile) {
            val b = Table()
            for (q in 0..1) b.add(ui.iconButton("sword", "Birlik ${sq(q)} saldırsın", ui.primaryStyle, vertical = false, iconSize = 26f) { s.setOrder(q, OrderKind.TARGET, targetId = creature.id); showToast("Hedef işaretlendi") }).growX().height(62f).pad(2f)
            c.add(b).growX().row()
        }
        return
    }
    if (y < 0) {
        val src = s.sources.firstOrNull { kotlin.math.abs(it.x - x) <= 1 }
        if (src != null) {
            card(c) { row -> row.add(ui.image(ui.art.sources[src.kind]!!)).size(64f).padRight(10f); row.add(ui.small("${src.kind.title}\n${src.amount} ${src.kind.resource.title} kaldı", wrap = true)).growX().left() }
            if (!s.isKnown(src.x)) line(c, "Uzak kaynak: karıncalar buraya ancak Yiyecek işaretiyle gider.", Palette.orange)
            c.add(ui.iconButton("mark", "Yiyecek işareti bırak", ui.primaryStyle, vertical = false, iconSize = 28f) { s.placePheromone(PheromoneType.FOOD, src.x, -1); showToast("İşaret bırakıldı") }).growX().height(64f).row()
        } else line(c, "Açık yüzey. Kaynaklar burada belirir; mevsim ve hava miktarlarını etkiler.", Palette.uiTextSoft)
        s.rivalSystem.rivals.firstOrNull { it.discovered && it.alive && kotlin.math.abs(it.x - x) <= 3 }?.let { r ->
            c.add(ui.iconButton("handshake", r.name, ui.buttonStyle, vertical = false, iconSize = 28f) { openRivals() }).growX().height(64f).row()
        }
        return
    }
    val info = s.cellInfo(x, y)
    val room = info.room
    if (room != null) { roomBody(c, room); return }
    val t = info.terrain
    if (info.revealed || t.walkable) line(c, t.description, Palette.uiTextSoft) else line(c, "İçeriği bilinmiyor. Yanına kazınca görünür.", Palette.uiTextSoft)
    if (t.diggable) card(c) { row ->
        row.add(ui.icon("dig", Palette.accentDark)).size(38f).padRight(10f)
        row.add(ui.small("Kazı ~${"%.0f".format(info.estimatedSeconds)} sn" + if (info.revealed && t.yieldType != null) "  •  ${t.yieldAmount} ${t.yieldType!!.title}" else "", wrap = true)).growX().left()
    }
    if (info.water > 0.05f) line(c, "Su seviyesi %${(info.water * 100).toInt()}", Palette.hex("2E7FB8"))
    for (r in info.risks) card(c, 2) { row -> row.add(ui.icon("warning", Palette.orange)).size(30f).padRight(8f); row.add(ui.small(r, wrap = true)).growX().left() }
    val btns = Table()
    when {
        info.designated || info.fillDesignated -> btns.add(ui.iconButton("erase", "İşareti kaldır", ui.buttonStyle, vertical = false, iconSize = 26f) { s.cancelDig(x, y); selectCell(x, y) }).growX().height(64f)
        t.diggable -> btns.add(ui.iconButton("dig", "Kaz", ui.primaryStyle, vertical = false, iconSize = 28f) { val r = s.designateDig(x, y); showToast(r.message ?: "Kazı işaretlendi"); if (r == DigDesignation.OK) selectCell(x, y) }).growX().height(64f)
        t.walkable -> btns.add(ui.iconButton("fill", "Tüneli kapat", ui.buttonStyle, vertical = false, iconSize = 26f) { showToast(s.designateFill(x, y) ?: "Dolgu işaretlendi"); selectCell(x, y) }).growX().height(64f)
    }
    c.add(btns).growX().padTop(4f).row()
}

private fun Hud.roomBody(c: Table, r: Room) {
    val s = sim
    line(c, r.type.description, Palette.uiTextSoft)
    if (!r.functional) {
        val p = if (r.state == RoomState.EXCAVATING) 0f else r.buildProgress / r.workRequired
        card(c, 1) { row -> row.add(ui.small(if (r.state == RoomState.EXCAVATING) "Alan kazılıyor" else "İnşa ediliyor")).padRight(10f); row.add(ui.bar(p)).growX().height(14f) }
    } else {
        card(c) { row ->
            row.add(ui.small("Seviye ${r.level}/${r.type.maxLevel}  •  Verim %${(s.roomEfficiency(r) * 100).toInt()}" + (if (!r.maintained) "  •  bakımsız" else "") + (if (s.floodPenalty(r) > 0f) "  •  su bastı" else ""), wrap = true)).growX().left()
        }
        val info = when (r.type) {
            RoomType.FOOD_STORAGE -> "Besin: ${s.resources.totalOf(StorageKind.FOOD)}/${s.capacityOf(StorageKind.FOOD)}"
            RoomType.MATERIAL_STORAGE -> "Malzeme: ${s.resources.totalOf(StorageKind.MATERIAL)}/${s.capacityOf(StorageKind.MATERIAL)}"
            RoomType.WATER_STORAGE -> "Su: ${s.resources[ResourceType.WATER]}/${s.capacityOf(StorageKind.WATER)}"
            RoomType.FUNGUS_FARM -> "Yaprak: ${r.leafBuffer}/${s.farmLeafCapacity(r)} • ~${"%.0f".format(s.farmRate(r) * 60)} mantar/dk"
            RoomType.REST_ROOM -> "+${10 * r.level} barınak"
            RoomType.BARRACKS -> "+${8 * r.level} barınak • askerler burada dinlenir"
            RoomType.EGG_ROOM, RoomType.LARVA_ROOM, RoomType.QUEEN_CHAMBER -> "Yavru: ${s.brood.count { it.roomId == r.id }}/${s.broodCapacity(r)}" + if (r.type == RoomType.QUEEN_CHAMBER) " • " + (s.layBlocker() ?: "kraliçe yumurtluyor") else ""
            RoomType.RESEARCH_LAB -> s.research.currentDef()?.let { "Araştırma: ${it.title} %${(s.research.progress / it.work * 100).toInt()}" } ?: "Aktif araştırma yok"
            RoomType.ROYAL_CENTER -> "Kraliyet özü üretimi %${(r.stationProgress / Simulation.ROYAL_WORK * 100).toInt()}"
            else -> null
        }
        if (info != null) line(c, info)
        when (r.type) {
            RoomType.RESEARCH_LAB -> c.add(ui.iconButton("research", "Araştırma ağacı", ui.primaryStyle, vertical = false, iconSize = 28f) { openResearch(0) }).growX().height(64f).padBottom(6f).row()
            RoomType.GENETICS_LAB -> c.add(ui.iconButton("dna", "Genetik", ui.primaryStyle, vertical = false, iconSize = 28f) { openCollection() }).growX().height(64f).padBottom(6f).row()
            RoomType.EXPLORATION_CENTER -> c.add(ui.iconButton("map", "Seferler", ui.primaryStyle, vertical = false, iconSize = 28f) { openExploration() }).growX().height(64f).padBottom(6f).row()
            RoomType.TRADE_ROOM -> c.add(ui.iconButton("handshake", "Ticaret", ui.primaryStyle, vertical = false, iconSize = 28f) { openRivals() }).growX().height(64f).padBottom(6f).row()
            RoomType.FOSSIL_MUSEUM -> c.add(ui.iconButton("bone", "Müze", ui.primaryStyle, vertical = false, iconSize = 28f) { openMuseum() }).growX().height(64f).padBottom(6f).row()
            RoomType.ROYAL_CENTER -> c.add(ui.iconButton("crown", "Yan koloniler", ui.primaryStyle, vertical = false, iconSize = 28f) { openEmpire() }).growX().height(64f).padBottom(6f).row()
            RoomType.WORKSHOP -> {
                val rb = Table()
                for (rec in Recipe.entries) rb.add(TextButton(rec.title, if (r.recipe == rec) ui.primaryStyle else ui.smallButtonStyle).onClick { r.recipe = rec; r.stationProgress = 0f; selectCell(r.anchorX, r.anchorY) }).growX().uniformX().height(58f).pad(2f)
                c.add(rb).growX().row()
                c.add(costRow(r.recipe.input)).left().padBottom(6f).row()
            }
            RoomType.TRAINING_ROOM -> trainingBody(c, r)
            else -> {}
        }
    }
    val btns = Table()
    if (r.state == RoomState.ACTIVE && r.level < r.type.maxLevel) {
        val up = ui.iconButton("up", "Geliştir", ui.primaryStyle, vertical = false, iconSize = 26f) { result(if (s.upgradeRoom(r)) null else "Yetersiz kaynak", "${r.type.title} geliştiriliyor"); selectCell(r.anchorX, r.anchorY) }
        up.isDisabled = !s.canUpgrade(r)
        btns.add(up).height(64f).growX().padRight(6f)
        btns.add(costRow(r.type.upgradeCost(r.level + 1))).padRight(6f)
    }
    if (r.type != RoomType.QUEEN_CHAMBER) btns.add(ui.danger(if (r.functional) "Yık" else "İptal") { s.removeRoom(r); closeSheet(); view.selectedCell = null }).height(64f).width(110f)
    c.add(btns).growX().padTop(6f).row()
}

private fun Hud.trainingBody(c: Table, r: Room) {
    val s = sim
    val cls = r.trainClass
    line(c, if (cls != null && r.trainCount > 0) "Eğitiliyor: ${cls.title} (${r.trainCount} kaldı)" else "Bir sınıf seçin; sıradan işçiler eğitilir.", Palette.accentDark)
    for (t in AntType.entries.filter { it.trainable }) {
        val reason = s.canTrain(t)
        if (reason != null && reason.startsWith("Araştırma")) continue
        card(c) { row ->
            val col = Table().apply { left(); defaults().left() }
            col.add(ui.bold(t.title)).left().row()
            col.add(ui.small(t.description + (if (reason != null) " — $reason" else ""), if (reason != null) Palette.orange else Palette.uiTextSoft, wrap = true)).growX().left().row()
            col.add(costRow(t.trainCost)).left()
            row.add(col).growX().left()
            val b = ui.accent(if (cls == t) "+1" else "Eğit") { s.setTraining(r, t, if (cls == t) r.trainCount + 1 else 1); selectCell(r.anchorX, r.anchorY) }
            b.isDisabled = reason != null && reason != "Yetersiz kaynak"
            row.add(b).height(60f).width(96f)
        }
    }
    if (cls != null) c.add(ui.button("Eğitimi durdur") { s.setTraining(r, null, 0); selectCell(r.anchorX, r.anchorY) }).growX().height(60f).row()
}

// ==================================================================== Araştırma

fun Hud.openResearch(tab: Int) {
    openSheet("Araştırma", 0.76f, "research") { c ->
        val bar = Table()
        for ((i, cat) in ResearchCategory.entries.withIndex()) {
            bar.add(TextButton(cat.title, if (i == tab) ui.primaryStyle else ui.smallButtonStyle).onClick { openResearch(i) }).height(56f).growX().uniformX().pad(2f)
            if (i % 2 == 1) bar.row()
        }
        c.add(bar).growX().padBottom(8f).row()
        val body = Table().top().left()
        c.add(body).growX().row()
        val fill = { body.clearChildren(); researchBody(body, ResearchCategory.entries[tab]) }
        fill()
        sheetRefresh = fill
    }
}

private fun Hud.researchBody(t: Table, cat: ResearchCategory) {
    val s = sim
    val cur = s.research.currentDef()
    if (cur != null) card(t, 1) { row ->
        val col = Table().apply { left(); defaults().left() }
        col.add(ui.bold("Sürüyor: ${cur.title}")).left().row()
        col.add(ui.bar(s.research.progress / cur.work)).width(380f).height(14f).left().padTop(4f)
        row.add(col).growX().left()
        row.add(ui.danger("İptal") { s.cancelResearch() }).height(56f)
    }
    if (s.rooms.none { it.type == RoomType.RESEARCH_LAB && it.functional }) card(t, 2) { row ->
        row.add(ui.small("Araştırma için Araştırma Laboratuvarı gerekir.", wrap = true)).growX().left()
        row.add(ui.accent("Lab kur") { startBuild(RoomType.RESEARCH_LAB) }).height(58f)
    }
    for (d in ResearchTree.all.filter { it.category == cat }) {
        val done = d.id in s.research.completed
        val available = s.research.available(d)
        card(t, if (done) 1 else 0) { row ->
            val col = Table().apply { left(); defaults().left() }
            col.add(ui.bold(d.title, if (done) Palette.accentDark else if (available) Palette.uiText else Palette.uiTextSoft)).left().row()
            col.add(ui.small(d.description, Palette.uiTextSoft, wrap = true)).growX().left().row()
            if (!done) {
                col.add(costRow(d.cost)).left().padTop(4f).row()
                if (!available) col.add(ui.small("Önce: " + d.prereqs.filter { it !in s.research.completed }.joinToString { ResearchTree.get(it)?.title ?: it }, Palette.orange, wrap = true)).growX().left()
            }
            row.add(col).growX().left()
            when {
                done -> row.add(ui.icon("check", Palette.accentDark)).size(40f)
                s.research.current == d.id -> row.add(ui.small("Sürüyor", Palette.orange))
                available -> { val b = ui.accent("Başlat") { result(s.startResearch(d), "Araştırma başladı: ${d.title}") }; b.isDisabled = s.research.current != null; row.add(b).height(60f).width(118f) }
                else -> row.add(ui.icon("lock", Palette.uiTextSoft)).size(34f)
            }
        }
    }
}

// ==================================================================== Ordu

fun Hud.openMilitary() {
    openSheet("Ordu", 0.76f, "army") { c -> militaryBody(c) }
    liveContent { militaryBody(it) }
}

private fun Hud.militaryBody(c: Table) {
    val s = sim
    val enemies = s.creatures.filter { it.kind.hostile }.sortedBy { kotlin.math.abs(it.cellX) + kotlin.math.abs(it.cellY) }
    if (s.militaryCount == 0) card(c, 2) { row ->
        row.add(ui.small("Henüz asker yok. Asker Yetiştirme araştırması, Asker Kışlası ve Eğitim Odası gerekir. O zamana kadar tüneli Doldur aracıyla kapatabilirsiniz.", wrap = true)).growX().left()
    }
    line(c, "Duraklatıp (sağ üstteki hız düğmesi) sakin sakin emir verebilirsiniz.", Palette.uiTextSoft)
    for (q in 0..1) card(c) { row ->
        val o = s.orders[q]
        row.add(ui.bold("Birlik ${sq(q)}  •  ${s.squadSize(q)} asker  •  ${o.kind.title}")).left().row()
        val g = Table()
        val opts = listOf(OrderKind.DEFEND to "home", OrderKind.ATTACK to "sword", OrderKind.RETREAT to "down", OrderKind.GUARD to "flag", OrderKind.AMBUSH to "eye", OrderKind.TARGET to "target")
        for ((i, e) in opts.withIndex()) {
            val (k, icon) = e
            val b = ui.iconButton(if (icon == "flag") "map" else icon, k.title, if (o.kind == k) ui.primaryStyle else ui.buttonStyle, iconSize = 36f) {
                when {
                    k.needsPoint -> { view.pendingSquad = q; view.pendingOrder = k; closeSheet(); setMode(ToolMode.ORDER_POINT) }
                    k == OrderKind.TARGET -> { view.pendingSquad = q; closeSheet(); setMode(ToolMode.TARGET) }
                    else -> { s.setOrder(q, k); showToast("Birlik ${sq(q)}: ${k.title}") }
                }
            }
            g.add(b).growX().uniformX().height(92f).pad(2f)
            if (i % 3 == 2) g.row()
        }
        row.add(g).growX().row()
        row.add(ui.button("Diğer birlikten 3 asker al", small = true) { showToast("${s.sendSupport(q)} asker katıldı") }).growX().height(56f).padTop(4f)
    }
    c.add(TextButton(if (s.retreatWounded) "Yaralılar geri çekilsin: Açık" else "Yaralılar geri çekilsin: Kapalı", ui.buttonStyle).onClick { s.retreatWounded = !s.retreatWounded }).growX().height(60f).padBottom(8f).row()
    header(c, "Tehditler (${enemies.size})")
    if (enemies.isEmpty()) line(c, "Görünürde düşman yok.", Palette.uiTextSoft)
    for (e in enemies.take(10)) card(c, 2) { row ->
        row.add(ui.image(if (e.kind == CreatureKind.RIVAL_SOLDIER || e.kind == CreatureKind.RIVAL_ELITE) ui.art.antSoldier[0] else ui.art.creatures[e.kind]!![0], if (e.rivalId >= 0) Palette.creature(e.kind) else null)).size(56f).padRight(8f)
        row.add(ui.small(e.kind.title + (s.rivalSystem.byId(e.rivalId)?.let { "\n${it.name}" } ?: ""), wrap = true)).growX().left()
        row.add(ui.iconButton("eye", null, ui.buttonStyle, iconSize = 24f) { closeSheet(); actions.focus(e.cellX, e.cellY) }).size(60f, 56f)
        row.add(ui.iconButton("target", null, ui.primaryStyle, iconSize = 24f) { s.setOrder(0, OrderKind.TARGET, targetId = e.id); showToast("Birlik A hedefe saldırıyor") }).size(60f, 56f).padLeft(4f)
    }
    val rivals = s.rivalSystem.rivals.filter { it.discovered && it.alive }
    if (rivals.isNotEmpty() && s.militaryCount > 0) {
        header(c, "Rakibe sefer")
        for (r in rivals) card(c) { row ->
            row.add(ui.small("${r.name}\n~${r.soldiers} asker, savunma ${r.defense.toInt()}", wrap = true)).growX().left()
            row.add(ui.danger("Saldır") { s.setOrder(0, OrderKind.RAID_RIVAL, targetId = r.id); showToast("Birlik A ${r.name} üzerine yürüyor") }).height(58f)
        }
    }
}

// ==================================================================== Diplomasi

fun Hud.openRivals() {
    openSheet("Diplomasi", 0.76f, "handshake") { c -> rivalsBody(c) }
    liveContent { rivalsBody(it) }
}

private fun Hud.rivalsBody(c: Table) {
    val s = sim
    val known = s.rivalSystem.rivals.filter { it.discovered }
    line(c, "Bilinen ${known.size} koloni. Keşifçiler yeni koloniler bulur. Gücünüz: ${s.rivalSystem.playerPower(s).toInt()}", Palette.uiTextSoft)
    for (r in known) card(c, if (r.hostile) 2 else 0) { row ->
        row.add(ui.bold(r.name + if (!r.alive) " (dağıldı)" else "")).left().row()
        row.add(ui.small("${r.personality.title} • ${r.treaty.title} • güç ${r.strength.toInt()}", Palette.uiTextSoft)).left().row()
        val rel = Table()
        rel.add(ui.small("İlişki", Palette.uiTextSoft)).padRight(8f)
        rel.add(ui.bar((r.relation + 100) / 200f, r.relation < -15)).width(300f).height(14f)
        row.add(rel).left().padTop(4f).row()
        if (!r.alive) return@card
        val g = Table()
        fun b(t: String, style: TextButton.TextButtonStyle = ui.smallButtonStyle, f: () -> Unit) = g.add(TextButton(t, style).onClick(f)).growX().uniformX().height(56f).pad(2f)
        b("Hediye ver") { result(s.gift(r), "Hediye gönderildi (+ilişki)") }
        b("Ateşkes") { result(s.proposeCeasefire(r), "Ateşkes imzalandı") }; g.row()
        b("Ticaret") { result(s.proposeTrade(r), "Ticaret anlaşması yapıldı") }
        b("İttifak") { result(s.proposeAlliance(r), "İttifak kuruldu") }; g.row()
        b("Savaş ilan et", ui.dangerStyle) { s.declareWar(r); showToast("Savaş ilan edildi") }
        if (s.militaryCount > 0) b("Saldır", ui.dangerStyle) { s.setOrder(0, OrderKind.RAID_RIVAL, targetId = r.id); showToast("Birlik A yürüyor") }
        row.add(g).growX().padTop(6f).row()
        if (r.treaty == Treaty.TRADE || r.treaty == Treaty.ALLIANCE) for (o in s.rivalSystem.offers(r)) {
            val tr = Table()
            tr.add(ui.icon(o.give)).size(30f); tr.add(ui.small("${o.giveAmount}  ›  ")).padRight(4f)
            tr.add(ui.icon(o.get)).size(30f); tr.add(ui.small("${o.getAmount} ${o.get.title}")).left().expandX()
            tr.add(ui.accent("Takas") { result(s.trade(r, o), "Takas yapıldı") }).height(54f).width(110f)
            row.add(tr).growX().padTop(4f).row()
        }
    }
}

// ==================================================================== Keşif

private var expeditionSize = 4

fun Hud.openExploration() {
    openSheet("Keşif", 0.74f, "map") { c -> explorationBody(c) }
    liveContent { explorationBody(it) }
}

private fun Hud.explorationBody(c: Table) {
    val s = sim
    card(c) { row ->
        row.add(ui.icon("map", Palette.accentDark)).size(40f).padRight(10f)
        row.add(ui.small("Bilinen yüzey: ${s.knownMin()} … ${s.knownMax()}. Keşifçiler ve Keşif işareti haritayı genişletir.", wrap = true)).growX().left()
    }
    for (b in (Math.floorDiv(s.knownMin(), 36)..Math.floorDiv(s.knownMax(), 36))) {
        val x0 = b * 36
        val rivals = s.rivalSystem.rivals.filter { it.discovered && it.alive && it.x in x0 until x0 + 36 }
        line(c, "${Biome.at(s.seed, x0 + 18).title} (x ${x0}…${x0 + 35})" + (if (x0 <= 0 && x0 + 36 > 0) " • yuvanız" else "") + rivals.joinToString("") { " • ${it.name}" }, if (rivals.isNotEmpty()) Palette.red else Palette.uiText)
    }
    header(c, "Seferler")
    val slots = s.expeditions.slots(s)
    if (slots == 0) {
        card(c, 2) { row ->
            row.add(ui.small("Sefer için Keşif Merkezi gerekir.", wrap = true)).growX().left()
            if (s.roomUnlocked(RoomType.EXPLORATION_CENTER)) row.add(ui.accent("Kur") { startBuild(RoomType.EXPLORATION_CENTER) }).height(58f)
        }
        return
    }
    for (e in s.expeditions.active) {
        val r = s.expeditions.region(s.seed, e.regionIndex)
        card(c, 1) { row ->
            val col = Table().apply { left(); defaults().left() }
            col.add(ui.bold("${r.name} • ${e.members.size} karınca")).left().row()
            col.add(ui.bar(((s.time - e.startedAt) / e.duration).toFloat())).width(400f).height(14f).left().padTop(4f)
            row.add(col).growX().left()
        }
    }
    val sz = Table()
    sz.add(ui.small("Ekip")).padRight(8f)
    sz.add(ui.iconButton("minus", null, ui.buttonStyle, iconSize = 22f) { expeditionSize = (expeditionSize - 1).coerceAtLeast(2) }).size(60f, 54f)
    sz.add(ui.bold("$expeditionSize", align = Align.center)).width(50f)
    sz.add(ui.iconButton("plus", null, ui.buttonStyle, iconSize = 22f) { expeditionSize = (expeditionSize + 1).coerceAtMost(15) }).size(60f, 54f)
    sz.add(ui.small("karınca • yer ${s.expeditions.active.size}/$slots", Palette.uiTextSoft)).padLeft(8f)
    c.add(sz).left().padBottom(8f).row()
    for (r in s.expeditions.availableRegions(s.seed)) card(c) { row ->
        val col = Table().apply { left(); defaults().left() }
        col.add(ui.bold(r.name)).left().row()
        col.add(ui.small("${r.distanceMinutes} dk • tehlike ${r.danger}/9", Palette.uiTextSoft)).left()
        row.add(col).growX().left()
        val b = ui.accent("Gönder") { result(s.expeditions.start(s, r.index, expeditionSize), "Sefer yola çıktı") }
        b.isDisabled = slots <= s.expeditions.active.size
        row.add(b).height(58f).width(118f)
    }
}

// ==================================================================== Evrim ve koleksiyonlar

fun Hud.openEvolution() { openSheet("Evrim", 0.68f, "leaf") { c -> evolutionBody(c) } }

private fun Hud.evolutionBody(c: Table) {
    val s = sim
    card(c, 1) { row -> row.add(ui.small("İtibar ${s.reputation} • Seviye ${s.evolution.level(s.reputation)} • Harcanabilir puan: ${s.evolution.points(s.reputation)}", wrap = true)).growX().left() }
    line(c, "Puanları istediğiniz yollara dağıtın; karışık gelişim serbest. 3. kademe Kraliyet Merkezi ister.", Palette.uiTextSoft)
    for (p in EvolutionPath.entries) card(c) { row ->
        val rank = s.evolution.ranks[p.ordinal]
        val col = Table().apply { left(); defaults().left() }
        col.add(ui.bold(p.title)).left().row()
        col.add(ui.small(p.description, Palette.uiTextSoft, wrap = true)).growX().left().row()
        val dots = Table()
        for (i in 1..5) dots.add(ui.icon("star", if (i <= rank) Palette.orange else Palette.hex("E6D6BC"))).size(30f).padRight(2f)
        col.add(dots).left().padTop(4f)
        row.add(col).growX().left()
        val b = ui.accent("Geliştir") { result(s.evolve(p), "${p.title} geliştirildi"); openEvolution() }
        b.isDisabled = rank >= 5 || s.evolution.points(s.reputation) <= 0
        row.add(b).height(60f).width(126f)
    }
}

fun Hud.openCollection() {
    openSheet("Koleksiyon", 0.72f, "dna") { c -> collectionBody(c) }
    liveContent { collectionBody(it) }
}

private fun Hud.collectionBody(c: Table) {
    val s = sim
    line(c, "Genetik örnek: ${s.resources[ResourceType.GENETIC]}. Düşmanlardan, seferlerden ve eski yuvalardan gelir.", Palette.uiTextSoft)
    if (s.rooms.none { it.type == RoomType.GENETICS_LAB && it.functional }) line(c, "Özellik geliştirmek için Genetik Araştırma Odası gerekir.", Palette.orange)
    s.genetics.current?.let { card(c, 1) { row -> row.add(ui.small("Geliştiriliyor: ${it.title}")).padRight(8f); row.add(ui.bar(s.genetics.progress / it.work)).growX().height(14f) } }
    for (t in GeneticTrait.entries) card(c, if (t in s.genetics.unlocked) 1 else 0) { row ->
        val col = Table().apply { left(); defaults().left() }
        col.add(ui.bold(t.title)).left().row()
        col.add(ui.small(t.description, Palette.uiTextSoft)).left()
        row.add(col).growX().left()
        if (t in s.genetics.unlocked) row.add(ui.icon("check", Palette.accentDark)).size(40f) else {
            val b = ui.accent("${t.samples} örnek") { result(s.startTrait(t), "${t.title} geliştiriliyor") }
            b.isDisabled = s.genetics.current != null
            row.add(b).height(58f).width(130f)
        }
    }
    header(c, "Canlı ansiklopedisi (${s.expeditions.discoveredCreatures.size}/${CreatureKind.entries.size})")
    for (k in CreatureKind.entries) card(c) { row ->
        val known = k in s.expeditions.discoveredCreatures
        val img = if (k == CreatureKind.RIVAL_SOLDIER || k == CreatureKind.RIVAL_ELITE) ui.art.antSoldier[0] else ui.art.creatures[k]!![0]
        row.add(ui.image(img, if (known) (if (k.name.startsWith("RIVAL")) Palette.creature(k) else null) else Palette.hex("CDBFA8"))).size(58f).padRight(8f)
        row.add(ui.small(if (known) "${k.title}${if (k.hostile) " (tehlikeli)" else ""}\n${k.description}" else "Henüz karşılaşılmadı", if (known) Palette.uiText else Palette.uiTextSoft, wrap = true)).growX().left()
    }
}

fun Hud.openMuseum() { openSheet("Fosil Müzesi", 0.68f, "bone") { c -> museumBody(c) } }

private fun Hud.museumBody(c: Table) {
    val s = sim
    val cap = s.museum.capacity(s)
    card(c, 1) { row -> row.add(ui.small("İncelenmemiş fosil: ${s.resources[ResourceType.FOSSIL]} • Katalog ${s.museum.found.size}/${FossilKind.entries.size} • Sergi ${s.museum.displayed(s).size}/$cap", wrap = true)).growX().left() }
    if (cap == 0) line(c, "Fosilleri sergilemek için Fosil Müzesi gerekir (Paleontoloji araştırması).", Palette.orange)
    val ex = ui.iconButton("bone", "Fosil incele", ui.primaryStyle, vertical = false, iconSize = 28f) { showToast(s.museum.examine(s)); openMuseum() }
    ex.isDisabled = cap == 0 || s.resources[ResourceType.FOSSIL] <= 0
    c.add(ex).growX().height(66f).padBottom(8f).row()
    val shown = s.museum.displayed(s).toSet()
    val grid = Table()
    for ((i, f) in FossilKind.entries.withIndex()) {
        val found = f in s.museum.found
        val cell = Table().apply { background = if (f in shown) ui.cardHi else ui.card; pad(10f) }
        cell.add(ui.image(ui.art.icons[ResourceType.FOSSIL]!!, if (found) null else Palette.hex("CDBFA8"))).size(52f).row()
        cell.add(ui.small(if (found) f.title else "???", if (found) Palette.uiText else Palette.uiTextSoft, wrap = true, align = Align.center)).width(180f).row()
        cell.add(ui.small("Nadirlik ${f.rarity}", Palette.uiTextSoft)).row()
        grid.add(cell).growX().uniformX().pad(3f)
        if (i % 3 == 2) grid.row()
    }
    c.add(grid).growX().row()
}

fun Hud.openMarket() { openSheet("Market", 0.76f, "cart") { c -> marketBody(c) } }

private fun Hud.marketBody(c: Table) {
    val s = sim
    card(c, 1) { row ->
        row.add(ui.image(ui.art.r("coin"))).size(48f).padRight(10f)
        row.add(ui.small("${s.coins} jeton. Hedefler, başarımlar, zaferler ve seferlerle kazanılır. Gerçek para yoktur.", wrap = true)).growX().left()
    }
    for (slot in MarketSlot.entries) {
        header(c, slot.title)
        for (item in MarketItem.entries.filter { it.slot == slot }) card(c) { row ->
            val col = Table().apply { left(); defaults().left() }
            col.add(ui.bold(item.title)).left().row()
            col.add(ui.small(item.description, Palette.uiTextSoft, wrap = true)).growX().left()
            row.add(col).growX().left()
            if (item in s.market.owned && !item.consumable) row.add(ui.icon("check", Palette.accentDark)).size(40f)
            else { val b = ui.gold("${item.price}") { showToast(s.market.buy(s, item)); openMarket() }; b.isDisabled = s.coins < item.price; row.add(b).height(58f).width(110f) }
        }
    }
}

fun Hud.openInventory() { openSheet("Envanter", 0.72f, "bag") { c -> inventoryBody(c) } }

private fun Hud.inventoryBody(c: Table) {
    val s = sim
    for (kind in StorageKind.entries) {
        header(c, "${kind.title} deposu  ${s.resources.totalOf(kind)}/${s.capacityOf(kind)}")
        val grid = Table()
        var i = 0
        for (r in ResourceType.entries.filter { it.storage == kind && (s.resources[it] > 0 || !it.advanced) }) {
            val cell = Table().apply { background = ui.card; pad(8f, 10f, 8f, 10f) }
            cell.add(ui.icon(r)).size(40f).padRight(8f)
            val col = Table().apply { left(); defaults().left() }; col.add(ui.bold("${s.resources[r]}")).left().row(); col.add(ui.small(r.title, Palette.uiTextSoft)).left()
            cell.add(col).left().expandX()
            grid.add(cell).growX().uniformX().pad(3f)
            if (++i % 2 == 0) grid.row()
        }
        c.add(grid).growX().row()
    }
    header(c, "Eşyalar")
    if (s.market.owned.isEmpty()) line(c, "Henüz eşya yok. Marketten jetonla alabilirsiniz.", Palette.uiTextSoft)
    for (item in s.market.owned) card(c) { row ->
        row.add(ui.small("${item.title}  •  ${item.slot.title}")).left().expandX()
        if (item.equippable) row.add(TextButton(if (s.market.isEquipped(item)) "Kullanılıyor" else "Kullan", if (s.market.isEquipped(item)) ui.primaryStyle else ui.smallButtonStyle).onClick { s.market.equip(item); openInventory() }).height(56f).width(160f)
    }
}

// ==================================================================== İmparatorluk, istatistik, ekosistem

fun Hud.openEmpire() {
    openSheet("İmparatorluk", 0.76f, "trophy") { c -> empireBody(c) }
    liveContent { empireBody(it) }
}

private fun Hud.empireBody(c: Table) {
    val s = sim
    val rep = s.reputation
    val next = Reputation.titles.firstOrNull { it.first > rep }
    card(c, 1) { row ->
        row.add(ui.icon("crown", Palette.orange)).size(48f).padRight(10f)
        val col = Table().apply { left(); defaults().left() }
        col.add(ui.bold(Reputation.title(rep))).left().row()
        col.add(ui.small("İtibar $rep" + (next?.let { " • sonraki unvan: ${it.second} (${it.first})" } ?: ""), Palette.uiTextSoft, wrap = true)).growX().left()
        if (next != null) { col.row(); col.add(ui.bar(rep.toFloat() / next.first)).width(380f).height(12f).left().padTop(4f) }
        row.add(col).growX().left()
    }
    header(c, "Uzun vadeli hedef")
    val g = s.empireGoal
    if (g.kind != null) line(c, "${g.kind!!.title}: ${g.progress(s)}/${g.target()} ${g.kind!!.unit} (kademe ${g.tier})", Palette.accentDark)
    val gb = Table()
    for ((i, k) in EmpireGoalKind.entries.withIndex()) {
        gb.add(TextButton(k.title, if (g.kind == k) ui.primaryStyle else ui.smallButtonStyle).onClick { if (g.kind != k) { g.kind = k; g.tier = 0 }; openEmpire() }).growX().uniformX().height(58f).pad(2f)
        if (i % 2 == 1) gb.row()
    }
    c.add(gb).growX().row()
    val max = s.federation.maxColonies(s)
    if (max > 0 || s.federation.colonies.isNotEmpty()) {
        header(c, "Federasyon (yan koloniler ${s.federation.colonies.size}/$max)")
        for (col in s.federation.colonies) card(c) { row ->
            row.add(ui.small("${col.name} • ${col.population} karınca • gönderilen ${col.totalSent}")).left().expandX()
            row.add(ui.button("Destek", small = true) { result(s.federation.support(s, col), "Destek gönderildi") }).height(54f)
        }
        if (max > s.federation.colonies.size) c.add(ui.accent("Yeni yan koloni kur") { result(s.federation.found(s), "Yan koloni kuruldu") }).growX().height(64f).row()
    }
    header(c, "Başarımlar ${s.achievements.unlocked.size}/${Achievement.entries.size}")
    for (a in Achievement.entries) {
        val done = a in s.achievements.unlocked
        card(c, if (done) 1 else 0) { row ->
            row.add(ui.icon(if (done) "trophy" else "lock", if (done) Palette.orange else Palette.uiTextSoft)).size(34f).padRight(10f)
            val col = Table().apply { left(); defaults().left() }
            col.add(ui.bold(a.title, if (done) Palette.uiText else Palette.uiTextSoft)).left().row()
            col.add(ui.small(a.description, Palette.uiTextSoft)).left()
            row.add(col).growX().left()
            row.add(ui.small("+${a.coins}", Palette.orange))
        }
    }
}

fun Hud.openStats() {
    openSheet("İstatistik", 0.7f, "chart") { c ->
        val st = sim.stats
        val h = (st.playTime / 3600).toInt()
        val m = ((st.playTime % 3600) / 60).toInt()
        listOf(
            "Oyun süresi" to "$h sa $m dk • gün ${sim.day}",
            "Kazılan hücre" to "${st.cellsDug} • en derin ${st.maxDepth}",
            "Toplanan" to "${st.foodGathered} yiyecek • ${st.materialsGathered} malzeme",
            "Doğan / ölen" to "${st.antsBorn} / ${st.antsDied}",
            "İnşa edilen oda" to "${st.roomsBuilt}",
            "Araştırma" to "${sim.research.completed.size}/${ResearchTree.all.size}",
            "Yenilen düşman" to "${st.enemiesKilled} • püskürtülen baskın ${st.raidsRepelled}",
            "Seferler" to "${st.expeditionsSucceeded} başarılı • ${sim.expeditions.regionsCompleted} bölge",
            "Tünel çökmesi" to "${st.collapses}",
        ).forEach { (k, v) -> card(c) { row -> row.add(ui.small(k, Palette.uiTextSoft)).left().expandX(); row.add(ui.bold(v)).right() } }
    }
}

fun Hud.openEcosystem() {
    openSheet("Ekosistem", 0.66f, "leaf") { c -> ecosystemBody(c) }
    liveContent { ecosystemBody(it) }
}

private fun Hud.ecosystemBody(c: Table) {
    val s = sim
    val env = s.environment
    card(c) { row ->
        row.add(ui.image(ui.art.r("season_${s.season.name}"))).size(56f).padRight(10f)
        row.add(ui.small("${s.season.title} • yıl ${env.year(s.day)} • mevsimin ${(s.day - 1) % Environment.DAYS_PER_SEASON + 1}. günü", wrap = true)).growX().left()
    }
    card(c) { row ->
        row.add(ui.image(ui.art.r("wx_${env.weather.name}"))).size(56f).padRight(10f)
        row.add(ui.small("${env.weather.title} • yüzey ${env.surfaceTemp.toInt()}°C • koloni ${env.colonyTemp.toInt()}°C • nem %${env.humidity.toInt()}", wrap = true)).growX().left()
    }
    val cf = env.climateFactor(s.mods)
    card(c, if (cf < 0.8f) 2 else 1) { row ->
        row.add(ui.small("Yavru gelişimi", Palette.uiTextSoft)).padRight(10f)
        row.add(ui.bar(cf / 1.15f, cf < 0.8f)).growX().height(14f)
        row.add(ui.bold("×${"%.2f".format(cf)}")).padLeft(8f)
    }
    if (env.water.isNotEmpty()) line(c, "Su basmış ${env.water.size} hücre: odaların altına derin bir kuyu kazın; kum zemin suyu hızlı emer.", Palette.hex("2E7FB8"))
    line(c, "İlkbahar bol yağmurlu ve bereketli, yaz sıcak ve kurak olabilir, sonbahar yağışlı, kış soğuk ve kıt.", Palette.uiTextSoft)
    if (s.events.history.isNotEmpty()) {
        header(c, "Son olaylar")
        for ((d, k) in s.events.history.asReversed().take(8)) line(c, "Gün $d: ${k.title}")
    }
}
