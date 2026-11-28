package com.besaniyelik.dunya.ui.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.besaniyelik.dunya.BesSaniyelikDunya
import com.besaniyelik.dunya.audio.Sfx
import com.besaniyelik.dunya.endless.EndlessGenerator
import com.besaniyelik.dunya.endless.ScoreCalculator
import com.besaniyelik.dunya.level.*
import com.besaniyelik.dunya.progress.*
import com.besaniyelik.dunya.render.WorldRenderer
import com.besaniyelik.dunya.sim.*
import com.besaniyelik.dunya.ui.*
import com.besaniyelik.dunya.GameConfig
import com.besaniyelik.dunya.i18n.L
import com.besaniyelik.dunya.i18n.LevelText

/**
 * Oynanış ekranı. [level] null ise Sonsuz Dünya ([seed] ile). Simülasyon sabit 60 Hz adımla ilerler;
 * çizim ve arayüz simülasyonun durumunu okur.
 */
class GameScreen(
    game: BesSaniyelikDunya, private val level: LevelData?, private val seed: Long?, private val boosts: List<PowerupType>,
    /** Bu deneme için kalıcı can harcandı mı (bölüm geçilir ya da hiç kalp kaybetmeden çıkılırsa geri verilir). */
    private val lifeSpent: Boolean = false,
) : BaseScreen(game) {
    override val background: MenuBackground? = null
    override val toastTime = 1.0f
    override val toastCompact = true
    private val endless = level == null
    private val d = game.profile.data
    private val generator = if (endless) EndlessGenerator(seed ?: 1L, Mechanic.unlockedAt(d.highestUnlocked).toSet()) else null
    private val sim = Simulation(level, SimConfig(hearts = if (endless) GameConfig.ENDLESS_HEARTS else GameConfig.HEARTS_PER_ATTEMPT), generator)
    private val levelName = level?.let { LevelText.name(it) } ?: ""
    private var lifeRefunded = false
    private val map = level?.map ?: 10
    private val renderer = WorldRenderer(a, sim, map, Catalog.skin(d.selectedSkin), Catalog.trail(d.selectedTrail), d.settings.reducedEffects)

    /** Test/tanıtım için: kayıtlı çözümü gerçek giriş yerine oynatır. */
    var autoplay: InputTrack? = null
    /** Yalnızca masaüstü test turu için: simülasyonu hızlandırır (oyunda her zaman 1). */
    var timeScale = 1f
    val simulation get() = sim
    private var acc = 0f
    private var pressed = false
    private var paused = true
    private var started = false
    private var finished = false
    private var jumps = 0
    private var frame = 0
    private var hintIdx = 0
    private var waitHint: HintDef? = null
    private var completeTimer = -1f
    private var overShown = false
    private val seenRules = HashSet<Mechanic>()

    // HUD
    private val hearts = Table()
    private val coinLabel by lazy { kit.label("0", a.f36o, Color.WHITE) }
    private val starSlots = Table()
    private val progress by lazy { kit.bar(0f, "bar_fill_y", 22f) }
    private val ruleIcons = Table()
    private val ruleTime by lazy { kit.label("5", a.f36o, Color.WHITE) }
    private val ruleBar by lazy { kit.bar(1f, "bar_fill_b", 16f) }
    private val nextIcons = Table()
    private val scoreLabel by lazy { kit.label("", a.f36o, Color.WHITE) }
    private val hintBox = Table()
    private val tint by lazy { Image(a.ui("white")).apply { touchable = Touchable.disabled; color = Color(1f, 1f, 1f, 0f) } }
    private val powerRow = Table()
    private var startOverlay: Table? = null

    init {
        sim.listener = object : SimListener {
            override fun onJump(air: Boolean) { jumps++; game.audio.play(Sfx.JUMP, if (air) 1.25f else 1f); renderer.onJump() }
            override fun onLand(impact: Float) { if (impact > 0.4f) game.audio.play(Sfx.LAND); renderer.onLand(impact) }
            override fun onCoin(c: Collectible, value: Int) { game.audio.play(Sfx.COIN, 1f + (sim.coinCount % 5) * 0.04f); renderer.onCoin(c) }
            override fun onStar(c: Collectible) { game.audio.play(Sfx.STAR); renderer.onStar(c); game.vibrate(20) }
            override fun onPiece(c: Collectible) { game.audio.play(Sfx.REWARD); renderer.onPiece(c); toast(L.t("game.piece_found"), "b_puzzle") }
            override fun onPowerup(type: PowerupType) { game.audio.play(Sfx.POWERUP); renderer.onPowerup(); if (started) toast(type.title, "b_" + type.icon) }
            override fun onRuleWarning(next: List<Mechanic>) { game.audio.play(Sfx.WARN); showNext(next) }
            override fun onRuleChange(prev: List<Mechanic>, next: List<Mechanic>) { game.audio.play(Sfx.RULE); renderer.onRule(); ruleBanner(next); game.vibrate(25); checkEndlessTutorial(next) }
            override fun onSpring() { game.audio.play(Sfx.SPRING); renderer.onSpring() }
            override fun onBoost() { game.audio.play(Sfx.BOOST) }
            override fun onCheckpoint(x: Float) { game.audio.play(Sfx.CHECKPOINT); renderer.onCheckpoint(x) }
            override fun onShieldSave() { game.audio.play(Sfx.SHIELD); toast(L.t("game.shield_saved"), "b_p_shield") }
            override fun onDeath(cause: DeathCause) { game.audio.play(Sfx.DEATH); renderer.onDeath(); game.vibrate(60); deathText(cause) }
            override fun onRespawn() { refreshHearts(); refreshRules(); ruleBanner(sim.activeRules); rewindHints(); readyHold(0.8f) }
            override fun onComplete() { game.audio.play(Sfx.COMPLETE); renderer.celebrate(); completeTimer = 1.4f }
            override fun onGameOver() { game.audio.play(Sfx.FAIL); refreshHearts() }
        }
        boosts.forEach { sim.activatePowerup(it) }
        input.addProcessor(object : InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean { press(true); return true }
            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean { if (!Gdx.input.isTouched) press(false); return true }
            override fun keyDown(keycode: Int): Boolean { if (keycode == Input.Keys.SPACE || keycode == Input.Keys.UP || keycode == Input.Keys.W) { press(true); return true }; return false }
            override fun keyUp(keycode: Int): Boolean { if (keycode == Input.Keys.SPACE || keycode == Input.Keys.UP || keycode == Input.Keys.W) { press(false); return true }; return false }
        })
    }

    /**
     * Yeniden doğuşta kısa hazırlık duraklaması: oyuncu karakteri ekranda bulsun. Simülasyon bu sürede ilerlemez
     * (yeniden doğuş dokunulmazlığı da duraklama bitince saymaya başlar).
     */
    private var respawnHold = 0f
    private var readyLabel: Label? = null
    private fun readyHold(seconds: Float) {
        respawnHold = seconds
        pressed = false
        acc = 0f
        readyLabel?.remove()
        val l = kit.label(L.t("game.get_ready"), a.f36o, Color.WHITE)
        readyLabel = l
        l.pack(); l.setPosition((stage.width - l.width) / 2, stage.height * 0.6f)
        l.addAction(Actions.sequence(Actions.delay(seconds * 0.6f), Actions.fadeOut(seconds * 0.4f), Actions.removeActor()))
        stage.addActor(l)
    }

    private fun press(v: Boolean) {
        if (v && !started && !finished && startOverlay != null) { begin(); return }
        pressed = v
        if (v && waitHint != null) clearWait()
    }

    /**
     * Yeniden doğuşta ipuçları geri sarılır: bekleyen "dokun" ipucu (ağır çekim) kapanır ve oyuncunun önünde kalan
     * ipuçları yeniden gösterilir. Aksi halde eğitim bölümünde ölen oyuncu başlangıçta ağır çekimde kalıyordu.
     */
    private fun rewindHints() {
        val hints = level?.hints ?: return
        if (waitHint != null) { waitHint = null; hintBox.clearActions(); hintBox.addAction(Actions.fadeOut(0.2f)) }
        hintIdx = hints.indexOfFirst { it.x > sim.player.x }.let { if (it < 0) hints.size else it }
    }

    private fun clearWait() { waitHint = null; hintBox.clearActions(); hintBox.addAction(Actions.sequence(Actions.delay(0.6f), Actions.fadeOut(0.3f))) }

    override fun build() {
        game.audio.music(if (endless) "endless" else "map_%02d".format(map))
        stage.addActor(tint)
        root.touchable = Touchable.childrenOnly
        // yatay ekran: tek üst satır — solda duraklat ve canlar, ortada kural sayacı, sağda ilerleme ve toplananlar
        val top = Table()
        val leftBox = Table()
        leftBox.add(kit.iconButton("g_pause", "white", 80f, 40f) { openPauseMenu() }).size(80f).padRight(10f)
        leftBox.add(hearts)
        top.add(leftBox).left().padLeft(16f).width(340f)
        val rp = Table().apply { background = kit.patch("chip_dark"); pad(6f, 14f, 8f, 14f) }
        rp.add(ruleIcons).padRight(10f)
        val rt = Table(); rt.add(ruleTime).row(); rt.add(ruleBar).width(130f).height(14f)
        rp.add(rt)
        rp.add(nextIcons).padLeft(10f)
        val center = Table()
        center.add(rp).row()
        center.add(powerRow).padTop(4f)
        top.add(center).expandX().top()
        val rightBox = Table()
        val prog = Table()
        if (endless) prog.add(scoreLabel) else {
            prog.add(kit.label(levelName, a.f22, Palette.INK)).row()
            prog.add(progress).width(220f).height(18f).padTop(4f)
        }
        rightBox.add(prog).padRight(14f)
        val coll = Table()
        coll.add(Image(a.ui("b_coin"))).size(42f); coll.add(coinLabel).padLeft(4f).minWidth(56f).row()
        coll.add(starSlots).colspan(2).right()
        rightBox.add(coll)
        top.add(rightBox).right().padRight(16f).width(340f)
        root.add(top).growX().padTop(12f).row()
        hintBox.color.a = 0f
        root.add(hintBox).padTop(10f).row()
        root.add().expandY()
        refreshHearts(); refreshStars(); refreshRules()
        showStartOverlay()
    }

    private fun showStartOverlay() {
        val o = Table().apply { setFillParent(true); touchable = Touchable.disabled }
        val p = kit.panel()
        val tut = level?.tutorial
        if (tut != null && level.kind == LevelKind.TUTORIAL) {
            p.add(kit.label(L.t("game.new_rule"), a.f28, Palette.INK_SOFT)).row()
            p.add(Image(a.ui("b_" + tut.icon))).size(100f).padTop(6f).row()
            p.add(kit.label(tut.title, a.f48, Palette.INK, wrap = true)).width(540f).padTop(6f).row()
            p.add(kit.label(tut.hint, a.f28, Palette.INK_SOFT, wrap = true)).width(520f).padTop(6f).row()
            p.add(kit.label(tut.detail, a.f22, Palette.INK_SOFT, wrap = true)).width(520f).padTop(10f).row()
            game.profile.markTutorialSeen("m_" + tut.name)
        } else if (level?.id == 1) {
            p.add(brandLogo(420f)).row()
            p.add(kit.label(L.t("game.intro"), a.f28, Palette.INK, wrap = true)).width(520f).padTop(10f).row()
        } else {
            p.add(kit.label(if (endless) L.t("endless.title") else levelName, a.f48, Palette.INK, wrap = true)).width(540f).row()
            if (!endless) p.add(kit.stars(d.stars(level!!.id), 54f)).padTop(8f).row()
        }
        p.add(Image(a.ui("g_tap")).apply { color = Palette.ORANGE }).size(70f).padTop(10f).row()
        p.add(kit.label(L.t("game.tap_to_start"), a.f36, Palette.ORANGE)).padTop(4f)
        o.add(p).width(620f)
        stage.addActor(o)
        p.popIn()
        startOverlay = o
    }

    private fun begin() {
        startOverlay?.addAction(Actions.sequence(Actions.fadeOut(0.2f), Actions.removeActor()))
        startOverlay = null
        started = true; paused = false
        ruleBanner(sim.activeRules)
    }

    // ------------------------------------------------------------ döngü
    override fun renderWorld(dt: Float) {
        frame++
        if (!paused && !finished) {
            // etkileşimli eğitim: ipucunda dokunuş beklenirken ağır çekim
            val slow = waitHint != null
            if (respawnHold > 0f) respawnHold -= dt
            else if (!slow || frame % 7 == 0) {
                acc += if (slow) Physics.TICK else dt * timeScale
                var steps = 0
                val maxSteps = (5 * timeScale).toInt()
                while (acc >= Physics.TICK && steps < maxSteps) {
                    val ap = autoplay
                    if (ap != null) { pressed = ap.at(sim.tick); if (pressed && waitHint != null) clearWait() }
                    sim.step(pressed)
                    acc -= Physics.TICK; steps++
                }
                if (steps == maxSteps) acc = 0f
            }
            checkHints()
            if (endless && frame % 120 == 0) sim.pruneBefore(sim.player.x - 30f)
        }
        renderer.update(if (paused) 0f else dt)
        renderer.render(game.batch)
        updateHud(dt)
        // Bölüm bitti: kutlama (1,4 sn, giriş yok) -> seyrek ve yalnızca koşullar uygunsa geçiş reklamı -> sonuç ekranı.
        // Reklam bir düğmeye basılınca değil bu doğal arada gösterilir; sonuç ekranındaki düğmeler her zaman anında çalışır.
        // Sonuç, reklamdan ÖNCE kaydedilir: reklam sırasında uygulama kapansa da ilerleme kaybolmaz.
        if (completeTimer > 0f) { completeTimer -= dt; if (completeTimer <= 0f) { commitComplete(); game.ads.onBreakEarned(); game.ads.atBreak { showComplete() } } }
        if (sim.status == SimStatus.GAME_OVER && !overShown) { overShown = true; stage.addAction(Actions.delay(0.6f, Actions.run { showGameOver() })) }
    }

    override fun resize(width: Int, height: Int) { super.resize(width, height); renderer.resize(width, height) }

    private fun checkHints() {
        val hints = level?.hints ?: return
        while (hintIdx < hints.size && sim.player.x >= hints[hintIdx].x) {
            val h = hints[hintIdx++]
            showHint(h)
            if (h.waitTap && !pressed) waitHint = h
        }
    }

    private fun showHint(h: HintDef) {
        hintBox.clear(); hintBox.clearActions()
        hintBox.background = kit.patch("sign"); hintBox.pad(18f, 26f, 24f, 26f)
        val icon = when { h.icon.startsWith("m_") -> "b_" + h.icon; h.icon == "i_tap" -> "g_tap"; h.icon == "i_hold" -> "g_hold"; h.icon == "i_run" -> "g_run"; h.icon == "i_star" -> "b_star"; h.icon == "i_spring" -> "b_m_bouncy"; h.icon == "i_boost" -> "b_m_fast"; h.icon == "i_saw" || h.icon == "i_spike" -> "b_heart"; else -> "b_star" }
        val img = Image(a.ui(icon)); if (icon.startsWith("g_")) img.color = Palette.ORANGE
        hintBox.add(img).size(64f).padRight(14f)
        hintBox.add(kit.label(LevelText.hint(h), a.f28, Palette.INK, wrap = true)).width(400f)
        hintBox.color.a = 0f
        hintBox.addAction(Actions.sequence(Actions.fadeIn(0.25f), Actions.delay(if (h.waitTap) 30f else 1.8f), Actions.fadeOut(0.3f)))
    }

    private var lastHearts = -1
    private var lastStars = -1
    private var lastRules: List<Mechanic>? = null
    private var lastCoins = -1
    private var lastPower = ""

    private fun updateHud(dt: Float) {
        if (sim.coinValue != lastCoins) { lastCoins = sim.coinValue; coinLabel.setText(sim.coinValue.toString()) }
        if (sim.hearts != lastHearts) refreshHearts()
        if (sim.starsCollected != lastStars) refreshStars()
        if (sim.activeRules != lastRules) refreshRules()
        if (!endless) progress.value = ((sim.player.x - sim.startX) / (sim.goalX - sim.startX)).coerceIn(0f, 1f)
        else scoreLabel.setText(L.n("unit.points", score()))
        // Sayaç bir sonraki GERÇEK kural değişimine kalan süreyi gösterir; bitiş düzlüğünde kapıya kalan süreyi.
        val toRule = sim.secondsToNextRule()
        val s = if (toRule.isFinite() || endless) toRule else sim.secondsToGoal()
        if (s.isFinite()) { ruleTime.setText(L.dec(s)); ruleBar.value = 1f - sim.phaseProgress() } else { ruleTime.setText("—"); ruleBar.value = 0f }
        ruleTime.color = if (toRule < Physics.WARNING_TIME) Palette.SUN else Color.WHITE
        // aktif güçlendirmeler
        val pw = buildString {
            if (sim.shield > 0) append("S"); if (sim.magnet > 0) append("M"); if (sim.doubleJump > 0) append("D"); if (sim.player.invuln > 1.5f) append("I")
            if (sim.coinMul > 0) append("C"); if (sim.finder > 0) append("F"); if (sim.guard) append("G")
        }
        if (pw != lastPower) {
            lastPower = pw
            powerRow.clear()
            val map = mapOf('S' to "p_shield", 'M' to "p_magnet", 'D' to "p_double", 'I' to "p_invincible", 'C' to "p_coinx2", 'F' to "p_finder", 'G' to "p_guard")
            pw.forEach { powerRow.add(Image(a.ui("b_" + map[it]))).size(56f).pad(2f) }
        }
        // kural renk tonu
        val e = sim.effects
        val target = when {
            e.timeScale < 1f -> Color(0.6f, 0.65f, 1f, 0.10f)
            e.ice -> Color(0.75f, 0.95f, 1f, 0.12f)
            e.ghost -> Color(0.8f, 0.65f, 1f, 0.10f)
            e.reverseControl -> Color(1f, 0.55f, 0.8f, 0.08f)
            e.timeScale > 1f -> Color(1f, 0.8f, 0.5f, 0.07f)
            else -> Color(1f, 1f, 1f, 0f)
        }
        tint.setBounds(-1000f, -1000f, 4000f, 4000f)
        if (tint.actions.size == 0) tint.color.lerp(target, kotlin.math.min(1f, dt * 3f))
    }

    private fun score() = ScoreCalculator.endless(sim.distance, sim.tick / 60f, sim.coinValue, sim.starsCollected, sim.ruleBonus)

    private fun refreshHearts() {
        lastHearts = sim.hearts
        hearts.clear()
        val total = kotlin.math.max(sim.hearts, if (endless) 1 else 3)
        for (i in 0 until total) hearts.add(Image(a.ui(if (i < sim.hearts) "b_heart" else "b_heart_empty"))).size(42f).padRight(2f)
    }

    private fun refreshStars() {
        lastStars = sim.starsCollected
        starSlots.clear()
        if (endless) { starSlots.add(Image(a.ui("b_star"))).size(38f); starSlots.add(kit.label(" ${sim.starsCollected}", a.f28, Color.WHITE)); return }
        for (i in 0 until 3) starSlots.add(Image(a.ui(if (i < sim.starsCollected) "b_star" else "b_star_empty"))).size(34f)
    }

    private fun refreshRules() {
        lastRules = sim.activeRules
        ruleIcons.clear()
        sim.activeRules.forEach { ruleIcons.add(Image(a.ui("b_" + it.icon))).size(54f).pad(2f) }
        nextIcons.clear()
    }

    private fun showNext(next: List<Mechanic>) {
        nextIcons.clear()
        nextIcons.add(Image(a.ui("g_next"))).size(32f).padRight(4f)
        next.forEach { val i = Image(a.ui("b_" + it.icon)); nextIcons.add(i).size(48f).pad(2f); i.addAction(Actions.forever(Actions.sequence(Actions.alpha(0.4f, 0.15f), Actions.alpha(1f, 0.15f)))) }
    }

    /**
     * Kural değişim bildirimi: küçük, ekranın üst kısmında (HUD'un altında) ve kısa süreli (~0,9 sn).
     * Oyun alanının ortasını kapatmaz; ayrıntılı açıklama yalnızca bir kuralı ilk kez gördüğünde gösterilir.
     */
    private var activeBanner: Table? = null
    private val bannerSeen = HashSet<Mechanic>()
    private fun ruleBanner(rules: List<Mechanic>) {
        activeBanner?.remove()
        val first = rules.any { it !in bannerSeen }
        bannerSeen.addAll(rules)
        val b = Table().apply { background = kit.patch("chip"); pad(6f, 16f, 10f, 20f); touchable = Touchable.disabled }
        rules.forEach { b.add(Image(a.ui("b_" + it.icon))).size(46f).padRight(4f) }
        b.add(kit.label(rules.joinToString(" + ") { it.title }, a.f22, Palette.INK)).padLeft(6f)
        b.pack()
        b.setPosition((stage.width - b.width) / 2, stage.height - 300f)
        b.color.a = 0f
        val hold = if (first) 0.9f else 0.55f
        b.addAction(Actions.sequence(Actions.fadeIn(0.1f), Actions.delay(hold), Actions.fadeOut(0.25f), Actions.removeActor()))
        stage.addActor(b)
        activeBanner = b
        tint.clearActions()
        tint.color.set(1f, 1f, 1f, 0.18f)
        tint.addAction(Actions.alpha(0f, 0.25f))
    }

    private fun deathText(cause: DeathCause) {
        val l = kit.label(cause.title, a.f36o, Color.WHITE)
        l.pack(); l.setPosition((stage.width - l.width) / 2, stage.height * 0.6f)
        l.addAction(Actions.sequence(Actions.delay(0.5f), Actions.fadeOut(0.2f), Actions.removeActor()))
        stage.addActor(l)
    }

    /** Sonsuz modda henüz öğrenilmemiş bir kural geldiğinde kısa öğretici gösterilir. */
    private fun checkEndlessTutorial(rules: List<Mechanic>) {
        if (!endless) return
        val unseen = rules.firstOrNull { it != Mechanic.NORMAL && it.unlockLevel > d.highestUnlocked && "m_${it.name}" !in d.seenTutorials && it !in seenRules }
            ?: return
        seenRules.add(unseen)
        game.profile.markTutorialSeen("m_${unseen.name}")
        paused = true
        dialog { p, close ->
            p.add(kit.label(L.t("game.new_rule"), a.f28, Palette.INK_SOFT)).row()
            p.add(Image(a.ui("b_" + unseen.icon))).size(120f).row()
            p.add(kit.label(unseen.title, a.f48, Palette.INK)).row()
            p.add(kit.label(unseen.detail, a.f28, Palette.INK_SOFT, wrap = true)).width(540f).padTop(8f).row()
            p.add(kit.button(L.t("common.got_it"), "green", "g_check") { close(); paused = false; pressed = false }).width(360f).height(116f).padTop(20f)
        }
    }

    // ------------------------------------------------------------ menüler
    private fun openPauseMenu() {
        if (finished || !started || paused || sim.status == SimStatus.COMPLETE) return
        paused = true
        pressed = false
        game.audio.pause()
        dialog { p, close ->
            p.add(kit.label(L.t("pause.title"), a.f48, Palette.INK)).row()
            if (!endless) p.add(kit.label(levelName, a.f28, Palette.INK_SOFT)).padTop(4f).row()
            val st = d.settings
            val toggles = Table()
            toggles.add(kit.iconButton(if (st.music) "g_music" else "g_music_off", "pink", 100f, 54f) { st.music = !st.music; game.audio.applyVolume(); game.profile.save(); close(); paused = false; openPauseMenu() }).size(100f).pad(8f)
            toggles.add(kit.iconButton(if (st.sound) "g_sound" else "g_sound_off", "turq", 100f, 54f) { st.sound = !st.sound; game.profile.save(); close(); paused = false; openPauseMenu() }).size(100f).pad(8f)
            p.add(toggles).padTop(10f).row()
            p.add(kit.button(L.t("pause.resume"), "green", "g_play") { close(); paused = false; game.audio.resume() }).width(460f).height(96f).padTop(10f).row()
            p.add(kit.button(L.t("pause.restart"), "yellow", "g_retry") { close(); restart() }).width(460f).height(96f).padTop(8f).row()
            p.add(kit.button(if (endless) L.t("common.main_menu") else L.t("common.levels"), "white", "g_home", a.f36, Palette.INK) { close(); quit() }).width(460f).height(96f).padTop(8f).row()
            // Kalıcı can: bu denemede kalp kaybedildiyse çıkış canı geri getirmez (oyuncu önceden bilsin)
            if (lifeSpent && !lifeRefunded && sim.deaths > 0) p.add(kit.label(L.t("lives.quit_warning"), a.f22, Palette.INK_SOFT, wrap = true)).width(460f).padTop(10f)
        }
    }

    override fun pause() {
        // "Reklamla devam" sorusu açıkken uygulama arka plana alınırsa (ya da reklam açılırsa) koşu kaybolmasın:
        // sonuç kayıt dosyasında bekletilir; devam edilirse bırakılır, edilmezse işlenir.
        if (endless && sim.status == SimStatus.GAME_OVER && !endlessCommitted) game.profile.holdEndless(endlessResult())
        if (started && !paused && !finished) openPauseMenu()
    }

    private fun endlessResult() = EndlessResult(score(), sim.tick / 60f, sim.distance, sim.coinValue, sim.starsCollected, jumps, sim.rulesChanged, sim.powerupsUsed)

    override fun onBack() {
        // başlangıç kartı açıkken geri tuşu bölüm seçimine döner (duraklatma menüsü henüz açılamaz)
        if (!started && !finished) { quit(); return }
        if (paused && started) return
        openPauseMenu()
    }

    /**
     * Denemeden ayrılırken: bölüm geçildiyse ya da bu denemede hiç kalp kaybedilmediyse harcanan can geri verilir
     * (yanlışlıkla açılan bölümden çıkmak can götürmez; yalnızca başarısız denemeler götürür).
     */
    private fun refundLifeIfFair() {
        if (!lifeSpent || lifeRefunded) return
        val fair = sim.status == SimStatus.COMPLETE || (sim.deaths == 0 && sim.status != SimStatus.GAME_OVER)
        if (fair) { lifeRefunded = true; game.lives.refund() }
    }

    private fun restart() {
        game.audio.resume()
        if (endless) { game.go(GameScreen(game, null, System.nanoTime(), emptyList())); return }
        refundLifeIfFair()
        startLevel(level!!, onCancel = { quit() })
    }

    private fun quit() {
        game.audio.resume()
        refundLifeIfFair()
        if (endless) game.go(EndlessIntroScreen(game)) else game.go(LevelSelectScreen(game, map))
    }

    private var completeResult: ResultSummary? = null

    /** Tamamlanan bölümün sonucunu bir kez işler (kutlama bitince, olası geçiş reklamından önce ya da ekran kapanırken). */
    private fun commitComplete(): ResultSummary? {
        completeResult?.let { return it }
        val lv = level ?: return null
        if (sim.status != SimStatus.COMPLETE) return null
        refundLifeIfFair() // bölüm geçildi: deneme için harcanan can geri gelir
        return game.profile.onLevelResult(LevelResult(lv, true, sim.starsCollected, sim.coinValue, sim.coinCount, sim.deaths, sim.tick, jumps, sim.rulesChanged, sim.powerupsUsed, sim.pieceCollected))
            .also { completeResult = it }
    }

    private fun showComplete() {
        if (finished) return
        finished = true
        val lv = level ?: return
        val res = commitComplete() ?: return
        dialog(resultW, resultH) { p, close ->
            p.top()
            p.add(kit.label(L.t("result.complete"), a.f48, Palette.INK)).colspan(2).padTop(4f).row()
            // Sol sütun: yıldızlar ve özet
            val left = Table().top()
            val st = Table()
            for (i in 0 until 3) {
                val img = Image(a.ui(if (i < res.stars) "b_star" else "b_star_empty"))
                st.add(img).size(if (i == 1) 96f else 80f).pad(4f).bottom()
                if (i < res.stars) { img.popIn(0.2f + i * 0.25f); stage.addAction(Actions.delay(0.25f + i * 0.25f, Actions.run { game.audio.play(Sfx.STAR, 1f + i * 0.12f) })) }
            }
            left.add(st).row()
            val info = Table()
            fun row(icon: String, text: String) { info.add(Image(a.ui(icon))).size(40f).padRight(10f).padTop(4f); info.add(kit.label(text, a.f28, Palette.INK, Align.left)).left().padTop(4f).row() }
            row("b_star", L.t("result.stars", "n" to sim.starsCollected))
            row("b_coin", L.n("result.coins", res.coins, "got" to sim.coinCount, "total" to lv.coins.size))
            row("b_bolt", L.t("result.xp", "n" to res.xp) + if (res.firstClear) L.t("result.first_clear") else "")
            row("b_heart", L.t("result.deaths", "n" to sim.deaths))
            left.add(info).padTop(8f).left().row()
            left.add(kit.label(L.t("result.star_rules"), a.f22, Palette.INK_SOFT, Align.left, wrap = true)).width(420f).padTop(10f).left()
            // Sağ sütun: ödüller ve bildirimler (sabit alan, gerekirse kaydırılır)
            val notes = Table().top().left()
            fun note(text: String, color: Color, font: com.badlogic.gdx.graphics.g2d.BitmapFont = a.f28) { notes.add(kit.label(text, font, color, Align.left, wrap = true)).width(440f).left().padBottom(8f).row() }
            res.levelUps.forEach { u -> note(L.t("result.levelup", "n" to u.level, "text" to u.text), Palette.GOLD) }
            res.achievements.forEach { ach -> note(L.t("result.achievement", "name" to ach.title, "gems" to L.n("unit.gems", ach.gems)), Palette.ORANGE) }
            res.missions.forEach { m -> note(L.t("result.mission_done", "text" to m.type.text(m.target)), Palette.GRASS, a.f22) }
            res.collectionCompleted?.let { note(L.t("result.collection_done", "map" to GameContent.maps[it - 1].name, "reward" to CollectionManager.rewardName(it)), Palette.GOLD) }
            res.unlocked.forEach { note(L.t("result.unlocked", "name" to it), Palette.GOLD) }
            if (notes.children.size == 0) note(if (res.stars < 3) L.t("result.replay_hint") else L.t("result.perfect"), Palette.INK_SOFT, a.f22)
            if (res.levelUps.isNotEmpty() || res.achievements.isNotEmpty()) game.audio.play(Sfx.REWARD)
            val right = Table().top()
            right.add(kit.label(L.t("result.rewards"), a.f36, Palette.INK)).left().row()
            right.add(scroll(notes)).width(460f).height(resultH - 330f).top().left().padTop(6f)
            p.add(left).width(resultW * 0.46f).top().padTop(6f).expandY().fillY()
            p.add(right).width(resultW * 0.46f).top().padTop(6f).expandY().fillY().row()
            val btns = Table()
            // İsteğe bağlı ödüllü reklam: bu bölümde toplanan altınları ikiye katlar
            if (res.coins > 0 && game.ads.rewardedReady) {
                var dbl: Actor? = null
                dbl = kit.button(L.n("ads.watch_coins", res.coins), "purple", "g_play", a.f28) {
                    watchAd { game.ads.grantBonusCoins(res.coins); dbl?.isVisible = false; game.audio.play(Sfx.REWARD); toast(L.n("toast.coins", res.coins), "b_coin") }
                }
                btns.add(dbl).width(330f).height(112f).pad(8f)
            }
            btns.add(kit.iconButton("g_retry", "yellow", 120f, 60f) { close(); restart() }).size(112f).pad(8f)
            btns.add(kit.iconButton("g_home", "white", 120f, 60f) { close(); quit() }).size(112f).pad(8f)
            if (lv.id < 500) btns.add(kit.button(L.t("common.next"), "green", "g_next") { close(); startLevel(LevelRepository.level(lv.id + 1), onCancel = { quit() }) }).width(260f).height(112f).pad(8f)
            p.add(btns).colspan(2).padBottom(4f)
        }
    }

    /** Reklamla devam bir denemede / koşuda yalnızca bir kez kullanılabilir. */
    private var adContinueUsed = false
    private var endlessCommitted = false

    private fun resumeAfterGameOver(hearts: Int) {
        if (endless) game.profile.releaseEndless() // koşu sürüyor: bekletilen sonuç geçersiz
        finished = false; overShown = false
        sim.continueAfterGameOver(hearts); refreshHearts(); readyHold(1.2f)
    }

    private fun showGameOver() {
        finished = true
        if (endless) {
            // Koşu başına bir kez: reklam izleyerek kaldığın yerden devam etme seçeneği (isteğe bağlı)
            if (!adContinueUsed && game.ads.rewardedReady) showEndlessRevive() else showEndlessResult()
            return
        }
        // Başarısız deneme, oyuncu bölümden ÇIKARKEN bir kez işlenir ("Devam Et" ile sürdürülürse henüz işlenmez;
        // aksi halde altınlar, görevler ve istatistikler iki kez sayılıyordu).
        failCommitted = false
        dialog(resultW, resultH) { p, close ->
            p.add(kit.label(L.t("over.title"), a.f48, Palette.INK)).colspan(2).row()
            p.add(Image(a.ui("b_heart_empty"))).size(80f).padTop(6f).colspan(2).row()
            p.add(kit.label(L.t("over.text"), a.f28, Palette.INK_SOFT, wrap = true)).width(640f).padTop(6f).colspan(2).row()
            // Kalıcı canların durumu: bu deneme bir can götürdü; tekrar denemek için can gerekir
            if (lifeSpent) p.add(Table().apply { add(kit.label(L.t("over.life_spent"), a.f22, Palette.INK_SOFT)).padRight(12f); add(livesChip()) }).colspan(2).padTop(6f).row()
            val conts = Table()
            // İsteğe bağlı ödüllü reklam: deneme başına bir kez, +2 canla kaldığın kontrol noktasından devam
            if (!adContinueUsed && game.ads.rewardedReady) {
                conts.add(kit.button(L.n("ads.watch_hearts", 2), "turq", "g_play", a.f28) {
                    watchAd { adContinueUsed = true; close(); resumeAfterGameOver(2) }
                }).width(430f).height(96f).pad(6f)
            }
            val cost = 10
            val cont = kit.button(L.n("over.continue_gems", 2, "cost" to cost), "purple", "b_gem", a.f28) {
                if (d.gems >= cost) { d.gems -= cost; game.profile.save(); close(); resumeAfterGameOver(2) }
                else toast(PurchaseResult.NOT_ENOUGH_GEMS.message, "b_gem")
            }
            cont.enabled = d.gems >= cost
            conts.add(cont).width(430f).height(96f).pad(6f)
            p.add(conts).colspan(2).padTop(12f).row()
            val exits = Table()
            exits.add(kit.button(L.t("over.retry"), "yellow", "g_retry", a.f28) { close(); commitFail(); restart() }).width(430f).height(96f).pad(6f)
            exits.add(kit.button(L.t("common.levels"), "white", "g_home", a.f28, Palette.INK) { close(); commitFail(); quit() }).width(430f).height(96f).pad(6f)
            p.add(exits).colspan(2)
        }
    }

    private fun showEndlessRevive() {
        dialog { p, close ->
            p.add(kit.label(L.t("endless.revive_title"), a.f48, Palette.INK)).row()
            p.add(kit.label(L.t("endless.revive_text"), a.f28, Palette.INK_SOFT, wrap = true)).width(560f).padTop(8f).row()
            p.add(kit.button(L.t("endless.revive_watch"), "turq", "g_play") { watchAd { adContinueUsed = true; close(); resumeAfterGameOver(1) } }).width(520f).height(104f).padTop(16f).row()
            p.add(kit.button(L.t("endless.revive_no"), "white", "g_check", a.f36, Palette.INK) { close(); showEndlessResult() }).width(520f).height(96f).padTop(8f)
        }
    }

    private fun showEndlessResult() {
        if (endlessCommitted) return
        endlessCommitted = true
        run {
            val sc = score()
            val res = game.profile.onEndlessResult(endlessResult())
            dialog(resultW, resultH) { p, close ->
                p.top()
                p.add(kit.label(if (res.newRecord) L.t("endless.new_record") else L.t("endless.game_over"), a.f48, if (res.newRecord) Palette.ORANGE else Palette.INK)).row()
                p.add(kit.label("$sc", a.f72o, Palette.SUN)).padTop(6f).row()
                p.add(kit.label(L.t("endless.points_label"), a.f22, Palette.INK_SOFT)).row()
                val info = Table()
                fun row(t: String, v: String) { info.add(kit.label(t, a.f28, Palette.INK_SOFT, Align.left)).left().width(300f); info.add(kit.label(v, a.f28, Palette.INK, Align.right)).right().row() }
                row(L.t("endless.time"), L.t("unit.seconds", "v" to L.dec(sim.tick / 60f))); row(L.t("endless.distance"), L.t("unit.meters", "v" to L.dec(sim.distance, 0))); row(L.t("endless.coins"), "+${sim.coinValue}")
                row(L.t("endless.rules"), "${sim.rulesChanged}"); row(L.t("endless.best"), "${d.stats.endlessBestScore}"); row(L.t("endless.xp"), "+${res.xp}")
                p.add(info).padTop(8f).row()
                val notes = Table().top()
                res.achievements.forEach { ach -> notes.add(kit.label(L.t("result.achievement_short", "name" to ach.title), a.f28, Palette.ORANGE, wrap = true)).width(640f).row() }
                res.missions.forEach { m -> notes.add(kit.label(L.t("result.mission_done", "text" to m.type.text(m.target)), a.f22, Palette.GRASS, wrap = true)).width(640f).row() }
                p.add(scroll(notes)).width(660f).expandY().fillY().padTop(4f).row()
                val btns = Table()
                btns.add(kit.button(L.t("endless.again"), "turq", "g_retry") { close(); restart() }).width(280f).height(116f).pad(8f)
                btns.add(kit.button(L.t("common.menu"), "white", "g_home", a.f36, Palette.INK) { close(); game.go(MainMenuScreen(game)) }).width(240f).height(116f).pad(8f)
                p.add(btns).padTop(16f)
            }
        }
    }

    /** Diriliş sorusu açıkken ekran kapanırsa koşu yine de kaydedilir. */
    private fun showEndlessResultSilently() {
        if (endlessCommitted) return
        endlessCommitted = true
        game.profile.onEndlessResult(endlessResult())
    }

    private var failCommitted = true
    private fun commitFail() {
        val lv = level ?: return
        if (failCommitted) return
        failCommitted = true
        game.profile.onLevelResult(LevelResult(lv, false, sim.starsCollected, sim.coinValue, sim.coinCount, sim.deaths, sim.tick, jumps, sim.rulesChanged, sim.powerupsUsed, false))
    }

    override fun dispose() {
        // oyun bitti ekranı açıkken uygulama kapanır ya da ekran değişirse deneme yine de işlenir
        if (sim.status == SimStatus.GAME_OVER) { if (endless) showEndlessResultSilently() else commitFail() }
        // bölüm bitmiş ama sonuç ekranı henüz açılmamışken (kutlama ya da reklam sırasında) ekran kapanırsa sonuç yine de işlenir
        if (sim.status == SimStatus.COMPLETE) commitComplete()
        super.dispose(); sim.dispose()
    }
}
