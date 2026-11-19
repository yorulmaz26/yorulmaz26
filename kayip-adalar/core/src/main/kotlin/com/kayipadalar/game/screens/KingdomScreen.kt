package com.kayipadalar.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Scaling
import com.kayipadalar.game.KayipAdalarGame
import com.kayipadalar.game.ui.Palette
import com.kayipadalar.game.view.BattleScene
import com.kayipadalar.game.view.KingdomView
import com.kayipadalar.logic.kingdom.*
import kotlin.math.roundToInt

/** Ana oyun ekranı: krallık, kaynak çubuğu, görev kartı ve alt menü. */
class KingdomScreen(app: KayipAdalarGame) : BaseScreen(app) {
    lateinit var view: KingdomView
    private val hud = Table()
    private val questCard = Table()
    private var tickTimer = 0f
    private var hudTimer = 0f
    private var lastQuest = -1
    private var pendingArrival: Int? = null

    override fun build() {
        if (!::view.isInitialized) {
            view = KingdomView(k, assets, theme) { app.game.now() }
            view.focus(com.badlogic.gdx.math.Vector2(0f, 20f), 0.74f)
        }
        view.setFillParent(true)
        stage.addActor(view)
        view.toBack()
        view.onPlot = { isl, plot -> buildSheet(isl, plot) }
        view.onBuilding = { buildingSheet(it) }
        view.onCollect = { collect(it) }
        view.onIsland = { islandSheet(it) }
        view.onRaid = { raidDialog() }
        view.onDock = { d -> view.moveMode?.let { id -> k.moveIsland(id, d)?.let { error(it) } ?: run { app.game.persist(); toast("Ada yeni yerine süzüldü.") } }; view.moveMode = null }
        pendingArrival?.let { view.arrive(it); pendingArrival = null }

        root.touchable = Touchable.childrenOnly
        root.top()
        root.add(hud).growX().pad(10f, 10f, 0f, 10f).row()
        root.add(questCard).left().pad(8f, 10f, 0f, 10f).width(470f).row()
        root.add().grow().row()
        val nav = Table()
        nav.background = theme.tinted("white", Color(0.08f, 0.12f, 0.22f, 0.55f))
        nav.pad(6f, 4f, 10f, 4f)
        nav.add(navButton("İnşa", "buildings/hi_house") { quickBuild(null) }).expandX()
        nav.add(navButton("Ordu", "units/u_infantry") { armySheet() }).expandX()
        nav.add(navButton("Araştır", "buildings/research") { researchSheet() }).expandX()
        nav.add(navButton("Harita", "resources/map", badge = k.s.islands.any { it.status == IslandStatus.CLEARED }) { openWorld() }).expandX()
        nav.add(navButton("Görevler", "icons/scroll") { questsSheet() }).expandX()
        root.add(nav).growX()
        refreshHud()
        if (!app.game.save.seenIntro) intro()
    }

    override fun show() {
        super.show()
        app.game.tick()
    }

    fun mergeArrived(id: Int) { pendingArrival = id }

    override fun onBack() = app.back()

    override fun afterBattle() = refreshHud()

    override fun update(dt: Float) {
        tickTimer += dt; hudTimer += dt
        if (tickTimer > 0.25f) {
            tickTimer = 0f
            val ev = app.game.tick()
            for (e in ev) { toast(e); app.audio.play(if (e.startsWith("Korsan")) "error" else "unlock", 0.6f) }
        }
        if (hudTimer > 0.5f) { hudTimer = 0f; refreshHud() }
    }

    // ------------------------------------------------------------------ HUD

    private fun refreshHud() {
        hud.clear()
        val row = Table()
        for (r in Res.entries) {
            val chip = Table()
            chip.background = theme.patch("chip")
            chip.pad(4f, 6f, 4f, 10f)
            chip.add(sprite(r.icon, 36f)).size(36f).padRight(4f)
            chip.add(label(fmt(k.res(r).toLong()), "lightSmall"))
            row.add(chip).expandX().fillX().pad(2f)
        }
        hud.add(row).growX().row()
        val row2 = Table()
        fun info(icon: String, text: String) {
            val c = Table(); c.background = theme.patch("chip"); c.pad(3f, 6f, 3f, 10f)
            c.add(sprite(icon, 30f)).size(30f).padRight(4f); c.add(label(text, "lightTiny"))
            row2.add(c).padRight(6f)
        }
        info("resources/ui_people", "Nüfus ${k.s.pop.toInt()}/${k.popCap()}")
        info("icons/swords", "Güç ${k.armyPower()}")
        info("units/u_worker", "İnşaatçı ${k.builders() - k.activeBuilds()}/${k.builders()}")
        row2.add().expandX()
        row2.add(button("", "smallWood", icon = "icons/gear", iconSize = 40f) { app.goTo(SettingsScreen(app)) }).size(70f, 62f)
        hud.add(row2).growX().padTop(4f)
        refreshQuest()
    }

