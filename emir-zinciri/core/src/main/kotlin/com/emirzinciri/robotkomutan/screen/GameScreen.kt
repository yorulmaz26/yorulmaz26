package com.emirzinciri.robotkomutan.screen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.utils.Align
import com.emirzinciri.robotkomutan.EmirZinciriGame
import com.emirzinciri.robotkomutan.StartupOptions
import com.emirzinciri.robotkomutan.logic.command.CommandType
import com.emirzinciri.robotkomutan.logic.command.Instr
import com.emirzinciri.robotkomutan.logic.content.Areas
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.level.LevelDef
import com.emirzinciri.robotkomutan.logic.level.ObjectiveType
import com.emirzinciri.robotkomutan.logic.progress.LivesSystem
import com.emirzinciri.robotkomutan.logic.progress.Stars
import com.emirzinciri.robotkomutan.logic.sim.FailReason
import com.emirzinciri.robotkomutan.logic.sim.Outcome
import com.emirzinciri.robotkomutan.logic.sim.SimEvent
import com.emirzinciri.robotkomutan.logic.sim.StepResult
import com.emirzinciri.robotkomutan.play.PlaySession
import com.emirzinciri.robotkomutan.play.PlaySession.Mode
import com.emirzinciri.robotkomutan.render.MapView
import com.emirzinciri.robotkomutan.ui.CardActor
import com.emirzinciri.robotkomutan.ui.Theme

/** Oynanış ekranı: Planla → Kartları yerleştir → Başlat → İzle → Düzelt. */
class GameScreen(game: EmirZinciriGame, private val def: LevelDef, private val startup: StartupOptions? = null) : BaseScreen(game) {

    private val level = LevelData(def)
    private val progression get() = services.progression
    private val session = PlaySession(level, progression.draft(def.id).ifEmpty {
        if (startup?.applySolution == true) def.solution else emptyMap()
    })
    private var selectedRobot = session.robotIds.first()
    /** Seçili kart (araç çubuğu açık); -1 yok. Yeni kartlar seçili kartın arkasına eklenir. */
    private var selectedCard = -1
    private val area = Areas.of(def.area)
    private val dnd = DragAndDrop().apply { setDragTime(220); setTapSquareSize(14f) }

    private lateinit var map: MapView
    private val stripTable = Table()
    private lateinit var stripScroll: ScrollPane
    private val toolbar = Table()
    private val palette = Table()
    private val controls = Table()
    private val robotTabs = Table()
    private val status = ui.label("", assets.fontSmall, Theme.inkSoft, wrap = true, align = Align.center)
    private val cardCounter = ui.label("", assets.fontBold, Theme.ink)
    private val livesLabel = ui.label("", assets.fontBold, Theme.ink)
    private val stripCards = ArrayList<CardActor>()
    private val stripSources = ArrayList<DragAndDrop.Source>()
    private val paletteCards = HashMap<CommandType, CardActor>()
    private lateinit var playButton: Button
    private lateinit var playIcon: Image
    private lateinit var stepButton: Button
    private lateinit var backStepButton: Button
    private lateinit var resetButton: Button
    private lateinit var speedLabel: Label
    private var paletteVisible = true
    private var infoTimer = 0f
    private var resultOverlay: Table? = null
    private var tutorial: TutorialHint? = null
    private val newCommands: Set<CommandType> = run {
        val prev = services.catalog[def.id - 1]?.commands?.toSet() ?: emptySet()
        def.commands.filter { it !in prev }.toSet()
    }

    init {
        session.listener = ::onStep
        build()
        refreshAll()
        if (startup?.autoRun == true) session.play()
    }

    // ================================================================== yerleşim

    private fun build() {
        root.top()
        root.add(buildTopBar()).growX().pad(16f, 16f, 6f, 16f).row()
        map = MapView(assets, session) { services.save.settings.showGrid }
        map.focusRobot = if (session.robotIds.size > 1) selectedRobot else null
        root.add(map).grow().row()
        root.add(status).growX().padLeft(24f).padRight(24f).height(30f).row()
        root.add(buildBottom()).growX()
    }

    private fun buildTopBar(): Table {
        val t = Table()
        t.add(ui.iconButton("back", size = 72f) { onBack() }).size(72f)
        val titles = Table()
        titles.add(ui.label("Bölüm ${def.id} · ${def.name}", assets.fontTitle, Theme.ink)).left().row()
        titles.add(ui.label("Alan ${area.id}: ${area.name}", assets.fontSmall, Theme.inkSoft)).left()
        t.add(titles).growX().left().padLeft(14f)
        val lives = Table()
        lives.background = assets.panel(Theme.white, 18, 3f)
        lives.add(ui.image("heart", Theme.red)).size(28f).padRight(6f)
        lives.add(livesLabel)
        lives.pad(6f, 12f, 6f, 14f)
        t.add(lives).padRight(10f)
        t.add(ui.iconButton("hint", Theme.yellow, Theme.ink, 72f) { showHint() }).size(72f)
        t.row()
        val goal = Table()
        goal.background = assets.panel(Theme.white, 20, 3f)
        goal.add(ui.image(goalIcon(), Theme.green)).size(30f).padRight(10f)
        goal.add(ui.label(def.goalText ?: objectivesText(), assets.font, Theme.ink, wrap = true)).growX()
        val star2 = Table()
        star2.add(ui.image("star", Theme.yellow)).size(24f).padRight(4f)
        star2.add(ui.label("${def.par}", assets.fontBold, Theme.inkSoft))
        goal.add(star2).padLeft(10f)
        goal.pad(10f, 16f, 10f, 16f)
        t.add(goal).colspan(4).growX().padTop(10f)
        return t
    }

