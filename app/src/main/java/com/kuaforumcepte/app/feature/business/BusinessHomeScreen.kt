package com.kuaforumcepte.app.feature.business

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Loyalty
import androidx.compose.material.icons.outlined.Reviews
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.domain.auth.Permission
import com.kuaforumcepte.app.feature.bizappointments.BusinessAppointmentsContent
import com.kuaforumcepte.app.feature.platform.SalonVerificationBanner
import com.kuaforumcepte.app.feature.inbox.InboxBell
import com.kuaforumcepte.app.feature.reports.ReportsSection
import com.kuaforumcepte.domain.model.MemberRole
import kotlinx.coroutines.launch

/** Sekmeler: günlük iş (Randevular) önce; Raporlar; Salonum = tüm ayarlar gruplu. */
private enum class BusinessTab(val label: String, val icon: ImageVector) {
    APPOINTMENTS("Randevular", Icons.AutoMirrored.Outlined.EventNote),
    STATS("Raporlar", Icons.Outlined.BarChart),
    MANAGE("Salonum", Icons.Outlined.Storefront),
}

@Composable
fun BusinessHomeScreen(
    onOpenStaff: () -> Unit,
    onOpenShopSettings: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenShare: () -> Unit,
    onOpenWorkingHours: () -> Unit,
    onOpenDaysOff: () -> Unit,
    onOpenMyClosures: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenSalonClosedDays: () -> Unit,
    onOpenAccount: () -> Unit,
    onSwitchSalon: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenAppointment: (String) -> Unit,
    onAddWalkIn: () -> Unit,
    onOpenPromotions: () -> Unit,
    onOpenSalonPage: (String) -> Unit,
    viewModel: BusinessHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    when (val s = state) {
        BusinessHomeState.Loading -> Surface { SkeletonCardList(4) }
        BusinessHomeState.NoActiveSalon -> Scaffold(topBar = { KcTopBar("İşletme Paneli", actions = { AccountAction(onOpenAccount) }) }) { p ->
            EmptyState(
                title = "Aktif salon üyeliğiniz yok",
                message = "Bu salondaki üyeliğiniz pasife alınmış veya sonlanmış olabilir. Başka bir salon seçebilir ya da hesap ayarlarından çıkış yapabilirsiniz.",
                actionLabel = "Salon seç",
                onAction = onSwitchSalon,
                modifier = Modifier.padding(p),
            )
        }
        is BusinessHomeState.Ready -> {
            val m = s.active.membership
            Scaffold(
                topBar = {
                    KcTopBar(m.salonName, actions = {
                        if (s.active.allActive.size > 1) {
                            IconButton(onClick = onSwitchSalon) { Icon(Icons.Outlined.SwapHoriz, contentDescription = "Salon değiştir") }
                        }
                        InboxBell(onOpenInbox)
                        AccountAction(onOpenAccount)
                    })
                },
                snackbarHost = { SnackbarHost(snackbar) },
                floatingActionButton = {
                    if (BusinessTab.entries[tab] == BusinessTab.APPOINTMENTS) {
                        ExtendedFloatingActionButton(
                            onClick = onAddWalkIn,
                            icon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                            text = { Text("Elden Randevu") },
                        )
                    }
                },
                bottomBar = {
                    NavigationBar {
                        BusinessTab.entries.forEachIndexed { i, t ->
                            NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
                        }
                    }
                },
            ) { padding ->
                if (BusinessTab.entries[tab] == BusinessTab.APPOINTMENTS) {
                    BusinessAppointmentsContent(
                        onOpen = onOpenAppointment,
                        onMessage = { m -> scope.launch { snackbar.showSnackbar(m) } },
                        modifier = Modifier.padding(padding),
                    )
                    return@Scaffold
                }
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.l),
                ) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(m.role.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs))
                    }
                    if (m.role == com.kuaforumcepte.domain.model.MemberRole.OWNER) SalonVerificationBanner(m.salonId)
                    val p = s.permissions
                    when (BusinessTab.entries[tab]) {
                        BusinessTab.STATS -> {
                            ReportsSection(isOwner = Permission.VIEW_SALON_REPORTS in p)
                        }
                        BusinessTab.MANAGE -> {
                            MenuSection("Müşteri kazan", "Salonunuzu paylaşın ve görünür olun") {
                                if (Permission.SHARE_BOOKING_LINK in p) MenuTile(Icons.Outlined.QrCode2, "Link ve QR Kod", "WhatsApp, yazdır", onOpenShare)
                                MenuTile(Icons.Outlined.Reviews, "Salon Sayfam", "Yorumlar ve yanıtlar") { onOpenSalonPage(m.salonId) }
                                if (Permission.MANAGE_SALON_SETTINGS in p) MenuTile(Icons.Outlined.Loyalty, "Kampanyalar", "İndirim kodu, sadakat", onOpenPromotions)
                            }
                            SalonCodeCard(m.salonCode, onCopied = { scope.launch { snackbar.showSnackbar("Salon kodu kopyalandı") } })
                            MenuSection("Benim çalışma düzenim", "Sadece sizin takviminiz ve hizmetleriniz") {
                                MenuTile(Icons.Outlined.ContentCut, "Hizmetlerim", "Fiyat ve süre", onOpenServices)
                                MenuTile(Icons.Outlined.Schedule, "Çalışma Saatleri", "Vardiya ve molalar", onOpenWorkingHours)
                                MenuTile(Icons.Outlined.BeachAccess, "İzin Günleri", "Haftalık izin", onOpenDaysOff)
                                MenuTile(Icons.Outlined.EventBusy, "Tarih Kapatma", "İzin, rapor", onOpenMyClosures)
                            }
                            if (Permission.MANAGE_SALON_SETTINGS in p || Permission.MANAGE_STAFF in p || Permission.MANAGE_SALON_CLOSURES in p || Permission.MANAGE_SALON_MEDIA in p) {
                                MenuSection("Salon yönetimi", "Tüm salonu etkileyen ayarlar") {
                                    if (Permission.MANAGE_STAFF in p) MenuTile(Icons.Outlined.Groups, "Ekibim", "Uzman, davet, prim", onOpenStaff)
                                    if (Permission.MANAGE_SALON_SETTINGS in p) MenuTile(Icons.Outlined.Storefront, "Dükkan Bilgileri", "Ad, adres, konum", onOpenShopSettings)
                                    if (Permission.MANAGE_SALON_MEDIA in p) MenuTile(Icons.Outlined.PhotoLibrary, "Logo ve Fotoğraflar", "Galeri", onOpenMedia)
                                    if (Permission.MANAGE_SALON_CLOSURES in p) MenuTile(Icons.Outlined.DoorFront, "Salon Kapalı Günleri", "Tatil, bayram", onOpenSalonClosedDays)
                                }
                            }
                            MenuRow(Icons.Outlined.ManageAccounts, "Hesap ve Oturum", "Profil, takvim, tema, çıkış", onOpenAccount)
                        }
                        BusinessTab.APPOINTMENTS -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountAction(onOpenAccount: () -> Unit) {
    IconButton(onClick = onOpenAccount) { Icon(Icons.Outlined.AccountCircle, contentDescription = "Hesap") }
}

/** Başlıklı menü grubu; içindeki kutucuklar iki sütunlu ızgarada dizilir. */
@Composable
internal fun MenuSection(title: String, subtitle: String, content: MenuSectionScope.() -> Unit) {
    val scope = MenuSectionScope().apply { content() }
    if (scope.items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        scope.items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                row.forEach { item -> Box(Modifier.weight(1f)) { item() } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

internal class MenuSectionScope {
    val items = mutableListOf<@Composable () -> Unit>()
}

internal fun MenuSectionScope.MenuTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    items += { MenuTileView(icon, title, subtitle, onClick) }
}

@Composable
private fun MenuTileView(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = 108.dp),
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(subtitle) },
            leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
        )
    }
}
