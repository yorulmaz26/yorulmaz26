package com.besaniyelik.dunya.ui.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.besaniyelik.dunya.BesSaniyelikDunya
import com.besaniyelik.dunya.audio.Sfx
import com.besaniyelik.dunya.level.GameContent
import com.besaniyelik.dunya.level.PowerupType
import com.besaniyelik.dunya.progress.*
import com.besaniyelik.dunya.render.CharState
import com.besaniyelik.dunya.render.rgb
import com.besaniyelik.dunya.sim.Mechanic
import com.besaniyelik.dunya.ui.Palette
import com.besaniyelik.dunya.ui.popIn

private fun Table.onTap(kit: com.besaniyelik.dunya.ui.UiKit, f: () -> Unit) {
    touchable = Touchable.enabled
    addListener(object : ClickListener() { override fun clicked(event: InputEvent?, x: Float, y: Float) { kit.click(); f() } })
}

class CharacterScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    private var selected: String = game.profile.data.selectedSkin
    private var tab = 0
    override fun build() {
        val d = game.profile.data
        root.add(header("Karakterler")).growX().padTop(20f).row()
        val tabs = Table()
        listOf("Görünümler", "İz Efektleri").forEachIndexed { i, t ->
            val b = Table().apply { background = kit.patch(if (tab == i) "tab_sel" else "tab"); pad(10f, 26f, 14f, 26f); add(kit.label(t, a.f28, Palette.INK)) }
            b.onTap(kit) { tab = i; rebuild() }
            tabs.add(b).pad(6f)
        }
        root.add(tabs).padTop(8f).row()
        if (tab == 0) skins(d) else trails(d)
    }

    private fun skins(d: SaveData) {
        val skin = Catalog.skin(selected)
        val preview = kit.panel()
        val av = AvatarActor(game, 220f, selected, CharState.HAPPY)
        preview.add(av).size(220f).row()
        preview.add(kit.label(skin.name, a.f48, Palette.INK)).row()
        val owned = skin.id in d.ownedSkins
        val unlock = skin.unlock
        when {
            owned && d.selectedSkin == skin.id -> preview.add(kit.label("Seçili karakter", a.f28, Palette.GRASS)).padTop(6f)
            owned -> preview.add(kit.button("Seç", "green", "g_check") { game.profile.selectSkin(skin.id); rebuild() }).width(300f).height(110f).padTop(6f)
            unlock is Unlock.Coins || unlock is Unlock.Gems -> {
                val icon = if (unlock is Unlock.Coins) "b_coin" else "b_gem"
                val price = if (unlock is Unlock.Coins) unlock.price else (unlock as Unlock.Gems).price
                preview.add(kit.button("Satın Al  $price", "yellow", icon) {
                    val r = game.profile.buySkin(skin.id)
                    if (r == PurchaseResult.OK) { game.audio.play(Sfx.REWARD); game.profile.selectSkin(skin.id) }
                    toast(r.message, icon); rebuild()
                }).width(360f).height(110f).padTop(6f)
            }
            else -> preview.add(kit.label("Kilit: ${unlock.describe()}", a.f28, Palette.INK_SOFT)).padTop(6f)
        }
        val grid = Table()
        Catalog.skins.forEachIndexed { i, s ->
            val cell = Table().apply { background = kit.patch(if (s.id == selected) "card_sel" else "card"); pad(6f) }
            val av2 = AvatarActor(game, 96f, s.id)
            if (s.id !in d.ownedSkins) av2.color.a = 0.45f
            cell.add(av2).size(96f).row()
            cell.add(kit.label(s.name, a.f22, Palette.INK)).row()
            if (s.id !in d.ownedSkins) cell.add(Image(a.ui("g_lock_gray"))).size(26f)
            cell.onTap(kit) { selected = s.id; rebuild() }
            grid.add(cell).size(140f, 168f).pad(5f)
            if (i % 5 == 4) grid.row()
        }
        val body = Table()
        body.add(preview).width(420f).top().padRight(16f)
        body.add(scroll(grid)).grow()
        root.add(body).grow().pad(8f, 20f, 10f, 20f)
    }

    private fun trails(d: SaveData) {
        val list = Table()
        for (t in Catalog.trails) {
            val row = Table().apply { background = kit.patch("panel"); pad(22f) }
            val sw = Table()
            t.colors.take(5).forEach { c -> sw.add(Image(a.ui("soft")).apply { color = rgb(c) }).size(44f) }
            row.add(sw).width(200f)
            val info = Table()
            info.add(kit.label(t.name, a.f36, Palette.INK, Align.left)).left().row()
            info.add(kit.label(t.unlock.describe(), a.f22, Palette.INK_SOFT, Align.left)).left()
            row.add(info).expandX().left()
            when {
                d.selectedTrail == t.id -> row.add(Image(a.ui("b_star"))).size(64f)
                t.id in d.ownedTrails -> row.add(kit.button("Seç", "green") { game.profile.selectTrail(t.id); rebuild() }).width(150f).height(96f)
                t.unlock is Unlock.Coins || t.unlock is Unlock.Gems -> row.add(kit.button("Al", "yellow", if (t.unlock is Unlock.Coins) "b_coin" else "b_gem") {
                    val r = game.profile.buyTrail(t.id); if (r == PurchaseResult.OK) { game.audio.play(Sfx.REWARD); game.profile.selectTrail(t.id) }
                    toast(r.message); rebuild()
                }).width(160f).height(96f)
                else -> row.add(Image(a.ui("g_lock_gray"))).size(60f)
            }
            list.add(row).width(900f).padBottom(12f).row()
        }
        root.add(scroll(list)).grow().padTop(12f)
    }
}

class ShopScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    override fun build() {
        val d = game.profile.data
        root.add(header("Mağaza")).growX().padTop(20f).row()
        val list = Table()
        list.add(kit.label("Güçlendirmeler", a.f36, Palette.INK)).padTop(6f).row()
        list.add(kit.label("Bölüm başında en fazla 2 tane seçebilirsin. Hepsi oynayarak kazanılır; hiçbir bölüm güçlendirme gerektirmez.", a.f22, Palette.INK_SOFT, wrap = true)).width(620f).row()
        val grid = Table()
        Catalog.shopPowerups.entries.forEachIndexed { i, (t, price) ->
            val c = Table().apply { background = kit.patch("panel"); pad(20f, 14f, 22f, 14f) }
            c.add(Image(a.ui("b_" + t.icon))).size(84f).row()
            c.add(kit.label(t.title, a.f28, Palette.INK, wrap = true)).width(250f).row()
            c.add(kit.label(t.desc, a.f22, Palette.INK_SOFT, wrap = true)).width(250f).row()
            c.add(kit.label("Sende: ${d.inventory[t] ?: 0}", a.f22, Palette.INK)).padTop(4f).row()
            c.add(kit.button("$price", "yellow", "b_coin", a.f36o) {
                val r = game.profile.buyPowerup(t); if (r == PurchaseResult.OK) game.audio.play(Sfx.REWARD); toast(r.message, "b_" + t.icon); rebuild()
            }).width(220f).height(96f).padTop(6f)
            grid.add(c).width(290f).pad(6f)
            if (i % 4 == 3) grid.row()
        }
        list.add(grid).padTop(8f).row()
        list.add(kit.label("Altın Paketleri (elmasla)", a.f36, Palette.INK)).padTop(20f).row()
        val packs = Table()
        Catalog.coinPacks.forEachIndexed { i, (gems, coins) ->
            val c = Table().apply { background = kit.patch("panel_yellow"); pad(18f) }
            c.add(Image(a.ui("b_coin"))).size(70f).row()
            c.add(kit.label("$coins altın", a.f28, Palette.INK)).row()
            c.add(kit.button("$gems", "purple", "b_gem", a.f28) {
                val r = game.profile.buyCoinPack(i); if (r == PurchaseResult.OK) game.audio.play(Sfx.REWARD); toast(r.message, "b_coin"); rebuild()
            }).width(170f).height(86f).padTop(6f)
            packs.add(c).width(205f).pad(4f)
        }
        list.add(packs).row()
        list.add(kit.label("Karakterler ve iz efektleri için Karakterler ekranına göz at!", a.f22, Palette.INK_SOFT, wrap = true)).width(600f).padTop(14f).row()
        list.add(kit.button("Karakterler", "pink", "b_person") { game.go(CharacterScreen(game)) }).width(380f).height(110f).padTop(8f).padBottom(30f)
        root.add(scroll(list)).grow().padTop(10f)
    }
}