    private fun goalIcon() = when (def.objectives.first().type) {
        ObjectiveType.REACH_EXIT -> "map"
        ObjectiveType.BOXES_ON_TARGETS -> "check"
        ObjectiveType.COLLECT_ALL_ENERGY, ObjectiveType.POWER_SOCKETS -> "bolt"
    }

    private fun objectivesText() = def.objectives.joinToString(" · ") { it.type.title }

    private fun buildBottom(): Table {
        val panel = ui.panel(Theme.white, 30, 8f)
        panel.pad(14f, 14f, 18f, 14f)
        // Robot sekmeleri + kart sayacı + palet aç/kapa
        val head = Table()
        head.add(robotTabs).left()
        head.add().growX()
        head.add(cardCounter).padRight(12f)
        head.add(ui.iconButton("menu", Theme.panelAlt, Theme.inkSoft, 56f) { togglePalette() }).size(56f)
        panel.add(head).growX().row()
        // Komut zinciri
        stripScroll = ScrollPane(stripTable, assets.scrollStyle)
        stripScroll.setScrollingDisabled(true, false)
        stripScroll.setFadeScrollBars(true)
        stripScroll.setOverscroll(false, true)
        val stripBg = Table()
        stripBg.background = assets.panel(Theme.panelAlt, 22, 0f, Theme.line)
        stripBg.add(stripScroll).grow().pad(10f)
        panel.add(stripBg).growX().height(236f).padTop(8f).row()
        dnd.addTarget(StripTarget(stripBg))
        panel.add(toolbar).growX().height(64f).padTop(6f).row()
        panel.add(palette).growX().padTop(4f).row()
        panel.add(controls).growX().padTop(10f)
        buildControls()
        buildPalette()
        return panel
    }

    private fun buildControls() {
        controls.clear()
        resetButton = ui.iconButton("reset", Theme.panelAlt, Theme.ink, 84f) { onReset() }
        backStepButton = ui.iconButton("stepback", Theme.panelAlt, Theme.ink, 84f) { session.stepBack(); map.clearPopups(); refreshAll() }
        stepButton = ui.iconButton("step", Theme.panelAlt, Theme.ink, 84f) { onStepButton() }
        playButton = Button(Button.ButtonStyle().apply {
            up = assets.panel(Theme.green, 26, 6f); down = assets.panel(Theme.shade(Theme.green, 0.9f), 26, 2f)
            disabled = assets.panel(Theme.line, 26, 3f)
        })
        playIcon = ui.image("play", Theme.white)
        playButton.add(playIcon).size(44f)
        ui.onTap(playButton, "card") { onPlayButton() }
        val speed = Button(Button.ButtonStyle().apply { up = assets.panel(Theme.panelAlt, 20, 5f, Theme.line); down = assets.panel(Theme.line, 20, 2f) })
        speedLabel = ui.label("", assets.fontBold, Theme.ink)
        speed.add(ui.image("speed", Theme.inkSoft)).size(26f).padRight(4f)
        speed.add(speedLabel)
        ui.onTap(speed) {
            val s = services.save.settings
            s.animSpeed = when (s.animSpeed) { 1 -> 2; 2 -> 4; else -> 1 }
            refreshControls()
        }
        controls.add(resetButton).size(84f).padRight(10f)
        controls.add(backStepButton).size(84f).padRight(10f)
        controls.add(playButton).growX().height(84f).padRight(10f)
        controls.add(stepButton).size(84f).padRight(10f)
        controls.add(speed).width(96f).height(84f)
    }

    private fun perRow(cardW: Float) = ((stage.width - 64f) / (cardW + 10f)).toInt().coerceAtLeast(4)

    private fun buildPalette() {
        palette.clear()
        paletteCards.clear()
        val per = perRow(82f)
        var i = 0
        for (t in def.commands) {
            val card = CardActor(assets, t, compact = true)
            card.newBadge = t in newCommands && !services.save.tutorialsSeen.contains("new_${t.name}_${def.id}") && def.id > 1
            paletteCards[t] = card
            ui.onTap(card, "card") { addCard(t) }
            dnd.addSource(PaletteSource(card, t))
            palette.add(card).size(82f, 92f).pad(5f)
            if (++i % per == 0) palette.row()
        }
    }

    // ================================================================== yenileme

    private fun refreshAll() {
        refreshTabs(); refreshStrip(); refreshToolbar(); refreshControls(); refreshInfo()
    }

    private fun refreshTabs() {
        robotTabs.clear()
        if (session.robotIds.size <= 1) {
            robotTabs.add(ui.label("Komut zinciri", assets.fontBold, Theme.inkSoft))
            return
        }
        session.robotIds.forEachIndexed { i, id ->
            val sel = id == selectedRobot
            val col = Theme.robotColors[i % Theme.robotColors.size]
            val b = ui.button("Robot $id", if (sel) col else Theme.panelAlt, if (sel) Theme.white else Theme.ink, "robot", assets.fontBold, 18) {
                selectedRobot = id; selectedCard = -1; map.focusRobot = id; refreshAll()
            }
            b.pad(6f, 12f, 6f, 14f)
            robotTabs.add(b).padRight(8f)
        }
    }

