package com.besaniyelik.dunya.tools

import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.utils.GdxNativesLoader
import com.besaniyelik.dunya.level.*
import com.besaniyelik.dunya.sim.Mechanic
import java.io.File
import java.util.concurrent.Executors

/** Teşhis: dar zamanlama pencerelerinin hangi desen × kural bileşiminden kaynaklandığını sayar. */
object TightSpots {
    @JvmStatic
    fun main(args: Array<String>) {
        GdxNativesLoader.load(); Box2D.init()
        val assets = File(args[0])
        LevelRepository.reader = { File(assets, it).readText() }
        val range = args[1].split("-").let { it[0].toInt()..it.last().toInt() }
        val pool = Executors.newFixedThreadPool(4)
        class Row(val pattern: String, val rules: String, val window: Int, val hold: Int, val id: Int, val x: Float)
        val futures = range.map { id -> pool.submit<List<Row>> {
            val l = LevelRepository.level(id)
            val feats = l.features
            val sol = l.solution.toIntArray()
            val m = LevelAnalyzer.measure(l, sol, trials = 4) ?: return@submit emptyList()
            (0 until m.jumps).map { j ->
                val x = m.jumpX[j]
                val f = feats.lastOrNull { it.x <= x + 0.5f }
                val ph = l.phases.lastOrNull { it.x <= x }
                Row(f?.p?.name ?: "-", ph?.rules?.joinToString("+") { it.name.take(5) } ?: "-", m.windows[j], m.holdWindows[j], id, x)
            }
        } }
        val rows = futures.flatMap { it.get() }
        pool.shutdown()
        fun table(title: String, key: (Row) -> String) {
            println("== $title: anahtar | zıplama | pencere<6 | pencere<10 | tutma<6 | medyan pencere")
            rows.groupBy(key).entries.sortedByDescending { e -> e.value.count { it.window < 6 }.toFloat() / e.value.size }.forEach { (k, v) ->
                if (v.size < 15) return@forEach
                val w = v.map { it.window }.sorted()
                println("%-34s %5d  %4.0f%%  %4.0f%%  %4.0f%%  %3d".format(k, v.size, 100f * v.count { it.window < 6 } / v.size, 100f * v.count { it.window < 10 } / v.size,
                    100f * v.count { it.hold < 6 } / v.size, w[w.size / 2]))
            }
        }
        table("desen") { it.pattern }
        table("kural") { it.rules }
        table("desen x kural (yalnızca tek kural)") { it.pattern + " / " + it.rules }
        File(args[2]).writeText(rows.joinToString("\n") { "${it.id}\t${"%.1f".format(it.x)}\t${it.pattern}\t${it.rules}\t${it.window}\t${it.hold}" })
    }
}
