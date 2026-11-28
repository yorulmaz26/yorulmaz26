package com.besaniyelik.dunya.ui.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.besaniyelik.dunya.BesSaniyelikDunya
import com.besaniyelik.dunya.level.*
import com.besaniyelik.dunya.progress.*
import com.besaniyelik.dunya.render.CharState
import com.besaniyelik.dunya.render.CharacterRenderer
import com.besaniyelik.dunya.sim.Mechanic
import com.besaniyelik.dunya.ui.*
import com.besaniyelik.dunya.i18n.L
import com.besaniyelik.dunya.i18n.LevelText
import com.besaniyelik.dunya.i18n.PlayerNames

class SplashScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    private var t = 0f
    private var went = false
    override fun build() {
        game.audio.music("menu")
        val logo = brandLogo(620f)
        root.add().height(50f).row()
        root.add(logo).row()
        logo.popIn(0.1f)
        root.add(kit.label(L.t("splash.tagline"), a.f36o, Color.WHITE)).padTop(20f).row()
        val bar = kit.bar(0f, "bar_fill_y")
        root.add(bar).width(420f).height(28f).padTop(36f).row()
        bar.addAction(object : com.badlogic.gdx.scenes.scene2d.Action() {
            override fun act(delta: Float): Boolean { bar.value = (t / 1.4f).coerceAtMost(1f); return false }
        })
        root.add(kit.label(L.t("splash.loading"), a.f28, Palette.INK)).padTop(12f)
    }
    override fun render(delta: Float) {
        t += delta
        if (t > 0.3f && t < 0.35f) { LevelRepository.pack(1) } // ilk haritayı önceden yükle
        super.render(delta)
        if (t > 1.7f && !went) {
            went = true
            game.profile.refreshDaily()
            game.go(if (game.profile.loginAvailable()) DailyRewardScreen(game, fromSplash = true) else MainMenuScreen(game))
        }
    }
    override fun onBack() {}
}

class MainMenuScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    override fun build() {
        game.audio.music("menu")
        val d = game.profile.data
        // üst: oyuncu kartı
        val top = Table()
        val card = Table().apply { background = kit.patch("chip"); pad(8f, 14f, 12f, 20f) }
        card.add(AvatarActor(game, 70f)).size(70f).padRight(10f)
        val info = Table()
        info.add(kit.label(PlayerNames.display(d.playerName), a.f28, Palette.INK, Align.left)).left().row()
        info.add(kit.label(L.t("profile.level", "n" to d.playerLevel), a.f22, Palette.INK_SOFT, Align.left)).left().row()
        info.add(kit.bar(d.xp / ProgressionManager.xpToNext(d.playerLevel).toFloat(), "bar_fill_b", 16f)).width(150f).height(16f).left().padTop(4f)
        card.add(info)
        card.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
        card.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() { override fun clicked(e: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) { kit.click(); game.go(ProfileScreen(game)) } })
        top.add(card).left().expandX().padLeft(20f)
        val cur = Table()
        cur.add(kit.currency("b_coin", d.coins.toString())).right().row()
        cur.add(kit.currency("b_gem", d.gems.toString())).right().padTop(6f)
        if (game.lives.enabled) top.add(livesChip(announce = true)).right().top().padRight(12f)
        top.add(cur).right().padRight(20f)
        root.add(top).growX().padTop(18f).row()

        // sol sütun: logo ve oyna düğmeleri, sağ sütun: menü ızgarası
        val left = Table()
        val logo = brandLogo(500f)
        left.add(logo).row()
        logo.popIn()
        val next = d.highestUnlocked.coerceAtMost(500)
        val play = kit.button(if (d.levels.isEmpty()) L.t("menu.play") else L.t("menu.continue", "n" to next), "green", "g_play", a.f48o) {
            val lv = LevelRepository.level(next)
            LevelInfo.show(this, game, lv)
        }
        left.add(play).width(520f).height(124f).padTop(16f).row()
        play.popIn(0.1f)
        val row2 = Table()
        row2.add(kit.button(L.t("menu.maps"), "yellow", "b_map", a.f36o) { game.go(MapSelectScreen(game)) }).width(252f).height(104f).padRight(16f)
        row2.add(kit.button(L.t("menu.endless"), "turq", "b_infinity", a.f36o) { game.go(EndlessIntroScreen(game)) }).width(252f).height(104f)
        left.add(row2).padTop(14f).row()
        row2.popIn(0.18f)

        val grid = Table()
        val missionsReady = (d.missions.daily + d.missions.weekly).any { it.done && !it.claimed }
        val items = listOf(
            Triple("b_person", L.t("menu.characters")) { game.go(CharacterScreen(game)) },
            Triple("b_bag", L.t("menu.shop")) { game.go(ShopScreen(game)) },
            Triple("b_scroll", L.t("menu.daily")) { game.go(MissionsScreen(game, weekly = false)) },
            Triple("b_calendar", L.t("menu.weekly")) { game.go(MissionsScreen(game, weekly = true)) },
            Triple("b_trophy", L.t("menu.achievements")) { game.go(AchievementsScreen(game)) },
            Triple("b_puzzle", L.t("menu.collection")) { game.go(CollectionScreen(game)) },
            Triple("b_gift", L.t("menu.rewards")) { game.go(DailyRewardScreen(game)) },
            Triple("b_bolt", L.t("menu.howto")) { game.go(HowToPlayScreen(game)) },
        )
        items.forEachIndexed { i, (icon, text, act) ->
            val cell = Table()
            val b = kit.iconButton(icon, "white", 104f, 66f) { act() }
            val stack = Stack(b)
            val dot = (icon == "b_gift" && game.profile.loginAvailable()) || ((icon == "b_scroll" || icon == "b_calendar") && missionsReady)
            if (dot) stack.add(Table().apply { top().right(); add(Image(a.ui("soft")).apply { color = Palette.RED }).size(30f) })
            cell.add(stack).size(104f).row()
            cell.add(kit.label(text, a.f22, Palette.INK, fit = true)).width(124f).padTop(2f)
            grid.add(cell).pad(6f, 10f, 6f, 10f)
            if (i % 4 == 3) grid.row()
            cell.popIn(0.2f + i * 0.03f)
        }
        val gp = Table().apply { background = kit.patch("panel"); pad(18f, 10f, 22f, 10f); add(grid) }
        val body = Table()
        body.add(left).expandX().center()
        body.add(gp).expandX().center()
        root.add(body).grow().row()
        val bottom = Table()
        bottom.add(kit.iconButton("g_settings", "blue", 84f, 48f) { game.go(SettingsScreen(game)) }).size(84f).padRight(12f)
        bottom.add(kit.iconButton(if (d.settings.music) "g_music" else "g_music_off", "pink", 84f, 44f) {
            d.settings.music = !d.settings.music; game.audio.applyVolume(); game.profile.save(); rebuild()
        }).size(84f)
        root.add(bottom).padBottom(16f).right().padRight(24f)
    }
    override fun onBack() { com.badlogic.gdx.Gdx.app.exit() }
}

/** Seçili karakteri UI içinde çizen aktör. */
class AvatarActor(private val game: BesSaniyelikDunya, private val sz: Float, var skinId: String? = null, var state: CharState = CharState.IDLE) : Actor() {
    private val a = game.assets
    private val r = CharacterRenderer(game.assets)
    private var t = 0f
    override fun act(delta: Float) { super.act(delta); t += delta }
    override fun draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: Float) {
        val skin = Catalog.skin(skinId ?: game.profile.data.selectedSkin)
        // küçük boyutlarda hafif önizleme karesi: 20 karakter aynı anda büyük atlaslarını yüklemez
        if (width <= 140f) {
            val th = a.heroThumb(skin.id, state == CharState.HAPPY)
            if (th != null) {
                val h = height * 0.92f; val w = h * th.regionWidth / th.regionHeight
                val bob = kotlin.math.abs(kotlin.math.sin(t * 2.2f)) * height * 0.03f
                batch.setColor(1f, 1f, 1f, parentAlpha * color.a)
                batch.draw(th, x + (width - w) / 2f, y + bob, w, h)
                batch.setColor(1f, 1f, 1f, 1f)
                return
            }
        }
        val sz = if (a.heroFrames(skin.id) != null) width * 0.62f else width * 0.78f
        val cy = if (a.heroFrames(skin.id) != null) y + sz / 2f + height * 0.04f else y + height * 0.42f
        r.draw(batch, skin, x + width / 2, cy, sz, state, t, alpha = parentAlpha * color.a)
        batch.setColor(1f, 1f, 1f, 1f)
    }
}

