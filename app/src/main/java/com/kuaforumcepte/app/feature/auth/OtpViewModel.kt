package com.kuaforumcepte.app.feature.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.data.auth.PhoneAuthRepository
import com.kuaforumcepte.app.data.auth.PhoneAuthRepository.Status
import com.kuaforumcepte.domain.phone.PhoneNumbers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class OtpUi(
    val phoneDisplay: String = "",
    val code: String = "",
    val verifying: Boolean = false,
    val resending: Boolean = false,
    val secondsUntilResend: Int = 0,
    val error: UiText? = null,
    val sessionLost: Boolean = false,
)

@HiltViewModel
class OtpViewModel @Inject constructor(
    private val phoneAuth: PhoneAuthRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _ui = MutableStateFlow(OtpUi())
    val ui: StateFlow<OtpUi> = _ui.asStateFlow()
    private val _signedIn = Channel<Unit>(Channel.BUFFERED)
    val signedIn = _signedIn.receiveAsFlow()

    init {
        viewModelScope.launch {
            phoneAuth.state.collect { s ->
                _ui.update {
                    it.copy(
                        phoneDisplay = s.phoneE164?.let(PhoneNumbers::formatForDisplay).orEmpty(),
                        // Süreç sonlandırıldıysa doğrulama kimliği kaybolur → yeniden gönderim gerekir.
                        sessionLost = s.verificationId == null && s.status !is Status.Sending,
                        resending = s.status == Status.Sending,
                        verifying = s.status == Status.Verifying,
                    )
                }
                when (val st = s.status) {
                    Status.SignedIn -> _signedIn.send(Unit)
                    is Status.Failed -> _ui.update { it.copy(error = ErrorMessages.of(st.error), code = "") }
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                val left = ((phoneAuth.state.value.resendAvailableAtMillis() - clock.millis()) / 1000).coerceAtLeast(0)
                _ui.update { it.copy(secondsUntilResend = left.toInt()) }
                delay(1000)
            }
        }
    }

    fun onCode(input: String) {
        val code = input.filter(Char::isDigit).take(6)
        _ui.update { it.copy(code = code, error = null) }
        if (code.length == 6) verify()
    }

    fun verify() {
        val code = _ui.value.code
        if (code.length != 6 || _ui.value.verifying) return
        phoneAuth.clearError()
        viewModelScope.launch { runCatching { phoneAuth.verifyCode(code) } }
    }

    fun resend(activity: Activity?) {
        val phone = phoneAuth.state.value.phoneE164 ?: return
        if (_ui.value.secondsUntilResend > 0 || activity == null) return
        _ui.update { it.copy(error = null, code = "") }
        phoneAuth.clearError()
        phoneAuth.sendCode(activity, phone, resend = true)
    }
}
