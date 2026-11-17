package com.kuaforumcepte.app.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kuaforumcepte.app.R
import com.kuaforumcepte.app.core.designsystem.component.BrandMark
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.domain.model.EntryRole

/**
 * Aşama 1 giriş noktası: seçilen rol DataStore'a yazılmış ve bu route'a gelinmiştir.
 * Aşama 2'de bu ekran telefon girişi → SMS OTP → kayıt/davet akışıyla değiştirilecektir.
 * Ekranda tıklanabilir sahte eylem yoktur; yalnızca geri dönüş.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthEntryScreen(role: EntryRole, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val title = stringResource(if (role == EntryRole.CUSTOMER) R.string.auth_title_customer else R.string.auth_title_business)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Spacing.screen)) {
            BrandMark(Modifier.fillMaxWidth(), showSlogan = false)
            Spacer(Modifier.height(Spacing.xl))
            Text(
                "Telefon numarası ile SMS doğrulamalı giriş bir sonraki geliştirme aşamasında (Aşama 2) bu ekrana eklenecek.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
