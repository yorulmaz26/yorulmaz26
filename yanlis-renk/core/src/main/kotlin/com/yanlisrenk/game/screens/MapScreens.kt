package com.yanlisrenk.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.yanlisrenk.game.YanlisRenkGame
import com.yanlisrenk.game.ui.Pal
import com.yanlisrenk.game.ui.Ui
import com.yanlisrenk.game.ui.trUpper
import com.yanlisrenk.logic.Reward
import com.yanlisrenk.logic.level.AreaCatalog

private fun areaColor(id: Int): Color = Pal.hex(AreaCatalog.area(id).accent1)

/** 3. Alan seçim ekranı (dünya haritası) */
class WorldMapScreen(game: YanlisRenkGame, private val mastery: Boolean = false) : BaseScreen(game) {
    override val recreate: (() -> BaseScreen) = { WorldMapScreen(game, mastery) }
    override val backgroundImage: String? get() = "ui2/map_bg.jpg"
    override fun build() {
        root.top()
        root.add(topBar(if (mastery) "Ustalık Modu" else "Harita")).growX().row()
        if (mastery) root.add(ui.label("Tamamladığın alanları daha hızlı ve hafıza kapılarıyla tekrar oyna.", Ui.Size.S, Pal.TEXT_SOFT, wrap = true)).width(620f).padBottom(10f).row()
        val list = Table()
        var focus: Table? = null
        val curArea = AreaCatalog.areaOfLevel(session.currentLevel)
        for (w in AreaCatalog.worlds) {
            val worldAreas = AreaCatalog.areas.filter { it.world == w.id }
            val head = Table()
            head.background = ui.pill(Pal.hex(w.skyTop))
            head.add(ui.label("Dünya ${w.id}", Ui.Size.S, Pal.TEXT_SOFT)).padRight(12f)
            head.add(ui.label(w.name, Ui.Size.L))
            val done = worldAreas.count { session.isAreaComplete(it.id) }
            head.add().expandX()
            head.add(ui.label("$done/5", Ui.Size.M)).padRight(10f)
            if (w.id in session.data.worldSetClaimed) head.add(ui.icon("trophy", 44f)).size(44f)
            head.pad(8f, 26f, 8f, 20f)
            list.add(head).width(660f).padTop(18f).padBottom(8f).row()
            for (a in worldAreas) {
                val unlocked = session.isAreaUnlocked(a.id)
                val usable = if (mastery) session.isAreaComplete(a.id) else unlocked
                val card = ui.card(if (usable) Pal.WHITE else Pal.hex(0xEEF1F6), 20f)
                val num = Table(); num.background = ui.pill(if (usable) areaColor(a.id) else Pal.GREY)
                num.add(ui.label(a.id.toString(), Ui.Size.L, Pal.WHITE)).width(70f).pad(6f)
                card.add(num).padRight(16f)
                val tx = Table()
                tx.add(ui.label(a.name, Ui.Size.M, if (usable) Pal.TEXT else Pal.TEXT_SOFT, Align.left)).left().row()
                tx.add(ui.label(if (usable) a.description else if (mastery) "Önce alanı tamamla" else "Bölüm ${a.firstLevel} ile açılır", Ui.Size.S, Pal.TEXT_SOFT, Align.left, true)).width(330f).left()
                card.add(tx).expandX().left()
                val right = Table()
                if (!usable) right.add(ui.icon("lock", 60f)).size(60f)
                else if (mastery) { right.add(ui.icon("medal", 44f)).size(44f).row(); right.add(ui.label("${session.masteryStars(a.id)}/5", Ui.Size.S)) }
                else { right.add(ui.icon("star_full", 44f)).size(44f).row(); right.add(ui.label("${session.areaStars(a.id)}/15", Ui.Size.S)) }
                card.add(right).width(110f)
                if (usable) ui.onTap(card) {
                    if (!mastery && a.id !in session.data.areaIntroSeen) game.go({ AreaIntroScreen(game, a.id) }) else game.go({ AreaScreen(game, a.id, mastery) })
                }
                list.add(card).width(660f).padBottom(10f).row()
                if (a.id == curArea) focus = card
            }
        }
        val sp = ui.scroll(list)
        root.add(sp).grow()
        focus?.let { f -> stage.addAction(Actions.delay(0.05f, Actions.run { sp.layout(); sp.scrollTo(0f, f.y - 200f, f.width, 600f, true, true); sp.updateVisualScroll() })) }
    }
}

