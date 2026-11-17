package com.kuaforumcepte.app.feature.promotions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcSecondaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.promo.DiscountCode
import com.kuaforumcepte.app.data.promo.PromoRepository
import com.kuaforumcepte.app.data.salon.LoyaltyProgram
import com.kuaforumcepte.app.data.salon.SalonProfile
import com.kuaforumcepte.app.data.salon.SalonRepository
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.domain.model.Kurus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PromotionsViewModel @Inject constructor(
    session: SessionRepository,
    private val salons: SalonRepository,
    private val promo: PromoRepository,
) : ViewModel() {
    private val salonId = MutableStateFlow<String?>(null)
    init { viewModelScope.launch { session.activeSalon.collect { salonId.value = it?.membership?.salonId } } }

    val salon: StateFlow<SalonProfile?> = salonId.flatMapLatest { id -> if (id == null) flowOf(null) else salons.observe(id).catch { emit(null) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val codes: StateFlow<List<DiscountCode>?> = salonId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else promo.observeCodes(id).catch { emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    fun messageShown() { _message.value = null }

    private fun run(success: String, block: suspend (String) -> Unit) {
        val id = salonId.value ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            runCatching { block(id) }.onSuccess { _message.value = success }.onFailure { _message.value = ErrorMessages.text(it) }
            _busy.value = false
        }
    }

    fun saveLoyalty(p: LoyaltyProgram) = run(if (p.enabled) "Sadakat programı kaydedildi." else "Sadakat programı kapatıldı.") { salons.updateLoyalty(it, p) }
    fun saveCode(c: DiscountCode) = run("“${c.code}” kaydedildi.") { promo.upsert(it, c) }
    fun setActive(c: DiscountCode, active: Boolean) = run(if (active) "Kod etkinleştirildi." else "Kod durduruldu.") { promo.upsert(it, c.copy(active = active)) }
}

/** Salon sahibi: indirim kodları ve sadakat programı. İndirim yalnızca hizmet bedelini düşürür; ödeme uygulamada alınmaz. */
@Composable
fun PromotionsScreen(onBack: () -> Unit, viewModel: PromotionsViewModel = hiltViewModel()) {
    val salon by viewModel.salon.collectAsStateWithLifecycle()
    val codes by viewModel.codes.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<DiscountCode?>(null) }
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }

    Scaffold(topBar = { KcTopBar("Kampanyalar ve Sadakat", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            LoyaltyCard(salon?.loyalty, busy, viewModel::saveLoyalty)
            Text("İndirim kodları", style = MaterialTheme.typography.titleMedium)
            InfoBanner("Müşteri kodu rezervasyon özetinde girer; her müşteri bir kodu bir kez kullanabilir. Kullanım randevu oluşunca ayrılır, iptal/gelmeme durumunda iade edilir.")
            KcPrimaryButton("Yeni Kod", { creating = true }, leadingIcon = Icons.Outlined.Add)
            codes.orEmpty().forEach { c ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, onClick = { editing = c }) {
                    Row(Modifier.fillMaxWidth().padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${c.code} · ${c.valueLabel}", style = MaterialTheme.typography.titleSmall)
                            c.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text(
                                listOfNotNull(
                                    "Kullanım ${c.usedCount}${c.maxUses?.let { "/$it" } ?: ""}",
                                    c.validFrom?.let { "başlangıç $it" },
                                    c.validUntil?.let { "bitiş $it" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Switch(checked = c.active, onCheckedChange = { viewModel.setActive(c, it) }, enabled = !busy)
                    }
                }
            }
            if (codes?.isEmpty() == true) Text("Henüz indirim kodu yok.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (creating || editing != null) {
        CodeDialog(editing, onSave = { viewModel.saveCode(it); creating = false; editing = null }, onDismiss = { creating = false; editing = null })
    }
}

@Composable
private fun LoyaltyCard(current: LoyaltyProgram?, busy: Boolean, onSave: (LoyaltyProgram) -> Unit) {
    var enabled by remember(current) { mutableStateOf(current?.enabled ?: false) }
    var visits by remember(current) { mutableStateOf((current?.visitsRequired ?: 10).toString()) }
    var percent by remember(current) { mutableStateOf((current?.rewardPercent ?: 20).toString()) }
    val v = visits.toIntOrNull()
    val p = percent.toIntOrNull()
    val valid = v != null && v in 3..50 && p != null && p in 5..100
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sadakat programı", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Text("Müşteri her N tamamlanmış ziyarette bir sonraki randevusunda yüzde indirim kazanır. Ödül müşterinin seçimiyle kullanılır.", style = MaterialTheme.typography.bodySmall)
            KcTextField(visits, { visits = it.filter(Char::isDigit).take(2) }, "Kaç ziyarette bir (3–50)", keyboardType = KeyboardType.Number, error = if (v != null && v !in 3..50) "3 ile 50 arası" else null)
            KcTextField(percent, { percent = it.filter(Char::isDigit).take(3) }, "Ödül indirimi % (5–100)", keyboardType = KeyboardType.Number, error = if (p != null && p !in 5..100) "5 ile 100 arası" else null)
            KcSecondaryButton("Kaydet", { onSave(LoyaltyProgram(enabled, v!!, p!!)) }, enabled = valid, loading = busy)
        }
    }
}

@Composable
private fun CodeDialog(existing: DiscountCode?, onSave: (DiscountCode) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf(existing?.code.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var isPercent by remember { mutableStateOf(existing?.amount == null) }
    var value by remember { mutableStateOf(existing?.percent?.toString() ?: existing?.amount?.let { (it.value / 100).toString() } ?: "") }
    var from by remember { mutableStateOf(existing?.validFrom?.toString().orEmpty()) }
    var until by remember { mutableStateOf(existing?.validUntil?.toString().orEmpty()) }
    var maxUses by remember { mutableStateOf(existing?.maxUses?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    fun date(s: String): Result<LocalDate?> = if (s.isBlank()) Result.success(null) else try { Result.success(LocalDate.parse(s.trim())) } catch (e: DateTimeParseException) { Result.failure(e) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Yeni indirim kodu" else "Kodu düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                KcTextField(code, { code = it.uppercase().filter(Char::isLetterOrDigit).take(16) }, "Kod (4–16 harf/rakam)", enabled = existing == null, capitalization = KeyboardCapitalization.Characters)
                KcTextField(description, { description = it.take(120) }, "Açıklama (isteğe bağlı)")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FilterChip(selected = isPercent, onClick = { isPercent = true }, label = { Text("Yüzde") })
                    FilterChip(selected = !isPercent, onClick = { isPercent = false }, label = { Text("Tutar (TL)") })
                }
                KcTextField(value, { value = it.filter(Char::isDigit).take(7) }, if (isPercent) "İndirim % (1–90)" else "İndirim tutarı (TL)", keyboardType = KeyboardType.Number)
                KcTextField(from, { from = it.take(10) }, "Başlangıç (YYYY-AA-GG, isteğe bağlı)")
                KcTextField(until, { until = it.take(10) }, "Bitiş (YYYY-AA-GG, isteğe bağlı)")
                KcTextField(maxUses, { maxUses = it.filter(Char::isDigit).take(6) }, "Toplam kullanım sınırı (isteğe bağlı)", keyboardType = KeyboardType.Number)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = value.toIntOrNull() ?: -1
                val f = date(from)
                val u = date(until)
                error = when {
                    !Regex("^[A-Z0-9]{4,16}$").matches(code) -> "Kod 4–16 harf/rakam olmalı."
                    n < 0 -> "İndirim değerini girin."
                    isPercent && n !in 1..90 -> "Yüzde 1 ile 90 arasında olmalı."
                    !isPercent && n < 1 -> "Tutar en az 1 TL olmalı."
                    f.isFailure || u.isFailure -> "Tarihleri YYYY-AA-GG biçiminde girin (ör. 2026-12-31)."
                    else -> null
                }
                if (error == null) {
                    onSave(
                        DiscountCode(
                            code = code, description = description.trim().ifBlank { null },
                            percent = if (isPercent) n else null, amount = if (isPercent) null else Kurus(n.toLong() * 100),
                            validFrom = f.getOrNull(), validUntil = u.getOrNull(), maxUses = maxUses.toIntOrNull(),
                            usedCount = existing?.usedCount ?: 0, active = existing?.active ?: true,
                        ),
                    )
                }
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
