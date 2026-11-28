package com.besaniyelik.dunya.ui.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.besaniyelik.dunya.ads.AdDirector
import com.besaniyelik.dunya.BesSaniyelikDunya
import com.besaniyelik.dunya.audio.Sfx
import com.besaniyelik.dunya.level.GameContent
import com.besaniyelik.dunya.level.PowerupType
import com.besaniyelik.dunya.progress.*
import com.besaniyelik.dunya.render.CharState
import com.besaniyelik.dunya.render.rgb
import com.besaniyelik.dunya.sim.Mechanic
import com.besaniyelik.dunya.ui.Palette
import com.besaniyelik.dunya.ui.popIn
import com.besaniyelik.dunya.GameConfig
import com.besaniyelik.dunya.i18n.L
import com.besaniyelik.dunya.i18n.PlayerNames

private fun Table.onTap(kit: com.besaniyelik.dunya.ui.UiKit, f: () -> Unit) {
    touchable = Touchable.enabled
    addListener(object : ClickListener() { override fun clicked(event: InputEvent?, x: Float, y: Float) { kit.click(); f() } })
}

class CharacterScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    private var selected: String = game.profile.data.selectedSkin
    private var tab = 0
    override fun build() {
        val d = game.profile.data
        root.add(header(L.t("chars.title"))).growX().padTop(20f).row()
        val tabs = Table()
        listOf(L.t("chars.tab_skins"), L.t("chars.tab_trails")).forEachIndexed { i, t ->
            val b = Table().apply { background = kit.patch(if (tab == i) "tab_sel" else "tab"); pad(10f, 26f, 14f, 26f); add(kit.label(t, a.f28, Palette.INK)) }
            b.onTap(kit) { tab = i; rebuild() }
            tabs.add(b).pad(6f)
        }
        root.add(tabs).padTop(8f).row()
        if (tab == 0) skins(d) else trails(d)
    }

    private fun skins(d: SaveData) {
        val skin = Catalog.skin(selected)
        val preview = kit.panel()
        val av = AvatarActor(game, 220f, selected, CharState.HAPPY)
        preview.add(av).size(220f).row()
        preview.add(kit.label(skin.name, a.f48, Palette.INK)).row()
        val owned = skin.id in d.ownedSkins
        val unlock = skin.unlock
        when {
            owned && d.selectedSkin == skin.id -> preview.add(kit.label(L.t("chars.selected"), a.f28, Palette.GRASS)).padTop(6f)
            owned -> preview.add(kit.button(L.t("common.select"), "green", "g_check") { game.profile.selectSkin(skin.id); rebuild() }).width(300f).height(110f).padTop(6f)
            unlock is Unlock.Coins || unlock is Unlock.Gems -> {
                val icon = if (unlock is Unlock.Coins) "b_coin" else "b_gem"
                val price = if (unlock is Unlock.Coins) unlock.price else (unlock as Unlock.Gems).price
                preview.add(kit.button(L.t("chars.buy", "price" to price), "yellow", icon) {
                    val r = game.profile.buySkin(skin.id)
                    if (r == PurchaseResult.OK) { game.audio.play(Sfx.REWARD); game.profile.selectSkin(skin.id) }
                    toast(r.message, icon); rebuild()
                }).width(360f).height(110f).padTop(6f)
            }
            else -> preview.add(kit.label(L.t("chars.locked", "how" to unlock.describe()), a.f28, Palette.INK_SOFT, wrap = true)).width(380f).padTop(6f)
        }
        val grid = Table()
        Catalog.skins.forEachIndexed { i, s ->
            val cell = Table().apply { background = kit.patch(if (s.id == selected) "card_sel" else "card"); pad(6f) }
            val av2 = AvatarActor(game, 96f, s.id)
            if (s.id !in d.ownedSkins) av2.color.a = 0.45f
            cell.add(av2).size(96f).row()
            cell.add(kit.label(s.name, a.f22, Palette.INK, fit = true)).width(128f).row()
            if (s.id !in d.ownedSkins) cell.add(Image(a.ui("g_lock_gray"))).size(26f)
            cell.onTap(kit) { selected = s.id; rebuild() }
            grid.add(cell).size(140f, 168f).pad(5f)
            if (i % 5 == 4) grid.row()
        }
        val body = Table()
        body.add(preview).width(420f).top().padRight(16f)
        body.add(scroll(grid)).grow()
        root.add(body).grow().pad(8f, 20f, 10f, 20f)
    }

    private fun trails(d: SaveData) {
        val list = Table()
        for (t in Catalog.trails) {
            val row = Table().apply { background = kit.patch("panel"); pad(22f) }
            val sw = Table()
            t.colors.take(5).forEach { c -> sw.add(Image(a.ui("soft")).apply { color = rgb(c) }).size(44f) }
            row.add(sw).width(200f)
            val info = Table()
            info.add(kit.label(t.name, a.f36, Palette.INK, Align.left)).left().row()
            info.add(kit.label(t.unlock.describe(), a.f22, Palette.INK_SOFT, Align.left)).left()
            row.add(info).expandX().left()
            when {
                d.selectedTrail == t.id -> row.add(Image(a.ui("b_star"))).size(64f)
                t.id in d.ownedTrails -> row.add(kit.button(L.t("common.select"), "green") { game.profile.selectTrail(t.id); rebuild() }).width(150f).height(96f)
                t.unlock is Unlock.Coins || t.unlock is Unlock.Gems -> row.add(kit.button(L.t("common.buy"), "yellow", if (t.unlock is Unlock.Coins) "b_coin" else "b_gem") {
                    val r = game.profile.buyTrail(t.id); if (r == PurchaseResult.OK) { game.audio.play(Sfx.REWARD); game.profile.selectTrail(t.id) }
                    toast(r.message); rebuild()
                }).width(160f).height(96f)
                else -> row.add(Image(a.ui("g_lock_gray"))).size(60f)
            }
            list.add(row).width(900f).padBottom(12f).row()
        }
        root.add(scroll(list)).grow().padTop(12f)
    }
}

class ShopScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    override val bannerAllowed get() = true // kaydırılabilir liste: afiş içeriğin altında durur
    override val watchesAdReadiness get() = true
    private var gemButton: com.besaniyelik.dunya.ui.UiButton? = null
    private var gemStatus: Label? = null
    /** Reklam yüklenince düğme açılır (liste yeniden kurulmaz: kaydırma konumu bozulmaz). */
    override fun onAdReadyChanged(ready: Boolean) {
        if (gemButton == null) { if (ready && game.ads.gemCardVisible()) rebuild(); return } // kart ilk kez beliriyor
        gemButton?.enabled = ready
        gemStatus?.setText(if (ready) "" else L.t("ads.preparing"))
    }
    override fun build() {
        val d = game.profile.data
        root.add(header(L.t("shop.title"))).growX().padTop(20f).row()
        val list = Table()
        // Kalıcı canlar: 20 dakikada bir yenilenir; beklemek istemeyen oyuncu elmasla doldurabilir ya da reklam izleyebilir
        if (game.lives.enabled) {
            val lc = Table().apply { background = kit.patch("panel"); pad(14f, 22f, 18f, 22f) }
            lc.add(livesChip()).padRight(16f)
            lc.add(kit.label(L.t("shop.lives_text", "min" to GameConfig.LIFE_REGEN_SECONDS / 60), a.f22, Palette.INK_SOFT, Align.left, wrap = true)).width(420f).padRight(16f)
            lc.add(kit.button(L.t("shop.lives_open"), "pink", "b_heart", a.f28) { showLives() }).width(300f).height(92f)
            list.add(lc).padTop(6f).padBottom(10f).row()
        }
        // İsteğe bağlı ödüllü reklam: günde sınırlı sayıda ücretsiz elmas. Kart, günlük hak bitene kadar durur; düğme
        // yalnızca reklam yüklüyken açıktır (reklam tek kullanımlıktır, izlendikten sonra yenisi birkaç saniyede yüklenir).
        gemButton = null; gemStatus = null
        if (game.ads.gemCardVisible()) {
            val ready = adReadyNow()
            val free = Table().apply { background = kit.patch("panel_yellow"); pad(14f, 22f, 18f, 22f) }
            free.add(Image(a.ui("b_gem"))).size(72f).padRight(16f)
            val txt = Table()
            txt.add(kit.label(L.t("shop.free_gems"), a.f28, Palette.INK, Align.left)).left().row()
            txt.add(kit.label(L.t("shop.free_gems_text", "gems" to L.n("unit.gems", AdDirector.GEMS_PER_AD), "left" to game.ads.gemAdsLeft()), a.f22, Palette.INK_SOFT, Align.left, wrap = true)).width(400f).left().row()
            val status = kit.label(if (ready) "" else L.t("ads.preparing"), a.f22, Palette.INK_SOFT, Align.left)
            txt.add(status).left()
            free.add(txt).padRight(16f)
            val btn = kit.button(L.n("ads.watch_gems", AdDirector.GEMS_PER_AD), "turq", "g_play", a.f28) {
                watchAd { val g = game.ads.grantGemAd(); if (g > 0) { game.audio.play(Sfx.REWARD); toast(L.n("toast.gems", g), "b_gem") }; rebuild() }
            }
            btn.enabled = ready
            free.add(btn).width(380f).height(92f)
            list.add(free).padTop(6f).padBottom(10f).row()
            gemButton = btn; gemStatus = status
        }
        list.add(kit.label(L.t("shop.powerups"), a.f36, Palette.INK)).padTop(6f).row()
        list.add(kit.label(L.t("shop.powerups_text"), a.f22, Palette.INK_SOFT, wrap = true)).width(620f).row()
        val grid = Table()
        Catalog.shopPowerups.entries.forEachIndexed { i, (t, price) ->
            val c = Table().apply { background = kit.patch("panel"); pad(20f, 14f, 22f, 14f) }
            c.add(Image(a.ui("b_" + t.icon))).size(84f).row()
            c.add(kit.label(t.title, a.f28, Palette.INK, wrap = true)).width(250f).row()
            c.add(kit.label(t.desc, a.f22, Palette.INK_SOFT, wrap = true)).width(250f).row()
            c.add(kit.label(L.t("shop.owned", "n" to (d.inventory[t] ?: 0)), a.f22, Palette.INK)).padTop(4f).row()
            c.add(kit.button("$price", "yellow", "b_coin", a.f36o) {
                val r = game.profile.buyPowerup(t); if (r == PurchaseResult.OK) game.audio.play(Sfx.REWARD); toast(r.message, "b_" + t.icon); rebuild()
            }).width(220f).height(96f).padTop(6f)
            grid.add(c).width(290f).pad(6f)
            if (i % 4 == 3) grid.row()
        }
        list.add(grid).padTop(8f).row()
        list.add(kit.label(L.t("shop.coin_packs"), a.f36, Palette.INK)).padTop(20f).row()
        val packs = Table()
        Catalog.coinPacks.forEachIndexed { i, (gems, coins) ->
            val c = Table().apply { background = kit.patch("panel_yellow"); pad(18f) }
            c.add(Image(a.ui("b_coin"))).size(70f).row()
            c.add(kit.label(L.n("unit.coins", coins), a.f28, Palette.INK)).row()
            c.add(kit.button("$gems", "purple", "b_gem", a.f28) {
                val r = game.profile.buyCoinPack(i); if (r == PurchaseResult.OK) game.audio.play(Sfx.REWARD); toast(r.message, "b_coin"); rebuild()
            }).width(170f).height(86f).padTop(6f)
            packs.add(c).width(205f).pad(4f)
        }
        list.add(packs).row()
        list.add(kit.label(L.t("shop.chars_hint"), a.f22, Palette.INK_SOFT, wrap = true)).width(600f).padTop(14f).row()
        list.add(kit.button(L.t("menu.characters"), "pink", "b_person") { game.go(CharacterScreen(game)) }).width(380f).height(110f).padTop(8f).padBottom(30f)
        root.add(scroll(list)).grow().padTop(10f)
    }
}

