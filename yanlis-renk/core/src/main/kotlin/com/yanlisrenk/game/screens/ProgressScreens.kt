package com.yanlisrenk.game.screens

import com.yanlisrenk.logic.i18n.L
import com.yanlisrenk.logic.i18n.lupper

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.yanlisrenk.game.YanlisRenkGame
import com.yanlisrenk.game.ui.Pal
import com.yanlisrenk.game.ui.Ui
import com.yanlisrenk.logic.level.AreaCatalog
import com.yanlisrenk.logic.life.formatMs
import com.yanlisrenk.logic.progress.Achievements
import com.yanlisrenk.logic.progress.DailyRewards
import com.yanlisrenk.logic.progress.Missions
import com.yanlisrenk.logic.progress.WeeklyChallenge
import com.yanlisrenk.logic.save.MissionSlot
import com.yanlisrenk.logic.shop.Currency
import com.yanlisrenk.logic.shop.ShopCatalog
import com.yanlisrenk.logic.shop.ShopCategory

/** 13. Görevler (günlük 3 + haftalık 5) */
class MissionsScreen(game: YanlisRenkGame, private var weekly: Boolean = false) : BaseScreen(game) {
    override val recreate: (() -> BaseScreen) = { MissionsScreen(game, weekly) }
    override fun build() {
        root.top()
        root.add(topBar(L.t("common.gorevler"))).growX().row()
        val tabs = Table()
        tabs.add(ui.button(L.t("common.gunluk"), if (!weekly) Pal.ORANGE else Pal.WHITE, "calendar", Ui.Size.S, if (!weekly) Pal.WHITE else Pal.TEXT) { weekly = false; rebuild() }).width(300f).padRight(12f)
        tabs.add(ui.button(L.t("common.haftalik"), if (weekly) Pal.ORANGE else Pal.WHITE, "flag", Ui.Size.S, if (weekly) Pal.WHITE else Pal.TEXT) { weekly = true; rebuild() }).width(300f)
        root.add(tabs).padBottom(10f).row()
        val dayMs = 86_400_000L
        val now = session.clock.dayNow() + game.platform.tzOffsetMillis()
        val left = if (weekly) (session.weekKey() + 1) * 7 * dayMs - 3 * dayMs - now else (session.dayKey() + 1) * dayMs - now
        root.add(ui.label(L.f("prog.yenilenmesine", formatMs(left.coerceAtLeast(0))), Ui.Size.S, Pal.TEXT_SOFT)).padBottom(10f).row()
        val list = Table()
        val slots = if (weekly) session.data.missions.weekly else session.data.missions.daily
        for (s in slots) list.add(missionCard(s)).width(660f).padBottom(12f).row()
        root.add(ui.scroll(list)).grow()
    }
    private fun rebuild() { root.clear(); build() }

    private fun missionCard(s: MissionSlot): Table {
        val c = ui.card(if (s.claimed) Pal.hex(0xEEF6EE) else Pal.WHITE, 22f)
        c.add(ui.icon(Missions.template(s.templateId).icon, 70f)).size(70f).padRight(14f)
        val tx = Table()
        tx.add(ui.label(Missions.title(s), Ui.Size.M, Pal.TEXT, Align.left, true)).width(330f).left().row()
        tx.add(ui.progress(s.progress.toFloat() / s.target, Pal.GREEN, 330f, 22f)).size(330f, 22f).left().padTop(6f).row()
        val rw = Table()
        rw.add(ui.label("${s.progress}/${s.target}", Ui.Size.S, Pal.TEXT_SOFT)).padRight(14f)
        rw.add(ui.icon("coin", 30f)).size(30f); rw.add(ui.label(s.rewardGold.toString(), Ui.Size.S)).padRight(8f)
        if (s.rewardDiamonds > 0) { rw.add(ui.icon("diamond", 30f)).size(30f); rw.add(ui.label(s.rewardDiamonds.toString(), Ui.Size.S)).padRight(8f) }
        rw.add(ui.label(L.f("common.xp", s.rewardXp), Ui.Size.S, Pal.TEXT_SOFT))
        tx.add(rw).left().padTop(4f)
        c.add(tx).expandX().left()
        when {
            s.claimed -> c.add(ui.icon("check", 70f)).size(70f)
            s.progress >= s.target -> c.add(ui.button(L.t("prog.al"), Pal.GREEN, "gift", Ui.Size.S) {
                if (session.claimMission(s, weekly)) { game.audio.play("purchase"); refreshTopBar(); flushAchievementToasts(); rebuild() }
            })
            else -> c.add(ui.icon("lock", 54f)).size(54f)
        }
        return c
    }
}

