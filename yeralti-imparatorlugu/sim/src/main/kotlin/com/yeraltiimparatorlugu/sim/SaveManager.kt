package com.yeraltiimparatorlugu.sim

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.zip.CRC32

/** Platformdan bağımsız dosya erişimi (Android'de libGDX yerel depolaması, testlerde bellek). */
interface SaveStorage {
    fun read(name: String): String?
    fun write(name: String, data: String)
    fun exists(name: String): Boolean
    fun move(from: String, to: String)
    fun delete(name: String)
}

class MemoryStorage : SaveStorage {
    val files = HashMap<String, String>()
    override fun read(name: String) = files[name]
    override fun write(name: String, data: String) { files[name] = data }
    override fun exists(name: String) = files.containsKey(name)
    override fun move(from: String, to: String) { files.remove(from)?.let { files[to] = it } }
    override fun delete(name: String) { files.remove(name) }
}

@Serializable
private data class Envelope(val format: String = "yeralti-save", val version: Int, val crc: Long, val payload: String)

class LoadResult(val sim: Simulation, val offline: OfflineReport, val usedBackup: Boolean, val maxSeenMillis: Long)

/**
 * Sürümlü, sağlama toplamlı ve yedekli kayıt. Yazma önce geçici dosyaya yapılır, sonra asıl dosyanın yerine
 * taşınır; önceki kayıt `.bak` olarak saklanır. Bozuk ana kayıtta yedekten yüklenir.
 */
class SaveManager(private val storage: SaveStorage, private val name: String = "koloni.json") {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val backup = "$name.bak"
    private val temp = "$name.tmp"

    var maxSeenMillis = 0L
        private set

    fun hasSave(): Boolean = storage.exists(name) || storage.exists(backup)

    fun save(sim: Simulation, nowMillis: Long) {
        maxSeenMillis = maxOf(maxSeenMillis, nowMillis)
        val payload = json.encodeToString(GameSave.serializer(), SaveMapper.toSave(sim, nowMillis, maxSeenMillis))
        val env = Envelope(version = SAVE_VERSION, crc = crc(payload), payload = payload)
        storage.write(temp, json.encodeToString(Envelope.serializer(), env))
        if (storage.exists(name)) {
            if (storage.exists(backup)) storage.delete(backup)
            storage.move(name, backup)
        }
        storage.move(temp, name)
    }

    /** Kaydı yükler ve çevrimdışı ilerlemeyi uygular. Kayıt yoksa veya iki kopya da bozuksa null döner. */
    fun load(nowMillis: Long): LoadResult? {
        var usedBackup = false
        val save = decode(storage.read(name)) ?: decode(storage.read(backup))?.also { usedBackup = true } ?: return null
        maxSeenMillis = maxOf(save.maxSeenMillis, save.savedAtMillis)
        val sim = SaveMapper.fromSave(save)
        val report = OfflineProgress.apply(sim, save.savedAtMillis, maxSeenMillis, nowMillis)
        maxSeenMillis = maxOf(maxSeenMillis, nowMillis)
        return LoadResult(sim, report, usedBackup, maxSeenMillis)
    }

    fun delete() {
        storage.delete(name); storage.delete(backup); storage.delete(temp)
    }

    private fun decode(text: String?): GameSave? {
        if (text.isNullOrBlank()) return null
        return try {
            val env = json.decodeFromString(Envelope.serializer(), text)
            if (env.format != "yeralti-save" || crc(env.payload) != env.crc) return null
            val element = json.parseToJsonElement(env.payload).jsonObject
            json.decodeFromJsonElement(GameSave.serializer(), migrate(element))
        } catch (e: Exception) {
            null
        }
    }

    /** Eski sürüm kayıtlarını güncel yapıya dönüştürür. Yeni sürümlerde adımlar buraya eklenir. */
    private fun migrate(obj: JsonObject): JsonObject {
        val v = obj["version"]?.jsonPrimitive?.int ?: 1
        require(v <= SAVE_VERSION) { "Daha yeni bir sürümün kaydı" }
        return obj
    }

    private fun crc(s: String): Long = CRC32().apply { update(s.toByteArray(Charsets.UTF_8)) }.value
}
