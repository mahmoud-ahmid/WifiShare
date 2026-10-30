package com.example.wifishare.data.wifi

import android.net.Network

object NetworkHolder {
    @Volatile private var network: Network? = null
    fun set(n: Network) { network = n }
    fun get(): Network? = network
    fun clear() { network = null }
}
