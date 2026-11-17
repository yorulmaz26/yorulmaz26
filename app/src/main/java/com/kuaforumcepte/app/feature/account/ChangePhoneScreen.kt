package com.kuaforumcepte.app.feature.account

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SuccessState
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.app.feature.auth.formatTrMobile
import com.kuaforumcepte.domain.phone.PhoneNumbers
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class ChangePhoneUi(
    val digits: String = "",
    val code: String = "",
    val codeSent: Boolean = false,
    val busy: Boolean = false,
    val done: Boolean = false,
    val error: UiText? = null,
)

/**
 * Telefon numarası değişikliği / hesap kurtarma: yeni numara SMS ile doğrulanır ve Firebase Auth'taki
 * hesaba bağlanır (updatePhoneNumber). Numara başka bir hesapta kayıtlıysa Firebase reddeder.
 */
@HiltViewModel
class ChangePhoneViewModel @Inject constructor(
    private val auth: Lazy<FirebaseAuth>,
    private val session: SessionRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(ChangePhoneUi())
    val ui: StateFlow<ChangePhoneUi> = _ui.asStateFlow()
    private var verificationId: String? = null
    val currentPhone: String get() = session.currentPhone.orEmpty()

    fun onDigits(v: String) = _ui.update { it.copy(digits = v.filter(Char::isDigit).trimStart('0').take(10), error = null) }
    fun onCode(v: String) = _ui.update { it.copy(code = v.filter(Char::isDigit).take(6), error = null) }

    fun send(activity: Activity?) {
        val e164 = PhoneNumbers.normalizeTr(_ui.value.digits, mobileOnly = true)
        if (e164 == null || activity == null) { _ui.update { it.copy(error = ErrorMessages.of(AppException(AppException.INVALID_PHONE))) }; return }
        if (e164 == session.currentPhone) { _ui.update { it.copy(error = UiText.Plain("Bu zaten mevcut numaranız.")) }; return }
        _ui.update { it.copy(busy = true, error = null) }
        val options = PhoneAuthOptions.newBuilder(auth.get())
            .setPhoneNumber(e164).setTimeout(60L, TimeUnit.SECONDS).setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = id
                    _ui.update { it.copy(busy = false, codeSent = true) }
                }
                override fun onVerificationCompleted(credential: PhoneAuthCredential) { link(credential) }
                override fun onVerificationFailed(e: FirebaseException) { _ui.update { it.copy(busy = false, error = ErrorMessages.of(e)) } }
            }).build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verify() {
        val id = verificationId ?: return
        if (_ui.value.code.length != 6) return
        link(PhoneAuthProvider.getCredential(id, _ui.value.code))
    }

    private fun link(credential: PhoneAuthCredential) {
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val user = auth.get().currentUser ?: throw AppException(AppException.NOT_SIGNED_IN)
                user.updatePhoneNumber(credential).await()
                session.syncPhoneNumber()
            }.onSuccess { _ui.update { it.copy(busy = false, done = true) } }
                .onFailure { e -> _ui.update { it.copy(busy = false, error = ErrorMessages.of(e), code = "") } }
        }
    }
}

@Composable
fun ChangePhoneScreen(onBack: () -> Unit, viewModel: ChangePhoneViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    Scaffold(topBar = { KcTopBar("Telefon Numarasını Değiştir", onBack) }) { padding ->
        if (ui.done) {
            SuccessState("Numaranız güncellendi", "Bundan sonra yeni numaranızla giriş yapacaksınız. Aktif randevularınızdaki iletişim numarası da güncellendi.", Modifier.padding(padding), "Tamam", onBack)
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text("Mevcut numara: ${PhoneNumbers.formatForDisplay(viewModel.currentPhone)}", style = MaterialTheme.typography.bodyMedium)
            KcTextField(formatTrMobile(ui.digits), viewModel::onDigits, "Yeni cep telefonu", prefix = "+90 ", keyboardType = KeyboardType.Phone, enabled = !ui.codeSent)
            if (!ui.codeSent) {
                KcPrimaryButton("Doğrulama Kodu Gönder", { viewModel.send(activity) }, loading = ui.busy, enabled = ui.digits.length == 10)
            } else {
                KcTextField(ui.code, viewModel::onCode, "SMS kodu (6 hane)", keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done, onImeAction = viewModel::verify)
                KcPrimaryButton("Numarayı Doğrula ve Değiştir", viewModel::verify, loading = ui.busy, enabled = ui.code.length == 6)
            }
            ui.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            InfoBanner("Telefon numarası hesabınızın kimliğidir. Yeni numara başka bir hesapta kayıtlıysa değişiklik yapılamaz. Salon davetleri ve üyelikleriniz hesabınıza bağlı olduğu için korunur.")
        }
    }
}
