package com.kuaforumcepte.app.feature.register

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.ConsentRow
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.registration.LegalDocument
import com.kuaforumcepte.domain.phone.PhoneNumbers

@Composable
fun CustomerRegisterScreen(
    onRegistered: () -> Unit,
    onOpenLegal: (LegalDocument) -> Unit,
    onCancel: () -> Unit,
    viewModel: CustomerRegisterViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val err = ui.error?.asString()
    LaunchedEffect(Unit) { viewModel.done.collect { onRegistered() } }
    LaunchedEffect(err) { if (err != null) { snackbar.showSnackbar(err); viewModel.dismissError() } }

    Scaffold(topBar = { KcTopBar("Müşteri Kaydı", onCancel) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(Spacing.screen)) {
            Text("Sizi tanıyalım", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Doğrulanan numara: ${PhoneNumbers.formatForDisplay(viewModel.phoneDisplay)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xl))
            KcTextField(ui.firstName, viewModel::onFirst, "Ad", error = ui.firstNameError, capitalization = KeyboardCapitalization.Words, maxLength = 50)
            Spacer(Modifier.height(Spacing.s))
            KcTextField(ui.lastName, viewModel::onLast, "Soyad", error = ui.lastNameError, capitalization = KeyboardCapitalization.Words, maxLength = 50, imeAction = ImeAction.Done)
            Spacer(Modifier.height(Spacing.l))
            ConsentRow(ui.kvkk, viewModel::onKvkk, "Kişisel verilerimin işlenmesine ilişkin aydınlatma metnini okudum.", "Aydınlatma metnini oku") { onOpenLegal(LegalDocument.KVKK) }
            ConsentRow(ui.terms, viewModel::onTerms, "Kullanım koşullarını kabul ediyorum.", "Kullanım koşullarını oku") { onOpenLegal(LegalDocument.TERMS) }
            Spacer(Modifier.height(Spacing.xl))
            KcPrimaryButton("Kaydı Tamamla", viewModel::submit, loading = ui.submitting, enabled = ui.kvkk && ui.terms)
        }
    }
}
