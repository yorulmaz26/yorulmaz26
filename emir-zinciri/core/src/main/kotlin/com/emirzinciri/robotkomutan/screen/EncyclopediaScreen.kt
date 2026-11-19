package com.emirzinciri.robotkomutan.screen

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.emirzinciri.robotkomutan.EmirZinciriGame
import com.emirzinciri.robotkomutan.logic.command.CommandType
import com.emirzinciri.robotkomutan.logic.level.Tile
import com.emirzinciri.robotkomutan.ui.CardActor
import com.emirzinciri.robotkomutan.ui.Theme

/** Komut ansiklopedisi: açılmış kartlar ve harita nesneleri. Henüz açılmamış kartlar gizli gösterilir. */
class EncyclopediaScreen(game: EmirZinciriGame) : BaseScreen(game) {
    init {
        root.top()
        val head = Table()
        head.add(ui.iconButton("back", size = 72f) { onBack() }).size(72f)
        head.add(ui.label("Komut Ansiklopedisi", assets.fontTitle, Theme.ink)).padLeft(16f).left().growX()
        root.add(head).growX().pad(16f).row()
        val list = Table()
        val scroll = ScrollPane(list, assets.scrollStyle)
        scroll.setScrollingDisabled(true, false)
        root.add(scroll).grow()

        val p = services.progression
        val firstUse = HashMap<CommandType, Int>()
        for (d in services.catalog.all()) for (c in d.commands) firstUse.putIfAbsent(c, d.id)
        list.add(ui.label("Kartlar", assets.fontTitle, Theme.ink)).left().pad(8f, 24f, 8f, 24f).row()
        val inGame = CommandType.entries.filter { it in firstUse }
        for (t in inGame) {
            val lvl = firstUse.getValue(t)
            val known = p.isUnlocked(lvl)
            val row = ui.panel(Theme.white, 24, 4f)
            row.pad(14f)
            val card = CardActor(assets, t, compact = true)
            if (!known) card.color.a = 0.35f
            row.add(card).size(82f, 92f).padRight(16f)
            val info = Table()
            info.add(ui.label(if (known) t.title else "???", assets.fontBold, Theme.ink)).left().row()
            info.add(ui.label(if (known) t.description else "Bölüm $lvl'de açılır.", assets.fontSmall, Theme.inkSoft, wrap = true)).growX().left().row()
            if (known) {
                val cost = if (t.energyCost > 0) "${t.energyCost} enerji" else "enerji harcamaz"
                val ticks = if (t.ticks == 0) "adım harcamaz" else "1 adım"
                info.add(ui.label("$cost · $ticks", assets.fontSmall, Theme.category(t.category))).left().padTop(4f)
            }
            row.add(info).growX()
            list.add(row).growX().pad(6f, 16f, 6f, 16f).row()
        }
        val upcoming = CommandType.entries.filter { it !in firstUse }
        if (upcoming.isNotEmpty()) {
            list.add(ui.label("${upcoming.size} kart daha motor tarafından destekleniyor; ileriki alanlarda açılacak.", assets.fontSmall, Theme.inkFaint, wrap = true))
                .growX().pad(8f, 24f, 8f, 24f).row()
        }
        list.add(ui.label("Harita nesneleri", assets.fontTitle, Theme.ink)).left().pad(20f, 24f, 8f, 24f).row()
        val tiles = listOf(
            Tile.EXIT to "Robot buraya ulaşınca hedef tamamlanır.",
            Tile.WALL to "Geçilemez. Çarpan robot yerinde kalır.",
            Tile.VOID to "Boşluğa giren robot düşer ve kaybolur.",
            Tile.PIT to "Robot düşer; ama itilen ya da bırakılan kutu çukuru doldurur.",
            Tile.TARGET to "Kutuların taşınması gereken sarı hedef.",
            Tile.PLATE to "Üzerinde robot veya kutu durdukça bağlı kapıları açık tutar.",
            Tile.SWITCH to "'Butona bas' ile kapıları açar/kapatır.",
            Tile.TIMER to "Basılınca kapılar birkaç adım açık kalır.",
            Tile.DOOR to "Mavi kapı: kanal etkinken açıktır. Üzerinde biri varken kapanmaz.",
            Tile.DOOR_INV to "Mor kapı: tersine çalışır, kanal etkinken kapanır.",
            Tile.CHARGER to "'Enerji topla' robotun enerjisini doldurur.",
            Tile.SOCKET to "'Enerji aktar' ile dolar; dolunca bağlı kapılar kalıcı olarak açılır.",
        )
        for ((tile, desc) in tiles) {
            val row = ui.panel(Theme.white, 22, 3f)
            row.pad(14f, 18f, 14f, 18f)
            row.add(ui.label(tile.title, assets.fontBold, Theme.ink)).left().width(230f)
            row.add(ui.label(desc, assets.fontSmall, Theme.inkSoft, wrap = true)).growX()
            list.add(row).growX().pad(5f, 16f, 5f, 16f).row()
        }
        list.add(ui.label("Düşmanlar", assets.fontTitle, Theme.ink)).left().pad(20f, 24f, 8f, 24f).row()
        val seenEnemies = services.catalog.all().filter { p.isUnlocked(it.id) }.flatMap { d -> d.enemies.map { it.type } }.toSet()
        for (e in com.emirzinciri.robotkomutan.logic.level.EnemyType.entries) {
            val known = e in seenEnemies
            val row = ui.panel(Theme.white, 22, 3f)
            row.pad(14f, 18f, 14f, 18f)
            row.add(ui.label(if (known) e.title else "???", assets.fontBold, Theme.ink)).left().width(230f)
            row.add(ui.label(if (known) "${e.description} (${e.defaultHp} can)" else "Henüz karşılaşılmadı.", assets.fontSmall, Theme.inkSoft, wrap = true)).growX()
            list.add(row).growX().pad(5f, 16f, 5f, 16f).row()
        }
        list.add(ui.label("Robot sınıfları (bulmacalarda standart değerler): Temel 3 can/1 hasar · Savaş 4 can/2 hasar · Kalkan 5 can, saldıramaz, 3 adımlık kalkan.",
            assets.fontSmall, Theme.inkFaint, wrap = true)).growX().pad(10f, 24f, 8f, 24f).row()
        list.add().height(30f)
    }

    override fun onBack() = game.go(MenuScreen(game))
}
