package com.yuruyensehir.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.yuruyensehir.game.ui.Palette
import com.yuruyensehir.logic.Biome
import com.yuruyensehir.logic.BuildingType
import com.yuruyensehir.logic.CityGrid
import com.yuruyensehir.logic.GameState
import com.yuruyensehir.logic.PlacedBuilding
import com.yuruyensehir.logic.BalanceReport
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Yerleştirme önizlemesi. */
data class Ghost(val type: BuildingType, val x: Int, val y: Int, val valid: Boolean)

/**
 * Şehir görünümünü çizer: zemin, dev taşıyıcı, sırtındaki şehir, insanlar ve efektler.
 * Mantık ızgarasında +x taşıyıcının önü, +y solu. Ekranda ön = sağ-aşağı, sol = sağ-yukarı.
 */
class CityRenderer {
    companion object {
        const val DECK_Y = 130f
        const val RIM = 30f
        val GW = CityGrid.GRID_W.toFloat()

        /** Mantık ızgarası -> izometrik (u, v). */
        fun isoU(@Suppress("UNUSED_PARAMETER") x: Float, y: Float) = y
        fun isoV(x: Float) = GW - x
        fun worldX(x: Float, y: Float) = Iso.x(isoU(x, y), isoV(x))
        fun worldY(x: Float, y: Float) = Iso.y(isoU(x, y), isoV(x))
    }

    private class Person(var x: Float, var y: Float, var tx: Float, var ty: Float, var wait: Float, val color: Color)
    private class Puff(var x: Float = 0f, var y: Float = 0f, var life: Float = 0f, var size: Float = 0f)

    private val people = ArrayList<Person>()
    private val puffs = Array(80) { Puff() }
    private var puffTimer = 0f
    private var walkPhase = 0f
    private var scroll = 0f
    private var blinkTimer = 0f
    private val tmpColor = Color()
    private val tmpColor2 = Color()
    private val tmpV = Vector2()
    var deckOffset = DECK_Y
        private set

    fun update(state: GameState, dt: Float, moving: Boolean, speed: Float, graphicsHigh: Boolean) {
        if (moving) {
            walkPhase += dt * (2.2f + speed * 1.2f)
            scroll += dt * 26f * (0.6f + speed)
        } else {
            walkPhase = MathUtils.lerp(walkPhase, (walkPhase / MathUtils.PI).toInt() * MathUtils.PI, dt * 2f)
        }
        blinkTimer += dt
        val target = if (graphicsHigh) minOf(state.population.count, 26) else minOf(state.population.count, 8)
        while (people.size < target) people += newPerson(state)
        while (people.size > target) people.removeAt(people.size - 1)
        for (p in people) {
            if (p.wait > 0) { p.wait -= dt; continue }
            val dx = p.tx - p.x; val dy = p.ty - p.y
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            if (d < 0.05f) {
                p.wait = MathUtils.random(0.5f, 3f)
                pickTarget(state, p)
            } else {
                val s = minOf(d, dt * 0.9f)
                p.x += dx / d * s; p.y += dy / d * s
            }
        }
        for (pf in puffs) if (pf.life > 0) { pf.life -= dt; pf.y += dt * 18f; pf.x -= dt * 6f; pf.size += dt * 3f }
        if (graphicsHigh) {
            puffTimer += dt
            if (puffTimer > 0.35f) {
                puffTimer = 0f
                for (b in state.buildings) {
                    if (b.underConstruction || !b.enabled || b.workers == 0) continue
                    if (b.type == BuildingType.POWER_PLANT || b.type == BuildingType.METALWORKS || b.type == BuildingType.WORKSHOP || b.type == BuildingType.FOOD_FACTORY) {
                        if (MathUtils.randomBoolean(0.5f)) spawnPuff(b)
                    }
                }
            }
        }
    }

