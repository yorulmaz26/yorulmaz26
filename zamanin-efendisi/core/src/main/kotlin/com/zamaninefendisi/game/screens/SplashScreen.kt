package com.zamaninefendisi.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.Align
import com.zamaninefendisi.game.ZamaninEfendisiGame
import com.zamaninefendisi.game.ui.Theme
import com.zamaninefendisi.logic.Era

/** Açılış ekranı: logo ve kısa yükleme animasyonu. */
class SplashScreen(game: ZamaninEfendisiGame) : BaseScreen(game) {
    private var done = false

    init {
        root.add(ui.label("Zamanın Efendisi", assets.fontXL, Theme.ink, align = Align.center)).padTop(560f).row()
        root.add(ui.label("Üç Çağ", assets.fontL, Theme.orange, align = Align.center)).padTop(6f).row()
        root.add(ui.label("Geçmişi değiştir, bugünü yönet, geleceği fethet.", assets.fontS, Theme.inkSoft, align = Align.center)).padTop(18f).row()
        root.add().expandY()
        game.progression.lastProblem()?.let { root.add(ui.label(it, assets.fontS, Theme.red, wrap = true, align = Align.center)).width(600f).padBottom(30f).row() }
    }

    override fun drawOverlay() {
        val sr = game.shapes
        sr.projectionMatrix = stage.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        sr.begin(ShapeRenderer.ShapeType.Filled)
        Emblem.draw(sr, stage.width / 2, stage.height / 2 + 240f, 120f, time)
        val prog = (time / 1.3f).coerceAtMost(1f)
        sr.color = Theme.creamDeep; sr.rect(160f, 300f, 400f, 14f)
        for ((i, e) in Era.entries.withIndex()) {
            val a = (prog * 3f - i).coerceIn(0f, 1f)
            sr.color = Theme.eraColor(e); sr.rect(160f + i * 400f / 3, 300f, 400f / 3 * a, 14f)
        }
        sr.end()
        if (!done && time > 1.4f) { done = true; game.go(DevStart.screen(game) ?: MainMenuScreen(game)) }
    }
}

/** Kum saati ve üç çağ halkasından oluşan amblem. */
object Emblem {
    fun draw(sr: ShapeRenderer, x: Float, y: Float, r: Float, t: Float) {
        val rot = t * 30f
        for ((i, e) in Era.entries.withIndex()) {
            sr.color = Theme.eraColor(e)
            sr.arc(x, y, r, rot + i * 120f, 110f, 24)
        }
        sr.color = Theme.panel; sr.circle(x, y, r * 0.78f, 40)
        sr.color = Theme.darker(Theme.gold, 0.7f)
        sr.rect(x - r * 0.42f, y + r * 0.48f, r * 0.84f, r * 0.1f)
        sr.rect(x - r * 0.42f, y - r * 0.58f, r * 0.84f, r * 0.1f)
        val sand = (t * 0.4f) % 1f
        sr.color = Theme.gold
        sr.triangle(x - r * 0.34f, y + r * 0.46f, x + r * 0.34f, y + r * 0.46f, x, y + r * 0.02f)
        sr.triangle(x - r * 0.34f, y - r * 0.46f, x + r * 0.34f, y - r * 0.46f, x, y - r * 0.02f)
        sr.color = Theme.panelWarm
        val f = 1f - sand
        sr.triangle(x - r * 0.34f * f, y + r * 0.02f + r * 0.44f * f, x + r * 0.34f * f, y + r * 0.02f + r * 0.44f * f, x, y + r * 0.02f)
        sr.color = Theme.orange
        sr.rectLine(x, y + r * 0.02f, x, y - r * 0.4f, 3f)
    }
}

/** Masaüstü geliştirme kısayolları: ekran görüntüsü ve hızlı test için. */
object DevStart {
    fun screen(game: ZamaninEfendisiGame): com.badlogic.gdx.Screen? {
        val spec = game.devStart ?: return null
        val parts = spec.split(":")
        return when (parts[0]) {
            "game" -> {
                val lv = com.zamaninefendisi.logic.levels.LevelCatalog.level(parts.getOrNull(1)?.toIntOrNull() ?: 1) ?: return null
                GameScreen(game, lv, lv.solution.take(parts.getOrNull(2)?.toIntOrNull() ?: 0))
            }
            "play" -> {
                val lv = com.zamaninefendisi.logic.levels.LevelCatalog.level(parts.getOrNull(1)?.toIntOrNull() ?: 1) ?: return null
                GameScreen(game, lv, emptyList(), autoplay = lv.solution)
            }
            "levels" -> LevelSelectScreen(game)
            "castle" -> CastleScreen(game)
            "research" -> ResearchScreen(game)
            "market" -> MarketScreen(game, com.zamaninefendisi.logic.meta.ShopCategory.entries[parts.getOrNull(1)?.toIntOrNull() ?: 0])
            "inventory" -> InventoryScreen(game)
            "challenges" -> ChallengesScreen(game)
            "quests" -> QuestsScreen(game)
            "expeditions" -> ExpeditionsScreen(game)
            "collection" -> CollectionScreen(game)
            "museum" -> MuseumScreen(game)
            "achievements" -> AchievementsScreen(game)
            "endless" -> { val cm = game.progression.continuity; GameScreen(game, cm.endlessLevel(cm.startEndless()), emptyList(), mode = PlayMode.Endless) }
            "rich" -> { game.progression.update { d -> d.copy(wallet = com.zamaninefendisi.logic.meta.Resource.entries.associateWith { 5000 }) }; CastleScreen(game) }
            "journal" -> JournalScreen(game)
            "profile" -> ProfileScreen(game)
            "howto" -> HowToPlayScreen(game)
            "settings" -> SettingsScreen(game)
            else -> MainMenuScreen(game)
        }
    }
}
