package com.besaniyelik.dunya.tools

import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.utils.GdxNativesLoader
import com.besaniyelik.dunya.level.*

/** Teşhis: verilen bölümün denemelerinde çözülemeyen taslakları ve takıldıkları yerdeki deseni listeler. */
object ProbeUnsolved {
    @JvmStatic
    fun main(args: Array<String>) {
        GdxNativesLoader.load(); Box2D.init()
        val ids = args[0].split(",").map { it.toInt() }
        val attempts = args.getOrElse(1) { "20" }.toInt()
        val shift = args.getOrElse(2) { "0.3" }.toFloat()
        val stuck = HashMap<String, Int>()
        var total = 0; var fail = 0
        for (id in ids) for (a in 0 until attempts) {
            val d = LevelGenerator.generate(id, a, shift).data
            val r = LevelForge.trySolve(d, 160_000)
            total++
            if (r.success) continue
            fail++
            val bx = Regex("x=([0-9.]+)").find(r.reason)?.groupValues?.get(1)?.toFloat() ?: -1f
            val f = d.features.lastOrNull { it.x <= bx + 1.5f }
            val ph = d.phases.lastOrNull { it.x <= bx + 1.5f }
            val key = "${f?.p} / ${ph?.rules?.joinToString("+") { it.name.take(6) }}"
            stuck.merge(key, 1, Int::plus)
            if (args.getOrNull(3) == "v") {
                println("#$id d$a: ${r.reason}  desen=${f?.p}@${f?.x} kurallar=${ph?.rules}")
                for (s in d.solids.filter { it.x + it.w > bx - 6f && it.x < bx + 8f }) println("    katı ${s.type} x=${s.x} y=${s.y} w=${s.w} h=${s.h} ax=${s.ax} ay=${s.ay} per=${s.period} ph=${s.phase}")
                for (h in d.hazards.filter { it.x > bx - 6f && it.x < bx + 8f }) println("    engel ${h.type} x=${h.x} y=${h.y} w=${h.w} dir=${h.dir} ay=${h.ay} per=${h.period} sync=${h.sync}")
            }
        }
        println("$total taslak, çözülemeyen $fail")
        for ((k, v) in stuck.entries.sortedByDescending { it.value }) println("  %3d  %s".format(v, k))
    }
}
