package com.zamaninefendisi.game.screens

import com.badlogic.gdx.Screen
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.zamaninefendisi.game.ZamaninEfendisiGame
import com.zamaninefendisi.game.ui.Theme
import com.zamaninefendisi.logic.levels.LevelCatalog
import com.zamaninefendisi.logic.save.MasterLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Kalıcı Zaman Günlüğü: oyuncunun kararları ve dünyaya etkileri. */
class JournalScreen(game: ZamaninEfendisiGame) : BaseScreen(game) {
    init {
        root.top().pad(24f)
        root.add(header("Zaman Günlüğü") { onBack() }).growX().padBottom(16f).row()
        val entries = game.progression.data.journal
        val list = Table()
        if (entries.isEmpty()) {
            list.add(ui.label("Henüz bir kayıt yok. Bir bölümü tamamladığında, Geçmiş'te yaptığın değişikliklerin Şimdi'ye ve Gelecek'e nasıl yansıdığı burada saklanır.", assets.fontM, Theme.inkSoft, wrap = true, align = Align.center)).width(600f).padTop(80f)
        } else {
            val fmt = SimpleDateFormat("d MMM HH:mm", Locale.forLanguageTag("tr"))
            var lastLevel = -1
            for (e in entries) {
                if (e.levelId != lastLevel) {
                    lastLevel = e.levelId
                    list.add(ui.label("${LevelCatalog.level(e.levelId)?.name ?: "Bölüm ${e.levelId}"} · ${fmt.format(Date(e.timestamp))}", assets.fontS, Theme.orange)).left().padTop(14f).row()
                }
                val card = ui.panel(Theme.panel, small = true)
                card.add(ui.label(e.text.substringAfter(": "), assets.fontS, Theme.ink, wrap = true)).width(580f).pad(12f, 16f, 12f, 16f)
                list.add(card).padTop(6f).row()
            }
        }
        root.add(ui.scroll(list)).grow()
    }

    override fun onBack() = game.go(MainMenuScreen(game))
}

/** Oyuncu profili: Zaman Ustası seviyesi, istatistikler ve kişisel rekorlar. */
class ProfileScreen(game: ZamaninEfendisiGame) : BaseScreen(game) {
    init {
        root.top().pad(24f)
        root.add(header("Profil") { onBack() }).growX().padBottom(16f).row()
        val p = game.progression
        val d = p.data
        val lvl = MasterLevel.levelOf(d.xp)
        val card = ui.panel(Theme.panel)
        card.pad(26f)
        card.add(ui.label("Zaman Muhafızı", assets.fontL, Theme.ink)).row()
        card.add(ui.label("Zaman Ustası Seviye $lvl", assets.fontM, Theme.purple)).padTop(6f).row()
        val bar = Table()
        val w = 520f
        val prog = MasterLevel.progress(d.xp)
        bar.add(Table().apply { background = assets.panel(Theme.purple, true) }).width((w * prog).coerceAtLeast(14f)).height(22f)
        bar.add(Table().apply { background = assets.panel(Theme.creamDeep, true) }).width((w * (1 - prog)).coerceAtLeast(0f)).height(22f)
        card.add(bar).padTop(10f).row()
        card.add(ui.label("${d.xp - MasterLevel.xpFor(lvl)} / ${MasterLevel.xpFor(lvl + 1) - MasterLevel.xpFor(lvl)} deneyim", assets.fontS, Theme.inkSoft)).padTop(6f).row()
        root.add(card).width(660f).padBottom(18f).row()

        val done = LevelCatalog.levels.count { p.isCompleted(it.id) }
        val st = d.stats
        val rows = listOf(
            "Tamamlanan bölüm" to "$done / ${LevelCatalog.levels.size}",
            "Toplam yıldız" to "${p.totalStars()} / ${LevelCatalog.levels.size * 3}",
            "Altın" to "${d.gold}",
            "Zaman kristali" to "${d.crystals}",
            "Kazanılan deneme" to "${st.levelsWon}",
            "Başarısız deneme" to "${st.levelsFailed}",
            "Zaman geçişi" to "${st.eraSwitches}",
            "Kalıcı değişiklik" to "${st.buildings}",
            "Çözülen/yaşanan paradoks" to "${st.paradoxes}",
            "Yenilen düşman" to "${st.enemiesDefeated}",
            "Rekor: en yüksek kombo" to "${st.bestCombo}",
            "Kaybedilen birlik" to "${st.unitsLost}",
            "Rekor: en az eylemle zafer" to if (st.fewestActionsWin > 0) "${st.fewestActionsWin} eylem" else "—",
        )
        val t = ui.panel(Theme.panel)
        t.pad(20f, 26f, 20f, 26f)
        for ((k, v) in rows) {
            t.add(ui.label(k, assets.fontS, Theme.inkSoft)).left().expandX().padBottom(10f)
            t.add(ui.label(v, assets.fontS, Theme.ink)).right().padBottom(10f).row()
        }
        root.add(t).width(660f).row()
        val best = Table()
        best.add(ui.label("Bölüm rekorları", assets.fontM, Theme.ink)).left().padTop(20f).padBottom(8f).row()
        for (lv in LevelCatalog.levels) {
            val pr = p.progress(lv.id)
            if (!pr.completed) continue
            best.add(ui.label("${lv.id}. ${lv.name}: ${pr.bestActions} eylem · ${pr.stars} yıldız · ${pr.attempts} deneme", assets.fontS, Theme.inkSoft)).left().row()
        }
        root.add(best).width(660f)
    }

