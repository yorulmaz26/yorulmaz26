package com.kuaforumcepte.app.feature.salonsettings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.data.media.FitMode
import com.kuaforumcepte.app.data.media.ImageKind
import com.kuaforumcepte.app.data.media.ImageProcessor
import com.kuaforumcepte.app.data.media.PendingImage
import com.kuaforumcepte.app.data.salon.MediaItem
import com.kuaforumcepte.app.data.salon.SalonProfile
import com.kuaforumcepte.app.data.salon.SalonRepository
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.domain.model.MemberRole
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class MediaUi(
    val loading: Boolean = true,
    val notAuthorized: Boolean = false,
    val salon: SalonProfile? = null,
    /** Yeni seçilmiş (henüz kaydedilmemiş) logo; null ise mevcut logo kalır. */
    val newLogoPath: String? = null,
    val items: List<MediaItem> = emptyList(),
    val pending: PendingImage? = null,
    val saving: Boolean = false,
    val progressLabel: String? = null,
    val progress: Float = 0f,
    val message: UiText? = null,
) {
    val dirty: Boolean
        get() = salon != null && (newLogoPath != null || items != salon.photos.map { MediaItem.Existing(it) })
}

/**
 * Logo ve galeri düzenleme (yalnızca sahibi). Kayıt sihirbazıyla aynı görsel işleme hattını
 * (ImageProcessor + önizleme) ve aynı salon dokümanını kullanır.
 */
@HiltViewModel
class MediaManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionRepository,
    private val salons: SalonRepository,
    private val images: ImageProcessor,
) : ViewModel() {

    private val _ui = MutableStateFlow(MediaUi())
    val ui: StateFlow<MediaUi> = _ui.asStateFlow()
    private val workDir = File(context.cacheDir, "media_edit").apply { mkdirs() }

    init { load() }

    private fun load() = viewModelScope.launch {
        val active = session.activeSalon.first()
        if (active == null || active.membership.role != MemberRole.OWNER) {
            _ui.update { it.copy(loading = false, notAuthorized = true) }
            return@launch
        }
        runCatching { salons.observe(active.membership.salonId).first() }
            .onSuccess { s -> _ui.update { it.copy(loading = false, salon = s, items = s?.photos.orEmpty().map { p -> MediaItem.Existing(p) }) } }
            .onFailure { e -> _ui.update { it.copy(loading = false, message = ErrorMessages.of(e)) } }
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    fun onPicked(uri: Uri, kind: ImageKind) {
        if (kind == ImageKind.PHOTO && _ui.value.items.size >= MAX_PHOTOS) return
        process(PendingImage(uri, kind, FitMode.CROP, null, true))
    }

    fun onMode(mode: FitMode) {
        val p = _ui.value.pending ?: return
        if (p.mode != mode) process(p.copy(mode = mode))
    }

    private fun process(p: PendingImage) {
        p.preview?.let { File(it.localPath).delete() }
        _ui.update { it.copy(pending = p.copy(preview = null, processing = true)) }
        viewModelScope.launch {
            runCatching { images.process(p.source, p.kind, p.mode, workDir) }
                .onSuccess { img -> _ui.update { it.copy(pending = p.copy(preview = img, processing = false)) } }
                .onFailure { e -> _ui.update { it.copy(pending = null, message = ErrorMessages.of(e)) } }
        }
    }

    fun confirmPending() {
        val p = _ui.value.pending ?: return
        val img = p.preview ?: return
        _ui.update {
            if (p.kind == ImageKind.LOGO) {
                it.newLogoPath?.let { old -> File(old).delete() }
                it.copy(pending = null, newLogoPath = img.localPath)
            } else {
                it.copy(pending = null, items = (it.items + MediaItem.New(img.localPath)).take(MAX_PHOTOS))
            }
        }
    }

    fun cancelPending() {
        _ui.value.pending?.preview?.let { File(it.localPath).delete() }
        _ui.update { it.copy(pending = null) }
    }

    fun undoLogo() = _ui.update { s -> s.newLogoPath?.let { File(it).delete() }; s.copy(newLogoPath = null) }

    fun remove(index: Int) = _ui.update { s ->
        (s.items.getOrNull(index) as? MediaItem.New)?.let { File(it.localPath).delete() }
        s.copy(items = s.items.filterIndexed { i, _ -> i != index })
    }

    /** Erişilebilir sıralama: sürükle-bırak yerine yukarı/aşağı taşı. */
    fun move(index: Int, delta: Int) = _ui.update { s ->
        val target = index + delta
        if (target !in s.items.indices) return@update s
        val list = s.items.toMutableList()
        list.add(target, list.removeAt(index))
        s.copy(items = list)
    }

    fun reset() = _ui.update { s ->
        s.newLogoPath?.let { File(it).delete() }
        s.items.filterIsInstance<MediaItem.New>().forEach { File(it.localPath).delete() }
        s.copy(newLogoPath = null, items = s.salon?.photos.orEmpty().map { MediaItem.Existing(it) })
    }

    fun save() {
        val s = _ui.value
        val salon = s.salon ?: return
        val uid = session.currentUid ?: return
        if (s.saving || !s.dirty) return
        _ui.update { it.copy(saving = true, progressLabel = "Hazırlanıyor", progress = 0f) }
        viewModelScope.launch {
            runCatching {
                salons.updateMedia(uid, salon.id, s.newLogoPath, s.items) { label, p -> _ui.update { it.copy(progressLabel = label, progress = p) } }
            }.onSuccess {
                workDir.listFiles()?.forEach { it.delete() }
                val fresh = salons.observe(salon.id).first()
                _ui.update {
                    it.copy(
                        saving = false, progressLabel = null, salon = fresh, newLogoPath = null,
                        items = fresh?.photos.orEmpty().map { p -> MediaItem.Existing(p) },
                        message = UiText.Plain("Logo ve fotoğraflar güncellendi. Müşteriler yeni görselleri hemen görür."),
                    )
                }
            }.onFailure { e ->
                _ui.update { it.copy(saving = false, progressLabel = null, message = ErrorMessages.of(e)) }
                if (e.message == "STATE_CHANGED") load()
            }
        }
    }

    override fun onCleared() {
        if (!_ui.value.saving) workDir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val MAX_PHOTOS = 5
    }
}
