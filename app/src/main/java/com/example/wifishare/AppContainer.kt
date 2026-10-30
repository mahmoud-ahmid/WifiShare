package com.example.wifishare

import android.content.Context
import com.example.wifishare.data.files.MediaStoreRepository
import com.example.wifishare.data.send.SendRepository
import com.example.wifishare.data.files.ReceivedFilesRepository
import com.example.wifishare.data.history.HistoryRepository
import com.example.wifishare.data.transfer.TransferClient
import com.example.wifishare.data.transfer.TransferServer
import com.example.wifishare.data.wifi.WifiP2pManagerWrapper
import com.example.wifishare.data.wifi.HotspotManager
import com.example.wifishare.data.wifi.WifiConnector
import com.example.wifishare.domain.usecase.ConnectToPeerUseCase
import com.example.wifishare.domain.usecase.DiscoverPeersUseCase

class AppContainer(private val appContext: Context) {
    val hotspotManager: HotspotManager by lazy { HotspotManager(appContext) }
    val wifiConnector: WifiConnector by lazy { WifiConnector(appContext) }
    val wifiP2pManager: WifiP2pManagerWrapper by lazy { WifiP2pManagerWrapper(appContext) }
    val discoverPeersUseCase: DiscoverPeersUseCase by lazy { DiscoverPeersUseCase(wifiP2pManager) }
    val connectToPeerUseCase: ConnectToPeerUseCase by lazy { ConnectToPeerUseCase(wifiP2pManager) }
    val transferClient: TransferClient by lazy { TransferClient(appContext.contentResolver) }
    val transferServer: TransferServer by lazy { TransferServer(appContext) }
    val historyRepository: HistoryRepository by lazy { HistoryRepository(appContext) }
    val receivedFilesRepository: ReceivedFilesRepository by lazy { ReceivedFilesRepository(appContext) }
    val mediaStoreRepository: MediaStoreRepository by lazy { MediaStoreRepository(appContext) }
    val sendRepository: SendRepository by lazy { SendRepository(appContext) }

    fun dispose() {
        hotspotManager.stopHotspot()
        wifiConnector.disconnect()
        wifiP2pManager.dispose()
    }
}
