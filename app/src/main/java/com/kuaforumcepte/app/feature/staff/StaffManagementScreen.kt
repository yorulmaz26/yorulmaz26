package com.kuaforumcepte.app.feature.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.ConfirmDialog
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.KcTheme
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.staff.InvitationStatus
import com.kuaforumcepte.app.data.staff.StaffInvitation
import com.kuaforumcepte.app.data.staff.StaffMember
import com.kuaforumcepte.app.feature.auth.formatTrMobile
import com.kuaforumcepte.app.feature.business.SalonCodeCard
import com.kuaforumcepte.domain.model.MemberRole
import com.kuaforumcepte.domain.model.MemberStatus
import com.kuaforumcepte.domain.time.BusinessTime
import java.time.format.DateTimeFormatter

@Composable
fun StaffManagementScreen(onBack: () -> Unit, viewModel: StaffManagementViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val invite by viewModel.invite.collectAsStateWithLifecycle()
    val edit by viewModel.edit.collectAsStateWithLifecycle()
    val busy by viewModel.busyIds.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val msgText = message?.asString()
    var confirmDeactivate by remember { mutableStateOf<StaffMember?>(null) }
    var confirmRevoke by remember { mutableStateOf<StaffInvitation?>(null) }
    var viewingSchedule by remember { mutableStateOf<StaffMember?>(null) }

    LaunchedEffect(msgText) { if (msgText != null) { snackbar.showSnackbar(msgText); viewModel.consumeMessage() } }

    Scaffold(
        topBar = { KcTopBar("Stilist Yönetimi", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state is StaffListState.Ready) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::openInvite,
                    icon = { Icon(Icons.Outlined.PersonAdd, null) },
                    text = { Text("Uzman Davet Et") },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            }
        },
    ) { padding ->
        when (val s = state) {
            StaffListState.Loading -> SkeletonCardList(4, Modifier.padding(padding))
            StaffListState.NotAuthorized -> EmptyState(
                title = "Bu bölüm yalnızca salon sahibine açıktır",
                message = "Uzman ekleme ve yönetimi salon sahibinin yetkisindedir.",
                modifier = Modifier.padding(padding),
            )
            is StaffListState.Error -> ErrorState(s.message.asString(), Modifier.padding(padding), onRetry = viewModel::retry)
            is StaffListState.Ready -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.s, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item { SalonCodeCard(s.salonCode) }
                item { SectionTitle("Uzmanlar (${s.members.count { it.status == MemberStatus.ACTIVE }} aktif)") }
                items(s.members, key = { "m_" + it.uid }) { m ->
                    MemberCard(
                        member = m,
                        busy = m.uid in busy,
                        onEdit = { viewModel.openEdit(m) },
                        onSchedule = { viewingSchedule = m },
                        onToggle = { active -> if (active) viewModel.setActive(m, true) else confirmDeactivate = m },
                    )
                }
                item { SectionTitle("Davetler") }
                if (s.invitations.isEmpty()) {
                    item {
                        Text(
                            "Henüz davet yok. Uzman eklemek için “Uzman Davet Et” düğmesini kullanın.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(s.invitations, key = { "i_" + it.id }) { inv ->
                    InvitationCard(inv, busy = inv.id in busy, onRevoke = { confirmRevoke = inv })
                }
            }
        }
    }

    invite?.let { f -> InviteSheet(f, viewModel) }

    edit?.let { f ->
        AlertDialog(
            onDismissRequest = viewModel::closeEdit,
            title = { Text("Uzman bilgilerini düzenle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    KcTextField(f.firstName, { v -> viewModel.onEdit { it.copy(firstName = v) } }, "Ad", capitalization = KeyboardCapitalization.Words, maxLength = 50)
                    KcTextField(f.lastName, { v -> viewModel.onEdit { it.copy(lastName = v) } }, "Soyad", capitalization = KeyboardCapitalization.Words, maxLength = 50)
                    KcTextField(f.specialty, { v -> viewModel.onEdit { it.copy(specialty = v) } }, "Uzmanlık alanı", maxLength = 60, imeAction = ImeAction.Done)
                    KcTextField(
                        f.commission ?: "", { v -> viewModel.onEdit { it.copy(commission = v.filter(Char::isDigit).take(3)) } }, "Prim oranı (%)",
                        enabled = f.commission != null, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        supporting = "Tamamlanan hizmet bedelinin bu yüzdesi raporlarda uzman primi olarak gösterilir. Yalnızca siz ve uzman görür.",
                    )
                }
            },
            confirmButton = { TextButton(onClick = viewModel::submitEdit, enabled = !f.submitting) { Text(if (f.submitting) "Kaydediliyor…" else "Kaydet") } },
            dismissButton = { TextButton(onClick = viewModel::closeEdit) { Text("Vazgeç") } },
        )
    }

    viewingSchedule?.let { m ->
        val week by remember(m.uid) { viewModel.scheduleOf(m.uid) }.collectAsStateWithLifecycle(initialValue = null)
        AlertDialog(
            onDismissRequest = { viewingSchedule = null },
            title = { Text("${m.fullName} · çalışma saatleri") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    val w = week
                    if (w == null) Text("Yükleniyor…") else com.kuaforumcepte.domain.schedule.ScheduleSummary.lines(w).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text("Program uzmanın kendisi tarafından “Çalışma Saatleri” bölümünden düzenlenir.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { viewingSchedule = null }) { Text("Kapat") } },
        )
    }
    confirmDeactivate?.let { m ->
        ConfirmDialog(
            title = "${m.fullName} pasife alınsın mı?",
            message = "Pasif uzman yeni randevu alamaz ve işletme paneline erişemez. Mevcut gelecek randevular otomatik iptal edilmez; size bildirilir.",
            confirmLabel = "Pasife Al",
            destructive = true,
            onConfirm = { viewModel.setActive(m, false); confirmDeactivate = null },
            onDismiss = { confirmDeactivate = null },
        )
    }
    confirmRevoke?.let { inv ->
        ConfirmDialog(
            title = "Davet iptal edilsin mi?",
            message = "${inv.fullName} bu davetle salonunuza katılamaz. Gerekirse yeniden davet edebilirsiniz.",
            confirmLabel = "Daveti İptal Et",
            destructive = true,
            onConfirm = { viewModel.revoke(inv); confirmRevoke = null },
            onDismiss = { confirmRevoke = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.s))
}

@Composable
private fun StatusPill(text: String, color: Color, container: Color) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = container) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs))
    }
}