/** 4. Bölüm seçim ekranı */
class AreaScreen(game: YanlisRenkGame, private val areaId: Int, private val mastery: Boolean = false) : BaseScreen(game) {
    override val recreate: (() -> BaseScreen) = { AreaScreen(game, areaId, mastery) }
    override val music: String get() = AreaCatalog.world(AreaCatalog.area(areaId).world).music
    override fun build() {
        val a = AreaCatalog.area(areaId)
        val w = AreaCatalog.world(a.world)
        root.top()
        root.add(topBar(null)).growX().row()
        root.add(ui.label("${w.name} · Alan ${a.id}", Ui.Size.S, Pal.TEXT_SOFT)).row()
        root.add(ui.label(a.name, Ui.Size.XL)).row()
        root.add(ui.label(if (mastery) "Ustalık: daha hızlı, hafıza kapılı sürümler" else a.description, Ui.Size.S, Pal.TEXT_SOFT, wrap = true)).width(620f).padBottom(14f).row()
        val path = Table()
        for (i in 0 until 5) {
            val id = a.firstLevel + i
            val final = i == 4
            val unlocked = if (mastery) session.stars(id) > 0 else session.isLevelUnlocked(id)
            val stars = if (mastery) session.data.masteryStars[id] ?: 0 else session.stars(id)
            val node = Table()
            val size = if (final) 190f else 150f
            val c = if (!unlocked) Pal.GREY else if (final) Pal.ORANGE else areaColor(areaId)
            val b = ui.button("", c) {
                when {
                    !session.lives.canPlay -> game.go({ LivesScreen(game) })
                    final && !mastery -> game.go({ AreaFinalScreen(game, id) })
                    else -> game.go({ GameScreen(game, if (mastery) Mode.MASTERY else Mode.LEVEL, id) })
                }
            }
            b.clearChildren()
            if (!unlocked) b.add(ui.icon("lock", 70f)).size(70f)
            else if (final) { b.add(ui.icon("crown", 70f)).size(70f).row(); b.add(ui.label("FİNAL", Ui.Size.S, Pal.WHITE)) }
            else b.add(ui.label(id.toString(), Ui.Size.XL, Pal.WHITE))
            b.isDisabled = !unlocked
            node.add(b).size(size).row()
            node.add(ui.stars(stars, size = 40f)).padTop(4f).row()
            if (unlocked) node.add(ui.label(if (final) a.finalTitle else "Bölüm $id", Ui.Size.S, Pal.TEXT_SOFT)).row()
            val offset = if (i % 2 == 0) -120f else 120f
            path.add(node).padLeft(if (offset > 0) offset * 2 else 0f).padRight(if (offset < 0) -offset * 2 else 0f).padBottom(14f).row()
        }
        root.add(path).expandY().row()
        val info = ui.card(pad = 20f)
        info.add(ui.label("Alan ödülü:", Ui.Size.S, Pal.TEXT_SOFT)).padRight(10f)
        info.add(ui.chip("coin", a.rewardGold.toString())).padRight(8f)
        info.add(ui.chip("diamond", a.rewardDiamonds.toString())).padRight(8f)
        info.add(ui.chip("box", if (a.id in session.data.collection) "✓" else "?"))
        root.add(info).padBottom(20f).row()
        if (!mastery && session.isAreaComplete(areaId)) root.add(ui.button("Ustalık Modu", Pal.PURPLE, "medal", Ui.Size.S) { game.go({ AreaScreen(game, areaId, true) }) }).padBottom(20f)
    }
}

