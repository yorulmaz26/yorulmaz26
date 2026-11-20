package com.yanlisrenk.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Cullable
import com.yanlisrenk.game.YanlisRenkGame
import com.yanlisrenk.game.ui.Pal
import com.yanlisrenk.game.ui.Ui
import com.yanlisrenk.logic.level.AreaCatalog
import com.yanlisrenk.logic.shop.ShopCategory
import kotlin.math.sin

/** Haritaya dönüş: ustalık modunda alan listesi, normalde yol haritası. */
fun mapScreen(game: YanlisRenkGame, levelId: Int, mastery: Boolean = false): BaseScreen =
    if (mastery) AreaScreen(game, AreaCatalog.areaOfLevel(levelId), true) else SagaMapScreen(game, levelId)

/**
 * Bölüm haritası: 500 bölüm, kıvrımlı bir yol üzerinde yuvarlak düğmeler.
 * 1. bölüm en altta; yukarı kaydırdıkça ilerlenir. Her dünyanın bölümleri kendi arka planı üzerindedir.
 */
class SagaMapScreen(game: YanlisRenkGame, private val focusLevel: Int = -1) : BaseScreen(game) {
    override val recreate: (() -> BaseScreen) = { SagaMapScreen(game, focusLevel) }
    private lateinit var scroll: ScrollPane
    private lateinit var map: MapActor

    override val backgroundImage: String? get() = null

    override fun build() {
        root.top()
        root.add(topBar(null)).growX().row()
        map = MapActor()
        scroll = ScrollPane(map)
        scroll.setScrollingDisabled(true, false)
        scroll.setOverscroll(false, false)
        scroll.setFlickScroll(true)
        scroll.fadeScrollBars = true
        root.add(scroll).grow()
        val target = if (focusLevel > 0) focusLevel else session.currentLevel
        // açılışta hedef bölüme kaydır
        stage.addAction(Actions.run { scrollTo(target, false) })
        val back = Table()
        back.setFillParent(true)
        back.bottom().right().pad(0f, 0f, 30f, 24f)
        back.add(ui.button("Bölümüme dön", Pal.ORANGE, "flag", Ui.Size.S) { scrollTo(session.currentLevel, true) })
        back.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly
        stage.addActor(back)
    }

    private fun scrollTo(level: Int, smooth: Boolean) {
        scroll.layout()
        if (map.width != scroll.width && scroll.width > 0f) { map.width = scroll.width; scroll.layout() }
        val y = map.nodeY(level)
        val vh = scroll.height
        scroll.scrollY = (map.height - y - vh / 2f).coerceIn(0f, scroll.maxY)
        if (!smooth) scroll.updateVisualScroll()
    }

