package com.zamaninefendisi.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.zamaninefendisi.game.ZamaninEfendisiGame
import com.zamaninefendisi.game.ui.Theme
import com.zamaninefendisi.game.ui.formatTime
import com.zamaninefendisi.logic.Era
import com.zamaninefendisi.logic.continuity.Achievements
import com.zamaninefendisi.logic.continuity.CivCollection
import com.zamaninefendisi.logic.continuity.ExpeditionKind
import com.zamaninefendisi.logic.continuity.Museum
import com.zamaninefendisi.logic.levels.LevelCatalog
import com.zamaninefendisi.logic.meta.BuildingType
import com.zamaninefendisi.logic.meta.describe

private fun BaseScreen.card(): Table = game.ui.panel(Theme.panel).apply { pad(18f, 22f, 18f, 22f) }

/** Mücadeleler merkezi: sonsuz mod, günlük bulmaca, haftalık kriz, krallık savunması, alternatif tarih ve döngü. */
class ChallengesScreen(game: ZamaninEfendisiGame) : BaseScreen(game) {
    init {
        val cm = game.progression.continuity
        cm.tick()
        val st = game.progression.data.continuity
        root.top().pad(24f)
        root.add(header("Mücadeleler") { onBack() }).growX().padBottom(10f).row()
        root.add(resourceBar()).padBottom(10f).row()
        val list = Table().top()
        fun entry(title: String, desc: String, status: String, color: Color, button: String, enabled: Boolean = true, onClick: () -> Unit) {
            val c = card()
            c.add(ui.label(title, assets.fontM, Theme.ink)).left().row()
            c.add(ui.label(desc, assets.fontS, Theme.inkSoft, wrap = true)).width(610f).left().row()
            c.add(ui.label(status, assets.fontS, color, wrap = true)).width(610f).left().padTop(4f).row()
            val b = ui.button(button, color, assets.fontS) { onClick() }
            b.isDisabled = !enabled
            c.add(b).width(300f).height(70f).padTop(8f).right()
            list.add(c).width(660f).padBottom(10f).row()
        }
        val run = st.endlessRun
        entry("Sonsuz Zaman Modu", "Prosedürel üretilen, çözülebilirliği doğrulanmış haritalarda olabildiğince ilerle. Her aşamada çağ düzeni, kaynaklar, düşmanlar ve zaman anomalileri değişir.",
            "En uzun seri: ${st.endlessBest} aşama" + (run?.let { " · Süren seri: aşama ${it.stage}" } ?: ""), Theme.purple, if (run != null) "Devam Et · Aşama ${run.stage}" else "Yeni Seri") {
            val r = run ?: cm.startEndless()
            game.go(GameScreen(game, cm.endlessLevel(r), emptyList(), mode = PlayMode.Endless))
        }
        val day = cm.today()
        entry("Günlük Zaman Bulmacası", "Her gün yeni bir bulmaca. Olabildiğince az eylemle çöz.",
            if (day in st.dailyClaimed) "Bugün çözüldü · en iyi: ${st.dailyBest[day]} eylem" else "Bugün henüz çözülmedi · ödül: 80 altın, 1 kristal, 1 eser", Theme.orange, "Oyna") {
            game.go(GameScreen(game, cm.dailyLevel(day), emptyList(), mode = PlayMode.Daily(day)))
        }
        val week = cm.thisWeek()
        val crisis = cm.weeklyCrisis(week)
        entry("Haftalık Zaman Krizi: ${crisis.title}", crisis.rule,
            if (week in st.weeklyClaimed) "Bu haftanın krizi çözüldü" else "Ödül: 250 altın, 4 kristal, 1 Zaman Mührü", Theme.red, "Krize Gir") {
            game.go(GameScreen(game, cm.weeklyLevel(week), emptyList(), mode = PlayMode.Weekly(week)))
        }
        val wave = st.defenseWave + 1
        entry("Krallık Savunması", "Farklı çağlardan gelen dalgalara karşı kaleni koru. Savunma Kulesi seviyen fazladan odun ve kule kurma hakkı verir.",
            "Sıradaki dalga: $wave · en iyi: ${st.defenseBest}", Theme.teal, "Dalga $wave") {
            game.go(GameScreen(game, cm.defenseLevel(wave), emptyList(), mode = PlayMode.Defense(wave)))
        }
        entry("Alternatif Tarih Görevleri", "Tamamladığın bölümleri farklı koşullarla yeniden oyna: paradokssuz, az geçişle, savaş başlamadan…",
            "Tamamlanan: ${st.altDone.size}", Theme.blue, "Görevleri Gör") { game.go(AltHistoryScreen(game)) }
        val all = cm.prestigeAvailable()
        entry("Yeni Zaman Döngüsü", "Bütün ana bölümler tamamlanınca açılır. Kayıtların korunur; aynı tarihi daha az zamanla ve kale bonusları olmadan yeniden yazarsın.",
            if (all) "Döngü ${st.prestige + 1} · ilerleme ${st.cycleDone.size}/${LevelCatalog.levels.size}" else "Kilitli: bütün ${LevelCatalog.levels.size} ana bölümü tamamla (${LevelCatalog.levels.count { game.progression.isCompleted(it.id) }} tamamlandı)",
            Theme.gold, "Döngüye Gir", enabled = all) { game.go(PrestigeScreen(game)) }
        root.add(ui.scroll(list)).grow()
    }

