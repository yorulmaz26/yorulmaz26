package com.kuaforumcepte.app.feature.services

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcDropdown
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.KcTheme
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.services.StaffService
import com.kuaforumcepte.domain.booking.ServiceRules
import com.kuaforumcepte.domain.booking.formatDuration

@Composable
fun ServicesScreen(onBack: () -> Unit, viewModel: ServicesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val delete by viewModel.delete.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val msg = message?.asString()
    LaunchedEffect(msg) { if (msg != null) { snackbar.showSnackbar(msg); viewModel.consumeMessage() } }

    Scaffold(
        topBar = { KcTopBar("Hizmetlerim", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state is ServicesState.Ready) {
                ExtendedFloatingActionButton(onClick = viewModel::openNew, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Hizmet Ekle") })
            }
        },
    ) { padding ->
        when (val s = state) {
            ServicesState.Loading -> SkeletonCardList(4, Modifier.padding(padding))
            ServicesState.NoSalon -> EmptyState("Aktif salon üyeliği yok", modifier = Modifier.padding(padding))
            is ServicesState.Error -> ErrorState(s.message.asString(), Modifier.padding(padding))
            is ServicesState.Ready -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.s, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item { InfoBanner("Bu hizmetler yalnızca size aittir; müşteriler sizi seçtiğinde bu listeden seçim yapar. Pasif hizmetler listede kalır ama yeni randevuda seçilemez.") }
                if (s.items.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.ContentCut, title = "Henüz hizmet eklemediniz",
                            message = "Müşterilerin size randevu alabilmesi için en az bir aktif hizmet ekleyin.",
                        )
                    }
                }
                items(s.items, key = { it.id }) { svc -> ServiceCard(svc, svc.id in busy, viewModel) }
            }
        }
    }

    form?.let { ServiceFormSheet(it, viewModel) }
    delete?.let { d ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("“${d.service.name}” silinsin mi?") },
            text = {
                Text(
                    when (val c = d.impactCount) {
                        null -> "Gelecek randevulara etkisi kontrol ediliyor…"
                        -1 -> "Gelecek randevulara etkisi kontrol edilemedi. Silme, mevcut randevuları değiştirmez."
                        0 -> "Bu hizmeti içeren gelecek randevu yok. Hizmet listenizden kalkar ve yeni randevuda seçilemez."
                        else -> "Bu hizmeti içeren $c gelecek randevu var. Bu randevular iptal edilmez; kayıtlı fiyat ve süreleriyle geçerliliğini korur. Hizmet yeni randevularda seçilemez."
                    },
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete, enabled = d.impactCount != null) { Text("Sil", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun ServiceCard(svc: StaffService, busy: Boolean, vm: ServicesViewModel) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(svc.name, style = MaterialTheme.typography.titleMedium)
                    Text("${svc.price.formatTry()} · ${formatDuration(svc.durationMinutes)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Switch(checked = svc.active, onCheckedChange = { vm.toggleActive(svc) })
            }
            if (svc.description.isNotBlank()) Text(svc.description, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (svc.active) "Aktif" else "Pasif",
                    color = if (svc.active) KcTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.openEdit(svc) }) { Icon(Icons.Outlined.Edit, "${svc.name} düzenle") }
                IconButton(onClick = { vm.requestDelete(svc) }) { Icon(Icons.Outlined.Delete, "${svc.name} sil", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceFormSheet(f: ServiceForm, vm: ServicesViewModel) {
    ModalBottomSheet(onDismissRequest = vm::closeForm, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(if (f.editing == null) "Yeni hizmet" else "Hizmeti düzenle", style = MaterialTheme.typography.headlineSmall)
            if (f.editing != null) {
                InfoBanner(
                    when (val c = f.impactCount) {
                        null -> "Gelecek randevulara etkisi kontrol ediliyor…"
                        -1 -> "Gelecek randevulara etkisi kontrol edilemedi."
                        0 -> "Bu hizmeti içeren gelecek randevu yok."
                        else -> "Bu hizmeti içeren $c gelecek randevu var. Fiyat/süre değişikliği bu randevuları etkilemez; yalnızca yeni randevularda geçerli olur."
                    },
                )
            }
            KcTextField(f.name, { v -> vm.onForm { it.copy(name = v) } }, "Hizmet adı", error = f.errors.name, capitalization = KeyboardCapitalization.Sentences, maxLength = 60)
            KcTextField(f.price, { v -> vm.onForm { it.copy(price = v.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12)) } }, "Fiyat (TL)",
                error = f.errors.price, keyboardType = KeyboardType.Decimal, supporting = "Ör. 350 veya 350,50", prefix = "₺ ")
            KcDropdown("Süre", ServiceRules.durationOptions, f.duration, { formatDuration(it) }, { d -> vm.onForm { it.copy(duration = d) } }, error = f.errors.duration)
            KcTextField(f.description, { v -> vm.onForm { it.copy(description = v) } }, "Açıklama (isteğe bağlı)", error = f.errors.description,
                singleLine = false, minLines = 2, maxLength = 500, imeAction = ImeAction.Default)
            Row(
                Modifier.fillMaxWidth().heightIn(min = Spacing.touch).clickable(role = Role.Switch) { vm.onForm { it.copy(active = !it.active) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Aktif", style = MaterialTheme.typography.bodyLarge)
                    Text("Pasif hizmetler müşteriye gösterilmez.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = f.active, onCheckedChange = { a -> vm.onForm { it.copy(active = a) } })
            }
            Spacer(Modifier.height(Spacing.s))
            KcPrimaryButton(if (f.editing == null) "Hizmeti Ekle" else "Değişiklikleri Kaydet", vm::saveForm, loading = f.saving)
            Spacer(Modifier.height(Spacing.l))
        }
    }
}