/** 14. Başarılar */
class AchievementsScreen(game: YanlisRenkGame) : BaseScreen(game) {
    override val recreate: (() -> BaseScreen) = { AchievementsScreen(game) }
    override fun build() {
        root.top()
        root.add(topBar(L.t("common.basarilar"))).growX().row()
        val total = Achievements.all.size
        root.add(ui.label(L.f("prog.rozet", session.data.achievementsUnlocked.size, total), Ui.Size.M, Pal.TEXT_SOFT)).padBottom(10f).row()
        val list = Table()
        val sorted = Achievements.all.sortedBy {
            when { it.id in session.data.achievementsUnlocked && it.id !in session.data.achievementsClaimed -> 0; it.id !in session.data.achievementsUnlocked -> 1; else -> 2 }
        }
        for (a in sorted) {
            val unlocked = a.id in session.data.achievementsUnlocked
            val claimed = a.id in session.data.achievementsClaimed
            val c = ui.card(if (unlocked) Pal.WHITE else Pal.hex(0xF1F4F9), 18f)
            c.add(ui.icon(a.icon, 66f).also { if (!unlocked) it.color.a = 0.4f }).size(66f).padRight(14f)
            val tx = Table()
            tx.add(ui.label(a.title, Ui.Size.M, if (unlocked) Pal.TEXT else Pal.TEXT_SOFT, Align.left)).left().row()
            tx.add(ui.label(a.description, Ui.Size.S, Pal.TEXT_SOFT, Align.left, true)).width(330f).left().row()
            val v = session.statValue(a.stat).coerceAtMost(a.target)
            if (!unlocked) tx.add(ui.progress(v.toFloat() / a.target, Pal.YELLOW, 330f, 18f)).size(330f, 18f).left().padTop(4f)
            c.add(tx).expandX().left()
            when {
                claimed -> c.add(ui.icon("check", 60f)).size(60f)
                unlocked -> c.add(ui.button("+${a.gold}", Pal.GREEN, "coin", Ui.Size.S) { if (session.claimAchievement(a)) { game.audio.play("achievement"); refreshTopBar(); root.clear(); build() } })
                else -> c.add(ui.label("$v/${a.target}", Ui.Size.S, Pal.TEXT_SOFT)).width(110f)
            }
            list.add(c).width(660f).padBottom(10f).row()
        }
        root.add(ui.scroll(list)).grow()
    }
}

