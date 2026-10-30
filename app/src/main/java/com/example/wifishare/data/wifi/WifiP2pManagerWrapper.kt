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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@SuppressLint("MissingPermission")
class WifiP2pManagerWrapper(private val appContext: Context) {

    private val manager: WifiP2pManager? =
        appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow<WifiP2pState>(WifiP2pState.Idle)
    val state: StateFlow<WifiP2pState> = _state.asStateFlow()

    @Volatile private var p2pEnabled = false
    @Volatile private var attempts = 0
    private var discoveryJob: Job? = null
    private var pollJob: Job? = null
    @Volatile private var pendingGroupIpCallback: ((String?) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val enabled = intent.getIntExtra(
                        WifiP2pManager.EXTRA_WIFI_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED
                    ) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    if (enabled != p2pEnabled) p2pEnabled = enabled
                    if (!p2pEnabled) _state.value = WifiP2pState.WifiDisabled
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> handleConnectionChanged()
            }
        }
    }

    init { initializeChannel(); registerReceiver() }

    private fun initializeChannel() {
        val mgr = manager ?: run {
            _state.value = WifiP2pState.NotSupported
            return
        }
        channel = mgr.initialize(appContext, appContext.mainLooper) {
            channel = null
        }
    }

    private fun ensureChannel(): WifiP2pManager.Channel? {
        if (channel == null) initializeChannel()
        return channel
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        val f = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(appContext, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    // ═══════════════════════════════════════════════════
    // إنشاء مجموعة → نصبح Group Owner (IP: 192.168.49.1)
    // ═══════════════════════════════════════════════════
    fun createGroup(onResult: (String?) -> Unit) {
        if (manager == null) { onResult(null); return }
        if (!hasPermissions()) { onResult(null); return }

        val ch = ensureChannel() ?: run { onResult(null); return }

        // احذف أي مجموعة قديمة
        try { manager.removeGroup(ch, null) } catch (_: SecurityException) {}
        delaySync(500)

        pendingGroupIpCallback = onResult
        try {
            manager.createGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Logger.i("Group created — we are group owner")
                    // انتظر معلومات الاتصال (IP)
                    scope.launch {
                        repeat(10) {
                            delay(500)
                            val ip = fetchOwnGroupIp()
                            if (ip != null) {
                                pendingGroupIpCallback?.invoke(ip)
                                pendingGroupIpCallback = null
                                return@launch
                            }
                        }
                        // فشل الحصول على IP
                        pendingGroupIpCallback?.invoke(null)
                        pendingGroupIpCallback = null
                    }
                }
                override fun onFailure(reason: Int) {
                    Logger.w("createGroup failed: $reason")
                    pendingGroupIpCallback?.invoke(null)
                    pendingGroupIpCallback = null
                }
            })
        } catch (_: SecurityException) {
            onResult(null)
        }
    }

    private fun fetchOwnGroupIp(): String? {
        val ch = channel ?: return null
        try {
            manager?.requestConnectionInfo(ch) { info ->
                // non-blocking; handled via state
            }
            // نستخدم requestGroupInfo
            var ip: String? = null
            manager?.requestGroupInfo(ch) { group ->
                if (group != null && group.isGroupOwner) {
                    ip = "192.168.49.1"
                }
            }
            return ip ?: "192.168.49.1" // افتراضي لمالك المجموعة
        } catch (_: SecurityException) { return null }
    }

    // ═══════════════════════════════════════════════════
    // اكتشاف الأجهزة
    // ═══════════════════════════════════════════════════
    fun startDiscovery() {
        if (manager == null) { _state.value = WifiP2pState.NotSupported; return }
        if (!hasPermissions()) { _state.value = WifiP2pState.Failed("Permissions missing"); return }
        attempts = 0
        doStartDiscovery()
    }

    private fun doStartDiscovery() {
        val ch = ensureChannel() ?: run {
            _state.value = WifiP2pState.Failed("Channel init failed"); return
        }
        discoveryJob?.cancel(); pollJob?.cancel()
        _state.value = WifiP2pState.Discovering

        discoveryJob = scope.launch {
            try { manager?.stopPeerDiscovery(ch, null) } catch (_: SecurityException) {}
            delay(600)
            try {
                manager?.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        attempts = 0
                        startPolling()
                    }
                    override fun onFailure(reason: Int) {
                        attempts++
                        Logger.w("Discovery failed reason=$reason attempt=$attempts")
                        if (attempts < 4) {
                            scope.launch {
                                channel?.close(); channel = null
                                initializeChannel()
                                delay(3000)
                                doStartDiscovery()
                            }
                        } else {
                            _state.value = WifiP2pState.Failed(mapFailure(reason))
                        }
                    }
                })
            } catch (e: SecurityException) {
                _state.value = WifiP2pState.Failed("Security: ${e.message}")
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            repeat(60) {
                if (!isActive) return@launch
                requestPeers()
                delay(2000)
            }
        }
    }

    private fun requestPeers() {
        val ch = channel ?: return
        if (!hasPermissions()) return
        try {
            manager?.requestPeers(ch) { peers ->
                val list = peers.deviceList.map { it.toPeer() }
                _state.value = if (list.isEmpty()) WifiP2pState.Discovering
                              else WifiP2pState.PeersAvailable(list)
            }
        } catch (_: SecurityException) {}
    }

    // ═══════════════════════════════════════════════════
    // الاتصال بجهاز (نصبح Client)
    // ═══════════════════════════════════════════════════
    fun connect(device: PeerDevice) {
        val ch = ensureChannel() ?: return
        if (!hasPermissions()) { _state.value = WifiP2pState.Failed("Permissions"); return }
        discoveryJob?.cancel(); pollJob?.cancel()
        _state.value = WifiP2pState.Connecting(device)

        val config = WifiP2pConfig().apply {
            deviceAddress = device.address
            wps.setup = WpsInfo.PBC
            groupOwnerIntent = 0 // نحن client (0 = منخفض)
        }
        try {
            manager?.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { Logger.i("Connect requested") }
                override fun onFailure(reason: Int) {
                    _state.value = WifiP2pState.Failed(mapFailure(reason))
                }
            })
        } catch (_: SecurityException) {}
    }

    private fun handleConnectionChanged() {
        val ch = channel ?: return
        if (!hasPermissions()) return
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

    fun stopDiscovery() {
        discoveryJob?.cancel(); pollJob?.cancel()
        val ch = channel ?: return
        try { manager?.stopPeerDiscovery(ch, null) } catch (_: SecurityException) {}
    }

    fun removeGroup() {
        stopDiscovery()
        val ch = channel ?: return
        try { manager?.removeGroup(ch, null) } catch (_: SecurityException) {}
    }

    /** مرادف لـ removeGroup() للتوافق مع الكود القديم */
    fun disconnect() = removeGroup()

    private fun delaySync(ms: Long) {
        try { Thread.sleep(ms) } catch (_: Exception) {}
    }

    private fun hasPermissions(): Boolean =
        PermissionHelper.hasAll(appContext, PermissionHelper.wifiPermissions())

    private fun mapFailure(r: Int): String = when (r) {
        WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct not supported"
        WifiP2pManager.BUSY -> "System busy — close Nearby Share and retry"
        WifiP2pManager.ERROR -> "Internal error"
        else -> "Unknown error ($r)"
    }

    private fun WifiP2pDevice.toPeer() = PeerDevice(
        name = deviceName?.ifBlank { deviceAddress ?: "?" } ?: "?",
        address = deviceAddress ?: "",
        isAvailable = status == WifiP2pDevice.AVAILABLE
    )

    fun dispose() {
        stopDiscovery()
        removeGroup()
        if (receiverRegistered) {
            try { appContext.unregisterReceiver(receiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
        channel?.close(); channel = null
    }
}
