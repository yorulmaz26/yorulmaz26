package com.kuaforumcepte.app.feature.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.data.reviews.ReportReason
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.reviews.Review
import com.kuaforumcepte.app.data.reviews.ReviewRepository
import com.kuaforumcepte.domain.model.Rating
import com.kuaforumcepte.domain.time.BusinessTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Erişilebilir yıldız seçici: her yıldız ayrı seçilebilir öğe, ekran okuyucu "3 / 5 yıldız" okur. */
@Composable
fun StarPicker(label: String, value: Int, onChange: (Int) -> Unit, size: Dp = 40.dp) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.selectableGroup()) {
            (1..5).forEach { i ->
                Icon(
                    if (i <= value) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(size + 8.dp).padding(4.dp)
                        .selectable(selected = value == i, role = Role.RadioButton, onClick = { onChange(i) })
                        .semantics { contentDescription = "$label: $i / 5 yıldız" },
                )
            }
        }
    }
}

@Composable
fun StarRow(value: Int, size: Dp = 16.dp) {
    Row(Modifier.semantics { contentDescription = "$value / 5 yıldız" }) {
        (1..5).forEach { i -> Icon(if (i <= value) Icons.Filled.Star else Icons.Outlined.StarOutline, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(size)) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SalonReviewsViewModel @Inject constructor(
    private val repo: ReviewRepository,
    private val session: com.kuaforumcepte.app.data.session.SessionRepository,
) : ViewModel() {
    fun isOwner(ownerUid: String?): Boolean = ownerUid != null && ownerUid == session.currentUid

    fun reply(review: Review, text: String) = viewModelScope.launch {
        runCatching { repo.reply(review.id, text) }
            .onSuccess { _message.value = if (text.isBlank()) "Yanıt kaldırıldı." else "Yanıtınız yayınlandı; müşteriye bildirildi." }
            .onFailure { _message.value = (ErrorMessages.of(it) as UiText.Plain).value }
    }

    private val salonId = MutableStateFlow<String?>(null)
    val reviews: StateFlow<List<Review>?> = salonId.filterNotNull().flatMapLatest { repo.observeSalon(it) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun bind(id: String) { salonId.value = id }
    fun consumeMessage() { _message.value = null }

    fun report(review: Review, reason: ReportReason, note: String) = viewModelScope.launch {
        runCatching { repo.report(review.id, reason, note) }
            .onSuccess { _message.value = "Şikâyetiniz alındı. Değerlendirme incelenecek." }
            .onFailure { _message.value = (ErrorMessages.of(it) as UiText.Plain).value }
    }
}

/** Yorum şikâyeti: neden + isteğe bağlı açıklama. */
@Composable
fun ReportReviewDialog(onSubmit: (ReportReason, String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Değerlendirmeyi şikâyet et") },
        text = {
            Column(Modifier.selectableGroup()) {
                ReportReason.entries.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = reason == r, role = Role.RadioButton, onClick = { reason = r }).padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = null)
                        Text(r.label, Modifier.padding(start = Spacing.s))
                    }
                }
                KcTextField(note, { note = it }, "Açıklama (isteğe bağlı)", maxLength = 300, singleLine = false, minLines = 2)
                Text("Şikâyetler platform yöneticisi tarafından incelenir; kuralları ihlal eden yorumlar kaldırılır ve puana dahil edilmez.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { reason?.let { onSubmit(it, note) } }, enabled = reason != null) { Text("Gönder") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", BusinessTime.LOCALE).withZone(BusinessTime.ZONE)

/** Salon puanı (salon değerlendirmelerinin ortalaması) ve son yorumlar. */
@Composable
fun SalonReviewsSection(
    salonId: String,
    rating: Rating,
    ownerUid: String? = null,
    viewModel: SalonReviewsViewModel = hiltViewModel(key = "reviews_$salonId"),
) {
    val canReply = viewModel.isOwner(ownerUid)
    var replying by remember { mutableStateOf<Review?>(null) }
    var replyText by remember { mutableStateOf("") }
    replying?.let { r ->
        AlertDialog(
            onDismissRequest = { replying = null },
            title = { Text("Yoruma yanıt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text("Yanıtınız herkese açık görünür. Kişisel veri (telefon, ad soyad) yazmayın.", style = MaterialTheme.typography.bodySmall)
                    KcTextField(replyText, { replyText = it }, "Yanıtınız", singleLine = false, minLines = 3, maxLength = 500)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.reply(r, replyText); replying = null }, enabled = replyText.isNotBlank()) { Text("Yayınla") } },
            dismissButton = {
                Row {
                    if (r.ownerReply != null) TextButton(onClick = { viewModel.reply(r, ""); replying = null }) { Text("Yanıtı kaldır", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { replying = null }) { Text("Vazgeç") }
                }
            },
        )
    }
    LaunchedEffect(salonId) { viewModel.bind(salonId) }
    val reviews by viewModel.reviews.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var reporting by remember { mutableStateOf<Review?>(null) }
    LaunchedEffect(message) { message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.consumeMessage() } }
    reporting?.let { r -> ReportReviewDialog(onSubmit = { reason, note -> reporting = null; viewModel.report(r, reason, note) }, onDismiss = { reporting = null }) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("Değerlendirmeler", style = MaterialTheme.typography.titleMedium)
        Text(
            if (rating.count == 0L) "Henüz değerlendirme yok (Yeni)."
            else "Salon puanı ${rating.label()} · ${rating.count} doğrulanmış değerlendirme",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Puanlar yalnızca tamamlanmış randevuların müşterilerinden gelir. Salon puanı salon değerlendirmelerinin, uzman puanı uzman değerlendirmelerinin ortalamasıdır; hizmet puanı ayrı gösterilir.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        reviews.orEmpty().forEach { r ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.author, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        r.createdAt?.let { Text(dateFmt.format(it), style = MaterialTheme.typography.labelSmall) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Salon ", style = MaterialTheme.typography.labelMedium); StarRow(r.salonRating) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("${r.staffName} ", style = MaterialTheme.typography.labelMedium); StarRow(r.staffRating) }
                    r.serviceRating?.let { s -> Row(verticalAlignment = Alignment.CenterVertically) { Text("Hizmet ", style = MaterialTheme.typography.labelMedium); StarRow(s) } }
                    if (r.comment.isNotBlank()) Text(r.comment, style = MaterialTheme.typography.bodyMedium)
                    if (r.serviceNames.isNotEmpty()) Text(r.serviceNames.joinToString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    r.ownerReply?.let { reply ->
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Column(Modifier.fillMaxWidth().padding(Spacing.s)) {
                                Text("Salonun yanıtı", style = MaterialTheme.typography.labelMedium)
                                Text(reply, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Row {
                        if (canReply) TextButton(onClick = { replyText = r.ownerReply.orEmpty(); replying = r }) {
                            Text(if (r.ownerReply == null) "Yanıtla" else "Yanıtı düzenle", style = MaterialTheme.typography.labelMedium)
                        }
                        if (!canReply) TextButton(onClick = { reporting = r }) { Text("Şikâyet et", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
    }
}
