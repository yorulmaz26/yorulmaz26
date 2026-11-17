package com.kuaforumcepte.app.feature.register

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.data.location.City
import com.kuaforumcepte.app.data.location.LocationRepository
import com.kuaforumcepte.app.data.media.FitMode
import com.kuaforumcepte.app.data.media.ImageKind
import com.kuaforumcepte.app.data.media.ImageProcessor
import com.kuaforumcepte.app.data.registration.DraftImage
import com.kuaforumcepte.app.data.registration.OwnerDraft
import com.kuaforumcepte.app.data.registration.OwnerDraftStore
import com.kuaforumcepte.app.data.registration.RegistrationRepository
import com.kuaforumcepte.app.data.registration.SalonCreated
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.domain.model.SalonType
import com.kuaforumcepte.domain.phone.PhoneNumbers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class OwnerStep(val title: String) {
    PERSONAL("Kişisel Bilgiler"), VENUE("Mekan Bilgileri"), ADDRESS("Adres"), MEDIA("Logo ve Fotoğraflar"), REVIEW("Özet ve Onay")
}

/** Seçilen görselin kırp/sığdır önizlemesi; "Kullan" ile taslağa eklenir. */
data class PendingImage(val source: Uri, val kind: ImageKind, val mode: FitMode, val preview: DraftImage?, val processing: Boolean)

