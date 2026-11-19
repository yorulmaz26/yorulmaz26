package com.kayipadalar.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.kayipadalar.game.KayipAdalarGame
import com.kayipadalar.game.render.BoardView
import com.kayipadalar.game.render.Cosmetics
import com.kayipadalar.game.render.IslandArt
import com.kayipadalar.game.ui.Palette
import com.kayipadalar.logic.*
import com.kayipadalar.logic.engine.*
import com.kayipadalar.logic.level.ObjectiveDef
import com.kayipadalar.logic.level.ObjectiveType
import com.kayipadalar.logic.model.*
import com.kayipadalar.logic.tr

/** Oynanış ekranı: izometrik tahta, hedefler, eylem çubuğu, öğretici, ipucu ve sonuç ekranları. */
class GameplayScreen(game: KayipAdalarGame, private var session: PlaySession) : BaseScreen(game) {
    private lateinit var view: BoardView
    private var hud = Table()
    private var objectivesTable = Table()
    private var actions = Table()
    private var info = Table()
    private var tutorialBox: Table? = null
    private var tutorialStep = -1
    private var resultShown = false
    private var resultDelay = -1f
    private var hintBusy = false
    private val board get() = session.board

    override fun build() {
        val skyPair = IslandArt.themeSky[session.def.theme]
        if (skyPair != null) { game.sky.topTint = skyPair.first; game.sky.bottomTint = skyPair.second }
        val shop = core.shop
        fun col(cat: String) = shop.equippedColor(cat)?.takeIf { it.isNotEmpty() }?.let { Color.valueOf(it) }
        val cos = Cosmetics(col("ISLAND_SKIN"), col("CASTLE_SKIN"), col("BRIDGE") ?: Color.valueOf("C49A6C"), col("UNIT_COSTUME"), col("ANIMATION") ?: Color.valueOf("FF7043"))
        if (!::view.isInitialized) {
            view = BoardView(board, game.shapes, theme, cos, ::perform, { refreshInfo() }) { core.save.settings.animationsFast }
            setupTutorial()
        }
        root.top()
        root.add(hud).growX().pad(12f, 12f, 4f, 12f).row()
        root.add(objectivesTable).growX().padLeft(12f).padRight(12f).row()
        root.add(view).grow().row()
        root.add(info).growX().padLeft(12f).padRight(12f).row()
        root.add(actions).growX().pad(6f, 12f, 16f, 12f).row()
        refreshAll()
        tutorialBox?.let { stage.addActor(it) }
    }

    override fun hide() {
        // Diğer ekranlar varsayılan gökyüzünü kullanır.
        game.applySky()
    }

    private fun refreshAll() { refreshHud(); refreshObjectives(); refreshInfo() }

    private fun refreshHud() {
        hud.clear()
        hud.background = theme.panel
        hud.pad(8f, 10f, 8f, 10f)
        hud.add(button("II", "smallGhost") { pauseMenu() }).size(76f, 66f)
        val title = Table()
        val kindText = when (session.kind) {
            SessionKind.CHALLENGE, SessionKind.ERA -> tr("play.challenge", session.challenge?.title ?: "")
            else -> if (session.def.id in 1..500) "Bölüm ${session.def.id}" else ""
        }
        title.add(label(session.def.name, "default", wrap = true)).growX().row()
        if (kindText.isNotEmpty()) title.add(label(kindText, "muted")).left()
        hud.add(title).growX().padLeft(10f)
        val stats = Table()
        val movesColor = if (board.movesLeft <= 2) Palette.danger else Palette.text
        stats.add(label(tr("play.moves", board.movesUsed, board.moveLimit), "small", movesColor)).right().row()
        if (board.has(Mechanic.RAISE) || board.has(Mechanic.JUMP) || board.has(Mechanic.SWAP) || board.energy > 0)
            stats.add(label("⚡ ${tr("play.energy", board.energy)}", "small", Color.valueOf("0288D1"))).right().row()
        if (board.units.any { it.owner == Owner.ENEMY } || board.def.boss != null)
            stats.add(label("♥ ${tr("play.castle", board.castleHp, board.castleMaxHp)}", "small", Palette.danger)).right().row()
        hud.add(stats).right()
    }

