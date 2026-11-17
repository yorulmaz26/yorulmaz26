package com.kuaforumcepte.app.feature.walkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcDropdown
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.core.navigation.WalkInRoute
import com.kuaforumcepte.app.data.appointments.AppointmentRepository
import com.kuaforumcepte.app.data.services.ServiceRepository
import com.kuaforumcepte.app.data.services.StaffService
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.app.data.staff.StaffMember
import com.kuaforumcepte.app.data.staff.StaffRepository
import com.kuaforumcepte.domain.booking.formatDuration
import com.kuaforumcepte.domain.model.MemberRole
import com.kuaforumcepte.domain.model.MemberStatus
import com.kuaforumcepte.domain.phone.PhoneNumbers
import com.kuaforumcepte.domain.time.BusinessTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class WalkInForm(
    val staffUid: String? = null,
    val serviceIds: Set<String> = emptySet(),
    val date: LocalDate,
    val time: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val submitting: Boolean = false,
    val timeError: String? = null,
    val nameError: String? = null,
    val phoneError: String? = null,
)

data class WalkInContext(val salonId: String, val isOwner: Boolean, val myUid: String, val staff: List<StaffMember>)

/**
 * Elden randevu: telefonla arayan/kapıdan gelen, uygulaması olmayan müşteri. Uzman yalnızca kendi adına,
 * sahip herhangi bir aktif uzman adına açar. Randevu doğrudan onaylıdır ve uzmanın takvimini kilitler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WalkInViewModel @Inject constructor(
    savedState: SavedStateHandle,
    session: SessionRepository,
    staffRepo: StaffRepository,
    private val services: ServiceRepository,
    private val appointments: AppointmentRepository,
    clock: Clock,
) : ViewModel() {
    private val route = savedState.toRoute<WalkInRoute>()
    val today: LocalDate = LocalDate.now(clock.withZone(BusinessTime.ZONE))
    private val key: String = savedState["key"] ?: UUID.randomUUID().toString().also { savedState["key"] = it }

    val context: StateFlow<WalkInContext?> = session.activeSalon.flatMapLatest { a ->
        val uid = session.currentUid
        if (a == null || uid == null) return@flatMapLatest flowOf(null)
        val isOwner = a.membership.role == MemberRole.OWNER
        staffRepo.members(a.membership.salonId).map { list ->
            val active = list.filter { it.status == MemberStatus.ACTIVE && (isOwner || it.uid == uid) }
            WalkInContext(a.membership.salonId, isOwner, uid, active)
        }.catch { emit(WalkInContext(a.membership.salonId, isOwner, uid, emptyList())) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _form = MutableStateFlow(WalkInForm(staffUid = route.staffUid, date = today))
    val form: StateFlow<WalkInForm> = _form.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _done = MutableStateFlow<String?>(null)
    val done: StateFlow<String?> = _done.asStateFlow()

    val servicesOfStaff: StateFlow<List<StaffService>> = combineStaff()

    private fun combineStaff(): StateFlow<List<StaffService>> =
        combine(_form.map { it.staffUid }.distinctUntilChanged(), context.map { it?.salonId }.distinctUntilChanged()) { a, b -> a to b }
            .flatMapLatest { (staffUid, salonId) ->
                if (staffUid == null || salonId == null) flowOf(emptyList()) else services.observeActiveOf(salonId, staffUid).catch { emit(emptyList()) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectStaff(uid: String) = _form.update { it.copy(staffUid = uid, serviceIds = emptySet()) }
    fun toggleService(id: String) = _form.update { f -> f.copy(serviceIds = if (id in f.serviceIds) f.serviceIds - id else f.serviceIds + id) }
    fun setDate(d: LocalDate) = _form.update { it.copy(date = d) }
    fun setTime(v: String) = _form.update { it.copy(time = v.filter { c -> c.isDigit() || c == ':' }.take(5), timeError = null) }
    fun setName(v: String) = _form.update { it.copy(customerName = v, nameError = null) }
    fun setPhone(v: String) = _form.update { it.copy(customerPhone = v, phoneError = null) }
    fun messageShown() { _message.value = null }

    /** "14:30" / "1430" → LocalTime (5 dakikalık ızgara). */
    private fun parseTime(raw: String): LocalTime? {
        val digits = raw.filter { it.isDigit() }
        if (digits.length != 4) return null
        val h = digits.take(2).toInt()
        val m = digits.takeLast(2).toInt()
        if (h > 23 || m > 59 || m % 5 != 0) return null
        return LocalTime.of(h, m)
    }

    fun submit() {
        val f = _form.value
        val c = context.value ?: return
        if (f.submitting) return
        val time = parseTime(f.time)
        val nameOk = Regex("^\\p{L}[\\p{L} .'-]{0,48}[\\p{L}.]$").matches(f.customerName.trim())
        val phone = f.customerPhone.trim().takeIf { it.isNotEmpty() }
        val phoneOk = phone == null || PhoneNumbers.normalizeTr(phone) != null
        _form.update {
            it.copy(
                timeError = if (time == null) "Saat SS:DD biçiminde ve 5 dakikanın katı olmalı (ör. 14:30)." else null,
                nameError = if (!nameOk) "Müşterinin adını ve soyadını yazın." else null,
                phoneError = if (!phoneOk) "Geçerli bir telefon numarası girin veya boş bırakın." else null,
            )
        }
        val staffUid = f.staffUid
        if (time == null || !nameOk || !phoneOk) return
        if (staffUid == null || f.serviceIds.isEmpty()) { _message.value = "Uzman ve en az bir hizmet seçin."; return }
        val startAt = f.date.atTime(time).atZone(BusinessTime.ZONE).toInstant()
        _form.update { it.copy(submitting = true) }
        viewModelScope.launch {
            runCatching {
                appointments.createWalkIn(c.salonId, staffUid, f.serviceIds.toList(), startAt, f.customerName.trim(), phone, key)
            }.onSuccess { _done.value = it }
                .onFailure { _message.value = ErrorMessages.text(it) }
            _form.update { it.copy(submitting = false) }
        }
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("d MMM EEE", BusinessTime.LOCALE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalkInScreen(onBack: () -> Unit, onCreated: (String) -> Unit, viewModel: WalkInViewModel = hiltViewModel()) {
    val ctx by viewModel.context.collectAsStateWithLifecycle()
    val f by viewModel.form.collectAsStateWithLifecycle()
    val svcs by viewModel.servicesOfStaff.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }
    LaunchedEffect(done) { done?.let(onCreated) }
    LaunchedEffect(ctx) {
        val c = ctx
        if (c != null && f.staffUid == null) (c.staff.firstOrNull { it.uid == c.myUid } ?: c.staff.firstOrNull())?.let { viewModel.selectStaff(it.uid) }
    }

    Scaffold(
        topBar = { KcTopBar("Elden Randevu Ekle", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                KcPrimaryButton("Randevuyu Kaydet", viewModel::submit, Modifier.navigationBarsPadding().padding(Spacing.l), loading = f.submitting, leadingIcon = Icons.Outlined.PersonAdd)
            }
        },
    ) { padding ->
        val c = ctx
        if (c == null) { SkeletonCardList(3, Modifier.padding(padding)); return@Scaffold }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            InfoBanner("Telefonla arayan veya salona gelen, uygulaması olmayan müşteriler için. Kayıt doğrudan onaylanır ve uzmanın takviminde o saat kapanır; uygulamadan aynı saate randevu verilmez.")
            if (c.isOwner) {
                KcDropdown("Uzman", c.staff, c.staff.firstOrNull { it.uid == f.staffUid }, { it.fullName }, { m -> viewModel.selectStaff(m.uid) })
            } else {
                Text("Uzman: ${c.staff.firstOrNull()?.fullName ?: "Siz"}", style = MaterialTheme.typography.titleMedium)
            }
            Text("Hizmetler", style = MaterialTheme.typography.titleMedium)
            if (svcs.isEmpty()) {
                EmptyState("Aktif hizmet yok", "Seçilen uzmanın aktif hizmeti olmadan randevu açılamaz. Hizmetlerim ekranından ekleyin.")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    svcs.forEach { s ->
                        FilterChip(selected = s.id in f.serviceIds, onClick = { viewModel.toggleService(s.id) }, label = { Text("${s.name} · ${formatDuration(s.durationMinutes)} · ${s.price.formatTry()}") })
                    }
                }
            }
            Text("Tarih", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                (0L until 14L).map { viewModel.today.plusDays(it) }.forEach { d ->
                    FilterChip(selected = d == f.date, onClick = { viewModel.setDate(d) }, label = { Text(if (d == viewModel.today) "Bugün" else d.format(dayFmt)) })
                }
            }
            KcTextField(f.time, viewModel::setTime, "Başlangıç saati (SS:DD)", error = f.timeError, keyboardType = KeyboardType.Number, supporting = "Ör. 14:30. Çalışma saatleri dışı da girilebilir; çakışma kontrol edilir.")
            KcTextField(f.customerName, viewModel::setName, "Müşteri adı soyadı", error = f.nameError, capitalization = KeyboardCapitalization.Words)
            KcTextField(f.customerPhone, viewModel::setPhone, "Müşteri telefonu (isteğe bağlı)", error = f.phoneError, keyboardType = KeyboardType.Phone, supporting = "Hatırlatma için arayabilmeniz amacıyla; müşteriye SMS gönderilmez.")
        }
    }
}
