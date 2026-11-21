package com.besaniyelik.dunya.tools

import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.utils.GdxNativesLoader
import com.besaniyelik.dunya.level.*
import java.util.Random

/** Teşhis: bir taslağın aşamalarını, desenlerini ve (çözülürse) zıplama paylarını yazar. Kullanım: id deneme tShift */
object DraftDump {
    @JvmStatic
    fun main(args: Array<String>) {
        GdxNativesLoader.load(); Box2D.init()
        val id = args[0].toInt(); val shift = args.getOrElse(2) { "0" }.toFloat()
        for (attempt in args[1].split(",").map { it.toInt() }) {
            val draft = LevelGenerator.generate(id, attempt, shift)
            val d = draft.data
            println("== #$id deneme $attempt tShift $shift  hedef ${GameContent.targetDifficulty(id)}  uzunluk ${d.goalX}")
            val res = LevelForge.trySolve(d, 160_000)
            var m: LevelMetrics? = null
            if (res.success) {
                val tg = LevelAnalyzer.humanize(d, res.toggles)
                val traj = LevelForge.trace(d, tg)
                if (traj != null) { val lv = LevelForge.placeItems(draft, traj, tg, Random(d.seed + 99)); m = LevelAnalyzer.measure(lv, tg, trials = 24) }
            } else println("   çözülemedi: ${res.reason}")
            for ((i, ph) in d.phases.withIndex()) {
                val x1 = d.phases.getOrNull(i + 1)?.x ?: d.goalX
                val feats = d.features.filter { it.x >= ph.x && it.x < x1 }
                val jumps = m?.let { mm -> (0 until mm.jumps).filter { mm.jumpX[it] >= ph.x && mm.jumpX[it] < x1 }.joinToString(" ") { "w${mm.windows[it]}/h${mm.holdWindows[it]}" } } ?: ""
                println("  %6.1f %-28s %s   | %s".format(ph.x, ph.rules.joinToString("+") { it.name.take(6) }, feats.joinToString(" ") { "${it.p.name.take(9)}@${it.x.toInt()}" }, jumps))
            }
            if (m != null) println("   ölçülen %.1f  P=%s  zıplama %d  en dar %d/%d".format(m.score, m.pComplete.joinToString("/") { "%.2f".format(it) }, m.jumps, m.minWindow, m.minHold))
        }
    }
}
