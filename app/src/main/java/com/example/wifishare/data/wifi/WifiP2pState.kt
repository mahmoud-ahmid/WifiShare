package com.example.wifishare.data.wifi

import com.example.wifishare.domain.model.PeerDevice

/** الحالة الحالية لشبكة Wi-Fi Direct. */
sealed interface WifiP2pState {
    data object Idle : WifiP2pState
    data object WifiDisabled : WifiP2pState
    data object NotSupported : WifiP2pState
    data object Discovering : WifiP2pState
    data class PeersAvailable(val peers: List<PeerDevice>) : WifiP2pState
    data class Connecting(val device: PeerDevice) : WifiP2pState
    data class Connected(val groupOwnerAddress: String, val isGroupOwner: Boolean) : WifiP2pState
    data class Failed(val reason: String) : WifiP2pState
}