    private fun spawnPuff(b: PlacedBuilding) {
        val st = BuildingArt.style(b.type)
        val u0 = b.y.toFloat(); val v0 = GW - b.x - b.w
        // Bacanın yaklaşık konumu (BuildingArt'taki gibi)
        val tdx = Iso.x(u0 + 0.09f, v0 + b.w - 0.09f); val tdy = Iso.y(u0 + 0.09f, v0 + b.w - 0.09f)
        val tcx = Iso.x(u0 + b.h - 0.09f, v0 + b.w - 0.09f); val tcy = Iso.y(u0 + b.h - 0.09f, v0 + b.w - 0.09f)
        val h = st.height * (1f + 0.15f * (b.level - 1))
        val p = puffs.firstOrNull { it.life <= 0 } ?: return
        p.x = tdx + (tcx - tdx) * 0.3f; p.y = tdy + (tcy - tdy) * 0.3f + h + 18f
        p.life = 2.2f; p.size = 4f
    }

    private fun newPerson(state: GameState): Person {
        val col = listOf(Color.valueOf("E9724C"), Color.valueOf("2EC4B6"), Color.valueOf("F6C445"), Color.valueOf("8E7CC3"), Color.valueOf("6DBE6A")).random()
        val p = Person(CityGrid.CENTER_X.toFloat(), CityGrid.CENTER_Y.toFloat(), 0f, 0f, MathUtils.random(0f, 2f), col)
        pickTarget(state, p)
        p.x = p.tx; p.y = p.ty
        return p
    }

    private fun pickTarget(state: GameState, p: Person) {
        val b = state.buildings.randomOrNull()
        if (b != null && MathUtils.randomBoolean(0.7f)) {
            // Binanın önündeki kenar
            p.tx = b.x + b.w + 0.25f - MathUtils.random(0f, 0.3f)
            p.ty = b.y + MathUtils.random(0f, b.h.toFloat())
        } else {
            for (i in 0 until 10) {
                val x = MathUtils.random(0, CityGrid.GRID_W - 1); val y = MathUtils.random(0, CityGrid.GRID_H - 1)
                if (CityGrid.usable(state, x, y) && state.buildingAt(x, y) == null) { p.tx = x + 0.5f; p.ty = y + 0.5f; return }
            }
        }
        if (!CityGrid.usable(state, p.tx.toInt(), p.ty.toInt())) { p.tx = CityGrid.CENTER_X.toFloat(); p.ty = CityGrid.CENTER_Y.toFloat() + 2.5f }
    }

    /** Ekran dünyası -> mantık ızgarası (güverte düzleminde). */
    fun pickCell(wx: Float, wy: Float): Pair<Int, Int> {
        Iso.toGrid(wx, wy - deckOffset, tmpV)
        val u = tmpV.x; val v = tmpV.y
        return floor(GW - v).toInt() to floor(u).toInt()
    }

    fun groundColor(state: GameState): Color {
        val here = state.world.biomeAt(state.travel.position)
        val t = state.travel
        val c = tmpColor2.set(Palette.biome(here))
        if (t.traveling) {
            val next = state.world.biomeAt(t.route[t.routeIndex])
            c.lerp(Palette.biome(next), t.progress.toFloat())
        }
        return c
    }

    fun render(
        sr: ShapeRenderer, cam: OrthographicCamera, state: GameState, time: Float, moving: Boolean,
        ghost: Ghost?, selectedId: Int, movingId: Int, balance: BalanceReport, showWeight: Boolean, graphicsHigh: Boolean,
    ) {
        val resting = state.carrier.resting || state.carrier.forcedRest
        val bob = if (moving) abs(sin(walkPhase)) * 3.5f else sin(time * 1.4f) * 1.6f
        deckOffset = DECK_Y + bob - if (resting) 14f else 0f

        drawGround(sr, cam, state, time, graphicsHigh)
        drawCarrier(sr, state, time, moving, resting)
        drawDeck(sr, state, time)

        // Binalar uzaktan yakına
        val list = state.buildings.sortedByDescending { b -> b.y + (GW - b.x - b.w) + (b.w + b.h) / 2f }
        for (b in list) {
            if (b.id == movingId) continue
            val build = if (b.underConstruction && b.constructionTotal > 0) (1f - (b.constructionLeft / b.constructionTotal).toFloat()) else 1f
            val active = !b.underConstruction && b.enabled && (b.def.workers == 0 || b.workers > 0)
            BuildingArt.draw(
                sr, b.type, b.y, (GW - b.x - b.w).toInt(), b.h, b.w, b.level, b.hp.toFloat(), deckOffset, time,
                build = if (b.underConstruction && b.level == 1) build else 1f, active = active,
                tint = if (!b.enabled) Color.LIGHT_GRAY else null,
            )
            if (b.underConstruction) drawProgress(sr, b, build)
            if (b.id == selectedId) outline(sr, b.x, b.y, b.w, b.h, Palette.gold, 3f)
        }

        drawPeople(sr)
        for (pf in puffs) if (pf.life > 0) {
            sr.setColor(0.95f, 0.95f, 0.95f, (pf.life / 2.2f) * 0.7f)
            sr.circle(pf.x, pf.y, pf.size, 10)
        }

        if (ghost != null) {
            val col = if (ghost.valid) Color(0.3f, 0.85f, 0.45f, 0.45f) else Color(0.95f, 0.35f, 0.3f, 0.5f)
            val def = ghost.type.def
            for (cx in ghost.x until ghost.x + def.w) for (cy in ghost.y until ghost.y + def.h) tile(sr, cx, cy, col, 0.02f)
            BuildingArt.draw(sr, ghost.type, ghost.y, (GW - ghost.x - def.w).toInt(), def.h, def.w, 1, 100f, deckOffset, time, alpha = 0.7f)
            outline(sr, ghost.x, ghost.y, def.w, def.h, if (ghost.valid) Palette.turquoise else Palette.red, 3f)
        }

        if (showWeight) drawBalanceMarker(sr, state, balance)
        if (graphicsHigh) drawWeather(sr, cam, state, time)
    }

