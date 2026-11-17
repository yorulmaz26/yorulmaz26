package com.kuaforumcepte.app.feature.bizappointments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.data.appointments.Appointment
import com.kuaforumcepte.app.data.appointments.AppointmentEvent
import com.kuaforumcepte.app.data.appointments.AppointmentRepository
import com.kuaforumcepte.app.data.salon.SalonRepository
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.app.data.staff.StaffMember
import com.kuaforumcepte.app.data.staff.StaffRepository
import com.kuaforumcepte.domain.model.ActorRole
import com.kuaforumcepte.domain.model.AppointmentAction
import com.kuaforumcepte.domain.model.AppointmentStatus
import com.kuaforumcepte.domain.model.AppointmentTransitions
import com.kuaforumcepte.domain.model.MemberStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

data class BizApptDetail(
    val appointment: Appointment,
    val actor: ActorRole,
    val actions: List<AppointmentAction>,
    val events: List<AppointmentEvent>,
    /** Yalnızca sahip için: atanabilecek aktif uzmanlar. */
    val reassignTargets: List<StaffMember>,
    val now: Instant,
)

sealed interface BizApptDetailState {
    data object Loading : BizApptDetailState
    data object NotFound : BizApptDetailState
    data class Error(val message: UiText) : BizApptDetailState
    data class Ready(val d: BizApptDetail) : BizApptDetailState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BusinessAppointmentViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val appointments: AppointmentRepository,
    private val salons: SalonRepository,
    private val staffRepo: StaffRepository,
    private val session: SessionRepository,
    private val clock: Clock,
) : ViewModel() {
    val id: String = checkNotNull(savedState["appointmentId"])
    private val ticker = flow { while (true) { emit(clock.instant()); delay(30_000) } }

    val state: StateFlow<BizApptDetailState> = appointments.observe(id).flatMapLatest { a ->
        if (a == null) return@flatMapLatest flowOf(BizApptDetailState.NotFound)
        val uid = session.currentUid
        salons.observe(a.salonId).flatMapLatest { salon ->
            val isOwner = salon?.ownerUid == uid
            val actor = if (isOwner) ActorRole.OWNER else ActorRole.STAFF
            val staff = if (isOwner) staffRepo.members(a.salonId).catch { emit(emptyList()) } else flowOf(emptyList())
            combine(appointments.observeEvents(id), staff, ticker) { events, members, now ->
                val actions = AppointmentTransitions.availableActions(a.status, actor, now, a.startAt, a.pendingExpiresAt)
                    .filter { it != AppointmentAction.COMPLETE && it != AppointmentAction.EXPIRE && it != AppointmentAction.CANCEL_BY_CUSTOMER }
                BizApptDetailState.Ready(
                    BizApptDetail(
                        appointment = a, actor = actor, actions = if (a.effectiveStatus(now) == AppointmentStatus.EXPIRED) emptyList() else actions,
                        events = events,
                        reassignTargets = members.filter { it.status == MemberStatus.ACTIVE && it.uid != a.staffUid && it.uid != a.customerUid },
                        now = now,
                    ),
                ) as BizApptDetailState
            }
        }
    }.catch { emit(BizApptDetailState.Error(ErrorMessages.of(it))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BizApptDetailState.Loading)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    private fun run(success: String, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            runCatching { block() }.onSuccess { _message.value = UiText.Plain(success) }.onFailure { _message.value = ErrorMessages.of(it) }
            _busy.value = false
        }
    }

    fun act(action: AppointmentAction, reason: String? = null) {
        val success = when (action) {
            AppointmentAction.CONFIRM -> "Randevu onaylandı; müşteriye bildirim gönderildi."
            AppointmentAction.REJECT -> "Talep reddedildi; müşteriye bildirildi."
            AppointmentAction.CANCEL_BY_STAFF -> "Randevu iptal edildi; müşteriye bildirildi."
            AppointmentAction.MARK_ARRIVED -> "Müşteri geldi olarak işaretlendi."
            AppointmentAction.MARK_NO_SHOW -> "Gelmedi olarak işaretlendi. Bu randevu gelir olarak sayılmaz."
            else -> "İşlem tamamlandı."
        }
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            runCatching { appointments.transition(id, action.name, reason) }
                .onSuccess { st ->
                    _message.value = UiText.Plain(if (st == AppointmentStatus.EXPIRED) "Talebin onay süresi dolmuştu; randevu düştü." else success)
                }
                .onFailure { _message.value = ErrorMessages.of(it) }
            _busy.value = false
        }
    }

    fun reassign(m: StaffMember) = run("Randevu ${m.fullName} uzmanına atandı; müşteri ve uzmanlar bilgilendirildi.") { appointments.reassign(id, m.uid) }
}
