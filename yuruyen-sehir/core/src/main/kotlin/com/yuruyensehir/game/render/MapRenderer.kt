package com.yuruyensehir.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.yuruyensehir.game.ui.Palette
import com.yuruyensehir.logic.Biome
import com.yuruyensehir.logic.Cell
import com.yuruyensehir.logic.GameState
import com.yuruyensehir.logic.ProductionSystem
import com.yuruyensehir.logic.RouteOption
import kotlin.math.floor
import kotlin.math.sin

/** Kuşbakışı dünya haritası. Hücre (x, y) -> dünya (x * SIZE, y * SIZE). */
class MapRenderer {
    companion object { const val SIZE = 52f }

    private val fog = Color.valueOf("EFE6D2")
    private val fogDot = Color.valueOf("E3D7BE")
    private val tmp = Color()

    fun cellAt(wx: Float, wy: Float) = Cell(floor(wx / SIZE).toInt(), floor(wy / SIZE).toInt())

    fun render(sr: ShapeRenderer, cam: OrthographicCamera, state: GameState, time: Float, preview: RouteOption?, selected: Cell?) {
        val w = cam.viewportWidth * cam.zoom; val h = cam.viewportHeight * cam.zoom
        val left = cam.position.x - w / 2; val bottom = cam.position.y - h / 2
        sr.color = fog
        sr.rect(left - 4, bottom - 4, w + 8, h + 8)
        val x0 = floor(left / SIZE).toInt() - 1; val x1 = floor((left + w) / SIZE).toInt() + 1
        val y0 = floor(bottom / SIZE).toInt() - 1; val y1 = floor((bottom + h) / SIZE).toInt() + 1
        for (x in x0..x1) for (y in y0..y1) {
            val c = Cell(x, y)
            val px = x * SIZE; val py = y * SIZE
            if (c.key() !in state.travel.explored) {
                sr.color = fogDot
                if ((x + y) % 2 == 0) sr.circle(px + SIZE / 2, py + SIZE / 2, 2f, 6)
                continue
            }
            val b = state.world.biomeAt(c)
            sr.color = Palette.biome(b)
            sr.rect(px, py, SIZE, SIZE)
            icon(sr, b, px + SIZE / 2, py + SIZE / 2, time)
            // Kalan kaynak göstergesi
            val left01 = (ProductionSystem.depositLeft(state, c) / state.world.depositSize(c)).toFloat()
            if (left01 < 0.98f) {
                sr.setColor(0f, 0f, 0f, 0.15f); sr.rect(px + 6, py + 4, SIZE - 12, 4f)
                sr.color = Palette.gold; sr.rect(px + 6, py + 4, (SIZE - 12) * left01, 4f)
            }
        }
        // Izgara çizgileri
        sr.setColor(1f, 1f, 1f, 0.25f)
        for (x in x0..x1) sr.rect(x * SIZE, y0 * SIZE, 1f, (y1 - y0 + 1) * SIZE)
        for (y in y0..y1) sr.rect(x0 * SIZE, y * SIZE, (x1 - x0 + 1) * SIZE, 1f)

        // Aktif rota
        val t = state.travel
        if (t.traveling) drawPath(sr, listOf(t.position) + t.route.subList(t.routeIndex, t.route.size), Palette.turquoise, time)
        if (preview != null) drawPath(sr, listOf(t.position) + preview.cells, Palette.orange, time)
        selected?.let {
            sr.color = Palette.red
            val px = it.x * SIZE; val py = it.y * SIZE
            sr.rectLine(px, py, px + SIZE, py, 3f); sr.rectLine(px + SIZE, py, px + SIZE, py + SIZE, 3f)
            sr.rectLine(px + SIZE, py + SIZE, px, py + SIZE, 3f); sr.rectLine(px, py + SIZE, px, py, 3f)
            // bayrak
            sr.setColor(0.4f, 0.3f, 0.2f, 1f); sr.rectLine(px + SIZE / 2, py + 10, px + SIZE / 2, py + 44, 2.5f)
            sr.color = Palette.red; sr.triangle(px + SIZE / 2, py + 44, px + SIZE / 2 + 16, py + 38 + sin(time * 4) * 2, px + SIZE / 2, py + 32)
        }
        // Medeniyetler: çadır ve ilişki renginde bayrak
        for (civ in state.civs) {
            if (!civ.met) continue
            val c = civ.position
            if (c.x < x0 || c.x > x1 || c.y < y0 || c.y > y1) continue
            val px = c.x * SIZE + SIZE / 2; val py = c.y * SIZE + SIZE / 2
            sr.setColor(0f, 0f, 0f, 0.18f); Draw.ellipseFan(sr, px, py - 12, 18f, 6f, 12)
            sr.color = Color.valueOf("FFF1D6"); sr.triangle(px - 16, py - 12, px + 16, py - 12, px, py + 10)
            sr.color = Color.valueOf("B98B5E"); sr.triangle(px - 5, py - 12, px + 5, py - 12, px, py)
            val col = Palette.relation(civ.status)
            sr.setColor(0.4f, 0.3f, 0.2f, 1f); sr.rectLine(px + 8, py - 12, px + 8, py + 22, 2f)
            sr.color = col; sr.triangle(px + 8, py + 22, px + 24, py + 17 + sin(time * 4 + civ.id) * 2, px + 8, py + 12)
            if (civ.atWar) { sr.color = Palette.red; sr.rectLine(px - 14, py + 16, px - 4, py + 26, 2.5f); sr.rectLine(px - 14, py + 26, px - 4, py + 16, 2.5f) }
        }

        // Keşif ekipleri
        for (e in state.expeditions) {
            val px = e.target.x * SIZE + SIZE / 2; val py = e.target.y * SIZE + SIZE / 2
            sr.setColor(0.4f, 0.3f, 0.2f, 1f); sr.rectLine(px, py - 14, px, py + 14, 2f)
            sr.color = Palette.turquoise; sr.triangle(px, py + 14, px - 14, py + 9 + sin(time * 4) * 2, px, py + 4)
            val f = (1 - e.secondsLeft / e.totalSeconds).toFloat()
            val sx = t.position.x * SIZE + SIZE / 2; val sy = t.position.y * SIZE + SIZE / 2
            sr.setColor(1f, 1f, 1f, 0.9f); sr.circle(sx + (px - sx) * (if (f < 0.5f) f * 2 else (1 - f) * 2), sy + (py - sy) * (if (f < 0.5f) f * 2 else (1 - f) * 2), 4f, 8)
        }

        // Şehir konumu
        val pp = t.position
        val cx = pp.x * SIZE + SIZE / 2; val cy = pp.y * SIZE + SIZE / 2
        val pulse = 1f + 0.12f * sin(time * 3f)
        sr.setColor(1f, 1f, 1f, 0.7f); sr.circle(cx, cy, 22f * pulse, 20)
        sr.color = Color.valueOf("C79A62"); sr.circle(cx, cy, 16f, 18)
        sr.color = Color.valueOf("BFE59A"); sr.circle(cx, cy + 2, 11f, 16)
        sr.color = Color.valueOf("8CCBA4"); sr.circle(cx + 17f, cy - 4f, 6f, 12)
    }

