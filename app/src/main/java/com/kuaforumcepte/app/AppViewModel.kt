package com.kuaforumcepte.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.datastore.AppPreferences
import com.kuaforumcepte.app.core.datastore.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Uygulama geneli durum (tema). */
@HiltViewModel
class AppViewModel @Inject constructor(preferences: AppPreferences) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = preferences.settings
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
}