    private fun objectiveText(o: ObjectiveDef): String = when (o.type) {
        ObjectiveType.GATHER -> tr("obj.GATHER", o.amount, tr("res.${o.res}"))
        ObjectiveType.PROTECT_CASTLE -> tr("obj.PROTECT_CASTLE", o.amount)
        ObjectiveType.SURVIVE -> tr("obj.SURVIVE", o.amount)
        ObjectiveType.TRADE_ROUTE -> tr("obj.TRADE_ROUTE", o.amount)
        ObjectiveType.MAKE_COMBO -> tr("obj.MAKE_COMBO", tr("combo.${o.combo}"))
        ObjectiveType.DEFEAT_BOSS -> tr("obj.DEFEAT_BOSS", session.def.boss?.name ?: "")
        else -> tr("obj.${o.type}")
    }

    private fun refreshObjectives() {
        objectivesTable.clear()
        objectivesTable.background = theme.panelAlt
        objectivesTable.pad(8f, 14f, 8f, 14f)
        for (o in session.def.objectives) {
            val p = Objectives.progress(board, o)
            val done = p >= 1.0
            val row = Table()
            row.add(label(if (done) "✓" else "•", "default", if (done) Palette.success else Palette.primary)).width(30f)
            var txt = objectiveText(o)
            if (o.type == ObjectiveType.GATHER) txt += "  (${board.res[Res.valueOf(o.res ?: "WOOD").ordinal]}/${o.amount})"
            if (o.type == ObjectiveType.SURVIVE || o.type == ObjectiveType.PROTECT_CASTLE) txt += "  (${board.turn}/${o.amount})"
            if (o.type == ObjectiveType.DEFEAT_BOSS) board.bossUnit?.let { txt += "  (♥ ${it.hp.coerceAtLeast(0)}/${it.maxHp})" }
            row.add(label(txt, "small", if (done) Palette.success else Palette.text, wrap = true)).growX()
            objectivesTable.add(row).growX().row()
        }
        if (session.def.special.isNotEmpty() && board.movesUsed == 0)
            objectivesTable.add(label(session.def.special, "muted", wrap = true)).growX().padTop(4f).row()
    }