class MissionsScreen(game: BesSaniyelikDunya, private var weekly: Boolean) : BaseScreen(game) {
    override val bannerAllowed get() = true // kaydırılabilir liste: afiş içeriğin altında durur
    override fun build() {
        game.profile.refreshDaily()
        val d = game.profile.data
        root.add(header(if (weekly) L.t("missions.weekly_title") else L.t("missions.daily_title"))).growX().padTop(20f).row()
        val tabs = Table()
        listOf(L.t("menu.daily"), L.t("menu.weekly")).forEachIndexed { i, t ->
            val b = Table().apply { background = kit.patch(if ((i == 1) == weekly) "tab_sel" else "tab"); pad(10f, 30f, 14f, 30f); add(kit.label(t, a.f28, Palette.INK)) }
            b.onTap(kit) { weekly = i == 1; rebuild() }
            tabs.add(b).pad(6f)
        }
        root.add(tabs).padTop(8f).row()
        root.add(kit.label(if (weekly) L.t("missions.weekly_hint") else L.t("missions.daily_hint"), a.f22, Palette.INK_SOFT)).padTop(4f).row()
        val list = Table()
        val ms = if (weekly) d.missions.weekly else d.missions.daily
        for (m in ms) {
            val row = Table().apply { background = kit.patch(if (m.done && !m.claimed) "panel_yellow" else "panel"); pad(24f) }
            val info = Table()
            info.add(kit.label(m.type.text(m.target), a.f28, Palette.INK, Align.left, wrap = true)).width(400f).left().row()
            info.add(kit.bar(m.progress / m.target.toFloat(), "bar_fill", 22f)).width(400f).height(22f).left().padTop(8f).row()
            val rw = buildList { add("${m.progress.coerceAtMost(m.target)}/${m.target}"); add(L.n("unit.coins", m.rewardCoins)); if (m.rewardGems > 0) add(L.n("unit.gems", m.rewardGems)); add(L.t("unit.xp", "n" to m.rewardXp)) }
            info.add(kit.label(rw.joinToString("  •  "), a.f22, Palette.INK_SOFT, Align.left)).left().padTop(6f)
            row.add(info).expandX().left()
            when {
                m.claimed -> row.add(Image(a.ui("b_star"))).size(70f)
                m.done -> row.add(kit.button(L.t("missions.claim"), "green", "b_gift") {
                    game.profile.claimMission(m.id)?.let { r -> game.audio.play(Sfx.REWARD); toast(listOfNotNull("+" + L.n("unit.coins", r.coins), if (r.gems > 0) "+" + L.n("unit.gems", r.gems) else null, "+" + L.t("unit.xp", "n" to r.xp)).joinToString(", "), "b_gift") }
                    rebuild()
                }).width(160f).height(100f)
                else -> row.add(Image(a.ui("g_lock_gray"))).size(56f)
            }
            list.add(row).width(900f).padBottom(14f).row()
        }
        root.add(scroll(list)).grow().padTop(10f)
    }
}

class AchievementsScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    override val bannerAllowed get() = true // kaydırılabilir liste: afiş içeriğin altında durur
    override fun build() {
        val d = game.profile.data
        root.add(header(L.t("menu.achievements"))).growX().padTop(20f).row()
        val n = d.achievements.size
        root.add(kit.label(L.t("ach.count", "n" to n, "total" to Catalog.achievements.size), a.f28, Palette.INK)).padTop(8f).row()
        root.add(kit.bar(n / Catalog.achievements.size.toFloat(), "bar_fill_y", 22f)).width(520f).height(22f).padTop(6f).row()
        val list = Table()
        val sorted = Catalog.achievements.sortedBy { if (it.id in d.achievements) 1 else 0 }
        for (ach in sorted) {
            val got = ach.id in d.achievements
            val row = Table().apply { background = kit.patch(if (got) "panel_yellow" else "panel"); pad(20f) }
            row.add(Image(a.ui(if (got) "b_trophy" else "g_lock_gray"))).size(70f).padRight(14f)
            val info = Table()
            info.add(kit.label(ach.title, a.f28, Palette.INK, Align.left)).left().row()
            info.add(kit.label(ach.desc, a.f22, Palette.INK_SOFT, Align.left)).left().row()
            if (!got) {
                val p = AchievementManager.progress(d, ach)
                info.add(kit.bar(p / ach.target.toFloat(), "bar_fill", 16f)).width(380f).height(16f).left().padTop(6f).row()
                info.add(kit.label("$p / ${ach.target}", a.f22, Palette.INK_SOFT, Align.left)).left()
            }
            row.add(info).expandX().left()
            row.add(Table().apply { add(Image(a.ui("b_gem"))).size(40f); add(kit.label("${ach.gems}", a.f22, Palette.INK)) })
            list.add(row).width(900f).padBottom(10f).row()
        }
        root.add(scroll(list)).grow().padTop(10f)
    }
}

class CollectionScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    override val bannerAllowed get() = true // kaydırılabilir liste: afiş içeriğin altında durur
    override fun build() {
        val d = game.profile.data
        root.add(header(L.t("menu.collection"))).growX().padTop(20f).row()
        root.add(kit.label(L.t("coll.text"), a.f22, Palette.INK_SOFT, wrap = true)).width(600f).padTop(6f).row()
        val list = Table()
        for (m in GameContent.maps) {
            val have = CollectionManager.piecesOf(d, m.id)
            val done = m.id in d.collectionsClaimed
            val row = Table().apply { background = kit.patch(if (done) "panel_yellow" else "panel"); pad(20f) }
            val info = Table()
            info.add(kit.label(m.name, a.f36, Palette.INK, Align.left)).left().row()
            val pcs = Table()
            val ids = listOf(8, 18, 28, 38, 48).map { m.firstLevel - 1 + it }
            ids.forEach { id -> pcs.add(Image(a.ui("b_puzzle")).apply { if (id !in d.pieces) color = Color(0.6f, 0.65f, 0.75f, 0.35f) }).size(56f).pad(2f) }
            info.add(pcs).left().padTop(4f).row()
            info.add(kit.label(L.t("coll.reward", "reward" to CollectionManager.rewardName(m.id)) + if (done) L.t("coll.claimed") else "", a.f22, if (done) Palette.GRASS else Palette.INK_SOFT, Align.left)).left().padTop(4f).row()
            info.add(kit.label(L.t("coll.pieces", "list" to ids.joinToString(", ") { L.t("level.short", "n" to it) }), a.f22, Palette.INK_SOFT, Align.left, wrap = true)).width(560f).left()
            row.add(info).expandX().left()
            row.add(kit.label("$have/5", a.f36, Palette.INK))
            list.add(row).width(900f).padBottom(12f).row()
        }
        root.add(scroll(list)).grow().padTop(10f)
    }
}

class ProfileScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    override fun build() {
        val d = game.profile.data
        root.add(header(L.t("profile.title"))).growX().padTop(20f).row()
        val p = kit.panel()
        p.add(AvatarActor(game, 170f, null, CharState.HAPPY)).size(170f).row()
        p.add(kit.label(PlayerNames.display(d.playerName), a.f48, Palette.INK)).row()
        p.add(kit.label(L.t("profile.level", "n" to d.playerLevel), a.f28, Palette.INK_SOFT)).row()
        val need = ProgressionManager.xpToNext(d.playerLevel)
        p.add(kit.bar(d.xp / need.toFloat(), "bar_fill_b", 24f)).width(420f).height(24f).padTop(8f).row()
        val nextLv = d.playerLevel + 1
        val nextReward = if (ProgressionManager.isPlainLevelUp(nextLv)) L.n("unit.coins", ProgressionManager.rewardFor(nextLv).coins) else ProgressionManager.levelUpText(nextLv)
        p.add(kit.label(L.t("profile.next", "xp" to d.xp, "need" to need, "level" to nextLv, "reward" to nextReward), a.f22, Palette.INK_SOFT, wrap = true)).width(480f).padTop(4f).row()
        p.add(kit.button(L.t("profile.rename"), "white", font = a.f28, textColor = Palette.INK) {
            d.playerName = PlayerNames.next(d.playerName); game.profile.save(); rebuild()
        }).width(300f).height(90f).padTop(10f).row()
        val st = d.stats
        val grid = Table()
        val stats = listOf(
            L.t("stats.levels") to "${st.levelsCompleted}/500", L.t("stats.stars") to "${d.totalStars}/1500", L.t("stats.coins") to "${st.coinsCollected}",
            L.t("stats.jumps") to "${st.jumps}", L.t("stats.rules") to "${st.rulesSurvived}", L.t("stats.perfect") to "${st.perfectLevels}",
            L.t("stats.endless_best") to "${st.endlessBestScore}", L.t("stats.endless_runs") to "${st.endlessRuns}", L.t("stats.pieces") to "${d.pieces.size}/50",
            L.t("stats.skins") to "${d.ownedSkins.size}/20", L.t("stats.achievements") to "${d.achievements.size}/50", L.t("stats.playtime") to L.t("unit.minutes", "v" to L.dec(st.playSeconds / 60f, 0)),
        )
        stats.forEachIndexed { i, (k, v) ->
            grid.add(Table().apply { background = kit.patch("card"); pad(12f); add(kit.label(v, a.f28, Palette.INK)).row(); add(kit.label(k, a.f22, Palette.INK_SOFT, fit = true)).width(176f) }).width(200f).height(100f).pad(5f)
            if (i % 3 == 2) grid.row()
        }
        val body = Table()
        body.add(p).width(560f).top().padRight(16f)
        body.add(scroll(grid)).grow().top()
        root.add(body).grow().pad(8f, 20f, 10f, 20f)
    }
}

class SettingsScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    private var confirmReset = false
    override fun build() {
        val st = game.profile.data.settings
        root.add(header(L.t("settings.title"), showCurrency = false)).growX().padTop(20f).row()
        val p = kit.panel()
        fun toggle(label: String, icon: String, value: Boolean, set: (Boolean) -> Unit) {
            val row = Table()
            row.add(Image(a.ui(icon)).apply { color = Palette.INK_SOFT }).size(56f).padRight(14f)
            row.add(kit.label(label, a.f28, Palette.INK, Align.left, wrap = true)).width(330f).expandX().left()
            val t = Table().apply { background = kit.patch(if (value) "toggle_on" else "toggle_off"); pad(6f) }
            t.add(Image(a.ui("soft")).apply { color = Color.WHITE }).size(52f).expandX().apply { if (value) right() else left() }
            t.onTap(kit) { set(!value); game.audio.applyVolume(); game.profile.save(); rebuild() }
            row.add(t).size(130f, 68f)
            p.add(row).width(560f).padBottom(12f).row()
        }
        toggle(L.t("settings.music"), "g_music", st.music) { st.music = it }
        toggle(L.t("settings.sound"), "g_sound", st.sound) { st.sound = it }
        toggle(L.t("settings.vibration"), "g_vibrate", st.vibration) { st.vibration = it }
        toggle(L.t("settings.reduced"), "g_info", st.reducedEffects) { st.reducedEffects = it }
        fun slider(label: String, v: Float, set: (Float) -> Unit) {
            val row = Table()
            row.add(kit.label(label, a.f28, Palette.INK, Align.left, fit = true)).width(220f).left()
            row.add(kit.iconButton("g_back", "white", 80f, 40f) { set((v - 0.1f).coerceAtLeast(0f)); game.audio.applyVolume(); game.profile.save(); rebuild() }).size(80f)
            row.add(kit.bar(v, "bar_fill_b", 24f)).width(180f).height(24f).pad(0f, 10f, 0f, 10f)
            row.add(kit.iconButton("g_next", "white", 80f, 40f) { set((v + 0.1f).coerceAtMost(1f)); game.audio.applyVolume(); game.profile.save(); rebuild() }).size(80f)
            p.add(row).width(560f).padBottom(14f).row()
        }
        slider(L.t("settings.music_volume"), st.musicVolume) { st.musicVolume = it }
        slider(L.t("settings.sound_volume"), st.soundVolume) { st.soundVolume = it }
        val side = Table()
        // Dil: oyun telefonun dilinde açılır; buradan istenildiği an değiştirilir (yeniden başlatma gerekmez)
        side.add(kit.button(L.t("settings.language") + ": " + (L.NATIVE_NAMES[L.lang] ?: L.lang), "yellow", "b_map", a.f28) { chooseLanguage() }).width(420f).height(96f).row()
        side.add(kit.button(L.t("settings.howto"), "turq", "g_info", a.f28) { game.go(HowToPlayScreen(game)) }).width(420f).height(96f).padTop(12f).row()
        side.add(kit.button(if (confirmReset) L.t("settings.reset_confirm") else L.t("settings.reset"), "red", "g_retry", a.f28) {
            if (confirmReset) { game.profile.resetAll(); toast(L.t("settings.reset_done")); confirmReset = false } else confirmReset = true
            rebuild()
        }).width(420f).height(92f).padTop(12f).row()
        side.add(kit.button(L.t("settings.privacy"), "white", "g_info", a.f28, Palette.INK) { showPrivacy() }).width(420f).height(88f).padTop(12f).row()
        // Reklam onayı gereken bölgelerde (ör. AB) oyuncu tercihini buradan değiştirebilir
        if (game.ads.privacyOptionsRequired) side.add(kit.button(L.t("settings.ad_prefs"), "white", "g_info", a.f28, Palette.INK) { game.ads.showPrivacyOptions() }).width(420f).height(88f).padTop(10f).row()
        side.add(kit.label(L.t("settings.about", "app" to L.t("app.name_main"), "ver" to APP_VERSION, "dev" to L.t("app.developer")), a.f22, Palette.INK_SOFT, wrap = true)).width(420f).padTop(12f)
        val body = Table()
        body.add(p).width(640f).padRight(24f)
        // uzun dillerde sağ sütun ekrana sığmazsa kaydırılır (alt bilgi metni kesilmez)
        body.add(scroll(side)).width(440f).maxHeight(stage.height - 150f).top()
        root.add(body).expand().center()
    }

    /** Dil seçimi: altı dil kendi dillerindeki adlarıyla; seçim hemen uygulanır ve kaydedilir. */
    private fun chooseLanguage() {
        dialog { p, close ->
            p.add(kit.label(L.t("settings.choose_language"), a.f48, Palette.INK)).padBottom(10f).row()
            val grid = Table()
            L.SUPPORTED.forEachIndexed { i, code ->
                val sel = code == L.lang
                grid.add(kit.button(L.NATIVE_NAMES[code] ?: code, if (sel) "green" else "white", if (sel) "g_check" else null, a.f36, if (sel) Color.WHITE else Palette.INK) {
                    close()
                    if (code != L.lang) { game.setLanguage(code); rebuild(); toast(L.t("settings.language_changed")) }
                }).width(300f).height(96f).pad(6f)
                if (i % 2 == 1) grid.row()
            }
            p.add(grid).row()
            p.add(kit.button(L.t("common.close"), "white", font = a.f28, textColor = Palette.INK) { close() }).width(300f).height(88f).padTop(10f)
        }
    }

    /** Gizlilik politikası uygulamanın içinde de okunabilir (Google Play Kullanıcı Verileri politikası gereği). */
    fun showPrivacy() {
        dialog(resultW, resultH) { p, close ->
            p.top()
            p.add(kit.label(L.t("settings.privacy"), a.f48, Palette.INK)).padTop(4f).row()
            val box = Table().top().left()
            box.add(kit.label(privacyPolicyText(game.ads.childDirected), a.f22, Palette.INK, Align.left, wrap = true)).width(resultW - 130f).left()
            p.add(scroll(box)).width(resultW - 90f).expandY().fillY().padTop(8f).row()
            p.add(kit.button(L.t("common.ok"), "green", "g_check") { close() }).width(300f).height(92f).padTop(10f)
        }
    }

    companion object { const val APP_VERSION = "1.1.0" }
}

