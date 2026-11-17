package com.kuaforumcepte.app.feature.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.theme.KcTheme
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.reviews.Review
import com.kuaforumcepte.app.data.reviews.ReviewReport
import com.kuaforumcepte.app.data.reviews.ReviewRepository
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.app.feature.reviews.StarRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModerationViewModel @Inject constructor(
    private val repo: ReviewRepository,
    private val session: SessionRepository,
) : ViewModel() {
    private val _isAdmin = MutableStateFlow<Boolean?>(null)
    val isAdmin: StateFlow<Boolean?> = _isAdmin.asStateFlow()
    val flagged: StateFlow<List<Review>?> = repo.observeFlagged().catch { emit(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val hidden: StateFlow<List<Review>?> = repo.observeHidden().catch { emit(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    init { viewModelScope.launch { _isAdmin.value = session.isPlatformAdmin(forceRefresh = true) } }

    fun reports(reviewId: String) = repo.observeReports(reviewId)
    fun consumeMessage() { _message.value = null }

    fun moderate(r: Review, action: String, note: String) {
        if (_busy.value != null) return
        _busy.value = r.id
        viewModelScope.launch {
            runCatching { repo.moderate(r.id, action, note) }
                .onSuccess {
                    _message.value = UiText.Plain(
                        when (action) {
                            "HIDE" -> "Yorum yayından kaldırıldı ve puanlardan çıkarıldı."
                            "RESTORE" -> "Yorum yeniden yayında; puanlar geri eklendi."
                            else -> "Şikâyet reddedildi; yorum yayında kalıyor."
                        },
                    )
                }
                .onFailure { _message.value = ErrorMessages.of(it) }
            _busy.value = null
        }
    }
}

/** Platform yöneticisi için yorum moderasyon kuyruğu. Yetki sunucuda ve Firestore kurallarında da denetlenir. */
@Composable
fun ModerationScreen(onBack: () -> Unit, viewModel: ModerationViewModel = hiltViewModel()) {
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val flagged by viewModel.flagged.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val msg = (message as? UiText.Plain)?.value
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(msg) { if (msg != null) { snackbar.showSnackbar(msg); viewModel.consumeMessage() } }

    Scaffold(topBar = { KcTopBar("Yorum Moderasyonu", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        when (isAdmin) {
            null -> SkeletonCardList(2, Modifier.padding(padding))
            false -> EmptyState("Bu bölüm yalnızca platform yöneticilerine açıktır", modifier = Modifier.padding(padding))
            true -> Column(Modifier.fillMaxSize().padding(padding)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(Spacing.screen)) {
                    listOf("İnceleme kuyruğu", "Kaldırılanlar").forEachIndexed { i, label ->
                        SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                    }
                }
                val list = if (tab == 0) flagged else hidden
                when {
                    list == null -> SkeletonCardList(2)
                    list.isEmpty() -> EmptyState(icon = Icons.Outlined.VerifiedUser, title = if (tab == 0) "İncelenecek yorum yok" else "Kaldırılmış yorum yok")
                    else -> LazyColumn(contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        items(list, key = { it.id }) { r -> ModerationCard(r, viewModel, isBusy = busy == r.id, hiddenTab = tab == 1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModerationCard(r: Review, vm: ModerationViewModel, isBusy: Boolean, hiddenTab: Boolean) {
    val reports by remember(r.id) { vm.reports(r.id) }.collectAsStateWithLifecycle(initialValue = emptyList<ReviewReport>())
    var note by remember { mutableStateOf("") }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("${r.author} · ${r.staffName} · ${r.reportCount} şikâyet", style = MaterialTheme.typography.titleSmall)
            Row { Text("Salon ", style = MaterialTheme.typography.labelMedium); StarRow(r.salonRating); Text("  Uzman ", style = MaterialTheme.typography.labelMedium); StarRow(r.staffRating) }
            Text(r.comment.ifBlank { "(yorum metni yok)" }, style = MaterialTheme.typography.bodyMedium)
            reports.forEach { rep ->
                Text("• ${rep.reason?.label ?: "-"}" + if (rep.note.isNotBlank()) ": ${rep.note}" else "", style = MaterialTheme.typography.bodySmall, color = KcTheme.colors.cancel)
            }
            KcTextField(note, { note = it }, "Karar notu (isteğe bağlı)", maxLength = 300)
            Row {
                if (hiddenTab) {
                    TextButton(onClick = { vm.moderate(r, "RESTORE", note) }, enabled = !isBusy) { Text("Geri yayınla") }
                } else {
                    TextButton(onClick = { vm.moderate(r, "DISMISS", note) }, enabled = !isBusy) { Text("Şikâyeti reddet") }
                    TextButton(onClick = { vm.moderate(r, "HIDE", note) }, enabled = !isBusy) { Text("Yayından kaldır", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
