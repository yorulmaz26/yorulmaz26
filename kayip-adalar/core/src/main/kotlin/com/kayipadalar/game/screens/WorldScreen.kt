package com.kayipadalar.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.kayipadalar.game.KayipAdalarGame
import com.kayipadalar.game.ui.Palette
import com.kayipadalar.game.view.WorldView
import com.kayipadalar.logic.kingdom.IslandState
import com.kayipadalar.logic.kingdom.IslandStatus
import com.kayipadalar.logic.kingdom.Res

/** Takımada haritası: keşfet, korsanlardan kurtar, krallığa çek. */
class WorldScreen(app: KayipAdalarGame, private val kingdom: KingdomScreen) : BaseScreen(app) {
    private lateinit var view: WorldView
    private val panel = Table()
    private var timer = 0f

    override fun build() {
        if (!::view.isInitialized) {
            view = WorldView(k, assets, theme) { app.game.now() }
            // görevle ilgili en yakın adayı seç
            val pick = k.s.islands.filter { it.status == IslandStatus.CLEARED }.minByOrNull { it.ring }
                ?: k.s.islands.filter { it.status == IslandStatus.DISCOVERED }.minByOrNull { it.guard }
                ?: k.s.islands.filter { it.status == IslandStatus.HIDDEN && it.ring <= maxOf(1, k.levelOf("explore")) }.minByOrNull { it.ring }
            pick?.let { view.selected = it.id; view.focus(it) }
        }
        view.setFillParent(true)
        stage.addActor(view); view.toBack()
        view.onIsland = { refreshPanel() }
        view.onHome = { app.back() }
        root.touchable = Touchable.childrenOnly
        root.top()
        val head = Table()
        head.background = theme.tinted("white", Color(0.08f, 0.12f, 0.22f, 0.6f))
        head.pad(10f)
        head.add(button("Krallık", "smallWood", icon = "icons/crown") { app.back() }).size(190f, 72f)
        head.add(label("Takımadalar", "title")).expandX()
        val chip = Table(); chip.background = theme.patch("chip"); chip.pad(4f, 8f, 4f, 12f)
        chip.add(sprite("icons/swords", 34f)).size(34f).padRight(4f); chip.add(label("Güç ${k.armyPower()}", "lightSmall"))
        head.add(chip)
        root.add(head).growX().row()
        root.add().grow().row()
        root.add(panel).growX().pad(8f)
        refreshPanel()
    }

    override fun update(dt: Float) {
        timer += dt
        if (timer > 0.5f) {
            timer = 0f
            for (e in app.game.tick()) toast(e)
            val sel = view.selected?.let { k.island(it) }
            if (sel != null && sel.status == IslandStatus.EXPLORING) refreshPanel()
        }
    }

    private fun refreshPanel() {
        panel.clear()
        val i = view.selected?.let { k.island(it) }
        if (i == null) {
            val p = parchment(14f)
            p.add(label("Bir adaya dokun. Bulutlu adaları keşfet, korsanlı adaları kurtar, kurtardıklarını krallığına çek.", "small", wrap = true, align = Align.center)).growX()
            panel.add(p).growX(); return
        }
        val d = k.cfg.island(i.type)
        val p = parchment(14f)
        val known = i.status != IslandStatus.HIDDEN && i.status != IslandStatus.EXPLORING
        val head = Table()
        head.add(sprite(if (known) d.sprite else "weather/cloud", 120f)).size(120f).padRight(10f)
        val info = Table()
        info.add(label(if (known) d.name else "Bilinmeyen Ada (${i.ring}. halka)", "default")).left().row()
        info.add(label(if (known) d.desc else "Bulutların ardında ne olduğunu öğrenmek için keşif gemisi gönder.", "small", wrap = true)).growX().left().row()
        if (i.status == IslandStatus.DISCOVERED) {
            val strong = k.armyPower() >= i.guard
            info.add(label("Korsan gücü: ${i.guard} · Ordun: ${k.armyPower()}", "small", if (strong) Palette.green else Palette.red)).left().row()
        }
        head.add(info).growX()
        p.add(head).growX().row()
        val n = app.game.now()
        when (i.status) {
            IslandStatus.HIDDEN -> {
                val err = k.canExplore(i)
                p.add(costRow(k.exploreCost(i)) { k.res(it) }).left().row()
                p.add(label("Süre: ${fmtTime(k.exploreSeconds(i).toLong())}", "tiny")).left().row()
                if (err != null) p.add(label(err, "small", Palette.red, wrap = true)).growX().left().row()
                p.add(button("Keşfet", "blue", err == null, icon = "ships/ship_explore") {
                    k.explore(i.id, app.game.now())?.let { error(it) } ?: run { app.audio.play("slide"); app.game.persist(); refreshPanel() }
                }).growX().height(84f).padTop(6f)
            }
            IslandStatus.EXPLORING -> {
                p.add(progressBar(gold = true) { ((app.game.now() - i.exploreStarted).toFloat() / (i.exploreUntil - i.exploreStarted).coerceAtLeast(1)) }).growX().height(20f).row()
                val cost = k.speedupCost(i.exploreUntil - n)
                p.add(button("Hızlandır ($cost kristal)", "blue", k.res(Res.CRYSTAL) >= cost, icon = "resources/crystal") {
                    k.speedupExplore(i.id, app.game.now())?.let { error(it) } ?: refreshPanel()
                }).growX().height(78f).padTop(8f)
            }
            IslandStatus.DISCOVERED -> {
                val strong = k.armyPower() >= i.guard
                p.add(label(if (strong) "Ordun yeterince güçlü. Saldırabilirsin!" else "Ordun zayıf. Kışla'da daha fazla asker eğit.", "small", if (strong) Palette.green else Palette.red, wrap = true)).growX().row()
                p.add(button("Saldır", "red", k.armyPower() > 0, icon = "icons/swords") { attack(i) }).growX().height(84f).padTop(6f)
            }
            IslandStatus.CLEARED -> {
                p.add(label("Ada güvende! Krallığına çekip birleştirebilirsin.", "small", Palette.green, wrap = true)).growX().row()
                p.add(button("Krallığa Çek", "green", k.freeDocks().isNotEmpty(), icon = "effects/fx_merge1") {
                    k.merge(i.id)?.let { error(it) } ?: run {
                        app.audio.play("combo"); app.game.persist()
                        kingdom.mergeArrived(i.id)
                        app.back()
                    }
                }).growX().height(84f).padTop(6f)
                if (k.freeDocks().isEmpty()) p.add(label("Krallığın çevresinde boş yer yok.", "tiny", Palette.red)).row()
            }
            IslandStatus.MERGED -> p.add(label("Bu ada krallığının parçası.", "small")).row()
        }
        panel.add(p).growX()
    }

    private fun attack(i: IslandState) {
        val ours = kingdom.armySprites()
        val (err, rep) = k.attack(i.id)
        if (err != null) { error(err); return }
        val theirs = List((2 + i.ring).coerceAtMost(8)) { listOf("units/swordsman", "units/spearman", "units/u_archer", "units/commander")[it % 4] }
        battle(rep!!, ours, theirs) { refreshPanel() }
    }

    override fun afterBattle() = refreshPanel()
}
