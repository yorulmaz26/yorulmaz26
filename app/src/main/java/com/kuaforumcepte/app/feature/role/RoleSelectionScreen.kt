package com.kuaforumcepte.app.feature.role

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Verified
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kuaforumcepte.app.R
import com.kuaforumcepte.app.core.designsystem.component.BrandMark
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.theme.Brand
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoleSelectionContent(onChoose: (EntryRole) -> Unit, hasPendingSalonLink: Boolean = false) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            // ---- Marka alanı: espresso → zemin geçişli, logo + ad + slogan + öne çıkanlar
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(Brush.verticalGradient(listOf(Brand.EspressoDeep, Brand.Espresso)))
                    .safeDrawingPadding()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BrandMark(color = Color(0xFFF3EEE7), accent = Brand.Gold, logoSize = 112.dp)
                    Spacer(Modifier.height(Spacing.l))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        FeaturePill(Icons.Outlined.Bolt, "Hızlı randevu")
                        FeaturePill(Icons.Outlined.Verified, "Anlık onay")
                        FeaturePill(Icons.Outlined.NotificationsActive, "Hatırlatma")
                    }
                }
            }

            Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xl)) {
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
                Spacer(Modifier.height(Spacing.l))
                RoleCard(
                    icon = Icons.Outlined.Person,
                    title = stringResource(R.string.role_customer),
                    description = stringResource(R.string.role_customer_desc),
                    onClick = { onChoose(EntryRole.CUSTOMER) },
                )
                Spacer(Modifier.height(Spacing.m))
                RoleCard(
                    icon = Icons.Outlined.ContentCut,
                    title = stringResource(R.string.role_business),
                    description = stringResource(R.string.role_business_desc),
                    onClick = { onChoose(EntryRole.BUSINESS) },
                )
                Spacer(Modifier.height(Spacing.xl))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(Spacing.s))
                    Text(
                        stringResource(R.string.role_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturePill(icon: ImageVector, label: String) {
    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.08f), border = BorderStroke(1.dp, Brand.Gold.copy(alpha = 0.5f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Brand.Gold, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFFF3EEE7), maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun RoleCard(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, Brand.Gold.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 6.dp),
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
    ) {
        Row(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.l + 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Brand.GoldLight, Brand.Gold, Brand.GoldDark))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Brand.EspressoDeep, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(Spacing.l))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.s))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Brand.Gold)
        }
    }
}