    private fun refreshStrip() {
        stripSources.forEach { dnd.removeSource(it) }
        stripSources.clear()
        stripTable.clear()
        stripCards.clear()
        val prog = session.programs.getValue(selectedRobot)
        val per = perRow(92f).coerceAtMost(7)
        val errors = session.errorCards[selectedRobot].orEmpty()
        val compileErr = session.compileError?.takeIf { it.first == selectedRobot }?.second?.error?.index
        val active = session.activePc(selectedRobot)
        var depth = 0
        prog.forEachIndexed { i, ins ->
            if (ins.type == CommandType.END) depth--
            val card = CardActor(assets, ins.type, ins.arg)
            card.indexLabel = "${i + 1}"
            card.selected = i == selectedCard
            card.highlight = i == active && session.mode != Mode.EDIT
            card.error = (i in errors || i == compileErr) && !card.highlight
            if (depth > 0) card.color.set(1f, 1f, 1f, 1f)
            if (ins.type.opensBlock) depth++
            ui.onTap(card, "click") { onCardTapped(i) }
            val src = StripSource(card, i)
            dnd.addSource(src); stripSources += src
            stripCards += card
            stripTable.add(card).size(92f, 104f).pad(6f)
            if ((i + 1) % per == 0) stripTable.row()
        }
        val left = session.maxCards - session.totalCards
        if (prog.size % per == 0 && prog.isNotEmpty()) stripTable.row()
        val slot = Table()
        slot.background = assets.panel(Color(1f, 1f, 1f, 0.6f), 18, 0f, Theme.line)
        slot.add(ui.label(if (left > 0) "+" else "dolu", if (left > 0) assets.fontTitle else assets.fontSmall, Theme.inkFaint))
        stripTable.add(slot).size(92f, 104f).pad(6f)
        stripTable.top().left()
        if (prog.isEmpty()) {
            stripTable.add(ui.label("Aşağıdaki kartlara dokun veya sürükle", assets.fontSmall, Theme.inkFaint)).padLeft(10f)
        }
        updateStripHighlights()
    }

    /** Simülasyon adımlarında zinciri yeniden kurmadan yalnızca vurguları günceller. */
    private fun updateStripHighlights() {
        val errors = session.errorCards[selectedRobot].orEmpty()
        val compileErr = session.compileError?.takeIf { it.first == selectedRobot }?.second?.error?.index
        val active = session.activePc(selectedRobot)
        stripCards.forEachIndexed { i, card ->
            card.highlight = i == active && session.mode != Mode.EDIT
            card.error = (i in errors || i == compileErr) && !card.highlight
            card.selected = i == selectedCard
        }
        // Çalışan kartı görünür tut
        if (active >= 0 && session.mode != Mode.EDIT) {
            stripTable.validate()
            stripCards.getOrNull(active)?.let { stripScroll.layout(); stripScroll.scrollTo(it.x, it.y, it.width, it.height, true, true) }
        }
    }

    private fun refreshToolbar() {
        toolbar.clear()
        val prog = session.programs.getValue(selectedRobot)
        val ins = prog.getOrNull(selectedCard)
        if (ins == null) {
            selectedCard = -1
            val tip = when (session.mode) {
                Mode.EDIT -> if (prog.isEmpty()) "Planla: kartları sırayla diz, sonra Başlat'a bas." else "Bir karta dokun: taşı, kopyala veya sil."
                Mode.RUNNING -> "Robotlar komutları uyguluyor…"
                Mode.PAUSED -> "Duraklatıldı: tek adım ilerle veya geri al."
                Mode.DONE -> "Kartları düzenlemek için bir karta dokun."
            }
            toolbar.add(ui.label(tip, assets.fontSmall, Theme.inkSoft, wrap = true, align = Align.center)).growX()
            if (session.mode == Mode.EDIT && prog.isNotEmpty()) {
                toolbar.add(ui.button("Temizle", Theme.panelAlt, Theme.red, "trash", assets.fontSmall, 18) { confirmClear() }.also { it.pad(6f, 12f, 6f, 14f) })
            }
            if (session.robotIds.size > 1 && session.mode == Mode.EDIT && prog.isNotEmpty()) {
                toolbar.add(ui.button("Diğerine kopyala", Theme.panelAlt, Theme.ink, "copy", assets.fontSmall, 18) { copyToOther() }
                    .also { it.pad(6f, 12f, 6f, 14f) }).padLeft(8f)
            }
            return
        }
        toolbar.add(ui.label("${selectedCard + 1}. ${ins.type.title}", assets.fontBold, Theme.ink)).left().growX()
        if (ins.type.hasArg) {
            toolbar.add(ui.iconButton("minus", Theme.panelAlt, Theme.purple, 56f) { session.setArg(selectedRobot, selectedCard, ins.arg - 1); afterEdit() }).size(56f).padRight(6f)
            toolbar.add(ui.iconButton("plus", Theme.panelAlt, Theme.purple, 56f) { session.setArg(selectedRobot, selectedCard, ins.arg + 1); afterEdit() }).size(56f).padRight(14f)
        }
        toolbar.add(ui.iconButton("left", Theme.panelAlt, Theme.ink, 56f) { moveSelected(-1) }).size(56f).padRight(6f)
        toolbar.add(ui.iconButton("right", Theme.panelAlt, Theme.ink, 56f) { moveSelected(1) }).size(56f).padRight(6f)
        toolbar.add(ui.iconButton("copy", Theme.panelAlt, Theme.ink, 56f) {
            if (session.duplicate(selectedRobot, selectedCard)) { selectedCard++; afterEdit() } else limitReached()
        }).size(56f).padRight(6f)
        toolbar.add(ui.iconButton("trash", Theme.panelAlt, Theme.red, 56f) {
            session.remove(selectedRobot, selectedCard); selectedCard = (selectedCard - 1).coerceAtLeast(-1); afterEdit()
        }).size(56f)
    }

    private fun refreshControls() {
        val m = session.mode
        playIcon.drawable = assets.icon(if (m == Mode.RUNNING) "pause" else "play")
        stepButton.isDisabled = m == Mode.RUNNING || m == Mode.DONE
        backStepButton.isDisabled = !(m == Mode.PAUSED && session.sim?.canStepBack == true)
        resetButton.isDisabled = m == Mode.EDIT
        playButton.isDisabled = m == Mode.DONE
        speedLabel.setText("${services.save.settings.animSpeed}×")
        palette.color.a = if (m == Mode.EDIT || m == Mode.DONE) 1f else 0.55f
    }

