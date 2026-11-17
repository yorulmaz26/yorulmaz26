package com.kuaforumcepte.app.feature.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.TextButton
import com.kuaforumcepte.app.data.registration.LegalDocument
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.datastore.ThemeMode
import com.kuaforumcepte.app.core.designsystem.component.ConfirmDialog
import com.kuaforumcepte.app.core.designsystem.component.KcSecondaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.feature.business.MenuRow
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Storefront
import com.kuaforumcepte.domain.model.EntryRole
import com.kuaforumcepte.domain.model.MemberRole
import com.kuaforumcepte.domain.phone.PhoneNumbers

@Composable
fun AccountScreen(
    currentRole: EntryRole?,
    onBack: () -> Unit,
    onSwitchRole: (EntryRole) -> Unit,
    onSignedOut: () -> Unit,
    onOpenLegal: (LegalDocument) -> Unit,
    onChangePhone: () -> Unit,
    onOpenModeration: () -> Unit,
    onOpenCalendarSync: () -> Unit,
    onSalonReopened: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val closedSalons by viewModel.closedSalons.collectAsStateWithLifecycle()
    var reopening by remember { mutableStateOf<com.kuaforumcepte.app.data.salon.SalonProfile?>(null) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val leaving by viewModel.leaving.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val msg = message?.asString()
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val savingName by viewModel.savingName.collectAsStateWithLifecycle()
    var editingName by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.signedOut.collect { onSignedOut() } }
    LaunchedEffect(msg) { if (msg != null) { snackbar.showSnackbar(msg); viewModel.consumeMessage() } }

    Scaffold(topBar = { KcTopBar("Hesap", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(ui.name.ifBlank { "Profil" }, style = MaterialTheme.typography.headlineSmall)
            Text(PhoneNumbers.formatForDisplay(ui.phone), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick = { editingName = true }) { Text("Adımı düzenle") }
                TextButton(onClick = onChangePhone) { Text("Telefonu değiştir") }
            }
            if (isAdmin) MenuRow(Icons.Outlined.Gavel, "Moderasyon ve Salon Onayı", "Şikâyet edilen yorumlar, onay bekleyen salonlar", onOpenModeration)
            MenuRow(Icons.Outlined.CalendarMonth, "Takvim Entegrasyonu", "Randevularınız Google/Outlook/Apple takviminde otomatik güncel", onOpenCalendarSync)
            closedSalons.forEach { cs ->
                MenuRow(Icons.Outlined.Storefront, "Kapattığınız salon: ${cs.name}", "Salonu yeniden açın (uzmanları yeniden davet edersiniz)") { reopening = cs }
            }

            if (ui.memberships.isNotEmpty()) {
                Text("Salon üyelikleri", style = MaterialTheme.typography.titleMedium)
                ui.memberships.forEach { m ->
                    Text("• ${m.salonName} — ${m.role.label} (${statusLabel(m.status.name)})", style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider()

            Text("Giriş türü", style = MaterialTheme.typography.titleMedium)
            if (currentRole != EntryRole.CUSTOMER) {
                MenuRow(Icons.Outlined.Person, "Müşteri olarak devam et", "Randevu almak için müşteri tarafına geçin") { onSwitchRole(EntryRole.CUSTOMER) }
            }
            if (currentRole != EntryRole.BUSINESS) {
                MenuRow(Icons.Outlined.ContentCut, "Kuaför / Uzman paneline geç", "Salon üyelikleriniz veya davetleriniz kontrol edilir") { onSwitchRole(EntryRole.BUSINESS) }
            }
            HorizontalDivider()

            Row(
                Modifier.fillMaxWidth().heightIn(min = Spacing.touch).clickable(role = Role.Switch) { viewModel.setRememberMe(!ui.settings.rememberMe) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Beni hatırla", style = MaterialTheme.typography.bodyLarge)
                    Text("Kapalıysa uygulamayı her açtığınızda yeniden SMS ile giriş yaparsınız.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = ui.settings.rememberMe, onCheckedChange = { viewModel.setRememberMe(it) })
            }

            Text("Tema", style = MaterialTheme.typography.titleMedium)
            listOf(ThemeMode.SYSTEM to "Sistem ayarı", ThemeMode.LIGHT to "Açık", ThemeMode.DARK to "Koyu").forEach { (mode, label) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Spacing.touch).clickable(role = Role.RadioButton) { viewModel.setTheme(mode) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = ui.settings.themeMode == mode, onClick = { viewModel.setTheme(mode) })
                    Text(label)
                }
            }
            HorizontalDivider()

            val active = ui.active?.membership
            if (currentRole == EntryRole.BUSINESS && active?.role == MemberRole.STAFF) {
                KcSecondaryButton("${active.salonName} salonundan ayrıl", { confirmLeave = true }, loading = leaving)
            }
            KcSecondaryButton("Çıkış Yap", { confirmSignOut = true }, leadingIcon = Icons.AutoMirrored.Outlined.Logout)
            HorizontalDivider()
            Text("Gizlilik", style = MaterialTheme.typography.titleMedium)
            MenuRow(Icons.Outlined.Policy, "KVKK Aydınlatma Metni", "Kişisel verilerinizin nasıl işlendiği", { onOpenLegal(LegalDocument.KVKK) })
            MenuRow(Icons.Outlined.Description, "Kullanım Koşulları", "Hizmet kuralları", { onOpenLegal(LegalDocument.TERMS) })
            TextButton(onClick = { confirmDelete = true }, enabled = !deleting) {
                Text(if (deleting) "Hesap siliniyor…" else "Hesabımı Sil", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Salondan ayrılmak istiyor musunuz?",
            message = "Bu salonda artık randevu alamaz ve panele erişemezsiniz. Geçmiş kayıtlar silinmez. Tekrar katılmak için salon sahibinin yeni davetine ihtiyaç vardır.",
            confirmLabel = "Ayrıl",
            destructive = true,
            onConfirm = { confirmLeave = false; viewModel.leaveActiveSalon() },
            onDismiss = { confirmLeave = false },
        )
    }
    if (editingName) {
        val parts = ui.name.split(" ")
        var first by remember { mutableStateOf(parts.dropLast(1).joinToString(" ").ifBlank { ui.name }) }
        var last by remember { mutableStateOf(if (parts.size > 1) parts.last() else "") }
        val valid = Regex("^[\\p{L}][\\p{L} .'-]{0,48}[\\p{L}.]$").let { r -> r.matches(first.trim()) && r.matches(last.trim()) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!savingName) editingName = false },
            title = { Text("Adınızı düzenleyin") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    com.kuaforumcepte.app.core.designsystem.component.KcTextField(first, { first = it }, "Ad", maxLength = 50)
                    com.kuaforumcepte.app.core.designsystem.component.KcTextField(last, { last = it }, "Soyad", maxLength = 50)
                    Text("Geçmiş randevu kayıtları değişmez; aktif randevular ve uzman kartlarınız güncellenir.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.updateName(first, last) { editingName = false } }, enabled = valid && !savingName) { Text(if (savingName) "Kaydediliyor…" else "Kaydet") } },
            dismissButton = { TextButton(onClick = { editingName = false }, enabled = !savingName) { Text("Vazgeç") } },
        )
    }
    reopening?.let { cs ->
        ConfirmDialog(
            title = "${cs.name} yeniden açılsın mı?",
            message = "Salon tekrar aktif olur ve randevu almaya açılır. Daha önce çalışan uzmanlar otomatik dönmez; Stilist Yönetimi'nden yeniden davet edersiniz. Başka bir salonun sahibiyseniz yeniden açamazsınız.",
            confirmLabel = "Yeniden Aç",
            onConfirm = { reopening = null; viewModel.reopenSalon(cs.id, onSalonReopened) },
            onDismiss = { reopening = null },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Hesabınız kalıcı olarak silinsin mi?",
            message = "Profiliniz, cihaz kayıtlarınız ve bildirimleriniz silinir; geçmiş randevu ve değerlendirmelerinizdeki adınız ve telefonunuz anonimleştirilir. " +
                "Salonların yasal olarak saklaması gereken işlem kayıtları kişisel veri içermeden korunur. Aktif randevunuz varsa önce iptal etmeniz gerekir. " +
                "Salon sahibiyseniz önce Dükkan Ayarları'ndan salonu devredin veya kapatın. Bu işlem geri alınamaz.",
            confirmLabel = "Kalıcı Olarak Sil",
            destructive = true,
            onConfirm = { confirmDelete = false; viewModel.deleteAccount() },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = "Çıkış yapılsın mı?",
            message = "Tekrar giriş yapmak için telefonunuza SMS kodu gönderilir.",
            confirmLabel = "Çıkış Yap",
            onConfirm = { confirmSignOut = false; viewModel.signOut() },
            onDismiss = { confirmSignOut = false },
        )
    }
}

private fun statusLabel(status: String) = when (status) {
    "ACTIVE" -> "aktif"
    "INACTIVE" -> "pasif"
    else -> "ayrıldı"
}
