package com.zamaninefendisi.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.zamaninefendisi.game.ZamaninEfendisiGame
import com.zamaninefendisi.game.render.Cosmetics
import com.zamaninefendisi.game.render.IsoLayout
import com.zamaninefendisi.game.render.IsoRenderer
import com.zamaninefendisi.game.ui.Theme
import com.zamaninefendisi.logic.Pos
import com.zamaninefendisi.logic.meta.BuildingType
import com.zamaninefendisi.logic.meta.Kingdom
import com.zamaninefendisi.logic.meta.ResearchCategory
import com.zamaninefendisi.logic.meta.ResearchTree
import com.zamaninefendisi.logic.meta.Resource
import com.zamaninefendisi.logic.meta.Shop
import com.zamaninefendisi.logic.meta.ShopCategory
import com.zamaninefendisi.logic.meta.describe
import kotlin.math.roundToInt

/** Zaman Kalesi: 15 binanın görsel olarak büyüdüğü kalıcı üs. */
class CastleScreen(game: ZamaninEfendisiGame) : BaseScreen(game) {
    private val renderer = IsoRenderer(game.shapes)
    private val w = 6
    private val h = 6
    /** Bina yerleşimi (çekirdek ortada). */
    private val slots: Map<BuildingType, Pos> = mapOf(
        BuildingType.CORE to Pos(2, 2), BuildingType.LAB to Pos(1, 1), BuildingType.STORAGE to Pos(3, 1), BuildingType.ACADEMY to Pos(4, 2),
        BuildingType.TECH_CENTER to Pos(1, 3), BuildingType.PORTAL to Pos(3, 3), BuildingType.POWER_PLANT to Pos(0, 2), BuildingType.MUSEUM to Pos(2, 0),
        BuildingType.EXPLORATION to Pos(5, 1), BuildingType.TRADE to Pos(2, 4), BuildingType.DEFENSE_TOWER to Pos(4, 4), BuildingType.CRYSTAL_MINE to Pos(0, 4),
        BuildingType.ARCHIVE to Pos(4, 0), BuildingType.WORKSHOP to Pos(0, 0), BuildingType.QUEST_CENTER to Pos(5, 3),
    )
    private var layout = IsoLayout(0f, 0f, 60f)
    private var selected: BuildingType? = null
    private val info = Table()
    private val pendingLabel = ui.label("", assets.fontS, Theme.ink, wrap = true, align = Align.center)
    private val tmp = Vector2()
    private var acc = 0f

    init {
        root.top().pad(24f)
        root.add(header("Zaman Kalesi") { onBack() }).growX().padBottom(10f).row()
        root.add(resourceBar()).padBottom(6f).row()
        root.add(ui.label("Kale gücü: ${game.progression.kingdomPower()}", assets.fontS, Theme.purple)).row()
        root.add().expandY().row()
        val box = ui.panel(Theme.panel)
        box.pad(14f, 20f, 14f, 20f)
        box.add(pendingLabel).width(600f).row()
        val row = Table()
        row.defaults().pad(6f)
        row.add(ui.button("Üretimi Topla", Theme.green, assets.fontS) { collect() }).size(210f, 70f)
        row.add(ui.button("Araştırma", Theme.blue, assets.fontS) { game.go(ResearchScreen(game)) }).size(180f, 70f)
        row.add(ui.button("Market", Theme.orange, assets.fontS) { game.go(MarketScreen(game)) }).size(160f, 70f)
        box.add(row).row()
        root.add(info).width(680f).padBottom(8f).row()
        root.add(box).width(680f)
        refreshPending()
        showInfo(null)
    }

    private fun collect() {
        val got = game.progression.collectProduction()
        if (got.isEmpty()) ui.toast(root, "Henüz toplanacak üretim yok")
        else { ui.toast(root, "Toplandı: " + got.describe(), Theme.green); game.audio.play("grow") }
        refreshPending()
        game.go(CastleScreen(game))
    }

    private fun refreshPending() {
        val p = game.progression
        p.tickProduction()
        val pend = p.data.production.pending.filterValues { it >= 1.0 }.mapValues { it.value.toInt() }
        val cap = Kingdom.storageCap(p.data.buildings[BuildingType.STORAGE] ?: 0, p.data.research)
        pendingLabel.setText(if (pend.isEmpty()) "Binalar üretiyor… (depo sınırı $cap)" else "Biriken üretim: ${pend.describe()} (depo sınırı $cap)")
    }

