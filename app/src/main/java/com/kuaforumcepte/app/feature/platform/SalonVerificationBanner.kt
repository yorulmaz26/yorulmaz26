package com.kuaforumcepte.app.feature.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.designsystem.theme.KcTheme
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.salon.SalonProfile
import com.kuaforumcepte.app.data.salon.SalonRepository
import com.kuaforumcepte.app.data.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SalonVerificationViewModel @Inject constructor(session: SessionRepository, private val salons: SalonRepository) : ViewModel() {
    val salon: StateFlow<SalonProfile?> = session.activeSalon.flatMapLatest { a ->
        if (a == null) flowOf(null) else salons.observe(a.membership.salonId).catch { emit(null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun requestReview() {
        val id = salon.value?.id ?: return
        viewModelScope.launch {
            runCatching { salons.requestReview(id) }
                .onSuccess { _status.value = "Yeniden incelemeye gönderildi." }
                .onFailure { _status.value = ErrorMessages.text(it) }
        }
    }
}

/** Salon sahibine platform onay durumu: onaylanana kadar müşteri aramasında görünmez (kod/QR ile ulaşılabilir). */
@Composable
fun SalonVerificationBanner(@Suppress("UNUSED_PARAMETER") salonId: String, viewModel: SalonVerificationViewModel = hiltViewModel()) {
    val salon by viewModel.salon.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val s = salon ?: return
    if (s.verified) return
    val rejected = s.verificationStatus == "REJECTED"
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (rejected) KcTheme.colors.cancelContainer else MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(if (rejected) "Salon başvurunuz düzeltme bekliyor" else "Salonunuz onay bekliyor", style = MaterialTheme.typography.titleSmall)
            Text(
                if (rejected) "Gerekçe: ${s.verificationNote ?: "-"}. Dükkan Ayarları'ndan bilgileri düzeltip yeniden incelemeye gönderin."
                else "Onaylanana kadar salonunuz müşteri aramasında listelenmez; randevu linki ve QR kod ile müşteri kabul edebilirsiniz.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (rejected) TextButton(onClick = viewModel::requestReview) { Text("Yeniden incelemeye gönder") }
            status?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        }
    }
}
