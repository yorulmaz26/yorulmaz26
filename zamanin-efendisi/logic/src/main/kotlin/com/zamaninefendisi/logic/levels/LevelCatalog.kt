package com.zamaninefendisi.logic.levels

import com.zamaninefendisi.logic.LevelDef

/** Alan tanımı. Elle tasarlanan alanların bölümleri sabittir; diğerlerininki ilk kullanımda üretilir ve önbelleğe alınır. */
class AreaDef(
    val id: Int,
    val name: String,
    val unlocks: String,
    val description: String,
    levelsProvider: () -> List<LevelDef>,
    val handcrafted: Boolean = true,
) {
    constructor(id: Int, name: String, unlocks: String, description: String, levels: List<LevelDef>) : this(id, name, unlocks, description, { levels }, true)

    val levels: List<LevelDef> by lazy(levelsProvider)
    val firstId: Int get() = (id - 1) * 5 + 1
    val ids: IntRange get() = firstId until firstId + 5
    val boss: Boolean get() = true
    val world: Int get() = (id - 1) / 20 + 1
}

/** 100 alan · 500 bölüm. Alan 1–7 elle tasarlandı; Alan 8–100 alan kurallarıyla üretilir ve doğrulanır. */
object LevelCatalog {
    const val AREA_COUNT = 100
    const val LEVEL_COUNT = 500
    val ids: IntRange = 1..LEVEL_COUNT

    private val handcrafted: List<AreaDef> by lazy { listOf(Area01.area, Area02.area, Area03.area, Area04.area, Area05.area, Area06.area, Area07.area) }

    val areas: List<AreaDef> by lazy {
        handcrafted + PROCEDURAL_AREAS.map { (a, name, feature) ->
            AreaDef(a, name, feature, ProceduralLevels.description(a, feature), { ProceduralLevels.area(a, name, feature) }, handcrafted = false)
        }
    }

    val handcraftedLevels: List<LevelDef> by lazy { handcrafted.flatMap { it.levels } }

    /** Bütün 500 bölüm (üretilenler dahil; ağırdır — testler ve toplu doğrulama için). */
    val levels: List<LevelDef> get() = ids.map { level(it)!! }

    fun areaOfLevel(id: Int): AreaDef = areas[(id - 1) / 5]
    fun level(id: Int): LevelDef? = if (id !in ids) null else areaOfLevel(id).levels[(id - 1) % 5]
    fun area(id: Int): AreaDef? = areas.getOrNull(id - 1)
    fun next(id: Int): LevelDef? = level(id + 1)
    val lastId: Int get() = LEVEL_COUNT

    /** Bölüm adı (üretilen bölümleri üretmeden). */
    fun levelName(id: Int): String {
        val a = areaOfLevel(id)
        return if (a.handcrafted) a.levels[(id - 1) % 5].name else ProceduralLevels.name(a.id, a.name, (id - 1) % 5 + 1)
    }

    fun isBoss(id: Int) = id % 5 == 0
}
