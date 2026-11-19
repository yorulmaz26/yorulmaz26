package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.yeraltiimparatorlugu.sim.Achievement
import com.yeraltiimparatorlugu.sim.AntType
import com.yeraltiimparatorlugu.sim.Biome
import com.yeraltiimparatorlugu.sim.BroodStage
import com.yeraltiimparatorlugu.sim.Caste
import com.yeraltiimparatorlugu.sim.CreatureKind
import com.yeraltiimparatorlugu.sim.DigDesignation
import com.yeraltiimparatorlugu.sim.EmpireGoalKind
import com.yeraltiimparatorlugu.sim.Environment
import com.yeraltiimparatorlugu.sim.EvolutionPath
import com.yeraltiimparatorlugu.sim.FossilKind
import com.yeraltiimparatorlugu.sim.GeneticTrait
import com.yeraltiimparatorlugu.sim.MarketItem
import com.yeraltiimparatorlugu.sim.MarketSlot
import com.yeraltiimparatorlugu.sim.OfflinePurchaseBackend
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
import com.yeraltiimparatorlugu.sim.SoundCue
import com.yeraltiimparatorlugu.sim.StorageKind
import com.yeraltiimparatorlugu.sim.TaskCategory
import com.yeraltiimparatorlugu.sim.Treaty

// ==================================================================== Araç bağlam satırı

internal fun buildContextRow(h: Hud) {
    val ui = h.ui
    val view = h.view
    val sim = h.sim
    val row = h.contextRow
    row.clearChildren()
    when (view.mode) {
        ToolMode.PHEROMONE -> {
            val types = PheromoneType.entries.filter { sim.pheromoneUnlocked(it) }
            for ((i, p) in types.withIndex()) {
                val b = TextButton(p.title, ui.smallButtonStyle).onClick { view.pheromone = p; h.rebuildContext(); h.showToast(p.description) }
                b.isChecked = view.pheromone == p
                b.label.color = if (b.isChecked) Color.WHITE else Palette.pheromone(p)
                row.add(b).height(54f).growX().uniformX().pad(2f)
                if (i % 4 == 3) row.row()
            }
            val locked = PheromoneType.entries.size - types.size
            if (locked > 0) { row.row(); row.add(ui.small("$locked feromon türü araştırmayla açılır", Palette.uiTextSoft)).colspan(4).left() }
        }
        ToolMode.BUILD -> {
            val t = view.ghostType ?: return
            val gx = view.ghostX
            val gy = view.ghostY
            val check = if (gx != null && gy != null) sim.checkRoomPlacement(t, gx, gy) else null
            row.add(ui.small(t.title + "  " + (check?.reason ?: if (check == null) "Yer seçin" else "Uygun (${check.digCells} hücre kazılacak)"),
                if (check?.ok == true) Palette.accentDark else Palette.red, wrap = true)).growX().left()
            val ok = ui.accent("Onayla") {
                val room = if (gx != null && gy != null) sim.placeRoom(t, gx, gy) else null
                if (room != null) { h.showToast("${t.title} planlandı: önce alan kazılacak, sonra inşa edilecek."); h.setMode(ToolMode.VIEW) }
                else h.showToast(check?.reason ?: "Yerleştirilemedi")
            }
            ok.isDisabled = check?.ok != true
            row.add(ok).height(56f).width(150f).padLeft(6f)
            row.add(ui.button("Vazgeç", small = true) { h.setMode(ToolMode.VIEW) }).height(56f).width(120f).padLeft(4f)
        }
        ToolMode.DIG, ToolMode.CANCEL, ToolMode.FILL -> {
            row.add(ui.small("Bekleyen kazı: ${sim.digOrders.size} • dolgu: ${sim.fillOrders.size}")).growX().left()
            if (view.mode == ToolMode.CANCEL) row.add(ui.danger("Tümünü sil") {
                sim.digOrders.values.filter { it.roomId == -1 }.toList().forEach { sim.cancelDig(it.x, it.y) }; sim.fillOrders.clear(); h.rebuildContext()
            }).height(52f)
            row.add(ui.button("Bitti", small = true) { h.setMode(ToolMode.VIEW) }).height(52f).width(110f).padLeft(6f)
        }
        ToolMode.ORDER_POINT, ToolMode.TARGET -> {
            row.add(ui.small("Birlik ${if (view.pendingSquad == 0) "A" else "B"}: ${if (view.mode == ToolMode.TARGET) "hedef seçin" else view.pendingOrder.title + " noktası seçin"}", Palette.accentDark)).growX().left()
            row.add(ui.button("Vazgeç", small = true) { h.setMode(ToolMode.VIEW); h.openMilitary() }).height(52f).width(120f)
        }
        ToolMode.VIEW -> {}
    }
}

// ==================================================================== İnşa

fun Hud.openBuildMenu() {
    openSheet("Oda İnşa Et") { c ->
        val unlocked = RoomType.entries.filter { sim.roomUnlocked(it) }
        for (t in unlocked) card(c) { row ->
            val head = Table()
            head.add(ui.swatch(Palette.room(t))).size(32f).padRight(10f)
            head.add(ui.label(t.title)).left().expandX()
            head.add(ui.small("${t.width}×${t.height}", Palette.uiTextSoft)).padRight(10f)
            head.add(ui.accent("Seç") {
                closeSheet(); setMode(ToolMode.BUILD); view.ghostType = t; view.ghostX = null; view.ghostY = null; rebuildContext()
            }).height(54f).width(110f)
            row.add(head).growX().row()
            row.add(ui.small(t.description, Palette.uiTextSoft, wrap = true)).growX().padTop(4f).row()
            val info = Table()
            info.add(costRow(t.cost)).left().expandX()
            info.add(ui.small("Bakım: ${t.maintenanceSoil}/gün", Palette.uiTextSoft))
            row.add(info).growX().padTop(6f)
        }
        val locked = RoomType.entries.filter { it.buildable && !sim.roomUnlocked(it) }
        if (locked.isNotEmpty()) {
            header(c, "Araştırma gerektiren odalar (${locked.size})")
            for (t in locked) line(c, "• ${t.title}: ${ResearchTree.get(t.requires!!)?.let { "${it.category.title} › ${it.title}" }}", Palette.uiTextSoft)
            c.add(ui.button("Araştırma ağacını aç") { openResearch(0) }).growX().height(58f).padTop(6f).row()
        }
    }
}

// ==================================================================== Koloni (sekmeler)

