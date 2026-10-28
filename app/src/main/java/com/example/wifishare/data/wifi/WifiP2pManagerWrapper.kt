package com.example.wifishare.data.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import androidx.core.content.ContextCompat
import com.example.wifishare.core.Logger
import com.example.wifishare.core.PermissionHelper
import com.example.wifishare.domain.model.PeerDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * غلاف إنتاجي حول WifiP2pManager:
 * - تهيئة idempotent.
 * - BroadcastReceiver آمن (يُلغى في dispose).
 * - دفق حالة موحّد.
 * - معالجة SecurityException في كل نقطة.
 *
 * يُنشأ مرة واحدة في AppContainer (Application scope) ولا يُكرر.
 */
@SuppressLint("MissingPermission")
class WifiP2pManagerWrapper(private val appContext: Context) {

    private val manager: WifiP2pManager? =
        appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false

    private val _state = MutableStateFlow<WifiP2pState>(WifiP2pState.Idle)
    val state: StateFlow<WifiP2pState> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> handleP2pStateChanged(intent)
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> handlePeersChanged()
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> handleConnectionChanged()
            }
        }
    }

    fun isSupported(): Boolean = manager != null

    private fun ensureInitialized(): Boolean {
        if (channel != null) return true
        val mgr = manager ?: run {
            _state.value = WifiP2pState.NotSupported
            return false
        }
        channel = mgr.initialize(appContext, appContext.mainLooper) {
            Logger.w("WifiP2p channel disconnected")
            _state.value = WifiP2pState.Failed("Channel disconnected")
        }
        registerReceiver()
        return true
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(
            appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    fun startDiscovery() {
        if (!ensureInitialized()) return
        if (!hasPermissions()) {
            _state.value = WifiP2pState.Failed("Permissions missing")
            return
        }
        _state.value = WifiP2pState.Discovering
        try {
            manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { Logger.d("Discovery started") }
                override fun onFailure(reason: Int) {
                    _state.value = WifiP2pState.Failed(mapFailure(reason))
                }
            })
        } catch (e: SecurityException) {
            _state.value = WifiP2pState.Failed("Security: ${e.message}")
        }
    }

    fun stopDiscovery() {
        val ch = channel ?: return
        try {
            manager?.stopPeerDiscovery(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {}
                override fun onFailure(reason: Int) {}
            })
        } catch (_: SecurityException) {}
    }

    fun connect(device: PeerDevice) {
        if (!ensureInitialized()) return
        if (!hasPermissions()) {
            _state.value = WifiP2pState.Failed("Permissions missing")
            return
        }
        _state.value = WifiP2pState.Connecting(device)
        val config = WifiP2pConfig().apply {
            deviceAddress = device.address
            wps.setup = WpsInfo.PBC
        }
        try {
            manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { Logger.d("Connect requested to ${device.name}") }
                override fun onFailure(reason: Int) {
                    _state.value = WifiP2pState.Failed(mapFailure(reason))
                }
            })
        } catch (e: SecurityException) {
            _state.value = WifiP2pState.Failed("Security: ${e.message}")
        }
    }

    fun disconnect() {
        val ch = channel ?: return
        try {
            manager?.removeGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { _state.value = WifiP2pState.Idle }
                override fun onFailure(reason: Int) { _state.value = WifiP2pState.Idle }
            })
        } catch (_: SecurityException) {}
    }

    private fun handleP2pStateChanged(intent: Intent) {
        val enabled = intent.getIntExtra(
            WifiP2pManager.EXTRA_WIFI_STATE,
            WifiP2pManager.WIFI_P2P_STATE_DISABLED
        ) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
        if (!enabled) {
            _state.value = WifiP2pState.WifiDisabled
        } else if (_state.value is WifiP2pState.WifiDisabled) {
            _state.value = WifiP2pState.Idle
        }
    }

    private fun handlePeersChanged() {
        if (!hasPermissions()) return
        val ch = channel ?: return
        try {
            manager?.requestPeers(ch) { peerList ->
                val peers = peerList.deviceList
                    .filter { it.status == WifiP2pDevice.AVAILABLE }
                    .map { it.toPeerDevice() }
                _state.value = if (peers.isEmpty()) WifiP2pState.Discovering
                else WifiP2pState.PeersAvailable(peers)
            }
        } catch (_: SecurityException) {}
    }

    private fun handleConnectionChanged() {
        if (!hasPermissions()) return
        val ch = channel ?: return
        try {
            manager?.requestConnectionInfo(ch) { info ->
                if (info.groupFormed) {
                    _state.value = WifiP2pState.Connected(
                        groupOwnerAddress = info.groupOwnerAddress?.hostAddress ?: "",
                        isGroupOwner = info.isGroupOwner
                    )
                } else if (_state.value is WifiP2pState.Connected) {
                    _state.value = WifiP2pState.Idle
                }
            }
        } catch (_: SecurityException) {}
    }

    private fun hasPermissions(): Boolean =
        PermissionHelper.hasAll(appContext, PermissionHelper.wifiPermissions())

    private fun mapFailure(reason: Int): String = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "P2P unsupported"
        WifiP2pManager.BUSY -> "WifiP2p busy"
        WifiP2pManager.ERROR -> "WifiP2p error"
        else -> "Unknown error ($reason)"
    }

    private fun WifiP2pDevice.toPeerDevice() = PeerDevice(
        name = deviceName?.takeIf { it.isNotBlank() } ?: deviceAddress ?: "Unknown",
        address = deviceAddress ?: "",
        isAvailable = status == WifiP2pDevice.AVAILABLE
    )

    fun dispose() {
        stopDiscovery()
        if (receiverRegistered) {
            try { appContext.unregisterReceiver(receiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
        channel?.close()
        channel = null
    }
}
