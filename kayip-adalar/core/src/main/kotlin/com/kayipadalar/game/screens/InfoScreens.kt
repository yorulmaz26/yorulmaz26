package com.kayipadalar.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.kayipadalar.game.KayipAdalarGame
import com.kayipadalar.game.render.IslandArt
import com.kayipadalar.game.ui.Palette
import com.kayipadalar.logic.engine.Combos
import com.kayipadalar.logic.meta.Stat
import com.kayipadalar.logic.model.IslandType
import com.kayipadalar.logic.save.QuestProgress
import com.kayipadalar.logic.save.SaveSystem
import com.kayipadalar.logic.tr

class QuestsScreen(game: KayipAdalarGame) : BaseScreen(game) {
    override fun build() {
        header(tr("menu.quests"))
        val list = Table()
        list.add(label(tr("quests.daily"), "title")).left().pad(6f).row()
        for (q in core.save.quests.daily) list.add(questPanel(q, false)).growX().pad(4f).row()
        list.add(label(tr("quests.weekly"), "title")).left().pad(14f, 6f, 6f, 6f).row()
        for (q in core.save.quests.weekly) list.add(questPanel(q, true)).growX().pad(4f).row()
        list.add(label("Günlük görevler her gün, haftalık görevler her pazartesi yenilenir. Görev Merkezi ödülleri artırır.", "muted", wrap = true, align = Align.center)).growX().pad(12f)
        root.add(scroll(list)).grow().pad(6f)
    }

    private fun questPanel(q: QuestProgress, weekly: Boolean): Table {
        val t = core.quests.template(q)
        val p = panel()
        val info = Table()
        info.add(label(t.desc.replace("{n}", q.target.toString()), "default", wrap = true)).growX().left().row()
        val bar = ProgressBar(0f, q.target.toFloat(), 1f, false, skin)
        bar.value = q.progress.toFloat()
        info.add(bar).growX().padTop(4f).row()
        info.add(label("${q.progress}/${q.target} · Ödül: ${costText(core.quests.reward(q, weekly))}", "small", Palette.textMuted, wrap = true)).growX().left()
        p.add(info).growX()
        p.add(button(if (q.claimed) tr("common.claimed") else tr("common.claim"), "smallSuccess", !q.claimed && q.progress >= q.target) {
            val e = core.claimQuest(q, weekly)
            if (e != null) error(e) else { game.audio.play("coin"); rebuild() }
        }).size(140f, 76f)
        return p
    }
}

class AchievementsScreen(game: KayipAdalarGame) : BaseScreen(game) {
    override fun build() {
        header(tr("menu.achievements"))
        val all = core.achievements.all
        val unlocked = all.count { core.achievements.unlocked(it) }
        root.add(label("$unlocked / ${all.size} başarım açıldı", "small", Palette.primaryDown)).padBottom(4f).row()
        val list = Table()
        val sorted = all.sortedWith(compareBy({ it.id in core.save.achievementsClaimed }, { !core.achievements.unlocked(it) }))
        for (a in sorted) {
            val p = panel(alt = !core.achievements.unlocked(a))
            val info = Table()
            info.add(label(a.name, "default")).left().row()
            info.add(label(a.desc, "muted", wrap = true)).growX().left().row()
            val v = core.achievements.value(a.stat).coerceAtMost(a.target)
            info.add(label("$v / ${a.target} · Ödül: ${a.gold} altın" + if (a.stardust > 0) " + ${a.stardust} yıldız tozu" else "", "small", Palette.textMuted, wrap = true)).growX().left()
            p.add(info).growX()
            val claimed = a.id in core.save.achievementsClaimed
            p.add(button(if (claimed) "✓" else tr("common.claim"), "smallSuccess", !claimed && core.achievements.unlocked(a)) {
                val e = core.claimAchievement(a.id)
                if (e != null) error(e) else { game.audio.play("coin"); rebuild() }
            }).size(130f, 72f)
            list.add(p).growX().pad(4f).row()
        }
        root.add(scroll(list)).grow().pad(6f)
    }
}