fun Hud.openColony(tab: Int) {
    val tabs = listOf("Genel", "Görevler", "Karıncalar", "Hedefler", "Günlük", "İstatistik")
    openSheet("Koloni", 0.7f) { c ->
        val bar = Table()
        for ((i, name) in tabs.withIndex()) {
            val b = TextButton(name, ui.smallButtonStyle).onClick { openColony(i) }
            b.isChecked = i == tab
            bar.add(b).height(52f).growX().uniformX().pad(2f)
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

private fun Hud.overview(t: Table) {
    val s = sim
    line(t, "Nüfus: ${s.population} (${s.workerCount} işçi, ${s.militaryCount} asker) + kraliçe • Barınak ${s.housing()}")
    line(t, "Yavrular: ${s.brood.count { it.stage == BroodStage.EGG }} yumurta, ${s.brood.count { it.stage == BroodStage.LARVA }} larva, ${s.brood.count { it.stage == BroodStage.PUPA }} pupa")
    val blocker = s.layBlocker()
    line(t, if (blocker == null) "Kraliçe yumurtluyor (sonraki: ${(s.layInterval - s.queenLayTimer).toInt()} sn)" else "Kraliçe bekliyor: $blocker", if (blocker == null) Palette.accentDark else Palette.orange)
    line(t, "Yiyecek tüketimi ~${"%.1f".format(s.foodConsumptionPerMinute())}/dk • Stok ${s.resources.edibleTotal()} • Bakım ${s.dailyMaintenance()} toprak/gün")
    line(t, "Hasta: ${s.ants.count { it.sick }} • Ceset: ${s.corpses.size} • Kazı: ${s.digOrders.size} • Yığın: ${s.piles.size}")
    header(t, "Odalar (${s.rooms.size})")
    for (r in s.rooms) card(t) { row ->
        row.add(ui.swatch(Palette.room(r.type))).size(24f).padRight(8f)
        row.add(ui.small("${r.type.title} Sv.${r.level} • ${r.state.title}" + (if (!r.maintained) " • bakımsız" else "") + (if (s.roomFloodLevel(r) > 0.1f) " • su bastı" else ""))).left().expandX()
        row.add(ui.button("Git", small = true) { closeSheet(); actions.focus(r.anchorX, r.anchorY); selectCell(r.anchorX, r.anchorY) }).height(46f).width(76f)
    }
}

private fun Hud.priorities(t: Table) {
    line(t, "1 en yüksek, 5 en düşük öncelik (↑ daha önemli, ↓ daha az). Kapalı görevler yapılmaz. Uzmanlar önce kendi işine bakar ve +%15 verimle çalışır. Askerler bu listeye değil, Ordu emirlerine göre hareket eder.", Palette.uiTextSoft)
    for (cat in TaskCategory.active) card(t) { row ->
        row.add(ui.label(cat.title)).left().expandX().row()
        val ctl = Table()
        val pl = ui.label(prio(this, cat), align = Align.center)
        ctl.add(ui.small("Öncelik", Palette.uiTextSoft)).padRight(6f)
        ctl.add(ui.button("↑", small = true) { val p = sim.priorities[cat]; if (p > 1) sim.priorities.set(cat, p - 1); pl.setText(prio(this, cat)) }).size(54f)
        ctl.add(pl).width(86f)
        ctl.add(ui.button("↓", small = true) { val p = sim.priorities[cat]; if (p in 1..4) sim.priorities.set(cat, p + 1); pl.setText(prio(this, cat)) }).size(54f)
        ctl.add(ui.button(if (sim.priorities[cat] == 0) "Aç" else "Kapat", small = true) { sim.priorities.set(cat, if (sim.priorities[cat] == 0) 3 else 0); openColony(1) }).height(54f).width(90f).padLeft(4f)
        ctl.add().expandX()
        val sl = ui.label("${sim.specialistCount(cat)}", align = Align.center)
        ctl.add(ui.small("Uzman", Palette.uiTextSoft)).padRight(6f)
        ctl.add(ui.button("−", small = true) { sim.setSpecialists(cat, (sim.specialistCount(cat) - 1).coerceAtLeast(0)); sl.setText("${sim.specialistCount(cat)}") }).size(54f)
        ctl.add(sl).width(46f)
        ctl.add(ui.button("+", small = true) { sim.setSpecialists(cat, sim.specialistCount(cat) + 1); sl.setText("${sim.specialistCount(cat)}") }).size(54f)
        row.add(ctl).growX()
    }
}

private fun prio(h: Hud, cat: TaskCategory): String = h.sim.priorities[cat].let { if (it == 0) "Kapalı" else "$it" }

private fun Hud.antList(t: Table) {
    val s = sim
    val counts = s.ants.groupingBy { it.type }.eachCount()
    line(t, counts.entries.sortedBy { it.key.ordinal }.joinToString(" • ") { "${it.key.title} ${it.value}" }, Palette.uiTextSoft)
    for (a in s.ants.sortedWith(compareBy({ it.type.ordinal }, { it.id })).take(80)) card(t) { row ->
        val spec = a.assignment?.let { " • Uzman: ${it.title}" } ?: ""
        val text = if (a.type == AntType.QUEEN) "Kraliçe • Can ${a.hp.toInt()}/${a.maxHp(s.mods).toInt()}"
        else "${a.type.title} #${a.id} Sv.${a.level} • ${a.activity.title}$spec" + (if (a.type.military) " • Birlik ${if (a.squad == 0) "A" else "B"}" else "") +
            "\nAçlık ${a.hunger.toInt()} • Enerji ${a.energy.toInt()} • Can ${a.hp.toInt()}/${a.maxHp(s.mods).toInt()}" + (if (a.sick) " • HASTA" else "")
        row.add(ui.small(text, if (a.starving || a.sick) Palette.red else Palette.uiText, wrap = true)).growX().left()
        row.add(ui.button("Git", small = true) { closeSheet(); actions.focus(a.cellX, a.cellY) }).height(46f).width(76f)
    }
    if (s.ants.size > 80) line(t, "… ve ${s.ants.size - 80} karınca daha", Palette.uiTextSoft)
}

private fun Hud.goals(t: Table) {
    line(t, "Hedefler koloninin durumuna göre sürekli yenilenir (her biri +3 jeton). Tamamlanan: ${sim.goals.completed}", Palette.uiTextSoft)
    for (g in sim.goals.active) card(t) { row ->
        row.add(ui.label(g.title)).left().expandX().row()
        row.add(ui.small("İlerleme: ${g.progress(sim).coerceIn(0, g.goalValue)}/${g.goalValue} • Ödül: ${g.rewardAmount} ${g.rewardType.title}", Palette.uiTextSoft)).left()
    }
    c2(t)
}

private fun Hud.c2(t: Table) {
    t.add(ui.button("İmparatorluk hedefleri ve başarımlar") { openEmpire() }).growX().height(58f).padTop(6f).row()
}

private fun Hud.worldLog(t: Table) {
    line(t, "Dünya Günlüğü: koloninin büyük keşifleri, savaşları ve olayları.", Palette.uiTextSoft)
    for (e in sim.log.asReversed().take(120)) line(t, "Gün ${e.day}: ${e.text}")
}

private fun Hud.statistics(t: Table) {
    val st = sim.stats
    val h = (st.playTime / 3600).toInt()
    val m = ((st.playTime % 3600) / 60).toInt()
    listOf(
        "Oyun süresi: $h sa $m dk • Gün ${sim.day}, yıl ${sim.environment.year(sim.day)}",
        "İtibar: ${sim.reputation} (${Reputation.title(sim.reputation)}) • Evrim seviyesi ${sim.evolution.level(sim.reputation)}",
        "Kazılan hücre (kontrol edilen toprak): ${st.cellsDug} • En derin nokta: ${st.maxDepth}",
        "Bilinen yüzey: ${sim.knownMin()}…${sim.knownMax()} (${sim.knownMax() - sim.knownMin()} hücre)",
        "Toplanan yiyecek: ${st.foodGathered} • malzeme: ${st.materialsGathered} • üretilen: ${st.produced}",
        "Yenen yiyecek: ${st.foodEaten}",
        "Yumurta: ${st.eggsLaid} • Doğan: ${st.antsBorn} • Eğitilen: ${st.antsTrained} • Ölen: ${st.antsDied}",
        "İnşa edilen oda: ${st.roomsBuilt} • Tünel çökmesi: ${st.collapses}",
        "Araştırma: ${sim.research.completed.size}/${ResearchTree.all.size} • Genetik özellik: ${sim.genetics.unlocked.size}/${GeneticTrait.entries.size}",
        "Yenilen düşman: ${st.enemiesKilled} • Püskürtülen baskın: ${st.raidsRepelled} • Yenilen rakip: ${st.rivalsDefeated}",
        "Başarılı sefer: ${st.expeditionsSucceeded} • Keşfedilen bölge: ${sim.expeditions.regionsCompleted}",
        "Fosil: ${sim.museum.found.size}/${FossilKind.entries.size} • Başarım: ${sim.achievements.unlocked.size}/${Achievement.entries.size}",
        "Yan koloni: ${sim.federation.colonies.size} • Jeton: ${sim.coins}",
        "Yüklü harita parçası: ${sim.world.loadedChunkCount}",
    ).forEach { line(t, it) }
}

// ==================================================================== Menü merkezi

fun Hud.openMenu() {
    openSheet("Menü", 0.72f) { c ->
        val screens = listOf<Pair<String, () -> Unit>>(
            "Araştırma Ağacı" to { openResearch(0) },
            "Ordu ve Savaş" to { openMilitary() },
            "Rakipler ve Diplomasi" to { openRivals() },
            "Keşif Haritası ve Seferler" to { openExploration() },
            "Koloni Evrimi" to { openEvolution() },
            "Genetik Koleksiyon" to { openCollection() },
            "Fosil Müzesi" to { openMuseum() },
            "Market" to { openMarket() },
            "Envanter" to { openInventory() },
            "İmparatorluk ve Başarımlar" to { openEmpire() },
            "Kaynak Ekonomisi" to { openEconomy() },
            "Ekosistem ve İklim" to { openEcosystem() },
            "Görevler (hedefler)" to { openColony(3) },
            "İstatistikler" to { openColony(5) },
            "Dünya Günlüğü" to { openColony(4) },
            "Ayarlar" to { openSettings() },
        )
        val grid = Table()
        for ((i, s) in screens.withIndex()) {
            grid.add(ui.button(s.first, small = true) { s.second() }).growX().uniformX().height(62f).pad(3f)
            if (i % 2 == 1) grid.row()
        }
        c.add(grid).growX().row()
    }
}

fun Hud.openSettings() {
    openSheet("Ayarlar", 0.62f) { c -> settingsBody(c) }
}

private fun Hud.settingsBody(c: Table) {
    fun toggle(text: String, f: () -> Unit) = c.add(TextButton(text, ui.buttonStyle).onClick { f(); openSettings() }).growX().height(62f).padBottom(6f).row()
    toggle(if (settings.lowQuality) "Grafik: Düşük (performans)" else "Grafik: Yüksek") { settings.lowQuality = !settings.lowQuality }
    toggle(if (settings.soundOn) "Ses: Açık" else "Ses: Kapalı") { settings.soundOn = !settings.soundOn }
    toggle("Ses düzeyi: %${(settings.volume * 100).toInt()}") { settings.volume = if (settings.volume >= 1f) 0.25f else settings.volume + 0.25f }
    toggle(if (settings.showHints) "İpuçları: Açık" else "İpuçları: Kapalı") { settings.showHints = !settings.showHints; setMode(view.mode) }
    c.add(ui.button("Şimdi kaydet") { actions.saveNow(); showToast("Koloni kaydedildi") }).growX().height(62f).padBottom(12f).row()
    line(c, "Otomatik kayıt 30 saniyede bir ve uygulamadan çıkarken yapılır. Oyun kapalıyken en fazla 8 saat, yarı verimle ilerler (toplama, üretim, yavru, araştırma, seferler, yan koloniler). Oyuncu yokken baskın, kazı, inşa ve savaş olmaz.", Palette.uiTextSoft)
    val reset = ui.danger(if (confirmReset) "Emin misiniz? Tüm ilerleme silinir — tekrar dokunun" else "Yeni koloni başlat") {
        if (confirmReset) { actions.newColony(); closeSheet(); showToast("Yeni koloni kuruldu") } else { confirmReset = true; c.clearChildren(); settingsBody(c) }
    }
    c.add(reset).growX().height(62f).padTop(8f).padBottom(12f).row()
    line(c, "Yeraltı İmparatorluğu: Koloni Savaşları • Sürüm 1.0 • Tamamen çevrimdışı, reklamsız. Market yalnızca oyun içi jetonlarla çalışır.", Palette.uiTextSoft)
}

fun Hud.showOfflineReport(lines: List<String>, title: String) {
    openSheet(title, 0.48f) { c ->
        for (l in lines) line(c, "• $l")
        c.add(ui.accent("Devam") { closeSheet() }).growX().height(62f).padTop(10f).row()
    }
}

// ==================================================================== Hücre, oda ve canlı bilgisi

fun Hud.selectCell(x: Int, y: Int) {
    view.selectedCell = x to y
    val room = if (y >= 0) sim.roomAt(x, y) else null
    val creature = sim.creatures.firstOrNull { kotlin.math.abs(it.x - (x + 0.5f)) < 1f && kotlin.math.abs(it.y - (y + 0.5f)) < 1f }
    val title = creature?.kind?.title ?: room?.type?.title ?: if (y < 0) "Yüzey (${Biome.at(sim.seed, x).title})" else "Hücre ($x, derinlik $y)"
    openSheet(title, 0.44f) { c -> cellBody(c, x, y) }
    liveContent { cellBody(it, x, y) }
}

private fun Hud.cellBody(c: Table, x: Int, y: Int) {
    val s = sim
    val creature = s.creatures.firstOrNull { kotlin.math.abs(it.x - (x + 0.5f)) < 1f && kotlin.math.abs(it.y - (y + 0.5f)) < 1f }
    if (creature != null) {
        line(c, "${creature.kind.title} • Can ${creature.hp.toInt()}/${creature.maxHp.toInt()} • Saldırı ${creature.kind.attack} • Savunma ${creature.kind.defense}")
        line(c, creature.kind.description, Palette.uiTextSoft)
        s.rivalSystem.byId(creature.rivalId)?.let { line(c, "Akıncı: ${it.name}" + if (creature.retreating) " (geri çekiliyor)" else "", Palette.red) }
        if (creature.kind.hostile || creature.kind.loot.isNotEmpty()) {
            val b = Table()
            for (sq in 0..1) b.add(ui.accent("Birlik ${if (sq == 0) "A" else "B"} saldırsın") { s.setOrder(sq, OrderKind.TARGET, targetId = creature.id); showToast("Hedef işaretlendi") }).growX().height(58f).pad(2f)
            c.add(b).growX().row()
        }
        return
    }
    if (y < 0) {
        val biome = Biome.at(s.seed, x)
        line(c, "Biyom: ${biome.title} • Kaynaklar: ${biome.sources.joinToString { it.title.lowercase() }}", Palette.uiTextSoft)
        val rival = s.rivalSystem.rivals.firstOrNull { it.discovered && it.alive && kotlin.math.abs(it.x - x) <= 3 }
        if (rival != null) { line(c, "${rival.name} yuvası (${rival.personality.title})", Palette.red); c.add(ui.button("Rakipler panelini aç") { openRivals() }).growX().height(58f).row() }
        val src = s.sources.firstOrNull { kotlin.math.abs(it.x - x) <= 1 }
        if (src != null) {
            line(c, "${src.kind.title}: ${src.amount}/${src.maxAmount} ${src.kind.resource.title}")
            if (!s.isKnown(src.x)) line(c, "Bilinen menzil dışında: karıncalar ancak Yiyecek feromonuyla buraya gider (Keşifçiler menzili genişletir).", Palette.orange)
            c.add(ui.button("Yiyecek feromonu bırak") { s.placePheromone(PheromoneType.FOOD, src.x, -1); showToast("Yiyecek feromonu bırakıldı") }).growX().height(58f).row()
        } else line(c, "Açık yüzey. Kaynaklar burada belirir; mevsim ve hava miktarlarını etkiler.", Palette.uiTextSoft)
        return
    }
    val info = s.cellInfo(x, y)
    val room = info.room
    if (room != null) { roomBody(c, room); return }
    val t = info.terrain
    line(c, if (info.revealed || t.walkable) "${t.title}: ${t.description}" else "Keşfedilmemiş toprak. Yakınını kazınca içeriği görünür.")
    if (t.diggable) line(c, "Kazı süresi: ~${"%.1f".format(info.estimatedSeconds)} sn (1 işçi)" + (if (info.revealed && t.yieldType != null) " • Verim: ${t.yieldAmount} ${t.yieldType!!.title}" else ""), Palette.uiTextSoft)
    if (info.water > 0.05f) line(c, "Su seviyesi: %${(info.water * 100).toInt()}" + if (info.water > 0.85f) " — boğulma tehlikesi!" else "", Palette.hex("2E7FB8"))
    for (r in info.risks) line(c, "⚠ $r", Palette.orange)
    val traffic = s.trafficAt(x, y)
    if (traffic > 1) line(c, "Trafik: $traffic karınca — dar tünel yavaşlatıyor.", Palette.orange)
    val btns = Table()
    when {
        info.designated || info.fillDesignated -> btns.add(ui.danger("İşareti kaldır") { s.cancelDig(x, y); selectCell(x, y) }).growX().height(58f)
        t.diggable -> btns.add(ui.accent("Kazı işaretle") { val r = s.designateDig(x, y); showToast(r.message ?: "Kazı işaretlendi"); if (r == DigDesignation.OK) selectCell(x, y) }).growX().height(58f)
        t.walkable -> btns.add(ui.button("Tüneli kapat") { showToast(s.designateFill(x, y) ?: "Dolgu işaretlendi"); selectCell(x, y) }).growX().height(58f)
    }
    for (p in listOf(PheromoneType.DIG, PheromoneType.RALLY, PheromoneType.DANGER).filter { s.pheromoneUnlocked(it) })
        btns.add(ui.button(p.title, small = true) { s.placePheromone(p, x, y); showToast("${p.title} feromonu bırakıldı") }).height(58f).padLeft(4f)
    c.add(btns).growX().padTop(6f).row()
    for (a in s.ants.filter { it.cellX == x && it.cellY == y }.take(4)) line(c, "${a.type.title} #${a.id}: ${a.activity.title}", Palette.uiTextSoft)
}

private fun Hud.roomBody(c: Table, r: Room) {
    val s = sim
    line(c, "${r.type.title} • Seviye ${r.level}/${r.type.maxLevel} • ${r.state.title}")
    line(c, r.type.description, Palette.uiTextSoft)
    if (r.functional) {
        line(c, "Verim: %${(s.roomEfficiency(r) * 100).toInt()}" + (if (!r.maintained) " (bakımsız)" else "") + (if (s.floodPenalty(r) > 0f) " (su baskını)" else "") + " • Bakım: ${r.type.maintenanceSoil * r.level} toprak/gün")
        if (r.type.isStation) line(c, "Çalışan: ${r.workers}/${r.stationCapacity}", Palette.uiTextSoft)
        when (r.type) {
            RoomType.FOOD_STORAGE -> line(c, "Toplam besin: ${s.resources.totalOf(StorageKind.FOOD)}/${s.capacityOf(StorageKind.FOOD)}")
            RoomType.MATERIAL_STORAGE -> line(c, "Toplam malzeme: ${s.resources.totalOf(StorageKind.MATERIAL)}/${s.capacityOf(StorageKind.MATERIAL)}")
            RoomType.WATER_STORAGE -> line(c, "Su: ${s.resources[ResourceType.WATER]}/${s.capacityOf(StorageKind.WATER)}")
            RoomType.FUNGUS_FARM -> line(c, "Yaprak tamponu: ${r.leafBuffer}/${s.farmLeafCapacity(r)} • Üretim: ${"%.1f".format(s.farmRate(r) * 60)} mantar/dk")
            RoomType.REST_ROOM -> line(c, "Barınak +${10 * r.level} • Dinlenme yeri ${6 * r.level}")
            RoomType.BARRACKS -> line(c, "Barınak +${8 * r.level} • Askerler burada dinlenir")
            RoomType.EGG_ROOM, RoomType.LARVA_ROOM, RoomType.QUEEN_CHAMBER -> line(c, "Yavru yeri: ${s.brood.count { it.roomId == r.id }}/${s.broodCapacity(r)}")
            RoomType.RESEARCH_LAB -> {
                val d = s.research.currentDef()
                line(c, d?.let { "Araştırma: ${it.title} %${(s.research.progress / it.work * 100).toInt()}" } ?: "Aktif araştırma yok")
                c.add(ui.button("Araştırma ağacı") { openResearch(0) }).growX().height(56f).row()
            }
            RoomType.GENETICS_LAB -> { line(c, s.genetics.current?.let { "Geliştiriliyor: ${it.title} %${(s.genetics.progress / it.work * 100).toInt()}" } ?: "Aktif çalışma yok"); c.add(ui.button("Genetik koleksiyon") { openCollection() }).growX().height(56f).row() }
            RoomType.WORKSHOP -> {
                line(c, "Tarif: ${r.recipe.title} (ilerleme %${(r.stationProgress / r.recipe.work * 100).toInt()})")
                val rb = Table()
                for (rec in Recipe.entries) {
                    val b = TextButton(rec.title, ui.smallButtonStyle).onClick { r.recipe = rec; r.stationProgress = 0f; selectCell(r.anchorX, r.anchorY) }
                    b.isChecked = r.recipe == rec
                    rb.add(b).growX().uniformX().height(52f).pad(2f)
                }
                c.add(rb).growX().row()
                c.add(costRow(r.recipe.input)).left().row()
            }
            RoomType.TRAINING_ROOM -> trainingBody(c, r)
            RoomType.ROYAL_CENTER -> { line(c, "Kraliyet özü üretimi: 2 enzim + 5 yiyecek → 1 öz (%${(r.stationProgress / Simulation.ROYAL_WORK * 100).toInt()})"); c.add(ui.button("Federasyon ve yan koloniler") { openEmpire() }).growX().height(56f).row() }
            RoomType.EXPLORATION_CENTER -> c.add(ui.button("Keşif seferleri") { openExploration() }).growX().height(56f).row()
            RoomType.TRADE_ROOM -> c.add(ui.button("Ticaret ve diplomasi") { openRivals() }).growX().height(56f).row()
            RoomType.FOSSIL_MUSEUM -> c.add(ui.button("Müzeyi gez") { openMuseum() }).growX().height(56f).row()
            RoomType.DEFENSE_ROOM, RoomType.FORTRESS, RoomType.TRAP_ROOM -> c.add(ui.button("Ordu paneli") { openMilitary() }).growX().height(56f).row()
            else -> {}
        }
        if (r.type == RoomType.QUEEN_CHAMBER) line(c, s.layBlocker()?.let { "Kraliçe bekliyor: $it" } ?: "Kraliçe yumurtluyor", Palette.accentDark)
        val bonus = r.type.adjacency.keys.filter { t -> s.rooms.any { it.type == t && it.functional && it.isAdjacentTo(r) } }
        if (r.type.adjacency.isNotEmpty()) line(c, "Komşuluk: " + r.type.adjacency.entries.joinToString { (t, b) -> "${t.title} +%${(b * 100).toInt()}" + if (t in bonus) " ✓" else "" }, Palette.uiTextSoft)
    } else line(c, if (r.state == RoomState.EXCAVATING) "Önce alan kazılıyor." else "İnşa: %${(r.buildProgress / r.workRequired * 100).toInt()} (en fazla ${Simulation.MAX_BUILDERS} işçi)", Palette.uiTextSoft)
    val btns = Table()
    if (r.state == RoomState.ACTIVE && r.level < r.type.maxLevel) {
        btns.add(costRow(r.type.upgradeCost(r.level + 1))).padRight(6f)
        val up = ui.accent("Geliştir") { result(if (s.upgradeRoom(r)) null else "Yetersiz kaynak", "${r.type.title} geliştiriliyor"); selectCell(r.anchorX, r.anchorY) }
        up.isDisabled = !s.canUpgrade(r)
        btns.add(up).height(58f).width(140f)
    }
    btns.add().expandX()
    if (r.type != RoomType.QUEEN_CHAMBER) btns.add(ui.danger(if (r.functional) "Yık (%50)" else "İptal") { s.removeRoom(r); closeSheet(); view.selectedCell = null }).height(58f)
    c.add(btns).growX().padTop(8f).row()
}

private fun Hud.trainingBody(c: Table, r: Room) {
    val s = sim
    val cls = r.trainClass
    line(c, if (cls != null && r.trainCount > 0) "Eğitiliyor: ${cls.title} (${r.trainCount} kaldı, %${(r.stationProgress / cls.trainTime * 100).toInt()})" else "Eğitim sırası boş. Sıradan işçiler (İşçi sınıfı) eğitilir.", Palette.accentDark)
    for (t in AntType.entries.filter { it.trainable }) {
        val reason = s.canTrain(t)
        card(c) { row ->
            val top = Table()
            top.add(ui.small("${t.title} • Can ${t.maxHp.toInt()} • Saldırı ${t.attack.toInt()} • Savunma ${t.defense.toInt()}")).left().expandX()
            val b = ui.accent(if (cls == t) "+1" else "Eğit") { s.setTraining(r, t, if (cls == t) r.trainCount + 1 else 1); selectCell(r.anchorX, r.anchorY) }
            b.isDisabled = reason != null && reason != "Yetersiz kaynak"
            top.add(b).height(50f).width(96f)
            row.add(top).growX().row()
            row.add(ui.small(t.description + (if (reason != null) " — $reason" else ""), if (reason != null) Palette.orange else Palette.uiTextSoft, wrap = true)).growX().left().row()
            row.add(costRow(t.trainCost)).left()
        }
    }
    if (cls != null) c.add(ui.button("Eğitimi durdur") { s.setTraining(r, null, 0); selectCell(r.anchorX, r.anchorY) }).growX().height(56f).row()
}

// ==================================================================== Araştırma

fun Hud.openResearch(tab: Int) {
    openSheet("Araştırma Ağacı", 0.74f) { c ->
        val bar = Table()
        for ((i, cat) in ResearchCategory.entries.withIndex()) {
            val b = TextButton(cat.title, ui.smallButtonStyle).onClick { openResearch(i) }
            b.isChecked = i == tab
            bar.add(b).height(52f).growX().uniformX().pad(2f)
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
    if (cur != null) {
        card(t) { row ->
            row.add(ui.small("Sürüyor: ${cur.title} — %${(s.research.progress / cur.work * 100).toInt()}" + if (s.time < s.research.boostUntil) " (hızlandırıcı aktif)" else "", Palette.accentDark)).left().expandX()
            row.add(ui.danger("İptal (%50)") { s.cancelResearch() }).height(50f)
        }
    }
    if (s.rooms.none { it.type == RoomType.RESEARCH_LAB && it.functional }) line(t, "Araştırma için Araştırma Laboratuvarı inşa edin; işçiler orada çalışır (Görevler › Araştırma önceliği).", Palette.orange)
    line(t, "Tamamlanan: ${s.research.completed.size}/${ResearchTree.all.size}", Palette.uiTextSoft)
    for (d in ResearchTree.all.filter { it.category == cat }) card(t) { row ->
        val done = d.id in s.research.completed
        val available = s.research.available(d)
        val top = Table()
        top.add(ui.label(d.title, if (done) Palette.accentDark else Palette.uiText)).left().expandX()
        when {
            done -> top.add(ui.small("✓ Tamamlandı", Palette.accentDark))
            s.research.current == d.id -> top.add(ui.small("Sürüyor", Palette.orange))
            available -> { val b = ui.accent("Başlat") { result(s.startResearch(d), "Araştırma başladı: ${d.title}") }; b.isDisabled = s.research.current != null; top.add(b).height(50f).width(110f) }
            else -> top.add(ui.small("Kilitli", Palette.uiTextSoft))
        }
        row.add(top).growX().row()
        row.add(ui.small(d.description, Palette.uiTextSoft, wrap = true)).growX().left().row()
        if (!done) {
            val info = Table()
            info.add(costRow(d.cost)).left().expandX()
            info.add(ui.small("${d.work.toInt()} iş", Palette.uiTextSoft))
            row.add(info).growX().row()
            if (d.prereqs.isNotEmpty()) row.add(ui.small("Gerekli: " + d.prereqs.joinToString { id -> (ResearchTree.get(id)?.title ?: id) + if (id in s.research.completed) " ✓" else "" }, Palette.uiTextSoft, wrap = true)).growX().left()
        }
    }
}

// ==================================================================== Ordu

fun Hud.openMilitary() {
    openSheet("Ordu ve Savaş", 0.74f) { c -> militaryBody(c) }
    liveContent { militaryBody(it) }
}

private fun Hud.militaryBody(c: Table) {
    val s = sim
    val counts = s.ants.filter { it.type.military }.groupingBy { it.type }.eachCount()
    line(c, if (counts.isEmpty()) "Henüz asker yok. Asker Yetiştirme araştırması, Asker Kışlası ve Eğitim Odası gerekir." else counts.entries.joinToString(" • ") { "${it.key.title} ${it.value}" })
    line(c, "Savaşlar gerçek zamanlıdır; oyunu duraklatıp (II) emir verebilirsiniz. Dar geçitler, tuzak ve savunma odaları, tünel kapatma sayıca üstünlükten önemlidir.", Palette.uiTextSoft)
    for (sq in 0..1) card(c) { row ->
        val o = s.orders[sq]
        val target = when (o.kind) {
            OrderKind.RAID_RIVAL -> s.rivalSystem.byId(o.targetId)?.name ?: ""
            OrderKind.TARGET -> s.creatures.firstOrNull { it.id == o.targetId }?.kind?.title ?: ""
            OrderKind.GUARD, OrderKind.RALLY, OrderKind.AMBUSH -> "(${o.x}, ${o.y})"
            else -> ""
        }
        row.add(ui.label("Birlik ${if (sq == 0) "A" else "B"} • ${s.squadSize(sq)} asker • Emir: ${o.kind.title} $target")).left().row()
        val g = Table()
        val opts = listOf(OrderKind.DEFEND, OrderKind.ATTACK, OrderKind.RETREAT, OrderKind.GUARD, OrderKind.RALLY, OrderKind.AMBUSH, OrderKind.TARGET)
        for ((i, k) in opts.withIndex()) {
            val b = TextButton(k.title, ui.smallButtonStyle).onClick {
                when {
                    k.needsPoint -> { view.pendingSquad = sq; view.pendingOrder = k; closeSheet(); setMode(ToolMode.ORDER_POINT) }
                    k == OrderKind.TARGET -> { view.pendingSquad = sq; closeSheet(); setMode(ToolMode.TARGET) }
                    else -> { s.setOrder(sq, k); showToast("Birlik ${if (sq == 0) "A" else "B"}: ${k.title}") }
                }
            }
            b.isChecked = o.kind == k
            g.add(b).growX().uniformX().height(52f).pad(2f)
            if (i % 3 == 2) g.row()
        }
        g.add(ui.button("Destek gönder", small = true) { showToast("${s.sendSupport(sq)} asker birlik ${if (sq == 0) "A" else "B"}'ye katıldı") }).growX().height(52f).pad(2f)
        row.add(g).growX().row()
    }
    c.add(TextButton(if (s.retreatWounded) "Yaralıları çek: Açık (can %40 altı)" else "Yaralıları çek: Kapalı", ui.buttonStyle).onClick { s.retreatWounded = !s.retreatWounded }).growX().height(58f).padBottom(6f).row()
    val enemies = s.creatures.filter { it.kind.hostile }.sortedBy { kotlin.math.abs(it.cellX) + kotlin.math.abs(it.cellY) }
    header(c, "Tehditler (${enemies.size})")
    if (enemies.isEmpty()) line(c, "Görünürde düşman yok.", Palette.uiTextSoft)
    for (e in enemies.take(12)) card(c) { row ->
        row.add(ui.small("${e.kind.title} • Can ${e.hp.toInt()} • (${e.cellX}, ${e.cellY})" + (s.rivalSystem.byId(e.rivalId)?.let { " • ${it.name}" } ?: ""), Palette.red)).left().expandX()
        row.add(ui.button("Git", small = true) { closeSheet(); actions.focus(e.cellX, e.cellY) }).height(46f).width(70f)
        row.add(ui.accent("Hedef") { s.setOrder(0, OrderKind.TARGET, targetId = e.id); showToast("Birlik A hedefe saldırıyor") }).height(46f).width(90f).padLeft(4f)
    }
    header(c, "Rakibe sefer")
    val rivals = s.rivalSystem.rivals.filter { it.discovered && it.alive }
    if (rivals.isEmpty()) line(c, "Keşfedilmiş rakip yok. Keşifçiler yüzeyde rakip yuvalarını bulur.", Palette.uiTextSoft)
    for (r in rivals) card(c) { row ->
        row.add(ui.small("${r.name} • ${r.personality.title} • ~${r.soldiers} asker, savunma ${r.defense.toInt()} • ${kotlin.math.abs(r.x)} hücre", Palette.uiText, wrap = true)).growX().left()
        row.add(ui.danger("Birlik A saldırsın") { s.setOrder(0, OrderKind.RAID_RIVAL, targetId = r.id); showToast("Birlik A ${r.name} üzerine yürüyor") }).height(50f)
    }
}

// ==================================================================== Rakipler ve diplomasi

fun Hud.openRivals() {
    openSheet("Rakipler ve Diplomasi", 0.74f) { c -> rivalsBody(c) }
    liveContent { rivalsBody(it) }
}

private fun Hud.rivalsBody(c: Table) {
    val s = sim
    val known = s.rivalSystem.rivals.filter { it.discovered }
    line(c, "Bilinen ${known.size} koloni, keşfedilmemiş ${s.rivalSystem.rivals.size - known.size}. Gücünüz: ${s.rivalSystem.playerPower(s).toInt()}", Palette.uiTextSoft)
    if (s.rooms.none { it.type == RoomType.TRADE_ROOM && it.functional }) line(c, "Ticaret için Ticaret Odası gerekir (Üretim › Ticaret araştırması).", Palette.uiTextSoft)
    for (r in known) card(c) { row ->
        val relColor = when { r.relation >= 30 -> Palette.accentDark; r.relation <= -20 -> Palette.red; else -> Palette.uiText }
        row.add(ui.label(r.name + if (!r.alive) " (dağıldı)" else "")).left().row()
        row.add(ui.small("${r.personality.title} • Antlaşma: ${r.treaty.title} • İlişki ${r.relation}", relColor)).left().row()
        row.add(ui.small("Nüfus ~${r.population} • Asker ~${r.soldiers} • Savunma ${r.defense.toInt()} • Güç ${r.strength.toInt()} • Konum ${r.x} • Baskın: ${r.raidsLaunched}", Palette.uiTextSoft, wrap = true)).growX().left().row()
        if (!r.alive) return@card
        val g = Table()
        g.add(ui.button("Hediye (15 yiyecek)", small = true) { result(s.gift(r), "Hediye gönderildi") }).growX().uniformX().height(50f).pad(2f)
        g.add(ui.button("Ateşkes öner", small = true) { result(s.proposeCeasefire(r), "Ateşkes imzalandı") }).growX().uniformX().height(50f).pad(2f).row()
        g.add(ui.button("Ticaret anlaşması", small = true) { result(s.proposeTrade(r), "Ticaret anlaşması yapıldı") }).growX().uniformX().height(50f).pad(2f)
        g.add(ui.button("İttifak öner", small = true) { result(s.proposeAlliance(r), "İttifak kuruldu") }).growX().uniformX().height(50f).pad(2f).row()
        g.add(ui.danger("Savaş ilan et") { s.declareWar(r); showToast("Savaş ilan edildi") }).growX().uniformX().height(50f).pad(2f)
        g.add(ui.danger("Birlik A saldırsın") { s.setOrder(0, OrderKind.RAID_RIVAL, targetId = r.id); showToast("Birlik A yürüyor") }).growX().uniformX().height(50f).pad(2f)
        row.add(g).growX().row()
        if (r.treaty == Treaty.TRADE || r.treaty == Treaty.ALLIANCE) {
            for (o in s.rivalSystem.offers(r)) {
                val tr = Table()
                tr.add(ui.icon(o.give)).size(26f); tr.add(ui.small("${o.giveAmount} ${o.give.title} → ")).padRight(4f)
                tr.add(ui.icon(o.get)).size(26f); tr.add(ui.small("${o.getAmount} ${o.get.title}")).left().expandX()
                tr.add(ui.accent("Takas") { result(s.trade(r, o), "Takas yapıldı") }).height(48f).width(100f)
                row.add(tr).growX().padTop(2f).row()
            }
        }
    }
}

// ==================================================================== Keşif

fun Hud.openExploration() {
    openSheet("Keşif Haritası ve Seferler", 0.74f) { c -> explorationBody(c) }
    liveContent { explorationBody(it) }
}

private var expeditionSize = 4

private fun Hud.explorationBody(c: Table) {
    val s = sim
    header(c, "Yüzey haritası")
    line(c, "Bilinen menzil: ${s.knownMin()} … ${s.knownMax()} (Keşifçiler ve Keşif feromonu genişletir).", Palette.uiTextSoft)
    val bands = (Math.floorDiv(s.knownMin(), 36)..Math.floorDiv(s.knownMax(), 36)).toList()
    for (b in bands) {
        val x0 = b * 36
        val rivals = s.rivalSystem.rivals.filter { it.discovered && it.alive && it.x in x0 until x0 + 36 }.joinToString { "⚑ ${it.name}" }
        line(c, "x ${x0}…${x0 + 35}: ${Biome.at(s.seed, x0 + 18).title}" + (if (x0 <= 0 && x0 + 36 > 0) " • YUVA" else "") + if (rivals.isNotEmpty()) " • $rivals" else "", if (rivals.isNotEmpty()) Palette.red else Palette.uiText)
    }
    header(c, "Keşif seferleri")
    val slots = s.expeditions.slots(s)
    line(c, if (slots == 0) "Sefer için Keşif Merkezi gerekir (Kazı › Zemin Etüdü)." else "Sefer yeri: ${s.expeditions.active.size}/$slots • Ekip başına 4 yiyecek erzak.", Palette.uiTextSoft)
    for (e in s.expeditions.active) {
        val r = s.expeditions.region(s.seed, e.regionIndex)
        val left = (e.startedAt + e.duration - s.time).toInt().coerceAtLeast(0)
        line(c, "→ ${r.name}: ${e.members.size} karınca, ${left / 60} dk ${left % 60} sn kaldı", Palette.accentDark)
    }
    val sz = Table()
    sz.add(ui.small("Ekip büyüklüğü")).padRight(8f)
    sz.add(ui.button("−", small = true) { expeditionSize = (expeditionSize - 1).coerceAtLeast(2) }).size(52f)
    sz.add(ui.label("$expeditionSize", align = Align.center)).width(50f)
    sz.add(ui.button("+", small = true) { expeditionSize = (expeditionSize + 1).coerceAtMost(15) }).size(52f)
    c.add(sz).left().padBottom(6f).row()
    for (r in s.expeditions.availableRegions(s.seed)) card(c) { row ->
        row.add(ui.small("${r.name} • ${r.biome.title} • ${r.distanceMinutes} dk • Tehlike ${"★".repeat(r.danger.coerceAtMost(5))}${if (r.danger > 5) "+" else ""}", wrap = true)).growX().left()
        val b = ui.accent("Gönder") { result(s.expeditions.start(s, r.index, expeditionSize), "Sefer yola çıktı") }
        b.isDisabled = slots <= s.expeditions.active.size
        row.add(b).height(50f).width(110f)
    }
    line(c, "Tamamlanan bölgeler: ${s.expeditions.regionsCompleted}. Her başarı yeni ve daha uzak bölgeler açar (sonsuz keşif).", Palette.uiTextSoft)
}

// ==================================================================== Evrim

fun Hud.openEvolution() {
    openSheet("Koloni Evrimi", 0.66f) { c -> evolutionBody(c) }
}

private fun Hud.evolutionBody(c: Table) {
    val s = sim
    line(c, "İtibar ${s.reputation} • Evrim seviyesi ${s.evolution.level(s.reputation)} • Harcanabilir puan: ${s.evolution.points(s.reputation)}")
    line(c, "Puanları istediğiniz yola dağıtın; hibrit gelişim serbesttir. 3. kademe ve sonrası Kraliyet Merkezi ister.", Palette.uiTextSoft)
    for (p in EvolutionPath.entries) card(c) { row ->
        val rank = s.evolution.ranks[p.ordinal]
        row.add(ui.label("${p.title}  ${"●".repeat(rank)}${"○".repeat(5 - rank)}")).left().expandX()
        val b = ui.accent("Geliştir") { result(s.evolve(p), "${p.title} kademe ${s.evolution.ranks[p.ordinal]}"); openEvolution() }
        b.isDisabled = rank >= 5
        row.add(b).height(50f).width(120f).row()
        row.add(ui.small(p.description, Palette.uiTextSoft, wrap = true)).colspan(2).growX().left()
    }
}

// ==================================================================== Koleksiyonlar

fun Hud.openCollection() {
    openSheet("Genetik Koleksiyon", 0.72f) { c -> collectionBody(c) }
    liveContent { collectionBody(it) }
}

private fun Hud.collectionBody(c: Table) {
    val s = sim
    line(c, "Genetik örnek: ${s.resources[ResourceType.GENETIC]} (düşmanlardan, seferlerden, eski yuvalardan ve rakip yağmalarından). Özellikler kalıcıdır.", Palette.uiTextSoft)
    if (s.rooms.none { it.type == RoomType.GENETICS_LAB && it.functional }) line(c, "Geliştirmek için Genetik Araştırma Odası gerekir.", Palette.orange)
    s.genetics.current?.let { line(c, "Geliştiriliyor: ${it.title} %${(s.genetics.progress / it.work * 100).toInt()}", Palette.accentDark) }
    for (t in GeneticTrait.entries) card(c) { row ->
        val have = t in s.genetics.unlocked
        row.add(ui.small("${t.title} — ${t.description}", if (have) Palette.accentDark else Palette.uiText, wrap = true)).growX().left()
        if (have) row.add(ui.small("✓")) else {
            val b = ui.accent("${t.samples} örnek") { result(s.startTrait(t), "${t.title} geliştiriliyor") }
            b.isDisabled = s.genetics.current != null
            row.add(b).height(48f).width(120f)
        }
    }
    header(c, "Canlı ansiklopedisi (${s.expeditions.discoveredCreatures.size}/${CreatureKind.entries.size})")
    for (k in CreatureKind.entries) {
        if (k in s.expeditions.discoveredCreatures) line(c, "• ${k.title}${if (k.hostile) " (tehlikeli)" else ""}: ${k.description}")
        else line(c, "• ??? — henüz karşılaşılmadı", Palette.uiTextSoft)
    }
}

fun Hud.openMuseum() {
    openSheet("Fosil Müzesi", 0.66f) { c -> museumBody(c) }
}

private fun Hud.museumBody(c: Table) {
    val s = sim
    val cap = s.museum.capacity(s)
    line(c, "İncelenmemiş fosil: ${s.resources[ResourceType.FOSSIL]} • Katalog: ${s.museum.found.size}/${FossilKind.entries.size} • Sergi: ${s.museum.displayed(s).size}/$cap")
    if (cap == 0) line(c, "Fosilleri sergilemek için Fosil Müzesi inşa edin (Kazı › Paleontoloji).", Palette.orange)
    c.add(ui.accent("Fosil incele") { showToast(s.museum.examine(s)); openMuseum() }).growX().height(60f).padBottom(8f).row()
    val shown = s.museum.displayed(s).toSet()
    for (f in FossilKind.entries) {
        val found = f in s.museum.found
        line(c, if (found) "${"★".repeat(f.rarity)} ${f.title}" + if (f in shown) " — sergide" else " — depoda" else "${"☆".repeat(f.rarity)} ???", if (found) Palette.uiText else Palette.uiTextSoft)
    }
}

// ==================================================================== Market ve envanter

fun Hud.openMarket() {
    openSheet("Market", 0.74f) { c -> marketBody(c) }
}

private fun Hud.marketBody(c: Table) {
    val s = sim
    line(c, "Jeton: ${s.coins}. Jetonlar hedefler, başarımlar, zaferler ve seferlerle kazanılır." + if (!OfflinePurchaseBackend.realMoneyAvailable) " Gerçek para ile satın alma yoktur; her şey oyun içinde kazanılır." else "", Palette.uiTextSoft)
    for (slot in MarketSlot.entries) {
        header(c, slot.title)
        for (item in MarketItem.entries.filter { it.slot == slot }) card(c) { row ->
            val owned = item in s.market.owned
            row.add(ui.small("${item.title} — ${item.description}", wrap = true)).growX().left()
            if (owned && !item.consumable) row.add(ui.small("Sahipsiniz", Palette.accentDark))
            else { val b = ui.accent("${item.price} jeton") { showToast(s.market.buy(s, item)); openMarket() }; b.isDisabled = s.coins < item.price; row.add(b).height(48f).width(130f) }
        }
    }
}

fun Hud.openInventory() {
    openSheet("Envanter", 0.72f) { c -> inventoryBody(c) }
}

private fun Hud.inventoryBody(c: Table) {
    val s = sim
    header(c, "Kaynaklar")
    for (kind in StorageKind.entries) {
        line(c, "${kind.title} deposu: ${s.resources.totalOf(kind)}/${s.capacityOf(kind)}", Palette.accentDark)
        val t = Table()
        var i = 0
        for (r in ResourceType.entries.filter { it.storage == kind }) {
            t.add(ui.icon(r)).size(28f); t.add(ui.small("${r.title}: ${s.resources[r]}")).left().padRight(12f)
            if (++i % 2 == 0) t.row()
        }
        c.add(t).left().padBottom(6f).row()
    }
    header(c, "Eşyalar (${s.market.owned.size})")
    if (s.market.owned.isEmpty()) line(c, "Henüz eşya yok. Marketten jetonla alabilirsiniz.", Palette.uiTextSoft)
    for (item in s.market.owned) card(c) { row ->
        row.add(ui.small("${item.title} (${item.slot.title})")).left().expandX()
        if (item.equippable) {
            val b = TextButton(if (s.market.isEquipped(item)) "Kullanılıyor" else "Kullan", ui.smallButtonStyle).onClick { s.market.equip(item); openInventory() }
            b.isChecked = s.market.isEquipped(item)
            row.add(b).height(48f).width(140f)
        } else row.add(ui.small("Koleksiyon", Palette.uiTextSoft))
    }
}

// ==================================================================== İmparatorluk

fun Hud.openEmpire() {
    openSheet("İmparatorluk", 0.74f) { c -> empireBody(c) }
    liveContent { empireBody(it) }
}

private fun Hud.empireBody(c: Table) {
    val s = sim
    val rep = s.reputation
    val next = Reputation.titles.firstOrNull { it.first > rep }
    line(c, "Unvan: ${Reputation.title(rep)} • İtibar $rep" + (next?.let { " • Sonraki: ${it.second} ($it.first)".replace("(${it}.first)", "(${it.first})") } ?: ""))
    header(c, "Uzun vadeli hedef")
    val g = s.empireGoal
    if (g.kind == null) line(c, "Bir hedef seçin; her kademe jeton ve kaynak ödülü verir, kademeler sınırsızdır.", Palette.uiTextSoft)
    else line(c, "${g.kind!!.title}: ${g.progress(s)}/${g.target()} ${g.kind!!.unit} • Kademe ${g.tier}", Palette.accentDark)
    val gb = Table()
    for ((i, k) in EmpireGoalKind.entries.withIndex()) {
        val b = TextButton(k.title, ui.smallButtonStyle).onClick { if (g.kind != k) { g.kind = k; g.tier = 0 }; openEmpire() }
        b.isChecked = g.kind == k
        gb.add(b).growX().uniformX().height(52f).pad(2f)
        if (i % 2 == 1) gb.row()
    }
    c.add(gb).growX().row()
    header(c, "Koloni Federasyonu")
    val max = s.federation.maxColonies(s)
    line(c, if (max == 0) "Yan koloni kurmak için Kraliyet Merkezi gerekir." else "Yan koloni: ${s.federation.colonies.size}/$max • Kuruluş: 2 kraliyet özü, 40 yiyecek, 20 toprak ve 6 işçi.", Palette.uiTextSoft)
    for (col in s.federation.colonies) card(c) { row ->
        row.add(ui.small("${col.name} • Nüfus ${col.population} • Yiyecek ${col.food.toInt()} • Gönderilen ${col.totalSent}")).left().expandX()
        row.add(ui.button("Destek (20)", small = true) { result(s.federation.support(s, col), "Destek gönderildi") }).height(48f)
    }
    if (max > s.federation.colonies.size) c.add(ui.accent("Yeni yan koloni kur") { result(s.federation.found(s), "Yan koloni kuruldu") }).growX().height(58f).row()
    header(c, "Başarımlar (${s.achievements.unlocked.size}/${Achievement.entries.size})")
    for (a in Achievement.entries) {
        val done = a in s.achievements.unlocked
        line(c, "${if (done) "✓" else "○"} ${a.title} — ${a.description} (+${a.coins} jeton)", if (done) Palette.accentDark else Palette.uiTextSoft)
    }
}

// ==================================================================== Ekonomi ve ekosistem

fun Hud.openEconomy() {
    openSheet("Kaynak Ekonomisi", 0.7f) { c -> economyBody(c) }
    liveContent { economyBody(it) }
}

private fun Hud.economyBody(c: Table) {
    val s = sim
    for (kind in StorageKind.entries) line(c, "${kind.title}: ${s.resources.totalOf(kind)}/${s.capacityOf(kind)}", Palette.accentDark)
    header(c, "Akışlar")
    line(c, "Tüketim: ~${"%.1f".format(s.foodConsumptionPerMinute())} yiyecek/dk (askerler %30 fazla yer)")
    val farmRate = s.rooms.filter { it.type == RoomType.FUNGUS_FARM && it.functional }.sumOf { (s.farmRate(it) * 60).toDouble() }
    line(c, "Mantar çiftlikleri: ~${"%.1f".format(farmRate)} mantar/dk (yaprak taşındıkça) • Yaprak stoğu ${s.resources[ResourceType.LEAF]}")
    line(c, "Günlük bakım: ${s.dailyMaintenance()} toprak • Toprak stoğu ${s.resources[ResourceType.SOIL]} (fazlası yüzeye atılır: ${s.stats.soilDumped})")
    line(c, "Yüzey kaynakları: ${s.sources.size} nokta, toplam ${s.sources.sumOf { it.amount }} birim • Mevsim çarpanı ×${s.season.sourceMult}")
    header(c, "Üretim zincirleri")
    line(c, "Yaprak → Mantar Çiftliği → Mantar → Besin")
    line(c, "Odun → Özel Üretim (Reçine) → Tuzak/Su Deposu/Arşiv")
    line(c, "Mantar + Su → Özel Üretim (Enzim) → Kraliyet Merkezi (Kraliyet özü) → Yan koloni, Kraliyet Muhafızı")
    line(c, "Mineral → Özel Üretim (Nadir mineral) → Kale, Elit asker")
    line(c, "Genetik örnek → Genetik Odası → Kalıcı koloni özelliği")
    header(c, "İstasyonlar")
    for (r in s.rooms.filter { it.type.isStation && it.functional }) line(c, "${r.type.title}: ${r.workers}/${r.stationCapacity} çalışan • ${if (s.stationHasWork(r)) "çalışıyor" else "iş yok / girdi eksik"}")
}

fun Hud.openEcosystem() {
    openSheet("Ekosistem ve İklim", 0.66f) { c -> ecosystemBody(c) }
    liveContent { ecosystemBody(it) }
}

private fun Hud.ecosystemBody(c: Table) {
    val s = sim
    val env = s.environment
    val dayInSeason = (s.day - 1) % Environment.DAYS_PER_SEASON + 1
    line(c, "Yıl ${env.year(s.day)} • ${s.season.title} (gün $dayInSeason/${Environment.DAYS_PER_SEASON})")
    line(c, "Hava: ${env.weather.title} (~${((env.weatherUntil - s.time) / 60).toInt().coerceAtLeast(0)} dk daha)" + if (env.droughtLevel > 0.1f) " • Kuraklık %${(env.droughtLevel * 100).toInt()}" else "")
    line(c, "Sıcaklık: yüzey ${"%.1f".format(env.surfaceTemp)}°C, koloni ${"%.1f".format(env.colonyTemp)}°C (ideal 22–28) • Nem %${env.humidity.toInt()} (ideal 50–70)")
    line(c, "Yavru gelişim çarpanı ×${"%.2f".format(env.climateFactor(s.mods))}", Palette.accentDark)
    line(c, "Su basmış hücre: ${env.water.size}" + if (env.water.isNotEmpty()) " — drenaj için odaların altına derin bir kuyu kazın, kum zemin suyu hızlı emer, Su Deposu suyu toplar." else "", if (env.water.isNotEmpty()) Palette.hex("2E7FB8") else Palette.uiText)
    line(c, "Hastalık: ${s.ants.count { it.sick }} karınca • Temizlenmemiş ceset: ${s.corpses.size}")
    header(c, "Mevsimler")
    line(c, "İlkbahar: bol yağmur ve kaynak • Yaz: sıcak, kuraklık riski • Sonbahar: yağmur, orta kaynak • Kış: soğuk, az kaynak, sakin canlılar.", Palette.uiTextSoft)
    header(c, "Son dünya olayları")
    if (s.events.history.isEmpty()) line(c, "Henüz olay yaşanmadı.", Palette.uiTextSoft)
    for ((d, k) in s.events.history.asReversed().take(10)) line(c, "Gün $d: ${k.title}")
}

/** Bir oyuncu işleminin ses çıkarması için kısayol. */
internal fun Hud.click() = sounds.play(SoundCue.CLICK)
