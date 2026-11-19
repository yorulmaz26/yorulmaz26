package com.kayipadalar.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.kayipadalar.game.KayipAdalarGame
import com.kayipadalar.game.ui.Palette
import com.kayipadalar.logic.meta.ShopItem
import com.kayipadalar.logic.model.Res
import com.kayipadalar.logic.tr

val SHOP_CATEGORIES = listOf("ISLAND_SKIN", "CASTLE_SKIN", "BRIDGE", "UNIT_COSTUME", "ANIMATION", "FRAME", "DECORATION", "BUNDLE", "COLLECTIBLE", "THEME")

class ShopScreen(game: KayipAdalarGame) : BaseScreen(game) {
    private var category = SHOP_CATEGORIES.first()

    override fun build() {
        header(tr("menu.shop"))
        val tabs = Table()
        for ((i, c) in SHOP_CATEGORIES.withIndex()) {
            val tb = TextButton(tr("shopTab.$c"), skin, "tab")
            tb.isChecked = c == category
            tb.addListener(object : ClickListener() { override fun clicked(event: InputEvent?, x: Float, y: Float) { category = c; rebuild() } })
            tabs.add(tb).growX().height(62f).pad(3f).uniformX()
            if (i % 4 == 3) tabs.row()
        }
        root.add(tabs).growX().padLeft(6f).padRight(6f).row()
        root.add(label(tr("shop.$category"), "default", Palette.primaryDown)).padTop(4f).row()
        val list = Table()
        for (item in core.meta.shop.filter { it.category == category }) list.add(itemPanel(item)).growX().pad(5f).row()
        list.add(label(tr("shop.realMoney"), "muted", wrap = true, align = Align.center)).growX().pad(12f)
        root.add(scroll(list)).grow().pad(6f)
    }

    private fun itemPanel(item: ShopItem): Table {
        val p = panel()
        if (item.color.isNotEmpty()) p.add(Image(skin.getRegion("circle")).also { it.color = Color.valueOf(item.color) }).size(56f).padRight(12f)
        val info = Table()
        info.add(label(item.name, "default", wrap = true)).growX().left().row()
        val desc = when (item.kind) {
            "BUNDLE" -> "İçerik: " + item.bundle.entries.joinToString("  ") { "${it.value} ${resName(it.key)}" }
            "COLLECTION" -> "Rastgele, henüz sahip olmadığın bir koleksiyon öğesi."
            else -> if (core.shop.owned(item.id)) (if (core.save.equipped[item.category] == item.id) tr("common.equipped") else "Sahipsin") else "Kozmetik"
        }
        info.add(label(desc, "muted", wrap = true)).growX().left().row()
        info.add(label(tr("common.cost", costText(item.price)) + if (item.reputation > 0) " · ${tr("shop.repReq", item.reputation)}" else "", "small", Palette.accentDown, wrap = true)).growX().left()
        p.add(info).growX()
        val owned = item.kind == "COSMETIC" && core.shop.owned(item.id)
        if (owned) {
            val equipped = core.save.equipped[item.category] == item.id
            p.add(button(if (equipped) tr("common.unequip") else tr("common.equip"), "smallGhost") {
                if (equipped) core.shop.unequip(item.category) else core.shop.equip(item.id)
                core.persist(); rebuild()
            }).size(150f, 76f)
        } else {
            val err = core.shop.canBuy(item)
            p.add(button(tr("common.buy"), "smallAccent", err == null) {
                val (e, got) = core.buy(item.id)
                if (e != null) error(e) else {
                    game.audio.play("coin")
                    toast(got?.let { "Yeni koleksiyon öğesi: ${core.collection.name(it)}" } ?: "${item.name} alındı!")
                    rebuild()
                }
            }).size(150f, 76f)
        }
        return p
    }
}

class InventoryScreen(game: KayipAdalarGame) : BaseScreen(game) {
    override fun build() {
        header(tr("menu.inventory"))
        val list = Table()
        val resPanel = panel()
        resPanel.add(label("Kaynaklar", "default")).left().colspan(2).row()
        for (r in Res.entries) {
            if (r == Res.ENERGY) continue
            resPanel.add(Image(skin.getRegion("circle")).also { it.color = resColor(r) }).size(22f).padRight(8f)
            resPanel.add(label("${resName(r.name)}: ${core.wallet.get(r)}", "small")).left().expandX().row()
        }
        list.add(resPanel).growX().pad(5f).row()
        val owned = core.meta.shop.filter { it.kind == "COSMETIC" && core.shop.owned(it.id) }
        if (owned.isEmpty()) list.add(label("Henüz kozmetik ürünün yok. Marketten alabilirsin.", "muted", wrap = true, align = Align.center)).growX().pad(20f).row()
        for ((cat, items) in owned.groupBy { it.category }) {
            list.add(label(tr("shop.$cat"), "default", Palette.primaryDown)).left().pad(8f).row()
            for (item in items) {
                val p = panel()
                if (item.color.isNotEmpty()) p.add(Image(skin.getRegion("circle")).also { it.color = Color.valueOf(item.color) }).size(44f).padRight(10f)
                p.add(label(item.name, "small")).growX().left()
                val equipped = core.save.equipped[cat] == item.id
                if (cat != "DECORATION") p.add(button(if (equipped) tr("common.unequip") else tr("common.equip"), if (equipped) "smallGhost" else "smallBtn") {
                    if (equipped) core.shop.unequip(cat) else core.shop.equip(item.id)
                    core.persist(); rebuild()
                }).size(150f, 70f)
                else p.add(label("Krallıkta sergileniyor", "muted"))
                list.add(p).growX().pad(4f).row()
            }
        }
        root.add(scroll(list)).grow().pad(6f)
    }
}
