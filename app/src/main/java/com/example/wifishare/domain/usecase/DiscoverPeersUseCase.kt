package com.example.wifishare.domain.usecase

import com.example.wifishare.data.wifi.WifiP2pManagerWrapper
import com.example.wifishare.data.wifi.WifiP2pState
import kotlinx.coroutines.flow.StateFlow

class DiscoverPeersUseCase(private val wrapper: WifiP2pManagerWrapper) {
    val state: StateFlow<WifiP2pState> = wrapper.state
    fun start() = wrapper.startDiscovery()
    fun stop() = wrapper.stopDiscovery()
}
