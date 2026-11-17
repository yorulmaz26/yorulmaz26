package com.kuaforumcepte.app.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.datastore.AppPreferences
import com.kuaforumcepte.app.core.datastore.AppSettings
import com.kuaforumcepte.app.core.datastore.ThemeMode
import com.kuaforumcepte.app.data.session.ActiveSalon
import com.kuaforumcepte.app.data.session.Membership
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.app.data.staff.StaffRepository
import com.kuaforumcepte.domain.model.MemberRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUi(
    val name: String = "",
    val phone: String = "",
    val settings: AppSettings = AppSettings(),
    val memberships: List<Membership> = emptyList(),
    val active: ActiveSalon? = null,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val session: SessionRepository,
    private val preferences: AppPreferences,
    private val staff: StaffRepository,
) : ViewModel() {

    val ui: StateFlow<AccountUi> = combine(
        preferences.settings, session.memberships, session.activeSalon, session.snapshot,
    ) { settings, memberships, active, snap ->
        AccountUi(
            name = listOfNotNull(snap?.firstName, snap?.lastName).joinToString(" "),
            phone = session.currentPhone.orEmpty(),
            settings = settings,
            memberships = memberships,
            active = active,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUi())

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()
    private val _leaving = MutableStateFlow(false)
    val leaving: StateFlow<Boolean> = _leaving.asStateFlow()
    private val _signedOut = Channel<Unit>(Channel.BUFFERED)
    val signedOut = _signedOut.receiveAsFlow()

    init {
        // Ad/soyad bilgisi yoksa (ör. Splash → doğrudan panel) bir kez yüklenir.
        if (session.snapshot.value == null) viewModelScope.launch { runCatching { session.bootstrap() } }
    }

    val canLeaveActiveSalon: Boolean get() = ui.value.active?.membership?.role == MemberRole.STAFF

    fun setRememberMe(v: Boolean) = viewModelScope.launch { preferences.setRememberMe(v) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { preferences.setThemeMode(mode) }
    fun consumeMessage() { _message.value = null }

    fun leaveActiveSalon() {
        val salon = ui.value.active?.membership ?: return
        if (salon.role != MemberRole.STAFF || _leaving.value) return
        _leaving.value = true
        viewModelScope.launch {
            runCatching { staff.leave(salon.salonId) }
                .onSuccess { _message.value = UiText.Plain("${salon.salonName} salonundan ayrıldınız.") }
                .onFailure { _message.value = ErrorMessages.of(it) }
            _leaving.value = false
        }
    }

    fun signOut() = viewModelScope.launch {
        session.signOut()
        _signedOut.send(Unit)
    }
}
