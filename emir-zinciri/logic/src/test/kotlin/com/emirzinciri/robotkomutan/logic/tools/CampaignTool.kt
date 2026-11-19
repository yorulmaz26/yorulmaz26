package com.emirzinciri.robotkomutan.logic.tools

import com.emirzinciri.robotkomutan.logic.gen.Campaign
import com.emirzinciri.robotkomutan.logic.level.AreaFile
import com.emirzinciri.robotkomutan.logic.level.LevelCatalog
import com.emirzinciri.robotkomutan.logic.level.LevelParser
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Kampanya üretimi: ./gradlew :logic:campaignTool --args="11 100" (alanlar paralel üretilir). */
fun main(args: Array<String>) {
    val dir = File(System.getProperty("levels.dir"))
    val from = args.getOrNull(0)?.toInt() ?: 11
    val to = args.getOrNull(1)?.toInt() ?: from
    val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    val t0 = System.currentTimeMillis()
    for (area in from..to) pool.submit {
        try {
            val t = System.currentTimeMillis()
            val r = Campaign.generateArea(area)
            File(dir, LevelCatalog.areaFileName(area)).writeText(LevelParser.json.encodeToString(AreaFile.serializer(), AreaFile(area, r.levels)) + "\n")
            println("Alan %3d: %s · deneme %4d · %6dms · par %s · robot %s".format(area, Campaign.features(area).joinToString(","), r.attempts,
                System.currentTimeMillis() - t, r.levels.map { it.par }, r.levels.map { it.robots.size }))
        } catch (e: Throwable) { println("Alan $area HATA: ${e.message}") }
    }
    pool.shutdown(); pool.awaitTermination(3, TimeUnit.HOURS)
    println("Toplam ${System.currentTimeMillis() - t0}ms")
}
