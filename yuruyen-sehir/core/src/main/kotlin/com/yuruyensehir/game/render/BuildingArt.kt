package com.yuruyensehir.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.yuruyensehir.logic.BuildingType
import com.yuruyensehir.logic.BuildingType.*
import kotlin.math.cos
import kotlin.math.sin

enum class Roof { FLAT, PYRAMID, GABLE, DOME, FIELD, TOWER }

data class BuildingStyle(val height: Float, val wall: Color, val roof: Color, val roofKind: Roof, val roofHeight: Float = 14f)

object BuildingArt {
    private fun c(hex: String) = Color.valueOf(hex)

    fun style(t: BuildingType): BuildingStyle = when (t) {
        CITY_CENTER -> BuildingStyle(30f, c("FFF1D6"), c("F28C38"), Roof.PYRAMID, 26f)
        TOWN_HALL -> BuildingStyle(26f, c("FFF7E8"), c("2EC4B6"), Roof.GABLE, 16f)
        RESEARCH_CENTER -> BuildingStyle(22f, c("EAF4FF"), c("8FD3F0"), Roof.DOME, 18f)
        MAP_CENTER -> BuildingStyle(34f, c("FFEFD0"), c("E9724C"), Roof.TOWER, 18f)
        SMALL_HOUSE -> BuildingStyle(16f, c("FFE9C7"), c("E9724C"), Roof.PYRAMID, 14f)
        LARGE_HOUSE -> BuildingStyle(20f, c("FDE3BE"), c("D96C4A"), Roof.GABLE, 16f)
        APARTMENTS -> BuildingStyle(38f, c("FBE5C8"), c("C9583F"), Roof.FLAT)
        HEALTH_CENTER -> BuildingStyle(18f, c("FFFFFF"), c("F7F2EA"), Roof.FLAT)
        REST_AREA -> BuildingStyle(3f, c("9ED77F"), c("A8E08A"), Roof.FIELD)
        CULTURE_CENTER -> BuildingStyle(22f, c("FFF0F5"), c("F48FB1"), Roof.DOME, 20f)
        FARM -> BuildingStyle(3f, c("B5835A"), c("B9DB6A"), Roof.FIELD)
        GREENHOUSE -> BuildingStyle(16f, c("D8F3EA"), c("B2EBF2"), Roof.GABLE, 12f)
        WATER_COLLECTOR -> BuildingStyle(10f, c("C9B38E"), c("6FCDF0"), Roof.FLAT)
        MILL -> BuildingStyle(26f, c("F5E3C4"), c("B97A4B"), Roof.PYRAMID, 14f)
        WORKSHOP -> BuildingStyle(18f, c("E7C9A3"), c("8D6E63"), Roof.GABLE, 12f)
        METALWORKS -> BuildingStyle(22f, c("C9CED6"), c("7E8EA3"), Roof.FLAT)
        TEXTILE -> BuildingStyle(16f, c("FFE4EE"), c("F48FB1"), Roof.GABLE, 12f)
        FOOD_FACTORY -> BuildingStyle(22f, c("FFF3D6"), c("8BC34A"), Roof.FLAT)
        POWER_PLANT -> BuildingStyle(20f, c("D9D2C5"), c("FFC94A"), Roof.FLAT)
        RECYCLER -> BuildingStyle(16f, c("D7E8C9"), c("7BC86C"), Roof.FLAT)
        FOOD_STORAGE -> BuildingStyle(18f, c("E7C08F"), c("A8C66C"), Roof.GABLE, 10f)
        WATER_STORAGE -> BuildingStyle(22f, c("7FC8EA"), c("B3E5FC"), Roof.DOME, 8f)
        MATERIAL_STORAGE -> BuildingStyle(18f, c("CFA77A"), c("8D6E63"), Roof.GABLE, 10f)
        CARRIER_CARE -> BuildingStyle(18f, c("E8F8EC"), c("81C784"), Roof.DOME, 14f)
        TECH_LAB -> BuildingStyle(24f, c("E3F2FD"), c("26C6DA"), Roof.DOME, 16f)
        NAVIGATION_TOWER -> BuildingStyle(44f, c("F3F7FA"), c("2EC4B6"), Roof.TOWER, 16f)
        GATHERER -> BuildingStyle(8f, c("C69C6D"), c("D9B98C"), Roof.FLAT)
        SUPPORT_FRAME -> BuildingStyle(10f, c("B98B5E"), c("D2AC7E"), Roof.FLAT)
        TRADE_MARKET -> BuildingStyle(14f, c("FFF3D6"), c("F6C445"), Roof.GABLE, 16f)
        TRADE_DEPOT -> BuildingStyle(18f, c("E3C59A"), c("D9A21B"), Roof.GABLE, 10f)
        WATCHTOWER -> BuildingStyle(40f, c("E7C9A3"), c("8D6E63"), Roof.TOWER, 14f)
        DEFENSE_TOWER -> BuildingStyle(36f, c("D9D2C5"), c("A0A4AA"), Roof.TOWER, 10f)
        ENERGY_TOWER -> BuildingStyle(40f, c("E3F2FD"), c("26C6DA"), Roof.TOWER, 12f)
        SHIELD_CENTER -> BuildingStyle(20f, c("E0F7FA"), c("4FC3F7"), Roof.DOME, 18f)
        BARRACKS -> BuildingStyle(20f, c("D7CCC8"), c("5C6BC0"), Roof.GABLE, 12f)
        EXPLORATION_CENTER -> BuildingStyle(20f, c("FFF3D6"), c("6DBE6A"), Roof.GABLE, 14f)
        QUEST_CENTER -> BuildingStyle(22f, c("FFF7E8"), c("F6C445"), Roof.PYRAMID, 16f)
        AIRSHIP_STATION -> BuildingStyle(12f, c("E3C59A"), c("B98B5E"), Roof.FLAT)
    }

