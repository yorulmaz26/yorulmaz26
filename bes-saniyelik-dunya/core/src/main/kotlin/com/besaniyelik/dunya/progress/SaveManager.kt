package com.besaniyelik.dunya.progress

import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import java.util.zip.CRC32

/** Kalıcı depolama soyutlaması (oyunda Gdx.files.local, testlerde geçici klasör). */
interface SaveStorage {
    fun read(name: String): String?
    fun write(name: String, text: String)
    fun delete(name: String)
}

class FileSaveStorage(private val dir: java.io.File) : SaveStorage {
    init { dir.mkdirs() }
    override fun read(name: String): String? = java.io.File(dir, name).takeIf { it.exists() }?.readText()
    override fun write(name: String, text: String) {
        val tmp = java.io.File(dir, "$name.tmp")
        tmp.writeText(text)
        val dst = java.io.File(dir, name)
        if (!tmp.renameTo(dst)) { dst.writeText(text); tmp.delete() }
    }
    override fun delete(name: String) { java.io.File(dir, name).delete() }
}

private val saveJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
private class SaveEnvelope(val v: Int, val crc: Long, val data: String)

/**
 * Güvenilir kayıt: veri CRC32 ile imzalanır, önce geçici dosyaya yazılıp sonra yer değiştirilir,
 * önceki sağlam kayıt ".bak" olarak saklanır. Bozuk kayıt algılanırsa yedekten yüklenir.
 * Sürüm alanı sayesinde güncellemelerde eski kayıtlar taşınır, silinmez.
 */
class SaveManager(private val storage: SaveStorage) {
    companion object {
        const val CURRENT_VERSION = 1
        const val MAIN = "profile.json"
        const val BACKUP = "profile.bak"
        private const val SALT = "5sn-dunya-v1"

        fun checksum(text: String): Long = CRC32().apply { update((SALT + text).toByteArray(Charsets.UTF_8)) }.value
    }

    var lastLoadSource = "yeni"
        private set

    fun load(): SaveData {
        for ((name, src) in listOf(MAIN to "ana kayıt", BACKUP to "yedek")) {
            val data = tryLoad(name) ?: continue
            lastLoadSource = src
            return migrate(data)
        }
        lastLoadSource = "yeni"
        return SaveData()
    }

    private fun tryLoad(name: String): SaveData? = try {
        val raw = storage.read(name) ?: return null
        val env = saveJson.decodeFromString(SaveEnvelope.serializer(), raw)
        if (checksum(env.data) != env.crc) null
        else saveJson.decodeFromString(SaveData.serializer(), env.data).takeIf { validate(it) }
    } catch (e: Exception) { null }

    /** Mantıksal tutarlılık denetimi. */
    fun validate(d: SaveData): Boolean =
        d.coins >= 0 && d.gems >= 0 && d.xp >= 0 && d.playerLevel >= 1 && d.highestUnlocked in 1..500 &&
            d.levels.values.all { it.stars in 0..3 } && d.selectedSkin.isNotEmpty()

    private fun migrate(d: SaveData): SaveData {
        if (d.version < CURRENT_VERSION) d.version = CURRENT_VERSION
        if ("pati" !in d.ownedSkins) d.ownedSkins.add("pati")
        if ("toz" !in d.ownedTrails) d.ownedTrails.add("toz")
        if (d.selectedSkin !in d.ownedSkins) d.selectedSkin = "pati"
        if (d.selectedTrail !in d.ownedTrails) d.selectedTrail = "toz"
        return d
    }

    fun save(d: SaveData) {
        val body = saveJson.encodeToString(SaveData.serializer(), d)
        val env = saveJson.encodeToString(SaveEnvelope.serializer(), SaveEnvelope(CURRENT_VERSION, checksum(body), body))
        // Var olan sağlam ana kaydı yedeğe al
        val current = storage.read(MAIN)
        if (current != null && tryLoad(MAIN) != null) storage.write(BACKUP, current)
        storage.write(MAIN, env)
    }

    fun reset() {
        storage.delete(MAIN); storage.delete(BACKUP)
    }
}
