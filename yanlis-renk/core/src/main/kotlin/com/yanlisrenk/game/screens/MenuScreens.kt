package com.yanlisrenk.game.screens

import com.yanlisrenk.logic.i18n.L
import com.yanlisrenk.logic.i18n.lupper

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.yanlisrenk.game.YanlisRenkGame
import com.yanlisrenk.game.Brand
import com.yanlisrenk.game.ui.Pal
import com.yanlisrenk.game.ui.Ui
import com.yanlisrenk.game.ui.badge
import com.yanlisrenk.logic.level.AreaCatalog
import com.yanlisrenk.logic.model.Mechanic
import com.yanlisrenk.logic.progress.PlayerLevels
import com.yanlisrenk.logic.progress.Stat
import com.yanlisrenk.logic.save.LoadOutcome
import com.yanlisrenk.logic.shop.ShopCatalog
import com.yanlisrenk.logic.shop.ShopCategory

/** 1. Açılış ekranı */
class SplashScreen(game: YanlisRenkGame) : BaseScreen(game) {
    override val showBanner: Boolean = false
    private var t = 0f
    private var done = false
    override fun build() {
        val ch = CharacterActor(game, session.equipped(ShopCategory.CHARACTER), session.equipped(ShopCategory.COSTUME).style, 0, cycleColors = true)
        root.add(ch).size(300f, 300f).padBottom(20f).row()
        brandLogo(root, if (Brand.turkishMain) 480f else 600f, if (Brand.turkishMain) 380f else 250f, 38f, 4f)
        root.add(ui.label(L.t("brand.tagline"), Ui.Size.M, Pal.TEXT_SOFT)).padTop(18f).padBottom(60f).row()
        root.add(ui.progress(0f, Pal.TURQ, 400f, 26f).also { bar = it as com.yanlisrenk.game.ui.ProgressBarActor }).size(400f, 26f)
    }
    private lateinit var bar: com.yanlisrenk.game.ui.ProgressBarActor
    override fun afterDraw(delta: Float) {
        t += delta
        bar.fraction = minOf(1f, t / 1.4f)
        if (t > 1.5f && !done) {
            done = true
            val tutorial = "intro" !in session.data.tutorialsSeen
            game.go({ if (tutorial) TutorialScreen(game, true) else MainMenuScreen(game) }, remember = false)
        }
    }
    override fun onBack() {}
}

/** 2. Ana menü */
class MainMenuScreen(game: YanlisRenkGame) : BaseScreen(game) {
    override val recreate: (() -> BaseScreen) = { MainMenuScreen(game) }
    override fun build() {
        game.clearHistory()
        root.top()
        root.add(topBar(null, back = false)).growX().row()
        val lvl = Table()
        lvl.add(ui.icon("profile", 54f)).size(54f).padRight(8f)
        lvl.add(ui.label(L.f("menu.seviye", session.data.playerLevel), Ui.Size.S)).padRight(10f)
        lvl.add(ui.progress(session.data.xp.toFloat() / PlayerLevels.xpForNext(session.data.playerLevel), Pal.YELLOW, 200f, 20f)).size(200f, 20f)
        ui.onTap(lvl) { game.go({ ProfileScreen(game) }) }
        root.add(lvl).padTop(4f).row()
        brandLogo(root, if (Brand.turkishMain) 330f else 480f, if (Brand.turkishMain) 230f else 160f, 30f, -2f)
        val ch = CharacterActor(game, session.equipped(ShopCategory.CHARACTER), session.equipped(ShopCategory.COSTUME).style, 0)
        root.add(ch).size(170f, 170f).row()
        ui.onTap(ch) { }

        val cur = session.currentLevel
        val area = AreaCatalog.area(AreaCatalog.areaOfLevel(cur))
        val play = ui.button(L.t("menu.oyna"), Pal.GREEN, "play", Ui.Size.XL) { openLevel(cur) }
        root.add(play).width(520f).height(140f).padTop(6f).row()
        root.add(ui.label(L.f("menu.bolum", cur, area.name), Ui.Size.S, Pal.TEXT_SOFT)).padTop(6f).padBottom(12f).row()
        val row2 = Table()
        row2.add(ui.button(L.t("menu.sonsuz"), Pal.TURQ, "infinity", Ui.Size.M) { game.go({ GameScreen(game, Mode.ENDLESS) }) }).width(250f).padRight(16f)
        row2.add(ui.button(L.t("common.harita"), Pal.BLUE, "map", Ui.Size.M) { game.go({ SagaMapScreen(game) }) }).width(250f).padRight(16f)
        row2.add(ui.iconButton("medal", 100f, Pal.PURPLE) {
            if (session.isAreaComplete(1)) game.go({ masteryScreen(game) }) else ui.toast(stage, L.t("menu.ustalik_icin_once_alani"), "medal")
        }).size(100f)
        root.add(row2).padBottom(12f).row()
        root.add(ui.button(L.t("menu.2_kisilik_kapisma"), Pal.ORANGE, "profile", Ui.Size.M) { game.go({ TwoPlayerScreen(game) }) }).width(536f).padBottom(14f).row()

        val grid = Table()
        fun tile(icon: String, label: String, count: Int = 0, action: () -> Unit) {
            val t = Table()
            t.add(badge(ui, ui.iconButton(icon, 104f) { action() }, count)).size(104f).row()
            t.add(ui.label(label, Ui.Size.S)).padTop(2f)
            grid.add(t).width(170f).pad(6f)
        }
        tile("shop", L.t("common.market")) { game.go({ ShopScreen(game) }) }
        tile("character", L.t("menu.karakter")) { game.go({ CharactersScreen(game) }) }
        tile("missions", L.t("common.gorevler"), session.claimableMissions) { game.go({ MissionsScreen(game) }) }
        tile("trophy", L.t("common.basarilar"), session.claimableAchievements) { game.go({ AchievementsScreen(game) }) }
        grid.row()
        tile("gift", L.t("common.gunluk"), if (session.canClaimDaily()) 1 else 0) { game.go({ DailyRewardScreen(game) }) }
        tile("flag", L.t("common.haftalik")) { game.go({ WeeklyChallengeScreen(game) }) }
        tile("box", L.t("common.koleksiyon")) { game.go({ CollectionScreen(game) }) }
        tile("settings", L.t("menu.ayarlar")) { game.go({ SettingsScreen(game) }) }
        root.add(grid).row()
        if (session.loadOutcome == LoadOutcome.RESTORED_BACKUP) ui.toast(stage, L.t("menu.kayit_yedekten_geri_yuklendi"), "info")
        if (session.canClaimDaily()) stage.addAction(Actions.delay(0.4f, Actions.run { game.go({ DailyRewardScreen(game) }) }))
        flushAchievementToasts()
    }

