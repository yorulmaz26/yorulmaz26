package com.kayipadalar.logic.tools

import com.kayipadalar.logic.data.GameConfig
import com.kayipadalar.logic.data.GameJson
import com.kayipadalar.logic.level.AreaCatalog
import com.kayipadalar.logic.level.AreaFile
import com.kayipadalar.logic.level.LevelGenerator
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Alan dosyalarını (assets/levels/area_XXX.json) üretir.
 * Kullanım: generateLevels <assetsDir> [ilkAlan] [sonAlan]. Elle tasarlanmış alan 1 korunur.
 */
fun main(args: Array<String>) {
    val assets = File(args.getOrElse(0) { "assets" })
    val list: List<Int> = args.getOrNull(1)?.let { a -> if (a.contains(',')) a.split(',').map { it.trim().toInt() } else null }
        ?: ((args.getOrNull(1)?.toInt() ?: 2)..(args.getOrNull(2)?.toInt() ?: AreaCatalog.AREAS)).toList()
    val cfg = GameConfig.parse(File(assets, "data/rules.json").readText())
    val gen = LevelGenerator(cfg)
    val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    val started = System.currentTimeMillis()
    for (a in list) {
        if (a == 1) continue
        pool.submit {
            try {
                val spec = AreaCatalog.area(a)
                val t0 = System.currentTimeMillis()
                val levels = (spec.firstLevel until spec.firstLevel + 5).map { gen.generateMain(it) }
                val file = AreaFile(a, spec.name, spec.theme, spec.newFeature?.name ?: spec.focus.joinToString("+") { it.name }, levels)
                File(assets, "levels/area_%03d.json".format(a)).writeText(GameJson.encodeToString(AreaFile.serializer(), file))
                println("Alan $a (${spec.name}) ${System.currentTimeMillis() - t0} ms")
            } catch (e: Throwable) {
                System.err.println("Alan $a HATA: $e"); e.printStackTrace()
            }
        }
    }
    pool.shutdown()
    pool.awaitTermination(3, TimeUnit.HOURS)
    println("Tamamlandı: ${(System.currentTimeMillis() - started) / 1000} sn")
}
