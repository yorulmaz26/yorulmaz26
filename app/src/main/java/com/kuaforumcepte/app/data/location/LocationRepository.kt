package com.kuaforumcepte.app.data.location

import android.content.Context
import com.kuaforumcepte.app.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class City(val code: String, val name: String, val districts: List<String>)

/** 81 il / 973 ilçe — assets/tr_locations.json (sunucu da aynı listeyle doğrular). */
@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var cache: List<City>? = null

    suspend fun cities(): List<City> = mutex.withLock {
        cache ?: withContext(io) {
            context.assets.open("tr_locations.json").bufferedReader().use { r ->
                Json.decodeFromString(ListSerializer(City.serializer()), r.readText())
            }
        }.also { cache = it }
    }

    suspend fun districtsOf(city: String): List<String> = cities().firstOrNull { it.name == city }?.districts.orEmpty()
}