/** 15. Koleksiyon */
class CollectionScreen(game: YanlisRenkGame) : BaseScreen(game) {
    override val recreate: (() -> BaseScreen) = { CollectionScreen(game) }
    override fun build() {
        root.top()
        root.add(topBar(L.t("common.koleksiyon"))).growX().row()
        root.add(ui.label(L.f("prog.100_parca_her_alani", session.data.collection.size), Ui.Size.S, Pal.TEXT_SOFT, wrap = true)).width(640f).padBottom(10f).row()
        val list = Table()
        for (w in AreaCatalog.worlds) {
            val c = ui.card(pad = 18f)
            val h = Table()
            h.add(ui.label(w.name, Ui.Size.M, Pal.TEXT, Align.left)).expandX().left()
            if (w.id in session.data.worldSetClaimed) h.add(ui.icon("trophy", 40f)).size(40f)
            c.add(h).growX().colspan(5).padBottom(8f).row()
            for (a in AreaCatalog.areas.filter { it.world == w.id }) {
                val got = a.id in session.data.collection
                val p = Table()
                val bg = Table(); bg.background = ui.pill(if (got) com.yanlisrenk.game.ui.Pal.hex(a.accent1) else Pal.GREY)
                bg.add(ui.icon(if (got) "box" else "lock", 60f).also { if (!got) it.color.a = 0.5f }).size(60f).pad(12f)
                p.add(bg).row()
                p.add(ui.label(if (got) a.collectible else "???", Ui.Size.S, if (got) Pal.TEXT else Pal.TEXT_SOFT, wrap = true).also { it.setFontScale(0.82f) }).width(118f)
                c.add(p).width(122f).top().pad(2f)
            }
            list.add(c).width(670f).padBottom(12f).row()
        }
        // markette alınan koleksiyon ürünleri rafı
        val shelf = ui.card(pad = 18f)
        shelf.add(ui.label(L.t("prog.rafim"), Ui.Size.M, Pal.TEXT, Align.left)).left().colspan(5).padBottom(8f).row()
        ShopCatalog.collectibles.forEachIndexed { i, it ->
            val got = session.owns(it.id)
            val p = Table(); p.add(ui.icon("box", 56f).also { im -> if (!got) im.color.a = 0.3f }).size(56f).row()
            p.add(ui.label(if (got) it.name else "?", Ui.Size.S, Pal.TEXT_SOFT, wrap = true)).width(116f)
            shelf.add(p).width(122f).pad(2f)
            if (i % 5 == 4) shelf.row()
        }
        shelf.row(); shelf.add(ui.button(L.t("prog.markete_git"), Pal.ORANGE, "shop", Ui.Size.S) { game.go({ ShopScreen(game, ShopCategory.COLLECTIBLE) }) }).colspan(5).padTop(8f)
        list.add(shelf).width(670f).padBottom(20f).row()
        root.add(ui.scroll(list)).grow()
    }
}

/** 17. Günlük ödüller (7 günlük döngü) */
class DailyRewardScreen(game: YanlisRenkGame) : BaseScreen(game) {
    override fun build() {
        root.top()
        root.add(topBar(L.t("prog.gunluk_odul"))).growX().row()
        val today = session.dailyIndexToday()
        val can = session.canClaimDaily()
        val grid = Table()
        DailyRewards.cycle.forEachIndexed { i, r ->
            val claimedInCycle = i < today || (!can && i == (today - 1 + 7) % 7 && today != 0)
            val isToday = i == today && can
            val c = ui.card(if (isToday) Pal.hex(0xFFF4D6) else if (i < today) Pal.hex(0xEEF6EE) else Pal.WHITE, 16f)
            c.add(ui.label(L.f("prog.gun", i + 1), Ui.Size.S, Pal.TEXT_SOFT)).row()
            c.add(ui.icon(when { r.item == "boost_shield" -> "shield"; r.item == "life_full" -> "heart_full"; r.diamonds > 0 && r.gold > 0 -> "gift"; r.diamonds > 0 -> "diamond"; else -> "coin" }, 80f)).size(80f).pad(6f).row()
            c.add(ui.label(r.label, Ui.Size.S, Pal.TEXT, wrap = true)).width(170f).row()
            if (i < today) c.add(ui.icon("check", 40f)).size(40f)
            grid.add(c).width(200f).height(240f).pad(6f)
            if (i % 3 == 2) grid.row()
            if (claimedInCycle) Unit
        }
        root.add(grid).padTop(20f).row()
        root.add(ui.label(L.f("prog.seri_gun_gun_kacirirsan", session.data.daily.currentStreak), Ui.Size.S, Pal.TEXT_SOFT, wrap = true)).width(620f).padTop(10f).row()
        if (can) root.add(ui.button(L.t("prog.odulu_al"), Pal.GREEN, "gift", Ui.Size.XL) {
            val r = session.claimDaily()
            if (r != null) { game.audio.play("unlock"); ui.toast(stage, L.f("prog.kazandin", r.label), "gift"); refreshTopBar(); flushAchievementToasts(); root.clear(); build() }
        }).width(480f).padTop(20f).row()
        else root.add(ui.label(L.t("prog.bugunun_odulunu_aldin_yarin"), Ui.Size.M, Pal.TEXT_SOFT)).padTop(20f).row()
        if (session.clock.hasUnverifiedGap) root.add(ui.label(L.t("prog.not_cihaz_yeniden_baslatildi"), Ui.Size.S, Pal.TEXT_SOFT, wrap = true)).width(600f).padTop(10f)
    }
}