    private fun showInfo(b: BuildingType?) {
        selected = b
        info.clearChildren()
        val p = game.progression
        val card = ui.panel(Theme.panel)
        card.pad(16f, 20f, 16f, 20f)
        if (b == null) {
            card.add(ui.label("Bir binaya dokunarak bilgilerini gör ve geliştir. Binalar sen oyunda değilken de üretir.", assets.fontS, Theme.inkSoft, wrap = true)).width(620f)
            info.add(card).width(680f)
            return
        }
        val lvl = p.data.buildings[b] ?: 0
        card.add(ui.label("${b.title} · ${if (lvl == 0) "kurulmadı" else "Seviye $lvl/${b.maxLevel}"}", assets.fontM, Theme.ink)).left().row()
        card.add(ui.label(b.description, assets.fontS, Theme.inkSoft, wrap = true)).width(620f).left().row()
        if (b.produces.isNotEmpty() && lvl > 0) {
            val prod = b.productionPerHour(lvl).entries.joinToString(" · ") { "${(it.value * ResearchTree.productionMultiplier(it.key, p.data.research) * 10).roundToInt() / 10.0} ${it.key.title}/saat" }
            card.add(ui.label("Üretim: $prod", assets.fontS, Theme.green, wrap = true)).width(620f).left().row()
        }
        if (lvl < b.maxLevel) {
            val cost = b.costFor(lvl + 1)
            val chk = Kingdom.canUpgrade(b, p.data.buildings, p.data.wallet)
            card.add(ui.label("${if (lvl == 0) "İnşa" else "Seviye ${lvl + 1}"} maliyeti: ${cost.describe()}", assets.fontS, Theme.orange, wrap = true)).width(620f).left().row()
            val btn = ui.button(if (lvl == 0) "İnşa Et" else "Geliştir", Theme.green, assets.fontS) {
                if (p.upgradeBuilding(b)) { game.audio.play("build"); ui.toast(root, "${b.title} seviye ${lvl + 1}!", Theme.green); game.go(CastleScreen(game).also { it.preselect(b) }) }
            }
            btn.isDisabled = !chk.ok
            card.add(btn).width(260f).height(70f).padTop(8f).row()
            chk.reason?.let { card.add(ui.label(it, assets.fontS, Theme.red, wrap = true)).width(620f).left().row() }
        } else card.add(ui.label("En yüksek seviyede.", assets.fontS, Theme.green)).left().row()
        info.add(card).width(680f)
    }

    fun preselect(b: BuildingType) = showInfo(b)

    override fun extraInput(): InputProcessor = object : InputAdapter() {
        private var dx = 0; private var dy = 0
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean { dx = screenX; dy = screenY; return true }
        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (Math.abs(screenX - dx) > 25 || Math.abs(screenY - dy) > 25) return false
            tmp.set(screenX.toFloat(), screenY.toFloat()); stage.screenToStageCoordinates(tmp)
            val p = layout.pick(tmp.x, tmp.y)
            val b = slots.entries.firstOrNull { it.value == p }?.key
            showInfo(b)
            return true
        }
    }

    override fun drawWorld(delta: Float) {
        acc += delta
        if (acc > 2f) { acc = 0f; refreshPending() }
        root.validate()
        val bottom = 330f + info.height
        layout = IsoLayout.fit(w, h, 20f, bottom, stage.width - 40f, (stage.height - 230f) - bottom)
        renderer.cosmetics = Cosmetics(game.progression.data.equipped)
        val sr = game.shapes
        sr.projectionMatrix = stage.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        sr.begin(ShapeRenderer.ShapeType.Filled)
        val lv = game.progression.data.buildings
        val list = slots.map { (b, pos) -> Triple(pos, b.ordinal, lv[b] ?: 0) }
        renderer.drawCastleScene(layout, w, h, list, selected?.let { slots[it] }, time)
        sr.end()
    }

    override fun onBack() = game.go(MainMenuScreen(game))
}

