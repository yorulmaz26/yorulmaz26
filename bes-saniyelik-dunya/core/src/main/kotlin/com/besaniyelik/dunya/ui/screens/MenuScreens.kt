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

class SplashScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    private var t = 0f
    private var went = false
    override fun build() {
        game.audio.music("menu")
        val logo = Image(a.ui("logo"))
        root.add().height(330f).row()
        root.add(logo).size(640f, 300f).row()
        logo.popIn(0.1f)
        root.add(kit.label("Her 5 saniyede kurallar değişir!", a.f36o, Color.WHITE)).padTop(40f).row()
        val bar = kit.bar(0f, "bar_fill_y")
        root.add(bar).width(420f).height(28f).padTop(60f).row()
        bar.addAction(object : com.badlogic.gdx.scenes.scene2d.Action() {
            override fun act(delta: Float): Boolean { bar.value = (t / 1.4f).coerceAtMost(1f); return false }
        })
        root.add(kit.label("Yükleniyor…", a.f28, Palette.INK)).padTop(14f)
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
        info.add(kit.label(d.playerName, a.f28, Palette.INK, Align.left)).left().row()
        info.add(kit.label("Seviye ${d.playerLevel}", a.f22, Palette.INK_SOFT, Align.left)).left().row()
        info.add(kit.bar(d.xp / ProgressionManager.xpToNext(d.playerLevel).toFloat(), "bar_fill_b", 16f)).width(150f).height(16f).left().padTop(4f)
        card.add(info)
        card.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
        card.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() { override fun clicked(e: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) { kit.click(); game.go(ProfileScreen(game)) } })
        top.add(card).left().expandX().padLeft(20f)
        val cur = Table()
        cur.add(kit.currency("b_coin", d.coins.toString())).right().row()
        cur.add(kit.currency("b_gem", d.gems.toString())).right().padTop(6f)
        top.add(cur).right().padRight(20f)
        root.add(top).growX().padTop(24f).row()

        val logo = Image(a.ui("logo"))
        root.add(logo).size(600f, 280f).padTop(20f).row()
        logo.popIn()

        val next = d.highestUnlocked.coerceAtMost(500)
        val play = kit.button(if (d.levels.isEmpty()) "OYNA" else "DEVAM: BÖLÜM $next", "green", "g_play", a.f48o) {
            val lv = LevelRepository.level(next)
            LevelInfo.show(this, game, lv)
        }
        root.add(play).width(560f).height(140f).padTop(30f).row()
        play.popIn(0.1f)
        val row2 = Table()
        row2.add(kit.button("Haritalar", "yellow", "b_map", a.f36o) { game.go(MapSelectScreen(game)) }).width(272f).height(118f).padRight(16f)
        row2.add(kit.button("Sonsuz", "turq", "b_infinity", a.f36o) { game.go(EndlessIntroScreen(game)) }).width(272f).height(118f)
        root.add(row2).padTop(16f).row()
        row2.popIn(0.18f)

        // ikon ızgarası
        val grid = Table()
        val missionsReady = (d.missions.daily + d.missions.weekly).any { it.done && !it.claimed }
        val items = listOf(
            Triple("b_person", "Karakterler") { game.go(CharacterScreen(game)) },
            Triple("b_bag", "Mağaza") { game.go(ShopScreen(game)) },
            Triple("b_scroll", "Günlük") { game.go(MissionsScreen(game, weekly = false)) },
            Triple("b_calendar", "Haftalık") { game.go(MissionsScreen(game, weekly = true)) },
            Triple("b_trophy", "Başarılar") { game.go(AchievementsScreen(game)) },
            Triple("b_puzzle", "Koleksiyon") { game.go(CollectionScreen(game)) },
            Triple("b_gift", "Ödüller") { game.go(DailyRewardScreen(game)) },
            Triple("b_bolt", "Nasıl?") { game.go(HowToPlayScreen(game)) },
        )
        items.forEachIndexed { i, (icon, text, act) ->
            val cell = Table()
            val b = kit.iconButton(icon, "white", 120f, 76f) { act() }
            val stack = Stack(b)
            val dot = (icon == "b_gift" && game.profile.loginAvailable()) || ((icon == "b_scroll" || icon == "b_calendar") && missionsReady)
            if (dot) stack.add(Table().apply { top().right(); add(Image(a.ui("b_heart"))).size(0f); add(Image(a.ui("soft")).apply { color = Palette.RED }).size(34f) })
            cell.add(stack).size(120f).row()
            cell.add(kit.label(text, a.f22, Palette.INK)).padTop(2f)
            grid.add(cell).pad(8f, 14f, 8f, 14f)
            if (i % 4 == 3) grid.row()
            cell.popIn(0.2f + i * 0.03f)
        }
        val gp = Table().apply { background = kit.patch("panel"); pad(20f, 10f, 24f, 10f); add(grid) }
        root.add(gp).padTop(22f).row()
        root.add().expandY().row()
        val bottom = Table()
        bottom.add(kit.iconButton("g_settings", "blue", 96f, 54f) { game.go(SettingsScreen(game)) }).size(96f).padRight(14f)
        bottom.add(kit.iconButton(if (d.settings.music) "g_music" else "g_music_off", "pink", 96f, 50f) {
            d.settings.music = !d.settings.music; game.audio.applyVolume(); game.profile.save(); rebuild()
        }).size(96f)
        root.add(bottom).padBottom(30f).right().padRight(30f)
    }
    override fun onBack() { com.badlogic.gdx.Gdx.app.exit() }
}

