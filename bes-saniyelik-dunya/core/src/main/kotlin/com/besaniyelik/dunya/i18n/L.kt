package com.besaniyelik.dunya.i18n

import com.besaniyelik.dunya.GameConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.Locale

/**
 * Yerelleştirme yöneticisi. Metinler assets/i18n/<dil>.json dosyalarında düz anahtar → metin olarak durur.
 *
 *  - Yer tutucular adlıdır: "{n} altın" → t("x", "n" to 5).
 *  - Çoğul: anahtar.one / anahtar.other (Fransızcada 0 ve 1 tekil sayılır).
 *  - Eksik anahtar: önce İngilizce, sonra Türkçe (kaynak dil), en son anahtarın kendisi gösterilir; eksikler
 *    birim testinde (LocalizationTest) yakalanır.
 *  - Dil değişimi anında geçerlidir: ekranlar metni her kuruluşta yeniden okur.
 */
object L {
    val SUPPORTED: List<String> get() = GameConfig.LANGUAGES
    /** Dillerin kendi dillerindeki adları (dil seçim düğmelerinde). */
    val NATIVE_NAMES = mapOf("tr" to "Türkçe", "en" to "English", "de" to "Deutsch", "it" to "Italiano", "fr" to "Français", "es" to "Español")
    const val SOURCE = "tr"

    /** Dosya okuyucu; oyun Gdx.files, testler doğrudan dosya sistemi kullanır (çalışma klasörü assets/). */
    var reader: (String) -> String? = { p -> java.io.File(p).takeIf { it.exists() }?.readText(Charsets.UTF_8) }

    private val tables = HashMap<String, Map<String, String>>()
    var lang: String = SOURCE
        private set
    var locale: Locale = Locale.forLanguageTag(SOURCE)
        private set
    private var curTable: Map<String, String>? = null
    private val cur: Map<String, String> get() = curTable ?: table(lang).also { curTable = it }

    fun fileName(lang: String) = "i18n/$lang.json"

    fun table(lang: String): Map<String, String> = synchronized(tables) {
        tables.getOrPut(lang) { parse(reader(fileName(lang)) ?: "{}") }
    }

    fun parse(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        fun walk(prefix: String, o: JsonObject) {
            for ((k, v) in o) {
                val key = if (prefix.isEmpty()) k else "$prefix.$k"
                when (v) {
                    is JsonObject -> walk(key, v)
                    is JsonPrimitive -> if (!k.startsWith("_")) out[key] = v.content
                    else -> {}
                }
            }
        }
        walk("", Json.parseToJsonElement(text).jsonObject)
        return out
    }

    /** Dili etkinleştirir (desteklenmiyorsa varsayılan dil). */
    fun use(language: String) {
        val l = if (language in SUPPORTED) language else GameConfig.DEFAULT_LANGUAGE
        lang = l
        locale = Locale.forLanguageTag(l)
        curTable = table(l)
    }

    /** Sistem dilinden oyun dili: destekleniyorsa o, değilse varsayılan (İngilizce). */
    fun detect(systemLanguage: String?): String {
        val code = systemLanguage?.trim()?.lowercase()?.replace('_', '-')?.substringBefore('-')
        return if (code != null && code in SUPPORTED) code else GameConfig.DEFAULT_LANGUAGE
    }

    fun has(key: String): Boolean = key in cur || key in table(GameConfig.DEFAULT_LANGUAGE) || key in table(SOURCE)

    fun raw(key: String): String = cur[key] ?: table(GameConfig.DEFAULT_LANGUAGE)[key] ?: table(SOURCE)[key] ?: key

    fun t(key: String, vararg args: Pair<String, Any?>): String = fill(raw(key), args)

    /** Sayıya göre tekil/çoğul biçim; {n} yer tutucusu sayının kendisidir. */
    fun n(key: String, count: Long, vararg args: Pair<String, Any?>): String {
        val form = if (isOne(count)) "one" else "other"
        val k = "$key.$form"
        val text = if (has(k)) raw(k) else raw("$key.other")
        return fill(text, arrayOf("n" to count, *args))
    }
    fun n(key: String, count: Int, vararg args: Pair<String, Any?>): String = n(key, count.toLong(), *args)

    private fun isOne(n: Long): Boolean = if (lang == "fr") n == 0L || n == 1L else n == 1L

    /** Liste değerleri "|" ile ayrılır. */
    fun list(key: String): List<String> = raw(key).split('|').map { it.trim() }

    fun fill(text: String, args: Array<out Pair<String, Any?>>): String {
        if (args.isEmpty() || '{' !in text) return text
        var s = text
        for ((k, v) in args) s = s.replace("{$k}", v?.toString() ?: "")
        return s
    }

    /** Dile göre ondalık sayı (Almanca, Fransızca vb. virgül kullanır). */
    fun dec(x: Float, digits: Int = 1): String = String.format(locale, "%.${digits}f", x)

    /** Kayıtlı bölüm verisindeki Türkçe metnin bu dildeki karşılığı (anahtarı [prefix] ile başlayanlar arasında). */
    fun fromSource(prefix: String, text: String): String? {
        val src = table(SOURCE)
        val key = src.entries.firstOrNull { it.key.startsWith(prefix) && it.value == text }?.key ?: return null
        return raw(key)
    }
}
