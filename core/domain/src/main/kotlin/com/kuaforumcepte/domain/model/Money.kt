package com.kuaforumcepte.domain.model

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Para birimi daima TRY; tutarlar kuruş cinsinden tam sayı tutulur (kayan nokta yok). */
@JvmInline
value class Kurus(val value: Long) : Comparable<Kurus> {
    operator fun plus(other: Kurus): Kurus = Kurus(Math.addExact(value, other.value))
    operator fun minus(other: Kurus): Kurus = Kurus(Math.subtractExact(value, other.value))
    override fun compareTo(other: Kurus): Int = value.compareTo(other.value)

    /** Ör. 125050 → "₺1.250,50" */
    fun formatTry(): String = formatter.get().format(value / 100.0)

    companion object {
        val ZERO = Kurus(0)
        const val CURRENCY = "TRY"
        const val MIN_SERVICE_PRICE = 100L // 1 TL
        const val MAX_SERVICE_PRICE = 100_000_000L // 1.000.000 TL

        private val formatter = ThreadLocal.withInitial {
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("tr-TR")).apply {
                currency = Currency.getInstance(CURRENCY)
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
        }

        /**
         * Kullanıcı girişini ("250", "250,5", "1.250,50") kuruşa çevirir. Geçersizse null.
         * Binlik ayıracı ".", ondalık ayıracı "," (Türkçe yazım).
         */
        fun parseTry(input: String): Kurus? {
            val cleaned = input.trim().replace("₺", "").replace(" ", "")
            if (cleaned.isEmpty()) return null
            val regex = Regex("""^(\d{1,3}(\.\d{3})*|\d+)(,\d{1,2})?$""")
            if (!regex.matches(cleaned)) return null
            val parts = cleaned.split(",")
            val lira = parts[0].replace(".", "").toLongOrNull() ?: return null
            val frac = parts.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0L
            return runCatching { Kurus(Math.addExact(Math.multiplyExact(lira, 100L), frac)) }.getOrNull()
        }
    }
}

fun Iterable<Kurus>.sum(): Kurus = fold(Kurus.ZERO) { acc, k -> acc + k }
