package com.kuaforumcepte.app.feature.customer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.common.UiState
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.KcTextButton
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.feature.salon.SalonHoursSection
import com.kuaforumcepte.app.feature.appointments.MyAppointmentsContent
import com.kuaforumcepte.app.feature.booking.ActiveAppointmentCard
import com.kuaforumcepte.app.feature.booking.SalonListContent
import com.kuaforumcepte.app.feature.inbox.InboxBell
import com.kuaforumcepte.app.feature.reviews.SalonReviewsSection
import com.kuaforumcepte.app.feature.salon.SalonInfoContent
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar

private enum class CustomerTab(val label: String, val icon: ImageVector) {
    BOOK("Randevu Al", Icons.Outlined.ContentCut),
    MINE("Randevularım", Icons.Outlined.CalendarMonth),
    SALON("Salon Bilgileri", Icons.Outlined.Storefront),
}

/** Müşteri ana kabuğu. Sekme içerikleri Aşama 5 (rezervasyon) ve Aşama 8'de (salon bilgileri) dolar. */
@Composable
fun CustomerHomeScreen(
    onOpenAccount: () -> Unit,
    onOpenSalon: (String) -> Unit,
    onOpenAppointment: (String) -> Unit,
    onOpenInbox: () -> Unit,
    onReview: (String) -> Unit,
    viewModel: CustomerHomeViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val selected by viewModel.selectedSalon.collectAsStateWithLifecycle()
    val active by viewModel.activeAppointment.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            KcTopBar(CustomerTab.entries[tab].label, actions = {
                InboxBell(onOpenInbox)
                IconButton(onClick = onOpenAccount) { Icon(Icons.Outlined.AccountCircle, contentDescription = "Hesap") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                CustomerTab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
                }
            }
        },
    ) { padding ->
        when (CustomerTab.entries[tab]) {
            CustomerTab.BOOK -> SalonListContent(
                onOpenSalon = onOpenSalon,
                header = { active?.let { a -> ActiveAppointmentCard(a, onOpen = { onOpenAppointment(a.id) }) } },
                modifier = Modifier.padding(padding),
            )
            CustomerTab.MINE -> MyAppointmentsContent(
                onOpen = onOpenAppointment,
                onBook = { tab = CustomerTab.BOOK.ordinal },
                onReview = onReview,
                onMessage = { m -> scope.launch { snackbar.showSnackbar(m) } },
                modifier = Modifier.padding(padding),
            )
            CustomerTab.SALON -> when (val st = selected) {
                null -> EmptyState(
                    icon = Icons.Outlined.Storefront,
                    title = "Önce kuaför seçiniz",
                    message = "Randevu Al sekmesinden bir salon açıp “Bu salonu seç” dediğinizde bilgileri burada görünür.",
                    actionLabel = "Randevu Al'a git",
                    onAction = { tab = CustomerTab.BOOK.ordinal },
                    modifier = Modifier.padding(padding),
                )
                UiState.Loading -> SkeletonCardList(3, Modifier.padding(padding))
                is UiState.Empty -> EmptyState(
                    title = "Salon bulunamadı", message = st.message, actionLabel = "Seçimi kaldır",
                    onAction = viewModel::clearSelection, modifier = Modifier.padding(padding),
                )
                is UiState.Error -> ErrorState(st.message.asString(), Modifier.padding(padding))
                is UiState.Success -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen)) {
                    SalonInfoContent(st.data) { m -> scope.launch { snackbar.showSnackbar(m) } }
                    Spacer(Modifier.height(Spacing.l))
                    SalonHoursSection(st.data.id, st.data.weeklyClosedDays)
                    Spacer(Modifier.height(Spacing.l))
                    SalonReviewsSection(st.data.id, st.data.rating)
                    Spacer(Modifier.height(Spacing.s))
                    KcTextButton("Salonu değiştir", viewModel::clearSelection)
                }
            }
        }
    }
}
