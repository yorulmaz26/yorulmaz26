package com.emirzinciri.robotkomutan.screen

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.emirzinciri.robotkomutan.EmirZinciriGame
import com.emirzinciri.robotkomutan.logic.meta.Meta
import com.emirzinciri.robotkomutan.logic.meta.QuestState
import com.emirzinciri.robotkomutan.ui.Theme
import java.time.LocalDateTime
import java.time.LocalTime

/** Günlük/haftalık görevler ve başarımlar. Ödüller elle toplanır. */
class QuestsScreen(game: EmirZinciriGame, private var tab: Int = 0) : BaseScreen(game) {
    private val meta get() = services.progression.meta
    private val content = Table()
    private val tabs = Table()

    init {
        meta.refresh(DateKeys.day(), DateKeys.week())
        root.top()
        val head = Table()
        head.add(ui.iconButton("back", size = 72f) { onBack() }).size(72f)
        head.add(ui.label("Görevler", assets.fontTitle, Theme.ink)).padLeft(16f).left().growX()
        head.add(ui.chip("coin", "${services.save.wallet.gold}", Theme.orange))
        root.add(head).growX().pad(16f).row()
        root.add(tabs).growX().pad(0f, 12f, 6f, 12f).row()
        val scroll = ScrollPane(content, assets.scrollStyle)
        scroll.setScrollingDisabled(true, false)
        root.add(scroll).grow()
        rebuild()
    }

    private fun rebuild() {
        tabs.clear()
        listOf("Günlük", "Haftalık", "Başarımlar").forEachIndexed { i, t ->
            tabs.add(ui.button(t, if (tab == i) Theme.turquoise else Theme.white, if (tab == i) Theme.white else Theme.ink, null, assets.fontBold, 18) {
                tab = i; rebuild()
            }).growX().uniformX().pad(4f)
        }
        content.clear()
        content.top()
        when (tab) {
            0 -> { timer("Yeni günlük görevler gece yarısı gelir."); meta.state.daily.forEach { quest(it) } }
            1 -> { timer("Haftalık görevler her pazartesi yenilenir."); meta.state.weekly.forEach { quest(it) } }
            else -> achievements()
        }
        content.add().height(30f)
    }

    private fun timer(text: String) {
        val now = LocalDateTime.now()
        val mins = java.time.Duration.between(now.toLocalTime(), LocalTime.MAX).toMinutes()
        val extra = if (tab == 0) " (kalan ${mins / 60} sa ${mins % 60} dk)" else ""
        content.add(ui.label(text + extra, assets.fontSmall, Theme.inkSoft, wrap = true)).growX().pad(6f, 22f, 6f, 22f).row()
    }

    private fun quest(q: QuestState) {
        val card = ui.panel(if (q.claimed) Theme.panelAlt else Theme.white, 24, if (q.claimed) 0f else 4f)
        card.pad(16f, 18f, 16f, 18f)
        val info = Table()
        info.add(ui.label(meta.questText(q), assets.fontBold, if (q.claimed) Theme.inkFaint else Theme.ink)).left().row()
        val p = meta.progress(q)
        val bar = Table(); bar.background = assets.panel(Theme.panelAlt, 8, 0f)
        val fill = Table(); fill.background = assets.panel(if (meta.isDone(q)) Theme.green else Theme.turquoise, 8, 0f)
        bar.add(fill).width((300f * p / q.target).coerceAtLeast(1f)).height(14f).left().expandX()
        info.add(bar).width(300f).height(14f).left().padTop(6f).row()
        info.add(ui.label("$p / ${q.target} · ödül ${q.gold} altın" + if (q.chips > 0) " + ${q.chips} çip" else "", assets.fontSmall, Theme.inkSoft)).left().padTop(4f)
        card.add(info).growX().left()
        when {
            q.claimed -> card.add(ui.image("check", Theme.green)).size(36f)
            meta.isDone(q) -> card.add(ui.button("Topla", Theme.green, Theme.white, null, assets.fontBold, 18) {
                if (meta.claim(q)) { assets.play("star"); services.persist() }; rebuild()
            }.also { it.pad(8f, 16f, 8f, 16f) })
        }
        content.add(card).growX().pad(6f, 14f, 6f, 14f).row()
    }

    private fun achievements() {
        val all = Meta.achievements
        val done = all.count { meta.isUnlocked(it) }
        content.add(ui.label("$done / ${all.size} başarım açıldı", assets.fontBold, Theme.ink)).left().pad(6f, 22f, 6f, 22f).row()
        // Toplanabilir olanlar önce, sonra açılmamışlar (her ölçüt için sıradaki), en son toplanmışlar
        val sorted = all.sortedWith(compareBy({ if (meta.isUnlocked(it) && !meta.isClaimed(it)) 0 else if (!meta.isUnlocked(it)) 1 else 2 }, { it.metric.ordinal }, { it.threshold }))
        for (a in sorted) {
            val unlocked = meta.isUnlocked(a); val claimed = meta.isClaimed(a)
            val card = ui.panel(if (claimed) Theme.panelAlt else Theme.white, 22, if (claimed) 0f else 3f)
            card.pad(12f, 16f, 12f, 16f)
            card.add(ui.image("star", if (unlocked) Theme.yellow else Theme.line)).size(34f).padRight(12f)
            val info = Table()
            info.add(ui.label(a.title, assets.fontBold, if (unlocked) Theme.ink else Theme.inkFaint)).left().row()
            val v = meta.value(a.metric).coerceAtMost(a.threshold)
            info.add(ui.label("${a.metric.title}: $v / ${a.threshold}", assets.fontSmall, Theme.inkSoft)).left()
            card.add(info).growX().left()
            when {
                claimed -> card.add(ui.image("check", Theme.green)).size(30f)
                unlocked -> card.add(ui.button("+${a.gold}", Theme.green, Theme.white, "coin", assets.fontSmall, 18) {
                    if (meta.claim(a)) { assets.play("star"); services.persist() }; rebuild()
                }.also { it.pad(6f, 12f, 6f, 12f) })
                else -> card.add(ui.label("${a.gold} altın", assets.fontSmall, Theme.inkFaint))
            }
            content.add(card).growX().pad(4f, 14f, 4f, 14f).row()
        }
    }

    override fun onBack() { services.persist(); game.go(MenuScreen(game)) }
}
