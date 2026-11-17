package com.kuaforumcepte.app.feature.invitations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.ConsentRow
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcSecondaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.registration.LegalDocument

/** Davetli personel: doğrulanmış telefonuna gelen salon davetlerini kabul eder veya reddeder. */
@Composable
fun InvitationsScreen(
    onJoined: () -> Unit,
    onRegisterOwnSalon: () -> Unit,
    onContinue: () -> Unit,
    onOpenLegal: (LegalDocument) -> Unit,
    onBack: () -> Unit,
    viewModel: InvitationsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val err = ui.error?.asString()
    LaunchedEffect(Unit) { viewModel.joined.collect { onJoined() } }
    LaunchedEffect(err) { if (err != null) { snackbar.showSnackbar(err); viewModel.dismissError() } }

    Scaffold(topBar = { KcTopBar("Salon Davetleri", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (ui.loading) {
            SkeletonCardList(2, Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item {
                Text(
                    if (ui.invitations.isEmpty()) "Bekleyen davetiniz yok." else "Telefon numaranıza gönderilmiş salon davetleri",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (ui.invitations.isNotEmpty() && !ui.profileExists) {
                item {
                    Column {
                        ConsentRow(ui.kvkk, viewModel::onKvkk, "Aydınlatma metnini okudum.", "Aydınlatma metnini oku") { onOpenLegal(LegalDocument.KVKK) }
                        ConsentRow(ui.terms, viewModel::onTerms, "Kullanım koşullarını kabul ediyorum.", "Kullanım koşullarını oku") { onOpenLegal(LegalDocument.TERMS) }
                    }
                }
            }
            items(ui.invitations, key = { it.inviteId }) { inv ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(inv.salonName, style = MaterialTheme.typography.titleLarge)
                        Text("Davet edilen: ${inv.firstName} ${inv.lastName} · ${inv.specialty}", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            KcSecondaryButton("Reddet", { viewModel.decline(inv) }, Modifier.weight(1f), enabled = ui.busyId == null)
                            KcPrimaryButton("Kabul Et", { viewModel.accept(inv) }, Modifier.weight(1f), loading = ui.busyId == inv.inviteId, enabled = ui.busyId == null)
                        }
                    }
                }
            }
            item {
                InfoBanner("Kabul ettiğinizde bu salonun uzmanı olarak kendi çalışma saatlerinizi, hizmetlerinizi ve randevularınızı yönetebilirsiniz. Salonun finans toplamları ve diğer uzmanların kişisel verileri size açık değildir.")
            }
            item {
                if (ui.hasActiveMembership) KcSecondaryButton("Davetleri sonra yanıtla, panele geç", onContinue)
                else KcSecondaryButton("Davet yerine kendi salonumu kaydet", onRegisterOwnSalon)
            }
        }
    }
}
