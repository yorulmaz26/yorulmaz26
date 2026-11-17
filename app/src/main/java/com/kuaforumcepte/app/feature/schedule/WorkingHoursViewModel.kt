package com.kuaforumcepte.app.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.data.schedule.ChangeResult
import com.kuaforumcepte.app.data.schedule.ScheduleRepository
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.domain.schedule.DaySchedule
import com.kuaforumcepte.domain.schedule.ScheduleError
import com.kuaforumcepte.domain.schedule.ScheduleValidator
import com.kuaforumcepte.domain.schedule.TimeRange
import com.kuaforumcepte.domain.schedule.WeeklySchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RangeKind { SHIFT, BREAK }

/** Düzenlenen aralık: index null → yeni ekleme. */
data class RangeEdit(val day: Int, val kind: RangeKind, val index: Int?, val initial: TimeRange?)

data class WorkingHoursUi(
    val loading: Boolean = true,
    val noSalon: Boolean = false,
    val salonId: String? = null,
    val original: WeeklySchedule? = null,
    val draft: WeeklySchedule = WeeklySchedule.default(),
    val editing: RangeEdit? = null,
    val change: ChangeState = ChangeState.Idle,
    val message: UiText? = null,
    val loadError: UiText? = null,
) {
    val errors: Map<Int, ScheduleError> get() = ScheduleValidator.validate(draft)
    val dirty: Boolean get() = original != null && original != draft
    val canSave: Boolean get() = dirty && errors.isEmpty() && change == ChangeState.Idle
}

/**
 * Kişisel haftalık çalışma saatleri, molalar ve haftalık izin günleri. Çalışma Saatleri ve
 * Haftalık İzin Günleri ekranları bu ViewModel'i ve aynı sunucu fonksiyonunu kullanır.
 */
@HiltViewModel
class WorkingHoursViewModel @Inject constructor(
    private val session: SessionRepository,
    private val schedules: ScheduleRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(WorkingHoursUi())
    val ui: StateFlow<WorkingHoursUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val active = session.activeSalon.first()
                val uid = session.currentUid
                if (active == null || uid == null) { _ui.update { it.copy(loading = false, noSalon = true) }; return@launch }
                val week = schedules.observeWeekly(active.membership.salonId, uid).first()
                _ui.update { it.copy(loading = false, salonId = active.membership.salonId, original = week, draft = week) }
            } catch (t: Throwable) {
                _ui.update { it.copy(loading = false, loadError = ErrorMessages.of(t)) }
            }
        }
    }

    private fun editDay(day: Int, block: (DaySchedule) -> DaySchedule) = _ui.update { s ->
        s.copy(draft = s.draft.copy(days = s.draft.days + (day to block(s.draft.day(day)))))
    }

    fun setWorking(day: Int, working: Boolean) = _ui.update { s ->
        val off = if (working) s.draft.weeklyDaysOff - day else s.draft.weeklyDaysOff + day
        var draft = s.draft.copy(weeklyDaysOff = off)
        // Çalışır yapılan gün boşsa makul bir varsayılan vardiya önerilir.
        if (working && draft.day(day).shifts.isEmpty()) draft = draft.copy(days = draft.days + (day to DaySchedule(listOf(TimeRange(540, 1140)))))
        s.copy(draft = draft)
    }

    fun startEdit(edit: RangeEdit) = _ui.update { it.copy(editing = edit) }
    fun cancelEdit() = _ui.update { it.copy(editing = null) }

    fun saveRange(range: TimeRange) {
        val e = _ui.value.editing ?: return
        editDay(e.day) { d ->
            fun upd(list: List<TimeRange>) = (if (e.index == null) list + range else list.mapIndexed { i, r -> if (i == e.index) range else r }).sortedBy { it.start }
            if (e.kind == RangeKind.SHIFT) d.copy(shifts = upd(d.shifts)) else d.copy(breaks = upd(d.breaks))
        }
        _ui.update { it.copy(editing = null) }
    }

    fun removeRange(day: Int, kind: RangeKind, index: Int) = editDay(day) { d ->
        if (kind == RangeKind.SHIFT) d.copy(shifts = d.shifts.filterIndexed { i, _ -> i != index })
        else d.copy(breaks = d.breaks.filterIndexed { i, _ -> i != index })
    }

    /** Seçilen günün programını diğer çalışma günlerine kopyalar. */
    fun copyToWorkingDays(from: Int) = _ui.update { s ->
        val src = s.draft.day(from)
        s.copy(draft = s.draft.copy(days = s.draft.days + (1..7).filter { it != from && it !in s.draft.weeklyDaysOff }.associateWith { src }))
    }

    fun toggleDayOff(day: Int) = setWorking(day, day in _ui.value.draft.weeklyDaysOff)

    fun discard() = _ui.update { s -> s.copy(draft = s.original ?: s.draft) }
    fun consumeMessage() = _ui.update { it.copy(message = null) }

    /** 1) Sunucu önizlemesi; çakışma yoksa doğrudan uygular, varsa onay ister. */
    fun save() {
        val s = _ui.value
        val salonId = s.salonId ?: return
        if (!s.canSave) return
        _ui.update { it.copy(change = ChangeState.Checking) }
        viewModelScope.launch {
            runCatching { schedules.updateWeekly(salonId, s.draft, dryRun = true, acknowledge = false) }
                .onSuccess { r -> if (r.conflicts.isEmpty()) apply(acknowledge = false) else _ui.update { it.copy(change = ChangeState.AwaitingConfirmation(r.conflicts)) } }
                .onFailure { e -> _ui.update { it.copy(change = ChangeState.Idle, message = ErrorMessages.of(e)) } }
        }
    }

    fun confirmConflicts() = apply(acknowledge = true)
    fun cancelConflicts() = _ui.update { it.copy(change = ChangeState.Idle) }

    private fun apply(acknowledge: Boolean) {
        val s = _ui.value
        val salonId = s.salonId ?: return
        val shown = (s.change as? ChangeState.AwaitingConfirmation)?.conflicts.orEmpty()
        _ui.update { it.copy(change = ChangeState.Applying(shown)) }
        viewModelScope.launch {
            runCatching { schedules.updateWeekly(salonId, s.draft, dryRun = false, acknowledge = acknowledge) }
                .onSuccess { r: ChangeResult ->
                    if (!r.applied) {
                        _ui.update { it.copy(change = ChangeState.AwaitingConfirmation(r.conflicts)) }
                    } else {
                        _ui.update {
                            it.copy(
                                change = ChangeState.Idle,
                                original = s.draft,
                                message = UiText.Plain(
                                    if (r.conflicts.isEmpty()) "Programınız kaydedildi."
                                    else "Programınız kaydedildi. ${r.conflicts.size} randevu ilgilenilmesi gereken olarak işaretlendi.",
                                ),
                            )
                        }
                    }
                }
                .onFailure { e -> _ui.update { it.copy(change = ChangeState.Idle, message = ErrorMessages.of(e)) } }
        }
    }
}