/** Seçili karakteri UI içinde çizen aktör. */
class AvatarActor(private val game: BesSaniyelikDunya, private val sz: Float, var skinId: String? = null, var state: CharState = CharState.IDLE) : Actor() {
    private val r = CharacterRenderer(game.assets)
    private var t = 0f
    override fun act(delta: Float) { super.act(delta); t += delta }
    override fun draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: Float) {
        val skin = Catalog.skin(skinId ?: game.profile.data.selectedSkin)
        r.draw(batch, skin, x + width / 2, y + height * 0.42f, width * 0.78f, state, t, alpha = parentAlpha * color.a)
        batch.setColor(1f, 1f, 1f, 1f)
    }
}

object LevelInfo {
    /** Bölüm başlangıç diyaloğu: bilgiler, mekanikler, en iyi sonuç ve isteğe bağlı güçlendirme seçimi. */
    fun show(screen: BaseScreen, game: BesSaniyelikDunya, lv: LevelData) {
        val kit = game.kit; val a = game.assets
        val d = game.profile.data
        screen.dialog { p, close ->
            val kindText = when (lv.kind) {
                LevelKind.TUTORIAL -> "Eğitim Bölümü"; LevelKind.CHALLENGE -> "Meydan Okuma"; LevelKind.FINAL -> "Harita Finali"
                LevelKind.GRAND_FINAL -> "BÜYÜK FİNAL"; LevelKind.NORMAL -> "Bölüm"
            }
            p.add(kit.label("$kindText ${lv.id}", a.f28, Palette.INK_SOFT)).row()
            p.add(kit.label(lv.name, a.f48, Palette.INK, wrap = true)).width(560f).padTop(4f).row()
            p.add(kit.label("Alan ${GameContent.areaNumber(lv.id)}: ${lv.idea}  •  Zorluk ${lv.difficulty}/10", a.f22, Palette.INK_SOFT, wrap = true)).width(560f).padTop(6f).row()
            p.add(kit.stars(d.stars(lv.id), 64f)).padTop(16f).row()
            val mech = Table()
            val ms = if (lv.mechanics.isEmpty()) listOf(Mechanic.NORMAL) else lv.mechanics
            ms.forEach { m -> mech.add(Table().apply { add(Image(a.ui("b_" + m.icon))).size(64f).row(); add(kit.label(m.title.split(" ").first(), a.f22, Palette.INK_SOFT)) }).pad(4f) }
            p.add(kit.label("Bu bölümdeki kurallar", a.f22, Palette.INK)).padTop(16f).row()
            p.add(mech).padTop(6f).row()
            if (GameContent.hasPiece(lv.id)) {
                val got = lv.id in d.pieces
                p.add(Table().apply { add(Image(a.ui("b_puzzle"))).size(44f).padRight(8f); add(kit.label(if (got) "Gizli parça bulundu" else "Bu bölümde gizli bir parça var!", a.f22, Palette.INK)) }).padTop(10f).row()
            }
            // güçlendirme seçimi
            val chosen = LinkedHashSet<PowerupType>()
            val owned = d.inventory.filter { it.value > 0 }
            if (owned.isNotEmpty()) {
                p.add(kit.label("Başlangıç güçlendirmesi (en fazla 2)", a.f22, Palette.INK)).padTop(16f).row()
                val row = Table()
                owned.forEach { (t, n) ->
                    val cell = Table().apply { background = kit.patch("card"); pad(8f) }
                    cell.add(Image(a.ui("b_" + t.icon))).size(60f).row()
                    cell.add(kit.label("x$n", a.f22, Palette.INK))
                    cell.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
                    cell.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                        override fun clicked(e: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                            kit.click()
                            if (t in chosen) chosen.remove(t) else if (chosen.size < 2) chosen.add(t)
                            cell.background = kit.patch(if (t in chosen) "card_sel" else "card")
                        }
                    })
                    row.add(cell).size(96f, 110f).pad(4f)
                }
                p.add(scrollRow(row)).width(560f).height(120f).row()
            }
            val btns = Table()
            btns.add(kit.button("Kapat", "white", font = a.f36, textColor = Palette.INK) { close() }).width(220f).height(110f).padRight(14f)
            btns.add(kit.button("BAŞLA", "green", "g_play", a.f48o) {
                close()
                val boosts = chosen.filter { game.profile.usePowerup(it) }
                game.go(GameScreen(game, lv, null, boosts))
            }).width(320f).height(120f)
            p.add(btns).padTop(24f)
        }
    }

    private fun scrollRow(row: Table) = ScrollPane(row).apply { setScrollingDisabled(false, true) }
}

class MapSelectScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    override fun build() {
        val d = game.profile.data
        root.add(header("Haritalar")).growX().padTop(20f).row()
        val list = Table()
        for (m in GameContent.maps) {
            val unlocked = m.firstLevel <= d.highestUnlocked
            val stars = (m.firstLevel..m.lastLevel).sumOf { d.stars(it) }
            val done = (m.firstLevel..m.lastLevel).count { d.isCompleted(it) }
            val card = Table().apply { background = kit.patch(if (unlocked) "panel" else "panel_blue"); pad(26f) }
            val left = Table()
            left.add(kit.label("Harita ${m.id}", a.f22, Palette.INK_SOFT, Align.left)).left().row()
            left.add(kit.label(m.name, a.f48, Palette.INK, Align.left)).left().row()
            left.add(kit.label(m.subtitle, a.f22, Palette.INK_SOFT, Align.left, wrap = true)).width(380f).left().row()
            val stats = Table()
            stats.add(Image(a.ui("b_star"))).size(36f); stats.add(kit.label(" $stars/150", a.f22, Palette.INK)).padRight(16f)
            stats.add(Image(a.ui("b_puzzle"))).size(36f); stats.add(kit.label(" ${CollectionManager.piecesOf(d, m.id)}/5", a.f22, Palette.INK)).padRight(16f)
            stats.add(kit.label("$done/50  •  10 alan", a.f22, Palette.INK))
            left.add(stats).left().padTop(8f).row()
            left.add(kit.bar(done / 50f, "bar_fill", 18f)).width(380f).height(18f).left().padTop(8f)
            card.add(left).expandX().left()
            if (unlocked) card.add(kit.iconButton("g_play", "green", 110f, 60f) { game.go(LevelSelectScreen(game, m.id)) }).size(110f)
            else card.add(Image(a.ui("g_lock_gray"))).size(80f)
            card.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
            card.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                override fun clicked(e: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                    if (unlocked) { kit.click(); game.go(LevelSelectScreen(game, m.id)) } else toast("Önce ${GameContent.maps[m.id - 2].name} finalini geç!", "g_lock_gray")
                }
            })
            list.add(card).width(660f).padBottom(18f).row()
        }
        root.add(scroll(list)).grow().padTop(12f)
    }
}

class LevelSelectScreen(game: BesSaniyelikDunya, private val map: Int) : BaseScreen(game) {
    init { background?.setMap(map) }
    override fun build() {
        game.audio.music("map_%02d".format(map))
        val d = game.profile.data
        val m = GameContent.maps[map - 1]
        root.add(header(m.name)).growX().padTop(20f).row()
        val list = Table()
        for (area in 0 until 10) {
            val first = m.firstLevel + area * GameContent.LEVELS_PER_AREA
            val idea = GameContent.stageOf(first)
            val no = GameContent.areaNumber(first)
            val stars = (first until first + 5).sumOf { d.stars(it) }
            val head = Table().apply { background = kit.patch("banner"); pad(10f, 24f, 14f, 24f) }
            val ht = Table()
            ht.add(kit.label("Alan $no: ${idea.title}", a.f28, Palette.INK, Align.left)).expandX().left()
            ht.add(Image(a.ui("b_star"))).size(30f); ht.add(kit.label(" $stars/15", a.f22, Palette.INK_SOFT))
            head.add(ht).width(580f).row()
            head.add(kit.label(idea.desc, a.f22, Palette.INK_SOFT, Align.left, wrap = true)).width(580f).left()
            list.add(head).width(640f).padTop(16f).padBottom(6f).row()
            val grid = Table()
            for (i in 0 until GameContent.LEVELS_PER_AREA) grid.add(levelButton(first + i, d)).size(116f, 136f).pad(6f)
            list.add(grid).row()
        }
        val sp = scroll(list)
        root.add(sp).grow().padTop(8f)
        // açık olan son bölüme kaydır
        val target = ((d.highestUnlocked - m.firstLevel) / GameContent.LEVELS_PER_AREA).coerceIn(0, 9)
        stage.addAction(Actions.delay(0.05f, Actions.run { sp.layout(); sp.scrollY = (target * 262f).coerceAtMost(sp.maxY) }))
    }

