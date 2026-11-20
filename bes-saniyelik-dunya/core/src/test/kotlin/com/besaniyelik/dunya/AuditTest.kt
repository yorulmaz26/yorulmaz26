package com.besaniyelik.dunya

import com.besaniyelik.dunya.level.*
import com.besaniyelik.dunya.sim.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertTrue

/**
 * Görsel ↔ mantık denetimi: bütün bölümler kayıtlı çözümle oynatılırken karakterin ÇİZİLEN gövdesi ile
 * dikenlerin/testerelerin ÇİZİLEN gövdesinin üst üste binip binmediği (ölmeden "sürterek geçme"),
 * toplanabilirlerin katıların içine gömülmesi ve havada asılı dikenler aranır.
 */
class AuditTest {
    private val all get() = LevelsTest.all

    private fun static(s: SolidDef) = s.period == 0f && s.type != SolidType.MOVE && s.type != SolidType.SHIFT && s.type != SolidType.GHOST

    @DisplayName("Toplanabilirler katıların içinde değil, dikenler bir yüzeye oturuyor")
    @Test fun staticGeometry() {
        val errors = ArrayList<String>()
        for (l in all) {
            val solids = l.solids.filter { static(it) }
            fun inside(x: Float, y: Float, pad: Float) = solids.any { x > it.x + pad && x < it.x + it.w - pad && y > it.y + pad && y < it.y + it.h - pad }
            l.coins.forEachIndexed { i, c -> if (inside(c.x, c.y, 0.1f)) errors += "${l.id}: altın #$i katı içinde (${c.x},${c.y})" }
            l.stars.forEachIndexed { i, c -> if (inside(c.x, c.y, 0.1f)) errors += "${l.id}: yıldız #$i katı içinde" }
            l.piece?.let { if (inside(it.x, it.y, 0.1f)) errors += "${l.id}: parça katı içinde" }
            for (h in l.hazards.filter { it.type == HazardType.SPIKE && it.period == 0f }) {
                // yukarı bakan diken: tabanın altında bir katı yüzeyi; aşağı bakan: tabanın üstünde
                val ok = l.solids.any { s ->
                    val surf = if (h.dir > 0) s.y + s.h else s.y
                    abs(surf - h.y) < 0.06f && s.x <= h.x + 0.05f && s.x + s.w >= h.x + h.w - 0.05f
                }
                if (!ok) errors += "${l.id}: diken havada (${h.x},${h.y}) yön ${h.dir}"
            }
        }
        println("Statik denetim: ${errors.size} sorun"); errors.take(60).forEach(::println)
        assertTrue(errors.isEmpty(), errors.take(30).joinToString("\n"))
    }

    @DisplayName("Çözüm oynatılırken karakter görseli dikene/testereye değip ölmeden geçmiyor")
    @Test fun visualVsHitbox() {
        val pool = Executors.newFixedThreadPool(4)
        val results = pool.invokeAll(all.map { l -> java.util.concurrent.Callable {
            val sim = Simulation(l, SimConfig(hearts = 1, powerups = false))
            var spikeTouch = 0; var sawTouch = 0; var headClip = 0
            var where = ""
            try {
                LevelSolver.replay(sim, l.solution.toIntArray()) { s ->
                    if (s.status != SimStatus.RUNNING) return@replay
                    val p = s.player
                    val ws = s.effects.gravitySign.toFloat()
                    val feet = p.y - ws * p.hh
                    val vh = Physics.PLAYER_VISUAL_H * p.scale
                    // çizilen gövdenin dolu kısmı: ±0.32 genişlik, ayaktan 0.92*boy yüksekliğe kadar (kapüşon ucu hariç)
                    val bx0 = p.x - 0.32f * p.scale; val bx1 = p.x + 0.32f * p.scale
                    val by0 = min(feet, feet + ws * vh * 0.92f); val by1 = max(feet, feet + ws * vh * 0.92f)
                    for (h in s.hazards) {
                        if (h.removed) continue
                        when (h.def.type) {
                            HazardType.SPIKE -> {
                                // çizilen dikenin dolu bölgesi: kenarlardan 0.22 içeride, tabandan 0.32 yükseğe kadar
                                val x0 = h.cx + 0.22f; val x1 = h.cx + h.def.w - 0.22f
                                val y0 = if (h.def.dir > 0) h.cy else h.cy - 0.32f
                                val y1 = if (h.def.dir > 0) h.cy + 0.32f else h.cy
                                if (bx0 < x1 && bx1 > x0 && by0 < y1 && by1 > y0) { spikeTouch++; if (where.isEmpty()) where = "diken x=${"%.1f".format(h.cx)} t=${s.tick}" }
                            }
                            HazardType.SAW -> {
                                val r = h.def.w * 0.8f // çizilen testerenin dişler hariç gövdesi
                                val nx = h.cx.coerceIn(bx0, bx1); val ny = h.cy.coerceIn(by0, by1)
                                if ((h.cx - nx) * (h.cx - nx) + (h.cy - ny) * (h.cy - ny) < r * r) { sawTouch++; if (where.isEmpty()) where = "testere x=${"%.1f".format(h.cx)} t=${s.tick}" }
                            }
                        }
                    }
                    // kafa (gövde üst kısmı) sabit bir katının içine giriyor mu?
                    for (so in s.solids) {
                        if (!static(so.def)) continue
                        val ix = min(bx1, so.x + so.w) - max(bx0, so.x)
                        val iy = min(by1, so.y + so.h) - max(by0, so.y)
                        if (ix > 0.05f && iy > 0.12f) { headClip++; if (where.isEmpty()) where = "katı içine girme ${so.def.type} [${so.x},${so.y},${so.w},${so.h}] p=(${p.x},${p.y}) g=${p.grounded} vy=${p.vy} ix=$ix iy=$iy t=${s.tick}" }
                    }
                }
            } finally { sim.dispose() }
            if (spikeTouch + sawTouch + headClip > 0) "${l.id}: diken=$spikeTouch testere=$sawTouch katı=$headClip  ilk: $where" else null
        } }).mapNotNull { it.get() }
        pool.shutdown()
        println("Görsel denetim: ${results.size} bölümde temas"); results.forEach(::println)
        assertTrue(results.isEmpty(), results.take(30).joinToString("\n"))
    }
}
