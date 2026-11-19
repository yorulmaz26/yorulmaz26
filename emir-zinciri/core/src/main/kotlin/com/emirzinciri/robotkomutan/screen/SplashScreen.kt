package com.emirzinciri.robotkomutan.screen

import com.badlogic.gdx.utils.Align
import com.emirzinciri.robotkomutan.EmirZinciriGame
import com.emirzinciri.robotkomutan.StartupOptions
import com.emirzinciri.robotkomutan.ui.Theme

/** Açılış ekranı: logo ve slogan; ardından ana menü (veya geliştirici seçenekleriyle doğrudan bir ekran). */
class SplashScreen(game: EmirZinciriGame, private val startup: StartupOptions) : BaseScreen(game) {
    init {
        root.add(ui.image("robot", Theme.turquoise)).size(180f).row()
        root.add(ui.label("EMİR ZİNCİRİ", assets.fontHuge, Theme.ink)).padTop(24f).row()
        root.add(ui.label("ROBOT KOMUTAN", assets.fontTitle, Theme.orange)).row()
        root.add(ui.label(EmirZinciriGame.SLOGAN, assets.font, Theme.inkSoft, wrap = true, align = Align.center)).width(560f).padTop(28f)
    }

    override fun update(delta: Float) {
        if (time < (if (startup.openLevel != null || startup.openScreen != null) 0.05f else 1.1f)) return
        val next = when {
            startup.openLevel != null -> services.catalog[startup.openLevel]?.let { GameScreen(game, it, startup) }
            startup.openScreen == "levels" -> LevelSelectScreen(game)
            startup.openScreen == "settings" -> SettingsScreen(game)
            startup.openScreen == "book" -> EncyclopediaScreen(game)
            startup.openScreen == "stats" -> StatsScreen(game)
            startup.openScreen == "factory" -> FactoryScreen(game)
            startup.openScreen == "quests" -> QuestsScreen(game)
            startup.openScreen == "achievements" -> QuestsScreen(game, 2)
            startup.openScreen == "market" -> MarketScreen(game)
            startup.openScreen == "endless" -> EndlessScreen(game)
            startup.openScreen == "endless_play" -> com.emirzinciri.robotkomutan.logic.gen.Generator.generate(9, 42)?.let { GameScreen(game, it.def, startup, PlayMode.ENDLESS) }
            else -> null
        } ?: MenuScreen(game)
        startup.screenshotPath?.let { (next as BaseScreen).scheduleScreenshot(it, startup.screenshotAfter) }
        game.go(next)
    }
}