    private fun refreshQuest() {
        val q = k.currentQuest()
        questCard.clear()
        questCard.background = theme.patch("navy")
        questCard.pad(10f)
        questCard.add(Image(assets["portraits/scholar"]).also { it.setScaling(Scaling.fit) }).size(64f, 88f).padRight(10f)
        val t = Table()
        t.add(label(q.def.chapter, "lightTiny", Palette.gold, wrap = true)).growX().left().row()
        t.add(label(q.def.text, "lightSmall", wrap = true)).growX().left().row()
        val prog = Table()
        prog.add(progressBar(height = 16f) { (k.currentQuest().progress.toFloat() / maxOf(1, k.currentQuest().def.amount)) }).growX().height(16f)
        prog.add(label("${q.progress}/${q.def.amount}", "lightTiny")).padLeft(8f)
        t.add(prog).growX().padTop(4f)
        questCard.add(t).growX()
        val btn = if (q.done) button("Ödül", "smallGreen") { claimQuest() } else button("Git", "smallWood") { goQuest() }
        if (q.done && lastQuest != q.index) {
            btn.isTransform = true; btn.setOrigin(Align.center)
            btn.addAction(Actions.forever(Actions.sequence(Actions.scaleTo(1.08f, 1.08f, 0.4f), Actions.scaleTo(1f, 1f, 0.4f))))
        }
        questCard.add(btn).size(110f, 70f).padLeft(8f)
    }

    private fun intro() {
        app.game.save.seenIntro = true
        app.game.persist()
        dialog(
            "Hoş geldin, Muhafız!",
            "Ben Bilgin Faruk. Büyük Parçalanma'dan sonra krallığımız bu tek adaya sığdı. Önce yıkık Ana Bina'yı onaralım.\n\n" +
                "Soldaki görev kartı sana her zaman ne yapacağını söyler. Takılırsan \"Git\"e bas.",
            "portraits/scholar", null, "Başlayalım" to { goQuest() },
        )
    }

    private fun claimQuest() {
        val (err, reward) = k.claimQuest()
        if (err != null) { error(err); return }
        app.audio.play("win", 0.6f)
        app.game.persist()
        val r = Table()
        r.add(costRow(reward) { Double.MAX_VALUE }).row()
        val next = k.currentQuest()
        dialog("Görev tamamlandı!", "Sıradaki görev: ${next.def.text}\n${next.def.hint}", "portraits/scholar", r, "Git" to { goQuest() }, "Tamam" to {})
        refreshHud()
    }

    /** Görevin hedefine götürür: ilgili binayı açar, arsayı gösterir veya haritaya geçer. */
    private fun goQuest() {
        val q = k.currentQuest().def
        view.pointer = null
        when (q.type) {
            QuestType.LEVEL, QuestType.COUNT -> {
                val existing = k.s.buildings.firstOrNull { it.type == q.target }
                if (existing != null && (q.type == QuestType.LEVEL || k.cfg.building(q.target!!).unique)) {
                    view.buildingAnchor(existing)?.let { view.focus(it) }
                    view.pointer = existing.island to existing.plot
                    buildingSheet(existing)
                } else quickBuild(q.target)
            }
            QuestType.COLLECT -> {
                val b = k.s.buildings.filter { k.cfg.building(it.type).produces == q.target }.maxByOrNull { it.stored }
                if (b == null) quickBuild("lumber") else { view.buildingAnchor(b)?.let { view.focus(it) }; view.pointer = b.island to b.plot; toast("Binanın üstündeki kaynak simgesine dokun.") }
            }
            QuestType.POP -> quickBuild("house")
            QuestType.ARMY, QuestType.POWER -> armySheet()
            QuestType.EXPLORE, QuestType.CONQUER, QuestType.MERGE -> openWorld()
            QuestType.RESEARCH -> researchSheet()
            QuestType.DEFEND -> if (k.s.raid != null) raidDialog() else toast("Korsanlar her ${k.r.raidEveryMinutes} dakikada bir gelir (Ana Bina 3+). Kule kur ve ordunu güçlendir.")
        }
        toast(q.hint)
    }

