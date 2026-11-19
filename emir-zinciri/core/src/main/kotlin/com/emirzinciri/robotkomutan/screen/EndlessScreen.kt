package com.emirzinciri.robotkomutan.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.emirzinciri.robotkomutan.EmirZinciriGame
import com.emirzinciri.robotkomutan.logic.gen.Generator
import com.emirzinciri.robotkomutan.logic.level.LevelDef
import com.emirzinciri.robotkomutan.logic.meta.Counter
import com.emirzinciri.robotkomutan.logic.sim.Outcome
import com.emirzinciri.robotkomutan.ui.Theme
import java.time.LocalDate

/** Gün/hafta anahtarları (cihazın yerel tarihi). */
object DateKeys {
    fun day(): Long = LocalDate.now().toEpochDay()
    /** Pazartesi başlangıçlı hafta numarası (epoch günü 0 perşembedir). */
    fun week(): Long = Math.floorDiv(day() + 3, 7)
}

/** Sonsuz Fabrika ve Günlük Komut Bulmacası girişi. */
class EndlessScreen(game: EmirZinciriGame) : BaseScreen(game) {
    private val status = ui.label("", assets.font, Theme.inkSoft, wrap = true, align = Align.center)
    private var busy = false

    init {
        val e = services.save.meta.endless
        root.top()
        val head = Table()
        head.add(ui.iconButton("back", size = 72f) { onBack() }).size(72f)
        head.add(ui.label("Sonsuz Fabrika", assets.fontTitle, Theme.ink)).padLeft(16f).left().growX()
        root.add(head).growX().pad(16f).row()

        val card = ui.panel(Theme.white, 28, 6f)
        card.pad(26f)
        card.add(ui.label("Her kat yeni bir harita: çözücüyle doğrulanmış, giderek zorlaşan bulmacalar. Bir katı geçemezsen koşu biter; rekorun kalır. Can harcanmaz.",
            assets.font, Theme.inkSoft, wrap = true, align = Align.center)).growX().row()
        val stats = Table()
        stats.add(ui.chip("star", "Rekor: ${e.bestFloor}. kat", Theme.yellow)).pad(6f)
        if (e.floor > 0) stats.add(ui.chip("map", "Koşu: ${e.floor}. kat", Theme.turquoise)).pad(6f)
        card.add(stats).padTop(16f).row()
        val next = e.floor + 1
        card.add(ui.button(if (e.floor > 0) "Devam: Kat $next" else "Koşuya Başla", Theme.green, Theme.white, "play", assets.fontTitle, 26) { startFloor(next) }
            .also { it.pad(18f) }).growX().padTop(18f).row()
        if (e.floor > 0) card.add(ui.button("Yeni koşu", Theme.white, Theme.red, "reset") {
            e.floor = 0; e.seed = System.nanoTime(); services.persist(); game.go(EndlessScreen(game))
        }).growX().padTop(10f).row()
        root.add(card).growX().pad(10f, 16f, 10f, 16f).row()

        val daily = ui.panel(Theme.white, 28, 6f)
        daily.pad(26f)
        val today = DateKeys.day()
        val solved = e.lastDailyDay == today
        daily.add(ui.label("Günlük Komut Bulmacası", assets.fontTitle, Theme.ink)).row()
        daily.add(ui.label(if (solved) "Bugünkü bulmacayı çözdün (en iyi: ${e.dailyBestCards} kart). Yarın yenisi gelir; tekrar oynayabilirsin."
            else "Her gün herkes için aynı bulmaca. En az kartla çöz! Ödül: 80 altın + 2 mikroçip.", assets.font, Theme.inkSoft, wrap = true, align = Align.center))
            .growX().padTop(8f).row()
        daily.add(ui.button(if (solved) "Tekrar oyna" else "Bugünün bulmacası", Theme.purple, Theme.white, "hint") { startDaily() }).growX().padTop(14f)
        root.add(daily).growX().pad(10f, 16f, 10f, 16f).row()
        root.add(status).growX().pad(16f)
        if (e.seed == 0L) { e.seed = System.nanoTime(); services.persist() }
    }

