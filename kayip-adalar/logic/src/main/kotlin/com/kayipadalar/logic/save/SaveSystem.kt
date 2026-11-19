package com.kayipadalar.logic.save

import com.kayipadalar.logic.kingdom.GameJson
import com.kayipadalar.logic.kingdom.KingdomSave
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

data class LoadResult(val data: KingdomSave, val source: Source, val message: String? = null) {
    enum class Source { MAIN, BACKUP, NEW }
}

/**
 * Sürümlü, sağlama toplamlı kayıt. Biçim: "KA|<sürüm>|<crc32>\n<json>".
 * Ana kayıt bozuksa yedekten, o da bozuksa yeni oyunla açılır; bozuk dosya "corrupt" olarak saklanır.
 */
class SaveSystem(private val storage: SaveStorage) {
    companion object {
        const val CURRENT_VERSION = 2
        const val MAIN = "kayip_adalar_save.dat"
        const val BACKUP = "kayip_adalar_save.bak"
        const val CORRUPT = "kayip_adalar_save.corrupt"

        /** Sürüm 1 (eski bölüm tabanlı oyun) kayıtları yeni krallık oyununa taşınır: ad ve ayarlar korunur. */
        private val migrations: Map<Int, (JsonObject) -> JsonObject> = mapOf(
            0 to { o -> JsonObject(o + ("version" to JsonPrimitive(1))) },
            1 to { o ->
                val keep = buildMap<String, JsonElement> {
                    o["playerName"]?.let { put("playerName", it) }
                    (o["settings"] as? JsonObject)?.let { st ->
                        put("settings", JsonObject(st.filterKeys { it in setOf("sound", "music", "vibration") }))
                    }
                    put("version", JsonPrimitive(2))
                }
                JsonObject(keep)
            },
        )

        fun crc(s: String): Long = CRC32().also { it.update(s.toByteArray(Charsets.UTF_8)) }.value

        fun encode(data: KingdomSave): String {
            val json = GameJson.encodeToString(KingdomSave.serializer(), data)
            return "KA|${data.version}|${crc(json)}\n$json"
        }

        fun decode(text: String?): KingdomSave? {
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
                GameJson.decodeFromJsonElement(KingdomSave.serializer(), obj)
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
        decode(storage.read(BACKUP))?.let { return LoadResult(it, LoadResult.Source.BACKUP, if (hadMain) "Kayıt dosyası bozuktu; yedekten geri yüklendi." else null) }
        return LoadResult(KingdomSave(), LoadResult.Source.NEW, if (hadMain) "Kayıt okunamadı; yeni oyun başlatıldı." else null)
    }

    fun save(data: KingdomSave) {
        data.version = CURRENT_VERSION
        val text = encode(data)
        if (storage.exists(MAIN) && decode(storage.read(MAIN)) != null) storage.copy(MAIN, BACKUP)
        storage.writeAtomic(MAIN, text)
    }
}
