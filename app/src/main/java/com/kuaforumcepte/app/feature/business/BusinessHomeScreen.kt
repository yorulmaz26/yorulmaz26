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
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Groups
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
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.domain.auth.Permission
import com.kuaforumcepte.app.feature.bizappointments.BusinessAppointmentsContent
import com.kuaforumcepte.app.feature.inbox.InboxBell
import com.kuaforumcepte.domain.model.MemberRole
import kotlinx.coroutines.launch

private enum class BusinessTab(val label: String, val icon: ImageVector) {
    STATS("İstatistikler", Icons.Outlined.BarChart),
    MANAGE("Bilgileri Yönet", Icons.Outlined.Tune),
    APPOINTMENTS("Randevular", Icons.Outlined.EventNote),
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
                    when (BusinessTab.entries[tab]) {
                        BusinessTab.STATS -> {
                            if (Permission.SHARE_BOOKING_LINK in s.permissions) {
                                MenuRow(Icons.Outlined.QrCode2, "Randevu Linki ve QR Kod", "WhatsApp'ta paylaş, QR'ı görüntüle, yazdır", onOpenShare)
                            }
                            SalonCodeCard(m.salonCode, onCopied = { scope.launch { snackbar.showSnackbar("Salon kodu kopyalandı") } })
                            InfoBanner(
                                if (Permission.VIEW_SALON_REPORTS in s.permissions)
                                    "Dönemsel işlem sayısı ve gerçekleşen hizmet bedeli raporları sonraki aşamada bu sekmeye eklenecek."
                                else "Kişisel işlem ve tutar raporlarınız sonraki aşamada bu sekmede görünecek.",
                            )
                        }
                        BusinessTab.MANAGE -> {
                            Text("Kişisel", style = MaterialTheme.typography.titleMedium)
                            MenuRow(Icons.Outlined.Schedule, "Çalışma Saatleri ve Molalar", "Gün gün vardiyalar ve molalar", onOpenWorkingHours)
                            MenuRow(Icons.Outlined.BeachAccess, "Haftalık İzin Günleri", "Her hafta çalışmadığınız günler", onOpenDaysOff)
                            MenuRow(Icons.Outlined.EventBusy, "Özel Tarih Kapatma", "İzin, rapor vb. tarih veya aralıklar", onOpenMyClosures)
                            MenuRow(Icons.Outlined.ContentCut, "Hizmetlerim", "Hizmet adı, fiyat, süre, aktif/pasif", onOpenServices)
                            if (Permission.MANAGE_SALON_SETTINGS in s.permissions) Text("Salon", style = MaterialTheme.typography.titleMedium)
                            if (Permission.MANAGE_SALON_CLOSURES in s.permissions) {
                                MenuRow(Icons.Outlined.DoorFront, "Salon Kapalı Günleri", "Haftalık kapalı günler ve özel kapalı tarihler", onOpenSalonClosedDays)
                            }
                            if (Permission.MANAGE_SALON_SETTINGS in s.permissions) {
                                MenuRow(Icons.Outlined.Storefront, "Dükkan Ayarları", "Ad, tür, telefon, adres, açıklama, konum", onOpenShopSettings)
                            }
                            if (Permission.MANAGE_SALON_MEDIA in s.permissions) {
                                MenuRow(Icons.Outlined.PhotoLibrary, "Logo ve Fotoğrafları Güncelle", "Logoyu değiştir, galeriyi düzenle", onOpenMedia)
                            }
                            if (Permission.MANAGE_STAFF in s.permissions) {
                                MenuRow(Icons.Outlined.Groups, "Stilist Yönetimi", "Uzman davet et, listele, düzenle, pasife al", onOpenStaff)
                            }
                            MenuRow(Icons.Outlined.ManageAccounts, "Hesap ve Oturum", "Profil, Beni hatırla, tema, çıkış", onOpenAccount)
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

@Composable
fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(subtitle) },
            leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
            trailingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
        )
    }
}