/**
 * Uygulama içi gizlilik politikası metni (seçili dilde); mağaza sayfasına konacak sürümü
 * docs/play_store/gizlilik_politikasi.md dosyasındadır. [childAds]: reklamlar çocuklara uygun modda mı isteniyor.
 */
fun privacyPolicyText(childAds: Boolean): String = listOf(
    L.t("privacy.intro", "app" to L.t("app.name_main"), "dev" to L.t("app.developer")),
    L.t("privacy.ads"),
    if (childAds) L.t("privacy.ads_child") else L.t("privacy.ads_general"),
    L.t("privacy.rewarded"),
    L.t("privacy.purchases"),
    L.t("privacy.save"),
    L.t("privacy.permissions"),
    L.t("privacy.children"),
    L.t("privacy.changes"),
    L.t("privacy.contact", "dev" to L.t("app.developer")),
    L.t("privacy.updated"),
).joinToString("\n\n")

class DailyRewardScreen(game: BesSaniyelikDunya, private val fromSplash: Boolean = false) : BaseScreen(game) {
    override val watchesAdReadiness get() = true
    /** Ödül alındıktan sonra reklam hazır olursa "ödülü tekrar al" düğmesi belirir. */
    override fun onAdReadyChanged(ready: Boolean) { if (!game.profile.loginAvailable()) rebuild() }
    override fun build() {
        val d = game.profile.data
        root.add(header(L.t("daily.title"), back = { onBack() })).growX().padTop(20f).row()
        root.add(kit.label(L.t("daily.text"), a.f22, Palette.INK_SOFT, wrap = true)).width(640f).padTop(8f).row()
        val grid = Table()
        val today = d.login.cycleDay % 7
        val available = game.profile.loginAvailable()
        Catalog.loginRewards.forEachIndexed { i, r ->
            val claimed = i < today || (i == today && !available && false)
            val isToday = i == today && available
            val cell = Table().apply { background = kit.patch(when { isToday -> "card_sel"; i < today -> "panel_yellow"; else -> "card" }); pad(12f) }
            cell.add(kit.label(L.t("daily.day", "n" to i + 1), a.f22, Palette.INK, fit = true)).width(134f).row()
            val icon = when { r.gems > 0 && r.coins > 0 -> "b_gift"; r.gems > 0 -> "b_gem"; r.coins > 0 -> "b_coin"; else -> "b_" + r.powerups.keys.first().icon }
            cell.add(Image(a.ui(if (claimed) "g_check" else icon)).apply { if (claimed) color = Palette.GRASS }).size(if (i == 6) 90f else 70f).padTop(4f).row()
            cell.add(kit.label(r.describe(), a.f22, Palette.INK_SOFT, wrap = true)).width(134f)
            grid.add(cell).width(160f).height(if (i == 6) 236f else 216f).pad(5f)
            if (isToday) cell.popIn(0.1f)
        }
        root.add(grid).padTop(24f).row()
        if (available) {
            root.add(kit.button(L.t("daily.claim"), "green", "b_gift", a.f48o) {
                val r = game.profile.claimLogin()
                if (r != null) { game.audio.play(Sfx.REWARD); toast(L.t("daily.won", "r" to r.describe()), "b_gift") }
                rebuild()
            }).width(460f).height(130f).padTop(24f).row()
        } else {
            root.add(kit.label(L.t("daily.claimed"), a.f28, Palette.INK, wrap = true)).width(900f).padTop(8f).row()
            val row = Table()
            // İsteğe bağlı ödüllü reklam: bugünkü ödülü bir kez daha verir (günde bir kez); ödül düğmenin üstünde açıkça yazar
            val canDouble = adReadyNow() && game.ads.loginDoubleAvailable()
            if (canDouble) {
                root.add(kit.label(L.t("daily.double_text", "r" to game.ads.todaysLoginReward().describe()), a.f22, Palette.INK, wrap = true)).width(900f).padTop(2f).row()
                row.add(kit.button(L.t("ads.watch_daily"), "turq", "g_play", a.f28) {
                    watchAd { game.ads.grantLoginDouble()?.let { r -> game.audio.play(Sfx.REWARD); toast(L.t("daily.won", "r" to r.describe()), "b_gift") }; rebuild() }
                }).width(500f).height(92f).pad(6f)
            }
            row.add(kit.button(L.t("common.continue"), "yellow", "g_next") { onBack() }).width(320f).height(92f).pad(6f)
            root.add(row).padTop(2f).row()
        }
    }
    override fun onBack() { game.go(MainMenuScreen(game)) }
}