/** 18. Haftalık mücadele */
class WeeklyChallengeScreen(game: YanlisRenkGame) : BaseScreen(game) {
    override val recreate: (() -> BaseScreen) = { WeeklyChallengeScreen(game) }
    override fun build() {
        root.top()
        root.add(topBar(L.t("common.haftalik_mucadele"))).growX().row()
        val wk = session.weekKey()
        val (title, mechs) = WeeklyChallenge.theme(wk)
        val w = session.data.weekly
        val best = if (w.weekKey == wk) w.best else 0
        val card = ui.card(pad = 30f)
        card.add(ui.icon("flag", 110f)).size(110f).row()
        card.add(ui.label(title, Ui.Size.XL, Pal.ORANGE)).row()
        card.add(ui.label(L.t("prog.hafta_herkes_ayni_parkuru") + mechs.joinToString { it.title }, Ui.Size.S, Pal.TEXT_SOFT, wrap = true)).width(580f).padBottom(12f).row()
        card.add(ui.label(L.f("prog.haftalik_en_iyin", best), Ui.Size.L)).padBottom(4f).row()
        card.add(ui.label(L.f("prog.deneme_canlarin_harcanmaz", if (w.weekKey == wk) w.attempts else 0), Ui.Size.S, Pal.TEXT_SOFT)).padBottom(16f).row()
        WeeklyChallenge.tiers.forEachIndexed { i, (need, rew) ->
            val r = Table(); r.background = ui.panel(Pal.hex(0xF4F8FD)); r.pad(12f)
            r.add(ui.label(L.f("prog.puan", need), Ui.Size.M, Pal.TEXT, Align.left)).width(180f).left()
            r.add(ui.chip("coin", rew.first.toString())).padRight(6f)
            if (rew.second > 0) r.add(ui.chip("diamond", rew.second.toString())).padRight(6f)
            r.add().expandX()
            val claimed = w.weekKey == wk && i in w.rewardTiersClaimed
            when {
                claimed -> r.add(ui.icon("check", 56f)).size(56f)
                best >= need -> r.add(ui.button(L.t("prog.al"), Pal.GREEN, null, Ui.Size.S) { if (session.claimWeeklyTier(i)) { game.audio.play("purchase"); refreshTopBar(); root.clear(); build() } })
                else -> r.add(ui.icon("lock", 48f)).size(48f)
            }
            card.add(r).width(600f).padBottom(8f).row()
        }
        card.add(ui.button(L.t("prog.mucadeleye_basla"), Pal.GREEN, "play") { game.go({ GameScreen(game, Mode.WEEKLY) }) }).width(460f).padTop(14f).row()
        card.add(ui.label(L.t("prog.gercek_oyuncu_siralamasi_icin"), Ui.Size.S, Pal.TEXT_SOFT, wrap = true)).width(560f).padTop(12f)
        root.add(ui.scroll(card)).width(680f).grow().padBottom(20f)
    }
}

