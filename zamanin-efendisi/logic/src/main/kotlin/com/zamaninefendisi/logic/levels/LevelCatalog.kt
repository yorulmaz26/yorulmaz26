package com.zamaninefendisi.logic.levels

import com.zamaninefendisi.logic.LevelDef

class AreaDef(val id: Int, val name: String, val unlocks: String, val description: String, val levels: List<LevelDef>)

/** Oynanabilir alanlar ve bölümler. Yeni alanlar buraya eklenir; her alan 5 bölümdür. */
object LevelCatalog {
    val areas: List<AreaDef> by lazy { listOf(Area01.area, Area02.area, Area03.area, Area04.area, Area05.area, Area06.area, Area07.area) }

    val levels: List<LevelDef> by lazy { areas.flatMap { it.levels } }

    fun level(id: Int): LevelDef? = levels.firstOrNull { it.id == id }
    fun area(id: Int): AreaDef? = areas.firstOrNull { it.id == id }
    fun next(id: Int): LevelDef? = levels.firstOrNull { it.id == id + 1 }
    val lastId: Int get() = levels.maxOf { it.id }
}
