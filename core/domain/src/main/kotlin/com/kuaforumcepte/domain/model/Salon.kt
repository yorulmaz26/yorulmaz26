package com.kuaforumcepte.domain.model

enum class SalonType(val label: String) {
    BEAUTY_SALON("Güzellik Salonu"),
    MEN_HAIRDRESSER("Erkek Kuaförü"),
    WOMEN_HAIRDRESSER("Kadın Kuaförü"),
    UNISEX_HAIRDRESSER("Erkek & Kadın Kuaförü");

    companion object {
        fun fromWire(value: String?): SalonType? = entries.firstOrNull { it.name == value }
    }
}

/** Uygulamaya giriş yönü (rol seçimi ekranı). Telefon tek başına rol vermez. */
enum class EntryRole { CUSTOMER, BUSINESS }

enum class MemberRole(val label: String) { OWNER("Salon Sahibi"), STAFF("Uzman") }

enum class MemberStatus { ACTIVE, INACTIVE, LEFT }

/** Salon kodu: 6 hane, 100000–999999. Kimliktir, yetki vermez. */
object SalonCode {
    private val regex = Regex("^[1-9][0-9]{5}$")
    fun isValid(code: String): Boolean = regex.matches(code)
}

/** Puan: 1 ondalık aritmetik ortalama; oy yoksa "Yeni". */
data class Rating(val sum: Long, val count: Long) {
    val average: Double?
        get() = if (count <= 0) null else Math.round(sum.toDouble() / count * 10) / 10.0

    fun label(): String = average?.let { String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.1f", it) } ?: "Yeni"

    companion object {
        val NONE = Rating(0, 0)
    }
}