    private fun drawGround(sr: ShapeRenderer, cam: OrthographicCamera, state: GameState, time: Float, high: Boolean) {
        val w = cam.viewportWidth * cam.zoom; val h = cam.viewportHeight * cam.zoom
        val left = cam.position.x - w / 2; val bottom = cam.position.y - h / 2
        val base = groundColor(state)
        sr.color = base
        sr.rect(left - 10, bottom - 10, w + 20, h + 20)
        val biome = state.world.biomeAt(state.travel.position)
        // Zemin dokusu: kaydırılan karo deseni (ön = sağ-aşağı, zemin sola-yukarı kayar)
        val tile = 96f
        val sx = -scroll * 0.7071f; val sy = scroll * 0.7071f
        val i0 = floor((left - sx) / tile).toInt() - 1; val i1 = floor((left + w - sx) / tile).toInt() + 1
        val j0 = floor((bottom - sy) / tile).toInt() - 1; val j1 = floor((bottom + h - sy) / tile).toInt() + 1
        val step = if (high) 1 else 2
        var i = i0
        while (i <= i1) {
            var j = j0
            while (j <= j1) {
                val hsh = state.world.hash01(i, j, 5)
                val px = i * tile + sx + (state.world.hash01(i, j, 6) * tile).toFloat()
                val py = j * tile + sy + (state.world.hash01(i, j, 7) * tile).toFloat()
                // Taşıyıcının altına düşenleri çizme (gölge altı)
                decoration(sr, biome, hsh.toFloat(), px, py, time, base)
                j += step
            }
            i += step
        }
        // Taşıyıcı gölgesi
        val gx = worldX(CityGrid.CENTER_X.toFloat(), CityGrid.CENTER_Y.toFloat()); val gy = worldY(CityGrid.CENTER_X.toFloat(), CityGrid.CENTER_Y.toFloat())
        sr.setColor(0.2f, 0.25f, 0.15f, 0.22f)
        Draw.ellipseFan(sr, gx + 20, gy - 30, 300f, 130f, 32)
    }

