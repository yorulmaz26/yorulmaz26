package com.kuaforumcepte.app.feature.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.data.appointments.Appointment
import com.kuaforumcepte.app.data.appointments.AppointmentRepository
import com.kuaforumcepte.app.data.services.ServiceRepository
import com.kuaforumcepte.app.data.services.StaffService
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.app.data.staff.StaffMember
import com.kuaforumcepte.app.data.staff.StaffRepository
import com.kuaforumcepte.domain.booking.ServiceOption
import com.kuaforumcepte.domain.booking.ServiceSelection
import com.kuaforumcepte.domain.model.MemberStatus
import com.kuaforumcepte.domain.text.SearchKey
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import javax.inject.Inject

enum class StaffSort(val label: String) { NAME("Ada göre"), RATING("Puana göre") }

data class BookingSectionUi(
    val staff: List<StaffMember>? = null,
    val staffSort: StaffSort = StaffSort.NAME,
    val selectedStaff: StaffMember? = null,
    val services: List<StaffService>? = null,
    val serviceSearch: String = "",
    val selection: ServiceSelection = ServiceSelection.EMPTY,
    /** Müşterinin aktif randevuları; sınır (3) doluysa yeni randevu akışı kilitlenir. */
    val activeAppointments: List<Appointment> = emptyList(),
    val selfIsStaff: Boolean = false,
) {
    val limitReached: Boolean get() = activeAppointments.size >= MAX_ACTIVE_APPOINTMENTS

    companion object {
        /** Sunucudaki MAX_ACTIVE_APPOINTMENTS ile aynı. */
        const val MAX_ACTIVE_APPOINTMENTS = 3
    }
}

private data class Inputs(val salonId: String)

/** Salon detayındaki uzman → hizmet seçimi. Seçili hizmetler yalnızca seçili uzmana ait olabilir. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookingSectionViewModel @Inject constructor(
    private val staffRepo: StaffRepository,
    private val serviceRepo: ServiceRepository,
    private val appointments: AppointmentRepository,
    private val session: SessionRepository,
    private val clock: Clock,
) : ViewModel() {

    private val inputs = MutableStateFlow<Inputs?>(null)
    private val sort = MutableStateFlow(StaffSort.NAME)
    private val selectedStaffUid = MutableStateFlow<String?>(null)
    private val search = MutableStateFlow("")
    private val selection = MutableStateFlow(ServiceSelection.EMPTY)

    private val staffFlow = inputs.filterNotNull().flatMapLatest { i ->
        staffRepo.members(i.salonId).map { list -> list.filter { it.status == MemberStatus.ACTIVE } }.catch { emit(emptyList()) }
    }

    private val servicesFlow = combine(inputs.filterNotNull(), selectedStaffUid) { i, uid -> i to uid }.flatMapLatest { (i, uid) ->
        if (uid == null) flowOf(null) else serviceRepo.observeActiveOf(i.salonId, uid).map<List<StaffService>, List<StaffService>?> { it }.catch { emit(emptyList()) }
    }

    private val activeFlow = session.uidFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else appointments.observeCustomerActive(uid).catch { emit(emptyList()) }
    }

    val ui: StateFlow<BookingSectionUi> = combine(
        combine(staffFlow, sort, selectedStaffUid) { s, o, u -> Triple(s, o, u) },
        servicesFlow, search, selection, activeFlow,
    ) { (staff, order, uid), services, q, sel, active ->
        val sorted = when (order) {
            StaffSort.NAME -> staff.sortedWith(compareBy({ it.firstName }, { it.lastName }, { it.uid }))
            StaffSort.RATING -> staff.sortedWith(compareByDescending<StaffMember> { it.rating.average ?: -1.0 }.thenBy { it.fullName }.thenBy { it.uid })
        }
        val key = SearchKey.of(q)
        BookingSectionUi(
            staff = sorted,
            staffSort = order,
            selectedStaff = sorted.firstOrNull { it.uid == uid },
            services = services?.filter { key.isEmpty() || SearchKey.of(it.name).contains(key) || SearchKey.of(it.description).contains(key) },
            serviceSearch = q,
            selection = sel,
            activeAppointments = active.filter { it.isActive(clock.instant()) },
            selfIsStaff = uid != null && uid == session.currentUid,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookingSectionUi())

    fun bind(salonId: String) { if (inputs.value?.salonId != salonId) inputs.value = Inputs(salonId) }
    fun onSort(s: StaffSort) { sort.value = s }
    fun onSearch(q: String) { search.value = q.take(40) }

    fun selectStaff(m: StaffMember) {
        if (selectedStaffUid.value == m.uid) return
        selectedStaffUid.value = m.uid
        // Uzman değişince önceki uzmanın hizmetleri seçimden çıkar (karışık seçim engellenir).
        selection.value = ServiceSelection.EMPTY
        search.value = ""
    }

    fun toggle(s: StaffService) = selection.update {
        it.toggle(ServiceOption(s.id, s.staffUid, s.name, s.price, s.durationMinutes))
    }
}
