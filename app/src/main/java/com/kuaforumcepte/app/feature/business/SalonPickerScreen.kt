package com.kuaforumcepte.app.feature.business

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.session.Membership
import com.kuaforumcepte.app.data.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SalonPickerViewModel @Inject constructor(private val session: SessionRepository) : ViewModel() {
    val salons: StateFlow<List<Membership>?> = session.memberships
        .map { list -> list.filter { it.isActive }.sortedBy { it.salonName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(salonId: String, onDone: () -> Unit) = viewModelScope.launch {
        session.selectActiveSalon(salonId)
        onDone()
    }
}

/** Aynı kişi birden çok salonda çalışıyorsa aktif salon seçimi; veriler salonId ile izole edilir. */
@Composable
fun SalonPickerScreen(onSelected: () -> Unit, onBack: (() -> Unit)?, viewModel: SalonPickerViewModel = hiltViewModel()) {
    val salons by viewModel.salons.collectAsStateWithLifecycle()
    Scaffold(topBar = { KcTopBar("Salon Seçin", onBack) }) { padding ->
        when (val list = salons) {
            null -> SkeletonCardList(3, Modifier.padding(padding))
            emptyList<Membership>() -> EmptyState(
                title = "Aktif salon üyeliği bulunamadı",
                message = "Salon sahibinizden davet isteyin veya kendi salonunuzu kaydedin.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item { Text("Hangi salonda çalışmak istiyorsunuz? İstediğiniz zaman üst menüden değiştirebilirsiniz.") }
                items(list, key = { it.salonId }) { m ->
                    MenuRow(Icons.Outlined.Storefront, m.salonName, "${m.role.label} · Kod ${m.salonCode}") { viewModel.select(m.salonId, onSelected) }
                }
            }
        }
    }
}
