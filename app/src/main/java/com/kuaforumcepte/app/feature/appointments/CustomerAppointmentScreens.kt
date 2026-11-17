package com.kuaforumcepte.app.feature.appointments

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarRate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.common.UiState
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcSecondaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.KcTheme
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.appointments.Appointment
import com.kuaforumcepte.domain.booking.formatDuration
import com.kuaforumcepte.domain.model.AppointmentStatus
import java.time.Instant

/** Randevu bileti (müşteri görünümü). */
@Composable
fun AppointmentTicket(a: Appointment, now: Instant) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.number, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                AppointmentStatusChip(a.effectiveStatus(now))
            }
            Text(a.salonName, style = MaterialTheme.typography.headlineSmall)
            Text(a.salonAddress, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(a.whenLabel(), style = MaterialTheme.typography.titleMedium)
            Text("Uzman: ${a.staffName}", style = MaterialTheme.typography.bodyMedium)
            a.guestName?.let { Text("Hizmeti alacak: $it", style = MaterialTheme.typography.bodyMedium) }
            a.items.forEach { i ->
                Row {
                    Text("${i.name} · ${formatDuration(i.durationMinutes)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(i.price.formatTry())
                }
            }
            HorizontalDivider()
            if (a.discount.value > 0) {
                Row {
                    Text(if (a.discountType == "LOYALTY") "Sadakat ödülü" else "İndirim (${a.discountCode})", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text("−${a.discount.formatTry()}", color = MaterialTheme.colorScheme.primary)
                }
            }
            Row {
                Text("Toplam · ${formatDuration(a.totalMinutes)}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(a.finalTotal.formatTry(), fontWeight = FontWeight.Bold)
            }
            a.cancelReason?.takeIf { a.status == AppointmentStatus.CANCELLED_BY_STAFF || a.status == AppointmentStatus.CANCELLED_BY_CUSTOMER }?.let {
                Text("İptal gerekçesi: $it", style = MaterialTheme.typography.bodySmall, color = KcTheme.colors.cancel)
            }
        }
    }
}

@Composable
fun BookingSuccessScreen(onOpenAppointment: () -> Unit, onHome: () -> Unit, viewModel: CustomerAppointmentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notifGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }

    Scaffold(topBar = { KcTopBar("Talebiniz Alındı") }) { padding ->
        when (val s = state) {
            UiState.Loading -> SkeletonCardList(2, Modifier.padding(padding))
            is UiState.Empty -> EmptyState("Randevu bulunamadı", modifier = Modifier.padding(padding))
            is UiState.Error -> ErrorState(s.message.asString(), Modifier.padding(padding))
            is UiState.Success -> {
                val (a, now) = s.data
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = KcTheme.colors.success, modifier = Modifier.size(56.dp))
                    Text("Randevu talebiniz salona iletildi", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text(
                        "Durum: ${a.effectiveStatus(now).label}. Uzman onayladığında size bildirim göndereceğiz.",
                        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    )
                    AppointmentTicket(a, now)
                    if (!notifGranted) {
                        InfoBanner("Onay ve hatırlatmaları kaçırmamak için bildirimlere izin verin. İzin vermezseniz bildirimler uygulama içindeki kutuda görünmeye devam eder.")
                        KcSecondaryButton("Bildirimlere İzin Ver", { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }, leadingIcon = Icons.Outlined.NotificationsActive)
                    }
                    KcSecondaryButton("Google Takvim'e Ekle", {
                        try { context.startActivity(CalendarIntents.insert(a)) } catch (_: ActivityNotFoundException) { }
                    }, leadingIcon = Icons.Outlined.CalendarMonth)
                    Text(
                        "Takvim etkinliği randevu durumu değişince otomatik güncellenmez. Onaydan sonra eklemeniz önerilir.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    KcSecondaryButton("Paylaş", {
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, CalendarIntents.shareText(a)) }
                        context.startActivity(Intent.createChooser(send, "Randevuyu paylaş"))
                    }, leadingIcon = Icons.Outlined.Share)
                    KcPrimaryButton("Randevuyu Görüntüle", onOpenAppointment)
                    TextButton(onClick = onHome) { Text("Ana sayfaya dön") }
                }
            }
        }
    }
}

