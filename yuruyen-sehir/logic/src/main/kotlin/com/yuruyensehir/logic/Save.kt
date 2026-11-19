package com.yuruyensehir.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.zip.CRC32

/** Kayıt dosyası depolama soyutlaması (libGDX dosyaları, testlerde bellek). */
interface SaveStorage {
    fun read(name: String): String?
    fun write(name: String, content: String)
    fun delete(name: String)
}

class MemoryStorage : SaveStorage {
    val files = HashMap<String, String>()
    override fun read(name: String) = files[name]
    override fun write(name: String, content: String) { files[name] = content }
    override fun delete(name: String) { files.remove(name) }
}

class SaveCorruptException(msg: String) : Exception(msg)

object SaveCodec {
    const val CURRENT_VERSION = 2

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowStructuredMapKeys = true
        coerceInputValues = true
    }

    /** Kayıt: {"crc": <CRC32>, "version": n, "data": {...}} */
    fun encode(state: GameState): String {
        state.version = CURRENT_VERSION
        val data = json.encodeToString(GameState.serializer(), state)
        val crc = CRC32().apply { update(data.toByteArray(Charsets.UTF_8)) }.value
        return "{\"crc\":$crc,\"version\":$CURRENT_VERSION,\"data\":$data}"
    }

    fun decode(text: String): GameState {
        val root = try { json.parseToJsonElement(text).jsonObject } catch (e: Exception) { throw SaveCorruptException("JSON okunamadı") }
        val dataEl = root["data"] ?: throw SaveCorruptException("Veri yok")
        val crc = root["crc"]?.jsonPrimitive?.long ?: throw SaveCorruptException("Sağlama yok")
        val dataText = dataEl.toString()
        val actual = CRC32().apply { update(dataText.toByteArray(Charsets.UTF_8)) }.value
        if (actual != crc) throw SaveCorruptException("Sağlama tutmuyor")
        val version = root["version"]?.jsonPrimitive?.int ?: 1
        val migrated = migrate(dataEl.jsonObject, version)
        return try { json.decodeFromJsonElement(GameState.serializer(), migrated) } catch (e: Exception) { throw SaveCorruptException("Durum çözülemedi: ${e.message}") }
    }

    /** Eski kayıt sürümlerini güncel biçime taşır. Yeni sürümlerde buraya adım eklenir. */
    fun migrate(data: JsonObject, fromVersion: Int): JsonObject {
        var obj = data
        var v = fromVersion
        while (v < CURRENT_VERSION) {
            obj = when (v) {
                // 1 -> 2: medeniyetler, itibar ve kervanlar eklendi. Eksik alanlar varsayılanlarını alır;
                // dünya yeniden tarandığında medeniyetler deterministik olarak üretilir.
                1 -> obj
                else -> obj
            }
            v++
        }
        return buildJsonObject {
            obj.forEach { (k, e) -> put(k, e) }
            put("version", JsonPrimitive(CURRENT_VERSION))
        }
    }
}

/**
 * Güvenilir kayıt: önce geçici dosyaya yazılır, doğrulanır, sonra eski kayıt yedeğe alınıp yenisi yazılır.
 * Ana kayıt bozulursa yedekten yüklenir.
 */
class SaveManager(private val storage: SaveStorage) {
    var lastLoadSource: String = "none"
        private set

    fun save(state: GameState, nowEpochMs: Long) {
        state.lastSavedEpochMs = nowEpochMs
        val text = SaveCodec.encode(state)
        storage.write(TMP, text)
        // Yazılanı doğrula
        val check = storage.read(TMP)
        if (check != text) throw IllegalStateException("Kayıt doğrulanamadı")
        storage.read(MAIN)?.let { old -> storage.write(BACKUP, old) }
        storage.write(MAIN, text)
        storage.delete(TMP)
    }

    fun load(): GameState? {
        for ((name, label) in listOf(MAIN to "main", BACKUP to "backup", TMP to "tmp")) {
            val text = storage.read(name) ?: continue
            try {
                val s = SaveCodec.decode(text)
                lastLoadSource = label
                return s
            } catch (_: SaveCorruptException) {
                continue
            }
        }
        lastLoadSource = "none"
        return null
    }

    fun hasSave() = storage.read(MAIN) != null || storage.read(BACKUP) != null

    fun wipe() {
        storage.delete(MAIN); storage.delete(BACKUP); storage.delete(TMP)
    }

    companion object {
        const val MAIN = "yuruyen_sehir_save.json"
        const val BACKUP = "yuruyen_sehir_save.bak"
        const val TMP = "yuruyen_sehir_save.tmp"
    }
}