@Composable
private fun MemberCard(member: StaffMember, busy: Boolean, onEdit: () -> Unit, onSchedule: () -> Unit, onToggle: (Boolean) -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(member.fullName, style = MaterialTheme.typography.titleMedium)
                Text("${member.role.label} · ${member.specialty}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    when (member.status) {
                        MemberStatus.ACTIVE -> StatusPill("Aktif", KcTheme.colors.success, KcTheme.colors.successContainer)
                        MemberStatus.INACTIVE -> StatusPill("Pasif", KcTheme.colors.cancel, KcTheme.colors.cancelContainer)
                        MemberStatus.LEFT -> StatusPill("Ayrıldı", MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
                    }
                    StatusPill("Puan: ${member.rating.label()}", MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
                }
            }
            if (member.status != MemberStatus.LEFT) {
                IconButton(onClick = onSchedule) { Icon(Icons.Outlined.Schedule, contentDescription = "${member.fullName} çalışma saatleri") }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "${member.fullName} düzenle") }
            }
            if (busy) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else if (member.role != MemberRole.OWNER && member.status != MemberStatus.LEFT) {
                val active = member.status == MemberStatus.ACTIVE
                Switch(
                    checked = active,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics { stateDescription = if (active) "Aktif" else "Pasif" },
                )
            }
        }
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", BusinessTime.LOCALE).withZone(BusinessTime.ZONE)

@Composable
private fun InvitationCard(inv: StaffInvitation, busy: Boolean, onRevoke: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(inv.fullName, style = MaterialTheme.typography.titleMedium)
                Text("${inv.specialty} · ${inv.phoneDisplay}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val (fg, bg) = when (inv.status) {
                    InvitationStatus.PENDING -> KcTheme.colors.pending to KcTheme.colors.pendingContainer
                    InvitationStatus.ACCEPTED -> KcTheme.colors.success to KcTheme.colors.successContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
                }
                val suffix = if (inv.status == InvitationStatus.PENDING) inv.expiresAt?.let { " · son: ${dateFmt.format(it)}" }.orEmpty() else ""
                StatusPill(inv.status.label + suffix, fg, bg)
            }
            if (inv.status == InvitationStatus.PENDING) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else TextButton(onClick = onRevoke) { Text("İptal Et", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteSheet(f: InviteForm, vm: StaffManagementViewModel) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = vm::closeInvite, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text("Uzman davet et", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Davet ettiğiniz kişi, aşağıdaki cep telefonuyla “Kuaför / Uzman Girişi” yapıp SMS ile doğruladığında daveti görür ve kabul ederek salonunuza katılır. Davet 7 gün geçerlidir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            KcTextField(f.firstName, { v -> vm.onInvite { it.copy(firstName = v) } }, "Ad", error = f.errors["firstName"], capitalization = KeyboardCapitalization.Words, maxLength = 50)
            KcTextField(f.lastName, { v -> vm.onInvite { it.copy(lastName = v) } }, "Soyad", error = f.errors["lastName"], capitalization = KeyboardCapitalization.Words, maxLength = 50)
            KcTextField(f.specialty, { v -> vm.onInvite { it.copy(specialty = v) } }, "Uzmanlık alanı", error = f.errors["specialty"], supporting = "Ör. Saç Kesimi, Renklendirme, Cilt Bakımı", maxLength = 60)
            KcTextField(
                value = formatTrMobile(f.phoneDigits),
                onValueChange = { v -> vm.onInvite { it.copy(phoneDigits = v.filter(Char::isDigit).trimStart('0').take(10)) } },
                label = "Cep telefonu",
                prefix = "+90 ",
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
                error = f.errors["phone"],
                onImeAction = vm::submitInvite,
            )
            Spacer(Modifier.height(Spacing.s))
            KcPrimaryButton("Daveti Oluştur", vm::submitInvite, loading = f.submitting)
            Spacer(Modifier.height(Spacing.l))
        }
    }
}