data class OwnerRegisterUi(
    val loading: Boolean = true,
    val draft: OwnerDraft? = null,
    val phoneE164: String = "",
    val cities: List<City> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
    val pendingImage: PendingImage? = null,
    val submitting: Boolean = false,
    val progressLabel: String? = null,
    val progress: Float = 0f,
    val error: UiText? = null,
) {
    val step: OwnerStep get() = OwnerStep.entries[draft?.step?.coerceIn(0, OwnerStep.entries.size - 1) ?: 0]
    val districts: List<String> get() = cities.firstOrNull { it.name == draft?.city }?.districts.orEmpty()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class OwnerRegisterViewModel @Inject constructor(
    private val session: SessionRepository,
    private val store: OwnerDraftStore,
    private val locations: LocationRepository,
    private val images: ImageProcessor,
    private val registration: RegistrationRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(OwnerRegisterUi())
    val ui: StateFlow<OwnerRegisterUi> = _ui.asStateFlow()
    private val _created = Channel<SalonCreated>(Channel.BUFFERED)
    val created = _created.receiveAsFlow()
    private val saves = MutableStateFlow<OwnerDraft?>(null)

    init {
        viewModelScope.launch {
            val uid = session.currentUid ?: run {
                _ui.update { it.copy(loading = false, error = ErrorMessages.of(AppException(AppException.NOT_SIGNED_IN))) }
                return@launch
            }
            var draft = store.loadOrCreate(uid)
            val snap = session.snapshot.value
            if (draft.firstName.isBlank() && snap?.firstName != null) {
                draft = draft.copy(firstName = snap.firstName, lastName = snap.lastName.orEmpty())
            }
            // Uygulama dışında silinmiş yerel görselleri taslaktan düşür.
            draft = draft.copy(
                logo = draft.logo?.takeIf { File(it.localPath).exists() },
                photos = draft.photos.filter { File(it.localPath).exists() },
            )
            _ui.update { it.copy(loading = false, draft = draft, phoneE164 = session.currentPhone.orEmpty(), cities = locations.cities()) }
        }
        // Her değişiklik kısa gecikmeyle diske yazılır (kayıt yarıda kalırsa devam için).
        viewModelScope.launch { saves.filterNotNull().debounce(400).collect { store.save(it) } }
    }

    private fun edit(clearError: String? = null, block: (OwnerDraft) -> OwnerDraft) {
        val d = _ui.value.draft ?: return
        val next = block(d)
        _ui.update { it.copy(draft = next, errors = if (clearError != null) it.errors - clearError else it.errors) }
        saves.value = next
    }

    fun onFirstName(v: String) = edit("firstName") { it.copy(firstName = v) }
    fun onLastName(v: String) = edit("lastName") { it.copy(lastName = v) }
    fun onSalonName(v: String) = edit("salonName") { it.copy(salonName = v) }
    fun onBusinessPhone(v: String) = edit("businessPhone") { it.copy(businessPhone = v.filter { c -> c.isDigit() || c == ' ' }.take(14)) }
    fun onType(t: SalonType) = edit("salonType") { it.copy(salonType = t.name) }
    fun onCity(c: City) = edit("city") { it.copy(city = c.name, district = null) }
    fun onDistrict(d: String) = edit("district") { it.copy(district = d) }
    fun onAddress(v: String) = edit("addressLine") { it.copy(addressLine = v) }
    fun onDescription(v: String) = edit { it.copy(description = v) }
    fun onKvkk(v: Boolean) = edit { it.copy(kvkkAccepted = v) }
    fun onTerms(v: Boolean) = edit { it.copy(termsAccepted = v) }
    fun dismissError() = _ui.update { it.copy(error = null) }

    // ---- Görseller ----
    fun onImagePicked(uri: Uri, kind: ImageKind) {
        val d = _ui.value.draft ?: return
        if (kind == ImageKind.PHOTO && d.photos.size >= MAX_PHOTOS) return
        reprocess(PendingImage(uri, kind, FitMode.CROP, null, true))
    }

    fun onPendingMode(mode: FitMode) {
        val p = _ui.value.pendingImage ?: return
        if (p.mode != mode) reprocess(p.copy(mode = mode, processing = true))
    }

    private fun reprocess(p: PendingImage) {
        val uid = _ui.value.draft?.uid ?: return
        p.preview?.let { File(it.localPath).delete() }
        _ui.update { it.copy(pendingImage = p.copy(preview = null, processing = true)) }
        viewModelScope.launch {
            runCatching { images.process(p.source, p.kind, p.mode, store.imageDir(uid)) }
                .onSuccess { img -> _ui.update { it.copy(pendingImage = p.copy(preview = img, processing = false)) } }
                .onFailure { e -> _ui.update { it.copy(pendingImage = null, error = ErrorMessages.of(e)) } }
        }
    }

    fun confirmPending() {
        val p = _ui.value.pendingImage ?: return
        val img = p.preview ?: return
        _ui.update { it.copy(pendingImage = null) }
        if (p.kind == ImageKind.LOGO) {
            edit("logo") { d -> d.logo?.let { File(it.localPath).delete() }; d.copy(logo = img) }
        } else {
            edit { d -> d.copy(photos = (d.photos + img).take(MAX_PHOTOS)) }
        }
    }

    fun cancelPending() {
        _ui.value.pendingImage?.preview?.let { File(it.localPath).delete() }
        _ui.update { it.copy(pendingImage = null) }
    }

    fun removePhoto(index: Int) = edit { d ->
        d.photos.getOrNull(index)?.let { File(it.localPath).delete() }
        d.copy(photos = d.photos.filterIndexed { i, _ -> i != index })
    }

    fun removeLogo() = edit { d -> d.logo?.let { File(it.localPath).delete() }; d.copy(logo = null) }

    // ---- Adımlar ----
    private fun validate(step: OwnerStep, d: OwnerDraft): Map<String, String> = buildMap {
        when (step) {
            OwnerStep.PERSONAL -> {
                nameError(d.firstName)?.let { put("firstName", it) }
                nameError(d.lastName)?.let { put("lastName", it) }
            }
            OwnerStep.VENUE -> {
                if (d.salonName.trim().length !in 2..80) put("salonName", "2–80 karakter girin")
                if (PhoneNumbers.normalizeTr(d.businessPhone) == null) put("businessPhone", "Geçerli bir telefon girin (ör. 0212 555 66 77)")
                if (d.salonType == null) put("salonType", "Salon türünü seçin")
            }
            OwnerStep.ADDRESS -> {
                if (d.city == null) put("city", "İl seçin")
                if (d.district == null) put("district", "İlçe seçin")
                if (d.addressLine.trim().length !in 5..300) put("addressLine", "Açık adresi girin (en az 5 karakter)")
                if (d.description.length > 1000) put("description", "En fazla 1000 karakter")
            }
            OwnerStep.MEDIA -> if (d.logo == null) put("logo", "Logo zorunludur")
            OwnerStep.REVIEW -> if (!d.kvkkAccepted || !d.termsAccepted) put("consent", "Onaylar gerekli")
        }
    }

    fun next() {
        val d = _ui.value.draft ?: return
        val step = _ui.value.step
        val errs = validate(step, d)
        _ui.update { it.copy(errors = errs) }
        if (errs.isNotEmpty()) return
        if (step == OwnerStep.REVIEW) submit() else edit { it.copy(step = it.step + 1) }
    }

    /** @return false ise ilk adımdayız (ekran çıkış onayı sorar). */
    fun back(): Boolean {
        val d = _ui.value.draft ?: return false
        if (d.step == 0 || _ui.value.submitting) return d.step != 0
        edit { it.copy(step = it.step - 1) }
        return true
    }

    fun goTo(step: OwnerStep) {
        if (step.ordinal < _ui.value.step.ordinal) edit { it.copy(step = step.ordinal) }
    }

    private fun submit() {
        val d = _ui.value.draft ?: return
        if (_ui.value.submitting) return
        val normalized = d.copy(businessPhone = PhoneNumbers.normalizeTr(d.businessPhone) ?: d.businessPhone)
        _ui.update { it.copy(submitting = true, error = null, progressLabel = "Hazırlanıyor", progress = 0f) }
        viewModelScope.launch {
            store.save(normalized)
            runCatching {
                registration.registerSalonOwner(normalized) { label, p -> _ui.update { it.copy(progressLabel = label, progress = p) } }
            }.onSuccess { created ->
                _ui.update { it.copy(submitting = false, progressLabel = null) }
                _created.send(created)
            }.onFailure { e ->
                // Yüklenmiş görseller taslakta işaretli kalır; tekrar denemede yalnızca eksikler yüklenir.
                val latest = runCatching { store.loadOrCreate(d.uid) }.getOrNull()
                _ui.update { it.copy(submitting = false, progressLabel = null, draft = latest ?: it.draft, error = ErrorMessages.of(e)) }
            }
        }
    }

    companion object {
        const val MAX_PHOTOS = 5
    }
}