class MissionsScreen(game: BesSaniyelikDunya, private var weekly: Boolean) : BaseScreen(game) {
    override fun build() {
        game.profile.refreshDaily()
        val d = game.profile.data
        root.add(header(if (weekly) "Haftalık Görevler" else "Günlük Görevler")).growX().padTop(20f).row()
        val tabs = Table()
        listOf("Günlük", "Haftalık").forEachIndexed { i, t ->
            val b = Table().apply { background = kit.patch(if ((i == 1) == weekly) "tab_sel" else "tab"); pad(10f, 30f, 14f, 30f); add(kit.label(t, a.f28, Palette.INK)) }
            b.onTap(kit) { weekly = i == 1; rebuild() }
            tabs.add(b).pad(6f)
        }
        root.add(tabs).padTop(8f).row()
        root.add(kit.label(if (weekly) "Her pazartesi yeni görevler gelir." else "Her gün 3 yeni görev seni bekliyor.", a.f22, Palette.INK_SOFT)).padTop(4f).row()
        val list = Table()
        val ms = if (weekly) d.missions.weekly else d.missions.daily
        for (m in ms) {
            val row = Table().apply { background = kit.patch(if (m.done && !m.claimed) "panel_yellow" else "panel"); pad(24f) }
            val info = Table()
            info.add(kit.label(m.type.text(m.target), a.f28, Palette.INK, Align.left, wrap = true)).width(400f).left().row()
            info.add(kit.bar(m.progress / m.target.toFloat(), "bar_fill", 22f)).width(400f).height(22f).left().padTop(8f).row()
            val rw = buildList { add("${m.progress.coerceAtMost(m.target)}/${m.target}"); add("${m.rewardCoins} altın"); if (m.rewardGems > 0) add("${m.rewardGems} elmas"); add("${m.rewardXp} XP") }
            info.add(kit.label(rw.joinToString("  •  "), a.f22, Palette.INK_SOFT, Align.left)).left().padTop(6f)
            row.add(info).expandX().left()
            when {
                m.claimed -> row.add(Image(a.ui("b_star"))).size(70f)
                m.done -> row.add(kit.button("Al", "green", "b_gift") {
                    game.profile.claimMission(m.id)?.let { r -> game.audio.play(Sfx.REWARD); toast("+${r.coins} altın" + (if (r.gems > 0) ", +${r.gems} elmas" else "") + ", +${r.xp} XP", "b_gift") }
                    rebuild()
                }).width(160f).height(100f)
                else -> row.add(Image(a.ui("g_lock_gray"))).size(56f)
            }
            list.add(row).width(900f).padBottom(14f).row()
        }
        root.add(scroll(list)).grow().padTop(10f)
    }
}

class AchievementsScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    override fun build() {
        val d = game.profile.data
        root.add(header("Başarılar")).growX().padTop(20f).row()
        val n = d.achievements.size
        root.add(kit.label("$n / ${Catalog.achievements.size} başarı açıldı", a.f28, Palette.INK)).padTop(8f).row()
        root.add(kit.bar(n / Catalog.achievements.size.toFloat(), "bar_fill_y", 22f)).width(520f).height(22f).padTop(6f).row()
        val list = Table()
        val sorted = Catalog.achievements.sortedBy { if (it.id in d.achievements) 1 else 0 }
        for (ach in sorted) {
            val got = ach.id in d.achievements
            val row = Table().apply { background = kit.patch(if (got) "panel_yellow" else "panel"); pad(20f) }
            row.add(Image(a.ui(if (got) "b_trophy" else "g_lock_gray"))).size(70f).padRight(14f)
            val info = Table()
            info.add(kit.label(ach.title, a.f28, Palette.INK, Align.left)).left().row()
            info.add(kit.label(ach.desc, a.f22, Palette.INK_SOFT, Align.left)).left().row()
            if (!got) {
                val p = AchievementManager.progress(d, ach)
                info.add(kit.bar(p / ach.target.toFloat(), "bar_fill", 16f)).width(380f).height(16f).left().padTop(6f).row()
                info.add(kit.label("$p / ${ach.target}", a.f22, Palette.INK_SOFT, Align.left)).left()
            }
            row.add(info).expandX().left()
            row.add(Table().apply { add(Image(a.ui("b_gem"))).size(40f); add(kit.label("${ach.gems}", a.f22, Palette.INK)) })
            list.add(row).width(900f).padBottom(10f).row()
        }
        root.add(scroll(list)).grow().padTop(10f)
    }
}

class CollectionScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    override fun build() {
        val d = game.profile.data
        root.add(header("Koleksiyon")).growX().padTop(20f).row()
        root.add(kit.label("Her haritada 5 gizli parça var. Hepsini bul, özel ödülü aç!", a.f22, Palette.INK_SOFT, wrap = true)).width(600f).padTop(6f).row()
        val list = Table()
        for (m in GameContent.maps) {
            val have = CollectionManager.piecesOf(d, m.id)
            val done = m.id in d.collectionsClaimed
            val row = Table().apply { background = kit.patch(if (done) "panel_yellow" else "panel"); pad(20f) }
            val info = Table()
            info.add(kit.label(m.name, a.f36, Palette.INK, Align.left)).left().row()
            val pcs = Table()
            val ids = listOf(8, 18, 28, 38, 48).map { m.firstLevel - 1 + it }
            ids.forEach { id -> pcs.add(Image(a.ui("b_puzzle")).apply { if (id !in d.pieces) color = Color(0.6f, 0.65f, 0.75f, 0.35f) }).size(56f).pad(2f) }
            info.add(pcs).left().padTop(4f).row()
            info.add(kit.label("Ödül: ${CollectionManager.rewardName(m.id)}" + if (done) "  (alındı)" else "", a.f22, if (done) Palette.GRASS else Palette.INK_SOFT, Align.left)).left().padTop(4f).row()
            info.add(kit.label("Parçalar: ${ids.joinToString(", ") { "Bölüm $it" }}", a.f22, Palette.INK_SOFT, Align.left, wrap = true)).width(560f).left()
            row.add(info).expandX().left()
            row.add(kit.label("$have/5", a.f36, Palette.INK))
            list.add(row).width(900f).padBottom(12f).row()
        }
        root.add(scroll(list)).grow().padTop(10f)
    }
}

class ProfileScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    override fun build() {
        val d = game.profile.data
        root.add(header("Profil")).growX().padTop(20f).row()
        val p = kit.panel()
        p.add(AvatarActor(game, 170f, null, CharState.HAPPY)).size(170f).row()
        p.add(kit.label(d.playerName, a.f48, Palette.INK)).row()
        p.add(kit.label("Seviye ${d.playerLevel}", a.f28, Palette.INK_SOFT)).row()
        val need = ProgressionManager.xpToNext(d.playerLevel)
        p.add(kit.bar(d.xp / need.toFloat(), "bar_fill_b", 24f)).width(420f).height(24f).padTop(8f).row()
        p.add(kit.label("${d.xp} / $need XP  •  Seviye ${d.playerLevel + 1}: ${ProgressionManager.levelUpText(d.playerLevel + 1).let { if (it == "Seviye atladın!") "${ProgressionManager.rewardFor(d.playerLevel + 1).coins} altın" else it }}", a.f22, Palette.INK_SOFT, wrap = true)).width(480f).padTop(4f).row()
        val names = listOf("Koşucu", "Zıpzıp", "Hızlı", "Yıldız", "Bulut", "Kaşif", "Şimşek", "Minik")
        p.add(kit.button("İsmi Değiştir", "white", font = a.f28, textColor = Palette.INK) {
            d.playerName = names[(names.indexOf(d.playerName) + 1) % names.size]; game.profile.save(); rebuild()
        }).width(300f).height(90f).padTop(10f).row()
        val st = d.stats
        val grid = Table()
        val stats = listOf(
            "Tamamlanan bölüm" to "${st.levelsCompleted}/500", "Toplam yıldız" to "${d.totalStars}/1500", "Toplanan altın" to "${st.coinsCollected}",
            "Zıplama" to "${st.jumps}", "Atlatılan kural" to "${st.rulesSurvived}", "Kusursuz bölüm" to "${st.perfectLevels}",
            "Sonsuz rekor" to "${st.endlessBestScore}", "Sonsuz koşu" to "${st.endlessRuns}", "Gizli parça" to "${d.pieces.size}/50",
            "Karakter" to "${d.ownedSkins.size}/20", "Başarı" to "${d.achievements.size}/50", "Oyun süresi" to "%.0f dk".format(st.playSeconds / 60f),
        )
        stats.forEachIndexed { i, (k, v) ->
            grid.add(Table().apply { background = kit.patch("card"); pad(12f); add(kit.label(v, a.f28, Palette.INK)).row(); add(kit.label(k, a.f22, Palette.INK_SOFT)) }).width(200f).height(100f).pad(5f)
            if (i % 3 == 2) grid.row()
        }
        val body = Table()
        body.add(p).width(560f).top().padRight(16f)
        body.add(scroll(grid)).grow().top()
        root.add(body).grow().pad(8f, 20f, 10f, 20f)
    }
}

class SettingsScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    private var confirmReset = false
    override fun build() {
        val st = game.profile.data.settings
        root.add(header("Ayarlar", showCurrency = false)).growX().padTop(20f).row()
        val p = kit.panel()
        fun toggle(label: String, icon: String, value: Boolean, set: (Boolean) -> Unit) {
            val row = Table()
            row.add(Image(a.ui(icon)).apply { color = Palette.INK_SOFT }).size(56f).padRight(14f)
            row.add(kit.label(label, a.f28, Palette.INK, Align.left, wrap = true)).width(330f).expandX().left()
            val t = Table().apply { background = kit.patch(if (value) "toggle_on" else "toggle_off"); pad(6f) }
            t.add(Image(a.ui("soft")).apply { color = Color.WHITE }).size(52f).expandX().apply { if (value) right() else left() }
            t.onTap(kit) { set(!value); game.audio.applyVolume(); game.profile.save(); rebuild() }
            row.add(t).size(130f, 68f)
            p.add(row).width(560f).padBottom(12f).row()
        }
        toggle("Müzik", "g_music", st.music) { st.music = it }
        toggle("Ses Efektleri", "g_sound", st.sound) { st.sound = it }
        toggle("Titreşim", "g_vibrate", st.vibration) { st.vibration = it }
        toggle("Az Efekt (pil tasarrufu)", "g_info", st.reducedEffects) { st.reducedEffects = it }
        fun slider(label: String, v: Float, set: (Float) -> Unit) {
            val row = Table()
            row.add(kit.label(label, a.f28, Palette.INK, Align.left)).width(220f).left()
            row.add(kit.iconButton("g_back", "white", 80f, 40f) { set((v - 0.1f).coerceAtLeast(0f)); game.audio.applyVolume(); game.profile.save(); rebuild() }).size(80f)
            row.add(kit.bar(v, "bar_fill_b", 24f)).width(180f).height(24f).pad(0f, 10f, 0f, 10f)
            row.add(kit.iconButton("g_next", "white", 80f, 40f) { set((v + 0.1f).coerceAtMost(1f)); game.audio.applyVolume(); game.profile.save(); rebuild() }).size(80f)
            p.add(row).width(560f).padBottom(14f).row()
        }
        slider("Müzik sesi", st.musicVolume) { st.musicVolume = it }
        slider("Efekt sesi", st.soundVolume) { st.soundVolume = it }
        val side = Table()
        side.add(kit.button("Nasıl Oynanır?", "turq", "g_info") { game.go(HowToPlayScreen(game)) }).width(420f).height(104f).row()
        side.add(kit.button(if (confirmReset) "Emin misin? Tekrar dokun" else "İlerlemeyi Sıfırla", "red", "g_retry", a.f28) {
            if (confirmReset) { game.profile.resetAll(); toast("İlerleme sıfırlandı."); confirmReset = false } else confirmReset = true
            rebuild()
        }).width(420f).height(96f).padTop(14f).row()
        side.add(kit.button("Gizlilik Politikası", "white", "g_info", a.f28, Palette.INK) { showPrivacy() }).width(420f).height(88f).padTop(14f).row()
        side.add(kit.label("5 Saniyelik Dünya • Sürüm 1.0.0\nİnternet gerekmez, ilerlemen cihazında güvenle saklanır.\nYazı tipi: Nunito (SIL OFL).", a.f22, Palette.INK_SOFT, wrap = true)).width(420f).padTop(14f)
        val body = Table()
        body.add(p).width(640f).padRight(24f)
        body.add(side).top()
        root.add(body).expand().center()
    }

    /** Gizlilik politikası uygulamanın içinde de okunabilir (Google Play Kullanıcı Verileri politikası gereği). */
    fun showPrivacy() {
        dialog(resultW, resultH) { p, close ->
            p.top()
            p.add(kit.label("Gizlilik Politikası", a.f48, Palette.INK)).padTop(4f).row()
            val box = Table().top().left()
            box.add(kit.label(PRIVACY_POLICY_TR, a.f22, Palette.INK, Align.left, wrap = true)).width(resultW - 130f).left()
            p.add(scroll(box)).width(resultW - 90f).expandY().fillY().padTop(8f).row()
            p.add(kit.button("Tamam", "green", "g_check") { close() }).width(300f).height(92f).padTop(10f)
        }
    }
}

