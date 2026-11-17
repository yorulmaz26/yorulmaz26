package com.kuaforumcepte.app.feature.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.navigation.BookingSummaryRoute
import com.kuaforumcepte.app.data.booking.BookingRepository
import com.kuaforumcepte.app.data.booking.PricePreview
import com.kuaforumcepte.app.data.salon.SalonProfile
import com.kuaforumcepte.app.data.salon.SalonRepository
import com.kuaforumcepte.app.data.services.ServiceRepository
import com.kuaforumcepte.app.data.services.StaffService
import com.kuaforumcepte.app.data.staff.StaffMember
import com.kuaforumcepte.app.data.staff.StaffRepository
import com.kuaforumcepte.domain.model.Kurus
import com.kuaforumcepte.domain.model.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class SummaryData(val salon: SalonProfile, val staff: StaffMember, val items: List<StaffService>) {
    val total: Kurus get() = items.map { it.price }.sum()
    val minutes: Int get() = items.sumOf { it.durationMinutes }
}

sealed interface SummaryProblem {
    data class PriceChanged(val newTotal: Kurus) : SummaryProblem
    /** Saat dolu/uygunsuz: kullanıcı saat seçimine geri döner. */
    data class SlotGone(val message: UiText) : SummaryProblem
    data class ActiveExists(val appointmentId: String?) : SummaryProblem
}

sealed interface SummaryEvent {
    data class Created(val appointmentId: String) : SummaryEvent
    data object BackToSlots : SummaryEvent
}

data class SummaryUi(
    val loading: Boolean = true,
    val data: SummaryData? = null,
    /** Kullanıcının onayladığı (ekranda gördüğü) toplam; sunucu bununla karşılaştırır. */
    val expectedTotal: Kurus? = null,
    val submitting: Boolean = false,
    val problem: SummaryProblem? = null,
    val error: UiText? = null,
    /** Başkası adına randevuda hizmeti alacak kişinin adı. */
    val guestName: String = "",
    val guestNameError: String? = null,
    val codeInput: String = "",
    /** Önizlemede geçerli bulunan ve randevuya uygulanacak kod. */
    val appliedCode: String? = null,
    val codeError: String? = null,
    val useLoyalty: Boolean = false,
    val previewing: Boolean = false,
    val preview: PricePreview? = null,
)

