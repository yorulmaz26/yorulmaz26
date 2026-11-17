package com.kuaforumcepte.app.feature.role

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kuaforumcepte.app.R
import com.kuaforumcepte.app.core.designsystem.component.BrandMark
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.domain.model.EntryRole

@Composable
fun RoleSelectionScreen(
    onRoleChosen: (EntryRole) -> Unit,
    hasPendingSalonLink: Boolean = false,
    viewModel: RoleSelectionViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.navigate.collect(onRoleChosen) }
    RoleSelectionContent(onChoose = viewModel::choose, hasPendingSalonLink = hasPendingSalonLink)
}

@Composable
fun RoleSelectionContent(onChoose: (EntryRole) -> Unit, hasPendingSalonLink: Boolean = false) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.xxl),
        ) {
            BrandMark(Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.xxxl))
            Text(stringResource(R.string.role_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(R.string.role_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasPendingSalonLink) {
                Spacer(Modifier.height(Spacing.m))
                InfoBanner("Bir salonun randevu bağlantısıyla geldiniz. “Müşteri Girişi” yaptıktan sonra salon sayfası otomatik açılacak.")
            }
            Spacer(Modifier.height(Spacing.xl))
            RoleCard(
                icon = Icons.Outlined.Person,
                title = stringResource(R.string.role_customer),
                description = stringResource(R.string.role_customer_desc),
                onClick = { onChoose(EntryRole.CUSTOMER) },
            )
            Spacer(Modifier.height(Spacing.l))
            RoleCard(
                icon = Icons.Outlined.ContentCut,
                title = stringResource(R.string.role_business),
                description = stringResource(R.string.role_business_desc),
                onClick = { onChoose(EntryRole.BUSINESS) },
            )
            Spacer(Modifier.height(Spacing.xl))
            Text(
                stringResource(R.string.role_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RoleCard(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 4.dp),
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
    ) {
        Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(52.dp)) {}
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(Spacing.l))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        }
    }
}
