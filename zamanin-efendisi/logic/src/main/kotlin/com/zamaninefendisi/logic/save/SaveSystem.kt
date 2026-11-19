package com.zamaninefendisi.logic.save

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Platforma bağlı dosya erişimi (libGDX'te Gdx.files.local, testlerde bellek). */
interface SaveStorage {
    fun read(name: String): String?
    fun write(name: String, content: String)
}

class InMemoryStorage : SaveStorage {
    val files = HashMap<String, String>()
    override fun read(name: String) = files[name]
    override fun write(name: String, content: String) { files[name] = content }
}

/**
 * Sürümlü kalıcı kayıt. Yazarken önce yedek alınır; ana kayıt bozulursa yedekten yüklenir.
 * Eski sürüm kayıtlar [migrate] ile güncel biçime dönüştürülür; bilinmeyen alanlar yok sayılır.
 */
class SaveSystem(private val storage: SaveStorage) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowStructuredMapKeys = true }

    var lastLoadProblem: String? = null
        private set

    fun load(): SaveData {
        lastLoadProblem = null
        val main = storage.read(MAIN)
        if (main != null) {
            decode(main)?.let { return it }
            lastLoadProblem = "Ana kayıt okunamadı, yedek deneniyor"
            storage.write("$MAIN.bozuk", main)
        }
        val backup = storage.read(BACKUP)
        if (backup != null) decode(backup)?.let { return it }
        if (main != null) lastLoadProblem = "Kayıt ve yedek okunamadı; yeni oyun başlatıldı"
        return SaveData()
    }

    fun save(data: SaveData) {
        val text = json.encodeToString(SaveData.serializer(), data.copy(version = SAVE_VERSION))
        storage.read(MAIN)?.let { if (decode(it) != null) storage.write(BACKUP, it) }
        storage.write(MAIN, text)
    }

    fun encode(data: SaveData): String = json.encodeToString(SaveData.serializer(), data)

    fun decode(text: String): SaveData? = try {
        val obj = json.parseToJsonElement(text).jsonObject
        json.decodeFromJsonElement(SaveData.serializer(), migrate(obj))
    } catch (e: Exception) {
        null
    }

    companion object {
        const val MAIN = "zaman_kaydi.json"
        const val BACKUP = "zaman_kaydi.yedek.json"

        /** Kayıt sürüm göçleri. Her adım bir önceki sürümden bir sonrakine dönüştürür. */
        fun migrate(input: JsonObject): JsonObject {
            var obj = input
            var v = obj["version"]?.jsonPrimitive?.intOrNull ?: 0
            while (v < SAVE_VERSION) {
                obj = when (v) {
                    0 -> migrate0to1(obj)
                    1 -> migrate1to2(obj)
                    else -> obj
                }
                v++
            }
            return obj
        }

        /** Sürüm 1 → 2: altın ve kristal çok kaynaklı cüzdana taşınır. */
        private fun migrate1to2(o: JsonObject): JsonObject = buildJsonObject {
            o.forEach { (k, v) -> if (k != "version" && k != "wallet") put(k, v) }
            put("version", 2)
            putJsonObject("wallet") {
                put("GOLD", o["gold"]?.jsonPrimitive?.intOrNull ?: 0)
                put("CRYSTAL", o["crystals"]?.jsonPrimitive?.intOrNull ?: 0)
            }
            put("gold", 0)
            put("crystals", 0)
        }

        /** Sürüm 0 (ön sürüm): yıldızlar "stars": {"1": 3} biçimindeydi ve altın "coins" adını taşıyordu. */
        private fun migrate0to1(o: JsonObject): JsonObject = buildJsonObject {
            o.forEach { (k, v) -> if (k != "stars" && k != "coins" && k != "version") put(k, v) }
            put("version", 1)
            o["coins"]?.jsonPrimitive?.intOrNull?.let { put("gold", it) }
            val stars = o["stars"]?.jsonObject
            if (stars != null && "levels" !in o) {
                putJsonObject("levels") {
                    stars.forEach { (id, s) ->
                        putJsonObject(id) {
                            put("stars", s.jsonPrimitive.int)
                            put("completions", 1)
                            put("attempts", 1)
                        }
                    }
                }
            }
        }
    }
}
