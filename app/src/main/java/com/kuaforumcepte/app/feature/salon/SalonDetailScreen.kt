package com.kuaforumcepte.app.feature.salon

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.kuaforumcepte.app.feature.booking.BookingSection
import com.kuaforumcepte.app.feature.reviews.SalonReviewsSection
import com.kuaforumcepte.app.feature.booking.BookingSectionViewModel
import com.kuaforumcepte.domain.booking.formatDuration
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.common.UiState
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun SalonDetailScreen(
    onBack: () -> Unit,
    onPickTime: (salonId: String, staffUid: String, serviceIds: List<String>) -> Unit,
    onOpenAppointment: (String) -> Unit,
    viewModel: SalonDetailViewModel = hiltViewModel(),
    bookingViewModel: BookingSectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val booking by bookingViewModel.ui.collectAsStateWithLifecycle()
    val selected by viewModel.isSelected.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toast: (String) -> Unit = { m -> scope.launch { snackbar.showSnackbar(m) } }

    Scaffold(
        topBar = { KcTopBar((state as? UiState.Success)?.data?.name ?: "Salon", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val salonState = state
            val sel = booking.selection
            if (salonState is UiState.Success && !sel.isEmpty && booking.activeAppointment == null) Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().padding(Spacing.l)) {
                    Text(
                        "${sel.items.size} hizmet · ${formatDuration(sel.totalMinutes)} · ${sel.totalPrice.formatTry()}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (booking.selfIsStaff) Text("Kendinize randevu oluşturamazsınız.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    KcPrimaryButton(
                        "Saat Seç",
                        { onPickTime(salonState.data.id, sel.staffUid!!, sel.items.map { it.id }) },
                        Modifier.padding(top = Spacing.s),
                        enabled = !booking.selfIsStaff,
                        leadingIcon = Icons.Outlined.Schedule,
                    )
                }
            } else if (salonState is UiState.Success) Surface(tonalElevation = 3.dp) {
                KcPrimaryButton(
                    text = if (selected) "Salon seçildi" else "Bu salonu seç",
                    onClick = { viewModel.select(); toast("Salon seçildi. Bilgilerine “Salon Bilgileri” sekmesinden ulaşabilirsiniz.") },
                    enabled = !selected,
                    leadingIcon = if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.Storefront,
                    modifier = Modifier.navigationBarsPadding().padding(Spacing.l),
                )
            }
        },
    ) { padding ->
        when (val s = state) {
            UiState.Loading -> SkeletonCardList(3, Modifier.padding(padding))
            is UiState.Empty -> EmptyState("Salon bulunamadı", s.message, Modifier.padding(padding))
            is UiState.Error -> ErrorState(s.message.asString(), Modifier.padding(padding), onRetry = viewModel::retry)
            is UiState.Success -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen)) {
                SalonInfoContent(s.data, toast)
                Spacer(Modifier.height(Spacing.xl))
                BookingSection(s.data.id, onActiveAppointment = onOpenAppointment, viewModel = bookingViewModel)
                Spacer(Modifier.height(Spacing.xl))
                SalonHoursSection(s.data.id, s.data.weeklyClosedDays)
                Spacer(Modifier.height(Spacing.xl))
                SalonReviewsSection(s.data.id, s.data.rating)
                Spacer(Modifier.height(Spacing.l))
            }
        }
    }
}
