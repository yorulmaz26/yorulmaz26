package com.kuaforumcepte.app.feature.salon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.designsystem.component.SkeletonBlock
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.schedule.ScheduleRepository
import com.kuaforumcepte.app.data.staff.StaffRepository
import com.kuaforumcepte.app.feature.schedule.trShortDate
import com.kuaforumcepte.domain.model.MemberStatus
import com.kuaforumcepte.domain.schedule.DateClosure
import com.kuaforumcepte.domain.schedule.ScheduleSummary
import com.kuaforumcepte.domain.schedule.Weekdays
import com.kuaforumcepte.domain.schedule.WeeklySchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StaffHours(val name: String, val title: String, val lines: List<String>)
data class SalonHours(val staff: List<StaffHours>, val closures: List<DateClosure>)

/** Salondaki aktif uzmanların haftalık programları + salonun özel kapalı tarihleri (kamuya açık veriler). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SalonHoursViewModel @Inject constructor(
    private val staff: StaffRepository,
    private val schedules: ScheduleRepository,
) : ViewModel() {
    private data class Key(val salonId: String, val closedDays: Set<Int>)
    private val key = MutableStateFlow<Key?>(null)

    val hours: StateFlow<SalonHours?> = key.filterNotNull().flatMapLatest { k ->
        staff.members(k.salonId).flatMapLatest { members ->
            val active = members.filter { it.status == MemberStatus.ACTIVE }
            val weekly = if (active.isEmpty()) flowOf(emptyList<WeeklySchedule>())
            else combine(active.map { schedules.observeWeekly(k.salonId, it.uid) }) { it.toList() }
            combine(weekly, schedules.observeSalonClosures(k.salonId)) { weeks, closures ->
                SalonHours(
                    staff = active.zip(weeks).map { (m, w) -> StaffHours(m.fullName, m.role.label, ScheduleSummary.lines(w, k.closedDays)) },
                    closures = closures,
                )
            }
        }
    }.catch { emit(SalonHours(emptyList(), emptyList())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(salonId: String, closedDays: Set<Int>) { key.value = Key(salonId, closedDays) }
}

@Composable
fun SalonHoursSection(salonId: String, closedDays: Set<Int>, viewModel: SalonHoursViewModel = hiltViewModel(key = "hours_$salonId")) {
    LaunchedEffect(salonId, closedDays) { viewModel.load(salonId, closedDays) }
    val hours by viewModel.hours.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("Çalışma saatleri", style = MaterialTheme.typography.titleMedium)
        if (closedDays.isNotEmpty()) {
            Text("Salon her hafta kapalı: ${closedDays.sorted().joinToString(", ") { Weekdays.name(it) }}", style = MaterialTheme.typography.bodyMedium)
        }
        val h = hours
        if (h == null) {
            SkeletonBlock(Modifier.fillMaxWidth(), 64.dp)
        } else {
            h.staff.forEach { s ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.m)) {
                        Text("${s.name} · ${s.title}", style = MaterialTheme.typography.titleSmall)
                        s.lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (h.closures.isNotEmpty()) {
                Text("Özel kapalı tarihler", style = MaterialTheme.typography.titleSmall)
                h.closures.forEach { c ->
                    val range = if (c.startDate == c.endDate) c.startDate.format(trShortDate) else "${c.startDate.format(trShortDate)} – ${c.endDate.format(trShortDate)}"
                    Text("• $range" + if (c.note.isNotBlank()) " (${c.note})" else "", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
