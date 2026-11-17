package com.kuaforumcepte.domain.booking

import com.kuaforumcepte.domain.model.Kurus

/** Seçilebilir hizmet (randevu kaleminde snapshot olarak saklanır). */
data class ServiceOption(
    val id: String,
    val staffUid: String,
    val name: String,
    val price: Kurus,
    val durationMinutes: Int,
)

/** Çoklu hizmet seçimi; tüm hizmetler aynı uzmana ait olmalıdır. Değişmez nesne. */
data class ServiceSelection(val staffUid: String?, val items: List<ServiceOption>) {
    val totalPrice: Kurus get() = items.fold(Kurus.ZERO) { acc, s -> acc + s.price }
    val totalMinutes: Int get() = items.sumOf { it.durationMinutes }
    val isEmpty: Boolean get() = items.isEmpty()
    fun isSelected(id: String): Boolean = items.any { it.id == id }

    /** Farklı uzmanın hizmeti seçilirse IllegalArgumentException: UI uzman değişince seçimi sıfırlar. */
    fun toggle(option: ServiceOption): ServiceSelection {
        require(staffUid == null || option.staffUid == staffUid) { "Seçili hizmetler aynı uzmana ait olmalıdır" }
        val next = if (isSelected(option.id)) items.filterNot { it.id == option.id } else items + option
        return ServiceSelection(if (next.isEmpty()) null else option.staffUid, next)
    }

    companion object {
        val EMPTY = ServiceSelection(null, emptyList())
    }
}

/** "1 sa 15 dk" biçimi */
fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "$h sa $m dk"
        h > 0 -> "$h sa"
        else -> "$m dk"
    }
}
