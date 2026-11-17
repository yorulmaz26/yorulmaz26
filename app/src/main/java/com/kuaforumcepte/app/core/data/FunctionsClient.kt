package com.kuaforumcepte.app.core.data

import com.google.firebase.functions.FirebaseFunctions
import dagger.Lazy
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Callable Cloud Functions çağrısı; tüm hatalar AppException'a çevrilir. */
@Singleton
class FunctionsClient @Inject constructor(private val functions: Lazy<FirebaseFunctions>) {

    suspend fun call(name: String, data: Map<String, Any?> = emptyMap()): Map<String, Any?> = try {
        val result = functions.get()
            .getHttpsCallable(name)
            .apply { setTimeout(30, TimeUnit.SECONDS) }
            .call(data)
            .await()
        @Suppress("UNCHECKED_CAST")
        (result.data as? Map<String, Any?>) ?: emptyMap()
    } catch (t: Throwable) {
        throw AppException.from(t)
    }
}

/** Callable yanıtlarını okumak için küçük yardımcılar (Firebase Map/List döner). */
@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.map(key: String): Map<String, Any?> = (this[key] as? Map<String, Any?>) ?: emptyMap()

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.list(key: String): List<Map<String, Any?>> =
    (this[key] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()

internal fun Map<String, Any?>.str(key: String): String? = this[key] as? String