    private fun freePlots(): List<Pair<Int, Int>> = k.s.islands.filter { it.status == IslandStatus.MERGED }.sortedBy { it.id }
        .flatMap { i -> (0 until k.plotsOf(i)).filter { k.buildingAt(i.id, it) == null }.map { i.id to it } }

    private fun quickBuild(highlight: String?) {
        val p = freePlots().firstOrNull()
        if (p == null) { dialog("Yer kalmadı", "Bütün arsalar dolu. Haritadan yeni bir ada kurtarıp krallığına çekerek yeni arsalar kazan.", "portraits/explorer", null, "Haritaya git" to { openWorld() }, "Tamam" to {}); return }
        k.island(p.first)?.let { view.focus(view.plotPos(it, p.second)) }
        view.pointer = p
        buildSheet(p.first, p.second, highlight)
    }

    // ------------------------------------------------------------------ Bina panelleri

    private fun buildSheet(islandId: Int, plot: Int, highlight: String? = null) {
        val list = Table()
        val defs = k.buildable().sortedWith(compareBy({ it.id != highlight }, { it.unlockCastle }))
        for (d in defs) {
            val err = k.canBuild(d.id, islandId, plot)
            val row = Table()
            row.background = theme.tinted("parchment", if (d.id == highlight) Color(1f, 0.93f, 0.6f, 1f) else Color.WHITE)
            row.pad(10f)
            row.add(sprite(d.sprite, 110f)).size(110f).padRight(10f)
            val info = Table()
            info.add(label(d.name, "default")).left().row()
            info.add(label(d.desc, "tiny", wrap = true)).growX().left().row()
            info.add(costRow(d.costFor(1)) { k.res(it) }).left().padTop(4f).row()
            info.add(label("Süre: ${fmtTime(d.timeFor(1, k.buildSpeed()).toLong())}", "tiny")).left().row()
            if (err != null) info.add(label(err, "tiny", Palette.red, wrap = true)).growX().left()
            row.add(info).growX()
            row.add(button("Kur", "smallGreen", err == null) {
                k.build(d.id, islandId, plot, app.game.now())?.let { error(it) } ?: run {
                    closeOverlay(); app.audio.play("bridge", 0.6f); view.pointer = null
                    k.island(islandId)?.let { view.burst(view.plotPos(it, plot), "effects/fx_build2") }
                    app.game.persist(); refreshHud()
                }
            }).size(110f, 72f).padLeft(6f)
            list.add(row).growX().pad(4f).row()
        }
        sheet("Ne inşa edelim?", list, "effects/fx_build1")
    }

    private fun collect(b: BuildingState) {
        val got = k.collect(b.uid)
        val res = Res.valueOf(k.cfg.building(b.type).produces!!)
        if (got <= 0) { error("Depo dolu! Depo kur veya yükselt."); return }
        app.audio.play("coin", 0.7f); app.vibrate(10)
        view.buildingAnchor(b)?.let { view.floatText("+$got", res.icon, it.cpy().add(0f, 150f), Palette.gold) }
        refreshHud()
    }

