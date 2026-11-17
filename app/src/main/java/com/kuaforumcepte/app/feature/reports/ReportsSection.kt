package com.kuaforumcepte.app.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.SkeletonBlock
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.KcTheme
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.finance.ReportLine
import com.kuaforumcepte.domain.model.Kurus
import com.kuaforumcepte.domain.time.PeriodType

/**
 * İstatistikler sekmesindeki dönem kartları. Sahip: salon toplamı + her uzman/sahip için ayrı satır.
 * Uzman: yalnızca kendi kartı (sunucu da yalnızca kendi verisini döndürür).
 */
@Composable
fun ReportsSection(isOwner: Boolean, viewModel: ReportsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(if (isOwner) "Salon raporları" else "Kişisel raporum", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PeriodType.entries.forEachIndexed { i, t ->
                SegmentedButton(selected = ui.period.type == t, onClick = { viewModel.setType(t) }, shape = SegmentedButtonDefaults.itemShape(i, PeriodType.entries.size)) { Text(t.label) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::previous) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Önceki dönem") }
            Text(ui.period.label(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            IconButton(onClick = viewModel::next, enabled = ui.canGoNext) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Sonraki dönem") }
        }
        val r = ui.report
        when {
            ui.loading && r == null -> SkeletonBlock(Modifier.fillMaxWidth(), 120.dp)
            ui.error != null -> ErrorState(ui.error!!.asString(), onRetry = viewModel::load)
            r != null -> {
                r.salon?.let { MetricCards("Salon toplamı", it) }
                if (isOwner) {
                    Text("Uzman bazında", style = MaterialTheme.typography.titleMedium)
                    if (r.staff.isEmpty()) Text("Bu dönemde kayıt yok.", style = MaterialTheme.typography.bodyMedium)
                    r.staff.forEach { s ->
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Row(Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.name + if (s.role == "OWNER") " (Salon Sahibi)" else "", style = MaterialTheme.typography.titleSmall)
                                    Text("${s.line.completedCount} işlem" + if (!s.active) " · artık aktif değil" else "", style = MaterialTheme.typography.bodySmall)
                                    if (s.line.commission.value != 0L || s.commissionPercent > 0) {
                                        Text("Prim ${s.line.commission.formatTry()} (güncel oran %${s.commissionPercent})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Text(s.line.amount.formatTry(), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                } else {
                    r.staff.firstOrNull()?.let { MetricCards("Benim işlemlerim", it.line) } ?: Text("Bu dönemde kayıt yok.")
                }
            }
        }
        InfoBanner(
            "Yalnızca “Tamamlandı” olarak kapatılan randevular sayılır; gelmeyen, iptal edilen veya süresi dolan randevular gelir yazmaz. " +
                "Tutarlar “gerçekleşen hizmet bedeli”dir; ödeme uygulama üzerinden alınmadığı için tahsilat anlamına gelmez. " +
                "Gün 00:00'da, hafta Pazartesi 00:00'da, ay her ayın 1'inde yenilenir (İstanbul saati). Yeni dönem başladığında sayım sıfırdan başlar; geçmiş raporlar korunur.",
        )
    }
}

@Composable
private fun MetricCards(title: String, line: ReportLine) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Metric("Tamamlanan işlem", "${line.completedCount}", Modifier.weight(1f))
        Metric("Gerçekleşen hizmet bedeli", line.amount.formatTry(), Modifier.weight(1f))
    }
    if (line.commission.value != 0L) {
        Text("Uzman primi toplamı: ${line.commission.formatTry()} (tamamlama anındaki oranlarla)", style = MaterialTheme.typography.bodySmall)
    }
    val diff = line.difference
    if (diff != Kurus.ZERO) {
        Text(
            "Liste fiyatı toplamı ${line.listed.formatTry()} · fark ${if (diff.value > 0) "+" else ""}${diff.formatTry()} (indirimler ve yetkili fiyat düzeltmeleri)",
            style = MaterialTheme.typography.bodySmall,
            color = if (diff.value < 0) KcTheme.colors.cancel else KcTheme.colors.success,
        )
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier) {
        Column(Modifier.padding(Spacing.l)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
