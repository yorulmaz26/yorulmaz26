package com.kayipadalar.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.kayipadalar.game.KayipAdalarGame
import com.kayipadalar.game.render.IslandArt
import com.kayipadalar.game.ui.Palette
import com.kayipadalar.logic.Challenge
import com.kayipadalar.logic.PlayBlock
import com.kayipadalar.logic.level.AreaCatalog
import com.kayipadalar.logic.level.Feature
import com.kayipadalar.logic.level.ObjectiveType
import com.kayipadalar.logic.tr

val FEATURE_NAMES = AreaCatalog.FEATURE_NAMES

class WorldMapScreen(game: KayipAdalarGame) : BaseScreen(game) {
    override fun build() {
        header(tr("menu.worldmap"))
        val list = Table()
        val h = core.save.highestUnlocked
        var focusRow = 0f
        for (a in 1..AreaCatalog.AREAS) {
            val spec = AreaCatalog.area(a)
            val unlocked = spec.firstLevel <= h
            val stars = (spec.firstLevel until spec.firstLevel + 5).sumOf { core.record(it)?.stars ?: 0 }
            val p = panel(alt = !unlocked)
            val sky = IslandArt.themeSky[spec.theme]?.first ?: Palette.skyTop
            p.background = theme.tinted("round", if (unlocked) Color(sky.r, sky.g, sky.b, 0.55f).lerp(Color.WHITE, 0.45f) else Color(0.9f, 0.92f, 0.95f, 0.9f))
            val left = Table()
            left.add(label("Alan $a · ${spec.name}", "default", if (unlocked) Palette.text else Palette.textMuted, wrap = true)).growX().left().row()
            val feat = spec.newFeature?.let { "Yeni: ${FEATURE_NAMES[it]}" } ?: "Remiks: ${spec.focus.joinToString(" + ") { FEATURE_NAMES[it] ?: it.name }}"
            left.add(label("Bölüm ${spec.firstLevel}–${spec.firstLevel + 4} · $feat", "muted", wrap = true)).growX().left().row()
            val boss = when (spec.bossTier) { 2 -> "Dünya patronu"; 1 -> "Büyük patron"; else -> "Alan finali" }
            left.add(label("$boss · ★ $stars/15", "small", Palette.accentDown)).left()
            p.add(left).growX()
            p.add(button(if (unlocked) "Aç" else tr("common.locked"), "smallBtn", unlocked) { game.goTo(LevelSelectScreen(game, a)) }).size(130f, 72f)
            list.add(p).growX().pad(6f).row()
            if (unlocked) focusRow = (a - 1).toFloat()
        }
        val sc = scroll(list)
        root.add(sc).grow().pad(8f).row()
        sc.layout()
        sc.scrollY = (focusRow * 150f - 300f).coerceAtLeast(0f)
        sc.updateVisualScroll()
    }
}

class LevelSelectScreen(game: KayipAdalarGame, private val area: Int) : BaseScreen(game) {
    override fun build() {
        val spec = AreaCatalog.area(area)
        header("Alan $area")
        root.add(label(spec.name, "title", Palette.primaryDown)).padBottom(6f).row()
        val list = Table()
        val levels = core.levels.area(area).levels
        for (lv in levels) {
            val rec = core.record(lv.id)
            val unlocked = lv.id <= core.save.highestUnlocked
            val p = panel(alt = !unlocked)
            val t = Table()
            t.add(label("${lv.id}. ${lv.name}", "default", if (unlocked) Palette.text else Palette.textMuted, wrap = true)).growX().left().row()
            val stars = rec?.stars ?: 0
            val tag = if (lv.boss != null) " · Patron: ${lv.boss!!.name}" else if (lv.units.isNotEmpty()) " · Savaş" else ""
            t.add(label("★".repeat(stars) + "☆".repeat(3 - stars) + tag, "small", Palette.gold)).left().row()
            if (rec != null && rec.bestMoves > 0) t.add(label("En iyi: ${rec.bestMoves} hamle · Mücadele madalyası: ${rec.challenges.count { !it.startsWith("ERA") }}", "muted")).left()
            p.add(t).growX()
            p.add(button(if (unlocked) tr("menu.play") else tr("common.locked"), "accent", unlocked) { details(lv.id) }).size(150f, 80f)
            list.add(p).growX().pad(6f).row()
        }
        root.add(scroll(list)).grow().pad(8f).row()
    }

    private fun details(id: Int) {
        val lv = core.levels.level(id)
        val extra = Table()
        extra.add(label(tr("play.objectives"), "small", Palette.primaryDown)).left().row()
        for (o in lv.objectives) {
            val txt = when (o.type) {
                ObjectiveType.GATHER -> tr("obj.GATHER", o.amount, tr("res.${o.res}"))
                ObjectiveType.PROTECT_CASTLE -> tr("obj.PROTECT_CASTLE", o.amount)
                ObjectiveType.SURVIVE -> tr("obj.SURVIVE", o.amount)
                ObjectiveType.TRADE_ROUTE -> tr("obj.TRADE_ROUTE", o.amount)
                ObjectiveType.MAKE_COMBO -> tr("obj.MAKE_COMBO", tr("combo.${o.combo}"))
                ObjectiveType.DEFEAT_BOSS -> tr("obj.DEFEAT_BOSS", lv.boss?.name ?: "")
                else -> tr("obj.${o.type}")
            }
            extra.add(label("• $txt", "small", wrap = true)).growX().left().row()
        }
        val hpMode = lv.objectives.any { it.type == ObjectiveType.SURVIVE || it.type == ObjectiveType.PROTECT_CASTLE }
        val starText = if (hpMode) "★★★: kale canı ≥ %${lv.star3} · ★★: ≥ %${lv.star2}" else "★★★: ≤ ${lv.star3} hamle · ★★: ≤ ${lv.star2} hamle · sınır ${lv.moveLimit}"
        extra.add(label(starText, "muted", wrap = true)).growX().padTop(8f).row()
        extra.add(label("Ödül: ~${core.levelRewardPreview(id)} altın" + if (core.isCompleted(id)) " (tekrar: %25)" else "", "small", Palette.accentDown)).padTop(4f).row()
        val challenges = core.availableChallenges(id)
        if (challenges.isNotEmpty()) {
            extra.add(label("Özel mücadeleler", "small", Palette.primaryDown)).padTop(10f).left().row()
            val rec = core.record(id)
            for (c in challenges) {
                val medal = rec?.challenges?.contains(c.name) == true
                extra.add(button("${if (medal) "✓ " else ""}${c.title}: ${c.desc}", "smallGhost") { startChallenge(id, c) }).growX().height(66f).pad(3f).row()
            }
        }
        val block = core.playBlock(id)
        val playText = when (block) { PlayBlock.NO_LIVES -> "Can yok"; else -> tr("menu.play") }
        showDialog("${lv.id}. ${lv.name}", if (block == PlayBlock.NO_LIVES) tr("lives.none") else "", playText to {
            core.startLevel(id)?.let { game.goTo(GameplayScreen(game, it)) } ?: error("lives.none")
        }, tr("common.close") to {}, extra = extra)
    }

    private fun startChallenge(id: Int, c: Challenge) {
        closeOverlay()
        core.startLevel(id, c)?.let { game.goTo(GameplayScreen(game, it)) }
    }
}
