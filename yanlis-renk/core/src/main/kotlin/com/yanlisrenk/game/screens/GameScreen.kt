package com.yanlisrenk.game.screens

import com.yanlisrenk.logic.i18n.L
import com.yanlisrenk.logic.i18n.lupper

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.yanlisrenk.game.YanlisRenkGame
import com.yanlisrenk.game.render.Theme
import com.yanlisrenk.game.render.WorldRenderer
import com.yanlisrenk.game.ui.Pal
import com.yanlisrenk.game.ui.ProgressBarActor
import com.yanlisrenk.game.ui.Ui
import com.yanlisrenk.logic.LevelResult
import com.yanlisrenk.logic.level.AreaCatalog
import com.yanlisrenk.logic.model.GameColor
import com.yanlisrenk.logic.model.LevelData
import com.yanlisrenk.logic.model.Mechanic
import com.yanlisrenk.logic.progress.WeeklyChallenge
import com.yanlisrenk.logic.run.EndlessGenerator
import com.yanlisrenk.logic.run.RunEvent
import com.yanlisrenk.logic.run.RunWorld
import com.yanlisrenk.logic.run.Track
import com.yanlisrenk.logic.shop.ShopCategory

enum class Mode { LEVEL, MASTERY, ENDLESS, WEEKLY }

class GameScreen(game: YanlisRenkGame, val mode: Mode, val levelId: Int = 1) : BaseScreen(game) {
    private enum class State { READY, COUNTDOWN, PLAYING, PAUSED, ENDED }

    private val level: LevelData? = when (mode) {
        Mode.LEVEL -> game.levels.level(levelId)
        Mode.MASTERY -> game.levels.mastery(levelId)
        else -> null
    }
    private val endlessGen: EndlessGenerator? = when (mode) {
        Mode.ENDLESS -> EndlessGenerator(System.nanoTime())
        Mode.WEEKLY -> EndlessGenerator(session.weekKey() * 7_919L + 13, WeeklyChallenge.theme(session.weekKey()).second)
        else -> null
    }
    private val track: Track = level?.let { Track.fromLevel(it) } ?: Track(endlessGen!!.speedFor(0))
    private var chunkEnd = 0f
    private var chunkIndex = 0
    private val world: RunWorld
    private val cam = OrthographicCamera()
    private val viewport = ExtendViewport(WorldRenderer.W, 16f, cam)
    private var state = State.READY
    private var countdown = 3f
    private var lastBeep = 4
    private var accumulator = 0f
    private var theme: Theme
    private val events = ArrayList<RunEvent>()
    private var shake = 0f
    private var flash = 0f
    private var prevGateColor = -1
    private var magnet = false
    private val look get() = WorldRenderer.Look(
        session.equipped(ShopCategory.CHARACTER), session.equipped(ShopCategory.COSTUME).style,
        session.equipped(ShopCategory.TRAIL).style, session.data.settings.colorBlindSymbols, session.data.settings.reducedMotion,
    )
    private val tapFx get() = session.equipped(ShopCategory.COLOR_FX).style
    private val gateFx get() = session.equipped(ShopCategory.GATE_FX).style

    // HUD
    private lateinit var progress: ProgressBarActor
    private lateinit var scoreLabel: Label
    private lateinit var coinLabel: Label
    private lateinit var comboLabel: Label
    private lateinit var centerLabel: Label
    private var hintCard: Table? = null

    override val showBanner: Boolean = false

    override val music: String = when (mode) {
        Mode.ENDLESS, Mode.WEEKLY -> "endless"
        else -> AreaCatalog.world(AreaCatalog.area(level!!.area).world).music
    }

    init {
        if (endlessGen != null) appendChunks(0f)
        world = RunWorld(track, level?.startColor ?: MathUtils.random(3), endless = level == null)
        theme = themeFor(0)
        game.world.resetRun(world.color)
        root.touchable = Touchable.childrenOnly
        if (mode == Mode.LEVEL || mode == Mode.MASTERY) {
            if (!session.startLevel(levelId, mode == Mode.MASTERY)) state = State.ENDED
        }
    }

