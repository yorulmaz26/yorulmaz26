package com.kuaforumcepte.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.KcDropdown
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.booking.SalonSort
import com.kuaforumcepte.app.data.salon.SalonProfile
import com.kuaforumcepte.domain.model.SalonType

/** "Kayıtlı Kuaförlerimiz": arama (debounce), sıralama, tür/il/ilçe filtresi, sonsuz kaydırma. */
@Composable
fun SalonListContent(
    onOpenSalon: (String) -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SalonListViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 4 } }
    LaunchedEffect(nearEnd, ui.items.size) { if (nearEnd) viewModel.loadMore() }

    LazyColumn(
        modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item { header() }
        item { Text("Kayıtlı Kuaförlerimiz", style = MaterialTheme.typography.headlineSmall) }
        item {
            OutlinedTextField(
                value = ui.searchInput,
                onValueChange = viewModel::onSearch,
                placeholder = { Text("Salon adına göre ara") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                SalonSort.entries.forEach { s ->
                    FilterChip(
                        selected = ui.query.effectiveSort == s,
                        onClick = { viewModel.onSort(s) },
                        enabled = ui.query.searchKey.isEmpty() || s == SalonSort.NAME,
                        label = { Text(s.label) },
                    )
                }
                FilterChip(
                    selected = showFilters || ui.query.type != null || ui.query.city != null,
                    onClick = { showFilters = !showFilters },
                    label = { Text("Filtre") },
                    leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)) },
                )
            }
        }
        if (ui.query.searchKey.isNotEmpty()) {
            item { Text("Aramada sonuçlar ada göre sıralanır.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (showFilters) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    KcDropdown("Salon türü", listOf<SalonType?>(null) + SalonType.entries, ui.query.type, { it?.label ?: "Tümü" }, viewModel::onType)
                    KcDropdown("İl", listOf(null) + ui.cities, ui.cities.firstOrNull { it.name == ui.query.city }, { it?.name ?: "Tümü" }, viewModel::onCity)
                    KcDropdown("İlçe", listOf<String?>(null) + ui.districts, ui.query.district, { it ?: "Tümü" }, viewModel::onDistrict, enabled = ui.query.city != null)
                    TextButton(onClick = viewModel::clearFilters) { Text("Filtreleri temizle") }
                }
            }
        }
        when {
            ui.loading -> item { SkeletonCardList(4, Modifier.padding(0.dp)) }
            ui.error != null && ui.items.isEmpty() -> item { ErrorState(ui.error!!.asString(), onRetry = viewModel::reload) }
            ui.items.isEmpty() -> item {
                EmptyState(
                    icon = Icons.Outlined.Storefront,
                    title = if (ui.query.searchKey.isNotEmpty()) "“${ui.searchInput}” ile eşleşen salon yok" else "Bu filtrelerle salon bulunamadı",
                    message = "Aramayı veya filtreleri değiştirip tekrar deneyin.",
                )
            }
            else -> {
                items(ui.items, key = { it.id }) { s -> SalonCard(s) { onOpenSalon(s.id) } }
                if (ui.loadingMore) item { Box(Modifier.fillMaxWidth().padding(Spacing.l), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
        }
    }
}

@Composable
private fun SalonCard(s: SalonProfile, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = s.logoUrl, contentDescription = "${s.name} logosu", contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${s.district} / ${s.city}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(s.ownerDisplayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = "Puan ${s.rating.label()}" }) {
                if (s.rating.count > 0) Icon(Icons.Outlined.Star, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Text(s.rating.label(), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
