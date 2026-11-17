package com.kuaforumcepte.app.core.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.StorageException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Uygulama genelinde tek hata tipi. `reason`, sunucudaki `details.reason` (docs/API_CONTRACTS.md)
 * veya istemci tarafı nedenidir; kullanıcı mesajı ErrorMessages ile üretilir.
 */
class AppException(
    val reason: String,
    val details: Map<String, Any?> = emptyMap(),
    cause: Throwable? = null,
) : Exception(reason, cause) {

    val isNetwork: Boolean get() = reason == NETWORK

    companion object {
        const val NETWORK = "NETWORK"
        const val UNKNOWN = "UNKNOWN"
        const val RATE_LIMITED = "RATE_LIMITED"
        const val INVALID_CODE = "INVALID_CODE"
        const val CODE_EXPIRED = "CODE_EXPIRED"
        const val INVALID_PHONE = "INVALID_PHONE"
        const val PERMISSION_DENIED = "PERMISSION_DENIED"
        const val NOT_SIGNED_IN = "UNAUTHENTICATED"

        fun from(t: Throwable): AppException = when (t) {
            is AppException -> t
            is CancellationException -> throw t
            is FirebaseFunctionsException -> {
                @Suppress("UNCHECKED_CAST")
                val details = (t.details as? Map<String, Any?>) ?: emptyMap()
                val reason = details["reason"] as? String ?: when (t.code) {
                    FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> NETWORK
                    FirebaseFunctionsException.Code.UNAUTHENTICATED -> NOT_SIGNED_IN
                    FirebaseFunctionsException.Code.PERMISSION_DENIED -> PERMISSION_DENIED
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> RATE_LIMITED
                    else -> UNKNOWN
                }
                AppException(reason, details, t)
            }
            is FirebaseTooManyRequestsException -> AppException(RATE_LIMITED, cause = t)
            is FirebaseNetworkException, is IOException -> AppException(NETWORK, cause = t)
            is FirebaseAuthInvalidCredentialsException -> when (t.errorCode) {
                "ERROR_INVALID_VERIFICATION_CODE" -> AppException(INVALID_CODE, cause = t)
                "ERROR_SESSION_EXPIRED", "ERROR_CODE_EXPIRED" -> AppException(CODE_EXPIRED, cause = t)
                "ERROR_INVALID_PHONE_NUMBER" -> AppException(INVALID_PHONE, cause = t)
                "ERROR_INVALID_APP_CREDENTIAL", "ERROR_MISSING_APP_CREDENTIAL", "ERROR_APP_NOT_AUTHORIZED" ->
                    AppException("AUTH_APP_NOT_AUTHORIZED", mapOf("code" to t.errorCode), t)
                else -> AppException(INVALID_CODE, cause = t)
            }
            is FirebaseAuthException -> when (t.errorCode) {
                "ERROR_TOO_MANY_REQUESTS", "ERROR_QUOTA_EXCEEDED" -> AppException(RATE_LIMITED, cause = t)
                "ERROR_OPERATION_NOT_ALLOWED" -> AppException("AUTH_PHONE_DISABLED", cause = t)
                "ERROR_APP_NOT_AUTHORIZED", "ERROR_MISSING_CLIENT_IDENTIFIER", "ERROR_INVALID_APP_CREDENTIAL",
                "ERROR_MISSING_APP_CREDENTIAL" -> AppException("AUTH_APP_NOT_AUTHORIZED", mapOf("code" to t.errorCode), t)
                "ERROR_SESSION_EXPIRED" -> AppException(CODE_EXPIRED, cause = t)
                "ERROR_CREDENTIAL_ALREADY_IN_USE", "ERROR_PROVIDER_ALREADY_LINKED" -> AppException("ERROR_CREDENTIAL_ALREADY_IN_USE", cause = t)
                else -> AppException(UNKNOWN, mapOf("code" to t.errorCode), t)
            }
            is FirebaseFirestoreException -> when (t.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE -> AppException(NETWORK, cause = t)
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppException(PERMISSION_DENIED, cause = t)
                else -> AppException(UNKNOWN, cause = t)
            }
            is StorageException -> when (t.errorCode) {
                StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> AppException(NETWORK, cause = t)
                StorageException.ERROR_NOT_AUTHORIZED -> AppException("UPLOAD_REJECTED", cause = t)
                else -> AppException("UPLOAD_FAILED", cause = t)
            }
            else -> {
                val msg = t.message.orEmpty()
                when {
                    msg.contains("BILLING_NOT_ENABLED", true) -> AppException("AUTH_BILLING", cause = t)
                    msg.contains("app identifier", true) || msg.contains("Play Integrity", true) || msg.contains("reCAPTCHA", true) ->
                        AppException("AUTH_APP_NOT_AUTHORIZED", mapOf("code" to msg.take(80)), t)
                    // Tanınmayan hata: ekrana kısa teşhis bilgisi taşınır (destek/hata ayıklama için).
                    else -> AppException(UNKNOWN, mapOf("code" to (t.javaClass.simpleName + ": " + msg).take(120)), t)
                }
            }
        }
    }
}

/** suspend bloğu çalıştırır; her hatayı AppException'a çevirir (iptal hariç). */
suspend fun <T> appResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    Result.failure(AppException.from(t))
}
