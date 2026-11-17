package com.kuaforumcepte.app.data.salon

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import androidx.core.location.LocationManagerCompat
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Salon konumu için harita SDK'sı/anahtar gerektirmeyen iki yol:
 *  1) Cihazın o anki konumu (salondayken), 2) yazılı adresten Geocoder ile arama.
 * Sonuç her durumda kullanıcıya haritada gösterilip onaylatılır.
 */
@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Çağırmadan önce ACCESS_FINE/COARSE_LOCATION izni alınmış olmalıdır. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): GeoPoint {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(lm)) throw AppException("LOCATION_DISABLED")
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { lm.isProviderEnabled(it) }
            ?: throw AppException("LOCATION_DISABLED")
        val executor = Executors.newSingleThreadExecutor()
        try {
            val location = withTimeout(20_000) {
                suspendCancellableCoroutine<android.location.Location?> { cont ->
                    val signal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    LocationManagerCompat.getCurrentLocation(lm, provider, signal, executor) { loc -> cont.resume(loc) }
                }
            } ?: throw AppException("LOCATION_UNAVAILABLE")
            return GeoPoint(location.latitude, location.longitude)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw AppException("LOCATION_UNAVAILABLE", cause = e)
        } finally {
            executor.shutdown()
        }
    }

    suspend fun geocode(address: String): GeoPoint = withContext(io) {
        if (!Geocoder.isPresent()) throw AppException("GEOCODER_UNAVAILABLE")
        val geocoder = Geocoder(context, Locale.forLanguageTag("tr-TR"))
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine<android.location.Address?> { cont ->
                geocoder.getFromLocationName(address, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) { cont.resume(addresses.firstOrNull()) }
                    override fun onError(errorMessage: String?) { cont.resume(null) }
                })
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching { geocoder.getFromLocationName(address, 1)?.firstOrNull() }.getOrNull()
        } ?: throw AppException("ADDRESS_NOT_FOUND")
        GeoPoint(result.latitude, result.longitude)
    }
}
