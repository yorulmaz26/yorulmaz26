package com.kuaforumcepte.app.feature.bizappointments

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.data.appointments.AppointmentRepository
import com.kuaforumcepte.app.data.booking.BookingRepository
import com.kuaforumcepte.app.data.booking.Slot
import com.kuaforumcepte.app.feature.booking.BaseSlotViewModel
import com.kuaforumcepte.app.feature.booking.SlotPickerContent
import com.kuaforumcepte.domain.time.BusinessTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class RescheduleViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val booking: BookingRepository,
    private val appointments: AppointmentRepository,
    clock: Clock,
) : BaseSlotViewModel(savedState, clock) {
    private val appointmentId: String = checkNotNull(savedState["appointmentId"])
    override suspend fun fetch(from: LocalDate, days: Int) = booking.availabilityForReschedule(appointmentId, from, days)
    init { start() }

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()
    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()
    private val _error = MutableStateFlow<UiText?>(null)
    val error: StateFlow<UiText?> = _error.asStateFlow()
    fun consumeError() { _error.value = null }

    fun apply(slot: Slot) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            runCatching { appointments.reschedule(appointmentId, slot.startAt) }
                .onSuccess { _done.send(Unit) }
                .onFailure { _error.value = ErrorMessages.of(it); load() }
            _saving.value = false
        }
    }
}

private val confirmFmt = DateTimeFormatter.ofPattern("d MMMM EEEE HH:mm", BusinessTime.LOCALE).withZone(BusinessTime.ZONE)

/** Salon tarafı saat değişikliği: aynı uzmanın güncel müsaitliği (randevunun kendisi hariç). */
@Composable
fun RescheduleScreen(onBack: () -> Unit, onDone: () -> Unit, viewModel: RescheduleViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pick by remember { mutableStateOf<Slot?>(null) }
    val errText = error?.asString()
    LaunchedEffect(Unit) {
        viewModel.done.collect {
            Toast.makeText(context, "Saat güncellendi; müşteriye bildirim gönderildi.", Toast.LENGTH_LONG).show()
            onDone()
        }
    }
    LaunchedEffect(errText) { if (errText != null) { Toast.makeText(context, errText, Toast.LENGTH_LONG).show(); viewModel.consumeError() } }

    SlotPickerContent(
        title = "Saati Değiştir", ui = ui, canPrev = viewModel.canPrev, canNext = viewModel.canNext,
        onBack = onBack, onReload = viewModel::load, onSelectDate = viewModel::selectDate, onShift = viewModel::shiftWindow,
        onSlotSelected = { pick = it }, showPrice = false,
        footer = "Müşteriye yeni saat bildirilir. Uygun değilse müşteri kendi iptal hakkını (başlangıçtan 2 saat öncesine kadar) kullanabilir.",
    )
    pick?.let { slot ->
        AlertDialog(
            onDismissRequest = { if (!saving) pick = null },
            title = { Text("Yeni saat onaylansın mı?") },
            text = { Text(confirmFmt.format(slot.startAt)) },
            confirmButton = { TextButton(onClick = { pick = null; viewModel.apply(slot) }, enabled = !saving) { Text("Güncelle") } },
            dismissButton = { TextButton(onClick = { pick = null }) { Text("Vazgeç") } },
        )
    }
}
