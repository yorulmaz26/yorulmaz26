package com.kuaforumcepte.app.feature.salon

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kuaforumcepte.app.core.common.UiState
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.datastore.AppPreferences
import com.kuaforumcepte.app.core.navigation.SalonDetailRoute
import com.kuaforumcepte.app.data.salon.SalonProfile
import com.kuaforumcepte.app.data.salon.SalonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SalonDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val salons: SalonRepository,
    private val preferences: AppPreferences,
    private val favorites: com.kuaforumcepte.app.data.favorites.FavoritesRepository,
    private val session: com.kuaforumcepte.app.data.session.SessionRepository,
) : ViewModel() {
    private val salonId = savedState.toRoute<SalonDetailRoute>().salonId
    private val retry = MutableStateFlow(0)

    val state: StateFlow<UiState<SalonProfile>> = retry.flatMapLatest {
        salons.observe(salonId)
            .map { s -> if (s == null || !s.active) UiState.Empty("Salon bulunamadı veya şu anda aktif değil.") else UiState.Success(s) }
            .onStart { emit(UiState.Loading) }
            .catch { emit(UiState.Error(ErrorMessages.of(it))) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val isSelected: StateFlow<Boolean> = preferences.settings.map { it.selectedCustomerSalonId == salonId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun retry() { retry.value++ }

    val isFavorite: StateFlow<Boolean> = session.uidFlow.flatMapLatest { uid ->
        if (uid == null) kotlinx.coroutines.flow.flowOf(false) else favorites.isFavorite(uid, salonId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** @return kullanıcıya gösterilecek kısa mesaj */
    fun toggleFavorite(onDone: (String) -> Unit) {
        val uid = session.currentUid ?: return
        val salon = (state.value as? UiState.Success)?.data ?: return
        val wasFavorite = isFavorite.value
        viewModelScope.launch {
            runCatching { if (wasFavorite) favorites.remove(uid, salonId) else favorites.add(uid, salon) }
                .onSuccess { onDone(if (wasFavorite) "Favorilerden çıkarıldı" else "Favorilere eklendi") }
                .onFailure { onDone(ErrorMessages.text(it)) }
        }
    }

    /** Müşterinin "Salon Bilgileri" sekmesi için tek kaynaklı salon seçimi. */
    fun select() = viewModelScope.launch { preferences.setSelectedCustomerSalon(salonId) }
}
