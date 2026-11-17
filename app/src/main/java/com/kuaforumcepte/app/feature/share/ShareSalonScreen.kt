package com.kuaforumcepte.app.feature.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.print.PrintHelper
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcSecondaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.KcTheme
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import kotlinx.coroutines.launch

private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

@Composable
fun ShareSalonScreen(onBack: () -> Unit, viewModel: ShareSalonViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val msg = ui.message?.asString()
    LaunchedEffect(msg) { if (msg != null) { snackbar.showSnackbar(msg); viewModel.consumeMessage() } }

    val shareText = "${ui.salonName} için Kuaförüm Cepte'den kolayca randevu alın: ${ui.link}"

    Scaffold(topBar = { KcTopBar("Randevu Linki ve QR", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        when {
            ui.loading -> SkeletonCardList(3, Modifier.padding(padding))
            ui.notAuthorized -> EmptyState("Bu bölüm yalnızca salon sahibine açıktır", modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text("Salon randevu bağlantısı", style = MaterialTheme.typography.titleMedium)
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    SelectionContainer { Text(ui.link, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(Spacing.l)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    KcSecondaryButton("Kopyala", {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Randevu linki", ui.link)))
                            viewModel.notify("Bağlantı kopyalandı")
                        }
                    }, Modifier.weight(1f), leadingIcon = Icons.Outlined.ContentCopy)
                    KcSecondaryButton("Paylaş", { shareTextChooser(context, shareText) }, Modifier.weight(1f), leadingIcon = Icons.Outlined.Share)
                }
                KcPrimaryButton("WhatsApp ile Paylaş", {
                    if (!shareWhatsApp(context, shareText)) {
                        viewModel.notify("WhatsApp yüklü değil; diğer uygulamalarla paylaşabilirsiniz.")
                        shareTextChooser(context, shareText)
                    }
                }, leadingIcon = Icons.Outlined.Chat)
                InfoBanner("Bağlantı müşteriyi doğrudan salonunuzun randevu sayfasına götürür. Uygulama yüklü değilse salon bilgilerini ve indirme bağlantısını içeren bir web sayfası açılır.")

                Text("QR kod", style = MaterialTheme.typography.titleMedium)
                ui.qr?.let { qr ->
                    Surface(shape = MaterialTheme.shapes.large, color = Color.White, tonalElevation = 1.dp) {
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "${ui.salonName} randevu sayfası QR kodu",
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(Spacing.l).clip(MaterialTheme.shapes.small).background(Color.White),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ok = ui.verified == true
                    Icon(
                        if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, contentDescription = null,
                        tint = if (ok) KcTheme.colors.success else KcTheme.colors.cancel, modifier = Modifier.size(20.dp),
                    )
                    Text(
                        if (ok) "  Taranabilirlik kontrolü başarılı: QR geri okundu ve bağlantıyla eşleşti."
                        else "  QR doğrulanamadı. Yeniden oluşturun.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (ui.verified != true) KcSecondaryButton("QR'ı yeniden oluştur", { viewModel.generate() }, leadingIcon = Icons.Outlined.QrCode2)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    KcSecondaryButton("Afişi paylaş", {
                        scope.launch {
                            val file = viewModel.posterFile() ?: return@launch
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "QR afişini paylaş"))
                        }
                    }, Modifier.weight(1f), enabled = ui.verified == true, leadingIcon = Icons.Outlined.Share)
                    KcSecondaryButton("Yazdır / PDF", {
                        val poster = ui.poster ?: return@KcSecondaryButton
                        PrintHelper(context).apply {
                            scaleMode = PrintHelper.SCALE_MODE_FIT
                            colorMode = PrintHelper.COLOR_MODE_COLOR
                        }.printBitmap("${ui.salonName} QR", poster)
                    }, Modifier.weight(1f), enabled = ui.verified == true, leadingIcon = Icons.Outlined.Print)
                }
                Text(
                    "Yazdır ekranında “PDF olarak kaydet” seçeneğiyle A4 PDF oluşturabilirsiniz. En iyi okuma için en az 4×4 cm basın ve parlak yüzeylerden kaçının.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun shareTextChooser(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    context.startActivity(Intent.createChooser(send, "Randevu linkini paylaş"))
}

/** @return WhatsApp (veya WhatsApp Business) açılabildiyse true. */
private fun shareWhatsApp(context: Context, text: String): Boolean {
    for (pkg in WHATSAPP_PACKAGES) {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); setPackage(pkg) }
        try {
            context.startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            // sıradakini dene
        }
    }
    return false
}
