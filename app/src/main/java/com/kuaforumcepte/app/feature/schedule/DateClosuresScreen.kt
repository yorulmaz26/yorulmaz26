package com.kuaforumcepte.app.feature.schedule

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.domain.schedule.DateClosure

/** Uzmanın kişisel veya salonun genel (sahip) özel tarih / aralık kapatmaları. */
@Composable
fun DateClosuresScreen(onBack: () -> Unit, viewModel: DateClosuresViewModel = hiltViewModel()) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val edit by viewModel.edit.collectAsStateWithLifecycle()
    val change by viewModel.change.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val msg = message?.asString()
    var confirmDelete by remember { mutableStateOf<DateClosure?>(null) }
    val salon = viewModel.scope == ClosureScope.SALON
    LaunchedEffect(msg) { if (msg != null) { snackbar.showSnackbar(msg); viewModel.consumeMessage() } }

    Scaffold(
        topBar = { KcTopBar(if (salon) "Salon Özel Kapalı Tarihleri" else "Özel Tarih Kapatma", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (list is ClosureListState.Ready) {
                ExtendedFloatingActionButton(onClick = viewModel::openNew, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Kapatma Ekle") })
            }
        },
    ) { padding ->
        when (val s = list) {
            ClosureListState.Loading -> SkeletonCardList(3, Modifier.padding(padding))
            ClosureListState.NotAuthorized -> EmptyState("Bu bölüm için yetkiniz yok", modifier = Modifier.padding(padding))
            is ClosureListState.Error -> ErrorState(s.message.asString(), Modifier.padding(padding))
            is ClosureListState.Ready -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.s, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item {
                    InfoBanner(
                        if (salon) "Salonun tamamen kapalı olacağı tarihler (tatil, tadilat vb.). Bu tarihlerde hiçbir uzmana randevu alınamaz. Açıklama müşterilere gösterilir."
                        else "Kişisel izin veya rapor gibi tarihlerde randevu almayı kapatın. Açıklama yalnızca size ve salon sahibine görünür.",
                    )
                }
                if (s.items.isEmpty()) {
                    item { EmptyState(icon = Icons.Outlined.EventBusy, title = "Planlanmış kapatma yok", message = "Geçmiş kapatmalar burada listelenmez.") }
                }
                items(s.items, key = { it.id }) { c ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.fillMaxWidth().padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (c.startDate == c.endDate) c.startDate.format(trDate) else "${c.startDate.format(trShortDate)} – ${c.endDate.format(trShortDate)}",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text("${c.dayCount} gün" + if (c.note.isNotBlank()) " · ${c.note}" else "", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { viewModel.openEdit(c) }) { Icon(Icons.Outlined.Edit, "Kapatmayı düzenle") }
                            IconButton(onClick = { confirmDelete = c }) { Icon(Icons.Outlined.Delete, "Kapatmayı kaldır", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    edit?.let { e ->
        ClosureEditorDialog(
            title = if (e.closure == null) "Yeni kapatma" else "Kapatmayı düzenle",
            today = viewModel.today,
            initialStart = e.closure?.startDate,
            initialEnd = e.closure?.endDate,
            initialNote = e.closure?.note.orEmpty(),
            noteLabel = if (salon) "Müşterilere görünen açıklama (isteğe bağlı)" else "Açıklama (isteğe bağlı)",
            saving = change != ChangeState.Idle,
            onSave = viewModel::save,
            onDismiss = viewModel::closeEdit,
        )
    }
    when (val c = change) {
        is ChangeState.AwaitingConfirmation -> ConflictDialog(c.conflicts, false, viewModel::confirmConflicts, viewModel::cancelConflicts)
        is ChangeState.Applying -> if (c.conflicts.isNotEmpty()) ConflictDialog(c.conflicts, true, {}, {})
        else -> Unit
    }
    confirmDelete?.let { c ->
        ConfirmDialog(
            "Kapatma kaldırılsın mı?", "Bu tarihler yeniden randevuya açılır.", "Kaldır",
            onConfirm = { viewModel.delete(c); confirmDelete = null }, onDismiss = { confirmDelete = null },
        )
    }
}
