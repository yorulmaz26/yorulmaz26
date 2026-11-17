package com.kuaforumcepte.app.feature.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.navigation.SlotPickerRoute
import com.kuaforumcepte.app.data.booking.Availability
import com.kuaforumcepte.app.data.booking.BookingRepository
import com.kuaforumcepte.app.data.booking.Slot
import com.kuaforumcepte.domain.time.BusinessTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class SlotPickerUi(
    val windowStart: LocalDate,
    val loading: Boolean = true,
    val availability: Availability? = null,
    val selectedDate: LocalDate? = null,
    val error: UiText? = null,
) {
    val slotsOfSelected: List<Slot> get() = availability?.days?.firstOrNull { it.date == selectedDate }?.slots.orEmpty()
}

/** Pencereli (14 gün) müsaitlik yükleyici; müşteri rezervasyonu ve salonun yeniden planlaması paylaşır. */
abstract class BaseSlotViewModel(savedState: SavedStateHandle, clock: Clock) : ViewModel() {
    protected abstract suspend fun fetch(from: LocalDate, days: Int): Availability
    val today: LocalDate = LocalDate.now(clock.withZone(BusinessTime.ZONE))
    private val _ui = MutableStateFlow(SlotPickerUi(windowStart = savedState.get<String>("window")?.let(LocalDate::parse) ?: today))
    val ui: StateFlow<SlotPickerUi> = _ui.asStateFlow()
    private val saved = savedState
    private var job: Job? = null

    protected fun start() = load()

    val horizonEnd: LocalDate get() = today.plusDays((_ui.value.availability?.horizonDays ?: 60).toLong())
    val canPrev: Boolean get() = _ui.value.windowStart.isAfter(today)
    val canNext: Boolean get() = _ui.value.windowStart.plusDays(WINDOW.toLong()) <= horizonEnd

    fun load() {
        job?.cancel()
        val start = _ui.value.windowStart
        _ui.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            runCatching { fetch(start, WINDOW) }
                .onSuccess { a ->
                    _ui.update { s ->
                        val keep = s.selectedDate?.takeIf { d -> a.days.any { it.date == d } }
                        s.copy(loading = false, availability = a, selectedDate = keep ?: a.days.firstOrNull { it.slots.isNotEmpty() }?.date ?: a.days.firstOrNull()?.date)
                    }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = ErrorMessages.of(e)) } }
        }
    }

    fun selectDate(d: LocalDate) = _ui.update { it.copy(selectedDate = d) }

    fun shiftWindow(forward: Boolean) {
        val next = _ui.value.windowStart.plusDays(if (forward) WINDOW.toLong() else -WINDOW.toLong()).coerceAtLeast(today)
        saved["window"] = next.toString()
        _ui.update { it.copy(windowStart = next, selectedDate = null) }
        load()
    }

    private fun LocalDate.coerceAtLeast(min: LocalDate) = if (isBefore(min)) min else this

    companion object {
        const val WINDOW = 14
    }
}

@HiltViewModel
class SlotPickerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val booking: BookingRepository,
    clock: Clock,
) : BaseSlotViewModel(savedState, clock) {
    val route = savedState.toRoute<SlotPickerRoute>()
    override suspend fun fetch(from: LocalDate, days: Int) = booking.availability(route.salonId, route.staffUid, route.serviceIds, from, days)
    init { start() }
}
