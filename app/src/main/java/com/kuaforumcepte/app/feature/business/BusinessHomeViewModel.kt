package com.kuaforumcepte.app.feature.business

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.data.session.ActiveSalon
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.domain.auth.Permission
import com.kuaforumcepte.domain.auth.PermissionPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface BusinessHomeState {
    data object Loading : BusinessHomeState
    /** Üyelik pasife alındı / ayrıldı → artık bu salonda yetki yok. */
    data object NoActiveSalon : BusinessHomeState
    data class Ready(val active: ActiveSalon, val permissions: Set<Permission>) : BusinessHomeState
}

@HiltViewModel
class BusinessHomeViewModel @Inject constructor(session: SessionRepository) : ViewModel() {
    val state: StateFlow<BusinessHomeState> = session.activeSalon
        .map { a ->
            if (a == null) BusinessHomeState.NoActiveSalon
            else BusinessHomeState.Ready(a, PermissionPolicy.permissionsOf(a.membership.role))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BusinessHomeState.Loading)
}
