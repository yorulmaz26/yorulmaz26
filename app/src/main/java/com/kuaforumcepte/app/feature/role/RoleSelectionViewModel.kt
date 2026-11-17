package com.kuaforumcepte.app.feature.role

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.datastore.AppPreferences
import com.kuaforumcepte.domain.model.EntryRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoleSelectionViewModel @Inject constructor(private val preferences: AppPreferences) : ViewModel() {

    /** Tek seferlik navigasyon olayı (ekran döndürmede tekrar tetiklenmez). */
    private val _navigate = Channel<EntryRole>(Channel.BUFFERED)
    val navigate = _navigate.receiveAsFlow()

    fun choose(role: EntryRole) {
        viewModelScope.launch {
            preferences.setLastEntryRole(role)
            _navigate.send(role)
        }
    }
}
