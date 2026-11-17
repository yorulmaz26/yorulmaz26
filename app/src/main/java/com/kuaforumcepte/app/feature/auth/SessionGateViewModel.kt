package com.kuaforumcepte.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.datastore.AppPreferences
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.domain.model.EntryRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface GateState {
    data object Loading : GateState
    data class Route(val target: SessionTarget) : GateState
    data class Error(val message: UiText, val signedOut: Boolean) : GateState
}

/** Girişten sonra (veya "Beni hatırla" ile açılışta) bootstrapSession çağırıp hedef ekranı belirler. */
@HiltViewModel
class SessionGateViewModel @Inject constructor(
    private val session: SessionRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow<GateState>(GateState.Loading)
    val state: StateFlow<GateState> = _state.asStateFlow()
    private var role: EntryRole = EntryRole.CUSTOMER

    fun start(role: EntryRole) {
        this.role = role
        if (_state.value is GateState.Route) return
        load()
    }

    fun retry() = load()

    private fun load() {
        _state.value = GateState.Loading
        viewModelScope.launch {
            try {
                preferences.setLastEntryRole(role)
                val snap = session.bootstrap()
                val target = SessionRouting.decide(role, snap, preferences.current().activeBusinessSalonId)
                if (target is SessionTarget.BusinessHome) session.selectActiveSalon(target.salonId)
                _state.value = GateState.Route(target)
            } catch (t: Throwable) {
                val e = AppException.from(t)
                _state.value = GateState.Error(ErrorMessages.of(e), signedOut = e.reason == AppException.NOT_SIGNED_IN)
            }
        }
    }

    fun signOut(onDone: () -> Unit) = viewModelScope.launch { session.signOut(); onDone() }
}
