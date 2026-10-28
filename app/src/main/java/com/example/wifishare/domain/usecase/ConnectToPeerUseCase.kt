package com.example.wifishare.domain.usecase

import com.example.wifishare.data.wifi.WifiP2pManagerWrapper
import com.example.wifishare.domain.model.PeerDevice

class ConnectToPeerUseCase(private val wrapper: WifiP2pManagerWrapper) {
    operator fun invoke(device: PeerDevice) = wrapper.connect(device)
    fun disconnect() = wrapper.disconnect()
}