    private fun themeFor(chunk: Int): Theme {
        val areaId = level?.area ?: (((chunk / 4) * 5) % 100 + 1).coerceIn(1, 100)
        val a = AreaCatalog.area(areaId)
        return Theme(a, AreaCatalog.world(a.world))
    }

    private fun appendChunks(fromY: Float) {
        val gen = endlessGen ?: return
        while (chunkEnd < fromY + 45f) {
            val ch = gen.chunk(chunkIndex++)
            track.append(ch, chunkEnd)
            chunkEnd += ch.length
        }
    }

    override fun extraInput(): InputProcessor = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (state == State.PLAYING) { doTap(); return true }
            return false
        }
        override fun keyDown(keycode: Int): Boolean {
            if (keycode == com.badlogic.gdx.Input.Keys.SPACE && state == State.PLAYING) { doTap(); return true }
            return false
        }
    }

    private fun doTap() {
        val before = world.color
        world.tap()
        if (world.color != before) {
            game.audio.play("tap", 0.9f + world.color * 0.07f, 0.8f)
            game.world.tapEffect(tapFx, world.color, world.y)
        }
    }

    override fun build() {
        if (state == State.ENDED) { // can yok
            Gdx.app.postRunnable { game.go({ LivesScreen(game) }, remember = false) }
            return
        }
        val hud = Table()
        hud.pad(18f, 18f, 0f, 18f)
        hud.add(ui.iconButton("pause", 92f) { pause() }).size(92f)
        val mid = Table()
        mid.background = ui.pill(Pal.hex(0xFFFFFF, 0.85f))
        mid.pad(6f, 24f, 8f, 24f)
        if (level != null) {
            val a = AreaCatalog.area(level.area)
            val title = (if (mode == Mode.MASTERY) L.t("game.ustalik") else "") + L.f("common.bolum", level.id) + if (level.isFinal) L.t("game.final") else ""
            mid.add(ui.label(title, Ui.Size.M)).row()
            progress = ProgressBarActor(ui, 0f, if (level.isFinal) Pal.ORANGE else Pal.TURQ).also { it.setSize(330f, 24f) }
            mid.add(progress).size(330f, 24f).padTop(4f).row()
            mid.add(ui.label(a.name, Ui.Size.S, Pal.TEXT_SOFT))
            scoreLabel = ui.label("", Ui.Size.S)
        } else {
            scoreLabel = ui.label("0", Ui.Size.XL)
            mid.add(ui.label(if (mode == Mode.WEEKLY) WeeklyChallenge.theme(session.weekKey()).first else L.t("game.sonsuz"), Ui.Size.S, Pal.TEXT_SOFT)).row()
            mid.add(scoreLabel).row()
            progress = ProgressBarActor(ui, 0f, Pal.TURQ)
        }
        hud.add(mid).expandX()
        val right = Table()
        right.background = ui.pill(Pal.WHITE)
        right.pad(4f, 8f, 4f, 18f)
        right.add(ui.icon("coin", 44f)).size(44f).padRight(6f)
        coinLabel = ui.label("0", Ui.Size.M)
        right.add(coinLabel)
        hud.add(right).width(130f)
        root.add(hud).growX().top().row()
        comboLabel = ui.label("", Ui.Size.L, Pal.ORANGE)
        root.add(comboLabel).padTop(10f).row()
        centerLabel = ui.label("", Ui.Size.HUGE, Pal.TEXT)
        root.add(centerLabel).expand().row()
        root.add().height(200f)
        showReady()
    }

    /** Başlangıç kartı: yeni mekanik ipucu ve (sonsuzda) güçlendirme seçimi. */
    private fun showReady() {
        val card = ui.card(pad = 34f)
        val intros: List<Mechanic> = when {
            level == null -> emptyList()
            level.index == 0 && mode == Mode.LEVEL -> AreaCatalog.area(level.area).introduces
            else -> emptyList()
        }
        if (level != null) {
            val a = AreaCatalog.area(level.area)
            if (level.isFinal) card.add(ui.label(L.t("common.alan_finali"), Ui.Size.M, Pal.ORANGE)).row()
            card.add(ui.label(if (level.isFinal) a.finalTitle else L.f("common.bolum", level.id), Ui.Size.XL)).padBottom(6f).row()
            card.add(ui.stars(if (mode == Mode.MASTERY) session.data.masteryStars[level.id] ?: 0 else session.stars(level.id), size = 46f)).padBottom(10f).row()
            val goals = Table()
            goals.add(ui.icon("star_full", 34f)).size(34f).padRight(8f); goals.add(ui.label(L.t("game.bitir"), Ui.Size.S, Pal.TEXT_SOFT, Align.left)).left().row()
            goals.add(ui.icon("coin", 34f)).size(34f).padRight(8f); goals.add(ui.label(L.f("game.en_az_altin_topla", level.coinTarget), Ui.Size.S, Pal.TEXT_SOFT, Align.left)).left().row()
            goals.add(ui.icon("palette", 34f)).size(34f).padRight(8f); goals.add(ui.label(L.f("game.en_fazla_dokunus", level.tapPar), Ui.Size.S, Pal.TEXT_SOFT, Align.left)).left().row()
            card.add(goals).padBottom(12f).row()
            for (m in intros.take(2)) {
                val t = Table(); t.background = ui.panel(Pal.hex(0xEAF6FF)); t.pad(16f)
                t.add(MechanicActor(game, m)).size(150f, 120f).padRight(14f)
                val tx = Table()
                tx.add(ui.label(L.f("game.yeni", m.title.lupper()), Ui.Size.M, Pal.BLUE, Align.left)).left().row()
                tx.add(ui.label(m.hint, Ui.Size.S, Pal.TEXT, Align.left, wrap = true)).width(360f).left()
                t.add(tx)
                card.add(t).padBottom(10f).row()
            }
            if (level.id <= 2 && mode == Mode.LEVEL) card.add(ui.label(L.t("game.ekrana_dokun_kirmizi_mavi"), Ui.Size.S, Pal.TEXT_SOFT, wrap = true)).width(520f).padBottom(10f).row()
        } else {
            card.add(ui.label(if (mode == Mode.WEEKLY) L.t("common.haftalik_mucadele") else L.t("game.sonsuz_mod"), Ui.Size.XL)).row()
            if (mode == Mode.WEEKLY) card.add(ui.label(WeeklyChallenge.theme(session.weekKey()).first, Ui.Size.M, Pal.ORANGE)).row()
            card.add(ui.label(L.f("game.en_iyi", if (mode == Mode.WEEKLY) session.data.weekly.best else session.data.endlessBest), Ui.Size.M, Pal.TEXT_SOFT)).padBottom(10f).row()
            card.add(ui.label(L.t("game.hiz_surekli_artar_kombo"), Ui.Size.S, Pal.TEXT_SOFT, wrap = true)).width(500f).padBottom(12f).row()
            val boosts = Table()
            if (session.count("boost_endless_shield") > 0) {
                lateinit var b: com.badlogic.gdx.scenes.scene2d.ui.Button
                b = ui.button(L.f("game.kalkan", session.count("boost_endless_shield")), Pal.BLUE, "shield", Ui.Size.S) {
                    if (session.useConsumable("boost_endless_shield")) { world.shields++; b.isDisabled = true; game.audio.play("shield") }
                }
                boosts.add(b).pad(6f)
            }
            if (session.count("boost_magnet") > 0) {
                lateinit var b: com.badlogic.gdx.scenes.scene2d.ui.Button
                b = ui.button(L.f("game.altin_2", session.count("boost_magnet")), Pal.ORANGE, "coin", Ui.Size.S) {
                    if (session.useConsumable("boost_magnet")) { magnet = true; b.isDisabled = true; game.audio.play("purchase") }
                }
                boosts.add(b).pad(6f)
            }
            card.add(boosts).padBottom(6f).row()
        }
        lateinit var ov: Table
        card.add(ui.button(L.t("common.basla"), Pal.GREEN, "play") { ov.remove(); hintCard = null; startCountdown() }).width(400f).padTop(8f)
        ov = ui.overlay(stage, card, 0.25f)
        hintCard = ov
    }

    private fun startCountdown() { state = State.COUNTDOWN; countdown = 3f; lastBeep = 4 }

    override fun pause() {
        if (state == State.PLAYING || state == State.COUNTDOWN) {
            state = State.PAUSED
            showPause()
        }
    }

    private fun showPause() {
        val card = ui.card(pad = 40f)
        lateinit var ov: Table
        card.add(ui.label(L.t("common.duraklatildi"), Ui.Size.XL)).padBottom(24f).row()
        card.add(ui.button(L.t("common.devam_et"), Pal.GREEN, "play") { ov.remove(); state = State.COUNTDOWN; countdown = 3f; lastBeep = 4 }).width(440f).padBottom(14f).row()
        val st = session.data.settings
        val row = Table()
        row.add(ui.iconButton(if (st.musicVolume > 0f) "music_on" else "music_off", 100f) { st.musicVolume = if (st.musicVolume > 0f) 0f else 0.6f; game.audio.applyVolume(); ov.remove(); showPause() }).size(100f).pad(8f)
        row.add(ui.iconButton(if (st.sfxVolume > 0f) "sound_on" else "sound_off", 100f) { st.sfxVolume = if (st.sfxVolume > 0f) 0f else 0.9f; ov.remove(); showPause() }).size(100f).pad(8f)
        card.add(row).padBottom(14f).row()
        val leaveText = if (level != null) L.t("game.cik_1_can_gider") else L.t("game.kosuyu_bitir")
        if (level != null) card.add(ui.button(L.t("game.yeniden_baslat"), Pal.ORANGE, "refresh") {
            ov.remove()
            ui.confirm(stage, L.t("game.yeniden_baslat_2"), L.t("game.bolumu_yeniden_baslatmak_basarisiz"), L.t("game.baslat"), yesColor = Pal.ORANGE,) {
                session.failLevel(levelId, world.gatesPassed, world.taps, world.coins)
                game.go({ if (session.lives.canPlay) GameScreen(game, mode, levelId) else LivesScreen(game) }, remember = false)
            }
        }).width(440f).padBottom(14f).row()
        card.add(ui.button(leaveText, Pal.RED, "home") {
            ov.remove()
            if (level != null) {
                ui.confirm(stage, L.t("game.bolumden_cik"), L.t("game.cikarsan_deneme_basarisiz_sayilir"), L.t("game.cik")) {
                    session.failLevel(levelId, world.gatesPassed, world.taps, world.coins)
                    game.go({ mapScreen(game, level.id, mode == Mode.MASTERY) }, remember = false)
                }
            } else endRun()
        }).width(440f)
        ov = ui.overlay(stage, card)
    }

    override fun onBack() { if (state == State.PLAYING || state == State.COUNTDOWN) pause() }

    private fun step(dt: Float) {
        world.update(dt)
        if (endlessGen != null) {
            appendChunks(world.y)
            val ch = ((chunkIndex - 2).coerceAtLeast(0))
            val nt = themeFor(ch)
            if (nt.area.id != theme.area.id) theme = nt
        }
        events.clear()
        world.drainEvents(events)
        for (e in events) handle(e)
    }

    private fun handle(e: RunEvent) {
        val fx = game.world
        when (e) {
            is RunEvent.GatePassed -> {
                val g = track.gates[e.gate]
                if (e.ok) {
                    game.audio.play("gate_ok", 1f + minOf(world.combo, 20) * 0.015f)
                    fx.gateEffect(gateFx, e.color, g.y, true)
                    if (level == null && world.combo > 0 && world.combo % 10 == 0) {
                        game.audio.play("combo"); comboLabel.setText(L.f("game.kombo", world.multiplier))
                        comboLabel.clearActions(); comboLabel.color.a = 1f; comboLabel.setScale(1f)
                        comboLabel.addAction(Actions.sequence(Actions.delay(1.2f), Actions.fadeOut(0.5f)))
                    }
                } else {
                    fx.gateEffect(gateFx, e.color, g.y, false)
                    if (e.shielded) { game.audio.play("shield"); ui.toast(stage, L.t("game.kalkan_seni_korudu"), "shield") }
                    else failInfo = describeRequirement(e.gate)
                }
                prevGateColor = e.color
            }
            is RunEvent.Coin -> if (e.collected) { game.audio.play("coin", 1f + MathUtils.random(0.1f)); fx.coinEffect(track.pickups[e.index].y) }
            is RunEvent.Diamond -> { game.audio.play("diamond"); fx.diamondEffect(track.pickups[e.index].y) }
            is RunEvent.Shield -> { game.audio.play("shield"); ui.toast(stage, L.t("game.kalkan_2"), "shield") }
            is RunEvent.Pad -> { game.audio.play("whoosh"); fx.tapEffect(2, e.color, world.y) }
            is RunEvent.ZoneEnter -> { val z = track.zones[e.zone]; if (z.type == com.yanlisrenk.logic.model.ZoneType.MIRROR) ui.toast(stage, L.t("game.ayna_sira_ters"), "refresh"); if (z.type == com.yanlisrenk.logic.model.ZoneType.LOCK) ui.toast(stage, L.t("game.kilitli_renk"), "lock") }
            is RunEvent.ZoneExit -> {}
            is RunEvent.Failed -> onFail()
            is RunEvent.Finished -> onFinish()
            is RunEvent.Tap -> {}
        }
    }

    private var failInfo = ""

    private fun describeRequirement(gateIndex: Int): String {
        val g = track.gates[gateIndex]
        val te = track.timeline.timeAt(g.y)
        val mask = g.validMask(te, prevGateColor)
        val names = (0..3).filter { mask and (1 shl it) != 0 }.map { GameColor.of(it).tr }
        return if (names.size == 1) L.f("game.kapi_istiyordu", names[0].lupper()) else L.f("game.kapi_istiyordu", names.joinToString(" / ") { it.lupper() })
    }

    private fun onFail() {
        state = State.ENDED
        shake = 0.45f; flash = 1f
        game.audio.play("gate_fail")
        if (session.data.settings.vibration) game.platform.vibrate(120)
        stage.addAction(Actions.delay(0.9f, Actions.run { if (level != null) showLevelFailed() else endRun() }))
    }

    private fun onFinish() {
        state = State.ENDED
        val lvl = level ?: return
        val stars = world.stars(lvl)
        val reward = session.completeLevel(LevelResult(lvl.id, mode == Mode.MASTERY, stars, world.taps, world.coins, world.diamonds, world.gatesPassed, world.mistakes))
        game.audio.play("level_complete")
        repeat(4) { k -> game.world.particles.emit(4.5f, world.y + 2f, 14, Pal.game[k], 7f, 0.16f, 1.4f, 3, -5f) }
        stage.addAction(Actions.delay(0.6f, Actions.run { showLevelComplete(stars, reward) }))
    }

    private fun showLevelComplete(stars: Int, reward: com.yanlisrenk.logic.Reward) {
        val lvl = level!!
        val card = ui.card(pad = 40f)
        lateinit var ov: Table
        card.add(ui.label(if (lvl.isFinal) L.t("game.alan_finali_gecildi") else L.t("game.bolum_tamam"), Ui.Size.XL, Pal.GREEN)).padBottom(10f).row()
        val st = Table()
        for (i in 0 until 3) {
            val img = ui.icon(if (i < stars) "star_full" else "star_empty", 110f)
            img.setOrigin(Align.center)
            img.isVisible = true
            img.color.a = 0f
            img.addAction(Actions.sequence(Actions.delay(0.25f + i * 0.3f), Actions.run { if (i < stars) game.audio.play("star", 1f + i * 0.12f) }, Actions.parallel(Actions.fadeIn(0.2f), Actions.sequence(Actions.scaleTo(1.4f, 1.4f, 0.12f), Actions.scaleTo(1f, 1f, 0.15f)))))
            st.add(img).size(110f).pad(6f)
        }
        card.add(st).padBottom(10f).row()
        val goals = Table()
        fun goal(ok: Boolean, text: String) { goals.add(ui.icon(if (ok) "check" else "close", 36f)).size(36f).padRight(10f); goals.add(ui.label(text, Ui.Size.S, Pal.TEXT, Align.left)).left().padBottom(4f).row() }
        goal(true, L.t("game.bolumu_bitir"))
        goal(world.coins >= lvl.coinTarget, L.f("game.altin", world.coins, lvl.coinTarget))
        goal(world.taps <= lvl.tapPar, L.f("game.dokunus_en_fazla", world.taps, lvl.tapPar))
        card.add(goals).padBottom(16f).row()
        val rw = Table()
        rw.add(ui.chip("coin", "+${reward.gold}")).pad(6f)
        if (reward.diamonds > 0) rw.add(ui.chip("diamond", "+${reward.diamonds}")).pad(6f)
        rw.add(ui.chip("sparkle", L.f("common.xp", reward.xp))).pad(6f)
        card.add(rw).padBottom(14f).row()
        doubleGoldButton(reward.gold, "lvl${lvl.id}")?.let { card.add(it).padBottom(14f).row() }
        val special = reward.areaCompleted > 0 || reward.levelUps.isNotEmpty() || reward.worldCompleted > 0
        val buttons = Table()
        buttons.add(ui.iconButton("refresh", 110f, Pal.WHITE) {
            ov.remove(); adNav({ if (session.lives.canPlay) GameScreen(game, mode, lvl.id) else LivesScreen(game) })
        }).size(110f).padRight(16f)
        buttons.add(ui.iconButton("map", 110f, Pal.WHITE) { adNav({ mapScreen(game, lvl.id, mode == Mode.MASTERY) }) }).size(110f).padRight(16f)
        val nextId = lvl.id + 1
        when {
            special -> buttons.add(ui.button(L.t("game.oduller"), Pal.ORANGE, "gift") { adNav({ RewardScreen(game, reward, lvl.id, mode == Mode.MASTERY) }) })
            mode == Mode.LEVEL && nextId <= 500 -> buttons.add(ui.button(L.t("game.sonraki"), Pal.GREEN, "play") {
                val nextArea = AreaCatalog.areaOfLevel(nextId)
                game.go({
                    when {
                        nextArea != lvl.area && nextArea !in session.data.areaIntroSeen -> AreaIntroScreen(game, nextArea)
                        AreaCatalog.area(nextArea).lastLevel == nextId -> AreaFinalScreen(game, nextId)
                        else -> GameScreen(game, Mode.LEVEL, nextId)
                    }
                }, remember = false)
            })
            else -> buttons.add(ui.button(L.t("common.devam"), Pal.GREEN, "play") { adNav({ mapScreen(game, lvl.id, mode == Mode.MASTERY) }) })
        }
        card.add(buttons)
        ov = ui.overlay(stage, card, 0.3f)
        flushAchievementToasts()
    }

    /** Sonuç ekranından çıkış: mola reklamı kurallara uygunsa önce o gösterilir. */
    private fun adNav(f: () -> BaseScreen) = game.adBreak { game.go(f, remember = false) }

    /** "Reklam izle, altını 2 katına çıkar" (isteğe bağlı). */
    private fun doubleGoldButton(amount: Int, tag: String): com.badlogic.gdx.scenes.scene2d.Actor? {
        if (amount <= 0 || game.rewardedLeftToday() <= 0 || !game.ads.isRewardedReady()) return null
        lateinit var b: com.badlogic.gdx.scenes.scene2d.ui.Button
        b = ui.button(L.f("game.reklam_izle_altin", amount), Pal.PURPLE, "film", Ui.Size.S) {
            if (b.isDisabled) return@button
            val started = game.watchRewarded {
                session.credit(com.yanlisrenk.logic.shop.Currency.GOLD, amount, "reklam 2x", "ad2x:$tag:${session.clock.now()}")
                game.audio.play("purchase"); ui.toast(stage, L.f("game.altin_3", amount), "coin")
                b.isDisabled = true
            }
            if (!started) ui.toast(stage, L.t("game.reklam_su_an_hazir"), "info")
        }
        return b
    }

    private fun showLevelFailed() {
        val lost = session.failLevel(levelId, world.gatesPassed, world.taps, world.coins)
        val card = ui.card(pad = 40f)
        card.add(ui.label(L.t("game.yanlis_renk"), Ui.Size.XL, Pal.RED)).padBottom(8f).row()
        if (failInfo.isNotEmpty()) card.add(ui.label(failInfo, Ui.Size.M, Pal.TEXT, wrap = true)).width(520f).padBottom(14f).row()
        val hearts = Table()
        for (i in 0 until 4) hearts.add(ui.icon(if (i < session.lives.lives) "heart_full" else "heart_empty", 64f)).size(64f).pad(3f)
        card.add(hearts).padBottom(6f).row()
        card.add(ui.label(if (lost) L.t("game.1_can_kaybettin") else L.t("game.can_kalkani_canini_korudu"), Ui.Size.S, Pal.TEXT_SOFT)).padBottom(6f).row()
        val pct = ((world.y / level!!.length) * 100).toInt().coerceIn(0, 99)
        card.add(ui.label(L.f("game.ilerleme_gecilen_kapi", pct, world.gatesPassed, level.gates.size), Ui.Size.S, Pal.TEXT_SOFT)).padBottom(20f).row()
        if (session.lives.canPlay) {
            card.add(ui.button(L.t("game.tekrar_dene"), Pal.GREEN, "refresh") { adNav({ GameScreen(game, mode, levelId) }) }).width(460f).padBottom(12f).row()
        } else {
            card.add(ui.button(L.t("common.canlar"), Pal.PINK, "heart_full", textColor = Pal.TEXT) { adNav({ LivesScreen(game) }) }).width(460f).padBottom(12f).row()
            card.add(ui.button(L.t("game.sonsuz_mod"), Pal.TURQ, "infinity") { adNav({ GameScreen(game, Mode.ENDLESS) }) }).width(460f).padBottom(12f).row()
        }
        card.add(ui.button(L.t("common.harita"), Pal.BLUE, "map") { adNav({ mapScreen(game, level.id, mode == Mode.MASTERY) }) }).width(460f)
        ui.overlay(stage, card, 0.3f)
    }

    private fun endRun() {
        if (state == State.ENDED && endShown) return
        state = State.ENDED
        endShown = true
        val r = session.completeEndless(world.score, world.gatesPassed, world.coins, world.maxCombo, world.taps, mode == Mode.WEEKLY, magnet)
        val card = ui.card(pad = 40f)
        card.add(ui.label(if (r.newBest || r.weeklyBest) L.t("game.yeni_rekor") else L.t("game.kosu_bitti"), Ui.Size.XL, if (r.newBest) Pal.ORANGE else Pal.TEXT)).padBottom(6f).row()
        if (failInfo.isNotEmpty()) card.add(ui.label(failInfo, Ui.Size.S, Pal.TEXT_SOFT, wrap = true)).width(500f).padBottom(8f).row()
        card.add(ui.label(world.score.toString(), Ui.Size.HUGE, Pal.TEXT)).row()
        card.add(ui.label(L.t("game.puan"), Ui.Size.S, Pal.TEXT_SOFT)).padBottom(10f).row()
        val stats = Table()
        fun s(k: String, v: String) { stats.add(ui.label(k, Ui.Size.S, Pal.TEXT_SOFT, Align.left)).left().padRight(30f); stats.add(ui.label(v, Ui.Size.M, Pal.TEXT, Align.right)).right().row() }
        s(L.t("common.gecilen_kapi"), world.gatesPassed.toString())
        s(L.t("common.en_yuksek_kombo"), world.maxCombo.toString())
        s(L.t("game.en_iyi_skor"), (if (mode == Mode.WEEKLY) session.data.weekly.best else session.data.endlessBest).toString())
        card.add(stats).padBottom(12f).row()
        val rw = Table(); rw.add(ui.chip("coin", "+${r.gold}")).pad(6f); rw.add(ui.chip("sparkle", L.f("common.xp", r.xp))).pad(6f)
        card.add(rw).padBottom(14f).row()
        doubleGoldButton(r.gold, "endless")?.let { card.add(it).padBottom(14f).row() }
        card.add(ui.button(L.t("game.tekrar"), Pal.GREEN, "refresh") { adNav({ GameScreen(game, mode) }) }).width(440f).padBottom(12f).row()
        card.add(ui.button(L.t("common.menu"), Pal.BLUE, "home") { game.clearHistory(); adNav({ MainMenuScreen(game) }) }).width(440f)
        ui.overlay(stage, card, 0.3f)
        flushAchievementToasts()
    }
    private var endShown = false

    /** QA/ekran görüntüsü: hazır kartını kapatıp [seconds] sn ileri sarar (kâhin oyuncuyla, kaybetmeden). */
    fun debugStart(seconds: Float, oracle: Boolean = true) {
        hintCard?.remove(); hintCard = null
        state = State.PLAYING
        this.oracle = oracle
        var t = 0f
        while (t < seconds && state == State.PLAYING) { if (oracle) oracleStep(); step(1f / 120f); t += 1f / 120f }
        centerLabel.setText("")
    }
    var oracle = false

    /** Sıradaki kapıyı geçerli renkte geçecek şekilde dokunur (yalnızca test/QA). */
    private fun oracleStep() {
        val next = track.gates.indexOfFirst { it.y > world.y }
        if (next < 0) return
        val g = track.gates[next]
        if (g.y - world.y > 1.2f) return
        val te = track.timeline.timeAt(g.y)
        var guard = 0
        while (!g.accepts(world.color, te, world.lastGateColor) && guard++ < 4) world.tap()
    }

    override fun render(delta: Float) {
        time += delta
        if (oracle && state == State.PLAYING) oracleStep()
        val dt = minOf(delta, 0.05f)
        when (state) {
            State.COUNTDOWN -> {
                countdown -= dt
                val n = kotlin.math.ceil(countdown).toInt()
                if (n != lastBeep && n >= 1) { lastBeep = n; game.audio.play("countdown"); centerLabel.setText(n.toString()); pop(centerLabel) }
                if (countdown <= 0f) { state = State.PLAYING; game.audio.play("go"); centerLabel.setText(L.t("game.basla").lupper()); pop(centerLabel); centerLabel.addAction(Actions.delay(0.5f, Actions.fadeOut(0.3f))) }
            }
            State.PLAYING -> {
                accumulator += dt
                val stepDt = 1f / 120f
                while (accumulator >= stepDt && state == State.PLAYING) { step(stepDt); accumulator -= stepDt }
            }
            else -> {}
        }
        game.world.update(if (state == State.PAUSED) 0f else dt, world)
        shake = maxOf(0f, shake - dt); flash = maxOf(0f, flash - dt * 2.5f)

        // HUD
        if (level != null) progress.fraction = (world.y / level.length).coerceIn(0f, 1f) else scoreLabel.setText(world.score.toString())
        coinLabel.setText(world.coins.toString())

        // dünya
        Gdx.gl.glClearColor(theme.skyBottom.r, theme.skyBottom.g, theme.skyBottom.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        viewport.apply()
        cam.position.set(WorldRenderer.W / 2 + (if (shake > 0f) MathUtils.random(-0.12f, 0.12f) * shake * 2 else 0f), world.y + cam.viewportHeight / 2 - WorldRenderer.PLAYER_SCREEN_Y, 0f)
        cam.update()
        game.world.render(cam, world, theme, look, time, level?.length)
        if (flash > 0f) {
            val sr = game.world.sr
            Gdx.gl.glEnable(GL20.GL_BLEND)
            sr.projectionMatrix = cam.combined
            sr.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled)
            sr.setColor(1f, 0.35f, 0.38f, flash * 0.35f)
            sr.rect(cam.position.x - cam.viewportWidth, cam.position.y - cam.viewportHeight, cam.viewportWidth * 2, cam.viewportHeight * 2)
            sr.end()
        }
        stage.viewport.apply()
        stage.act(delta)
        stage.draw()
    }

    private fun pop(l: Label) {
        l.clearActions(); l.color.a = 1f; l.setOrigin(Align.center); l.isVisible = true
        l.addAction(Actions.sequence(Actions.scaleTo(1.5f, 1.5f), Actions.scaleTo(1f, 1f, 0.3f, Interpolation.swingOut)))
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        viewport.update(width, height, false)
    }

    override fun hide() {
        if (state == State.PLAYING) { state = State.PAUSED }
    }
}
