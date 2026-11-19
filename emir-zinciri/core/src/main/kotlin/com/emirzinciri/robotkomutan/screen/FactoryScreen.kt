package com.emirzinciri.robotkomutan.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.utils.Align
import com.emirzinciri.robotkomutan.EmirZinciriGame
import com.emirzinciri.robotkomutan.logic.factory.BuildingType
import com.emirzinciri.robotkomutan.logic.factory.Cost
import com.emirzinciri.robotkomutan.logic.factory.Effect
import com.emirzinciri.robotkomutan.logic.factory.Factory
import com.emirzinciri.robotkomutan.logic.factory.FactoryResult
import com.emirzinciri.robotkomutan.logic.factory.Research
import com.emirzinciri.robotkomutan.logic.factory.ResearchBranch
import com.emirzinciri.robotkomutan.logic.factory.Resource
import com.emirzinciri.robotkomutan.logic.factory.get
import com.emirzinciri.robotkomutan.ui.Assets
import com.emirzinciri.robotkomutan.ui.Theme
import kotlin.math.sin

/** Kalıcı Robot Fabrikası: binalar, üretim, araştırma ve ticaret. */
class FactoryScreen(game: EmirZinciriGame) : BaseScreen(game) {
    private val factory: Factory get() = services.progression.factory
    private val resources = Table()
    private val content = Table()
    private var tab = 0
    private val view = FactoryView(assets) { factory }
    private var tick = 0f
    private var saveTick = 0f

    init {
        val report = factory.update()
        root.top()
        val head = Table()
        head.add(ui.iconButton("back", size = 72f) { onBack() }).size(72f)
        head.add(ui.label("Robot Fabrikası", assets.fontTitle, Theme.ink)).padLeft(16f).left().growX()
        root.add(head).growX().pad(16f, 16f, 8f, 16f).row()
        root.add(resources).growX().padLeft(12f).padRight(12f).row()
        root.add(view).growX().height(330f).row()
        val tabs = Table()
        listOf("Binalar", "Araştırma", "Ticaret").forEachIndexed { i, t ->
            tabs.add(ui.button(t, if (tab == i) Theme.turquoise else Theme.white, if (tab == i) Theme.white else Theme.ink, null, assets.fontBold, 18) {
                tab = i; rebuildTabs(tabs)
            }).growX().uniformX().pad(4f)
        }
        root.add(tabs).growX().pad(4f, 12f, 4f, 12f).row()
        val scroll = ScrollPane(content, assets.scrollStyle)
        scroll.setScrollingDisabled(true, false)
        root.add(scroll).grow()
        refreshResources(); buildContent()
        if (report.gained.any { it.value > 0 }) {
            val text = report.gained.filterValues { it > 0 }.entries.joinToString(", ") { "+${it.value} ${it.key.title.lowercase()}" }
            val h = report.minutes / 60; val m = report.minutes % 60
            val time = if (h > 0) "$h sa $m dk" else "$m dk"
            ui.dialog(stage, "Üretim raporu", "Son $time içinde fabrikan üretti: $text." +
                if (report.capped) "\n\nBazı depolar doldu ya da çevrimdışı sınırına (${factory.offlineHours} saat) ulaşıldı: Kaynak Deposu'nu yükselt." else "",
                "Harika" to {})
        }
        services.persist()
    }

    private fun rebuildTabs(tabs: Table) {
        tabs.clear()
        listOf("Binalar", "Araştırma", "Ticaret").forEachIndexed { i, t ->
            tabs.add(ui.button(t, if (tab == i) Theme.turquoise else Theme.white, if (tab == i) Theme.white else Theme.ink, null, assets.fontBold, 18) {
                tab = i; rebuildTabs(tabs)
            }).growX().uniformX().pad(4f)
        }
        buildContent()
    }

