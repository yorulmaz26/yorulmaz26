package com.kuaforumcepte.domain.booking

import com.kuaforumcepte.domain.model.Kurus

/** firestore.rules → services/validService ile aynı sınırlar. */
object ServiceRules {
    const val MIN_DURATION = 5
    const val MAX_DURATION = 600
    const val DURATION_STEP = 5
    val durationOptions: List<Int> = (MIN_DURATION..MAX_DURATION step DURATION_STEP).toList()

    data class Errors(val name: String? = null, val price: String? = null, val duration: String? = null, val description: String? = null) {
        val isValid: Boolean get() = name == null && price == null && duration == null && description == null
    }

    fun validate(name: String, priceInput: String, durationMinutes: Int?, description: String): Errors {
        val price = Kurus.parseTry(priceInput)
        return Errors(
            name = if (name.trim().length !in 2..60) "2–60 karakter girin" else null,
            price = when {
                price == null -> "Geçerli bir tutar girin (ör. 250 veya 250,50)"
                price.value < Kurus.MIN_SERVICE_PRICE -> "En az 1 TL olmalı"
                price.value > Kurus.MAX_SERVICE_PRICE -> "En fazla 1.000.000 TL olabilir"
                else -> null
            },
            duration = when {
                durationMinutes == null -> "Süre seçin"
                durationMinutes !in MIN_DURATION..MAX_DURATION || durationMinutes % DURATION_STEP != 0 -> "5–600 dakika, 5'in katı"
                else -> null
            },
            description = if (description.length > 500) "En fazla 500 karakter" else null,
        )
    }
}
