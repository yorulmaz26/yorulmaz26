package com.kuaforumcepte.app.feature.bizappointments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.KcDropdown
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.KcTheme
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.appointments.Appointment
import com.kuaforumcepte.app.feature.appointments.AppointmentStatusChip
import com.kuaforumcepte.app.feature.appointments.apptDate
import com.kuaforumcepte.app.feature.appointments.apptTime
import com.kuaforumcepte.domain.booking.formatDuration
import com.kuaforumcepte.domain.phone.PhoneNumbers
import java.time.Instant

@Composable
fun BusinessAppointmentsContent(onOpen: (String) -> Unit, onMessage: (String) -> Unit, modifier: Modifier = Modifier, viewModel: BusinessAppointmentsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val f by viewModel.filters.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val msg = message?.asString()
    LaunchedEffect(msg) { if (msg != null) { onMessage(msg); viewModel.consumeMessage() } }

    when (val s = state) {
        BizApptState.Loading -> SkeletonCardList(4, modifier)
        BizApptState.NoSalon -> EmptyState("Aktif salon üyeliği yok", modifier = modifier)
        is BizApptState.Error -> ErrorState(s.message.asString(), modifier)
        is BizApptState.Ready -> {
            val list = viewModel.visible(s, f)
            LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                if (s.isOwner) item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ApptScope.entries.forEachIndexed { i, sc ->
                            SegmentedButton(selected = f.scope == sc, onClick = { viewModel.setScope(sc) }, shape = SegmentedButtonDefaults.itemShape(i, ApptScope.entries.size)) { Text(sc.label) }
                        }
                    }
                } else item { Text("Randevularım", style = MaterialTheme.typography.titleLarge) }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        ApptRange.entries.forEach { r -> FilterChip(selected = f.range == r, onClick = { viewModel.setRange(r) }, label = { Text(r.label) }) }
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        ApptFilter.entries.forEach { x -> FilterChip(selected = f.filter == x, onClick = { viewModel.setFilter(x) }, label = { Text(x.label) }) }
                    }
                }
                if (s.isOwner && f.scope == ApptScope.STAFF) item {
                    val others = s.staff.filter { it.uid != s.myUid }
                    KcDropdown("Personel", listOf(null) + others, others.firstOrNull { it.uid == f.staffUid }, { it?.fullName ?: "Tüm personel" }, { viewModel.setStaff(it?.uid) })
                }
                if (selected.isNotEmpty()) item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${selected.size} seçili", Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearSelection) { Text("Vazgeç") }
                        TextButton(onClick = viewModel::hideSelected) { Text("Listemden gizle") }
                    }
                }
                if (list.isEmpty()) item {
                    EmptyState(icon = Icons.Outlined.EditCalendar, title = "Bu filtrede randevu yok", message = "Tarih veya durum filtresini değiştirin.")
                }
                items(list, key = { it.id }) { a ->
                    BizAppointmentCard(a, s.now, showStaff = s.isOwner && f.scope == ApptScope.STAFF, selected = a.id in selected,
                        onClick = { if (selected.isNotEmpty()) viewModel.toggleSelect(a, s.now) else onOpen(a.id) },
                        onLongClick = { viewModel.toggleSelect(a, s.now) })
                }
                if (f.filter == ApptFilter.CLOSED || f.range == ApptRange.PAST30) item {
                    Text("Kapanmış randevuları seçmek için uzun basın; “Listemden gizle” yalnızca sizin görünümünüzü etkiler.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BizAppointmentCard(a: Appointment, now: Instant, showStaff: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        modifier = Modifier
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, MaterialTheme.shapes.large) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Seç")
            .semantics { this.selected = selected },
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${apptTime.format(a.startAt)}–${apptTime.format(a.endAt)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AppointmentStatusChip(a.effectiveStatus(now))
            }
            Text(apptDate.format(a.startAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${a.customerName} · ${PhoneNumbers.formatForDisplay(a.customerPhoneE164)}", style = MaterialTheme.typography.bodyLarge)
            Text(a.items.joinToString { "${it.name} (${formatDuration(it.durationMinutes)})" }, style = MaterialTheme.typography.bodySmall)
            Row {
                Text(if (showStaff) "Uzman: ${a.staffName}" else formatDuration(a.totalMinutes), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(a.finalTotal.formatTry(), style = MaterialTheme.typography.titleSmall)
            }
            if (a.attentionReason != null && a.effectiveStatus(now).isActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PriorityHigh, null, tint = KcTheme.colors.cancel)
                    Text("Takvim değişikliğiyle çakışıyor — yeniden planlayın veya iptal edin", style = MaterialTheme.typography.labelMedium, color = KcTheme.colors.cancel)
                }
            }
        }
    }
}
