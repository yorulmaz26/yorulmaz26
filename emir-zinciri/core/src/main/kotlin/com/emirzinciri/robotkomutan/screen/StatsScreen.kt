package com.emirzinciri.robotkomutan.screen

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.emirzinciri.robotkomutan.EmirZinciriGame
import com.emirzinciri.robotkomutan.ui.Theme

/** Oyuncu profili ve istatistikler (kayıttan okunur). */
class StatsScreen(game: EmirZinciriGame) : BaseScreen(game) {
    init {
        root.top()
        val head = Table()
        head.add(ui.iconButton("back", size = 72f) { onBack() }).size(72f)
        head.add(ui.label("Profil", assets.fontTitle, Theme.ink)).padLeft(16f).left().growX()
        root.add(head).growX().pad(16f).row()
        val list = Table()
        val scroll = ScrollPane(list, assets.scrollStyle)
        scroll.setScrollingDisabled(true, false)
        root.add(scroll).grow()

        val s = services.save
        val p = services.progression
        val card = ui.panel(Theme.white, 28, 6f)
        card.pad(24f)
        card.add(ui.image("robot", Theme.turquoise)).size(96f).padRight(18f)
        val who = Table()
        who.add(ui.label(s.profileName, assets.fontTitle, Theme.ink)).left().row()
        who.add(ui.label("Komutan seviyesi ${p.playerLevel} · ${s.xp} XP", assets.font, Theme.inkSoft)).left().row()
        // Seviye ilerleme çubuğu
        var need = 100L; var left = s.xp
        while (left >= need) { left -= need; need += 40 }
        val bar = Table()
        bar.background = assets.panel(Theme.panelAlt, 10, 0f)
        val fill = Table(); fill.background = assets.panel(Theme.turquoise, 10, 0f)
        bar.add(fill).width(320f * left / need).height(18f).left().expandX()
        who.add(bar).width(320f).height(18f).left().padTop(8f)
        card.add(who).growX().left()
        list.add(card).growX().pad(8f, 16f, 8f, 16f).row()

        val completed = services.catalog.ids.count { p.isCompleted(it) }
        val three = services.catalog.ids.count { s.stars(it) == 3 }
        val rows = listOf(
            "Tamamlanan bölüm" to "$completed / ${services.catalog.count}",
            "Toplam yıldız" to "${s.totalStars} / ${services.catalog.count * 3}",
            "Üç yıldızlı bölüm" to "$three",
            "Simülasyon denemesi" to "${s.stats.runs}",
            "Başarılı / başarısız" to "${s.stats.wins} / ${s.stats.fails}",
            "Harcanan can" to "${s.stats.livesLost}",
            "Yerleştirilen kart" to "${s.stats.cardsPlaced}",
            "Simüle edilen adım" to "${s.stats.ticksSimulated}",
            "Altın" to "${s.wallet.gold}",
            "Metal" to "${s.wallet.metal}",
            "Mikroçip" to "${s.wallet.chips}",
        )
        val table = ui.panel(Theme.white, 26, 4f)
        table.pad(10f, 22f, 10f, 22f)
        rows.forEachIndexed { i, (k, v) ->
            table.add(ui.label(k, assets.font, Theme.inkSoft)).left().growX().pad(10f, 0f, 10f, 0f)
            table.add(ui.label(v, assets.fontBold, Theme.ink)).right().row()
        }
        list.add(table).growX().pad(8f, 16f, 8f, 16f).row()
        list.add(ui.label("Kaynakları Robot Fabrikası'nda bina kurmak, araştırma ve ticaret için kullanabilirsin.",
            assets.fontSmall, Theme.inkFaint, wrap = true)).growX().pad(8f, 24f, 24f, 24f)
    }

    override fun onBack() = game.go(MenuScreen(game))
}
