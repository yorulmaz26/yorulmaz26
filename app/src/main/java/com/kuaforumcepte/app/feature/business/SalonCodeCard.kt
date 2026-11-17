package com.kuaforumcepte.app.feature.business

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import kotlinx.coroutines.launch

/** Salonun değiştirilemez 6 haneli kimlik kodu; kopyalama ve paylaşma. */
@Composable
fun SalonCodeCard(code: String, modifier: Modifier = Modifier, onCopied: (() -> Unit)? = null) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spaced = code.chunked(3).joinToString(" ")
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Salon kimlik kodu", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    spaced,
                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 34.sp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Salon kodu ${code.toList().joinToString(" ")}" },
                )
                IconButton(onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Salon kodu", code)))
                        onCopied?.invoke()
                    }
                }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Kodu kopyala") }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Kuaförüm Cepte salon kodumuz: $code")
                    }
                    context.startActivity(Intent.createChooser(send, "Salon kodunu paylaş"))
                }) { Icon(Icons.Outlined.Share, contentDescription = "Kodu paylaş") }
            }
            Text(
                "Bu kod salonunuzun kimliğidir ve değiştirilemez. Personel eşleştirmesinde kullanılır; gizli bir şifre değildir. " +
                    "Kodu bilmek tek başına yetki vermez: personel yalnızca sizin davet ettiğiniz ve SMS ile doğrulanan telefonla katılabilir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
