package com.kuaforumcepte.app.feature.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiState
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.appointments.Appointment
import com.kuaforumcepte.app.data.appointments.AppointmentRepository
import com.kuaforumcepte.app.data.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

data class MyAppointments(val active: List<Appointment>, val past: List<Appointment>, val now: Instant)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyAppointmentsViewModel @Inject constructor(
    session: SessionRepository,
    appointments: AppointmentRepository,
    clock: Clock,
) : ViewModel() {
    val state: StateFlow<UiState<MyAppointments>> = session.uidFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(UiState.Empty())
        else appointments.observeCustomerHistory(uid).map<List<Appointment>, UiState<MyAppointments>> { list ->
            val now = clock.instant()
            val visible = list.filter { uid !in it.hiddenFor }
            val (active, past) = visible.partition { it.isActive(now) }
            if (visible.isEmpty()) UiState.Empty() else UiState.Success(MyAppointments(active.sortedBy { it.startAt }, past, now))
        }.onStart { emit(UiState.Loading) }.catch { emit(UiState.Error(ErrorMessages.of(it))) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
}

/** Müşteri "Randevularım" sekmesi: aktif/bekleyen/onaylı ve geçmiş randevular. */
@Composable
fun MyAppointmentsContent(onOpen: (String) -> Unit, onBook: () -> Unit, modifier: Modifier = Modifier, viewModel: MyAppointmentsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        UiState.Loading -> SkeletonCardList(3, modifier)
        is UiState.Error -> ErrorState(s.message.asString(), modifier)
        is UiState.Empty -> EmptyState(
            icon = Icons.Outlined.EventAvailable, title = "Henüz randevunuz yok",
            message = "Aldığınız randevular burada aktif ve geçmiş olarak listelenir.", actionLabel = "Randevu Al", onAction = onBook, modifier = modifier,
        )
        is UiState.Success -> LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            if (s.data.active.isNotEmpty()) item { Text("Aktif", style = MaterialTheme.typography.titleMedium) }
            items(s.data.active, key = { it.id }) { a -> AppointmentRow(a, s.data.now) { onOpen(a.id) } }
            if (s.data.past.isNotEmpty()) item { Text("Geçmiş", style = MaterialTheme.typography.titleMedium) }
            items(s.data.past, key = { it.id }) { a -> AppointmentRow(a, s.data.now) { onOpen(a.id) } }
        }
    }
}

@Composable
fun AppointmentRow(a: Appointment, now: Instant, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.salonName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AppointmentStatusChip(a.effectiveStatus(now))
            }
            Text(a.whenLabel(), style = MaterialTheme.typography.bodyMedium)
            Text("${a.staffName} · ${a.items.joinToString { it.name }} · ${a.finalTotal.formatTry()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
