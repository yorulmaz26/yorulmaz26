package com.kuaforumcepte.app.feature.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcDropdown
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.theme.KcTheme
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.admin.AdminRepository
import com.kuaforumcepte.app.data.admin.PlatformAdmin
import com.kuaforumcepte.app.data.reviews.Review
import com.kuaforumcepte.app.data.reviews.ReviewReport
import com.kuaforumcepte.app.data.reviews.ReviewRepository
import com.kuaforumcepte.app.data.salon.SalonProfile
import com.kuaforumcepte.app.data.salon.SalonRepository
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.app.feature.reviews.StarRow
import com.kuaforumcepte.domain.phone.PhoneNumbers
import com.kuaforumcepte.domain.text.SearchKey
import com.kuaforumcepte.domain.time.BusinessTime
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
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ModerationViewModel @Inject constructor(
    private val reviews: ReviewRepository,
    private val session: SessionRepository,
    private val salons: SalonRepository,
    private val admin: AdminRepository,
) : ViewModel() {
    private val _isAdmin = MutableStateFlow<Boolean?>(null)
    val isAdmin: StateFlow<Boolean?> = _isAdmin.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()
    fun consumeMessage() { _message.value = null }

    init { viewModelScope.launch { _isAdmin.value = runCatching { session.isPlatformAdmin(forceRefresh = true) }.getOrDefault(false) } }

    private fun <T> live(f: kotlinx.coroutines.flow.Flow<T>, initial: T): StateFlow<T> =
        _isAdmin.flatMapLatest { if (it == true) f else flowOf(initial) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val pendingSalons: StateFlow<List<SalonProfile>?> = live(salons.observePendingVerification().catch { emit(emptyList()) }, null)
    val allSalons: StateFlow<List<SalonProfile>?> = live(admin.observeAllSalons().catch { emit(emptyList()) }, null)
    val flagged: StateFlow<List<Review>?> = live(reviews.observeFlagged().catch { emit(emptyList()) }, null)
    val admins: StateFlow<List<PlatformAdmin>> = live(admin.observeAdmins(), emptyList())

    private val selectedSalon = MutableStateFlow<String?>(null)
    val selectedSalonId: StateFlow<String?> = selectedSalon.asStateFlow()
    val salonReviews: StateFlow<List<Review>?> = selectedSalon.flatMapLatest { id ->
        if (id == null) flowOf(null) else reviews.observeSalonAll(id).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun selectSalon(id: String?) { selectedSalon.value = id }

    fun reports(reviewId: String) = reviews.observeReports(reviewId)

    private fun run(key: String, success: String, block: suspend () -> Unit) {
        if (_busy.value != null) return
        _busy.value = key
        viewModelScope.launch {
            runCatching { block() }.onSuccess { _message.value = success }.onFailure { _message.value = ErrorMessages.text(it) }
            _busy.value = null
        }
    }

    fun reviewSalon(salonId: String, approve: Boolean, note: String) {
        if (!approve && note.isBlank()) { _message.value = "Reddetmek için gerekçe yazın (sahibe iletilir)."; return }
        run(salonId, if (approve) "Salon onaylandı; artık aramada görünüyor." else "Salon reddedildi; sahibe gerekçe iletildi.") { salons.reviewSalon(salonId, approve, note) }
    }

    fun removeSalon(salonId: String, reason: String) =
        run(salonId, "Salon kaldırıldı; aktif randevular iptal edildi ve taraflar bilgilendirildi.") { admin.removeSalon(salonId, reason) }

    /** action: HIDE (sil) | RESTORE (geri yükle) | DISMISS (şikâyeti reddet) */
    fun moderate(r: Review, action: String, note: String) = run(
        r.id,
        when (action) {
            "HIDE" -> "Yorum silindi ve puanlardan çıkarıldı."
            "RESTORE" -> "Yorum geri yüklendi; puanlar yeniden eklendi."
            else -> "Şikâyet reddedildi; yorum yayında kalıyor."
        },
    ) { reviews.moderate(r.id, action, note) }

    fun signOut(onDone: () -> Unit) = viewModelScope.launch { session.signOut(); onDone() }

    fun setAdmin(phone: String, grant: Boolean) {
        val e164 = PhoneNumbers.normalizeTr(phone, true)
        if (e164 == null) { _message.value = "Geçerli bir cep telefonu girin (5XX XXX XX XX)."; return }
        run(e164, if (grant) "Yönetici eklendi. Kişi çıkış yapıp yeniden girince paneli görür." else "Yönetici yetkisi kaldırıldı.") { admin.setAdmin(e164, grant) }
    }
}

private enum class AdminTab(val label: String) { APPROVAL("Salon onayı"), SALONS("Salonlar"), REVIEWS("Yorumlar"), REPORTS("Şikâyetler"), ADMINS("Yöneticiler") }

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", BusinessTime.LOCALE).withZone(BusinessTime.ZONE)

/**
 * Moderatör (platform yöneticisi) paneli: salon onayı, salon kaldırma, salon bazında yorum silme,
 * şikâyet kuyruğu ve yönetici ekleme. asHome: girişte doğrudan açıldığında (geri tuşu yerine rol geçişi/çıkış).
 */
@Composable
fun ModerationScreen(
    onBack: (() -> Unit)?,
    onContinueAs: ((String) -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
    viewModel: ModerationViewModel = hiltViewModel(),
) {
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    Scaffold(
        topBar = {
            KcTopBar("Moderatör Paneli", onBack, actions = {
                if (onContinueAs != null || onSignOut != null) {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.AdminPanelSettings, "Hesap seçenekleri") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        onContinueAs?.let { go ->
                            DropdownMenuItem(text = { Text("Müşteri olarak devam et") }, leadingIcon = { Icon(Icons.Outlined.Person, null) }, onClick = { menu = false; go("CUSTOMER") })
                            DropdownMenuItem(text = { Text("Kuaför paneline geç") }, leadingIcon = { Icon(Icons.Outlined.ContentCut, null) }, onClick = { menu = false; go("BUSINESS") })
                        }
                        onSignOut?.let { out ->
                            DropdownMenuItem(text = { Text("Çıkış yap") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) }, onClick = { menu = false; viewModel.signOut(out) })
                        }
                    }
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (isAdmin) {
            null -> SkeletonCardList(2, Modifier.padding(padding))
            false -> EmptyState("Bu bölüm yalnızca moderatörlere açıktır", modifier = Modifier.padding(padding))
            true -> Column(Modifier.fillMaxSize().padding(padding)) {
                PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = Spacing.s) {
                    AdminTab.entries.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t.label) }) }
                }
                when (AdminTab.entries[tab]) {
                    AdminTab.APPROVAL -> ApprovalTab(viewModel)
                    AdminTab.SALONS -> SalonsTab(viewModel)
                    AdminTab.REVIEWS -> ReviewsTab(viewModel)
                    AdminTab.REPORTS -> ReportsTab(viewModel)
                    AdminTab.ADMINS -> AdminsTab(viewModel)
                }
            }
        }
    }
}

