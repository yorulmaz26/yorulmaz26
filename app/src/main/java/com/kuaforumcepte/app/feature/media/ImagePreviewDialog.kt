package com.kuaforumcepte.app.feature.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.media.FitMode
import com.kuaforumcepte.app.data.media.ImageKind
import com.kuaforumcepte.app.data.media.PendingImage
import java.io.File

/** Kayıt ve Logo/Fotoğraf yönetimi ekranlarının ortak kırp/sığdır önizlemesi. */
@Composable
fun ImagePreviewDialog(pending: PendingImage, onMode: (FitMode) -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val preview = pending.preview
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (pending.kind == ImageKind.LOGO) "Logo önizleme" else "Fotoğraf önizleme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(pending.kind.aspect).clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (pending.processing || preview == null) CircularProgressIndicator()
                    else AsyncImage(File(preview.localPath), contentDescription = "Önizleme", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FilterChip(selected = pending.mode == FitMode.CROP, onClick = { onMode(FitMode.CROP) }, label = { Text("Kırp (doldur)") })
                    FilterChip(selected = pending.mode == FitMode.FIT, onClick = { onMode(FitMode.FIT) }, label = { Text("Sığdır (tamamı)") })
                }
                Text(
                    if (pending.kind == ImageKind.LOGO) "Logo kare (1:1) olarak kaydedilir." else "Fotoğraf 4:3 oranında kaydedilir.",
                    style = MaterialTheme.typography.bodySmall,
                )
                preview?.let { Text("Boyut: ${it.widthPx}×${it.heightPx} px, ${it.sizeBytes / 1024} KB", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = preview != null && !pending.processing) { Text("Kullan") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Vazgeç") } },
    )
}