    override fun onBack() = game.go(MainMenuScreen(game))
}

/** Öğretici sayfalar. */
class HowToPlayScreen(game: ZamaninEfendisiGame, private val returnTo: (() -> Screen)? = null) : BaseScreen(game) {
    private val pages = listOf(
        "Üç Zaman Katmanı" to "Her bölümde aynı haritanın Geçmiş, Şimdi ve (ileride) Gelecek hâlleri vardır. Alttaki çağ düğmeleriyle geçiş yaparsın; düğmelerin içindeki küçük haritalar o çağın canlı görüntüsüdür.",
        "Nedensellik" to "Geçmiş'te yaptığın her şey Şimdi'ye evrilerek ulaşır: ahşap köprü taş köprü olur, fidan ağaca dönüşür, çit taş sura. Şimdi'de yapılanlar Geçmiş'i asla değiştirmez.",
        "Hareket ve Eylem" to "Komşu hücreye dokunarak yürürsün; uzak bir hücreye dokunursan kahraman yolu kendisi bulur. Yanındaki bir hücreye dokunduğunda yapabileceğin eylemler listelenir. Her eylem ve zaman geçişi bir tur sürer.",
        "Doğa Evrimi" to "Çöl her çağda ilerler. Bir çayırın 1 hücre yakınında bitki varsa kum oraya giremez. Ağaç kesmek odun kazandırır ama koruduğu toprakları savunmasız bırakır.",
        "Köyler" to "Geçmiş'te kurulan köy, suya komşuysa ve çevresi kumla kaplanmazsa Şimdi'de kasabaya dönüşür. Aksi hâlde harabeye döner.",
        "Akıncılar ve Zaman Tuzağı" to "Akıncılar Şimdi'de her tur ilerler. Şimdi'de kurulan çiti kırabilirler ama Geçmiş'te kurulan çit Şimdi'de yıkılmaz bir taş surdur. Bir akıncının durduğu hücreye Geçmiş'te çit kurarsan akıncı zamanın içinde kaybolur.",
        "Gelecek ve Kadim Orman" to "Alan 2'de Gelecek açılır: Geçmiş'teki bir değişiklik artık iki çağ boyunca evrilir. En az bir çağ yaşamış bir ağacın yanında (köşeler dahil) 2 ya da daha fazla bitki varsa, sonraki çağda geçilmez bir Kadim Orman olur. Orman hem kahramanı hem düşmanı durdurur.",
        "Zaman Enerjisi ve Kristaller" to "Bazı bölümlerde her çağ geçişi 1 zaman enerjisi harcar. Zaman kristalinin üstüne yürüyünce 1 enerji kazanırsın. Geçmiş'te toplanan kristal sonraki çağlarda hiç var olmamış olur; aynı kristali önce Gelecek'te, sonra Geçmiş'te toplarsan Gelecek'teki kazanç paradoksla silinir.",
        "Zaman Mühürleri" to "Uyuyan bir mühür uyandırıldığında başka bir çağda bir şey değişir: nehir kurur, dağ yarılır. Hücre bilgisinde bütün çağlardaki hâli görebilirsin. Nedensellik hiçbir zaman geriye işlemez.",
        "Zamanı Dondurma" to "Alan 6'da dondurma hakkı kazanırsın. Zamanı dondurulan nesne sonraki çağlara olduğu gibi aktarılır: köy harabeye dönmez, ağaç ormana dönüşmez, düşman kampı kaleye büyümez. Dondurulmuş nesne bitki ya da yapı olarak sayılmaya devam eder.",
        "Birlikler" to "Alan 7'de müttefik birlikler komutanda. Düşmanların bulunduğu çağdayken bir birliğe dokun: mavi hücreler yürüyebileceği, kırmızı hücreler saldırabileceği yerlerdir. Her komut bir tur sürer. Komuta etmediğin birlikler menzillerindeki en zayıf düşmana kendiliğinden ateş eder. Hasar = saldırı − savunma (en az 1). Şövalyenin kalkanı, koruyucunun zırhı, büyücünün zaman kilidi gibi yetenekleri kullan.",
        "Kuleler" to "Geçmiş'te kurulan gözcü kulesi Şimdi'de yıkılmaz taş kule, Gelecek'te enerji topu olur. Kuleler her tur menzillerindeki en yakın düşmana kendiliğinden ateş eder.",
        "Düşman Stratejileri" to "Saldırgan düşmanlar kaleye koşar. Savunmacılar kalelerini korur, kale düşerse öfkelenir. Yağmacılar tarla ve köyleri talan eder. Zaman Hırsızları duvarların üstünden faz atlar ve sen başka çağdayken hızlanır. Paradoks Ajanları senin yaptığın yapıları siler. Teknoloji Uzmanları uzaktan vurur, arayı açar ve güçlenir. Bir düşmana dokunarak stratejisini görebilirsin.",
        "Önizleme ve Kombo" to "Bir inşa seçeneğinin yanındaki Önizle düğmesi, eylemin ileri çağlarda neyi değiştireceğini uygulamadan gösterir. Kısa sürede üç farklı çağda kalıcı değişiklik yaparsan Zaman Zinciri Kombosu kazanırsın; her kombo ödülüne altın ekler.",
        "Paradoks" to "Geçmiş'te yaptığın bir değişiklik, Şimdi'de daha önce yaptığın bir şeyi imkânsız kılarsa o eylem hiç gerçekleşmemiş olur. Harcadığın odun geri döner; kazandığın odun ise buharlaşır. Bütün bunları Günlük'te görebilirsin.",
        "Yıldızlar ve Canlar" to "Bölümü az eylemle bitirirsen 3 yıldız kazanırsın (Bekle eylem sayılmaz). 4 canın var; başarısız bir denemede 1 can gider ve her can 15 dakikada sırayla yenilenir. Tamamladığın bölümleri can harcamadan tekrar oynayabilirsin; Geri Al ise her zaman ücretsizdir.",
    )
    private var page = 0
    private val content = Table()

