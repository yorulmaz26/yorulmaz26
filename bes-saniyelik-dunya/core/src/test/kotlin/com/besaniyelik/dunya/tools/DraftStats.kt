package com.besaniyelik.dunya.tools

import com.besaniyelik.dunya.level.*
import com.besaniyelik.dunya.sim.Physics
import com.besaniyelik.dunya.sim.RuleEffects
import kotlin.math.min

/**
 * Teşhis: üreticinin taslaklarını (çözmeden, ölçmeden) tarar; kurala özgü nesnelerin aşamalarda bulunma oranını,
 * dar hayalet parçaları ve hareketli platform boşluklarını sayar. Üretici değişikliklerinin hızlı sağlaması içindir.
 */
object DraftStats {
    @JvmStatic
    fun main(args: Array<String>) {
        val attempts = args.getOrElse(0) { "3" }.toInt()
        val shifts = floatArrayOf(0f, -0.3f, 0.4f)
        val need = LinkedHashMap<String, IntArray>()
        var phases = 0; var empty = 0; var ghostNarrow = 0; var ghosts = 0; var moveGapBad = 0; var fastPlat = 0; var drafts = 0
        val featCount = HashMap<Pattern, Int>()
        var shown = 0
        for (id in 1..500) for (a in 0 until attempts) {
            val l = LevelGenerator.generate(id, a, shifts[a % shifts.size]).data
            drafts++
            val finish = l.goalX - Physics.FINISH_LEN
            for (f in l.features) featCount.merge(f.p, 1, Int::plus)
            for (i in 1 until l.phases.size) {
                val x0 = l.phases[i].x
                if (x0 >= finish - 0.01f) continue
                val x1 = if (i + 1 < l.phases.size) min(l.phases[i + 1].x, finish) else finish
                val feats = l.features.filter { it.x >= x0 && it.x < x1 }.map { it.p }
                phases++
                if (feats.isEmpty()) empty++
                val e = RuleEffects.of(l.phases[i].rules)
                fun has(t: SolidType) = l.solids.any { it.type == t && it.x + it.w > x0 && it.x < x1 }
                fun check(name: String, wanted: Boolean, present: Boolean) {
                    if (!wanted) return
                    val c = need.getOrPut(name) { IntArray(2) }
                    c[0]++; if (!present) {
                        c[1]++
                        if (name.startsWith(System.getProperty("show", "kayan")) && shown++ < 30)
                            println("  YOK $name: #$id d$a t=%.2f %s uzunluk=%.1f desenler=%s".format(shifts[a % shifts.size], l.phases[i].rules.joinToString("+") { it.name.take(6) }, x1 - x0, feats))
                    }
                }
                check("kayan", e.shifting, has(SolidType.SHIFT))
                check("hayalet", e.ghost, has(SolidType.GHOST))
                check("dar geçit", e.sizeScale < 1f && !e.reverseControl, has(SolidType.LOW))
                check("asılı diken", e.reverseControl, Pattern.OVERHEAD in feats)
                check("yüksek zıplama", e.jumpMul > 1.2f, Pattern.BOUNCY_WALL in feats)
            }
            for (g in l.solids.filter { it.type == SolidType.GHOST }) { ghosts++; if (g.w < Patterns.MIN_GAP) ghostNarrow++ }
            val grounds = l.solids.filter { it.type == SolidType.GROUND || it.type == SolidType.CEILING }
            for (m in l.solids.filter { it.type == SolidType.MOVE }) {
                if (m.ax > 0f) { if (2f * Math.PI.toFloat() * m.ax / m.period > 0.66f * Physics.RUN_SPEED) fastPlat++; continue }
                val others = grounds + l.solids.filter { it.type == SolidType.MOVE && it !== m }
                val left = others.filter { it.x + it.w <= m.x + 0.01f }.maxOfOrNull { it.x + it.w }
                val right = others.filter { it.x >= m.x + m.w - 0.01f }.minOfOrNull { it.x }
                for (gap in listOfNotNull(left?.let { m.x - it }, right?.let { it - (m.x + m.w) })) if (gap > 0.02f && gap < Patterns.MIN_GAP - 0.01f) moveGapBad++
            }
        }
        println("$drafts taslak, $phases aşama, boş $empty")
        for ((k, v) in need) println("  %-15s %4d / %4d aşamada yok (%.1f%%)".format(k, v[1], v[0], 100f * v[1] / v[0]))
        println("hayalet parça: $ghosts, dar: $ghostNarrow; hareketli platform dar boşluk: $moveGapBad, hızlı platform: $fastPlat")
        println(featCount.entries.sortedByDescending { it.value }.joinToString("  ") { "${it.key}=${it.value}" })
    }
}
