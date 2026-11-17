package com.kuaforumcepte.app.feature.waitlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiState
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.booking.BookingRepository
import com.kuaforumcepte.app.data.booking.WaitlistEntry
import com.kuaforumcepte.app.data.favorites.FavoritesRepository
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.domain.time.BusinessTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WaitlistViewModel @Inject constructor(
    session: SessionRepository,
    favorites: FavoritesRepository,
    private val booking: BookingRepository,
    clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock.withZone(BusinessTime.ZONE))
    val state: StateFlow<UiState<List<WaitlistEntry>>> = session.uidFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(UiState.Empty())
        else favorites.observeWaitlist(uid, today)
            .map<List<WaitlistEntry>, UiState<List<WaitlistEntry>>> { if (it.isEmpty()) UiState.Empty() else UiState.Success(it) }
            .onStart { emit(UiState.Loading) }
            .catch { emit(UiState.Error(ErrorMessages.of(it))) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    fun leave(e: WaitlistEntry) = viewModelScope.launch {
        runCatching { booking.leaveWaitlist(e.id) }
            .onSuccess { _message.value = "Bekleme listesinden çıkıldı." }
            .onFailure { _message.value = ErrorMessages.text(it) }
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("d MMMM EEEE", BusinessTime.LOCALE)

@Composable
fun WaitlistScreen(onBack: () -> Unit, onOpenSalon: (String) -> Unit, viewModel: WaitlistViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }
    Scaffold(topBar = { KcTopBar("Bekleme Listem", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        when (val s = state) {
            UiState.Loading -> SkeletonCardList(3, Modifier.padding(padding))
            is UiState.Error -> ErrorState(s.message.asString(), Modifier.padding(padding))
            is UiState.Empty -> EmptyState(
                "Bekleme listeniz boş", "Saat seçerken dolu bir günde “Bekleme listesine katıl” derseniz, o gün yer açıldığında bildirim alırsınız.",
                Modifier.padding(padding), icon = Icons.Outlined.HourglassTop,
            )
            is UiState.Success -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                item { InfoBanner("Yer açıldığında bildirim gelir; saat sizin için ayrılmaz, ilk alan alır. Randevu aldığınızda kayıt kendiliğinden silinir.") }
                items(s.data, key = { it.id }) { e ->
                    Surface(onClick = { onOpenSalon(e.salonId) }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("${e.salonName} · ${e.staffName}") },
                            supportingContent = { Text(e.date.format(dateFmt)) },
                            trailingContent = { TextButton(onClick = { viewModel.leave(e) }) { Text("Çık") } },
                        )
                    }
                }
            }
        }
    }
}
