package com.example.wifishare.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wifishare.data.wifi.WifiP2pState
import com.example.wifishare.domain.model.PeerDevice
import com.example.wifishare.domain.usecase.ConnectToPeerUseCase
import com.example.wifishare.domain.usecase.DiscoverPeersUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DiscoverViewModel(
    private val discover: DiscoverPeersUseCase,
    private val connect: ConnectToPeerUseCase
) : ViewModel() {

    val state: StateFlow<WifiP2pState> = discover.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WifiP2pState.Idle
    )

    fun startDiscovery() = discover.start()
    fun stopDiscovery() = discover.stop()
    fun connectTo(device: PeerDevice) = connect(device)
    fun disconnect() = connect.disconnect()

    class Factory(
        private val discover: DiscoverPeersUseCase,
        private val connect: ConnectToPeerUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DiscoverViewModel::class.java))
            return DiscoverViewModel(discover, connect) as T
        }
    }
}