@HiltViewModel
class BookingSummaryViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val salons: SalonRepository,
    private val staff: StaffRepository,
    private val services: ServiceRepository,
    private val booking: BookingRepository,
) : ViewModel() {
    val route = savedState.toRoute<BookingSummaryRoute>()
    val startAt: Instant = Instant.parse(route.startAt)
    /** Ekran yeniden oluşsa bile aynı anahtar → ağ tekrarında ikinci randevu oluşmaz. */
    private val idempotencyKey: String = savedState["key"] ?: UUID.randomUUID().toString().also { savedState["key"] = it }

    private val _ui = MutableStateFlow(SummaryUi())
    val ui: StateFlow<SummaryUi> = _ui.asStateFlow()
    private val _events = Channel<SummaryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { load() }

    fun setGuestName(v: String) = _ui.update { it.copy(guestName = v, guestNameError = null) }
    fun setCodeInput(v: String) = _ui.update { it.copy(codeInput = v.uppercase().filter { c -> c.isLetterOrDigit() }.take(16), codeError = null) }

    /** Fiyat önizlemesi (kod/ödül). Geçersiz kod randevuya uygulanmaz. */
    private fun refreshPreview(code: String?, loyalty: Boolean) {
        _ui.update { it.copy(previewing = true) }
        viewModelScope.launch {
            runCatching { booking.preview(route.salonId, route.staffUid, route.serviceIds, code, loyalty) }
                .onSuccess { p ->
                    _ui.update {
                        it.copy(
                            previewing = false, preview = p,
                            appliedCode = if (code != null && p.codeError == null) code else null,
                            codeError = p.codeError?.let { r -> ErrorMessages.text(AppException(r)) },
                            useLoyalty = loyalty && p.loyalty?.rewardsAvailable?.let { n -> n > 0 } == true,
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(previewing = false, error = ErrorMessages.of(e)) } }
        }
    }

    fun applyCode() {
        val code = _ui.value.codeInput.trim()
        if (code.length < 4) { _ui.update { it.copy(codeError = "Kod en az 4 karakter olmalı.") }; return }
        refreshPreview(code, false)
    }

    fun removeCode() { _ui.update { it.copy(codeInput = "", appliedCode = null, codeError = null) }; refreshPreview(null, _ui.value.useLoyalty) }

    fun setUseLoyalty(v: Boolean) { _ui.update { it.copy(appliedCode = if (v) null else it.appliedCode) }; refreshPreview(if (v) null else _ui.value.appliedCode, v) }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val salon = salons.observe(route.salonId).first() ?: throw AppException("SALON_NOT_FOUND")
                val member = staff.members(route.salonId).first().firstOrNull { it.uid == route.staffUid } ?: throw AppException("STAFF_INACTIVE")
                val active = services.observeActiveOf(route.salonId, route.staffUid).first()
                val items = route.serviceIds.map { id -> active.firstOrNull { it.id == id } ?: throw AppException("SERVICE_INACTIVE") }
                val data = SummaryData(salon, member, items)
                _ui.update { it.copy(loading = false, data = data, expectedTotal = it.expectedTotal ?: data.total) }
                refreshPreview(_ui.value.appliedCode, _ui.value.useLoyalty)
            } catch (t: Throwable) {
                _ui.update { it.copy(loading = false, error = ErrorMessages.of(t)) }
            }
        }
    }

    fun confirm() {
        val s = _ui.value
        val expected = s.expectedTotal ?: return
        if (s.submitting) return
        val guest = s.guestName.trim().replace(Regex("\\s+"), " ")
        if (!route.forSelf && !Regex("^\\p{L}[\\p{L} .'-]{0,48}[\\p{L}.]$").matches(guest)) {
            _ui.update { it.copy(guestNameError = "Hizmeti alacak kişinin adını ve soyadını yazın.") }
            return
        }
        _ui.update { it.copy(submitting = true, problem = null) }
        viewModelScope.launch {
            try {
                val created = booking.create(
                    route.salonId, route.staffUid, route.serviceIds, startAt, expected, idempotencyKey,
                    guestName = if (route.forSelf) null else guest, discountCode = s.appliedCode, useLoyaltyReward = s.useLoyalty,
                )
                _ui.update { it.copy(submitting = false) }
                _events.send(SummaryEvent.Created(created.id))
            } catch (t: Throwable) {
                val e = AppException.from(t)
                val problem = when (e.reason) {
                    "PRICE_CHANGED" -> SummaryProblem.PriceChanged(Kurus((e.details["newTotalKurus"] as? Number)?.toLong() ?: 0))
                    "SLOT_TAKEN", "SLOT_UNAVAILABLE", "CUSTOMER_BUSY" -> SummaryProblem.SlotGone(ErrorMessages.of(e))
                    "ACTIVE_APPOINTMENT_EXISTS", "ACTIVE_APPOINTMENT_LIMIT" -> SummaryProblem.ActiveExists(e.details["appointmentId"] as? String)
                    else -> null
                }
                _ui.update { it.copy(submitting = false, problem = problem, error = if (problem == null) ErrorMessages.of(e) else null) }
                if (e.reason == "SERVICE_INACTIVE" || e.reason == "STAFF_INACTIVE") load()
                if (e.reason.startsWith("CODE_") || e.reason == "LOYALTY_NOT_AVAILABLE") {
                    _ui.update { it.copy(appliedCode = null, useLoyalty = false) }
                    refreshPreview(null, false)
                }
            }
        }
    }

    /** Fiyat değişikliğinde: güncel bilgileri yükle ve kullanıcı yeni tutarı açıkça onaylasın. */
    fun acceptNewPrice() {
        val p = _ui.value.problem as? SummaryProblem.PriceChanged ?: return
        _ui.update { it.copy(problem = null, expectedTotal = p.newTotal) }
        load()
    }

    fun dismissProblem() = _ui.update { it.copy(problem = null) }
    fun dismissError() = _ui.update { it.copy(error = null) }
    fun backToSlots() = viewModelScope.launch { _ui.update { it.copy(problem = null) }; _events.send(SummaryEvent.BackToSlots) }
}
