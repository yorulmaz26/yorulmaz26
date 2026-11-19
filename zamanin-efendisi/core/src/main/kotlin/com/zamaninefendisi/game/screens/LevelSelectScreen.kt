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

/** Alan ve bölüm seçimi. Kilitli bölümler önceki bölüm tamamlanınca açılır. */
class LevelSelectScreen(game: ZamaninEfendisiGame) : BaseScreen(game) {

    init {
        root.top().pad(24f)
        root.add(header("Bölümler") { onBack() }).growX().padBottom(14f).row()
        root.add(resourceBar()).padBottom(18f).row()
        val list = Table().top()
        for (area in LevelCatalog.areas) {
            val card = ui.panel(Theme.panel)
            card.pad(26f)
            card.add(ui.label("Alan ${area.id}", assets.fontS, Theme.orange)).left().row()
            card.add(ui.label(area.name, assets.fontL, Theme.ink)).left().row()
            card.add(ui.label("Açılan özellik: ${area.unlocks}", assets.fontS, Theme.purple)).left().padTop(4f).row()
            card.add(ui.label(area.description, assets.fontS, Theme.inkSoft, wrap = true)).width(600f).left().padTop(8f).padBottom(18f).row()
            val row = Table()
            for (lv in area.levels) row.add(levelButton(lv)).size(110f, 130f).pad(4f)
            card.add(row).row()
            val stars = area.levels.sumOf { game.progression.progress(it.id).stars }
            card.add(ui.label("Alan yıldızları: $stars / ${area.levels.size * 3}", assets.fontS, Theme.inkSoft)).padTop(12f)
            list.add(card).width(660f).padBottom(20f).row()
        }
        list.add(ui.label("Yeni alanlar geliştirme aşamalarında eklenecek: Gelecek çağı, zaman enerjisi ve daha fazlası.", assets.fontS, Theme.muted, wrap = true, align = Align.center)).width(600f).padTop(10f)
        root.add(ui.scroll(list).also { it.setScrollingDisabled(true, false) }).grow().top()
    }

    private fun levelButton(lv: LevelDef): Table {
        val p = game.progression
        val unlocked = p.isUnlocked(lv.id)
        val prog = p.progress(lv.id)
        val boss = lv.id % 5 == 0
        val color: Color = when {
            !unlocked -> Theme.grey
            prog.completed -> Theme.green
            boss -> Theme.red
            else -> Theme.orange
        }
        val t = Table()
        val b = ui.button(if (unlocked) "${lv.id}" else "Kilit", color, if (unlocked) assets.fontL else assets.fontS) {
            tryStart(game, root, lv.id, resume = false)
        }
        b.isDisabled = !unlocked
        t.add(b).size(100f, 90f).row()
        val starText = if (prog.completed) "★".repeat(prog.stars) + "☆".repeat(3 - prog.stars) else if (boss) "Patron" else ""
        t.add(ui.label(starText, assets.fontS, Theme.gold, align = Align.center)).height(30f)
        return t
    }

    override fun onBack() = game.go(MainMenuScreen(game))

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
            box.add(ui.label(if (lv.id % 5 == 0) "Alan Patronu · Bölüm ${lv.id}" else "Bölüm ${lv.id}", a.fontS, Theme.orange)).left().row()
            box.add(ui.label(lv.name, a.fontL, Theme.ink)).left().padBottom(10f).row()
            box.add(ui.label(lv.intro, a.fontS, Theme.inkSoft, wrap = true)).width(570f).left().padBottom(16f).row()
            box.add(ui.label("Hedefler", a.fontM, Theme.ink)).left().row()
            for (g in lv.goals) box.add(ui.label("• ${g.text}", a.fontS, Theme.ink, wrap = true)).width(570f).left().row()
            val eras = lv.eras.joinToString(" · ") { it.title }
            box.add(ui.label("Çağlar: $eras   ·   3 yıldız: en fazla ${lv.par} eylem   ·   Tur sınırı: ${lv.maxTurns}", a.fontS, Theme.purple, wrap = true)).width(570f).left().padTop(12f).row()
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
