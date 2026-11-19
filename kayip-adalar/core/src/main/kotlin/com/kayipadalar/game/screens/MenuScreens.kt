package com.kayipadalar.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.kayipadalar.game.KayipAdalarGame
import com.kayipadalar.game.render.IslandArt
import com.kayipadalar.game.ui.Palette
import com.kayipadalar.logic.PlayBlock
import com.kayipadalar.logic.level.AreaCatalog
import com.kayipadalar.logic.model.IslandType
import com.kayipadalar.logic.tr
import kotlin.math.sin

/** Yüzen adalardan oluşan küçük dekoratif sahne (açılış ve ana menü). */
class FloatingIslands(private val shapes: ShapeRenderer) : Actor() {
    private var t = 0f
    override fun act(delta: Float) { super.act(delta); t += delta }
    override fun draw(batch: Batch, parentAlpha: Float) {
        batch.end()
        shapes.projectionMatrix = batch.projectionMatrix
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        val cx = x + width / 2; val cy = y + height / 2
        val list = listOf(
            Triple(IslandType.FOREST, -190f, -30f), Triple(IslandType.CRYSTAL, 190f, -10f),
            Triple(IslandType.WATER, -110f, -110f), Triple(IslandType.FIRE, 120f, -120f), Triple(IslandType.CASTLE, 0f, 0f),
        )
        for ((i, e) in list.withIndex()) {
            val (type, dx, dy) = e
            val big = type == IslandType.CASTLE
            val tw = if (big) 230f else 130f
            val px = cx + dx; val py = cy + dy + sin(t * 1.4f + i) * 8f
            if (!big) {
                shapes.color = Color.valueOf("C49A6C")
                shapes.rectLine(px, py, cx, cy + sin(t * 1.4f + 4) * 8f, 7f)
            }
        }
        for ((i, e) in list.withIndex()) {
            val (type, dx, dy) = e
            val big = type == IslandType.CASTLE
            val tw = if (big) 230f else 130f
            val px = cx + dx; val py = cy + dy + sin(t * 1.4f + i) * 8f
            IslandArt.body(shapes, px, py, tw, tw / 2, IslandArt.topColors[type]!!)
            IslandArt.decor(shapes, type, px, py, tw, tw / 2, t, null, false)
        }
        shapes.end()
        batch.begin()
    }
}

class SplashScreen(game: KayipAdalarGame) : BaseScreen(game) {
    private var elapsed = 0f
    private var done = false

    override fun build() {
        root.center()
        root.add(label(tr("app.title"), "huge", Palette.primaryDown, align = Align.center)).row()
        root.add(label(tr("app.subtitle"), "title", Palette.accentDown, align = Align.center)).padBottom(30f).row()
        root.add(FloatingIslands(game.shapes)).size(640f, 420f).row()
        root.add(label("Dokun ve başla", "muted")).padTop(30f)
        root.color.a = 0f
        root.addAction(Actions.fadeIn(0.5f))
        root.addListener(object : ClickListener() { override fun clicked(event: InputEvent?, x: Float, y: Float) { finish() } })
        root.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
    }

    override fun update(dt: Float) {
        elapsed += dt
        if (elapsed > 2.2f) finish()
    }

    private fun finish() {
        if (done) return
        done = true
        game.resetTo(MainMenuScreen(game))
    }

    override fun onBack() = finish()
}

class MainMenuScreen(game: KayipAdalarGame) : BaseScreen(game) {
    private var livesLabel = label("", "small")
    private var shownLoadMessage = false

