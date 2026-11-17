package com.kuaforumcepte.app.feature.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.reviews.Review
import com.kuaforumcepte.app.data.reviews.ReviewRepository
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
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReviewApprovalViewModel @Inject constructor(session: SessionRepository, private val repo: ReviewRepository) : ViewModel() {
    val state: StateFlow<UiState<List<Review>>> = session.activeSalon.flatMapLatest { a ->
        if (a == null) flowOf(UiState.Empty())
        else repo.observePendingApproval(a.membership.salonId)
            .map<List<Review>, UiState<List<Review>>> { if (it.isEmpty()) UiState.Empty() else UiState.Success(it) }
            .onStart { emit(UiState.Loading) }
            .catch { emit(UiState.Error(ErrorMessages.of(it))) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    fun decide(r: Review, approve: Boolean, note: String?) {
        if (_busy.value != null) return
        _busy.value = r.id
        viewModelScope.launch {
            runCatching { repo.decide(r.id, approve, note) }
                .onSuccess { _message.value = if (approve) "Değerlendirme yayınlandı ve puanlara eklendi." else "Değerlendirme yayınlanmadı; müşteriye gerekçe iletildi." }
                .onFailure { _message.value = ErrorMessages.text(it) }
            _busy.value = null
        }
    }
}

private val whenFmt = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", BusinessTime.LOCALE).withZone(BusinessTime.ZONE)

/** Salon sahibi: müşteri değerlendirmeleri onaylanana kadar yayınlanmaz ve puanlara girmez. */
@Composable
fun ReviewApprovalScreen(onBack: () -> Unit, viewModel: ReviewApprovalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var rejecting by remember { mutableStateOf<Review?>(null) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }

    Scaffold(topBar = { KcTopBar("Değerlendirme Onayı", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        when (val s = state) {
            UiState.Loading -> SkeletonCardList(3, Modifier.padding(padding))
            is UiState.Error -> ErrorState(s.message.asString(), Modifier.padding(padding))
            is UiState.Empty -> EmptyState(
                "Onay bekleyen değerlendirme yok", "Müşteriler hizmetten sonra puan verdiğinde burada görünür ve onayınızla yayınlanır.",
                Modifier.padding(padding), icon = Icons.Outlined.RateReview,
            )
            is UiState.Success -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                item {
                    InfoBanner("Onayladığınız değerlendirme salon sayfanızda yayınlanır ve puanınıza eklenir. Yayınlamamak için gerekçe yazmanız gerekir; müşteriye iletilir.")
                }
                items(s.data, key = { it.id }) { r ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.author, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                r.createdAt?.let { Text(whenFmt.format(it), style = MaterialTheme.typography.labelSmall) }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("Salon ", style = MaterialTheme.typography.labelMedium); StarRow(r.salonRating) }
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("${r.staffName} ", style = MaterialTheme.typography.labelMedium); StarRow(r.staffRating) }
                            r.serviceRating?.let { v -> Row(verticalAlignment = Alignment.CenterVertically) { Text("Hizmet ", style = MaterialTheme.typography.labelMedium); StarRow(v) } }
                            if (r.comment.isNotBlank()) Text(r.comment, style = MaterialTheme.typography.bodyMedium)
                            if (r.serviceNames.isNotEmpty()) Text(r.serviceNames.joinToString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(top = Spacing.s)) {
                                OutlinedButton(onClick = { rejecting = r }, enabled = busy == null, modifier = Modifier.weight(1f)) { Text("Yayınlama") }
                                Button(onClick = { viewModel.decide(r, true, null) }, enabled = busy == null, modifier = Modifier.weight(1f)) {
                                    Text(if (busy == r.id) "İşleniyor…" else "Onayla ve Yayınla")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    rejecting?.let { r ->
        var note by remember(r.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text("Değerlendirme yayınlanmasın mı?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text("Gerekçeniz müşteriye iletilir. Yorum puanınıza eklenmez.", style = MaterialTheme.typography.bodySmall)
                    KcTextField(note, { note = it }, "Gerekçe (ör. hakaret, kişisel bilgi)", singleLine = false, minLines = 2, maxLength = 300)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.decide(r, false, note); rejecting = null }, enabled = note.trim().length >= 3) {
                    Text("Yayınlama", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { rejecting = null }) { Text("Vazgeç") } },
        )
    }
}