    private fun buildingSheet(b: BuildingState) {
        val d = k.cfg.building(b.type)
        val n = app.game.now()
        val c = Table()
        val top = Table()
        top.add(sprite(d.sprite, 170f)).size(170f).padRight(12f)
        val info = Table()
        info.add(label(if (b.level == 0) (if (b.type == "castle") "Yıkık" else "İnşa ediliyor") else "Seviye ${b.level} / ${d.maxLevel}", "default", Palette.navy)).left().row()
        info.add(label(d.desc, "small", wrap = true)).growX().left().row()
        effectText(b)?.let { info.add(label(it, "small", Palette.green, wrap = true)).growX().left().padTop(4f).row() }
        top.add(info).growX()
        c.add(top).growX().row()
        if (b.busy) {
            c.add(progressBar(gold = true, height = 22f) { ((app.game.now() - b.busyStarted).toFloat() / (b.busyUntil - b.busyStarted).coerceAtLeast(1)) }).growX().height(22f).padTop(12f).row()
            val rem = b.busyUntil - n
            c.add(label("Kalan: ${fmtTime(rem / 1000)}", "small")).padTop(4f).row()
            val cost = k.speedupCost(rem)
            c.add(button("Hızlandır ($cost kristal)", "blue", k.res(Res.CRYSTAL) >= cost, icon = "resources/crystal") {
                k.speedupBuilding(b.uid, app.game.now())?.let { error(it) } ?: run { closeOverlay(); app.audio.play("unlock"); refreshHud(); app.game.persist() }
            }).growX().height(80f).padTop(10f).row()
        } else if (b.level < d.maxLevel) {
            val err = k.canUpgrade(b)
            c.add(label(if (b.level == 0) "Onarım maliyeti" else "${b.level + 1}. seviye maliyeti", "small")).left().padTop(12f).row()
            c.add(costRow(k.upgradeCost(b)) { k.res(it) }).left().row()
            c.add(label("Süre: ${fmtTime(k.upgradeTime(b).toLong())}", "tiny")).left().row()
            if (err != null) c.add(label(err, "small", Palette.red, wrap = true)).growX().left().row()
            c.add(button(if (b.level == 0) "Onar" else "Yükselt", "green", err == null, icon = "effects/fx_up0") {
                k.upgrade(b.uid, app.game.now())?.let { error(it) } ?: run {
                    closeOverlay(); app.audio.play("bridge"); view.pointer = null
                    view.buildingAnchor(b)?.let { view.burst(it.cpy().add(0f, 60f), "effects/fx_up2") }
                    app.game.persist(); refreshHud()
                }
            }).growX().height(84f).padTop(10f).row()
        } else c.add(label("En üst seviyede!", "default", Palette.green)).padTop(10f).row()
        // Binaya özel kısayollar
        when (b.type) {
            "barracks" -> if (b.level > 0) c.add(button("Asker Eğit", icon = "units/u_infantry") { armySheet() }).growX().height(80f).padTop(8f).row()
            "research" -> if (b.level > 0) c.add(button("Araştırmalar", icon = "buildings/research") { researchSheet() }).growX().height(80f).padTop(8f).row()
            "explore" -> if (b.level > 0) c.add(button("Haritayı Aç", icon = "resources/map") { openWorld() }).growX().height(80f).padTop(8f).row()
            "market" -> if (b.level > 0) c.add(marketTable()).growX().padTop(8f).row()
        }
        if (d.produces != null && b.level > 0 && !b.busy) {
            c.add(button("Topla (${b.stored.toInt()})", "smallWood", b.stored >= 1, icon = Res.valueOf(d.produces!!).icon) { closeOverlay(); collect(b) }).growX().height(72f).padTop(8f).row()
        }
        sheet(d.name, c)
    }

    private fun effectText(b: BuildingState): String? {
        val d = k.cfg.building(b.type)
        return when {
            d.produces != null -> "Üretim: saatte ${k.perHour(b).roundToInt()} ${Res.valueOf(d.produces!!).label} (birikim sınırı ${(k.perHour(b) * k.r.collectCapHours).roundToInt()})"
            b.type == "house" -> "Nüfus sınırı +${b.level * d.pop}"
            b.type == "warehouse" -> "Depo sınırı: ${k.storageCap().toInt()}"
            b.type == "tower" -> "Savunma gücü: ${k.defensePower()}"
            b.type == "castle" -> "İnşaatçı: ${k.builders()} · Depo: ${k.storageCap().toInt()} · Yeni binalar seviye arttıkça açılır"
            b.type == "explore" -> "Keşif menzili: ${b.level}. halka · ${b.level} keşif gemisi"
            b.type == "barracks" -> "Açık birlikler: " + k.cfg.units.filter { k.unitUnlocked(it) }.joinToString { it.name }
            b.type == "market" -> "Takas oranı: 1 → %.2f".format(k.tradeRate())
            else -> null
        }
    }

