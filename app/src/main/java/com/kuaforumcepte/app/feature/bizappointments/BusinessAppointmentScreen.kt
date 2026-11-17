package com.kuaforumcepte.app.feature.bizappointments

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.ConfirmDialog
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
import com.kuaforumcepte.app.data.schedule.conflictReasonLabel
import com.kuaforumcepte.app.feature.appointments.AppointmentStatusChip
import com.kuaforumcepte.app.feature.appointments.whenLabel
import com.kuaforumcepte.app.feature.salon.MapIntents
import com.kuaforumcepte.domain.booking.formatDuration
import com.kuaforumcepte.domain.model.ActorRole
import com.kuaforumcepte.domain.model.AppointmentAction
import com.kuaforumcepte.domain.model.AppointmentStatus
import com.kuaforumcepte.domain.phone.PhoneNumbers
import com.kuaforumcepte.domain.time.BusinessTime
import java.time.format.DateTimeFormatter

private val eventFmt = DateTimeFormatter.ofPattern("d MMM HH:mm", BusinessTime.LOCALE).withZone(BusinessTime.ZONE)

private fun actorLabel(role: String) = when (role) {
    "CUSTOMER" -> "Müşteri"; "STAFF" -> "Uzman"; "OWNER" -> "Salon sahibi"; else -> "Sistem"
}