/** Uygulama içi gizlilik politikası metni; mağaza sayfasına konacak sürümü docs/play_store/gizlilik_politikasi.md dosyasındadır. */
const val PRIVACY_POLICY_TR = """5 Saniyelik Dünya tamamen çevrimdışı çalışan bir oyundur.

TOPLANAN VERİ: Yok. Oyun adınızı, e-posta adresinizi, konumunuzu, rehberinizi, reklam kimliğinizi veya cihaz bilgilerinizi toplamaz; hiçbir sunucuya veri göndermez ve internet izni istemez.

REKLAM VE SATIN ALMA: Oyunda reklam, gerçek parayla satın alma, analiz ya da izleme aracı yoktur. Mağazadaki altın ve elmaslar yalnızca oynayarak kazanılır.

KAYIT DOSYASI: İlerlemeniz (açılan bölümler, yıldızlar, ayarlar) yalnızca cihazınızda saklanır. Cihazınızda Android yedeklemesi açıksa bu dosya kendi Google hesabınızın yedeğine dahil edilebilir; geliştirici bu yedeğe erişemez. Kaydı Ayarlar ekranındaki İlerlemeyi Sıfırla düğmesiyle ya da uygulamayı kaldırarak silebilirsiniz.

İZİNLER: Yalnızca titreşim. Ayarlar ekranından kapatılabilir.

ÇOCUKLAR: Oyun hiç kimseden kişisel veri toplamadığı için çocuklardan da veri toplamaz.

DEĞİŞİKLİKLER: Bu politika değişirse güncel metin bu ekranda ve mağaza sayfasındaki bağlantıda yayımlanır.

İLETİŞİM: Google Play sayfasında yazan geliştirici e-posta adresi.

Son güncelleme: 21 Kasım 2026"""

