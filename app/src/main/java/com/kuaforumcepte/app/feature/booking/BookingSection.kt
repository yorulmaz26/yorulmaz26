package com.kuaforumcepte.app.feature.booking

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.SkeletonBlock
import com.kuaforumcepte.app.core.designsystem.theme.KcTheme
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.services.StaffService
import com.kuaforumcepte.app.data.staff.StaffMember
import com.kuaforumcepte.domain.booking.formatDuration
import com.kuaforumcepte.domain.model.MemberRole

/** Salon sayfasında uzman ve hizmet seçimi. Toplamlar seçim değiştikçe anında güncellenir. */
@Composable
fun BookingSection(salonId: String, onActiveAppointment: (String) -> Unit, viewModel: BookingSectionViewModel = hiltViewModel()) {
    LaunchedEffect(salonId) { viewModel.bind(salonId) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text("Randevu Al", style = MaterialTheme.typography.headlineSmall)
        ui.activeAppointment?.let { a ->
            ActiveAppointmentCard(a, onOpen = { onActiveAppointment(a.id) })
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("1. Uzman seçin", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StaffSort.entries.forEach { s ->
                FilterChip(selected = ui.staffSort == s, onClick = { viewModel.onSort(s) }, label = { Text(s.label) }, modifier = Modifier.padding(start = Spacing.xs))
            }
        }
        val staff = ui.staff
        when {
            staff == null -> SkeletonBlock(Modifier.fillMaxWidth(), 96.dp)
            staff.isEmpty() -> Text("Bu salonda şu anda randevu alan uzman yok.", style = MaterialTheme.typography.bodyMedium)
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                items(staff, key = { it.uid }) { m -> StaffCard(m, selected = ui.selectedStaff?.uid == m.uid) { viewModel.selectStaff(m) } }
            }
        }

        val selected = ui.selectedStaff ?: return@Column
        Text("2. Hizmet seçin", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = ui.serviceSearch, onValueChange = viewModel::onSearch, singleLine = true,
            placeholder = { Text("${selected.firstName} hizmetlerinde ara") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
        )
        val services = ui.services
        when {
            services == null -> SkeletonBlock(Modifier.fillMaxWidth(), 72.dp)
            services.isEmpty() && ui.serviceSearch.isNotBlank() -> Text("Aramayla eşleşen hizmet yok.")
            services.isEmpty() -> InfoBanner("${selected.fullName} henüz hizmet eklememiş. Başka bir uzman seçebilirsiniz.")
            else -> services.forEach { s -> ServiceRow(s, ui.selection.isSelected(s.id)) { viewModel.toggle(s) } }
        }
    }
}

@Composable
fun ActiveAppointmentCard(a: com.kuaforumcepte.app.data.appointments.Appointment, onOpen: () -> Unit) {
    Surface(onClick = onOpen, shape = MaterialTheme.shapes.large, color = KcTheme.colors.pendingContainer) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Bekleyen/aktif randevunuz bulunmaktadır", style = MaterialTheme.typography.titleMedium)
            Text("${a.salonName} · ${com.kuaforumcepte.app.feature.appointments.apptDate.format(a.startAt)} ${com.kuaforumcepte.app.feature.appointments.apptTime.format(a.startAt)}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Aynı anda yalnızca bir aktif randevunuz olabilir. Randevunuz tamamlandığında, iptal edildiğinde veya talebiniz düştüğünde yeni randevu alabilirsiniz.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Görüntüle / İptal et →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StaffCard(m: StaffMember, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.width(168.dp)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, MaterialTheme.shapes.large) else Modifier)
            .semantics { this.selected = selected; stateDescription = if (selected) "Seçildi" else "Seçilmedi" },
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(m.fullName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(if (m.role == MemberRole.OWNER) "Salon Sahibi" else "Uzman", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text(m.specialty, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Star, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Text(" ${m.rating.label()}", style = MaterialTheme.typography.labelLarge)
            }
            if (selected) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp), tint = KcTheme.colors.success)
                Text(" Seçildi", style = MaterialTheme.typography.labelMedium, color = KcTheme.colors.success)
            }
        }
    }
}

@Composable
private fun ServiceRow(s: StaffService, checked: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.medium,
        color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.semantics { this.selected = checked },
    ) {
        Row(Modifier.fillMaxWidth().padding(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleSmall)
                if (s.description.isNotBlank()) Text(s.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Text(formatDuration(s.durationMinutes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(s.price.formatTry(), style = MaterialTheme.typography.titleSmall)
        }
    }
}