    override fun build() {
        root.top()
        root.add(label(tr("app.title"), "huge", Palette.primaryDown, align = Align.center)).padTop(40f).row()
        root.add(label(tr("app.subtitle"), "title", Palette.accentDown, align = Align.center)).row()
        val rep = core.reputation
        root.add(label("${core.save.playerName} · ${rep.title()} · ★ ${core.totalStars()}", "small", Palette.textMuted)).padTop(4f).row()
        root.add(resourceRow()).growX().pad(10f, 16f, 6f, 16f).row()
        root.add(livesLabel).padBottom(6f).row()
        root.add(FloatingIslands(game.shapes)).size(560f, 240f).row()

        val next = core.save.highestUnlocked.coerceAtMost(AreaCatalog.LEVELS)
        val all = core.save.highestUnlocked > AreaCatalog.LEVELS
        val play = button(if (all) tr("menu.worldmap") else tr("menu.continue", next), "accent") {
            if (all) game.goTo(WorldMapScreen(game)) else openLevel(next)
        }
        root.add(play).width(560f).height(110f).padTop(6f).row()
        val row = Table()
        row.add(button(tr("menu.worldmap")) { game.goTo(WorldMapScreen(game)) }).growX().height(90f).pad(5f)
        val kBadge = if (core.kingdom.pendingIdle(core.now()).isNotEmpty() || core.events.pending() != null) " •" else ""
        row.add(button(tr("menu.kingdom") + kBadge, "success") { game.goTo(KingdomScreen(game)) }).growX().height(90f).pad(5f)
        root.add(row).width(580f).row()

        val grid = Table()
        val h = core.save.highestUnlocked
        val claimQ = (core.save.quests.daily + core.save.quests.weekly).count { it.progress >= it.target && !it.claimed }
        val claimA = core.achievements.claimable().size
        val items = listOf(
            Triple(tr("menu.daily"), 5) { game.goTo(DailyPuzzleScreen(game)) },
            Triple(tr("menu.endless"), 15) { game.goTo(EndlessScreen(game)) },
            Triple(tr("menu.quests") + if (claimQ > 0) " ($claimQ)" else "", 0) { game.goTo(QuestsScreen(game)) },
            Triple(tr("menu.shop"), 0) { game.goTo(ShopScreen(game)) },
            Triple(tr("menu.expeditions"), 5) { game.goTo(ExpeditionsScreen(game)) },
            Triple(tr("menu.research"), 10) { game.goTo(ResearchScreen(game)) },
            Triple(tr("menu.units"), 46) { game.goTo(UnitsScreen(game)) },
            Triple(tr("menu.defense") + if (core.defenseReady()) " !" else "", 46) { game.goTo(DefenseScreen(game)) },
            Triple(tr("menu.collection"), 0) { game.goTo(CollectionScreen(game)) },
            Triple(tr("menu.achievements") + if (claimA > 0) " ($claimA)" else "", 0) { game.goTo(AchievementsScreen(game)) },
            Triple(tr("menu.encyclopedia"), 0) { game.goTo(EncyclopediaScreen(game)) },
            Triple(tr("menu.inventory"), 0) { game.goTo(InventoryScreen(game)) },
            Triple(tr("menu.profile"), 0) { game.goTo(ProfileScreen(game)) },
            Triple(tr("menu.settings"), 0) { game.goTo(SettingsScreen(game)) },
            Triple(tr("menu.era"), 500) { game.goTo(EraScreen(game)) },
        )
        for ((i, it) in items.withIndex()) {
            val unlocked = h > it.second
            val text = if (unlocked) it.first else "${it.first}\nBölüm ${it.second + 1}"
            grid.add(button(text, "smallGhost", unlocked) { it.third() }).growX().uniformX().height(92f).pad(4f)
            if (i % 3 == 2) grid.row()
        }
        root.add(scroll(grid)).width(640f).grow().padTop(8f).padBottom(10f).row()
        updateLives()
        if (!shownLoadMessage) {
            shownLoadMessage = true
            core.loadResult.message?.let { toast(tr(it), Palette.danger) }
        }
    }

    private fun openLevel(id: Int) {
        when (core.playBlock(id)) {
            PlayBlock.OK -> core.startLevel(id)?.let { game.goTo(GameplayScreen(game, it)) }
            PlayBlock.NO_LIVES -> showDialog(tr("menu.play"), tr("lives.none"))
            PlayBlock.LOCKED -> error("common.locked")
        }
    }

    private var tick = 0f
    override fun update(dt: Float) {
        tick += dt
        if (tick > 1f) { tick = 0f; updateLives() }
    }

    private fun updateLives() {
        val now = core.now()
        val l = core.lives.lives(now)
        val hearts = "♥".repeat(l) + "♡".repeat((core.lives.max - l).coerceAtLeast(0))
        livesLabel.setText(if (l >= core.lives.max) "$hearts  ${tr("lives.full")}" else "$hearts  ${tr("lives.next", fmtDuration(core.lives.msToNext(now)))}")
        livesLabel.color = Palette.danger
    }

    override fun onBack() {
        showDialog(tr("app.title"), "Oyundan çıkmak istiyor musun? İlerlemen kaydedildi.",
            tr("common.no") to {}, tr("common.yes") to { core.persist(); com.badlogic.gdx.Gdx.app.exit() })
    }
}
