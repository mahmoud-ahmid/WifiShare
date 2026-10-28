package com.example.wifishare.data.transfer

import com.example.wifishare.domain.model.TransferDirection

data class TransferProgress(
    val direction: TransferDirection = TransferDirection.SEND,
    val currentFileName: String = "",
    val fileIndex: Int = 0,
    val totalFiles: Int = 0,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val bytesPerSecond: Long = 0L,
    val isRunning: Boolean = false,
    val isDone: Boolean = false,
    val errorMessage: String? = null
) {
    val percent: Int
        get() = if (totalBytes <= 0L) 0
        else ((bytesTransferred.coerceAtMost(totalBytes) * 100) / totalBytes).toInt()

    val etaSeconds: Long
        get() = if (bytesPerSecond <= 0L) 0L
        else (totalBytes - bytesTransferred).coerceAtLeast(0L) / bytesPerSecond
}