    private fun refreshInfo() {
        info.clear()
        actions.clear()
        val sel = view.selectedId?.let { board.island(it) }
        val b = board
        if (sel != null && sel.alive && !sel.hidden) {
            val t = Table()
            t.background = theme.panelAlt
            t.pad(8f, 12f, 8f, 12f)
            var name = tr("island.${sel.type}")
            if (sel.frozen > 0) name += " · donmuş"
            if (sel.anchored > 0) name += " · kilitli"
            if (sel.fixed) name += " · sabit"
            if (sel.group != 0) name += " · birleşik"
            t.add(label(name, "small", Palette.primaryDown)).left().row()
            t.add(label(tr("islandDesc.${sel.type}"), "muted", wrap = true)).growX().left().row()
            b.unitOn(sel.id)?.let { u ->
                t.add(label("${tr("unit.${u.type}")} · Can ${u.hp}/${u.maxHp} · Saldırı ${u.atk} · Savunma ${u.def}", "small",
                    if (u.owner == Owner.PLAYER) Palette.primary else Palette.danger)).left().row()
            }
            info.add(t).growX()
        } else {
            info.add(label(when (view.mode) {
                BoardView.Mode.UNIT -> tr("play.unitMode"); BoardView.Mode.SWAP -> tr("play.swapMode"); BoardView.Mode.JUMP -> tr("play.jumpMode")
                BoardView.Mode.MERGE -> tr("play.mergeMode")
                else -> tr("play.selectIsland")
            }, "muted", wrap = true, align = Align.center)).growX().pad(6f)
        }
        val btns = ArrayList<com.badlogic.gdx.scenes.scene2d.ui.TextButton>()
        val id = sel?.id
        fun valid(a: Action) = Rules.validate(b, a) == null
        if (b.has(Mechanic.ROTATE)) btns.add(button("↻ ${tr("mech.ROTATE")}", "smallBtn", id != null && valid(Action.Rotate(id))) { id?.let { perform(Action.Rotate(it)) } })
        if (b.has(Mechanic.SWAP)) btns.add(button(tr("mech.SWAP"), "smallBtn", id != null && b.energy >= b.cfg.rules.costs.swapEnergy && sel?.movable == true) { view.mode = BoardView.Mode.SWAP; refreshInfo() })
        if (b.has(Mechanic.RAISE)) {
            btns.add(button("↑ ${tr("mech.RAISE")}", "smallBtn", id != null && valid(Action.Raise(id, true))) { id?.let { perform(Action.Raise(it, true)) } })
            btns.add(button("↓ ${tr("mech.LOWER")}", "smallBtn", id != null && valid(Action.Raise(id, false))) { id?.let { perform(Action.Raise(it, false)) } })
        }
        if (b.has(Mechanic.MERGE)) {
            if (sel?.group ?: 0 != 0) btns.add(button(tr("mech.SPLIT"), "smallBtn", id != null && valid(Action.Split(id))) { id?.let { perform(Action.Split(it)) } })
            else btns.add(button(tr("mech.MERGE"), "smallBtn", id != null && sel?.movable == true) { view.mode = BoardView.Mode.MERGE; refreshInfo() })
        }
        if (b.has(Mechanic.JUMP)) btns.add(button(tr("mech.JUMP"), "smallBtn", id != null && b.energy >= b.cfg.rules.costs.jumpEnergy && sel?.movable == true) { view.mode = BoardView.Mode.JUMP; refreshInfo() })
        if (b.has(Mechanic.UNIT_MOVE)) {
            val u = id?.let { b.unitOn(it) }
            btns.add(button(tr("mech.UNIT_MOVE"), "smallAccent", u != null && u.owner == Owner.PLAYER && u.stun == 0) { view.mode = BoardView.Mode.UNIT; refreshInfo() })
        }
        if (b.has(Mechanic.WAIT)) btns.add(button(tr("mech.WAIT"), "smallGhost") { perform(Action.Wait) })
        val perRow = if (btns.size > 4) (btns.size + 1) / 2 else btns.size
        var row1 = Table()
        for ((k, bt) in btns.withIndex()) {
            row1.add(bt).growX().uniformX().height(68f).pad(3f)
            if ((k + 1) % perRow == 0 && k + 1 < btns.size) { actions.add(row1).growX().row(); row1 = Table() }
        }
        actions.add(row1).growX().row()
        val row2 = Table()
        row2.add(button(tr("play.hint"), "smallGhost", !hintBusy && b.status == Status.PLAYING) { hint() }).growX().height(64f).pad(3f)
        row2.add(button("Yakınlaş ${"%.1f".format(view.zoom)}×", "smallGhost") { view.cycleZoom(); refreshInfo() }).growX().height(64f).pad(3f)
        row2.add(button(tr("play.restart"), "smallGhost") { restartAsk() }).growX().height(64f).pad(3f)
        actions.add(row2).growX()
    }

    // ------------------------------------------------------------------ Eylem

    /** Otomatik testler ve ekran görüntüsü aracı için. */
    fun debugSelect(id: String) { view.selectedId = id; refreshInfo() }
    fun debugPerform(a: Action) = perform(a)

    private fun perform(a: Action) {
        if (board.status != Status.PLAYING || resultShown) return
        val r = Rules.apply(board, a)
        if (!r.ok) { error(r.error ?: "error.invalid"); return }
        when (a) {
            is Action.Slide, is Action.Jump, is Action.Swap -> game.audio.play("slide", 0.6f)
            is Action.MoveUnit -> game.audio.play("tap", 0.6f)
            else -> {}
        }
        game.vibrate(12)
        view.onEvents(r.events) { game.audio.play(it, 0.7f) }
        advanceTutorial(a)
        if (view.hint != null && tutorialStep < 0) view.hint = null
        if (board.status != Status.PLAYING) {
            view.inputLocked = true
            resultDelay = if (core.save.settings.animationsFast) 0.4f else 0.8f
        }
        if (view.selectedId?.let { board.island(it)?.alive } != true) view.selectedId = null
        refreshAll()
    }

