package com.besaniyelik.dunya.tools

import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.utils.GdxNativesLoader
import com.besaniyelik.dunya.level.*
import com.besaniyelik.dunya.sim.Mechanic
import com.besaniyelik.dunya.sim.SimConfig
import com.besaniyelik.dunya.sim.Simulation
import java.io.File
import java.util.concurrent.Executors

/**
 * 500 bölümün hepsini "insan gibi" oynatıp ölçer ve docs/difficulty_report.tsv dosyasına yazar.
 * Kullanım: gradlew :core:analyzeLevels
 *   -Plevels=1-50      yalnızca bu aralık (tek bölüm verilirse dokunuş payları ayrıntılı yazdırılır)
 *   -Phumanize=1       kayıtlı çözümü önce doğallaştırıp ortalar (eski, çözücü biçimli çözümler için)
 *   -Pdump=<klasör>    her bölüm için yörünge + zıplama pencereleri JSON'u (bölüm şeridi çizimi için)
 */
object LevelAnalyzeTool {
    @JvmStatic
    fun main(args: Array<String>) {
        GdxNativesLoader.load(); Box2D.init()
        val assets = File(args.getOrElse(0) { "." })
        LevelRepository.reader = { File(assets, it).readText() }
        val range = (args.getOrNull(1) ?: "1-500").split("-").let { it[0].toInt()..it.last().toInt() }
        val humanize = args.getOrNull(2) == "1"
        val out = File(args.getOrElse(3) { File(assets, "../docs/difficulty_report.tsv").path })
        val dump = args.getOrNull(4)?.takeIf { it.isNotBlank() && it != "-" }?.let { File(it).apply { mkdirs() } }
        if (range.first == range.last) {
            val l = LevelRepository.level(range.first)
            val raw = l.solution.toIntArray()
            val nat = LevelAnalyzer.normalize(l, raw)
            val cen = LevelAnalyzer.center(l, nat)
            for ((name, sol) in listOf("kayıtlı" to raw, "doğal" to nat, "ortalanmış" to cen)) println("$name: " + LevelAnalyzer.debug(l, sol))
        }
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
        val t0 = System.currentTimeMillis()
        val futures = range.map { id -> id to pool.submit<String> {
            val l = LevelRepository.level(id)
            var sol = l.solution.toIntArray()
            if (humanize) sol = LevelAnalyzer.humanize(l, sol)
            val m = LevelAnalyzer.measure(l, sol)
            val combo = l.phases.maxOf { it.rules.count { r -> r != Mechanic.NORMAL } }
            val w = m?.windows?.sorted() ?: emptyList()
            fun pct(q: Float) = if (w.isEmpty()) 0 else w[((w.size - 1) * q).toInt()]
            if (dump != null && m != null) dumpLevel(File(dump, "lv_%03d.json".format(id)), l, sol, m)
            val hot = m?.hotspot ?: (0f to 0f)
            listOf(id, l.kind, l.difficulty, "%.2f".format(GameContent.targetDifficulty(id)), GameContent.areaNumber(id), "%.1f".format((m?.ticks ?: l.solveTicks) / 60f), l.phases.size, combo,
                m?.jumps ?: -1, m?.minWindow ?: -1, pct(0.1f), pct(0.5f), m?.minHold ?: -1, m?.tight(8) ?: -1, m?.tight(12) ?: -1,
                m?.pComplete?.joinToString("\t") { "%.2f".format(it) } ?: "-\t-\t-",
                "%.2f".format(m?.expectedDeaths ?: -1f), "%.2f".format(m?.score ?: -1f), "%.0f".format(hot.first), "%.2f".format(hot.second),
                l.phases.joinToString("|") { p -> p.rules.joinToString("+") { it.name.take(4) } }).joinToString("\t")
        } }
        val lines = ArrayList<String>()
        for ((id, f) in futures) { val s = f.get(); lines.add(s); if (id % 50 == 0) System.err.println("analiz $id / ${range.last} (${(System.currentTimeMillis() - t0) / 1000} sn)") }
        pool.shutdown()
        val header = "id\ttür\tzorluk\thedef\talan\tsüre_sn\taşama\tkombo\tzıplama\tmin_pencere\tp10_pencere\tmedyan_pencere\tmin_tutma\tdar8\tdar12\tP_usta\tP_orta\tP_acemi\tbeklenen_ölüm\tölçülen_zorluk\tzor_nokta_x\tzor_nokta_pay\tkurallar"
        out.parentFile.mkdirs()
        out.writeText((listOf(header) + lines).joinToString("\n") + "\n")
        println("Analiz bitti: ${lines.size} bölüm, ${(System.currentTimeMillis() - t0) / 1000} sn -> ${out.path}")
    }

    /** Bölüm şeridi çizimi için yörünge ve ölçümleri JSON olarak yazar. */
    fun dumpLevel(file: File, l: LevelData, sol: IntArray, m: LevelMetrics) {
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        val sim = Simulation(l, SimConfig(hearts = 1, powerups = false))
        try {
            xs.add(sim.player.x); ys.add(sim.player.y)
            LevelSolver.replay(sim, sol) { s -> xs.add(s.player.x); ys.add(s.player.y) }
        } finally { sim.dispose() }
        val sb = StringBuilder()
        sb.append("{\"id\":${l.id},\"score\":${"%.2f".format(m.score)},\"p\":[${m.pComplete.joinToString(",") { "%.3f".format(it) }}],")
        sb.append("\"traj\":[")
        var first = true
        for (i in xs.indices step 2) { if (!first) sb.append(','); first = false; sb.append("[${"%.2f".format(xs[i])},${"%.2f".format(ys[i])}]") }
        sb.append("],\"jumps\":[")
        for (j in 0 until m.jumps) {
            if (j > 0) sb.append(',')
            val t = m.jumpTick[j].coerceIn(0, ys.size - 1)
            sb.append("{\"t\":${m.jumpTick[j]},\"x\":${"%.2f".format(m.jumpX[j])},\"y\":${"%.2f".format(ys[t])},\"w\":${m.windows[j]},\"h\":${m.holdWindows[j]}}")
        }
        sb.append("],\"deaths\":[${m.deathX.joinToString(",") { "%.1f".format(it) }}]}")
        file.writeText(sb.toString())
    }
}
