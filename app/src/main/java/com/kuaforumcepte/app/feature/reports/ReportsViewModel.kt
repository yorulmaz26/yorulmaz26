package com.kuaforumcepte.app.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.data.finance.Report
import com.kuaforumcepte.app.data.finance.ReportsRepository
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.domain.time.BusinessTime
import com.kuaforumcepte.domain.time.PeriodType
import com.kuaforumcepte.domain.time.ReportPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class ReportsUi(
    val period: ReportPeriod,
    val loading: Boolean = true,
    val report: Report? = null,
    val error: UiText? = null,
    val canGoNext: Boolean = false,
)

/**
 * Dönem raporları. Dönem sınırları İstanbul yerel saatine göredir (gün 00:00, hafta Pazartesi 00:00, ay takvim ayı);
 * sayaçlar fiziksel olarak sıfırlanmaz, her dönem ayrı anahtarla tutulur.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val session: SessionRepository,
    private val reports: ReportsRepository,
    private val clock: Clock,
) : ViewModel() {
    private fun today(): LocalDate = LocalDate.now(clock.withZone(BusinessTime.ZONE))

    private val _ui = MutableStateFlow(ReportsUi(ReportPeriod.of(PeriodType.DAY, today())))
    val ui: StateFlow<ReportsUi> = _ui.asStateFlow()
    private var salonId: String? = null
    private var job: Job? = null

    init {
        viewModelScope.launch {
            session.activeSalon.map { it?.membership?.salonId }.distinctUntilChanged().collect { id ->
                salonId = id
                load()
            }
        }
    }

    fun setType(type: PeriodType) = setPeriod(ReportPeriod.of(type, today()))
    fun previous() = setPeriod(_ui.value.period.previous())
    fun next() { if (_ui.value.canGoNext) setPeriod(_ui.value.period.next()) }

    private fun setPeriod(p: ReportPeriod) {
        _ui.update { it.copy(period = p) }
        load()
    }

    fun load() {
        val id = salonId ?: return
        val period = _ui.value.period
        job?.cancel()
        _ui.update { it.copy(loading = true, error = null, canGoNext = period.endDateExclusive <= today()) }
        job = viewModelScope.launch {
            runCatching { reports.load(id, period) }
                .onSuccess { r -> _ui.update { it.copy(loading = false, report = r) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = ErrorMessages.of(e)) } }
        }
    }
}