object LevelInfo {
    /** Bölüm başlangıç diyaloğu: bilgiler, mekanikler, en iyi sonuç ve isteğe bağlı güçlendirme seçimi. */
    fun show(screen: BaseScreen, game: BesSaniyelikDunya, lv: LevelData) {
        val kit = game.kit; val a = game.assets
        val d = game.profile.data
        screen.dialog { p, close ->
            val kindText = L.t("level.kind.${lv.kind.name}")
            val left = Table()
            left.add(kit.label(L.t("level.kind_id", "kind" to kindText, "n" to lv.id), a.f28, Palette.INK_SOFT)).row()
            left.add(kit.label(LevelText.name(lv), a.f48, Palette.INK, wrap = true)).width(460f).padTop(4f).row()
            left.add(kit.label(L.t("level.info_line", "area" to GameContent.areaNumber(lv.id), "idea" to LevelText.idea(lv), "d" to lv.difficulty), a.f22, Palette.INK_SOFT, wrap = true)).width(460f).padTop(6f).row()
            left.add(kit.stars(d.stars(lv.id), 60f)).padTop(12f).row()
            val mech = Table()
            val ms = if (lv.mechanics.isEmpty()) listOf(Mechanic.NORMAL) else lv.mechanics
            ms.forEach { m -> mech.add(Table().apply { add(Image(a.ui("b_" + m.icon))).size(56f).row(); add(kit.label(m.short, a.f22, Palette.INK_SOFT)) }).pad(3f) }
            left.add(kit.label(L.t("level.rules_here"), a.f22, Palette.INK)).padTop(12f).row()
            left.add(mech).padTop(4f).row()
            if (GameContent.hasPiece(lv.id)) {
                val got = lv.id in d.pieces
                left.add(Table().apply { add(Image(a.ui("b_puzzle"))).size(40f).padRight(8f); add(kit.label(if (got) L.t("level.piece_found") else L.t("level.piece_here"), a.f22, Palette.INK)) }).padTop(8f).row()
            }
            val right = Table()
            val chosen = LinkedHashSet<PowerupType>()
            val owned = d.inventory.filter { it.value > 0 }
            if (owned.isNotEmpty()) {
                right.add(kit.label(L.t("level.boosts"), a.f22, Palette.INK)).row()
                val grid = Table()
                owned.entries.forEachIndexed { i, (t, n) ->
                    val cell = Table().apply { background = kit.patch("card"); pad(6f) }
                    cell.add(Image(a.ui("b_" + t.icon))).size(54f).row()
                    cell.add(kit.label("x$n", a.f22, Palette.INK))
                    cell.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
                    cell.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                        override fun clicked(e: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                            kit.click()
                            if (t in chosen) chosen.remove(t) else if (chosen.size < 2) chosen.add(t)
                            cell.background = kit.patch(if (t in chosen) "card_sel" else "card")
                        }
                    })
                    grid.add(cell).size(84f, 96f).pad(3f)
                    if (i % 3 == 2) grid.row()
                }
                right.add(grid).padTop(6f).row()
            }
            right.add(kit.button(L.t("common.start"), "green", "g_play", a.f48o) {
                close()
                screen.startLevel(lv, boosts = { chosen.filter { game.profile.usePowerup(it) } })
            }).width(300f).height(112f).padTop(16f).row()
            // kalıcı can: başarısız deneme 1 can götürür (ilk bölümler hariç)
            if (game.lives.costs(lv.id)) right.add(Table().apply { add(screen.livesChip()).padRight(8f); add(kit.label(L.t("lives.cost_hint"), a.f22, Palette.INK_SOFT, wrap = true)).width(170f) }).padTop(10f).row()
            right.add(kit.button(L.t("common.close"), "white", font = a.f36, textColor = Palette.INK) { close() }).width(300f).height(96f).padTop(10f)
            p.add(left).top().padRight(24f)
            p.add(right).top()
        }
    }

    private fun scrollRow(row: Table) = ScrollPane(row).apply { setScrollingDisabled(false, true) }
}

class MapSelectScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    override val bannerAllowed get() = true // kaydırılabilir liste: afiş içeriğin altında durur
    override fun build() {
        val d = game.profile.data
        root.add(header(L.t("maps.title"))).growX().padTop(20f).row()
        val list = Table()
        for (m in GameContent.maps) {
            val unlocked = m.firstLevel <= d.highestUnlocked
            val stars = (m.firstLevel..m.lastLevel).sumOf { d.stars(it) }
            val done = (m.firstLevel..m.lastLevel).count { d.isCompleted(it) }
            val card = Table().apply { background = kit.patch(if (unlocked) "panel" else "panel_blue"); pad(26f) }
            val left = Table()
            left.add(kit.label(L.t("maps.map_n", "n" to m.id), a.f22, Palette.INK_SOFT, Align.left)).left().row()
            left.add(kit.label(m.name, a.f48, Palette.INK, Align.left)).left().row()
            left.add(kit.label(m.subtitle, a.f22, Palette.INK_SOFT, Align.left, wrap = true)).width(400f).left().row()
            val stats = Table()
            stats.add(Image(a.ui("b_star"))).size(36f); stats.add(kit.label(" $stars/150", a.f22, Palette.INK)).padRight(16f)
            stats.add(Image(a.ui("b_puzzle"))).size(36f); stats.add(kit.label(" ${CollectionManager.piecesOf(d, m.id)}/5", a.f22, Palette.INK)).padRight(16f)
            stats.add(kit.label(L.t("maps.progress", "done" to done), a.f22, Palette.INK))
            left.add(stats).left().padTop(8f).row()
            left.add(kit.bar(done / 50f, "bar_fill", 18f)).width(400f).height(18f).left().padTop(8f)
            card.add(left).expandX().left()
            if (unlocked) card.add(kit.iconButton("g_play", "green", 110f, 60f) { game.go(LevelSelectScreen(game, m.id)) }).size(110f)
            else card.add(Image(a.ui("g_lock_gray"))).size(80f)
            card.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
            card.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                override fun clicked(e: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                    if (unlocked) { kit.click(); game.go(LevelSelectScreen(game, m.id)) } else toast(L.t("maps.locked", "map" to GameContent.maps[m.id - 2].name), "g_lock_gray")
                }
            })
            list.add(card).width(590f).pad(0f, 8f, 16f, 8f)
            if (m.id % 2 == 0) list.row()
        }
        root.add(scroll(list)).grow().padTop(12f)
    }
}

/**
 * Bölüm haritası: harita manzarası üzerinde kıvrılarak ilerleyen noktalı bir yol ve yol boyunca 50 yuvarlak bölüm düğmesi.
 * Yatayda kaydırılır; arka plan kaydırmayla birlikte paralaks hareket eder. Her 5 bölümde bir alan tabelası vardır.
 */
class LevelSelectScreen(game: BesSaniyelikDunya, private val map: Int) : BaseScreen(game) {
    private lateinit var sp: ScrollPane
    init {
        background?.setMap(map)
        background?.showRunner = false
        background?.scrollOverride = { if (this::sp.isInitialized) sp.visualScrollX / 70f else 0f }
    }

    companion object {
        const val SPACING = 168f
        const val MARGIN = 220f
    }

    override fun build() {
        game.audio.music("map_%02d".format(map))
        val d = game.profile.data
        val m = GameContent.maps[map - 1]
        root.add(header(m.name)).growX().padTop(14f).row()
        val path = LevelPath(this, d, m)
        sp = ScrollPane(path).apply { setScrollingDisabled(false, true); fadeScrollBars = true; setOverscroll(true, false); setFlickScroll(true) }
        root.add(sp).grow()
        // şu anki bölüme kaydır
        val cur = (d.highestUnlocked - m.firstLevel).coerceIn(0, 49)
        stage.addAction(Actions.delay(0.05f, Actions.run {
            sp.layout()
            sp.scrollX = (MARGIN + cur * SPACING - sp.width / 2f).coerceIn(0f, sp.maxX)
            sp.updateVisualScroll()
        }))
    }

    fun openLevel(id: Int) {
        if (game.profile.data.isUnlocked(id)) LevelInfo.show(this, game, LevelRepository.level(id)) else toast(L.t("level.locked"), "g_lock_gray")
    }

    override fun onBack() { game.go(MapSelectScreen(game)) }
}

/** Yol ve düğmeleri çizen, mutlak konumlu çocuklar barındıran grup. */
class LevelPath(private val screen: LevelSelectScreen, private val d: SaveData, private val m: MapDef) : com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup() {
    private val a = screen.game.assets
    private val kit = screen.game.kit
    private val pts = ArrayList<com.badlogic.gdx.math.Vector2>()
    private val dot = a.ui("soft")
    private var t = 0f
    private val current = (d.highestUnlocked - m.firstLevel).coerceIn(0, 49)
    private val currentNode = m.firstLevel + current

