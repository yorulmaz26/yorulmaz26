package com.kuaforumcepte.app.feature.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiState
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.datastore.AppPreferences
import com.kuaforumcepte.app.data.salon.SalonProfile
import com.kuaforumcepte.app.data.salon.SalonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Müşterinin seçili salonu (DataStore'da tek kaynak; uygulama yeniden açılınca korunur). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomerHomeViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val salons: SalonRepository,
) : ViewModel() {

    /** null = seçili salon yok. */
    val selectedSalon: StateFlow<UiState<SalonProfile>?> = preferences.settings
        .map { it.selectedCustomerSalonId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else salons.observe(id)
                .map<SalonProfile?, UiState<SalonProfile>?> { s ->
                    if (s == null || !s.active) UiState.Empty("Seçtiğiniz salon artık aktif değil. Lütfen başka bir salon seçin.") else UiState.Success(s)
                }
                .onStart { emit(UiState.Loading) }
                .catch { emit(UiState.Error(ErrorMessages.of(it))) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun clearSelection() = viewModelScope.launch { preferences.setSelectedCustomerSalon(null) }
}