    private fun openLevel(id: Int) {
        val area = AreaCatalog.areaOfLevel(id)
        when {
            !session.lives.canPlay -> showOutOfLives()
            area !in session.data.areaIntroSeen -> game.go({ AreaIntroScreen(game, area) })
            AreaCatalog.area(area).lastLevel == id -> game.go({ AreaFinalScreen(game, id) })
            else -> game.go({ GameScreen(game, Mode.LEVEL, id) })
        }
    }

    override fun onBack() { com.badlogic.gdx.Gdx.app.exit() }
}

/** 20. Öğretici ekranlar */
class TutorialScreen(game: YanlisRenkGame, private val first: Boolean = false) : BaseScreen(game) {
    override val showBanner: Boolean = false
    private var page = 0
    private val pages = listOf(
        Triple(L.t("menu.dokun_renk_degistir"), L.t("menu.ekranin_herhangi_yerine_dokun"), Mechanic.SAME),
        Triple(L.t("menu.esittir_ayni_renk"), L.t("menu.esittir_isaretli_kapidan_kapiyla"), Mechanic.SAME),
        Triple(L.t("menu.esit_degil_farkli_renk"), L.t("menu.esit_degildir_isaretli_kapidan"), Mechanic.DIFF),
        Triple(L.t("menu.sekiller_soyler"), L.t("menu.renk_korlugu_destegi_kirmizi"), Mechanic.RAINBOW),
        Triple(L.f("menu.tutorial_lives_title", com.yanlisrenk.logic.config.GameConfig.MAX_LIVES), L.f("menu.tutorial_lives_body", com.yanlisrenk.logic.config.GameConfig.LIFE_REGEN_SECONDS / 60), Mechanic.SPEED),
    )
    override fun build() { show(0) }
    private fun show(p: Int) {
        page = p
        root.clear()
        root.add(ui.label(L.f("menu.nasil_oynanir", p + 1, pages.size), Ui.Size.M, Pal.TEXT_SOFT)).padBottom(20f).row()
        val (title, text, mech) = pages[p]
        val card = ui.card(pad = 40f)
        card.add(ui.label(title, Ui.Size.XL)).padBottom(20f).row()
        if (p == 3) {
            val t = Table()
            listOf("shape_circle", "shape_square", "shape_triangle", "shape_star").forEachIndexed { i, s ->
                val c = Table(); c.background = ui.pill(Pal.game[i]); c.add(ui.icon(s, 70f)).size(70f).pad(16f); t.add(c).pad(8f)
            }
            card.add(t).padBottom(20f).row()
        } else if (p == 4) {
            val t = Table(); for (i in 0 until 4) t.add(ui.icon("heart_full", 90f)).size(90f).pad(6f)
            card.add(t).padBottom(20f).row()
        } else if (p == 0) {
            card.add(CharacterActor(game, ShopCatalog.characters[0], 0, 0, cycleColors = true)).size(280f, 280f).row()
        } else card.add(MechanicActor(game, mech)).size(420f, 300f).padBottom(20f).row()
        card.add(ui.label(text, Ui.Size.M, Pal.TEXT_SOFT, wrap = true)).width(540f).padBottom(30f).row()
        val row = Table()
        if (p > 0) row.add(ui.button(L.t("common.geri"), Pal.GREY, textColor = Pal.TEXT, size = Ui.Size.M) { show(p - 1) }).padRight(16f)
        row.add(ui.button(if (p == pages.size - 1) L.t("menu.basla") else L.t("menu.ileri"), Pal.GREEN, if (p == pages.size - 1) "play" else null) {
            if (p == pages.size - 1) finish() else show(p + 1)
        })
        card.add(row)
        root.add(card).width(640f)
        if (!first) root.row().also { root.add(ui.button(L.t("menu.kapat"), Pal.BLUE, size = Ui.Size.S) { finish() }).padTop(20f) }
    }
    private fun finish() {
        session.data.tutorialsSeen.add("intro"); session.save()
        if (first) game.go({ AreaIntroScreen(game, 1) }, remember = false) else game.back()
    }
    override fun onBack() { if (page > 0) show(page - 1) else if (!first) game.back() }
}

