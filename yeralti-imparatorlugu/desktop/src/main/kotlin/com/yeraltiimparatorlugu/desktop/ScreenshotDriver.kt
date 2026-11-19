package com.yeraltiimparatorlugu.desktop

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.ScreenUtils
import com.yeraltiimparatorlugu.game.ToolMode
import com.yeraltiimparatorlugu.game.YeraltiGame
import com.yeraltiimparatorlugu.game.openAdvice
import com.yeraltiimparatorlugu.game.openBuildMenu
import com.yeraltiimparatorlugu.game.openColony
import com.yeraltiimparatorlugu.game.openEcosystem
import com.yeraltiimparatorlugu.game.openEmpire
import com.yeraltiimparatorlugu.game.openExploration
import com.yeraltiimparatorlugu.game.openMarket
import com.yeraltiimparatorlugu.game.openMenu
import com.yeraltiimparatorlugu.game.openMilitary
import com.yeraltiimparatorlugu.game.openResearch
import com.yeraltiimparatorlugu.game.openRivals
import com.yeraltiimparatorlugu.game.selectCell
import com.yeraltiimparatorlugu.sim.Ant
import com.yeraltiimparatorlugu.sim.AntType
import com.yeraltiimparatorlugu.sim.CreatureKind
import com.yeraltiimparatorlugu.sim.MarketItem
import com.yeraltiimparatorlugu.sim.PheromoneType
import com.yeraltiimparatorlugu.sim.ResearchTree
import com.yeraltiimparatorlugu.sim.ResourceType
import com.yeraltiimparatorlugu.sim.RoomType
import com.yeraltiimparatorlugu.sim.Simulation
import com.yeraltiimparatorlugu.sim.Weather

/**
 * Geliştirme aracı: `-Dyi.shots=klasör` ile sabit tohumlu bir koloniyi gerçek simülasyonla büyütür
 * (araştırmalar ve kaynaklar hızlandırılır, odaları işçiler kazar ve inşa eder), ekranları PNG olarak kaydeder.
 */
class ScreenshotDriver(private val game: YeraltiGame, private val outDir: String) : ApplicationListener by game {
    private var frame = 0
    private lateinit var sim: Simulation

    private fun run(seconds: Float) = repeat((seconds / Simulation.STEP).toInt()) { sim.step(Simulation.STEP) }

    private fun stock() {
        for (t in ResourceType.entries) sim.resources.set(t, 25)
        sim.resources.set(ResourceType.SOIL, 60); sim.resources.set(ResourceType.FOOD, 40)
    }

    private fun setup() {
        sim = Simulation.newGame(2026)
        ResearchTree.all.forEach { sim.research.completed.add(it.id) }
        sim.research.completed.remove("mil_elite"); sim.research.completed.remove("eco_waterproof")
        sim.recomputeModifiers()
        repeat(22) { i -> sim.ants.add(Ant(sim.newId(), AntType.WORKER, -1.5f + (i % 5), 9.5f)) }
        stock()
        for (x in -3 downTo -18) sim.designateDig(x, 9)
        for (x in 7..20) sim.designateDig(x, 9)
        for (y in 10..17) { sim.designateDig(-18, y); sim.designateDig(20, y) }
        for (x in -17..19) sim.designateDig(x, 17)
        run(260f); stock()
        listOf(
            Triple(RoomType.MATERIAL_STORAGE, -7, 7), Triple(RoomType.FUNGUS_FARM, 7, 7), Triple(RoomType.REST_ROOM, -12, 8),
            Triple(RoomType.RESEARCH_LAB, -17, 8), Triple(RoomType.BARRACKS, 13, 8), Triple(RoomType.TRAINING_ROOM, 18, 8),
            Triple(RoomType.EGG_ROOM, -6, 15), Triple(RoomType.LARVA_ROOM, -1, 15), Triple(RoomType.DEFENSE_ROOM, 4, 15),
            Triple(RoomType.TRAP_ROOM, 9, 16), Triple(RoomType.WATER_STORAGE, 13, 15), Triple(RoomType.INFIRMARY, -11, 15),
            Triple(RoomType.WORKSHOP, -16, 15),
        ).forEach { (t, x, y) -> stock(); if (sim.placeRoom(t, x, y) == null) println("YERLEŞMEDİ ${t.title}: ${sim.checkRoomPlacement(t, x, y).reason}") }
        repeat(8) { run(120f); stock() }
        sim.rooms.firstOrNull { it.type == RoomType.TRAINING_ROOM }?.let { sim.setTraining(it, AntType.SOLDIER, 3) }
        repeat(3) { run(100f); stock() }
        repeat(4) { sim.ants.add(Ant(sim.newId(), AntType.SOLDIER, 14.5f, 9.5f).also { a -> a.squad = it % 2 }) }
        sim.ants.add(Ant(sim.newId(), AntType.HEAVY_SOLDIER, 5.5f, 16.5f))
        sim.ants.add(Ant(sim.newId(), AntType.SCOUT, 0.5f, 9.5f).also { it.assignment = com.yeraltiimparatorlugu.sim.TaskCategory.EXPLORE })
        sim.rivalSystem.rivals.take(3).forEach { it.discovered = true }
        sim.rivalSystem.rivals.first().relation = 35
        sim.coins = 140
        sim.market.buy(sim, MarketItem.DECOR_GARDEN); sim.market.buy(sim, MarketItem.QUEEN_FLOWER)
        sim.placePheromone(PheromoneType.FOOD, 24, -1)
        sim.placePheromone(PheromoneType.DEFENSE, 6, 16)
        sim.startResearch(ResearchTree.get("mil_elite")!!)
        sim.spawnCreature(CreatureKind.SPIDER, 18, 17)
        sim.spawnCreature(CreatureKind.WORM, -9, 9)
        run(6f)
        println("Durum: nüfus ${sim.population}, oda ${sim.rooms.size}, aktif ${sim.rooms.count { it.functional }}")
    }

