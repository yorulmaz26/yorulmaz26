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
                                // çizilen dikenin dolu bölgesi: kenarlardan 0.18 içeride, tabandan diken boyunun %70'ine kadar
                                val sv = Physics.SPIKE_VISUAL_H * 0.7f
                                val x0 = h.cx + 0.18f; val x1 = h.cx + h.def.w - 0.18f
                                val y0 = if (h.def.dir > 0) h.cy else h.cy - sv
                                val y1 = if (h.def.dir > 0) h.cy + sv else h.cy
                                if (bx0 < x1 && bx1 > x0 && by0 < y1 && by1 > y0) { spikeTouch++; if (where.isEmpty()) where = "diken x=${"%.1f".format(h.cx)} t=${s.tick}" }
                            }
                            HazardType.SAW -> {
                                val r = h.def.w * 0.72f // çizilen testerenin dişler hariç gövdesi
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

    @DisplayName("Her kural aşamasında oynanacak bir şey var; kurala özgü nesneler (hayalet, kayan platform, dar geçit) gerçekten yerinde")
    @Test fun phasesHaveContent() {
        var phases = 0; var empty = 0
        val emptyList = ArrayList<String>()
        // kural -> (o kuralı taşıyan aşama sayısı, dünyada karşılığı olmayan aşama sayısı)
        val need = LinkedHashMap<String, IntArray>()
        val missing = ArrayList<String>()
        for (l in all) {
            val finish = l.goalX - Physics.FINISH_LEN
            for (i in 1 until l.phases.size) { // ilk aşama (ısınma) hariç
                val x0 = l.phases[i].x
                if (x0 >= finish - 0.01f) continue // bitiş düzlüğü
                val x1 = if (i + 1 < l.phases.size) min(l.phases[i + 1].x, finish) else finish
                val feats = l.features.filter { it.x >= x0 && it.x < x1 }.map { it.p }
                phases++
                if (feats.isEmpty()) { empty++; if (emptyList.size < 40) emptyList += "${l.id}@${x0.toInt()}(${l.phases[i].rules.joinToString("+") { it.name.take(4) }})" }
                val e = RuleEffects.of(l.phases[i].rules)
                fun has(t: SolidType) = l.solids.any { it.type == t && it.x + it.w > x0 && it.x < x1 }
                fun check(name: String, wanted: Boolean, present: Boolean) {
                    if (!wanted) return
                    val c = need.getOrPut(name) { IntArray(2) }
                    c[0]++
                    if (!present) { c[1]++; if (name == "kayan" || name == "hayalet") missing += "${l.id}@${x0.toInt()} $name (${(x1 - x0).toInt()} karo)" }
                }
                check("kayan", e.shifting, has(SolidType.SHIFT))
                check("hayalet", e.ghost, has(SolidType.GHOST))
                check("dar geçit", e.sizeScale < 1f && !e.reverseControl, has(SolidType.LOW))
                check("asılı diken", e.reverseControl, Pattern.OVERHEAD in feats)
                check("yüksek zıplama", e.jumpMul > 1.2f, Pattern.BOUNCY_WALL in feats)
            }
        }
        println("Aşama denetimi: $phases aşama, boş $empty")
        for ((k, v) in need) println("  $k: ${v[1]} / ${v[0]} aşamada karşılığı yok")
        println("Boş aşamalar: $emptyList")
        println("Kayan/hayalet nesnesi olmayan aşamalar: ${missing.take(40)}")
        assertTrue(empty <= phases * 0.02, "boş aşama sayısı: $empty / $phases")
        // Dünyayı değiştiren kurallar (kayan platform, hayalet) duyurulduysa o aşamada mutlaka karşılığı bulunur
        for (k in listOf("kayan", "hayalet")) need[k]?.let { assertTrue(it[1] <= it[0] * 0.02, "$k: ${it[1]} / ${it[0]} aşamada nesne yok: ${missing.take(20)}") }
        for (k in listOf("dar geçit", "asılı diken", "yüksek zıplama")) need[k]?.let { assertTrue(it[1] <= it[0] * 0.25, "$k: ${it[1]} / ${it[0]} aşamada desen yok") }
    }

    @DisplayName("Kural çizgilerinin çevresi temiz: çizginin hemen önünde/arkasında engel yok, karakterden dar boşluk yok")
    @Test fun ruleLinesAndGapsAreClean() {
        val errors = ArrayList<String>()
        for (l in all) {
            for (ph in l.phases.drop(1)) {
                for (h in l.hazards) {
                    val hx0 = if (h.type == HazardType.SPIKE) h.x else h.x - h.w
                    val hx1 = if (h.type == HazardType.SPIKE) h.x + h.w else h.x + h.w
                    if (hx1 > ph.x - 1.2f && hx0 < ph.x + 2.0f) errors += "${l.id}: kural çizgisi ${ph.x} dibinde engel (${h.type} x=${h.x})"
                }
            }
            // aynı yüzeydeki zemin parçaları arasında karakterden dar (ama sıfır olmayan) boşluk olmamalı
            for (type in listOf(SolidType.GROUND, SolidType.CEILING)) {
                val spans = l.solids.filter { it.type == type }.sortedBy { it.x }
                for ((a, b) in spans.zipWithNext()) {
                    val gap = b.x - (a.x + a.w)
                    if (gap > 0.02f && gap < Physics.PLAYER_W) errors += "${l.id}: kılcal boşluk x=${a.x + a.w} genişlik=$gap"
                }
            }
            // hayalet köprü parçaları karakterden dar olamaz (yanıp sönen ama üstünden koşulan parça)
            for (g in l.solids.filter { it.type == SolidType.GHOST }) {
                if (g.w < Patterns.MIN_GAP) errors += "${l.id}: dar hayalet parça x=${g.x} genişlik=${g.w}"
            }
            // hareketli platformlar: zeminle/komşu platformla arasındaki boşluk ya yok ya da gerçek boşluk (>= MIN_GAP);
            // yatay giden platform koşu hızının %65'inden hızlı gidemez
            val grounds = l.solids.filter { it.type == SolidType.GROUND || it.type == SolidType.CEILING }
            for (m in l.solids.filter { it.type == SolidType.MOVE }) {
                if (m.ax > 0f) {
                    val peak = 2f * Math.PI.toFloat() * m.ax / m.period
                    if (peak > 0.66f * Physics.RUN_SPEED) errors += "${l.id}: hızlı platform x=${m.x} tepe hız=$peak"
                    continue
                }
                val others = grounds + l.solids.filter { it.type == SolidType.MOVE && it !== m }
                val left = others.filter { it.x + it.w <= m.x + 0.01f }.maxOfOrNull { it.x + it.w }
                val right = others.filter { it.x >= m.x + m.w - 0.01f }.minOfOrNull { it.x }
                for (gap in listOfNotNull(left?.let { m.x - it }, right?.let { it - (m.x + m.w) })) {
                    if (gap > 0.02f && gap < Patterns.MIN_GAP - 0.01f) errors += "${l.id}: hareketli platform yanında dar boşluk x=${m.x} genişlik=$gap"
                }
            }
            // ilk engel, başlangıç dokunuşundan en az ~1,5 sn sonra
            val first = l.features.firstOrNull()
            if (first != null && l.id > 1 && first.x < 9f) errors += "${l.id}: ilk engel çok erken (x=${first.x})"
        }
        println("Çizgi/boşluk denetimi: ${errors.size} sorun"); errors.take(40).forEach(::println)
        assertTrue(errors.isEmpty(), errors.take(30).joinToString("\n"))
    }

    @DisplayName("Her kontrol noktasında yeniden doğan oyuncunun tepki verecek zamanı var (en az 1 sn hayatta kalır)")
    @Test fun checkpointsGiveReactionTime() {
        val pool = Executors.newFixedThreadPool(4)
        val results = pool.invokeAll(all.map { l -> java.util.concurrent.Callable {
            val bad = ArrayList<String>()
            for (cp in l.checkpoints) {
                var best = 0
                for (hold in listOf(false, true)) {
                    val sim = Simulation(l, SimConfig(hearts = 3, powerups = false))
                    try {
                        val track = InputTrack(l.solution.toIntArray())
                        while (sim.status == SimStatus.RUNNING && sim.lastCheckpointX < cp) sim.step(track.at(sim.tick))
                        if (sim.lastCheckpointX != cp) { bad += "${l.id}: bayrak $cp kaydedilmedi"; break }
                        repeat(6) { if (sim.status == SimStatus.RUNNING) sim.step(track.at(sim.tick)) }
                        sim.kill(DeathCause.SPIKE)
                        var g = 0
                        while (sim.status == SimStatus.DYING && g++ < 200) sim.step(false)
                        if (sim.status != SimStatus.RUNNING) { bad += "${l.id}: bayrak $cp yeniden doğuş başarısız"; break }
                        var alive = 0
                        while (alive < 120 && sim.status == SimStatus.RUNNING) { sim.step(hold); alive++ }
                        best = max(best, alive)
                    } finally { sim.dispose() }
                }
                if (best in 1 until 60) bad += "${l.id}: bayrak $cp sonrası yalnızca $best tick"
            }
            bad
        } }).flatMap { it.get() }
        pool.shutdown()
        println("Kontrol noktası denetimi: ${results.size} sorun"); results.take(40).forEach(::println)
        assertTrue(results.isEmpty(), results.take(30).joinToString("\n"))
    }
}