/** 19. Ayarlar */
class SettingsScreen(game: YanlisRenkGame) : BaseScreen(game) {
    override val recreate: (() -> BaseScreen) = { SettingsScreen(game) }
    override fun build() {
        root.top()
        root.add(topBar(L.t("menu.ayarlar"), showLives = false)).growX().row()
        val st = session.data.settings
        val list = Table()
        list.defaults().width(640f).padBottom(14f)
        fun slider(icon: String, name: String, get: () -> Float, set: (Float) -> Unit) {
            val c = ui.card()
            c.add(ui.icon(icon, 56f)).size(56f).padRight(14f)
            c.add(ui.label(name, Ui.Size.M, Pal.TEXT, Align.left)).width(200f).left()
            val s = Slider(0f, 1f, 0.05f, false, sliderStyle())
            s.value = get()
            s.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) { set(s.value); game.audio.applyVolume() } })
            c.add(s).width(300f).height(60f)
            list.add(c).row()
        }
        fun toggle(icon: String, name: String, desc: String, get: () -> Boolean, set: (Boolean) -> Unit) {
            val c = ui.card()
            c.add(ui.icon(icon, 56f)).size(56f).padRight(14f)
            val tx = Table(); tx.add(ui.label(name, Ui.Size.M, Pal.TEXT, Align.left)).left().row(); tx.add(ui.label(desc, Ui.Size.S, Pal.TEXT_SOFT, Align.left, true)).width(340f).left()
            c.add(tx).expandX().left()
            lateinit var b: com.badlogic.gdx.scenes.scene2d.ui.Button
            fun mk(): com.badlogic.gdx.scenes.scene2d.ui.Button = ui.button(if (get()) L.t("menu.acik") else L.t("menu.kapali"), if (get()) Pal.GREEN else Pal.GREY, size = Ui.Size.S, textColor = if (get()) Pal.WHITE else Pal.TEXT) {
                set(!get()); session.save(); game.go({ SettingsScreen(game) }, remember = false)
            }
            b = mk(); c.add(b).width(150f)
            list.add(c).row()
        }
        // dil seçimi (değişiklik anında uygulanır, oyunu yeniden başlatmak gerekmez)
        run {
            val c = ui.card()
            c.add(ui.icon("globe", 56f)).size(56f).padRight(14f)
            val tx = Table()
            tx.add(ui.label(L.t("settings.language"), Ui.Size.M, Pal.TEXT, Align.left)).left().row()
            val cur = L.NATIVE_NAMES[L.lang] ?: L.lang
            tx.add(ui.label(if (st.language.isEmpty()) L.f("settings.language_auto_current", cur) else cur, Ui.Size.S, Pal.TEXT_SOFT, Align.left, true)).width(340f).left()
            c.add(tx).expandX().left()
            c.add(ui.button(L.t("settings.change"), Pal.TURQ, size = Ui.Size.S) { languagePicker() }).width(150f)
            list.add(c).row()
        }
        slider("music_on", L.t("menu.muzik"), { st.musicVolume }) { st.musicVolume = it }
        slider("sound_on", L.t("menu.efektler"), { st.sfxVolume }) { st.sfxVolume = it }
        toggle("character", L.t("menu.renk_korlugu_simgeleri"), L.t("menu.kapilarda_karakterde_daire_kare"), { st.colorBlindSymbols }) { st.colorBlindSymbols = it }
        toggle("bolt", L.t("menu.titresim"), L.t("menu.yanlis_kapida_kisa_titresim"), { st.vibration }) { st.vibration = it }
        toggle("sparkle", L.t("menu.azaltilmis_hareket"), L.t("menu.arka_plan_karakter_animasyonlarini"), { st.reducedMotion }) { st.reducedMotion = it }
        val tc = ui.card()
        val sync = game.lastSyncOk
        tc.add(ui.icon("clock", 56f)).size(56f).padRight(14f)
        val tt = Table()
        tt.add(ui.label(L.t("menu.zaman_dogrulama"), Ui.Size.M, Pal.TEXT, Align.left)).left().row()
        tt.add(ui.label(when {
            session.clock.hasUnverifiedGap -> L.t("menu.cihaz_yeniden_basladi_internete")
            session.clock.isVerified -> L.t("menu.saat_sunucuyla_dogrulandi")
            sync == false -> L.t("menu.sunucuya_ulasilamadi_cevrimdisi_mod")
            else -> L.t("menu.henuz_dogrulanmadi")
        }, Ui.Size.S, Pal.TEXT_SOFT, Align.left, true)).width(340f).left()
        tc.add(tt).expandX().left()
        tc.add(ui.button(L.t("menu.esitle"), Pal.TURQ, size = Ui.Size.S) {
            game.syncTime { ok, credit ->
                ui.toast(stage, if (ok) (if (credit > 0) L.f("menu.esitlendi_dk", credit / 60000) else L.t("menu.saat_esitlendi")) else L.t("menu.baglanti_yok"), if (ok) "check" else "info")
                refreshTopBar()
            }
        }).width(150f)
        list.add(tc).row()
        val misc = Table()
        misc.add(ui.button(L.t("menu.nasil_oynanir_2"), Pal.BLUE, "info", Ui.Size.S) { game.go({ TutorialScreen(game) }) }).pad(8f)
        misc.add(ui.button(L.t("menu.profil"), Pal.TURQ, "profile", Ui.Size.S) { game.go({ ProfileScreen(game) }) }).pad(8f)
        list.add(misc).row()
        if (game.ads.privacyOptionsRequired) list.add(ui.button(L.t("menu.reklam_gizlilik_secenekleri"), Pal.BLUE, "info", Ui.Size.S) { game.ads.showPrivacyOptions() }).width(520f).row()
        list.add(ui.button(L.t("menu.ilerlemeyi_sifirla"), Pal.RED, "refresh", Ui.Size.S) {
            ui.confirm(stage, L.t("menu.emin_misin"), L.t("menu.tum_bolumler_paralar_karakterler"), L.t("menu.sifirla")) {
                session.resetProgress(); game.clearHistory(); game.go({ SplashScreen(game) }, remember = false)
            }
        }).width(420f).row()
        list.add(ui.label(L.f("menu.yanlis_renk_yazi_tipi", game.platform.versionName, game.platform.platformName), Ui.Size.S, Pal.TEXT_SOFT, wrap = true)).padTop(10f).row()
        root.add(ui.scroll(list)).grow().padBottom(20f)
    }

    private fun languagePicker() {
        val st = session.data.settings
        val card = ui.card(pad = 30f)
        lateinit var ov: Table
        card.add(ui.label(L.t("settings.language"), Ui.Size.L)).padBottom(14f).row()
        fun pick(code: String) {
            ov.remove()
            st.language = code
            L.setLanguage(L.resolve(code, java.util.Locale.getDefault().language))
            session.save()
            game.go({ SettingsScreen(game) }, remember = false)
        }
        val sysName = L.NATIVE_NAMES[L.resolve("", java.util.Locale.getDefault().language)] ?: "English"
        card.add(ui.button(L.f("settings.language_auto", sysName), if (st.language.isEmpty()) Pal.GREEN else Pal.GREY, "globe", Ui.Size.S,
            if (st.language.isEmpty()) Pal.WHITE else Pal.TEXT) { pick("") }).width(520f).padBottom(10f).row()
        for ((code, name) in L.NATIVE_NAMES) {
            val sel = st.language == code
            card.add(ui.button(name, if (sel) Pal.GREEN else Pal.BLUE, if (sel) "check" else null, Ui.Size.S) { pick(code) }).width(520f).padBottom(10f).row()
        }
        card.add(ui.button(L.t("common.vazgec"), Pal.GREY, textColor = Pal.TEXT, size = Ui.Size.S) { ov.remove() }).width(520f).padTop(6f)
        ov = ui.overlay(stage, card) { ov.remove() }
    }

    private fun sliderStyle(): Slider.SliderStyle {
        val s = Slider.SliderStyle()
        s.background = ui.pill(Pal.hex(0xE6ECF5)).also { it.minHeight = 22f }
        s.knobBefore = ui.pill(Pal.TURQ).also { it.minHeight = 22f }
        s.knob = game.assets.drawable("circle").tint(Pal.WHITE).also { it.minWidth = 54f; it.minHeight = 54f }
        return s
    }
}