    private fun decoration(sr: ShapeRenderer, biome: Biome, h: Float, x: Float, y: Float, time: Float, base: Color) {
        val c = tmpColor
        when {
            h < 0.30f -> { // çimen / kum dalgası
                c.set(base).mul(0.88f, 0.92f, 0.86f, 1f); sr.color = c
                sr.rectLine(x, y, x - 4, y + 9, 2f); sr.rectLine(x + 4, y, x + 6, y + 11, 2f); sr.rectLine(x + 8, y, x + 7, y + 7, 2f)
            }
            h < 0.48f -> when (biome) {
                Biome.FOREST, Biome.MYSTIC_FOREST, Biome.PLAINS, Biome.SWAMP -> tree(sr, x, y, biome == Biome.MYSTIC_FOREST, time)
                Biome.DESERT -> { sr.setColor(0.45f, 0.7f, 0.4f, 1f); sr.rectLine(x, y, x, y + 26, 7f); sr.rectLine(x - 8, y + 12, x - 8, y + 20, 5f); sr.rectLine(x - 8, y + 12, x, y + 12, 5f) }
                Biome.SNOW -> { sr.setColor(1f, 1f, 1f, 1f); Draw.ellipseFan(sr, x, y + 4, 16f, 7f, 12) }
                Biome.CRYSTAL_VALLEY, Biome.STORMLANDS -> crystal(sr, x, y, time)
                Biome.RUINS -> { sr.setColor(0.85f, 0.78f, 0.62f, 1f); sr.rect(x - 5, y, 10f, 30f); sr.rect(x - 8, y + 30, 16f, 5f) }
                Biome.VOLCANIC -> { sr.setColor(0.45f, 0.35f, 0.32f, 1f); sr.triangle(x - 14, y, x + 14, y, x, y + 20); sr.setColor(1f, 0.55f, 0.25f, 0.6f + 0.3f * sin(time * 3 + x)); sr.circle(x, y + 18, 3f, 8) }
                Biome.LAKE -> { sr.setColor(0.6f, 0.85f, 0.96f, 1f); Draw.ellipseFan(sr, x, y, 34f, 14f, 16); sr.setColor(1f, 1f, 1f, 0.5f); sr.rectLine(x - 10, y + 2, x + 6, y + 2, 1.5f) }
                Biome.MOUNTAINS -> rock(sr, x, y, 1.4f)
            }
            h < 0.58f -> rock(sr, x, y, 0.8f)
            h < 0.64f && (biome == Biome.PLAINS || biome == Biome.MYSTIC_FOREST || biome == Biome.FOREST) -> { // çiçek
                sr.setColor(1f, 0.85f, 0.4f, 1f); sr.circle(x, y + 6, 3f, 8)
                sr.setColor(1f, 0.6f, 0.7f, 1f); sr.circle(x + 8, y + 3, 3f, 8)
            }
        }
    }

    private fun tree(sr: ShapeRenderer, x: Float, y: Float, mystic: Boolean, time: Float) {
        sr.setColor(0.55f, 0.38f, 0.22f, 1f); sr.rectLine(x, y, x, y + 18, 5f)
        val sway = sin(time * 1.2f + x * 0.05f) * 1.5f
        if (mystic) sr.setColor(0.45f, 0.78f, 0.7f, 1f) else sr.setColor(0.38f, 0.68f, 0.36f, 1f)
        sr.circle(x + sway, y + 26, 13f, 14); sr.circle(x - 8 + sway, y + 20, 9f, 12); sr.circle(x + 8 + sway, y + 20, 9f, 12)
        if (mystic) { sr.setColor(0.85f, 1f, 0.9f, 0.6f + 0.4f * sin(time * 2 + y)); sr.circle(x + 5, y + 30, 2.2f, 8) }
    }

    private fun rock(sr: ShapeRenderer, x: Float, y: Float, s: Float) {
        sr.setColor(0.66f, 0.64f, 0.62f, 1f); Draw.ellipseFan(sr, x, y + 5 * s, 12f * s, 8f * s, 12)
        sr.setColor(0.78f, 0.76f, 0.74f, 1f); Draw.ellipseFan(sr, x - 3 * s, y + 8 * s, 6f * s, 4f * s, 10)
    }

    private fun crystal(sr: ShapeRenderer, x: Float, y: Float, time: Float) {
        val g = 0.8f + 0.2f * sin(time * 2f + x)
        sr.setColor(0.72f * g, 0.6f * g, 1f * g, 1f)
        sr.triangle(x - 6, y, x + 6, y, x, y + 28); sr.triangle(x + 4, y, x + 14, y, x + 10, y + 18)
        sr.setColor(1f, 1f, 1f, 0.6f); sr.triangle(x - 2, y + 4, x + 1, y + 4, x, y + 20)
    }