    private fun generateAsync(label: String, make: () -> Generator.Result?, open: (LevelDef) -> Unit) {
        if (busy) return
        busy = true
        status.setText("$label hazırlanıyor… (çözücü bölümü doğruluyor)")
        Thread {
            val r = runCatching { make() }.getOrNull()
            Gdx.app.postRunnable {
                busy = false
                if (r == null) status.setText("Bölüm üretilemedi. Tekrar dene.") else open(r.def)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun startFloor(floor: Int) {
        val e = services.save.meta.endless
        generateAsync("Kat $floor", { Generator.generate(floor, e.seed) }) { game.go(GameScreen(game, it, mode = PlayMode.ENDLESS)) }
    }

    private fun startDaily() = generateAsync("Günlük bulmaca", { Generator.daily(DateKeys.day()) }) {
        game.go(GameScreen(game, it, mode = PlayMode.DAILY))
    }

    override fun onBack() = game.go(MenuScreen(game))

    companion object {
        fun startFloorFrom(game: EmirZinciriGame) = game.go(EndlessScreen(game).also { it.autoStart = true })
    }

    var autoStart = false
    override fun show() {
        super.show()
        if (autoStart) startFloor(services.save.meta.endless.floor + 1)
    }
}

/** Sonsuz ve günlük modda sonuç işleme. */
object EndlessFlow {
    fun showResult(screen: BaseScreen, game: EmirZinciriGame, def: LevelDef, mode: PlayMode, o: Outcome, stars: Int, retry: () -> Unit): Table {
        val s = game.services
        val e = s.save.meta.endless
        val ui = game.ui; val a = game.assets
        val c = ui.panel()
        c.pad(30f)
        val buttons = Table()
        if (mode == PlayMode.CUSTOM || mode == PlayMode.HARD) {
            val win = o is Outcome.Win
            val hardOk = mode == PlayMode.HARD && win && o.stats.bumps == 0
            if (hardOk && s.save.meta.hardCleared.add(def.id)) {
                val gold = 20L * (1 + s.save.meta.prestige)
                s.save.wallet.gold += gold
                s.progression.meta.count(Counter.HARD_CLEARS); s.progression.meta.count(Counter.GOLD_EARNED, gold)
            }
            a.play(if (win) "win" else "fail")
            c.add(ui.label(if (win) (if (mode == PlayMode.HARD) (if (hardOk) "Zor kural tamam!" else "Kazandın ama çarptın") else "Harita çözüldü!") else "Olmadı",
                a.fontBig, if (win) Theme.green else Theme.orange, align = Align.center, wrap = true)).growX().row()
            val detail = if (o is Outcome.Fail) o.detail else "${o.stats.cards} kart · ${o.stats.ticks} adım"
            c.add(ui.label(detail + if (mode == PlayMode.HARD && !hardOk && win) "\nZor kural hiç çarpmadan bitirmeyi ister." else "", a.font, Theme.inkSoft, wrap = true, align = Align.center)).growX().padTop(10f).row()
            buttons.add(ui.button(if (mode == PlayMode.CUSTOM) "Editör" else "Bölümler", Theme.panelAlt, Theme.ink) {
                game.go(if (mode == PlayMode.CUSTOM) EditorScreen(game, com.emirzinciri.robotkomutan.logic.editor.EditorModel.from(def)) else LevelSelectScreen(game, def.area, hard = true))
            }).growX().uniformX().pad(6f)
            buttons.add(ui.button("Tekrar", Theme.turquoise, Theme.white, "reset") { retry() }).growX().uniformX().pad(6f)
        } else if (mode == PlayMode.ENDLESS) {
            val floor = def.id - 10_000
            if (o is Outcome.Win) {
                e.floor = floor
                val record = floor > e.bestFloor
                if (record) e.bestFloor = floor
                val gold = 10L * floor + 5L * stars
                s.save.wallet.gold += gold
                s.progression.meta.count(Counter.ENDLESS_FLOORS); s.progression.meta.count(Counter.GOLD_EARNED, gold)
                a.play("win")
                c.add(ui.label("Kat $floor geçildi!", a.fontBig, Theme.green, align = Align.center)).growX().row()
                c.add(ui.stars(stars, 54f)).padTop(10f).row()
                c.add(ui.label((if (record) "Yeni rekor! " else "") + "+$gold altın", a.fontBold, Theme.ink)).padTop(10f).row()
                buttons.add(ui.button("Mola", Theme.panelAlt, Theme.ink) { game.go(EndlessScreen(game)) }).growX().uniformX().pad(6f)
                buttons.add(ui.button("Kat ${floor + 1}", Theme.green, Theme.white, "play") { EndlessScreen.startFloorFrom(game) }).growX().uniformX().pad(6f)
            } else {
                a.play("fail")
                val reached = floor - 1
                e.floor = 0; e.seed = System.nanoTime()
                c.add(ui.label("Koşu bitti", a.fontBig, Theme.orange, align = Align.center)).growX().row()
                c.add(ui.label("${(o as Outcome.Fail).detail}\nBu koşuda $reached kat geçtin. Rekor: ${e.bestFloor}.", a.font, Theme.inkSoft, wrap = true, align = Align.center))
                    .growX().padTop(10f).row()
                buttons.add(ui.button("Çıkış", Theme.panelAlt, Theme.ink) { game.go(EndlessScreen(game)) }).growX().uniformX().pad(6f)
                buttons.add(ui.button("Yeni koşu", Theme.turquoise, Theme.white, "reset") { EndlessScreen.startFloorFrom(game) }).growX().uniformX().pad(6f)
            }
        } else {
            val today = DateKeys.day()
            if (o is Outcome.Win) {
                val first = e.lastDailyDay != today
                val cards = o.stats.cards
                if (first) {
                    e.lastDailyDay = today; e.dailyBestCards = cards
                    s.save.wallet.gold += 80; s.save.wallet.chips += 2
                    s.progression.meta.count(Counter.DAILY_PUZZLES); s.progression.meta.count(Counter.GOLD_EARNED, 80)
                } else if (cards < e.dailyBestCards) e.dailyBestCards = cards
                a.play("win")
                c.add(ui.label("Günlük bulmaca çözüldü!", a.fontBig, Theme.green, align = Align.center, wrap = true)).growX().row()
                c.add(ui.stars(stars, 54f)).padTop(10f).row()
                c.add(ui.label("$cards kart (en iyi çözüm ${def.par} kart)" + if (first) " · +80 altın, +2 mikroçip" else "", a.font, Theme.inkSoft, wrap = true, align = Align.center))
                    .growX().padTop(10f).row()
            } else {
                a.play("fail")
                c.add(ui.label("Olmadı", a.fontBig, Theme.orange, align = Align.center)).growX().row()
                c.add(ui.label((o as Outcome.Fail).detail + "\nCan harcanmadı; kartların korunuyor.", a.font, Theme.inkSoft, wrap = true, align = Align.center)).growX().padTop(10f).row()
            }
            buttons.add(ui.button("Çıkış", Theme.panelAlt, Theme.ink) { game.go(EndlessScreen(game)) }).growX().uniformX().pad(6f)
            buttons.add(ui.button("Düzelt / Tekrar", Theme.turquoise, Theme.white, "reset") { retry() }).growX().uniformX().pad(6f)
        }
        c.add(buttons).growX().padTop(20f)
        s.persist()
        return ui.modal(screen.stage, c, dismissable = false)
    }
}
