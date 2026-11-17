package com.kuaforumcepte.domain.text

import java.text.Normalizer
import java.util.Locale

/**
 * Türkçe duyarlı arama anahtarı: küçük harf (tr), aksanlar kaldırılır, ı→i, boşluklar sadeleşir.
 * "İSTANBUL Güzellik" → "istanbul guzellik". Salon/hizmet adlarındaki `nameLower` alanı bununla üretilir.
 */
object SearchKey {
    private val tr = Locale.forLanguageTag("tr-TR")
    private val marks = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")

    fun of(text: String): String {
        val lower = text.trim().lowercase(tr).replace('ı', 'i')
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(marks, "")
        return decomposed.replace(spaces, " ")
    }
}
