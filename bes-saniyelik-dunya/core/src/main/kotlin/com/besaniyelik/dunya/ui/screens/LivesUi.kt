package com.besaniyelik.dunya.ui.screens

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.besaniyelik.dunya.GameConfig
import com.besaniyelik.dunya.audio.Sfx
import com.besaniyelik.dunya.i18n.L
import com.besaniyelik.dunya.level.LevelData
import com.besaniyelik.dunya.level.PowerupType
import com.besaniyelik.dunya.progress.LifeManager
import com.besaniyelik.dunya.progress.PurchaseResult
import com.besaniyelik.dunya.ui.Palette
import com.besaniyelik.dunya.ui.popIn

/**
 * Kalıcı can göstergesi: "❤ 2/3" ve bir sonraki cana kalan süre ("19:42"; canlar doluysa "Dolu"). Dokununca canlar
 * penceresi açılır. Can yenilenince kalp zıplar; [announce] true ise kısa bir bildirim de gösterilir.
 */
class LivesChip(private val screen: BaseScreen, private val announce: Boolean) : Table() {
    private val game = screen.game
    private val kit = game.kit
    private val a = game.assets
    private val heart = Image(a.ui("b_heart"))
    private val count = kit.label("", a.f28, Palette.INK)
    private val timer = kit.label("", a.f22, Palette.INK_SOFT)
    private var last = -1
    private var acc = 0f

    init {
        background = kit.patch("chip")
        pad(6f, 10f, 10f, 16f)
        add(heart).size(44f).padRight(6f)
        val col = Table()
        col.add(count).left().row()
        col.add(timer).left().minWidth(64f)
        add(col)
        touchable = Touchable.enabled
        addListener(object : ClickListener() {
            override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) { kit.click(); screen.showLives() }
        })
        refresh(first = true)
    }

    override fun act(delta: Float) {
        super.act(delta)
        acc += delta
        if (acc >= 0.25f) { acc = 0f; refresh(first = false) }
    }

    private fun refresh(first: Boolean) {
        val lives = game.lives
        val n = lives.lives
        count.setText(L.t("lives.count", "n" to n, "max" to lives.max))
        timer.setText(lives.msToNext()?.let { LifeManager.clock(it) } ?: L.t("lives.full"))
        heart.drawable = TextureRegionDrawable(a.ui(if (n > 0) "b_heart" else "b_heart_empty"))
        if (!first && n > last) {
            heart.clearActions()
            heart.setOrigin(com.badlogic.gdx.utils.Align.center)
            heart.addAction(Actions.sequence(Actions.scaleTo(1.45f, 1.45f, 0.12f), Actions.scaleTo(1f, 1f, 0.35f, com.badlogic.gdx.math.Interpolation.swingOut)))
            if (announce) {
                game.audio.play(Sfx.REWARD, 1.2f)
                screen.toast(if (lives.full) L.t("lives.full_toast") else L.t("lives.gained_toast"), "b_heart")
            }
        }
        last = n
    }
}

fun BaseScreen.livesChip(announce: Boolean = false): LivesChip = LivesChip(this, announce)

/**
 * Bölüm denemesini başlatır: bölüm can harcıyorsa 1 can harcanır; can yoksa "Canların bitti" penceresi açılır (can
 * yenilenince ya da reklam/elmasla can alınınca "Oyna" ile devam edilir). Güçlendirmeler yalnızca deneme gerçekten
 * başlarsa envanterden düşer.
 */
fun BaseScreen.startLevel(lv: LevelData, boosts: () -> List<PowerupType> = { emptyList() }, onCancel: () -> Unit = {}) {
    if (game.transitioning) return
    val st = game.lives.tryStart(lv.id)
    if (st.ok) game.go(GameScreen(game, lv, null, boosts(), st.spent))
    else showLives(onReady = { startLevel(lv, boosts, onCancel) }, onCancel = onCancel)
}

/**
 * Canlar penceresi. [onReady] verilirse (bir bölüm başlatılmak istenmişti) can olunca "Oyna" düğmesi açılır;
 * pencere oynamadan kapatılırsa [onCancel] çağrılır.
 */
