package com.kuaforumcepte.app.feature.salonsettings

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.ConfirmDialog
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcDropdown
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcSecondaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.feature.business.MenuRow
import com.kuaforumcepte.app.feature.salon.MapIntents
import com.kuaforumcepte.domain.model.SalonType
import java.util.Locale

@Composable
fun ShopSettingsScreen(onBack: () -> Unit, onOpenMedia: () -> Unit, onSalonLeft: (message: String, closed: Boolean) -> Unit, viewModel: ShopSettingsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var transferring by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val finishedMsg = ui.message?.asString()
    LaunchedEffect(ui.finished) { if (ui.finished) onSalonLeft(finishedMsg ?: "İşlem tamamlandı.", ui.closed) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val msg = ui.message?.asString()
    var confirmDiscard by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) viewModel.useCurrentLocation() else viewModel.onLocationPermissionDenied()
    }

    LaunchedEffect(msg) { if (msg != null) { snackbar.showSnackbar(msg); viewModel.consumeMessage() } }
    val goBack: () -> Unit = {
        if (ui.dirty) { confirmDiscard = true } else { onBack() }
    }
    BackHandler(onBack = goBack)

    Scaffold(
        topBar = { KcTopBar("Dükkan Ayarları", goBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (ui.original != null) Surface(tonalElevation = 3.dp) {
                KcPrimaryButton(
                    "Değişiklikleri Kaydet", viewModel::save, Modifier.navigationBarsPadding().padding(Spacing.l),
                    loading = ui.saving, enabled = ui.dirty,
                )
            }
        },
    ) { padding ->
        when {
            ui.loading -> SkeletonCardList(4, Modifier.padding(padding))
            ui.notAuthorized -> EmptyState("Bu bölüm yalnızca salon sahibine açıktır", modifier = Modifier.padding(padding))
            ui.loadError != null -> ErrorState(ui.loadError!!.asString(), Modifier.padding(padding), onRetry = viewModel::load)
            else -> Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                val f = ui.form
                MenuRow(Icons.Outlined.Image, "Logo ve Fotoğraflar", "Logoyu değiştir, galeriyi düzenle", onOpenMedia)
                Spacer(Modifier.height(Spacing.s))
                KcTextField(f.name, viewModel::onName, "Salon adı", error = ui.errors["name"], capitalization = KeyboardCapitalization.Words, maxLength = 80)
                KcDropdown("Salon türü", SalonType.entries.toList(), f.type, { it.label }, viewModel::onType, error = ui.errors["type"])
                KcTextField(f.businessPhone, viewModel::onPhone, "Mekan telefonu", error = ui.errors["businessPhone"], keyboardType = KeyboardType.Phone, supporting = "Müşterilere gösterilir.")
                KcDropdown("İl", ui.cities, ui.cities.firstOrNull { it.name == f.city }, { it.name }, viewModel::onCity, error = ui.errors["city"])
                KcDropdown("İlçe", ui.districts, f.district, { it }, viewModel::onDistrict, enabled = f.city != null, error = ui.errors["district"])
                KcTextField(f.addressLine, viewModel::onAddress, "Açık adres", error = ui.errors["addressLine"], singleLine = false, minLines = 2, maxLength = 300)
                KcTextField(
                    f.description, viewModel::onDescription, "Salon açıklaması", error = ui.errors["description"],
                    singleLine = false, minLines = 3, maxLength = 1000, imeAction = ImeAction.Default,
                )
                HorizontalDivider(Modifier.padding(vertical = Spacing.s))
                Text("Harita konumu", style = MaterialTheme.typography.titleMedium)
                Text(
                    f.location?.let { String.format(Locale.US, "%.6f, %.6f", it.lat, it.lng) } ?: "Konum seçilmedi. Müşteriler yine adresle haritada arayabilir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    KcSecondaryButton(
                        "Bulunduğum yer", {
                            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }, Modifier.weight(1f), loading = ui.locating, leadingIcon = Icons.Outlined.MyLocation,
                    )
                    KcSecondaryButton("Adresten bul", viewModel::findFromAddress, Modifier.weight(1f), enabled = !ui.locating, leadingIcon = Icons.Outlined.TravelExplore)
                }
                f.location?.let { loc ->
                    Row {
                        TextButton(onClick = { context.startActivity(MapIntents.show(loc.lat, loc.lng, f.name)) }) {
                            Icon(Icons.Outlined.Map, null)
                            Text("  Haritada kontrol et")
                        }
                        TextButton(onClick = viewModel::clearLocation) { Text("Konumu kaldır", color = MaterialTheme.colorScheme.error) }
                    }
                }
                InfoBanner("“Bulunduğum yer” için salondayken kullanın. Konum yalnızca salon sayfasındaki “Haritada Göster” için kullanılır; kişisel konumunuz saklanmaz.")
                Text("Salon kodu: ${ui.salonCode} (değiştirilemez)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.padding(vertical = Spacing.s))
                Text("Sahiplik ve kapatma", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Salonu aktif bir uzmanınıza devredebilir veya kalıcı olarak kapatabilirsiniz. Salon sahibi hesabını silmek için önce bunlardan birini yapmanız gerekir.",
                    style = MaterialTheme.typography.bodySmall,
                )
                KcSecondaryButton("Salonu Devret", { transferring = true }, enabled = ui.transferTargets.isNotEmpty() && !ui.lifecycleBusy)
                if (ui.transferTargets.isEmpty()) Text("Devretmek için salonda en az bir aktif uzman olmalı.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { closing = true }, enabled = !ui.lifecycleBusy) { Text("Salonu Kalıcı Olarak Kapat", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(Spacing.l))
            }
        }
    }

    if (transferring) {
        var target by remember { mutableStateOf<com.kuaforumcepte.app.data.staff.StaffMember?>(null) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { transferring = false },
            title = { Text("Sahipliği devret") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("Yeni sahip salon ayarlarını, personeli ve tüm raporları yönetir. Siz aynı salonda uzman olarak devam edersiniz. Bu işlemi yalnızca yeni sahip geri devredebilir.", style = MaterialTheme.typography.bodySmall)
                    ui.transferTargets.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(selected = target?.uid == m.uid, onClick = { target = m })
                            Text("${m.fullName} · ${m.specialty}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { target?.let { viewModel.transferTo(it) }; transferring = false }, enabled = target != null) { Text("Devret", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { transferring = false }) { Text("Vazgeç") } },
        )
    }
    if (closing) {
        var typed by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { closing = false },
            title = { Text("Salon kalıcı olarak kapatılsın mı?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(
                        "Gelecekteki tüm bekleyen ve onaylı randevular gerekçenizle iptal edilir ve müşterilere bildirilir. Personel üyelikleri sona erer, salon listeden kalkar. " +
                            "Geçmiş işlem ve finans kayıtları yasal saklama için korunur. Bu işlem geri alınamaz.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    KcTextField(reason, { reason = it }, "Gerekçe (müşterilere gösterilir)", maxLength = 300)
                    KcTextField(typed, { typed = it }, "Onaylamak için KAPAT yazın", capitalization = KeyboardCapitalization.Characters)
                }
            },
            confirmButton = {
                TextButton(onClick = { closing = false; viewModel.closeSalon(reason) }, enabled = typed.trim() == "KAPAT" && reason.trim().length >= 3) {
                    Text("Kapat", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { closing = false }) { Text("Vazgeç") } },
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = "Kaydedilmemiş değişiklikler",
            message = "Yaptığınız değişiklikler kaydedilmedi. Çıkarsanız kaybolacak.",
            confirmLabel = "Kaydetmeden Çık",
            destructive = true,
            onConfirm = { confirmDiscard = false; onBack() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

