package com.kuaforumcepte.app.feature.services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.data.services.ServiceDraft
import com.kuaforumcepte.app.data.services.ServiceRepository
import com.kuaforumcepte.app.data.services.StaffService
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.domain.booking.ServiceRules
import com.kuaforumcepte.domain.model.Kurus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ServicesState {
    data object Loading : ServicesState
    data object NoSalon : ServicesState
    data class Error(val message: UiText) : ServicesState
    data class Ready(val salonId: String, val items: List<StaffService>) : ServicesState
}

data class ServiceForm(
    val editing: StaffService?,
    val name: String = editing?.name.orEmpty(),
    val description: String = editing?.description.orEmpty(),
    val price: String = editing?.price?.let { formatPriceInput(it) }.orEmpty(),
    val duration: Int? = editing?.durationMinutes ?: 30,
    val active: Boolean = editing?.active ?: true,
    val errors: ServiceRules.Errors = ServiceRules.Errors(),
    /** Düzenlenen hizmeti içeren gelecek randevu sayısı (null = yükleniyor / yeni hizmet). */
    val impactCount: Int? = null,
    val saving: Boolean = false,
)

/** 25050 kuruş → "250,50"; tam liralar "250". */
fun formatPriceInput(k: Kurus): String = if (k.value % 100 == 0L) "${k.value / 100}" else "%d,%02d".format(k.value / 100, k.value % 100)

data class DeleteRequest(val service: StaffService, val impactCount: Int?)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ServicesViewModel @Inject constructor(
    private val session: SessionRepository,
    private val services: ServiceRepository,
) : ViewModel() {

    val state: StateFlow<ServicesState> = session.activeSalon.flatMapLatest { a ->
        val uid = session.currentUid
        if (a == null || uid == null) flowOf(ServicesState.NoSalon)
        else services.observeMine(a.membership.salonId, uid)
            .map<List<StaffService>, ServicesState> { ServicesState.Ready(a.membership.salonId, it) }
            .onStart { emit(ServicesState.Loading) }
            .catch { emit(ServicesState.Error(ErrorMessages.of(it))) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServicesState.Loading)

    private val _form = MutableStateFlow<ServiceForm?>(null)
    val form: StateFlow<ServiceForm?> = _form.asStateFlow()
    private val _delete = MutableStateFlow<DeleteRequest?>(null)
    val delete: StateFlow<DeleteRequest?> = _delete.asStateFlow()
    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    private val salonId: String? get() = (state.value as? ServicesState.Ready)?.salonId

    fun consumeMessage() { _message.value = null }

    fun openNew() { _form.value = ServiceForm(editing = null) }

    fun openEdit(s: StaffService) {
        _form.value = ServiceForm(editing = s)
        loadImpact(s) { count -> _form.update { f -> f?.takeIf { it.editing?.id == s.id }?.copy(impactCount = count) ?: f } }
    }

    fun closeForm() { if (_form.value?.saving != true) _form.value = null }
    fun onForm(transform: (ServiceForm) -> ServiceForm) = _form.update { it?.let(transform) }

    fun saveForm() {
        val f = _form.value ?: return
        val sid = salonId ?: return
        val uid = session.currentUid ?: return
        val errors = ServiceRules.validate(f.name, f.price, f.duration, f.description)
        if (!errors.isValid) { _form.value = f.copy(errors = errors); return }
        val draft = ServiceDraft(f.name, f.description, Kurus.parseTry(f.price)!!, f.duration!!, f.active)
        _form.value = f.copy(saving = true, errors = errors)
        viewModelScope.launch {
            runCatching { if (f.editing == null) services.create(sid, uid, draft) else services.update(sid, f.editing.id, draft) }
                .onSuccess {
                    _form.value = null
                    _message.value = UiText.Plain(
                        when {
                            f.editing == null -> "Hizmet eklendi."
                            (f.impactCount ?: 0) > 0 -> "Hizmet güncellendi. Mevcut ${f.impactCount} randevunun fiyat ve süresi değişmedi; yeni bilgiler yeni randevularda geçerli."
                            else -> "Hizmet güncellendi."
                        },
                    )
                }
                .onFailure { e -> _form.value = f.copy(saving = false); _message.value = ErrorMessages.of(e) }
        }
    }

    fun toggleActive(s: StaffService) {
        val sid = salonId ?: return
        if (s.id in _busy.value) return
        _busy.update { it + s.id }
        viewModelScope.launch {
            runCatching { services.setActive(sid, s.id, !s.active) }
                .onSuccess { _message.value = UiText.Plain(if (s.active) "“${s.name}” pasife alındı; yeni randevularda seçilemez." else "“${s.name}” yeniden aktif.") }
                .onFailure { _message.value = ErrorMessages.of(it) }
            _busy.update { it - s.id }
        }
    }

    fun requestDelete(s: StaffService) {
        _delete.value = DeleteRequest(s, null)
        loadImpact(s) { count -> _delete.update { d -> d?.takeIf { it.service.id == s.id }?.copy(impactCount = count) ?: d } }
    }

    fun cancelDelete() { _delete.value = null }

    fun confirmDelete() {
        val d = _delete.value ?: return
        val sid = salonId ?: return
        _delete.value = null
        _busy.update { it + d.service.id }
        viewModelScope.launch {
            runCatching { services.delete(sid, d.service.id) }
                .onSuccess { _message.value = UiText.Plain("“${d.service.name}” silindi. Geçmiş ve mevcut randevulardaki kayıtları korunur.") }
                .onFailure { _message.value = ErrorMessages.of(it) }
            _busy.update { it - d.service.id }
        }
    }

    private fun loadImpact(s: StaffService, onLoaded: (Int) -> Unit) = viewModelScope.launch {
        val sid = salonId ?: (session.activeSalon.first()?.membership?.salonId ?: return@launch)
        runCatching { services.futureAppointmentCount(sid, s.id) }.onSuccess(onLoaded).onFailure { onLoaded(-1) }
    }
}
