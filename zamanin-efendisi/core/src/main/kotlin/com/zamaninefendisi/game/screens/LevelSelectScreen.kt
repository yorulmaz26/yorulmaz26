package com.zamaninefendisi.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.zamaninefendisi.game.ZamaninEfendisiGame
import com.zamaninefendisi.game.ui.Theme
import com.zamaninefendisi.game.ui.formatTime
import com.zamaninefendisi.logic.Era
import com.zamaninefendisi.logic.LevelDef
import com.zamaninefendisi.logic.levels.LevelCatalog

/** Dünya haritası: 5 dünya × 20 alan. */
class WorldMapScreen(game: ZamaninEfendisiGame, private val world: Int = (LevelCatalog.areaOfLevel(game.progression.nextPlayable()).id - 1) / 20 + 1) : BaseScreen(game) {
    init {
        root.top().pad(24f)
        root.add(header("Dünya Haritası") { onBack() }).growX().padBottom(10f).row()
        root.add(resourceBar()).padBottom(10f).row()
        val names = listOf("Kırık Vadi", "Deniz, Buz ve Gök", "Çöl ve Ateş", "Paralel Zamanlar", "Zamanın Sonu")
        val tabs = Table()
        for (w in 1..5) tabs.add(ui.button("$w", if (w == world) worldColor(w) else Theme.grey, assets.fontM, if (w == world) Color.WHITE else Theme.ink) { game.go(WorldMapScreen(game, w)) }).size(110f, 70f).pad(4f)
        root.add(tabs).row()
        root.add(ui.label("Dünya $world · ${names[world - 1]}", assets.fontL, Theme.ink)).padTop(6f).padBottom(10f).row()
        val grid = Table()
        val p = game.progression
        for (i in 0 until 20) {
            val a = LevelCatalog.area((world - 1) * 20 + i + 1) ?: continue
            val unlocked = p.isUnlocked(a.firstId)
            val stars = a.ids.sumOf { p.progress(it).stars }
            val done = a.ids.all { p.isCompleted(it) }
            val col = when { !unlocked -> Theme.grey; done -> Theme.green; else -> worldColor(world) }
            val b = ui.button("${a.id}\n${a.name}\n${if (unlocked) "★ $stars/15" else "Kilitli"}", col, assets.fontS) { game.go(LevelSelectScreen(game, a.id)) }
            b.isDisabled = !unlocked
            grid.add(b).size(160f, 150f).pad(4f)
            if (i % 4 == 3) grid.row()
        }
        root.add(ui.scroll(grid)).grow()
    }

    private fun worldColor(w: Int) = listOf(Theme.green, Theme.blue, Theme.purple, Theme.orange, Theme.pink)[w - 1]

    override fun onBack() = game.go(MainMenuScreen(game))
}

/** Alan seçimi: bir alanın 5 bölümü. Kilitli bölümler önceki bölüm tamamlanınca açılır. */
class LevelSelectScreen(game: ZamaninEfendisiGame, areaId: Int = LevelCatalog.areaOfLevel(game.progression.nextPlayable()).id) : BaseScreen(game) {
    private val area = LevelCatalog.area(areaId) ?: LevelCatalog.areas.first()

    init {
        root.top().pad(24f)
        root.add(header("Alan ${area.id}") { onBack() }).growX().padBottom(14f).row()
        root.add(resourceBar()).padBottom(18f).row()
        val card = ui.panel(Theme.panel)
        card.pad(26f)
        card.add(ui.label("Dünya ${area.world} · Alan ${area.id}", assets.fontS, Theme.orange)).left().row()
        card.add(ui.label(area.name, assets.fontL, Theme.ink)).left().row()
        card.add(ui.label("Açılan özellik: ${area.unlocks}", assets.fontS, Theme.purple, wrap = true)).width(600f).left().padTop(4f).row()
        card.add(ui.label(area.description, assets.fontS, Theme.inkSoft, wrap = true)).width(600f).left().padTop(8f).padBottom(18f).row()
        val row = Table()
        for (id in area.ids) row.add(levelButton(id)).size(110f, 130f).pad(4f)
        card.add(row).row()
        val stars = area.ids.sumOf { game.progression.progress(it).stars }
        card.add(ui.label("Alan yıldızları: $stars / 15", assets.fontS, Theme.inkSoft)).padTop(12f)
        root.add(card).width(660f).padBottom(16f).row()
        val nav = Table()
        LevelCatalog.area(area.id - 1)?.let { prev -> nav.add(ui.button("‹ Alan ${prev.id}", Theme.blue, assets.fontS) { game.go(LevelSelectScreen(game, prev.id)) }).size(240f, 76f).padRight(16f) }
        LevelCatalog.area(area.id + 1)?.let { next ->
            val b = ui.button("Alan ${next.id} ›", Theme.blue, assets.fontS) { game.go(LevelSelectScreen(game, next.id)) }
            b.isDisabled = !game.progression.isUnlocked(next.firstId)
            nav.add(b).size(240f, 76f)
        }
        root.add(nav).row()
        root.add(ui.button("Dünya Haritası", Theme.purple, assets.fontS) { game.go(WorldMapScreen(game, area.world)) }).size(300f, 76f).padTop(12f)
    }