    override fun onBack() = game.go(MainMenuScreen(game))
}

class AltHistoryScreen(game: ZamaninEfendisiGame) : BaseScreen(game) {
    init {
        val cm = game.progression.continuity
        val st = game.progression.data.continuity
        root.top().pad(24f)
        root.add(header("Alternatif Tarih") { onBack() }).growX().padBottom(14f).row()
        val list = Table().top()
        val done = LevelCatalog.levels.filter { game.progression.isCompleted(it.id) }
        if (done.isEmpty()) list.add(ui.label("Önce ana bölümleri tamamla; tamamlanan her bölüm burada farklı bir koşulla yeniden açılır.", assets.fontM, Theme.inkSoft, wrap = true, align = Align.center)).width(600f).padTop(60f)
        for (lv in done) {
            val task = cm.altTask(lv)
            val ok = "alt_${lv.id}" in st.altDone
            val c = card()
            val left = Table()
            left.add(ui.label("${lv.id}. ${lv.name}", assets.fontM, Theme.ink)).left().row()
            left.add(ui.label("${task.title}: ${task.description}", assets.fontS, if (ok) Theme.green else Theme.inkSoft, wrap = true)).width(420f).left().row()
            c.add(left).expandX().left()
            c.add(ui.button(if (ok) "✓ Tekrar" else "Oyna", if (ok) Theme.green else Theme.blue, assets.fontS) { game.go(GameScreen(game, lv, emptyList(), mode = PlayMode.Alt)) }).size(150f, 70f)
            list.add(c).width(660f).padBottom(8f).row()
        }
        root.add(ui.scroll(list)).grow()
    }

    override fun onBack() = game.go(ChallengesScreen(game))
}

class PrestigeScreen(game: ZamaninEfendisiGame) : BaseScreen(game) {
    init {
        val cm = game.progression.continuity
        val st = game.progression.data.continuity
        root.top().pad(24f)
        root.add(header("Zaman Döngüsü ${st.prestige + 1}") { onBack() }).growX().padBottom(14f).row()
        root.add(ui.label("Bu döngüde tur sınırları daralır ve kale bonusları kapanır. Bütün bölümleri yeniden yaz: +3 Zaman Mührü, +10 kristal.", assets.fontS, Theme.purple, wrap = true, align = Align.center)).width(640f).padBottom(10f).row()
        val list = Table().top()
        for (lv in LevelCatalog.levels) {
            val ok = lv.id in st.cycleDone
            val row = Table()
            row.add(ui.label("${lv.id}. ${lv.name}", assets.fontS, if (ok) Theme.green else Theme.ink)).left().expandX()
            row.add(ui.button(if (ok) "✓" else "Oyna", if (ok) Theme.green else Theme.gold, assets.fontS) { game.go(GameScreen(game, cm.prestigeLevel(lv), emptyList(), mode = PlayMode.Prestige)) }).size(130f, 60f)
            list.add(row).width(640f).padBottom(6f).row()
        }
        root.add(ui.scroll(list)).grow()
    }