    override fun getPrefWidth() = LevelSelectScreen.MARGIN * 2 + 49 * LevelSelectScreen.SPACING
    override fun getPrefHeight() = 560f

    init {
        for (i in 0 until 50) {
            val x = LevelSelectScreen.MARGIN + i * LevelSelectScreen.SPACING
            // kıvrımlı yol: iki farklı dalganın toplamı, her haritada farklı bir şekil
            val y = 270f + 120f * kotlin.math.sin(i * 0.55f + m.id) + 40f * kotlin.math.sin(i * 1.3f + m.id * 2f)
            pts.add(com.badlogic.gdx.math.Vector2(x, y))
        }
        for (i in 0 until 50) addNode(i)
        // alan tabelaları
        for (area in 0 until 10) {
            val i = area * GameContent.LEVELS_PER_AREA
            val first = m.firstLevel + i
            val idea = GameContent.stageOf(first)
            val stars = (first until first + 5).sumOf { d.stars(it) }
            val sign = Table().apply { background = kit.patch("banner"); pad(6f, 16f, 10f, 16f) }
            sign.add(kit.label(L.t("level.area_n", "n" to GameContent.areaNumber(first)), a.f22, Palette.INK_SOFT)).row()
            sign.add(kit.label(idea.title, a.f28, Palette.INK)).row()
            sign.add(Table().apply { add(Image(a.ui("b_star"))).size(22f); add(kit.label(" $stars/15", a.f22, Palette.INK_SOFT)) })
            sign.pack()
            val p = pts[i]
            val above = p.y < 300f
            sign.setPosition(p.x - sign.width / 2f, if (above) p.y + 74f else p.y - 74f - sign.height)
            addActor(sign)
        }
    }

    private fun addNode(i: Int) {
        val id = m.firstLevel + i
        val kind = GameContent.kindOf(id)
        val unlocked = d.isUnlocked(id)
        val col = when { !unlocked -> "gray"; kind == LevelKind.FINAL || kind == LevelKind.GRAND_FINAL -> "pink"; kind == LevelKind.CHALLENGE -> "orange"; kind == LevelKind.TUTORIAL -> "turq"; d.isCompleted(id) -> "yellow"; else -> "green" }
        val big = kind == LevelKind.FINAL || kind == LevelKind.GRAND_FINAL || kind == LevelKind.CHALLENGE
        val size = if (big) 112f else 96f
        val node = UiButton(com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(a.ui("node_$col")), kit)
        if (unlocked) node.add(kit.label(((id - 1) % 50 + 1).toString(), if (big) a.f48o else a.f36o, com.badlogic.gdx.graphics.Color.WHITE)).padBottom(4f)
        else node.add(Image(a.ui("g_lock"))).size(size * 0.42f)
        node.onClick { screen.openLevel(id) }
        node.setSize(size, size)
        node.setPosition(pts[i].x - size / 2f, pts[i].y - size / 2f)
        addActor(node)
        if (d.isCompleted(id)) {
            val st = kit.stars(d.stars(id), 26f)
            st.pack(); st.setPosition(pts[i].x - st.width / 2f, pts[i].y - size / 2f - 22f)
            addActor(st)
        }
        if (kind == LevelKind.FINAL || kind == LevelKind.GRAND_FINAL) {
            val crown = Image(a.ui("b_crown")); crown.setSize(56f, 56f); crown.setPosition(pts[i].x - 28f, pts[i].y + size / 2f - 14f)
            addActor(crown)
        }
        if (id == currentNode) {
            val av = AvatarActor(screen.game, 96f, null, CharState.IDLE)
            av.setSize(96f, 96f)
            av.setPosition(pts[i].x - 48f, pts[i].y + size / 2f - 8f)
            av.addAction(Actions.forever(Actions.sequence(Actions.moveBy(0f, 10f, 0.5f, com.badlogic.gdx.math.Interpolation.sine), Actions.moveBy(0f, -10f, 0.5f, com.badlogic.gdx.math.Interpolation.sine))))
            addActor(av)
        }
    }

    override fun act(delta: Float) { super.act(delta); t += delta }

