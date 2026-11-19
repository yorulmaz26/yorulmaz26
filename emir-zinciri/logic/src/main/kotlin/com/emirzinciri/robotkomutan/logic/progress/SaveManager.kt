package com.emirzinciri.robotkomutan.logic.progress

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Kayıt dosyalarına erişim. */
interface SaveStorage {
    fun read(name: String): String?
    /** Atomik yazma: yarıda kalan bir yazma eski dosyayı bozmaz. */
    fun write(name: String, text: String)
}

/** java.nio tabanlı atomik dosya yazımı (Android ve masaüstünde çalışır). */
class FileSaveStorage(private val dir: File) : SaveStorage {
    init { dir.mkdirs() }

    override fun read(name: String): String? {
        val f = File(dir, name)
        return if (f.isFile) runCatching { f.readText(Charsets.UTF_8) }.getOrNull() else null
    }

    override fun write(name: String, text: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.outputStream().use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        val target = File(dir, name).toPath()
        try {
            Files.move(tmp.toPath(), target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

class MemorySaveStorage : SaveStorage {
    val files = HashMap<String, String>()
    override fun read(name: String) = files[name]
    override fun write(name: String, text: String) { files[name] = text }
}

data class LoadResult(val data: SaveData, val source: Source, val message: String? = null) {
    enum class Source { NEW, PRIMARY, BACKUP }
}

/**
 * Sürümlendirilmiş kayıt yöneticisi.
 *  - Ana dosya okunamazsa yedekten kurtarır; ikisi de bozuksa bozuk dosyayı saklayıp yeni kayıt açar.
 *  - Her yazmadan önce sağlam ana dosya yedeğe kopyalanır.
 *  - Eski sürümler [migrate] ile güncel sürüme yükseltilir.
 */
class SaveManager(private val storage: SaveStorage) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowStructuredMapKeys = true }

    fun load(): LoadResult {
        val primary = storage.read(PRIMARY)
        decode(primary)?.let { return LoadResult(it, LoadResult.Source.PRIMARY) }
        val backup = storage.read(BACKUP)
        decode(backup)?.let {
            return LoadResult(it, LoadResult.Source.BACKUP, "Kayıt dosyası bozulmuştu; son yedek yüklendi.")
        }
        if (primary != null) storage.write(CORRUPT, primary)
        return LoadResult(
            SaveData(), LoadResult.Source.NEW,
            if (primary != null) "Kayıt okunamadı; yeni bir profil açıldı (bozuk dosya saklandı)." else null,
        )
    }

    fun save(data: SaveData) {
        data.version = SaveData.CURRENT_VERSION
        val text = json.encodeToString(SaveData.serializer(), data)
        val current = storage.read(PRIMARY)
        if (current != null && decode(current) != null) storage.write(BACKUP, current)
        storage.write(PRIMARY, text)
    }

    fun encode(data: SaveData): String = json.encodeToString(SaveData.serializer(), data)

    fun decode(text: String?): SaveData? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            val obj = json.parseToJsonElement(text).jsonObject
            val version = obj["version"]?.jsonPrimitive?.int ?: 0
            require(version <= SaveData.CURRENT_VERSION) { "Kayıt daha yeni bir oyun sürümünden: $version" }
            json.decodeFromJsonElement(SaveData.serializer(), migrate(obj, version))
        }.getOrNull()
    }

    /** Sürüm yükseltmeleri. Her yeni sürüm bir adım ekler: v(n) -> v(n+1). */
    private fun migrate(obj: JsonObject, from: Int): JsonObject {
        var o = obj
        var v = from
        while (v < SaveData.CURRENT_VERSION) {
            o = when (v) {
                0 -> JsonObject(o + ("version" to JsonPrimitive(1)))
                else -> o
            }
            v++
        }
        return o
    }

    companion object {
        const val PRIMARY = "save.json"
        const val BACKUP = "save.bak.json"
        const val CORRUPT = "save.corrupt.json"
    }
}
