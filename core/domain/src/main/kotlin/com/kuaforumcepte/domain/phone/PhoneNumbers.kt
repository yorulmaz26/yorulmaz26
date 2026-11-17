package com.kuaforumcepte.domain.phone

/** Türkiye numaralarını E.164 (+90XXXXXXXXXX) biçimine normalize eder (sunucudaki phone.ts ile aynı). */
object PhoneNumbers {
    private val separators = Regex("""[\s\-().]""")
    private val mobile = Regex("^5[0-9]{9}$")
    private val any = Regex("^[2-5][0-9]{9}$")

    fun normalizeTr(raw: String, mobileOnly: Boolean = false): String? {
        var digits = raw.replace(separators, "")
        if (digits.startsWith("+")) digits = digits.substring(1)
        if (digits.isEmpty() || !digits.all { it.isDigit() }) return null
        digits = when {
            digits.startsWith("90") && digits.length == 12 -> digits.substring(2)
            digits.startsWith("0") && digits.length == 11 -> digits.substring(1)
            else -> digits
        }
        if (digits.length != 10) return null
        return if ((if (mobileOnly) mobile else any).matches(digits)) "+90$digits" else null
    }

    /** "+905321234567" → "0532 123 45 67" */
    fun formatForDisplay(e164: String): String {
        if (!e164.startsWith("+90") || e164.length != 13) return e164
        val d = e164.substring(3)
        return "0${d.substring(0, 3)} ${d.substring(3, 6)} ${d.substring(6, 8)} ${d.substring(8, 10)}"
    }
}