    private fun icon(r: Resource) = when (r) {
        Resource.GOLD -> "coin"; Resource.METAL -> "gearSmall"; Resource.ENERGY -> "bolt"; Resource.CHIPS -> "chip"; Resource.TECH -> "star"
    }
    private fun color(r: Resource) = when (r) {
        Resource.GOLD -> Theme.orange; Resource.METAL -> Theme.inkSoft; Resource.ENERGY -> Theme.energyColor; Resource.CHIPS -> Theme.purple; Resource.TECH -> Theme.turquoise
    }

    private fun refreshResources() {
        resources.clear()
        val w = services.save.wallet
        for (r in Resource.entries) {
            val cap = factory.capacity(r)
            val v = w.get(r)
            val t = Table()
            t.background = assets.panel(Theme.white, 16, 3f)
            t.add(ui.image(icon(r), color(r))).size(22f).padRight(4f)
            val txt = if (cap != null) "$v/$cap" else "$v"
            t.add(ui.label(txt, assets.fontSmall, if (cap != null && v >= cap) Theme.red else Theme.ink))
            t.pad(5f, 8f, 5f, 10f)
            resources.add(t).pad(3f).growX()
        }
    }

    private fun costRow(c: Cost): Table {
        val t = Table()
        val w = services.save.wallet
        for ((r, v) in c) {
            t.add(ui.image(icon(r), color(r))).size(22f).padRight(3f)
            t.add(ui.label("$v", assets.fontBold, if (w.get(r) >= v) Theme.ink else Theme.red)).padRight(10f)
        }
        return t
    }

    private fun buildContent() {
        content.clear()
        when (tab) {
            0 -> buildBuildings()
            1 -> buildResearch()
            else -> buildTrade()
        }
        content.add().height(30f)
    }

    private fun production(b: BuildingType): String? {
        val f = factory
        fun r(x: Double) = if (x % 1.0 == 0.0) "${x.toInt()}" else "%.1f".format(x)
        return when (b) {
            BuildingType.ENERGY_PLANT -> "+${r(f.ratePerHour(Resource.ENERGY))} enerji/sa"
            BuildingType.METAL_WORKS -> "+${r(f.ratePerHour(Resource.METAL))} metal/sa · −${r(f.consumptionPerHour(Resource.ENERGY))} enerji/sa"
            BuildingType.CHIP_LAB -> if (f.level(b) > 0) "+${r(f.ratePerHour(Resource.CHIPS))} çip/sa · −${r(f.consumptionPerHour(Resource.METAL))} metal/sa" else null
            BuildingType.RESEARCH_CENTER -> if (f.level(b) > 0) "+${r(f.ratePerHour(Resource.TECH))} TP/sa · araştırma kademesi ${f.level(b)}" else null
            BuildingType.STORAGE -> "Metal/enerji sınırı ${f.capacity(Resource.METAL)} · çevrimdışı ${f.offlineHours} sa"
            BuildingType.COMMAND_CENTER -> "Bina seviye sınırı ${f.level(b) + 2}"
            BuildingType.TRADE_CENTER -> if (f.level(b) > 0) "50 metal → ${f.tradeGoldFor(Resource.METAL)} altın" else null
            else -> null
        }
    }

    private fun buildBuildings() {
        for (b in BuildingType.entries) {
            val f = factory
            val lvl = f.level(b)
            val card = ui.panel(if (b.available) Theme.white else Theme.panelAlt, 24, if (b.available) 4f else 0f)
            card.pad(16f, 18f, 16f, 18f)
            val top = Table()
            top.add(ui.label(b.title, assets.fontBold, if (b.available) Theme.ink else Theme.inkFaint)).left().growX()
            top.add(ui.label(if (lvl > 0) "Sv. $lvl/${b.maxLevel}" else "Kurulmadı", assets.fontSmall, Theme.inkSoft))
            card.add(top).growX().row()
            card.add(ui.label(b.description, assets.fontSmall, Theme.inkSoft, wrap = true)).growX().left().padTop(2f).row()
            production(b)?.let { card.add(ui.label(it, assets.fontSmall, Theme.turquoise, wrap = true)).growX().left().padTop(4f).row() }
            val lock = f.lockReason(b)
            val cost = f.upgradeCost(b)
            val bottom = Table()
            if (cost != null && lock == null) bottom.add(costRow(cost)).left().growX() else
                bottom.add(ui.label(lock ?: "", assets.fontSmall, Theme.inkFaint, wrap = true)).growX().left()
            if (lock == null && cost != null) {
                val btn = ui.button(if (lvl == 0) "Kur" else "Yükselt", if (f.canAfford(cost)) Theme.green else Theme.line,
                    Theme.white, null, assets.fontBold, 18) { doUpgrade(b) }
                btn.pad(8f, 18f, 8f, 18f)
                bottom.add(btn)
            }
            card.add(bottom).growX().padTop(8f)
            content.add(card).growX().pad(6f, 14f, 6f, 14f).row()
        }
    }

