package com.kuaforumcepte.app.feature.platform

import android.Manifest
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.salon.GeoPoint
import com.kuaforumcepte.app.data.salon.LocationHelper
import com.kuaforumcepte.app.data.salon.NearbySalon
import com.kuaforumcepte.app.data.salon.SalonRepository
import com.kuaforumcepte.app.feature.salon.MapIntents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class NearbyUi(
    val radiusKm: Int = 5,
    val location: GeoPoint? = null,
    val loading: Boolean = false,
    val results: List<NearbySalon>? = null,
    val error: String? = null,
    val permissionDenied: Boolean = false,
)

@HiltViewModel
class NearbySalonsViewModel @Inject constructor(private val salons: SalonRepository, private val location: LocationHelper) : ViewModel() {
    private val _ui = MutableStateFlow(NearbyUi())
    val ui: StateFlow<NearbyUi> = _ui.asStateFlow()

    fun onPermissionResult(granted: Boolean) {
        if (!granted) { _ui.update { it.copy(permissionDenied = true) }; return }
        _ui.update { it.copy(permissionDenied = false, loading = true, error = null) }
        viewModelScope.launch {
            runCatching { location.currentLocation() }
                .onSuccess { p -> _ui.update { it.copy(location = p) }; search() }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = ErrorMessages.text(e)) } }
        }
    }

    fun setRadius(km: Int) { _ui.update { it.copy(radiusKm = km) }; if (_ui.value.location != null) search() }

    private fun search() {
        val p = _ui.value.location ?: return
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { salons.nearby(p.lat, p.lng, _ui.value.radiusKm, null) }
                .onSuccess { list -> _ui.update { it.copy(loading = false, results = list) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = ErrorMessages.text(e)) } }
        }
    }
}

/** Konuma göre yakındaki onaylı salonlar; mesafe sırasıyla. Konum yalnızca bu arama için kullanılır, saklanmaz. */
@Composable
fun NearbySalonsScreen(onBack: () -> Unit, onOpenSalon: (String) -> Unit, viewModel: NearbySalonsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { g -> viewModel.onPermissionResult(g.values.any { it }) }
    val ask = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    LaunchedEffect(Unit) { if (ui.location == null) ask() }

    Scaffold(topBar = { KcTopBar("Yakınımdaki Salonlar", onBack, actions = { IconButton(onClick = ask) { Icon(Icons.Outlined.MyLocation, "Konumu yenile") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                listOf(2, 5, 10, 25, 50).forEach { km -> FilterChip(selected = ui.radiusKm == km, onClick = { viewModel.setRadius(km) }, label = { Text("$km km") }) }
            }
            when {
                ui.permissionDenied -> EmptyState(
                    "Konum izni gerekli", "Yakındaki salonları bulmak için konum izni verin. İl/ilçe ile aramak için Salonlar sekmesini kullanabilirsiniz.",
                    icon = Icons.Outlined.NearMe, actionLabel = "İzin ver", onAction = ask,
                )
                ui.loading -> SkeletonCardList(4)
                ui.error != null -> ErrorState(ui.error!!, onRetry = ask)
                ui.results == null -> EmptyState("Konum alınıyor", icon = Icons.Outlined.NearMe)
                ui.results!!.isEmpty() -> EmptyState(
                    "${ui.radiusKm} km içinde salon bulunamadı", "Yarıçapı büyütmeyi deneyin. Yalnızca konumunu işaretlemiş, onaylı salonlar listelenir.",
                    icon = Icons.Outlined.NearMe,
                )
                else -> LazyColumn(contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    items(ui.results!!, key = { it.salonId }) { s ->
                        Surface(onClick = { onOpenSalon(s.salonId) }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                            ListItem(
                                headlineContent = { Text(s.name, style = MaterialTheme.typography.titleMedium) },
                                supportingContent = { Text("${s.type?.label ?: ""} · ${s.district} / ${s.city}\n★ ${s.rating.label()} (${s.rating.count})") },
                                overlineContent = { Text(String.format(Locale.forLanguageTag("tr-TR"), "%.1f km", s.distanceKm)) },
                                trailingContent = {
                                    IconButton(onClick = {
                                        try { context.startActivity(MapIntents.directions(s.location.lat, s.location.lng, "${s.addressLine}, ${s.district} / ${s.city}")) } catch (_: ActivityNotFoundException) { }
                                    }) { Icon(Icons.Outlined.Directions, "Yol tarifi") }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
