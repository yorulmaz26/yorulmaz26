package com.emirzinciri.robotkomutan.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Align
import com.emirzinciri.robotkomutan.EmirZinciriGame
import com.emirzinciri.robotkomutan.logic.progress.LivesSystem
import com.emirzinciri.robotkomutan.ui.Theme

/** Ana menü. Yalnızca gerçekten çalışan bölümlere bağlantı verir. */
class MenuScreen(game: EmirZinciriGame) : BaseScreen(game) {
    private val diorama = Diorama(assets)
    private val livesChip = ui.label("", assets.fontBold, Theme.ink)

    init {
        val save = services.save
        val p = services.progression
        root.top().pad(24f)
        val top = com.badlogic.gdx.scenes.scene2d.ui.Table()
        top.add(ui.chip("star", "${save.totalStars}", Theme.yellow)).padRight(8f)
        top.add(ui.chip("coin", "${save.wallet.gold}", Theme.orange)).padRight(8f)
        val lives = com.badlogic.gdx.scenes.scene2d.ui.Table()
        lives.background = assets.panel(Theme.white, 18, 3f)
        lives.add(ui.image("heart", Theme.red)).size(26f).padRight(6f)
        lives.add(livesChip)
        lives.pad(6f, 12f, 6f, 14f)
        top.add(lives)
        top.add().growX()
        top.add(ui.chip("robot", "Sv. ${p.playerLevel}", Theme.turquoise))
        root.add(top).growX().row()

        root.add(ui.label("EMİR ZİNCİRİ", assets.fontHuge, Theme.ink)).padTop(30f).row()
        root.add(ui.label("ROBOT KOMUTAN", assets.fontTitle, Theme.orange)).row()
        root.add(diorama).grow().minHeight(300f).padTop(6f).row()

        val nextId = services.nextLevelId()
        val allDone = services.catalog.ids.all { p.isCompleted(it) }
        val main = if (allDone) "Bütün bölümler tamam!" else if (nextId == 1 && !p.isCompleted(1)) "Oyuna Başla" else "Devam Et · Bölüm $nextId"
        root.add(ui.button(main, Theme.green, Theme.white, "play", assets.fontTitle, 28) {
            val def = services.catalog[nextId]
            if (def != null && !allDone) game.go(GameScreen(game, def)) else game.go(LevelSelectScreen(game))
        }.also { it.pad(22f) }).width(560f).padTop(18f).row()
        val row0 = com.badlogic.gdx.scenes.scene2d.ui.Table()
        row0.add(ui.button("Bölümler", Theme.turquoise, Theme.white, "map") { game.go(LevelSelectScreen(game)) }).growX().uniformX().padRight(7f)
        row0.add(ui.button("Fabrika", Theme.orange, Theme.white, "gear") { game.go(FactoryScreen(game)) }).growX().uniformX().padLeft(7f)
        root.add(row0).width(560f).padTop(14f).row()
        p.meta.refresh(DateKeys.day(), DateKeys.week())
        val claim = p.meta.claimable
        val rowA = com.badlogic.gdx.scenes.scene2d.ui.Table()
        rowA.add(ui.button("Sonsuz", Theme.purple, Theme.white, "speed") { game.go(EndlessScreen(game)) }).growX().uniformX().padRight(7f)
        rowA.add(ui.button(if (claim > 0) "Görevler ($claim)" else "Görevler", if (claim > 0) Theme.yellow else Theme.white, Theme.ink, "check") {
            game.go(QuestsScreen(game)) }).growX().uniformX().padLeft(7f)
        root.add(rowA).width(560f).padTop(14f).row()
        val row = com.badlogic.gdx.scenes.scene2d.ui.Table()
        row.add(ui.button("Market", Theme.white, Theme.ink, "coin") { game.go(MarketScreen(game)) }).growX().uniformX().padRight(5f)
        row.add(ui.button("Komutlar", Theme.white, Theme.ink, "book") { game.go(EncyclopediaScreen(game)) }).growX().uniformX().padLeft(5f).padRight(5f)
        row.add(ui.iconButton("chart", Theme.white, Theme.ink, 72f) { game.go(StatsScreen(game)) }).size(72f).padLeft(5f).padRight(5f)
        row.add(ui.iconButton("gear", Theme.white, Theme.ink, 72f) { game.go(SettingsScreen(game)) }).size(72f).padLeft(5f)
        root.add(row).width(560f).padTop(14f).row()
        root.add(ui.label(EmirZinciriGame.SLOGAN, assets.fontSmall, Theme.inkFaint, wrap = true, align = Align.center)).width(560f).padTop(24f).padBottom(20f)
        services.loadMessage?.let { msg ->
            services.loadMessage = null
            ui.dialog(stage, "Kayıt kurtarıldı", msg, "Tamam" to {})
        }
        refreshLives()
    }

    private var tick = 0f
    override fun update(delta: Float) {
        tick -= delta
        if (tick <= 0f) { tick = 0.5f; refreshLives() }
    }

    private fun refreshLives() {
        val l = services.progression.lives
        val n = l.lives
        val next = l.msToNext()
        livesChip.setText(if (next != null && n < LivesSystem.MAX_LIVES) "$n · ${LivesSystem.format(next)}" else "$n")
    }

    override fun onBack() {
        ui.dialog(stage, "Çıkış", "Oyundan çıkmak istiyor musun? İlerlemen kaydedildi.", "Kal" to {}, "Çık" to {
            services.persist(); Gdx.app.exit()
        })
    }

    override fun dispose() { diorama.dispose(); super.dispose() }
}