class DailyRewardScreen(game: BesSaniyelikDunya, private val fromSplash: Boolean = false) : BaseScreen(game) {
    override fun build() {
        val d = game.profile.data
        root.add(header("Günlük Ödül", back = { onBack() })).growX().padTop(20f).row()
        root.add(kit.label("Her gün giriş yap, ödülleri topla! Bir gün kaçırırsan ilerlemen kaybolmaz.", a.f22, Palette.INK_SOFT, wrap = true)).width(600f).padTop(8f).row()
        val grid = Table()
        val today = d.login.cycleDay % 7
        val available = game.profile.loginAvailable()
        Catalog.loginRewards.forEachIndexed { i, r ->
            val claimed = i < today || (i == today && !available && false)
            val isToday = i == today && available
            val cell = Table().apply { background = kit.patch(when { isToday -> "card_sel"; i < today -> "panel_yellow"; else -> "card" }); pad(12f) }
            cell.add(kit.label("${i + 1}. Gün", a.f22, Palette.INK)).row()
            val icon = when { r.gems > 0 && r.coins > 0 -> "b_gift"; r.gems > 0 -> "b_gem"; r.coins > 0 -> "b_coin"; else -> "b_" + r.powerups.keys.first().icon }
            cell.add(Image(a.ui(if (claimed) "g_check" else icon)).apply { if (claimed) color = Palette.GRASS }).size(if (i == 6) 90f else 70f).padTop(4f).row()
            cell.add(kit.label(r.describe(), a.f22, Palette.INK_SOFT, wrap = true)).width(134f)
            grid.add(cell).width(160f).height(if (i == 6) 236f else 216f).pad(5f)
            if (isToday) cell.popIn(0.1f)
        }
        root.add(grid).padTop(24f).row()
        if (available) {
            root.add(kit.button("Ödülü Al!", "green", "b_gift", a.f48o) {
                val r = game.profile.claimLogin()
                if (r != null) { game.audio.play(Sfx.REWARD); toast("Kazandın: ${r.describe()}", "b_gift") }
                rebuild()
            }).width(460f).height(130f).padTop(24f).row()
        } else {
            root.add(kit.label("Bugünkü ödülünü aldın. Yarın yine gel!", a.f28, Palette.INK)).padTop(24f).row()
            root.add(kit.button("Devam", "yellow", "g_next") { onBack() }).width(360f).height(110f).padTop(16f)
        }
    }
    override fun onBack() { game.go(MainMenuScreen(game)) }
}

/** Öğretici ekranlar: temel kontroller ve on dünya mekaniği. */
class HowToPlayScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    private var page = 0
    override fun build() {
        root.add(header("Nasıl Oynanır?", showCurrency = false)).growX().padTop(20f).row()
        val pages = buildList {
            add(Triple("g_tap", "Dokun ve Zıpla", "Karakterin kendiliğinden koşar. Ekrana dokunduğunda zıplar. Basılı tutarsan daha yükseğe zıplarsın. Kenardan düştükten hemen sonra bile kısa bir süre zıplayabilirsin."))
            add(Triple("b_m_normal", "5 Saniye Kuralı", "Her 5 saniyede dünyanın kuralları değişir. Değişimden önce ekranın üstünde sıradaki kural yanıp söner. Kural değişimi seni asla haksız yere öldürmez."))
            add(Triple("b_star", "Yıldızlar ve Altınlar", "Her bölümde 3 yıldız var. 1 yıldız için bölümü bitir, 2 yıldız için en az 2 yıldız topla, 3 yıldız için üç yıldızı da topla ve en fazla 1 kez yanıl. Altınlarla mağazadan alışveriş yap."))
            add(Triple("b_heart", "Canlar ve Kontrol Noktaları", "Her bölümde 3 canın var. Yeşil bayraklar kontrol noktasıdır: düşersen oradan devam edersin."))
            add(Triple("b_p_shield", "Güçlendirmeler", "Baloncuklu güçlendirmeleri topla: kalkan, mıknatıs, çift zıplama, dokunulmazlık, ekstra can, altın çarpanı, yıldız algılayıcı ve kontrol noktası koruması."))
            Mechanic.learnOrder.forEach { add(Triple("b_" + it.icon, it.title, it.hint + "\n(Bölüm ${it.unlockLevel}'de öğrenilir)")) }
        }
        page = page.coerceIn(0, pages.size - 1)
        val (icon, title, text) = pages[page]
        val p = kit.panel()
        p.add(Image(a.ui(icon)).apply { if (icon.startsWith("g_")) color = Palette.ORANGE }).size(120f).row()
        p.add(kit.label(title, a.f48, Palette.INK)).padTop(10f).row()
        p.add(kit.label(text, a.f28, Palette.INK_SOFT, wrap = true)).width(640f).padTop(10f).row()
        p.add(kit.label("${page + 1} / ${pages.size}", a.f22, Palette.INK_SOFT)).padTop(12f)
        p.popIn()
        val body = Table()
        body.add(kit.iconButton("g_back", "white", 110f, 56f) { page--; rebuild() }).size(110f).padRight(30f).also { if (page == 0) it.actor.enabled = false }
        body.add(p).width(740f)
        body.add(kit.iconButton("g_next", "green", 110f, 56f) { if (page < pages.size - 1) { page++; rebuild() } else onBack() }).size(110f).padLeft(30f)
        root.add(body).expand().center()
    }
}
