package com.kuaforumcepte.app.feature.appointments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiState
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.data.appointments.Appointment
import com.kuaforumcepte.app.data.appointments.AppointmentRepository
import com.kuaforumcepte.domain.model.AppointmentStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** Müşterinin tek randevusu (bilet): BookingSuccessRoute ve AppointmentDetailRoute ortak kullanır. */
@HiltViewModel
class CustomerAppointmentViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val appointments: AppointmentRepository,
    private val clock: Clock,
) : ViewModel() {
    private val id: String = checkNotNull(savedState["appointmentId"])

    /** Süre dolumunu ve 2 saat iptal penceresini canlı göstermek için dakikalık saat. */
    private val ticker = flow { while (true) { emit(clock.instant()); delay(30_000) } }

    val state: StateFlow<UiState<Pair<Appointment, Instant>>> = combine(appointments.observe(id), ticker) { a, now -> a to now }
        .map<Pair<Appointment?, Instant>, UiState<Pair<Appointment, Instant>>> { (a, now) ->
            if (a == null) UiState.Empty("Randevu bulunamadı.") else UiState.Success(a to now)
        }
        .onStart { emit(UiState.Loading) }
        .catch { emit(UiState.Error(ErrorMessages.of(it))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    fun cancel(reason: String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            runCatching { appointments.transition(id, "CANCEL_BY_CUSTOMER", reason.trim().ifBlank { null }) }
                .onSuccess { st ->
                    _message.value = UiText.Plain(
                        if (st == AppointmentStatus.EXPIRED) "Talebinizin onay süresi dolmuştu; randevu zaten düşmüş."
                        else "Randevunuz iptal edildi. Salon bilgilendirildi.",
                    )
                }
                .onFailure { _message.value = ErrorMessages.of(it) }
            _busy.value = false
        }
    }
}
