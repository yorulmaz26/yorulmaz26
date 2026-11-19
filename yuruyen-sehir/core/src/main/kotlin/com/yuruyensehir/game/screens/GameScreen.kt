package com.yuruyensehir.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.yuruyensehir.game.YuruyenSehirGame
import com.yuruyensehir.game.render.CityRenderer
import com.yuruyensehir.game.render.Ghost
import com.yuruyensehir.game.render.MapRenderer
import com.yuruyensehir.game.ui.Bar
import com.yuruyensehir.game.ui.Palette
import com.yuruyensehir.game.ui.Ui
import com.yuruyensehir.logic.*

enum class PanelId(val title: String) {
    BUILD("İnşa Et"), BUILDING("Bina"), CARRIER("Taşıyıcı"), ECONOMY("Kaynak Ekonomisi"), POPULATION("Nüfus"),
    RESEARCH("Araştırma"), MAP("Dünya Haritası ve Rota"), LOG("Dünya Günlüğü"), SETTINGS("Ayarlar ve İstatistikler"),
    TRADE("Ticaret"), DIPLOMACY("Diplomasi"), DEFENSE("Savunma"), EXPLORATION("Keşif Ekipleri"),
    FLEET("Göçebe Filo"), GOALS("Hedefler ve Başarımlar"), MARKET("Market ve Özelleştirme"),
}

enum class ViewMode { CITY, MAP }

class GameScreen(val game: YuruyenSehirGame) : ScreenAdapter() {
    val theme = game.theme
    val ui = Ui(theme)
    val core: GameCore get() = game.core
    val state: GameState get() = game.core.state

    private val worldCam = OrthographicCamera()
    private val worldVp = ExtendViewport(720f, 1280f, worldCam)
    val stage = Stage(ExtendViewport(720f, 1280f))
    private val sr = ShapeRenderer()
    private val city = CityRenderer()
    private val mapR = MapRenderer()
    private var time = 0f

    var mode = ViewMode.CITY
        private set

    // Etkileşim durumu
    var placingType: BuildingType? = null
        private set
    private var ghostX = 0
    private var ghostY = 0
    private var movingId = -1
    var selectedId = -1
        private set
    private var mapSelected: Cell? = null
    private var routes: List<RouteOption> = emptyList()
    private var chosenRoute = 0
    private var confirmDemolishId = -1
    private var confirmNewGame = false
    private var buildCategory = BuildingCategory.PRODUCTION
    private var autosaveTimer = 0f
    private var refreshTimer = 0f
    private var hudTimer = 0f
    private var cityZoom = 0.9f
    private val cityCamPos = Vector2()
    private var mapCamInit = false

    // Arayüz
    private val root = Table()
    private val hud = Table()
    private val resLabels = HashMap<Resource, Label>()
    private val stageLabel = ui.label("", "default")
    private val dayLabel = ui.label("", "soft")
    private val statusLabel = ui.label("", "small", wrap = true)
    private val carrierLabel = ui.label("", "small", wrap = true)
    private val speedButtons = ArrayList<TextButton>()
    private val nav = Table()
    private val placeBar = Table()
    private val placeTitle = ui.label("", "default")
    private val placeInfo = ui.label("", "small", wrap = true)
    private lateinit var placeConfirm: TextButton
    private val sheet = Table()
    private val sheetTitle = ui.label("", "title")
    private val content = Table()
    private val scroll = ScrollPane(content, theme.skin)
    var openPanel: PanelId? = null
        private set
    private val overlay = Table()
    private val toast = ui.label("", "light", wrap = true, align = Align.center)
    private val toastBox = Table()
    private val battleBar = Table()
    private val battleLabel = ui.label("", "small", wrap = true)
    private val prioButtons = ArrayList<TextButton>()
    private lateinit var repairButton: TextButton
    private lateinit var tributeButton: TextButton
    var expDirection = 1
    var foundSpecies: CarrierSpecies? = null
    var foundSpecialty = SideSpecialty.FARMING
    var expDistance = 5

    private val fpsLabel = ui.label("", "small")