class CollectionScreen(game: KayipAdalarGame) : BaseScreen(game) {
    override fun build() {
        header(tr("menu.collection"))
        root.add(label("${core.save.collection.size} / ${core.meta.collectionItems.size} öğe · Seti tamamla, kalıcı bonus kazan!", "small", Palette.primaryDown, wrap = true, align = Align.center)).growX().row()
        val list = Table()
        for (s in core.meta.collection) {
            val p = panel()
            val complete = core.collection.setComplete(s.id)
            p.add(label("${s.name} (${core.collection.ownedInSet(s.id)}/10)" + if (complete) " ✓" else "", "default", if (complete) Palette.success else Palette.text)).left().colspan(5).row()
            p.add(label("Set bonusu: ${bonusText(s.bonus.kind, s.bonus.value)}", "muted", wrap = true)).growX().left().colspan(5).row()
            for (i in 0 until 10) {
                val id = "${s.id}#$i"
                val has = id in core.save.collection
                val cell = Table()
                cell.background = theme.tinted("roundSmall", if (has) Color.valueOf("FFF3C4") else Color.valueOf("E6EBF2"))
                cell.add(label(if (has) "★" else "?", "default", if (has) Palette.gold else Palette.textMuted)).row()
                cell.add(label(if (has) core.collection.name(id).substringAfterLast(' ') else "", "small"))
                p.add(cell).size(104f, 80f).pad(4f)
                if (i == 4) p.row()
            }
            list.add(p).growX().pad(5f).row()
        }
        list.add(label("Öğeler patronlardan, alan finallerinden, 3 yıldızlı zaferlerden, seferlerden ve marketten bulunur.", "muted", wrap = true, align = Align.center)).growX().pad(12f)
        root.add(scroll(list)).grow().pad(6f)
    }
}

fun bonusText(kind: String, v: Double): String = when (kind) {
    "GOLD_REWARD_PCT" -> "Bölüm altını +%${v.toInt()}"
    "CRYSTAL_ENERGY" -> "Kristal enerjisi +${v.toInt()}"
    "CASTLE_HP" -> "Kale canı +${v.toInt()}"
    "PRODUCTION_PCT" -> "Bina üretimi +%${v.toInt()}"
    "START_ENERGY" -> "Başlangıç enerjisi +${v.toInt()}"
    "UNIT_ATK" -> "Birlik saldırısı +${v.toInt()}"
    "STORAGE_HOURS" -> "Depo süresi +${v.toInt()} saat"
    "COMBO_DAMAGE" -> "Kombo hasarı +${v.toInt()}"
    else -> kind
}

class EncyclopediaScreen(game: KayipAdalarGame) : BaseScreen(game) {
    private var tab = 0
    override fun build() {
        header(tr("menu.encyclopedia"))
        val tabs = Table()
        tabs.add(button("Ada Türleri", if (tab == 0) "smallBtn" else "smallGhost") { tab = 0; rebuild() }).growX().height(66f).pad(3f)
        tabs.add(button("Kombolar", if (tab == 1) "smallBtn" else "smallGhost") { tab = 1; rebuild() }).growX().height(66f).pad(3f)
        root.add(tabs).growX().padLeft(8f).padRight(8f).row()
        val list = Table()
        if (tab == 0) {
            for (t in IslandType.entries) {
                val known = core.discoveredType(t) || t == IslandType.CASTLE
                val p = panel(alt = !known)
                p.add(com.badlogic.gdx.scenes.scene2d.ui.Image(skin.getRegion("circle")).also { it.color = if (known) IslandArt.topColors[t] else Palette.disabled }).size(48f).padRight(10f)
                val info = Table()
                info.add(label(if (known) tr("island.$t") else "??? (henüz keşfedilmedi)", "default")).left().row()
                if (known) info.add(label(tr("islandDesc.$t"), "muted", wrap = true)).growX().left()
                p.add(info).growX()
                list.add(p).growX().pad(4f).row()
            }
        } else {
            val found = core.save.discoveredCombos
            list.add(label("${found.size} / ${core.rules.combos.size + 2} kombo keşfedildi", "small", Palette.primaryDown)).pad(6f).row()
            for (c in core.rules.combos) {
                val known = c.id in found
                val p = panel(alt = !known)
                val info = Table()
                info.add(label(if (known) tr("combo.${c.id}") else "??? Kombosu", "default")).left().row()
                val pair = "${tr("island.${c.a}")} + ${tr("island.${c.b}")}"
                info.add(label(if (known) pair + " → " + c.effects.joinToString(", ") { effectText(it.kind, it.value, it.res) } else "İpucu: ${tr("island.${c.a}")} ile bir şeyi bağla...", "muted", wrap = true)).growX().left()
                p.add(info).growX()
                list.add(p).growX().pad(4f).row()
            }
            for (id in listOf(Combos.RESONANCE, Combos.CHAIN_REACTION)) {
                val known = id in found
                val p = panel(alt = !known)
                p.add(label((if (known) tr("combo.$id") else "???") + "\n" + tr("comboDesc.$id"), "small", wrap = true)).growX().left()
                list.add(p).growX().pad(4f).row()
            }
        }
        root.add(scroll(list)).grow().pad(6f)
    }

    private fun effectText(kind: String, v: Int, res: String?): String = when (kind) {
        "ADD_MOVES" -> "+$v hamle"; "ADD_ENERGY" -> "+$v enerji"; "ADD_RES" -> "+$v ${tr("res.$res")}"
        "DAMAGE_ENEMIES" -> "düşmanlara $v hasar"; "STUN_ENEMIES" -> "düşmanları $v tur sersemletir"; "BOSS_DAMAGE" -> "patrona $v hasar"
        "HEAL_UNITS" -> "birliklere +$v can"; "CASTLE_HEAL" -> "kaleye +$v can"; "TRANSFORM" -> "volkanı madene çevirir"
        "THAW" -> "çevredeki buzları eritir"; "REVEAL" -> "gizli adaları açar"; else -> kind
    }
}

