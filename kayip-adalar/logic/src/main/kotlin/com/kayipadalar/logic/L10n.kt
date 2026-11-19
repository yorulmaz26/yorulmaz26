package com.kayipadalar.logic

/**
 * Basit yerelleştirme yöneticisi. "anahtar=değer" satırlarından oluşan UTF-8 dosyaları okur.
 * Yeni dil eklemek için i18n/strings_<dil>.properties dosyası eklemek yeterlidir.
 */
class L10n(text: String, private val fallback: L10n? = null) {
    private val map: Map<String, String> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
        .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim().replace("\\n", "\n") }

    fun has(key: String): Boolean = key in map || fallback?.has(key) == true

    fun t(key: String, vararg args: Any?): String {
        var s = map[key] ?: fallback?.let { if (it.has(key)) it.t(key) else null } ?: key
        args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
        return s
    }

    companion object {
        private var current: L10n = L10n("")
        fun install(l: L10n) { current = l }
        fun get(key: String, vararg args: Any?) = current.t(key, *args)
        fun has(key: String) = current.has(key)
    }
}

/** Kısa erişim. */
fun tr(key: String, vararg args: Any?) = L10n.get(key, *args)
