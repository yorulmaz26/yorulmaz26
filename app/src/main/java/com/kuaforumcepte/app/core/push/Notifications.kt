package com.kuaforumcepte.app.core.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kuaforumcepte.app.MainActivity
import com.kuaforumcepte.app.R
import com.kuaforumcepte.domain.link.DeepLinks

object Notifications {
    const val CHANNEL_APPOINTMENTS = "appointments"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_APPOINTMENTS, "Randevular", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Randevu talepleri, onaylar, iptaller ve hatırlatmalar"
            },
        )
    }

    /**
     * Bildirim tıklanınca kuaforumcepte://appointment/{id} ile MainActivity açılır; DeepLinkRepository
     * kullanıcının rolüne göre doğru randevu ekranına yönlendirir. Kilit ekranında içerik gizlenir.
     */
    fun show(context: Context, notificationId: String, title: String, body: String, appointmentId: String?) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = appointmentId?.let { Uri.parse(DeepLinks.appointmentUri(it)) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, notificationId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_APPOINTMENTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Kuaförüm Cepte")
            .setContentText("Randevunuzla ilgili yeni bir bildirim var")
            .build()
        val n = NotificationCompat.Builder(context, CHANNEL_APPOINTMENTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(notificationId.hashCode(), n)
    }
}
