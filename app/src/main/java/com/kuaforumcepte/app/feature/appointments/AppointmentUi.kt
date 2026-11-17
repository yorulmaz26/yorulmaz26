package com.kuaforumcepte.app.feature.appointments

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kuaforumcepte.app.core.designsystem.theme.KcTheme
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.appointments.Appointment
import com.kuaforumcepte.domain.model.AppointmentStatus
import com.kuaforumcepte.domain.time.BusinessTime
import java.time.format.DateTimeFormatter

val apptDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", BusinessTime.LOCALE).withZone(BusinessTime.ZONE)
val apptTime: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", BusinessTime.LOCALE).withZone(BusinessTime.ZONE)

fun Appointment.whenLabel(): String = "${apptDate.format(startAt)} · ${apptTime.format(startAt)}–${apptTime.format(endAt)}"

@Composable
fun AppointmentStatusChip(status: AppointmentStatus, modifier: Modifier = Modifier) {
    val c = KcTheme.colors
    val (fg, bg) = when (status) {
        AppointmentStatus.PENDING -> c.pending to c.pendingContainer
        AppointmentStatus.CONFIRMED, AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED -> c.success to c.successContainer
        AppointmentStatus.CANCELLED_BY_CUSTOMER, AppointmentStatus.CANCELLED_BY_STAFF, AppointmentStatus.NO_SHOW -> c.cancel to c.cancelContainer
        AppointmentStatus.EXPIRED -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
    }
    StatusPillBox(status.label, fg, bg, modifier)
}

@Composable
private fun StatusPillBox(text: String, fg: Color, bg: Color, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = bg, modifier = modifier) {
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs))
    }
}

/** Android takvim uygulamasına (Google Takvim dahil) etkinlik ekleme formu; kullanıcı onaylar. */
object CalendarIntents {
    fun insert(a: Appointment): Intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, a.startAt.toEpochMilli())
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, a.endAt.toEpochMilli())
        putExtra(CalendarContract.Events.TITLE, "${a.salonName} randevusu")
        putExtra(CalendarContract.Events.EVENT_LOCATION, a.salonAddress)
        putExtra(
            CalendarContract.Events.DESCRIPTION,
            "Kuaförüm Cepte · ${a.number}\nUzman: ${a.staffName}\nHizmetler: ${a.items.joinToString { it.name }}\n" +
                "Durum takvimde otomatik güncellenmez; güncel durumu uygulamadan kontrol edin.",
        )
        putExtra(CalendarContract.Events.EVENT_TIMEZONE, BusinessTime.ZONE.id)
    }

    fun shareText(a: Appointment): String =
        "${a.salonName} · ${a.whenLabel()}\nUzman: ${a.staffName}\nRandevu no: ${a.number}\n${a.salonAddress}"
}
