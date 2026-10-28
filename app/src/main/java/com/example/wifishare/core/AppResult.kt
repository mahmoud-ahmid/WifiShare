package com.example.wifishare.core

/**
 * نتيجة موحّدة لكل عمليات التطبيق.
 * Success / Error / Loading.
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(
        val code: ErrorCode,
        val message: String,
        val cause: Throwable? = null
    ) : AppResult<Nothing>()
    data object Loading : AppResult<Nothing>()
}

enum class ErrorCode {
    PERMISSION_DENIED,
    WIFI_DISABLED,
    WIFI_P2P_UNSUPPORTED,
    CONNECTION_FAILED,
    TRANSFER_FAILED,
    IO_ERROR,
    INVALID_INPUT,
    UNKNOWN
}
