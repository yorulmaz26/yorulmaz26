package com.kuaforumcepte.domain.link

import com.kuaforumcepte.domain.model.SalonCode
import java.net.URI

/** Uygulamanın tanıdığı bağlantılar. */
sealed interface DeepLink {
    /** https://<host>/s/<kod> veya kuaforumcepte://salon/<kod> */
    data class Salon(val code: String) : DeepLink
    /** kuaforumcepte://appointment/<id> (bildirim tıklaması) */
    data class Appointment(val appointmentId: String) : DeepLink
}

object DeepLinks {
    const val SCHEME = "kuaforumcepte"
    private val idRegex = Regex("^[A-Za-z0-9_-]{1,128}$")

    /** Müşteriye paylaşılan, salonun randevu sayfasını açan herkese açık bağlantı. */
    fun salonUrl(host: String, code: String): String = "https://$host/s/$code"

    fun appointmentUri(appointmentId: String): String = "$SCHEME://appointment/$appointmentId"

    /**
     * Gelen bağlantıyı ayrıştırır. Yalnızca beklenen alan adı ve geçerli salon kodu kabul edilir;
     * bilinmeyen/bozuk bağlantılar null döner (uygulama normal açılır).
     */
    fun parse(raw: String?, appLinkHost: String): DeepLink? {
        if (raw.isNullOrBlank()) return null
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        val segments = (uri.rawPath ?: "").split('/').filter { it.isNotEmpty() }
        return when (uri.scheme?.lowercase()) {
            "https" -> {
                if (!uri.host.equals(appLinkHost, ignoreCase = true)) return null
                if (segments.size == 2 && segments[0] == "s" && SalonCode.isValid(segments[1])) DeepLink.Salon(segments[1]) else null
            }
            SCHEME -> {
                // kuaforumcepte://salon/482913 → host = "salon", path = "/482913"
                val value = segments.firstOrNull() ?: return null
                when (uri.host) {
                    "salon" -> if (SalonCode.isValid(value)) DeepLink.Salon(value) else null
                    "appointment" -> if (idRegex.matches(value)) DeepLink.Appointment(value) else null
                    else -> null
                }
            }
            else -> null
        }
    }
}