@Composable
fun CustomerAppointmentDetailScreen(
    onBack: () -> Unit,
    onReview: (String) -> Unit,
    onRebook: (Appointment) -> Unit,
    viewModel: CustomerAppointmentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val msg = message?.asString()
    var askCancel by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    LaunchedEffect(msg) { if (msg != null) { snackbar.showSnackbar(msg); viewModel.consumeMessage() } }

    Scaffold(topBar = { KcTopBar("Randevu Bileti", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        when (val s = state) {
            UiState.Loading -> SkeletonCardList(2, Modifier.padding(padding))
            is UiState.Empty -> EmptyState("Randevu bulunamadı", modifier = Modifier.padding(padding))
            is UiState.Error -> ErrorState(s.message.asString(), Modifier.padding(padding))
            is UiState.Success -> {
                val (a, now) = s.data
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    AppointmentTicket(a, now)
                    val status = a.effectiveStatus(now)
                    when {
                        a.customerCanCancel(now) -> {
                            KcSecondaryButton("Randevuyu İptal Et", { askCancel = true }, loading = busy)
                            Text("Başlangıçtan en geç 2 saat öncesine kadar iptal edebilirsiniz.", style = MaterialTheme.typography.bodySmall)
                        }
                        a.customerMustCallToCancel(now) -> {
                            InfoBanner("Randevuya 2 saatten az kaldığı için uygulamadan iptal edilemez. Gelemeyecekseniz uzmanı veya salon sahibini arayın; iptali salon yapar.")
                            val list = contacts
                            if (list == null) {
                                KcSecondaryButton("İptal için salonla iletişime geç", viewModel::loadContacts, loading = busy, leadingIcon = Icons.Outlined.Call)
                            } else {
                                list.forEach { c ->
                                    KcSecondaryButton("${c.roleLabel}: ${c.name} — Ara", {
                                        try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.phoneE164}"))) } catch (_: ActivityNotFoundException) { }
                                    }, leadingIcon = Icons.Outlined.Call)
                                }
                            }
                        }
                        status == AppointmentStatus.EXPIRED -> InfoBanner("Salon talebinizi süresi içinde yanıtlamadığı için talep düştü. Yeni randevu alabilirsiniz.")
                        else -> Unit
                    }
                    if (status == AppointmentStatus.COMPLETED) {
                        if (a.reviewId == null) KcPrimaryButton("Hizmeti / Kuaförü Puanla", { onReview(a.id) }, leadingIcon = Icons.Outlined.StarRate)
                        else InfoBanner("Bu randevuyu değerlendirdiniz. Teşekkürler!")
                    }
                    if (status.isTerminal) {
                        KcPrimaryButton("Tekrar Randevu Al", { onRebook(a) }, leadingIcon = Icons.Outlined.Replay)
                        Text("Aynı salon, uzman ve hizmetlerle yeni saat seçersiniz.", style = MaterialTheme.typography.bodySmall)
                        KcSecondaryButton("Listemden Gizle", { viewModel.hide(onBack) }, loading = busy, leadingIcon = Icons.Outlined.VisibilityOff)
                        Text("Yalnızca sizin listenizden kaldırılır; salon kayıtları ve değerlendirmeler silinmez.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (status.isActive) {
                        KcSecondaryButton("Google Takvim'e Ekle", {
                            try { context.startActivity(CalendarIntents.insert(a)) } catch (_: ActivityNotFoundException) { }
                        }, leadingIcon = Icons.Outlined.CalendarMonth)
                    }
                }
            }
        }
    }

    if (askCancel) {
        AlertDialog(
            onDismissRequest = { askCancel = false },
            title = { Text("Randevu iptal edilsin mi?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text("Salon ve uzman bilgilendirilir; saat yeniden müsait olur.")
                    KcTextField(reason, { reason = it }, "Gerekçe (isteğe bağlı)", maxLength = 300, singleLine = false, minLines = 2)
                }
            },
            confirmButton = { TextButton(onClick = { askCancel = false; viewModel.cancel(reason) }) { Text("İptal Et", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { askCancel = false }) { Text("Vazgeç") } },
        )
    }
}
