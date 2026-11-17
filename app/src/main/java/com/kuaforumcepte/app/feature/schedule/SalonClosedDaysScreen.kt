package com.kuaforumcepte.app.feature.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.KcTheme
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.salon.SalonRepository
import com.kuaforumcepte.app.data.schedule.ScheduleRepository
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.app.feature.business.MenuRow
import com.kuaforumcepte.domain.model.MemberRole
import com.kuaforumcepte.domain.schedule.Weekdays
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SalonDaysUi(
    val loading: Boolean = true,
    val notAuthorized: Boolean = false,
    val salonId: String? = null,
    val original: Set<Int>? = null,
    val draft: Set<Int> = emptySet(),
    val change: ChangeState = ChangeState.Idle,
    val message: UiText? = null,
) {
    val dirty: Boolean get() = original != null && original != draft
}

@HiltViewModel
class SalonClosedDaysViewModel @Inject constructor(
    private val session: SessionRepository,
    private val salons: SalonRepository,
    private val schedules: ScheduleRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(SalonDaysUi())
    val ui: StateFlow<SalonDaysUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val a = session.activeSalon.first()
            if (a == null || a.membership.role != MemberRole.OWNER) { _ui.update { it.copy(loading = false, notAuthorized = true) }; return@launch }
            runCatching { salons.observe(a.membership.salonId).first() }
                .onSuccess { s -> val d = s?.weeklyClosedDays.orEmpty(); _ui.update { it.copy(loading = false, salonId = a.membership.salonId, original = d, draft = d) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, message = ErrorMessages.of(e)) } }
        }
    }

    fun toggle(day: Int) = _ui.update { s -> s.copy(draft = if (day in s.draft) s.draft - day else s.draft + day) }
    fun discard() = _ui.update { it.copy(draft = it.original.orEmpty()) }
    fun consumeMessage() = _ui.update { it.copy(message = null) }
    fun cancelConflicts() = _ui.update { it.copy(change = ChangeState.Idle) }

    fun save() {
        val s = _ui.value
        val salonId = s.salonId ?: return
        if (!s.dirty || s.change != ChangeState.Idle) return
        if (s.draft.size == 7) { _ui.update { it.copy(message = ErrorMessages.of(com.kuaforumcepte.app.core.data.AppException("ALL_DAYS_CLOSED"))) }; return }
        _ui.update { it.copy(change = ChangeState.Checking) }
        viewModelScope.launch {
            runCatching { schedules.updateSalonClosedDays(salonId, s.draft, dryRun = true, acknowledge = false) }
                .onSuccess { r -> if (r.conflicts.isEmpty()) apply(false) else _ui.update { it.copy(change = ChangeState.AwaitingConfirmation(r.conflicts)) } }
                .onFailure { e -> _ui.update { it.copy(change = ChangeState.Idle, message = ErrorMessages.of(e)) } }
        }
    }

    fun confirmConflicts() = apply(true)

    private fun apply(ack: Boolean) {
        val s = _ui.value
        val salonId = s.salonId ?: return
        _ui.update { it.copy(change = ChangeState.Applying((s.change as? ChangeState.AwaitingConfirmation)?.conflicts.orEmpty())) }
        viewModelScope.launch {
            runCatching { schedules.updateSalonClosedDays(salonId, s.draft, dryRun = false, acknowledge = ack) }
                .onSuccess { r ->
                    if (!r.applied) _ui.update { it.copy(change = ChangeState.AwaitingConfirmation(r.conflicts)) }
                    else _ui.update {
                        it.copy(
                            change = ChangeState.Idle, original = s.draft,
                            message = UiText.Plain(if (r.conflicts.isEmpty()) "Salon kapalı günleri kaydedildi." else "Kaydedildi; ${r.conflicts.size} randevu ilgilenilmesi gereken olarak işaretlendi."),
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(change = ChangeState.Idle, message = ErrorMessages.of(e)) } }
        }
    }
}

@Composable
fun SalonClosedDaysScreen(onBack: () -> Unit, onOpenDateClosures: () -> Unit, viewModel: SalonClosedDaysViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val msg = ui.message?.asString()
    LaunchedEffect(msg) { if (msg != null) { snackbar.showSnackbar(msg); viewModel.consumeMessage() } }
    BackHandler { viewModel.discard(); onBack() }

    Scaffold(
        topBar = { KcTopBar("Salon Kapalı Günleri", { viewModel.discard(); onBack() }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (ui.original != null) Surface(tonalElevation = 3.dp) {
                KcPrimaryButton("Kaydet", viewModel::save, Modifier.navigationBarsPadding().padding(Spacing.l), loading = ui.change != ChangeState.Idle, enabled = ui.dirty)
            }
        },
    ) { padding ->
        when {
            ui.loading -> SkeletonCardList(3, Modifier.padding(padding))
            ui.notAuthorized -> EmptyState("Bu bölüm yalnızca salon sahibine açıktır", modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text("Haftalık kapalı günler", style = MaterialTheme.typography.titleMedium)
                Text("Salonun her hafta kapalı olduğu günler. Bu günlerde hiçbir uzmana randevu alınamaz.", style = MaterialTheme.typography.bodySmall)
                (1..7).forEach { day ->
                    val closed = day in ui.draft
                    Surface(shape = MaterialTheme.shapes.medium, color = if (closed) KcTheme.colors.cancelContainer else MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                            Text(Weekdays.name(day), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(if (closed) "Kapalı" else "Açık", style = MaterialTheme.typography.labelMedium)
                            Switch(checked = closed, onCheckedChange = { viewModel.toggle(day) }, modifier = Modifier.padding(start = Spacing.s))
                        }
                    }
                }
                InfoBanner("Kaydetmeden önce mevcut randevulara etkisi kontrol edilir. Çakışan randevular iptal edilmez; işaretlenir ve bildirim gönderilir.")
                MenuRow(Icons.Outlined.EventBusy, "Özel kapalı tarihler", "Tatil, tadilat gibi belirli tarih veya aralıklar", onOpenDateClosures)
            }
        }
    }
    when (val c = ui.change) {
        is ChangeState.AwaitingConfirmation -> ConflictDialog(c.conflicts, false, viewModel::confirmConflicts, viewModel::cancelConflicts)
        is ChangeState.Applying -> if (c.conflicts.isNotEmpty()) ConflictDialog(c.conflicts, true, {}, {})
        else -> Unit
    }
}
