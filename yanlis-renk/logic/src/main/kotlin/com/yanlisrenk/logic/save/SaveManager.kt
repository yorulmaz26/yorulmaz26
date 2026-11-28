package com.yanlisrenk.logic.save

import com.yanlisrenk.logic.shop.ShopCatalog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Platform depolama. Android: uygulamaya özel dosyalar (atomik yazma). */
interface SaveStorage {
    fun read(name: String): String?
    /** Atomik yazma: önce geçici dosya, sonra yeniden adlandırma. */
    fun write(name: String, content: String)
    fun delete(name: String)
}

class MemoryStorage : SaveStorage {
    val files = HashMap<String, String>()
    override fun read(name: String) = files[name]
    override fun write(name: String, content: String) { files[name] = content }
    override fun delete(name: String) { files.remove(name) }
}

enum class LoadOutcome { NEW, OK, RESTORED_BACKUP, TAMPERED_RESET, MIGRATED }

/** Kayıt şeması göçleri. */
object SaveMigrations {
    /** Ham JSON'u güncel sürüme getirir. */
    fun migrate(obj: JsonObject): JsonObject {
        var o = obj
        val v = o["version"]?.jsonPrimitive?.int ?: 1
        if (v < 2) {
            // v1 → v2: "coins" alanı "gold" oldu
            val m = o.toMutableMap()
            m["coins"]?.let { m["gold"] = it; m.remove("coins") }
            m["version"] = JsonPrimitive(2)
            o = JsonObject(m)
        }
        return o
    }
}

/**
 * Sürümlü ve bütünlüğü doğrulanan kayıt sistemi.
 * Dosya biçimi: `YRSAVE|<sürüm>|<hmac>\n<json>`. HMAC anahtarı uygulama sabiti + kurulum
 * kimliğinden türetilir. Amaç basit dosya düzenlemelerini yakalamaktır; cihazda saklanan
 * bir anahtar kararlı bir saldırgana karşı tam koruma sağlamaz.
 *
 * Her yazımda önceki geçerli kayıt `.bak` olarak saklanır. Ana dosya bozuksa yedek
 * yüklenir; ikisi de bozuksa bozuk dosya `.corrupt` adıyla korunur ve yeni kayıt başlar
 * (oyuncu "hileci" olarak işaretlenmez).
 */
class SaveManager(private val storage: SaveStorage, private val newInstallId: () -> String) {
    companion object {
        const val FILE = "yanlisrenk_save.dat"
        const val BACKUP = "yanlisrenk_save.bak"
        private const val SECRET = "yR-renk-4c0l0r-gate-v1"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    }

    var lastOutcome: LoadOutcome = LoadOutcome.NEW
        private set

    fun load(): SaveData {
        val main = storage.read(FILE)
        if (main == null) {
            val bak = storage.read(BACKUP)?.let { decode(it) }
            if (bak != null) { lastOutcome = LoadOutcome.RESTORED_BACKUP; return bak }
            lastOutcome = LoadOutcome.NEW
            return fresh()
        }
        val d = decode(main)
        if (d != null) {
            if (lastOutcome != LoadOutcome.MIGRATED) lastOutcome = LoadOutcome.OK
            return d
        }
        val bak = storage.read(BACKUP)?.let { decode(it) }
        storage.write("$FILE.corrupt", main)
        if (bak != null) { lastOutcome = LoadOutcome.RESTORED_BACKUP; return bak }
        lastOutcome = LoadOutcome.TAMPERED_RESET
        return fresh()
    }

    fun save(data: SaveData) {
        data.saveCount++
        data.version = SaveData.CURRENT_VERSION
        val body = json.encodeToString(SaveData.serializer(), data)
        val text = "YRSAVE|${data.version}|${hmac(data.installId, body)}\n$body"
        storage.read(FILE)?.let { old -> if (decode(old) != null) storage.write(BACKUP, old) }
        storage.write(FILE, text)
    }

    fun fresh(): SaveData {
        val d = SaveData(installId = newInstallId())
        ShopCatalog.defaults.forEach { (cat, id) -> d.owned.add(id); d.equipped[cat.name] = id }
        return d
    }

    private fun decode(text: String): SaveData? = runCatching {
        val nl = text.indexOf('\n')
        if (nl < 0 || !text.startsWith("YRSAVE|")) return null
        val header = text.substring(0, nl).split('|')
        val body = text.substring(nl + 1)
        val obj = json.parseToJsonElement(body).jsonObject
        val installId = obj["installId"]?.jsonPrimitive?.content ?: ""
        if (header.size < 3 || header[2] != hmac(installId, body)) return null
        val ver = header[1].toIntOrNull() ?: 1
        val migrated = if (ver < SaveData.CURRENT_VERSION) { lastOutcome = LoadOutcome.MIGRATED; SaveMigrations.migrate(obj) } else obj
        val d = json.decodeFromJsonElement(SaveData.serializer(), migrated)
        sanitize(d)
    }.getOrNull()

    /** Mantıksız değerleri düzeltir (negatif bakiye, can sınırı vb.). */
    private fun sanitize(d: SaveData): SaveData {
        if (d.gold < 0) d.gold = 0
        if (d.diamonds < 0) d.diamonds = 0
        d.lives.lives = d.lives.lives.coerceIn(0, com.yanlisrenk.logic.config.GameConfig.MAX_LIVES)
        d.lives.regenRemaining = d.lives.regenRemaining.coerceIn(1, com.yanlisrenk.logic.config.GameConfig.LIFE_REGEN_MS)
        d.unlockedLevel = d.unlockedLevel.coerceIn(1, 500)
        ShopCatalog.defaults.forEach { (cat, id) -> d.owned.add(id); if (d.equipped[cat.name] !in d.owned) d.equipped[cat.name] = id }
        return d
    }

    fun hmac(installId: String, body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("$SECRET:$installId".toByteArray(), "HmacSHA256"))
        return mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** Test/geliştirme: v1 biçiminde (eski) bir kayıt yazar. */
    fun writeLegacyV1(raw: String, installId: String) {
        storage.write(FILE, "YRSAVE|1|${hmac(installId, raw)}\n$raw")
    }
}
