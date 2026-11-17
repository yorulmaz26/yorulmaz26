package com.kuaforumcepte.app.feature.customer

import androidx.compose.foundation.layout.padding
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
fun CustomerHomeScreen(onOpenAccount: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            KcTopBar(CustomerTab.entries[tab].label, actions = {
                IconButton(onClick = onOpenAccount) { Icon(Icons.Outlined.AccountCircle, contentDescription = "Hesap") }
            })
        },
        bottomBar = {
            NavigationBar {
                CustomerTab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
                }
            }
        },
    ) { padding ->
        when (CustomerTab.entries[tab]) {
            CustomerTab.BOOK -> EmptyState(
                icon = Icons.Outlined.ContentCut,
                title = "Kayıtlı Kuaförlerimiz",
                message = "Salon listesi, uzman ve hizmet seçimi ile randevu alma bir sonraki geliştirme aşamasında burada etkinleşecek.",
                modifier = Modifier.padding(padding),
            )
            CustomerTab.MINE -> EmptyState(
                icon = Icons.Outlined.EventAvailable,
                title = "Henüz randevunuz yok",
                message = "Aldığınız randevular burada aktif ve geçmiş olarak listelenecek.",
                modifier = Modifier.padding(padding),
            )
            CustomerTab.SALON -> EmptyState(
                icon = Icons.Outlined.Storefront,
                title = "Önce kuaför seçiniz",
                message = "Randevu Al sekmesinden bir salon seçtiğinizde salon bilgileri burada görünür.",
                actionLabel = "Randevu Al'a git",
                onAction = { tab = CustomerTab.BOOK.ordinal },
                modifier = Modifier.padding(padding),
            )
        }
    }
}
