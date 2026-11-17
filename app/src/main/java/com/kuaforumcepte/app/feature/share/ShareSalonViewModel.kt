package com.kuaforumcepte.app.feature.share

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.di.DefaultDispatcher
import com.kuaforumcepte.app.core.di.IoDispatcher
import com.kuaforumcepte.app.data.link.DeepLinkRepository
import com.kuaforumcepte.app.data.salon.SalonRepository
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.domain.model.MemberRole
import com.kuaforumcepte.domain.qr.QrCodes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class ShareUi(
    val loading: Boolean = true,
    val notAuthorized: Boolean = false,
    val salonName: String = "",
    val code: String = "",
    val link: String = "",
    val qr: Bitmap? = null,
    val poster: Bitmap? = null,
    /** Üretilen QR geri okunup bağlantıyla birebir eşleşti mi? */
    val verified: Boolean? = null,
    val message: UiText? = null,
)

@HiltViewModel
class ShareSalonViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionRepository,
    private val salons: SalonRepository,
    private val links: DeepLinkRepository,
    @DefaultDispatcher private val cpu: CoroutineDispatcher,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _ui = MutableStateFlow(ShareUi())
    val ui: StateFlow<ShareUi> = _ui.asStateFlow()

    init { generate() }

    fun generate() = viewModelScope.launch {
        _ui.update { it.copy(loading = true) }
        try {
            val active = session.activeSalon.first()
            if (active == null || active.membership.role != MemberRole.OWNER) {
                _ui.update { it.copy(loading = false, notAuthorized = true) }
                return@launch
            }
            val salon = salons.observe(active.membership.salonId).first()
            val name = salon?.name ?: active.membership.salonName
            val code = salon?.code ?: active.membership.salonCode
            val link = links.salonUrl(code)
            val (qr, verified, poster) = withContext(cpu) {
                val img = QrCodes.encode(link, size = 1024)
                val ok = QrCodes.verify(img, link)
                val bmp = QrPoster.bitmap(img)
                Triple(bmp, ok, QrPoster.poster(bmp, name, link, code))
            }
            _ui.update { it.copy(loading = false, salonName = name, code = code, link = link, qr = qr, verified = verified, poster = poster) }
        } catch (t: Throwable) {
            _ui.update { it.copy(loading = false, message = ErrorMessages.of(t)) }
        }
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }
    fun notify(text: String) = _ui.update { it.copy(message = UiText.Plain(text)) }

    /** Paylaşım için afişi uygulama önbelleğine PNG olarak yazar (FileProvider ile paylaşılır). */
    suspend fun posterFile(): File? {
        val poster = _ui.value.poster ?: return null
        return withContext(io) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            File(dir, "kuaforumcepte_qr_${_ui.value.code}.png").also { f ->
                FileOutputStream(f).use { poster.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