    init {
        root.top().pad(24f)
        root.add(header("Nasıl Oynanır") { onBack() }).growX().padBottom(30f).row()
        root.add(content).width(660f).expandY().top().row()
        show(0)
    }

    private fun show(i: Int) {
        page = i
        content.clearChildren()
        val (title, text) = pages[i]
        val card = ui.panel(Theme.panel)
        card.pad(32f)
        card.add(ui.label("${i + 1} / ${pages.size}", assets.fontS, Theme.orange)).left().row()
        card.add(ui.label(title, assets.fontL, Theme.ink)).left().padTop(6f).padBottom(14f).row()
        card.add(ui.label(text, assets.fontM, Theme.inkSoft, wrap = true)).width(590f).left()
        content.add(card).width(660f).row()
        val nav = Table()
        val prev = ui.button("‹ Önceki", Theme.blue) { show(page - 1) }
        prev.isDisabled = i == 0
        nav.add(prev).size(280f, 90f).padRight(20f)
        if (i < pages.lastIndex) nav.add(ui.button("Sonraki ›", Theme.green) { show(page + 1) }).size(280f, 90f)
        else nav.add(ui.button("Anladım", Theme.green) { onBack() }).size(280f, 90f)
        content.add(nav).padTop(30f)
    }

    override fun onBack() = game.go(returnTo?.invoke() ?: MainMenuScreen(game))
}

