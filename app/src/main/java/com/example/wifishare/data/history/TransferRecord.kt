package com.example.wifishare.data.history

data class TransferRecord(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val direction: String,   // "SEND" or "RECEIVE"
    val peerName: String,
    val timestampMs: Long,
    val success: Boolean,
    val savedPath: String? = null
)