    private fun marketTable(): Table {
        val t = Table()
        val pairs = listOf(Res.WOOD to Res.STONE, Res.STONE to Res.WOOD, Res.FOOD to Res.GOLD, Res.GOLD to Res.FOOD, Res.WOOD to Res.GOLD, Res.STONE to Res.FOOD)
        for ((i, p) in pairs.withIndex()) {
            t.add(button("100 ${p.first.label} → ${(100 * k.tradeRate()).toInt()} ${p.second.label}", "smallWood", k.res(p.first) >= 100) {
                k.trade(p.first, p.second, 100)?.let { error(it) } ?: run { app.audio.play("coin"); refreshHud() }
            }).growX().height(66f).pad(3f)
            if (i % 2 == 1) t.row()
        }
        return t
    }

    // ------------------------------------------------------------------ Ordu ve araştırma

    private fun armySheet() {
        val c = Table()
        c.add(label("Ordu gücü: ${k.armyPower()} · Boş nüfus: ${k.freePop().coerceAtLeast(0)}", "default", Palette.navy)).left().row()
        if (k.levelOf("barracks") < 1) {
            c.add(label("Asker eğitmek için önce Kışla kur (Ana Bina 2. seviye).", "small", Palette.red, wrap = true)).growX().padTop(8f).row()
            c.add(button("Kışla kur", "green") { closeOverlay(); quickBuild("barracks") }).growX().height(80f).padTop(8f).row()
        }
        for (job in k.s.training) {
            val u = k.cfg.unit(job.unit)
            val row = Table()
            row.add(sprite(u.sprite, 50f)).size(50f)
            row.add(label("${u.name} ×${job.count} eğitiliyor", "small")).padLeft(6f).expandX().left()
            row.add(progressBar { 1f - ((job.nextAt - app.game.now()).toFloat() / job.each).coerceIn(0f, 1f) }).width(160f).height(16f)
            c.add(row).growX().padTop(6f).row()
        }
        for (u in k.cfg.units) {
            val row = Table()
            row.background = theme.tinted("parchment", Color.WHITE); row.pad(8f)
            row.add(sprite(u.sprite, 90f)).size(90f).padRight(8f)
            val info = Table()
            info.add(label("${u.name}  ·  Güç ${u.power}", "default")).left().row()
            info.add(costRow(u.cost.entries.associate { Res.valueOf(it.key) to it.value }) { k.res(it) }).left().row()
            info.add(label(if (k.unitUnlocked(u)) "Süre: ${u.time}sn / asker" else "Kışla ${u.barracks}. seviye gerekli", "tiny", if (k.unitUnlocked(u)) Palette.inkSoft else Palette.red)).left()
            row.add(info).growX()
            val counts = Table()
            for (n in listOf(1, 5, 10)) {
                counts.add(button("+$n", "smallGreen", k.canTrain(u.id, n) == null) {
                    // etiket tek satırda kalsın
                    k.train(u.id, n, app.game.now())?.let { error(it) } ?: run { app.audio.play("tap"); app.game.persist(); refreshHud(); armySheet() }
                }).size(86f, 62f).pad(2f)
            }
            row.add(counts)
            c.add(row).growX().pad(4f).row()
        }
        val army = k.s.units.filterValues { it > 0 }
        if (army.isNotEmpty()) c.add(label("Ordun: " + army.entries.joinToString { "${k.cfg.unit(it.key).name} ${it.value}" }, "small", wrap = true)).growX().left().padTop(8f).row()
        sheet("Ordu", c, "buildings/hi_barracks")
    }

    private fun researchSheet() {
        val c = Table()
        if (k.levelOf("research") < 1) c.add(label("Araştırma için önce Araştırma Merkezi kur (Ana Bina 3. seviye).", "small", Palette.red, wrap = true)).growX().row()
        k.s.researching?.let { j ->
            val d = k.cfg.researchDef(j.id)
            c.add(label("Sürüyor: ${d.name} ${j.level}. seviye", "default", Palette.navy)).left().row()
            c.add(progressBar(gold = true) { ((app.game.now() - j.started).toFloat() / (j.endsAt - j.started).coerceAtLeast(1)) }).growX().height(18f).row()
        }
        for (d in k.cfg.research) {
            val lv = k.s.research[d.id] ?: 0
            val err = k.canResearch(d.id)
            val row = Table()
            row.background = theme.tinted("parchment", Color.WHITE); row.pad(8f)
            row.add(sprite(d.icon, 80f)).size(80f).padRight(8f)
            val info = Table()
            info.add(label("${d.name}  ·  $lv/${d.maxLevel}", "default")).left().row()
            info.add(label(d.desc, "tiny", wrap = true)).growX().left().row()
            if (lv < d.maxLevel) {
                info.add(costRow(d.costFor(lv + 1)) { k.res(it) }).left().row()
                info.add(label("Süre: ${fmtTime(d.timeFor(lv + 1).toLong())}", "tiny")).left().row()
            }
            if (err != null && lv < d.maxLevel) info.add(label(err, "tiny", Palette.red, wrap = true)).growX().left()
            row.add(info).growX()
            row.add(button("Başla", "smallGreen", err == null) {
                k.startResearch(d.id, app.game.now())?.let { error(it) } ?: run { app.audio.play("unlock"); app.game.persist(); researchSheet() }
            }).size(110f, 70f)
            c.add(row).growX().pad(4f).row()
        }
        sheet("Araştırma", c, "buildings/research")
    }

