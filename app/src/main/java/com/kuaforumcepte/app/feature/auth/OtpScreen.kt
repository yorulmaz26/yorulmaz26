package com.kuaforumcepte.app.feature.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTextButton
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing

@Composable
fun OtpScreen(onBack: () -> Unit, onSignedIn: () -> Unit, viewModel: OtpViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { viewModel.signedIn.collect { onSignedIn() } }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Scaffold(topBar = { KcTopBar("Doğrulama Kodu", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("SMS kodunu girin", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "${ui.phoneDisplay} numarasına gönderilen 6 haneli kodu yazın.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xl))

            BasicTextField(
                value = ui.code,
                onValueChange = viewModel::onCode,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.focusRequester(focus).semantics { contentDescription = "6 haneli doğrulama kodu" },
                decorationBox = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        repeat(6) { i ->
                            val char = ui.code.getOrNull(i)?.toString().orEmpty()
                            val active = i == ui.code.length
                            Box(
                                Modifier.size(width = 46.dp, height = 56.dp).border(
                                    width = if (active) 2.dp else 1.dp,
                                    color = when {
                                        ui.error != null -> MaterialTheme.colorScheme.error
                                        active -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.outline
                                    },
                                    shape = MaterialTheme.shapes.small,
                                ),
                                contentAlignment = Alignment.Center,
                            ) { Text(char, style = MaterialTheme.typography.headlineSmall) }
                        }
                    }
                },
            )

            ui.error?.let {
                Spacer(Modifier.height(Spacing.m))
                Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            if (ui.sessionLost) {
                Spacer(Modifier.height(Spacing.m))
                InfoBanner("Doğrulama oturumu bulunamadı (uygulama yeniden başlatılmış olabilir). Lütfen yeni kod isteyin.")
            }
            Spacer(Modifier.height(Spacing.xl))
            KcPrimaryButton(
                text = "Doğrula",
                onClick = viewModel::verify,
                loading = ui.verifying,
                enabled = ui.code.length == 6 && !ui.sessionLost,
            )
            Spacer(Modifier.height(Spacing.s))
            KcTextButton(
                text = when {
                    ui.resending -> "Kod gönderiliyor…"
                    ui.secondsUntilResend > 0 -> "Kodu tekrar gönder (${ui.secondsUntilResend} sn)"
                    else -> "Kodu tekrar gönder"
                },
                onClick = { viewModel.resend(activity) },
                enabled = ui.secondsUntilResend == 0 && !ui.resending,
                modifier = Modifier.fillMaxWidth(),
            )
            KcTextButton(text = "Numarayı değiştir", onClick = onBack, modifier = Modifier.fillMaxWidth())
        }
    }
}