/** 21. Alan açılış ekranı */
class AreaIntroScreen(game: YanlisRenkGame, private val areaId: Int) : BaseScreen(game) {
    override val music: String get() = AreaCatalog.world(AreaCatalog.area(areaId).world).music
    override fun build() {
        val a = AreaCatalog.area(areaId)
        val w = AreaCatalog.world(a.world)
        session.data.areaIntroSeen.add(areaId); session.save()
        val card = ui.card(pad = 40f)
        card.add(ui.label("DÜNYA ${w.id} · ${w.name.trUpper()}", Ui.Size.S, Pal.TEXT_SOFT)).row()
        card.add(ui.label("Alan ${a.id}", Ui.Size.M, Pal.ORANGE)).padTop(6f).row()
        card.add(ui.label(a.name, Ui.Size.XL)).padBottom(10f).row()
        card.add(ui.label(a.description, Ui.Size.M, Pal.TEXT_SOFT, wrap = true)).width(560f).padBottom(18f).row()
        val mech = a.introduces.ifEmpty { a.focus.filter { it != com.yanlisrenk.logic.model.Mechanic.EXAM }.take(2) }
        for (m in mech.take(3)) {
            val t = Table(); t.background = ui.panel(Pal.hex(0xEAF6FF)); t.pad(14f)
            t.add(MechanicActor(game, m)).size(150f, 120f).padRight(14f)
            val tx = Table()
            tx.add(ui.label((if (m in a.introduces) "YENİ: " else "") + m.title, Ui.Size.M, Pal.BLUE, Align.left)).left().row()
            tx.add(ui.label(m.hint, Ui.Size.S, Pal.TEXT, Align.left, true)).width(380f).left()
            t.add(tx)
            card.add(t).width(580f).padBottom(10f).row()
        }
        card.add(ui.label("Toplanacak parça: ${a.collectible}", Ui.Size.S, Pal.TEXT_SOFT)).padTop(6f).padBottom(18f).row()
        card.add(ui.button("Alana Gir", Pal.GREEN, "play") { game.go({ AreaScreen(game, areaId) }, remember = false) }).width(420f)
        root.add(card).width(660f)
    }
}

/** 22. Alan finali ekranı */
class AreaFinalScreen(game: YanlisRenkGame, private val levelId: Int) : BaseScreen(game) {
    override fun build() {
        val a = AreaCatalog.area(AreaCatalog.areaOfLevel(levelId))
        val lvl = game.levels.level(levelId)
        val card = ui.card(pad = 40f)
        card.add(ui.icon("crown", 120f)).size(120f).row()
        card.add(ui.label("ALAN FİNALİ", Ui.Size.L, Pal.ORANGE)).row()
        card.add(ui.label(a.finalTitle, Ui.Size.XL)).padBottom(10f).row()
        card.add(ui.label("Bu alanda öğrendiğin her şeyi ölçen ${lvl.gates.size} kapılık büyük sınav.", Ui.Size.M, Pal.TEXT_SOFT, wrap = true)).width(540f).padBottom(14f).row()
        val m = Table()
        lvl.mechanics.filter { it != com.yanlisrenk.logic.model.Mechanic.SAME }.take(6).forEach { mm ->
            val p = Table(); p.background = ui.pill(Pal.hex(0xEAF6FF)); p.add(ui.label(mm.title, Ui.Size.S, Pal.BLUE)).pad(4f, 14f, 4f, 14f)
            m.add(p).pad(4f)
            if (m.children.size % 2 == 0) m.row()
        }
        card.add(m).padBottom(16f).row()
        val r = Table()
        r.add(ui.label("Ödül", Ui.Size.S, Pal.TEXT_SOFT)).colspan(2).row()
        r.add(ui.chip("coin", a.rewardGold.toString())).padRight(8f); r.add(ui.chip("diamond", (a.rewardDiamonds + 2).toString())).row()
        r.add(ui.chip("box", a.collectible)).colspan(2).padTop(6f)
        card.add(r).padBottom(24f).row()
        card.add(ui.button("Finale Başla", Pal.ORANGE, "play") { game.go({ if (session.lives.canPlay) GameScreen(game, Mode.LEVEL, levelId) else LivesScreen(game) }, remember = false) }).width(440f).padBottom(10f).row()
        card.add(ui.button("Geri", Pal.GREY, textColor = Pal.TEXT, size = Ui.Size.M) { onBack() }).width(300f)
        root.add(card).width(660f)
    }
}