    private fun levelButton(id: Int): Table {
        val p = game.progression
        val unlocked = p.isUnlocked(id)
        val prog = p.progress(id)
        val boss = id % 5 == 0
        val color: Color = when {
            !unlocked -> Theme.grey
            prog.completed -> Theme.green
            boss -> Theme.red
            else -> Theme.orange
        }
        val t = Table()
        val b = ui.button(if (unlocked) "$id" else "Kilit", color, if (unlocked) assets.fontM else assets.fontS) {
            tryStart(game, root, id, resume = false)
        }
        b.isDisabled = !unlocked
        t.add(b).size(100f, 90f).row()
        val starText = if (prog.completed) "★".repeat(prog.stars) + "☆".repeat(3 - prog.stars) else if (boss) "Patron" else ""
        t.add(ui.label(starText, assets.fontS, Theme.gold, align = Align.center)).height(30f)
        return t
    }

    override fun onBack() = game.go(WorldMapScreen(game, area.world))

    companion object {
        /** Bölüm tanıtımını gösterir; can ve kilit kurallarını uygular. */
        fun tryStart(game: ZamaninEfendisiGame, root: Table, levelId: Int, resume: Boolean) {
            val ui = game.ui
            val a = game.assets
            val p = game.progression
            val lv = LevelCatalog.level(levelId) ?: return
            if (!p.isUnlocked(levelId)) { ui.toast(root, "Bu bölüm henüz kilitli"); return }
            if (!p.canStart(levelId)) {
                val box = ui.panel(Theme.panel)
                box.pad(30f)
                box.add(ui.label("Canın kalmadı", a.fontL, Theme.red)).row()
                box.add(ui.label("Sonraki can ${formatTime(p.data.lives.regenRemainingMs)} içinde dolacak. Bu sırada tamamladığın bölümleri can harcamadan tekrar oynayabilir, Zaman Günlüğü'ne ve profiline göz atabilirsin.", a.fontS, Theme.inkSoft, wrap = true)).width(560f).pad(16f).row()
                lateinit var m: Table
                box.add(ui.button("Tamam", Theme.blue) { m.remove() }).width(300f).height(80f)
                m = ui.modal(root, box, dismissOnOutside = true)
                return
            }
            val actions = if (resume) p.data.inProgress?.takeIf { it.levelId == levelId }?.actions.orEmpty() else emptyList()
            if (resume && actions.isNotEmpty()) { game.go(GameScreen(game, lv, actions)); return }
            val box = ui.panel(Theme.panel)
            box.pad(30f)
            box.add(ui.label((if (lv.id % 5 == 0) "Patron · " else "") + "Bölüm ${lv.id} · Alan ${lv.area}", a.fontS, Theme.orange)).left().row()
            box.add(ui.label(lv.name, a.fontL, Theme.ink)).left().padBottom(10f).row()
            box.add(ui.label(lv.intro, a.fontS, Theme.inkSoft, wrap = true)).width(570f).left().padBottom(16f).row()
            box.add(ui.label("Hedefler", a.fontM, Theme.ink)).left().row()
            for (g in lv.goals) box.add(ui.label("• ${g.text}", a.fontS, Theme.ink, wrap = true)).width(570f).left().row()
            val eras = lv.eras.joinToString(" · ") { it.title }
            val energy = lv.energy?.let { "   ·   Zaman enerjisi: $it" } ?: ""
            box.add(ui.label("Çağlar: $eras   ·   3 yıldız: en fazla ${lv.par} eylem   ·   Tur sınırı: ${lv.maxTurns}$energy", a.fontS, Theme.purple, wrap = true)).width(570f).left().padTop(12f).row()
            if (p.needsLife(levelId)) box.add(ui.label("Başarısız denemede 1 can harcanır.", a.fontS, Theme.red)).left().padTop(8f).row()
            else box.add(ui.label("Tamamlanmış bölüm: tekrar oynamak can harcamaz.", a.fontS, Theme.green)).left().padTop(8f).row()
            val row = Table()
            lateinit var m: Table
            row.add(ui.button("Kapat", Theme.grey, fontColor = Theme.ink) { m.remove() }).width(220f).height(84f).padRight(16f)
            row.add(ui.button("Başla", Theme.eraColor(Era.PAST), a.fontL) {
                m.remove()
                p.markIntroSeen(lv.id)
                game.go(GameScreen(game, lv, emptyList()))
            }).width(320f).height(84f)
            box.add(row).padTop(22f)
            m = ui.modal(root, box, dismissOnOutside = true)
        }
    }
}
