package com.yanlisrenk.logic.i18n

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.text.NumberFormat
import java.util.Locale

/**
 * Merkezi yerelleştirme yöneticisi.
 *
 * - Her dil ayrı bir JSON dosyasıdır: `assets/localization/<kod>.json` (düz anahtar → metin).
 * - Desteklenen diller [SUPPORTED]; yeni dil eklemek için JSON dosyası ekleyip listeye kodu yazmak yeter.
 * - Bulunamayan anahtar önce İngilizceye, sonra koddaki Türkçe varsayılana düşer ve [missing] kümesine yazılır.
 * - Biçim: `{0}`, `{1}` yer tutucuları; çoğul için ICU alt kümesi: `{0,plural,one{# kapı} other{# kapı}}`.
 * - Dil değişimi anında uygulanır (yeniden başlatma gerekmez); dinleyiciler [onChange] ile bildirilir.
 */
object L {
    val SUPPORTED = listOf("tr", "en", "de", "it", "fr", "es")
    /** Dil seçicide gösterilen yerel adlar (çevrilmez). */
    val NATIVE_NAMES = linkedMapOf(
        "tr" to "Türkçe", "en" to "English", "de" to "Deutsch", "it" to "Italiano", "fr" to "Français", "es" to "Español",
    )
    const val FALLBACK = "en"

    var lang: String = "tr"; private set
    var locale: Locale = Locale.forLanguageTag("tr-TR"); private set

    private var cur: Map<String, String> = emptyMap()
    private var en: Map<String, String> = emptyMap()
    private var loader: (String) -> String? = { null }
    private val listeners = ArrayList<() -> Unit>()
    /** Çalışma sırasında bulunamayan anahtarlar (teşhis / test). */
    val missing: MutableSet<String> = LinkedHashSet()

    /** Dil dosyası okuyucusunu kurar (ör. Gdx.files.internal). */
    fun install(read: (code: String) -> String?) {
        loader = read
        en = parse(read(FALLBACK))
    }

    /** Kayıtlı tercih (boş = otomatik) ve sistem dilinden kullanılacak dili seçer. Desteklenmeyen sistem dili → İngilizce. */
    fun resolve(preference: String, systemLanguage: String): String {
        if (preference in SUPPORTED) return preference
        val sys = systemLanguage.lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')
        return if (sys in SUPPORTED) sys else FALLBACK
    }

    fun setLanguage(code: String) {
        val c = if (code in SUPPORTED) code else FALLBACK
        lang = c
        locale = when (c) { "tr" -> Locale.forLanguageTag("tr-TR"); "en" -> Locale.US; else -> Locale.forLanguageTag(c) }
        cur = if (c == FALLBACK) en else parse(loader(c))
        listeners.toList().forEach { it() }
    }

    fun onChange(l: () -> Unit) { listeners += l }

    fun has(key: String): Boolean = cur.containsKey(key)

    /** Anahtarın metni. */
    fun t(key: String): String = cur[key] ?: en[key] ?: run { missing += key; key }

    /** Anahtar yoksa [def] (koddaki Türkçe) kullanılır; Türkçe dışındaki dillerde önce İngilizce denenir. */
    fun tOr(key: String, def: String): String {
        cur[key]?.let { return it }
        if (lang != "tr") { en[key]?.let { return it }; missing += key }
        return def
    }

    fun f(key: String, vararg args: Any?): String = format(t(key), *args)
    fun fOr(key: String, def: String, vararg args: Any?): String = format(tOr(key, def), *args)

    /** Sayıyı dile uygun basamak ayırıcıyla yazar (1.500 / 1,500 / 1 500). */
    fun num(n: Long): String = if (n in -9999..9999) n.toString() else NumberFormat.getIntegerInstance(locale).format(n)

    /** Çoğul kategorisi (CLDR tamsayı kuralları). */
    fun pluralCategory(n: Long, language: String = lang): String = when (language) {
        "tr" -> if (n == 1L) "one" else "other"
        "fr" -> if (n == 0L || n == 1L) "one" else "other"
        else -> if (n == 1L) "one" else "other"
    }

    fun format(pattern: String, vararg args: Any?): String {
        if (pattern.indexOf('{') < 0) return pattern
        val sb = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '{') {
                val end = matchingBrace(pattern, i)
                if (end > i) {
                    sb.append(placeholder(pattern.substring(i + 1, end), args))
                    i = end + 1; continue
                }
            }
            sb.append(c); i++
        }
        return sb.toString()
    }

    private fun matchingBrace(s: String, open: Int): Int {
        var d = 0
        for (j in open until s.length) {
            if (s[j] == '{') d++ else if (s[j] == '}') { d--; if (d == 0) return j }
        }
        return -1
    }

    private fun argText(a: Any?): String = when (a) {
        is Int -> num(a.toLong()); is Long -> num(a); null -> ""; else -> a.toString()
    }

    private fun placeholder(body: String, args: Array<out Any?>): String {
        val comma = body.indexOf(',')
        if (comma < 0) {
            val idx = body.trim().toIntOrNull() ?: return "{$body}"
            return argText(args.getOrNull(idx))
        }
        val idx = body.substring(0, comma).trim().toIntOrNull() ?: return "{$body}"
        val rest = body.substring(comma + 1)
        if (!rest.trimStart().startsWith("plural")) return argText(args.getOrNull(idx))
        val arg = args.getOrNull(idx)
        val n = when (arg) { is Number -> arg.toLong(); else -> arg?.toString()?.toLongOrNull() ?: 0L }
        val forms = HashMap<String, String>()
        var k = rest.indexOf(',') + 1
        while (k in 1 until rest.length) {
            while (k < rest.length && rest[k].isWhitespace()) k++
            val brace = rest.indexOf('{', k)
            if (brace < 0) break
            val sel = rest.substring(k, brace).trim()
            val close = matchingBrace(rest, brace)
            if (close < 0) break
            forms[sel] = rest.substring(brace + 1, close)
            k = close + 1
        }
        val form = forms["=$n"] ?: forms[pluralCategory(n)] ?: forms["other"] ?: ""
        return format(form.replace("#", num(n)), *args)
    }

    fun parse(text: String?): Map<String, String> {
        if (text.isNullOrBlank()) return emptyMap()
        val obj: JsonObject = Json.parseToJsonElement(text).jsonObject
        val m = HashMap<String, String>(obj.size * 2)
        for ((k, v) in obj) if (v is JsonPrimitive && v.isString && !k.startsWith("_")) m[k] = v.content
        return m
    }
}

/** Seçili dile göre büyük harf (Türkçede i → İ, Almancada ß → SS). */
fun String.lupper(): String = uppercase(L.locale)
