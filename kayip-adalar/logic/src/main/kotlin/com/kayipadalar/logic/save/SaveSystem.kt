package com.kayipadalar.logic.save

import com.kayipadalar.logic.data.GameJson
import kotlinx.serialization.json.*
import java.util.zip.CRC32

/** Platforma özel dosya erişimi (libGDX local storage, testlerde bellek). */
interface SaveStorage {
    fun read(name: String): String?
    /** Önce geçici dosyaya yazıp sonra yerine taşır (atomik yazma). */
    fun writeAtomic(name: String, text: String)
    fun copy(from: String, to: String)
    fun exists(name: String): Boolean
}

class MemoryStorage : SaveStorage {
    val files = HashMap<String, String>()
    override fun read(name: String) = files[name]
    override fun writeAtomic(name: String, text: String) { files[name] = text }
    override fun copy(from: String, to: String) { files[from]?.let { files[to] = it } }
    override fun exists(name: String) = name in files
}

data class LoadResult(val data: SaveData, val source: Source, val message: String? = null) {
    enum class Source { MAIN, BACKUP, NEW }
}

/**
 * Sürümlü, sağlama toplamlı kayıt sistemi.
 * Biçim: "KA|<sürüm>|<crc32>\n<json>". Ana kayıt bozuksa yedekten, o da bozuksa yeni kayıtla açılır;
 * bozuk dosya incelenmek üzere "save.corrupt" olarak saklanır.
 */
class SaveSystem(private val storage: SaveStorage) {
    companion object {
        const val CURRENT_VERSION = 1
        const val MAIN = "kayip_adalar_save.dat"
        const val BACKUP = "kayip_adalar_save.bak"
        const val CORRUPT = "kayip_adalar_save.corrupt"

        private val migrations: Map<Int, (JsonObject) -> JsonObject> = mapOf(
            // 0 -> 1: sürüm alanı olmayan erken test kayıtları; eksik alanlar varsayılanlarla doldurulur.
            0 to { o -> JsonObject(o + ("version" to JsonPrimitive(1))) },
        )

        fun encode(data: SaveData): String {
            val json = GameJson.encodeToString(SaveData.serializer(), data)
            return "KA|${data.version}|${crc(json)}\n$json"
        }

        fun crc(s: String): Long = CRC32().also { it.update(s.toByteArray(Charsets.UTF_8)) }.value

        /** Kayıt metnini çözer; geçersizse null. */
        fun decode(text: String?): SaveData? {
            if (text.isNullOrBlank()) return null
            val nl = text.indexOf('\n')
            if (nl < 0 || !text.startsWith("KA|")) return null
            val header = text.substring(0, nl).split('|')
            if (header.size != 3) return null
            val body = text.substring(nl + 1)
            if (header[2].toLongOrNull() != crc(body)) return null
            return try {
                var obj = GameJson.parseToJsonElement(body).jsonObject
                var v = obj["version"]?.jsonPrimitive?.intOrNull ?: 0
                while (v < CURRENT_VERSION) {
                    obj = migrations[v]?.invoke(obj) ?: break
                    v = obj["version"]?.jsonPrimitive?.intOrNull ?: (v + 1)
                }
                GameJson.decodeFromJsonElement(SaveData.serializer(), obj)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun load(): LoadResult {
        val mainText = storage.read(MAIN)
        decode(mainText)?.let { return LoadResult(it, LoadResult.Source.MAIN) }
        val hadMain = mainText != null
        if (hadMain) storage.writeAtomic(CORRUPT, mainText!!)
        decode(storage.read(BACKUP))?.let {
            return LoadResult(it, LoadResult.Source.BACKUP, if (hadMain) "save.recovered" else null)
        }
        return LoadResult(SaveData(), LoadResult.Source.NEW, if (hadMain) "save.reset" else null)
    }

    fun save(data: SaveData) {
        data.version = CURRENT_VERSION
        val text = encode(data)
        // Mevcut ana kayıt geçerliyse yedeğe alınır.
        if (storage.exists(MAIN) && decode(storage.read(MAIN)) != null) storage.copy(MAIN, BACKUP)
        storage.writeAtomic(MAIN, text)
    }
}
