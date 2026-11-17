package com.kuaforumcepte.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kuaforumcepte.domain.model.EntryRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Cihaz tercihleri. Kimlik bilgisi veya token tutulmaz: Firebase Auth oturumu kendi
 * şifreli deposunda saklanır.
 *
 * "Beni hatırla" politikası: true (varsayılan) → oturum uygulama yeniden açıldığında sürer.
 * false → uygulama soğuk başlatıldığında oturum kapatılır (SplashViewModel uygular).
 */
data class AppSettings(
    val rememberMe: Boolean = true,
    val lastEntryRole: EntryRole? = null,
    /** İşletme tarafında aktif salon (çoklu salon üyeliği). */
    val activeBusinessSalonId: String? = null,
    /** Müşterinin seçtiği salon (Salon Bilgileri sekmesi için tek kaynak). */
    val selectedCustomerSalonId: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kc_settings")

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val rememberMe = booleanPreferencesKey("remember_me")
        val lastEntryRole = stringPreferencesKey("last_entry_role")
        val activeBusinessSalonId = stringPreferencesKey("active_business_salon_id")
        val selectedCustomerSalonId = stringPreferencesKey("selected_customer_salon_id")
        val themeMode = stringPreferencesKey("theme_mode")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                rememberMe = p[Keys.rememberMe] ?: true,
                lastEntryRole = p[Keys.lastEntryRole]?.let { v -> EntryRole.entries.firstOrNull { it.name == v } },
                activeBusinessSalonId = p[Keys.activeBusinessSalonId],
                selectedCustomerSalonId = p[Keys.selectedCustomerSalonId],
                themeMode = p[Keys.themeMode]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.SYSTEM,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setRememberMe(value: Boolean) = context.dataStore.edit { it[Keys.rememberMe] = value }
    suspend fun setLastEntryRole(role: EntryRole) = context.dataStore.edit { it[Keys.lastEntryRole] = role.name }
    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.themeMode] = mode.name }

    suspend fun setActiveBusinessSalon(salonId: String?) = context.dataStore.edit {
        if (salonId == null) it.remove(Keys.activeBusinessSalonId) else it[Keys.activeBusinessSalonId] = salonId
    }

    suspend fun setSelectedCustomerSalon(salonId: String?) = context.dataStore.edit {
        if (salonId == null) it.remove(Keys.selectedCustomerSalonId) else it[Keys.selectedCustomerSalonId] = salonId
    }

    /** Çıkışta oturuma bağlı seçimler temizlenir; tema ve beni hatırla korunur. */
    suspend fun clearSessionScoped() = context.dataStore.edit {
        it.remove(Keys.activeBusinessSalonId)
        it.remove(Keys.selectedCustomerSalonId)
        it.remove(Keys.lastEntryRole)
    }
}
