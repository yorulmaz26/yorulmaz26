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

/**
 * Oynanış ekranı. [level] null ise Sonsuz Dünya ([seed] ile). Simülasyon sabit 60 Hz adımla ilerler;
 * çizim ve arayüz simülasyonun durumunu okur.
 */
class GameScreen(game: BesSaniyelikDunya, private val level: LevelData?, private val seed: Long?, private val boosts: List<PowerupType>) : BaseScreen(game) {
    override val background: MenuBackground? = null
    override val toastTime = 1.0f
    private val endless = level == null
    private val d = game.profile.data
    private val generator = if (endless) EndlessGenerator(seed ?: 1L, Mechanic.unlockedAt(d.highestUnlocked).toSet()) else null
    private val sim = Simulation(level, SimConfig(hearts = if (endless) 1 else 3), generator)
    private val map = level?.map ?: 10
    private val renderer = WorldRenderer(a, sim, map, Catalog.skin(d.selectedSkin), Catalog.trail(d.selectedTrail), d.settings.reducedEffects)

    /** Test/tanıtım için: kayıtlı çözümü gerçek giriş yerine oynatır. */
    var autoplay: InputTrack? = null
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
            override fun onPiece(c: Collectible) { game.audio.play(Sfx.REWARD); renderer.onPiece(c); toast("Gizli parça!", "b_puzzle") }
            override fun onPowerup(type: PowerupType) { game.audio.play(Sfx.POWERUP); renderer.onPowerup(); if (started) toast(type.title, "b_" + type.icon) }
            override fun onRuleWarning(next: List<Mechanic>) { game.audio.play(Sfx.WARN); showNext(next) }
            override fun onRuleChange(prev: List<Mechanic>, next: List<Mechanic>) { game.audio.play(Sfx.RULE); renderer.onRule(); ruleBanner(next); game.vibrate(25); checkEndlessTutorial(next) }
            override fun onSpring() { game.audio.play(Sfx.SPRING); renderer.onSpring() }
            override fun onBoost() { game.audio.play(Sfx.BOOST) }
            override fun onCheckpoint(x: Float) { game.audio.play(Sfx.CHECKPOINT); renderer.onCheckpoint(x) }
            override fun onShieldSave() { game.audio.play(Sfx.SHIELD); toast("Kalkan korudu!", "b_p_shield") }
            override fun onDeath(cause: DeathCause) { game.audio.play(Sfx.DEATH); renderer.onDeath(); game.vibrate(60); deathText(cause) }
            override fun onRespawn() { refreshHearts() }
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

    private fun press(v: Boolean) {
        if (v && !started && !finished && startOverlay != null) { begin(); return }
        pressed = v
        if (v && waitHint != null) clearWait()
    }

    private fun clearWait() { waitHint = null; hintBox.clearActions(); hintBox.addAction(Actions.sequence(Actions.delay(0.6f), Actions.fadeOut(0.3f))) }

    override fun build() {
        game.audio.music(if (endless) "endless" else "map_%02d".format(map))
        stage.addActor(tint)
        root.touchable = Touchable.childrenOnly
        // üst çubuk
        val top = Table()
        top.add(kit.iconButton("g_pause", "white", 88f, 44f) { openPauseMenu() }).size(88f).padLeft(18f)
        val mid = Table()
        if (endless) mid.add(scoreLabel).row() else {
            mid.add(kit.label(level!!.name, a.f22, Palette.INK)).row()
            mid.add(progress).width(300f).height(22f).padTop(4f)
        }
        top.add(mid).expandX()
        val right = Table()
        right.add(Image(a.ui("b_coin"))).size(48f); right.add(coinLabel).padLeft(4f).minWidth(60f).row()
        right.add(starSlots).colspan(2).right()
        top.add(right).right().padRight(18f)
        root.add(top).growX().padTop(22f).row()
        root.add(hearts).left().padLeft(24f).padTop(6f).row()
        // kural paneli
        val rp = Table().apply { background = kit.patch("chip_dark"); pad(8f, 16f, 10f, 16f) }
        rp.add(ruleIcons).padRight(10f)
        val rt = Table(); rt.add(ruleTime).row(); rt.add(ruleBar).width(140f).height(16f)
        rp.add(rt)
        rp.add(nextIcons).padLeft(12f)
        root.add(rp).padTop(4f).row()
        root.add(powerRow).padTop(6f).row()
        hintBox.color.a = 0f
        root.add(hintBox).padTop(16f).row()
        root.add().expandY()
        refreshHearts(); refreshStars(); refreshRules()
        showStartOverlay()
    }

