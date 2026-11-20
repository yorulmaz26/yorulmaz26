package com.yanlisrenk.game.screens

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.yanlisrenk.game.YanlisRenkGame
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
    private var t = 0f
    private var done = false
    override fun build() {
        val ch = CharacterActor(game, session.equipped(ShopCategory.CHARACTER), session.equipped(ShopCategory.COSTUME).style, 0, cycleColors = true)
        root.add(ch).size(300f, 300f).padBottom(20f).row()
        if (game.assets.has("ui2_logo")) {
            val logo = com.badlogic.gdx.scenes.scene2d.ui.Image(game.assets.drawable("ui2_logo"), com.badlogic.gdx.utils.Scaling.fit)
            root.add(logo).size(520f, 440f).padBottom(10f).row()
        } else {
            val title = Table()
            "YANLIŞ".forEachIndexed { i, c -> title.add(ui.label(c.toString(), Ui.Size.HUGE, Pal.game[i % 4])) }
            root.add(title).row()
            root.add(ui.label("RENK", Ui.Size.HUGE, Pal.TEXT)).padBottom(30f).row()
        }
        root.add(ui.label("Renkler seni kandırmasın!", Ui.Size.M, Pal.TEXT_SOFT)).padBottom(60f).row()
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
        lvl.add(ui.label("Seviye ${session.data.playerLevel}", Ui.Size.S)).padRight(10f)
        lvl.add(ui.progress(session.data.xp.toFloat() / PlayerLevels.xpForNext(session.data.playerLevel), Pal.YELLOW, 200f, 20f)).size(200f, 20f)
        ui.onTap(lvl) { game.go({ ProfileScreen(game) }) }
        root.add(lvl).padTop(4f).row()
        if (game.assets.has("ui2_logo")) {
            val logo = com.badlogic.gdx.scenes.scene2d.ui.Image(game.assets.drawable("ui2_logo"), com.badlogic.gdx.utils.Scaling.fit)
            root.add(logo).size(400f, 260f).padTop(6f).row()
        } else {
            val title = Table()
            "YANLIŞ".forEachIndexed { i, c -> title.add(ui.label(c.toString(), Ui.Size.HUGE, Pal.game[i % 4])) }
            root.add(title).padTop(16f).row()
            root.add(ui.label("RENK", Ui.Size.XL, Pal.TEXT)).row()
        }
        val ch = CharacterActor(game, session.equipped(ShopCategory.CHARACTER), session.equipped(ShopCategory.COSTUME).style, 0)
        root.add(ch).size(220f, 220f).row()
        ui.onTap(ch) { }

        val cur = session.currentLevel
        val area = AreaCatalog.area(AreaCatalog.areaOfLevel(cur))
        val play = ui.button("OYNA", Pal.GREEN, "play", Ui.Size.XL) { openLevel(cur) }
        root.add(play).width(520f).height(140f).padTop(6f).row()
        root.add(ui.label("Bölüm $cur · ${area.name}", Ui.Size.S, Pal.TEXT_SOFT)).padTop(6f).padBottom(12f).row()
        val row2 = Table()
        row2.add(ui.button("Sonsuz", Pal.TURQ, "infinity", Ui.Size.M) { game.go({ GameScreen(game, Mode.ENDLESS) }) }).width(250f).padRight(16f)
        row2.add(ui.button("Harita", Pal.BLUE, "map", Ui.Size.M) { game.go({ SagaMapScreen(game) }) }).width(250f).padRight(16f)
        row2.add(ui.iconButton("medal", 100f, Pal.PURPLE) {
            if (session.isAreaComplete(1)) game.go({ masteryScreen(game) }) else ui.toast(stage, "Ustalık için önce bir alanı tamamla", "medal")
        }).size(100f)
        root.add(row2).padBottom(12f).row()
        root.add(ui.button("2 Kişilik Kapışma", Pal.ORANGE, "profile", Ui.Size.M) { game.go({ TwoPlayerScreen(game) }) }).width(536f).padBottom(14f).row()

        val grid = Table()
        fun tile(icon: String, label: String, count: Int = 0, action: () -> Unit) {
            val t = Table()
            t.add(badge(ui, ui.iconButton(icon, 104f) { action() }, count)).size(104f).row()
            t.add(ui.label(label, Ui.Size.S)).padTop(2f)
            grid.add(t).width(170f).pad(6f)
        }
        tile("shop", "Market") { game.go({ ShopScreen(game) }) }
        tile("character", "Karakter") { game.go({ CharactersScreen(game) }) }
        tile("missions", "Görevler", session.claimableMissions) { game.go({ MissionsScreen(game) }) }
        tile("trophy", "Başarılar", session.claimableAchievements) { game.go({ AchievementsScreen(game) }) }
        grid.row()
        tile("gift", "Günlük", if (session.canClaimDaily()) 1 else 0) { game.go({ DailyRewardScreen(game) }) }
        tile("flag", "Haftalık") { game.go({ WeeklyChallengeScreen(game) }) }
        tile("box", "Koleksiyon") { game.go({ CollectionScreen(game) }) }
        tile("settings", "Ayarlar") { game.go({ SettingsScreen(game) }) }
        root.add(grid).row()
        if (session.loadOutcome == LoadOutcome.RESTORED_BACKUP) ui.toast(stage, "Kayıt yedekten geri yüklendi", "info")
        if (session.canClaimDaily()) stage.addAction(Actions.delay(0.4f, Actions.run { game.go({ DailyRewardScreen(game) }) }))
        flushAchievementToasts()
    }

    private fun openLevel(id: Int) {
        val area = AreaCatalog.areaOfLevel(id)
        when {
            !session.lives.canPlay -> game.go({ LivesScreen(game) })
            area !in session.data.areaIntroSeen -> game.go({ AreaIntroScreen(game, area) })
            AreaCatalog.area(area).lastLevel == id -> game.go({ AreaFinalScreen(game, id) })
            else -> game.go({ GameScreen(game, Mode.LEVEL, id) })
        }
    }

    override fun onBack() { com.badlogic.gdx.Gdx.app.exit() }
}

