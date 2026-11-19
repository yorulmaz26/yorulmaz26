package com.kayipadalar.game.screens

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.kayipadalar.game.KayipAdalarGame
import com.kayipadalar.game.ui.Palette
import com.kayipadalar.logic.model.UnitType
import com.kayipadalar.logic.tr

/** Bölüm üretimi ağır olabildiği için bir sonraki karede başlatılır ve bekleme mesajı gösterilir. */
private fun BaseScreen.startAsync(make: () -> com.kayipadalar.logic.PlaySession?) {
    showDialog("Takımadalar hazırlanıyor…", "Adalar yerine yerleşiyor, lütfen bekle.", dismissible = false)
    Thread {
        val s = runCatching { make() }.getOrNull()
        com.badlogic.gdx.Gdx.app.postRunnable {
            closeOverlay()
            if (s != null) game.goTo(GameplayScreen(game, s)) else error("error.invalid")
        }
    }.apply { isDaemon = true }.start()
}

class EndlessScreen(game: KayipAdalarGame) : BaseScreen(game) {
    override fun build() {
        header(tr("menu.endless"))
        val e = core.save.endless
        val p = panel()
        p.add(label("Prosedürel olarak üretilen ve her aşamada büyüyen takımadalar. Tehlikeler, düşman davranışları ve kombolar aşama ilerledikçe zorlaşır. Can gerekmez.", "small", wrap = true)).width(600f).row()
        p.add(label(tr("endless.best", e.best), "title", Palette.accentDown)).pad(14f).row()
        if (e.active) p.add(label(tr("endless.current", e.current), "default")).row()
        if (e.active) p.add(button(tr("endless.continue"), "accent") { startAsync { core.startEndless(false) } }).growX().height(96f).pad(8f).row()
        p.add(button(tr("endless.newRun"), if (e.active) "ghost" else "accent") { startAsync { core.startEndless(true) } }).growX().height(96f).pad(8f).row()
        p.add(label("Her aşama ${core.meta.economy.endlessGoldPerStage} × aşama altın verir; her 10 aşamada bir koleksiyon öğesi.", "muted", wrap = true)).width(600f)
        root.add(p).pad(12f).expand().top()
    }
}

class DailyPuzzleScreen(game: KayipAdalarGame) : BaseScreen(game) {
    override fun build() {
        header(tr("menu.daily"))
        val key = core.dailyKey()
        val best = core.save.dailyPuzzleBest[key]
        val p = panel()
        p.add(label("Bugünün bulmacası herkes için aynı kurallara sahiptir. Amaç: en az hamleyle çözmek. Can gerekmez, istediğin kadar dene.", "small", wrap = true)).width(600f).row()
        p.add(label(if (best != null) tr("daily.best", best) else tr("daily.none"), "title", Palette.accentDown)).pad(14f).row()
        p.add(button(tr("common.start"), "accent") { startAsync { core.startDaily() } }).growX().height(96f).pad(8f).row()
        p.add(label("İlk çözüm ödülü: ${core.meta.economy.dailyPuzzleGold} altın + 3 kristal", "muted")).row()
        val hist = core.save.dailyPuzzleBest.entries.sortedByDescending { it.key }.take(7)
        if (hist.isNotEmpty()) {
            p.add(label("Son rekorların", "default")).padTop(14f).row()
            for ((k, v) in hist) p.add(label("${if (k == key) "Bugün" else "${key - k} gün önce"}: $v hamle", "small")).row()
        }
        root.add(p).pad(12f).expand().top()
    }
}

class ExpeditionsScreen(game: KayipAdalarGame) : BaseScreen(game) {
    private var tick = 0f
    override fun build() {
        header(tr("menu.expeditions"))
        val now = core.now()
        val ex = core.expeditions
        root.add(label(tr("expedition.slots", core.save.expeditions.size, ex.slots()), "small", Palette.primaryDown)).row()
        val list = Table()
        for (run in core.save.expeditions.toList()) {
            val def = core.meta.expeditions.first { it.id == run.id }
            val p = panel()
            val left = run.endsAt - now
            val info = Table()
            info.add(label(def.name, "default")).left().row()
            val bar = ProgressBar(0f, 1f, 0.001f, false, skin)
            bar.value = (1f - left.toFloat() / (run.endsAt - run.startedAt).coerceAtLeast(1)).coerceIn(0f, 1f)
            info.add(bar).growX().row()
            info.add(label(if (left <= 0) tr("expedition.ready") else tr("expedition.running", fmtDuration(left)), "small", if (left <= 0) Palette.success else Palette.textMuted)).left()
            p.add(info).growX()
            p.add(button(tr("common.claim"), "smallSuccess", left <= 0) {
                val (e, loot) = core.claimExpedition(run)
                if (e != null) error(e) else {
                    game.audio.play("coin")
                    val txt = loot!!.res.entries.joinToString("  ") { "+${it.value} ${resName(it.key)}" } + (loot.item?.let { "\nKoleksiyon: ${core.collection.name(it)}" } ?: "")
                    showDialog("Sefer döndü!", txt)
                    rebuild()
                }
            }).size(140f, 76f)
            list.add(p).growX().pad(4f).row()
        }
        list.add(label("Yeni sefer gönder", "title")).left().pad(10f).row()
        for (def in core.meta.expeditions) {
            val p = panel()
            val info = Table()
            info.add(label("${def.name} · ${fmtDuration(ex.durationMs(def))}", "default", wrap = true)).growX().left().row()
            info.add(label("Bedel: ${costText(def.cost)} · Ödül: ${costText(def.rewards)}" + if (def.collectionChancePct > 0) " · koleksiyon %${def.collectionChancePct}" else "", "muted", wrap = true)).growX().left()
            p.add(info).growX()
            val running = core.save.expeditions.any { it.id == def.id }
            val ok = ex.available(def) && !running && core.save.expeditions.size < ex.slots() && core.wallet.canAfford(def.cost)
            p.add(button(if (running) "Yolda" else if (!ex.available(def)) tr("common.locked") else "Gönder", "smallBtn", ok) {
                val e = core.startExpedition(def.id)
                if (e != null) error(e) else rebuild()
            }).size(140f, 76f)
            list.add(p).growX().pad(4f).row()
        }
        list.add(label("Seferler gerçek zamanda ilerler; uygulama kapalıyken de devam eder. Keşif Kulesi yuva ekler ve süreyi kısaltır.", "muted", wrap = true, align = Align.center)).growX().pad(12f)
        root.add(scroll(list)).grow().pad(6f)
    }