/** Beş kategorili araştırma ağacı. */
class ResearchScreen(game: ZamaninEfendisiGame) : BaseScreen(game) {
    init {
        root.top().pad(24f)
        root.add(header("Araştırma") { onBack() }).growX().padBottom(10f).row()
        root.add(resourceBar()).padBottom(10f).row()
        val p = game.progression
        val lab = p.data.buildings[BuildingType.LAB] ?: 0
        root.add(ui.label(if (lab == 0) "Araştırma için Zaman Kalesi'nde Araştırma Laboratuvarı kur." else "Laboratuvar seviyesi $lab: ${lab}. kademeye kadar araştırma yapılabilir.", assets.fontS, Theme.purple, wrap = true, align = Align.center)).width(640f).padBottom(10f).row()
        val list = Table().top()
        for (cat in ResearchCategory.entries) {
            list.add(ui.label(cat.title, assets.fontL, Theme.ink)).left().padTop(12f).row()
            list.add(ui.label(cat.description, assets.fontS, Theme.inkSoft)).left().padBottom(6f).row()
            for (n in ResearchTree.nodes.filter { it.category == cat }) {
                val done = n.id in p.data.research
                val chk = ResearchTree.canResearch(n, p.data.research, lab, p.data.wallet)
                val card = ui.panel(if (done) Color.valueOf("E8F7EA") else Theme.panel, small = true)
                card.pad(12f, 16f, 12f, 16f)
                val left = Table()
                left.add(ui.label("Kademe ${n.tier} · ${n.title}", assets.fontM, if (done) Theme.green else Theme.ink)).left().row()
                left.add(ui.label(n.effect, assets.fontS, Theme.inkSoft, wrap = true)).width(400f).left().row()
                if (!done) left.add(ui.label(n.cost.describe(), assets.fontS, Theme.orange, wrap = true)).width(400f).left().row()
                if (!done && chk.reason != null && chk.reason != "Araştırıldı") left.add(ui.label(chk.reason!!, assets.fontS, Theme.red, wrap = true)).width(400f).left().row()
                card.add(left).expandX().left()
                if (done) card.add(ui.label("✓", assets.fontL, Theme.green)).width(160f)
                else {
                    val b = ui.button("Araştır", Theme.blue, assets.fontS) { if (p.research(n.id)) { game.audio.play("win", 0.4f); game.go(ResearchScreen(game)) } }
                    b.isDisabled = !chk.ok
                    card.add(b).size(160f, 70f)
                }
                list.add(card).width(660f).padBottom(8f).row()
            }
        }
        root.add(ui.scroll(list)).grow()
    }

    override fun onBack() = game.go(CastleScreen(game))
}

