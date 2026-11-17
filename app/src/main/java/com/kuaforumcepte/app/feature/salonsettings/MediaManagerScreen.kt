package com.kuaforumcepte.app.feature.salonsettings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kuaforumcepte.app.core.designsystem.component.ConfirmDialog
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcSecondaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.media.ImageKind
import com.kuaforumcepte.app.data.salon.MediaItem
import com.kuaforumcepte.app.feature.media.ImagePreviewDialog
import java.io.File

@Composable
fun MediaManagerScreen(onBack: () -> Unit, viewModel: MediaManagerViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val msg = ui.message?.asString()
    var confirmDiscard by remember { mutableStateOf(false) }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { u -> viewModel.onPicked(u, ImageKind.LOGO) } }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { u -> viewModel.onPicked(u, ImageKind.PHOTO) } }

    LaunchedEffect(msg) { if (msg != null) { snackbar.showSnackbar(msg); viewModel.consumeMessage() } }
    val goBack: () -> Unit = {
        when {
            ui.saving -> Unit
            ui.dirty -> { confirmDiscard = true }
            else -> onBack()
        }
    }
    BackHandler(onBack = goBack)

    Scaffold(
        topBar = { KcTopBar("Logo ve Fotoğraflar", goBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (ui.salon != null) Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().padding(Spacing.l)) {
                    ui.progressLabel?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(progress = { ui.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s))
                    }
                    KcPrimaryButton("Kaydet", viewModel::save, loading = ui.saving, enabled = ui.dirty)
                }
            }
        },
    ) { padding ->
        val salon = ui.salon
        when {
            ui.loading -> SkeletonCardList(3, Modifier.padding(padding))
            ui.notAuthorized -> EmptyState("Bu bölüm yalnızca salon sahibine açıktır", modifier = Modifier.padding(padding))
            salon == null -> EmptyState("Salon bilgisi alınamadı", modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text("Logo", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = ui.newLogoPath?.let(::File) ?: salon.logoUrl,
                        contentDescription = "Salon logosu",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(112.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Column(Modifier.padding(start = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        KcSecondaryButton("Logoyu değiştir", { logoPicker.launch(imageOnly) }, enabled = !ui.saving, leadingIcon = Icons.Outlined.Image)
                        if (ui.newLogoPath != null) TextButton(onClick = viewModel::undoLogo) { Text("Eski logoya dön") }
                    }
                }
                Text("Logo zorunludur; silinemez, yalnızca değiştirilebilir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text("Mekan fotoğrafları (${ui.items.size}/${MediaManagerViewModel.MAX_PHOTOS})", style = MaterialTheme.typography.titleMedium)
                if (ui.items.isEmpty()) {
                    Text("Henüz fotoğraf yok. Fotoğraf yoksa salon sayfasında yalnızca logo gösterilir.", style = MaterialTheme.typography.bodyMedium)
                }
                ui.items.forEachIndexed { i, item ->
                    PhotoRow(
                        index = i,
                        count = ui.items.size,
                        model = when (item) { is MediaItem.Existing -> item.photo.url; is MediaItem.New -> File(item.localPath) },
                        isNew = item is MediaItem.New,
                        enabled = !ui.saving,
                        onUp = { viewModel.move(i, -1) },
                        onDown = { viewModel.move(i, +1) },
                        onRemove = { viewModel.remove(i) },
                    )
                }
                if (ui.items.size < MediaManagerViewModel.MAX_PHOTOS) {
                    KcSecondaryButton("Fotoğraf ekle", { photoPicker.launch(imageOnly) }, enabled = !ui.saving, leadingIcon = Icons.Outlined.AddPhotoAlternate)
                }
                InfoBanner("Salon sayfasında fotoğraflar bu sırayla 3 saniyede bir değişir, en sonda logo gösterilir. Değişiklikler “Kaydet” ile birlikte uygulanır.")
                if (ui.dirty && !ui.saving) TextButton(onClick = viewModel::reset) { Text("Değişiklikleri geri al") }
            }
        }
    }

    ui.pending?.let { ImagePreviewDialog(it, viewModel::onMode, viewModel::confirmPending, viewModel::cancelPending) }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Kaydedilmemiş değişiklikler",
            message = "Görsel değişiklikleriniz kaydedilmedi. Çıkarsanız kaybolacak.",
            confirmLabel = "Kaydetmeden Çık",
            destructive = true,
            onConfirm = { confirmDiscard = false; viewModel.reset(); onBack() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun PhotoRow(index: Int, count: Int, model: Any, isNew: Boolean, enabled: Boolean, onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = model,
                contentDescription = "Fotoğraf ${index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(112.dp).aspectRatio(4f / 3f).clip(MaterialTheme.shapes.medium),
            )
            Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
                Text("${index + 1}. fotoğraf", style = MaterialTheme.typography.titleSmall)
                if (isNew) Text("Yeni · kaydedilmedi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            IconButton(onClick = onUp, enabled = enabled && index > 0) { Icon(Icons.Outlined.ArrowUpward, "Yukarı taşı") }
            IconButton(onClick = onDown, enabled = enabled && index < count - 1) { Icon(Icons.Outlined.ArrowDownward, "Aşağı taşı") }
            IconButton(onClick = onRemove, enabled = enabled) { Icon(Icons.Outlined.Delete, "Fotoğrafı kaldır", tint = MaterialTheme.colorScheme.error) }
        }
    }
}
