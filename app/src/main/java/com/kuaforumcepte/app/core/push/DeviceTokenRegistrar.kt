package com.kuaforumcepte.app.core.push

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FCM token'ını users/{uid}/devices/{installationId} altına yazar (Firestore kuralları yalnızca
 * sahibine izin verir). Token yenilendiğinde ve her girişte güncellenir; çıkışta silinir.
 */
@Singleton
class DeviceTokenRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: Lazy<FirebaseAuth>,
    private val firestore: Lazy<FirebaseFirestore>,
    private val messaging: Lazy<FirebaseMessaging>,
) {
    private val prefs by lazy { context.getSharedPreferences("kc_device", Context.MODE_PRIVATE) }

    /** Uygulama kurulumu başına sabit kimlik (yeniden kurulumda değişir). */
    val installationId: String
        get() = prefs.getString("installation_id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("installation_id", it).apply() }

    private fun ref(uid: String) = firestore.get().collection("users").document(uid).collection("devices").document(installationId)

    suspend fun register(token: String? = null) {
        val uid = auth.get().currentUser?.uid ?: return
        val t = token ?: runCatching { messaging.get().token.await() }.getOrNull() ?: return
        runCatching {
            ref(uid).set(mapOf("token" to t, "platform" to "android", "updatedAt" to FieldValue.serverTimestamp())).await()
        }
    }

    /** Çıkıştan önce çağrılır: bu cihaz artık o hesabın bildirimlerini almaz. */
    suspend fun unregister() {
        val uid = auth.get().currentUser?.uid ?: return
        runCatching { ref(uid).delete().await() }
    }
}
