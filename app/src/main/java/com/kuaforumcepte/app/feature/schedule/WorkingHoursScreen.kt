package com.kuaforumcepte.app.feature.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.ConfirmDialog
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.KcTheme
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.domain.schedule.TimeRange
import com.kuaforumcepte.domain.schedule.Weekdays

/** Ortak iskelet: yükleme/hata/kaydet çubuğu/çakışma ve çıkış onayı. */
@Composable
private fun ScheduleScaffold(title: String, ui: WorkingHoursUi, vm: WorkingHoursViewModel, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val msg = ui.message?.asString()
    var confirmDiscard by remember { mutableStateOf(false) }
    LaunchedEffect(msg) { if (msg != null) { snackbar.showSnackbar(msg); vm.consumeMessage() } }
    val goBack: () -> Unit = { if (ui.dirty) { confirmDiscard = true } else { onBack() } }
    BackHandler(onBack = goBack)

    Scaffold(
        topBar = { KcTopBar(title, goBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (ui.original != null) Surface(tonalElevation = 3.dp) {
                KcPrimaryButton(
                    if (ui.errors.isEmpty()) "Kaydet" else "Hataları düzeltin", vm::save,
                    Modifier.navigationBarsPadding().padding(Spacing.l),
                    loading = ui.change != ChangeState.Idle, enabled = ui.canSave,
                )
            }
        },
    ) { padding ->
        when {
            ui.loading -> SkeletonCardList(4, Modifier.padding(padding))
            ui.noSalon -> EmptyState("Aktif salon üyeliği yok", modifier = Modifier.padding(padding))
            ui.loadError != null -> ErrorState(ui.loadError.asString(), Modifier.padding(padding), onRetry = vm::load)
            else -> content(padding)
        }
    }

    when (val c = ui.change) {
        is ChangeState.AwaitingConfirmation -> ConflictDialog(c.conflicts, applying = false, onConfirm = vm::confirmConflicts, onDismiss = vm::cancelConflicts)
        is ChangeState.Applying -> if (c.conflicts.isNotEmpty()) ConflictDialog(c.conflicts, applying = true, onConfirm = {}, onDismiss = {})
        else -> Unit
    }
    if (confirmDiscard) {
        ConfirmDialog(
            "Kaydedilmemiş değişiklikler", "Programdaki değişiklikler kaydedilmedi. Çıkarsanız kaybolacak.", "Kaydetmeden Çık",
            onConfirm = { confirmDiscard = false; vm.discard(); onBack() }, onDismiss = { confirmDiscard = false }, destructive = true,
        )
    }
}

@Composable
fun WorkingHoursScreen(onBack: () -> Unit, viewModel: WorkingHoursViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    ScheduleScaffold("Çalışma Saatleri ve Molalar", ui, viewModel, onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item {
                InfoBanner("Bir günde birden fazla vardiya ve mola ekleyebilirsiniz. Müşteriler yalnızca vardiyaların içinde, molalar dışında kalan ve hizmet süresinin tamamının sığdığı saatleri seçebilir.")
            }
            items((1..7).toList(), key = { it }) { day ->
                val working = day !in ui.draft.weeklyDaysOff
                val d = ui.draft.day(day)
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Weekdays.name(day), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (working) "Çalışıyor · ${d.workingMinutes / 60} sa ${d.workingMinutes % 60} dk" else "Haftalık izin",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = working, onCheckedChange = { viewModel.setWorking(day, it) })
                        }
                        if (working) {
                            RangeChips("Vardiyalar", d.shifts, onEdit = { i -> viewModel.startEdit(RangeEdit(day, RangeKind.SHIFT, i, d.shifts[i])) },
                                onRemove = { i -> viewModel.removeRange(day, RangeKind.SHIFT, i) },
                                onAdd = { viewModel.startEdit(RangeEdit(day, RangeKind.SHIFT, null, null)) })
                            RangeChips("Molalar", d.breaks, onEdit = { i -> viewModel.startEdit(RangeEdit(day, RangeKind.BREAK, i, d.breaks[i])) },
                                onRemove = { i -> viewModel.removeRange(day, RangeKind.BREAK, i) },
                                onAdd = { viewModel.startEdit(RangeEdit(day, RangeKind.BREAK, null, null)) })
                            ui.errors[day]?.let { Text(it.message, color = KcTheme.colors.cancel, style = MaterialTheme.typography.bodySmall) }
                            TextButton(onClick = { viewModel.copyToWorkingDays(day) }) {
                                Icon(Icons.Outlined.ContentCopy, null)
                                Text("  Diğer çalışma günlerine kopyala")
                            }
                        }
                    }
                }
            }
        }
    }
    ui.editing?.let { e ->
        TimeRangeDialog(
            title = "${Weekdays.name(e.day)} · ${if (e.kind == RangeKind.SHIFT) "Vardiya" else "Mola"}",
            initial = e.initial,
            onSave = viewModel::saveRange,
            onDismiss = viewModel::cancelEdit,
        )
    }
}

@Composable
private fun RangeChips(label: String, ranges: List<TimeRange>, onEdit: (Int) -> Unit, onRemove: (Int) -> Unit, onAdd: () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    ranges.forEachIndexed { i, r ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { onEdit(i) }) { Icon(Icons.Outlined.Edit, contentDescription = "$label ${r.label} düzenle") }
            IconButton(onClick = { onRemove(i) }) { Icon(Icons.Outlined.Close, contentDescription = "$label ${r.label} kaldır", tint = MaterialTheme.colorScheme.error) }
        }
    }
    AssistChip(onClick = onAdd, label = { Text("$label: ekle") }, leadingIcon = { Icon(Icons.Outlined.Add, null) })
}

/** Haftalık izin günleri: aynı program verisi, yalnızca günler. */
@Composable
fun WeeklyDaysOffScreen(onBack: () -> Unit, viewModel: WorkingHoursViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    ScheduleScaffold("Haftalık İzin Günleri", ui, viewModel, onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("İzinli olduğunuz günleri seçin. Bu günlerde size randevu alınamaz.", style = MaterialTheme.typography.bodyMedium)
            (1..7).forEach { day ->
                val off = day in ui.draft.weeklyDaysOff
                Surface(shape = MaterialTheme.shapes.medium, color = if (off) KcTheme.colors.pendingContainer else MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        Text(Weekdays.name(day), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(if (off) "İzinli" else "Çalışıyor", style = MaterialTheme.typography.labelMedium)
                        Switch(checked = off, onCheckedChange = { viewModel.toggleDayOff(day) }, modifier = Modifier.padding(start = Spacing.s))
                    }
                }
            }
            InfoBanner("Çalışma saatlerinizi gün gün düzenlemek için “Çalışma Saatleri ve Molalar” bölümünü kullanın.")
        }
    }
}
