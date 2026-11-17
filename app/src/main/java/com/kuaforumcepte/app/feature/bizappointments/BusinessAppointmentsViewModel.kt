package com.kuaforumcepte.app.feature.bizappointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.data.appointments.Appointment
import com.kuaforumcepte.app.data.appointments.AppointmentRepository
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.app.data.staff.StaffMember
import com.kuaforumcepte.app.data.staff.StaffRepository
import com.kuaforumcepte.domain.model.AppointmentStatus
import com.kuaforumcepte.domain.model.MemberRole
import com.kuaforumcepte.domain.time.BusinessTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

enum class ApptScope(val label: String) { MINE("Benim Randevularım"), STAFF("Çalışan Randevuları") }

enum class ApptRange(val label: String) { TODAY("Bugün"), TOMORROW("Yarın"), NEXT7("7 gün"), PAST30("Son 30 gün") }

enum class ApptFilter(val label: String) { ALL("Tümü"), PENDING("Bekleyen"), CONFIRMED("Onaylı"), IN_PROGRESS("İşlemde"), ATTENTION("İlgilenilmeli"), CLOSED("Kapanan") }

data class BizApptFilters(
    val scope: ApptScope = ApptScope.MINE,
    val range: ApptRange = ApptRange.TODAY,
    val filter: ApptFilter = ApptFilter.ALL,
    val staffUid: String? = null,
)

sealed interface BizApptState {
    data object Loading : BizApptState
    data object NoSalon : BizApptState
    data class Error(val message: UiText) : BizApptState
    data class Ready(
        val isOwner: Boolean,
        val myUid: String,
        val items: List<Appointment>,
        val staff: List<StaffMember>,
        val now: Instant,
    ) : BizApptState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BusinessAppointmentsViewModel @Inject constructor(
    private val session: SessionRepository,
    private val appointments: AppointmentRepository,
    private val staffRepo: StaffRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _filters = MutableStateFlow(BizApptFilters())
    val filters: StateFlow<BizApptFilters> = _filters.asStateFlow()
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()
    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    private fun window(range: ApptRange): Pair<Instant, Instant> {
        val today = LocalDate.now(clock.withZone(BusinessTime.ZONE))
        val (from, to) = when (range) {
            ApptRange.TODAY -> today to today.plusDays(1)
            ApptRange.TOMORROW -> today.plusDays(1) to today.plusDays(2)
            ApptRange.NEXT7 -> today to today.plusDays(7)
            ApptRange.PAST30 -> today.minusDays(30) to today.plusDays(1)
        }
        return BusinessTime.startOfDay(from) to BusinessTime.startOfDay(to)
    }

    val state: StateFlow<BizApptState> = combine(session.activeSalon, _filters.map { it.range }) { a, r -> a to r }
        .flatMapLatest { (active, range) ->
            val uid = session.currentUid
            if (active == null || uid == null) return@flatMapLatest flowOf(BizApptState.NoSalon)
            val salonId = active.membership.salonId
            val isOwner = active.membership.role == MemberRole.OWNER
            val (from, to) = window(range)
            val source = if (isOwner) appointments.observeSalonRange(salonId, from, to) else appointments.observeStaffRange(salonId, uid, from, to)
            val staff = if (isOwner) staffRepo.members(salonId).catch { emit(emptyList()) } else flowOf(emptyList())
            combine(source, staff) { list, members ->
                BizApptState.Ready(isOwner, uid, list.filter { uid !in it.hiddenFor }, members, clock.instant()) as BizApptState
            }.onStart { emit(BizApptState.Loading) }.catch { emit(BizApptState.Error(ErrorMessages.of(it))) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BizApptState.Loading)

    /** Filtrelenmiş görünüm (kapsam, durum, personel) — veriyi yeniden sorgulamaz. */
    fun visible(s: BizApptState.Ready, f: BizApptFilters): List<Appointment> = s.items
        .filter { a ->
            when {
                !s.isOwner -> true
                f.scope == ApptScope.MINE -> a.staffUid == s.myUid
                else -> a.staffUid != s.myUid && (f.staffUid == null || a.staffUid == f.staffUid)
            }
        }
        .filter { a ->
            val st = a.effectiveStatus(s.now)
            when (f.filter) {
                ApptFilter.ALL -> true
                ApptFilter.PENDING -> st == AppointmentStatus.PENDING
                ApptFilter.CONFIRMED -> st == AppointmentStatus.CONFIRMED
                ApptFilter.IN_PROGRESS -> st == AppointmentStatus.IN_PROGRESS
                ApptFilter.ATTENTION -> a.attentionReason != null && st.isActive
                ApptFilter.CLOSED -> st.isTerminal
            }
        }
        .let { list -> if (f.range == ApptRange.PAST30) list.sortedByDescending { it.startAt } else list.sortedBy { it.startAt } }

    fun setScope(s: ApptScope) = _filters.update { it.copy(scope = s, staffUid = null) }
    fun setRange(r: ApptRange) { _selected.value = emptySet(); _filters.update { it.copy(range = r) } }
    fun setFilter(f: ApptFilter) = _filters.update { it.copy(filter = f) }
    fun setStaff(uid: String?) = _filters.update { it.copy(staffUid = uid) }
    fun consumeMessage() { _message.value = null }

    /** Toplu gizleme yalnızca kapanmış randevular için. */
    fun toggleSelect(a: Appointment, now: Instant) {
        if (!a.effectiveStatus(now).isTerminal) return
        _selected.update { if (a.id in it) it - a.id else it + a.id }
    }

    fun clearSelection() { _selected.value = emptySet() }

    fun hideSelected() {
        val ids = _selected.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { appointments.hide(ids) }
                .onSuccess { n -> _selected.value = emptySet(); _message.value = UiText.Plain("$n randevu listenizden gizlendi. Kayıtlar ve raporlar korunur.") }
                .onFailure { _message.value = ErrorMessages.of(it) }
        }
    }
}