    private val windowed = setOf(SMALL_HOUSE, LARGE_HOUSE, APARTMENTS, CITY_CENTER, TOWN_HALL, HEALTH_CENTER, CULTURE_CENTER)
    private val gA = Color(); private val gB = Color(); private val edge = Color(); private val shadowC = Color(0.15f, 0.2f, 0.1f, 0.18f)
    private val aoTop = Color(0f, 0f, 0f, 0f); private val aoBottom = Color(0.1f, 0.08f, 0.05f, 0.22f)
    private val tmp1 = Color()
    private val tmp2 = Color()
    private val tmp3 = Color()

    /**
     * Binayı ızgara konumunda çizer. [oy] güverte yüksekliği, [build] 0..1 inşaat oranı (1 = bitti),
     * [alpha] hayalet önizleme için saydamlık, [active] çalışıyor mu (animasyonlar).
     */
    fun draw(
        sr: ShapeRenderer, t: BuildingType, gx: Int, gy: Int, w: Int, h: Int, level: Int, hp: Float,
        oy: Float, time: Float, build: Float = 1f, alpha: Float = 1f, active: Boolean = true, tint: Color? = null,
        roofColor: Color? = null, roofKind: Roof? = null,
    ) {
        val base = style(t)
        val st = if (roofKind != null) base.copy(roofKind = roofKind, roofHeight = maxOf(base.roofHeight, 14f)) else base
        val inset = 0.09f
        val x0 = gx + inset; val y0 = gy + inset; val x1 = gx + w - inset; val y1 = gy + h - inset
        // Köşeler: A ön, B sağ, C arka, D sol
        val ax = Iso.x(x0, y0); val ay = Iso.y(x0, y0) + oy
        val bx = Iso.x(x1, y0); val by = Iso.y(x1, y0) + oy
        val cx = Iso.x(x1, y1); val cy = Iso.y(x1, y1) + oy
        val dx = Iso.x(x0, y1); val dy = Iso.y(x0, y1) + oy

        val wall = tmp1.set(st.wall)
        val roof = tmp2.set(roofColor ?: st.roof)
        if (tint != null) { wall.lerp(tint, 0.55f); roof.lerp(tint, 0.55f) }
        if (hp < 60f) { wall.lerp(Color.GRAY, (60f - hp) / 120f); roof.lerp(Color.GRAY, (60f - hp) / 120f) }
        wall.a = alpha; roof.a = alpha

        // Düşen gölge (ışık sol-yukarıdan): taban dörtgeni sağ-aşağı kaydırılır
        val sh = (st.height.coerceAtLeast(6f)) * 0.45f
        shadowC.a = 0.20f * alpha
        sr.color = shadowC
        Draw.quad(sr, ax, ay - 1, bx + sh, by - sh * 0.45f, cx + sh, cy - sh * 0.45f, dx, dy)
        sr.setColor(0.3f, 0.26f, 0.18f, 0.22f * alpha)
        Draw.quad(sr, ax, ay - 2, bx + 2, by - 1, cx, cy, dx - 2, dy - 1)

        if (build < 1f) {
            drawScaffold(sr, ax, ay, bx, by, cx, cy, dx, dy, st.height.coerceAtLeast(14f), build, alpha)
            return
        }

        val levelBoost = 1f + 0.15f * (level - 1)
        var hgt = st.height * levelBoost
        val tower = st.roofKind == Roof.TOWER
        var pax = ax; var pay = ay; var pbx = bx; var pby = by; var pcx = cx; var pcy = cy; var pdx = dx; var pdy = dy
        if (tower) {
            val mx = (ax + cx) / 2; val my = (ay + cy) / 2
            fun sh(v: Float, m: Float) = m + (v - m) * 0.55f
            pax = sh(ax, mx); pay = sh(ay, my); pbx = sh(bx, mx); pby = sh(by, my)
            pcx = sh(cx, mx); pcy = sh(cy, my); pdx = sh(dx, mx); pdy = sh(dy, my)
        }
        if (st.roofKind == Roof.FIELD) hgt = st.height

        // Yan duvarlar: ışık sol-yukarıdan; sol yüz gölgede, sağ yüz aydınlık, aşağıya doğru koyulaşır
        Draw.quadGradient(sr, pdx, pdy, pax, pay, pax, pay + hgt, pdx, pdy + hgt,
            Draw.shade(wall, 0.66f, gA).also { it.a = alpha }, Draw.shade(wall, 0.82f, gB).also { it.a = alpha })
        Draw.quadGradient(sr, pax, pay, pbx, pby, pbx, pby + hgt, pax, pay + hgt,
            Draw.shade(wall, 0.86f, gA).also { it.a = alpha }, Draw.shade(wall, 1.0f, gB).also { it.a = alpha })
        // Ortam kapanması: duvar dibinde yumuşak koyuluk
        if (hgt > 6) {
            aoBottom.a = 0.22f * alpha
            val ao = minOf(7f, hgt * 0.4f)
            Draw.quadGradient(sr, pdx, pdy, pax, pay, pax, pay + ao, pdx, pdy + ao, aoBottom, aoTop)
            Draw.quadGradient(sr, pax, pay, pbx, pby, pbx, pby + ao, pax, pay + ao, aoBottom, aoTop)
        }

        val tax = pax; val tay = pay + hgt; val tbx = pbx; val tby = pby + hgt
        val tcx = pcx; val tcy = pcy + hgt; val tdx = pdx; val tdy = pdy + hgt

        when (st.roofKind) {
            Roof.FLAT -> {
                Draw.quadGradient(sr, tax, tay, tbx, tby, tcx, tcy, tdx, tdy, roof, Draw.shade(roof, 0.9f, gA).also { it.a = alpha })
                sr.color = Draw.shade(roof, 0.85f, tmp3).also { it.a = alpha }
                // parapet
                sr.rectLine(tax, tay, tbx, tby, 2f); sr.rectLine(tax, tay, tdx, tdy, 2f)
            }
            Roof.FIELD -> {
                sr.color = roof
                Draw.quad(sr, tax, tay, tbx, tby, tcx, tcy, tdx, tdy)
                // Ekin sıraları
                val rows = 2 + w * 2
                sr.color = Draw.shade(roof, if (t == FARM) 0.8f else 0.88f, tmp3).also { it.a = alpha }
                for (i in 1 until rows) {
                    val f = i.toFloat() / rows
                    val sway = if (active) sin(time * 2f + i) * 0.6f else 0f
                    sr.rectLine(tax + (tdx - tax) * f + sway, tay + (tdy - tay) * f, tbx + (tcx - tbx) * f + sway, tby + (tcy - tby) * f, 2.2f)
                }
                if (t == REST_AREA) {
                    // Küçük ağaç ve bank
                    val mx = (tax + tcx) / 2; val my = (tay + tcy) / 2
                    sr.setColor(0.55f, 0.38f, 0.22f, alpha); sr.rectLine(mx, my, mx, my + 14, 3f)
                    sr.setColor(0.42f, 0.75f, 0.38f, alpha); sr.circle(mx, my + 18, 9f, 14)
                    sr.setColor(0.98f, 0.6f, 0.7f, alpha); sr.circle(mx + 4, my + 20, 2.2f, 8); sr.circle(mx - 4, my + 16, 2.2f, 8)
                }
            }
            Roof.PYRAMID, Roof.TOWER -> {
                val apx = (tax + tcx) / 2; val apy = (tay + tcy) / 2 + st.roofHeight * levelBoost
                val r1 = Draw.shade(roof, 0.72f, gA).also { it.a = alpha }; val r2 = Draw.shade(roof, 0.9f, gB).also { it.a = alpha }
                sr.triangle(tdx, tdy, tax, tay, apx, apy, r1, r1, r2)
                val r3 = Draw.shade(roof, 0.92f, tmp3).also { it.a = alpha }
                sr.triangle(tax, tay, tbx, tby, apx, apy, r3, r3, roof)
                Draw.shade(roof, 0.5f, edge).also { it.a = 0.5f * alpha }; sr.color = edge
                sr.rectLine(tdx, tdy, apx, apy, 1.1f); sr.rectLine(tax, tay, apx, apy, 1.3f); sr.rectLine(tbx, tby, apx, apy, 1.1f)
                sr.rectLine(tdx, tdy, tax, tay, 1.1f); sr.rectLine(tax, tay, tbx, tby, 1.1f)
                if (tower) {
                    // bayrak / ışık
                    sr.setColor(0.45f, 0.35f, 0.25f, alpha); sr.rectLine(apx, apy, apx, apy + 12, 1.6f)
                    val wave = sin(time * 4f) * 2f
                    if (t == ENERGY_TOWER) {
                        val glow = 0.5f + 0.5f * sin(time * 5f)
                        sr.setColor(0.4f, 0.9f, 1f, alpha * (0.5f + 0.5f * glow)); sr.circle(apx, apy + 8, 5f, 12)
                    } else if (t == DEFENSE_TOWER) {
                        // mazgallar
                        sr.setColor(0.55f, 0.55f, 0.58f, alpha); sr.rect(apx - 6, apy - 6, 3f, 5f); sr.rect(apx + 3, apy - 6, 3f, 5f)
                    } else if (t == NAVIGATION_TOWER) {
                        val glow = 0.6f + 0.4f * sin(time * 3f)
                        sr.setColor(1f, 0.9f, 0.4f, alpha * glow); sr.circle(apx, apy + 14, 4f, 10)
                    } else {
                        sr.setColor(0.93f, 0.42f, 0.3f, alpha); sr.triangle(apx, apy + 12, apx + 10, apy + 9 + wave, apx, apy + 6)
                    }
                }
            }
            Roof.GABLE -> {
                // Mahya x ekseni boyunca
                val rh = st.roofHeight * levelBoost
                val rax = (tax + tdx) / 2; val ray = (tay + tdy) / 2 + rh
                val rbx = (tbx + tcx) / 2; val rby = (tby + tcy) / 2 + rh
                Draw.quadGradient(sr, tax, tay, tbx, tby, rbx, rby, rax, ray,
                    Draw.shade(roof, 0.82f, gA).also { it.a = alpha }, Draw.shade(roof, 1.05f, gB).also { it.a = alpha })
                sr.color = Draw.shade(wall, 0.72f, tmp3).also { it.a = alpha }
                sr.triangle(tdx, tdy, tax, tay, rax, ray)
                Draw.shade(roof, 0.5f, edge).also { it.a = 0.55f * alpha }; sr.color = edge
                sr.rectLine(tax, tay, tbx, tby, 1.2f); sr.rectLine(tax, tay, rax, ray, 1.1f); sr.rectLine(tdx, tdy, rax, ray, 1.1f)
                sr.setColor(1f, 1f, 1f, 0.45f * alpha)
                sr.rectLine(rax, ray, rbx, rby, 2f)
            }
            Roof.DOME -> {
                sr.color = Draw.shade(wall, 0.96f, tmp3).also { it.a = alpha }
                Draw.quad(sr, tax, tay, tbx, tby, tcx, tcy, tdx, tdy)
                val mx = (tax + tcx) / 2; val my = (tay + tcy) / 2
                val rx = kotlin.math.abs(tbx - tdx) * 0.32f
                val pulse = if (active && (t == RESEARCH_CENTER || t == TECH_LAB)) 0.85f + 0.15f * sin(time * 2.5f) else 1f
                sr.color = Draw.shade(roof, 0.85f * pulse, tmp3).also { it.a = alpha }
                Draw.ellipseFan(sr, mx, my, rx, rx * 0.5f, 18)
                sr.color = Draw.shade(roof, pulse, tmp3).also { it.a = alpha }
                sr.arc(mx, my, rx, 0f, 180f, 16)
                sr.setColor(1f, 1f, 1f, 0.55f * alpha)
                sr.circle(mx - rx * 0.35f, my + rx * 0.45f, rx * 0.16f, 8)
            }
        }

        // Kenar çizgileri (silüet): ince ve yarı saydam
        if (hgt > 4) {
            Draw.shade(wall, 0.45f, edge).also { it.a = 0.55f * alpha }
            sr.color = edge
            sr.rectLine(pax, pay, pax, pay + hgt, 1.3f)
            sr.rectLine(pdx, pdy, pdx, pdy + hgt, 1.1f)
            sr.rectLine(pbx, pby, pbx, pby + hgt, 1.1f)
            sr.rectLine(pdx, pdy, pax, pay, 1.1f); sr.rectLine(pax, pay, pbx, pby, 1.1f)
            if (st.roofKind == Roof.FLAT || st.roofKind == Roof.DOME) { sr.rectLine(tax, tay, tbx, tby, 1.1f); sr.rectLine(tax, tay, tdx, tdy, 1.1f) }
            // Üst kenarda ışık çizgisi
            sr.setColor(1f, 1f, 1f, 0.35f * alpha); sr.rectLine(pax + 1, pay + hgt - 0.5f, pbx, pby + hgt - 0.5f, 1.2f)
        }

        // Pencereler / kapı (konutlar ve yönetim)
        if (t in windowed && hgt > 12) {
            sr.setColor(0.55f, 0.85f, 0.95f, alpha)
            val floors = (hgt / 16f).toInt().coerceAtLeast(1)
            for (f in 0 until floors) {
                val fy = 6f + f * 14f
                for (k in 1..(w * 2)) {
                    val fr = k / (w * 2f + 1f)
                    val wx = pax + (pbx - pax) * fr; val wy = pay + (pby - pay) * fr + fy
                    Draw.quad(sr, wx - 2.5f, wy - 1.2f, wx + 2.5f, wy + 1.2f, wx + 2.5f, wy + 6.2f, wx - 2.5f, wy + 3.8f)
                }
            }
        }
        if (t == HEALTH_CENTER) {
            val mx = (tax + tcx) / 2; val my = (tay + tcy) / 2
            sr.setColor(0.93f, 0.33f, 0.3f, alpha)
            sr.rectLine(mx - 7, my, mx + 7, my, 4f); sr.rectLine(mx, my - 4, mx, my + 4, 4f)
        }
        if (t == WATER_COLLECTOR) {
            val mx = (tax + tcx) / 2; val my = (tay + tcy) / 2
            val r = 1f + 0.15f * sin(time * 2f)
            sr.setColor(1f, 1f, 1f, 0.5f * alpha); Draw.ellipseFan(sr, mx, my, 9f * r, 4.5f * r, 14)
            // toplama direkleri
            sr.setColor(0.5f, 0.42f, 0.33f, alpha); sr.rectLine(tdx, tdy, tdx, tdy + 12, 2f); sr.rectLine(tbx, tby, tbx, tby + 12, 2f)
            sr.setColor(0.9f, 0.95f, 1f, alpha); sr.circle(tdx, tdy + 13, 3f, 8); sr.circle(tbx, tby + 13, 3f, 8)
        }
        if (t == MILL) {
            val hx = (pax + pbx) / 2; val hy = (pay + pby) / 2 + hgt * 0.75f
            val ang = if (active) time * 2.2f else 0.4f
            sr.setColor(0.98f, 0.95f, 0.88f, alpha)
            for (k in 0 until 4) {
                val a = ang + k * Math.PI.toFloat() / 2
                sr.rectLine(hx, hy, hx + cos(a) * 20, hy + sin(a) * 20, 3.5f)
            }
            sr.setColor(0.55f, 0.4f, 0.26f, alpha); sr.circle(hx, hy, 3f, 8)
        }
        if (t == POWER_PLANT || t == METALWORKS || t == WORKSHOP || t == FOOD_FACTORY) {
            val chx = tdx + (tcx - tdx) * 0.3f; val chy = tdy + (tcy - tdy) * 0.3f
            sr.setColor(0.6f, 0.45f, 0.35f, alpha); sr.rectLine(chx, chy - 4, chx, chy + 16, 6f)
            sr.setColor(0.85f, 0.35f, 0.25f, alpha); sr.rectLine(chx, chy + 13, chx, chy + 16, 6f)
        }
        if (t == GATHERER) {
            // Vinç kolu ve sarkan kova
            val bx2 = tbx + 18; val by2 = tby + 26
            sr.setColor(0.55f, 0.38f, 0.22f, alpha); sr.rectLine(tax, tay, tax, tay + 22, 3f); sr.rectLine(tax, tay + 22, bx2, by2, 2.5f)
            val swing = if (active) sin(time * 1.5f) * 4 else 0f
            val ry = by2 - 22 - 8 * (0.5f + 0.5f * sin(time * 0.9f))
            sr.setColor(0.4f, 0.32f, 0.24f, alpha); sr.rectLine(bx2, by2, bx2 + swing, ry, 1.2f)
            sr.setColor(0.75f, 0.55f, 0.3f, alpha); sr.rect(bx2 + swing - 4, ry - 6, 8f, 6f)
        }
        if (t == SUPPORT_FRAME) {
            sr.setColor(0.5f, 0.33f, 0.2f, alpha)
            sr.rectLine(pax, pay, pbx, pby + hgt, 2f); sr.rectLine(pbx, pby, pax, pay + hgt, 2f)
            sr.rectLine(pdx, pdy, pax, pay + hgt, 2f); sr.rectLine(pax, pay, pdx, pdy + hgt, 2f)
        }
        if (t == AIRSHIP_STATION) {
            // Bağlı hava gemisi: süzülen balon ve sepet
            val mx = (tax + tcx) / 2; val my = (tay + tcy) / 2
            val bob = sin(time * 1.5f) * 4f
            sr.setColor(0.45f, 0.32f, 0.2f, alpha); sr.rectLine(mx, my, mx, my + 34 + bob, 1.2f)
            sr.setColor(0.72f, 0.52f, 0.3f, alpha); sr.rect(mx - 7, my + 30 + bob, 14f, 7f)
            sr.setColor(0.93f, 0.42f, 0.3f, alpha); Draw.ellipseFan(sr, mx, my + 54 + bob, 26f, 15f, 20)
            sr.setColor(1f, 0.95f, 0.85f, alpha); Draw.ellipseFan(sr, mx, my + 54 + bob, 26f, 4f, 16)
        }
        if (t == TRADE_MARKET) {
            // Çizgili tente ve sandıklar
            sr.setColor(0.93f, 0.42f, 0.3f, alpha)
            for (k in 0 until 4) {
                val f0 = k / 4f + 0.04f; val f1 = f0 + 0.12f
                Draw.quad(sr, pax + (pbx - pax) * f0, pay + (pby - pay) * f0 + hgt, pax + (pbx - pax) * f1, pay + (pby - pay) * f1 + hgt,
                    pax + (pbx - pax) * f1, pay + (pby - pay) * f1 + hgt - 6, pax + (pbx - pax) * f0, pay + (pby - pay) * f0 + hgt - 6)
            }
            sr.setColor(0.72f, 0.52f, 0.3f, alpha); sr.rect(pax + 6, pay + 2, 8f, 7f); sr.rect(pax + 16, pay + 5, 7f, 6f)
            sr.setColor(0.97f, 0.77f, 0.27f, alpha); sr.circle(pax + 10, pay + 12, 2.5f, 8)
        }
        if (t == FOOD_STORAGE || t == MATERIAL_STORAGE || t == TRADE_DEPOT) {
            // kapı
            val mx = (pax + pbx) / 2; val my = (pay + pby) / 2
            sr.setColor(0.45f, 0.3f, 0.2f, alpha)
            Draw.quad(sr, mx - 5, my - 2.5f, mx + 5, my + 2.5f, mx + 5, my + 12.5f, mx - 5, my + 7.5f)
        }

        // Seviye göstergesi: ön köşede altın noktalar
        if (level > 1) {
            sr.setColor(0.97f, 0.77f, 0.27f, alpha)
            for (i in 0 until level - 1) sr.circle(ax + 6f + i * 7f, ay + 4f + i * 3.5f, 3f, 8)
        }
        // Hasar işareti
        if (hp < 60f) {
            sr.setColor(0.35f, 0.3f, 0.3f, 0.8f * alpha)
            sr.rectLine(pax + 4, pay + hgt * 0.8f, pax + 9, pay + hgt * 0.45f, 1.6f)
            sr.rectLine(pax + 9, pay + hgt * 0.45f, pax + 6, pay + hgt * 0.2f, 1.6f)
        }
    }

    private fun drawScaffold(sr: ShapeRenderer, ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float, h: Float, build: Float, alpha: Float) {
        sr.setColor(0.86f, 0.74f, 0.55f, alpha)
        Draw.quad(sr, ax, ay, bx, by, cx, cy, dx, dy)
        val hh = h * build.coerceIn(0.1f, 1f)
        sr.setColor(0.93f, 0.82f, 0.62f, 0.9f * alpha)
        Draw.quad(sr, dx, dy, ax, ay, ax, ay + hh, dx, dy + hh)
        Draw.quad(sr, ax, ay, bx, by, bx, by + hh, ax, ay + hh)
        sr.setColor(0.6f, 0.42f, 0.25f, alpha)
        for (p in listOf(ax to ay, bx to by, dx to dy)) sr.rectLine(p.first, p.second, p.first, p.second + h + 6, 2f)
        sr.rectLine(ax, ay + h + 6, bx, by + h + 6, 1.5f)
        sr.rectLine(ax, ay + h + 6, dx, dy + h + 6, 1.5f)
        sr.rectLine(ax, ay, bx, by + h, 1.2f)
        sr.rectLine(dx, dy, ax, ay + h, 1.2f)
    }
}
