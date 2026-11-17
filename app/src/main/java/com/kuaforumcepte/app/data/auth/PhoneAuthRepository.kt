package com.kuaforumcepte.app.data.auth

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.di.ApplicationScope
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Phone Auth (SMS OTP). Kod Firebase tarafından üretilir/gönderilir/doğrulanır;
 * uygulama kodu hiçbir zaman kendisi üretmez veya karşılaştırmaz.
 * Telefon girişi ve OTP ekranı aynı durumu paylaşır (singleton), ekran döndürmeye dayanıklıdır.
 */
@Singleton
class PhoneAuthRepository @Inject constructor(
    private val auth: Lazy<FirebaseAuth>,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    sealed interface Status {
        data object Idle : Status
        data object Sending : Status
        data object CodeSent : Status
        data object Verifying : Status
        data object SignedIn : Status
        data class Failed(val error: AppException) : Status
    }

    data class State(
        val phoneE164: String? = null,
        val verificationId: String? = null,
        val resendToken: PhoneAuthProvider.ForceResendingToken? = null,
        val codeSentAtMillis: Long? = null,
        val status: Status = Status.Idle,
    ) {
        fun resendAvailableAtMillis(): Long = (codeSentAtMillis ?: 0L) + RESEND_COOLDOWN_SECONDS * 1000
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Kod gönderir. Aynı numaraya tekrar gönderimde resendToken kullanılır (Firebase hız limitine tabidir). */
    fun sendCode(activity: Activity, phoneE164: String, resend: Boolean = false) {
        val current = _state.value
        if (current.status == Status.Sending) return
        val token = if (resend && current.phoneE164 == phoneE164) current.resendToken else null
        _state.value = current.copy(
            phoneE164 = phoneE164,
            verificationId = if (current.phoneE164 == phoneE164) current.verificationId else null,
            status = Status.Sending,
        )

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onCodeSent(id: String, t: PhoneAuthProvider.ForceResendingToken) {
                _state.update {
                    it.copy(verificationId = id, resendToken = t, codeSentAtMillis = clock.millis(), status = Status.CodeSent)
                }
            }

            /** Otomatik SMS okuma / anında doğrulama (bazı cihazlarda). */
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                scope.launch { signIn(credential) }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _state.update { it.copy(status = Status.Failed(AppException.from(e))) }
            }
        }

        val builder = PhoneAuthOptions.newBuilder(auth.get())
            .setPhoneNumber(phoneE164)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        if (token != null) builder.setForceResendingToken(token)
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    /** Kullanıcının girdiği 6 haneli kodu Firebase ile doğrular. */
    suspend fun verifyCode(code: String): FirebaseUser {
        val id = _state.value.verificationId ?: throw AppException(AppException.CODE_EXPIRED)
        return signIn(PhoneAuthProvider.getCredential(id, code))
    }

    private suspend fun signIn(credential: PhoneAuthCredential): FirebaseUser {
        _state.update { it.copy(status = Status.Verifying) }
        try {
            val user = auth.get().signInWithCredential(credential).await().user
                ?: throw AppException(AppException.UNKNOWN)
            _state.update { it.copy(status = Status.SignedIn) }
            return user
        } catch (t: Throwable) {
            val e = AppException.from(t)
            _state.update { it.copy(status = Status.Failed(e)) }
            throw e
        }
    }

    fun clearError() = _state.update { if (it.status is Status.Failed) it.copy(status = if (it.verificationId != null) Status.CodeSent else Status.Idle) else it }

    fun reset() {
        _state.value = State()
    }

    companion object {
        const val RESEND_COOLDOWN_SECONDS = 60L
    }
}
