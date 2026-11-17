package com.kuaforumcepte.app.feature.salon

import android.content.Intent
import android.net.Uri
import java.util.Locale

/** Google Maps / harita uygulaması intent'leri (API anahtarı gerektirmez). */
object MapIntents {
    fun show(lat: Double, lng: Double, label: String): Intent {
        val q = String.format(Locale.US, "%f,%f(%s)", lat, lng, label)
        return Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US, "geo:%f,%f?q=", lat, lng) + Uri.encode(q)))
    }

    fun showAddress(address: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address)))

    /** Yol tarifi: Google Maps evrensel URL'si (uygulama yoksa tarayıcıda açılır). */
    fun directions(lat: Double?, lng: Double?, address: String): Intent {
        val dest = if (lat != null && lng != null) String.format(Locale.US, "%f,%f", lat, lng) else address
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(dest)))
    }

    fun dial(phoneE164: String): Intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneE164"))
}