    override fun draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: Float) {
        // noktalı yol: düğümler arasında yumuşak eğri (Catmull-Rom); geçilen kısım altın, kalan beyaz
        val tmp = com.badlogic.gdx.math.Vector2()
        val spline = com.badlogic.gdx.math.CatmullRomSpline(pts.toTypedArray(), false)
        val n = pts.size - 1
        val steps = n * 9
        for (k in 0..steps) {
            val u = k / steps.toFloat()
            spline.valueAt(tmp, u)
            val seg = u * n
            val done = seg < current
            val px = x + tmp.x; val py = y + tmp.y
            batch.setColor(0.15f, 0.22f, 0.35f, 0.35f * parentAlpha)
            batch.draw(dot, px - 16f, py - 19f, 32f, 32f)
            if (done) batch.setColor(1f, 0.82f, 0.25f, parentAlpha) else batch.setColor(1f, 1f, 1f, parentAlpha)
            batch.draw(dot, px - 14f, py - 14f, 28f, 28f)
        }
        // şu anki bölümün etrafında nabız
        val cp = pts[current]
        val pulse = 1f + 0.12f * kotlin.math.sin(t * 5f)
        batch.setColor(1f, 0.95f, 0.6f, 0.55f * parentAlpha)
        val r = 82f * pulse
        batch.draw(dot, x + cp.x - r, y + cp.y - r, r * 2, r * 2)
        batch.setColor(1f, 1f, 1f, 1f)
        super.draw(batch, parentAlpha)
    }
}

class EndlessIntroScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    override fun build() {
        game.audio.music("endless")
        background?.setMap(10)
        val d = game.profile.data
        root.add(header(L.t("endless.title"))).growX().padTop(20f).row()
        val p = kit.panel()
        p.add(Image(a.ui("b_infinity"))).size(96f).row()
        p.add(kit.label(L.t("endless.question"), a.f36, Palette.INK, wrap = true)).width(520f).padTop(6f).row()
        p.add(kit.label(L.t("endless.desc"), a.f22, Palette.INK_SOFT, wrap = true)).width(520f).padTop(6f).row()
        val st = Table()
        fun stat(icon: String, title: String, v: String) { st.add(Table().apply { background = kit.patch("card"); pad(12f); add(Image(a.ui(icon))).size(48f).row(); add(kit.label(v, a.f36, Palette.INK)).row(); add(kit.label(title, a.f22, Palette.INK_SOFT, wrap = true)).width(150f) }).width(168f).pad(5f) }
        stat("b_trophy", L.t("endless.best"), d.stats.endlessBestScore.toString())
        stat("b_bolt", L.t("endless.longest"), L.t("unit.seconds", "v" to L.dec(d.stats.endlessBestSeconds, 0)))
        stat("b_map", L.t("endless.farthest"), L.t("unit.meters", "v" to L.dec(d.stats.endlessBestDistance, 0)))
        p.add(st).padTop(12f).row()
        val side = kit.panel()
        side.add(kit.label(L.t("endless.records"), a.f28, Palette.INK)).row()
        if (d.endlessRecords.isEmpty()) side.add(kit.label(L.t("endless.no_records"), a.f22, Palette.INK_SOFT, wrap = true)).width(320f).padTop(6f).row()
        val rec = Table()
        d.endlessRecords.take(5).forEachIndexed { i, r ->
            rec.add(kit.label("${i + 1}.", a.f22, Palette.INK_SOFT)).width(40f)
            rec.add(kit.label(L.n("unit.points", r.score), a.f22, Palette.INK, Align.left)).width(150f).left()
            rec.add(kit.label(L.t("unit.seconds", "v" to L.dec(r.seconds, 0)), a.f22, Palette.INK_SOFT)).row()
        }
        side.add(rec).padTop(4f).row()
        val ms = d.missions.daily.filter { it.type in setOf(MissionType.ENDLESS_SCORE, MissionType.ENDLESS_SURVIVE, MissionType.PLAY_ENDLESS) }
        if (ms.isNotEmpty()) {
            side.add(kit.label(L.t("endless.today_missions"), a.f22, Palette.INK)).padTop(12f).row()
            ms.forEach { m -> side.add(kit.label("• ${m.type.text(m.target)} (${m.progress.coerceAtMost(m.target)}/${m.target})", a.f22, Palette.INK_SOFT, wrap = true)).width(320f).row() }
        }
        side.add(kit.button(L.t("common.start"), "turq", "g_play", a.f48o) {
            val seed = System.nanoTime()
            game.go(GameScreen(game, null, seed, emptyList()))
        }).width(320f).height(116f).padTop(16f)
        val body = Table()
        body.add(p).width(600f).padRight(20f)
        body.add(side).width(380f)
        root.add(body).expand().center()
    }
}
