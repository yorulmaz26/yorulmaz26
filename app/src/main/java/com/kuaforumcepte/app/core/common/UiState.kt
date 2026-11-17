package com.kuaforumcepte.app.core.common

/** Tüm ekranlarda ortak yükleme/başarı/boş/hata durumu. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Empty(val message: String? = null) : UiState<Nothing>
    data class Error(val message: UiText, val retryable: Boolean = true) : UiState<Nothing>
}

/** ViewModel'de Context olmadan metin üretmek için. */
sealed interface UiText {
    data class Plain(val value: String) : UiText
    data class Res(val id: Int, val args: List<Any> = emptyList()) : UiText
}