class ProfileScreen(game: KayipAdalarGame) : BaseScreen(game) {
    override fun build() {
        header(tr("menu.profile"))
        val s = core.save
        val list = Table()
        val frame = core.shop.equippedColor("FRAME")?.takeIf { it.isNotEmpty() }?.let { Color.valueOf(it) } ?: Palette.panelBorder
        val card = Table()
        card.background = theme.tinted("round", frame)
        card.pad(8f)
        val inner = panel()
        val nameField = TextField(s.playerName, TextField.TextFieldStyle(theme.body, Palette.text, theme.tinted("white", Palette.primary), theme.tinted("white", Color(0.3f, 0.5f, 1f, 0.3f)), theme.panelAlt))
        nameField.maxLength = 18
        nameField.setTextFieldListener { f, _ -> s.playerName = f.text.ifBlank { "Ada Muhafızı" } }
        inner.add(nameField).growX().height(70f).row()
        val rep = core.reputation
        inner.add(label("${rep.title()} · İtibar ${s.reputationXp}" + (rep.nextXp()?.let { " / $it" } ?: ""), "small", Palette.accentDown)).left().padTop(6f).row()
        if (s.era > 0) inner.add(label(tr("era.current", s.era + 1), "small", Palette.primaryDown)).left().row()
        card.add(inner).growX()
        list.add(card).growX().pad(6f).row()
        val st = panel()
        val rows = listOf(
            "Tamamlanan bölüm" to (s.stats[Stat.LEVELS_COMPLETED] ?: 0), "Toplam yıldız" to core.totalStars().toLong(),
            "3 yıldızlı zafer" to (s.stats[Stat.PERFECT_WINS] ?: 0), "Kurulan köprü" to (s.stats[Stat.BRIDGES] ?: 0),
            "Oluşturulan kombo" to (s.stats[Stat.COMBOS] ?: 0), "Keşfedilen kombo" to s.discoveredCombos.size.toLong(),
            "Ada hareketi" to (s.stats[Stat.ISLANDS_MOVED] ?: 0), "Yenilen düşman" to (s.stats[Stat.ENEMIES] ?: 0),
            "Yenilen patron" to (s.stats[Stat.BOSSES] ?: 0), "Koleksiyon" to s.collection.size.toLong(),
            "Sonsuz mod rekoru" to s.endless.best.toLong(), "Günlük bulmaca" to (s.stats[Stat.DAILY_PUZZLES] ?: 0),
            "Krallık savunması" to s.defense.wins.toLong(), "Kazanılan altın" to (s.stats[Stat.GOLD_EARNED] ?: 0),
        )
        for ((k, v) in rows) { st.add(label(k, "small")).left().expandX(); st.add(label(v.toString(), "small", Palette.primaryDown)).right().row() }
        list.add(st).growX().pad(6f).row()
        root.add(scroll(list)).grow().pad(6f)
    }

    override fun onBack() { core.persist(); super.onBack() }
}

class SettingsScreen(game: KayipAdalarGame) : BaseScreen(game) {
    override fun build() {
        header(tr("menu.settings"))
        val st = core.save.settings
        val p = panel()
        fun toggle(text: String, value: Boolean, set: (Boolean) -> Unit) {
            val cb = CheckBox("  $text", skin)
            cb.isChecked = value
            cb.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) { set(cb.isChecked); core.persist() } })
            p.add(cb).left().pad(10f).row()
        }
        toggle(tr("settings.sound"), st.sound) { st.sound = it }
        toggle(tr("settings.music"), st.music) { st.music = it; game.audio.updateMusic() }
        toggle(tr("settings.vibration"), st.vibration) { st.vibration = it }
        toggle(tr("settings.fast"), st.animationsFast) { st.animationsFast = it }
        toggle(tr("settings.largeText"), st.largeText) { st.largeText = it; game.rebuildTheme() }
        p.add(button(tr("settings.resetTutorial"), "ghost") { st.tutorialsSeen.clear(); core.persist(); toast("Öğreticiler yeniden gösterilecek.") }).growX().height(80f).pad(10f).row()
        p.add(label(tr("settings.language"), "small")).left().pad(10f).row()
        p.add(label(tr("settings.saveInfo", SaveSystem.CURRENT_VERSION), "muted")).left().pad(10f).row()
        if (core.save.clock.suspicious > 0) p.add(label("Cihaz saatinde değişiklik algılandı; zamanlayıcılar güvenilir saatle çalışıyor.", "muted", wrap = true)).growX().pad(10f).row()
        root.add(scroll(p)).grow().pad(10f)
    }
}