    override fun onBack() = game.go(ChallengesScreen(game))
}

/** Günlük ve haftalık görevler. */
class QuestsScreen(game: ZamaninEfendisiGame) : BaseScreen(game) {
    init {
        val cm = game.progression.continuity
        cm.ensurePeriods()
        val st = game.progression.data.continuity
        root.top().pad(24f)
        root.add(header("Görevler") { onBack() }).growX().padBottom(14f).row()
        for ((weekly, board) in listOf(false to st.dailyQuests, true to st.weeklyQuests)) {
            root.add(ui.label(if (weekly) "Haftalık Görevler" else "Günlük Görevler", assets.fontL, Theme.ink)).left().padTop(6f).row()
            for ((i, q) in board.quests.withIndex()) {
                val prog = (board.progress[q.type] ?: 0).coerceAtMost(q.target)
                val claimed = i in board.claimed
                val c = card()
                val left = Table()
                left.add(ui.label("${q.target} kez ${q.type.title}", assets.fontM, Theme.ink)).left().row()
                val bar = Table()
                val w = 380f
                bar.add(Table().apply { background = assets.panel(Theme.green, true) }).width((w * prog / q.target).coerceAtLeast(12f)).height(18f)
                bar.add(Table().apply { background = assets.panel(Theme.creamDeep, true) }).width((w * (1f - prog.toFloat() / q.target)).coerceAtLeast(0f)).height(18f)
                left.add(bar).left().padTop(4f).row()
                left.add(ui.label("$prog/${q.target} · ödül: ${q.reward.describe()}", assets.fontS, Theme.inkSoft, wrap = true)).width(420f).left().row()
                c.add(left).expandX().left()
                val b = ui.button(if (claimed) "Alındı" else "Ödülü Al", if (claimed) Theme.grey else Theme.green, assets.fontS) {
                    if (cm.claimQuest(weekly, i)) { game.audio.play("win", 0.4f); game.go(QuestsScreen(game)) }
                }
                b.isDisabled = claimed || prog < q.target
                c.add(b).size(160f, 70f)
                root.add(c).width(660f).padBottom(8f).row()
            }
        }
        root.add(ui.label("Görevler her gün ve her pazartesi yenilenir. Görev Merkezi seviyesi ödülleri artırır.", assets.fontS, Theme.muted, wrap = true, align = Align.center)).width(620f).padTop(8f)
    }

    override fun onBack() = game.go(MainMenuScreen(game))
}

/** Zaman seferleri: keşif ekipleri gerçek süre sonunda ödülle döner. */
class ExpeditionsScreen(game: ZamaninEfendisiGame) : BaseScreen(game) {
    private var acc = 0f

    init { build() }

    private fun build() {
        root.clearChildren()
        val cm = game.progression.continuity
        cm.tick()
        val st = game.progression.data.continuity
        root.top().pad(24f)
        root.add(header("Zaman Seferleri") { onBack() }).growX().padBottom(10f).row()
        root.add(ui.label("Yuva: ${st.expeditions.size}/${cm.expeditionSlots()} (Zaman Portalı seviyesi yuva sayısını artırır)", assets.fontS, Theme.purple)).padBottom(10f).row()
        for (e in st.expeditions) {
            val c = card()
            c.add(ui.label("${e.kind.title} · ${e.kind.era.title}", assets.fontM, Theme.eraColor(e.kind.era))).left().row()
            val left = (e.durationMs - e.progressMs).coerceAtLeast(0)
            c.add(ui.label(if (e.done) "Döndü! Ödül: ${e.kind.reward.describe()}" else "Kalan süre: ${formatTime(left)}", assets.fontS, Theme.ink, wrap = true)).width(610f).left().row()
            val b = ui.button("Ödülü Al", Theme.green, assets.fontS) {
                val got = cm.claimExpedition(e.id)
                if (got != null) { ui.toast(root, "Ekip döndü: ${got.first.describe()}" + (got.second?.let { " · Eser: ${it.title}" } ?: ""), Theme.green); build() }
            }
            b.isDisabled = !e.done
            c.add(b).width(240f).height(66f).padTop(6f).right()
            root.add(c).width(660f).padBottom(8f).row()
        }
        root.add(ui.label("Yeni sefer gönder", assets.fontM, Theme.ink)).padTop(10f).row()
        for (k in ExpeditionKind.entries) {
            val c = card()
            val left = Table()
            left.add(ui.label("${k.title} · ${k.era.title} · ${k.hours} saat", assets.fontS, Theme.ink)).left().row()
            left.add(ui.label("${k.reward.describe()} · eser şansı %${k.itemChance}", assets.fontS, Theme.inkSoft, wrap = true)).width(420f).left().row()
            c.add(left).expandX().left()
            c.add(ui.button("Gönder", Theme.blue, assets.fontS) { val err = cm.startExpedition(k); if (err != null) ui.toast(root, err, Theme.red) else build() }).size(150f, 66f)
            root.add(c).width(660f).padBottom(6f).row()
        }
    }

