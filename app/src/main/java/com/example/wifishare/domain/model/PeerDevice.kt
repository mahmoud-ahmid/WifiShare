package com.example.wifishare.domain.model

/**
 * جهاز نظير في شبكة Wi-Fi Direct. نموذج نقي بلا اعتماد على Android.
 */
data class PeerDevice(
    val name: String,
    val address: String,
    val isAvailable: Boolean,
    val isGroupOwner: Boolean = false
)
