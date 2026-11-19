package com.kayipadalar.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.kayipadalar.game.DesktopPlatform
import com.kayipadalar.game.KayipAdalarGame
import com.kayipadalar.game.screens.*
import com.kayipadalar.logic.kingdom.IslandStatus
import com.kayipadalar.logic.kingdom.Res

/**
 * Geliştirme/CI aracı: oyunu otomatik oynatıp ekran görüntüsü alır.
 * Kullanım: desktop --shots <klasör> (çalışma dizini assets).
 */
class ScreenshotDriver(private val outDir: String) : KayipAdalarGame(DesktopPlatform()) {
    private var frame = 0
    private var step = 0
    private val ks get() = screen as? KingdomScreen

    private fun finishWork() {
        val now = game.now()
        for (b in k.s.buildings) if (b.busy) b.busyUntil = now
        for (i in k.s.islands) if (i.status == IslandStatus.EXPLORING) i.exploreUntil = now
        k.s.training.forEach { it.nextAt = now; it.each = 1 }
        game.tick(); game.tick()
    }

    private fun give() { for (r in Res.entries) k.s.res[r.name] = k.storageCap() * 0.9 }

    private val steps: List<Pair<String, () -> Unit>> by lazy {
        listOf(
            "01_splash" to {},
            "02_menu" to {},
            "03_intro" to { goTo(KingdomScreen(this)) },
            "04_start" to { ks?.closeOverlay() },
            "05_build_menu" to {
                val c = k.s.buildings.first(); k.upgrade(c.uid, game.now()); finishWork()
                ks?.view?.onPlot?.invoke(0, 1)
            },
            "06_growing" to {
                ks?.closeOverlay(); give()
                k.build("lumber", 0, 1, game.now()); finishWork()
                k.build("house", 0, 2, game.now()); finishWork()
                k.build("farm", 0, 3, game.now()); finishWork()
                k.upgrade(k.s.buildings.first().uid, game.now())
                k.s.pop = 8.0
                k.s.buildings.filter { it.type == "lumber" || it.type == "farm" }.forEach { it.stored = 200.0 }
            },
            "07_castle_sheet" to { finishWork(); give(); ks?.view?.onBuilding?.invoke(k.s.buildings.first()) },
            "08_world" to {
                ks?.closeOverlay(); give()
                k.upgrade(k.s.buildings.first().uid, game.now()); finishWork()
                listOf("quarry" to 4, "explore" to 5, "barracks" to 6, "house" to 7).forEach { (t, p) -> give(); k.build(t, 0, p, game.now()); finishWork() }
                k.s.islands.filter { it.ring == 1 }.forEach { k.explore(it.id, game.now()); finishWork() }
                k.s.pop = k.popCap().toDouble()
                ks?.view?.let { } ; goTo(WorldScreen(this, screen as KingdomScreen))
            },
            "09_army" to { back(); give(); k.s.pop = 40.0; k.train("infantry", 10, game.now()); k.train("archer", 6, game.now()); finishWork(); ks?.let { it.rebuild() }; ks?.javaClass?.getDeclaredMethod("armySheet")?.let { m -> m.isAccessible = true; m.invoke(ks) } },
            "10_battle" to {
                ks?.closeOverlay()
                val target = k.s.islands.filter { it.status == IslandStatus.DISCOVERED }.minByOrNull { it.guard }!!
                goTo(WorldScreen(this, screen as KingdomScreen))
                val ws = screen as WorldScreen
                val m = ws.javaClass.getDeclaredMethod("attack", com.kayipadalar.logic.kingdom.IslandState::class.java)
                m.isAccessible = true; m.invoke(ws, target)
            },
            "11_battle_result" to {},
            "12_merge" to {
                (screen as? BaseScreen)?.closeOverlay()
                k.s.islands.filter { it.status == IslandStatus.DISCOVERED }.forEach { it.status = IslandStatus.CLEARED }
                val cleared = k.s.islands.filter { it.status == IslandStatus.CLEARED }
                cleared.take(3).forEach { k.merge(it.id) }
                back()
                ks?.mergeArrived(cleared.first().id); ks?.rebuild()
            },
            "13_kingdom_big" to {
                give()
                for (isl in k.mergedIslands()) for (p in 0 until k.plotsOf(isl)) { give(); k.build(listOf("mine", "warehouse", "house", "lumber")[p % 4], isl.id, p, game.now()) ; finishWork() }
                k.s.buildings.first().level = 5
                k.s.raid = com.kayipadalar.logic.kingdom.Raid(40, game.now())
                ks?.view?.focus(com.badlogic.gdx.math.Vector2(0f, 0f), 0.4f)
            },
            "14_research" to { give(); k.build("research", k.mergedIslands().last().id, 0, game.now()); ks?.javaClass?.getDeclaredMethod("researchSheet")?.let { m -> m.isAccessible = true; m.invoke(ks) } },
            "15_quests" to { ks?.closeOverlay(); ks?.javaClass?.getDeclaredMethod("questsSheet")?.let { m -> m.isAccessible = true; m.invoke(ks) } },
            "16_raid" to { ks?.closeOverlay(); ks?.javaClass?.getDeclaredMethod("raidDialog")?.let { m -> m.isAccessible = true; m.invoke(ks) } },
            "17_settings" to { ks?.closeOverlay(); goTo(SettingsScreen(this)) },
        )
    }

    override fun create() {
        Gdx.files.local("kayip_adalar_save.dat").delete()
        Gdx.files.local("kayip_adalar_save.bak").delete()
        super.create()
    }

    override fun render() {
        super.render()
        frame++
        if (frame % 45 == 0) {
            if (step < steps.size) {
                val pix = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
                PixmapIO.writePNG(Gdx.files.absolute("$outDir/${steps[step].first}.png"), pix, -1, true)
                pix.dispose()
                step++
                if (step < steps.size) runCatching { steps[step].second() }.onFailure { System.err.println("Adım ${steps[step].first}: $it"); it.printStackTrace() }
            } else {
                System.err.println("Eksik sprite: ${assets.missingSprites}")
                Gdx.app.exit()
            }
        }
    }
}
