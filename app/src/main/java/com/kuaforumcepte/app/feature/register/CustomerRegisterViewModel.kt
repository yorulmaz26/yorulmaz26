package com.kuaforumcepte.app.feature.register

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.data.registration.RegistrationRepository
import com.kuaforumcepte.app.data.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CustomerRegisterUi(
    val firstName: String = "",
    val lastName: String = "",
    val kvkk: Boolean = false,
    val terms: Boolean = false,
    val submitting: Boolean = false,
    val firstNameError: String? = null,
    val lastNameError: String? = null,
    val error: UiText? = null,
)

internal val NAME_REGEX = Regex("^[\\p{L}][\\p{L} .'-]{0,48}[\\p{L}.]$")
internal fun nameError(v: String): String? = if (NAME_REGEX.matches(v.trim())) null else "2–50 harf girin"

@HiltViewModel
class CustomerRegisterViewModel @Inject constructor(
    private val registration: RegistrationRepository,
    private val session: SessionRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        CustomerRegisterUi(firstName = savedState["first"] ?: "", lastName = savedState["last"] ?: ""),
    )
    val ui: StateFlow<CustomerRegisterUi> = _ui.asStateFlow()
    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    /** Ağ kopup tekrar denenirse aynı istek tekrar profil açmaz. */
    private val idempotencyKey: String = savedState["key"] ?: UUID.randomUUID().toString().also { savedState["key"] = it }

    val phoneDisplay: String get() = session.currentPhone.orEmpty()

    fun onFirst(v: String) { savedState["first"] = v; _ui.update { it.copy(firstName = v, firstNameError = null) } }
    fun onLast(v: String) { savedState["last"] = v; _ui.update { it.copy(lastName = v, lastNameError = null) } }
    fun onKvkk(v: Boolean) = _ui.update { it.copy(kvkk = v) }
    fun onTerms(v: Boolean) = _ui.update { it.copy(terms = v) }
    fun dismissError() = _ui.update { it.copy(error = null) }

    fun submit() {
        val s = _ui.value
        val fe = nameError(s.firstName)
        val le = nameError(s.lastName)
        if (fe != null || le != null) { _ui.update { it.copy(firstNameError = fe, lastNameError = le) }; return }
        if (!s.kvkk || !s.terms || s.submitting) return
        _ui.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            runCatching { registration.registerCustomer(s.firstName, s.lastName, idempotencyKey) }
                .onSuccess { _ui.update { it.copy(submitting = false) }; _done.send(Unit) }
                .onFailure { e -> _ui.update { it.copy(submitting = false, error = ErrorMessages.of(e)) } }
        }
    }
}
