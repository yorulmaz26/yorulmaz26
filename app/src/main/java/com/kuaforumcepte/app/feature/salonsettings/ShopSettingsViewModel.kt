package com.kuaforumcepte.app.feature.salonsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.data.location.City
import com.kuaforumcepte.app.data.location.LocationRepository
import com.kuaforumcepte.app.data.salon.GeoPoint
import com.kuaforumcepte.app.data.salon.LocationHelper
import com.kuaforumcepte.app.data.salon.SalonProfile
import com.kuaforumcepte.app.data.salon.SalonProfileInput
import com.kuaforumcepte.app.data.salon.SalonRepository
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.domain.model.MemberRole
import com.kuaforumcepte.domain.model.SalonType
import com.kuaforumcepte.domain.phone.PhoneNumbers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShopForm(
    val name: String = "",
    val type: SalonType? = null,
    val businessPhone: String = "",
    val city: String? = null,
    val district: String? = null,
    val addressLine: String = "",
    val description: String = "",
    val location: GeoPoint? = null,
)

data class ShopSettingsUi(
    val loading: Boolean = true,
    val notAuthorized: Boolean = false,
    val salonId: String? = null,
    val salonCode: String = "",
    val original: ShopForm? = null,
    val form: ShopForm = ShopForm(),
    val cities: List<City> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val locating: Boolean = false,
    val message: UiText? = null,
    val loadError: UiText? = null,
) {
    val dirty: Boolean get() = original != null && original != form
    val districts: List<String> get() = cities.firstOrNull { it.name == form.city }?.districts.orEmpty()
}

private fun SalonProfile.toForm() = ShopForm(
    name = name, type = type, businessPhone = PhoneNumbers.formatForDisplay(businessPhoneE164), city = city,
    district = district, addressLine = addressLine, description = description, location = location,
)

@HiltViewModel
class ShopSettingsViewModel @Inject constructor(
    private val session: SessionRepository,
    private val salons: SalonRepository,
    private val locations: LocationRepository,
    private val locationHelper: LocationHelper,
) : ViewModel() {

    private val _ui = MutableStateFlow(ShopSettingsUi())
    val ui: StateFlow<ShopSettingsUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val active = session.activeSalon.first()
                if (active == null || active.membership.role != MemberRole.OWNER) {
                    _ui.update { it.copy(loading = false, notAuthorized = true) }
                    return@launch
                }
                val cities = locations.cities()
                val salon = salons.observe(active.membership.salonId).first() ?: throw AppException("SALON_NOT_FOUND")
                val form = salon.toForm()
                _ui.update { it.copy(loading = false, salonId = salon.id, salonCode = salon.code, original = form, form = form, cities = cities) }
            } catch (t: Throwable) {
                _ui.update { it.copy(loading = false, loadError = ErrorMessages.of(t)) }
            }
        }
    }

    private fun edit(field: String?, block: (ShopForm) -> ShopForm) =
        _ui.update { it.copy(form = block(it.form), errors = if (field == null) it.errors else it.errors - field) }

    fun onName(v: String) = edit("name") { it.copy(name = v) }
    fun onType(v: SalonType) = edit("type") { it.copy(type = v) }
    fun onPhone(v: String) = edit("businessPhone") { it.copy(businessPhone = v.filter { c -> c.isDigit() || c == ' ' }.take(14)) }
    fun onCity(c: City) = edit("city") { it.copy(city = c.name, district = null) }
    fun onDistrict(d: String) = edit("district") { it.copy(district = d) }
    fun onAddress(v: String) = edit("addressLine") { it.copy(addressLine = v) }
    fun onDescription(v: String) = edit("description") { it.copy(description = v) }
    fun clearLocation() = edit("location") { it.copy(location = null) }
    fun consumeMessage() = _ui.update { it.copy(message = null) }

    fun onLocationPermissionDenied() = _ui.update { it.copy(message = ErrorMessages.of(AppException("LOCATION_PERMISSION"))) }

    fun useCurrentLocation() = locate { locationHelper.currentLocation() }

    fun findFromAddress() {
        val f = _ui.value.form
        if (f.city == null || f.district == null || f.addressLine.isBlank()) {
            _ui.update { it.copy(message = UiText.Plain("Önce il, ilçe ve açık adresi girin.")) }
            return
        }
        locate { locationHelper.geocode("${f.addressLine}, ${f.district}, ${f.city}, Türkiye") }
    }

    private fun locate(block: suspend () -> GeoPoint) {
        if (_ui.value.locating) return
        _ui.update { it.copy(locating = true) }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { p ->
                    edit("location") { it.copy(location = p) }
                    _ui.update { it.copy(locating = false, message = UiText.Plain("Konum bulundu. Lütfen “Haritada kontrol et” ile doğruluğunu onaylayın.")) }
                }
                .onFailure { e -> _ui.update { it.copy(locating = false, message = ErrorMessages.of(e)) } }
        }
    }

    fun save() {
        val s = _ui.value
        val f = s.form
        val salonId = s.salonId ?: return
        val errors = buildMap {
            if (f.name.trim().length !in 2..80) put("name", "2–80 karakter girin")
            if (f.type == null) put("type", "Salon türünü seçin")
            if (PhoneNumbers.normalizeTr(f.businessPhone) == null) put("businessPhone", "Geçerli bir telefon girin")
            if (f.city == null) put("city", "İl seçin")
            if (f.district == null) put("district", "İlçe seçin")
            if (f.addressLine.trim().length !in 5..300) put("addressLine", "Açık adresi girin (en az 5 karakter)")
            if (f.description.length > 1000) put("description", "En fazla 1000 karakter")
        }
        if (errors.isNotEmpty()) { _ui.update { it.copy(errors = errors) }; return }
        if (s.saving) return
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching {
                salons.updateProfile(
                    salonId,
                    SalonProfileInput(f.name, f.type!!, PhoneNumbers.normalizeTr(f.businessPhone)!!, f.city!!, f.district!!, f.addressLine, f.description, f.location),
                )
            }.onSuccess {
                _ui.update { it.copy(saving = false, original = f, message = UiText.Plain("Dükkan bilgileri kaydedildi.")) }
            }.onFailure { e -> _ui.update { it.copy(saving = false, message = ErrorMessages.of(e)) } }
        }
    }
}
