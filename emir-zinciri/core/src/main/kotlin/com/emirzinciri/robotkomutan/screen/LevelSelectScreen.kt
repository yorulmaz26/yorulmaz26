package com.emirzinciri.robotkomutan.screen

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.emirzinciri.robotkomutan.EmirZinciriGame
import com.emirzinciri.robotkomutan.logic.content.Areas
import com.emirzinciri.robotkomutan.ui.Theme

/** Alan ve bölüm seçimi. Kilitli bölümler açılamaz; içerik olmayan alanlar dürüstçe "geliştiriliyor" olarak gösterilir. */
class LevelSelectScreen(game: EmirZinciriGame, private val focusArea: Int? = null, private var hard: Boolean = false) : BaseScreen(game) {
    private val list = Table()
    private lateinit var scroll: ScrollPane
    private var focusActor: Table? = null

    init {
        root.top()
        val head = Table()
        head.add(ui.iconButton("back", size = 72f) { onBack() }).size(72f)
        head.add(ui.label("Bölümler", assets.fontTitle, Theme.ink)).padLeft(16f).left().growX()
        head.add(ui.chip("star", "${services.save.totalStars}/${services.catalog.count * 3}", Theme.yellow))
        root.add(head).growX().pad(16f).row()
        val hardRow = Table()
        val meta = services.save.meta
        hardRow.add(ui.button(if (hard) "Zor mod: AÇIK" else "Zor mod: kapalı", if (hard) Theme.red else Theme.white, if (hard) Theme.white else Theme.ink, null, assets.fontSmall, 18) {
            game.go(LevelSelectScreen(game, focusArea, !hard)) }.also { it.pad(6f, 14f, 6f, 14f) })
        hardRow.add(ui.label(if (hard) "Tamamlanan bölümleri kart sınırı = par ve çarpmadan bitir (${meta.hardCleared.size} tamam)." else
            "Zor mod: tamamlanan bölümler için alternatif kurallar.", assets.fontSmall, Theme.inkSoft, wrap = true)).growX().padLeft(10f)
        root.add(hardRow).growX().pad(0f, 16f, 8f, 16f).row()
        val all = services.catalog.ids.all { services.progression.isCompleted(it) }
        if (all) {
            root.add(ui.button("Yeni Komuta Çağı · prestij ${meta.prestige}", Theme.purple, Theme.white, "star") {
                ui.dialog(stage, "Yeni Komuta Çağı", "Bütün bölümleri tamamladın! Yeni çağa geçince ilerlemen korunur; zor mod ödülleri çağ başına artar ve zor mod listesi sıfırlanır.",
                    "Vazgeç" to {}, "Çağa geç" to {
                        meta.prestige++; meta.hardCleared.clear(); services.progression.meta.count(com.emirzinciri.robotkomutan.logic.meta.Counter.PRESTIGE)
                        services.persist(); game.go(LevelSelectScreen(game, focusArea, true))
                    }, color = Theme.purple)
            }).growX().pad(0f, 16f, 8f, 16f).row()
        }
        scroll = ScrollPane(list, assets.scrollStyle)
        scroll.setScrollingDisabled(true, false)
        root.add(scroll).grow()
        build()
    }

    private fun build() {
        val p = services.progression
        val catalog = services.catalog
        val areasWithContent = catalog.all().map { it.area }.distinct().sorted()
        for (areaId in areasWithContent) {
            val area = Areas.of(areaId)
            val levels = catalog.ofArea(areaId)
            val unlocked = p.isUnlocked(area.firstLevel)
            val color = Theme.rgb(area.themeColor)
            val card = ui.panel(Theme.white, 28, 6f)
            card.pad(20f)
            val header = Table()
            val badge = Table()
            badge.background = assets.panel(if (unlocked) color else Theme.line, 20, 0f)
            badge.add(ui.label("${area.id}", assets.fontTitle, Theme.white)).pad(4f, 14f, 4f, 14f)
            header.add(badge).size(70f, 64f).padRight(14f)
            val titles = Table()
            titles.add(ui.label(area.name, assets.fontTitle, if (unlocked) Theme.ink else Theme.inkFaint)).left().row()
            titles.add(ui.label("Yeni: ${area.mechanic} · Bölüm ${area.firstLevel}–${area.lastLevel}", assets.fontSmall, Theme.inkSoft)).left()
            header.add(titles).growX().left()
            val stars = levels.sumOf { services.save.stars(it.id) }
            header.add(ui.chip("star", "$stars/${levels.size * 3}", Theme.yellow, Theme.panelAlt))
            card.add(header).growX().row()
            val row = Table()
            for (d in levels) {
                val open = p.isUnlocked(d.id)
                val s = services.save.stars(d.id)
                val b = Table()
                b.background = assets.panel(if (!open) Theme.panelAlt else if (s > 0) Theme.mix(color, Theme.white, 0.82f) else Theme.white, 22, 4f,
                    if (open && s == 0) color else Theme.line)
                if (open) {
                    b.add(ui.label("${d.id}", assets.fontTitle, Theme.ink)).row()
                    b.add(ui.stars(s, 22f)).padTop(2f)
                } else {
                    b.add(ui.image("lock", Theme.inkFaint)).size(34f)
                }
                val hardDone = d.id in services.save.meta.hardCleared
                if (hard && hardDone) b.background = assets.panel(Theme.mix(Theme.red, Theme.white, 0.75f), 22, 4f, Theme.red)
                if (open) ui.onTap(b) {
                    if (!hard) game.go(GameScreen(game, d))
                    else if (s == 0) ui.dialog(stage, "Zor mod", "Zor mod yalnızca tamamlanan bölümlerde açılır.", "Tamam" to {})
                    else game.go(GameScreen(game, d.copy(maxCommands = d.par, third = com.emirzinciri.robotkomutan.logic.level.StarRule(com.emirzinciri.robotkomutan.logic.level.StarRuleType.NO_BUMPS)), mode = PlayMode.HARD))
                }
                row.add(b).size(112f, 112f).pad(6f)
            }
            card.add(row).padTop(14f).row()
            list.add(card).growX().pad(10f, 16f, 10f, 16f).row()
            if (areaId == focusArea) focusActor = card
        }
        val next = (areasWithContent.maxOrNull() ?: 0) + 1
        if (next <= Areas.COUNT) {
            val soon = ui.panel(Theme.panelAlt, 28, 0f, Theme.line)
            soon.pad(22f)
            val a = Areas.of(next)
            soon.add(ui.label("Sıradaki: Alan ${a.id} · ${a.name}", assets.fontBold, Theme.inkSoft)).row()
            soon.add(ui.label("Bu alan ve sonrası henüz geliştirme aşamasında (toplam ${Areas.COUNT} alan planlandı). " +
                "Şu an ${services.catalog.count} bölüm oynanabilir.", assets.fontSmall, Theme.inkFaint, wrap = true, align = Align.center)).growX().padTop(6f)
            list.add(soon).growX().pad(10f, 16f, 30f, 16f).row()
        }
    }


    override fun show() {
        super.show()
        focusActor?.let { a ->
            stage.act(0f); scroll.layout()
            scroll.scrollTo(0f, a.y, a.width, a.height, false, true)
            scroll.updateVisualScroll()
        }
    }

    override fun onBack() = game.go(MenuScreen(game))

}
