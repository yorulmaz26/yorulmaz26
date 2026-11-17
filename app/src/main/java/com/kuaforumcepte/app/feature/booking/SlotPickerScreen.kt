package com.kuaforumcepte.app.feature.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.booking.Slot
import com.kuaforumcepte.domain.booking.formatDuration
import com.kuaforumcepte.domain.time.BusinessTime
import java.time.format.DateTimeFormatter

private val dayName = DateTimeFormatter.ofPattern("EEE", BusinessTime.LOCALE)
private val dayNum = DateTimeFormatter.ofPattern("d MMM", BusinessTime.LOCALE)
private val fullDay = DateTimeFormatter.ofPattern("d MMMM EEEE", BusinessTime.LOCALE)

@Composable
fun SlotPickerScreen(onBack: () -> Unit, onSlotSelected: (Slot) -> Unit, viewModel: SlotPickerViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    SlotPickerContent(
        title = "Tarih ve Saat", ui = ui, canPrev = viewModel.canPrev, canNext = viewModel.canNext,
        onBack = onBack, onReload = viewModel::load, onSelectDate = viewModel::selectDate, onShift = viewModel::shiftWindow,
        onSlotSelected = onSlotSelected,
        footer = "Yalnızca hizmetlerin toplam süresinin tamamının sığdığı başlangıç saatleri gösterilir. Gösterilen saat, onayladığınız anda sunucuda yeniden kontrol edilir.",
    )
}

/** Müşteri rezervasyonu ve salonun "Saati Değiştir" ekranının ortak tarih/saat seçici arayüzü. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SlotPickerContent(
    title: String,
    ui: SlotPickerUi,
    canPrev: Boolean,
    canNext: Boolean,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onSelectDate: (java.time.LocalDate) -> Unit,
    onShift: (Boolean) -> Unit,
    onSlotSelected: (Slot) -> Unit,
    footer: String,
    showPrice: Boolean = true,
) {
    Scaffold(topBar = {
        KcTopBar(title, onBack, actions = { IconButton(onClick = onReload) { Icon(Icons.Outlined.Refresh, "Saatleri yenile") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(vertical = Spacing.s)) {
            ui.availability?.let { a ->
                Text(
                    if (showPrice) "Toplam ${formatDuration(a.totalMinutes)} · ${a.total.formatTry()}" else "Süre ${formatDuration(a.totalMinutes)}",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = Spacing.screen),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onShift(false) }, enabled = canPrev && !ui.loading) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Önceki günler")
                }
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), contentPadding = PaddingValues(horizontal = Spacing.xs)) {
                    items(ui.availability?.days.orEmpty(), key = { it.date.toString() }) { d ->
                        val selected = d.date == ui.selectedDate
                        val has = d.slots.isNotEmpty()
                        Surface(
                            onClick = { onSelectDate(d.date) },
                            shape = MaterialTheme.shapes.medium,
                            color = when {
                                selected -> MaterialTheme.colorScheme.primary
                                has -> MaterialTheme.colorScheme.surfaceContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(64.dp).semantics { this.selected = selected },
                        ) {
                            Column(Modifier.padding(vertical = Spacing.s), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d.date.format(dayName), style = MaterialTheme.typography.labelMedium)
                                Text(d.date.format(dayNum), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                                Text(if (has) "${d.slots.size} saat" else "Dolu/kapalı", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                IconButton(onClick = { onShift(true) }, enabled = canNext && !ui.loading) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Sonraki günler")
                }
            }
            Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                when {
                    ui.loading -> SkeletonCardList(2, Modifier.padding(0.dp))
                    ui.error != null -> ErrorState(ui.error!!.asString(), onRetry = onReload)
                    ui.selectedDate == null -> Unit
                    ui.slotsOfSelected.isEmpty() -> EmptyState(
                        title = "${ui.selectedDate!!.format(fullDay)} için uygun saat yok",
                        message = "Uzman o gün çalışmıyor, salon kapalı veya tüm saatler dolu olabilir. Başka bir gün seçin.",
                    )
                    else -> {
                        Text(ui.selectedDate!!.format(fullDay), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            ui.slotsOfSelected.forEach { slot ->
                                OutlinedButton(onClick = { onSlotSelected(slot) }, shape = MaterialTheme.shapes.medium) { Text(slot.label) }
                            }
                        }
                    }
                }
                InfoBanner(footer)
            }
        }
    }
}