    private fun doUpgrade(b: BuildingType) {
        when (val r = factory.upgrade(b)) {
            FactoryResult.Ok -> { assets.play("win", 0.5f); services.persist(); view.pulse(b) }
            is FactoryResult.Missing -> { assets.play("bump", 0.6f); ui.dialog(stage, "Kaynak yetersiz", missingText(r.cost), "Tamam" to {}) }
            is FactoryResult.Locked -> ui.dialog(stage, b.title, r.reason, "Tamam" to {})
        }
        refreshResources(); buildContent()
    }

    private fun missingText(c: Cost): String {
        val w = services.save.wallet
        val lack = c.filter { (r, v) -> w.get(r) < v }.entries.joinToString(", ") { (r, v) -> "${v - w.get(r)} ${r.title.lowercase()}" }
        return "Eksik: $lack. Bölüm tamamlayarak ve fabrika üretimiyle kaynak kazanırsın."
    }

    private fun buildResearch() {
        val f = factory
        content.add(ui.label("Araştırma Merkezi seviyesi araştırma kademesini belirler. Araştırmalar kalıcıdır.", assets.fontSmall, Theme.inkSoft, wrap = true))
            .growX().pad(6f, 22f, 6f, 22f).row()
        for (branch in ResearchBranch.entries) {
            content.add(ui.label(branch.title + if (branch.phase > 5) " (savaş sistemiyle açılacak)" else "", assets.fontBold, Theme.ink))
                .left().pad(12f, 22f, 4f, 22f).row()
            for (r in Research.entries.filter { it.branch == branch }) {
                val done = f.has(r)
                val lock = f.researchLock(r)
                val card = ui.panel(if (done) Theme.mix(Theme.green, Theme.white, 0.8f) else Theme.white, 22, 3f)
                card.pad(12f, 16f, 12f, 16f)
                val info = Table()
                info.add(ui.label("${r.tier}. ${r.title}", assets.fontBold, Theme.ink)).left().row()
                val eff = if (r.effect == Effect.NONE) "Etki savaş sistemiyle gelecek" else
                    "${r.effect.title} +${r.amount}" + if (r.effect == Effect.OFFLINE_HOURS) " saat" else "%"
                info.add(ui.label(eff, assets.fontSmall, Theme.turquoise)).left()
                card.add(info).growX().left()
                when {
                    done -> card.add(ui.image("check", Theme.green)).size(34f)
                    lock != null -> card.add(ui.label(lock, assets.fontSmall, Theme.inkFaint, wrap = true)).width(230f)
                    else -> {
                        val c = f.researchCost(r)
                        card.add(costRow(c)).padRight(6f)
                        card.add(ui.button("Araştır", if (f.canAfford(c)) Theme.purple else Theme.line, Theme.white, null, assets.fontSmall, 18) {
                            when (val res = f.research(r)) {
                                FactoryResult.Ok -> { assets.play("star"); services.persist() }
                                is FactoryResult.Missing -> ui.dialog(stage, "Kaynak yetersiz", missingText(res.cost), "Tamam" to {})
                                is FactoryResult.Locked -> ui.dialog(stage, r.title, res.reason, "Tamam" to {})
                            }
                            refreshResources(); buildContent()
                        }.also { it.pad(6f, 12f, 6f, 12f) })
                    }
                }
                content.add(card).growX().pad(4f, 14f, 4f, 14f).row()
            }
        }
    }

