package com.yanlisrenk.logic.level

import com.yanlisrenk.logic.model.LevelData

/**
 * Bölüm deposu. Alan paketleri ihtiyaç olunca yüklenir ([loader] dosya adından metin döndürür).
 * Yüklenemezse bölüm, aynı tohumla yeniden üretilir (veri dosyası bozulsa bile oyun çalışır).
 */
class LevelRepository(private val loader: (String) -> String?) {
    private val packs = HashMap<Int, AreaPack>()
    private val masteryCache = HashMap<Int, LevelData>()

    fun pack(area: Int): AreaPack = packs.getOrPut(area) {
        val text = runCatching { loader(LevelJson.fileName(area)) }.getOrNull()
        val decoded = text?.let { runCatching { LevelJson.decode(it) }.getOrNull() }
        decoded ?: AreaPack(AreaCatalog.area(area), (AreaCatalog.area(area).firstLevel..AreaCatalog.area(area).lastLevel).map { LevelGenerator.generateLevel(it) })
    }

    fun level(id: Int): LevelData {
        val a = AreaCatalog.areaOfLevel(id)
        return pack(a).levels[(id - 1) % 5]
    }

    fun mastery(id: Int): LevelData = masteryCache.getOrPut(id) { LevelGenerator.mastery(level(id)) }

    companion object {
        const val LEVEL_COUNT = 500
        const val AREA_COUNT = 100
    }
}
