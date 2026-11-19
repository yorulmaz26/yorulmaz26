package com.yeraltiimparatorlugu.desktop

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.ScreenUtils
import com.yeraltiimparatorlugu.game.ToolMode
import com.yeraltiimparatorlugu.game.YeraltiGame
import com.yeraltiimparatorlugu.sim.PheromoneType
import com.yeraltiimparatorlugu.sim.ResourceType
import com.yeraltiimparatorlugu.sim.RoomType
import com.yeraltiimparatorlugu.sim.Simulation

/**
 * Geliştirme aracı: `-Dyi.shots=klasör` ile çalıştırıldığında sabit tohumlu bir koloniyi ileri sarar,
 * birkaç ekranın görüntüsünü PNG olarak kaydeder ve kapanır. Oyunun kendisini değiştirmez.
 */
class ScreenshotDriver(private val game: YeraltiGame, private val outDir: String) : ApplicationListener by game {
    private var frame = 0

    override fun render() {
        game.render()
        frame++
        val t = game.tools
        when (frame) {
            3 -> {
                val sim = Simulation.newGame(2026)
                sim.resources.add(ResourceType.SOIL, 40); sim.resources.add(ResourceType.LEAF, 20); sim.resources.add(ResourceType.WOOD, 8); sim.resources.add(ResourceType.FOOD, 20)
                for (x in -3 downTo -9) sim.designateDig(x, 9)
                for (y in 10..13) sim.designateDig(-9, y)
                for (x in 7..10) sim.designateDig(x, 9)
                repeat((200 / Simulation.STEP).toInt()) { sim.step(Simulation.STEP) }
                sim.placeRoom(RoomType.MATERIAL_STORAGE, -8, 7)
                sim.placeRoom(RoomType.FUNGUS_FARM, 7, 7)
                sim.placeRoom(RoomType.REST_ROOM, -11, 14)
                repeat((360 / Simulation.STEP).toInt()) { sim.step(Simulation.STEP) }
                for (x in -1..3) sim.designateDig(x, 13)
                sim.placePheromone(PheromoneType.FOOD, 24, -1)
                repeat((20 / Simulation.STEP).toInt()) { sim.step(Simulation.STEP) }
                t.replaceSim(sim)
                t.hud.closeSheet()
                t.camera.position.set(0f, -5f, 0f)
                t.camera.zoom = 1.35f
            }
            10 -> shot("01_koloni")
            11 -> { t.camera.zoom = 0.75f; t.camera.position.set(-1f, -8.5f, 0f) }
            14 -> shot("02_yakin")
            15 -> t.hud.openBuildMenu()
            18 -> shot("03_insa_menusu")
            19 -> t.hud.openColony(1)
            22 -> shot("04_gorevler")
            23 -> t.hud.openColony(0)
            26 -> shot("05_genel")
            27 -> { t.hud.closeSheet(); t.camera.zoom = 1.1f; t.camera.position.set(0f, -8f, 0f); t.hud.selectCell(8, 8) }
            30 -> shot("06_oda_bilgisi")
            31 -> { t.hud.closeSheet(); t.hud.setMode(ToolMode.BUILD); t.view.ghostType = RoomType.EGG_ROOM; t.view.ghostX = -6; t.view.ghostY = 11; t.hud.onGhostMoved() }
            34 -> shot("07_yerlestirme")
            35 -> { t.hud.setMode(ToolMode.PHEROMONE); t.camera.position.set(14f, -3f, 0f); t.camera.zoom = 2.2f }
            38 -> shot("08_feromon")
            40 -> Gdx.app.exit()
        }
    }

    private fun shot(name: String) {
        val w = Gdx.graphics.backBufferWidth
        val h = Gdx.graphics.backBufferHeight
        val pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true)
        val pm = Pixmap(w, h, Pixmap.Format.RGBA8888)
        // Alfa kanalını tam opak yap.
        for (i in 3 until pixels.size step 4) pixels[i] = 255.toByte()
        pm.pixels.clear(); pm.pixels.put(pixels); pm.pixels.position(0)
        PixmapIO.writePNG(Gdx.files.absolute("$outDir/$name.png"), pm)
        pm.dispose()
        println("SHOT $outDir/$name.png")
    }
}