    private fun drawPath(sr: ShapeRenderer, cells: List<Cell>, col: Color, time: Float) {
        if (cells.size < 2) return
        sr.color = col
        for (i in 0 until cells.size - 1) {
            val a = cells[i]; val b = cells[i + 1]
            sr.rectLine(a.x * SIZE + SIZE / 2, a.y * SIZE + SIZE / 2, b.x * SIZE + SIZE / 2, b.y * SIZE + SIZE / 2, 5f)
        }
        val k = ((time * 2f).toInt()) % cells.size
        val c = cells[k]
        sr.setColor(1f, 1f, 1f, 0.9f); sr.circle(c.x * SIZE + SIZE / 2, c.y * SIZE + SIZE / 2, 5f, 10)
        for (c2 in cells.drop(1)) { sr.color = col; sr.circle(c2.x * SIZE + SIZE / 2, c2.y * SIZE + SIZE / 2, 5f, 10) }
    }

    private fun icon(sr: ShapeRenderer, b: Biome, x: Float, y: Float, time: Float) {
        val c = tmp
        when (b) {
            Biome.PLAINS -> { sr.setColor(0.55f, 0.8f, 0.45f, 1f); sr.rectLine(x - 6, y - 4, x - 8, y + 4, 2f); sr.rectLine(x + 2, y - 4, x + 3, y + 5, 2f) }
            Biome.FOREST, Biome.MYSTIC_FOREST -> {
                if (b == Biome.MYSTIC_FOREST) sr.setColor(0.35f, 0.65f, 0.6f, 1f) else sr.setColor(0.3f, 0.6f, 0.32f, 1f)
                sr.triangle(x - 14, y - 8, x - 2, y - 8, x - 8, y + 10); sr.triangle(x, y - 6, x + 14, y - 6, x + 7, y + 14)
            }
            Biome.DESERT -> { sr.setColor(0.85f, 0.72f, 0.45f, 1f); sr.arc(x, y - 8, 14f, 0f, 180f, 10) }
            Biome.MOUNTAINS -> { sr.setColor(0.6f, 0.52f, 0.45f, 1f); sr.triangle(x - 16, y - 10, x + 6, y - 10, x - 5, y + 12); sr.setColor(1f, 1f, 1f, 1f); sr.triangle(x - 8, y + 4, x - 2, y + 4, x - 5, y + 12) }
            Biome.SWAMP -> { sr.setColor(0.45f, 0.62f, 0.5f, 1f); Draw.ellipseFan(sr, x, y, 14f, 6f, 12) }
            Biome.SNOW -> { sr.setColor(1f, 1f, 1f, 1f); sr.circle(x - 5, y, 6f, 10); sr.circle(x + 6, y - 2, 4f, 8) }
            Biome.VOLCANIC -> { sr.setColor(0.5f, 0.35f, 0.3f, 1f); sr.triangle(x - 14, y - 10, x + 14, y - 10, x, y + 10); sr.setColor(1f, 0.5f, 0.2f, 0.7f + 0.3f * sin(time * 3)); sr.circle(x, y + 10, 4f, 8) }
            Biome.CRYSTAL_VALLEY -> { c.set(0.62f, 0.5f, 0.95f, 1f); sr.color = c; sr.triangle(x - 6, y - 10, x + 6, y - 10, x, y + 14) }
            Biome.RUINS -> { sr.setColor(0.72f, 0.62f, 0.45f, 1f); sr.rect(x - 10, y - 10, 5f, 18f); sr.rect(x + 4, y - 10, 5f, 14f); sr.rect(x - 12, y + 8, 14f, 4f) }
            Biome.LAKE -> { sr.setColor(1f, 1f, 1f, 0.6f); sr.rectLine(x - 10, y, x + 2, y, 2f); sr.rectLine(x - 2, y - 6, x + 10, y - 6, 2f) }
            Biome.STORMLANDS -> { sr.setColor(1f, 0.9f, 0.3f, 1f); sr.triangle(x - 2, y + 12, x + 6, y + 2, x - 4, y + 2); sr.triangle(x + 4, y + 2, x - 6, y - 12, x + 2, y + 2) }
        }
    }
}
