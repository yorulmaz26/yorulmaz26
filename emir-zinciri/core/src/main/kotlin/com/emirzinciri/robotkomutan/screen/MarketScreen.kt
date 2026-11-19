package com.emirzinciri.robotkomutan.screen

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.emirzinciri.robotkomutan.EmirZinciriGame
import com.emirzinciri.robotkomutan.logic.meta.Counter
import com.emirzinciri.robotkomutan.logic.meta.Skin
import com.emirzinciri.robotkomutan.logic.meta.Skins
import com.emirzinciri.robotkomutan.ui.Theme

/**
 * Market: oyun içi altınla robot görünümleri. Gerçek para yoktur; ana oyun ücretsiz tamamlanabilir.
 * (Google Play Billing ileride ayrı bir ödeme sağlayıcısı olarak eklenebilir.)
 */
class MarketScreen(game: EmirZinciriGame) : BaseScreen(game) {
    private val list = Table()
    private val gold = ui.label("", assets.fontBold, Theme.ink)
    private val previews = ArrayList<com.emirzinciri.robotkomutan.render.IsoRenderer>()

    init {
        root.top()
        val head = Table()
        head.add(ui.iconButton("back", size = 72f) { onBack() }).size(72f)
        head.add(ui.label("Market", assets.fontTitle, Theme.ink)).padLeft(16f).left().growX()
        val g = Table(); g.background = assets.panel(Theme.white, 18, 3f)
        g.add(ui.image("coin", Theme.orange)).size(26f).padRight(6f); g.add(gold); g.pad(6f, 12f, 6f, 14f)
        head.add(g)
        root.add(head).growX().pad(16f).row()
        root.add(ui.label("Robot görünümleri yalnızca görseldir; oyun avantajı vermez. Altın bölümlerden, görevlerden ve fabrikadan kazanılır.",
            assets.fontSmall, Theme.inkSoft, wrap = true)).growX().pad(0f, 24f, 8f, 24f).row()
        val scroll = ScrollPane(list, assets.scrollStyle)
        scroll.setScrollingDisabled(true, false)
        root.add(scroll).grow()
        build()
    }

    private fun build() {
        previews.forEach { it.dispose() }; previews.clear()
        list.clear()
        gold.setText("${services.save.wallet.gold}")
        val m = services.save.meta
        var col = 0
        for (skin in Skins.all) {
            val owned = skin.id in m.ownedSkins
            val selected = m.skin == skin.id
            val card = ui.panel(Theme.white, 26, 5f, if (selected) Theme.turquoise else null)
            card.pad(14f)
            card.add(RobotPreview(skin)).size(150f, 130f).row()
            card.add(ui.label(skin.title, assets.fontBold, Theme.ink)).padTop(4f).row()
            val action = when {
                selected -> ui.label("Kullanılıyor", assets.fontSmall, Theme.turquoise)
                owned -> ui.button("Seç", Theme.turquoise, Theme.white, null, assets.fontSmall, 18) { Skins.select(services.save, skin); services.persist(); build() }
                else -> ui.button("${skin.price}", if (services.save.wallet.gold >= skin.price) Theme.green else Theme.line, Theme.white, "coin", assets.fontSmall, 18) { buy(skin) }
            }
            card.add(action).padTop(8f)
            list.add(card).width(320f).pad(8f)
            if (++col % 2 == 0) list.row()
        }
    }

    private fun buy(skin: Skin) {
        if (Skins.buy(services.save, skin)) {
            services.progression.meta.count(Counter.SKINS)
            assets.play("win", 0.6f); services.persist()
        } else {
            assets.play("bump", 0.6f)
            ui.dialog(stage, "Altın yetersiz", "${skin.price - services.save.wallet.gold} altın daha gerekiyor.", "Tamam" to {})
        }
        build()
    }

    /** Görünümün küçük önizlemesi: gerçek robot çizicisiyle. */
    private inner class RobotPreview(private val skin: Skin) : Widget() {
        private val renderer = com.emirzinciri.robotkomutan.render.IsoRenderer(assets)
        private var t = 0f
        private val level = com.emirzinciri.robotkomutan.logic.level.LevelData(com.emirzinciri.robotkomutan.logic.level.LevelDef(
            id = 0, area = 1, name = "", map = listOf("."), robots = listOf(com.emirzinciri.robotkomutan.logic.level.RobotDef("A", 0, 0, com.emirzinciri.robotkomutan.logic.grid.Dir.S)),
            commands = emptyList(), maxCommands = 1, par = 1))
        private val state = com.emirzinciri.robotkomutan.logic.sim.WorldState.initial(level, mapOf("A" to 0))
        init { renderer.primaryColor = Theme.rgb(skin.color); previews += renderer }
        override fun act(delta: Float) { super.act(delta); t += delta }
        override fun draw(batch: Batch, parentAlpha: Float) {
            renderer.fit(level, x, y, width, height)
            renderer.draw(batch, com.emirzinciri.robotkomutan.render.IsoRenderer.Frame(level, state, state, 1f, emptyList(), t, false, null, false), parentAlpha)
        }
    }

    override fun onBack() = game.go(MenuScreen(game))
    override fun dispose() { previews.forEach { it.dispose() }; super.dispose() }
}
