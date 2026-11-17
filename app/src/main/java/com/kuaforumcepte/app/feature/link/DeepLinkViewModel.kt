package com.kuaforumcepte.app.feature.link

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.data.link.DeepLinkRepository
import com.kuaforumcepte.app.data.salon.SalonRepository
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.domain.link.DeepLink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LinkEvent {
    data class OpenSalon(val salonId: String) : LinkEvent
    data class OpenAppointment(val appointmentId: String, val asCustomer: Boolean) : LinkEvent
    data class Failed(val message: String) : LinkEvent
}

/**
 * Bekleyen bağlantıyı oturum hazır olduğunda çözer. Salon kodu → salonId Firestore'dan
 * (salonCodes/{kod}, yalnızca tekil okuma) bulunur; kod tek başına yetki vermez.
 */
@HiltViewModel
class DeepLinkViewModel @Inject constructor(
    private val links: DeepLinkRepository,
    private val salons: SalonRepository,
    private val session: SessionRepository,
    private val appointments: com.kuaforumcepte.app.data.appointments.AppointmentRepository,
) : ViewModel() {

    val pending: StateFlow<DeepLink?> = links.pending
    private val _events = Channel<LinkEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var resolving = false

    /** Kullanıcı ana ekranlardan birindeyken çağrılır. */
    fun resolvePending() {
        val link = links.pending.value ?: return
        if (resolving || session.currentUid == null) return
        when (link) {
            is DeepLink.Salon -> {
                resolving = true
                viewModelScope.launch {
                    val result = runCatching { salons.resolveCode(link.code) }
                    links.consume(link)
                    resolving = false
                    val id = result.getOrNull()
                    _events.send(
                        if (id != null) LinkEvent.OpenSalon(id)
                        else LinkEvent.Failed("Bu salon bağlantısı geçersiz veya salon artık aktif değil."),
                    )
                }
            }
            // Bildirim tıklaması: randevu detayına (okuma yetkisi Firestore kurallarıyla denetlenir).
            is DeepLink.Appointment -> {
                resolving = true
                viewModelScope.launch {
                    links.consume(link)
                    // Müşteri mi yoksa salon tarafı mı? Okuma yetkisi Firestore kurallarıyla denetlenir.
                    val appt = runCatching { appointments.observe(link.appointmentId).first() }.getOrNull()
                    resolving = false
                    _events.send(
                        if (appt == null) LinkEvent.Failed("Randevu bulunamadı veya görüntüleme yetkiniz yok.")
                        else LinkEvent.OpenAppointment(appt.id, asCustomer = appt.customerUid == session.currentUid),
                    )
                }
            }
        }
    }
}