@Composable
private fun ApprovalTab(vm: ModerationViewModel) {
    val list by vm.pendingSalons.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val ps = list
    when {
        ps == null -> SkeletonCardList(2)
        ps.isEmpty() -> EmptyState(icon = Icons.Outlined.VerifiedUser, title = "Onay bekleyen salon yok", message = "Yeni kaydolan salonlar onaylanana kadar müşteri aramasında görünmez.")
        else -> LazyColumn(contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            items(ps, key = { it.id }) { salon ->
                var note by remember(salon.id) { mutableStateOf("") }
                SalonCard(salon) {
                    KcTextField(note, { note = it }, "Not / ret gerekçesi", maxLength = 300, singleLine = false)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        TextButton(onClick = { vm.reviewSalon(salon.id, false, note) }, enabled = busy == null) { Text("Reddet", color = MaterialTheme.colorScheme.error) }
                        TextButton(onClick = { vm.reviewSalon(salon.id, true, note) }, enabled = busy == null) { Text("Onayla") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SalonsTab(vm: ModerationViewModel) {
    val list by vm.allSalons.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var removing by remember { mutableStateOf<SalonProfile?>(null) }
    val all = list
    Column {
        KcTextField(query, { query = it }, "Salon adı, il, ilçe veya kod ara", modifier = Modifier.padding(Spacing.screen))
        when {
            all == null -> SkeletonCardList(3)
            else -> {
                val key = SearchKey.of(query)
                val shown = all.filter { s -> key.isEmpty() || listOf(s.name, s.city, s.district, s.code).any { SearchKey.of(it).contains(key) } }
                if (shown.isEmpty()) EmptyState("Salon bulunamadı")
                LazyColumn(contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    items(shown, key = { it.id }) { salon ->
                        SalonCard(salon) {
                            if (!salon.closed) {
                                TextButton(onClick = { removing = salon }, enabled = busy == null) { Text("Salonu kaldır", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
    removing?.let { s ->
        var reason by remember(s.id) { mutableStateOf("") }
        var confirm by remember(s.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("${s.name} kaldırılsın mı?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(
                        "Salon kalıcı olarak kapanır ve aramadan kalkar; tüm aktif randevular (işlemdekiler dahil) iptal edilir, müşteri ve uzmanlar bilgilendirilir. " +
                            "Sahip salonu yeniden açamaz. Finans ve değerlendirme kayıtları saklanır.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    KcTextField(reason, { reason = it }, "Gerekçe (sahibe iletilir)", singleLine = false, maxLength = 300)
                    KcTextField(confirm, { confirm = it.uppercase() }, "Onay için KALDIR yazın")
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.removeSalon(s.id, reason); removing = null }, enabled = confirm.trim() == "KALDIR" && reason.trim().length >= 3) {
                    Text("Kaldır", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun ReviewsTab(vm: ModerationViewModel) {
    val salons by vm.allSalons.collectAsStateWithLifecycle()
    val selectedId by vm.selectedSalonId.collectAsStateWithLifecycle()
    val reviews by vm.salonReviews.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Review?>(null) }
    val options = salons.orEmpty()
    Column {
        KcDropdown(
            "Salon seçin", options, options.firstOrNull { it.id == selectedId },
            { "${it.name} · ${it.district}/${it.city}" + if (it.closed) " (kapalı)" else "" }, { vm.selectSalon(it.id) },
            modifier = Modifier.padding(Spacing.screen),
        )
        val list = reviews
        when {
            selectedId == null -> EmptyState("Yorumlarını görmek için bir salon seçin")
            list == null -> SkeletonCardList(2)
            list.isEmpty() -> EmptyState("Bu salonda yorum yok")
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                items(list, key = { it.id }) { r ->
                    ReviewCard(r) {
                        if (r.hidden) {
                            Text("Silinmiş (yayında değil)", style = MaterialTheme.typography.labelMedium, color = KcTheme.colors.cancel)
                            TextButton(onClick = { vm.moderate(r, "RESTORE", "") }, enabled = busy == null) { Text("Geri yükle") }
                        } else {
                            TextButton(onClick = { deleting = r }, enabled = busy == null) { Text("Yorumu sil", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { r ->
        var note by remember(r.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Yorum silinsin mi?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text("Yorum yayından kalkar ve puanı salon ile uzman ortalamasından çıkarılır. Gerekirse geri yükleyebilirsiniz.", style = MaterialTheme.typography.bodySmall)
                    KcTextField(note, { note = it }, "Not (isteğe bağlı)", maxLength = 300)
                }
            },
            confirmButton = { TextButton(onClick = { vm.moderate(r, "HIDE", note); deleting = null }) { Text("Sil", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun ReportsTab(vm: ModerationViewModel) {
    val list by vm.flagged.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val l = list
    when {
        l == null -> SkeletonCardList(2)
        l.isEmpty() -> EmptyState(icon = Icons.Outlined.VerifiedUser, title = "İncelenecek şikâyet yok", message = "3 farklı kişinin şikâyet ettiği veya kişisel veri içerdiği bildirilen yorumlar burada görünür.")
        else -> LazyColumn(contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            items(l, key = { it.id }) { r ->
                val reports by remember(r.id) { vm.reports(r.id) }.collectAsStateWithLifecycle(initialValue = emptyList<ReviewReport>())
                ReviewCard(r) {
                    reports.forEach { rep ->
                        Text("• ${rep.reason?.label ?: "-"}" + if (rep.note.isNotBlank()) ": ${rep.note}" else "", style = MaterialTheme.typography.bodySmall, color = KcTheme.colors.cancel)
                    }
                    Row {
                        TextButton(onClick = { vm.moderate(r, "DISMISS", "") }, enabled = busy == null) { Text("Şikâyeti reddet") }
                        TextButton(onClick = { vm.moderate(r, "HIDE", "Şikâyet üzerine") }, enabled = busy == null) { Text("Yorumu sil", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminsTab(vm: ModerationViewModel) {
    val admins by vm.admins.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var phone by rememberSaveable { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        item {
            InfoBanner("Moderatör ekleyin: kişi uygulamaya bu numarayla en az bir kez giriş yapmış olmalı. Yetki, kişi çıkış yapıp yeniden girince etkinleşir.")
        }
        item {
            KcTextField(phone, { phone = it }, "Cep telefonu (5XX XXX XX XX)", keyboardType = KeyboardType.Phone, prefix = "+90 ")
        }
        item { KcPrimaryButton("Moderatör olarak ekle", { vm.setAdmin(phone, true) }, loading = busy != null, leadingIcon = Icons.Outlined.AdminPanelSettings) }
        item { Text("Mevcut moderatörler", style = MaterialTheme.typography.titleMedium) }
        if (admins.isEmpty()) item { Text("Liste boş (yalnızca uygulamadan veya GitHub \"Yönetici Ata\" ile eklenenler görünür).", style = MaterialTheme.typography.bodySmall) }
        items(admins, key = { it.uid }) { a ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                    Text(PhoneNumbers.formatForDisplay(a.phoneE164).ifBlank { a.uid }, Modifier.weight(1f))
                    TextButton(onClick = { vm.setAdmin(a.phoneE164, false) }, enabled = busy == null && a.phoneE164.isNotBlank()) {
                        Text("Yetkiyi kaldır", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun SalonCard(salon: SalonProfile, actions: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(salon.name, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    salon.type?.label, "Kod ${salon.code}",
                    when {
                        salon.closed -> "Kapalı"
                        salon.verified -> "Onaylı"
                        salon.verificationStatus == "REJECTED" -> "Reddedildi"
                        else -> "Onay bekliyor"
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = if (salon.closed) KcTheme.colors.cancel else MaterialTheme.colorScheme.primary,
            )
            Text(salon.fullAddress, style = MaterialTheme.typography.bodySmall)
            Text("Sahip: ${salon.ownerDisplayName} · ${PhoneNumbers.formatForDisplay(salon.businessPhoneE164)}", style = MaterialTheme.typography.bodySmall)
            Text("Puan ${salon.rating.label()} (${salon.rating.count})", style = MaterialTheme.typography.bodySmall)
            actions()
        }
    }
}

@Composable
private fun ReviewCard(r: Review, actions: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${r.author} · ${r.staffName}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                r.createdAt?.let { Text(dateFmt.format(it), style = MaterialTheme.typography.labelSmall) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Salon ", style = MaterialTheme.typography.labelMedium); StarRow(r.salonRating)
                Text("  Uzman ", style = MaterialTheme.typography.labelMedium); StarRow(r.staffRating)
            }
            Text(r.comment.ifBlank { "(yorum metni yok)" }, style = MaterialTheme.typography.bodyMedium)
            if (r.reportCount > 0) Text("${r.reportCount} şikâyet", style = MaterialTheme.typography.labelSmall, color = KcTheme.colors.cancel)
            actions()
        }
    }
}
