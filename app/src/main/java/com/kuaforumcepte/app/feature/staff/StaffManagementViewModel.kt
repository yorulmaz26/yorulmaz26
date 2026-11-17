package com.kuaforumcepte.app.feature.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.data.session.ActiveSalon
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.app.data.staff.StaffInvitation
import com.kuaforumcepte.app.data.staff.StaffMember
import com.kuaforumcepte.app.data.staff.StaffRepository
import com.kuaforumcepte.domain.model.MemberRole
import com.kuaforumcepte.domain.phone.PhoneNumbers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StaffListState {
    data object Loading : StaffListState
    data object NotAuthorized : StaffListState
    data class Error(val message: UiText) : StaffListState
    data class Ready(
        val salonId: String,
        val salonCode: String,
        val members: List<StaffMember>,
        val invitations: List<StaffInvitation>,
    ) : StaffListState
}

data class InviteForm(
    val firstName: String = "",
    val lastName: String = "",
    val specialty: String = "",
    val phoneDigits: String = "",
    val errors: Map<String, String> = emptyMap(),
    val submitting: Boolean = false,
    /** Aynı form tekrar gönderilirse (ağ kopması) ikinci davet oluşmaz. */
    val key: String,
)

data class EditForm(val member: StaffMember, val firstName: String, val lastName: String, val specialty: String, val submitting: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StaffManagementViewModel @Inject constructor(
    session: SessionRepository,
    private val repo: StaffRepository,
    private val schedules: com.kuaforumcepte.app.data.schedule.ScheduleRepository,
) : ViewModel() {

    /** Sahip, uzmanların programını görüntüler (düzenleme yalnızca uzmanın kendisinde). */
    fun scheduleOf(memberUid: String): kotlinx.coroutines.flow.Flow<com.kuaforumcepte.domain.schedule.WeeklySchedule?> {
        val sid = (state.value as? StaffListState.Ready)?.salonId ?: return flowOf(null)
        return schedules.observeWeekly(sid, memberUid).map<com.kuaforumcepte.domain.schedule.WeeklySchedule, com.kuaforumcepte.domain.schedule.WeeklySchedule?> { it }.catch { emit(null) }
    }


    private val retry = MutableStateFlow(0)

    val state: StateFlow<StaffListState> = combine(session.activeSalon.distinctUntilChanged(), retry) { a, _ -> a }
        .flatMapLatest { active: ActiveSalon? ->
            when {
                active == null || active.membership.role != MemberRole.OWNER -> flowOf(StaffListState.NotAuthorized)
                else -> combine(repo.members(active.membership.salonId), repo.invitations(active.membership.salonId)) { members, invitations ->
                    StaffListState.Ready(active.membership.salonId, active.membership.salonCode, members, invitations) as StaffListState
                }.onStart { emit(StaffListState.Loading) }
                    .catch { emit(StaffListState.Error(ErrorMessages.of(it))) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StaffListState.Loading)

    private val _invite = MutableStateFlow<InviteForm?>(null)
    val invite: StateFlow<InviteForm?> = _invite.asStateFlow()
    private val _edit = MutableStateFlow<EditForm?>(null)
    val edit: StateFlow<EditForm?> = _edit.asStateFlow()
    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()
    private val _busyIds = MutableStateFlow<Set<String>>(emptySet())
    val busyIds: StateFlow<Set<String>> = _busyIds.asStateFlow()

    private val salonId: String? get() = (state.value as? StaffListState.Ready)?.salonId

    fun retry() = retry.update { it + 1 }
    fun consumeMessage() { _message.value = null }

    // ---- Davet ----
    fun openInvite() { _invite.value = InviteForm(key = repo.newKey()) }
    fun closeInvite() { if (_invite.value?.submitting != true) _invite.value = null }
    fun onInvite(transform: (InviteForm) -> InviteForm) = _invite.update { it?.let(transform)?.copy(errors = emptyMap()) }

    fun submitInvite() {
        val f = _invite.value ?: return
        val sid = salonId ?: return
        val errors = buildMap {
            if (!NAME.matches(f.firstName.trim())) put("firstName", "2–50 harf girin")
            if (!NAME.matches(f.lastName.trim())) put("lastName", "2–50 harf girin")
            if (f.specialty.trim().length !in 2..60) put("specialty", "Ör. Saç Kesimi, Renk, Manikür")
            if (PhoneNumbers.normalizeTr(f.phoneDigits, mobileOnly = true) == null) put("phone", "Geçerli bir cep telefonu girin")
        }
        if (errors.isNotEmpty()) { _invite.value = f.copy(errors = errors); return }
        _invite.value = f.copy(submitting = true)
        viewModelScope.launch {
            runCatching {
                repo.invite(sid, f.firstName, f.lastName, f.specialty, PhoneNumbers.normalizeTr(f.phoneDigits, true)!!, f.key)
            }.onSuccess {
                _invite.value = null
                _message.value = UiText.Plain("Davet oluşturuldu. ${f.firstName}, ${PhoneNumbers.formatForDisplay(PhoneNumbers.normalizeTr(f.phoneDigits, true)!!)} numarasıyla Kuaför / Uzman Girişi yaptığında daveti görecek.")
            }.onFailure { e ->
                _invite.value = f.copy(submitting = false)
                _message.value = ErrorMessages.of(e)
            }
        }
    }

    // ---- Üye işlemleri ----
    private fun runBusy(id: String, success: String?, block: suspend () -> Unit) {
        if (id in _busyIds.value) return
        _busyIds.update { it + id }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { if (success != null) _message.value = UiText.Plain(success) }
                .onFailure { _message.value = ErrorMessages.of(AppException.from(it)) }
            _busyIds.update { it - id }
        }
    }

    fun revoke(inv: StaffInvitation) {
        val sid = salonId ?: return
        runBusy(inv.id, "Davet iptal edildi.") { repo.revoke(sid, inv.id) }
    }

    fun setActive(member: StaffMember, active: Boolean) {
        val sid = salonId ?: return
        if (member.role == MemberRole.OWNER) return
        runBusy(member.uid, null) {
            val affected = repo.setStatus(sid, member.uid, active)
            _message.value = UiText.Plain(
                when {
                    active -> "${member.fullName} yeniden aktif."
                    affected > 0 -> "${member.fullName} pasife alındı. Bu uzmanın $affected gelecek randevusu var; randevu yönetiminden yeniden atayın veya iptal edin."
                    else -> "${member.fullName} pasife alındı ve artık randevu almıyor."
                },
            )
        }
    }

    fun openEdit(member: StaffMember) { _edit.value = EditForm(member, member.firstName, member.lastName, member.specialty) }
    fun closeEdit() { if (_edit.value?.submitting != true) _edit.value = null }
    fun onEdit(transform: (EditForm) -> EditForm) = _edit.update { it?.let(transform) }

    fun submitEdit() {
        val f = _edit.value ?: return
        val sid = salonId ?: return
        if (!NAME.matches(f.firstName.trim()) || !NAME.matches(f.lastName.trim()) || f.specialty.trim().length !in 2..60) {
            _message.value = UiText.Plain("Ad, soyad (2–50 harf) ve uzmanlık alanını (2–60 karakter) kontrol edin.")
            return
        }
        _edit.value = f.copy(submitting = true)
        viewModelScope.launch {
            runCatching { repo.updateMember(sid, f.member.uid, f.firstName, f.lastName, f.specialty) }
                .onSuccess { _edit.value = null; _message.value = UiText.Plain("Bilgiler güncellendi.") }
                .onFailure { _edit.value = f.copy(submitting = false); _message.value = ErrorMessages.of(it) }
        }
    }

    private companion object {
        val NAME = Regex("^[\\p{L}][\\p{L} .'-]{0,48}[\\p{L}.]$")
    }
}

