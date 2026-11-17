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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
class SalonReviewsViewModel @Inject constructor(repo: ReviewRepository) : ViewModel() {
    private val salonId = MutableStateFlow<String?>(null)
    val reviews: StateFlow<List<Review>?> = salonId.filterNotNull().flatMapLatest { repo.observeSalon(it) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun bind(id: String) { salonId.value = id }
}

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", BusinessTime.LOCALE).withZone(BusinessTime.ZONE)

/** Salon puanı (salon değerlendirmelerinin ortalaması) ve son yorumlar. */
@Composable
fun SalonReviewsSection(salonId: String, rating: Rating, viewModel: SalonReviewsViewModel = hiltViewModel(key = "reviews_$salonId")) {
    LaunchedEffect(salonId) { viewModel.bind(salonId) }
    val reviews by viewModel.reviews.collectAsStateWithLifecycle()
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
                }
            }
        }
    }
}