    override fun render(delta: Float) {
        super.render(delta)
        acc += delta
        if (acc > 1f) { acc = 0f; if (game.progression.data.continuity.expeditions.isNotEmpty()) build() }
    }

    override fun onBack() = game.go(MainMenuScreen(game))
}

/** Medeniyet Koleksiyonu: 150 eser, çağ ve sete göre. */
class CollectionScreen(game: ZamaninEfendisiGame, private val era: Era = Era.PAST) : BaseScreen(game) {
    init {
        val owned = game.progression.data.continuity.collection
        root.top().pad(24f)
        root.add(header("Koleksiyon") { onBack() }).growX().padBottom(10f).row()
        root.add(ui.label("${owned.size}/${CivCollection.items.size} eser · tamamlanan set: ${CivCollection.completedSets(owned).size}", assets.fontS, Theme.purple)).padBottom(8f).row()
        val tabs = Table()
        for (e in Era.entries) tabs.add(ui.button(e.title, if (e == era) Theme.eraColor(e) else Theme.grey, assets.fontS, if (e == era) Color.WHITE else Theme.ink) { game.go(CollectionScreen(game, e)) }).size(200f, 64f).pad(4f)
        tabs.add(ui.button("Müze", Theme.gold, assets.fontS) { game.go(MuseumScreen(game)) }).size(160f, 64f).pad(4f)
        root.add(tabs).padBottom(10f).row()
        val list = Table().top()
        for (set in CivCollection.setsOf(era)) {
            val items = CivCollection.items.filter { it.era == era && it.set == set }
            val have = items.count { it.id in owned }
            val c = card()
            c.add(ui.label("$set · $have/${items.size}", assets.fontM, if (have == items.size) Theme.green else Theme.ink)).left().colspan(2).row()
            for ((i, it) in items.withIndex()) {
                val o = it.id in owned
                c.add(ui.label(if (o) "• ${it.title}" else "• ?", assets.fontS, if (o) Theme.darker(Theme.eraColor(era), 0.8f) else Theme.muted)).width(300f).left()
                if (i % 2 == 1) c.row()
            }
            list.add(c).width(660f).padBottom(8f).row()
        }
        list.add(ui.label("Eserler patron bölümlerinden, günlük bulmacadan, haftalık krizden, seferlerden, alternatif tarih görevlerinden ve zaman olaylarından gelir.", assets.fontS, Theme.muted, wrap = true, align = Align.center)).width(620f)
        root.add(ui.scroll(list)).grow()
    }

    override fun onBack() = game.go(MainMenuScreen(game))
}

/** Zaman Müzesi: eser sayısı ve Tarih Müzesi seviyesi arttıkça yeni salonlar açılır. */
class MuseumScreen(game: ZamaninEfendisiGame) : BaseScreen(game) {
    private val owned = game.progression.data.continuity.collection
    private val museumLevel = game.progression.data.buildings[BuildingType.MUSEUM] ?: 0
    private val hallTables = ArrayList<Pair<Table, Int>>()