    private fun refreshInfo() {
        val lives = progression.lives
        val l = lives.lives
        val next = lives.msToNext()
        livesLabel.setText(if (next != null && l < LivesSystem.MAX_LIVES) "$l · ${LivesSystem.format(next)}" else "$l")
        val total = session.totalCards
        cardCounter.setText("$total/${session.maxCards} kart")
        cardCounter.color = if (total >= session.maxCards) Theme.orange else Theme.ink
    }

    override fun update(delta: Float) {
        val tick = 0.42f / services.save.settings.animSpeed
        val wasT = session.t
        session.update(delta, tick)?.let { showOutcome(it) }
        if (wasT < 1f && session.t >= 1f && session.mode == Mode.PAUSED) refreshControls()
        infoTimer -= delta
        if (infoTimer <= 0f) { infoTimer = 0.5f; refreshInfo() }
        tutorial?.update()
        if (startup?.openScreen == "win" && session.shownOutcome == null && session.mode == Mode.EDIT && time > 0.3f) {
            session.play()
        }
    }

    // ================================================================== düzenleme

    private fun editAllowed(): Boolean {
        if (session.mode == Mode.RUNNING || session.mode == Mode.PAUSED) {
            session.reset(); map.clearPopups()
            status.setText("Zincir değişti: simülasyon sıfırlandı.")
        }
        if (session.mode == Mode.DONE) session.backToEdit()
        return true
    }

    private fun addCard(t: CommandType) {
        editAllowed()
        val prog = session.programs.getValue(selectedRobot)
        val at = if (selectedCard >= 0) selectedCard + 1 else prog.size
        if (session.insertWithBlock(selectedRobot, at, Instr(t))) {
            if (selectedCard >= 0) selectedCard = at
            markNewSeen(t)
            afterEdit()
            stripScroll.layout(); stripScroll.scrollPercentY = 1f
        } else limitReached()
    }

    private fun markNewSeen(t: CommandType) {
        val key = "new_${t.name}_${def.id}"
        if (t in newCommands && services.save.tutorialsSeen.add(key)) paletteCards[t]?.newBadge = false
    }

    private fun onCardTapped(i: Int) {
        if (session.mode == Mode.RUNNING) { session.pause(); refreshAll(); return }
        if (session.mode != Mode.EDIT) { session.backToEdit(); map.clearPopups() }
        selectedCard = if (selectedCard == i) -1 else i
        refreshAll()
    }

    private fun moveSelected(d: Int) {
        val prog = session.programs.getValue(selectedRobot)
        val to = selectedCard + d
        if (to !in prog.indices) return
        session.move(selectedRobot, selectedCard, to)
        selectedCard = to
        afterEdit()
    }

    private fun afterEdit() {
        progression.saveDraft(def.id, session.tokens())
        services.save.stats.cardsPlaced++
        refreshAll()
    }

    private fun limitReached() {
        assets.play("bump", 0.7f)
        status.setText("Kart sınırı: bu bölümde en fazla ${session.maxCards} kart kullanabilirsin.")
        cardCounter.clearActions()
        cardCounter.addAction(Actions.sequence(Actions.color(Theme.red, 0.1f), Actions.color(Theme.orange, 0.5f)))
    }

    private fun confirmClear() {
        ui.dialog(stage, "Zinciri temizle", "Bu robotun bütün kartları silinsin mi?", "Vazgeç" to {}, "Temizle" to {
            session.clear(selectedRobot); selectedCard = -1; afterEdit()
        }, color = Theme.red)
    }

    private fun copyToOther() {
        val others = session.robotIds.filter { it != selectedRobot }
        val target = others.first()
        if (session.copyProgram(selectedRobot, target)) { status.setText("Zincir Robot $target'ya kopyalandı."); afterEdit() } else limitReached()
    }

    private fun togglePalette() {
        paletteVisible = !paletteVisible
        palette.isVisible = paletteVisible
        val cell = (palette.parent as Table).getCell(palette)
        if (paletteVisible) cell.height(palette.prefHeight) else cell.height(0f)
        (palette.parent as Table).invalidateHierarchy()
    }

    // ================================================================== çalıştırma

    private fun ensureCanRun(): Boolean {
        if (session.mode != Mode.EDIT) return true
        if (session.totalCards == 0) {
            status.setText("Önce zincire en az bir kart ekle.")
            assets.play("bump", 0.6f)
            return false
        }
        if (!progression.canStart(def)) { showNoLives(); return false }
        return true
    }

    private fun onPlayButton() {
        when (session.mode) {
            Mode.RUNNING -> session.pause()
            Mode.EDIT -> {
                if (!ensureCanRun()) return
                if (!session.play()) { showCompileError(); return }
                onRunStarted()
            }
            Mode.PAUSED -> session.play()
            Mode.DONE -> return
        }
        selectedCard = -1
        refreshAll()
    }

    private fun onStepButton() {
        val fresh = session.mode == Mode.EDIT
        if (fresh && !ensureCanRun()) return
        if (!session.stepOnce()) { if (session.compileError != null) showCompileError(); return }
        if (fresh) onRunStarted()
        selectedCard = -1
        refreshAll()
    }

    private fun onRunStarted() {
        progression.onRunStarted(def)
        progression.saveDraft(def.id, session.tokens())
        services.persist()
        map.clearPopups()
        status.setText("")
        tutorial?.onRunStarted()
    }

    private fun onReset() {
        session.reset()
        map.clearPopups()
        status.setText("Sıfırlandı. Kartların yerinde duruyor.")
        refreshAll()
    }