    private fun drawCarrier(sr: ShapeRenderer, state: GameState, time: Float, moving: Boolean, resting: Boolean) {
        val cx = CityGrid.CENTER_X.toFloat(); val cy = CityGrid.CENTER_Y.toFloat()
        val rx = CityGrid.radiusX(state).toFloat() + 0.6f; val ry = CityGrid.radiusY(state).toFloat() + 0.6f
        val skin = Color.valueOf("8CCBA4"); val skinDark = Color.valueOf("6AAE88"); val nail = Color.valueOf("FFF3DA")
        val legH = deckOffset - RIM + 8f

        data class Leg(val x: Float, val y: Float, val phase: Float)
        val legs = listOf(Leg(cx + rx * 0.55f, cy + ry * 0.78f, 0f), Leg(cx + rx * 0.55f, cy - ry * 0.78f, MathUtils.PI),
            Leg(cx - rx * 0.55f, cy + ry * 0.78f, MathUtils.PI), Leg(cx - rx * 0.55f, cy - ry * 0.78f, 0f))
        // Uzaklık: u + v büyük olan geride
        val sorted = legs.sortedByDescending { it.y + (GW - it.x) }
        fun drawLeg(l: Leg) {
            val lift = if (moving) maxOf(0f, sin(walkPhase + l.phase)) * 12f else 0f
            val fwd = if (moving) cos(walkPhase + l.phase) * 0.35f else 0f
            val gx = worldX(l.x + fwd, l.y); val gy = worldY(l.x + fwd, l.y)
            val top = worldY(l.x, l.y) + legH
            val restDrop = if (resting) 0.55f else 1f
            sr.color = skinDark
            Draw.ellipseFan(sr, gx, gy + lift, 26f, 12f, 16)
            sr.color = skin
            sr.rect(gx - 24f, gy + lift, 48f, (top - gy - lift) * restDrop + 4f)
            sr.color = skinDark
            Draw.ellipseFan(sr, gx, gy + lift, 24f, 10f, 16)
            sr.color = nail
            for (k in -1..1) sr.circle(gx + k * 11f, gy + lift - 6f, 4.5f, 8)
        }
        drawLeg(sorted[0]); drawLeg(sorted[1])

        // Kuyruk (arka)
        val tx = worldX(cx - rx - 0.4f, cy); val ty = worldY(cx - rx - 0.4f, cy) + deckOffset * 0.45f
        sr.color = skin
        sr.triangle(tx + 30, ty + 12, tx + 30, ty - 12, tx - 18 + sin(time * 2f) * 4, ty - 2)

        // Karın
        sr.color = Color.valueOf("E9DDB8")
        Draw.ellipseFan(sr, worldX(cx, cy), worldY(cx, cy) + deckOffset * 0.42f, Iso.HW * (rx + ry) * 0.86f, Iso.HH * (rx + ry) * 0.7f, 32)

        drawLeg(sorted[2]); drawLeg(sorted[3])

        // Boyun ve baş (ön = sağ-aşağı)
        val nodX = if (moving) sin(walkPhase * 2f) * 3f else 0f
        val headLift = if (resting) -34f else sin(time * 1.4f) * 3f
        val nbx = worldX(cx + rx - 0.8f, cy - 0.4f); val nby = worldY(cx + rx - 0.8f, cy - 0.4f) + deckOffset * 0.55f
        val hx = worldX(cx + rx + 1.6f, cy - 0.6f) + nodX; val hy = worldY(cx + rx + 1.6f, cy - 0.6f) + deckOffset * 0.62f + headLift
        sr.color = skin
        sr.rectLine(nbx, nby, hx, hy, 44f)
        sr.circle(hx, hy, 40f, 24)
        sr.color = skinDark
        Draw.ellipseFan(sr, hx + 26f, hy - 12f, 26f, 18f, 18) // burun
        sr.color = skin
        Draw.ellipseFan(sr, hx + 24f, hy - 8f, 24f, 16f, 18)
        sr.color = Color.valueOf("5B8F72")
        sr.circle(hx + 40f, hy - 6f, 3f, 8); sr.circle(hx + 32f, hy - 3f, 3f, 8)
        // Küçük boynuz tomurcukları ve yaprak tutamı
        sr.color = Color.valueOf("F3E3C3")
        sr.triangle(hx - 14, hy + 32, hx - 4, hy + 36, hx - 12, hy + 50)
        sr.triangle(hx + 6, hy + 36, hx + 16, hy + 32, hx + 12, hy + 48)
        sr.color = Color.valueOf("7BC86C")
        sr.triangle(hx - 4, hy + 38, hx + 4, hy + 38, hx - 10 + sin(time * 2) * 3, hy + 56)
        // Yanak
        sr.setColor(1f, 0.6f, 0.6f, 0.45f)
        sr.circle(hx + 8f, hy - 16f, 7f, 12)
        // Göz
        val blinking = resting || (blinkTimer % 4.2f) < 0.14f
        if (blinking) {
            sr.color = Color.valueOf("3C4A40")
            sr.rectLine(hx + 2, hy + 6, hx + 20, hy + 6, 3f)
        } else {
            sr.color = Color.WHITE
            sr.circle(hx + 11f, hy + 8f, 11f, 16)
            sr.color = Color.valueOf("2B3A33")
            sr.circle(hx + 14f, hy + 7f, 6.5f, 14)
            sr.color = Color.WHITE
            sr.circle(hx + 16f, hy + 10f, 2.4f, 8)
        }
        if (resting) {
            sr.setColor(0.4f, 0.5f, 0.7f, 0.5f + 0.5f * sin(time * 2))
            val zy = hy + 50 + (time * 12f) % 30f
            sr.rectLine(hx + 30, zy, hx + 40, zy, 2f); sr.rectLine(hx + 40, zy, hx + 30, zy - 8, 2f); sr.rectLine(hx + 30, zy - 8, hx + 40, zy - 8, 2f)
        }

        // Kabuk kenarı (yan bant) ve güverte
        val seg = 48
        val top = FloatArray(seg * 2); val bot = FloatArray(seg * 2)
        for (i in 0 until seg) {
            val a = i.toFloat() / seg * MathUtils.PI2
            val gx = cx + rx * cos(a); val gy = cy + ry * sin(a)
            top[i * 2] = worldX(gx, gy); top[i * 2 + 1] = worldY(gx, gy) + deckOffset
            bot[i * 2] = top[i * 2]; bot[i * 2 + 1] = top[i * 2 + 1] - RIM
        }
        val rim = Color.valueOf("C79A62"); val rimDark = Color.valueOf("A97C47")
        for (i in 0 until seg) {
            val j = (i + 1) % seg
            sr.color = if ((i / 4) % 2 == 0) rim else rimDark
            Draw.quad(sr, bot[i * 2], bot[i * 2 + 1], bot[j * 2], bot[j * 2 + 1], top[j * 2], top[j * 2 + 1], top[i * 2], top[i * 2 + 1])
        }
        // Kabuk alt kenarındaki pul çizgisi
        sr.color = Color.valueOf("8E6A3E")
        for (i in 0 until seg) {
            val j = (i + 1) % seg
            sr.rectLine(bot[i * 2], bot[i * 2 + 1], bot[j * 2], bot[j * 2 + 1], 3f)
        }
        val ccx = worldX(cx, cy); val ccy = worldY(cx, cy) + deckOffset
        sr.color = Color.valueOf("B98B5E")
        for (i in 0 until seg) {
            val j = (i + 1) % seg
            sr.triangle(ccx, ccy, top[i * 2], top[i * 2 + 1], top[j * 2], top[j * 2 + 1])
        }
    }

