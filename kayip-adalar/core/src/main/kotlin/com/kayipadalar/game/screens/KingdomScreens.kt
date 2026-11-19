package com.kayipadalar.game.screens

import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.kayipadalar.game.KayipAdalarGame
import com.kayipadalar.game.render.KingdomView
import com.kayipadalar.game.ui.Palette
import com.kayipadalar.logic.model.Res
import com.kayipadalar.logic.model.UnitType
import com.kayipadalar.logic.tr

class KingdomScreen(game: KayipAdalarGame) : BaseScreen(game) {
    private var eventChecked = false

    override fun build() {
        header(tr("kingdom.title"))
        val content = Table()
        content.add(KingdomView(core, game.shapes)).height(380f).growX().row()

        // Üretim
        val prod = panel()
        val now = core.now()
        val pending = core.kingdom.pendingIdle(now)
        val perHour = core.kingdom.productionPerHour().filterValues { it >= 0.5 }
        prod.add(label(tr("kingdom.perHour", perHour.entries.joinToString("  ") { "${it.value.toInt()} ${resName(it.key.name)}" }), "small", wrap = true)).growX().left().row()
        prod.add(label(tr("kingdom.storage", "%.0f".format(core.kingdom.storageHours())), "muted")).left().row()
        val pendText = if (pending.isEmpty()) "—" else pending.entries.joinToString("  ") { "${it.value} ${resName(it.key.name)}" }
        prod.add(label(tr("kingdom.pending", pendText), "small", Palette.success, wrap = true)).growX().left().padTop(4f).row()
        prod.add(button(tr("kingdom.collect"), "success", pending.isNotEmpty()) {
            val got = core.collectIdle()
            game.audio.play("coin")
            toast(got.entries.joinToString("  ") { "+${it.value} ${resName(it.key.name)}" })
            rebuild()
        }).growX().height(80f).padTop(8f)
        content.add(prod).growX().pad(6f).row()

        // Krallık olayı
        core.events.pending()?.let { e -> content.add(eventPanel(e.id)).growX().pad(6f).row() }

        // Liman takası
        if (core.kingdom.level("HARBOR") > 0) content.add(tradePanel()).growX().pad(6f).row()

        content.add(label(tr("kingdom.buildings"), "title")).left().pad(10f, 8f, 4f, 8f).row()
        for (b in core.meta.buildings) {
            val p = panel()
            val lv = core.kingdom.level(b.id)
            val unlocked = core.kingdom.isUnlocked(b.id)
            val info = Table()
            info.add(label("${b.name} · ${tr("common.level", lv)}/${core.kingdom.cap(b.id)}", "default", wrap = true)).growX().left().row()
            info.add(label(b.desc, "muted", wrap = true)).growX().left().row()
            val err = core.kingdom.canUpgrade(b.id)
            val costLine = when {
                !unlocked -> tr("common.unlocksAt", b.unlockLevel + 1)
                lv >= b.maxLevel -> tr("common.max")
                else -> tr("common.cost", costText(core.kingdom.upgradeCost(b.id)))
            }
            info.add(label(costLine, "small", if (err == null) Palette.text else Palette.textMuted, wrap = true)).growX().left()
            p.add(info).growX()
            p.add(button(tr("common.upgrade"), "smallBtn", err == null) {
                val e = core.upgradeBuilding(b.id)
                if (e != null) error(e) else { game.audio.play("unlock"); toast("${b.name} ${tr("common.level", lv + 1)}"); rebuild() }
            }).size(150f, 76f)
            content.add(p).growX().pad(5f).row()
        }
        root.add(scroll(content)).grow().pad(6f).row()

        if (!eventChecked) {
            eventChecked = true
            if (core.events.pending() == null && core.events.maybeTrigger(core.now()) != null) { core.persist(); rebuild() }
        }
    }

    private fun eventPanel(id: String): Table {
        val e = core.meta.events.first { it.id == id }
        val p = panel()
        p.background = theme.tinted("round", com.badlogic.gdx.graphics.Color(1f, 0.95f, 0.82f, 0.96f))
        p.add(label("${tr("kingdom.event")}: ${e.title}", "default", Palette.accentDown)).left().row()
        p.add(label(e.text, "small", wrap = true)).growX().left().padBottom(6f).row()
        for ((i, c) in e.choices.withIndex()) {
            val affordable = core.wallet.canAfford(c.cost)
            p.add(button(c.label, "smallGhost", affordable) {
                val err = core.resolveEvent(i)
                if (err != null) error(err) else {
                    if (c.reward.isNotEmpty()) toast(c.reward.entries.joinToString("  ") { "+${it.value} ${resName(it.key)}" })
                    rebuild()
                }
            }).growX().height(70f).pad(3f).row()
        }
        return p
    }