    init {
        ui.onClick = { game.audio.play(state, SoundId.CLICK, 0.6f) }
        buildUi()
        stage.addActor(fpsLabel)
        val gestures = GestureDetector(20f, 0.4f, 1.1f, 0.15f, WorldGestures())
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        Gdx.input.inputProcessor = InputMultiplexer(object : InputAdapter() {
            override fun keyDown(keycode: Int): Boolean {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) { onBack(); return true }
                return false
            }
        }, stage, gestures, object : InputAdapter() {
            override fun scrolled(amountX: Float, amountY: Float): Boolean {
                setZoom(worldCam.zoom * (1f + amountY * 0.1f)); return true
            }
        })
        resetCamera()
        showOfflineIfAny()
        if (state.log.size <= 2 && state.stats.buildingsBuilt <= 3) showWelcome()
    }

    // ------------------------------------------------------------------ Döngü

    override fun render(delta: Float) {
        val dt = delta.coerceAtMost(0.1f)
        time += dt
        val before = state.cityVersion
        core.update(dt.toDouble())
        if (state.cityVersion != before && openPanel != null) refreshPanel()

        val moving = state.travel.traveling && CarrierSystem.canMove(state) && state.pendingEvent == null && state.settings.gameSpeed > 0
        city.update(state, dt * maxOf(1, state.settings.gameSpeed), moving, CarrierSystem.speed(state, core.lastBalance).toFloat(), state.settings.graphicsHigh)
        game.audio.update(state, dt, moving)
        fpsLabel.isVisible = state.settings.showFps
        if (state.settings.showFps) {
            fpsLabel.setText("${Gdx.graphics.framesPerSecond} FPS")
            fpsLabel.pack(); fpsLabel.setPosition(stage.width - fpsLabel.width - 16f, hud.y - fpsLabel.height - 4f)
        }

        Gdx.gl.glClearColor(Palette.sky.r, Palette.sky.g, Palette.sky.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        worldVp.apply()
        sr.projectionMatrix = worldCam.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        sr.begin(ShapeRenderer.ShapeType.Filled)
        if (mode == ViewMode.CITY) {
            val ghost = placingType?.let { t ->
                val valid = if (movingId >= 0) BuildingSystem.footprintFits(state, t, ghostX, ghostY, movingId) == null
                else BuildingSystem.canPlace(state, t, ghostX, ghostY) == null
                Ghost(t, ghostX, ghostY, valid)
            }
            city.render(sr, worldCam, state, time, moving, ghost, selectedId, movingId, core.lastBalance, state.settings.showWeightOverlay || placingType != null, state.settings.graphicsHigh)
        } else {
            mapR.render(sr, worldCam, state, time, routes.getOrNull(chosenRoute), mapSelected)
        }
        sr.end()

        hudTimer += dt
        if (hudTimer > 0.2f) { hudTimer = 0f; updateHud() }
        refreshTimer += dt
        if (refreshTimer > 0.6f && !Gdx.input.isTouched) { refreshTimer = 0f; if (openPanel != null) refreshPanel() }
        if (state.pendingEvent != null && overlay.children.size == 0) showEvent()
        val inBattle = state.battle != null
        if (inBattle != battleBar.isVisible) {
            battleBar.isVisible = inBattle
            if (!inBattle) layoutUi()
            if (inBattle) { closePanel(); cancelPlacement(); if (mode == ViewMode.MAP) setMode(ViewMode.CITY); updateBattleBar() }
        }

        autosaveTimer += dt
        if (autosaveTimer > 30f) { autosaveTimer = 0f; game.save() }

        stage.viewport.apply()
        stage.act(dt)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        worldVp.update(width, height, false)
        stage.viewport.update(width, height, true)
        layoutUi()
        if (mode == ViewMode.CITY) resetCamera()
    }

    override fun dispose() {
        sr.dispose()
        stage.dispose()
    }

    fun onNewGame() {
        closePanel(); cancelPlacement(); overlay.clear(); overlay.isVisible = false
        mode = ViewMode.CITY; mapSelected = null; routes = emptyList()
        resetCamera()
        showWelcome()
    }

    // ------------------------------------------------------------------ Kamera

    private fun cityFocus(): Vector2 {
        val cx = CityGrid.CENTER_X.toFloat(); val cy = CityGrid.CENTER_Y.toFloat()
        return Vector2(CityRenderer.worldX(cx + 1.5f, cy), CityRenderer.worldY(cx, cy) + CityRenderer.DECK_Y - 40f)
    }

    private fun resetCamera() {
        if (mode == ViewMode.CITY) {
            val f = cityFocus()
            worldCam.zoom = cityZoom
            // Şehri üst yarıda tut: alt panel ve menüler için yer kalsın
            worldCam.position.set(f.x, f.y - 140f * cityZoom, 0f)
        } else {
            val p = state.travel.position
            worldCam.zoom = 1f
            worldCam.position.set(p.x * MapRenderer.SIZE + MapRenderer.SIZE / 2, p.y * MapRenderer.SIZE - 230f, 0f)
        }
        worldCam.update()
    }

    private fun setZoom(z: Float) {
        worldCam.zoom = z.coerceIn(0.55f, 2.2f)
        if (mode == ViewMode.CITY) cityZoom = worldCam.zoom
        worldCam.update()
    }

    private fun unproject(sx: Float, sy: Float): Vector3 = worldVp.unproject(Vector3(sx, sy, 0f))

    private inner class WorldGestures : GestureDetector.GestureAdapter() {
        private var startZoom = 1f
        private var zooming = false

        override fun touchDown(x: Float, y: Float, pointer: Int, button: Int): Boolean {
            if (!zooming) startZoom = worldCam.zoom
            return false
        }

        override fun tap(x: Float, y: Float, count: Int, button: Int): Boolean {
            val w = unproject(x, y)
            if (mode == ViewMode.CITY) onCityTap(w.x, w.y) else onMapTap(w.x, w.y)
            return true
        }

        override fun pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean {
            if (mode == ViewMode.CITY && placingType != null) {
                val w = unproject(x, y)
                moveGhostTo(w.x, w.y)
                return true
            }
            val scale = worldVp.worldWidth / Gdx.graphics.width * worldCam.zoom
            worldCam.position.add(-deltaX * scale, deltaY * scale, 0f)
            clampCamera()
            worldCam.update()
            return true
        }

        override fun zoom(initialDistance: Float, distance: Float): Boolean {
            zooming = true
            setZoom(startZoom * initialDistance / distance)
            return true
        }

        override fun panStop(x: Float, y: Float, pointer: Int, button: Int): Boolean {
            zooming = false; startZoom = worldCam.zoom
            return false
        }

        override fun pinchStop() { zooming = false; startZoom = worldCam.zoom }
    }

    private fun clampCamera() {
        if (mode == ViewMode.CITY) {
            val f = cityFocus()
            worldCam.position.x = worldCam.position.x.coerceIn(f.x - 700f, f.x + 700f)
            worldCam.position.y = worldCam.position.y.coerceIn(f.y - 900f, f.y + 600f)
        }
    }

    // ------------------------------------------------------------------ Şehir etkileşimi

    private fun onCityTap(wx: Float, wy: Float) {
        if (placingType != null) { moveGhostTo(wx, wy); return }
        val (x, y) = city.pickCell(wx, wy)
        val b = state.buildingAt(x, y)
        if (b != null) {
            selectedId = b.id
            confirmDemolishId = -1
            showPanel(PanelId.BUILDING)
        } else if (openPanel == PanelId.BUILDING) {
            selectedId = -1
            closePanel()
        }
    }

    private fun moveGhostTo(wx: Float, wy: Float) {
        val t = placingType ?: return
        val (x, y) = city.pickCell(wx, wy)
        ghostX = (x - (t.def.w - 1) / 2).coerceIn(0, CityGrid.GRID_W - t.def.w)
        ghostY = (y - (t.def.h - 1) / 2).coerceIn(0, CityGrid.GRID_H - t.def.h)
        updatePlaceBar()
    }

    fun beginPlacement(type: BuildingType) {
        closePanel()
        placingType = type
        movingId = -1
        val spot = findSpot(type)
        ghostX = spot.first; ghostY = spot.second
        nav.isVisible = false
        placeBar.isVisible = true
        updatePlaceBar()
    }

    private fun beginMove(b: PlacedBuilding) {
        closePanel()
        placingType = b.type
        movingId = b.id
        ghostX = b.x; ghostY = b.y
        nav.isVisible = false
        placeBar.isVisible = true
        updatePlaceBar()
    }

    /** Denge açısından en iyi geçerli konumu önerir. */
    private fun findSpot(type: BuildingType): Pair<Int, Int> {
        var best: Pair<Int, Int>? = null
        var bestScore = -1.0
        for (x in 0..CityGrid.GRID_W - type.def.w) for (y in 0..CityGrid.GRID_H - type.def.h) {
            if (BuildingSystem.footprintFits(state, type, x, y) != null) continue
            val r = WeightBalanceSystem.compute(state, PlacedBuilding(-1, type, x, y))
            if (r.balance > bestScore) { bestScore = r.balance; best = x to y }
        }
        return best ?: (CityGrid.CENTER_X.toInt() to CityGrid.CENTER_Y.toInt())
    }

    private fun cancelPlacement() {
        placingType = null
        movingId = -1
        placeBar.isVisible = false
        nav.isVisible = true
    }

    private fun confirmPlacement() {
        val t = placingType ?: return
        val r = if (movingId >= 0) BuildingSystem.move(state, movingId, ghostX, ghostY) else BuildingSystem.place(state, t, ghostX, ghostY)
        toast(r.message, !r.success)
        if (r.success) {
            cancelPlacement()
            game.save()
        }
    }

    private fun updatePlaceBar() {
        val t = placingType ?: return
        val def = t.def
        val before = core.lastBalance
        val after = if (movingId >= 0) WeightBalanceSystem.compute(state, PlacedBuilding(-1, t, ghostX, ghostY, level = state.building(movingId)?.level ?: 1), movedId = movingId)
        else WeightBalanceSystem.compute(state, PlacedBuilding(-1, t, ghostX, ghostY))
        val err = if (movingId >= 0) BuildingSystem.footprintFits(state, t, ghostX, ghostY, movingId)?.message
        else BuildingSystem.canPlace(state, t, ghostX, ghostY)?.message
        placeTitle.setText(if (movingId >= 0) "${def.title} taşınıyor" else "${def.title} yerleştir")
        fun cmp(a: Int, b: Int, higherBetter: Boolean = true): String {
            val col = when { a == b -> "4A3826"; (b > a) == higherBetter -> "2E8B57"; else -> "D84A2B" }
            return "%$a › [#$col]%$b[]"
        }
        val cost = if (movingId >= 0) BuildingSystem.moveCost(state.building(movingId)!!) else def.cost
        val loadCol = if (after.overloaded) "D84A2B" else "4A3826"
        placeInfo.setText(
            "Denge ${cmp(before.balancePercent, after.balancePercent)}   Verim ${cmp(before.efficiencyPercent, after.efficiencyPercent)}\n" +
                "Yük ${before.totalWeight.toInt()} › [#$loadCol]${after.totalWeight.toInt()}[] / ${after.capacity.toInt()}   Ön/Arka %${(after.frontShare * 100).toInt()}/%${100 - (after.frontShare * 100).toInt()}\n" +
                "Maliyet: ${ui.costText(cost, state.store)}\n" +
                (if (err != null) "[#D84A2B]$err[]" else "[#2E8B57]Uygun konum. Yeri değiştirmek için dokunun veya sürükleyin.[]"),
        )
        placeConfirm.isDisabled = err != null
        placeBar.invalidateHierarchy()
        placeBar.height = placeBar.prefHeight
        placeBar.setPosition(10f, 10f)
    }

    // ------------------------------------------------------------------ Harita etkileşimi

    private fun onMapTap(wx: Float, wy: Float) {
        val c = mapR.cellAt(wx, wy)
        mapSelected = c
        val err = TravelSystem.canTarget(state, c)
        routes = if (err == null) TravelSystem.planRoutes(state, c) else emptyList()
        chosenRoute = 0
        showPanel(PanelId.MAP)
    }

    fun setMode(m: ViewMode) {
        if (m == mode) return
        mode = m
        cancelPlacement()
        if (m == ViewMode.CITY) { mapSelected = null; routes = emptyList() }
        resetCamera()
        layoutUi()
    }

    // ------------------------------------------------------------------ Arayüz kurulumu

    private fun buildUi() {
        root.setFillParent(true)
        stage.addActor(root)

        // Üst bilgi paneli
        hud.background = theme.tinted("round", Color(1f, 0.98f, 0.93f, 0.93f))
        hud.pad(10f, 12f, 10f, 12f)
        ui.blockTouches(hud)
        val resTable = Table()
        Resource.basic.forEachIndexed { i, r ->
            val cell = Table()
            cell.add(ui.dot(Palette.resource(r), 16f)).size(16f).padRight(5f)
            val l = ui.label("0", "small")
            resLabels[r] = l
            cell.add(l).left().expandX()
            resTable.add(cell).expandX().fillX().left().pad(2f, 4f, 2f, 4f).uniformX()
            if (i % 4 == 3) resTable.row()
        }
        resTable.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
        resTable.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) { showPanel(PanelId.ECONOMY) }
        })
        hud.add(resTable).expandX().fillX().colspan(2).row()

        val line = Table()
        val titleBox = Table()
        titleBox.add(stageLabel).left().row()
        titleBox.add(dayLabel).left()
        line.add(titleBox).expandX().left()
        val speeds = listOf("Dur" to 0, "1x" to 1, "2x" to 2, "3x" to 3)
        for ((txt, sp) in speeds) {
            val b = ui.button(txt, "chip") { state.settings.gameSpeed = sp; updateHud() }
            b.pad(6f, 12f, 6f, 12f)
            speedButtons += b
            line.add(b).padLeft(4f)
        }
        hud.add(line).expandX().fillX().colspan(2).padTop(4f).row()
        hud.add(statusLabel).expandX().fillX().colspan(2).padTop(4f).row()
        hud.add(carrierLabel).expandX().fillX().colspan(2).padTop(2f)

        // Alt gezinme
        nav.background = theme.tinted("round", Color(1f, 0.98f, 0.93f, 0.95f))
        nav.pad(8f)
        ui.blockTouches(nav)
        val items = listOf(
            "İnşa Et" to { togglePanel(PanelId.BUILD) }, "Taşıyıcı" to { togglePanel(PanelId.CARRIER) }, "Filo" to { togglePanel(PanelId.FLEET) },
            "Harita" to { if (mode == ViewMode.MAP) { setMode(ViewMode.CITY); closePanel() } else { setMode(ViewMode.MAP); showPanel(PanelId.MAP) } },
            "Ekonomi" to { togglePanel(PanelId.ECONOMY) }, "Ticaret" to { togglePanel(PanelId.TRADE) },
            "Savunma" to { togglePanel(PanelId.DEFENSE) },
            "Nüfus" to { togglePanel(PanelId.POPULATION) }, "Araştırma" to { togglePanel(PanelId.RESEARCH) },
            "Diplomasi" to { togglePanel(PanelId.DIPLOMACY) }, "Keşif" to { togglePanel(PanelId.EXPLORATION) }, "Hedefler" to { togglePanel(PanelId.GOALS) },
            "Market" to { togglePanel(PanelId.MARKET) }, "Ayarlar" to { togglePanel(PanelId.SETTINGS) },
        )
        items.forEachIndexed { i, (t, f) ->
            nav.add(ui.button(t, "nav", f).apply { pad(12f, 1f, 12f, 1f) }).expandX().fillX().uniformX().pad(2f)
            if (i == 6) nav.row()
        }

        // Savaş çubuğu
        battleBar.background = theme.tinted("round", Color(1f, 0.93f, 0.9f, 0.97f))
        battleBar.pad(12f)
        ui.blockTouches(battleBar)
        battleBar.add(battleLabel).expandX().fillX().colspan(2).row()
        val prio = Table()
        for (p in TargetPriority.entries) {
            val b = ui.button(p.title, "chip") { CombatSystem.setPriority(state, p); updateBattleBar() }
            b.pad(6f, 8f, 6f, 8f)
            prioButtons += b
            prio.add(b).padRight(4f)
        }
        battleBar.add(ui.label("Hedef önceliği:", "small")).left().colspan(2).padTop(4f).row()
        battleBar.add(prio).left().colspan(2).padTop(2f).row()
        repairButton = ui.button("Acil Onarım", "gold") { toast(CombatSystem.emergencyRepair(state).message) }
        tributeButton = ui.button("Haraç öde", "danger") { val r = CombatSystem.payTribute(state); toast(r.message, !r.success) }
        battleBar.add(repairButton).expandX().fillX().pad(6f, 0f, 0f, 4f)
        battleBar.add(tributeButton).expandX().fillX().pad(6f, 4f, 0f, 0f)
        battleBar.isVisible = false

        // Yerleştirme çubuğu
        placeBar.background = theme.tinted("round", Color(1f, 0.98f, 0.93f, 0.97f))
        placeBar.pad(14f)
        ui.blockTouches(placeBar)
        placeBar.add(placeTitle).left().colspan(2).row()
        placeBar.add(placeInfo).expandX().fillX().colspan(2).padTop(6f).row()
        placeConfirm = ui.button("Onayla", "primary") { confirmPlacement() }
        placeBar.add(ui.button("İptal", "default") { cancelPlacement() }).expandX().fillX().pad(8f, 0f, 0f, 6f)
        placeBar.add(placeConfirm).expandX().fillX().pad(8f, 6f, 0f, 0f)
        placeBar.isVisible = false

        // Alt sayfa (paneller)
        sheet.background = theme.tinted("round", Palette.panel)
        sheet.pad(16f, 16f, 12f, 16f)
        ui.blockTouches(sheet)
        val head = Table()
        head.add(sheetTitle).expandX().left()
        head.add(ui.button("Kapat", "default") { closePanel(); if (mode == ViewMode.CITY) selectedId = -1 })
        sheet.add(head).expandX().fillX().row()
        scroll.setScrollingDisabled(true, false)
        scroll.setFadeScrollBars(true)
        scroll.setOverscroll(false, true)
        content.top().left()
        sheet.add(scroll).expand().fill().padTop(10f)
        sheet.isVisible = false

        // Bildirim
        toastBox.background = theme.tinted("round", Color(0.22f, 0.2f, 0.18f, 0.85f))
        toastBox.pad(12f, 18f, 12f, 18f)
        toastBox.add(toast).width(560f)
        toastBox.isVisible = false
        toastBox.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled

        overlay.setFillParent(true)
        overlay.isVisible = false

        stage.addActor(sheet)
        stage.addActor(nav)
        stage.addActor(placeBar)
        stage.addActor(battleBar)
        stage.addActor(hud)
        stage.addActor(toastBox)
        stage.addActor(overlay)
        layoutUi()
        updateHud()
    }

    private fun layoutUi() {
        val w = stage.width; val h = stage.height
        val m = 10f
        hud.pack(); hud.width = w - 2 * m; hud.invalidate(); hud.validate(); hud.pack(); hud.width = w - 2 * m
        hud.setPosition(m, h - hud.height - m)
        nav.width = w - 2 * m; nav.pack(); nav.width = w - 2 * m
        nav.setPosition(m, m)
        placeBar.width = w - 2 * m; placeBar.pack(); placeBar.width = w - 2 * m
        placeBar.setPosition(m, m)
        battleBar.width = w - 2 * m; battleBar.pack(); battleBar.width = w - 2 * m
        battleBar.setPosition(m, nav.height + 2 * m)
        val sheetH = if (mode == ViewMode.MAP) (h * 0.36f).coerceAtLeast(400f) else (h * 0.5f).coerceAtLeast(520f)
        sheet.setSize(w - 2 * m, sheetH)
        sheet.setPosition(m, nav.height + 2 * m)
        toastBox.pack()
        toastBox.setPosition((w - toastBox.width) / 2, h * 0.62f)
    }

    private fun updateHud() {
        val s = state
        for (r in Resource.basic) {
            val v = s.store[r]
            val cap = s.store.cap(r)
            val full = r != Resource.GOLD && v >= cap - 0.5
            val low = r != Resource.GOLD && r != Resource.ENERGY && v < 15
            val col = when { low -> "[#D84A2B]"; full -> "[#C07A10]"; else -> "" }
            resLabels[r]?.setText(if (r == Resource.GOLD) "$col${fmt(v)}${if (col.isNotEmpty()) "[]" else ""}" else "$col${fmt(v)}${if (col.isNotEmpty()) "[]" else ""}[#9A8670]/${fmt(cap)}[]")
        }
        stageLabel.setText("${s.cityName} • ${StageSystem.title(s.stage)}")
        dayLabel.setText("Gün ${s.day} • Nüfus ${s.population.count}/${PopulationSystem.housing(s)} • Mutluluk %${s.population.happiness.toInt()}")
        speedButtons.forEachIndexed { i, b -> b.isChecked = s.settings.gameSpeed == i }
        val r = core.lastBalance
        val balCol = when { r.balance > 0.85 -> "2E8B57"; r.balance > 0.6 -> "C07A10"; else -> "D84A2B" }
        val loadCol = when { r.loadRatio > 1.0 -> "D84A2B"; r.loadRatio > 0.8 -> "C07A10"; else -> "2E8B57" }
        val travel = if (s.travel.traveling) {
            val left = TravelSystem.remainingMinutes(s)
            if (CarrierSystem.canMove(s)) "Yolda ${s.travel.routeIndex}/${s.travel.route.size} • ~${fmt(left)} dk"
            else "[#D84A2B]Yolculuk duraklatıldı (dinlenme)[]"
        } else "Kamp: ${s.world.biomeAt(s.travel.position).title}"
        statusLabel.setText("Denge [#$balCol]%${r.balancePercent}[]  •  Yük [#$loadCol]${r.totalWeight.toInt()}/${r.capacity.toInt()}[]  •  Hareket Verimliliği %${r.efficiencyPercent}  •  $travel")
        val c = s.carrier
        fun pct(v: Double, bad: Double) = if (v >= bad) "[#D84A2B]%${v.toInt()}[]" else "%${v.toInt()}"
        carrierLabel.setText("${c.name}: Can %${c.health.toInt()} • Açlık ${pct(c.hunger, 70.0)} • Susuzluk ${pct(c.thirst, 70.0)} • Yorgunluk ${pct(c.fatigue, 80.0)}" +
            (if (c.forcedRest) " • [#D84A2B]Bitkin![]" else if (c.resting) " • Dinleniyor" else ""))
        if (placingType != null) updatePlaceBar()
        if (state.battle != null) updateBattleBar()
    }

    private fun updateBattleBar() {
        val b = state.battle ?: return
        val left = (CombatSystem.TIME_LIMIT - (b.elapsed - b.prep.coerceAtLeast(0.0))).coerceAtLeast(0.0)
        val cap = CombatSystem.shieldCapacity(state)
        battleLabel.setText(
            "[#D84A2B]SAVAŞ:[] ${b.title}\n" +
                (if (b.prep > 0) "Hazırlık: ${b.prep.toInt()} sn • " else "Kalan süre: ${left.toInt()} sn • ") +
                "Düşman ${b.enemies.size}/${b.total} • Yenilen ${b.kills}" +
                (if (cap > 0) " • Kalkan ${b.shield.toInt()}/${cap.toInt()}" else "") +
                " • Muhafız ${b.guards.count { it.alive }}/${b.guards.size} • Enerji ${fmt(state.store[Resource.ENERGY])}" +
                "\n[#7D6750]Oyunu 'Dur' ile duraklatıp öncelikleri değiştirebilirsiniz. Süre dolarsa düşman yağmalayıp çekilir.[]",
        )
        prioButtons.forEachIndexed { i, btn -> btn.isChecked = state.defensePriority.ordinal == i }
        repairButton.setText("Acil Onarım (${CombatSystem.repairCost.describe()})" + if (b.repairCooldown > 0) " ${b.repairCooldown.toInt()} sn" else "")
        repairButton.isDisabled = b.repairCooldown > 0 || !state.store.canAfford(CombatSystem.repairCost)
        tributeButton.setText("Haraç öde (${fmt(CombatSystem.tributeCost(state)[Resource.GOLD] ?: 0.0)} altın)")
        tributeButton.isDisabled = !state.store.canAfford(CombatSystem.tributeCost(state))
        battleBar.invalidateHierarchy(); battleBar.height = battleBar.prefHeight
        battleBar.setPosition(10f, nav.y + nav.height + 8f)
        if (sheet.isVisible) {
            val bottom = battleBar.y + battleBar.height + 8f
            sheet.setPosition(10f, bottom)
            sheet.height = (hud.y - 8f - bottom).coerceAtLeast(200f)
        }
    }

    // ------------------------------------------------------------------ Bildirimler ve diyaloglar

    fun toast(msg: String, error: Boolean = false) {
        if (error) game.audio.play(state, SoundId.ERROR, 0.6f)
        toast.setText(if (error) "[#FFB4A2]$msg[]" else msg)
        toastBox.pack()
        toastBox.setPosition((stage.width - toastBox.width) / 2, stage.height * 0.62f)
        toastBox.clearActions()
        toastBox.isVisible = true
        toastBox.color.a = 1f
        toastBox.addAction(Actions.sequence(Actions.delay(2.2f), Actions.fadeOut(0.5f), Actions.visible(false)))
    }

    private fun modal(title: String, body: String, buttons: List<Pair<String, () -> Unit>>, extra: Table? = null) {
        overlay.clear()
        overlay.isVisible = true
        overlay.background = theme.solid(Color(0.15f, 0.18f, 0.22f, 0.45f))
        ui.blockTouches(overlay)
        val box = ui.card(Palette.cream)
        box.pad(24f)
        box.add(ui.label(title, "title", wrap = true, align = Align.center)).width(560f).row()
        box.add(ui.label(body, "default", wrap = true, align = Align.center)).width(560f).padTop(12f).row()
        if (extra != null) box.add(extra).width(560f).padTop(10f).row()
        for ((t, f) in buttons) {
            box.add(ui.button(t, "primary") { f() }).width(560f).padTop(10f).row()
        }
        overlay.add(box).center()
    }

    private fun closeModal() {
        overlay.clear(); overlay.isVisible = false
    }

    /** Android geri tuşu: önce açık olanı kapatır, en sonda çıkış onayı ister. */
    private fun onBack() {
        when {
            overlay.isVisible && state.pendingEvent == null -> closeModal()
            overlay.isVisible -> Unit // olay seçimi zorunlu
            placingType != null -> cancelPlacement()
            openPanel != null -> { closePanel(); selectedId = -1 }
            mode == ViewMode.MAP -> setMode(ViewMode.CITY)
            else -> modal("Oyundan Çık", "Şehriniz kaydedilecek ve siz yokken de (en fazla 8 saat) üretmeye devam edecek.", listOf(
                "Kaydet ve Çık" to { game.save(); Gdx.app.exit() },
                "Oynamaya Devam" to { closeModal() },
            ))
        }
    }

    private fun showWelcome() {
        modal(
            "Yürüyen Şehir",
            "Büyük Çöküş'ten sonra topluluğunuz ${state.carrier.name} adlı dev taşıyıcının sırtında yaşıyor.\n\n" +
                "• Tarla ve Su Toplama Tesisi kurun.\n• Toplayıcı Rampası ile bulunduğunuz araziden odun ve taş toplayın.\n" +
                "• Binaların ağırlığı taşıyıcıyı etkiler: dengeyi koruyun.\n• Haritadan yeni bölgelere yolculuk edin.",
            listOf("Yolculuğa Başla" to { closeModal() }),
        )
    }

    fun showOfflineIfAny() {
        val rep = game.pendingOffline ?: return
        game.pendingOffline = null
        val gained = rep.gained.entries.sortedByDescending { kotlin.math.abs(it.value) }.take(8)
            .joinToString("\n") { (r, v) -> "${r.title}: ${if (v >= 0) "+" else ""}${fmt(v)}" }
        val mins = (rep.seconds / 60).toInt()
        modal(
            "Tekrar Hoş Geldiniz",
            "Siz yokken ${if (mins >= 60) "${mins / 60} saat ${mins % 60} dakika" else "$mins dakika"} geçti (en fazla 8 saat hesaba katılır).\n" +
                (if (rep.travelPaused) "Taşıyıcı yolculuğa ara verip dinlendi.\n" else "") + "\n" + gained.ifEmpty { "Kayda değer değişiklik yok." },
            listOf("Devam" to { closeModal() }),
        )
    }

    private fun showEvent() {
        val ev = state.pendingEvent ?: return
        val opts = Table()
        modal(ev.title, ev.text, emptyList(), opts)
        ev.options.forEachIndexed { i, o ->
            val parts = ArrayList<String>()
            if (o.cost.isNotEmpty()) parts += "Bedel: ${ui.costText(o.cost, state.store)}"
            if (o.reward.isNotEmpty()) parts += "[#2E8B57]Ödül: ${o.reward.describe()}[]"
            val b = ui.button(o.label, if (i == ev.options.lastIndex) "default" else "primary") {
                val r = EventSystem.resolve(state, i)
                if (r.success) { closeModal(); toast(r.message) } else toast(r.message, true)
            }
            b.isDisabled = !state.store.canAfford(o.cost)
            opts.add(b).expandX().fillX().padTop(10f).row()
            if (parts.isNotEmpty()) opts.add(ui.label(parts.joinToString("   "), "small", wrap = true, align = Align.center)).expandX().fillX().row()
        }
    }

    // ------------------------------------------------------------------ Paneller

    fun togglePanel(id: PanelId) {
        if (openPanel == id) closePanel() else {
            if (id != PanelId.MAP && mode == ViewMode.MAP && id != PanelId.CARRIER && id != PanelId.DIPLOMACY && id != PanelId.TRADE && id != PanelId.EXPLORATION) setMode(ViewMode.CITY)
            showPanel(id)
        }
    }

    fun showPanel(id: PanelId) {
        if (placingType != null) cancelPlacement()
        if (state.battle != null && id != PanelId.BUILDING && id != PanelId.DEFENSE) { toast("Savaş sürüyor: önce savunmaya odaklanın.", true); return }
        openPanel = id
        sheet.isVisible = true
        scroll.scrollY = 0f
        refreshPanel()
    }

    fun closePanel() {
        openPanel = null
        sheet.isVisible = false
    }

    private fun refreshPanel() {
        val id = openPanel ?: return
        val keepY = scroll.scrollY
        content.clear()
        content.defaults().left()
        sheetTitle.setText(id.title)
        val width = sheet.width - 40f
        when (id) {
            PanelId.BUILD -> Panels.build(this, content, width)
            PanelId.BUILDING -> {
                val b = state.building(selectedId)
                if (b == null) { closePanel(); return }
                sheetTitle.setText("${b.def.title} • Seviye ${b.level}")
                Panels.building(this, content, width, b)
            }
            PanelId.CARRIER -> Panels.carrier(this, content, width)
            PanelId.ECONOMY -> Panels.economy(this, content, width)
            PanelId.POPULATION -> Panels.population(this, content, width)
            PanelId.RESEARCH -> Panels.research(this, content, width)
            PanelId.MAP -> Panels.map(this, content, width)
            PanelId.LOG -> Panels.log(this, content, width)
            PanelId.SETTINGS -> Panels.settings(this, content, width)
            PanelId.TRADE -> Panels.trade(this, content, width)
            PanelId.DIPLOMACY -> Panels.diplomacy(this, content, width)
            PanelId.DEFENSE -> Panels.defense(this, content, width)
            PanelId.EXPLORATION -> Panels.exploration(this, content, width)
            PanelId.FLEET -> Panels.fleet(this, content, width)
            PanelId.GOALS -> Panels.goals(this, content, width)
            PanelId.MARKET -> Panels.market(this, content, width)
        }
        scroll.layout()
        scroll.scrollY = keepY
        scroll.updateVisualScroll()
    }

    // Panellerden çağrılan eylemler
    fun act(r: ActionResult) {
        toast(r.message, !r.success)
        if (r.success) state.cityVersion++
        refreshPanel()
    }

    var currentBuildCategory: BuildingCategory
        get() = buildCategory
        set(v) { buildCategory = v; refreshPanel() }

    fun requestDemolish(b: PlacedBuilding) {
        if (confirmDemolishId == b.id) {
            confirmDemolishId = -1
            val r = BuildingSystem.demolish(state, b.id)
            toast(r.message, !r.success)
            if (r.success) { selectedId = -1; closePanel() }
        } else {
            confirmDemolishId = b.id
            refreshPanel()
        }
    }

    /** Testler/betikler için: harita hücresine dokunmayı taklit eder. */
    fun tapMapCellForTest(c: Cell) = onMapTap(c.x * MapRenderer.SIZE + 1f, c.y * MapRenderer.SIZE + 1f)
    fun startRouteForTest() = startRoute()
    fun dismissModalForTest() = closeModal()

    /** Bir binayı seçip bilgi panelini açar. */
    fun selectBuilding(id: Int) { selectedId = id; showPanel(PanelId.BUILDING) }

    fun demolishPending(b: PlacedBuilding) = confirmDemolishId == b.id
    fun startMove(b: PlacedBuilding) = beginMove(b)

    fun mapRoutes() = routes
    fun mapTarget() = mapSelected
    fun chosenRouteIndex() = chosenRoute
    fun chooseRoute(i: Int) { chosenRoute = i; refreshPanel() }
    fun startRoute() {
        val r = routes.getOrNull(chosenRoute) ?: return
        val res = TravelSystem.start(state, r)
        toast(res.message, !res.success)
        if (res.success) { routes = emptyList(); mapSelected = null; game.save() }
        refreshPanel()
    }
    fun replanRoutes() {
        val c = mapSelected ?: return
        routes = if (TravelSystem.canTarget(state, c) == null) TravelSystem.planRoutes(state, c) else emptyList()
    }

    /** Ticaret/Diplomasi panellerinde seçili medeniyet. */
    var selectedCivId = -1
    private var confirmWarId = -1
    fun openCiv(id: Int, panel: PanelId) {
        selectedCivId = id
        if (panel != PanelId.MAP && mode == ViewMode.MAP) setMode(ViewMode.CITY)
        showPanel(panel)
    }
    fun selectCiv(id: Int) { selectedCivId = id; confirmWarId = -1; refreshPanel() }
    fun warPending(id: Int) = confirmWarId == id
    fun requestWar(id: Int) {
        if (confirmWarId == id) { confirmWarId = -1; act(DiplomacySystem.declareWar(state, id)) } else { confirmWarId = id; refreshPanel() }
    }

    /** Cihazın metin giriş penceresiyle isim değiştirme. */
    fun askRename(city: Boolean) {
        val current = if (city) state.cityName else state.carrier.name
        Gdx.input.getTextInput(object : Input.TextInputListener {
            override fun input(text: String) {
                Gdx.app.postRunnable {
                    act(if (city) ShopSystem.rename(state, text, null) else ShopSystem.rename(state, null, text))
                }
            }
            override fun canceled() {}
        }, if (city) "Şehrin adı" else "Taşıyıcının adı", current, "En fazla 24 harf")
    }

    fun newGameConfirm(): Boolean = confirmNewGame
    fun requestNewGame() {
        if (confirmNewGame) { confirmNewGame = false; game.startNewGame() } else { confirmNewGame = true; refreshPanel() }
    }

    fun bar(value: Float, color: Color, w: Float = 220f): Bar = Bar(theme, color, w, 16f).also { it.value = value }
}