/** 16. Oyuncu profili */
class ProfileScreen(game: YanlisRenkGame) : BaseScreen(game) {
    override val recreate: (() -> BaseScreen) = { ProfileScreen(game) }
    override fun build() {
        root.top()
        root.add(topBar(L.t("menu.profil"))).growX().row()
        val d = session.data
        val head = ui.card(pad = 30f)
        head.add(CharacterActor(game, session.equipped(ShopCategory.CHARACTER), session.equipped(ShopCategory.COSTUME).style, 2)).size(220f, 220f).padRight(20f)
        val info = Table()
        info.add(ui.label(d.playerName, Ui.Size.XL, Pal.TEXT, Align.left)).left().row()
        info.add(ui.label(L.f("menu.seviye", d.playerLevel), Ui.Size.L, Pal.ORANGE, Align.left)).left().row()
        info.add(ui.progress(d.xp.toFloat() / PlayerLevels.xpForNext(d.playerLevel), Pal.YELLOW, 300f, 24f)).size(300f, 24f).left().padTop(6f).row()
        info.add(ui.label(L.f("menu.xp", d.xp, PlayerLevels.xpForNext(d.playerLevel)), Ui.Size.S, Pal.TEXT_SOFT, Align.left)).left().row()
        val (g, dia) = PlayerLevels.reward(d.playerLevel + 1)
        info.add(ui.label(L.f("menu.sonraki_seviye_odulu_altin", g) + if (dia > 0) L.f("menu.elmas", dia) else "", Ui.Size.S, Pal.TEXT_SOFT, Align.left, true)).width(330f).left()
        head.add(info)
        root.add(head).width(660f).padBottom(16f).row()
        val stats = ui.card(pad = 26f)
        stats.header(ui, L.t("menu.istatistikler"))
        fun s(icon: String, k: String, v: Any) {
            val r = Table(); r.add(ui.icon(icon, 42f)).size(42f).padRight(12f); r.add(ui.label(k, Ui.Size.S, Pal.TEXT_SOFT, Align.left)).expandX().left(); r.add(ui.label(v.toString(), Ui.Size.M))
            stats.add(r).width(590f).padBottom(6f).row()
        }
        s("flag", L.t("menu.tamamlanan_bolum"), "${d.completedLevels} / 500")
        s("star_full", L.t("menu.toplam_yildiz"), "${d.totalStars} / 1500")
        s("map", L.t("menu.tamamlanan_alan"), "${session.statValue(Stat.AREAS)} / 100")
        s("medal", L.t("menu.ustalik_yildizi"), session.statValue(Stat.MASTERY_STARS))
        s("check", L.t("common.gecilen_kapi"), d.stat(Stat.GATES))
        s("palette", L.t("menu.renk_degistirme"), d.stat(Stat.TAPS))
        s("infinity", L.t("menu.sonsuz_mod_rekoru"), d.endlessBest)
        s("bolt", L.t("common.en_yuksek_kombo"), d.stat(Stat.MAX_COMBO))
        s("coin", L.t("menu.toplam_kazanilan_altin"), d.stat(Stat.GOLD_EARNED))
        s("character", L.t("common.karakterler"), "${session.statValue(Stat.CHARACTERS)} / 30")
        s("trophy", L.t("common.basarilar"), "${d.achievementsUnlocked.size} / ${com.yanlisrenk.logic.progress.Achievements.all.size}")
        s("calendar", L.t("menu.giris_serisi_en_uzun"), d.daily.longestStreak)
        root.add(ui.scroll(stats)).width(660f).grow().padBottom(20f)
    }
}
