package com.besaniyelik.dunya.tools

import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.utils.GdxNativesLoader
import com.besaniyelik.dunya.level.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * 500 bölümü üretir, doğrular ve assets/levels/map_XX.json dosyalarına yazar.
 * Kullanım: gradlew :core:generateLevels  (isteğe bağlı: -Plevels=1-20)
 */
object LevelBuildTool {
    @JvmStatic
    fun main(args: Array<String>) {
        GdxNativesLoader.load()
        Box2D.init()
        val outDir = File(args.getOrElse(0) { "levels" })
        outDir.mkdirs()
        val range = (System.getProperty("levels") ?: args.getOrNull(1) ?: "1-500").split("-").let { it[0].toInt()..it[1].toInt() }
        val threads = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
        val pool = Executors.newFixedThreadPool(threads)
        val t0 = System.currentTimeMillis()
        val futures = LinkedHashMap<Int, Future<ForgeResult>>()
        for (id in range) futures[id] = pool.submit<ForgeResult> { LevelForge.forge(id) { System.err.println(it) } }
        val results = HashMap<Int, ForgeResult>()
        for ((id, f) in futures) {
            val r = f.get()
            results[id] = r
            val rep = r.report
            println("Bölüm %3d | %-30s | zorluk %2d | aşama %2d | deneme %2d | düğüm %7d | %5.1f sn | altın %3d | %s".format(
                id, r.level.name, r.level.difficulty, r.level.phases.size, rep.attempts, rep.nodes, rep.solveTicks / 60f, rep.coins,
                if (rep.fair) "adil(±${rep.fairShift})" else "DAR"))
        }
        pool.shutdown()
        // Harita dosyalarını yaz (var olan dosyadaki diğer bölümleri koru)
        val byMap = results.values.groupBy { it.level.map }
        for ((map, list) in byMap) {
            val file = File(outDir, "map_%02d.json".format(map))
            val existing = if (file.exists()) LevelRepository.json.decodeFromString(MapPack.serializer(), file.readText()).levels else emptyList()
            val merged = (existing.filter { e -> list.none { it.level.id == e.id } } + list.map { it.level }).sortedBy { it.id }
            file.writeText(LevelRepository.json.encodeToString(MapPack.serializer(), MapPack(map, merged)))
        }
        val report = File(outDir, "../../docs/level_report.tsv")
        report.parentFile.mkdirs()
        val lines = results.values.sortedBy { it.level.id }.map { r ->
            listOf(r.level.id, r.level.name, r.level.kind, r.level.difficulty, r.level.phases.size, r.report.attempts, r.report.nodes,
                "%.1f".format(r.report.solveTicks / 60f), r.report.coins, r.level.stars.size, if (r.level.piece != null) 1 else 0,
                if (r.report.fair) "adil±${r.report.fairShift}" else "dar").joinToString("\t")
        }
        val header = "id\tad\ttür\tzorluk\taşama\tdeneme\tdüğüm\tsüre_sn\taltın\tyıldız\tparça\tzamanlama"
        if (range.first == 1 && range.last == 500) report.writeText((listOf(header) + lines).joinToString("\n") + "\n")
        println("Toplam ${results.size} bölüm, ${(System.currentTimeMillis() - t0) / 1000} sn. Adil: ${results.values.count { it.report.fair }}")
    }
}