    private fun onStep(r: StepResult) {
        map.onEvents(r.events)
        var sound: String? = null
        var msg: String? = null
        for (e in r.events) {
            val robot = if (session.robotIds.size > 1) "Robot ${robotOf(e)}: " else ""
            when (e) {
                is SimEvent.Moved -> sound = sound ?: if (e.jump) "jump" else "move"
                is SimEvent.Turned -> sound = sound ?: "turn"
                is SimEvent.Bumped -> { sound = "bump"; msg = "$robot${e.pc + 1}. kart: ${e.reason}" }
                is SimEvent.CommandFailed -> { sound = "bump"; msg = "$robot${e.pc + 1}. kart: ${e.reason}" }
                is SimEvent.BoxPicked -> sound = "pick"
                is SimEvent.BoxDropped -> sound = "drop"
                is SimEvent.BoxMoved -> sound = "push"
                is SimEvent.Pressed -> sound = "press"
                is SimEvent.DoorChanged -> { sound = "door"; msg = msg ?: if (e.open) "Kapı açıldı." else "Kapı kapandı." }
                is SimEvent.EnergyCollected -> sound = "energy"
                is SimEvent.EnergyTransferred -> sound = "energy"
                is SimEvent.SocketPowered -> { sound = "energy"; msg = "Soket doldu!" }
                is SimEvent.MessageSent -> { sound = "signal"; msg = msg ?: "${robot}sinyal gönderdi." }
                is SimEvent.OutOfEnergy -> { sound = "bump"; msg = "$robot${e.pc + 1}. kart için enerji yetmedi." }
                is SimEvent.RobotLost -> { sound = "fall"; msg = "${robot}boşluğa düştü!" }
                else -> Unit
            }
        }
        sound?.let { assets.play(it, 0.55f) }
        status.setText("Adım ${r.tick}" + (msg?.let { " · $it" } ?: ""))
        updateStripHighlights()
        refreshControls()
    }

    private fun robotOf(e: SimEvent): String = when (e) {
        is SimEvent.Bumped -> e.robot; is SimEvent.CommandFailed -> e.robot; is SimEvent.MessageSent -> e.robot
        is SimEvent.OutOfEnergy -> e.robot; is SimEvent.RobotLost -> e.robot; else -> ""
    }

    private fun showCompileError() {
        val (robot, err) = session.compileError ?: return
        selectedRobot = robot
        refreshAll()
        assets.play("bump")
        status.setText("${err.error.index + 1}. kart: ${err.error.message}")
    }

    // ================================================================== sonuçlar

    private fun showOutcome(o: Outcome) {
        refreshAll()
        when (o) {
            is Outcome.Win -> showWin(o)
            is Outcome.Fail -> showFail(o)
        }
    }

    private fun showWin(o: Outcome.Win) {
        val checks = Stars.evaluate(def, o)
        val stars = checks.count { it.met }
        val res = progression.onWin(def, o, stars)
        services.persist()
        assets.play("win")
        val c = ui.panel()
        c.pad(30f)
        c.add(ui.label(if (res.firstClear) "Görev tamamlandı!" else "Yine başardın!", assets.fontBig, Theme.green, align = Align.center)).growX().row()
        val starRow = Table()
        for (i in 0 until 3) {
            val img = ui.image("star", if (checks[i].met) Theme.yellow else Theme.line)
            img.setOrigin(Align.center)
            if (checks[i].met) {
                img.setScale(0f)
                img.addAction(Actions.sequence(Actions.delay(0.25f + i * 0.22f), Actions.run { assets.play("star", 0.6f, 1f + i * 0.12f) },
                    Actions.scaleTo(1f, 1f, 0.35f, Interpolation.swingOut)))
            }
            starRow.add(img).size(if (i == 1) 96f else 78f).pad(8f).padBottom(if (i == 1) 26f else 8f)
        }
        c.add(starRow).padTop(12f).row()
        val list = Table()
        for (ch in checks) {
            list.add(ui.image(if (ch.met) "check" else "close", if (ch.met) Theme.green else Theme.inkFaint)).size(26f).padRight(10f)
            list.add(ui.label(ch.text, assets.font, if (ch.met) Theme.ink else Theme.inkFaint)).left().growX().row()
        }
        c.add(list).growX().padTop(6f).row()
        val st = o.stats
        c.add(ui.label("${st.cards} kart · ${st.ticks} adım" + (if (def.energy) " · ${st.energyUsed} enerji" else ""),
            assets.fontSmall, Theme.inkSoft, align = Align.center)).growX().padTop(10f).row()
        if (!res.reward.isEmpty) {
            val rw = Table()
            if (res.reward.gold > 0) rw.add(ui.chip("coin", "+${res.reward.gold}", Theme.yellow)).pad(4f)
            if (res.reward.metal > 0) rw.add(ui.chip("gearSmall", "+${res.reward.metal}", Theme.inkSoft)).pad(4f)
            if (res.reward.chips > 0) rw.add(ui.chip("chip", "+${res.reward.chips}", Theme.purple)).pad(4f)
            if (res.reward.xp > 0) rw.add(ui.chip("star", "+${res.reward.xp} XP", Theme.turquoise)).pad(4f)
            c.add(rw).padTop(14f).row()
        }
        if (def.id == 25) {
            c.add(ui.label("Tebrikler! Bir sonraki alanda ikinci robotun seni bekliyor.", assets.font, Theme.purple, wrap = true, align = Align.center))
                .growX().padTop(12f).row()
        }
        val next = services.catalog[def.id + 1]
        val buttons = Table()
        buttons.add(ui.button("Tekrar", Theme.panelAlt, Theme.ink, "reset") { closeResult(); session.backToEdit(); refreshAll() }).growX().uniformX().pad(6f)
        if (next != null) {
            buttons.add(ui.button("Sonraki", Theme.green, Theme.white, "play") { game.go(GameScreen(game, next)) }).growX().uniformX().pad(6f)
        } else {
            buttons.add(ui.button("Bölümler", Theme.turquoise, Theme.white, "map") { game.go(LevelSelectScreen(game)) }).growX().uniformX().pad(6f)
        }
        c.add(buttons).growX().padTop(20f).row()
        c.add(ui.button("Bölüm listesi", Theme.white, Theme.inkSoft, null, assets.fontSmall) { game.go(LevelSelectScreen(game, def.area)) }.also { it.pad(8f) })
            .padTop(4f)
        resultOverlay = ui.modal(stage, c, dismissable = false)
    }