    override fun render() {
        game.render()
        frame++
        val t = game.tools
        when (frame) {
            2 -> shot("00_hosgeldiniz")
            3 -> { setup(); t.replaceSim(sim); t.hud.closeSheet(); t.camera.position.set(1f, -11f, 0f); t.camera.zoom = 2.3f; t.view.speed = 1 }
            8 -> shot("01_koloni")
            9 -> { t.camera.zoom = 0.9f; t.camera.position.set(14f, -16f, 0f) }
            12 -> shot("02_savas")
            13 -> { t.hud.selectCell(19, 9) }
            16 -> shot("03_egitim_odasi")
            17 -> { t.hud.openResearch(3) }
            20 -> shot("04_arastirma")
            21 -> { t.hud.openMilitary() }
            24 -> shot("05_ordu")
            25 -> { t.hud.openRivals() }
            28 -> shot("06_diplomasi")
            29 -> { t.hud.openExploration() }
            32 -> shot("07_kesif")
            33 -> { t.hud.openMarket() }
            36 -> shot("08_market")
            37 -> { t.hud.openEmpire() }
            40 -> shot("09_imparatorluk")
            41 -> { t.hud.openMenu() }
            44 -> shot("10_menu")
            45 -> { t.hud.openEcosystem() }
            48 -> shot("11_ekosistem")
            49 -> {
                t.hud.closeSheet()
                sim.environment.forceWeather(sim, Weather.STORM, 1e5)
                repeat((40 / Simulation.STEP).toInt()) { sim.step(Simulation.STEP) }
                t.camera.position.set(0f, -6f, 0f); t.camera.zoom = 1.4f
            }
            52 -> shot("12_saganak_sel")
            53 -> { val r = sim.rivalSystem.rivals.first(); t.camera.position.set(r.x.toFloat(), -1f, 0f); t.camera.zoom = 1.8f; sim.environment.forceWeather(sim, Weather.CLEAR, 1e5) }
            56 -> shot("13_rakip_yuvasi")
            57 -> { sim.time = Simulation.DAY_LENGTH * 9.4; sim.environment.forceWeather(sim, Weather.CLOUDY, 1e5); t.camera.position.set(-2f, -3f, 0f); t.camera.zoom = 1.6f }
            60 -> shot("14_kis")
            61 -> { t.camera.position.set(1f, -11f, 0f); t.camera.zoom = 1.2f; t.hud.openBuildMenu() }
            64 -> shot("15_insa_menusu")
            65 -> { t.hud.closeSheet(); t.hud.setMode(ToolMode.PHEROMONE) }
            68 -> shot("16_feromonlar")
            69 -> t.hud.openColony(0)
            72 -> shot("17_koloni_durum")
            73 -> t.hud.openAdvice()
            76 -> shot("18_oneriler")
            77 -> { t.hud.closeSheet(); t.camera.zoom = 0.8f; t.camera.position.set(-1f, -9f, 0f) }
            80 -> shot("19_yakin_cekim")
            82 -> Gdx.app.exit()
        }
    }

    private fun shot(name: String) {
        val w = Gdx.graphics.backBufferWidth
        val h = Gdx.graphics.backBufferHeight
        val pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true)
        val pm = Pixmap(w, h, Pixmap.Format.RGBA8888)
        for (i in 3 until pixels.size step 4) pixels[i] = 255.toByte()
        pm.pixels.clear(); pm.pixels.put(pixels); pm.pixels.position(0)
        PixmapIO.writePNG(Gdx.files.absolute("$outDir/$name.png"), pm)
        pm.dispose()
        println("SHOT $outDir/$name.png")
    }
}
