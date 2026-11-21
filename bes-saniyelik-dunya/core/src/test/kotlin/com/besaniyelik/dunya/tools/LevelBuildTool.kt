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
        // "1-500" ya da virgülle ayrılmış aralıklar: "31-40,131-140"
        val spec = System.getProperty("levels") ?: args.getOrNull(1) ?: "1-500"
        val ids = spec.split(",").flatMap { part -> part.trim().split("-").let { (it[0].toInt()..it.last().toInt()).toList() } }.distinct().sorted()
        val range = ids.first()..ids.last()
        // -Prestamp=1: bölümleri yeniden üretmeden yalnızca zorluk etiketlerini güncel hedef eğrisine göre yeniden yazar
        if ((System.getProperty("restamp") ?: args.getOrNull(4) ?: "0") == "1") {
            var changed = 0
            for (map in 1..10) {
                val file = File(outDir, "map_%02d.json".format(map))
                val pack = LevelRepository.json.decodeFromString(MapPack.serializer(), file.readText())
                val levels = pack.levels.map { l -> val d = GameContent.difficulty(l.id); if (d != l.difficulty) { changed++; l.copy(difficulty = d) } else l }
                file.writeText(LevelRepository.json.encodeToString(MapPack.serializer(), MapPack(map, levels)))
            }
            println("Zorluk etiketi güncellenen bölüm: $changed")
            return
        }
        val threads = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
        val pool = Executors.newFixedThreadPool(threads)
        val t0 = System.currentTimeMillis()
        val futures = LinkedHashMap<Int, Future<ForgeResult>>()
        // -Poffset=N: deneme tohumlarını kaydırır; -Pimprove=1: yalnızca mevcut bölümden daha iyi (hedefe daha yakın) sonuç yazılır
        val offset = (System.getProperty("offset") ?: args.getOrNull(2) ?: "0").toInt()
        val improve = (System.getProperty("improve") ?: args.getOrNull(3) ?: "0") == "1"
        if (improve) LevelRepository.reader = { File(outDir.parentFile, it).readText() }
        val kept = java.util.concurrent.ConcurrentHashMap<Int, String>()
        for (id in ids) futures[id] = pool.submit<ForgeResult> {
            val fresh = LevelForge.forge(id, seedOffset = offset) { System.err.println(it) }
            if (!improve) return@submit fresh
            val old = LevelRepository.level(id)
            val target = GameContent.targetDifficulty(id)
            val om = LevelAnalyzer.measure(old, old.solution.toIntArray())
            val oldErr = if (om == null || om.minWindow < LevelAnalyzer.minWindowFor(target) || om.minHold < LevelAnalyzer.minHoldFor(target)) 99f else kotlin.math.abs(om.score - target)
            val newErr = if (fresh.report.minWindow < LevelAnalyzer.minWindowFor(target)) 99f else kotlin.math.abs(fresh.report.measured - target)
            if (om != null && newErr >= oldErr - 0.15f) {
                kept[id] = "eski korundu (eski sapma %.1f, yeni %.1f)".format(oldErr, newErr)
                ForgeResult(old, ForgeReport(id, 0, 0, old.solveTicks, oldErr <= LevelForge.tolerance(target), target, om.score, om.minWindow, om.minHold, om.pComplete, old.coins.size, "eski korundu"))
            } else fresh
        }
        val results = HashMap<Int, ForgeResult>()
        // Bir haritanın istenen bütün bölümleri bitince dosyası hemen yazılır (uzun üretim yarıda kesilirse emek kaybolmasın)
        fun writeMap(map: Int) {
            val list = results.values.filter { it.level.map == map }
            val file = File(outDir, "map_%02d.json".format(map))
            val existing = if (file.exists()) LevelRepository.json.decodeFromString(MapPack.serializer(), file.readText()).levels else emptyList()
            val merged = (existing.filter { e -> list.none { it.level.id == e.id } } + list.map { it.level }).sortedBy { it.id }
                .map { l -> if (l.difficulty == GameContent.difficulty(l.id)) l else l.copy(difficulty = GameContent.difficulty(l.id)) }
            file.writeText(LevelRepository.json.encodeToString(MapPack.serializer(), MapPack(map, merged)))
        }
        for ((id, f) in futures) {
            val r = f.get()
            results[id] = r
            val mapOfId = (id - 1) / 50 + 1
            if (ids.none { it > id && (it - 1) / 50 + 1 == mapOfId }) { writeMap(mapOfId); System.err.println("harita $mapOfId yazıldı (${(System.currentTimeMillis() - t0) / 1000} sn)") }
            val rep = r.report
            println("Bölüm %3d | %-30s | hedef %4.1f | ölçülen %4.1f | pencere %2d | deneme %2d | %5.1f sn | altın %3d | %s".format(
                id, r.level.name, rep.target, rep.measured, rep.minWindow, rep.attempts, rep.solveTicks / 60f, rep.coins,
                if (rep.fair) "uygun" else "SAPMA: ${rep.note}"))
        }
        pool.shutdown()
        val report = File(outDir, "../../docs/generation_report.tsv")
        report.parentFile.mkdirs()
        val lines = results.values.sortedBy { it.level.id }.map { r ->
            val rep = r.report
            listOf(r.level.id, r.level.name, r.level.kind, r.level.difficulty, "%.2f".format(rep.target), "%.2f".format(rep.measured), rep.minWindow, rep.minHold,
                rep.pComplete.joinToString("\t") { "%.2f".format(it) }, r.level.phases.size, rep.attempts, rep.nodes,
                "%.1f".format(rep.solveTicks / 60f), rep.coins, r.level.stars.size, if (r.level.piece != null) 1 else 0,
                if (rep.fair) "uygun" else rep.note).joinToString("\t")
        }
        val header = "id\tad\ttür\tzorluk\thedef\tölçülen\tmin_pencere\tmin_tutma\tP_usta\tP_orta\tP_acemi\taşama\tdeneme\tdüğüm\tsüre_sn\taltın\tyıldız\tparça\tdurum"
        if (ids.size == 500) report.writeText((listOf(header) + lines).joinToString("\n") + "\n")
        System.err.println("süre dökümü (iş parçacığı-sn): çöz %.0f, doğallaştır %.0f, ortala %.0f, ölç %.0f".format(LevelForge.tSolve.get() / 1e9, LevelForge.tNormalize.get() / 1e9, LevelForge.tCenter.get() / 1e9, LevelForge.tMeasure.get() / 1e9))
        println("Toplam ${results.size} bölüm, ${(System.currentTimeMillis() - t0) / 1000} sn. Hedef bandında: ${results.values.count { it.report.fair }}")
    }
}