    private fun showFail(o: Outcome.Fail) {
        val lost = progression.onFail(def, o.stats.ticks)
        services.persist()
        assets.play("fail")
        val c = ui.panel()
        c.pad(30f)
        c.add(ui.label(failTitle(o.reason), assets.fontBig, Theme.orange, align = Align.center)).growX().row()
        c.add(ui.label(o.detail, assets.font, Theme.ink, wrap = true, align = Align.center)).growX().padTop(10f).row()
        if (o.pc >= 0) {
            c.add(ui.label("Sorun ${o.pc + 1}. kartta (kırmızı işaretli).", assets.font, Theme.red, wrap = true, align = Align.center))
                .growX().padTop(8f).row()
        }
        c.add(ui.label(failTip(o.reason), assets.fontSmall, Theme.inkSoft, wrap = true, align = Align.center)).growX().padTop(10f).row()
        val livesRow = Table()
        livesRow.add(ui.image("heart", if (lost) Theme.red else Theme.inkFaint)).size(30f).padRight(8f)
        livesRow.add(ui.label(when {
            lost -> "1 can harcandı · kalan ${progression.lives.lives}"
            def.area == 1 -> "Öğretici alan: can harcanmadı"
            else -> "Tamamlanmış bölüm: can harcanmadı"
        }, assets.font, Theme.inkSoft))
        c.add(livesRow).padTop(14f).row()
        val buttons = Table()
        buttons.add(ui.button("Bölümler", Theme.panelAlt, Theme.ink, "map") { game.go(LevelSelectScreen(game, def.area)) }).growX().uniformX().pad(6f)
        buttons.add(ui.button("Düzelt", Theme.turquoise, Theme.white, "reset") {
            closeResult(); session.backToEdit(); refreshAll()
            status.setText("Kartların korunuyor: yalnızca hatalı kısmı düzelt.")
        }).growX().uniformX().pad(6f)
        c.add(buttons).growX().padTop(20f)
        resultOverlay = ui.modal(stage, c, dismissable = false)
    }

    private fun failTitle(r: FailReason) = when (r) {
        FailReason.ROBOT_LOST -> "Robot düştü!"
        FailReason.NO_ENERGY -> "Enerji bitti!"
        FailReason.PROGRAM_ENDED -> "Hedefe ulaşılamadı"
        FailReason.DEADLOCK -> "Robotlar takıldı"
        FailReason.TIMEOUT -> "Süre doldu"
        FailReason.INVALID_PROGRAM -> "Zincir hatalı"
    }

    private fun failTip(r: FailReason) = when (r) {
        FailReason.ROBOT_LOST -> "İpucu: boşluk ve çukurlara dikkat. Tek adım düğmesiyle hareketi adım adım izleyebilirsin."
        FailReason.NO_ENERGY -> "İpucu: enerji hücresi ya da şarj istasyonu kullan, gereksiz hamlelerden kaçın."
        FailReason.PROGRAM_ENDED -> "İpucu: kırmızı kartlar çarpmaları gösterir. Robotun yönünü takip et."
        FailReason.DEADLOCK -> "İpucu: 'Mesaj bekle' kartı bir sinyal gelene kadar bekler."
        FailReason.TIMEOUT -> "İpucu: döngülerin bitmesi için koşulları kontrol et."
        FailReason.INVALID_PROGRAM -> ""
    }

    private fun closeResult() { resultOverlay?.let { ui.close(it) }; resultOverlay = null }

    private fun showNoLives() {
        val next = progression.lives.msToNext()?.let { LivesSystem.format(it) } ?: "—"
        ui.dialog(stage, "Canın kalmadı",
            "Yeni bir can $next sonra dolacak. Bu sırada tamamladığın bölümleri can harcamadan tekrar oynayabilir, komutlarını planlamaya devam edebilirsin.",
            "Bölümler" to { game.go(LevelSelectScreen(game, def.area)) }, "Planlamaya devam" to {}, color = Theme.turquoise)
    }

    private fun showHint() {
        val c = ui.panel()
        c.pad(30f)
        c.add(ui.label("İpucu", assets.fontTitle, Theme.ink, align = Align.center)).growX().row()
        c.add(ui.label(def.hint ?: "Robotun yönünü takip et ve her kartın ne yaptığını düşün.", assets.font, Theme.inkSoft, wrap = true, align = Align.center))
            .growX().padTop(12f).row()
        val rules = Table()
        rules.add(ui.label("Yıldızlar", assets.fontBold, Theme.ink)).left().row()
        for (ch in Stars.evaluate(def, null)) rules.add(ui.label("• " + ch.text, assets.font, Theme.inkSoft)).left().row()
        if (progression.costsLife(def)) rules.add(ui.label("Başarısız deneme 1 can harcar. Planlamak ücretsizdir.", assets.fontSmall, Theme.inkFaint, wrap = true)).growX().padTop(6f).row()
        c.add(rules).growX().padTop(16f).row()
        var ov: Table? = null
        val row = Table()
        if (def.tutorial != null || def.story != null) row.add(ui.button("Öğretici", Theme.panelAlt, Theme.ink, "book") {
            ov?.let { ui.close(it) }; startTutorial(force = true)
        }).growX().uniformX().pad(6f)
        row.add(ui.button("Tamam", Theme.turquoise) { ov?.let { ui.close(it) } }).growX().uniformX().pad(6f)
        c.add(row).growX().padTop(18f)
        ov = ui.modal(stage, c)
    }

