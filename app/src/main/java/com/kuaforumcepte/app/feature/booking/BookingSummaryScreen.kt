package com.kuaforumcepte.app.feature.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.domain.booking.formatDuration
import com.kuaforumcepte.domain.time.BusinessTime
import java.time.format.DateTimeFormatter

private val whenFmt = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE · HH:mm", BusinessTime.LOCALE).withZone(BusinessTime.ZONE)
private val endFmt = DateTimeFormatter.ofPattern("HH:mm", BusinessTime.LOCALE).withZone(BusinessTime.ZONE)

@Composable
fun BookingSummaryScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    onBackToSlots: () -> Unit,
    onOpenAppointment: (String) -> Unit,
    viewModel: BookingSummaryViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val err = ui.error?.asString()
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is SummaryEvent.Created -> onCreated(e.appointmentId)
                SummaryEvent.BackToSlots -> onBackToSlots()
            }
        }
    }
    LaunchedEffect(err) { if (err != null && ui.data != null) { snackbar.showSnackbar(err); viewModel.dismissError() } }

    Scaffold(
        topBar = { KcTopBar("Randevu Özeti", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (ui.data != null) Surface(tonalElevation = 3.dp) {
                KcPrimaryButton("Randevuyu Onayla", viewModel::confirm, Modifier.navigationBarsPadding().padding(Spacing.l), loading = ui.submitting)
            }
        },
    ) { padding ->
        val d = ui.data
        when {
            ui.loading && d == null -> SkeletonCardList(3, Modifier.padding(padding))
            d == null -> ErrorState(err ?: "Bilgiler alınamadı.", Modifier.padding(padding), onRetry = viewModel::load)
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(d.salon.name, style = MaterialTheme.typography.headlineSmall)
                        Text(d.salon.fullAddress, style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                        Row { Text("Uzman", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge); Text(d.staff.fullName) }
                        Row {
                            Text("Tarih/Saat", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            Text("${whenFmt.format(viewModel.startAt)}–${endFmt.format(viewModel.startAt.plusSeconds(d.minutes * 60L))}")
                        }
                        HorizontalDivider()
                        d.items.forEach { s ->
                            Row {
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(formatDuration(s.durationMinutes), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(s.price.formatTry())
                            }
                        }
                        HorizontalDivider()
                        val pv = ui.preview
                        val discounted = pv != null && pv.discount.value > 0 && (ui.appliedCode != null || ui.useLoyalty)
                        Row {
                            Text("Toplam · ${formatDuration(d.minutes)}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text(d.total.formatTry(), style = MaterialTheme.typography.titleMedium, fontWeight = if (discounted) FontWeight.Normal else FontWeight.Bold)
                        }
                        if (discounted) {
                            Row {
                                Text(if (ui.useLoyalty) "Sadakat ödülü" else "İndirim (${ui.appliedCode})", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                                Text("−${pv!!.discount.formatTry()}", color = MaterialTheme.colorScheme.primary)
                            }
                            Row {
                                Text("Ödenecek", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Text(pv!!.finalTotal.formatTry(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (!viewModel.route.forSelf) {
                    KcTextField(
                        value = ui.guestName, onValueChange = viewModel::setGuestName, label = "Hizmeti alacak kişinin adı soyadı",
                        error = ui.guestNameError, capitalization = KeyboardCapitalization.Words,
                        supporting = "Randevu sizin hesabınızda görünür; salon bu adı görür.",
                    )
                }
                DiscountSection(ui, viewModel::setCodeInput, viewModel::applyCode, viewModel::removeCode, viewModel::setUseLoyalty)
                InfoBanner(
                    "İptal politikası: Randevunuzu başlangıçtan en geç 2 saat öncesine kadar uygulamadan iptal edebilirsiniz. " +
                        "Son 2 saatte iptal için uzmanı veya salonu aramanız gerekir. " +
                        "Talebiniz salon onayına gönderilir; 30 dakika içinde (veya randevu saatine kadar) onaylanmazsa otomatik olarak düşer. " +
                        "Ödeme uygulama üzerinden alınmaz; tutar salonda ödenir.",
                )
            }
        }
    }

    when (val p = ui.problem) {
        is SummaryProblem.PriceChanged -> AlertDialog(
            onDismissRequest = viewModel::dismissProblem,
            title = { Text("Fiyat değişti") },
            text = { Text("Salon hizmet fiyatlarını güncellemiş. Yeni toplam tutar: ${p.newTotal.formatTry()}. Bu tutarla devam etmek istiyor musunuz?") },
            confirmButton = { TextButton(onClick = viewModel::acceptNewPrice) { Text("Yeni fiyatı gör") } },
            dismissButton = { TextButton(onClick = onBack) { Text("Vazgeç") } },
        )
        is SummaryProblem.SlotGone -> AlertDialog(
            onDismissRequest = viewModel::backToSlots,
            title = { Text("Saat artık uygun değil") },
            text = { Text(p.message.asString()) },
            confirmButton = { TextButton(onClick = viewModel::backToSlots) { Text("Başka saat seç") } },
        )
        is SummaryProblem.ActiveExists -> AlertDialog(
            onDismissRequest = viewModel::dismissProblem,
            title = { Text("Aktif randevu sınırı") },
            text = { Text("Aynı anda en fazla 3 aktif randevunuz olabilir. Yeni randevu için birinin tamamlanmasını bekleyin veya Randevularım'dan birini iptal edin.") },
            confirmButton = { TextButton(onClick = { p.appointmentId?.let(onOpenAppointment) ?: viewModel.dismissProblem() }) { Text("Tamam") } },
            dismissButton = { TextButton(onClick = viewModel::dismissProblem) { Text("Kapat") } },
        )
        null -> Unit
    }
}

@Composable
private fun DiscountSection(
    ui: SummaryUi,
    onCode: (String) -> Unit,
    onApply: () -> Unit,
    onRemove: () -> Unit,
    onLoyalty: (Boolean) -> Unit,
) {
    val loyalty = ui.preview?.loyalty
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("İndirim", style = MaterialTheme.typography.titleSmall)
            if (loyalty != null) {
                if (loyalty.rewardsAvailable > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Sadakat ödülünüz hazır: %${loyalty.rewardPercent} indirim", style = MaterialTheme.typography.bodyMedium)
                            Text("Bu salonda ${loyalty.completedCount} tamamlanmış ziyaretiniz var.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = ui.useLoyalty, onCheckedChange = onLoyalty, enabled = !ui.previewing)
                    }
                } else {
                    Text(
                        "Sadakat programı: her ${loyalty.visitsRequired} ziyarette %${loyalty.rewardPercent} indirim. " +
                            (loyalty.visitsToNext?.let { "Ödüle $it ziyaret kaldı." } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (!ui.useLoyalty) {
                if (ui.appliedCode != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("“${ui.appliedCode}” kodu uygulandı", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = onRemove) { Text("Kaldır") }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        KcTextField(
                            value = ui.codeInput, onValueChange = onCode, label = "İndirim kodu", modifier = Modifier.weight(1f),
                            error = ui.codeError, capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done, onImeAction = onApply,
                        )
                        TextButton(onClick = onApply, enabled = ui.codeInput.isNotBlank() && !ui.previewing) { Text("Uygula") }
                    }
                }
            }
        }
    }
}
