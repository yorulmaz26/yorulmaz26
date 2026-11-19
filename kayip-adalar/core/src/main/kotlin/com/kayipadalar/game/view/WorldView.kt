package com.kayipadalar.game.view

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.kayipadalar.game.Assets
import com.kayipadalar.game.ui.Palette
import com.kayipadalar.game.ui.Theme
import com.kayipadalar.logic.kingdom.IslandState
import com.kayipadalar.logic.kingdom.IslandStatus
import com.kayipadalar.logic.kingdom.Kingdom
import kotlin.math.sin

/** Takımada haritası: ana krallık ortada, keşfedilecek adalar halkalar hâlinde çevresinde. */
class WorldView(private val k: Kingdom, private val assets: Assets, private val theme: Theme, private val now: () -> Long) : com.badlogic.gdx.scenes.scene2d.ui.Widget() {
    var onIsland: (IslandState) -> Unit = {}
    var onHome: () -> Unit = {}
    var selected: Int? = null
    private var t = 0f
    val cam = PanZoom(0.75f, 0.35f, 1.6f) { x, y -> tap(x, y) }
    private val layout = GlyphLayout()

    init { addListener(cam); cam.bounds = 1500f }

    fun pos(i: IslandState): Vector2 {
        val rr = 300f + (i.ring - 1) * 250f
        val a = i.angle * MathUtils.degreesToRadians
        return Vector2(MathUtils.cos(a) * rr, MathUtils.sin(a) * rr * 0.8f)
    }

    private fun toScreen(w: Vector2) = Vector2(x + width / 2 + cam.panX + w.x * cam.zoom, y + height / 2 + cam.panY + w.y * cam.zoom)
    private fun toWorld(lx: Float, ly: Float) = Vector2((lx - width / 2 - cam.panX) / cam.zoom, (ly - height / 2 - cam.panY) / cam.zoom)

    fun focus(i: IslandState) { val p = pos(i); cam.panX = -p.x * cam.zoom; cam.panY = -p.y * cam.zoom + 160f; cam.clamp() }

    private fun shown(i: IslandState) = i.id != 0 && i.status != IslandStatus.MERGED && i.ring <= k.visibleRing() + 1

    private fun tap(lx: Float, ly: Float) {
        val w = toWorld(lx, ly)
        if (w.len() < 160f) { onHome(); return }
        k.s.islands.filter { shown(it) }.minByOrNull { pos(it).dst(w) }?.let { if (pos(it).dst(w) < 120f) { selected = it.id; onIsland(it) } }
    }

    override fun act(delta: Float) { super.act(delta); t += delta }

    private fun draw(b: Batch, r: TextureRegion, w: Vector2, width: Float, alpha: Float = 1f, tint: Color = Color.WHITE) {
        val s = toScreen(w); val sw = width * cam.zoom; val sh = sw * r.regionHeight / r.regionWidth.coerceAtLeast(1)
        b.setColor(tint.r, tint.g, tint.b, alpha); b.draw(r, s.x - sw / 2, s.y - sh / 2, sw, sh)
    }

    private fun text(b: Batch, s: String, w: Vector2, c: Color, font: com.badlogic.gdx.graphics.g2d.BitmapFont = theme.small) {
        val p = toScreen(w)
        font.color = Color(0f, 0f, 0f, 0.6f); layout.setText(font, s)
        for ((dx, dy) in listOf(-2f to 0f, 2f to 0f, 0f to -2f, 0f to 2f)) font.draw(b, layout, p.x - layout.width / 2 + dx, p.y + layout.height / 2 + dy)
        font.color = c; layout.setText(font, s); font.draw(b, layout, p.x - layout.width / 2, p.y + layout.height / 2); font.color = Color.WHITE
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        validate()
        val n = now()
        val power = k.armyPower()
        // Ana krallık
        val homeP = Vector2(0f, sin(t) * 6f)
        draw(batch, assets["islands/home"], homeP, 260f)
        draw(batch, assets["buildings/hi_castle"], Vector2(homeP.x, homeP.y + 60f), 150f)
        text(batch, "Krallığın", Vector2(0f, -150f), Palette.gold, theme.body)
        val list = k.s.islands.filter { shown(it) }.sortedByDescending { pos(it).y }
        for (i in list) {
            val p = pos(i).add(0f, sin(t * 1.3f + i.id) * 6f)
            val locked = i.ring > k.visibleRing() - 0 && i.status == IslandStatus.HIDDEN && i.ring > k.levelOf("explore")
            if (selected == i.id) draw(batch, assets["effects/fx_portal1"], Vector2(p.x, p.y - 30f), 260f, 0.6f + 0.3f * sin(t * 4f))
            when (i.status) {
                IslandStatus.HIDDEN, IslandStatus.EXPLORING -> {
                    draw(batch, assets["weather/cloud"], p, 220f, if (locked) 0.55f else 0.95f)
                    text(batch, "?", Vector2(p.x, p.y + 4f), if (locked) Color.LIGHT_GRAY else Palette.navy, theme.title)
                    if (locked) text(batch, "Keşif Merkezi ${i.ring}", Vector2(p.x, p.y - 70f), Color(1f, 1f, 1f, 0.8f), theme.tiny)
                    if (i.status == IslandStatus.EXPLORING) {
                        val f = ((n - i.exploreStarted).toFloat() / (i.exploreUntil - i.exploreStarted).coerceAtLeast(1)).coerceIn(0f, 1f)
                        val ship = Vector2(homeP).lerp(p, f).add(0f, sin(t * 3f) * 4f)
                        draw(batch, assets["ships/ship_explore"], ship, 110f)
                        text(batch, "${((i.exploreUntil - n) / 1000).coerceAtLeast(0)}sn", Vector2(p.x, p.y - 72f), Color.WHITE)
                    }
                }
                IslandStatus.DISCOVERED -> {
                    draw(batch, assets[k.cfg.island(i.type).sprite], p, 200f)
                    draw(batch, assets["ships/ship_war"], Vector2(p.x + 85f, p.y - 40f + sin(t * 2f + i.id) * 5f), 90f, tint = Color(1f, 0.8f, 0.8f, 1f))
                    val strong = power >= i.guard
                    draw(batch, assets["icons/swords"], Vector2(p.x - 42f, p.y - 92f), 40f)
                    text(batch, i.guard.toString(), Vector2(p.x + 8f, p.y - 92f), if (strong) Color.valueOf("9CFF6B") else Color.valueOf("FF8A80"), theme.body)
                }
                IslandStatus.CLEARED -> {
                    draw(batch, assets[k.cfg.island(i.type).sprite], p, 200f)
                    draw(batch, assets["icons/check"], Vector2(p.x + 70f, p.y + 60f), 48f)
                    text(batch, "Çekilebilir", Vector2(p.x, p.y - 92f), Color.valueOf("C8FFB0"))
                }
                IslandStatus.MERGED -> {}
            }
        }
        batch.setColor(Color.WHITE)
    }
}
