package com.kuaforumcepte.app.feature.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.domain.model.EntryRole

@Composable
fun PhoneEntryScreen(
    role: EntryRole,
    onBack: () -> Unit,
    onCodeSent: () -> Unit,
    onSignedIn: () -> Unit,
    viewModel: PhoneEntryViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val snackbar = remember { SnackbarHostState() }
    val errorText = ui.error?.asString()

    LaunchedEffect(Unit) {
        viewModel.events.collect {
            when (it) {
                PhoneEntryEvent.CodeSent -> onCodeSent()
                PhoneEntryEvent.SignedIn -> onSignedIn()
            }
        }
    }
    LaunchedEffect(errorText) {
        if (errorText != null) { snackbar.showSnackbar(errorText); viewModel.dismissError() }
    }

    Scaffold(
        topBar = { KcTopBar(if (role == EntryRole.CUSTOMER) "Müşteri Girişi" else "Kuaför / Uzman Girişi", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(Spacing.screen),
        ) {
            Text("Telefon numaranız", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Numaranıza 6 haneli bir doğrulama kodu göndereceğiz. Hesabınız yoksa doğrulamadan sonra kayıt adımına geçersiniz.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xl))
            KcTextField(
                value = formatTrMobile(ui.digits),
                onValueChange = viewModel::onDigits,
                label = "Cep telefonu",
                prefix = "+90 ",
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
                error = ui.fieldError,
                supporting = "Örnek: 532 123 45 67",
                onImeAction = { viewModel.send(activity) },
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = Spacing.touch).clickable(role = Role.Checkbox) { viewModel.onRememberMe(!ui.rememberMe) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = ui.rememberMe, onCheckedChange = viewModel::onRememberMe)
                Column {
                    Text("Beni hatırla", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Kapalıysa uygulamayı her açtığınızda yeniden giriş yaparsınız.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            KcPrimaryButton(
                text = "Kod Gönder",
                onClick = { viewModel.send(activity) },
                loading = ui.sending,
                enabled = ui.digits.length == 10,
                leadingIcon = Icons.Outlined.Sms,
            )
            Spacer(Modifier.height(Spacing.l))
            Text(
                "Telefon numarası tek başına yetki vermez. İşletme yetkileriniz, salon sahibinin onayladığı üyeliklerden belirlenir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

