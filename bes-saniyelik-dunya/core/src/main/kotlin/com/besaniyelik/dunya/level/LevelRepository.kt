package com.besaniyelik.dunya.level

import kotlinx.serialization.json.Json

/** Bölüm verilerini (assets/levels/map_XX.json) yükler ve önbellekler. */
object LevelRepository {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
    private val cache = HashMap<Int, MapPack>()
    /** Dosya okuyucu; oyun Gdx.files, testler doğrudan dosya sistemi kullanır. */
    var reader: (String) -> String = { path -> java.io.File(path).readText() }

    fun fileName(map: Int) = "levels/map_%02d.json".format(map)

    fun pack(map: Int): MapPack = synchronized(cache) {
        cache.getOrPut(map) { json.decodeFromString(MapPack.serializer(), reader(fileName(map))) }
    }

    fun level(id: Int): LevelData {
        val map = (id - 1) / GameContent.LEVELS_PER_MAP + 1
        return pack(map).levels.first { it.id == id }
    }

    fun clear() = synchronized(cache) { cache.clear() }
}