    private fun drawDeck(sr: ShapeRenderer, state: GameState, time: Float) {
        val light = Color.valueOf("BFE59A"); val dark = Color.valueOf("AEDB8A")
        for (x in 0 until CityGrid.GRID_W) for (y in 0 until CityGrid.GRID_H) {
            if (!CityGrid.usable(state, x, y)) continue
            tile(sr, x, y, if ((x + y) % 2 == 0) light else dark, 0.0f)
        }
    }

    private fun tile(sr: ShapeRenderer, x: Int, y: Int, c: Color, inset: Float) {
        val u0 = y + inset; val u1 = y + 1 - inset; val v0 = GW - x - 1 + inset; val v1 = GW - x - inset
        sr.color = c
        Draw.quad(sr,
            Iso.x(u0, v0), Iso.y(u0, v0) + deckOffset, Iso.x(u1, v0), Iso.y(u1, v0) + deckOffset,
            Iso.x(u1, v1), Iso.y(u1, v1) + deckOffset, Iso.x(u0, v1), Iso.y(u0, v1) + deckOffset)
    }

    fun outline(sr: ShapeRenderer, x: Int, y: Int, w: Int, h: Int, c: Color, width: Float) {
        val u0 = y.toFloat(); val u1 = (y + h).toFloat(); val v0 = GW - x - w; val v1 = GW - x
        val pts = listOf(u0 to v0, u1 to v0, u1 to v1, u0 to v1)
        sr.color = c
        for (i in 0 until 4) {
            val (a, b) = pts[i]; val (c2, d) = pts[(i + 1) % 4]
            sr.rectLine(Iso.x(a, b), Iso.y(a, b) + deckOffset, Iso.x(c2, d), Iso.y(c2, d) + deckOffset, width)
        }
    }