    private fun questsSheet() {
        val c = Table()
        var chapter = ""
        for (i in 0 until maxOf(k.cfg.quests.size, k.s.questIndex + 1)) {
            val q = k.questDef(i)
            if (q.chapter != chapter) { chapter = q.chapter; c.add(label(chapter, "default", Palette.navy)).left().padTop(10f).row() }
            val done = i < k.s.questIndex
            val cur = i == k.s.questIndex
            val row = Table()
            row.add(sprite(if (done) "icons/check" else if (cur) "icons/star" else "icons/lock", 34f)).size(34f).padRight(8f)
            row.add(label(q.text, "small", if (done) Palette.green else if (cur) Palette.ink else Palette.inkSoft, wrap = true)).growX().left()
            c.add(row).growX().padTop(3f).row()
        }
        sheet("Görevler", c, "icons/scroll")
    }

    // ------------------------------------------------------------------ Ada ve baskın

    private fun islandSheet(i: IslandState) {
        val d = k.cfg.island(i.type)
        val c = Table()
        c.add(sprite(d.sprite, 200f)).size(200f).row()
        c.add(label(d.desc, "default", wrap = true, align = Align.center)).growX().row()
        c.add(label("${k.plotsOf(i)} arsa · Aynı türden yan yana adalar ek +%10 üretim verir.", "small", wrap = true, align = Align.center)).growX().padTop(6f).row()
        c.add(button("Adayı Taşı", "blue", icon = "effects/fx_portal1") { closeOverlay(); view.moveMode = i.id; toast("Parlayan halkalardan birine dokun: ada oraya süzülür.") }).growX().height(80f).padTop(10f).row()
        c.add(button("Krallıktan Ayır", "red", k.s.buildings.none { it.island == i.id }) {
            k.split(i.id)?.let { error(it) } ?: run { closeOverlay(); app.game.persist(); toast("Ada haritaya geri bırakıldı.") }
        }).growX().height(72f).padTop(6f).row()
        if (k.s.buildings.any { it.island == i.id }) c.add(label("Üzerinde bina olan ada ayrılamaz.", "tiny")).row()
        sheet(d.name, c)
    }

    private fun raidDialog() {
        val raid = k.s.raid ?: return
        val def = k.defensePower()
        dialog("Korsan baskını!", "Korsan gücü: ${raid.power}\nSavunman: $def (kuleler + ordunun %${k.r.armyDefensePct}'i)\n" +
            if (def >= raid.power) "Kazanabiliriz!" else "Güçleri fazla. Kaybedersen kaynaklarının %${k.r.raidLootPct}'u yağmalanır.",
            "portraits/commander", null, "Savun!" to {
                val ours = armySprites()
                val rep = k.defend(app.game.now()) ?: return@to
                battle(rep, ours, List(5) { listOf("units/u_infantry", "units/swordsman", "units/archer")[it % 3] }, null)
            }, "Bekle" to {})
    }

    fun armySprites(): List<String> {
        val list = ArrayList<String>()
        for (u in k.cfg.units.sortedByDescending { it.power }) repeat(minOf(3, k.s.units[u.id] ?: 0)) { list.add(u.sprite) }
        if (list.isEmpty()) list.addAll(listOf("buildings/defense_tower"))
        return list.take(9)
    }

    private fun openWorld() {
        closeOverlay()
        app.goTo(WorldScreen(app, this))
    }
}