/** 20. Öğretici ekranlar */
class TutorialScreen(game: YanlisRenkGame, private val first: Boolean = false) : BaseScreen(game) {
    private var page = 0
    private val pages = listOf(
        Triple("Dokun, renk değiştir!", "Ekranın herhangi bir yerine dokun. Karakterin sırayla Kırmızı > Mavi > Yeşil > Sarı olur.", Mechanic.SAME),
        Triple("Eşittir: Aynı renk", "Eşittir işaretli kapıdan kapıyla AYNI renkteyken geç.", Mechanic.SAME),
        Triple("Eşit değil: Farklı renk", "Eşit değildir işaretli kapıdan kapının renginden FARKLI bir renkle geç.", Mechanic.DIFF),
        Triple("Şekiller de söyler", "Renk körlüğü desteği: Kırmızı daire, Mavi kare, Yeşil üçgen, Sarı yıldız. Ayarlardan açıp kapatabilirsin.", Mechanic.RAINBOW),
        Triple("4 can", "Bölümlü modda başarısız olursan 1 can gider. Her can 15 dakikada dolar. Sonsuz mod her zaman ücretsiz!", Mechanic.SPEED),
    )
    override fun build() { show(0) }
    private fun show(p: Int) {
        page = p
        root.clear()
        root.add(ui.label("Nasıl Oynanır? ${p + 1}/${pages.size}", Ui.Size.M, Pal.TEXT_SOFT)).padBottom(20f).row()
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
        if (p > 0) row.add(ui.button("Geri", Pal.GREY, textColor = Pal.TEXT, size = Ui.Size.M) { show(p - 1) }).padRight(16f)
        row.add(ui.button(if (p == pages.size - 1) "Başla!" else "İleri", Pal.GREEN, if (p == pages.size - 1) "play" else null) {
            if (p == pages.size - 1) finish() else show(p + 1)
        })
        card.add(row)
        root.add(card).width(640f)
        if (!first) root.row().also { root.add(ui.button("Kapat", Pal.BLUE, size = Ui.Size.S) { finish() }).padTop(20f) }
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
        root.add(topBar("Ayarlar", showLives = false)).growX().row()
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
            fun mk(): com.badlogic.gdx.scenes.scene2d.ui.Button = ui.button(if (get()) "AÇIK" else "KAPALI", if (get()) Pal.GREEN else Pal.GREY, size = Ui.Size.S, textColor = if (get()) Pal.WHITE else Pal.TEXT) {
                set(!get()); session.save(); game.go({ SettingsScreen(game) }, remember = false)
            }
            b = mk(); c.add(b).width(150f)
            list.add(c).row()
        }
        slider("music_on", "Müzik", { st.musicVolume }) { st.musicVolume = it }
        slider("sound_on", "Efektler", { st.sfxVolume }) { st.sfxVolume = it }
        toggle("character", "Renk körlüğü simgeleri", "Kapılarda ve karakterde daire, kare, üçgen, yıldız şekilleri", { st.colorBlindSymbols }) { st.colorBlindSymbols = it }
        toggle("bolt", "Titreşim", "Yanlış kapıda kısa titreşim", { st.vibration }) { st.vibration = it }
        toggle("sparkle", "Azaltılmış hareket", "Arka plan ve karakter animasyonlarını azaltır", { st.reducedMotion }) { st.reducedMotion = it }
        val tc = ui.card()
        val sync = game.lastSyncOk
        tc.add(ui.icon("clock", 56f)).size(56f).padRight(14f)
        val tt = Table()
        tt.add(ui.label("Zaman doğrulama", Ui.Size.M, Pal.TEXT, Align.left)).left().row()
        tt.add(ui.label(when {
            session.clock.hasUnverifiedGap -> "Cihaz yeniden başladı; internete bağlanınca bekleyen süre eklenecek."
            session.clock.isVerified -> "Saat sunucuyla doğrulandı."
            sync == false -> "Sunucuya ulaşılamadı. Çevrimdışı mod: can süresi cihaz açık kaldıkça işler."
            else -> "Henüz doğrulanmadı."
        }, Ui.Size.S, Pal.TEXT_SOFT, Align.left, true)).width(340f).left()
        tc.add(tt).expandX().left()
        tc.add(ui.button("Eşitle", Pal.TURQ, size = Ui.Size.S) {
            game.syncTime { ok, credit ->
                ui.toast(stage, if (ok) (if (credit > 0) "Eşitlendi: +${credit / 60000} dk" else "Saat eşitlendi") else "Bağlantı yok", if (ok) "check" else "info")
                refreshTopBar()
            }
        }).width(150f)
        list.add(tc).row()
        val misc = Table()
        misc.add(ui.button("Nasıl Oynanır", Pal.BLUE, "info", Ui.Size.S) { game.go({ TutorialScreen(game) }) }).pad(8f)
        misc.add(ui.button("Profil", Pal.TURQ, "profile", Ui.Size.S) { game.go({ ProfileScreen(game) }) }).pad(8f)
        list.add(misc).row()
        list.add(ui.button("İlerlemeyi Sıfırla", Pal.RED, "refresh", Ui.Size.S) {
            ui.confirm(stage, "Emin misin?", "Tüm bölümler, paralar ve karakterler silinir. Bu işlem geri alınamaz.", "Sıfırla") {
                session.resetProgress(); game.clearHistory(); game.go({ SplashScreen(game) }, remember = false)
            }
        }).width(420f).row()
        list.add(ui.label("Yanlış Renk ${game.platform.versionName} · ${game.platform.platformName}\nYazı tipi: Nunito (SIL OFL 1.1). Tüm görsel ve sesler bu proje için üretilmiştir.", Ui.Size.S, Pal.TEXT_SOFT, wrap = true)).padTop(10f).row()
        root.add(ui.scroll(list)).grow().padBottom(20f)
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
        root.add(topBar("Profil")).growX().row()
        val d = session.data
        val head = ui.card(pad = 30f)
        head.add(CharacterActor(game, session.equipped(ShopCategory.CHARACTER), session.equipped(ShopCategory.COSTUME).style, 2)).size(220f, 220f).padRight(20f)
        val info = Table()
        info.add(ui.label(d.playerName, Ui.Size.XL, Pal.TEXT, Align.left)).left().row()
        info.add(ui.label("Seviye ${d.playerLevel}", Ui.Size.L, Pal.ORANGE, Align.left)).left().row()
        info.add(ui.progress(d.xp.toFloat() / PlayerLevels.xpForNext(d.playerLevel), Pal.YELLOW, 300f, 24f)).size(300f, 24f).left().padTop(6f).row()
        info.add(ui.label("${d.xp} / ${PlayerLevels.xpForNext(d.playerLevel)} XP", Ui.Size.S, Pal.TEXT_SOFT, Align.left)).left().row()
        val (g, dia) = PlayerLevels.reward(d.playerLevel + 1)
        info.add(ui.label("Sonraki seviye ödülü: $g altın" + if (dia > 0) " + $dia elmas" else "", Ui.Size.S, Pal.TEXT_SOFT, Align.left, true)).width(330f).left()
        head.add(info)
        root.add(head).width(660f).padBottom(16f).row()
        val stats = ui.card(pad = 26f)
        stats.header(ui, "İstatistikler")
        fun s(icon: String, k: String, v: Any) {
            val r = Table(); r.add(ui.icon(icon, 42f)).size(42f).padRight(12f); r.add(ui.label(k, Ui.Size.S, Pal.TEXT_SOFT, Align.left)).expandX().left(); r.add(ui.label(v.toString(), Ui.Size.M))
            stats.add(r).width(590f).padBottom(6f).row()
        }
        s("flag", "Tamamlanan bölüm", "${d.completedLevels} / 500")
        s("star_full", "Toplam yıldız", "${d.totalStars} / 1500")
        s("map", "Tamamlanan alan", "${session.statValue(Stat.AREAS)} / 100")
        s("medal", "Ustalık yıldızı", session.statValue(Stat.MASTERY_STARS))
        s("check", "Geçilen kapı", d.stat(Stat.GATES))
        s("palette", "Renk değiştirme", d.stat(Stat.TAPS))
        s("infinity", "Sonsuz mod rekoru", d.endlessBest)
        s("bolt", "En yüksek kombo", d.stat(Stat.MAX_COMBO))
        s("coin", "Toplam kazanılan altın", d.stat(Stat.GOLD_EARNED))
        s("character", "Karakterler", "${session.statValue(Stat.CHARACTERS)} / 30")
        s("trophy", "Başarılar", "${d.achievementsUnlocked.size} / ${com.yanlisrenk.logic.progress.Achievements.all.size}")
        s("calendar", "Giriş serisi (en uzun)", d.daily.longestStreak)
        root.add(ui.scroll(stats)).width(660f).grow().padBottom(20f)
    }
}