    private fun levelButton(id: Int, d: SaveData): Actor {
        val kind = GameContent.kindOf(id)
        val unlocked = d.isUnlocked(id)
        val color = when { !unlocked -> "gray"; kind == LevelKind.FINAL || kind == LevelKind.GRAND_FINAL -> "pink"; kind == LevelKind.CHALLENGE -> "orange"; kind == LevelKind.TUTORIAL -> "turq"; else -> "yellow" }
        val b = UiButton(kit.patch("btn_$color"), kit)
        b.pad(6f, 4f, 16f, 4f)
        if (unlocked) {
            b.add(kit.label(((id - 1) % 50 + 1).toString(), a.f36o, Color.WHITE)).row()
            b.add(kit.stars(d.stars(id), 28f)).padTop(2f)
            if (kind == LevelKind.FINAL || kind == LevelKind.GRAND_FINAL) b.add(Image(a.ui("b_crown"))).size(0f)
        } else b.add(Image(a.ui("g_lock"))).size(50f)
        b.onClick {
            if (unlocked) LevelInfo.show(this, game, LevelRepository.level(id)) else toast("Bu bölüm henüz kilitli.", "g_lock_gray")
        }
        return b
    }

    override fun onBack() { game.go(MapSelectScreen(game)) }
}

class EndlessIntroScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    override fun build() {
        game.audio.music("endless")
        background?.setMap(10)
        val d = game.profile.data
        root.add(header("Sonsuz Dünya")).growX().padTop(20f).row()
        val p = kit.panel()
        p.add(Image(a.ui("b_infinity"))).size(120f).row()
        p.add(kit.label("Ne kadar uzağa gidebilirsin?", a.f36, Palette.INK)).padTop(8f).row()
        p.add(kit.label("Platformlar sonsuza kadar sürer, hız giderek artar ve her 5 saniyede kurallar değişir. Henüz öğrenmediğin kurallar gelince kısa bir öğretici gösterilir.",
            a.f22, Palette.INK_SOFT, wrap = true)).width(560f).padTop(8f).row()
        val st = Table()
        fun stat(icon: String, title: String, v: String) { st.add(Table().apply { background = kit.patch("card"); pad(14f); add(Image(a.ui(icon))).size(54f).row(); add(kit.label(v, a.f36, Palette.INK)).row(); add(kit.label(title, a.f22, Palette.INK_SOFT)) }).width(180f).pad(6f) }
        stat("b_trophy", "En iyi puan", d.stats.endlessBestScore.toString())
        stat("b_bolt", "En uzun süre", "%.0f sn".format(d.stats.endlessBestSeconds))
        stat("b_map", "En uzak", "%.0f m".format(d.stats.endlessBestDistance))
        p.add(st).padTop(16f).row()
        if (d.endlessRecords.isNotEmpty()) {
            p.add(kit.label("Rekorlar", a.f28, Palette.INK)).padTop(14f).row()
            val rec = Table()
            d.endlessRecords.take(5).forEachIndexed { i, r ->
                rec.add(kit.label("${i + 1}.", a.f22, Palette.INK_SOFT)).width(50f)
                rec.add(kit.label("${r.score} puan", a.f22, Palette.INK, Align.left)).width(220f).left()
                rec.add(kit.label("%.0f sn  •  %.0f m".format(r.seconds, r.distance), a.f22, Palette.INK_SOFT)).row()
            }
            p.add(rec).row()
        }
        val ms = d.missions.daily.filter { it.type in setOf(MissionType.ENDLESS_SCORE, MissionType.ENDLESS_SURVIVE, MissionType.PLAY_ENDLESS) }
        if (ms.isNotEmpty()) {
            p.add(kit.label("Bugünün sonsuz görevleri", a.f28, Palette.INK)).padTop(14f).row()
            ms.forEach { m -> p.add(kit.label("• ${m.type.text(m.target)} (${m.progress.coerceAtMost(m.target)}/${m.target})", a.f22, Palette.INK_SOFT)).row() }
        }
        root.add(p).width(660f).padTop(20f).row()
        root.add().expandY().row()
        root.add(kit.button("BAŞLA", "turq", "g_play", a.f48o) {
            val seed = System.nanoTime()
            game.go(GameScreen(game, null, seed, emptyList()))
        }).width(480f).height(140f).padBottom(60f)
    }
}