    // ================================================================== öğretici

    override fun show() {
        super.show()
        if (startup?.applySolution != true) startTutorial(force = false)
    }

    private fun startTutorial(force: Boolean) {
        val key = "level_${def.id}"
        val seen = key in services.save.tutorialsSeen
        if (seen && !force) return
        services.save.tutorialsSeen += key
        val story = def.story
        if (story != null && (force || !seen)) {
            ui.dialog(stage, area.name, story, "Başla" to { beginTutorialSteps() }, color = Theme.purple)
        } else beginTutorialSteps()
    }

    private fun beginTutorialSteps() {
        tutorial?.dispose()
        val steps = tutorialSteps()
        if (steps.isEmpty()) return
        tutorial = TutorialHint(this, steps)
    }

    /** Etkileşimli öğretici adımları: hedef öğe vurgulanır, oyuncu eylemi yapınca sonraki adıma geçilir. */
    private fun tutorialSteps(): List<TutorialHint.Step> {
        fun card(t: CommandType) = paletteCards[t]
        fun count(t: CommandType) = session.programs.getValue(selectedRobot).count { it.type == t }
        val running = { session.mode != Mode.EDIT }
        return when (def.tutorial) {
            "first_move" -> listOf(
                TutorialHint.Step(card(CommandType.FORWARD), "Bu bir komut kartı. Dokunarak zincire ekle: robot bir hücre ilerler.") { count(CommandType.FORWARD) >= 1 },
                TutorialHint.Step(card(CommandType.FORWARD), "Çıkış iki hücre ötede. Bir kart daha ekle.") { count(CommandType.FORWARD) >= 2 },
                TutorialHint.Step(playButton, "Hazırsın! Başlat'a bas ve robotunu izle.", running),
            )
            "turn" -> listOf(
                TutorialHint.Step(card(CommandType.TURN_RIGHT), "Yeni kart: Sağa dön. Robot yerinde döner, ilerlemez.") { count(CommandType.TURN_RIGHT) >= 1 },
                TutorialHint.Step(stripBgOrNull(), "Kartın sırası önemli! Bir karta dokunup oklarla yerini değiştirebilirsin.") { session.totalCards >= 3 },
            )
            "back" -> listOf(TutorialHint.Step(card(CommandType.BACK), "Yeni kart: Geri git. Robot dönmeden geri gider; dönüş kartlarından tasarruf edersin.") { count(CommandType.BACK) >= 1 })
            "limit" -> listOf(TutorialHint.Step(cardCounter, "Bu bölümde kart sınırı var. Sayaç kaç kart kaldığını gösterir.") { session.totalCards >= 1 })
            "carry" -> listOf(TutorialHint.Step(card(CommandType.PICK), "Nesne al: robotun önündeki kutuyu kaldırır. Bırakmak için 'Nesne bırak'.") { count(CommandType.PICK) >= 1 })
            "push" -> listOf(TutorialHint.Step(card(CommandType.PUSH), "İt: kutuyu bir hücre öne iterken robot da ilerler.") { count(CommandType.PUSH) >= 1 })
            "pit" -> listOf(TutorialHint.Step(map, "Çukura düşen robot kaybolur ama çukura itilen kutu çukuru doldurur.") { session.totalCards >= 1 })
            "press" -> listOf(TutorialHint.Step(card(CommandType.PRESS), "Butona bas: robot önündeki panele basar. Anahtar mavi kapıyı açar.") { count(CommandType.PRESS) >= 1 })
            "plate" -> listOf(TutorialHint.Step(map, "Turuncu plaka, üstünde bir şey durdukça kapıyı açık tutar.") { session.totalCards >= 1 })
            "timer" -> listOf(TutorialHint.Step(map, "Zamanlı düğme kapıyı yalnızca birkaç adım açık tutar. Sayaç kalan adımı gösterir.") { session.totalCards >= 1 })
            "wait" -> listOf(TutorialHint.Step(card(CommandType.WAIT), "Bekle: robot bir adım bekler. Zamanlama bulmacalarının anahtarı.") { count(CommandType.WAIT) >= 1 })
            "energy" -> listOf(
                TutorialHint.Step(map, "Robotun üstündeki sayı enerjisi. Her hareket enerji harcar.") { session.totalCards >= 1 },
                TutorialHint.Step(card(CommandType.COLLECT), "Enerji topla: üzerinde durduğun hücreden enerji alırsın.") { count(CommandType.COLLECT) >= 1 },
            )
            "transfer" -> listOf(TutorialHint.Step(card(CommandType.TRANSFER), "Enerji aktar: önündeki sokete 3 enerji verir. Dolan soket kapıyı açar.") { count(CommandType.TRANSFER) >= 1 })
            else -> emptyList()
        }
    }

    private fun stripBgOrNull(): Actor? = stripScroll.parent

    // ================================================================== geri / yaşam döngüsü

    override fun onBack() {
        if (tutorial?.active == true) { tutorial?.dispose(); tutorial = null; return }
        progression.saveDraft(def.id, session.tokens())
        services.persist()
        game.go(LevelSelectScreen(game, def.area))
    }

    override fun onPause() {
        progression.saveDraft(def.id, session.tokens())
    }

    override fun dispose() {
        tutorial?.dispose()
        map.dispose()
        super.dispose()
    }

    // ================================================================== sürükle-bırak

    private class Payload(val newType: CommandType? = null, val fromIndex: Int = -1)

    private fun dragActor(t: CommandType, arg: Int): Actor = CardActor(assets, t, arg).also {
        it.setSize(92f, 104f); it.color.a = 0.9f
    }