/** 9. Can yenileme ekranı */
class LivesScreen(game: YanlisRenkGame) : BaseScreen(game) {
    override val recreate: (() -> BaseScreen) = { LivesScreen(game) }
    private lateinit var timer: com.badlogic.gdx.scenes.scene2d.ui.Label
    private lateinit var full: com.badlogic.gdx.scenes.scene2d.ui.Label
    override fun build() {
        root.top()
        root.add(topBar(L.t("common.canlar"), showLives = false)).growX().row()
        val l = session.lives
        val card = ui.card(pad = 34f)
        val hearts = Table()
        for (i in 0 until com.yanlisrenk.logic.config.GameConfig.MAX_LIVES) hearts.add(ui.icon(if (i < l.lives) "heart_full" else "heart_empty", 110f)).size(110f).pad(6f)
        card.add(hearts).row()
        card.add(ui.label(L.f("prog.lives_count", l.lives, com.yanlisrenk.logic.config.GameConfig.MAX_LIVES), Ui.Size.XL)).padTop(6f).row()
        timer = ui.label("", Ui.Size.L, Pal.ORANGE); full = ui.label("", Ui.Size.S, Pal.TEXT_SOFT)
        card.add(timer).row(); card.add(full).padBottom(10f).row()
        if (l.state.shieldArmed) card.add(ui.label(L.t("prog.can_kalkani_aktif_siradaki"), Ui.Size.S, Pal.BLUE, wrap = true)).width(560f).padBottom(8f).row()
        card.add(ui.label(L.f("prog.lives_regen_info", com.yanlisrenk.logic.config.GameConfig.LIFE_REGEN_SECONDS / 60), Ui.Size.S, Pal.TEXT_SOFT, wrap = true)).width(560f).padBottom(16f).row()
        // sahip olunan destekler
        val owned = listOf("life_single", "life_single_d", "life_full", "boost_shield").filter { session.count(it) > 0 }
        if (owned.isNotEmpty()) {
            card.add(ui.label(L.t("prog.kazandigin_destekler"), Ui.Size.M)).padBottom(8f).row()
            for (id in owned) {
                val it = ShopCatalog.item(id)!!
                card.add(ui.button("${it.name} (${session.count(id)})", Pal.PINK, if (id == "boost_shield") "shield" else "heart_full", Ui.Size.S, Pal.TEXT) {
                    if (session.useConsumable(id)) { game.audio.play(if (id == "boost_shield") "shield" else "life_refill"); root.clear(); build(); } else ui.toast(stage, L.t("prog.su_an_kullanilamaz"), "info")
                }).width(520f).padBottom(8f).row()
            }
        }
        if (!l.isFull) {
            val single = ShopCatalog.item("life_single")!!
            val fullItem = ShopCatalog.item("life_full")!!
            val row = Table()
            row.add(ui.button(L.f("prog.1_can", single.price), Pal.ORANGE, "coin", Ui.Size.S) { buyAndUse(single.id) }).padRight(10f)
            row.add(ui.button(L.f("prog.tam_can", fullItem.price), Pal.TURQ, "diamond", Ui.Size.S) { buyAndUse(fullItem.id) })
            card.add(row).padBottom(14f).row()
        }
        if (!l.isFull && game.rewardedLeftToday() > 0) {
            card.add(ui.button(L.t("prog.reklam_izle_1_can"), Pal.PURPLE, "film", Ui.Size.M) {
                val started = game.watchRewarded {
                    session.grantAdLife()
                    game.audio.play("life_refill"); ui.toast(stage, L.t("prog.1_can_2"), "heart_full")
                    root.clear(); build()
                }
                if (!started) ui.toast(stage, L.t("prog.reklam_su_an_hazir"), "info")
            }).width(520f).padBottom(6f).row()
            card.add(ui.label(L.f("prog.bugun_kalan_reklam_odulu", game.rewardedLeftToday()), Ui.Size.S, Pal.TEXT_SOFT)).padBottom(12f).row()
        }
        card.add(ui.label(L.t("prog.beklerken"), Ui.Size.M)).padTop(6f).padBottom(8f).row()
        card.add(ui.button(L.t("prog.sonsuz_mod_ucretsiz"), Pal.GREEN, "infinity", Ui.Size.M) { game.go({ GameScreen(game, Mode.ENDLESS) }) }).width(520f).padBottom(10f).row()
        card.add(ui.button(L.t("prog.gunluk_gorevler"), Pal.BLUE, "missions", Ui.Size.M) { game.go({ MissionsScreen(game) }) }).width(520f).padBottom(10f).row()
        if (l.canPlay) card.add(ui.button(L.t("prog.oyna"), Pal.GREEN, "play", Ui.Size.M) { game.go({ SagaMapScreen(game) }) }).width(520f)
        root.add(ui.scroll(card)).width(680f).grow().padBottom(20f)
    }

    private fun buyAndUse(id: String) = buyLife(id)

    override fun afterDraw(delta: Float) {
        val l = session.lives
        if (!::timer.isInitialized) return
        timer.setText(if (l.isFull) L.t("prog.canlarin_dolu") else L.f("common.sonraki_can", formatMs(l.nextLifeIn)))
        full.setText(if (l.isFull) "" else L.f("prog.tumu_dolana_kadar", formatMs(l.fullIn)))
    }

    override fun onLivesChanged() { root.clear(); build() }
}
