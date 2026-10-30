package com.example.wifishare.data.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.wifishare.core.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HotspotInfo(val ssid: String, val password: String, val ip: String)

class HotspotManager(private val context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var callback: WifiManager.LocalOnlyHotspotCallback? = null

    private val _hotspotInfo = MutableStateFlow<HotspotInfo?>(null)
    val hotspotInfo: StateFlow<HotspotInfo?> = _hotspotInfo.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startHotspot() {
        if (_hotspotInfo.value != null) return
        stopHotspot()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            _error.value = "يتطلب أندرويد 8.0 أو أحدث"
            return
        }

        val cb = object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                reservation = res
                var ssid = "WifiShare"
                var pass = ""
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ssid = res.softApConfiguration.ssid ?: "WifiShare"
                        pass = res.softApConfiguration.passphrase ?: ""
                    } else {
                        @Suppress("DEPRECATION")
                        val cfg = res.wifiConfiguration
                        ssid = cfg?.SSID ?: "WifiShare"
                        @Suppress("DEPRECATION")
                        pass = cfg?.preSharedKey ?: ""
                    }
                } catch (e: Exception) { Logger.e("hotspot read", e) }
                _hotspotInfo.value = HotspotInfo(ssid, pass, "192.168.43.1")
                _error.value = null
            }
            override fun onStopped() { _hotspotInfo.value = null }
            override fun onFailed(reason: Int) {
                _error.value = "فشل إنشاء النقطة (كود: $reason)"
                _hotspotInfo.value = null
            }
        }
        callback = cb

        try {
            wifiManager.startLocalOnlyHotspot(cb, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            _error.value = "استثناء: ${e.message}"
        }
    }

    fun stopHotspot() {
        try { reservation?.close() } catch (_: Exception) {}
        reservation = null
        _hotspotInfo.value = null
    }
}
