package com.example.wifishare.data.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import com.example.wifishare.core.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class WifiConnector(private val context: Context) {
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var currentNetwork: Network? = null

    suspend fun connect(ssid: String, password: String, timeoutMs: Int = 45_000): Network? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Logger.w("WifiNetworkSpecifier requires Android 10+")
            return null
        }
        return suspendCancellableCoroutine { cont ->
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val specifier: WifiNetworkSpecifier = try {
                WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(password)
                    .build()
            } catch (e: Exception) {
                Logger.e("specifier build failed", e)
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    currentNetwork = network
                    NetworkHolder.set(network)
                    Logger.i("WiFi connected: $ssid")
                    if (cont.isActive) cont.resume(network)
                }
                override fun onUnavailable() {
                    Logger.w("WiFi unavailable: $ssid")
                    if (cont.isActive) cont.resume(null)
                }
                override fun onLost(network: Network) {
                    if (currentNetwork == network) {
                        NetworkHolder.clear()
                        currentNetwork = null
                    }
                }
            }
            callback = cb
            cont.invokeOnCancellation { runCatching { cm.unregisterNetworkCallback(cb) } }
            try { cm.requestNetwork(request, cb, timeoutMs) }
            catch (e: Exception) { Logger.e("requestNetwork failed", e); if (cont.isActive) cont.resume(null) }
        }
    }

    fun disconnect() {
        callback?.let {
            runCatching {
                (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(it)
            }
        }
        callback = null
        currentNetwork = null
        NetworkHolder.clear()
    }
}