class SettingsScreen(game: ZamaninEfendisiGame) : BaseScreen(game) {
    init { build() }

    private fun build() {
        root.clearChildren()
        root.top().pad(24f)
        root.add(header("Ayarlar") { onBack() }).growX().padBottom(30f).row()
        val p = game.progression
        val s = p.data.settings
        val card = ui.panel(Theme.panel)
        card.pad(26f)
        fun toggle(name: String, on: Boolean, f: (Boolean) -> Unit) {
            card.add(ui.label(name, assets.fontM, Theme.ink)).left().expandX().padBottom(18f)
            card.add(ui.button(if (on) "Açık" else "Kapalı", if (on) Theme.green else Theme.grey, assets.fontS) { f(!on); build() }).size(160f, 70f).padBottom(18f).row()
        }
        toggle("Ses efektleri", s.sound) { v -> p.update { it.copy(settings = it.settings.copy(sound = v)) } }
        toggle("Titreşim", s.vibration) { v -> p.update { it.copy(settings = it.settings.copy(vibration = v)) } }
        toggle("Bölüm ipuçları", s.hints) { v -> p.update { it.copy(settings = it.settings.copy(hints = v)) } }
        root.add(card).width(660f).row()
        root.add(ui.button("İlerlemeyi Sıfırla", Theme.red) { confirmReset() }).width(420f).height(84f).padTop(40f).row()
        root.add(ui.label("Kayıtlar yalnızca bu cihazda saklanır. Oyun tamamen çevrimdışı çalışır.", assets.fontS, Theme.inkSoft, wrap = true, align = Align.center)).width(600f).padTop(30f).row()
        root.add(ui.label("Yazı tipi: Inter (SIL Open Font License 1.1)", assets.fontS, Theme.muted)).padTop(10f)
    }

    private fun confirmReset() {
        val box = ui.panel(Theme.panel)
        box.pad(30f)
        box.add(ui.label("Emin misin?", assets.fontL, Theme.red)).row()
        box.add(ui.label("Bütün bölüm ilerlemen, yıldızların, altının ve Zaman Günlüğün silinecek. Ayarların korunur.", assets.fontS, Theme.inkSoft, wrap = true)).width(560f).pad(16f).row()
        lateinit var m: Table
        val row = Table()
        row.add(ui.button("Vazgeç", Theme.grey, fontColor = Theme.ink) { m.remove() }).size(240f, 80f).padRight(16f)
        row.add(ui.button("Sıfırla", Theme.red) { game.progression.resetAll(); m.remove(); ui.toast(root, "İlerleme sıfırlandı") }).size(240f, 80f)
        box.add(row)
        m = ui.modal(root, box, dismissOnOutside = true)
    }

    override fun onBack() = game.go(MainMenuScreen(game))
}
