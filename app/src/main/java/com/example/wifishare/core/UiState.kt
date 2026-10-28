package com.example.wifishare.core

/**
 * حالة الواجهة الموحّدة. تُستخدم مع StateFlow في ViewModels.
 * Idle: لا شيء بعد. Loading: جارٍ العمل. Success: نجاح. Error: فشل.
 */
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>
}