    private fun buildTrade() {
        val f = factory
        if (f.level(BuildingType.TRADE_CENTER) == 0) {
            content.add(ui.label("Ticaret için önce Ticaret Merkezi'ni kur (Komuta Merkezi seviye 3).", assets.font, Theme.inkSoft, wrap = true, align = Align.center))
                .growX().pad(30f).row()
            return
        }
        for (r in listOf(Resource.METAL, Resource.ENERGY)) {
            val gold = f.tradeGoldFor(r)
            val card = ui.panel(Theme.white, 24, 4f)
            card.pad(16f)
            card.add(ui.label("${Factory.TRADE_LOT} ${r.title.lowercase()} → $gold altın", assets.fontBold, Theme.ink)).left().growX()
            for (lots in listOf(1, 5)) {
                card.add(ui.button("×$lots", Theme.orange, Theme.white, null, assets.fontBold, 18) {
                    when (val res = f.trade(r, lots)) {
                        FactoryResult.Ok -> { assets.play("energy"); services.persist() }
                        is FactoryResult.Missing -> ui.dialog(stage, "Kaynak yetersiz", missingText(res.cost), "Tamam" to {})
                        is FactoryResult.Locked -> Unit
                    }
                    refreshResources()
                }.also { it.pad(6f, 14f, 6f, 14f) }).padLeft(8f)
            }
            content.add(card).growX().pad(6f, 14f, 6f, 14f).row()
        }
    }

    override fun update(delta: Float) {
        tick -= delta; saveTick -= delta
        if (tick <= 0f) {
            tick = 1f
            val r = factory.update()
            if (r.gained.isNotEmpty()) refreshResources()
        }
        if (saveTick <= 0f) { saveTick = 15f; services.persist() }
    }

    override fun onPause() { factory.update() }
    override fun onBack() { services.persist(); game.go(MenuScreen(game)) }
    override fun dispose() { view.dispose(); super.dispose() }
}

/** İzometrik fabrika görünümü: her bina sabit bir parselde, seviyesiyle yükselen bir blok. */
class FactoryView(private val assets: Assets, private val factory: () -> Factory) : Widget() {
    private val shapes = ShapeRenderer()
    private var time = 0f
    private var pulse: BuildingType? = null
    private var pulseT = 0f

    private val slots = listOf(
        BuildingType.COMMAND_CENTER to (1 to 1), BuildingType.ENERGY_PLANT to (0 to 0), BuildingType.METAL_WORKS to (2 to 0),
        BuildingType.STORAGE to (0 to 2), BuildingType.CHIP_LAB to (3 to 1), BuildingType.RESEARCH_CENTER to (1 to 3),
        BuildingType.TRADE_CENTER to (3 to 3), BuildingType.ROBOT_PRODUCTION to (2 to 2), BuildingType.GARAGE to (0 to 3),
        BuildingType.DEFENSE to (3 to 0), BuildingType.ACADEMY to (1 to 0), BuildingType.EXPLORATION to (2 to 3),
        BuildingType.REPAIR to (0 to 1), BuildingType.MUSEUM to (3 to 2), BuildingType.AI_LAB to (2 to 1),
    )
    private val colors = mapOf(
        BuildingType.COMMAND_CENTER to Theme.turquoise, BuildingType.ENERGY_PLANT to Theme.energyColor, BuildingType.METAL_WORKS to Theme.inkSoft,
        BuildingType.STORAGE to Theme.orange, BuildingType.CHIP_LAB to Theme.purple, BuildingType.RESEARCH_CENTER to Theme.blue,
        BuildingType.TRADE_CENTER to Theme.green,
    )

    fun pulse(b: BuildingType) { pulse = b; pulseT = 1f }