/** 25. Ödül kazanma ekranı */
class RewardScreen(game: YanlisRenkGame, private val reward: Reward, private val levelId: Int, private val mastery: Boolean) : BaseScreen(game) {
    override fun build() {
        game.audio.play(if (reward.areaCompleted > 0) "area_complete" else "unlock")
        val card = ui.card(pad = 40f)
        val hero = if (reward.areaCompleted > 0) "ui2_chest" else "ui2_stars"
        if (game.assets.has(hero)) {
            val img = com.badlogic.gdx.scenes.scene2d.ui.Image(game.assets.drawable(hero), com.badlogic.gdx.utils.Scaling.fit)
            card.add(img).size(if (hero == "ui2_chest") 200f else 300f, 200f).row()
        } else card.add(ui.icon(if (reward.areaCompleted > 0) "trophy" else "gift", 150f)).size(150f).row()
        if (reward.areaCompleted > 0) {
            val a = AreaCatalog.area(reward.areaCompleted)
            card.add(ui.label("ALAN TAMAMLANDI!", Ui.Size.L, Pal.GREEN)).row()
            card.add(ui.label(a.name, Ui.Size.XL)).padBottom(10f).row()
            reward.collectible?.let {
                val c = Table(); c.background = ui.panel(Pal.hex(0xFFF4D6)); c.pad(16f)
                c.add(ui.icon("box", 70f)).size(70f).padRight(12f)
                c.add(ui.label("Koleksiyon parçası:\n$it", Ui.Size.M, Pal.TEXT, Align.left))
                card.add(c).padBottom(12f).row()
            }
        }
        if (reward.worldCompleted > 0) card.add(ui.label("${AreaCatalog.world(reward.worldCompleted).name} seti tamam! +20 elmas", Ui.Size.M, Pal.ORANGE, wrap = true)).width(540f).padBottom(10f).row()
        reward.levelUps.forEach { lv -> card.add(ui.label("Seviye atladın: $lv", Ui.Size.L, Pal.ORANGE)).padBottom(6f).row() }
        val rw = Table()
        rw.add(ui.chip("coin", "+${reward.gold}")).pad(6f)
        if (reward.diamonds > 0) rw.add(ui.chip("diamond", "+${reward.diamonds}")).pad(6f)
        rw.add(ui.chip("sparkle", "+${reward.xp} XP")).pad(6f)
        card.add(rw).padBottom(20f).row()
        val next = levelId + 1
        card.add(ui.button("Devam", Pal.GREEN, "play") {
            game.go({
                if (!mastery && next <= 500 && reward.areaCompleted > 0) {
                    val na = AreaCatalog.areaOfLevel(next)
                    if (na !in session.data.areaIntroSeen) AreaIntroScreen(game, na) else AreaScreen(game, na)
                } else AreaScreen(game, AreaCatalog.areaOfLevel(levelId), mastery)
            }, remember = false)
        }).width(420f)
        root.add(card).width(660f)
        repeat(3) { i -> stage.addAction(Actions.delay(i * 0.25f, Actions.run { game.audio.play("star", 1f + i * 0.1f) })) }
        flushAchievementToasts()
    }
    override fun onBack() { game.go({ AreaScreen(game, AreaCatalog.areaOfLevel(levelId), mastery) }, remember = false) }
}

/** 23. Ustalık modu ekranı = ustalık filtreli harita */
fun masteryScreen(game: YanlisRenkGame) = WorldMapScreen(game, mastery = true)

@Suppress("unused") private fun ScrollPane.noop() = Unit