    private fun tradePanel(): Table {
        val p = panel()
        val rate = core.kingdom.tradeRate()
        p.add(label("${tr("kingdom.trade")} · ${tr("kingdom.tradeRate", "%.2f".format(rate))}", "default")).left().row()
        val pairs = listOf("WOOD" to "STONE", "STONE" to "WOOD", "FOOD" to "GOLD", "WOOD" to "IRON", "GOLD" to "CRYSTAL", "STONE" to "IRON")
        val grid = Table()
        for ((i, pr) in pairs.withIndex()) {
            val amount = if (pr.first == "GOLD") 100 else 50
            grid.add(button("$amount ${resName(pr.first)} → ${(amount * rate * (if (pr.second == "CRYSTAL") 0.1 else 1.0)).toInt()} ${resName(pr.second)}", "smallGhost",
                core.wallet.get(pr.first) >= amount) {
                val err = if (pr.second == "CRYSTAL") {
                    // Kristal değerli olduğu için 10:1 ek çarpan uygulanır.
                    if (core.wallet.get("GOLD") < amount) "error.noResources" else {
                        core.wallet.add("GOLD", -amount.toLong()); core.wallet.add("CRYSTAL", (amount * rate * 0.1).toLong()); core.persist(); null
                    }
                } else core.trade(pr.first, pr.second, amount)
                if (err != null) error(err) else rebuild()
            }).growX().height(70f).pad(3f)
            if (i % 2 == 1) grid.row()
        }
        p.add(grid).growX()
        return p
    }
}

class UnitsScreen(game: KayipAdalarGame) : BaseScreen(game) {
    override fun build() {
        header(tr("menu.units"))
        val list = Table()
        list.add(label("Birlik seviyeleri bölümlerdeki ve krallık savunmasındaki birliklerine saldırı, savunma ve can bonusu verir. Yeni türler Asker Kışlası ile açılır.", "muted", wrap = true)).growX().pad(6f).row()
        for (t in UnitType.entries) {
            if (t == UnitType.BOSS) continue
            val st = core.rules.stats(t)
            val lv = core.units.level(t)
            val unlocked = core.units.unlocked(t)
            val p = panel(alt = !unlocked)
            val info = Table()
            info.add(label("${tr("unit.$t")} · ${tr("common.level", lv)}/${core.units.maxLevel}", "default")).left().row()
            info.add(label(tr("unitDesc.$t"), "muted", wrap = true)).growX().left().row()
            info.add(label("Can ${st.hp + lv} · Saldırı ${st.atk + lv} · Savunma ${st.def + lv / 2} · Menzil ${st.range} · Hareket ${st.moves}", "small", wrap = true)).growX().left().row()
            val line = when {
                !unlocked -> "Kışla seviyesi ${st.unlockBarracks} gerekir"
                lv >= core.units.maxLevel -> tr("common.max")
                else -> tr("common.cost", costText(core.units.upgradeCost(t)))
            }
            info.add(label(line, "small", Palette.textMuted, wrap = true)).growX().left()
            p.add(info).growX()
            p.add(button(tr("common.upgrade"), "smallBtn", unlocked && lv < core.units.maxLevel && core.wallet.canAfford(core.units.upgradeCost(t))) {
                val e = core.upgradeUnit(t)
                if (e != null) error(e) else { game.audio.play("unlock"); rebuild() }
            }).size(150f, 76f)
            list.add(p).growX().pad(5f).row()
        }
        root.add(scroll(list)).grow().pad(6f)
    }
}

class ResearchScreen(game: KayipAdalarGame) : BaseScreen(game) {
    private var branch = "ENGINEERING"

    override fun build() {
        header(tr("menu.research"))
        val tabs = Table()
        val group = ButtonGroup<TextButton>()
        for (b in listOf("ENGINEERING", "MILITARY", "ECONOMY", "MAGIC")) {
            val tb = TextButton(tr("research.$b"), skin, "tab")
            tb.isChecked = b == branch
            tb.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) { branch = b; rebuild() }
            })
            group.add(tb)
            tabs.add(tb).growX().height(70f).pad(3f)
        }
        root.add(tabs).growX().padLeft(8f).padRight(8f).row()
        val list = Table()
        val nodes = core.meta.research.filter { it.branch == branch }.sortedBy { it.tier }
        for (n in nodes) {
            val done = core.research.done(n.id)
            val err = core.research.canResearch(n.id)
            val p = panel(alt = !done && err != null)
            if (done) p.background = theme.tinted("round", com.badlogic.gdx.graphics.Color(0.86f, 0.96f, 0.86f, 0.95f))
            val info = Table()
            val ex = if (n.exclusive != null) " · ${tr("research.exclusive")}" else ""
            info.add(label("Kademe ${n.tier}: ${n.name}$ex", "default", wrap = true)).growX().left().row()
            info.add(label(n.desc, "muted", wrap = true)).growX().left().row()
            val state = when {
                done -> "✓ Tamamlandı"
                err == "error.exclusive" -> "Alternatif seçildi (kilitli)"
                err == "error.prereq" -> "Önce: " + n.requires.joinToString { r -> r.split('|').joinToString(" veya ") { id -> core.meta.researchNode(id).name } }
                else -> tr("common.cost", costText(core.research.cost(n.id)))
            }
            info.add(label(state, "small", if (done) Palette.success else Palette.textMuted, wrap = true)).growX().left()
            p.add(info).growX()
            if (!done) p.add(button("Araştır", "smallBtn", err == null) {
                val e = core.doResearch(n.id)
                if (e != null) error(e) else { game.audio.play("unlock"); toast("${n.name} tamamlandı!"); rebuild() }
            }).size(150f, 76f)
            list.add(p).growX().pad(5f).row()
        }
        list.add(label("Kademe 3'te iki seçenekten yalnızca birini seçebilirsin; seçimin kalıcıdır.", "muted", wrap = true, align = Align.center)).growX().pad(10f)
        root.add(scroll(list)).grow().pad(6f)
    }
}
