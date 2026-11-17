package com.kuaforumcepte.app.feature.calendar

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.BuildConfig
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.data.FunctionsClient
import com.kuaforumcepte.app.core.data.str
import com.kuaforumcepte.app.core.designsystem.component.ConfirmDialog
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcSecondaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CalendarFeedUi(val loading: Boolean = true, val path: String? = null, val error: String? = null, val message: String? = null) {
    val httpsUrl: String? get() = path?.let { "https://${BuildConfig.APP_LINK_HOST}$it" }
    val webcalUrl: String? get() = path?.let { "webcal://${BuildConfig.APP_LINK_HOST}$it" }
}

@HiltViewModel
class CalendarSyncViewModel @Inject constructor(private val functions: FunctionsClient) : ViewModel() {
    private val _ui = MutableStateFlow(CalendarFeedUi())
    val ui: StateFlow<CalendarFeedUi> = _ui.asStateFlow()

    init { load(false) }

    fun load(rotate: Boolean) {
        _ui.value = _ui.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { functions.call("getCalendarFeed", mapOf("rotate" to rotate)).str("path") }
                .onSuccess { p -> _ui.value = CalendarFeedUi(loading = false, path = p, message = if (rotate) "Yeni adres oluşturuldu; eski abonelikler artık güncellenmez." else null) }
                .onFailure { e -> _ui.value = _ui.value.copy(loading = false, error = ErrorMessages.text(e)) }
        }
    }

    fun messageShown() { _ui.value = _ui.value.copy(message = null) }
}

/**
 * Takvim aboneliği: müşteri, uzman ve salon sahibi randevularını Google Takvim/Outlook/Apple Takvim'e
 * abone olarak ekler. Randevu onaylanınca, saati değişince veya iptal olunca takvim kendiliğinden güncellenir.
 */
@Composable
fun CalendarSyncScreen(onBack: () -> Unit, viewModel: CalendarSyncViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmRotate by remember { mutableStateOf(false) }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }

    Scaffold(topBar = { KcTopBar("Takvim Entegrasyonu", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        when {
            ui.loading -> SkeletonCardList(2, Modifier.padding(padding))
            ui.error != null -> ErrorState(ui.error!!, Modifier.padding(padding), onRetry = { viewModel.load(false) })
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text("Randevularınız takviminizde, her zaman güncel", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Müşteri olarak aldığınız, uzman olarak size atanan ve salon sahibiyseniz salonunuzdaki tüm randevular tek bir takvimde görünür. " +
                        "Onay, saat değişikliği ve iptaller takviminize otomatik yansır (takvim uygulamanız genellikle birkaç saatte bir yeniler).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                KcPrimaryButton("Takvim uygulamasında abone ol", {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ui.webcalUrl))) }
                    catch (_: ActivityNotFoundException) {
                        scope.launch { snackbar.showSnackbar("Abone olabilecek takvim uygulaması bulunamadı. Adresi kopyalayıp Google Takvim'de “URL ile ekle” bölümüne yapıştırın.") }
                    }
                }, leadingIcon = Icons.Outlined.CalendarMonth)
                KcSecondaryButton("Adresi kopyala", {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Takvim adresi", ui.httpsUrl)))
                        snackbar.showSnackbar("Adres kopyalandı")
                    }
                }, leadingIcon = Icons.Outlined.ContentCopy)
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    SelectionContainer { Text(ui.httpsUrl.orEmpty(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(Spacing.m)) }
                }
                InfoBanner(
                    "Google Takvim (bilgisayar): Diğer takvimler › “+” › URL ile ekle › adresi yapıştırın. Telefonunuzda birkaç dakika içinde görünür.\n" +
                        "Bu adres size özeldir ve uzman/sahip için müşteri adlarını içerir; kimseyle paylaşmayın.",
                )
                KcSecondaryButton("Adresi yenile (eski aboneliği iptal et)", { confirmRotate = true }, leadingIcon = Icons.Outlined.Refresh)
                Text("Tek bir randevuyu eklemek için randevu ekranındaki “Takvime Ekle” düğmesini de kullanabilirsiniz.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (confirmRotate) {
        ConfirmDialog(
            title = "Adres yenilensin mi?",
            message = "Eski adrese abone olan takvimler artık güncellenmez. Yeni adrese yeniden abone olmanız gerekir.",
            confirmLabel = "Yenile",
            onConfirm = { confirmRotate = false; viewModel.load(true) },
            onDismiss = { confirmRotate = false },
            destructive = true,
        )
    }
}
