package com.emirzinciri.robotkomutan.logic.level

import kotlinx.serialization.Serializable

/** Bir alan dosyası: assets/levels/area_NNN.json */
@Serializable
data class AreaFile(val area: Int, val levels: List<LevelDef>)

/** Bölümlere erişim. Oyun bunu libGDX dosya sisteminden, testler doğrudan diskten doldurur. */
class LevelCatalog(levels: Collection<LevelDef>) {
    private val byId: Map<Int, LevelDef> = levels.associateBy { it.id }
    val ids: List<Int> = byId.keys.sorted()
    val count: Int get() = ids.size
    val maxId: Int get() = ids.lastOrNull() ?: 0

    operator fun get(id: Int): LevelDef? = byId[id]
    fun all(): List<LevelDef> = ids.map { byId.getValue(it) }
    fun ofArea(area: Int): List<LevelDef> = all().filter { it.area == area }
    fun has(id: Int) = id in byId

    companion object {
        fun fromAreaFiles(texts: List<String>): LevelCatalog =
            LevelCatalog(texts.flatMap { LevelParser.json.decodeFromString(AreaFile.serializer(), it).levels })

        fun areaFileName(area: Int) = "area_%03d.json".format(area)
    }
}
