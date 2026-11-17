package com.kuaforumcepte.app.feature.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.KcTextButton
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.domain.model.EntryRole

@Composable
fun SessionGateScreen(
    role: EntryRole,
    onRoute: (SessionTarget) -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SessionGateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(role) { viewModel.start(role) }
    LaunchedEffect(state) { (state as? GateState.Route)?.let { onRoute(it.target) } }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.Center) {
            when (val s = state) {
                is GateState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ErrorState(
                        message = s.message.asString(),
                        title = "Hesap bilgileri alınamadı",
                        onRetry = if (s.signedOut) null else viewModel::retry,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    KcTextButton("Çıkış yap ve başa dön", onClick = { viewModel.signOut(onSignedOut) })
                }
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(Spacing.l))
                    Text("Hesabınız hazırlanıyor…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
