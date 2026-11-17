package com.kuaforumcepte.app.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kuaforumcepte.app.core.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sunucu yalnızca "data" mesajı gönderir (functions/src/notify/push.ts); bildirim burada oluşturulur.
 * Böylece uygulama arka plandayken de tıklama doğru randevuya gider. Aynı bildirim kimliği ikinci kez
 * gelirse Android aynı id ile günceller (yinelenen olay tek bildirim gösterir).
 */
@AndroidEntryPoint
class KcMessagingService : FirebaseMessagingService() {

    @Inject lateinit var registrar: DeviceTokenRegistrar
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onNewToken(token: String) {
        appScope.launch { registrar.register(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        val id = d["notificationId"] ?: return
        Notifications.ensureChannels(this)
        Notifications.show(this, id, d["title"].orEmpty(), d["body"].orEmpty(), d["appointmentId"]?.takeIf { it.isNotBlank() })
    }
}