    override fun act(delta: Float) { super.act(delta); time += delta; pulseT = (pulseT - delta).coerceAtLeast(0f) }

    override fun draw(batch: Batch, parentAlpha: Float) {
        validate()
        val n = 4
        val tw = minOf(width / (n + 0.6f), height / (n / 2f + 1.6f))
        val th = tw / 2
        val ox = x + width / 2; val oy = y + height / 2 + n * th / 2 - tw * 0.15f
        fun sx(gx: Float, gy: Float) = ox + (gx - gy) * tw / 2
        fun sy(gx: Float, gy: Float) = oy - (gx + gy + 1) * th / 2
        batch.end()
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapes.projectionMatrix = batch.projectionMatrix
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        fun diamond(cx: Float, cy: Float, w: Float, h: Float, c: Color) {
            shapes.color = c
            shapes.triangle(cx - w / 2, cy, cx, cy + h / 2, cx + w / 2, cy); shapes.triangle(cx - w / 2, cy, cx, cy - h / 2, cx + w / 2, cy)
        }
        fun block(cx: Float, cy: Float, fw: Float, z: Float, top: Color, l: Color, r: Color) {
            val w = tw * fw; val h = th * fw
            shapes.color = l
            shapes.triangle(cx - w / 2, cy + z, cx, cy - h / 2 + z, cx, cy - h / 2); shapes.triangle(cx - w / 2, cy + z, cx, cy - h / 2, cx - w / 2, cy)
            shapes.color = r
            shapes.triangle(cx, cy - h / 2 + z, cx + w / 2, cy + z, cx + w / 2, cy); shapes.triangle(cx, cy - h / 2 + z, cx + w / 2, cy, cx, cy - h / 2)
            diamond(cx, cy + z, w, h, top)
        }
        for (gy in 0 until n) for (gx in 0 until n) {
            val cx = sx(gx.toFloat(), gy.toFloat()); val cy = sy(gx.toFloat(), gy.toFloat())
            block(cx, cy, 1f, -tw * 0.06f, if ((gx + gy) % 2 == 0) Theme.floorTop else Theme.floorTopAlt, Theme.floorSide, Theme.shade(Theme.floorSide, 0.88f))
        }
        val f = factory()
        for ((b, pos) in slots.sortedBy { it.second.first + it.second.second }) {
            val (gx, gy) = pos
            val cx = sx(gx.toFloat(), gy.toFloat()); val cy = sy(gx.toFloat(), gy.toFloat())
            val lvl = f.level(b)
            if (lvl == 0) {
                diamond(cx, cy, tw * 0.7f, th * 0.7f, Color(0.6f, 0.68f, 0.8f, if (b.available) 0.35f else 0.15f))
                continue
            }
            val base = colors[b] ?: Theme.inkFaint
            val bump = if (pulse == b) sin(pulseT * 12f) * pulseT * tw * 0.06f else 0f
            val z = tw * (0.18f + 0.045f * lvl) + bump
            block(cx, cy, 0.74f, z, Theme.mix(base, Theme.white, 0.3f), base, Theme.shade(base, 0.82f))
            // Çatı detayı: enerji santrali yanıp söner, diğerleri pencere şeridi
            val glow = if (b == BuildingType.ENERGY_PLANT) 0.5f + 0.5f * sin(time * 4f) else 0.2f
            diamond(cx, cy + z, tw * 0.36f, th * 0.36f, Theme.mix(Theme.white, Theme.yellow, glow))
        }
        shapes.end()
        batch.begin()
        val font = assets.fontSmall
        for ((b, pos) in slots) {
            val lvl = f.level(b)
            if (lvl == 0) continue
            val (gx, gy) = pos
            val cx = sx(gx.toFloat(), gy.toFloat()); val cy = sy(gx.toFloat(), gy.toFloat()) + tw * (0.18f + 0.045f * lvl) + th * 0.5f
            font.color = Theme.ink
            font.draw(batch, "$lvl", cx - 6f, cy + 22f)
        }
    }

    fun dispose() = shapes.dispose()

}