/** Öğretici ekranlar: temel kontroller, canlar ve on dünya mekaniği. */
class HowToPlayScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    private var page = 0
    override fun build() {
        root.add(header(L.t("settings.howto"), showCurrency = false)).growX().padTop(20f).row()
        val pages = buildList {
            add(Triple("g_tap", L.t("howto.tap.title"), L.t("howto.tap.text")))
            add(Triple("b_m_normal", L.t("howto.rule5.title"), L.t("howto.rule5.text")))
            add(Triple("b_star", L.t("howto.stars.title"), L.t("howto.stars.text")))
            add(Triple("b_heart", L.t("howto.hearts.title"), L.t("howto.hearts.text", "n" to GameConfig.HEARTS_PER_ATTEMPT)))
            if (game.lives.enabled) add(Triple("b_heart", L.t("howto.lives.title"), L.t("lives.rules", "min" to GameConfig.LIFE_REGEN_SECONDS / 60, "free" to GameConfig.FREE_LEVELS)))
            add(Triple("b_p_shield", L.t("howto.power.title"), L.t("howto.power.text")))
            Mechanic.learnOrder.forEach { add(Triple("b_" + it.icon, it.title, it.hint + "\n" + L.t("howto.learned_at", "n" to it.unlockLevel))) }
        }
        page = page.coerceIn(0, pages.size - 1)
        val (icon, title, text) = pages[page]
        val p = kit.panel()
        p.add(Image(a.ui(icon)).apply { if (icon.startsWith("g_")) color = Palette.ORANGE }).size(120f).row()
        p.add(kit.label(title, a.f48, Palette.INK, wrap = true)).width(640f).padTop(10f).row()
        p.add(kit.label(text, a.f28, Palette.INK_SOFT, wrap = true)).width(640f).padTop(10f).row()
        p.add(kit.label("${page + 1} / ${pages.size}", a.f22, Palette.INK_SOFT)).padTop(12f)
        p.popIn()
        val body = Table()
        body.add(kit.iconButton("g_back", "white", 110f, 56f) { page--; rebuild() }).size(110f).padRight(30f).also { if (page == 0) it.actor.enabled = false }
        body.add(p).width(740f)
        body.add(kit.iconButton("g_next", "green", 110f, 56f) { if (page < pages.size - 1) { page++; rebuild() } else onBack() }).size(110f).padLeft(30f)
        root.add(body).expand().center()
    }
}