    private fun showStartOverlay() {
        val o = Table().apply { setFillParent(true); touchable = Touchable.disabled }
        val p = kit.panel()
        val tut = level?.tutorial
        if (tut != null && level.kind == LevelKind.TUTORIAL) {
            p.add(kit.label("Yeni Kural!", a.f28, Palette.INK_SOFT)).row()
            p.add(Image(a.ui("b_" + tut.icon))).size(130f).padTop(8f).row()
            p.add(kit.label(tut.title, a.f48, Palette.INK, wrap = true)).width(540f).padTop(6f).row()
            p.add(kit.label(tut.hint, a.f28, Palette.INK_SOFT, wrap = true)).width(520f).padTop(6f).row()
            p.add(kit.label(tutorialDetail(tut), a.f22, Palette.INK_SOFT, wrap = true)).width(520f).padTop(10f).row()
            game.profile.markTutorialSeen("m_" + tut.name)
        } else if (level?.id == 1) {
            p.add(Image(a.ui("logo"))).size(420f, 196f).row()
            p.add(kit.label("Karakterin kendiliğinden koşar.\nEkrana dokun: zıpla.\nBasılı tut: daha yükseğe zıpla.\nHer 5 saniyede dünyanın kuralları değişir!", a.f28, Palette.INK, wrap = true)).width(520f).padTop(10f).row()
        } else {
            p.add(kit.label(if (endless) "Sonsuz Dünya" else level!!.name, a.f48, Palette.INK, wrap = true)).width(540f).row()
            if (!endless) p.add(kit.stars(d.stars(level!!.id), 54f)).padTop(8f).row()
        }
        p.add(Image(a.ui("g_tap")).apply { color = Palette.ORANGE }).size(90f).padTop(16f).row()
        p.add(kit.label("Başlamak için dokun", a.f36, Palette.ORANGE)).padTop(4f)
        o.add(p).width(620f)
        stage.addActor(o)
        p.popIn()
        startOverlay = o
    }