    private inner class PaletteSource(actor: CardActor, val type: CommandType) : DragAndDrop.Source(actor) {
        override fun dragStart(event: InputEvent, x: Float, y: Float, pointer: Int): DragAndDrop.Payload =
            DragAndDrop.Payload().apply {
                `object` = Payload(newType = type)
                dragActor = dragActor(type, type.defaultArg)
                dnd.setDragActorPosition(46f, -52f)
            }
    }

    private inner class StripSource(val card: CardActor, val index: Int) : DragAndDrop.Source(card) {
        override fun dragStart(event: InputEvent, x: Float, y: Float, pointer: Int): DragAndDrop.Payload {
            stripScroll.setFlickScroll(false)
            card.ghost = true
            return DragAndDrop.Payload().apply {
                `object` = Payload(fromIndex = index)
                dragActor = dragActor(card.type, card.arg)
                dnd.setDragActorPosition(46f, -52f)
            }
        }

        override fun dragStop(event: InputEvent?, x: Float, y: Float, pointer: Int, payload: DragAndDrop.Payload?, target: DragAndDrop.Target?) {
            stripScroll.setFlickScroll(true)
            card.ghost = false
            if (target == null) {
                // Zincirin dışına bırakılan kart silinir.
                editAllowed()
                session.remove(selectedRobot, index)
                selectedCard = -1
                assets.play("drop", 0.5f)
                afterEdit()
            }
        }
    }

    private inner class StripTarget(actor: Actor) : DragAndDrop.Target(actor) {
        override fun drag(source: DragAndDrop.Source, payload: DragAndDrop.Payload, x: Float, y: Float, pointer: Int): Boolean = true

        override fun drop(source: DragAndDrop.Source, payload: DragAndDrop.Payload, x: Float, y: Float, pointer: Int) {
            val p = payload.`object` as Payload
            val stagePos = actor.localToStageCoordinates(Vector2(x, y))
            val index = insertionIndex(stagePos)
            editAllowed()
            if (p.newType != null) {
                if (session.insertWithBlock(selectedRobot, index, Instr(p.newType))) { markNewSeen(p.newType); afterEdit() } else limitReached()
            } else if (p.fromIndex >= 0) {
                val to = if (index > p.fromIndex) index - 1 else index
                session.move(selectedRobot, p.fromIndex, to)
                selectedCard = -1
                afterEdit()
            }
        }
    }

    /** Sahne koordinatındaki bırakma noktasına göre zincirde ekleme yeri. */
    private fun insertionIndex(stagePos: Vector2): Int {
        var best = stripCards.size
        var bestD = Float.MAX_VALUE
        val tmp = Vector2()
        stripCards.forEachIndexed { i, c ->
            tmp.set(c.width / 2f, c.height / 2f)
            c.localToStageCoordinates(tmp)
            val dy = (tmp.y - stagePos.y) * 1.8f
            val dx = tmp.x - stagePos.x
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = if (stagePos.x < tmp.x) i else i + 1 }
        }
        return best
    }
}

/**
 * Öğretici balonu: hedef öğenin çevresinde nabız atan bir halka ve kısa açıklama gösterir.
 * Dokunmayı engellemez; oyuncu doğrudan hedef öğeyle etkileşir. Koşul sağlanınca sonraki adıma geçer.
 */
class TutorialHint(private val screen: BaseScreen, private val steps: List<Step>) {
    class Step(val target: Actor?, val text: String, val done: () -> Boolean)

    private var index = -1
    private val ring = Image(screen.assets.ring(26)).apply { touchable = Touchable.disabled; color = Color(Theme.orange) }
    private val bubble = Table()
    private val label = screen.ui.label("", screen.assets.font, Theme.ink, wrap = true)
    private var runStarted = false

    init {
        bubble.background = screen.assets.panel(Theme.white, 24, 8f, Theme.orange)
        bubble.add(screen.ui.image("hand", Theme.orange)).size(40f).padRight(12f)
        bubble.add(label).growX()
        val skip = screen.ui.label("Geç", screen.assets.fontSmall, Theme.inkFaint)
        bubble.add(skip).padLeft(12f)
        skip.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) = next()
        })
        bubble.pad(14f, 16f, 14f, 18f)
        screen.stage.addActor(ring)
        screen.stage.addActor(bubble)
        next()
    }

    fun onRunStarted() { runStarted = true }

    val active: Boolean get() = index in steps.indices

    private fun next() {
        index++
        if (index >= steps.size) { dispose(); return }
        label.setText(steps[index].text)
        bubble.clearActions(); bubble.color.a = 0f; bubble.addAction(Actions.fadeIn(0.25f))
        ring.clearActions()
        ring.addAction(Actions.forever(Actions.sequence(Actions.alpha(1f, 0.45f), Actions.alpha(0.35f, 0.45f))))
    }

    private val tmp = Vector2()

    fun update() {
        if (index !in steps.indices) return
        val step = steps[index]
        if (step.done()) { next(); return }
        val stage = screen.stage
        val w = stage.width * 0.9f
        bubble.width = w
        bubble.layout()
        bubble.height = bubble.prefHeight
        val target = step.target
        if (target == null || target.stage == null) {
            ring.isVisible = false
            bubble.setPosition((stage.width - w) / 2f, stage.height * 0.6f)
            return
        }
        ring.isVisible = true
        tmp.set(0f, 0f)
        target.localToStageCoordinates(tmp)
        val pad = 10f
        ring.setBounds(tmp.x - pad, tmp.y - pad, target.width + pad * 2, target.height + pad * 2)
        // Balon hedefin üstünde; yer yoksa altında.
        val above = tmp.y + target.height + 24f
        val by = if (above + bubble.height < stage.height - 20f) above else (tmp.y - bubble.height - 24f).coerceAtLeast(20f)
        bubble.setPosition((stage.width - w) / 2f, by)
        ring.toFront(); bubble.toFront()
    }

    fun dispose() {
        index = steps.size
        ring.remove(); bubble.remove()
    }
}