    override fun update(dt: Float) {
        if (resultDelay > 0) {
            resultDelay -= dt
            if (resultDelay <= 0) showResult()
        }
    }

    // ------------------------------------------------------------------ Öğretici

    private fun setupTutorial() {
        val key = session.def.tutorial ?: return
        if (key in core.save.settings.tutorialsSeen) return
        if (session.def.solution.isEmpty()) return
        tutorialStep = 0
        view.hint = Action.decode(session.def.solution[0])
        val box = Table()
        box.background = theme.tinted("round", Color(1f, 0.97f, 0.88f, 0.97f))
        box.pad(14f)
        box.add(label("Yeni: ${tr("mech.${session.def.mechanics.lastOrNull() ?: "SLIDE"}").takeIf { key == "SLIDE" || key == "ROTATE" } ?: "Öğretici"}", "small", Palette.accentDown)).left().row()
        box.add(label(tr("tutorial.$key"), "small", wrap = true)).width(600f).row()
        box.pack()
        box.setPosition((VW - box.width) / 2, 330f)
        box.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
        tutorialBox = box
    }

    private fun advanceTutorial(a: Action) {
        if (tutorialStep < 0) return
        val sol = session.def.solution
        if (tutorialStep < sol.size && a.encode() == sol[tutorialStep]) {
            tutorialStep++
            view.hint = if (tutorialStep < sol.size && session.def.id <= 2) Action.decode(sol[tutorialStep]) else null
        } else view.hint = null
        if (view.hint == null) {
            tutorialStep = -1
            tutorialBox?.remove(); tutorialBox = null
            session.def.tutorial?.let { core.save.settings.tutorialsSeen.add(it) }
        }
    }

    // ------------------------------------------------------------------ İpucu

    private fun hint() {
        if (core.wallet.get(Res.GOLD) < 20) { error("error.noResources"); return }
        hintBusy = true
        refreshInfo()
        val snapshot = board.copy()
        Thread {
            val res = Solver.beamFrom(snapshot, 28, snapshot.movesLeft)
            Gdx.app.postRunnable {
                hintBusy = false
                if (res != null && res.solution.isNotEmpty() && board.stateKey() == snapshot.stateKey()) {
                    core.wallet.add(Res.GOLD.name, -20)
                    view.hint = Action.decode(res.solution[0])
                    toast("İpucu: işaretli adayı kullan.")
                } else if (res == null) toast(tr("play.hintNone"), Palette.danger)
                refreshInfo()
            }
        }.apply { isDaemon = true }.start()
    }

    // ------------------------------------------------------------------ Duraklatma / yeniden başlatma

    private val penalizes get() = session.kind == SessionKind.MAIN && !session.replay && board.movesUsed > 0 && board.status == Status.PLAYING

    private fun pauseMenu() {
        showDialog(
            tr("play.pause"), if (penalizes) tr("play.quitWarn") else "",
            tr("play.resume") to {},
            tr("play.restart") to { restartAsk() },
            tr("play.quit") to { quit() },
        )
    }

    private fun quit() {
        if (penalizes || (session.kind == SessionKind.ENDLESS && board.movesUsed > 0)) { board.status = Status.LOST; core.finish(session) }
        game.back()
    }

    private fun restartAsk() {
        if (penalizes) showDialog(tr("play.restart"), tr("play.quitWarn"), tr("common.yes") to { doRestart() }, tr("common.no") to {})
        else doRestart()
    }

    private fun doRestart() {
        val failed = board.movesUsed > 0 && board.status == Status.PLAYING
        if (failed && session.kind in setOf(SessionKind.MAIN, SessionKind.CHALLENGE)) { board.status = Status.LOST; core.finish(session) }
        val next: PlaySession? = when (session.kind) {
            SessionKind.MAIN -> core.startLevel(session.def.id)
            SessionKind.CHALLENGE -> core.startLevel(session.def.id, session.challenge)
            SessionKind.ENDLESS -> if (failed) { board.status = Status.LOST; core.finish(session); null } else core.startEndless(false)
            SessionKind.DAILY -> core.startDaily()
            SessionKind.DEFENSE -> core.startDefense()
            SessionKind.ERA -> core.startEra(session.def.id, session.challenge!!)
        }
        if (next == null) {
            if (session.kind == SessionKind.ENDLESS) toast(tr("endless.over")) else toast(tr("lives.none"), Palette.danger)
            game.back(); return
        }
        replaceSession(next)
    }

