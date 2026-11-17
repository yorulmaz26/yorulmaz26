package com.kuaforumcepte.app.feature.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.core.designsystem.theme.KcTheme
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.schedule.ScheduleConflict
import com.kuaforumcepte.domain.schedule.ClosureValidator
import com.kuaforumcepte.domain.schedule.TimeRange
import com.kuaforumcepte.domain.time.BusinessTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val trDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", BusinessTime.LOCALE)
val trShortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", BusinessTime.LOCALE)

/**
 * Değişiklik mevcut randevuları etkiliyorsa gösterilir. Onaylanırsa randevular bozulmaz;
 * "ilgilenilmesi gerekiyor" olarak işaretlenir ve randevu yönetiminden yeniden planlanır/iptal edilir.
 */
@Composable
fun ConflictDialog(conflicts: List<ScheduleConflict>, applying: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!applying) onDismiss() },
        title = { Text("${conflicts.size} randevu etkileniyor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    "Bu değişiklik aşağıdaki randevularla çakışıyor. Randevular otomatik iptal edilmez; uyguladığınızda " +
                        "“ilgilenilmesi gerekiyor” olarak işaretlenir ve Randevular bölümünden müşteriyi bilgilendirerek yeniden planlamanız veya iptal etmeniz gerekir.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(conflicts, key = { it.appointmentId }) { c ->
                        Surface(shape = MaterialTheme.shapes.small, color = KcTheme.colors.cancelContainer) {
                            Column(Modifier.fillMaxWidth().padding(Spacing.s)) {
                                Text("${c.localDate.format(trShortDate)} · ${c.start}–${c.end}", style = MaterialTheme.typography.titleSmall)
                                Text("${c.customerName} · ${c.reasonLabel}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !applying) { Text(if (applying) "Uygulanıyor…" else "Yine de Uygula", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !applying) { Text("Vazgeç") } },
    )
}

/** Başlangıç–bitiş saati seçimi (24 saat). Bitiş için "Gün sonu (24:00)" seçeneği vardır. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRangeDialog(title: String, initial: TimeRange?, onSave: (TimeRange) -> Unit, onDismiss: () -> Unit) {
    val start = rememberTimePickerState(initialHour = (initial?.start ?: 540) / 60, initialMinute = (initial?.start ?: 540) % 60, is24Hour = true)
    val endInit = initial?.end?.coerceAtMost(1439) ?: 1080
    val end = rememberTimePickerState(initialHour = endInit / 60, initialMinute = endInit % 60, is24Hour = true)
    var endOfDay by remember { mutableStateOf(initial?.end == 1440) }
    val startMin = start.hour * 60 + start.minute
    val endMin = if (endOfDay) 1440 else end.hour * 60 + end.minute
    val valid = endMin > startMin

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text("Başlangıç", style = MaterialTheme.typography.labelLarge)
                TimeInput(state = start)
                Text("Bitiş", style = MaterialTheme.typography.labelLarge)
                if (!endOfDay) TimeInput(state = end)
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { endOfDay = !endOfDay },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = endOfDay, onCheckedChange = { endOfDay = it })
                    Text("Gün sonuna kadar (24:00)")
                }
                if (!valid) Text("Bitiş saati başlangıçtan sonra olmalı. Gece yarısını geçen vardiyalar desteklenmez.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(TimeRange(startMin, endMin)) }, enabled = valid) { Text("Tamam") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

/** DatePicker UTC gece yarısı milisaniyesi ↔ LocalDate */
private fun LocalDate.toPickerMillis(): Long = toEpochDay() * 86_400_000L
private fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(this, 86_400_000L))

/**
 * İki adımlı kapatma düzenleyici: 1) tarih aralığı seçimi (geçmiş tarihler seçilemez),
 * 2) açıklama + özet. Kaydet → çağıran taraf sunucu önizlemesi/çakışma akışını yürütür.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosureEditorDialog(
    title: String,
    today: LocalDate,
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    initialNote: String,
    noteLabel: String,
    saving: Boolean,
    onSave: (LocalDate, LocalDate, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }
    var note by remember { mutableStateOf(initialNote) }
    var picking by remember { mutableStateOf(initialStart == null) }

    if (picking) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = start?.toPickerMillis(),
            initialSelectedEndDateMillis = end?.toPickerMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = !utcTimeMillis.toLocalDate().isBefore(today)
                override fun isSelectableYear(year: Int) = year >= today.year && year <= today.year + 1
            },
        )
        DatePickerDialog(
            onDismissRequest = { if (start == null) onDismiss() else picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        start = state.selectedStartDateMillis?.toLocalDate()
                        end = (state.selectedEndDateMillis ?: state.selectedStartDateMillis)?.toLocalDate()
                        picking = false
                    },
                    enabled = state.selectedStartDateMillis != null,
                ) { Text("Devam") }
            },
            dismissButton = { TextButton(onClick = { if (start == null) onDismiss() else picking = false }) { Text("Vazgeç") } },
        ) {
            DateRangePicker(state = state, title = { Text("Kapatılacak tarih(ler)", Modifier.padding(start = 24.dp, top = 16.dp)) }, modifier = Modifier.weight(1f))
        }
        return
    }

    val s = start ?: return
    val e = end ?: s
    val error = ClosureValidator.validate(s, e, note, today)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(if (s == e) s.format(trDate) else "${s.format(trShortDate)} – ${e.format(trShortDate)} (${java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1} gün)", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { picking = true }, enabled = !saving) { Text("Tarihleri değiştir") }
                KcTextField(note, { note = it }, noteLabel, maxLength = 200, singleLine = false, minLines = 2)
                error?.let { Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(s, e, note) }, enabled = error == null && !saving) { Text(if (saving) "Kontrol ediliyor…" else "Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Vazgeç") } },
    )
}
