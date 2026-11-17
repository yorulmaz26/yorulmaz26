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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.datastore.ThemeMode
import com.kuaforumcepte.app.core.designsystem.component.ConfirmDialog
import com.kuaforumcepte.app.core.designsystem.component.KcSecondaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.feature.business.MenuRow
import com.kuaforumcepte.domain.model.EntryRole
import com.kuaforumcepte.domain.model.MemberRole
import com.kuaforumcepte.domain.phone.PhoneNumbers

@Composable
fun AccountScreen(
    currentRole: EntryRole?,
    onBack: () -> Unit,
    onSwitchRole: (EntryRole) -> Unit,
    onSignedOut: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val leaving by viewModel.leaving.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val msg = message?.asString()
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.signedOut.collect { onSignedOut() } }
    LaunchedEffect(msg) { if (msg != null) { snackbar.showSnackbar(msg); viewModel.consumeMessage() } }

    Scaffold(topBar = { KcTopBar("Hesap", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(ui.name.ifBlank { "Profil" }, style = MaterialTheme.typography.headlineSmall)
            Text(PhoneNumbers.formatForDisplay(ui.phone), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