    private fun tutorialDetail(m: Mechanic): String = when (m) {
        Mechanic.REVERSE_GRAVITY -> "Kural başlayınca tavana düşersin. Tavanda dokununca aşağı doğru zıplarsın."
        Mechanic.FAST -> "Oyun %30 hızlanır. Engellere daha erken tepki ver."
        Mechanic.ICE -> "Buzda hızın yavaş yavaş artar ve zıplayışların uzar."
        Mechanic.GHOST -> "Mor platformlar önce yanıp söner, sonra kısa süre kaybolur. Ritmi izle!"
        Mechanic.BOUNCY -> "Zıplama gücün artar. Yüksek duvarların üstünden geçebilirsin."
        Mechanic.SMALL -> "Küçülünce alçak geçitlerin altından geçebilirsin. Geçitte büyümezsin, merak etme."
        Mechanic.REVERSE_CONTROL -> "Karakter kendiliğinden zıplar. Dokunup basılı tutarsan yerde kalır. Dikenlerin altında basılı tut!"
        Mechanic.SLOW -> "Zaman yavaşlar. Hareketli engellerin ritmini kolayca yakala."
        Mechanic.SHIFTING -> "Oklu sarı platformlar yukarı aşağı kayar. Doğru anda üstlerine zıpla."
        Mechanic.NORMAL -> ""
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
            if (!slow || frame % 7 == 0) {
                acc += if (slow) Physics.TICK else dt
                var steps = 0
                while (acc >= Physics.TICK && steps < 5) {
                    val ap = autoplay
                    if (ap != null) { pressed = ap.at(sim.tick); if (pressed && waitHint != null) clearWait() }
                    sim.step(pressed)
                    acc -= Physics.TICK; steps++
                }
                if (steps == 5) acc = 0f
            }
            checkHints()
            if (endless && frame % 120 == 0) sim.pruneBefore(sim.player.x - 30f)
        }
        renderer.update(if (paused) 0f else dt)
        renderer.render(game.batch)
        updateHud(dt)
        if (completeTimer > 0f) { completeTimer -= dt; if (completeTimer <= 0f) showComplete() }
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
        hintBox.add(kit.label(h.text, a.f28, Palette.INK, wrap = true)).width(440f)
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
        else scoreLabel.setText("${score()} puan")
        val s = sim.secondsToNextRule()
        if (s.isFinite()) { ruleTime.setText("%.1f".format(s)); ruleBar.value = 1f - sim.phaseProgress() } else { ruleTime.setText("—"); ruleBar.value = 0f }
        ruleTime.color = if (s < Physics.WARNING_TIME) Palette.SUN else Color.WHITE
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
        for (i in 0 until total) hearts.add(Image(a.ui(if (i < sim.hearts) "b_heart" else "b_heart_empty"))).size(48f).padRight(2f)
    }

    private fun refreshStars() {
        lastStars = sim.starsCollected
        starSlots.clear()
        if (endless) { starSlots.add(Image(a.ui("b_star"))).size(38f); starSlots.add(kit.label(" ${sim.starsCollected}", a.f28, Color.WHITE)); return }
        for (i in 0 until 3) starSlots.add(Image(a.ui(if (i < sim.starsCollected) "b_star" else "b_star_empty"))).size(40f)
    }

    private fun refreshRules() {
        lastRules = sim.activeRules
        ruleIcons.clear()
        sim.activeRules.forEach { ruleIcons.add(Image(a.ui("b_" + it.icon))).size(64f).pad(2f) }
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
        b.setPosition((stage.width - b.width) / 2, stage.height - 330f)
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
        l.pack(); l.setPosition((stage.width - l.width) / 2, stage.height - 400f)
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
            p.add(kit.label("Yeni Kural!", a.f28, Palette.INK_SOFT)).row()
            p.add(Image(a.ui("b_" + unseen.icon))).size(120f).row()
            p.add(kit.label(unseen.title, a.f48, Palette.INK)).row()
            p.add(kit.label(tutorialDetail(unseen), a.f28, Palette.INK_SOFT, wrap = true)).width(540f).padTop(8f).row()
            p.add(kit.button("Anladım!", "green", "g_check") { close(); paused = false; pressed = false }).width(360f).height(116f).padTop(20f)
        }
    }

    // ------------------------------------------------------------ menüler
    private fun openPauseMenu() {
        if (finished || !started || paused) return
        paused = true
        pressed = false
        game.audio.pause()
        dialog { p, close ->
            p.add(kit.label("Duraklatıldı", a.f48, Palette.INK)).row()
            if (!endless) p.add(kit.label(level!!.name, a.f28, Palette.INK_SOFT)).padTop(4f).row()
            val st = d.settings
            val toggles = Table()
            toggles.add(kit.iconButton(if (st.music) "g_music" else "g_music_off", "pink", 100f, 54f) { st.music = !st.music; game.audio.applyVolume(); game.profile.save(); close(); paused = false; openPauseMenu() }).size(100f).pad(8f)
            toggles.add(kit.iconButton(if (st.sound) "g_sound" else "g_sound_off", "turq", 100f, 54f) { st.sound = !st.sound; game.profile.save(); close(); paused = false; openPauseMenu() }).size(100f).pad(8f)
            p.add(toggles).padTop(10f).row()
            p.add(kit.button("Devam Et", "green", "g_play") { close(); paused = false; game.audio.resume() }).width(460f).height(116f).padTop(12f).row()
            p.add(kit.button("Yeniden Başla", "yellow", "g_retry") { close(); restart() }).width(460f).height(116f).padTop(10f).row()
            p.add(kit.button(if (endless) "Ana Menü" else "Bölümler", "white", "g_home", a.f36, Palette.INK) { close(); quit() }).width(460f).height(116f).padTop(10f)
        }
    }

    override fun pause() { if (started && !paused && !finished) openPauseMenu() }

    override fun onBack() { if (paused && started) return; openPauseMenu() }

    private fun restart() {
        game.audio.resume()
        game.go(if (endless) GameScreen(game, null, System.nanoTime(), emptyList()) else GameScreen(game, level, null, emptyList()))
    }

    private fun quit() {
        game.audio.resume()
        if (endless) game.go(EndlessIntroScreen(game)) else game.go(LevelSelectScreen(game, map))
    }

    private fun showComplete() {
        if (finished) return
        finished = true
        val lv = level ?: return
        val res = game.profile.onLevelResult(LevelResult(lv, true, sim.starsCollected, sim.coinValue, sim.coinCount, sim.deaths, sim.tick, jumps, sim.rulesChanged, sim.powerupsUsed, sim.pieceCollected))
        dialog { p, close ->
            p.add(kit.label("Bölüm Tamamlandı!", a.f48, Palette.INK)).row()
            val st = Table()
            for (i in 0 until 3) {
                val img = Image(a.ui(if (i < res.stars) "b_star" else "b_star_empty"))
                st.add(img).size(if (i == 1) 130f else 110f).pad(4f).bottom()
                if (i < res.stars) { img.popIn(0.2f + i * 0.25f); stage.addAction(Actions.delay(0.25f + i * 0.25f, Actions.run { game.audio.play(Sfx.STAR, 1f + i * 0.12f) })) }
            }
            p.add(st).padTop(10f).row()
            val info = Table()
            fun row(icon: String, text: String) { info.add(Image(a.ui(icon))).size(44f).padRight(10f); info.add(kit.label(text, a.f28, Palette.INK, Align.left)).left().row() }
            row("b_star", "Toplanan yıldız: ${sim.starsCollected}/3")
            row("b_coin", "+${res.coins} altın  (${sim.coinCount}/${lv.coins.size})")
            row("b_bolt", "+${res.xp} XP" + if (res.firstClear) "  (ilk geçiş x2)" else "")
            row("b_heart", "Ölüm: ${sim.deaths}")
            p.add(info).padTop(10f).row()
            p.add(kit.label("2★: en az 2 yıldız veya altınların %70'i  •  3★: 3 yıldız, en fazla 1 ölüm", a.f22, Palette.INK_SOFT, wrap = true)).width(560f).padTop(6f).row()
            res.levelUps.forEach { u -> p.add(kit.label("Seviye ${u.level}! ${u.text}", a.f28, Palette.GOLD, wrap = true)).width(560f).padTop(4f).row() }
            res.achievements.forEach { ach -> p.add(kit.label("Başarı: ${ach.title} (+${ach.gems} elmas)", a.f28, Palette.ORANGE, wrap = true)).width(560f).padTop(4f).row() }
            res.missions.forEach { m -> p.add(kit.label("Görev tamamlandı: ${m.type.text(m.target)}", a.f22, Palette.GRASS, wrap = true)).width(560f).row() }
            res.collectionCompleted?.let { p.add(kit.label("${GameContent.maps[it - 1].name} koleksiyonu tamam! ${CollectionManager.rewardName(it)}", a.f28, Palette.GOLD, wrap = true)).width(560f).row() }
            res.unlocked.forEach { p.add(kit.label("Açıldı: $it", a.f28, Palette.GOLD)).row() }
            if (res.levelUps.isNotEmpty() || res.achievements.isNotEmpty()) game.audio.play(Sfx.REWARD)
            val btns = Table()
            btns.add(kit.iconButton("g_retry", "yellow", 120f, 60f) { close(); restart() }).size(120f).pad(8f)
            btns.add(kit.iconButton("g_home", "white", 120f, 60f) { close(); quit() }).size(120f).pad(8f)
            if (lv.id < 500) btns.add(kit.button("Sonraki", "green", "g_next") { close(); game.go(GameScreen(game, LevelRepository.level(lv.id + 1), null, emptyList())) }).width(260f).height(120f).pad(8f)
            p.add(btns).padTop(18f)
        }
    }

    private fun showGameOver() {
        finished = true
        if (endless) {
            val sc = score()
            val res = game.profile.onEndlessResult(EndlessResult(sc, sim.tick / 60f, sim.distance, sim.coinValue, sim.starsCollected, jumps, sim.rulesChanged, sim.powerupsUsed))
            dialog { p, close ->
                p.add(kit.label(if (res.newRecord) "YENİ REKOR!" else "Oyun Bitti", a.f48, if (res.newRecord) Palette.ORANGE else Palette.INK)).row()
                p.add(kit.label("$sc", a.f72o, Palette.SUN)).padTop(6f).row()
                p.add(kit.label("puan", a.f22, Palette.INK_SOFT)).row()
                val info = Table()
                fun row(t: String, v: String) { info.add(kit.label(t, a.f28, Palette.INK_SOFT, Align.left)).left().width(300f); info.add(kit.label(v, a.f28, Palette.INK, Align.right)).right().row() }
                row("Süre", "%.1f sn".format(sim.tick / 60f)); row("Mesafe", "%.0f m".format(sim.distance)); row("Altın", "+${sim.coinValue}")
                row("Atlatılan kural", "${sim.rulesChanged}"); row("En iyi puan", "${d.stats.endlessBestScore}"); row("Kazanılan XP", "+${res.xp}")
                p.add(info).padTop(12f).row()
                res.achievements.forEach { ach -> p.add(kit.label("Başarı: ${ach.title}", a.f28, Palette.ORANGE)).row() }
                res.missions.forEach { m -> p.add(kit.label("Görev tamamlandı: ${m.type.text(m.target)}", a.f22, Palette.GRASS, wrap = true)).width(560f).row() }
                val btns = Table()
                btns.add(kit.button("Tekrar", "turq", "g_retry") { close(); restart() }).width(280f).height(116f).pad(8f)
                btns.add(kit.button("Menü", "white", "g_home", a.f36, Palette.INK) { close(); game.go(MainMenuScreen(game)) }).width(240f).height(116f).pad(8f)
                p.add(btns).padTop(16f)
            }
            return
        }
        val lv = level!!
        game.profile.onLevelResult(LevelResult(lv, false, sim.starsCollected, sim.coinValue, sim.coinCount, sim.deaths, sim.tick, jumps, sim.rulesChanged, sim.powerupsUsed, false))
        dialog { p, close ->
            p.add(kit.label("Canların Bitti", a.f48, Palette.INK)).row()
            p.add(Image(a.ui("b_heart_empty"))).size(120f).padTop(8f).row()
            p.add(kit.label("Pes etme! Her denemede daha iyi olursun.", a.f28, Palette.INK_SOFT, wrap = true)).width(540f).padTop(6f).row()
            val cost = 10
            val cont = kit.button("Devam Et (+2 can)  $cost", "purple", "b_gem") {
                if (d.gems >= cost) {
                    d.gems -= cost; game.profile.save(); close()
                    finished = false; overShown = false; sim.continueAfterGameOver(2); refreshHearts()
                } else toast("Yeterli elmasın yok.", "b_gem")
            }
            cont.enabled = d.gems >= cost
            p.add(cont).width(520f).height(116f).padTop(16f).row()
            p.add(kit.button("Tekrar Dene", "yellow", "g_retry") { close(); restart() }).width(520f).height(116f).padTop(10f).row()
            p.add(kit.button("Bölümler", "white", "g_home", a.f36, Palette.INK) { close(); quit() }).width(520f).height(116f).padTop(10f)
        }
    }

    override fun dispose() { super.dispose(); sim.dispose() }
}