    override fun drawBackground(delta: Float) {
        val (top, bottom) = themeColors()
        val cam = stage.camera
        bgShapes.projectionMatrix = cam.combined
        bgShapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled)
        val w = stage.viewport.worldWidth; val h = stage.viewport.worldHeight
        bgShapes.rect(cam.position.x - w / 2, cam.position.y - h / 2, w, h, bottom, bottom, top, top)
        bgShapes.end()
    }

    /** Haritanın tamamını çizen, görünmeyen kısımları atlayan aktör. */
    inner class MapActor : Actor(), Cullable {
        private val spacing = 172f
        private val margin = 260f
        private val worldGap = 190f        // dünya başlığı için ek boşluk
        private val segH = spacing * 25 + worldGap // bir dünya = 5 alan × 5 bölüm
        private var cull: Rectangle? = null
        private val layout = GlyphLayout()
        private var t = 0f
        private val tmp = Color()

        init {
            setSize(720f, margin * 2 + spacing * 499 + worldGap * 19)
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) { hit(x, y)?.let { openLevel(it) } }
            })
        }

        override fun setCullingArea(cullingArea: Rectangle?) { cull = cullingArea }
        override fun act(delta: Float) { super.act(delta); t += delta }

        fun nodeY(level: Int) = margin + (level - 1) * spacing + ((level - 1) / 25) * worldGap
        /** Dünya [w]'nin arka plan bölümünün alt sınırı (ilk düğümün yarım aralık altı + başlık boşluğu). */
        private fun worldStart(w: Int) = if (w == 1) 0f else nodeY((w - 1) * 25 + 1) - spacing * 0.5f - worldGap
        private fun worldEnd(w: Int) = if (w == 20) height else worldStart(w + 1)
        private fun bannerY(w: Int) = if (w == 1) 105f else nodeY((w - 1) * 25 + 1) - spacing * 0.5f - worldGap * 0.5f
        fun nodeX(level: Int): Float {
            val i = level - 1
            return width / 2 + sin(i * 0.78f) * 205f + sin(i * 0.23f) * 40f
        }
        /** y konumuna en yakın bölüm (yaklaşık). */
        fun levelAt(y: Float): Int {
            var lo = 1; var hi = 500
            while (lo < hi) { val m = (lo + hi + 1) / 2; if (nodeY(m) <= y) lo = m else hi = m - 1 }
            return lo
        }
        private fun radius(level: Int) = if (level % 5 == 0) 74f else 56f

        private fun hit(x: Float, y: Float): Int? {
            val approx = levelAt(y)
            for (lv in (approx - 2)..(approx + 2)) {
                if (lv !in 1..500) continue
                val dx = x - nodeX(lv); val dy = y - nodeY(lv)
                val r = radius(lv) + 14f
                if (dx * dx + dy * dy <= r * r) return lv
            }
            return null
        }

        override fun draw(batch: Batch, parentAlpha: Float) {
            val c = cull ?: Rectangle(0f, 0f, width, height)
            val visBottom = c.y - 200f
            val visTop = c.y + c.height + 200f
            val ox = x; val oy = y
            val assets = game.assets
            // 1) dünya arka planları
            for (w in 1..20) {
                val sy = worldStart(w)
                val ey = worldEnd(w)
                if (ey < visBottom || sy > visTop) continue
                val tex = assets.background("bg/world_$w.jpg")
                if (tex != null) {
                    batch.setColor(1f, 1f, 1f, parentAlpha)
                    val th = width * tex.height / tex.width
                    var yy = sy
                    var flip = false
                    while (yy < ey) {
                        val hh = minOf(th, ey - yy)
                        if (yy + hh >= visBottom && yy <= visTop) {
                            val srcH = (tex.height * hh / th).toInt()
                            // aynalanarak tekrar: kenarlarda dikiş görünmesin
                            batch.draw(tex, ox, oy + yy, width, hh, 0, if (flip) 0 else tex.height - srcH, tex.width, srcH, false, flip)
                        }
                        yy += th; flip = !flip
                    }
                } else {
                    val wi = AreaCatalog.world(w)
                    batch.setColor(Pal.hex(wi.skyBottom))
                    batch.draw(assets.region("white"), ox, oy + sy, width, ey - sy)
                }
                // yumuşak beyaz pus: düğmeler okunaklı olsun
                batch.setColor(1f, 1f, 1f, 0.18f * parentAlpha)
                batch.draw(assets.region("white"), ox, oy + sy, width, ey - sy)
            }
            // 2) yol: düğümler arası noktalı iz
            val first = (levelAt(visBottom) - 1).coerceIn(1, 500)
            val last = (levelAt(visTop) + 1).coerceIn(1, 500)
            val circle = assets.region("circle")
            for (lv in first until minOf(500, last + 1)) {
                val x1 = nodeX(lv); val y1 = nodeY(lv); val x2 = nodeX(lv + 1); val y2 = nodeY(lv + 1)
                val done = lv < session.data.unlockedLevel
                val n = 7
                for (k in 1 until n) {
                    val f = k / n.toFloat()
                    val px = MathUtils.lerp(x1, x2, f); val py = MathUtils.lerp(y1, y2, f)
                    batch.setColor(0.15f, 0.22f, 0.35f, 0.25f * parentAlpha)
                    batch.draw(circle, ox + px - 13f, oy + py - 16f, 26f, 26f)
                    if (done) batch.setColor(1f, 0.85f, 0.3f, parentAlpha) else batch.setColor(1f, 1f, 1f, 0.95f * parentAlpha)
                    batch.draw(circle, ox + px - 11f, oy + py - 11f, 22f, 22f)
                }
            }
            // 3) dünya başlıkları
            val fS = ui.font(Ui.Size.S); val fM = ui.font(Ui.Size.M); val fL = ui.font(Ui.Size.L)
            for (w in 1..20) {
                val by = bannerY(w)
                if (by < visBottom - 100f || by > visTop + 100f) continue
                val wi = AreaCatalog.world(w)
                batch.setColor(1f, 1f, 1f, 0.92f * parentAlpha)
                ui.panel(Pal.WHITE).draw(batch, ox + 110f, oy + by - 40f, 500f, 80f)
                fM.color = Pal.TEXT
                layout.setText(fM, "Dünya $w · ${wi.name}")
                fM.draw(batch, layout, ox + width / 2 - layout.width / 2, oy + by + layout.height / 2)
            }
            // 4) bölüm düğmeleri
            val unlocked = session.data.unlockedLevel
            val current = session.currentLevel
            for (lv in first..minOf(500, last + 1)) {
                val cx = ox + nodeX(lv); val cy = oy + nodeY(lv)
                val r = radius(lv)
                val area = AreaCatalog.areaOfLevel(lv)
                val open = lv <= unlocked
                val isFinal = lv % 5 == 0
                val base = when { !open -> Pal.hex(0xB9C2D0); isFinal -> Pal.ORANGE; else -> Pal.hex(AreaCatalog.area(area).accent1) }
                // alan etiketi (alanın ilk bölümünde)
                if ((lv - 1) % 5 == 0) {
                    val a = AreaCatalog.area(area)
                    val text = "Alan ${a.id} · ${a.name}"
                    fS.color = if (open) Pal.TEXT else Pal.TEXT_SOFT
                    layout.setText(fS, text)
                    val lw = layout.width + 36f
                    val side = if (nodeX(lv) > width / 2) -1 else 1
                    val lx = if (side > 0) cx + r + 16f else cx - r - 16f - lw
                    batch.setColor(1f, 1f, 1f, 0.9f * parentAlpha)
                    ui.pill(Pal.WHITE).draw(batch, lx, cy - 24f, lw, 48f)
                    fS.draw(batch, layout, lx + 18f, cy + layout.height / 2)
                }
                // geçerli bölümde parlayan halka
                if (lv == current) {
                    val pulse = 1f + 0.12f * sin(t * 4f)
                    batch.setColor(1f, 0.95f, 0.5f, 0.55f * parentAlpha)
                    batch.draw(assets.region("glow"), cx - r * 2.1f * pulse, cy - r * 2.1f * pulse, r * 4.2f * pulse, r * 4.2f * pulse)
                }
                // gölge + kenar + gövde + parlama
                batch.setColor(0.15f, 0.22f, 0.35f, 0.3f * parentAlpha); batch.draw(circle, cx - r, cy - r - 8f, r * 2, r * 2)
                batch.setColor(1f, 1f, 1f, parentAlpha); batch.draw(circle, cx - r - 6f, cy - r - 6f, r * 2 + 12f, r * 2 + 12f)
                batch.setColor(Pal.darker(base, 0.85f)); batch.draw(circle, cx - r, cy - r - 4f, r * 2, r * 2)
                batch.setColor(base); batch.draw(circle, cx - r, cy - r + 2f, r * 2, r * 2 - 4f)
                batch.setColor(1f, 1f, 1f, 0.35f * parentAlpha); batch.draw(circle, cx - r * 0.55f, cy + r * 0.15f, r * 1.1f, r * 0.6f)
                if (!open) {
                    batch.setColor(1f, 1f, 1f, parentAlpha)
                    batch.draw(assets.region("lock"), cx - r * 0.55f, cy - r * 0.55f, r * 1.1f, r * 1.1f)
                } else {
                    val f = fL
                    val num = lv.toString()
                    layout.setText(f, num)
                    val tw = layout.width; val th = layout.height
                    // koyu gölgeli yazı (renk her çizimden önce verilir)
                    f.color = tmp.set(0.15f, 0.22f, 0.35f, 0.45f)
                    f.draw(batch, num, cx - tw / 2 + 2f, cy + th / 2 - 3f)
                    f.color = Pal.WHITE
                    f.draw(batch, num, cx - tw / 2, cy + th / 2)
                    // yıldızlar
                    val st = session.stars(lv)
                    for (k in 0 until 3) {
                        val sx = cx + (k - 1) * 34f - 17f
                        val sy = cy - r - 30f + if (k == 1) -6f else 0f
                        batch.setColor(1f, 1f, 1f, parentAlpha)
                        batch.draw(assets.region(if (k < st) "star_full" else "star_empty"), sx, sy, 34f, 34f)
                    }
                }
                if (isFinal) {
                    batch.setColor(1f, 1f, 1f, parentAlpha)
                    batch.draw(assets.region("crown"), cx - 34f, cy + r - 14f, 68f, 68f)
                }
                // oyuncunun karakteri geçerli bölümün üstünde zıplar
                if (lv == current) {
                    val item = session.equipped(ShopCategory.CHARACTER)
                    val spr = assets.characterSprite(item.id)
                    val bob = kotlin.math.abs(sin(t * 3f)) * 14f
                    val cs = 112f
                    if (spr != null) {
                        batch.setColor(Pal.game[((t / 1.2f).toInt()) % 4])
                        batch.draw(spr, cx - cs / 2, cy + r + 4f + bob, cs, cs)
                    } else {
                        batch.setColor(1f, 1f, 1f, parentAlpha)
                        batch.draw(assets.region("character"), cx - cs / 2, cy + r + 4f + bob, cs, cs)
                    }
                }
            }
            batch.setColor(1f, 1f, 1f, 1f)
            fS.color = Color.WHITE; fM.color = Color.WHITE; fL.color = Color.WHITE
        }
    }

    private fun openLevel(id: Int) {
        if (id > session.data.unlockedLevel) { ui.toast(stage, "Önce ${session.data.unlockedLevel}. bölümü geç", "lock"); return }
        val area = AreaCatalog.areaOfLevel(id)
        game.audio.play("button")
        when {
            !session.lives.canPlay -> game.go({ LivesScreen(game) })
            area !in session.data.areaIntroSeen -> game.go({ AreaIntroScreen(game, area) })
            id % 5 == 0 -> game.go({ AreaFinalScreen(game, id) })
            else -> game.go({ GameScreen(game, Mode.LEVEL, id) })
        }
    }
}
