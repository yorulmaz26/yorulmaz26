package com.kuaforumcepte.app.feature.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.datastore.AppPreferences
import com.kuaforumcepte.app.data.auth.PhoneAuthRepository
import com.kuaforumcepte.app.data.auth.PhoneAuthRepository.Status
import com.kuaforumcepte.domain.phone.PhoneNumbers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhoneEntryUi(
    val digits: String = "",
    val rememberMe: Boolean = true,
    val sending: Boolean = false,
    val fieldError: String? = null,
    val error: UiText? = null,
)

sealed interface PhoneEntryEvent {
    data object CodeSent : PhoneEntryEvent
    data object SignedIn : PhoneEntryEvent
}

@HiltViewModel
class PhoneEntryViewModel @Inject constructor(
    private val phoneAuth: PhoneAuthRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _ui = MutableStateFlow(PhoneEntryUi())
    val ui: StateFlow<PhoneEntryUi> = _ui.asStateFlow()
    private val _events = Channel<PhoneEntryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var awaiting = false

    init {
        viewModelScope.launch { _ui.update { it.copy(rememberMe = preferences.current().rememberMe) } }
        viewModelScope.launch {
            phoneAuth.state.collect { s ->
                if (!awaiting) return@collect
                when (val st = s.status) {
                    Status.Sending, Status.Verifying -> _ui.update { it.copy(sending = true) }
                    Status.CodeSent -> { awaiting = false; _ui.update { it.copy(sending = false) }; _events.send(PhoneEntryEvent.CodeSent) }
                    Status.SignedIn -> { awaiting = false; _ui.update { it.copy(sending = false) }; _events.send(PhoneEntryEvent.SignedIn) }
                    is Status.Failed -> { awaiting = false; _ui.update { it.copy(sending = false, error = ErrorMessages.of(st.error)) } }
                    Status.Idle -> Unit
                }
            }
        }
    }

    /** Yalnızca rakam tutulur; 10 haneyi (5XXXXXXXXX) aşmaz. Başta 0 yazılırsa atılır. */
    fun onDigits(input: String) {
        val d = input.filter(Char::isDigit).trimStart('0').take(10)
        _ui.update { it.copy(digits = d, fieldError = null, error = null) }
    }

    fun onRememberMe(value: Boolean) {
        _ui.update { it.copy(rememberMe = value) }
        viewModelScope.launch { preferences.setRememberMe(value) }
    }

    fun send(activity: Activity?) {
        val e164 = PhoneNumbers.normalizeTr(_ui.value.digits, mobileOnly = true)
        if (e164 == null) {
            _ui.update { it.copy(fieldError = "Geçerli bir cep telefonu girin: 5XX XXX XX XX") }
            return
        }
        if (activity == null) {
            _ui.update { it.copy(error = UiText.Plain("Doğrulama başlatılamadı. Lütfen tekrar deneyin.")) }
            return
        }
        awaiting = true
        phoneAuth.clearError()
        phoneAuth.sendCode(activity, e164)
    }

    fun dismissError() = _ui.update { it.copy(error = null) }
}

/** 5321234567 → "532 123 45 67" (giriş alanı gösterimi). */
fun formatTrMobile(digits: String): String = buildString {
    digits.forEachIndexed { i, c ->
        if (i == 3 || i == 6 || i == 8) append(' ')
        append(c)
    }
}
