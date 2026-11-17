package com.kuaforumcepte.app.feature.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.navigation.DateClosuresRoute
import com.kuaforumcepte.app.data.schedule.ChangeResult
import com.kuaforumcepte.app.data.schedule.ScheduleRepository
import com.kuaforumcepte.app.data.session.ActiveSalon
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.domain.model.MemberRole
import com.kuaforumcepte.domain.schedule.DateClosure
import com.kuaforumcepte.domain.time.BusinessTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class ClosureScope { STAFF, SALON }

sealed interface ClosureListState {
    data object Loading : ClosureListState
    data object NotAuthorized : ClosureListState
    data class Error(val message: UiText) : ClosureListState
    data class Ready(val items: List<DateClosure>) : ClosureListState
}

/** Düzenleme penceresi: closure null → yeni. */
data class ClosureEdit(val closure: DateClosure?)
private data class PendingSave(val id: String?, val start: LocalDate, val end: LocalDate, val note: String)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DateClosuresViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val session: SessionRepository,
    private val schedules: ScheduleRepository,
    private val clock: Clock,
) : ViewModel() {

    val scope: ClosureScope = ClosureScope.valueOf(savedState.toRoute<DateClosuresRoute>().scope)
    val today: LocalDate get() = LocalDate.now(clock.withZone(BusinessTime.ZONE))
    private var active: ActiveSalon? = null

    val list: StateFlow<ClosureListState> = session.activeSalon.flatMapLatest { a ->
        active = a
        val uid = session.currentUid
        when {
            a == null || uid == null -> flowOf(ClosureListState.NotAuthorized)
            scope == ClosureScope.SALON && a.membership.role != MemberRole.OWNER -> flowOf(ClosureListState.NotAuthorized)
            else -> (if (scope == ClosureScope.SALON) schedules.observeSalonClosures(a.membership.salonId) else schedules.observeStaffClosures(a.membership.salonId, uid))
                .map<List<DateClosure>, ClosureListState> { ClosureListState.Ready(it) }
                .onStart { emit(ClosureListState.Loading) }
                .catch { emit(ClosureListState.Error(ErrorMessages.of(it))) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClosureListState.Loading)

    private val _edit = MutableStateFlow<ClosureEdit?>(null)
    val edit: StateFlow<ClosureEdit?> = _edit.asStateFlow()
    private val _change = MutableStateFlow<ChangeState>(ChangeState.Idle)
    val change: StateFlow<ChangeState> = _change.asStateFlow()
    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()
    private var pending: PendingSave? = null

    fun openNew() { _edit.value = ClosureEdit(null) }
    fun openEdit(c: DateClosure) { _edit.value = ClosureEdit(c) }
    fun closeEdit() { if (_change.value == ChangeState.Idle) _edit.value = null }
    fun consumeMessage() { _message.value = null }

    private suspend fun call(p: PendingSave, dryRun: Boolean, ack: Boolean): ChangeResult {
        val salonId = (active ?: session.activeSalon.first())?.membership?.salonId ?: error("salon yok")
        return if (scope == ClosureScope.SALON) schedules.upsertSalonClosure(salonId, p.id, p.start, p.end, p.note, dryRun, ack)
        else schedules.upsertStaffClosure(salonId, p.id, p.start, p.end, p.note, dryRun, ack)
    }

    fun save(start: LocalDate, end: LocalDate, note: String) {
        if (_change.value != ChangeState.Idle) return
        val p = PendingSave(_edit.value?.closure?.id, start, end, note)
        pending = p
        _change.value = ChangeState.Checking
        viewModelScope.launch {
            runCatching { call(p, dryRun = true, ack = false) }
                .onSuccess { r -> if (r.conflicts.isEmpty()) apply(false) else _change.value = ChangeState.AwaitingConfirmation(r.conflicts) }
                .onFailure { e -> _change.value = ChangeState.Idle; _message.value = ErrorMessages.of(e) }
        }
    }

    fun confirmConflicts() = apply(true)
    fun cancelConflicts() { _change.value = ChangeState.Idle }

    private fun apply(ack: Boolean) {
        val p = pending ?: return
        val shown = (_change.value as? ChangeState.AwaitingConfirmation)?.conflicts.orEmpty()
        _change.value = ChangeState.Applying(shown)
        viewModelScope.launch {
            runCatching { call(p, dryRun = false, ack = ack) }
                .onSuccess { r ->
                    if (!r.applied) { _change.value = ChangeState.AwaitingConfirmation(r.conflicts); return@onSuccess }
                    _change.value = ChangeState.Idle
                    _edit.value = null
                    _message.value = UiText.Plain(
                        if (r.conflicts.isEmpty()) "Kapatma kaydedildi." else "Kapatma kaydedildi; ${r.conflicts.size} randevu ilgilenilmesi gereken olarak işaretlendi.",
                    )
                }
                .onFailure { e -> _change.value = ChangeState.Idle; _message.value = ErrorMessages.of(e) }
        }
    }

    fun delete(c: DateClosure) = viewModelScope.launch {
        val salonId = (active ?: session.activeSalon.first())?.membership?.salonId ?: return@launch
        runCatching {
            if (scope == ClosureScope.SALON) schedules.deleteSalonClosure(salonId, c.id) else schedules.deleteStaffClosure(salonId, c.id)
        }.onSuccess { _message.value = UiText.Plain("Kapatma kaldırıldı; bu tarihler yeniden randevuya açık.") }
            .onFailure { _message.value = ErrorMessages.of(it) }
    }
}

