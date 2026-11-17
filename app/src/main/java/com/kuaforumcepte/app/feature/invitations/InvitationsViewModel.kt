package com.kuaforumcepte.app.feature.invitations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.data.session.PendingInvitation
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.app.data.staff.StaffRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InvitationsUi(
    val loading: Boolean = true,
    val invitations: List<PendingInvitation> = emptyList(),
    val hasActiveMembership: Boolean = false,
    val profileExists: Boolean = false,
    val busyId: String? = null,
    val kvkk: Boolean = false,
    val terms: Boolean = false,
    val error: UiText? = null,
)

@HiltViewModel
class InvitationsViewModel @Inject constructor(
    private val session: SessionRepository,
    private val staff: StaffRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(InvitationsUi())
    val ui: StateFlow<InvitationsUi> = _ui.asStateFlow()
    private val _joined = Channel<String>(Channel.BUFFERED)
    /** Katılınan salonun kimliği. */
    val joined = _joined.receiveAsFlow()

    init { refresh() }

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { session.bootstrap() }
                .onSuccess { s ->
                    _ui.update {
                        it.copy(
                            loading = false,
                            invitations = s.pendingInvitations,
                            hasActiveMembership = s.activeMemberships.isNotEmpty(),
                            profileExists = s.profileExists,
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = ErrorMessages.of(e)) } }
        }
    }

    fun onKvkk(v: Boolean) = _ui.update { it.copy(kvkk = v) }
    fun onTerms(v: Boolean) = _ui.update { it.copy(terms = v) }
    fun dismissError() = _ui.update { it.copy(error = null) }

    fun accept(inv: PendingInvitation) {
        val s = _ui.value
        if (s.busyId != null) return
        if (!s.profileExists && (!s.kvkk || !s.terms)) {
            _ui.update { it.copy(error = UiText.Plain("Daveti kabul etmeden önce aydınlatma metnini ve kullanım koşullarını onaylayın.")) }
            return
        }
        _ui.update { it.copy(busyId = inv.inviteId) }
        viewModelScope.launch {
            runCatching { staff.accept(inv.salonId, inv.inviteId) }
                .onSuccess {
                    session.selectActiveSalon(inv.salonId)
                    _ui.update { it.copy(busyId = null) }
                    _joined.send(inv.salonId)
                }
                .onFailure { e -> _ui.update { it.copy(busyId = null, error = ErrorMessages.of(e)) }; refresh() }
        }
    }

    fun decline(inv: PendingInvitation) {
        if (_ui.value.busyId != null) return
        _ui.update { it.copy(busyId = inv.inviteId) }
        viewModelScope.launch {
            runCatching { staff.decline(inv.salonId, inv.inviteId) }
                .onFailure { e -> _ui.update { it.copy(error = ErrorMessages.of(e)) } }
            _ui.update { it.copy(busyId = null) }
            refresh()
        }
    }
}
