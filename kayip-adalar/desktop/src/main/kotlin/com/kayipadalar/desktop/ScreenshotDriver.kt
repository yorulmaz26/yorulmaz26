package com.kayipadalar.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.ScreenUtils
import com.kayipadalar.game.DesktopPlatform
import com.kayipadalar.game.KayipAdalarGame
import com.kayipadalar.game.screens.*
import com.kayipadalar.logic.engine.Action
import com.kayipadalar.logic.engine.Rules

/**
 * Geliştirme/CI aracı: ekranlar arasında otomatik gezinip PNG ekran görüntüsü alır.
 * Kullanım: desktop çalıştırılırken "--shots <klasör>" argümanı verilir.
 */
class ScreenshotDriver(private val outDir: String) : KayipAdalarGame(DesktopPlatform()) {
    private var frame = 0
    private var step = 0
    private val steps: List<Pair<String, () -> Unit>> by lazy {
        listOf(
            "01_splash" to {},
            "02_menu" to { resetTo(MainMenuScreen(this)) },
            "03_level1" to { core.startLevel(1)?.let { goTo(GameplayScreen(this, it)) } },
            "04_level1_selected" to { (screen as? GameplayScreen)?.debugSelect("forest") },
            "05_level1_won" to {
                val gs = screen as GameplayScreen
                gs.debugPerform(Action.decode("S:forest:S"))
            },
            "06_level3" to { resetTo(MainMenuScreen(this)); core.save.highestUnlocked = 60; core.startLevel(3)?.let { goTo(GameplayScreen(this, it)) } },
            "07_level21_combo" to { resetTo(MainMenuScreen(this)); core.startLevel(21)?.let { goTo(GameplayScreen(this, it)) } },
            "08_level46_battle" to { resetTo(MainMenuScreen(this)); core.startLevel(46)?.let { goTo(GameplayScreen(this, it)) } },
            "09_level50_boss" to { resetTo(MainMenuScreen(this)); core.startLevel(50)?.let { goTo(GameplayScreen(this, it)) } },
            "10_worldmap" to { resetTo(MainMenuScreen(this)); goTo(WorldMapScreen(this)) },
            "11_levelselect" to { goTo(LevelSelectScreen(this, 2)) },
            "12_kingdom" to {
                core.wallet.add("GOLD", 50000); core.wallet.add("WOOD", 50000); core.wallet.add("STONE", 50000); core.wallet.add("IRON", 5000); core.wallet.add("FOOD", 5000); core.wallet.add("CRYSTAL", 2000)
                repeat(3) { core.upgradeBuilding("CASTLE") }
                listOf("LUMBER", "QUARRY", "FARM", "HARBOR", "ACADEMY", "TREASURY", "MUSEUM", "BARRACKS").forEach { id -> repeat(2) { core.upgradeBuilding(id) } }
                resetTo(MainMenuScreen(this)); goTo(KingdomScreen(this))
            },
            "13_research" to { goTo(ResearchScreen(this)) },
            "14_shop" to { goTo(ShopScreen(this)) },
            "15_quests" to { goTo(QuestsScreen(this)) },
            "16_achievements" to { goTo(AchievementsScreen(this)) },
            "17_collection" to { core.collection.grantRandom(1); core.collection.grantRandom(2); goTo(CollectionScreen(this)) },
            "18_encyclopedia" to { goTo(EncyclopediaScreen(this)) },
            "19_expeditions" to { core.startExpedition("SCOUT_SHORT"); goTo(ExpeditionsScreen(this)) },
            "20_units" to { goTo(UnitsScreen(this)) },
            "21_defense" to { goTo(DefenseScreen(this)) },
            "22_endless" to { goTo(EndlessScreen(this)) },
            "23_daily" to { goTo(DailyPuzzleScreen(this)) },
            "24_profile" to { goTo(ProfileScreen(this)) },
            "25_settings" to { goTo(SettingsScreen(this)) },
            "26_inventory" to { goTo(InventoryScreen(this)) },
            "27_era" to { goTo(EraScreen(this)) },
            "28_level120" to { resetTo(MainMenuScreen(this)); core.save.highestUnlocked = 501; core.startLevel(120)?.let { goTo(GameplayScreen(this, it)) } },
            "29_level250" to { resetTo(MainMenuScreen(this)); core.startLevel(250)?.let { goTo(GameplayScreen(this, it)) } },
            "30_level500" to { resetTo(MainMenuScreen(this)); core.startLevel(500)?.let { goTo(GameplayScreen(this, it)) } },
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
        if (frame % 50 == 0) {
            if (step < steps.size) {
                val (name, _) = steps[step]
                val pix = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
                PixmapIO.writePNG(Gdx.files.absolute("$outDir/$name.png"), pix, -1, true)
                pix.dispose()
                step++
                if (step < steps.size) runCatching { steps[step].second() }.onFailure { System.err.println("Adım ${steps[step].first}: $it"); it.printStackTrace() }
            } else Gdx.app.exit()
        }
    }
}