    private fun drawProgress(sr: ShapeRenderer, b: PlacedBuilding, f: Float) {
        val x = worldX(b.x + b.w / 2f, b.y + b.h / 2f); val y = worldY(b.x + b.w / 2f, b.y + b.h / 2f) + deckOffset + 52f
        sr.setColor(1f, 1f, 1f, 0.9f); sr.rect(x - 22, y, 44f, 7f)
        sr.color = Palette.turquoise; sr.rect(x - 21, y + 1, 42f * f.coerceIn(0f, 1f), 5f)
    }

    private fun drawPeople(sr: ShapeRenderer) {
        for (p in people.sortedByDescending { it.y + (GW - it.x) }) {
            val x = worldX(p.x, p.y); val y = worldY(p.x, p.y) + deckOffset
            val step = if (p.wait <= 0) abs(sin((p.x + p.y) * 9f)) * 1.5f else 0f
            sr.setColor(0.2f, 0.2f, 0.1f, 0.25f); Draw.ellipseFan(sr, x, y, 4f, 2f, 8)
            sr.color = p.color; sr.rect(x - 2.5f, y + step, 5f, 8f)
            sr.setColor(1f, 0.85f, 0.7f, 1f); sr.circle(x, y + 10.5f + step, 3f, 8)
        }
    }

    private fun drawBalanceMarker(sr: ShapeRenderer, state: GameState, r: BalanceReport) {
        val cx = CityGrid.CENTER_X.toFloat(); val cy = CityGrid.CENTER_Y.toFloat()
        val mx = cx + (r.offsetX * CityGrid.radiusX(state)).toFloat(); val my = cy + (r.offsetY * CityGrid.radiusY(state)).toFloat()
        val ax = worldX(cx, cy); val ay = worldY(cx, cy) + deckOffset
        val bx = worldX(mx, my); val by = worldY(mx, my) + deckOffset
        val col = when { r.balance > 0.85 -> Palette.green; r.balance > 0.6 -> Palette.gold; else -> Palette.red }
        sr.setColor(1f, 1f, 1f, 0.8f); sr.circle(ax, ay, 6f, 12)
        sr.color = col
        sr.rectLine(ax, ay, bx, by, 3f)
        sr.circle(bx, by, 9f, 16)
        sr.setColor(1f, 1f, 1f, 1f); sr.circle(bx, by, 4f, 10)
    }

    private fun drawWeather(sr: ShapeRenderer, cam: OrthographicCamera, state: GameState, time: Float) {
        val biome = state.world.biomeAt(state.travel.position)
        if (biome != Biome.SNOW && biome != Biome.STORMLANDS) return
        val w = cam.viewportWidth * cam.zoom; val h = cam.viewportHeight * cam.zoom
        val left = cam.position.x - w / 2; val bottom = cam.position.y - h / 2
        for (i in 0 until 70) {
            val fx = ((i * 97.13f + time * (if (biome == Biome.SNOW) 14f else 60f)) % w)
            val fy = h - ((i * 53.7f + time * (if (biome == Biome.SNOW) 40f else 420f)) % h)
            if (biome == Biome.SNOW) { sr.setColor(1f, 1f, 1f, 0.85f); sr.circle(left + fx + sin(time + i) * 6, bottom + fy, 2.5f, 6) }
            else { sr.setColor(0.6f, 0.7f, 0.85f, 0.5f); sr.rectLine(left + fx, bottom + fy, left + fx - 6, bottom + fy - 18, 1.5f) }
        }
    }
}
