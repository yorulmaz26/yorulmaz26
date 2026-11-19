package com.yanlisrenk.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.yanlisrenk.game.YanlisRenkGame
import com.yanlisrenk.game.ui.Pal
import com.yanlisrenk.game.ui.Ui
import com.yanlisrenk.logic.PurchaseResult
import com.yanlisrenk.logic.shop.Currency
import com.yanlisrenk.logic.shop.ShopCatalog
import com.yanlisrenk.logic.shop.ShopCategory
import com.yanlisrenk.logic.shop.ShopItem

/** Ürün kartı oluşturmak ve satın alma/kuşanma akışı için ortak mantık. */
abstract class ShopBase(game: YanlisRenkGame) : BaseScreen(game) {

    fun preview(item: ShopItem, size: Float): Actor = when (item.category) {
        ShopCategory.CHARACTER -> CharacterActor(game, item, 0, (item.id.hashCode() and 3))
        ShopCategory.COSTUME -> CharacterActor(game, session.equipped(ShopCategory.CHARACTER), item.style, 1)
        else -> {
            val t = Table()
            t.background = ui.pill(Pal.hex(0xEAF6FF))
            t.add(ui.icon(when (item.category) {
                ShopCategory.COLOR_FX -> "palette"; ShopCategory.GATE_FX -> "sparkle"; ShopCategory.TRAIL -> "trail"
                ShopCategory.ANIMATION -> "magic"; ShopCategory.BOOST -> listOf("shield", "infinity", "coin")[item.style % 3]
                ShopCategory.LIFE -> "heart_full"; ShopCategory.THEME -> "map"; ShopCategory.COLLECTIBLE -> "box"; else -> "gift"
            }, size * 0.6f)).size(size * 0.6f)
            t
        }
    }

    fun priceText(item: ShopItem) = if (item.price == 0) "Ücretsiz" else item.price.toString()

    fun actionButton(item: ShopItem, onChange: () -> Unit): Actor {
        val owned = session.owns(item.id)
        val equipable = item.category in ShopCatalog.equipCategories
        return when {
            item.consumable -> ui.button(priceText(item), if (item.currency == Currency.GOLD) Pal.ORANGE else Pal.TURQ, if (item.currency == Currency.GOLD) "coin" else "diamond", Ui.Size.S) { buy(item, onChange) }
            owned && equipable && session.equipped(item.category).id == item.id -> ui.button("Seçili", Pal.GREY, "check", Ui.Size.S, Pal.TEXT) {}
            owned && equipable -> ui.button("Seç", Pal.GREEN, null, Ui.Size.S) { session.equip(item); game.audio.play("unlock"); onChange() }
            owned -> ui.button("Sende", Pal.GREY, "check", Ui.Size.S, Pal.TEXT) {}
            item.rewardOnly -> ui.button("Başarı", Pal.GREY, "lock", Ui.Size.S, Pal.TEXT) { ui.toast(stage, "500. bölümü tamamlayınca açılır", "trophy") }
            item.requiredLevel > session.data.playerLevel -> ui.button("Sv. ${item.requiredLevel}", Pal.GREY, "lock", Ui.Size.S, Pal.TEXT) { ui.toast(stage, "Seviye ${item.requiredLevel} gerekli", "lock") }
            else -> ui.button(priceText(item), if (item.currency == Currency.GOLD) Pal.ORANGE else Pal.TURQ, if (item.currency == Currency.GOLD) "coin" else "diamond", Ui.Size.S) { buy(item, onChange) }
        }
    }

    private fun buy(item: ShopItem, onChange: () -> Unit) {
        val title = item.name
        ui.confirm(stage, title, "${item.description}\n\nFiyat: ${item.price} ${item.currency.tr}", "Satın Al", yesColor = Pal.GREEN) {
            when (session.buy(item)) {
                PurchaseResult.OK -> {
                    game.audio.play("purchase")
                    ui.toast(stage, "$title alındı!", "check")
                    if (item.category in ShopCatalog.equipCategories) session.equip(item)
                    refreshTopBar(); onChange(); flushAchievementToasts()
                }
                PurchaseResult.NOT_ENOUGH -> ui.toast(stage, "Yeterli ${item.currency.tr.lowercase()} yok", if (item.currency == Currency.GOLD) "coin" else "diamond", Pal.RED)
                PurchaseResult.LOCKED_LEVEL -> ui.toast(stage, "Seviye ${item.requiredLevel} gerekli", "lock")
                else -> ui.toast(stage, "Bu ürün satın alınamaz", "info")
            }
        }
    }

    fun itemCard(item: ShopItem, onChange: () -> Unit, width: Float = 320f): Table {
        val c = ui.card(Pal.WHITE, 18f)
        c.add(preview(item, 150f)).size(170f, 150f).row()
        c.add(ui.label(item.name, Ui.Size.M, Pal.TEXT, wrap = true)).width(width - 40f).row()
        c.add(ui.label(item.description, Ui.Size.S, Pal.TEXT_SOFT, wrap = true)).width(width - 40f).height(64f).row()
        if (item.consumable) c.add(ui.label("Sende: ${session.count(item.id)}", Ui.Size.S, Pal.BLUE)).row()
        c.add(actionButton(item, onChange)).width(width - 60f).padTop(6f)
        return c
    }