    override fun update(dt: Float) {
        tick += dt
        if (tick > 1f && !hasOverlay) { tick = 0f; if (core.save.expeditions.isNotEmpty()) rebuild() }
    }
}

class DefenseScreen(game: KayipAdalarGame) : BaseScreen(game) {
    override fun build() {
        header(tr("menu.defense"))
        val p = panel()
        p.add(label("Krallığına belirli aralıklarla yapay zekâ ordusu saldırır. Garnizonunu seç, köprüleri yöneterek kaleyi koru. Kaybedersen ceza yok; tekrar deneyebilirsin.", "small", wrap = true)).width(600f).row()
        p.add(label("Kale canı: ${10 + core.bonuses.int("CASTLE_HP")} (Savunma Kulesi ve araştırmalarla artar) · Kazanılan savunma: ${core.save.defense.wins}", "muted", wrap = true)).width(600f).padTop(6f).row()
        p.add(label(tr("defense.garrison"), "default")).padTop(12f).left().row()
        val chosen = core.garrison().toMutableList()
        val grid = Table()
        for ((i, t) in UnitType.entries.filter { it != UnitType.BOSS }.withIndex()) {
            val unlocked = core.units.unlocked(t)
            val cb = CheckBox("  ${tr("unit.$t")} · Sv ${core.units.level(t)}" + if (!unlocked) " (kilitli)" else "", skin, "small")
            cb.isChecked = t in chosen
            cb.isDisabled = !unlocked
            cb.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                    if (cb.isChecked) { if (chosen.size >= 4) { cb.isChecked = false; toast("En fazla 4 birlik."); return }; chosen.add(t) } else chosen.remove(t)
                    core.setGarrison(chosen); core.persist()
                }
            })
            grid.add(cb).left().pad(5f).row()
        }
        p.add(grid).growX().row()
        val ready = core.defenseReady()
        p.add(label(if (ready) tr("defense.ready") else tr("defense.wait", fmtDuration(core.msToDefense())), "default", if (ready) Palette.danger else Palette.textMuted)).pad(12f).row()
        p.add(button("Savunmaya başla", "danger", ready) { startAsync { core.startDefense() } }).growX().height(96f).pad(6f)
        root.add(scroll(p)).pad(12f).grow()
    }
}

class EraScreen(game: KayipAdalarGame) : BaseScreen(game) {
    override fun build() {
        header(tr("menu.era"))
        val p = panel()
        if (!core.eraUnlocked) {
            p.add(label(tr("era.locked"), "default", wrap = true, align = Align.center)).width(600f)
            root.add(p).pad(12f).expand().top()
            return
        }
        p.add(label(tr("era.current", core.save.era + 1), "title", Palette.accentDown)).row()
        p.add(label("Her çağda 10 özel mücadele seni bekliyor. Hepsini tamamlayınca yeni çağa geçerek kalıcı +%${core.meta.economy.eraGoldPct} altın bonusu ve 2 Kraliyet Mührü kazanırsın. Ana ilerlemen silinmez.", "small", wrap = true)).width(600f).pad(8f).row()
        for ((id, c) in core.eraChallenges()) {
            val done = core.eraDone(id, c)
            p.add(button("${if (done) "✓ " else ""}Bölüm $id · ${c.title}", if (done) "smallGhost" else "smallBtn", !done) {
                core.startEra(id, c)?.let { game.goTo(GameplayScreen(game, it)) }
            }).growX().height(70f).pad(3f).row()
        }
        p.add(button(tr("era.advance"), "accent", core.canAdvanceEra()) { if (core.advanceEra()) { core.persist(); toast("Yeni çağ başladı!"); rebuild() } }).growX().height(90f).padTop(10f)
        root.add(scroll(p)).pad(12f).grow()
    }
}