    init {
        root.top().pad(24f)
        root.add(header("Zaman Müzesi") { onBack() }).growX().padBottom(10f).row()
        root.add(ui.label("${owned.size} eser · Tarih Müzesi seviyesi $museumLevel", assets.fontS, Theme.purple)).padBottom(10f).row()
        val list = Table().top()
        for ((i, hall) in Museum.halls.withIndex()) {
            val open = Museum.hallOpen(i, owned.size, museumLevel)
            val c = card()
            c.add(ui.label(hall.first, assets.fontM, if (open) Theme.ink else Theme.muted)).left().row()
            val exhibits = hallItems(i)
            c.add(ui.label(if (open) "${exhibits.size} eser sergileniyor" else "Açılması için ${hall.second} eser ve Tarih Müzesi seviye $i gerekli", assets.fontS, if (open) Theme.inkSoft else Theme.red, wrap = true)).width(610f).left().row()
            val shelf = Table()
            c.add(shelf).height(if (open && exhibits.isNotEmpty()) 70f else 0f).width(610f).row()
            if (open && exhibits.isNotEmpty()) hallTables += shelf to i
            list.add(c).width(660f).padBottom(8f).row()
        }
        root.add(ui.scroll(list)).grow()
    }

    private fun hallItems(i: Int): List<com.zamaninefendisi.logic.continuity.CollectionItem> {
        val own = CivCollection.items.filter { it.id in owned }
        return when (i) {
            0 -> own.take(10)
            1 -> own.filter { it.era == Era.PAST }
            2 -> own.filter { it.era == Era.PRESENT }
            3 -> own.filter { it.era == Era.FUTURE }
            4 -> own.filter { "${it.era.ordinal}/${it.set}" in CivCollection.completedSets(owned) }
            else -> own
        }
    }

    private val tmp = Vector2()

    /** Sergi kaidelerini ve eserleri çizer. */
    override fun drawOverlay() {
        val sr = game.shapes
        sr.projectionMatrix = stage.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        sr.begin(ShapeRenderer.ShapeType.Filled)
        for ((t, hall) in hallTables) {
            if (t.stage == null) continue
            tmp.set(0f, 0f); t.localToStageCoordinates(tmp)
            if (tmp.y < 0 || tmp.y > stage.height) continue
            val items = hallItems(hall).take(14)
            for ((k, it) in items.withIndex()) {
                val x = tmp.x + 20f + k * 42f; val y = tmp.y + 10f
                sr.color = Theme.creamDeep; sr.rect(x - 14f, y, 28f, 22f)
                sr.color = Theme.eraColor(it.era)
                val bob = kotlin.math.sin(time * 2f + k) * 3f
                sr.triangle(x - 9f, y + 34f + bob, x + 9f, y + 34f + bob, x, y + 50f + bob)
                sr.triangle(x - 9f, y + 34f + bob, x + 9f, y + 34f + bob, x, y + 24f + bob)
            }
        }
        sr.end()
    }

    override fun onBack() = game.go(CollectionScreen(game))
}

class AchievementsScreen(game: ZamaninEfendisiGame) : BaseScreen(game) {
    init {
        game.progression.continuity.checkAchievements()
        val done = game.progression.data.continuity.achievements
        root.top().pad(24f)
        root.add(header("Başarımlar") { onBack() }).growX().padBottom(10f).row()
        root.add(ui.label("${done.size}/${Achievements.all.size} başarım", assets.fontS, Theme.purple)).padBottom(10f).row()
        val list = Table().top()
        for (a in Achievements.all) {
            val ok = a.id in done
            val c = ui.panel(if (ok) Color.valueOf("E8F7EA") else Theme.panel, small = true)
            c.pad(12f, 18f, 12f, 18f)
            val left = Table()
            left.add(ui.label(a.title, assets.fontM, if (ok) Theme.green else Theme.ink)).left().row()
            left.add(ui.label(a.description, assets.fontS, Theme.inkSoft, wrap = true)).width(460f).left()
            c.add(left).expandX().left()
            c.add(ui.label(if (ok) "✓" else "+${a.reward} kristal", assets.fontS, if (ok) Theme.green else Theme.purple)).width(130f)
            list.add(c).width(660f).padBottom(6f).row()
        }
        root.add(ui.scroll(list)).grow()
    }

    override fun onBack() = game.go(MainMenuScreen(game))
}