fun BaseScreen.showLives(onReady: (() -> Unit)? = null, onCancel: () -> Unit = {}) {
    val lives = game.lives
    val kit = this.kit
    val a = this.a
    dialog { p, close ->
        val title = kit.label("", a.f48, Palette.INK)
        p.add(title).row()
        val hearts = Table()
        p.add(hearts).padTop(8f).row()
        val timer = kit.label("", a.f36, Palette.ORANGE)
        p.add(timer).padTop(4f).row()
        p.add(kit.label(L.t("lives.rules", "min" to GameConfig.LIFE_REGEN_SECONDS / 60, "free" to GameConfig.FREE_LEVELS), a.f22, Palette.INK_SOFT, wrap = true)).width(620f).padTop(8f).row()
        val opts = Table()
        val adBtn = kit.button(L.n("ads.watch_lives", GameConfig.LIFE_AD_REWARD), "turq", "g_play", a.f28) {
            watchAd {
                if (game.ads.grantLifeAd(lives)) game.audio.play(Sfx.REWARD)
            }
        }
        val gemBtn = kit.button(L.t("lives.refill", "cost" to GameConfig.LIFE_REFILL_GEMS), "purple", "b_gem", a.f28) {
            if (lives.full) return@button
            if (lives.refillWithGems()) { game.audio.play(Sfx.REWARD); toast(L.t("lives.refilled"), "b_heart") }
            else toast(PurchaseResult.NOT_ENOUGH_GEMS.message, "b_gem")
        }
        val adCell = opts.add(adBtn).width(420f).height(92f).pad(6f)
        opts.add(gemBtn).width(420f).height(92f).pad(6f)
        p.add(opts).padTop(12f).row()
        val exits = Table()
        val play = if (onReady != null) kit.button(L.t("lives.play"), "green", "g_play") { close(); onReady() } else null
        if (play != null) exits.add(play).width(300f).height(96f).pad(6f)
        exits.add(kit.button(L.t("common.close"), "white", font = a.f28, textColor = Palette.INK) { close(); if (onReady != null) onCancel() }).width(300f).height(96f).pad(6f)
        p.add(exits).padTop(4f)
        var lastN = -1
        fun refresh() {
            val n = lives.lives
            title.setText(if (n <= 0) L.t("lives.empty_title") else L.t("lives.title"))
            if (n != lastN) {
                hearts.clear()
                for (i in 0 until lives.max) hearts.add(Image(a.ui(if (i < n) "b_heart" else "b_heart_empty"))).size(72f).pad(4f)
                if (lastN >= 0 && n > lastN) hearts.popIn()
                lastN = n
            }
            timer.setText(lives.msToNext()?.let { L.t("lives.next", "t" to LifeManager.clock(it)) } ?: L.t("lives.full_toast"))
            val showAd = game.ads.lifeAdOffer(lives.full)
            adBtn.isVisible = showAd
            adCell.width(if (showAd) 420f else 0f).pad(if (showAd) 6f else 0f)
            gemBtn.enabled = !lives.full && game.profile.data.gems >= GameConfig.LIFE_REFILL_GEMS
            play?.enabled = n > 0
        }
        refresh()
        p.addAction(Actions.forever(Actions.sequence(Actions.delay(0.25f), Actions.run { refresh(); p.invalidateHierarchy() })))
    }
}

/** İki dilli logo: seçili dilin adı büyük, diğer ad altında küçük. */
fun BaseScreen.brandLogo(width: Float): Table {
    val t = Table()
    val main = a.logo(L.lang)
    t.add(Image(main)).size(width, width * main.regionHeight / main.regionWidth).row()
    val sec = a.logoSecondary(L.lang)
    if (sec != null) {
        val w = width * 0.58f
        t.add(Image(sec)).size(w, w * sec.regionHeight / sec.regionWidth).padTop(-width * 0.015f)
    } else {
        t.add(kit.label(if (L.lang == "tr") L.t("app.name_en") else L.t("app.name_tr"), a.f28, Palette.INK_SOFT)).padTop(-4f)
    }
    return t
}
