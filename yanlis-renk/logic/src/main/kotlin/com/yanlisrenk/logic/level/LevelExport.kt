package com.yanlisrenk.logic.level

import com.yanlisrenk.logic.model.LevelData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Bir alanın 5 bölümünü içeren veri dosyası (levels/area_XXX.json). */
@Serializable
data class AreaPack(val area: AreaInfo, val levels: List<LevelData>)

object LevelJson {
    val json = Json { encodeDefaults = false; ignoreUnknownKeys = true; explicitNulls = false }
    fun encode(pack: AreaPack): String = json.encodeToString(pack)
    fun decode(text: String): AreaPack = json.decodeFromString(AreaPack.serializer(), text)
    fun fileName(area: Int) = "area_%03d.json".format(area)
}

/** Bölüm verisini üretir ve yazar. Kullanım: ./gradlew :logic:generateLevels */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "levels")
    out.mkdirs()
    val t0 = System.currentTimeMillis()
    var total = 0
    for (a in AreaCatalog.areas) {
        val levels = (a.firstLevel..a.lastLevel).map { LevelGenerator.generateLevel(it) }
        total += levels.size
        File(out, LevelJson.fileName(a.id)).writeText(LevelJson.encode(AreaPack(a, levels)))
    }
    val worlds = LevelJson.json.encodeToString(AreaCatalog.worlds)
    File(out, "worlds.json").writeText(worlds)
    println("Yazıldı: $total bölüm, ${AreaCatalog.areas.size} alan, yedek üretim=${LevelGenerator.fallbackCount}, süre=${System.currentTimeMillis() - t0} ms → ${out.absolutePath}")
}