    fun grid(items: List<ShopItem>, onChange: () -> Unit): Table {
        val g = Table()
        items.forEachIndexed { i, it ->
            g.add(itemCard(it, onChange)).width(320f).pad(8f).top()
            if (i % 2 == 1) g.row()
        }
        return g
    }
}

/** 10. Market (10 kategori) */
class ShopScreen(game: YanlisRenkGame, private var category: ShopCategory = ShopCategory.CHARACTER) : ShopBase(game) {
    override val recreate: (() -> BaseScreen) = { ShopScreen(game, category) }
    private val content = Table()
    override fun build() {
        root.top()
        root.add(topBar("Market")).growX().row()
        val tabs = Table()
        for (cat in ShopCategory.entries) {
            val sel = cat == category
            val b = ui.button("", if (sel) Pal.ORANGE else Pal.WHITE, cat.icon, Ui.Size.S) { category = cat; fill() }
            tabs.add(b).size(110f, 100f).pad(4f)
        }
        val tsp = ui.scroll(tabs).also { it.setScrollingDisabled(false, true) }
        root.add(tsp).height(120f).width(700f).row()
        root.add(content).grow()
        fill()
    }
    private fun fill() {
        content.clear()
        content.add(ui.label(category.title, Ui.Size.L)).padBottom(6f).row()
        if (category == ShopCategory.LIFE) {
            content.add(ui.label("Canlar: ${session.lives.lives}/4. Can destekleri görevlerden ve günlük ödüllerden de kazanılır.", Ui.Size.S, Pal.TEXT_SOFT, wrap = true)).width(640f).padBottom(6f).row()
            content.add(ui.button("Sahip olduklarını kullan", Pal.PINK, "heart_full", Ui.Size.S, Pal.TEXT) { game.go({ LivesScreen(game) }) }).padBottom(6f).row()
        }
        content.add(ui.scroll(grid(ShopCatalog.inCategory(category)) { refreshTopBar(); fill() })).grow()
        // sekmeleri güncelle
        root.clear(); root.top(); root.add(topBar("Market")).growX().row()
        val tabs = Table()
        for (cat in ShopCategory.entries) {
            val sel = cat == category
            tabs.add(ui.button("", if (sel) Pal.ORANGE else Pal.WHITE, cat.icon, Ui.Size.S) { category = cat; fill() }).size(110f, 100f).pad(4f)
        }
        root.add(ui.scroll(tabs).also { it.setScrollingDisabled(false, true) }).height(120f).width(700f).row()
        root.add(content).grow()
    }
}

/** 11. Karakterler */
class CharactersScreen(game: YanlisRenkGame) : ShopBase(game) {
    override val recreate: (() -> BaseScreen) = { CharactersScreen(game) }
    override fun build() {
        root.top()
        root.add(topBar("Karakterler")).growX().row()
        val eq = session.equipped(ShopCategory.CHARACTER)
        val head = ui.card(pad = 20f)
        head.add(CharacterActor(game, eq, session.equipped(ShopCategory.COSTUME).style, 0, cycleColors = true)).size(240f, 240f).padRight(16f)
        val tx = Table()
        tx.add(ui.label(eq.name, Ui.Size.L, Pal.TEXT, Align.left)).left().row()
        tx.add(ui.label(eq.description, Ui.Size.S, Pal.TEXT_SOFT, Align.left, true)).width(320f).left().row()
        tx.add(ui.label("${session.data.owned.count { it.startsWith("char_") }}/30 karakter", Ui.Size.S, Pal.BLUE, Align.left)).left().padTop(8f).row()
        tx.add(ui.button("Kostümler", Pal.PURPLE, "costume", Ui.Size.S) { game.go({ CostumesScreen(game) }) }).left().padTop(10f)
        head.add(tx)
        root.add(head).width(680f).padBottom(10f).row()
        root.add(ui.scroll(grid(ShopCatalog.characters) { game.go({ CharactersScreen(game) }, remember = false) })).grow()
    }
}

/** 12. Kostümler */
class CostumesScreen(game: YanlisRenkGame) : ShopBase(game) {
    override val recreate: (() -> BaseScreen) = { CostumesScreen(game) }
    override fun build() {
        root.top()
        root.add(topBar("Kostümler")).growX().row()
        root.add(ui.label("Kostümler görünümü değiştirir, oyun dengesini değiştirmez.", Ui.Size.S, Pal.TEXT_SOFT)).padBottom(8f).row()
        root.add(ui.scroll(grid(ShopCatalog.costumes) { game.go({ CostumesScreen(game) }, remember = false) })).grow()
    }
}

@Suppress("unused") private val unusedColor = Color.WHITE
