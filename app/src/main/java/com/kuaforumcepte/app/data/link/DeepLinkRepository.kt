package com.kuaforumcepte.app.data.link

import com.kuaforumcepte.app.BuildConfig
import com.kuaforumcepte.domain.link.DeepLink
import com.kuaforumcepte.domain.link.DeepLinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uygulamayı açan bağlantı (App Link / QR / bildirim) giriş tamamlanana kadar burada bekler;
 * oturum hazır olduğunda navigasyon tarafından bir kez tüketilir.
 */
@Singleton
class DeepLinkRepository @Inject constructor() {
    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending.asStateFlow()

    fun offer(raw: String?) {
        DeepLinks.parse(raw, BuildConfig.APP_LINK_HOST)?.let { _pending.value = it }
    }

    fun consume(link: DeepLink) {
        if (_pending.value == link) _pending.value = null
    }

    fun salonUrl(code: String): String = DeepLinks.salonUrl(BuildConfig.APP_LINK_HOST, code)
}