    private fun replaceSession(s: PlaySession) {
        session = s
        resultShown = false
        resultDelay = -1f
        view.reset(s.board)
        view.inputLocked = false
        tutorialBox?.remove(); tutorialBox = null; tutorialStep = -1
        setupTutorial()
        rebuild()
    }

    override fun onBack() = pauseMenu()

    override fun onPause() {
        // Uygulama arka plana alınırsa ilerleme kaydedilir; deneme sürmeye devam eder.
        core.persist()
    }

    // ------------------------------------------------------------------ Sonuç

    private fun showResult() {
        if (resultShown) return
        resultShown = true
        val won = board.status == Status.WON
        val out = core.finish(session)
        if (won) { view.victory(); game.audio.play("win", 0.8f) } else game.audio.play("lose", 0.7f)
        val extra = Table()
        if (won) {
            val stars = out.stars
            extra.add(label("★".repeat(stars) + "☆".repeat(3 - stars), "huge", Palette.gold, align = Align.center)).row()
            if (out.rewards.isNotEmpty()) extra.add(label(out.rewards.entries.joinToString("   ") { "+${it.value} ${resName(it.key)}" }, "small", wrap = true, align = Align.center)).width(560f).padTop(6f).row()
            if (out.firstClear) extra.add(label(tr("play.firstClear"), "small", Palette.success)).padTop(4f).row()
            if (out.newBest && session.kind != SessionKind.ENDLESS) extra.add(label(tr("play.newBest", board.movesUsed), "small", Palette.primary)).row()
            out.collectionItem?.let { extra.add(label(tr("play.collected", core.collection.name(it)), "small", Color.valueOf("8E24AA"))).row() }
            if (session.kind == SessionKind.ENDLESS) extra.add(label(tr("endless.best", core.save.endless.best), "small")).row()
            if (session.kind == SessionKind.DAILY) extra.add(label(tr("daily.best", core.save.dailyPuzzleBest[core.dailyKey()] ?: board.movesUsed), "small")).row()
        } else {
            extra.add(label(tr("lose.${board.loseReason}"), "small", wrap = true, align = Align.center)).width(560f).row()
            if (out.lifeLost) extra.add(label(tr("play.lifeLost") + "  (${core.lives.lives(core.now())}/${core.lives.max})", "small", Palette.danger)).padTop(6f).row()
            if (out.message == "endless.over") extra.add(label(tr("endless.over") + " " + tr("endless.best", core.save.endless.best), "small")).row()
        }
        val buttons = ArrayList<Pair<String, () -> Unit>>()
        val toMap = tr("play.map") to { game.back() }
        if (won) {
            when (session.kind) {
                SessionKind.MAIN -> {
                    val nextId = session.def.id + 1
                    if (nextId <= 500 && core.playBlock(nextId) == PlayBlock.OK) buttons.add(tr("play.next") to { core.startLevel(nextId)?.let { replaceSession(it) } ?: game.back() })
                }
                SessionKind.ENDLESS -> buttons.add("Sonraki aşama" to { replaceSession(core.startEndless(false)) })
                else -> {}
            }
            if (session.kind != SessionKind.ENDLESS) buttons.add(tr("play.retry") to { doRestart() })
        } else {
            val canRetry = when (session.kind) {
                SessionKind.MAIN -> core.playBlock(session.def.id) == PlayBlock.OK
                SessionKind.ENDLESS -> false
                else -> true
            }
            if (canRetry) buttons.add(tr("play.retry") to { doRestart() })
            if (session.kind == SessionKind.ENDLESS) buttons.add(tr("endless.newRun") to { replaceSession(core.startEndless(true)) })
        }
        buttons.add(toMap)
        showDialog(if (won) tr("play.win") else tr("play.lose"), "", *buttons.toTypedArray(), extra = extra, dismissible = false)
    }
}
