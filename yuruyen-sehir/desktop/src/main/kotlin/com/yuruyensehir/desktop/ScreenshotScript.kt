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
import com.yuruyensehir.logic.CivSystem
import com.yuruyensehir.logic.Culture
import com.yuruyensehir.logic.Resource
import com.yuruyensehir.logic.TravelSystem
import com.yuruyensehir.logic.CombatSystem
import com.yuruyensehir.logic.EnemyType
import com.yuruyensehir.logic.PopulationSystem
import com.yuruyensehir.logic.CarrierSpecies
import com.yuruyensehir.logic.Artifact
import com.yuruyensehir.logic.FleetSystem
import com.yuruyensehir.logic.SideSpecialty
import com.yuruyensehir.logic.CollectionSystem
import com.yuruyensehir.logic.SpecializationSystem
import com.yuruyensehir.logic.Specialization
import com.yuruyensehir.logic.GoalSystem
import com.yuruyensehir.logic.Cosmetic
import com.yuruyensehir.logic.ShopSystem
import com.yuruyensehir.logic.ResourceSystem

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
        275L to { g -> g.gameScreen.closePanel(); g.core.state.buildings.firstOrNull { it.type == BuildingType.FARM }?.let { g.gameScreen.selectBuilding(it.id) } },
        300L to { shot(it, "08_bina") },
        305L to { g -> g.gameScreen.closePanel(); g.gameScreen.setMode(ViewMode.MAP); g.gameScreen.tapMapCellForTest(Cell(2, 1)) },
        330L to { shot(it, "09_rota") },
        335L to { g -> g.gameScreen.startRouteForTest(); g.gameScreen.setMode(ViewMode.CITY); g.gameScreen.closePanel(); g.core.state.settings.gameSpeed = 3 },
        420L to { shot(it, "10_yolculuk") },
        425L to { g -> val s = g.core.state; s.pendingEvent = EventSystem.create(s, Biome.FOREST) },
        450L to { shot(it, "11_olay") },
        455L to { g -> g.core.state.pendingEvent = null; g.gameScreen.dismissModalForTest(); g.gameScreen.togglePanel(PanelId.POPULATION) },
        480L to { shot(it, "12_nufus") },
        485L to { g ->
            val s = g.core.state
            TravelSystem.stop(s)
            val m = s.civs.first { it.culture == Culture.MERCHANT_GUILD }
            s.travel.position = Cell(m.position.x - 1, m.position.y)
            s.store.amounts[Resource.GOLD] = 300.0
            CivSystem.meetCheck(s, offline = false)
            g.gameScreen.openCiv(m.id, PanelId.TRADE)
        },
        510L to { shot(it, "13_ticaret") },
        515L to { g -> g.gameScreen.openCiv(g.core.state.civs.first { it.met }.id, PanelId.DIPLOMACY) },
        540L to { shot(it, "14_diplomasi") },
        545L to { g -> g.gameScreen.closePanel(); g.gameScreen.setMode(ViewMode.MAP); g.gameScreen.tapMapCellForTest(g.core.state.civs.first { it.met }.position) },
        570L to { shot(it, "15_harita_medeniyet") },
        575L to { g ->
            val s = g.core.state
            g.gameScreen.setMode(ViewMode.CITY); g.gameScreen.closePanel()
            s.stage = 2
            s.population.count = 16
            listOf(Resource.WOOD, Resource.STONE, Resource.METAL, Resource.CLOTH, Resource.FOOD, Resource.WATER).forEach { s.store.amounts[it] = 200.0 }
            s.store.amounts[Resource.WOOD] = 400.0
            s.store.amounts[Resource.TOOLS] = 40.0
            BuildingSystem.place(s, BuildingType.DEFENSE_TOWER, 13, 7, instant = true)
            BuildingSystem.place(s, BuildingType.DEFENSE_TOWER, 5, 6, instant = true)
            BuildingSystem.place(s, BuildingType.WATCHTOWER, 9, 9, instant = true)
            BuildingSystem.place(s, BuildingType.BARRACKS, 9, 2, instant = true)
            BuildingSystem.place(s, BuildingType.EXPLORATION_CENTER, 11, 8, instant = true)
            PopulationSystem.autoAssign(s)
            s.settings.gameSpeed = 1
            ResourceSystem.recalcCapacity(s); s.store.clampToCapacity(); s.population.count = minOf(s.population.count, PopulationSystem.housing(s))
            CombatSystem.start(s, "Haydut Çetesi", listOf(EnemyType.BANDIT to 4, EnemyType.BEAST to 2))
            s.battle!!.prep = 0.0
        },
        700L to { shot(it, "16_savas") },
        705L to { g -> g.core.state.battle = null },
        710L to { g -> g.gameScreen.togglePanel(PanelId.DEFENSE) },
        735L to { shot(it, "17_savunma") },
        740L to { g -> g.gameScreen.togglePanel(PanelId.EXPLORATION) },
        765L to { shot(it, "18_kesif") },
        770L to { g ->
            val s = g.core.state
            g.gameScreen.closePanel()
            s.stage = 4
            s.population.count = 30
            listOf(Resource.WOOD, Resource.STONE, Resource.FOOD, Resource.WATER, Resource.CLOTH, Resource.CARE_ESSENCE).forEach { s.store.amounts[it] = 600.0 }
            s.store.capacity.keys.forEach { s.store.capacity[it] = 2000.0 }
            listOf(Resource.WOOD, Resource.STONE, Resource.FOOD, Resource.WATER, Resource.CLOTH).forEach { s.store.amounts[it] = 900.0 }
            s.store.amounts[Resource.CARE_ESSENCE] = 50.0
            s.collection.species += listOf(CarrierSpecies.ROCK_BACK, CarrierSpecies.FOREST_GUARDIAN, CarrierSpecies.CRYSTAL_HORN)
            s.collection.artifacts += listOf(Artifact.SUN_COMPASS, Artifact.OLD_MAP)
            FleetSystem.found(s, CarrierSpecies.ROCK_BACK, SideSpecialty.MINING)
            FleetSystem.found(s, CarrierSpecies.FOREST_GUARDIAN, SideSpecialty.FARMING)
            CollectionSystem.switchCarrier(s, CarrierSpecies.CRYSTAL_HORN)
            SpecializationSystem.choose(s, Specialization.NATURE)
            GoalSystem.refresh(s)
            s.settings.gameSpeed = 1
            ResourceSystem.recalcCapacity(s); s.store.clampToCapacity(); s.population.count = minOf(s.population.count, PopulationSystem.housing(s))
        },
        830L to { shot(it, "19_filo_sehir") },
        835L to { g -> g.gameScreen.togglePanel(PanelId.FLEET) },
        860L to { shot(it, "20_filo") },
        865L to { g -> g.gameScreen.togglePanel(PanelId.GOALS) },
        890L to { shot(it, "21_hedefler") },
        895L to { g ->
            val s = g.core.state
            g.gameScreen.closePanel()
            s.sideCities.clear()
            CollectionSystem.switchCarrier(s, CarrierSpecies.ROCK_BACK).let { if (!it.success) s.carrier.kind = CarrierSpecies.GIANT_TORTOISE }
            s.carrier.kind = CarrierSpecies.GIANT_TORTOISE
            s.store.amounts[Resource.GOLD] = 3000.0
            listOf(Cosmetic.SKIN_SUNSET, Cosmetic.ROOF_TURQUOISE, Cosmetic.FLAG_SUN, Cosmetic.DECOR_LANTERNS, Cosmetic.HOUSE_DOME, Cosmetic.TRAIL_FLOWERS).forEach { ShopSystem.buy(s, it) }
            ShopSystem.rename(s, "Mavi Kervan", null)
            ResourceSystem.recalcCapacity(s); s.store.clampToCapacity(); s.population.count = minOf(s.population.count, PopulationSystem.housing(s))
            s.cityVersion++
        },
        930L to { shot(it, "22_ozellestirme") },
        935L to { g -> g.gameScreen.togglePanel(PanelId.MARKET) },
        960L to { shot(it, "23_market") },
        965L to { Gdx.app.exit() },
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