@Composable
fun BusinessAppointmentScreen(onBack: () -> Unit, onReschedule: (String) -> Unit, viewModel: BusinessAppointmentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val msg = message?.asString()
    var reasonFor by remember { mutableStateOf<AppointmentAction?>(null) }
    var confirmNoShow by remember { mutableStateOf(false) }
    var reassigning by remember { mutableStateOf(false) }
    var confirmComplete by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf(false) }
    LaunchedEffect(msg) { if (msg != null) { snackbar.showSnackbar(msg); viewModel.consumeMessage() } }

    Scaffold(topBar = { KcTopBar("Randevu Detayı", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        when (val s = state) {
            BizApptDetailState.Loading -> SkeletonCardList(3, Modifier.padding(padding))
            BizApptDetailState.NotFound -> EmptyState("Randevu bulunamadı", modifier = Modifier.padding(padding))
            is BizApptDetailState.Error -> ErrorState(s.message.asString(), Modifier.padding(padding))
            is BizApptDetailState.Ready -> {
                val d = s.d
                val a = d.appointment
                val status = a.effectiveStatus(d.now)
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp) {
                        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(a.number, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                AppointmentStatusChip(status)
                            }
                            Text(a.customerName, style = MaterialTheme.typography.headlineSmall)
                            Text(PhoneNumbers.formatForDisplay(a.customerPhoneE164), style = MaterialTheme.typography.bodyMedium)
                            Text(a.whenLabel(), style = MaterialTheme.typography.titleMedium)
                            Text("Uzman: ${a.staffName}", style = MaterialTheme.typography.bodyMedium)
                            HorizontalDivider()
                            a.items.forEach { i ->
                                Row { Text("${i.name} · ${formatDuration(i.durationMinutes)}", Modifier.weight(1f)); Text(i.price.formatTry()) }
                            }
                            HorizontalDivider()
                            Row {
                                Text("Toplam · ${formatDuration(a.totalMinutes)}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                Text(a.finalTotal.formatTry(), fontWeight = FontWeight.Bold)
                            }
                            a.cancelReason?.let { Text("Gerekçe: $it", style = MaterialTheme.typography.bodySmall, color = KcTheme.colors.cancel) }
                        }
                    }
                    if (a.attentionReason != null && status.isActive) {
                        InfoBanner(
                            "${conflictReasonLabel(a.attentionReason)}. Bu randevu takvim değişikliğiyle çakışıyor; saatini değiştirin, başka uzmana atayın veya gerekçeyle iptal edin.",
                            container = KcTheme.colors.cancelContainer,
                        )
                    }
                    if (status == AppointmentStatus.PENDING) {
                        InfoBanner("Talep ${eventFmt.format(a.pendingExpiresAt)} saatine kadar yanıtlanmazsa otomatik düşer.")
                    }

                    // ---- İşlemler ----
                    d.actions.forEach { action ->
                        when (action) {
                            AppointmentAction.CONFIRM -> KcPrimaryButton("Onayla", { viewModel.act(action) }, loading = busy, leadingIcon = Icons.Outlined.Check)
                            AppointmentAction.REJECT -> KcSecondaryButton("Reddet", { reasonFor = action }, enabled = !busy)
                            AppointmentAction.MARK_ARRIVED -> KcPrimaryButton("Geldi", { viewModel.act(action) }, loading = busy, leadingIcon = Icons.Outlined.HowToReg)
                            AppointmentAction.MARK_NO_SHOW -> KcSecondaryButton("Gelmedi", { confirmNoShow = true }, enabled = !busy, leadingIcon = Icons.Outlined.PersonOff)
                            AppointmentAction.CANCEL_BY_STAFF -> KcSecondaryButton("Gerekçeyle İptal Et", { reasonFor = action }, enabled = !busy)
                            AppointmentAction.COMPLETE -> KcPrimaryButton("İşlemi Tamamla", { confirmComplete = true }, loading = busy, leadingIcon = Icons.Outlined.TaskAlt)
                            else -> Unit
                        }
                    }
                    if (status == AppointmentStatus.CONFIRMED && AppointmentAction.MARK_ARRIVED !in d.actions) {
                        Text("“Geldi” en erken başlangıçtan 15 dakika önce, “Gelmedi” başlangıç saatinden sonra işaretlenebilir.", style = MaterialTheme.typography.bodySmall)
                    }
                    val canAdjust = status == AppointmentStatus.IN_PROGRESS || (status == AppointmentStatus.COMPLETED && d.actor == ActorRole.OWNER)
                    if (canAdjust) {
                        KcSecondaryButton(
                            if (status == AppointmentStatus.COMPLETED) "Fiyat Düzeltmesi Yap" else "Fiyatı Düzelt",
                            { adjusting = true }, enabled = !busy, leadingIcon = Icons.Outlined.Edit,
                        )
                    }
                    if (a.finalTotal != a.total) {
                        Text("Liste tutarı ${a.total.formatTry()} · gerçekleşen ${a.finalTotal.formatTry()}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (status == AppointmentStatus.PENDING || status == AppointmentStatus.CONFIRMED) {
                        KcSecondaryButton("Saati Değiştir", { onReschedule(a.id) }, enabled = !busy, leadingIcon = Icons.Outlined.EventRepeat)
                        if (d.actor == ActorRole.OWNER) {
                            KcSecondaryButton("Uzmanı Değiştir", { reassigning = true }, enabled = !busy && d.reassignTargets.isNotEmpty(), leadingIcon = Icons.Outlined.SwapHoriz)
                        }
                    }
                    if (status.isActive) {
                        KcSecondaryButton("Müşteriyi Ara", {
                            try { context.startActivity(MapIntents.dial(a.customerPhoneE164)) } catch (_: ActivityNotFoundException) { }
                        }, leadingIcon = Icons.Outlined.Call)
                    }

                    if (d.events.isNotEmpty()) {
                        Text("İşlem geçmişi", style = MaterialTheme.typography.titleMedium)
                        d.events.forEach { e ->
                            Text(
                                "${e.at?.let(eventFmt::format).orEmpty()} · ${actorLabel(e.actorRole)} · ${e.to?.label.orEmpty()}" + (e.reason?.let { " — $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                if (reassigning) {
                    AlertDialog(
                        onDismissRequest = { reassigning = false },
                        title = { Text("Uzmanı değiştir") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text("Hizmetler ve fiyatlar aynı kalır. Seçilen uzmanın bu saatte çalışıyor ve boş olması gerekir.", style = MaterialTheme.typography.bodySmall)
                                d.reassignTargets.forEach { m ->
                                    TextButton(onClick = { reassigning = false; viewModel.reassign(m) }, modifier = Modifier.fillMaxWidth()) {
                                        Text("${m.fullName} · ${m.specialty}", Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = { TextButton(onClick = { reassigning = false }) { Text("Vazgeç") } },
                    )
                }
            }
        }
    }

    reasonFor?.let { action ->
        var reason by remember(action) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reasonFor = null },
            title = { Text(if (action == AppointmentAction.REJECT) "Talebi reddet" else "Randevuyu iptal et") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text("Gerekçe müşteriye gösterilir.", style = MaterialTheme.typography.bodySmall)
                    KcTextField(reason, { reason = it }, "Gerekçe", maxLength = 300, singleLine = false, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = { reasonFor = null; viewModel.act(action, reason) }, enabled = reason.trim().length >= 3) {
                    Text(if (action == AppointmentAction.REJECT) "Reddet" else "İptal Et", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { reasonFor = null }) { Text("Vazgeç") } },
        )
    }
    val ready = state as? BizApptDetailState.Ready
    if (confirmComplete && ready != null) {
        ConfirmDialog(
            "İşlem tamamlandı mı?",
            "Gerçekleşen hizmet bedeli ${ready.d.appointment.finalTotal.formatTry()} olarak raporlara bir kez işlenecek. Tutar farklıysa önce “Fiyatı Düzelt” kullanın.",
            "Tamamla",
            onConfirm = { confirmComplete = false; viewModel.act(AppointmentAction.COMPLETE) },
            onDismiss = { confirmComplete = false },
        )
    }
    if (adjusting && ready != null) {
        var amount by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        val parsed = com.kuaforumcepte.domain.model.Kurus.parseTry(amount)
        AlertDialog(
            onDismissRequest = { adjusting = false },
            title = { Text("Tutarı düzelt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text("Mevcut: ${ready.d.appointment.finalTotal.formatTry()}. Düzeltme gerekçesiyle birlikte işlem geçmişine ve denetim kaydına yazılır; " +
                        if (ready.d.appointment.status == AppointmentStatus.COMPLETED) "raporlarda ayrı bir düzeltme hareketi olarak görünür." else "tamamlandığında bu tutar raporlanır.",
                        style = MaterialTheme.typography.bodySmall)
                    KcTextField(amount, { amount = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12) }, "Yeni tutar (TL)", prefix = "₺ ",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, error = if (amount.isNotEmpty() && parsed == null) "Geçerli bir tutar girin" else null)
                    KcTextField(reason, { reason = it }, "Gerekçe", maxLength = 300, singleLine = false, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = { adjusting = false; viewModel.adjustPrice(parsed!!, reason) }, enabled = parsed != null && reason.trim().length >= 3) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { adjusting = false }) { Text("Vazgeç") } },
        )
    }
    if (confirmNoShow) {
        ConfirmDialog(
            "Müşteri gelmedi mi?", "Randevu “Gelmedi” olarak kapanır ve gelir olarak sayılmaz.", "Gelmedi",
            onConfirm = { confirmNoShow = false; viewModel.act(AppointmentAction.MARK_NO_SHOW) }, onDismiss = { confirmNoShow = false }, destructive = true,
        )
    }
}