/** Oyun içi para ile çalışan market. Kozmetikler satın alınınca kuşanılır ve oyunda görünür. */
class MarketScreen(game: ZamaninEfendisiGame, private val category: ShopCategory = ShopCategory.CASTLE_SKIN) : BaseScreen(game) {
    init {
        root.top().pad(24f)
        root.add(header("Market") { onBack() }).growX().padBottom(10f).row()
        root.add(resourceBar()).padBottom(10f).row()
        val tabs = Table()
        for ((i, c) in ShopCategory.entries.withIndex()) {
            val b = ui.button(c.title, if (c == category) Theme.orange else Theme.grey, assets.fontS, if (c == category) Color.WHITE else Theme.ink) { game.go(MarketScreen(game, c)) }
            tabs.add(b).width(220f).height(62f).pad(3f)
            if (i % 3 == 2) tabs.row()
        }
        root.add(tabs).padBottom(10f).row()
        val p = game.progression
        val list = Table().top()
        val tradeLevel = p.data.buildings[BuildingType.TRADE] ?: 0
        val items = Shop.items.filter { it.category == category }
        for (item in items) {
            val locked = item.requiresResearch != null && item.requiresResearch !in p.data.research
            val owned = item.id in p.data.owned
            val equipped = p.equipped(category) == item.id
            val card = ui.panel(if (equipped) Color.valueOf("FFF1D6") else Theme.panel, small = true)
            card.pad(12f, 16f, 12f, 16f)
            val left = Table()
            left.add(ui.label(item.title, assets.fontM, Theme.ink)).left().row()
            left.add(ui.label(item.description, assets.fontS, Theme.inkSoft, wrap = true)).width(400f).left().row()
            val price = Shop.priceFor(item, tradeLevel)
            if (!owned || item.consumable) left.add(ui.label(if (price.values.all { it == 0 }) "Ücretsiz" else price.describe(), assets.fontS, Theme.orange)).left().row()
            if (locked) left.add(ui.label("Gerekli araştırma: ${ResearchTree.node(item.requiresResearch!!)?.title}", assets.fontS, Theme.red)).left().row()
            card.add(left).expandX().left()
            if (item.colors.isNotEmpty()) {
                val sw = Table()
                for (c in item.colors.take(4)) sw.add(Table().apply { background = assets.panel(Color.valueOf(c), true) }).size(26f).pad(2f)
                card.add(sw).padRight(8f)
            }
            val btn = when {
                equipped -> ui.button("Kuşanıldı", Theme.green, assets.fontS) {}
                owned && !item.consumable -> ui.button("Kuşan", Theme.blue, assets.fontS) { p.equip(item.id); game.go(MarketScreen(game, category)) }
                else -> ui.button(if (item.consumable) "Satın Al" else "Al ve Kuşan", Theme.orange, assets.fontS) {
                    val err = p.buy(item.id)
                    if (err == null) { game.audio.play("win", 0.4f); ui.toast(root, "${item.title} alındı!", Theme.green); game.go(MarketScreen(game, category)) }
                    else ui.toast(root, err, Theme.red)
                }
            }
            btn.isDisabled = locked
            card.add(btn).size(170f, 70f)
            list.add(card).width(660f).padBottom(8f).row()
        }
        list.add(ui.label("Market yalnızca oyun içi kaynaklarla çalışır; ilerlemek için hiçbir satın alma gerekmez. Kaynak paketlerinde Ticaret Merkezi seviyesine göre indirim uygulanır.", assets.fontS, Theme.muted, wrap = true, align = Align.center)).width(620f).padTop(8f)
        root.add(ui.scroll(list)).grow()
    }

    override fun onBack() = game.go(CastleScreen(game))
}

/** Envanter: bütün kaynaklar ve sahip olunan/kuşanılan ürünler. */
class InventoryScreen(game: ZamaninEfendisiGame) : BaseScreen(game) {
    init {
        root.top().pad(24f)
        root.add(header("Envanter") { onBack() }).growX().padBottom(16f).row()
        val p = game.progression
        val res = ui.panel(Theme.panel)
        res.pad(18f, 24f, 18f, 24f)
        res.add(ui.label("Kaynaklar", assets.fontM, Theme.ink)).colspan(2).left().padBottom(8f).row()
        for (r in Resource.entries) {
            res.add(ui.label(r.title + if (r.special) " (özel)" else "", assets.fontS, if (r.special) Theme.purple else Theme.inkSoft)).left().expandX().padBottom(6f)
            res.add(ui.label("${p.amount(r)}", assets.fontS, Theme.ink)).right().padBottom(6f).row()
        }
        root.add(res).width(660f).padBottom(14f).row()
        val eq = ui.panel(Theme.panel)
        eq.pad(18f, 24f, 18f, 24f)
        eq.add(ui.label("Kuşanılanlar", assets.fontM, Theme.ink)).colspan(2).left().padBottom(8f).row()
        for (c in ShopCategory.entries.filter { it != ShopCategory.RESOURCE_PACK }) {
            eq.add(ui.label(c.title, assets.fontS, Theme.inkSoft)).left().expandX().padBottom(6f)
            eq.add(ui.label(p.equipped(c)?.let { Shop.item(it)?.title } ?: "—", assets.fontS, Theme.ink)).right().padBottom(6f).row()
        }
        eq.add(ui.label("Sahip olunan ürün: ${p.data.owned.size}", assets.fontS, Theme.orange)).colspan(2).left().padTop(6f).row()
        root.add(eq).width(660f).padBottom(14f).row()
        root.add(ui.button("Markete Git", Theme.orange) { game.go(MarketScreen(game)) }).width(360f).height(80f)
    }

    override fun onBack() = game.go(MainMenuScreen(game))
}
