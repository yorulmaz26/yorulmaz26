package com.kuaforumcepte.app.feature.register

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.theme.KcTheme
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.feature.business.SalonCodeCard

@Composable
fun OwnerRegisterSuccessScreen(salonName: String, code: String, onContinue: () -> Unit) {
    BackHandler(onBack = onContinue)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().padding(Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.xxl))
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = KcTheme.colors.success, modifier = Modifier.height(64.dp))
            Spacer(Modifier.height(Spacing.l))
            Text("Salonunuz oluşturuldu", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(salonName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.xl))
            SalonCodeCard(code)
            Spacer(Modifier.weight(1f))
            KcPrimaryButton("Yönetim Paneline Git", onContinue)
        }
    }
}
