package com.kuaforumcepte.app.feature.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.SuccessState
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.appointments.Appointment
import com.kuaforumcepte.app.data.appointments.AppointmentRepository
import com.kuaforumcepte.app.data.reviews.ReviewRepository
import com.kuaforumcepte.domain.model.AppointmentStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUi(
    val loading: Boolean = true,
    val appointment: Appointment? = null,
    val salon: Int = 0,
    val staff: Int = 0,
    val service: Int = 0,
    val comment: String = "",
    val submitting: Boolean = false,
    val done: Boolean = false,
    val error: UiText? = null,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val appointments: AppointmentRepository,
    private val reviews: ReviewRepository,
) : ViewModel() {
    private val id: String = checkNotNull(savedState["appointmentId"])
    private val _ui = MutableStateFlow(ReviewUi())
    val ui: StateFlow<ReviewUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { appointments.observe(id).first() }
                .onSuccess { a -> _ui.update { it.copy(loading = false, appointment = a, done = a?.reviewId != null) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = ErrorMessages.of(e)) } }
        }
    }

    fun setSalon(v: Int) = _ui.update { it.copy(salon = v) }
    fun setStaff(v: Int) = _ui.update { it.copy(staff = v) }
    fun setService(v: Int) = _ui.update { it.copy(service = if (it.service == v) 0 else v) }
    fun setComment(v: String) = _ui.update { it.copy(comment = v) }
    fun consumeError() = _ui.update { it.copy(error = null) }

    fun submit() {
        val s = _ui.value
        if (s.salon == 0 || s.staff == 0 || s.submitting) return
        _ui.update { it.copy(submitting = true) }
        viewModelScope.launch {
            runCatching { reviews.submit(id, s.salon, s.staff, s.service.takeIf { it > 0 }, s.comment) }
                .onSuccess { _ui.update { it.copy(submitting = false, done = true) } }
                .onFailure { e -> _ui.update { it.copy(submitting = false, error = ErrorMessages.of(e), done = (e as? com.kuaforumcepte.app.core.data.AppException)?.reason == "ALREADY_REVIEWED") } }
        }
    }
}

/** "Hizmeti / Kuaförü Puanla": randevu başına bir değerlendirme seti; gönderildikten sonra değiştirilemez. */
@Composable
fun ReviewScreen(onBack: () -> Unit, viewModel: ReviewViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val err = ui.error?.asString()
    LaunchedEffect(err) { if (err != null) { snackbar.showSnackbar(err); viewModel.consumeError() } }

    Scaffold(topBar = { KcTopBar("Hizmeti / Kuaförü Puanla", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val a = ui.appointment
        when {
            ui.loading -> SkeletonCardList(2, Modifier.padding(padding))
            ui.done -> SuccessState("Değerlendirmeniz için teşekkürler", "Puanınız salon ve uzman puanlarına eklendi.", Modifier.padding(padding), "Geri dön", onBack)
            a == null || a.status != AppointmentStatus.COMPLETED -> EmptyState("Bu randevu puanlanamaz", "Yalnızca tamamlanmış randevular puanlanabilir.", Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Text("${a.salonName} · ${a.staffName}", style = MaterialTheme.typography.titleLarge)
                StarPicker("Salon deneyimi", ui.salon, viewModel::setSalon)
                StarPicker("${a.staffName} (uzman)", ui.staff, viewModel::setStaff)
                StarPicker("Hizmet (isteğe bağlı)", ui.service, viewModel::setService)
                if (ui.service > 0) TextButton(onClick = { viewModel.setService(ui.service) }) { Text("Hizmet puanını kaldır") }
                KcTextField(ui.comment, viewModel::setComment, "Kısa yorum (isteğe bağlı)", maxLength = 500, singleLine = false, minLines = 3, imeAction = ImeAction.Default)
                InfoBanner("Salon puanı salonun, uzman puanı uzmanın ortalamasına eklenir. Hizmet puanı ayrı gösterilir ve ortalamalara girmez. Yorumunuz adınız ve soyadınızın baş harfiyle yayınlanır. Değerlendirme gönderildikten sonra değiştirilemez.")
                KcPrimaryButton("Gönder", viewModel::submit, loading = ui.submitting, enabled = ui.salon > 0 && ui.staff > 0)
            }
        }
    }
}
