package com.zamaninefendisi.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.zamaninefendisi.game.ZamaninEfendisiGame
import com.zamaninefendisi.game.ui.Theme
import com.zamaninefendisi.logic.levels.LevelCatalog
import com.zamaninefendisi.logic.save.MasterLevel

class MainMenuScreen(game: ZamaninEfendisiGame) : BaseScreen(game) {
    private var exitAsked = false

    init { build() }

    private fun build() {
        root.clearChildren()
        val p = game.progression
        root.top().pad(30f)
        root.add(resourceBar()).padTop(10f).row()
        root.add().height(330f).row()
        root.add(ui.label("Zamanın Efendisi", assets.fontXL, Theme.ink, align = Align.center)).row()
        root.add(ui.label("Üç Çağ", assets.fontL, Theme.orange, align = Align.center)).padBottom(10f).row()
        val lvl = MasterLevel.levelOf(p.data.xp)
        root.add(ui.label("Zaman Ustası Seviye $lvl", assets.fontS, Theme.purple, align = Align.center)).padBottom(26f).row()

        val buttons = Table()
        val inProg = p.data.inProgress?.takeIf { LevelCatalog.level(it.levelId) != null }
        if (inProg != null) {
            val lv = LevelCatalog.level(inProg.levelId)!!
            buttons.add(ui.button("Devam Et · Bölüm ${lv.id}: ${lv.name}", Theme.green) {
                LevelSelectScreen.tryStart(game, root, lv.id, resume = true)
            }).width(560f).height(96f).padBottom(16f).row()
        }
        val next = p.nextPlayable()
        val allDone = LevelCatalog.levels.all { p.isCompleted(it.id) }
        buttons.add(ui.button(if (allDone) "Bölümler" else "Oyna · Bölüm $next", Theme.orange, assets.fontL) {
            if (allDone) game.go(LevelSelectScreen(game)) else LevelSelectScreen.tryStart(game, root, next, resume = false)
        }).width(560f).height(110f).padBottom(16f).row()
        val grid = Table()
        fun cell(text: String, color: com.badlogic.gdx.graphics.Color, f: () -> Unit) =
            grid.add(ui.button(text, color, assets.fontM) { f() }).width(272f).height(88f).pad(8f)
        cell("Bölüm Seçimi", Theme.blue) { game.go(LevelSelectScreen(game)) }
        cell("Zaman Günlüğü", Theme.purple) { game.go(JournalScreen(game)) }
        grid.row()
        cell("Profil", Theme.teal) { game.go(ProfileScreen(game)) }
        cell("Nasıl Oynanır", Theme.green) { game.go(HowToPlayScreen(game)) }
        grid.row()
        cell("Ayarlar", Theme.inkSoft) { game.go(SettingsScreen(game)) }
        cell("Çıkış", Theme.red) { Gdx.app.exit() }
        buttons.add(grid).row()
        root.add(buttons).row()
        root.add().expandY().row()
        root.add(ui.label("Sürüm 0.3 · Aşama 3: Strateji · 7 alan, 35 bölüm", assets.fontS, Theme.muted, align = Align.center)).padBottom(10f)
    }

    override fun drawOverlay() {
        val sr = game.shapes
        sr.projectionMatrix = stage.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        sr.begin(ShapeRenderer.ShapeType.Filled)
        Emblem.draw(sr, stage.width / 2, stage.height - 300f, 110f, time)
        sr.end()
    }

    override fun onBack() {
        if (exitAsked) Gdx.app.exit()
        exitAsked = true
        ui.toast(root, "Çıkmak için tekrar Geri'ye bas")
    }
}
