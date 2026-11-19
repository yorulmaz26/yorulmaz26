package com.yuruyensehir.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.yuruyensehir.game.GameHook
import com.yuruyensehir.game.YuruyenSehirGame
import com.yuruyensehir.game.screens.PanelId
import com.yuruyensehir.game.screens.ViewMode
import com.yuruyensehir.logic.BuildingSystem
import com.yuruyensehir.logic.BuildingType
import com.yuruyensehir.logic.Biome
import com.yuruyensehir.logic.Cell
import com.yuruyensehir.logic.EventSystem

/** Oyunu otomatik sürüp belirli anlarda ekran görüntüsü alır (görsel doğrulama için). */
class ScreenshotScript(private val dir: String) : GameHook {
    private val steps: List<Pair<Long, (YuruyenSehirGame) -> Unit>> = listOf(
        5L to { g -> g.startNewGame() },
        30L to { g ->
            val s = g.core.state
            BuildingSystem.place(s, BuildingType.FARM, 10, 5, instant = true)
            BuildingSystem.place(s, BuildingType.WATER_COLLECTOR, 10, 7, instant = true)
            BuildingSystem.place(s, BuildingType.GATHERER, 12, 5, instant = true)
            BuildingSystem.place(s, BuildingType.SMALL_HOUSE, 7, 3, instant = true)
            BuildingSystem.place(s, BuildingType.MILL, 12, 6)
            g.gameScreen.dismissModalForTest()
        },
        90L to { shot(it, "01_sehir") },
        95L to { g -> g.gameScreen.beginPlacement(BuildingType.WORKSHOP) },
        120L to { shot(it, "02_yerlestirme") },
        125L to { g -> g.gameScreen.togglePanel(PanelId.BUILD) },
        150L to { shot(it, "03_insa") },
        155L to { g -> g.gameScreen.togglePanel(PanelId.CARRIER) },
        180L to { shot(it, "04_tasiyici") },
        185L to { g -> g.gameScreen.togglePanel(PanelId.ECONOMY) },
        210L to { shot(it, "05_ekonomi") },
        215L to { g -> g.gameScreen.setMode(ViewMode.MAP); g.gameScreen.showPanel(PanelId.MAP) },
        240L to { shot(it, "06_harita") },
        245L to { g -> g.gameScreen.setMode(ViewMode.CITY); g.gameScreen.togglePanel(PanelId.RESEARCH) },
        270L to { shot(it, "07_arastirma") },
        275L to { g -> g.gameScreen.closePanel(); g.core.state.buildings.firstOrNull { it.type == BuildingType.FARM }?.let { g.gameScreen.selectForTest(it.id) } },
        300L to { shot(it, "08_bina") },
        305L to { g -> g.gameScreen.closePanel(); g.gameScreen.setMode(ViewMode.MAP); g.gameScreen.tapMapCellForTest(Cell(2, 1)) },
        330L to { shot(it, "09_rota") },
        335L to { g -> g.gameScreen.startRouteForTest(); g.gameScreen.setMode(ViewMode.CITY); g.gameScreen.closePanel(); g.core.state.settings.gameSpeed = 3 },
        420L to { shot(it, "10_yolculuk") },
        425L to { g -> val s = g.core.state; s.pendingEvent = EventSystem.create(s, Biome.FOREST) },
        450L to { shot(it, "11_olay") },
        455L to { g -> g.core.state.pendingEvent = null; g.gameScreen.dismissModalForTest(); g.gameScreen.togglePanel(PanelId.POPULATION) },
        480L to { shot(it, "12_nufus") },
        485L to { Gdx.app.exit() },
    )

    override fun onFrame(game: YuruyenSehirGame, frame: Long) {
        steps.filter { it.first == frame }.forEach { it.second(game) }
    }

    private fun shot(g: YuruyenSehirGame, name: String) {
        val w = Gdx.graphics.backBufferWidth; val h = Gdx.graphics.backBufferHeight
        val pm = Pixmap.createFromFrameBuffer(0, 0, w, h)
        PixmapIO.writePNG(Gdx.files.absolute("$dir/$name.png"), pm, -1, true)
        pm.dispose()
    }
}
